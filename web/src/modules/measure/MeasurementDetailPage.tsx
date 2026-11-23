import { useEffect, useMemo, useState } from 'react'
import { useParams, useSearchParams } from 'react-router-dom'
import { useLiveQuery } from 'dexie-react-hooks'
import { Pencil, Plus } from 'lucide-react'
import { measureType, type MeasureType } from '../../data/catalogs'
import { useDb, useProfileRow } from '../../data/profiles'
import type { Measurement } from '../../data/types'
import { Fmt, millisToEpochDay, today } from '../../lib/dates'
import {
  Button, Card, ChartLegend, ConfirmDialog, EmptyState, Fab, InfoNote, LineChart, Page, Pill, RangeSelector, SectionLabel, Sheet,
  StatTile, rangeFromDay, rangeLabel, useToast, type ChartBand, type ChartHLine, type ChartRange, type ChartSeries,
} from '../../ui'
import {
  bmi as bmiOf, bmiCategory, dayX, goalProgress, goalProjection, goalReached, healthyWeightRange, movingAverage, points, regression,
  summary, type Assessment, type TPoint,
} from './stats'
import {
  MINOR_BMI_NOTE, assess, band as refBand, inGrams, isAdult, note as refNote, showBmi, showBmiCategory, weightDelta, weightText,
  type MeasureWho,
} from './who'
import {
  MeasureForm, NumberDialog, amount, deltaText, levelColor, measureIcon, recordHeight, syncProfileHeight, updateProfile,
  useMeasureWho, valueText, valueWithUnit,
} from './shared'
import './measure.css'

const HISTORY_PAGE = 40

/** Detalle de un tipo de medición: gráfica, estadísticas, interpretación orientativa e historial. */
export function MeasurementDetailPage() {
  const params = useParams()
  const type = measureType(params.type ?? '') ?? measureType('weight')!
  // Una instancia por tipo: al pasar de un tipo a otro se reinician rango, formulario y diálogos.
  return <Detail key={type.key} type={type} />
}

