// Estadísticas del ciclo (port de CycleStatsScreen.kt). Ruta /ciclo/estadisticas.
import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { BarChart3 } from 'lucide-react'
import { CycleCatalog } from '../../data/catalogs'
import { useActive } from '../../data/profiles'
import { Fmt, epochDayToDate } from '../../lib/dates'
import { BarChart, Button, Card, EmptyState, HBarList, InfoNote, LineChart, Page, Pill, StatTile } from '../../ui'
import { PeopleOnly, useCycleState } from './common'
import { isValidCycle, type CycleAnalysis, type CycleRecord } from './engine'
import { dias, moodStats, phaseShort, regularityText, symptomStats, type PhaseStat } from './logic'

export function CycleStatsPage() {
  const { isPet } = useActive()
  return isPet ? <PeopleOnly title="Ciclo menstrual" /> : <Stats />
}

function Stats() {
  const nav = useNavigate()
  const state = useCycleState()
  const a = state.analysis
  const symptoms = useMemo(() => symptomStats(state.days, a), [state.days, a])
  const moods = useMemo(() => moodStats(state.days, a), [state.days, a])
  const temps = useMemo(() => state.days.filter((d) => d.basalTemp !== null).map((d) => ({ x: d.epochDay, y: d.basalTemp as number })), [state.days])
  const tests = useMemo(() => state.days.filter((d) => d.pregnancyTest !== 0).sort((x, y) => y.epochDay - x.epochDay), [state.days])

  return (
    <Page title="Estadísticas del ciclo">
      {state.loaded && !a.hasData && (
        <EmptyState
          icon={BarChart3}
          title="Aún no hay ciclos registrados"
          message="Cuando registres tus periodos aquí verás promedios, regularidad, historial y los síntomas que más se repiten."
          actionLabel="Registrar hoy"
          onAction={() => nav(`/ciclo/dia/${state.today}`)}
        />
      )}
      {state.loaded && a.hasData && (
        <>
          <SummaryCard a={a} />
          <LengthsCard a={a} />
          <HistoryCard a={a} />
          {tests.length > 0 && (
            <Card title="Pruebas de embarazo">
              <div>
                {tests.slice(0, 24).map((d) => {
                  const cd = a.info(d.epochDay).cycleDay
                  return (
                    <button key={d.epochDay} type="button" className="cy-hist-row tap" data-testid="cy-test-row" onClick={() => nav(`/ciclo/dia/${d.epochDay}`)}>
                      <span className="grow">
                        <span style={{ display: 'block' }}>{Fmt.dateWeekday(d.epochDay)} {epochDayToDate(d.epochDay).getFullYear()}</span>
                        <span className="small muted" style={{ display: 'block' }}>{[cd !== null ? `Día ${cd} del ciclo` : null, Fmt.daysFromNow(d.epochDay)].filter(Boolean).join(' · ')}</span>
                      </span>
                      <Pill text={CycleCatalog.testLabel(d.pregnancyTest)} color={d.pregnancyTest === 2 ? 'var(--tertiary)' : 'var(--outline)'} />
                    </button>
                  )
                })}
              </div>
              {tests.length > 24 && <p className="small muted">Se muestran las 24 más recientes de {tests.length}.</p>}
            </Card>
          )}
          {symptoms.length > 0 && <FrequencyCard title="Síntomas más frecuentes" stats={symptoms} color="var(--period)" />}
          {moods.length > 0 && <FrequencyCard title="Ánimo más frecuente" stats={moods} color="var(--primary)" />}
          {temps.length >= 2 && (
            <Card title="Temperatura basal">
              <LineChart series={[{ name: 'Temperatura', points: temps.slice(-90), color: 'var(--ovulation)' }]} yFormatter={(y) => Fmt.num(y, 2)} unit="°C" />
              <p className="small muted">Tras la ovulación la temperatura basal suele subir entre 0.2 y 0.5 °C y mantenerse así hasta el siguiente periodo.</p>
            </Card>
          )}
          <InfoNote>
            Conviene consultar a una persona profesional de la salud si, de forma sostenida, tus ciclos duran menos de 21 o más de
            35 días, el sangrado dura más de 7 días o es muy abundante, pasan más de 90 días sin periodo (sin embarazo), hay sangrado
            entre periodos o el dolor te impide hacer tu vida normal. Esta información es orientativa y no sustituye una valoración
            médica.
          </InfoNote>
        </>
      )}
    </Page>
  )
}

