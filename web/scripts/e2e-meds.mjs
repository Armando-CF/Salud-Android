// Prueba en navegador (iPhone emulado) de Medicamentos, Citas y Vacunas.
// Uso (desde web/):  npm run build && node scripts/e2e-meds.mjs
import fs from 'node:fs'
import { withApp } from './pw.mjs'

// El navegador de prueba usa la zona horaria de Ciudad de México; los datos de ejemplo se calculan en Node.
// Se alinea Node con esa zona para que "hoy" sea el mismo día en ambos lados.
process.env.TZ = 'America/Mexico_City'

let failures = 0
const check = (cond, msg) => { if (!cond) { failures++; console.error('FALLA:', msg) } else console.log('  ok ·', msg) }
const has = async (page, text) => (await page.getByText(text, { exact: false }).count()) > 0

/** Pulsa algo que entrega un .ics y devuelve su texto. */
async function icsFrom(page, click) {
  const [download] = await Promise.all([page.waitForEvent('download', { timeout: 10000 }), click()])
  const text = fs.readFileSync(await download.path(), 'utf8')
  return { name: download.suggestedFilename(), text }
}

function checkIcs(label, { name, text }, extra = []) {
  check(name.endsWith('.ics') && name.startsWith('savia-'), `${label}: nombre de archivo ${name}`)
  check(text.startsWith('BEGIN:VCALENDAR\r\nVERSION:2.0') && text.trimEnd().endsWith('END:VCALENDAR'), `${label}: estructura VCALENDAR`)
  check(/BEGIN:VEVENT[\s\S]+UID:savia-[\s\S]+DTSTAMP:\d{8}T\d{6}Z[\s\S]+END:VEVENT/.test(text), `${label}: VEVENT con UID y DTSTAMP`)
  check(text.includes('BEGIN:VALARM') && text.includes('ACTION:DISPLAY'), `${label}: trae avisos (VALARM)`)
  check(text.split('\r\n').every((l) => Buffer.byteLength(l, 'utf8') <= 75), `${label}: líneas de 75 octetos o menos`)
  check(!/TZID|DTSTART:\d{8}T\d{6}Z/.test(text), `${label}: horas locales flotantes`)
  for (const [re, what] of extra) check(re.test(text), `${label}: ${what}`)
}

async function audit(ctx, where) {
  const small = await ctx.auditInputs()
  check(small.length === 0, `${where}: todos los campos con letra ≥ 16px${small.length ? ' → ' + JSON.stringify(small) : ''}`)
}