function Detail({ type }: { type: MeasureType }) {
  const db = useDb()
  const toast = useToast()
  const loaded = useLiveQuery(() => db.measurements.where('type').equals(type.key).toArray(), [db, type.key])
  const profile = useProfileRow()
  const heightCm = profile?.heightCm ?? null
  const goal = profile?.goalWeightKg ?? null
  const who = useMeasureWho(profile)
  const pet = who.isPet
  const isWeight = type.key === 'weight'

  const [search, setSearch] = useSearchParams()
  const [range, setRange] = useState<ChartRange>('m3')
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<Measurement | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<Measurement | null>(null)
  const [goalDialog, setGoalDialog] = useState(false)
  const [heightDialog, setHeightDialog] = useState(false)
  const [showAll, setShowAll] = useState(false)

  // "?add=1" abre el formulario una sola vez: se quita de la dirección para que no reaparezca al regresar.
  useEffect(() => {
    if (search.get('add') !== '1') return
    setEditing(null)
    setFormOpen(true)
    setSearch({}, { replace: true })
  }, [search, setSearch])

  const openNew = () => { setEditing(null); setFormOpen(true) }
  const asc = useMemo(() => [...(loaded ?? [])].sort((a, b) => a.timestamp - b.timestamp), [loaded])
  const desc = useMemo(() => [...asc].reverse(), [asc])
  const t = today()
  const inRange = useMemo(() => {
    const from = rangeFromDay(range, t)
    return from === null ? asc : asc.filter((m) => dayX(m.timestamp) >= from)
  }, [asc, range, t])
  const deltas = useMemo(() => {
    const map = new Map<number, number>()
    for (let i = 1; i < asc.length; i++) map.set(asc[i].id!, asc[i].value - asc[i - 1].value)
    return map
  }, [asc])

  const save = async (m: Measurement) => {
    setFormOpen(false)
    if (m.id === undefined) await db.measurements.add(m)
    else await db.measurements.put(m)
    if (type.key === 'height') await syncProfileHeight(db)
    toast(m.id === undefined ? 'Medición guardada' : 'Cambios guardados')
  }
  const remove = async (m: Measurement) => {
    setDeleteTarget(null)
    setFormOpen(false)
    if (m.id !== undefined) await db.measurements.delete(m.id)
    if (type.key === 'height') await syncProfileHeight(db)
    toast('Medición eliminada')
  }

  const last = asc[asc.length - 1]
  const lastAssessment = last && !isWeight ? assess(who, type, last, heightCm) : null
  const typeNote = refNote(who, type)

  return (
    <Page title={type.label} fab={<Fab icon={Plus} label="Agregar" onClick={openNew} />}>
      {loaded === undefined ? null : asc.length === 0 ? (
        <EmptyState
          icon={measureIcon(type)}
          title={`Sin registros de ${type.label.toLowerCase()}`}
          message="Agrega tu primera medición para ver aquí tu gráfica, tus estadísticas y tu historial."
          actionLabel={`Registrar ${type.label.toLowerCase()}`}
          onAction={openNew}
        />
      ) : (
        <>
          <ChartCard type={type} asc={asc} inRange={inRange} range={range} onRange={setRange} goal={goal} who={who} />
          <StatsSection type={type} latest={last} inRange={inRange} range={range} heightCm={heightCm} goal={goal} who={who} />
          {isWeight ? (
            <>
              <GoalCard asc={asc} goal={goal} who={who} onEdit={() => setGoalDialog(true)} />
              {pet
                ? typeNote && <ReferenceNoteCard note={typeNote} /> /* las mascotas no llevan IMC */
                : <BmiCard latest={last} heightCm={heightCm} who={who} onEditHeight={() => setHeightDialog(true)} />}
            </>
          ) : lastAssessment ? (
            <AssessmentCard type={type} latest={last} a={lastAssessment} who={who} />
          ) : typeNote ? (
            <ReferenceNoteCard note={typeNote} />
          ) : type.key === 'waist' && heightCm === null && isAdult(who) ? (
            <Card title="Referencia">
              <p>Con tu estatura puedo mostrarte la relación cintura/estatura, una referencia orientativa.</p>
              <div><Button variant="outline" onClick={() => setHeightDialog(true)}>Agregar estatura</Button></div>
            </Card>
          ) : null}
          <SectionLabel>{asc.length === 1 ? 'Historial · 1 registro' : `Historial · ${asc.length} registros`}</SectionLabel>
          {(showAll ? desc : desc.slice(0, HISTORY_PAGE)).map((m) => (
            <HistoryRow
              key={m.id}
              type={type}
              m={m}
              assessment={isWeight ? null : assess(who, type, m, heightCm)}
              delta={type.secondLabel ? null : deltas.get(m.id!) ?? null}
              isPet={pet}
              onClick={() => { setEditing(m); setFormOpen(true) }}
            />
          ))}
          {!showAll && desc.length > HISTORY_PAGE && (
            <Button variant="outline" full onClick={() => setShowAll(true)}>Ver todo el historial ({desc.length})</Button>
          )}
        </>
      )}

      <Sheet open={formOpen} onClose={() => setFormOpen(false)} title={`${editing ? 'Editar' : 'Registrar'} ${type.label.toLowerCase()}`}>
        <MeasureForm
          key={editing?.id ?? 'new'}
          type={type}
          initial={editing}
          isPet={pet}
          onSave={(m) => { void save(m) }}
          onCancel={() => setFormOpen(false)}
          onDelete={editing ? () => setDeleteTarget(editing) : undefined}
        />
      </Sheet>

      <ConfirmDialog
        open={deleteTarget !== null}
        title="Eliminar medición"
        text={deleteTarget ? `Se eliminará ${valueWithUnit(type, deleteTarget, pet)} del ${Fmt.dateTime(deleteTarget.timestamp)}. Esta acción no se puede deshacer.` : ''}
        onConfirm={() => { if (deleteTarget) void remove(deleteTarget) }}
        onClose={() => setDeleteTarget(null)}
      />

      <NumberDialog
        open={goalDialog}
        title="Meta de peso"
        label="Meta"
        unit="kg"
        initial={goal}
        min={pet ? 0.01 : 20}
        max={400}
        decimals={pet ? 3 : 1}
        text={pet
          ? 'Si tu veterinario te indicó un peso ideal, anótalo aquí como meta (en kilogramos; 0.35 kg son 350 g).'
          : 'Elige un peso objetivo realista; si tienes dudas, platícalo con un profesional de la salud.'}
        onSave={(v) => { setGoalDialog(false); void updateProfile(db, (p) => ({ ...p, goalWeightKg: v })) }}
        onClose={() => setGoalDialog(false)}
        clearLabel="Quitar meta"
        onClear={() => { setGoalDialog(false); void updateProfile(db, (p) => ({ ...p, goalWeightKg: null })) }}
      />
      <NumberDialog
        open={heightDialog}
        title="Estatura"
        label="Estatura"
        unit="cm"
        initial={heightCm}
        min={30}
        max={250}
        text="Se guarda en la ficha y en el historial de estatura, y se usa para calcular el IMC."
        onSave={(v) => { setHeightDialog(false); void recordHeight(db, v) }}
        onClose={() => setHeightDialog(false)}
      />
    </Page>
  )
}

