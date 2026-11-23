package app.savia.ui.measure

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.savia.Savia
import app.savia.data.MeasureType
import app.savia.data.Measurement
import app.savia.data.Profile
import app.savia.util.Fmt
import app.savia.util.today
import java.time.LocalDate
import kotlin.math.abs

/**
 * A quién corresponden las mediciones. Las referencias del módulo (IMC, presión, pulso…) están pensadas
 * para personas adultas: con mascotas y con menores de edad no se clasifica con esos rangos.
 */
data class MeasureWho(
    val isPet: Boolean = false,
    /** Clave de PetSpecies ("perro", "gato"…); vacío en personas. */
    val species: String = "",
    /** Persona menor de [ADULT_AGE] años (según su fecha de nacimiento). */
    val isMinor: Boolean = false,
    /** Nombre para textos ("el peso de Firulais"). */
    val name: String = "",
) {
    /** Persona adulta (o sin fecha de nacimiento): aplican las referencias habituales. */
    val isAdult: Boolean get() = !isPet && !isMinor

    companion object {
        const val ADULT_AGE = 18

        /** ¿Menor de edad en la fecha [today]? Sin fecha de nacimiento se asume persona adulta. */
        fun isMinor(birthEpochDay: Long?, today: Long): Boolean {
            if (birthEpochDay == null || birthEpochDay > today) return false
            return LocalDate.ofEpochDay(birthEpochDay).plusYears(ADULT_AGE.toLong()).toEpochDay() > today
        }

        fun of(isPet: Boolean, species: String, birthEpochDay: Long?, today: Long, name: String = ""): MeasureWho =
            MeasureWho(isPet, if (isPet) species else "", !isPet && isMinor(birthEpochDay, today), name)
    }
}

/** Contexto del perfil activo; usa la fecha de nacimiento de la ficha en cuanto se carga. */
@Composable
internal fun rememberMeasureWho(profile: Profile?): MeasureWho {
    val info = Savia.active.value
    val birth = profile?.birthEpochDay ?: info.birthEpochDay
    val t = today()
    return remember(info, birth, t) { MeasureWho.of(info.isPet, info.species, birth, t, info.displayName) }
}

/** Referencias y formatos que dependen de a quién se mide (lógica pura). */
object MeasureRefs {
    const val MINOR_BMI_NOTE =
        "En niñas, niños y adolescentes el IMC se interpreta con tablas por edad; consúltalo con su pediatra."
    const val MINOR_VITALS_NOTE =
        "En niñas, niños y adolescentes los valores normales cambian con la edad, así que aquí no se clasifican. " +
            "Su pediatra puede decirte qué rango es el adecuado."
    const val PET_GENERAL_NOTE =
        "Los valores normales cambian según la especie, la raza, el tamaño y la edad. " +
            "Tu veterinaria o veterinario es quien puede interpretarlos."

    private val MINOR_UNCLASSIFIED = setOf(
        MeasureType.WEIGHT, MeasureType.BLOOD_PRESSURE, MeasureType.HEART_RATE, MeasureType.WAIST,
    )

    /** Tipos de medición que se muestran para el perfil. */
    fun types(isPet: Boolean): List<MeasureType> = MeasureType.available(isPet)

    /**
     * Clasificación orientativa según a quién se mide: nada para mascotas; para menores no se usan
     * los rangos de adultos de peso (IMC), presión, pulso ni cintura.
     */
    fun assess(who: MeasureWho, type: MeasureType, m: Measurement, heightCm: Double?): Assessment? = when {
        who.isPet -> null
        who.isMinor && type in MINOR_UNCLASSIFIED -> null
        else -> MeasureStats.assess(type, m, heightCm)
    }

    /** ¿Se calcula el IMC? (no para mascotas) */
    fun showBmi(who: MeasureWho): Boolean = !who.isPet

    /** ¿Se muestra la categoría de IMC (solo personas adultas)? */
    fun showBmiCategory(who: MeasureWho): Boolean = who.isAdult

