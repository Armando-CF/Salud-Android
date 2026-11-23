import 'fake-indexeddb/auto'
import { beforeEach, describe, expect, it } from 'vitest'
import { dbFor, meta } from '../../data/db'
import { getSetting, setSetting } from '../../data/prefs'
import { allProfiles, profileIds } from '../../data/profiles'
import {
  backupFileName, collectArchive, getSafetyInfo, importAsNewProfiles, importIntoProfile, importReplaceAll, openBackupBytes,
  parseBackupJson, serializeWebBackup, summarize, toWebJson, undoLastReplace, WEB_FORMAT,
} from './backup'
import { BackupError, readTables, recordCount } from './model'
import { bytes, cyc, resetAll, seedFamily, snapshot } from './testkit'

const enc = (s: string) => new TextEncoder().encode(s)

beforeEach(async () => {
  await resetAll()
})

describe('respaldo .json', () => {
  it('ida y vuelta completa: exportar → borrar todo → importar → todo regresa igual', async () => {
    const { kid, dog } = await seedFamily()
    await setSetting('themeMode', 'dark')
    await setSetting('pinHash', 'HASH-SECRETO')
    await setSetting('pinSalt', 'SAL-SECRETA')
    await meta.kv.put({ key: 'activeProfile', value: dog })
    const before = [await snapshot(1), await snapshot(kid), await snapshot(dog)]

    const archive = await collectArchive('all')
    const text = serializeWebBackup(archive)
    const json = JSON.parse(text)
    expect(json.format).toBe(WEB_FORMAT)
    expect(json.formatVersion).toBe(1)
    expect(json.app).toBe('Savia Web')
    expect(json.activeProfileId).toBe(dog)
    expect(json.globalPrefs).toEqual({ themeMode: 'dark' })
    expect(json.profiles.map((p: { name: string; kind: string; species: string }) => [p.name, p.kind, p.species])).toEqual([['Alex', 'person', ''], ['Leo', 'person', ''], ['Firulais', 'pet', 'perro']])
    // Mismos nombres de tabla que data.json de Android.
    expect(Object.keys(json.profiles[0].tables)).toEqual(['profile', 'symptoms', 'measurements', 'daily_logs', 'studies', 'study_results', 'study_attachments', 'cycle_days', 'medications', 'med_intakes', 'appointments', 'self_exams', 'vaccinations'])
    expect(json.profiles[0].tables.profile[0].id).toBeUndefined()
    expect(typeof json.profiles[0].tables.study_attachments[0].dataBase64).toBe('string')
    // El PIN nunca viaja en el respaldo.
    expect(text).not.toContain('HASH-SECRETO')
    expect(text).not.toContain('SAL-SECRETA')
    expect(text).not.toContain('pinHash')

    // Dispositivo nuevo / datos borrados.
    await resetAll()
    expect(await profileIds()).toEqual([1])
    expect(recordCount(await readTables(dbFor(1)))).toBe(0)
    expect(await getSetting('themeMode')).toBe('system')

    const parsed = openBackupBytes(enc(text))
    expect(parsed.kind).toBe('all')
    const sum = summarize(parsed)
    expect(sum.profiles.length).toBe(3)
    expect(sum.attachments).toBe(2)
    expect(sum.profiles.every((p) => p.attachmentsMissing === 0)).toBe(true)
    const r = await importReplaceAll(parsed)
    expect(r.profiles).toBe(3)

    const ids = await profileIds()
    expect(ids.length).toBe(3)
    const after = [await snapshot(ids[0]), await snapshot(ids[1]), await snapshot(ids[2])]
    expect(after).toEqual(before)
    expect((await allProfiles()).map((p) => p.name)).toEqual(['Alex', 'Leo', 'Firulais'])
    expect(await getSetting('themeMode')).toBe('dark')
    // El perfil activo del respaldo vuelve a ser el activo (con su id nuevo).
    expect((await meta.kv.get('activeProfile'))?.value).toBe(ids[2])
    // No se restaura ningún bloqueo.
    expect(await getSetting('pinHash')).toBe('')
  })

  it('agregar como perfiles nuevos no toca lo existente', async () => {
    await seedFamily()
    const text = serializeWebBackup(await collectArchive('all'))
    await resetAll()
    await dbFor(1).profile.put({ ...(await dbFor(1).profile.get(1))!, name: 'Otra persona' })
    await dbFor(1).symptoms.add({ timestamp: 5, name: 'Tos', intensity: 2, regionId: null, regionLabel: null, durationMin: null, tags: '', notes: '' })
    const mine = await snapshot(1)

    const r = await importAsNewProfiles(parseBackupJson(text))
    expect(r.mode).toBe('add')
    expect(r.newProfileIds.length).toBe(3)
    expect(await snapshot(1)).toEqual(mine)
    expect((await allProfiles()).map((p) => p.name)).toEqual(['Otra persona', 'Alex', 'Leo', 'Firulais'])
    const alex = dbFor(r.newProfileIds[0])
    expect(await alex.symptoms.count()).toBe(2)
    expect((await alex.prefs.get('cycleEnabled'))?.value).toBe(true)
    expect([...new Uint8Array((await alex.studyAttachments.toArray())[0].data)]).toEqual([37, 80, 68, 70, 0, 255, 1, 2, 3])
    expect(await getSafetyInfo()).toBeNull()
  })

  it('reemplazar todo guarda una copia interna y se puede deshacer', async () => {
    const { kid, dog } = await seedFamily()
    const stateA = [await snapshot(1), await snapshot(kid), await snapshot(dog)]
    // Respaldo B: un solo perfil distinto.
    const b = parseBackupJson(JSON.stringify({
      format: WEB_FORMAT, formatVersion: 1, scope: 'all', createdAtMillis: 123, activeProfileId: 1, globalPrefs: { themeMode: 'light' },
      profiles: [{ id: 1, prefs: { cycleEnabled: false }, tables: { profile: [{ name: 'Bea', sex: 'F' }], symptoms: [{ id: 7, timestamp: 9, name: 'Mareo', intensity: 3 }] } }],
    }))
    const r = await importReplaceAll(b)
    expect(r.profiles).toBe(1)
    expect(await profileIds()).toEqual([1])
    expect((await dbFor(1).profile.get(1))?.name).toBe('Bea')
    expect((await dbFor(1).symptoms.toArray()).map((s) => [s.id, s.name])).toEqual([[7, 'Mareo']])
    expect(await dbFor(1).cycleDays.count()).toBe(0)
    expect((await dbFor(1).prefs.get('cycleEnabled'))?.value).toBe(false)

    const safety = await getSafetyInfo()
    expect(safety?.scope).toBe('all')
    expect(safety?.profiles).toBe(3)

    const undo = await undoLastReplace()
    expect(undo.profiles).toBe(3)
    const ids = await profileIds()
    expect([await snapshot(ids[0]), await snapshot(ids[1]), await snapshot(ids[2])]).toEqual(stateA)
    expect(await getSafetyInfo()).toBeNull()
    await expect(undoLastReplace()).rejects.toThrow(BackupError)
  })

  it('rechaza archivos inválidos con mensajes claros y sin tocar nada', async () => {
    await seedFamily()
    const before = await snapshot(1)
    const bad = (text: string) => { try { openBackupBytes(enc(text)); return 'sin error' } catch (e) { expect(e).toBeInstanceOf(BackupError); return (e as Error).message } }
    expect(bad('esto no es json {')).toMatch(/dañado o no es un respaldo/)
    expect(bad('[1,2,3]')).toMatch(/no es un respaldo de Savia/)
    expect(bad('{"format":"otra-cosa","formatVersion":1}')).toMatch(/no es un respaldo de Savia/)
    expect(bad(JSON.stringify({ format: WEB_FORMAT, formatVersion: 99, profiles: [] }))).toMatch(/versión más nueva/)
    expect(bad(JSON.stringify({ format: WEB_FORMAT, formatVersion: 1, profiles: [] }))).toMatch(/no contiene datos/)
    expect(bad(JSON.stringify({ format: WEB_FORMAT, formatVersion: 1, profiles: [{ id: 1 }] }))).toMatch(/no contiene datos/)
    const good = serializeWebBackup(await collectArchive('all'))
    expect(bad(good.slice(0, good.length - 40))).toMatch(/dañado/)
    expect(() => openBackupBytes(new Uint8Array(0))).toThrow(/vacío/)
    expect(await snapshot(1)).toEqual(before)
    expect((await profileIds()).length).toBe(3)
    expect(await getSafetyInfo()).toBeNull()
  })

  it('tolera campos faltantes o desconocidos y BOM', async () => {
    const a = parseBackupJson('﻿' + JSON.stringify({
      format: WEB_FORMAT, formatVersion: 1, campoNuevo: true,
      profiles: [{ tables: { profile: [{ name: 'Zoe', kind: 'alien', campoRaro: 1 }], cycle_days: [{ epochDay: 100, flow: 3, otro: 'x' }], tabla_nueva: [{}] }, prefs: { cycleEnabled: true, cycle_length: 31, desconocido: 5, periodLength: 'no-numero' } }],
    }))
    expect(a.kind).toBe('profile')
    const p = a.profiles[0]
    expect(p.tables.profile?.kind).toBe('person')
    expect(p.tables.cycleDays[0]).toEqual(cyc(100, { flow: 3 }))
    expect(p.prefs).toEqual({ cycleEnabled: true, cycleLength: 31 })
  })

  it('respaldo sin archivos adjuntos conserva sus datos pero no su contenido', async () => {
    await seedFamily()
    const json = toWebJson(await collectArchive('all', false), false) as { attachmentsIncluded: boolean; profiles: { tables: { study_attachments: Record<string, unknown>[] } }[] }
    expect(json.attachmentsIncluded).toBe(false)
    expect(json.profiles[0].tables.study_attachments[0].dataBase64).toBeUndefined()
    const parsed = parseBackupJson(JSON.stringify(json))
    expect(summarize(parsed).profiles[0].attachmentsMissing).toBe(1)
    const r = await importAsNewProfiles(parsed)
    const att = await dbFor(r.newProfileIds[0]).studyAttachments.toArray()
    expect(att.length).toBe(1)
    expect(att[0].displayName).toBe('resultados.pdf')
    expect(att[0].data.byteLength).toBe(0)
  })

  it('nombres de archivo', () => {
    expect(backupFileName(20780)).toBe('savia-respaldo-2026-11-23.json')
    expect(backupFileName(20780, 'Firulais García')).toBe('savia-respaldo-firulais-garcia-2026-11-23.json')
  })
})

