import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useLiveQuery } from 'dexie-react-hooks'
import { ChevronRight, Microscope, Plus, Search, SearchX, X } from 'lucide-react'
import { StudyCategories } from '../../data/catalogs'
import { useDb } from '../../data/profiles'
import type { Study, StudyResult } from '../../data/types'
import { Fmt, epochDayToDate, today } from '../../lib/dates'
import { Button, Card, EmptyState, Fab, IconButton, InfoNote, Page, Pill, Segmented, Sparkline } from '../../ui'
import {
  flagLabel, normalizeName, outOfRange, paramPath, paramSummaries, resultFlag, upcomingFollowUps, valueText,
} from './catalog'
import { VACCINE, useStudyProfile, type StudyProfile } from './profile'
import { CategoryBadge, StatusPill, VaccinesMovedNote, flagColor } from './shared'

// Se recuerda la pestaña y el filtro al volver del detalle (la pantalla se vuelve a montar al navegar).
const view = { tab: 0 as 0 | 1, category: null as string | null, query: '', searching: false }

/** Lista de estudios y chequeos, con pestaña de evolución de parámetros. */
export function StudiesPage() {
  const nav = useNavigate()
  const db = useDb()
  const sp = useStudyProfile()
  const studies = useLiveQuery(() => db.studies.toArray(), [db])
  const results = useLiveQuery(() => db.studyResults.toArray(), [db]) ?? []
  const [tab, setTabState] = useState<0 | 1>(view.tab)
  const [searching, setSearchingState] = useState(view.searching)
  const [query, setQueryState] = useState(view.query)
  const [category, setCategoryState] = useState<string | null>(view.category)
  const setTab = (t: 0 | 1) => { view.tab = t; setTabState(t) }
  const setQuery = (q: string) => { view.query = q; setQueryState(q) }
  const setCategory = (c: string | null) => { view.category = c; setCategoryState(c) }
  const setSearching = (s: boolean) => { view.searching = s; setSearchingState(s); if (!s) setQuery('') }

  const sorted = useMemo(() => (studies ? [...studies].sort((a, b) => b.epochDay - a.epochDay || (b.id ?? 0) - (a.id ?? 0)) : null), [studies])

  return (
    <Page
      title="Estudios y chequeos"
      actions={tab === 0 && sorted && sorted.length > 0 ? (
        <IconButton icon={searching ? SearchX : Search} label={searching ? 'Cerrar búsqueda' : 'Buscar'} onClick={() => setSearching(!searching)} />
      ) : undefined}
      fab={<Fab icon={Plus} label="Nuevo estudio" onClick={() => nav('/estudios/nuevo')} />}
    >
      <div className="st-page">
        <Segmented options={[[0, 'Estudios'], [1, 'Parámetros']] as const} value={tab} onChange={(t) => setTab(t as 0 | 1)} />
        {sorted === null ? null : tab === 0 ? (
          <StudyList sp={sp} studies={sorted} results={results} searching={searching} query={query} onQuery={setQuery} category={category} onCategory={setCategory} />
        ) : (
          <ParamList studies={sorted} results={results} />
        )}
      </div>
    </Page>
  )
}

