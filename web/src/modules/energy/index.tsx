import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useLiveQuery } from 'dexie-react-hooks'
import { Flame } from 'lucide-react'
import { usePref } from '../../data/prefs'
import { useActive, useDb, useProfileRow } from '../../data/profiles'
import type { DailyLog, Measurement, Profile } from '../../data/types'
import { Fmt, millisToEpochDay, today } from '../../lib/dates'
import { Button, Card, EmptyState, InfoNote, NumberInput, Page, Select, useToast } from '../../ui'
import {
  ACTIVITIES, ACTIVITY_LEVELS, MIN_AGE, activityLevel, bmr, breakdown, dailyBalanceFromTrend, exerciseDaysPerWeek, exerciseKcal,
  kcalText, share, suggestLevel, tdee, weightSlopePerWeek, type ActivityLevel,
} from './energy'
import './energy.css'

export * from './energy'

/** Datos necesarios para calcular y, si está todo, el metabolismo basal. */
export interface EnergyInputs {
  weightKg: number | null
  heightCm: number | null
  age: number | null
  sex: string
  missing: string[]
  isMinor: boolean
  /** Metabolismo basal en kcal, o null si faltan datos o es menor de edad. */
  basal: number | null
}

export function energyInputs(profile: Profile | undefined, weights: readonly Measurement[]): EnergyInputs {
  const latest = weights.length ? weights.reduce((a, b) => (b.timestamp > a.timestamp ? b : a)) : null
  const weightKg = latest?.value ?? null
  const heightCm = profile?.heightCm ?? null
  const age = profile?.birthEpochDay != null ? Fmt.age(profile.birthEpochDay) : null
  const sex = profile?.sex ?? 'X'
  const missing = [weightKg === null ? 'tu peso' : '', heightCm === null ? 'tu estatura' : '', age === null ? 'tu fecha de nacimiento' : ''].filter(Boolean)
  const isMinor = age !== null && age < MIN_AGE
  const basal = missing.length === 0 && !isMinor ? bmr(weightKg!, heightCm!, age!, sex) : null
  return { weightKg, heightCm, age, sex, missing, isMinor, basal }
}

/** Nivel elegido, o el sugerido por el ejercicio registrado, o null. Devuelve también los días por semana estimados. */
export function effectiveLevel(chosen: string, logs: readonly DailyLog[], t: number): { level: ActivityLevel | null; perWeek: number | null; chosen: boolean } {
  const perWeek = exerciseDaysPerWeek(logs, t)
  const picked = activityLevel(chosen)
  return { level: picked ?? (perWeek !== null ? suggestLevel(perWeek) : null), perWeek, chosen: picked !== null }
}

/** Datos reactivos del gasto de energía del perfil activo (para la pantalla y para la tarjeta de Inicio). */
export function useEnergy() {
  const db = useDb()
  const t = today()
  const profile = useProfileRow()
  const weights = useLiveQuery(() => db.measurements.where('type').equals('weight').toArray(), [db]) ?? []
  const logs = useLiveQuery(() => db.dailyLogs.where('epochDay').above(t - 40).toArray(), [db, t]) ?? []
  const [chosen, setChosen] = usePref('activityLevel')
  const inputs = useMemo(() => energyInputs(profile, weights), [profile, weights])
  const eff = useMemo(() => effectiveLevel(chosen, logs, t), [chosen, logs, t])
  return { inputs, ...eff, weights, setChosen, t }
}

const joinY = (items: string[]): string => (items.length <= 1 ? items.join('') : `${items.slice(0, -1).join(', ')} y ${items[items.length - 1]}`)

