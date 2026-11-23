// Renderiza las vistas del atlas a PNG para revisión: node preview.mjs <salida.png> [idVista...]
import fs from 'node:fs';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const { chromium } = require('playwright');

const atlas = JSON.parse(fs.readFileSync(new URL('../../app/src/main/assets/atlas.json', import.meta.url)));
const out = process.argv[2];
const ids = process.argv.slice(3);
const views = ids.length ? atlas.views.filter((v) => ids.includes(v.id)) : atlas.views;
const dark = process.env.DARK === '1';
const pal = dark
  ? { bg: '#0F1512', skin: '#26302B', stroke: '#6F837A', ink: '#9FB2A9', bone: '#D9D0BE', boneStroke: '#8A8272', text: '#DEE4DF' }
  : { bg: '#F5FAF6', skin: '#E6EEE9', stroke: '#8FA39A', ink: '#6F837A', bone: '#F2EBDD', boneStroke: '#A89F8B', text: '#171D1A' };
const scale = Number(process.env.SCALE || 1.6);
let html = `<html><body style="margin:0;background:${pal.bg};display:flex;flex-wrap:wrap;gap:8px;padding:8px;font-family:sans-serif">`;
for (const v of views) {
  const [bx, by, bw, bh] = v.box;
  html += `<div><div style="color:${pal.text};font-size:12px">${v.id}</div><svg width="${bw * scale}" height="${bh * scale}" viewBox="${bx} ${by} ${bw} ${bh}">`;
  for (const s of v.shapes) {
    let fill = 'none', stroke = pal.stroke, sw = 1, extra = '';
    if (s.kind === 'region') fill = pal.skin;
    else if (s.kind === 'skin') { fill = pal.skin; stroke = s.outline ? pal.stroke : 'none'; extra = s.alpha ? ` opacity="${s.alpha}"` : ''; }
    else if (s.kind === 'bone') { fill = pal.bone; stroke = pal.boneStroke; sw = 0.8; }
    else if (s.kind === 'organ') { fill = s.color; stroke = 'rgba(0,0,0,0.35)'; sw = 0.8; if (s.alpha) extra += ` fill-opacity="${s.alpha}"`; if (s.dashed) extra += ' stroke-dasharray="3 2"'; }
    else if (s.kind === 'deco') { fill = s.fill === 'ink' ? pal.ink : 'none'; stroke = s.fill === 'ink' ? 'none' : pal.ink; sw = 0.9; if (s.dashed) extra = ' stroke-dasharray="3 3"'; }
    html += `<path d="${s.d}" fill="${fill}" stroke="${stroke}" stroke-width="${sw}" stroke-linejoin="round"${extra}/>`;
  }
  for (const t of v.texts || []) {
    html += `<text x="${t.x}" y="${t.y}" font-size="${t.size}" text-anchor="${t.anchor || 'middle'}" fill="${pal.text}">${t.t}</text>`;
  }
  html += `</svg></div>`;
}
html += '</body></html>';
const browser = await chromium.launch();
const page = await browser.newPage({ deviceScaleFactor: 1 });
await page.setContent(html);
await page.screenshot({ path: out, fullPage: true });
await browser.close();
console.log('ok', out, views.map((v) => v.id).join(','));
