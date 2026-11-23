import { describe, expect, it } from 'vitest'
import type { ProfileInfo, Vaccination } from '../../data/types'
import { ymdToEpochDay } from '../../lib/dates'
import { VaccineCatalog } from './catalog'
import { VaccineLogic } from './logic'

const today = ymdToEpochDay(2026, 11, 23)

const vac = (id: number, name: string, day: number, next: number | null = null, type = 'vacuna', dose = ''): Vaccination => ({
  id, epochDay: day, type, name, dose, nextDueEpochDay: next, place: '', lot: '', notes: '', remind: true,
})
const prof = (o: Partial<ProfileInfo>): ProfileInfo => ({ id: 1, name: '', kind: 'person', species: '', sex: 'X', relation: '', avatarColor: 0, birthEpochDay: null, ...o })

describe('vacunas: pendientes y próximas', () => {
  it('estado según la distancia a hoy', () => {
    expect(VaccineLogic.status(today - 1, today)).toBe('VENCIDA')
    expect(VaccineLogic.status(today, today)).toBe('PROXIMA')
    expect(VaccineLogic.status(today + 30, today)).toBe('PROXIMA')
    expect(VaccineLogic.status(today + 31, today)).toBe('PROGRAMADA')
  })

  it('las pendientes salen ordenadas y marcan las vencidas', () => {
    const list = [
      vac(1, 'Rabia', today - 400, today - 35),
      vac(2, 'Pipeta', today - 20, today + 10, 'desp_externa'),
      vac(3, 'Múltiple (quíntuple)', today - 100, today + 265),
      vac(4, 'Bordetella', today - 50),
    ]
    const up = VaccineLogic.upcoming(list, today)
    expect(up.map((i) => i.vaccination.id)).toEqual([1, 2, 3])
    expect(up[0].overdue).toBe(true)
    expect(up[1].overdue).toBe(false)
    expect(up[1].dueDay).toBe(today + 10)
    expect(VaccineLogic.dueSoon(list, today).map((i) => i.vaccination.id)).toEqual([1, 2])
  })

  it('un registro posterior con el mismo nombre cumple la fecha', () => {
    // Rabia aplicada hace un año con refuerzo que tocaba hace 5 días; se aplicó hace 3 días (sin próxima fecha).
    const old = vac(1, 'Rabia', today - 370, today - 5)
    const done = vac(2, 'rabia ', today - 3)
    expect(VaccineLogic.isFulfilled(old, [old, done])).toBe(true)
    expect(VaccineLogic.upcoming([old, done], today)).toEqual([])
    // Sin distinguir acentos ni mayúsculas.
    const a = vac(3, 'Múltiple', today - 370, today - 5)
    const b = vac(4, 'MULTIPLE', today - 20)
    expect(VaccineLogic.isFulfilled(a, [a, b])).toBe(true)
    // Otra vacuna distinta, u otro tipo con el mismo nombre, no la cumple.
    expect(VaccineLogic.isFulfilled(old, [old, vac(5, 'Moquillo', today - 3)])).toBe(false)
    expect(VaccineLogic.isFulfilled(old, [old, vac(6, 'Rabia', today - 3, null, 'otro')])).toBe(false)
    // Un registro anterior tampoco.
    expect(VaccineLogic.isFulfilled(old, [old, vac(7, 'Rabia', today - 800)])).toBe(false)
  })

  it('una dosis adelantada de una serie no cumple salvo que lleve el calendario', () => {
    // 1.ª dosis con 2.ª a los 60 días; a los 10 días se registra otra "igual" sin próxima fecha: no cumple (muy pronto).
    const first = vac(1, 'Hepatitis B', today - 30, today + 30)
    const tooEarly = vac(2, 'Hepatitis B', today - 20)
    expect(VaccineLogic.isFulfilled(first, [first, tooEarly])).toBe(false)
    // Pero si el registro posterior trae su propia próxima fecha, es el que lleva el calendario.
    const carries = vac(3, 'Hepatitis B', today - 20, today + 160)
    expect(VaccineLogic.upcoming([first, carries], today).map((i) => i.vaccination.id)).toEqual([3])
    // Adelantarse dentro del margen sí cuenta.
    const annual = vac(4, 'Influenza', today - 350, today + 15)
    const early = vac(5, 'Influenza', today - 2)
    expect(VaccineLogic.isFulfilled(annual, [annual, early])).toBe(true)
  })

  it('los registros sin próxima fecha nunca aparecen', () => {
    expect(VaccineLogic.upcoming([vac(1, 'BCG', today - 3000)], today)).toEqual([])
  })

  it('dosis siguiente', () => {
    expect(VaccineLogic.nextDose('1.ª dosis')).toBe('2.ª dosis')
    expect(VaccineLogic.nextDose('2.ª dosis')).toBe('3.ª dosis')
    expect(VaccineLogic.nextDose('3.ª dosis')).toBe('Refuerzo')
    expect(VaccineLogic.nextDose('Refuerzo')).toBe('Refuerzo')
    expect(VaccineLogic.nextDose('Anual')).toBe('Anual')
    expect(VaccineLogic.nextDose('Dosis única')).toBe('Refuerzo')
    expect(VaccineLogic.nextDose('  ')).toBe('')
    expect(VaccineLogic.nextDose('1 tableta')).toBe('1 tableta')
  })

  it('los intervalos usan meses y años de calendario', () => {
    const jan31 = ymdToEpochDay(2026, 1, 31)
    expect(VaccineLogic.plusMonths(jan31, 1)).toBe(ymdToEpochDay(2026, 2, 28))
    expect(VaccineLogic.plusInterval(jan31, 365)).toBe(ymdToEpochDay(2027, 1, 31))
    expect(VaccineLogic.plusInterval(jan31, 90)).toBe(ymdToEpochDay(2026, 4, 30))
    expect(VaccineLogic.plusInterval(jan31, 21)).toBe(jan31 + 21)
    expect(VaccineLogic.plusInterval(jan31, 3650)).toBe(ymdToEpochDay(2036, 1, 31))
    // año bisiesto y cruce de año
    expect(VaccineLogic.plusMonths(ymdToEpochDay(2027, 11, 30), 3)).toBe(ymdToEpochDay(2028, 2, 29))
  })

  it('textos de vencimiento', () => {
    expect(VaccineLogic.dueText(today, today)).toBe('Toca hoy')
    expect(VaccineLogic.dueText(today + 1, today)).toBe('Toca mañana')
    expect(VaccineLogic.dueText(today - 1, today)).toBe('Venció ayer')
    expect(VaccineLogic.dueText(today - 12, today)).toBe('Vencida hace 12 días')
    expect(VaccineLogic.dueText(today + 5, today)).toBe('En 5 días')
    expect(VaccineLogic.dueText(today + 182, today)).toBe('En 6 meses')
    expect(VaccineLogic.dueText(today + 365, today)).toBe('En 1 año')
    expect(VaccineLogic.dueText(today + 3650, today)).toBe('En 10 años')
  })
})