/** "36.2–37.1", o un solo valor si mínimo y máximo coinciden. */
function minMax(min: number, max: number, decimals: number): string {
  const a = Fmt.num(min, decimals)
  const b = Fmt.num(max, decimals)
  return a === b ? a : `${a}–${b}`
}

const soft = (color: string): string => `color-mix(in srgb, ${color} 15%, transparent)`

function ChartCard(props: {
  type: MeasureType
  asc: Measurement[]
  inRange: Measurement[]
  range: ChartRange
  onRange: (r: ChartRange) => void
  goal: number | null
  who: MeasureWho
}) {
  const { type, asc, inRange, goal, who } = props
  const isWeight = type.key === 'weight'
  const chart = useMemo(() => {
    if (!inRange.length) return null
    const series: ChartSeries[] = []
    const bands: ChartBand[] = []
    const hLines: ChartHLine[] = []
    const legend: [string, string][] = []
    const firstX = dayX(inRange[0].timestamp)
    // Mascotas de menos de 1 kg: la gráfica de peso se muestra en gramos (los datos siguen en kg).
    const grams = isWeight && who.isPet && inRange.every((m) => inGrams(m.value, true)) && (goal === null || inGrams(goal, true))
    const scale = grams ? 1000 : 1
    const unit = grams ? 'g' : type.unit
    const yDecimals = grams ? 0 : isWeight && who.isPet ? 2 : 1
    const scaled = (pts: TPoint[]): TPoint[] => (scale === 1 ? pts : pts.map((p) => ({ x: p.x, y: p.y * scale })))

    if (isWeight) {
      const pts = scaled(points(inRange))
      series.push({ name: 'Peso', points: pts, color: 'var(--primary)', fill: true })
      legend.push(['Peso', 'var(--primary)'])
      if (pts.length >= 5) {
        const ma = scaled(movingAverage(points(asc)).filter((p) => p.x >= firstX))
        series.push({ name: 'Media 7 días', points: ma, color: 'var(--chart-4)', dots: false, strokeWidth: 2, selectable: false })
        legend.push(['Media móvil 7 días', 'var(--chart-4)'])
      }
      const reg = pts.length >= 3 ? regression(pts) : null
      if (reg) {
        const a = pts[0].x
        const b = pts[pts.length - 1].x
        series.push({ name: 'Tendencia', points: [{ x: a, y: reg.at(a) }, { x: b, y: reg.at(b) }], color: 'var(--outline)', dots: false, dashed: true, strokeWidth: 1.5, selectable: false })
        legend.push(['Tendencia', 'var(--outline)'])
      }
      if (goal !== null) {
        hLines.push({ y: goal * scale, color: 'var(--good)', label: `Meta ${Fmt.num(goal * scale, yDecimals)}` })
        legend.push(['Meta', 'var(--good)'])
      }
    } else if (type.key === 'bp') {
      series.push({ name: 'Sistólica', points: points(inRange), color: 'var(--chart-2)' })
      series.push({ name: 'Diastólica', points: points(inRange, true), color: 'var(--chart-3)' })
      if (isAdult(who)) {
        bands.push({ min: 90, max: 120, color: soft('var(--chart-2)') })
        bands.push({ min: 60, max: 80, color: soft('var(--chart-3)') })
      }
      legend.push(['Sistólica (alta)', 'var(--chart-2)'], ['Diastólica (baja)', 'var(--chart-3)'])
    } else {
      const b = refBand(who, type, inRange)
      series.push({ name: type.label, points: points(inRange), color: 'var(--primary)', fill: b === null })
      if (b) {
        bands.push({ min: b[0], max: b[1], color: soft('var(--good)') })
        legend.push([type.label, 'var(--primary)'], [who.isPet ? 'Rango habitual (orientativo)' : 'Rango de referencia', 'var(--good)'])
      }
    }
    return { series, bands, hLines, legend, unit, yDecimals }
  }, [type, asc, inRange, goal, who, isWeight])

  return (
    <Card>
      <RangeSelector value={props.range} onChange={props.onRange} />
      {!chart ? (
        <div className="mz-empty-chart">No hay registros en este periodo.<br />Elige un rango mayor.</div>
      ) : (
        <>
          <LineChart series={chart.series} bands={chart.bands} hLines={chart.hLines} yFormatter={(y) => Fmt.num(y, chart.yDecimals)} unit={chart.unit} />
          {chart.legend.length > 0 && <ChartLegend items={chart.legend} />}
          {type.key === 'bp' && isAdult(who) && (
            <p className="small muted">Las franjas marcan el rango de referencia de cada valor (90–120 y 60–80 mmHg).</p>
          )}
          <p className="small muted">Toca o arrastra sobre la gráfica para ver cada valor.</p>
        </>
      )}
    </Card>
  )
}

