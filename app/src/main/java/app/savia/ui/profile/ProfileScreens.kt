package app.savia.ui.profile

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MedicalInformation
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.savia.Savia
import app.savia.data.Profile
import app.savia.data.SexOptions
import app.savia.ui.components.CenterBox
import app.savia.ui.components.ChoiceChips
import app.savia.ui.components.NumberField
import app.savia.ui.components.OptionalDateField
import app.savia.ui.components.SaviaScaffold
import app.savia.ui.components.SaviaTextField
import app.savia.ui.components.SectionCard
import app.savia.ui.nav.Routes
import app.savia.ui.nav.back
import app.savia.util.Fmt
import app.savia.util.toDoubleLenient
import app.savia.util.today
import kotlinx.coroutines.flow.map

/** valor guardado → etiqueta */
private val BloodTypes: List<Pair<String, String>> = listOf(
    "A+" to "A+", "A-" to "A−", "B+" to "B+", "B-" to "B−",
    "AB+" to "AB+", "AB-" to "AB−", "O+" to "O+", "O-" to "O−", "No sé" to "No sé",
)

private fun bloodLabel(value: String): String = BloodTypes.firstOrNull { it.first == value }?.second ?: value

/** Abre el marcador del teléfono con el número (no llama por sí solo). */
internal fun dial(context: Context, phone: String) {
    val number = phone.trim()
    if (number.isEmpty()) return
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No hay una app de teléfono disponible", Toast.LENGTH_SHORT).show()
    }
}

/** Índice de masa corporal. */
private fun bmi(weightKg: Double, heightCm: Double): Double = weightKg / ((heightCm / 100.0) * (heightCm / 100.0))

