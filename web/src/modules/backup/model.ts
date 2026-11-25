// Modelo del respaldo: contenido completo de la base de UN perfil, su lectura/escritura en IndexedDB
// y su forma en JSON. Los nombres de tabla y de campo del JSON son los mismos que usa `data.json`
// en Savia para Android (ver Backup.kt), para que los perfiles sean intercambiables entre ambas apps.

import type { SaviaDB } from '../../data/db'
import { PREF_DEFAULTS, type PrefKey } from '../../data/prefs'
import {
  emptyProfile, type Appointment, type CycleDay, type DailyLog, type MedIntake, type Measurement, type Medication,
  type Profile, type SelfExam, type Study, type StudyAttachment, type StudyResult, type SymptomEntry, type Vaccination,
} from '../../data/types'

/** Error con mensaje listo para mostrarse a la persona. */
export class BackupError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'BackupError'
  }
}

/** Adjunto dentro de un respaldo: `data` puede faltar si el respaldo se hizo sin archivos. */
export type BackupAttachment = Omit<StudyAttachment, 'data'> & { data: ArrayBuffer | null }

/** Contenido completo de la base de datos de un perfil (todas las tablas, sin filtrar). */
export interface ProfileTables {
  profile: Profile | null
  symptoms: SymptomEntry[]
  measurements: Measurement[]
  dailyLogs: DailyLog[]
  studies: Study[]
  studyResults: StudyResult[]
  studyAttachments: BackupAttachment[]
  cycleDays: CycleDay[]
  medications: Medication[]
  medIntakes: MedIntake[]
  appointments: Appointment[]
  selfExams: SelfExam[]
  vaccinations: Vaccination[]
}

export const emptyTables = (): ProfileTables => ({
  profile: null, symptoms: [], measurements: [], dailyLogs: [], studies: [], studyResults: [], studyAttachments: [],
  cycleDays: [], medications: [], medIntakes: [], appointments: [], selfExams: [], vaccinations: [],
})

/** Total de registros (sin contar el perfil ni los adjuntos), igual que en Android. */
export function recordCount(t: ProfileTables): number {
  return t.symptoms.length + t.measurements.length + t.dailyLogs.length + t.studies.length + t.studyResults.length +
    t.cycleDays.length + t.medications.length + t.medIntakes.length + t.appointments.length + t.selfExams.length + t.vaccinations.length
}

// ── Lectura tolerante (campos faltantes o de tipo inesperado toman su valor por defecto) ──────────

export type Row = Record<string, unknown>
export const isRow = (v: unknown): v is Row => typeof v === 'object' && v !== null && !Array.isArray(v)
const str = (o: Row, k: string, def = ''): string => {
  const v = o[k]
  return v === null || v === undefined ? def : typeof v === 'string' ? v : String(v)
}
const strN = (o: Row, k: string): string | null => (o[k] === null || o[k] === undefined ? null : str(o, k))
const numN = (o: Row, k: string): number | null => {
  const v = o[k]
  if (v === null || v === undefined || v === '') return null
  const n = typeof v === 'number' ? v : Number(v)
  return Number.isFinite(n) ? n : null
}
const num = (o: Row, k: string, def = 0): number => numN(o, k) ?? def
const int = (o: Row, k: string, def = 0): number => Math.trunc(num(o, k, def))
const intN = (o: Row, k: string): number | null => { const n = numN(o, k); return n === null ? null : Math.trunc(n) }
const bool = (o: Row, k: string, def = false): boolean => {
  const v = o[k]
  return typeof v === 'boolean' ? v : v === 'true' ? true : v === 'false' ? false : v === 1 ? true : v === 0 ? false : def
}
const boolN = (o: Row, k: string): boolean | null => (o[k] === null || o[k] === undefined ? null : bool(o, k))

/*
 * Normalizadores: construyen cada registro SIEMPRE con las mismas claves y en el mismo orden, de modo que
 * JSON.stringify sirva para comparar registros (duplicados exactos) y para escribir el respaldo.
 */
