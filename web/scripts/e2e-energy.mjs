// Gasto de energía: cálculo automático, nivel de actividad, calculadora de ejercicio y casos sin datos.
import { withApp, writeIdb } from './pw.mjs'
const fails = []
const check = (ok, m) => { if (!ok) { fails.push(m); console.error('FALLA:', m) } else console.log('ok  ', m) }
const num = (s) => Number(String(s).replace(/[^0-9]/g, ''))

let errors = await withApp({ seed: 'person' }, async ({ page, go, shot, auditInputs }) => {
  await page.waitForSelector('.tabbar')
  await go('/energia')
  await page.getByText('Tu gasto diario aproximado').waitFor()
  // Alex: mujer, 31 años, 168 cm, 66.3 kg → basal 10·66.3 + 6.25·168 − 5·31 − 161 = 1397
  const total = num(await page.getByTestId('en-total').innerText())
  check(await page.getByText('sugerido por tu ejercicio registrado').count() === 1, 'sin nivel elegido usa el sugerido por el ejercicio de Bienestar')
  check(total >= 1650 && total <= 2450, `total razonable para el nivel sugerido (${total} kcal)`)
  await shot('energy_suggested', true)
  check((await auditInputs()).length === 0, 'campos de 16px o más')
  // Elegir "Moderado": 1397 × 1.55 ≈ 2170
  await page.locator('.en-level', { hasText: 'Moderado' }).getByRole('button', { name: 'Elegir' }).click()
  await page.locator('.en-level.on', { hasText: 'Moderado' }).waitFor()
  check(num(await page.getByTestId('en-total').innerText()) === 2170, 'moderado: 1,397 × 1.55 ≈ 2,170 kcal')
  check(await page.getByText('Solo por existir (metabolismo basal)').count() === 1 && await page.getByText('Lo que dice tu peso').count() === 1, 'muestra las tres partes y la lectura del peso')
  // Calculadora: correr a 8 km/h (8 METs) 30 min con 66.3 kg = 265.2 → 270
  await page.locator('select.input').selectOption({ label: 'Correr a 8 km/h · 8 METs' })
  check(num(await page.getByTestId('en-exercise').innerText()) === 270, 'correr 30 min ≈ 270 kcal')
  await page.locator('input[inputmode=numeric]').fill('60')
  check(num(await page.getByTestId('en-exercise').innerText()) === 530, 'correr 60 min ≈ 530 kcal')
  const before = await page.evaluate(async () => { const d = new Date(); const t = Math.round(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()) / 86400000); const db = await new Promise((r) => { const q = indexedDB.open('savia-p1'); q.onsuccess = () => r(q.result) }); return new Promise((r) => { const g = db.transaction('dailyLogs').objectStore('dailyLogs').get(t); g.onsuccess = () => r(g.result?.exerciseMin ?? 0) }) })
  await page.getByRole('button', { name: 'Sumar a mi ejercicio de hoy' }).click()
  await page.getByText('Sumados 60 min').waitFor()
  const after = await page.evaluate(async () => { const d = new Date(); const t = Math.round(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()) / 86400000); const db = await new Promise((r) => { const q = indexedDB.open('savia-p1'); q.onsuccess = () => r(q.result) }); return new Promise((r) => { const g = db.transaction('dailyLogs').objectStore('dailyLogs').get(t); g.onsuccess = () => r(g.result?.exerciseMin ?? 0) }) })
  check(after === before + 60, `los minutos se suman al bienestar de hoy (${before} → ${after})`)
  await shot('energy_chosen', true)
  // Inicio muestra la tarjeta y lleva a la pantalla
  await go('/')
  await page.getByText('Gasto diario aproximado').first().waitFor()
  check(await page.getByText('2,170 kcal').count() >= 1, 'Inicio muestra el gasto diario')
  await go('/mas')
  check(await page.getByText('Gasto de energía').count() === 1, 'está en el menú Más')
})
if (errors.length) check(false, 'errores de consola (persona): ' + errors.slice(0, 3).join(' | '))

errors = await withApp({ seed: 'kid', dark: true }, async ({ page, go, shot }) => {
  await page.waitForSelector('.tabbar'); await go('/energia')
  check(await page.getByText('está pensada para personas adultas').count() === 1, 'menor de edad: no se calcula y se explica por qué')
  await shot('energy_kid_dark')
})
if (errors.length) check(false, 'errores de consola (menor): ' + errors.slice(0, 3).join(' | '))

errors = await withApp({ seed: 'pet' }, async ({ page, go }) => {
  await page.waitForSelector('.tabbar'); await go('/energia')
  check(await page.getByText('Solo para personas').count() === 1, 'mascota: no aplica')
  await go('/mas')
  check(await page.getByText('Gasto de energía').count() === 0, 'mascota: no aparece en el menú')
})
if (errors.length) check(false, 'errores de consola (mascota): ' + errors.slice(0, 3).join(' | '))

errors = await withApp({ seed: 'empty' }, async ({ page, go, shot }) => {
  await page.waitForSelector('.tabbar'); await go('/energia')
  check(await page.getByText('Faltan datos para calcular').count() === 1 && await page.getByRole('button', { name: 'Registrar peso' }).count() === 1, 'sin datos: pide peso y ficha')
  await shot('energy_missing')
  // sin sexo indicado: punto medio
  const t = await page.evaluate(() => { const d = new Date(); return Math.round(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()) / 86400000) })
  await writeIdb(page, 'savia-p1', { profile: [{ id: 1, name: 'Sam', sex: 'X', birthEpochDay: t - 365 * 40 - 20, heightCm: 170, goalWeightKg: null, bloodType: '', allergies: '', conditions: '', emergencyName: '', emergencyPhone: '', doctorName: '', doctorPhone: '', insurance: '', notes: '', kind: 'person', species: '', breed: '', sterilized: null, microchip: '', markings: '', relation: '', avatarColor: 0 }], measurements: [{ id: 1, timestamp: Date.now() - 3600000, type: 'weight', value: 70, value2: null, tag: '', notes: '' }] })
  await page.reload(); await page.waitForSelector('.page')
  await page.getByTestId('en-total').waitFor()
  // 700 + 1062.5 − 200 − 78 = 1484.5 → 1,480 (sin nivel: solo basal)
  check(num(await page.getByTestId('en-total').innerText()) === 1480 && await page.getByText('punto medio').count() === 1, 'sin sexo indicado usa el punto medio y, sin nivel, muestra el basal')
})
if (errors.length) check(false, 'errores de consola (sin datos): ' + errors.slice(0, 3).join(' | '))
console.log(fails.length ? `CON FALLAS (${fails.length})` : 'OK')
process.exitCode = fails.length ? 1 : 0
