// Pruebas portadas de PetsLogicTest.kt (parte de mediciones) de la app de Android.
import { describe, expect, it } from 'vitest'
import { MEASURE_TYPES, measureType, type MeasureType } from '../../data/catalogs'
import type { Measurement } from '../../data/types'
import { ymdToEpochDay } from '../../lib/dates'
import { MeasureStats } from './stats'
import { MINOR_BMI_NOTE, MeasureRefs, isMinor, makeWho, whoOf } from './who'

const T = (key: string): MeasureType => measureType(key)!
const m = (type: string, value: number, value2: number | null = null, tag = ''): Measurement => ({ timestamp: 0, type, value, value2, tag, notes: '' })

const adult = makeWho()
const child = makeWho({ isMinor: true })
const dog = makeWho({ isPet: true, species: 'perro', name: 'Firulais' })
const cat = makeWho({ isPet: true, species: 'gato' })
const bird = makeWho({ isPet: true, species: 'ave' })

describe('peso de mascotas y personas', () => {
  it('el peso de mascotas se muestra en gramos por debajo de 1 kg', () => {
    expect(MeasureRefs.weightText(0.35, true)).toBe('350 g')
    expect(MeasureRefs.weightText(0.028, true)).toBe('28 g')
    expect(MeasureRefs.weightText(0.999, true)).toBe('999 g')
    expect(MeasureRefs.weightText(1.0, true)).toBe('1 kg')
    expect(MeasureRefs.weightText(12.345, true)).toBe('12.35 kg')
    expect(MeasureRefs.weightText(4.2, true)).toBe('4.2 kg')
    expect(MeasureRefs.weightParts(0.35, true)).toEqual(['350', 'g'])
  })

  it('el peso de personas conserva un decimal en kilos', () => {
    expect(MeasureRefs.weightText(72.5, false)).toBe('72.5 kg')
    expect(MeasureRefs.weightText(0.35, false)).toBe('0.3 kg') // nunca en gramos
    expect(MeasureRefs.weightParts(72.5, false)).toEqual(['72.5', 'kg'])
    expect(MeasureRefs.weightDelta(0.4, false)).toBe('+0.4 kg')
  })

  it('los cambios de peso usan gramos cuando son pequeños en mascotas', () => {
    expect(MeasureRefs.weightDelta(0.025, true)).toBe('+25 g')
    expect(MeasureRefs.weightDelta(-0.12, true)).toBe('−120 g')
    expect(MeasureRefs.weightDelta(1.25, true)).toBe('+1.25 kg')
    expect(MeasureRefs.weightDelta(0, true)).toBe('0 g')
  })

  it('el peso de mascotas se captura y edita con precisión de gramos', () => {
    expect(MeasureRefs.inputDecimals(T('weight'), true)).toBe(3)
    expect(MeasureRefs.inputDecimals(T('weight'), false)).toBe(1)
    expect(MeasureRefs.inputDecimals(T('temp'), true)).toBe(1)
    const now = 1_000_000_000_000
    expect(MeasureStats.validate(T('weight'), 0.035, null, now, now, true)).toBeNull()
    expect(MeasureStats.validate(T('weight'), 0.035, null, now, now, false)).not.toBeNull()
    expect(MeasureStats.validate(T('weight'), 0.035, null, now, now)).not.toBeNull()
    expect(MeasureStats.validate(T('weight'), 3.2, null, now, now)).toBeNull() // recién nacido
    expect(MeasureStats.validate(T('weight'), 0, null, now, now, true)).not.toBeNull()
  })
})

