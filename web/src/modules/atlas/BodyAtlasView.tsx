import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useLiveQuery } from 'dexie-react-hooks'
import { Check, PawPrint, Plus, Pointer, X } from 'lucide-react'
import { usePref } from '../../data/prefs'
import { useActive } from '../../data/profiles'
import type { SymptomEntry } from '../../data/types'
import { Fmt, millisToEpochDay } from '../../lib/dates'
import { Button, EmptyState, InfoNote } from '../../ui'
import { AtlasCanvas } from './AtlasCanvas'
import { loadAtlas, recentCounts, type Atlas, type AtlasInfo, type GroupOpt } from './atlasData'
import './atlas.css'

export interface BodyAtlasViewProps {
  /** Modo "elegir zona" (para el editor de síntomas). */
  pickMode?: boolean
  onPick?: (regionId: string, label: string) => void
  onLog?: (regionId: string, label: string) => void
  /** Grupo inicial ("front", "hand", "pet_side"…). Si no existe para el perfil se usa el primero disponible. */
  initialGroup?: string
  /** Zona seleccionada al abrir. */
  initialSelection?: string | null
}

/** Ruta del editor de síntomas con la zona prellenada. */
export const symptomRoute = (regionId: string, label: string): string =>
  `/sintomas/nuevo?region=${encodeURIComponent(regionId)}&label=${encodeURIComponent(label)}`

/**
 * Atlas corporal interactivo (sin barra superior): llena el alto disponible de su contenedor.
 * Personas: figura humana (frente, espalda, abdomen, órganos, esqueleto, mano y pie).
 * Mascotas: solo las figuras de su especie (perro o gato).
 */
export function BodyAtlasView(props: BodyAtlasViewProps) {
  const pickMode = props.pickMode ?? false
  const nav = useNavigate()
  const { info: profile, isPet, db } = useActive()
  const [sex, setSex] = usePref('atlasSex')
  const species = isPet ? profile.species : null
  const [atlas, setAtlas] = useState<Atlas | null>(null)
  const [failed, setFailed] = useState(false)
  const [attempt, setAttempt] = useState(0)
  const [requestedGroup, setRequestedGroup] = useState(props.initialGroup ?? 'front')
  const [selectedId, setSelectedId] = useState<string | null>(props.initialSelection ?? null)
  const symptoms = useLiveQuery(() => db.symptoms.toArray(), [db]) ?? NO_SYMPTOMS
  const counts = useMemo(() => recentCounts(symptoms), [symptoms])

  useEffect(() => {
    let alive = true
    setFailed(false)
    loadAtlas().then((a) => { if (alive) setAtlas(a) }).catch(() => { if (alive) setFailed(true) })
    return () => { alive = false }
  }, [attempt])

  if (failed) {
    return (
      <div className="atlas">
        <div className="atlas-center">
          <p className="muted">No se pudo cargar la figura.</p>
          <Button variant="outline" onClick={() => setAttempt((n) => n + 1)}>Reintentar</Button>
        </div>
      </div>
    )
  }
  if (!atlas) return <div className="atlas"><div className="atlas-center muted">Cargando…</div></div>

  const groups = atlas.groupsFor(isPet, profile.species)
  const group = groups.some((g) => g[0] === requestedGroup) ? requestedGroup : groups[0]?.[0] ?? null
  const view = group ? atlas.view(group, sex, species) : null
  if (!group || !view) {
    return (
      <div className="atlas">
        <div className="atlas-center">
          <EmptyState
            icon={PawPrint}
            title="Aún no tenemos una figura para esta especie"
            message="Por ahora hay figuras de perro y de gato. Puedes registrar síntomas sin indicar la zona."
            actionLabel={pickMode ? undefined : 'Registrar un síntoma'}
            onAction={pickMode ? undefined : () => nav('/sintomas/nuevo')}
          />
        </div>
      </div>
    )
  }
  const selected = view.shapes.find((s) => s.id !== null && s.id === selectedId) ?? null
  const info = selected?.id ? atlas.info[selected.id] ?? null : null
  const openGroup = (g: string) => { setRequestedGroup(g); setSelectedId(null) }
  const onLog = props.onLog ?? ((id: string, label: string) => nav(symptomRoute(id, label)))

  return (
    <div className="atlas" data-view={view.id} data-selected={selected?.id ?? ''}>
      {groups.length > 1 && (
        <div className="atlas-groups" role="group" aria-label="Vistas">
          {groups.map(([g, label]) => (
            <button key={g} type="button" className={`chip${g === group ? ' on' : ''}`} aria-pressed={g === group} onClick={() => openGroup(g)}>{label}</button>
          ))}
        </div>
      )}
      <div className="atlas-stage">
        <AtlasCanvas view={view} selectedId={selected?.id ?? null} counts={counts} onSelect={setSelectedId} />
        {atlas.hasSexVariants(group, species) && (
          <div className="atlas-sex">
            <div className="segmented" role="group" aria-label="Modelo">
              {SEXES.map(([k, label]) => (
                <button key={k} type="button" className={sex === k ? 'on' : ''} aria-pressed={sex === k} onClick={() => { void setSex(k); setSelectedId(null) }}>{label}</button>
              ))}
            </div>
          </div>
        )}
      </div>
      <InfoPanel
        atlas={atlas}
        group={group}
        groups={groups}
        selectedId={selected?.id ?? null}
        info={info}
        symptoms={symptoms}
        pickMode={pickMode}
        isPet={isPet}
        onClear={() => setSelectedId(null)}
        onOpenGroup={openGroup}
        onLog={onLog}
        onPick={(id, name) => props.onPick?.(id, name)}
      />
    </div>
  )
}

