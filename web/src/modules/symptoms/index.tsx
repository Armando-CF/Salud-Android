import { Page } from '../../ui'

/** Lista e historial de síntomas (pestaña). */
export function SymptomsPage() {
  return <Page title="Síntomas"><p className="muted">Pendiente</p></Page>
}

/** Alta/edición. Rutas: /sintomas/nuevo (admite ?region=&label=&name=) y /sintomas/:id. */
export function SymptomEditPage() {
  return <Page title="Síntoma"><p className="muted">Pendiente</p></Page>
}

