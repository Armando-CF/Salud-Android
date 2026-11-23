// Módulo de Estudios y chequeos médicos (portado de app/src/main/java/app/savia/ui/studies).
export { StudiesPage } from './StudiesPage'
export { StudyDetailPage } from './StudyDetailPage'
export { StudyEditPage } from './StudyEditPage'
export { ParamTrendPage } from './ParamTrendPage'

// Lógica útil para otras pantallas (p. ej. Inicio).
export {
  upcomingFollowUps, paramSummaries, paramPath, resultFlag, outOfRange, valueText, flag, type ParamSummary, type RangeFlag,
} from './catalog'
export { StudyProfile, useStudyProfile } from './profile'
