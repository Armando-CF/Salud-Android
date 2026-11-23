import { createContext, useContext } from 'react'
import { useLiveQuery } from 'dexie-react-hooks'
import { dbFor, dropDb, meta, type SaviaDB } from './db'
import { PREF_DEFAULTS, type PrefKey } from './prefs'
import { emptyProfile, type Profile, type ProfileInfo } from './types'
import { PetSpecies } from './catalogs'

/*
 * Perfiles múltiples: personas y mascotas, cada uno con su propia base de datos.
 * El perfil 1 es el original y no se puede eliminar. Los ids nunca se reutilizan.
 */

export function toInfo(id: number, p: Profile | undefined): ProfileInfo {
  return {
    id, name: p?.name ?? '', kind: p?.kind ?? 'person', species: p?.species ?? '', sex: p?.sex ?? 'X',
    relation: p?.relation ?? '', avatarColor: p?.avatarColor ?? 0, birthEpochDay: p?.birthEpochDay ?? null,
  }
}

export const isPet = (i: { kind: string }): boolean => i.kind === 'pet'

/** Nombre para mostrar; nunca vacío. */
export function displayName(i: ProfileInfo): string {
  return i.name.trim() || (isPet(i) ? 'Mascota' : i.id === 1 ? 'Yo' : `Perfil ${i.id}`)
}

/** "Hija", "Perro", "Gato"… o "". */
export function profileSubtitle(i: ProfileInfo): string {
  return isPet(i) ? PetSpecies.label(i.species) : i.relation.trim()
}

/** Asegura que exista el perfil 1 y devuelve el id del perfil activo. */
export async function initProfiles(): Promise<number> {
  if ((await meta.profiles.count()) === 0) await meta.profiles.put({ id: 1, createdAt: Date.now() })
  const db1 = dbFor(1)
  if (!(await db1.profile.get(1))) await db1.profile.put(emptyProfile())
  const active = ((await meta.kv.get('activeProfile'))?.value as number | undefined) ?? 1
  const ids = (await meta.profiles.toArray()).map((p) => p.id)
  return ids.includes(active) ? active : 1
}

export async function profileIds(): Promise<number[]> {
  return (await meta.profiles.orderBy('id').toArray()).map((p) => p.id)
}

export async function loadInfo(id: number): Promise<ProfileInfo> {
  return toInfo(id, await dbFor(id).profile.get(1))
}

export async function allProfiles(): Promise<ProfileInfo[]> {
  const ids = await profileIds()
  return Promise.all(ids.map(loadInfo))
}

/** Crea un perfil nuevo con su ficha y ajustes iniciales; devuelve su id (no lo activa). */
export async function createProfile(profile: Partial<Profile>, prefs: Partial<Record<PrefKey, unknown>> = {}): Promise<number> {
  const next = Math.max(((await meta.kv.get('nextProfileId'))?.value as number | undefined) ?? 2, Math.max(1, ...(await profileIds())) + 1)
  await meta.kv.put({ key: 'nextProfileId', value: next + 1 })
  const db = dbFor(next)
  // Primero la ficha y los ajustes; hasta el final se publica el perfil en la lista.
  await db.profile.put({ ...emptyProfile(), ...profile, id: 1 })
  for (const [key, value] of Object.entries(prefs)) if (key in PREF_DEFAULTS) await db.prefs.put({ key, value })
  await meta.profiles.put({ id: next, createdAt: Date.now() })
  return next
}

/** Elimina por completo un perfil (su base de datos entera). El perfil 1 no se puede eliminar. */
export async function deleteProfile(id: number): Promise<void> {
  if (id === 1) return
  const active = ((await meta.kv.get('activeProfile'))?.value as number | undefined) ?? 1
  if (active === id) await meta.kv.put({ key: 'activeProfile', value: 1 })
  await meta.profiles.delete(id)
  await dropDb(id)
}

/** Cambia el perfil activo. La interfaz completa se vuelve a crear en Inicio (ver App.tsx). */
export async function switchProfile(id: number): Promise<void> {
  if (!(await meta.profiles.get(id))) return
  await meta.kv.put({ key: 'activeProfile', value: id })
}

/** Cambia de perfil y arranca en Inicio (úsalo desde la interfaz). */
export async function activateProfile(id: number): Promise<void> {
  if (typeof window !== 'undefined') window.location.hash = '#/'
  await switchProfile(id)
}

// ── React ──────────────────────────────────────────────────────────────────────────────────────

export interface ActiveProfile {
  /** Resumen del perfil activo (se actualiza al editar la ficha). */
  info: ProfileInfo
  /** Base de datos del perfil activo. */
  db: SaviaDB
  /** ¿El perfil activo es una mascota? */
  isPet: boolean
}

export const ProfileContext = createContext<ActiveProfile | null>(null)

/** Perfil activo: `const { info, db, isPet } = useActive()`. */
export function useActive(): ActiveProfile {
  const ctx = useContext(ProfileContext)
  if (!ctx) throw new Error('useActive fuera de ProfileContext')
  return ctx
}

/** Base de datos del perfil activo. */
export const useDb = (): SaviaDB => useActive().db

/** Ficha completa del perfil activo (reactiva). `undefined` mientras carga. */
export function useProfileRow(): Profile | undefined {
  const db = useDb()
  return useLiveQuery(() => db.profile.get(1), [db])
}

/** Todos los perfiles, en vivo. */
export function useProfiles(): ProfileInfo[] {
  return useLiveQuery(() => allProfiles(), []) ?? []
}

/** Id del perfil activo según la base meta (reactivo). `undefined` mientras carga. */
export function useActiveId(): number | undefined {
  return useLiveQuery(async () => {
    const v = ((await meta.kv.get('activeProfile'))?.value as number | undefined) ?? 1
    return (await meta.profiles.get(v)) ? v : 1
  }, [])
}
