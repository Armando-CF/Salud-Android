// Pantalla principal del ciclo: encabezado, acciones rápidas, calendario y registro de hoy (port de CycleScreen.kt).
import { useRef, useState, type TouchEvent } from 'react'
import { useLiveQuery } from 'dexie-react-hooks'
import { useNavigate } from 'react-router-dom'
import { BarChart3, ChevronLeft, ChevronRight, Droplet, NotebookPen, Pencil, Settings } from 'lucide-react'
import { CycleCatalog } from '../../data/catalogs'
import { getPref, usePref } from '../../data/prefs'
import { useActive, useDb } from '../../data/profiles'
import type { CycleDay } from '../../data/types'
import { Fmt, epochDayToDate, today } from '../../lib/dates'
import { Button, Card, Chips, DateInput, Dialog, IconButton, InfoNote, Page, StatTile } from '../../ui'
import { CycleRing, PeopleOnly, markPeriod, setFlow, useCycleState, type CycleUiState } from './common'
import type { DayInfo } from './engine'
import { firstOfMonth, hasExtras, monthGridDays, monthIndex, monthOfEpochDay, monthOfIndex, quickState, rangeText, summary } from './logic'

const KEY_END_ACK = 'cycleEndAckDay'
const WEEKDAYS = ['L', 'M', 'M', 'J', 'V', 'S', 'D']

/** Pestaña / pantalla "Ciclo" (ruta /ciclo). */
export function CyclePage() {
  const { isPet } = useActive()
  return isPet ? <PeopleOnly title="Ciclo menstrual" /> : <CycleMain />
}

function CycleMain() {
  const db = useDb()
  const nav = useNavigate()
  const state = useCycleState()
  const [showFert] = usePref('showFertility')
  const [editMode, setEditMode] = useState(false)
  const [pickStart, setPickStart] = useState(false)
  const [startDay, setStartDay] = useState(today())
  const openDay = (day: number) => nav(`/ciclo/dia/${day}`)

  return (
    <Page
      title="Ciclo"
      actions={
        <>
          <IconButton icon={BarChart3} label="Estadísticas del ciclo" onClick={() => nav('/ciclo/estadisticas')} />
          <IconButton icon={Settings} label="Ajustes del ciclo" onClick={() => nav('/ciclo/ajustes')} />
        </>
      }
    >
      {state.loaded && (
        <>
          {state.analysis.hasData ? (
            <>
              <HeaderCard state={state} showFert={showFert} />
              <QuickActionsCard state={state} onLogToday={() => openDay(state.today)} />
            </>
          ) : (
            <FirstTimeCard onPickStart={() => { setStartDay(today()); setPickStart(true) }} onEdit={() => setEditMode(true)} />
          )}
          <CalendarCard
            state={state}
            showFert={showFert}
            editMode={editMode}
            onEditMode={setEditMode}
            onDayClick={(day) => {
              if (editMode) {
                if (day <= state.today) void setFlow(db, day, (state.byDay.get(day)?.flow ?? 0) >= 1 ? 0 : 3)
              } else {
                openDay(day)
              }
            }}
          />
          {state.analysis.hasData && <TodayCard rec={state.byDay.get(state.today)} onOpen={() => openDay(state.today)} />}
          <InfoNote>
            Las fechas de periodo, ovulación y ventana fértil son estimaciones basadas en tu historial. No sirven como método
            anticonceptivo ni sustituyen una consulta médica.
          </InfoNote>
        </>
      )}
      <Dialog
        open={pickStart}
        onClose={() => setPickStart(false)}
        title="¿Cuándo empezó tu último periodo?"
        actions={
          <>
            <Button variant="text" onClick={() => setPickStart(false)}>Cancelar</Button>
            <Button
              variant="text"
              onClick={async () => {
                setPickStart(false)
                if (startDay <= state.today) await markPeriod(db, startDay, await getPref(db, 'periodLength'), state.today)
              }}
            >
              Marcar periodo
            </Button>
          </>
        }
      >
        <DateInput label="Primer día de sangrado" value={startDay} max={state.today} onChange={setStartDay} />
        <p className="small muted">Se marcarán los días de tu periodo con flujo moderado; después puedes ajustarlos tocando cada día.</p>
      </Dialog>
    </Page>
  )
}

