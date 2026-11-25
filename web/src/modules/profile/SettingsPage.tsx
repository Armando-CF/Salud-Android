import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  CloudOff, Database, Droplet, FileDown, HardDrive, KeyRound, Palette, PersonStanding, PlusSquare, Share, ShieldCheck, SlidersHorizontal,
  Smartphone, Trash2, UserRound, Users,
} from 'lucide-react'
import pkg from '../../../package.json'
import { usePref, useSetting } from '../../data/prefs'
import { displayName, useActive } from '../../data/profiles'
import { Fmt, millisToEpochDay } from '../../lib/dates'
import { isIos, isStandalone } from '../../lib/files'
import { Button, Card, ConfirmDialog, Dialog, InfoNote, NavRow, Page, Segmented, TextInput, Toggle, useToast } from '../../ui'
import { BackupButtons } from '../backup'
import { Hint, ProfileAvatar, SettingHeader } from './common'
import { PinSheet } from './Lock'
import { bytesText, profileLine, wipeProfileData } from './logic'
import { useProfileList } from './ProfilesPages'

const DELETE_WORD = 'BORRAR'
const THEMES = [['system', 'Sistema'], ['light', 'Claro'], ['dark', 'Oscuro']] as const
const ATLAS = [['F', 'Mujer'], ['M', 'Hombre']] as const

interface StorageInfo { usage: number | null; quota: number | null; persisted: boolean | null }

async function readStorage(): Promise<StorageInfo> {
  const out: StorageInfo = { usage: null, quota: null, persisted: null }
  try {
    const e = await navigator.storage?.estimate?.()
    if (e) { out.usage = e.usage ?? null; out.quota = e.quota ?? null }
    out.persisted = (await navigator.storage?.persisted?.()) ?? null
  } catch { /* no disponible en este navegador */ }
  return out
}

