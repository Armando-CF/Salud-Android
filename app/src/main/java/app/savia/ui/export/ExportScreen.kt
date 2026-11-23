package app.savia.ui.export

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.savia.Savia
import app.savia.export.Backup
import app.savia.export.BackupException
import app.savia.export.ExportFormat
import app.savia.export.ExportModule
import app.savia.export.ExportOptions
import app.savia.export.ExportPrefs
import app.savia.export.ExportTables
import app.savia.export.Exporter
import app.savia.export.RestoreMode
import app.savia.export.RestoreResult
import app.savia.ui.components.ChoiceChips
import app.savia.ui.components.ConfirmDialog
import app.savia.ui.components.DateField
import app.savia.ui.components.InfoNote
import app.savia.ui.components.SaviaScaffold
import app.savia.ui.components.SectionCard
import app.savia.ui.nav.back
import app.savia.ui.theme.SaviaTheme
import app.savia.util.Fmt
import app.savia.util.millisToEpochDay
import app.savia.util.nowMillis
import app.savia.util.today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val RANGE_ALL = "all"
private const val RANGE_YEAR = "year"
private const val RANGE_3M = "3m"
private const val RANGE_CUSTOM = "custom"

private const val PENDING_EXPORT = "export"
private const val PENDING_BACKUP = "backup"

private fun formatIcon(f: ExportFormat): ImageVector = when (f) {
    ExportFormat.XLSX -> Icons.Filled.TableChart
    ExportFormat.PDF -> Icons.Filled.PictureAsPdf
    ExportFormat.CSV -> Icons.Filled.Description
    ExportFormat.JSON -> Icons.Filled.DataObject
}

private fun errorText(e: Throwable, fallback: String): String =
    if (e is BackupException) e.message ?: fallback else "$fallback (${e.javaClass.simpleName})"

private fun restoreSummary(r: RestoreResult): String = when (r.mode) {
    RestoreMode.REPLACE -> "Respaldo restaurado: ${r.added} registros" +
        (if (r.attachmentsRestored > 0) " y ${r.attachmentsRestored} ${if (r.attachmentsRestored == 1) "archivo adjunto" else "archivos adjuntos"}" else "") + "."
    RestoreMode.MERGE -> when {
        r.added == 0 && r.updated == 0 -> "Todo lo del respaldo ya estaba en tu teléfono; no hubo cambios."
        else -> "Respaldo combinado: ${r.added} registros agregados" +
            (if (r.updated > 0) ", ${r.updated} días actualizados" else "") + "."
    }
}

