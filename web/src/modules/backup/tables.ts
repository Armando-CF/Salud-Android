// Tablas exportables: la misma estructura alimenta Excel, CSV, el reporte para imprimir y el JSON legible.
// Port de ExportTables.kt (mismos títulos, columnas y textos).

import { CycleCatalog, PetSpecies, SelfExamTypes, SexOptions, StudyCategories, SymptomCatalog, VaccinationTypes, MEASURE_TYPES, measureType } from '../../data/catalogs'
import type { CycleDay, StudyResult } from '../../data/types'
import { Fmt } from '../../lib/dates'
import { MAX_VALID_CYCLE, MIN_VALID_CYCLE, RECENT } from './cycleLite'
import { dataIsPet, has, type ExportData, type ExportOptions, type ModuleKey } from './exportData'

/** Celda de fecha (epochDay). */
export interface DateCell { d: number }
/** Celda de fecha y hora (milisegundos epoch). */
export interface DateTimeCell { dt: number }
export type Cell = string | number | boolean | null | undefined | DateCell | DateTimeCell

export const isDateCell = (v: Cell): v is DateCell => typeof v === 'object' && v !== null && 'd' in v
export const isDateTimeCell = (v: Cell): v is DateTimeCell => typeof v === 'object' && v !== null && 'dt' in v
const D = (epochDay: number): DateCell => ({ d: epochDay })
const DT = (millis: number): DateTimeCell => ({ dt: millis })

/** Columna: `width` = ancho aproximado en caracteres (Excel) y peso relativo (impresión); `wrap` = texto largo. */
export interface ExportColumn { title: string; width: number; wrap: boolean }

export interface ExportTable {
  /** Nombre de archivo sin extensión (para CSV), sin acentos ni espacios. */
  id: string
  /** Título legible (nombre de hoja en Excel, sección en el reporte). Máx. 31 caracteres. */
  title: string
  module: ModuleKey
  columns: ExportColumn[]
  rows: Cell[][]
}

const c = (title: string, width = 14, wrap = false): ExportColumn => ({ title, width, wrap })
const orNull = (s: string | null | undefined): string | null => { const t = (s ?? '').trim(); return t === '' ? null : t }
const yesNo = (b: boolean) => (b ? 'Sí' : 'No')
const dayNames = ['lun', 'mar', 'mié', 'jue', 'vie', 'sáb', 'dom']

/** Máscara de días (bit 0 = lunes) → texto. */
export function daysMaskLabel(mask: number): string {
  const m = mask & 127
  if (m === 127) return 'Todos los días'
  if (m === 0) return 'Ningún día'
  return dayNames.filter((_, i) => (m & (1 << i)) !== 0).join(', ')
}

const labels = (csv: string, label: (k: string) => string): string | null => orNull(CycleCatalog.split(csv).map(label).join(', '))

export function resultInterpretation(r: StudyResult): string | null {
  if (r.value === null) return null
  if (r.refMin !== null && r.value < r.refMin) return 'Bajo'
  if (r.refMax !== null && r.value > r.refMax) return 'Alto'
  if (r.refMin !== null || r.refMax !== null) return 'En rango'
  return null
}

/** Cuántas tablas produce cada módulo (sirve para saber si un CSV será un solo archivo). */
export const tableCount = (m: ModuleKey): number => (m === 'CICLO' ? 3 : m === 'ESTUDIOS' || m === 'MEDICAMENTOS' ? 2 : 1)
export const tableCountOf = (o: ExportOptions): number => o.modules.reduce((n, m) => n + tableCount(m), 0)

/** Texto corto del tipo de perfil: "Persona", "Mascota (perro)". */
export const kindLabel = (kind: string, species: string): string =>
  kind === 'pet' ? 'Mascota' + (species.trim() ? ` (${PetSpecies.label(species).toLowerCase()})` : '') : 'Persona'

