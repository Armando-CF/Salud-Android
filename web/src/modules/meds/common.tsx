import { useEffect, useState } from 'react'
import { CalendarPlus, Undo2 } from 'lucide-react'
import { useActive, displayName } from '../../data/profiles'
import { saveFile, slug } from '../../lib/files'
import { Fmt } from '../../lib/dates'
import { Button, Pill, useToast } from '../../ui'
import { buildIcs, type IcsEvent } from './ics'
import { type Adherence, type DoseItem } from './schedule'
import { CALENDAR_NOTE } from './text'
import './meds.css'

/** Hora actual en milisegundos que se refresca cada medio minuto (para "atrasada", "en X días"…). */
export function useNow(intervalMs = 30_000): number {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const t = window.setInterval(() => setNow(Date.now()), intervalMs)
    const onVisible = () => { if (document.visibilityState === 'visible') setNow(Date.now()) }
    document.addEventListener('visibilitychange', onVisible)
    return () => { window.clearInterval(t); document.removeEventListener('visibilitychange', onVisible) }
  }, [intervalMs])
  return now
}

/**
 * Nombre del perfil para anteponerlo en los eventos del calendario cuando no es el perfil principal
 * (un hijo, una mascota…), para saber de quién es el aviso. `undefined` para el perfil principal.
 */
export function useCalendarOwner(): { profileId: number; owner: string | undefined } {
  const { info, isPet } = useActive()
  return { profileId: info.id, owner: info.id !== 1 || isPet ? displayName(info) : undefined }
}

/**
 * Devuelve una función que entrega un archivo .ics con los eventos indicados.
 * Debe llamarse como respuesta directa a un toque (iOS lo exige para compartir o descargar).
 */
export function useAddToCalendar(): (fileBase: string, events: IcsEvent[]) => Promise<void> {
  const toast = useToast()
  return async (fileBase, events) => {
    if (!events.length) { toast('No hay nada que agregar al calendario'); return }
    try {
      const result = await saveFile(`savia-${slug(fileBase, 40)}.ics`, buildIcs(events), 'text/calendar')
      if (result === 'downloaded') toast('Archivo de calendario listo: ábrelo para agregar el evento')
    } catch {
      toast('No se pudo crear el archivo de calendario')
    }
  }
}

/** Botón "Agregar al calendario". */
export function CalendarButton(props: { onClick: () => void; label?: string; small?: boolean; full?: boolean; variant?: 'tonal' | 'outline' | 'text' }) {
  return (
    <Button variant={props.variant ?? 'outline'} icon={CalendarPlus} small={props.small} full={props.full} onClick={props.onClick}>
      {props.label ?? 'Agregar al calendario'}
    </Button>
  )
}

/** Nota breve que explica por qué se usa el calendario. */
export function CalendarNote() {
  return <p className="small muted">{CALENDAR_NOTE}</p>
}

/** Una toma programada: hora, medicamento, estado y acciones. Reutilizable en otras pantallas (p. ej. Inicio). */
export function DoseRow(props: { dose: DoseItem; late: boolean; onTake: () => void; onSkip: () => void; onUndo: () => void }) {
  const { dose, late } = props
  const detail = [dose.med.dose, dose.med.instructions].filter((s) => s.trim()).join(' · ')
  return (
    <div className="md-dose" data-testid={`dose-${dose.med.id}-${dose.slot}`}>
      <div className="row">
        <span className="md-dose-time" style={late ? { color: 'var(--warn)' } : undefined}>{dose.slot}</span>
        <span className="grow">
          <span className="ellipsis" style={{ display: 'block', fontWeight: 600 }}>{dose.med.name}</span>
          {detail && <span className="small muted ellipsis" style={{ display: 'block' }}>{detail}</span>}
        </span>
        {dose.intake ? (
          <>
            {dose.taken ? <Pill text={`Tomada ${Fmt.time(dose.intake.timestamp)}`} color="var(--good)" /> : <Pill text="Omitida" color="var(--muted)" />}
            <button type="button" className="icon-btn" aria-label="Deshacer" onClick={props.onUndo} style={{ color: 'var(--muted)' }}><Undo2 size={22} /></button>
          </>
        ) : late ? (
          <Pill text="Atrasada" color="var(--warn)" />
        ) : null}
      </div>
      {dose.pending && (
        <div className="row" style={{ justifyContent: 'flex-end', marginTop: 6 }}>
          <Button variant="text" onClick={props.onSkip}>Omitir</Button>
          <Button variant="tonal" small onClick={props.onTake}>Tomada</Button>
        </div>
      )}
    </div>
  )
}

export function AdherencePill(props: { label: string; a: Adherence }) {
  const p = props.a.percent
  const color = p === null ? 'var(--muted)' : p >= 80 ? 'var(--good)' : p >= 50 ? 'var(--warn)' : 'var(--bad)'
  return <Pill text={`${props.label}: ${p === null ? '—' : `${p}%`}`} color={color} />
}
