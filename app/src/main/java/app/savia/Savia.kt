package app.savia

import android.content.Context
import app.savia.data.Prefs
import app.savia.data.SaviaDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Punto de acceso único a los servicios de la app.
 * Uso típico en pantallas:  `Savia.db.symptoms().observeAll()`  y  `Savia.io { Savia.db.symptoms().insert(x) }`.
 */
object Savia {
    lateinit var appContext: Context
        private set
    lateinit var db: SaviaDb
        private set
    lateinit var prefs: Prefs
        private set

    /** Vive mientras viva el proceso: las escrituras no se cancelan al salir de una pantalla. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val isReady: Boolean get() = ::db.isInitialized

    fun init(context: Context, database: SaviaDb? = null) {
        appContext = context.applicationContext
        prefs = Prefs(appContext)
        db = database ?: SaviaDb.create(appContext)
    }

    /** Lanza trabajo de base de datos / archivos fuera del hilo principal. */
    fun io(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(Dispatchers.IO, block = block)
}
