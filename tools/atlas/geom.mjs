// Utilidades geométricas para generar el atlas corporal de Savia.
// Todo se trabaja en "media figura": x = distancia al eje central (positivo hacia un lado), y hacia abajo.

export const CX = 200; // eje central de la figura en el lienzo

export const r1 = (n) => Math.round(n * 10) / 10;

/** Interpolación de Hermite (tangentes limitadas) por puntos de control [[y, x], ...] → x(y). */
export function curve(pts) {
  const n = pts.length;
  const ys = pts.map((p) => p[0]);
  const xs = pts.map((p) => p[1]);
  const m = new Array(n);
  for (let i = 0; i < n; i++) {
    if (i === 0) m[i] = (xs[1] - xs[0]) / (ys[1] - ys[0]);
    else if (i === n - 1) m[i] = (xs[n - 1] - xs[n - 2]) / (ys[n - 1] - ys[n - 2]);
    else {
      const d0 = (xs[i] - xs[i - 1]) / (ys[i] - ys[i - 1]);
      const d1 = (xs[i + 1] - xs[i]) / (ys[i + 1] - ys[i]);
      m[i] = d0 * d1 <= 0 ? 0 : (d0 + d1) / 2;
    }
  }
  return (y) => {
    if (y <= ys[0]) return xs[0];
    if (y >= ys[n - 1]) return xs[n - 1];
    let i = 0;
    while (y > ys[i + 1]) i++;
    const h = ys[i + 1] - ys[i];
    const t = (y - ys[i]) / h;
    const h00 = 2 * t ** 3 - 3 * t ** 2 + 1;
    const h10 = t ** 3 - 2 * t ** 2 + t;
    const h01 = -2 * t ** 3 + 3 * t ** 2;
    const h11 = t ** 3 - t ** 2;
    return h00 * xs[i] + h10 * h * m[i] + h01 * xs[i + 1] + h11 * h * m[i + 1];
  };
}

export const konst = (v) => () => v;

/** Muestras de x(y) entre y0 e y1 → [[x, y], ...] (en orden y0 → y1). */
export function edge(fn, y0, y1, step = 3) {
  const out = [];
  const n = Math.max(1, Math.ceil(Math.abs(y1 - y0) / step));
  for (let i = 0; i <= n; i++) {
    const y = y0 + ((y1 - y0) * i) / n;
    out.push([fn(y), y]);
  }
  return out;
}

/** Banda entre dos funciones x(y) (izquierda y derecha) de y0 a y1. */
export function band(xl, xr, y0, y1, step = 3) {
  return [...edge(xr, y0, y1, step), ...edge(xl, y1, y0, step)];
}

export const mirror = (pts) => pts.map(([x, y]) => [-x, y]).reverse();

/** Polígono (media figura) → cadena de path SVG en coordenadas del lienzo. */
export function toPath(pts, cx = CX) {
  return 'M' + pts.map(([x, y]) => `${r1(cx + x)} ${r1(y)}`).join('L') + 'Z';
}

/** Curva cerrada suave (Catmull-Rom) por puntos [[x, y], ...]. */
export function blob(pts, samples = 8) {
  const n = pts.length;
  const out = [];
  for (let i = 0; i < n; i++) {
    const p0 = pts[(i - 1 + n) % n], p1 = pts[i], p2 = pts[(i + 1) % n], p3 = pts[(i + 2) % n];
    for (let s = 0; s < samples; s++) {
      const t = s / samples;
      const t2 = t * t, t3 = t2 * t;
      out.push([
        0.5 * (2 * p1[0] + (-p0[0] + p2[0]) * t + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2 + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3),
        0.5 * (2 * p1[1] + (-p0[1] + p2[1]) * t + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2 + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3),
      ]);
    }
  }
  return out;
}

