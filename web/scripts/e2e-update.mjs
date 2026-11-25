// Actualización de la PWA: se publica una versión nueva mientras la app está abierta →
// aparece "Hay una versión nueva", al tocar Actualizar corre la versión nueva y los datos siguen ahí.
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { execSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { withApp } from './pw.mjs'

const web = fileURLToPath(new URL('..', import.meta.url))
const dist = path.join(web, 'dist')
const live = fs.mkdtempSync(path.join(os.tmpdir(), 'savia-live-')) + path.sep
const copy = (from, to) => fs.cpSync(from, to, { recursive: true, force: true })
const fails = []
const check = (ok, m) => { if (!ok) { fails.push(m); console.error('FALLA:', m) } else console.log('ok  ', m) }

copy(dist, live) // versión 1 "publicada"
const errors = await withApp({ seed: 'person', dir: live }, async ({ page }) => {
  await page.waitForSelector('.tabbar')
  await page.evaluate(() => navigator.serviceWorker.ready)
  await page.reload(); await page.waitForSelector('.tabbar')
  const v1 = await page.evaluate(() => document.documentElement.dataset.build)
  const count = () => page.evaluate(async () => { const db = await new Promise((r) => { const q = indexedDB.open('savia-p1'); q.onsuccess = () => r(q.result) }); return new Promise((r) => { const g = db.transaction('symptoms').objectStore('symptoms').count(); g.onsuccess = () => r(g.result) }) })
  const before = await count()
  check(await page.locator('.update-bar').count() === 0, 'sin versión nueva no hay aviso')

  // Se publica la versión 2 (nueva compilación) en el mismo sitio
  execSync('npm run build', { cwd: web, stdio: 'ignore' })
  copy(dist, live)
  await page.evaluate(() => navigator.serviceWorker.getRegistration().then((r) => r?.update()))
  await page.locator('.update-bar').waitFor({ timeout: 20000 }).catch(() => {})
  check(await page.locator('.update-bar').count() === 1, 'aparece el aviso "Hay una versión nueva"')
  check(await page.evaluate(() => document.documentElement.dataset.build) === v1, 'hasta que se toca Actualizar sigue corriendo la versión anterior')
  await Promise.all([page.waitForEvent('load', { timeout: 20000 }).catch(() => {}), page.getByRole('button', { name: 'Actualizar' }).click()])
  await page.waitForSelector('.tabbar')
  await page.waitForFunction((old) => document.documentElement.dataset.build !== old, v1, { timeout: 15000 }).catch(() => {})
  const v2 = await page.evaluate(() => document.documentElement.dataset.build)
  check(v2 && v2 !== v1, `tras Actualizar corre la versión nueva (${v1} → ${v2})`)
  check(await count() === before && before > 0, `los datos siguen intactos (${before} síntomas)`)
  check(await page.locator('.update-bar').count() === 0, 'el aviso desaparece')
})
if (errors.length) check(false, 'errores de consola: ' + errors.slice(0, 3).join(' | '))
fs.rmSync(live, { recursive: true, force: true })
console.log(fails.length ? `CON FALLAS (${fails.length})` : 'OK')
process.exitCode = fails.length ? 1 : 0
