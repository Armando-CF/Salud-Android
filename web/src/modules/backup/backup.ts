// Respaldo y restauración. Un "archivo" (Archive) es la forma común en memoria de cualquier respaldo:
// el .json de la web, el data.json de Android o el .zip de Android. Todos se importan con las mismas funciones.

import { strFromU8, strToU8, unzipSync, zipSync } from 'fflate'
import { dbFor, meta } from '../../data/db'
import { getSetting, setSetting } from '../../data/prefs'
import { createProfile, deleteProfile, profileIds, switchProfile } from '../../data/profiles'
import { Fmt, epochDayToIso, today } from '../../lib/dates'
import { slug } from '../../lib/files'
import {
  BackupError, isRow, prefsFromJson, prefsToAndroid, readPrefs, readTables, recordCount, replaceTables, mergeTables,
  tablesFromJson, tablesToJson, toArrayBuffer, writePrefs, type ProfilePrefs, type ProfileTables, type Row,
} from './model'

export const WEB_FORMAT = 'savia-web-backup'
export const WEB_FORMAT_VERSION = 1
/** Formato del respaldo de UN perfil en Savia para Android (data.json). */
export const ANDROID_FORMAT = 'savia-backup'
export const ANDROID_FORMAT_VERSION = 2
/** Formato del respaldo completo de Android (perfiles.json). */
export const ANDROID_ALL_FORMAT = 'savia-backup-all'
export const ANDROID_ALL_FORMAT_VERSION = 1
export const WEB_APP_VERSION = '1.0.0'

/** Un perfil dentro de un respaldo. */
export interface ArchivedProfile {
  /** Id que tenía el perfil donde se creó el respaldo (1 = perfil principal). */
  originalId: number
  tables: ProfileTables
  prefs: ProfilePrefs
}

/** Respaldo ya leído y validado, listo para importarse. */
export interface Archive {
  /** "all": todos los perfiles; "profile": un solo perfil. */
  kind: 'all' | 'profile'
  source: 'web' | 'android'
  createdAtMillis: number
  appVersion: string
  activeProfileId: number | null
  globalPrefs: { themeMode?: string }
  profiles: ArchivedProfile[]
}

export interface ProfileSummary {
  originalId: number
  name: string
  kind: string
  species: string
  records: number
  attachments: number
  /** Adjuntos listados cuyo contenido no viene en el respaldo. */
  attachmentsMissing: number
}

export interface ArchiveSummary {
  kind: 'all' | 'profile'
  source: 'web' | 'android'
  createdAtMillis: number
  appVersion: string
  profiles: ProfileSummary[]
  records: number
  attachments: number
}

export function summarize(a: Archive): ArchiveSummary {
  const profiles = a.profiles.map((p) => ({
    originalId: p.originalId,
    name: p.tables.profile?.name?.trim() ?? '',
    kind: p.tables.profile?.kind ?? 'person',
    species: p.tables.profile?.species ?? '',
    records: recordCount(p.tables),
    attachments: p.tables.studyAttachments.length,
    attachmentsMissing: p.tables.studyAttachments.filter((x) => !x.data || x.data.byteLength === 0).length,
  }))
  return {
    kind: a.kind, source: a.source, createdAtMillis: a.createdAtMillis, appVersion: a.appVersion, profiles,
    records: profiles.reduce((n, p) => n + p.records, 0),
    attachments: profiles.reduce((n, p) => n + p.attachments, 0),
  }
}

// ── Crear ─────────────────────────────────────────────────────────────────────────────────────────

