import { useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useLiveQuery } from 'dexie-react-hooks'
import { ArrowLeftRight, Check, Droplet, MoreVertical, Pencil, Trash2, UserPlus } from 'lucide-react'
import { PetSpecies, SexOptions } from '../../data/catalogs'
import { activateProfile, allProfiles, createProfile, displayName, switchProfile, useActive } from '../../data/profiles'
import type { PrefKey } from '../../data/prefs'
import type { Profile, ProfileInfo } from '../../data/types'
import { Fmt, toNumber, today } from '../../lib/dates'
import {
  Button, Card, Chips, ConfirmDialog, Dialog, InfoNote, NumberInput, OptionalDateInput, Page, PawIcon, Pill, SectionLabel, Sheet,
  TextInput, Toggle, useBack, useToast,
} from '../../ui'
import { AvatarColorPicker, Hint, Labeled, ProfileAvatar, ProfileColors } from './common'
import {
  RELATIONS, STERILIZED_OPTIONS, ageText, birthFromAge, heightError as heightInvalid, profileLine, removeProfile, sterilizedValue, suggestCycle,
} from './logic'

const REMOVE_WORD = 'ELIMINAR'

/** Lista de perfiles en vivo. Si un perfil se está eliminando justo al consultar, conserva la lista anterior. */
export function useProfileList(): ProfileInfo[] {
  const last = useRef<ProfileInfo[]>([])
  const list = useLiveQuery(async () => {
    try { return await allProfiles() } catch { return null }
  }, [])
  if (list) last.current = list
  return last.current
}

