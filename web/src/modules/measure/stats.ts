// Cálculos puros del módulo de mediciones (portados de MeasureStats.kt de la app de Android).
// Todas las clasificaciones son orientativas; no son un diagnóstico.
import type { MeasureType } from '../../data/catalogs'
import type { Measurement } from '../../data/types'
import { Fmt, epochDayToMillis, millisToEpochDay } from '../../lib/dates'

/** Nivel de una clasificación orientativa. */
export type Level = 'good' | 'warn' | 'bad' | 'neutral'

/** Clasificación orientativa de un valor (NO es un diagnóstico). */
export interface Assessment { label: string; level: Level; detail: string }
const A = (label: string, level: Level, detail = ''): Assessment => ({ label, level, detail })

/** Punto en el tiempo: x = día (epochDay con fracción), y = valor. */
export interface TPoint { x: number; y: number }

/** Recta de tendencia y = intercept + slopePerDay · x. */
export interface Regression {
  slopePerDay: number
  intercept: number
  /** Cambio por semana (unidades/semana). */
  perWeek: number
  at: (x: number) => number
}

export interface RangeSummary { count: number; min: number; max: number; avg: number; first: number; last: number; change: number }

/** Fecha estimada para llegar a la meta siguiendo la tendencia actual. */
export interface GoalProjection { epochDay: number; daysLeft: number }

/** Resumen de peso listo para tarjetas (inicio, hub de mediciones). */
export interface WeightSummary {
  latest: number
  latestTimestamp: number
  change7: number | null
  change30: number | null
  bmi: number | null
  bmiAssessment: Assessment | null
  goal: number | null
  /** meta − peso actual (negativo = falta bajar). */
  toGoal: number | null
  goalReached: boolean
  /** Avance hacia la meta entre 0 y 1 (desde el primer peso registrado). */
  goalProgress: number | null
  /** Últimos valores en orden cronológico, para una mini gráfica. */
  spark: number[]
}

const has = (text: string, part: string): boolean => text.toLowerCase().includes(part)
const isFasting = (tag: string): boolean => has(tag, 'ayunas') || has(tag, 'antes de comer')
const isAfterMeal = (tag: string): boolean => has(tag, 'después de comer')
const byX = (a: TPoint, b: TPoint): number => a.x - b.x

/** Día (con fracción) en hora local de una medición. */
export function dayX(timestamp: number): number {
  const day = millisToEpochDay(timestamp)
  return day + (timestamp - epochDayToMillis(day, 0)) / 86_400_000
}

/** Mediciones → puntos en orden cronológico (valor principal, o el segundo valor si `second`). */
export function points(items: readonly Measurement[], second = false): TPoint[] {
  const out: TPoint[] = []
  for (const m of [...items].sort((a, b) => a.timestamp - b.timestamp)) {
    const y = second ? m.value2 : m.value
    if (y !== null && y !== undefined) out.push({ x: dayX(m.timestamp), y })
  }
  return out
}

/** Regresión lineal por mínimos cuadrados. null si hay menos de 2 puntos o todos en el mismo instante. */
export function regression(pts: readonly TPoint[]): Regression | null {
  if (pts.length < 2) return null
  const mx = pts.reduce((s, p) => s + p.x, 0) / pts.length
  const my = pts.reduce((s, p) => s + p.y, 0) / pts.length
  let sxx = 0
  let sxy = 0
  for (const p of pts) {
    sxx += (p.x - mx) * (p.x - mx)
    sxy += (p.x - mx) * (p.y - my)
  }
  if (sxx < 1e-9) return null
  const slopePerDay = sxy / sxx
  const intercept = my - slopePerDay * mx
  return { slopePerDay, intercept, perWeek: slopePerDay * 7, at: (x) => intercept + slopePerDay * x }
}

/** Media móvil: para cada punto, promedio de los puntos de los `windowDays` días anteriores (incluido él). */
export function movingAverage(pts: readonly TPoint[], windowDays = 7): TPoint[] {
  const sorted = [...pts].sort(byX)
  const out: TPoint[] = []
  let start = 0
  let sum = 0
  for (let i = 0; i < sorted.length; i++) {
    sum += sorted[i].y
    while (sorted[start].x <= sorted[i].x - windowDays) {
      sum -= sorted[start].y
      start++
    }
    out.push({ x: sorted[i].x, y: sum / (i - start + 1) })
  }
  return out
}

