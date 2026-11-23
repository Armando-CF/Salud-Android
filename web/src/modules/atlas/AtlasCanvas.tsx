import { memo, useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState, type CSSProperties, type PointerEvent as ReactPointerEvent } from 'react'
import { Maximize2, Minus, Plus } from 'lucide-react'
import {
  MAX_ZOOM, MIN_ZOOM, SIDE_MARK_GROUPS, clampPan, fitView, hitTest, screenToAtlas, tintPercent, zoomAbout,
  type AtlasShape, type AtlasViewData,
} from './atlasData'

/** Tolerancia al tocar (en píxeles de pantalla) para formas pequeñas o delgadas, como los huesos. */
const TAP_TOLERANCE_PX = 14
/** Movimiento máximo (px) para que un toque cuente como "toque" y no como arrastre. */
const TAP_SLOP_PX = 10

interface Size { w: number; h: number }

/**
 * Lienzo del atlas: dibuja la vista como SVG y maneja el toque (selección), el pellizco con dos dedos (zoom),
 * el arrastre con un dedo cuando hay zoom, la rueda del ratón y los botones de acercar/alejar/restablecer.
 * El zoom transforma un único <g> de forma directa (sin volver a renderizar los paths en cada movimiento).
 */
export function AtlasCanvas(props: {
  view: AtlasViewData
  selectedId: string | null
  /** Síntomas recientes por zona (tinte). */
  counts: Record<string, number>
  onSelect: (id: string | null) => void
}) {
  const { view, selectedId, counts, onSelect } = props
  const boxRef = useRef<HTMLDivElement>(null)
  const gRef = useRef<SVGGElement>(null)
  const [size, setSize] = useState<Size>({ w: 0, h: 0 })
  const sizeRef = useRef(size)
  sizeRef.current = size
  // Estado del zoom fuera de React: se aplica directo al atributo transform.
  const t = useRef({ zoom: 1, panX: 0, panY: 0 })
  const [zoomLevel, setZoomLevel] = useState(1) // solo para habilitar/mostrar botones
  const viewRef = useRef(view)
  viewRef.current = view

  const apply = useCallback(() => {
    const g = gRef.current
    const { w, h } = sizeRef.current
    if (!g || w <= 0 || h <= 0) return
    const v = viewRef.current
    const { s0, mx, my } = fitView(v.box, w, h)
    const { zoom, panX, panY } = t.current
    g.setAttribute(
      'transform',
      `translate(${panX.toFixed(2)} ${panY.toFixed(2)}) scale(${zoom.toFixed(4)}) translate(${mx.toFixed(2)} ${my.toFixed(2)}) scale(${s0.toFixed(5)}) translate(${-v.box[0]} ${-v.box[1]})`,
    )
  }, [])

  const setTransform = useCallback((zoom: number, panX: number, panY: number) => {
    const { w, h } = sizeRef.current
    const z = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, zoom))
    const [px, py] = clampPan(panX, panY, z, w, h)
    t.current = { zoom: z, panX: px, panY: py }
    apply()
    // Redondeado para no provocar renders en cada cuadro del gesto.
    const level = Math.round(z * 4) / 4
    setZoomLevel((prev) => (prev === level ? prev : level))
  }, [apply])

  // Tamaño del lienzo
  useLayoutEffect(() => {
    const el = boxRef.current
    if (!el) return
    const measure = () => {
      const r = el.getBoundingClientRect()
      setSize((s) => (Math.abs(s.w - r.width) < 0.5 && Math.abs(s.h - r.height) < 0.5 ? s : { w: r.width, h: r.height }))
    }
    measure()
    const ro = new ResizeObserver(measure)
    ro.observe(el)
    return () => ro.disconnect()
  }, [])

  // Al cambiar de vista se restablece el zoom; al cambiar el tamaño solo se reacomoda.
  useLayoutEffect(() => { setTransform(1, 0, 0) }, [view.id, setTransform])
  useLayoutEffect(() => { setTransform(t.current.zoom, t.current.panX, t.current.panY) }, [size, setTransform])

  // ── Gestos ──
  const pointers = useRef(new Map<number, { x: number; y: number }>())
  const gesture = useRef({ startX: 0, startY: 0, moved: false, multi: false, lastDist: 0, lastCx: 0, lastCy: 0 })

  const local = (e: { clientX: number; clientY: number }): [number, number] => {
    const r = boxRef.current!.getBoundingClientRect()
    return [e.clientX - r.left, e.clientY - r.top]
  }

  const onPointerDown = (e: ReactPointerEvent<HTMLDivElement>) => {
    if (e.pointerType === 'mouse' && e.button !== 0) return
    const [x, y] = local(e)
    try { e.currentTarget.setPointerCapture(e.pointerId) } catch { /* sin captura */ }
    pointers.current.set(e.pointerId, { x, y })
    const g = gesture.current
    if (pointers.current.size === 1) {
      g.startX = x; g.startY = y; g.moved = false; g.multi = false
    } else if (pointers.current.size === 2) {
      const [a, b] = [...pointers.current.values()]
      g.multi = true
      g.lastDist = Math.hypot(a.x - b.x, a.y - b.y)
      g.lastCx = (a.x + b.x) / 2; g.lastCy = (a.y + b.y) / 2
    }
  }

  const onPointerMove = (e: ReactPointerEvent<HTMLDivElement>) => {
    const prev = pointers.current.get(e.pointerId)
    if (!prev) return
    const [x, y] = local(e)
    pointers.current.set(e.pointerId, { x, y })
    const g = gesture.current
    const cur = t.current
    if (pointers.current.size >= 2) {
      const [a, b] = [...pointers.current.values()]
      const dist = Math.hypot(a.x - b.x, a.y - b.y)
      const cx = (a.x + b.x) / 2, cy = (a.y + b.y) / 2
      if (g.lastDist > 0) {
        const { w, h } = sizeRef.current
        const z = zoomAbout(cur.zoom, cur.panX, cur.panY, dist / g.lastDist, cx, cy, w, h)
        // además del pellizco, los dos dedos arrastran la figura
        setTransform(z.zoom, z.panX + (cx - g.lastCx), z.panY + (cy - g.lastCy))
      }
      g.lastDist = dist; g.lastCx = cx; g.lastCy = cy
      g.moved = true
    } else {
      if (!g.moved && Math.hypot(x - g.startX, y - g.startY) > TAP_SLOP_PX) g.moved = true
      if (g.moved && cur.zoom > 1.001) setTransform(cur.zoom, cur.panX + (x - prev.x), cur.panY + (y - prev.y))
    }
  }

  const endPointer = (e: ReactPointerEvent<HTMLDivElement>, cancelled: boolean) => {
    if (!pointers.current.has(e.pointerId)) return
    pointers.current.delete(e.pointerId)
    const g = gesture.current
    if (pointers.current.size > 0) { g.lastDist = 0; return }
    if (cancelled || g.moved || g.multi) return
    // Toque simple: seleccionar la zona bajo el dedo (o quitar la selección si se tocó el vacío).
    const [x, y] = local(e)
    const v = viewRef.current
    const { w, h } = sizeRef.current
    const { zoom, panX, panY } = t.current
    const [ax, ay] = screenToAtlas(x, y, v.box, w, h, zoom, panX, panY)
    const { s0 } = fitView(v.box, w, h)
    onSelect(hitTest(v, ax, ay, TAP_TOLERANCE_PX / (s0 * zoom))?.id ?? null)
  }

  // Rueda del ratón (escritorio) y gesto de pellizco propio de Safari: listeners no pasivos.
  useEffect(() => {
    const el = boxRef.current
    if (!el) return
    const onWheel = (e: WheelEvent) => {
      e.preventDefault()
      const r = el.getBoundingClientRect()
      const cur = t.current
      const z = zoomAbout(cur.zoom, cur.panX, cur.panY, Math.exp(-e.deltaY * 0.002), e.clientX - r.left, e.clientY - r.top, r.width, r.height)
      setTransform(z.zoom, z.panX, z.panY)
    }
    const stop = (e: Event) => e.preventDefault()
    el.addEventListener('wheel', onWheel, { passive: false })
    el.addEventListener('gesturestart', stop)
    el.addEventListener('gesturechange', stop)
    return () => {
      el.removeEventListener('wheel', onWheel)
      el.removeEventListener('gesturestart', stop)
      el.removeEventListener('gesturechange', stop)
    }
  }, [setTransform])

  const zoomBy = (factor: number) => {
    const { w, h } = sizeRef.current
    const cur = t.current
    const z = zoomAbout(cur.zoom, cur.panX, cur.panY, factor, w / 2, h / 2, w, h)
    setTransform(z.zoom, z.panX, z.panY)
  }

  const marks = SIDE_MARK_GROUPS.includes(view.group)
  return (
    <div
      ref={boxRef}
      className="atlas-canvas"
      data-zoom={zoomLevel}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={(e) => endPointer(e, false)}
      onPointerCancel={(e) => endPointer(e, true)}
    >
      {size.w > 0 && (
        <svg width={size.w} height={size.h} viewBox={`0 0 ${size.w} ${size.h}`} role="img" aria-label={`Figura: ${view.title}`}>
          <g ref={gRef}>
            <Shapes view={view} selectedId={selectedId} counts={counts} />
          </g>
        </svg>
      )}
      {marks && (
        <>
          <span className="atlas-mark left" aria-hidden>{view.group === 'back' ? 'Izq.' : 'Der.'}</span>
          <span className="atlas-mark right" aria-hidden>{view.group === 'back' ? 'Der.' : 'Izq.'}</span>
        </>
      )}
      <div className="atlas-zoom" onPointerDown={(e) => e.stopPropagation()}>
        {zoomLevel > 1.01 && (
          <button type="button" aria-label="Restablecer zoom" title="Restablecer zoom" onClick={() => setTransform(1, 0, 0)}><Maximize2 size={20} /></button>
        )}
        <div className="col">
          <button type="button" aria-label="Acercar" title="Acercar" disabled={zoomLevel >= MAX_ZOOM} onClick={() => zoomBy(1.6)}><Plus size={22} /></button>
          <button type="button" aria-label="Alejar" title="Alejar" disabled={zoomLevel <= 1.01} onClick={() => zoomBy(1 / 1.6)}><Minus size={22} /></button>
        </div>
      </div>
    </div>
  )
}

