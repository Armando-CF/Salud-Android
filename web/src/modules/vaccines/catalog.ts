// Catálogo de sugerencias del módulo de vacunas (personas, perros, gatos y otras mascotas).
// Portado de VaccineCatalog.kt. No es un esquema médico: solo ayuda a escribir nombres y a proponer fechas.
import type { ProfileInfo } from '../../data/types'
import { Fmt } from '../../lib/dates'

export const VACUNA = 'vacuna'
export const DESP_INTERNA = 'desp_interna'
export const DESP_EXTERNA = 'desp_externa'
export const OTRO = 'otro'

/**
 * Sugerencia de nombre para el registro de vacunas.
 * `intervalDays`: intervalo típico hasta la siguiente aplicación; SOLO sirve para proponer una fecha con un
 * toque (orientativo: manda la cartilla y lo que indique la médica/el médico o veterinario).
 */
export interface VaccineSuggestion {
  name: string
  /** Clave de VaccinationTypes. */
  type: string
  group: string
  intervalDays: number | null
  /** Qué cubre o cuándo suele aplicarse (texto corto). */
  hint: string
}

export const DOSES = ['1.ª dosis', '2.ª dosis', '3.ª dosis', 'Refuerzo', 'Anual', 'Dosis única']

const G_BEBE = 'Recién nacidos y bebés'
const G_INFANCIA = 'Infancia'
const G_ADOLESCENCIA = 'Adolescencia'
const G_ADULTOS = 'Adultos y refuerzos'
const G_OTROS = 'Otras aplicaciones'

const s = (name: string, type: string, group: string, intervalDays: number | null = null, hint = ''): VaccineSuggestion => ({ name, type, group, intervalDays, hint })
const v = (name: string, group: string, interval: number | null = null, hint = ''): VaccineSuggestion => s(name, VACUNA, group, interval, hint)

/** Esquema habitual en México (orientativo). */
const PEOPLE: VaccineSuggestion[] = [
  v('BCG', G_BEBE, null, 'Tuberculosis · al nacer'),
  v('Hepatitis B', G_BEBE, null, 'Al nacer y en la infancia'),
  v('Hexavalente', G_BEBE, null, 'Difteria, tosferina, tétanos, polio, Hib y hepatitis B'),
  v('Pentavalente', G_BEBE, null, 'Difteria, tosferina, tétanos, polio y Hib'),
  v('Rotavirus', G_BEBE, null, 'Diarrea por rotavirus'),
  v('Neumococo conjugada', G_BEBE, null, 'Neumonía y meningitis'),
  v('Influenza', G_BEBE, 365, 'Temporada de invierno'),
  v('SRP (triple viral)', G_INFANCIA, null, 'Sarampión, rubéola y paperas'),
  v('DPT', G_INFANCIA, null, 'Difteria, tosferina y tétanos · refuerzo'),
  v('Varicela', G_INFANCIA),
  v('Hepatitis A', G_INFANCIA),
  v('Polio (Sabin)', G_INFANCIA, null, 'Gotas en campañas de vacunación'),
  v('VPH', G_ADOLESCENCIA, null, 'Virus del papiloma humano'),
  v('Tdpa', G_ADOLESCENCIA, null, 'Tétanos, difteria y tosferina · también en el embarazo'),
  v('SR (doble viral)', G_ADOLESCENCIA, null, 'Sarampión y rubéola'),
  v('Meningococo', G_ADOLESCENCIA),
  v('Td (tétanos y difteria)', G_ADULTOS, 3650, 'Refuerzo para personas adultas'),
  v('COVID-19', G_ADULTOS, null, 'Refuerzos según la campaña vigente'),
  v('Neumococo polisacárida', G_ADULTOS, null, 'Personas adultas mayores'),
  v('Herpes zóster', G_ADULTOS, null, 'Personas adultas mayores'),
  v('Fiebre amarilla', G_ADULTOS, null, 'Para algunos viajes'),
  v('Fiebre tifoidea', G_ADULTOS, null, 'Para algunos viajes'),
  s('Desparasitación', OTRO, G_OTROS, 180, 'Campañas de salud'),
  s('Vitamina A', OTRO, G_OTROS, null, 'Campañas de salud infantil'),
  s('Prueba de tuberculina (PPD)', OTRO, G_OTROS),
  s('Inmunoglobulina', OTRO, G_OTROS),
]

