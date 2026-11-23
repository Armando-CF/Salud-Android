// Lógica pura del módulo de vacunas (portada de VaccineLogic.kt de la app de Android).
import type { Vaccination } from '../../data/types'
import { epochDayToDate, ymdToEpochDay } from '../../lib/dates'
import { normalizeName } from './catalog'

/** Situación de una próxima aplicación pendiente. */
export type DueStatus = 'VENCIDA' | 'PROXIMA' | 'PROGRAMADA'
export const DUE_STATUS_LABEL: Record<DueStatus, string> = { VENCIDA: 'Vencida', PROXIMA: 'Próxima', PROGRAMADA: 'Programada' }

/**
 * Próxima aplicación pendiente: sale del campo `nextDueEpochDay` de un registro que aún no ha sido
 * "cumplido" por un registro posterior con el mismo nombre.
 */
export interface DueItem {
  /** Registro del que proviene la próxima fecha (la última aplicación conocida). */
  vaccination: Vaccination
  dueDay: number
  overdue: boolean
}

/** Una fecha cuenta como "próxima" cuando faltan estos días o menos. */
export const SOON_DAYS = 30

export function dueStatus(dueDay: number, today: number): DueStatus {
  return dueDay < today ? 'VENCIDA' : dueDay <= today + SOON_DAYS ? 'PROXIMA' : 'PROGRAMADA'
}

/** ¿Son "la misma vacuna"? Mismo tipo y mismo nombre sin distinguir mayúsculas ni acentos. */
export const sameVaccine = (a: Vaccination, b: Vaccination): boolean => a.type === b.type && normalizeName(a.name) === normalizeName(b.name)

/** ¿`b` se aplicó después de `a`? (mismo día: gana el id mayor) */
const isLater = (b: Vaccination, a: Vaccination): boolean => b.epochDay > a.epochDay || (b.epochDay === a.epochDay && (b.id ?? 0) > (a.id ?? 0))

/**
 * ¿La próxima fecha de `v` ya quedó atendida? Sí cuando existe un registro posterior de la misma vacuna que
 * (a) se aplicó en esa fecha o después —con un margen por si se adelantó— o (b) trae su propia próxima fecha
 * (ese registro es el que lleva ahora el calendario).
 */
export function isFulfilled(v: Vaccination, all: readonly Vaccination[]): boolean {
  const due = v.nextDueEpochDay
  if (due == null) return true
  const tolerance = Math.max(SOON_DAYS, Math.trunc((due - v.epochDay) / 4))
  return all.some((w) => w.id !== v.id && sameVaccine(v, w) && isLater(w, v) && (w.epochDay >= due - tolerance || w.nextDueEpochDay != null))
}

/**
 * Próximas aplicaciones pendientes (vencidas incluidas), de la más antigua a la más lejana.
 * API para la pantalla de inicio.
 */
export function upcoming(list: readonly Vaccination[], today: number): DueItem[] {
  return list
    .filter((v) => v.nextDueEpochDay != null && !isFulfilled(v, list))
    .map((v) => ({ vaccination: v, dueDay: v.nextDueEpochDay as number, overdue: (v.nextDueEpochDay as number) < today }))
    .sort((a, b) => a.dueDay - b.dueDay || a.vaccination.name.toLowerCase().localeCompare(b.vaccination.name.toLowerCase()))
}

/** Solo vencidas y las que tocan en `withinDays` días o menos. */
export function dueSoon(list: readonly Vaccination[], today: number, withinDays: number = SOON_DAYS): DueItem[] {
  return upcoming(list, today).filter((i) => i.dueDay <= today + withinDays)
}

/** Dosis que seguiría a `dose` ("1.ª dosis" → "2.ª dosis"; "3.ª dosis" → "Refuerzo"). */
export function nextDose(dose: string): string {
  const d = dose.trim()
  if (!d) return ''
  const m = /^(\d+)/.exec(d)
  if (m && /dosis/i.test(d)) { const n = Number(m[1]); return n >= 3 ? 'Refuerzo' : `${n + 1}.ª dosis` }
  if (d.toLowerCase() === 'dosis única') return 'Refuerzo'
  return d
}

/** Fecha + meses de calendario (31 ene + 1 mes = 28/29 feb). */
export function plusMonths(epochDay: number, months: number): number {
  const d = epochDayToDate(epochDay)
  const total = d.getFullYear() * 12 + d.getMonth() + months
  const y = Math.floor(total / 12)
  const m = ((total % 12) + 12) % 12
  const lastDay = new Date(y, m + 1, 0).getDate()
  return ymdToEpochDay(y, m + 1, Math.min(d.getDate(), lastDay))
}

/** Fecha + intervalo del catálogo: los múltiplos de 30/365 días se suman como meses/años de calendario. */
export function plusInterval(epochDay: number, days: number): number {
  if (days >= 365 && days % 365 === 0) return plusMonths(epochDay, (days / 365) * 12)
  if (days >= 30 && days % 30 === 0) return plusMonths(epochDay, days / 30)
  return epochDay + days
}

/** "Vencida hace 12 días", "Toca hoy", "En 5 días". */
export function dueText(dueDay: number, today: number): string {
  const d = dueDay - today
  if (d === 0) return 'Toca hoy'
  if (d === 1) return 'Toca mañana'
  if (d === -1) return 'Venció ayer'
  if (d < 0) return `Vencida hace ${-d} días`
  if (d < 60) return `En ${d} días`
  if (d < 365) return `En ${Math.round(d / 30.4)} meses`
  const years = d / 365.25
  return years < 1.5 ? 'En 1 año' : `En ${Math.round(years)} años`
}

export const VaccineLogic = { SOON_DAYS, status: dueStatus, sameVaccine, isFulfilled, upcoming, dueSoon, nextDose, plusMonths, plusInterval, dueText }
