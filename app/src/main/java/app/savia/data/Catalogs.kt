package app.savia.data

/** Tipos de medición disponibles. La clave [key] es lo que se guarda en la base. */
enum class MeasureType(
    val key: String,
    val label: String,
    val unit: String,
    val decimals: Int,
    /** Etiqueta del valor principal en el formulario. */
    val valueLabel: String,
    /** Etiqueta del segundo valor, si lo hay (presión diastólica). */
    val secondLabel: String? = null,
    /** Límites razonables para validar la captura. */
    val min: Double,
    val max: Double,
    /** Contextos sugeridos. */
    val tags: List<String> = emptyList(),
    /** ¿Aplica también a mascotas? */
    val forPets: Boolean = false,
) {
    WEIGHT("weight", "Peso", "kg", 1, "Peso", null, 0.01, 400.0, forPets = true),
    HEIGHT("height", "Estatura", "cm", 1, "Estatura", null, 20.0, 250.0),
    BLOOD_PRESSURE("bp", "Presión arterial", "mmHg", 0, "Sistólica (alta)", "Diastólica (baja)", 40.0, 300.0,
        listOf("En reposo", "Por la mañana", "Por la noche", "Después de ejercicio")),
    HEART_RATE("hr", "Frecuencia cardiaca", "lpm", 0, "Pulsaciones", null, 20.0, 400.0,
        listOf("En reposo", "Después de ejercicio"), forPets = true),
    GLUCOSE("glucose", "Glucosa", "mg/dL", 0, "Glucosa", null, 20.0, 700.0,
        listOf("En ayunas", "Antes de comer", "2 h después de comer", "Antes de dormir")),
    TEMPERATURE("temp", "Temperatura", "°C", 1, "Temperatura", null, 30.0, 45.0, forPets = true),
    SPO2("spo2", "Oxigenación", "%", 0, "SpO₂", null, 50.0, 100.0),
    WAIST("waist", "Cintura", "cm", 1, "Cintura", null, 20.0, 300.0),
    HIP("hip", "Cadera", "cm", 1, "Cadera", null, 20.0, 300.0),
    BODY_FAT("fat", "Grasa corporal", "%", 1, "Grasa corporal", null, 1.0, 80.0);

    companion object {
        fun of(key: String): MeasureType? = entries.firstOrNull { it.key == key }

        /** Tipos que aplican al perfil: todos para personas; solo los marcados [forPets] para mascotas. */
        fun available(isPet: Boolean): List<MeasureType> = if (isPet) entries.filter { it.forPets } else entries.toList()
    }
}

/** Categorías de estudios / chequeos. */
object StudyCategories {
    /** clave → etiqueta */
    val all: List<Pair<String, String>> = listOf(
        "lab" to "Laboratorio",
        "imagen" to "Imagen (rayos X, ultrasonido…)",
        "chequeo" to "Chequeo general",
        "cardio" to "Corazón",
        "gine" to "Ginecológico",
        "uro" to "Urológico",
        "dental" to "Dental",
        "vista" to "Vista",
        "oido" to "Oído",
        "piel" to "Piel",
        "vacuna" to "Vacuna",
        "otro" to "Otro",
    )

    fun label(key: String): String = all.firstOrNull { it.first == key }?.second ?: "Otro"

    /** Etiqueta corta para chips. */
    fun short(key: String): String = label(key).substringBefore(" (")

    val statuses: List<Pair<String, String>> = listOf(
        "normal" to "Todo normal",
        "seguimiento" to "Requiere seguimiento",
        "pendiente" to "Resultados pendientes",
    )

    fun statusLabel(key: String): String = statuses.firstOrNull { it.first == key }?.second ?: ""
}

/** Catálogos del módulo de ciclo menstrual (clave → etiqueta). */
object CycleCatalog {
    val flows: List<Pair<Int, String>> = listOf(
        0 to "Sin sangrado", 1 to "Manchado", 2 to "Ligero", 3 to "Moderado", 4 to "Abundante",
    )

    fun flowLabel(flow: Int): String = flows.firstOrNull { it.first == flow }?.second ?: ""

    val symptoms: List<Pair<String, String>> = listOf(
        "colicos" to "Cólicos",
        "dolor_cabeza" to "Dolor de cabeza",
        "migrana" to "Migraña",
        "dolor_espalda" to "Dolor de espalda baja",
        "senos" to "Sensibilidad en senos",
        "hinchazon" to "Hinchazón",
        "acne" to "Acné",
        "fatiga" to "Fatiga",
        "nauseas" to "Náuseas",
        "antojos" to "Antojos",
        "insomnio" to "Insomnio",
        "mareo" to "Mareo",
        "diarrea" to "Diarrea",
        "estrenimiento" to "Estreñimiento",
        "dolor_ovulacion" to "Dolor de ovulación",
        "dolor_pelvico" to "Dolor pélvico",
        "bochornos" to "Bochornos",
        "resequedad" to "Resequedad vaginal",
        "comezon" to "Comezón / irritación",
        "flujo_inusual" to "Flujo inusual",
        "libido_alta" to "Libido alta",
        "libido_baja" to "Libido baja",
    )

