// Pruebas portadas de MeasureStatsTest.kt (app de Android).
import { describe, expect, it } from 'vitest'
import { measureType, type MeasureType } from '../../data/catalogs'
import type { Measurement } from '../../data/types'
import { epochDayToMillis } from '../../lib/dates'
import { MeasureStats, type TPoint } from './stats'

const T = (key: string): MeasureType => measureType(key)!
const m = (type: string, value: number, value2: number | null = null, tag = '', timestamp = 0): Measurement => ({ timestamp, type, value, value2, tag, notes: '' })
const line = (n: number, start: number, slopePerDay: number, stepDays = 1, x0 = 20000): TPoint[] =>
  Array.from({ length: n }, (_, i) => ({ x: x0 + i * stepDays, y: start + slopePerDay * i * stepDays }))

describe('MeasureStats', () => {
  it('la regresión recupera pendiente e intersección', () => {
    const r = MeasureStats.regression(line(30, 80, -0.1))!
    expect(r.slopePerDay).toBeCloseTo(-0.1, 9)
    expect(r.perWeek).toBeCloseTo(-0.7, 9)
    expect(r.at(20000)).toBeCloseTo(80, 6)
    expect(MeasureStats.regression([{ x: 1, y: 2 }])).toBeNull()
    expect(MeasureStats.regression([{ x: 1, y: 2 }, { x: 1, y: 3 }])).toBeNull()
  })

  it('la media móvil usa una ventana de 7 días', () => {
    const pts = Array.from({ length: 10 }, (_, i) => ({ x: 100 + i, y: i }))
    const ma = MeasureStats.movingAverage(pts)
    expect(ma).toHaveLength(10)
    expect(ma[0].y).toBeCloseTo(0, 9)
    expect(ma[2].y).toBeCloseTo(1, 9) // (0+1+2)/3
    expect(ma[9].y).toBeCloseTo(6, 9) // promedio de 3..9
    expect(MeasureStats.movingAverage([])).toHaveLength(0)
  })

  it('el cambio compara con la medición más cercana', () => {
    const pts = line(21, 70, -0.1, 3) // cada 3 días, 60 días
    expect(MeasureStats.changeOver(pts, 7)!).toBeCloseTo(-0.6, 9) // referencia: hace 6 días
    expect(MeasureStats.changeOver(pts, 30)!).toBeCloseTo(-3, 9)
    expect(MeasureStats.changeOver([{ x: 0, y: 70 }, { x: 100, y: 69 }], 7)).toBeNull()
    expect(MeasureStats.changeOver([{ x: 0, y: 70 }], 7)).toBeNull()
  })

  it('resumen de valores', () => {
    const s = MeasureStats.summary([3, 1, 5])!
    expect(s.min).toBe(1)
    expect(s.max).toBe(5)
    expect(s.avg).toBeCloseTo(3, 9)
    expect(s.change).toBe(2)
    expect(MeasureStats.summary([])).toBeNull()
  })

  it('IMC y categorías', () => {
    expect(MeasureStats.bmi(70, 170)!).toBeCloseTo(24.22, 2)
    expect(MeasureStats.bmi(70, null)).toBeNull()
    expect(MeasureStats.bmiCategory(18.4).label).toBe('Bajo peso')
    expect(MeasureStats.bmiCategory(22).level).toBe('good')
    expect(MeasureStats.bmiCategory(27).label).toBe('Sobrepeso')
    expect(MeasureStats.bmiCategory(31).label).toBe('Obesidad grado I')
    expect(MeasureStats.bmiCategory(41).label).toBe('Obesidad grado III')
    const [lo, hi] = MeasureStats.healthyWeightRange(170)
    expect(lo).toBeCloseTo(53.5, 1)
    expect(hi).toBeCloseTo(72, 1)
  })

  it('clases de presión arterial', () => {
    expect(MeasureStats.bloodPressure(115, 75).label).toBe('Normal')
    expect(MeasureStats.bloodPressure(125, 75).label).toBe('Elevada')
    expect(MeasureStats.bloodPressure(118, 84).label).toBe('Alta (grado 1)')
    expect(MeasureStats.bloodPressure(135, 70).label).toBe('Alta (grado 1)')
    expect(MeasureStats.bloodPressure(150, 85).label).toBe('Alta (grado 2)')
    expect(MeasureStats.bloodPressure(185, 100).label).toBe('Revisar con urgencia')
    expect(MeasureStats.bloodPressure(85, 55).label).toBe('Baja')
  })

  it('la glucosa depende del contexto', () => {
    expect(MeasureStats.glucose(95, 'En ayunas').label).toBe('Normal')
    expect(MeasureStats.glucose(110, 'En ayunas').label).toBe('Elevada')
    expect(MeasureStats.glucose(130, 'En ayunas').label).toBe('Alta')
    expect(MeasureStats.glucose(130, '2 h después de comer').label).toBe('Normal')
    expect(MeasureStats.glucose(160, '2 h después de comer').label).toBe('Elevada')
    expect(MeasureStats.glucose(65, '').label).toBe('Baja')
    expect(MeasureStats.glucose(50, 'En ayunas').level).toBe('bad')
    expect(MeasureStats.glucose(120, '').level).toBe('good')
  })

  it('otras clasificaciones', () => {
    expect(MeasureStats.spo2(97).level).toBe('good')
    expect(MeasureStats.spo2(93).level).toBe('warn')
    expect(MeasureStats.spo2(89).level).toBe('bad')
    expect(MeasureStats.temperature(36.6).label).toBe('Normal')
    expect(MeasureStats.temperature(37.7).label).toBe('Febrícula')
    expect(MeasureStats.temperature(38.4).label).toBe('Fiebre')
    expect(MeasureStats.heartRate(72, 'En reposo').label).toBe('Normal')
    expect(MeasureStats.heartRate(110, '').level).toBe('warn')
    expect(MeasureStats.heartRate(150, 'Después de ejercicio').level).toBe('neutral')
    expect(MeasureStats.waistToHeight(78, 168).level).toBe('good')
    expect(MeasureStats.waistToHeight(90, 168).level).toBe('warn')
  })

  it('clasifica según el tipo', () => {
    expect(MeasureStats.assess(T('bp'), m('bp', 150, 95))!.label).toBe('Alta (grado 2)')
    const w = m('weight', 70)
    expect(MeasureStats.assess(T('weight'), w, null)).toBeNull()
    expect(MeasureStats.assess(T('weight'), w, 170)!.label).toBe('Peso saludable')
    expect(MeasureStats.assess(T('hip'), m('hip', 95), 170)).toBeNull()
    expect(MeasureStats.assess(T('height'), m('height', 170), 170)).toBeNull()
  })

  it('solo proyecta la meta si la tendencia va hacia ella', () => {
    const pts = line(31, 73, -0.1) // baja 0.1 kg/día; último = 70.0 en el día 20030
    const p = MeasureStats.goalProjection(pts, 68, 20030)!
    expect(p.daysLeft).toBe(20)
    expect(p.epochDay).toBe(20050)
    expect(MeasureStats.goalProjection(pts, 75, 20030)).toBeNull() // meta arriba, tendencia a la baja
    expect(MeasureStats.goalProjection(line(10, 70, 0), 68, 20010)).toBeNull() // tendencia plana
    expect(MeasureStats.goalProjection(pts, 70, 20030)!.daysLeft).toBe(0) // ya en la meta
    expect(MeasureStats.goalProjection(line(31, 100, -0.005), 60, 20030)).toBeNull() // más de 3 años
  })

  it('meta alcanzada y avance', () => {
    expect(MeasureStats.goalReached(80, 69.9, 70)).toBe(true)
    expect(MeasureStats.goalReached(80, 72, 70)).toBe(false)
    expect(MeasureStats.goalReached(50, 55, 55)).toBe(true)
    expect(MeasureStats.goalProgress(80, 75, 70)).toBeCloseTo(0.5, 6)
    expect(MeasureStats.goalProgress(80, 82, 70)).toBeCloseTo(0, 6)
    expect(MeasureStats.goalProgress(80, 68, 70)).toBeCloseTo(1, 6)
  })

  it('resumen de peso para tarjetas (en cualquier orden)', () => {
    const t = 20000
    const ws = Array.from({ length: 21 }, (_, i) => m('weight', 72 - i * 0.1, null, '', epochDayToMillis(t + i * 3, 8 * 60)))
    const shuffled = [...ws].sort((a, b) => ((a.timestamp * 7919) % 13) - ((b.timestamp * 7919) % 13))
    const s = MeasureStats.weightSummary(shuffled, 170, 68)!
    expect(s.latest).toBeCloseTo(70, 9)
    expect(s.change7!).toBeCloseTo(-0.2, 9)
    expect(s.change30!).toBeCloseTo(-1, 9)
    expect(s.toGoal!).toBeCloseTo(-2, 9)
    expect(s.goalProgress!).toBeCloseTo(0.5, 4)
    expect(s.goalReached).toBe(false)
    expect(s.bmiAssessment).not.toBeNull()
    expect(s.spark).toHaveLength(20)
    expect(s.spark[19]).toBeCloseTo(70, 9)
    expect(MeasureStats.weightSummary([], 170, null)).toBeNull()
    // Mascotas: sin estatura no hay IMC
    expect(MeasureStats.weightSummary(ws, null, null)!.bmi).toBeNull()
  })

  it('validación de captura', () => {
    const now = 1_000_000_000_000
    expect(MeasureStats.validate(T('weight'), null, null, now, now)).not.toBeNull()
    expect(MeasureStats.validate(T('weight'), 900, null, now, now)).not.toBeNull()
    expect(MeasureStats.validate(T('weight'), 72.5, null, now, now)).toBeNull()
    expect(MeasureStats.validate(T('bp'), 120, null, now, now)).not.toBeNull()
    expect(MeasureStats.validate(T('bp'), 80, 120, now, now)).not.toBeNull()
    expect(MeasureStats.validate(T('bp'), 120, 80, now, now)).toBeNull()
    expect(MeasureStats.validate(T('weight'), 72.5, null, now + 3_600_000, now)).not.toBeNull()
  })

  it('el día con fracción respeta la hora local', () => {
    expect(MeasureStats.dayX(epochDayToMillis(20000, 0))).toBeCloseTo(20000, 9)
    expect(MeasureStats.dayX(epochDayToMillis(20000, 12 * 60))).toBeCloseTo(20000.5, 6)
    const pts = MeasureStats.points([m('bp', 120, 80, '', epochDayToMillis(20001, 60)), m('bp', 118, null, '', epochDayToMillis(20000, 60))], true)
    expect(pts).toHaveLength(1)
    expect(pts[0].y).toBe(80)
  })
})