function regionFill(count: number, selected: boolean): string {
  const tint = tintPercent(count)
  const base = tint > 0 ? `color-mix(in srgb, var(--bad) ${tint}%, var(--body-fill))` : 'var(--body-fill)'
  return selected ? `color-mix(in srgb, var(--primary) 72%, ${base})` : base
}

/** Los paths de la vista. Solo se vuelven a renderizar al cambiar de vista, de selección o de tinte (no al hacer zoom). */
const Shapes = memo(function Shapes(props: { view: AtlasViewData; selectedId: string | null; counts: Record<string, number> }) {
  const { view, selectedId, counts } = props
  const selected = useMemo(() => view.shapes.find((s) => s.id !== null && s.id === selectedId) ?? null, [view, selectedId])
  return (
    <>
      {view.shapes.map((s, i) => <ShapePath key={i} shape={s} selected={s === selected} count={s.id ? counts[s.id] ?? 0 : 0} />)}
      {view.texts.map((tx, i) => (
        <text key={`t${i}`} className="atlas-text" x={tx.x} y={tx.y} fontSize={tx.size} textAnchor="middle">{tx.text}</text>
      ))}
      {/* Contorno de la selección por encima de todo, para que los vecinos no lo tapen. */}
      {selected && <path className={`atlas-sel ${selected.kind}`} d={selected.d} vectorEffect="non-scaling-stroke" />}
    </>
  )
})

