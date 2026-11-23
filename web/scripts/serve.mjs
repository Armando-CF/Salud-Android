// Sirve dist/ bajo una subcarpeta (como GitHub Pages: https://usuario.github.io/<repo>/).
// Uso: node scripts/serve.mjs [puerto] [base]      → http://localhost:4173/Salud-Android/
import http from 'node:http'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const TYPES = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.png': 'image/png', '.svg': 'image/svg+xml', '.webmanifest': 'application/manifest+json', '.woff2': 'font/woff2' }

export function serve(port = 4173, base = '/Salud-Android/', dir = fileURLToPath(new URL('../dist/', import.meta.url))) {
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://x')
    if (!url.pathname.startsWith(base)) { res.writeHead(404); res.end('fuera de la base'); return }
    let rel = decodeURIComponent(url.pathname.slice(base.length)) || 'index.html'
    if (rel.endsWith('/')) rel += 'index.html'
    const file = path.join(dir, rel)
    if (!file.startsWith(dir) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) { res.writeHead(404); res.end('no encontrado'); return }
    res.writeHead(200, { 'Content-Type': TYPES[path.extname(file)] ?? 'application/octet-stream', 'Cache-Control': 'no-cache' })
    fs.createReadStream(file).pipe(res)
  })
  return new Promise((resolve) => server.listen(port, () => resolve(server)))
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const port = Number(process.argv[2] ?? 4173), base = process.argv[3] ?? '/Salud-Android/'
  await serve(port, base)
  console.log(`http://localhost:${port}${base}`)
}
