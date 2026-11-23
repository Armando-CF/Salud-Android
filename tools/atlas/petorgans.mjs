// Vista de órganos de perro y gato (lateral), sobre la misma silueta de pets.mjs.
import { blob, spline, ellipse, thick, absPath, absLine } from './geom.mjs';
import { tube, tubePart, legEnd } from './pets.mjs';

/** Caja del tronco por especie: los órganos se colocan en coordenadas normalizadas (u, v) dentro de ella. */
const TORSO = {
  perro: { x0: 180, x1: 426, y0: 121, y1: 259, brain: [[108, 110], [122, 101], [142, 103], [150, 114], [140, 124], [118, 124]], throat: [[128, 150], [150, 160], [176, 166]] },
  gato: { x0: 182, x1: 421, y0: 140, y1: 259, brain: [[88, 136], [104, 126], [128, 128], [140, 142], [128, 154], [100, 152]], throat: [[112, 180], [140, 184], [174, 186]] },
};

export function petOrgansView(sp) {
  const s = [];
  const T = TORSO[sp.species];
  const P = ([u, v]) => [T.x0 + u * (T.x1 - T.x0), T.y0 + v * (T.y1 - T.y0)];
  const id = (name) => `pet_o_${name}_${sp.species}`;
  const skin = (pts, alpha = 1) => s.push({ kind: 'skin', d: absPath(pts), alpha });
  const outline = (pts) => s.push({ kind: 'deco', d: absPath(pts) });
  const O = (name, color, pts, extra = {}) => s.push({ id: id(name), kind: 'organ', color, d: absPath(pts), ...extra });

  // Silueta de fondo (no tocable)
  const front = tube(sp.frontLeg), hind = tube(sp.hindLeg);
  skin(legEnd(front, 0.3, sp.frontPaw, sp.ground, sp.pawW, sp.pawH).map(([x, y]) => [x + sp.farFrontDx, y]), 0.4);
  skin(legEnd(hind, 0, sp.hindPaw, sp.ground, sp.pawW, sp.pawH).map(([x, y]) => [x + sp.farHindDx, y]), 0.4);
  if (sp.farEar) skin(blob(sp.farEar, 6), 0.5);
  // contorno nítido: primero las piezas con borde y encima los rellenos, para que solo se vea la silueta exterior
  const pieces = [
    tubePart(tube(sp.tail), 0, 1, true),
    legEnd(front, 0, sp.frontPaw, sp.ground, sp.pawW, sp.pawH),
    legEnd(hind, 0, sp.hindPaw, sp.ground, sp.pawW, sp.pawH),
    blob(sp.thigh, 8),
    blob(sp.ear, 7),
    blob(sp.body, 10),
  ];
  for (const pc of pieces) outline(pc);
  for (const pc of pieces) skin(pc);
  s.push({ kind: 'deco', d: absPath(ellipse(sp.eye[0] - 0.6, sp.eye[1], sp.eye[2] * 0.5, sp.eye[3] * 0.62, 12)), fill: 'ink' });
  s.push({ kind: 'deco', d: absPath(blob(sp.nose, 6)), fill: 'ink' });

  // Órganos
  O('cerebro', '#E7AEB6', blob(T.brain, 6));
  const tr = spline([...T.throat, P([0.1, 0.34]), P([0.2, 0.36])], 6);
  O('traquea', '#BCD3DF', thick(tr, 7));
  O('rinones', '#A85A50', ellipse(...P([0.66, 0.2]), (T.x1 - T.x0) * 0.052, (T.y1 - T.y0) * 0.085, 18, -0.25));
  O('pulmones', '#EBA3AD', blob([[0.13, 0.2], [0.28, 0.11], [0.45, 0.15], [0.5, 0.36], [0.44, 0.6], [0.3, 0.56], [0.2, 0.5], [0.12, 0.4]].map(P), 7));
  O('corazon', '#C7434C', blob([[0.13, 0.62], [0.2, 0.52], [0.29, 0.56], [0.31, 0.74], [0.24, 0.9], [0.16, 0.84]].map(P), 7));
  s.push({ kind: 'deco', d: absLine(spline([[0.5, 0.06], [0.47, 0.4], [0.4, 0.7], [0.38, 0.96]].map(P), 6)), dashed: true });
  O('higado', '#9A4B3D', blob([[0.5, 0.5], [0.57, 0.44], [0.61, 0.6], [0.58, 0.82], [0.48, 0.92], [0.43, 0.76]].map(P), 7));
  O('estomago', '#E2A276', blob([[0.6, 0.4], [0.69, 0.37], [0.73, 0.5], [0.69, 0.66], [0.62, 0.66]].map(P), 7));
  O('intestinos', '#EEB999', blob([[0.63, 0.74], [0.7, 0.66], [0.76, 0.46], [0.85, 0.36], [0.92, 0.44], [0.91, 0.64], [0.82, 0.8], [0.7, 0.83]].map(P), 7));
  for (let i = 0; i < 3; i++) s.push({ kind: 'deco', d: absLine(spline([[0.69 + i * 0.03, 0.76 - i * 0.015], [0.75 + i * 0.03, 0.68 - i * 0.07], [0.79 + i * 0.03, 0.56 - i * 0.05], [0.85 + i * 0.02, 0.5 - i * 0.03]].map(P), 5)) });
  O('bazo', '#8D5C8C', blob([[0.66, 0.6], [0.705, 0.57], [0.72, 0.7], [0.675, 0.77]].map(P), 6));
  O('vejiga', '#E5CE69', ellipse(...P([0.935, 0.68]), (T.x1 - T.x0) * 0.034, (T.y1 - T.y0) * 0.075, 16, 0.3));
  return s;
}
