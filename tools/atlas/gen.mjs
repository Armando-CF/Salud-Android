// Genera app/src/main/assets/atlas.json  —  ejecutar:  node tools/atlas/gen.mjs
import fs from 'node:fs';
import { frontView, backView } from './body.mjs';
import { infoFor, GENERAL_NOTE } from './info.mjs';
import { skeletonView, organsView, abdomenView, handView, footView } from './detail.mjs';

const views = [];
for (const sex of ['F', 'M']) {
  const f = frontView(sex);
  views.push({ id: `front_${sex}`, group: 'front', sex, title: 'Frente', box: [70, 20, 260, 846], shapes: f.shapes });
  const b = backView(sex);
  views.push({ id: `back_${sex}`, group: 'back', sex, title: 'Espalda', box: [70, 20, 260, 846], shapes: b.shapes });
}
for (const sex of ['F', 'M']) {
  const a = abdomenView(sex);
  views.push({ id: `abdomen_${sex}`, group: 'abdomen', sex, title: 'Abdomen', box: [112, 236, 176, 230], shapes: a.shapes, texts: a.texts });
  views.push({ id: `organs_${sex}`, group: 'organs', sex, title: 'Órganos', box: [100, 20, 200, 470], shapes: organsView(sex) });
}
views.push({ id: 'skeleton', group: 'skeleton', sex: null, title: 'Esqueleto', box: [70, 20, 260, 846], shapes: skeletonView() });
const hand = handView();
views.push({ id: 'hand', group: 'hand', sex: null, title: 'Mano', box: [30, 60, 320, 530], shapes: hand.shapes, texts: hand.texts });
const foot = footView();
views.push({ id: 'foot', group: 'foot', sex: null, title: 'Pie', box: [70, 130, 270, 470], shapes: foot.shapes, texts: foot.texts });
const info = {};
const missing = new Set();
for (const v of views) for (const sh of v.shapes) if (sh.id) { const i = infoFor(sh.id); if (i) info[sh.id] = i; else missing.add(sh.id); }
if (missing.size) { console.error('SIN INFO:', [...missing].join(', ')); process.exit(1); }
const atlas = { version: 1, note: GENERAL_NOTE, views, info };
const outPath = new URL('../../app/src/main/assets/atlas.json', import.meta.url);
fs.writeFileSync(outPath, JSON.stringify(atlas));
console.log('atlas.json', (fs.statSync(outPath).size / 1024).toFixed(1) + ' KB', views.map((v) => `${v.id}:${v.shapes.length}`).join(' '));
