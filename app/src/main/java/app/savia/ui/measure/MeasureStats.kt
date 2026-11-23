package app.savia.ui.measure

import app.savia.data.MeasureType
import app.savia.data.Measurement
import app.savia.util.Fmt
import app.savia.util.millisToDateTime
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sign

/** Nivel de una clasificación orientativa. */
enum class Level { GOOD, WARN, BAD, NEUTRAL }

/** Clasificación orientativa de un valor (NO es un diagnóstico). */
data class Assessment(val label: String, val level: Level, val detail: String = "")

/** Punto en el tiempo: x = día (epochDay con fracción), y = valor. */
data class TPoint(val x: Double, val y: Double)

/** Recta de tendencia y = intercept + slopePerDay · x. */
data class Regression(val slopePerDay: Double, val intercept: Double) {
    /** Cambio por semana (unidades/semana). */
    val perWeek: Double get() = slopePerDay * 7.0
    fun at(x: Double): Double = intercept + slopePerDay * x
}

data class RangeSummary(val count: Int, val min: Double, val max: Double, val avg: Double, val first: Double, val last: Double) {
    val change: Double get() = last - first
}

/** Fecha estimada para llegar a la meta siguiendo la tendencia actual. */
data class GoalProjection(val epochDay: Long, val daysLeft: Int)

/** Resumen de peso listo para tarjetas (inicio, hub de mediciones). */
data class WeightSummary(
    val latest: Double,
    val latestTimestamp: Long,
    val change7: Double?,
    val change30: Double?,
    val bmi: Double?,
    val bmiAssessment: Assessment?,
    val goal: Double?,
    /** meta − peso actual (negativo = falta bajar). */
    val toGoal: Double?,
    val goalReached: Boolean,
    /** Avance hacia la meta entre 0 y 1 (desde el primer peso registrado). */
    val goalProgress: Float?,
    /** Últimos valores en orden cronológico, para una mini gráfica. */
    val spark: List<Double>,
)

/** Cálculos puros del módulo de mediciones. Todas las clasificaciones son orientativas. */
object MeasureStats {

    /** Día (con fracción) en hora local de una medición. */
    fun dayX(timestamp: Long): Double {
        val dt = timestamp.millisToDateTime()
        return dt.toLocalDate().toEpochDay() + dt.toLocalTime().toSecondOfDay() / 86_400.0
    }

    /** Mediciones → puntos en orden cronológico (valor principal, o el segundo valor si [second]). */
    fun points(items: List<Measurement>, second: Boolean = false): List<TPoint> =
        items.sortedBy { it.timestamp }.mapNotNull { m ->
            val y = if (second) m.value2 else m.value
            y?.let { TPoint(dayX(m.timestamp), it) }
        }

    /** Regresión lineal por mínimos cuadrados. null si hay menos de 2 puntos o todos en el mismo instante. */
    fun regression(points: List<TPoint>): Regression? {
        if (points.size < 2) return null
        val mx = points.sumOf { it.x } / points.size
        val my = points.sumOf { it.y } / points.size
        var sxx = 0.0
        var sxy = 0.0
        for (p in points) {
            sxx += (p.x - mx) * (p.x - mx)
            sxy += (p.x - mx) * (p.y - my)
        }
        if (sxx < 1e-9) return null
        val slope = sxy / sxx
        return Regression(slope, my - slope * mx)
    }

    /** Media móvil: para cada punto, promedio de los puntos de los [windowDays] días anteriores (incluido él). */
    fun movingAverage(points: List<TPoint>, windowDays: Double = 7.0): List<TPoint> {
        val sorted = points.sortedBy { it.x }
        val out = ArrayList<TPoint>(sorted.size)
        var start = 0
        var sum = 0.0
        for (i in sorted.indices) {
            sum += sorted[i].y
            while (sorted[start].x <= sorted[i].x - windowDays) {
                sum -= sorted[start].y
                start++
            }
            out += TPoint(sorted[i].x, sum / (i - start + 1))
        }
        return out
    }