/** "66.2–68.7" + unidad; el peso de mascotas va en gramos si todo el rango es menor a 1 kg. */
function rangeParts(type: MeasureType, min: number, max: number, isPet: boolean): [string, string] {
  if (type.key === 'weight' && isPet && inGrams(max, true)) return [minMax(min * 1000, max * 1000, 0), 'g']
  if (type.key === 'weight' && isPet) return [minMax(min, max, 2), 'kg']
  return [minMax(min, max, type.decimals), type.unit]
}

function StatsSection(props: {
  type: MeasureType
  latest: Measurement
  inRange: Measurement[]
  range: ChartRange
  heightCm: number | null
  goal: number | null
  who: MeasureWho
}) {
  const { type, latest, inRange, goal, who } = props
  const d = type.decimals
  const pet = who.isPet
  const isWeight = type.key === 'weight'
  const rangeText = props.range === 'all' ? 'todo el historial' : `últimos ${rangeLabel(props.range)}`
  const s1 = useMemo(() => summary(inRange.map((m) => m.value)), [inRange])
  const s2 = useMemo(() => summary(inRange.filter((m) => m.value2 !== null && m.value2 !== undefined).map((m) => m.value2 as number)), [inRange])
  const reg = useMemo(() => {
    const pts = points(inRange)
    return pts.length >= 3 ? regression(pts) : null
  }, [inRange])
  const latestDay = Fmt.relativeDay(millisToEpochDay(latest.timestamp))

  if (type.secondLabel) {
    return (
      <div className="stats grid">
        <StatTile label="Última" value={valueText(type, latest)} sub={latestDay} />
        <StatTile label="Promedio" value={s1 && s2 ? `${Fmt.num(s1.avg, 0)}/${Fmt.num(s2.avg, 0)}` : '—'} sub={rangeText} />
        <StatTile label="Sistólica" value={s1 ? minMax(s1.min, s1.max, d) : '—'} sub="mín – máx" />
        <StatTile label="Diastólica" value={s2 ? minMax(s2.min, s2.max, d) : '—'} sub="mín – máx" />
      </div>
    )
  }
  const change = s1 && s1.count >= 2 ? s1.change : null
  const flat = pet ? 0.0005 : 0.05
  const changeColor =
    !isWeight || goal === null || change === null || !s1 || Math.abs(change) < flat
      ? undefined
      : Math.sign(goal - s1.first) === Math.sign(change) ? 'var(--good)' : 'var(--warn)'
  const mm = s1 ? rangeParts(type, s1.min, s1.max, pet) : null
  const bmi = bmiOf(latest.value, props.heightCm)
  // La categoría de la OMS es para personas adultas; en menores solo se muestra el número.
  const category = bmi !== null && showBmiCategory(who) ? bmiCategory(bmi) : null
  return (
    <div className="stats grid">
      <StatTile label={isWeight ? 'Actual' : 'Última'} value={amount(type, latest.value, pet)} sub={latestDay} />
      <StatTile label="Cambio" value={change !== null ? deltaText(type, change, pet) : '—'} sub={rangeText} color={changeColor} />
      <StatTile label="Promedio" value={s1 ? amount(type, s1.avg, pet, Math.max(d, 1)) : '—'} sub={rangeText} />
      <StatTile label="Mín – máx" value={mm?.[0] ?? '—'} sub={mm?.[1] ?? type.unit} />
      {isWeight && (
        <>
          <StatTile
            label="Ritmo"
            value={reg ? (pet ? `${weightDelta(reg.perWeek, true)}/sem` : `${Fmt.signed(reg.perWeek, 2)} kg/sem`) : '—'}
            sub={`tendencia, ${rangeText}`}
          />
          {showBmi(who) ? (
            <StatTile
              label="IMC"
              value={bmi !== null ? Fmt.num(bmi, 1) : '—'}
              sub={bmi === null ? 'falta la estatura' : category ? category.label : 'se interpreta por edad'}
              color={category ? levelColor(category.level) : undefined}
            />
          ) : (
            <StatTile label="Registros" value={`${inRange.length}`} sub={rangeText} />
          )}
        </>
      )}
    </div>
  )
}

