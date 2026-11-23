import type { SymptomEntry } from '../../data/types'
import { Fmt, epochDayToDate, millisToEpochDay, normalize } from '../../lib/dates'

/** Resumen estadístico de un conjunto de síntomas (lógica pura, sin interfaz). */
export interface SymptomSummary {
  total: number
  avgIntensity: number | null
  /** Días distintos con al menos un registro. */
  daysWithSymptoms: number
  /** Nombre → veces, del más frecuente al menos. */
  topNames: [string, number][]
  /** Nombre → intensidad promedio, del más intenso al menos. */
  strongest: [string, number][]
  topRegions: [string, number][]
  topTriggers: [string, number][]
  /** Etiqueta del periodo → registros (semanas o meses consecutivos, incluidos los vacíos). */
  buckets: [string, number][]
  /** true = los periodos son meses; false = semanas. */
  monthly: boolean
}

const mod = (a: number, n: number): number => ((a % n) + n) % n
const cmpText = (a: string, b: string): number => (a < b ? -1 : a > b ? 1 : 0)

function top(items: string[], n: number): [string, number][] {
  const counts = new Map<string, number>()
  for (const i of items) counts.set(i, (counts.get(i) ?? 0) + 1)
  return [...counts.entries()].sort((a, b) => b[1] - a[1] || cmpText(a[0], b[0])).slice(0, n)
}

const monthFmt = (withYear: boolean) => new Intl.DateTimeFormat('es-MX', withYear ? { month: 'short', year: '2-digit' } : { month: 'short' })

export const SymptomStats = {
  /** Minúsculas y sin acentos, para comparar y buscar. */
  norm: normalize,

  /** "a, b,c" → [a, b, c] */
  splitTags: (csv: string): string[] => csv.split(',').map((s) => s.trim()).filter(Boolean),

  /** Lunes de la semana a la que pertenece `epochDay`. */
  weekStart: (epochDay: number): number => epochDay - mod(epochDay + 3, 7),

  /** Registros desde `fromDay` (inclusive); null = todos. */
  inRange: (entries: SymptomEntry[], fromDay: number | null): SymptomEntry[] =>
    fromDay === null ? entries : entries.filter((e) => millisToEpochDay(e.timestamp) >= fromDay),

  /** ¿El registro coincide con el texto buscado? (nombre, zona, etiquetas o nota). */
  matches(e: SymptomEntry, query: string): boolean {
    const q = normalize(query)
    if (!q) return true
    return normalize(e.name).includes(q) || normalize(e.regionLabel ?? '').includes(q) || normalize(e.tags).includes(q) || normalize(e.notes).includes(q)
  },

  /** Nombres ya usados, del más frecuente al menos. */
  usedNames: (entries: SymptomEntry[]): string[] => top(entries.map((e) => e.name), Number.MAX_SAFE_INTEGER).map((x) => x[0]),

  /**
   * Resume los registros a partir de `fromDay` (null = todo el historial) hasta `today`.
   * Agrupa por semana si el periodo es de unos 3 meses o menos; si no, por mes.
   */
  summarize(entries: SymptomEntry[], fromDay: number | null, today: number, topN = 6): SymptomSummary {
    const list = SymptomStats.inRange(entries, fromDay)
    const days = list.map((e) => millisToEpochDay(e.timestamp))
    const firstDay = fromDay ?? (days.length ? Math.min(...days) : today)
    const monthly = today - firstDay > 92
    const buckets: [string, number][] = []
    if (monthly) {
      const key = (d: number) => { const x = epochDayToDate(d); return x.getFullYear() * 12 + x.getMonth() }
      const counts = new Map<number, number>()
      for (const d of days) counts.set(key(d), (counts.get(key(d)) ?? 0) + 1)
      const months: number[] = []
      for (let m = key(firstDay); m <= key(today); m++) months.push(m)
      const shown = months.slice(-24)
      const fmt = monthFmt(shown.length > 12)
      for (const m of shown) buckets.push([fmt.format(new Date(Math.floor(m / 12), m % 12, 1)).replace(/\./g, ''), counts.get(m) ?? 0])
    } else {
      const counts = new Map<number, number>()
      for (const d of days) { const w = SymptomStats.weekStart(d); counts.set(w, (counts.get(w) ?? 0) + 1) }
      for (let w = SymptomStats.weekStart(firstDay); w <= SymptomStats.weekStart(today); w += 7) buckets.push([Fmt.dateShort(w), counts.get(w) ?? 0])
    }
    const byName = new Map<string, number[]>()
    for (const e of list) byName.set(e.name, [...(byName.get(e.name) ?? []), e.intensity])
    const strongest = [...byName.entries()]
      .map(([k, v]) => [k, v.reduce((a, b) => a + b, 0) / v.length] as [string, number])
      .sort((a, b) => b[1] - a[1] || cmpText(a[0], b[0]))
      .slice(0, topN)
    return {
      total: list.length,
      avgIntensity: list.length ? list.reduce((a, e) => a + e.intensity, 0) / list.length : null,
      daysWithSymptoms: new Set(days).size,
      topNames: top(list.map((e) => e.name), topN),
      strongest,
      topRegions: top(list.map((e) => e.regionLabel ?? '').filter((l) => l.trim() !== ''), topN),
      topTriggers: top(list.flatMap((e) => SymptomStats.splitTags(e.tags)), topN),
      buckets,
      monthly,
    }
  },
}
