import type { ReactNode } from 'react'
import { Page } from '../../ui'

/** Ficha del perfil activo. */
export function ProfilePage() { return <Page title="Mi ficha"><p className="muted">Pendiente</p></Page> }

/** Lista de perfiles: cambiar, crear, eliminar. */
export function ProfilesPage() { return <Page title="Perfiles"><p className="muted">Pendiente</p></Page> }

/** Alta de perfil. Ruta /perfiles/nuevo/:kind ("person" | "pet"). */
export function NewProfilePage() { return <Page title="Nuevo perfil"><p className="muted">Pendiente</p></Page> }

/** Ajustes / Configuración. */
export function SettingsPage() { return <Page title="Ajustes"><p className="muted">Pendiente</p></Page> }

/** Bienvenida de primer uso (fuera del enrutador). Al terminar guarda la ficha, marca `onboardingDone` y llama a onDone. */
export function Onboarding(props: { onDone: () => void }) {
  return <div className="page"><main className="page-body"><button className="btn primary" onClick={props.onDone}>Empezar</button></main></div>
}

/** Si hay PIN configurado, lo pide antes de mostrar el contenido. */
export function LockGate(props: { children: ReactNode }) { return <>{props.children}</> }
