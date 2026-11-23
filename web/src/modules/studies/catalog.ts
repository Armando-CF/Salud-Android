// Catálogo y lógica pura del módulo de estudios (portado de StudyCatalog.kt de la app de Android).
import { Fmt } from '../../lib/dates'
import type { Study, StudyResult } from '../../data/types'

/** Posición de un valor respecto a su rango de referencia. */
export type RangeFlag = 'BAJO' | 'NORMAL' | 'ALTO' | 'SIN_RANGO'

/**
 * Parámetro común de laboratorio con un rango de referencia típico para adultos.
 * Los rangos son ORIENTATIVOS: cada laboratorio usa los suyos y siempre mandan los del reporte.
 */
export interface LabParam {
  name: string
  unit: string
  refMin?: number
  refMax?: number
  /** Rango específico para mujeres (si difiere). */
  refMinF?: number
  refMaxF?: number
  /** Rango específico para hombres (si difiere). */
  refMinM?: number
  refMaxM?: number
  /** Otros nombres con los que suele aparecer en reportes. */
  aliases?: string[]
}

/** Rango [mín, máx] para el sexo del perfil: "F", "M" u otro (rango general). */
export function rangeFor(p: LabParam, sex: string): [number | null, number | null] {
  if (sex === 'F') return [p.refMinF ?? p.refMin ?? null, p.refMaxF ?? p.refMax ?? null]
  if (sex === 'M') return [p.refMinM ?? p.refMin ?? null, p.refMaxM ?? p.refMax ?? null]
  return [p.refMin ?? null, p.refMax ?? null]
}

/** Grupo de parámetros que suelen pedirse juntos. */
export interface LabGroup { name: string; params: string[] }

const P = (name: string, unit: string, refMin?: number | null, refMax?: number | null, extra: Partial<LabParam> = {}): LabParam => ({
  name, unit, ...(refMin != null ? { refMin } : {}), ...(refMax != null ? { refMax } : {}), ...extra,
})