const NO_SYMPTOMS: SymptomEntry[] = []
const SEXES = [['F', 'Mujer'], ['M', 'Hombre']] as const

function InfoPanel(props: {
  atlas: Atlas
  group: string
  groups: readonly GroupOpt[]
  selectedId: string | null
  info: AtlasInfo | null
  symptoms: SymptomEntry[]
  pickMode: boolean
  isPet: boolean
  onClear: () => void
  onOpenGroup: (g: string) => void
  onLog: (id: string, name: string) => void
  onPick: (id: string, name: string) => void
}) {
  const { atlas, group, groups, info, pickMode, isPet } = props
  const id = props.selectedId
  const here = useMemo(() => (id ? props.symptoms.filter((s) => s.regionId === id) : []), [props.symptoms, id])
  if (!info || !id) {
    return (
      <div className="atlas-panel">
        <div className="atlas-hint">
          <Pointer className="ic" size={26} />
          <div>
            <div className="t">
              {!pickMode ? 'Toca cualquier parte para ver su nombre y qué hay ahí' : isPet ? 'Toca la zona donde notas la molestia' : 'Toca la zona donde sientes la molestia'}
            </div>
            <div className="n">{atlas.orientationNote(group)} Pellizca para acercar.</div>
          </div>
        </div>
      </div>
    )
  }
  const last = here.length ? here.reduce((a, b) => (b.timestamp > a.timestamp ? b : a)) : null
  const link = info.link
  return (
    <div className="atlas-panel" role="region" aria-label={info.name}>
      <div className="atlas-panel-scroll">
        <div className="atlas-panel-head">
          <h2>{info.name}</h2>
          <button type="button" className="icon-btn" onClick={props.onClear} aria-label="Cerrar"><X size={24} /></button>
        </div>
        <div className="atlas-panel-body">
          {info.description && <p>{info.description}</p>}
          <ListLine label="Órganos y estructuras" items={info.organs} />
          <ListLine label="Huesos" items={info.bones} />
          <ListLine label="Músculos" items={info.muscles} />
          {last && (
            <div className="atlas-here">
              Has registrado {here.length} {here.length === 1 ? 'síntoma' : 'síntomas'} aquí · último: {last.name}, {Fmt.relativeDay(millisToEpochDay(last.timestamp)).toLowerCase()}
            </div>
          )}
          {info.tip && <InfoNote>{(isPet ? 'Cuándo ir al veterinario: ' : 'Cuándo consultar: ') + info.tip}</InfoNote>}
          <div className="atlas-actions">
            {pickMode
              ? <Button icon={Check} onClick={() => props.onPick(id, info.name)}>Usar esta zona</Button>
              : <Button icon={Plus} onClick={() => props.onLog(id, info.name)}>Registrar síntoma aquí</Button>}
            {link && link !== group && groups.some((g) => g[0] === link) && (
              <Button variant="outline" onClick={() => props.onOpenGroup(link)}>Ver {(atlas.groupLabel(link) ?? 'detalle').toLowerCase()}</Button>
            )}
          </div>
          <div className="atlas-foot">
            {isPet ? 'Información educativa; no sustituye una revisión veterinaria.' : 'Información educativa; no sustituye una valoración médica.'}
          </div>
        </div>
      </div>
    </div>
  )
}

function ListLine(props: { label: string; items: string[] }) {
  if (!props.items.length) return null
  return (
    <div className="atlas-line">
      <div className="l">{props.label}</div>
      <div className="v">{props.items.join(' · ')}</div>
    </div>
  )
}
