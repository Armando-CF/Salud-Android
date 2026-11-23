package app.savia.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Preferencia observable respaldada por SharedPreferences. */
class Pref<T>(
    private val sp: SharedPreferences,
    val key: String,
    val default: T,
    private val read: (SharedPreferences, String, T) -> T,
    private val write: (SharedPreferences.Editor, String, T) -> Unit,
) {
    private val state = MutableStateFlow(read(sp, key, default))
    val flow: StateFlow<T> get() = state
    var value: T
        get() = state.value
        set(v) {
            state.value = v
            sp.edit().also { write(it, key, v) }.apply()
        }

    fun reload() {
        state.value = read(sp, key, default)
    }
}

/** Ajustes globales (iguales para todos los perfiles). Una sola instancia por proceso. */
class GlobalPrefs(context: Context) {
    internal val sp: SharedPreferences = context.applicationContext.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)

    /** Ya se mostró la bienvenida. */
    val onboardingDone = Pref(sp, "onboarding_done", false, { s, k, d -> s.getBoolean(k, d) }, { e, k, v -> e.putBoolean(k, v) })

    /** "system", "light" o "dark". */
    val themeMode = Pref(sp, "theme_mode", "system", { s, k, d -> s.getString(k, d) ?: d }, { e, k, v -> e.putString(k, v) })

    /** Pedir huella / PIN del teléfono al abrir. */
    val appLock = Pref(sp, "app_lock", false, { s, k, d -> s.getBoolean(k, d) }, { e, k, v -> e.putBoolean(k, v) })
}

/**
 * Ajustes de la app (no son datos de salud; esos viven en la base de datos).
 * Hay una instancia POR PERFIL: los ajustes del ciclo, del atlas y de recordatorios son de cada perfil
 * (archivo [fileName]); tema, bloqueo y bienvenida son globales y se comparten entre todas las instancias.
 */
class Prefs(context: Context, fileName: String = FILE, global: GlobalPrefs? = null) {
    private val sp = context.applicationContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)
    private val g: GlobalPrefs = global ?: GlobalPrefs(context)

    private fun bool(key: String, def: Boolean) =
        Pref(sp, key, def, { s, k, d -> s.getBoolean(k, d) }, { e, k, v -> e.putBoolean(k, v) })

    private fun int(key: String, def: Int) =
        Pref(sp, key, def, { s, k, d -> s.getInt(k, d) }, { e, k, v -> e.putInt(k, v) })

    private fun str(key: String, def: String) =
        Pref(sp, key, def, { s, k, d -> s.getString(k, d) ?: d }, { e, k, v -> e.putString(k, v) })

    // ── Globales ──
    /** Ya se mostró la bienvenida. */
    val onboardingDone: Pref<Boolean> get() = g.onboardingDone

    /** "system", "light" o "dark". */
    val themeMode: Pref<String> get() = g.themeMode

    /** Pedir huella / PIN del teléfono al abrir. */
    val appLock: Pref<Boolean> get() = g.appLock

    // ── Por perfil ──
    /** Módulo de ciclo menstrual visible. */
    val cycleEnabled = bool("cycle_enabled", false)

    /** Duración típica del ciclo (días) usada mientras no haya suficiente historial. */
    val cycleLength = int("cycle_length", 28)

    /** Duración típica del periodo (días). */
    val periodLength = int("period_length", 5)

    /** Fase lútea (días entre ovulación y siguiente periodo). */
    val lutealLength = int("luteal_length", 14)

    /** Mostrar ventana fértil y ovulación estimadas. */
    val showFertility = bool("show_fertility", true)

    /** Avisar unos días antes del periodo estimado. */
    val periodReminder = bool("period_reminder", false)

    /** Modelo del atlas corporal: "F" o "M". */
    val atlasSex = str("atlas_sex", "F")

    /** Recordatorio mensual de autoexploración. */
    val selfExamReminder = bool("self_exam_reminder", false)

    /** Ajustes propios de este perfil. */
    val profileScoped: List<Pref<*>> = listOf(
        cycleEnabled, cycleLength, periodLength, lutealLength, showFertility, periodReminder, atlasSex, selfExamReminder,
    )

    val all: List<Pref<*>> get() = listOf(onboardingDone, themeMode, appLock) + profileScoped

    /** Mapa clave → valor para respaldo. */
    fun export(): Map<String, Any?> = all.associate { it.key to it.value }

    /** Restaura valores desde un respaldo (ignora claves desconocidas). */
    fun import(values: Map<String, Any?>) {
        for (p in all) {
            val v = values[p.key] ?: continue
            @Suppress("UNCHECKED_CAST")
            when (p.default) {
                is Boolean -> (v as? Boolean)?.let { (p as Pref<Boolean>).value = it }
                is Int -> (v as? Number)?.let { (p as Pref<Int>).value = it.toInt() }
                is String -> (p as Pref<String>).value = v.toString()
            }
        }
    }

    /** Borra los ajustes propios de este perfil (al eliminar el perfil). */
    fun clearProfileScoped() {
        sp.edit().also { e -> profileScoped.forEach { e.remove(it.key) } }.commit()
        profileScoped.forEach { it.reload() }
    }

    companion object {
        /** Archivo del perfil 1 y de los ajustes globales. */
        const val FILE = "savia_prefs"
    }
}