/** Nota orientativa propia del perfil (especie de la mascota, o aviso de que en menores no se clasifica). */
function ReferenceNoteCard(props: { note: string }) {
  return (
    <Card title="Referencia">
      <p>{props.note}</p>
      <InfoNote>Es solo una orientación; no sustituye la valoración de un profesional.</InfoNote>
    </Card>
  )
}

function AssessmentCard(props: { type: MeasureType; latest: Measurement; a: Assessment; who: MeasureWho }) {
  const { type, latest, a, who } = props
  const context = [Fmt.relativeDateTime(latest.timestamp), latest.tag].filter(Boolean).join(' · ')
  return (
    <Card title="Última medición">
      <div>
        <div className="row"><span className="mz-title">{valueWithUnit(type, latest, who.isPet)}</span><Pill text={a.label} color={levelColor(a.level)} /></div>
        <div className="small muted">{context}</div>
      </div>
      {a.detail && <p>{a.detail}</p>}
      <InfoNote>
        {who.isMinor
          ? 'Referencia orientativa, no un diagnóstico. En niñas, niños y adolescentes coméntalo con su pediatra.'
          : 'Referencia orientativa para personas adultas; no es un diagnóstico. Tu médico puede indicarte otros valores objetivo.'}
      </InfoNote>
    </Card>
  )
}

function GoalCard(props: { asc: Measurement[]; goal: number | null; who: MeasureWho; onEdit: () => void }) {
  const { asc, goal, who } = props
  const pet = who.isPet
  const t = today()
  const pts = useMemo(() => points(asc), [asc])
  const projection = useMemo(() => (goal === null ? null : goalProjection(pts, goal, t)), [pts, goal, t])
  const recentRate = useMemo(() => {
    const last = pts[pts.length - 1]
    const recent = pts.filter((p) => p.x >= last.x - 60)
    return recent.length >= 3 ? regression(recent)?.perWeek ?? null : null
  }, [pts])
  if (goal === null) {
    return (
      <Card title="Meta de peso">
        <p className="muted">
          {pet
            ? 'Si tu veterinario te indicó un peso ideal, defínelo como meta para ver cuánto le falta y una fecha estimada según su tendencia.'
            : 'Define una meta para ver cuánto te falta y una fecha estimada según tu tendencia.'}
        </p>
        <div><Button variant="outline" onClick={props.onEdit}>Definir meta</Button></div>
      </Card>
    )
  }
  const first = asc[0].value
  const current = asc[asc.length - 1].value
  const reached = goalReached(first, current, goal)
  const projecting = !reached && projection !== null && projection.daysLeft > 0
  let message: string
  if (reached) {
    message = pet ? 'Llegó a su meta. Puedes mantenerla o definir una nueva.' : 'Llegaste a tu meta. Puedes mantenerla o definir una nueva.'
  } else if (projecting && projection) {
    const rate = recentRate === null ? '' : pet ? ` (${weightDelta(recentRate, true)} por semana)` : ` (${Fmt.signed(recentRate, 2)} kg por semana)`
    const when = `alrededor del ${Fmt.date(projection.epochDay)}, ${Fmt.daysFromNow(projection.epochDay)}.`
    message = pet ? `A su ritmo reciente${rate} llegaría ${when}` : `A tu ritmo reciente${rate} llegarías ${when}`
  } else if (pts.length < 3) {
    message = pet ? 'Registra su peso varias veces para estimar una fecha.' : 'Registra tu peso varias veces para estimar una fecha.'
  } else {
    message = pet
      ? 'Con la tendencia de las últimas semanas no se está acercando a la meta, así que no hay fecha estimada.'
      : 'Con la tendencia de las últimas semanas no te estás acercando a la meta, así que no hay fecha estimada.'
  }
  return (
    <Card title="Meta de peso" action={<Button variant="text" small onClick={props.onEdit}>Editar</Button>}>
      <div className="row" style={{ alignItems: 'baseline' }}>
        <span className="mz-title grow">{weightText(goal, pet)}</span>
        <b style={{ color: reached ? 'var(--good)' : 'var(--primary)' }}>
          {reached ? '¡Meta alcanzada!' : `${pet ? 'Le' : 'Te'} faltan ${weightText(Math.abs(goal - current), pet)}`}
        </b>
      </div>
      <div>
        <div className={`mz-progress${reached ? ' done' : ''}`}><div style={{ width: `${(reached ? 1 : goalProgress(first, current, goal)) * 100}%` }} /></div>
        <div className="row small muted" style={{ marginTop: 4 }}>
          <span className="grow">Inicio {weightText(first, pet)}</span><span>Actual {weightText(current, pet)}</span>
        </div>
      </div>
      <p>{message}</p>
      {projecting && <p className="small muted">Es una estimación: el peso cambia de forma natural día con día.</p>}
    </Card>
  )
}

