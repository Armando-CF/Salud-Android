// Prueba en navegador del módulo de Estudios (iPhone emulado). Uso: npm run build && node scripts/e2e-studies.mjs
import { withApp } from './pw.mjs'

let failures = 0
const fail = (m) => { failures++; console.error('FALLA:', m) }
const check = (cond, m) => { if (!cond) fail(m) }
const report = (label, errors) => { if (errors.length) { fail(`${label}: errores de consola`); console.error(errors.slice(0, 5)) } }

// PNG de 2×2 px (rojo) para probar adjuntos.
const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAEElEQVR4nGP8z8DAwMDAxAADAA0GAQPdk1DPAAAAAElFTkSuQmCC', 'base64')

const field = (scope, label) => scope.locator(`.field:has(> label:text-is("${label}"))`).locator('input, textarea').first()

// ── Persona (claro): lista, parámetros, detalle, editor, alta por la interfaz con adjunto, evolución ──
report('persona', await withApp({ seed: 'person' }, async ({ page, go, shot, auditInputs }) => {
  await go('/estudios')
  await page.waitForSelector('text=Química sanguínea y biometría')
  check(await page.locator('text=Seguimientos próximos').count() === 1, 'falta la tarjeta de seguimientos')
  check(await page.locator('text=1 fuera de rango').count() >= 1, 'la lista no marca resultados fuera de rango')
  await shot('studies_list', true)

  await page.getByRole('button', { name: 'Parámetros' }).click()
  await page.waitForSelector('text=Colesterol total')
  await shot('studies_params', true)
  await page.getByRole('button', { name: 'Estudios', exact: true }).click()

  // Detalle del estudio más reciente
  await page.locator('.card.tap', { hasText: 'Química sanguínea y biometría' }).first().click()
  await page.waitForSelector('text=Resultados')
  check(await page.locator('[data-result="Colesterol total"] >> text=Alto ↑').count() === 1, 'el detalle no marca Colesterol total como alto')
  check(await page.locator('[data-result="Glucosa"] >> text=En rango').count() === 1, 'el detalle no marca Glucosa en rango')
  check(await page.locator('[data-result="Glucosa"] .st-range').count() === 1, 'falta la barra de rango')
  await shot('studies_detail', true)

  // Editor de un estudio existente
  await page.getByRole('button', { name: 'Editar' }).click()
  await page.waitForSelector('.st-row')
  check(await page.locator('.st-row').count() === 5, 'el editor no cargó los 5 resultados')
  check(await field(page, 'Nombre del estudio').inputValue() === 'Química sanguínea y biometría', 'el editor no cargó el título')
  const small = await auditInputs()
  check(small.length === 0, 'campos con letra menor a 16px: ' + JSON.stringify(small))
  await shot('studies_edit_existing', true)
  await page.getByRole('button', { name: 'Regresar' }).click()   // sin cambios: sale sin preguntar
  await page.waitForSelector('text=Resultados')
  await page.getByRole('button', { name: 'Regresar' }).click()
  await page.waitForSelector('.fab')

  // Alta por la interfaz: título sugerido que precarga un grupo, dos resultados y una imagen adjunta
  await page.locator('.fab').click()
  await page.waitForSelector('text=Datos generales')
  await shot('studies_edit_new', true)
  await page.locator('.st-suggest .chip', { hasText: 'Perfil de lípidos' }).click()
  await page.waitForSelector('.st-row')
  check(await page.locator('.st-row').count() === 4, 'el título sugerido no precargó el grupo de lípidos')
  const rows = page.locator('.st-row')
  check(await field(rows.nth(0), 'Parámetro').inputValue() === 'Colesterol total', 'primera fila inesperada')
  check(await field(rows.nth(0), 'Ref. máxima').inputValue() === '200', 'no se rellenó el rango orientativo')
  check(await field(rows.nth(1), 'Ref. mínima').inputValue() === '50', 'el rango de HDL no usó el sexo del perfil (F → 50)')
  await field(rows.nth(0), 'Valor').fill('230')
  await field(rows.nth(1), 'Valor').fill('45,5')
  check(await rows.nth(0).locator('text=Alto ↑').count() === 1, 'el editor no marca el valor alto al capturarlo')
  await rows.nth(3).getByRole('button', { name: 'Quitar resultado' }).click()
  await rows.nth(2).getByRole('button', { name: 'Quitar resultado' }).click()
  check(await page.locator('.st-row').count() === 2, 'no se quitaron las filas')
  const [chooser] = await Promise.all([page.waitForEvent('filechooser'), page.getByRole('button', { name: 'Agregar foto o PDF' }).click()])
  await chooser.setFiles([{ name: 'reporte.png', mimeType: 'image/png', buffer: PNG }, { name: 'resultados.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4\n%%EOF') }])
  await page.waitForSelector('.st-att img')
  check(await page.locator('.st-att').count() === 2, 'no se agregaron los dos adjuntos')
  const inputsNew = await auditInputs()
  check(inputsNew.length === 0, 'campos con letra menor a 16px (alta): ' + JSON.stringify(inputsNew))
  await shot('studies_edit_new_filled', true)
  await page.locator('.topbar .text-btn', { hasText: 'Guardar' }).click()
  await page.waitForSelector('.fab')
  const card = page.locator('.card.tap', { hasText: 'Perfil de lípidos' }).first()
  await card.waitFor()
  check(await card.locator('text=2 fuera de rango').count() === 1, 'la tarjeta nueva no muestra "2 fuera de rango"')
  check(await card.locator('text=2 resultados').count() === 1, 'la tarjeta nueva no muestra "2 resultados"')
  await card.click()
  await page.waitForSelector('text=Adjuntos')
  check(await page.locator('[data-result="Colesterol total"] >> text=Alto ↑').count() === 1, 'detalle nuevo: falta Alto ↑')
  check(await page.locator('[data-result="Colesterol HDL"] >> text=Bajo ↓').count() === 1, 'detalle nuevo: falta Bajo ↓')
  check(await page.locator('[data-result="Colesterol HDL"] >> text=45.5 mg/dL').count() === 1, 'detalle nuevo: la coma decimal no se interpretó')
  check(await page.locator('.st-att img').count() === 1, 'detalle nuevo: falta la miniatura de la imagen')
  const stored = await page.evaluate(() => new Promise((res) => {
    const r = indexedDB.open('savia-p1')
    r.onsuccess = () => { const g = r.result.transaction('studyAttachments').objectStore('studyAttachments').getAll(); g.onsuccess = () => res(g.result.map((a) => [a.displayName, a.fileName, a.mime, a.data.byteLength])) }
  }))
  check(stored.length === 2 && stored[0][3] === PNG.length && stored[0][0] === stored[0][1], 'adjuntos mal guardados: ' + JSON.stringify(stored))
  await shot('studies_detail_new', true)
  // Visor de imagen a pantalla completa
  await page.locator('.st-att .thumb').first().click()
  await page.waitForSelector('.sheet.full img')
  check(await page.locator('.sheet.full').getByRole('button', { name: 'Compartir o guardar' }).count() === 1, 'visor: falta el botón de compartir')
  await page.waitForTimeout(450)   // deja terminar la animación de la hoja
  await shot('studies_viewer')
  await page.getByRole('button', { name: 'Cerrar' }).click()

  // Evolución de un parámetro (ahora con 4 valores)
  await page.locator('[data-result="Colesterol total"]').click()
  await page.waitForSelector('text=Evolución del parámetro')
  await page.waitForSelector('.chart')
  check(await page.locator('.chart').count() === 1, 'falta la gráfica de evolución')
  check(await page.locator('.card', { hasText: 'Valores' }).locator('.list-row').count() === 4, 'la evolución no tiene 4 valores')
  await shot('studies_trend', true)

  // Eliminar el estudio nuevo: se van también sus resultados y adjuntos
  await page.getByRole('button', { name: 'Regresar' }).click()
  await page.waitForSelector('text=Adjuntos')
  await page.getByRole('button', { name: 'Eliminar' }).first().click()
  await page.locator('.dialog').getByRole('button', { name: 'Eliminar' }).click()
  await page.waitForSelector('.fab')
  const left = await page.evaluate(() => new Promise((res) => {
    const r = indexedDB.open('savia-p1')
    r.onsuccess = () => {
      const tx = r.result.transaction(['studyAttachments', 'studyResults', 'studies'])
      const a = tx.objectStore('studyAttachments').count(), b = tx.objectStore('studyResults').count(), c = tx.objectStore('studies').count()
      tx.oncomplete = () => res([a.result, b.result, c.result])
    }
  }))
  check(left[0] === 0 && left[1] === 15 && left[2] === 5, 'el borrado no limpió resultados/adjuntos: ' + JSON.stringify(left))

  // Descartar cambios pide confirmación
  await page.locator('.fab').click()
  await field(page, 'Nombre del estudio').fill('Borrador')
  await page.getByRole('button', { name: 'Regresar' }).click()
  await page.waitForSelector('text=¿Descartar cambios?')
  await page.locator('.dialog').getByRole('button', { name: 'Descartar' }).click()
  await page.waitForSelector('.fab')
  check(await page.locator('text=Borrador').count() === 0, 'el borrador descartado se guardó')
}))

