// Estado compartido, acciones y piezas visuales del módulo de ciclo (port de CycleCommon.kt).
import { useMemo, type ReactNode } from 'react'
import { useLiveQuery } from 'dexie-react-hooks'
import { useNavigate } from 'react-router-dom'
import { ChevronRight, Users } from 'lucide-react'
import type { SaviaDB } from '../../data/db'
import { usePref } from '../../data/prefs'
import { useDb } from '../../data/profiles'
import { emptyCycleDay, type CycleDay } from '../../data/types'
import { today } from '../../lib/dates'
import { Card, EmptyState, Page } from '../../ui'
import { analyze, type CycleAnalysis } from './engine'
import { todayStatus, withFlow, type TodayStatus } from './logic'
import './cycle.css'

/** Todo lo que las pantallas del ciclo necesitan saber, ya calculado. */
export interface CycleUiState {
  /** false mientras llega la primera lectura de la base (evita parpadear el estado vacío). */
  loaded: boolean
  days: CycleDay[]
  byDay: Map<number, CycleDay>
  analysis: CycleAnalysis
  today: number
  /** Situación de hoy lista para mostrarse (día del ciclo, mensaje principal…). */
  status: TodayStatus
}

/** Observa los registros y ajustes del ciclo del perfil activo y devuelve el análisis vigente. */
export function useCycleState(): CycleUiState {
  const db = useDb()
  const days = useLiveQuery(() => db.cycleDays.orderBy('epochDay').toArray(), [db])
  const [cycleLen] = usePref('cycleLength')
  const [periodLen] = usePref('periodLength')
  const [luteal] = usePref('lutealLength')
  const t = today()
  return useMemo(() => {
    const list = days ?? []
    const analysis = analyze(list, cycleLen, periodLen, luteal, t)
    return { loaded: days !== undefined, days: list, byDay: new Map(list.map((d) => [d.epochDay, d])), analysis, today: t, status: todayStatus(analysis) }
  }, [days, cycleLen, periodLen, luteal, t])
}

/** Cambia el flujo de un día conservando lo demás; borra la fila si queda vacía. */
export async function setFlow(db: SaviaDB, day: number, flow: number): Promise<void> {
  await db.transaction('rw', db.cycleDays, async () => {
    const n = withFlow(await db.cycleDays.get(day), day, flow)
    if (n) await db.cycleDays.put(n)
    else await db.cycleDays.delete(day)
  })
}

/** Marca varios días seguidos con flujo moderado (sin pisar un flujo ya registrado). */
export async function markPeriod(db: SaviaDB, start: number, length: number, lastAllowed: number): Promise<void> {
  await db.transaction('rw', db.cycleDays, async () => {
    for (let d = start; d < start + length; d++) {
      if (d > lastAllowed) break
      const existing = await db.cycleDays.get(d)
      if ((existing?.flow ?? 0) >= 2) continue
      await db.cycleDays.put({ ...(existing ?? emptyCycleDay(d)), flow: 3 })
    }
  })
}

const rad = (deg: number): number => (deg * Math.PI) / 180

function arcPath(cx: number, cy: number, r: number, startDeg: number, sweepDeg: number): string {
  const a0 = rad(startDeg), a1 = rad(startDeg + sweepDeg)
  const large = sweepDeg > 180 ? 1 : 0
  return `M${(cx + r * Math.cos(a0)).toFixed(2)} ${(cy + r * Math.sin(a0)).toFixed(2)}A${r.toFixed(2)} ${r.toFixed(2)} 0 ${large} 1 ${(cx + r * Math.cos(a1)).toFixed(2)} ${(cy + r * Math.sin(a1)).toFixed(2)}`
}

/**
 * Anillo del ciclo: una vuelta = un ciclo. Marca los días de periodo, la ventana fértil,
 * la ovulación estimada y la posición de hoy. El contenido central lo pone quien llama.
 */
