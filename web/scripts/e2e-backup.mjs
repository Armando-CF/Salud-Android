// Prueba de punta a punta del módulo de respaldo en un iPhone emulado:
//  exportar .json → borrar todos los datos del sitio → importar el mismo archivo → todo regresa;
//  agregar como perfiles nuevos, reemplazar todo y deshacer; exportar Excel / CSV / Android; reporte para imprimir.
// Uso (desde web/):  npm run build && node scripts/e2e-backup.mjs
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { withApp, writeIdb } from './pw.mjs'
import { demoData } from './demo-data.mjs'

const OUT = fileURLToPath(new URL('../shots/', import.meta.url))
fs.mkdirSync(OUT, { recursive: true })
let failed = false
const check = (cond, msg) => { if (!cond) { failed = true; console.error('FALLA:', msg) } else console.log('ok  ', msg) }

/** Resumen del contenido real de IndexedDB: perfiles y conteos por tabla. */
const dump = (page) => page.evaluate(async () => {
  const open = (name) => new Promise((res, rej) => { const r = indexedDB.open(name); r.onsuccess = () => res(r.result); r.onerror = () => rej(r.error) })
  const all = (db, store) => new Promise((res, rej) => { const r = db.transaction(store).objectStore(store).getAll(); r.onsuccess = () => res(r.result); r.onerror = () => rej(r.error) })
  const meta = await open('savia-meta')
  const ids = (await all(meta, 'profiles')).map((p) => p.id).sort((a, b) => a - b)
  const kv = Object.fromEntries((await all(meta, 'kv')).map((r) => [r.key, r.value]))
  meta.close()
  const profiles = []
  for (const id of ids) {
    const db = await open('savia-p' + id)
    const counts = {}
    for (const s of ['symptoms', 'measurements', 'dailyLogs', 'studies', 'studyResults', 'cycleDays', 'medications', 'medIntakes', 'appointments', 'selfExams', 'vaccinations', 'studyAttachments']) counts[s] = (await all(db, s)).length
    const p = (await all(db, 'profile'))[0]
    const prefs = Object.fromEntries((await all(db, 'prefs')).map((r) => [r.key, r.value]))
    const att = (await all(db, 'studyAttachments')).map((a) => [a.displayName, a.data ? a.data.byteLength : -1])
    db.close()
    const defaults = { cycleEnabled: false, cycleLength: 28, periodLength: 5, lutealLength: 14, showFertility: true, atlasSex: 'F', activityLevel: '' }
    profiles.push({ id, name: p?.name, kind: p?.kind, species: p?.species, counts, prefs: { ...defaults, ...prefs }, att })
  }
  return { profiles, activeProfile: kv.activeProfile ?? 1, lastBackup: kv.lastBackup ?? 0, hasSafety: !!kv['backup.safety'] }
})
const content = (d) => d.profiles.map((p) => ({ name: p.name, kind: p.kind, species: p.species, counts: p.counts, prefs: p.prefs, att: p.att }))

/** Los perfiles 2 y 3 se crean al abrir una pantalla que liste perfiles; luego se siembran sus datos. */
async function seedOthers(page, go, seed) {
  await go('/exportar')
  const data = demoData(seed)
  for (const [name, stores] of Object.entries(data)) {
    if (name === 'savia-meta' || name === 'savia-p1') continue
    await page.waitForFunction((n) => indexedDB.databases().then((d) => d.some((x) => x.name === n && x.version >= 10)), name, { timeout: 15000 })
    await writeIdb(page, name, stores)
  }
  // Un adjunto de verdad en el perfil principal (PDF mínimo) para probar que viaja en el respaldo.
  await page.evaluate(async () => {
    const db = await new Promise((res) => { const r = indexedDB.open('savia-p1'); r.onsuccess = () => res(r.result) })
    await new Promise((res) => { const tx = db.transaction('studyAttachments', 'readwrite'); tx.objectStore('studyAttachments').put({ id: 1, studyId: 1, fileName: 'resultados.pdf', displayName: 'resultados.pdf', mime: 'application/pdf', data: new Uint8Array([37, 80, 68, 70, 45, 49, 46, 52, 10, 1, 2, 3, 4, 5]).buffer }); tx.oncomplete = res })
    db.close()
  })
  await page.reload(); await page.waitForSelector('.app')
  await go('/exportar')
}

