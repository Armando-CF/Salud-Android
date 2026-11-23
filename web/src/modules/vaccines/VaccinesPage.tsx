import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useLiveQuery } from 'dexie-react-hooks'
import { Plus, Syringe } from 'lucide-react'
import { VaccinationTypes } from '../../data/catalogs'
import { displayName, useActive } from '../../data/profiles'
import type { Vaccination } from '../../data/types'
import { Fmt, epochDayToDate, today as todayDay } from '../../lib/dates'
import { Card, EmptyState, Fab, InfoNote, Page, Pill, SectionLabel } from '../../ui'
import { CalendarNote, useAddToCalendar, useCalendarOwner } from '../meds/common'
import { vaccinationEvent } from '../meds/ics'
import { DESP_EXTERNA, DESP_INTERNA, VACUNA } from './catalog'
import { DueRow, VaccineTypeBadge, dueStatusColor, vaccineTypeColor, vaccinesDisclaimer, vaccinesTitle } from './common'
import { dueStatus, dueText, nextDose, upcoming, type DueItem } from './logic'

const filterLabel = (type: string): string =>
  type === VACUNA ? 'Vacunas' : type === DESP_INTERNA ? 'Desp. interna' : type === DESP_EXTERNA ? 'Pulgas y garrapatas' : 'Otros'

const countText = (n: number): string => (n === 1 ? '1 registro' : `${n} registros`)

/** Ruta del alta prellenada a partir de una aplicación pendiente. */
export function newVaccineRoute(prefill?: { type: string; name: string; dose?: string; place?: string }): string {
  if (!prefill) return '/vacunas/nueva'
  const q = new URLSearchParams({ type: prefill.type, name: prefill.name })
  if (prefill.dose) q.set('dose', prefill.dose)
  if (prefill.place) q.set('place', prefill.place)
  return `/vacunas/nueva?${q.toString()}`
}

/**
 * Lista del módulo de vacunas: pendientes y próximas arriba, historial por año abajo.
 * Para mascotas incluye desparasitación y un filtro por tipo. Ruta /vacunas.
 */
