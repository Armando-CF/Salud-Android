// Utilidades compartidas por las pruebas del módulo de respaldo (solo se importan desde *.test.ts).

import { dbFor, meta } from '../../data/db'
import { createProfile, deleteProfile, initProfiles, profileIds } from '../../data/profiles'
import { emptyProfile, type CycleDay, type Profile } from '../../data/types'
import { readPrefs, readTables, type ProfilePrefs, type ProfileTables } from './model'

export const bytes = (...v: number[]): ArrayBuffer => new Uint8Array(v).buffer

/** Deja todo como recién instalado: un solo perfil vacío. */
export async function resetAll(): Promise<void> {
  for (const id of await profileIds()) if (id !== 1) await deleteProfile(id)
  const db = dbFor(1)
  await Promise.all(db.tables.map((t) => t.clear()))
  await meta.kv.clear()
  await meta.profiles.clear()
  await initProfiles()
}

export const cyc = (epochDay: number, o: Partial<CycleDay> = {}): CycleDay => ({
  epochDay, flow: 0, symptoms: '', moods: '', mucus: '', basalTemp: null, sex: 0, ovulationTest: 0, pregnancyTest: 0, contraceptive: false, notes: '', ...o,
})

export const prof = (o: Partial<Profile>): Profile => ({ ...emptyProfile(), ...o })

