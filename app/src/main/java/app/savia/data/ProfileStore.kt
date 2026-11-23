package app.savia.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Resumen de un perfil para listas, encabezados y avisos. */
data class ProfileInfo(
    val id: Int,
    val name: String = "",
    /** "person" o "pet". */
    val kind: String = "person",
    /** Clave de [PetSpecies] (solo mascotas). */
    val species: String = "",
    val sex: String = "X",
    val relation: String = "",
    val avatarColor: Int = 0,
    val birthEpochDay: Long? = null,
) {
    val isPet: Boolean get() = kind == "pet"

    /** Nombre para mostrar; nunca vacío. */
    val displayName: String
        get() = name.trim().ifEmpty { if (isPet) "Mascota" else if (id == 1) "Yo" else "Perfil $id" }

    /** "Hija", "Perro", "Gato"… o "" si no hay nada que decir. */
    val subtitle: String
        get() = if (isPet) PetSpecies.label(species) else relation.trim()

    companion object {
        fun of(id: Int, p: Profile?): ProfileInfo =
            if (p == null) ProfileInfo(id)
            else ProfileInfo(id, p.name, p.kind, p.species, p.sex, p.relation, p.avatarColor, p.birthEpochDay)
    }
}

/** Especies de mascota (clave → etiqueta). */
object PetSpecies {
    val all: List<Pair<String, String>> = listOf(
        "perro" to "Perro", "gato" to "Gato", "ave" to "Ave", "conejo" to "Conejo",
        "roedor" to "Hámster / roedor", "reptil" to "Reptil / tortuga", "pez" to "Pez", "otro" to "Otra",
    )

    fun label(key: String): String = all.firstOrNull { it.first == key }?.second ?: "Mascota"

    /** Sexo de una mascota usando las mismas claves que Profile.sex. */
    val sexes: List<Pair<String, String>> = listOf("F" to "Hembra", "M" to "Macho", "X" to "No sé")
}

/**
 * Lista de perfiles y perfil activo. Solo guarda ids: los datos de cada perfil viven en su propia
 * base de datos (fila única de la tabla `profile`). El perfil 1 es el original y no se puede eliminar.
 */
class ProfileStore(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("savia_profiles", Context.MODE_PRIVATE)

    private val _ids = MutableStateFlow(readIds())
    val ids: StateFlow<List<Int>> get() = _ids

    private val _activeId = MutableStateFlow(sp.getInt("active", 1).let { if (it in _ids.value) it else 1 })
    val activeId: StateFlow<Int> get() = _activeId

    private fun readIds(): List<Int> {
        val raw = sp.getString("ids", null) ?: return listOf(1)
        val list = raw.split(',').mapNotNull { it.trim().toIntOrNull() }.distinct()
        return if (1 in list) list else listOf(1) + list
    }

    /** Reserva un id nuevo (nunca se reutilizan) y lo agrega a la lista. */
    @Synchronized
    fun add(): Int {
        val next = maxOf(sp.getInt("next", 2), (_ids.value.maxOrNull() ?: 1) + 1)
        val list = _ids.value + next
        sp.edit().putInt("next", next + 1).putString("ids", list.joinToString(",")).commit()
        _ids.value = list
        return next
    }

    @Synchronized
    fun remove(id: Int) {
        if (id == 1) return
        val list = _ids.value - id
        val e = sp.edit().putString("ids", list.joinToString(","))
        if (_activeId.value == id) { e.putInt("active", 1); _activeId.value = 1 }
        e.commit()
        _ids.value = list
    }

    fun setActive(id: Int) {
        if (id !in _ids.value) return
        sp.edit().putInt("active", id).apply()
        _activeId.value = id
    }

    /** Guarda lo esencial del resumen para tenerlo disponible al instante en el siguiente arranque. */
    fun cacheInfo(info: ProfileInfo) {
        val v = listOf(info.kind, info.species, info.sex, info.avatarColor.toString(), info.relation.replace('|', ' '), info.name.replace('|', ' ')).joinToString("|")
        if (sp.getString("info_${info.id}", null) != v) sp.edit().putString("info_${info.id}", v).apply()
    }

    fun cachedInfo(id: Int): ProfileInfo {
        val p = sp.getString("info_$id", null)?.split('|') ?: return ProfileInfo(id)
        if (p.size < 6) return ProfileInfo(id)
        return ProfileInfo(id, name = p[5], kind = p[0], species = p[1], sex = p[2], relation = p[4], avatarColor = p[3].toIntOrNull() ?: 0)
    }

    fun forgetInfo(id: Int) {
        sp.edit().remove("info_$id").apply()
    }

    companion object {
        fun dbName(id: Int): String = if (id == 1) SaviaDb.NAME else "savia_p$id.db"
        fun prefsName(id: Int): String = if (id == 1) Prefs.FILE else "savia_prefs_p$id"

        /** Carpeta de adjuntos relativa a filesDir: "attachments" (perfil 1) o "attachments/p<N>". */
        fun attachmentsPath(id: Int): String = if (id == 1) "attachments" else "attachments/p$id"
    }
}
