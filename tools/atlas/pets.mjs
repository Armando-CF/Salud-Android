// Atlas de mascotas: perro y gato en vista lateral (cabeza a la izquierda).
// Todo en coordenadas absolutas del lienzo. Las regiones del tronco se obtienen recortando la silueta
// con celdas convexas; las patas y la cola son "tubos" de grosor variable cortados en tramos.
import { blob, spline, ellipse, absPath, absLine } from './geom.mjs';

/** Recorte de Sutherland–Hodgman: polígono cualquiera ∩ polígono convexo. */
export function clip(subject, convex) {
  let area = 0;
  for (let i = 0; i < convex.length; i++) { const a = convex[i], b = convex[(i + 1) % convex.length]; area += a[0] * b[1] - b[0] * a[1]; }
  const sign = area >= 0 ? 1 : -1;
  let out = subject;
  for (let i = 0; i < convex.length && out.length; i++) {
    const a = convex[i], b = convex[(i + 1) % convex.length];
    const side = (p) => (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0]);
    const inside = (p) => sign * side(p) >= 0;
    const cut = (p, q) => {
      const d1 = side(p), d2 = side(q);
      const t = d1 / (d1 - d2);
      return [p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t];
    };
    const res = [];
    for (let j = 0; j < out.length; j++) {
      const p = out[j], q = out[(j + 1) % out.length];
      const pin = inside(p), qin = inside(q);
      if (pin && qin) res.push(q);
      else if (pin && !qin) res.push(cut(p, q));
      else if (!pin && qin) { res.push(cut(p, q)); res.push(q); }
    }
    out = res;
  }
  return out;
}

/** Tubo de grosor variable: centro por [x, y, semiancho]. Devuelve muestras {l, r, c}. */
export function tube(ctrl, samples = 8) {
  const line = spline(ctrl.map(([x, y]) => [x, y]), samples);
  const n = line.length;
  const segs = ctrl.length - 1;
  const out = [];
  for (let i = 0; i < n; i++) {
    const u = Math.min(segs - 1e-9, (i / (n - 1)) * segs);
    const k = Math.floor(u), f = u - k;
    const s = f * f * (3 - 2 * f);
    const w = ctrl[k][2] + (ctrl[k + 1][2] - ctrl[k][2]) * s;
    const a = line[Math.max(0, i - 1)], b = line[Math.min(n - 1, i + 1)];
    let dx = b[0] - a[0], dy = b[1] - a[1];
    const len = Math.hypot(dx, dy) || 1;
    dx /= len; dy /= len;
    out.push({ l: [line[i][0] - dy * w, line[i][1] + dx * w], r: [line[i][0] + dy * w, line[i][1] - dx * w], c: line[i] });
  }
  return out;
}

/** Tramo del tubo entre las fracciones t0 y t1 (0..1). Con `cap` redondea el extremo final. */
export function tubePart(tb, t0, t1, cap = false) {
  const n = tb.length - 1;
  const i0 = Math.round(t0 * n), i1 = Math.round(t1 * n);
  const left = [], right = [];
  for (let i = i0; i <= i1; i++) { left.push(tb[i].l); right.push(tb[i].r); }
  const end = [];
  if (cap) {
    const a = tb[i1].l, c = tb[i1].c;
    const r = Math.hypot(a[0] - c[0], a[1] - c[1]);
    const a0 = Math.atan2(a[1] - c[1], a[0] - c[0]);
    for (let k = 1; k < 8; k++) { const ang = a0 - (Math.PI * k) / 8; end.push([c[0] + Math.cos(ang) * r, c[1] + Math.sin(ang) * r]); }
  }
  return [...left, ...end, ...right.reverse()];
}

const shift = (pts, dx, dy = 0) => pts.map(([x, y]) => [x + dx, y + dy]);

/**
 * Último tramo de una pata (desde la fracción t0) unido a la mano/pie apoyado en el suelo,
 * como un solo contorno. Los dedos apuntan a la izquierda.
 */
export function legEnd(tb, t0, cx, gy, w, h) {
  const n = tb.length - 1;
  const i0 = Math.round(t0 * n);
  const front = [], rear = [];
  for (let i = i0; i <= n; i++) { front.push(tb[i].l); rear.push(tb[i].r); }
  const a = front[front.length - 1], b = rear[rear.length - 1];
  const pawPts = spline([
    a, [cx - w * 0.3, gy - h * 0.98], [cx - w * 0.52, gy - h * 0.55], [cx - w * 0.5, gy - h * 0.08], [cx - w * 0.36, gy],
    [cx + w * 0.36, gy], [cx + w * 0.5, gy - h * 0.3], [cx + w * 0.42, gy - h * 0.8], b,
  ], 4);
  return [...front, ...pawPts.slice(1, -1), ...rear.reverse()];
}

/**
 * Construye la vista lateral a partir de una especificación (ver DOG y CAT).
 * Los ids llevan el sufijo de especie: pet_<zona>_<especie>.
 */