    /**
     * Nota orientativa propia del perfil para un tipo de medición, o null si no hay nada que decir.
     * Mascotas: solo perro y gato tienen referencia; menores: aviso de que no se clasifica.
     */
    fun note(who: MeasureWho, type: MeasureType): String? = when {
        who.isPet -> when (who.species) {
            "perro" -> when (type) {
                MeasureType.TEMPERATURE ->
                    "En perros la temperatura normal (rectal) ronda de 38 a 39.2 °C. Más de 39.5 °C o menos de 37.5 °C " +
                        "es motivo para llamar a tu veterinario."
                MeasureType.HEART_RATE ->
                    "En perros en reposo lo habitual es de 60 a 140 latidos por minuto: más lento en razas grandes " +
                        "y más rápido en razas pequeñas y cachorros."
                MeasureType.WEIGHT ->
                    "El peso ideal depende de la raza y la complexión. Tu veterinario puede decirte el rango adecuado " +
                        "y cómo está su condición corporal."
                else -> null
            }
            "gato" -> when (type) {
                MeasureType.TEMPERATURE ->
                    "En gatos la temperatura normal (rectal) ronda de 38 a 39.2 °C. Más de 39.5 °C o menos de 37.5 °C " +
                        "es motivo para llamar a tu veterinario."
                MeasureType.HEART_RATE ->
                    "En gatos en reposo lo habitual es de 140 a 220 latidos por minuto; suele subir con el estrés de la consulta."
                MeasureType.WEIGHT ->
                    "El peso ideal depende de la complexión. Tu veterinario puede decirte el rango adecuado " +
                        "y cómo está su condición corporal."
                else -> null
            }
            else -> null
        }
        who.isMinor -> when (type) {
            MeasureType.WEIGHT -> MINOR_BMI_NOTE
            MeasureType.BLOOD_PRESSURE, MeasureType.HEART_RATE, MeasureType.WAIST -> MINOR_VITALS_NOTE
            MeasureType.HEIGHT ->
                "Medir la estatura cada cierto tiempo y siempre igual (sin zapatos, de pie y con la espalda recta) " +
                    "ayuda a seguir el crecimiento. Su pediatra la compara con tablas por edad."
            else -> null
        }
        else -> null
    }

    /** Rango de referencia para sombrear en la gráfica, o null si no aplica a este perfil. */
    fun band(who: MeasureWho, type: MeasureType, inRange: List<Measurement>): Pair<Double, Double>? {
        if (who.isPet) {
            return when {
                type == MeasureType.TEMPERATURE && (who.species == "perro" || who.species == "gato") -> 38.0 to 39.2
                type == MeasureType.HEART_RATE && who.species == "perro" -> 60.0 to 140.0
                type == MeasureType.HEART_RATE && who.species == "gato" -> 140.0 to 220.0
                else -> null
            }
        }
        return when (type) {
            MeasureType.GLUCOSE -> {
                val fasting = inRange.count { it.tag.contains("ayunas", true) || it.tag.contains("antes de comer", true) }
                if (fasting * 2 >= inRange.size) 70.0 to 99.0 else 70.0 to 139.0
            }
            MeasureType.HEART_RATE -> if (who.isMinor) null else 60.0 to 100.0
            MeasureType.SPO2 -> 95.0 to 100.0
            MeasureType.TEMPERATURE -> 36.0 to 37.4
            else -> null
        }
    }

    // ---------- Formato de peso ----------

    /** ¿Conviene mostrar este peso en gramos? (mascotas de menos de 1 kg) */
    fun inGrams(kg: Double, isPet: Boolean): Boolean = isPet && abs(kg) < 0.9995

    /**
     * Número y unidad por separado: personas ("72.5", "kg"); mascotas con 2 decimales ("12.35", "kg")
     * o en gramos bajo 1 kg ("350", "g"). Siempre se guarda en kg; esto solo es presentación.
     */
    fun weightParts(kg: Double, isPet: Boolean): Pair<String, String> = when {
        !isPet -> Fmt.num(kg, 1) to "kg"
        inGrams(kg, true) -> Fmt.num(kg * 1000, 0) to "g"
        else -> Fmt.num(kg, 2) to "kg"
    }

    /** "72.5 kg", "12.35 kg" o "350 g". */
    fun weightText(kg: Double, isPet: Boolean): String = weightParts(kg, isPet).let { "${it.first} ${it.second}" }

    /** Cambio con signo: "+0.4 kg", "−25 g", "0 kg". */
    fun weightDelta(deltaKg: Double, isPet: Boolean): String = when {
        !isPet -> "${Fmt.signed(deltaKg, 1)} kg"
        inGrams(deltaKg, true) -> "${Fmt.signed(deltaKg * 1000, 0)} g"
        else -> "${Fmt.signed(deltaKg, 2)} kg"
    }

    /** Decimales al mostrar un valor para editarlo (el peso de mascotas necesita llegar a gramos). */
    fun inputDecimals(type: MeasureType, isPet: Boolean): Int = if (isPet && type == MeasureType.WEIGHT) 3 else type.decimals

    /** Mínimo de captura: el peso de una persona no baja de 0.5 kg; el de una mascota puede ser de gramos. */
    fun minValue(type: MeasureType, isPet: Boolean): Double = if (type == MeasureType.WEIGHT && !isPet) 0.5 else type.min
}
