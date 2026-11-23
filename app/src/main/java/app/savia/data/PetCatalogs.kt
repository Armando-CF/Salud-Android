package app.savia.data

/**
 * Síntomas frecuentes en mascotas para sugerir al registrar (agrupados).
 * Se describen como lo que la persona OBSERVA en su animal, no como un diagnóstico.
 */
object PetSymptomCatalog {
    val groups: List<Pair<String, List<String>>> = listOf(
        "General" to listOf(
            "Decaimiento", "No quiere comer", "Fiebre", "Bebe mucha agua", "Bebe poca agua",
            "Pérdida de peso", "Aumento de peso", "Temblores", "Duerme más de lo normal",
        ),
        "Digestivo" to listOf(
            "Vómito", "Diarrea", "Estreñimiento", "Gases", "Sangre en heces", "Panza inflamada",
            "Arcadas sin vomitar", "Come pasto u objetos",
        ),
        "Piel y pelaje" to listOf(
            "Comezón / se rasca", "Caída de pelo", "Herida", "Pulgas o garrapatas", "Bulto o bolita",
            "Piel enrojecida", "Costras o caspa", "Se lame mucho una zona", "Mal olor en la piel",
        ),
        "Movimiento" to listOf(
            "Cojera", "Dolor al moverse", "Rigidez", "No quiere subir o brincar", "Debilidad en las patas",
            "Pierde el equilibrio", "Arrastra el trasero",
        ),
        "Ojos, oídos y boca" to listOf(
            "Lagañas", "Ojo rojo", "Ojo cerrado o entrecerrado", "Sacude la cabeza", "Se rasca la oreja",
            "Mal olor en el oído", "Mal aliento", "Babeo", "Le cuesta masticar", "Encías pálidas",
        ),
        "Respiratorio" to listOf(
            "Tos", "Estornudos", "Respiración agitada", "Secreción nasal", "Jadeo excesivo", "Ronquido o silbido al respirar",
        ),
        "Urinario" to listOf(
            "Orina mucho", "Orina poco", "Sangre en la orina", "Le cuesta orinar", "Se orina dentro de casa",
            "Se lame los genitales",
        ),
        "Conducta" to listOf(
            "Ansiedad", "Agresividad", "Se esconde", "Llora o se queja", "Desorientación", "Inquietud por la noche",
        ),
    )

    val all: List<String> = groups.flatMap { it.second }

    /** Posibles desencadenantes para etiquetar lo que se observó. */
    val triggers: List<String> = listOf(
        "Cambio de alimento", "Comió algo raro", "Paseo / parque", "Baño", "Calor", "Estrés / viaje",
        "Otros animales", "Medicamento", "Vacuna reciente", "Golpe o caída",
    )

    /** Intensidad de la molestia tal como se observa en el animal (1..10). */
    fun intensityLabel(i: Int): String = when {
        i <= 2 -> "Apenas se nota"
        i <= 4 -> "Leve"
        i <= 6 -> "Moderado"
        i <= 8 -> "Fuerte"
        else -> "Muy fuerte"
    }

    /** Señales por las que conviene acudir a urgencias veterinarias sin esperar (orientativo). */
    val alarmSigns: List<String> = listOf(
        "le cuesta respirar o tiene las encías azuladas o muy pálidas",
        "tiene la panza inflamada y dura, o intenta vomitar sin sacar nada",
        "no puede orinar (sobre todo en gatos machos)",
        "convulsiona, se desmaya o no puede levantarse",
        "sangra y no para, o tuvo un golpe fuerte o un atropello",
        "pudo comer algo tóxico (chocolate, uvas, veneno, medicamentos)",
        "vomita o tiene diarrea muchas veces en un día, o con sangre",
    )

    /** Aviso breve cuando se registra una molestia muy fuerte. */
    const val ALARM_NOTE: String =
        "Si lo ves muy mal, empeora rápido o no cede, llévalo a tu veterinaria o veterinario o a urgencias veterinarias. " +
            "Esta app no sustituye una valoración profesional."

    /** Lista de señales de alarma en una sola frase, para una nota informativa. */
    val alarmSummary: String
        get() = "Acude a urgencias veterinarias si " + alarmSigns.take(4).joinToString("; ") + "."

    /** Especies para las que existe figura en el atlas corporal ("Elegir en el cuerpo"). */
    val speciesWithBodyMap: Set<String> = setOf("perro", "gato")

    fun hasBodyMap(species: String): Boolean = species in speciesWithBodyMap
}
