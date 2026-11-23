import 'fake-indexeddb/auto'
import { strFromU8, strToU8, unzipSync, zipSync } from 'fflate'
import { beforeEach, describe, expect, it } from 'vitest'
import { dbFor } from '../../data/db'
import { allProfiles, profileIds } from '../../data/profiles'
import { androidFileName, collectArchive, importAsNewProfiles, importReplaceAll, openBackupBytes, readAndroidZip, summarize, writeAndroidZip } from './backup'
import { BackupError } from './model'
import { resetAll, seedFamily, snapshot } from './testkit'

// Archivos construidos a mano siguiendo Backup.kt de Savia para Android.

/** data.json como lo escribe Android 1.0 (formato 1: sin vacunas ni campos de mascota). */
const androidV1 = {
  format: 'savia-backup', formatVersion: 1, app: 'Savia', appVersion: '1.0.0', appVersionCode: 1, createdAtMillis: 1_795_000_000_000, createdAt: '2026-11-18 10:00',
  prefs: { onboarding_done: true, theme_mode: 'dark', app_lock: true, cycle_enabled: true, cycle_length: 29, period_length: 4, luteal_length: 13, show_fertility: false, period_reminder: true, atlas_sex: 'F', self_exam_reminder: false },
  tables: {
    profile: [{ name: 'Ana', sex: 'F', birthEpochDay: 9000, heightCm: 165.0, goalWeightKg: null, bloodType: 'A+', allergies: '', conditions: '', emergencyName: '', emergencyPhone: '', doctorName: '', doctorPhone: '', insurance: '', notes: '' }],
    symptoms: [{ id: 3, timestamp: 1_794_000_000_000, name: 'Migraña', intensity: 8, regionId: null, regionLabel: null, durationMin: 120, tags: 'Estrés', notes: '' }],
    measurements: [{ id: 1, timestamp: 1_794_000_000_000, type: 'weight', value: 61.2, value2: null, tag: '', notes: '' }],
    daily_logs: [{ epochDay: 20770, mood: 4, energy: null, stress: null, sleepHours: 7.0, waterGlasses: null, exerciseMin: null, notes: '' }],
    studies: [{ id: 10, epochDay: 20700, title: 'Ultrasonido', category: 'imagen', place: '', doctor: '', status: 'normal', summary: '', followUpEpochDay: null }],
    study_results: [{ id: 4, studyId: 10, name: 'Hallazgo', value: null, textValue: 'Normal', unit: '', refMin: null, refMax: null, notes: '' }],
    study_attachments: [{ id: 2, studyId: 10, fileName: 'att_1700_foto.jpg', displayName: 'Foto del estudio.jpg', mime: 'image/jpeg' }],
    cycle_days: [{ epochDay: 20760, flow: 3, symptoms: 'colicos', moods: '', mucus: '', basalTemp: null, sex: 0, ovulationTest: 0, pregnancyTest: 1, contraceptive: false, notes: '' }],
    medications: [{ id: 5, name: 'Hierro', dose: '1 tableta', instructions: '', times: '09:00', daysMask: 127, startEpochDay: 20700, endEpochDay: null, active: true, remind: true, notes: '' }],
    med_intakes: [{ id: 9, medicationId: 5, epochDay: 20770, slot: '09:00', timestamp: 1_794_500_000_000, status: 'taken' }],
    appointments: [{ id: 1, timestamp: 1_796_000_000_000, title: 'Ginecología', doctor: '', specialty: '', place: '', notes: '', remind: true, done: false }],
    self_exams: [{ id: 1, epochDay: 20750, type: 'mama', normal: true, notes: '' }],
  },
}

const petData = {
  format: 'savia-backup', formatVersion: 2, app: 'Savia', appVersion: '1.1.0', createdAtMillis: 1_795_000_000_000,
  prefs: { cycle_enabled: false, atlas_sex: 'M' },
  tables: {
    profile: [{ name: 'Garfield', sex: 'M', kind: 'pet', species: 'gato', breed: 'Naranja', sterilized: true, microchip: '', markings: '', relation: '', avatarColor: 6 }],
    vaccinations: [{ id: 1, epochDay: 20500, type: 'vacuna', name: 'Triple felina', dose: 'Anual', nextDueEpochDay: 20865, place: 'Vet', lot: 'L9', notes: '', remind: true }],
    study_attachments: [],
  },
}

const zip = (files: Record<string, string | Uint8Array>) => zipSync(Object.fromEntries(Object.entries(files).map(([k, v]) => [k, typeof v === 'string' ? strToU8(v) : v])))
const jpg = new Uint8Array([255, 216, 255, 224, 1, 2, 3, 4])

