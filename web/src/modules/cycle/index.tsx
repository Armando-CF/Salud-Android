import { Page } from '../../ui'

/** Calendario y estado del ciclo (pestaña). */
export function CyclePage() {
  return <Page title="Ciclo"><p className="muted">Pendiente</p></Page>
}

/** Ruta /ciclo/dia/:day */
export function CycleDayPage() {
  return <Page title="Día"><p className="muted">Pendiente</p></Page>
}

/** Ruta /ciclo/estadisticas */
export function CycleStatsPage() {
  return <Page title="Estadísticas"><p className="muted">Pendiente</p></Page>
}

/** Ruta /ciclo/ajustes */
export function CycleSettingsPage() {
  return <Page title="Ajustes del ciclo"><p className="muted">Pendiente</p></Page>
}

