// Escritores de archivos: Excel (.xlsx), CSV y JSON legible. Ports de XlsxWriter.kt y TextWriters.kt.

import { strToU8, zipSync } from 'fflate'
import { Fmt, epochDayToIso, millisToEpochDay, millisToMinutes, today } from '../../lib/dates'
import { slug } from '../../lib/files'
import { MODULES, dataIsPet, dataProfileName, has, moduleLabel, moduleOf, type ExportData, type ExportFormat, type ExportOptions } from './exportData'
import { buildTables, isDateCell, isDateTimeCell, kindLabel, plain, rangeLabel, tableCountOf, type Cell, type ExportTable } from './tables'

// ── Excel ─────────────────────────────────────────────────────────────────────────────────────────

const XML = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
const NS_MAIN = 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'
const NS_REL = 'http://schemas.openxmlformats.org/officeDocument/2006/relationships'
const NS_PKG_REL = 'http://schemas.openxmlformats.org/package/2006/relationships'
// Índices de estilos (cellXfs) definidos en styles.xml
const S_DEFAULT = 0, S_HEADER = 1, S_DATE = 2, S_DATETIME = 3, S_WRAP = 4
/** Días entre 1899-12-30 (día 0 de Excel) y 1970-01-01. */
const EXCEL_EPOCH_OFFSET = 25569

/** Escapa texto para XML y quita caracteres que XML 1.0 no permite. */
export function esc(s: string): string {
  let out = ''
  for (let i = 0; i < s.length; i++) {
    const ch = s[i]
    const code = s.charCodeAt(i)
    if (ch === '&') out += '&amp;'
    else if (ch === '<') out += '&lt;'
    else if (ch === '>') out += '&gt;'
    else if (ch === '"') out += '&quot;'
    else if (ch === "'") out += '&apos;'
    else if (ch === '\r') { if (s[i + 1] !== '\n') out += '\n' }
    else if (ch === '\t' || ch === '\n') out += ch
    else if (code < 0x20 || code === 0xfffe || code === 0xffff) { /* no permitido en XML */ }
    else if (code >= 0xd800 && code <= 0xdbff) {
      const next = s.charCodeAt(i + 1)
      if (next >= 0xdc00 && next <= 0xdfff) { out += ch + s[i + 1]; i++ }
    } else if (code >= 0xdc00 && code <= 0xdfff) { /* sustituto suelto */ }
    else out += ch
  }
  return out
}

/** 0 → A, 25 → Z, 26 → AA… */
export function colName(index: number): string {
  let i = index
  let s = ''
  do {
    s = String.fromCharCode(65 + (i % 26)) + s
    i = Math.floor(i / 26) - 1
  } while (i >= 0)
  return s
}

/** Nombres de hoja válidos para Excel: sin []:*?/\, máx. 31 caracteres, únicos. */
export function uniqueSheetNames(titles: string[]): string[] {
  const used = new Set<string>()
  return titles.map((raw) => {
    const base = raw.replace(/[[\]:*?/\\]/g, ' ').trim().replace(/^'+|'+$/g, '').slice(0, 31) || 'Hoja'
    let name = base
    for (let n = 2; used.has(name.toLowerCase()); n++) {
      const suffix = ` (${n})`
      name = base.slice(0, 31 - suffix.length) + suffix
    }
    used.add(name.toLowerCase())
    return name
  })
}

const numText = (n: number): string => {
  if (Number.isInteger(n)) return String(n)
  // Sin notación científica.
  return String(Number(n.toFixed(8)))
}

/** Milisegundos epoch → número de serie de Excel en hora local. */
function excelDateTime(millis: number): string {
  const serial = millisToEpochDay(millis) + EXCEL_EPOCH_OFFSET + (millisToMinutes(millis) * 60 + new Date(millis).getSeconds()) / 86400
  return String(Number(serial.toFixed(8)))
}

const stringCell = (ref: string, text: string, style: number) => `<c r="${ref}" s="${style}" t="inlineStr"><is><t xml:space="preserve">${esc(text.slice(0, 32000))}</t></is></c>`
const numberCell = (ref: string, value: string, style: number) => `<c r="${ref}" s="${style}"><v>${value}</v></c>`

