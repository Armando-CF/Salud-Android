package app.savia.ui.studies

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.savia.Savia
import app.savia.data.Study
import app.savia.data.StudyAttachment
import app.savia.data.StudyCategories
import app.savia.data.StudyResult
import app.savia.ui.components.ChoiceChips
import app.savia.ui.components.ConfirmDialog
import app.savia.ui.components.DateField
import app.savia.ui.components.InfoNote
import app.savia.ui.components.NumberField
import app.savia.ui.components.OptionalDateField
import app.savia.ui.components.Pill
import app.savia.ui.components.SaviaScaffold
import app.savia.ui.components.SaviaTextField
import app.savia.ui.components.SectionCard
import app.savia.ui.nav.back
import app.savia.util.Fmt
import app.savia.util.toDoubleLenient
import app.savia.util.today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Fila editable de un resultado (todo como texto mientras se edita). */
data class ResultRowState(
    val key: Long,
    val name: String = "",
    /** true = resultado en texto ("Negativo"); false = numérico. */
    val isText: Boolean = false,
    val value: String = "",
    val text: String = "",
    val unit: String = "",
    val refMin: String = "",
    val refMax: String = "",
    val notes: String = "",
) {
    val isBlank: Boolean get() = name.isBlank() && value.isBlank() && text.isBlank()

    fun toResult(studyId: Long): StudyResult = StudyResult(
        studyId = studyId,
        name = name.trim(),
        value = if (isText) null else value.toDoubleLenient(),
        textValue = if (isText) text.trim() else "",
        unit = if (isText) "" else unit.trim(),
        refMin = if (isText) null else refMin.toDoubleLenient(),
        refMax = if (isText) null else refMax.toDoubleLenient(),
        notes = notes,
    )
}

private fun fieldNum(v: Double?): String = v?.let { Fmt.num(it, 6) } ?: ""

private fun StudyResult.toRowState(key: Long) = ResultRowState(
    key = key,
    name = name,
    isText = value == null && textValue.isNotBlank(),
    value = fieldNum(value),
    text = textValue,
    unit = unit,
    refMin = fieldNum(refMin),
    refMax = fieldNum(refMax),
    notes = notes,
)

private const val ROW_FIELDS = 9

private val RowsSaver = listSaver<SnapshotStateList<ResultRowState>, String>(
    save = { list ->
        list.flatMap { listOf(it.key.toString(), it.name, if (it.isText) "1" else "0", it.value, it.text, it.unit, it.refMin, it.refMax, it.notes) }
    },
    restore = { flat ->
        flat.chunked(ROW_FIELDS).map { ResultRowState(it[0].toLong(), it[1], it[2] == "1", it[3], it[4], it[5], it[6], it[7], it[8]) }.toMutableStateList()
    },
)

private val AttsSaver = listSaver<SnapshotStateList<AttachmentRef>, String>(
    save = { list -> list.flatMap { listOf(it.id.toString(), it.fileName, it.displayName, it.mime) } },
    restore = { flat -> flat.chunked(4).map { AttachmentRef(it[0].toLong(), it[1], it[2], it[3]) }.toMutableStateList() },
)

