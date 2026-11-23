// Port de app/src/test/java/app/savia/CycleUiLogicTest.kt.
import { describe, expect, it } from 'vitest'
import { emptyCycleDay, type CycleDay } from '../../data/types'
import { ymdToEpochDay } from '../../lib/dates'
import { PHASE_LABEL, analyze } from './engine'
import {
  dias, hasExtras, isEmpty, monthGrid, monthGridDays, monthIndex, monthOfEpochDay, monthOfIndex, moodStats, quickState,
  rangeText, regularityText, summary, symptomStats, todayStatus, withFlow,
} from './logic'

const cd = (epochDay: number, o: Partial<CycleDay> = {}): CycleDay => ({ ...emptyCycleDay(epochDay), ...o })
const bleed = (start: number, n: number): CycleDay[] => Array.from({ length: n }, (_, i) => cd(start + i, { flow: 3 }))
const run = (days: CycleDay[], today: number, cycle = 28) => analyze(days, cycle, 5, 14, today)

describe('lógica del ciclo', () => {
  it('la cuadrícula del mes empieza en lunes y tiene seis semanas', () => {
    // Noviembre de 2026 empieza en domingo → 6 huecos antes del día 1.
    const cells = monthGrid({ year: 2026, month: 11 })
    expect(cells.length).toBe(42)
    expect(cells.findIndex((c) => c !== null)).toBe(6)
    expect(cells[6]).toBe(ymdToEpochDay(2026, 11, 1))
    const real = cells.filter((c) => c !== null)
    expect(real.length).toBe(30)
    expect(real[real.length - 1]).toBe(ymdToEpochDay(2026, 11, 30))
    // Febrero de 2021 empieza en lunes y dura 28 días: 4 semanas exactas sin relleno.
    const feb = monthGrid({ year: 2021, month: 2 }, false)
    expect(feb.length).toBe(28)
    expect(feb.every((c) => c !== null)).toBe(true)
  })

  it('índice de mes ida y vuelta', () => {
    const i = monthIndex({ year: 2026, month: 11 })
    expect(monthOfIndex(i)).toEqual({ year: 2026, month: 11 })
    expect(monthOfIndex(i + 1)).toEqual({ year: 2026, month: 12 })
    expect(monthOfIndex(i + 2)).toEqual({ year: 2027, month: 1 })
    expect(monthOfIndex(0)).toEqual({ year: 1990, month: 1 })
    expect(monthOfIndex(-1)).toEqual({ year: 1989, month: 12 })
    expect(monthOfEpochDay(ymdToEpochDay(2026, 11, 23))).toEqual({ year: 2026, month: 11 })
  })

  it('registro vacío y extras', () => {
    expect(isEmpty(cd(1))).toBe(true)
    expect(isEmpty(cd(1, { flow: 1 }))).toBe(false)
    expect(isEmpty(cd(1, { contraceptive: true }))).toBe(false)
    expect(isEmpty(cd(1, { pregnancyTest: 1 }))).toBe(false)
    expect(hasExtras(cd(1, { flow: 3 }))).toBe(false)
    expect(hasExtras(cd(1, { flow: 3, notes: 'x' }))).toBe(true)
    expect(hasExtras(cd(1, { symptoms: 'colicos' }))).toBe(true)
  })

  it('cambiar el flujo conserva lo demás y borra cuando queda vacío', () => {
    expect(withFlow(cd(5, { flow: 3 }), 5, 0)).toBeNull()
    expect(withFlow(null, 5, 0)).toBeNull()
    const kept = withFlow(cd(5, { flow: 3, notes: 'nota' }), 5, 0)
    expect(kept).not.toBeNull()
    expect(kept!.flow).toBe(0)
    expect(kept!.notes).toBe('nota')
    expect(withFlow(null, 7, 3)!.flow).toBe(3)
    expect(withFlow(null, 7, 3)!.epochDay).toBe(7)
    expect(withFlow(null, 7, 9)!.flow).toBe(4)
  })

  it('el resumen lista todo lo registrado', () => {
    const d = cd(1, { flow: 3, symptoms: 'colicos,fatiga', moods: 'triste', mucus: 'clara', basalTemp: 36.55, sex: 1, ovulationTest: 2, pregnancyTest: 1, contraceptive: true, notes: ' hola ' })
    expect(summary(d)).toEqual([
      'Flujo moderado', 'Síntomas: cólicos, fatiga', 'Ánimo: tristeza', 'Flujo cervical: clara de huevo',
      'Temperatura basal: 36.55 °C', 'Relaciones: con protección', 'Prueba de ovulación: positiva',
      'Prueba de embarazo: negativa', 'Anticonceptivo tomado o aplicado', 'Notas: hola',
    ])
    expect(summary(cd(1, { flow: 1 }))).toEqual(['Manchado'])
    expect(summary(cd(1))).toEqual([])
  })

  it('estado: cuenta regresiva', () => {
    const s = todayStatus(run([...bleed(1000, 5), ...bleed(1028, 5), ...bleed(1056, 5)], 1066))
    expect(s.cycleDay).toBe(11)
    expect(s.headline).toBe('Periodo en 18 días')
    expect(s.inPeriod).toBe(false)
    expect(s.phaseLabel).toBe(PHASE_LABEL.FERTIL)
  })

  it('estado: durante el periodo', () => {
    const s = todayStatus(run([...bleed(1000, 5), ...bleed(1028, 3)], 1030))
    expect(s.inPeriod).toBe(true)
    expect(s.cycleDay).toBe(3)
    expect(s.headline).toBe('Día 3 de tu periodo')
    expect(s.phaseLabel).toBe(PHASE_LABEL.MENSTRUAL)
  })

  it('estado: esperado hoy y con retraso conservan el día real del ciclo', () => {
    const days = [...bleed(1000, 5), ...bleed(1028, 5)]
    const expected = todayStatus(run(days, 1056))
    expect(expected.headline).toBe('Tu periodo podría llegar hoy')
    expect(expected.cycleDay).toBe(29)
    const late = todayStatus(run(days, 1059))
    expect(late.headline).toBe('3 días de retraso')
    expect(late.cycleDay).toBe(32)
    expect(late.phaseLabel).toBe('Periodo retrasado')
    expect(todayStatus(run(days, 1057)).headline).toBe('1 día de retraso')
  })

  it('estado: sin datos o con historial viejo', () => {
    const none = todayStatus(run([], 500))
    expect(none.cycleDay).toBeNull()
    expect(none.headline).toBe('Registra tu periodo para empezar')
    const stale = todayStatus(run(bleed(1000, 5), 1400))
    expect(stale.cycleDay).toBeNull()
    expect(stale.phaseLabel).toBe('Sin registros recientes')
  })

  it('acciones rápidas', () => {
    const days = [...bleed(1000, 5), ...bleed(1028, 3)]
    // Ayer hubo sangrado, hoy sin registro
    expect(quickState(run(days, 1031), null)).toBe('CONTINUE')
    // Hoy ya registrado
    expect(quickState(run(days, 1030), cd(1030, { flow: 3 }))).toBe('LOGGED')
    expect(quickState(run(days, 1040), cd(1040, { flow: 1 }))).toBe('LOGGED')
    // Sin periodo en curso
    expect(quickState(run(days, 1045), cd(1045, { notes: 'x' }))).toBe('START')
    expect(quickState(run([], 1045), null)).toBe('START')
  })

  it('estadísticas de síntomas: conteo y fase', () => {
    const days = [
      ...bleed(1000, 5).map((d, i) => (i < 2 ? { ...d, symptoms: 'colicos' } : d)),
      ...bleed(1028, 5).map((d, i) => (i === 0 ? { ...d, symptoms: 'colicos,fatiga' } : d)),
      cd(1024, { symptoms: 'senos', moods: 'irritable' }),
      cd(1014, { symptoms: 'dolor_ovulacion' }),
    ]
    const a = run(days, 1040)
    const st = symptomStats(days, a)
    expect(st[0].key).toBe('colicos')
    expect(st[0].count).toBe(3)
    expect(st[0].topPhase).toBe('MENSTRUAL')
    expect(st[0].label).toBe('Cólicos')
    expect(st.find((s) => s.key === 'senos')!.topPhase).toBe('LUTEA')
    // La ovulación (día 1014 = 1028 − 14) cuenta como ventana fértil.
    expect(st.find((s) => s.key === 'dolor_ovulacion')!.topPhase).toBe('FERTIL')
    const moods = moodStats(days, a)
    expect(moods.length).toBe(1)
    expect(moods[0].label).toBe('Irritabilidad')
  })

  it('los días de la cuadrícula rellenan los meses vecinos', () => {
    const cells = monthGridDays({ year: 2026, month: 11 })
    expect(cells.length).toBe(42)
    // Lunes 26 de octubre … domingo 1 de noviembre
    expect(cells[0]).toEqual({ day: ymdToEpochDay(2026, 10, 26), inMonth: false })
    expect(cells[6]).toEqual({ day: ymdToEpochDay(2026, 11, 1), inMonth: true })
    expect(cells[41]).toEqual({ day: ymdToEpochDay(2026, 12, 6), inMonth: false })
    expect(cells.every((c, i) => i === 0 || c.day === cells[i - 1].day + 1)).toBe(true)
  })

  it('el texto de rango se acorta dentro del mismo mes', () => {
    const a = ymdToEpochDay(2026, 11, 21)
    expect(rangeText({ first: a, last: a + 6 })).toBe('21 – 27 nov')
    expect(rangeText({ first: a + 7, last: a + 13 })).toBe('28 nov – 4 dic')
  })

  it('plurales y regularidad', () => {
    expect(dias(1)).toBe('1 día')
    expect(dias(28)).toBe('28 días')
    expect(regularityText(run([], 10))).toBe('Sin datos suficientes')
    const reg = run([...bleed(1000, 5), ...bleed(1028, 5), ...bleed(1058, 4), ...bleed(1084, 5)], 1095)
    expect(regularityText(reg)).toBe('Regular')
  })
})
