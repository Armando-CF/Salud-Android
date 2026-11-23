// Adaptación del módulo de estudios al tipo de perfil: persona adulta, menor de edad o mascota
// (portado de StudyProfile.kt). Lógica pura para poder probarla.
import { useMemo } from 'react'
import { StudyCategories, type Opt } from '../../data/catalogs'
import { useActive } from '../../data/profiles'
import type { StudyResult } from '../../data/types'
import { Fmt, today } from '../../lib/dates'
import { StudyCatalog, normalizeName, rangeFor, type LabGroup } from './catalog'

export const VACCINE = 'vacuna'
/** Texto del aviso que lleva a la sección propia de vacunas. */
export const VACCINES_MOVED = 'Las vacunas ahora tienen su propia sección.'

const PET_CATEGORIES = ['lab', 'imagen', 'chequeo', 'cardio', 'dental', 'piel', 'otro']

const PET_GROUPS: LabGroup[] = [
  { name: 'Química sanguínea', params: ['Glucosa', 'Urea', 'Creatinina', 'TGP (ALT)', 'Fosfatasa alcalina', 'Proteínas totales', 'Albúmina'] },
  { name: 'Biometría hemática', params: ['Hemoglobina', 'Hematocrito', 'Eritrocitos', 'Leucocitos', 'Plaquetas'] },
  { name: 'Función hepática', params: ['TGO (AST)', 'TGP (ALT)', 'GGT', 'Fosfatasa alcalina', 'Bilirrubina total', 'Albúmina'] },
  { name: 'Función renal', params: ['Urea', 'Nitrógeno ureico (BUN)', 'Creatinina'] },
  { name: 'Electrolitos', params: ['Sodio', 'Potasio', 'Calcio'] },
  { name: 'Examen general de orina', params: ['Densidad urinaria', 'pH urinario'] },
]

export interface StudyDefaults { unit: string; refMin: number | null; refMax: number | null }

export class StudyProfile {
  constructor(
    readonly isPet = false,
    /** Clave de PetSpecies ("perro", "gato"…) si es mascota. */
    readonly species = '',
    readonly sex = 'X',
    /** Persona menor de 18 años. */
    readonly isMinor = false,
  ) {}

  /** Etiqueta del campo de quien indicó o atendió el estudio. */
  get doctorLabel(): string { return this.isPet ? 'Veterinaria o veterinario' : 'Médico que lo indicó o atendió' }
  get placeLabel(): string { return this.isPet ? 'Clínica veterinaria o laboratorio' : 'Laboratorio, clínica u hospital' }
  /** A quién consultar las dudas. */
  get whoToAsk(): string { return this.isPet ? 'tu veterinaria o veterinario' : 'tu médico' }
  /** De quién es el laboratorio. */
  get labName(): string { return this.isPet ? 'el laboratorio veterinario' : 'tu laboratorio' }

  /** Nota sobre los rangos de referencia en el editor. */
  get rangesNote(): string {
    if (this.isPet) return 'Usa los rangos de referencia que vienen en el reporte del laboratorio veterinario: cambian según la especie, la raza y la edad.'
    if (this.isMinor) return 'Rangos de referencia de adultos: verifica los del reporte, cambian con la edad.'
    return 'Los rangos que se rellenan solos son orientativos para adultos. Cámbialos por los que vengan en el reporte de tu laboratorio.'
  }

  /**
   * Categorías que se ofrecen al crear o editar un estudio. "Vacuna" ya no se ofrece (las vacunas tienen
   * su propia sección) salvo que el estudio que se edita ya la tenga (`current`).
   */
  categories(current?: string | null): Opt[] {
    const known = StudyCategories.all.map((o) => o[0])
    const keys = this.isPet ? PET_CATEGORIES : known.filter((k) => k !== VACCINE)
    const all = current != null && !keys.includes(current) && known.includes(current) ? [...keys, current] : keys
    return all.map((k) => [k, this.categoryShort(k)] as const)
  }

  /** Etiqueta corta de una categoría para chips y listas. */
  categoryShort(key: string): string {
    return this.isPet && key === 'chequeo' ? 'Consulta o revisión' : StudyCategories.short(key)
  }

  /** Títulos sugeridos para una categoría. */
  titlesFor(category: string): string[] {
    if (category === VACCINE) return []
    return this.isPet ? this.petTitles(category) : StudyCatalog.titlesFor(category)
  }

  /** Grupos de parámetros comunes. */
  get groups(): LabGroup[] { return this.isPet ? PET_GROUPS : StudyCatalog.groups }

  /**
   * Unidad y rango con que se rellena un parámetro al elegirlo: primero lo último que la persona capturó
   * con ese nombre; si no hay, el catálogo. Para mascotas el catálogo solo aporta la unidad (sus rangos son
   * de personas adultas y no aplican a animales).
   */
  defaultsFor(name: string, pastResults: StudyResult[]): StudyDefaults | null {
    const n = normalizeName(name)
    let past: StudyResult | undefined
    for (const r of pastResults) if (r.value != null && normalizeName(r.name) === n) past = r
    if (past) return { unit: past.unit, refMin: past.refMin, refMax: past.refMax }
    const p = StudyCatalog.find(name)
    if (!p) return null
    if (this.isPet) return { unit: p.unit, refMin: null, refMax: null }
    const [lo, hi] = rangeFor(p, this.sex)
    return { unit: p.unit, refMin: lo, refMax: hi }
  }

  private petTitles(category: string): string[] {
    switch (category) {
      case 'lab':
        return [
          'Química sanguínea', 'Biometría hemática', 'Examen general de orina', 'Coproparasitoscópico',
          ...(this.species === 'gato' ? ['Prueba de leucemia e inmunodeficiencia felina'] : this.species === 'perro' ? ['Prueba de parvovirus y moquillo', 'Prueba de gusano del corazón'] : []),
          'Citología', 'Cultivo',
        ]
      case 'imagen': return ['Radiografía', 'Ultrasonido abdominal', 'Radiografía de cadera', 'Ecocardiograma', 'Tomografía']
      case 'chequeo': return ['Consulta veterinaria', 'Revisión anual', 'Revisión de cachorro', 'Revisión posoperatoria', 'Valoración de peso y nutrición']
      case 'cardio': return ['Electrocardiograma', 'Ecocardiograma', 'Medición de presión arterial']
      case 'dental': return ['Limpieza dental', 'Revisión dental', 'Extracción dental']
      case 'piel': return ['Consulta dermatológica', 'Raspado de piel', 'Prueba de alergias']
      case 'otro': return ['Esterilización', 'Cirugía', 'Hospitalización', 'Consulta de urgencia']
      default: return []
    }
  }

  static isMinor(birthEpochDay: number | null | undefined, at: number): boolean {
    return birthEpochDay != null && at >= birthEpochDay && Fmt.age(birthEpochDay, at) < 18
  }
}

/** StudyProfile del perfil activo (se actualiza si cambia la ficha). */
export function useStudyProfile(): StudyProfile {
  const { info, isPet } = useActive()
  const t = today()
  return useMemo(
    () => new StudyProfile(isPet, info.species, info.sex, !isPet && StudyProfile.isMinor(info.birthEpochDay, t)),
    [isPet, info.species, info.sex, info.birthEpochDay, t],
  )
}