/** Lee de IndexedDB todos los perfiles ("all") o uno solo (su id). */
export async function collectArchive(scope: 'all' | number, withFiles = true): Promise<Archive> {
  const ids = scope === 'all' ? await profileIds() : [scope]
  const profiles: ArchivedProfile[] = []
  for (const id of ids) {
    const db = dbFor(id)
    profiles.push({ originalId: id, tables: await readTables(db, withFiles), prefs: await readPrefs(db) })
  }
  const active = ((await meta.kv.get('activeProfile'))?.value as number | undefined) ?? 1
  return {
    kind: scope === 'all' ? 'all' : 'profile', source: 'web', createdAtMillis: Date.now(), appVersion: WEB_APP_VERSION,
    activeProfileId: active, globalPrefs: { themeMode: await getSetting('themeMode') }, profiles,
  }
}

/** Respaldo → objeto JSON del formato web. Nunca incluye el PIN ni otros ajustes de bloqueo. */
export function toWebJson(a: Archive, includeAttachments = true): Row {
  return {
    format: WEB_FORMAT,
    formatVersion: WEB_FORMAT_VERSION,
    app: 'Savia Web',
    appVersion: WEB_APP_VERSION,
    scope: a.kind,
    createdAt: Fmt.isoDateTime(a.createdAtMillis),
    createdAtMillis: a.createdAtMillis,
    activeProfileId: a.activeProfileId ?? 1,
    nota: 'Respaldo de Savia. epochDay = días desde 1970-01-01; timestamp = milisegundos desde 1970-01-01 UTC. Los archivos adjuntos van en base64 (dataBase64).',
    attachmentsIncluded: includeAttachments,
    globalPrefs: { themeMode: a.globalPrefs.themeMode ?? 'system' },
    profiles: a.profiles.map((p) => ({
      id: p.originalId,
      name: p.tables.profile?.name ?? '',
      kind: p.tables.profile?.kind ?? 'person',
      species: p.tables.profile?.species ?? '',
      prefs: p.prefs,
      tables: tablesToJson(p.tables, includeAttachments ? 'inline' : 'none'),
    })),
  }
}

export const serializeWebBackup = (a: Archive, includeAttachments = true): string => JSON.stringify(toWebJson(a, includeAttachments))

/** Nombre de archivo sugerido. Con `profileName` es el respaldo de un solo perfil. */
export function backupFileName(day: number = today(), profileName?: string): string {
  return profileName !== undefined ? `savia-respaldo-${slug(profileName)}-${epochDayToIso(day)}.json` : `savia-respaldo-${epochDayToIso(day)}.json`
}

// ── Leer ──────────────────────────────────────────────────────────────────────────────────────────

const NEWER = 'Este respaldo se creó con una versión más nueva de Savia. Actualiza la app para poder importarlo.'
const NOT_SAVIA = 'Este archivo no es un respaldo de Savia.'
const DAMAGED = 'El archivo está dañado o incompleto.'

function parseJson(text: string): Row {
  let v: unknown
  try {
    v = JSON.parse(text.replace(/^﻿/, ''))
  } catch {
    throw new BackupError('El archivo está dañado o no es un respaldo de Savia.')
  }
  if (!isRow(v)) throw new BackupError(NOT_SAVIA)
  return v
}

const millis = (o: Row): number => (typeof o.createdAtMillis === 'number' && Number.isFinite(o.createdAtMillis) ? o.createdAtMillis : 0)

/** data.json de Android (un perfil) → perfil archivado. Valida formato y versión. */
export function parseAndroidData(root: Row, originalId = 1): { profile: ArchivedProfile; createdAtMillis: number; appVersion: string } {
  if (root.format !== ANDROID_FORMAT) throw new BackupError(NOT_SAVIA)
  const version = typeof root.formatVersion === 'number' ? root.formatVersion : 0
  if (version < 1) throw new BackupError('El respaldo está dañado o incompleto.')
  if (version > ANDROID_FORMAT_VERSION) throw new BackupError(NEWER)
  if (!isRow(root.tables)) throw new BackupError('El respaldo está dañado: no contiene datos.')
  return {
    profile: { originalId, tables: tablesFromJson(root.tables), prefs: prefsFromJson(root.prefs) },
    createdAtMillis: millis(root),
    appVersion: typeof root.appVersion === 'string' ? root.appVersion : '',
  }
}

