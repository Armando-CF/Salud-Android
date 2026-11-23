package app.savia.cycle

import app.savia.data.CycleDay
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Periodo registrado: del primer al último día con sangrado (inclusive). */
data class Period(val start: Long, val end: Long) {
    val length: Int get() = (end - start + 1).toInt()
    operator fun contains(day: Long): Boolean = day in start..end
}

/** Un ciclo: del primer día de un periodo al día anterior del siguiente. */
data class CycleRecord(
    val start: Long,
    val periodLength: Int,
    /** Días hasta el siguiente periodo; null si es el ciclo en curso. */
    val length: Int?,
) {
    /** ¿Entra en las estadísticas? (se descartan valores atípicos, p. ej. meses sin registrar). */
    val valid: Boolean get() = length != null && length in CycleEngine.MIN_VALID_CYCLE..CycleEngine.MAX_VALID_CYCLE
}

enum class Phase(val label: String) {
    MENSTRUAL("Menstruación"),
    FOLICULAR("Fase folicular"),
    FERTIL("Ventana fértil"),
    OVULACION("Ovulación estimada"),
    LUTEA("Fase lútea"),
    DESCONOCIDA("Sin datos"),
}

/** Lo que se sabe / estima de un día concreto. */
data class DayInfo(
    val epochDay: Long,
    /** Día del ciclo (1 = primer día del periodo) o null si no se puede saber. */
    val cycleDay: Int?,
    val phase: Phase,
    /** Dentro de un periodo registrado. */
    val inPeriod: Boolean,
    /** Dentro de un periodo estimado (futuro). */
    val predictedPeriod: Boolean,
    val fertile: Boolean,
    val ovulation: Boolean,
)

/**
 * Resultado del análisis del historial. Todas las fechas son epochDay.
 * Las predicciones son estimaciones estadísticas: NO sirven como método anticonceptivo.
 */
class CycleAnalysis(
    val periods: List<Period>,
    val cycles: List<CycleRecord>,
    /** Duración de ciclo usada para predecir (promedio reciente o el valor por defecto). */
    val cycleLength: Int,
    /** Duración de periodo usada para predecir. */
    val periodLength: Int,
    val lutealLength: Int,
    /** Promedios reales (null si aún no hay ciclos completos). */
    val avgCycle: Double?,
    val avgPeriod: Double?,
    val shortestCycle: Int?,
    val longestCycle: Int?,
    /** Desviación estándar de los ciclos válidos recientes. */
    val cycleStdDev: Double?,
    val today: Long,
    /** Inicios estimados de los próximos periodos (ordenados). */
    val predictedStarts: List<Long>,
    /** Días de retraso respecto a lo esperado (0 si no hay retraso). */
    val lateDays: Int,
) {
    val hasData: Boolean get() = periods.isNotEmpty()
    val lastPeriod: Period? get() = periods.lastOrNull()
    val nextPeriodStart: Long? get() = predictedStarts.firstOrNull()

    /** Días que faltan para el siguiente periodo estimado (0 = hoy o con retraso). */
    val daysUntilNext: Int? get() = nextPeriodStart?.let { (it - today).toInt().coerceAtLeast(0) }

    /** ¿Hoy cae dentro de un periodo registrado (o registrado hasta ayer)? */
    val periodOngoing: Boolean get() = lastPeriod?.let { it.end >= today - 1 } ?: false

    /** Regularidad: true si la variación entre ciclos recientes es de 7 días o menos. */
    val regular: Boolean? get() {
        val s = shortestCycle ?: return null
        val l = longestCycle ?: return null
        if (cycles.count { it.valid } < 3) return null
        return l - s <= 7
    }

    private val allStarts: List<Long> = (periods.map { it.start } + predictedStarts).distinct().sorted()

    /** Día de ovulación estimado para el ciclo que empieza en [start] y cuyo siguiente periodo empieza en [next]. */
    private fun ovulationOf(start: Long, next: Long): Long? {
        val ov = next - lutealLength
        return if (ov - start >= 5) ov else null
    }

    fun info(day: Long): DayInfo {
        val inPeriod = periods.any { day in it }
        // Un inicio estimado para HOY (periodo esperado o con retraso, aún sin registrar) no reinicia
        // la cuenta de hoy: hoy sigue siendo el día N del ciclo anterior.
        val start = allStarts.lastOrNull { it <= day && !(it == today && day == today && periods.none { p -> p.start == it }) }
            ?: return DayInfo(day, null, if (inPeriod) Phase.MENSTRUAL else Phase.DESCONOCIDA, inPeriod, false, false, false)
        val next = allStarts.firstOrNull { it > start } ?: (start + cycleLength)
        val cycleDay = (day - start + 1).toInt()
        if (cycleDay > CycleEngine.MAX_VALID_CYCLE + 30) {
            return DayInfo(day, null, Phase.DESCONOCIDA, inPeriod, false, false, false)
        }
        val predictedPeriod = !inPeriod && day >= today &&
            predictedStarts.any { s -> periods.none { it.start == s } && day >= s && day < s + periodLength }
        val ov = ovulationOf(start, next)
        val ovulation = ov != null && day == ov
        val fertile = ov != null && day in (ov - 5)..(ov + 1)
        val phase = when {
            inPeriod -> Phase.MENSTRUAL
            predictedPeriod && day > today -> Phase.MENSTRUAL
            ovulation -> Phase.OVULACION
            fertile -> Phase.FERTIL
            ov != null && day < ov -> Phase.FOLICULAR
            ov != null && day > ov -> Phase.LUTEA
            else -> Phase.FOLICULAR
        }
        return DayInfo(day, cycleDay, phase, inPeriod, predictedPeriod, fertile && !inPeriod, ovulation && !inPeriod)
    }

    /** Día del ciclo de hoy. */
    val currentCycleDay: Int? get() = if (hasData) info(today).cycleDay else null

    /** Próxima ovulación estimada (hoy o después). */
    val nextOvulation: Long? get() {
        for (i in allStarts.indices) {
            val s = allStarts[i]
            val n = allStarts.getOrNull(i + 1) ?: (s + cycleLength)
            val ov = ovulationOf(s, n) ?: continue
            if (ov >= today) return ov
        }
        return null
    }

    /** Próxima ventana fértil estimada (la actual si hoy cae dentro). */
    val nextFertileWindow: LongRange? get() {
        for (i in allStarts.indices) {
            val s = allStarts[i]
            val n = allStarts.getOrNull(i + 1) ?: (s + cycleLength)
            val ov = ovulationOf(s, n) ?: continue
            if (ov + 1 >= today) return (ov - 5)..(ov + 1)
        }
        return null
    }
}

