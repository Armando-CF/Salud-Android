// Vistas de detalle: esqueleto, órganos, mano y pie.
import { CX, band, edge, mirror, toPath, ellipse, absPath, absLine, blob, spline, thick, bone, capsule, roundRect, konst } from './geom.mjs';
import { params, frontView, Y } from './body.mjs';

const A = (pts) => pts.map(([x, y]) => [CX + x, y]);           // media figura → absoluto
const M = (pts) => pts.map(([x, y]) => [2 * CX - x, y]);       // espejo en absoluto

/** Silueta completa de la figura frontal como fondo no tocable. */
function silhouette(sex, alpha = 1) {
  return frontView(sex).shapes.filter((s) => s.kind === 'region').map((s) => ({ kind: 'skin', d: s.d, alpha }));
}

// ───────────────────────── Esqueleto ─────────────────────────
export function skeletonView() {
  const p = params('M');
  const s = silhouette('M', 0.55);
  const B = (id, pts) => s.push({ id, kind: 'bone', d: absPath(pts) });
  const BM = (id, parts) => s.push({ id, kind: 'bone', d: parts.map(absPath).join('') });
  const both = (id, fn) => { BM(`${id}_izq`, fn(1)); BM(`${id}_der`, fn(-1)); };
  const X = (sgn, x) => CX + sgn * x;

  // escápulas (detrás de las costillas)
  both('h_escapula', (g) => [[[X(g, 34), 180], [X(g, 70), 176], [X(g, 64), 204], [X(g, 50), 236], [X(g, 38), 214]]]);
  // columna
  const vert = (y0, y1, n, w0, w1) => { const out = []; const h = (y1 - y0) / n; for (let i = 0; i < n; i++) { const w = w0 + ((w1 - w0) * i) / Math.max(1, n - 1); out.push(roundRect(CX - w / 2, y0 + i * h + 0.6, w, h - 1.2, 1.5, 2)); } return out; };
  BM('h_cervicales', vert(138, 172, 7, 13, 16));
  BM('h_dorsales', vert(172, 292, 12, 15, 19));
  BM('h_lumbares', vert(292, 372, 5, 21, 25));
  // costillas: 10 arcos por lado (las flotantes cortas)
  both('h_costillas', (g) => {
    const parts = [];
    for (let i = 0; i < 12; i++) {
      const y = 178 + i * 9.6;
      const w = [38, 47, 54, 58, 61, 63, 64, 64, 62, 59, 50, 40][i];
      const drop = 6 + i * 0.9;
      const endX = i < 7 ? 9 : i < 10 ? 14 + (i - 7) * 7 : w * 0.72; // llegan al esternón, al cartílago o quedan flotantes
      const endY = i < 7 ? y + drop + 4 : y + drop + 9;
      const line = spline([[9, y], [w * 0.6, y + drop * 0.25], [w, y + drop * 0.9], [Math.max(endX, w * 0.55), endY + (i < 10 ? 0 : -3)], ...(i < 10 ? [[endX, endY + (i < 7 ? -2 : 3)]] : [])], 5);
      parts.push(thick(line.map(([x, yy]) => [X(g, x), yy]), 3.4));
    }
    return parts;
  });
  // esternón
  B('h_esternon', [[CX - 9, 172], [CX + 9, 172], [CX + 10, 186], [CX + 6.5, 192], [CX + 6.5, 240], [CX + 3, 256], [CX, 262], [CX - 3, 256], [CX - 6.5, 240], [CX - 6.5, 192], [CX - 10, 186]]);
  // clavículas
  both('h_clavicula', (g) => [thick(spline([[9, 173], [28, 170], [48, 172], [68, 174]], 5).map(([x, y]) => [X(g, x), y]), 4.6)]);
  // cráneo y mandíbula
  const hw = p.head;
  B('h_craneo', A([...edge(hw, 30, 112, 2), [hw(112) - 6, 118], [16, 122], [0, 123], [-16, 122], [-hw(112) + 6, 118], ...edge((y) => -hw(y), 112, 30, 2)]));
  B('h_mandibula', A([[hw(110) - 2, 108], ...edge((y) => hw(y) - 1, 116, 142, 2), ...edge((y) => -hw(y) + 1, 142, 116, 2), [-hw(110) + 2, 108], [-hw(112) + 8, 120], [-16, 126], [0, 127], [16, 126], [hw(112) - 8, 120]]));
  for (const g of [-1, 1]) s.push({ kind: 'deco', d: absPath(ellipse(X(g, 15), 86, 8, 7, 16)), fill: 'ink' });
  s.push({ kind: 'deco', d: absPath([[CX, 96], [CX + 4.5, 108], [CX - 4.5, 108]]), fill: 'ink' });
  // pelvis
  both('h_coxal', (g) => [A(spline([[12, 374], [40, 364], [66, 366], [73, 382], [66, 404], [52, 416], [46, 430], [38, 448], [22, 456], [9, 448], [8, 432], [20, 424], [22, 410], [14, 398], [12, 374]], 4)).map(([x, y]) => [CX + g * (x - CX), y])]);
  B('h_sacro', [[CX - 14, 373], [CX + 14, 373], [CX + 8, 400], [CX + 4, 416], [CX, 428], [CX - 4, 416], [CX - 8, 400]]);
  // brazos
  const ax = (y) => (p.armOuter(y) + p.armInner(Math.max(214, y))) / 2;
  both('h_humero', (g) => [bone(X(g, ax(196) - 3), 194, X(g, ax(330)), 332, 8, 10, 8)]);
  both('h_radio', (g) => [bone(X(g, ax(346) + 5.5), 346, X(g, ax(446) + 5), 446, 4.6, 4.8, 6)]);
  both('h_cubito', (g) => [bone(X(g, ax(344) - 5), 342, X(g, ax(446) - 5.5), 446, 4.4, 6.2, 3.6)]);
  both('h_mano', (g) => {
    const parts = [];
    const wx = (p.armOuter(456) + p.armInner(456)) / 2;
    for (let i = 0; i < 4; i++) parts.push(roundRect(X(g, wx - 9 + i * 4.8) - (g < 0 ? 4.2 : 0), 452, 4.2, 4.4, 1.4, 2));
    for (let i = 0; i < 4; i++) parts.push(roundRect(X(g, wx - 8 + i * 4.8) - (g < 0 ? 4.2 : 0), 457.6, 4.2, 4.4, 1.4, 2));
    const rays = [[-7, 490, -9.5, 518, 3], [-2.5, 493, -3.5, 527, 3], [2.5, 493, 2, 529, 3], [7, 491, 7.5, 524, 3]];
    for (const [x0, y0, x1, y1] of rays) {
      parts.push(capsule(X(g, wx + x0 * 0.8), 464, X(g, wx + x0), y0 - 2, 2.8));
      const dx = x1 - x0, dy = y1 - y0;
      parts.push(capsule(X(g, wx + x0), y0 + 1, X(g, wx + x0 + dx * 0.42), y0 + dy * 0.42, 2.6));
      parts.push(capsule(X(g, wx + x0 + dx * 0.5), y0 + dy * 0.5, X(g, wx + x0 + dx * 0.76), y0 + dy * 0.76, 2.4));
      parts.push(capsule(X(g, wx + x0 + dx * 0.83), y0 + dy * 0.83, X(g, wx + x1), y1, 2.2));
    }
    // pulgar (hacia adentro)
    parts.push(capsule(X(g, wx - 9), 464, X(g, wx - 13.5), 479, 2.8));
    parts.push(capsule(X(g, wx - 14), 482, X(g, wx - 14.5), 491, 2.6));
    parts.push(capsule(X(g, wx - 14.5), 494, X(g, wx - 13), 500, 2.4));
    return parts;
  });
  // piernas
  const lx = (y) => (p.legOuter(y) + p.legInner(Math.max(448, y))) / 2;
  both('h_femur', (g) => [bone(X(g, 46), 424, X(g, lx(590)), 590, 9.5, 9.5, 12), ellipse(X(g, 38), 418, 9, 9, 16)]);
  both('h_rotula', (g) => [ellipse(X(g, lx(606)), 607, 8, 9, 16)]);
  both('h_tibia', (g) => [bone(X(g, lx(626) - 3), 626, X(g, lx(796) - 2), 796, 8, 11, 7.5)]);
  both('h_perone', (g) => [bone(X(g, lx(632) + 11), 632, X(g, lx(798) + 8), 800, 3.4, 4.4, 4.6)]);
  both('h_pie', (g) => {
    const parts = [];
    const fx = (p.legOuter(810) + p.legInner(810)) / 2;
    parts.push(roundRect(X(g, fx) - 9, 806, 18, 9, 3, 3));
    parts.push(roundRect(X(g, fx) - 10, 816.5, 9.5, 8, 2.5, 3));
    parts.push(roundRect(X(g, fx) + 0.5, 816.5, 9.5, 8, 2.5, 3));
    for (let i = 0; i < 5; i++) {
      const x0 = fx - 9 + i * 4.6, x1 = fx - 13 + i * 6.6;
      parts.push(capsule(X(g, x0), 827, X(g, x0 + (x1 - x0) * 0.55), 841, 2.8));
      parts.push(capsule(X(g, x0 + (x1 - x0) * 0.68), 844, X(g, x1), 852, 2.6));
    }
    return parts;
  });
  return s;
}

