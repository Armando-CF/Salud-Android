import { useEffect, useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useLiveQuery } from 'dexie-react-hooks'
import { Plus, Trash2, Undo2, X } from 'lucide-react'
import { useActive } from '../../data/profiles'
import type { MedIntake, Medication } from '../../data/types'
import { Fmt, formatHm, parseHm, today as todayDay } from '../../lib/dates'
import {
  Button, Card, ConfirmDialog, DateInput, IconButton, InfoNote, OptionalDateInput, Page, Pill, TextArea, TextInput, Toggle, useBack, useToast,
} from '../../ui'
import { deleteIntake, deleteMedication } from './actions'
import { CalendarButton, CalendarNote, useAddToCalendar, useCalendarOwner } from './common'
import { medicationEvents } from './ics'
import { ALL_DAYS, DAY_FULL_NAMES, TAKEN, WEEK_LETTERS, daysSummary, joinTimes } from './schedule'
import { medsText } from './text'

const SHORTCUTS: readonly (readonly [label: string, times: number[]])[] = [
  ['Una vez al día', [8 * 60]],
  ['Cada 12 h', [8 * 60, 20 * 60]],
  ['Cada 8 h', [7 * 60, 15 * 60, 23 * 60]],
  ['Según sea necesario', []],
]

/** Alta / edición de un medicamento. Rutas: /medicamentos/nuevo y /medicamentos/:id. */
export function MedEditPage() {
  const params = useParams()
  // La clave reinicia el formulario al pasar de un registro a otro (o a uno nuevo) sin salir de la pantalla.
  return <MedEditor key={params.id ?? 'nuevo'} />
}

