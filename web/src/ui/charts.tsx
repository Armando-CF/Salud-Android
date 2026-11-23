import { useMemo, useRef, useState, type PointerEvent } from 'react'
import { Fmt, today } from '../lib/dates'
import { Chips } from './components'

/** Punto de una gráfica. Para ejes de fecha, x = epochDay (puede llevar fracción de día). */
export interface ChartPoint { x: number; y: number }

export interface ChartSeries {
  name: string
  points: ChartPoint[]
  /** Color CSS (p. ej. "var(--chart-1)"). */
  color: string
  dots?: boolean
  dashed?: boolean
  strokeWidth?: number
  /** Rellenar suavemente bajo la línea. */
  fill?: boolean
  /** Si es false no participa en la selección al tocar (líneas de tendencia). */
  selectable?: boolean
}

/** Banda horizontal (rango de referencia). min/max null = abierto hacia ese lado. */
export interface ChartBand { min: number | null; max: number | null; color: string }
/** Línea horizontal (meta). */
export interface ChartHLine { y: number; color: string; label?: string }

/** Rangos de tiempo para filtrar gráficas. */
export const CHART_RANGES = [['m1', '1 mes'], ['m3', '3 meses'], ['m6', '6 meses'], ['y1', '1 año'], ['all', 'Todo']] as const
export type ChartRange = (typeof CHART_RANGES)[number][0]
const RANGE_DAYS: Record<ChartRange, number | null> = { m1: 30, m3: 90, m6: 180, y1: 365, all: null }
/** epochDay mínimo incluido en el rango, o null para todo. */
export const rangeFromDay = (r: ChartRange, t: number = today()): number | null => (RANGE_DAYS[r] === null ? null : t - (RANGE_DAYS[r] as number))
export const rangeLabel = (r: ChartRange): string => CHART_RANGES.find((x) => x[0] === r)![1]

export function RangeSelector(props: { value: ChartRange; onChange: (r: ChartRange) => void }) {
  return <Chips options={CHART_RANGES} value={props.value} onChange={(v) => v && props.onChange(v)} scroll />
}

function niceStep(range: number, target: number): number {
  if (range <= 0) return 1
  const raw = range / target
  const mag = Math.pow(10, Math.floor(Math.log10(raw)))
  const n = raw / mag
  return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 2.5 ? 2.5 : n <= 5 ? 5 : 10) * mag
}

/**
 * Gráfica de líneas con eje X continuo (por defecto fechas en epochDay). Tocar o arrastrar muestra el valor más cercano.
 */