function BmiCard(props: { latest: Measurement; heightCm: number | null; who: MeasureWho; onEditHeight: () => void }) {
  const { latest, heightCm, who } = props
  const bmi = bmiOf(latest.value, heightCm)
  if (bmi === null || heightCm === null) {
    return (
      <Card title="Índice de masa corporal">
        <p className="muted">
          {who.isMinor ? 'Agrega su estatura para calcular el IMC a partir del peso.' : 'Agrega tu estatura para calcular tu IMC a partir de tu peso.'}
        </p>
        <div><Button variant="outline" onClick={props.onEditHeight}>Agregar estatura</Button></div>
      </Card>
    )
  }
  const action = <Button variant="text" small onClick={props.onEditHeight}>Estatura</Button>
  const basis = `Con ${Fmt.num(latest.value, 1)} kg y ${Fmt.num(heightCm, 1)} cm de estatura.`
  if (!showBmiCategory(who)) {
    // Menores de edad: el número sí, pero sin categorías ni "peso saludable" de personas adultas.
    return (
      <Card title="Índice de masa corporal" action={action}>
        <div>
          <div className="mz-title">{Fmt.num(bmi, 1)}</div>
          <div className="small muted">{basis}</div>
        </div>
        <InfoNote>{MINOR_BMI_NOTE}</InfoNote>
      </Card>
    )
  }
  const a = bmiCategory(bmi)
  const [lo, hi] = healthyWeightRange(heightCm)
  return (
    <Card title="Índice de masa corporal" action={action}>
      <div>
        <div className="row"><span className="mz-title">{Fmt.num(bmi, 1)}</span><Pill text={a.label} color={levelColor(a.level)} /></div>
        <div className="small muted">{basis} {a.detail}</div>
      </div>
      <p>Para tu estatura, un IMC de 18.5 a 24.9 equivale a entre {Fmt.num(lo, 1)} y {Fmt.num(hi, 1)} kg.</p>
      <InfoNote>
        El IMC es una referencia orientativa para personas adultas: no distingue músculo de grasa ni aplica igual en el embarazo, la niñez o en deportistas.
      </InfoNote>
    </Card>
  )
}

function HistoryRow(props: { type: MeasureType; m: Measurement; assessment: Assessment | null; delta: number | null; isPet: boolean; onClick: () => void }) {
  const { type, m, assessment, delta, isPet } = props
  const petWeight = isPet && type.key === 'weight'
  const showDelta = delta !== null && Math.abs(delta) >= (petWeight ? 0.0005 : 0.05)
  return (
    <Card className="mz-history" onClick={props.onClick}>
      <div className="row">
        <div className="grow">
          <div className="mz-value">{valueWithUnit(type, m, isPet)}</div>
          <div className="small muted">{[Fmt.relativeDateTime(m.timestamp), m.tag].filter(Boolean).join(' · ')}</div>
          {m.notes && <div className="small mz-notes">{m.notes}</div>}
        </div>
        {assessment ? (
          <Pill text={assessment.label} color={levelColor(assessment.level)} />
        ) : showDelta ? (
          <span className="muted">{petWeight ? weightDelta(delta!, true) : Fmt.signed(delta!, Math.max(type.decimals, 1))}</span>
        ) : null}
        <Pencil size={18} color="var(--muted)" aria-label="Editar" />
      </div>
    </Card>
  )
}