/** Texto de un .json → respaldo. Acepta el formato web y también el `data.json` suelto de Android. */
export function parseBackupJson(text: string): Archive {
  const root = parseJson(text)
  if (root.format === ANDROID_FORMAT) {
    const p = parseAndroidData(root)
    return { kind: 'profile', source: 'android', createdAtMillis: p.createdAtMillis, appVersion: p.appVersion, activeProfileId: null, globalPrefs: {}, profiles: [p.profile] }
  }
  if (root.format !== WEB_FORMAT) throw new BackupError(NOT_SAVIA)
  const version = typeof root.formatVersion === 'number' ? root.formatVersion : 0
  if (version < 1) throw new BackupError(DAMAGED)
  if (version > WEB_FORMAT_VERSION) throw new BackupError(NEWER)
  const list = Array.isArray(root.profiles) ? root.profiles.filter(isRow) : []
  if (list.length === 0) throw new BackupError('El respaldo está dañado: no contiene datos.')
  const profiles = list.map((p, i): ArchivedProfile => {
    if (!isRow(p.tables)) throw new BackupError('El respaldo está dañado: no contiene datos.')
    return { originalId: typeof p.id === 'number' && p.id > 0 ? Math.trunc(p.id) : i + 1, tables: tablesFromJson(p.tables), prefs: prefsFromJson(p.prefs) }
  })
  const g = isRow(root.globalPrefs) ? root.globalPrefs : {}
  const theme = typeof g.themeMode === 'string' && ['system', 'light', 'dark'].includes(g.themeMode) ? g.themeMode : undefined
  return {
    kind: root.scope === 'profile' || (root.scope !== 'all' && profiles.length === 1) ? 'profile' : 'all',
    source: 'web',
    createdAtMillis: millis(root),
    appVersion: typeof root.appVersion === 'string' ? root.appVersion : '',
    activeProfileId: typeof root.activeProfileId === 'number' ? root.activeProfileId : null,
    globalPrefs: theme ? { themeMode: theme } : {},
    profiles,
  }
}

// ── Android (.zip) ────────────────────────────────────────────────────────────────────────────────

const isSafeName = (name: string): boolean => name.trim() !== '' && name !== '.' && name !== '..' && !/[/\\\0]/.test(name)

/** Asigna a cada adjunto el contenido del archivo con su mismo `fileName`. */
function attachFiles(tables: ProfileTables, files: Map<string, Uint8Array>): void {
  for (const a of tables.studyAttachments) {
    const f = files.get(a.fileName)
    if (f) a.data = toArrayBuffer(f)
  }
}

/**
 * Lee un respaldo .zip de Savia para Android: de un perfil (`data.json` + `attachments/…`) o completo
 * (`perfiles.json` + `perfiles/<id>/data.json` + `perfiles/<id>/attachments/…`).
 */
