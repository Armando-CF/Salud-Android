package app.savia

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.savia.data.SaviaDb
import app.savia.data.StudyAttachment
import app.savia.data.SymptomEntry
import app.savia.export.Backup
import app.savia.export.BackupException
import app.savia.export.BackupTables
import app.savia.export.CsvWriter
import app.savia.export.ExportData
import app.savia.export.ExportFormat
import app.savia.export.ExportModule
import app.savia.export.ExportOptions
import app.savia.export.ExportTables
import app.savia.export.Exporter
import app.savia.export.RestoreMode
import app.savia.export.XlsxWriter
import app.savia.util.epochDayToMillis
import app.savia.util.today
import kotlinx.coroutines.runBlocking
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
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Genera cada formato en app/build/export-test/ y valida su estructura y la ida y vuelta del respaldo. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ExportTest {
    private lateinit var ctx: Context
    private val outDir = File("build/export-test").apply { mkdirs() }

    /** Texto con todo lo que suele romper exportadores. */
    private val tricky = "Dolor \"raro\"\u0001\u000B, con comas; <b>&amp;</b> y\nsalto de línea 😀 fin"
    private val attachmentBytes = ByteArray(5000) { (it * 31 % 251).toByte() }

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        Savia.init(ctx, SaviaDb.createInMemory(ctx))
        Backup.attachmentsDir(ctx).deleteRecursively()
        Backup.safetyFile(ctx).delete()
        DemoData.seed()
        runBlocking {
            val db = Savia.db
            db.symptoms().insert(
                SymptomEntry(timestamp = epochDayToMillis(today() - 2, 600), name = "Síntoma, \"especial\"", intensity = 9, tags = "Estrés,Comida", notes = tricky),
            )
            val study = db.studies().getAll().first()
            Backup.attachmentsDir(ctx).mkdirs()
            File(Backup.attachmentsDir(ctx), "att_demo_1.pdf").writeBytes(attachmentBytes)
            db.studies().insertAttachment(StudyAttachment(studyId = study.id, fileName = "att_demo_1.pdf", displayName = "Resultados laboratorio.pdf", mime = "application/pdf"))
            // Vacunas y campos de perfil de la versión 1.1
            db.vaccinations().insert(app.savia.data.Vaccination(epochDay = today() - 60, name = "Influenza", dose = "Anual", nextDueEpochDay = today() + 305, place = "Centro de salud", lot = "L-9", notes = tricky))
            db.vaccinations().insert(app.savia.data.Vaccination(epochDay = today() - 900, type = "otro", name = "Tétanos", remind = false))
            db.profile().save(db.profile().get()!!.copy(relation = "Yo", avatarColor = 3))
        }
    }

    private fun parseXml(bytes: ByteArray) =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))

    /** Analizador RFC 4180 mínimo para comprobar el CSV. */
    private fun parseCsv(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val sb = StringBuilder()
        var q = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (q) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { sb.append('"'); i++ } else q = false
                } else sb.append(ch)
            } else when (ch) {
                '"' -> q = true
                ',' -> { row.add(sb.toString()); sb.setLength(0) }
                '\r' -> {}
                '\n' -> { row.add(sb.toString()); sb.setLength(0); rows.add(row); row = ArrayList() }
                else -> sb.append(ch)
            }
            i++
        }
        if (sb.isNotEmpty() || row.isNotEmpty()) { row.add(sb.toString()); rows.add(row) }
        return rows
    }

    private fun export(format: ExportFormat, options: ExportOptions, name: String = Exporter.fileName(format, options)): File {
        val f = File(outDir, name)
        runBlocking { f.outputStream().use { Exporter.export(format, options, it) } }
        return f
    }

    // ------------------------------------------------------------------ Excel

    @Test
    fun xlsxIsAValidWorkbook() {
        val f = export(ExportFormat.XLSX, ExportOptions.ALL)
        val data = runBlocking { ExportData.load(ExportOptions.ALL) }
        val tables = ExportTables.build(data)
        ZipFile(f).use { zip ->
            val names = zip.entries().toList().map { it.name }
            for (required in listOf("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/styles.xml")) {
                assertTrue("falta $required", required in names)
            }
            // Todo el XML está bien formado
            for (n in names) parseXml(zip.getInputStream(zip.getEntry(n)).readBytes())

            val wb = parseXml(zip.getInputStream(zip.getEntry("xl/workbook.xml")).readBytes())
            val sheets = wb.getElementsByTagNameNS("*", "sheet")
            val sheetNames = (0 until sheets.length).map { sheets.item(it).attributes.getNamedItem("name").nodeValue }
            assertEquals(tables.map { it.title }, sheetNames)
            for (expected in listOf("Ciclo - días", "Ciclo - resumen", "Peso y mediciones", "Síntomas", "Estudios", "Resultados", "Vacunas", "Medicamentos", "Tomas", "Citas", "Bienestar", "Autoexploración", "Perfil")) {
                assertTrue("falta la hoja $expected", expected in sheetNames)
            }
            assertTrue(sheetNames.all { it.length <= 31 })
            assertEquals(tables.size, names.count { it.startsWith("xl/worksheets/sheet") })

            tables.forEachIndexed { i, t ->
                val doc = parseXml(zip.getInputStream(zip.getEntry("xl/worksheets/sheet${i + 1}.xml")).readBytes())
                val rows = doc.getElementsByTagNameNS("*", "row")
                assertEquals("renglones de ${t.title}", t.rows.size + 1, rows.length)
                assertEquals(1, doc.getElementsByTagNameNS("*", "pane").length)
            }

            // El texto difícil llega íntegro (sin los caracteres de control que XML no permite).
            val symIdx = tables.indexOfFirst { it.title == "Síntomas" }
            val sym = parseXml(zip.getInputStream(zip.getEntry("xl/worksheets/sheet${symIdx + 1}.xml")).readBytes())
            val texts = sym.getElementsByTagNameNS("*", "t")
            val all = (0 until texts.length).map { texts.item(it).textContent }
            assertTrue(all.contains(tricky.replace("\u0001", "").replace("\u000B", "")))
            assertTrue(all.contains("Síntoma, \"especial\""))

            // Las fechas van como número de serie de Excel con estilo de fecha.
            val cyc = String(zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml")).readBytes(), Charsets.UTF_8)
            val firstDay = data.cycleDays.minOf { it.epochDay }
            assertTrue(cyc.contains("<c r=\"A2\" s=\"2\"><v>${firstDay + 25569}</v></c>"))
        }
    }

    @Test
    fun xlsxHelpers() {
        assertEquals("A", XlsxWriter.colName(0))
        assertEquals("Z", XlsxWriter.colName(25))
        assertEquals("AA", XlsxWriter.colName(26))
        assertEquals("AB", XlsxWriter.colName(27))
        assertEquals("BA", XlsxWriter.colName(52))
        assertEquals("a &amp; b &lt;c&gt; &quot;d&quot;", XlsxWriter.esc("a & b <c> \"d\"\u0000\u0008"))
        assertEquals(listOf("Hoja", "hoja (2)", "A B"), XlsxWriter.uniqueSheetNames(listOf("Hoja", "hoja", "A/B")))
        assertTrue(XlsxWriter.uniqueSheetNames(listOf("x".repeat(50)))[0].length == 31)
    }

    @Test
    fun cycleOnlyXlsxHasOnlyCycleSheets() {
        val f = export(ExportFormat.XLSX, ExportOptions.CYCLE_ONLY)
        assertTrue(f.name.startsWith("savia-ciclo-") && f.name.endsWith(".xlsx"))
        ZipFile(f).use { zip ->
            val wb = String(zip.getInputStream(zip.getEntry("xl/workbook.xml")).readBytes(), Charsets.UTF_8)
            assertTrue(wb.contains("Ciclo - días") && wb.contains("Ciclo - resumen") && wb.contains("Ciclo - estadísticas"))
            assertFalse(wb.contains("Síntomas"))
        }
        val data = runBlocking { ExportData.load(ExportOptions.CYCLE_ONLY) }
        // DemoData siembra 9 periodos → 8 ciclos completos + el actual
        assertEquals(9, data.cycles.size)
        assertEquals(28, data.cycles.first().value.length)
        assertNull(data.cycles.last().value.length)
    }

    // ------------------------------------------------------------------ CSV

    @Test
    fun csvEscapesFields() {
        assertEquals("simple", CsvWriter.field("simple"))
        assertEquals("\"a,b\"", CsvWriter.field("a,b"))
        assertEquals("\"dijo \"\"hola\"\"\"", CsvWriter.field("dijo \"hola\""))
        assertEquals("\"dos\nlíneas\"", CsvWriter.field("dos\nlíneas"))
        assertEquals("\" espacio\"", CsvWriter.field(" espacio"))
        assertEquals("", CsvWriter.field(""))
        assertEquals("a,\"b,c\",\r\n", CsvWriter.line(listOf("a", "b,c", "")))
    }

    @Test
    fun csvZipHasOneFilePerTableAndRoundTrips() {
        val f = export(ExportFormat.CSV, ExportOptions.ALL)
        assertTrue(f.name.endsWith(".zip"))
        val data = runBlocking { ExportData.load(ExportOptions.ALL) }
        val tables = ExportTables.build(data)
        ZipFile(f).use { zip ->
            val names = zip.entries().toList().map { it.name }
            for (t in tables) assertTrue("falta ${t.id}.csv", "${t.id}.csv" in names)
            val bytes = zip.getInputStream(zip.getEntry("sintomas.csv")).readBytes()
            assertEquals(0xEF.toByte(), bytes[0]); assertEquals(0xBB.toByte(), bytes[1]); assertEquals(0xBF.toByte(), bytes[2])
            val rows = parseCsv(String(bytes, 3, bytes.size - 3, Charsets.UTF_8))
            assertEquals(data.symptoms.size + 1, rows.size)
            assertEquals(listOf("Fecha y hora", "Síntoma", "Intensidad (1-10)", "Nivel", "Zona del cuerpo", "Duración (min)", "Posibles desencadenantes", "Notas"), rows[0])
            assertTrue(rows.all { it.size == 8 })
            val special = rows.first { it[1] == "Síntoma, \"especial\"" }
            assertEquals(tricky, special[7])
            assertEquals("Estrés, Comida", special[6])
            assertEquals("9", special[2])
            assertTrue(special[0].matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")))

            val cyc = parseCsv(String(zip.getInputStream(zip.getEntry("ciclo_dias.csv")).readBytes().drop(3).toByteArray(), Charsets.UTF_8))
            assertEquals(data.cycleDays.size + 1, cyc.size)
            assertTrue(cyc[1][0].matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
            assertEquals("1", cyc[1][1])
            assertEquals("Moderado", cyc[1][3])
            assertEquals("Cólicos, Fatiga", cyc[1][4])
        }
    }

    @Test
    fun singleTableModuleExportsPlainCsv() {
        val opts = ExportOptions(modules = setOf(ExportModule.MEDICIONES))
        assertTrue(Exporter.csvIsSingleFile(opts))
        assertEquals("text/csv", Exporter.mimeType(ExportFormat.CSV, opts))
        val f = export(ExportFormat.CSV, opts)
        assertTrue(f.name.matches(Regex("savia-mediciones-\\d{4}-\\d{2}-\\d{2}\\.csv")))
        val bytes = f.readBytes()
        assertEquals(0xEF.toByte(), bytes[0])
        val rows = parseCsv(String(bytes, 3, bytes.size - 3, Charsets.UTF_8))
        assertEquals("Fecha y hora", rows[0][0])
        assertTrue(rows.any { it[1] == "Peso" && it[4] == "kg" })
        assertTrue(rows.any { it[1] == "Presión arterial" && it[3].isNotEmpty() })
        assertFalse(Exporter.csvIsSingleFile(ExportOptions.CYCLE_ONLY))
    }

    // ------------------------------------------------------------------ JSON legible

    @Test
    fun readableJsonHasTables() {
        val f = export(ExportFormat.JSON, ExportOptions.ALL)
        val root = JSONObject(f.readText())
        assertEquals("Savia", root.getJSONObject("savia").getString("aplicacion"))
        val tablas = root.getJSONObject("tablas")
        val data = runBlocking { ExportData.load(ExportOptions.ALL) }
        assertEquals(data.cycleDays.size, tablas.getJSONArray("Ciclo - días").length())
        assertEquals(data.measurements.size, tablas.getJSONArray("Peso y mediciones").length())
        val first = tablas.getJSONArray("Peso y mediciones").getJSONObject(0)
        assertEquals("Peso", first.getString("Tipo"))
        assertTrue(first.get("Valor") is Number)
        assertTrue(first.isNull("Segundo valor"))
    }

    // ------------------------------------------------------------------ PDF

    /** Destino de páginas que dibuja en mapas de bits (2x) y los guarda como PNG para revisar la maquetación. */
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

    /**
     * Robolectric no trae el generador nativo de PdfDocument, así que la maquetación del PDF se verifica
     * dibujando las mismas páginas en mapas de bits (app/build/export-test/pdf/).
     */
    @Test
    fun pdfLayoutRendersAllSections() {
        val dir = File(outDir, "pdf").apply { deleteRecursively() }
        val data = runBlocking { ExportData.load(ExportOptions.ALL) }
        val full = BitmapSink(dir, "reporte")
        app.savia.export.PdfReport.write(data, ByteArrayOutputStream(), full)
        assertTrue("páginas: ${full.pages}", full.pages in 8..60)

        val cycle = BitmapSink(dir, "ciclo")
        app.savia.export.PdfReport.write(runBlocking { ExportData.load(ExportOptions.CYCLE_ONLY) }, ByteArrayOutputStream(), cycle)
        assertTrue(cycle.pages >= 2)

        val card = BitmapSink(dir, "ficha")
        runBlocking {
            val db = Savia.db
            app.savia.export.writeMedicalCardPdf(
                db.profile().get(), db.medications().getAll().filter { it.active },
                db.measurements().getAll().filter { it.type == "weight" }.maxByOrNull { it.timestamp }?.value,
                ByteArrayOutputStream(), System.currentTimeMillis(), card,
            )
        }
        assertEquals(1, card.pages)
    }

    @Test
    fun pdfWrapBreaksLongTextIntoLines() {
        val paint = app.savia.export.PdfBuilder.paint(9f)
        val lines = app.savia.export.PdfBuilder.wrap("uno dos tres cuatro cinco seis siete ocho nueve diez\nsegundo párrafo " + "x".repeat(200), paint, 80f)
        assertTrue(lines.size > 5)
        assertTrue(lines.all { paint.measureText(it) <= 80.5f })
        assertEquals(listOf(""), app.savia.export.PdfBuilder.wrap("", paint, 80f))
        assertEquals("unodostrescuatrocincoseissieteochonuevediezsegundopárrafo" + "x".repeat(200), lines.joinToString("").replace(" ", ""))
    }

    @Test
    fun emptyDatabaseExportsWithoutCrashing() {
        Savia.init(ctx, SaviaDb.createInMemory(ctx))
        for (fmt in ExportFormat.entries.filter { it != ExportFormat.PDF }) {
            val f = export(fmt, ExportOptions.ALL, "vacio-" + Exporter.fileName(fmt, ExportOptions.ALL))
            assertTrue(f.length() > 0)
        }
        val data = runBlocking { ExportData.load(ExportOptions.ALL) }
        assertTrue(data.isEmpty)
        val dir = File(outDir, "pdf-vacio").apply { deleteRecursively() }
        val sink = BitmapSink(dir, "reporte")
        app.savia.export.PdfReport.write(data, ByteArrayOutputStream(), sink)
        assertTrue(sink.pages >= 1)
        val card = BitmapSink(dir, "ficha")
        app.savia.export.writeMedicalCardPdf(null, emptyList(), null, ByteArrayOutputStream(), System.currentTimeMillis(), card)
        assertEquals(1, card.pages)
    }

    // ------------------------------------------------------------------ opciones

    @Test
    fun dateRangeFiltersEveryModule() {
        val t = today()
        val opts = ExportOptions(fromDay = t - 30, toDay = t)
        val data = runBlocking { ExportData.load(opts) }
        val all = runBlocking { ExportData.load(ExportOptions.ALL) }
        assertTrue(data.symptoms.size < all.symptoms.size && data.symptoms.isNotEmpty())
        assertTrue(data.measurements.size < all.measurements.size)
        assertTrue(data.cycleDays.all { it.epochDay in (t - 30)..t })
        assertTrue(data.cycles.size in 1..2)
        // Las estadísticas del ciclo usan todo el historial aunque el rango sea corto.
        assertEquals(all.cycleAnalysis!!.avgCycle, data.cycleAnalysis!!.avgCycle)
        assertTrue(data.studies.all { it.epochDay >= t - 30 })
        assertTrue(data.studyResults.all { r -> data.studies.any { it.id == r.studyId } })
        assertEquals("Del ${app.savia.util.Fmt.date(t - 30)} al ${app.savia.util.Fmt.date(t)}", ExportTables.rangeLabel(opts))
        // Un tratamiento terminado antes del rango no entra; los vigentes sí.
        assertTrue(data.medications.none { it.name == "Amoxicilina" })
        assertTrue(data.medications.any { it.name == "Loratadina" })
    }

    @Test
    fun fileNamesAreSafe() {
        val re = Regex("[a-z0-9.-]+")
        for (fmt in ExportFormat.entries) {
            for (o in listOf(ExportOptions.ALL, ExportOptions.CYCLE_ONLY, ExportOptions(setOf(ExportModule.AUTOEXPLORACION)))) {
                assertTrue(Exporter.fileName(fmt, o).matches(re))
            }
        }
        assertTrue(Exporter.backupFileName().matches(Regex("savia-respaldo-\\d{4}-\\d{2}-\\d{2}\\.zip")))
        assertEquals("savia-ciclo-1970-01-02.xlsx", Exporter.fileName(ExportFormat.XLSX, ExportOptions.CYCLE_ONLY, 1))
        assertEquals("savia-datos-csv-1970-01-02.zip", Exporter.fileName(ExportFormat.CSV, ExportOptions.ALL, 1))
    }

    // ------------------------------------------------------------------ respaldo

    private fun writeBackup(name: String = Exporter.backupFileName()): File {
        val f = File(outDir, name)
        runBlocking { f.outputStream().use { Backup.write(ctx, it) } }
        return f
    }

    private fun wipe() = runBlocking {
        val db = Savia.db
        db.medications().clearIntakes(); db.medications().clear()
        db.studies().clearAttachments(); db.studies().clearResults(); db.studies().clear()
        db.symptoms().clear(); db.measurements().clear(); db.dailyLogs().clear(); db.cycle().clear()
        db.appointments().clear(); db.selfExams().clear(); db.vaccinations().clear()
        db.profile().save(app.savia.data.Profile())
        Backup.attachmentsDir(ctx).deleteRecursively()
    }

    @Test
    fun backupRoundTripReplace() {
        Savia.prefs.cycleLength.value = 31
        Savia.prefs.themeMode.value = "dark"
        Savia.prefs.atlasSex.value = "M"
        val before = runBlocking { BackupTables.load(Savia.db) }
        val prefsBefore = Savia.prefs.export()
        assertTrue(before.recordCount > 200)
        val f = writeBackup()

        // El zip trae data.json y los adjuntos
        ZipFile(f).use { zip ->
            val names = zip.entries().toList().map { it.name }
            assertTrue("data.json" in names)
            assertTrue("attachments/att_demo_1.pdf" in names)
            val root = JSONObject(String(zip.getInputStream(zip.getEntry("data.json")).readBytes(), Charsets.UTF_8))
            assertEquals("savia-backup", root.getString("format"))
            assertEquals(2, root.getInt("formatVersion"))
            assertEquals(before.symptoms.size, root.getJSONObject("tables").getJSONArray("symptoms").length())
            assertEquals(2, root.getJSONObject("tables").getJSONArray("vaccinations").length())
        }

        // Borrar todo (como en un teléfono nuevo)
        wipe()
        Savia.prefs.cycleLength.value = 28
        Savia.prefs.themeMode.value = "system"
        Savia.prefs.atlasSex.value = "F"
        Savia.prefs.cycleEnabled.value = false
        Savia.prefs.appLock.value = false
        assertEquals(0, runBlocking { BackupTables.load(Savia.db) }.recordCount)

        val result = runBlocking { f.inputStream().use { Backup.restore(ctx, it, RestoreMode.REPLACE) } }
        assertEquals(before.recordCount, result.added)
        assertEquals(1, result.attachmentsRestored)

        val after = runBlocking { BackupTables.load(Savia.db) }
        assertEquals(before.profile, after.profile)
        assertEquals(before.symptoms, after.symptoms)
        assertEquals(before.measurements, after.measurements)
        assertEquals(before.dailyLogs, after.dailyLogs)
        assertEquals(before.studies, after.studies)
        assertEquals(before.studyResults, after.studyResults)
        assertEquals(before.studyAttachments, after.studyAttachments)
        assertEquals(before.cycleDays, after.cycleDays)
        assertEquals(before.medications, after.medications)
        assertEquals(before.medIntakes, after.medIntakes)
        assertEquals(before.appointments, after.appointments)
        assertEquals(before.selfExams, after.selfExams)
        assertEquals(before.vaccinations, after.vaccinations)
        assertEquals(2, after.vaccinations.size)
        assertEquals("Yo", after.profile!!.relation)
        assertEquals(3, after.profile!!.avatarColor)
        assertEquals(before, after)
        assertEquals(prefsBefore, Savia.prefs.export())
        assertEquals(31, Savia.prefs.cycleLength.value)
        assertEquals("dark", Savia.prefs.themeMode.value)
        assertTrue(Savia.prefs.cycleEnabled.value)
        assertTrue(attachmentBytes.contentEquals(File(Backup.attachmentsDir(ctx), "att_demo_1.pdf").readBytes()))
        // El texto difícil sobrevive intacto, con todo y caracteres de control.
        assertTrue(after.symptoms.any { it.notes == tricky })
    }

    @Test
    fun replaceKeepsASafetyCopyOfPreviousData() {
        val f = writeBackup("respaldo-para-seguridad.zip")
        runBlocking { Savia.db.symptoms().insert(SymptomEntry(timestamp = 123456789L, name = "Solo en el teléfono")) }
        val current = runBlocking { BackupTables.load(Savia.db) }
        runBlocking { f.inputStream().use { Backup.restore(ctx, it, RestoreMode.REPLACE) } }
        assertTrue(runBlocking { Savia.db.symptoms().getAll() }.none { it.name == "Solo en el teléfono" })
        // La copia de seguridad permite deshacer.
        val safety = Backup.safetyFile(ctx)
        assertTrue(safety.isFile)
        val safetyCopy = File(outDir, "copia-seguridad.zip").also { safety.copyTo(it, overwrite = true) }
        runBlocking { safetyCopy.inputStream().use { Backup.restore(ctx, it, RestoreMode.REPLACE) } }
        assertEquals(current, runBlocking { BackupTables.load(Savia.db) })
    }

    @Test
    fun mergeOfSameBackupAddsNothing() {
        val before = runBlocking { BackupTables.load(Savia.db) }
        val f = writeBackup("respaldo-merge.zip")
        val result = runBlocking { f.inputStream().use { Backup.restore(ctx, it, RestoreMode.MERGE) } }
        assertEquals(0, result.added)
        assertEquals(0, result.updated)
        assertEquals(before.recordCount, result.skipped)
        assertEquals(before, runBlocking { BackupTables.load(Savia.db) })
    }

    @Test
    fun mergeRestoresMissingRecordsAndKeepsNewOnes() {
        val before = runBlocking { BackupTables.load(Savia.db) }
        val f = writeBackup("respaldo-merge2.zip")
        val t = today()
        runBlocking {
            val db = Savia.db
            // Se pierden cosas...
            db.studies().delete(before.studies.first { it.title.startsWith("Química") })
            db.medications().delete(before.medications.first { it.name == "Vitamina D" })
            db.symptoms().delete(before.symptoms.first())
            db.cycle().deleteDay(before.cycleDays.first().epochDay)
            File(Backup.attachmentsDir(ctx), "att_demo_1.pdf").delete()
            // ...se cambia un día y se agregan registros nuevos después del respaldo.
            db.cycle().save(before.cycleDays.last().copy(notes = "Editado después"))
            db.symptoms().insert(SymptomEntry(timestamp = epochDayToMillis(t, 700), name = "Nuevo después del respaldo"))
            db.measurements().insert(app.savia.data.Measurement(timestamp = epochDayToMillis(t, 701), type = "weight", value = 61.0))
        }
        val result = runBlocking { f.inputStream().use { Backup.restore(ctx, it, RestoreMode.MERGE) } }
        assertTrue(result.added > 0)
        assertEquals(1, result.updated)
        val after = runBlocking { BackupTables.load(Savia.db) }
        assertEquals(before.symptoms.size + 1, after.symptoms.size)
        assertTrue(after.symptoms.any { it.name == "Nuevo después del respaldo" })
        assertEquals(before.measurements.size + 1, after.measurements.size)
        assertEquals(before.cycleDays, after.cycleDays)
        assertEquals(before.studies.size, after.studies.size)
        assertEquals(before.studyResults.size, after.studyResults.size)
        assertEquals(before.medications.size, after.medications.size)
        assertEquals(before.medIntakes.size, after.medIntakes.size)
        assertEquals(before.studyAttachments.size, after.studyAttachments.size)
        // Las llaves foráneas apuntan al estudio/medicamento recreado.
        val study = after.studies.first { it.epochDay == before.studies.first { s -> s.title.startsWith("Química") }.epochDay && it.title.startsWith("Química") }
        val oldStudy = before.studies.first { it.title.startsWith("Química") }
        assertEquals(before.studyResults.count { it.studyId == oldStudy.id }, after.studyResults.count { it.studyId == study.id })
        val med = after.medications.first { it.name == "Vitamina D" }
        assertEquals(1, after.medIntakes.count { it.medicationId == med.id })
        assertTrue(after.studyAttachments.all { a -> after.studies.any { it.id == a.studyId } })
        assertTrue(File(Backup.attachmentsDir(ctx), "att_demo_1.pdf").isFile)
    }

    @Test
    fun mergeRenamesConflictingAttachment() {
        val f = writeBackup("respaldo-merge3.zip")
        runBlocking {
            val db = Savia.db
            db.studies().clearAttachments()
            // Mismo nombre, contenido distinto, ligado a otro estudio.
            File(Backup.attachmentsDir(ctx), "att_demo_1.pdf").writeBytes(ByteArray(10) { 7 })
            db.studies().insertAttachment(StudyAttachment(studyId = db.studies().getAll().last().id, fileName = "att_demo_1.pdf", displayName = "Otro.pdf"))
        }
        runBlocking { f.inputStream().use { Backup.restore(ctx, it, RestoreMode.MERGE) } }
        val atts = runBlocking { Savia.db.studies().getAllAttachments() }
        assertEquals(2, atts.size)
        val restored = atts.first { it.displayName == "Resultados laboratorio.pdf" }
        assertTrue(restored.fileName != "att_demo_1.pdf")
        assertTrue(attachmentBytes.contentEquals(File(Backup.attachmentsDir(ctx), restored.fileName).readBytes()))
        assertEquals(10, File(Backup.attachmentsDir(ctx), "att_demo_1.pdf").length())
    }

    @Test
    fun rejectsInvalidFiles() {
        fun restore(bytes: ByteArray) = runBlocking { Backup.restore(ctx, ByteArrayInputStream(bytes), RestoreMode.REPLACE) }
        val before = runBlocking { BackupTables.load(Savia.db) }
        for (bad in listOf(ByteArray(0), "hola mundo".toByteArray(), "{\"format\":\"otro\"}".toByteArray(), export(ExportFormat.XLSX, ExportOptions.CYCLE_ONLY).readBytes(), export(ExportFormat.JSON, ExportOptions.ALL).readBytes())) {
            try {
                restore(bad)
                fail("debió rechazar el archivo")
            } catch (e: BackupException) {
                assertTrue(e.message!!.isNotBlank())
            }
        }
        // Respaldo de una versión futura
        val future = JSONObject().put("format", "savia-backup").put("formatVersion", 99).put("tables", JSONObject()).toString().toByteArray()
        try {
            restore(future)
            fail("debió rechazar una versión futura")
        } catch (e: BackupException) {
            assertTrue(e.message!!.contains("versión más nueva"))
        }
        // Nada cambió
        assertEquals(before, runBlocking { BackupTables.load(Savia.db) })
    }

    @Test
    fun toleratesUnknownAndMissingFields() {
        val json = JSONObject()
            .put("format", "savia-backup").put("formatVersion", 1)
            .put("tables", JSONObject()
                .put("symptoms", org.json.JSONArray().put(JSONObject().put("timestamp", 1000L).put("name", "X").put("campoFuturo", 5)))
                .put("tabla_futura", org.json.JSONArray()))
        wipe()
        val r = runBlocking { Backup.restore(ctx, ByteArrayInputStream(json.toString().toByteArray()), RestoreMode.REPLACE) }
        assertEquals(1, r.added)
        val s = runBlocking { Savia.db.symptoms().getAll() }.single()
        assertEquals("X", s.name); assertEquals(5, s.intensity); assertNull(s.regionId)
    }

    @Test
    fun zipSlipEntriesAreIgnored() {
        val bos = ByteArrayOutputStream()
        val good = writeBackup("respaldo-base.zip")
        java.util.zip.ZipOutputStream(bos).use { zo ->
            ZipInputStream(good.inputStream()).use { zi ->
                while (true) {
                    val e = zi.nextEntry ?: break
                    zo.putNextEntry(java.util.zip.ZipEntry(e.name)); zi.copyTo(zo); zo.closeEntry()
                }
            }
            zo.putNextEntry(java.util.zip.ZipEntry("attachments/../../evil.txt")); zo.write("x".toByteArray()); zo.closeEntry()
        }
        runBlocking { Backup.restore(ctx, ByteArrayInputStream(bos.toByteArray()), RestoreMode.REPLACE) }
        assertFalse(File(ctx.filesDir.parentFile, "evil.txt").exists())
        assertFalse(File(ctx.filesDir, "evil.txt").exists())
        assertNotNull(Backup.attachmentsDir(ctx).listFiles())
        assertEquals(listOf("att_demo_1.pdf"), Backup.attachmentsDir(ctx).listFiles()!!.map { it.name })
    }
}
