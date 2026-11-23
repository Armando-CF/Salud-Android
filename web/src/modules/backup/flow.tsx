// Flujo compartido de respaldo (exportar, importar, deshacer) con sus diálogos.
// Lo usan tanto la pantalla "Exportar y respaldo" como los botones de Ajustes.

import { useCallback, useRef, useState, type ReactNode } from 'react'
import { useLiveQuery } from 'dexie-react-hooks'
import { Download, Undo2 } from 'lucide-react'
import { AVATAR_COLORS, PetSpecies } from '../../data/catalogs'
import { dbFor } from '../../data/db'
import { setSetting, useSetting } from '../../data/prefs'
import { displayName, profileIds, useActive } from '../../data/profiles'
import { Fmt, today } from '../../lib/dates'
import { saveFile } from '../../lib/files'
import { Avatar, Button, ConfirmDialog, Dialog, InfoNote, Sheet, useToast } from '../../ui'
import {
  androidFileName, backupFileName, clearLastResult, collectArchive, discardSafety, getSafetyInfo, importAsNewProfiles, importIntoProfile,
  importReplaceAll, lastResult, openBackupBytes, rememberResult, serializeWebBackup, sizeText, summarize, undoLastReplace, writeAndroidZip,
  type Archive, type ArchiveSummary, type ImportResult,
} from './backup'
import { BackupError, recordCount } from './model'

/** Archivo ya generado, a la espera de que la persona toque "Guardar archivo" (iOS exige un toque directo). */
export interface ReadyFile {
  name: string
  mime: string
  blob: Blob
  title: string
  detail: string
  /** Cuenta como respaldo completo (actualiza la fecha del último respaldo). */
  countsAsBackup?: boolean
}

