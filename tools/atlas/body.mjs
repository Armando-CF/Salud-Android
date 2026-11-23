// Figura corporal (frente y espalda) para hombre y mujer.
import { curve, konst, edge, band, mirror, toPath, ellipse, absPath, absLine, spline, CX } from './geom.mjs';

/** Parámetros de silueta por sexo (media figura: x = distancia al eje). */
export function params(sex) {
  const F = sex === 'F';
  const sh = F ? 57 : 66;          // semiancho del torso a la altura del hombro
  const ax = F ? -10 : 0;          // desplazamiento de los brazos
  const at = F ? -2.5 : 0;         // brazos más delgados
  return {
    F, sh,
    head: curve([[30, 0], [32, 14], [38, 26], [50, 35], [68, 39.5], [90, 40], [108, 37], [122, 30], [133, 19], [140, 8], [142, 0]]),
    neck: F ? 19 : 23,
    // línea del trapecio: del cuello al hombro
    slope: curve([[0, 0]]), // se reemplaza abajo
    torso: F
      ? curve([[172, 57], [218, 60], [240, 62], [268, 57], [296, 51], [322, 48.5], [350, 55], [380, 67], [400, 73]])
      : curve([[174, 66], [218, 69], [240, 71], [280, 67], [322, 60.5], [350, 61], [380, 66], [400, 70]]),
    armOuter: curve([[174, sh], [176, sh + 7], [180, sh + 14], [186, sh + 20.5], [195, sh + 26.5 + at], [208, sh + 31 + at], [226, sh + 34.5 + at], [262, sh + 35 + at], [300, sh + 35.5 + at], [332, sh + 37.5 + at], [372, sh + 43 + at], [412, sh + 47 + at], [450, sh + 49 + at]]),
    armInner: curve([[214, sh + 3], [250, sh + 6 - at], [300, sh + 10 - at], [335, sh + 14 - at], [362, sh + 18 - at], [410, sh + 26 - at * 0.6], [450, sh + 34 - at * 0.4]]),
    legOuter: F
      ? curve([[400, 73], [432, 78], [470, 77], [520, 70], [570, 61], [600, 57], [622, 56.5], [650, 59.5], [690, 61], [740, 52], [785, 44.5], [808, 44]])
      : curve([[400, 70], [436, 73.5], [480, 72], [530, 67], [575, 60.5], [600, 57.5], [622, 57], [650, 60], [690, 62], [740, 52.5], [785, 45], [808, 44.5]]),
    legInner: curve([[448, 3.5], [470, 4.5], [520, 9], [570, 16.5], [600, 20], [622, 20.5], [650, 17.5], [690, 14.5], [740, 20], [785, 24.5], [808, 25]]),
    hipY: 400,
    crotchY: 448,
  };
}

const Y = { headTop: 30, chin: 142, neckTop: 134, neckBase: 158, shoulder: 174, armpit: 218, chestEnd: 278, abd1: 318, abd2: 362, pelvis: 400 };

/** y de la línea del trapecio para |x| entre el cuello y el hombro. */
function slopeY(p, x) {
  const t = Math.min(1, Math.max(0, (x - p.neck) / (p.sh - p.neck)));
  const e = t * t * (3 - 2 * t) * 0.35 + t * 0.65;
  return Y.neckBase - 2 + (Y.shoulder - Y.neckBase + 2) * e;
}

function slopePts(p, x0, x1, n = 10) {
  const out = [];
  for (let i = 0; i <= n; i++) {
    const x = x0 + ((x1 - x0) * i) / n;
    out.push([x, slopeY(p, x)]);
  }
  return out;
}

/** Línea inguinal (V) del borde exterior de la cadera a la entrepierna. */
function vLine(p, n = 12) {
  const out = [];
  const x0 = p.torso(Y.pelvis), y0 = Y.pelvis, x1 = 3.5, y1 = p.crotchY;
  for (let i = 0; i <= n; i++) {
    const t = i / n;
    // ligera curva hacia abajo
    out.push([x0 + (x1 - x0) * t, y0 + (y1 - y0) * (t * 0.8 + 0.2 * t * t)]);
  }
  return out;
}

