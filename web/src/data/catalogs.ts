// Catálogos compartidos (mismas claves que la app de Android).

export type Opt<K = string> = readonly [key: K, label: string]
const labelOf = <K,>(list: readonly Opt<K>[], key: K, fallback = ''): string => list.find((o) => o[0] === key)?.[1] ?? fallback

/** Tipos de medición. `key` es lo que se guarda en la base. */
export interface MeasureType {
  key: string
  label: string
  unit: string
  decimals: number
  /** Etiqueta del valor principal en el formulario. */
  valueLabel: string
  /** Etiqueta del segundo valor (presión diastólica), si lo hay. */
  secondLabel?: string
  min: number
  max: number
  /** Contextos sugeridos. */
  tags: string[]
  /** ¿Aplica también a mascotas? */
  forPets: boolean
}

export const MEASURE_TYPES: MeasureType[] = [
  { key: 'weight', label: 'Peso', unit: 'kg', decimals: 1, valueLabel: 'Peso', min: 0.01, max: 400, tags: [], forPets: true },
  { key: 'height', label: 'Estatura', unit: 'cm', decimals: 1, valueLabel: 'Estatura', min: 20, max: 250, tags: [], forPets: false },
  { key: 'bp', label: 'Presión arterial', unit: 'mmHg', decimals: 0, valueLabel: 'Sistólica (alta)', secondLabel: 'Diastólica (baja)', min: 40, max: 300, tags: ['En reposo', 'Por la mañana', 'Por la noche', 'Después de ejercicio'], forPets: false },
  { key: 'hr', label: 'Frecuencia cardiaca', unit: 'lpm', decimals: 0, valueLabel: 'Pulsaciones', min: 20, max: 400, tags: ['En reposo', 'Después de ejercicio'], forPets: true },
  { key: 'glucose', label: 'Glucosa', unit: 'mg/dL', decimals: 0, valueLabel: 'Glucosa', min: 20, max: 700, tags: ['En ayunas', 'Antes de comer', '2 h después de comer', 'Antes de dormir'], forPets: false },
  { key: 'temp', label: 'Temperatura', unit: '°C', decimals: 1, valueLabel: 'Temperatura', min: 30, max: 45, tags: [], forPets: true },
  { key: 'spo2', label: 'Oxigenación', unit: '%', decimals: 0, valueLabel: 'SpO₂', min: 50, max: 100, tags: [], forPets: false },
  { key: 'waist', label: 'Cintura', unit: 'cm', decimals: 1, valueLabel: 'Cintura', min: 20, max: 300, tags: [], forPets: false },
  { key: 'hip', label: 'Cadera', unit: 'cm', decimals: 1, valueLabel: 'Cadera', min: 20, max: 300, tags: [], forPets: false },
  { key: 'fat', label: 'Grasa corporal', unit: '%', decimals: 1, valueLabel: 'Grasa corporal', min: 1, max: 80, tags: [], forPets: false },
]

export const measureType = (key: string): MeasureType | undefined => MEASURE_TYPES.find((t) => t.key === key)
/** Tipos que aplican al perfil: todos para personas; solo los marcados para mascotas. */
export const measureTypesFor = (pet: boolean): MeasureType[] => (pet ? MEASURE_TYPES.filter((t) => t.forPets) : MEASURE_TYPES)

/** Categorías de estudios / chequeos. */
export const StudyCategories = {
  all: [
    ['lab', 'Laboratorio'], ['imagen', 'Imagen (rayos X, ultrasonido…)'], ['chequeo', 'Chequeo general'], ['cardio', 'Corazón'],
    ['gine', 'Ginecológico'], ['uro', 'Urológico'], ['dental', 'Dental'], ['vista', 'Vista'], ['oido', 'Oído'], ['piel', 'Piel'],
    ['vacuna', 'Vacuna'], ['otro', 'Otro'],
  ] as readonly Opt[],
  label: (key: string): string => labelOf(StudyCategories.all, key, 'Otro'),
  /** Etiqueta corta para chips. */
  short: (key: string): string => StudyCategories.label(key).split(' (')[0],
  statuses: [['normal', 'Todo normal'], ['seguimiento', 'Requiere seguimiento'], ['pendiente', 'Resultados pendientes']] as readonly Opt[],
  statusLabel: (key: string): string => labelOf(StudyCategories.statuses, key),
}

