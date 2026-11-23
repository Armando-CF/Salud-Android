package app.savia.ui.nav

import android.net.Uri
import androidx.navigation.NavController

/** Rutas de navegación. Usa las funciones para construir rutas con argumentos. */
object Routes {
    const val HOME = "home"
    const val MORE = "more"
    const val SYMPTOMS = "symptoms"
    const val CYCLE = "cycle"
    const val MEASURES = "measures"
    const val BODY = "body"

    const val WELLBEING = "wellbeing"
    const val SELF_EXAM = "self_exam"
    const val STUDIES = "studies"
    const val MEDS = "meds"
    const val APPOINTMENTS = "appointments"
    const val CYCLE_STATS = "cycle_stats"
    const val CYCLE_SETTINGS = "cycle_settings"
    const val EXPORT = "export"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"

    /** Lista de perfiles (personas y mascotas): cambiar, crear, eliminar. */
    const val PROFILES = "profiles"
    const val VACCINES = "vaccines"

    /** Clave en savedStateHandle donde el atlas deja la zona elegida como "id|Nombre". */
    const val RESULT_REGION = "picked_region"

    private fun enc(s: String?): String = Uri.encode(s ?: "")

    /** Editor de síntoma. id = null → nuevo. Se puede prellenar zona del cuerpo y nombre. */
    fun symptomEdit(id: Long? = null, regionId: String? = null, regionLabel: String? = null, name: String? = null): String =
        "symptom_edit?id=${id ?: -1}&regionId=${enc(regionId)}&regionLabel=${enc(regionLabel)}&name=${enc(name)}"

    /** Detalle de un tipo de medición (clave de MeasureType). add = abrir de una vez el formulario de captura. */
    fun measure(type: String, add: Boolean = false): String = "measure/$type?add=$add"

    /** Atlas corporal en modo "elegir zona" (devuelve el resultado en RESULT_REGION). */
    fun bodyPick(): String = "body_pick"

    /** Registro de un día del ciclo. */
    fun cycleDay(epochDay: Long): String = "cycle_day/$epochDay"

    fun study(id: Long): String = "study/$id"
    fun studyEdit(id: Long? = null): String = "study_edit?id=${id ?: -1}"

    /** Evolución de un parámetro de laboratorio (por nombre) a través de todos los estudios. */
    fun paramTrend(name: String): String = "param_trend?name=${enc(name)}"

    /** Alta de un perfil nuevo. kind = "person" o "pet". */
    fun profileNew(kind: String): String = "profile_new/$kind"

    fun vaccineEdit(id: Long? = null): String = "vaccine_edit?id=${id ?: -1}"

    fun medEdit(id: Long? = null): String = "med_edit?id=${id ?: -1}"
    fun appointmentEdit(id: Long? = null): String = "appointment_edit?id=${id ?: -1}"
}

/** Regresa sin riesgo de vaciar la pila (doble toque). */
fun NavController.back() {
    if (previousBackStackEntry != null) popBackStack()
}
