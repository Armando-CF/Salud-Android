// Vacunas y desparasitación. Las pantallas se registran en App.tsx; lo demás es API para otros módulos (p. ej. Inicio).
export { VaccinesPage, newVaccineRoute } from './VaccinesPage'
export { VaccineEditPage } from './VaccineEditPage'

export {
  VaccineLogic, upcoming, dueSoon, dueStatus, dueText, isFulfilled, sameVaccine, nextDose, plusMonths, plusInterval, SOON_DAYS, DUE_STATUS_LABEL,
  type DueItem, type DueStatus,
} from './logic'

export {
  VaccineCatalog, suggestions, filterSuggestions, findSuggestion, normalizeName, intervalLabel, inLabel, DOSES, VACUNA, DESP_INTERNA, DESP_EXTERNA, OTRO,
  type VaccineSuggestion,
} from './catalog'

export { DueRow, VaccineTypeBadge, vaccineTypeIcon, vaccineTypeColor, dueStatusColor, vaccinesTitle, vaccinesDisclaimer } from './common'
