import Dexie, { type Table } from 'dexie'
import type {
  Appointment, CycleDay, DailyLog, MedIntake, Measurement, Medication, Profile, SelfExam, Study, StudyAttachment,
  StudyResult, SymptomEntry, Vaccination,
} from './types'

/**
 * Base de datos de UN perfil (IndexedDB). Cada perfil —persona o mascota— tiene la suya ("savia-p<N>"),
 * así los historiales nunca se mezclan. Todo vive en el dispositivo; la app no usa servidores.
 *
 * IMPORTANTE para el futuro: al cambiar el esquema, añade una versión nueva con `this.version(n).stores(...)`
 * (y `.upgrade(...)` si hay que transformar datos). Nunca borres versiones anteriores.
 */
export class SaviaDB extends Dexie {
  profile!: Table<Profile, number>
  symptoms!: Table<SymptomEntry, number>
  measurements!: Table<Measurement, number>
  dailyLogs!: Table<DailyLog, number>
  studies!: Table<Study, number>
  studyResults!: Table<StudyResult, number>
  studyAttachments!: Table<StudyAttachment, number>
  cycleDays!: Table<CycleDay, number>
  medications!: Table<Medication, number>
  medIntakes!: Table<MedIntake, number>
  appointments!: Table<Appointment, number>
  selfExams!: Table<SelfExam, number>
  vaccinations!: Table<Vaccination, number>
  /** Ajustes propios del perfil (ciclo, atlas…). */
  prefs!: Table<{ key: string; value: unknown }, string>

  constructor(name: string) {
    super(name)
    this.version(1).stores({
      profile: 'id',
      symptoms: '++id, timestamp',
      measurements: '++id, type, timestamp, [type+timestamp]',
      dailyLogs: 'epochDay',
      studies: '++id, epochDay',
      studyResults: '++id, studyId, name',
      studyAttachments: '++id, studyId',
      cycleDays: 'epochDay',
      medications: '++id',
      medIntakes: '++id, medicationId, epochDay',
      appointments: '++id, timestamp',
      selfExams: '++id, epochDay',
      vaccinations: '++id, epochDay',
      prefs: 'key',
    })
  }
}

/** Tablas de datos de un perfil (todas menos `prefs`), en el orden en que se respaldan. */
export const DATA_TABLES = [
  'profile', 'symptoms', 'measurements', 'dailyLogs', 'studies', 'studyResults', 'studyAttachments', 'cycleDays',
  'medications', 'medIntakes', 'appointments', 'selfExams', 'vaccinations',
] as const
export type DataTable = (typeof DATA_TABLES)[number]

/** Base "meta": lista de perfiles y ajustes globales (tema, bloqueo, bienvenida, último respaldo). */
export class MetaDB extends Dexie {
  profiles!: Table<{ id: number; createdAt: number }, number>
  kv!: Table<{ key: string; value: unknown }, string>

  constructor() {
    super('savia-meta')
    this.version(1).stores({ profiles: 'id', kv: 'key' })
  }
}

export const meta = new MetaDB()

const cache = new Map<number, SaviaDB>()

export const dbName = (id: number): string => `savia-p${id}`

/** Base de datos de un perfil (se abre una sola vez y se reutiliza). */
export function dbFor(id: number): SaviaDB {
  let db = cache.get(id)
  if (!db) {
    db = new SaviaDB(dbName(id))
    cache.set(id, db)
  }
  return db
}

/** Cierra y olvida la base de un perfil (al eliminarlo). */
export async function dropDb(id: number): Promise<void> {
  const db = cache.get(id)
  if (db) { db.close(); cache.delete(id) }
  await Dexie.delete(dbName(id))
}