const G_VAC = 'Vacunas'
const G_INT = 'Desparasitación interna'
const G_EXT = 'Pulgas y garrapatas'

const PET_INTERNAL: VaccineSuggestion[] = [
  s('Desparasitante en tableta', DESP_INTERNA, G_INT, 90, 'Lombrices y otros parásitos intestinales'),
  s('Desparasitante en suspensión', DESP_INTERNA, G_INT, 90, 'Común en cachorros y gatitos'),
  s('Desparasitante en pasta', DESP_INTERNA, G_INT, 90),
  s('Desparasitante inyectable', DESP_INTERNA, G_INT, 90),
]

const PET_EXTERNAL: VaccineSuggestion[] = [
  s('Pipeta', DESP_EXTERNA, G_EXT, 30, 'Se aplica sobre la piel'),
  s('Tableta masticable', DESP_EXTERNA, G_EXT, 30, 'Algunas marcas duran 3 meses'),
  s('Collar antipulgas', DESP_EXTERNA, G_EXT, 180, 'La duración depende de la marca'),
  s('Spray', DESP_EXTERNA, G_EXT, 30),
  s('Baño garrapaticida', DESP_EXTERNA, G_EXT, 30),
]

const PET_OTHER: VaccineSuggestion[] = [
  s('Revisión preventiva', OTRO, G_OTROS, 365, 'Chequeo general'),
  s('Limpieza dental', OTRO, G_OTROS, 365),
  s('Prevención de gusano del corazón', OTRO, G_OTROS, 30, 'Dirofilaria, donde hay riesgo'),
]

const DOGS: VaccineSuggestion[] = [
  v('Rabia', G_VAC, 365),
  v('Múltiple (quíntuple)', G_VAC, 365, 'Moquillo, parvovirus, hepatitis, parainfluenza y leptospira'),
  v('Séxtuple', G_VAC, 365, 'Quíntuple más coronavirus'),
  v('Puppy (moquillo y parvovirus)', G_VAC, 21, 'Primera vacuna de cachorro'),
  v('Parvovirus', G_VAC, 21, 'Serie de cachorro'),
  v('Moquillo', G_VAC, 21, 'Serie de cachorro'),
  v('Bordetella', G_VAC, 365, 'Tos de las perreras'),
  v('Leptospira', G_VAC, 365),
  v('Giardia', G_VAC, 365),
  v('Coronavirus canino', G_VAC, 365),
  ...PET_INTERNAL, ...PET_EXTERNAL, ...PET_OTHER,
]

const CATS: VaccineSuggestion[] = [
  v('Triple felina', G_VAC, 365, 'Rinotraqueítis, calicivirus y panleucopenia'),
  v('Rabia', G_VAC, 365),
  v('Leucemia felina', G_VAC, 365, 'Sobre todo si sale de casa'),
  v('Cuádruple felina', G_VAC, 365, 'Triple más clamidia'),
  v('Panleucopenia', G_VAC, 365),
  ...PET_INTERNAL, ...PET_EXTERNAL, ...PET_OTHER.filter((x) => !x.name.includes('gusano')),
]

const RABBITS: VaccineSuggestion[] = [
  v('Mixomatosis', G_VAC, 180),
  v('Enfermedad hemorrágica vírica', G_VAC, 365),
  v('Rabia', G_VAC, 365),
  ...PET_INTERNAL, ...PET_EXTERNAL, ...PET_OTHER.slice(0, 1),
]

const BIRDS: VaccineSuggestion[] = [
  v('Newcastle', G_VAC, 365),
  v('Viruela aviar', G_VAC, 365),
  ...PET_INTERNAL,
  s('Tratamiento contra ácaros', DESP_EXTERNA, G_EXT, 90),
  ...PET_OTHER.slice(0, 1),
]

