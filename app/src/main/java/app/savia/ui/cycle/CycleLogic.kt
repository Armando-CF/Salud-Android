package app.savia.ui.cycle

import app.savia.cycle.CycleAnalysis
import app.savia.cycle.CycleEngine
import app.savia.cycle.Phase
import app.savia.data.CycleCatalog
import app.savia.data.CycleDay
import app.savia.util.Fmt
import java.time.LocalDate

/** Cuántas veces apareció un síntoma (o ánimo) y en qué fases. */
data class PhaseStat(
    val key: String,
    val label: String,
    val count: Int,
    val byPhase: Map<Phase, Int>,
) {
    /** Fase donde más aparece (null si no se pudo determinar ninguna). */
    val topPhase: Phase? get() = byPhase.filterKeys { it != Phase.DESCONOCIDA }.maxByOrNull { it.value }?.key
}

/** Qué acción rápida ofrecer hoy. */
enum class QuickState {
    /** No hay periodo en curso: "Empezó mi periodo". */
    START,

    /** Ayer hubo sangrado y hoy no hay registro: "Sigue hoy" / "Ya terminó". */
    CONTINUE,

    /** Hoy ya tiene flujo registrado. */
    LOGGED,
}

/** Estado de hoy listo para mostrarse (encabezado del ciclo, tarjeta de inicio). */
data class TodayStatus(
    /** Día del ciclo contado desde el inicio del último periodo registrado (null si es muy viejo o no hay datos). */
    val cycleDay: Int?,
    /** Etiqueta de fase o situación ("Fase lútea", "Periodo retrasado"…). */
    val phaseLabel: String,
    /** Mensaje principal ("Periodo en 5 días"). */
    val headline: String,
    /** Detalle bajo el mensaje principal (fecha estimada, etc.). */
    val detail: String?,
    /** Hoy cae dentro de un periodo registrado. */
    val inPeriod: Boolean,
)

/** Lógica pura (sin Compose) del módulo de ciclo. */
object CycleLogic {
    const val BASE_YEAR = 1990

    /** Días desde el último periodo a partir de los cuales las predicciones ya no se muestran como vigentes. */
    const val STALE_DAYS = CycleEngine.MAX_VALID_CYCLE + 30

    fun dias(n: Int): String = if (n == 1) "1 día" else "$n días"

    // ---- Calendario ----

    /** Índice de mes continuo (enero de [BASE_YEAR] = 0) para el paginador. */
    fun monthIndex(date: LocalDate): Int = (date.year - BASE_YEAR) * 12 + (date.monthValue - 1)

    fun monthOfIndex(index: Int): LocalDate =
        LocalDate.of(BASE_YEAR + Math.floorDiv(index, 12), Math.floorMod(index, 12) + 1, 1)

    /**
     * Celdas del mes (epochDay o null para huecos), semana empezando en lunes.
     * Con [fixedWeeks] siempre devuelve 42 celdas (6 semanas) para que la altura no brinque.
     */
    fun monthGrid(month: LocalDate, fixedWeeks: Boolean = true): List<Long?> {
        val first = month.withDayOfMonth(1)
        val lead = first.dayOfWeek.value - 1
        val cells = ArrayList<Long?>(42)
        repeat(lead) { cells += null }
        val base = first.toEpochDay()
        for (d in 0 until first.lengthOfMonth()) cells += base + d
        val target = if (fixedWeeks) 42 else ((cells.size + 6) / 7) * 7
        while (cells.size < target) cells += null
        return cells
    }

    // ---- Registros de un día ----

    /** ¿El registro no contiene nada? (se elimina la fila en vez de guardarla). */
    fun isEmpty(d: CycleDay): Boolean =
        d.flow == 0 && d.symptoms.isBlank() && d.moods.isBlank() && d.mucus.isBlank() && d.basalTemp == null &&
            d.sex == 0 && d.ovulationTest == 0 && d.pregnancyTest == 0 && !d.contraceptive && d.notes.isBlank()

    /** ¿Tiene algo además del flujo? (para el puntito del calendario). */
    fun hasExtras(d: CycleDay): Boolean = !isEmpty(d.copy(flow = 0))

    /** Devuelve el registro con el flujo cambiado, o null si quedaría vacío (hay que borrar la fila). */
    fun withFlow(existing: CycleDay?, day: Long, flow: Int): CycleDay? {
        val n = (existing ?: CycleDay(epochDay = day)).copy(flow = flow.coerceIn(0, 4))
        return if (isEmpty(n)) null else n
    }

    /** Líneas legibles de lo registrado en un día. */
    fun summary(d: CycleDay): List<String> {
        val out = ArrayList<String>()
        if (d.flow > 0) out += if (d.flow == 1) "Manchado" else "Flujo ${CycleCatalog.flowLabel(d.flow).lowercase()}"
        val s = CycleCatalog.split(d.symptoms)
        if (s.isNotEmpty()) out += "Síntomas: " + s.joinToString(", ") { CycleCatalog.symptomLabel(it).lowercase() }
        val m = CycleCatalog.split(d.moods)
        if (m.isNotEmpty()) out += "Ánimo: " + m.joinToString(", ") { CycleCatalog.moodLabel(it).lowercase() }
        if (d.mucus.isNotBlank()) out += "Flujo cervical: " + CycleCatalog.mucusLabel(d.mucus).lowercase()
        d.basalTemp?.let { out += "Temperatura basal: ${Fmt.num(it, 2)} °C" }
        if (d.sex != 0) out += "Relaciones: " + CycleCatalog.sexLabel(d.sex).lowercase()
        if (d.ovulationTest != 0) out += "Prueba de ovulación: " + CycleCatalog.testLabel(d.ovulationTest).lowercase()
        if (d.pregnancyTest != 0) out += "Prueba de embarazo: " + CycleCatalog.testLabel(d.pregnancyTest).lowercase()
        if (d.contraceptive) out += "Anticonceptivo tomado o aplicado"
        if (d.notes.isNotBlank()) out += "Notas: " + d.notes.trim()
        return out
    }