export const norm = {
  profile: (o: Row): Profile => ({
    id: 1, name: str(o, 'name'), sex: str(o, 'sex', 'X') || 'X', birthEpochDay: intN(o, 'birthEpochDay'), heightCm: numN(o, 'heightCm'),
    goalWeightKg: numN(o, 'goalWeightKg'), bloodType: str(o, 'bloodType'), allergies: str(o, 'allergies'), conditions: str(o, 'conditions'),
    emergencyName: str(o, 'emergencyName'), emergencyPhone: str(o, 'emergencyPhone'), doctorName: str(o, 'doctorName'),
    doctorPhone: str(o, 'doctorPhone'), insurance: str(o, 'insurance'), notes: str(o, 'notes'),
    // Campos de la versión 2 del formato de Android; en respaldos de Savia 1.0 no existen y toman su valor por defecto.
    kind: str(o, 'kind', 'person') === 'pet' ? 'pet' : 'person', species: str(o, 'species'), breed: str(o, 'breed'),
    sterilized: boolN(o, 'sterilized'), microchip: str(o, 'microchip'), markings: str(o, 'markings'), relation: str(o, 'relation'),
    avatarColor: int(o, 'avatarColor'),
  }),
  symptom: (o: Row): SymptomEntry => ({
    id: int(o, 'id'), timestamp: int(o, 'timestamp'), name: str(o, 'name'), intensity: int(o, 'intensity', 5), regionId: strN(o, 'regionId'),
    regionLabel: strN(o, 'regionLabel'), durationMin: intN(o, 'durationMin'), tags: str(o, 'tags'), notes: str(o, 'notes'),
  }),
  measurement: (o: Row): Measurement => ({
    id: int(o, 'id'), timestamp: int(o, 'timestamp'), type: str(o, 'type'), value: num(o, 'value'), value2: numN(o, 'value2'), tag: str(o, 'tag'), notes: str(o, 'notes'),
  }),
  dailyLog: (o: Row): DailyLog => ({
    epochDay: int(o, 'epochDay'), mood: intN(o, 'mood'), energy: intN(o, 'energy'), stress: intN(o, 'stress'), sleepHours: numN(o, 'sleepHours'),
    waterGlasses: intN(o, 'waterGlasses'), exerciseMin: intN(o, 'exerciseMin'), notes: str(o, 'notes'),
  }),
  study: (o: Row): Study => ({
    id: int(o, 'id'), epochDay: int(o, 'epochDay'), title: str(o, 'title'), category: str(o, 'category', 'lab'), place: str(o, 'place'),
    doctor: str(o, 'doctor'), status: str(o, 'status'), summary: str(o, 'summary'), followUpEpochDay: intN(o, 'followUpEpochDay'),
  }),
  studyResult: (o: Row): StudyResult => ({
    id: int(o, 'id'), studyId: int(o, 'studyId'), name: str(o, 'name'), value: numN(o, 'value'), textValue: str(o, 'textValue'), unit: str(o, 'unit'),
    refMin: numN(o, 'refMin'), refMax: numN(o, 'refMax'), notes: str(o, 'notes'),
  }),
  /** Sin contenido: `data` se resuelve aparte (base64 en la web, archivos del zip en Android). */
  attachment: (o: Row): BackupAttachment => {
    const fileName = str(o, 'fileName')
    return { id: int(o, 'id'), studyId: int(o, 'studyId'), fileName, displayName: str(o, 'displayName') || fileName, mime: str(o, 'mime'), data: null }
  },
  cycleDay: (o: Row): CycleDay => ({
    epochDay: int(o, 'epochDay'), flow: int(o, 'flow'), symptoms: str(o, 'symptoms'), moods: str(o, 'moods'), mucus: str(o, 'mucus'),
    basalTemp: numN(o, 'basalTemp'), sex: int(o, 'sex'), ovulationTest: int(o, 'ovulationTest'), pregnancyTest: int(o, 'pregnancyTest'),
    contraceptive: bool(o, 'contraceptive'), notes: str(o, 'notes'),
  }),
  medication: (o: Row): Medication => ({
    id: int(o, 'id'), name: str(o, 'name'), dose: str(o, 'dose'), instructions: str(o, 'instructions'), times: str(o, 'times'),
    daysMask: int(o, 'daysMask', 127), startEpochDay: int(o, 'startEpochDay'), endEpochDay: intN(o, 'endEpochDay'), active: bool(o, 'active', true),
    remind: bool(o, 'remind', true), notes: str(o, 'notes'),
  }),
  medIntake: (o: Row): MedIntake => ({
    id: int(o, 'id'), medicationId: int(o, 'medicationId'), epochDay: int(o, 'epochDay'), slot: str(o, 'slot'), timestamp: int(o, 'timestamp'),
    status: str(o, 'status', 'taken'),
  }),
  appointment: (o: Row): Appointment => ({
    id: int(o, 'id'), timestamp: int(o, 'timestamp'), title: str(o, 'title'), doctor: str(o, 'doctor'), specialty: str(o, 'specialty'),
    place: str(o, 'place'), notes: str(o, 'notes'), remind: bool(o, 'remind', true), done: bool(o, 'done'),
  }),
  selfExam: (o: Row): SelfExam => ({ id: int(o, 'id'), epochDay: int(o, 'epochDay'), type: str(o, 'type'), normal: bool(o, 'normal', true), notes: str(o, 'notes') }),
  vaccination: (o: Row): Vaccination => ({
    id: int(o, 'id'), epochDay: int(o, 'epochDay'), type: str(o, 'type', 'vacuna'), name: str(o, 'name'), dose: str(o, 'dose'),
    nextDueEpochDay: intN(o, 'nextDueEpochDay'), place: str(o, 'place'), lot: str(o, 'lot'), notes: str(o, 'notes'), remind: bool(o, 'remind', true),
  }),
}

