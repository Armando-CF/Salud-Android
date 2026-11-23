import { createContext, useCallback, useContext, useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { useLocation, useNavigate } from 'react-router-dom'
import { ChevronLeft, ChevronRight, X, type LucideIcon } from 'lucide-react'
import {
  Fmt, epochDayToIso, epochDayToMillis, formatHm, isoToEpochDay, millisToEpochDay, millisToMinutes, parseHm, today,
} from '../lib/dates'
import type { Opt } from '../data/catalogs'

/** Rutas que muestran la barra inferior de pestañas. */
export const TAB_PATHS = ['/', '/sintomas', '/ciclo', '/mediciones', '/cuerpo', '/mas']

/** ¿La ruta actual es una pestaña de primer nivel? (definido por App según el perfil) */
export const TabsContext = createContext<string[]>(TAB_PATHS)

/** Regresa a la pantalla anterior; si no hay historial (se abrió directo), va a Inicio. */
export function useBack(): () => void {
  const nav = useNavigate()
  const loc = useLocation()
  return useCallback(() => {
    if (loc.key !== 'default' && window.history.length > 1) nav(-1)
    else nav('/', { replace: true })
  }, [nav, loc.key])
}

/**
 * Pantalla estándar: barra superior (con zona segura del iPhone) + cuerpo desplazable.
 * - `back`: true muestra la flecha de regreso (o pasa una función). En las pestañas de primer nivel se omite sola.
 * - `actions`: botones a la derecha del título (usa <IconButton> o <button className="text-btn">).
 * - `fab`: botón flotante (usa <Fab>).
 * - `flush`: el cuerpo sin relleno (para contenido a sangre, como el atlas).
 */
export function Page(props: {
  title: string
  subtitle?: string
  back?: boolean | (() => void)
  actions?: ReactNode
  fab?: ReactNode
  flush?: boolean
  children: ReactNode
}) {
  const loc = useLocation()
  const tabs = useContext(TabsContext)
  const isTab = tabs.includes(loc.pathname)
  const goBack = useBack()
  const showBack = props.back !== undefined ? !!props.back : !isTab
  const onBack = typeof props.back === 'function' ? props.back : goBack
  return (
    <div className={`page${isTab ? '' : ' no-tabs'}`}>
      <header className="topbar">
        {showBack && (
          <button className="icon-btn" onClick={onBack} aria-label="Regresar"><ChevronLeft size={28} /></button>
        )}
        <div className="titles">
          <h1>{props.title}</h1>
          {props.subtitle && <div className="sub">{props.subtitle}</div>}
        </div>
        <div className="actions">{props.actions}</div>
      </header>
      <main className={`page-body${props.fab ? ' with-fab' : ''}${props.flush ? ' flush' : ''}`}>{props.children}</main>
      {props.fab}
    </div>
  )
}

export function IconButton(props: { icon: LucideIcon; label: string; onClick: () => void; size?: number; style?: CSSProperties }) {
  const I = props.icon
  return (
    <button className="icon-btn" onClick={props.onClick} aria-label={props.label} title={props.label} style={props.style}>
      <I size={props.size ?? 24} />
    </button>
  )
}

export function Fab(props: { icon: LucideIcon; label: string; onClick: () => void }) {
  const I = props.icon
  return (
    <button className="fab" onClick={props.onClick}><I size={22} />{props.label}</button>
  )
}

/** Tarjeta con título y acción opcionales. Con `onClick` toda la tarjeta es tocable. */
export function Card(props: { title?: string; action?: ReactNode; onClick?: () => void; children: ReactNode; style?: CSSProperties; className?: string }) {
  return (
    <section className={`card${props.onClick ? ' tap' : ''}${props.className ? ' ' + props.className : ''}`} onClick={props.onClick} style={props.style}>
      {(props.title || props.action) && (
        <div className="card-head"><h2>{props.title}</h2>{props.action}</div>
      )}
      <div className="card-body">{props.children}</div>
    </section>
  )
}

export function SectionLabel(props: { children: ReactNode }) {
  return <div className="section-label">{props.children}</div>
}

type BtnVariant = 'primary' | 'tonal' | 'outline' | 'text' | 'danger'
export function Button(props: {
  children: ReactNode
  onClick?: () => void
  variant?: BtnVariant
  icon?: LucideIcon
  full?: boolean
  small?: boolean
  disabled?: boolean
  type?: 'button' | 'submit'
  style?: CSSProperties
}) {
  const I = props.icon
  return (
    <button
      type={props.type ?? 'button'}
      className={`btn ${props.variant ?? 'primary'}${props.full ? ' full' : ''}${props.small ? ' small' : ''}`}
      onClick={props.onClick}
      disabled={props.disabled}
      style={props.style}
    >
      {I && <I size={19} />}
      {props.children}
    </button>
  )
}

/** Chips de selección única. Con `allowDeselect`, tocar el elegido lo quita (onChange(null)). */
export function Chips<K extends string | number>(props: {
  options: readonly Opt<K>[]
  value: K | null | undefined
  onChange: (v: K | null) => void
  allowDeselect?: boolean
  scroll?: boolean
}) {
  return (
    <div className={`chips${props.scroll ? ' scroll' : ''}`} role="group">
      {props.options.map(([k, label]) => (
        <button
          key={String(k)}
          type="button"
          className={`chip${k === props.value ? ' on' : ''}`}
          aria-pressed={k === props.value}
          onClick={() => (k === props.value ? props.allowDeselect && props.onChange(null) : props.onChange(k))}
        >
          {label}
        </button>
      ))}
    </div>
  )
}

/** Chips de selección múltiple. */
export function MultiChips<K extends string | number>(props: { options: readonly Opt<K>[]; values: ReadonlySet<K> | readonly K[]; onToggle: (k: K) => void; scroll?: boolean }) {
  const has = (k: K) => (props.values instanceof Set ? props.values.has(k) : (props.values as readonly K[]).includes(k))
  return (
    <div className={`chips${props.scroll ? ' scroll' : ''}`} role="group">
      {props.options.map(([k, label]) => (
        <button key={String(k)} type="button" className={`chip${has(k) ? ' on' : ''}`} aria-pressed={has(k)} onClick={() => props.onToggle(k)}>
          {label}
        </button>
      ))}
    </div>
  )
}

/** Envoltura de un campo: etiqueta arriba y ayuda/error abajo. */
export function Field(props: { label?: string; hint?: string; error?: string | false; children: ReactNode; style?: CSSProperties }) {
  return (
    <div className="field" style={props.style}>
      {props.label && <label>{props.label}</label>}
      {props.children}
      {props.error ? <div className="hint error">{props.error}</div> : props.hint ? <div className="hint">{props.hint}</div> : null}
    </div>
  )
}

/** Campo de texto de una línea (16px: sin zoom automático en iOS). `enterKeyHint="done"` cierra el teclado al pulsar. */
export function TextInput(props: {
  value: string
  onChange: (v: string) => void
  label?: string
  placeholder?: string
  hint?: string
  error?: string | false
  maxLength?: number
  type?: 'text' | 'tel' | 'search' | 'email'
  autoCapitalize?: 'sentences' | 'words' | 'none'
  enterKeyHint?: 'done' | 'next' | 'search'
  onEnter?: () => void
  suffix?: string
  autoFocus?: boolean
}) {
  return (
    <Field label={props.label} hint={props.hint} error={props.error}>
      <div className="input-wrap">
        <input
          className={`input${props.error ? ' invalid' : ''}${props.suffix ? ' has-suffix' : ''}`}
          type={props.type ?? 'text'}
          value={props.value}
          placeholder={props.placeholder}
          maxLength={props.maxLength}
          autoCapitalize={props.autoCapitalize ?? 'sentences'}
          autoComplete="off"
          enterKeyHint={props.enterKeyHint ?? 'done'}
          autoFocus={props.autoFocus}
          onChange={(e) => props.onChange(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter') { (e.target as HTMLInputElement).blur(); props.onEnter?.() } }}
        />
        {props.suffix && <span className="suffix">{props.suffix}</span>}
      </div>
    </Field>
  )
}

export function TextArea(props: { value: string; onChange: (v: string) => void; label?: string; placeholder?: string; hint?: string; rows?: number }) {
  return (
    <Field label={props.label} hint={props.hint}>
      <textarea className="input" value={props.value} placeholder={props.placeholder} rows={props.rows ?? 3} onChange={(e) => props.onChange(e.target.value)} />
    </Field>
  )
}

/**
 * Campo numérico. El valor es TEXTO (lo controla quien llama; conviértelo con `toNumber()` de lib/dates).
 * Muestra el teclado numérico de iOS y acepta coma o punto decimal.
 */
export function NumberInput(props: {
  value: string
  onChange: (v: string) => void
  label?: string
  suffix?: string
  decimal?: boolean
  hint?: string
  error?: string | false
  placeholder?: string
  onEnter?: () => void
  autoFocus?: boolean
}) {
  const decimal = props.decimal ?? true
  return (
    <Field label={props.label} hint={props.hint} error={props.error}>
      <div className="input-wrap">
        <input
          className={`input${props.error ? ' invalid' : ''}${props.suffix ? ' has-suffix' : ''}`}
          type="text"
          inputMode={decimal ? 'decimal' : 'numeric'}
          pattern={decimal ? '[0-9]*[.,]?[0-9]*' : '[0-9]*'}
          value={props.value}
          placeholder={props.placeholder}
          autoComplete="off"
          enterKeyHint="done"
          autoFocus={props.autoFocus}
          onChange={(e) => props.onChange(e.target.value.replace(decimal ? /[^0-9.,]/g : /[^0-9]/g, '').slice(0, 9))}
          onKeyDown={(e) => { if (e.key === 'Enter') { (e.target as HTMLInputElement).blur(); props.onEnter?.() } }}
        />
        {props.suffix && <span className="suffix">{props.suffix}</span>}
      </div>
    </Field>
  )
}

/** Fecha (epochDay) con el selector nativo de iOS. `max` limita el máximo (p. ej. hoy). */
export function DateInput(props: { value: number; onChange: (epochDay: number) => void; label?: string; max?: number; min?: number; hint?: string }) {
  return (
    <Field label={props.label} hint={props.hint}>
      <input
        className="input"
        type="date"
        value={epochDayToIso(props.value)}
        max={props.max !== undefined ? epochDayToIso(props.max) : undefined}
        min={props.min !== undefined ? epochDayToIso(props.min) : undefined}
        onChange={(e) => { const d = isoToEpochDay(e.target.value); if (d !== null) props.onChange(d) }}
      />
    </Field>
  )
}

/** Fecha opcional: vacía = null. */
export function OptionalDateInput(props: { value: number | null; onChange: (epochDay: number | null) => void; label?: string; max?: number; min?: number; hint?: string }) {
  return (
    <Field label={props.label} hint={props.hint}>
      <div className="row">
        <input
          className="input grow"
          type="date"
          value={props.value === null ? '' : epochDayToIso(props.value)}
          max={props.max !== undefined ? epochDayToIso(props.max) : undefined}
          min={props.min !== undefined ? epochDayToIso(props.min) : undefined}
          onChange={(e) => props.onChange(e.target.value ? isoToEpochDay(e.target.value) : null)}
        />
        {props.value !== null && <button type="button" className="icon-btn" aria-label="Quitar fecha" onClick={() => props.onChange(null)}><X size={22} /></button>}
      </div>
    </Field>
  )
}

/** Hora en minutos del día (0..1439). */
export function TimeInput(props: { value: number; onChange: (minutes: number) => void; label?: string }) {
  return (
    <Field label={props.label}>
      <input className="input" type="time" value={formatHm(props.value)} onChange={(e) => { const m = parseHm(e.target.value); if (m !== null) props.onChange(m) }} />
    </Field>
  )
}

/** Fecha + hora en una fila (valor en milisegundos). */
export function DateTimeInput(props: { value: number; onChange: (ms: number) => void; dateLabel?: string; timeLabel?: string; max?: number }) {
  const day = millisToEpochDay(props.value)
  const minutes = millisToMinutes(props.value)
  return (
    <div className="row" style={{ alignItems: 'flex-start' }}>
      <div style={{ flex: '1.4 1 0', minWidth: 0 }}>
        <DateInput label={props.dateLabel ?? 'Fecha'} value={day} max={props.max} onChange={(d) => props.onChange(epochDayToMillis(d, minutes))} />
      </div>
      <div style={{ flex: '1 1 0', minWidth: 0 }}>
        <TimeInput label={props.timeLabel ?? 'Hora'} value={minutes} onChange={(m) => props.onChange(epochDayToMillis(day, m))} />
      </div>
    </div>
  )
}

export function Select<K extends string | number>(props: { value: K; onChange: (v: K) => void; options: readonly Opt<K>[]; label?: string }) {
  return (
    <Field label={props.label}>
      <select
        className="input"
        value={String(props.value)}
        onChange={(e) => { const o = props.options.find(([k]) => String(k) === e.target.value); if (o) props.onChange(o[0]) }}
      >
        {props.options.map(([k, label]) => <option key={String(k)} value={String(k)}>{label}</option>)}
      </select>
    </Field>
  )
}

/** Interruptor con título y descripción. */
export function Toggle(props: { label: string; sub?: string; checked: boolean; onChange: (v: boolean) => void; disabled?: boolean }) {
  return (
    <label className="toggle-row">
      <span className="grow">
        <span style={{ fontWeight: 600, display: 'block' }}>{props.label}</span>
        {props.sub && <span className="small muted" style={{ display: 'block' }}>{props.sub}</span>}
      </span>
      <span className="switch">
        <input type="checkbox" role="switch" checked={props.checked} disabled={props.disabled} onChange={(e) => props.onChange(e.target.checked)} />
        <span className="track" /><span className="thumb" />
      </span>
    </label>
  )
}

/** Deslizador de 1 a `max` con etiqueta y valor a la derecha. */
export function LevelSlider(props: { label: string; value: number; onChange: (v: number) => void; max?: number; valueText?: string; color?: string }) {
  const max = props.max ?? 10
  return (
    <div className="field">
      <div className="row"><span className="grow">{props.label}</span><b style={{ color: props.color ?? 'var(--primary)' }}>{props.valueText ?? `${props.value} / ${max}`}</b></div>
      <input type="range" min={1} max={max} step={1} value={props.value} style={{ accentColor: props.color }} onChange={(e) => props.onChange(Number(e.target.value))} aria-label={props.label} />
    </div>
  )
}

/** Selector segmentado (2–4 opciones). */
export function Segmented<K extends string | number>(props: { options: readonly Opt<K>[]; value: K; onChange: (v: K) => void }) {
  return (
    <div className="segmented" role="group">
      {props.options.map(([k, label]) => (
        <button key={String(k)} type="button" className={k === props.value ? 'on' : ''} aria-pressed={k === props.value} onClick={() => props.onChange(k)}>{label}</button>
      ))}
    </div>
  )
}

export function EmptyState(props: { icon: LucideIcon; title: string; message: string; actionLabel?: string; onAction?: () => void }) {
  const I = props.icon
  return (
    <div className="empty">
      <div className="ic"><I size={36} /></div>
      <h3>{props.title}</h3>
      <p className="muted">{props.message}</p>
      {props.actionLabel && props.onAction && <Button onClick={props.onAction} style={{ marginTop: 10 }}>{props.actionLabel}</Button>}
    </div>
  )
}

/** Cuadro con un dato destacado. Úsalo dentro de <div className="stats"> (o "stats grid"). */
export function StatTile(props: { label: string; value: string; sub?: string; color?: string }) {
  return (
    <div className="stat">
      <div className="l">{props.label}</div>
      <div className="v" style={{ color: props.color }}>{props.value}</div>
      {props.sub && <div className="s">{props.sub}</div>}
    </div>
  )
}

/** Fila de navegación: icono, título, subtítulo y flecha. */
export function NavRow(props: { icon: LucideIcon; title: string; subtitle?: string; onClick: () => void; color?: string; trailing?: ReactNode }) {
  const I = props.icon
  const color = props.color ?? 'var(--primary)'
  return (
    <button type="button" className="navrow" onClick={props.onClick}>
      <span className="ic" style={{ background: `color-mix(in srgb, ${color} 15%, transparent)`, color }}><I size={22} /></span>
      <span className="grow">
        <span className="t" style={{ display: 'block' }}>{props.title}</span>
        {props.subtitle && <span className="s" style={{ display: 'block' }}>{props.subtitle}</span>}
      </span>
      {props.trailing ?? <ChevronRight size={20} color="var(--muted)" />}
    </button>
  )
}

/** Etiqueta pequeña de color (estado, categoría). */
export function Pill(props: { text: string; color?: string }) {
  const c = props.color ?? 'var(--outline)'
  return <span className="pill-tag" style={{ background: `color-mix(in srgb, ${c} 17%, transparent)`, color: c }}>{props.text}</span>
}

/** Aviso informativo ("esto no sustituye una consulta médica"). */
export function InfoNote(props: { children: ReactNode; tone?: 'info' | 'warn' | 'danger' }) {
  return <div className={`note${props.tone && props.tone !== 'info' ? ' ' + props.tone : ''}`}>{props.children}</div>
}

/** Color según intensidad 1..10 (verde → ámbar → rojo). */
export const intensityColor = (i: number): string => (i <= 3 ? 'var(--good)' : i <= 6 ? 'var(--warn)' : 'var(--bad)')

/** Avatar circular de un perfil: inicial para personas, huella para mascotas. */
export function Avatar(props: { name: string; color: string; pet?: boolean; size?: number }) {
  const size = props.size ?? 40
  return (
    <span className="avatar" style={{ width: size, height: size, background: props.color, fontSize: size * 0.44 }} aria-hidden>
      {props.pet ? <PawIcon size={size * 0.56} /> : (props.name.trim().charAt(0) || '?').toUpperCase()}
    </span>
  )
}

export function PawIcon(props: { size?: number }) {
  const s = props.size ?? 24
  return (
    <svg width={s} height={s} viewBox="0 0 24 24" fill="currentColor" aria-hidden>
      <circle cx="4.5" cy="9.5" r="2.5" /><circle cx="9" cy="5.5" r="2.5" /><circle cx="15" cy="5.5" r="2.5" /><circle cx="19.5" cy="9.5" r="2.5" />
      <path d="M17.34 14.86c-.87-1.02-1.6-1.89-2.48-2.91-.46-.54-1.05-1.08-1.75-1.32a3.3 3.3 0 0 0-2.22 0c-.7.24-1.29.78-1.75 1.32-.87 1.02-1.6 1.89-2.48 2.91-1.31 1.31-2.92 2.76-2.62 4.79.29 1.02 1.02 2.03 2.33 2.32.73.15 3.06-.44 5.54-.44h.18c2.48 0 4.81.58 5.54.44 1.31-.29 2.04-1.31 2.33-2.32.31-2.04-1.3-3.49-2.62-4.79z" />
    </svg>
  )
}

/** Contenedor donde se montan hojas y diálogos (dentro de .app, para respetar el ancho máximo). */
function overlayRoot(): HTMLElement {
  return (document.querySelector('.app') as HTMLElement | null) ?? document.body
}

/**
 * Hoja inferior modal. Con `full` ocupa toda la pantalla (útil para elegir zona del cuerpo).
 * El contenido se desplaza por dentro; tocar fuera o la X la cierra.
 */
export function Sheet(props: { open: boolean; onClose: () => void; title?: string; full?: boolean; children: ReactNode; actions?: ReactNode }) {
  if (!props.open) return null
  return createPortal(
    <div className="overlay" onClick={(e) => { if (e.target === e.currentTarget) props.onClose() }}>
      <div className={`sheet${props.full ? ' full' : ''}`} role="dialog" aria-modal="true" aria-label={props.title}>
        <div className="sheet-head">
          <h2>{props.title}</h2>
          {props.actions}
          <button className="icon-btn" onClick={props.onClose} aria-label="Cerrar"><X size={24} /></button>
        </div>
        <div className="sheet-body">{props.children}</div>
      </div>
    </div>,
    overlayRoot(),
  )
}

/** Diálogo centrado genérico. */
export function Dialog(props: { open: boolean; onClose: () => void; title: string; children: ReactNode; actions?: ReactNode }) {
  if (!props.open) return null
  return createPortal(
    <div className="overlay center" onClick={(e) => { if (e.target === e.currentTarget) props.onClose() }}>
      <div className="dialog" role="dialog" aria-modal="true" aria-label={props.title}>
        <h2>{props.title}</h2>
        {props.children}
        {props.actions && <div className="dialog-actions">{props.actions}</div>}
      </div>
    </div>,
    overlayRoot(),
  )
}

/** Diálogo de confirmación (p. ej. para borrar). */
export function ConfirmDialog(props: {
  open: boolean
  title: string
  text: string
  onConfirm: () => void
  onClose: () => void
  confirmLabel?: string
  destructive?: boolean
}) {
  return (
    <Dialog
      open={props.open}
      onClose={props.onClose}
      title={props.title}
      actions={
        <>
          <Button variant="text" onClick={props.onClose}>Cancelar</Button>
          <Button variant="text" onClick={props.onConfirm} style={(props.destructive ?? true) ? { color: 'var(--error)' } : undefined}>{props.confirmLabel ?? 'Eliminar'}</Button>
        </>
      }
    >
      <p className="muted">{props.text}</p>
    </Dialog>
  )
}

// ── Avisos breves (toast) ──────────────────────────────────────────────────────────────────────
const ToastContext = createContext<(msg: string) => void>(() => {})

export function ToastProvider(props: { children: ReactNode }) {
  const [msg, setMsg] = useState<string | null>(null)
  const timer = useRef<number>()
  const show = useCallback((m: string) => {
    setMsg(m)
    window.clearTimeout(timer.current)
    timer.current = window.setTimeout(() => setMsg(null), 3200)
  }, [])
  useEffect(() => () => window.clearTimeout(timer.current), [])
  return (
    <ToastContext.Provider value={show}>
      {props.children}
      {msg && createPortal(<div className="toast" role="status">{msg}</div>, overlayRoot())}
    </ToastContext.Provider>
  )
}

/** `const toast = useToast(); toast('Guardado')` */
export const useToast = (): ((msg: string) => void) => useContext(ToastContext)

/** Navegador de día (anterior / siguiente, sin pasar de hoy). */
export function DayNav(props: { day: number; onChange: (d: number) => void; max?: number }) {
  const max = props.max ?? today()
  return (
    <div className="row">
      <button className="icon-btn" aria-label="Día anterior" onClick={() => props.onChange(props.day - 1)}><ChevronLeft size={26} /></button>
      <div className="grow center">
        <div style={{ fontWeight: 700 }}>{Fmt.relativeDay(props.day)}</div>
        <div className="small muted">{Fmt.dateLong(props.day)}</div>
      </div>
      <button className="icon-btn" aria-label="Día siguiente" disabled={props.day >= max} style={{ opacity: props.day >= max ? 0.3 : 1 }} onClick={() => props.day < max && props.onChange(props.day + 1)}><ChevronRight size={26} /></button>
    </div>
  )
}