/** Otros registros de un día del ciclo, en texto. */
export function cycleDayExtras(d: CycleDay): string | null {
  const out: string[] = []
  if (d.mucus) out.push(`Flujo cervical: ${CycleCatalog.mucusLabel(d.mucus).toLowerCase()}`)
  if (d.basalTemp !== null) out.push(`Temp. basal: ${Fmt.num(d.basalTemp, 2)} °C`)
  if (d.sex !== 0) out.push(`Relaciones: ${CycleCatalog.sexLabel(d.sex).toLowerCase()}`)
  if (d.ovulationTest !== 0) out.push(`Prueba de ovulación: ${CycleCatalog.testLabel(d.ovulationTest).toLowerCase()}`)
  if (d.pregnancyTest !== 0) out.push(`Prueba de embarazo: ${CycleCatalog.testLabel(d.pregnancyTest).toLowerCase()}`)
  if (d.contraceptive) out.push('Anticonceptivo')
  return orNull(out.join('; '))
}

function cycleDays(data: ExportData): ExportTable {
  const a = data.cycleAnalysis
  return {
    id: 'ciclo_dias', title: 'Ciclo - días', module: 'CICLO',
    columns: [
      c('Fecha', 12), c('Día del ciclo', 8), c('Fase estimada', 18), c('Sangrado', 14), c('Síntomas', 34, true), c('Ánimo', 24, true),
      c('Flujo cervical', 14), c('Temp. basal (°C)', 10), c('Relaciones', 16), c('Prueba de ovulación', 12), c('Prueba de embarazo', 12),
      c('Anticonceptivo', 10), c('Notas', 40, true),
    ],
    rows: [...data.cycleDays].sort((x, y) => x.epochDay - y.epochDay).map((d) => {
      const info = a?.info(d.epochDay)
      return [
        D(d.epochDay), info?.cycleDay ?? null, info && info.cycleDay !== null ? info.phase : null, d.flow > 0 ? CycleCatalog.flowLabel(d.flow) : null,
        labels(d.symptoms, CycleCatalog.symptomLabel), labels(d.moods, CycleCatalog.moodLabel), orNull(d.mucus) ? CycleCatalog.mucusLabel(d.mucus) : null,
        d.basalTemp, d.sex !== 0 ? CycleCatalog.sexLabel(d.sex) : null, d.ovulationTest !== 0 ? CycleCatalog.testLabel(d.ovulationTest) : null,
        d.pregnancyTest !== 0 ? CycleCatalog.testLabel(d.pregnancyTest) : null, d.contraceptive ? 'Sí' : null, orNull(d.notes),
      ]
    }),
  }
}

function cycleSummary(data: ExportData): ExportTable {
  const luteal = data.cycleAnalysis?.lutealLength ?? 14
  return {
    id: 'ciclo_resumen', title: 'Ciclo - resumen', module: 'CICLO',
    columns: [
      c('Ciclo n.º', 8), c('Inicio del periodo', 14), c('Fin del periodo', 14), c('Duración del periodo (días)', 12),
      c('Duración del ciclo (días)', 12), c('Ovulación estimada', 14), c('Observación', 36, true),
    ],
    rows: data.cycles.map(({ index, value: r }) => [
      index + 1, D(r.start), D(r.start + r.periodLength - 1), r.periodLength, r.length,
      r.length !== null && r.valid && r.length - luteal >= 5 ? D(r.start + r.length - luteal) : null,
      r.length === null ? 'Ciclo en curso'
        : !r.valid ? `Atípico: no se usa en los promedios (menos de ${MIN_VALID_CYCLE} o más de ${MAX_VALID_CYCLE} días)`
        : null,
    ]),
  }
}