// ── base64 ────────────────────────────────────────────────────────────────────────────────────────

export function toBase64(data: ArrayBuffer | Uint8Array): string {
  const bytes = data instanceof Uint8Array ? data : new Uint8Array(data)
  let bin = ''
  const CHUNK = 0x8000
  for (let i = 0; i < bytes.length; i += CHUNK) bin += String.fromCharCode(...bytes.subarray(i, i + CHUNK))
  return btoa(bin)
}

export function fromBase64(b64: string): ArrayBuffer {
  const bin = atob(b64.replace(/\s+/g, ''))
  const bytes = new Uint8Array(bin.length)
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i)
  return bytes.buffer
}

/** Copia independiente de un contenido binario como ArrayBuffer. */
export function toArrayBuffer(data: ArrayBuffer | ArrayBufferView): ArrayBuffer {
  if (ArrayBuffer.isView(data)) return (data.buffer as ArrayBuffer).slice(data.byteOffset, data.byteOffset + data.byteLength)
  return (data as ArrayBuffer).slice(0)
}

// ── JSON de las tablas (mismos nombres que data.json de Android) ──────────────────────────────────

/** Tablas en el JSON del respaldo. */
export interface TablesJson {
  profile: Row[]
  symptoms: Row[]
  measurements: Row[]
  daily_logs: Row[]
  studies: Row[]
  study_results: Row[]
  study_attachments: Row[]
  cycle_days: Row[]
  medications: Row[]
  med_intakes: Row[]
  appointments: Row[]
  self_exams: Row[]
  vaccinations: Row[]
}

const asRow = (v: unknown): Row => v as Row

/**
 * Tablas → JSON. `attachments`:
 *  - "inline": cada adjunto lleva su contenido en `dataBase64` (formato web).
 *  - "none": solo los datos del adjunto, sin contenido (respaldo sin archivos, o formato Android: van aparte en el zip).
 */
