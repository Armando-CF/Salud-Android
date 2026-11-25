import { useLiveQuery } from 'dexie-react-hooks'
import { meta, type SaviaDB } from './db'
import { useDb } from './profiles'

/** Ajustes propios de cada perfil, con sus valores por defecto. */
export const PREF_DEFAULTS = {
  /** Módulo de ciclo menstrual visible. */
  cycleEnabled: false,
  /** Duración típica del ciclo (días) mientras no haya historial suficiente. */
  cycleLength: 28,
  /** Duración típica del periodo (días). */
  periodLength: 5,
  /** Fase lútea (días entre ovulación y siguiente periodo). */
  lutealLength: 14,
  /** Mostrar ventana fértil y ovulación estimadas. */
  showFertility: true,
  /** Modelo del atlas corporal: "F" o "M". */
  atlasSex: 'F' as string,
  /** Nivel de actividad física para estimar el gasto de energía: clave de ACTIVITY_LEVELS o "" si no se ha elegido. */
  activityLevel: '' as string,
}
export type PrefKey = keyof typeof PREF_DEFAULTS
export type PrefValue<K extends PrefKey> = (typeof PREF_DEFAULTS)[K]

export async function getPref<K extends PrefKey>(db: SaviaDB, key: K): Promise<PrefValue<K>> {
  const row = await db.prefs.get(key)
  return (row === undefined ? PREF_DEFAULTS[key] : row.value) as PrefValue<K>
}

export async function setPref<K extends PrefKey>(db: SaviaDB, key: K, value: PrefValue<K>): Promise<void> {
  await db.prefs.put({ key, value })
}

/** Todos los ajustes del perfil (para respaldo). */
export async function allPrefs(db: SaviaDB): Promise<Record<string, unknown>> {
  const out: Record<string, unknown> = { ...PREF_DEFAULTS }
  for (const row of await db.prefs.toArray()) out[row.key] = row.value
  return out
}

/** Ajuste del perfil activo, reactivo: `const [cycleEnabled, setCycleEnabled] = usePref('cycleEnabled')`. */
export function usePref<K extends PrefKey>(key: K): [PrefValue<K>, (v: PrefValue<K>) => Promise<void>] {
  const db = useDb()
  const row = useLiveQuery(() => db.prefs.get(key), [db, key])
  const value = (row === undefined ? PREF_DEFAULTS[key] : row.value) as PrefValue<K>
  return [value, (v) => setPref(db, key, v)]
}

/** Ajustes globales (iguales para todos los perfiles). */
export const SETTING_DEFAULTS = {
  /** Ya se mostró la bienvenida. */
  onboardingDone: false,
  /** "system", "light" o "dark". */
  themeMode: 'system' as string,
  /** Hash (SHA-256, con sal) del PIN de bloqueo; "" = sin bloqueo. */
  pinHash: '' as string,
  pinSalt: '' as string,
  /** Fecha (ms) del último respaldo exportado. */
  lastBackup: 0,
}
export type SettingKey = keyof typeof SETTING_DEFAULTS
export type SettingValue<K extends SettingKey> = (typeof SETTING_DEFAULTS)[K]

export async function getSetting<K extends SettingKey>(key: K): Promise<SettingValue<K>> {
  const row = await meta.kv.get(key)
  return (row === undefined ? SETTING_DEFAULTS[key] : row.value) as SettingValue<K>
}

export async function setSetting<K extends SettingKey>(key: K, value: SettingValue<K>): Promise<void> {
  await meta.kv.put({ key, value })
  if (key === 'themeMode') applyTheme(value as string)
}

/** Ajuste global reactivo. Devuelve `undefined` mientras carga. */
export function useSetting<K extends SettingKey>(key: K): [SettingValue<K> | undefined, (v: SettingValue<K>) => Promise<void>] {
  const row = useLiveQuery(async () => ({ v: await getSetting(key) }), [key])
  return [row?.v, (v) => setSetting(key, v)]
}

/** Aplica el tema ("system" | "light" | "dark") al documento y lo deja en caché para el siguiente arranque. */
export function applyTheme(mode: string): void {
  if (typeof document === 'undefined') return
  try { localStorage.setItem('savia-theme', mode) } catch { /* sin almacenamiento local */ }
  const dark = mode === 'dark' || (mode !== 'light' && window.matchMedia('(prefers-color-scheme: dark)').matches)
  document.documentElement.dataset.theme = dark ? 'dark' : 'light'
  const color = dark ? '#0F1512' : '#1B6B58'
  document.querySelectorAll('meta[name="theme-color"]').forEach((m) => m.setAttribute('content', color))
}
