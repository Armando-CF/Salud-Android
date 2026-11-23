import { useEffect, useMemo, useState } from 'react'
import { useLiveQuery } from 'dexie-react-hooks'
import { ChevronLeft, ChevronRight, Footprints, GlassWater, Minus, Moon, Plus, type LucideIcon } from 'lucide-react'
import type { SaviaDB } from '../../data/db'
import { useActive, useDb } from '../../data/profiles'
import type { DailyLog } from '../../data/types'
import { Fmt, epochDayToIso, isoToEpochDay, today } from '../../lib/dates'
import { Button, Card, ChartLegend, LineChart, Page, RangeSelector, StatTile, TextArea, rangeFromDay, type ChartPoint, type ChartRange } from '../../ui'
import { PeopleOnlyPage } from './PeopleOnly'
import { HabitSteps, WellbeingScale, emptyDailyLog, wellbeingAverages } from './scale'
import './wellbeing.css'

/**
 * Escribe los registros diarios en orden (una sola cola), para que el guardado automático nunca se desordene.
 * La base se toma AL ENVIAR (la del perfil en el que se hizo la edición), no al escribir.
 */
let writeQueue: Promise<unknown> = Promise.resolve()
function submitDailyLog(db: SaviaDB, log: DailyLog): void {
  writeQueue = writeQueue
    .then(async () => {
      if (WellbeingScale.isEmpty(log)) await db.dailyLogs.delete(log.epochDay)
      else await db.dailyLogs.put({ ...log, notes: log.notes.trim() })
    })
    .catch(() => { /* un fallo de escritura no debe detener la cola */ })
}

/** Bienestar diario: ánimo, energía, estrés, sueño, agua y ejercicio de un día, con guardado automático, y tendencias debajo. */
export function WellbeingPage() {
  const { isPet } = useActive()
  if (isPet) return <PeopleOnlyPage title="Bienestar diario" />
  return <WellbeingContent />
}

function WellbeingContent() {
  const db = useDb()
  const t = today()
  const [day, setDay] = useState(t)
  const [log, setLog] = useState<DailyLog | null>(null)

  useEffect(() => {
    let alive = true
    // Mientras carga se sigue mostrando el día anterior (sus ediciones conservan su propio epochDay).
    void db.dailyLogs.get(day).then((l) => { if (alive) setLog(l ?? emptyDailyLog(day)) })
    return () => { alive = false }
  }, [db, day])

  const update = (next: DailyLog) => {
    setLog(next)
    submitDailyLog(db, next)
  }
  const l = log

  return (
    <Page title="Bienestar diario">
      <DayNavigator day={day} today={t} onChange={(d) => setDay(Math.min(d, t))} />

      {l && (
        <>
          <Card title="¿Cómo te sientes?">
            <IconScale title="Ánimo" icons={WellbeingScale.moodIcons} labels={WellbeingScale.moodLabels} value={l.mood} onChange={(v) => update({ ...l, mood: v })} colorOf={WellbeingScale.goodColor} />
            <IconScale title="Energía" icons={WellbeingScale.energyIcons} labels={WellbeingScale.energyLabels} value={l.energy} onChange={(v) => update({ ...l, energy: v })} colorOf={WellbeingScale.goodColor} />
            <IconScale title="Estrés" icons={null} labels={WellbeingScale.stressLabels} value={l.stress} onChange={(v) => update({ ...l, stress: v })} colorOf={WellbeingScale.stressColor} />
          </Card>

          <Card title="Hábitos del día">
            <StepperRow
              icon={Moon} label="Sueño" tint="var(--ovulation)"
              valueText={l.sleepHours !== null ? `${Fmt.num(l.sleepHours, 1)} h` : null}
              onMinus={() => update({ ...l, sleepHours: HabitSteps.sleepMinus(l.sleepHours) })}
              onPlus={() => update({ ...l, sleepHours: HabitSteps.sleepPlus(l.sleepHours) })}
            />
            <StepperRow
              icon={GlassWater} label="Agua" tint="var(--fertile)"
              valueText={l.waterGlasses !== null ? (l.waterGlasses === 1 ? '1 vaso' : `${l.waterGlasses} vasos`) : null}
              onMinus={() => update({ ...l, waterGlasses: HabitSteps.waterMinus(l.waterGlasses) })}
              onPlus={() => update({ ...l, waterGlasses: HabitSteps.waterPlus(l.waterGlasses) })}
            />
            <StepperRow
              icon={Footprints} label="Ejercicio" tint="var(--good)"
              valueText={l.exerciseMin !== null ? Fmt.duration(l.exerciseMin) : null}
              onMinus={() => update({ ...l, exerciseMin: HabitSteps.exerciseMinus(l.exerciseMin) })}
              onPlus={() => update({ ...l, exerciseMin: HabitSteps.exercisePlus(l.exerciseMin) })}
            />
          </Card>

          <Card title="Nota del día">
            <TextArea label="¿Algo que quieras recordar?" value={l.notes} onChange={(v) => update({ ...l, notes: v.slice(0, 1000) })} rows={2} />
          </Card>

          <div className="wb-autosave">
            <span className="small muted grow">Los cambios se guardan automáticamente.</span>
            {!WellbeingScale.isEmpty(l) && (
              <Button variant="text" onClick={() => update(emptyDailyLog(l.epochDay))} style={{ color: 'var(--error)' }}>Borrar este día</Button>
            )}
          </div>
        </>
      )}

      <TrendsSection />
    </Page>
  )
}