// ── Encabezado ─────────────────────────────────────────────────────────────────────────────────

function HeaderCard({ state, showFert }: { state: CycleUiState; showFert: boolean }) {
  const a = state.analysis
  const s = state.status
  const window = a.nextFertileWindow
  const ov = a.nextOvulation
  return (
    <Card>
      <div className="cy-header">
        <CycleRing analysis={a} cycleDay={s.cycleDay} showFertility={showFert}>
          {s.cycleDay !== null ? (
            <>
              <span className="cy-ring-lbl">Día</span>
              <span className="cy-ring-num" style={{ color: s.inPeriod ? 'var(--period)' : undefined }}>{s.cycleDay}</span>
              <span className="cy-ring-lbl">del ciclo</span>
            </>
          ) : (
            <Droplet size={40} color="var(--period)" />
          )}
        </CycleRing>
        <div className="cy-phase" style={{ color: s.inPeriod || a.lateDays > 0 ? 'var(--period)' : 'var(--primary)' }}>{s.phaseLabel}</div>
        <div className="cy-headline">{s.headline}</div>
        {s.detail && <div className="muted">{s.detail}</div>}
        {showFert && s.cycleDay !== null && (window || ov !== null) && (
          <div className="stats">
            {window && (
              <StatTile
                label="Ventana fértil"
                value={rangeText(window)}
                sub={state.today >= window.first && state.today <= window.last ? 'En curso (estimada)' : `Estimada · ${Fmt.daysFromNow(window.first)}`}
                color="var(--fertile)"
              />
            )}
            {ov !== null && <StatTile label="Ovulación" value={Fmt.dateShort(ov)} sub={`Estimada · ${Fmt.daysFromNow(ov)}`} color="var(--ovulation)" />}
          </div>
        )}
      </div>
    </Card>
  )
}

function FirstTimeCard({ onPickStart, onEdit }: { onPickStart: () => void; onEdit: () => void }) {
  return (
    <Card>
      <div className="cy-first">
        <div className="ic"><Droplet size={36} /></div>
        <h3>Empieza a seguir tu ciclo</h3>
        <p className="muted">
          Registra los días de tu periodo y Savia estimará el siguiente, tu ventana fértil y la ovulación. Entre más ciclos
          registres, mejores serán las estimaciones.
        </p>
        <button type="button" className="btn primary cy-btn-period" style={{ marginTop: 8 }} onClick={onPickStart}>¿Cuándo empezó tu último periodo?</button>
        <Button variant="text" onClick={onEdit}>Marcar días en el calendario</Button>
      </div>
    </Card>
  )
}

// ── Acciones rápidas ───────────────────────────────────────────────────────────────────────────