object CycleEngine {
    const val MIN_VALID_CYCLE = 15
    const val MAX_VALID_CYCLE = 60

    /** Cuántos ciclos recientes se promedian para predecir. */
    const val RECENT = 6

    /** Un día cuenta como "periodo" con flujo ligero o mayor (el manchado solo no inicia un periodo). */
    fun isBleeding(day: CycleDay): Boolean = day.flow >= 2

    /**
     * Agrupa los días con sangrado en periodos. Dos días con sangrado separados por hasta
     * 2 días sin registro pertenecen al mismo periodo.
     */
    fun periods(days: List<CycleDay>): List<Period> {
        val bleed = days.filter { isBleeding(it) }.map { it.epochDay }.distinct().sorted()
        if (bleed.isEmpty()) return emptyList()
        val out = ArrayList<Period>()
        var start = bleed[0]
        var prev = bleed[0]
        for (i in 1 until bleed.size) {
            val d = bleed[i]
            if (d - prev > 3) {
                out += Period(start, prev)
                start = d
            }
            prev = d
        }
        out += Period(start, prev)
        return out
    }

    fun analyze(
        days: List<CycleDay>,
        defaultCycleLength: Int,
        defaultPeriodLength: Int,
        lutealLength: Int,
        today: Long,
        horizon: Int = 12,
    ): CycleAnalysis {
        val periods = periods(days).filter { it.start <= today }
        val cycles = periods.mapIndexed { i, p ->
            CycleRecord(p.start, p.length, periods.getOrNull(i + 1)?.let { (it.start - p.start).toInt() })
        }
        val validLengths = cycles.filter { it.valid }.map { it.length!! }
        val recent = validLengths.takeLast(RECENT)
        val avgCycle = if (recent.isEmpty()) null else recent.average()
        val std = if (recent.size < 2) null else {
            val m = recent.average()
            sqrt(recent.sumOf { (it - m) * (it - m) } / (recent.size - 1))
        }
        // Periodos terminados (el último puede seguir en curso si llega hasta hoy o ayer).
        val finished = periods.filterIndexed { i, p -> i < periods.lastIndex || p.end < today - 1 }
        val recentPeriods = finished.takeLast(RECENT).map { it.length }
        val avgPeriod = if (recentPeriods.isEmpty()) null else recentPeriods.average()

        val cycleLen = (avgCycle?.roundToInt() ?: defaultCycleLength).coerceIn(MIN_VALID_CYCLE, MAX_VALID_CYCLE)
        val periodLen = (avgPeriod?.roundToInt() ?: defaultPeriodLength).coerceIn(1, 14)
        val luteal = lutealLength.coerceIn(8, 18)

        val predicted = ArrayList<Long>()
        var late = 0
        val last = periods.lastOrNull()
        if (last != null) {
            val expected = last.start + cycleLen
            late = (today - expected).toInt().coerceAtLeast(0)
            // Si hay retraso, lo esperado es "en cualquier momento": se ancla a hoy.
            var next = maxOf(expected, today)
            // Si el historial es muy viejo (más de ~3 ciclos sin registrar) no tiene sentido anclar el retraso.
            if (late > cycleLen * 3) late = 0
            repeat(horizon) {
                predicted += next
                next += cycleLen
            }
        }
        return CycleAnalysis(
            periods = periods,
            cycles = cycles,
            cycleLength = cycleLen,
            periodLength = periodLen,
            lutealLength = luteal,
            avgCycle = avgCycle,
            avgPeriod = avgPeriod,
            shortestCycle = recent.minOrNull(),
            longestCycle = recent.maxOrNull(),
            cycleStdDev = std,
            today = today,
            predictedStarts = predicted,
            lateDays = late,
        )
    }
}
