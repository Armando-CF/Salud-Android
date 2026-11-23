import { Bug, CalendarCheck, Pill as PillIcon, ShieldCheck, Syringe, type LucideIcon } from 'lucide-react'
import { VaccinationTypes } from '../../data/catalogs'
import { Fmt } from '../../lib/dates'
import { Button, Pill } from '../../ui'
import { CalendarButton } from '../meds/common'
import '../meds/meds.css'
import { DESP_EXTERNA, DESP_INTERNA, VACUNA } from './catalog'
import { DUE_STATUS_LABEL, dueStatus, dueText, nextDose, type DueItem, type DueStatus } from './logic'

/** Icono según el tipo de registro. */
export const vaccineTypeIcon = (type: string): LucideIcon => (type === VACUNA ? Syringe : type === DESP_INTERNA ? PillIcon : type === DESP_EXTERNA ? Bug : ShieldCheck)

/** Color según el tipo de registro. */
export const vaccineTypeColor = (type: string): string =>
  type === VACUNA ? 'var(--primary)' : type === DESP_INTERNA ? 'var(--warn)' : type === DESP_EXTERNA ? 'var(--ovulation)' : 'var(--chart-3)'

export const dueStatusColor = (status: DueStatus): string => (status === 'VENCIDA' ? 'var(--bad)' : status === 'PROXIMA' ? 'var(--warn)' : 'var(--primary)')

/** Insignia cuadrada con el icono del tipo. */
export function VaccineTypeBadge(props: { type: string; small?: boolean }) {
  const I = vaccineTypeIcon(props.type)
  const color = vaccineTypeColor(props.type)
  return (
    <span className={`md-badge${props.small ? ' sm' : ''}`} style={{ background: `color-mix(in srgb, ${color} 15%, transparent)`, color }} title={VaccinationTypes.label(props.type)}>
      <I size={22} />
    </span>
  )
}

/** Título del módulo según el perfil. */
export const vaccinesTitle = (isPet: boolean): string => (isPet ? 'Vacunas y desparasitación' : 'Vacunas')

export const vaccinesDisclaimer = (isPet: boolean): string =>
  isPet
    ? 'Los nombres y fechas sugeridas son orientativos. Sigue la cartilla de tu mascota y lo que indique su veterinaria o veterinario.'
    : 'Los nombres y fechas sugeridas son orientativos. Sigue tu cartilla de vacunación y lo que indique tu médica o médico.'

/**
 * Fila de una próxima aplicación pendiente (reutilizable, p. ej. en la pantalla de inicio).
 * - `onRegister` abre el alta prellenada; si se omite no se muestra el botón.
 * - `onCalendar` entrega el evento para el Calendario; si se omite no se muestra el botón.
 */
export function DueRow(props: { item: DueItem; today: number; onOpen: () => void; onRegister?: () => void; onCalendar?: () => void }) {
  const { item, today } = props
  const v = item.vaccination
  const status = dueStatus(item.dueDay, today)
  const color = dueStatusColor(status)
  const next = nextDose(v.dose)
  return (
    <div data-testid={`due-${v.id}`}>
      <div className="row tap" role="button" tabIndex={0} onClick={props.onOpen} style={{ cursor: 'pointer' }}>
        <VaccineTypeBadge type={v.type} small />
        <span className="grow">
          <span style={{ display: 'block', fontWeight: 700, overflowWrap: 'anywhere' }}>{v.name}{next ? ` · ${next}` : ''}</span>
          <span className="small" style={{ display: 'block', color: status === 'PROGRAMADA' ? 'var(--muted)' : color, fontWeight: status === 'PROGRAMADA' ? 400 : 600 }}>
            {dueText(item.dueDay, today)} · {Fmt.date(item.dueDay)}
          </span>
        </span>
        <Pill text={DUE_STATUS_LABEL[status]} color={color} />
      </div>
      {(props.onRegister || props.onCalendar) && (
        <div className="row wrap" style={{ marginTop: 8, paddingLeft: 50, gap: 8 }}>
          {props.onRegister && (
            <Button variant={status === 'PROGRAMADA' ? 'outline' : 'tonal'} small icon={CalendarCheck} onClick={props.onRegister}>Registrar aplicación</Button>
          )}
          {props.onCalendar && !item.overdue && <CalendarButton small variant="text" label="Al calendario" onClick={props.onCalendar} />}
        </div>
      )}
    </div>
  )
}
