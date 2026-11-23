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

/** Ajustes de la app (no son datos de salud; esos viven en la base de datos). */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("savia_prefs", Context.MODE_PRIVATE)

    private fun bool(key: String, def: Boolean) =
        Pref(sp, key, def, { s, k, d -> s.getBoolean(k, d) }, { e, k, v -> e.putBoolean(k, v) })

    private fun int(key: String, def: Int) =
        Pref(sp, key, def, { s, k, d -> s.getInt(k, d) }, { e, k, v -> e.putInt(k, v) })

    private fun str(key: String, def: String) =
        Pref(sp, key, def, { s, k, d -> s.getString(k, d) ?: d }, { e, k, v -> e.putString(k, v) })

    /** Ya se mostró la bienvenida. */
    val onboardingDone = bool("onboarding_done", false)

    /** "system", "light" o "dark". */
    val themeMode = str("theme_mode", "system")

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

    /** Pedir huella / PIN del teléfono al abrir. */
    val appLock = bool("app_lock", false)

    /** Modelo del atlas corporal: "F" o "M". */
    val atlasSex = str("atlas_sex", "F")

    /** Recordatorio mensual de autoexploración. */
    val selfExamReminder = bool("self_exam_reminder", false)

    val all: List<Pref<*>> = listOf(
        onboardingDone, themeMode, cycleEnabled, cycleLength, periodLength, lutealLength,
        showFertility, periodReminder, appLock, atlasSex, selfExamReminder,
    )

    /** Mapa clave → valor para respaldo. */
    fun export(): Map<String, Any?> = all.associate { it.key to it.value }

    /** Restaura valores desde un respaldo (ignora claves desconocidas). */
    fun import(values: Map<String, Any?>) {
        val e = sp.edit()
        for (p in all) {
            val v = values[p.key] ?: continue
            when (p.default) {
                is Boolean -> (v as? Boolean)?.let { e.putBoolean(p.key, it) }
                is Int -> (v as? Number)?.let { e.putInt(p.key, it.toInt()) }
                is String -> e.putString(p.key, v.toString())
            }
        }
        e.commit()
        all.forEach { it.reload() }
    }
}