// ── Persona (oscuro): detalle y evolución ──
report('oscuro', await withApp({ seed: 'person', dark: true }, async ({ page, go, shot }) => {
  await go('/estudios/1')
  await page.waitForSelector('text=Resultados')
  await shot('studies_detail_dark', true)
  await go('/parametro/' + encodeURIComponent('Triglicéridos'))
  await page.waitForSelector('.chart')
  await shot('studies_trend_dark', true)
  await go('/estudios')
  await page.waitForSelector('.fab')
  await shot('studies_list_dark')
}))

// ── Mascota: textos de veterinario, categorías filtradas, sin rangos automáticos ──
report('mascota', await withApp({ seed: 'pet' }, async ({ page, go, shot, auditInputs }) => {
  await go('/estudios')
  await page.waitForSelector('text=Química sanguínea')
  await shot('studies_pet_list')
  await page.locator('.fab').click()
  await page.waitForSelector('text=Datos generales')
  check(await page.locator('label:text-is("Veterinaria o veterinario")').count() === 1, 'mascota: falta la etiqueta de veterinario')
  const chips = await page.locator('.card', { hasText: 'Datos generales' }).locator('.chips').first().locator('.chip').allInnerTexts()
  check(!chips.includes('Ginecológico') && !chips.includes('Vacuna') && chips.includes('Consulta o revisión'), 'mascota: categorías sin filtrar: ' + chips.join(','))
  await page.getByRole('button', { name: 'Agregar resultado' }).click()
  const row = page.locator('.st-row').first()
  await field(row, 'Parámetro').fill('gluc')
  await row.locator('.st-suggest .chip', { hasText: 'Glucosa' }).first().click()
  check(await field(row, 'Unidad').inputValue() === 'mg/dL', 'mascota: no se rellenó la unidad')
  check(await field(row, 'Ref. mínima').inputValue() === '' && await field(row, 'Ref. máxima').inputValue() === '', 'mascota: se rellenó un rango de personas')
  check(await page.locator('text=laboratorio veterinario').count() >= 1, 'mascota: falta la nota del laboratorio veterinario')
  check((await auditInputs()).length === 0, 'mascota: campos menores a 16px')
  await shot('studies_pet_edit', true)
  await go('/parametro/Creatinina')
  await page.waitForSelector('.chart')
  await shot('studies_pet_trend', true)
}))

