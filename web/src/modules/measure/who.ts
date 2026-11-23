// A quién corresponden las mediciones (portado de MeasureWho.kt).
// Las referencias del módulo (IMC, presión, pulso…) están pensadas para personas adultas:
// con mascotas y con menores de edad no se clasifica con esos rangos.
import { measureTypesFor, type MeasureType } from '../../data/catalogs'
import type { Measurement } from '../../data/types'
import { Fmt, epochDayToDate, ymdToEpochDay } from '../../lib/dates'
import { assess as assessAdult, minValue, type Assessment } from './stats'

export const ADULT_AGE = 18

export interface MeasureWho {
  isPet: boolean
  /** Clave de PetSpecies ("perro", "gato"…); vacío en personas. */
  species: string
  /** Persona menor de 18 años (según su fecha de nacimiento). */
  isMinor: boolean
  /** Nombre para textos ("el peso de Firulais"). */
  name: string
}

/** Persona adulta (o sin fecha de nacimiento): aplican las referencias habituales. */
export const isAdult = (who: MeasureWho): boolean => !who.isPet && !who.isMinor

/** ¿Menor de edad en la fecha `today`? Sin fecha de nacimiento se asume persona adulta. */
export function isMinor(birthEpochDay: number | null | undefined, today: number): boolean {
  if (birthEpochDay === null || birthEpochDay === undefined || birthEpochDay > today) return false
  const b = epochDayToDate(birthEpochDay)
  const y = b.getFullYear() + ADULT_AGE
  const m = b.getMonth() + 1
  // 29 de febrero → 28 de febrero en años no bisiestos (igual que LocalDate.plusYears)
  const lastDay = new Date(y, m, 0).getDate()
  return ymdToEpochDay(y, m, Math.min(b.getDate(), lastDay)) > today
}

export function makeWho(p: Partial<MeasureWho> = {}): MeasureWho {
  return { isPet: false, species: '', isMinor: false, name: '', ...p }
}

export function whoOf(isPet: boolean, species: string, birthEpochDay: number | null | undefined, today: number, name = ''): MeasureWho {
  return { isPet, species: isPet ? species : '', isMinor: !isPet && isMinor(birthEpochDay, today), name }
}

export const MINOR_BMI_NOTE = 'En niñas, niños y adolescentes el IMC se interpreta con tablas por edad; consúltalo con su pediatra.'
export const MINOR_VITALS_NOTE =
  'En niñas, niños y adolescentes los valores normales cambian con la edad, así que aquí no se clasifican. ' +
  'Su pediatra puede decirte qué rango es el adecuado.'
export const PET_GENERAL_NOTE =
  'Los valores normales cambian según la especie, la raza, el tamaño y la edad. ' +
  'Tu veterinaria o veterinario es quien puede interpretarlos.'

const MINOR_UNCLASSIFIED = ['weight', 'bp', 'hr', 'waist']

/** Tipos de medición que se muestran para el perfil. */
export const types = (isPet: boolean): MeasureType[] => measureTypesFor(isPet)

/**
 * Clasificación orientativa según a quién se mide: nada para mascotas; para menores no se usan
 * los rangos de adultos de peso (IMC), presión, pulso ni cintura.
 */
export function assess(who: MeasureWho, type: MeasureType, m: Measurement, heightCm: number | null): Assessment | null {
  if (who.isPet) return null
  if (who.isMinor && MINOR_UNCLASSIFIED.includes(type.key)) return null
  return assessAdult(type, m, heightCm)
}

/** ¿Se calcula el IMC? (no para mascotas) */
export const showBmi = (who: MeasureWho): boolean => !who.isPet
/** ¿Se muestra la categoría de IMC (solo personas adultas)? */
export const showBmiCategory = (who: MeasureWho): boolean => isAdult(who)

/**
 * Nota orientativa propia del perfil para un tipo de medición, o null si no hay nada que decir.
 * Mascotas: solo perro y gato tienen referencia; menores: aviso de que no se clasifica.
 */
