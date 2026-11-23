// Acciones sobre tomas, reutilizables desde cualquier pantalla (p. ej. la de inicio).
import type { SaviaDB } from '../../data/db'
import type { MedIntake } from '../../data/types'
import { SKIPPED, TAKEN, type DoseItem } from './schedule'

/** Registra una toma programada. `status` = "taken", "skipped" o null para deshacer el registro. */
export async function setDose(db: SaviaDB, medId: number, epochDay: number, slot: string, status: string | null): Promise<void> {
  await db.transaction('rw', db.medIntakes, async () => {
    await db.medIntakes.where('epochDay').equals(epochDay).filter((i) => i.medicationId === medId && i.slot === slot).delete()
    if (status !== null) await db.medIntakes.add({ medicationId: medId, epochDay, slot, timestamp: Date.now(), status })
  })
}

/** Marca una toma programada como tomada. */
export const takeDose = (db: SaviaDB, dose: DoseItem, epochDay: number): Promise<void> => setDose(db, dose.med.id!, epochDay, dose.slot, TAKEN)

/** Marca una toma programada como omitida. */
export const skipDose = (db: SaviaDB, dose: DoseItem, epochDay: number): Promise<void> => setDose(db, dose.med.id!, epochDay, dose.slot, SKIPPED)

/** Deshace el registro de una toma programada. */
export const undoDose = (db: SaviaDB, dose: DoseItem, epochDay: number): Promise<void> => setDose(db, dose.med.id!, epochDay, dose.slot, null)

/** Registra ahora una toma libre (medicamentos "según sea necesario"). */
export async function logDoseNow(db: SaviaDB, medId: number, epochDay: number): Promise<void> {
  await db.medIntakes.add({ medicationId: medId, epochDay, slot: '', timestamp: Date.now(), status: TAKEN })
}

export async function deleteIntake(db: SaviaDB, intake: MedIntake): Promise<void> {
  if (intake.id !== undefined) await db.medIntakes.delete(intake.id)
}

/** Borra un medicamento junto con todo su historial de tomas. */
export async function deleteMedication(db: SaviaDB, medId: number): Promise<void> {
  await db.transaction('rw', db.medications, db.medIntakes, async () => {
    await db.medIntakes.where('medicationId').equals(medId).delete()
    await db.medications.delete(medId)
  })
}