export function petSideView(sp) {
  const s = [];
  const id = (name) => `pet_${name}_${sp.species}`;
  const R = (name, ...polys) => s.push({ id: id(name), kind: 'region', d: polys.map(absPath).join('') });
  const skin = (pts, alpha = 0.55) => s.push({ kind: 'skin', d: absPath(pts), alpha, outline: true });
  const deco = (pts, extra = {}) => s.push({ kind: 'deco', d: absLine(pts), ...extra });
  const ink = (pts) => s.push({ kind: 'deco', d: absPath(pts), fill: 'ink' });

  const body = blob(sp.body, 10);
  const front = tube(sp.frontLeg);
  const hind = tube(sp.hindLeg);

  // 1) Patas y oreja del lado lejano (tenues, no tocables)
  skin(shift(legEnd(front, sp.farFrontFrom ?? 0.3, sp.frontPaw, sp.ground, sp.pawW, sp.pawH), sp.farFrontDx, 0));
  skin(shift(legEnd(hind, sp.farHindFrom ?? 0, sp.hindPaw, sp.ground, sp.pawW, sp.pawH), sp.farHindDx, 0));
  if (sp.farEar) skin(blob(sp.farEar, 6), 0.75);

  // 2) Cola (detrás del cuerpo)
  R('cola', tubePart(tube(sp.tail), 0, 1, true));

  // 3) Tronco, cuello y cabeza: silueta ∩ celdas convexas
  for (const [name, cell] of Object.entries(sp.cells)) {
    if (name === 'grupa_baja') continue;
    const polys = [clip(body, cell)];
    if (name === 'grupa') polys.push(clip(body, sp.cells.grupa_baja));
    R(name, ...polys.filter((q) => q.length >= 3));
  }
  // 4) Patas cercanas por tramos
  const f = sp.frontParts, h = sp.hindParts;
  R('brazo', tubePart(front, 0, f[0]));
  R('codo', tubePart(front, f[0], f[1]));
  R('antebrazo', tubePart(front, f[1], f[2]));
  R('carpo', tubePart(front, f[2], f[3]));
  R('mano', legEnd(front, f[3], sp.frontPaw, sp.ground, sp.pawW, sp.pawH));
  R('rodilla', tubePart(hind, 0, h[0]));
  R('pierna', tubePart(hind, h[0], h[1]));
  R('corvejon', tubePart(hind, h[1], h[2]));
  R('pie', legEnd(hind, h[2], sp.hindPaw, sp.ground, sp.pawW, sp.pawH));

  // 5) Hombro y muslo sobre el tronco
  R('hombro', blob(sp.shoulder, 8));
  R('muslo', blob(sp.thigh, 8));
  // zona anal y genital: franja posterior bajo la cola (encima del muslo)
  R('genital', clip(body, sp.genitalCell));

  // 6) Cabeza: oreja, ojo, nariz y boca
  R('oreja', blob(sp.ear, 7));
  R('ojo', ellipse(sp.eye[0], sp.eye[1], sp.eye[2], sp.eye[3], 16));
  ink(ellipse(sp.eye[0] - 0.6, sp.eye[1], sp.eye[2] * 0.5, sp.eye[3] * 0.62, 12));
  ink(blob(sp.nose, 6));
  if (sp.mouth) deco(spline(sp.mouth, 6));
  if (sp.whiskers) for (const w of sp.whiskers) deco(w);
  // separación de los dedos
  for (const cx of [sp.frontPaw, sp.hindPaw]) {
    for (const k of [-0.2, 0.06]) deco([[cx + sp.pawW * k, sp.ground - sp.pawH * 0.6], [cx + sp.pawW * (k - 0.05), sp.ground - 1]]);
  }
  return s;
}

