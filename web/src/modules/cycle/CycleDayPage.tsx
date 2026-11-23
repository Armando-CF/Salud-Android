// Editor del registro de un día del ciclo (port de CycleDayScreen.kt). Ruta /ciclo/dia/:day (epochDay).
import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { Check } from 'lucide-react'
import { CycleCatalog } from '../../data/catalogs'
import { useActive, useDb } from '../../data/profiles'
import type { CycleDay } from '../../data/types'
import { Fmt, toNumber, today } from '../../lib/dates'
import { Button, Card, Chips, ConfirmDialog, IconButton, InfoNote, MultiChips, NumberInput, Page, Pill, TextArea, Toggle, useBack } from '../../ui'
import { PeopleOnly, useCycleState } from './common'
import { PHASE_LABEL } from './engine'
import { isEmpty } from './logic'

export function CycleDayPage() {
  const { isPet } = useActive()
  return isPet ? <PeopleOnly title="Ciclo menstrual" /> : <DayEditor />
}

function DayEditor() {
  const params = useParams()
  const parsed = Number(params.day)
  const epochDay = Number.isFinite(parsed) && params.day !== undefined && params.day !== '' ? Math.trunc(parsed) : today()
  const db = useDb()
  const back = useBack()
  const state = useCycleState()
  const isFuture = epochDay > today()

  const [loaded, setLoaded] = useState(false)
  const [existed, setExisted] = useState(false)
  const [flow, setFlowValue] = useState(0)
  const [symptoms, setSymptoms] = useState('')
  const [moods, setMoods] = useState('')
  const [mucus, setMucus] = useState('')
  const [temp, setTemp] = useState('')
  const [sex, setSex] = useState(0)
  const [ovTest, setOvTest] = useState(0)
  const [pregTest, setPregTest] = useState(0)
  const [contraceptive, setContraceptive] = useState(false)
  const [notes, setNotes] = useState('')
  const [confirmDelete, setConfirmDelete] = useState(false)

  // Se carga una sola vez al abrir: lo que se edite después no se pisa.
  useEffect(() => {
    let alive = true
    void db.cycleDays.get(epochDay).then((d) => {
      if (!alive) return
      if (d) {
        setExisted(true)
        setFlowValue(d.flow); setSymptoms(d.symptoms); setMoods(d.moods); setMucus(d.mucus)
        setTemp(d.basalTemp !== null ? Fmt.num(d.basalTemp, 2) : '')
        setSex(d.sex); setOvTest(d.ovulationTest); setPregTest(d.pregnancyTest); setContraceptive(d.contraceptive); setNotes(d.notes)
      }
      setLoaded(true)
    })
    return () => { alive = false }
  }, [db, epochDay])

  const tempValue = toNumber(temp)
  const tempError = temp.trim() !== '' && (tempValue === null || tempValue < 35 || tempValue > 39)

  const save = async () => {
    if (tempError || !loaded) return
    const record: CycleDay = {
      epochDay,
      flow: isFuture ? 0 : flow,
      symptoms, moods, mucus,
      basalTemp: temp.trim() === '' ? null : tempValue,
      sex, ovulationTest: ovTest, pregnancyTest: pregTest, contraceptive,
      notes: notes.trim(),
    }
    if (isEmpty(record)) await db.cycleDays.delete(epochDay)
    else await db.cycleDays.put(record)
    back()
  }

  const rel = Fmt.relativeDay(epochDay)
  const title = rel === Fmt.date(epochDay) ? 'Registro del día' : `Registro de ${rel.toLowerCase()}`
  const symptomSet = new Set(CycleCatalog.split(symptoms))
  const moodSet = new Set(CycleCatalog.split(moods))
  const toggle = (set: Set<string>, k: string): string => {
    const n = new Set(set)
    if (n.has(k)) n.delete(k); else n.add(k)
    return CycleCatalog.join(n)
  }

  // Contexto del día dentro del ciclo
  let context = null
  if (state.loaded && state.analysis.hasData) {
    const info = state.analysis.info(epochDay)
    const anchorToday = epochDay === state.today
    const cycleDay = anchorToday ? state.status.cycleDay : info.cycleDay
    const phase = anchorToday ? state.status.phaseLabel : PHASE_LABEL[info.phase]
    if (cycleDay !== null) {
      context = (
        <div className="row wrap" style={{ gap: 8 }}>
          <Pill text={`Día ${cycleDay} del ciclo`} color="var(--primary)" />
          <Pill text={phase + (epochDay > state.today || info.predictedPeriod ? ' (estimado)' : '')} color={info.inPeriod || info.predictedPeriod ? 'var(--period)' : 'var(--muted)'} />
        </div>
      )
    }
  }

  return (
    <Page title={title} subtitle={Fmt.dateLong(epochDay)} actions={<IconButton icon={Check} label="Guardar" onClick={() => void save()} />}>
      {loaded && (
        <>
          {context}
          <Card title="Sangrado">
            {isFuture ? (
              <p className="muted">No se puede registrar sangrado en fechas futuras. Puedes dejar notas para ese día.</p>
            ) : (
              <>
                <Chips options={CycleCatalog.flows.filter((f) => f[0] > 0)} value={flow > 0 ? flow : null} onChange={(v) => setFlowValue(v ?? 0)} allowDeselect />
                <p className="small muted">{flow === 0 ? 'Sin sangrado. Elige una opción si hubo.' : 'Toca de nuevo la opción elegida para quitarla.'}</p>
              </>
            )}
          </Card>

          <Card title="Síntomas">
            <MultiChips options={CycleCatalog.symptoms} values={symptomSet} onToggle={(k) => setSymptoms(toggle(symptomSet, k))} />
          </Card>

          <Card title="Ánimo">
            <MultiChips options={CycleCatalog.moods} values={moodSet} onToggle={(k) => setMoods(toggle(moodSet, k))} />
          </Card>

          <Card title="Señales de fertilidad">
            <div className="cy-sub">Flujo cervical</div>
            <Chips options={CycleCatalog.mucus} value={mucus || null} onChange={(v) => setMucus(v ?? '')} allowDeselect />
            <NumberInput
              label="Temperatura basal"
              value={temp}
              onChange={setTemp}
              suffix="°C"
              error={tempError && 'Escribe un valor entre 35 y 39 °C'}
              hint="Tómala al despertar, antes de levantarte"
            />
          </Card>

          <Card title="Vida sexual y anticoncepción">
            <div className="cy-sub">Relaciones sexuales</div>
            <Chips options={CycleCatalog.sex} value={sex > 0 ? sex : null} onChange={(v) => setSex(v ?? 0)} allowDeselect />
            <Toggle label="Anticonceptivo" sub="Lo tomé o apliqué hoy (pastilla, parche, anillo, inyección…)" checked={contraceptive} onChange={setContraceptive} />
          </Card>

          {/* Las pruebas tienen su propia tarjeta para que sean fáciles de encontrar. */}
          <Card title="Pruebas">
            <p className="small muted">Si hoy te hiciste una prueba, anota aquí el resultado. Queda guardada en tu historial.</p>
            <div className="cy-sub">Prueba de embarazo</div>
            <div data-testid="cy-preg-test">
              <Chips options={CycleCatalog.tests} value={pregTest > 0 ? pregTest : null} onChange={(v) => setPregTest(v ?? 0)} allowDeselect />
            </div>
            {pregTest === 2 && (
              <InfoNote>
                Una prueba casera positiva conviene confirmarla con una prueba de laboratorio o con una persona profesional de la
                salud, quien además puede orientarte sobre los siguientes pasos.
              </InfoNote>
            )}
            <div className="cy-sub">Prueba de ovulación</div>
            <div data-testid="cy-ov-test">
              <Chips options={CycleCatalog.tests} value={ovTest > 0 ? ovTest : null} onChange={(v) => setOvTest(v ?? 0)} allowDeselect />
            </div>
          </Card>

          <Card title="Notas">
            <TextArea label="Notas del día" value={notes} onChange={setNotes} rows={3} />
          </Card>

          <Button full disabled={tempError} onClick={() => void save()}>Guardar</Button>
          {existed && <Button variant="text" full style={{ color: 'var(--error)' }} onClick={() => setConfirmDelete(true)}>Borrar registro del día</Button>}
        </>
      )}
      <ConfirmDialog
        open={confirmDelete}
        title="¿Borrar el registro?"
        text={`Se eliminará todo lo anotado el ${Fmt.date(epochDay)}, incluido el sangrado.`}
        confirmLabel="Borrar"
        onClose={() => setConfirmDelete(false)}
        onConfirm={async () => {
          setConfirmDelete(false)
          await db.cycleDays.delete(epochDay)
          back()
        }}
      />
    </Page>
  )
}
