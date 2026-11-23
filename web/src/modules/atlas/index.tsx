import { Page } from '../../ui'

/**
 * Atlas corporal como componente reutilizable (sin barra superior).
 * - Modo normal: `onLog(id, nombre)` se llama al pulsar "Registrar síntoma aquí".
 * - Modo elegir (`pickMode`): `onPick(id, nombre)` se llama al pulsar "Usar esta zona".
 */
export function BodyAtlas(_props: { pickMode?: boolean; onPick?: (regionId: string, label: string) => void; onLog?: (regionId: string, label: string) => void }) {
  return <p className="muted">Pendiente</p>
}

/** Pestaña "Cuerpo". */
export function AtlasPage() {
  return <Page title="Cuerpo"><BodyAtlas /></Page>
}
