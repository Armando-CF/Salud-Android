// Lógica pura del módulo de perfiles (sin React): edades, validaciones, PIN y borrado de datos.
import { dropDb, meta, type SaviaDB } from '../../data/db'
import { emptyProfile, type Profile, type ProfileInfo } from '../../data/types'
import { PetSpecies, type Opt } from '../../data/catalogs'
import { Fmt, dateToEpochDay, epochDayToDate, today, toNumber } from '../../lib/dates'

// ── Edad ─────────────────────────────────────────────────────────────────────────────────────────

/**
 * Edad en palabras: "3 semanas", "8 meses", "2 años y 4 meses", "34 años".
 * @param detailed si es true siempre incluye los meses (útil para mascotas); si no, solo en menores de 3 años.
 */
export function ageText(birthEpochDay: number, at: number = today(), detailed = false): string {
  if (birthEpochDay > at) return ''
  const b = epochDayToDate(birthEpochDay), t = epochDayToDate(at)
  let months = (t.getFullYear() - b.getFullYear()) * 12 + (t.getMonth() - b.getMonth())
  if (t.getDate() < b.getDate()) months--
  months = Math.max(0, months)
  const y = Math.floor(months / 12), m = months % 12
  const years = (n: number) => (n === 1 ? '1 año' : `${n} años`)
  const mons = (n: number) => (n === 1 ? '1 mes' : `${n} meses`)
  if (y === 0 && m === 0) {
    const d = at - birthEpochDay
    return d < 14 ? (d === 1 ? '1 día' : `${d} días`) : `${Math.floor(d / 7)} semanas`
  }
  if (y === 0) return mons(m)
  return m > 0 && (detailed || y < 3) ? `${years(y)} y ${mons(m)}` : years(y)
}

/** Fecha de nacimiento aproximada a partir de una edad en años y meses. */
export function birthFromAge(years: number, months: number, at: number = today()): number {
  const d = epochDayToDate(at)
  const day = d.getDate()
  d.setDate(1)
  d.setMonth(d.getMonth() - (years * 12 + months))
  const last = new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate()
  d.setDate(Math.min(day, last))
  return dateToEpochDay(d)
}

/** Subtítulo de un perfil en listas: "Hija · 8 años", "Perro · 3 años y 2 meses". */
export function profileLine(info: ProfileInfo, at: number = today()): string {
  const pet = info.kind === 'pet'
  const first = pet ? PetSpecies.label(info.species) : info.relation.trim()
  const age = info.birthEpochDay !== null ? ageText(info.birthEpochDay, at, pet) : ''
  return [first, age].filter(Boolean).join(' · ')
}

// ── Formularios ──────────────────────────────────────────────────────────────────────────────────

export const RELATIONS: readonly Opt[] = ['Hija', 'Hijo', 'Pareja', 'Mamá', 'Papá', 'Hermana', 'Hermano', 'Abuela', 'Abuelo', 'Otro'].map((r) => [r, r] as const)

/** valor guardado → etiqueta */
export const BLOOD_TYPES: readonly Opt[] = [
  ['A+', 'A+'], ['A-', 'A−'], ['B+', 'B+'], ['B-', 'B−'], ['AB+', 'AB+'], ['AB-', 'AB−'], ['O+', 'O+'], ['O-', 'O−'], ['No sé', 'No sé'],
]
export const bloodLabel = (value: string): string => BLOOD_TYPES.find((b) => b[0] === value)?.[1] ?? value

/** clave → etiqueta para "esterilizado": "si" / "no" / "ns" (no sé). */
export const STERILIZED_OPTIONS: readonly Opt[] = [['si', 'Sí'], ['no', 'No'], ['ns', 'No sé']]
export const sterilizedKey = (v: boolean | null): string => (v === true ? 'si' : v === false ? 'no' : 'ns')
export const sterilizedValue = (key: string): boolean | null => (key === 'si' ? true : key === 'no' ? false : null)

