import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { HeartPulse, Save, Share } from 'lucide-react'
import { PetSpecies, SexOptions } from '../../data/catalogs'
import { toInfo, useActive, useProfileRow } from '../../data/profiles'
import { emptyProfile, type Profile } from '../../data/types'
import { Fmt, toNumber, today } from '../../lib/dates'
import {
  Button, Card, Chips, Dialog, NumberInput, OptionalDateInput, Page, PawIcon, TextArea, TextInput, useBack, useToast,
} from '../../ui'
import { AvatarColorPicker, CallButton, Hint, Labeled, PhoneField, ProfileAvatar } from './common'
import {
  BLOOD_TYPES, STERILIZED_OPTIONS, ageText, bloodLabel, bmi, goalError as goalInvalid, heightError as heightInvalid, profileTitle,
  sterilizedKey, sterilizedValue,
} from './logic'

/** Valores del formulario (todo como texto o valores simples). */
interface Form {
  name: string; sex: string; birth: number | null; height: string; goal: string; blood: string; allergies: string; conditions: string
  emergencyName: string; emergencyPhone: string; doctorName: string; doctorPhone: string; insurance: string; notes: string
  relation: string; avatarColor: number; species: string; breed: string; sterilized: string; microchip: string; markings: string
}

const toForm = (p: Profile): Form => ({
  name: p.name, sex: p.sex, birth: p.birthEpochDay, height: p.heightCm === null ? '' : Fmt.num(p.heightCm, 2),
  goal: p.goalWeightKg === null ? '' : Fmt.num(p.goalWeightKg, 2), blood: p.bloodType, allergies: p.allergies, conditions: p.conditions,
  emergencyName: p.emergencyName, emergencyPhone: p.emergencyPhone, doctorName: p.doctorName, doctorPhone: p.doctorPhone,
  insurance: p.insurance, notes: p.notes, relation: p.relation, avatarColor: p.avatarColor, species: p.species, breed: p.breed,
  sterilized: sterilizedKey(p.sterilized), microchip: p.microchip, markings: p.markings,
})

function fromForm(base: Profile, f: Form, pet: boolean): Profile {
  return {
    ...base,
    name: f.name.trim(), sex: f.sex, birthEpochDay: f.birth,
    heightCm: f.height.trim() === '' ? null : toNumber(f.height),
    goalWeightKg: f.goal.trim() === '' ? null : toNumber(f.goal),
    bloodType: f.blood, allergies: f.allergies.trim(), conditions: f.conditions.trim(),
    emergencyName: f.emergencyName.trim(), emergencyPhone: f.emergencyPhone.trim(), doctorName: f.doctorName.trim(),
    doctorPhone: f.doctorPhone.trim(), insurance: f.insurance.trim(), notes: f.notes.trim(), relation: f.relation.trim(),
    avatarColor: f.avatarColor, species: pet ? f.species : base.species, breed: f.breed.trim(),
    sterilized: pet ? sterilizedValue(f.sterilized) : base.sterilized, microchip: f.microchip.trim(), markings: f.markings.trim(),
  }
}

