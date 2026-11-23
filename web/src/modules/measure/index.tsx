// Módulo de mediciones: peso con estadísticas y tendencias, estatura, presión, glucosa, etc.
// Adaptado a mascotas (sin IMC ni rangos humanos, peso en gramos) y a menores (sin categorías de adultos).

export { MeasurementsPage } from './MeasurementsPage'
export { MeasurementDetailPage } from './MeasurementDetailPage'

// API para la pantalla de inicio y otros módulos.
export { MeasureStats, weightSummary, bmi, bmiCategory, assess as assessAdult } from './stats'
export type { Assessment, Level, WeightSummary } from './stats'
export { MeasureRefs, weightText, weightDelta, weightParts, isMinor, whoOf } from './who'
export type { MeasureWho } from './who'
export { levelColor, measureIcon, valueWithUnit, recordHeight, syncProfileHeight } from './shared'
