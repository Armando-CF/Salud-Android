package app.savia.ui.vaccines

import app.savia.data.ProfileInfo
import app.savia.util.Fmt
import java.text.Normalizer

/**
 * Sugerencia de nombre para el registro de vacunas.
 * @param intervalDays intervalo típico hasta la siguiente aplicación; SOLO sirve para proponer una fecha
 * con un toque (orientativo: manda la cartilla y lo que indique la médica/el médico o veterinario).
 */
data class VaccineSuggestion(
    val name: String,
    /** Clave de [app.savia.data.VaccinationTypes]. */
    val type: String,
    val group: String,
    val intervalDays: Int? = null,
    /** Qué cubre o cuándo suele aplicarse (texto corto). */
    val hint: String = "",
) {
    /** "cada año", "cada 3 meses"… o null. */
    val intervalLabel: String? get() = intervalDays?.let { VaccineCatalog.intervalLabel(it) }
}

/** Catálogo de sugerencias (personas, perros, gatos y otras mascotas). No es un esquema médico. */
object VaccineCatalog {
    const val VACUNA = "vacuna"
    const val DESP_INTERNA = "desp_interna"
    const val DESP_EXTERNA = "desp_externa"
    const val OTRO = "otro"

    val doses: List<String> = listOf("1.ª dosis", "2.ª dosis", "3.ª dosis", "Refuerzo", "Anual", "Dosis única")

    private const val G_BEBE = "Recién nacidos y bebés"
    private const val G_INFANCIA = "Infancia"
    private const val G_ADOLESCENCIA = "Adolescencia"
    private const val G_ADULTOS = "Adultos y refuerzos"
    private const val G_OTROS = "Otras aplicaciones"

    private fun v(name: String, group: String, interval: Int? = null, hint: String = "") = VaccineSuggestion(name, VACUNA, group, interval, hint)

    /** Esquema habitual en México (orientativo). */
    private val people: List<VaccineSuggestion> = listOf(
        v("BCG", G_BEBE, hint = "Tuberculosis · al nacer"),
        v("Hepatitis B", G_BEBE, hint = "Al nacer y en la infancia"),
        v("Hexavalente", G_BEBE, hint = "Difteria, tosferina, tétanos, polio, Hib y hepatitis B"),
        v("Pentavalente", G_BEBE, hint = "Difteria, tosferina, tétanos, polio y Hib"),
        v("Rotavirus", G_BEBE, hint = "Diarrea por rotavirus"),
        v("Neumococo conjugada", G_BEBE, hint = "Neumonía y meningitis"),
        v("Influenza", G_BEBE, 365, "Temporada de invierno"),
        v("SRP (triple viral)", G_INFANCIA, hint = "Sarampión, rubéola y paperas"),
        v("DPT", G_INFANCIA, hint = "Difteria, tosferina y tétanos · refuerzo"),
        v("Varicela", G_INFANCIA),
        v("Hepatitis A", G_INFANCIA),
        v("Polio (Sabin)", G_INFANCIA, hint = "Gotas en campañas de vacunación"),
        v("VPH", G_ADOLESCENCIA, hint = "Virus del papiloma humano"),
        v("Tdpa", G_ADOLESCENCIA, hint = "Tétanos, difteria y tosferina · también en el embarazo"),
        v("SR (doble viral)", G_ADOLESCENCIA, hint = "Sarampión y rubéola"),
        v("Meningococo", G_ADOLESCENCIA),
        v("Td (tétanos y difteria)", G_ADULTOS, 3650, "Refuerzo para personas adultas"),
        v("COVID-19", G_ADULTOS, hint = "Refuerzos según la campaña vigente"),
        v("Neumococo polisacárida", G_ADULTOS, hint = "Personas adultas mayores"),
        v("Herpes zóster", G_ADULTOS, hint = "Personas adultas mayores"),
        v("Fiebre amarilla", G_ADULTOS, hint = "Para algunos viajes"),
        v("Fiebre tifoidea", G_ADULTOS, hint = "Para algunos viajes"),
        VaccineSuggestion("Desparasitación", OTRO, G_OTROS, 180, "Campañas de salud"),
        VaccineSuggestion("Vitamina A", OTRO, G_OTROS, hint = "Campañas de salud infantil"),
        VaccineSuggestion("Prueba de tuberculina (PPD)", OTRO, G_OTROS),
        VaccineSuggestion("Inmunoglobulina", OTRO, G_OTROS),
    )