const errorText = (e: unknown, fallback: string): string => (e instanceof BackupError ? e.message : fallback)
const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`
const DAY = 86_400_000

/** Días completos desde el último respaldo, o null si nunca se ha hecho. */
export const daysSinceBackup = (lastBackup: number | undefined): number | null => (!lastBackup ? null : Math.max(0, Math.floor((Date.now() - lastBackup) / DAY)))

export function lastBackupText(lastBackup: number | undefined): string {
  if (lastBackup === undefined) return ''
  const d = daysSinceBackup(lastBackup)
  if (d === null) return 'Aún no has hecho ningún respaldo.'
  return `Último respaldo: ${Fmt.dateTime(lastBackup)}${d >= 2 ? ` (hace ${d} días)` : ''}.`
}

type Choice = { kind: 'add' } | { kind: 'replaceAll' } | { kind: 'profile'; mode: 'replace' | 'merge' }

export function useBackupFlow() {
  const { info } = useActive()
  const toast = useToast()
  const [lastBackup] = useSetting('lastBackup')
  const [busy, setBusy] = useState<string | null>(null)
  const [ready, setReady] = useState<ReadyFile | null>(null)
  const [incoming, setIncoming] = useState<{ archive: Archive; summary: ArchiveSummary; fresh: boolean } | null>(null)
  const [confirm, setConfirm] = useState<Choice | null>(null)
  const [undoAsk, setUndoAsk] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [includeFiles, setIncludeFiles] = useState(true)
  const inputRef = useRef<HTMLInputElement>(null)
  const safety = useLiveQuery(() => getSafetyInfo(), [])
  const result = useLiveQuery(() => lastResult(), [])
  const activeName = displayName(info)

  const run = useCallback(async (label: string, fn: () => Promise<void>, fallback: string) => {
    setBusy(label)
    try {
      await fn()
    } catch (e) {
      setError(errorText(e, fallback))
    } finally {
      setBusy(null)
    }
  }, [])

  /** Respaldo .json de todos los perfiles. */
  const exportAll = useCallback(() => run('Preparando el respaldo…', async () => {
    const a = await collectArchive('all', includeFiles)
    const s = summarize(a)
    const blob = new Blob([serializeWebBackup(a, includeFiles)], { type: 'application/json' })
    setReady({
      name: backupFileName(today()), mime: 'application/json', blob, title: 'Tu respaldo está listo', countsAsBackup: true,
      detail: `${plural(s.profiles.length, 'perfil', 'perfiles')}, ${plural(s.records, 'registro', 'registros')}${includeFiles && s.attachments ? ` y ${plural(s.attachments, 'archivo adjunto', 'archivos adjuntos')}` : ''} · ${sizeText(blob.size)}`,
    })
  }, 'No se pudo crear el respaldo.'), [run, includeFiles])

  /** Respaldo .json solo del perfil activo. */
  const exportActive = useCallback(() => run('Preparando el respaldo…', async () => {
    const a = await collectArchive(info.id, includeFiles)
    const blob = new Blob([serializeWebBackup(a, includeFiles)], { type: 'application/json' })
    const onlyOne = (await profileIds()).length === 1
    setReady({
      name: backupFileName(today(), activeName), mime: 'application/json', blob, title: 'Respaldo de este perfil listo', countsAsBackup: onlyOne,
      detail: `${activeName}: ${plural(recordCount(a.profiles[0].tables), 'registro', 'registros')} · ${sizeText(blob.size)}`,
    })
  }, 'No se pudo crear el respaldo.'), [run, info.id, activeName, includeFiles])

  /** Respaldo del perfil activo en el formato de Savia para Android (.zip). */
  const exportAndroid = useCallback(() => run('Preparando el archivo…', async () => {
    const a = await collectArchive(info.id, true)
    const bytes = writeAndroidZip(a.profiles[0])
    const blob = new Blob([bytes as BlobPart], { type: 'application/zip' })
    setReady({
      name: androidFileName(activeName), mime: 'application/zip', blob, title: 'Archivo para Savia Android listo',
      detail: `${activeName}: ${plural(recordCount(a.profiles[0].tables), 'registro', 'registros')} · ${sizeText(blob.size)}. En tu teléfono Android: Más → Exportar y respaldo → Restaurar.`,
    })
  }, 'No se pudo crear el archivo.'), [run, info.id, activeName])

  const saveReady = useCallback(async () => {
    if (!ready) return
    // Respuesta directa al toque: aquí no puede haber esperas antes de compartir (requisito de iOS).
    const r = await saveFile(ready.name, ready.blob, ready.mime)
    if (r === 'cancelled') return
    if (ready.countsAsBackup) await setSetting('lastBackup', Date.now())
    toast(r === 'shared' ? 'Listo. Guárdalo en un lugar seguro.' : `Se descargó ${ready.name}`)
    setReady(null)
  }, [ready, toast])

  /** Abre el selector de archivos del sistema. */
  const pickImport = useCallback(() => {
    if (inputRef.current) { inputRef.current.value = ''; inputRef.current.click() }
  }, [])

  const onFile = useCallback((file: File | undefined) => {
    if (!file) return
    void run('Leyendo el archivo…', async () => {
      const archive = openBackupBytes(new Uint8Array(await file.arrayBuffer()))
      // ¿Dispositivo recién estrenado? (un solo perfil, sin nombre y sin registros): lo natural es restaurar encima.
      const ids = await profileIds()
      let fresh = false
      if (ids.length === 1) {
        const db = dbFor(1)
        const p = await db.profile.get(1)
        const counts = await Promise.all([db.symptoms, db.measurements, db.dailyLogs, db.studies, db.cycleDays, db.medications, db.appointments, db.selfExams, db.vaccinations].map((t) => t.count()))
        fresh = !(p?.name ?? '').trim() && counts.every((n) => n === 0)
      }
      setIncoming({ archive, summary: summarize(archive), fresh })
    }, 'No se pudo leer el archivo.')
  }, [run])

  const apply = useCallback((choice: Choice) => {
    const inc = incoming
    if (!inc) return
    setConfirm(null)
    void run('Importando…', async () => {
      let r: ImportResult
      if (choice.kind === 'add') r = await importAsNewProfiles(inc.archive)
      else if (choice.kind === 'replaceAll') r = await importReplaceAll(inc.archive, { safety: !inc.fresh })
      else r = await importIntoProfile(inc.archive.profiles[0], info.id, choice.mode, { safety: !inc.fresh })
      // La pantalla puede recrearse si cambió el perfil activo: el resultado se guarda para mostrarlo después.
      await rememberResult(r.text)
      setIncoming(null)
      toast(r.text)
    }, 'No se pudo importar el respaldo. Tus datos no cambiaron.')
  }, [incoming, run, info.id, toast])

  const undo = useCallback(() => {
    setUndoAsk(false)
    void run('Recuperando…', async () => {
      const r = await undoLastReplace()
      await rememberResult(r.text)
      toast(r.text)
    }, 'No se pudo deshacer.')
  }, [run, toast])

  const sum = incoming?.summary
  const backupKind = sum?.profiles[0]?.kind ?? 'person'
  const sameKind = backupKind === info.kind
  const isAll = sum?.kind === 'all'

  const dialogs: ReactNode = (
    <>
      {/* Selector de archivo oculto (también lo usan las pruebas automáticas). */}
      <input
        ref={inputRef}
        type="file"
        hidden
        data-testid="backup-file-input"
        accept=".json,.zip,application/json,application/zip"
        onChange={(e) => { const f = e.target.files?.[0]; onFile(f); e.target.value = '' }}
      />

      <Dialog open={busy !== null} onClose={() => {}} title={busy ?? ''}>
        <p className="muted">Un momento…</p>
      </Dialog>

      <Dialog
        open={ready !== null}
        onClose={() => setReady(null)}
        title={ready?.title ?? ''}
        actions={<Button variant="text" onClick={() => setReady(null)}>Cerrar</Button>}
      >
        <p className="muted">{ready?.detail}</p>
        <p className="small muted bk-break">{ready?.name}</p>
        <Button icon={Download} full onClick={() => { void saveReady() }}>Guardar archivo</Button>
        <p className="small muted">En iPhone se abre el menú de compartir: elige «Guardar en Archivos» (iCloud Drive) o envíatelo por correo.</p>
      </Dialog>

      <Dialog open={error !== null} onClose={() => setError(null)} title="No se pudo completar" actions={<Button variant="text" onClick={() => setError(null)}>Entendido</Button>}>
        <p className="muted">{error}</p>
      </Dialog>

      <Sheet open={incoming !== null && busy === null} onClose={() => setIncoming(null)} title="Importar respaldo">
        {sum && (
          <>
            <p className="muted">
              {sum.source === 'android' ? 'Respaldo de Savia para Android' : 'Respaldo de Savia'}
              {sum.createdAtMillis > 0 ? ` del ${Fmt.dateTime(sum.createdAtMillis)}` : ''}. Contiene:
            </p>
            <div className="bk-list">
              {sum.profiles.map((p) => (
                <div key={p.originalId} className="list-row">
                  <Avatar name={p.name || '?'} color={AVATAR_COLORS[p.originalId % AVATAR_COLORS.length]} pet={p.kind === 'pet'} size={38} />
                  <div className="grow">
                    <div style={{ fontWeight: 600 }} className="ellipsis">{p.name || (p.kind === 'pet' ? 'Mascota' : 'Sin nombre')}</div>
                    <div className="small muted">
                      {p.kind === 'pet' ? PetSpecies.label(p.species) : 'Persona'} · {plural(p.records, 'registro', 'registros')}
                      {p.attachments > 0 ? ` · ${plural(p.attachments, 'adjunto', 'adjuntos')}${p.attachmentsMissing ? ` (${p.attachmentsMissing} sin archivo)` : ''}` : ''}
                    </div>
                  </div>
                </div>
              ))}
            </div>

            {isAll ? (
              incoming.fresh ? (
                <>
                  <Button full onClick={() => apply({ kind: 'replaceAll' })}>Restaurar todo en este dispositivo</Button>
                  <p className="small muted">Este dispositivo todavía no tiene datos: quedará igual que el respaldo.</p>
                  <Button variant="outline" full onClick={() => apply({ kind: 'add' })}>Agregar como perfiles nuevos</Button>
                </>
              ) : (
                <>
                  <Button full onClick={() => apply({ kind: 'add' })}>Agregar como perfiles nuevos</Button>
                  <p className="small muted">La opción segura: no cambia nada de lo que ya tienes. Cada perfil del respaldo aparece como un perfil adicional.</p>
                  <Button variant="danger" full onClick={() => setConfirm({ kind: 'replaceAll' })}>Reemplazar todo</Button>
                  <p className="small muted">Deja este dispositivo igual que el respaldo: se quitan los perfiles y datos actuales. Antes guardamos una copia interna para que puedas deshacerlo.</p>
                </>
              )
            ) : (
              <>
                {incoming.fresh && sameKind ? (
                  <>
                    <Button full onClick={() => apply({ kind: 'profile', mode: 'replace' })}>Restaurar en este dispositivo</Button>
                    <p className="small muted">Este dispositivo todavía no tiene datos: tu perfil quedará igual que el respaldo.</p>
                    <Button variant="outline" full onClick={() => apply({ kind: 'add' })}>Agregar como perfil nuevo</Button>
                  </>
                ) : (
                  <>
                    <Button full onClick={() => apply({ kind: 'add' })}>Agregar como perfil nuevo</Button>
                    <p className="small muted">La opción segura: no cambia nada de lo que ya tienes.</p>
                    {sameKind ? (
                      <>
                        <Button variant="outline" full onClick={() => apply({ kind: 'profile', mode: 'merge' })}>Combinar con «{activeName}»</Button>
                        <p className="small muted">Conserva lo que hay en «{activeName}» y agrega lo que falte. En el ciclo y el bienestar diario gana el respaldo en los días que trae.</p>
                        <Button variant="danger" full onClick={() => setConfirm({ kind: 'profile', mode: 'replace' })}>Reemplazar los datos de «{activeName}»</Button>
                        <p className="small muted">Borra lo actual de «{activeName}» y deja lo del respaldo. Antes guardamos una copia interna para que puedas deshacerlo.</p>
                      </>
                    ) : (
                      <InfoNote>
                        Este respaldo es de {backupKind === 'pet' ? 'una mascota' : 'una persona'} y el perfil que estás viendo es de {info.kind === 'pet' ? 'una mascota' : 'una persona'}: por eso solo puede entrar como perfil nuevo.
                      </InfoNote>
                    )}
                  </>
                )}
              </>
            )}
          </>
        )}
      </Sheet>

      <ConfirmDialog
        open={confirm !== null}
        title={confirm?.kind === 'replaceAll' ? '¿Reemplazar todo?' : `¿Reemplazar los datos de «${activeName}»?`}
        text={confirm?.kind === 'replaceAll'
          ? 'Se quitarán TODOS los perfiles y datos que hay ahora en este dispositivo y quedará lo del respaldo. Guardaremos una copia interna para que puedas deshacerlo desde esta misma pantalla.'
          : 'Se borrará lo que hay ahora en este perfil y quedará lo del respaldo. Guardaremos una copia interna para que puedas deshacerlo.'}
        confirmLabel="Sí, reemplazar"
        onClose={() => setConfirm(null)}
        onConfirm={() => confirm && apply(confirm)}
      />

      <ConfirmDialog
        open={undoAsk}
        title="¿Deshacer el último reemplazo?"
        text={safety ? `Se recuperará lo que había antes (${plural(safety.profiles, 'perfil', 'perfiles')}, ${plural(safety.records, 'registro', 'registros')}; copia del ${Fmt.dateTime(safety.createdAtMillis)}) y se perderá lo que hayas registrado después.` : ''}
        confirmLabel="Sí, deshacer"
        onClose={() => setUndoAsk(false)}
        onConfirm={undo}
      />
    </>
  )

  /** Aviso de la última importación y botón para deshacer el último reemplazo (si aplica). */
  const afterImport: ReactNode = (
    <>
      {result && (
        <InfoNote>
          {result.text}{' '}
          <button type="button" className="bk-link" onClick={() => { void clearLastResult() }}>Ocultar</button>
        </InfoNote>
      )}
      {safety && (
        <div className="bk-undo">
          <div className="small muted">Copia de seguridad interna del {Fmt.dateTime(safety.createdAtMillis)} (antes de tu último reemplazo).</div>
          <div className="row wrap">
            <Button variant="outline" small icon={Undo2} onClick={() => setUndoAsk(true)}>Deshacer el último reemplazo</Button>
            <Button variant="text" small onClick={() => { void discardSafety() }}>Descartar copia</Button>
          </div>
        </div>
      )}
    </>
  )

  return { lastBackup, busy, includeFiles, setIncludeFiles, exportAll, exportActive, exportAndroid, pickImport, setReady, setError, run, dialogs, afterImport, activeName }
}