/** Indicadores del ciclo como pares (indicador, valor). */
export function cycleStatPairs(data: ExportData): [string, Cell][] {
  const a = data.cycleAnalysis
  if (!a) return []
  const r1 = (v: number) => Math.round(v * 10) / 10
  return [
    ['Periodos registrados', a.periods.length],
    ['Ciclos completos', a.cycles.filter((x) => x.length !== null).length],
    ['Duración promedio del ciclo (días)', a.avgCycle !== null ? r1(a.avgCycle) : null],
    ['Duración promedio del periodo (días)', a.avgPeriod !== null ? r1(a.avgPeriod) : null],
    ['Ciclo más corto reciente (días)', a.shortestCycle],
    ['Ciclo más largo reciente (días)', a.longestCycle],
    ['Variación entre ciclos (desviación estándar, días)', a.cycleStdDev !== null ? r1(a.cycleStdDev) : null],
    ['Regularidad', a.regular === true ? 'Regular (variación de 7 días o menos)' : a.regular === false ? 'Irregular (variación mayor a 7 días)' : 'Aún no hay suficientes ciclos para saberlo'],
    ['Inicio del último periodo', a.lastPeriod ? D(a.lastPeriod.start) : null],
    ['Próximo periodo estimado', a.nextPeriodStart !== null ? D(a.nextPeriodStart) : null],
    ['Duración de ciclo usada para estimar (días)', a.cycleLength],
    ['Fase lútea usada para estimar (días)', a.lutealLength],
    ['Promedios calculados con', `Los últimos ${RECENT} ciclos válidos`],
    ['Aviso', 'Las fechas estimadas son aproximaciones estadísticas; no sirven como método anticonceptivo.'],
  ]
}

const cycleStats = (data: ExportData): ExportTable => ({
  id: 'ciclo_estadisticas', title: 'Ciclo - estadísticas', module: 'CICLO',
  columns: [c('Indicador', 46), c('Valor', 46, true)],
  rows: cycleStatPairs(data).map(([k, v]) => [k, v]),
})

const typeOrder = (key: string): number => { const i = MEASURE_TYPES.findIndex((t) => t.key === key); return i < 0 ? 99 : i }

const measurements = (data: ExportData): ExportTable => ({
  id: 'mediciones', title: 'Peso y mediciones', module: 'MEDICIONES',
  columns: [c('Fecha y hora', 18), c('Tipo', 20), c('Valor', 10), c('Segundo valor', 10), c('Unidad', 9), c('Contexto', 22), c('Notas', 40, true)],
  rows: [...data.measurements]
    .sort((x, y) => typeOrder(x.type) - typeOrder(y.type) || x.type.localeCompare(y.type) || x.timestamp - y.timestamp)
    .map((m) => { const t = measureType(m.type); return [DT(m.timestamp), t?.label ?? m.type, m.value, m.value2, t?.unit ?? null, orNull(m.tag), orNull(m.notes)] }),
})

const symptoms = (data: ExportData): ExportTable => ({
  id: 'sintomas', title: 'Síntomas', module: 'SINTOMAS',
  columns: [
    c('Fecha y hora', 18), c('Síntoma', 24, true), c('Intensidad (1-10)', 10), c('Nivel', 12), c('Zona del cuerpo', 24, true),
    c('Duración (min)', 10), c('Posibles desencadenantes', 26, true), c('Notas', 40, true),
  ],
  rows: [...data.symptoms].sort((x, y) => x.timestamp - y.timestamp).map((s) => [
    DT(s.timestamp), s.name, s.intensity, SymptomCatalog.intensityLabel(s.intensity), orNull(s.regionLabel), s.durationMin,
    orNull(CycleCatalog.split(s.tags).join(', ')), orNull(s.notes),
  ]),
})

const wellbeing = (data: ExportData): ExportTable => ({
  id: 'bienestar', title: 'Bienestar', module: 'BIENESTAR',
  columns: [
    c('Fecha', 12), c('Ánimo (1-5)', 9), c('Energía (1-5)', 9), c('Estrés (1-5)', 9), c('Sueño (horas)', 9), c('Vasos de agua', 9),
    c('Ejercicio (min)', 9), c('Notas', 44, true),
  ],
  rows: [...data.dailyLogs].sort((x, y) => x.epochDay - y.epochDay).map((d) => [D(d.epochDay), d.mood, d.energy, d.stress, d.sleepHours, d.waterGlasses, d.exerciseMin, orNull(d.notes)]),
})

