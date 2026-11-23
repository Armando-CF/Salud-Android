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

    /**
     * Resumen del perfil activo. Se toma del registro por id ([Savia.infoOf]), que no depende de que
     * `Savia.active` ya se haya puesto al día tras un cambio de perfil.
     */
    private fun activeInfo() = Savia.infoOf(Savia.activeId.value)

    /** ¿El perfil activo es una mascota? (false si Savia aún no está lista) */
    private fun activeIsPet(): Boolean = Savia.isReady && activeInfo().isPet

    /** Opciones tal como se aplicarán al perfil activo (sin los módulos que no le corresponden). */
    fun effectiveOptions(options: ExportOptions): ExportOptions = options.forProfile(activeIsPet())

    /** Texto apto para nombres de archivo: minúsculas ASCII, dígitos y guiones ("Firulaís 2" → "firulais-2"). */
    fun slug(text: String, max: Int = 24): String =
        java.text.Normalizer.normalize(text.lowercase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-').take(max).trim('-')

    /** "nombre-" del perfil activo cuando hay más de un perfil (para distinguir los archivos); si no, "". */
    private fun profilePart(): String {
        if (!Savia.isReady || Savia.profileIds.size <= 1) return ""
        val s = slug(activeInfo().displayName)
        return if (s.isEmpty()) "perfil-${Savia.activeId.value}-" else "$s-"
    }

    /** ¿El CSV saldrá como un solo archivo (una tabla) o como .zip con varios? */
    fun csvIsSingleFile(options: ExportOptions): Boolean = ExportTables.tableCount(effectiveOptions(options)) == 1

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

    /**
     * Nombre sugerido, p. ej. "savia-ciclo-2026-11-23.xlsx". Solo minúsculas ASCII, dígitos y guiones.
     * Cuando hay más de un perfil incluye el nombre del perfil activo: "savia-firulais-vacunas-2026-11-23.xlsx".
     */
    fun fileName(format: ExportFormat, options: ExportOptions, day: Long = today()): String {
        val eff = effectiveOptions(options)
        val scope = when {
            eff.modules.size == 1 -> eff.modules.first().slug
            else -> "datos"
        }
        val csvZip = if (format == ExportFormat.CSV && !csvIsSingleFile(options)) "-csv" else ""
        return "savia-${profilePart()}$scope$csvZip-${Fmt.isoDate(day)}.${extension(format, options)}"
    }

    /** Respaldo del perfil activo. */
    fun backupFileName(day: Long = today()): String = "savia-respaldo-${profilePart()}${Fmt.isoDate(day)}.zip"

    /** Respaldo completo de todos los perfiles. */
    fun fullBackupFileName(day: Long = today()): String = "savia-respaldo-completo-${Fmt.isoDate(day)}.zip"

    fun medicalCardFileName(day: Long = today()): String =
        "savia-${profilePart()}${if (activeIsPet()) "ficha" else "ficha-medica"}-${Fmt.isoDate(day)}.pdf"

    // ------------------------------------------------------------------ exportar

    /** Escribe datos ya cargados en el formato pedido (no cierra [out]... salvo los formatos zip, que lo cierran). */
    fun write(format: ExportFormat, data: ExportData, out: OutputStream) {
        when (format) {
            ExportFormat.XLSX -> XlsxWriter.write(
                ExportTables.build(data), out,
                title = "Savia — " + (if (data.profileName.isNotEmpty()) data.profileName + " — " else "") + ExportTables.rangeLabel(data.options),
            )
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

    /**
     * Ficha (una página) del perfil activo con sus medicamentos activos, el peso más reciente y sus vacunas.
     * Para una mascota sale como "Ficha de <nombre>" con especie, raza, microchip y veterinario.
     */
    suspend fun writeMedicalCard(out: OutputStream) = withContext(Dispatchers.IO) {
        val db = Savia.db
        val t = today()
        val meds = db.medications().getAll().filter { it.active && (it.endEpochDay == null || it.endEpochDay >= t) }
        val weight = db.measurements().getAll().filter { it.type == "weight" }.maxByOrNull { it.timestamp }?.value
        writeMedicalCardPdf(db.profile().get(), meds, weight, out, vaccinations = db.vaccinations().getAll())
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

    /** Respaldo completo de TODOS los perfiles en cacheDir/exports. */
    suspend fun backupAllToCache(context: Context): Pair<File, BackupAllInfo> = withContext(Dispatchers.IO) {
        val file = File(exportsDir(context), fullBackupFileName())
        try {
            val info = file.outputStream().use { Backup.writeAll(context, it) }
            file to info
        } catch (e: Throwable) {
            file.delete()
            throw e
        }
    }

    private fun openUri(context: Context, uri: Uri): InputStream = try {
        context.contentResolver.openInputStream(uri)
    } catch (e: Exception) {
        null
    } ?: throw BackupException("No se pudo abrir el archivo elegido.")

    /** Dice qué trae un archivo de respaldo (de un perfil o completo) sin cambiar nada. */
    suspend fun inspectBackup(context: Context, uri: Uri): BackupSummary = withContext(Dispatchers.IO) {
        openUri(context, uri).use { Backup.inspect(context, it) }
    }

    suspend fun inspectBackup(context: Context, input: InputStream): BackupSummary = withContext(Dispatchers.IO) {
        Backup.inspect(context, input)
    }

    /** Restaura el respaldo de un perfil sobre el perfil ACTIVO. */
    suspend fun restore(context: Context, input: InputStream, mode: RestoreMode): RestoreResult = withContext(Dispatchers.IO) {
        Backup.restore(context, input, mode)
    }

    suspend fun restore(context: Context, uri: Uri, mode: RestoreMode): RestoreResult = withContext(Dispatchers.IO) {
        openUri(context, uri).use { Backup.restore(context, it, mode) }
    }

    /** Carga el respaldo de un perfil como perfil NUEVO (ver [RestoreResult.newProfileId]); no lo activa. */
    suspend fun restoreAsNewProfile(context: Context, uri: Uri): RestoreResult = withContext(Dispatchers.IO) {
        openUri(context, uri).use { Backup.restoreAsNewProfile(context, it) }
    }

    suspend fun restoreAsNewProfile(context: Context, input: InputStream): RestoreResult = withContext(Dispatchers.IO) {
        Backup.restoreAsNewProfile(context, input)
    }

    /** Carga un respaldo completo (todos los perfiles). */
    suspend fun restoreAll(context: Context, uri: Uri, mode: RestoreAllMode, removeOtherProfiles: Boolean = false): RestoreAllResult =
        withContext(Dispatchers.IO) { openUri(context, uri).use { Backup.restoreAll(context, it, mode, removeOtherProfiles) } }

    suspend fun restoreAll(context: Context, input: InputStream, mode: RestoreAllMode, removeOtherProfiles: Boolean = false): RestoreAllResult =
        withContext(Dispatchers.IO) { Backup.restoreAll(context, input, mode, removeOtherProfiles) }

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

    /** Momento del último respaldo que cubrió TODOS los perfiles de este teléfono, o null. */
    fun lastBackup(context: Context): Long? = sp(context).getLong(KEY_LAST_BACKUP, 0L).takeIf { it > 0 }

    fun setLastBackup(context: Context, millis: Long) {
        sp(context).edit().putLong(KEY_LAST_BACKUP, millis).apply()
    }
}