/** Ficha del perfil activo (persona o mascota). */
export function ProfilePage() {
  const nav = useNavigate()
  const back = useBack()
  const toast = useToast()
  const { info, db, isPet: pet } = useActive()
  const stored = useProfileRow()
  const [form, setForm] = useState<Form | null>(null)
  const [baseline, setBaseline] = useState<Profile | null>(null)
  const [askLeave, setAskLeave] = useState(false)
  const t = today()

  // Carga la ficha una sola vez (después manda lo que la persona va escribiendo).
  useEffect(() => {
    if (form === null && stored !== undefined) {
      const p = stored ?? emptyProfile()
      setBaseline(p)
      setForm(toForm(p))
    }
  }, [stored, form])

  const current = useMemo(() => (form && baseline ? fromForm(baseline, form, pet) : null), [form, baseline, pet])

  if (!form || !baseline || !current) {
    return <Page title={profileTitle(pet, info.id, info.name)}><p className="muted center">Cargando…</p></Page>
  }

  const set = <K extends keyof Form>(key: K, value: Form[K]) => setForm((f) => (f ? { ...f, [key]: value } : f))
  const heightErr = !pet && heightInvalid(form.height)
  const goalErr = goalInvalid(form.goal, pet)
  const valid = !heightErr && !goalErr
  const dirty = JSON.stringify(current) !== JSON.stringify(baseline)
  const goalValue = toNumber(form.goal), heightValue = toNumber(form.height)

  const save = async (then?: () => void) => {
    if (!valid) return
    await db.profile.put(current)
    setBaseline(current)
    toast('Ficha guardada')
    then?.()
  }

  return (
    <Page
      title={profileTitle(pet, info.id, form.name)}
      back={() => (dirty ? setAskLeave(true) : back())}
      actions={<button className="text-btn" disabled={!dirty || !valid} onClick={() => void save()}>Guardar</button>}
    >
      {pet ? (
        <>
          <PetCard p={current} />

          <Card title="Datos de la mascota">
            <TextInput label="Nombre" value={form.name} onChange={(v) => set('name', v.slice(0, 60))} autoCapitalize="words" />
            <Labeled label="Especie"><Chips options={PetSpecies.all} value={form.species || null} onChange={(v) => v && set('species', v)} /></Labeled>
            <TextInput label="Raza" value={form.breed} onChange={(v) => set('breed', v.slice(0, 60))} autoCapitalize="words" placeholder="Mestizo, labrador, siamés…" />
            <Labeled label="Sexo"><Chips options={PetSpecies.sexes} value={form.sex} onChange={(v) => set('sex', v ?? 'X')} /></Labeled>
            <OptionalDateInput label="Fecha de nacimiento (o aproximada)" value={form.birth} max={t} onChange={(d) => { if (d === null || d <= t) set('birth', d) }} />
            {form.birth !== null && <Hint>Edad: {ageText(form.birth, t, true)}</Hint>}
            <Labeled label="¿Está esterilizado/a?"><Chips options={STERILIZED_OPTIONS} value={form.sterilized} onChange={(v) => set('sterilized', v ?? 'ns')} /></Labeled>
            <TextInput label="Color y señas particulares" value={form.markings} onChange={(v) => set('markings', v.slice(0, 200))} placeholder="Café con manchas blancas, oreja doblada…" />
            <TextInput label="Microchip o placa" value={form.microchip} onChange={(v) => set('microchip', v.slice(0, 40))} autoCapitalize="none" />
          </Card>

          <Card title="Salud">
            <TextArea label="Alergias" value={form.allergies} onChange={(v) => set('allergies', v)} rows={2} placeholder="Alimentos, medicamentos, picaduras…" />
            <TextArea label="Padecimientos" value={form.conditions} onChange={(v) => set('conditions', v)} rows={2} placeholder="Enfermedades crónicas, cirugías, cuidados especiales…" />
          </Card>

          <Card title="Veterinaria o veterinario">
            <TextInput label="Clínica o nombre de quien le atiende" value={form.doctorName} onChange={(v) => set('doctorName', v.slice(0, 80))} autoCapitalize="words" />
            <PhoneField label="Teléfono" value={form.doctorPhone} onChange={(v) => set('doctorPhone', v)} callLabel="Llamar a la veterinaria" />
          </Card>

          <Card title="Meta de peso">
            <NumberInput label="Peso ideal" value={form.goal} onChange={(v) => set('goal', v)} suffix="kg" error={goalErr && 'Escribe un peso en kilogramos'} />
            <Hint>Opcional. Pregunta en su veterinaria cuál es su peso ideal; se muestra como línea de meta en la gráfica de peso.</Hint>
          </Card>

          <Card title="Notas">
            <TextArea label="Alimento, cuidados, comportamiento…" value={form.notes} onChange={(v) => set('notes', v)} rows={3} />
          </Card>
        </>
      ) : (
        <>
          <EmergencyCard p={current} />

          <Card title="Datos personales">
            <TextInput label="Nombre" value={form.name} onChange={(v) => set('name', v.slice(0, 60))} autoCapitalize="words" />
            <TextInput label="Parentesco" value={form.relation} onChange={(v) => set('relation', v.slice(0, 40))} placeholder={info.id === 1 ? 'Yo' : 'Hija, hijo, pareja, papá…'} />
            {info.id === 1 && form.relation.trim() === '' && (
              <div className="row">
                <button type="button" className="chip" onClick={() => set('relation', 'Yo')}>Yo</button>
                <Hint>Este es el perfil principal.</Hint>
              </div>
            )}
            <Labeled label="Sexo"><Chips options={SexOptions.all} value={form.sex} onChange={(v) => set('sex', v ?? 'X')} /></Labeled>
            <OptionalDateInput label="Fecha de nacimiento" value={form.birth} max={t} onChange={(d) => { if (d === null || d <= t) set('birth', d) }} />
            {form.birth !== null && <Hint>Edad: {ageText(form.birth, t)}</Hint>}
            <NumberInput label="Estatura" value={form.height} onChange={(v) => set('height', v)} suffix="cm" error={heightErr && 'Escribe la estatura en centímetros (p. ej. 168)'} />
          </Card>

          <Card title="Datos médicos">
            <Labeled label="Tipo de sangre"><Chips options={BLOOD_TYPES} value={form.blood || null} onChange={(v) => set('blood', v ?? '')} allowDeselect /></Labeled>
            <TextArea label="Alergias" value={form.allergies} onChange={(v) => set('allergies', v)} rows={2} placeholder="Medicamentos, alimentos, látex…" />
            <TextArea label="Padecimientos" value={form.conditions} onChange={(v) => set('conditions', v)} rows={2} placeholder="Enfermedades crónicas, cirugías, condiciones importantes…" />
          </Card>

          <Card title="Contacto de emergencia">
            <TextInput label="Nombre y parentesco" value={form.emergencyName} onChange={(v) => set('emergencyName', v.slice(0, 80))} autoCapitalize="words" />
            <PhoneField label="Teléfono" value={form.emergencyPhone} onChange={(v) => set('emergencyPhone', v)} callLabel="Llamar al contacto de emergencia" />
          </Card>

          <Card title="Médico de cabecera">
            <TextInput label="Nombre" value={form.doctorName} onChange={(v) => set('doctorName', v.slice(0, 80))} autoCapitalize="words" />
            <PhoneField label="Teléfono" value={form.doctorPhone} onChange={(v) => set('doctorPhone', v)} />
          </Card>

          <Card title="Seguro médico">
            <TextArea label="Aseguradora y número de póliza" value={form.insurance} onChange={(v) => set('insurance', v)} rows={2} placeholder="También puedes anotar el número de afiliación" />
          </Card>

          <Card title="Meta de peso">
            <NumberInput label="Peso que se quiere alcanzar" value={form.goal} onChange={(v) => set('goal', v)} suffix="kg" error={goalErr && 'Escribe un peso en kilogramos'} />
            <Hint>
              {!goalErr && !heightErr && goalValue !== null && heightValue !== null
                ? `Con esa meta el índice de masa corporal sería ${Fmt.num(bmi(goalValue, heightValue), 1)}. Se muestra como línea de meta en la gráfica de peso.`
                : 'Opcional. Se muestra como línea de meta en la gráfica de peso.'}
            </Hint>
          </Card>

          <Card title="Notas">
            <TextArea label="Otras cosas que conviene tener a la mano" value={form.notes} onChange={(v) => set('notes', v)} rows={3} />
          </Card>
        </>
      )}

      <Card title="Color del perfil">
        <AvatarColorPicker value={form.avatarColor} onChange={(i) => set('avatarColor', i)} />
        <Hint>Distingue este perfil de los demás (familia y mascotas).</Hint>
      </Card>

      <div className="row">
        <div className="grow">
          <Button variant="outline" icon={Share} full onClick={() => { if (dirty && valid) void save(() => nav('/exportar')); else nav('/exportar') }}>Exportar</Button>
        </div>
        <div className="grow">
          <Button icon={Save} full disabled={!dirty || !valid} onClick={() => void save()}>Guardar</Button>
        </div>
      </div>
      <Hint>La ficha se guarda solo en este dispositivo. Inclúyela en tus respaldos para no perderla.</Hint>

      <Dialog
        open={askLeave}
        onClose={() => setAskLeave(false)}
        title="Cambios sin guardar"
        actions={
          <>
            <Button variant="text" style={{ color: 'var(--error)' }} onClick={() => { setAskLeave(false); back() }}>Descartar</Button>
            {valid && <Button variant="text" onClick={() => setAskLeave(false)}>Cancelar</Button>}
            {valid
              ? <Button variant="text" onClick={() => { setAskLeave(false); void save(back) }}>Guardar</Button>
              : <Button variant="text" onClick={() => setAskLeave(false)}>Revisar</Button>}
          </>
        }
      >
        <p className="muted">{valid ? '¿Quieres guardar los cambios de la ficha antes de salir?' : 'Hay datos que revisar (marcados en rojo). ¿Salir sin guardar los cambios?'}</p>
      </Dialog>
    </Page>
  )
}

