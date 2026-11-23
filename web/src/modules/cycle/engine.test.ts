// Port de app/src/test/java/app/savia/CycleEngineTest.kt (mismos casos y resultados), más casos de borde.
import { describe, expect, it } from 'vitest'
import { emptyCycleDay, type CycleDay } from '../../data/types'
import { analyze, isValidCycle, periodsOf } from './engine'

const day = (epochDay: number, flow: number): CycleDay => ({ ...emptyCycleDay(epochDay), flow })
const bleed = (start: number, n: number): CycleDay[] => Array.from({ length: n }, (_, i) => day(start + i, 3))

describe('motor del ciclo', () => {
  it('agrupa periodos permitiendo huecos cortos', () => {
    const p = periodsOf([day(100, 3), day(101, 4), day(103, 2), day(104, 1), day(128, 3), day(129, 3)])
    expect(p.length).toBe(2)
    expect(p[0]).toEqual({ start: 100, end: 103 })
    expect(p[1].start).toBe(128)
  })

  it('el manchado solo no inicia un periodo', () => {
    expect(periodsOf([day(100, 1), day(101, 1)])).toEqual([])
  })

  it('un hueco de 3 días sin sangrado separa dos periodos', () => {
    expect(periodsOf([day(100, 3), day(103, 3)]).length).toBe(1) // 2 días vacíos entre ambos
    expect(periodsOf([day(100, 3), day(104, 3)]).length).toBe(2) // 3 días vacíos
  })

  it('predice con el promedio de los ciclos recientes', () => {
    // Ciclos de 28, 30, 26 → promedio 28
    const days = [...bleed(1000, 5), ...bleed(1028, 5), ...bleed(1058, 4), ...bleed(1084, 5)]
    const a = analyze(days, 28, 5, 14, 1095)
    expect(a.periods.length).toBe(4)
    expect(a.cycleLength).toBe(28)
    expect(a.avgCycle).toBeCloseTo(28, 3)
    expect(a.nextPeriodStart).toBe(1084 + 28)
    expect(a.daysUntilNext).toBe(17)
    expect(a.currentCycleDay).toBe(12)
    expect(a.lateDays).toBe(0)
    // Ovulación estimada = siguiente periodo − 14
    expect(a.nextOvulation).toBe(1112 - 14)
    expect(a.info(1098).phase).toBe('OVULACION')
    expect(a.info(1095).fertile).toBe(true)
    expect(a.info(1105).phase).toBe('LUTEA')
    expect(a.info(1113).predictedPeriod).toBe(true)
    expect(a.info(1085).phase).toBe('MENSTRUAL')
    expect(a.shortestCycle).toBe(26)
    expect(a.longestCycle).toBe(30)
    expect(a.regular).toBe(true)
    expect(a.cycleStdDev).toBeCloseTo(2, 6)
    expect(a.nextFertileWindow).toEqual({ first: 1093, last: 1099 })
  })

  it('usa los valores por defecto con un solo periodo y reporta el retraso', () => {
    const a = analyze(bleed(1000, 5), 30, 5, 14, 1034)
    expect(a.avgCycle).toBeNull()
    expect(a.cycleLength).toBe(30)
    expect(a.lateDays).toBe(4)
    // Con retraso, la predicción se ancla a hoy.
    expect(a.nextPeriodStart).toBe(1034)
    expect(a.daysUntilNext).toBe(0)
    // Hoy sigue contando desde el último periodo real (día 35), no como día 1 de un periodo estimado.
    expect(a.currentCycleDay).toBe(35)
    expect(a.info(1034).predictedPeriod).toBe(true)
    expect(a.info(1035).cycleDay).toBe(2)
    expect(a.regular).toBeNull()
  })

  it('ignora ciclos atípicos en el promedio', () => {
    // 28, 120 (meses sin registrar), 28
    const days = [...bleed(1000, 5), ...bleed(1028, 5), ...bleed(1148, 5), ...bleed(1176, 5)]
    const a = analyze(days, 28, 5, 14, 1180)
    expect(a.avgCycle).toBeCloseTo(28, 3)
    expect(isValidCycle(a.cycles[1])).toBe(false)
    expect(a.cycles[3].length).toBeNull()
  })

  it('historial vacío', () => {
    const a = analyze([], 28, 5, 14, 500)
    expect(a.hasData).toBe(false)
    expect(a.nextPeriodStart).toBeNull()
    expect(a.currentCycleDay).toBeNull()
    expect(a.info(500).phase).toBe('DESCONOCIDA')
    expect(a.nextOvulation).toBeNull()
    expect(a.nextFertileWindow).toBeNull()
    expect(a.periodOngoing).toBe(false)
  })

  it('el periodo en curso no entra en el promedio de duración', () => {
    const a = analyze([...bleed(1000, 5), ...bleed(1028, 2)], 28, 5, 14, 1029)
    expect(a.avgPeriod).toBeCloseTo(5, 3)
    expect(a.periodOngoing).toBe(true)
  })

  it('no considera periodos con fecha futura y limita los valores por defecto', () => {
    const a = analyze([...bleed(1000, 5), ...bleed(1100, 3)], 99, 30, 3, 1010)
    expect(a.periods.length).toBe(1)
    expect(a.cycleLength).toBe(60) // 99 → máximo 60
    expect(a.periodLength).toBe(5) // promedio real del periodo terminado
    expect(a.lutealLength).toBe(8) // 3 → mínimo 8
    expect(a.predictedStarts.length).toBe(12)
    expect(a.predictedStarts[1] - a.predictedStarts[0]).toBe(60)
  })

  it('con un historial muy viejo no reporta retraso', () => {
    const a = analyze(bleed(1000, 5), 28, 5, 14, 1400)
    expect(a.lateDays).toBe(0)
    expect(a.nextPeriodStart).toBe(1400)
    expect(a.info(1400).cycleDay).toBeNull()
  })

  it('un día de periodo registrado no cuenta como fértil ni como ovulación', () => {
    // Ciclo corto: la ventana fértil estimada cae sobre días de periodo registrados
    const days = [...bleed(1000, 5), ...bleed(1020, 8)]
    const a = analyze(days, 28, 5, 14, 1030)
    const i = a.info(1022)
    expect(i.inPeriod).toBe(true)
    expect(i.fertile).toBe(false)
    expect(i.ovulation).toBe(false)
    expect(i.phase).toBe('MENSTRUAL')
  })
})
