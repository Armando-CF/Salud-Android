// Piezas compartidas del módulo de mediciones: iconos, formato de valores, acceso a la ficha y formularios.
import { useMemo, useState } from 'react'
import { Activity, Droplet, HeartPulse, Percent, Ruler, Scale, Thermometer, Wind, type LucideIcon } from 'lucide-react'
import type { MeasureType } from '../../data/catalogs'
import type { SaviaDB } from '../../data/db'
import { displayName, useActive } from '../../data/profiles'
import { emptyProfile, type Measurement, type Profile } from '../../data/types'
import { Fmt, nowMillis, toNumber, today } from '../../lib/dates'
import { Button, Chips, DateTimeInput, Dialog, NumberInput, TextArea } from '../../ui'
import { validate, type Level } from './stats'
import { inputDecimals, weightDelta, weightText, whoOf, type MeasureWho } from './who'

const ICONS: Record<string, LucideIcon> = {
  weight: Scale, height: Ruler, bp: HeartPulse, hr: Activity, glucose: Droplet, temp: Thermometer, spo2: Wind,
  waist: Ruler, hip: Ruler, fat: Percent,
}
export const measureIcon = (type: MeasureType): LucideIcon => ICONS[type.key] ?? Activity

/** "72.5" o "120/80" (sin unidad). */
export function valueText(type: MeasureType, m: Measurement): string {
  const v2 = m.value2
  if (type.secondLabel && v2 !== null && v2 !== undefined) return `${Fmt.num(m.value, type.decimals)}/${Fmt.num(v2, type.decimals)}`
  return Fmt.num(m.value, type.decimals)
}

/** "72.5 kg" o "120/80 mmHg". El peso de mascotas va con 2 decimales, o en gramos bajo 1 kg ("350 g"). */
export const valueWithUnit = (type: MeasureType, m: Measurement, isPet: boolean): string =>
  type.key === 'weight' ? weightText(m.value, isPet) : `${valueText(type, m)} ${type.unit}`

/** Valor suelto con unidad (promedios, mínimos): "72.5 kg", "350 g", "36.8 °C". */
export const amount = (type: MeasureType, value: number, isPet: boolean, decimals: number = type.decimals): string =>
  type.key === 'weight' ? weightText(value, isPet) : `${Fmt.num(value, decimals)} ${type.unit}`

/** Cambio con signo y unidad: "+0.4 kg", "−25 g", "+3 lpm". */
export const deltaText = (type: MeasureType, value: number, isPet: boolean): string =>
  type.key === 'weight' ? weightDelta(value, isPet) : `${Fmt.signed(value, Math.max(type.decimals, 1))} ${type.unit}`

export const levelColor = (level: Level): string =>
  level === 'good' ? 'var(--good)' : level === 'warn' ? 'var(--warn)' : level === 'bad' ? 'var(--bad)' : 'var(--muted)'

/** A quién se mide (perfil activo); usa la fecha de nacimiento de la ficha en cuanto se carga. */
export function useMeasureWho(profile: Profile | undefined): MeasureWho {
  const { info, isPet } = useActive()
  const birth = profile?.birthEpochDay ?? info.birthEpochDay
  const t = today()
  return useMemo(() => whoOf(isPet, info.species, birth, t, displayName(info)), [isPet, info, birth, t])
}

/** Guarda un cambio en la ficha aunque todavía no exista la fila. */
export async function updateProfile(db: SaviaDB, change: (p: Profile) => Profile): Promise<void> {
  await db.profile.put(change((await db.profile.get(1)) ?? emptyProfile()))
}

async function latestHeight(db: SaviaDB): Promise<Measurement | undefined> {
  const all = await db.measurements.where('type').equals('height').toArray()
  return all.reduce<Measurement | undefined>((a, b) => (!a || b.timestamp > a.timestamp ? b : a), undefined)
}

/** Deja `heightCm` de la ficha igual a la estatura más reciente del historial (si hay alguna). */
export async function syncProfileHeight(db: SaviaDB): Promise<void> {
  const latest = await latestHeight(db)
  if (!latest) return
  await updateProfile(db, (p) => (p.heightCm === latest.value ? p : { ...p, heightCm: latest.value }))
}

/** Guarda la estatura en la ficha y, si cambió respecto a la última registrada, también en el historial. */
export async function recordHeight(db: SaviaDB, heightCm: number, now: number = nowMillis()): Promise<void> {
  await updateProfile(db, (p) => ({ ...p, heightCm }))
  const last = await latestHeight(db)
  if (!last || Math.abs(last.value - heightCm) > 0.05) {
    await db.measurements.add({ timestamp: now, type: 'height', value: heightCm, value2: null, tag: '', notes: '' })
  }
}

/**
 * Formulario de alta / edición de una medición (contenido de la hoja inferior).
 * `initial` = null → nueva medición.
 */
