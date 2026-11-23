import { describe, expect, it } from 'vitest'
import type { SelfExam } from '../../data/types'
import { SelfExamGuides } from './guides'
import { HabitSteps, WellbeingScale, emptyDailyLog, wellbeingAverages } from './scale'

describe('bienestar diario', () => {
  it('detecta registros vacíos', () => {
    expect(WellbeingScale.isEmpty(emptyDailyLog(100))).toBe(true)
    expect(WellbeingScale.isEmpty({ ...emptyDailyLog(100), notes: '   ' })).toBe(true)
    expect(WellbeingScale.isEmpty({ ...emptyDailyLog(100), mood: 3 })).toBe(false)
    expect(WellbeingScale.isEmpty({ ...emptyDailyLog(100), waterGlasses: 0 })).toBe(false)
  })

  it('etiquetas y colores de las escalas', () => {
    expect(WellbeingScale.moodLabel(1)).toBe('Muy mal')
    expect(WellbeingScale.moodLabel(5)).toBe('Excelente')
    expect(WellbeingScale.moodLabel(9)).toBe('')
    expect(WellbeingScale.goodColor(1)).toBe('var(--bad)')
    expect(WellbeingScale.goodColor(4)).toBe('var(--good)')
    expect(WellbeingScale.stressColor(1)).toBe('var(--good)')
    expect(WellbeingScale.stressColor(5)).toBe('var(--bad)')
  })

  it('contadores de hábitos', () => {
    expect(HabitSteps.sleepPlus(null)).toBe(7)
    expect(HabitSteps.sleepPlus(23.75)).toBe(24)
    expect(HabitSteps.sleepMinus(0.5)).toBe(0)
    expect(HabitSteps.sleepMinus(0)).toBeNull()
    expect(HabitSteps.waterPlus(null)).toBe(1)
    expect(HabitSteps.waterPlus(30)).toBe(30)
    expect(HabitSteps.waterMinus(1)).toBe(0)
    expect(HabitSteps.waterMinus(0)).toBeNull()
    expect(HabitSteps.exercisePlus(null)).toBe(10)
    expect(HabitSteps.exerciseMinus(3)).toBe(0)
    expect(HabitSteps.exerciseMinus(null)).toBeNull()
  })

  it('promedios del periodo', () => {
    const logs = [
      { ...emptyDailyLog(100), mood: 4, sleepHours: 7, exerciseMin: 30 },
      { ...emptyDailyLog(101), mood: 2, waterGlasses: 6 },
      { ...emptyDailyLog(106), energy: 5, exerciseMin: 40 },
    ]
    const a = wellbeingAverages(logs, 106)
    expect(a.mood).toBe(3)
    expect(a.energy).toBe(5)
    expect(a.stress).toBeNull()
    expect(a.sleep).toBe(7)
    expect(a.water).toBe(6)
    expect(a.exerciseTotal).toBe(70)
    expect(a.exercisePerWeek).toBe(70) // 7 días de periodo
  })
})

describe('autoexploración', () => {
  const exam = (epochDay: number, type: string): SelfExam => ({ epochDay, type, normal: true, notes: '' })

  it('ordena los tipos según el sexo del perfil', () => {
    expect(SelfExamGuides.orderedTypes('F')).toEqual(['mama', 'testicular', 'piel'])
    expect(SelfExamGuides.orderedTypes('M')).toEqual(['testicular', 'mama', 'piel'])
    expect(SelfExamGuides.orderedTypes('X')).toEqual(['mama', 'testicular', 'piel'])
  })

  it('última fecha por tipo, cuándo toca y próxima sugerida', () => {
    const last = SelfExamGuides.lastByType([exam(100, 'mama'), exam(140, 'mama'), exam(90, 'piel')])
    expect(last).toEqual({ mama: 140, piel: 90 })
    expect(SelfExamGuides.isDue(last.mama, 169)).toBe(false)
    expect(SelfExamGuides.isDue(last.mama, 170)).toBe(true)
    expect(SelfExamGuides.isDue(undefined, 170)).toBe(false)
    expect(SelfExamGuides.nextSuggested(140)).toBe(170)
    expect(SelfExamGuides.nextSuggested(null)).toBeNull()
  })

  it('guías y títulos', () => {
    expect(SelfExamGuides.title('mama')).toBe('Autoexploración mamaria')
    expect(SelfExamGuides.title('piel')).toBe('Revisión de piel y lunares')
    expect(SelfExamGuides.of('testicular')!.steps.length).toBe(4)
    const piel = SelfExamGuides.of('piel')!
    expect(piel.steps.filter(SelfExamGuides.isLettered).length).toBe(5)
    expect(SelfExamGuides.of('otro')).toBeUndefined()
  })
})