/**
 * Cambio del último valor respecto a la medición más cercana a "hace `days` días"
 * (contados desde la última medición). null si no hay una referencia razonablemente cercana.
 */
export function changeOver(pts: readonly TPoint[], days: number): number | null {
  const sorted = [...pts].sort(byX)
  if (sorted.length < 2) return null
  const last = sorted[sorted.length - 1]
  const target = last.x - days
  let ref = sorted[0]
  for (const p of sorted.slice(0, -1)) if (Math.abs(p.x - target) < Math.abs(ref.x - target)) ref = p
  if (Math.abs(ref.x - target) > Math.max(3, days * 0.5)) return null
  return last.y - ref.y
}

/** Mín / máx / promedio / primero / último de los valores (en orden cronológico). */
export function summary(values: readonly number[]): RangeSummary | null {
  if (!values.length) return null
  const first = values[0]
  const last = values[values.length - 1]
  return {
    count: values.length,
    min: Math.min(...values),
    max: Math.max(...values),
    avg: values.reduce((s, v) => s + v, 0) / values.length,
    first,
    last,
    change: last - first,
  }
}

// ---------- IMC ----------

export function bmi(weightKg: number, heightCm: number | null | undefined): number | null {
  if (heightCm === null || heightCm === undefined || heightCm < 50 || weightKg <= 0) return null
  const m = heightCm / 100
  return weightKg / (m * m)
}

/** Categorías de la OMS para personas adultas. */
export function bmiCategory(v: number): Assessment {
  if (v < 18.5) return A('Bajo peso', 'warn', 'IMC menor a 18.5.')
  if (v < 25) return A('Peso saludable', 'good', 'IMC entre 18.5 y 24.9.')
  if (v < 30) return A('Sobrepeso', 'warn', 'IMC entre 25 y 29.9.')
  if (v < 35) return A('Obesidad grado I', 'bad', 'IMC entre 30 y 34.9.')
  if (v < 40) return A('Obesidad grado II', 'bad', 'IMC entre 35 y 39.9.')
  return A('Obesidad grado III', 'bad', 'IMC de 40 o más.')
}

/** Rango de peso con IMC 18.5–24.9 para una estatura: [mínimo, máximo]. */
export function healthyWeightRange(heightCm: number): [number, number] {
  const m2 = (heightCm / 100) * (heightCm / 100)
  return [18.5 * m2, 24.9 * m2]
}

// ---------- Clasificaciones orientativas ----------

export function bloodPressure(sys: number, dia: number): Assessment {
  if (sys > 180 || dia > 120) {
    return A(
      'Revisar con urgencia',
      'bad',
      'Más de 180 de sistólica o de 120 de diastólica. Repite la medición tras unos minutos de reposo; si sigue así o tienes molestias, busca atención médica de inmediato.',
    )
  }
  if (sys >= 140 || dia >= 90) return A('Alta (grado 2)', 'bad', '140 o más de sistólica, o 90 o más de diastólica. Coméntalo con tu médico.')
  if (sys >= 130 || dia >= 80) return A('Alta (grado 1)', 'warn', 'Sistólica de 130 a 139 o diastólica de 80 a 89.')
  if (sys >= 120) return A('Elevada', 'warn', 'Sistólica de 120 a 129 con diastólica menor a 80.')
  if (sys < 90 || dia < 60) return A('Baja', 'warn', 'Menos de 90 de sistólica o de 60 de diastólica. Si sientes mareo o debilidad, consúltalo.')
  return A('Normal', 'good', 'Menos de 120 de sistólica y menos de 80 de diastólica.')
}

