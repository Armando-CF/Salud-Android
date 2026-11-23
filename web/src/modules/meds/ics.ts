// Generador de archivos iCalendar (.ics) — lógica pura.
//
// Una PWA en iPhone no puede programar avisos locales, así que Savia entrega un archivo de calendario:
// la persona lo agrega al Calendario del iPhone y es el Calendario quien avisa a su hora.
//
// Las fechas son LOCALES "flotantes" (sin zona ni Z): el evento ocurre a esa hora del reloj
// dondequiera que esté el teléfono, igual que una toma de medicamento.
import type { Appointment, Medication, Vaccination } from '../../data/types'
import { millisToEpochDay, millisToMinutes } from '../../lib/dates'
import { ALL_DAYS, dayInMask, parseTimes } from './schedule'

export interface IcsAlarm {
  /** Desfase respecto al inicio en formato de duración iCalendar: "-P1D", "-PT2H", "PT9H", "PT0M"… */
  trigger: string
  description: string
}

export interface IcsEvent {
  /** Identificador estable: volver a importar el mismo evento lo actualiza en vez de duplicarlo. */
  uid: string
  summary: string
  description?: string
  location?: string
  /** Día de inicio (epochDay). */
  day: number
  /** Minutos del día de inicio; si se omite, el evento es de todo el día. */
  minutes?: number
  /** Duración en minutos (eventos con hora). Por defecto 30. */
  durationMin?: number
  /** Regla de repetición sin el prefijo "RRULE:", p. ej. "FREQ=DAILY". */
  rrule?: string
  alarms?: IcsAlarm[]
}

const pad = (n: number, w = 2): string => String(n).padStart(w, '0')

/** epochDay → "20261123". */
export function icsDate(epochDay: number): string {
  const u = new Date(epochDay * 86_400_000)
  return `${pad(u.getUTCFullYear(), 4)}${pad(u.getUTCMonth() + 1)}${pad(u.getUTCDate())}`
}

/** epochDay + minutos del día → "20261123T103000" (hora local flotante). Los minutos pueden pasar de medianoche. */
export function icsLocalDateTime(epochDay: number, minutes: number): string {
  const day = epochDay + Math.floor(minutes / 1440)
  const m = ((minutes % 1440) + 1440) % 1440
  return `${icsDate(day)}T${pad(Math.floor(m / 60))}${pad(m % 60)}00`
}

/** Instante → "20261123T163000Z" (UTC), para DTSTAMP. */
export function icsUtcStamp(ms: number): string {
  const d = new Date(ms)
  return `${pad(d.getUTCFullYear(), 4)}${pad(d.getUTCMonth() + 1)}${pad(d.getUTCDate())}T${pad(d.getUTCHours())}${pad(d.getUTCMinutes())}${pad(d.getUTCSeconds())}Z`
}

/** Escapa texto según RFC 5545: barra invertida, punto y coma, coma y saltos de línea. */
export function icsEscape(text: string): string {
  return text.replace(/\\/g, '\\\\').replace(/;/g, '\\;').replace(/,/g, '\\,').replace(/\r\n|\r|\n/g, '\\n')
}

const utf8Length = (ch: string): number => {
  const c = ch.codePointAt(0)!
  return c < 0x80 ? 1 : c < 0x800 ? 2 : c < 0x10000 ? 3 : 4
}

/**
 * Pliega una línea a un máximo de 75 octetos (UTF-8) por renglón, sin partir caracteres;
 * los renglones de continuación empiezan con un espacio (que cuenta para el límite).
 */
export function icsFold(line: string): string {
  const out: string[] = []
  let current = ''
  let bytes = 0
  for (const ch of line) { // itera por puntos de código (no parte emojis)
    const n = utf8Length(ch)
    if (bytes + n > 75) {
      out.push(current)
      current = ' '
      bytes = 1
    }
    current += ch
    bytes += n
  }
  out.push(current)
  return out.join('\r\n')
}

/** Construye el texto completo del archivo .ics (líneas plegadas, CRLF). */
export function buildIcs(events: readonly IcsEvent[], nowMs: number = Date.now()): string {
  const out: string[] = ['BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//Savia//Savia Web//ES', 'CALSCALE:GREGORIAN', 'METHOD:PUBLISH']
  const stamp = icsUtcStamp(nowMs)
  for (const e of events) {
    out.push('BEGIN:VEVENT', `UID:${e.uid}`, `DTSTAMP:${stamp}`)
    if (e.minutes === undefined) {
      out.push(`DTSTART;VALUE=DATE:${icsDate(e.day)}`, `DTEND;VALUE=DATE:${icsDate(e.day + 1)}`)
    } else {
      out.push(`DTSTART:${icsLocalDateTime(e.day, e.minutes)}`, `DTEND:${icsLocalDateTime(e.day, e.minutes + (e.durationMin ?? 30))}`)
    }
    if (e.rrule) out.push(`RRULE:${e.rrule}`)
    out.push(`SUMMARY:${icsEscape(e.summary)}`)
    if (e.description) out.push(`DESCRIPTION:${icsEscape(e.description)}`)
    if (e.location) out.push(`LOCATION:${icsEscape(e.location)}`)
    for (const a of e.alarms ?? []) {
      out.push('BEGIN:VALARM', 'ACTION:DISPLAY', `DESCRIPTION:${icsEscape(a.description)}`, `TRIGGER:${a.trigger}`, 'END:VALARM')
    }
    out.push('END:VEVENT')
  }
  out.push('END:VCALENDAR')
  return out.map(icsFold).join('\r\n') + '\r\n'
}

