// Análisis del ciclo para la exportación (ciclos derivados, promedios y día/fase de cada fecha).
// Es un port directo de CycleEngine.kt (Android) reducido a lo que necesitan las tablas exportadas;
// vive aquí para que el módulo de respaldo no dependa del módulo del ciclo.

import type { CycleDay } from '../../data/types'

export const MIN_VALID_CYCLE = 15
export const MAX_VALID_CYCLE = 60
/** Cuántos ciclos recientes se promedian. */
export const RECENT = 6

export interface Period { start: number; end: number }
export interface CycleRecord {
  start: number
  periodLength: number
  /** Días hasta el siguiente periodo; null si es el ciclo en curso. */
  length: number | null
  /** ¿Entra en las estadísticas? */
  valid: boolean
}

export type Phase = 'Menstruación' | 'Fase folicular' | 'Ventana fértil' | 'Ovulación estimada' | 'Fase lútea' | 'Sin datos'

export interface DayInfo { cycleDay: number | null; phase: Phase }

export interface CycleAnalysis {
  periods: Period[]
  cycles: CycleRecord[]
  cycleLength: number
  periodLength: number
  lutealLength: number
  avgCycle: number | null
  avgPeriod: number | null
  shortestCycle: number | null
  longestCycle: number | null
  cycleStdDev: number | null
  regular: boolean | null
  lastPeriod: Period | null
  nextPeriodStart: number | null
  info(day: number): DayInfo
}

/** Agrupa los días con sangrado (flujo ligero o mayor) en periodos; hasta 2 días sin registro siguen siendo el mismo periodo. */
export function periodsOf(days: CycleDay[]): Period[] {
  const bleed = [...new Set(days.filter((d) => d.flow >= 2).map((d) => d.epochDay))].sort((a, b) => a - b)
  if (bleed.length === 0) return []
  const out: Period[] = []
  let start = bleed[0], prev = bleed[0]
  for (let i = 1; i < bleed.length; i++) {
    const d = bleed[i]
    if (d - prev > 3) { out.push({ start, end: prev }); start = d }
    prev = d
  }
  out.push({ start, end: prev })
  return out
}

const clamp = (v: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, v))
const avg = (xs: number[]) => xs.reduce((a, b) => a + b, 0) / xs.length

export function analyzeCycle(days: CycleDay[], defaultCycleLength: number, defaultPeriodLength: number, lutealLength: number, today: number, horizon = 12): CycleAnalysis {
  const periods = periodsOf(days).filter((p) => p.start <= today)
  const cycles: CycleRecord[] = periods.map((p, i) => {
    const next = periods[i + 1]
    const length = next ? next.start - p.start : null
    return { start: p.start, periodLength: p.end - p.start + 1, length, valid: length !== null && length >= MIN_VALID_CYCLE && length <= MAX_VALID_CYCLE }
  })
  const recent = cycles.filter((c) => c.valid).map((c) => c.length as number).slice(-RECENT)
  const avgCycle = recent.length ? avg(recent) : null
  let std: number | null = null
  if (recent.length >= 2) {
    const m = avg(recent)
    std = Math.sqrt(recent.reduce((s, x) => s + (x - m) * (x - m), 0) / (recent.length - 1))
  }
  const finished = periods.filter((p, i) => i < periods.length - 1 || p.end < today - 1)
  const recentPeriods = finished.slice(-RECENT).map((p) => p.end - p.start + 1)
  const avgPeriod = recentPeriods.length ? avg(recentPeriods) : null
  const cycleLength = clamp(avgCycle !== null ? Math.round(avgCycle) : defaultCycleLength, MIN_VALID_CYCLE, MAX_VALID_CYCLE)
  const periodLength = clamp(avgPeriod !== null ? Math.round(avgPeriod) : defaultPeriodLength, 1, 14)
  const luteal = clamp(lutealLength, 8, 18)

  const predicted: number[] = []
  const last = periods.length ? periods[periods.length - 1] : null
  if (last) {
    let next = Math.max(last.start + cycleLength, today)
    for (let i = 0; i < horizon; i++) { predicted.push(next); next += cycleLength }
  }
  const actualStarts = new Set(periods.map((p) => p.start))
  const allStarts = [...new Set([...actualStarts, ...predicted])].sort((a, b) => a - b)
  const ovulationOf = (start: number, next: number): number | null => (next - luteal - start >= 5 ? next - luteal : null)
  const shortest = recent.length ? Math.min(...recent) : null
  const longest = recent.length ? Math.max(...recent) : null

  const info = (day: number): DayInfo => {
    const inPeriod = periods.some((p) => day >= p.start && day <= p.end)
    let start: number | null = null
    for (const s of allStarts) {
      // Un inicio estimado para HOY (aún sin registrar) no reinicia la cuenta de hoy.
      if (s <= day && !(s === today && day === today && !actualStarts.has(s))) start = s
    }
    if (start === null) return { cycleDay: null, phase: inPeriod ? 'Menstruación' : 'Sin datos' }
    const next = allStarts.find((s) => s > (start as number)) ?? start + cycleLength
    const cycleDay = day - start + 1
    if (cycleDay > MAX_VALID_CYCLE + 30) return { cycleDay: null, phase: 'Sin datos' }
    const predictedPeriod = !inPeriod && day >= today && predicted.some((s) => !actualStarts.has(s) && day >= s && day < s + periodLength)
    const ov = ovulationOf(start, next)
    const phase: Phase = inPeriod ? 'Menstruación'
      : predictedPeriod && day > today ? 'Menstruación'
      : ov !== null && day === ov ? 'Ovulación estimada'
      : ov !== null && day >= ov - 5 && day <= ov + 1 ? 'Ventana fértil'
      : ov !== null && day < ov ? 'Fase folicular'
      : ov !== null && day > ov ? 'Fase lútea'
      : 'Fase folicular'
    return { cycleDay, phase }
  }

  return {
    periods, cycles, cycleLength, periodLength, lutealLength: luteal, avgCycle, avgPeriod, shortestCycle: shortest, longestCycle: longest,
    cycleStdDev: std,
    regular: shortest === null || longest === null || cycles.filter((c) => c.valid).length < 3 ? null : longest - shortest <= 7,
    lastPeriod: last, nextPeriodStart: predicted[0] ?? null, info,
  }
}
