import { useMemo, useState } from 'react'
import { useLiveQuery } from 'dexie-react-hooks'
import { ChevronDown, ChevronUp, Plus, ShieldCheck } from 'lucide-react'
import { SelfExamTypes } from '../../data/catalogs'
import { useActive, useDb, useProfileRow } from '../../data/profiles'
import type { SelfExam } from '../../data/types'
import { Fmt, today } from '../../lib/dates'
import { Button, Card, Chips, ConfirmDialog, DateInput, Fab, InfoNote, Page, Pill, SectionLabel, Sheet, TextArea, useToast } from '../../ui'
import { SelfExamGuides, type SelfExamGuide } from './guides'
import { PeopleOnlyPage } from './PeopleOnly'
import './wellbeing.css'

/** Autoexploración mamaria, testicular y de piel: estado por tipo, guías y registro. */
export function SelfExamPage() {
  const { isPet } = useActive()
  if (isPet) return <PeopleOnlyPage title="Autoexploración" />
  return <SelfExamContent />
}

function SelfExamContent() {
  const db = useDb()
  const toast = useToast()
  const profile = useProfileRow()
  const exams = useLiveQuery(() => db.selfExams.orderBy('epochDay').reverse().toArray(), [db])
  const types = useMemo(() => SelfExamGuides.orderedTypes(profile?.sex), [profile?.sex])
  const t = today()

  /** Registro en edición (sin id → nuevo). */
  const [editing, setEditing] = useState<SelfExam | null>(null)
  const [deleting, setDeleting] = useState<SelfExam | null>(null)
  const [openGuide, setOpenGuide] = useState<string | null>(null)

  const blank = (type: string): SelfExam => ({ epochDay: t, type, normal: true, notes: '' })
  const last = useMemo(() => SelfExamGuides.lastByType(exams ?? []), [exams])
  // Mismo orden que en Android: fecha descendente y, con la misma fecha, el más reciente primero.
  const list = useMemo(() => [...(exams ?? [])].sort((a, b) => b.epochDay - a.epochDay || (b.id ?? 0) - (a.id ?? 0)), [exams])

  const save = async (exam: SelfExam) => {
    setEditing(null)
    await db.selfExams.put({ ...exam, epochDay: Math.min(exam.epochDay, today()), notes: exam.notes.trim() })
    toast('Registro guardado')
  }
  const remove = async () => {
    const exam = deleting
    setDeleting(null)
    if (exam?.id !== undefined) await db.selfExams.delete(exam.id)
  }

  return (
    <Page title="Autoexploración" fab={<Fab icon={Plus} label="Registrar" onClick={() => setEditing(blank(types[0]))} />}>
      {exams !== undefined && (
        <>
          <InfoNote>
            Conocer tu cuerpo te ayuda a notar cambios a tiempo. Una revisión al mes es suficiente; aquí puedes anotar cuándo la hiciste y si notaste algo.
          </InfoNote>

          <Card title="Tus revisiones">
            {types.map((type, i) => (
              <div key={type} className="stack" style={{ gap: 10 }}>
                {i > 0 && <div className="divider" />}
                <StatusRow type={type} lastDay={last[type]} today={t} onRegister={() => setEditing(blank(type))} />
              </div>
            ))}
          </Card>

          <Card title="Cómo hacerla">
            <div>
              {types.map((type, i) => (
                <div key={type}>
                  {i > 0 && <div className="divider" />}
                  <GuideSection type={type} expanded={openGuide === type} onToggle={() => setOpenGuide(openGuide === type ? null : type)} />
                </div>
              ))}
            </div>
            <p className="small muted">Guía orientativa. No sustituye la revisión de un profesional de la salud.</p>
          </Card>

          <SectionLabel>Historial</SectionLabel>
          {list.length === 0 ? (
            <div className="wb-empty-history">
              <ShieldCheck size={40} color="var(--primary)" aria-hidden />
              <b>Aún no registras ninguna autoexploración</b>
              <span>Cuando hagas una, anótala con el botón «Registrar».</span>
            </div>
          ) : (
            list.map((exam) => <ExamRow key={exam.id} exam={exam} onClick={() => setEditing(exam)} />)
          )}
        </>
      )}

      <Sheet open={editing !== null} onClose={() => setEditing(null)} title={editing?.id === undefined ? 'Registrar autoexploración' : 'Editar registro'}>
        {editing && (
          <SelfExamForm
            key={editing.id ?? `nuevo-${editing.type}`}
            initial={editing}
            types={types}
            onSave={save}
            onDelete={editing.id !== undefined ? () => { const e = editing; setEditing(null); setDeleting(e) } : undefined}
            onCancel={() => setEditing(null)}
          />
        )}
      </Sheet>

      <ConfirmDialog
        open={deleting !== null}
        title="¿Eliminar este registro?"
        text={deleting ? `Se borrará la ${SelfExamGuides.title(deleting.type).toLowerCase()} del ${Fmt.date(deleting.epochDay)}.` : ''}
        onConfirm={remove}
        onClose={() => setDeleting(null)}
      />
    </Page>
  )
}