export function readAndroidZip(bytes: Uint8Array): Archive {
  let entries: Record<string, Uint8Array>
  try {
    entries = unzipSync(bytes)
  } catch {
    throw new BackupError('No se pudo abrir el archivo .zip: está dañado o no es un respaldo de Savia.')
  }
  const text = (name: string) => strFromU8(entries[name])
  let single: string | null = null
  let manifest: string | null = null
  const singleFiles = new Map<string, Uint8Array>()
  const perProfile = new Map<number, { data: string | null; files: Map<string, Uint8Array> }>()
  const slot = (id: number) => {
    let s = perProfile.get(id)
    if (!s) { s = { data: null, files: new Map() }; perProfile.set(id, s) }
    return s
  }
  for (const name of Object.keys(entries)) {
    if (name.endsWith('/')) continue
    if (name === 'data.json') single = text(name)
    else if (name === 'perfiles.json') manifest = text(name)
    else if (name.startsWith('attachments/')) {
      const f = name.slice('attachments/'.length)
      if (isSafeName(f)) singleFiles.set(f, entries[name])
    } else if (name.startsWith('perfiles/')) {
      const rest = name.slice('perfiles/'.length)
      const i = rest.indexOf('/')
      const id = i > 0 ? Number(rest.slice(0, i)) : NaN
      if (!Number.isInteger(id) || id <= 0) continue
      const sub = rest.slice(i + 1)
      if (sub === 'data.json') slot(id).data = text(name)
      else if (sub.startsWith('attachments/')) {
        const f = sub.slice('attachments/'.length)
        if (isSafeName(f)) slot(id).files.set(f, entries[name])
      }
    }
  }
  if (manifest !== null) {
    const root = parseJson(manifest)
    if (root.format !== ANDROID_ALL_FORMAT) throw new BackupError(NOT_SAVIA)
    const version = typeof root.formatVersion === 'number' ? root.formatVersion : 0
    if (version < 1) throw new BackupError(DAMAGED)
    if (version > ANDROID_ALL_FORMAT_VERSION) throw new BackupError(NEWER)
    const ids = [...perProfile.keys()].sort((a, b) => a - b)
    const profiles: ArchivedProfile[] = []
    for (const id of ids) {
      const s = perProfile.get(id)!
      if (s.data === null) continue
      const p = parseAndroidData(parseJson(s.data), id).profile
      attachFiles(p.tables, s.files)
      profiles.push(p)
    }
    if (profiles.length === 0) throw new BackupError('El respaldo está dañado: no contiene datos.')
    const g = isRow(root.globalPrefs) ? root.globalPrefs : {}
    const theme = typeof g.theme_mode === 'string' && ['system', 'light', 'dark'].includes(g.theme_mode) ? g.theme_mode : undefined
    return {
      kind: 'all', source: 'android', createdAtMillis: millis(root), appVersion: typeof root.appVersion === 'string' ? root.appVersion : '',
      activeProfileId: typeof root.activeProfileId === 'number' ? root.activeProfileId : null, globalPrefs: theme ? { themeMode: theme } : {}, profiles,
    }
  }
  if (single !== null) {
    const p = parseAndroidData(parseJson(single))
    attachFiles(p.profile.tables, singleFiles)
    return { kind: 'profile', source: 'android', createdAtMillis: p.createdAtMillis, appVersion: p.appVersion, activeProfileId: null, globalPrefs: {}, profiles: [p.profile] }
  }
  throw new BackupError(NOT_SAVIA)
}

/**
 * Respaldo de UN perfil en el formato exacto que Savia para Android restaura:
 * `data.json` (format "savia-backup", prefs con claves de Android, tablas) + `attachments/<fileName>`.
 */
export function writeAndroidZip(p: ArchivedProfile, createdAtMillis: number = Date.now()): Uint8Array {
  // Android guarda cada adjunto como un archivo: los nombres deben ser únicos y sin separadores de ruta.
  const used = new Set<string>()
  const files: Record<string, Uint8Array> = {}
  const attachments = p.tables.studyAttachments.map((a, i) => {
    const dot = a.displayName.lastIndexOf('.')
    const ext = dot > 0 ? a.displayName.slice(dot + 1).replace(/[^A-Za-z0-9]/g, '').slice(0, 5) : a.mime.includes('pdf') ? 'pdf' : a.mime.includes('png') ? 'png' : 'jpg'
    let name = isSafeName(a.fileName) && !used.has(a.fileName) && /^[\w.-]+$/.test(a.fileName) ? a.fileName : `adj_${a.id ?? i + 1}_${slug(a.displayName.replace(/\.[^.]*$/, ''), 20)}.${ext || 'bin'}`
    for (let n = 2; used.has(name); n++) name = `adj_${a.id ?? i + 1}_${n}.${ext || 'bin'}`
    used.add(name)
    if (a.data && a.data.byteLength > 0) files[`attachments/${name}`] = new Uint8Array(a.data)
    return { ...a, fileName: name }
  })
  const data: Row = {
    format: ANDROID_FORMAT,
    formatVersion: ANDROID_FORMAT_VERSION,
    app: 'Savia',
    appVersion: `web-${WEB_APP_VERSION}`,
    appVersionCode: 0,
    createdAtMillis,
    createdAt: Fmt.isoDateTime(createdAtMillis),
    nota: 'Respaldo completo de Savia. epochDay = días desde 1970-01-01; timestamp = milisegundos desde 1970-01-01 UTC.',
    prefs: prefsToAndroid(p.prefs),
    tables: tablesToJson({ ...p.tables, studyAttachments: attachments }, 'none'),
  }
  return zipSync({ 'data.json': strToU8(JSON.stringify(data, null, 2)), ...files }, { level: 6 })
}

