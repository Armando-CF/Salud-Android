package app.savia.reminders

import app.savia.data.Appointment
import app.savia.data.MedIntake
import app.savia.data.Medication
import app.savia.data.Vaccination
import app.savia.ui.meds.MedSchedule
import app.savia.ui.meds.dosesForDay
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class ReminderKind { MED, APPT_DAY_BEFORE, APPT_SOON, PERIOD, SELF_EXAM, VACCINE_SOON, VACCINE_DUE }

/** Un aviso que debe mostrarse en el instante [at]. */
data class ReminderEvent(
    val at: Long,
    val kind: ReminderKind,
    /** Id del medicamento, de la cita o de la vacuna (0 para periodo / autoexploración). */
    val refId: Long = 0,
    /** Día al que corresponde (toma, cita, inicio estimado del periodo, fecha de la vacuna…). */
    val epochDay: Long = 0,
    /** Horario "HH:mm" de la toma (solo medicamentos). */
    val slot: String = "",
    val title: String,
    val text: String,
    /** Perfil (persona o mascota) al que pertenece el aviso. */
    val profileId: Int = 1,
) {
    /**
     * Identidad estable del aviso (para no duplicar notificaciones). El perfil 1 conserva la clave
     * original; los demás perfiles la llevan con prefijo para no chocar entre sí.
     */
    val key: String
        get() = "${kind.name}:$refId:$epochDay:$slot".let { if (profileId == 1) it else "p$profileId:$it" }
    val notificationId: Int get() = key.hashCode()
}

/** Todo lo que se necesita para calcular los avisos de UN perfil (ya leído de su base y de sus ajustes). */
data class ReminderInput(
    val meds: List<Medication> = emptyList(),
    /** Tomas registradas de los días cercanos (para no avisar lo ya registrado). */
    val intakes: List<MedIntake> = emptyList(),
    val appointments: List<Appointment> = emptyList(),
    /** Inicios estimados de próximos periodos (epochDay); vacío si el aviso está apagado. */
    val periodStarts: List<Long> = emptyList(),
    /** Día de la última autoexploración (o ancla); null si el aviso está apagado. Se avisa cada 30 días a partir de ahí. */
    val selfExamBaseDay: Long? = null,
    /** Vacunas / desparasitaciones registradas (se avisa de las próximas pendientes con recordatorio). */
    val vaccinations: List<Vaccination> = emptyList(),
    /** Perfil dueño de estos datos. */
    val profileId: Int = 1,
    /**
     * Nombre del perfil para anteponer a los títulos ("Firulais · …"). Null cuando solo hay un
     * perfil: en ese caso los avisos se redactan en segunda persona, como siempre.
     */
    val profileName: String? = null,
)

/** Reglas puras de los avisos de vacunas y desparasitaciones. */
object VaccineReminders {
    /** Días de anticipación del primer aviso. */
    const val SOON_DAYS = 7L

    /** Un registro posterior del mismo nombre cuenta como "ya aplicada" si cae desde 30 días antes de la fecha prevista. */
    const val FULFILL_WINDOW_DAYS = 30L

    private val marks = Regex("\\p{M}+")
    private val spaces = Regex("\\s+")

    /** Nombre comparable: sin acentos, en minúsculas y sin espacios sobrantes. */
    fun normalize(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(marks, "")
            .lowercase(Locale.ROOT)
            .trim()
            .replace(spaces, " ")

    /**
     * ¿La próxima aplicación prevista en [v] ya quedó cumplida? Sí, si existe un registro POSTERIOR
     * con el mismo nombre aplicado en o después de (próxima fecha − 30 días).
     */
    fun isFulfilled(v: Vaccination, all: List<Vaccination>): Boolean {
        val due = v.nextDueEpochDay ?: return false
        val name = normalize(v.name)
        return all.any { w ->
            w !== v && normalize(w.name) == name &&
                (w.epochDay > v.epochDay || (w.epochDay == v.epochDay && w.id > v.id)) &&
                w.epochDay >= due - FULFILL_WINDOW_DAYS
        }
    }

    /** Registros con recordatorio activo cuya próxima fecha sigue pendiente. */
    fun pending(all: List<Vaccination>): List<Vaccination> =
        all.filter { it.remind && it.nextDueEpochDay != null && !isFulfilled(it, all) }

    /** "vacuna", "desparasitación" o "aplicación" según el tipo guardado. */
    fun noun(type: String): String = when {
        type.startsWith("desp") -> "desparasitación"
        type == "vacuna" -> "vacuna"
        else -> "aplicación"
    }
}

/** Cálculo puro de recordatorios (sin Android): qué avisos caen en una ventana y cuándo es el siguiente. */
object ReminderPlanner {
    const val PERIOD_DAYS_BEFORE = 2L
    const val PERIOD_MINUTES = 9 * 60
    const val SELF_EXAM_EVERY_DAYS = 30L
    const val SELF_EXAM_MINUTES = 10 * 60
    const val APPT_SOON_MS = 2 * 60 * 60 * 1000L
    const val VACCINE_MINUTES = 9 * 60