// ───────────────────────── Persona (claro) ─────────────────────────
console.log('\n== Persona ==')
const errPerson = await withApp({ seed: 'person' }, async (ctx) => {
  const { page, go, shot } = ctx

  // Medicamentos: lista y tomas del día
  await go('/medicamentos')
  await page.waitForSelector('[data-testid="med-1"]')
  check(await has(page, 'Loratadina') && await has(page, 'Vitamina D'), 'lista de medicamentos con datos')
  check(await has(page, '2 de 3 registradas'), 'progreso de hoy: 2 de 3')
  await shot('meds_list', true)

  const dose = page.locator('[data-testid="dose-2-20:00"]')
  await dose.getByRole('button', { name: 'Tomada' }).click()
  await page.waitForFunction(() => document.body.innerText.includes('¡Listo por hoy!'))
  check(await dose.getByText(/Tomada \d\d:\d\d/).count() === 1, 'marcar toma como tomada')
  await dose.getByRole('button', { name: 'Deshacer' }).click()
  await page.waitForFunction(() => document.body.innerText.includes('2 de 3 registradas'))
  check(await dose.getByRole('button', { name: 'Tomada' }).count() === 1, 'deshacer la toma')
  await dose.getByRole('button', { name: 'Omitir' }).click()
  await page.waitForFunction(() => document.body.innerText.includes('Omitida'))
  check(await has(page, 'Omitida'), 'omitir una toma')
  await dose.getByRole('button', { name: 'Deshacer' }).click()

  // Según sea necesario
  const asNeeded = page.locator('[data-testid="asneeded-3"]')
  await asNeeded.getByRole('button', { name: 'Registrar toma' }).click()
  await page.waitForFunction(() => document.body.innerText.includes('Hoy: 1 toma'))
  check(true, 'registrar toma libre')
  await asNeeded.getByRole('button', { name: 'Deshacer última' }).click()
  await page.waitForFunction(() => !document.body.innerText.includes('Hoy: 1 toma'))

  // Calendario del medicamento
  const medIcs = await icsFrom(page, () => page.locator('[data-testid="med-2"]').getByRole('button', { name: 'Agregar al calendario' }).click())
  checkIcs('medicamento .ics', medIcs, [
    [/RRULE:FREQ=DAILY/, 'se repite todos los días'],
    [/DTSTART:\d{8}T080000/, 'toma de las 08:00'],
    [/DTSTART:\d{8}T200000/, 'toma de las 20:00'],
    [/SUMMARY:Tomar Vitamina D/, 'título con el medicamento'],
    [/TRIGGER:PT0M/, 'avisa a la hora'],
  ])
  check((medIcs.text.match(/BEGIN:VEVENT/g) ?? []).length === 2, 'medicamento .ics: un evento por horario')

  // Editor de medicamento existente
  await go('/medicamentos/2')
  await page.waitForSelector('input[type="time"]')
  check(await page.locator('input[type="time"]').count() === 2, 'editor carga los dos horarios')
  await audit(ctx, 'editor de medicamento')
  await shot('meds_edit', true)

  // Alta de medicamento por la interfaz
  await go('/medicamentos/nuevo')
  await page.getByPlaceholder('p. ej. Loratadina').fill('Ibuprofeno')
  await page.getByPlaceholder('p. ej. 500 mg, 1 tableta, 5 ml').fill('400 mg')
  await page.getByRole('button', { name: 'Cada 12 h' }).click()
  await page.getByRole('button', { name: 'Sábado' }).click()
  await page.getByRole('button', { name: 'Domingo' }).click()
  check(await has(page, 'Lunes a viernes'), 'resumen de días al quitar sábado y domingo')
  await shot('meds_new', true)
  await page.locator('.topbar .text-btn', { hasText: 'Guardar' }).click()
  await page.waitForTimeout(400)
  await go('/medicamentos')
  await page.waitForFunction(() => document.body.innerText.includes('Ibuprofeno'))
  check(await has(page, 'Ibuprofeno · 400 mg') && await has(page, '08:00, 20:00 · Lunes a viernes'), 'medicamento creado aparece en la lista')

  // Validación: sin nombre no guarda
  await go('/medicamentos/nuevo')
  await page.locator('.topbar .text-btn', { hasText: 'Guardar' }).click()
  check(await has(page, 'Escribe el nombre'), 'validación de nombre vacío')

  // Citas
  await go('/citas')
  await page.waitForSelector('[data-testid="appt-1"]')
  check(await has(page, 'Revisión anual') && await has(page, 'Entrega de resultados'), 'lista de citas con próximas y anteriores')
  await shot('appointments_list', true)
  const apptIcs = await icsFrom(page, () => page.locator('[data-testid="appt-1"]').getByRole('button', { name: 'Agregar al calendario' }).click())
  checkIcs('cita .ics', apptIcs, [
    [/DTSTART:\d{8}T103000/, 'a las 10:30'],
    [/SUMMARY:Revisión anual/, 'título'],
    [/LOCATION:Consultorio 204\\, Hospital Ángeles/, 'lugar con coma escapada'],
    [/TRIGGER:-P1D/, 'aviso un día antes'],
    [/TRIGGER:-PT2H/, 'aviso dos horas antes'],
  ])
  await go('/citas/1')
  await page.waitForSelector('input[type="date"]')
  await audit(ctx, 'editor de cita')
  await shot('appointment_edit', true)
  await go('/citas/nueva')
  await page.getByPlaceholder('p. ej. Revisión anual').fill('Examen de la vista')
  await page.getByPlaceholder('p. ej. Dra. Rivera').fill('Dr. Luna')
  await page.getByRole('button', { name: 'Oftalmología' }).click()
  await shot('appointment_new', true)
  await page.locator('.topbar .text-btn', { hasText: 'Guardar' }).click()
  await page.waitForTimeout(400)
  await go('/citas')
  await page.waitForFunction(() => document.body.innerText.includes('Examen de la vista'))
  check(await has(page, 'Dr. Luna · Oftalmología'), 'cita creada aparece en la lista')
  // marcar una anterior como no realizada y de vuelta
  await page.locator('[data-testid="appt-3"]').getByRole('button', { name: 'Desmarcar' }).click()
  await page.waitForFunction(() => document.body.innerText.includes('Sin marcar'))
  check(true, 'desmarcar cita realizada')
  await page.locator('[data-testid="appt-3"]').getByRole('button', { name: 'Marcar como realizada' }).click()

  // Vacunas
  await go('/vacunas')
  await page.waitForSelector('[data-testid="vac-1"]')
  check(await has(page, 'Pendientes y próximas') && await has(page, 'Influenza'), 'lista de vacunas con pendientes')
  await shot('vaccines_person', true)
  const vacIcs = await icsFrom(page, () => page.locator('[data-testid="due-1"]').getByRole('button', { name: 'Al calendario' }).click())
  checkIcs('vacuna .ics', vacIcs, [
    [/DTSTART;VALUE=DATE:\d{8}/, 'evento de todo el día'],
    [/SUMMARY:Toca vacuna: Influenza/, 'título'],
    [/TRIGGER:PT9H/, 'aviso el mismo día a las 09:00'],
    [/TRIGGER:-P6DT15H/, 'aviso 7 días antes a las 09:00'],
  ])
  await go('/vacunas/1')
  await page.waitForSelector('input[type="date"]')
  await audit(ctx, 'editor de vacuna')
  await shot('vaccine_edit', true)
  await go('/vacunas/nueva')
  await page.getByPlaceholder('Ej. Influenza').fill('covid')
  await page.getByRole('button', { name: 'COVID-19' }).click()
  await page.getByRole('button', { name: 'Refuerzo', exact: true }).click()
  await page.getByRole('button', { name: '+1 año' }).click()
  check(await has(page, 'Tocaría el'), 'atajo de próxima fecha')
  await shot('vaccine_new', true)
  await page.locator('.topbar .text-btn', { hasText: 'Guardar' }).click()
  await page.waitForTimeout(400)
  await go('/vacunas')
  await page.waitForFunction(() => document.body.innerText.includes('COVID-19'))
  check(await has(page, 'COVID-19'), 'vacuna creada aparece en la lista')
  // validación
  await go('/vacunas/nueva')
  await page.locator('.topbar .text-btn', { hasText: 'Guardar' }).click()
  check(await has(page, 'Escribe o elige qué se aplicó'), 'validación de nombre de vacuna vacío')
})
check(errPerson.length === 0, `persona: sin errores de consola${errPerson.length ? ' → ' + errPerson.slice(0, 3).join(' | ') : ''}`)

