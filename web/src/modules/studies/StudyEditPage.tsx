import { useEffect, useMemo, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useLiveQuery } from 'dexie-react-hooks'
import { ListPlus, Paperclip, Plus, Trash2 } from 'lucide-react'
import { StudyCategories } from '../../data/catalogs'
import { useDb } from '../../data/profiles'
import type { Study, StudyResult } from '../../data/types'
import { Fmt, toNumber, today } from '../../lib/dates'
import { pickFiles } from '../../lib/files'
import {
  Button, Card, Chips, ConfirmDialog, DateInput, InfoNote, NumberInput, OptionalDateInput, Page, Pill, Sheet, TextArea, TextInput,
  useBack, useToast,
} from '../../ui'
import { StudyCatalog, flag, flagLabel, normalizeName } from './catalog'
import { VACCINE, useStudyProfile } from './profile'
import { AttachmentTile, BIG_FILE_BYTES, VaccinesMovedNote, flagColor, sizeText, useAttachmentViewer, type AttView } from './shared'

/** Fila editable de un resultado (todo como texto mientras se edita). */
interface RowState {
  key: number
  name: string
  /** true = resultado en texto ("Negativo"); false = numérico. */
  isText: boolean
  value: string
  text: string
  unit: string
  refMin: string
  refMax: string
  notes: string
}

const emptyRow = (key: number): RowState => ({ key, name: '', isText: false, value: '', text: '', unit: '', refMin: '', refMax: '', notes: '' })
const rowIsBlank = (r: RowState): boolean => r.name.trim() === '' && r.value.trim() === '' && r.text.trim() === ''
const fieldNum = (v: number | null | undefined): string => (v == null ? '' : Fmt.num(v, 6))

function toRow(r: StudyResult, key: number): RowState {
  return {
    key, name: r.name, isText: r.value == null && r.textValue.trim() !== '', value: fieldNum(r.value), text: r.textValue, unit: r.unit,
    refMin: fieldNum(r.refMin), refMax: fieldNum(r.refMax), notes: r.notes,
  }
}

function toResult(r: RowState, studyId: number): StudyResult {
  return {
    studyId,
    name: r.name.trim(),
    value: r.isText ? null : toNumber(r.value),
    textValue: r.isText ? r.text.trim() : '',
    unit: r.isText ? '' : r.unit.trim(),
    refMin: r.isText ? null : toNumber(r.refMin),
    refMax: r.isText ? null : toNumber(r.refMax),
    notes: r.notes,
  }
}

const MIME_BY_EXT: Record<string, string> = { pdf: 'application/pdf', jpg: 'image/jpeg', jpeg: 'image/jpeg', png: 'image/png', webp: 'image/webp', gif: 'image/gif', heic: 'image/heic', heif: 'image/heif' }

