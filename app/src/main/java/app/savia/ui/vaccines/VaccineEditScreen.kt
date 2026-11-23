package app.savia.ui.vaccines

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.savia.Savia
import app.savia.data.Vaccination
import app.savia.data.VaccinationTypes
import app.savia.reminders.ReminderScheduler
import app.savia.ui.components.ChoiceChips
import app.savia.ui.components.ConfirmDialog
import app.savia.ui.components.DateField
import app.savia.ui.components.InfoNote
import app.savia.ui.components.OptionalDateField
import app.savia.ui.components.SaviaScaffold
import app.savia.ui.components.SaviaTextField
import app.savia.ui.components.SectionCard
import app.savia.ui.nav.back
import app.savia.util.Fmt
import app.savia.util.LocaleEs
import app.savia.util.today

/** Cuántas sugerencias de nombre se muestran antes de "Ver todas". */
private const val SUGGESTIONS_SHOWN = 8

/**
 * Alta / edición de una vacuna, desparasitación u otra aplicación preventiva.
 * En alta puede venir prellenada desde la lista (ver [VaccineDraft]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VaccineEditScreen(nav: NavController, id: Long?) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val dao = Savia.db.vaccinations()
    val isNew = id == null
    val profile = rememberVaccineProfile()
    val isPet = profile.isPet
    val t = today()
    // El prellenado se consume una sola vez; después manda el estado guardado.
    val draft = remember { if (isNew) VaccineDraft.take() else null }

    var loaded by rememberSaveable { mutableStateOf(isNew) }
    var missing by rememberSaveable { mutableStateOf(false) }
    var type by rememberSaveable { mutableStateOf(draft?.type ?: VaccineCatalog.VACUNA) }
    var name by rememberSaveable { mutableStateOf(draft?.name ?: "") }
    var dose by rememberSaveable { mutableStateOf(draft?.dose ?: "") }
    var day by rememberSaveable { mutableLongStateOf(t) }
    var nextDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var place by rememberSaveable { mutableStateOf(draft?.place ?: "") }
    var lot by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var remind by rememberSaveable { mutableStateOf(true) }

    var nameError by rememberSaveable { mutableStateOf(false) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (!loaded && id != null) {
            val v = dao.get(id)
            if (v == null) {
                missing = true
            } else {
                type = v.type
                name = v.name
                dose = v.dose
                day = v.epochDay
                nextDay = v.nextDueEpochDay
                place = v.place
                lot = v.lot
                notes = v.notes
                remind = v.remind
            }
            loaded = true
        }
    }

    val everything by remember { dao.observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList())

    val typeOptions = remember(type, isPet) {
        val base = VaccinationTypes.forProfile(isPet)
        if (base.any { it.first == type }) base else base + (type to VaccinationTypes.label(type))
    }
    val match = remember(name, type, profile) { VaccineCatalog.find(profile, name, type) }
    val futureDay = day > t
    val nextError = nextDay?.let { it <= day } ?: false

    val doSave: () -> Unit = {
        val v = Vaccination(
            id = id ?: 0,
            epochDay = day,
            type = type,
            name = name.trim().replaceFirstChar { it.titlecase(LocaleEs) },
            dose = dose.trim(),
            nextDueEpochDay = nextDay,
            place = place.trim(),
            lot = lot.trim(),
            notes = notes.trim(),
            remind = remind,
        )
        val app = context.applicationContext
        Savia.io {
            if (id == null) dao.insert(v) else dao.update(v)
            ReminderScheduler.rescheduleAll(app)
        }
        nav.back()
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { doSave() }
    val save: () -> Unit = {
        focus.clearFocus()
        when {
            name.isBlank() -> nameError = true
            futureDay || nextError -> Unit
            // Si pidió aviso y falta el permiso de notificaciones (Android 13+), se solicita antes de guardar.
            remind && nextDay != null && Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ->
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> doSave()
        }
    }

    SaviaScaffold(
        title = if (isNew) "Registrar aplicación" else "Editar registro",
        subtitle = if (isPet) profile.displayName else null,
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
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Qué se aplicó
            SectionCard(title = "¿Qué se aplicó?") {
                if (typeOptions.size > 1) {
                    ChoiceChips(typeOptions, type, { k -> if (k != null) { type = k; showAll = false } })
                    Spacer(Modifier.height(6.dp))
                }
                SaviaTextField(
                    value = name,
                    onValueChange = { name = it.take(80); nameError = false },
                    label = if (type == VaccineCatalog.VACUNA) "Vacuna" else "Nombre o producto",
                    placeholder = namePlaceholder(type, isPet),
                    isError = nameError,
                    supportingText = if (nameError) "Escribe o elige qué se aplicó" else null,
                    capitalization = KeyboardCapitalization.Sentences,
                )
                // Sugerencias: primero lo ya usado en este perfil, luego el catálogo.
                val suggestions = remember(type, name, everything, match, profile) {
                    val query = if (match != null) "" else name
                    val q = VaccineCatalog.normalize(query)
                    val used = everything.filter { it.type == type }.map { it.name.trim() }
                        .filter { it.isNotEmpty() && (q.isEmpty() || VaccineCatalog.normalize(it).contains(q)) }
                    val catalog = VaccineCatalog.filter(profile, type, query).map { it.name }
                    (used + catalog).distinctBy { VaccineCatalog.normalize(it) }
                }
                if (suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    val shown = if (showAll) suggestions else suggestions.take(SUGGESTIONS_SHOWN)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (s in shown) {
                            val selected = VaccineCatalog.normalize(s) == VaccineCatalog.normalize(name)
                            FilterChip(
                                selected = selected,
                                onClick = { name = if (selected) "" else s; nameError = false; focus.clearFocus() },
                                label = { Text(s) },
                            )
                        }
                    }
                    if (suggestions.size > SUGGESTIONS_SHOWN) {
                        TextButton(onClick = { showAll = !showAll }) {
                            Text(if (showAll) "Ver menos" else "Ver todas (${suggestions.size})")
                        }
                    }
                }
                if (match != null && (match.hint.isNotEmpty() || match.intervalLabel != null)) {
                    Text(
                        listOfNotNull(match.hint.takeIf { it.isNotEmpty() }, match.intervalLabel?.let { "suele repetirse $it (orientativo)" }).joinToString(" · ")
                            .replaceFirstChar { it.titlecase(LocaleEs) },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── Dosis
            SectionCard(title = "Dosis") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (d in VaccineCatalog.doses) {
                        val selected = dose.trim().equals(d, ignoreCase = true)
                        FilterChip(selected = selected, onClick = { dose = if (selected) "" else d; focus.clearFocus() }, label = { Text(d) })
                    }
                }
                Spacer(Modifier.height(4.dp))
                SaviaTextField(
                    value = dose,
                    onValueChange = { dose = it.take(40) },
                    label = "Dosis o esquema (opcional)",
                    placeholder = "Ej. 2.ª dosis, refuerzo, 1 tableta",
                )
            }

            // ── Fechas
            SectionCard(title = "Fechas") {
                DateField("Fecha de aplicación", day, { day = it })
                if (futureDay) {
                    Text(
                        "La fecha de aplicación no puede ser futura. Si aún no se aplica, guárdala como próxima aplicación de un registro anterior.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(10.dp))
                OptionalDateField("Próxima aplicación o refuerzo", nextDay, { nextDay = it }, placeholder = "Sin próxima fecha")
                if (nextError) {
                    Text(
                        "La próxima fecha debe ser posterior a la de aplicación.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val suggested = match?.intervalDays
                    if (suggested != null) {
                        val target = VaccineLogic.plusInterval(day, suggested)
                        FilterChip(
                            selected = nextDay == target,
                            onClick = { nextDay = if (nextDay == target) null else target; focus.clearFocus() },
                            label = { Text("Sugerido: ${VaccineCatalog.inLabel(suggested)}") },
                        )
                    }
                    for ((label, months) in listOf("+1 mes" to 1, "+3 meses" to 3, "+6 meses" to 6, "+1 año" to 12)) {
                        if (suggested != null && suggested == months * 30 || (suggested == 365 && months == 12)) continue
                        val target = VaccineLogic.plusMonths(day, months)
                        AssistChip(onClick = { nextDay = target; focus.clearFocus() }, label = { Text(label) })
                    }
                }
                val nd = nextDay
                if (nd != null && !nextError) {
                    Text(
                        "Tocaría el ${Fmt.dateLong(nd).replaceFirstChar { it.lowercase(LocaleEs) }} (${VaccineLogic.dueText(nd, t).lowercase()}).",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Recordarme la próxima", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (nextDay == null) "Elige una próxima fecha para activar el aviso." else "Savia te avisará cuando se acerque la fecha.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = remind && nextDay != null, onCheckedChange = { remind = it }, enabled = nextDay != null)
                }
            }

            // ── Detalles
            SectionCard(title = "Detalles") {
                SaviaTextField(
                    value = place,
                    onValueChange = { place = it.take(80) },
                    label = if (isPet) "Veterinaria o clínica (opcional)" else "Lugar o clínica (opcional)",
                    placeholder = if (isPet) "Ej. Clínica veterinaria San Francisco" else "Ej. Centro de salud, consultorio…",
                    capitalization = KeyboardCapitalization.Words,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = lot,
                    onValueChange = { lot = it.take(40) },
                    label = { Text("Lote (opcional)") },
                    placeholder = { Text("Viene en la etiqueta o en la cartilla") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                )
                Spacer(Modifier.height(8.dp))
                SaviaTextField(
                    value = notes,
                    onValueChange = { notes = it.take(1000) },
                    label = "Notas (opcional)",
                    singleLine = false,
                    minLines = 3,
                    placeholder = if (isPet) "Reacciones, peso ese día, marca del producto…" else "Reacciones, quién la aplicó, brazo…",
                )
            }

            InfoNote(disclaimer(isPet))

            Button(onClick = save, modifier = Modifier.fillMaxWidth(), enabled = !futureDay && !nextError) {
                Text(if (isNew) "Guardar registro" else "Guardar cambios", fontWeight = FontWeight.SemiBold)
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
            text = "Se borrará \"${name.trim()}\" del ${Fmt.date(day)}. Esta acción no se puede deshacer.",
            onConfirm = {
                showDelete = false
                val app = context.applicationContext
                Savia.io {
                    dao.get(id)?.let { dao.delete(it) }
                    ReminderScheduler.rescheduleAll(app)
                }
                nav.back()
            },
            onDismiss = { showDelete = false },
        )
    }
}

private fun namePlaceholder(type: String, isPet: Boolean): String = when (type) {
    VaccineCatalog.VACUNA -> if (isPet) "Ej. Rabia" else "Ej. Influenza"
    VaccineCatalog.DESP_INTERNA -> "Ej. Desparasitante en tableta"
    VaccineCatalog.DESP_EXTERNA -> "Ej. Pipeta"
    else -> if (isPet) "Ej. Revisión preventiva" else "Ej. Desparasitación"
}