    private val hm = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.forLanguageTag("es-MX"))

    private fun at(day: Long, minutes: Int, zone: ZoneId) = MedSchedule.millisOf(day, minutes, zone)

    /** "Firulais · " cuando hay varios perfiles; "" con uno solo. */
    private fun prefix(input: ReminderInput): String =
        input.profileName?.trim()?.takeIf { it.isNotEmpty() }?.let { "$it · " } ?: ""

    /** Avisos de cita: un día antes a la misma hora y 2 horas antes. */
    private fun appointmentEvents(a: Appointment, input: ReminderInput, zone: ZoneId): List<ReminderEvent> {
        if (!a.remind || a.done) return emptyList()
        val ldt = Instant.ofEpochMilli(a.timestamp).atZone(zone).toLocalDateTime()
        val time = hm.format(ldt)
        val who = listOf(a.doctor, a.place).filter { it.isNotBlank() }.joinToString(" · ")
        val title = a.title.ifBlank { "Cita médica" }
        val day = ldt.toLocalDate().toEpochDay()
        val dayBefore = ldt.minusDays(1).atZone(zone).toInstant().toEpochMilli()
        val multi = input.profileName != null
        return listOf(
            ReminderEvent(
                at = dayBefore, kind = ReminderKind.APPT_DAY_BEFORE, refId = a.id, epochDay = day,
                title = if (multi) prefix(input) + "Cita mañana $time" else "Mañana tienes cita",
                text = "$title · mañana a las $time" + if (who.isNotEmpty()) " · $who" else "",
                profileId = input.profileId,
            ),
            ReminderEvent(
                at = a.timestamp - APPT_SOON_MS, kind = ReminderKind.APPT_SOON, refId = a.id, epochDay = day,
                title = if (multi) prefix(input) + "Cita en 2 horas" else "Tu cita es en 2 horas",
                text = "$title · hoy a las $time" + if (who.isNotEmpty()) " · $who" else "",
                profileId = input.profileId,
            ),
        )
    }

    private fun periodEvent(start: Long, input: ReminderInput, zone: ZoneId): ReminderEvent {
        val multi = input.profileName != null
        val est = dayFmt.format(LocalDate.ofEpochDay(start)).replace(".", "")
        return ReminderEvent(
            at = at(start - PERIOD_DAYS_BEFORE, PERIOD_MINUTES, zone), kind = ReminderKind.PERIOD, epochDay = start,
            title = if (multi) prefix(input) + "Recordatorio del ciclo" else "Recordatorio de tu ciclo",
            text = (if (multi) "El periodo" else "Tu periodo") + " podría comenzar en unos 2 días (estimado: $est).",
            profileId = input.profileId,
        )
    }

    private fun selfExamEvent(day: Long, input: ReminderInput, zone: ZoneId): ReminderEvent {
        val multi = input.profileName != null
        return ReminderEvent(
            at = at(day, SELF_EXAM_MINUTES, zone), kind = ReminderKind.SELF_EXAM, epochDay = day,
            title = prefix(input) + "Autoexploración mensual",
            text = if (multi) "Toca la autoexploración de este mes; anota cómo fue." else "Tómate unos minutos para revisarte y anota cómo te fue.",
            profileId = input.profileId,
        )
    }

    private fun medEvent(med: Medication, day: Long, slot: String, minutes: Int, input: ReminderInput, zone: ZoneId): ReminderEvent {
        val dose = if (med.dose.isNotBlank()) " ${med.dose.trim()}" else ""
        val multi = input.profileName != null
        return ReminderEvent(
            at = at(day, minutes, zone), kind = ReminderKind.MED, refId = med.id, epochDay = day, slot = slot,
            title = if (multi) prefix(input) + "${med.name} $slot" else "Hora de tu medicamento",
            text = if (multi) "Hora del medicamento: ${med.name}$dose" else "${med.name}$dose · $slot",
            profileId = input.profileId,
        )
    }

