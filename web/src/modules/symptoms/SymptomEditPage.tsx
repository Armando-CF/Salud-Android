import { useEffect, useMemo, useState } from 'react'
import { useParams, useSearchParams } from 'react-router-dom'
import { useLiveQuery } from 'dexie-react-hooks'
import { ChevronDown, ChevronUp, PersonStanding, Plus, Trash2 } from 'lucide-react'
import { useDb } from '../../data/profiles'
import type { SymptomEntry } from '../../data/types'
import { Fmt, nowMillis, toNumber, today } from '../../lib/dates'
import {
  Button, Card, Chips, ConfirmDialog, DateTimeInput, Dialog, IconButton, InfoNote, LevelSlider, MultiChips, NumberInput, Page, Sheet,
  TextArea, TextInput, intensityColor, useBack,
} from '../../ui'
import { BodyAtlas } from '../atlas'
import { useSymptomKit } from './kit'
import { SymptomStats } from './stats'
import './symptoms.css'

const capFirst = (s: string): string => s.charAt(0).toLocaleUpperCase('es-MX') + s.slice(1)

/**
 * Alta / edición de un síntoma.
 * Rutas: /sintomas/nuevo (admite ?region=<id>&label=<nombre>&name=<texto> para prellenar) y /sintomas/:id.
 */