// ───────────────────────── Órganos ─────────────────────────
export function organsView(sex) {
  const s = silhouette(sex, 0.7);
  const F = sex === 'F';
  const O = (id, color, pts, extra = {}) => s.push({ id, kind: 'organ', color, d: absPath(pts), ...extra });
  const OM = (id, color, parts) => s.push({ id, kind: 'organ', color, d: parts.map(absPath).join('') });
  const C = CX;
  // Recuerda: la derecha de la persona está a la izquierda de la pantalla.
  O('o_cerebro', '#E7AEB6', blob([[C - 30, 70], [C - 26, 48], [C - 10, 37], [C + 10, 37], [C + 26, 48], [C + 30, 70], [C + 22, 84], [C + 6, 88], [C - 6, 88], [C - 22, 84]], 6));
  s.push({ kind: 'deco', d: absLine(spline([[C, 39], [C - 2, 52], [C + 2, 66], [C, 86]], 4)) });
  O('o_traquea', '#BCD3DF', roundRect(C - 4.5, 146, 9, 52, 3));
  O('o_tiroides', '#D58466', blob([[C - 11, 150], [C - 5, 153], [C, 151.5], [C + 5, 153], [C + 11, 150], [C + 12, 160], [C + 6, 163], [C, 158.5], [C - 6, 163], [C - 12, 160]], 5));
  // pulmones
  O('o_pulmon_der', '#EBA3AD', blob([[C - 12, 190], [C - 26, 184], [C - 44, 200], [C - 55, 232], [C - 57, 264], [C - 40, 268], [C - 14, 262], [C - 10, 230]], 6));
  O('o_pulmon_izq', '#EBA3AD', blob([[C + 12, 190], [C + 26, 184], [C + 44, 200], [C + 55, 232], [C + 57, 264], [C + 44, 268], [C + 34, 258], [C + 30, 236], [C + 14, 222]], 6));
  O('o_corazon', '#C7434C', blob([[C - 8, 222], [C + 6, 216], [C + 22, 222], [C + 32, 240], [C + 26, 258], [C + 12, 268], [C - 2, 258], [C - 10, 240]], 6));
  s.push({ kind: 'deco', d: absLine(spline([[C - 60, 276], [C - 30, 270], [C, 274], [C + 30, 270], [C + 60, 276]], 5)), dashed: true });
  // bazo y páncreas (parcialmente detrás del estómago)
  O('o_bazo', '#8D5C8C', blob([[C + 48, 284], [C + 58, 288], [C + 60, 302], [C + 52, 310], [C + 44, 300]], 6));
  O('o_pancreas', '#E2C366', blob([[C - 14, 318], [C, 313], [C + 22, 311], [C + 44, 306], [C + 46, 313], [C + 24, 320], [C + 2, 324], [C - 12, 327]], 5));
  O('o_higado', '#9A4B3D', blob([[C - 60, 284], [C - 46, 276], [C - 20, 275], [C + 6, 279], [C + 22, 286], [C + 12, 294], [C - 12, 302], [C - 36, 314], [C - 58, 312]], 6));
  O('o_vesicula', '#6FA56A', blob([[C - 26, 302], [C - 20, 304], [C - 19, 313], [C - 24, 318], [C - 29, 312]], 5));
  O('o_estomago', '#E2A276', blob([[C + 12, 282], [C + 24, 280], [C + 44, 284], [C + 52, 298], [C + 46, 314], [C + 30, 322], [C + 14, 320], [C + 8, 312], [C + 22, 306], [C + 24, 296], [C + 14, 292]], 5));
  // intestino grueso (marco cólico)
  const colon = spline([[C - 48, 396], [C - 50, 372], [C - 49, 344], [C - 36, 334], [C, 337], [C + 36, 334], [C + 49, 344], [C + 50, 372], [C + 46, 394], [C + 30, 404], [C + 12, 408], [C + 2, 420], [C, 434]], 6);
  OM('o_intestino_grueso', '#D48C78', [thick(colon, 13)]);
  O('o_apendice', '#C06E5C', capsule(C - 47, 400, C - 41, 414, 5));
  O('o_intestino_delgado', '#EEB999', blob([[C - 38, 352], [C - 20, 345], [C, 348], [C + 20, 345], [C + 38, 352], [C + 40, 376], [C + 30, 394], [C + 8, 400], [C - 12, 400], [C - 34, 394], [C - 41, 376]], 6));
  for (let i = 0; i < 4; i++) s.push({ kind: 'deco', d: absLine(spline([[C - 32, 356 + i * 11], [C - 16, 362 + i * 11], [C, 355 + i * 11], [C + 16, 362 + i * 11], [C + 32, 356 + i * 11]], 5)) });
  // riñones: están detrás de los demás órganos; se dibujan encima, translúcidos
  for (const g of [-1, 1]) O(g < 0 ? 'o_rinon_der' : 'o_rinon_izq', '#A85A50', blob([[C + g * 31, 314], [C + g * 41, 311], [C + g * 48, 322], [C + g * 45, 340], [C + g * 35, 344], [C + g * 35, 328]], 6), { alpha: 0.72, dashed: true });
  if (F) {
    for (const g of [-1, 1]) {
      s.push({ kind: 'deco', d: absLine(spline([[C + g * 8, 408], [C + g * 18, 402], [C + g * 28, 406]], 4)) });
      O(g < 0 ? 'o_ovario_der' : 'o_ovario_izq', '#E9A6BA', ellipse(C + g * 31, 409, 6.5, 4.6, 16));
    }
    O('o_utero', '#D6708B', blob([[C - 11, 404], [C, 401], [C + 11, 404], [C + 8, 416], [C + 4, 426], [C, 430], [C - 4, 426], [C - 8, 416]], 5));
    O('o_vejiga', '#E5CE69', blob([[C - 9, 430], [C, 427], [C + 9, 430], [C + 8, 440], [C, 444], [C - 8, 440]], 5));
  } else {
    O('o_vejiga', '#E5CE69', blob([[C - 12, 414], [C, 410], [C + 12, 414], [C + 11, 428], [C, 433], [C - 11, 428]], 5));
    O('o_prostata', '#B98A66', ellipse(C, 438, 6, 4.5, 16));
    for (const g of [-1, 1]) O(g < 0 ? 'o_testiculo_der' : 'o_testiculo_izq', '#D8B196', ellipse(C + g * 7, 462, 5.5, 7.5, 16));
  }
  return s;
}