// ───────────────────────── Mascota ─────────────────────────
console.log('\n== Mascota ==')
const errPet = await withApp({ seed: 'pet' }, async (ctx) => {
  const { page, go, shot } = ctx
  await go('/medicamentos')
  await page.waitForFunction(() => document.body.innerText.includes('Omeprazol'))
  check(await has(page, 'Citas veterinarias'), 'medicamentos de mascota: textos de veterinaria')
  await shot('pet_meds_list', true)
  const medIcs = await icsFrom(page, () => page.locator('[data-testid="med-1"]').getByRole('button', { name: 'Agregar al calendario' }).click())
  checkIcs('mascota medicamento .ics', medIcs, [
    [/SUMMARY:Firulais · Tomar Omeprazol/, 'el título lleva el nombre de la mascota'],
    [/RRULE:FREQ=DAILY;UNTIL=\d{8}T235959/, 'termina en la fecha de fin'],
    [/UID:savia-med-p3-1-0900@savia\.app/, 'UID con el perfil'],
  ])
  await go('/medicamentos/nuevo')
  check(await page.getByPlaceholder('p. ej. Amoxicilina, antipulgas').count() === 1, 'editor de medicamento con ejemplos de mascota')
  await go('/citas')
  await page.waitForFunction(() => document.body.innerText.includes('Vacuna anual'))
  await shot('pet_appointments', true)
  await go('/citas/nueva')
  check(await has(page, 'Veterinaria o veterinario') && !(await has(page, 'Especialidad')), 'cita de mascota: veterinario y sin especialidad')
  await audit(ctx, 'cita de mascota')
  await shot('pet_appointment_new', true)

  await go('/vacunas')
  await page.waitForSelector('[data-testid="due-3"]')
  check(await has(page, 'Vacunas y desparasitación') && await has(page, '1 vencida'), 'vacunas de mascota: título y vencidas')
  check(await page.getByRole('button', { name: 'Pulgas y garrapatas' }).count() >= 1, 'filtro por tipo para mascotas')
  await shot('pet_vaccines', true)
  // filtro
  await page.getByRole('group', { name: 'Filtrar por tipo' }).getByRole('button', { name: 'Desp. interna' }).click()
  check(await page.locator('[data-testid^="vac-"]').count() === 1, 'el filtro deja solo la desparasitación interna')
  await page.getByRole('group', { name: 'Filtrar por tipo' }).getByRole('button', { name: 'Todo' }).click()
  // registrar aplicación prellenada desde un pendiente
  await page.locator('[data-testid="due-1"]').getByRole('button', { name: 'Registrar aplicación' }).click()
  await page.waitForSelector('input[type="date"]')
  check(await page.getByPlaceholder('Ej. Rabia').inputValue() === 'Rabia', 'alta prellenada con el nombre')
  check(await has(page, 'suele repetirse cada año'), 'sugerencia de intervalo del catálogo')
  await page.getByRole('button', { name: 'Sugerido: en 1 año' }).click()
  await audit(ctx, 'vacuna de mascota')
  await shot('pet_vaccine_prefilled', true)
  await page.locator('.topbar .text-btn', { hasText: 'Guardar' }).click()
  await page.waitForTimeout(400)
  await go('/vacunas')
  await page.waitForSelector('[data-testid="due-3"]')
  // la Rabia vieja (id 1) quedó cumplida: ya no aparece como pendiente con su fecha anterior
  check(await page.locator('[data-testid="due-1"]').count() === 0, 'registrar la aplicación cumple la pendiente anterior')
  const vacIcs = await icsFrom(page, () => page.locator('[data-testid="due-4"]').getByRole('button', { name: 'Al calendario' }).click())
  checkIcs('mascota desparasitación .ics', vacIcs, [[/SUMMARY:Firulais · Toca desparasitación: Pipeta antipulgas/, 'título de desparasitación con el nombre']])
})
check(errPet.length === 0, `mascota: sin errores de consola${errPet.length ? ' → ' + errPet.slice(0, 3).join(' | ') : ''}`)

