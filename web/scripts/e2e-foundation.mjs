// Verifica lo esencial de la PWA: carga en subcarpeta, metadatos de iOS, manifest, service worker y modo sin conexión.
import { withApp, BASE } from './pw.mjs'
const fail = (m) => { console.error('FALLA:', m); process.exitCode = 1 }
const errors = await withApp({ seed: 'person' }, async ({ page, context, root, go, shot }) => {
  const meta = await page.evaluate(() => Object.fromEntries([...document.querySelectorAll('meta[name]')].map((m) => [m.name, m.content])))
  for (const [k, v] of [['apple-mobile-web-app-capable', 'yes'], ['apple-mobile-web-app-status-bar-style', 'black-translucent']]) if (meta[k] !== v) fail(`meta ${k}=${meta[k]}`)
  if (!/viewport-fit=cover/.test(meta.viewport)) fail('viewport-fit=cover')
  const links = await page.evaluate(() => [...document.querySelectorAll('link[rel]')].map((l) => [l.rel, l.href]))
  const manifestUrl = links.find((l) => l[0] === 'manifest')?.[1]
  const touchIcon = links.find((l) => l[0] === 'apple-touch-icon')?.[1]
  if (!manifestUrl?.includes(BASE)) fail('manifest fuera de la base: ' + manifestUrl)
  for (const u of [manifestUrl, touchIcon]) { const r = await page.request.get(u); if (!r.ok()) fail('no carga ' + u) }
  const manifest = await (await page.request.get(manifestUrl)).json()
  if (manifest.display !== 'standalone' || !manifest.icons?.length) fail('manifest incompleto')
  // service worker activo y controlando la página
  await page.waitForFunction(() => navigator.serviceWorker?.controller != null || navigator.serviceWorker.ready.then(() => true), null, { timeout: 15000 })
  await page.evaluate(() => navigator.serviceWorker.ready)
  await page.reload(); await page.waitForSelector('.app')
  const controlled = await page.evaluate(() => !!navigator.serviceWorker.controller)
  if (!controlled) fail('el service worker no controla la página')
  // sin conexión: recargar y navegar
  await context.setOffline(true)
  await page.reload(); await page.waitForSelector('.tabbar', { timeout: 10000 }).catch(() => fail('sin conexión no carga la app'))
  await go('/cuerpo'); await go('/mas')
  const atlasOffline = await page.evaluate(() => fetch('./atlas.json').then((r) => r.ok).catch(() => false))
  if (!atlasOffline) fail('atlas.json no disponible sin conexión')
  await shot('foundation_offline')
  await context.setOffline(false)
  console.log('meta', JSON.stringify({ capable: meta['apple-mobile-web-app-capable'], bar: meta['apple-mobile-web-app-status-bar-style'], viewport: meta.viewport }))
  console.log('manifest', manifest.name, manifest.display, manifest.start_url, manifest.scope, 'iconos', manifest.icons.length, '| SW controla:', controlled, '| atlas sin conexión:', atlasOffline)
})
if (errors.length) { console.error('Errores de consola:', errors.slice(0, 5)); process.exitCode = 1 }
console.log(process.exitCode ? 'CON FALLAS' : 'OK')
