package app.savia.ui.meds

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.savia.Savia
import app.savia.data.Appointment
import app.savia.reminders.ReminderScheduler
import app.savia.ui.components.ConfirmDialog
import app.savia.ui.components.DateTimeField
import app.savia.ui.components.EmptyState
import app.savia.ui.components.Pill
import app.savia.ui.components.SaviaScaffold
import app.savia.ui.components.SaviaTextField
import app.savia.ui.components.SectionCard
import app.savia.ui.components.SectionLabel
import app.savia.ui.nav.Routes
import app.savia.ui.nav.back
import app.savia.ui.theme.SaviaTheme
import app.savia.util.Fmt
import app.savia.util.LocaleEs
import app.savia.util.epochDayToDate
import app.savia.util.epochDayToMillis
import app.savia.util.millisToEpochDay
import app.savia.util.today
import java.time.format.TextStyle

private val specialties = listOf(
    "Medicina general", "Ginecología", "Odontología", "Dermatología", "Cardiología", "Oftalmología",
    "Nutrición", "Psicología", "Traumatología", "Urología", "Pediatría", "Laboratorio",
)

@Composable
fun AppointmentsScreen(nav: NavController) {
    val context = LocalContext.current
    val dao = Savia.db.appointments()
    val all by remember { dao.observeAll() }.collectAsStateWithLifecycle(initialValue = null as List<Appointment>?)
    val now by rememberNowMillis()

    fun setDone(a: Appointment, done: Boolean) {
        val app = context.applicationContext
        Savia.io {
            dao.update(a.copy(done = done))
            ReminderScheduler.rescheduleAll(app)
        }
    }

    SaviaScaffold(
        title = "Citas médicas",
        onBack = { nav.back() },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.appointmentEdit()) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Cita") },
            )
        },
    ) { pad ->
        val list = all ?: return@SaviaScaffold
        val upcoming = list.filter { !it.done && it.timestamp >= now }.sortedBy { it.timestamp }
        val past = list.filter { it.done || it.timestamp < now }.sortedByDescending { it.timestamp }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (list.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Event,
                        title = "Aún no tienes citas",
                        message = "Anota tus consultas y estudios programados. Savia puede avisarte un día antes y 2 horas antes.",
                        actionLabel = "Agregar cita",
                        onAction = { nav.navigate(Routes.appointmentEdit()) },
                    )
                }
            }
            if (list.isNotEmpty()) {
                item { SectionLabel("Próximas") }
                if (upcoming.isEmpty()) {
                    item {
                        Text(
                            "No tienes citas próximas.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(upcoming, key = { it.id }) { a ->
                    AppointmentCard(a, upcoming = true, onClick = { nav.navigate(Routes.appointmentEdit(a.id)) }, onToggleDone = { setDone(a, true) })
                }
            }
            if (past.isNotEmpty()) {
                item { SectionLabel("Anteriores") }
                items(past, key = { it.id }) { a ->
                    AppointmentCard(a, upcoming = false, onClick = { nav.navigate(Routes.appointmentEdit(a.id)) }, onToggleDone = { setDone(a, !a.done) })
                }
            }
        }
    }
}