    // ---- Estado de hoy ----

    fun quickState(a: CycleAnalysis, todayRecord: CycleDay?): QuickState = when {
        (todayRecord?.flow ?: 0) >= 1 -> QuickState.LOGGED
        a.lastPeriod?.end == a.today - 1 -> QuickState.CONTINUE
        else -> QuickState.START
    }

    /**
     * Resume la situación de hoy. No usa `analysis.currentCycleDay` porque, cuando hay retraso o el
     * periodo se espera hoy, el motor ancla un inicio estimado en hoy y reportaría "día 1".
     */
    fun todayStatus(a: CycleAnalysis): TodayStatus {
        val last = a.lastPeriod
            ?: return TodayStatus(null, "Sin datos", "Registra tu periodo para empezar", null, false)
        val sinceStart = (a.today - last.start + 1).toInt()
        if (sinceStart > STALE_DAYS) {
            return TodayStatus(
                null, "Sin registros recientes", "Registra tu periodo para reanudar las predicciones",
                "Último periodo: ${Fmt.date(last.start)}", false,
            )
        }
        val inPeriod = a.today in last
        val next = a.nextPeriodStart
        return when {
            inPeriod -> TodayStatus(
                sinceStart, Phase.MENSTRUAL.label, "Día $sinceStart de tu periodo",
                "Empezó el ${Fmt.dateWeekday(last.start)}", true,
            )
            a.lateDays > 0 -> TodayStatus(
                sinceStart, "Periodo retrasado", "${dias(a.lateDays)} de retraso",
                "Se esperaba el ${Fmt.dateWeekday(last.start + a.cycleLength)}", false,
            )
            next != null && next <= a.today -> TodayStatus(
                sinceStart, "Periodo esperado", "Tu periodo podría llegar hoy",
                "Según tu ciclo de ${dias(a.cycleLength)}", false,
            )
            next != null -> TodayStatus(
                sinceStart, a.info(a.today).phase.label, "Periodo en ${dias((next - a.today).toInt())}",
                "Estimado para el ${Fmt.dateWeekday(next)}", false,
            )
            else -> TodayStatus(sinceStart, a.info(a.today).phase.label, "Día $sinceStart del ciclo", null, false)
        }
    }

    fun regularityText(a: CycleAnalysis): String = when (a.regular) {
        true -> "Regular"
        false -> "Irregular"
        null -> "Sin datos suficientes"
    }

    /** "28 nov – 4 dic", o "21 – 27 nov" si ambos días caen en el mismo mes. */
    fun rangeText(range: LongRange): String {
        val a = LocalDate.ofEpochDay(range.first)
        val b = LocalDate.ofEpochDay(range.last)
        return if (a.year == b.year && a.month == b.month) "${a.dayOfMonth} – ${Fmt.dateShort(range.last)}"
        else Fmt.dateShort(range.first) + " – " + Fmt.dateShort(range.last)
    }

    /**
     * Las 42 celdas del mes con su fecha real: los huecos de [monthGrid] se rellenan con los días
     * del mes anterior/siguiente (second = false) para dibujarlos atenuados.
     */
    fun monthGridDays(month: LocalDate): List<Pair<Long, Boolean>> {
        val cells = monthGrid(month)
        val first = month.withDayOfMonth(1).toEpochDay()
        val lead = cells.indexOfFirst { it != null }
        return cells.mapIndexed { i, c -> (c ?: (first - lead + i)) to (c != null) }
    }

    // ---- Estadísticas ----

    /** Fase simplificada para estadísticas (la ovulación cuenta como ventana fértil). */
    private fun statPhase(a: CycleAnalysis, day: Long): Phase {
        val p = a.info(day).phase
        return if (p == Phase.OVULACION) Phase.FERTIL else p
    }

    private fun stats(
        days: List<CycleDay>,
        a: CycleAnalysis,
        keys: (CycleDay) -> String,
        label: (String) -> String,
    ): List<PhaseStat> {
        val acc = LinkedHashMap<String, MutableMap<Phase, Int>>()
        for (d in days) {
            val ks = CycleCatalog.split(keys(d))
            if (ks.isEmpty()) continue
            val phase = statPhase(a, d.epochDay)
            for (k in ks) {
                val m = acc.getOrPut(k) { LinkedHashMap() }
                m[phase] = (m[phase] ?: 0) + 1
            }
        }
        return acc.map { (k, m) -> PhaseStat(k, label(k), m.values.sum(), m) }
            .sortedWith(compareByDescending<PhaseStat> { it.count }.thenBy { it.label })
    }

    /** Síntomas del más al menos frecuente, con su reparto por fase. */
    fun symptomStats(days: List<CycleDay>, a: CycleAnalysis): List<PhaseStat> =
        stats(days, a, { it.symptoms }, CycleCatalog::symptomLabel)

    /** Estados de ánimo del más al menos frecuente, con su reparto por fase. */
    fun moodStats(days: List<CycleDay>, a: CycleAnalysis): List<PhaseStat> =
        stats(days, a, { it.moods }, CycleCatalog::moodLabel)

    fun phaseShort(p: Phase): String = when (p) {
        Phase.MENSTRUAL -> "la menstruación"
        Phase.FOLICULAR -> "la fase folicular"
        Phase.FERTIL, Phase.OVULACION -> "la ventana fértil"
        Phase.LUTEA -> "la fase lútea (antes del periodo)"
        Phase.DESCONOCIDA -> "días sin fase conocida"
    }
}