function DayNavigator(props: { day: number; today: number; onChange: (d: number) => void }) {
  const { day, today: t } = props
  const rel = day === t ? 'Hoy' : day === t - 1 ? 'Ayer' : (() => { const s = Fmt.daysFromNow(day); return s.charAt(0).toUpperCase() + s.slice(1) })()
  return (
    <div className="wb-daynav">
      <button type="button" className="icon-btn" aria-label="Día anterior" onClick={() => props.onChange(day - 1)}><ChevronLeft size={26} /></button>
      <div className="wb-day">
        <b>{rel}</b>
        <span className="small muted">{Fmt.dateLong(day)}</span>
        <input
          type="date"
          aria-label="Elegir fecha"
          value={epochDayToIso(day)}
          max={epochDayToIso(t)}
          onChange={(e) => { const d = isoToEpochDay(e.target.value); if (d !== null) props.onChange(d) }}
        />
      </div>
      <button type="button" className="icon-btn" aria-label="Día siguiente" disabled={day >= t} onClick={() => day < t && props.onChange(day + 1)}><ChevronRight size={26} /></button>
    </div>
  )
}

/** Escala de 1 a 5 con iconos (o números si `icons` es null). Tocar el valor elegido lo quita. */
function IconScale(props: {
  title: string
  icons: LucideIcon[] | null
  labels: string[]
  value: number | null
  onChange: (v: number | null) => void
  colorOf: (level: number) => string
}) {
  const { value } = props
  return (
    <div>
      <div className="wb-scale-head">
        <span className="t">{props.title}</span>
        {value !== null ? <span className="v" style={{ color: props.colorOf(value) }}>{props.labels[value - 1] ?? ''}</span> : <span className="small muted">Sin registrar</span>}
      </div>
      <div className="wb-scale-row" role="group" aria-label={props.title}>
        {[1, 2, 3, 4, 5].map((level) => {
          const on = value === level
          const color = props.colorOf(level)
          const I = props.icons?.[level - 1]
          return (
            <button
              key={level}
              type="button"
              className={`wb-level${on ? ' on' : ''}`}
              aria-pressed={on}
              aria-label={`${props.title}: ${props.labels[level - 1]} (${level} de 5)`}
              style={on ? { background: `color-mix(in srgb, ${color} 22%, transparent)`, color } : undefined}
              onClick={() => props.onChange(on ? null : level)}
            >
              {I ? <I size={on ? 32 : 27} /> : level}
            </button>
          )
        })}
      </div>
      {!props.icons && <div className="wb-scale-ends"><span>{props.labels[0]}</span><span>{props.labels[props.labels.length - 1]}</span></div>}
    </div>
  )
}

