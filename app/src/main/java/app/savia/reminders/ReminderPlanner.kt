package app.savia.reminders

import app.savia.data.Appointment
import app.savia.data.MedIntake
import app.savia.data.Medication
import app.savia.ui.meds.MedSchedule
import app.savia.ui.meds.dosesForDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class ReminderKind { MED, APPT_DAY_BEFORE, APPT_SOON, PERIOD, SELF_EXAM }

/** Un aviso que debe mostrarse en el instante [at]. */
data class ReminderEvent(
    val at: Long,
    val kind: ReminderKind,
    /** Id del medicamento o de la cita (0 para periodo / autoexploración). */
    val refId: Long = 0,
    /** Día al que corresponde (toma, cita, inicio estimado del periodo…). */
    val epochDay: Long = 0,
    /** Horario "HH:mm" de la toma (solo medicamentos). */
    val slot: String = "",
    val title: String,
    val text: String,
) {
    /** Identidad estable del aviso (para no duplicar notificaciones). */
    val key: String get() = "${kind.name}:$refId:$epochDay:$slot"
    val notificationId: Int get() = key.hashCode()
}

/** Todo lo que se necesita para calcular avisos (ya leído de la base y de los ajustes). */
data class ReminderInput(
    val meds: List<Medication> = emptyList(),
    /** Tomas registradas de los días cercanos (para no avisar lo ya registrado). */
    val intakes: List<MedIntake> = emptyList(),
    val appointments: List<Appointment> = emptyList(),
    /** Inicios estimados de próximos periodos (epochDay); vacío si el aviso está apagado. */
    val periodStarts: List<Long> = emptyList(),
    /** Día de la última autoexploración (o ancla); null si el aviso está apagado. Se avisa cada 30 días a partir de ahí. */
    val selfExamBaseDay: Long? = null,
)

/** Cálculo puro de recordatorios (sin Android): qué avisos caen en una ventana y cuándo es el siguiente. */
object ReminderPlanner {
    const val PERIOD_DAYS_BEFORE = 2L
    const val PERIOD_MINUTES = 9 * 60
    const val SELF_EXAM_EVERY_DAYS = 30L
    const val SELF_EXAM_MINUTES = 10 * 60
    const val APPT_SOON_MS = 2 * 60 * 60 * 1000L

    private val hm = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.forLanguageTag("es-MX"))

    private fun at(day: Long, minutes: Int, zone: ZoneId) = MedSchedule.millisOf(day, minutes, zone)

    /** Avisos de cita: un día antes a la misma hora y 2 horas antes. */
    private fun appointmentEvents(a: Appointment, zone: ZoneId): List<ReminderEvent> {
        if (!a.remind || a.done) return emptyList()
        val ldt = Instant.ofEpochMilli(a.timestamp).atZone(zone).toLocalDateTime()
        val time = hm.format(ldt)
        val who = listOf(a.doctor, a.place).filter { it.isNotBlank() }.joinToString(" · ")
        val title = a.title.ifBlank { "Cita médica" }
        val day = ldt.toLocalDate().toEpochDay()
        val dayBefore = ldt.minusDays(1).atZone(zone).toInstant().toEpochMilli()
        return listOf(
            ReminderEvent(
                at = dayBefore, kind = ReminderKind.APPT_DAY_BEFORE, refId = a.id, epochDay = day,
                title = "Mañana tienes cita",
                text = "$title · mañana a las $time" + if (who.isNotEmpty()) " · $who" else "",
            ),
            ReminderEvent(
                at = a.timestamp - APPT_SOON_MS, kind = ReminderKind.APPT_SOON, refId = a.id, epochDay = day,
                title = "Tu cita es en 2 horas",
                text = "$title · hoy a las $time" + if (who.isNotEmpty()) " · $who" else "",
            ),
        )
    }

    private fun periodEvent(start: Long, zone: ZoneId) = ReminderEvent(
        at = at(start - PERIOD_DAYS_BEFORE, PERIOD_MINUTES, zone), kind = ReminderKind.PERIOD, epochDay = start,
        title = "Recordatorio de tu ciclo",
        text = "Tu periodo podría comenzar en unos 2 días (estimado: ${dayFmt.format(LocalDate.ofEpochDay(start)).replace(".", "")}).",
    )

    private fun selfExamEvent(day: Long, zone: ZoneId) = ReminderEvent(
        at = at(day, SELF_EXAM_MINUTES, zone), kind = ReminderKind.SELF_EXAM, epochDay = day,
        title = "Autoexploración mensual",
        text = "Tómate unos minutos para revisarte y anota cómo te fue.",
    )

    private fun medEvent(med: Medication, day: Long, slot: String, minutes: Int, zone: ZoneId): ReminderEvent {
        val dose = if (med.dose.isNotBlank()) " ${med.dose.trim()}" else ""
        return ReminderEvent(
            at = at(day, minutes, zone), kind = ReminderKind.MED, refId = med.id, epochDay = day, slot = slot,
            title = "Hora de tu medicamento",
            text = "${med.name}$dose · $slot",
        )
    }

    /** Avisos cuyo instante cae en (afterMillis, untilMillis], ordenados por hora. No incluye tomas ya registradas. */
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
                    val e = medEvent(dose.med, d, dose.slot, dose.minutes, zone)
                    if (e.at in window) out += e
                }
            }
        }
        for (a in input.appointments) out += appointmentEvents(a, zone).filter { it.at in window }
        for (p in input.periodStarts) periodEvent(p, zone).let { if (it.at in window) out += it }
        input.selfExamBaseDay?.let { base ->
            var k = maxOf(1L, (dayFrom - base) / SELF_EXAM_EVERY_DAYS)
            while (base + k * SELF_EXAM_EVERY_DAYS <= dayTo) {
                selfExamEvent(base + k * SELF_EXAM_EVERY_DAYS, zone).let { if (it.at in window) out += it }
                k++
            }
        }
        return out.sortedBy { it.at }
    }

    /** Instante del siguiente aviso posterior a [afterMillis], o null si no hay nada pendiente. */
    fun nextEventTime(input: ReminderInput, afterMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val candidates = ArrayList<Long>()
        MedSchedule.nextDose(input.meds.filter { it.remind && it.active }, input.intakes, afterMillis, zone)?.let { candidates += it.first }
        for (a in input.appointments) appointmentEvents(a, zone).forEach { if (it.at > afterMillis) candidates += it.at }
        for (p in input.periodStarts) periodEvent(p, zone).let { if (it.at > afterMillis) candidates += it.at }
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
        return candidates.minOrNull()
    }
}
