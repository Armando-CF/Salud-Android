package app.savia.ui.wellbeing

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.savia.Savia
import app.savia.data.SelfExam
import app.savia.data.SelfExamTypes
import app.savia.reminders.ReminderScheduler
import app.savia.ui.components.ChoiceChips
import app.savia.ui.components.ConfirmDialog
import app.savia.ui.components.DateField
import app.savia.ui.components.InfoNote
import app.savia.ui.components.Pill
import app.savia.ui.components.SaviaScaffold
import app.savia.ui.components.SaviaTextField
import app.savia.ui.components.dismissKeyboardOnTapOutside
import app.savia.ui.components.SectionCard
import app.savia.ui.components.SectionLabel
import app.savia.ui.nav.back
import app.savia.ui.theme.SaviaTheme
import app.savia.util.Fmt
import app.savia.util.today

/** Guías breves y prudentes de autoexploración (orientativas; no sustituyen la revisión médica). */
object SelfExamGuides {
    /** Días tras los cuales se sugiere repetir la autoexploración. */
    const val REPEAT_DAYS = 30

    data class Guide(val whenToDo: String, val steps: List<String>, val consultIf: String, val note: String)

    private val guides: Map<String, Guide> = mapOf(
        "mama" to Guide(
            whenToDo = "Una vez al mes. Si menstrúas, unos días después de que termine el periodo (las mamas están menos sensibles); si no, elige un día fijo.",
            steps = listOf(
                "Frente al espejo, con los brazos a los lados, luego en alto y después con las manos en la cintura, observa si hay cambios de forma o tamaño, piel hundida, enrojecida o con aspecto de cáscara de naranja, o cambios en el pezón.",
                "En la regadera o recostándote, usa las yemas de los tres dedos medios y recorre toda la mama con pequeños círculos, con presión suave, media y firme.",
                "Cubre toda la zona: de la clavícula a la parte baja de la mama y del esternón a la axila. Revisa también la axila.",
                "Presiona suavemente el pezón y observa si sale alguna secreción.",
            ),
            consultIf = "Un bulto o endurecimiento nuevo, cambios en la piel o en el pezón, secreción, o dolor persistente en un punto.",
            note = "La mayoría de los bultos no son graves, pero siempre conviene revisarlos. La autoexploración no sustituye la revisión clínica ni los estudios (mastografía o ultrasonido) que te indiquen.",
        ),
        "testicular" to Guide(
            whenToDo = "Una vez al mes, durante o después de un baño caliente, cuando la piel del escroto está relajada.",
            steps = listOf(
                "Revisa un testículo a la vez, usando ambas manos.",
                "Hazlo rodar suavemente entre el pulgar y los demás dedos. Busca bultos duros o cambios de tamaño, forma o consistencia.",
                "Es normal que uno sea un poco más grande o cuelgue más, y sentir atrás un conducto blando (el epidídimo).",
                "Compara con lo que sentiste el mes anterior.",
            ),
            consultIf = "Un bulto (aunque no duela), aumento de tamaño, sensación de pesadez, o dolor sordo en la ingle o en la parte baja del abdomen.",
            note = "Detectar cambios a tiempo hace una gran diferencia. Ante cualquier duda, pide una valoración médica.",
        ),
        "piel" to Guide(
            whenToDo = "Una vez al mes, con buena luz, un espejo de cuerpo completo y uno de mano.",
            steps = listOf(
                "Revisa todo el cuerpo: cuero cabelludo, cara, cuello, espalda, axilas, entre los dedos, plantas de los pies y uñas.",
                "En cada lunar o mancha aplica la regla ABCDE:",
                "A · Asimetría: una mitad no se parece a la otra.",
                "B · Bordes: irregulares, borrosos o dentados.",
                "C · Color: disparejo, con varios tonos (café, negro, rojo, blanco o azul).",
                "D · Diámetro: mayor de 6 mm (como la goma de un lápiz).",
                "E · Evolución: cambia de tamaño, forma o color, da comezón o sangra.",
            ),
            consultIf = "Un lunar que cumple alguna letra del ABCDE, uno nuevo que se ve distinto a los demás, o una herida que no cicatriza.",
            note = "Tomar fotos de tus lunares te ayuda a comparar con el tiempo. Solo un profesional puede valorar una lesión.",
        ),
    )

    fun of(type: String): Guide? = guides[type]