beforeEach(async () => {
  await resetAll()
})

describe('importar respaldo de Savia para Android (.zip)', () => {
  it('respaldo de un perfil (formato de la versión 1.0) con adjuntos', async () => {
    const archive = openBackupBytes(zip({ 'data.json': JSON.stringify(androidV1), 'attachments/att_1700_foto.jpg': jpg, 'attachments/../fuera.txt': 'x' }))
    expect(archive.source).toBe('android')
    expect(archive.kind).toBe('profile')
    expect(summarize(archive).profiles[0]).toMatchObject({ name: 'Ana', kind: 'person', attachments: 1, attachmentsMissing: 0 })
    // Las claves de ajustes de Android se traducen; las globales (tema, bloqueo) no son ajustes de perfil.
    expect(archive.profiles[0].prefs).toEqual({ cycleEnabled: true, cycleLength: 29, periodLength: 4, lutealLength: 13, showFertility: false, atlasSex: 'F' })

    const r = await importAsNewProfiles(archive)
    const db = dbFor(r.newProfileIds[0])
    const p = await db.profile.get(1)
    expect([p?.name, p?.kind, p?.bloodType, p?.sterilized, p?.avatarColor]).toEqual(['Ana', 'person', 'A+', null, 0])
    expect((await db.symptoms.toArray())[0]).toMatchObject({ id: 3, name: 'Migraña', intensity: 8, durationMin: 120 })
    expect((await db.cycleDays.get(20760))?.pregnancyTest).toBe(1)
    expect((await db.medIntakes.toArray())[0].medicationId).toBe(5)
    expect(await db.vaccinations.count()).toBe(0)
    const att = (await db.studyAttachments.toArray())[0]
    expect([att.studyId, att.displayName, att.mime]).toEqual([10, 'Foto del estudio.jpg', 'image/jpeg'])
    expect([...new Uint8Array(att.data)]).toEqual([...jpg])
    expect((await db.prefs.get('cycleLength'))?.value).toBe(29)
  })

  it('respaldo completo (perfiles.json + perfiles/<id>/…)', async () => {
    const manifest = { format: 'savia-backup-all', formatVersion: 1, app: 'Savia', appVersion: '1.1.0', createdAtMillis: 1_795_000_000_000, activeProfileId: 3, globalPrefs: { theme_mode: 'dark' }, profiles: [{ id: 1, name: 'Ana' }, { id: 3, name: 'Garfield' }] }
    const archive = readAndroidZip(zip({
      'perfiles.json': JSON.stringify(manifest),
      'perfiles/1/data.json': JSON.stringify({ ...androidV1, formatVersion: 2 }),
      'perfiles/1/attachments/att_1700_foto.jpg': jpg,
      'perfiles/3/data.json': JSON.stringify(petData),
    }))
    expect(archive.kind).toBe('all')
    expect(archive.profiles.map((p) => p.originalId)).toEqual([1, 3])
    expect(archive.globalPrefs).toEqual({ themeMode: 'dark' })
    const r = await importReplaceAll(archive)
    expect(r.profiles).toBe(2)
    const all = await allProfiles()
    expect(all.map((p) => [p.name, p.kind, p.species])).toEqual([['Ana', 'person', ''], ['Garfield', 'pet', 'gato']])
    const cat = dbFor((await profileIds())[1])
    expect((await cat.vaccinations.toArray())[0]).toMatchObject({ name: 'Triple felina', nextDueEpochDay: 20865, lot: 'L9' })
    expect((await dbFor(1).studyAttachments.toArray())[0].data.byteLength).toBe(jpg.length)
  })

  it('errores claros', () => {
    const msg = (bytes: Uint8Array) => { try { openBackupBytes(bytes); return 'sin error' } catch (e) { expect(e).toBeInstanceOf(BackupError); return (e as Error).message } }
    expect(msg(zip({ 'otra.txt': 'hola' }))).toMatch(/no es un respaldo de Savia/)
    expect(msg(zip({ 'data.json': JSON.stringify({ ...androidV1, formatVersion: 3 }) }))).toMatch(/versión más nueva/)
    expect(msg(zip({ 'data.json': JSON.stringify({ format: 'savia-backup', formatVersion: 2 }) }))).toMatch(/no contiene datos/)
    expect(msg(zip({ 'data.json': '{roto' }))).toMatch(/dañado/)
    expect(msg(new Uint8Array([0x50, 0x4b, 1, 2, 3, 4]))).toMatch(/\.zip/)
    expect(msg(zip({ 'perfiles.json': JSON.stringify({ format: 'savia-backup-all', formatVersion: 1 }) }))).toMatch(/no contiene datos/)
  })
})