// ───────────────────────── Abdomen (9 regiones) ─────────────────────────
export function abdomenView(sex) {
  const f = frontView(sex);
  const keep = new Set(['pecho_izq', 'pecho_der', 'esternon', 'ingle_izq', 'ingle_der', 'pubis']);
  const shapes = f.shapes.filter((s) => s.kind !== 'region' || s.id.startsWith('abd_') || keep.has(s.id) || /^(muslo|brazo|codo|antebrazo|hombro)/.test(s.id));
  const col = f.p.F ? 21 : 24;
  const rowsY = [298, 338, 381];
  const names = [['Hipocondrio', 'derecho'], ['Epigastrio', ''], ['Hipocondrio', 'izquierdo'], ['Flanco', 'derecho'], ['Mesogastrio', ''], ['Flanco', 'izquierdo'], ['Fosa ilíaca', 'derecha'], ['Hipogastrio', ''], ['Fosa ilíaca', 'izquierda']];
  const texts = [];
  names.forEach(([a, b], i) => {
    const c = i % 3, r = Math.floor(i / 3);
    const xs = [CX - (col + f.p.torso(rowsY[r])) / 2, CX, CX + (col + f.p.torso(rowsY[r])) / 2];
    texts.push({ x: xs[c], y: rowsY[r] - (b ? 2.5 : c === 1 && r === 1 ? 9 : 0), t: a, size: 5.4 });
    if (b) texts.push({ x: xs[c], y: rowsY[r] + 4, t: b, size: 5.4 });
  });
  return { shapes, texts };
}

