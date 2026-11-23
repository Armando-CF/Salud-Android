import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useLiveQuery } from 'dexie-react-hooks'
import { CalendarDays, MapPin, Plus, Trash2, User } from 'lucide-react'
import { useActive } from '../../data/profiles'
import type { Appointment } from '../../data/types'
import { Fmt, epochDayToDate, epochDayToMillis, millisToEpochDay, today as todayDay } from '../../lib/dates'
import {
  Button, Card, ConfirmDialog, DateTimeInput, EmptyState, Fab, IconButton, Page, Pill, SectionLabel, TextArea, TextInput, Toggle, useBack, useToast,
} from '../../ui'
import { CalendarButton, CalendarNote, useAddToCalendar, useCalendarOwner, useNow } from './common'
import { appointmentEvent } from './ics'
import { appointmentTitle } from './MedsPage'
import { medsText } from './text'

const SPECIALTIES = [
  'Medicina general', 'Ginecología', 'Odontología', 'Dermatología', 'Cardiología', 'Oftalmología',
  'Nutrición', 'Psicología', 'Traumatología', 'Urología', 'Pediatría', 'Laboratorio',
]

const MONTHS = ['ENE', 'FEB', 'MAR', 'ABR', 'MAY', 'JUN', 'JUL', 'AGO', 'SEP', 'OCT', 'NOV', 'DIC']

/** Lista de citas: próximas y anteriores. Ruta /citas. */
export function AppointmentsPage() {
  const nav = useNavigate()
  const { db, isPet } = useActive()
  const txt = medsText(isPet)
  const now = useNow()
  const all = useLiveQuery(() => db.appointments.toArray(), [db])

  const upcoming = useMemo(() => (all ?? []).filter((a) => !a.done && a.timestamp >= now).sort((a, b) => a.timestamp - b.timestamp), [all, now])
  const past = useMemo(() => (all ?? []).filter((a) => a.done || a.timestamp < now).sort((a, b) => b.timestamp - a.timestamp), [all, now])

  const setDone = (a: Appointment, done: boolean) => void db.appointments.update(a.id!, { done })

  return (
    <Page title={txt.appointmentsTitle} fab={<Fab icon={Plus} label="Cita" onClick={() => nav('/citas/nueva')} />}>
      {all === undefined ? null : all.length === 0 ? (
        <EmptyState icon={CalendarDays} title="Aún no tienes citas" message={txt.appointmentsEmpty} actionLabel="Agregar cita" onAction={() => nav('/citas/nueva')} />
      ) : (
        <>
          <SectionLabel>Próximas</SectionLabel>
          {upcoming.length === 0 && <p className="muted">No tienes citas próximas.</p>}
          {upcoming.map((a) => <AppointmentCard key={a.id} a={a} upcoming onToggleDone={() => setDone(a, true)} />)}
          {upcoming.length > 0 && <CalendarNote />}
          {past.length > 0 && <SectionLabel>Anteriores</SectionLabel>}
          {past.map((a) => <AppointmentCard key={a.id} a={a} upcoming={false} onToggleDone={() => setDone(a, !a.done)} />)}
        </>
      )}
    </Page>
  )
}

function AppointmentCard(props: { a: Appointment; upcoming: boolean; onToggleDone: () => void }) {
  const nav = useNavigate()
  const { isPet } = useActive()
  const txt = medsText(isPet)
  const { profileId, owner } = useCalendarOwner()
  const addToCalendar = useAddToCalendar()
  const { a, upcoming } = props
  const day = millisToEpochDay(a.timestamp)
  const date = epochDayToDate(day)
  const who = [a.doctor, a.specialty].filter((s) => s.trim()).join(' · ')
  const title = appointmentTitle(a, txt)
  return (
    <section className="card" data-testid={`appt-${a.id}`}>
      <div className="row tap" role="button" tabIndex={0} onClick={() => nav(`/citas/${a.id}`)} style={{ alignItems: 'flex-start', cursor: 'pointer' }}>
        <div className={`md-date${upcoming ? '' : ' past'}`}>
          <b>{date.getDate()}</b>
          <span>{MONTHS[date.getMonth()]}</span>
          {date.getFullYear() !== new Date().getFullYear() && <small>{date.getFullYear()}</small>}
        </div>
        <div className="grow">
          <div className="md-title">{title}</div>
          <div className="muted" style={{ fontSize: 15 }}>
            {Fmt.dateWeekday(day)} · {Fmt.time(a.timestamp)}
            {upcoming && <b style={{ color: 'var(--primary)' }}> · {Fmt.daysFromNow(day)}</b>}
          </div>
          {who && <div className="md-line"><User size={15} /><span>{who}</span></div>}
          {a.place.trim() && <div className="md-line"><MapPin size={15} /><span>{a.place}</span></div>}
        </div>
      </div>
      {upcoming ? (
        <div style={{ marginTop: 10 }}>
          <CalendarButton small onClick={() => void addToCalendar(`cita-${title}`, [appointmentEvent(a, profileId, txt.appointmentDefault, owner)])} />
        </div>
      ) : (
        <div className="row" style={{ marginTop: 8 }}>
          {a.done ? <Pill text="Realizada" color="var(--good)" /> : <Pill text="Sin marcar" color="var(--warn)" />}
          <span className="grow" />
          <Button variant="text" onClick={props.onToggleDone}>{a.done ? 'Desmarcar' : 'Marcar como realizada'}</Button>
        </div>
      )}
    </section>
  )
}

/** Alta / edición de una cita. Rutas: /citas/nueva y /citas/:id. */
export function AppointmentEditPage() {
  const params = useParams()
  // La clave reinicia el formulario al pasar de un registro a otro (o a uno nuevo) sin salir de la pantalla.
  return <AppointmentEditor key={params.id ?? 'nuevo'} />
}

