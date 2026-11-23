import { Page } from '../../ui'

/** Pantalla "Exportar y respaldo" (ruta /exportar). */
export function ExportPage() {
  return <Page title="Exportar y respaldo"><p className="muted">Pendiente</p></Page>
}

/**
 * Botones listos para la sección de Configuración: "Exportar respaldo (.json)" e "Importar respaldo (.json)",
 * con sus diálogos y avisos. Ajustes solo tiene que renderizar <BackupButtons />.
 */
export function BackupButtons() {
  return <p className="muted">Pendiente</p>
}
