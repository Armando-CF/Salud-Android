package app.savia.ui.studies

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import app.savia.Savia
import app.savia.data.StudyCategories
import app.savia.data.StudyResult
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Adaptación del módulo de estudios al tipo de perfil (persona adulta, menor de edad o mascota).
 * Es lógica pura para poder probarla; las pantallas leen el perfil activo con [StudyProfile.current].
 */
data class StudyProfile(
    val isPet: Boolean = false,
    /** Clave de PetSpecies ("perro", "gato"…) si es mascota. */
    val species: String = "",
    val sex: String = "X",
    /** Persona menor de 18 años. */
    val isMinor: Boolean = false,
) {
    /** Etiqueta del campo de quien indicó o atendió el estudio. */
    val doctorLabel: String get() = if (isPet) "Veterinaria o veterinario" else "Médico que lo indicó o atendió"

    val placeLabel: String get() = if (isPet) "Clínica veterinaria o laboratorio" else "Laboratorio, clínica u hospital"

    /** A quién consultar las dudas ("tu médico" / "tu veterinaria o veterinario"). */
    val whoToAsk: String get() = if (isPet) "tu veterinaria o veterinario" else "tu médico"

    /** De quién es el laboratorio ("tu laboratorio" / "el laboratorio veterinario"). */
    val labName: String get() = if (isPet) "el laboratorio veterinario" else "tu laboratorio"

    /** Nota sobre los rangos de referencia en el editor. */
    val rangesNote: String
        get() = when {
            isPet -> "Usa los rangos de referencia que vienen en el reporte del laboratorio veterinario: cambian según la especie, la raza y la edad."
            isMinor -> "Rangos de referencia de adultos: verifica los del reporte, cambian con la edad."
            else -> "Los rangos que se rellenan solos son orientativos para adultos. Cámbialos por los que vengan en el reporte de tu laboratorio."
        }

    /**
     * Categorías que se ofrecen al crear o editar un estudio. "Vacuna" ya no se ofrece (las vacunas
     * tienen su propia sección) salvo que el estudio que se edita ya la tenga ([current]).
     */
    fun categories(current: String? = null): List<Pair<String, String>> {
        val keys = if (isPet) PET_CATEGORIES else StudyCategories.all.map { it.first }.filter { it != VACCINE }
        val all = if (current != null && current !in keys && StudyCategories.all.any { it.first == current }) keys + current else keys
        return all.map { it to categoryShort(it) }
    }

    /** Etiqueta corta de una categoría para chips y listas. */
    fun categoryShort(key: String): String =
        if (isPet && key == "chequeo") "Consulta o revisión" else StudyCategories.short(key)

    /** Títulos sugeridos para una categoría. */
    fun titlesFor(category: String): List<String> = when {
        category == VACCINE -> emptyList()
        !isPet -> StudyCatalog.titlesFor(category)
        else -> petTitles(category)
    }

    /** Grupos de parámetros comunes. */
    val groups: List<LabGroup> get() = if (isPet) PET_GROUPS else StudyCatalog.groups

    /**
     * Unidad y rango con que se rellena un parámetro al elegirlo: primero lo último que la persona
     * capturó con ese nombre; si no hay, el catálogo. Para mascotas el catálogo solo aporta la unidad
     * (sus rangos son de personas adultas y no aplican a animales).
     */
    fun defaultsFor(name: String, pastResults: List<StudyResult>): Triple<String, Double?, Double?>? {
        val n = normalizeName(name)
        val past = pastResults.lastOrNull { it.value != null && normalizeName(it.name) == n }
        if (past != null) return Triple(past.unit, past.refMin, past.refMax)
        val p = StudyCatalog.find(name) ?: return null
        if (isPet) return Triple(p.unit, null, null)
        val (lo, hi) = p.rangeFor(sex)
        return Triple(p.unit, lo, hi)
    }

    private fun petTitles(category: String): List<String> = when (category) {
        "lab" -> listOf("Química sanguínea", "Biometría hemática", "Examen general de orina", "Coproparasitoscópico") + when (species) {
            "gato" -> listOf("Prueba de leucemia e inmunodeficiencia felina")
            "perro" -> listOf("Prueba de parvovirus y moquillo", "Prueba de gusano del corazón")
            else -> emptyList()
        } + listOf("Citología", "Cultivo")
        "imagen" -> listOf("Radiografía", "Ultrasonido abdominal", "Radiografía de cadera", "Ecocardiograma", "Tomografía")
        "chequeo" -> listOf("Consulta veterinaria", "Revisión anual", "Revisión de cachorro", "Revisión posoperatoria", "Valoración de peso y nutrición")
        "cardio" -> listOf("Electrocardiograma", "Ecocardiograma", "Medición de presión arterial")
        "dental" -> listOf("Limpieza dental", "Revisión dental", "Extracción dental")
        "piel" -> listOf("Consulta dermatológica", "Raspado de piel", "Prueba de alergias")
        "otro" -> listOf("Esterilización", "Cirugía", "Hospitalización", "Consulta de urgencia")
        else -> emptyList()
    }

    companion object {
        const val VACCINE = "vacuna"

        /** Texto del aviso que lleva a la sección propia de vacunas. */
        const val VACCINES_MOVED = "Las vacunas ahora tienen su propia sección."

        private val PET_CATEGORIES = listOf("lab", "imagen", "chequeo", "cardio", "dental", "piel", "otro")

        private val PET_GROUPS = listOf(
            LabGroup("Química sanguínea", listOf("Glucosa", "Urea", "Creatinina", "TGP (ALT)", "Fosfatasa alcalina", "Proteínas totales", "Albúmina")),
            LabGroup("Biometría hemática", listOf("Hemoglobina", "Hematocrito", "Eritrocitos", "Leucocitos", "Plaquetas")),
            LabGroup("Función hepática", listOf("TGO (AST)", "TGP (ALT)", "GGT", "Fosfatasa alcalina", "Bilirrubina total", "Albúmina")),
            LabGroup("Función renal", listOf("Urea", "Nitrógeno ureico (BUN)", "Creatinina")),
            LabGroup("Electrolitos", listOf("Sodio", "Potasio", "Calcio")),
            LabGroup("Examen general de orina", listOf("Densidad urinaria", "pH urinario")),
        )

        fun isMinor(birthEpochDay: Long?, today: Long): Boolean =
            birthEpochDay != null && ChronoUnit.YEARS.between(LocalDate.ofEpochDay(birthEpochDay), LocalDate.ofEpochDay(today)) < 18

        /** Perfil activo. El tipo de perfil no cambia mientras sus pantallas están vivas. */
        fun current(today: Long = app.savia.util.today()): StudyProfile {
            val a = Savia.active.value
            return StudyProfile(
                isPet = a.isPet,
                species = a.species,
                sex = a.sex,
                isMinor = !a.isPet && isMinor(a.birthEpochDay, today),
            )
        }
    }
}

/**
 * [StudyProfile] del perfil activo, observado: si el resumen del perfil llega o cambia después de
 * abrir la pantalla (p. ej. justo tras crear el perfil), las etiquetas se corrigen solas.
 */
@Composable
fun rememberStudyProfile(): StudyProfile {
    val info by Savia.active.collectAsStateWithLifecycle()
    val today = app.savia.util.today()
    return remember(info, today) {
        StudyProfile(
            isPet = info.isPet,
            species = info.species,
            sex = info.sex,
            isMinor = !info.isPet && StudyProfile.isMinor(info.birthEpochDay, today),
        )
    }
}

/** Aviso con enlace a la sección de vacunas (para los estudios guardados con la categoría "Vacuna"). */
@Composable
fun VaccinesMovedNote(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 2.dp)) {
            Text(
                StudyProfile.VACCINES_MOVED + " Ahí puedes anotar la fecha de aplicación y cuándo toca el refuerzo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            TextButton(onClick = onOpen) { Text("Ir a Vacunas") }
        }
    }
}