/** Pantalla "Gasto de energía" (ruta /energia). */
export function EnergyPage() {
  const nav = useNavigate()
  const { isPet } = useActive()
  const { inputs, level, perWeek, chosen, weights, setChosen, t } = useEnergy()

  if (isPet) {
    return (
      <Page title="Gasto de energía">
        <EmptyState icon={Flame} title="Solo para personas" message="Este cálculo usa una fórmula para personas adultas; no aplica a mascotas." />
      </Page>
    )
  }
  const basal = inputs.basal
  const suggested = perWeek !== null ? suggestLevel(perWeek) : null
  const slope = weightSlopePerWeek(weights.filter((w) => millisToEpochDay(w.timestamp) >= t - 42).map((w) => [w.timestamp / 86_400_000, w.value] as const))
  return (
    <Page title="Gasto de energía" subtitle="Cuánto quemas al día, aproximadamente">
      {inputs.isMinor ? (
        <InfoNote>La fórmula que usa Savia (Mifflin-St Jeor) está pensada para personas adultas. En niñas, niños y adolescentes el gasto de energía se valora con su pediatra.</InfoNote>
      ) : basal === null ? (
        <Card title="Faltan datos para calcular">
          <p className="muted">Para estimar tu gasto de energía hace falta {joinY(inputs.missing)}.</p>
          <div className="row wrap">
            {inputs.weightKg === null && <Button onClick={() => nav('/mediciones/weight?add=1')}>Registrar peso</Button>}
            {(inputs.heightCm === null || inputs.age === null) && <Button variant="outline" onClick={() => nav('/ficha')}>Completar mi ficha</Button>}
          </div>
        </Card>
      ) : (
        <>
          <Card>
            <div className="small" style={{ color: 'var(--primary)', fontWeight: 700 }}>Tu gasto diario aproximado</div>
            <div className="en-total" data-testid="en-total">{kcalText(level ? tdee(basal, level) : basal)} kcal</div>
            <p className="muted">
              {level
                ? `Nivel de actividad: ${level.label.toLowerCase()}${chosen ? '' : ' (sugerido por tu ejercicio registrado)'}`
                : 'Solo por existir (metabolismo basal). Elige abajo tu nivel de actividad para ver el total.'}
            </p>
            <p className="small muted">
              Calculado con tu peso más reciente ({Fmt.num(inputs.weightKg!, 1)} kg), estatura ({Fmt.num(inputs.heightCm!, 1)} cm) y edad ({inputs.age} años).
            </p>
            {inputs.sex !== 'M' && inputs.sex !== 'F' && (
              <p className="small muted">Como tu ficha no indica sexo, se usa el punto medio entre las dos versiones de la fórmula (la diferencia es de ±83 kcal).</p>
            )}
          </Card>

          <Card title="¿Qué tan activo es tu día?">
            {ACTIVITY_LEVELS.map((l) => {
              const on = chosen && level?.key === l.key
              return (
                <div key={l.key} className={`en-level${on ? ' on' : ''}`}>
                  <div className="grow">
                    <div className="t">{l.label}</div>
                    <div className="small" style={{ opacity: 0.85 }}>{l.detail} · {kcalText(tdee(basal, l))} kcal</div>
                  </div>
                  {on ? <b className="small">Elegido</b> : <button className="btn text small" onClick={() => { void setChosen(l.key) }}>Elegir</button>}
                </div>
              )
            })}
            {suggested && perWeek !== null ? (
              <InfoNote>
                Según el ejercicio que has anotado en Bienestar (unos {Fmt.num(perWeek, 1)} días por semana en el último mes), tu nivel sería: {suggested.label.toLowerCase()}.
                {chosen ? '' : ' Savia lo usa mientras no elijas otro.'}
              </InfoNote>
            ) : (
              <p className="small muted">Si anotas tu ejercicio en Bienestar diario, Savia te sugiere el nivel automáticamente.</p>
            )}
          </Card>

          {level && <Parts basal={basal} level={level} />}

          {slope !== null && level && <WeightSays slope={slope} total={tdee(basal, level)} />}

          <ExerciseCalculator weightKg={inputs.weightKg!} day={t} />
        </>
      )}
      <InfoNote>
        Son estimaciones con fórmulas de población (Mifflin-St Jeor y METs); el valor real de cada persona puede variar. El metabolismo basal exacto
        se mide con una calorimetría indirecta. Los relojes inteligentes cuentan bien pasos y pulso, pero en calorías pueden equivocarse entre 20 y 40 %.
        Esta información es orientativa y no sustituye la valoración de un profesional de la salud.
      </InfoNote>
    </Page>
  )
}

function Parts(props: { basal: number; level: ActivityLevel }) {
  const b = breakdown(props.basal, props.level)
  const rows: [string, number, string][] = [
    ['Solo por existir (metabolismo basal)', b.basal, 'var(--primary)'],
    ['Moverte y hacer ejercicio', b.movement, 'var(--warn)'],
    ['Digerir la comida', b.digestion, 'var(--chart-3)'],
  ]
  return (
    <Card title="En qué se va tu energía">
      {rows.map(([label, kcal, color]) => (
        <div key={label} className="en-part">
          <div className="row"><span className="grow">{label}</span><b>{kcalText(kcal)} kcal · {share(kcal, b.total)} %</b></div>
          <div className="en-bar"><div style={{ width: `${share(kcal, b.total)}%`, background: color }} /></div>
        </div>
      ))}
      <p className="small muted">Lo que haces sin entrenar (caminar, estar de pie, moverte en el trabajo) suele pesar más que el gimnasio en el total del día.</p>
    </Card>
  )
}