function StatusRow(props: { type: string; lastDay: number | undefined; today: number; onRegister: () => void }) {
  const { lastDay } = props
  const next = SelfExamGuides.nextSuggested(lastDay)
  const due = SelfExamGuides.isDue(lastDay, props.today)
  return (
    <div className="wb-status">
      <div className="wb-status-main">
        <div className="t">{SelfExamGuides.title(props.type)}</div>
        {lastDay === undefined ? (
          <div className="small muted">Sin registros todavía</div>
        ) : (
          <>
            <div className="small muted">Última: {Fmt.date(lastDay)} ({Fmt.daysFromNow(lastDay)})</div>
            {/* En la web no hay avisos: en su lugar se muestra la fecha sugerida de la próxima revisión. */}
            {next !== null && !due && <div className="small muted">Próxima sugerida: {Fmt.date(next)} ({Fmt.daysFromNow(next)})</div>}
            <div style={{ marginTop: 4 }}>{due ? <Pill text="Toca repetirla" color="var(--warn)" /> : <Pill text="Al día" color="var(--good)" />}</div>
          </>
        )}
      </div>
      <Button variant="tonal" small onClick={props.onRegister}>Registrar</Button>
    </div>
  )
}

function GuideSection(props: { type: string; expanded: boolean; onToggle: () => void }) {
  const guide = SelfExamGuides.of(props.type)
  if (!guide) return null
  return (
    <div>
      <button type="button" className="wb-guide-head" aria-expanded={props.expanded} onClick={props.onToggle}>
        <span>{SelfExamGuides.title(props.type)}</span>
        {props.expanded ? <ChevronUp size={22} color="var(--muted)" aria-label="Ocultar guía" /> : <ChevronDown size={22} color="var(--muted)" aria-label="Ver guía" />}
      </button>
      {props.expanded && <GuideContent guide={guide} />}
    </div>
  )
}

/** Contenido de una guía (exportado para poder mostrarlo en otras pantallas). */
export function GuideContent({ guide }: { guide: SelfExamGuide }) {
  let n = 0
  return (
    <div className="wb-guide">
      <h3>Cuándo</h3>
      <p>{guide.whenToDo}</p>
      <h3>Paso a paso</h3>
      {guide.steps.map((step) => {
        const lettered = SelfExamGuides.isLettered(step)
        return (
          <div key={step} className="wb-step">
            <span className="n">{lettered ? '' : `${++n}.`}</span>
            <span>{step}</span>
          </div>
        )
      })}
      <h3>Consulta si notas</h3>
      <p>{guide.consultIf}</p>
      <p className="small muted">{guide.note}</p>
    </div>
  )
}

function ExamRow({ exam, onClick }: { exam: SelfExam; onClick: () => void }) {
  return (
    <Card onClick={onClick}>
      <div className="wb-exam">
        <div className="wb-exam-main">
          <div className="t">{SelfExamGuides.title(exam.type)}</div>
          <div className="small muted">{Fmt.date(exam.epochDay)} · {Fmt.daysFromNow(exam.epochDay)}</div>
        </div>
        {exam.normal ? <Pill text="Sin cambios" color="var(--good)" /> : <Pill text="Noté algo" color="var(--warn)" />}
      </div>
      {exam.notes.trim() !== '' && <div className="wb-exam-notes">{exam.notes}</div>}
    </Card>
  )
}

/** Formulario de una autoexploración (dentro de una hoja inferior). */
function SelfExamForm(props: { initial: SelfExam; types: string[]; onSave: (e: SelfExam) => void; onDelete?: () => void; onCancel: () => void }) {
  const [exam, setExam] = useState(props.initial)
  const t = today()
  return (
    <>
      <div className="field">
        <span className="field-label">Tipo</span>
        <Chips options={props.types.map((k) => [k, SelfExamTypes.label(k)] as const)} value={exam.type} onChange={(v) => v && setExam({ ...exam, type: v })} />
      </div>
      <DateInput label="Fecha" value={exam.epochDay} max={t} onChange={(d) => setExam({ ...exam, epochDay: d })} />
      {exam.epochDay > t && <div className="wb-error">La fecha no puede ser futura; se guardará con la fecha de hoy.</div>}
      <div className="field">
        <span className="field-label">¿Qué encontraste?</span>
        <Chips options={[[1, 'Sin cambios'], [0, 'Noté algo']] as const} value={exam.normal ? 1 : 0} onChange={(v) => v !== null && setExam({ ...exam, normal: v === 1 })} />
      </div>
      {!exam.normal && (
        <InfoNote tone="warn">
          Anota qué notaste y dónde. Lo más probable es que no sea grave, pero conviene que lo valore pronto un profesional de la salud.
        </InfoNote>
      )}
      <TextArea label="Notas (opcional)" value={exam.notes} onChange={(v) => setExam({ ...exam, notes: v.slice(0, 600) })} rows={2} />
      <Button full onClick={() => props.onSave(exam)}>Guardar</Button>
      <div className="row">
        {props.onDelete && <Button variant="danger" onClick={props.onDelete} style={{ flex: '1 1 0' }}>Eliminar</Button>}
        <Button variant="outline" onClick={props.onCancel} style={{ flex: '1 1 0' }}>Cancelar</Button>
      </div>
    </>
  )
}