describe('respaldo de un solo perfil', () => {
  it('se exporta como "profile" y puede entrar como perfil nuevo', async () => {
    const { dog } = await seedFamily()
    const a = parseBackupJson(serializeWebBackup(await collectArchive(dog)))
    expect(a.kind).toBe('profile')
    expect(a.profiles.length).toBe(1)
    const r = await importAsNewProfiles(a)
    const p = await dbFor(r.newProfileIds[0]).profile.get(1)
    expect([p?.name, p?.kind, p?.species, p?.sterilized]).toEqual(['Firulais', 'pet', 'perro', true])
    expect((await dbFor(r.newProfileIds[0]).prefs.get('atlasSex'))?.value).toBe('M')
  })

  it('combinar: omite duplicados, gana el respaldo en tablas por día y remapea llaves', async () => {
    await seedFamily()
    const backup = parseBackupJson(serializeWebBackup(await collectArchive(1))).profiles[0]
    // Cambios locales después del respaldo.
    const db = dbFor(1)
    await db.symptoms.add({ timestamp: 77, name: 'Nuevo local', intensity: 1, regionId: null, regionLabel: null, durationMin: null, tags: '', notes: '' })
    await db.cycleDays.put(cyc(19660, { pregnancyTest: 2, notes: 'editado local' }))
    const localCounts = { symptoms: await db.symptoms.count(), studies: await db.studies.count(), results: await db.studyResults.count(), meds: await db.medications.count(), intakes: await db.medIntakes.count(), att: await db.studyAttachments.count() }
    // El respaldo además trae cosas que aquí no existen.
    backup.tables.symptoms.push({ id: 900, timestamp: 88, name: 'Solo en respaldo', intensity: 2, regionId: null, regionLabel: null, durationMin: null, tags: '', notes: '' })
    backup.tables.studies.push({ id: 500, epochDay: 19000, title: 'Ultrasonido', category: 'imagen', place: '', doctor: '', status: '', summary: '', followUpEpochDay: null })
    backup.tables.studyResults.push({ id: 501, studyId: 500, name: 'Hallazgo', value: null, textValue: 'Normal', unit: '', refMin: null, refMax: null, notes: '' })
    backup.tables.studyAttachments.push({ id: 502, studyId: 500, fileName: 'us.png', displayName: 'us.png', mime: 'image/png', data: bytes(1, 2, 3) })
    backup.tables.medications.push({ id: 600, name: 'Ibuprofeno', dose: '400 mg', instructions: '', times: '', daysMask: 127, startEpochDay: 19000, endEpochDay: null, active: false, remind: false, notes: '' })
    backup.tables.medIntakes.push({ id: 601, medicationId: 600, epochDay: 19001, slot: '', timestamp: 99, status: 'taken' })

    const r = await importIntoProfile(backup, 1, 'merge')
    expect(r.mode).toBe('merge')
    expect(r.updated).toBe(1) // el día 19660 del ciclo
    expect(r.added).toBe(5) // síntoma, estudio, resultado, medicamento, toma
    expect(r.attachments).toBe(1)
    expect(await db.symptoms.count()).toBe(localCounts.symptoms + 1)
    expect((await db.cycleDays.get(19660))?.notes).toBe('nota') // gana el respaldo
    expect(await db.studies.count()).toBe(localCounts.studies + 1)
    expect(await db.studyAttachments.count()).toBe(localCounts.att + 1)
    const us = (await db.studies.toArray()).find((s) => s.title === 'Ultrasonido')!
    expect(us.id).not.toBe(500)
    expect((await db.studyResults.where('studyId').equals(us.id!).toArray()).map((x) => x.name)).toEqual(['Hallazgo'])
    expect((await db.studyAttachments.where('studyId').equals(us.id!).toArray()).map((x) => x.displayName)).toEqual(['us.png'])
    const ibu = (await db.medications.toArray()).find((m) => m.name === 'Ibuprofeno')!
    expect((await db.medIntakes.where('medicationId').equals(ibu.id!).toArray()).length).toBe(1)
    expect((await db.symptoms.toArray()).some((s) => s.name === 'Nuevo local')).toBe(true)
    expect(await getSafetyInfo()).toBeNull()

    // Volver a combinar el mismo respaldo no duplica nada.
    const again = await importIntoProfile(backup, 1, 'merge')
    expect(again.added).toBe(0)
    expect(again.attachments).toBe(0)
    expect(await db.symptoms.count()).toBe(localCounts.symptoms + 1)
  })

  it('reemplazar un perfil guarda copia de ese perfil y se puede deshacer', async () => {
    const { kid } = await seedFamily()
    const before = await snapshot(kid)
    const other = await snapshot(1)
    const backup = parseBackupJson(JSON.stringify({ format: WEB_FORMAT, formatVersion: 1, scope: 'profile', profiles: [{ id: 1, prefs: {}, tables: { profile: [{ name: 'Leo nuevo', sex: 'M' }] } }] })).profiles[0]
    const r = await importIntoProfile(backup, kid, 'replace')
    expect(r.mode).toBe('replaceProfile')
    expect((await dbFor(kid).profile.get(1))?.name).toBe('Leo nuevo')
    expect(await dbFor(kid).measurements.count()).toBe(0)
    expect((await getSafetyInfo())?.scope).toBe(kid)
    await undoLastReplace()
    expect(await snapshot(kid)).toEqual(before)
    expect(await snapshot(1)).toEqual(other)
  })
})
