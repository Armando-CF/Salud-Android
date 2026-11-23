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
import app.savia.reminders.ReminderScheduler
import app.savia.util.Fmt
import app.savia.util.nowMillis
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Error con mensaje listo para mostrarse a la persona usuaria. */
class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Cómo aplicar un respaldo sobre los datos actuales. */
enum class RestoreMode { REPLACE, MERGE }

/** Contenido completo de la base de datos (todas las tablas, sin filtrar). */
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
) {
    /** Total de registros (sin contar el perfil). */
    val recordCount: Int
        get() = symptoms.size + measurements.size + dailyLogs.size + studies.size + studyResults.size + cycleDays.size +
            medications.size + medIntakes.size + appointments.size + selfExams.size

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
        )
    }
}

/** Datos generales de un respaldo. */
class BackupInfo(
    val createdAtMillis: Long,
    val appVersion: String,
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
)

/**
 * Respaldo completo de Savia: un .zip con `data.json` (todas las tablas + ajustes) y la carpeta
 * `attachments/` con los archivos adjuntos de los estudios. Es el único formato que se puede restaurar.
 *
 * El formato está versionado ([FORMAT_VERSION]); al leer se toleran campos faltantes o desconocidos
 * para que respaldos viejos sigan sirviendo en versiones futuras.
 */
object Backup {
    const val FORMAT = "savia-backup"
    const val FORMAT_VERSION = 1
    const val DATA_ENTRY = "data.json"
    const val ATTACHMENTS_PREFIX = "attachments/"
    const val MIME = "application/zip"

    /** Ajustes que no se restauran (dependen del teléfono actual). */
    private val PREFS_NOT_RESTORED = setOf("app_lock")

    fun attachmentsDir(context: Context): File = File(context.filesDir, "attachments")

    /** Copia interna que se guarda antes de un "Reemplazar todo", por si hay que deshacer. */
    fun safetyFile(context: Context): File = File(File(context.filesDir, "safety"), "antes-de-restaurar.zip")

    // ------------------------------------------------------------------ escribir