function studies(data: ExportData): ExportTable {
  const att = new Map<number, string[]>()
  for (const a of data.studyAttachments) att.set(a.studyId, [...(att.get(a.studyId) ?? []), a.displayName])
  return {
    id: 'estudios', title: 'Estudios', module: 'ESTUDIOS',
    columns: [
      c('Fecha', 12), c('Estudio', 30, true), c('Categoría', 16), c('Lugar', 22, true), c(dataIsPet(data) ? 'Veterinario' : 'Médico', 20, true),
      c('Estado', 20), c('Resumen', 44, true), c('Seguimiento', 12), c('Archivos adjuntos', 24, true),
    ],
    rows: [...data.studies].sort((x, y) => x.epochDay - y.epochDay).map((s) => [
      D(s.epochDay), s.title, StudyCategories.short(s.category), orNull(s.place), orNull(s.doctor), orNull(StudyCategories.statusLabel(s.status)),
      orNull(s.summary), s.followUpEpochDay !== null ? D(s.followUpEpochDay) : null, orNull(att.get(s.id as number)?.join(', ')),
    ]),
  }
}

function studyResults(data: ExportData): ExportTable {
  const byId = new Map(data.studies.map((s) => [s.id as number, s]))
  return {
    id: 'estudios_resultados', title: 'Resultados', module: 'ESTUDIOS',
    columns: [
      c('Fecha', 12), c('Estudio', 28, true), c('Parámetro', 24, true), c('Valor', 10), c('Resultado (texto)', 20, true), c('Unidad', 9),
      c('Ref. mínima', 9), c('Ref. máxima', 9), c('Interpretación', 12), c('Notas', 30, true),
    ],
    rows: [...data.studyResults]
      .sort((x, y) => (byId.get(x.studyId)?.epochDay ?? 0) - (byId.get(y.studyId)?.epochDay ?? 0) || x.studyId - y.studyId || (x.id ?? 0) - (y.id ?? 0))
      .map((r) => {
        const s = byId.get(r.studyId)
        return [s ? D(s.epochDay) : null, s?.title ?? null, r.name, r.value, orNull(r.textValue), orNull(r.unit), r.refMin, r.refMax, resultInterpretation(r), orNull(r.notes)]
      }),
  }
}

const medications = (data: ExportData): ExportTable => ({
  id: 'medicamentos', title: 'Medicamentos', module: 'MEDICAMENTOS',
  columns: [
    c('Medicamento', 24, true), c('Dosis', 14), c('Indicaciones', 28, true), c('Horarios', 18, true), c('Días', 16), c('Inicio', 12), c('Fin', 12),
    c('Activo', 7), c('Recordatorio', 9), c('Notas', 30, true),
  ],
  rows: [...data.medications].sort((x, y) => Number(!x.active) - Number(!y.active) || x.name.toLowerCase().localeCompare(y.name.toLowerCase())).map((m) => [
    m.name, orNull(m.dose), orNull(m.instructions), CycleCatalog.split(m.times).join(', ') || 'Según sea necesario', daysMaskLabel(m.daysMask),
    D(m.startEpochDay), m.endEpochDay !== null ? D(m.endEpochDay) : null, yesNo(m.active), yesNo(m.remind), orNull(m.notes),
  ]),
})

function intakes(data: ExportData): ExportTable {
  const byId = new Map(data.medications.map((m) => [m.id as number, m]))
  return {
    id: 'medicamentos_tomas', title: 'Tomas', module: 'MEDICAMENTOS',
    columns: [c('Fecha', 12), c('Hora programada', 10), c('Registrada', 18), c('Medicamento', 24, true), c('Dosis', 14), c('Estado', 10)],
    rows: [...data.medIntakes].sort((x, y) => x.timestamp - y.timestamp).map((i) => {
      const m = byId.get(i.medicationId)
      return [D(i.epochDay), i.slot || 'Libre', DT(i.timestamp), m?.name ?? null, orNull(m?.dose), i.status === 'skipped' ? 'Omitida' : 'Tomada']
    }),
  }
}