function MedEditor() {
  const params = useParams()
  const id = params.id ? Number(params.id) : null
  const { db, isPet } = useActive()
  const txt = medsText(isPet)
  const back = useBack()
  const toast = useToast()
  const today = todayDay()
  const { profileId, owner } = useCalendarOwner()
  const addToCalendar = useAddToCalendar()

  const [loaded, setLoaded] = useState(id === null)
  const [missing, setMissing] = useState(false)
  const [name, setName] = useState('')
  const [dose, setDose] = useState('')
  const [instructions, setInstructions] = useState('')
  /** Horas de toma en minutos del día (sin ordenar mientras se editan, para que las filas no salten). */
  const [slots, setSlots] = useState<number[]>([8 * 60])
  const [daysMask, setDaysMask] = useState(ALL_DAYS)
  const [start, setStart] = useState(today)
  const [end, setEnd] = useState<number | null>(null)
  const [remind, setRemind] = useState(true)
  const [active, setActive] = useState(true)
  const [notes, setNotes] = useState('')
  const [tried, setTried] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)

  useEffect(() => {
    if (id === null) return
    let alive = true
    void db.medications.get(id).then((m) => {
      if (!alive) return
      if (!m) setMissing(true)
      else {
        setName(m.name); setDose(m.dose); setInstructions(m.instructions)
        setSlots(m.times.split(',').map((t) => parseHm(t)).filter((x): x is number => x !== null))
        setDaysMask(m.daysMask & ALL_DAYS); setStart(m.startEpochDay); setEnd(m.endEpochDay ?? null)
        setRemind(m.remind); setActive(m.active); setNotes(m.notes)
      }
      setLoaded(true)
    })
    return () => { alive = false }
  }, [db, id])

  const history = useLiveQuery(
    async () => (id === null ? [] : (await db.medIntakes.where('medicationId').equals(id).toArray()).sort((a, b) => b.timestamp - a.timestamp)),
    [db, id],
  ) ?? []

  const hasSlots = slots.length > 0
  const nameError = tried && !name.trim()
  const daysError = tried && hasSlots && daysMask === 0
  const endError = end !== null && end < start

  const current = useMemo<Medication>(() => ({
    ...(id !== null ? { id } : {}),
    name: name.trim(),
    dose: dose.trim(),
    instructions: instructions.trim(),
    times: joinTimes(slots),
    daysMask: hasSlots ? daysMask : ALL_DAYS,
    startEpochDay: start,
    endEpochDay: end,
    active,
    remind,
    notes: notes.trim(),
  }), [id, name, dose, instructions, slots, daysMask, hasSlots, start, end, active, remind, notes])

  const valid = !!name.trim() && !endError && !(hasSlots && daysMask === 0)

  const save = async () => {
    setTried(true)
    if (!valid) return
    if (id === null) await db.medications.add(current)
    else await db.medications.put(current)
    toast('Medicamento guardado')
    back()
  }

  const addSlot = () => {
    // Propone una hora razonable: 8:00, y luego 6 horas después de la última.
    const next = slots.length === 0 ? 8 * 60 : Math.min(23 * 60, Math.max(...slots) + 6 * 60)
    setSlots([...slots, slots.includes(next) ? (next + 30) % 1440 : next])
  }

  const calendarEvents = medicationEvents(current, profileId, today, owner)

  return (
    <Page
      title={id === null ? 'Nuevo medicamento' : 'Editar medicamento'}
      actions={
        <>
          {id !== null && !missing && <IconButton icon={Trash2} label="Eliminar" onClick={() => setConfirmDelete(true)} />}
          {!missing && <button className="text-btn" disabled={!loaded} onClick={() => void save()}>Guardar</button>}
        </>
      }
    >
      {missing ? <p>Este medicamento ya no existe.</p> : !loaded ? null : (
        <>
          <Card title="Medicamento">
            <TextInput label="Nombre" value={name} onChange={setName} placeholder={txt.medNamePlaceholder} error={nameError && 'Escribe el nombre'} maxLength={80} enterKeyHint="next" />
            <TextInput label="Dosis" value={dose} onChange={setDose} placeholder={txt.dosePlaceholder} maxLength={60} enterKeyHint="next" />
            <TextInput label="Indicaciones" value={instructions} onChange={setInstructions} placeholder={txt.instructionsPlaceholder} maxLength={120} />
          </Card>

          <Card title="Horarios">
            {!hasSlots && <p className="muted">Sin horarios: se toma según sea necesario y registras cada toma cuando ocurra.</p>}
            {slots.map((m, i) => (
              <div className="md-slot" key={i}>
                <input
                  className="input"
                  type="time"
                  aria-label={`Hora de la toma ${i + 1}`}
                  value={formatHm(m)}
                  onChange={(e) => { const v = parseHm(e.target.value); if (v !== null) setSlots(slots.map((x, j) => (j === i ? v : x))) }}
                />
                <button type="button" className="icon-btn" aria-label={`Quitar ${formatHm(m)}`} onClick={() => setSlots(slots.filter((_, j) => j !== i))}><X size={22} /></button>
              </div>
            ))}
            <div><Button variant="tonal" small icon={Plus} onClick={addSlot}>Agregar hora</Button></div>
            <div className="field-label">Atajos</div>
            <div className="chips">
              {SHORTCUTS.map(([label, times]) => (
                <button key={label} type="button" className="chip" onClick={() => setSlots(times)}>{label}</button>
              ))}
            </div>
            {hasSlots && (
              <>
                <div className="field-label" style={{ marginTop: 4 }}>Días</div>
                <div className="md-days" role="group" aria-label="Días de la semana">
                  {WEEK_LETTERS.map((letter, i) => {
                    const on = (daysMask & (1 << i)) !== 0
                    return (
                      <button key={i} type="button" className={`md-day${on ? ' on' : ''}`} aria-pressed={on} aria-label={DAY_FULL_NAMES[i]} onClick={() => setDaysMask(daysMask ^ (1 << i))}>
                        {letter}
                      </button>
                    )
                  })}
                </div>
                <div className={daysError ? 'md-error' : 'small muted'}>{daysError ? 'Elige al menos un día' : daysSummary(daysMask)}</div>
              </>
            )}
          </Card>

          <Card title="Duración">
            <DateInput label="Inicio" value={start} onChange={setStart} />
            <OptionalDateInput label="Fin (opcional)" value={end} onChange={setEnd} min={start} hint={end === null ? 'Sin fecha de fin' : undefined} />
            {endError && <div className="md-error">La fecha de fin no puede ser anterior al inicio.</div>}
          </Card>

          <Card title="Avisos">
            {hasSlots ? (
              <>
                <CalendarButton
                  full
                  variant="tonal"
                  label="Agregar las tomas al calendario"
                  onClick={() => { setTried(true); if (valid) void addToCalendar(`medicamento-${current.name}`, calendarEvents) }}
                />
                <CalendarNote />
              </>
            ) : (
              <p className="muted small">Agrega al menos un horario para poder pasar las tomas al calendario.</p>
            )}
          </Card>

          <Card title="Opciones">
            <Toggle label="Activo" sub={txt.inactiveHint} checked={active} onChange={setActive} />
            <TextArea label="Notas" value={notes} onChange={setNotes} placeholder={txt.medNotesPlaceholder} rows={2} />
          </Card>

          {id !== null && history.length > 0 && (
            <Card title="Tomas recientes">
              <IntakeHistory intakes={history.slice(0, 8)} onDelete={(i) => void deleteIntake(db, i)} />
              {history.length > 8 && <p className="small muted">y {history.length - 8} más en el historial</p>}
            </Card>
          )}

          <InfoNote>Savia solo te ayuda a recordar y registrar. Sigue siempre la dosis y los horarios que te indicó {txt.prescriber}.</InfoNote>

          <Button full onClick={() => void save()}>Guardar</Button>
          {id !== null && <Button full variant="danger" icon={Trash2} onClick={() => setConfirmDelete(true)}>Eliminar medicamento</Button>}
        </>
      )}

      <ConfirmDialog
        open={confirmDelete && id !== null}
        title="¿Eliminar medicamento?"
        text={`Se borrará "${name.trim() || 'este medicamento'}" junto con su historial de tomas. Si solo dejaste de tomarlo, puedes desactivarlo.`}
        onClose={() => setConfirmDelete(false)}
        onConfirm={() => {
          setConfirmDelete(false)
          if (id !== null) void deleteMedication(db, id).then(() => { toast('Medicamento eliminado'); back() })
        }}
      />
    </Page>
  )
}

/** Historial reciente de tomas de un medicamento. */
function IntakeHistory(props: { intakes: MedIntake[]; onDelete: (i: MedIntake) => void }) {
  return (
    <div>
      {props.intakes.map((intake, i) => (
        <div key={intake.id} className="row" style={{ padding: '4px 0', borderTop: i > 0 ? '1px solid var(--line)' : undefined }}>
          <span className="grow">
            <span style={{ display: 'block' }}>{Fmt.relativeDateTime(intake.timestamp)}</span>
            <span className="small muted" style={{ display: 'block' }}>
              {intake.slot ? `Toma de las ${intake.slot} · ${Fmt.dateShort(intake.epochDay)}` : 'Toma libre'}
            </span>
          </span>
          {intake.status === TAKEN ? <Pill text="Tomada" color="var(--good)" /> : <Pill text="Omitida" color="var(--muted)" />}
          <button type="button" className="icon-btn" aria-label="Quitar registro" onClick={() => props.onDelete(intake)} style={{ color: 'var(--muted)' }}><Undo2 size={22} /></button>
        </div>
      ))}
    </div>
  )
}
