package app.savia.ui.studies

import app.savia.data.Study
import app.savia.data.StudyResult
import app.savia.util.Fmt
import java.text.Normalizer

/** Posición de un valor respecto a su rango de referencia. */
enum class RangeFlag(val label: String) {
    BAJO("Bajo"),
    NORMAL("En rango"),
    ALTO("Alto"),
    SIN_RANGO("Sin rango"),
}

/**
 * Parámetro común de laboratorio con un rango de referencia típico para adultos.
 * Los rangos son ORIENTATIVOS: cada laboratorio usa los suyos y siempre mandan los del reporte.
 */
data class LabParam(
    val name: String,
    val unit: String,
    val refMin: Double? = null,
    val refMax: Double? = null,
    /** Rango específico para mujeres (si difiere). */
    val refMinF: Double? = null,
    val refMaxF: Double? = null,
    /** Rango específico para hombres (si difiere). */
    val refMinM: Double? = null,
    val refMaxM: Double? = null,
    /** Otros nombres con los que suele aparecer en reportes. */
    val aliases: List<String> = emptyList(),
) {
    /** Rango (mín, máx) para el sexo del perfil: "F", "M" u otro (rango general). */
    fun rangeFor(sex: String): Pair<Double?, Double?> = when (sex) {
        "F" -> (refMinF ?: refMin) to (refMaxF ?: refMax)
        "M" -> (refMinM ?: refMin) to (refMaxM ?: refMax)
        else -> refMin to refMax
    }
}

/** Grupo de parámetros que suelen pedirse juntos. */
data class LabGroup(val name: String, val params: List<String>)

object StudyCatalog {

    val params: List<LabParam> = listOf(
        // Química sanguínea
        LabParam("Glucosa", "mg/dL", 70.0, 99.0, aliases = listOf("Glucosa en ayunas", "Glucemia")),
        LabParam("Hemoglobina glucosilada (HbA1c)", "%", null, 5.6, aliases = listOf("HbA1c", "Hemoglobina glicada")),
        LabParam("Urea", "mg/dL", 15.0, 45.0),
        LabParam("Nitrógeno ureico (BUN)", "mg/dL", 7.0, 20.0, aliases = listOf("BUN")),
        LabParam("Creatinina", "mg/dL", 0.5, 1.3, refMinF = 0.5, refMaxF = 1.1, refMinM = 0.7, refMaxM = 1.3),
        LabParam("Ácido úrico", "mg/dL", 2.4, 7.0, refMinF = 2.4, refMaxF = 6.0, refMinM = 3.4, refMaxM = 7.0),
        // Lípidos
        LabParam("Colesterol total", "mg/dL", null, 200.0, aliases = listOf("Colesterol")),
        LabParam("Colesterol HDL", "mg/dL", 40.0, null, refMinF = 50.0, refMinM = 40.0, aliases = listOf("HDL")),
        LabParam("Colesterol LDL", "mg/dL", null, 130.0, aliases = listOf("LDL")),
        LabParam("Triglicéridos", "mg/dL", null, 150.0),
        // Biometría hemática
        LabParam("Hemoglobina", "g/dL", 12.0, 17.5, refMinF = 12.0, refMaxF = 16.0, refMinM = 13.5, refMaxM = 17.5),
        LabParam("Hematocrito", "%", 36.0, 53.0, refMinF = 36.0, refMaxF = 46.0, refMinM = 41.0, refMaxM = 53.0),
        LabParam("Eritrocitos", "millones/µL", 4.0, 5.9, refMinF = 4.0, refMaxF = 5.2, refMinM = 4.5, refMaxM = 5.9, aliases = listOf("Glóbulos rojos")),
        LabParam("Leucocitos", "miles/µL", 4.5, 11.0, aliases = listOf("Glóbulos blancos")),
        LabParam("Plaquetas", "miles/µL", 150.0, 450.0),
        // Hígado
        LabParam("TGO (AST)", "U/L", null, 40.0, aliases = listOf("AST", "TGO", "Aspartato aminotransferasa")),
        LabParam("TGP (ALT)", "U/L", null, 40.0, aliases = listOf("ALT", "TGP", "Alanina aminotransferasa")),
        LabParam("GGT", "U/L", null, 55.0, refMaxF = 38.0, refMaxM = 55.0, aliases = listOf("Gamma glutamil transferasa")),
        LabParam("Fosfatasa alcalina", "U/L", 44.0, 147.0),
        LabParam("Bilirrubina total", "mg/dL", 0.1, 1.2),
        LabParam("Albúmina", "g/dL", 3.5, 5.0),
        LabParam("Proteínas totales", "g/dL", 6.0, 8.3),
        // Tiroides
        LabParam("TSH", "µUI/mL", 0.4, 4.0, aliases = listOf("Hormona estimulante de tiroides", "Tirotropina")),
        LabParam("T4 libre", "ng/dL", 0.8, 1.8, aliases = listOf("Tiroxina libre")),
        LabParam("T3 total", "ng/dL", 80.0, 200.0, aliases = listOf("Triyodotironina")),
        // Vitaminas y minerales
        LabParam("Vitamina D (25-OH)", "ng/mL", 30.0, 100.0, aliases = listOf("Vitamina D", "25-hidroxivitamina D")),
        LabParam("Vitamina B12", "pg/mL", 200.0, 900.0),
        LabParam("Hierro", "µg/dL", 50.0, 175.0, refMinF = 50.0, refMaxF = 170.0, refMinM = 65.0, refMaxM = 175.0, aliases = listOf("Hierro sérico")),
        LabParam("Ferritina", "ng/mL", 15.0, 400.0, refMinF = 15.0, refMaxF = 150.0, refMinM = 30.0, refMaxM = 400.0),
        // Electrolitos
        LabParam("Sodio", "mEq/L", 135.0, 145.0),
        LabParam("Potasio", "mEq/L", 3.5, 5.0),
        LabParam("Calcio", "mg/dL", 8.5, 10.2),
        // Otros
        LabParam("Antígeno prostático (PSA)", "ng/mL", null, 4.0, aliases = listOf("PSA", "Antígeno prostático específico")),
        LabParam("Proteína C reactiva", "mg/L", null, 5.0, aliases = listOf("PCR")),
        LabParam("Velocidad de sedimentación (VSG)", "mm/h", null, 20.0, aliases = listOf("VSG")),
        LabParam("Densidad urinaria", "", 1.005, 1.030),
        LabParam("pH urinario", "", 4.5, 8.0),
    )