function ShapePath(props: { shape: AtlasShape; selected: boolean; count: number }) {
  const { shape: s, selected, count } = props
  switch (s.kind) {
    case 'skin':
      return <path className={`atlas-skin${s.outline ? ' outline' : ''}`} d={s.d} fillOpacity={s.alpha} vectorEffect="non-scaling-stroke" />
    case 'region':
      return <path className="atlas-region" d={s.d} style={{ fill: regionFill(count, selected) }} vectorEffect="non-scaling-stroke" data-id={s.id ?? undefined} />
    case 'bone':
      return <path className={`atlas-bone${selected ? ' on' : ''}`} d={s.d} vectorEffect="non-scaling-stroke" data-id={s.id ?? undefined} />
    case 'organ':
      return (
        <path
          className={`atlas-organ${s.dashed && !selected ? ' dashed' : ''}`}
          d={s.d}
          style={{ '--oc': s.color ?? '#cccccc' } as CSSProperties}
          fillOpacity={s.alpha}
          vectorEffect="non-scaling-stroke"
          data-id={s.id ?? undefined}
        />
      )
    default:
      return s.inkFill
        ? <path className="atlas-deco ink" d={s.d} />
        : <path className={`atlas-deco${s.dashed ? ' dashed' : ''}`} d={s.d} vectorEffect="non-scaling-stroke" />
  }
}
