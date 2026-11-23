import 'fake-indexeddb/auto'
import { describe, expect, it } from 'vitest'
import { dbFor, meta } from '../../data/db'
import { createProfile, initProfiles, profileIds, switchProfile } from '../../data/profiles'
import { getPref } from '../../data/prefs'
import { emptyProfile } from '../../data/types'
import { ymdToEpochDay } from '../../lib/dates'
import {
  ageText, birthFromAge, bytesText, cleanPhone, goalError, hashPin, heightError, newSalt, pinLengthOf, profileLine, profileTitle,
  removeProfile, sha256Fallback, sha256Hex, sterilizedKey, sterilizedValue, suggestCycle, validPin, verifyPin, wipeProfileData,
} from './logic'

const D = ymdToEpochDay

describe('edad', () => {
  const t = D(2026, 11, 23)
  it('en palabras', () => {
    expect(ageText(D(2026, 11, 22), t)).toBe('1 día')
    expect(ageText(D(2026, 11, 13), t)).toBe('10 días')
    expect(ageText(D(2026, 11, 2), t)).toBe('3 semanas')
    expect(ageText(D(2026, 3, 20), t)).toBe('8 meses')
    expect(ageText(D(2024, 7, 10), t)).toBe('2 años y 4 meses')
    expect(ageText(D(1992, 5, 1), t)).toBe('34 años')
    expect(ageText(D(2022, 9, 20), t, true)).toBe('4 años y 2 meses')
    expect(ageText(D(2022, 9, 20), t)).toBe('4 años')
    expect(ageText(D(2025, 11, 23), t)).toBe('1 año')
    expect(ageText(D(2027, 1, 1), t)).toBe('')
  })
  it('nacimiento aproximado a partir de la edad', () => {
    expect(birthFromAge(3, 2, t)).toBe(D(2023, 9, 23))
    expect(ageText(birthFromAge(3, 2, t), t, true)).toBe('3 años y 2 meses')
    expect(birthFromAge(0, 0, t)).toBe(t)
    // 31 de marzo menos un mes → último día de febrero
    expect(birthFromAge(0, 1, D(2026, 3, 31))).toBe(D(2026, 2, 28))
  })
  it('subtítulo de perfil', () => {
    const base = { id: 2, name: 'Sofía', sex: 'F', avatarColor: 0 }
    expect(profileLine({ ...base, kind: 'person', species: '', relation: 'Hija', birthEpochDay: D(2018, 6, 1) }, t)).toBe('Hija · 8 años')
    expect(profileLine({ ...base, kind: 'pet', species: 'perro', relation: '', birthEpochDay: D(2023, 9, 20) }, t)).toBe('Perro · 3 años y 2 meses')
    expect(profileLine({ ...base, kind: 'pet', species: 'gato', relation: '', birthEpochDay: null }, t)).toBe('Gato')
    expect(profileLine({ ...base, kind: 'person', species: '', relation: '', birthEpochDay: null }, t)).toBe('')
  })
})

describe('formularios', () => {
  it('validaciones', () => {
    expect(heightError('')).toBe(false)
    expect(heightError('168')).toBe(false)
    expect(heightError('1,68')).toBe(true)
    expect(heightError('abc')).toBe(true)
    expect(goalError('', false)).toBe(false)
    expect(goalError('64,5', false)).toBe(false)
    expect(goalError('5', false)).toBe(true)
    expect(goalError('0.35', true)).toBe(false)
    expect(cleanPhone('55 1234-5678 ext. 2')).toBe('55 1234-5678  2')
  })
  it('esterilizado, ciclo y títulos', () => {
    expect(sterilizedValue(sterilizedKey(true))).toBe(true)
    expect(sterilizedValue(sterilizedKey(false))).toBe(false)
    expect(sterilizedValue(sterilizedKey(null))).toBeNull()
    const t = D(2026, 11, 23)
    expect(suggestCycle('F', null, t)).toBe(true)
    expect(suggestCycle('F', D(2020, 1, 1), t)).toBe(false)
    expect(suggestCycle('F', D(2010, 1, 1), t)).toBe(true)
    expect(suggestCycle('M', null, t)).toBe(false)
    expect(suggestCycle(null, null, t)).toBe(false)
    expect(profileTitle(true, 3, 'Firulais')).toBe('Ficha de Firulais')
    expect(profileTitle(true, 3, '')).toBe('Ficha de tu mascota')
    expect(profileTitle(false, 1, 'Alex')).toBe('Mi ficha')
    expect(profileTitle(false, 2, 'Leo')).toBe('Ficha de Leo')
    expect(bytesText(1536)).toBe('1.5 KB')
    expect(bytesText(5 * 1024 * 1024)).toBe('5 MB')
  })
})