function SummaryCard({ a }: { a: CycleAnalysis }) {
  const validCount = a.cycles.filter(isValidCycle).length
  const recent = Math.min(validCount, 6)
  const variation = a.shortestCycle !== null && a.longestCycle !== null && recent >= 2 ? a.longestCycle - a.shortestCycle : null
  const first = a.periods[0]
  return (
    <Card title="Resumen">
      <div className="stats">
        <StatTile
          label="Ciclo promedio"
          value={a.avgCycle !== null ? `${Fmt.num(a.avgCycle, 1)} días` : '—'}
          sub={a.avgCycle !== null ? `últimos ${recent === 1 ? '1 ciclo' : `${recent} ciclos`}` : 'Faltan ciclos completos'}
        />
        <StatTile label="Periodo promedio" value={a.avgPeriod !== null ? `${Fmt.num(a.avgPeriod, 1)} días` : '—'} sub={a.avgPeriod !== null ? 'de sangrado' : 'Aún en curso'} color="var(--period)" />
      </div>
      <div className="stats">
        <StatTile label="Ciclo más corto" value={a.shortestCycle !== null ? dias(a.shortestCycle) : '—'} />
        <StatTile label="Ciclo más largo" value={a.longestCycle !== null ? dias(a.longestCycle) : '—'} />
      </div>
      <div className="stats">
        <StatTile label="Variación" value={variation !== null ? dias(variation) : '—'} sub={a.cycleStdDev !== null ? `± ${Fmt.num(a.cycleStdDev, 1)} días` : 'entre ciclos'} />
        <StatTile
          label="Regularidad"
          value={regularityText(a)}
          sub={a.regular === true ? 'Varía 7 días o menos' : a.regular === false ? 'Varía más de 7 días' : 'Se necesitan 3 ciclos'}
          color={a.regular === true ? 'var(--good)' : a.regular === false ? 'var(--warn)' : undefined}
        />
      </div>
      <p className="small muted">
        Periodos registrados: {a.periods.length} · Ciclos completos: {a.cycles.filter((c) => c.length !== null).length}
        {first ? ` · Desde ${Fmt.date(first.start)}` : ''}
      </p>
    </Card>
  )
}

function LengthsCard({ a }: { a: CycleAnalysis }) {
  const bars = a.cycles.filter(isValidCycle).slice(-12)
  return (
    <Card title="Duración de tus ciclos">
      {bars.length === 0 ? (
        <p className="muted">Cuando registres el inicio de dos periodos seguidos aparecerá aquí la duración de cada ciclo.</p>
      ) : (
        <>
          <BarChart
            bars={bars.map((c) => ({ label: Fmt.dateShort(c.start), value: c.length as number }))}
            color="var(--period)"
            valueFormatter={(v) => Fmt.num(v, 0)}
            reference={a.avgCycle ?? undefined}
          />
          <p className="small muted">
            Días por ciclo, según la fecha en que empezó. La línea punteada es tu promedio reciente
            {a.avgCycle !== null ? ` (${Fmt.num(a.avgCycle, 1)} días).` : '.'}
          </p>
        </>
      )}
    </Card>
  )
}

function HistoryCard({ a }: { a: CycleAnalysis }) {
  const [showAll, setShowAll] = useState(false)
  const all = useMemo(() => [...a.cycles].reverse(), [a])
  const shown = showAll ? all : all.slice(0, 8)
  return (
    <Card title="Historial de ciclos">
      <div>
        {shown.map((c, i) => (
          <div key={c.start}>
            {i > 0 && <div className="divider" />}
            <HistoryRow c={c} />
          </div>
        ))}
      </div>
      {all.length > 8 && <Button variant="text" full onClick={() => setShowAll(!showAll)}>{showAll ? 'Ver menos' : `Ver los ${all.length} ciclos`}</Button>}
    </Card>
  )
}

function HistoryRow({ c }: { c: CycleRecord }) {
  return (
    <div className="cy-hist-row">
      <div className="grow">
        <div style={{ fontWeight: 600 }}>{Fmt.date(c.start)}</div>
        <div className="small muted">Periodo de {dias(c.periodLength)}</div>
      </div>
      {c.length === null ? (
        <Pill text="En curso" color="var(--primary)" />
      ) : isValidCycle(c) ? (
        <span className="cy-hist-len">{dias(c.length)}</span>
      ) : (
        <div style={{ textAlign: 'right' }}>
          <div className="cy-hist-len muted">{dias(c.length)}</div>
          <Pill text="Atípico · no cuenta" color="var(--warn)" />
        </div>
      )}
    </div>
  )
}

function FrequencyCard({ title, stats, color }: { title: string; stats: PhaseStat[]; color: string }) {
  const top = stats.slice(0, 8)
  const hints = top.slice(0, 4).filter((s) => s.topPhase !== null).map((s) => `${s.label}: sobre todo en ${phaseShort(s.topPhase!)}`)
  return (
    <Card title={title}>
      <HBarList items={top.map((s) => [s.label, s.count] as const)} color={color} />
      {hints.length > 0 && (
        <div>
          <div style={{ fontWeight: 700, fontSize: 14 }}>¿Cuándo aparecen?</div>
          {hints.map((h) => <div key={h} className="small muted">{h}</div>)}
        </div>
      )}
    </Card>
  )
}