export function glucose(value: number, tag: string): Assessment {
  if (value < 54) return A('Muy baja', 'bad', 'Menos de 54 mg/dL. Toma algo con azúcar y busca atención si no mejora.')
  if (value < 70) return A('Baja', 'warn', 'Menos de 70 mg/dL.')
  if (isFasting(tag)) {
    if (value < 100) return A('Normal', 'good', 'En ayunas: de 70 a 99 mg/dL.')
    if (value < 126) return A('Elevada', 'warn', 'En ayunas: de 100 a 125 mg/dL.')
    return A('Alta', 'bad', 'En ayunas: 126 mg/dL o más. Coméntalo con tu médico.')
  }
  if (isAfterMeal(tag)) {
    if (value < 140) return A('Normal', 'good', '2 h después de comer: menos de 140 mg/dL.')
    if (value < 200) return A('Elevada', 'warn', '2 h después de comer: de 140 a 199 mg/dL.')
    return A('Alta', 'bad', '2 h después de comer: 200 mg/dL o más. Coméntalo con tu médico.')
  }
  if (value < 140) {
    return A('En rango habitual', 'good', 'Entre 70 y 139 mg/dL. Indica el contexto (en ayunas, después de comer) para una referencia más precisa.')
  }
  if (value < 200) return A('Elevada', 'warn', 'De 140 a 199 mg/dL sin contexto indicado.')
  return A('Alta', 'bad', '200 mg/dL o más. Coméntalo con tu médico.')
}

export function spo2(value: number): Assessment {
  if (value >= 95) return A('Normal', 'good', '95 % o más.')
  if (value > 90) return A('Algo baja', 'warn', 'Entre 91 y 94 %. Repite la medición en reposo y con la mano tibia.')
  return A('Baja', 'bad', '90 % o menos. Si se repite o te falta el aire, busca atención médica.')
}

export function temperature(value: number): Assessment {
  if (value < 35) return A('Baja', 'warn', 'Menos de 35 °C.')
  if (value < 37.5) return A('Normal', 'good', 'Entre 35 y 37.4 °C.')
  if (value < 38) return A('Febrícula', 'warn', 'Entre 37.5 y 37.9 °C.')
  if (value < 39.5) return A('Fiebre', 'bad', '38 °C o más.')
  return A('Fiebre alta', 'bad', '39.5 °C o más. Busca atención médica.')
}

export function heartRate(value: number, tag: string): Assessment {
  if (has(tag, 'ejercicio')) return A('Tras ejercicio', 'neutral', 'Después del ejercicio es normal que el pulso esté más alto.')
  if (value < 50) return A('Baja', 'warn', 'Menos de 50 lpm en reposo. Puede ser habitual en personas muy entrenadas.')
  if (value < 60) return A('Algo baja', 'good', 'Entre 50 y 59 lpm en reposo; frecuente en personas activas.')
  if (value <= 100) return A('Normal', 'good', 'Entre 60 y 100 lpm en reposo.')
  return A('Elevada', 'warn', 'Más de 100 lpm en reposo.')
}

/** Relación cintura/estatura (se recomienda menor a 0.5). */
export function waistToHeight(waistCm: number, heightCm: number): Assessment {
  const r = waistCm / heightCm
  const detail = `Relación cintura/estatura: ${Fmt.num(r, 2)} (se recomienda menor a 0.5).`
  if (r < 0.5) return A('Recomendable', 'good', detail)
  if (r < 0.6) return A('Elevada', 'warn', detail)
  return A('Muy elevada', 'bad', detail)
}

/** Clasificación orientativa de una medición (rangos de personas adultas), o null si ese tipo no tiene referencia. */
export function assess(type: MeasureType, m: Measurement, heightCm: number | null = null): Assessment | null {
  switch (type.key) {
    case 'weight': {
      const b = bmi(m.value, heightCm)
      return b === null ? null : bmiCategory(b)
    }
    case 'bp':
      return m.value2 === null || m.value2 === undefined ? null : bloodPressure(m.value, m.value2)
    case 'hr':
      return heartRate(m.value, m.tag)
    case 'glucose':
      return glucose(m.value, m.tag)
    case 'temp':
      return temperature(m.value)
    case 'spo2':
      return spo2(m.value)
    case 'waist':
      return heightCm !== null && heightCm >= 50 ? waistToHeight(m.value, heightCm) : null
    default:
      return null
  }
}

// ---------- Meta de peso ----------

/**
 * Fecha estimada para llegar a `goal` con la tendencia reciente (últimos `windowDays` días).
 * null si la tendencia no se acerca a la meta, es casi plana o tardaría más de 3 años.
 */
