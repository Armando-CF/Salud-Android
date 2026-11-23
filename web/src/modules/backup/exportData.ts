// Qué exportar y los datos ya cargados y filtrados (port de ExportModel.kt).

import type { SaviaDB } from '../../data/db'
import { getPref } from '../../data/prefs'
import {
  emptyProfile, type Appointment, type CycleDay, type DailyLog, type MedIntake, type Measurement, type Medication, type Profile,
  type SelfExam, type Study, type StudyResult, type SymptomEntry, type Vaccination,
} from '../../data/types'
import { millisToEpochDay, today as todayFn } from '../../lib/dates'
import { analyzeCycle, type CycleAnalysis, type CycleRecord } from './cycleLite'

/** Bloques de información que se pueden exportar. */
export interface ExportModule {
  key: ModuleKey
  label: string
  slug: string
  description: string
  /** ¿Aplica a mascotas? */
  forPets: boolean
}

export type ModuleKey = 'PERFIL' | 'CICLO' | 'SINTOMAS' | 'MEDICIONES' | 'BIENESTAR' | 'ESTUDIOS' | 'VACUNAS' | 'MEDICAMENTOS' | 'CITAS' | 'AUTOEXPLORACION'

export const MODULES: ExportModule[] = [
  { key: 'PERFIL', label: 'Mi ficha', slug: 'ficha', description: 'Datos personales y de emergencia', forPets: true },
  { key: 'CICLO', label: 'Ciclo menstrual', slug: 'ciclo', description: 'Días registrados, ciclos y estadísticas', forPets: false },
  { key: 'SINTOMAS', label: 'Síntomas', slug: 'sintomas', description: 'Malestares con intensidad y zona del cuerpo', forPets: true },
  { key: 'MEDICIONES', label: 'Peso y mediciones', slug: 'mediciones', description: 'Peso, presión, glucosa y demás', forPets: true },
  { key: 'BIENESTAR', label: 'Bienestar diario', slug: 'bienestar', description: 'Ánimo, energía, sueño, agua y ejercicio', forPets: false },
  { key: 'ESTUDIOS', label: 'Estudios y chequeos', slug: 'estudios', description: 'Estudios con sus resultados', forPets: true },
  { key: 'VACUNAS', label: 'Vacunas', slug: 'vacunas', description: 'Vacunas aplicadas y próximas fechas', forPets: true },
  { key: 'MEDICAMENTOS', label: 'Medicamentos', slug: 'medicamentos', description: 'Tratamientos y tomas registradas', forPets: true },
  { key: 'CITAS', label: 'Citas médicas', slug: 'citas', description: 'Citas pasadas y próximas', forPets: true },
  { key: 'AUTOEXPLORACION', label: 'Autoexploración', slug: 'autoexploracion', description: 'Registro de autoexploraciones', forPets: false },
]

export const moduleOf = (key: ModuleKey): ExportModule => MODULES.find((m) => m.key === key)!

/** Etiqueta adaptada al tipo de perfil. */
export function moduleLabel(m: ExportModule, pet: boolean): string {
  if (!pet) return m.label
  return m.key === 'PERFIL' ? 'Ficha' : m.key === 'CITAS' ? 'Citas con el veterinario' : m.key === 'VACUNAS' ? 'Vacunas y desparasitación' : m.label
}

export function moduleDescription(m: ExportModule, pet: boolean): string {
  if (!pet) return m.description
  return m.key === 'PERFIL' ? 'Especie, raza, microchip y veterinario'
    : m.key === 'MEDICIONES' ? 'Peso, temperatura y demás'
    : m.key === 'VACUNAS' ? 'Vacunas, desparasitaciones y próximas fechas'
    : m.description
}

/** Módulos que tienen sentido para el tipo de perfil. */
export const modulesFor = (pet: boolean): ExportModule[] => (pet ? MODULES.filter((m) => m.forPets) : MODULES)

/** Formatos de salida. */
export type ExportFormat = 'XLSX' | 'PRINT' | 'CSV' | 'JSON'
export const FORMATS: readonly (readonly [ExportFormat, string])[] = [['XLSX', 'Excel'], ['PRINT', 'PDF / imprimir'], ['CSV', 'CSV'], ['JSON', 'JSON']]
export const FORMAT_DESCRIPTION: Record<ExportFormat, string> = {
  XLSX: 'Hoja de cálculo (.xlsx) para Excel, Numbers o Google Sheets.',
  PRINT: 'Reporte limpio para imprimir o guardar como PDF y enviarlo a tu médico.',
  CSV: 'Texto separado por comas, compatible con casi todo.',
  JSON: 'Formato abierto para otros programas (no sirve para restaurar; para eso usa el respaldo).',
}

/** Qué exportar. fromDay / toDay son límites inclusivos en epochDay; null = sin límite. */
export interface ExportOptions {
  modules: ModuleKey[]
  fromDay: number | null
  toDay: number | null
}

export const ALL_OPTIONS: ExportOptions = { modules: MODULES.map((m) => m.key), fromDay: null, toDay: null }
export const CYCLE_ONLY: ExportOptions = { modules: ['CICLO'], fromDay: null, toDay: null }

const inRange = (o: ExportOptions, day: number): boolean => (o.fromDay === null || day >= o.fromDay) && (o.toDay === null || day <= o.toDay)

/** Las mismas opciones sin los módulos que no aplican al tipo de perfil. */
export function optionsForProfile(o: ExportOptions, pet: boolean): ExportOptions {
  return pet ? { ...o, modules: o.modules.filter((k) => moduleOf(k).forPets) } : o
}

