package app.savia.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import app.savia.Savia
import app.savia.util.Fmt
import app.savia.util.today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Fachada del módulo de exportación. Todo lo `suspend` ya corre en Dispatchers.IO.
 *
 * Ejemplos:
 * ```
 * // Solo el ciclo menstrual a Excel, listo para compartir:
 * val file = Exporter.exportToCache(context, ExportFormat.XLSX, ExportOptions.CYCLE_ONLY)
 * context.startActivity(Exporter.shareIntent(context, file, Exporter.mimeType(ExportFormat.XLSX, ExportOptions.CYCLE_ONLY)))
 *
 * // Ficha médica en PDF:
 * val pdf = Exporter.medicalCardToCache(context)
 * context.startActivity(Exporter.shareIntent(context, pdf, "application/pdf"))
 * ```
 */
object Exporter {
    const val AUTHORITY = "app.savia.files"
    const val MIME_PDF = "application/pdf"
    const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    const val MIME_ZIP = "application/zip"
    const val MIME_CSV = "text/csv"
    const val MIME_JSON = "application/json"

    /** ¿El CSV saldrá como un solo archivo (una tabla) o como .zip con varios? */
    fun csvIsSingleFile(options: ExportOptions): Boolean = ExportTables.tableCount(options) == 1

    fun extension(format: ExportFormat, options: ExportOptions): String = when (format) {
        ExportFormat.XLSX -> "xlsx"
        ExportFormat.PDF -> "pdf"
        ExportFormat.CSV -> if (csvIsSingleFile(options)) "csv" else "zip"
        ExportFormat.JSON -> "json"
    }

    fun mimeType(format: ExportFormat, options: ExportOptions): String = when (format) {
        ExportFormat.XLSX -> MIME_XLSX
        ExportFormat.PDF -> MIME_PDF
        ExportFormat.CSV -> if (csvIsSingleFile(options)) MIME_CSV else MIME_ZIP
        ExportFormat.JSON -> MIME_JSON
    }

    /** Nombre sugerido, p. ej. "savia-ciclo-2026-11-23.xlsx". Solo minúsculas ASCII, dígitos y guiones. */
    fun fileName(format: ExportFormat, options: ExportOptions, day: Long = today()): String {
        val scope = when {
            options.modules.size == 1 -> options.modules.first().slug
            else -> "datos"
        }
        val csvZip = if (format == ExportFormat.CSV && !csvIsSingleFile(options)) "-csv" else ""
        return "savia-$scope$csvZip-${Fmt.isoDate(day)}.${extension(format, options)}"
    }

    fun backupFileName(day: Long = today()): String = "savia-respaldo-${Fmt.isoDate(day)}.zip"

    fun medicalCardFileName(day: Long = today()): String = "savia-ficha-medica-${Fmt.isoDate(day)}.pdf"

    // ------------------------------------------------------------------ exportar

    /** Escribe datos ya cargados en el formato pedido (no cierra [out]... salvo los formatos zip, que lo cierran). */
    fun write(format: ExportFormat, data: ExportData, out: OutputStream) {
        when (format) {
            ExportFormat.XLSX -> XlsxWriter.write(ExportTables.build(data), out, title = "Savia — " + ExportTables.rangeLabel(data.options))
            ExportFormat.PDF -> PdfReport.write(data, out)
            ExportFormat.CSV -> {
                val tables = ExportTables.build(data)
                if (tables.size == 1) CsvWriter.write(tables[0], out) else CsvWriter.writeZip(tables, out)
            }
            ExportFormat.JSON -> JsonExportWriter.write(data, out)
        }
    }

    /** Carga y escribe. Devuelve los datos exportados (útil para saber si estaba vacío). */
    suspend fun export(format: ExportFormat, options: ExportOptions, out: OutputStream): ExportData = withContext(Dispatchers.IO) {
        val data = ExportData.load(options)
        write(format, data, out)
        data
    }

    /** Carpeta temporal para compartir (cacheDir/exports, expuesta por el FileProvider). Borra archivos viejos. */
    fun exportsDir(context: Context): File {
        val dir = File(context.cacheDir, "exports")
        dir.mkdirs()
        val limit = System.currentTimeMillis() - 24L * 3600_000L
        dir.listFiles()?.forEach { if (it.isFile && it.lastModified() < limit) it.delete() }
        return dir
    }