const saveDownload = async (page, name) => {
  const [download] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: 'Guardar archivo' }).click()])
  const file = path.join(OUT, name ?? download.suggestedFilename())
  await download.saveAs(file)
  return { file, suggested: download.suggestedFilename() }
}

const errorsLight = await withApp({ seed: 'family' }, async ({ page, context, go, shot, auditInputs }) => {
  try {
  await seedOthers(page, go, 'family')
  const before = await dump(page)
  check(before.profiles.length === 3 && before.profiles[1].counts.measurements === 18 && before.profiles[2].kind === 'pet', 'semilla: 3 perfiles con datos')

  await page.getByText('Exportando datos de:').waitFor()
  await shot('backup_export')
  await shot('backup_export_full', true)
  check((await auditInputs()).length === 0, 'campos de al menos 16px en Exportar y respaldo')

  // 1) Exportar respaldo .json
  await page.getByRole('button', { name: 'Exportar respaldo (.json)' }).click()
  await page.getByText('Tu respaldo está listo').waitFor()
  await page.waitForTimeout(400)
  await shot('backup_ready_dialog')
  const backup = await saveDownload(page, 'respaldo.json')
  check(/^savia-respaldo-\d{4}-\d{2}-\d{2}\.json$/.test(backup.suggested), `nombre sugerido: ${backup.suggested}`)
  const json = JSON.parse(fs.readFileSync(backup.file, 'utf8'))
  check(json.format === 'savia-web-backup' && json.formatVersion === 1 && json.profiles.length === 3, 'archivo .json con formato web y 3 perfiles')
  check(json.profiles[0].tables.study_attachments[0]?.dataBase64 === 'JVBERi0xLjQKAQIDBAU=', 'el adjunto viaja en base64')
  check(json.profiles[0].tables.cycle_days.length > 0 && json.profiles[2].tables.vaccinations.length === 4, 'tablas con los nombres de Android')
  await page.waitForTimeout(300)
  check((await dump(page)).lastBackup > 0, 'se registró la fecha del último respaldo')

  // 2) Borrar TODOS los datos del sitio y recargar: la app queda como recién instalada
  const cdp = await context.newCDPSession(page)
  await cdp.send('Storage.clearDataForOrigin', { origin: new URL(page.url()).origin, storageTypes: 'indexeddb' })
  await page.reload(); await page.waitForSelector('.app')
  // la bienvenida ofrece ir directo a restaurar un respaldo
  await page.getByRole('button', { name: 'Ya tengo un respaldo: restaurarlo' }).click()
  await page.waitForSelector('.tabbar, [data-testid="backup-file-input"]', { state: 'attached' })
  const wiped = await dump(page)
  check(wiped.profiles.length === 1 && Object.values(wiped.profiles[0].counts).every((n) => n === 0), 'datos borrados: un perfil vacío')

  // 3) Importar el mismo archivo
  await go('/exportar')
  await page.setInputFiles('[data-testid="backup-file-input"]', backup.file)
  await page.getByText('Importar respaldo', { exact: true }).waitFor()
  await page.getByText('Firulais').first().waitFor()
  await page.waitForTimeout(400)
  await shot('backup_import_fresh')
  await page.getByRole('button', { name: 'Restaurar todo en este dispositivo' }).click()
  await page.waitForFunction(() => indexedDB.databases().then((d) => d.filter((x) => /^savia-p\d+$/.test(x.name)).length === 3), null, { timeout: 15000 })
  await page.waitForTimeout(800)
  const after = await dump(page)
  check(JSON.stringify(content(after)) === JSON.stringify(content(before)), 'tras importar regresan los 3 perfiles con todos sus datos, ajustes y adjuntos')
  check(after.activeProfile === after.profiles[0].id, 'el perfil activo es el principal')
  await go('/exportar')
  await page.getByText('Respaldo restaurado: 3 perfiles').first().waitFor()
  await shot('backup_after_import')

  // 4) Con datos existentes: agregar como perfiles nuevos (no toca lo actual)
  await page.setInputFiles('[data-testid="backup-file-input"]', backup.file)
  await page.getByRole('button', { name: 'Agregar como perfiles nuevos' }).waitFor()
  await page.waitForTimeout(400)
  await shot('backup_import_options')
  await page.getByRole('button', { name: 'Agregar como perfiles nuevos' }).click()
  await page.waitForFunction(() => indexedDB.databases().then((d) => d.filter((x) => /^savia-p\d+$/.test(x.name)).length === 6), null, { timeout: 15000 })
  await page.waitForTimeout(600)
  const added = await dump(page)
  check(added.profiles.length === 6 && JSON.stringify(content(added).slice(0, 3)) === JSON.stringify(content(before)) && JSON.stringify(content(added).slice(3)) === JSON.stringify(content(before)), 'agregar como nuevos: 6 perfiles, los originales intactos')

  // 5) Reemplazar todo (con confirmación) y deshacer
  await page.setInputFiles('[data-testid="backup-file-input"]', backup.file)
  await page.getByRole('button', { name: 'Reemplazar todo' }).click()
  await page.getByText('¿Reemplazar todo?').waitFor()
  await page.waitForTimeout(400)
  await shot('backup_replace_confirm')
  await page.getByRole('button', { name: 'Sí, reemplazar' }).click()
  await page.waitForFunction(() => indexedDB.databases().then((d) => d.filter((x) => /^savia-p\d+$/.test(x.name)).length === 3), null, { timeout: 15000 })
  await page.waitForTimeout(800)
  const replaced = await dump(page)
  check(replaced.profiles.length === 3 && replaced.hasSafety, 'reemplazar todo: 3 perfiles y copia de seguridad interna')
  await go('/exportar')
  await page.getByRole('button', { name: 'Deshacer el último reemplazo' }).click()
  await page.getByRole('button', { name: 'Sí, deshacer' }).click()
  await page.waitForFunction(() => indexedDB.databases().then((d) => d.filter((x) => /^savia-p\d+$/.test(x.name)).length === 6), null, { timeout: 15000 })
  await page.waitForTimeout(800)
  const undone = await dump(page)
  check(undone.profiles.length === 6 && !undone.hasSafety && JSON.stringify(content(undone)) === JSON.stringify(content(added)), 'deshacer: regresan los 6 perfiles y se borra la copia')

  // 6) Archivo inválido: mensaje claro y nada cambia
  const bad = path.join(OUT, 'no-es-respaldo.json')
  fs.writeFileSync(bad, '{"hola": 1}')
  await go('/exportar')
  await page.setInputFiles('[data-testid="backup-file-input"]', bad)
  await page.getByText('Este archivo no es un respaldo de Savia.').waitFor()
  await page.waitForTimeout(400)
  await shot('backup_invalid_file')
  await page.getByRole('button', { name: 'Entendido' }).click()
  check((await dump(page)).profiles.length === 6, 'archivo inválido: nada cambia')

  // 7) Exportar para leer: Excel, CSV (solo ciclo) y Android
  await page.getByRole('button', { name: 'Generar archivo' }).click()
  await page.getByText('Tu archivo está listo').waitFor()
  const xlsx = await saveDownload(page, 'exportacion.xlsx')
  check(/^savia-alex-datos-\d{4}-\d{2}-\d{2}\.xlsx$/.test(xlsx.suggested), `Excel: ${xlsx.suggested}`)
  check(fs.readFileSync(xlsx.file).subarray(0, 2).toString() === 'PK', 'el .xlsx es un zip')
  await page.waitForTimeout(300)
  await page.getByRole('button', { name: 'Solo ciclo menstrual (todo el historial)' }).click()
  await page.getByRole('button', { name: 'CSV', exact: true }).click()
  await page.getByRole('button', { name: 'Generar archivo' }).click()
  await page.getByText('Tu archivo está listo').waitFor()
  const csv = await saveDownload(page, 'ciclo-csv.zip')
  check(/^savia-alex-ciclo-csv-\d{4}-\d{2}-\d{2}\.zip$/.test(csv.suggested), `CSV: ${csv.suggested}`)
  await page.waitForTimeout(300)
  await page.getByRole('button', { name: 'Exportar para Savia Android (.zip)' }).click()
  await page.getByText('Archivo para Savia Android listo').waitFor()
  const android = await saveDownload(page, 'android.zip')
  check(/^savia-android-alex-\d{4}-\d{2}-\d{2}\.zip$/.test(android.suggested), `Android: ${android.suggested}`)

  // 8) Importar el .zip de Android recién exportado (ida y vuelta)
  await page.waitForTimeout(300)
  await page.setInputFiles('[data-testid="backup-file-input"]', android.file)
  await page.getByText('Respaldo de Savia para Android', { exact: false }).waitFor()
  await page.waitForTimeout(400)
  await shot('backup_import_android')
  await page.getByRole('button', { name: 'Agregar como perfil nuevo' }).click()
  await page.waitForFunction(() => indexedDB.databases().then((d) => d.filter((x) => /^savia-p\d+$/.test(x.name)).length === 7), null, { timeout: 15000 })
  await page.waitForTimeout(600)
  const withAndroid = await dump(page)
  const a = content(withAndroid)[6], b = content(before)[0]
  check(JSON.stringify({ ...a, att: a.att.map((x) => x[1]) }) === JSON.stringify({ ...b, att: b.att.map((x) => x[1]) }), 'zip de Android: el perfil regresa completo con su adjunto')

  // 9) Reporte para imprimir / PDF
  await go('/exportar')
  await page.getByRole('button', { name: 'PDF / imprimir' }).click()
  await page.getByRole('button', { name: 'Ver reporte' }).click()
  await page.getByText('Savia — Reporte de salud').waitFor()
  await shot('backup_report')
  await shot('backup_report_full', true)
  await page.emulateMedia({ media: 'print' })
  await page.pdf({ path: path.join(OUT, 'reporte.pdf'), format: 'Letter', landscape: true, printBackground: true })
  await page.emulateMedia({ media: 'screen' })
  check(fs.statSync(path.join(OUT, 'reporte.pdf')).size > 20000, 'reporte.pdf generado con la hoja de estilos de impresión')
  } catch (e) { await shot('backup_FALLA'); throw e }
})