@Composable
fun ProfileScreen(nav: NavController) {
    val context = LocalContext.current
    val storedFlow = remember { Savia.db.profile().observe().map { it ?: Profile() } }
    val stored by storedFlow.collectAsStateWithLifecycle(initialValue = null)

    var loaded by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var sex by rememberSaveable { mutableStateOf("X") }
    var birth by rememberSaveable { mutableStateOf<Long?>(null) }
    var height by rememberSaveable { mutableStateOf("") }
    var goal by rememberSaveable { mutableStateOf("") }
    var blood by rememberSaveable { mutableStateOf("") }
    var allergies by rememberSaveable { mutableStateOf("") }
    var conditions by rememberSaveable { mutableStateOf("") }
    var emergencyName by rememberSaveable { mutableStateOf("") }
    var emergencyPhone by rememberSaveable { mutableStateOf("") }
    var doctorName by rememberSaveable { mutableStateOf("") }
    var doctorPhone by rememberSaveable { mutableStateOf("") }
    var insurance by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var askLeave by remember { mutableStateOf(false) }

    val baseline = stored
    LaunchedEffect(baseline) {
        val p = baseline ?: return@LaunchedEffect
        if (!loaded) {
            name = p.name
            sex = p.sex
            birth = p.birthEpochDay
            height = p.heightCm?.let { Fmt.num(it, 2) } ?: ""
            goal = p.goalWeightKg?.let { Fmt.num(it, 2) } ?: ""
            blood = p.bloodType
            allergies = p.allergies
            conditions = p.conditions
            emergencyName = p.emergencyName
            emergencyPhone = p.emergencyPhone
            doctorName = p.doctorName
            doctorPhone = p.doctorPhone
            insurance = p.insurance
            notes = p.notes
            loaded = true
        }
    }

    val heightValue = height.toDoubleLenient()
    val goalValue = goal.toDoubleLenient()
    val heightError = height.isNotBlank() && (heightValue == null || heightValue !in 40.0..260.0)
    val goalError = goal.isNotBlank() && (goalValue == null || goalValue !in 20.0..400.0)
    val valid = !heightError && !goalError

    val current = (baseline ?: Profile()).copy(
        name = name.trim(),
        sex = sex,
        birthEpochDay = birth,
        heightCm = if (height.isBlank()) null else heightValue,
        goalWeightKg = if (goal.isBlank()) null else goalValue,
        bloodType = blood,
        allergies = allergies.trim(),
        conditions = conditions.trim(),
        emergencyName = emergencyName.trim(),
        emergencyPhone = emergencyPhone.trim(),
        doctorName = doctorName.trim(),
        doctorPhone = doctorPhone.trim(),
        insurance = insurance.trim(),
        notes = notes.trim(),
    )
    val dirty = loaded && baseline != null && current != baseline

    fun save(then: () -> Unit = {}) {
        if (!valid) return
        val toSave = current
        Savia.io { Savia.db.profile().save(toSave) }
        Toast.makeText(context, "Ficha guardada", Toast.LENGTH_SHORT).show()
        then()
    }

    BackHandler(enabled = dirty) { askLeave = true }

    SaviaScaffold(
        title = "Mi ficha",
        onBack = { if (dirty) askLeave = true else nav.back() },
        actions = {
            TextButton(onClick = { save() }, enabled = dirty && valid) { Text("Guardar") }
        },
    ) { pad ->
        if (!loaded) {
            CenterBox(Modifier.padding(pad)) { Text("Cargando…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            return@SaviaScaffold
        }
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EmergencyCard(current, onCall = { dial(context, it) })

            SectionCard(title = "Datos personales") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SaviaTextField(name, { name = it.take(60) }, "Nombre", capitalization = KeyboardCapitalization.Words)
                    Column {
                        FieldLabel("Sexo")
                        ChoiceChips(SexOptions.all, sex, { sex = it ?: "X" })
                    }
                    OptionalDateField(
                        "Fecha de nacimiento", birth, { d -> if (d == null || d <= today()) birth = d },
                        placeholder = "Sin indicar",
                    )
                    birth?.let { Hint("Edad: ${Fmt.age(it)} años") }
                    NumberField(
                        height, { height = it }, "Estatura", suffix = "cm", isError = heightError,
                        supportingText = if (heightError) "Escribe tu estatura en centímetros (p. ej. 168)" else null,
                    )
                }
            }

            SectionCard(title = "Datos médicos") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column {
                        FieldLabel("Tipo de sangre")
                        ChoiceChips(BloodTypes, blood.ifEmpty { null }, { blood = it ?: "" }, allowDeselect = true)
                    }
                    SaviaTextField(
                        allergies, { allergies = it }, "Alergias", singleLine = false, minLines = 2,
                        placeholder = "Medicamentos, alimentos, látex…",
                    )
                    SaviaTextField(
                        conditions, { conditions = it }, "Padecimientos", singleLine = false, minLines = 2,
                        placeholder = "Enfermedades crónicas, cirugías, condiciones importantes…",
                    )
                }
            }

            SectionCard(title = "Contacto de emergencia") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SaviaTextField(emergencyName, { emergencyName = it.take(80) }, "Nombre y parentesco", capitalization = KeyboardCapitalization.Words)
                    PhoneField(emergencyPhone, { emergencyPhone = it }, "Teléfono", onCall = { dial(context, emergencyPhone) })
                }
            }

            SectionCard(title = "Médico de cabecera") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SaviaTextField(doctorName, { doctorName = it.take(80) }, "Nombre", capitalization = KeyboardCapitalization.Words)
                    PhoneField(doctorPhone, { doctorPhone = it }, "Teléfono", onCall = { dial(context, doctorPhone) })
                }
            }

            SectionCard(title = "Seguro médico") {
                SaviaTextField(
                    insurance, { insurance = it }, "Aseguradora y número de póliza", singleLine = false,
                    placeholder = "También puedes anotar tu número de afiliación",
                )
            }

            SectionCard(title = "Meta de peso") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumberField(
                        goal, { goal = it }, "Peso que quieres alcanzar", suffix = "kg", isError = goalError,
                        supportingText = if (goalError) "Escribe un peso en kilogramos" else null,
                    )
                    if (!goalError && !heightError && goalValue != null && heightValue != null && goal.isNotBlank() && height.isNotBlank()) {
                        Hint("Con esa meta tu índice de masa corporal sería ${Fmt.num(bmi(goalValue, heightValue), 1)}. Se muestra como línea de meta en la gráfica de peso.")
                    } else {
                        Hint("Opcional. Se muestra como línea de meta en la gráfica de peso.")
                    }
                }
            }

            SectionCard(title = "Notas") {
                SaviaTextField(
                    notes, { notes = it }, "Otras cosas que conviene tener a la mano", singleLine = false, minLines = 3,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = {
                        if (dirty && valid) save()
                        nav.navigate(Routes.EXPORT)
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Icon(Icons.Filled.IosShare, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Exportar ficha", maxLines = 1)
                }
                Button(onClick = { save() }, enabled = dirty && valid, modifier = Modifier.weight(1f).height(48.dp)) {
                    Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Guardar", maxLines = 1)
                }
            }
            Hint("Tu ficha se guarda solo en este teléfono. Puedes exportarla en PDF para llevarla a una consulta.")
            Spacer(Modifier.height(16.dp))
        }
    }

    if (askLeave) {
        AlertDialog(
            onDismissRequest = { askLeave = false },
            title = { Text("Cambios sin guardar") },
            text = {
                Text(if (valid) "¿Quieres guardar los cambios de tu ficha antes de salir?" else "Hay datos que revisar (marcados en rojo). ¿Salir sin guardar los cambios?")
            },
            confirmButton = {
                if (valid) {
                    TextButton(onClick = { askLeave = false; save { nav.back() } }) { Text("Guardar") }
                } else {
                    TextButton(onClick = { askLeave = false }) { Text("Revisar") }
                }
            },
            dismissButton = {
                Row {
                    if (valid) TextButton(onClick = { askLeave = false }) { Text("Cancelar") }
                    TextButton(onClick = { askLeave = false; nav.back() }) { Text("Descartar", color = MaterialTheme.colorScheme.error) }
                }
            },
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PhoneField(value: String, onChange: (String) -> Unit, label: String, onCall: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SaviaTextField(
            value, { v -> onChange(v.filter { it.isDigit() || it in "+ -()#*" }.take(24)) }, label,
            modifier = Modifier.weight(1f), keyboardType = KeyboardType.Phone, capitalization = KeyboardCapitalization.None,
        )
        Spacer(Modifier.width(10.dp))
        FilledTonalIconButton(onClick = onCall, enabled = value.isNotBlank(), modifier = Modifier.padding(top = 6.dp)) {
            Icon(Icons.Filled.Call, contentDescription = "Llamar")
        }
    }
}

/** Resumen de lo esencial en una emergencia, con los valores actuales del formulario. */
@Composable
private fun EmergencyCard(p: Profile, onCall: (String) -> Unit) {
    val bg = MaterialTheme.colorScheme.errorContainer
    val fg = MaterialTheme.colorScheme.onErrorContainer
    val empty = p.bloodType.isBlank() && p.allergies.isBlank() && p.conditions.isBlank() && p.emergencyName.isBlank() && p.emergencyPhone.isBlank()
    Surface(shape = RoundedCornerShape(20.dp), color = bg, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.MedicalInformation, contentDescription = null, tint = fg)
                Spacer(Modifier.width(8.dp))
                Text("Tarjeta de emergencia", style = MaterialTheme.typography.labelLarge, color = fg, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        p.name.ifBlank { "Sin nombre" },
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = fg,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    val details = listOfNotNull(
                        p.birthEpochDay?.let { "${Fmt.age(it)} años" },
                        SexOptions.label(p.sex).takeIf { p.sex == "F" || p.sex == "M" },
                        p.heightCm?.let { "${Fmt.num(it, 1)} cm" },
                    )
                    if (details.isNotEmpty()) {
                        Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = fg)
                    }
                }
                if (p.bloodType.isNotBlank() && p.bloodType != "No sé") {
                    Spacer(Modifier.width(12.dp))
                    Surface(shape = RoundedCornerShape(14.dp), color = fg) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Sangre", style = MaterialTheme.typography.labelSmall, color = bg)
                            Text(bloodLabel(p.bloodType), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = bg)
                        }
                    }
                }
            }
            if (empty) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Completa tu ficha para tener a la mano lo esencial: tipo de sangre, alergias y a quién llamar.",
                    style = MaterialTheme.typography.bodyMedium, color = fg,
                )
            } else {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = fg.copy(alpha = 0.2f))
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EmergencyLine("Alergias", p.allergies.ifBlank { "Sin registrar" })
                    EmergencyLine("Padecimientos", p.conditions.ifBlank { "Sin registrar" })
                    if (p.emergencyName.isNotBlank() || p.emergencyPhone.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                EmergencyLine("En caso de emergencia avisar a", listOf(p.emergencyName, p.emergencyPhone).filter { it.isNotBlank() }.joinToString(" · "))
                            }
                            if (p.emergencyPhone.isNotBlank()) {
                                Spacer(Modifier.width(8.dp))
                                FilledTonalIconButton(onClick = { onCall(p.emergencyPhone) }) {
                                    Icon(Icons.Filled.Call, contentDescription = "Llamar al contacto de emergencia")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmergencyLine(label: String, value: String) {
    val fg = MaterialTheme.colorScheme.onErrorContainer
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = 0.75f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = fg, fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}
