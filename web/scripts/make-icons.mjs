// Genera los iconos PNG de la PWA a partir del logo (requiere Playwright/Chromium; solo se usa en desarrollo).
import { createRequire } from 'node:module'
import fs from 'node:fs'
const require = createRequire(import.meta.url)
const { chromium } = require('playwright')
const logo = (pad) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="100%" height="100%"><rect width="108" height="108" fill="#1B6B58"/><g transform="translate(54 54) scale(${pad}) translate(-54 -54)"><path fill="#fff" d="M54 27c16 8 24 21 22 35-2 12-11 19-22 20-11-1-20-8-22-20-2-14 6-27 22-35z"/><path fill="none" stroke="#1B6B58" stroke-width="3.4" stroke-linecap="round" stroke-linejoin="round" d="M37 58h9l4-9 6 18 4-9h11"/></g></svg>`
const out = new URL('../public/icons/', import.meta.url)
fs.mkdirSync(out, { recursive: true })
const browser = await chromium.launch()
for (const [name, size, pad] of [['apple-touch-icon', 180, 1.25], ['icon-192', 192, 1.25], ['icon-512', 512, 1.25], ['icon-maskable-512', 512, 0.95]]) {
  const page = await browser.newPage({ viewport: { width: size, height: size }, deviceScaleFactor: 1 })
  await page.setContent(`<html><body style="margin:0;width:${size}px;height:${size}px">${logo(pad)}</body></html>`)
  await page.screenshot({ path: new URL(`${name}.png`, out).pathname })
  await page.close()
}
await browser.close()
console.log('iconos generados')