describe('referencias según el perfil', () => {
  it('las mascotas solo ven las mediciones que les aplican', () => {
    expect(MeasureRefs.types(true).map((t) => t.key)).toEqual(['weight', 'hr', 'temp'])
    const people = MeasureRefs.types(false)
    expect(people.some((t) => t.key === 'height')).toBe(true)
    expect(people.some((t) => t.key === 'bp')).toBe(true)
    expect(people).toHaveLength(MEASURE_TYPES.length)
  })

  it('detecta menores de edad', () => {
    const today = ymdToEpochDay(2026, 11, 23)
    const born = (years: number, extraDays = 0) => ymdToEpochDay(2026 - years, 11, 23) + extraDays
    expect(isMinor(born(7), today)).toBe(true)
    expect(isMinor(born(18, 1), today)).toBe(true) // cumple 18 mañana
    expect(isMinor(born(18), today)).toBe(false) // cumple 18 hoy
    expect(isMinor(born(40), today)).toBe(false)
    expect(isMinor(null, today)).toBe(false) // sin fecha: se asume persona adulta
    expect(whoOf(false, '', born(7), today).isMinor).toBe(true)
    // una mascota joven nunca es "menor de edad"
    const puppy = whoOf(true, 'perro', born(1), today)
    expect(puppy.isMinor).toBe(false)
    expect(puppy.species).toBe('perro')
    // nacida un 29 de febrero
    expect(isMinor(ymdToEpochDay(2008, 2, 29), ymdToEpochDay(2026, 2, 27))).toBe(true)
    expect(isMinor(ymdToEpochDay(2008, 2, 29), ymdToEpochDay(2026, 2, 28))).toBe(false)
  })

  it('menores: IMC como número, sin categoría de adultos', () => {
    const weight = m('weight', 24)
    expect(MeasureStats.bmi(24, 121.5)!).toBeCloseTo(16.3, 1)
    expect(MeasureRefs.showBmi(child)).toBe(true)
    expect(MeasureRefs.showBmiCategory(child)).toBe(false)
    expect(MeasureRefs.assess(child, T('weight'), weight, 121.5)).toBeNull()
    expect(MeasureRefs.note(child, T('weight'))).toBe(MINOR_BMI_NOTE)
    expect(MINOR_BMI_NOTE).toContain('pediatra')
    expect(MeasureRefs.showBmiCategory(adult)).toBe(true)
    expect(MeasureRefs.assess(adult, T('weight'), m('weight', 64), 168)!.label).toBe('Peso saludable')
    expect(MeasureRefs.note(adult, T('weight'))).toBeNull()
  })

  it('menores: presión y pulso sin rangos de adultos; fiebre y oxigenación sí', () => {
    expect(MeasureRefs.assess(child, T('bp'), m('bp', 150, 95), null)).toBeNull()
    expect(MeasureRefs.assess(child, T('hr'), m('hr', 110), null)).toBeNull()
    expect(MeasureRefs.note(child, T('bp'))).not.toBeNull()
    expect(MeasureRefs.note(child, T('hr'))).not.toBeNull()
    expect(MeasureRefs.band(child, T('hr'), [])).toBeNull()
    expect(MeasureRefs.assess(child, T('temp'), m('temp', 38.6), null)!.level).toBe('bad')
    expect(MeasureRefs.assess(child, T('spo2'), m('spo2', 97), null)).not.toBeNull()
    expect(MeasureRefs.assess(adult, T('bp'), m('bp', 150, 95), null)!.label).toBe('Alta (grado 2)')
    expect(MeasureRefs.band(adult, T('hr'), [])).toEqual([60, 100])
  })

  it('mascotas: sin IMC ni clasificaciones humanas', () => {
    expect(MeasureRefs.showBmi(dog)).toBe(false)
    for (const type of MEASURE_TYPES) expect(MeasureRefs.assess(dog, type, m(type.key, 39, 20), 170)).toBeNull()
    expect(MeasureRefs.assess(adult, T('temp'), m('temp', 39), null)!.level).toBe('bad')
  })

  it('mascotas: notas y franjas solo para perro y gato', () => {
    expect(MeasureRefs.note(dog, T('temp'))).toContain('38')
    expect(MeasureRefs.note(cat, T('hr'))).toContain('140')
    expect(MeasureRefs.note(dog, T('weight'))).not.toBeNull()
    expect(MeasureRefs.note(bird, T('temp'))).toBeNull()
    expect(MeasureRefs.note(bird, T('weight'))).toBeNull()
    expect(MeasureRefs.band(dog, T('temp'), [])).toEqual([38, 39.2])
    expect(MeasureRefs.band(cat, T('hr'), [])).toEqual([140, 220])
    expect(MeasureRefs.band(dog, T('hr'), [])).toEqual([60, 140])
    expect(MeasureRefs.band(bird, T('temp'), [])).toBeNull()
    expect(MeasureRefs.band(adult, T('temp'), [])).toEqual([36, 37.4])
  })

  it('la franja de glucosa depende de si predominan las mediciones en ayunas', () => {
    expect(MeasureRefs.band(adult, T('glucose'), [m('glucose', 90, null, 'En ayunas'), m('glucose', 95, null, 'En ayunas'), m('glucose', 130)])).toEqual([70, 99])
    expect(MeasureRefs.band(adult, T('glucose'), [m('glucose', 130, null, '2 h después de comer'), m('glucose', 120)])).toEqual([70, 139])
  })
})