function Line(props: { label: string; value: string }) {
  return (
    <div className="line">
      <div className="l">{props.label}</div>
      <div className="v">{props.value}</div>
    </div>
  )
}

/** Resumen de lo esencial en una emergencia, con los valores actuales del formulario. */
function EmergencyCard({ p }: { p: Profile }) {
  const empty = !p.bloodType && !p.allergies && !p.conditions && !p.emergencyName && !p.emergencyPhone
  const details = [
    p.birthEpochDay !== null ? ageText(p.birthEpochDay) : '',
    p.sex === 'F' || p.sex === 'M' ? SexOptions.label(p.sex) : '',
    p.heightCm !== null ? `${Fmt.num(p.heightCm, 1)} cm` : '',
  ].filter(Boolean)
  return (
    <section className="pf-summary emergency" aria-label="Tarjeta de emergencia">
      <div className="kicker"><HeartPulse size={20} />Tarjeta de emergencia</div>
      <div className="row">
        <div className="grow">
          <h2>{p.name || 'Sin nombre'}</h2>
          {details.length > 0 && <div>{details.join(' · ')}</div>}
        </div>
        {p.bloodType && p.bloodType !== 'No sé' && <div className="pf-blood"><small>Sangre</small><b>{bloodLabel(p.bloodType)}</b></div>}
      </div>
      {empty ? (
        <div>Completa la ficha para tener a la mano lo esencial: tipo de sangre, alergias y a quién llamar.</div>
      ) : (
        <>
          <div className="sep" />
          <Line label="Alergias" value={p.allergies || 'Sin registrar'} />
          <Line label="Padecimientos" value={p.conditions || 'Sin registrar'} />
          {(p.emergencyName || p.emergencyPhone) && (
            <div className="row">
              <div className="grow"><Line label="En caso de emergencia avisar a" value={[p.emergencyName, p.emergencyPhone].filter(Boolean).join(' · ')} /></div>
              {p.emergencyPhone && <CallButton phone={p.emergencyPhone} label="Llamar al contacto de emergencia" />}
            </div>
          )}
        </>
      )}
    </section>
  )
}