export function tablesToJson(t: ProfileTables, attachments: 'inline' | 'none'): TablesJson {
  const profile: Row[] = []
  if (t.profile) {
    // Igual que Android: la ficha va sin `id` (siempre es la fila única).
    const { id: _id, ...rest } = norm.profile(asRow(t.profile))
    profile.push(rest)
  }
  return {
    profile,
    symptoms: t.symptoms.map((r) => asRow(norm.symptom(asRow(r)))),
    measurements: t.measurements.map((r) => asRow(norm.measurement(asRow(r)))),
    daily_logs: t.dailyLogs.map((r) => asRow(norm.dailyLog(asRow(r)))),
    studies: t.studies.map((r) => asRow(norm.study(asRow(r)))),
    study_results: t.studyResults.map((r) => asRow(norm.studyResult(asRow(r)))),
    study_attachments: t.studyAttachments.map((a) => {
      const row: Row = { id: a.id ?? 0, studyId: a.studyId, fileName: a.fileName, displayName: a.displayName, mime: a.mime }
      if (attachments === 'inline' && a.data) row.dataBase64 = toBase64(a.data)
      return row
    }),
    cycle_days: t.cycleDays.map((r) => asRow(norm.cycleDay(asRow(r)))),
    medications: t.medications.map((r) => asRow(norm.medication(asRow(r)))),
    med_intakes: t.medIntakes.map((r) => asRow(norm.medIntake(asRow(r)))),
    appointments: t.appointments.map((r) => asRow(norm.appointment(asRow(r)))),
    self_exams: t.selfExams.map((r) => asRow(norm.selfExam(asRow(r)))),
    vaccinations: t.vaccinations.map((r) => asRow(norm.vaccination(asRow(r)))),
  }
}

/** JSON → tablas. Tolera tablas o campos faltantes. Los adjuntos toman su contenido de `dataBase64` si viene. */
export function tablesFromJson(json: unknown): ProfileTables {
  if (!isRow(json)) throw new BackupError('El respaldo está dañado: no contiene datos.')
  const list = <T,>(key: string, f: (o: Row) => T): T[] => {
    const arr = json[key]
    return Array.isArray(arr) ? arr.filter(isRow).map(f) : []
  }
  return {
    profile: list('profile', norm.profile)[0] ?? null,
    symptoms: list('symptoms', norm.symptom),
    measurements: list('measurements', norm.measurement),
    dailyLogs: list('daily_logs', norm.dailyLog),
    studies: list('studies', norm.study),
    studyResults: list('study_results', norm.studyResult),
    studyAttachments: list('study_attachments', (o) => {
      const a = norm.attachment(o)
      const b64 = o.dataBase64
      if (typeof b64 === 'string' && b64.length > 0) {
        try { a.data = fromBase64(b64) } catch { a.data = null }
      }
      return a
    }),
    cycleDays: list('cycle_days', norm.cycleDay),
    medications: list('medications', norm.medication),
    medIntakes: list('med_intakes', norm.medIntake),
    appointments: list('appointments', norm.appointment),
    selfExams: list('self_exams', norm.selfExam),
    vaccinations: list('vaccinations', norm.vaccination),
  }
}

// ── IndexedDB ─────────────────────────────────────────────────────────────────────────────────────

/** Lee todo el contenido de la base de un perfil. Con `withFiles = false` los adjuntos vienen sin contenido. */
export async function readTables(db: SaviaDB, withFiles = true): Promise<ProfileTables> {
  const attachments = await db.studyAttachments.toArray()
  return {
    profile: (await db.profile.get(1)) ?? null,
    symptoms: await db.symptoms.toArray(),
    measurements: await db.measurements.toArray(),
    dailyLogs: await db.dailyLogs.toArray(),
    studies: await db.studies.toArray(),
    studyResults: await db.studyResults.toArray(),
    studyAttachments: attachments.map((a) => ({ ...a, data: withFiles && a.data ? toArrayBuffer(a.data) : null })),
    cycleDays: await db.cycleDays.toArray(),
    medications: await db.medications.toArray(),
    medIntakes: await db.medIntakes.toArray(),
    appointments: await db.appointments.toArray(),
    selfExams: await db.selfExams.toArray(),
    vaccinations: await db.vaccinations.toArray(),
  }
}

const withData = (a: BackupAttachment): StudyAttachment => ({ ...a, data: a.data ?? new ArrayBuffer(0) })

