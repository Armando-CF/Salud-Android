import 'fake-indexeddb/auto'
import { strFromU8, unzipSync } from 'fflate'
import { beforeEach, describe, expect, it } from 'vitest'
import { dbFor } from '../../data/db'
import { ymdToEpochDay } from '../../lib/dates'
import { analyzeCycle, periodsOf } from './cycleLite'
import { ALL_OPTIONS, CYCLE_ONLY, dataIsEmpty, loadExportData } from './exportData'
import { buildTables, daysMaskLabel, display, plain, rangeLabel, type ExportTable } from './tables'
import { buildExportFile, buildJsonExport, colName, csvField, csvText, esc, exportFileName, uniqueSheetNames, writeCsvZip, writeXlsx } from './writers'
import { cyc, resetAll, seedFamily } from './testkit'

beforeEach(async () => {
  await resetAll()
})

/** Comprobación mínima de XML bien formado: etiquetas balanceadas y sin caracteres prohibidos. */
function assertWellFormed(xml: string): void {
  expect(xml.startsWith('<?xml')).toBe(true)
  // eslint-disable-next-line no-control-regex
  expect(/[\u0000-\u0008\u000B\u000C\u000E-\u001F]/.test(xml)).toBe(false)
  const body = xml.replace(/^<\?xml[^>]*\?>\s*/, '')
  const stack: string[] = []
  const re = /<(\/?)([A-Za-z_][\w:.-]*)((?:\s+[\w:.-]+="[^"<]*")*)\s*(\/?)>/g
  let last = 0
  let m: RegExpExecArray | null
  while ((m = re.exec(body))) {
    const text = body.slice(last, m.index)
    expect(text.includes('<')).toBe(false)
    expect(/&(?!(amp|lt|gt|quot|apos);)/.test(text)).toBe(false)
    last = re.lastIndex
    if (m[1]) expect(stack.pop()).toBe(m[2])
    else if (!m[4]) stack.push(m[2])
  }
  expect(body.slice(last).trim()).toBe('')
  expect(stack).toEqual([])
}

describe('motor del ciclo (port de CycleEngine.kt)', () => {
  const bleed = (start: number, n: number) => Array.from({ length: n }, (_, i) => cyc(start + i, { flow: 3 }))

  it('agrupa periodos y predice con el promedio reciente', () => {
    expect(periodsOf([cyc(100, { flow: 3 }), cyc(101, { flow: 4 }), cyc(103, { flow: 2 }), cyc(104, { flow: 1 }), cyc(128, { flow: 3 }), cyc(129, { flow: 3 })])).toEqual([{ start: 100, end: 103 }, { start: 128, end: 129 }])
    expect(periodsOf([cyc(100, { flow: 1 })])).toEqual([])
    const a = analyzeCycle([...bleed(1000, 5), ...bleed(1028, 5), ...bleed(1058, 4), ...bleed(1084, 5)], 28, 5, 14, 1095)
    expect(a.periods.length).toBe(4)
    expect(a.cycleLength).toBe(28)
    expect(a.avgCycle).toBeCloseTo(28)
    expect(a.nextPeriodStart).toBe(1112)
    expect(a.info(1095).cycleDay).toBe(12)
    expect(a.info(1098).phase).toBe('Ovulación estimada')
    expect(a.info(1095).phase).toBe('Ventana fértil')
    expect(a.info(1105).phase).toBe('Fase lútea')
    expect(a.info(1085).phase).toBe('Menstruación')
    expect([a.shortestCycle, a.longestCycle, a.regular]).toEqual([26, 30, true])
  })

  it('con retraso el día de hoy sigue contando desde el último periodo real', () => {
    const a = analyzeCycle(bleed(1000, 5), 30, 5, 14, 1034)
    expect(a.avgCycle).toBeNull()
    expect(a.nextPeriodStart).toBe(1034)
    expect(a.info(1034).cycleDay).toBe(35)
    expect(a.info(1035).cycleDay).toBe(2)
  })

  it('descarta ciclos atípicos del promedio', () => {
    const a = analyzeCycle([...bleed(1000, 5), ...bleed(1028, 5), ...bleed(1148, 5), ...bleed(1176, 5)], 28, 5, 14, 1180)
    expect(a.avgCycle).toBeCloseTo(28)
    expect(a.cycles[1].valid).toBe(false)
  })
})

describe('tablas de exportación', () => {
  it('carga todo, respeta el rango y arma las mismas tablas que Android', async () => {
    await seedFamily()
    const data = await loadExportData(dbFor(1), ALL_OPTIONS, 19700)
    expect(dataIsEmpty(data)).toBe(false)
    const tables = buildTables(data)
    expect(tables.map((t) => t.title)).toEqual(['Ciclo - días', 'Ciclo - resumen', 'Ciclo - estadísticas', 'Peso y mediciones', 'Síntomas', 'Bienestar', 'Estudios', 'Resultados', 'Vacunas', 'Medicamentos', 'Tomas', 'Citas', 'Autoexploración', 'Perfil'])
    const byTitle = (t: string) => tables.find((x) => x.title === t) as ExportTable
    // Ciclo: 4 periodos de 4 días + el día con prueba de embarazo
    expect(byTitle('Ciclo - días').rows.length).toBe(17)
    const test = byTitle('Ciclo - días').rows.find((r) => (r[0] as { d: number }).d === 19660)!
    expect(test[10]).toBe('Negativa')
    expect(test[8]).toBe('Sin protección')
    expect(byTitle('Ciclo - resumen').rows.map((r) => r[4])).toEqual([28, 30, 26, null])
    expect(byTitle('Ciclo - resumen').rows[3][6]).toBe('Ciclo en curso')
    expect(byTitle('Ciclo - estadísticas').rows[0]).toEqual(['Periodos registrados', 4])
    expect(byTitle('Resultados').rows.map((r) => r[8])).toEqual(['En rango', 'Alto', null])
    expect(byTitle('Estudios').rows[0][8]).toBe('resultados.pdf')
    expect(byTitle('Peso y mediciones').rows.map((r) => r[1])).toEqual(['Peso', 'Presión arterial'])
    expect(byTitle('Medicamentos').rows[0][3]).toBe('08:00')
    expect(byTitle('Perfil').rows.find((r) => r[0] === 'Tipo de sangre')?.[1]).toBe('O+')

    // Rango: solo lo que cae dentro
    const ranged = await loadExportData(dbFor(1), { ...ALL_OPTIONS, fromDay: 19670, toDay: 19700 }, 19700)
    expect(ranged.symptoms.length).toBe(2)
    expect(ranged.cycleDays.length).toBe(0)
    expect(ranged.studies.length).toBe(0)
    expect(ranged.dailyLogs.length).toBe(1)
    expect(rangeLabel(ranged.options)).toMatch(/^Del .* al /)

    // Solo ciclo
    const cycle = await loadExportData(dbFor(1), CYCLE_ONLY, 19700)
    expect(buildTables(cycle).map((t) => t.id)).toEqual(['ciclo_dias', 'ciclo_resumen', 'ciclo_estadisticas'])
    expect(cycle.profile?.name).toBe('Alex')
    expect(cycle.profile?.bloodType).toBe('')
  })

  it('en una mascota se omiten ciclo, bienestar y autoexploración', async () => {
    const { dog } = await seedFamily()
    const data = await loadExportData(dbFor(dog), ALL_OPTIONS, 19700)
    expect(data.options.modules).not.toContain('CICLO')
    expect(data.options.modules).not.toContain('BIENESTAR')
    expect(data.options.modules).not.toContain('AUTOEXPLORACION')
    const tables = buildTables(data)
    expect(tables.map((t) => t.title)).toEqual(['Peso y mediciones', 'Síntomas', 'Estudios', 'Resultados', 'Vacunas', 'Medicamentos', 'Tomas', 'Citas', 'Perfil'])
    const ficha = tables[tables.length - 1].rows
    expect(ficha.find((r) => r[0] === 'Especie')?.[1]).toBe('Perro')
    expect(ficha.find((r) => r[0] === 'Esterilizado')?.[1]).toBe('Sí')
    expect(tables.find((t) => t.title === 'Vacunas')?.rows[0][1]).toBe('Desparasitación interna')
    expect(tables.find((t) => t.title === 'Estudios')?.columns[4].title).toBe('Veterinario')
  })

  it('perfil vacío no tiene nada que exportar', async () => {
    expect(dataIsEmpty(await loadExportData(dbFor(1), ALL_OPTIONS, 19700))).toBe(true)
  })

  it('textos de celda', () => {
    const d = ymdToEpochDay(2026, 11, 23)
    expect(display({ d })).toBe('23 nov 2026')
    expect(plain({ d })).toBe('2026-11-23')
    expect(plain(72.5)).toBe('72.5')
    expect(plain(true)).toBe('Sí')
    expect(plain(null)).toBe('')
    expect(daysMaskLabel(127)).toBe('Todos los días')
    expect(daysMaskLabel(0b0010101)).toBe('lun, mié, vie')
  })
})

describe('Excel (.xlsx)', () => {
  it('es un zip OOXML válido con una hoja por tabla, fechas reales y XML bien formado', async () => {
    await seedFamily()
    await dbFor(1).symptoms.add({ timestamp: 1_700_200_000_000, name: 'Raro <&> "x" \u0001\u0007 fin', intensity: 3, regionId: null, regionLabel: null, durationMin: null, tags: '', notes: 'emoji 😀 y tab\taquí' })
    const data = await loadExportData(dbFor(1), ALL_OPTIONS, 19700)
    const tables = buildTables(data)
    const entries = unzipSync(writeXlsx(tables, 'Savia — Alex'))
    const names = Object.keys(entries)
    for (const part of ['[Content_Types].xml', '_rels/.rels', 'docProps/core.xml', 'docProps/app.xml', 'xl/workbook.xml', 'xl/_rels/workbook.xml.rels', 'xl/styles.xml']) expect(names).toContain(part)
    expect(names.filter((n) => n.startsWith('xl/worksheets/')).length).toBe(tables.length)
    for (const n of names) assertWellFormed(strFromU8(entries[n]))

    const workbook = strFromU8(entries['xl/workbook.xml'])
    expect(workbook).toContain('<sheet name="Ciclo - días" sheetId="1" r:id="rId1"/>')
    expect(workbook).toContain('name="Perfil"')
    const types = strFromU8(entries['[Content_Types].xml'])
    expect((types.match(/worksheet\+xml/g) ?? []).length).toBe(tables.length)

    const sheet1 = strFromU8(entries['xl/worksheets/sheet1.xml'])
    // Encabezado congelado, con estilo, y filtro
    expect(sheet1).toContain('<pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>')
    expect(sheet1).toContain('<c r="A1" s="1" t="inlineStr"><is><t xml:space="preserve">Fecha</t></is></c>')
    expect(sheet1).toContain('<autoFilter ref="A1:M18"/>')
    // Fecha real de Excel: epochDay 19560 + 25569
    expect(sheet1).toContain(`<c r="A2" s="2"><v>${19560 + 25569}</v></c>`)
    // Número como número
    expect(sheet1).toContain('<c r="B2" s="0"><v>1</v></c>')

    const sintomas = strFromU8(entries[`xl/worksheets/sheet${tables.findIndex((t) => t.title === 'Síntomas') + 1}.xml`])
    expect(sintomas).toContain('Raro &lt;&amp;&gt; &quot;x&quot;  fin')
    expect(sintomas).toContain('emoji 😀 y tab\taquí')
    // Fecha y hora: número de serie con fracción y estilo de fecha-hora
    expect(/<c r="A2" s="3"><v>\d+\.\d+<\/v><\/c>/.test(sintomas)).toBe(true)
    // Decimal como número
    const med = strFromU8(entries[`xl/worksheets/sheet${tables.findIndex((t) => t.title === 'Peso y mediciones') + 1}.xml`])
    expect(med).toContain('<v>64.5</v>')
  })

  it('libro sin datos y utilidades', () => {
    const entries = unzipSync(writeXlsx([]))
    expect(strFromU8(entries['xl/workbook.xml'])).toContain('name="Sin datos"')
    assertWellFormed(strFromU8(entries['xl/worksheets/sheet1.xml']))
    expect([colName(0), colName(25), colName(26), colName(27), colName(701), colName(702)]).toEqual(['A', 'Z', 'AA', 'AB', 'ZZ', 'AAA'])
    expect(uniqueSheetNames(['Datos', 'datos', 'Con/diagonal:y*raros?', '', 'x'.repeat(40)])).toEqual(['Datos', 'datos (2)', 'Con diagonal y raros', 'Hoja', 'x'.repeat(31)])
    expect(esc('a\r\nb\rc')).toBe('a\nb\nc')
  })
})

describe('CSV y JSON legible', () => {
  it('escapa según RFC 4180', () => {
    expect(csvField('simple')).toBe('simple')
    expect(csvField('con, coma')).toBe('"con, coma"')
    expect(csvField('con "comillas"')).toBe('"con ""comillas"""')
    expect(csvField('dos\nlíneas')).toBe('"dos\nlíneas"')
    expect(csvField(' espacio')).toBe('" espacio"')
    expect(csvField('')).toBe('')
  })

  it('una tabla → .csv con BOM; varias → .zip', async () => {
    await seedFamily()
    const one = buildExportFile('CSV', await loadExportData(dbFor(1), { modules: ['SINTOMAS'], fromDay: null, toDay: null }, 19700))
    expect(one.name).toBe('savia-sintomas-2026-11-23.csv'.replace('2026-11-23', one.name.slice(-14, -4)))
    expect([...one.bytes.slice(0, 3)]).toEqual([0xef, 0xbb, 0xbf])
    const text = strFromU8(one.bytes.slice(3))
    const lines = text.split('\r\n')
    expect(lines[0]).toBe('Fecha y hora,Síntoma,Intensidad (1-10),Nivel,Zona del cuerpo,Duración (min),Posibles desencadenantes,Notas')
    expect(text).toContain('"Estrés, Pantallas","Con\nsalto, coma y ""comillas"""')
    expect(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2},Dolor de cabeza,6,Moderado,Cabeza,90,/.test(lines[1])).toBe(true)

    const many = buildExportFile('CSV', await loadExportData(dbFor(1), CYCLE_ONLY, 19700))
    expect(many.mime).toBe('application/zip')
    expect(Object.keys(unzipSync(many.bytes)).sort()).toEqual(['LEEME.txt', 'ciclo_dias.csv', 'ciclo_estadisticas.csv', 'ciclo_resumen.csv'])
    const t: ExportTable = { id: 'x', title: 'X', module: 'PERFIL', columns: [{ title: 'A', width: 5, wrap: false }], rows: [['1']] }
    expect(Object.keys(unzipSync(writeCsvZip([t, t]))).sort()).toEqual(['LEEME.txt', 'x.csv', 'x_2.csv'])
    expect(csvText(t)).toBe('A\r\n1\r\n')
  })

  it('JSON legible y nombres de archivo', async () => {
    const { dog } = await seedFamily()
    const data = await loadExportData(dbFor(dog), ALL_OPTIONS, 19700)
    const json = buildJsonExport(data) as { savia: Record<string, unknown>; tablas: Record<string, Record<string, unknown>[]> }
    expect(json.savia.perfil).toBe('Firulais')
    expect(json.savia.tipo_perfil).toBe('Mascota (perro)')
    expect(json.tablas['Vacunas'][0]['Próxima']).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    const d = ymdToEpochDay(2026, 11, 23)
    expect(exportFileName('XLSX', ALL_OPTIONS, undefined, d)).toBe('savia-datos-2026-11-23.xlsx')
    expect(exportFileName('XLSX', { modules: ['VACUNAS'], fromDay: null, toDay: null }, 'Firulais', d)).toBe('savia-firulais-vacunas-2026-11-23.xlsx')
    expect(exportFileName('CSV', CYCLE_ONLY, undefined, d)).toBe('savia-ciclo-csv-2026-11-23.zip')
    expect(exportFileName('CSV', { modules: ['CITAS'], fromDay: null, toDay: null }, undefined, d)).toBe('savia-citas-2026-11-23.csv')
    expect(exportFileName('JSON', ALL_OPTIONS, undefined, d)).toBe('savia-datos-exportacion-2026-11-23.json')
  })
})
