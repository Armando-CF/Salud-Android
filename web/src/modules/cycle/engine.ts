// Motor de predicción del ciclo menstrual. Port EXACTO de app/savia/cycle/CycleEngine.kt (Android):
// mismos resultados para los mismos datos. Todas las fechas son epochDay.
// Las predicciones son estimaciones estadísticas: NO sirven como método anticonceptivo.
import type { CycleDay } from '../../data/types'

export const MIN_VALID_CYCLE = 15
export const MAX_VALID_CYCLE = 60
/** Cuántos ciclos recientes se promedian para predecir. */
export const RECENT = 6

/** Periodo registrado: del primer al último día con sangrado (inclusive). */
export interface Period { start: number; end: number }
export const periodLength = (p: Period): number => p.end - p.start + 1
export const inPeriod = (p: Period, day: number): boolean => day >= p.start && day <= p.end

/** Un ciclo: del primer día de un periodo al día anterior del siguiente. */
export interface CycleRecord {
  start: number
  periodLength: number
  /** Días hasta el siguiente periodo; null si es el ciclo en curso. */
  length: number | null
}
/** ¿Entra en las estadísticas? (se descartan valores atípicos, p. ej. meses sin registrar). */
export const isValidCycle = (c: CycleRecord): boolean => c.length !== null && c.length >= MIN_VALID_CYCLE && c.length <= MAX_VALID_CYCLE

export type Phase = 'MENSTRUAL' | 'FOLICULAR' | 'FERTIL' | 'OVULACION' | 'LUTEA' | 'DESCONOCIDA'
export const PHASE_LABEL: Record<Phase, string> = {
  MENSTRUAL: 'Menstruación',
  FOLICULAR: 'Fase folicular',
  FERTIL: 'Ventana fértil',
  OVULACION: 'Ovulación estimada',
  LUTEA: 'Fase lútea',
  DESCONOCIDA: 'Sin datos',
}

/** Lo que se sabe / estima de un día concreto. */
export interface DayInfo {
  epochDay: number
  /** Día del ciclo (1 = primer día del periodo) o null si no se puede saber. */
  cycleDay: number | null
  phase: Phase
  /** Dentro de un periodo registrado. */
  inPeriod: boolean
  /** Dentro de un periodo estimado (futuro). */
  predictedPeriod: boolean
  fertile: boolean
  ovulation: boolean
}

export interface DayRange { first: number; last: number }

const clamp = (v: number, lo: number, hi: number): number => Math.min(Math.max(v, lo), hi)
const average = (xs: number[]): number => xs.reduce((a, b) => a + b, 0) / xs.length

/** Resultado del análisis del historial. */
export class CycleAnalysis {
  private readonly allStarts: number[]
  private readonly actualStarts: Set<number>

  constructor(
    readonly periods: Period[],
    readonly cycles: CycleRecord[],
    /** Duración de ciclo usada para predecir (promedio reciente o el valor por defecto). */
    readonly cycleLength: number,
    /** Duración de periodo usada para predecir. */
    readonly periodLength: number,
    readonly lutealLength: number,
    /** Promedios reales (null si aún no hay ciclos completos). */
    readonly avgCycle: number | null,
    readonly avgPeriod: number | null,
    readonly shortestCycle: number | null,
    readonly longestCycle: number | null,
    /** Desviación estándar de los ciclos válidos recientes. */
    readonly cycleStdDev: number | null,
    readonly today: number,
    /** Inicios estimados de los próximos periodos (ordenados). */
    readonly predictedStarts: number[],
    /** Días de retraso respecto a lo esperado (0 si no hay retraso). */
    readonly lateDays: number,
  ) {
    this.actualStarts = new Set(periods.map((p) => p.start))
    this.allStarts = [...new Set([...periods.map((p) => p.start), ...predictedStarts])].sort((a, b) => a - b)
  }