export function androidFileName(profileName: string, day: number = today()): string {
  return `savia-android-${slug(profileName)}-${epochDayToIso(day)}.zip`
}

/** Abre un archivo elegido por la persona: detecta solo si es .json (web o Android) o .zip (Android). */
export function openBackupBytes(bytes: Uint8Array): Archive {
  if (bytes.length < 2) throw new BackupError('El archivo está vacío.')
  if (bytes[0] === 0x50 && bytes[1] === 0x4b) return readAndroidZip(bytes)
  let text: string
  try {
    text = new TextDecoder('utf-8', { fatal: false }).decode(bytes)
  } catch {
    throw new BackupError('No se pudo leer el archivo.')
  }
  return parseBackupJson(text)
}

// ── Importar ──────────────────────────────────────────────────────────────────────────────────────

export type ImportMode = 'add' | 'replaceAll' | 'replaceProfile' | 'merge'

export interface ImportResult {
  mode: ImportMode
  /** Perfiles creados o reemplazados. */
  profiles: number
  added: number
  updated: number
  skipped: number
  attachments: number
  /** Ids de los perfiles nuevos, en el orden del respaldo. */
  newProfileIds: number[]
  /** Resumen listo para mostrarse. */
  text: string
}

const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`
const filesOf = (t: ProfileTables) => t.studyAttachments.filter((a) => a.data && a.data.byteLength > 0).length

async function addProfile(p: ArchivedProfile): Promise<number> {
  const id = await createProfile(p.tables.profile ?? {}, p.prefs)
  await replaceTables(dbFor(id), p.tables)
  return id
}

/** Cada perfil del respaldo entra como perfil nuevo; no toca nada de lo existente. */
export async function importAsNewProfiles(a: Archive): Promise<ImportResult> {
  const ids: number[] = []
  let records = 0, files = 0
  for (const p of a.profiles) {
    ids.push(await addProfile(p))
    records += recordCount(p.tables)
    files += filesOf(p.tables)
  }
  return {
    mode: 'add', profiles: ids.length, added: records, updated: 0, skipped: 0, attachments: files, newProfileIds: ids,
    text: `${ids.length === 1 ? 'Se agregó 1 perfil nuevo' : `Se agregaron ${ids.length} perfiles nuevos`} con ${plural(records, 'registro', 'registros')}.`,
  }
}

// Copia de seguridad interna previa a un reemplazo (se guarda en la base meta y permite deshacerlo).
const SAFETY_KEY = 'backup.safety'
const RESULT_KEY = 'backup.lastResult'

interface SafetyCopy {
  createdAtMillis: number
  /** "all" = todos los perfiles; número = solo ese perfil. */
  scope: 'all' | number
  archive: Archive
}

export interface SafetyInfo { createdAtMillis: number; scope: 'all' | number; profiles: number; records: number }

async function saveSafety(scope: 'all' | number): Promise<void> {
  const archive = await collectArchive(scope, true)
  const value: SafetyCopy = { createdAtMillis: Date.now(), scope, archive }
  await meta.kv.put({ key: SAFETY_KEY, value })
}

/** Datos de la copia de seguridad interna, si existe. */
export async function getSafetyInfo(): Promise<SafetyInfo | null> {
  const v = (await meta.kv.get(SAFETY_KEY))?.value as SafetyCopy | undefined
  if (!v?.archive) return null
  return { createdAtMillis: v.createdAtMillis, scope: v.scope, profiles: v.archive.profiles.length, records: v.archive.profiles.reduce((n, p) => n + recordCount(p.tables), 0) }
}

export async function discardSafety(): Promise<void> {
  await meta.kv.delete(SAFETY_KEY)
}

async function replaceProfileContent(id: number, p: ArchivedProfile): Promise<void> {
  const db = dbFor(id)
  await replaceTables(db, p.tables)
  await db.prefs.clear()
  await writePrefs(db, p.prefs)
}

async function replaceAllCore(a: Archive): Promise<{ ids: number[]; records: number; files: number }> {
  const main = a.profiles.find((p) => p.originalId === 1) ?? a.profiles[0]
  const rest = a.profiles.filter((p) => p !== main)
  const map = new Map<number, number>()
  await replaceProfileContent(1, main)
  map.set(main.originalId, 1)
  for (const id of await profileIds()) if (id !== 1) await deleteProfile(id)
  const ids = [1]
  for (const p of rest) {
    const id = await addProfile(p)
    map.set(p.originalId, id)
    ids.push(id)
  }
  if (a.globalPrefs.themeMode) await setSetting('themeMode', a.globalPrefs.themeMode)
  // El perfil que estaba activo en el respaldo vuelve a ser el activo.
  await switchProfile(a.activeProfileId !== null ? map.get(a.activeProfileId) ?? 1 : 1)
  return { ids, records: a.profiles.reduce((n, p) => n + recordCount(p.tables), 0), files: a.profiles.reduce((n, p) => n + filesOf(p.tables), 0) }
}

/**
 * Deja el dispositivo como el respaldo: el perfil principal del respaldo reemplaza al perfil 1, los demás perfiles
 * actuales se eliminan y el resto del respaldo entra como perfiles nuevos. Antes guarda una copia interna de TODO
 * lo actual, que se puede recuperar con [undoLastReplace].
 */
export async function importReplaceAll(a: Archive, opts: { safety?: boolean } = {}): Promise<ImportResult> {
  if (a.profiles.length === 0) throw new BackupError('El respaldo está dañado: no contiene datos.')
  const safety = opts.safety ?? true
  if (safety) await saveSafety('all')
  const r = await replaceAllCore(a)
  const what = `${plural(r.ids.length, 'perfil', 'perfiles')} y ${plural(r.records, 'registro', 'registros')}`
  return {
    mode: 'replaceAll', profiles: r.ids.length, added: r.records, updated: 0, skipped: 0, attachments: r.files, newProfileIds: r.ids.slice(1),
    text: safety ? `Se reemplazó todo con el respaldo: ${what}. Guardamos una copia de lo que había por si quieres deshacerlo.` : `Respaldo restaurado: ${what}.`,
  }
}

/**
 * Aplica el respaldo de UN perfil sobre un perfil existente.
 *  - "replace": borra lo actual de ese perfil y deja exactamente lo del respaldo (antes guarda una copia interna).
 *  - "merge": conserva lo actual y agrega lo que falte (en ciclo y bienestar gana el respaldo en los días que trae).
 */
export async function importIntoProfile(p: ArchivedProfile, targetId: number, mode: 'replace' | 'merge', opts: { safety?: boolean } = {}): Promise<ImportResult> {
  const db = dbFor(targetId)
  if (mode === 'replace') {
    const safety = opts.safety ?? true
    if (safety) await saveSafety(targetId)
    await replaceProfileContent(targetId, p)
    const n = recordCount(p.tables)
    return {
      mode: 'replaceProfile', profiles: 1, added: n, updated: 0, skipped: 0, attachments: filesOf(p.tables), newProfileIds: [],
      text: safety
        ? `Se reemplazaron los datos de este perfil con ${plural(n, 'registro', 'registros')} del respaldo. Guardamos una copia de lo que había por si quieres deshacerlo.`
        : `Respaldo restaurado: ${plural(n, 'registro', 'registros')}.`,
    }
  }
  const c = await mergeTables(db, p.tables)
  if (p.tables.cycleDays.length > 0 && p.prefs.cycleEnabled !== false) await writePrefs(db, { cycleEnabled: true })
  return {
    mode: 'merge', profiles: 1, added: c.added, updated: c.updated, skipped: c.skipped, attachments: c.attachments, newProfileIds: [],
    text: `Combinado: ${plural(c.added, 'registro nuevo', 'registros nuevos')}, ${plural(c.updated, 'actualizado', 'actualizados')} y ${plural(c.skipped, 'que ya estaba', 'que ya estaban')}.`,
  }
}

/** Recupera lo que había antes del último reemplazo y borra la copia interna. */
export async function undoLastReplace(): Promise<ImportResult> {
  const v = (await meta.kv.get(SAFETY_KEY))?.value as SafetyCopy | undefined
  if (!v?.archive || v.archive.profiles.length === 0) throw new BackupError('No hay nada que deshacer.')
  let profiles = 1
  let records = 0
  if (v.scope === 'all') {
    const r = await replaceAllCore(v.archive)
    profiles = r.ids.length
    records = r.records
  } else {
    if (!(await meta.profiles.get(v.scope))) throw new BackupError('El perfil de esa copia ya no existe.')
    await replaceProfileContent(v.scope, v.archive.profiles[0])
    records = recordCount(v.archive.profiles[0].tables)
  }
  await meta.kv.delete(SAFETY_KEY)
  return {
    mode: v.scope === 'all' ? 'replaceAll' : 'replaceProfile', profiles, added: records, updated: 0, skipped: 0, attachments: 0, newProfileIds: [],
    text: `Listo: se recuperó lo que había antes (${plural(profiles, 'perfil', 'perfiles')}, ${plural(records, 'registro', 'registros')}).`,
  }
}

// ── Avisos que sobreviven a un cambio de perfil ───────────────────────────────────────────────────

/** Guarda el resultado de la última importación (la pantalla puede recrearse al cambiar el perfil activo). */
export async function rememberResult(text: string): Promise<void> {
  await meta.kv.put({ key: RESULT_KEY, value: { at: Date.now(), text } })
}

/** Resultado de la última importación si ocurrió en las últimas 24 horas. */
export async function lastResult(): Promise<{ at: number; text: string } | null> {
  const v = (await meta.kv.get(RESULT_KEY))?.value as { at: number; text: string } | undefined
  return v && Date.now() - v.at < 86_400_000 ? v : null
}

export async function clearLastResult(): Promise<void> {
  await meta.kv.delete(RESULT_KEY)
}

/** Tamaño total (bytes) de los archivos adjuntos de los perfiles dados. */
export async function attachmentsSize(ids: number[]): Promise<{ count: number; bytes: number }> {
  let count = 0, bytes = 0
  for (const id of ids) {
    await dbFor(id).studyAttachments.each((a) => { count++; bytes += a.data?.byteLength ?? 0 })
  }
  return { count, bytes }
}

/** "1.4 MB", "320 KB". */
export function sizeText(bytes: number): string {
  if (bytes >= 1024 * 1024) return `${Fmt.num(bytes / (1024 * 1024), 1)} MB`
  if (bytes >= 1024) return `${Math.round(bytes / 1024)} KB`
  return `${bytes} bytes`
}
