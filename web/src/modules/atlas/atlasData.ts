// Modelo del atlas corporal (mismo archivo atlas.json que usa la app de Android) y búsqueda por punto.
import { millisToEpochDay, today } from '../../lib/dates'

export type ShapeKind = 'region' | 'skin' | 'bone' | 'organ' | 'deco'

/** Una forma: uno o más polígonos cerrados (o líneas abiertas si `open`). */
export interface AtlasShape {
  id: string | null
  kind: ShapeKind
  /** Path SVG original ("M x yL x y…Z"). */
  d: string
  /** Cada polígono: [x0, y0, x1, y1, …]. */
  polys: Float32Array[]
  open: boolean
  /** Color "#RRGGBB" (órganos). */
  color: string | null
  alpha: number
  dashed: boolean
  inkFill: boolean
  /** Silueta con contorno (figuras de mascota). */
  outline: boolean
  /** Caja envolvente [izq, arriba, der, abajo]. */
  bounds: [number, number, number, number]
}

export interface AtlasText { x: number; y: number; text: string; size: number }

export interface AtlasViewData {
  id: string
  group: string
  /** "F", "M" o null si es igual para todos. */
  sex: string | null
  /** Especie ("perro", "gato") o null para la figura humana. */
  species: string | null
  title: string
  /** [x, y, ancho, alto] */
  box: [number, number, number, number]
  shapes: AtlasShape[]
  texts: AtlasText[]
}

/** Ficha educativa de una zona, hueso u órgano. */
export interface AtlasInfo {
  name: string
  description: string
  bones: string[]
  muscles: string[]
  organs: string[]
  /** Cuándo conviene consultar (orientativo). */
  tip: string | null
  /** Grupo de vista relacionado ("hand", "foot", "organs"…). */
  link: string | null
}

export type GroupOpt = readonly [key: string, label: string]

/** Grupos de la figura humana, en el orden en que se muestran. */
export const HUMAN_GROUPS: readonly GroupOpt[] = [
  ['front', 'Frente'], ['back', 'Espalda'], ['abdomen', 'Abdomen'], ['organs', 'Órganos'],
  ['skeleton', 'Esqueleto'], ['hand', 'Mano'], ['foot', 'Pie'],
]
/** Grupos de las figuras de mascota (vista lateral). */
export const PET_GROUPS: readonly GroupOpt[] = [['pet_side', 'Cuerpo'], ['pet_organs', 'Órganos']]

/** Grupos que muestran las marcas "Der." / "Izq." en las esquinas. */
export const SIDE_MARK_GROUPS = ['front', 'back', 'organs', 'skeleton', 'abdomen']

export class Atlas {
  constructor(
    readonly views: AtlasViewData[],
    readonly info: Record<string, AtlasInfo>,
    readonly note: string,
    readonly petNote: string,
  ) {}

  /**
   * Grupos que se muestran a un perfil: los humanos para personas; para mascotas, solo los que tienen
   * figura de su especie (lista vacía si aún no hay figura para esa especie).
   */
  groupsFor(isPet: boolean, species: string): readonly GroupOpt[] {
    if (!isPet) return HUMAN_GROUPS
    return PET_GROUPS.filter(([g]) => this.views.some((v) => v.group === g && v.species === species))
  }

  groupLabel(group: string): string | null {
    return [...HUMAN_GROUPS, ...PET_GROUPS].find((g) => g[0] === group)?.[1] ?? null
  }

  /** Vista de un grupo. Para personas `species` es null; para mascotas, su especie. */
  view(group: string, sex: string, species: string | null = null): AtlasViewData | null {
    const candidates = this.views.filter((v) => v.group === group && v.species === species)
    return candidates.find((v) => v.sex === sex) ?? candidates[0] ?? null
  }

  hasSexVariants(group: string, species: string | null = null): boolean {
    return this.views.filter((v) => v.group === group && v.species === species).length > 1
  }

  /** Nota de orientación para cada grupo. */
  orientationNote(group: string): string {
    switch (group) {
      case 'pet_side':
      case 'pet_organs': return 'Vista de lado: las zonas valen para ambos lados del cuerpo.'
      case 'back': return 'Vista de espalda: su lado derecho queda a tu derecha.'
      case 'hand': return 'Mano derecha vista por el dorso (los huesos son iguales en la izquierda, en espejo).'
      case 'foot': return 'Pie derecho visto desde arriba (el izquierdo es igual, en espejo).'
      default: return 'Vista de frente, como en un espejo invertido: su lado derecho queda a tu izquierda.'
    }
  }
}