const appointments = (data: ExportData): ExportTable => ({
  id: 'citas', title: 'Citas', module: 'CITAS',
  columns: [c('Fecha y hora', 18), c('Motivo', 28, true), c(dataIsPet(data) ? 'Veterinario' : 'Médico', 20, true), c('Especialidad', 18, true), c('Lugar', 28, true), c('Realizada', 9), c('Notas', 36, true)],
  rows: [...data.appointments].sort((x, y) => x.timestamp - y.timestamp).map((a) => [DT(a.timestamp), a.title, orNull(a.doctor), orNull(a.specialty), orNull(a.place), yesNo(a.done), orNull(a.notes)]),
})

const vaccinations = (data: ExportData): ExportTable => ({
  id: 'vacunas', title: 'Vacunas', module: 'VACUNAS',
  columns: [c('Fecha', 12), c('Tipo', 22), c('Nombre', 28, true), c('Dosis', 16), c('Próxima', 12), c('Lugar', 26, true), c('Lote', 14), c('Recordatorio', 11), c('Notas', 40, true)],
  rows: [...data.vaccinations].sort((x, y) => x.epochDay - y.epochDay || (x.id ?? 0) - (y.id ?? 0)).map((v) => [
    D(v.epochDay), VaccinationTypes.label(v.type), v.name, orNull(v.dose), v.nextDueEpochDay !== null ? D(v.nextDueEpochDay) : null, orNull(v.place),
    orNull(v.lot), v.nextDueEpochDay !== null ? yesNo(v.remind) : null, orNull(v.notes),
  ]),
})

const selfExams = (data: ExportData): ExportTable => ({
  id: 'autoexploracion', title: 'Autoexploración', module: 'AUTOEXPLORACION',
  columns: [c('Fecha', 12), c('Tipo', 18), c('Resultado', 16), c('Notas', 50, true)],
  rows: [...data.selfExams].sort((x, y) => x.epochDay - y.epochDay).map((e) => [D(e.epochDay), SelfExamTypes.label(e.type), e.normal ? 'Sin cambios' : 'Noté algo', orNull(e.notes)]),
})

/** Datos de la ficha como pares (campo, valor). Omite los vacíos si `skipEmpty`. */
export function profilePairs(data: ExportData, skipEmpty = false): [string, Cell][] {
  const p = data.profile
  if (!p) return []
  const pet = p.kind === 'pet'
  const all: [string, Cell][] = pet
    ? [
        ['Nombre', orNull(p.name)], ['Tipo de perfil', 'Mascota'], ['Especie', orNull(p.species) ? PetSpecies.label(p.species) : null], ['Raza', orNull(p.breed)],
        ['Sexo', p.sex !== 'X' ? PetSpecies.sexes.find((s) => s[0] === p.sex)?.[1] ?? null : null],
        ['Fecha de nacimiento', p.birthEpochDay !== null ? D(p.birthEpochDay) : null], ['Edad (años)', p.birthEpochDay !== null ? Fmt.age(p.birthEpochDay, data.today) : null],
        ['Esterilizado', p.sterilized !== null ? yesNo(p.sterilized) : null], ['Microchip o placa', orNull(p.microchip)], ['Color y señas', orNull(p.markings)],
        ['Peso meta (kg)', p.goalWeightKg], ['Alergias', orNull(p.allergies)], ['Padecimientos', orNull(p.conditions)], ['Veterinario', orNull(p.doctorName)],
        ['Teléfono del veterinario', orNull(p.doctorPhone)], ['Notas', orNull(p.notes)],
      ]
    : [
        ['Nombre', orNull(p.name)], ['Tipo de perfil', 'Persona'], ['Parentesco', orNull(p.relation)], ['Sexo', orNull(SexOptions.label(p.sex))],
        ['Fecha de nacimiento', p.birthEpochDay !== null ? D(p.birthEpochDay) : null], ['Edad (años)', p.birthEpochDay !== null ? Fmt.age(p.birthEpochDay, data.today) : null],
        ['Estatura (cm)', p.heightCm], ['Peso meta (kg)', p.goalWeightKg], ['Tipo de sangre', orNull(p.bloodType)], ['Alergias', orNull(p.allergies)],
        ['Padecimientos', orNull(p.conditions)], ['Contacto de emergencia', orNull(p.emergencyName)], ['Teléfono de emergencia', orNull(p.emergencyPhone)],
        ['Médico de cabecera', orNull(p.doctorName)], ['Teléfono del médico', orNull(p.doctorPhone)], ['Seguro médico', orNull(p.insurance)], ['Notas', orNull(p.notes)],
      ]
  if (!skipEmpty) return all
  const filled = all.filter(([, v]) => v !== null && v !== undefined)
  // "Tipo de perfil" siempre trae valor: si es lo único (ficha de persona en blanco), no hay nada que mostrar.
  return !pet && filled.every(([k]) => k === 'Tipo de perfil' || (k === 'Sexo' && p.sex === 'X')) ? [] : filled
}