export function CycleRing(props: { analysis: CycleAnalysis; cycleDay: number | null; showFertility: boolean; size?: number; stroke?: number; children?: ReactNode }) {
  const { analysis: a, cycleDay, showFertility } = props
  const size = props.size ?? 196
  const sw = props.stroke ?? 16
  const inset = sw / 2 + 6
  const r = (size - inset * 2) / 2
  const c = size / 2
  const total = Math.max(a.cycleLength, 1)
  const per = 360 / total
  const pointAt = (dayPos: number): [number, number] => {
    const ang = rad(-90 + dayPos * per)
    return [c + r * Math.cos(ang), c + r * Math.sin(ang)]
  }
  // Ventana fértil y ovulación (día de ovulación = duración − fase lútea + 1)
  const ovDay = total - a.lutealLength + 1
  const showFert = showFertility && a.hasData && ovDay - 5 > 1
  const startDay = Math.max(ovDay - 5, 1)
  const endDay = Math.min(ovDay + 1, total)
  const p = Math.min(Math.max(a.periodLength, 1), total)
  const ov = pointAt(ovDay - 0.5)
  const now = cycleDay !== null ? pointAt(Math.min(Math.max(cycleDay, 1), total) - 0.5) : null
  return (
    <div className="cy-ring" style={{ width: size, height: size }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} aria-hidden>
        <circle cx={c} cy={c} r={r} fill="none" stroke="var(--surface-3)" strokeWidth={sw} />
        {showFert && <path d={arcPath(c, c, r, -90 + (startDay - 1) * per + 1, (endDay - startDay + 1) * per - 2)} fill="none" stroke="var(--fertile)" strokeWidth={sw} />}
        {a.hasData && <path d={arcPath(c, c, r, -90, Math.min(p * per - 1, 359.5))} fill="none" stroke="var(--period)" strokeWidth={sw} />}
        {showFert && (
          <>
            <circle cx={ov[0]} cy={ov[1]} r={sw * 0.36} fill="var(--surface)" />
            <circle cx={ov[0]} cy={ov[1]} r={sw * 0.26} fill="var(--ovulation)" />
          </>
        )}
        {now && <circle cx={now[0]} cy={now[1]} r={sw * 0.82} fill="var(--surface)" stroke="var(--text)" strokeWidth={3} />}
      </svg>
      <div className="cy-ring-center">{props.children}</div>
    </div>
  )
}

/**
 * Tarjeta-resumen del ciclo para la pantalla de inicio: anillo pequeño con el día del ciclo,
 * mensaje principal ("Periodo en 5 días") y detalle. Al tocarla abre /ciclo (o llama a `onClick`).
 * No muestra nada mientras carga.
 */
export function CycleSummaryCard(props: { onClick?: () => void }) {
  const state = useCycleState()
  const [showFert] = usePref('showFertility')
  const nav = useNavigate()
  if (!state.loaded) return null
  const s = state.status
  return (
    <Card onClick={props.onClick ?? (() => nav('/ciclo'))}>
      <div className="row" style={{ gap: 14 }}>
        <CycleRing analysis={state.analysis} cycleDay={s.cycleDay} showFertility={showFert} size={76} stroke={8}>
          <span style={{ fontSize: 22, fontWeight: 800 }}>{s.cycleDay ?? '–'}</span>
        </CycleRing>
        <div className="grow">
          <div className="ellipsis" style={{ color: 'var(--period)', fontSize: 14, fontWeight: 700 }}>{s.cycleDay !== null ? `Ciclo · ${s.phaseLabel}` : 'Ciclo'}</div>
          <div style={{ fontSize: 17, fontWeight: 700 }}>{s.headline}</div>
          {s.detail && <div className="small muted">{s.detail}</div>}
        </div>
        <ChevronRight size={20} color="var(--muted)" />
      </div>
    </Card>
  )
}

/** Pantalla para perfiles de mascota: el ciclo es solo para personas. */
export function PeopleOnly(props: { title: string }) {
  return (
    <Page title={props.title}>
      <EmptyState
        icon={Users}
        title="Esta sección es para perfiles de personas"
        message="El seguimiento del ciclo menstrual no aplica a las mascotas. Cambia a un perfil de persona para usarlo."
      />
    </Page>
  )
}
