import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useLiveQuery } from 'dexie-react-hooks'
import { CalendarDays, ChevronDown, ChevronUp, Pill as PillIcon, Plus } from 'lucide-react'
import { useActive } from '../../data/profiles'
import type { Appointment, MedIntake, Medication } from '../../data/types'
import { Fmt, millisToEpochDay, millisToMinutes } from '../../lib/dates'
import { Button, Card, EmptyState, Fab, IconButton, NavRow, Page, SectionLabel } from '../../ui'
import { deleteIntake, logDoseNow, skipDose, takeDose, undoDose } from './actions'
import { AdherencePill, CalendarButton, CalendarNote, DoseRow, useAddToCalendar, useCalendarOwner, useNow } from './common'
import { medicationEvents } from './ics'
import { TAKEN, dosesForDay, isAsNeeded, isFinished, recentAdherence, scheduleSummary } from './schedule'
import { medsText, type MedsText } from './text'

/** Minutos de tolerancia antes de marcar una toma como atrasada. */
const LATE_AFTER_MIN = 15

export const appointmentTitle = (a: Appointment, txt: MedsText): string => a.title.trim() || txt.appointmentDefault

export function MedsPage() {
  const nav = useNavigate()
  const { db, isPet } = useActive()
  const txt = medsText(isPet)
  const now = useNow()
  const today = millisToEpochDay(now)
  const nowMinutes = millisToMinutes(now)
  const [showInactive, setShowInactive] = useState(false)

  const meds = useLiveQuery(() => db.medications.toArray(), [db])
  const intakes = useLiveQuery(() => db.medIntakes.toArray(), [db]) ?? []
  const nextAppointment = useLiveQuery(
    async () => (await db.appointments.where('timestamp').aboveOrEqual(Date.now()).filter((a) => !a.done).first()) ?? null,
    [db],
  )

  const sorted = useMemo(
    () => [...(meds ?? [])].sort((a, b) => Number(b.active) - Number(a.active) || a.name.toLowerCase().localeCompare(b.name.toLowerCase())),
    [meds],
  )
  const intakesToday = useMemo(() => intakes.filter((i) => i.epochDay === today), [intakes, today])
  const byMed = useMemo(() => {
    const m = new Map<number, MedIntake[]>()
    for (const i of intakes) { const l = m.get(i.medicationId); if (l) l.push(i); else m.set(i.medicationId, [i]) }
    for (const l of m.values()) l.sort((a, b) => b.timestamp - a.timestamp)
    return m
  }, [intakes])

  const current = sorted.filter((m) => m.active && !isFinished(m, today))
  const inactive = sorted.filter((m) => !m.active || isFinished(m, today))
  const doses = dosesForDay(current, intakesToday, today)
  const asNeeded = current.filter(isAsNeeded)
  const hasScheduled = current.some((m) => !isAsNeeded(m))

  return (
    <Page
      title="Medicamentos"
      actions={<IconButton icon={CalendarDays} label={txt.appointmentsTitle} onClick={() => nav('/citas')} />}
      fab={<Fab icon={Plus} label="Medicamento" onClick={() => nav('/medicamentos/nuevo')} />}
    >
      {meds === undefined ? null : (
        <>
          {sorted.length === 0 && (
            <EmptyState
              icon={PillIcon}
              title="Aún no tienes medicamentos"
              message={txt.medsEmpty}
              actionLabel="Agregar medicamento"
              onAction={() => nav('/medicamentos/nuevo')}
            />
          )}

          {current.length > 0 && (
            <Card title="Hoy" action={<span className="small muted" style={{ fontWeight: 600 }}>{Fmt.dateWeekday(today)}</span>}>
              {doses.length === 0 ? (
                <p className="muted">{hasScheduled ? 'Hoy no hay tomas programadas.' : txt.noScheduledDoses}</p>
              ) : (
                <>
                  <TodayProgress doses={doses.length} done={doses.filter((d) => !d.pending).length} />
                  <div>
                    {doses.map((dose) => (
                      <DoseRow
                        key={`${dose.med.id}-${dose.slot}`}
                        dose={dose}
                        late={dose.pending && dose.minutes < nowMinutes - LATE_AFTER_MIN}
                        onTake={() => void takeDose(db, dose, today)}
                        onSkip={() => void skipDose(db, dose, today)}
                        onUndo={() => void undoDose(db, dose, today)}
                      />
                    ))}
                  </div>
                </>
              )}
            </Card>
          )}

          {asNeeded.length > 0 && (
            <Card title="Según sea necesario">
              {asNeeded.map((med, i) => (
                <div key={med.id}>
                  {i > 0 && <div className="divider" style={{ marginBottom: 10 }} />}
                  <AsNeededRow med={med} today={today} intakes={byMed.get(med.id!) ?? []} />
                </div>
              ))}
            </Card>
          )}

          {current.length > 0 && <SectionLabel>Mis medicamentos</SectionLabel>}
          {current.map((med) => (
            <MedCard key={med.id} med={med} today={today} nowMinutes={nowMinutes} intakes={byMed.get(med.id!) ?? []} dimmed={false} />
          ))}
          {hasScheduled && <CalendarNote />}

          {inactive.length > 0 && (
            <button type="button" className="md-expander" aria-expanded={showInactive} onClick={() => setShowInactive(!showInactive)}>
              <span className="grow">Terminados o inactivos ({inactive.length})</span>
              {showInactive ? <ChevronUp size={22} /> : <ChevronDown size={22} />}
            </button>
          )}
          {showInactive && inactive.map((med) => (
            <MedCard key={med.id} med={med} today={today} nowMinutes={nowMinutes} intakes={byMed.get(med.id!) ?? []} dimmed />
          ))}

          <Card>
            <NavRow
              icon={CalendarDays}
              title={txt.appointmentsTitle}
              subtitle={nextAppointment ? `Próxima: ${appointmentTitle(nextAppointment, txt)} · ${Fmt.relativeDateTime(nextAppointment.timestamp)}` : 'Sin citas próximas'}
              onClick={() => nav('/citas')}
            />
          </Card>
        </>
      )}
    </Page>
  )
}