    /**
     * Cambio del último valor respecto a la medición más cercana a "hace [days] días"
     * (contados desde la última medición). null si no hay una referencia razonablemente cercana.
     */
    fun changeOver(points: List<TPoint>, days: Int): Double? {
        val sorted = points.sortedBy { it.x }
        if (sorted.size < 2) return null
        val last = sorted.last()
        val target = last.x - days
        val ref = sorted.dropLast(1).minByOrNull { abs(it.x - target) } ?: return null
        val tolerance = max(3.0, days * 0.5)
        if (abs(ref.x - target) > tolerance) return null
        return last.y - ref.y
    }

    /** Mín / máx / promedio / primero / último de los valores (en orden cronológico). */
    fun summary(values: List<Double>): RangeSummary? {
        if (values.isEmpty()) return null
        return RangeSummary(values.size, values.min(), values.max(), values.average(), values.first(), values.last())
    }

    // ---------- IMC ----------

    fun bmi(weightKg: Double, heightCm: Double?): Double? {
        if (heightCm == null || heightCm < 50 || weightKg <= 0) return null
        val m = heightCm / 100.0
        return weightKg / (m * m)
    }

    /** Categorías de la OMS para personas adultas. */
    fun bmiCategory(bmi: Double): Assessment = when {
        bmi < 18.5 -> Assessment("Bajo peso", Level.WARN, "IMC menor a 18.5.")
        bmi < 25.0 -> Assessment("Peso saludable", Level.GOOD, "IMC entre 18.5 y 24.9.")
        bmi < 30.0 -> Assessment("Sobrepeso", Level.WARN, "IMC entre 25 y 29.9.")
        bmi < 35.0 -> Assessment("Obesidad grado I", Level.BAD, "IMC entre 30 y 34.9.")
        bmi < 40.0 -> Assessment("Obesidad grado II", Level.BAD, "IMC entre 35 y 39.9.")
        else -> Assessment("Obesidad grado III", Level.BAD, "IMC de 40 o más.")
    }

    /** Rango de peso con IMC 18.5–24.9 para una estatura. */
    fun healthyWeightRange(heightCm: Double): Pair<Double, Double> {
        val m2 = (heightCm / 100.0) * (heightCm / 100.0)
        return 18.5 * m2 to 24.9 * m2
    }

    // ---------- Clasificaciones orientativas ----------

    fun bloodPressure(sys: Double, dia: Double): Assessment = when {
        sys > 180 || dia > 120 -> Assessment(
            "Revisar con urgencia", Level.BAD,
            "Más de 180 de sistólica o de 120 de diastólica. Repite la medición tras unos minutos de reposo; si sigue así o tienes molestias, busca atención médica de inmediato.",
        )
        sys >= 140 || dia >= 90 -> Assessment("Alta (grado 2)", Level.BAD, "140 o más de sistólica, o 90 o más de diastólica. Coméntalo con tu médico.")
        sys >= 130 || dia >= 80 -> Assessment("Alta (grado 1)", Level.WARN, "Sistólica de 130 a 139 o diastólica de 80 a 89.")
        sys >= 120 -> Assessment("Elevada", Level.WARN, "Sistólica de 120 a 129 con diastólica menor a 80.")
        sys < 90 || dia < 60 -> Assessment("Baja", Level.WARN, "Menos de 90 de sistólica o de 60 de diastólica. Si sientes mareo o debilidad, consúltalo.")
        else -> Assessment("Normal", Level.GOOD, "Menos de 120 de sistólica y menos de 80 de diastólica.")
    }

    private fun isFasting(tag: String): Boolean = tag.contains("ayunas", true) || tag.contains("antes de comer", true)
    private fun isAfterMeal(tag: String): Boolean = tag.contains("después de comer", true)

