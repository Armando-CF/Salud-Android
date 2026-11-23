import { useMemo, useState } from 'react'
import { useLiveQuery } from 'dexie-react-hooks'
import { Download, FileDown, Printer, Smartphone, Upload, UserRound } from 'lucide-react'
import { AVATAR_COLORS } from '../../data/catalogs'
import { profileSubtitle, useActive, useProfiles } from '../../data/profiles'
import { today } from '../../lib/dates'
import { Avatar, Button, Card, Chips, DateInput, InfoNote, Page, Toggle } from '../../ui'
import { attachmentsSize, sizeText } from './backup'
import { FORMATS, FORMAT_DESCRIPTION, dataIsEmpty, loadExportData, moduleDescription, moduleLabel, modulesFor, type ExportData, type ExportFormat, type ModuleKey } from './exportData'
import { daysSinceBackup, lastBackupText, useBackupFlow } from './flow'
import { BackupError } from './model'
import { ReportView } from './report'
import { buildTables, type ExportTable } from './tables'
import { buildExportFile } from './writers'
import './backup.css'

export {
  collectArchive, importAsNewProfiles, importIntoProfile, importReplaceAll, openBackupBytes, parseBackupJson, readAndroidZip, serializeWebBackup,
  summarize, undoLastReplace, writeAndroidZip, backupFileName,
} from './backup'
export type { Archive, ArchiveSummary, ArchivedProfile, ImportResult } from './backup'
export { BackupError } from './model'

const STORAGE_NOTE = 'Tus datos viven solo en este dispositivo y en este navegador: Savia no usa servidores. Si se borra la app o los datos del sitio, lo único que los recupera es un respaldo guardado fuera (Archivos / iCloud Drive, o enviado por correo).'

type RangeKey = 'all' | 'year' | 'm3' | 'custom'
const RANGES: readonly (readonly [RangeKey, string])[] = [['all', 'Todo'], ['year', 'Último año'], ['m3', 'Últimos 3 meses'], ['custom', 'Personalizado']]

/**
 * Botones listos para la sección de Configuración: "Exportar respaldo (.json)" e "Importar respaldo (.json)",
 * con sus diálogos, avisos de resultado y la fecha del último respaldo. Funciona sin props.
 */
export function BackupButtons() {
  const flow = useBackupFlow()
  const days = daysSinceBackup(flow.lastBackup)
  return (
    <div className="stack">
      <p className="small muted">
        {lastBackupText(flow.lastBackup)} El respaldo incluye todos los perfiles en un solo archivo .json.
      </p>
      {flow.lastBackup !== undefined && (days === null || days > 30) && (
        <InfoNote tone="warn">
          {days === null ? 'Haz tu primer respaldo: ' : `Han pasado ${days} días desde tu último respaldo: `}
          si se borran los datos del navegador, un respaldo guardado fuera es lo único que los recupera.
        </InfoNote>
      )}
      <Button icon={Download} full onClick={flow.exportAll}>Exportar respaldo (.json)</Button>
      <Button variant="outline" icon={Upload} full onClick={flow.pickImport}>Importar respaldo (.json)</Button>
      {flow.afterImport}
      {flow.dialogs}
    </div>
  )
}