function QuickActionsCard({ state, onLogToday }: { state: CycleUiState; onLogToday: () => void }) {
  const db = useDb()
  const t = state.today
  const rec = state.byDay.get(t)
  // "Ya terminó" se recuerda por perfil (tabla de ajustes del perfil en IndexedDB).
  const ackDay = useLiveQuery(async () => ((await db.prefs.get(KEY_END_ACK))?.value as number | undefined) ?? -1, [db]) ?? -1
  let quick = quickState(state.analysis, rec)
  if (quick === 'CONTINUE' && ackDay === t) quick = 'START'
  return (
    <Card title={`Hoy · ${Fmt.dateWeekday(t)}`}>
      {quick === 'START' && (
        <button type="button" className="btn primary full cy-btn-period" onClick={() => void setFlow(db, t, 3)}>
          <Droplet size={19} />Empezó mi periodo
        </button>
      )}
      {quick === 'CONTINUE' && (
        <>
          <p className="muted">Ayer registraste sangrado. ¿Tu periodo sigue hoy?</p>
          <div className="row">
            <button type="button" className="btn primary cy-btn-period grow" onClick={() => void setFlow(db, t, Math.max(state.byDay.get(t - 1)?.flow ?? 3, 2))}>Sigue hoy</button>
            <button type="button" className="btn outline grow" onClick={() => void db.prefs.put({ key: KEY_END_ACK, value: t })}>Ya terminó</button>
          </div>
        </>
      )}
      {quick === 'LOGGED' && (
        <>
          <p className="muted">Flujo de hoy</p>
          <Chips options={CycleCatalog.flows.filter((f) => f[0] > 0)} value={rec && rec.flow > 0 ? rec.flow : null} onChange={(v) => void setFlow(db, t, v ?? 0)} />
          <Button variant="text" onClick={() => void setFlow(db, t, 0)}>Terminó: hoy no hubo sangrado</Button>
        </>
      )}
      <button type="button" className="btn outline full cy-btn-log" onClick={onLogToday}><NotebookPen size={18} />Registrar hoy (síntomas, pruebas y más)</button>
      {state.analysis.lateDays >= 3 && (rec?.pregnancyTest ?? 0) === 0 && (
        <p className="small muted">
          Tu periodo lleva {state.analysis.lateDays} días de retraso. Si te haces una prueba de embarazo, puedes anotarla en el registro de hoy.
        </p>
      )}
    </Card>
  )
}

function TodayCard({ rec, onOpen }: { rec: CycleDay | undefined; onOpen: () => void }) {
  const lines = rec ? summary(rec) : []
  return (
    <Card title={lines.length ? 'Hoy registraste' : 'Registro de hoy'} onClick={onOpen}>
      {lines.length === 0 ? (
        <p className="muted">Aún no registras nada hoy. Toca para anotar cómo te sientes.</p>
      ) : (
        <div className="cy-lines">{lines.map((l) => <div key={l}><b>•</b><span>{l}</span></div>)}</div>
      )}
    </Card>
  )
}

// ── Calendario ─────────────────────────────────────────────────────────────────────────────────

function CalendarCard(props: { state: CycleUiState; showFert: boolean; editMode: boolean; onEditMode: (v: boolean) => void; onDayClick: (day: number) => void }) {
  const { state, showFert, editMode } = props
  const current = monthIndex(monthOfEpochDay(state.today))
  const last = current + 60 // hasta 5 años adelante
  const [index, setIndex] = useState(current)
  const go = (i: number) => setIndex(Math.min(Math.max(i, 0), last))
  const ym = monthOfIndex(index)
  const cells = monthGridDays(ym)
  // Deslizar a los lados cambia de mes (el desplazamiento vertical de la página sigue funcionando).
  const touch = useRef<{ x: number; y: number } | null>(null)
  const onTouchStart = (e: TouchEvent) => { const t = e.touches[0]; touch.current = { x: t.clientX, y: t.clientY } }
  const onTouchEnd = (e: TouchEvent) => {
    const s = touch.current
    touch.current = null
    if (!s) return
    const t = e.changedTouches[0]
    const dx = t.clientX - s.x, dy = t.clientY - s.y
    if (Math.abs(dx) > 56 && Math.abs(dx) > Math.abs(dy) * 1.6) go(index + (dx < 0 ? 1 : -1))
  }
  return (
    <Card>
      <div className="cy-cal-head">
        <button type="button" className="icon-btn" aria-label="Mes anterior" disabled={index <= 0} onClick={() => go(index - 1)}><ChevronLeft size={26} /></button>
        <div className="mid">
          <div className="month" data-testid="cy-month">{Fmt.monthYear(firstOfMonth(ym))}</div>
          {index !== current && <button type="button" className="goto" onClick={() => go(current)}>Ir a hoy</button>}
        </div>
        <button type="button" className="icon-btn" aria-label="Mes siguiente" disabled={index >= last} style={{ opacity: index >= last ? 0.3 : 1 }} onClick={() => go(index + 1)}><ChevronRight size={26} /></button>
      </div>
      <div>
        <div className="cy-week" aria-hidden>{WEEKDAYS.map((w, i) => <span key={i}>{w}</span>)}</div>
        <div className="cy-grid" onTouchStart={onTouchStart} onTouchEnd={onTouchEnd}>
          {cells.map(({ day, inMonth }) => (
            <DayCell
              key={day}
              day={day}
              rec={state.byDay.get(day)}
              info={state.analysis.info(day)}
              isToday={day === state.today}
              showFert={showFert}
              dim={!inMonth || (editMode && day > state.today)}
              onClick={() => props.onDayClick(day)}
            />
          ))}
        </div>
      </div>
      {editMode && (
        <div className="note cy-note-period">
          Modo edición: toca los días en que tuviste sangrado para marcarlos o desmarcarlos. Para detallar el flujo de un día,
          sal del modo edición y tócalo.
        </div>
      )}
      <div className="cy-cal-foot">
        <button type="button" className={`chip${editMode ? ' on' : ''}`} aria-pressed={editMode} onClick={() => props.onEditMode(!editMode)}>
          <Pencil size={16} />{editMode ? 'Listo' : 'Editar periodo'}
        </button>
        <span className="small muted">{editMode ? 'Tocando días…' : 'Toca un día para ver o registrar'}</span>
      </div>
      <Legend showFert={showFert} />
    </Card>
  )
}