// ───────────────────────── Perro ─────────────────────────
export const DOG = {
  species: 'perro',
  ground: 370, pawW: 40, pawH: 17,
  body: [
    [44, 151], [50, 138], [72, 131], [94, 124], [102, 108], [124, 94], [150, 98], [180, 108], [214, 114], [270, 122], [330, 123],
    [382, 127], [414, 139], [428, 160], [431, 188], [425, 214], [416, 232], [380, 238], [345, 240], [300, 253], [260, 262],
    [215, 262], [186, 244], [172, 216], [150, 197], [124, 187], [108, 181], [84, 177], [60, 171], [47, 163],
  ],
  cells: {
    hocico: [[20, 70], [96, 80], [101.5, 162], [20, 162]],
    boca: [[20, 162], [101.5, 162], [104, 200], [20, 200]],
    cabeza: [[96, 80], [158, 80], [118, 200], [104, 200]],
    cuello: [[158, 80], [221.4, 80], [118, 290], [118, 200]],
    lomo: [[221.4, 80], [322, 100], [309.5, 166], [178.2, 168]],
    lumbar: [[322, 100], [376, 100], [373.4, 164], [309.5, 166]],
    grupa: [[376, 100], [450, 100], [450, 165], [373.4, 164]],
    pecho: [[178.2, 168], [309.5, 166], [286, 290], [118, 290]],
    flanco: [[309.5, 166], [373.4, 164], [372, 200], [301.5, 208]],
    abdomen: [[301.5, 208], [372, 200], [368, 300], [286, 290]],
    grupa_baja: [[373.4, 164], [450, 165], [450, 300], [368, 300]],
  },
  genitalCell: [[411, 152], [450, 152], [450, 236], [416, 236]],
  frontLeg: [[196, 212, 17], [210, 236, 16], [222, 257, 14], [219, 290, 10.5], [215, 324, 8.5], [213, 344, 8], [209, 357, 8.5]],
  frontParts: [0.27, 0.42, 0.74, 0.86],
  frontPaw: 206,
  hindLeg: [[378, 240, 20], [374, 259, 17], [392, 284, 12], [414, 305, 9], [410, 332, 7.5], [404, 356, 8]],
  hindParts: [0.22, 0.55, 0.72],
  hindPaw: 402,
  farFrontDx: 24, farHindDx: 22, farHindFrom: 0,
  shoulder: [[213, 126], [230, 154], [227, 190], [207, 222], [186, 214], [183, 190], [195, 154]],
  thigh: [[392, 152], [419, 172], [427, 202], [418, 234], [399, 263], [369, 263], [362, 236], [366, 202], [376, 172]],
  tail: [[410, 146, 11], [440, 151, 9.5], [468, 168, 8], [488, 198, 6.5], [498, 228, 3.6]],
  ear: [[126, 102], [144, 98], [158, 112], [160, 138], [150, 156], [138, 150], [130, 128]],
  eye: [110, 120, 5, 4.2],
  nose: [[42.5, 149], [45.5, 142.5], [52, 141.5], [53, 148], [48, 152.5]],
};

// ───────────────────────── Gato ─────────────────────────
export const CAT = {
  species: 'gato',
  ground: 342, pawW: 32, pawH: 14,
  body: [
    [53.6, 172], [63.2, 156.4], [77.6, 134.8], [104, 118], [137.6, 125.2], [162, 146], [200, 141], [270, 138],
    [330, 138], [380, 142], [408, 153], [421, 174], [424, 200], [416, 228], [382, 244], [345, 250], [300, 258], [255, 262],
    [214, 258], [190, 242], [176, 224], [152, 212], [120.8, 210.4], [94.4, 208], [70.4, 198.4], [56, 186.4],
  ],
  cells: {
    hocico: [[0, 80], [77.8, 80], [78.8, 176.8], [0, 176.8]],
    boca: [[0, 176.8], [78.8, 176.8], [79.8, 240], [0, 240]],
    cabeza: [[77.8, 80], [152, 80], [148, 240], [79.8, 240]],
    cuello: [[152, 80], [212.2, 80], [147.5, 290], [148, 240]],
    lomo: [[212.2, 80], [322, 110], [311, 180], [181.4, 180]],
    lumbar: [[322, 110], [376, 110], [373.2, 180], [311, 180]],
    grupa: [[376, 110], [450, 110], [450, 180], [373.2, 180]],
    pecho: [[181.4, 180], [311, 180], [292, 300], [147.5, 290]],
    flanco: [[311, 180], [373.2, 180], [372, 212], [305.3, 216]],
    abdomen: [[305.3, 216], [372, 212], [368.5, 300], [292, 300]],
    grupa_baja: [[373.2, 180], [450, 180], [450, 300], [368.5, 300]],
  },
  genitalCell: [[406, 164], [450, 164], [450, 238], [410, 238]],
  frontLeg: [[200, 224, 14], [210, 244, 13], [217, 262, 11], [214, 286, 8.5], [211, 308, 7], [209, 322, 6.8], [206, 331, 7]],
  frontParts: [0.27, 0.42, 0.74, 0.86],
  frontPaw: 203,
  hindLeg: [[378, 246, 17], [375, 262, 14.5], [391, 280, 10.5], [409, 296, 8], [405, 314, 6.8], [400, 330, 7]],
  hindParts: [0.22, 0.55, 0.72],
  hindPaw: 398,
  farFrontDx: 19, farHindDx: 19, farHindFrom: 0,
  shoulder: [[206, 152], [224, 174], [221, 204], [206, 230], [189, 224], [186, 200], [193, 172]],
  thigh: [[390, 164], [414, 182], [421, 208], [413, 238], [397, 265], [370, 267], [362, 243], [366, 212], [376, 184]],
  tail: [[404, 160, 9], [438, 168, 8], [470, 162, 7.4], [493, 140, 6.8], [502, 110, 6.2], [494, 84, 4.8]],
  ear: [[94.4, 126.4], [101.6, 100], [114.8, 78.4], [125.6, 98.8], [131.6, 122.8], [113.6, 118]],
  farEar: [[75.2, 137.2], [81.2, 109.6], [92, 88], [101.6, 104.8], [104, 120.4]],
  eye: [86, 149.2, 7, 6.4],
  nose: [[51.3, 171.1], [54.8, 167.6], [59.1, 170.2], [56.5, 174.5], [52.2, 175.4]],
  whiskers: [
    [[65.6, 180.4], [29.6, 170.8]], [[65.6, 184], [27.2, 184]], [[66.8, 187.6], [32, 197.2]],
  ],
};
