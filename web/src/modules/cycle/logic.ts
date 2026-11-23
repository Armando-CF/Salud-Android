// Lógica pura (sin React) del módulo de ciclo. Port de app/savia/ui/cycle/CycleLogic.kt.
import { CycleCatalog } from '../../data/catalogs'
import { emptyCycleDay, type CycleDay } from '../../data/types'
import { Fmt, epochDayToDate, ymdToEpochDay } from '../../lib/dates'
import { MAX_VALID_CYCLE, PHASE_LABEL, inPeriod, type CycleAnalysis, type DayRange, type Phase } from './engine'

/** Cuántas veces apareció un síntoma (o ánimo) y en qué fases. */
export interface PhaseStat {
  key: string
  label: string
  count: number
  byPhase: Partial<Record<Phase, number>>
  /** Fase donde más aparece (null si no se pudo determinar ninguna). */
  topPhase: Phase | null
}

/** Qué acción rápida ofrecer hoy: START = "Empezó mi periodo"; CONTINUE = "Sigue hoy" / "Ya terminó"; LOGGED = hoy ya tiene flujo. */
export type QuickState = 'START' | 'CONTINUE' | 'LOGGED'

/** Estado de hoy listo para mostrarse (encabezado del ciclo, tarjeta de inicio). */
export interface TodayStatus {
  /** Día del ciclo contado desde el inicio del último periodo registrado (null si es muy viejo o no hay datos). */
  cycleDay: number | null
  /** Etiqueta de fase o situación ("Fase lútea", "Periodo retrasado"…). */
  phaseLabel: string
  /** Mensaje principal ("Periodo en 5 días"). */
  headline: string
  /** Detalle bajo el mensaje principal (fecha estimada, etc.). */
  detail: string | null
  /** Hoy cae dentro de un periodo registrado. */
  inPeriod: boolean
}

export const BASE_YEAR = 1990
/** Días desde el último periodo a partir de los cuales las predicciones ya no se muestran como vigentes. */
export const STALE_DAYS = MAX_VALID_CYCLE + 30

export const dias = (n: number): string => (n === 1 ? '1 día' : `${n} días`)

// ---- Calendario ----

export interface YearMonth { year: number; /** 1..12 */ month: number }

/** Índice de mes continuo (enero de BASE_YEAR = 0). */
export const monthIndex = (ym: YearMonth): number => (ym.year - BASE_YEAR) * 12 + (ym.month - 1)
export const monthOfIndex = (index: number): YearMonth => ({ year: BASE_YEAR + Math.floor(index / 12), month: (((index % 12) + 12) % 12) + 1 })
export const monthOfEpochDay = (day: number): YearMonth => { const d = epochDayToDate(day); return { year: d.getFullYear(), month: d.getMonth() + 1 } }
export const firstOfMonth = (ym: YearMonth): Date => new Date(ym.year, ym.month - 1, 1)

/**
 * Celdas del mes (epochDay o null para huecos), semana empezando en lunes.
 * Con `fixedWeeks` siempre devuelve 42 celdas (6 semanas) para que la altura no brinque.
 */
export function monthGrid(ym: YearMonth, fixedWeeks = true): (number | null)[] {
  const first = firstOfMonth(ym)
  const lead = (first.getDay() + 6) % 7
  const cells: (number | null)[] = []
  for (let i = 0; i < lead; i++) cells.push(null)
  const base = ymdToEpochDay(ym.year, ym.month, 1)
  const length = new Date(ym.year, ym.month, 0).getDate()
  for (let d = 0; d < length; d++) cells.push(base + d)
  const target = fixedWeeks ? 42 : Math.floor((cells.length + 6) / 7) * 7
  while (cells.length < target) cells.push(null)
  return cells
}

/**
 * Las 42 celdas del mes con su fecha real: los huecos se rellenan con los días del mes
 * anterior/siguiente (inMonth = false) para dibujarlos atenuados.
 */
export function monthGridDays(ym: YearMonth): { day: number; inMonth: boolean }[] {
  const cells = monthGrid(ym)
  const first = ymdToEpochDay(ym.year, ym.month, 1)
  const lead = cells.findIndex((c) => c !== null)
  return cells.map((c, i) => ({ day: c ?? first - lead + i, inMonth: c !== null }))
}

