package app.savia.ui.meds

import app.savia.data.MedIntake
import app.savia.data.Medication
import app.savia.util.formatHm
import app.savia.util.parseHm
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Una toma programada de un día: el medicamento, su hora ("HH:mm" y en minutos del día)
 * y la toma registrada para ese horario (null = pendiente).
 */
data class DoseItem(
    val med: Medication,
    /** Hora programada "HH:mm". */
    val slot: String,
    /** La misma hora en minutos del día (0..1439). */
    val minutes: Int,
    /** Registro de ese horario, si ya se marcó como tomada u omitida. */
    val intake: MedIntake?,
) {
    val taken: Boolean get() = intake?.status == MedSchedule.TAKEN
    val skipped: Boolean get() = intake?.status == MedSchedule.SKIPPED
    val pending: Boolean get() = intake == null
}

/** Tomadas / programadas en un rango. */
data class Adherence(val taken: Int, val scheduled: Int) {
    /** 0..100, o null si no había nada programado. */
    val percent: Int? get() = if (scheduled <= 0) null else (taken * 100f / scheduled).roundToInt().coerceIn(0, 100)
}

/**
 * Tomas programadas de [epochDay], ordenadas por hora.
 * Respeta `active`, fechas de inicio/fin, días de la semana (bit 0 = lunes) y los horarios CSV.
 * Los medicamentos "según sea necesario" (sin horarios) no generan tomas.
 */
fun dosesForDay(meds: List<Medication>, intakes: List<MedIntake>, epochDay: Long): List<DoseItem> {
    val byKey = HashMap<String, MedIntake>()
    for (i in intakes) {
        if (i.epochDay != epochDay || i.slot.isEmpty()) continue
        val k = "${i.medicationId}|${i.slot}"
        val prev = byKey[k]
        if (prev == null || i.timestamp >= prev.timestamp) byKey[k] = i
    }
    val out = ArrayList<DoseItem>()
    for (m in meds) {
        if (!m.active || !MedSchedule.isScheduledOn(m, epochDay)) continue
        for ((slot, minutes) in MedSchedule.slots(m)) {
            out += DoseItem(m, slot, minutes, byKey["${m.id}|$slot"])
        }
    }
    return out.sortedWith(compareBy<DoseItem> { it.minutes }.thenBy { it.med.name.lowercase() })
}

/** Lógica pura de horarios de medicamentos (sin Android), usada por pantallas y recordatorios. */
object MedSchedule {
    const val TAKEN = "taken"
    const val SKIPPED = "skipped"
    const val ALL_DAYS = 127

    /** Horarios válidos del medicamento, normalizados a "HH:mm", sin repetir y ordenados. */
    fun slots(med: Medication): List<Pair<String, Int>> = parseTimes(med.times).map { formatHm(it) to it }

    /** "8:00, 20:00" → [480, 1200] (ignora valores inválidos y repetidos). */
    fun parseTimes(csv: String): List<Int> = csv.split(',').mapNotNull { parseHm(it) }.distinct().sorted()

    /** [480, 1200] → "08:00,20:00". */
    fun joinTimes(minutes: Collection<Int>): String = minutes.distinct().sorted().joinToString(",") { formatHm(it) }

    /** Sin horarios = se toma "según sea necesario". */
    fun isAsNeeded(med: Medication): Boolean = parseTimes(med.times).isEmpty()

    /** ¿El día de la semana de [epochDay] está en la máscara? (bit 0 = lunes … bit 6 = domingo). */
    fun dayInMask(daysMask: Int, epochDay: Long): Boolean {
        val bit = LocalDate.ofEpochDay(epochDay).dayOfWeek.value - 1
        return daysMask and (1 shl bit) != 0
    }

    /** ¿Hay tomas programadas ese día? (no mira `active`). */
    fun isScheduledOn(med: Medication, epochDay: Long): Boolean {
        if (epochDay < med.startEpochDay) return false
        val end = med.endEpochDay
        if (end != null && epochDay > end) return false
        return dayInMask(med.daysMask, epochDay)
    }

    /** ¿El tratamiento ya terminó (fecha de fin anterior a [today])? */
    fun isFinished(med: Medication, today: Long): Boolean = med.endEpochDay?.let { it < today } ?: false