const PARAMS: LabParam[] = [
  // Química sanguínea
  P('Glucosa', 'mg/dL', 70, 99, { aliases: ['Glucosa en ayunas', 'Glucemia'] }),
  P('Hemoglobina glucosilada (HbA1c)', '%', null, 5.6, { aliases: ['HbA1c', 'Hemoglobina glicada'] }),
  P('Urea', 'mg/dL', 15, 45),
  P('Nitrógeno ureico (BUN)', 'mg/dL', 7, 20, { aliases: ['BUN'] }),
  P('Creatinina', 'mg/dL', 0.5, 1.3, { refMinF: 0.5, refMaxF: 1.1, refMinM: 0.7, refMaxM: 1.3 }),
  P('Ácido úrico', 'mg/dL', 2.4, 7, { refMinF: 2.4, refMaxF: 6, refMinM: 3.4, refMaxM: 7 }),
  // Lípidos
  P('Colesterol total', 'mg/dL', null, 200, { aliases: ['Colesterol'] }),
  P('Colesterol HDL', 'mg/dL', 40, null, { refMinF: 50, refMinM: 40, aliases: ['HDL'] }),
  P('Colesterol LDL', 'mg/dL', null, 130, { aliases: ['LDL'] }),
  P('Triglicéridos', 'mg/dL', null, 150),
  // Biometría hemática
  P('Hemoglobina', 'g/dL', 12, 17.5, { refMinF: 12, refMaxF: 16, refMinM: 13.5, refMaxM: 17.5 }),
  P('Hematocrito', '%', 36, 53, { refMinF: 36, refMaxF: 46, refMinM: 41, refMaxM: 53 }),
  P('Eritrocitos', 'millones/µL', 4, 5.9, { refMinF: 4, refMaxF: 5.2, refMinM: 4.5, refMaxM: 5.9, aliases: ['Glóbulos rojos'] }),
  P('Leucocitos', 'miles/µL', 4.5, 11, { aliases: ['Glóbulos blancos'] }),
  P('Plaquetas', 'miles/µL', 150, 450),
  // Hígado
  P('TGO (AST)', 'U/L', null, 40, { aliases: ['AST', 'TGO', 'Aspartato aminotransferasa'] }),
  P('TGP (ALT)', 'U/L', null, 40, { aliases: ['ALT', 'TGP', 'Alanina aminotransferasa'] }),
  P('GGT', 'U/L', null, 55, { refMaxF: 38, refMaxM: 55, aliases: ['Gamma glutamil transferasa'] }),
  P('Fosfatasa alcalina', 'U/L', 44, 147),
  P('Bilirrubina total', 'mg/dL', 0.1, 1.2),
  P('Albúmina', 'g/dL', 3.5, 5),
  P('Proteínas totales', 'g/dL', 6, 8.3),
  // Tiroides
  P('TSH', 'µUI/mL', 0.4, 4, { aliases: ['Hormona estimulante de tiroides', 'Tirotropina'] }),
  P('T4 libre', 'ng/dL', 0.8, 1.8, { aliases: ['Tiroxina libre'] }),
  P('T3 total', 'ng/dL', 80, 200, { aliases: ['Triyodotironina'] }),
  // Vitaminas y minerales
  P('Vitamina D (25-OH)', 'ng/mL', 30, 100, { aliases: ['Vitamina D', '25-hidroxivitamina D'] }),
  P('Vitamina B12', 'pg/mL', 200, 900),
  P('Hierro', 'µg/dL', 50, 175, { refMinF: 50, refMaxF: 170, refMinM: 65, refMaxM: 175, aliases: ['Hierro sérico'] }),
  P('Ferritina', 'ng/mL', 15, 400, { refMinF: 15, refMaxF: 150, refMinM: 30, refMaxM: 400 }),
  // Electrolitos
  P('Sodio', 'mEq/L', 135, 145),
  P('Potasio', 'mEq/L', 3.5, 5),
  P('Calcio', 'mg/dL', 8.5, 10.2),
  // Otros
  P('Antígeno prostático (PSA)', 'ng/mL', null, 4, { aliases: ['PSA', 'Antígeno prostático específico'] }),
  P('Proteína C reactiva', 'mg/L', null, 5, { aliases: ['PCR'] }),
  P('Velocidad de sedimentación (VSG)', 'mm/h', null, 20, { aliases: ['VSG'] }),
  P('Densidad urinaria', '', 1.005, 1.03),
  P('pH urinario', '', 4.5, 8),
]

const GROUPS: LabGroup[] = [
  { name: 'Química sanguínea', params: ['Glucosa', 'Urea', 'Creatinina', 'Ácido úrico', 'Colesterol total', 'Triglicéridos'] },
  { name: 'Perfil de lípidos', params: ['Colesterol total', 'Colesterol HDL', 'Colesterol LDL', 'Triglicéridos'] },
  { name: 'Biometría hemática', params: ['Hemoglobina', 'Hematocrito', 'Eritrocitos', 'Leucocitos', 'Plaquetas'] },
  { name: 'Función hepática', params: ['TGO (AST)', 'TGP (ALT)', 'GGT', 'Fosfatasa alcalina', 'Bilirrubina total', 'Albúmina'] },
  { name: 'Perfil tiroideo', params: ['TSH', 'T4 libre', 'T3 total'] },
  { name: 'Electrolitos', params: ['Sodio', 'Potasio', 'Calcio'] },
  { name: 'Examen general de orina', params: ['Densidad urinaria', 'pH urinario'] },
]