    val groups: List<LabGroup> = listOf(
        LabGroup("Química sanguínea", listOf("Glucosa", "Urea", "Creatinina", "Ácido úrico", "Colesterol total", "Triglicéridos")),
        LabGroup("Perfil de lípidos", listOf("Colesterol total", "Colesterol HDL", "Colesterol LDL", "Triglicéridos")),
        LabGroup("Biometría hemática", listOf("Hemoglobina", "Hematocrito", "Eritrocitos", "Leucocitos", "Plaquetas")),
        LabGroup("Función hepática", listOf("TGO (AST)", "TGP (ALT)", "GGT", "Fosfatasa alcalina", "Bilirrubina total", "Albúmina")),
        LabGroup("Perfil tiroideo", listOf("TSH", "T4 libre", "T3 total")),
        LabGroup("Electrolitos", listOf("Sodio", "Potasio", "Calcio")),
        LabGroup("Examen general de orina", listOf("Densidad urinaria", "pH urinario")),
    )

    /** Títulos sugeridos por clave de categoría (ver StudyCategories). */
    val titles: Map<String, List<String>> = mapOf(
        "lab" to listOf(
            "Química sanguínea", "Biometría hemática", "Examen general de orina", "Perfil de lípidos",
            "Perfil tiroideo", "Hemoglobina glucosilada", "Pruebas de función hepática", "Vitamina D", "Coprológico",
        ),
        "imagen" to listOf(
            "Radiografía de tórax", "Ultrasonido abdominal", "Ultrasonido pélvico", "Tomografía",
            "Resonancia magnética", "Densitometría ósea", "Mastografía", "Ultrasonido mamario",
        ),
        "chequeo" to listOf("Chequeo anual", "Revisión médica general", "Examen médico laboral", "Valoración nutricional"),
        "cardio" to listOf("Electrocardiograma", "Ecocardiograma", "Prueba de esfuerzo", "Holter", "Monitoreo de presión arterial"),
        "gine" to listOf("Papanicolaou", "Mastografía", "Ultrasonido mamario", "Ultrasonido pélvico", "Colposcopía", "Prueba de VPH", "Revisión ginecológica"),
        "uro" to listOf("Antígeno prostático", "Revisión urológica", "Ultrasonido prostático", "Ultrasonido renal", "Urocultivo"),
        "dental" to listOf("Limpieza dental", "Revisión dental", "Radiografía dental", "Resina / empaste", "Ortodoncia"),
        "vista" to listOf("Examen de la vista", "Graduación de lentes", "Fondo de ojo", "Presión intraocular"),
        "oido" to listOf("Audiometría", "Revisión de oídos", "Lavado de oídos"),
        "piel" to listOf("Revisión de lunares", "Consulta dermatológica", "Biopsia de piel"),
        "vacuna" to listOf("Influenza", "COVID-19", "Tétanos y difteria (Td)", "Hepatitis B", "VPH", "Neumococo", "Sarampión y rubéola (SR)", "Herpes zóster"),
        "otro" to listOf("Consulta de especialidad", "Estudio de sueño", "Espirometría", "Prueba de alergias"),
    )

    fun titlesFor(category: String): List<String> = titles[category] ?: emptyList()

    /** Busca un parámetro del catálogo por nombre o alias (sin distinguir mayúsculas ni acentos). */
    fun find(name: String): LabParam? {
        val n = normalizeName(name)
        if (n.isEmpty()) return null
        return params.firstOrNull { normalizeName(it.name) == n }
            ?: params.firstOrNull { p -> p.aliases.any { normalizeName(it) == n } }
    }

