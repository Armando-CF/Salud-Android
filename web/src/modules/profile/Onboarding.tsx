import { useRef, useState } from 'react'
import {
  ArrowLeft, ArrowRight, Check, CloudOff, Droplet, FlaskConical, HeartPulse, Lock, NotebookPen, PersonStanding, Pill as PillIcon, Scale,
  Share, Siren, Smile, Syringe, Users, type LucideIcon,
} from 'lucide-react'
import { SexOptions } from '../../data/catalogs'
import { dbFor } from '../../data/db'
import { PREF_DEFAULTS, setPref } from '../../data/prefs'
import { emptyProfile } from '../../data/types'
import { Fmt, toNumber, today } from '../../lib/dates'
import { Button, Card, Chips, NumberInput, OptionalDateInput, TextInput, Toggle } from '../../ui'
import { Hint, Labeled } from './common'
import { heightError as heightInvalid } from './logic'
import './profile.css'

const STEPS = 4

function Feature(props: { icon: LucideIcon; title: string; text: string; color?: string }) {
  const I = props.icon
  return (
    <div className="pf-feature">
      <span className="ic" style={props.color ? { background: `color-mix(in srgb, ${props.color} 16%, transparent)`, color: props.color } : undefined}><I size={22} /></span>
      <span className="grow">
        <span className="t" style={{ display: 'block' }}>{props.title}</span>
        <span className="s" style={{ display: 'block' }}>{props.text}</span>
      </span>
    </div>
  )
}

/**
 * Bienvenida de primer uso (fuera del enrutador). Al terminar guarda la ficha y los ajustes del perfil 1
 * y llama a `onDone` (App marca `onboardingDone`).
 */
