// Prueba de punta a punta del módulo de ciclo en un iPhone emulado.
// Uso (desde web/):  npm run build && node scripts/e2e-cycle.mjs
import { withApp } from './pw.mjs'

let failed = false
const fail = (m) => { console.error('FALLA:', m); failed = true }
const check = (cond, m) => { if (!cond) fail(m) }

/** Hoy según el navegador (la app usa la hora local del dispositivo). */
const browserToday = (page) => page.evaluate(() => { const d = new Date(); return Math.round(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()) / 86400000) })

const idbGet = (page, dbName, store, key) => page.evaluate(async ({ dbName, store, key }) => {
  const db = await new Promise((res, rej) => { const r = indexedDB.open(dbName); r.onsuccess = () => res(r.result); r.onerror = () => rej(r.error) })
  const v = await new Promise((res) => { const q = db.transaction(store).objectStore(store).get(key); q.onsuccess = () => res(q.result ?? null); q.onerror = () => res(null) })
  db.close()
  return v
}, { dbName, store, key })

const report = (name, errors) => { if (errors.length) fail(`${name}: errores de consola: ${errors.slice(0, 4).join(' | ')}`) }

// ── Persona con historial (claro) ────────────────────────────────────────────────────────────────
report('persona', await withApp({ seed: 'person' }, async ({ page, go, shot, auditInputs }) => {
  const t = await browserToday(page)
  await go('/ciclo')
  await page.waitForSelector('.cy-headline')
  check(await page.locator('.tabbar a.active', { hasText: 'Ciclo' }).count() === 1, 'la pestaña Ciclo no está activa')
  await shot('cycle_main')
  await shot('cycle_main_full', true)
  check(await page.locator('[data-testid=cy-test-mark]').count() >= 1, 'no se ve el rombo de la prueba de embarazo sembrada')
  check(await page.locator('.cy-cell').count() === 42, 'el calendario no tiene 42 celdas')
  const cell = await page.locator('.cy-cell').first().boundingBox()
  check(cell && cell.width >= 44 && cell.height >= 44, `celda del calendario demasiado pequeña: ${JSON.stringify(cell)}`)

  // Cambio de mes
  const month0 = await page.locator('[data-testid=cy-month]').innerText()
  await page.getByRole('button', { name: 'Mes siguiente' }).click()
  const month1 = await page.locator('[data-testid=cy-month]').innerText()
  check(month0 !== month1, 'el mes no cambió')
  check(await page.locator('.cy-disc.predicted').count() >= 1, 'el mes siguiente no muestra periodo estimado')
  await shot('cycle_next_month')
  await page.getByRole('button', { name: 'Ir a hoy' }).click()
  check(await page.locator('[data-testid=cy-month]').innerText() === month0, '"Ir a hoy" no regresó al mes actual')

  // "Empezó mi periodo"
  await page.getByRole('button', { name: 'Empezó mi periodo' }).click()
  await page.waitForFunction(() => document.querySelector('.cy-headline')?.textContent === 'Día 1 de tu periodo')
  const today1 = await idbGet(page, 'savia-p1', 'cycleDays', t)
  check(today1?.flow === 3, `tras "Empezó mi periodo" el flujo de hoy es ${today1?.flow}`)
  check(await page.locator(`.cy-cell[data-day="${t}"] .cy-disc.flow3`).count() === 1, 'hoy no quedó marcado como periodo en el calendario')
  await shot('cycle_in_period')

  // Registro del día: prueba de embarazo
  await page.getByRole('button', { name: /Registrar hoy/ }).click()
  await page.waitForSelector('[data-testid=cy-preg-test]')
  check((await auditInputs()).length === 0, 'campos con letra menor a 16px en el editor del día: ' + JSON.stringify(await auditInputs()))
  check(await page.locator('.chip.on', { hasText: 'Moderado' }).count() === 1, 'el editor no cargó el flujo de hoy')
  await page.locator('[data-testid=cy-preg-test] .chip', { hasText: 'Positiva' }).click()
  await page.waitForSelector('text=Una prueba casera positiva')
  await page.locator('.chip', { hasText: 'Cólicos' }).click()
  await page.locator('input[inputmode=decimal]').fill('36,6')
  await shot('cycle_day_full', true)
  await page.locator('.page-body').evaluate((el) => { el.scrollTop = el.scrollHeight })
  await shot('cycle_day_tests')
  await page.locator('.page-body .btn.primary', { hasText: 'Guardar' }).click()
  await page.waitForSelector('.cy-headline')
  const today2 = await idbGet(page, 'savia-p1', 'cycleDays', t)
  check(today2?.pregnancyTest === 2, `la prueba de embarazo no se guardó (${today2?.pregnancyTest})`)
  check(today2?.basalTemp === 36.6 && String(today2?.symptoms).includes('colicos') && today2?.flow === 3, 'el registro del día no conservó temperatura, síntomas o flujo: ' + JSON.stringify(today2))
  check(await page.locator(`.cy-cell[data-day="${t}"] [data-testid=cy-test-mark]`).count() === 1, 'hoy no muestra el rombo de prueba de embarazo')
  check(await page.locator('.cy-lines', { hasText: 'Prueba de embarazo: positiva' }).count() === 1, 'la tarjeta "Hoy registraste" no lista la prueba')
  await shot('cycle_after_test')

  // Temperatura fuera de rango: no deja guardar
  await go(`/ciclo/dia/${t}`)
  await page.waitForSelector('[data-testid=cy-preg-test]')
  await page.locator('input[inputmode=decimal]').fill('50')
  check(await page.locator('.hint.error', { hasText: 'entre 35 y 39' }).count() === 1, 'no se muestra el error de temperatura')
  check(await page.locator('.page-body .btn.primary', { hasText: 'Guardar' }).isDisabled(), 'Guardar debería estar deshabilitado con temperatura inválida')

  // Estadísticas
  await go('/ciclo/estadisticas')
  await page.waitForSelector('text=Historial de ciclos')
  check(await page.locator('[data-testid=cy-test-row]').count() === 2, 'el historial de pruebas de embarazo no tiene 2 filas')
  check(await page.locator('text=Ciclo promedio').count() === 1, 'faltan las estadísticas de resumen')
  await shot('cycle_stats_full', true)

  // Ajustes
  await go('/ciclo/ajustes')
  await page.waitForSelector('text=Tu ciclo típico')
  await shot('cycle_settings_full', true)
  await page.getByRole('button', { name: 'Más: Duración del ciclo' }).click()
  await page.waitForTimeout(250)
  check((await idbGet(page, 'savia-p1', 'prefs', 'cycleLength'))?.value === 29, 'el ajuste de duración del ciclo no se guardó')
  check(await page.locator('text=Recordatorio').count() === 0, 'los ajustes no deben ofrecer recordatorios en la web')

  // Modo "Editar periodo"
  await go('/ciclo')
  await page.waitForSelector('.cy-headline')
  await page.getByRole('button', { name: 'Editar periodo' }).click()
  await page.locator(`.cy-cell[data-day="${t - 2}"]`).click()
  await page.waitForTimeout(250)
  check((await idbGet(page, 'savia-p1', 'cycleDays', t - 2))?.flow === 3, 'el modo edición no marcó el día')
  await shot('cycle_edit_mode_full', true)
  await page.locator(`.cy-cell[data-day="${t - 2}"]`).click()
  await page.waitForTimeout(250)
  const cleared = await idbGet(page, 'savia-p1', 'cycleDays', t - 2)
  check(!cleared || cleared.flow === 0, 'el modo edición no desmarcó el día')
  await page.getByRole('button', { name: 'Listo' }).click()

  // Borrar el registro de hoy
  await go(`/ciclo/dia/${t}`)
  await page.waitForSelector('[data-testid=cy-preg-test]')
  await page.locator('.btn', { hasText: 'Borrar registro del día' }).click()
  await page.locator('.dialog .btn', { hasText: 'Borrar' }).click()
  // (se llegó aquí cambiando el hash a mano, así que "regresar" lleva a Inicio; se vuelve al ciclo explícitamente)
  await page.waitForTimeout(350)
  check((await idbGet(page, 'savia-p1', 'cycleDays', t)) === null, 'el registro de hoy no se borró')
  await go('/ciclo')
  await page.waitForSelector('.cy-headline')
  check(await page.locator(`.cy-cell[data-day="${t}"] [data-testid=cy-test-mark]`).count() === 0, 'el rombo sigue visible tras borrar el registro')
}))