describe('vacunas: catálogo', () => {
  const dog = prof({ id: 2, name: 'Firulais', kind: 'pet', species: 'perro' })
  const cat = prof({ id: 3, name: 'Garfield', kind: 'pet', species: 'gato' })
  const fish = prof({ id: 4, name: 'Nemo', kind: 'pet', species: 'pez' })
  const adult = prof({ id: 1, name: 'Ana', birthEpochDay: today - 365 * 35 })
  const baby = prof({ id: 5, name: 'Leo', birthEpochDay: today - 200 })

  it('depende del perfil', () => {
    expect(VaccineCatalog.suggestions(dog).some((x) => x.name.startsWith('Múltiple'))).toBe(true)
    expect(VaccineCatalog.suggestions(cat).some((x) => x.name === 'Triple felina')).toBe(true)
    expect(VaccineCatalog.suggestions(cat).some((x) => x.name.startsWith('Múltiple'))).toBe(false)
    expect(VaccineCatalog.suggestions(fish).length).toBeGreaterThan(0)
    // Personas: sin desparasitación de mascotas, y la etapa de su edad va primero.
    expect(VaccineCatalog.suggestions(adult, today).every((x) => x.type !== 'desp_interna' && x.type !== 'desp_externa')).toBe(true)
    expect(VaccineCatalog.suggestions(adult, today)[0].name).toBe('Td (tétanos y difteria)')
    expect(VaccineCatalog.suggestions(baby, today)[0].name).toBe('BCG')
  })

  it('busca sin acentos, por nombre o por lo que cubre', () => {
    expect(VaccineCatalog.filter(dog, 'vacuna', 'multiple').map((x) => x.name)).toEqual(['Múltiple (quíntuple)'])
    expect(VaccineCatalog.filter(dog, 'vacuna', 'moquillo').length).toBeGreaterThanOrEqual(2)
    expect(VaccineCatalog.filter(dog, 'desp_externa', '').some((x) => x.name === 'Pipeta')).toBe(true)
    expect(VaccineCatalog.filter(adult, 'vacuna', 'tetanos', today).length).toBeGreaterThan(0)
  })

  it('intervalos orientativos', () => {
    expect(VaccineCatalog.find(dog, 'rabia')!.intervalDays).toBe(365)
    expect(VaccineCatalog.intervalLabel(VaccineCatalog.find(dog, 'RABIA', 'vacuna')!.intervalDays!)).toBe('cada año')
    expect(VaccineCatalog.intervalLabel(VaccineCatalog.find(dog, 'Desparasitante en tableta')!.intervalDays!)).toBe('cada 3 meses')
    expect(VaccineCatalog.intervalLabel(VaccineCatalog.find(adult, 'Td (tétanos y difteria)', null, today)!.intervalDays!)).toBe('cada 10 años')
    expect(VaccineCatalog.find(adult, 'BCG', null, today)!.intervalDays).toBeNull()
    expect(VaccineCatalog.find(dog, 'Rabia', 'desp_interna')).toBeNull()
    expect(VaccineCatalog.find(adult, 'influenza', null, today)).not.toBeNull()
    expect(VaccineCatalog.inLabel(21)).toBe('en 3 semanas')
    expect(VaccineCatalog.inLabel(180)).toBe('en 6 meses')
    expect(VaccineCatalog.inLabel(365)).toBe('en 1 año')
    expect(VaccineCatalog.intervalLabel(30)).toBe('cada mes')
  })
})