/** Extremidades (igual en frente y espalda; cambian los nombres). Devuelve polígonos en media figura. */
function limbs(p) {
  const armTop = [...edge(p.armOuter, Y.shoulder, 222, 1.5)];
  const hombro = [[p.sh, Y.shoulder], ...armTop.slice(1), [p.armInner(222), 222], ...edge((y) => p.sh + (y - Y.shoulder) * 0.07, Y.armpit, Y.shoulder, 6)];
  const brazo = band(p.armInner, p.armOuter, 222, 318);
  const codo = band(p.armInner, p.armOuter, 318, 352);
  const antebrazo = band(p.armInner, p.armOuter, 352, 436);
  const muneca = band(p.armInner, p.armOuter, 436, 456);
  // mano: silueta suave con el pulgar hacia adentro
  const wo = p.armOuter(456), wi = p.armInner(456);
  const mano = [[wi, 456], [wo, 456], ...spline([[wo, 456], [wo + 4, 470], [wo + 7.5, 490], [wo + 7, 510], [wo + 3.5, 525], [wo - 3, 533], [wo - 10, 532], [wi + 2, 520], [wi + 0.5, 504], [wi - 5, 498], [wi - 8.5, 486], [wi - 5, 470], [wi, 456]], 5).slice(1, -1)];
  const v = vLine(p);
  const musloFront = [...v, ...edge(p.legInner, p.crotchY + 2, 575), ...edge(p.legOuter, 575, Y.pelvis)];
  const rodilla = band(p.legInner, p.legOuter, 575, 640);
  const pierna = band(p.legInner, p.legOuter, 640, 778);
  const tobillo = band(p.legInner, p.legOuter, 778, 810);
  const fo = p.legOuter(810), fi = p.legInner(810);
  const pie = [[fi, 810], [fo, 810], ...spline([[fo, 810], [fo + 5, 824], [fo + 11.5, 840], [fo + 11, 851], [fo + 2, 856], [fi - 2, 856], [fi - 7.5, 850], [fi - 6.5, 838], [fi - 2.5, 823], [fi, 810]], 5).slice(1, -1)];
  return { hombro, brazo, codo, antebrazo, muneca, mano, musloFront, rodilla, pierna, tobillo, pie, v };
}

function headShapes(p) {
  const hw = p.head;
  return {
    upper: band((y) => -hw(y), hw, Y.headTop, 76, 2),
    mid: band((y) => -hw(y), hw, 76, 116, 2),
    lower: band((y) => -hw(y), hw, 116, Y.chin, 2),
    ear: ellipse(hw(92) + 3.5, 92, 5, 11, 16),
  };
}

/** Añade un par izquierdo/derecho. En vista frontal x>0 es el lado IZQUIERDO de la persona; en la posterior, el DERECHO. */
function pair(shapes, id, pts, positiveSide) {
  const other = positiveSide === 'izq' ? 'der' : 'izq';
  shapes.push({ id: `${id}_${positiveSide}`, kind: 'region', d: toPath(pts) });
  shapes.push({ id: `${id}_${other}`, kind: 'region', d: toPath(mirror(pts)) });
}

const single = (shapes, id, pts) => shapes.push({ id, kind: 'region', d: toPath(pts) });
const deco = (shapes, pts, extra = {}) => shapes.push({ kind: 'deco', d: absLine(pts.map(([x, y]) => [CX + x, y])), ...extra });

