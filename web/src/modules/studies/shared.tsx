// Piezas de interfaz compartidas por las pantallas de estudios.
import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Droplets, Ear, Eye, FileText, FlaskConical, Flower2, HeartPulse, ScanFace, ScanSearch, Share, Smile, Stethoscope, Syringe, X,
  type LucideIcon,
} from 'lucide-react'
import { StudyCategories } from '../../data/catalogs'
import { saveFile } from '../../lib/files'
import { Button, Pill, Sheet, useToast } from '../../ui'
import { flag, type RangeFlag } from './catalog'
import { VACCINES_MOVED } from './profile'
import './studies.css'

/** Color para la bandera de rango: verde en rango, ámbar fuera de rango, neutro sin rango. */
export const flagColor = (f: RangeFlag): string => (f === 'NORMAL' ? 'var(--good)' : f === 'SIN_RANGO' ? 'var(--muted)' : 'var(--warn)')

export const statusColor = (status: string): string => (status === 'normal' ? 'var(--good)' : status === 'seguimiento' ? 'var(--warn)' : 'var(--muted)')

/** Etiqueta de estado del estudio (no dibuja nada si no hay estado). */
export function StatusPill(props: { status: string }) {
  const label = StudyCategories.statusLabel(props.status)
  return label ? <Pill text={label} color={statusColor(props.status)} /> : null
}

const ICONS: Record<string, LucideIcon> = {
  lab: FlaskConical, imagen: ScanSearch, chequeo: Stethoscope, cardio: HeartPulse, gine: Flower2, uro: Droplets, dental: Smile,
  vista: Eye, oido: Ear, piel: ScanFace, vacuna: Syringe,
}
export const categoryIcon = (category: string): LucideIcon => ICONS[category] ?? FileText

/** Icono de categoría dentro de un cuadro redondeado. */
export function CategoryBadge(props: { category: string }) {
  const I = categoryIcon(props.category)
  return <span className="st-badge" aria-hidden><I size={24} /></span>
}

/** Barra que muestra dónde cae `value` respecto a su rango de referencia. */
export function RangeBar(props: { value: number; refMin: number | null; refMax: number | null }) {
  const { value, refMin, refMax } = props
  if (refMin == null && refMax == null) return null
  let lo: number, hi: number
  if (refMin != null && refMax != null) {
    const span = refMax - refMin > 0 ? refMax - refMin : 1
    lo = refMin - span * 0.5
    hi = refMax + span * 0.5
  } else if (refMax != null) {
    lo = 0
    hi = refMax > 0 ? refMax * 1.5 : refMax + 1
  } else {
    const m = refMin as number
    lo = m > 0 ? m * 0.5 : m - 1
    hi = m > 0 ? m * 2 : m + 1
  }
  const frac = (v: number) => Math.min(1, Math.max(0, (v - lo) / (hi - lo)))
  const z0 = frac(refMin ?? lo) * 100, z1 = frac(refMax ?? hi) * 100
  return (
    <div className="st-range" aria-hidden>
      <div className="track" />
      {z1 > z0 && <div className="zone" style={{ left: `${z0}%`, width: `${z1 - z0}%` }} />}
      <div className="dot" style={{ left: `clamp(6px, ${frac(value) * 100}%, calc(100% - 6px))`, background: flagColor(flag(value, refMin, refMax)) }} />
    </div>
  )
}

/** Aviso con enlace a la sección de vacunas (para los estudios guardados con la categoría "Vacuna"). */
export function VaccinesMovedNote() {
  const nav = useNavigate()
  return (
    <div className="note">
      {VACCINES_MOVED} Ahí puedes anotar la fecha de aplicación y cuándo toca el refuerzo.
      <div><Button variant="text" small onClick={() => nav('/vacunas')}>Ir a Vacunas</Button></div>
    </div>
  )
}

// ── Adjuntos ───────────────────────────────────────────────────────────────────────────────────