/** ¿Conviene proponer el seguimiento del ciclo? Solo si eligieron Mujer y tiene 10 años o más (o no hay fecha). */
export function suggestCycle(sex: string | null, birthEpochDay: number | null, at: number = today()): boolean {
  return sex === 'F' && (birthEpochDay === null || Fmt.age(birthEpochDay, at) >= 10)
}

export const bmi = (weightKg: number, heightCm: number): number => weightKg / ((heightCm / 100) * (heightCm / 100))

/** Estatura escrita → error si no es un número entre 40 y 260 cm (vacío es válido). */
export function heightError(text: string): boolean {
  if (text.trim() === '') return false
  const v = toNumber(text)
  return v === null || v < 40 || v > 260
}

/** Meta de peso escrita → error si no es válida (vacío es válido). */
export function goalError(text: string, pet: boolean): boolean {
  if (text.trim() === '') return false
  const v = toNumber(text)
  return v === null || v < (pet ? 0.01 : 20) || v > (pet ? 200 : 400)
}

/** Solo dígitos y signos habituales de un teléfono. */
export const cleanPhone = (v: string): string => v.replace(/[^0-9+\-() #*]/g, '').slice(0, 24)

/** Título de la ficha según el perfil. */
export function profileTitle(pet: boolean, activeId: number, name: string): string {
  const n = name.trim()
  if (pet) return 'Ficha de ' + (n || 'tu mascota')
  return activeId === 1 || !n ? 'Mi ficha' : 'Ficha de ' + n
}

// ── Borrado ──────────────────────────────────────────────────────────────────────────────────────

/**
 * Borra todos los datos de salud de UN perfil. Conserva sus ajustes y su identidad (nombre, si es persona
 * o mascota, especie, parentesco y color) para que siga siendo reconocible. Los demás perfiles no se tocan.
 */
export async function wipeProfileData(db: SaviaDB): Promise<void> {
  const tables = [
    db.studyAttachments, db.studyResults, db.studies, db.medIntakes, db.medications, db.symptoms, db.measurements,
    db.dailyLogs, db.cycleDays, db.appointments, db.selfExams, db.vaccinations,
  ]
  await db.transaction('rw', [...tables, db.profile], async () => {
    for (const t of tables) await t.clear()
    const old: Profile = (await db.profile.get(1)) ?? emptyProfile()
    await db.profile.put({ ...emptyProfile(), name: old.name, kind: old.kind, species: old.species, relation: old.relation, avatarColor: old.avatarColor })
  })
}

/**
 * Elimina un perfil por completo sin dejar consultas a medias: si era el activo vuelve al perfil 1, lo quita
 * de la lista, espera a que la interfaz suelte su base de datos y al final la borra. El perfil 1 no se elimina.
 */
export async function removeProfile(id: number, settleMs = 450): Promise<void> {
  if (id === 1) return
  const active = ((await meta.kv.get('activeProfile'))?.value as number | undefined) ?? 1
  if (active === id) await meta.kv.put({ key: 'activeProfile', value: 1 })
  await meta.profiles.delete(id)
  if (settleMs > 0) await new Promise((r) => setTimeout(r, settleMs))
  await dropDb(id)
}

// ── PIN ──────────────────────────────────────────────────────────────────────────────────────────

export const PIN_MIN = 4
export const PIN_MAX = 6

/** SHA-256 en hexadecimal. Usa crypto.subtle; si no existe (página sin https), una implementación propia. */
export async function sha256Hex(text: string): Promise<string> {
  const bytes = new TextEncoder().encode(text)
  const subtle = (globalThis.crypto as Crypto | undefined)?.subtle
  if (subtle) {
    const buf = await subtle.digest('SHA-256', bytes)
    return [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, '0')).join('')
  }
  return sha256Fallback(bytes)
}

/** Sal nueva con la longitud del PIN al frente: "<longitud>:<16 bytes en hex>". */
export function newSalt(pinLength: number): string {
  const rnd = new Uint8Array(16)
  globalThis.crypto.getRandomValues(rnd)
  return `${pinLength}:${[...rnd].map((b) => b.toString(16).padStart(2, '0')).join('')}`
}

/** Longitud del PIN guardada en la sal (0 si no se conoce). */
export function pinLengthOf(salt: string): number {
  const n = parseInt(salt.split(':')[0] ?? '', 10)
  return n >= PIN_MIN && n <= PIN_MAX ? n : 0
}

export const hashPin = (pin: string, salt: string): Promise<string> => sha256Hex(`${salt}|${pin}`)

export async function verifyPin(pin: string, salt: string, hash: string): Promise<boolean> {
  return hash !== '' && (await hashPin(pin, salt)) === hash
}

export const validPin = (pin: string): boolean => new RegExp(`^\\d{${PIN_MIN},${PIN_MAX}}$`).test(pin)

function firstPrimes(count: number): number[] {
  const out: number[] = []
  for (let c = 2; out.length < count; c++) {
    let prime = true
    for (let d = 2; d * d <= c; d++) if (c % d === 0) { prime = false; break }
    if (prime) out.push(c)
  }
  return out
}

/** SHA-256 en JavaScript puro (solo para contextos sin crypto.subtle). */
export function sha256Fallback(data: Uint8Array): string {
  const frac = (x: number) => Math.floor((x - Math.floor(x)) * 4294967296) | 0
  const K = firstPrimes(64).map((p) => frac(Math.cbrt(p)))
  const H = firstPrimes(8).map((p) => frac(Math.sqrt(p)))
  const len = data.length
  const total = ((len + 9 + 63) >> 6) << 6
  const msg = new Uint8Array(total)
  msg.set(data)
  msg[len] = 0x80
  const view = new DataView(msg.buffer)
  view.setUint32(total - 8, Math.floor((len * 8) / 4294967296))
  view.setUint32(total - 4, (len * 8) >>> 0)
  const w = new Int32Array(64)
  const rotr = (x: number, s: number) => (x >>> s) | (x << (32 - s))
  for (let off = 0; off < total; off += 64) {
    for (let i = 0; i < 16; i++) w[i] = view.getInt32(off + i * 4)
    for (let i = 16; i < 64; i++) {
      const s0 = rotr(w[i - 15], 7) ^ rotr(w[i - 15], 18) ^ (w[i - 15] >>> 3)
      const s1 = rotr(w[i - 2], 17) ^ rotr(w[i - 2], 19) ^ (w[i - 2] >>> 10)
      w[i] = (w[i - 16] + s0 + w[i - 7] + s1) | 0
    }
    let a = H[0], b = H[1], c = H[2], d = H[3], e = H[4], f = H[5], g = H[6], h = H[7]
    for (let i = 0; i < 64; i++) {
      const S1 = rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25)
      const ch = (e & f) ^ (~e & g)
      const t1 = (h + S1 + ch + K[i] + w[i]) | 0
      const S0 = rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22)
      const maj = (a & b) ^ (a & c) ^ (b & c)
      const t2 = (S0 + maj) | 0
      h = g; g = f; f = e; e = (d + t1) | 0; d = c; c = b; b = a; a = (t1 + t2) | 0
    }
    H[0] = (H[0] + a) | 0; H[1] = (H[1] + b) | 0; H[2] = (H[2] + c) | 0; H[3] = (H[3] + d) | 0
    H[4] = (H[4] + e) | 0; H[5] = (H[5] + f) | 0; H[6] = (H[6] + g) | 0; H[7] = (H[7] + h) | 0
  }
  return H.map((x) => (x >>> 0).toString(16).padStart(8, '0')).join('')
}

/** Tamaño legible: 1536 → "1.5 KB". */
export function bytesText(n: number): string {
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${Fmt.num(n / 1024, 1)} KB`
  if (n < 1024 * 1024 * 1024) return `${Fmt.num(n / (1024 * 1024), 1)} MB`
  return `${Fmt.num(n / (1024 * 1024 * 1024), 2)} GB`
}
