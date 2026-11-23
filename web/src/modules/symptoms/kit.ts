import { PetSpecies, SymptomCatalog } from '../../data/catalogs'
import { displayName, useActive } from '../../data/profiles'
import { PetSymptomCatalog } from './petCatalog'

/**
 * Catálogo y textos del módulo de síntomas según de quién sea el perfil: una persona anota lo que
 * siente; con una mascota se anota lo que se le observa.
 */
export interface SymptomKit {
  isPet: boolean
  species: string
  name: string
  /** Síntomas sugeridos, agrupados. */
  groups: readonly (readonly [string, readonly string[]])[]
  all: string[]
  /** Desencadenantes sugeridos. */
  triggers: string[]
  intensityLabel: (i: number) => string
  /** ¿Se puede señalar la zona en el atlas? Personas siempre; mascotas solo si hay figura de su especie. */
  canPickBodyRegion: boolean
  whatTitle: string
  nameLabel: string
  namePlaceholder: string
  nameError: string
  intensityHigh: string
  whereTitle: string
  whereHint: string
  notesPlaceholder: string
  customTriggerExample: string
  /** Aviso cuando la intensidad es muy alta (9–10). */
  alarmNote: string
  /** Texto de la lista vacía. */
  emptyMessage: string
}

export function symptomKit(isPet: boolean, species = '', name = ''): SymptomKit {
  const groups = isPet ? PetSymptomCatalog.groups : SymptomCatalog.groups
  return {
    isPet, species, name, groups,
    all: groups.flatMap((g) => [...g[1]]),
    triggers: isPet ? PetSymptomCatalog.triggers : SymptomCatalog.triggers,
    intensityLabel: isPet ? PetSymptomCatalog.intensityLabel : SymptomCatalog.intensityLabel,
    canPickBodyRegion: !isPet || PetSpecies.withAtlas.includes(species),
    whatTitle: isPet ? '¿Qué le notas?' : '¿Qué sientes?',
    nameLabel: isPet ? 'Síntoma o cambio que observas' : 'Síntoma o molestia',
    namePlaceholder: isPet ? 'Ej. Vómito' : 'Ej. Dolor de cabeza',
    nameError: isPet ? 'Escribe o elige qué le notas' : 'Escribe o elige qué sientes',
    intensityHigh: isPet ? '10 · lo ves muy mal' : '10 · insoportable',
    whereTitle: '¿En qué parte del cuerpo?',
    whereHint: isPet
      ? 'Opcional. Señala en la figura dónde lo notas; ahí verás también cómo se llama la zona.'
      : 'Opcional. Señala en el modelo dónde lo sientes; ahí verás también cómo se llama la zona.',
    notesPlaceholder: isPet ? 'Cómo empezó, qué comió, cómo está de ánimo, qué le diste…' : 'Cómo empezó, qué lo alivia o empeora, qué tomaste…',
    customTriggerExample: isPet ? 'Ej. Premio nuevo' : 'Ej. Cafeína',
    alarmNote: isPet
      ? PetSymptomCatalog.ALARM_NOTE
      : 'Si el dolor es muy fuerte, apareció de repente o no cede, busca atención médica o llama a emergencias. ' +
        'Esta app no sustituye una valoración profesional.',
    emptyMessage: isPet
      ? `Anota aquí lo que le notes a ${name.trim() || 'tu mascota'}: vómito, cojera, comezón… con qué tan fuerte fue y cuándo. Le servirá mucho a tu veterinario.`
      : 'Cuando algo te moleste, anótalo aquí: qué sientes, qué tan fuerte y en qué parte del cuerpo. ' +
        'Con el tiempo podrás ver patrones y contárselos mejor a tu médico.',
  }
}

/** Kit según el perfil activo. */
export function useSymptomKit(): SymptomKit {
  const { info, isPet } = useActive()
  return symptomKit(isPet, info.species, displayName(info))
}
