import { describe, expect, it } from 'vitest'
import { Fmt, epochDayToIso, epochDayToMillis, formatHm, isoToEpochDay, millisToEpochDay, parseHm, toNumber, ymdToEpochDay } from './dates'

describe('fechas', () => {
  it('convierte entre fecha ISO y epochDay', () => {
    const d = ymdToEpochDay(2026, 11, 23)
    expect(epochDayToIso(d)).toBe('2026-11-23')
    expect(isoToEpochDay('2026-11-23')).toBe(d)
    expect(isoToEpochDay('1970-01-01')).toBe(0)
    expect(isoToEpochDay('no')).toBeNull()
  })
  it('ida y vuelta entre epochDay y milisegundos locales', () => {
    const d = ymdToEpochDay(2026, 3, 8)
    expect(millisToEpochDay(epochDayToMillis(d, 0))).toBe(d)
    expect(millisToEpochDay(epochDayToMillis(d, 23 * 60 + 59))).toBe(d)
  })
  it('formatea en español', () => {
    const d = ymdToEpochDay(2026, 11, 23)
    expect(Fmt.date(d)).toBe('23 nov 2026')
    expect(Fmt.dateWeekday(d)).toBe('lun 23 nov')
    expect(Fmt.dateShort(d)).toBe('23 nov')
    expect(Fmt.dateLong(d)).toBe('Lunes 23 de noviembre de 2026')
    expect(Fmt.num(72.5)).toBe('72.5')
    expect(Fmt.num(80)).toBe('80')
    expect(Fmt.signed(-0.8)).toBe('−0.8')
    expect(Fmt.duration(95)).toBe('1 h 35 min')
  })
  it('horas y números', () => {
    expect(parseHm('08:30')).toBe(510)
    expect(formatHm(510)).toBe('08:30')
    expect(parseHm('25:00')).toBeNull()
    expect(toNumber('72,5')).toBe(72.5)
    expect(toNumber('abc')).toBeNull()
    expect(toNumber('')).toBeNull()
  })
  it('edad', () => {
    expect(Fmt.age(ymdToEpochDay(2019, 8, 15), ymdToEpochDay(2026, 11, 23))).toBe(7)
    expect(Fmt.ageText(ymdToEpochDay(2023, 9, 20), true, ymdToEpochDay(2026, 11, 23))).toBe('3 años y 2 meses')
  })
})