  get hasData(): boolean { return this.periods.length > 0 }
  get lastPeriod(): Period | null { return this.periods.length ? this.periods[this.periods.length - 1] : null }
  get nextPeriodStart(): number | null { return this.predictedStarts.length ? this.predictedStarts[0] : null }

  /** Días que faltan para el siguiente periodo estimado (0 = hoy o con retraso). */
  get daysUntilNext(): number | null {
    const n = this.nextPeriodStart
    return n === null ? null : Math.max(0, n - this.today)
  }

  /** ¿Hoy cae dentro de un periodo registrado (o registrado hasta ayer)? */
  get periodOngoing(): boolean {
    const l = this.lastPeriod
    return l !== null && l.end >= this.today - 1
  }

  /** Regularidad: true si la variación entre ciclos recientes es de 7 días o menos (null sin datos suficientes). */
  get regular(): boolean | null {
    if (this.shortestCycle === null || this.longestCycle === null) return null
    if (this.cycles.filter(isValidCycle).length < 3) return null
    return this.longestCycle - this.shortestCycle <= 7
  }

  /** Día de ovulación estimado para el ciclo que empieza en `start` y cuyo siguiente periodo empieza en `next`. */
  private ovulationOf(start: number, next: number): number | null {
    const ov = next - this.lutealLength
    return ov - start >= 5 ? ov : null
  }

  info(day: number): DayInfo {
    const inP = this.periods.some((p) => inPeriod(p, day))
    // Un inicio estimado para HOY (periodo esperado o con retraso, aún sin registrar) no reinicia
    // la cuenta de hoy: hoy sigue siendo el día N del ciclo anterior.
    let start: number | null = null
    for (let i = this.allStarts.length - 1; i >= 0; i--) {
      const s = this.allStarts[i]
      if (s <= day && !(s === this.today && day === this.today && !this.actualStarts.has(s))) { start = s; break }
    }
    if (start === null) {
      return { epochDay: day, cycleDay: null, phase: inP ? 'MENSTRUAL' : 'DESCONOCIDA', inPeriod: inP, predictedPeriod: false, fertile: false, ovulation: false }
    }
    const s0 = start
    const next = this.allStarts.find((s) => s > s0) ?? s0 + this.cycleLength
    const cycleDay = day - s0 + 1
    if (cycleDay > MAX_VALID_CYCLE + 30) {
      return { epochDay: day, cycleDay: null, phase: 'DESCONOCIDA', inPeriod: inP, predictedPeriod: false, fertile: false, ovulation: false }
    }
    const predictedPeriod = !inP && day >= this.today &&
      this.predictedStarts.some((s) => !this.actualStarts.has(s) && day >= s && day < s + this.periodLength)
    const ov = this.ovulationOf(s0, next)
    const ovulation = ov !== null && day === ov
    const fertile = ov !== null && day >= ov - 5 && day <= ov + 1
    let phase: Phase
    if (inP) phase = 'MENSTRUAL'
    else if (predictedPeriod && day > this.today) phase = 'MENSTRUAL'
    else if (ovulation) phase = 'OVULACION'
    else if (fertile) phase = 'FERTIL'
    else if (ov !== null && day < ov) phase = 'FOLICULAR'
    else if (ov !== null && day > ov) phase = 'LUTEA'
    else phase = 'FOLICULAR'
    return { epochDay: day, cycleDay, phase, inPeriod: inP, predictedPeriod, fertile: fertile && !inP, ovulation: ovulation && !inP }
  }

  /** Día del ciclo de hoy. */
  get currentCycleDay(): number | null { return this.hasData ? this.info(this.today).cycleDay : null }

  /** Próxima ovulación estimada (hoy o después). */
  get nextOvulation(): number | null {
    for (let i = 0; i < this.allStarts.length; i++) {
      const s = this.allStarts[i]
      const n = i + 1 < this.allStarts.length ? this.allStarts[i + 1] : s + this.cycleLength
      const ov = this.ovulationOf(s, n)
      if (ov !== null && ov >= this.today) return ov
    }
    return null
  }