    private const val G_VAC = "Vacunas"
    private const val G_INT = "Desparasitación interna"
    private const val G_EXT = "Pulgas y garrapatas"

    private val petInternal: List<VaccineSuggestion> = listOf(
        VaccineSuggestion("Desparasitante en tableta", DESP_INTERNA, G_INT, 90, "Lombrices y otros parásitos intestinales"),
        VaccineSuggestion("Desparasitante en suspensión", DESP_INTERNA, G_INT, 90, "Común en cachorros y gatitos"),
        VaccineSuggestion("Desparasitante en pasta", DESP_INTERNA, G_INT, 90),
        VaccineSuggestion("Desparasitante inyectable", DESP_INTERNA, G_INT, 90),
    )

    private val petExternal: List<VaccineSuggestion> = listOf(
        VaccineSuggestion("Pipeta", DESP_EXTERNA, G_EXT, 30, "Se aplica sobre la piel"),
        VaccineSuggestion("Tableta masticable", DESP_EXTERNA, G_EXT, 30, "Algunas marcas duran 3 meses"),
        VaccineSuggestion("Collar antipulgas", DESP_EXTERNA, G_EXT, 180, "La duración depende de la marca"),
        VaccineSuggestion("Spray", DESP_EXTERNA, G_EXT, 30),
        VaccineSuggestion("Baño garrapaticida", DESP_EXTERNA, G_EXT, 30),
    )

    private val petOther: List<VaccineSuggestion> = listOf(
        VaccineSuggestion("Revisión preventiva", OTRO, G_OTROS, 365, "Chequeo general"),
        VaccineSuggestion("Limpieza dental", OTRO, G_OTROS, 365),
        VaccineSuggestion("Prevención de gusano del corazón", OTRO, G_OTROS, 30, "Dirofilaria, donde hay riesgo"),
    )

    private val dogs: List<VaccineSuggestion> = listOf(
        v("Rabia", G_VAC, 365),
        v("Múltiple (quíntuple)", G_VAC, 365, "Moquillo, parvovirus, hepatitis, parainfluenza y leptospira"),
        v("Séxtuple", G_VAC, 365, "Quíntuple más coronavirus"),
        v("Puppy (moquillo y parvovirus)", G_VAC, 21, "Primera vacuna de cachorro"),
        v("Parvovirus", G_VAC, 21, "Serie de cachorro"),
        v("Moquillo", G_VAC, 21, "Serie de cachorro"),
        v("Bordetella", G_VAC, 365, "Tos de las perreras"),
        v("Leptospira", G_VAC, 365),
        v("Giardia", G_VAC, 365),
        v("Coronavirus canino", G_VAC, 365),
    ) + petInternal + petExternal + petOther

    private val cats: List<VaccineSuggestion> = listOf(
        v("Triple felina", G_VAC, 365, "Rinotraqueítis, calicivirus y panleucopenia"),
        v("Rabia", G_VAC, 365),
        v("Leucemia felina", G_VAC, 365, "Sobre todo si sale de casa"),
        v("Cuádruple felina", G_VAC, 365, "Triple más clamidia"),
        v("Panleucopenia", G_VAC, 365),
    ) + petInternal + petExternal + petOther.filterNot { it.name.contains("gusano") }

    private val rabbits: List<VaccineSuggestion> = listOf(
        v("Mixomatosis", G_VAC, 180),
        v("Enfermedad hemorrágica vírica", G_VAC, 365),
        v("Rabia", G_VAC, 365),
    ) + petInternal + petExternal + petOther.take(1)