function StudyList(props: {
  sp: StudyProfile
  studies: Study[]
  results: StudyResult[]
  searching: boolean
  query: string
  onQuery: (q: string) => void
  category: string | null
  onCategory: (c: string | null) => void
}) {
  const { sp, studies, results } = props
  const nav = useNavigate()
  const db = useDb()
  const t = today()
  const byStudy = useMemo(() => {
    const m = new Map<number, StudyResult[]>()
    for (const r of results) m.set(r.studyId, [...(m.get(r.studyId) ?? []), r])
    return m
  }, [results])
  const categories = useMemo(() => {
    const used = new Set(studies.map((s) => s.category))
    return StudyCategories.all.map((o) => o[0]).filter((k) => used.has(k))
  }, [studies])
  const activeCategory = props.category && categories.includes(props.category) ? props.category : null
  const q = normalizeName(props.query)
  const filtered = useMemo(
    () => studies.filter((s) =>
      (activeCategory === null || s.category === activeCategory) &&
      (q === '' ||
        [s.title, s.place, s.doctor, s.summary, StudyCategories.label(s.category)].some((x) => normalizeName(x).includes(q)) ||
        (byStudy.get(s.id as number) ?? []).some((r) => normalizeName(r.name).includes(q)))),
    [studies, byStudy, activeCategory, q],
  )
  const followUps = useMemo(() => upcomingFollowUps(studies, t), [studies, t])
  const years = useMemo(() => {
    const m = new Map<number, Study[]>()
    for (const s of filtered) {
      const y = epochDayToDate(s.epochDay).getFullYear()
      m.set(y, [...(m.get(y) ?? []), s])
    }
    return [...m.entries()].sort((a, b) => b[0] - a[0])
  }, [filtered])

  if (studies.length === 0) {
    return (
      <EmptyState
        icon={Microscope}
        title="Aún no hay estudios"
        message={sp.isPet
          ? 'Guarda aquí sus análisis, radiografías y consultas veterinarias con sus resultados para ver cómo evolucionan con el tiempo.'
          : 'Guarda aquí tus análisis, radiografías y chequeos con sus resultados para ver cómo evolucionan con el tiempo.'}
        actionLabel="Agregar estudio"
        onAction={() => nav('/estudios/nuevo')}
      />
    )
  }

  return (
    <>
      {props.searching && (
        <div className="st-search">
          <Search size={20} className="lead" />
          <input
            className="input"
            type="search"
            value={props.query}
            placeholder="Buscar por título, lugar, parámetro…"
            autoComplete="off"
            enterKeyHint="search"
            aria-label="Buscar estudios"
            onChange={(e) => props.onQuery(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter') (e.target as HTMLInputElement).blur() }}
          />
          {props.query !== '' && (
            <button type="button" className="icon-btn clear" aria-label="Borrar búsqueda" onClick={() => props.onQuery('')}><X size={20} /></button>
          )}
        </div>
      )}
      {categories.length > 1 && (
        <div className="chips scroll" role="group" aria-label="Filtrar por tipo">
          <button type="button" className={`chip${activeCategory === null ? ' on' : ''}`} onClick={() => props.onCategory(null)}>Todos</button>
          {categories.map((c) => (
            <button key={c} type="button" className={`chip${activeCategory === c ? ' on' : ''}`} onClick={() => props.onCategory(activeCategory === c ? null : c)}>
              {sp.categoryShort(c)}
            </button>
          ))}
        </div>
      )}
      {activeCategory === VACCINE && <VaccinesMovedNote />}
      {followUps.length > 0 && q === '' && activeCategory === null && (
        <Card title="Seguimientos próximos" className="st-soft">
          {followUps.map((s, i) => {
            const day = s.followUpEpochDay as number
            const overdue = day < t
            return (
              <div key={s.id}>
                {i > 0 && <div className="divider" />}
                <div className="list-row tap" onClick={() => nav(`/estudios/${s.id}`)}>
                  <div className="grow">
                    <div className="ellipsis" style={{ fontWeight: 600 }}>{s.title}</div>
                    <div className="small" style={{ color: overdue ? 'var(--warn)' : 'var(--muted)', fontWeight: overdue ? 700 : 400 }}>
                      {Fmt.date(day)} · {Fmt.daysFromNow(day)}{overdue ? ' (vencido)' : ''}
                    </div>
                  </div>
                  <span onClick={(e) => e.stopPropagation()}>
                    <Button variant="text" small onClick={() => { void db.studies.update(s.id as number, { followUpEpochDay: null }) }}>Hecho</Button>
                  </span>
                </div>
              </div>
            )
          })}
        </Card>
      )}
      {filtered.length === 0 && (
        <EmptyState icon={SearchX} title="Sin coincidencias" message="Ningún estudio coincide con el filtro o la búsqueda." />
      )}
      {years.map(([year, items]) => (
        <div key={year} className="stack" style={{ gap: 10 }}>
          <div className="st-year">{year}</div>
          {items.map((s) => (
            <StudyCard key={s.id} study={s} results={byStudy.get(s.id as number) ?? []} categoryLabel={sp.categoryShort(s.category)} onClick={() => nav(`/estudios/${s.id}`)} />
          ))}
        </div>
      ))}
    </>
  )
}

function StudyCard(props: { study: Study; results: StudyResult[]; categoryLabel: string; onClick: () => void }) {
  const { study, results } = props
  const out = results.filter(outOfRange).length
  const where = [study.place, study.doctor].filter((x) => x.trim() !== '').join(' · ')
  return (
    <Card onClick={props.onClick}>
      <div className="st-card-row">
        <CategoryBadge category={study.category} />
        <div className="grow">
          <div className="st-title">{study.title}</div>
          <div className="small muted">{Fmt.date(study.epochDay)} · {props.categoryLabel}</div>
          {where && <div className="small muted ellipsis">{where}</div>}
          {(study.status !== '' || results.length > 0) && (
            <div className="st-pills" style={{ marginTop: 8 }}>
              <StatusPill status={study.status} />
              {out > 0 && <Pill text={out === 1 ? '1 fuera de rango' : `${out} fuera de rango`} color="var(--warn)" />}
              {results.length > 0 && <Pill text={results.length === 1 ? '1 resultado' : `${results.length} resultados`} color="var(--muted)" />}
            </div>
          )}
        </div>
        <ChevronRight size={20} color="var(--muted)" style={{ flex: '0 0 auto', marginTop: 2 }} />
      </div>
    </Card>
  )
}

/** Pestaña "Parámetros": cada parámetro numérico con su último valor; tocar abre su evolución. */
function ParamList(props: { studies: Study[]; results: StudyResult[] }) {
  const nav = useNavigate()
  const summaries = useMemo(() => paramSummaries(props.studies, props.results), [props.studies, props.results])
  if (summaries.length === 0) {
    return (
      <EmptyState
        icon={Microscope}
        title="Sin parámetros todavía"
        message="Cuando captures resultados numéricos en tus estudios (glucosa, colesterol, hemoglobina…) aquí verás la evolución de cada uno."
      />
    )
  }
  return (
    <>
      <InfoNote>Evolución de cada parámetro a través de tus estudios. Los rangos son orientativos; usa los de tu laboratorio.</InfoNote>
      {summaries.map((p) => {
        const last = p.latest
        const f = resultFlag(last.result)
        const off = f === 'ALTO' || f === 'BAJO'
        return (
          <Card key={normalizeName(p.name)} onClick={() => nav(paramPath(p.name))}>
            <div className="row">
              <div className="grow">
                <div className="st-title ellipsis">{p.name}</div>
                <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
                  <b style={{ color: off ? flagColor(f) : undefined }}>{valueText(last.result)}</b>
                  {f !== 'SIN_RANGO' && <Pill text={flagLabel(f)} color={flagColor(f)} />}
                </div>
                <div className="small muted">{Fmt.date(last.epochDay)} · {p.points.length === 1 ? '1 valor' : `${p.points.length} valores`}</div>
              </div>
              {p.points.length >= 2 && <Sparkline values={p.points.map((x) => x.value)} width={72} height={32} />}
              <ChevronRight size={20} color="var(--muted)" style={{ flex: '0 0 auto' }} />
            </div>
          </Card>
        )
      })}
    </>
  )
}