/** Lista de perfiles (personas y mascotas): cambiar de perfil, crear y eliminar. */
export function ProfilesPage() {
  const nav = useNavigate()
  const back = useBack()
  const toast = useToast()
  const { info: active } = useActive()
  const profiles = useProfileList()
  const [menu, setMenu] = useState<ProfileInfo | null>(null)
  const [removeAsk, setRemoveAsk] = useState<ProfileInfo | null>(null)
  const [removeConfirm, setRemoveConfirm] = useState<ProfileInfo | null>(null)
  const [typed, setTyped] = useState('')

  /** Cambia de perfil. La app se recrea: llega a Inicio o, si se pide, a otra pantalla del perfil nuevo. */
  const switchTo = async (p: ProfileInfo, route?: string) => {
    toast(`Ahora ves el perfil de ${displayName(p)}`)
    if (route) {
      window.location.hash = '#' + route
      await switchProfile(p.id)
    } else {
      await activateProfile(p.id)
    }
  }
  const open = (p: ProfileInfo) => { if (p.id === active.id) back(); else void switchTo(p) }
  const edit = (p: ProfileInfo) => { if (p.id === active.id) nav('/ficha'); else void switchTo(p, '/ficha') }

  const people = profiles.filter((p) => p.kind !== 'pet')
  const pets = profiles.filter((p) => p.kind === 'pet')
  const removeOk = typed.trim().toUpperCase() === REMOVE_WORD

  const row = (p: ProfileInfo) => {
    const isActive = p.id === active.id
    const sub = profileLine(p)
    return (
      <Card key={p.id} onClick={() => open(p)} className={isActive ? 'pf-active' : undefined}>
        <div className="pf-profile">
          <ProfileAvatar info={p} size={48} />
          <div className="grow">
            <div className="name">{displayName(p)}</div>
            {sub && <div className="sub">{sub}</div>}
            {isActive && <div style={{ marginTop: 4 }}><Pill text="Viendo ahora" color="var(--primary)" /></div>}
          </div>
          <button type="button" className="icon-btn" aria-label={`Opciones de ${displayName(p)}`} onClick={(e) => { e.stopPropagation(); setMenu(p) }}>
            <MoreVertical size={22} />
          </button>
        </div>
      </Card>
    )
  }

  return (
    <Page title="Perfiles" subtitle="Familia y mascotas">
      <InfoNote>Cada perfil tiene su propio historial, separado de los demás. Úsalos para llevar la salud de tu familia y de tus mascotas. Toca un perfil para verlo.</InfoNote>

      <SectionLabel>Personas</SectionLabel>
      {people.map(row)}
      <Button variant="tonal" icon={UserPlus} full onClick={() => nav('/perfiles/nuevo/person')}>Agregar persona</Button>

      <SectionLabel>Mascotas</SectionLabel>
      {pets.length === 0 && <Hint>Aún no has agregado mascotas. Puedes llevar su peso, vacunas, síntomas, medicamentos y visitas a la veterinaria.</Hint>}
      {pets.map(row)}
      <button type="button" className="btn tonal full" onClick={() => nav('/perfiles/nuevo/pet')}><PawIcon size={19} />Agregar mascota</button>

      <Sheet open={menu !== null} onClose={() => setMenu(null)} title={menu ? displayName(menu) : ''}>
        {menu && (
          <div className="stack tight">
            {menu.id !== active.id && (
              <Button variant="outline" icon={ArrowLeftRight} full onClick={() => { const p = menu; setMenu(null); void switchTo(p) }}>Ver este perfil</Button>
            )}
            <Button variant="outline" icon={Pencil} full onClick={() => { const p = menu; setMenu(null); edit(p) }}>Editar ficha</Button>
            {menu.id !== 1 && (
              <Button variant="danger" icon={Trash2} full onClick={() => { const p = menu; setMenu(null); setRemoveAsk(p) }}>Eliminar perfil</Button>
            )}
            {menu.id === 1 && <Hint>Este es el perfil principal y no se puede eliminar.</Hint>}
          </div>
        )}
      </Sheet>

      <ConfirmDialog
        open={removeAsk !== null}
        title={`¿Eliminar el perfil de ${removeAsk ? displayName(removeAsk) : ''}?`}
        text={`Se borrará TODO el historial de ${removeAsk ? displayName(removeAsk) : ''}: síntomas, mediciones, vacunas, estudios y sus archivos, medicamentos, citas y su ficha. Los demás perfiles no se tocan. No se puede deshacer: si quieres conservar algo, primero expórtalo en «Exportar y respaldo» desde ese perfil.`}
        confirmLabel="Continuar"
        onConfirm={() => { setTyped(''); setRemoveConfirm(removeAsk); setRemoveAsk(null) }}
        onClose={() => setRemoveAsk(null)}
      />

      <Dialog
        open={removeConfirm !== null}
        onClose={() => setRemoveConfirm(null)}
        title="Confirma la eliminación"
        actions={
          <>
            <Button variant="text" onClick={() => setRemoveConfirm(null)}>Cancelar</Button>
            <Button
              variant="text"
              disabled={!removeOk}
              style={{ color: 'var(--error)' }}
              onClick={() => {
                const p = removeConfirm!
                setRemoveConfirm(null)
                const name = displayName(p)
                void removeProfile(p.id).then(() => toast(`Se eliminó el perfil de ${name}`))
              }}
            >
              Eliminar perfil
            </Button>
          </>
        }
      >
        <p>Para eliminar definitivamente el perfil de {removeConfirm ? displayName(removeConfirm) : ''} y todo su historial, escribe {REMOVE_WORD}.</p>
        <TextInput value={typed} onChange={(v) => setTyped(v.slice(0, 12))} placeholder={REMOVE_WORD} autoCapitalize="none" />
      </Dialog>
    </Page>
  )
}

