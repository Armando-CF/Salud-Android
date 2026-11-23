package app.savia.ui.vaccines

import app.savia.data.Vaccination
import java.time.LocalDate

/** Situación de una próxima aplicación pendiente. */
enum class DueStatus(val label: String) {
    VENCIDA("Vencida"),
    PROXIMA("Próxima"),
    PROGRAMADA("Programada"),
}

/**
 * Próxima aplicación pendiente: sale del campo `nextDueEpochDay` de un registro que aún no ha sido
 * "cumplido" por un registro posterior con el mismo nombre.
 */
data class DueItem(
    /** Registro del que proviene la próxima fecha (la última aplicación conocida). */
    val vaccination: Vaccination,
    val dueDay: Long,
    val overdue: Boolean,
)

/** Lógica pura (sin Compose ni Android) del módulo de vacunas. */
object VaccineLogic {
    /** Una fecha cuenta como "próxima" cuando faltan estos días o menos. */
    const val SOON_DAYS = 30

    fun status(dueDay: Long, today: Long): DueStatus = when {
        dueDay < today -> DueStatus.VENCIDA
        dueDay <= today + SOON_DAYS -> DueStatus.PROXIMA
        else -> DueStatus.PROGRAMADA
    }

    fun status(item: DueItem, today: Long): DueStatus = status(item.dueDay, today)

    /** ¿Son "la misma vacuna"? Mismo tipo y mismo nombre sin distinguir mayúsculas ni acentos. */
    fun sameVaccine(a: Vaccination, b: Vaccination): Boolean =
        a.type == b.type && VaccineCatalog.normalize(a.name) == VaccineCatalog.normalize(b.name)

    /** ¿[b] se aplicó después de [a]? (mismo día: gana el id mayor) */
    private fun isLater(b: Vaccination, a: Vaccination): Boolean =
        b.epochDay > a.epochDay || (b.epochDay == a.epochDay && b.id > a.id)

    /**
     * ¿La próxima fecha de [v] ya quedó atendida? Sí cuando existe un registro posterior de la misma
     * vacuna que (a) se aplicó en esa fecha o después —con un margen por si se adelantó— o (b) trae su
     * propia próxima fecha (ese registro es el que lleva ahora el calendario).
     */
    fun isFulfilled(v: Vaccination, all: List<Vaccination>): Boolean {
        val due = v.nextDueEpochDay ?: return true
        val tolerance = maxOf(SOON_DAYS.toLong(), (due - v.epochDay) / 4)
        return all.any { w ->
            w.id != v.id && sameVaccine(v, w) && isLater(w, v) && (w.epochDay >= due - tolerance || w.nextDueEpochDay != null)
        }
    }

    /**
     * Próximas aplicaciones pendientes (vencidas incluidas), de la más antigua a la más lejana.
     * API para la pantalla de inicio y los recordatorios (filtra por `vaccination.remind` si hace falta).
     */
    fun upcoming(list: List<Vaccination>, today: Long): List<DueItem> =
        list.filter { it.nextDueEpochDay != null && !isFulfilled(it, list) }
            .map { DueItem(it, it.nextDueEpochDay!!, it.nextDueEpochDay < today) }
            .sortedWith(compareBy<DueItem> { it.dueDay }.thenBy { it.vaccination.name.lowercase() })

    /** Solo vencidas y las que tocan en [withinDays] días o menos. */
    fun dueSoon(list: List<Vaccination>, today: Long, withinDays: Int = SOON_DAYS): List<DueItem> =
        upcoming(list, today).filter { it.dueDay <= today + withinDays }

    /** Dosis que seguiría a [dose] ("1.ª dosis" → "2.ª dosis"; "3.ª dosis" → "Refuerzo"). */
    fun nextDose(dose: String): String {
        val d = dose.trim()
        val n = Regex("^(\\d+)").find(d)?.groupValues?.get(1)?.toIntOrNull()
        return when {
            d.isEmpty() -> ""
            n != null && d.contains("dosis", ignoreCase = true) -> if (n >= 3) "Refuerzo" else "${n + 1}.ª dosis"
            d.equals("Dosis única", ignoreCase = true) -> "Refuerzo"
            else -> d
        }
    }

    /** Fecha + meses de calendario (31 ene + 1 mes = 28/29 feb). */
    fun plusMonths(epochDay: Long, months: Int): Long = LocalDate.ofEpochDay(epochDay).plusMonths(months.toLong()).toEpochDay()

    /** Fecha + intervalo del catálogo: los múltiplos de 30/365 días se suman como meses/años de calendario. */
    fun plusInterval(epochDay: Long, days: Int): Long = when {
        days >= 365 && days % 365 == 0 -> LocalDate.ofEpochDay(epochDay).plusYears((days / 365).toLong()).toEpochDay()
        days >= 30 && days % 30 == 0 -> plusMonths(epochDay, days / 30)
        else -> epochDay + days
    }

    /** "Vencida hace 12 días", "Toca hoy", "En 5 días". */
    fun dueText(dueDay: Long, today: Long): String {
        val d = dueDay - today
        return when {
            d == 0L -> "Toca hoy"
            d == 1L -> "Toca mañana"
            d == -1L -> "Venció ayer"
            d < 0 -> "Vencida hace ${-d} días"
            d < 60 -> "En $d días"
            d < 365 -> "En ${Math.round(d / 30.4)} meses"
            else -> {
                val years = d / 365.25
                if (years < 1.5) "En 1 año" else "En ${Math.round(years)} años"
            }
        }
    }
}

/**
 * Prellenado para el alta: la lista lo deja aquí antes de navegar a `Routes.vaccineEdit()` y el editor
 * lo consume una sola vez (la ruta no admite argumentos de prellenado).
 */
internal object VaccineDraft {
    data class Draft(val type: String, val name: String, val dose: String = "", val place: String = "")

    private var pending: Draft? = null

    fun put(draft: Draft) {
        pending = draft
    }

    /** Devuelve el prellenado pendiente (si hay) y lo borra. */
    fun take(): Draft? = pending.also { pending = null }
}