/** Pantalla "Exportar y respaldo" (ruta /exportar). */
export function ExportPage() {
  const { info, db, isPet } = useActive()
  const flow = useBackupFlow()
  const profiles = useProfiles()
  const many = profiles.length > 1
  const size = useLiveQuery(() => attachmentsSize(profiles.map((p) => p.id)), [profiles.map((p) => p.id).join(',')])
  const days = daysSinceBackup(flow.lastBackup)

  const modules = useMemo(() => modulesFor(isPet), [isPet])
  const [selected, setSelected] = useState<ModuleKey[]>(() => modulesFor(isPet).map((m) => m.key))
  const [format, setFormat] = useState<ExportFormat>('XLSX')
  const [range, setRange] = useState<RangeKey>('all')
  const [from, setFrom] = useState(() => today() - 30)
  const [to, setTo] = useState(() => today())
  const [report, setReport] = useState<{ data: ExportData; tables: ExportTable[] } | null>(null)

  const toggle = (k: ModuleKey) => setSelected((s) => (s.includes(k) ? s.filter((x) => x !== k) : [...s, k]))
  const rangeInvalid = range === 'custom' && from > to

  const generate = () => flow.run('Preparando la exportación…', async () => {
    const t = today()
    const options = {
      // Se conserva el orden estable de los módulos.
      modules: modules.map((m) => m.key).filter((k) => selected.includes(k)),
      fromDay: range === 'year' ? t - 365 : range === 'm3' ? t - 90 : range === 'custom' ? from : null,
      toDay: range === 'custom' ? to : null,
    }
    const data = await loadExportData(db, options)
    if (dataIsEmpty(data)) throw new BackupError('No hay nada que exportar con esas secciones y ese periodo. Prueba con "Todo" o con más secciones.')
    if (format === 'PRINT') {
      setReport({ data, tables: buildTables(data) })
      return
    }
    const file = buildExportFile(format, data, many ? info.name : undefined)
    setReport(null)
    flow.setReady({
      name: file.name, mime: file.mime, blob: new Blob([file.bytes as BlobPart], { type: file.mime }), title: 'Tu archivo está listo',
      detail: `${FORMATS.find((f) => f[0] === format)?.[1]} · ${sizeText(file.bytes.length)}`,
    })
  }, 'No se pudo generar la exportación.')

  if (report) {
    return (
      <Page key="reporte" title="Vista previa del reporte" back={() => setReport(null)}>
        <div className="no-print stack">
          <Button icon={Printer} full onClick={() => window.print()}>Imprimir o guardar como PDF</Button>
          <InfoNote>
            En iPhone: en la pantalla de impresión, separa dos dedos (pellizco hacia afuera) sobre la vista previa para abrirla como PDF y luego toca Compartir → «Guardar en Archivos».
            En computadora elige «Guardar como PDF» como impresora.
          </InfoNote>
        </div>
        <ReportView data={report.data} tables={report.tables} />
        {flow.dialogs}
      </Page>
    )
  }

  return (
    <Page key="principal" title="Exportar y respaldo">
      <Card>
        <div className="bk-who">
          <Avatar name={flow.activeName} color={AVATAR_COLORS[info.avatarColor % AVATAR_COLORS.length]} pet={isPet} size={44} />
          <div className="grow">
            <div className="small muted">Exportando datos de:</div>
            <div className="t ellipsis">{flow.activeName}{profileSubtitle(info) ? ` · ${profileSubtitle(info)}` : ''}</div>
          </div>
        </div>
        {many && <p className="small muted">Las exportaciones para leer son de este perfil; el respaldo completo incluye los {profiles.length} perfiles. Para exportar otro perfil, cámbiate a él.</p>}
      </Card>

      <InfoNote>{STORAGE_NOTE}</InfoNote>

      <Card title="Respaldo">
        <p className="small muted">{lastBackupText(flow.lastBackup)}</p>
        {flow.lastBackup !== undefined && (days === null || days > 30) && (
          <InfoNote tone="warn">
            {days === null ? 'Todavía no tienes un respaldo. ' : `Han pasado ${days} días desde tu último respaldo. `}
            Te recomendamos hacer uno y guardarlo fuera de este dispositivo.
          </InfoNote>
        )}
        <Toggle
          label="Incluir fotos y archivos adjuntos"
          sub={size && size.count > 0 ? `${size.count} ${size.count === 1 ? 'archivo' : 'archivos'}, aprox. ${sizeText(Math.ceil(size.bytes * 1.37))} en el respaldo` : 'Aún no hay archivos adjuntos en tus estudios'}
          checked={flow.includeFiles}
          onChange={flow.setIncludeFiles}
        />
        <Button icon={Download} full onClick={flow.exportAll}>Exportar respaldo (.json)</Button>
        <p className="small muted">Un solo archivo con {many ? `los ${profiles.length} perfiles` : 'todo tu historial'}. Sirve para recuperar tus datos aquí o en otro dispositivo.</p>
        {many && <Button variant="outline" icon={UserRound} full onClick={flow.exportActive}>Respaldo solo de «{flow.activeName}»</Button>}
        <div className="bk-divider" />
        <Button variant="outline" icon={Upload} full onClick={flow.pickImport}>Importar respaldo (.json)</Button>
        <p className="small muted">Antes de cambiar nada verás qué trae el archivo y podrás elegir entre agregarlo como perfiles nuevos o reemplazar.</p>
        {flow.afterImport}
      </Card>

      <Card title="Savia para Android">
        <p className="small muted">¿Vienes de la app de Android o vas hacia ella? Los respaldos son compatibles.</p>
        <Button variant="outline" icon={Smartphone} full onClick={flow.pickImport}>Importar respaldo de Android (.zip)</Button>
        <Button variant="outline" icon={FileDown} full onClick={flow.exportAndroid}>Exportar para Savia Android (.zip)</Button>
        <p className="small muted">La exportación para Android es del perfil «{flow.activeName}», con sus archivos adjuntos.</p>
      </Card>

      <Card title="Exportar para leer o compartir">
        <div className="field">
          <span className="field-label">Formato</span>
          <Chips options={FORMATS} value={format} onChange={(v) => v && setFormat(v)} />
          <span className="bk-format">{FORMAT_DESCRIPTION[format]}</span>
        </div>

        <div className="field">
          <div className="bk-row-actions">
            <span className="field-label grow">Qué incluir · {selected.length} de {modules.length}</span>
            <Button variant="text" small onClick={() => setSelected(modules.map((m) => m.key))}>Todas</Button>
            <Button variant="text" small onClick={() => setSelected([])}>Ninguna</Button>
          </div>
          {!isPet && <Button variant="tonal" small onClick={() => { setSelected(['CICLO']); setRange('all') }}>Solo ciclo menstrual (todo el historial)</Button>}
          <div>
            {modules.map((m) => (
              <label key={m.key} className="bk-check">
                <input type="checkbox" checked={selected.includes(m.key)} onChange={() => toggle(m.key)} />
                <span className="grow">
                  <span className="t">{moduleLabel(m, isPet)}</span>
                  <span className="s">{moduleDescription(m, isPet)}</span>
                </span>
              </label>
            ))}
          </div>
        </div>

        <div className="field">
          <span className="field-label">Periodo</span>
          <Chips options={RANGES} value={range} onChange={(v) => v && setRange(v)} />
          {range === 'custom' && (
            <div className="row" style={{ alignItems: 'flex-start' }}>
              <div className="grow"><DateInput label="Desde" value={from} max={today()} onChange={setFrom} /></div>
              <div className="grow"><DateInput label="Hasta" value={to} max={today()} onChange={setTo} /></div>
            </div>
          )}
          {rangeInvalid && <span className="hint error">La fecha inicial debe ser anterior a la final.</span>}
        </div>

        <Button icon={format === 'PRINT' ? Printer : FileDown} full disabled={selected.length === 0 || rangeInvalid} onClick={generate}>
          {format === 'PRINT' ? 'Ver reporte' : 'Generar archivo'}
        </Button>
      </Card>

      {flow.dialogs}
    </Page>
  )
}