export function frontView(sex) {
  const p = params(sex);
  const s = [];
  const side = 'izq';
  const h = headShapes(p);
  single(s, 'cabeza', h.upper);
  single(s, 'cara', h.mid);
  single(s, 'mandibula', h.lower);
  pair(s, 'oido', h.ear, side);
  // cuello
  const nk = p.neck;
  single(s, 'cuello', [[-nk + 2, Y.neckTop], [nk - 2, Y.neckTop], [nk, 150], ...slopePts(p, nk, nk + 6, 3), [0, Y.neckBase + 3], ...slopePts(p, nk, nk + 6, 3).map(([x, y]) => [-x, y]).reverse(), [-nk, 150]]);
  // pecho: de la línea del trapecio a Y.chestEnd, sin la franja del esternón
  const st = 10;
  const pecho = [[st, Y.neckBase + 3], ...slopePts(p, nk + 6, p.sh, 8), ...edge(p.torso, Y.shoulder, Y.chestEnd), [st, Y.chestEnd]];
  pair(s, 'pecho', pecho, side);
  single(s, 'esternon', [[-st, Y.neckBase + 3], [0, Y.neckBase + 3], [st, Y.neckBase + 3], [st, Y.chestEnd], [-st, Y.chestEnd]]);
  // abdomen: 9 regiones
  const col = p.F ? 21 : 24;
  const rows = [[Y.chestEnd, Y.abd1], [Y.abd1, Y.abd2], [Y.abd2, Y.pelvis]];
  const lateral = ['abd_hipocondrio', 'abd_flanco', 'abd_fosa_iliaca'];
  const central = ['abd_epigastrio', 'abd_mesogastrio', 'abd_hipogastrio'];
  rows.forEach(([y0, y1], i) => {
    pair(s, lateral[i], band(konst(col), p.torso, y0, y1), side);
    single(s, central[i], [[-col, y0], [col, y0], [col, y1], [-col, y1]]);
  });
  // pelvis: ingles y pubis bajo la línea Y.pelvis hasta la V
  const L = limbs(p);
  const v = L.v;
  const px = 20;
  // punto de la V donde x = px
  const vAt = (x) => { for (let i = 0; i < v.length - 1; i++) if (v[i][0] >= x && v[i + 1][0] <= x) { const t = (v[i][0] - x) / (v[i][0] - v[i + 1][0]); return v[i][1] + (v[i + 1][1] - v[i][1]) * t; } return p.crotchY; };
  const vOuter = v.filter(([x]) => x > px);
  const vInner = v.filter(([x]) => x < px);
  pair(s, 'ingle', [[px, Y.pelvis], [p.torso(Y.pelvis), Y.pelvis], ...vOuter.slice(1), [px, vAt(px)]], side);
  single(s, 'pubis', [[-px, Y.pelvis], [px, Y.pelvis], [px, vAt(px)], ...vInner, [0, p.crotchY + 3], ...vInner.map(([x, y]) => [-x, y]).reverse(), [-px, vAt(px)]]);
  // extremidades
  pair(s, 'hombro', L.hombro, side);
  pair(s, 'brazo', L.brazo, side);
  pair(s, 'codo', L.codo, side);
  pair(s, 'antebrazo', L.antebrazo, side);
  pair(s, 'muneca', L.muneca, side);
  pair(s, 'mano', L.mano, side);
  pair(s, 'muslo', L.musloFront, side);
  pair(s, 'rodilla', L.rodilla, side);
  pair(s, 'pierna', L.pierna, side);
  pair(s, 'tobillo', L.tobillo, side);
  pair(s, 'pie', L.pie, side);
  // detalles no tocables
  for (const sgn of [-1, 1]) {
    s.push({ kind: 'deco', d: absPath(ellipse(CX + sgn * 15, 86, 4.2, 2.6, 14)), fill: 'ink' });
    // ceja
    deco(s, spline([[sgn * 9, 78], [sgn * 15, 75.5], [sgn * 22, 77.5]], 4));
    if (p.F) {
      // contorno inferior del busto
      const pts = [];
      for (let a = 15; a <= 165; a += 10) pts.push([sgn * (30 + Math.cos((a * Math.PI) / 180) * 23), 236 + Math.sin((a * Math.PI) / 180) * 21]);
      deco(s, pts);
    } else {
      deco(s, spline([[sgn * 12, 252], [sgn * 32, 258], [sgn * 54, 250], [sgn * 62, 238]], 5));
    }
    // clavícula
    deco(s, spline([[sgn * 9, 170], [sgn * 30, 172], [sgn * (p.sh - 8), 176]], 4));
    // rótula
    deco(s, ellipse(sgn * ((p.legOuter(606) + p.legInner(606)) / 2), 606, 10, 12, 18).concat([[sgn * ((p.legOuter(606) + p.legInner(606)) / 2) + 10, 606]]));
  }
  deco(s, [[-2.5, 100], [0, 104], [2.5, 100]]); // nariz
  deco(s, spline([[-9, 118], [0, 121], [9, 118]], 4)); // boca
  s.push({ kind: 'deco', d: absPath(ellipse(CX, 340, 2.2, 3, 10)), fill: 'ink' }); // ombligo
  return { shapes: s, p };
}

