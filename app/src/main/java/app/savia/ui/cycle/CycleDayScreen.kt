package app.savia.ui.cycle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.savia.Savia
import app.savia.ui.measure.DoneNumberField
import app.savia.ui.wellbeing.PeopleOnlyScreen
import app.savia.data.CycleCatalog
import app.savia.data.CycleDay
import app.savia.reminders.ReminderScheduler
import app.savia.ui.components.ChoiceChips
import app.savia.ui.components.ConfirmDialog
import app.savia.ui.components.InfoNote
import app.savia.ui.components.MultiChoiceChips
import app.savia.ui.components.Pill
import app.savia.ui.components.SaviaScaffold
import app.savia.ui.components.SaviaTextField
import app.savia.ui.components.SectionCard
import app.savia.ui.nav.back
import app.savia.ui.theme.SaviaTheme
import app.savia.util.Fmt
import app.savia.util.toDoubleLenient
import app.savia.util.today

@Composable
fun CycleDayScreen(nav: NavController, epochDay: Long) {
    if (Savia.isPet) {
        PeopleOnlyScreen("Ciclo menstrual") { nav.back() }
        return
    }
    val context = LocalContext.current
    val state = rememberCycleState()
    val isFuture = epochDay > today()

    var loaded by rememberSaveable { mutableStateOf(false) }
    var existed by rememberSaveable { mutableStateOf(false) }
    var flow by rememberSaveable { mutableStateOf(0) }
    var symptoms by rememberSaveable { mutableStateOf("") }
    var moods by rememberSaveable { mutableStateOf("") }
    var mucus by rememberSaveable { mutableStateOf("") }
    var temp by rememberSaveable { mutableStateOf("") }
    var sex by rememberSaveable { mutableStateOf(0) }
    var ovTest by rememberSaveable { mutableStateOf(0) }
    var pregTest by rememberSaveable { mutableStateOf(0) }
    var contraceptive by rememberSaveable { mutableStateOf(false) }
    var notes by rememberSaveable { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(epochDay) {
        if (!loaded) {
            val d = Savia.db.cycle().get(epochDay)
            if (d != null) {
                existed = true
                flow = d.flow
                symptoms = d.symptoms
                moods = d.moods
                mucus = d.mucus
                temp = d.basalTemp?.let { Fmt.num(it, 2) } ?: ""
                sex = d.sex
                ovTest = d.ovulationTest
                pregTest = d.pregnancyTest
                contraceptive = d.contraceptive
                notes = d.notes
            }
            loaded = true
        }
    }

    val tempValue = temp.toDoubleLenient()
    val tempError = temp.isNotBlank() && (tempValue == null || tempValue !in 35.0..39.0)

    fun save() {
        if (tempError) return
        val record = CycleDay(
            epochDay = epochDay,
            flow = if (isFuture) 0 else flow,
            symptoms = symptoms,
            moods = moods,
            mucus = mucus,
            basalTemp = if (temp.isBlank()) null else tempValue,
            sex = sex,
            ovulationTest = ovTest,
            pregnancyTest = pregTest,
            contraceptive = contraceptive,
            notes = notes.trim(),
        )
        val app = context.applicationContext
        Savia.io {
            if (CycleLogic.isEmpty(record)) Savia.db.cycle().deleteDay(epochDay) else Savia.db.cycle().save(record)
            ReminderScheduler.rescheduleAll(app)
        }
        nav.back()
    }

    SaviaScaffold(
        title = Fmt.relativeDay(epochDay).let { if (it == Fmt.date(epochDay)) "Registro del día" else "Registro de ${it.lowercase()}" },
        subtitle = Fmt.dateLong(epochDay),
        onBack = { nav.back() },
        actions = {
            IconButton(onClick = { save() }, enabled = loaded && !tempError) {
                Icon(Icons.Filled.Check, contentDescription = "Guardar")
            }
        },
    ) { pad ->
        if (!loaded) return@SaviaScaffold
        // SaviaScaffold ya ajusta el contenido a la altura del teclado.
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Contexto del día dentro del ciclo
            if (state.loaded && state.analysis.hasData) {
                val info = state.analysis.info(epochDay)
                val isAnchorToday = epochDay == state.today
                val cycleDay = if (isAnchorToday) state.status.cycleDay else info.cycleDay
                val phase = if (isAnchorToday) state.status.phaseLabel else info.phase.label
                if (cycleDay != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Pill("Día $cycleDay del ciclo", MaterialTheme.colorScheme.primary)
                        Pill(
                            phase + if (epochDay > state.today || info.predictedPeriod) " (estimado)" else "",
                            if (info.inPeriod || info.predictedPeriod) SaviaTheme.colors.period else MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }

            SectionCard(title = "Sangrado") {
                if (isFuture) {
                    Text(
                        "No se puede registrar sangrado en fechas futuras. Puedes dejar notas para ese día.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    ChoiceChips(
                        options = CycleCatalog.flows.filter { it.first > 0 },
                        selected = flow.takeIf { it > 0 },
                        onSelect = { flow = it ?: 0 },
                        allowDeselect = true,
                    )
                    Text(
                        if (flow == 0) "Sin sangrado. Elige una opción si hubo." else "Toca de nuevo la opción elegida para quitarla.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionCard(title = "Síntomas") {
                val selected = remember(symptoms) { CycleCatalog.split(symptoms).toSet() }
                MultiChoiceChips(CycleCatalog.symptoms, selected, { k ->
                    symptoms = CycleCatalog.join(if (k in selected) selected - k else selected + k)
                })
            }

            SectionCard(title = "Ánimo") {
                val selected = remember(moods) { CycleCatalog.split(moods).toSet() }
                MultiChoiceChips(CycleCatalog.moods, selected, { k ->
                    moods = CycleCatalog.join(if (k in selected) selected - k else selected + k)
                })
            }

            SectionCard(title = "Señales de fertilidad") {
                Label("Flujo cervical")
                ChoiceChips(CycleCatalog.mucus, mucus.takeIf { it.isNotEmpty() }, { mucus = it ?: "" }, allowDeselect = true)
                Spacer(Modifier.height(6.dp))
                // "Listo" en el teclado lo cierra: lo que sigue son chips, no otro campo de texto.
                DoneNumberField(
                    temp, { temp = it }, "Temperatura basal", suffix = "°C", isError = tempError,
                    supportingText = if (tempError) "Escribe un valor entre 35 y 39 °C" else "Tómala al despertar, antes de levantarte",
                )
            }

            SectionCard(title = "Vida sexual y anticoncepción") {
                Label("Relaciones sexuales")
                ChoiceChips(CycleCatalog.sex, sex.takeIf { it > 0 }, { sex = it ?: 0 }, allowDeselect = true)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Anticonceptivo", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Lo tomé o apliqué hoy (pastilla, parche, anillo, inyección…)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = contraceptive, onCheckedChange = { contraceptive = it })
                }
            }

            // Las pruebas tienen su propia tarjeta para que sean fáciles de encontrar.
            SectionCard(title = "Pruebas") {
                Text(
                    "Si hoy te hiciste una prueba, anota aquí el resultado. Queda guardada en tu historial.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Label("Prueba de embarazo")
                ChoiceChips(CycleCatalog.tests, pregTest.takeIf { it > 0 }, { pregTest = it ?: 0 }, allowDeselect = true)
                if (pregTest == 2) {
                    Spacer(Modifier.height(8.dp))
                    InfoNote(
                        "Una prueba casera positiva conviene confirmarla con una prueba de laboratorio o con una persona " +
                            "profesional de la salud, quien además puede orientarte sobre los siguientes pasos.",
                    )
                }
                Spacer(Modifier.height(10.dp))
                Label("Prueba de ovulación")
                ChoiceChips(CycleCatalog.tests, ovTest.takeIf { it > 0 }, { ovTest = it ?: 0 }, allowDeselect = true)
            }

            SectionCard(title = "Notas") {
                SaviaTextField(notes, { notes = it }, "Notas del día", singleLine = false, minLines = 3)
            }

            Button(onClick = { save() }, enabled = !tempError, modifier = Modifier.fillMaxWidth()) { Text("Guardar") }
            if (existed) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Borrar registro del día", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "¿Borrar el registro?",
            text = "Se eliminará todo lo anotado el ${Fmt.date(epochDay)}, incluido el sangrado.",
            onConfirm = {
                confirmDelete = false
                val app = context.applicationContext
                Savia.io {
                    Savia.db.cycle().deleteDay(epochDay)
                    ReminderScheduler.rescheduleAll(app)
                }
                nav.back()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 2.dp),
    )
}