/** Adjunto en pantalla: los ya guardados tienen id; los recién agregados aún no. */
export interface AttView { key: string; id?: number; displayName: string; mime: string; data: ArrayBuffer }

/** A partir de este tamaño se avisa con suavidad (ocupa espacio del dispositivo y del respaldo). */
export const BIG_FILE_BYTES = 10 * 1024 * 1024

const IMAGE_EXT = ['jpg', 'jpeg', 'png', 'webp', 'gif', 'heic', 'heif']
export const isImage = (a: { mime: string; displayName: string }): boolean =>
  a.mime.startsWith('image/') || IMAGE_EXT.includes(a.displayName.split('.').pop()?.toLowerCase() ?? '')
export const isPdf = (a: { mime: string; displayName: string }): boolean => a.mime === 'application/pdf' || /\.pdf$/i.test(a.displayName)

export const sizeText = (bytes: number): string => (bytes >= 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`)

/** URL de objeto para mostrar el contenido; se libera al desmontar o al cambiar el contenido. */
export function useObjectUrl(data: ArrayBuffer | null, mime: string): string | null {
  const [url, setUrl] = useState<string | null>(null)
  useEffect(() => {
    if (!data) { setUrl(null); return }
    const u = URL.createObjectURL(new Blob([data], { type: mime || 'application/octet-stream' }))
    setUrl(u)
    return () => URL.revokeObjectURL(u)
  }, [data, mime])
  return url
}

/** Miniatura de un adjunto: imagen recortada o icono de documento. `onRemove` muestra la X (en el editor). */
export function AttachmentTile(props: { att: AttView; onOpen: () => void; onRemove?: () => void }) {
  const { att } = props
  const image = isImage(att)
  const url = useObjectUrl(image ? att.data : null, att.mime)
  return (
    <div className="st-att" data-att={att.displayName}>
      <button type="button" className="thumb" onClick={props.onOpen} aria-label={`Abrir ${att.displayName}`}>
        {image && url ? <img src={url} alt={att.displayName} /> : <FileText size={34} />}
      </button>
      <div className="cap">{isPdf(att) && !/\.pdf$/i.test(att.displayName) ? 'PDF · ' : ''}{att.displayName}</div>
      {props.onRemove && (
        <button type="button" className="rm" onClick={props.onRemove} aria-label={`Quitar ${att.displayName}`}><X size={16} /></button>
      )}
    </div>
  )
}

/**
 * Abre adjuntos: las imágenes en una hoja a pantalla completa; PDF y otros con la hoja de compartir / visor del sistema.
 * Uso: `const { open, viewer } = useAttachmentViewer()` y renderiza `{viewer}`.
 */
export function useAttachmentViewer(): { open: (a: AttView) => void; viewer: JSX.Element } {
  const [current, setCurrent] = useState<AttView | null>(null)
  const toast = useToast()
  const share = async (a: AttView) => {
    try {
      await saveFile(a.displayName, a.data, a.mime || 'application/octet-stream')
    } catch {
      toast('No se pudo abrir el archivo.')
    }
  }
  const open = (a: AttView) => { if (isImage(a)) setCurrent(a); else void share(a) }
  const viewer = <ImageViewer att={current} onClose={() => setCurrent(null)} onShare={share} />
  return { open, viewer }
}

function ImageViewer(props: { att: AttView | null; onClose: () => void; onShare: (a: AttView) => void }) {
  const url = useObjectUrl(props.att?.data ?? null, props.att?.mime ?? '')
  const att = props.att
  const title = useMemo(() => att?.displayName ?? '', [att])
  return (
    <Sheet open={att !== null} onClose={props.onClose} title={title} full>
      {att && (
        <div className="st-viewer">
          {url && <img src={url} alt={att.displayName} />}
          <div className="small muted">{sizeText(att.data.byteLength)}</div>
          <Button variant="outline" icon={Share} onClick={() => props.onShare(att)}>Compartir o guardar</Button>
        </div>
      )}
    </Sheet>
  )
}