    /** Avisos de una vacuna pendiente: 7 días antes y el mismo día, a las 09:00. */
    private fun vaccineEvents(v: Vaccination, input: ReminderInput, zone: ZoneId): List<ReminderEvent> {
        val due = v.nextDueEpochDay ?: return emptyList()
        val noun = VaccineReminders.noun(v.type)
        val label = noun.replaceFirstChar { it.titlecase(Locale.ROOT) }
        val name = v.name.trim().ifEmpty { label }
        val date = dayFmt.format(LocalDate.ofEpochDay(due)).replace(".", "")
        val dose = v.dose.trim().let { if (it.isEmpty()) "" else " · $it" }
        return listOf(
            ReminderEvent(
                at = at(due - VaccineReminders.SOON_DAYS, VACCINE_MINUTES, zone), kind = ReminderKind.VACCINE_SOON,
                refId = v.id, epochDay = due,
                title = prefix(input) + "Próxima $noun: $name en ${VaccineReminders.SOON_DAYS} días",
                text = "$label prevista para el $date$dose.",
                profileId = input.profileId,
            ),
            ReminderEvent(
                at = at(due, VACCINE_MINUTES, zone), kind = ReminderKind.VACCINE_DUE,
                refId = v.id, epochDay = due,
                title = prefix(input) + "Hoy toca: $name",
                text = "$label prevista para hoy, $date$dose.",
                profileId = input.profileId,
            ),
        )
    }

    /** Avisos de un perfil cuyo instante cae en (afterMillis, untilMillis], ordenados por hora. No incluye tomas ya registradas. */
    fun eventsBetween(
        input: ReminderInput,
        afterMillis: Long,
        untilMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ReminderEvent> {
        if (untilMillis <= afterMillis) return emptyList()
        val out = ArrayList<ReminderEvent>()
        val window = (afterMillis + 1)..untilMillis
        val dayTo = MedSchedule.dayOf(untilMillis, zone)
        val dayFrom = maxOf(MedSchedule.dayOf(afterMillis, zone), dayTo - 62)

        val remindMeds = input.meds.filter { it.remind && it.active }
        if (remindMeds.isNotEmpty()) {
            for (d in dayFrom..dayTo) {
                for (dose in dosesForDay(remindMeds, input.intakes, d)) {
                    if (dose.intake != null) continue
                    val e = medEvent(dose.med, d, dose.slot, dose.minutes, input, zone)
                    if (e.at in window) out += e
                }
            }
        }
        for (a in input.appointments) out += appointmentEvents(a, input, zone).filter { it.at in window }
        for (p in input.periodStarts) periodEvent(p, input, zone).let { if (it.at in window) out += it }
        input.selfExamBaseDay?.let { base ->
            var k = maxOf(1L, (dayFrom - base) / SELF_EXAM_EVERY_DAYS)
            while (base + k * SELF_EXAM_EVERY_DAYS <= dayTo) {
                selfExamEvent(base + k * SELF_EXAM_EVERY_DAYS, input, zone).let { if (it.at in window) out += it }
                k++
            }
        }
        for (v in VaccineReminders.pending(input.vaccinations)) out += vaccineEvents(v, input, zone).filter { it.at in window }
        return out.sortedBy { it.at }
    }

    /** Instante del siguiente aviso de un perfil posterior a [afterMillis], o null si no hay nada pendiente. */
    fun nextEventTime(input: ReminderInput, afterMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val candidates = ArrayList<Long>()
        MedSchedule.nextDose(input.meds.filter { it.remind && it.active }, input.intakes, afterMillis, zone)?.let { candidates += it.first }
        for (a in input.appointments) appointmentEvents(a, input, zone).forEach { if (it.at > afterMillis) candidates += it.at }
        for (p in input.periodStarts) periodEvent(p, input, zone).let { if (it.at > afterMillis) candidates += it.at }
        input.selfExamBaseDay?.let { base ->
            val afterDay = MedSchedule.dayOf(afterMillis, zone)
            var k = maxOf(1L, (afterDay - base) / SELF_EXAM_EVERY_DAYS)
            while (true) {
                val t = at(base + k * SELF_EXAM_EVERY_DAYS, SELF_EXAM_MINUTES, zone)
                if (t > afterMillis) {
                    candidates += t
                    break
                }
                k++
            }
        }
        for (v in VaccineReminders.pending(input.vaccinations)) {
            vaccineEvents(v, input, zone).forEach { if (it.at > afterMillis) candidates += it.at }
        }
        return candidates.minOrNull()
    }

    // ── Varios perfiles ──

    /** Avisos de TODOS los perfiles en (afterMillis, untilMillis], intercalados por hora. */
    fun eventsBetween(
        inputs: List<ReminderInput>,
        afterMillis: Long,
        untilMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ReminderEvent> = inputs.flatMap { eventsBetween(it, afterMillis, untilMillis, zone) }.sortedBy { it.at }

    /** Instante del siguiente aviso entre todos los perfiles, o null si no hay nada pendiente. */
    fun nextEventTime(inputs: List<ReminderInput>, afterMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long? =
        inputs.mapNotNull { nextEventTime(it, afterMillis, zone) }.minOrNull()
}