/** Fila con icono, etiqueta y contador − valor +. */
function StepperRow(props: { icon: LucideIcon; label: string; tint: string; valueText: string | null; onMinus: () => void; onPlus: () => void }) {
  const I = props.icon
  return (
    <div className="wb-stepper">
      <span className="wb-ic" style={{ background: `color-mix(in srgb, ${props.tint} 15%, transparent)`, color: props.tint }}><I size={22} /></span>
      <span className="wb-label">{props.label}</span>
      <button type="button" className="wb-step-btn" aria-label={`Menos ${props.label}`} disabled={props.valueText === null} onClick={props.onMinus}><Minus size={22} /></button>
      <span className={`wb-value${props.valueText === null ? ' none' : ''}`} aria-live="polite">{props.valueText ?? '—'}</span>
      <button type="button" className="wb-step-btn" aria-label={`Más ${props.label}`} onClick={props.onPlus}><Plus size={22} /></button>
    </div>
  )
}

function TrendsSection() {
  const db = useDb()
  const logs = useLiveQuery(() => db.dailyLogs.orderBy('epochDay').toArray(), [db]) ?? []
  const [range, setRange] = useState<ChartRange>('m1')
  const t = today()
  const from = rangeFromDay(range, t)
  const inRange = useMemo(() => logs.filter((l) => from === null || l.epochDay >= from), [logs, from])
  const a = useMemo(() => wellbeingAverages(inRange, t), [inRange, t])

  const points = (sel: (l: DailyLog) => number | null): ChartPoint[] =>
    inRange.flatMap((l) => { const v = sel(l); return v === null ? [] : [{ x: l.epochDay, y: v }] })
  const moodPts = points((l) => l.mood), energyPts = points((l) => l.energy), stressPts = points((l) => l.stress), sleepPts = points((l) => l.sleepHours)
  const one = (v: number | null, suffix = ''): string => (v === null ? '—' : Fmt.num(v, 1) + suffix)

  return (
    <Card title="Tendencias">
      <RangeSelector value={range} onChange={setRange} />
      {inRange.length === 0 ? (
        <p className="muted" style={{ padding: '10px 0' }}>Aún no hay registros en este periodo. Anota cómo te sientes unos días y aquí verás tu evolución.</p>
      ) : (
        <>
          <div className="stats">
            <StatTile label="Ánimo" value={one(a.mood)} sub="promedio de 5" />
            <StatTile label="Energía" value={one(a.energy)} sub="promedio de 5" />
            <StatTile label="Estrés" value={one(a.stress)} sub="promedio de 5" />
          </div>
          <div className="stats">
            <StatTile label="Sueño" value={one(a.sleep, ' h')} sub="por noche" />
            <StatTile label="Agua" value={one(a.water)} sub="vasos al día" />
            <StatTile label="Ejercicio" value={a.exerciseTotal > 0 ? Fmt.num(a.exercisePerWeek, 0) : '—'} sub="min por semana" />
          </div>

          {moodPts.length + energyPts.length + stressPts.length > 0 && (
            <>
              <div className="wb-chart-title">Ánimo, energía y estrés</div>
              <LineChart
                series={[
                  { name: 'Ánimo', points: moodPts, color: 'var(--chart-1)', dots: moodPts.length <= 31 },
                  { name: 'Energía', points: energyPts, color: 'var(--chart-4)', dots: false, strokeWidth: 2 },
                  { name: 'Estrés', points: stressPts, color: 'var(--chart-2)', dots: false, dashed: true, strokeWidth: 2 },
                ].filter((s) => s.points.length > 0)}
                yMin={1}
                yMax={5}
                height={200}
              />
              <ChartLegend items={[['Ánimo', 'var(--chart-1)'], ['Energía', 'var(--chart-4)'], ['Estrés (línea punteada)', 'var(--chart-2)']]} />
            </>
          )}

          {sleepPts.length > 0 && (
            <>
              <div className="wb-chart-title">Horas de sueño</div>
              <LineChart series={[{ name: 'Sueño', points: sleepPts, color: 'var(--ovulation)', dots: sleepPts.length <= 31, fill: true }]} unit="h" height={180} />
            </>
          )}
          <p className="small muted">Toca o arrastra sobre una gráfica para ver el valor de cada día.</p>
        </>
      )}
    </Card>
  )
}