// ---- Registros de un día ----

/** ¿El registro no contiene nada? (se elimina la fila en vez de guardarla). */
export const isEmpty = (d: CycleDay): boolean =>
  d.flow === 0 && !d.symptoms.trim() && !d.moods.trim() && !d.mucus.trim() && d.basalTemp === null &&
  d.sex === 0 && d.ovulationTest === 0 && d.pregnancyTest === 0 && !d.contraceptive && !d.notes.trim()

/** ¿Tiene algo además del flujo? (para el puntito del calendario). */
export const hasExtras = (d: CycleDay): boolean => !isEmpty({ ...d, flow: 0 })

/** Devuelve el registro con el flujo cambiado, o null si quedaría vacío (hay que borrar la fila). */
export function withFlow(existing: CycleDay | null | undefined, day: number, flow: number): CycleDay | null {
  const n: CycleDay = { ...(existing ?? emptyCycleDay(day)), flow: Math.min(Math.max(flow, 0), 4) }
  return isEmpty(n) ? null : n
}

/** Líneas legibles de lo registrado en un día. */
export function summary(d: CycleDay): string[] {
  const out: string[] = []
  if (d.flow > 0) out.push(d.flow === 1 ? 'Manchado' : `Flujo ${CycleCatalog.flowLabel(d.flow).toLowerCase()}`)
  const s = CycleCatalog.split(d.symptoms)
  if (s.length) out.push('Síntomas: ' + s.map((k) => CycleCatalog.symptomLabel(k).toLowerCase()).join(', '))
  const m = CycleCatalog.split(d.moods)
  if (m.length) out.push('Ánimo: ' + m.map((k) => CycleCatalog.moodLabel(k).toLowerCase()).join(', '))
  if (d.mucus.trim()) out.push('Flujo cervical: ' + CycleCatalog.mucusLabel(d.mucus).toLowerCase())
  if (d.basalTemp !== null) out.push(`Temperatura basal: ${Fmt.num(d.basalTemp, 2)} °C`)
  if (d.sex !== 0) out.push('Relaciones: ' + CycleCatalog.sexLabel(d.sex).toLowerCase())
  if (d.ovulationTest !== 0) out.push('Prueba de ovulación: ' + CycleCatalog.testLabel(d.ovulationTest).toLowerCase())
  if (d.pregnancyTest !== 0) out.push('Prueba de embarazo: ' + CycleCatalog.testLabel(d.pregnancyTest).toLowerCase())
  if (d.contraceptive) out.push('Anticonceptivo tomado o aplicado')
  if (d.notes.trim()) out.push('Notas: ' + d.notes.trim())
  return out
}

// ---- Estado de hoy ----

export function quickState(a: CycleAnalysis, todayRecord: CycleDay | null | undefined): QuickState {
  if ((todayRecord?.flow ?? 0) >= 1) return 'LOGGED'
  if (a.lastPeriod?.end === a.today - 1) return 'CONTINUE'
  return 'START'
}

/**
 * Resume la situación de hoy. Cuenta el día del ciclo desde el último periodo REAL (cuando hay
 * retraso o el periodo se espera hoy, el motor ancla un inicio estimado en hoy).
 */