const profile = (data: ExportData): ExportTable => ({
  id: 'ficha', title: 'Perfil', module: 'PERFIL',
  columns: [c('Campo', 26), c('Valor', 50, true)],
  rows: profilePairs(data).map(([k, v]) => [k, v]),
})

/** Todas las tablas de los módulos pedidos, en un orden estable. */
export function buildTables(data: ExportData): ExportTable[] {
  const out: ExportTable[] = []
  if (has(data, 'CICLO')) out.push(cycleDays(data), cycleSummary(data), cycleStats(data))
  if (has(data, 'MEDICIONES')) out.push(measurements(data))
  if (has(data, 'SINTOMAS')) out.push(symptoms(data))
  if (has(data, 'BIENESTAR')) out.push(wellbeing(data))
  if (has(data, 'ESTUDIOS')) out.push(studies(data), studyResults(data))
  if (has(data, 'VACUNAS')) out.push(vaccinations(data))
  if (has(data, 'MEDICAMENTOS')) out.push(medications(data), intakes(data))
  if (has(data, 'CITAS')) out.push(appointments(data))
  if (has(data, 'AUTOEXPLORACION')) out.push(selfExams(data))
  if (has(data, 'PERFIL')) out.push(profile(data))
  return out
}

/** Valor de celda como texto legible (reporte) — fechas en español. */
export function display(v: Cell): string {
  if (v === null || v === undefined) return ''
  if (isDateCell(v)) return Fmt.date(v.d)
  if (isDateTimeCell(v)) return Fmt.dateTime(v.dt)
  if (typeof v === 'number') return Number.isInteger(v) ? String(v) : Fmt.num(v, 2)
  if (typeof v === 'boolean') return yesNo(v)
  return String(v)
}

/** Valor de celda como texto neutro para máquinas (CSV) — fechas ISO, punto decimal. */
export function plain(v: Cell): string {
  if (v === null || v === undefined) return ''
  if (isDateCell(v)) return Fmt.isoDate(v.d)
  if (isDateTimeCell(v)) return Fmt.isoDateTime(v.dt)
  if (typeof v === 'number') return Number.isInteger(v) ? String(v) : Fmt.num(v, 4)
  if (typeof v === 'boolean') return yesNo(v)
  return String(v)
}

/** Texto del rango de fechas de unas opciones. */
export function rangeLabel(o: ExportOptions): string {
  if (o.fromDay === null && o.toDay === null) return 'Todo el historial'
  if (o.fromDay !== null && o.toDay !== null) return `Del ${Fmt.date(o.fromDay)} al ${Fmt.date(o.toDay)}`
  if (o.fromDay !== null) return `Desde el ${Fmt.date(o.fromDay)}`
  return `Hasta el ${Fmt.date(o.toDay as number)}`
}