function sheetXml(t: ExportTable, selected: boolean): string {
  const cols = Math.max(1, t.columns.length)
  const lastCol = colName(cols - 1)
  const lastRow = t.rows.length + 1
  const p: string[] = [XML, `<worksheet xmlns="${NS_MAIN}" xmlns:r="${NS_REL}">`, `<dimension ref="A1:${lastCol}${lastRow}"/>`]
  p.push(`<sheetViews><sheetView workbookViewId="0"${selected ? ' tabSelected="1"' : ''}><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>`)
  p.push('<selection pane="bottomLeft" activeCell="A2" sqref="A2"/></sheetView></sheetViews><sheetFormatPr defaultRowHeight="15"/><cols>')
  t.columns.forEach((c, i) => p.push(`<col min="${i + 1}" max="${i + 1}" width="${Math.min(80, Math.max(6, c.width)) + 2}" customWidth="1"/>`))
  p.push('</cols><sheetData><row r="1" ht="32" customHeight="1">')
  t.columns.forEach((c, i) => p.push(stringCell(`${colName(i)}1`, c.title, S_HEADER)))
  p.push('</row>')
  t.rows.forEach((row, ri) => {
    const r = ri + 2
    p.push(`<row r="${r}">`)
    for (let ci = 0; ci < Math.min(row.length, cols); ci++) {
      const ref = colName(ci) + r
      const v: Cell = row[ci]
      if (v === null || v === undefined) continue
      if (isDateCell(v)) p.push(numberCell(ref, String(v.d + EXCEL_EPOCH_OFFSET), S_DATE))
      else if (isDateTimeCell(v)) p.push(numberCell(ref, excelDateTime(v.dt), S_DATETIME))
      else if (typeof v === 'number') { if (Number.isFinite(v)) p.push(numberCell(ref, numText(v), S_DEFAULT)) }
      else if (typeof v === 'boolean') p.push(stringCell(ref, v ? 'Sí' : 'No', S_DEFAULT))
      else if (v !== '') p.push(stringCell(ref, String(v), t.columns[ci]?.wrap ? S_WRAP : S_DEFAULT))
    }
    p.push('</row>')
  })
  p.push('</sheetData>')
  if (t.rows.length > 0) p.push(`<autoFilter ref="A1:${lastCol}${lastRow}"/>`)
  p.push('<pageMargins left="0.5" right="0.5" top="0.75" bottom="0.75" header="0.3" footer="0.3"/><pageSetup orientation="landscape"/></worksheet>')
  return p.join('')
}

const stylesXml = (): string => XML + `<styleSheet xmlns="${NS_MAIN}">` +
  '<numFmts count="2"><numFmt numFmtId="164" formatCode="yyyy\\-mm\\-dd"/><numFmt numFmtId="165" formatCode="yyyy\\-mm\\-dd\\ hh:mm"/></numFmts>' +
  '<fonts count="2"><font><sz val="11"/><name val="Calibri"/><family val="2"/></font><font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/><family val="2"/></font></fonts>' +
  '<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF1B6B58"/><bgColor indexed="64"/></patternFill></fill></fills>' +
  '<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>' +
  '<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>' +
  '<cellXfs count="5">' +
  '<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment vertical="top"/></xf>' +
  '<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf>' +
  '<xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyAlignment="1"><alignment horizontal="left" vertical="top"/></xf>' +
  '<xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyAlignment="1"><alignment horizontal="left" vertical="top"/></xf>' +
  '<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf>' +
  '</cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>'

/**
 * Libro de Excel (.xlsx, OOXML) sin dependencias de Office: una hoja por tabla, encabezado con estilo y congelado,
 * filtros, anchos de columna, fechas como fechas reales de Excel y números como números.
 */
