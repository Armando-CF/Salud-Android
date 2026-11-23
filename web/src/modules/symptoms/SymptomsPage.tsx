import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useLiveQuery } from 'dexie-react-hooks'
import { NotebookPen, PersonStanding, Plus, Search, X } from 'lucide-react'
import { useDb } from '../../data/profiles'
import type { SymptomEntry } from '../../data/types'
import { Fmt, epochDayToDate, millisToEpochDay, today } from '../../lib/dates'
import {
  BarChart, Card, Chips, EmptyState, Fab, HBarList, InfoNote, Page, Pill, RangeSelector, Segmented, StatTile, intensityColor,
  rangeFromDay, type ChartRange,
} from '../../ui'
import { useSymptomKit, type SymptomKit } from './kit'
import { SymptomStats } from './stats'
import './symptoms.css'

/** Círculo con la intensidad (1–10) coloreado según su gravedad. */
export function IntensityBadge(props: { intensity: number }) {
  const c = intensityColor(props.intensity)
  return (
    <span className="sym-badge" style={{ background: `color-mix(in srgb, ${c} 17%, transparent)`, color: c }} aria-label={`Intensidad ${props.intensity} de 10`}>
      {props.intensity}
    </span>
  )
}

/**
 * Tarjeta de un síntoma registrado (reutilizable, p. ej. en la pantalla de inicio).
 * `showDate` muestra "Hoy, 14:05" en vez de solo la hora.
 */
export function SymptomListItem(props: { entry: SymptomEntry; onClick: () => void; showDate?: boolean; kit?: SymptomKit }) {
  const active = useSymptomKit()
  const kit = props.kit ?? active
  const e = props.entry
  const meta = [
    props.showDate ? Fmt.relativeDateTime(e.timestamp) : Fmt.time(e.timestamp),
    kit.intensityLabel(e.intensity),
    ...(e.durationMin ? [`duró ${Fmt.duration(e.durationMin)}`] : []),
  ].join(' · ')
  const tags = SymptomStats.splitTags(e.tags)
  const region = e.regionLabel?.trim() || null
  return (
    <Card onClick={props.onClick}>
      <div className="sym-item">
        <IntensityBadge intensity={e.intensity} />
        <div className="sym-main">
          <div className="sym-name">{e.name}</div>
          <div className="sym-meta">{meta}</div>
          {(region || tags.length > 0) && (
            <div className="sym-tags">
              {region && (
                <span className="sym-region" title="Zona del cuerpo">
                  <PersonStanding size={16} aria-label="Zona del cuerpo" />
                  <Pill text={region} color="var(--primary)" />
                </span>
              )}
              {tags.map((t) => <Pill key={t} text={t} color="var(--muted)" />)}
            </div>
          )}
          {e.notes.trim() !== '' && <div className="sym-notes">{e.notes}</div>}
        </div>
      </div>
    </Card>
  )
}

/** Historial y resumen de síntomas (pestaña de primer nivel). */
export function SymptomsPage() {
  const db = useDb()
  const nav = useNavigate()
  const kit = useSymptomKit()
  const entries = useLiveQuery(() => db.symptoms.orderBy('timestamp').reverse().toArray(), [db])
  const [tab, setTab] = useState<'history' | 'summary'>('history')
  const add = () => nav('/sintomas/nuevo')

  return (
    <Page title="Síntomas" fab={<Fab icon={Plus} label="Registrar" onClick={add} />}>
      {entries === undefined ? null : entries.length === 0 ? (
        <EmptyState icon={NotebookPen} title="Aún no registras síntomas" message={kit.emptyMessage} actionLabel="Registrar un síntoma" onAction={add} />
      ) : (
        <>
          <Segmented options={[['history', 'Historial'], ['summary', 'Resumen']] as const} value={tab} onChange={setTab} />
          {tab === 'history' ? <HistoryTab list={entries} kit={kit} /> : <SummaryTab list={entries} kit={kit} />}
        </>
      )}
    </Page>
  )
}

