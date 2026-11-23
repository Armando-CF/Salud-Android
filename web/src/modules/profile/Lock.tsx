import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { Delete } from 'lucide-react'
import { setSetting, useSetting } from '../../data/prefs'
import { Sheet } from '../../ui'
import { PIN_MAX, PIN_MIN, hashPin, newSalt, pinLengthOf, validPin, verifyPin } from './logic'
import './profile.css'

/** Tiempo en segundo plano tras el cual se vuelve a pedir el PIN. */
const RELOCK_MS = 60_000
/** Intentos fallidos antes de imponer una pausa. */
const MAX_TRIES = 5
const PAUSE_MS = 30_000

/** Puntos que muestran cuántos dígitos se han escrito. */
function PinDots(props: { length: number; total: number; shake?: boolean }) {
  return (
    <div className={`pf-dots${props.shake ? ' shake' : ''}`} aria-label={`${props.length} dígitos escritos`}>
      {Array.from({ length: Math.max(props.total, props.length, PIN_MIN) }, (_, i) => <i key={i} className={i < props.length ? 'on' : ''} />)}
    </div>
  )
}

/**
 * Teclado numérico propio (botones grandes): no depende del teclado del sistema.
 * `onOk` muestra un botón "OK" abajo a la izquierda (para PIN de longitud variable).
 */
export function PinPad(props: { onDigit: (d: string) => void; onDelete: () => void; onOk?: () => void; okDisabled?: boolean; disabled?: boolean }) {
  return (
    <div className="pf-pad" role="group" aria-label="Teclado numérico">
      {['1', '2', '3', '4', '5', '6', '7', '8', '9'].map((d) => (
        <button key={d} type="button" disabled={props.disabled} onClick={() => props.onDigit(d)}>{d}</button>
      ))}
      {props.onOk
        ? <button type="button" className="ghost" disabled={props.disabled || props.okDisabled} onClick={props.onOk}>OK</button>
        : <span />}
      <button type="button" disabled={props.disabled} onClick={() => props.onDigit('0')}>0</button>
      <button type="button" className="ghost" aria-label="Borrar" disabled={props.disabled} onClick={props.onDelete}><Delete size={28} /></button>
    </div>
  )
}

/** Entrada de un PIN ya existente: verifica solo al completar la longitud guardada. */
function usePinEntry(salt: string, hash: string, onOk: () => void) {
  const [pin, setPin] = useState('')
  const [error, setError] = useState('')
  const [shake, setShake] = useState(false)
  const [pausedUntil, setPausedUntil] = useState(0)
  const [, tick] = useState(0)
  const tries = useRef(0)
  const expected = pinLengthOf(salt)

  const fail = useCallback(() => {
    tries.current++
    setShake(true)
    window.setTimeout(() => setShake(false), 320)
    setPin('')
    if (tries.current >= MAX_TRIES) {
      tries.current = 0
      setPausedUntil(Date.now() + PAUSE_MS)
      setError('Demasiados intentos. Espera 30 segundos.')
    } else {
      setError('PIN incorrecto. Inténtalo de nuevo.')
    }
  }, [])

  const check = useCallback(async (value: string) => {
    if (await verifyPin(value, salt, hash)) { tries.current = 0; setPin(''); setError(''); onOk() } else fail()
  }, [salt, hash, onOk, fail])

  // Mientras dura la pausa se refresca la pantalla para volver a habilitar el teclado.
  useEffect(() => {
    if (pausedUntil <= Date.now()) return
    const id = window.setTimeout(() => { setPausedUntil(0); setError(''); tick((n) => n + 1) }, pausedUntil - Date.now())
    return () => window.clearTimeout(id)
  }, [pausedUntil])

  const paused = pausedUntil > Date.now()
  const onDigit = (d: string) => {
    if (paused || pin.length >= PIN_MAX) return
    const next = pin + d
    setPin(next)
    setError('')
    if (expected ? next.length === expected : next.length === PIN_MAX) void check(next)
  }
  const onDelete = () => setPin((p) => p.slice(0, -1))
  /** Solo si no se conoce la longitud: confirmar con OK. */
  const onOkKey = expected ? undefined : () => { if (pin.length >= PIN_MIN) void check(pin) }
  return { pin, error, shake, paused, expected, onDigit, onDelete, onOkKey }
}

/**
 * Si hay PIN configurado, lo pide antes de mostrar el contenido: al abrir la app y al volver tras más de
 * 60 segundos en segundo plano. Es una barrera de privacidad sencilla (no cifra los datos).
 */
export function LockGate(props: { children: ReactNode }) {
  const [pinHash] = useSetting('pinHash')
  const [pinSalt] = useSetting('pinSalt')
  const [unlocked, setUnlocked] = useState(false)
  /** Ya se desbloqueó al menos una vez en esta sesión: el contenido se conserva montado (oculto) al volver a bloquear. */
  const [everUnlocked, setEverUnlocked] = useState(false)
  const hiddenAt = useRef(0)
  const hasPin = !!pinHash

  useEffect(() => {
    const onVis = () => {
      if (document.visibilityState === 'hidden') hiddenAt.current = Date.now()
      else if (hiddenAt.current && Date.now() - hiddenAt.current > RELOCK_MS) setUnlocked(false)
    }
    document.addEventListener('visibilitychange', onVis)
    return () => document.removeEventListener('visibilitychange', onVis)
  }, [])

  const unlock = useCallback(() => { setUnlocked(true); setEverUnlocked(true) }, [])

  // Sin PIN la sesión cuenta como desbloqueada: así, al activar el PIN desde Ajustes, la app no se bloquea de golpe.
  useEffect(() => {
    if (pinHash === '') { setUnlocked(true); setEverUnlocked(true) }
  }, [pinHash])

  if (pinHash === undefined || pinSalt === undefined) return <div style={{ flex: 1, background: 'var(--header-bg)' }} />
  const locked = hasPin && !unlocked
  // La estructura es siempre la misma (con o sin PIN) para que el contenido no se vuelva a montar al activarlo.
  return (
    <>
      {(!hasPin || everUnlocked || !locked) && (
        <div className={locked ? 'pf-hidden' : undefined} style={{ display: 'contents' }} aria-hidden={locked}>{props.children}</div>
      )}
      {locked && <LockScreen salt={pinSalt} hash={pinHash} onUnlock={unlock} />}
    </>
  )
}