export function MeasureForm(props: {
  type: MeasureType
  initial: Measurement | null
  isPet: boolean
  onSave: (m: Measurement) => void
  onCancel: () => void
  onDelete?: () => void
}) {
  const { type, initial, isPet } = props
  const decimals = inputDecimals(type, isPet)
  const [v1, setV1] = useState(initial ? Fmt.num(initial.value, decimals) : '')
  const [v2, setV2] = useState(initial?.value2 !== null && initial?.value2 !== undefined ? Fmt.num(initial.value2, type.decimals) : '')
  const [ts, setTs] = useState(initial?.timestamp ?? nowMillis())
  const [tag, setTag] = useState(initial?.tag ?? '')
  const [notes, setNotes] = useState(initial?.notes ?? '')
  const [error, setError] = useState<string | null>(null)

  const submit = () => {
    const a = toNumber(v1)
    const b = type.secondLabel ? toNumber(v2) : null
    const problem = validate(type, a, b, ts, nowMillis(), isPet)
    if (problem !== null || a === null) {
      setError(problem)
      return
    }
    props.onSave({ ...(initial ?? {}), timestamp: ts, type: type.key, value: a, value2: b, tag, notes: notes.trim() })
  }
  const tagOptions = [...type.tags, ...(tag && !type.tags.includes(tag) ? [tag] : [])].map((t) => [t, t] as const)

  return (
    <div className="stack">
      {type.secondLabel ? (
        <div className="row" style={{ alignItems: 'flex-start' }}>
          <div className="grow">
            <NumberInput label={type.valueLabel} value={v1} onChange={(v) => { setV1(v); setError(null) }} suffix={type.unit} decimal={type.decimals > 0} />
          </div>
          <div className="grow">
            <NumberInput label={type.secondLabel} value={v2} onChange={(v) => { setV2(v); setError(null) }} suffix={type.unit} decimal={type.decimals > 0} onEnter={submit} />
          </div>
        </div>
      ) : (
        <NumberInput
          label={`${type.valueLabel} (${type.unit})`}
          value={v1}
          onChange={(v) => { setV1(v); setError(null) }}
          decimal={decimals > 0}
          hint={isPet && type.key === 'weight' ? 'En kilogramos. Para gramos usa decimales: 0.35 kg son 350 g.' : undefined}
          onEnter={submit}
        />
      )}
      <DateTimeInput value={ts} onChange={(v) => { setTs(v); setError(null) }} max={today()} />
      {tagOptions.length > 0 && (
        <div className="field">
          <span className="field-label">Contexto (opcional)</span>
          <Chips options={tagOptions} value={tag || null} onChange={(v) => setTag(v ?? '')} allowDeselect />
        </div>
      )}
      <TextArea label="Nota (opcional)" value={notes} onChange={(v) => setNotes(v.slice(0, 500))} rows={2} />
      {error && <div className="hint error" role="alert" style={{ color: 'var(--error)', fontSize: 14 }}>{error}</div>}
      <div className="row" style={{ marginTop: 4 }}>
        {initial && props.onDelete && <Button variant="text" onClick={props.onDelete} style={{ color: 'var(--error)' }}>Eliminar</Button>}
        <span className="grow" />
        <Button variant="outline" onClick={props.onCancel}>Cancelar</Button>
        <Button onClick={submit}>Guardar</Button>
      </div>
    </div>
  )
}

/**
 * Diálogo para capturar un número (meta de peso, estatura).
 * `onSave` recibe el valor validado; `onClear` (opcional) muestra un botón para quitar el valor.
 */
export function NumberDialog(props: {
  open: boolean
  title: string
  label: string
  unit: string
  initial: number | null
  min: number
  max: number
  onSave: (v: number) => void
  onClose: () => void
  text?: string
  clearLabel?: string
  onClear?: () => void
  decimals?: number
}) {
  if (!props.open) return null
  return <NumberDialogBody {...props} />
}

function NumberDialogBody(props: Parameters<typeof NumberDialog>[0]) {
  const [value, setValue] = useState(props.initial !== null ? Fmt.num(props.initial, props.decimals ?? 1) : '')
  const [error, setError] = useState<string | null>(null)
  const submit = () => {
    const v = toNumber(value)
    if (v === null || v < props.min || v > props.max) setError(`Escribe un valor entre ${Fmt.num(props.min, 2)} y ${Fmt.num(props.max, 0)} ${props.unit}.`)
    else props.onSave(v)
  }
  return (
    <Dialog
      open
      onClose={props.onClose}
      title={props.title}
      actions={
        <>
          {props.clearLabel && props.onClear && props.initial !== null && (
            <Button variant="text" onClick={props.onClear} style={{ color: 'var(--error)' }}>{props.clearLabel}</Button>
          )}
          <Button variant="text" onClick={props.onClose}>Cancelar</Button>
          <Button variant="text" onClick={submit}>Guardar</Button>
        </>
      }
    >
      {props.text && <p className="muted">{props.text}</p>}
      {/* Único campo del diálogo: la tecla "Listo" del teclado guarda y cierra. */}
      <NumberInput label={props.label} value={value} onChange={(v) => { setValue(v); setError(null) }} suffix={props.unit} error={error ?? false} onEnter={submit} />
    </Dialog>
  )
}