/** Títulos sugeridos por clave de categoría (ver StudyCategories). */
const TITLES: Record<string, string[]> = {
  lab: ['Química sanguínea', 'Biometría hemática', 'Examen general de orina', 'Perfil de lípidos', 'Perfil tiroideo', 'Hemoglobina glucosilada', 'Pruebas de función hepática', 'Vitamina D', 'Coprológico'],
  imagen: ['Radiografía de tórax', 'Ultrasonido abdominal', 'Ultrasonido pélvico', 'Tomografía', 'Resonancia magnética', 'Densitometría ósea', 'Mastografía', 'Ultrasonido mamario'],
  chequeo: ['Chequeo anual', 'Revisión médica general', 'Examen médico laboral', 'Valoración nutricional'],
  cardio: ['Electrocardiograma', 'Ecocardiograma', 'Prueba de esfuerzo', 'Holter', 'Monitoreo de presión arterial'],
  gine: ['Papanicolaou', 'Mastografía', 'Ultrasonido mamario', 'Ultrasonido pélvico', 'Colposcopía', 'Prueba de VPH', 'Revisión ginecológica'],
  uro: ['Antígeno prostático', 'Revisión urológica', 'Ultrasonido prostático', 'Ultrasonido renal', 'Urocultivo'],
  dental: ['Limpieza dental', 'Revisión dental', 'Radiografía dental', 'Resina / empaste', 'Ortodoncia'],
  vista: ['Examen de la vista', 'Graduación de lentes', 'Fondo de ojo', 'Presión intraocular'],
  oido: ['Audiometría', 'Revisión de oídos', 'Lavado de oídos'],
  piel: ['Revisión de lunares', 'Consulta dermatológica', 'Biopsia de piel'],
  vacuna: ['Influenza', 'COVID-19', 'Tétanos y difteria (Td)', 'Hepatitis B', 'VPH', 'Neumococo', 'Sarampión y rubéola (SR)', 'Herpes zóster'],
  otro: ['Consulta de especialidad', 'Estudio de sueño', 'Espirometría', 'Prueba de alergias'],
}

/** Minúsculas, sin acentos y con espacios colapsados: para comparar nombres de parámetros. */
export function normalizeName(s: string): string {
  return s.trim().toLowerCase().normalize('NFD').replace(/[̀-ͯ]+/g, '').replace(/\s+/g, ' ')
}

export const StudyCatalog = {
  params: PARAMS,
  groups: GROUPS,
  titles: TITLES,
  titlesFor: (category: string): string[] => TITLES[category] ?? [],

  /** Busca un parámetro del catálogo por nombre o alias (sin distinguir mayúsculas ni acentos). */
  find(name: string): LabParam | null {
    const n = normalizeName(name)
    if (!n) return null
    return PARAMS.find((p) => normalizeName(p.name) === n) ?? PARAMS.find((p) => (p.aliases ?? []).some((a) => normalizeName(a) === n)) ?? null
  },

  /** Sugerencias de nombres de parámetro que contienen `query`; primero los que empiezan con él. */
  suggest(query: string, extraNames: string[] = [], limit = 5): string[] {
    const q = normalizeName(query)
    if (!q) return []
    const fromCatalog = PARAMS.filter((p) => normalizeName(p.name).includes(q) || (p.aliases ?? []).some((a) => normalizeName(a).includes(q))).map((p) => p.name)
    const fromExtra = extraNames.filter((n) => normalizeName(n).includes(q))
    const seen = new Set<string>()
    const out: string[] = []
    for (const n of [...fromCatalog, ...fromExtra]) {
      const k = normalizeName(n)
      if (seen.has(k) || k === q) continue
      seen.add(k)
      out.push(n)
    }
    // orden estable: primero los que empiezan con la búsqueda
    return [...out.filter((n) => normalizeName(n).startsWith(q)), ...out.filter((n) => !normalizeName(n).startsWith(q))].slice(0, limit)
  },
}

/** Clasifica `value` contra el rango `refMin`..`refMax` (cualquiera de los límites puede faltar). */
export function flag(value: number | null | undefined, refMin: number | null | undefined, refMax: number | null | undefined): RangeFlag {
  if (value == null || (refMin == null && refMax == null)) return 'SIN_RANGO'
  if (refMin != null && value < refMin) return 'BAJO'
  if (refMax != null && value > refMax) return 'ALTO'
  return 'NORMAL'
}

export const resultFlag = (r: Pick<StudyResult, 'value' | 'refMin' | 'refMax'>): RangeFlag => flag(r.value, r.refMin, r.refMax)

export const outOfRange = (r: Pick<StudyResult, 'value' | 'refMin' | 'refMax'>): boolean => {
  const f = resultFlag(r)
  return f === 'BAJO' || f === 'ALTO'
}