function LockScreen(props: { salt: string; hash: string; onUnlock: () => void }) {
  const e = usePinEntry(props.salt, props.hash, props.onUnlock)
  return (
    <div className="pf-lock" role="dialog" aria-modal="true" aria-label="Savia bloqueada">
      <img src="./favicon.svg" width={72} height={72} alt="" />
      <h1>Savia</h1>
      <div className={`msg${e.error ? ' bad' : ''}`} role="status">{e.error || 'Escribe tu PIN para entrar'}</div>
      <PinDots length={e.pin.length} total={e.expected || PIN_MIN} shake={e.shake} />
      <PinPad onDigit={e.onDigit} onDelete={e.onDelete} onOk={e.onOkKey} okDisabled={e.pin.length < PIN_MIN} disabled={e.paused} />
      <details>
        <summary>¿Olvidaste tu PIN?</summary>
        No hay forma de recuperarlo: el PIN solo existe en este dispositivo. La única salida es borrar los datos de Savia
        (elimina la app de la pantalla de inicio o borra los datos del sitio en los ajustes de Safari) y después importar tu respaldo.
      </details>
    </div>
  )
}

type Mode = 'set' | 'change' | 'remove'

/**
 * Hoja para activar, cambiar o quitar el PIN. Cambiar y quitar piden primero el PIN actual.
 * Solo se guarda un hash SHA-256 con sal; el PIN nunca se almacena.
 */
export function PinSheet(props: { mode: Mode | null; onClose: () => void; onDone: (msg: string) => void }) {
  return (
    <Sheet open={props.mode !== null} onClose={props.onClose} title={props.mode === 'set' ? 'Activar bloqueo con PIN' : props.mode === 'change' ? 'Cambiar PIN' : 'Quitar bloqueo'}>
      {props.mode !== null && <PinFlow key={props.mode} mode={props.mode} onDone={props.onDone} />}
    </Sheet>
  )
}

function PinFlow(props: { mode: Mode; onDone: (msg: string) => void }) {
  const [pinHash] = useSetting('pinHash')
  const [pinSalt] = useSetting('pinSalt')
  const [step, setStep] = useState<'current' | 'new' | 'confirm'>(props.mode === 'set' ? 'new' : 'current')
  const [first, setFirst] = useState('')
  const [pin, setPin] = useState('')
  const [error, setError] = useState('')
  const [shake, setShake] = useState(false)

  const afterCurrent = useCallback(async () => {
    if (props.mode === 'remove') {
      await setSetting('pinHash', '')
      await setSetting('pinSalt', '')
      props.onDone('Se quitó el bloqueo con PIN')
    } else {
      setStep('new')
    }
  }, [props])
  const current = usePinEntry(pinSalt ?? '', pinHash ?? '', () => { void afterCurrent() })

  if (step === 'current') {
    return (
      <div className="pf-pinsheet">
        <div className={`msg${current.error ? ' bad' : ''}`} role="status">{current.error || 'Escribe tu PIN actual'}</div>
        <PinDots length={current.pin.length} total={current.expected || PIN_MIN} shake={current.shake} />
        <PinPad onDigit={current.onDigit} onDelete={current.onDelete} onOk={current.onOkKey} okDisabled={current.pin.length < PIN_MIN} disabled={current.paused} />
      </div>
    )
  }

  const bad = (msg: string) => {
    setError(msg)
    setShake(true)
    window.setTimeout(() => setShake(false), 320)
  }
  const ok = async () => {
    if (!validPin(pin)) return
    if (step === 'new') {
      setFirst(pin); setPin(''); setError(''); setStep('confirm')
      return
    }
    if (pin !== first) {
      setFirst(''); setPin(''); setStep('new')
      bad('Los PIN no coinciden. Empieza de nuevo.')
      return
    }
    const salt = newSalt(pin.length)
    // Primero la sal y luego el hash: el bloqueo se activa cuando ya está todo guardado.
    await setSetting('pinSalt', salt)
    await setSetting('pinHash', await hashPin(pin, salt))
    props.onDone(props.mode === 'change' ? 'PIN actualizado' : 'Bloqueo con PIN activado')
  }
  return (
    <div className="pf-pinsheet">
      <div className={`msg${error ? ' bad' : ''}`} role="status">
        {error || (step === 'new' ? `Elige un PIN de ${PIN_MIN} a ${PIN_MAX} dígitos y toca OK` : 'Escríbelo otra vez para confirmar y toca OK')}
      </div>
      <PinDots length={pin.length} total={step === 'confirm' ? first.length : PIN_MIN} shake={shake} />
      <PinPad
        onDigit={(d) => { if (pin.length < PIN_MAX) { setPin(pin + d); setError('') } }}
        onDelete={() => setPin(pin.slice(0, -1))}
        onOk={() => void ok()}
        okDisabled={!validPin(pin)}
      />
      {step === 'new' && (
        <p className="small muted center" style={{ maxWidth: 330 }}>
          Es una barrera de privacidad sencilla. Si lo olvidas no se puede recuperar: tendrías que borrar los datos de Savia y restaurar un respaldo.
        </p>
      )}
    </div>
  )
}