const OTHER_PETS: VaccineSuggestion[] = [
  v('Rabia', G_VAC, 365, 'Si aplica a su especie'),
  ...PET_INTERNAL, ...PET_EXTERNAL.slice(0, 2), ...PET_OTHER.slice(0, 1),
]

function groupOrder(age: number | null): string[] {
  if (age === null || age < 2) return [G_BEBE, G_INFANCIA, G_ADOLESCENCIA, G_ADULTOS, G_OTROS]
  if (age < 10) return [G_INFANCIA, G_BEBE, G_ADOLESCENCIA, G_ADULTOS, G_OTROS]
  if (age < 20) return [G_ADOLESCENCIA, G_ADULTOS, G_INFANCIA, G_BEBE, G_OTROS]
  return [G_ADULTOS, G_ADOLESCENCIA, G_INFANCIA, G_BEBE, G_OTROS]
}

/** Nombre comparable: minúsculas, sin acentos, sin espacios repetidos. */
export const normalizeName = (text: string): string =>
  text.trim().toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/\s+/g, ' ')

type ProfileLike = Pick<ProfileInfo, 'kind' | 'species' | 'birthEpochDay'>

/** Todas las sugerencias para el perfil (personas: ordenadas con la etapa de su edad primero). */
export function suggestions(profile: ProfileLike, today?: number): VaccineSuggestion[] {
  if (profile.kind !== 'pet') {
    const order = groupOrder(profile.birthEpochDay == null ? null : Fmt.age(profile.birthEpochDay, today))
    const rank = (g: string) => { const i = order.indexOf(g); return i < 0 ? order.length : i }
    // orden estable por grupo
    return PEOPLE.map((x, i) => ({ x, i })).sort((a, b) => rank(a.x.group) - rank(b.x.group) || a.i - b.i).map((p) => p.x)
  }
  switch (profile.species) {
    case 'perro': return DOGS
    case 'gato': return CATS
    case 'conejo': return RABBITS
    case 'ave': return BIRDS
    default: return OTHER_PETS
  }
}

/** Sugerencias filtradas por tipo y por lo escrito (sin distinguir mayúsculas ni acentos). */
export function filterSuggestions(profile: ProfileLike, type: string, query: string, today?: number): VaccineSuggestion[] {
  const q = normalizeName(query)
  return suggestions(profile, today).filter((x) => x.type === type && (q === '' || normalizeName(x.name).includes(q) || normalizeName(x.hint).includes(q)))
}

/** Sugerencia cuyo nombre coincide con `name` para ese perfil (y tipo, si se indica). */
export function findSuggestion(profile: ProfileLike, name: string, type: string | null = null, today?: number): VaccineSuggestion | null {
  const n = normalizeName(name)
  if (!n) return null
  return suggestions(profile, today).find((x) => normalizeName(x.name) === n && (type === null || x.type === type)) ?? null
}

const unit = (days: number): { n: number; one: string; many: string } =>
  days >= 365 && days % 365 === 0 ? { n: days / 365, one: 'año', many: 'años' }
    : days >= 30 && days % 30 === 0 ? { n: days / 30, one: 'mes', many: 'meses' }
      : days % 7 === 0 ? { n: days / 7, one: 'semana', many: 'semanas' }
        : { n: days, one: 'día', many: 'días' }

/** 365 → "cada año", 90 → "cada 3 meses", 21 → "cada 3 semanas". */
export function intervalLabel(days: number): string {
  const u = unit(days)
  return u.n === 1 ? `cada ${u.one}` : `cada ${u.n} ${u.many}`
}

/** 365 → "en 1 año", 90 → "en 3 meses" (para el atajo de la próxima fecha). */
export function inLabel(days: number): string {
  const u = unit(days)
  return u.n === 1 ? `en 1 ${u.one}` : `en ${u.n} ${u.many}`
}

export const VaccineCatalog = { VACUNA, DESP_INTERNA, DESP_EXTERNA, OTRO, doses: DOSES, suggestions, filter: filterSuggestions, find: findSuggestion, normalize: normalizeName, intervalLabel, inLabel }