    /** Genera la exportación en cacheDir/exports y devuelve el archivo. */
    suspend fun exportToCache(context: Context, format: ExportFormat, options: ExportOptions): File =
        prepare(context, format, options).first

    /**
     * Como [exportToCache], pero además devuelve los datos exportados (p. ej. para avisar si
     * `data.isEmpty`). Siempre genera el archivo; quien llama decide qué hacer.
     */
    suspend fun prepare(context: Context, format: ExportFormat, options: ExportOptions): Pair<File, ExportData> = withContext(Dispatchers.IO) {
        val data = ExportData.load(options)
        val file = File(exportsDir(context), fileName(format, options))
        try {
            file.outputStream().use { write(format, data, it) }
        } catch (e: Throwable) {
            file.delete()
            throw e
        }
        file to data
    }

    // ------------------------------------------------------------------ ficha médica

    /** Ficha médica (una página) con el perfil actual, los medicamentos activos y el peso más reciente. */
    suspend fun writeMedicalCard(out: OutputStream) = withContext(Dispatchers.IO) {
        val db = Savia.db
        val t = today()
        val meds = db.medications().getAll().filter { it.active && (it.endEpochDay == null || it.endEpochDay >= t) }
        val weight = db.measurements().getAll().filter { it.type == "weight" }.maxByOrNull { it.timestamp }?.value
        writeMedicalCardPdf(db.profile().get(), meds, weight, out)
    }

    suspend fun medicalCardToCache(context: Context): File = withContext(Dispatchers.IO) {
        val file = File(exportsDir(context), medicalCardFileName())
        try {
            file.outputStream().use { writeMedicalCard(it) }
        } catch (e: Throwable) {
            file.delete()
            throw e
        }
        file
    }

    // ------------------------------------------------------------------ respaldo

    suspend fun backupToCache(context: Context): Pair<File, BackupInfo> = withContext(Dispatchers.IO) {
        val file = File(exportsDir(context), backupFileName())
        try {
            val info = file.outputStream().use { Backup.write(context, it) }
            file to info
        } catch (e: Throwable) {
            file.delete()
            throw e
        }
    }

    suspend fun restore(context: Context, input: InputStream, mode: RestoreMode): RestoreResult = withContext(Dispatchers.IO) {
        Backup.restore(context, input, mode)
    }

    suspend fun restore(context: Context, uri: Uri, mode: RestoreMode): RestoreResult = withContext(Dispatchers.IO) {
        val input = try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            null
        } ?: throw BackupException("No se pudo abrir el archivo elegido.")
        input.use { Backup.restore(context, it, mode) }
    }

    // ------------------------------------------------------------------ guardar / compartir

    /** Copia un archivo ya generado al documento elegido con el selector del sistema. */
    suspend fun copyToUri(context: Context, file: File, uri: Uri) = withContext(Dispatchers.IO) {
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: throw java.io.IOException("No se pudo escribir en la ubicación elegida.")
        out.use { o -> file.inputStream().use { it.copyTo(o) } }
    }

    /** Intent del selector "Guardar en…" del sistema (ACTION_CREATE_DOCUMENT). */
    fun createDocumentIntent(mime: String, suggestedName: String): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mime
            putExtra(Intent.EXTRA_TITLE, suggestedName)
        }

    /** Intent para compartir un archivo de cacheDir/exports (correo, WhatsApp, Drive…). */
    fun shareIntent(context: Context, file: File, mime: String, subject: String = "Savia — ${file.name}"): Intent {
        val uri = FileProvider.getUriForFile(context, AUTHORITY, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Compartir").apply { addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
}

/** Preferencias propias del módulo de exportación. */
object ExportPrefs {
    private const val FILE = "savia_export"
    private const val KEY_LAST_BACKUP = "last_backup_millis"

    private fun sp(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Momento del último respaldo completo creado en este teléfono, o null. */
    fun lastBackup(context: Context): Long? = sp(context).getLong(KEY_LAST_BACKUP, 0L).takeIf { it > 0 }

    fun setLastBackup(context: Context, millis: Long) {
        sp(context).edit().putLong(KEY_LAST_BACKUP, millis).apply()
    }
}