export function VaccinesPage() {
  const nav = useNavigate()
  const { db, info, isPet } = useActive()
  const t = todayDay()
  const { profileId, owner } = useCalendarOwner()
  const addToCalendar = useAddToCalendar()
  const [filter, setFilter] = useState('')

  const all = useLiveQuery(async () => (await db.vaccinations.toArray()).sort((a, b) => b.epochDay - a.epochDay || (b.id ?? 0) - (a.id ?? 0)), [db])

  const pending = useMemo(() => upcoming(all ?? [], t), [all, t])
  const pendingIds = useMemo(() => new Set(pending.map((p) => p.vaccination.id)), [pending])
  const typesPresent = useMemo(() => VaccinationTypes.all.map((o) => o[0]).filter((k) => (all ?? []).some((v) => v.type === k)), [all])
  const effectiveFilter = typesPresent.includes(filter) ? filter : ''
  const byYear = useMemo(() => {
    const m = new Map<number, Vaccination[]>()
    for (const v of all ?? []) {
      if (effectiveFilter && v.type !== effectiveFilter) continue
      const y = epochDayToDate(v.epochDay).getFullYear()
      const l = m.get(y)
      if (l) l.push(v); else m.set(y, [v])
    }
    return [...m.entries()].sort((a, b) => b[0] - a[0])
  }, [all, effectiveFilter])

  const toCalendar = (item: DueItem) => {
    const e = vaccinationEvent(item.vaccination, profileId, owner, nextDose(item.vaccination.dose))
    void addToCalendar(`${item.vaccination.type === VACUNA ? 'vacuna' : 'aplicacion'}-${item.vaccination.name}`, e ? [e] : [])
  }
  const overdue = pending.filter((p) => p.overdue).length

  return (
    <Page title={vaccinesTitle(isPet)} fab={<Fab icon={Plus} label="Registrar" onClick={() => nav(newVaccineRoute())} />}>
      {all === undefined ? null : all.length === 0 ? (
        <>
          <EmptyState
            icon={Syringe}
            title={isPet ? 'Sin vacunas ni desparasitaciones' : 'Lleva aquí tu cartilla de vacunación'}
            message={isPet
              ? `Registra las vacunas y desparasitaciones de ${displayName(info)} con su fecha y la próxima aplicación, y pásala a tu calendario para que te avise.`
              : 'Anota cada vacuna con su fecha, dosis y lote. Si tiene refuerzo, guarda la próxima fecha y pásala a tu calendario para que te avise.'}
            actionLabel={isPet ? 'Registrar la primera' : 'Registrar una vacuna'}
            onAction={() => nav(newVaccineRoute())}
          />
          <InfoNote>{vaccinesDisclaimer(isPet)}</InfoNote>
        </>
      ) : (
        <>
          {pending.length > 0 && (
            <Card
              title="Pendientes y próximas"
              action={overdue > 0 ? <Pill text={overdue === 1 ? '1 vencida' : `${overdue} vencidas`} color="var(--bad)" /> : undefined}
            >
              {pending.map((item, i) => (
                <div key={item.vaccination.id}>
                  {i > 0 && <div className="divider" style={{ marginBottom: 10 }} />}
                  <DueRow
                    item={item}
                    today={t}
                    onOpen={() => nav(`/vacunas/${item.vaccination.id}`)}
                    onRegister={() => nav(newVaccineRoute({ type: item.vaccination.type, name: item.vaccination.name, dose: nextDose(item.vaccination.dose), place: item.vaccination.place }))}
                    onCalendar={() => toCalendar(item)}
                  />
                </div>
              ))}
              {pending.some((p) => !p.overdue) && <CalendarNote />}
            </Card>
          )}

          {isPet && typesPresent.length > 1 && (
            // flexShrink 0: una fila con desplazamiento horizontal dentro del cuerpo de la página (flex en columna) se encogería a cero.
            <div className="chips scroll" role="group" aria-label="Filtrar por tipo" style={{ flexShrink: 0 }}>
              <button type="button" className={`chip${effectiveFilter === '' ? ' on' : ''}`} aria-pressed={effectiveFilter === ''} onClick={() => setFilter('')}>Todo</button>
              {typesPresent.map((k) => (
                <button key={k} type="button" className={`chip${effectiveFilter === k ? ' on' : ''}`} aria-pressed={effectiveFilter === k} onClick={() => setFilter(effectiveFilter === k ? '' : k)}>
                  {filterLabel(k)}
                </button>
              ))}
            </div>
          )}

          {byYear.map(([year, entries]) => (
            <div key={year} className="stack" style={{ gap: 10 }}>
              <SectionLabel>{year} · {countText(entries.length)}</SectionLabel>
              {entries.map((v) => (
                <HistoryCard
                  key={v.id}
                  v={v}
                  showType={isPet || v.type !== VACUNA}
                  pendingDue={pendingIds.has(v.id) ? v.nextDueEpochDay : null}
                  today={t}
                  onClick={() => nav(`/vacunas/${v.id}`)}
                />
              ))}
            </div>
          ))}
          <InfoNote>{vaccinesDisclaimer(isPet)}</InfoNote>
        </>
      )}
    </Page>
  )
}

/** Tarjeta de un registro del historial. */
function HistoryCard(props: { v: Vaccination; showType: boolean; pendingDue: number | null; today: number; onClick: () => void }) {
  const { v, pendingDue, today } = props
  const meta = [Fmt.date(v.epochDay), props.showType ? VaccinationTypes.label(v.type) : '', v.place.trim()].filter(Boolean).join(' · ')
  return (
    <section className="card tap" onClick={props.onClick} data-testid={`vac-${v.id}`}>
      <div className="row" style={{ alignItems: 'flex-start' }}>
        <VaccineTypeBadge type={v.type} />
        <div className="grow">
          <div className="row wrap" style={{ gap: 8 }}>
            <span className="md-title">{v.name}</span>
            {v.dose.trim() && <Pill text={v.dose.trim()} color={vaccineTypeColor(v.type)} />}
          </div>
          <div className="small muted">{meta}</div>
          {v.lot.trim() && <div className="small muted ellipsis">Lote {v.lot.trim()}</div>}
          {pendingDue != null && (
            <div className="small" style={{ color: dueStatusColor(dueStatus(pendingDue, today)), fontWeight: 600 }}>
              Próxima: {Fmt.date(pendingDue)} · {dueText(pendingDue, today).toLowerCase()}
            </div>
          )}
          {v.notes.trim() && <div className="small" style={{ display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{v.notes.trim()}</div>}
        </div>
      </div>
    </section>
  )
}