    /** Sugerencias de nombres de parámetro que contienen [query]; primero los que empiezan con él. */
    fun suggest(query: String, extraNames: List<String> = emptyList(), limit: Int = 5): List<String> {
        val q = normalizeName(query)
        if (q.isEmpty()) return emptyList()
        val fromCatalog = params.filter { p ->
            normalizeName(p.name).contains(q) || p.aliases.any { normalizeName(it).contains(q) }
        }.map { it.name }
        val fromExtra = extraNames.filter { normalizeName(it).contains(q) }
        return (fromCatalog + fromExtra)
            .distinctBy { normalizeName(it) }
            .filter { normalizeName(it) != q }
            .sortedBy { if (normalizeName(it).startsWith(q)) 0 else 1 }
            .take(limit)
    }
}

private val DIACRITICS = Regex("\\p{M}+")
private val SPACES = Regex("\\s+")

/** Minúsculas, sin acentos y con espacios colapsados: para comparar nombres de parámetros. */
fun normalizeName(s: String): String =
    Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD)
        .replace(DIACRITICS, "")
        .replace(SPACES, " ")

/** Clasifica [value] contra el rango [refMin]..[refMax] (cualquiera de los límites puede faltar). */
fun flag(value: Double?, refMin: Double?, refMax: Double?): RangeFlag = when {
    value == null || (refMin == null && refMax == null) -> RangeFlag.SIN_RANGO
    refMin != null && value < refMin -> RangeFlag.BAJO
    refMax != null && value > refMax -> RangeFlag.ALTO
    else -> RangeFlag.NORMAL
}

fun StudyResult.flag(): RangeFlag = flag(value, refMin, refMax)

val StudyResult.outOfRange: Boolean get() = flag().let { it == RangeFlag.BAJO || it == RangeFlag.ALTO }

/** Número para mostrar/editar: hasta 3 decimales, sin ceros sobrantes. */
fun labNum(v: Double): String = Fmt.num(v, 3)

/** "70 – 99", "< 200", "> 40" o "" si no hay rango. */
fun rangeText(refMin: Double?, refMax: Double?): String = when {
    refMin != null && refMax != null -> "${labNum(refMin)} – ${labNum(refMax)}"
    refMax != null -> "< ${labNum(refMax)}"
    refMin != null -> "> ${labNum(refMin)}"
    else -> ""
}

/** "92 mg/dL", "Negativo" o "—". */
fun StudyResult.valueText(): String = when {
    value != null -> labNum(value) + (if (unit.isNotBlank()) " $unit" else "")
    textValue.isNotBlank() -> textValue
    else -> "—"
}

/**
 * Seguimientos pendientes: estudios con fecha de seguimiento desde hace [graceDays] días en
 * adelante, del más próximo al más lejano. Útil para la pantalla de inicio.
 */
fun upcomingFollowUps(studies: List<Study>, today: Long, graceDays: Long = 60): List<Study> =
    studies.filter { it.followUpEpochDay != null && it.followUpEpochDay >= today - graceDays }
        .sortedBy { it.followUpEpochDay }

/** Un valor numérico de un parámetro, con el estudio del que proviene. */
data class ParamPoint(val study: Study, val result: StudyResult) {
    val epochDay: Long get() = study.epochDay
    val value: Double get() = result.value ?: 0.0
}

/** Resumen de un parámetro a través de los estudios (puntos en orden cronológico). */
data class ParamSummary(val name: String, val points: List<ParamPoint>) {
    val latest: ParamPoint get() = points.last()
    val previous: ParamPoint? get() = points.getOrNull(points.size - 2)
}

/** Serie cronológica de un parámetro (por nombre normalizado) a través de todos los estudios. */
fun paramSeries(name: String, studies: List<Study>, results: List<StudyResult>): List<ParamPoint> {
    val n = normalizeName(name)
    val byId = studies.associateBy { it.id }
    return results.filter { it.value != null && normalizeName(it.name) == n }
        .mapNotNull { r -> byId[r.studyId]?.let { ParamPoint(it, r) } }
        .sortedWith(compareBy({ it.epochDay }, { it.result.id }))
}

/** Todos los parámetros con al menos un valor numérico, ordenados alfabéticamente. */
fun paramSummaries(studies: List<Study>, results: List<StudyResult>): List<ParamSummary> {
    val byId = studies.associateBy { it.id }
    return results.filter { it.value != null && it.name.isNotBlank() }
        .mapNotNull { r -> byId[r.studyId]?.let { ParamPoint(it, r) } }
        .groupBy { normalizeName(it.result.name) }
        .map { (_, pts) ->
            val sorted = pts.sortedWith(compareBy({ it.epochDay }, { it.result.id }))
            ParamSummary(sorted.last().result.name.trim(), sorted)
        }
        .sortedBy { normalizeName(it.name) }
}