export function backView(sex) {
  const p = params(sex);
  const s = [];
  const side = 'der'; // en vista posterior, x>0 (derecha de la pantalla) es el lado derecho de la persona
  const hw = p.head;
  single(s, 'cabeza_post', band((y) => -hw(y), hw, Y.headTop, 108, 2));
  const nk = p.neck;
  const lowerHead = band((y) => -hw(y), hw, 108, Y.chin, 2);
  single(s, 'nuca', [...edge(hw, 108, Y.neckTop, 2), [nk, 150], ...slopePts(p, nk, nk + 6, 3), [0, Y.neckBase + 3], ...slopePts(p, nk, nk + 6, 3).map(([x, y]) => [-x, y]).reverse(), [-nk, 150], ...edge((y) => -hw(y), Y.neckTop, 108, 2)]);
  void lowerHead;
  pair(s, 'oido', ellipse(hw(92) + 3.5, 92, 5, 11, 16), side);
  const sp = 9; // semiancho de la franja de la columna
  // espalda alta (escapular)
  const alta = [[sp, Y.neckBase + 3], ...slopePts(p, nk + 6, p.sh, 8), ...edge(p.torso, Y.shoulder, 262), [sp, 262]];
  pair(s, 'espalda_alta', alta, side);
  pair(s, 'espalda_media', band(konst(sp), p.torso, 262, Y.abd1), side);
  single(s, 'columna_dorsal', [[-sp, Y.neckBase + 3], [sp, Y.neckBase + 3], [sp, Y.abd1], [-sp, Y.abd1]]);
  // lumbar
  const lumEnd = 378;
  pair(s, 'lumbar', band(konst(sp), p.torso, Y.abd1, lumEnd), side);
  single(s, 'columna_lumbar', [[-sp, Y.abd1], [sp, Y.abd1], [sp, lumEnd], [-sp, lumEnd]]);
  // sacro y glúteos
  const edgeOut = (y) => (y <= Y.pelvis ? p.torso(y) : p.legOuter(y));
  const sac = [[-16, lumEnd], [16, lumEnd], [9, 412], [0, 428], [-9, 412]];
  single(s, 'sacro', sac);
  const glY = 458;
  const gl = [[16, lumEnd], ...edge(edgeOut, lumEnd, glY), ...spline([[edgeOut(glY), glY], [edgeOut(glY) * 0.6, glY + 7], [edgeOut(glY) * 0.25, glY + 5], [2, glY - 6]], 5).slice(1), [0, 428], [9, 412]];
  pair(s, 'gluteo', gl, side);
  const L = limbs(p);
  pair(s, 'hombro_post', L.hombro, side);
  pair(s, 'brazo_post', L.brazo, side);
  pair(s, 'codo_post', L.codo, side);
  pair(s, 'antebrazo_post', L.antebrazo, side);
  pair(s, 'muneca_post', L.muneca, side);
  pair(s, 'mano_dorso', L.mano, side);
  const li = (y) => (y < 450 ? 2 : p.legInner(y));
  const musloPost = [...spline([[2, glY - 6], [edgeOut(glY) * 0.25, glY + 5], [edgeOut(glY) * 0.6, glY + 7], [edgeOut(glY), glY]], 5), ...edge(p.legOuter, glY, 575).slice(1), ...edge(li, 575, glY - 4)];
  pair(s, 'muslo_post', musloPost, side);
  pair(s, 'corva', L.rodilla, side);
  pair(s, 'pantorrilla', band(p.legInner, p.legOuter, 640, 760), side);
  pair(s, 'aquiles', band(p.legInner, p.legOuter, 760, 810), side);
  pair(s, 'talon', L.pie, side);
  // detalles
  for (const sgn of [-1, 1]) {
    // borde de la escápula
    deco(s, spline([[sgn * 20, 196], [sgn * 26, 232], [sgn * 44, 250], [sgn * (p.sh - 6), 222]], 5));
    deco(s, spline([[sgn * ((p.legOuter(606) + p.legInner(606)) / 2 - 9), 600], [sgn * ((p.legOuter(606) + p.legInner(606)) / 2), 610], [sgn * ((p.legOuter(606) + p.legInner(606)) / 2 + 9), 600]], 4));
  }
  // línea media de la columna
  deco(s, [[0, Y.neckBase + 8], [0, lumEnd]], { dashed: true });
  // nacimiento del cabello
  deco(s, spline([[-hw(110) + 3, 110], [-14, 119], [0, 121], [14, 119], [hw(110) - 3, 110]], 5));
  return { shapes: s, p };
}

export { Y };