function HistoryTab({ list, kit }: { list: SymptomEntry[]; kit: SymptomKit }) {
  const nav = useNavigate()
  const [query, setQuery] = useState('')
  const [nameFilter, setNameFilter] = useState<string | null>(null)
  const topNames = useMemo(() => SymptomStats.usedNames(list).slice(0, 6), [list])
  const filtered = useMemo(
    () => list.filter((e) => (nameFilter === null || e.name === nameFilter) && SymptomStats.matches(e, query)),
    [list, query, nameFilter],
  )
  const grouped = useMemo(() => {
    const out: [number, SymptomEntry[]][] = []
    for (const e of filtered) {
      const d = millisToEpochDay(e.timestamp)
      const last = out[out.length - 1]
      if (last && last[0] === d) last[1].push(e)
      else out.push([d, [e]])
    }
    return out
  }, [filtered])

  return (
    <>
      <div className="sym-search">
        <Search size={20} className="sym-search-ic" aria-hidden />
        <input
          className="input"
          type="search"
          value={query}
          placeholder="Buscar por síntoma, zona o nota"
          aria-label="Buscar por síntoma, zona o nota"
          autoComplete="off"
          autoCapitalize="none"
          enterKeyHint="search"
          onChange={(e) => setQuery(e.target.value)}
          // La tecla "Buscar" del teclado lo cierra para dejar ver los resultados.
          onKeyDown={(e) => { if (e.key === 'Enter') (e.target as HTMLInputElement).blur() }}
        />
        {query !== '' && (
          <button type="button" className="icon-btn sym-clear" aria-label="Borrar búsqueda" onClick={() => setQuery('')}><X size={20} /></button>
        )}
      </div>
      {topNames.length > 1 && (
        <Chips options={topNames.map((n) => [n, n] as const)} value={nameFilter} onChange={setNameFilter} allowDeselect scroll />
      )}
      {filtered.length === 0 && <p className="sym-none">No hay registros que coincidan con tu búsqueda.</p>}
      {grouped.map(([day, items]) => (
        <div key={day} className="stack" style={{ gap: 8 }}>
          <DayHeader day={day} count={items.length} />
          {items.map((e) => <SymptomListItem key={e.id} entry={e} kit={kit} onClick={() => nav(`/sintomas/${e.id}`)} />)}
        </div>
      ))}
    </>
  )
}

function DayHeader({ day, count }: { day: number; count: number }) {
  const t = today()
  const year = epochDayToDate(day).getFullYear()
  const weekday = Fmt.dateWeekday(day) + (year !== epochDayToDate(t).getFullYear() ? ` ${year}` : '')
  const title = day === t ? `Hoy · ${weekday}` : day === t - 1 ? `Ayer · ${weekday}` : weekday.charAt(0).toUpperCase() + weekday.slice(1)
  return (
    <div className="sym-day">
      <span>{title}</span>
      {count > 1 && <span className="n">{count} registros</span>}
    </div>
  )
}

function SummaryTab({ list, kit }: { list: SymptomEntry[]; kit: SymptomKit }) {
  const [range, setRange] = useState<ChartRange>('m3')
  const t = today()
  const s = useMemo(() => SymptomStats.summarize(list, rangeFromDay(range, t), t), [list, range, t])
  const avg = s.avgIntensity ?? 0

  return (
    <>
      <RangeSelector value={range} onChange={setRange} />
      {s.total === 0 ? (
        <p className="sym-none">No hay registros en este periodo. Prueba con un rango más amplio.</p>
      ) : (
        <>
          <div className="stats">
            <StatTile label="Registros" value={`${s.total}`} sub={s.total === 1 ? 'síntoma' : 'síntomas'} />
            <StatTile label="Intensidad" value={Fmt.num(avg, 1)} sub="promedio de 10" color={intensityColor(Math.round(avg))} />
            <StatTile label="Días" value={`${s.daysWithSymptoms}`} sub="con molestias" />
          </div>
          <Card title="Más frecuentes"><HBarList items={s.topNames} /></Card>
          <Card title={s.monthly ? 'Registros por mes' : 'Registros por semana'}>
            <BarChart bars={s.buckets.map(([label, value]) => ({ label, value }))} valueFormatter={(v) => (v === 0 ? '' : Fmt.num(v, 0))} />
            {!s.monthly && <p className="small muted">Cada barra es una semana; la fecha indica el lunes.</p>}
          </Card>
          <Card title="Más intensos">
            {s.strongest.map(([name, a]) => (
              <div key={name} className="sym-strong">
                <span className="sym-strong-name">{name}</span>
                <b style={{ color: intensityColor(Math.round(a)) }}>{Fmt.num(a, 1)} · {kit.intensityLabel(Math.round(a))}</b>
              </div>
            ))}
            <p className="small muted">Intensidad promedio de cada síntoma, de 1 a 10.</p>
          </Card>
          {s.topRegions.length > 0 && <Card title="Zonas del cuerpo"><HBarList items={s.topRegions} color="var(--chart-3)" /></Card>}
          {s.topTriggers.length > 0 && <Card title="Posibles desencadenantes"><HBarList items={s.topTriggers} color="var(--chart-4)" /></Card>}
          <InfoNote>
            {kit.isPet
              ? 'Este resumen es orientativo y se basa solo en lo que has registrado. Si un síntoma se repite, empeora o te preocupa, coméntalo con tu veterinaria o veterinario.'
              : 'Este resumen es orientativo y se basa solo en lo que has registrado. Si un síntoma se repite, empeora o te preocupa, coméntalo con un profesional de la salud.'}
          </InfoNote>
        </>
      )}
    </>
  )
}
