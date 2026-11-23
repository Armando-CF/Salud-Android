// Ajustes del ciclo (port de CycleSettingsScreen.kt, sin recordatorios: la web no tiene notificaciones). Ruta /ciclo/ajustes.
import { Minus, Plus } from 'lucide-react'
import { usePref } from '../../data/prefs'
import { useActive } from '../../data/profiles'
import { Card, InfoNote, Page, Toggle } from '../../ui'
import { PeopleOnly, useCycleState } from './common'
import { dias } from './logic'

export function CycleSettingsPage() {
  const { isPet } = useActive()
  return isPet ? <PeopleOnly title="Ciclo menstrual" /> : <Settings />
}

function Settings() {
  const [enabled, setEnabled] = usePref('cycleEnabled')
  const [cycleLen, setCycleLen] = usePref('cycleLength')
  const [periodLen, setPeriodLen] = usePref('periodLength')
  const [luteal, setLuteal] = usePref('lutealLength')
  const [showFert, setShowFert] = usePref('showFertility')
  const state = useCycleState()
  return (
    <Page title="Ajustes del ciclo">
      <Card>
        <Toggle
          label="Seguimiento del ciclo menstrual"
          sub="Muestra el ciclo en la barra inferior y en la pantalla de inicio. Si lo desactivas, tus registros NO se borran: se conservan y vuelven a aparecer al activarlo."
          checked={enabled}
          onChange={(v) => void setEnabled(v)}
        />
      </Card>

      <Card title="Tu ciclo típico">
        <p className="small muted">
          Estos valores se usan para estimar mientras no haya historial suficiente. En cuanto registres ciclos completos, Savia usa
          el promedio de tus últimos 6.
        </p>
        <div>
          <Stepper title="Duración del ciclo" sub="Del primer día de un periodo al día anterior del siguiente" value={cycleLen} min={15} max={60} onChange={(v) => void setCycleLen(v)} />
          <div className="divider" />
          <Stepper title="Duración del periodo" sub="Días de sangrado" value={periodLen} min={1} max={14} onChange={(v) => void setPeriodLen(v)} />
          <div className="divider" />
          <Stepper title="Fase lútea" sub="Días entre la ovulación y el siguiente periodo (lo habitual es 14)" value={luteal} min={8} max={18} onChange={(v) => void setLuteal(v)} />
        </div>
        {state.loaded && state.analysis.avgCycle !== null && (
          <InfoNote>
            Según tu historial, ahora mismo Savia usa un ciclo de {dias(state.analysis.cycleLength)} y un periodo de{' '}
            {dias(state.analysis.periodLength)} para estimar.
          </InfoNote>
        )}
      </Card>

      <Card title="Predicciones">
        <Toggle label="Mostrar fertilidad" sub="Ventana fértil y ovulación estimadas en el calendario y el resumen." checked={showFert} onChange={(v) => void setShowFert(v)} />
      </Card>

      <div className="note cy-note-tertiary">
        Importante: las predicciones de periodo, ovulación y ventana fértil son estimaciones estadísticas. NO sirven como método
        anticonceptivo ni para confirmar o descartar un embarazo, y no sustituyen una consulta médica.
      </div>
      <InfoNote>Tus datos del ciclo se guardan solo en este dispositivo. Puedes exportarlos o respaldarlos desde Más → Exportar y respaldo.</InfoNote>
    </Page>
  )
}

function Stepper(props: { title: string; sub: string; value: number; min: number; max: number; onChange: (v: number) => void }) {
  const { value, min, max } = props
  return (
    <div className="cy-stepper" data-testid={`cy-stepper-${props.title}`}>
      <div className="grow">
        <div style={{ fontWeight: 600 }}>{props.title}</div>
        <div className="small muted">{props.sub}</div>
      </div>
      <button type="button" className="cy-step-btn" aria-label={`Menos: ${props.title}`} disabled={value <= min} onClick={() => props.onChange(Math.max(min, value - 1))}><Minus size={20} /></button>
      <div className="val" aria-live="polite"><b>{value}</b><span>{value === 1 ? 'día' : 'días'}</span></div>
      <button type="button" className="cy-step-btn" aria-label={`Más: ${props.title}`} disabled={value >= max} onClick={() => props.onChange(Math.min(max, value + 1))}><Plus size={20} /></button>
    </div>
  )
}