@Composable
private fun AppointmentCard(a: Appointment, upcoming: Boolean, onClick: () -> Unit, onToggleDone: () -> Unit) {
    val colors = SaviaTheme.colors
    val day = a.timestamp.millisToEpochDay()
    val date = day.epochDayToDate()
    val accent = if (upcoming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    SectionCard(onClick = onClick, contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (upcoming) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (upcoming) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Column(Modifier.width(56.dp).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        date.month.getDisplayName(TextStyle.SHORT, LocaleEs).replace(".", "").uppercase(LocaleEs),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (date.year != today().epochDayToDate().year) {
                        Text(date.year.toString(), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(appointmentTitle(a), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${Fmt.dateWeekday(day)} · ${Fmt.time(a.timestamp)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (upcoming) {
                        Text(
                            " · ${Fmt.daysFromNow(day)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = accent,
                        )
                    }
                }
                val who = listOf(a.doctor, a.specialty).filter { it.isNotBlank() }.joinToString(" · ")
                if (who.isNotEmpty()) IconLine(Icons.Filled.Person, who)
                if (a.place.isNotBlank()) IconLine(Icons.Filled.Place, a.place)
                if (upcoming && a.remind) IconLine(Icons.Filled.NotificationsActive, "Con recordatorio", MaterialTheme.colorScheme.primary)
            }
        }
        if (!upcoming) {
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (a.done) Pill("Realizada", colors.good) else Pill("Sin marcar", colors.warn)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onToggleDone) { Text(if (a.done) "Desmarcar" else "Marcar como realizada") }
            }
        }
    }
}

@Composable
private fun IconLine(icon: ImageVector, text: String, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = tint, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun AppointmentEditScreen(nav: NavController, id: Long?) {
    val context = LocalContext.current
    val dao = Savia.db.appointments()

    var loaded by rememberSaveable { mutableStateOf(id == null) }
    var title by rememberSaveable { mutableStateOf("") }
    var timestamp by rememberSaveable { mutableLongStateOf(epochDayToMillis(today() + 1, 10 * 60)) }
    var doctor by rememberSaveable { mutableStateOf("") }
    var specialty by rememberSaveable { mutableStateOf("") }
    var place by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var remind by rememberSaveable { mutableStateOf(true) }
    var done by rememberSaveable { mutableStateOf(false) }
    var tried by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (!loaded && id != null) {
            dao.get(id)?.let { a ->
                title = a.title; timestamp = a.timestamp; doctor = a.doctor; specialty = a.specialty
                place = a.place; notes = a.notes; remind = a.remind; done = a.done
            }
            loaded = true
        }
    }

    fun save() {
        val item = Appointment(
            id = id ?: 0,
            timestamp = timestamp,
            title = title.trim(),
            doctor = doctor.trim(),
            specialty = specialty.trim(),
            place = place.trim(),
            notes = notes.trim(),
            remind = remind,
            done = done,
        )
        val app = context.applicationContext
        Savia.io {
            if (id == null) dao.insert(item) else dao.update(item)
            ReminderScheduler.rescheduleAll(app)
        }
        nav.back()
    }

    val access = rememberNotificationAccess(onResult = { save() })
    val isFuture = timestamp > System.currentTimeMillis()

    fun onSave() {
        tried = true
        if (title.isBlank()) return
        if (remind && !done && isFuture && !access.allowed && access.canAsk) access.request() else save()
    }

    SaviaScaffold(
        title = if (id == null) "Nueva cita" else "Editar cita",
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
            SectionCard(title = "Cita") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val titleError = tried && title.isBlank()
                    SaviaTextField(
                        title, { title = it }, "Motivo",
                        placeholder = "p. ej. Revisión anual",
                        isError = titleError,
                        supportingText = if (titleError) "Escribe el motivo de la cita" else null,
                    )
                    DateTimeField(timestamp, { timestamp = it })
                    Text(
                        Fmt.dateLong(timestamp.millisToEpochDay()) + " · " + Fmt.daysFromNow(timestamp.millisToEpochDay()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionCard(title = "Con quién y dónde") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SaviaTextField(doctor, { doctor = it }, "Profesional de salud", placeholder = "p. ej. Dra. Rivera")
                    SaviaTextField(specialty, { specialty = it }, "Especialidad")
                    if (specialty.isBlank()) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (s in specialties) SuggestionChip(onClick = { specialty = s }, label = { Text(s) })
                        }
                    }
                    SaviaTextField(place, { place = it }, "Lugar", placeholder = "Consultorio, clínica u hospital")
                }
            }

            SectionCard(title = "Notas") {
                SaviaTextField(
                    notes, { notes = it }, "Notas y preguntas para la consulta",
                    singleLine = false, minLines = 3,
                    placeholder = "Síntomas que quieres comentar, dudas, estudios que debes llevar…",
                )
            }

            SectionCard(title = "Opciones") {
                SwitchRow(
                    "Recordatorio",
                    checked = remind,
                    onCheckedChange = { remind = it },
                    subtitle = "Te avisamos un día antes y 2 horas antes",
                )
                if (id != null || !isFuture) {
                    SwitchRow(
                        "Realizada",
                        checked = done,
                        onCheckedChange = { done = it },
                        subtitle = "Márcala cuando ya hayas asistido",
                    )
                }
            }

            Button(onClick = { onSave() }, modifier = Modifier.fillMaxWidth()) { Text("Guardar") }
            if (id != null) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Eliminar cita") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmDelete && id != null) {
        ConfirmDialog(
            title = "¿Eliminar cita?",
            text = "Se borrará \"${title.ifBlank { "esta cita" }}\". Esta acción no se puede deshacer.",
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
