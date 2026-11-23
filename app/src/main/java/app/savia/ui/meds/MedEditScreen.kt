package app.savia.ui.meds

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.savia.Savia
import app.savia.data.Medication
import app.savia.reminders.ReminderScheduler
import app.savia.ui.components.ConfirmDialog
import app.savia.ui.components.DateField
import app.savia.ui.components.InfoNote
import app.savia.ui.components.OptionalDateField
import app.savia.ui.components.SaviaScaffold
import app.savia.ui.components.SaviaTextField
import app.savia.ui.components.SaviaTimePickerDialog
import app.savia.ui.components.SectionCard
import app.savia.ui.nav.back
import app.savia.util.formatHm
import app.savia.util.today

private val dayFullNames = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedEditScreen(nav: NavController, id: Long?) {
    val context = LocalContext.current
    val dao = Savia.db.medications()

    var loaded by rememberSaveable { mutableStateOf(id == null) }
    var name by rememberSaveable { mutableStateOf("") }
    var dose by rememberSaveable { mutableStateOf("") }
    var instructions by rememberSaveable { mutableStateOf("") }
    var times by rememberSaveable { mutableStateOf("") }
    var daysMask by rememberSaveable { mutableIntStateOf(MedSchedule.ALL_DAYS) }
    var start by rememberSaveable { mutableLongStateOf(today()) }
    var end by rememberSaveable { mutableStateOf<Long?>(null) }
    var remind by rememberSaveable { mutableStateOf(true) }
    var active by rememberSaveable { mutableStateOf(true) }
    var notes by rememberSaveable { mutableStateOf("") }
    var tried by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    /** Hora que se está editando en el reloj: -1 = nueva, otro valor = minutos de la hora a reemplazar. */
    var pickerFor by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(id) {
        if (!loaded && id != null) {
            dao.get(id)?.let { m ->
                name = m.name; dose = m.dose; instructions = m.instructions
                times = MedSchedule.joinTimes(MedSchedule.parseTimes(m.times))
                daysMask = m.daysMask and MedSchedule.ALL_DAYS
                start = m.startEpochDay; end = m.endEpochDay
                remind = m.remind; active = m.active; notes = m.notes
            }
            loaded = true
        }
    }

    val slots = MedSchedule.parseTimes(times)
    val nameError = tried && name.isBlank()
    val daysError = tried && slots.isNotEmpty() && daysMask == 0
    val endError = end?.let { it < start } ?: false

    fun save() {
        val med = Medication(
            id = id ?: 0,
            name = name.trim(),
            dose = dose.trim(),
            instructions = instructions.trim(),
            times = MedSchedule.joinTimes(slots),
            daysMask = if (slots.isEmpty()) MedSchedule.ALL_DAYS else daysMask,
            startEpochDay = start,
            endEpochDay = end,
            active = active,
            remind = remind,
            notes = notes.trim(),
        )
        val app = context.applicationContext
        Savia.io {
            if (id == null) dao.insert(med) else dao.update(med)
            ReminderScheduler.rescheduleAll(app)
        }
        nav.back()
    }

    // Al guardar con recordatorios, se pide el permiso de notificaciones si hace falta (Android 13+).
    val access = rememberNotificationAccess(onResult = { save() })

    fun onSave() {
        tried = true
        if (name.isBlank() || endError || (slots.isNotEmpty() && daysMask == 0)) return
        val wantsReminders = remind && active && slots.isNotEmpty()
        if (wantsReminders && !access.allowed && access.canAsk) {
            access.request()
        } else {
            save()
        }
    }

    SaviaScaffold(
        title = if (id == null) "Nuevo medicamento" else "Editar medicamento",
        onBack = { nav.back() },
        actions = {
            if (id != null) {
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Eliminar") }
            }
            TextButton(onClick = { onSave() }, enabled = loaded) { Text("Guardar") }
        },
    ) { pad ->
        if (!loaded) return@SaviaScaffold
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = "Medicamento") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SaviaTextField(
                        name, { name = it }, "Nombre",
                        placeholder = "p. ej. Loratadina",
                        isError = nameError,
                        supportingText = if (nameError) "Escribe el nombre" else null,
                    )
                    SaviaTextField(dose, { dose = it }, "Dosis", placeholder = "p. ej. 500 mg, 1 tableta, 5 ml")
                    SaviaTextField(instructions, { instructions = it }, "Indicaciones", placeholder = "p. ej. con alimentos")
                }
            }

            SectionCard(title = "Horarios") {
                if (slots.isEmpty()) {
                    Text(
                        "Sin horarios: se toma según sea necesario y registras cada toma cuando ocurra.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (m in slots) {
                        InputChip(
                            selected = true,
                            onClick = { pickerFor = m },
                            label = { Text(formatHm(m)) },
                            trailingIcon = {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Quitar ${formatHm(m)}",
                                    modifier = Modifier.size(18.dp).clickable { times = MedSchedule.joinTimes(slots - m) },
                                )
                            },
                        )
                    }
                    AssistChip(
                        onClick = { pickerFor = -1 },
                        label = { Text("Agregar hora") },
                        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text("Atajos", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SuggestionChip(onClick = { times = "08:00" }, label = { Text("Una vez al día") })
                    SuggestionChip(onClick = { times = "08:00,20:00" }, label = { Text("Cada 12 h") })
                    SuggestionChip(onClick = { times = "07:00,15:00,23:00" }, label = { Text("Cada 8 h") })
                    SuggestionChip(onClick = { times = "" }, label = { Text("Según sea necesario") })
                }
                if (slots.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("Días", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        for (i in 0..6) {
                            val on = daysMask and (1 shl i) != 0
                            Surface(
                                onClick = { daysMask = daysMask xor (1 shl i) },
                                shape = CircleShape,
                                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp).semantics { contentDescription = dayFullNames[i] + if (on) ", activado" else ", desactivado" },
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(MedSchedule.weekLetters[i], fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (daysError) "Elige al menos un día" else MedSchedule.daysSummary(daysMask),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (daysError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionCard(title = "Duración") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DateField("Inicio", start, { start = it })
                    OptionalDateField("Fin (opcional)", end, { end = it }, placeholder = "Sin fecha de fin")
                    if (endError) {
                        Text("La fecha de fin no puede ser anterior al inicio.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            SectionCard(title = "Opciones") {
                SwitchRow(
                    "Recordatorios",
                    checked = remind && slots.isNotEmpty(),
                    onCheckedChange = { remind = it },
                    subtitle = if (slots.isEmpty()) "Agrega al menos un horario para recibir avisos" else "Te avisamos a la hora de cada toma",
                    enabled = slots.isNotEmpty(),
                )
                SwitchRow(
                    "Activo",
                    checked = active,
                    onCheckedChange = { active = it },
                    subtitle = "Desactívalo si dejaste de tomarlo; su historial se conserva",
                )
                Spacer(Modifier.height(8.dp))
                SaviaTextField(notes, { notes = it }, "Notas", singleLine = false, minLines = 2, placeholder = "p. ej. quién lo recetó, efectos que notes")
            }

            if (id != null) {
                val history by remember(id) { dao.observeIntakesOf(id) }.collectAsStateWithLifecycle(initialValue = emptyList())
                if (history.isNotEmpty()) {
                    SectionCard(title = "Tomas recientes") {
                        IntakeHistory(history.take(8)) { MedActions.deleteIntake(context, it) }
                        if (history.size > 8) {
                            Text(
                                "y ${history.size - 8} más en el historial",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }

            InfoNote("Savia solo te ayuda a recordar y registrar. Sigue siempre la dosis y los horarios que te indicó tu profesional de salud.")

            Button(onClick = { onSave() }, modifier = Modifier.fillMaxWidth()) { Text("Guardar") }
            if (id != null) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Eliminar medicamento") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    pickerFor?.let { target ->
        SaviaTimePickerDialog(
            initial = if (target >= 0) target else 8 * 60,
            onPick = { picked ->
                val base = if (target >= 0) slots - target else slots
                times = MedSchedule.joinTimes(base + picked)
                pickerFor = null
            },
            onDismiss = { pickerFor = null },
        )
    }

    if (confirmDelete && id != null) {
        ConfirmDialog(
            title = "¿Eliminar medicamento?",
            text = "Se borrará \"${name.ifBlank { "este medicamento" }}\" junto con su historial de tomas. Si solo dejaste de tomarlo, puedes desactivarlo.",
            onConfirm = {
                confirmDelete = false
                val app = context.applicationContext
                Savia.io {
                    dao.get(id)?.let { dao.delete(it) }
                    ReminderScheduler.rescheduleAll(app)
                }
                nav.back()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