/** Exportar datos (Excel, PDF, CSV, JSON) y crear / restaurar respaldos completos. */
@Composable
fun ExportScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val cycleEnabled by Savia.prefs.cycleEnabled.flow.collectAsStateWithLifecycle()
    val hasCycleData by produceState(initialValue = false) {
        value = withContext(Dispatchers.IO) { Savia.db.cycle().getAll().isNotEmpty() }
    }
    val showCycle = cycleEnabled || hasCycleData

    // ---- Estado del formulario (los módulos se guardan como nombres separados por coma)
    var modulesCsv by rememberSaveable {
        mutableStateOf(ExportModule.entries.filter { it != ExportModule.CICLO || Savia.prefs.cycleEnabled.value }.joinToString(",") { it.name })
    }
    val modules = remember(modulesCsv) {
        modulesCsv.split(',').mapNotNull { n -> ExportModule.entries.firstOrNull { it.name == n } }.toSet()
    }
    fun setModules(set: Set<ExportModule>) {
        modulesCsv = ExportModule.entries.filter { it in set }.joinToString(",") { it.name }
    }
    var rangeKey by rememberSaveable { mutableStateOf(RANGE_ALL) }
    var fromDay by rememberSaveable { mutableStateOf(today() - 30) }
    var toDay by rememberSaveable { mutableStateOf(today()) }
    var formatName by rememberSaveable { mutableStateOf(ExportFormat.XLSX.name) }
    val format = ExportFormat.valueOf(formatName)

    val options = remember(modules, rangeKey, fromDay, toDay) {
        val t = today()
        when (rangeKey) {
            RANGE_YEAR -> ExportOptions(modules, t - 365, t)
            RANGE_3M -> ExportOptions(modules, t - 90, t)
            RANGE_CUSTOM -> ExportOptions(modules, minOf(fromDay, toDay), maxOf(fromDay, toDay))
            else -> ExportOptions(modules, null, null)
        }
    }

    // ---- Estado de trabajo
    var busy by remember { mutableStateOf<String?>(null) }
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingKind by rememberSaveable { mutableStateOf(PENDING_EXPORT) }
    var lastBackup by remember { mutableStateOf(ExportPrefs.lastBackup(context)) }
    var safetyDate by remember { mutableStateOf(Backup.safetyFile(context).takeIf { it.isFile }?.lastModified()) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var confirmReplace by remember { mutableStateOf(false) }
    var confirmUndo by remember { mutableStateOf(false) }

    fun say(message: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    /** Corre trabajo que no debe cancelarse al salir de la pantalla. */
    fun work(label: String, block: suspend () -> Unit) {
        if (busy != null) return
        busy = label
        Savia.scope.launch(Dispatchers.Main) {
            try {
                block()
            } finally {
                busy = null
            }
        }
    }

    // ---- Selectores del sistema
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = if (result.resultCode == Activity.RESULT_OK) result.data?.data else null
        val path = pendingPath
        pendingPath = null
        if (uri != null && path != null) {
            val kind = pendingKind
            work("Guardando…") {
                try {
                    Exporter.copyToUri(context, File(path), uri)
                    if (kind == PENDING_BACKUP) {
                        ExportPrefs.setLastBackup(context, nowMillis())
                        lastBackup = ExportPrefs.lastBackup(context)
                        say("Respaldo guardado. Consérvalo en un lugar seguro.")
                    } else {
                        say("Archivo guardado.")
                    }
                } catch (e: Throwable) {
                    // No dejar un archivo vacío o a medias en la ubicación elegida.
                    runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                    say(errorText(e, "No se pudo guardar el archivo."))
                }
            }
        }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) restoreUri = uri
    }

    fun launchSave(file: File, mime: String, kind: String) {
        pendingPath = file.absolutePath
        pendingKind = kind
        try {
            createDocument.launch(Exporter.createDocumentIntent(mime, file.name))
        } catch (e: Exception) {
            pendingPath = null
            say("No se encontró una app para elegir dónde guardar. Usa «Compartir».")
        }
    }

    fun launchShare(context: Context, file: File, mime: String): Boolean = try {
        val intent = Exporter.shareIntent(context, file, mime)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        say("No se encontró una app para compartir el archivo.")
        false
    }

    fun doExport(share: Boolean) {
        if (modules.isEmpty()) {
            say("Elige al menos una sección para exportar.")
            return
        }
        val opts = options
        val fmt = format
        work("Preparando ${fmt.label}…") {
            try {
                val (file, data) = Exporter.prepare(context, fmt, opts)
                if (data.isEmpty) {
                    file.delete()
                    say("No hay registros en ese periodo para exportar.")
                    return@work
                }
                val mime = Exporter.mimeType(fmt, opts)
                if (share) launchShare(context, file, mime) else launchSave(file, mime, PENDING_EXPORT)
            } catch (e: Throwable) {
                say(errorText(e, "No se pudo generar el archivo."))
            }
        }
    }

    fun doBackup(share: Boolean) {
        work("Creando respaldo…") {
            try {
                val (file, _) = Exporter.backupToCache(context)
                if (share) {
                    if (launchShare(context, file, Backup.MIME)) {
                        ExportPrefs.setLastBackup(context, nowMillis())
                        lastBackup = ExportPrefs.lastBackup(context)
                    }
                } else {
                    launchSave(file, Backup.MIME, PENDING_BACKUP)
                }
            } catch (e: Throwable) {
                say(errorText(e, "No se pudo crear el respaldo."))
            }
        }
    }

    fun doRestore(uri: Uri, mode: RestoreMode) {
        work(if (mode == RestoreMode.REPLACE) "Restaurando respaldo…" else "Combinando respaldo…") {
            try {
                val result = Exporter.restore(context, uri, mode)
                safetyDate = Backup.safetyFile(context).takeIf { it.isFile }?.lastModified()
                say(restoreSummary(result))
            } catch (e: Throwable) {
                say(errorText(e, "No se pudo restaurar el respaldo."))
            }
        }
    }

    fun doUndo() {
        work("Recuperando datos anteriores…") {
            try {
                // Se copia primero: la restauración vuelve a escribir la copia de seguridad.
                val copy = withContext(Dispatchers.IO) {
                    File(Exporter.exportsDir(context), "deshacer.zip").also { Backup.safetyFile(context).copyTo(it, overwrite = true) }
                }
                val result = withContext(Dispatchers.IO) { copy.inputStream().use { Exporter.restore(context, it, RestoreMode.REPLACE) } }
                withContext(Dispatchers.IO) { copy.delete() }
                safetyDate = Backup.safetyFile(context).takeIf { it.isFile }?.lastModified()
                say("Listo: se recuperaron los datos anteriores (${result.added} registros).")
            } catch (e: Throwable) {
                say(errorText(e, "No se pudieron recuperar los datos anteriores."))
            }
        }
    }

    SaviaScaffold("Exportar y respaldo", onBack = { nav.back() }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InfoNote(
                    "Tus datos viven solo en este teléfono: Savia no usa internet ni cuentas. Si cambias de teléfono o " +
                        "desinstalas la app, lo único que los conserva es un respaldo guardado fuera del teléfono.",
                )

                // ------------------------------------------------ Qué exportar
                SectionCard(title = "Qué exportar") {
                    if (showCycle) {
                        FilledTonalButton(
                            onClick = {
                                setModules(setOf(ExportModule.CICLO))
                                rangeKey = RANGE_ALL
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = SaviaTheme.colors.period, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Solo ciclo menstrual (todo el historial)")
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    val visible = ExportModule.entries.filter { it != ExportModule.CICLO || showCycle }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${modules.count { it in visible }} de ${visible.size} secciones",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { setModules(visible.toSet()) }) { Text("Todas") }
                        TextButton(onClick = { setModules(emptySet()) }) { Text("Ninguna") }
                    }
                    for (m in visible) {
                        ModuleRow(m, checked = m in modules) { on -> setModules(if (on) modules + m else modules - m) }
                    }
                }

                // ------------------------------------------------ Periodo
                SectionCard(title = "Periodo") {
                    ChoiceChips(
                        options = listOf(RANGE_ALL to "Todo", RANGE_YEAR to "Último año", RANGE_3M to "Últimos 3 meses", RANGE_CUSTOM to "Personalizado"),
                        selected = rangeKey,
                        onSelect = { if (it != null) rangeKey = it },
                    )
                    if (rangeKey == RANGE_CUSTOM) {
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            DateField("Desde", fromDay, { fromDay = it }, Modifier.weight(1f))
                            DateField("Hasta", toDay, { toDay = it }, Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(ExportTables.rangeLabel(options), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // ------------------------------------------------ Formato
                SectionCard(title = "Formato") {
                    val all = ExportFormat.entries
                    for (pair in all.chunked(2)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                            for (f in pair) {
                                FormatCard(f, selected = f == format, modifier = Modifier.weight(1f)) { formatName = f.name }
                            }
                        }
                    }
                    Text(format.description + ".", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (modules.isNotEmpty()) {
                        Text(
                            "Archivo: " + Exporter.fileName(format, options),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { doExport(share = false) }, enabled = busy == null && modules.isNotEmpty(), modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Guardar", maxLines = 1)
                        }
                        OutlinedButton(onClick = { doExport(share = true) }, enabled = busy == null && modules.isNotEmpty(), modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Compartir", maxLines = 1)
                        }
                    }
                    Text(
                        "«Guardar» te deja elegir una carpeta del teléfono; «Compartir» lo envía por correo, mensajería o a la nube.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                // ------------------------------------------------ Respaldo
                SectionCard(title = "Respaldo completo") {
                    Text(
                        "Un solo archivo con toda tu información, tus ajustes y los archivos adjuntos. Es el único formato que " +
                            "se puede volver a cargar en Savia (por ejemplo, al estrenar teléfono).",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    BackupStatus(lastBackup)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { doBackup(share = false) }, enabled = busy == null, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Crear respaldo", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(onClick = { doBackup(share = true) }, enabled = busy == null, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Compartir", maxLines = 1)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Text("Restaurar", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Carga un respaldo creado antes con Savia. Podrás elegir entre combinarlo con lo que ya tienes o reemplazar todo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                    )
                    OutlinedButton(
                        onClick = {
                            try {
                                openBackup.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "application/json"))
                            } catch (e: Exception) {
                                say("No se encontró una app para elegir el archivo.")
                            }
                        },
                        enabled = busy == null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Restaurar un respaldo")
                    }
                    val sd = safetyDate
                    if (sd != null) {
                        TextButton(onClick = { confirmUndo = true }, enabled = busy == null, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Recuperar los datos previos a la restauración del ${Fmt.date(sd.millisToEpochDay())}", maxLines = 2)
                        }
                    }
                }
            }

            if (busy != null) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
                    Column {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(busy ?: "", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
        }
    }

    // ---- Diálogos de restauración
    val uri = restoreUri
    if (uri != null && !confirmReplace) {
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text("Restaurar respaldo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("¿Cómo quieres cargar el respaldo?")
                    RestoreChoice(
                        title = "Combinar con lo actual",
                        text = "Conserva lo que ya tienes y agrega lo que falte. Los registros repetidos no se duplican; si un mismo día del ciclo o de bienestar está en ambos, queda como en el respaldo.",
                    ) {
                        restoreUri = null
                        doRestore(uri, RestoreMode.MERGE)
                    }
                    RestoreChoice(
                        title = "Reemplazar todo",
                        text = "Borra lo que hay ahora en la app y deja exactamente lo del respaldo. Ideal para un teléfono nuevo.",
                        destructive = true,
                    ) { confirmReplace = true }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { restoreUri = null }) { Text("Cancelar") } },
        )
    }
    if (uri != null && confirmReplace) {
        ConfirmDialog(
            title = "¿Reemplazar todos tus datos?",
            text = "Se borrará lo que hay ahora en Savia y se cargará el respaldo. Savia guarda una copia interna de tus datos actuales por si necesitas deshacerlo.",
            confirmLabel = "Reemplazar",
            onConfirm = {
                confirmReplace = false
                restoreUri = null
                doRestore(uri, RestoreMode.REPLACE)
            },
            onDismiss = { confirmReplace = false },
        )
    }
    if (confirmUndo) {
        ConfirmDialog(
            title = "¿Recuperar los datos anteriores?",
            text = "Savia volverá a dejar la información tal como estaba justo antes de la última vez que reemplazaste todo con un respaldo. Lo que tienes ahora se guardará como nueva copia interna.",
            confirmLabel = "Recuperar",
            destructive = false,
            onConfirm = {
                confirmUndo = false
                doUndo()
            },
            onDismiss = { confirmUndo = false },
        )
    }
}

@Composable
private fun ModuleRow(module: ExportModule, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(!checked) }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Column(Modifier.weight(1f)) {
            Text(module.label, style = MaterialTheme.typography.bodyLarge)
            Text(module.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FormatCard(format: ExportFormat, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) scheme.primaryContainer else scheme.surfaceContainerLow,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(formatIcon(format), contentDescription = null, tint = if (selected) scheme.primary else scheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Text(
                format.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = "Elegido", tint = scheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun BackupStatus(lastBackup: Long?) {
    val days = lastBackup?.let { today() - it.millisToEpochDay() }
    val stale = days == null || days > 30
    val color = if (stale) SaviaTheme.colors.warn else SaviaTheme.colors.good
    Surface(shape = RoundedCornerShape(14.dp), color = color.copy(alpha = 0.13f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (stale) Icons.Filled.WarningAmber else Icons.Filled.CheckCircle, contentDescription = null, tint = color)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    when {
                        lastBackup == null -> "Aún no has creado un respaldo en este teléfono"
                        else -> "Último respaldo: ${Fmt.date(lastBackup.millisToEpochDay())} (${Fmt.daysFromNow(lastBackup.millisToEpochDay())})"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    when {
                        lastBackup == null -> "Crea uno y guárdalo en la nube, en tu correo o en tu computadora."
                        stale -> "Ya pasó más de un mes. Te conviene crear uno nuevo y guardarlo fuera del teléfono."
                        else -> "Vas bien. Repite el respaldo cada mes o después de registrar algo importante."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RestoreChoice(title: String, text: String, destructive: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = scheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, if (destructive) scheme.error.copy(alpha = 0.5f) else scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = if (destructive) scheme.error else scheme.primary)
            Text(text, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}
