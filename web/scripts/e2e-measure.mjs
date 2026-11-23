// Pruebas en navegador del módulo de mediciones (iPhone emulado). Requiere `npm run build` antes.
// Uso: node scripts/e2e-measure.mjs      → capturas en web/shots/measure_*.png
import { withApp } from './pw.mjs'

// El navegador emulado usa la zona horaria de la Ciudad de México; los datos de ejemplo se calculan en Node.
// Se alinean ambas para que "hoy" sea el mismo día en los dos lados (si no, lo sembrado "hoy" queda en el futuro).
process.env.TZ = 'America/Mexico_City'

let failures = 0
const check = (cond, msg) => { if (!cond) { failures++; console.error('FALLA:', msg) } }
const bodyText = (page) => page.evaluate(() => document.querySelector('.app').innerText)
const reportErrors = (label, errors) => { if (errors.length) { failures++; console.error(`Errores de consola (${label}):`, errors.slice(0, 5)) } }
/** Lee la ficha del perfil directamente de IndexedDB. */
const profileRow = (page, dbName) => page.evaluate(async (name) => {
  const db = await new Promise((res, rej) => { const r = indexedDB.open(name); r.onsuccess = () => res(r.result); r.onerror = () => rej(r.error) })
  const row = await new Promise((res) => { const q = db.transaction('profile').objectStore('profile').get(1); q.onsuccess = () => res(q.result) })
  db.close()
  return row
}, dbName)
const sheetButton = (page, name) => page.locator('.sheet').getByRole('button', { name, exact: true })