// ── Eventos de Savia ───────────────────────────────────────────────────────────────────────────

const BYDAY = ['MO', 'TU', 'WE', 'TH', 'FR', 'SA', 'SU']
const joinLines = (...parts: (string | false | null | undefined)[]): string => parts.filter(Boolean).join('\n')

/** Prefijo "Nombre · " cuando conviene decir de quién es (familia o mascota); vacío si no. */
const who = (owner?: string): string => (owner ? `${owner} · ` : '')

/**
 * Cita: evento a su hora (1 hora) con avisos un día antes y dos horas antes.
 * @param owner nombre del perfil si conviene anteponerlo (p. ej. "Firulais").
 */
export function appointmentEvent(a: Appointment, profileId: number, fallbackTitle: string, owner?: string): IcsEvent {
  const title = a.title.trim() || fallbackTitle
  return {
    uid: `savia-cita-p${profileId}-${a.id ?? 'nueva'}@savia.app`,
    summary: who(owner) + title,
    description: joinLines([a.doctor, a.specialty].filter((s) => s.trim()).join(' · '), a.notes.trim()) || undefined,
    location: a.place.trim() || undefined,
    day: millisToEpochDay(a.timestamp),
    minutes: millisToMinutes(a.timestamp),
    durationMin: 60,
    alarms: [
      { trigger: '-P1D', description: `Mañana: ${title}` },
      { trigger: '-PT2H', description: `En 2 horas: ${title}` },
    ],
  }
}

/** Sustantivo según el tipo de registro: "vacuna", "desparasitación" o "aplicación". */
export const vaccineNoun = (type: string): string => (type === 'vacuna' ? 'vacuna' : type.startsWith('desp_') ? 'desparasitación' : 'aplicación')

/**
 * Próxima vacuna o desparasitación: evento de todo el día en la fecha que toca, con avisos
 * a las 09:00 de ese día y 7 días antes (también a las 09:00). Devuelve null si no tiene próxima fecha.
 */
export function vaccinationEvent(v: Vaccination, profileId: number, owner?: string, nextDoseLabel = ''): IcsEvent | null {
  if (v.nextDueEpochDay == null) return null
  const noun = vaccineNoun(v.type)
  const what = v.name.trim() + (nextDoseLabel ? ` (${nextDoseLabel})` : '')
  return {
    uid: `savia-vacuna-p${profileId}-${v.id ?? 'nueva'}@savia.app`,
    summary: `${who(owner)}Toca ${noun}: ${what}`,
    description: joinLines(v.place.trim() && `Lugar de la última aplicación: ${v.place.trim()}`, v.notes.trim()) || undefined,
    location: v.place.trim() || undefined,
    day: v.nextDueEpochDay,
    alarms: [
      // El evento de todo el día empieza a medianoche: +9 h = 09:00 del mismo día.
      { trigger: 'PT9H', description: `Hoy toca ${noun}: ${what}` },
      // 7 días antes a las 09:00 = inicio − 6 días 15 horas.
      { trigger: '-P6DT15H', description: `En 7 días toca ${noun}: ${what}` },
    ],
  }
}

/**
 * Medicamento con horario: un evento recurrente por cada hora de toma (diario, o semanal con los días
 * elegidos), desde hoy o su fecha de inicio —lo que sea posterior— y hasta la fecha de fin si existe.
 * Cada evento avisa a su hora. Sin horarios, sin días o ya terminado → lista vacía.
 */
export function medicationEvents(med: Medication, profileId: number, today: number, owner?: string): IcsEvent[] {
  const times = parseTimes(med.times)
  const mask = med.daysMask & ALL_DAYS
  if (!times.length || mask === 0) return []
  if (med.endEpochDay != null && med.endEpochDay < today) return []
  let first = Math.max(med.startEpochDay, today)
  for (let i = 0; i < 7 && !dayInMask(mask, first); i++) first++
  if (med.endEpochDay != null && first > med.endEpochDay) return []
  let rrule = mask === ALL_DAYS ? 'FREQ=DAILY' : `FREQ=WEEKLY;BYDAY=${BYDAY.filter((_, i) => (mask & (1 << i)) !== 0).join(',')}`
  // UNTIL en hora local flotante, igual que DTSTART.
  if (med.endEpochDay != null) rrule += `;UNTIL=${icsDate(med.endEpochDay)}T235959`
  const label = [med.name.trim(), med.dose.trim()].filter(Boolean).join(' · ')
  return times.map((minutes) => ({
    uid: `savia-med-p${profileId}-${med.id ?? 'nuevo'}-${pad(Math.floor(minutes / 60))}${pad(minutes % 60)}@savia.app`,
    summary: `${who(owner)}Tomar ${label}`,
    description: joinLines(med.instructions.trim(), med.notes.trim()) || undefined,
    day: first,
    minutes,
    durationMin: 10,
    rrule,
    alarms: [{ trigger: 'PT0M', description: `Hora de tomar ${label}` }],
  }))
}