function DayCell(props: { day: number; rec: CycleDay | undefined; info: DayInfo; isToday: boolean; showFert: boolean; dim: boolean; onClick: () => void }) {
  const { rec, info, isToday, showFert } = props
  const flow = rec?.flow ?? 0
  const extras = rec !== undefined && hasExtras(rec)
  const test = (rec?.pregnancyTest ?? 0) !== 0
  let kind = ''
  let what = ''
  if (flow >= 2) { kind = `flow${flow}`; what = `periodo, flujo ${CycleCatalog.flowLabel(flow).toLowerCase()}` }
  else if (flow === 1 || info.inPeriod) { kind = 'soft'; what = flow === 1 ? 'manchado' : 'periodo' }
  else if (info.predictedPeriod) { kind = 'predicted'; what = 'periodo estimado' }
  else if (showFert && info.ovulation) { kind = 'ovulation'; what = 'ovulación estimada' }
  else if (showFert && info.fertile) { kind = 'fertile'; what = 'ventana fértil estimada' }
  const label = [Fmt.dateWeekday(props.day), isToday ? 'hoy' : '', what, extras ? 'con notas' : '', test ? 'prueba de embarazo' : ''].filter(Boolean).join(', ')
  return (
    <button type="button" className={`cy-cell${props.dim ? ' dim' : ''}`} aria-label={label} data-day={props.day} onClick={props.onClick}>
      <span className={`cy-disc ${kind}${isToday ? ' today' : ''}${isToday || flow >= 2 ? ' bold' : ''}`}>{epochDayToDate(props.day).getDate()}</span>
      {extras && <i className={`cy-extra${flow >= 2 ? ' on-period' : ''}`} />}
      {test && <i className="cy-test" data-testid="cy-test-mark" />}
    </button>
  )
}

function Legend({ showFert }: { showFert: boolean }) {
  return (
    <div className="cy-legend" aria-label="Leyenda del calendario">
      <span><i className="period" />Periodo</span>
      <span><i className="predicted" />Periodo estimado</span>
      {showFert && <span><i className="fertile" />Ventana fértil</span>}
      {showFert && <span><i className="ovulation" />Ovulación</span>}
      <span><i className="today" />Hoy</span>
      <span><i className="notes" />Con notas</span>
      <span><i className="test" />Prueba de embarazo</span>
    </div>
  )
}
