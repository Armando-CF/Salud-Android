// Fechas y formatos en español (México).
// Convenciones (idénticas a la app de Android):
//  - epochDay: días desde 1970-01-01 en hora LOCAL, para cosas que ocurren "en un día".
//  - timestamp: milisegundos epoch, para cosas con hora.

const DAY_MS = 86_400_000
export const LOCALE = 'es-MX'

/** Fecha local (año, mes 1-12, día) → epochDay. */
export function ymdToEpochDay(y: number, m: number, d: number): number {
  return Math.round(Date.UTC(y, m - 1, d) / DAY_MS)
}

/** epochDay → Date a medianoche LOCAL. */
export function epochDayToDate(epochDay: number): Date {
  const u = new Date(epochDay * DAY_MS)
  return new Date(u.getUTCFullYear(), u.getUTCMonth(), u.getUTCDate())
}

export function dateToEpochDay(d: Date): number {
  return ymdToEpochDay(d.getFullYear(), d.getMonth() + 1, d.getDate())
}

/** Día de hoy como epochDay. */
export const today = (): number => dateToEpochDay(new Date())
export const nowMillis = (): number => Date.now()

/** Milisegundos → epochDay local. */
export const millisToEpochDay = (ms: number): number => dateToEpochDay(new Date(ms))

/** Minutos del día (0..1439) de un timestamp. */
export function millisToMinutes(ms: number): number {
  const d = new Date(ms)
  return d.getHours() * 60 + d.getMinutes()
}

/** epochDay + minutos del día → milisegundos. */
export function epochDayToMillis(epochDay: number, minutesOfDay = 12 * 60): number {
  const d = epochDayToDate(epochDay)
  d.setHours(Math.floor(minutesOfDay / 60), minutesOfDay % 60, 0, 0)
  return d.getTime()
}

/** "2026-11-23" (valor de <input type="date">) ↔ epochDay. */
export function epochDayToIso(epochDay: number): string {
  const u = new Date(epochDay * DAY_MS)
  return `${u.getUTCFullYear()}-${String(u.getUTCMonth() + 1).padStart(2, '0')}-${String(u.getUTCDate()).padStart(2, '0')}`
}
export function isoToEpochDay(iso: string): number | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso.trim())
  if (!m) return null
  return ymdToEpochDay(+m[1], +m[2], +m[3])
}

/** "08:30" → 510 (minutos del día) o null. */
export function parseHm(s: string): number | null {
  const m = /^(\d{1,2}):(\d{2})$/.exec(s.trim())
  if (!m) return null
  const h = +m[1], mi = +m[2]
  if (h > 23 || mi > 59) return null
  return h * 60 + mi
}
/** 510 → "08:30". */
export const formatHm = (min: number): string => `${String(Math.floor(min / 60)).padStart(2, '0')}:${String(min % 60).padStart(2, '0')}`

/** Texto de un campo numérico → número (acepta coma o punto), o null. */
export function toNumber(s: string): number | null {
  const t = s.trim().replace(',', '.')
  if (t === '' || !/^-?\d*\.?\d+$|^-?\d+\.?$/.test(t)) return null
  const n = Number(t)
  return Number.isFinite(n) ? n : null
}

const cap = (s: string) => s.charAt(0).toUpperCase() + s.slice(1)
const noDots = (s: string) => s.replace(/\./g, '')
const f = (opts: Intl.DateTimeFormatOptions) => new Intl.DateTimeFormat(LOCALE, opts)
const fMedium = f({ day: 'numeric', month: 'short', year: 'numeric' })
const fShort = f({ day: 'numeric', month: 'short' })
const fLong = f({ weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })
const fWeekday = f({ weekday: 'short', day: 'numeric', month: 'short' })
const fMonthYear = f({ month: 'long', year: 'numeric' })

