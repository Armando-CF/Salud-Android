package app.savia

import android.content.Context
import app.savia.data.GlobalPrefs
import app.savia.data.Prefs
import app.savia.data.Profile
import app.savia.data.ProfileInfo
import app.savia.data.ProfileStore
import app.savia.data.SaviaDb
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Punto de acceso único a los servicios de la app.
 *
 * Savia maneja varios PERFILES (personas y mascotas). Cada perfil tiene su propia base de datos y sus
 * propios ajustes, así que los historiales nunca se mezclan. [db] y [prefs] apuntan siempre al perfil
 * ACTIVO; al cambiar de perfil la interfaz completa se vuelve a crear (ver MainActivity).
 *
 * Uso típico en pantallas:  `Savia.db.symptoms().observeAll()`  y  `Savia.io { Savia.db.symptoms().insert(x) }`.
 * Para trabajo que abarca todos los perfiles (recordatorios, respaldo): [profileIds], [dbFor], [prefsFor].
 */
object Savia {
    lateinit var appContext: Context
        private set
    lateinit var profiles: ProfileStore
        private set
    private lateinit var global: GlobalPrefs

    private val dbs = ConcurrentHashMap<Int, SaviaDb>()
    private val prefsById = ConcurrentHashMap<Int, Prefs>()
    private val infoById = ConcurrentHashMap<Int, ProfileInfo>()
    private var inMemory = false
    private var watcher: CoroutineScope? = null
    private val activeLock = Any()

    /** Vive mientras viva el proceso: las escrituras no se cancelan al salir de una pantalla. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val isReady: Boolean get() = ::profiles.isInitialized

    private val _active = MutableStateFlow(ProfileInfo(1))

    /** Resumen del perfil activo (nombre, si es mascota, especie…). Se actualiza al cambiar o editar el perfil. */
    val active: StateFlow<ProfileInfo> get() = _active

    /** Id del perfil activo. */
    val activeId: StateFlow<Int> get() = profiles.activeId

    /** Ids de todos los perfiles, en orden de creación. */
    val profileIds: List<Int> get() = profiles.ids.value

    /** Base de datos del perfil activo. */
    val db: SaviaDb get() = dbFor(profiles.activeId.value)

    /** Ajustes del perfil activo (los globales —tema, bloqueo— son los mismos en todos). */
    val prefs: Prefs get() = prefsFor(profiles.activeId.value)

    /** ¿El perfil activo es una mascota? (no cambia mientras la interfaz de ese perfil está viva) */
    val isPet: Boolean get() = _active.value.isPet

    /**
     * @param database solo para pruebas: base (en memoria) del perfil 1; los demás perfiles también
     * se crean en memoria.
     */
    fun init(context: Context, database: SaviaDb? = null) {
        watcher?.cancel()
        dbs.clear(); prefsById.clear(); infoById.clear()
        appContext = context.applicationContext
        profiles = ProfileStore(appContext)
        global = GlobalPrefs(appContext)
        inMemory = database != null
        if (database != null) dbs[1] = database
        for (id in profiles.ids.value) infoById[id] = profiles.cachedInfo(id)
        synchronized(activeLock) { _active.value = infoOf(profiles.activeId.value) }
        watchProfiles()
    }

    fun dbFor(id: Int): SaviaDb = dbs.getOrPut(id) {
        if (inMemory) SaviaDb.createInMemory(appContext) else SaviaDb.create(appContext, ProfileStore.dbName(id))
    }

    fun prefsFor(id: Int): Prefs = prefsById.getOrPut(id) { Prefs(appContext, ProfileStore.prefsName(id), global) }

    /** Resumen conocido de un perfil (puede venir vacío justo al arrancar). */
    fun infoOf(id: Int): ProfileInfo = infoById[id] ?: ProfileInfo(id)

    /** Lee el resumen de un perfil directamente de su base (para trabajo en segundo plano). */
    suspend fun loadInfo(id: Int): ProfileInfo = ProfileInfo.of(id, dbFor(id).profile().get()).also { infoById[id] = it }

    /** Resúmenes de todos los perfiles, actualizados en vivo. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val summaries: Flow<List<ProfileInfo>>
        get() = profiles.ids.flatMapLatest { ids ->
            if (ids.isEmpty()) flowOf(emptyList())
            else combine(ids.map { id -> dbFor(id).profile().observe().map { ProfileInfo.of(id, it) } }) { it.toList() }
        }

    private fun watchProfiles() {
        // Si un perfil se elimina o se reinicia mientras se observa, la consulta en curso puede fallar: se ignora.
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> })
        watcher = s
        s.launch {
            while (isActive) {
                try {
                    summaries.collect { list ->
                        for (i in list) { infoById[i.id] = i; profiles.cacheInfo(i) }
                        // Bajo el mismo candado que switchTo: así nunca se pisa el perfil recién activado con uno viejo.
                        synchronized(activeLock) { _active.value = infoOf(profiles.activeId.value) }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    delay(250) // p. ej. se eliminó un perfil a media consulta: se vuelve a observar
                }
            }
        }
    }

    /** Cambia el perfil activo. La interfaz se recrea sola (MainActivity observa [activeId]). */
    fun switchTo(id: Int) {
        if (id !in profiles.ids.value) return
        synchronized(activeLock) {
            profiles.setActive(id)
            _active.value = infoOf(id)
        }
    }

    /**
     * Crea un perfil nuevo con su ficha inicial y devuelve su id (no lo activa).
     * @param setup ajustes iniciales del perfil (p. ej. activar el ciclo).
     */
    suspend fun createProfile(profile: Profile, setup: (Prefs) -> Unit = {}): Int {
        // Primero se guarda la ficha y hasta el final se publica el id: así nadie ve un perfil a medio crear.
        val id = profiles.reserve()
        prefsFor(id).clearProfileScoped()
        dbFor(id).profile().save(profile.copy(id = 1))
        setup(prefsFor(id))
        ProfileInfo.of(id, profile).let { infoById[id] = it; profiles.cacheInfo(it) }
        profiles.publish(id)
        return id
    }

    /**
     * Elimina por completo un perfil: su base de datos, sus ajustes y sus adjuntos. El perfil 1 no se
     * puede eliminar. Si era el activo, se activa el perfil 1.
     */
    fun deleteProfile(id: Int) {
        if (id == 1 || id !in profiles.ids.value) return
        if (profiles.activeId.value == id) switchTo(1)
        profiles.remove(id)
        dbs.remove(id)?.let { runCatching { it.close() } }
        prefsById.remove(id)?.clearProfileScoped()
        infoById.remove(id)
        profiles.forgetInfo(id)
        if (!inMemory) appContext.deleteDatabase(ProfileStore.dbName(id))
        runCatching { appContext.deleteSharedPreferences(ProfileStore.prefsName(id)) }
        attachmentsDir(id).deleteRecursively()
    }

    /** Carpeta de adjuntos (fotos/PDF de estudios) de un perfil; por defecto, del activo. */
    fun attachmentsDir(id: Int = profiles.activeId.value): File = File(appContext.filesDir, ProfileStore.attachmentsPath(id))

    /** Archivo adjunto del perfil activo. */
    fun attachmentFile(fileName: String, id: Int = profiles.activeId.value): File = File(attachmentsDir(id), fileName)

    /** Sufijo para nombrar archivos/ajustes propios de un perfil: "" (perfil 1) o "_p<N>". */
    fun profileSuffix(id: Int = profiles.activeId.value): String = if (id == 1) "" else "_p$id"

    /** Lanza trabajo de base de datos / archivos fuera del hilo principal. */
    fun io(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(Dispatchers.IO, block = block)
}