/** "Alto ↑", "Bajo ↓", "En rango". */
export const flagLabel = (f: RangeFlag): string => (f === 'ALTO' ? 'Alto ↑' : f === 'BAJO' ? 'Bajo ↓' : f === 'NORMAL' ? 'En rango' : '')

/** Número para mostrar/editar: hasta 3 decimales, sin ceros sobrantes. */
export const labNum = (v: number): string => Fmt.num(v, 3)

/** "70 – 99", "< 200", "> 40" o "" si no hay rango. */
export function rangeText(refMin: number | null | undefined, refMax: number | null | undefined): string {
  if (refMin != null && refMax != null) return `${labNum(refMin)} – ${labNum(refMax)}`
  if (refMax != null) return `< ${labNum(refMax)}`
  if (refMin != null) return `> ${labNum(refMin)}`
  return ''
}

/** "92 mg/dL", "Negativo" o "—". */
export function valueText(r: Pick<StudyResult, 'value' | 'textValue' | 'unit'>): string {
  if (r.value != null) return labNum(r.value) + (r.unit.trim() ? ` ${r.unit}` : '')
  if (r.textValue.trim()) return r.textValue
  return '—'
}

/**
 * Seguimientos pendientes: estudios con fecha de seguimiento desde hace `graceDays` días en adelante,
 * del más próximo al más lejano. Útil para la pantalla de inicio.
 */
export function upcomingFollowUps(studies: Study[], today: number, graceDays = 60): Study[] {
  return studies
    .filter((s) => s.followUpEpochDay != null && s.followUpEpochDay >= today - graceDays)
    .sort((a, b) => (a.followUpEpochDay as number) - (b.followUpEpochDay as number))
}

/** Un valor numérico de un parámetro, con el estudio del que proviene. */
export interface ParamPoint { study: Study; result: StudyResult; epochDay: number; value: number }

/** Resumen de un parámetro a través de los estudios (puntos en orden cronológico). */
export interface ParamSummary { name: string; points: ParamPoint[]; latest: ParamPoint; previous: ParamPoint | null }

const byDateThenId = (a: ParamPoint, b: ParamPoint) => a.epochDay - b.epochDay || (a.result.id ?? 0) - (b.result.id ?? 0)

function toPoints(studies: Study[], results: StudyResult[], keep: (r: StudyResult) => boolean): ParamPoint[] {
  const byId = new Map(studies.map((s) => [s.id, s]))
  const out: ParamPoint[] = []
  for (const r of results) {
    if (r.value == null || !keep(r)) continue
    const study = byId.get(r.studyId)
    if (study) out.push({ study, result: r, epochDay: study.epochDay, value: r.value })
  }
  return out
}

/** Serie cronológica de un parámetro (por nombre normalizado) a través de todos los estudios. */
export function paramSeries(name: string, studies: Study[], results: StudyResult[]): ParamPoint[] {
  const n = normalizeName(name)
  return toPoints(studies, results, (r) => normalizeName(r.name) === n).sort(byDateThenId)
}

/** Todos los parámetros con al menos un valor numérico, ordenados alfabéticamente. */
export function paramSummaries(studies: Study[], results: StudyResult[]): ParamSummary[] {
  const groups = new Map<string, ParamPoint[]>()
  for (const p of toPoints(studies, results, (r) => r.name.trim() !== '')) {
    const k = normalizeName(p.result.name)
    groups.set(k, [...(groups.get(k) ?? []), p])
  }
  return [...groups.values()]
    .map((pts) => {
      const points = [...pts].sort(byDateThenId)
      const latest = points[points.length - 1]
      return { name: latest.result.name.trim(), points, latest, previous: points.length > 1 ? points[points.length - 2] : null }
    })
    .sort((a, b) => normalizeName(a.name).localeCompare(normalizeName(b.name)))
}

/** Ruta de la evolución de un parámetro (el nombre va codificado; "/" se protege aparte). */
export const paramPath = (name: string): string => '/parametro/' + encodeURIComponent(name.trim()).replace(/%2F/gi, '~2F')
export const paramFromRoute = (segment: string | undefined): string => (segment ?? '').replace(/~2F/g, '/')