export function note(who: MeasureWho, type: MeasureType): string | null {
  const k = type.key
  if (who.isPet) {
    if (who.species === 'perro') {
      if (k === 'temp') {
        return 'En perros la temperatura normal (rectal) ronda de 38 a 39.2 °C. Más de 39.5 °C o menos de 37.5 °C es motivo para llamar a tu veterinario.'
      }
      if (k === 'hr') {
        return 'En perros en reposo lo habitual es de 60 a 140 latidos por minuto: más lento en razas grandes y más rápido en razas pequeñas y cachorros.'
      }
      if (k === 'weight') {
        return 'El peso ideal depende de la raza y la complexión. Tu veterinario puede decirte el rango adecuado y cómo está su condición corporal.'
      }
      return null
    }
    if (who.species === 'gato') {
      if (k === 'temp') {
        return 'En gatos la temperatura normal (rectal) ronda de 38 a 39.2 °C. Más de 39.5 °C o menos de 37.5 °C es motivo para llamar a tu veterinario.'
      }
      if (k === 'hr') return 'En gatos en reposo lo habitual es de 140 a 220 latidos por minuto; suele subir con el estrés de la consulta.'
      if (k === 'weight') {
        return 'El peso ideal depende de la complexión. Tu veterinario puede decirte el rango adecuado y cómo está su condición corporal.'
      }
      return null
    }
    return null
  }
  if (who.isMinor) {
    if (k === 'weight') return MINOR_BMI_NOTE
    if (k === 'bp' || k === 'hr' || k === 'waist') return MINOR_VITALS_NOTE
    if (k === 'height') {
      return (
        'Medir la estatura cada cierto tiempo y siempre igual (sin zapatos, de pie y con la espalda recta) ' +
        'ayuda a seguir el crecimiento. Su pediatra la compara con tablas por edad.'
      )
    }
  }
  return null
}

/** Rango de referencia [mín, máx] para sombrear en la gráfica, o null si no aplica a este perfil. */
export function band(who: MeasureWho, type: MeasureType, inRange: readonly Measurement[]): [number, number] | null {
  const k = type.key
  if (who.isPet) {
    if (k === 'temp' && (who.species === 'perro' || who.species === 'gato')) return [38, 39.2]
    if (k === 'hr' && who.species === 'perro') return [60, 140]
    if (k === 'hr' && who.species === 'gato') return [140, 220]
    return null
  }
  if (k === 'glucose') {
    const fasting = inRange.filter((m) => /ayunas|antes de comer/i.test(m.tag)).length
    return fasting * 2 >= inRange.length ? [70, 99] : [70, 139]
  }
  if (k === 'hr') return who.isMinor ? null : [60, 100]
  if (k === 'spo2') return [95, 100]
  if (k === 'temp') return [36, 37.4]
  return null
}

// ---------- Formato de peso ----------

/** ¿Conviene mostrar este peso en gramos? (mascotas de menos de 1 kg) */
export const inGrams = (kg: number, isPet: boolean): boolean => isPet && Math.abs(kg) < 0.9995

/**
 * Número y unidad por separado: personas ["72.5", "kg"]; mascotas con 2 decimales ["12.35", "kg"]
 * o en gramos bajo 1 kg ["350", "g"]. Siempre se guarda en kg; esto solo es presentación.
 */
export function weightParts(kg: number, isPet: boolean): [string, string] {
  if (!isPet) return [Fmt.num(kg, 1), 'kg']
  if (inGrams(kg, true)) return [Fmt.num(kg * 1000, 0), 'g']
  return [Fmt.num(kg, 2), 'kg']
}

/** "72.5 kg", "12.35 kg" o "350 g". */
export function weightText(kg: number, isPet: boolean): string {
  const [n, u] = weightParts(kg, isPet)
  return `${n} ${u}`
}

/** Cambio con signo: "+0.4 kg", "−25 g", "0 kg". */
export function weightDelta(deltaKg: number, isPet: boolean): string {
  if (!isPet) return `${Fmt.signed(deltaKg, 1)} kg`
  if (inGrams(deltaKg, true)) return `${Fmt.signed(deltaKg * 1000, 0)} g`
  return `${Fmt.signed(deltaKg, 2)} kg`
}

/** Decimales al mostrar un valor para editarlo (el peso de mascotas necesita llegar a gramos). */
export const inputDecimals = (type: MeasureType, isPet: boolean): number => (isPet && type.key === 'weight' ? 3 : type.decimals)

/** Todo junto, con el mismo nombre que en la app de Android. */
export const MeasureRefs = {
  MINOR_BMI_NOTE, MINOR_VITALS_NOTE, PET_GENERAL_NOTE, types, assess, showBmi, showBmiCategory, note, band, inGrams,
  weightParts, weightText, weightDelta, inputDecimals, minValue,
}