// Tema oscuro y perfil de mascota
const errorsDark = await withApp({ seed: 'family', dark: true }, async ({ page, go, shot }) => {
  await seedOthers(page, go, 'family')
  await page.getByText('Exportando datos de:').waitFor()
  await shot('backup_export_dark')
  await shot('backup_export_dark_full', true)
})
const errorsPet = await withApp({ seed: 'pet' }, async ({ page, go, shot, auditInputs }) => {
  await seedOthers(page, go, 'pet')
  await page.getByText('Firulais').first().waitFor()
  const text = await page.locator('.page-body').innerText()
  check(!text.includes('Ciclo menstrual') && !text.includes('Bienestar diario') && !text.includes('Autoexploración'), 'mascota: sin ciclo, bienestar ni autoexploración')
  check(text.includes('Vacunas y desparasitación') && text.includes('Citas con el veterinario'), 'mascota: etiquetas adaptadas')
  await page.getByRole('button', { name: 'Personalizado' }).click()
  check((await auditInputs()).length === 0, 'campos de fecha de al menos 16px')
  await shot('backup_export_pet_full', true)
})

const errors = [...errorsLight, ...errorsDark, ...errorsPet]
check(errors.length === 0, 'sin errores de consola' + (errors.length ? ': ' + errors.slice(0, 5).join(' | ') : ''))
console.log(failed ? 'CON FALLAS' : 'TODO OK')
process.exit(failed ? 1 : 0)