export function SymptomEditPage() {
  const db = useDb()
  const back = useBack()
  const kit = useSymptomKit()
  const params = useParams()
  const [search] = useSearchParams()
  const id = params.id !== undefined ? Number(params.id) : null
  const isNew = id === null

  const [loaded, setLoaded] = useState(isNew)
  const [missing, setMissing] = useState(false)
  const [nameText, setNameText] = useState(isNew ? search.get('name') ?? '' : '')
  const [intensity, setIntensity] = useState(5)
  const [timestamp, setTimestamp] = useState(() => nowMillis())
  const [regId, setRegId] = useState<string | null>(isNew ? search.get('region') || null : null)
  const [regLabel, setRegLabel] = useState<string | null>(isNew ? search.get('label') || search.get('region') || null : null)
  const [durText, setDurText] = useState('')
  const [durUnit, setDurUnit] = useState<'min' | 'h'>('min')
  const [tagsCsv, setTagsCsv] = useState('')
  const [notes, setNotes] = useState('')

  const [nameError, setNameError] = useState(false)
  const [showCatalog, setShowCatalog] = useState(false)
  const [showDelete, setShowDelete] = useState(false)
  const [showCustomTag, setShowCustomTag] = useState(false)
  const [customTag, setCustomTag] = useState('')
  const [picking, setPicking] = useState(false)
  const [saving, setSaving] = useState(false)

  // Carga del registro existente (una sola vez).
  useEffect(() => {
    if (id === null) return
    let alive = true
    void db.symptoms.get(id).then((e) => {
      if (!alive) return
      if (!e) { setMissing(true); setLoaded(true); return }
      setNameText(e.name)
      setIntensity(Math.min(10, Math.max(1, e.intensity)))
      setTimestamp(e.timestamp)
      setRegId(e.regionId)
      setRegLabel(e.regionLabel)
      const d = e.durationMin
      if (d !== null && d > 0) {
        if (d >= 60 && d % 30 === 0) { setDurText(Fmt.num(d / 60, 1)); setDurUnit('h') } else { setDurText(`${d}`); setDurUnit('min') }
      }
      setTagsCsv(e.tags)
      setNotes(e.notes)
      setLoaded(true)
    })
    return () => { alive = false }
  }, [db, id])

  const all = useLiveQuery(() => db.symptoms.toArray(), [db])
  const used = useMemo(() => SymptomStats.usedNames(all ?? []), [all])

  const isFuture = timestamp > nowMillis() + 5 * 60_000
  const durNum = toNumber(durText)
  const durationMin = durNum !== null && durNum > 0 ? Math.max(1, Math.round(durUnit === 'h' ? durNum * 60 : durNum)) : null

  const q = SymptomStats.norm(nameText)
  const suggestions = useMemo(() => {
    if (!q) return used.slice(0, 8)
    const seen = new Set<string>()
    const out: string[] = []
    for (const s of [...used, ...kit.all]) {
      const n = SymptomStats.norm(s)
      if (seen.has(n)) continue
      seen.add(n)
      if (n.includes(q) && n !== q) out.push(s)
      if (out.length >= 8) break
    }
    return out
  }, [q, used, kit])

  const pickName = (s: string | null) => {
    if (!s) return
    setNameText(s)
    setNameError(false)
    setShowCatalog(false)
  }

  const save = async () => {
    const n = capFirst(nameText.trim())
    if (!n) { setNameError(true); document.querySelector('.page-body')?.scrollTo({ top: 0, behavior: 'smooth' }); return }
    if (isFuture || saving) return
    setSaving(true)
    const entry: SymptomEntry = {
      ...(id !== null ? { id } : {}),
      timestamp, name: n, intensity, regionId: regId, regionLabel: regLabel, durationMin,
      tags: SymptomStats.splitTags(tagsCsv).join(','),
      notes: notes.trim(),
    }
    await db.symptoms.put(entry)
    back()
  }

  const remove = async () => {
    setShowDelete(false)
    if (id !== null) await db.symptoms.delete(id)
    back()
  }

  const selectedTags = SymptomStats.splitTags(tagsCsv)
  const tagOptions = [...kit.triggers, ...selectedTags.filter((t) => !kit.triggers.includes(t))]
  const toggleTag = (tag: string) => setTagsCsv((selectedTags.includes(tag) ? selectedTags.filter((t) => t !== tag) : [...selectedTags, tag]).join(','))
  const addCustomTag = () => {
    const tag = capFirst(customTag.trim())
    if (tag && !selectedTags.some((t) => SymptomStats.norm(t) === SymptomStats.norm(tag))) setTagsCsv([...selectedTags, tag].join(','))
    setCustomTag('')
    setShowCustomTag(false)
  }

  return (
    <Page
      title={isNew ? 'Registrar síntoma' : 'Editar síntoma'}
      actions={
        missing ? null : (
          <>
            {!isNew && <IconButton icon={Trash2} label="Eliminar" onClick={() => setShowDelete(true)} />}
            <button type="button" className="text-btn" onClick={save} disabled={!loaded || saving}>Guardar</button>
          </>
        )
      }
    >
      {missing ? (
        <p>Este registro ya no existe.</p>
      ) : !loaded ? null : (
        <>
          {/* ── Qué */}
          <Card title={kit.whatTitle}>
            {/* El nombre es el único dato obligatorio: la tecla "Listo" cierra el teclado y deja ver el resto. */}
            <TextInput
              label={kit.nameLabel}
              value={nameText}
              onChange={(v) => { setNameText(v.slice(0, 60)); setNameError(false) }}
              placeholder={kit.namePlaceholder}
              error={nameError && kit.nameError}
              maxLength={60}
              enterKeyHint="done"
            />
            {suggestions.length > 0 && (
              <>
                <div className="sym-group-label">{q ? 'Sugerencias' : 'Los que has registrado antes'}</div>
                <Chips options={suggestions.map((s) => [s, s] as const)} value={null} onChange={pickName} />
              </>
            )}
            <Button variant="text" icon={showCatalog ? ChevronUp : ChevronDown} onClick={() => setShowCatalog(!showCatalog)} style={{ alignSelf: 'flex-start' }}>
              {showCatalog ? 'Ocultar lista de síntomas comunes' : 'Ver lista de síntomas comunes'}
            </Button>
            {showCatalog && kit.groups.map(([group, names]) => (
              <div key={group} className="stack tight">
                <div className="sym-group-label">{group}</div>
                <Chips options={names.map((s) => [s, s] as const)} value={null} onChange={pickName} />
              </div>
            ))}
          </Card>

          {/* ── Intensidad */}
          <Card title="¿Qué tan fuerte?">
            <LevelSlider
              label="Intensidad"
              value={intensity}
              onChange={setIntensity}
              valueText={`${intensity} · ${kit.intensityLabel(intensity)}`}
              color={intensityColor(intensity)}
            />
            <div className="sym-scale-legend"><span>1 · apenas se nota</span><span>{kit.intensityHigh}</span></div>
            {intensity >= 9 && <InfoNote tone="danger">{kit.alarmNote}</InfoNote>}
          </Card>

          {/* ── Cuándo */}
          <Card title="¿Cuándo?">
            <DateTimeInput value={timestamp} onChange={setTimestamp} max={today()} />
            {isFuture && <div className="sym-error">La fecha y hora no pueden estar en el futuro.</div>}
            <div className="sym-duration">
              <NumberInput label="Duración (opcional)" value={durText} onChange={setDurText} />
              <Chips options={[['min', 'min'], ['h', 'horas']] as const} value={durUnit} onChange={(v) => setDurUnit(v ?? 'min')} />
            </div>
          </Card>

          {/* ── Dónde (solo si hay figura para este perfil: personas, y mascotas perro o gato) */}
          {(kit.canPickBodyRegion || regLabel !== null) && (
            <Card title={kit.whereTitle}>
              {regLabel !== null ? (
                <>
                  <div className="sym-region-row">
                    <button type="button" className="chip on" onClick={() => kit.canPickBodyRegion && setPicking(true)}>
                      <PersonStanding size={18} aria-hidden />{regLabel}
                    </button>
                    <Button variant="text" onClick={() => { setRegId(null); setRegLabel(null) }}>Quitar</Button>
                  </div>
                  {kit.canPickBodyRegion && <p className="small muted">Toca la zona para cambiarla en el modelo del cuerpo.</p>}
                </>
              ) : (
                <>
                  <Button variant="outline" icon={PersonStanding} full onClick={() => setPicking(true)}>Elegir en el cuerpo</Button>
                  <p className="small muted">{kit.whereHint}</p>
                </>
              )}
            </Card>
          )}

          {/* ── Desencadenantes */}
          <Card title="¿Qué pudo provocarlo?">
            <MultiChips options={tagOptions.map((t) => [t, t] as const)} values={selectedTags} onToggle={toggleTag} />
            <button type="button" className="chip" style={{ alignSelf: 'flex-start' }} onClick={() => setShowCustomTag(true)}><Plus size={18} aria-hidden />Otro…</button>
            <p className="small muted">Opcional. Marcar posibles causas ayuda a encontrar patrones en el resumen.</p>
          </Card>

          {/* ── Notas */}
          <Card title="Notas">
            <TextArea label="Detalles (opcional)" value={notes} onChange={(v) => setNotes(v.slice(0, 1000))} placeholder={kit.notesPlaceholder} rows={3} />
          </Card>

          <Button full onClick={save} disabled={isFuture || saving}>{isNew ? 'Guardar síntoma' : 'Guardar cambios'}</Button>
          {!isNew && <Button variant="danger" icon={Trash2} full onClick={() => setShowDelete(true)}>Eliminar registro</Button>}
        </>
      )}

      <ConfirmDialog
        open={showDelete && id !== null}
        title="¿Eliminar este registro?"
        text={`Se borrará "${nameText.trim()}" de ${kit.isPet ? 'su' : 'tu'} historial. Esta acción no se puede deshacer.`}
        onConfirm={remove}
        onClose={() => setShowDelete(false)}
      />

      <Dialog
        open={showCustomTag}
        onClose={() => setShowCustomTag(false)}
        title="Otro desencadenante"
        actions={
          <>
            <Button variant="text" onClick={() => setShowCustomTag(false)}>Cancelar</Button>
            <Button variant="text" onClick={addCustomTag}>Agregar</Button>
          </>
        }
      >
        {/* Único campo del diálogo: la tecla "Listo" lo agrega y cierra. */}
        <TextInput
          label="¿Qué pudo provocarlo?"
          value={customTag}
          onChange={(v) => setCustomTag(v.replace(/,/g, ' ').slice(0, 30))}
          placeholder={kit.customTriggerExample}
          enterKeyHint="done"
          onEnter={addCustomTag}
          autoFocus
        />
      </Dialog>

      {/* Elegir zona en el atlas: hoja a pantalla completa, sin salir del formulario (no se pierde lo capturado). */}
      <Sheet open={picking} onClose={() => setPicking(false)} title="Elige la zona" full>
        <BodyAtlas
          pickMode
          onPick={(regionId, label) => {
            setRegId(regionId || null)
            setRegLabel(label || regionId || null)
            setPicking(false)
          }}
        />
      </Sheet>
    </Page>
  )
}
