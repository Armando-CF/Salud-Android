import { useMemo } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useLiveQuery } from 'dexie-react-hooks'
import { ChartNoAxesCombined, ChevronRight } from 'lucide-react'
import { useDb } from '../../data/profiles'
import { Fmt } from '../../lib/dates'
import { Card, ChartLegend, EmptyState, InfoNote, LineChart, Page, Pill, StatTile, type ChartHLine } from '../../ui'
import { flagLabel, labNum, paramFromRoute, paramSeries, rangeText, resultFlag, valueText, type ParamPoint } from './catalog'
import { useStudyProfile } from './profile'
import { RangeBar, flagColor } from './shared'

/** Evolución de un parámetro a través de todos los estudios. Ruta /parametro/:name. */
export function ParamTrendPage() {
  const params = useParams()
  const name = paramFromRoute(params.name)
  const db = useDb()
  const studies = useLiveQuery(() => db.studies.toArray(), [db])
  const results = useLiveQuery(() => db.studyResults.toArray(), [db])
  const points = useMemo(() => (studies && results ? paramSeries(name, studies, results) : null), [name, studies, results])
  const displayName = points?.length ? points[points.length - 1].result.name.trim() || name : name
  return (
    <Page title={displayName || 'Parámetro'} subtitle="Evolución del parámetro">
      {points === null ? null : points.length === 0 ? (
        <EmptyState icon={ChartNoAxesCombined} title="Sin valores" message={`Todavía no hay resultados numéricos de "${name}" en tus estudios.`} />
      ) : (
        <div className="st-page"><TrendContent points={points} /></div>
      )}
    </Page>
  )
}

function TrendContent({ points }: { points: ParamPoint[] }) {
  const nav = useNavigate()
  const sp = useStudyProfile()
  const latest = points[points.length - 1]
  const previous = points.length > 1 ? points[points.length - 2] : null
  const unit = latest.result.unit
  const refMin = latest.result.refMin
  const refMax = latest.result.refMax
  const f = resultFlag(latest.result)
  const off = f === 'ALTO' || f === 'BAJO'
  const values = points.map((p) => p.value)
  const lo = Math.min(...values), hi = Math.max(...values)
  const decimals = Math.max(Math.abs(lo), Math.abs(hi)) < 10 ? 2 : 1
  const withUnit = (v: number) => labNum(v) + (unit.trim() ? ` ${unit}` : '')
  const minPoint = points.reduce((a, b) => (b.value < a.value ? b : a))
  const maxPoint = points.reduce((a, b) => (b.value > a.value ? b : a))
  const range = rangeText(refMin, refMax)

  // Los límites de referencia se dibujan como líneas solo si quedan cerca de los datos,
  // para no aplastar la gráfica cuando el rango es muy amplio.
  const span = Math.max(hi - lo, Math.abs(hi) * 0.1, 1e-6)
  const near = (v: number) => v >= lo - 1.5 * span && v <= hi + 1.5 * span
  const hLines: ChartHLine[] = []
  if (refMax != null && near(refMax)) hLines.push({ y: refMax, color: 'var(--good)', label: `Máx. ${labNum(refMax)}` })
  if (refMin != null && near(refMin)) hLines.push({ y: refMin, color: 'var(--good)', label: `Mín. ${labNum(refMin)}` })

  return (
    <>
      <Card>
        <div className="stats">
          <StatTile label="Último" value={withUnit(latest.value)} sub={Fmt.date(latest.epochDay)} color={off ? flagColor(f) : undefined} />
          <StatTile
            label="Cambio"
            value={previous ? Fmt.signed(latest.value - previous.value, 3) : '—'}
            sub={previous ? `vs. ${Fmt.date(previous.epochDay)}` : 'Sin valor anterior'}
          />
        </div>
        <div className="stats">
          <StatTile label="Mínimo" value={withUnit(lo)} sub={Fmt.date(minPoint.epochDay)} />
          <StatTile label="Máximo" value={withUnit(hi)} sub={Fmt.date(maxPoint.epochDay)} />
        </div>
        {range !== '' && (
          <>
            <div className="row">
              <span className="grow muted" style={{ fontSize: 15 }}>Referencia: {range}{unit.trim() ? ` ${unit}` : ''}</span>
              <Pill text={flagLabel(f)} color={flagColor(f)} />
            </div>
            <RangeBar value={latest.value} refMin={refMin} refMax={refMax} />
          </>
        )}
      </Card>

      <Card title="Evolución">
        <LineChart
          series={[{ name: latest.result.name, points: points.map((p) => ({ x: p.epochDay, y: p.value })), color: 'var(--primary)' }]}
          bands={refMin != null || refMax != null ? [{ min: refMin, max: refMax, color: 'color-mix(in srgb, var(--good) 13%, transparent)' }] : []}
          hLines={hLines}
          xFormatter={(x) => Fmt.date(Math.floor(x)).replace(/^\S+\s/, '')}
          yFormatter={(y) => Fmt.num(y, decimals)}
          unit={unit}
        />
        <ChartLegend items={[[latest.result.name, 'var(--primary)'] as const, ...(refMin != null || refMax != null ? [['Rango de referencia', 'var(--good)'] as const] : [])]} />
        {points.length === 1 && (
          <p className="small muted">Con un solo valor aún no hay tendencia. Cuando registres otro estudio con este parámetro verás la línea.</p>
        )}
      </Card>

      <Card title="Valores">
        <div>
          {[...points].reverse().map((p, i) => {
            const pf = resultFlag(p.result)
            return (
              <div key={p.result.id}>
                {i > 0 && <div className="divider" />}
                <div className="list-row tap" onClick={() => nav(`/estudios/${p.study.id}`)} style={{ minHeight: 48 }}>
                  <div className="grow">
                    <div style={{ fontWeight: 600 }}>{Fmt.date(p.epochDay)}</div>
                    <div className="small muted ellipsis">{p.study.title}</div>
                  </div>
                  <div style={{ textAlign: 'right', display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 3 }}>
                    <b style={{ fontSize: 17, color: pf === 'ALTO' || pf === 'BAJO' ? flagColor(pf) : undefined }}>{valueText(p.result)}</b>
                    {pf !== 'SIN_RANGO' && <Pill text={flagLabel(pf)} color={flagColor(pf)} />}
                  </div>
                  <ChevronRight size={20} color="var(--muted)" style={{ flex: '0 0 auto' }} />
                </div>
              </div>
            )
          })}
        </div>
      </Card>

      <InfoNote>
        Los rangos de referencia son orientativos y pueden variar entre laboratorios; usa los de tu reporte. Esta gráfica no sustituye la valoración de {sp.whoToAsk}.
      </InfoNote>
    </>
  )
}