    val moods: List<Pair<String, String>> = listOf(
        "tranquila" to "Tranquilidad",
        "feliz" to "Alegría",
        "energia" to "Con energía",
        "sensible" to "Sensibilidad",
        "irritable" to "Irritabilidad",
        "triste" to "Tristeza",
        "ansiosa" to "Ansiedad",
        "cambios" to "Cambios de humor",
        "estres" to "Estrés",
        "apatia" to "Apatía",
    )

    val mucus: List<Pair<String, String>> = listOf(
        "seco" to "Seco",
        "pegajoso" to "Pegajoso",
        "cremoso" to "Cremoso",
        "acuoso" to "Acuoso",
        "clara" to "Clara de huevo",
    )

    val sex: List<Pair<Int, String>> = listOf(1 to "Con protección", 2 to "Sin protección")
    val tests: List<Pair<Int, String>> = listOf(1 to "Negativa", 2 to "Positiva")

    fun symptomLabel(key: String): String = symptoms.firstOrNull { it.first == key }?.second ?: key
    fun moodLabel(key: String): String = moods.firstOrNull { it.first == key }?.second ?: key
    fun mucusLabel(key: String): String = mucus.firstOrNull { it.first == key }?.second ?: key
    fun sexLabel(v: Int): String = sex.firstOrNull { it.first == v }?.second ?: ""
    fun testLabel(v: Int): String = tests.firstOrNull { it.first == v }?.second ?: ""

    /** "a,b,c" → [a, b, c] */
    fun split(csv: String): List<String> = csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    fun join(keys: Collection<String>): String = keys.joinToString(",")
}

/** Síntomas frecuentes para sugerir al registrar (agrupados). */
object SymptomCatalog {
    val groups: List<Pair<String, List<String>>> = listOf(
        "General" to listOf("Fiebre", "Escalofríos", "Fatiga", "Debilidad", "Mareo", "Malestar general", "Insomnio", "Sudoración"),
        "Cabeza" to listOf("Dolor de cabeza", "Migraña", "Dolor de oído", "Dolor de muela", "Visión borrosa", "Zumbido de oídos"),
        "Respiratorio" to listOf("Tos", "Dolor de garganta", "Congestión nasal", "Escurrimiento nasal", "Estornudos", "Falta de aire", "Flemas"),
        "Digestivo" to listOf("Dolor abdominal", "Náuseas", "Vómito", "Diarrea", "Estreñimiento", "Acidez / reflujo", "Gases / inflamación", "Falta de apetito"),
        "Músculos y huesos" to listOf("Dolor de espalda", "Dolor de cuello", "Dolor muscular", "Dolor articular", "Calambre", "Rigidez", "Hinchazón", "Golpe / contusión"),
        "Piel" to listOf("Sarpullido", "Comezón", "Ronchas", "Herida", "Moretón", "Quemadura"),
        "Corazón y pecho" to listOf("Dolor en el pecho", "Palpitaciones", "Presión en el pecho"),
        "Urinario" to listOf("Ardor al orinar", "Orinar con frecuencia", "Dolor pélvico"),
        "Ánimo" to listOf("Ansiedad", "Tristeza", "Estrés", "Irritabilidad"),
        "Alergia" to listOf("Reacción alérgica", "Ojos llorosos", "Hinchazón de labios o párpados"),
    )

    val all: List<String> = groups.flatMap { it.second }

    /** Posibles desencadenantes para etiquetar un síntoma. */
    val triggers: List<String> = listOf(
        "Estrés", "Comida", "Falta de sueño", "Ejercicio", "Clima", "Alcohol", "Medicamento",
        "Mala postura", "Pantallas", "Ayuno", "Periodo", "Alergia",
    )

    fun intensityLabel(i: Int): String = when {
        i <= 2 -> "Muy leve"
        i <= 4 -> "Leve"
        i <= 6 -> "Moderado"
        i <= 8 -> "Fuerte"
        else -> "Muy fuerte"
    }
}

object SexOptions {
    val all: List<Pair<String, String>> = listOf("F" to "Mujer", "M" to "Hombre", "X" to "Prefiero no decir")
    fun label(key: String): String = all.firstOrNull { it.first == key }?.second ?: ""
}

object SelfExamTypes {
    val all: List<Pair<String, String>> = listOf("mama" to "Mamaria", "testicular" to "Testicular", "piel" to "Piel y lunares")
    fun label(key: String): String = all.firstOrNull { it.first == key }?.second ?: key
}

/** Tipos de registro del módulo de vacunas (clave → etiqueta). */
object VaccinationTypes {
    val all: List<Pair<String, String>> = listOf(
        "vacuna" to "Vacuna",
        "desp_interna" to "Desparasitación interna",
        "desp_externa" to "Pulgas y garrapatas",
        "otro" to "Otro",
    )

    /** Para personas solo tienen sentido "vacuna" y "otro". */
    fun forProfile(isPet: Boolean): List<Pair<String, String>> = if (isPet) all else all.filter { it.first == "vacuna" || it.first == "otro" }

    fun label(key: String): String = all.firstOrNull { it.first == key }?.second ?: "Otro"
}
