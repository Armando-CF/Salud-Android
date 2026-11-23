import { Annoyed, Battery, BatteryFull, BatteryLow, BatteryMedium, Frown, Laugh, Meh, Smile, Zap, type LucideIcon } from 'lucide-react'
import type { DailyLog } from '../../data/types'

/** Etiquetas, iconos y colores de las escalas 1–5 del bienestar diario (reutilizables en otras pantallas). */
export const WellbeingScale = {
  moodLabels: ['Muy mal', 'Mal', 'Regular', 'Bien', 'Excelente'],
  energyLabels: ['Sin energía', 'Poca', 'Normal', 'Buena', 'Mucha'],
  stressLabels: ['Nada', 'Poco', 'Moderado', 'Alto', 'Muy alto'],
  moodIcons: [Frown, Annoyed, Meh, Smile, Laugh] as LucideIcon[],
  energyIcons: [Battery, BatteryLow, BatteryMedium, BatteryFull, Zap] as LucideIcon[],

  moodLabel: (level: number): string => WellbeingScale.moodLabels[level - 1] ?? '',
  energyLabel: (level: number): string => WellbeingScale.energyLabels[level - 1] ?? '',
  stressLabel: (level: number): string => WellbeingScale.stressLabels[level - 1] ?? '',
  moodIcon: (level: number): LucideIcon => WellbeingScale.moodIcons[Math.min(4, Math.max(0, level - 1))],

  /** Color (variable CSS) para escalas donde más es mejor (ánimo, energía). */
  goodColor: (level: number): string => (level <= 1 ? 'var(--bad)' : level === 2 ? 'var(--warn)' : level === 3 ? 'var(--fertile)' : 'var(--good)'),

  /** Color (variable CSS) para escalas donde más es peor (estrés). */
  stressColor: (level: number): string => (level <= 2 ? 'var(--good)' : level <= 4 ? 'var(--warn)' : 'var(--bad)'),

  /** ¿El registro no tiene ningún dato? */
  isEmpty: (l: DailyLog): boolean =>
    l.mood === null && l.energy === null && l.stress === null && l.sleepHours === null && l.waterGlasses === null && l.exerciseMin === null && l.notes.trim() === '',
}

export const emptyDailyLog = (epochDay: number): DailyLog => ({
  epochDay, mood: null, energy: null, stress: null, sleepHours: null, waterGlasses: null, exerciseMin: null, notes: '',
})

/** Pasos de los contadores de hábitos (misma lógica que en Android). */
export const HabitSteps = {
  sleepMinus: (v: number | null): number | null => (v === null ? null : v <= 0 ? null : Math.max(0, v - 0.5)),
  sleepPlus: (v: number | null): number => (v === null ? 7 : Math.min(24, v + 0.5)),
  waterMinus: (v: number | null): number | null => (v === null ? null : v <= 0 ? null : v - 1),
  waterPlus: (v: number | null): number => Math.min(30, (v ?? 0) + 1),
  exerciseMinus: (v: number | null): number | null => (v === null ? null : v <= 0 ? null : Math.max(0, v - 5)),
  exercisePlus: (v: number | null): number => (v === null ? 10 : Math.min(600, v + 5)),
}

/** Promedios y totales de un conjunto de registros diarios (para la sección de tendencias). */
export function wellbeingAverages(logs: DailyLog[], today: number) {
  const avg = (sel: (l: DailyLog) => number | null): number | null => {
    const v = logs.map(sel).filter((x): x is number => x !== null)
    return v.length ? v.reduce((a, b) => a + b, 0) / v.length : null
  }
  const exerciseTotal = logs.reduce((a, l) => a + (l.exerciseMin ?? 0), 0)
  const first = logs.length ? Math.min(...logs.map((l) => l.epochDay)) : today
  const spanDays = Math.max(1, today - first + 1)
  return {
    mood: avg((l) => l.mood), energy: avg((l) => l.energy), stress: avg((l) => l.stress), sleep: avg((l) => l.sleepHours),
    water: avg((l) => l.waterGlasses), exerciseTotal, exercisePerWeek: (exerciseTotal * 7) / spanDays,
  }
}
