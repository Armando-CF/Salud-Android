import { useEffect, useMemo, useState } from 'react'
import { useParams, useSearchParams } from 'react-router-dom'
import { useLiveQuery } from 'dexie-react-hooks'
import { Trash2 } from 'lucide-react'
import { VaccinationTypes, type Opt } from '../../data/catalogs'
import { displayName, useActive } from '../../data/profiles'
import type { Vaccination } from '../../data/types'
import { Fmt, today as todayDay } from '../../lib/dates'
import {
  Button, Card, Chips, ConfirmDialog, DateInput, IconButton, InfoNote, OptionalDateInput, Page, TextArea, TextInput, useBack, useToast,
} from '../../ui'
import { CalendarButton, CalendarNote, useAddToCalendar, useCalendarOwner } from '../meds/common'
import { vaccinationEvent } from '../meds/ics'
import { DOSES, VACUNA, DESP_EXTERNA, DESP_INTERNA, filterSuggestions, findSuggestion, inLabel, intervalLabel, normalizeName } from './catalog'
import { vaccinesDisclaimer } from './common'
import { dueText, nextDose, plusInterval, plusMonths } from './logic'

/** Cuántas sugerencias de nombre se muestran antes de "Ver todas". */
const SUGGESTIONS_SHOWN = 8

const namePlaceholder = (type: string, isPet: boolean): string =>
  type === VACUNA ? (isPet ? 'Ej. Rabia' : 'Ej. Influenza')
    : type === DESP_INTERNA ? 'Ej. Desparasitante en tableta'
      : type === DESP_EXTERNA ? 'Ej. Pipeta'
        : isPet ? 'Ej. Revisión preventiva' : 'Ej. Desparasitación'

const capFirst = (s: string): string => (s ? s.charAt(0).toLocaleUpperCase('es-MX') + s.slice(1) : s)
const lowerFirst = (s: string): string => (s ? s.charAt(0).toLocaleLowerCase('es-MX') + s.slice(1) : s)

/**
 * Alta / edición de una vacuna, desparasitación u otra aplicación preventiva.
 * Rutas: /vacunas/nueva (admite ?type=&name=&dose=&place= para prellenar) y /vacunas/:id.
 */
export function VaccineEditPage() {
  const params = useParams()
  // La clave reinicia el formulario al pasar de un registro a otro (o a uno nuevo) sin salir de la pantalla.
  return <VaccineEditor key={params.id ?? 'nuevo'} />
}