export function Onboarding(props: { onDone: () => void }) {
  const [step, setStep] = useState(0)
  const [name, setName] = useState('')
  const [sex, setSex] = useState<string | null>(null)
  const [birth, setBirth] = useState<number | null>(null)
  const [height, setHeight] = useState('')
  const [cycle, setCycle] = useState(false)
  const [cycleTouched, setCycleTouched] = useState(false)
  const [cycleLength, setCycleLength] = useState(String(PREF_DEFAULTS.cycleLength))
  const [periodLength, setPeriodLength] = useState(String(PREF_DEFAULTS.periodLength))
  const [saving, setSaving] = useState(false)
  const body = useRef<HTMLElement>(null)
  const t = today()

  const heightErr = heightInvalid(height)
  const cl = cycleLength === '' ? null : parseInt(cycleLength, 10)
  const pl = periodLength === '' ? null : parseInt(periodLength, 10)
  const cycleLengthErr = cycle && cl !== null && (cl < 15 || cl > 60)
  const periodLengthErr = cycle && pl !== null && (pl < 1 || pl > 14)
  const blocked = (step === 1 && heightErr) || (step === 2 && (cycleLengthErr || periodLengthErr))

  const go = (n: number) => { setStep(n); body.current?.scrollTo({ top: 0 }) }

  const finish = async () => {
    if (saving) return
    setSaving(true)
    try {
      const db = dbFor(1)
      await setPref(db, 'cycleEnabled', cycle)
      if (cycle) {
        if (cl !== null && cl >= 15 && cl <= 60) await setPref(db, 'cycleLength', cl)
        if (pl !== null && pl >= 1 && pl <= 14) await setPref(db, 'periodLength', pl)
      }
      if (sex === 'F' || sex === 'M') await setPref(db, 'atlasSex', sex)
      const existing = (await db.profile.get(1)) ?? emptyProfile()
      const h = heightErr ? null : toNumber(height)
      await db.profile.put({
        ...existing,
        name: name.trim() || existing.name,
        sex: sex ?? 'X',
        birthEpochDay: birth ?? existing.birthEpochDay,
        heightCm: h ?? existing.heightCm,
        // El primer perfil es el de quien usa la app; después se pueden agregar familia y mascotas.
        relation: existing.relation || 'Yo',
      })
    } finally {
      props.onDone()
    }
  }

  return (
    <div className="page no-tabs pf-onb">
      <header className="topbar pf-onb-top">
        <div className="pf-onb-dots" aria-label={`Paso ${step + 1} de ${STEPS}`}>
          {Array.from({ length: STEPS }, (_, i) => <i key={i} className={i === step ? 'now' : i < step ? 'done' : ''} />)}
        </div>
        {step < STEPS - 1 && <button className="text-btn" onClick={() => void finish()}>Omitir</button>}
      </header>

      <main className="page-body" ref={body}>
        {step === 0 && (
          <>
            <div className="pf-onb-hero">
              <div className="logo"><img src="./favicon.svg" width={64} height={64} alt="" style={{ borderRadius: 16 }} /></div>
              <h1>Te damos la bienvenida a Savia</h1>
              <p className="muted">Tu bitácora de salud: síntomas, peso, estudios, medicamentos y más, en un solo lugar.</p>
            </div>
            <Card>
              <Feature icon={Lock} title="Privada de verdad" text="Todo se guarda únicamente en tu dispositivo." />
              <Feature icon={CloudOff} title="Sin cuenta y sin servidores" text="No hay registro, anuncios ni rastreo. Funciona sin conexión." />
              <Feature icon={Share} title="Tus datos son tuyos" text="Expórtalos o respáldalos cuando quieras." />
            </Card>
          </>
        )}

        {step === 1 && (
          <>
            <div className="pf-onb-title">
              <h1>Cuéntanos un poco de ti</h1>
              <p className="muted">Todo es opcional y puedes cambiarlo después en «Mi ficha».</p>
            </div>
            <Card>
              <TextInput label="¿Cómo te llamas?" value={name} onChange={(v) => setName(v.slice(0, 60))} autoCapitalize="words" />
              <Labeled label="Sexo" hint="Solo se usa para elegir el modelo del atlas corporal y sugerirte módulos.">
                <Chips
                  options={SexOptions.all}
                  value={sex}
                  onChange={(v) => { setSex(v); if (!cycleTouched) setCycle(v === 'F') }}
                  allowDeselect
                />
              </Labeled>
              <OptionalDateInput label="Fecha de nacimiento" value={birth} max={t} onChange={(d) => { if (d === null || d <= t) setBirth(d) }} />
              {birth !== null && <Hint>Edad: {Fmt.age(birth)} años</Hint>}
              <NumberInput
                label="Estatura"
                value={height}
                onChange={setHeight}
                suffix="cm"
                error={heightErr && 'Escribe tu estatura en centímetros (p. ej. 168)'}
                hint="Sirve para calcular tu índice de masa corporal"
              />
            </Card>
          </>
        )}

        {step === 2 && (
          <>
            <div className="pf-onb-title">
              <h1>Elige lo que quieres llevar</h1>
              <p className="muted">Activa solo lo que te sirva. Puedes cambiarlo cuando quieras en Ajustes.</p>
            </div>
            <Card>
              <div className="row" style={{ alignItems: 'flex-start' }}>
                <Droplet size={26} color="var(--period)" style={{ marginTop: 2, flex: '0 0 auto' }} />
                <div className="grow">
                  <Toggle
                    label="Seguimiento del ciclo menstrual"
                    sub="Calendario del periodo, síntomas, predicciones y estadísticas."
                    checked={cycle}
                    onChange={(v) => { setCycle(v); setCycleTouched(true) }}
                  />
                </div>
              </div>
              {cycle && (
                <>
                  <div className="row" style={{ alignItems: 'flex-start' }}>
                    <div className="grow"><NumberInput label="Duración del ciclo" value={cycleLength} onChange={(v) => setCycleLength(v.slice(0, 2))} suffix="días" decimal={false} error={cycleLengthErr && 'Entre 15 y 60'} /></div>
                    <div className="grow"><NumberInput label="Duración del periodo" value={periodLength} onChange={(v) => setPeriodLength(v.slice(0, 2))} suffix="días" decimal={false} error={periodLengthErr && 'Entre 1 y 14'} /></div>
                  </div>
                  <Hint>Si no lo sabes, deja 28 y 5: Savia se va ajustando con tus registros.</Hint>
                </>
              )}
            </Card>
            <Card title="Siempre disponibles">
              <div className="pf-mods">
                <span><NotebookPen size={17} />Síntomas</span>
                <span><Scale size={17} />Peso y mediciones</span>
                <span><FlaskConical size={17} />Estudios y chequeos</span>
                <span><PillIcon size={17} />Medicamentos y citas</span>
                <span><PersonStanding size={17} />Atlas del cuerpo</span>
                <span><Smile size={17} />Bienestar diario</span>
                <span><Syringe size={17} />Vacunas</span>
              </div>
            </Card>
            <Card>
              <Feature
                icon={Users}
                title="Para toda tu familia y tus mascotas"
                text="Después podrás agregar perfiles para tus hijas, hijos, tu pareja o tus mascotas, cada uno con su propio historial: en Más → Perfiles o tocando tu nombre en Inicio."
              />
            </Card>
          </>
        )}

        {step === 3 && (
          <>
            <div className="pf-onb-title">
              <h1>Antes de empezar</h1>
              <p className="muted">Un par de cosas importantes sobre lo que Savia es y lo que no.</p>
            </div>
            <Card>
              <Feature icon={HeartPulse} title="Es una bitácora, no un servicio médico" text="Savia no da diagnósticos ni sustituye la atención de profesionales de la salud." />
              <Feature icon={NotebookPen} title="Lo que anotas le sirve a quien te atiende" text="Llevar fechas, síntomas y resultados ayuda a explicar mejor cómo te has sentido." />
              <Feature icon={Droplet} title="Las estimaciones son orientativas" text="Por ejemplo, las fechas del ciclo son cálculos aproximados y no un método anticonceptivo." />
              <Feature icon={Lock} title="Tus datos viven solo aquí" text="No hay nube: si borras los datos del navegador o cambias de teléfono sin respaldo, se pierden. Exporta un respaldo de vez en cuando desde Ajustes." />
            </Card>
            <div className="note danger">
              <Feature icon={Siren} color="var(--bad)" title="En una emergencia" text="Llama a los servicios de emergencia de tu localidad (911 en México) o acude a urgencias." />
            </div>
          </>
        )}
      </main>

      <footer className="pf-onb-foot">
        {step > 0 && <Button variant="text" icon={ArrowLeft} onClick={() => go(step - 1)}>Atrás</Button>}
        <span className="grow" />
        {step < STEPS - 1
          ? <button type="button" className="btn primary" disabled={blocked} onClick={() => go(step + 1)}>Siguiente<ArrowRight size={19} /></button>
          : <Button icon={Check} disabled={saving} onClick={() => void finish()}>Empezar</Button>}
      </footer>
    </div>
  )
}