/** Alta y edición de un estudio. [id] = null → nuevo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudyEditScreen(nav: NavController, id: Long?) {
    val context = LocalContext.current
    val dao = Savia.db.studies()

    var loaded by rememberSaveable { mutableStateOf(id == null) }
    var title by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("lab") }
    var day by rememberSaveable { mutableLongStateOf(today()) }
    var place by rememberSaveable { mutableStateOf("") }
    var doctor by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("") }
    var summary by rememberSaveable { mutableStateOf("") }
    var followUp by rememberSaveable { mutableStateOf<Long?>(null) }
    val rows = rememberSaveable(saver = RowsSaver) { mutableStateListOf<ResultRowState>() }
    val atts = rememberSaveable(saver = AttsSaver) { mutableStateListOf<AttachmentRef>() }
    val removedAtts = rememberSaveable(saver = AttsSaver) { mutableStateListOf<AttachmentRef>() }
    var nextKey by rememberSaveable { mutableLongStateOf(1L) }
    var dirty by rememberSaveable { mutableStateOf(false) }

    var titleError by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var showGroups by remember { mutableStateOf(false) }
    var focusedKey by remember { mutableStateOf<Long?>(null) }

    val profile by remember { Savia.db.profile().observe() }.collectAsStateWithLifecycle(initialValue = null)
    val sex = profile?.sex ?: "X"
    val pastResults by remember { dao.observeAllResults() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val pastNames = remember(pastResults) { pastResults.map { it.name.trim() }.filter { it.isNotEmpty() }.distinctBy { normalizeName(it) } }

    LaunchedEffect(id) {
        if (!loaded && id != null) {
            val s = dao.get(id)
            if (s == null) {
                nav.back()
                return@LaunchedEffect
            }
            title = s.title
            category = s.category
            day = s.epochDay
            place = s.place
            doctor = s.doctor
            status = s.status
            summary = s.summary
            followUp = s.followUpEpochDay
            rows.clear()
            rows.addAll(dao.getResults(id).map { it.toRowState(nextKey++) })
            atts.clear()
            atts.addAll(dao.getAttachments(id).map { AttachmentRef(it.id, it.fileName, it.displayName, it.mime) })
            loaded = true
        }
    }

    /** Unidad y rango sugeridos: primero lo último que capturaste con ese nombre, luego el catálogo. */
    fun defaultsFor(name: String): Triple<String, Double?, Double?>? {
        val n = normalizeName(name)
        val past = pastResults.lastOrNull { it.value != null && normalizeName(it.name) == n }
        if (past != null) return Triple(past.unit, past.refMin, past.refMax)
        val p = StudyCatalog.find(name) ?: return null
        val (lo, hi) = p.rangeFor(sex)
        return Triple(p.unit, lo, hi)
    }

    fun rowFor(name: String): ResultRowState {
        val d = defaultsFor(name)
        return ResultRowState(key = nextKey++, name = name, unit = d?.first ?: "", refMin = fieldNum(d?.second), refMax = fieldNum(d?.third))
    }

    fun addGroup(groupName: String) {
        val group = StudyCatalog.groups.firstOrNull { it.name == groupName } ?: return
        val existing = rows.map { normalizeName(it.name) }.toSet()
        rows.removeAll { it.isBlank }
        for (p in group.params) if (normalizeName(p) !in existing) rows.add(rowFor(p))
        dirty = true
    }

    fun discardAndExit() {
        // Los archivos recién copiados que no se guardaron ya no se necesitan.
        val orphans = atts.filter { it.id == 0L }.map { it.fileName }
        Savia.io { orphans.forEach { StudyFiles.delete(context, it) } }
        nav.back()
    }

    fun requestExit() {
        if (dirty) showDiscard = true else nav.back()
    }

    fun save() {
        if (saving) return
        titleError = title.isBlank()
        val unnamed = rows.any { it.name.isBlank() && !it.isBlank }
        val badNumber = rows.any { r ->
            !r.isText && listOf(r.value, r.refMin, r.refMax).any { it.isNotBlank() && it.toDoubleLenient() == null }
        }
        errorMsg = when {
            titleError -> "Escribe el nombre del estudio."
            unnamed -> "Hay un resultado sin nombre de parámetro."
            badNumber -> "Revisa los números de los resultados."
            else -> null
        }
        if (errorMsg != null) return
        saving = true
        val study = Study(
            id = id ?: 0,
            epochDay = day,
            title = title.trim(),
            category = category,
            place = place.trim(),
            doctor = doctor.trim(),
            status = status,
            summary = summary.trim(),
            followUpEpochDay = followUp,
        )
        val rowList = rows.filter { it.name.isNotBlank() }
        val newAtts = atts.filter { it.id == 0L }
        val removed = removedAtts.toList()
        Savia.io {
            val sid = if (id == null) dao.insert(study) else {
                dao.update(study)
                id
            }
            dao.replaceResults(sid, rowList.map { it.toResult(sid) })
            for (a in newAtts) dao.insertAttachment(StudyAttachment(studyId = sid, fileName = a.fileName, displayName = a.displayName, mime = a.mime))
            for (a in removed) {
                if (a.id > 0) dao.deleteAttachment(StudyAttachment(id = a.id, studyId = sid, fileName = a.fileName, displayName = a.displayName, mime = a.mime))
                StudyFiles.delete(context, a.fileName)
            }
            withContext(Dispatchers.Main) { nav.back() }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            Savia.io {
                val imported = uris.mapNotNull { StudyFiles.import(context, it) }
                withContext(Dispatchers.Main) {
                    atts.addAll(imported)
                    if (imported.isNotEmpty()) dirty = true
                    if (imported.size < uris.size) errorMsg = "No se pudo copiar alguno de los archivos."
                }
            }
        }
    }

    BackHandler(enabled = dirty) { showDiscard = true }

    SaviaScaffold(
        title = if (id == null) "Nuevo estudio" else "Editar estudio",
        onBack = { requestExit() },
        actions = { TextButton(onClick = { save() }, enabled = loaded && !saving) { Text("Guardar") } },
    ) { pad ->
        if (!loaded) return@SaviaScaffold
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- Datos generales ----
            SectionCard(title = "Datos generales") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Tipo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ChoiceChips(
                        options = StudyCategories.all.map { it.first to StudyCategories.short(it.first) },
                        selected = category,
                        onSelect = { if (it != null) { category = it; dirty = true } },
                    )
                    SaviaTextField(
                        value = title,
                        onValueChange = { title = it; titleError = false; dirty = true },
                        label = "Nombre del estudio",
                        placeholder = "Ej. Química sanguínea",
                        isError = titleError,
                        supportingText = if (titleError) "Este dato es necesario" else null,
                    )
                    val tq = normalizeName(title)
                    val suggestions = remember(category, tq) {
                        StudyCatalog.titlesFor(category)
                            .filter { normalizeName(it) != tq && (tq.isEmpty() || normalizeName(it).contains(tq)) }
                            .take(6)
                    }
                    if (suggestions.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (s in suggestions) {
                                SuggestionChip(
                                    onClick = {
                                        title = s
                                        titleError = false
                                        dirty = true
                                        // Si el título corresponde a un grupo conocido y no hay resultados, se precargan sus parámetros.
                                        if (rows.all { it.isBlank }) addGroup(s)
                                    },
                                    label = { Text(s) },
                                )
                            }
                        }
                    }
                    DateField("Fecha del estudio", day, { day = it; dirty = true })
                    SaviaTextField(place, { place = it; dirty = true }, "Laboratorio, clínica u hospital", capitalization = KeyboardCapitalization.Words)
                    SaviaTextField(doctor, { doctor = it; dirty = true }, "Médico que lo indicó o atendió", capitalization = KeyboardCapitalization.Words)
                }
            }

            // ---- Resultado general ----
            SectionCard(title = "Resultado general") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChoiceChips(
                        options = StudyCategories.statuses,
                        selected = status.takeIf { it.isNotEmpty() },
                        onSelect = { status = it ?: ""; dirty = true },
                        allowDeselect = true,
                    )
                    SaviaTextField(
                        value = summary,
                        onValueChange = { summary = it; dirty = true },
                        label = "Resumen, conclusiones o indicaciones",
                        singleLine = false,
                        minLines = 3,
                    )
                    OptionalDateField("Seguimiento o próxima vez", followUp, { followUp = it; dirty = true }, placeholder = "Sin fecha de seguimiento")
                }
            }

            // ---- Resultados ----
            SectionCard(title = "Resultados") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (rows.isEmpty()) {
                        Text(
                            "Agrega los valores del reporte (por ejemplo: Glucosa 92 mg/dL) para ver después cómo cambian con el tiempo.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    rows.forEachIndexed { index, row ->
                        ResultRowEditor(
                            row = row,
                            showSuggestions = focusedKey == row.key,
                            pastNames = pastNames,
                            onFocus = { focusedKey = row.key },
                            onChange = { rows[index] = it; dirty = true },
                            onPickName = { name ->
                                val d = defaultsFor(name)
                                rows[index] = row.copy(
                                    name = name,
                                    unit = row.unit.ifBlank { d?.first ?: "" },
                                    refMin = row.refMin.ifBlank { fieldNum(d?.second) },
                                    refMax = row.refMax.ifBlank { fieldNum(d?.third) },
                                )
                                dirty = true
                            },
                            onRemove = { rows.removeAt(index); dirty = true },
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val r = ResultRowState(key = nextKey++)
                            rows.add(r)
                            focusedKey = r.key
                            dirty = true
                        }) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Agregar resultado")
                        }
                        OutlinedButton(onClick = { showGroups = true }) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Parámetros comunes")
                        }
                    }
                    InfoNote("Los rangos que se rellenan solos son orientativos para adultos. Cámbialos por los que vengan en el reporte de tu laboratorio.")
                }
            }

            // ---- Adjuntos ----
            SectionCard(title = "Adjuntos") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (atts.isEmpty()) {
                        Text(
                            "Guarda una foto o el PDF del reporte. Se copia dentro de la app y queda disponible sin conexión.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (a in atts.toList()) {
                                AttachmentTile(
                                    att = a,
                                    onClick = { StudyFiles.open(context, a) },
                                    onRemove = {
                                        atts.remove(a)
                                        if (a.id > 0) removedAtts.add(a) else Savia.io { StudyFiles.delete(context, a.fileName) }
                                        dirty = true
                                    },
                                )
                            }
                        }
                    }
                    OutlinedButton(onClick = { picker.launch(arrayOf("image/*", "application/pdf")) }) {
                        Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Agregar foto o PDF")
                    }
                }
            }

            val err = errorMsg
            if (err != null) {
                Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = { save() }, enabled = !saving, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (id == null) "Guardar estudio" else "Guardar cambios")
            }
        }
    }

    if (showDiscard) {
        ConfirmDialog(
            title = "¿Descartar cambios?",
            text = "Los cambios que hiciste en este estudio no se guardarán.",
            confirmLabel = "Descartar",
            dismissLabel = "Seguir editando",
            onConfirm = {
                showDiscard = false
                discardAndExit()
            },
            onDismiss = { showDiscard = false },
        )
    }

    if (showGroups) {
        AlertDialog(
            onDismissRequest = { showGroups = false },
            title = { Text("Parámetros comunes") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Elige un grupo para agregar sus parámetros con unidad y rango orientativo. Después solo capturas los valores.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    for (g in StudyCatalog.groups) {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    addGroup(g.name)
                                    showGroups = false
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                        ) {
                            Text(g.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(g.params.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showGroups = false }) { Text("Cerrar") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultRowEditor(
    row: ResultRowState,
    showSuggestions: Boolean,
    pastNames: List<String>,
    onFocus: () -> Unit,
    onChange: (ResultRowState) -> Unit,
    onPickName: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SaviaTextField(
                    value = row.name,
                    onValueChange = { onChange(row.copy(name = it)) },
                    label = "Parámetro",
                    placeholder = "Ej. Glucosa",
                    modifier = Modifier.weight(1f).onFocusChanged { if (it.isFocused) onFocus() },
                )
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Quitar resultado", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showSuggestions) {
                    val suggestions = remember(row.name, pastNames) { StudyCatalog.suggest(row.name, pastNames, limit = 4) }
                    if (suggestions.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (s in suggestions) SuggestionChip(onClick = { onPickName(s) }, label = { Text(s) })
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !row.isText, onClick = { onChange(row.copy(isText = false)) }, label = { Text("Número") })
                    FilterChip(selected = row.isText, onClick = { onChange(row.copy(isText = true)) }, label = { Text("Texto") })
                    Spacer(Modifier.weight(1f))
                    if (!row.isText) {
                        val f = flag(row.value.toDoubleLenient(), row.refMin.toDoubleLenient(), row.refMax.toDoubleLenient())
                        if (f != RangeFlag.SIN_RANGO) Pill(flagLabel(f), flagColor(f))
                    }
                }
                if (row.isText) {
                    SaviaTextField(
                        value = row.text,
                        onValueChange = { onChange(row.copy(text = it)) },
                        label = "Resultado",
                        placeholder = "Ej. Negativo, sin alteraciones",
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(row.value, { onChange(row.copy(value = it)) }, "Valor", Modifier.weight(1f))
                        SaviaTextField(
                            value = row.unit,
                            onValueChange = { onChange(row.copy(unit = it.take(16))) },
                            label = "Unidad",
                            modifier = Modifier.weight(1f),
                            capitalization = KeyboardCapitalization.None,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(row.refMin, { onChange(row.copy(refMin = it)) }, "Ref. mínima", Modifier.weight(1f))
                        NumberField(row.refMax, { onChange(row.copy(refMax = it)) }, "Ref. máxima", Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
