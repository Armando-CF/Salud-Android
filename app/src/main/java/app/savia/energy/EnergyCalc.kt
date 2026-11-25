package app.savia.energy

import kotlin.math.roundToInt

/** Nivel de actividad física y su factor para estimar el gasto total del día. */
enum class ActivityLevel(val key: String, val label: String, val detail: String, val factor: Double) {
    SEDENTARY("sedentary", "Sedentario", "Poco o nada de ejercicio", 1.2),
    LIGHT("light", "Ligero", "Ejercicio 1 a 3 días por semana", 1.375),
    MODERATE("moderate", "Moderado", "Ejercicio 3 a 5 días por semana", 1.55),
    VERY("very", "Muy activo", "Ejercicio intenso casi todos los días", 1.725);

    companion object {
        fun of(key: String): ActivityLevel? = entries.firstOrNull { it.key == key }
    }
}

/** Un tipo de ejercicio con su intensidad en METs (1 MET = estar en reposo). */
data class Activity(val name: String, val met: Double)

/** Las tres partes del gasto diario, en kcal. */
data class EnergyBreakdown(
    /** Metabolismo basal: lo que el cuerpo gasta por solo existir. */
    val basal: Double,
    /** Efecto térmico: digerir la comida (≈ 10 % del total). */
    val digestion: Double,
    /** Moverte y hacer ejercicio. */
    val movement: Double,
) {
    val total: Double get() = basal + digestion + movement
    fun share(part: Double): Int = if (total <= 0) 0 else (part / total * 100).roundToInt()
}

/**
 * Estimación del gasto de energía. Todo es APROXIMADO (fórmulas de población): sirve de orientación,
 * no sustituye una calorimetría ni la valoración de un profesional.
 */
object EnergyCalc {
    /** Edad mínima para la que está pensada la fórmula. */
    const val MIN_AGE = 18

    /** kcal aproximadas por kilo de peso corporal ganado o perdido. */
    const val KCAL_PER_KG = 7700.0

    /**
     * Metabolismo basal con la fórmula de Mifflin-St Jeor (kcal/día).
     * sex: "M" hombre (+5), "F" mujer (−161); otro valor → punto medio entre ambas (−78).
     */
    fun bmr(weightKg: Double, heightCm: Double, ageYears: Int, sex: String): Double {
        val base = 10 * weightKg + 6.25 * heightCm - 5 * ageYears
        return base + when (sex) {
            "M" -> 5.0
            "F" -> -161.0
            else -> -78.0
        }
    }

    /** Gasto total aproximado del día = metabolismo basal × factor de actividad. */
    fun tdee(bmr: Double, level: ActivityLevel): Double = bmr * level.factor

    /** Reparte el total en basal, digestión (10 % del total) y movimiento (el resto). */
    fun breakdown(bmr: Double, level: ActivityLevel): EnergyBreakdown {
        val total = tdee(bmr, level)
        val digestion = total * 0.10
        return EnergyBreakdown(basal = bmr, digestion = digestion, movement = (total - bmr - digestion).coerceAtLeast(0.0))
    }

    /** Calorías de un ejercicio: MET × peso (kg) × horas. */
    fun exerciseKcal(met: Double, weightKg: Double, minutes: Int): Double = met * weightKg * minutes / 60.0

    /**
     * Nivel sugerido a partir de los días por semana con ejercicio registrado
     * (menos de 1 → sedentario, hasta 3 → ligero, hasta 5 → moderado, más → muy activo).
     */
    fun suggestLevel(exerciseDaysPerWeek: Double): ActivityLevel = when {
        exerciseDaysPerWeek < 1.0 -> ActivityLevel.SEDENTARY
        exerciseDaysPerWeek < 3.0 -> ActivityLevel.LIGHT
        exerciseDaysPerWeek <= 5.0 -> ActivityLevel.MODERATE
        else -> ActivityLevel.VERY
    }

    /**
     * Días por semana con ejercicio (10 min o más) en los últimos [windowDays] días, según el registro
     * de bienestar. Devuelve null si hay menos de [minLogs] días registrados (no alcanza para sugerir).
     * @param logs pares (epochDay, minutos de ejercicio o null).
     */
    fun exerciseDaysPerWeek(logs: List<Pair<Long, Int?>>, today: Long, windowDays: Int = 28, minLogs: Int = 7): Double? {
        val recent = logs.filter { it.first in (today - windowDays + 1)..today }
        if (recent.size < minLogs) return null
        val active = recent.count { (it.second ?: 0) >= 10 }
        // Se promedia sobre los días realmente registrados, para no castigar los días sin anotar.
        return active.toDouble() / recent.size * 7.0
    }

    /**
     * Pendiente de una serie (día, kg) en kg por semana, por mínimos cuadrados.
     * Devuelve null si hay menos de [minPoints] puntos o abarcan menos de [minSpanDays] días.
     */
    fun weightSlopePerWeek(points: List<Pair<Double, Double>>, minPoints: Int = 4, minSpanDays: Double = 14.0): Double? {
        if (points.size < minPoints) return null
        val xs = points.map { it.first }
        if (xs.max() - xs.min() < minSpanDays) return null
        val mx = xs.average()
        val my = points.map { it.second }.average()
        var num = 0.0
        var den = 0.0
        for ((x, y) in points) { num += (x - mx) * (y - my); den += (x - mx) * (x - mx) }
        if (den == 0.0) return null
        return num / den * 7.0
    }

    /**
     * Balance diario aproximado (kcal) que explica una tendencia de peso: negativo = estás comiendo menos
     * de lo que gastas; positivo = más. Si el peso se mantiene, lo que comes es lo que gastas.
     */
    fun dailyBalanceFromTrend(slopeKgPerWeek: Double): Double = slopeKgPerWeek * KCAL_PER_KG / 7.0

    /** Redondea a decenas: son estimaciones, no tiene sentido mostrar unidades. */
    fun round10(kcal: Double): Int = (kcal / 10.0).roundToInt() * 10

    /** Actividades comunes con sus METs aproximados. */
    val activities: List<Activity> = listOf(
        Activity("Caminar a paso normal", 3.5),
        Activity("Caminar rápido", 4.3),
        Activity("Trotar suave", 7.0),
        Activity("Correr a 8 km/h", 8.0),
        Activity("Correr a 10 km/h", 10.0),
        Activity("Bicicleta, paseo", 4.0),
        Activity("Bicicleta, ritmo moderado", 6.8),
        Activity("Bicicleta fija intensa", 8.8),
        Activity("Pesas, esfuerzo ligero", 3.5),
        Activity("Pesas, esfuerzo intenso", 6.0),
        Activity("Natación suave", 6.0),
        Activity("Yoga o estiramientos", 2.5),
        Activity("Baile", 5.0),
        Activity("Fútbol", 7.0),
        Activity("Básquetbol", 6.5),
        Activity("Subir escaleras", 8.0),
        Activity("Quehacer de la casa", 3.3),
        Activity("Jardinería", 3.8),
        Activity("Jugar con niñas y niños", 4.0),
    )
}