const dataStores = (db: SaviaDB) => [
  db.profile, db.symptoms, db.measurements, db.dailyLogs, db.studies, db.studyResults, db.studyAttachments, db.cycleDays,
  db.medications, db.medIntakes, db.appointments, db.selfExams, db.vaccinations,
]

/** Deja la base del perfil EXACTAMENTE como las tablas dadas (conserva los ids). Una sola transacción. */
export async function replaceTables(db: SaviaDB, t: ProfileTables): Promise<void> {
  await db.transaction('rw', dataStores(db), async () => {
    await Promise.all([
      db.symptoms.clear(), db.measurements.clear(), db.dailyLogs.clear(), db.studies.clear(), db.studyResults.clear(),
      db.studyAttachments.clear(), db.cycleDays.clear(), db.medications.clear(), db.medIntakes.clear(), db.appointments.clear(),
      db.selfExams.clear(), db.vaccinations.clear(),
    ])
    await db.profile.put({ ...(t.profile ?? emptyProfile()), id: 1 })
    // Un id 0 o ausente no es válido como llave: se deja que la base asigne uno nuevo.
    const keyed = <T extends { id?: number }>(rows: T[]): T[] =>
      rows.map((r) => { if (r.id && r.id > 0) return r; const { id: _id, ...rest } = r; return rest as T })
    await db.symptoms.bulkPut(keyed(t.symptoms))
    await db.measurements.bulkPut(keyed(t.measurements))
    await db.dailyLogs.bulkPut(t.dailyLogs)
    await db.studies.bulkPut(keyed(t.studies))
    await db.studyResults.bulkPut(keyed(t.studyResults))
    await db.studyAttachments.bulkPut(keyed(t.studyAttachments.map(withData)))
    await db.cycleDays.bulkPut(t.cycleDays)
    await db.medications.bulkPut(keyed(t.medications))
    await db.medIntakes.bulkPut(keyed(t.medIntakes))
    await db.appointments.bulkPut(keyed(t.appointments))
    await db.selfExams.bulkPut(keyed(t.selfExams))
    await db.vaccinations.bulkPut(keyed(t.vaccinations))
  })
}

/** Perfil combinado: se conserva lo actual y se rellenan los huecos con el respaldo. El tipo (persona/mascota) no cambia. */
export function mergeProfile(cur: Profile, b: Profile): Profile {
  const or = (a: string, c: string) => (a.trim() ? a : c)
  return {
    ...cur,
    name: or(cur.name, b.name), sex: cur.sex === 'X' ? b.sex : cur.sex, birthEpochDay: cur.birthEpochDay ?? b.birthEpochDay,
    heightCm: cur.heightCm ?? b.heightCm, goalWeightKg: cur.goalWeightKg ?? b.goalWeightKg, bloodType: or(cur.bloodType, b.bloodType),
    allergies: or(cur.allergies, b.allergies), conditions: or(cur.conditions, b.conditions), emergencyName: or(cur.emergencyName, b.emergencyName),
    emergencyPhone: or(cur.emergencyPhone, b.emergencyPhone), doctorName: or(cur.doctorName, b.doctorName), doctorPhone: or(cur.doctorPhone, b.doctorPhone),
    insurance: or(cur.insurance, b.insurance), notes: or(cur.notes, b.notes), species: or(cur.species, b.species), breed: or(cur.breed, b.breed),
    sterilized: cur.sterilized ?? b.sterilized, microchip: or(cur.microchip, b.microchip), markings: or(cur.markings, b.markings),
    relation: or(cur.relation, b.relation),
  }
}

export interface MergeCounts { added: number; updated: number; skipped: number; attachments: number }

/**
 * Combina un respaldo con lo que ya hay (misma regla que Android):
 *  - Ficha: se conserva lo actual y se rellenan los huecos.
 *  - Tablas "una fila por día" (ciclo, bienestar): gana el respaldo en los días que trae.
 *  - Las demás: se omiten duplicados exactos y el resto entra con id nuevo, remapeando studyId / medicationId.
 */