function WeightSays(props: { slope: number; total: number }) {
  const { slope, total } = props
  const balance = dailyBalanceFromTrend(slope)
  const stable = Math.abs(slope) < 0.1
  return (
    <Card title="Lo que dice tu peso">
      <p>
        {stable
          ? 'Tu peso se ha mantenido estable en las últimas semanas: en promedio estás comiendo lo mismo que gastas.'
          : slope < 0
            ? `Tu peso baja unos ${Fmt.num(-slope, 2)} kg por semana. Eso equivale a comer alrededor de ${kcalText(-balance)} kcal menos de lo que gastas cada día.`
            : `Tu peso sube unos ${Fmt.num(slope, 2)} kg por semana. Eso equivale a comer alrededor de ${kcalText(balance)} kcal más de lo que gastas cada día.`}
      </p>
      {!stable && <p className="small muted">Con tu gasto estimado, tu consumo promedio rondaría las {kcalText(total + balance)} kcal al día.</p>}
      <p className="small muted">Basado en la tendencia de tus pesos de las últimas 6 semanas. Es la pista más confiable: si tu peso no cambia, lo que comes es lo que gastas.</p>
    </Card>
  )
}

function ExerciseCalculator(props: { weightKg: number; day: number }) {
  const db = useDb()
  const toast = useToast()
  const [index, setIndex] = useState(0)
  const [minutes, setMinutes] = useState('30')
  const [name, met] = ACTIVITIES[index]
  const n = Number(minutes)
  const mins = /^\d+$/.test(minutes) && n >= 1 && n <= 1440 ? n : null
  const add = async () => {
    if (mins === null) return
    const cur: DailyLog = (await db.dailyLogs.get(props.day)) ?? { epochDay: props.day, mood: null, energy: null, stress: null, sleepHours: null, waterGlasses: null, exerciseMin: null, notes: '' }
    await db.dailyLogs.put({ ...cur, exerciseMin: (cur.exerciseMin ?? 0) + mins })
    toast(`Sumados ${mins} min al ejercicio de hoy`)
  }
  return (
    <Card title="¿Cuánto quema un ejercicio?">
      <Select label="Actividad" value={index} onChange={setIndex} options={ACTIVITIES.map(([a, m], i) => [i, `${a} · ${Fmt.num(m, 1)} METs`] as const)} />
      <NumberInput label="Duración" value={minutes} onChange={(v) => setMinutes(v.slice(0, 4))} suffix="min" decimal={false} error={minutes !== '' && mins === null ? 'Escribe los minutos (1 a 1440)' : false} />
      {mins !== null && (
        <>
          <div className="en-result" data-testid="en-exercise">≈ {kcalText(exerciseKcal(met, props.weightKg, mins))} kcal</div>
          <p className="small muted">{name}: {Fmt.num(met, 1)} METs × {Fmt.num(props.weightKg, 1)} kg × {Fmt.duration(mins)}</p>
          <Button variant="tonal" onClick={() => { void add() }}>Sumar a mi ejercicio de hoy</Button>
        </>
      )}
    </Card>
  )
}

/** Tarjeta compacta para Inicio: solo aparece si ya se puede calcular el total. */
export function EnergyHomeCard() {
  const nav = useNavigate()
  const { inputs, level } = useEnergy()
  if (inputs.basal === null || !level) return null
  return (
    <Card onClick={() => nav('/energia')}>
      <div className="row">
        <span style={{ width: 46, height: 46, borderRadius: 14, display: 'flex', alignItems: 'center', justifyContent: 'center', flex: '0 0 auto', background: 'color-mix(in srgb, var(--bad) 15%, transparent)', color: 'var(--bad)' }}><Flame size={24} /></span>
        <div className="grow">
          <div className="small" style={{ color: 'var(--bad)', fontWeight: 700 }}>Gasto diario aproximado</div>
          <div style={{ fontSize: 21, fontWeight: 700 }}>{kcalText(tdee(inputs.basal, level))} kcal</div>
          <div className="small muted">Actividad: {level.label.toLowerCase()} · basal {kcalText(inputs.basal)} kcal</div>
        </div>
      </div>
    </Card>
  )
}
