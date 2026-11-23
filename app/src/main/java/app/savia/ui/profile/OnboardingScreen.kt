package app.savia.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.savia.Savia
import app.savia.data.Profile
import app.savia.data.SexOptions
import app.savia.ui.components.ChoiceChips
import app.savia.ui.components.NumberField
import app.savia.ui.components.OptionalDateField
import app.savia.ui.components.SaviaTextField
import app.savia.ui.components.SectionCard
import app.savia.ui.lock.SaviaLogo
import app.savia.ui.theme.SaviaTheme
import app.savia.util.Fmt
import app.savia.util.toDoubleLenient
import app.savia.util.today

private const val STEPS = 4

/** Bienvenida de primer uso. Al terminar guarda el perfil/ajustes y llama a [onDone]. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    OnboardingContent(onDone = onDone)
}

/** Implementación; [initialStep] e [initialSex] existen para vistas previas y capturas. */
@Composable
internal fun OnboardingContent(onDone: () -> Unit, initialStep: Int = 0, initialSex: String? = null) {
    var step by rememberSaveable { mutableStateOf(initialStep.coerceIn(0, STEPS - 1)) }
    var name by rememberSaveable { mutableStateOf("") }
    var sex by rememberSaveable { mutableStateOf(initialSex) }
    var birth by rememberSaveable { mutableStateOf<Long?>(null) }
    var height by rememberSaveable { mutableStateOf("") }
    var cycle by rememberSaveable { mutableStateOf(initialSex == "F") }
    var cycleTouched by rememberSaveable { mutableStateOf(false) }
    var cycleLength by rememberSaveable { mutableStateOf(Savia.prefs.cycleLength.value.toString()) }
    var periodLength by rememberSaveable { mutableStateOf(Savia.prefs.periodLength.value.toString()) }

    val heightValue = height.toDoubleLenient()
    val heightError = height.isNotBlank() && (heightValue == null || heightValue !in 40.0..260.0)
    val cycleLengthValue = cycleLength.toIntOrNull()
    val periodLengthValue = periodLength.toIntOrNull()
    val cycleLengthError = cycle && cycleLength.isNotBlank() && (cycleLengthValue == null || cycleLengthValue !in 15..60)
    val periodLengthError = cycle && periodLength.isNotBlank() && (periodLengthValue == null || periodLengthValue !in 1..14)

    fun finish() {
        val prefs = Savia.prefs
        prefs.cycleEnabled.value = cycle
        if (cycle) {
            cycleLengthValue?.takeIf { it in 15..60 }?.let { prefs.cycleLength.value = it }
            periodLengthValue?.takeIf { it in 1..14 }?.let { prefs.periodLength.value = it }
        }
        when (sex) {
            "F" -> prefs.atlasSex.value = "F"
            "M" -> prefs.atlasSex.value = "M"
        }
        val cleanName = name.trim()
        val cleanSex = sex ?: "X"
        val cleanHeight = heightValue?.takeIf { !heightError }
        val cleanBirth = birth
        Savia.io {
            val dao = Savia.db.profile()
            val existing = dao.get() ?: Profile()
            dao.save(
                existing.copy(
                    name = cleanName.ifEmpty { existing.name },
                    sex = cleanSex,
                    birthEpochDay = cleanBirth ?: existing.birthEpochDay,
                    heightCm = cleanHeight ?: existing.heightCm,
                ),
            )
        }
        onDone()
    }

    BackHandler(enabled = step > 0) { step-- }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            // Progreso + omitir
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(start = 24.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(STEPS) { i ->
                        Box(
                            Modifier
                                .height(6.dp)
                                .width(if (i == step) 28.dp else 12.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
                        )
                    }
                }
                if (step < STEPS - 1) {
                    TextButton(onClick = { finish() }) { Text("Omitir") }
                }
            }

            AnimatedContent(
                targetState = step,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally { it / 4 * dir } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 * dir } + fadeOut())
                },
                label = "onboarding",
            ) { page ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (page) {
                        0 -> WelcomePage()
                        1 -> AboutYouPage(
                            name = name, onName = { name = it.take(60) },
                            sex = sex,
                            onSex = {
                                sex = it
                                if (!cycleTouched) cycle = it == "F"
                            },
                            birth = birth, onBirth = { d -> if (d == null || d <= today()) birth = d },
                            height = height, onHeight = { height = it }, heightError = heightError,
                        )
                        2 -> ModulesPage(
                            cycle = cycle,
                            onCycle = { cycle = it; cycleTouched = true },
                            cycleLength = cycleLength, onCycleLength = { cycleLength = it.take(2) }, cycleLengthError = cycleLengthError,
                            periodLength = periodLength, onPeriodLength = { periodLength = it.take(2) }, periodLengthError = periodLengthError,
                        )
                        else -> NoticePage()
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // Navegación
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (step > 0) {
                    TextButton(onClick = { step-- }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Atrás")
                    }
                }
                Spacer(Modifier.weight(1f))
                val blocked = (step == 1 && heightError) || (step == 2 && (cycleLengthError || periodLengthError))
                Button(
                    onClick = { if (step < STEPS - 1) step++ else finish() },
                    enabled = !blocked,
                    modifier = Modifier.height(48.dp),
                ) {
                    if (step < STEPS - 1) {
                        Text("Siguiente")
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    } else {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Empezar")
                    }
                }
            }
        }
    }
}