export async function mergeTables(db: SaviaDB, t: ProfileTables): Promise<MergeCounts> {
  const c: MergeCounts = { added: 0, updated: 0, skipped: 0, attachments: 0 }
  const noId = <T extends { id?: number }>(r: T): Omit<T, 'id'> => { const { id: _id, ...rest } = r; return rest }
  const key = (v: unknown) => JSON.stringify(v)
  await db.transaction('rw', dataStores(db), async () => {
    if (t.profile) {
      const cur = await db.profile.get(1)
      await db.profile.put({ ...(cur ? mergeProfile(cur, t.profile) : t.profile), id: 1 })
    }

    const curCycle = new Map((await db.cycleDays.toArray()).map((d) => [d.epochDay, key(norm.cycleDay(asRow(d)))]))
    for (const d of t.cycleDays) {
      const have = curCycle.get(d.epochDay)
      if (have === undefined) c.added++
      else if (have === key(norm.cycleDay(asRow(d)))) c.skipped++
      else c.updated++
    }
    await db.cycleDays.bulkPut(t.cycleDays)
    const curLogs = new Map((await db.dailyLogs.toArray()).map((d) => [d.epochDay, key(norm.dailyLog(asRow(d)))]))
    for (const d of t.dailyLogs) {
      const have = curLogs.get(d.epochDay)
      if (have === undefined) c.added++
      else if (have === key(norm.dailyLog(asRow(d)))) c.skipped++
      else c.updated++
    }
    await db.dailyLogs.bulkPut(t.dailyLogs)

    // Tablas simples
    const simple = async <T extends { id?: number }>(current: T[], incoming: T[], n: (o: Row) => T, add: (rows: T[]) => Promise<unknown>) => {
      const seen = new Set(current.map((r) => key(noId(n(asRow(r))))))
      const fresh: T[] = []
      for (const r of incoming) {
        const k = noId(n(asRow(r)))
        const ks = key(k)
        if (seen.has(ks)) c.skipped++
        else { seen.add(ks); fresh.push(k as T); c.added++ }
      }
      if (fresh.length) await add(fresh)
    }
    await simple(await db.symptoms.toArray(), t.symptoms, norm.symptom, (r) => db.symptoms.bulkAdd(r))
    await simple(await db.measurements.toArray(), t.measurements, norm.measurement, (r) => db.measurements.bulkAdd(r))
    await simple(await db.appointments.toArray(), t.appointments, norm.appointment, (r) => db.appointments.bulkAdd(r))
    await simple(await db.selfExams.toArray(), t.selfExams, norm.selfExam, (r) => db.selfExams.bulkAdd(r))
    await simple(await db.vaccinations.toArray(), t.vaccinations, norm.vaccination, (r) => db.vaccinations.bulkAdd(r))

    // Estudios (con resultados y adjuntos): se remapean las llaves foráneas.
    const curStudies = new Map((await db.studies.toArray()).map((s) => [key(noId(norm.study(asRow(s)))), s.id as number]))
    const studyMap = new Map<number, number>()
    for (const s of t.studies) {
      const k = noId(norm.study(asRow(s)))
      const existing = curStudies.get(key(k))
      if (existing !== undefined) { studyMap.set(s.id as number, existing); c.skipped++ }
      else { const id = await db.studies.add(k as Study); curStudies.set(key(k), id); studyMap.set(s.id as number, id); c.added++ }
    }
    const resSeen = new Set((await db.studyResults.toArray()).map((r) => key(noId(norm.studyResult(asRow(r))))))
    for (const r of t.studyResults) {
      const sid = studyMap.get(r.studyId)
      if (sid === undefined) continue
      const k = noId({ ...norm.studyResult(asRow(r)), studyId: sid })
      if (resSeen.has(key(k))) c.skipped++
      else { resSeen.add(key(k)); await db.studyResults.add(k as StudyResult); c.added++ }
    }
    const attKey = (a: { studyId: number; displayName: string; mime: string; data: ArrayBuffer | null }) =>
      `${a.studyId}|${a.displayName}|${a.mime}|${a.data?.byteLength ?? 0}`
    const attSeen = new Set((await db.studyAttachments.toArray()).map(attKey))
    for (const a of t.studyAttachments) {
      const sid = studyMap.get(a.studyId)
      if (sid === undefined) continue
      const moved = { ...a, studyId: sid }
      if (attSeen.has(attKey(moved))) continue
      attSeen.add(attKey(moved))
      await db.studyAttachments.add(noId(withData(moved)) as StudyAttachment)
      c.attachments++
    }

    // Medicamentos y tomas
    const curMeds = new Map((await db.medications.toArray()).map((m) => [key(noId(norm.medication(asRow(m)))), m.id as number]))
    const medMap = new Map<number, number>()
    for (const m of t.medications) {
      const k = noId(norm.medication(asRow(m)))
      const existing = curMeds.get(key(k))
      if (existing !== undefined) { medMap.set(m.id as number, existing); c.skipped++ }
      else { const id = await db.medications.add(k as Medication); curMeds.set(key(k), id); medMap.set(m.id as number, id); c.added++ }
    }
    const intSeen = new Set((await db.medIntakes.toArray()).map((i) => key(noId(norm.medIntake(asRow(i))))))
    for (const i of t.medIntakes) {
      const mid = medMap.get(i.medicationId)
      if (mid === undefined) continue
      const k = noId({ ...norm.medIntake(asRow(i)), medicationId: mid })
      if (intSeen.has(key(k))) c.skipped++
      else { intSeen.add(key(k)); await db.medIntakes.add(k as MedIntake); c.added++ }
    }
  })
  return c
}