/** Curva abierta suave por puntos (Catmull-Rom con extremos repetidos). */
export function spline(pts, samples = 8) {
  const n = pts.length;
  const out = [];
  for (let i = 0; i < n - 1; i++) {
    const p0 = pts[Math.max(0, i - 1)], p1 = pts[i], p2 = pts[i + 1], p3 = pts[Math.min(n - 1, i + 2)];
    for (let s = 0; s < samples; s++) {
      const t = s / samples, t2 = t * t, t3 = t2 * t;
      out.push([
        0.5 * (2 * p1[0] + (-p0[0] + p2[0]) * t + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2 + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3),
        0.5 * (2 * p1[1] + (-p0[1] + p2[1]) * t + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2 + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3),
      ]);
    }
  }
  out.push(pts[n - 1]);
  return out;
}

/** Banda de grosor w a lo largo de una polilínea (para "tubos": costillas, intestino). */
export function thick(line, w) {
  const left = [], right = [];
  for (let i = 0; i < line.length; i++) {
    const a = line[Math.max(0, i - 1)], b = line[Math.min(line.length - 1, i + 1)];
    let dx = b[0] - a[0], dy = b[1] - a[1];
    const len = Math.hypot(dx, dy) || 1;
    dx /= len; dy /= len;
    left.push([line[i][0] - dy * w / 2, line[i][1] + dx * w / 2]);
    right.push([line[i][0] + dy * w / 2, line[i][1] - dx * w / 2]);
  }
  return [...left, ...right.reverse()];
}

export function ellipse(cx, cy, rx, ry, n = 28, rot = 0) {
  const out = [];
  const c = Math.cos(rot), s = Math.sin(rot);
  for (let i = 0; i < n; i++) {
    const a = (i / n) * Math.PI * 2;
    const x = Math.cos(a) * rx, y = Math.sin(a) * ry;
    out.push([cx + x * c - y * s, cy + x * s + y * c]);
  }
  return out;
}

/**
 * Hueso largo: diáfisis de ancho w entre (x1,y1) y (x2,y2) con epífisis redondeadas de radios ra y rb.
 */
export function bone(x1, y1, x2, y2, w, ra, rb) {
  const len = Math.hypot(x2 - x1, y2 - y1);
  const ang = Math.atan2(y2 - y1, x2 - x1);
  const h = w / 2;
  ra = Math.max(ra, h + 0.01); rb = Math.max(rb, h + 0.01);
  const a0 = Math.asin(h / ra), b0 = Math.asin(h / rb);
  const local = [];
  const N = 10;
  // arco de la epífisis A (alrededor de x=0), de ángulo a0 a 2π−a0 pasando por π
  for (let i = 0; i <= N; i++) {
    const a = a0 + ((2 * Math.PI - 2 * a0) * i) / N;
    local.push([Math.cos(a) * ra, Math.sin(a) * ra]);
  }
  // arco de la epífisis B (alrededor de x=len), de −(π−b0) a (π−b0) pasando por 0
  for (let i = 0; i <= N; i++) {
    const a = -(Math.PI - b0) + ((2 * Math.PI - 2 * b0) * i) / N;
    local.push([len + Math.cos(a) * rb, Math.sin(a) * rb]);
  }
  const c = Math.cos(ang), s = Math.sin(ang);
  return local.map(([x, y]) => [x1 + x * c - y * s, y1 + x * s + y * c]);
}

/** Cápsula (segmento grueso con extremos redondos). */
export function capsule(x1, y1, x2, y2, w) {
  return bone(x1, y1, x2, y2, w, w / 2 + 0.01, w / 2 + 0.01);
}

export function roundRect(x, y, w, h, r, n = 4) {
  const out = [];
  const corners = [[x + w - r, y + r, -Math.PI / 2], [x + w - r, y + h - r, 0], [x + r, y + h - r, Math.PI / 2], [x + r, y + r, Math.PI]];
  for (const [cx, cy, a0] of corners) {
    for (let i = 0; i <= n; i++) {
      const a = a0 + (Math.PI / 2) * (i / n);
      out.push([cx + Math.cos(a) * r, cy + Math.sin(a) * r]);
    }
  }
  return out;
}

/** Polígono en coordenadas absolutas → path. */
export const absPath = (pts) => 'M' + pts.map(([x, y]) => `${r1(x)} ${r1(y)}`).join('L') + 'Z';
export const absLine = (pts) => 'M' + pts.map(([x, y]) => `${r1(x)} ${r1(y)}`).join('L');