function VaccineEditor() {
  const params = useParams()
  const [search] = useSearchParams()
  const id = params.id ? Number(params.id) : null
  const isNew = id === null
  const { db, info, isPet } = useActive()
  const back = useBack()
  const toast = useToast()
  const t = todayDay()
  const { profileId, owner } = useCalendarOwner()
  const addToCalendar = useAddToCalendar()

  const [loaded, setLoaded] = useState(isNew)
  const [missing, setMissing] = useState(false)
  const [type, setType] = useState(() => (isNew && search.get('type')) || VACUNA)
  const [name, setName] = useState(() => (isNew && search.get('name')) || '')
  const [dose, setDose] = useState(() => (isNew && search.get('dose')) || '')
  const [day, setDay] = useState(t)
  const [nextDay, setNextDay] = useState<number | null>(null)
  const [place, setPlace] = useState(() => (isNew && search.get('place')) || '')
  const [lot, setLot] = useState('')
  const [notes, setNotes] = useState('')
  const [remind, setRemind] = useState(true)
  const [nameError, setNameError] = useState(false)
  const [showAll, setShowAll] = useState(false)
  const [showDelete, setShowDelete] = useState(false)

  useEffect(() => {
    if (id === null) return
    let alive = true
    void db.vaccinations.get(id).then((v) => {
      if (!alive) return
      if (!v) setMissing(true)
      else {
        setType(v.type); setName(v.name); setDose(v.dose); setDay(v.epochDay); setNextDay(v.nextDueEpochDay ?? null)
        setPlace(v.place); setLot(v.lot); setNotes(v.notes); setRemind(v.remind)
      }
      setLoaded(true)
    })
    return () => { alive = false }
  }, [db, id])

  const everything = useLiveQuery(() => db.vaccinations.toArray(), [db]) ?? []

  const typeOptions = useMemo<readonly Opt[]>(() => {
    const base = VaccinationTypes.forProfile(isPet)
    return base.some((o) => o[0] === type) ? base : [...base, [type, VaccinationTypes.label(type)] as const]
  }, [type, isPet])
  const match = useMemo(() => findSuggestion(info, name, type), [info, name, type])
  const futureDay = day > t
  const nextError = nextDay !== null && nextDay <= day

  // Sugerencias: primero lo ya usado en este perfil, luego el catálogo.
  const suggestions = useMemo(() => {
    const query = match ? '' : name
    const q = normalizeName(query)
    const used = everything.filter((v) => v.type === type).map((v) => v.name.trim()).filter((n) => n && (q === '' || normalizeName(n).includes(q)))
    const catalog = filterSuggestions(info, type, query).map((x) => x.name)
    const seen = new Set<string>()
    return [...used, ...catalog].filter((n) => { const k = normalizeName(n); if (seen.has(k)) return false; seen.add(k); return true })
  }, [type, name, everything, match, info])
  const shown = showAll ? suggestions : suggestions.slice(0, SUGGESTIONS_SHOWN)

  const current: Vaccination = {
    ...(id !== null ? { id } : {}),
    epochDay: day, type, name: capFirst(name.trim()), dose: dose.trim(), nextDueEpochDay: nextDay, place: place.trim(), lot: lot.trim(), notes: notes.trim(), remind,
  }

  const save = async () => {
    if (!name.trim()) { setNameError(true); return }
    if (futureDay || nextError) return
    if (id === null) await db.vaccinations.add(current)
    else await db.vaccinations.put(current)
    toast('Registro guardado')
    back()
  }

  const suggested = match?.intervalDays ?? null
  const quick: readonly (readonly [label: string, months: number])[] = [['+1 mes', 1], ['+3 meses', 3], ['+6 meses', 6], ['+1 año', 12]]

  return (
    <Page
      title={isNew ? 'Registrar aplicación' : 'Editar registro'}
      subtitle={isPet ? displayName(info) : undefined}
      actions={
        <>
          {!isNew && !missing && <IconButton icon={Trash2} label="Eliminar" onClick={() => setShowDelete(true)} />}
          {!missing && <button className="text-btn" disabled={!loaded} onClick={() => void save()}>Guardar</button>}
        </>
      }
    >
      {missing ? <p>Este registro ya no existe.</p> : !loaded ? null : (
        <>
          <Card title="¿Qué se aplicó?">
            {typeOptions.length > 1 && <Chips options={typeOptions} value={type} onChange={(k) => { if (k) { setType(k); setShowAll(false) } }} />}
            <TextInput
              label={type === VACUNA ? 'Vacuna' : 'Nombre o producto'}
              value={name}
              onChange={(v) => { setName(v); setNameError(false) }}
              placeholder={namePlaceholder(type, isPet)}
              error={nameError && 'Escribe o elige qué se aplicó'}
              maxLength={80}
            />
            {suggestions.length > 0 && (
              <>
                <div className="chips" role="group" aria-label="Sugerencias">
                  {shown.map((sug) => {
                    const selected = normalizeName(sug) === normalizeName(name)
                    return (
                      <button key={sug} type="button" className={`chip${selected ? ' on' : ''}`} aria-pressed={selected} onClick={() => { setName(selected ? '' : sug); setNameError(false) }}>
                        {sug}
                      </button>
                    )
                  })}
                </div>
                {suggestions.length > SUGGESTIONS_SHOWN && (
                  <div><Button variant="text" onClick={() => setShowAll(!showAll)}>{showAll ? 'Ver menos' : `Ver todas (${suggestions.length})`}</Button></div>
                )}
              </>
            )}
            {match && (match.hint || match.intervalDays !== null) && (
              <p className="small muted">
                {capFirst([match.hint, match.intervalDays !== null ? `suele repetirse ${intervalLabel(match.intervalDays)} (orientativo)` : ''].filter(Boolean).join(' · '))}
              </p>
            )}
          </Card>

          <Card title="Dosis">
            <div className="chips" role="group" aria-label="Dosis">
              {DOSES.map((d) => {
                const selected = dose.trim().toLowerCase() === d.toLowerCase()
                return <button key={d} type="button" className={`chip${selected ? ' on' : ''}`} aria-pressed={selected} onClick={() => setDose(selected ? '' : d)}>{d}</button>
              })}
            </div>
            <TextInput label="Dosis o esquema (opcional)" value={dose} onChange={setDose} placeholder="Ej. 2.ª dosis, refuerzo, 1 tableta" maxLength={40} />
          </Card>

          <Card title="Fechas">
            <DateInput label="Fecha de aplicación" value={day} onChange={setDay} max={t} />
            {futureDay && (
              <div className="md-error">La fecha de aplicación no puede ser futura. Si aún no se aplica, guárdala como próxima aplicación de un registro anterior.</div>
            )}
            <OptionalDateInput label="Próxima aplicación o refuerzo" value={nextDay} onChange={setNextDay} hint={nextDay === null ? 'Sin próxima fecha' : undefined} />
            {nextError && <div className="md-error">La próxima fecha debe ser posterior a la de aplicación.</div>}
            <div className="chips" role="group" aria-label="Atajos de próxima fecha">
              {suggested !== null && (() => {
                const target = plusInterval(day, suggested)
                return (
                  <button type="button" className={`chip${nextDay === target ? ' on' : ''}`} aria-pressed={nextDay === target} onClick={() => setNextDay(nextDay === target ? null : target)}>
                    Sugerido: {inLabel(suggested)}
                  </button>
                )
              })()}
              {quick.filter(([, months]) => !(suggested !== null && (suggested === months * 30 || (suggested === 365 && months === 12)))).map(([label, months]) => (
                <button key={label} type="button" className="chip" onClick={() => setNextDay(plusMonths(day, months))}>{label}</button>
              ))}
            </div>
            {nextDay !== null && !nextError && (
              <p className="small muted">Tocaría el {lowerFirst(Fmt.dateLong(nextDay))} ({dueText(nextDay, t).toLowerCase()}).</p>
            )}
            {nextDay !== null && !nextError && nextDay >= t && (
              <>
                <CalendarButton
                  full
                  variant="tonal"
                  label="Agregar la próxima al calendario"
                  onClick={() => {
                    if (!name.trim()) { setNameError(true); return }
                    const e = vaccinationEvent(current, profileId, owner, nextDose(current.dose))
                    void addToCalendar(`${type === VACUNA ? 'vacuna' : 'aplicacion'}-${current.name}`, e ? [e] : [])
                  }}
                />
                <p className="small muted">El calendario te avisará 7 días antes y el mismo día a las 9:00.</p>
                <CalendarNote />
              </>
            )}
          </Card>

          <Card title="Detalles">
            <TextInput
              label={isPet ? 'Veterinaria o clínica (opcional)' : 'Lugar o clínica (opcional)'}
              value={place}
              onChange={setPlace}
              placeholder={isPet ? 'Ej. Clínica veterinaria San Francisco' : 'Ej. Centro de salud, consultorio…'}
              maxLength={80}
              autoCapitalize="words"
              enterKeyHint="next"
            />
            <TextInput label="Lote (opcional)" value={lot} onChange={setLot} placeholder="Viene en la etiqueta o en la cartilla" maxLength={40} autoCapitalize="none" />
            <TextArea label="Notas (opcional)" value={notes} onChange={setNotes} rows={3} placeholder={isPet ? 'Reacciones, peso ese día, marca del producto…' : 'Reacciones, quién la aplicó, brazo…'} />
          </Card>

          <InfoNote>{vaccinesDisclaimer(isPet)}</InfoNote>

          <Button full disabled={futureDay || nextError} onClick={() => void save()}>{isNew ? 'Guardar registro' : 'Guardar cambios'}</Button>
          {!isNew && <Button full variant="danger" icon={Trash2} onClick={() => setShowDelete(true)}>Eliminar registro</Button>}
        </>
      )}

      <ConfirmDialog
        open={showDelete && id !== null}
        title="¿Eliminar este registro?"
        text={`Se borrará "${name.trim()}" del ${Fmt.date(day)}. Esta acción no se puede deshacer.`}
        onClose={() => setShowDelete(false)}
        onConfirm={() => {
          setShowDelete(false)
          if (id !== null) void db.vaccinations.delete(id).then(() => { toast('Registro eliminado'); back() })
        }}
      />
    </Page>
  )
}
