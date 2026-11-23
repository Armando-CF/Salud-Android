import { useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { useLiveQuery } from 'dexie-react-hooks'
import { ChevronRight, Plus } from 'lucide-react'
import { measureType, type MeasureType } from '../../data/catalogs'
import { useDb, useProfileRow } from '../../data/profiles'
import type { Measurement } from '../../data/types'
import { Fmt, millisToEpochDay } from '../../lib/dates'
import { Button, Card, InfoNote, Page, Pill, SectionLabel, Sparkline, StatTile } from '../../ui'
import { weightSummary, type WeightSummary } from './stats'
import { MINOR_BMI_NOTE, PET_GENERAL_NOTE, assess, note, showBmi, showBmiCategory, types, weightDelta, weightParts, weightText, type MeasureWho } from './who'
import { levelColor, measureIcon, useMeasureWho, valueWithUnit } from './shared'
import './measure.css'

/** Resumen de mediciones: tarjeta principal de peso y una fila por cada tipo que aplica al perfil. */
export function MeasurementsPage() {
  const db = useDb()
  const nav = useNavigate()
  const all = useLiveQuery(() => db.measurements.toArray(), [db])
  const profile = useProfileRow()
  const who = useMeasureWho(profile)
  const height = profile?.heightCm ?? null
  const goal = profile?.goalWeightKg ?? null

  const latestByType = useMemo(() => {
    const map = new Map<string, Measurement>()
    for (const m of all ?? []) {
      const cur = map.get(m.type)
      if (!cur || m.timestamp > cur.timestamp) map.set(m.type, m)
    }
    return map
  }, [all])
  const summary = useMemo(
    () => weightSummary((all ?? []).filter((m) => m.type === 'weight'), who.isPet ? null : height, goal),
    [all, height, goal, who.isPet],
  )
  // Solo los tipos que aplican al perfil (las mascotas no llevan presión, glucosa, IMC…).
  const others = types(who.isPet).filter((t) => t.key !== 'weight')
  const open = (key: string, add = false) => nav(`/mediciones/${key}${add ? '?add=1' : ''}`)

  return (
    <Page title="Mediciones">
      {all !== undefined && (
        <>
          <WeightCard summary={summary} who={who} onOpen={() => open('weight')} onAdd={() => open('weight', true)} />
          <SectionLabel>Otras mediciones</SectionLabel>
          <Card className="mz-rows">
            {others.map((type) => (
              <MeasureRow key={type.key} type={type} latest={latestByType.get(type.key)} who={who} heightCm={height} onOpen={() => open(type.key)} onAdd={() => open(type.key, true)} />
            ))}
          </Card>
          <InfoNote>
            {who.isPet
              ? PET_GENERAL_NOTE
              : who.isMinor
                ? 'En niñas, niños y adolescentes los valores normales cambian con la edad: aquí se guardan y grafican, pero peso, presión y pulso no se clasifican con rangos de personas adultas. Su pediatra es quien los interpreta.'
                : 'Las clasificaciones son orientativas y no sustituyen la valoración de un profesional de la salud.'}
          </InfoNote>
        </>
      )}
    </Page>
  )
}

function WeightCard(props: { summary: WeightSummary | null; who: MeasureWho; onOpen: () => void; onAdd: () => void }) {
  const { summary, who } = props
  const pet = who.isPet
  const addButton = (
    <div onClick={(e) => e.stopPropagation()}>
      <Button full icon={Plus} onClick={props.onAdd}>Registrar peso</Button>
    </div>
  )
  if (!summary) {
    return (
      <Card title="Peso" onClick={props.onOpen} action={<ChevronRight size={22} color="var(--muted)" aria-label="Ver detalle" />}>
        <p className="muted">
          {pet
            ? `Aún no registras el peso de ${who.name}. Anótalo con regularidad (por ejemplo en cada visita al veterinario) para ver su tendencia.`
            : 'Aún no registras tu peso. Anótalo con regularidad para ver tu tendencia, tu IMC y cuánto te falta para tu meta.'}
        </p>
        {addButton}
      </Card>
    )
  }
  const [number, unit] = weightParts(summary.latest, pet)
  const a = summary.bmiAssessment
  // La categoría (y su color) solo aplica a personas adultas.
  const bmiColor = showBmiCategory(who) && a ? levelColor(a.level) : undefined
  const petNote = pet ? note(who, measureType('weight')!) : null
  return (
    <Card title="Peso" onClick={props.onOpen} action={<ChevronRight size={22} color="var(--muted)" aria-label="Ver detalle" />}>
      <div className="row">
        <div className="grow">
          <div className="mz-big"><b>{number}</b><span>{unit}</span></div>
          <div className="small muted">{Fmt.relativeDay(millisToEpochDay(summary.latestTimestamp))}</div>
        </div>
        {summary.spark.length >= 2 && <Sparkline values={summary.spark} width={120} height={48} />}
      </div>
      <div className="stats">
        <StatTile label="7 días" value={summary.change7 !== null ? weightDelta(summary.change7, pet) : '—'} />
        <StatTile label="30 días" value={summary.change30 !== null ? weightDelta(summary.change30, pet) : '—'} />
        {showBmi(who) && <StatTile label="IMC" value={summary.bmi !== null ? Fmt.num(summary.bmi, 1) : '—'} color={bmiColor} />}
      </div>
      {pet ? (
        petNote && <p className="small muted">{petNote}</p>
      ) : who.isMinor ? (
        <p className="small muted">{summary.bmi !== null ? MINOR_BMI_NOTE : 'Agrega su estatura en el detalle de peso para calcular el IMC.'}</p>
      ) : a ? (
        <div className="row"><Pill text={a.label} color={levelColor(a.level)} /><span className="small muted">según tu IMC (orientativo)</span></div>
      ) : (
        <p className="small muted">Agrega tu estatura en el detalle de peso para calcular tu IMC.</p>
      )}
      {summary.goal !== null && summary.toGoal !== null && (
        <div>
          <div className="row" style={{ marginBottom: 6 }}>
            <span className="grow" style={{ fontWeight: 600 }}>Meta: {weightText(summary.goal, pet)}</span>
            <b style={{ color: summary.goalReached ? 'var(--good)' : 'var(--primary)' }}>
              {summary.goalReached ? '¡Meta alcanzada!' : `Faltan ${weightText(Math.abs(summary.toGoal), pet)}`}
            </b>
          </div>
          <div className={`mz-progress${summary.goalReached ? ' done' : ''}`}>
            <div style={{ width: `${(summary.goalReached ? 1 : summary.goalProgress ?? 0) * 100}%` }} />
          </div>
        </div>
      )}
      {addButton}
    </Card>
  )
}

function MeasureRow(props: { type: MeasureType; latest: Measurement | undefined; who: MeasureWho; heightCm: number | null; onOpen: () => void; onAdd: () => void }) {
  const { type, latest, who } = props
  const Icon = measureIcon(type)
  const a = latest ? assess(who, type, latest, props.heightCm) : null
  return (
    <div className="mz-row" role="button" tabIndex={0} onClick={props.onOpen}>
      <span className="ic"><Icon size={22} /></span>
      <div className="grow">
        <div className="small muted">{type.label}</div>
        {!latest ? (
          <div className="mz-value muted" style={{ fontWeight: 600 }}>Sin registros</div>
        ) : (
          <>
            <div className="row" style={{ gap: 8 }}>
              <span className="mz-value">{valueWithUnit(type, latest, who.isPet)}</span>
              <span className="small muted ellipsis">{Fmt.daysFromNow(millisToEpochDay(latest.timestamp))}</span>
            </div>
            {a && <div style={{ marginTop: 3 }}><Pill text={a.label} color={levelColor(a.level)} /></div>}
          </>
        )}
      </div>
      <button type="button" className="mz-add" aria-label={`Agregar ${type.label.toLowerCase()}`} onClick={(e) => { e.stopPropagation(); props.onAdd() }}>
        <Plus size={22} />
      </button>
    </div>
  )
}