/** Catálogos del módulo de ciclo menstrual. */
export const CycleCatalog = {
  flows: [[0, 'Sin sangrado'], [1, 'Manchado'], [2, 'Ligero'], [3, 'Moderado'], [4, 'Abundante']] as readonly Opt<number>[],
  flowLabel: (flow: number): string => labelOf(CycleCatalog.flows, flow),
  symptoms: [
    ['colicos', 'Cólicos'], ['dolor_cabeza', 'Dolor de cabeza'], ['migrana', 'Migraña'], ['dolor_espalda', 'Dolor de espalda baja'],
    ['senos', 'Sensibilidad en senos'], ['hinchazon', 'Hinchazón'], ['acne', 'Acné'], ['fatiga', 'Fatiga'], ['nauseas', 'Náuseas'],
    ['antojos', 'Antojos'], ['insomnio', 'Insomnio'], ['mareo', 'Mareo'], ['diarrea', 'Diarrea'], ['estrenimiento', 'Estreñimiento'],
    ['dolor_ovulacion', 'Dolor de ovulación'], ['dolor_pelvico', 'Dolor pélvico'], ['bochornos', 'Bochornos'],
    ['resequedad', 'Resequedad vaginal'], ['comezon', 'Comezón / irritación'], ['flujo_inusual', 'Flujo inusual'],
    ['libido_alta', 'Libido alta'], ['libido_baja', 'Libido baja'],
  ] as readonly Opt[],
  moods: [
    ['tranquila', 'Tranquilidad'], ['feliz', 'Alegría'], ['energia', 'Con energía'], ['sensible', 'Sensibilidad'],
    ['irritable', 'Irritabilidad'], ['triste', 'Tristeza'], ['ansiosa', 'Ansiedad'], ['cambios', 'Cambios de humor'],
    ['estres', 'Estrés'], ['apatia', 'Apatía'],
  ] as readonly Opt[],
  mucus: [['seco', 'Seco'], ['pegajoso', 'Pegajoso'], ['cremoso', 'Cremoso'], ['acuoso', 'Acuoso'], ['clara', 'Clara de huevo']] as readonly Opt[],
  sex: [[1, 'Con protección'], [2, 'Sin protección']] as readonly Opt<number>[],
  tests: [[1, 'Negativa'], [2, 'Positiva']] as readonly Opt<number>[],
  symptomLabel: (key: string): string => labelOf(CycleCatalog.symptoms, key, key),
  moodLabel: (key: string): string => labelOf(CycleCatalog.moods, key, key),
  mucusLabel: (key: string): string => labelOf(CycleCatalog.mucus, key, key),
  sexLabel: (v: number): string => labelOf(CycleCatalog.sex, v),
  testLabel: (v: number): string => labelOf(CycleCatalog.tests, v),
  /** "a,b,c" → [a, b, c] */
  split: (csv: string): string[] => csv.split(',').map((s) => s.trim()).filter(Boolean),
  join: (keys: Iterable<string>): string => [...keys].join(','),
}