export function writeXlsx(tables: ExportTable[], title = 'Savia'): Uint8Array {
  const sheets = tables.length ? tables : [{ id: 'vacio', title: 'Sin datos', module: 'PERFIL' as const, columns: [{ title: 'Sin datos', width: 30, wrap: false }], rows: [] }]
  const names = uniqueSheetNames(sheets.map((t) => t.title))
  const n = sheets.length
  const now = new Date().toISOString().replace(/\.\d+Z$/, 'Z')
  const files: Record<string, Uint8Array> = {}
  const put = (name: string, content: string) => { files[name] = strToU8(content) }
  put('[Content_Types].xml', XML + '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">' +
    '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/>' +
    '<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>' +
    sheets.map((_, i) => `<Override PartName="/xl/worksheets/sheet${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>`).join('') +
    '<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>' +
    '<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>' +
    '<Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/></Types>')
  put('_rels/.rels', XML + `<Relationships xmlns="${NS_PKG_REL}"><Relationship Id="rId1" Type="${NS_REL}/officeDocument" Target="xl/workbook.xml"/>` +
    '<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>' +
    `<Relationship Id="rId3" Type="${NS_REL}/extended-properties" Target="docProps/app.xml"/></Relationships>`)
  put('docProps/core.xml', XML + '<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" ' +
    'xmlns:dcterms="http://purl.org/dc/terms/" xmlns:dcmitype="http://purl.org/dc/dcmitype/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">' +
    `<dc:title>${esc(title)}</dc:title><dc:creator>Savia</dc:creator><dcterms:created xsi:type="dcterms:W3CDTF">${now}</dcterms:created>` +
    `<dcterms:modified xsi:type="dcterms:W3CDTF">${now}</dcterms:modified></cp:coreProperties>`)
  put('docProps/app.xml', XML + '<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"><Application>Savia</Application></Properties>')
  put('xl/workbook.xml', XML + `<workbook xmlns="${NS_MAIN}" xmlns:r="${NS_REL}"><bookViews><workbookView xWindow="0" yWindow="0" windowWidth="24000" windowHeight="12000"/></bookViews><sheets>` +
    names.map((name, i) => `<sheet name="${esc(name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>`).join('') + '</sheets></workbook>')
  put('xl/_rels/workbook.xml.rels', XML + `<Relationships xmlns="${NS_PKG_REL}">` +
    sheets.map((_, i) => `<Relationship Id="rId${i + 1}" Type="${NS_REL}/worksheet" Target="worksheets/sheet${i + 1}.xml"/>`).join('') +
    `<Relationship Id="rId${n + 1}" Type="${NS_REL}/styles" Target="styles.xml"/></Relationships>`)
  put('xl/styles.xml', stylesXml())
  sheets.forEach((t, i) => put(`xl/worksheets/sheet${i + 1}.xml`, sheetXml(t, i === 0)))
  return zipSync(files, { level: 6 })
}

// ── CSV (RFC 4180: coma, comillas dobles, CRLF, UTF-8 con BOM para que Excel respete los acentos) ──

const BOM = new Uint8Array([0xef, 0xbb, 0xbf])

