// Medicamentos y citas. Las pantallas se registran en App.tsx; lo demás es API para otros módulos (p. ej. Inicio).
export { MedsPage, appointmentTitle } from './MedsPage'
export { MedEditPage } from './MedEditPage'
export { AppointmentsPage, AppointmentEditPage } from './AppointmentsPages'

// Lógica pura de horarios
export {
  TAKEN, SKIPPED, ALL_DAYS, dosesForDay, slotsOf, parseTimes, joinTimes, isAsNeeded, isScheduledOn, isFinished, dayInMask, weekdayIndex,
  adherence, recentAdherence, nextDose, daysSummary, scheduleSummary,
  type DoseItem, type Adherence,
} from './schedule'

// Acciones sobre tomas
export { setDose, takeDose, skipDose, undoDose, logDoseNow, deleteIntake, deleteMedication } from './actions'

// Componentes y utilidades reutilizables
export { DoseRow, AdherencePill, CalendarButton, CalendarNote, useAddToCalendar, useCalendarOwner, useNow } from './common'

// Calendario (.ics)
export {
  buildIcs, appointmentEvent, vaccinationEvent, medicationEvents, icsEscape, icsFold, icsDate, icsLocalDateTime, icsUtcStamp, vaccineNoun,
  type IcsEvent, type IcsAlarm,
} from './ics'

export { medsText, CALENDAR_NOTE, type MedsText } from './text'