/** Alta de un perfil nuevo. Ruta /perfiles/nuevo/:kind ("person" | "pet"). Al guardar crea el perfil y lo activa. */
export function NewProfilePage() {
  const { kind } = useParams()
  const pet = kind === 'pet'
  const toast = useToast()
  const count = useProfileList().length
  const t = today()

  const [name, setName] = useState('')
  const [triedSave, setTriedSave] = useState(false)
  const [saving, setSaving] = useState(false)
  const [sex, setSex] = useState<string | null>(null)
  const [birth, setBirth] = useState<number | null>(null)
  const [color, setColor] = useState<number | null>(null)
  // Persona
  const [relation, setRelation] = useState<string | null>(null)
  const [relationOther, setRelationOther] = useState('')
  const [height, setHeight] = useState('')
  const [cycle, setCycle] = useState(false)
  const [cycleTouched, setCycleTouched] = useState(false)
  // Mascota
  const [species, setSpecies] = useState('perro')
  const [breed, setBreed] = useState('')
  const [sterilized, setSterilized] = useState('ns')
  const [markings, setMarkings] = useState('')
  const [microchip, setMicrochip] = useState('')
  const [approxAge, setApproxAge] = useState(false)
  const [ageYears, setAgeYears] = useState('')
  const [ageMonths, setAgeMonths] = useState('')

  const avatarColor = color ?? count % ProfileColors.all.length
  const heightErr = !pet && heightInvalid(height)
  const years = ageYears === '' ? null : parseInt(ageYears, 10)
  const months = ageMonths === '' ? null : parseInt(ageMonths, 10)
  const ageErr = approxAge && ((years !== null && years > 120) || (months !== null && months > 11))
  const effectiveBirth = pet && approxAge ? ((years === null && months === null) || ageErr ? null : birthFromAge(years ?? 0, months ?? 0, t)) : birth
  const nameErr = name.trim() === ''
  const cycleOn = !pet && (cycleTouched ? cycle : suggestCycle(sex, effectiveBirth))
  const valid = !nameErr && !heightErr && !ageErr

  const save = async () => {
    setTriedSave(true)
    if (!valid || saving) return
    setSaving(true)
    const cleanSex = sex ?? 'X'
    const profile: Partial<Profile> = pet
      ? {
          name: name.trim(), sex: cleanSex, birthEpochDay: effectiveBirth, kind: 'pet', species, breed: breed.trim(),
          sterilized: sterilizedValue(sterilized), markings: markings.trim(), microchip: microchip.trim(), avatarColor,
        }
      : {
          name: name.trim(), sex: cleanSex, birthEpochDay: effectiveBirth, heightCm: height.trim() === '' ? null : toNumber(height),
          kind: 'person', relation: (relation === 'Otro' ? relationOther : relation ?? '').trim(), avatarColor,
        }
    const prefs: Partial<Record<PrefKey, unknown>> = { cycleEnabled: cycleOn }
    if (cleanSex === 'F' || cleanSex === 'M') prefs.atlasSex = cleanSex
    try {
      const id = await createProfile(profile, prefs)
      toast(`Listo. Ahora ves el perfil de ${profile.name}`)
      // Recrea la app en Inicio con el perfil nuevo.
      await activateProfile(id)
    } catch {
      setSaving(false)
      toast('No se pudo crear el perfil. Inténtalo de nuevo.')
    }
  }

  return (
    <Page
      title={pet ? 'Nueva mascota' : 'Nueva persona'}
      actions={<button className="text-btn" disabled={saving} onClick={() => void save()}>Guardar</button>}
    >
      <div className="pf-preview">
        <ProfileAvatar info={{ name, kind: pet ? 'pet' : 'person', avatarColor }} size={56} />
        <div className="grow">
          <h2>{name.trim() || (pet ? 'Tu mascota' : 'Nuevo perfil')}</h2>
          <Hint>{pet ? 'Tendrá su propio historial: peso, vacunas, síntomas y visitas.' : 'Tendrá su propio historial, separado del tuyo.'}</Hint>
        </div>
      </div>

      {pet ? (
        <>
          <Card title="Datos de la mascota">
            <TextInput label="Nombre" value={name} onChange={(v) => setName(v.slice(0, 60))} autoCapitalize="words" error={triedSave && nameErr && 'Escribe su nombre'} />
            <Labeled label="Especie"><Chips options={PetSpecies.all} value={species} onChange={(v) => v && setSpecies(v)} /></Labeled>
            <TextInput label="Raza (opcional)" value={breed} onChange={(v) => setBreed(v.slice(0, 60))} autoCapitalize="words" placeholder="Mestizo, labrador, siamés…" />
            <Labeled label="Sexo"><Chips options={PetSpecies.sexes} value={sex} onChange={setSex} allowDeselect /></Labeled>
            <Labeled label="¿Está esterilizado/a?"><Chips options={STERILIZED_OPTIONS} value={sterilized} onChange={(v) => setSterilized(v ?? 'ns')} /></Labeled>
          </Card>
          <Card title="Edad">
            {!approxAge ? (
              <>
                <OptionalDateInput label="Fecha de nacimiento" value={birth} max={t} onChange={(d) => { if (d === null || d <= t) setBirth(d) }} />
                {birth !== null && <Hint>Edad: {ageText(birth, t, true)}</Hint>}
              </>
            ) : (
              <>
                <div className="row" style={{ alignItems: 'flex-start' }}>
                  <div className="grow"><NumberInput label="Años" value={ageYears} onChange={(v) => setAgeYears(v.slice(0, 3))} decimal={false} error={years !== null && years > 120 && 'Revisa la edad'} /></div>
                  <div className="grow"><NumberInput label="Meses" value={ageMonths} onChange={(v) => setAgeMonths(v.slice(0, 2))} decimal={false} error={months !== null && months > 11 && 'De 0 a 11'} /></div>
                </div>
                {effectiveBirth !== null && <Hint>Se guardará como nacimiento aproximado: {Fmt.date(effectiveBirth)}.</Hint>}
              </>
            )}
            <Toggle label="No sé la fecha exacta" sub="Indica su edad aproximada y Savia calcula la fecha." checked={approxAge} onChange={setApproxAge} />
          </Card>
          <Card title="Identificación (opcional)">
            <TextInput label="Color y señas particulares" value={markings} onChange={(v) => setMarkings(v.slice(0, 200))} placeholder="Café con manchas blancas, oreja doblada…" />
            <TextInput label="Microchip o placa" value={microchip} onChange={(v) => setMicrochip(v.slice(0, 40))} autoCapitalize="none" />
          </Card>
        </>
      ) : (
        <>
          <Card title="Datos de la persona">
            <TextInput label="Nombre" value={name} onChange={(v) => setName(v.slice(0, 60))} autoCapitalize="words" error={triedSave && nameErr && 'Escribe su nombre'} />
            <Labeled label="Parentesco contigo"><Chips options={RELATIONS} value={relation} onChange={setRelation} allowDeselect /></Labeled>
            {relation === 'Otro' && <TextInput label="¿Cuál?" value={relationOther} onChange={(v) => setRelationOther(v.slice(0, 40))} placeholder="Tía, sobrino, amiga…" />}
            <Labeled label="Sexo"><Chips options={SexOptions.all} value={sex} onChange={setSex} allowDeselect /></Labeled>
            <OptionalDateInput label="Fecha de nacimiento" value={birth} max={t} onChange={(d) => { if (d === null || d <= t) setBirth(d) }} />
            {birth !== null && <Hint>Edad: {ageText(birth, t)}</Hint>}
            <NumberInput label="Estatura (opcional)" value={height} onChange={setHeight} suffix="cm" error={heightErr && 'Escribe la estatura en centímetros (p. ej. 128)'} />
          </Card>
          <Card title="Módulos">
            <div className="row" style={{ alignItems: 'flex-start' }}>
              <Droplet size={24} color="var(--period)" style={{ marginTop: 2, flex: '0 0 auto' }} />
              <div className="grow">
                <Toggle
                  label="Seguimiento del ciclo menstrual"
                  sub="Calendario del periodo y predicciones. Se puede activar o apagar después en Ajustes."
                  checked={cycleOn}
                  onChange={(v) => { setCycle(v); setCycleTouched(true) }}
                />
              </div>
            </div>
          </Card>
        </>
      )}

      <Card title="Color del perfil">
        <AvatarColorPicker value={avatarColor} onChange={setColor} />
        <Hint>Sirve para distinguir de un vistazo de quién es lo que estás viendo.</Hint>
      </Card>

      <Button icon={Check} full disabled={saving} onClick={() => void save()}>{pet ? 'Crear perfil de mascota' : 'Crear perfil'}</Button>
      {triedSave && !valid && <div className="pf-error">{nameErr ? 'Falta el nombre.' : 'Revisa los datos marcados en rojo.'}</div>}
      <Hint>Podrás completar su ficha después (alergias, padecimientos, {pet ? 'veterinaria' : 'contacto de emergencia'}…).</Hint>
    </Page>
  )
}