/** Escapa un campo: se entrecomilla si trae coma, comillas, saltos de línea o espacios en los extremos. */
export function csvField(s: string): string {
  const needs = /[,"\n\r]/.test(s) || (s.length > 0 && (s[0] === ' ' || s[s.length - 1] === ' '))
  return needs ? `"${s.replace(/"/g, '""')}"` : s
}

export const csvLine = (values: string[]): string => values.map(csvField).join(',') + '\r\n'

/** Texto CSV completo de una tabla (sin BOM). */
export function csvText(table: ExportTable): string {
  let out = csvLine(table.columns.map((c) => c.title))
  for (const row of table.rows) out += csvLine(table.columns.map((_, i) => plain(row[i])))
  return out
}

const withBom = (text: string): Uint8Array => {
  const body = strToU8(text)
  const out = new Uint8Array(BOM.length + body.length)
  out.set(BOM)
  out.set(body, BOM.length)
  return out
}

/** Un solo archivo .csv. */
export const writeCsv = (table: ExportTable): Uint8Array => withBom(csvText(table))

/** Un .zip con un CSV por tabla. */
export function writeCsvZip(tables: ExportTable[]): Uint8Array {
  const files: Record<string, Uint8Array> = {}
  const used = new Set<string>()
  for (const t of tables) {
    let name = t.id
    for (let n = 2; used.has(name); n++) name = `${t.id}_${n}`
    used.add(name)
    files[`${name}.csv`] = withBom(csvText(t))
  }
  files['LEEME.txt'] = strToU8('Exportación de Savia\r\nCada archivo .csv es una tabla (UTF-8, separado por comas).\r\nLas fechas van en formato AAAA-MM-DD y los decimales con punto.\r\n')
  return zipSync(files, { level: 6 })
}

// ── JSON legible ──────────────────────────────────────────────────────────────────────────────────

const jsonValue = (v: Cell): unknown => {
  if (v === null || v === undefined) return null
  if (isDateCell(v)) return Fmt.isoDate(v.d)
  if (isDateTimeCell(v)) return Fmt.isoDateTime(v.dt)
  if (typeof v === 'number') return Number.isFinite(v) ? v : null
  return v
}

/** JSON abierto: las mismas tablas que Excel, con encabezados en español. (No sirve para restaurar: para eso es el respaldo.) */
export function buildJsonExport(data: ExportData, tables: ExportTable[] = buildTables(data)): Record<string, unknown> {
  const pet = dataIsPet(data)
  const tablas: Record<string, unknown> = {}
  for (const t of tables) tablas[t.title] = t.rows.map((row) => Object.fromEntries(t.columns.map((c, i) => [c.title, jsonValue(row[i])])))
  return {
    savia: {
      aplicacion: 'Savia', tipo: 'exportacion', version_formato: 1, generado: Fmt.isoDateTime(data.generatedAt),
      perfil: dataProfileName(data) || null,
      tipo_perfil: data.profile ? kindLabel(data.profile.kind, data.profile.species) : null,
      rango: rangeLabel(data.options),
      desde: data.options.fromDay !== null ? Fmt.isoDate(data.options.fromDay) : null,
      hasta: data.options.toDay !== null ? Fmt.isoDate(data.options.toDay) : null,
      modulos: MODULES.filter((m) => has(data, m.key)).map((m) => moduleLabel(m, pet)),
      nota: 'Fechas en formato AAAA-MM-DD (hora local). Los valores vacíos van como null.',
    },
    tablas,
  }
}

// ── Archivo final ─────────────────────────────────────────────────────────────────────────────────

export interface ExportFile { name: string; mime: string; bytes: Uint8Array }

/** Nombre sugerido: "savia[-perfil]-<qué>-AAAA-MM-DD.ext". `profileName` solo se pasa cuando hay más de un perfil. */
export function exportFileName(format: Exclude<ExportFormat, 'PRINT'>, options: ExportOptions, profileName?: string, day: number = today()): string {
  const what = options.modules.length === 1 ? moduleOf(options.modules[0]).slug : 'datos'
  const who = profileName && profileName.trim() ? `${slug(profileName)}-` : ''
  const ext = format === 'XLSX' ? 'xlsx' : format === 'JSON' ? 'json' : tableCountOf(options) === 1 ? 'csv' : 'zip'
  const tag = format === 'CSV' && ext === 'zip' ? '-csv' : format === 'JSON' ? '-exportacion' : ''
  return `savia-${who}${what}${tag}-${epochDayToIso(day)}.${ext}`
}

/** Genera el archivo de exportación (Excel, CSV o JSON legible) a partir de los datos ya cargados. */
export function buildExportFile(format: Exclude<ExportFormat, 'PRINT'>, data: ExportData, profileName?: string): ExportFile {
  const tables = buildTables(data)
  const name = exportFileName(format, data.options, profileName, data.today)
  if (format === 'XLSX') {
    return { name, mime: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', bytes: writeXlsx(tables, 'Savia — ' + (dataProfileName(data) || 'Exportación')) }
  }
  if (format === 'JSON') return { name, mime: 'application/json', bytes: strToU8(JSON.stringify(buildJsonExport(data, tables), null, 2)) }
  return tables.length === 1
    ? { name, mime: 'text/csv', bytes: writeCsv(tables[0]) }
    : { name, mime: 'application/zip', bytes: writeCsvZip(tables) }
}