    private val birds: List<VaccineSuggestion> = listOf(
        v("Newcastle", G_VAC, 365),
        v("Viruela aviar", G_VAC, 365),
    ) + petInternal + listOf(VaccineSuggestion("Tratamiento contra ácaros", DESP_EXTERNA, G_EXT, 90)) + petOther.take(1)

    private val otherPets: List<VaccineSuggestion> = listOf(
        v("Rabia", G_VAC, 365, "Si aplica a su especie"),
    ) + petInternal + petExternal.take(2) + petOther.take(1)

    /** Todas las sugerencias para el perfil (personas: ordenadas con la etapa de su edad primero). */
    fun suggestions(profile: ProfileInfo): List<VaccineSuggestion> {
        if (!profile.isPet) {
            val order = groupOrder(profile.birthEpochDay?.let { Fmt.age(it) })
            return people.sortedBy { order.indexOf(it.group).let { i -> if (i < 0) order.size else i } }
        }
        return when (profile.species) {
            "perro" -> dogs
            "gato" -> cats
            "conejo" -> rabbits
            "ave" -> birds
            else -> otherPets
        }
    }

    private fun groupOrder(age: Int?): List<String> = when {
        age == null -> listOf(G_BEBE, G_INFANCIA, G_ADOLESCENCIA, G_ADULTOS, G_OTROS)
        age < 2 -> listOf(G_BEBE, G_INFANCIA, G_ADOLESCENCIA, G_ADULTOS, G_OTROS)
        age < 10 -> listOf(G_INFANCIA, G_BEBE, G_ADOLESCENCIA, G_ADULTOS, G_OTROS)
        age < 20 -> listOf(G_ADOLESCENCIA, G_ADULTOS, G_INFANCIA, G_BEBE, G_OTROS)
        else -> listOf(G_ADULTOS, G_ADOLESCENCIA, G_INFANCIA, G_BEBE, G_OTROS)
    }

    /** Sugerencias filtradas por tipo y por lo escrito (sin distinguir mayúsculas ni acentos). */
    fun filter(profile: ProfileInfo, type: String, query: String): List<VaccineSuggestion> {
        val q = normalize(query)
        return suggestions(profile).filter { it.type == type && (q.isEmpty() || normalize(it.name).contains(q) || normalize(it.hint).contains(q)) }
    }

    /** Sugerencia cuyo nombre coincide con [name] para ese perfil (y tipo, si se indica). */
    fun find(profile: ProfileInfo, name: String, type: String? = null): VaccineSuggestion? {
        val n = normalize(name)
        if (n.isEmpty()) return null
        return suggestions(profile).firstOrNull { normalize(it.name) == n && (type == null || it.type == type) }
    }

    /** Nombre comparable: minúsculas, sin acentos, sin espacios repetidos. */
    fun normalize(s: String): String =
        Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("\\s+"), " ")

    /** 365 → "cada año", 90 → "cada 3 meses", 21 → "cada 3 semanas". */
    fun intervalLabel(days: Int): String = when {
        days >= 365 && days % 365 == 0 -> if (days == 365) "cada año" else "cada ${days / 365} años"
        days >= 30 && days % 30 == 0 -> if (days == 30) "cada mes" else "cada ${days / 30} meses"
        days % 7 == 0 -> if (days == 7) "cada semana" else "cada ${days / 7} semanas"
        else -> "cada $days días"
    }

    /** 365 → "en 1 año", 90 → "en 3 meses" (para el atajo de la próxima fecha). */
    fun inLabel(days: Int): String = when {
        days >= 365 && days % 365 == 0 -> if (days == 365) "en 1 año" else "en ${days / 365} años"
        days >= 30 && days % 30 == 0 -> if (days == 30) "en 1 mes" else "en ${days / 30} meses"
        days % 7 == 0 -> if (days == 7) "en 1 semana" else "en ${days / 7} semanas"
        else -> "en $days días"
    }
}
