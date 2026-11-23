import type { ReactNode } from 'react'
import { Check, Phone, type LucideIcon } from 'lucide-react'
import { AVATAR_COLORS } from '../../data/catalogs'
import type { ProfileInfo } from '../../data/types'
import { Avatar, Field } from '../../ui'
import { cleanPhone } from './logic'
import './profile.css'

/** Paleta de 8 colores para distinguir perfiles (índice = `Profile.avatarColor`). */
export const ProfileColors = {
  all: AVATAR_COLORS,
  names: ['Verde', 'Rosa', 'Azul', 'Ámbar', 'Morado', 'Olivo', 'Coral', 'Pizarra'],
  /** Color del índice dado (cualquier entero; se ajusta al tamaño de la paleta). */
  of: (index: number): string => AVATAR_COLORS[((index % AVATAR_COLORS.length) + AVATAR_COLORS.length) % AVATAR_COLORS.length],
}

/** Avatar de un perfil: círculo de color con la inicial del nombre (personas) o una huella (mascotas). */
export function ProfileAvatar(props: { info: Pick<ProfileInfo, 'name' | 'kind' | 'avatarColor'>; size?: number }) {
  return <Avatar name={props.info.name} color={ProfileColors.of(props.info.avatarColor)} pet={props.info.kind === 'pet'} size={props.size ?? 40} />
}

/** Selector del color del avatar: una sola fila que reparte el ancho (los 8 colores caben en cualquier pantalla). */
export function AvatarColorPicker(props: { value: number; onChange: (i: number) => void }) {
  const sel = ((props.value % ProfileColors.all.length) + ProfileColors.all.length) % ProfileColors.all.length
  return (
    <div className="pf-colors" role="radiogroup" aria-label="Color del perfil">
      {ProfileColors.all.map((c, i) => (
        <button
          key={c}
          type="button"
          role="radio"
          aria-checked={i === sel}
          aria-label={`Color ${ProfileColors.names[i].toLowerCase()}`}
          className={i === sel ? 'on' : ''}
          onClick={() => props.onChange(i)}
        >
          <i style={{ background: c }}>{i === sel && <Check size={16} />}</i>
        </button>
      ))}
    </div>
  )
}

export function Hint(props: { children: ReactNode }) {
  return <div className="pf-hint">{props.children}</div>
}

/** Grupo con etiqueta arriba (para chips). */
export function Labeled(props: { label: string; children: ReactNode; hint?: string }) {
  return (
    <div>
      <div className="pf-label">{props.label}</div>
      {props.children}
      {props.hint && <div className="pf-hint" style={{ marginTop: 5 }}>{props.hint}</div>}
    </div>
  )
}

/** Teléfono con botón para llamar (abre el marcador; no llama por sí solo). */
export function PhoneField(props: { value: string; onChange: (v: string) => void; label: string; callLabel?: string }) {
  const number = props.value.trim()
  return (
    <Field label={props.label}>
      <div className="row">
        <input
          className="input grow"
          type="tel"
          inputMode="tel"
          autoComplete="off"
          enterKeyHint="done"
          value={props.value}
          onChange={(e) => props.onChange(cleanPhone(e.target.value))}
          onKeyDown={(e) => { if (e.key === 'Enter') (e.target as HTMLInputElement).blur() }}
        />
        <CallButton phone={number} label={props.callLabel ?? 'Llamar'} />
      </div>
    </Field>
  )
}

export function CallButton(props: { phone: string; label: string }) {
  const number = props.phone.trim()
  return (
    <a
      className="pf-call"
      href={number ? `tel:${encodeURIComponent(number)}` : undefined}
      aria-label={props.label}
      aria-disabled={!number}
      onClick={(e) => e.stopPropagation()}
    >
      <Phone size={21} />
    </a>
  )
}

/** Encabezado de un ajuste: icono, título y explicación, con un control opcional a la derecha. */
export function SettingHeader(props: { icon: LucideIcon; title: string; text?: string; color?: string; trailing?: ReactNode }) {
  const I = props.icon
  const color = props.color ?? 'var(--primary)'
  return (
    <div className="pf-setting">
      <span className="ic" style={{ background: `color-mix(in srgb, ${color} 15%, transparent)`, color }}><I size={22} /></span>
      <span className="grow">
        <span className="t" style={{ display: 'block' }}>{props.title}</span>
        {props.text && <span className="s" style={{ display: 'block' }}>{props.text}</span>}
      </span>
      {props.trailing}
    </div>
  )
}