/** Datos ya cargados y filtrados, listos para escribirse en cualquier formato. */
export interface ExportData {
  options: ExportOptions
  generatedAt: number
  today: number
  /** Ficha completa si se pidió PERFIL; si no, solo nombre, tipo y especie (para encabezados). */
  profile: Profile | null
  symptoms: SymptomEntry[]
  measurements: Measurement[]
  dailyLogs: DailyLog[]
  studies: Study[]
  studyResults: StudyResult[]
  /** Solo los datos de los adjuntos (sin su contenido). */
  studyAttachments: { studyId: number; displayName: string }[]
  /** Días del ciclo dentro del rango. */
  cycleDays: CycleDay[]
  /** Análisis hecho con TODO el historial. */
  cycleAnalysis: CycleAnalysis | null
  /** Ciclos derivados cuyo inicio cae en el rango; index + 1 = número consecutivo. */
  cycles: { index: number; value: CycleRecord }[]
  medications: Medication[]
  medIntakes: MedIntake[]
  appointments: Appointment[]
  selfExams: SelfExam[]
  vaccinations: Vaccination[]
}

export const has = (d: ExportData, key: ModuleKey): boolean => d.options.modules.includes(key)
export const dataIsPet = (d: ExportData): boolean => d.profile?.kind === 'pet'
export const dataProfileName = (d: ExportData): string => d.profile?.name?.trim() ?? ''

const blankProfile = JSON.stringify({ ...emptyProfile(), kind: 'person', avatarColor: 0 })

/** No hay ningún registro que exportar (la ficha sola no cuenta si está en blanco). */
export function dataIsEmpty(d: ExportData): boolean {
  const profileFilled = has(d, 'PERFIL') && d.profile !== null && JSON.stringify({ ...emptyProfile(), ...d.profile, kind: 'person', avatarColor: 0 }) !== blankProfile
  return d.symptoms.length === 0 && d.measurements.length === 0 && d.dailyLogs.length === 0 && d.studies.length === 0 && d.cycleDays.length === 0 &&
    d.medications.length === 0 && d.appointments.length === 0 && d.selfExams.length === 0 && d.vaccinations.length === 0 && !profileFilled
}

/** Lee de la base del perfil lo que pidan las opciones. */
export async function loadExportData(db: SaviaDB, options: ExportOptions, today: number = todayFn()): Promise<ExportData> {
  const profile = (await db.profile.get(1)) ?? null
  // En una mascota no se exportan los módulos que no le aplican (ciclo, bienestar, autoexploración).
  const o = optionsForProfile(options, profile?.kind === 'pet')
  const on = (k: ModuleKey) => o.modules.includes(k)
  const symptoms = on('SINTOMAS') ? (await db.symptoms.toArray()).filter((s) => inRange(o, millisToEpochDay(s.timestamp))) : []
  const measurements = on('MEDICIONES') ? (await db.measurements.toArray()).filter((m) => inRange(o, millisToEpochDay(m.timestamp))) : []
  const dailyLogs = on('BIENESTAR') ? (await db.dailyLogs.toArray()).filter((d) => inRange(o, d.epochDay)) : []

  const studies = on('ESTUDIOS') ? (await db.studies.toArray()).filter((s) => inRange(o, s.epochDay)) : []
  const studyIds = new Set(studies.map((s) => s.id))
  const studyResults = studies.length ? (await db.studyResults.toArray()).filter((r) => studyIds.has(r.studyId)) : []
  const studyAttachments: { studyId: number; displayName: string }[] = []
  if (studies.length) await db.studyAttachments.each((a) => { if (studyIds.has(a.studyId)) studyAttachments.push({ studyId: a.studyId, displayName: a.displayName }) })

  let cycleDays: CycleDay[] = []
  let cycleAnalysis: CycleAnalysis | null = null
  let cycles: { index: number; value: CycleRecord }[] = []
  if (on('CICLO')) {
    const all = await db.cycleDays.toArray()
    cycleDays = all.filter((d) => inRange(o, d.epochDay))
    cycleAnalysis = analyzeCycle(all, await getPref(db, 'cycleLength'), await getPref(db, 'periodLength'), await getPref(db, 'lutealLength'), today)
    cycles = cycleAnalysis.cycles.map((value, index) => ({ index, value })).filter((c) => inRange(o, c.value.start))
  }

  let medications: Medication[] = []
  let medIntakes: MedIntake[] = []
  if (on('MEDICAMENTOS')) {
    // Un tratamiento entra si se traslapa con el rango pedido.
    medications = (await db.medications.toArray()).filter((m) =>
      (o.toDay === null || m.startEpochDay <= o.toDay) && (o.fromDay === null || m.endEpochDay === null || m.endEpochDay >= o.fromDay))
    const ids = new Set(medications.map((m) => m.id))
    medIntakes = (await db.medIntakes.toArray()).filter((i) => ids.has(i.medicationId) && inRange(o, i.epochDay))
  }
  const appointments = on('CITAS') ? (await db.appointments.toArray()).filter((a) => inRange(o, millisToEpochDay(a.timestamp))) : []
  const selfExams = on('AUTOEXPLORACION') ? (await db.selfExams.toArray()).filter((e) => inRange(o, e.epochDay)) : []
  // Una vacuna entra si su aplicación o su próxima fecha caen en el rango.
  const vaccinations = on('VACUNAS')
    ? (await db.vaccinations.toArray()).filter((v) => inRange(o, v.epochDay) || (v.nextDueEpochDay !== null && inRange(o, v.nextDueEpochDay)))
    : []

  return {
    options: o, generatedAt: Date.now(), today,
    profile: on('PERFIL') || !profile ? profile : { ...emptyProfile(), name: profile.name, kind: profile.kind, species: profile.species },
    symptoms, measurements, dailyLogs, studies, studyResults, studyAttachments, cycleDays, cycleAnalysis, cycles, medications, medIntakes,
    appointments, selfExams, vaccinations,
  }
}
