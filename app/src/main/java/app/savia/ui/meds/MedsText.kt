package app.savia.ui.meds

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.savia.Savia

/**
 * Textos del módulo de medicamentos y citas según el tipo de perfil activo (persona o mascota).
 * La lógica es la misma para ambos; solo cambian etiquetas y ejemplos.
 */
internal class MedsText(val isPet: Boolean) {
    val appointmentsTitle: String get() = if (isPet) "Citas veterinarias" else "Citas médicas"
    val appointmentDefault: String get() = if (isPet) "Cita veterinaria" else "Cita médica"

    val appointmentsEmpty: String
        get() = if (isPet) "Anota sus consultas, vacunas y estudios programados. Savia puede avisarte un día antes y 2 horas antes."
        else "Anota tus consultas y estudios programados. Savia puede avisarte un día antes y 2 horas antes."

    val reasonPlaceholder: String get() = if (isPet) "p. ej. Vacuna anual, revisión, desparasitación" else "p. ej. Revisión anual"
    val doctorLabel: String get() = if (isPet) "Veterinaria o veterinario" else "Profesional de salud"
    val doctorPlaceholder: String get() = if (isPet) "p. ej. MVZ Rivera" else "p. ej. Dra. Rivera"
    val placePlaceholder: String get() = if (isPet) "Clínica u hospital veterinario" else "Consultorio, clínica u hospital"

    val notesPlaceholder: String
        get() = if (isPet) "Cambios que has notado, dudas, cartilla o estudios que debes llevar…"
        else "Síntomas que quieres comentar, dudas, estudios que debes llevar…"

    val medsEmpty: String
        get() = if (isPet) "Agrega sus medicamentos o tratamientos para llevar el control de las tomas y recibir recordatorios a su hora."
        else "Agrega tus medicamentos o tratamientos para llevar el control de tus tomas y recibir recordatorios a su hora."

    val noScheduledDoses: String
        get() = if (isPet) "No hay tomas con horario. Registra abajo las que le des según se necesiten."
        else "No tienes tomas con horario. Registra abajo las que tomes según las necesites."

    val medNamePlaceholder: String get() = if (isPet) "p. ej. Amoxicilina, antipulgas" else "p. ej. Loratadina"
    val dosePlaceholder: String get() = if (isPet) "p. ej. 1/2 tableta, 2 ml, 1 pipeta" else "p. ej. 500 mg, 1 tableta, 5 ml"
    val instructionsPlaceholder: String get() = if (isPet) "p. ej. con la comida, escondido en un premio" else "p. ej. con alimentos"
    val inactiveHint: String get() = if (isPet) "Desactívalo cuando termine el tratamiento; su historial se conserva" else "Desactívalo si dejaste de tomarlo; su historial se conserva"
    /** Quién indicó el tratamiento. */
    val prescriber: String get() = if (isPet) "tu veterinaria o veterinario" else "tu profesional de salud"
    val medNotesPlaceholder: String get() = if (isPet) "p. ej. quién lo recetó, cómo lo tolera" else "p. ej. quién lo recetó, efectos que notes"

    companion object {
        /** Textos para el perfil activo, leídos en el momento (para código que no es composable). */
        fun current(): MedsText = MedsText(Savia.isPet)
    }
}

/**
 * Textos para el perfil activo, observados: si el resumen del perfil llega después de abrir la
 * pantalla (p. ej. justo tras crearlo), las etiquetas se corrigen solas.
 */
@Composable
internal fun rememberMedsText(): MedsText {
    val info by Savia.active.collectAsStateWithLifecycle()
    return remember(info.isPet) { MedsText(info.isPet) }
}