// ── Ajustes del perfil ────────────────────────────────────────────────────────────────────────────

/** Ajustes de perfil con las claves de la web (camelCase). */
export type ProfilePrefs = Partial<Record<PrefKey, unknown>>

/** Clave web ↔ clave de Android (SharedPreferences). */
const ANDROID_PREF: Record<PrefKey, string> = {
  cycleEnabled: 'cycle_enabled', cycleLength: 'cycle_length', periodLength: 'period_length', lutealLength: 'luteal_length',
  showFertility: 'show_fertility', atlasSex: 'atlas_sex', activityLevel: 'activity_level',
}

/** Lee ajustes de perfil de un objeto que puede traer claves web o de Android; ignora lo desconocido y los tipos incorrectos. */
export function prefsFromJson(json: unknown): ProfilePrefs {
  const out: ProfilePrefs = {}
  if (!isRow(json)) return out
  for (const k of Object.keys(PREF_DEFAULTS) as PrefKey[]) {
    const v = json[k] ?? json[ANDROID_PREF[k]]
    if (v === null || v === undefined) continue
    const def = PREF_DEFAULTS[k]
    if (typeof def === 'boolean' && typeof v === 'boolean') out[k] = v
    else if (typeof def === 'number' && typeof v === 'number' && Number.isFinite(v)) out[k] = Math.trunc(v)
    else if (typeof def === 'string' && typeof v === 'string') out[k] = v
  }
  return out
}

/** Ajustes de perfil con las claves que entiende Android. */
export function prefsToAndroid(prefs: ProfilePrefs): Row {
  const out: Row = {}
  for (const k of Object.keys(PREF_DEFAULTS) as PrefKey[]) if (prefs[k] !== undefined) out[ANDROID_PREF[k]] = prefs[k]
  return out
}

export async function readPrefs(db: SaviaDB): Promise<ProfilePrefs> {
  const out: ProfilePrefs = { ...PREF_DEFAULTS }
  for (const r of await db.prefs.toArray()) if (r.key in PREF_DEFAULTS) out[r.key as PrefKey] = r.value
  return out
}

/** Escribe los ajustes dados (los que no vienen se quedan como están). */
export async function writePrefs(db: SaviaDB, prefs: ProfilePrefs): Promise<void> {
  const rows = (Object.keys(prefs) as PrefKey[])
    .filter((k) => k in PREF_DEFAULTS && prefs[k] !== undefined)
    .map((k) => ({ key: k as string, value: prefs[k] }))
  if (rows.length) await db.prefs.bulkPut(rows)
}