/** "M x yL x y…Z M…" → lista de polígonos. */
export function parsePolys(d: string): Float32Array[] {
  const out: Float32Array[] = []
  for (const sub of d.split('M')) {
    let body = sub.trim()
    if (body.endsWith('Z')) body = body.slice(0, -1)
    if (!body) continue
    const pts = body.split('L')
    const arr = new Float32Array(pts.length * 2)
    let k = 0
    for (const pt of pts) {
      const t = pt.trim()
      const sp = t.indexOf(' ')
      arr[k++] = parseFloat(t.slice(0, sp))
      arr[k++] = parseFloat(t.slice(sp + 1))
    }
    out.push(arr)
  }
  return out
}

function boundsOf(polys: Float32Array[]): [number, number, number, number] {
  let l = Infinity, t = Infinity, r = -Infinity, b = -Infinity
  for (const p of polys) {
    for (let i = 0; i < p.length; i += 2) {
      if (p[i] < l) l = p[i]
      if (p[i] > r) r = p[i]
      if (p[i + 1] < t) t = p[i + 1]
      if (p[i + 1] > b) b = p[i + 1]
    }
  }
  return [l, t, r, b]
}

/* eslint-disable @typescript-eslint/no-explicit-any */
/** Convierte el JSON del atlas en el modelo. */
export function parseAtlas(root: any): Atlas {
  const views: AtlasViewData[] = (root.views as any[]).map((v) => ({
    id: v.id,
    group: v.group,
    sex: v.sex ?? null,
    species: v.species ? String(v.species) : null,
    title: v.title,
    box: [v.box[0], v.box[1], v.box[2], v.box[3]],
    shapes: (v.shapes as any[]).map((s): AtlasShape => {
      const d: string = s.d
      const polys = parsePolys(d)
      const kind: ShapeKind = ['region', 'skin', 'bone', 'organ'].includes(s.kind) ? s.kind : 'deco'
      return {
        id: s.id ? String(s.id) : null,
        kind,
        d,
        polys,
        open: !d.trimEnd().endsWith('Z'),
        color: typeof s.color === 'string' && s.color.startsWith('#') ? s.color : null,
        alpha: typeof s.alpha === 'number' ? s.alpha : 1,
        dashed: s.dashed === true,
        inkFill: s.fill === 'ink',
        outline: s.outline === true,
        bounds: boundsOf(polys),
      }
    }),
    texts: ((v.texts ?? []) as any[]).map((t) => ({ x: t.x, y: t.y, text: t.t, size: t.size })),
  }))
  const info: Record<string, AtlasInfo> = {}
  for (const [key, o] of Object.entries(root.info as Record<string, any>)) {
    info[key] = {
      name: o.n,
      description: o.d ?? '',
      bones: o.b ?? [],
      muscles: o.m ?? [],
      organs: o.o ?? [],
      tip: o.t ? String(o.t) : null,
      link: o.link ? String(o.link) : null,
    }
  }
  return new Atlas(views, info, root.note ?? '', root.petNote ?? '')
}
/* eslint-enable @typescript-eslint/no-explicit-any */

/** ¿El punto cae dentro de algún polígono de la forma? (regla par-impar por polígono) */
export function shapeContains(s: AtlasShape, x: number, y: number): boolean {
  if (s.open) return false
  const [l, t, r, b] = s.bounds
  if (x < l || x > r || y < t || y > b) return false
  for (const p of s.polys) {
    let inside = false
    const n = p.length / 2
    let j = n - 1
    for (let i = 0; i < n; i++) {
      const xi = p[i * 2], yi = p[i * 2 + 1], xj = p[j * 2], yj = p[j * 2 + 1]
      if (yi > y !== yj > y && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) inside = !inside
      j = i
    }
    if (inside) return true
  }
  return false
}