// ── Persona (oscuro) ─────────────────────────────────────────────────────────────────────────────
report('oscuro', await withApp({ seed: 'person', dark: true }, async ({ page, go, shot }) => {
  // La semilla usa el "hoy" de Node (puede diferir un día del navegador por la zona horaria): la prueba sembrada está en hoyNode − 3.
  const n = new Date()
  const seededTestDay = Math.round(Date.UTC(n.getFullYear(), n.getMonth(), n.getDate()) / 86400000) - 3
  await go('/ciclo')
  await page.waitForSelector('.cy-headline')
  await shot('cycle_main_dark_full', true)
  await go(`/ciclo/dia/${seededTestDay}`)
  await page.waitForSelector('[data-testid=cy-preg-test]')
  check(await page.locator('[data-testid=cy-preg-test] .chip.on', { hasText: 'Negativa' }).count() === 1, 'el editor no cargó la prueba negativa sembrada')
  await shot('cycle_day_dark_full', true)
  await go('/ciclo/estadisticas')
  await page.waitForSelector('text=Historial de ciclos')
  await shot('cycle_stats_dark_full', true)
}))

// ── Sin datos ────────────────────────────────────────────────────────────────────────────────────
report('vacío', await withApp({ seed: 'empty' }, async ({ page, go, shot, auditInputs }) => {
  const t = await browserToday(page)
  await go('/ciclo/estadisticas')
  await page.waitForSelector('text=Aún no hay ciclos registrados')
  await shot('cycle_stats_empty')
  await go('/ciclo')
  await page.waitForSelector('text=Empieza a seguir tu ciclo')
  await shot('cycle_empty_full', true)
  await page.getByRole('button', { name: '¿Cuándo empezó tu último periodo?' }).click()
  await page.waitForSelector('.dialog input[type=date]')
  check((await auditInputs()).length === 0, 'campos con letra menor a 16px en el diálogo: ' + JSON.stringify(await auditInputs()))
  await page.waitForTimeout(350) // deja terminar la animación del diálogo
  await shot('cycle_pick_start')
  await page.locator('.dialog .btn', { hasText: 'Marcar periodo' }).click()
  await page.waitForFunction(() => document.querySelector('.cy-headline')?.textContent === 'Día 1 de tu periodo')
  const d0 = await idbGet(page, 'savia-p1', 'cycleDays', t)
  check(d0?.flow === 3, 'marcar el inicio no registró el día de hoy')
  check((await idbGet(page, 'savia-p1', 'cycleDays', t + 1)) === null, 'se marcaron días futuros')
  await shot('cycle_first_period_full', true)
}))

// ── Mascota ──────────────────────────────────────────────────────────────────────────────────────
report('mascota', await withApp({ seed: 'pet' }, async ({ page, go, shot }) => {
  for (const route of ['/ciclo', '/ciclo/estadisticas', '/ciclo/ajustes', '/ciclo/dia/20000']) {
    await go(route)
    await page.waitForSelector('text=Esta sección es para perfiles de personas')
  }
  await shot('cycle_pet')
}))

console.log(failed ? 'CON FALLAS' : 'OK')
process.exitCode = failed ? 1 : 0
