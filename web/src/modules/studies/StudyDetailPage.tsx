import { useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useLiveQuery } from 'dexie-react-hooks'
import { CalendarClock, ChartLine, MapPin, Pencil, Trash2, User } from 'lucide-react'
import { useDb } from '../../data/profiles'
import type { Study, StudyAttachment, StudyResult } from '../../data/types'
import { Fmt, today } from '../../lib/dates'
import { Button, Card, ConfirmDialog, IconButton, InfoNote, Page, Pill, useBack, useToast } from '../../ui'
import { flagLabel, outOfRange, paramPath, rangeText, resultFlag, valueText } from './catalog'
import { VACCINE, useStudyProfile } from './profile'
import { AttachmentTile, CategoryBadge, RangeBar, StatusPill, VaccinesMovedNote, flagColor, useAttachmentViewer, type AttView } from './shared'

/** Detalle de un estudio: datos generales, resultados con su rango, adjuntos y seguimiento. */
export function StudyDetailPage() {
  const params = useParams()
  const id = Number(params.id)
  const nav = useNavigate()
  const back = useBack()
  const db = useDb()
  const toast = useToast()
  const loaded = useLiveQuery(async () => ({ study: Number.isFinite(id) ? await db.studies.get(id) : undefined }), [db, id])
  const results = useLiveQuery(() => (Number.isFinite(id) ? db.studyResults.where('studyId').equals(id).sortBy('id') : Promise.resolve([] as StudyResult[])), [db, id]) ?? []
  const attachments = useLiveQuery(() => (Number.isFinite(id) ? db.studyAttachments.where('studyId').equals(id).sortBy('id') : Promise.resolve([] as StudyAttachment[])), [db, id]) ?? []
  const [confirmDelete, setConfirmDelete] = useState(false)
  const { open, viewer } = useAttachmentViewer()
  const study = loaded?.study
  const atts = useMemo<AttView[]>(() => attachments.map((a) => ({ key: `s${a.id}`, id: a.id, displayName: a.displayName, mime: a.mime, data: a.data })), [attachments])

  const remove = async () => {
    setConfirmDelete(false)
    if (!study) return
    await db.transaction('rw', db.studies, db.studyResults, db.studyAttachments, async () => {
      await db.studyResults.where('studyId').equals(id).delete()
      await db.studyAttachments.where('studyId').equals(id).delete()
      await db.studies.delete(id)
    })
    toast('Estudio eliminado')
    back()
  }

  return (
    <Page
      title="Estudio"
      actions={study ? (
        <>
          <IconButton icon={Pencil} label="Editar" onClick={() => nav(`/estudios/${id}/editar`)} />
          <IconButton icon={Trash2} label="Eliminar" onClick={() => setConfirmDelete(true)} />
        </>
      ) : undefined}
    >
      {!loaded ? null : !study ? (
        <p className="muted center" style={{ padding: 32 }}>Este estudio ya no existe.</p>
      ) : (
        <div className="st-page">
          <HeaderCard study={study} />
          {study.category === VACCINE && <VaccinesMovedNote />}
          {study.followUpEpochDay != null && <FollowUpCard day={study.followUpEpochDay} />}
          {study.summary.trim() !== '' && (
            <Card title="Resumen y conclusiones"><p style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{study.summary}</p></Card>
          )}
          {results.length > 0 && <ResultsCard results={results} />}
          {atts.length > 0 && (
            <Card title="Adjuntos">
              <div className="st-atts">
                {atts.map((a) => <AttachmentTile key={a.key} att={a} onOpen={() => open(a)} />)}
              </div>
            </Card>
          )}
          {results.length === 0 && atts.length === 0 && study.summary.trim() === '' && (
            <Card>
              <p className="muted">Este estudio aún no tiene resultados ni archivos. Puedes agregar los valores del reporte, una foto o el PDF.</p>
              <div><Button variant="outline" icon={Pencil} onClick={() => nav(`/estudios/${id}/editar`)}>Agregar resultados</Button></div>
            </Card>
          )}
        </div>
      )}
      {viewer}
      <ConfirmDialog
        open={confirmDelete && !!study}
        title="Eliminar estudio"
        text={`Se eliminará "${study?.title ?? ''}" con sus resultados y archivos adjuntos. Esta acción no se puede deshacer.`}
        onConfirm={() => { void remove() }}
        onClose={() => setConfirmDelete(false)}
      />
    </Page>
  )
}