    fun glucose(value: Double, tag: String): Assessment = when {
        value < 54 -> Assessment("Muy baja", Level.BAD, "Menos de 54 mg/dL. Toma algo con azúcar y busca atención si no mejora.")
        value < 70 -> Assessment("Baja", Level.WARN, "Menos de 70 mg/dL.")
        isFasting(tag) -> when {
            value < 100 -> Assessment("Normal", Level.GOOD, "En ayunas: de 70 a 99 mg/dL.")
            value < 126 -> Assessment("Elevada", Level.WARN, "En ayunas: de 100 a 125 mg/dL.")
            else -> Assessment("Alta", Level.BAD, "En ayunas: 126 mg/dL o más. Coméntalo con tu médico.")
        }
        isAfterMeal(tag) -> when {
            value < 140 -> Assessment("Normal", Level.GOOD, "2 h después de comer: menos de 140 mg/dL.")
            value < 200 -> Assessment("Elevada", Level.WARN, "2 h después de comer: de 140 a 199 mg/dL.")
            else -> Assessment("Alta", Level.BAD, "2 h después de comer: 200 mg/dL o más. Coméntalo con tu médico.")
        }
        else -> when {
            value < 140 -> Assessment("En rango habitual", Level.GOOD, "Entre 70 y 139 mg/dL. Indica el contexto (en ayunas, después de comer) para una referencia más precisa.")
            value < 200 -> Assessment("Elevada", Level.WARN, "De 140 a 199 mg/dL sin contexto indicado.")
            else -> Assessment("Alta", Level.BAD, "200 mg/dL o más. Coméntalo con tu médico.")
        }
    }

    fun spo2(value: Double): Assessment = when {
        value >= 95 -> Assessment("Normal", Level.GOOD, "95 % o más.")
        value > 90 -> Assessment("Algo baja", Level.WARN, "Entre 91 y 94 %. Repite la medición en reposo y con la mano tibia.")
        else -> Assessment("Baja", Level.BAD, "90 % o menos. Si se repite o te falta el aire, busca atención médica.")
    }

    fun temperature(value: Double): Assessment = when {
        value < 35.0 -> Assessment("Baja", Level.WARN, "Menos de 35 °C.")
        value < 37.5 -> Assessment("Normal", Level.GOOD, "Entre 35 y 37.4 °C.")
        value < 38.0 -> Assessment("Febrícula", Level.WARN, "Entre 37.5 y 37.9 °C.")
        value < 39.5 -> Assessment("Fiebre", Level.BAD, "38 °C o más.")
        else -> Assessment("Fiebre alta", Level.BAD, "39.5 °C o más. Busca atención médica.")
    }

    fun heartRate(value: Double, tag: String): Assessment {
        if (tag.contains("ejercicio", true)) {
            return Assessment("Tras ejercicio", Level.NEUTRAL, "Después del ejercicio es normal que el pulso esté más alto.")
        }
        return when {
            value < 50 -> Assessment("Baja", Level.WARN, "Menos de 50 lpm en reposo. Puede ser habitual en personas muy entrenadas.")
            value < 60 -> Assessment("Algo baja", Level.GOOD, "Entre 50 y 59 lpm en reposo; frecuente en personas activas.")
            value <= 100 -> Assessment("Normal", Level.GOOD, "Entre 60 y 100 lpm en reposo.")
            else -> Assessment("Elevada", Level.WARN, "Más de 100 lpm en reposo.")
        }
    }

    /** Relación cintura/estatura (se recomienda menor a 0.5). */
    fun waistToHeight(waistCm: Double, heightCm: Double): Assessment {
        val r = waistCm / heightCm
        val detail = "Relación cintura/estatura: ${Fmt.num(r, 2)} (se recomienda menor a 0.5)."
        return when {
            r < 0.5 -> Assessment("Recomendable", Level.GOOD, detail)
            r < 0.6 -> Assessment("Elevada", Level.WARN, detail)
            else -> Assessment("Muy elevada", Level.BAD, detail)
        }
    }

    /** Clasificación orientativa de una medición, o null si ese tipo no tiene referencia. */
    fun assess(type: MeasureType, m: Measurement, heightCm: Double? = null): Assessment? = when (type) {
        MeasureType.WEIGHT -> bmi(m.value, heightCm)?.let { bmiCategory(it) }
        MeasureType.BLOOD_PRESSURE -> m.value2?.let { bloodPressure(m.value, it) }
        MeasureType.HEART_RATE -> heartRate(m.value, m.tag)
        MeasureType.GLUCOSE -> glucose(m.value, m.tag)
        MeasureType.TEMPERATURE -> temperature(m.value)
        MeasureType.SPO2 -> spo2(m.value)
        MeasureType.WAIST -> if (heightCm != null && heightCm >= 50) waistToHeight(m.value, heightCm) else null
        MeasureType.HIP, MeasureType.BODY_FAT -> null
    }