function TodayProgress(props: { doses: number; done: number }) {
  return (
    <div className="row">
      <div className="md-progress" role="progressbar" aria-valuemin={0} aria-valuemax={props.doses} aria-valuenow={props.done}>
        <div style={{ width: `${(props.done / props.doses) * 100}%` }} />
      </div>
      <span className="small muted" style={{ fontWeight: 600 }}>
        {props.done === props.doses ? '¡Listo por hoy!' : `${props.done} de ${props.doses} registradas`}
      </span>
    </div>
  )
}

/** Medicamento sin horario: se registra cada toma cuando ocurre. */
function AsNeededRow(props: { med: Medication; today: number; intakes: MedIntake[] }) {
  const { db } = useActive()
  const { med, today } = props
  const free = props.intakes.filter((i) => i.status === TAKEN)
  const todayOnes = free.filter((i) => i.epochDay === today)
  const last = free[0]
  const status = todayOnes.length
    ? `Hoy: ${todayOnes.length} ${todayOnes.length === 1 ? 'toma' : 'tomas'} · última a las ${Fmt.time(todayOnes[0].timestamp)}`
    : last ? `Última toma: ${Fmt.relativeDateTime(last.timestamp)}` : 'Sin tomas registradas'
  return (
    <div data-testid={`asneeded-${med.id}`}>
      <div className="row">
        <span className="grow">
          <span style={{ display: 'block', fontWeight: 600 }}>{[med.name, med.dose].filter((s) => s.trim()).join(' · ')}</span>
          <span className="small muted" style={{ display: 'block' }}>{status}</span>
        </span>
        <Button variant="outline" small onClick={() => void logDoseNow(db, med.id!, today)}>Registrar toma</Button>
      </div>
      {todayOnes.length > 0 && (
        <div className="row" style={{ justifyContent: 'flex-end' }}>
          <Button variant="text" onClick={() => void deleteIntake(db, todayOnes[0])}>Deshacer última</Button>
        </div>
      )}
    </div>
  )
}

/** Tarjeta de un medicamento con su horario y adherencia. */
function MedCard(props: { med: Medication; today: number; nowMinutes: number; intakes: MedIntake[]; dimmed: boolean }) {
  const nav = useNavigate()
  const { med, today, nowMinutes, intakes, dimmed } = props
  const { profileId, owner } = useCalendarOwner()
  const addToCalendar = useAddToCalendar()
  const asNeeded = isAsNeeded(med)
  const tint = dimmed ? 'var(--muted)' : 'var(--primary)'
  const period =
    med.endEpochDay != null && med.endEpochDay < today ? `Terminó el ${Fmt.date(med.endEpochDay)}`
      : med.endEpochDay != null ? `Hasta el ${Fmt.date(med.endEpochDay)}`
        : med.startEpochDay > today ? `Empieza el ${Fmt.date(med.startEpochDay)}`
          : !med.active ? 'Inactivo' : null
  const count30 = intakes.filter((i) => i.status === TAKEN && i.epochDay > today - 30).length
  return (
    <section className={`card${dimmed ? ' md-dim' : ''}`} data-testid={`med-${med.id}`}>
      <div className="row tap" role="button" tabIndex={0} onClick={() => nav(`/medicamentos/${med.id}`)} style={{ cursor: 'pointer', alignItems: 'flex-start' }}>
        <span className="md-badge" style={{ background: `color-mix(in srgb, ${tint} 15%, transparent)`, color: tint }}><PillIcon size={22} /></span>
        <span className="grow">
          <span className="md-title" style={{ display: 'block' }}>{[med.name, med.dose].filter((s) => s.trim()).join(' · ')}</span>
          <span className="muted" style={{ display: 'block', fontSize: 15 }}>{scheduleSummary(med)}</span>
          {period && <span className="small muted" style={{ display: 'block' }}>{period}</span>}
        </span>
      </div>
      {!dimmed && (
        <div className="row wrap" style={{ marginTop: 10, gap: 8 }}>
          {asNeeded ? (
            <span className="small muted">{count30} {count30 === 1 ? 'toma' : 'tomas'} en los últimos 30 días</span>
          ) : (
            <>
              <AdherencePill label="7 días" a={recentAdherence(med, intakes, 7, today, nowMinutes)} />
              <AdherencePill label="30 días" a={recentAdherence(med, intakes, 30, today, nowMinutes)} />
              <span className="small muted">tomas cumplidas</span>
            </>
          )}
        </div>
      )}
      {!dimmed && !asNeeded && (
        <div style={{ marginTop: 10 }}>
          <CalendarButton small onClick={() => void addToCalendar(`medicamento-${med.name}`, medicationEvents(med, profileId, today, owner))} />
        </div>
      )}
    </section>
  )
}
