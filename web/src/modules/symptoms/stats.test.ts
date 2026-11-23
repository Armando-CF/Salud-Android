import { describe, expect, it } from 'vitest'
import type { SymptomEntry } from '../../data/types'
import { epochDayToMillis, ymdToEpochDay } from '../../lib/dates'
import { symptomKit } from './kit'
import { PetSymptomCatalog } from './petCatalog'
import { SymptomStats } from './stats'

const T = ymdToEpochDay(2026, 11, 23) // lunes
const e = (ago: number, name: string, intensity: number, extra: Partial<SymptomEntry> = {}): SymptomEntry => ({
  timestamp: epochDayToMillis(T - ago, 600), name, intensity, regionId: null, regionLabel: null, durationMin: null, tags: '', notes: '', ...extra,
})

describe('SymptomStats', () => {
  it('semana: el lunes de cada día', () => {
    expect(SymptomStats.weekStart(T)).toBe(T)
    expect(SymptomStats.weekStart(T + 6)).toBe(T)
    expect(SymptomStats.weekStart(T - 1)).toBe(T - 7)
    expect(SymptomStats.weekStart(0)).toBe(-3) // 1970-01-01 fue jueves
  })

  it('busca sin acentos en nombre, zona, etiquetas y nota', () => {
    const x = e(0, 'Dolor de cabeza', 5, { regionLabel: 'Fosa ilíaca derecha', tags: 'Estrés,Pantallas', notes: 'Mejoró con reposo' })
    expect(SymptomStats.matches(x, 'CABEZA')).toBe(true)
    expect(SymptomStats.matches(x, 'iliaca')).toBe(true)
    expect(SymptomStats.matches(x, 'estres')).toBe(true)
    expect(SymptomStats.matches(x, 'reposo')).toBe(true)
    expect(SymptomStats.matches(x, 'rodilla')).toBe(false)
    expect(SymptomStats.matches(x, '  ')).toBe(true)
  })

  it('resume por semana en rangos cortos, con semanas vacías', () => {
    const list = [e(0, 'Tos', 3), e(1, 'Tos', 5), e(1, 'Fiebre', 8, { tags: 'Clima, Estrés' }), e(15, 'Tos', 4, { regionLabel: 'Pecho', tags: 'Clima' }), e(200, 'Migraña', 9)]
    const s = SymptomStats.summarize(list, T - 30, T)
    expect(s.total).toBe(4)
    expect(s.monthly).toBe(false)
    expect(s.daysWithSymptoms).toBe(3)
    expect(s.avgIntensity).toBe(5)
    expect(s.topNames[0]).toEqual(['Tos', 3])
    expect(s.strongest[0]).toEqual(['Fiebre', 8])
    expect(s.topRegions).toEqual([['Pecho', 1]])
    expect(s.topTriggers[0]).toEqual(['Clima', 2])
    // del lunes de (T-30) al lunes de T: 6 semanas (T-35, T-28, …, T)
    expect(s.buckets.length).toBe(6)
    expect(s.buckets[s.buckets.length - 1][1]).toBe(1) // solo el de hoy cae en la semana actual
    expect(s.buckets.map((b) => b[1]).reduce((a, b) => a + b, 0)).toBe(4)
  })

  it('resume por mes en rangos largos y con todo el historial', () => {
    const list = [e(0, 'Tos', 3), e(40, 'Tos', 5), e(200, 'Migraña', 9)]
    const s = SymptomStats.summarize(list, null, T)
    expect(s.total).toBe(3)
    expect(s.monthly).toBe(true)
    expect(s.buckets[s.buckets.length - 1]).toEqual(['nov', 1])
    expect(s.buckets.map((b) => b[1]).reduce((a, b) => a + b, 0)).toBe(3)
    const empty = SymptomStats.summarize([], T - 30, T)
    expect(empty.total).toBe(0)
    expect(empty.avgIntensity).toBeNull()
  })

  it('nombres usados, del más frecuente al menos', () => {
    expect(SymptomStats.usedNames([e(0, 'Tos', 1), e(1, 'Fiebre', 1), e(2, 'Tos', 1)])).toEqual(['Tos', 'Fiebre'])
    expect(SymptomStats.splitTags(' a, b,,c ')).toEqual(['a', 'b', 'c'])
  })
})

describe('kit de síntomas según el perfil', () => {
  it('persona', () => {
    const k = symptomKit(false)
    expect(k.whatTitle).toBe('¿Qué sientes?')
    expect(k.all).toContain('Dolor de cabeza')
    expect(k.canPickBodyRegion).toBe(true)
    expect(k.intensityLabel(1)).toBe('Muy leve')
  })
  it('mascota', () => {
    const k = symptomKit(true, 'perro', 'Firulais')
    expect(k.whatTitle).toBe('¿Qué le notas?')
    expect(k.all).toContain('Cojera')
    expect(k.triggers).toContain('Comió algo raro')
    expect(k.canPickBodyRegion).toBe(true)
    expect(k.emptyMessage).toContain('Firulais')
    expect(k.intensityLabel(1)).toBe('Apenas se nota')
    expect(symptomKit(true, 'ave').canPickBodyRegion).toBe(false)
    expect(PetSymptomCatalog.alarmSummary.startsWith('Acude a urgencias veterinarias si ')).toBe(true)
  })
})
