import { useEffect, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { useLiveQuery } from 'dexie-react-hooks'
import {
  Angry, CalendarClock, ChevronDown, ChevronRight, ClipboardCheck, Download, Droplet, FlaskConical, Frown, HeartPulse, Laugh,
  Meh, NotebookPen, PersonStanding, Pill as PillIcon, Scale, Settings, Share, Smile, Syringe, UserRound, Users, X, type LucideIcon,
} from 'lucide-react'
import { AVATAR_COLORS, PetSpecies } from '../../data/catalogs'
import { dbFor } from '../../data/db'
import { getSetting, usePref } from '../../data/prefs'
import { activateProfile, allProfiles, displayName, isPet as infoIsPet, profileSubtitle, useActive, useProfileRow } from '../../data/profiles'
import { useProfileList } from '../profile'
import type { DailyLog, Profile, ProfileInfo } from '../../data/types'
import { Fmt, millisToEpochDay, nowMillis, today } from '../../lib/dates'
import { isIos, isStandalone } from '../../lib/files'
import { Avatar, Card, IconButton, NavRow, Page, PawIcon, Sparkline, intensityColor } from '../../ui'
import { CycleSummaryCard } from '../cycle'
import { upcomingFollowUps } from '../studies'
import { weightSummary, weightText as measureWeightText } from '../measure'
import './home.css'

export const MOOD_ICONS: LucideIcon[] = [Angry, Frown, Meh, Smile, Laugh]
export const MOOD_LABELS = ['Muy mal', 'Mal', 'Regular', 'Bien', 'Excelente']
const MOOD_COLORS = ['var(--bad)', 'var(--warn)', 'var(--outline)', 'var(--good)', 'var(--good)']

/** Avatar de un perfil a partir de su resumen. */
export function ProfileAvatar(props: { info: ProfileInfo; size?: number }) {
  return <Avatar name={displayName(props.info)} color={AVATAR_COLORS[props.info.avatarColor % AVATAR_COLORS.length]} pet={infoIsPet(props.info)} size={props.size} />
}

function Badge(props: { icon: ReactNode; color: string }) {
  return <span className="home-badge" style={{ background: `color-mix(in srgb, ${props.color} 15%, transparent)`, color: props.color }}>{props.icon}</span>
}

/** Peso para tarjetas: en mascotas pequeñas se muestra en gramos. */
export const weightText = (kg: number, pet: boolean): string => measureWeightText(kg, pet)

/** Lo pendiente HOY de otro perfil (se completa al integrar los módulos de medicamentos y vacunas). */
export interface OtherStatus { info: ProfileInfo; notes: string[]; urgent: boolean }

/** Extensiones que aportan otros módulos a la pantalla de inicio (se registran en `homeParts.ts`). */
export interface HomeParts {
  /** Tarjeta del ciclo (solo si el módulo está activo). */
  cycleCard?: () => ReactNode
  /** Tarjeta de medicamentos de hoy. */
  medsCard?: () => ReactNode
  /** Tarjetas de vacunas pendientes o próximas. */
  vaccineCards?: () => ReactNode
  /** Tarjetas de seguimientos de estudios. */
  followUpCards?: () => ReactNode
  /** Pendientes de otros perfiles. */
  others?: (profiles: ProfileInfo[], day: number) => Promise<OtherStatus[]>
}
export const homeParts: HomeParts = {}

export function HomePage() {
  const nav = useNavigate()
  const { info, db, isPet } = useActive()
  const t = today()
  const profile = useProfileRow()
  const profiles = useProfileList()
  const [cycleEnabled] = usePref('cycleEnabled')
  const symptoms = useLiveQuery(() => db.symptoms.orderBy('timestamp').reverse().limit(3).toArray(), [db]) ?? []
  const weights = useLiveQuery(() => db.measurements.where('type').equals('weight').sortBy('timestamp'), [db]) ?? []
  const log = useLiveQuery(async () => ({ row: await db.dailyLogs.get(t) }), [db, t])?.row
  const appts = useLiveQuery(() => db.appointments.where('timestamp').aboveOrEqual(nowMillis() - 3_600_000).filter((a) => !a.done).sortBy('timestamp'), [db]) ?? []
  const counts = useLiveQuery(async () => (await db.symptoms.count()) + (await db.measurements.count()) + (await db.cycleDays.count()) + (await db.vaccinations.count()) + (await db.medications.count()), [db])
  const others = useLiveQuery(async () => (homeParts.others ? homeParts.others((await allProfiles()).filter((p) => p.id !== info.id), t) : []), [info.id, t]) ?? []
  const lastBackup = useLiveQuery(() => getSetting('lastBackup'), []) ?? 0
  const studies = useLiveQuery(() => db.studies.toArray(), [db]) ?? []
  const followUps = upcomingFollowUps(studies, t).filter((s) => (s.followUpEpochDay ?? 0) <= t + 45).slice(0, 2)

  const name = (profile?.name ?? info.name).trim()
  const self = !isPet && info.id === 1
  const title = self ? (name ? `Hola, ${name.split(' ')[0]}` : 'Hola') : displayName(info)
  const subtitle = [self ? '' : profileSubtitle(info), Fmt.dateLong(t)].filter(Boolean).join(' · ')
  const ws = weightSummary(weights, isPet ? null : profile?.heightCm ?? null, profile?.goalWeightKg ?? null)
  const empty = counts === 0 && appts.length === 0
  const backupDue = (counts ?? 0) > 0 && (lastBackup === 0 || nowMillis() - lastBackup > 30 * 86_400_000)

  const setMood = async (mood: number) => {
    const cur: DailyLog = (await db.dailyLogs.get(t)) ?? { epochDay: t, mood: null, energy: null, stress: null, sleepHours: null, waterGlasses: null, exerciseMin: null, notes: '' }
    await db.dailyLogs.put({ ...cur, mood })
  }

  return (
    <Page
      title={title}
      subtitle={subtitle}
      actions={
        <>
          <button className="home-profile-btn" onClick={() => nav('/perfiles')} aria-label="Cambiar de perfil">
            <ProfileAvatar info={info} size={34} /><ChevronDown size={18} />
          </button>
          <IconButton icon={Settings} label="Ajustes" onClick={() => nav('/ajustes')} />
        </>
      }
    >
      <InstallHint />

      <div className="home-quick">
        <Quick icon={<NotebookPen size={25} />} label="Síntoma" color="var(--primary)" onClick={() => nav('/sintomas/nuevo')} />
        <Quick icon={<Scale size={25} />} label="Peso" color="var(--chart-3)" onClick={() => nav('/mediciones/weight?add=1')} />
        {isPet ? (
          <>
            <Quick icon={<Syringe size={25} />} label="Vacuna" color="var(--good)" onClick={() => nav('/vacunas/nueva')} />
            <Quick icon={<CalendarClock size={25} />} label="Cita" color="var(--tertiary)" onClick={() => nav('/citas/nueva')} />
          </>
        ) : (
          <>
            <Quick icon={<Smile size={25} />} label="Bienestar" color="var(--warn)" onClick={() => nav('/bienestar')} />
            {cycleEnabled
              ? <Quick icon={<Droplet size={25} />} label="Periodo" color="var(--period)" onClick={() => nav(`/ciclo/dia/${t}`)} />
              : <Quick icon={<HeartPulse size={25} />} label="Presión" color="var(--period)" onClick={() => nav('/mediciones/bp?add=1')} />}
          </>
        )}
      </div>

      {others.length > 0 && (
        <Card title="Tu familia y mascotas" action={<button className="btn text small" onClick={() => nav('/perfiles')}>Perfiles</button>}>
          {others.map((o) => (
            <div key={o.info.id} className="list-row tap" onClick={() => { void activateProfile(o.info.id) }}>
              <ProfileAvatar info={o.info} size={38} />
              <div className="grow">
                <div className="ellipsis" style={{ fontWeight: 600 }}>{displayName(o.info)}</div>
                <div className="small" style={{ color: o.urgent ? 'var(--warn)' : 'var(--muted)' }}>{o.notes.join(' · ')}</div>
              </div>
              <ChevronRight size={20} color="var(--muted)" />
            </div>
          ))}
        </Card>
      )}

      {cycleEnabled && !isPet && <CycleSummaryCard />}

      {!isPet && (
        <Card
          title={log?.mood ? `Hoy te sientes: ${MOOD_LABELS[log.mood - 1].toLowerCase()}` : '¿Cómo te sientes hoy?'}
          action={<button className="btn text small" onClick={() => nav('/bienestar')}>{log?.mood ? 'Detalles' : 'Más'}</button>}
        >
          <div className="home-moods">
            {MOOD_ICONS.map((I, i) => {
              const sel = log?.mood === i + 1
              return (
                <button key={i} aria-label={MOOD_LABELS[i]} aria-pressed={sel} onClick={() => { void setMood(i + 1) }}
                  style={{ background: sel ? `color-mix(in srgb, ${MOOD_COLORS[i]} 20%, transparent)` : undefined, color: sel || !log?.mood ? MOOD_COLORS[i] : 'var(--line)' }}>
                  <I size={sel ? 40 : 34} />
                </button>
              )
            })}
          </div>
        </Card>
      )}

      {homeParts.medsCard?.()}

      {appts[0] && (
        <Card onClick={() => nav('/citas')}>
          <div className="row">
            <Badge icon={<CalendarClock size={24} />} color="var(--tertiary)" />
            <div className="grow">
              <div className="home-kicker" style={{ color: 'var(--tertiary)' }}>Próxima cita · {Fmt.daysFromNow(millisToEpochDay(appts[0].timestamp))}</div>
              <div className="home-title ellipsis">{appts[0].title}</div>
              <div className="small muted">{[`${Fmt.dateWeekday(millisToEpochDay(appts[0].timestamp))}, ${Fmt.time(appts[0].timestamp)}`, appts[0].doctor].filter(Boolean).join(' · ')}</div>
            </div>
          </div>
        </Card>
      )}

      {homeParts.vaccineCards?.()}

      {ws && (
        <Card onClick={() => nav('/mediciones/weight')}>
          <div className="row">
            <div className="grow">
              <div className="home-kicker" style={{ color: 'var(--primary)' }}>Peso</div>
              <div style={{ fontSize: 25, fontWeight: 700 }}>{weightText(ws.latest, isPet)}</div>
              <div className="small muted">
                {[
                  ws.change30 !== null ? `${Fmt.signed(ws.change30, isPet ? 2 : 1)} kg en 30 días` : '',
                  !isPet && ws.bmi !== null ? `IMC ${Fmt.num(ws.bmi, 1)}` : '',
                ].filter(Boolean).join(' · ') || Fmt.relativeDay(millisToEpochDay(ws.latestTimestamp))}
              </div>
            </div>
            <Sparkline values={ws.spark} />
          </div>
        </Card>
      )}

      {symptoms.length > 0 && (
        <Card title="Síntomas recientes" action={<button className="btn text small" onClick={() => nav('/sintomas')}>Ver todos</button>}>
          {symptoms.map((s) => (
            <div key={s.id} className="list-row tap" onClick={() => nav(`/sintomas/${s.id}`)}>
              <span className="home-dot" style={{ background: intensityColor(s.intensity) }} />
              <div className="grow">
                <div className="ellipsis" style={{ fontWeight: 600 }}>{s.name}</div>
                <div className="small muted ellipsis">{[Fmt.relativeDateTime(s.timestamp), s.regionLabel].filter(Boolean).join(' · ')}</div>
              </div>
              <b style={{ color: intensityColor(s.intensity) }}>{s.intensity}/10</b>
            </div>
          ))}
        </Card>
      )}

      {followUps.map((st) => (
        <Card key={st.id} onClick={() => nav(`/estudios/${st.id}`)}>
          <div className="row">
            <Badge icon={<FlaskConical size={24} />} color="var(--warn)" />
            <div className="grow">
              <div className="home-kicker" style={{ color: 'var(--warn)' }}>Seguimiento · {Fmt.daysFromNow(st.followUpEpochDay!)}</div>
              <div className="home-title ellipsis">{st.title}</div>
            </div>
          </div>
        </Card>
      ))}

      {backupDue && (
        <Card onClick={() => nav('/exportar')}>
          <div className="row">
            <Badge icon={<Download size={24} />} color="var(--warn)" />
            <div className="grow">
              <div className="home-title">Guarda un respaldo</div>
              <div className="small muted">{lastBackup === 0 ? 'Aún no has exportado tus datos.' : `Tu último respaldo fue ${Fmt.daysFromNow(millisToEpochDay(lastBackup))}.`} Tus datos viven solo en este dispositivo.</div>
            </div>
            <ChevronRight size={20} color="var(--muted)" />
          </div>
        </Card>
      )}

      {empty && (
        <Card title="Empieza por aquí">
          {isPet ? (
            <>
              <p className="muted">Aquí llevarás el historial de salud de {displayName(info)}: lo que anotes se queda solo en tu dispositivo.</p>
              <NavRow icon={Syringe} title="Registra sus vacunas" subtitle="Vacunas y desparasitación, con fecha de la próxima" onClick={() => nav('/vacunas')} />
              <NavRow icon={Scale} title="Registra su peso" subtitle="Con gráfica y tendencia" onClick={() => nav('/mediciones/weight?add=1')} />
              <NavRow icon={NotebookPen} title="Anota lo que le notes" subtitle="Vómito, cojera, comezón, falta de apetito…" onClick={() => nav('/sintomas/nuevo')} />
              <NavRow icon={ClipboardCheck} title="Completa su ficha" subtitle="Raza, edad, microchip, veterinario" onClick={() => nav('/ficha')} />
            </>
          ) : (
            <>
              <p className="muted">Savia es tu bitácora de salud: todo lo que anotes se queda solo en tu dispositivo.</p>
              <NavRow icon={NotebookPen} title="Anota cómo te sientes" subtitle="Síntomas, con intensidad y zona del cuerpo" onClick={() => nav('/sintomas/nuevo')} />
              <NavRow icon={Scale} title="Registra tu peso" subtitle="Con gráficas y tendencias" onClick={() => nav('/mediciones/weight?add=1')} />
              <NavRow icon={PersonStanding} title="Explora el cuerpo humano" subtitle="Nombres de zonas, huesos y órganos" onClick={() => nav('/cuerpo')} />
              <NavRow icon={UserRound} title="Completa tu ficha" subtitle="Tipo de sangre, alergias, contacto de emergencia" onClick={() => nav('/ficha')} />
              {profiles.length <= 1 && <NavRow icon={Users} title="Agrega a tu familia y mascotas" subtitle="Cada quien con su propio historial" onClick={() => nav('/perfiles')} />}
            </>
          )}
        </Card>
      )}
    </Page>
  )
}

function Quick(props: { icon: ReactNode; label: string; color: string; onClick: () => void }) {
  return (
    <button onClick={props.onClick} style={{ background: `color-mix(in srgb, ${props.color} 14%, transparent)` }}>
      <span style={{ color: props.color }}>{props.icon}</span>
      {props.label}
    </button>
  )
}

/** Aviso para instalar la app en la pantalla de inicio del iPhone (solo en Safari, sin instalar). */
function InstallHint() {
  const [show, setShow] = useState(false)
  useEffect(() => {
    let hidden = false
    try { hidden = sessionStorage.getItem('savia-install-hint') === '0' } catch { /* sin almacenamiento */ }
    setShow(!hidden && isIos() && !isStandalone())
  }, [])
  if (!show) return null
  const close = () => { try { sessionStorage.setItem('savia-install-hint', '0') } catch { /* nada */ } setShow(false) }
  return (
    <div className="home-install" role="note">
      <Share size={24} style={{ flex: '0 0 auto', marginTop: 2 }} />
      <div className="grow small">
        <b>Instala Savia en tu iPhone</b>
        En Safari toca el botón Compartir y luego “Agregar a inicio”. Así se abre como una app, a pantalla completa y sin conexión.
      </div>
      <button className="icon-btn" aria-label="Cerrar aviso" onClick={close} style={{ margin: '-8px -8px 0 0' }}><X size={20} /></button>
    </div>
  )
}

export function MorePage() {
  const nav = useNavigate()
  const { info, isPet } = useActive()
  const profile = useProfileRow()
  const profiles = useProfileList()
  const [cycleEnabled] = usePref('cycleEnabled')
  return (
    <Page title="Más">
      <Card onClick={() => nav('/perfiles')}>
        <div className="row">
          <ProfileAvatar info={info} size={50} />
          <div className="grow">
            <div className="home-title ellipsis">{displayName(info)}</div>
            <div className="small muted">{profiles.length > 1 ? `Viendo este perfil · toca para cambiar (${profiles.length} perfiles)` : 'Agrega a tu familia y a tus mascotas'}</div>
          </div>
          <Users size={22} color="var(--primary)" />
        </div>
      </Card>
      <Card title="Registro y seguimiento">
        {!isPet && (cycleEnabled
          ? <NavRow icon={Scale} title="Mediciones" subtitle="Peso, estatura, presión, glucosa…" color="var(--chart-3)" onClick={() => nav('/mediciones')} />
          : <NavRow icon={Droplet} title="Ciclo menstrual" subtitle="Activa el seguimiento del periodo" color="var(--period)" onClick={() => nav('/ciclo/ajustes')} />)}
        <NavRow icon={Syringe} title={isPet ? 'Vacunas y desparasitación' : 'Vacunas'} subtitle={isPet ? 'Cartilla y próximas aplicaciones' : 'Cartilla de vacunación y próximos refuerzos'} color="var(--good)" onClick={() => nav('/vacunas')} />
        {!isPet && <NavRow icon={Smile} title="Bienestar diario" subtitle="Ánimo, energía, sueño, agua y ejercicio" color="var(--warn)" onClick={() => nav('/bienestar')} />}
        <NavRow icon={PillIcon} title="Medicamentos" subtitle="Tomas del día y tratamientos" color="var(--chart-1)" onClick={() => nav('/medicamentos')} />
        <NavRow icon={CalendarClock} title={isPet ? 'Citas con el veterinario' : 'Citas médicas'} subtitle="Próximas consultas" color="var(--tertiary)" onClick={() => nav('/citas')} />
        <NavRow icon={FlaskConical} title="Estudios y chequeos" subtitle="Resultados de laboratorio y su evolución" color="var(--chart-5)" onClick={() => nav('/estudios')} />
        {!isPet && <NavRow icon={ClipboardCheck} title="Autoexploración" subtitle="Mamaria, testicular y de piel" color="var(--ovulation)" onClick={() => nav('/autoexploracion')} />}
      </Card>
      <Card title={isPet ? `Información de ${displayName(info)}` : 'Tu información'}>
        <NavRow icon={isPet ? (PawGlyph as unknown as LucideIcon) : UserRound} title={isPet ? 'Ficha' : 'Mi ficha'} subtitle={fichaSubtitle(profile)} onClick={() => nav('/ficha')} />
        <NavRow icon={Users} title="Perfiles" subtitle="Familia y mascotas, cada quien con su historial" onClick={() => nav('/perfiles')} />
        <NavRow icon={Download} title="Exportar y respaldo" subtitle="Respaldo .json, Excel, CSV e impresión" onClick={() => nav('/exportar')} />
        <NavRow icon={Settings} title="Ajustes" subtitle="Tema, bloqueo con PIN, instalación y respaldo" onClick={() => nav('/ajustes')} />
      </Card>
      <p className="small muted" style={{ padding: '0 8px' }}>
        Savia no usa servidores: tus datos viven solo en este dispositivo. Haz un respaldo de vez en cuando.
      </p>
    </Page>
  )
}

const PawGlyph = (props: { size?: number }) => <PawIcon size={props.size} />

function fichaSubtitle(p: Profile | undefined): string {
  if (!p) return 'Datos personales y de emergencia'
  if (p.kind === 'pet') return [PetSpecies.label(p.species), p.breed, p.birthEpochDay !== null ? Fmt.ageText(p.birthEpochDay) : ''].filter(Boolean).join(' · ')
  const parts = [p.name, p.birthEpochDay !== null ? `${Fmt.age(p.birthEpochDay)} años` : '', p.bloodType ? `Sangre ${p.bloodType}` : ''].filter(Boolean)
  return parts.length ? parts.join(' · ') : 'Datos personales y de emergencia'
}
