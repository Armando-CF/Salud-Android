// @ts-nocheck — usa APIs de Node (solo corre en pruebas locales con SAVIA_XPLAT=1)
// Compatibilidad real entre plataformas (solo corre con SAVIA_XPLAT=1, junto con ExportCrossPlatformTest de Android):
//  - escribe un respaldo en formato Android para que la app de Android lo restaure,
//  - lee respaldos generados por la app de Android (un perfil y completo).
import 'fake-indexeddb/auto'
import fs from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { collectArchive, openBackupBytes, summarize, writeAndroidZip } from './backup'
import { resetAll, seedFamily } from './testkit'

const DIR = path.resolve(__dirname, '../../../../build/xplat')
const on = process.env.SAVIA_XPLAT === '1'

describe.skipIf(!on)('respaldos entre Android y web', () => {
  it('escribe un respaldo que Android puede restaurar', async () => {
    await resetAll()
    await seedFamily()
    const bytes = writeAndroidZip((await collectArchive(1)).profiles[0])
    fs.mkdirSync(DIR, { recursive: true })
    fs.writeFileSync(path.join(DIR, 'web-to-android.zip'), bytes)
    expect(bytes.length).toBeGreaterThan(500)
  })

  it.skipIf(!fs.existsSync(path.join(DIR, 'android-profile.zip')))('lee el respaldo de un perfil hecho por Android', () => {
    const a = openBackupBytes(new Uint8Array(fs.readFileSync(path.join(DIR, 'android-profile.zip'))))
    expect(a.profiles.length).toBe(1)
    const t = a.profiles[0].tables
    expect(t.profile?.name).toBe('Alex')
    expect(t.symptoms.length).toBe(12)
    expect(t.cycleDays.length).toBeGreaterThan(30)
    expect(t.measurements.some((m) => m.type === 'bp' && m.value2 !== null)).toBe(true)
    expect(t.studyResults.length).toBeGreaterThan(10)
    expect(t.medications.map((m) => m.name)).toContain('Loratadina')
    expect(a.profiles[0].prefs.cycleEnabled).toBe(true)
  })

  it.skipIf(!fs.existsSync(path.join(DIR, 'android-full.zip')))('lee el respaldo completo (varios perfiles) hecho por Android', () => {
    const a = openBackupBytes(new Uint8Array(fs.readFileSync(path.join(DIR, 'android-full.zip'))))
    expect(a.profiles.map((p) => p.tables.profile?.name)).toEqual(['Alex', 'Leo', 'Firulais'])
    const dog = a.profiles[2]
    expect(dog.tables.profile?.kind).toBe('pet')
    expect(dog.tables.profile?.species).toBe('perro')
    expect(dog.tables.vaccinations.length).toBe(4)
    expect(a.profiles[1].tables.measurements.some((m) => m.type === 'height')).toBe(true)
    expect(summarize(a).profiles.length).toBe(3)
  })
})
