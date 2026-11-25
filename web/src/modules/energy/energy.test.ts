import { describe, expect, it } from 'vitest'
import { ACTIVITY_LEVELS, activityLevel, bmr, breakdown, dailyBalanceFromTrend, exerciseDaysPerWeek, exerciseKcal, kcalText, round10, share, suggestLevel, tdee, weightSlopePerWeek } from './energy'

const [SED, LIGHT, MOD, VERY] = ACTIVITY_LEVELS

describe('gasto de energía', () => {
  it('Mifflin-St Jeor', () => {
    expect(bmr(80, 180, 30, 'M')).toBeCloseTo(1780, 3)
    expect(bmr(65, 165, 30, 'F')).toBeCloseTo(1370.25, 3)
    expect(bmr(70, 170, 40, 'X')).toBeCloseTo((bmr(70, 170, 40, 'M') + bmr(70, 170, 40, 'F')) / 2, 3)
  })
  it('total por nivel de actividad', () => {
    expect(tdee(1780, SED)).toBeCloseTo(2136, 3)
    expect(tdee(1780, LIGHT)).toBeCloseTo(2447.5, 3)
    expect(tdee(1780, MOD)).toBeCloseTo(2759, 3)
    expect(tdee(1780, VERY)).toBeCloseTo(3070.5, 3)
    expect(activityLevel('moderate')).toBe(MOD)
    expect(activityLevel('')).toBeNull()
  })
  it('las tres partes suman el total', () => {
    const b = breakdown(1780, MOD)
    expect(b.total).toBeCloseTo(2759, 3)
    expect(b.digestion).toBeCloseTo(275.9, 3)
    expect(b.movement).toBeCloseTo(703.1, 3)
    expect(share(b.basal, b.total) + share(b.digestion, b.total) + share(b.movement, b.total)).toBe(100)
    expect(breakdown(1780, SED).movement).toBeGreaterThanOrEqual(0)
  })
  it('ejercicio con METs', () => {
    expect(exerciseKcal(3.5, 70, 60)).toBeCloseTo(245, 3)
    expect(exerciseKcal(8, 70, 30)).toBeCloseTo(280, 3)
    expect(round10(245)).toBe(250)
    expect(kcalText(2759)).toBe('2,760')
  })
  it('sugiere el nivel con el ejercicio registrado', () => {
    expect(suggestLevel(0.5)).toBe(SED)
    expect(suggestLevel(2)).toBe(LIGHT)
    expect(suggestLevel(4)).toBe(MOD)
    expect(suggestLevel(6)).toBe(VERY)
    const logs = Array.from({ length: 28 }, (_, i) => ({ epochDay: 1000 - i, exerciseMin: i % 3 === 0 ? 40 : 0 }))
    expect(exerciseDaysPerWeek(logs, 1000)).toBeCloseTo(2.5, 3)
    expect(exerciseDaysPerWeek(logs.slice(0, 5), 1000)).toBeNull()
    expect(exerciseDaysPerWeek(logs.map((l) => ({ ...l, epochDay: l.epochDay - 100 })), 1000)).toBeNull()
  })
  it('balance según la tendencia de peso', () => {
    const pts = Array.from({ length: 7 }, (_, i) => [i * 7, 80 - i * 0.5] as const)
    const slope = weightSlopePerWeek(pts)!
    expect(slope).toBeCloseTo(-0.5, 4)
    expect(dailyBalanceFromTrend(slope)).toBeCloseTo(-550, 2)
    expect(weightSlopePerWeek(pts.slice(0, 3))).toBeNull()
    expect(weightSlopePerWeek(Array.from({ length: 6 }, (_, i) => [i, 70] as const))).toBeNull()
  })
})