function HeaderCard({ study }: { study: Study }) {
  const sp = useStudyProfile()
  return (
    <Card>
      <div className="st-card-row">
        <CategoryBadge category={study.category} />
        <div className="grow">
          <div className="st-h1">{study.title}</div>
          <div className="muted" style={{ fontSize: 15 }}>{Fmt.dateLong(study.epochDay)} · {Fmt.daysFromNow(study.epochDay)}</div>
        </div>
      </div>
      <div className="st-pills">
        <Pill text={sp.categoryShort(study.category)} color="var(--primary)" />
        <StatusPill status={study.status} />
      </div>
      {study.place.trim() !== '' && <div className="st-iconline"><MapPin size={18} /><span>{study.place}</span></div>}
      {study.doctor.trim() !== '' && <div className="st-iconline"><User size={18} /><span>{study.doctor}</span></div>}
    </Card>
  )
}

function FollowUpCard({ day }: { day: number }) {
  const overdue = day < today()
  return (
    <Card className="st-soft">
      <div className="row">
        <CalendarClock size={24} color={overdue ? 'var(--warn)' : 'var(--primary)'} style={{ flex: '0 0 auto' }} />
        <div className="grow">
          <div className="small muted" style={{ fontWeight: 600 }}>Seguimiento</div>
          <div style={{ fontWeight: 600, color: overdue ? 'var(--warn)' : undefined }}>
            {Fmt.date(day)} · {Fmt.daysFromNow(day)}{overdue ? ' (vencido)' : ''}
          </div>
        </div>
      </div>
    </Card>
  )
}

function ResultsCard({ results }: { results: StudyResult[] }) {
  const sp = useStudyProfile()
  const nav = useNavigate()
  const out = results.filter(outOfRange).length
  const withRange = results.filter((r) => resultFlag(r) !== 'SIN_RANGO').length
  return (
    <Card
      title="Resultados"
      action={withRange > 0 ? (
        out === 0 ? <Pill text="Todo en rango" color="var(--good)" /> : <Pill text={out === 1 ? '1 fuera de rango' : `${out} fuera de rango`} color="var(--warn)" />
      ) : undefined}
    >
      <div>
        {results.map((r, i) => (
          <div key={r.id}>
            {i > 0 && <div className="divider" />}
            <ResultRow r={r} onClick={r.value != null ? () => nav(paramPath(r.name)) : undefined} />
          </div>
        ))}
      </div>
      <InfoNote>
        {results.some((r) => r.value != null) ? 'Toca un resultado numérico para ver su evolución. ' : ''}
        Los rangos de referencia son orientativos; usa los de {sp.labName} y consulta cualquier duda con {sp.whoToAsk}.
      </InfoNote>
    </Card>
  )
}

function ResultRow({ r, onClick }: { r: StudyResult; onClick?: () => void }) {
  const f = resultFlag(r)
  const range = rangeText(r.refMin, r.refMax)
  const off = f === 'ALTO' || f === 'BAJO'
  const body = (
    <>
      <div className="top">
        <div className="grow">
          <div className="name">{r.name}</div>
          {range !== '' && <div className="small muted">Referencia: {range}{r.unit.trim() ? ` ${r.unit}` : ''}</div>}
        </div>
        <div className="val">
          <b style={{ color: off ? flagColor(f) : undefined, fontSize: r.value != null ? 17 : 16 }}>{valueText(r)}</b>
          {f !== 'SIN_RANGO' && <Pill text={flagLabel(f)} color={flagColor(f)} />}
        </div>
        {onClick && <ChartLine size={18} color="var(--muted)" aria-label="Ver evolución" style={{ flex: '0 0 auto' }} />}
      </div>
      {r.value != null && f !== 'SIN_RANGO' && <RangeBar value={r.value} refMin={r.refMin} refMax={r.refMax} />}
      {r.notes.trim() !== '' && <div className="small muted">{r.notes}</div>}
    </>
  )
  return onClick
    ? <button type="button" className="st-result" data-result={r.name} onClick={onClick}>{body}</button>
    : <div className="st-result" data-result={r.name}>{body}</div>
}