// ── 1. Persona adulta (claro): hub, detalles y captura por la interfaz ──────────────────────────
reportErrors('persona', await withApp({ seed: 'person' }, async ({ page, go, shot, auditInputs }) => {
  await go('/mediciones')
  await page.waitForSelector('text=Otras mediciones')
  let text = await bodyText(page)
  for (const t of ['Peso', 'Estatura', 'Presión arterial', 'Frecuencia cardiaca', 'Glucosa', 'Temperatura', 'Oxigenación', 'Cintura', 'Cadera', 'Grasa corporal', 'IMC', 'Meta: 64 kg'])
    check(text.includes(t), `hub persona: falta "${t}"`)
  check(text.includes('Peso saludable'), 'hub persona: categoría de IMC')
  await shot('measure_hub', true)

  await go('/mediciones/weight')
  await page.waitForSelector('text=Meta de peso')
  text = await bodyText(page)
  for (const t of ['Media móvil 7 días', 'Tendencia', 'Ritmo', 'Índice de masa corporal', 'Te faltan', 'Historial · 61 registros'])
    check(text.includes(t), `peso persona: falta "${t}"`)
  // tocar la gráfica muestra el valor más cercano
  const chart = page.locator('svg.chart').first()
  const box = await chart.boundingBox()
  await page.mouse.click(box.x + box.width * 0.6, box.y + box.height * 0.5)
  await shot('measure_weight', true)

  await go('/mediciones/bp')
  await page.waitForSelector('text=Última medición')
  text = await bodyText(page)
  for (const t of ['Sistólica (alta)', 'Diastólica (baja)', 'mmHg', 'Referencia orientativa para personas adultas'])
    check(text.includes(t), `presión: falta "${t}"`)
  await shot('measure_bp', true)

  await go('/mediciones/glucose')
  await page.waitForSelector('text=Rango de referencia')
  await shot('measure_glucose', true)

  await go('/mediciones/height')
  await page.waitForSelector('text=Sin registros de estatura')
  await shot('measure_height_empty')

  // Registrar un peso por la interfaz
  await go('/mediciones/weight?add=1')
  await page.waitForSelector('.sheet')
  check((await page.locator('.sheet-head h2').innerText()) === 'Registrar peso', 'título de la hoja')
  const small = await auditInputs()
  check(small.length === 0, 'campos con letra menor a 16px: ' + JSON.stringify(small))
  await shot('measure_form_new')
  await sheetButton(page, 'Guardar').click()
  await page.waitForSelector('text=Escribe un valor para peso.')
  await page.locator('.sheet input[inputmode="decimal"]').fill('65,4')
  await page.locator('.sheet textarea').fill('Después de desayunar')
  await sheetButton(page, 'Guardar').click()
  await page.waitForSelector('.sheet', { state: 'detached' })
  await page.waitForSelector('text=65.4 kg')
  check(!(await page.evaluate(() => location.hash)).includes('add=1'), '?add=1 debe quitarse de la dirección')
  text = await bodyText(page)
  check(text.includes('Historial · 62 registros') && text.includes('Después de desayunar'), 'el peso nuevo aparece en el historial')

  // Editar y eliminar
  await page.locator('.mz-history').first().click()
  await page.waitForSelector('.sheet')
  check((await page.locator('.sheet-head h2').innerText()) === 'Editar peso', 'título al editar')
  check((await page.locator('.sheet input[inputmode="decimal"]').inputValue()) === '65.4', 'valor precargado al editar')
  await page.locator('.sheet input[inputmode="decimal"]').fill('65.9')
  await sheetButton(page, 'Guardar').click()
  await page.waitForSelector('.sheet', { state: 'detached' })
  await page.waitForSelector('text=65.9 kg')
  await page.locator('.mz-history').first().click()
  await page.waitForSelector('.sheet')
  await sheetButton(page, 'Eliminar').click()
  await page.waitForSelector('.dialog')
  await page.locator('.dialog').getByRole('button', { name: 'Eliminar', exact: true }).click()
  await page.waitForSelector('.sheet', { state: 'detached' })
  await page.waitForSelector('text=Historial · 61 registros')
  check(!(await bodyText(page)).includes('65.9 kg'), 'el peso eliminado ya no aparece')

  // Meta de peso
  await page.locator('.card', { hasText: 'Meta de peso' }).getByRole('button', { name: 'Editar' }).click()
  await page.waitForSelector('.dialog')
  check((await auditInputs()).length === 0, 'diálogo de meta: letra menor a 16px')
  await page.locator('.dialog input').fill('63.5')
  await page.waitForTimeout(3500) // deja terminar la animación y que se vaya el aviso anterior
  await shot('measure_goal_dialog')
  await page.locator('.dialog').getByRole('button', { name: 'Guardar' }).click()
  await page.waitForSelector('text=Meta 63.5')
  check((await profileRow(page, 'savia-p1')).goalWeightKg === 63.5, 'la meta se guarda en la ficha')

  // Presión: dos valores, contexto y validación
  await go('/mediciones/bp?add=1')
  await page.waitForSelector('.sheet')
  const inputs = page.locator('.sheet input[inputmode="numeric"]')
  await inputs.nth(0).fill('80')
  await inputs.nth(1).fill('120')
  await sheetButton(page, 'Guardar').click()
  await page.waitForSelector('text=La diastólica debe ser menor que la sistólica.')
  await inputs.nth(0).fill('151')
  await inputs.nth(1).fill('96')
  await page.locator('.sheet .chip', { hasText: 'Por la noche' }).click()
  check((await auditInputs()).length === 0, 'formulario de presión: letra menor a 16px')
  await shot('measure_form_bp')
  await sheetButton(page, 'Guardar').click()
  await page.waitForSelector('.sheet', { state: 'detached' })
  await page.waitForSelector('text=151/96 mmHg')
  check((await bodyText(page)).includes('Alta (grado 2)'), 'la presión nueva se clasifica')

  // El hub refleja lo último
  await go('/mediciones')
  await page.waitForSelector('text=151/96 mmHg')
}))

// ── 2. Persona adulta (oscuro) ──────────────────────────────────────────────────────────────────
reportErrors('oscuro', await withApp({ seed: 'person', dark: true }, async ({ page, go, shot }) => {
  await go('/mediciones')
  await page.waitForSelector('text=Otras mediciones')
  await shot('measure_hub_dark', true)
  await go('/mediciones/weight')
  await page.waitForSelector('text=Meta de peso')
  await shot('measure_weight_dark', true)
  await go('/mediciones/bp')
  await page.waitForSelector('text=Última medición')
  await shot('measure_bp_dark')
}))

