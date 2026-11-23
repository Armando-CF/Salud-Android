// Lógica pura de horarios de medicamentos (portada de MedSchedule.kt de la app de Android).
import type { MedIntake, Medication } from '../../data/types'
import { epochDayToMillis, formatHm, millisToEpochDay, parseHm } from '../../lib/dates'

export const TAKEN = 'taken'
export const SKIPPED = 'skipped'
export const ALL_DAYS = 127

/**
 * Una toma programada de un día: el medicamento, su hora ("HH:mm" y en minutos del día)
 * y la toma registrada para ese horario (null = pendiente).
 */
export interface DoseItem {
  med: Medication
  /** Hora programada "HH:mm". */
  slot: string
  /** La misma hora en minutos del día (0..1439). */
  minutes: number
  /** Registro de ese horario, si ya se marcó como tomada u omitida. */
  intake: MedIntake | null
  taken: boolean
  skipped: boolean
  pending: boolean
}

function doseItem(med: Medication, slot: string, minutes: number, intake: MedIntake | null): DoseItem {
  return { med, slot, minutes, intake, taken: intake?.status === TAKEN, skipped: intake?.status === SKIPPED, pending: intake === null }
}

/** Tomadas / programadas en un rango. */
export interface Adherence {
  taken: number
  scheduled: number
  /** 0..100, o null si no había nada programado. */
  percent: number | null
}

const adherenceOf = (taken: number, scheduled: number): Adherence => ({
  taken,
  scheduled,
  percent: scheduled <= 0 ? null : Math.min(100, Math.max(0, Math.round((taken * 100) / scheduled))),
})

/** "8:00, 20:00" → [480, 1200] (ignora valores inválidos y repetidos). */
export function parseTimes(csv: string): number[] {
  const out: number[] = []
  for (const part of csv.split(',')) {
    const m = parseHm(part)
    if (m !== null && !out.includes(m)) out.push(m)
  }
  return out.sort((a, b) => a - b)
}

/** [480, 1200] → "08:00,20:00". */
export const joinTimes = (minutes: Iterable<number>): string => [...new Set(minutes)].sort((a, b) => a - b).map(formatHm).join(',')

/** Horarios válidos del medicamento, normalizados a "HH:mm", sin repetir y ordenados. */
export const slotsOf = (med: Medication): { slot: string; minutes: number }[] => parseTimes(med.times).map((m) => ({ slot: formatHm(m), minutes: m }))

/** Sin horarios = se toma "según sea necesario". */
export const isAsNeeded = (med: Medication): boolean => parseTimes(med.times).length === 0

/** Índice del día de la semana de un epochDay: 0 = lunes … 6 = domingo (1970-01-01 fue jueves). */
export const weekdayIndex = (epochDay: number): number => (((epochDay + 3) % 7) + 7) % 7

/** ¿El día de la semana de `epochDay` está en la máscara? (bit 0 = lunes … bit 6 = domingo). */
export const dayInMask = (daysMask: number, epochDay: number): boolean => (daysMask & (1 << weekdayIndex(epochDay))) !== 0

/** ¿Hay tomas programadas ese día? (no mira `active`). */
export function isScheduledOn(med: Medication, epochDay: number): boolean {
  if (epochDay < med.startEpochDay) return false
  if (med.endEpochDay != null && epochDay > med.endEpochDay) return false
  return dayInMask(med.daysMask, epochDay)
}

/** ¿El tratamiento ya terminó (fecha de fin anterior a hoy)? */
export const isFinished = (med: Medication, today: number): boolean => med.endEpochDay != null && med.endEpochDay < today

/**
 * Tomas programadas de `epochDay`, ordenadas por hora.
 * Respeta `active`, fechas de inicio/fin, días de la semana (bit 0 = lunes) y los horarios CSV.
 * Los medicamentos "según sea necesario" (sin horarios) no generan tomas.
 */
export function dosesForDay(meds: readonly Medication[], intakes: readonly MedIntake[], epochDay: number): DoseItem[] {
  const byKey = new Map<string, MedIntake>()
  for (const i of intakes) {
    if (i.epochDay !== epochDay || !i.slot) continue
    const k = `${i.medicationId}|${i.slot}`
    const prev = byKey.get(k)
    if (!prev || i.timestamp >= prev.timestamp) byKey.set(k, i)
  }
  const out: DoseItem[] = []
  for (const m of meds) {
    if (!m.active || !isScheduledOn(m, epochDay)) continue
    for (const { slot, minutes } of slotsOf(m)) out.push(doseItem(m, slot, minutes, byKey.get(`${m.id}|${slot}`) ?? null))
  }
  return out.sort((a, b) => a.minutes - b.minutes || a.med.name.toLowerCase().localeCompare(b.med.name.toLowerCase()))
}