export function goalProjection(pts: readonly TPoint[], goal: number, today: number, windowDays = 60): GoalProjection | null {
  const sorted = [...pts].sort(byX)
  if (!sorted.length) return null
  const last = sorted[sorted.length - 1]
  const diff = goal - last.y
  if (Math.abs(diff) < 0.05) return { epochDay: today, daysLeft: 0 }
  const inWindow = sorted.filter((p) => p.x >= last.x - windowDays)
  const recent = inWindow.length >= 3 ? inWindow : sorted.slice(-5)
  const reg = regression(recent)
  if (!reg) return null
  if (Math.abs(reg.perWeek) < 0.02 || Math.sign(diff) !== Math.sign(reg.slopePerDay)) return null
  const days = Math.ceil(diff / reg.slopePerDay - 1e-9)
  if (days > 365 * 3) return null
  const day = Math.trunc(last.x + days)
  const left = day - today
  if (left > 365 * 3) return null
  return { epochDay: Math.max(day, today), daysLeft: Math.max(left, 0) }
}

/** ¿Se alcanzó la meta? Se decide según la dirección desde el primer peso registrado. */
export function goalReached(first: number, current: number, goal: number): boolean {
  return first >= goal ? current <= goal + 0.05 : current >= goal - 0.05
}

/** Avance 0..1 desde el primer peso hacia la meta. */
export function goalProgress(first: number, current: number, goal: number): number {
  const total = goal - first
  if (Math.abs(total) < 0.05) return Math.abs(current - goal) < 0.05 ? 1 : 0
  return Math.min(1, Math.max(0, (current - first) / total))
}

/**
 * Resumen de peso a partir de las mediciones de tipo "weight" (en cualquier orden). null si no hay.
 * Para mascotas pasa `heightCm = null` (no llevan IMC).
 */
export function weightSummary(
  weights: readonly Measurement[],
  heightCm: number | null,
  goal: number | null,
  sparkSize = 20,
): WeightSummary | null {
  const sorted = [...weights].sort((a, b) => a.timestamp - b.timestamp)
  if (!sorted.length) return null
  const last = sorted[sorted.length - 1]
  const pts = points(sorted)
  const b = bmi(last.value, heightCm)
  const first = sorted[0].value
  return {
    latest: last.value,
    latestTimestamp: last.timestamp,
    change7: changeOver(pts, 7),
    change30: changeOver(pts, 30),
    bmi: b,
    bmiAssessment: b === null ? null : bmiCategory(b),
    goal,
    toGoal: goal === null ? null : goal - last.value,
    goalReached: goal !== null && goalReached(first, last.value, goal),
    goalProgress: goal === null ? null : goalProgress(first, last.value, goal),
    spark: sorted.slice(-sparkSize).map((m) => m.value),
  }
}

// ---------- Validación de captura ----------

/** Mínimo de captura: el peso de una persona no baja de 0.5 kg; el de una mascota puede ser de gramos. */
export const minValue = (type: MeasureType, isPet: boolean): number => (type.key === 'weight' && !isPet ? 0.5 : type.min)

/** Devuelve un mensaje de error para mostrar, o null si la captura es válida. */
export function validate(
  type: MeasureType,
  value: number | null,
  value2: number | null,
  timestamp: number,
  now: number,
  isPet = false,
): string | null {
  if (value === null) return `Escribe un valor para ${type.valueLabel.toLowerCase()}.`
  const min = minValue(type, isPet)
  if (value < min || value > type.max) {
    return `${type.valueLabel}: el valor debe estar entre ${Fmt.num(min, 2)} y ${Fmt.num(type.max, 1)} ${type.unit}.`
  }
  if (type.secondLabel) {
    if (value2 === null) return `Escribe también la ${type.secondLabel.toLowerCase()}.`
    if (value2 < 20 || value2 > 200) return `${type.secondLabel}: el valor debe estar entre 20 y 200 ${type.unit}.`
    if (value2 >= value) return 'La diastólica debe ser menor que la sistólica.'
  }
  if (timestamp > now + 5 * 60_000) return 'La fecha y hora no pueden ser futuras.'
  return null
}

/** Todo junto, con el mismo nombre que en la app de Android. */
export const MeasureStats = {
  dayX, points, regression, movingAverage, changeOver, summary, bmi, bmiCategory, healthyWeightRange, bloodPressure,
  glucose, spo2, temperature, heartRate, waistToHeight, assess, goalProjection, goalReached, goalProgress,
  weightSummary, validate,
}
