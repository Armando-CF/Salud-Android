import { describe, expect, it } from 'vitest'
import type { Study, StudyResult } from '../../data/types'
import { ymdToEpochDay } from '../../lib/dates'
import {
  StudyCatalog, flag, normalizeName, outOfRange, paramFromRoute, paramPath, paramSeries, paramSummaries, rangeFor, rangeText,
  upcomingFollowUps, valueText,
} from './catalog'
import { StudyProfile } from './profile'

const study = (o: Partial<Study>): Study => ({ epochDay: 0, title: '', category: 'lab', place: '', doctor: '', status: '', summary: '', followUpEpochDay: null, ...o })
const result = (o: Partial<StudyResult>): StudyResult => ({ studyId: 1, name: '', value: null, textValue: '', unit: '', refMin: null, refMax: null, notes: '', ...o })

describe('catálogo de estudios', () => {
  it('clasifica contra el rango', () => {
    expect(flag(92, 70, 99)).toBe('NORMAL')
    expect(flag(70, 70, 99)).toBe('NORMAL')
    expect(flag(99, 70, 99)).toBe('NORMAL')
    expect(flag(69.9, 70, 99)).toBe('BAJO')
    expect(flag(99.1, 70, 99)).toBe('ALTO')
  })
  it('rangos abiertos', () => {
    expect(flag(212, null, 200)).toBe('ALTO')
    expect(flag(185, null, 200)).toBe('NORMAL')
    expect(flag(35, 40, null)).toBe('BAJO')
    expect(flag(55, 40, null)).toBe('NORMAL')
  })
  it('sin rango o sin valor', () => {
    expect(flag(5, null, null)).toBe('SIN_RANGO')
    expect(flag(null, 1, 2)).toBe('SIN_RANGO')
    expect(outOfRange(result({ value: 212, refMax: 200 }))).toBe(true)
    expect(outOfRange(result({ textValue: 'Negativo' }))).toBe(false)
  })
  it('normaliza mayúsculas, acentos y espacios', () => {
    expect(normalizeName('  Triglicéridos ')).toBe('trigliceridos')
    expect(normalizeName('ÁCIDO   Úrico')).toBe('acido urico')
  })
  it('busca por nombre y alias', () => {
    expect(StudyCatalog.find('glucosa')?.name).toBe('Glucosa')
    expect(StudyCatalog.find('hdl')?.name).toBe('Colesterol HDL')
    expect(StudyCatalog.find('TRIGLICERIDOS')?.name).toBe('Triglicéridos')
    expect(StudyCatalog.find('no existe')).toBeNull()
    expect(StudyCatalog.find('')).toBeNull()
  })
  it('los rangos dependen del sexo', () => {
    const hb = StudyCatalog.find('Hemoglobina')!
    expect(rangeFor(hb, 'F')).toEqual([12, 16])
    expect(rangeFor(hb, 'M')).toEqual([13.5, 17.5])
    expect(rangeFor(hb, 'X')).toEqual([12, 17.5])
    expect(rangeFor(StudyCatalog.find('Colesterol HDL')!, 'F')).toEqual([50, null])
  })
  it('el catálogo es consistente', () => {
    for (const p of StudyCatalog.params) if (p.refMin != null && p.refMax != null) expect(p.refMin, p.name).toBeLessThan(p.refMax)
    for (const g of StudyCatalog.groups) for (const n of g.params) expect(StudyCatalog.find(n), `${n} en ${g.name}`).not.toBeNull()
    expect(new Set(StudyCatalog.params.map((p) => normalizeName(p.name))).size).toBe(StudyCatalog.params.length)
  })
  it('las sugerencias prefieren el prefijo e incluyen nombres propios', () => {
    const s = StudyCatalog.suggest('col', ['Colinesterasa'])
    expect(s[0]).toBe('Colesterol total')
    expect(s).toContain('Colinesterasa')
    expect(StudyCatalog.suggest('')).toEqual([])
    expect(StudyCatalog.suggest('glucosa')).not.toContain('Glucosa')
  })
  it('formatea rangos y valores', () => {
    expect(rangeText(70, 99)).toBe('70 – 99')
    expect(rangeText(null, 200)).toBe('< 200')
    expect(rangeText(40, null)).toBe('> 40')
    expect(rangeText(null, null)).toBe('')
    expect(valueText(result({ value: 92, unit: 'mg/dL' }))).toBe('92 mg/dL')
    expect(valueText(result({ textValue: 'Negativo' }))).toBe('Negativo')
    expect(valueText(result({}))).toBe('—')
  })
  it('las series comparan nombres sin acentos y ordenan por fecha', () => {
    const studies = [study({ id: 1, epochDay: 300, title: 'B' }), study({ id: 2, epochDay: 100, title: 'A' })]
    const results = [
      result({ id: 1, studyId: 1, name: 'Triglicéridos', value: 138 }),
      result({ id: 2, studyId: 2, name: 'trigliceridos ', value: 160 }),
      result({ id: 3, studyId: 2, name: 'Glucosa', value: 95 }),
      result({ id: 4, studyId: 2, name: 'Orina', textValue: 'Normal' }),
      result({ id: 5, studyId: 99, name: 'Glucosa', value: 1 }),
    ]
    expect(paramSeries('TRIGLICERIDOS', studies, results).map((p) => p.value)).toEqual([160, 138])
    const summaries = paramSummaries(studies, results)
    expect(summaries.map((s) => s.name)).toEqual(['Glucosa', 'Triglicéridos'])
    expect(summaries[1].latest.value).toBe(138)
    expect(summaries[1].previous!.value).toBe(160)
  })
  it('seguimientos ordenados y sin los muy viejos', () => {
    const studies = [
      study({ id: 1, title: 'Lejano', followUpEpochDay: 1200 }),
      study({ id: 2, title: 'Próximo', followUpEpochDay: 1010 }),
      study({ id: 3, title: 'Vencido hace poco', followUpEpochDay: 990 }),
      study({ id: 4, title: 'Muy viejo', followUpEpochDay: 500 }),
      study({ id: 5, title: 'Sin seguimiento' }),
    ]
    expect(upcomingFollowUps(studies, 1000).map((s) => s.id)).toEqual([3, 2, 1])
  })
  it('la ruta del parámetro protege la diagonal', () => {
    const p = paramPath('Relación A/G')
    expect(p).not.toContain('%2F')
    expect(paramFromRoute(decodeURIComponent(p.replace('/parametro/', '')))).toBe('Relación A/G')
  })
})