    /** Escribe el respaldo completo. Llamar fuera del hilo principal. */
    suspend fun write(context: Context, out: OutputStream, db: SaviaDb = Savia.db, prefs: Prefs = Savia.prefs): BackupInfo {
        val tables = BackupTables.load(db)
        val now = nowMillis()
        val json = toJson(tables, prefs.export(), now)
        val dir = attachmentsDir(context)
        val files = tables.studyAttachments.map { it.fileName }.distinct()
            .filter { isSafeName(it) }
            .map { File(dir, it) }
            .filter { it.isFile }
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(DATA_ENTRY))
            zip.write(json.toString(1).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            for (f in files) {
                zip.putNextEntry(ZipEntry(ATTACHMENTS_PREFIX + f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return BackupInfo(now, BuildConfig.VERSION_NAME, tables.recordCount, files.size)
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
        root.put("tables", tb)
        return root
    }

    private fun profileToJson(p: Profile): JSONObject = JSONObject()
        .put("name", p.name).put("sex", p.sex).putN("birthEpochDay", p.birthEpochDay).putN("heightCm", p.heightCm)
        .putN("goalWeightKg", p.goalWeightKg).put("bloodType", p.bloodType).put("allergies", p.allergies)
        .put("conditions", p.conditions).put("emergencyName", p.emergencyName).put("emergencyPhone", p.emergencyPhone)
        .put("doctorName", p.doctorName).put("doctorPhone", p.doctorPhone).put("insurance", p.insurance).put("notes", p.notes)

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

    // ------------------------------------------------------------------ restaurar

    private fun isSafeName(name: String): Boolean =
        name.isNotBlank() && name != "." && name != ".." && name.none { it == '/' || it == '\\' || it == '\u0000' }

    /**
     * Restaura un respaldo. Llamar fuera del hilo principal.
     *
     * - [RestoreMode.REPLACE]: borra todo lo actual y deja exactamente lo del respaldo (antes guarda una
     *   copia interna en [safetyFile]).
     * - [RestoreMode.MERGE]: conserva lo actual y agrega lo que falte. Los días del ciclo y de bienestar que
     *   existan en ambos lados quedan como en el respaldo; los demás registros idénticos se omiten.
     */
    suspend fun restore(
        context: Context,
        input: InputStream,
        mode: RestoreMode,
        db: SaviaDb = Savia.db,
        prefs: Prefs = Savia.prefs,
    ): RestoreResult {
        val tmp = File(context.cacheDir, "restore_tmp")
        tmp.deleteRecursively()
        val tmpFiles = File(tmp, "files").apply { mkdirs() }
        try {
            // 1) Copia local del archivo (el flujo de entrada solo se puede leer una vez).
            val source = File(tmp, "input.bin")
            try {
                source.outputStream().use { input.copyTo(it) }
            } catch (e: Exception) {
                throw BackupException("No se pudo leer el archivo.", e)
            }
            if (source.length() < 2) throw BackupException("El archivo está vacío.")

            // 2) Extraer data.json y adjuntos
            val head = ByteArray(2)
            source.inputStream().use { it.read(head) }
            val jsonText: String
            if (head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) {
                var text: String? = null
                try {
                    ZipInputStream(source.inputStream().buffered()).use { zis ->
                        while (true) {
                            val e = zis.nextEntry ?: break
                            val name = e.name
                            when {
                                e.isDirectory -> {}
                                name == DATA_ENTRY -> text = zis.readBytes().toString(Charsets.UTF_8)
                                name.startsWith(ATTACHMENTS_PREFIX) -> {
                                    val fname = name.removePrefix(ATTACHMENTS_PREFIX)
                                    if (isSafeName(fname)) File(tmpFiles, fname).outputStream().use { zis.copyTo(it) }
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
                jsonText = text ?: throw BackupException("Este archivo no es un respaldo de Savia.")
            } else {
                // También se acepta el data.json suelto.
                jsonText = source.readText(Charsets.UTF_8).trimStart('﻿', ' ', '\n', '\r', '\t')
                if (!jsonText.startsWith("{")) throw BackupException("Este archivo no es un respaldo de Savia.")
            }
            val root = try {
                JSONObject(jsonText)
            } catch (e: JSONException) {
                throw BackupException("El respaldo está dañado y no se pudo leer.", e)
            }
            val (tables, backupPrefs, info) = parse(root)

            // 3) Aplicar
            val attDir = attachmentsDir(context).apply { mkdirs() }
            val result = when (mode) {
                RestoreMode.REPLACE -> {
                    saveSafetyCopy(context, db, prefs)
                    replaceAll(db, tables)
                    // Adjuntos: quedan exactamente los del respaldo.
                    attDir.listFiles()?.forEach { it.delete() }
                    var n = 0
                    tmpFiles.listFiles()?.forEach { f ->
                        f.copyTo(File(attDir, f.name), overwrite = true)
                        n++
                    }
                    prefs.import(backupPrefs.filterKeys { it !in PREFS_NOT_RESTORED })
                    RestoreResult(mode, info, added = tables.recordCount, updated = 0, skipped = 0, attachmentsRestored = n)
                }
                RestoreMode.MERGE -> {
                    // Si un adjunto del respaldo choca con uno distinto ya existente, se guarda con otro nombre.
                    val rename = HashMap<String, String>()
                    val toCopy = ArrayList<Pair<File, File>>()
                    val stamp = System.currentTimeMillis().toString(36)
                    tmpFiles.listFiles()?.forEach { f ->
                        val target = File(attDir, f.name)
                        if (!target.exists()) {
                            toCopy += f to target
                        } else if (target.length() != f.length()) {
                            val newName = "r${stamp}_${f.name}"
                            rename[f.name] = newName
                            toCopy += f to File(attDir, newName)
                        }
                    }
                    val counts = merge(db, tables, rename)
                    toCopy.forEach { (from, to) -> from.copyTo(to, overwrite = true) }
                    if (tables.cycleDays.isNotEmpty() && backupPrefs["cycle_enabled"] != false) prefs.cycleEnabled.value = true
                    RestoreResult(mode, info, added = counts[0], updated = counts[1], skipped = counts[2], attachmentsRestored = toCopy.size)
                }
            }
            try {
                ReminderScheduler.rescheduleAll(context.applicationContext)
            } catch (_: Exception) {
                // Los recordatorios se reprograman de todos modos al abrir la app.
            }
            return result
        } finally {
            tmp.deleteRecursively()
        }
    }

    /** Guarda una copia interna de los datos actuales (si hay algo que guardar). */
    private suspend fun saveSafetyCopy(context: Context, db: SaviaDb, prefs: Prefs) {
        val current = BackupTables.load(db)
        if (current.recordCount == 0 && (current.profile == null || current.profile == Profile())) return
        try {
            val f = safetyFile(context)
            f.parentFile?.mkdirs()
            val part = File(f.parentFile, f.name + ".tmp")
            part.outputStream().use { write(context, it, db, prefs) }
            if (f.exists()) f.delete()
            part.renameTo(f)
        } catch (_: Exception) {
            // Es solo una red de seguridad; si falla no se bloquea la restauración.
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