/** Alta y edición de un estudio. Rutas: /estudios/nuevo y /estudios/:id/editar. */
export function StudyEditPage() {
  const params = useParams()
  const id = params.id !== undefined ? Number(params.id) : null
  const db = useDb()
  const back = useBack()
  const toast = useToast()
  const sp = useStudyProfile()

  const [loaded, setLoaded] = useState(id === null)
  const [title, setTitle] = useState('')
  const [category, setCategory] = useState('lab')
  const [day, setDay] = useState(today())
  const [place, setPlace] = useState('')
  const [doctor, setDoctor] = useState('')
  const [status, setStatus] = useState('')
  const [summary, setSummary] = useState('')
  const [followUp, setFollowUp] = useState<number | null>(null)
  const [rows, setRows] = useState<RowState[]>([])
  const [atts, setAtts] = useState<AttView[]>([])
  const [removedAtts, setRemovedAtts] = useState<number[]>([])
  const [dirty, setDirty] = useState(false)
  const [titleError, setTitleError] = useState(false)
  const [errorMsg, setErrorMsg] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [showDiscard, setShowDiscard] = useState(false)
  const [showGroups, setShowGroups] = useState(false)
  const [focusedKey, setFocusedKey] = useState<number | null>(null)
  const nextKey = useRef(1)
  const { open, viewer } = useAttachmentViewer()

  const pastResults = useLiveQuery(() => db.studyResults.orderBy('id').toArray(), [db]) ?? []
  const pastNames = useMemo(() => {
    const seen = new Set<string>()
    const out: string[] = []
    for (const r of pastResults) {
      const n = r.name.trim(), k = normalizeName(n)
      if (n && !seen.has(k)) { seen.add(k); out.push(n) }
    }
    return out
  }, [pastResults])

  // Carga única del estudio existente.
  useEffect(() => {
    if (id === null) return
    let alive = true
    ;(async () => {
      const s = Number.isFinite(id) ? await db.studies.get(id) : undefined
      if (!alive) return
      if (!s) { back(); return }
      const rs = await db.studyResults.where('studyId').equals(id).sortBy('id')
      const as = await db.studyAttachments.where('studyId').equals(id).sortBy('id')
      if (!alive) return
      setTitle(s.title); setCategory(s.category); setDay(s.epochDay); setPlace(s.place); setDoctor(s.doctor)
      setStatus(s.status); setSummary(s.summary); setFollowUp(s.followUpEpochDay)
      setRows(rs.map((r) => toRow(r, nextKey.current++)))
      setAtts(as.map((a) => ({ key: `s${a.id}`, id: a.id, displayName: a.displayName, mime: a.mime, data: a.data })))
      setLoaded(true)
    })()
    return () => { alive = false }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [db, id])

  const touch = () => setDirty(true)

  /** Unidad y rango sugeridos: primero lo último capturado con ese nombre, luego el catálogo. */
  const rowFor = (name: string): RowState => {
    const d = sp.defaultsFor(name, pastResults)
    return { ...emptyRow(nextKey.current++), name, unit: d?.unit ?? '', refMin: fieldNum(d?.refMin), refMax: fieldNum(d?.refMax) }
  }

  const addGroup = (groupName: string, current: RowState[] = rows) => {
    const group = sp.groups.find((g) => g.name === groupName)
    if (!group) return
    const kept = current.filter((r) => !rowIsBlank(r))
    const existing = new Set(kept.map((r) => normalizeName(r.name)))
    setRows([...kept, ...group.params.filter((p) => !existing.has(normalizeName(p))).map(rowFor)])
    touch()
  }

  const updateRow = (key: number, patch: Partial<RowState>) => { setRows((rs) => rs.map((r) => (r.key === key ? { ...r, ...patch } : r))); touch() }

  const pickName = (row: RowState, name: string) => {
    const d = sp.defaultsFor(name, pastResults)
    updateRow(row.key, {
      name,
      unit: row.unit.trim() !== '' ? row.unit : d?.unit ?? '',
      refMin: row.refMin.trim() !== '' ? row.refMin : fieldNum(d?.refMin),
      refMax: row.refMax.trim() !== '' ? row.refMax : fieldNum(d?.refMax),
    })
  }

  const requestExit = () => { if (dirty) setShowDiscard(true); else back() }

  const addFiles = async () => {
    const files = await pickFiles('image/*,application/pdf', true)
    if (files.length === 0) return
    const added: AttView[] = []
    let failed = 0
    let big = 0
    for (const f of files) {
      try {
        const data = await f.arrayBuffer()
        const ext = f.name.split('.').pop()?.toLowerCase() ?? ''
        added.push({ key: `n${nextKey.current++}`, displayName: f.name || 'archivo', mime: f.type || MIME_BY_EXT[ext] || 'application/octet-stream', data })
        if (data.byteLength > BIG_FILE_BYTES) big = Math.max(big, data.byteLength)
      } catch {
        failed++
      }
    }
    if (added.length > 0) { setAtts((a) => [...a, ...added]); touch() }
    if (failed > 0) setErrorMsg('No se pudo leer alguno de los archivos.')
    else if (big > 0) toast(`Ese archivo pesa ${sizeText(big)}: ocupará ese espacio en tu dispositivo y en el respaldo.`)
  }

  const removeAtt = (a: AttView) => {
    setAtts((list) => list.filter((x) => x.key !== a.key))
    if (a.id !== undefined) setRemovedAtts((r) => [...r, a.id as number])
    touch()
  }

  const save = async () => {
    if (saving || !loaded) return
    const noTitle = title.trim() === ''
    setTitleError(noTitle)
    const unnamed = rows.some((r) => r.name.trim() === '' && !rowIsBlank(r))
    const badNumber = rows.some((r) => !r.isText && [r.value, r.refMin, r.refMax].some((x) => x.trim() !== '' && toNumber(x) === null))
    const msg = noTitle ? 'Escribe el nombre del estudio.' : unnamed ? 'Hay un resultado sin nombre de parámetro.' : badNumber ? 'Revisa los números de los resultados.' : null
    setErrorMsg(msg)
    if (msg) return
    setSaving(true)
    const study: Study = {
      epochDay: day, title: title.trim(), category, place: place.trim(), doctor: doctor.trim(), status, summary: summary.trim(), followUpEpochDay: followUp,
    }
    const rowList = rows.filter((r) => r.name.trim() !== '')
    const newAtts = atts.filter((a) => a.id === undefined)
    try {
      await db.transaction('rw', db.studies, db.studyResults, db.studyAttachments, async () => {
        const sid = id === null ? await db.studies.add(study) : (await db.studies.put({ ...study, id }), id)
        await db.studyResults.where('studyId').equals(sid).delete()
        if (rowList.length) await db.studyResults.bulkAdd(rowList.map((r) => toResult(r, sid)))
        for (const a of newAtts) await db.studyAttachments.add({ studyId: sid, fileName: a.displayName, displayName: a.displayName, mime: a.mime, data: a.data })
        if (removedAtts.length) await db.studyAttachments.bulkDelete(removedAtts)
      })
      toast(id === null ? 'Estudio guardado' : 'Cambios guardados')
      back()
    } catch {
      setSaving(false)
      setErrorMsg('No se pudo guardar. Si adjuntaste archivos muy grandes, puede faltar espacio en el dispositivo.')
    }
  }

  const tq = normalizeName(title)
  const titleSuggestions = useMemo(
    () => sp.titlesFor(category).filter((s) => normalizeName(s) !== tq && (tq === '' || normalizeName(s).includes(tq))).slice(0, 6),
    [sp, category, tq],
  )

  return (
    <Page
      title={id === null ? 'Nuevo estudio' : 'Editar estudio'}
      back={requestExit}
      actions={<button type="button" className="text-btn" disabled={!loaded || saving} onClick={() => { void save() }}>Guardar</button>}
    >
      {loaded && (
        <div className="st-page">
          {/* ---- Datos generales ---- */}
          <Card title="Datos generales">
            <div className="field">
              <div className="field-label">Tipo</div>
              <Chips options={sp.categories(category)} value={category} onChange={(c) => { if (c) { setCategory(c); touch() } }} />
            </div>
            {category === VACCINE && <VaccinesMovedNote />}
            <TextInput
              label="Nombre del estudio"
              value={title}
              placeholder={sp.isPet ? 'Ej. Consulta veterinaria' : 'Ej. Química sanguínea'}
              error={titleError && 'Este dato es necesario'}
              onChange={(v) => { setTitle(v); setTitleError(false); touch() }}
            />
            {titleSuggestions.length > 0 && (
              <div className="st-suggest" role="group" aria-label="Sugerencias de nombre">
                {titleSuggestions.map((s) => (
                  <button
                    key={s}
                    type="button"
                    className="chip"
                    onClick={() => {
                      setTitle(s); setTitleError(false); touch()
                      // Si el título corresponde a un grupo conocido y no hay resultados, se precargan sus parámetros.
                      if (rows.every(rowIsBlank)) addGroup(s)
                    }}
                  >
                    {s}
                  </button>
                ))}
              </div>
            )}
            <DateInput label="Fecha del estudio" value={day} onChange={(d) => { setDay(d); touch() }} />
            <TextInput label={sp.placeLabel} value={place} autoCapitalize="words" onChange={(v) => { setPlace(v); touch() }} />
            <TextInput label={sp.doctorLabel} value={doctor} autoCapitalize="words" onChange={(v) => { setDoctor(v); touch() }} />
          </Card>

          {/* ---- Resultado general ---- */}
          <Card title="Resultado general">
            <Chips options={StudyCategories.statuses} value={status === '' ? null : status} allowDeselect onChange={(s) => { setStatus(s ?? ''); touch() }} />
            <TextArea label="Resumen, conclusiones o indicaciones" value={summary} onChange={(v) => { setSummary(v); touch() }} />
            <OptionalDateInput label="Seguimiento o próxima vez" value={followUp} onChange={(d) => { setFollowUp(d); touch() }} hint={followUp === null ? 'Sin fecha de seguimiento' : undefined} />
          </Card>

          {/* ---- Resultados ---- */}
          <Card title="Resultados">
            {rows.length === 0 && (
              <p className="muted">Agrega los valores del reporte (por ejemplo: Glucosa 92 mg/dL) para ver después cómo cambian con el tiempo.</p>
            )}
            {rows.map((row) => (
              <ResultRowEditor
                key={row.key}
                row={row}
                showSuggestions={focusedKey === row.key}
                pastNames={pastNames}
                onFocusName={() => setFocusedKey(row.key)}
                onChange={(patch) => updateRow(row.key, patch)}
                onPickName={(name) => pickName(row, name)}
                onRemove={() => { setRows((rs) => rs.filter((r) => r.key !== row.key)); touch() }}
              />
            ))}
            <div className="row wrap">
              <Button
                variant="outline"
                icon={Plus}
                onClick={() => { const r = emptyRow(nextKey.current++); setRows((rs) => [...rs, r]); setFocusedKey(r.key); touch() }}
              >
                Agregar resultado
              </Button>
              <Button variant="outline" icon={ListPlus} onClick={() => setShowGroups(true)}>Parámetros comunes</Button>
            </div>
            <InfoNote>{sp.rangesNote}</InfoNote>
          </Card>

          {/* ---- Adjuntos ---- */}
          <Card title="Adjuntos">
            {atts.length === 0 ? (
              <p className="muted">Guarda una foto o el PDF del reporte. Se guarda dentro de la app, en este dispositivo, y queda disponible sin conexión.</p>
            ) : (
              <div className="st-atts">
                {atts.map((a) => <AttachmentTile key={a.key} att={a} onOpen={() => open(a)} onRemove={() => removeAtt(a)} />)}
              </div>
            )}
            <div><Button variant="outline" icon={Paperclip} onClick={() => { void addFiles() }}>Agregar foto o PDF</Button></div>
          </Card>

          {errorMsg && <p className="st-error" role="alert">{errorMsg}</p>}
          <Button full disabled={saving} onClick={() => { void save() }} style={{ minHeight: 52 }}>
            {id === null ? 'Guardar estudio' : 'Guardar cambios'}
          </Button>
        </div>
      )}

      {viewer}
      <ConfirmDialog
        open={showDiscard}
        title="¿Descartar cambios?"
        text="Los cambios que hiciste en este estudio no se guardarán."
        confirmLabel="Descartar"
        onConfirm={() => { setShowDiscard(false); back() }}
        onClose={() => setShowDiscard(false)}
      />
      <Sheet open={showGroups} onClose={() => setShowGroups(false)} title="Parámetros comunes">
        <p className="muted">
          {sp.isPet
            ? 'Elige un grupo para agregar sus parámetros con su unidad. Después capturas los valores y los rangos que vengan en el reporte.'
            : 'Elige un grupo para agregar sus parámetros con unidad y rango orientativo. Después solo capturas los valores.'}
        </p>
        <div>
          {sp.groups.map((g) => (
            <button key={g.name} type="button" className="st-group" onClick={() => { addGroup(g.name); setShowGroups(false) }}>
              <span style={{ fontWeight: 600, display: 'block' }}>{g.name}</span>
              <span className="small muted" style={{ display: 'block' }}>{g.params.join(', ')}</span>
            </button>
          ))}
        </div>
      </Sheet>
    </Page>
  )
}

function ResultRowEditor(props: {
  row: RowState
  showSuggestions: boolean
  pastNames: string[]
  onFocusName: () => void
  onChange: (patch: Partial<RowState>) => void
  onPickName: (name: string) => void
  onRemove: () => void
}) {
  const { row } = props
  const suggestions = useMemo(
    () => (props.showSuggestions ? StudyCatalog.suggest(row.name, props.pastNames, 4) : []),
    [props.showSuggestions, row.name, props.pastNames],
  )
  const f = row.isText ? 'SIN_RANGO' : flag(toNumber(row.value), toNumber(row.refMin), toNumber(row.refMax))
  const bad = (s: string) => s.trim() !== '' && toNumber(s) === null
  return (
    <div className="st-row" data-row={row.key}>
      <div className="head">
        <div className="grow" onFocusCapture={props.onFocusName}>
          <TextInput label="Parámetro" value={row.name} placeholder="Ej. Glucosa" onChange={(v) => props.onChange({ name: v })} />
        </div>
        <button type="button" className="icon-btn" aria-label="Quitar resultado" onClick={props.onRemove} style={{ width: 48, height: 48, color: 'var(--muted)' }}>
          <Trash2 size={22} />
        </button>
      </div>
      {suggestions.length > 0 && (
        <div className="st-suggest" role="group" aria-label="Sugerencias de parámetro">
          {suggestions.map((s) => <button key={s} type="button" className="chip" onClick={() => props.onPickName(s)}>{s}</button>)}
        </div>
      )}
      <div className="st-kind">
        <button type="button" className={`chip${!row.isText ? ' on' : ''}`} aria-pressed={!row.isText} onClick={() => props.onChange({ isText: false })}>Número</button>
        <button type="button" className={`chip${row.isText ? ' on' : ''}`} aria-pressed={row.isText} onClick={() => props.onChange({ isText: true })}>Texto</button>
        <span className="grow" />
        {f !== 'SIN_RANGO' && <Pill text={flagLabel(f)} color={flagColor(f)} />}
      </div>
      {row.isText ? (
        <TextInput label="Resultado" value={row.text} placeholder="Ej. Negativo, sin alteraciones" onChange={(v) => props.onChange({ text: v })} />
      ) : (
        <>
          <div className="st-grid2">
            <NumberInput label="Valor" value={row.value} error={bad(row.value) && 'Número no válido'} onChange={(v) => props.onChange({ value: v })} />
            <TextInput label="Unidad" value={row.unit} maxLength={16} autoCapitalize="none" onChange={(v) => props.onChange({ unit: v })} />
          </div>
          <div className="st-grid2">
            <NumberInput label="Ref. mínima" value={row.refMin} error={bad(row.refMin) && 'Número no válido'} onChange={(v) => props.onChange({ refMin: v })} />
            <NumberInput label="Ref. máxima" value={row.refMax} error={bad(row.refMax) && 'Número no válido'} onChange={(v) => props.onChange({ refMax: v })} />
          </div>
        </>
      )}
    </div>
  )
}
