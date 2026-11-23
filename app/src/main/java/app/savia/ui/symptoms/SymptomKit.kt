package app.savia.ui.symptoms

import app.savia.Savia
import app.savia.data.PetSymptomCatalog
import app.savia.data.SymptomCatalog

/**
 * Catálogo y textos del módulo de síntomas según de quién sea el perfil: una persona anota lo que
 * siente; con una mascota se anota lo que se le observa.
 */
class SymptomKit(val isPet: Boolean, val species: String = "", val name: String = "") {
    /** Síntomas sugeridos, agrupados. */
    val groups: List<Pair<String, List<String>>> get() = if (isPet) PetSymptomCatalog.groups else SymptomCatalog.groups

    val all: List<String> get() = if (isPet) PetSymptomCatalog.all else SymptomCatalog.all

    /** Desencadenantes sugeridos. */
    val triggers: List<String> get() = if (isPet) PetSymptomCatalog.triggers else SymptomCatalog.triggers

    fun intensityLabel(i: Int): String = if (isPet) PetSymptomCatalog.intensityLabel(i) else SymptomCatalog.intensityLabel(i)

    /** ¿Se puede señalar la zona en el atlas? Personas siempre; mascotas solo si hay figura de su especie. */
    val canPickBodyRegion: Boolean get() = !isPet || PetSymptomCatalog.hasBodyMap(species)

    // ── Textos ──
    val whatTitle: String get() = if (isPet) "¿Qué le notas?" else "¿Qué sientes?"
    val nameLabel: String get() = if (isPet) "Síntoma o cambio que observas" else "Síntoma o molestia"
    val namePlaceholder: String get() = if (isPet) "Ej. Vómito" else "Ej. Dolor de cabeza"
    val nameError: String get() = if (isPet) "Escribe o elige qué le notas" else "Escribe o elige qué sientes"
    val intensityHigh: String get() = if (isPet) "10 · lo ves muy mal" else "10 · insoportable"
    val whereTitle: String get() = if (isPet) "¿En qué parte del cuerpo?" else "¿En qué parte del cuerpo?"
    val whereHint: String
        get() = if (isPet) "Opcional. Señala en la figura dónde lo notas; ahí verás también cómo se llama la zona."
        else "Opcional. Señala en el modelo dónde lo sientes; ahí verás también cómo se llama la zona."
    val notesPlaceholder: String
        get() = if (isPet) "Cómo empezó, qué comió, cómo está de ánimo, qué le diste…"
        else "Cómo empezó, qué lo alivia o empeora, qué tomaste…"
    val customTriggerExample: String get() = if (isPet) "Ej. Premio nuevo" else "Ej. Cafeína"

    /** Aviso cuando la intensidad es muy alta (9–10). */
    val alarmNote: String
        get() = if (isPet) PetSymptomCatalog.ALARM_NOTE
        else "Si el dolor es muy fuerte, apareció de repente o no cede, busca atención médica o llama a emergencias. " +
            "Esta app no sustituye una valoración profesional."

    /** Texto de la lista vacía. */
    val emptyMessage: String
        get() = if (isPet) {
            "Anota aquí lo que le notes a ${name.ifBlank { "tu mascota" }}: vómito, cojera, comezón… " +
                "con qué tan fuerte fue y cuándo. Le servirá mucho a tu veterinario."
        } else {
            ""
        }

    companion object {
        /** Según el perfil activo. No cambia mientras vive la interfaz de ese perfil. */
        fun current(): SymptomKit = Savia.active.value.let { SymptomKit(it.isPet, it.species, it.displayName) }
    }
}