@Composable
private fun PageTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, text: String, tint: Color = MaterialTheme.colorScheme.primary) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = tint) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WelcomePage() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(12.dp))
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(176.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)))
            Box(Modifier.size(146.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer))
            SaviaLogo(size = 112.dp)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Te damos la bienvenida a Savia",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Tu bitácora de salud: síntomas, peso, estudios, medicamentos y más, en un solo lugar.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FeatureRow(Icons.Filled.Lock, "Privada de verdad", "Todo se guarda únicamente en tu teléfono.")
            FeatureRow(Icons.Filled.CloudOff, "Sin cuenta y sin internet", "No hay registro, anuncios ni servidores.")
            FeatureRow(Icons.Filled.IosShare, "Tus datos son tuyos", "Expórtalos o respáldalos cuando quieras.")
        }
    }
}

@Composable
private fun AboutYouPage(
    name: String, onName: (String) -> Unit,
    sex: String?, onSex: (String?) -> Unit,
    birth: Long?, onBirth: (Long?) -> Unit,
    height: String, onHeight: (String) -> Unit, heightError: Boolean,
) {
    PageTitle("Cuéntanos un poco de ti", "Todo es opcional y puedes cambiarlo después en «Mi ficha».")
    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SaviaTextField(name, onName, "¿Cómo te llamas?", capitalization = KeyboardCapitalization.Words)
            Column {
                Text("Sexo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ChoiceChips(SexOptions.all, sex, onSex, allowDeselect = true)
                Text(
                    "Solo se usa para elegir el modelo del atlas corporal y sugerirte módulos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OptionalDateField(
                "Fecha de nacimiento", birth, onBirth,
                placeholder = "Sin indicar",
            )
            if (birth != null) {
                Text(
                    "Edad: ${Fmt.age(birth)} años",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            NumberField(
                height, onHeight, "Estatura", suffix = "cm",
                isError = heightError,
                supportingText = if (heightError) "Escribe tu estatura en centímetros (p. ej. 168)" else "Sirve para calcular tu índice de masa corporal",
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModulesPage(
    cycle: Boolean, onCycle: (Boolean) -> Unit,
    cycleLength: String, onCycleLength: (String) -> Unit, cycleLengthError: Boolean,
    periodLength: String, onPeriodLength: (String) -> Unit, periodLengthError: Boolean,
) {
    PageTitle("Elige lo que quieres llevar", "Activa solo lo que te sirva. Puedes cambiarlo cuando quieras en Ajustes.")
    val period = SaviaTheme.colors.period
    SectionCard {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).toggleable(value = cycle, role = Role.Switch, onValueChange = onCycle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(period.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = period) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Seguimiento del ciclo menstrual", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Calendario del periodo, síntomas, predicciones y estadísticas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = cycle, onCheckedChange = null)
        }
        if (cycle) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    cycleLength, onCycleLength, "Duración del ciclo", Modifier.weight(1f),
                    suffix = "días", decimal = false, isError = cycleLengthError,
                    supportingText = if (cycleLengthError) "Entre 15 y 60" else null,
                )
                NumberField(
                    periodLength, onPeriodLength, "Duración del periodo", Modifier.weight(1f),
                    suffix = "días", decimal = false, isError = periodLengthError,
                    supportingText = if (periodLengthError) "Entre 1 y 14" else null,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Si no lo sabes, deja 28 y 5: Savia se va ajustando con tus registros.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    SectionCard(title = "Siempre disponibles") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ModuleChip(Icons.Filled.EditNote, "Síntomas")
            ModuleChip(Icons.Filled.MonitorWeight, "Peso y mediciones")
            ModuleChip(Icons.Filled.Science, "Estudios y chequeos")
            ModuleChip(Icons.Filled.Medication, "Medicamentos y citas")
            ModuleChip(Icons.Filled.Accessibility, "Atlas del cuerpo")
            ModuleChip(Icons.Filled.SentimentSatisfied, "Bienestar diario")
        }
    }
}

@Composable
private fun ModuleChip(icon: ImageVector, label: String) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun NoticePage() {
    PageTitle("Antes de empezar", "Un par de cosas importantes sobre lo que Savia es y lo que no.")
    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FeatureRow(
                Icons.Filled.HealthAndSafety, "Es una bitácora, no un servicio médico",
                "Savia no da diagnósticos ni sustituye la atención de profesionales de la salud.",
            )
            FeatureRow(
                Icons.Filled.EditNote, "Lo que anotas le sirve a quien te atiende",
                "Llevar fechas, síntomas y resultados ayuda a explicar mejor cómo te has sentido.",
            )
            FeatureRow(
                Icons.Filled.WaterDrop, "Las estimaciones son orientativas",
                "Por ejemplo, las fechas del ciclo son cálculos aproximados y no un método anticonceptivo.",
                tint = SaviaTheme.colors.period,
            )
        }
    }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Emergency, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Text(
                "En una emergencia, llama a los servicios de emergencia de tu localidad (911 en México) o acude a urgencias.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}
