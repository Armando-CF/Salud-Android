package app.savia.export

import android.content.Context
import androidx.room.withTransaction
import app.savia.BuildConfig
import app.savia.Savia
import app.savia.data.Appointment
import app.savia.data.CycleDay
import app.savia.data.DailyLog
import app.savia.data.MedIntake
import app.savia.data.Measurement
import app.savia.data.Medication
import app.savia.data.Prefs
import app.savia.data.Profile
import app.savia.data.SaviaDb
import app.savia.data.SelfExam
import app.savia.data.Study
import app.savia.data.StudyAttachment
import app.savia.data.StudyResult
import app.savia.data.SymptomEntry
import app.savia.data.Vaccination
import app.savia.reminders.ReminderScheduler
import app.savia.util.Fmt
import app.savia.util.nowMillis
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Error con mensaje listo para mostrarse a la persona usuaria. */
class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Cómo aplicar el respaldo de UN perfil sobre los datos actuales de un perfil. */
enum class RestoreMode { REPLACE, MERGE }

/** Qué contiene un archivo de respaldo. */
enum class BackupKind {
    /** Un solo perfil (`data.json` + `attachments/`). Es también el formato de la versión 1.0. */
    PROFILE,

    /** Todos los perfiles (`perfiles.json` + `perfiles/<id>/…`). */
    ALL,
}

/** Cómo cargar un respaldo completo (de todos los perfiles). */
enum class RestoreAllMode {
    /**
     * Deja el teléfono como el respaldo: el perfil principal del respaldo reemplaza al perfil 1 y los
     * demás perfiles del respaldo se agregan como perfiles nuevos.
     */
    REPLACE,

    /** No toca nada de lo actual: cada perfil del respaldo entra como perfil nuevo. */
    ADD_AS_NEW,
}

/** Contenido completo de la base de datos de un perfil (todas las tablas, sin filtrar). */
data class BackupTables(
    val profile: Profile? = null,
    val symptoms: List<SymptomEntry> = emptyList(),
    val measurements: List<Measurement> = emptyList(),
    val dailyLogs: List<DailyLog> = emptyList(),
    val studies: List<Study> = emptyList(),
    val studyResults: List<StudyResult> = emptyList(),
    val studyAttachments: List<StudyAttachment> = emptyList(),
    val cycleDays: List<CycleDay> = emptyList(),
    val medications: List<Medication> = emptyList(),
    val medIntakes: List<MedIntake> = emptyList(),
    val appointments: List<Appointment> = emptyList(),
    val selfExams: List<SelfExam> = emptyList(),
    val vaccinations: List<Vaccination> = emptyList(),
) {
    /** Total de registros (sin contar el perfil). */
    val recordCount: Int
        get() = symptoms.size + measurements.size + dailyLogs.size + studies.size + studyResults.size + cycleDays.size +
            medications.size + medIntakes.size + appointments.size + selfExams.size + vaccinations.size

    companion object {
        suspend fun load(db: SaviaDb): BackupTables = BackupTables(
            profile = db.profile().get(),
            symptoms = db.symptoms().getAll(),
            measurements = db.measurements().getAll(),
            dailyLogs = db.dailyLogs().getAll(),
            studies = db.studies().getAll(),
            studyResults = db.studies().getAllResults(),
            studyAttachments = db.studies().getAllAttachments(),
            cycleDays = db.cycle().getAll(),
            medications = db.medications().getAll(),
            medIntakes = db.medications().getAllIntakes(),
            appointments = db.appointments().getAll(),
            selfExams = db.selfExams().getAll(),
            vaccinations = db.vaccinations().getAll(),
        )
    }
}

/** Datos generales del respaldo de un perfil. */
class BackupInfo(
    val createdAtMillis: Long,
    val appVersion: String,
    val recordCount: Int,
    val attachmentCount: Int,
)

/** Datos generales de un respaldo completo recién creado. */
class BackupAllInfo(
    val createdAtMillis: Long,
    val profileCount: Int,
    val recordCount: Int,
    val attachmentCount: Int,
)

class RestoreResult(
    val mode: RestoreMode,
    val info: BackupInfo,
    /** Registros agregados. */
    val added: Int,
    /** Días (ciclo / bienestar) que ya existían y se actualizaron con el respaldo. */
    val updated: Int,
    /** Registros que ya existían idénticos y se omitieron. */
    val skipped: Int,
    val attachmentsRestored: Int,
    /** Id del perfil creado, cuando el respaldo se cargó como perfil nuevo. */
    val newProfileId: Int? = null,
    /** Nombre del perfil que venía en el respaldo ("" si no tenía). */
    val profileName: String = "",
)

/** Resultado de cargar un respaldo completo. */
class RestoreAllResult(
    val mode: RestoreAllMode,
    /** 1 si el perfil principal del respaldo reemplazó al perfil 1; si no, 0. */
    val profilesReplaced: Int,
    /** Ids de los perfiles creados. */
    val newProfileIds: List<Int>,
    /** Perfiles que había en el teléfono y se eliminaron porque así se pidió. */
    val profilesRemoved: Int,
    val records: Int,
    val attachmentsRestored: Int,
)

/** Resumen de un perfil dentro de un archivo de respaldo (para mostrarlo antes de restaurar). */
class BackupProfileSummary(
    /** Id que tenía el perfil en el teléfono donde se creó el respaldo. */
    val originalId: Int,
    val name: String,
    /** "person" o "pet". */
    val kind: String,
    val species: String,
    val recordCount: Int,
    val attachmentCount: Int,
) {
    val isPet: Boolean get() = kind == "pet"
    val displayName: String get() = name.trim().ifEmpty { if (isPet) "Mascota sin nombre" else "Perfil sin nombre" }
}

/** Lo que trae un archivo de respaldo. */
class BackupSummary(
    val kind: BackupKind,
    val createdAtMillis: Long,
    val appVersion: String,
    val profiles: List<BackupProfileSummary>,
) {
    val recordCount: Int get() = profiles.sumOf { it.recordCount }
}

/** Dónde se lee o se escribe el respaldo de UN perfil: su base, sus ajustes y su carpeta de adjuntos. */
class BackupTarget(val profileId: Int, val db: SaviaDb, val prefs: Prefs, val attachmentsDir: File) {
    companion object {
        fun of(profileId: Int): BackupTarget =
            BackupTarget(profileId, Savia.dbFor(profileId), Savia.prefsFor(profileId), Savia.attachmentsDir(profileId))

        fun active(): BackupTarget = of(Savia.activeId.value)

        /** Destino al que pertenece [db] (el perfil activo si no se reconoce). */
        fun forDb(db: SaviaDb, prefs: Prefs): BackupTarget {
            val active = Savia.activeId.value
            val id = if (Savia.dbFor(active) === db) active else Savia.profileIds.firstOrNull { Savia.dbFor(it) === db } ?: active
            return BackupTarget(id, db, prefs, Savia.attachmentsDir(id))
        }
    }
}