/** Ajustes / Configuración. */
export function SettingsPage() {
  const nav = useNavigate()
  const toast = useToast()
  const { info: active, db, isPet: pet } = useActive()
  const profiles = useProfileList()
  const [themeMode, setThemeMode] = useSetting('themeMode')
  const [pinHash] = useSetting('pinHash')
  const [lastBackup] = useSetting('lastBackup')
  const [cycleEnabled, setCycleEnabled] = usePref('cycleEnabled')
  const [atlasSex, setAtlasSex] = usePref('atlasSex')
  const [pinMode, setPinMode] = useState<'set' | 'change' | 'remove' | null>(null)
  const [wipeAsk, setWipeAsk] = useState(false)
  const [wipeConfirm, setWipeConfirm] = useState(false)
  const [typed, setTyped] = useState('')
  const [storage, setStorage] = useState<StorageInfo | null>(null)

  useEffect(() => { void readStorage().then(setStorage) }, [])

  const name = displayName(active)
  const sub = profileLine(active)
  const hasPin = !!pinHash
  const ios = isIos()
  const installed = isStandalone()
  const wipeOk = typed.trim().toUpperCase() === DELETE_WORD

  const askPersist = async () => {
    try {
      const ok = await navigator.storage?.persist?.()
      setStorage(await readStorage())
      toast(ok ? 'Listo: el navegador conservará tus datos.' : 'El navegador no lo concedió por ahora. Instalar la app en la pantalla de inicio ayuda.')
    } catch {
      toast('Este navegador no permite pedirlo.')
    }
  }

  return (
    <Page title="Ajustes" subtitle="Configuración">
      <Card title="Perfil">
        <div className="row">
          <ProfileAvatar info={active} size={44} />
          <div className="grow">
            <div style={{ fontWeight: 600 }}>Viendo a {name}</div>
            <div className="small muted">{sub || (pet ? 'Mascota' : 'Perfil principal')}</div>
          </div>
        </div>
        <NavRow
          icon={Users}
          title="Perfiles (familia y mascotas)"
          subtitle={profiles.length <= 1 ? 'Agrega a tu familia y a tus mascotas' : `${profiles.length} perfiles · cambiar, agregar o eliminar`}
          onClick={() => nav('/perfiles')}
        />
      </Card>

      <Card title="Respaldo">
        <SettingHeader
          icon={Database}
          title="Guarda una copia de tu historial"
          text="Tus datos viven solo en este dispositivo. Exporta un respaldo de vez en cuando y guárdalo en Archivos, iCloud o tu correo; con él puedes recuperar todo en este u otro teléfono."
        />
        <div className="stack tight pf-backup"><BackupButtons /></div>
        <NavRow icon={FileDown} title="Exportar y respaldo" subtitle="Más opciones para exportar tus datos" onClick={() => nav('/exportar')} />
      </Card>

      <Card title="Apariencia">
        <SettingHeader icon={Palette} title="Tema" text="Colores claros, oscuros o los que use tu teléfono." />
        <Segmented options={THEMES} value={(themeMode ?? 'system') as 'system' | 'light' | 'dark'} onChange={(v) => void setThemeMode(v)} />
      </Card>

      {!pet && (
        <Card title="Módulos">
          <div className="row" style={{ alignItems: 'flex-start' }}>
            <Droplet size={24} color="var(--period)" style={{ marginTop: 2, flex: '0 0 auto' }} />
            <div className="grow">
              <Toggle
                label="Ciclo menstrual"
                sub="Calendario del periodo, predicciones y estadísticas. Tus registros se conservan aunque lo apagues."
                checked={cycleEnabled}
                onChange={(v) => void setCycleEnabled(v)}
              />
            </div>
          </div>
          {cycleEnabled && <NavRow icon={SlidersHorizontal} title="Ajustes del ciclo" subtitle="Duraciones y fertilidad" onClick={() => nav('/ciclo/ajustes')} color="var(--period)" />}
          <div className="divider" />
          <SettingHeader icon={PersonStanding} title="Modelo del atlas corporal" text="Figura que se muestra al abrir la sección Cuerpo." />
          <Segmented options={ATLAS} value={atlasSex === 'M' ? 'M' : 'F'} onChange={(v) => void setAtlasSex(v)} />
        </Card>
      )}

      <Card title="Privacidad y seguridad">
        <SettingHeader
          icon={KeyRound}
          title="Bloqueo con PIN"
          text={hasPin
            ? 'Activado: se pide al abrir Savia y al volver tras más de un minuto fuera.'
            : 'Pide un PIN de 4 a 6 dígitos al abrir Savia y al volver tras más de un minuto fuera.'}
          color={hasPin ? 'var(--good)' : undefined}
        />
        {hasPin ? (
          <div className="row">
            <div className="grow"><Button variant="outline" full onClick={() => setPinMode('change')}>Cambiar PIN</Button></div>
            <div className="grow"><Button variant="danger" full onClick={() => setPinMode('remove')}>Quitar</Button></div>
          </div>
        ) : (
          <Button variant="tonal" full onClick={() => setPinMode('set')}>Activar bloqueo</Button>
        )}
        <Hint>Es una barrera de privacidad sencilla: no cifra los datos. Si olvidas el PIN no se puede recuperar; habría que borrar los datos de Savia y restaurar un respaldo, así que conviene tener uno al día.</Hint>
        <div className="divider" />
        <SettingHeader
          icon={CloudOff}
          title="Tus datos se quedan contigo"
          text="Savia funciona sin cuentas y sin servidores: todo se guarda únicamente en este dispositivo. Nada se envía a ningún lado y no hay rastreo ni anuncios."
        />
      </Card>

      <Card title={installed ? 'App instalada' : 'Instalar en tu iPhone'}>
        {installed ? (
          <SettingHeader icon={ShieldCheck} color="var(--good)" title="Savia ya está en tu pantalla de inicio" text="Se abre a pantalla completa y funciona sin conexión." />
        ) : ios ? (
          <>
            <SettingHeader icon={Smartphone} title="Úsala como una app" text="Instalada se abre a pantalla completa, funciona sin conexión y el iPhone conserva mejor tus datos." />
            <ol className="pf-steps">
              <li>Abre esta página en <b>Safari</b>.</li>
              <li>Toca el botón <b>Compartir</b> <Share size={18} className="pf-inline-ic" /> en la barra de abajo.</li>
              <li>Elige <b>Agregar a inicio</b> <PlusSquare size={18} className="pf-inline-ic" />.</li>
              <li>Toca <b>Agregar</b>. El icono de Savia aparecerá en tu pantalla de inicio.</li>
            </ol>
            <Hint>Instálala antes de capturar mucha información: la app instalada guarda sus datos aparte de los de Safari.</Hint>
          </>
        ) : (
          <>
            <SettingHeader icon={Smartphone} title="Úsala como una app" text="Instalada se abre a pantalla completa y funciona sin conexión." />
            <ol className="pf-steps">
              <li><b>En iPhone o iPad:</b> abre esta página en Safari, toca Compartir <Share size={18} className="pf-inline-ic" /> y elige «Agregar a inicio».</li>
              <li><b>En Android o computadora:</b> abre el menú del navegador y elige «Instalar app» o «Agregar a la pantalla principal».</li>
            </ol>
          </>
        )}
      </Card>

      <Card title="Almacenamiento">
        <SettingHeader
          icon={HardDrive}
          title={storage?.usage != null ? `Savia ocupa ${bytesText(storage.usage)}` : 'Datos guardados en este dispositivo'}
          text={storage?.usage != null && storage.quota ? `De ${bytesText(storage.quota)} disponibles para la app (aproximado).` : 'Este navegador no informa cuánto espacio se usa.'}
        />
        <SettingHeader
          icon={ShieldCheck}
          color={storage?.persisted ? 'var(--good)' : 'var(--warn)'}
          title={storage?.persisted ? 'Almacenamiento protegido' : 'Almacenamiento sin proteger'}
          text={storage?.persisted
            ? 'El navegador no borrará tus datos por falta de espacio.'
            : 'El navegador podría borrar datos si falta espacio o si pasas mucho tiempo sin abrir la página. Instalar la app y hacer respaldos lo evita.'}
        />
        {storage && !storage.persisted && <Button variant="outline" full onClick={() => void askPersist()}>Proteger mis datos</Button>}
        <Hint>Los datos viven solo en este dispositivo y en este navegador: no se sincronizan con otros teléfonos. Si borras los datos de Safari o eliminas la app, se pierden; por eso importa el respaldo.</Hint>
      </Card>

      <Card title="Datos">
        <NavRow
          icon={UserRound}
          title={pet || active.id !== 1 ? `Ficha de ${name}` : 'Mi ficha'}
          subtitle={pet ? 'Datos de la mascota y su veterinaria' : 'Datos personales y de emergencia'}
          onClick={() => nav('/ficha')}
        />
        <NavRow
          icon={Trash2}
          color="var(--error)"
          title="Borrar los datos de este perfil"
          subtitle={`Elimina todo lo registrado de ${name}`}
          onClick={() => setWipeAsk(true)}
        />
      </Card>

      <Card title="Acerca de">
        <div className="row">
          <img src="./favicon.svg" width={52} height={52} alt="" style={{ borderRadius: 14 }} />
          <div className="grow">
            <div style={{ fontSize: 21, fontWeight: 700 }}>Savia</div>
            <div className="muted">Versión web {pkg.version} · compilada el {__BUILD_ID__.slice(0, 10)}</div>
          </div>
        </div>
        <InfoNote>
          Savia es una bitácora personal de salud. No da diagnósticos ni sustituye la atención de profesionales de la salud.
          En una emergencia, llama a los servicios de emergencia de tu localidad (911 en México).
        </InfoNote>
        <Hint>Privacidad: sin servidores, sin cuentas y sin rastreo. Lo que anotas no sale de tu dispositivo, salvo los archivos que tú decidas exportar o compartir.</Hint>
      </Card>

      <PinSheet mode={pinMode} onClose={() => setPinMode(null)} onDone={(msg) => { setPinMode(null); toast(msg) }} />

      <ConfirmDialog
        open={wipeAsk}
        title={`¿Borrar los datos de ${name}?`}
        text={`Se eliminará de este dispositivo todo lo registrado en el perfil de ${name}: síntomas, mediciones, vacunas, estudios y archivos adjuntos, ${pet ? '' : 'ciclo, bienestar, '}medicamentos, citas y los datos de su ficha. Se conservan el nombre del perfil y los ajustes; los demás perfiles no se tocan. No se puede deshacer. Si quieres conservarlos, primero exporta un respaldo.`}
        confirmLabel="Continuar"
        onConfirm={() => { setWipeAsk(false); setTyped(''); setWipeConfirm(true) }}
        onClose={() => setWipeAsk(false)}
      />
      <Dialog
        open={wipeConfirm}
        onClose={() => setWipeConfirm(false)}
        title="Confirma el borrado"
        actions={
          <>
            <Button variant="text" onClick={() => setWipeConfirm(false)}>Cancelar</Button>
            <Button
              variant="text"
              disabled={!wipeOk}
              style={{ color: 'var(--error)' }}
              onClick={() => {
                setWipeConfirm(false)
                void wipeProfileData(db).then(() => toast(`Se borraron los datos de ${name}`), () => toast('No se pudieron borrar los datos.'))
              }}
            >
              Borrar datos
            </Button>
          </>
        }
      >
        <p>Para confirmar que quieres eliminar de forma definitiva los datos de {name}, escribe {DELETE_WORD}.</p>
        <TextInput value={typed} onChange={(v) => setTyped(v.slice(0, 12))} placeholder={DELETE_WORD} autoCapitalize="none" />
      </Dialog>
    </Page>
  )
}
