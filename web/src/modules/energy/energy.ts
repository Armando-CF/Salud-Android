// Estimación del gasto de energía (misma lógica que EnergyCalc.kt en Android).
// Todo es APROXIMADO (fórmulas de población): sirve de orientación, no sustituye una calorimetría
// ni la valoración de un profesional.

export interface ActivityLevel { key: string; label: string; detail: string; factor: number }

/** Niveles de actividad física y su factor para estimar el gasto total del día. */
export const ACTIVITY_LEVELS: ActivityLevel[] = [
  { key: 'sedentary', label: 'Sedentario', detail: 'Poco o nada de ejercicio', factor: 1.2 },
  { key: 'light', label: 'Ligero', detail: 'Ejercicio 1 a 3 días por semana', factor: 1.375 },
  { key: 'moderate', label: 'Moderado', detail: 'Ejercicio 3 a 5 días por semana', factor: 1.55 },
  { key: 'very', label: 'Muy activo', detail: 'Ejercicio intenso casi todos los días', factor: 1.725 },
]
export const activityLevel = (key: string): ActivityLevel | null => ACTIVITY_LEVELS.find((l) => l.key === key) ?? null

/** Edad mínima para la que está pensada la fórmula. */
export const MIN_AGE = 18
/** kcal aproximadas por kilo de peso corporal ganado o perdido. */
export const KCAL_PER_KG = 7700

/**
 * Metabolismo basal con la fórmula de Mifflin-St Jeor (kcal/día).
 * sex: "M" hombre (+5), "F" mujer (−161); otro valor → punto medio entre ambas (−78).
 */
export function bmr(weightKg: number, heightCm: number, ageYears: number, sex: string): number {
  return 10 * weightKg + 6.25 * heightCm - 5 * ageYears + (sex === 'M' ? 5 : sex === 'F' ? -161 : -78)
}

/** Gasto total aproximado del día = metabolismo basal × factor de actividad. */
export const tdee = (basal: number, level: ActivityLevel): number => basal * level.factor

export interface EnergyBreakdown { basal: number; digestion: number; movement: number; total: number }

/** Reparte el total en basal, digestión (10 % del total) y movimiento (el resto). */
export function breakdown(basal: number, level: ActivityLevel): EnergyBreakdown {
  const total = tdee(basal, level)
  const digestion = total * 0.1
  return { basal, digestion, movement: Math.max(0, total - basal - digestion), total }
}
export const share = (part: number, total: number): number => (total <= 0 ? 0 : Math.round((part / total) * 100))

/** Calorías de un ejercicio: MET × peso (kg) × horas. */
export const exerciseKcal = (met: number, weightKg: number, minutes: number): number => (met * weightKg * minutes) / 60

/** Nivel sugerido según los días por semana con ejercicio (menos de 1 → sedentario, hasta 3 → ligero, hasta 5 → moderado, más → muy activo). */
export function suggestLevel(daysPerWeek: number): ActivityLevel {
  return ACTIVITY_LEVELS[daysPerWeek < 1 ? 0 : daysPerWeek < 3 ? 1 : daysPerWeek <= 5 ? 2 : 3]
}

/**
 * Días por semana con ejercicio (10 min o más) en los últimos `windowDays` días, según el registro de bienestar.
 * Devuelve null si hay menos de `minLogs` días registrados.
 */
export function exerciseDaysPerWeek(logs: readonly { epochDay: number; exerciseMin: number | null }[], today: number, windowDays = 28, minLogs = 7): number | null {
  const recent = logs.filter((l) => l.epochDay >= today - windowDays + 1 && l.epochDay <= today)
  if (recent.length < minLogs) return null
  const active = recent.filter((l) => (l.exerciseMin ?? 0) >= 10).length
  return (active / recent.length) * 7
}

/** Pendiente de una serie (día, kg) en kg por semana; null con menos de 4 puntos o menos de 14 días. */
export function weightSlopePerWeek(points: readonly (readonly [day: number, kg: number])[], minPoints = 4, minSpanDays = 14): number | null {
  if (points.length < minPoints) return null
  const xs = points.map((p) => p[0])
  if (Math.max(...xs) - Math.min(...xs) < minSpanDays) return null
  const mx = xs.reduce((a, b) => a + b, 0) / xs.length
  const my = points.reduce((a, p) => a + p[1], 0) / points.length
  let num = 0, den = 0
  for (const [x, y] of points) { num += (x - mx) * (y - my); den += (x - mx) ** 2 }
  return den === 0 ? null : (num / den) * 7
}

/** Balance diario aproximado (kcal) que explica una tendencia de peso: negativo = comes menos de lo que gastas. */
export const dailyBalanceFromTrend = (slopeKgPerWeek: number): number => (slopeKgPerWeek * KCAL_PER_KG) / 7

/** Redondea a decenas: son estimaciones. */
export const round10 = (kcal: number): number => Math.round(kcal / 10) * 10
/** "2,760" */
export const kcalText = (kcal: number): string => round10(kcal).toLocaleString('en-US')

/** Actividades comunes con sus METs aproximados (1 MET = estar en reposo). */
export const ACTIVITIES: readonly (readonly [name: string, met: number])[] = [
  ['Caminar a paso normal', 3.5], ['Caminar rápido', 4.3], ['Trotar suave', 7], ['Correr a 8 km/h', 8], ['Correr a 10 km/h', 10],
  ['Bicicleta, paseo', 4], ['Bicicleta, ritmo moderado', 6.8], ['Bicicleta fija intensa', 8.8], ['Pesas, esfuerzo ligero', 3.5],
  ['Pesas, esfuerzo intenso', 6], ['Natación suave', 6], ['Yoga o estiramientos', 2.5], ['Baile', 5], ['Fútbol', 7], ['Básquetbol', 6.5],
  ['Subir escaleras', 8], ['Quehacer de la casa', 3.3], ['Jardinería', 3.8], ['Jugar con niñas y niños', 4],
]