/**
 * Respaldos de Savia. Son los únicos archivos que se pueden volver a cargar en la app.
 *
 * - **De un perfil** ([write]): un .zip con `data.json` (todas las tablas + ajustes del perfil) y la carpeta
 *   `attachments/` con los adjuntos de sus estudios.
 * - **Completo** ([writeAll]): un .zip con `perfiles.json` (lista de perfiles) y, por cada perfil, su respaldo
 *   individual dentro de `perfiles/<id>/` (mismo `data.json` + `attachments/`).
 *
 * El formato está versionado ([FORMAT_VERSION]); al leer se toleran campos faltantes o desconocidos para que
 * los respaldos viejos (incluidos los de la versión 1.0, sin vacunas ni perfiles) sigan sirviendo.
 */
object Backup {
    const val FORMAT = "savia-backup"

    /** 1 = Savia 1.0 (un perfil). 2 = añade vacunas y los campos de perfil para mascotas y familia. */
    const val FORMAT_VERSION = 2
    const val DATA_ENTRY = "data.json"
    const val ATTACHMENTS_PREFIX = "attachments/"

    const val ALL_FORMAT = "savia-backup-all"
    const val ALL_FORMAT_VERSION = 1
    const val ALL_ENTRY = "perfiles.json"
    const val ALL_PREFIX = "perfiles/"
    const val MIME = "application/zip"

    /** Ajustes que no se restauran (dependen del teléfono actual). */
    private val PREFS_NOT_RESTORED = setOf("app_lock")

    /** Ajustes globales que viajan en el respaldo completo. */
    private val GLOBAL_PREFS = setOf("theme_mode")

    /** Carpeta de adjuntos del perfil activo. */
    fun attachmentsDir(context: Context): File = Savia.attachmentsDir()

    /** Copia interna de UN perfil que se guarda antes de reemplazar sus datos, por si hay que deshacer. */
    fun safetyFile(context: Context, profileId: Int = Savia.activeId.value): File =
        File(File(context.filesDir, "safety"), "antes-de-restaurar${Savia.profileSuffix(profileId)}.zip")

    /** Copia interna de TODOS los perfiles que se guarda antes de reemplazar con un respaldo completo. */
    fun fullSafetyFile(context: Context): File = File(File(context.filesDir, "safety"), "antes-de-restaurar-completo.zip")

    // ------------------------------------------------------------------ escribir

    /** Escribe el respaldo del perfil dueño de [db] (por defecto, el activo). Llamar fuera del hilo principal. */
    suspend fun write(context: Context, out: OutputStream, db: SaviaDb = Savia.db, prefs: Prefs = Savia.prefs): BackupInfo =
        write(context, out, BackupTarget.forDb(db, prefs))

    /** Escribe el respaldo de un perfil concreto. */
    suspend fun write(context: Context, out: OutputStream, target: BackupTarget): BackupInfo {
        val tables = BackupTables.load(target.db)
        val now = nowMillis()
        var files = 0
        ZipOutputStream(out).use { zip -> files = writeProfileEntries(zip, "", tables, target.prefs.export(), target.attachmentsDir, now) }
        return BackupInfo(now, BuildConfig.VERSION_NAME, tables.recordCount, files)
    }

    /** Escribe `data.json` y `attachments/…` de un perfil bajo [prefix]. Devuelve cuántos adjuntos se copiaron. */
    private fun writeProfileEntries(zip: ZipOutputStream, prefix: String, tables: BackupTables, prefs: Map<String, Any?>, attDir: File, now: Long): Int {
        val json = toJson(tables, prefs, now)
        val files = tables.studyAttachments.map { it.fileName }.distinct()
            .filter { isSafeName(it) }
            .map { File(attDir, it) }
            .filter { it.isFile }
        zip.putNextEntry(ZipEntry(prefix + DATA_ENTRY))
        zip.write(json.toString(1).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        for (f in files) {
            zip.putNextEntry(ZipEntry(prefix + ATTACHMENTS_PREFIX + f.name))
            f.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
        return files.size
    }

    /** Escribe el respaldo completo: todos los perfiles en un solo archivo. Llamar fuera del hilo principal. */
    suspend fun writeAll(context: Context, out: OutputStream): BackupAllInfo {
        val now = nowMillis()
        val targets = Savia.profileIds.map { BackupTarget.of(it) }
        val loaded = targets.map { it to BackupTables.load(it.db) }
        val manifest = JSONObject()
        manifest.put("format", ALL_FORMAT)
        manifest.put("formatVersion", ALL_FORMAT_VERSION)
        manifest.put("app", "Savia")
        manifest.put("appVersion", BuildConfig.VERSION_NAME)
        manifest.put("appVersionCode", BuildConfig.VERSION_CODE)
        manifest.put("createdAtMillis", now)
        manifest.put("createdAt", Fmt.isoDateTime(now))
        manifest.put("activeProfileId", Savia.activeId.value)
        manifest.put("nota", "Respaldo completo de Savia: cada carpeta de perfiles/ es el respaldo individual de un perfil.")
        val global = JSONObject()
        Savia.prefs.export().filterKeys { it in GLOBAL_PREFS }.forEach { (k, v) -> global.put(k, v ?: JSONObject.NULL) }
        manifest.put("globalPrefs", global)
        val arr = JSONArray()
        for ((t, tables) in loaded) {
            val p = tables.profile
            arr.put(
                JSONObject().put("id", t.profileId).put("name", p?.name ?: "").put("kind", p?.kind ?: "person")
                    .put("species", p?.species ?: "").put("records", tables.recordCount)
                    .put("attachments", tables.studyAttachments.size).put("path", "$ALL_PREFIX${t.profileId}/"),
            )
        }
        manifest.put("profiles", arr)
        var records = 0
        var files = 0
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(ALL_ENTRY))
            zip.write(manifest.toString(1).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            for ((t, tables) in loaded) {
                files += writeProfileEntries(zip, "$ALL_PREFIX${t.profileId}/", tables, t.prefs.export(), t.attachmentsDir, now)
                records += tables.recordCount
            }
        }
        return BackupAllInfo(now, loaded.size, records, files)
    }

    // ------------------------------------------------------------------ JSON

    private fun JSONObject.putN(key: String, value: Any?): JSONObject = put(key, value ?: JSONObject.NULL)
    private fun JSONObject.str(key: String, def: String = ""): String = if (isNull(key)) def else optString(key, def)
    private fun JSONObject.int(key: String, def: Int = 0): Int = if (isNull(key)) def else optInt(key, def)
    private fun JSONObject.intN(key: String): Int? = if (isNull(key)) null else optInt(key)
    private fun JSONObject.long(key: String, def: Long = 0L): Long = if (isNull(key)) def else optLong(key, def)
    private fun JSONObject.longN(key: String): Long? = if (isNull(key)) null else optLong(key)
    private fun JSONObject.dbl(key: String, def: Double = 0.0): Double = if (isNull(key)) def else optDouble(key, def).let { if (it.isNaN()) def else it }
    private fun JSONObject.dblN(key: String): Double? = if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }
    private fun JSONObject.bool(key: String, def: Boolean = false): Boolean = if (isNull(key)) def else optBoolean(key, def)