export function todayStatus(a: CycleAnalysis): TodayStatus {
  const last = a.lastPeriod
  if (!last) return { cycleDay: null, phaseLabel: 'Sin datos', headline: 'Registra tu periodo para empezar', detail: null, inPeriod: false }
  const sinceStart = a.today - last.start + 1
  if (sinceStart > STALE_DAYS) {
    return {
      cycleDay: null, phaseLabel: 'Sin registros recientes', headline: 'Registra tu periodo para reanudar las predicciones',
      detail: `Último periodo: ${Fmt.date(last.start)}`, inPeriod: false,
    }
  }
  const next = a.nextPeriodStart
  if (inPeriod(last, a.today)) {
    return { cycleDay: sinceStart, phaseLabel: PHASE_LABEL.MENSTRUAL, headline: `Día ${sinceStart} de tu periodo`, detail: `Empezó el ${Fmt.dateWeekday(last.start)}`, inPeriod: true }
  }
  if (a.lateDays > 0) {
    return { cycleDay: sinceStart, phaseLabel: 'Periodo retrasado', headline: `${dias(a.lateDays)} de retraso`, detail: `Se esperaba el ${Fmt.dateWeekday(last.start + a.cycleLength)}`, inPeriod: false }
  }
  if (next !== null && next <= a.today) {
    return { cycleDay: sinceStart, phaseLabel: 'Periodo esperado', headline: 'Tu periodo podría llegar hoy', detail: `Según tu ciclo de ${dias(a.cycleLength)}`, inPeriod: false }
  }
  if (next !== null) {
    return { cycleDay: sinceStart, phaseLabel: PHASE_LABEL[a.info(a.today).phase], headline: `Periodo en ${dias(next - a.today)}`, detail: `Estimado para el ${Fmt.dateWeekday(next)}`, inPeriod: false }
  }
  return { cycleDay: sinceStart, phaseLabel: PHASE_LABEL[a.info(a.today).phase], headline: `Día ${sinceStart} del ciclo`, detail: null, inPeriod: false }
}

export const regularityText = (a: CycleAnalysis): string => (a.regular === true ? 'Regular' : a.regular === false ? 'Irregular' : 'Sin datos suficientes')

/** "28 nov – 4 dic", o "21 – 27 nov" si ambos días caen en el mismo mes. */
export function rangeText(range: DayRange): string {
  const a = epochDayToDate(range.first)
  const b = epochDayToDate(range.last)
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth()
    ? `${a.getDate()} – ${Fmt.dateShort(range.last)}`
    : `${Fmt.dateShort(range.first)} – ${Fmt.dateShort(range.last)}`
}

// ---- Estadísticas ----

/** Fase simplificada para estadísticas (la ovulación cuenta como ventana fértil). */
function statPhase(a: CycleAnalysis, day: number): Phase {
  const p = a.info(day).phase
  return p === 'OVULACION' ? 'FERTIL' : p
}

function stats(days: readonly CycleDay[], a: CycleAnalysis, keys: (d: CycleDay) => string, label: (k: string) => string): PhaseStat[] {
  const acc = new Map<string, Map<Phase, number>>()
  for (const d of days) {
    const ks = CycleCatalog.split(keys(d))
    if (!ks.length) continue
    const phase = statPhase(a, d.epochDay)
    for (const k of ks) {
      let m = acc.get(k)
      if (!m) { m = new Map(); acc.set(k, m) }
      m.set(phase, (m.get(phase) ?? 0) + 1)
    }
  }
  const out: PhaseStat[] = []
  for (const [k, m] of acc) {
    let top: Phase | null = null
    let topCount = -1
    for (const [p, c] of m) if (p !== 'DESCONOCIDA' && c > topCount) { top = p; topCount = c }
    out.push({ key: k, label: label(k), count: [...m.values()].reduce((x, y) => x + y, 0), byPhase: Object.fromEntries(m), topPhase: top })
  }
  return out.sort((x, y) => y.count - x.count || x.label.localeCompare(y.label, 'es'))
}

/** Síntomas del más al menos frecuente, con su reparto por fase. */
export const symptomStats = (days: readonly CycleDay[], a: CycleAnalysis): PhaseStat[] => stats(days, a, (d) => d.symptoms, CycleCatalog.symptomLabel)
/** Estados de ánimo del más al menos frecuente, con su reparto por fase. */
export const moodStats = (days: readonly CycleDay[], a: CycleAnalysis): PhaseStat[] => stats(days, a, (d) => d.moods, CycleCatalog.moodLabel)

export function phaseShort(p: Phase): string {
  switch (p) {
    case 'MENSTRUAL': return 'la menstruación'
    case 'FOLICULAR': return 'la fase folicular'
    case 'FERTIL':
    case 'OVULACION': return 'la ventana fértil'
    case 'LUTEA': return 'la fase lútea (antes del periodo)'
    default: return 'días sin fase conocida'
  }
}
