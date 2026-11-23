import { describe, expect, it } from 'vitest'
import type { MedIntake, Medication } from '../../data/types'
import { epochDayToMillis, ymdToEpochDay } from '../../lib/dates'
import {
  adherence, daysSummary, dosesForDay, isAsNeeded, isFinished, joinTimes, nextDose, parseTimes, recentAdherence, scheduleSummary, weekdayIndex,
} from './schedule'

/** Lunes 2 de noviembre de 2026. */
const monday = ymdToEpochDay(2026, 11, 2)

const med = (id: number, times: string, o: Partial<Medication> = {}): Medication => ({
  id, name: `Med${id}`, dose: '', instructions: '', times, daysMask: 127, startEpochDay: monday - 30, endEpochDay: null, active: true, remind: true, notes: '', ...o,
})
const intake = (medicationId: number, epochDay: number, slot: string, status = 'taken', timestamp = 0): MedIntake => ({ medicationId, epochDay, slot, timestamp, status })

describe('horarios de medicamentos', () => {
  it('el lunes es el índice 0', () => {
    expect(weekdayIndex(monday)).toBe(0)
    expect(weekdayIndex(monday + 6)).toBe(6)
    expect(weekdayIndex(0)).toBe(3) // 1970-01-01, jueves
  })

  it('analiza y normaliza horas', () => {
    expect(parseTimes('20:00, 8:00,xx,08:00,25:00')).toEqual([480, 1200])
    expect(joinTimes([1200, 480, 480])).toBe('08:00,20:00')
    expect(isAsNeeded(med(1, ''))).toBe(true)
    expect(isAsNeeded(med(1, '08:00'))).toBe(false)
  })

  it('ordena las tomas y las empareja con sus registros', () => {
    const meds = [med(1, '20:00,08:00', { name: 'B' }), med(2, '08:00', { name: 'A' }), med(3, '')]
    const intakes = [intake(1, monday, '08:00'), intake(2, monday, '08:00', 'skipped'), intake(1, monday - 1, '20:00'), intake(3, monday, '')]
    const doses = dosesForDay(meds, intakes, monday)
    expect(doses.map((d) => `${d.med.name} ${d.slot}`)).toEqual(['A 08:00', 'B 08:00', 'B 20:00'])
    expect(doses[0].skipped).toBe(true)
    expect(doses[1].taken).toBe(true)
    expect(doses[2].pending).toBe(true)
    expect(doses[2].minutes).toBe(1200)
  })

  it('respeta la máscara de días con el bit 0 como lunes', () => {
    const m = med(1, '09:00', { daysMask: 0b0010101 })
    expect(dosesForDay([m], [], monday)).toHaveLength(1)
    expect(dosesForDay([m], [], monday + 1)).toHaveLength(0) // martes
    expect(dosesForDay([m], [], monday + 2)).toHaveLength(1) // miércoles
    expect(dosesForDay([m], [], monday + 5)).toHaveLength(0) // sábado
    const sundayOnly = med(2, '09:00', { daysMask: 1 << 6 })
    expect(dosesForDay([sundayOnly], [], monday + 6)).toHaveLength(1)
    expect(dosesForDay([sundayOnly], [], monday)).toHaveLength(0)
  })

  it('respeta inicio, fin y activo', () => {
    const m = med(1, '09:00', { startEpochDay: monday, endEpochDay: monday + 2 })
    expect(dosesForDay([m], [], monday - 1)).toHaveLength(0)
    expect(dosesForDay([m], [], monday)).toHaveLength(1)
    expect(dosesForDay([m], [], monday + 2)).toHaveLength(1)
    expect(dosesForDay([m], [], monday + 3)).toHaveLength(0)
    expect(dosesForDay([{ ...m, active: false }], [], monday)).toHaveLength(0)
    expect(isFinished(m, monday + 3)).toBe(true)
    expect(isFinished(m, monday + 2)).toBe(false)
  })

  it('la adherencia solo cuenta las tomas que ya tocaban', () => {
    const m = med(1, '08:00,20:00', { startEpochDay: monday })
    const intakes = [
      intake(1, monday, '08:00'), intake(1, monday, '20:00'),
      intake(1, monday + 1, '08:00'), intake(1, monday + 1, '20:00', 'skipped'),
      intake(1, monday + 2, '08:00'),
    ]
    // Miércoles a mediodía: 2 + 2 + 1 tomas ya vencidas; la de las 20:00 de hoy aún no cuenta.
    const a = adherence(m, intakes, monday - 6, monday + 2, monday + 2, 12 * 60)
    expect(a.scheduled).toBe(5)
    expect(a.taken).toBe(4)
    expect(a.percent).toBe(80)
    expect(adherence(med(2, ''), [], monday, monday + 2).percent).toBeNull()
    // Una toma adelantada (registrada antes de su hora) sí cuenta.
    const early = adherence(m, [intake(1, monday, '08:00'), intake(1, monday, '20:00')], monday, monday, monday, 9 * 60)
    expect(early.scheduled).toBe(2)
    expect(early.percent).toBe(100)
  })

  it('la adherencia reciente empieza en el primer registro', () => {
    const m = med(1, '08:00', { startEpochDay: monday - 30 })
    expect(recentAdherence(m, [], 7, monday + 3, 12 * 60).percent).toBeNull()
    // Primer registro el martes; hoy jueves a mediodía: martes ✓, miércoles ✗, jueves ✓.
    const a = recentAdherence(m, [intake(1, monday + 1, '08:00'), intake(1, monday + 3, '08:00')], 7, monday + 3, 12 * 60)
    expect(a.scheduled).toBe(3)
    expect(a.taken).toBe(2)
  })

  it('la próxima toma salta las registradas y los días sin horario', () => {
    const m = med(1, '08:00,20:00')
    const at9 = epochDayToMillis(monday, 9 * 60)
    const next = nextDose([m], [], at9)!
    expect(next.at).toBe(epochDayToMillis(monday, 20 * 60))
    expect(next.dose.slot).toBe('20:00')
    // Si la de las 20:00 ya está registrada, sigue la de mañana a las 08:00.
    expect(nextDose([m], [intake(1, monday, '20:00')], at9)!.at).toBe(epochDayToMillis(monday + 1, 8 * 60))
    // Solo viernes: desde el lunes, toca el viernes.
    expect(nextDose([med(2, '07:30', { daysMask: 1 << 4 })], [], at9)!.at).toBe(epochDayToMillis(monday + 4, 7 * 60 + 30))
    // Tratamiento terminado o según sea necesario: nada.
    expect(nextDose([med(3, '08:00', { endEpochDay: monday - 1 }), med(4, '')], [], at9)).toBeNull()
    // Empieza en el futuro.
    expect(nextDose([med(5, '08:00', { startEpochDay: monday + 20 })], [], at9)!.at).toBe(epochDayToMillis(monday + 20, 8 * 60))
  })

  it('resúmenes', () => {
    expect(daysSummary(127)).toBe('Todos los días')
    expect(daysSummary(0b0011111)).toBe('Lunes a viernes')
    expect(daysSummary(0b0000101)).toBe('lun, mié')
    expect(scheduleSummary(med(1, '20:00,08:00'))).toBe('08:00, 20:00 · Todos los días')
    expect(scheduleSummary(med(1, ''))).toBe('Según sea necesario')
  })
})