// ───────────────────────── Hijo (vacuna vencida) ─────────────────────────
console.log('\n== Hijo ==')
const errKid = await withApp({ seed: 'kid' }, async (ctx) => {
  const { page, go, shot } = ctx
  await go('/vacunas')
  await page.waitForSelector('[data-testid="due-1"]')
  check(await has(page, 'Vencida hace 35 días'), 'cartilla del hijo: influenza vencida')
  check(await page.getByRole('group', { name: 'Filtrar por tipo' }).count() === 0, 'personas: sin filtro por tipo')
  await shot('kid_vaccines', true)
  await go('/vacunas/nueva')
  // a los 7 años, las sugerencias de infancia van primero
  const first = await page.getByRole('group', { name: 'Sugerencias' }).getByRole('button').first().innerText()
  check(first === 'Influenza' || first.startsWith('SRP'), `sugerencias según la edad (primera: ${first})`)
  await go('/medicamentos')
  await page.waitForFunction(() => document.body.innerText.includes('Paracetamol infantil'))
  await shot('kid_meds', true)
})
check(errKid.length === 0, `hijo: sin errores de consola${errKid.length ? ' → ' + errKid.slice(0, 3).join(' | ') : ''}`)

// ───────────────────────── Sin datos ─────────────────────────
console.log('\n== Sin datos ==')
const errEmpty = await withApp({ seed: 'empty' }, async (ctx) => {
  const { page, go, shot } = ctx
  await go('/medicamentos')
  check(await has(page, 'Aún no tienes medicamentos'), 'estado vacío de medicamentos')
  await shot('empty_meds')
  await go('/citas')
  check(await has(page, 'Aún no tienes citas'), 'estado vacío de citas')
  await shot('empty_appointments')
  await go('/vacunas')
  check(await has(page, 'Lleva aquí tu cartilla de vacunación'), 'estado vacío de vacunas')
  await shot('empty_vaccines')
})
check(errEmpty.length === 0, `sin datos: sin errores de consola${errEmpty.length ? ' → ' + errEmpty.slice(0, 3).join(' | ') : ''}`)

// ───────────────────────── Tema oscuro ─────────────────────────
console.log('\n== Oscuro ==')
const errDark = await withApp({ seed: 'person', dark: true }, async (ctx) => {
  const { page, go, shot } = ctx
  await go('/medicamentos')
  await page.waitForSelector('[data-testid="med-1"]')
  await shot('meds_list_dark', true)
  await go('/vacunas')
  await page.waitForSelector('[data-testid="vac-1"]')
  await shot('vaccines_dark', true)
  await go('/citas/1')
  await page.waitForSelector('input[type="date"]')
  await shot('appointment_edit_dark', true)
})
check(errDark.length === 0, `oscuro: sin errores de consola${errDark.length ? ' → ' + errDark.slice(0, 3).join(' | ') : ''}`)

console.log(failures ? `\nCON ${failures} FALLAS` : '\nOK')
process.exitCode = failures ? 1 : 0
