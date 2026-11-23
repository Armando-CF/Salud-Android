// Utilidades de prueba con Playwright (Chromium emulando un iPhone).
// Uso típico (desde web/):
//   import { withApp } from './pw.mjs'
//   await withApp({ seed: 'family' }, async ({ page, go, shot }) => { await go('/sintomas'); await shot('sintomas') })
// Requiere `npm run build` antes (sirve dist/ en una subcarpeta, como GitHub Pages).
import { createRequire } from 'node:module'
import fs from 'node:fs'
import { serve } from './serve.mjs'
import { demoData } from './demo-data.mjs'

const require = createRequire(import.meta.url)
const { chromium, devices } = require('playwright')

export const BASE = '/Salud-Android/'
const SHOTS = new URL('../shots/', import.meta.url)

/** Escribe registros directamente en IndexedDB (las bases ya deben existir: carga la app antes). */
export async function writeIdb(page, dbName, stores) {
  await page.evaluate(async ({ dbName, stores }) => {
    const db = await new Promise((res, rej) => { const r = indexedDB.open(dbName); r.onsuccess = () => res(r.result); r.onerror = () => rej(r.error) })
    const names = Object.keys(stores).filter((n) => db.objectStoreNames.contains(n))
    if (names.length) {
      await new Promise((res, rej) => {
        const tx = db.transaction(names, 'readwrite')
        for (const n of names) for (const row of stores[n]) tx.objectStore(n).put(row)
        tx.oncomplete = res; tx.onerror = () => rej(tx.error)
      })
    }
    db.close()
  }, { dbName, stores })
}

/**
 * Abre la app en un iPhone emulado.
 * opts.seed: 'none' (bienvenida), 'empty' (bienvenida ya hecha, sin datos), 'person' (datos de ejemplo),
 *            'family' (persona + hijo + perro), 'pet' (como 'family' con el perro activo), 'kid' (hijo activo).
 * opts.dark: tema oscuro. opts.safeArea: simula muesca y barra de inicio del iPhone (por defecto true).
 */
export async function withApp(opts, fn) {
  const port = opts.port ?? 4300 + Math.floor(Math.random() * 500)
  const server = await serve(port, BASE)
  const browser = await chromium.launch()
  const context = await browser.newContext({ ...devices['iPhone 14'], colorScheme: opts.dark ? 'dark' : 'light', locale: 'es-MX' }) // misma zona horaria que Node: los datos de ejemplo se calculan aquí
  const page = await context.newPage()
  const errors = []
  page.on('pageerror', (e) => errors.push(String(e)))
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()) })
  const root = `http://localhost:${port}${BASE}`
  const safe = async () => {
    if (opts.safeArea === false) return
    await page.addStyleTag({ content: ':root{--safe-top:47px !important;--safe-bottom:34px !important}' })
  }
  await page.goto(root)
  await page.waitForSelector('.app')
  const seed = opts.seed ?? 'person'
  if (seed !== 'none') {
    await page.waitForFunction(() => indexedDB.databases().then((d) => d.some((x) => x.name === 'savia-p1')))
    const data = demoData(seed)
    for (const [dbName, stores] of Object.entries(data)) {
      if (dbName !== 'savia-meta' && dbName !== 'savia-p1') {
        // crea la base del perfil con el mismo esquema que la app (la abre Dexie al navegar); aquí basta abrirla desde la app
        await page.evaluate(async (name) => { await new Promise((r) => setTimeout(r, 0)); void name }, dbName)
      }
    }
    await page.evaluate(async (data) => { window.__seed = data }, data)
    await writeIdb(page, 'savia-meta', data['savia-meta'])
    // Las bases de los demás perfiles se crean al recargar (la app las abre al listar perfiles).
    await page.reload(); await page.waitForSelector('.app')
    await page.waitForTimeout(400)
    for (const [dbName, stores] of Object.entries(data)) if (dbName !== 'savia-meta') {
      await page.waitForFunction((n) => indexedDB.databases().then((d) => d.some((x) => x.name === n)), dbName, { timeout: 8000 }).catch(() => {})
      await writeIdb(page, dbName, stores)
    }
    await page.reload(); await page.waitForSelector('.app')
  }
  await safe()
  const go = async (route) => {
    await page.evaluate((r) => { window.location.hash = '#' + r }, route)
    await page.waitForTimeout(350)
    await safe()
  }
  const shot = async (name, full = false) => {
    fs.mkdirSync(SHOTS, { recursive: true })
    if (full) {
      // captura el contenido completo de la pantalla (expande el cuerpo desplazable)
      const h = await page.evaluate(() => { const b = document.querySelector('.page-body'); return b ? b.scrollHeight + 200 : 0 })
      if (h > 844) await page.setViewportSize({ width: 390, height: Math.min(h, 4200) })
      await page.waitForTimeout(150)
    }
    await page.screenshot({ path: new URL(`${name}.png`, SHOTS).pathname })
    if (full) await page.setViewportSize({ width: 390, height: 844 })
  }
  /** Verifica que ningún campo visible tenga letra menor a 16px (evita el zoom automático de iOS). */
  const auditInputs = async () => page.evaluate(() => [...document.querySelectorAll('input, textarea, select')]
    .filter((el) => el.offsetParent !== null && !['checkbox', 'radio', 'range', 'file', 'hidden'].includes(el.type))
    .map((el) => ({ tag: el.tagName, type: el.type, size: parseFloat(getComputedStyle(el).fontSize) }))
    .filter((x) => x.size < 16))
  try {
    await fn({ page, context, browser, root, go, shot, auditInputs, errors })
  } finally {
    await browser.close()
    server.close()
  }
  return errors
}