/** Resumen de la mascota con lo que conviene tener a la mano (p. ej. en la veterinaria). */
function PetCard({ p }: { p: Profile }) {
  const details = [
    [PetSpecies.label(p.species), p.breed].filter(Boolean).join(' · '),
    p.sex === 'F' || p.sex === 'M' ? PetSpecies.sexes.find((s) => s[0] === p.sex)?.[1] ?? '' : '',
    p.birthEpochDay !== null ? ageText(p.birthEpochDay, today(), true) : '',
  ].filter(Boolean)
  const lines: [string, string][] = []
  if (p.sterilized !== null) lines.push(['Esterilizado/a', p.sterilized ? 'Sí' : 'No'])
  if (p.microchip) lines.push(['Microchip o placa', p.microchip])
  if (p.markings) lines.push(['Señas', p.markings])
  if (p.allergies) lines.push(['Alergias', p.allergies])
  if (p.conditions) lines.push(['Padecimientos', p.conditions])
  const hasVet = !!(p.doctorName || p.doctorPhone)
  return (
    <section className="pf-summary pet" aria-label="Ficha de mascota">
      <div className="kicker"><PawIcon size={20} />Ficha de mascota</div>
      <div className="row">
        <ProfileAvatar info={toInfo(0, p)} size={56} />
        <div className="grow">
          <h2>{p.name || 'Sin nombre'}</h2>
          {details.length > 0 && <div>{details.join(' · ')}</div>}
        </div>
      </div>
      {lines.length === 0 && !hasVet ? (
        <div>Completa su ficha para tener a la mano lo esencial: esterilización, microchip, alergias y el teléfono de su veterinaria.</div>
      ) : (
        <>
          <div className="sep" />
          {lines.map(([l, v]) => <Line key={l} label={l} value={v} />)}
          {hasVet && (
            <div className="row">
              <div className="grow"><Line label="Veterinaria" value={[p.doctorName, p.doctorPhone].filter(Boolean).join(' · ')} /></div>
              {p.doctorPhone && <CallButton phone={p.doctorPhone} label="Llamar a la veterinaria" />}
            </div>
          )}
        </>
      )}
    </section>
  )
}