describe('PIN', () => {
  it('hash con sal y verificación', async () => {
    const salt = newSalt(4)
    expect(pinLengthOf(salt)).toBe(4)
    const hash = await hashPin('1234', salt)
    expect(hash).toMatch(/^[0-9a-f]{64}$/)
    expect(await verifyPin('1234', salt, hash)).toBe(true)
    expect(await verifyPin('1235', salt, hash)).toBe(false)
    expect(await verifyPin('1234', newSalt(4), hash)).toBe(false)
    expect(await verifyPin('1234', salt, '')).toBe(false)
    expect(validPin('1234')).toBe(true)
    expect(validPin('123456')).toBe(true)
    expect(validPin('123')).toBe(false)
    expect(validPin('1234567')).toBe(false)
    expect(validPin('12a4')).toBe(false)
    expect(pinLengthOf('raro')).toBe(0)
  })
  it('la implementación propia de SHA-256 coincide con la del sistema', async () => {
    const enc = new TextEncoder()
    expect(sha256Fallback(enc.encode('abc'))).toBe('ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad')
    expect(sha256Fallback(enc.encode(''))).toBe('e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855')
    for (const s of ['4:00ff|1234', 'x'.repeat(55), 'y'.repeat(56), 'ñandú '.repeat(40)]) {
      expect(sha256Fallback(enc.encode(s))).toBe(await sha256Hex(s))
    }
  })
})

describe('borrado', () => {
  it('borrar los datos de un perfil conserva su identidad y sus ajustes', async () => {
    await initProfiles()
    const id = await createProfile({ name: 'Firulais', kind: 'pet', species: 'perro', avatarColor: 5, breed: 'Mestizo', allergies: 'Pollo' }, { atlasSex: 'M' })
    const db = dbFor(id)
    await db.symptoms.add({ timestamp: 1, name: 'Vómito', intensity: 4, regionId: null, regionLabel: null, durationMin: null, tags: '', notes: '' })
    await db.measurements.add({ timestamp: 2, type: 'weight', value: 12.4, value2: null, tag: '', notes: '' })
    const st = await db.studies.add({ epochDay: 1, title: 'Química', category: 'lab', place: '', doctor: '', status: '', summary: '', followUpEpochDay: null })
    await db.studyResults.add({ studyId: st, name: 'Glucosa', value: 90, textValue: '', unit: 'mg/dL', refMin: null, refMax: null, notes: '' })
    await db.studyAttachments.add({ studyId: st, fileName: 'a.png', displayName: 'a.png', mime: 'image/png', data: new ArrayBuffer(4) })
    await db.vaccinations.add({ epochDay: 3, type: 'vacuna', name: 'Rabia', dose: '', nextDueEpochDay: null, place: '', lot: '', notes: '', remind: true })
    // otro perfil con datos que no deben tocarse
    await dbFor(1).symptoms.add({ timestamp: 9, name: 'Tos', intensity: 2, regionId: null, regionLabel: null, durationMin: null, tags: '', notes: '' })

    await wipeProfileData(db)
    for (const t of [db.symptoms, db.measurements, db.studies, db.studyResults, db.studyAttachments, db.vaccinations]) expect(await t.count()).toBe(0)
    const p = (await db.profile.get(1))!
    expect(p).toEqual({ ...emptyProfile(), name: 'Firulais', kind: 'pet', species: 'perro', avatarColor: 5 })
    expect(await getPref(db, 'atlasSex')).toBe('M')
    expect(await dbFor(1).symptoms.count()).toBe(1)
  })

  it('eliminar un perfil lo quita de la lista y regresa al perfil 1 si era el activo', async () => {
    await initProfiles()
    const id = await createProfile({ name: 'Garfield', kind: 'pet', species: 'gato' })
    await switchProfile(id)
    expect((await meta.kv.get('activeProfile'))?.value).toBe(id)
    await removeProfile(id, 0)
    expect(await profileIds()).not.toContain(id)
    expect((await meta.kv.get('activeProfile'))?.value).toBe(1)
    // el perfil 1 nunca se elimina
    await removeProfile(1, 0)
    expect(await profileIds()).toContain(1)
    // los ids no se reutilizan
    const next = await createProfile({ name: 'Otro' })
    expect(next).toBeGreaterThan(id)
  })
})
