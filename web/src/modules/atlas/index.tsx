import { useNavigate, useSearchParams } from 'react-router-dom'
import { useActive } from '../../data/profiles'
import { Page } from '../../ui'
import { BodyAtlasView, symptomRoute } from './BodyAtlasView'

/**
 * Atlas corporal como componente reutilizable (sin barra superior): llena el alto disponible de su contenedor.
 * - Modo normal: `onLog(id, nombre)` se llama al pulsar "Registrar síntoma aquí".
 * - Modo elegir (`pickMode`): `onPick(id, nombre)` se llama al pulsar "Usar esta zona".
 */
export function BodyAtlas(props: { pickMode?: boolean; onPick?: (regionId: string, label: string) => void; onLog?: (regionId: string, label: string) => void }) {
  return <BodyAtlasView pickMode={props.pickMode} onPick={props.onPick} onLog={props.onLog} />
}

/**
 * Pestaña "Cuerpo". Admite `?g=<grupo>&sel=<id>` para abrir directamente una vista con una zona seleccionada
 * (p. ej. `/cuerpo?g=hand&sel=m_falange_media_indice`) y `?pick=1` para elegir una zona y abrir con ella el
 * editor de síntomas.
 */
export function AtlasPage() {
  const nav = useNavigate()
  const [params] = useSearchParams()
  const { isPet } = useActive()
  const pick = params.get('pick') === '1'
  const toEditor = (id: string, label: string) => nav(symptomRoute(id, label))
  return (
    <Page
      title={pick ? 'Elige la zona' : 'Cuerpo'}
      subtitle={!pick ? 'Toca una zona para saber cómo se llama' : isPet ? 'Toca la zona donde notas la molestia' : 'Toca dónde sientes la molestia'}
      flush
    >
      <BodyAtlasView
        key={`${pick}|${params.get('g') ?? ''}|${params.get('sel') ?? ''}`}
        pickMode={pick}
        initialGroup={params.get('g') ?? (isPet ? 'pet_side' : 'front')}
        initialSelection={params.get('sel')}
        onLog={toEditor}
        onPick={toEditor}
      />
    </Page>
  )
}
