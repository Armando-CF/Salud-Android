package app.savia.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

val LocaleEs: Locale = Locale.forLanguageTag("es-MX")

/** Día de hoy como epochDay. */
fun today(): Long = LocalDate.now().toEpochDay()

fun nowMillis(): Long = System.currentTimeMillis()

fun Long.epochDayToDate(): LocalDate = LocalDate.ofEpochDay(this)

fun LocalDate.atMillis(hour: Int = 12, minute: Int = 0): Long =
    atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** Milisegundos → fecha y hora locales. */
fun Long.millisToDateTime(): LocalDateTime =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()

/** Milisegundos → epochDay local. */
fun Long.millisToEpochDay(): Long = millisToDateTime().toLocalDate().toEpochDay()

/** epochDay + minutos del día → milisegundos. */
fun epochDayToMillis(epochDay: Long, minutesOfDay: Int = 12 * 60): Long =
    LocalDate.ofEpochDay(epochDay).atTime(LocalTime.of((minutesOfDay / 60).coerceIn(0, 23), (minutesOfDay % 60).coerceIn(0, 59)))
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** "08:30" → 510 (minutos del día) o null. */
fun parseHm(s: String): Int? {
    val p = s.trim().split(":")
    if (p.size != 2) return null
    val h = p[0].toIntOrNull() ?: return null
    val m = p[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

/** 510 → "08:30". */
fun formatHm(minutesOfDay: Int): String = "%02d:%02d".format(minutesOfDay / 60, minutesOfDay % 60)

/** Texto de un campo numérico → Double (acepta coma o punto). */
fun String.toDoubleLenient(): Double? = trim().replace(',', '.').toDoubleOrNull()

/** Formatos de fecha y número en español. */
object Fmt {
    private val dMedium = DateTimeFormatter.ofPattern("d MMM yyyy", LocaleEs)
    private val dShort = DateTimeFormatter.ofPattern("d MMM", LocaleEs)
    private val dLong = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", LocaleEs)
    private val dWeekday = DateTimeFormatter.ofPattern("EEE d MMM", LocaleEs)
    private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", LocaleEs)
    private val hm = DateTimeFormatter.ofPattern("HH:mm", LocaleEs)
    private val iso = DateTimeFormatter.ISO_LOCAL_DATE
    private val isoDt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

    /** "23 nov 2026" */
    fun date(epochDay: Long): String = dMedium.format(LocalDate.ofEpochDay(epochDay)).replace(".", "")

    /** "23 nov" */
    fun dateShort(epochDay: Long): String = dShort.format(LocalDate.ofEpochDay(epochDay)).replace(".", "")

    /** "lun 23 nov" */
    fun dateWeekday(epochDay: Long): String = dWeekday.format(LocalDate.ofEpochDay(epochDay)).replace(".", "")

    /** "Lunes 23 de noviembre de 2026" */
    fun dateLong(epochDay: Long): String =
        dLong.format(LocalDate.ofEpochDay(epochDay)).replaceFirstChar { it.titlecase(LocaleEs) }

    /** "Noviembre 2026" */
    fun monthYear(date: LocalDate): String = monthYear.format(date).replaceFirstChar { it.titlecase(LocaleEs) }

    /** "Hoy", "Ayer", "Mañana" o "23 nov 2026". */
    fun relativeDay(epochDay: Long): String = when (epochDay - today()) {
        0L -> "Hoy"
        -1L -> "Ayer"
        1L -> "Mañana"
        else -> date(epochDay)
    }

    /** "14:05" */
    fun time(millis: Long): String = hm.format(millis.millisToDateTime())

    /** "23 nov 2026, 14:05" */
    fun dateTime(millis: Long): String = date(millis.millisToEpochDay()) + ", " + time(millis)

    /** "Hoy, 14:05" / "23 nov 2026, 14:05" */
    fun relativeDateTime(millis: Long): String = relativeDay(millis.millisToEpochDay()) + ", " + time(millis)

    /** "2026-11-23" (para exportar). */
    fun isoDate(epochDay: Long): String = iso.format(LocalDate.ofEpochDay(epochDay))

    /** "2026-11-23 14:05" (para exportar). */
    fun isoDateTime(millis: Long): String = isoDt.format(millis.millisToDateTime())

    /** Número con hasta [decimals] decimales, sin ceros sobrantes: 72.50 → "72.5", 80.0 → "80". */
    fun num(value: Double, decimals: Int = 1): String {
        val s = String.format(Locale.US, "%.${decimals}f", value)
        return if (s.contains('.')) s.trimEnd('0').trimEnd('.') else s
    }

    /** Con signo: "+1.2" / "−0.8" / "0". */
    fun signed(value: Double, decimals: Int = 1): String {
        val s = num(kotlin.math.abs(value), decimals)
        return when {
            s == "0" -> "0"
            value > 0 -> "+$s"
            else -> "−$s"
        }
    }

    /** "hace 3 días", "en 5 días", "hoy". */
    fun daysFromNow(epochDay: Long): String {
        val d = epochDay - today()
        return when {
            d == 0L -> "hoy"
            d == 1L -> "mañana"
            d == -1L -> "ayer"
            d > 0 -> "en $d días"
            else -> "hace ${-d} días"
        }
    }

    /** Edad en años cumplidos a partir de la fecha de nacimiento. */
    fun age(birthEpochDay: Long): Int =
        ChronoUnit.YEARS.between(LocalDate.ofEpochDay(birthEpochDay), LocalDate.now()).toInt()

    /** 95 → "1 h 35 min". */
    fun duration(minutes: Int): String = when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}