function AppointmentEditor() {
  const params = useParams()
  const id = params.id ? Number(params.id) : null
  const { db, isPet } = useActive()
  const txt = medsText(isPet)
  const back = useBack()
  const toast = useToast()
  const { profileId, owner } = useCalendarOwner()
  const addToCalendar = useAddToCalendar()

  const [loaded, setLoaded] = useState(id === null)
  const [missing, setMissing] = useState(false)
  const [title, setTitle] = useState('')
  const [timestamp, setTimestamp] = useState(() => epochDayToMillis(todayDay() + 1, 10 * 60))
  const [doctor, setDoctor] = useState('')
  const [specialty, setSpecialty] = useState('')
  const [hadSpecialty, setHadSpecialty] = useState(false)
  const [place, setPlace] = useState('')
  const [notes, setNotes] = useState('')
  const [remind, setRemind] = useState(true)
  const [done, setDone] = useState(false)
  const [tried, setTried] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)

  useEffect(() => {
    if (id === null) return
    let alive = true
    void db.appointments.get(id).then((a) => {
      if (!alive) return
      if (!a) setMissing(true)
      else {
        setTitle(a.title); setTimestamp(a.timestamp); setDoctor(a.doctor); setSpecialty(a.specialty); setHadSpecialty(!!a.specialty.trim())
        setPlace(a.place); setNotes(a.notes); setRemind(a.remind); setDone(a.done)
      }
      setLoaded(true)
    })
    return () => { alive = false }
  }, [db, id])

  const isFuture = timestamp > Date.now()
  const titleError = tried && !title.trim()
  const day = millisToEpochDay(timestamp)

  const item: Appointment = {
    ...(id !== null ? { id } : {}),
    timestamp, title: title.trim(), doctor: doctor.trim(), specialty: specialty.trim(), place: place.trim(), notes: notes.trim(), remind, done,
  }

  const save = async () => {
    setTried(true)
    if (!title.trim()) return
    if (id === null) await db.appointments.add(item)
    else await db.appointments.put(item)
    toast('Cita guardada')
    back()
  }

  return (
    <Page
      title={id === null ? 'Nueva cita' : 'Editar cita'}
      actions={
        <>
          {id !== null && !missing && <IconButton icon={Trash2} label="Eliminar" onClick={() => setConfirmDelete(true)} />}
          {!missing && <button className="text-btn" disabled={!loaded} onClick={() => void save()}>Guardar</button>}
        </>
      }
    >
      {missing ? <p>Esta cita ya no existe.</p> : !loaded ? null : (
        <>
          <Card title="Cita">
            <TextInput label="Motivo" value={title} onChange={setTitle} placeholder={txt.reasonPlaceholder} error={titleError && 'Escribe el motivo de la cita'} maxLength={100} />
            <DateTimeInput value={timestamp} onChange={setTimestamp} />
            <p className="small muted">{Fmt.dateLong(day)} · {Fmt.daysFromNow(day)}</p>
          </Card>

          <Card title="Con quién y dónde">
            <TextInput label={txt.doctorLabel} value={doctor} onChange={setDoctor} placeholder={txt.doctorPlaceholder} maxLength={80} autoCapitalize="words" enterKeyHint="next" />
            {/* En mascotas la especialidad no aplica; solo se muestra si la cita ya traía una. */}
            {(!isPet || hadSpecialty) && (
              <>
                <TextInput label="Especialidad" value={specialty} onChange={setSpecialty} maxLength={60} enterKeyHint="next" />
                {!specialty.trim() && (
                  <div className="chips scroll">
                    {SPECIALTIES.map((s) => <button key={s} type="button" className="chip" onClick={() => setSpecialty(s)}>{s}</button>)}
                  </div>
                )}
              </>
            )}
            <TextInput label="Lugar" value={place} onChange={setPlace} placeholder={txt.placePlaceholder} maxLength={120} />
          </Card>

          <Card title="Notas">
            <TextArea label="Notas y preguntas para la consulta" value={notes} onChange={setNotes} placeholder={txt.notesPlaceholder} rows={3} />
          </Card>

          <Card title="Avisos">
            {isFuture && !done ? (
              <>
                <CalendarButton
                  full
                  variant="tonal"
                  onClick={() => { setTried(true); if (title.trim()) void addToCalendar(`cita-${item.title}`, [appointmentEvent(item, profileId, txt.appointmentDefault, owner)]) }}
                />
                <p className="small muted">El calendario te avisará un día antes y 2 horas antes.</p>
                <CalendarNote />
              </>
            ) : (
              <p className="small muted">Las citas pasadas o ya realizadas no necesitan aviso.</p>
            )}
          </Card>

          {(id !== null || !isFuture) && (
            <Card title="Opciones">
              <Toggle label="Realizada" sub="Márcala cuando ya hayas asistido" checked={done} onChange={setDone} />
            </Card>
          )}

          <Button full onClick={() => void save()}>Guardar</Button>
          {id !== null && <Button full variant="danger" icon={Trash2} onClick={() => setConfirmDelete(true)}>Eliminar cita</Button>}
        </>
      )}

      <ConfirmDialog
        open={confirmDelete && id !== null}
        title="¿Eliminar cita?"
        text={`Se borrará "${title.trim() || 'esta cita'}". Esta acción no se puede deshacer.`}
        onClose={() => setConfirmDelete(false)}
        onConfirm={() => {
          setConfirmDelete(false)
          if (id !== null) void db.appointments.delete(id).then(() => { toast('Cita eliminada'); back() })
        }}
      />
    </Page>
  )
}