describe('perfil de estudios', () => {
  const past = [result({ id: 1, name: 'Glucosa', value: 90, unit: 'mg/dl', refMin: 74, refMax: 106 })]
  it('personas: rango del catálogo según el sexo; lo capturado antes tiene prioridad', () => {
    const sp = new StudyProfile(false, '', 'F')
    expect(sp.defaultsFor('Hemoglobina', [])).toEqual({ unit: 'g/dL', refMin: 12, refMax: 16 })
    expect(sp.defaultsFor('glucosa', past)).toEqual({ unit: 'mg/dl', refMin: 74, refMax: 106 })
    expect(sp.defaultsFor('Algo raro', [])).toBeNull()
    expect(sp.categories().map((c) => c[0])).not.toContain('vacuna')
    expect(sp.categories('vacuna').map((c) => c[0])).toContain('vacuna')
    expect(sp.titlesFor('vacuna')).toEqual([])
    expect(sp.titlesFor('gine')).toContain('Papanicolaou')
  })
  it('mascotas: solo unidad, categorías filtradas y textos de veterinario', () => {
    const sp = new StudyProfile(true, 'perro', 'M')
    expect(sp.defaultsFor('Creatinina', [])).toEqual({ unit: 'mg/dL', refMin: null, refMax: null })
    expect(sp.defaultsFor('Glucosa', past)).toEqual({ unit: 'mg/dl', refMin: 74, refMax: 106 })
    expect(sp.categories().map((c) => c[0])).toEqual(['lab', 'imagen', 'chequeo', 'cardio', 'dental', 'piel', 'otro'])
    expect(sp.categoryShort('chequeo')).toBe('Consulta o revisión')
    expect(sp.titlesFor('lab')).toContain('Prueba de parvovirus y moquillo')
    expect(new StudyProfile(true, 'gato').titlesFor('lab')).toContain('Prueba de leucemia e inmunodeficiencia felina')
    expect(sp.doctorLabel).toBe('Veterinaria o veterinario')
    expect(sp.rangesNote).toContain('laboratorio veterinario')
    for (const g of sp.groups) for (const n of g.params) expect(StudyCatalog.find(n), n).not.toBeNull()
  })
  it('menores de edad', () => {
    const t = ymdToEpochDay(2026, 11, 23)
    expect(StudyProfile.isMinor(ymdToEpochDay(2019, 8, 15), t)).toBe(true)
    expect(StudyProfile.isMinor(ymdToEpochDay(2008, 11, 23), t)).toBe(false)
    expect(StudyProfile.isMinor(ymdToEpochDay(2008, 11, 24), t)).toBe(true)
    expect(StudyProfile.isMinor(null, t)).toBe(false)
    expect(new StudyProfile(false, '', 'M', true).rangesNote).toContain('adultos')
  })
})