/** Siembra una familia: persona (perfil 1, con ciclo y un adjunto), hijo y perro. Devuelve los ids. */
export async function seedFamily(): Promise<{ kid: number; dog: number }> {
  const db = dbFor(1)
  await db.profile.put(prof({ name: 'Alex', sex: 'F', relation: 'Yo', birthEpochDay: 9000, heightCm: 168, bloodType: 'O+', allergies: 'Penicilina, "polvo"' }))
  await db.prefs.bulkPut([{ key: 'cycleEnabled', value: true }, { key: 'cycleLength', value: 30 }, { key: 'atlasSex', value: 'F' }])
  await db.symptoms.bulkAdd([
    { timestamp: 1_700_000_000_000, name: 'Dolor de cabeza', intensity: 6, regionId: 'cabeza', regionLabel: 'Cabeza', durationMin: 90, tags: 'Estrés,Pantallas', notes: 'Con\nsalto, coma y "comillas"' },
    { timestamp: 1_700_100_000_000, name: 'Fatiga', intensity: 4, regionId: null, regionLabel: null, durationMin: null, tags: '', notes: '' },
  ])
  await db.measurements.bulkAdd([
    { timestamp: 1_700_000_000_000, type: 'weight', value: 64.5, value2: null, tag: '', notes: '' },
    { timestamp: 1_700_050_000_000, type: 'bp', value: 118, value2: 76, tag: 'En reposo', notes: '' },
  ])
  await db.dailyLogs.put({ epochDay: 19700, mood: 4, energy: 3, stress: 2, sleepHours: 7.5, waterGlasses: 6, exerciseMin: 30, notes: 'Bien' })
  const s1 = await db.studies.add({ epochDay: 19650, title: 'Química sanguínea', category: 'lab', place: 'Lab', doctor: 'Dra. Rivera', status: 'seguimiento', summary: 'Colesterol alto', followUpEpochDay: 19830 })
  await db.studyResults.bulkAdd([
    { studyId: s1, name: 'Glucosa', value: 92, textValue: '', unit: 'mg/dL', refMin: 70, refMax: 99, notes: '' },
    { studyId: s1, name: 'Colesterol total', value: 212, textValue: '', unit: 'mg/dL', refMin: null, refMax: 200, notes: '' },
    { studyId: s1, name: 'EGO', value: null, textValue: 'Sin alteraciones', unit: '', refMin: null, refMax: null, notes: '' },
  ])
  await db.studyAttachments.add({ studyId: s1, fileName: 'resultados.pdf', displayName: 'resultados.pdf', mime: 'application/pdf', data: bytes(37, 80, 68, 70, 0, 255, 1, 2, 3) })
  for (const start of [19560, 19588, 19618, 19644]) for (let d = 0; d < 4; d++) await db.cycleDays.put(cyc(start + d, { flow: 3, symptoms: d === 0 ? 'colicos,fatiga' : '' }))
  await db.cycleDays.put(cyc(19660, { pregnancyTest: 1, sex: 2, notes: 'nota' }))
  const m1 = await db.medications.add({ name: 'Loratadina', dose: '10 mg', instructions: '', times: '08:00', daysMask: 127, startEpochDay: 19600, endEpochDay: null, active: true, remind: true, notes: '' })
  await db.medIntakes.add({ medicationId: m1, epochDay: 19700, slot: '08:00', timestamp: 1_702_000_000_000, status: 'taken' })
  await db.appointments.add({ timestamp: 1_703_000_000_000, title: 'Revisión anual', doctor: 'Dra. Rivera', specialty: '', place: '', notes: '', remind: true, done: false })
  await db.selfExams.add({ epochDay: 19690, type: 'mama', normal: true, notes: '' })
  await db.vaccinations.add({ epochDay: 19400, type: 'vacuna', name: 'Influenza', dose: 'Anual', nextDueEpochDay: 19765, place: '', lot: '', notes: '', remind: true })

  const kid = await createProfile({ name: 'Leo', sex: 'M', relation: 'Hijo', birthEpochDay: 17000, avatarColor: 2 })
  const kdb = dbFor(kid)
  await kdb.measurements.bulkAdd([
    { timestamp: 1_690_000_000_000, type: 'weight', value: 21, value2: null, tag: '', notes: '' },
    { timestamp: 1_690_000_060_000, type: 'height', value: 118, value2: null, tag: '', notes: '' },
  ])
  await kdb.vaccinations.add({ epochDay: 19000, type: 'vacuna', name: 'SRP', dose: 'Refuerzo', nextDueEpochDay: null, place: 'Centro de salud', lot: 'A1', notes: '', remind: true })

  const dog = await createProfile({ name: 'Firulais', kind: 'pet', species: 'perro', breed: 'Mestizo', sex: 'M', sterilized: true, microchip: '985', avatarColor: 5 }, { atlasSex: 'M' })
  const ddb = dbFor(dog)
  await ddb.measurements.add({ timestamp: 1_695_000_000_000, type: 'weight', value: 17.65, value2: null, tag: '', notes: '' })
  await ddb.vaccinations.add({ epochDay: 19300, type: 'desp_interna', name: 'Desparasitación interna', dose: '', nextDueEpochDay: 19390, place: '', lot: '', notes: '', remind: true })
  const ds = await ddb.studies.add({ epochDay: 19500, title: 'Radiografía', category: 'imagen', place: 'Vet', doctor: '', status: 'normal', summary: '', followUpEpochDay: null })
  await ddb.studyAttachments.add({ studyId: ds, fileName: 'rx.jpg', displayName: 'rx.jpg', mime: 'image/jpeg', data: bytes(255, 216, 255, 224, 9, 8, 7) })
  return { kid, dog }
}

export interface Snapshot { tables: ProfileTables; prefs: ProfilePrefs }

/** Contenido de un perfil en forma comparable (los adjuntos como arreglos de números). */
export async function snapshot(id: number): Promise<unknown> {
  const db = dbFor(id)
  const t = await readTables(db, true)
  return {
    prefs: await readPrefs(db),
    tables: { ...t, studyAttachments: t.studyAttachments.map((a) => ({ ...a, data: a.data ? [...new Uint8Array(a.data)] : null })) },
  }
}

/** Como [snapshot] pero sin ids (para comparar perfiles importados con id nuevo). */
export async function looseSnapshot(id: number): Promise<unknown> {
  return JSON.parse(JSON.stringify(await snapshot(id)))
}