export const Fmt = {
  /** "23 nov 2026" */
  date: (epochDay: number): string => noDots(fMedium.format(epochDayToDate(epochDay))).replace(/ de /g, ' '),
  /** "23 nov" */
  dateShort: (epochDay: number): string => noDots(fShort.format(epochDayToDate(epochDay))).replace(' de ', ' '),
  /** "lun 23 nov" */
  dateWeekday: (epochDay: number): string => noDots(fWeekday.format(epochDayToDate(epochDay))).replace(',', '').replace(' de ', ' '),
  /** "Lunes 23 de noviembre de 2026" */
  dateLong: (epochDay: number): string => cap(fLong.format(epochDayToDate(epochDay)).replace(',', '')),
  /** "Noviembre 2026" */
  monthYear: (d: Date): string => cap(fMonthYear.format(d).replace(' de ', ' ')),
  /** "Hoy", "Ayer", "Mañana" o "23 nov 2026". */
  relativeDay(epochDay: number): string {
    const d = epochDay - today()
    return d === 0 ? 'Hoy' : d === -1 ? 'Ayer' : d === 1 ? 'Mañana' : Fmt.date(epochDay)
  },
  /** "14:05" */
  time: (ms: number): string => formatHm(millisToMinutes(ms)),
  /** "23 nov 2026, 14:05" */
  dateTime: (ms: number): string => `${Fmt.date(millisToEpochDay(ms))}, ${Fmt.time(ms)}`,
  /** "Hoy, 14:05" */
  relativeDateTime: (ms: number): string => `${Fmt.relativeDay(millisToEpochDay(ms))}, ${Fmt.time(ms)}`,
  /** "2026-11-23" */
  isoDate: epochDayToIso,
  /** "2026-11-23 14:05" */
  isoDateTime: (ms: number): string => `${epochDayToIso(millisToEpochDay(ms))} ${Fmt.time(ms)}`,
  /** Número con hasta `decimals` decimales, sin ceros sobrantes: 72.50 → "72.5", 80.0 → "80". */
  num(value: number, decimals = 1): string {
    // Redondeo "mitad hacia arriba" como en la app de Android (0.35 → "0.4"), sin los empates binarios de toFixed.
    const f = 10 ** decimals
    const s = (Math.round(Number((Math.abs(value) * f).toPrecision(12))) / f * Math.sign(value || 1)).toFixed(decimals)
    return s.includes('.') ? s.replace(/0+$/, '').replace(/\.$/, '') : s
  },
  /** Con signo: "+1.2" / "−0.8" / "0". */
  signed(value: number, decimals = 1): string {
    const s = Fmt.num(Math.abs(value), decimals)
    return s === '0' ? '0' : value > 0 ? `+${s}` : `−${s}`
  },
  /** "hoy", "mañana", "en 5 días", "hace 3 días". */
  daysFromNow(epochDay: number): string {
    const d = epochDay - today()
    return d === 0 ? 'hoy' : d === 1 ? 'mañana' : d === -1 ? 'ayer' : d > 0 ? `en ${d} días` : `hace ${-d} días`
  },
  /** Edad en años cumplidos. */
  age(birthEpochDay: number, at: number = today()): number {
    const b = epochDayToDate(birthEpochDay), t = epochDayToDate(at)
    let years = t.getFullYear() - b.getFullYear()
    if (t.getMonth() < b.getMonth() || (t.getMonth() === b.getMonth() && t.getDate() < b.getDate())) years--
    return Math.max(0, years)
  },
  /** Edad legible: "8 años", "3 años y 2 meses" (detallado), "5 meses". */
  ageText(birthEpochDay: number, detailed = false, at: number = today()): string {
    const b = epochDayToDate(birthEpochDay), t = epochDayToDate(at)
    let months = (t.getFullYear() - b.getFullYear()) * 12 + (t.getMonth() - b.getMonth())
    if (t.getDate() < b.getDate()) months--
    months = Math.max(0, months)
    const y = Math.floor(months / 12), m = months % 12
    const ys = `${y} ${y === 1 ? 'año' : 'años'}`, ms = `${m} ${m === 1 ? 'mes' : 'meses'}`
    if (y === 0) return ms
    return detailed && m > 0 ? `${ys} y ${ms}` : ys
  },
  /** 95 → "1 h 35 min". */
  duration: (min: number): string => (min < 60 ? `${min} min` : min % 60 === 0 ? `${min / 60} h` : `${Math.floor(min / 60)} h ${min % 60} min`),
}

/** Quita acentos y pasa a minúsculas (para comparar nombres). */
export const normalize = (s: string): string => s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim()
