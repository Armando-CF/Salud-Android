import { PawPrint } from 'lucide-react'
import { displayName, useActive } from '../../data/profiles'
import { EmptyState, Page } from '../../ui'

/**
 * Pantalla que se muestra cuando se llega con un perfil de mascota a una sección pensada solo para
 * personas (bienestar diario, autoexploración). Uso al inicio de la pantalla:
 *
 *     const { isPet } = useActive()
 *     if (isPet) return <PeopleOnlyPage title="Bienestar diario" />
 */
export function PeopleOnlyPage(props: { title: string }) {
  const { info } = useActive()
  const name = displayName(info)
  return (
    <Page title={props.title}>
      <EmptyState
        icon={PawPrint}
        title="Esta sección es para perfiles de personas"
        message={`"${props.title}" no aplica al perfil de ${name}. Para ${name} puedes usar síntomas, peso y mediciones, medicamentos, vacunas, estudios y citas.`}
      />
    </Page>
  )
}