// ───────────────────────── Mano (derecha, vista dorsal) ─────────────────────────
export function handView() {
  const s = [];
  const texts = [];
  const R = (id, pts) => s.push({ id, kind: 'region', d: absPath(pts) });
  const B = (id, pts) => s.push({ id, kind: 'bone', d: absPath(pts) });
  // dedo: [clave, nombre, base MCP (x,y), ángulo en grados (0 = hacia arriba, + = a la derecha), longitudes prox/med/dist, grosor piel]
  const fingers = [
    ['indice', 'Índice', 166, 318, -7, [66, 40, 30], 34],
    ['medio', 'Medio', 201, 306, 0, [72, 46, 32], 35],
    ['anular', 'Anular', 235, 316, 6, [66, 42, 31], 33],
    ['menique', 'Meñique', 265, 340, 13, [50, 30, 27], 29],
  ];
  const metaBase = { indice: [172, 432], medio: [198, 428], anular: [222, 432], menique: [243, 440] };
  const dir = (deg) => [Math.sin((deg * Math.PI) / 180), -Math.cos((deg * Math.PI) / 180)];
  // antebrazo y muñeca (piel)
  R('m_muneca', roundRect(140, 478, 124, 96, 18, 4));
  // palma / dorso
  R('m_dorso', blob([[132, 470], [118, 420], [128, 360], [150, 318], [185, 300], [222, 300], [256, 318], [280, 346], [284, 400], [272, 462], [250, 486], [160, 488]], 6));
  // pulgar
  const th = { base: [128, 418], a: -42, lens: [56, 42], w: 40 };
  const [tdx, tdy] = dir(th.a);
  const thMcp = [th.base[0] + tdx * 4, th.base[1] + tdy * 4];
  const thIp = [thMcp[0] + tdx * th.lens[0], thMcp[1] + tdy * th.lens[0]];
  const thTip = [thIp[0] + tdx * th.lens[1], thIp[1] + tdy * th.lens[1]];
  R('m_dedo_pulgar', capsule(thMcp[0] + tdx * 6, thMcp[1] + tdy * 6, thTip[0], thTip[1], th.w));
  const tips = {};
  for (const [key, , bx, by, ang, lens, w] of fingers) {
    const [dx, dy] = dir(ang);
    const total = lens[0] + lens[1] + lens[2];
    R(`m_dedo_${key}`, capsule(bx + dx * 8, by + dy * 8, bx + dx * total, by + dy * total, w));
    tips[key] = [bx + dx * (total + w / 2 + 9), by + dy * (total + w / 2 + 9)];
  }
  // huesos del antebrazo
  B('m_radio', bone(170, 580, 172, 508, 30, 16, 21));
  B('m_cubito', bone(236, 580, 234, 512, 22, 12, 14));
  // carpo: fila proximal (junto al antebrazo) y distal
  B('m_escafoides', blob([[150, 482], [164, 472], [178, 478], [176, 492], [160, 496]], 5));
  B('m_semilunar', blob([[182, 478], [198, 473], [208, 482], [202, 494], [186, 493]], 5));
  B('m_piramidal', blob([[212, 480], [228, 474], [240, 484], [232, 496], [216, 494]], 5));
  B('m_pisiforme', ellipse(244, 474, 7.5, 7, 14));
  B('m_trapecio', blob([[142, 462], [152, 448], [166, 452], [166, 468], [152, 474]], 5));
  B('m_trapezoide', blob([[170, 450], [184, 446], [188, 460], [180, 470], [170, 466]], 5));
  B('m_grande', blob([[192, 446], [208, 444], [212, 460], [206, 472], [192, 468]], 5));
  B('m_ganchoso', blob([[216, 446], [236, 448], [244, 460], [234, 470], [216, 468]], 5));
  // metacarpianos y falanges
  const ord = { indice: 2, medio: 3, anular: 4, menique: 5 };
  for (const [key, , bx, by, ang, lens] of fingers) {
    const [dx, dy] = dir(ang);
    const mb = metaBase[key];
    B(`m_metacarpiano_${ord[key]}`, bone(mb[0], mb[1], bx, by + 6, 9, 8, 8.5));
    let px = bx + dx * 8, py = by + dy * 8;
    const names = ['proximal', 'media', 'distal'];
    lens.forEach((len, i) => {
      const l = i === 0 ? len - 8 : len;
      const ex = px + dx * (l - 5), ey = py + dy * (l - 5);
      B(`m_falange_${names[i]}_${key}`, bone(px, py, ex, ey, 8 - i * 0.8, 7.2 - i * 0.6, i === 2 ? 5 : 6.4 - i * 0.6));
      px = ex + dx * 5; py = ey + dy * 5;
    });
  }
  B('m_metacarpiano_1', bone(150, 446, thMcp[0] + tdx * 2, thMcp[1] + tdy * 2 + 6, 10, 8.5, 9));
  B('m_falange_proximal_pulgar', bone(thMcp[0] + tdx * 12, thMcp[1] + tdy * 12, thIp[0] - tdx * 3, thIp[1] - tdy * 3, 8.6, 7.6, 7));
  B('m_falange_distal_pulgar', bone(thIp[0] + tdx * 4, thIp[1] + tdy * 4, thTip[0] - tdx * 10, thTip[1] - tdy * 10, 7.6, 6.8, 5.6));
  for (const [key, name] of fingers) texts.push({ x: tips[key][0], y: tips[key][1], t: name, size: 11 });
  texts.push({ x: thTip[0] + 2, y: thTip[1] - 30, t: 'Pulgar', size: 11 });
  return { shapes: s, texts };
}