// ── 3. Mascota (perro) ──────────────────────────────────────────────────────────────────────────
reportErrors('mascota', await withApp({ seed: 'pet' }, async ({ page, go, shot, auditInputs }) => {
  await go('/mediciones')
  await page.waitForSelector('text=Otras mediciones')
  let text = await bodyText(page)
  check(text.includes('Frecuencia cardiaca') && text.includes('Temperatura'), 'hub mascota: tipos que aplican')
  for (const t of ['Presión arterial', 'Glucosa', 'Estatura', 'IMC', 'Cintura']) check(!text.includes(t), `hub mascota: no debe mostrar "${t}"`)
  check(text.includes('Meta: 17 kg') && text.includes('veterinari'), 'hub mascota: meta y nota de veterinario')
  await shot('measure_pet_hub', true)

  await go('/mediciones/weight')
  await page.waitForSelector('text=Meta de peso')
  text = await bodyText(page)
  check(text.includes('Le faltan') && text.includes('Registros') && !text.includes('Índice de masa corporal'), 'peso mascota: sin IMC y en tercera persona')
  await shot('measure_pet_weight', true)

  await go('/mediciones/temp')
  await page.waitForSelector('text=Referencia')
  text = await bodyText(page)
  check(text.includes('38.6 °C') && text.includes('En perros la temperatura normal') && !text.includes('Fiebre'), 'temperatura de perro: nota propia, sin clasificación humana')
  await shot('measure_pet_temp', true)

  // Peso en gramos (cachorro / mascota pequeña): se captura en kg con decimales
  await go('/mediciones/weight?add=1')
  await page.waitForSelector('.sheet')
  check((await bodyText(page)).includes('0.35 kg son 350 g'), 'ayuda de gramos en el formulario de mascota')
  check((await auditInputs()).length === 0, 'formulario mascota: letra menor a 16px')
  await page.locator('.sheet input[inputmode="decimal"]').fill('17.264')
  await sheetButton(page, 'Guardar').click()
  await page.waitForSelector('.sheet', { state: 'detached' })
  await page.waitForSelector('text=17.26 kg')
}))

// ── 4. Menor de edad (7 años) ───────────────────────────────────────────────────────────────────
reportErrors('menor', await withApp({ seed: 'kid' }, async ({ page, go, shot }) => {
  await go('/mediciones')
  await page.waitForSelector('text=Otras mediciones')
  let text = await bodyText(page)
  check(text.includes('Estatura') && text.includes('122 cm'), 'hub menor: estatura')
  check(text.includes('pediatra') && !text.includes('Peso saludable') && !text.includes('Bajo peso'), 'hub menor: sin categoría de adultos')
  await shot('measure_kid_hub', true)

  await go('/mediciones/weight')
  await page.waitForSelector('text=Índice de masa corporal')
  text = await bodyText(page)
  check(text.includes('se interpreta por edad') && text.includes('tablas por edad') && !text.includes('Peso saludable'), 'peso menor: IMC sin categoría')
  await shot('measure_kid_weight', true)

  await go('/mediciones/height')
  await page.waitForSelector('text=Historial · 9 registros')
  text = await bodyText(page)
  check(text.includes('ayuda a seguir el crecimiento'), 'estatura menor: nota de crecimiento')
  await shot('measure_kid_height', true)

  // Una estatura nueva actualiza la ficha
  await page.locator('.fab').click()
  await page.waitForSelector('.sheet')
  await page.locator('.sheet input[inputmode="decimal"]').fill('123.5')
  await sheetButton(page, 'Guardar').click()
  await page.waitForSelector('.sheet', { state: 'detached' })
  await page.waitForSelector('text=Historial · 10 registros')
  await page.waitForFunction(async () => {
    const db = await new Promise((res) => { const r = indexedDB.open('savia-p2'); r.onsuccess = () => res(r.result) })
    const row = await new Promise((res) => { const q = db.transaction('profile').objectStore('profile').get(1); q.onsuccess = () => res(q.result) })
    db.close()
    return row?.heightCm === 123.5
  }, null, { timeout: 5000 }).catch(() => check(false, 'la estatura nueva no actualizó la ficha'))
}))

// ── 5. Sin datos ────────────────────────────────────────────────────────────────────────────────
reportErrors('vacío', await withApp({ seed: 'empty' }, async ({ page, go, shot }) => {
  await go('/mediciones')
  await page.waitForSelector('text=Otras mediciones')
  const text = await bodyText(page)
  check(text.includes('Aún no registras tu peso') && text.includes('Sin registros'), 'hub vacío')
  await shot('measure_hub_empty', true)
  await go('/mediciones/weight')
  await page.waitForSelector('text=Sin registros de peso')
  await shot('measure_weight_empty')
  // desde el estado vacío se abre el formulario
  await page.getByRole('button', { name: 'Registrar peso' }).click()
  await page.waitForSelector('.sheet')
  await page.locator('.sheet input[inputmode="decimal"]').fill('70')
  await sheetButton(page, 'Guardar').click()
  await page.waitForSelector('text=Historial · 1 registro')
  await shot('measure_weight_single', true)
  // tipo desconocido → peso
  await go('/mediciones/no-existe')
  await page.waitForSelector('text=Historial · 1 registro')
}))

console.log(failures ? `CON FALLAS (${failures})` : 'OK')
process.exitCode = failures ? 1 : 0