  /** Próxima ventana fértil estimada (la actual si hoy cae dentro). */
  get nextFertileWindow(): DayRange | null {
    for (let i = 0; i < this.allStarts.length; i++) {
      const s = this.allStarts[i]
      const n = i + 1 < this.allStarts.length ? this.allStarts[i + 1] : s + this.cycleLength
      const ov = this.ovulationOf(s, n)
      if (ov !== null && ov + 1 >= this.today) return { first: ov - 5, last: ov + 1 }
    }
    return null
  }
}

/** Un día cuenta como "periodo" con flujo ligero o mayor (el manchado solo no inicia un periodo). */
export const isBleeding = (day: Pick<CycleDay, 'flow'>): boolean => day.flow >= 2

/**
 * Agrupa los días con sangrado en periodos. Dos días con sangrado separados por hasta
 * 2 días sin registro pertenecen al mismo periodo.
 */
export function periodsOf(days: readonly Pick<CycleDay, 'epochDay' | 'flow'>[]): Period[] {
  const bleed = [...new Set(days.filter(isBleeding).map((d) => d.epochDay))].sort((a, b) => a - b)
  if (!bleed.length) return []
  const out: Period[] = []
  let start = bleed[0]
  let prev = bleed[0]
  for (let i = 1; i < bleed.length; i++) {
    const d = bleed[i]
    if (d - prev > 3) {
      out.push({ start, end: prev })
      start = d
    }
    prev = d
  }
  out.push({ start, end: prev })
  return out
}

export function analyze(
  days: readonly Pick<CycleDay, 'epochDay' | 'flow'>[],
  defaultCycleLength: number,
  defaultPeriodLength: number,
  lutealLength: number,
  today: number,
  horizon = 12,
): CycleAnalysis {
  const periods = periodsOf(days).filter((p) => p.start <= today)
  const cycles: CycleRecord[] = periods.map((p, i) => ({
    start: p.start,
    periodLength: periodLength(p),
    length: i + 1 < periods.length ? periods[i + 1].start - p.start : null,
  }))
  const validLengths = cycles.filter(isValidCycle).map((c) => c.length as number)
  const recent = validLengths.slice(-RECENT)
  const avgCycle = recent.length ? average(recent) : null
  let std: number | null = null
  if (recent.length >= 2) {
    const m = average(recent)
    std = Math.sqrt(recent.reduce((acc, x) => acc + (x - m) * (x - m), 0) / (recent.length - 1))
  }
  // Periodos terminados (el último puede seguir en curso si llega hasta hoy o ayer).
  const finished = periods.filter((p, i) => i < periods.length - 1 || p.end < today - 1)
  const recentPeriods = finished.slice(-RECENT).map(periodLength)
  const avgPeriod = recentPeriods.length ? average(recentPeriods) : null

  const cycleLen = clamp(avgCycle !== null ? Math.round(avgCycle) : defaultCycleLength, MIN_VALID_CYCLE, MAX_VALID_CYCLE)
  const periodLen = clamp(avgPeriod !== null ? Math.round(avgPeriod) : defaultPeriodLength, 1, 14)
  const luteal = clamp(lutealLength, 8, 18)

  const predicted: number[] = []
  let late = 0
  const last = periods.length ? periods[periods.length - 1] : null
  if (last) {
    const expected = last.start + cycleLen
    late = Math.max(0, today - expected)
    // Si hay retraso, lo esperado es "en cualquier momento": se ancla a hoy.
    let next = Math.max(expected, today)
    // Si el historial es muy viejo (más de ~3 ciclos sin registrar) no tiene sentido anclar el retraso.
    if (late > cycleLen * 3) late = 0
    for (let i = 0; i < horizon; i++) {
      predicted.push(next)
      next += cycleLen
    }
  }
  return new CycleAnalysis(
    periods, cycles, cycleLen, periodLen, luteal, avgCycle, avgPeriod,
    recent.length ? Math.min(...recent) : null, recent.length ? Math.max(...recent) : null, std, today, predicted, late,
  )
}
