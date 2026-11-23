// Recorrido completo de la app web en un iPhone emulado:
//  - abre TODAS las rutas con cada tipo de perfil (persona, menor, mascota) y sin datos,
//  - verifica que no haya errores de consola, que ningún campo tenga letra menor a 16px y que nada se desborde a lo ancho,
//  - cambia de perfil desde Inicio, y registra un síntoma SIN CONEXIÓN que sigue ahí al recargar.
import { withApp } from './pw.mjs'

const fails = []
const fail = (m) => { fails.push(m); console.error('FALLA:', m) }
const ROUTES = [
  '/', '/mas', '/sintomas', '/sintomas/nuevo', '/sintomas/1', '/cuerpo', '/ciclo', '/ciclo/estadisticas', '/ciclo/ajustes',
  '/mediciones', '/mediciones/weight', '/mediciones/weight?add=1', '/mediciones/bp', '/mediciones/temp', '/estudios', '/estudios/nuevo', '/estudios/1',
  '/estudios/1/editar', '/medicamentos', '/medicamentos/nuevo', '/medicamentos/1', '/citas', '/citas/nueva', '/citas/1', '/vacunas', '/vacunas/nueva',
  '/vacunas/1', '/bienestar', '/autoexploracion', '/ficha', '/perfiles', '/perfiles/nuevo/person', '/perfiles/nuevo/pet', '/exportar', '/ajustes', '/no-existe',
]

for (const seed of ['person', 'kid', 'pet', 'empty']) {
  const errors = await withApp({ seed, dark: seed === 'pet' }, async ({ page, go, auditInputs }) => {
    await page.waitForSelector('.tabbar')
    const t = await page.evaluate(() => { const d = new Date(); return Math.round(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()) / 86400000) })
    for (const route of [...ROUTES, `/ciclo/dia/${t}`]) {
      await go(route)
      const ok = await page.locator('.page').first().waitFor({ timeout: 6000 }).then(() => true).catch(() => false)
      if (!ok) { fail(`${seed} ${route}: no se pintó la pantalla`); continue }
      const small = await auditInputs()
      if (small.length) fail(`${seed} ${route}: campos con letra menor a 16px ${JSON.stringify(small)}`)
      const overflow = await page.evaluate(() => { const b = document.querySelector('.page-body'); return b ? b.scrollWidth - b.clientWidth : 0 })
      if (overflow > 1) fail(`${seed} ${route}: desborde horizontal de ${overflow}px`)
      const pending = await page.evaluate(() => document.body.innerText.includes('Pendiente') && !!document.querySelector('.page-body > p.muted'))
      if (pending) fail(`${seed} ${route}: pantalla sin implementar`)
    }
  })
  if (errors.length) fail(`${seed}: errores de consola: ${[...new Set(errors)].slice(0, 4).join(' | ')}`)
  console.log(`rutas ${seed}: ${ROUTES.length + 1} revisadas`)
}

// Cambio de perfil desde Inicio y trabajo sin conexión
const errors = await withApp({ seed: 'family' }, async ({ page, context, go, shot }) => {
  await page.waitForSelector('.tabbar')
  await page.getByText('Tu familia y mascotas').waitFor({ timeout: 8000 })
  await page.locator('.list-row', { hasText: 'Firulais' }).first().click()
  await page.locator('.topbar h1', { hasText: 'Firulais' }).waitFor({ timeout: 8000 }).catch(() => fail('no cambió al perfil de la mascota'))
  if (!(await page.locator('.tabbar').innerText()).includes('Mediciones')) fail('la mascota no muestra la pestaña Mediciones')
  await shot('all_pet_home')
  // sin conexión: registrar un síntoma y recargar
  await page.evaluate(() => navigator.serviceWorker.ready)
  await page.reload(); await page.waitForSelector('.tabbar')
  await context.setOffline(true)
  await page.reload(); await page.waitForSelector('.tabbar', { timeout: 10000 }).catch(() => fail('sin conexión no abre'))
  await go('/sintomas/nuevo')
  await page.locator('input.input').first().fill('Estornudos sin conexión')
  await page.getByRole('button', { name: /^Guardar/ }).first().click()
  await page.waitForTimeout(600)
  await page.reload(); await page.waitForSelector('.tabbar')
  await go('/sintomas')
  await page.getByText('Estornudos sin conexión').first().waitFor({ timeout: 6000 }).catch(() => fail('el síntoma registrado sin conexión no aparece tras recargar'))
  await go('/cuerpo'); await page.waitForSelector('svg path', { timeout: 6000 }).catch(() => fail('el atlas no carga sin conexión'))
  await shot('all_offline_atlas')
  await context.setOffline(false)
})
if (errors.filter((e) => !/Failed to load resource|ERR_INTERNET_DISCONNECTED/.test(e)).length) fail('familia/sin conexión: ' + errors.slice(0, 3).join(' | '))
console.log(fails.length ? `CON FALLAS (${fails.length})` : 'OK')
process.exitCode = fails.length ? 1 : 0
