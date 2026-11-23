package app.savia.ui.symptoms

import app.savia.data.SymptomEntry
import app.savia.util.Fmt
import app.savia.util.LocaleEs
import app.savia.util.millisToEpochDay
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Resumen estadístico de un conjunto de síntomas (lógica pura, sin UI). */
data class SymptomSummary(
    val total: Int,
    val avgIntensity: Double?,
    /** Días distintos con al menos un registro. */
    val daysWithSymptoms: Int,
    /** Nombre → veces, del más frecuente al menos. */
    val topNames: List<Pair<String, Int>>,
    /** Nombre → intensidad promedio, del más intenso al menos. */
    val strongest: List<Pair<String, Double>>,
    val topRegions: List<Pair<String, Int>>,
    val topTriggers: List<Pair<String, Int>>,
    /** Etiqueta del periodo → registros (semanas o meses consecutivos, incluidos los vacíos). */
    val buckets: List<Pair<String, Int>>,
    /** true = los periodos son meses; false = semanas. */
    val monthly: Boolean,
)

object SymptomStats {
    private val marks = Regex("\\p{M}+")

    /** Minúsculas y sin acentos, para comparar y buscar. */
    fun norm(s: String): String =
        Normalizer.normalize(s.trim().lowercase(LocaleEs), Normalizer.Form.NFD).replace(marks, "")

    /** "a, b,c" → [a, b, c] */
    fun splitTags(csv: String): List<String> = csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /** Lunes de la semana a la que pertenece [epochDay]. */
    fun weekStart(epochDay: Long): Long = epochDay - Math.floorMod(epochDay + 3, 7L)

    /** Registros desde [fromDay] (inclusive); null = todos. */
    fun inRange(entries: List<SymptomEntry>, fromDay: Long?): List<SymptomEntry> =
        if (fromDay == null) entries else entries.filter { it.timestamp.millisToEpochDay() >= fromDay }

    /** ¿El registro coincide con el texto buscado? (nombre, zona, etiquetas o nota). */
    fun matches(e: SymptomEntry, query: String): Boolean {
        val q = norm(query)
        if (q.isEmpty()) return true
        return norm(e.name).contains(q) || norm(e.regionLabel ?: "").contains(q) ||
            norm(e.tags).contains(q) || norm(e.notes).contains(q)
    }

    private fun top(items: List<String>, n: Int): List<Pair<String, Int>> =
        items.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(n).map { it.key to it.value }

    /**
     * Resume los registros a partir de [fromDay] (null = todo el historial) hasta [today].
     * Agrupa por semana si el periodo es de unos 3 meses o menos; si no, por mes.
     */
    fun summarize(entries: List<SymptomEntry>, fromDay: Long?, today: Long, topN: Int = 6): SymptomSummary {
        val list = inRange(entries, fromDay)
        val days = list.map { it.timestamp.millisToEpochDay() }
        val firstDay = fromDay ?: (days.minOrNull() ?: today)
        val monthly = today - firstDay > 92

        val buckets = ArrayList<Pair<String, Int>>()
        if (monthly) {
            val counts = days.groupingBy { LocalDate.ofEpochDay(it).withDayOfMonth(1) }.eachCount()
            var m = LocalDate.ofEpochDay(firstDay).withDayOfMonth(1)
            val end = LocalDate.ofEpochDay(today).withDayOfMonth(1)
            val months = ArrayList<LocalDate>()
            while (!m.isAfter(end)) {
                months += m
                m = m.plusMonths(1)
            }
            val shown = months.takeLast(24)
            val fmt = DateTimeFormatter.ofPattern(if (shown.size > 12) "MMM yy" else "MMM", LocaleEs)
            for (mm in shown) buckets += fmt.format(mm).replace(".", "") to (counts[mm] ?: 0)
        } else {
            val counts = days.groupingBy { weekStart(it) }.eachCount()
            var w = weekStart(firstDay)
            val end = weekStart(today)
            while (w <= end) {
                buckets += Fmt.dateShort(w) to (counts[w] ?: 0)
                w += 7
            }
        }

        val strongest = list.groupBy { it.name }.map { (k, v) -> k to v.map { it.intensity }.average() }
            .sortedWith(compareByDescending<Pair<String, Double>> { it.second }.thenBy { it.first }).take(topN)

        return SymptomSummary(
            total = list.size,
            avgIntensity = if (list.isEmpty()) null else list.map { it.intensity }.average(),
            daysWithSymptoms = days.distinct().size,
            topNames = top(list.map { it.name }, topN),
            strongest = strongest,
            topRegions = top(list.mapNotNull { it.regionLabel?.takeIf { l -> l.isNotBlank() } }, topN),
            topTriggers = top(list.flatMap { splitTags(it.tags) }, topN),
            buckets = buckets,
            monthly = monthly,
        )
    }
}
