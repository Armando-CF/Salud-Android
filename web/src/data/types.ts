// Modelo de datos. Los nombres de campos son los mismos que en la app de Android,
// para que los respaldos sean compatibles entre ambas.

/** Ficha del perfil. Siempre existe una sola fila con id = 1 dentro de la base de cada perfil. */
export interface Profile {
  id: 1
  name: string
  /** "F" mujer/hembra, "M" hombre/macho, "X" sin especificar. */
  sex: string
  birthEpochDay: number | null
  heightCm: number | null
  goalWeightKg: number | null
  bloodType: string
  allergies: string
  conditions: string
  emergencyName: string
  emergencyPhone: string
  doctorName: string
  doctorPhone: string
  insurance: string
  notes: string
  /** "person" o "pet". No cambia después de crear el perfil. */
  kind: string
  /** Solo mascotas: clave de PetSpecies. */
  species: string
  breed: string
  /** Solo mascotas: esterilizado/a (null = no se sabe). */
  sterilized: boolean | null
  microchip: string
  markings: string
  /** Solo personas: parentesco ("Yo", "Hija"…). */
  relation: string
  /** Índice del color del avatar (0..7). */
  avatarColor: number
}

export const emptyProfile = (): Profile => ({
  id: 1, name: '', sex: 'X', birthEpochDay: null, heightCm: null, goalWeightKg: null, bloodType: '', allergies: '',
  conditions: '', emergencyName: '', emergencyPhone: '', doctorName: '', doctorPhone: '', insurance: '', notes: '',
  kind: 'person', species: '', breed: '', sterilized: null, microchip: '', markings: '', relation: '', avatarColor: 0,
})

/** Un síntoma o malestar registrado. */
export interface SymptomEntry {
  id?: number
  timestamp: number
  name: string
  /** 1 (muy leve) a 10 (insoportable). */
  intensity: number
  /** Id de la zona del atlas corporal (opcional). */
  regionId: string | null
  regionLabel: string | null
  durationMin: number | null
  /** Desencadenantes, separados por coma. */
  tags: string
  notes: string
}

/** Medición numérica (peso, presión, glucosa…). `type` = clave de MeasureType. */
export interface Measurement {
  id?: number
  timestamp: number
  type: string
  value: number
  /** Segundo valor (presión diastólica). */
  value2: number | null
  /** Contexto libre ("En ayunas"). */
  tag: string
  notes: string
}

/** Bienestar diario: una fila por día (clave = epochDay). */
export interface DailyLog {
  epochDay: number
  mood: number | null
  energy: number | null
  stress: number | null
  sleepHours: number | null
  waterGlasses: number | null
  exerciseMin: number | null
  notes: string
}

export interface Study {
  id?: number
  epochDay: number
  title: string
  /** Clave de StudyCategories. */
  category: string
  place: string
  doctor: string
  /** "normal", "seguimiento", "pendiente" o "". */
  status: string
  summary: string
  followUpEpochDay: number | null
}

export interface StudyResult {
  id?: number
  studyId: number
  name: string
  value: number | null
  /** Resultado no numérico ("Negativo"). */
  textValue: string
  unit: string
  refMin: number | null
  refMax: number | null
  notes: string
}

/** Archivo adjunto (foto/PDF) de un estudio; el contenido se guarda en IndexedDB. */
export interface StudyAttachment {
  id?: number
  studyId: number
  /** Nombre interno (compatibilidad con Android); en la web es igual a displayName. */
  fileName: string
  displayName: string
  mime: string
  /** Contenido del archivo. */
  data: ArrayBuffer
}

/** Registro de un día del ciclo menstrual (clave = epochDay). */
export interface CycleDay {
  epochDay: number
  /** 0 sin sangrado, 1 manchado, 2 ligero, 3 moderado, 4 abundante. */
  flow: number
  symptoms: string
  moods: string
  mucus: string
  basalTemp: number | null
  /** 0 sin registro, 1 con protección, 2 sin protección. */
  sex: number
  /** 0 sin registro, 1 negativa, 2 positiva. */
  ovulationTest: number
  pregnancyTest: number
  contraceptive: boolean
  notes: string
}

export const emptyCycleDay = (epochDay: number): CycleDay => ({
  epochDay, flow: 0, symptoms: '', moods: '', mucus: '', basalTemp: null, sex: 0, ovulationTest: 0, pregnancyTest: 0, contraceptive: false, notes: '',
})

export interface Medication {
  id?: number
  name: string
  dose: string
  instructions: string
  /** Horas "HH:mm" separadas por coma. Vacío = según sea necesario. */
  times: string
  /** Máscara de días: bit 0 = lunes … bit 6 = domingo. 127 = todos. */
  daysMask: number
  startEpochDay: number
  endEpochDay: number | null
  active: boolean
  remind: boolean
  notes: string
}

export interface MedIntake {
  id?: number
  medicationId: number
  epochDay: number
  /** Hora programada "HH:mm" o "" si fue toma libre. */
  slot: string
  timestamp: number
  /** "taken" u "skipped". */
  status: string
}

export interface Appointment {
  id?: number
  timestamp: number
  title: string
  doctor: string
  specialty: string
  place: string
  notes: string
  remind: boolean
  done: boolean
}

export interface SelfExam {
  id?: number
  epochDay: number
  /** "mama", "testicular", "piel". */
  type: string
  normal: boolean
  notes: string
}

export interface Vaccination {
  id?: number
  epochDay: number
  /** "vacuna", "desp_interna", "desp_externa" u "otro". */
  type: string
  name: string
  dose: string
  nextDueEpochDay: number | null
  place: string
  lot: string
  notes: string
  remind: boolean
}

/** Resumen de un perfil para listas y encabezados. */
export interface ProfileInfo {
  id: number
  name: string
  kind: string
  species: string
  sex: string
  relation: string
  avatarColor: number
  birthEpochDay: number | null
}
