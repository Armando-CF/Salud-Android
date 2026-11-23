import { Page } from '../../ui'

/** Tomas del día y medicamentos. */
export function MedsPage() {
  return <Page title="Medicamentos"><p className="muted">Pendiente</p></Page>
}

/** Rutas /medicamentos/nuevo y /medicamentos/:id */
export function MedEditPage() {
  return <Page title="Medicamento"><p className="muted">Pendiente</p></Page>
}

/** Citas médicas. */
export function AppointmentsPage() {
  return <Page title="Citas"><p className="muted">Pendiente</p></Page>
}

/** Rutas /citas/nueva y /citas/:id */
export function AppointmentEditPage() {
  return <Page title="Cita"><p className="muted">Pendiente</p></Page>
}