// ── Menor de edad: nota de rangos de adultos; sin estudios → estado vacío ──
report('menor', await withApp({ seed: 'kid' }, async ({ page, go, shot }) => {
  await go('/estudios')
  await page.waitForSelector('text=Aún no hay estudios')
  await shot('studies_empty')
  await page.getByRole('button', { name: 'Agregar estudio' }).click()
  await page.waitForSelector('text=Datos generales')
  check(await page.locator('text=Rangos de referencia de adultos').count() === 1, 'menor: falta la nota de rangos de adultos')
  await shot('studies_kid_edit', true)
}))

// ── Estudio guardado como "Vacuna": aviso con enlace a la sección de vacunas ──
report('vacuna', await withApp({ seed: 'empty' }, async ({ page, go, shot }) => {
  await page.evaluate(() => new Promise((res, rej) => {
    const r = indexedDB.open('savia-p1')
    r.onsuccess = () => {
      const tx = r.result.transaction('studies', 'readwrite')
      tx.objectStore('studies').put({ id: 1, epochDay: 20000, title: 'Influenza', category: 'vacuna', place: 'Centro de salud', doctor: '', status: '', summary: '', followUpEpochDay: null })
      tx.oncomplete = res; tx.onerror = () => rej(tx.error)
    }
  }))
  await go('/estudios/1')
  await page.waitForSelector('text=Las vacunas ahora tienen su propia sección.')
  await shot('studies_vaccine_note')
  await page.getByRole('button', { name: 'Ir a Vacunas' }).click()
  await page.waitForFunction(() => location.hash === '#/vacunas')
}))

console.log(failures ? `CON FALLAS (${failures})` : 'OK')
process.exitCode = failures ? 1 : 0