export function LineChart(props: {
  series: ChartSeries[]
  bands?: ChartBand[]
  hLines?: ChartHLine[]
  xFormatter?: (x: number) => string
  yFormatter?: (y: number) => string
  yMin?: number
  yMax?: number
  unit?: string
  height?: number
}) {
  const W = 360
  const H = props.height ?? 220
  const xf = props.xFormatter ?? ((x: number) => Fmt.dateShort(Math.floor(x)))
  const yf = props.yFormatter ?? ((y: number) => Fmt.num(y, 1))
  const ref = useRef<SVGSVGElement>(null)
  const [touch, setTouch] = useState<number | null>(null)
  const all = useMemo(() => props.series.flatMap((s) => s.points), [props.series])
  if (all.length === 0) {
    return <div className="center muted" style={{ height: H, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>Aún no hay datos para graficar</div>
  }
  let loY = Math.min(...all.map((p) => p.y)), hiY = Math.max(...all.map((p) => p.y))
  for (const h of props.hLines ?? []) { loY = Math.min(loY, h.y); hiY = Math.max(hiY, h.y) }
  if (hiY - loY < 1e-9) { loY -= 1; hiY += 1 }
  const pad = (hiY - loY) * 0.12
  const step = niceStep(hiY - loY + 2 * pad, 4)
  const axLo = props.yMin ?? Math.floor((loY - pad) / step) * step
  const axHi = props.yMax ?? Math.ceil((hiY + pad) / step) * step
  let loX = Math.min(...all.map((p) => p.x)), hiX = Math.max(...all.map((p) => p.x))
  if (hiX - loX < 1e-9) { loX -= 1; hiX += 1 }
  const L = 38, R = 10, T = 12, B = 24
  const w = W - L - R, h = H - T - B
  const px = (x: number) => L + ((x - loX) / (hiX - loX)) * w
  const py = (y: number) => T + (1 - (y - axLo) / (axHi - axLo)) * h
  const ticks: number[] = []
  for (let t = Math.ceil(axLo / step) * step, i = 0; t <= axHi + 1e-9 && i < 12; t += step, i++) ticks.push(t)
  const xTicks = [0, 1, 2, 3].map((i) => loX + ((hiX - loX) * i) / 3)

  const onPointer = (e: PointerEvent<SVGSVGElement>) => {
    const r = ref.current?.getBoundingClientRect()
    if (!r) return
    setTouch(((e.clientX - r.left) / r.width) * W)
  }
  const selectable = props.series.filter((s) => s.selectable !== false && s.points.length > 0)
  let sel: { x: number; lines: { text: string; color?: string; y?: number }[] } | null = null
  if (touch !== null && selectable.length) {
    const pts = selectable.flatMap((s) => s.points)
    const nearest = pts.reduce((a, b) => (Math.abs(px(b.x) - touch) < Math.abs(px(a.x) - touch) ? b : a))
    const x = px(nearest.x)
    const lines: { text: string; color?: string; y?: number }[] = [{ text: xf(nearest.x) }]
    for (const s of selectable) {
      const p = s.points.reduce((a, b) => (Math.abs(b.x - nearest.x) < Math.abs(a.x - nearest.x) ? b : a))
      if (Math.abs(px(p.x) - x) > 1.5) continue
      const v = yf(p.y) + (props.unit ? ` ${props.unit}` : '')
      lines.push({ text: selectable.length > 1 ? `${s.name}: ${v}` : v, color: s.color, y: py(p.y) })
    }
    sel = { x, lines }
  }
  const boxW = sel ? Math.max(...sel.lines.map((l) => l.text.length)) * 6.3 + 16 : 0
  const boxX = sel ? Math.min(Math.max(sel.x - boxW / 2, 0), W - boxW) : 0

  return (
    <svg
      ref={ref}
      className="chart"
      viewBox={`0 0 ${W} ${H}`}
      role="img"
      aria-label="Gráfica"
      onPointerDown={onPointer}
      onPointerMove={(e) => (e.pointerType === 'mouse' ? e.buttons === 1 && onPointer(e) : onPointer(e))}
    >
      {(props.bands ?? []).map((b, i) => {
        const top = py(Math.min(Math.max(b.max ?? axHi, axLo), axHi)), bottom = py(Math.min(Math.max(b.min ?? axLo, axLo), axHi))
        return bottom > top ? <rect key={i} x={L} y={top} width={w} height={bottom - top} fill={b.color} /> : null
      })}
      {ticks.map((t) => (
        <g key={t}>
          <line x1={L} x2={L + w} y1={py(t)} y2={py(t)} stroke="var(--line)" strokeWidth={1} />
          <text x={L - 6} y={py(t) + 3.5} fontSize={10} textAnchor="end" fill="var(--muted)">{yf(t)}</text>
        </g>
      ))}
      {xTicks.map((x, i) => (
        <text key={i} x={px(x)} y={H - 6} fontSize={10} textAnchor={i === 0 ? 'start' : i === 3 ? 'end' : 'middle'} fill="var(--muted)">{xf(x)}</text>
      ))}
      {(props.hLines ?? []).map((hl, i) => (
        <g key={i}>
          <line x1={L} x2={L + w} y1={py(hl.y)} y2={py(hl.y)} stroke={hl.color} strokeWidth={1.6} strokeDasharray="6 5" />
          {hl.label && <text x={L + w - 4} y={py(hl.y) - 4} fontSize={10} fontWeight={700} textAnchor="end" fill={hl.color}>{hl.label}</text>}
        </g>
      ))}
      {props.series.map((s, si) => {
        const pts = [...s.points].sort((a, b) => a.x - b.x)
        if (!pts.length) return null
        const d = pts.map((p, i) => `${i ? 'L' : 'M'}${px(p.x).toFixed(1)} ${py(p.y).toFixed(1)}`).join('')
        return (
          <g key={si}>
            {s.fill && pts.length > 1 && <path d={`${d}L${px(pts[pts.length - 1].x).toFixed(1)} ${T + h}L${px(pts[0].x).toFixed(1)} ${T + h}Z`} fill={s.color} opacity={0.12} />}
            <path d={d} fill="none" stroke={s.color} strokeWidth={s.strokeWidth ?? 2.4} strokeLinecap="round" strokeLinejoin="round" strokeDasharray={s.dashed ? '7 6' : undefined} />
            {(s.dots ?? true) && pts.length <= 60 && pts.map((p, i) => <circle key={i} cx={px(p.x)} cy={py(p.y)} r={2.8} fill={s.color} stroke="var(--surface)" strokeWidth={1.2} />)}
            {pts.length === 1 && <circle cx={px(pts[0].x)} cy={py(pts[0].y)} r={3.6} fill={s.color} />}
          </g>
        )
      })}
      {sel && (
        <g pointerEvents="none">
          <line x1={sel.x} x2={sel.x} y1={T} y2={T + h} stroke="var(--muted)" strokeWidth={1.2} />
          {sel.lines.filter((l) => l.y !== undefined).map((l, i) => <circle key={i} cx={sel!.x} cy={l.y} r={5} fill={l.color} stroke="var(--surface)" strokeWidth={2} />)}
          <rect x={boxX} y={0} width={boxW} height={sel.lines.length * 14 + 8} rx={7} fill="var(--text)" />
          {sel.lines.map((l, i) => <text key={i} x={boxX + 8} y={14 + i * 14} fontSize={11} fontWeight={600} fill="var(--bg)">{l.text}</text>)}
        </g>
      )}
    </svg>
  )
}

/** Leyenda de colores para una gráfica. */
export function ChartLegend(props: { items: readonly (readonly [label: string, color: string])[] }) {
  return <div className="legend">{props.items.map(([l, c]) => <span key={l}><i style={{ background: c }} />{l}</span>)}</div>
}

export interface BarEntry { label: string; value: number; color?: string }

/** Barras verticales simples con etiqueta abajo y valor arriba. `reference` dibuja una línea (p. ej. promedio). */
export function BarChart(props: { bars: BarEntry[]; color?: string; valueFormatter?: (v: number) => string; reference?: number; height?: number }) {
  const W = 360, H = props.height ?? 180
  const vf = props.valueFormatter ?? ((v: number) => Fmt.num(v, 1))
  if (!props.bars.length) {
    return <div className="center muted" style={{ height: H, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>Aún no hay datos para graficar</div>
  }
  const max = Math.max(...props.bars.map((b) => b.value), props.reference ?? 0) || 1
  const T = 16, B = 18, h = H - T - B
  const slot = W / props.bars.length
  const bw = Math.min(slot * 0.62, 36)
  const every = Math.max(1, Math.ceil(props.bars.length / 8))
  return (
    <svg className="chart" viewBox={`0 0 ${W} ${H}`} role="img" aria-label="Gráfica de barras">
      {props.bars.map((b, i) => {
        const bh = Math.max(2, (b.value / max) * h), x = slot * i + (slot - bw) / 2, y = T + h - bh
        return (
          <g key={i}>
            <rect x={x} y={y} width={bw} height={bh} rx={5} fill={b.color ?? props.color ?? 'var(--primary)'} />
            {(props.bars.length <= 14 || i % every === 0) && <text x={x + bw / 2} y={y - 3} fontSize={10} textAnchor="middle" fill="var(--muted)">{vf(b.value)}</text>}
            {i % every === 0 && <text x={x + bw / 2} y={H - 5} fontSize={10} textAnchor="middle" fill="var(--muted)">{b.label}</text>}
          </g>
        )
      })}
      {props.reference !== undefined && <line x1={0} x2={W} y1={T + h - (props.reference / max) * h} y2={T + h - (props.reference / max) * h} stroke="var(--outline)" strokeWidth={1.6} strokeDasharray="6 6" />}
    </svg>
  )
}

/** Lista de barras horizontales (p. ej. "síntomas más frecuentes"). */
export function HBarList(props: { items: readonly (readonly [label: string, count: number])[]; color?: string }) {
  const max = Math.max(1, ...props.items.map((i) => i[1]))
  return (
    <div className="stack" style={{ gap: 9 }}>
      {props.items.map(([label, count]) => (
        <div key={label}>
          <div className="row" style={{ marginBottom: 3 }}><span className="grow ellipsis">{label}</span><b>{count}</b></div>
          <div className="hbar"><div style={{ width: `${(count / max) * 100}%`, background: props.color ?? 'var(--primary)' }} /></div>
        </div>
      ))}
    </div>
  )
}

/** Mini gráfica sin ejes para tarjetas de resumen. */
export function Sparkline(props: { values: number[]; color?: string; width?: number; height?: number }) {
  const W = props.width ?? 96, H = props.height ?? 36
  const v = props.values
  if (v.length < 2) return null
  const lo = Math.min(...v), hi = Math.max(...v), span = hi - lo || 1
  const y = (x: number) => H * (1 - (x - lo) / span) * 0.8 + H * 0.1
  const d = v.map((x, i) => `${i ? 'L' : 'M'}${((W - 4) * i / (v.length - 1)).toFixed(1)} ${y(x).toFixed(1)}`).join('')
  return (
    <svg width={W} height={H} viewBox={`0 0 ${W} ${H}`} aria-hidden>
      <path d={d} fill="none" stroke={props.color ?? 'var(--primary)'} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" />
      <circle cx={W - 4} cy={y(v[v.length - 1])} r={3} fill={props.color ?? 'var(--primary)'} />
    </svg>
  )
}