    /** Título completo, p. ej. "Autoexploración mamaria". */
    fun title(type: String): String = when (type) {
        "mama" -> "Autoexploración mamaria"
        "testicular" -> "Autoexploración testicular"
        "piel" -> "Revisión de piel y lunares"
        else -> SelfExamTypes.label(type)
    }

    /** Tipos ordenados con el más relevante según el sexo del perfil primero (todos disponibles para cualquiera). */
    fun orderedTypes(sex: String?): List<String> {
        val all = SelfExamTypes.all.map { it.first }
        val first = when (sex) {
            "F" -> "mama"
            "M" -> "testicular"
            else -> null
        }
        return if (first == null) all else listOf(first) + all.filter { it != first }
    }

    /** Última fecha registrada por tipo. */
    fun lastByType(exams: List<SelfExam>): Map<String, Long> =
        exams.groupBy { it.type }.mapValues { (_, v) -> v.maxOf { it.epochDay } }

    /** ¿Toca repetir este tipo? (solo si ya hay al menos un registro). */
    fun isDue(lastDay: Long?, today: Long): Boolean = lastDay != null && today - lastDay >= REPEAT_DAYS
}

@Composable
fun SelfExamScreen(nav: NavController) {
    if (Savia.isPet) {
        PeopleOnlyScreen("Autoexploración") { nav.back() }
        return
    }
    val dao = Savia.db.selfExams()
    val context = LocalContext.current
    val exams by remember { dao.observeAll() }.collectAsStateWithLifecycle(initialValue = null)
    val profile by remember { Savia.db.profile().observe() }.collectAsStateWithLifecycle(initialValue = null)
    val reminder by Savia.prefs.selfExamReminder.flow.collectAsStateWithLifecycle()
    val types = remember(profile?.sex) { SelfExamGuides.orderedTypes(profile?.sex) }
    val t = today()

    /** Registro en edición (id = 0 → nuevo). */
    var editing by remember { mutableStateOf<SelfExam?>(null) }
    var deleting by remember { mutableStateOf<SelfExam?>(null) }
    var openGuide by rememberSaveable { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val setReminder: (Boolean) -> Unit = { on ->
        Savia.prefs.selfExamReminder.value = on
        if (on && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val appContext = context.applicationContext
        Savia.io { ReminderScheduler.rescheduleAll(appContext) }
    }

    SaviaScaffold(
        title = "Autoexploración",
        onBack = { nav.back() },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = SelfExam(epochDay = t, type = types.first()) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Registrar") },
            )
        },
    ) { pad ->
        val list = exams ?: return@SaviaScaffold
        val last = remember(list) { SelfExamGuides.lastByType(list) }
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "intro") {
                InfoNote(
                    "Conocer tu cuerpo te ayuda a notar cambios a tiempo. Una revisión al mes es suficiente; " +
                        "aquí puedes anotar cuándo la hiciste y si notaste algo.",
                )
            }
            item(key = "status") {
                SectionCard(title = "Tus revisiones") {
                    types.forEachIndexed { i, type ->
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        StatusRow(type = type, lastDay = last[type], today = t, onRegister = { editing = SelfExam(epochDay = t, type = type) })
                    }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Recordatorio mensual", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(
                                "Recibe un aviso cuando haya pasado un mes desde tu última revisión.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(checked = reminder, onCheckedChange = setReminder)
                    }
                }
            }
            item(key = "guides") {
                SectionCard(title = "Cómo hacerla") {
                    types.forEachIndexed { i, type ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        GuideSection(type = type, expanded = openGuide == type, onToggle = { openGuide = if (openGuide == type) null else type })
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Guía orientativa. No sustituye la revisión de un profesional de la salud.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "history_title") { SectionLabel("Historial", Modifier.padding(start = 4.dp)) }
            if (list.isEmpty()) {
                item(key = "empty") {
                    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.HealthAndSafety, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Aún no registras ninguna autoexploración", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Cuando hagas una, anótala con el botón «Registrar».",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(list, key = { it.id }) { exam ->
                ExamRow(exam, onClick = { editing = exam })
            }
        }
    }

    editing?.let { current ->
        SelfExamDialog(
            initial = current,
            types = types,
            onSave = { exam ->
                editing = null
                val appContext = context.applicationContext
                Savia.io {
                    if (exam.id == 0L) dao.insert(exam) else dao.update(exam)
                    ReminderScheduler.rescheduleAll(appContext)
                }
            },
            onDelete = if (current.id != 0L) ({ editing = null; deleting = current }) else null,
            onDismiss = { editing = null },
        )
    }

    deleting?.let { exam ->
        ConfirmDialog(
            title = "¿Eliminar este registro?",
            text = "Se borrará la ${SelfExamGuides.title(exam.type).lowercase()} del ${Fmt.date(exam.epochDay)}.",
            onConfirm = {
                deleting = null
                val appContext = context.applicationContext
                Savia.io {
                    dao.delete(exam)
                    ReminderScheduler.rescheduleAll(appContext)
                }
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun StatusRow(type: String, lastDay: Long?, today: Long, onRegister: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(SelfExamGuides.title(type), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (lastDay == null) {
                Text("Sin registros todavía", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    "Última: ${Fmt.date(lastDay)} (${Fmt.daysFromNow(lastDay)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                if (SelfExamGuides.isDue(lastDay, today)) Pill("Toca repetirla", SaviaTheme.colors.warn) else Pill("Al día", SaviaTheme.colors.good)
            }
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(onClick = onRegister, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("Registrar") }
    }
}

@Composable
private fun GuideSection(type: String, expanded: Boolean, onToggle: () -> Unit) {
    val guide = SelfExamGuides.of(type) ?: return
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onToggle).padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(SelfExamGuides.title(type), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Ocultar guía" else "Ver guía",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = expanded) {
            GuideContent(guide, Modifier.padding(start = 4.dp, end = 4.dp, bottom = 12.dp))
        }
    }
}

/** Contenido de una guía (público para poder mostrarlo en otras pantallas o en capturas). */
@Composable
fun GuideContent(guide: SelfExamGuides.Guide, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Cuándo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        Text(guide.whenToDo, style = MaterialTheme.typography.bodyMedium)
        Text("Paso a paso", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        var n = 0
        for (step in guide.steps) {
            // Los renglones "A · …" de la regla ABCDE no se numeran.
            val lettered = step.length > 3 && step[1] == ' ' && step[2] == '·'
            Row {
                Text(
                    if (lettered) "" else "${++n}.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(22.dp),
                )
                Text(step, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
        Text("Consulta si notas", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        Text(guide.consultIf, style = MaterialTheme.typography.bodyMedium)
        Text(guide.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun ExamRow(exam: SelfExam, onClick: () -> Unit) {
    SectionCard(onClick = onClick, contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(SelfExamGuides.title(exam.type), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "${Fmt.date(exam.epochDay)} · ${Fmt.daysFromNow(exam.epochDay)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            if (exam.normal) Pill("Sin cambios", SaviaTheme.colors.good) else Pill("Noté algo", SaviaTheme.colors.warn)
        }
        if (exam.notes.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(exam.notes, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SelfExamDialog(
    initial: SelfExam,
    types: List<String>,
    onSave: (SelfExam) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var exam by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Registrar autoexploración" else "Editar registro") },
        text = {
            // El diálogo es otra ventana: tocar un chip o un espacio vacío cierra el teclado de las notas.
            SelfExamForm(
                exam, types, onChange = { exam = it },
                modifier = Modifier.dismissKeyboardOnTapOutside().verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(exam.copy(epochDay = exam.epochDay.coerceAtMost(today()), notes = exam.notes.trim())) }) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

/** Formulario de una autoexploración (separado del diálogo para poder probarlo y capturarlo). */
@Composable
fun SelfExamForm(exam: SelfExam, types: List<String>, onChange: (SelfExam) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Tipo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        ChoiceChips(types.map { it to SelfExamTypes.label(it) }, exam.type, { if (it != null) onChange(exam.copy(type = it)) })
        DateField("Fecha", exam.epochDay, { onChange(exam.copy(epochDay = it)) })
        if (exam.epochDay > today()) {
            Text("La fecha no puede ser futura; se guardará con la fecha de hoy.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Text("¿Qué encontraste?", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        ChoiceChips(listOf(true to "Sin cambios", false to "Noté algo"), exam.normal, { if (it != null) onChange(exam.copy(normal = it)) })
        if (!exam.normal) {
            InfoNote(
                "Anota qué notaste y dónde. Lo más probable es que no sea grave, pero conviene que lo valore pronto un profesional de la salud.",
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        SaviaTextField(
            value = exam.notes,
            onValueChange = { onChange(exam.copy(notes = it.take(600))) },
            label = "Notas (opcional)",
            singleLine = false,
            minLines = 2,
        )
    }
}
