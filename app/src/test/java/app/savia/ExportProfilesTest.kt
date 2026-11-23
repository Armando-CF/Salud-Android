package app.savia

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.savia.data.Appointment
import app.savia.data.CycleDay
import app.savia.data.Measurement
import app.savia.data.Medication
import app.savia.data.Profile
import app.savia.data.SaviaDb
import app.savia.data.Study
import app.savia.data.StudyAttachment
import app.savia.data.SymptomEntry
import app.savia.data.Vaccination
import app.savia.export.Backup
import app.savia.export.BackupException
import app.savia.export.BackupKind
import app.savia.export.BackupTables
import app.savia.export.ExportData
import app.savia.export.ExportFormat
import app.savia.export.ExportModule
import app.savia.export.ExportOptions
import app.savia.export.ExportTables
import app.savia.export.Exporter
import app.savia.export.JsonExportWriter
import app.savia.export.RestoreAllMode
import app.savia.export.RestoreMode
import app.savia.util.epochDayToMillis
import app.savia.util.today
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Exportación y respaldo con varios perfiles (personas y mascotas), vacunas y respaldos antiguos. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ExportProfilesTest {
    private lateinit var ctx: Context
    private val outDir = File("build/export-test/perfiles").apply { mkdirs() }
    private val t = today()
    private val petPhoto = ByteArray(3000) { (it * 7 % 253).toByte() }
    private val anaPdf = ByteArray(4000) { (it * 13 % 251).toByte() }

    private var kid = 0
    private var dog = 0

    /** Deja el teléfono "de fábrica": sin perfiles extra, sin ajustes, sin adjuntos y con base nueva. */
    private fun freshPhone() {
        if (Savia.isReady) for (id in Savia.profileIds.filter { it != 1 }) Savia.deleteProfile(id)
        for (n in listOf("savia_profiles", "savia_prefs", "savia_export")) ctx.getSharedPreferences(n, 0).edit().clear().commit()
        File(ctx.filesDir, "attachments").deleteRecursively()
        File(ctx.filesDir, "safety").deleteRecursively()
        Savia.init(ctx, SaviaDb.createInMemory(ctx))
    }

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        freshPhone()
    }

    /** Tres perfiles: Ana (persona, perfil 1), Leo (hijo) y Firulais (perro con adjunto y vacunas). */
    private fun seedFamily() = runBlocking {
        // Perfil 1: Ana
        val db1 = Savia.dbFor(1)
        db1.profile().save(Profile(name = "Ana", sex = "F", birthEpochDay = t - 12000, heightCm = 165.0, bloodType = "O+", allergies = "Penicilina", relation = "Yo", avatarColor = 2))
        db1.symptoms().insert(SymptomEntry(timestamp = epochDayToMillis(t - 3, 600), name = "Dolor de cabeza", intensity = 6))
        db1.measurements().insert(Measurement(timestamp = epochDayToMillis(t - 1, 480), type = "weight", value = 64.5))
        for (d in 0..3) db1.cycle().save(CycleDay(epochDay = t - 20 + d, flow = 3))
        val st = db1.studies().insert(Study(epochDay = t - 30, title = "Química sanguínea"))
        Savia.attachmentsDir(1).mkdirs()
        File(Savia.attachmentsDir(1), "ana_lab.pdf").writeBytes(anaPdf)
        db1.studies().insertAttachment(StudyAttachment(studyId = st, fileName = "ana_lab.pdf", displayName = "Laboratorio.pdf", mime = "application/pdf"))
        db1.vaccinations().insert(Vaccination(epochDay = t - 40, name = "Influenza", dose = "Anual", nextDueEpochDay = t + 325, place = "Centro de salud"))
        Savia.prefsFor(1).cycleEnabled.value = true
        Savia.prefsFor(1).cycleLength.value = 31
        Savia.prefs.themeMode.value = "dark"

        // Leo, hijo
        kid = Savia.createProfile(Profile(name = "Leo", sex = "M", birthEpochDay = t - 2500, relation = "Hijo", avatarColor = 4)) { it.atlasSex.value = "M" }
        val dbK = Savia.dbFor(kid)
        dbK.measurements().insert(Measurement(timestamp = epochDayToMillis(t - 5, 480), type = "height", value = 121.0))
        dbK.measurements().insert(Measurement(timestamp = epochDayToMillis(t - 5, 481), type = "weight", value = 23.4))
        dbK.vaccinations().insert(Vaccination(epochDay = t - 400, name = "SRP", dose = "Refuerzo", lot = "A12"))
        dbK.symptoms().insert(SymptomEntry(timestamp = epochDayToMillis(t - 2, 700), name = "Tos", intensity = 3))

        // Firulais, perro
        dog = Savia.createProfile(
            Profile(
                name = "Firulais", kind = "pet", species = "perro", breed = "Mestizo", sex = "M", birthEpochDay = t - 1500, sterilized = true,
                microchip = "985112345678903", markings = "Café con mancha blanca en el pecho", doctorName = "MVZ Laura Ortiz", doctorPhone = "55 1111 2222",
                allergies = "Pollo", goalWeightKg = 14.0, avatarColor = 6,
            ),
        )
        val dbD = Savia.dbFor(dog)
        for (i in 0..5) dbD.measurements().insert(Measurement(timestamp = epochDayToMillis(t - 150 + i * 30, 600), type = "weight", value = 13.0 + i * 0.25))
        dbD.measurements().insert(Measurement(timestamp = epochDayToMillis(t - 2, 600), type = "temp", value = 38.6))
        dbD.symptoms().insert(SymptomEntry(timestamp = epochDayToMillis(t - 9, 800), name = "Vómito", intensity = 5, regionId = "pet_abdomen", regionLabel = "Abdomen"))
        dbD.vaccinations().insert(Vaccination(epochDay = t - 200, type = "vacuna", name = "Rabia", dose = "Anual", nextDueEpochDay = t + 165, place = "Clínica Huellitas", lot = "RB-22", notes = "Sin reacción"))
        dbD.vaccinations().insert(Vaccination(epochDay = t - 100, type = "vacuna", name = "Múltiple (quíntuple)", nextDueEpochDay = t - 10, remind = false))
        dbD.vaccinations().insert(Vaccination(epochDay = t - 30, type = "desp_interna", name = "Desparasitante oral", nextDueEpochDay = t + 60))
        dbD.vaccinations().insert(Vaccination(epochDay = t - 20, type = "desp_externa", name = "Pipeta antipulgas"))
        dbD.medications().insert(Medication(name = "Omeprazol", dose = "10 mg", times = "08:00", startEpochDay = t - 9))
        dbD.appointments().insert(Appointment(timestamp = epochDayToMillis(t + 12, 660), title = "Revisión y refuerzo", doctor = "MVZ Laura Ortiz", place = "Clínica Huellitas"))
        val rx = dbD.studies().insert(Study(epochDay = t - 8, title = "Radiografía abdominal", category = "imagen", status = "normal"))
        Savia.attachmentsDir(dog).mkdirs()
        File(Savia.attachmentsDir(dog), "rx_firulais.jpg").writeBytes(petPhoto)
        dbD.studies().insertAttachment(StudyAttachment(studyId = rx, fileName = "rx_firulais.jpg", displayName = "Radiografía.jpg", mime = "image/jpeg"))
    }

    private fun tables(id: Int): BackupTables = runBlocking { BackupTables.load(Savia.dbFor(id)) }

    // ------------------------------------------------------------------ exportación de una mascota

    @Test
    fun petExportHasVaccinesAndPetFieldsAndSkipsHumanOnlyModules() {
        seedFamily()
        Savia.switchTo(dog)
        val data = runBlocking { ExportData.load(ExportOptions.ALL) }
        assertTrue(data.isPet)
        assertEquals("Firulais", data.profileName)
        // Ciclo, bienestar y autoexploración no aplican a una mascota.
        assertFalse(data.has(ExportModule.CICLO)); assertFalse(data.has(ExportModule.BIENESTAR)); assertFalse(data.has(ExportModule.AUTOEXPLORACION))
        assertTrue(data.has(ExportModule.VACUNAS))
        val all = ExportTables.build(data)
        assertEquals(listOf("Peso y mediciones", "Síntomas", "Estudios", "Resultados", "Vacunas", "Medicamentos", "Tomas", "Citas", "Perfil"), all.map { it.title })

        val vac = all.first { it.title == "Vacunas" }
        assertEquals(listOf("Fecha", "Tipo", "Nombre", "Dosis", "Próxima", "Lugar", "Lote", "Recordatorio", "Notas"), vac.columns.map { it.title })
        assertEquals(4, vac.rows.size)
        val rabia = vac.rows.first { it[2] == "Rabia" }
        assertEquals("Vacuna", rabia[1]); assertEquals("Anual", rabia[3]); assertEquals("Clínica Huellitas", rabia[5]); assertEquals("RB-22", rabia[6])
        assertEquals("Sí", rabia[7]); assertEquals("Sin reacción", rabia[8])
        assertTrue(vac.rows.any { it[1] == "Desparasitación interna" })
        assertTrue(vac.rows.any { it[1] == "Pulgas y garrapatas" && it[4] == null && it[7] == null })

        val pairs = ExportTables.profilePairs(data).toMap()
        assertEquals("Mascota", pairs["Tipo de perfil"]); assertEquals("Perro", pairs["Especie"]); assertEquals("Mestizo", pairs["Raza"])
        assertEquals("Macho", pairs["Sexo"]); assertEquals("Sí", pairs["Esterilizado"]); assertEquals("985112345678903", pairs["Microchip o placa"])
        assertEquals("MVZ Laura Ortiz", pairs["Veterinario"])
        assertFalse(pairs.containsKey("Tipo de sangre")); assertFalse(pairs.containsKey("Contacto de emergencia")); assertFalse(pairs.containsKey("Seguro médico"))
        assertEquals("Veterinario", all.first { it.title == "Citas" }.columns[2].title)

        // Con varios perfiles el nombre del archivo dice de quién es.
        assertEquals("savia-firulais-vacunas-1970-01-02.xlsx", Exporter.fileName(ExportFormat.XLSX, ExportOptions(setOf(ExportModule.VACUNAS)), 1))
        assertEquals("savia-firulais-datos-1970-01-02.pdf", Exporter.fileName(ExportFormat.PDF, ExportOptions.ALL, 1))
        assertEquals("savia-respaldo-firulais-1970-01-02.zip", Exporter.backupFileName(1))
        assertEquals("savia-respaldo-completo-1970-01-02.zip", Exporter.fullBackupFileName(1))
        assertEquals("savia-firulais-ficha-1970-01-02.pdf", Exporter.medicalCardFileName(1))
        // Pedir "solo ciclo" en una mascota no produce un zip vacío con extensión equivocada.
        assertEquals("csv", Exporter.extension(ExportFormat.CSV, ExportOptions(setOf(ExportModule.VACUNAS, ExportModule.CICLO))))

        // Excel válido con la hoja de vacunas
        val xlsx = File(outDir, Exporter.fileName(ExportFormat.XLSX, ExportOptions.ALL))
        runBlocking { xlsx.outputStream().use { Exporter.export(ExportFormat.XLSX, ExportOptions.ALL, it) } }
        ZipFile(xlsx).use { zip ->
            val wb = String(zip.getInputStream(zip.getEntry("xl/workbook.xml")).readBytes(), Charsets.UTF_8)
            assertTrue(wb.contains("name=\"Vacunas\""))
            assertFalse(wb.contains("Ciclo"))
        }
        val json = JsonExportWriter.build(data)
        assertEquals("Firulais", json.getJSONObject("savia").getString("perfil"))
        assertEquals("Mascota (perro)", json.getJSONObject("savia").getString("tipo_perfil"))
        assertEquals(4, json.getJSONObject("tablas").getJSONArray("Vacunas").length())

        // Persona: el perfil trae parentesco y el archivo su nombre.
        Savia.switchTo(kid)
        val kd = runBlocking { ExportData.load(ExportOptions.ALL) }
        assertFalse(kd.isPet)
        assertTrue(kd.has(ExportModule.CICLO))
        val kp = ExportTables.profilePairs(kd).toMap()
        assertEquals("Persona", kp["Tipo de perfil"]); assertEquals("Hijo", kp["Parentesco"])
        assertEquals("savia-leo-ciclo-1970-01-02.xlsx", Exporter.fileName(ExportFormat.XLSX, ExportOptions.CYCLE_ONLY, 1))
        assertEquals(1, ExportTables.vaccinations(kd).rows.size)
    }

    @Test
    fun vaccineRangeUsesApplicationOrNextDueDate() {
        seedFamily()
        Savia.switchTo(dog)
        val recent = runBlocking { ExportData.load(ExportOptions(setOf(ExportModule.VACUNAS), t - 35, t)) }
        // Aplicadas en el rango (desparasitante, pipeta) o con próxima fecha en el rango (múltiple).
        assertEquals(setOf("Desparasitante oral", "Pipeta antipulgas", "Múltiple (quíntuple)"), recent.vaccinations.map { it.name }.toSet())
        assertFalse(recent.isEmpty)
        val none = runBlocking { ExportData.load(ExportOptions(setOf(ExportModule.VACUNAS), t - 1000, t - 900)) }
        assertTrue(none.isEmpty)
    }

    /** Destino de páginas que dibuja en mapas de bits y los guarda como PNG para revisar la maquetación. */
    private class BitmapSink(private val dir: File, private val prefix: String) : app.savia.export.PageSink {
        var pages = 0
        private var bmp: android.graphics.Bitmap? = null
        private var number = 0

        override fun startPage(width: Int, height: Int, number: Int): android.graphics.Canvas {
            val b = android.graphics.Bitmap.createBitmap(width * 2, height * 2, android.graphics.Bitmap.Config.ARGB_8888)
            b.eraseColor(android.graphics.Color.WHITE)
            bmp = b
            this.number = number
            return android.graphics.Canvas(b).apply { scale(2f, 2f) }
        }

        override fun finishPage() {
            val b = bmp ?: return
            dir.mkdirs()
            File(dir, "$prefix-%02d.png".format(number)).outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            b.recycle()
            bmp = null
            pages++
        }

        override fun finish(out: java.io.OutputStream) {
            out.write("paginas=$pages".toByteArray())
        }
    }

    @Test
    fun petPdfReportAndCardRender() {
        seedFamily()
        val dir = File(outDir, "pdf").apply { deleteRecursively() }
        Savia.switchTo(dog)
        val report = BitmapSink(dir, "mascota-reporte")
        app.savia.export.PdfReport.write(runBlocking { ExportData.load(ExportOptions.ALL) }, ByteArrayOutputStream(), report)
        assertTrue("páginas: ${report.pages}", report.pages in 2..10)
        val card = BitmapSink(dir, "mascota-ficha")
        runBlocking {
            val db = Savia.db
            app.savia.export.writeMedicalCardPdf(
                db.profile().get(), db.medications().getAll(), 14.25, ByteArrayOutputStream(), System.currentTimeMillis(), card, db.vaccinations().getAll(),
            )
        }
        assertEquals(1, card.pages)
        // Ficha de una niña o niño: edad en años, parentesco y vacunas.
        Savia.switchTo(kid)
        val kidCard = BitmapSink(dir, "hijo-ficha")
        runBlocking {
            val db = Savia.db
            app.savia.export.writeMedicalCardPdf(db.profile().get(), emptyList(), 23.4, ByteArrayOutputStream(), System.currentTimeMillis(), kidCard, db.vaccinations().getAll())
        }
        assertEquals(1, kidCard.pages)
        assertEquals("8 meses", app.savia.export.ageText(t - 250, t))
        assertEquals("1 año", app.savia.export.ageText(t - 400, t))
        assertEquals("2 semanas", app.savia.export.ageText(t - 15, t))
    }

    // ------------------------------------------------------------------ respaldo de un perfil

    @Test
    fun profileBackupRoundTripsVaccinesAndNewFields() {
        seedFamily()
        Savia.switchTo(dog)
        val before = tables(dog)
        assertEquals(4, before.vaccinations.size)
        val f = File(outDir, "firulais.zip")
        runBlocking { f.outputStream().use { Backup.write(ctx, it) } }
        ZipFile(f).use { zip ->
            val names = zip.entries().toList().map { it.name }
            assertEquals(setOf("data.json", "attachments/rx_firulais.jpg"), names.toSet())
            val root = JSONObject(String(zip.getInputStream(zip.getEntry("data.json")).readBytes(), Charsets.UTF_8))
            assertEquals(2, root.getInt("formatVersion"))
            assertEquals(4, root.getJSONObject("tables").getJSONArray("vaccinations").length())
            assertEquals("pet", root.getJSONObject("tables").getJSONArray("profile").getJSONObject(0).getString("kind"))
        }
        val sm = runBlocking { f.inputStream().use { Backup.inspect(ctx, it) } }
        assertEquals(BackupKind.PROFILE, sm.kind)
        assertEquals("Firulais", sm.profiles.single().name); assertTrue(sm.profiles.single().isPet); assertEquals("perro", sm.profiles.single().species)
        assertEquals(before.recordCount, sm.recordCount); assertEquals(1, sm.profiles.single().attachmentCount)

        // Se borra todo lo del perro y se restaura sobre el mismo perfil.
        runBlocking {
            val db = Savia.db
            db.vaccinations().clear(); db.measurements().clear(); db.symptoms().clear()
            db.profile().save(Profile(kind = "pet"))
        }
        Savia.attachmentFile("rx_firulais.jpg").delete()
        val r = runBlocking { f.inputStream().use { Backup.restore(ctx, it, RestoreMode.REPLACE) } }
        assertEquals(before.recordCount, r.added)
        assertEquals("Firulais", r.profileName)
        assertEquals(before, tables(dog))
        assertTrue(petPhoto.contentEquals(Savia.attachmentFile("rx_firulais.jpg").readBytes()))
        // Los otros perfiles ni se enteran, y los adjuntos de Ana siguen en su carpeta.
        assertEquals("Ana", tables(1).profile!!.name)
        assertTrue(anaPdf.contentEquals(File(Savia.attachmentsDir(1), "ana_lab.pdf").readBytes()))
        // La copia de seguridad es del perfil restaurado, no del principal.
        assertTrue(Backup.safetyFile(ctx, dog).isFile)
        assertFalse(Backup.safetyFile(ctx, 1).exists())

        // Combinar el mismo respaldo no duplica vacunas.
        val m = runBlocking { f.inputStream().use { Backup.restore(ctx, it, RestoreMode.MERGE) } }
        assertEquals(0, m.added)
        assertEquals(4, tables(dog).vaccinations.size)
        // …y si faltaba una, la agrega.
        runBlocking { Savia.db.vaccinations().delete(Savia.db.vaccinations().getAll().first { it.name == "Rabia" }) }
        val m2 = runBlocking { f.inputStream().use { Backup.restore(ctx, it, RestoreMode.MERGE) } }
        assertEquals(1, m2.added)
        assertTrue(tables(dog).vaccinations.any { it.name == "Rabia" && it.lot == "RB-22" })
    }

    @Test
    fun replacingTheMainProfileKeepsOtherProfilesAttachments() {
        seedFamily()
        Savia.switchTo(1)
        val f = File(outDir, "ana.zip")
        runBlocking { f.outputStream().use { Backup.write(ctx, it) } }
        runBlocking { f.inputStream().use { Backup.restore(ctx, it, RestoreMode.REPLACE) } }
        // La carpeta del perfil 1 contiene las subcarpetas de los demás: no deben borrarse.
        assertTrue(petPhoto.contentEquals(File(Savia.attachmentsDir(dog), "rx_firulais.jpg").readBytes()))
        assertTrue(anaPdf.contentEquals(File(Savia.attachmentsDir(1), "ana_lab.pdf").readBytes()))
    }

    /** Emula un respaldo creado por Savia 1.0: formato 1, sin vacunas ni campos de perfil nuevos. */
    private fun v1Backup(): ByteArray {
        val profile = JSONObject()
            .put("name", "Mariana").put("sex", "F").put("birthEpochDay", 11000).put("heightCm", 160.5).put("goalWeightKg", JSONObject.NULL)
            .put("bloodType", "A+").put("allergies", "Polen").put("conditions", "").put("emergencyName", "Mamá").put("emergencyPhone", "55 0000 0000")
            .put("doctorName", "").put("doctorPhone", "").put("insurance", "GNP 123").put("notes", "")
        val tb = JSONObject()
            .put("profile", JSONArray().put(profile))
            .put("symptoms", JSONArray().put(JSONObject().put("id", 7).put("timestamp", 1_700_000_000_000L).put("name", "Migraña").put("intensity", 8)
                .put("regionId", JSONObject.NULL).put("regionLabel", JSONObject.NULL).put("durationMin", 90).put("tags", "Estrés").put("notes", "Con aura")))
            .put("measurements", JSONArray().put(JSONObject().put("id", 3).put("timestamp", 1_700_000_100_000L).put("type", "weight").put("value", 61.2).put("value2", JSONObject.NULL).put("tag", "").put("notes", "")))
            .put("daily_logs", JSONArray())
            .put("studies", JSONArray().put(JSONObject().put("id", 4).put("epochDay", 19600).put("title", "Biometría").put("category", "lab").put("place", "").put("doctor", "").put("status", "normal").put("summary", "").put("followUpEpochDay", JSONObject.NULL)))
            .put("study_results", JSONArray().put(JSONObject().put("id", 9).put("studyId", 4).put("name", "Hemoglobina").put("value", 13.1).put("textValue", "").put("unit", "g/dL").put("refMin", 12.0).put("refMax", 16.0).put("notes", "")))
            .put("study_attachments", JSONArray().put(JSONObject().put("id", 2).put("studyId", 4).put("fileName", "v1_foto.jpg").put("displayName", "Foto.jpg").put("mime", "image/jpeg")))
            .put("cycle_days", JSONArray().put(JSONObject().put("epochDay", 19650).put("flow", 3).put("symptoms", "colicos").put("moods", "").put("mucus", "").put("basalTemp", JSONObject.NULL)
                .put("sex", 0).put("ovulationTest", 0).put("pregnancyTest", 0).put("contraceptive", false).put("notes", "")))
            .put("medications", JSONArray()).put("med_intakes", JSONArray()).put("appointments", JSONArray()).put("self_exams", JSONArray())
        val root = JSONObject()
            .put("format", "savia-backup").put("formatVersion", 1).put("app", "Savia").put("appVersion", "1.0.0").put("appVersionCode", 1)
            .put("createdAtMillis", 1_700_000_200_000L)
            .put("prefs", JSONObject().put("onboarding_done", true).put("theme_mode", "light").put("cycle_enabled", true).put("cycle_length", 29).put("period_length", 4)
                .put("luteal_length", 14).put("show_fertility", true).put("period_reminder", false).put("app_lock", true).put("atlas_sex", "F").put("self_exam_reminder", false))
            .put("tables", tb)
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            z.putNextEntry(ZipEntry("data.json")); z.write(root.toString(1).toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("attachments/v1_foto.jpg")); z.write(petPhoto); z.closeEntry()
        }
        return bos.toByteArray()
    }

    @Test
    fun backupFromVersion1StillRestores() {
        val bytes = v1Backup()
        val sm = runBlocking { Backup.inspect(ctx, ByteArrayInputStream(bytes)) }
        assertEquals(BackupKind.PROFILE, sm.kind)
        assertEquals("1.0.0", sm.appVersion)
        assertEquals("Mariana", sm.profiles.single().name); assertFalse(sm.profiles.single().isPet)
        assertEquals(5, sm.recordCount)

        // Teléfono nuevo: se reemplaza el perfil principal.
        val r = runBlocking { Backup.restore(ctx, ByteArrayInputStream(bytes), RestoreMode.REPLACE) }
        assertEquals(5, r.added); assertEquals(1, r.attachmentsRestored)
        val after = tables(1)
        val p = after.profile!!
        assertEquals("Mariana", p.name); assertEquals("A+", p.bloodType); assertEquals("GNP 123", p.insurance); assertEquals(160.5, p.heightCm!!, 0.0)
        // Los campos que no existían en la versión 1 quedan con sus valores por defecto.
        assertEquals("person", p.kind); assertEquals("", p.species); assertNull(p.sterilized); assertEquals("", p.relation); assertEquals(0, p.avatarColor)
        assertEquals("Migraña", after.symptoms.single().name); assertEquals(90, after.symptoms.single().durationMin)
        assertEquals(61.2, after.measurements.single().value, 0.0)
        assertEquals("Hemoglobina", after.studyResults.single().name)
        assertEquals(3, after.cycleDays.single().flow)
        assertTrue(after.vaccinations.isEmpty())
        assertTrue(petPhoto.contentEquals(File(Savia.attachmentsDir(1), "v1_foto.jpg").readBytes()))
        assertTrue(Savia.prefs.cycleEnabled.value); assertEquals(29, Savia.prefs.cycleLength.value); assertEquals("light", Savia.prefs.themeMode.value)
        assertFalse("el bloqueo nunca se restaura", Savia.prefs.appLock.value)

        // También se puede combinar (no duplica) y cargar como perfil nuevo.
        val m = runBlocking { Backup.restore(ctx, ByteArrayInputStream(bytes), RestoreMode.MERGE) }
        assertEquals(0, m.added)
        val n = runBlocking { Backup.restoreAsNewProfile(ctx, ByteArrayInputStream(bytes)) }
        assertNotNull(n.newProfileId)
        assertEquals("Mariana", tables(n.newProfileId!!).profile!!.name)
    }

    @Test
    fun restoreAsNewProfileLeavesExistingProfilesUntouched() {
        seedFamily()
        Savia.switchTo(dog)
        val f = File(outDir, "firulais-nuevo.zip")
        runBlocking { f.outputStream().use { Backup.write(ctx, it) } }
        val dogTables = tables(dog)
        Savia.switchTo(1)
        val anaBefore = tables(1)
        Savia.prefs.themeMode.value = "light"
        val idsBefore = Savia.profileIds

        val r = runBlocking { f.inputStream().use { Backup.restoreAsNewProfile(ctx, it) } }
        val newId = r.newProfileId!!
        assertEquals(idsBefore + newId, Savia.profileIds)
        assertEquals(1, Savia.activeId.value)              // no cambia el perfil activo
        assertEquals("Firulais", r.profileName)
        assertEquals(dogTables.recordCount, r.added)
        assertEquals(dogTables, tables(newId))
        assertTrue(Savia.infoOf(newId).isPet)
        assertEquals("Firulais", Savia.infoOf(newId).displayName)
        assertTrue(petPhoto.contentEquals(File(Savia.attachmentsDir(newId), "rx_firulais.jpg").readBytes()))
        assertEquals(anaBefore, tables(1))
        assertEquals(dogTables, tables(dog))
        assertEquals("light", Savia.prefs.themeMode.value)   // un perfil nuevo no cambia los ajustes globales
        assertFalse(Backup.safetyFile(ctx, 1).exists())
    }

    // ------------------------------------------------------------------ respaldo completo

    private fun writeFull(name: String): File {
        val f = File(outDir, name)
        val info = runBlocking { f.outputStream().use { Backup.writeAll(ctx, it) } }
        assertEquals(Savia.profileIds.size, info.profileCount)
        return f
    }

    @Test
    fun fullBackupRoundTripOnAFreshPhone() {
        seedFamily()
        Savia.prefsFor(kid).cycleEnabled.value = false
        Savia.prefsFor(kid).periodLength.value = 6
        Savia.prefsFor(dog).selfExamReminder.value = true
        val before = mapOf(1 to tables(1), kid to tables(kid), dog to tables(dog))
        val f = writeFull("completo.zip")

        ZipFile(f).use { zip ->
            val names = zip.entries().toList().map { it.name }.toSet()
            assertEquals(
                setOf(
                    "perfiles.json", "perfiles/1/data.json", "perfiles/1/attachments/ana_lab.pdf",
                    "perfiles/$kid/data.json", "perfiles/$dog/data.json", "perfiles/$dog/attachments/rx_firulais.jpg",
                ),
                names,
            )
            val man = JSONObject(String(zip.getInputStream(zip.getEntry("perfiles.json")).readBytes(), Charsets.UTF_8))
            assertEquals("savia-backup-all", man.getString("format"))
            assertEquals(3, man.getJSONArray("profiles").length())
            assertEquals("pet", man.getJSONArray("profiles").getJSONObject(2).getString("kind"))
            assertEquals("dark", man.getJSONObject("globalPrefs").getString("theme_mode"))
        }
        val sm = runBlocking { f.inputStream().use { Backup.inspect(ctx, it) } }
        assertEquals(BackupKind.ALL, sm.kind)
        assertEquals(listOf("Ana", "Leo", "Firulais"), sm.profiles.map { it.name })
        assertEquals(before.values.sumOf { it.recordCount }, sm.recordCount)

        // Un respaldo completo no se puede cargar sobre un solo perfil.
        try {
            runBlocking { f.inputStream().use { Backup.restore(ctx, it, RestoreMode.REPLACE) } }
            fail("debió rechazarlo")
        } catch (e: BackupException) {
            assertTrue(e.message!!.contains("respaldo completo"))
        }
        assertEquals(before[1], tables(1))

        // ── Teléfono nuevo
        freshPhone()
        assertEquals(listOf(1), Savia.profileIds)
        assertEquals(0, tables(1).recordCount)
        assertEquals("system", Savia.prefs.themeMode.value)

        val r = runBlocking { f.inputStream().use { Backup.restoreAll(ctx, it, RestoreAllMode.REPLACE) } }
        assertEquals(1, r.profilesReplaced); assertEquals(2, r.newProfileIds.size); assertEquals(0, r.profilesRemoved)
        assertEquals(before.values.sumOf { it.recordCount }, r.records); assertEquals(2, r.attachmentsRestored)
        val (newKid, newDog) = r.newProfileIds
        assertEquals(listOf(1, newKid, newDog), Savia.profileIds)

        assertEquals(before[1], tables(1))
        assertEquals(before[kid], tables(newKid))
        assertEquals(before[dog], tables(newDog))
        assertEquals(4, tables(newDog).vaccinations.size)
        assertTrue(anaPdf.contentEquals(File(Savia.attachmentsDir(1), "ana_lab.pdf").readBytes()))
        assertTrue(petPhoto.contentEquals(File(Savia.attachmentsDir(newDog), "rx_firulais.jpg").readBytes()))
        // Ajustes por perfil
        assertTrue(Savia.prefsFor(1).cycleEnabled.value); assertEquals(31, Savia.prefsFor(1).cycleLength.value)
        assertFalse(Savia.prefsFor(newKid).cycleEnabled.value); assertEquals(6, Savia.prefsFor(newKid).periodLength.value); assertEquals("M", Savia.prefsFor(newKid).atlasSex.value)
        assertTrue(Savia.prefsFor(newDog).selfExamReminder.value); assertFalse(Savia.prefsFor(newDog).cycleEnabled.value)
        // Ajuste global
        assertEquals("dark", Savia.prefs.themeMode.value)
        // Los resúmenes de perfil quedan listos para la lista de perfiles.
        assertEquals("Leo", Savia.infoOf(newKid).displayName); assertEquals("Hijo", Savia.infoOf(newKid).subtitle)
        assertTrue(Savia.infoOf(newDog).isPet)
    }

    @Test
    fun fullBackupAddedAsNewProfilesTouchesNothing() {
        seedFamily()
        val f = writeFull("completo-agregar.zip")
        val before = mapOf(1 to tables(1), kid to tables(kid), dog to tables(dog))
        val r = runBlocking { f.inputStream().use { Backup.restoreAll(ctx, it, RestoreAllMode.ADD_AS_NEW) } }
        assertEquals(0, r.profilesReplaced); assertEquals(3, r.newProfileIds.size)
        assertEquals(6, Savia.profileIds.size)
        for ((id, tb) in before) assertEquals(tb, tables(id))
        assertEquals(before[1], tables(r.newProfileIds[0]))
        assertEquals(before[dog], tables(r.newProfileIds[2]))
        assertFalse(Backup.fullSafetyFile(ctx).exists())
        assertFalse(Backup.safetyFile(ctx, 1).exists())
    }

    @Test
    fun fullReplaceCanRemoveOtherProfilesAndBeUndone() {
        seedFamily()
        val f = writeFull("completo-reemplazo.zip")
        // Después del respaldo la familia cambia: un gato nuevo y un síntoma más de Ana.
        val cat = runBlocking { Savia.createProfile(Profile(name = "Garfield", kind = "pet", species = "gato")) }
        runBlocking {
            Savia.dbFor(cat).vaccinations().insert(Vaccination(epochDay = t - 5, name = "Triple felina"))
            Savia.dbFor(1).symptoms().insert(SymptomEntry(timestamp = 5L, name = "Solo en el teléfono"))
        }
        Savia.switchTo(cat)
        val current = Savia.profileIds.associateWith { tables(it) }

        val r = runBlocking { f.inputStream().use { Backup.restoreAll(ctx, it, RestoreAllMode.REPLACE, removeOtherProfiles = true) } }
        assertEquals(1, r.profilesReplaced); assertEquals(2, r.newProfileIds.size); assertEquals(3, r.profilesRemoved)
        assertEquals(listOf(1) + r.newProfileIds, Savia.profileIds)
        assertEquals(1, Savia.activeId.value)           // el perfil activo se eliminó → vuelve al principal
        assertTrue(tables(1).symptoms.none { it.name == "Solo en el teléfono" })
        assertEquals(listOf("Ana", "Leo", "Firulais"), Savia.profileIds.map { tables(it).profile!!.name })
        assertFalse(Savia.attachmentsDir(dog).exists())  // se fue con el perfil eliminado
        assertTrue(petPhoto.contentEquals(File(Savia.attachmentsDir(r.newProfileIds[1]), "rx_firulais.jpg").readBytes()))

        // Deshacer con la copia interna de todo.
        val safety = Backup.fullSafetyFile(ctx)
        assertTrue(safety.isFile)
        val copy = File(outDir, "deshacer-completo.zip").also { safety.copyTo(it, overwrite = true) }
        val u = runBlocking { copy.inputStream().use { Backup.restoreAll(ctx, it, RestoreAllMode.REPLACE, removeOtherProfiles = true) } }
        assertEquals(3, u.newProfileIds.size)
        val names = Savia.profileIds.map { tables(it).profile!!.name }
        assertEquals(listOf("Ana", "Leo", "Firulais", "Garfield"), names)
        assertTrue(tables(1).symptoms.any { it.name == "Solo en el teléfono" })
        assertEquals(current[1], tables(1))
        assertEquals(current[cat], tables(Savia.profileIds.last()))
    }

    @Test
    fun fullReplaceWithoutRemovingKeepsCurrentProfiles() {
        seedFamily()
        val f = writeFull("completo-conservar.zip")
        val r = runBlocking { f.inputStream().use { Backup.restoreAll(ctx, it, RestoreAllMode.REPLACE) } }
        assertEquals(0, r.profilesRemoved)
        assertEquals(5, Savia.profileIds.size)
        assertEquals("Firulais", tables(dog).profile!!.name)
        assertTrue(petPhoto.contentEquals(File(Savia.attachmentsDir(dog), "rx_firulais.jpg").readBytes()))
    }

    @Test
    fun rejectsBrokenFullBackups() {
        seedFamily()
        val good = writeFull("completo-base.zip")
        fun rebuild(transform: (String, ByteArray) -> ByteArray?): ByteArray {
            val bos = ByteArrayOutputStream()
            ZipOutputStream(bos).use { zo ->
                ZipFile(good).use { zip ->
                    for (e in zip.entries()) {
                        val bytes = transform(e.name, zip.getInputStream(e).readBytes()) ?: continue
                        zo.putNextEntry(ZipEntry(e.name)); zo.write(bytes); zo.closeEntry()
                    }
                }
                zo.putNextEntry(ZipEntry("perfiles/$dog/attachments/../../../../evil.txt")); zo.write("x".toByteArray()); zo.closeEntry()
                zo.putNextEntry(ZipEntry("perfiles/../evil2.txt")); zo.write("x".toByteArray()); zo.closeEntry()
            }
            return bos.toByteArray()
        }
        val before = Savia.profileIds.associateWith { tables(it) }
        // Falta la información de un perfil
        val missing = rebuild { name, bytes -> if (name == "perfiles/$kid/data.json") null else bytes }
        // Versión futura del respaldo completo
        val future = rebuild { name, bytes -> if (name == "perfiles.json") JSONObject(String(bytes)).put("formatVersion", 99).toString().toByteArray() else bytes }
        // Un perfil con datos dañados
        val broken = rebuild { name, bytes -> if (name == "perfiles/$dog/data.json") "{no es json".toByteArray() else bytes }
        for ((bad, expect) in listOf(missing to "incompleto", future to "versión más nueva", broken to "dañado")) {
            for (mode in RestoreAllMode.entries) {
                try {
                    runBlocking { Backup.restoreAll(ctx, ByteArrayInputStream(bad), mode, removeOtherProfiles = true) }
                    fail("debió rechazar el archivo ($expect)")
                } catch (e: BackupException) {
                    assertTrue(e.message, e.message!!.contains(expect))
                }
            }
            try {
                runBlocking { Backup.inspect(ctx, ByteArrayInputStream(bad)) }
                fail("debió rechazar el archivo al inspeccionarlo")
            } catch (e: BackupException) {
                assertTrue(e.message!!.isNotBlank())
            }
        }
        // Nada cambió y no quedó basura fuera de su lugar.
        assertEquals(before.keys.toList(), Savia.profileIds)
        for ((id, tb) in before) assertEquals(tb, tables(id))
        // Las rutas maliciosas se ignoran y el resto del archivo sí sirve.
        val ok = rebuild { _, bytes -> bytes }
        val r = runBlocking { Backup.restoreAll(ctx, ByteArrayInputStream(ok), RestoreAllMode.ADD_AS_NEW) }
        assertEquals(3, r.newProfileIds.size)
        assertFalse(File(ctx.filesDir, "evil.txt").exists()); assertFalse(File(ctx.cacheDir, "evil.txt").exists())
        assertFalse(File(ctx.cacheDir, "evil2.txt").exists()); assertFalse(File(File(ctx.cacheDir, "restore_tmp"), "evil2.txt").exists())
        // No quedan temporales.
        assertTrue(File(ctx.cacheDir, "restore_tmp").listFiles().isNullOrEmpty())
    }
}