/** Síntomas frecuentes para sugerir al registrar (personas). */
export const SymptomCatalog = {
  groups: [
    ['General', ['Fiebre', 'Escalofríos', 'Fatiga', 'Debilidad', 'Mareo', 'Malestar general', 'Insomnio', 'Sudoración']],
    ['Cabeza', ['Dolor de cabeza', 'Migraña', 'Dolor de oído', 'Dolor de muela', 'Visión borrosa', 'Zumbido de oídos']],
    ['Respiratorio', ['Tos', 'Dolor de garganta', 'Congestión nasal', 'Escurrimiento nasal', 'Estornudos', 'Falta de aire', 'Flemas']],
    ['Digestivo', ['Dolor abdominal', 'Náuseas', 'Vómito', 'Diarrea', 'Estreñimiento', 'Acidez / reflujo', 'Gases / inflamación', 'Falta de apetito']],
    ['Músculos y huesos', ['Dolor de espalda', 'Dolor de cuello', 'Dolor muscular', 'Dolor articular', 'Calambre', 'Rigidez', 'Hinchazón', 'Golpe / contusión']],
    ['Piel', ['Sarpullido', 'Comezón', 'Ronchas', 'Herida', 'Moretón', 'Quemadura']],
    ['Corazón y pecho', ['Dolor en el pecho', 'Palpitaciones', 'Presión en el pecho']],
    ['Urinario', ['Ardor al orinar', 'Orinar con frecuencia', 'Dolor pélvico']],
    ['Ánimo', ['Ansiedad', 'Tristeza', 'Estrés', 'Irritabilidad']],
    ['Alergia', ['Reacción alérgica', 'Ojos llorosos', 'Hinchazón de labios o párpados']],
  ] as readonly (readonly [string, readonly string[]])[],
  triggers: ['Estrés', 'Comida', 'Falta de sueño', 'Ejercicio', 'Clima', 'Alcohol', 'Medicamento', 'Mala postura', 'Pantallas', 'Ayuno', 'Periodo', 'Alergia'],
  intensityLabel: (i: number): string => (i <= 2 ? 'Muy leve' : i <= 4 ? 'Leve' : i <= 6 ? 'Moderado' : i <= 8 ? 'Fuerte' : 'Muy fuerte'),
}

export const SexOptions = {
  all: [['F', 'Mujer'], ['M', 'Hombre'], ['X', 'Prefiero no decir']] as readonly Opt[],
  label: (key: string): string => labelOf(SexOptions.all, key),
}

export const SelfExamTypes = {
  all: [['mama', 'Mamaria'], ['testicular', 'Testicular'], ['piel', 'Piel y lunares']] as readonly Opt[],
  label: (key: string): string => labelOf(SelfExamTypes.all, key, key),
}

/** Especies de mascota. */
export const PetSpecies = {
  all: [
    ['perro', 'Perro'], ['gato', 'Gato'], ['ave', 'Ave'], ['conejo', 'Conejo'], ['roedor', 'Hámster / roedor'],
    ['reptil', 'Reptil / tortuga'], ['pez', 'Pez'], ['otro', 'Otra'],
  ] as readonly Opt[],
  label: (key: string): string => labelOf(PetSpecies.all, key, 'Mascota'),
  sexes: [['F', 'Hembra'], ['M', 'Macho'], ['X', 'No sé']] as readonly Opt[],
  /** Especies con figura en el atlas corporal. */
  withAtlas: ['perro', 'gato'],
}

/** Tipos de registro del módulo de vacunas. */
export const VaccinationTypes = {
  all: [['vacuna', 'Vacuna'], ['desp_interna', 'Desparasitación interna'], ['desp_externa', 'Pulgas y garrapatas'], ['otro', 'Otro']] as readonly Opt[],
  /** Para personas solo tienen sentido "vacuna" y "otro". */
  forProfile: (pet: boolean): readonly Opt[] => (pet ? VaccinationTypes.all : VaccinationTypes.all.filter((o) => o[0] === 'vacuna' || o[0] === 'otro')),
  label: (key: string): string => labelOf(VaccinationTypes.all, key, 'Otro'),
}

/** Colores de avatar de perfil (índice = Profile.avatarColor). */
export const AVATAR_COLORS = ['#1B6B58', '#B5446E', '#2E7FB3', '#B26A00', '#7A55C7', '#5E7A2F', '#C4553A', '#4B635A']