/**
 * Adherencia de un medicamento entre `fromDay` y `toDay` (inclusive).
 * Cuenta como programadas las tomas cuya hora ya pasó (`nowDay`, `nowMinutes`) o que ya tienen registro;
 * como tomadas, las registradas con estado "taken". No mira `active`.
 */
export function adherence(
  med: Medication,
  intakes: readonly MedIntake[],
  fromDay: number,
  toDay: number,
  nowDay: number = toDay,
  nowMinutes: number = 24 * 60,
): Adherence {
  const slots = slotsOf(med)
  if (!slots.length) return adherenceOf(0, 0)
  const status = new Map<string, string>()
  for (const i of [...intakes].sort((a, b) => a.timestamp - b.timestamp)) {
    if (i.medicationId === med.id && i.slot) status.set(`${i.epochDay}|${i.slot}`, i.status)
  }
  let scheduled = 0
  let taken = 0
  const first = Math.max(fromDay, med.startEpochDay)
  const last = Math.min(toDay, med.endEpochDay ?? Number.MAX_SAFE_INTEGER)
  for (let d = first; d <= last; d++) {
    if (!dayInMask(med.daysMask, d)) continue
    for (const { slot, minutes } of slots) {
      const st = status.get(`${d}|${slot}`)
      const due = d < nowDay || (d === nowDay && minutes <= nowMinutes)
      if (due || st !== undefined) scheduled++
      if (st === TAKEN) taken++
    }
  }
  return adherenceOf(taken, scheduled)
}

/**
 * Adherencia de los últimos `days` días contando solo desde el primer registro del medicamento
 * (para no penalizar los días anteriores a empezar a usar la app). Sin registros → 0 / 0.
 */
export function recentAdherence(med: Medication, intakes: readonly MedIntake[], days: number, today: number, nowMinutes: number): Adherence {
  const own = intakes.filter((i) => i.medicationId === med.id && i.slot)
  if (!own.length) return adherenceOf(0, 0)
  const firstRecord = Math.min(...own.map((i) => i.epochDay))
  return adherence(med, intakes, Math.max(today - days + 1, firstRecord), today, today, nowMinutes)
}

/**
 * Próxima toma programada y aún sin registrar posterior a `afterMillis`, entre los medicamentos activos.
 * Devuelve el instante y la toma, o null si no hay ninguna en los próximos días.
 */
export function nextDose(meds: readonly Medication[], intakes: readonly MedIntake[], afterMillis: number): { at: number; dose: DoseItem } | null {
  const registered = new Set<string>()
  for (const i of intakes) if (i.slot) registered.add(`${i.medicationId}|${i.epochDay}|${i.slot}`)
  const afterDay = millisToEpochDay(afterMillis)
  let best: { at: number; dose: DoseItem } | null = null
  for (const m of meds) {
    if (!m.active || (m.daysMask & ALL_DAYS) === 0) continue
    const slots = slotsOf(m)
    if (!slots.length) continue
    const first = Math.max(afterDay, m.startEpochDay)
    search: for (let d = first; d <= first + 7; d++) {
      if (m.endEpochDay != null && d > m.endEpochDay) break
      if (!dayInMask(m.daysMask, d)) continue
      for (const { slot, minutes } of slots) {
        const t = epochDayToMillis(d, minutes)
        if (t <= afterMillis || registered.has(`${m.id}|${d}|${slot}`)) continue
        if (!best || t < best.at) best = { at: t, dose: doseItem(m, slot, minutes, null) }
        break search
      }
    }
  }
  return best
}

const DAY_NAMES = ['lun', 'mar', 'mié', 'jue', 'vie', 'sáb', 'dom']
/** Letras de los días para chips (índice 0 = lunes). */
export const WEEK_LETTERS = ['L', 'M', 'M', 'J', 'V', 'S', 'D']
export const DAY_FULL_NAMES = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo']

/** "Todos los días", "Lunes a viernes", "lun, mié, vie"… */
export function daysSummary(daysMask: number): string {
  const m = daysMask & ALL_DAYS
  if (m === ALL_DAYS) return 'Todos los días'
  if (m === 0b0011111) return 'Lunes a viernes'
  if (m === 0b1100000) return 'Fines de semana'
  if (m === 0) return 'Ningún día'
  return [0, 1, 2, 3, 4, 5, 6].filter((i) => (m & (1 << i)) !== 0).map((i) => DAY_NAMES[i]).join(', ')
}

/** "08:00, 20:00 · Todos los días" o "Según sea necesario". */
export function scheduleSummary(med: Medication): string {
  const s = slotsOf(med)
  if (!s.length) return 'Según sea necesario'
  return `${s.map((x) => x.slot).join(', ')} · ${daysSummary(med.daysMask)}`
}