describe('exportar para Savia Android (.zip)', () => {
  it('escribe exactamente el formato que Android restaura', async () => {
    await seedFamily()
    const archive = await collectArchive(1)
    const bytes = writeAndroidZip(archive.profiles[0], 1_795_000_000_000)
    const entries = unzipSync(bytes)
    expect(Object.keys(entries).sort()).toEqual(['attachments/resultados.pdf', 'data.json'])
    const data = JSON.parse(strFromU8(entries['data.json']))
    // Encabezado que valida Backup.parse()
    expect(data.format).toBe('savia-backup')
    expect(data.formatVersion).toBe(2)
    expect(typeof data.createdAtMillis).toBe('number')
    // Ajustes con las claves de SharedPreferences de Android
    expect(data.prefs).toMatchObject({ cycle_enabled: true, cycle_length: 30, period_length: 5, luteal_length: 14, show_fertility: true, atlas_sex: 'F' })
    expect(Object.keys(data.prefs).some((k) => /[A-Z]/.test(k))).toBe(false)
    // Tablas y campos como en toJson()
    expect(Object.keys(data.tables)).toEqual(['profile', 'symptoms', 'measurements', 'daily_logs', 'studies', 'study_results', 'study_attachments', 'cycle_days', 'medications', 'med_intakes', 'appointments', 'self_exams', 'vaccinations'])
    expect(Object.keys(data.tables.profile[0])).toEqual(['name', 'sex', 'birthEpochDay', 'heightCm', 'goalWeightKg', 'bloodType', 'allergies', 'conditions', 'emergencyName', 'emergencyPhone', 'doctorName', 'doctorPhone', 'insurance', 'notes', 'kind', 'species', 'breed', 'sterilized', 'microchip', 'markings', 'relation', 'avatarColor'])
    expect(Object.keys(data.tables.symptoms[0])).toEqual(['id', 'timestamp', 'name', 'intensity', 'regionId', 'regionLabel', 'durationMin', 'tags', 'notes'])
    expect(Object.keys(data.tables.cycle_days[0])).toEqual(['epochDay', 'flow', 'symptoms', 'moods', 'mucus', 'basalTemp', 'sex', 'ovulationTest', 'pregnancyTest', 'contraceptive', 'notes'])
    expect(Object.keys(data.tables.vaccinations[0])).toEqual(['id', 'epochDay', 'type', 'name', 'dose', 'nextDueEpochDay', 'place', 'lot', 'notes', 'remind'])
    // Los adjuntos van como archivos aparte, no dentro del JSON.
    const stored = (await dbFor(1).studyAttachments.toArray())[0]
    expect(data.tables.study_attachments[0]).toEqual({ id: stored.id, studyId: stored.studyId, fileName: 'resultados.pdf', displayName: 'resultados.pdf', mime: 'application/pdf' })
    expect([...entries['attachments/resultados.pdf']]).toEqual([37, 80, 68, 70, 0, 255, 1, 2, 3])
    expect(androidFileName('Alex', 20780)).toBe('savia-android-alex-2026-11-23.zip')
  })

  it('nombres de adjunto únicos y seguros; ida y vuelta web → Android → web', async () => {
    await seedFamily()
    const db = dbFor(1)
    await db.studyAttachments.bulkAdd([
      { studyId: 1, fileName: 'resultados.pdf', displayName: 'resultados.pdf', mime: 'application/pdf', data: new Uint8Array([9, 9]).buffer },
      { studyId: 1, fileName: 'Foto con espacios/y diagonal.JPG', displayName: 'Foto con espacios/y diagonal.JPG', mime: 'image/jpeg', data: new Uint8Array([7]).buffer },
    ])
    const before = await snapshot(1)
    const bytes = writeAndroidZip((await collectArchive(1)).profiles[0])
    const names = Object.keys(unzipSync(bytes)).filter((n) => n.startsWith('attachments/'))
    expect(names.length).toBe(3)
    expect(new Set(names).size).toBe(3)
    expect(names.every((n) => /^attachments\/[\w.-]+$/.test(n))).toBe(true)

    await resetAll()
    const back = readAndroidZip(bytes)
    const r = await importAsNewProfiles(back)
    const after = (await snapshot(r.newProfileIds[0])) as { tables: { studyAttachments: { fileName: string }[] }; prefs: unknown }
    const strip = (s: unknown) => { const c = JSON.parse(JSON.stringify(s)); for (const a of c.tables.studyAttachments) delete a.fileName; return c }
    expect(strip(after)).toEqual(strip(before))
  })
})