    // ---------- Meta de peso ----------

    /**
     * Fecha estimada para llegar a [goal] con la tendencia reciente (últimos [windowDays] días).
     * null si la tendencia no se acerca a la meta, es casi plana o tardaría más de 3 años.
     */
    fun goalProjection(points: List<TPoint>, goal: Double, today: Long, windowDays: Double = 60.0): GoalProjection? {
        val sorted = points.sortedBy { it.x }
        val last = sorted.lastOrNull() ?: return null
        val diff = goal - last.y
        if (abs(diff) < 0.05) return GoalProjection(today, 0)
        val recent = sorted.filter { it.x >= last.x - windowDays }.let { if (it.size >= 3) it else sorted.takeLast(5) }
        val reg = regression(recent) ?: return null
        if (abs(reg.perWeek) < 0.02 || sign(diff) != sign(reg.slopePerDay)) return null
        val days = ceil(diff / reg.slopePerDay - 1e-9)
        if (days > 365 * 3) return null
        val day = (last.x + days).toLong()
        val left = (day - today).toInt()
        if (left > 365 * 3) return null
        return GoalProjection(maxOf(day, today), left.coerceAtLeast(0))
    }

    /** ¿Se alcanzó la meta? Se decide según la dirección desde el primer peso registrado. */
    fun goalReached(first: Double, current: Double, goal: Double): Boolean =
        if (first >= goal) current <= goal + 0.05 else current >= goal - 0.05

    /** Avance 0..1 desde el primer peso hacia la meta. */
    fun goalProgress(first: Double, current: Double, goal: Double): Float {
        val total = goal - first
        if (abs(total) < 0.05) return if (abs(current - goal) < 0.05) 1f else 0f
        return ((current - first) / total).toFloat().coerceIn(0f, 1f)
    }

    /** Resumen de peso a partir de las mediciones de tipo "weight" (en cualquier orden). null si no hay. */
    fun weightSummary(weights: List<Measurement>, heightCm: Double?, goal: Double?, sparkSize: Int = 20): WeightSummary? {
        val sorted = weights.sortedBy { it.timestamp }
        val last = sorted.lastOrNull() ?: return null
        val pts = points(sorted)
        val b = bmi(last.value, heightCm)
        val first = sorted.first().value
        return WeightSummary(
            latest = last.value,
            latestTimestamp = last.timestamp,
            change7 = changeOver(pts, 7),
            change30 = changeOver(pts, 30),
            bmi = b,
            bmiAssessment = b?.let { bmiCategory(it) },
            goal = goal,
            toGoal = goal?.let { it - last.value },
            goalReached = goal != null && goalReached(first, last.value, goal),
            goalProgress = goal?.let { goalProgress(first, last.value, it) },
            spark = sorted.takeLast(sparkSize).map { it.value },
        )
    }

    // ---------- Validación de captura ----------

    /** Devuelve un mensaje de error para mostrar, o null si la captura es válida. */
    fun validate(type: MeasureType, value: Double?, value2: Double?, timestamp: Long, now: Long): String? {
        if (value == null) return "Escribe un valor para ${type.valueLabel.lowercase()}."
        if (value < type.min || value > type.max) {
            return "${type.valueLabel}: el valor debe estar entre ${Fmt.num(type.min, 1)} y ${Fmt.num(type.max, 1)} ${type.unit}."
        }
        if (type.secondLabel != null) {
            if (value2 == null) return "Escribe también la ${type.secondLabel.lowercase()}."
            if (value2 < 20 || value2 > 200) return "${type.secondLabel}: el valor debe estar entre 20 y 200 ${type.unit}."
            if (value2 >= value) return "La diastólica debe ser menor que la sistólica."
        }
        if (timestamp > now + 5 * 60_000L) return "La fecha y hora no pueden ser futuras."
        return null
    }
}
