package app.savia.ui.symptoms

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.savia.Savia
import app.savia.data.SymptomCatalog
import app.savia.data.SymptomEntry
import app.savia.ui.components.ChoiceChips
import app.savia.ui.components.ConfirmDialog
import app.savia.ui.components.DateTimeField
import app.savia.ui.components.InfoNote
import app.savia.ui.components.LevelSlider
import app.savia.ui.components.MultiChoiceChips
import app.savia.ui.components.NumberField
import app.savia.ui.components.SaviaScaffold
import app.savia.ui.components.SaviaTextField
import app.savia.ui.components.SectionCard
import app.savia.ui.components.SectionLabel
import app.savia.ui.nav.Routes
import app.savia.ui.nav.back
import app.savia.ui.theme.SaviaTheme
import app.savia.util.Fmt
import app.savia.util.LocaleEs
import app.savia.util.nowMillis
import app.savia.util.toDoubleLenient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.roundToInt

/**
 * Alta / edición de un síntoma.
 * @param id null = nuevo registro (se puede prellenar con [regionId], [regionLabel] y [name]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SymptomEditScreen(nav: NavController, id: Long?, regionId: String?, regionLabel: String?, name: String?) {
    val dao = Savia.db.symptoms()
    val isNew = id == null

    var loaded by rememberSaveable { mutableStateOf(isNew) }
    var missing by rememberSaveable { mutableStateOf(false) }
    var nameText by rememberSaveable { mutableStateOf(if (isNew) name ?: "" else "") }
    var intensity by rememberSaveable { mutableIntStateOf(5) }
    var timestamp by rememberSaveable { mutableLongStateOf(nowMillis()) }
    var regId by rememberSaveable { mutableStateOf(if (isNew) regionId else null) }
    var regLabel by rememberSaveable { mutableStateOf(if (isNew) regionLabel else null) }
    var durText by rememberSaveable { mutableStateOf("") }
    var durUnit by rememberSaveable { mutableStateOf("min") }
    var tagsCsv by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }

    var nameError by rememberSaveable { mutableStateOf(false) }
    var showCatalog by rememberSaveable { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showCustomTag by remember { mutableStateOf(false) }

    // Carga del registro existente (una sola vez; después manda el estado guardado).
    LaunchedEffect(id) {
        if (!loaded && id != null) {
            val e = dao.get(id)
            if (e == null) {
                missing = true
            } else {
                nameText = e.name
                intensity = e.intensity.coerceIn(1, 10)
                timestamp = e.timestamp
                regId = e.regionId
                regLabel = e.regionLabel
                val d = e.durationMin
                if (d != null && d > 0) {
                    if (d >= 60 && d % 30 == 0) {
                        durText = Fmt.num(d / 60.0, 1)
                        durUnit = "h"
                    } else {
                        durText = "$d"
                        durUnit = "min"
                    }
                }
                tagsCsv = e.tags
                notes = e.notes
            }
            loaded = true
        }
    }

    // Resultado del atlas corporal ("id|Nombre") que llega por el savedStateHandle de esta pantalla.
    val handle = remember { nav.currentBackStackEntry?.savedStateHandle }
    val pickedFlow = remember(handle) { handle?.getStateFlow(Routes.RESULT_REGION, "") ?: MutableStateFlow("") }
    val picked by pickedFlow.collectAsStateWithLifecycle()
    LaunchedEffect(picked) {
        if (picked.isNotEmpty()) {
            val parts = picked.split("|", limit = 2)
            regId = parts[0].takeIf { it.isNotBlank() }
            regLabel = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: parts[0]
            handle?.set(Routes.RESULT_REGION, "")
        }
    }

    val used by remember { dao.observeUsedNames() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val isFuture = timestamp > nowMillis() + 5 * 60_000L
    val durationMin = durText.toDoubleLenient()?.let { if (durUnit == "h") (it * 60).roundToInt() else it.roundToInt() }?.takeIf { it > 0 }

    val save: () -> Unit = {
        val n = nameText.trim().replaceFirstChar { it.titlecase(LocaleEs) }
        if (n.isEmpty()) {
            nameError = true
        } else if (!isFuture) {
            val entry = SymptomEntry(
                id = id ?: 0,
                timestamp = timestamp,
                name = n,
                intensity = intensity,
                regionId = regId,
                regionLabel = regLabel,
                durationMin = durationMin,
                tags = SymptomStats.splitTags(tagsCsv).joinToString(","),
                notes = notes.trim(),
            )
            Savia.io { if (id == null) dao.insert(entry) else dao.update(entry) }
            nav.back()
        }
    }

    SaviaScaffold(
        title = if (isNew) "Registrar síntoma" else "Editar síntoma",
        onBack = { nav.back() },
        actions = {
            if (!isNew && !missing) {
                IconButton(onClick = { showDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Eliminar") }
            }
            if (!missing) TextButton(onClick = save) { Text("Guardar") }
        },
    ) { pad ->
        if (missing) {
            Column(Modifier.padding(pad).padding(24.dp)) {
                Text("Este registro ya no existe.", style = MaterialTheme.typography.bodyLarge)
            }
            return@SaviaScaffold
        }
        if (!loaded) return@SaviaScaffold

        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Qué
            SectionCard(title = "¿Qué sientes?") {
                SaviaTextField(
                    value = nameText,
                    onValueChange = { nameText = it.take(60); nameError = false },
                    label = "Síntoma o molestia",
                    placeholder = "Ej. Dolor de cabeza",
                    isError = nameError,
                    supportingText = if (nameError) "Escribe o elige qué sientes" else null,
                )
                val q = SymptomStats.norm(nameText)
                val suggestions = remember(q, used) {
                    if (q.isEmpty()) {
                        used.take(8)
                    } else {
                        (used + SymptomCatalog.all).distinctBy { SymptomStats.norm(it) }
                            .filter { SymptomStats.norm(it).contains(q) && SymptomStats.norm(it) != q }
                            .take(8)
                    }
                }
                if (suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    SectionLabel(if (q.isEmpty()) "Los que has registrado antes" else "Sugerencias")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (s in suggestions) {
                            SuggestionChip(onClick = { nameText = s; nameError = false; showCatalog = false }, label = { Text(s) })
                        }
                    }
                }
                TextButton(onClick = { showCatalog = !showCatalog }) {
                    Icon(if (showCatalog) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (showCatalog) "Ocultar lista de síntomas comunes" else "Ver lista de síntomas comunes")
                }
                AnimatedVisibility(visible = showCatalog) {
                    Column {
                        for ((group, names) in SymptomCatalog.groups) {
                            SectionLabel(group)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (s in names) {
                                    SuggestionChip(onClick = { nameText = s; nameError = false; showCatalog = false }, label = { Text(s) })
                                }
                            }
                        }
                    }
                }
            }

            // ── Intensidad
            SectionCard(title = "¿Qué tan fuerte?") {
                LevelSlider(
                    label = "Intensidad",
                    value = intensity,
                    onChange = { intensity = it },
                    valueText = "$intensity · ${SymptomCatalog.intensityLabel(intensity)}",
                    color = SaviaTheme.colors.intensity(intensity),
                )
                Row {
                    Text("1 · apenas se nota", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text("10 · insoportable", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (intensity >= 9) {
                    Spacer(Modifier.height(10.dp))
                    InfoNote(
                        "Si el dolor es muy fuerte, apareció de repente o no cede, busca atención médica o llama a emergencias. " +
                            "Esta app no sustituye una valoración profesional.",
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            // ── Cuándo
            SectionCard(title = "¿Cuándo?") {
                DateTimeField(timestamp, { timestamp = it })
                if (isFuture) {
                    Text(
                        "La fecha y hora no pueden estar en el futuro.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NumberField(durText, { durText = it }, "Duración (opcional)", Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    ChoiceChips(listOf("min" to "min", "h" to "horas"), durUnit, { durUnit = it ?: "min" })
                }
            }

            // ── Dónde
            SectionCard(title = "¿En qué parte del cuerpo?") {
                val label = regLabel
                if (label != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        InputChip(
                            selected = true,
                            onClick = { nav.navigate(Routes.bodyPick()) },
                            label = { Text(label) },
                            leadingIcon = { Icon(Icons.Filled.Accessibility, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { regId = null; regLabel = null }) { Text("Quitar") }
                    }
                    Text(
                        "Toca la zona para cambiarla en el modelo del cuerpo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    OutlinedButton(onClick = { nav.navigate(Routes.bodyPick()) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Accessibility, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Elegir en el cuerpo")
                    }
                    Text(
                        "Opcional. Señala en el modelo dónde lo sientes; ahí verás también cómo se llama la zona.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            // ── Desencadenantes
            SectionCard(title = "¿Qué pudo provocarlo?") {
                val selected = SymptomStats.splitTags(tagsCsv)
                val options = SymptomCatalog.triggers + selected.filter { it !in SymptomCatalog.triggers }
                MultiChoiceChips(
                    options = options.map { it to it },
                    selected = selected.toSet(),
                    onToggle = { tag ->
                        tagsCsv = (if (tag in selected) selected - tag else selected + tag).joinToString(",")
                    },
                )
                AssistChip(
                    onClick = { showCustomTag = true },
                    label = { Text("Otro…") },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
                Text(
                    "Opcional. Marcar posibles causas ayuda a encontrar patrones en el resumen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Notas
            SectionCard(title = "Notas") {
                SaviaTextField(
                    value = notes,
                    onValueChange = { notes = it.take(1000) },
                    label = "Detalles (opcional)",
                    singleLine = false,
                    minLines = 3,
                    placeholder = "Cómo empezó, qué lo alivia o empeora, qué tomaste…",
                )
            }

            Button(onClick = save, modifier = Modifier.fillMaxWidth(), enabled = !isFuture) {
                Text(if (isNew) "Guardar síntoma" else "Guardar cambios", fontWeight = FontWeight.SemiBold)
            }
            if (!isNew) {
                OutlinedButton(
                    onClick = { showDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Eliminar registro")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showDelete && id != null) {
        ConfirmDialog(
            title = "¿Eliminar este registro?",
            text = "Se borrará \"${nameText.trim()}\" de tu historial. Esta acción no se puede deshacer.",
            onConfirm = {
                showDelete = false
                Savia.io { dao.get(id)?.let { dao.delete(it) } }
                nav.back()
            },
            onDismiss = { showDelete = false },
        )
    }

    if (showCustomTag) {
        var custom by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCustomTag = false },
            title = { Text("Otro desencadenante") },
            text = {
                SaviaTextField(custom, { custom = it.replace(",", " ").take(30) }, "¿Qué pudo provocarlo?", placeholder = "Ej. Cafeína")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val tag = custom.trim().replaceFirstChar { it.titlecase(LocaleEs) }
                        if (tag.isNotEmpty()) {
                            val selected = SymptomStats.splitTags(tagsCsv)
                            if (selected.none { SymptomStats.norm(it) == SymptomStats.norm(tag) }) {
                                tagsCsv = (selected + tag).joinToString(",")
                            }
                        }
                        showCustomTag = false
                    },
                ) { Text("Agregar") }
            },
            dismissButton = { TextButton(onClick = { showCustomTag = false }) { Text("Cancelar") } },
        )
    }
}
