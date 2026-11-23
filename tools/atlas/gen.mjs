// Genera app/src/main/assets/atlas.json  —  ejecutar:  node tools/atlas/gen.mjs
import fs from 'node:fs';
import { frontView, backView } from './body.mjs';
import { infoFor, GENERAL_NOTE } from './info.mjs';
import { skeletonView, organsView, abdomenView, handView, footView } from './detail.mjs';
import { petSideView, DOG, CAT } from './pets.mjs';
import { petOrgansView } from './petorgans.mjs';
import { petInfoFor, PET_NOTE } from './petinfo.mjs';

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
// Mascotas: vista lateral por especie (solo se muestran en perfiles de mascota de esa especie)
const PET_BOX = { perro: [24, 76, 492, 306], gato: [22, 62, 500, 292] };
for (const sp of [DOG, CAT]) {
  views.push({ id: `pet_side_${sp.species}`, group: 'pet_side', sex: null, species: sp.species, title: 'Cuerpo', box: PET_BOX[sp.species], shapes: petSideView(sp) });
  views.push({ id: `pet_organs_${sp.species}`, group: 'pet_organs', sex: null, species: sp.species, title: 'Órganos', box: PET_BOX[sp.species], shapes: petOrgansView(sp) });
}
const info = {};
const missing = new Set();
for (const v of views) for (const sh of v.shapes) if (sh.id) { const i = petInfoFor(sh.id) || infoFor(sh.id); if (i) info[sh.id] = i; else if (process.env.DRAFT) info[sh.id] = { n: sh.id, d: '' }; else missing.add(sh.id); }
if (missing.size) { console.error('SIN INFO:', [...missing].join(', ')); process.exit(1); }
const atlas = { version: 1, note: GENERAL_NOTE, petNote: PET_NOTE, views, info };
const outPath = new URL('../../app/src/main/assets/atlas.json', import.meta.url);
fs.writeFileSync(outPath, JSON.stringify(atlas));
console.log('atlas.json', (fs.statSync(outPath).size / 1024).toFixed(1) + ' KB', views.map((v) => `${v.id}:${v.shapes.length}`).join(' '));
