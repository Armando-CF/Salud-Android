package app.savia.ui.profile

import android.widget.Toast
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.savia.Savia
import app.savia.data.PetSpecies
import app.savia.data.Profile
import app.savia.data.ProfileInfo
import app.savia.data.SexOptions
import app.savia.reminders.ReminderScheduler
import app.savia.ui.components.ChoiceChips
import app.savia.ui.components.ConfirmDialog
import app.savia.ui.components.InfoNote
import app.savia.ui.components.OptionalDateField
import app.savia.ui.components.Pill
import app.savia.ui.components.SaviaScaffold
import app.savia.ui.components.SectionCard
import app.savia.ui.components.SectionLabel
import app.savia.ui.components.dismissKeyboardOnTapOutside
import app.savia.ui.nav.Routes
import app.savia.ui.nav.back
import app.savia.ui.theme.SaviaTheme
import app.savia.util.Fmt
import app.savia.util.toDoubleLenient
import app.savia.util.today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val REMOVE_WORD = "ELIMINAR"

/** Subtítulo de un perfil en listas: "Hija · 8 años", "Perro · 3 años y 2 meses". */
internal fun profileSubtitle(info: ProfileInfo, today: Long = today()): String =
    listOfNotNull(
        info.subtitle.takeIf { it.isNotBlank() },
        info.birthEpochDay?.let { ageText(it, today, detailed = info.isPet) }?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")

/** Lista de perfiles (personas y mascotas): cambiar de perfil, crear y eliminar. */
@Composable
fun ProfilesScreen(nav: NavController) {
    val context = LocalContext.current
    val flow = remember { Savia.summaries }
    val profiles by flow.collectAsStateWithLifecycle(initialValue = Savia.profileIds.map { Savia.infoOf(it) })
    val activeId by Savia.activeId.collectAsStateWithLifecycle()
    var removeAsk by remember { mutableStateOf<ProfileInfo?>(null) }
    var removeConfirm by remember { mutableStateOf<ProfileInfo?>(null) }

    /** Cambia de perfil. La navegación se recrea: después de esto NO se puede usar `nav`. */
    fun switchTo(info: ProfileInfo, pendingRoute: String? = null) {
        ProfileSwitch.pendingRoute = pendingRoute
        Toast.makeText(context.applicationContext, "Ahora ves el perfil de ${info.displayName}", Toast.LENGTH_SHORT).show()
        Savia.switchTo(info.id)
    }

    SaviaScaffold(title = "Perfiles", subtitle = "Familia y mascotas", onBack = { nav.back() }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            InfoNote("Cada perfil tiene su propio historial, separado de los demás. Úsalos para llevar la salud de tu familia y de tus mascotas. Toca un perfil para verlo.")

            val people = profiles.filter { !it.isPet }
            val pets = profiles.filter { it.isPet }

            SectionLabel("Personas")
            for (p in people) {
                ProfileRow(
                    info = p, active = p.id == activeId,
                    onClick = { if (p.id == activeId) nav.back() else switchTo(p) },
                    onEdit = { if (p.id == activeId) nav.navigate(Routes.PROFILE) else switchTo(p, Routes.PROFILE) },
                    onRemove = if (p.id == 1) null else ({ removeAsk = p }),
                )
            }
            FilledTonalButton(onClick = { nav.navigate(Routes.profileNew("person")) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Agregar persona")
            }

            Spacer(Modifier.height(4.dp))
            SectionLabel("Mascotas")
            if (pets.isEmpty()) {
                Hint("Aún no has agregado mascotas. Puedes llevar su peso, vacunas, síntomas, medicamentos y visitas a la veterinaria.")
            }
            for (p in pets) {
                ProfileRow(
                    info = p, active = p.id == activeId,
                    onClick = { if (p.id == activeId) nav.back() else switchTo(p) },
                    onEdit = { if (p.id == activeId) nav.navigate(Routes.PROFILE) else switchTo(p, Routes.PROFILE) },
                    onRemove = { removeAsk = p },
                )
            }
            FilledTonalButton(onClick = { nav.navigate(Routes.profileNew("pet")) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Filled.Pets, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Agregar mascota")
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    removeAsk?.let { p ->
        ConfirmDialog(
            title = "¿Eliminar el perfil de ${p.displayName}?",
            text = "Se borrará TODO el historial de ${p.displayName}: síntomas, mediciones, vacunas, estudios y sus archivos, medicamentos, citas y su ficha. " +
                "Los demás perfiles no se tocan. No se puede deshacer: si quieres conservar algo, primero expórtalo en «Exportar y respaldo» desde ese perfil.",
            confirmLabel = "Continuar",
            onConfirm = { removeAsk = null; removeConfirm = p },
            onDismiss = { removeAsk = null },
        )
    }

    removeConfirm?.let { p ->
        var typed by remember(p.id) { mutableStateOf("") }
        val ok = typed.trim().equals(REMOVE_WORD, ignoreCase = true)
        val focus = LocalFocusManager.current
        AlertDialog(
            onDismissRequest = { removeConfirm = null },
            icon = { Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Confirma la eliminación") },
            text = {
                Column(
                    Modifier.dismissKeyboardOnTapOutside().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Para eliminar definitivamente el perfil de ${p.displayName} y todo su historial, escribe $REMOVE_WORD.")
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it.take(12) },
                        singleLine = true,
                        placeholder = { Text(REMOVE_WORD) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        removeConfirm = null
                        val app = context.applicationContext
                        val name = p.displayName
                        // Si es el perfil que se está viendo, primero se regresa al perfil 1 (en el hilo principal).
                        if (p.id == Savia.activeId.value) Savia.switchTo(1)
                        Savia.io {
                            Savia.deleteProfile(p.id)
                            ReminderScheduler.rescheduleAll(app)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(app, "Se eliminó el perfil de $name", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = ok,
                ) { Text("Eliminar perfil", color = if (ok) MaterialTheme.colorScheme.error else Color.Unspecified) }
            },
            dismissButton = { TextButton(onClick = { removeConfirm = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ProfileRow(info: ProfileInfo, active: Boolean, onClick: () -> Unit, onEdit: () -> Unit, onRemove: (() -> Unit)?) {
    var menu by remember { mutableStateOf(false) }
    SectionCard(
        onClick = onClick,
        contentPadding = 12.dp,
        containerColor = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(info, size = 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(info.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = profileSubtitle(info)
                if (sub.isNotEmpty()) {
                    Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (active) {
                    Spacer(Modifier.height(4.dp))
                    Pill("Viendo ahora", MaterialTheme.colorScheme.primary)
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Opciones de ${info.displayName}") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (!active) {
                        DropdownMenuItem(
                            text = { Text("Ver este perfil") },
                            leadingIcon = { Icon(Icons.Filled.SwapHoriz, contentDescription = null) },
                            onClick = { menu = false; onClick() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Editar ficha") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = { menu = false; onEdit() },
                    )
                    if (onRemove != null) {
                        DropdownMenuItem(
                            text = { Text("Eliminar perfil", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; onRemove() },
                        )
                    }
                }
            }
        }
    }
}

private val Relations: List<Pair<String, String>> =
    listOf("Hija", "Hijo", "Pareja", "Mamá", "Papá", "Hermana", "Hermano", "Abuela", "Abuelo", "Otro").map { it to it }

/** clave → etiqueta para "esterilizado": "si" / "no" / "" (no sé). */
internal val SterilizedOptions: List<Pair<String, String>> = listOf("si" to "Sí", "no" to "No", "" to "No sé")

internal fun sterilizedKey(v: Boolean?): String = when (v) { true -> "si"; false -> "no"; null -> "" }
internal fun sterilizedValue(key: String): Boolean? = when (key) { "si" -> true; "no" -> false; else -> null }

/** ¿Conviene proponer el seguimiento del ciclo? Solo si eligieron Mujer y tiene 10 años o más (o no hay fecha). */
internal fun suggestCycle(sex: String?, birthEpochDay: Long?): Boolean =
    sex == "F" && (birthEpochDay == null || Fmt.age(birthEpochDay) >= 10)

/** Alta de un perfil nuevo. [kind] = "person" o "pet". Al guardar crea el perfil y lo activa. */
@Composable
fun NewProfileScreen(nav: NavController, kind: String) {
    val context = LocalContext.current
    val pet = kind == "pet"

    var name by rememberSaveable { mutableStateOf("") }
    var triedSave by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var sex by rememberSaveable { mutableStateOf<String?>(null) }
    var birth by rememberSaveable { mutableStateOf<Long?>(null) }
    var color by rememberSaveable { mutableIntStateOf(Savia.profileIds.size % ProfileColors.all.size) }
    // Persona
    var relation by rememberSaveable { mutableStateOf<String?>(null) }
    var relationOther by rememberSaveable { mutableStateOf("") }
    var height by rememberSaveable { mutableStateOf("") }
    var cycle by rememberSaveable { mutableStateOf(false) }
    var cycleTouched by rememberSaveable { mutableStateOf(false) }
    // Mascota
    var species by rememberSaveable { mutableStateOf("perro") }
    var breed by rememberSaveable { mutableStateOf("") }
    var sterilized by rememberSaveable { mutableStateOf("") }
    var markings by rememberSaveable { mutableStateOf("") }
    var microchip by rememberSaveable { mutableStateOf("") }
    var approxAge by rememberSaveable { mutableStateOf(false) }
    var ageYears by rememberSaveable { mutableStateOf("") }
    var ageMonths by rememberSaveable { mutableStateOf("") }

    val t = today()
    val heightValue = height.toDoubleLenient()
    val heightError = !pet && height.isNotBlank() && (heightValue == null || heightValue !in 40.0..260.0)
    val years = ageYears.toIntOrNull()
    val months = ageMonths.toIntOrNull()
    val ageError = approxAge && ((years != null && years > 120) || (months != null && months > 11))
    val effectiveBirth: Long? = if (pet && approxAge) {
        if ((years == null && months == null) || ageError) null else birthFromAge(years ?: 0, months ?: 0, t)
    } else birth
    val nameError = name.isBlank()
    val cycleOn = !pet && (if (cycleTouched) cycle else suggestCycle(sex, effectiveBirth))
    val valid = !nameError && !heightError && !ageError

    fun save() {
        triedSave = true
        if (!valid || saving) return
        saving = true
        val cleanSex = sex ?: "X"
        val profile = if (pet) {
            Profile(
                name = name.trim(), sex = cleanSex, birthEpochDay = effectiveBirth,
                kind = "pet", species = species, breed = breed.trim(), sterilized = sterilizedValue(sterilized),
                markings = markings.trim(), microchip = microchip.trim(), avatarColor = color,
            )
        } else {
            Profile(
                name = name.trim(), sex = cleanSex, birthEpochDay = effectiveBirth,
                heightCm = if (height.isBlank()) null else heightValue,
                kind = "person", relation = (if (relation == "Otro") relationOther else relation.orEmpty()).trim(), avatarColor = color,
            )
        }
        val enableCycle = cycleOn
        val app = context.applicationContext
        val shownName = profile.name
        Savia.io {
            val id = Savia.createProfile(profile) { prefs ->
                prefs.cycleEnabled.value = enableCycle
                when (cleanSex) {
                    "F" -> prefs.atlasSex.value = "F"
                    "M" -> prefs.atlasSex.value = "M"
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(app, "Listo. Ahora ves el perfil de $shownName", Toast.LENGTH_SHORT).show()
                // Recrea la navegación en Inicio con el perfil nuevo: no usar `nav` después de esto.
                Savia.switchTo(id)
            }
        }
    }

    SaviaScaffold(
        title = if (pet) "Nueva mascota" else "Nueva persona",
        onBack = { nav.back() },
        actions = { TextButton(onClick = { save() }, enabled = !saving) { Text("Guardar") } },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Vista previa del avatar
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                ProfileAvatar(ProfileInfo(id = 0, name = name, kind = if (pet) "pet" else "person", avatarColor = color), size = 56.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        name.trim().ifEmpty { if (pet) "Tu mascota" else "Nuevo perfil" },
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Hint(if (pet) "Tendrá su propio historial: peso, vacunas, síntomas y visitas." else "Tendrá su propio historial, separado del tuyo.")
                }
            }

            if (pet) {
                SectionCard(title = "Datos de la mascota") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FormTextField(
                            name, { name = it.take(60) }, "Nombre", last = true, capitalization = KeyboardCapitalization.Words,
                            isError = triedSave && nameError, supportingText = if (triedSave && nameError) "Escribe su nombre" else null,
                        )
                        Column {
                            FieldLabel("Especie")
                            ChoiceChips(PetSpecies.all, species, { species = it ?: species })
                        }
                        FormTextField(breed, { breed = it.take(60) }, "Raza (opcional)", last = true, capitalization = KeyboardCapitalization.Words, placeholder = "Mestizo, labrador, siamés…")
                        Column {
                            FieldLabel("Sexo")
                            ChoiceChips(PetSpecies.sexes, sex, { sex = it }, allowDeselect = true)
                        }
                        Column {
                            FieldLabel("¿Está esterilizado/a?")
                            ChoiceChips(SterilizedOptions, sterilized, { sterilized = it ?: "" })
                        }
                    }
                }
                SectionCard(title = "Edad") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!approxAge) {
                            OptionalDateField("Fecha de nacimiento", birth, { d -> if (d == null || d <= t) birth = d }, placeholder = "Sin indicar")
                            birth?.let { Hint("Edad: ${ageText(it, t, detailed = true)}") }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                FormNumberField(ageYears, { ageYears = it }, "Años", Modifier.weight(1f), decimal = false, maxLength = 3, isError = years != null && years > 120)
                                FormNumberField(
                                    ageMonths, { ageMonths = it }, "Meses", Modifier.weight(1f), last = true, decimal = false, maxLength = 2,
                                    isError = months != null && months > 11, supportingText = if (months != null && months > 11) "De 0 a 11" else null,
                                )
                            }
                            effectiveBirth?.let { Hint("Se guardará como nacimiento aproximado: ${Fmt.date(it)}.") }
                        }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).toggleable(value = approxAge, role = Role.Switch, onValueChange = { approxAge = it }).padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("No sé la fecha exacta", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Hint("Indica su edad aproximada y Savia calcula la fecha.")
                            }
                            Spacer(Modifier.width(8.dp))
                            Switch(checked = approxAge, onCheckedChange = null)
                        }
                    }
                }
                SectionCard(title = "Identificación (opcional)") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FormTextField(markings, { markings = it.take(200) }, "Color y señas particulares", placeholder = "Café con manchas blancas, oreja doblada…")
                        FormTextField(microchip, { microchip = it.take(40) }, "Microchip o placa", last = true, capitalization = KeyboardCapitalization.Characters)
                    }
                }
            } else {
                SectionCard(title = "Datos de la persona") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FormTextField(
                            name, { name = it.take(60) }, "Nombre", last = true, capitalization = KeyboardCapitalization.Words,
                            isError = triedSave && nameError, supportingText = if (triedSave && nameError) "Escribe su nombre" else null,
                        )
                        Column {
                            FieldLabel("Parentesco contigo")
                            ChoiceChips(Relations, relation, { relation = it }, allowDeselect = true)
                        }
                        if (relation == "Otro") {
                            FormTextField(relationOther, { relationOther = it.take(40) }, "¿Cuál?", last = true, placeholder = "Tía, sobrino, amiga…")
                        }
                        Column {
                            FieldLabel("Sexo")
                            ChoiceChips(SexOptions.all, sex, { sex = it }, allowDeselect = true)
                        }
                        OptionalDateField("Fecha de nacimiento", birth, { d -> if (d == null || d <= t) birth = d }, placeholder = "Sin indicar")
                        birth?.let { Hint("Edad: ${ageText(it, t)}") }
                        FormNumberField(
                            height, { height = it }, "Estatura (opcional)", last = true, suffix = "cm", isError = heightError,
                            supportingText = if (heightError) "Escribe la estatura en centímetros (p. ej. 128)" else null,
                        )
                    }
                }
                SectionCard(title = "Módulos") {
                    val period = SaviaTheme.colors.period
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).toggleable(value = cycleOn, role = Role.Switch, onValueChange = { cycle = it; cycleTouched = true }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = period)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Seguimiento del ciclo menstrual", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Hint("Calendario del periodo y predicciones. Se puede activar o apagar después en Ajustes.")
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(checked = cycleOn, onCheckedChange = null)
                    }
                }
            }

            SectionCard(title = "Color del perfil") {
                AvatarColorPicker(color, { color = it })
                Spacer(Modifier.height(6.dp))
                Hint("Sirve para distinguir de un vistazo de quién es lo que estás viendo.")
            }

            Button(onClick = { save() }, enabled = !saving, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (pet) "Crear perfil de mascota" else "Crear perfil")
            }
            if (triedSave && !valid) {
                Text(
                    if (nameError) "Falta el nombre." else "Revisa los datos marcados en rojo.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                )
            }
            Hint("Podrás completar su ficha después (alergias, padecimientos, ${if (pet) "veterinaria" else "contacto de emergencia"}…).")
            Spacer(Modifier.height(20.dp))
        }
    }
}