    /**
     * Adherencia de un medicamento entre [fromDay] y [toDay] (inclusive).
     * Cuenta como programadas las tomas cuya hora ya pasó ([nowDay], [nowMinutes]) o que ya
     * tienen registro; como tomadas, las registradas con estado "taken". No mira `active`.
     */
    fun adherence(
        med: Medication,
        intakes: List<MedIntake>,
        fromDay: Long,
        toDay: Long,
        nowDay: Long = toDay,
        nowMinutes: Int = 24 * 60,
    ): Adherence {
        val slots = slots(med)
        if (slots.isEmpty()) return Adherence(0, 0)
        val status = HashMap<String, String>()
        for (i in intakes.sortedBy { it.timestamp }) {
            if (i.medicationId == med.id && i.slot.isNotEmpty()) status["${i.epochDay}|${i.slot}"] = i.status
        }
        var scheduled = 0
        var taken = 0
        val first = maxOf(fromDay, med.startEpochDay)
        val last = minOf(toDay, med.endEpochDay ?: Long.MAX_VALUE)
        var d = first
        while (d <= last) {
            if (dayInMask(med.daysMask, d)) {
                for ((slot, minutes) in slots) {
                    val st = status["$d|$slot"]
                    val due = d < nowDay || (d == nowDay && minutes <= nowMinutes)
                    if (due || st != null) scheduled++
                    if (st == TAKEN) taken++
                }
            }
            d++
        }
        return Adherence(taken, scheduled)
    }

    /**
     * Adherencia de los últimos [days] días contando solo desde el primer registro del medicamento
     * (para no penalizar los días anteriores a empezar a usar la app). Sin registros → 0 / 0.
     */
    fun recentAdherence(med: Medication, intakes: List<MedIntake>, days: Int, today: Long, nowMinutes: Int): Adherence {
        val firstRecord = intakes.filter { it.medicationId == med.id && it.slot.isNotEmpty() }.minOfOrNull { it.epochDay }
            ?: return Adherence(0, 0)
        return adherence(med, intakes, maxOf(today - days + 1, firstRecord), today, today, nowMinutes)
    }

    /** Milisegundos epoch de un día + minutos del día en la zona indicada. */
    fun millisOf(epochDay: Long, minutesOfDay: Int, zone: ZoneId = ZoneId.systemDefault()): Long =
        LocalDate.ofEpochDay(epochDay).atTime(minutesOfDay / 60, minutesOfDay % 60).atZone(zone).toInstant().toEpochMilli()

    /** Día local (epochDay) de un instante. */
    fun dayOf(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()

    /**
     * Próxima toma programada y aún sin registrar posterior a [afterMillis], entre los medicamentos
     * activos. Devuelve el instante y la toma, o null si no hay ninguna en los próximos días.
     */
    fun nextDose(
        meds: List<Medication>,
        intakes: List<MedIntake>,
        afterMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Pair<Long, DoseItem>? {
        val registered = HashSet<String>()
        for (i in intakes) if (i.slot.isNotEmpty()) registered += "${i.medicationId}|${i.epochDay}|${i.slot}"
        val afterDay = dayOf(afterMillis, zone)
        var best: Pair<Long, DoseItem>? = null
        for (m in meds) {
            if (!m.active || m.daysMask and ALL_DAYS == 0) continue
            val slots = slots(m)
            if (slots.isEmpty()) continue
            val first = maxOf(afterDay, m.startEpochDay)
            val end = m.endEpochDay
            search@ for (d in first..(first + 7)) {
                if (end != null && d > end) break
                if (!dayInMask(m.daysMask, d)) continue
                for ((slot, minutes) in slots) {
                    val t = millisOf(d, minutes, zone)
                    if (t <= afterMillis || "${m.id}|$d|$slot" in registered) continue
                    val current = best
                    if (current == null || t < current.first) best = t to DoseItem(m, slot, minutes, null)
                    break@search
                }
            }
        }
        return best
    }

    private val dayNames = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")

    /** Letras de los días para chips (índice 0 = lunes). */
    val weekLetters: List<String> = listOf("L", "M", "M", "J", "V", "S", "D")

    /** "Todos los días", "Lunes a viernes", "lun, mié, vie"… */
    fun daysSummary(daysMask: Int): String {
        val m = daysMask and ALL_DAYS
        return when (m) {
            ALL_DAYS -> "Todos los días"
            0b0011111 -> "Lunes a viernes"
            0b1100000 -> "Fines de semana"
            0 -> "Ningún día"
            else -> (0..6).filter { m and (1 shl it) != 0 }.joinToString(", ") { dayNames[it] }
        }
    }

    /** "08:00, 20:00 · Todos los días" o "Según sea necesario". */
    fun scheduleSummary(med: Medication): String {
        val s = slots(med)
        if (s.isEmpty()) return "Según sea necesario"
        return s.joinToString(", ") { it.first } + " · " + daysSummary(med.daysMask)
    }
}