    private fun <T> array(items: List<T>, f: (T) -> JSONObject): JSONArray = JSONArray().also { a -> items.forEach { a.put(f(it)) } }

    private fun <T> list(tables: JSONObject, key: String, f: (JSONObject) -> T): List<T> {
        val arr = tables.optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(f) }
    }

    fun toJson(t: BackupTables, prefs: Map<String, Any?>, createdAtMillis: Long): JSONObject {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("formatVersion", FORMAT_VERSION)
        root.put("app", "Savia")
        root.put("appVersion", BuildConfig.VERSION_NAME)
        root.put("appVersionCode", BuildConfig.VERSION_CODE)
        root.put("createdAtMillis", createdAtMillis)
        root.put("createdAt", Fmt.isoDateTime(createdAtMillis))
        root.put("nota", "Respaldo completo de Savia. epochDay = días desde 1970-01-01; timestamp = milisegundos desde 1970-01-01 UTC.")
        val p = JSONObject()
        for ((k, v) in prefs) p.putN(k, v)
        root.put("prefs", p)

        val tb = JSONObject()
        tb.put("profile", JSONArray().also { a -> t.profile?.let { a.put(profileToJson(it)) } })
        tb.put("symptoms", array(t.symptoms) { s ->
            JSONObject().put("id", s.id).put("timestamp", s.timestamp).put("name", s.name).put("intensity", s.intensity)
                .putN("regionId", s.regionId).putN("regionLabel", s.regionLabel).putN("durationMin", s.durationMin)
                .put("tags", s.tags).put("notes", s.notes)
        })
        tb.put("measurements", array(t.measurements) { m ->
            JSONObject().put("id", m.id).put("timestamp", m.timestamp).put("type", m.type).put("value", m.value)
                .putN("value2", m.value2).put("tag", m.tag).put("notes", m.notes)
        })
        tb.put("daily_logs", array(t.dailyLogs) { d ->
            JSONObject().put("epochDay", d.epochDay).putN("mood", d.mood).putN("energy", d.energy).putN("stress", d.stress)
                .putN("sleepHours", d.sleepHours).putN("waterGlasses", d.waterGlasses).putN("exerciseMin", d.exerciseMin).put("notes", d.notes)
        })
        tb.put("studies", array(t.studies) { s ->
            JSONObject().put("id", s.id).put("epochDay", s.epochDay).put("title", s.title).put("category", s.category)
                .put("place", s.place).put("doctor", s.doctor).put("status", s.status).put("summary", s.summary)
                .putN("followUpEpochDay", s.followUpEpochDay)
        })
        tb.put("study_results", array(t.studyResults) { r ->
            JSONObject().put("id", r.id).put("studyId", r.studyId).put("name", r.name).putN("value", r.value)
                .put("textValue", r.textValue).put("unit", r.unit).putN("refMin", r.refMin).putN("refMax", r.refMax).put("notes", r.notes)
        })
        tb.put("study_attachments", array(t.studyAttachments) { a ->
            JSONObject().put("id", a.id).put("studyId", a.studyId).put("fileName", a.fileName).put("displayName", a.displayName).put("mime", a.mime)
        })
        tb.put("cycle_days", array(t.cycleDays) { c ->
            JSONObject().put("epochDay", c.epochDay).put("flow", c.flow).put("symptoms", c.symptoms).put("moods", c.moods)
                .put("mucus", c.mucus).putN("basalTemp", c.basalTemp).put("sex", c.sex).put("ovulationTest", c.ovulationTest)
                .put("pregnancyTest", c.pregnancyTest).put("contraceptive", c.contraceptive).put("notes", c.notes)
        })
        tb.put("medications", array(t.medications) { m ->
            JSONObject().put("id", m.id).put("name", m.name).put("dose", m.dose).put("instructions", m.instructions)
                .put("times", m.times).put("daysMask", m.daysMask).put("startEpochDay", m.startEpochDay)
                .putN("endEpochDay", m.endEpochDay).put("active", m.active).put("remind", m.remind).put("notes", m.notes)
        })
        tb.put("med_intakes", array(t.medIntakes) { i ->
            JSONObject().put("id", i.id).put("medicationId", i.medicationId).put("epochDay", i.epochDay).put("slot", i.slot)
                .put("timestamp", i.timestamp).put("status", i.status)
        })
        tb.put("appointments", array(t.appointments) { a ->
            JSONObject().put("id", a.id).put("timestamp", a.timestamp).put("title", a.title).put("doctor", a.doctor)
                .put("specialty", a.specialty).put("place", a.place).put("notes", a.notes).put("remind", a.remind).put("done", a.done)
        })
        tb.put("self_exams", array(t.selfExams) { e ->
            JSONObject().put("id", e.id).put("epochDay", e.epochDay).put("type", e.type).put("normal", e.normal).put("notes", e.notes)
        })
        tb.put("vaccinations", array(t.vaccinations) { v ->
            JSONObject().put("id", v.id).put("epochDay", v.epochDay).put("type", v.type).put("name", v.name).put("dose", v.dose)
                .putN("nextDueEpochDay", v.nextDueEpochDay).put("place", v.place).put("lot", v.lot).put("notes", v.notes).put("remind", v.remind)
        })
        root.put("tables", tb)
        return root
    }

    private fun profileToJson(p: Profile): JSONObject = JSONObject()
        .put("name", p.name).put("sex", p.sex).putN("birthEpochDay", p.birthEpochDay).putN("heightCm", p.heightCm)
        .putN("goalWeightKg", p.goalWeightKg).put("bloodType", p.bloodType).put("allergies", p.allergies)
        .put("conditions", p.conditions).put("emergencyName", p.emergencyName).put("emergencyPhone", p.emergencyPhone)
        .put("doctorName", p.doctorName).put("doctorPhone", p.doctorPhone).put("insurance", p.insurance).put("notes", p.notes)
        .put("kind", p.kind).put("species", p.species).put("breed", p.breed).putN("sterilized", p.sterilized)
        .put("microchip", p.microchip).put("markings", p.markings).put("relation", p.relation).put("avatarColor", p.avatarColor)

    /** Valida el encabezado y devuelve (tablas, ajustes, info). Lanza [BackupException] con mensaje claro. */
    fun parse(root: JSONObject): Triple<BackupTables, Map<String, Any?>, BackupInfo> {
        if (root.optString("format") != FORMAT) {
            throw BackupException("Este archivo no es un respaldo de Savia.")
        }
        val version = root.optInt("formatVersion", 0)
        if (version < 1) throw BackupException("El respaldo está dañado o incompleto.")
        if (version > FORMAT_VERSION) {
            throw BackupException("Este respaldo se creó con una versión más nueva de Savia. Actualiza la app para poder restaurarlo.")
        }
        val tb = root.optJSONObject("tables") ?: throw BackupException("El respaldo está dañado: no contiene datos.")
        val tables = BackupTables(
            profile = list(tb, "profile") { o ->
                Profile(
                    name = o.str("name"), sex = o.str("sex", "X"), birthEpochDay = o.longN("birthEpochDay"), heightCm = o.dblN("heightCm"),
                    goalWeightKg = o.dblN("goalWeightKg"), bloodType = o.str("bloodType"), allergies = o.str("allergies"),
                    conditions = o.str("conditions"), emergencyName = o.str("emergencyName"), emergencyPhone = o.str("emergencyPhone"),
                    doctorName = o.str("doctorName"), doctorPhone = o.str("doctorPhone"), insurance = o.str("insurance"), notes = o.str("notes"),
                    // Campos de la versión 2 del formato; en respaldos de Savia 1.0 no existen y quedan con su valor por defecto.
                    kind = o.str("kind", "person").let { if (it == "pet") "pet" else "person" }, species = o.str("species"), breed = o.str("breed"),
                    sterilized = if (o.isNull("sterilized")) null else o.optBoolean("sterilized"),
                    microchip = o.str("microchip"), markings = o.str("markings"), relation = o.str("relation"), avatarColor = o.int("avatarColor"),
                )
            }.firstOrNull(),
            symptoms = list(tb, "symptoms") { o ->
                SymptomEntry(
                    id = o.long("id"), timestamp = o.long("timestamp"), name = o.str("name"), intensity = o.int("intensity", 5),
                    regionId = if (o.isNull("regionId")) null else o.str("regionId"),
                    regionLabel = if (o.isNull("regionLabel")) null else o.str("regionLabel"),
                    durationMin = o.intN("durationMin"), tags = o.str("tags"), notes = o.str("notes"),
                )
            },
            measurements = list(tb, "measurements") { o ->
                Measurement(id = o.long("id"), timestamp = o.long("timestamp"), type = o.str("type"), value = o.dbl("value"), value2 = o.dblN("value2"), tag = o.str("tag"), notes = o.str("notes"))
            },
            dailyLogs = list(tb, "daily_logs") { o ->
                DailyLog(
                    epochDay = o.long("epochDay"), mood = o.intN("mood"), energy = o.intN("energy"), stress = o.intN("stress"),
                    sleepHours = o.dblN("sleepHours"), waterGlasses = o.intN("waterGlasses"), exerciseMin = o.intN("exerciseMin"), notes = o.str("notes"),
                )
            },
            studies = list(tb, "studies") { o ->
                Study(
                    id = o.long("id"), epochDay = o.long("epochDay"), title = o.str("title"), category = o.str("category", "lab"), place = o.str("place"),
                    doctor = o.str("doctor"), status = o.str("status"), summary = o.str("summary"), followUpEpochDay = o.longN("followUpEpochDay"),
                )
            },
            studyResults = list(tb, "study_results") { o ->
                StudyResult(
                    id = o.long("id"), studyId = o.long("studyId"), name = o.str("name"), value = o.dblN("value"), textValue = o.str("textValue"),
                    unit = o.str("unit"), refMin = o.dblN("refMin"), refMax = o.dblN("refMax"), notes = o.str("notes"),
                )
            },
            studyAttachments = list(tb, "study_attachments") { o ->
                StudyAttachment(id = o.long("id"), studyId = o.long("studyId"), fileName = o.str("fileName"), displayName = o.str("displayName"), mime = o.str("mime"))
            },
            cycleDays = list(tb, "cycle_days") { o ->
                CycleDay(
                    epochDay = o.long("epochDay"), flow = o.int("flow"), symptoms = o.str("symptoms"), moods = o.str("moods"), mucus = o.str("mucus"),
                    basalTemp = o.dblN("basalTemp"), sex = o.int("sex"), ovulationTest = o.int("ovulationTest"), pregnancyTest = o.int("pregnancyTest"),
                    contraceptive = o.bool("contraceptive"), notes = o.str("notes"),
                )
            },
            medications = list(tb, "medications") { o ->
                Medication(
                    id = o.long("id"), name = o.str("name"), dose = o.str("dose"), instructions = o.str("instructions"), times = o.str("times"),
                    daysMask = o.int("daysMask", 127), startEpochDay = o.long("startEpochDay"), endEpochDay = o.longN("endEpochDay"),
                    active = o.bool("active", true), remind = o.bool("remind", true), notes = o.str("notes"),
                )
            },
            medIntakes = list(tb, "med_intakes") { o ->
                MedIntake(id = o.long("id"), medicationId = o.long("medicationId"), epochDay = o.long("epochDay"), slot = o.str("slot"), timestamp = o.long("timestamp"), status = o.str("status", "taken"))
            },
            appointments = list(tb, "appointments") { o ->
                Appointment(
                    id = o.long("id"), timestamp = o.long("timestamp"), title = o.str("title"), doctor = o.str("doctor"), specialty = o.str("specialty"),
                    place = o.str("place"), notes = o.str("notes"), remind = o.bool("remind", true), done = o.bool("done"),
                )
            },
            selfExams = list(tb, "self_exams") { o ->
                SelfExam(id = o.long("id"), epochDay = o.long("epochDay"), type = o.str("type"), normal = o.bool("normal", true), notes = o.str("notes"))
            },
            vaccinations = list(tb, "vaccinations") { o ->
                Vaccination(
                    id = o.long("id"), epochDay = o.long("epochDay"), type = o.str("type", "vacuna"), name = o.str("name"), dose = o.str("dose"),
                    nextDueEpochDay = o.longN("nextDueEpochDay"), place = o.str("place"), lot = o.str("lot"), notes = o.str("notes"), remind = o.bool("remind", true),
                )
            },
        )
        val prefs = LinkedHashMap<String, Any?>()
        root.optJSONObject("prefs")?.let { p ->
            for (k in p.keys()) prefs[k] = if (p.isNull(k)) null else p.get(k)
        }
        val info = BackupInfo(
            createdAtMillis = root.optLong("createdAtMillis", 0L),
            appVersion = root.optString("appVersion", ""),
            recordCount = tables.recordCount,
            attachmentCount = tables.studyAttachments.size,
        )
        return Triple(tables, prefs, info)
    }

    // ------------------------------------------------------------------ leer archivos

    private fun isSafeName(name: String): Boolean =
        name.isNotBlank() && name != "." && name != ".." && name.none { it == '/' || it == '\\' || it == '\u0000' }

    /** Un perfil dentro de un archivo de respaldo ya extraído a una carpeta temporal. */
    internal class ArchivedProfile(
        val originalId: Int,
        val tables: BackupTables,
        val prefs: Map<String, Any?>,
        val info: BackupInfo,
        /** Carpeta temporal con sus adjuntos. */
        val filesDir: File,
    ) {
        val summary: BackupProfileSummary
            get() = BackupProfileSummary(
                originalId = originalId,
                name = tables.profile?.name ?: "",
                kind = tables.profile?.kind ?: "person",
                species = tables.profile?.species ?: "",
                recordCount = tables.recordCount,
                attachmentCount = filesDir.listFiles()?.count { it.isFile } ?: 0,
            )
    }

    /** Archivo de respaldo ya leído. Hay que cerrarlo para borrar los temporales. */
    internal class Archive(
        val kind: BackupKind,
        val profiles: List<ArchivedProfile>,
        val createdAtMillis: Long,
        val appVersion: String,
        val globalPrefs: Map<String, Any?>,
        private val tmp: File,
    ) : Closeable {
        fun summary(): BackupSummary = BackupSummary(kind, createdAtMillis, appVersion, profiles.map { it.summary })

        override fun close() {
            tmp.deleteRecursively()
        }
    }

    private fun parseJson(text: String): JSONObject = try {
        JSONObject(text)
    } catch (e: JSONException) {
        throw BackupException("El respaldo está dañado y no se pudo leer.", e)
    }

    private fun jsonMap(o: JSONObject?): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        if (o != null) for (k in o.keys()) out[k] = if (o.isNull(k)) null else o.get(k)
        return out
    }

    /**
     * Lee un archivo de respaldo (de un perfil —incluidos los de Savia 1.0— o completo) y lo deja listo para
     * aplicarse. Lanza [BackupException] con un mensaje claro si el archivo no sirve.
     */
    internal fun open(context: Context, input: InputStream): Archive {
        val tmp = File(File(context.cacheDir, "restore_tmp"), System.nanoTime().toString(36))
        tmp.mkdirs()
        try {
            // 1) Copia local del archivo (el flujo de entrada solo se puede leer una vez).
            val source = File(tmp, "input.bin")
            try {
                source.outputStream().use { input.copyTo(it) }
            } catch (e: Exception) {
                throw BackupException("No se pudo leer el archivo.", e)
            }
            if (source.length() < 2) throw BackupException("El archivo está vacío.")
            val head = ByteArray(2)
            source.inputStream().use { it.read(head) }

            fun filesDir(profileId: Int?): File = File(tmp, if (profileId == null) "files" else "p$profileId/files").apply { mkdirs() }

            var single: String? = null
            var manifest: String? = null
            val perProfile = LinkedHashMap<Int, String>()
            if (head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) {
                try {
                    ZipInputStream(source.inputStream().buffered()).use { zis ->
                        while (true) {
                            val e = zis.nextEntry ?: break
                            val name = e.name
                            when {
                                e.isDirectory -> {}
                                name == DATA_ENTRY -> single = zis.readBytes().toString(Charsets.UTF_8)
                                name == ALL_ENTRY -> manifest = zis.readBytes().toString(Charsets.UTF_8)
                                name.startsWith(ATTACHMENTS_PREFIX) -> {
                                    val fname = name.removePrefix(ATTACHMENTS_PREFIX)
                                    if (isSafeName(fname)) File(filesDir(null), fname).outputStream().use { zis.copyTo(it) }
                                }
                                name.startsWith(ALL_PREFIX) -> {
                                    val rest = name.removePrefix(ALL_PREFIX)
                                    val slash = rest.indexOf('/')
                                    val id = if (slash > 0) rest.substring(0, slash).toIntOrNull() else null
                                    val sub = if (slash > 0) rest.substring(slash + 1) else ""
                                    if (id != null && id > 0) {
                                        if (sub == DATA_ENTRY) {
                                            perProfile[id] = zis.readBytes().toString(Charsets.UTF_8)
                                        } else if (sub.startsWith(ATTACHMENTS_PREFIX)) {
                                            val fname = sub.removePrefix(ATTACHMENTS_PREFIX)
                                            if (isSafeName(fname)) File(filesDir(id), fname).outputStream().use { zis.copyTo(it) }
                                        }
                                    }
                                }
                            }
                            zis.closeEntry()
                        }
                    }
                } catch (e: BackupException) {
                    throw e
                } catch (e: Exception) {
                    throw BackupException("El archivo está dañado y no se pudo abrir.", e)
                }
            } else {
                // También se acepta el data.json suelto.
                val text = source.readText(Charsets.UTF_8).trimStart('﻿', ' ', '\n', '\r', '\t')
                if (!text.startsWith("{")) throw BackupException("Este archivo no es un respaldo de Savia.")
                single = text
            }
            source.delete()

            val manifestText = manifest
            if (manifestText != null) {
                // 2a) Respaldo completo
                val root = parseJson(manifestText)
                if (root.optString("format") != ALL_FORMAT) throw BackupException("Este archivo no es un respaldo de Savia.")
                val version = root.optInt("formatVersion", 0)
                if (version < 1) throw BackupException("El respaldo está dañado o incompleto.")
                if (version > ALL_FORMAT_VERSION) {
                    throw BackupException("Este respaldo se creó con una versión más nueva de Savia. Actualiza la app para poder restaurarlo.")
                }
                val listed = ArrayList<Int>()
                root.optJSONArray("profiles")?.let { arr ->
                    for (i in 0 until arr.length()) arr.optJSONObject(i)?.optInt("id", 0)?.takeIf { it > 0 }?.let { listed += it }
                }
                val ids = listed.distinct().ifEmpty { perProfile.keys.sorted() }
                if (ids.isEmpty()) throw BackupException("El respaldo está dañado: no contiene perfiles.")
                val profiles = ids.map { id ->
                    val text = perProfile[id] ?: throw BackupException("El respaldo está incompleto: falta la información de uno de los perfiles.")
                    val (tables, prefs, info) = parse(parseJson(text))
                    ArchivedProfile(id, tables, prefs, info, filesDir(id))
                }
                return Archive(
                    BackupKind.ALL, profiles, root.optLong("createdAtMillis", 0L), root.optString("appVersion", ""),
                    jsonMap(root.optJSONObject("globalPrefs")), tmp,
                )
            }
            // 2b) Respaldo de un perfil
            val text = single ?: throw BackupException("Este archivo no es un respaldo de Savia.")
            val (tables, prefs, info) = parse(parseJson(text))
            return Archive(BackupKind.PROFILE, listOf(ArchivedProfile(1, tables, prefs, info, filesDir(null))), info.createdAtMillis, info.appVersion, emptyMap(), tmp)
        } catch (e: Throwable) {
            tmp.deleteRecursively()
            throw e
        }
    }

    /** Dice qué trae un archivo de respaldo (tipo, perfiles, cuántos registros) sin cambiar nada. */
    suspend fun inspect(context: Context, input: InputStream): BackupSummary = open(context, input).use { it.summary() }

    // ------------------------------------------------------------------ restaurar

    private fun reschedule(context: Context) {
        try {
            ReminderScheduler.rescheduleAll(context.applicationContext)
        } catch (_: Exception) {
            // Los recordatorios se reprograman de todos modos al abrir la app.
        }
    }

    /**
     * Restaura el respaldo de UN perfil sobre el perfil dueño de [db] (por defecto, el activo).
     * Llamar fuera del hilo principal.
     *
     * - [RestoreMode.REPLACE]: borra lo actual de ese perfil y deja exactamente lo del respaldo (antes guarda
     *   una copia interna en [safetyFile]).
     * - [RestoreMode.MERGE]: conserva lo actual y agrega lo que falte. Los días del ciclo y de bienestar que
     *   existan en ambos lados quedan como en el respaldo; los demás registros idénticos se omiten.
     *
     * Para un respaldo completo (varios perfiles) usa [restoreAll].
     */
    suspend fun restore(
        context: Context,
        input: InputStream,
        mode: RestoreMode,
        db: SaviaDb = Savia.db,
        prefs: Prefs = Savia.prefs,
    ): RestoreResult = restore(context, input, mode, BackupTarget.forDb(db, prefs))

    suspend fun restore(context: Context, input: InputStream, mode: RestoreMode, target: BackupTarget): RestoreResult {
        open(context, input).use { a ->
            if (a.kind == BackupKind.ALL) {
                throw BackupException("Este archivo es un respaldo completo con varios perfiles; no se puede cargar sobre un solo perfil.")
            }
            val result = applyProfile(context, a.profiles[0], mode, target, safetyCopy = true, importGlobalPrefs = target.profileId == 1)
            reschedule(context)
            return result
        }
    }

    /**
     * Carga el respaldo de un perfil como PERFIL NUEVO (no toca ninguno de los actuales).
     * El id del perfil creado viene en [RestoreResult.newProfileId]; no se activa.
     */
    suspend fun restoreAsNewProfile(context: Context, input: InputStream): RestoreResult {
        open(context, input).use { a ->
            if (a.kind == BackupKind.ALL) {
                throw BackupException("Este archivo es un respaldo completo con varios perfiles; cárgalo con la opción de agregar todos los perfiles.")
            }
            val result = addAsNewProfile(context, a.profiles[0])
            reschedule(context)
            return result
        }
    }

    /**
     * Carga un respaldo completo (también acepta el de un solo perfil).
     *
     * - [RestoreAllMode.ADD_AS_NEW]: cada perfil del respaldo entra como perfil nuevo; no se toca nada.
     * - [RestoreAllMode.REPLACE]: el perfil principal del respaldo (el que tenía id 1) reemplaza al perfil 1
     *   y los demás se agregan como nuevos. Antes se guarda una copia interna de todo ([fullSafetyFile]).
     *   Con [removeOtherProfiles] además se eliminan los otros perfiles que había en el teléfono, de modo que
     *   quede exactamente lo del respaldo.
     */
    suspend fun restoreAll(
        context: Context,
        input: InputStream,
        mode: RestoreAllMode,
        removeOtherProfiles: Boolean = false,
    ): RestoreAllResult {
        open(context, input).use { a ->
            val existingOthers = Savia.profileIds.filter { it != 1 }
            val newIds = ArrayList<Int>()
            var replaced = 0
            var removed = 0
            var records = 0
            var attachments = 0
            suspend fun addNew(ap: ArchivedProfile) {
                val r = addAsNewProfile(context, ap)
                r.newProfileId?.let { newIds += it }
                records += r.added
                attachments += r.attachmentsRestored
            }
            when (mode) {
                RestoreAllMode.ADD_AS_NEW -> for (ap in a.profiles) addNew(ap)
                RestoreAllMode.REPLACE -> {
                    val copied = saveFullSafetyCopy(context)
                    val willRemove = removeOtherProfiles && existingOthers.isNotEmpty()
                    if (willRemove && !copied) {
                        throw BackupException("No se pudo guardar una copia de seguridad de tus perfiles actuales, así que no se cambió nada. Intenta de nuevo sin eliminar los otros perfiles.")
                    }
                    val main = a.profiles.firstOrNull { it.originalId == 1 } ?: a.profiles.first()
                    val currentKind = Savia.dbFor(1).profile().get()?.kind ?: "person"
                    val mainKind = main.tables.profile?.kind ?: "person"
                    for (ap in a.profiles) {
                        if (ap === main && mainKind == currentKind) {
                            val r = applyProfile(context, ap, RestoreMode.REPLACE, BackupTarget.of(1), safetyCopy = true, importGlobalPrefs = true)
                            replaced = 1
                            records += r.added
                            attachments += r.attachmentsRestored
                        } else {
                            addNew(ap)
                        }
                    }
                    val global = a.globalPrefs.filterKeys { it in GLOBAL_PREFS }
                    if (global.isNotEmpty()) Savia.prefsFor(1).import(global)
                    // Solo al final, cuando todo lo del respaldo ya quedó cargado.
                    if (willRemove) {
                        for (id in existingOthers) {
                            Savia.deleteProfile(id)
                            removed++
                        }
                    }
                }
            }
            reschedule(context)
            return RestoreAllResult(mode, replaced, newIds, removed, records, attachments)
        }
    }

    private suspend fun addAsNewProfile(context: Context, ap: ArchivedProfile): RestoreResult {
        val base = (ap.tables.profile ?: Profile()).copy(id = 1)
        val id = Savia.createProfile(base)
        try {
            val r = applyProfile(context, ap, RestoreMode.REPLACE, BackupTarget.of(id), safetyCopy = false, importGlobalPrefs = false)
            Savia.loadInfo(id)
            return RestoreResult(r.mode, r.info, r.added, 0, 0, r.attachmentsRestored, newProfileId = id, profileName = base.name.trim())
        } catch (e: Throwable) {
            runCatching { Savia.deleteProfile(id) }
            throw e
        }
    }

    /** Aplica un perfil del respaldo sobre [target]. */
    private suspend fun applyProfile(
        context: Context,
        ap: ArchivedProfile,
        mode: RestoreMode,
        target: BackupTarget,
        safetyCopy: Boolean,
        /** También restaura los ajustes globales que traiga el respaldo (tema). Solo para el perfil principal. */
        importGlobalPrefs: Boolean,
    ): RestoreResult {
        val db = target.db
        val prefs = target.prefs
        val tables = ap.tables
        val attDir = target.attachmentsDir.apply { mkdirs() }
        val backupFiles = ap.filesDir.listFiles()?.filter { it.isFile } ?: emptyList()
        val name = tables.profile?.name?.trim().orEmpty()
        return when (mode) {
            RestoreMode.REPLACE -> {
                if (safetyCopy) saveSafetyCopy(context, target)
                replaceAll(db, tables)
                // Adjuntos: quedan exactamente los del respaldo. (Solo archivos: dentro de la carpeta del
                // perfil 1 viven las subcarpetas de los demás perfiles.)
                attDir.listFiles()?.forEach { if (it.isFile) it.delete() }
                var n = 0
                for (f in backupFiles) {
                    f.copyTo(File(attDir, f.name), overwrite = true)
                    n++
                }
                val scoped = prefs.profileScoped.mapTo(HashSet()) { it.key }
                prefs.import(ap.prefs.filterKeys { it !in PREFS_NOT_RESTORED && (importGlobalPrefs || it in scoped) })
                RestoreResult(mode, ap.info, added = tables.recordCount, updated = 0, skipped = 0, attachmentsRestored = n, profileName = name)
            }
            RestoreMode.MERGE -> {
                // Si un adjunto del respaldo choca con uno distinto ya existente, se guarda con otro nombre.
                val rename = HashMap<String, String>()
                val toCopy = ArrayList<Pair<File, File>>()
                val stamp = System.currentTimeMillis().toString(36)
                for (f in backupFiles) {
                    val dest = File(attDir, f.name)
                    if (!dest.exists()) {
                        toCopy += f to dest
                    } else if (dest.length() != f.length()) {
                        val newName = "r${stamp}_${f.name}"
                        rename[f.name] = newName
                        toCopy += f to File(attDir, newName)
                    }
                }
                val counts = merge(db, tables, rename)
                toCopy.forEach { (from, to) -> from.copyTo(to, overwrite = true) }
                if (tables.cycleDays.isNotEmpty() && ap.prefs["cycle_enabled"] != false) prefs.cycleEnabled.value = true
                RestoreResult(mode, ap.info, added = counts[0], updated = counts[1], skipped = counts[2], attachmentsRestored = toCopy.size, profileName = name)
            }
        }
    }

    /** Guarda una copia interna de los datos actuales de un perfil (si hay algo que guardar). */
    private suspend fun saveSafetyCopy(context: Context, target: BackupTarget) {
        val current = BackupTables.load(target.db)
        if (current.recordCount == 0 && (current.profile == null || current.profile.copy(kind = "person", avatarColor = 0) == Profile())) return
        try {
            val f = safetyFile(context, target.profileId)
            f.parentFile?.mkdirs()
            val part = File(f.parentFile, f.name + ".tmp")
            part.outputStream().use { write(context, it, target) }
            if (f.exists()) f.delete()
            part.renameTo(f)
        } catch (_: Exception) {
            // Es solo una red de seguridad; si falla no se bloquea la restauración.
        }
    }

    /**
     * Guarda una copia interna de TODOS los perfiles. Devuelve false si había algo que guardar y no se pudo.
     */
    private suspend fun saveFullSafetyCopy(context: Context): Boolean {
        return try {
            if (Savia.profileIds == listOf(1)) {
                val current = BackupTables.load(Savia.dbFor(1))
                if (current.recordCount == 0 && (current.profile == null || current.profile == Profile())) return true
            }
            val f = fullSafetyFile(context)
            f.parentFile?.mkdirs()
            val part = File(f.parentFile, f.name + ".tmp")
            part.outputStream().use { writeAll(context, it) }
            if (f.exists()) f.delete()
            part.renameTo(f)
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun replaceAll(db: SaviaDb, t: BackupTables) {
        val studyIds = t.studies.map { it.id }.toSet()
        val medIds = t.medications.map { it.id }.toSet()
        db.withTransaction {
            db.medications().clearIntakes()
            db.medications().clear()
            db.studies().clearAttachments()
            db.studies().clearResults()
            db.studies().clear()
            db.symptoms().clear()
            db.measurements().clear()
            db.dailyLogs().clear()
            db.cycle().clear()
            db.appointments().clear()
            db.selfExams().clear()
            db.vaccinations().clear()
            db.profile().save((t.profile ?: Profile()).copy(id = 1))

            db.symptoms().insertAll(t.symptoms)
            db.measurements().insertAll(t.measurements)
            db.dailyLogs().insertAll(t.dailyLogs)
            db.studies().insertAll(t.studies)
            db.studies().insertResults(t.studyResults.filter { it.studyId in studyIds })
            db.studies().insertAttachments(t.studyAttachments.filter { it.studyId in studyIds })
            db.cycle().insertAll(t.cycleDays)
            db.medications().insertAll(t.medications)
            db.medications().insertIntakes(t.medIntakes.filter { it.medicationId in medIds })
            db.appointments().insertAll(t.appointments)
            db.selfExams().insertAll(t.selfExams)
            db.vaccinations().insertAll(t.vaccinations)
        }
    }

    /** Perfil combinado: se conserva lo actual y se rellenan los huecos con el respaldo. */
    private fun mergeProfile(cur: Profile, b: Profile): Profile = cur.copy(
        name = cur.name.ifBlank { b.name },
        sex = if (cur.sex == "X") b.sex else cur.sex,
        birthEpochDay = cur.birthEpochDay ?: b.birthEpochDay,
        heightCm = cur.heightCm ?: b.heightCm,
        goalWeightKg = cur.goalWeightKg ?: b.goalWeightKg,
        bloodType = cur.bloodType.ifBlank { b.bloodType },
        allergies = cur.allergies.ifBlank { b.allergies },
        conditions = cur.conditions.ifBlank { b.conditions },
        emergencyName = cur.emergencyName.ifBlank { b.emergencyName },
        emergencyPhone = cur.emergencyPhone.ifBlank { b.emergencyPhone },
        doctorName = cur.doctorName.ifBlank { b.doctorName },
        doctorPhone = cur.doctorPhone.ifBlank { b.doctorPhone },
        insurance = cur.insurance.ifBlank { b.insurance },
        notes = cur.notes.ifBlank { b.notes },
        // El tipo de perfil (persona/mascota) nunca cambia al combinar.
        species = cur.species.ifBlank { b.species },
        breed = cur.breed.ifBlank { b.breed },
        sterilized = cur.sterilized ?: b.sterilized,
        microchip = cur.microchip.ifBlank { b.microchip },
        markings = cur.markings.ifBlank { b.markings },
        relation = cur.relation.ifBlank { b.relation },
    )

    /** @return [agregados, actualizados, omitidos] */
    private suspend fun merge(db: SaviaDb, t: BackupTables, renamedFiles: Map<String, String>): IntArray {
        var added = 0
        var updated = 0
        var skipped = 0
        db.withTransaction {
            // Perfil
            t.profile?.let { b ->
                val cur = db.profile().get()
                db.profile().save((if (cur == null) b else mergeProfile(cur, b)).copy(id = 1))
            }

            // Tablas "una fila por día": gana el respaldo en los días que trae.
            val curCycle = db.cycle().getAll().associateBy { it.epochDay }
            for (d in t.cycleDays) {
                when (curCycle[d.epochDay]) {
                    null -> added++
                    d -> skipped++
                    else -> updated++
                }
            }
            db.cycle().insertAll(t.cycleDays)
            val curLogs = db.dailyLogs().getAll().associateBy { it.epochDay }
            for (d in t.dailyLogs) {
                when (curLogs[d.epochDay]) {
                    null -> added++
                    d -> skipped++
                    else -> updated++
                }
            }
            db.dailyLogs().insertAll(t.dailyLogs)

            // Tablas simples: se omiten duplicados exactos, lo demás entra con id nuevo.
            val symSeen = db.symptoms().getAll().mapTo(HashSet()) { it.copy(id = 0) }
            val symNew = t.symptoms.map { it.copy(id = 0) }.filter { symSeen.add(it) }
            db.symptoms().insertAll(symNew)
            added += symNew.size; skipped += t.symptoms.size - symNew.size

            val meaSeen = db.measurements().getAll().mapTo(HashSet()) { it.copy(id = 0) }
            val meaNew = t.measurements.map { it.copy(id = 0) }.filter { meaSeen.add(it) }
            db.measurements().insertAll(meaNew)
            added += meaNew.size; skipped += t.measurements.size - meaNew.size

            val appSeen = db.appointments().getAll().mapTo(HashSet()) { it.copy(id = 0) }
            val appNew = t.appointments.map { it.copy(id = 0) }.filter { appSeen.add(it) }
            db.appointments().insertAll(appNew)
            added += appNew.size; skipped += t.appointments.size - appNew.size

            val exSeen = db.selfExams().getAll().mapTo(HashSet()) { it.copy(id = 0) }
            val exNew = t.selfExams.map { it.copy(id = 0) }.filter { exSeen.add(it) }
            db.selfExams().insertAll(exNew)
            added += exNew.size; skipped += t.selfExams.size - exNew.size

            val vacSeen = db.vaccinations().getAll().mapTo(HashSet()) { it.copy(id = 0) }
            val vacNew = t.vaccinations.map { it.copy(id = 0) }.filter { vacSeen.add(it) }
            db.vaccinations().insertAll(vacNew)
            added += vacNew.size; skipped += t.vaccinations.size - vacNew.size

            // Estudios (con resultados y adjuntos): se remapean las llaves foráneas.
            val curStudies = HashMap<Study, Long>()
            db.studies().getAll().forEach { curStudies[it.copy(id = 0)] = it.id }
            val studyMap = HashMap<Long, Long>()
            for (s in t.studies) {
                val existing = curStudies[s.copy(id = 0)]
                if (existing != null) {
                    studyMap[s.id] = existing
                    skipped++
                } else {
                    studyMap[s.id] = db.studies().insert(s.copy(id = 0))
                    added++
                }
            }
            val resSeen = db.studies().getAllResults().mapTo(HashSet()) { it.copy(id = 0) }
            for (r in t.studyResults) {
                val sid = studyMap[r.studyId] ?: continue
                val key = r.copy(id = 0, studyId = sid)
                if (resSeen.add(key)) {
                    db.studies().insertResult(key)
                    added++
                } else {
                    skipped++
                }
            }
            val attSeen = db.studies().getAllAttachments().mapTo(HashSet()) { it.copy(id = 0) }
            for (a in t.studyAttachments) {
                val sid = studyMap[a.studyId] ?: continue
                val key = a.copy(id = 0, studyId = sid, fileName = renamedFiles[a.fileName] ?: a.fileName)
                if (attSeen.add(key)) db.studies().insertAttachment(key)
            }

            // Medicamentos y tomas
            val curMeds = HashMap<Medication, Long>()
            db.medications().getAll().forEach { curMeds[it.copy(id = 0)] = it.id }
            val medMap = HashMap<Long, Long>()
            for (m in t.medications) {
                val existing = curMeds[m.copy(id = 0)]
                if (existing != null) {
                    medMap[m.id] = existing
                    skipped++
                } else {
                    medMap[m.id] = db.medications().insert(m.copy(id = 0))
                    added++
                }
            }
            val intSeen = db.medications().getAllIntakes().mapTo(HashSet()) { it.copy(id = 0) }
            for (i in t.medIntakes) {
                val mid = medMap[i.medicationId] ?: continue
                val key = i.copy(id = 0, medicationId = mid)
                if (intSeen.add(key)) {
                    db.medications().insertIntake(key)
                    added++
                } else {
                    skipped++
                }
            }
        }
        return intArrayOf(added, updated, skipped)
    }
}
