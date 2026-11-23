import { describe, expect, it } from 'vitest'
import type { Appointment, Medication, Vaccination } from '../../data/types'
import { epochDayToMillis, ymdToEpochDay } from '../../lib/dates'
import { appointmentEvent, buildIcs, icsDate, icsEscape, icsFold, icsLocalDateTime, icsUtcStamp, medicationEvents, vaccinationEvent } from './ics'

const monday = ymdToEpochDay(2026, 11, 2)
const bytes = (s: string) => new TextEncoder().encode(s).length

const med = (o: Partial<Medication> = {}): Medication => ({
  id: 7, name: 'Loratadina', dose: '10 mg', instructions: 'Con el desayuno', times: '08:00,20:00', daysMask: 127, startEpochDay: monday - 10, endEpochDay: null, active: true, remind: true, notes: '', ...o,
})

describe('iCalendar', () => {
  it('formatea fechas locales flotantes y sellos UTC', () => {
    expect(icsDate(monday)).toBe('20261102')
    expect(icsLocalDateTime(monday, 10 * 60 + 30)).toBe('20261102T103000')
    // pasar de medianoche avanza el día
    expect(icsLocalDateTime(monday, 23 * 60 + 50 + 30)).toBe('20261103T002000')
    expect(icsUtcStamp(Date.UTC(2026, 10, 2, 16, 5, 9))).toBe('20261102T160509Z')
    expect(icsLocalDateTime(monday, 600)).not.toMatch(/Z$/)
  })

  it('escapa texto', () => {
    expect(icsEscape('Dra. Rivera; consultorio 2, piso 3\nLlevar estudios \\ cartilla')).toBe('Dra. Rivera\\; consultorio 2\\, piso 3\\nLlevar estudios \\\\ cartilla')
    expect(icsEscape('a\r\nb')).toBe('a\\nb')
  })

  it('pliega líneas a 75 octetos sin partir caracteres', () => {
    const long = 'DESCRIPTION:' + 'Revisión con análisis de sangre, niño ñandú — '.repeat(6) + '🐶🐱'
    const folded = icsFold(long)
    const parts = folded.split('\r\n')
    expect(parts.length).toBeGreaterThan(2)
    for (const p of parts) expect(bytes(p)).toBeLessThanOrEqual(75)
    for (const p of parts.slice(1)) expect(p.startsWith(' ')).toBe(true)
    // al desplegar (quitar CRLF + espacio) se recupera el texto original, sin caracteres rotos
    expect(folded.replace(/\r\n /g, '')).toBe(long)
    expect(icsFold('SUMMARY:corta')).toBe('SUMMARY:corta')
  })

  it('cita: evento con hora local y avisos 1 día y 2 horas antes', () => {
    const a: Appointment = {
      id: 3, timestamp: epochDayToMillis(monday + 6, 10 * 60 + 30), title: 'Revisión anual', doctor: 'Dra. Rivera', specialty: 'Medicina general',
      place: 'Consultorio 204, Hospital Ángeles', notes: 'Llevar estudios', remind: true, done: false,
    }
    const ics = buildIcs([appointmentEvent(a, 1, 'Cita médica')], Date.UTC(2026, 10, 2, 12))
    expect(ics.startsWith('BEGIN:VCALENDAR\r\nVERSION:2.0\r\n')).toBe(true)
    expect(ics.endsWith('END:VCALENDAR\r\n')).toBe(true)
    expect(ics).toContain('UID:savia-cita-p1-3@savia.app')
    expect(ics).toContain('DTSTAMP:20261102T120000Z')
    expect(ics).toContain('DTSTART:20261108T103000\r\n')
    expect(ics).toContain('DTEND:20261108T113000\r\n')
    expect(ics).toContain('SUMMARY:Revisión anual')
    expect(ics).toContain('LOCATION:Consultorio 204\\, Hospital Ángeles')
    expect(ics).toContain('DESCRIPTION:Dra. Rivera · Medicina general\\nLlevar estudios')
    expect(ics).toContain('TRIGGER:-P1D')
    expect(ics).toContain('TRIGGER:-PT2H')
    expect(ics.match(/BEGIN:VALARM/g)).toHaveLength(2)
    expect(ics).not.toMatch(/TZID|DTSTART:[0-9T]+Z/)
    // sin título usa el genérico, y el nombre del perfil va al frente
    const e = appointmentEvent({ ...a, title: ' ' }, 3, 'Cita veterinaria', 'Firulais')
    expect(e.summary).toBe('Firulais · Cita veterinaria')
    expect(e.uid).toBe('savia-cita-p3-3@savia.app')
  })

  it('vacuna: evento de todo el día con avisos a las 09:00 del día y 7 días antes', () => {
    const v: Vaccination = {
      id: 9, epochDay: monday - 360, type: 'vacuna', name: 'Rabia', dose: 'Anual', nextDueEpochDay: monday + 5, place: 'Veterinaria Huellitas', lot: '', notes: '', remind: true,
    }
    const e = vaccinationEvent(v, 3, 'Firulais')!
    const ics = buildIcs([e])
    expect(ics).toContain('DTSTART;VALUE=DATE:20261107')
    expect(ics).toContain('DTEND;VALUE=DATE:20261108')
    expect(ics).toContain('SUMMARY:Firulais · Toca vacuna: Rabia')
    expect(ics).toContain('TRIGGER:PT9H')
    expect(ics).toContain('TRIGGER:-P6DT15H')
    expect(e.uid).toBe('savia-vacuna-p3-9@savia.app')
    expect(vaccinationEvent({ ...v, type: 'desp_interna', name: 'Desparasitante' }, 3)!.summary).toBe('Toca desparasitación: Desparasitante')
    expect(vaccinationEvent({ ...v, nextDueEpochDay: null }, 3)).toBeNull()
  })

  it('medicamento: un evento recurrente por horario', () => {
    const events = medicationEvents(med(), 1, monday)
    expect(events).toHaveLength(2)
    expect(events.map((e) => e.minutes)).toEqual([480, 1200])
    expect(events[0].day).toBe(monday) // empieza hoy, no en la fecha de inicio pasada
    expect(events[0].rrule).toBe('FREQ=DAILY')
    expect(events[0].uid).toBe('savia-med-p1-7-0800@savia.app')
    const ics = buildIcs(events)
    expect(ics.match(/BEGIN:VEVENT/g)).toHaveLength(2)
    expect(ics).toContain('RRULE:FREQ=DAILY')
    expect(ics).toContain('DTSTART:20261102T080000')
    expect(ics).toContain('SUMMARY:Tomar Loratadina · 10 mg')
    expect(ics).toContain('TRIGGER:PT0M')
  })

  it('medicamento: días de la semana, fecha de fin y casos sin eventos', () => {
    // lunes, miércoles y viernes, hasta el día 20; hoy es martes → empieza el miércoles
    const e = medicationEvents(med({ times: '09:00', daysMask: 0b0010101, endEpochDay: monday + 18 }), 2, monday + 1)[0]
    expect(e.day).toBe(monday + 2)
    expect(e.rrule).toBe('FREQ=WEEKLY;BYDAY=MO,WE,FR;UNTIL=20261120T235959')
    // empieza en el futuro
    expect(medicationEvents(med({ startEpochDay: monday + 30 }), 1, monday)[0].day).toBe(monday + 30)
    // sin horario, sin días o ya terminado
    expect(medicationEvents(med({ times: '' }), 1, monday)).toEqual([])
    expect(medicationEvents(med({ daysMask: 0 }), 1, monday)).toEqual([])
    expect(medicationEvents(med({ endEpochDay: monday - 1 }), 1, monday)).toEqual([])
  })

  it('el UID es estable entre exportaciones', () => {
    const a = medicationEvents(med(), 1, monday).map((e) => e.uid)
    const b = medicationEvents(med({ name: 'Otro nombre' }), 1, monday + 3).map((e) => e.uid)
    expect(a).toEqual(b)
  })
})