/** Distancia mínima del punto al contorno (tolerancia al tocar huesos delgados). */
export function shapeDistance(s: AtlasShape, x: number, y: number): number {
  let best = Infinity
  for (const p of s.polys) {
    const n = p.length / 2
    for (let i = 0; i < n; i++) {
      const j = (i + 1) % n
      const ax = p[i * 2], ay = p[i * 2 + 1], bx = p[j * 2], by = p[j * 2 + 1]
      const dx = bx - ax, dy = by - ay
      const len2 = dx * dx + dy * dy
      const t = len2 <= 0 ? 0 : Math.min(1, Math.max(0, ((x - ax) * dx + (y - ay) * dy) / len2))
      const d = Math.hypot(x - (ax + t * dx), y - (ay + t * dy))
      if (d < best) best = d
    }
  }
  return best
}

/** Forma tocable en el punto (la de más arriba), con tolerancia para formas pequeñas. */
export function hitTest(view: AtlasViewData, x: number, y: number, tolerance: number): AtlasShape | null {
  for (let i = view.shapes.length - 1; i >= 0; i--) {
    const s = view.shapes[i]
    if (s.id !== null && shapeContains(s, x, y)) return s
  }
  let best: AtlasShape | null = null
  let bestD = tolerance
  for (const s of view.shapes) {
    if (s.id === null) continue
    const [l, t, r, b] = s.bounds
    if (x < l - tolerance || x > r + tolerance || y < t - tolerance || y > b + tolerance) continue
    const d = shapeDistance(s, x, y)
    if (d < bestD) { bestD = d; best = s }
  }
  return best
}

/** Síntomas por zona en los últimos 30 días. */
export function recentCounts(symptoms: readonly { regionId: string | null; timestamp: number }[], t: number = today()): Record<string, number> {
  const from = t - 30
  const out: Record<string, number> = {}
  for (const s of symptoms) {
    if (s.regionId && millisToEpochDay(s.timestamp) >= from) out[s.regionId] = (out[s.regionId] ?? 0) + 1
  }
  return out
}

/** Porcentaje de tinte (0..70) de una zona según cuántos síntomas recientes tiene. */
export const tintPercent = (count: number): number => (count > 0 ? Math.min(70, 28 + 12 * count) : 0)

// ── Transformación lienzo ↔ atlas (zoom y desplazamiento) ──────────────────────────────────────

export const MIN_ZOOM = 1
export const MAX_ZOOM = 6

/** Encaje base de la vista en el lienzo: escala y márgenes para centrarla. */
export function fitView(box: readonly [number, number, number, number], width: number, height: number, pad = 8): { s0: number; mx: number; my: number } {
  const s0 = Math.max(0.0001, Math.min((width - 2 * pad) / box[2], (height - 2 * pad) / box[3]))
  return { s0, mx: (width - box[2] * s0) / 2, my: (height - box[3] * s0) / 2 }
}

/** Limita el desplazamiento para que el contenido no se salga del lienzo. */
export function clampPan(px: number, py: number, zoom: number, width: number, height: number): [number, number] {
  return [Math.min(0, Math.max(width - width * zoom, px)), Math.min(0, Math.max(height - height * zoom, py))]
}

/** Punto de pantalla (relativo al lienzo) → coordenadas del atlas. */
export function screenToAtlas(
  sx: number, sy: number, box: readonly [number, number, number, number], width: number, height: number, zoom: number, panX: number, panY: number,
): [number, number] {
  const { s0, mx, my } = fitView(box, width, height)
  return [((sx - panX) / zoom - mx) / s0 + box[0], ((sy - panY) / zoom - my) / s0 + box[1]]
}

/** Nuevo zoom y desplazamiento al escalar por `factor` manteniendo fijo el punto (cx, cy) del lienzo. */
export function zoomAbout(
  zoom: number, panX: number, panY: number, factor: number, cx: number, cy: number, width: number, height: number,
): { zoom: number; panX: number; panY: number } {
  const next = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, zoom * factor))
  const k = next / zoom
  const [px, py] = clampPan(cx - (cx - panX) * k, cy - (cy - panY) * k, next, width, height)
  return { zoom: next, panX: px, panY: py }
}

// ── Carga ──────────────────────────────────────────────────────────────────────────────────────

let cached: Promise<Atlas> | null = null

/** Carga atlas.json (ruta relativa: la app puede vivir en una subcarpeta) y lo deja en memoria. */
export function loadAtlas(): Promise<Atlas> {
  if (!cached) {
    cached = fetch('./atlas.json')
      .then((r) => { if (!r.ok) throw new Error(`atlas.json: ${r.status}`); return r.json() })
      .then(parseAtlas)
      .catch((e) => { cached = null; throw e })
  }
  return cached
}