// ───────────────────────── Pie (derecho, visto desde arriba) ─────────────────────────
export function footView() {
  const s = [];
  const texts = [];
  const R = (id, pts) => s.push({ id, kind: 'region', d: absPath(pts) });
  const B = (id, pts) => s.push({ id, kind: 'bone', d: absPath(pts) });
  // Pie derecho visto desde arriba, dedos hacia arriba: el dedo gordo queda a la izquierda.
  R('p_talon', blob([[160, 560], [150, 500], [162, 452], [246, 452], [256, 500], [248, 560], [204, 586]], 6));
  R('p_empeine', blob([[150, 470], [128, 400], [118, 320], [128, 256], [170, 232], [222, 238], [262, 262], [284, 310], [286, 380], [270, 440], [256, 474]], 6));
  const toes = [
    ['gordo', 'Dedo gordo', 142, 248, -4, [44, 34], 44],
    ['segundo', '2.º', 186, 232, 0, [30, 20, 16], 27],
    ['tercero', '3.º', 218, 240, 4, [27, 18, 15], 25],
    ['cuarto', '4.º', 246, 256, 8, [24, 16, 14], 24],
    ['quinto', '5.º', 270, 280, 13, [20, 12, 12], 23],
  ];
  const dir = (deg) => [Math.sin((deg * Math.PI) / 180), -Math.cos((deg * Math.PI) / 180)];
  for (const [key, name, bx, by, ang, lens, w] of toes) {
    const [dx, dy] = dir(ang);
    const total = lens.reduce((a, b) => a + b, 0);
    R(`p_dedo_${key}`, capsule(bx + dx * 6, by + dy * 6, bx + dx * total, by + dy * total, w));
    texts.push({ x: bx + dx * (total + w / 2 + 10), y: by + dy * (total + w / 2 + 8), t: name, size: 10.5 });
  }
  // tarso
  B('p_calcaneo', blob([[176, 566], [170, 520], [182, 488], [232, 486], [242, 520], [234, 566], [206, 578]], 6));
  B('p_astragalo', blob([[172, 482], [174, 452], [200, 440], [226, 448], [230, 478], [202, 488]], 6));
  B('p_navicular', blob([[152, 436], [162, 416], [198, 414], [204, 434], [178, 442]], 5));
  B('p_cuboides', blob([[212, 440], [214, 404], [250, 398], [262, 430], [240, 446]], 5));
  B('p_cuneiforme_medial', blob([[140, 408], [142, 380], [162, 378], [166, 408], [152, 414]], 5));
  B('p_cuneiforme_intermedio', blob([[170, 408], [168, 384], [186, 382], [190, 408]], 5));
  B('p_cuneiforme_lateral', blob([[194, 408], [190, 382], [210, 380], [212, 402]], 5));
  // metatarsianos y falanges
  const mbase = { gordo: [150, 374], segundo: [177, 378], tercero: [200, 376], cuarto: [226, 392], quinto: [248, 396] };
  const ord = { gordo: 1, segundo: 2, tercero: 3, cuarto: 4, quinto: 5 };
  for (const [key, , bx, by, ang, lens] of toes) {
    const [dx, dy] = dir(ang);
    const mb = mbase[key];
    const fat = key === 'gordo';
    B(`p_metatarsiano_${ord[key]}`, bone(mb[0], mb[1], bx - dx * 4, by - dy * 4 + 8, fat ? 15 : 8.5, fat ? 11 : 7.6, fat ? 12 : 7.8));
    let px = bx + dx * 8, py = by + dy * 8;
    const names = lens.length === 2 ? ['proximal', 'distal'] : ['proximal', 'media', 'distal'];
    lens.forEach((len, i) => {
      const l = i === 0 ? len - 8 : len;
      const ex = px + dx * (l - 4), ey = py + dy * (l - 4);
      const w = fat ? 13 - i * 2 : 7 - i * 0.8;
      B(`p_falange_${names[i]}_${key}`, bone(px, py, ex, ey, w, w * 0.78, i === lens.length - 1 ? w * 0.6 : w * 0.72));
      px = ex + dx * 4; py = ey + dy * 4;
    });
  }
  return { shapes: s, texts };
}
