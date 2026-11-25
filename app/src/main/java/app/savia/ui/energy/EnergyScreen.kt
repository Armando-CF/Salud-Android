package app.savia.ui.energy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.savia.Savia
import app.savia.data.DailyLog
import app.savia.data.Measurement
import app.savia.data.Profile
import app.savia.energy.ActivityLevel
import app.savia.energy.EnergyCalc
import app.savia.ui.components.EmptyState
import app.savia.ui.components.InfoNote
import app.savia.ui.components.NumberField
import app.savia.ui.components.SaviaScaffold
import app.savia.ui.components.SectionCard
import app.savia.ui.nav.Routes
import app.savia.ui.nav.back
import app.savia.ui.theme.SaviaTheme
import app.savia.util.Fmt
import app.savia.util.millisToEpochDay
import app.savia.util.today
import java.util.Locale
import kotlin.math.abs

/** "2,760" (miles con coma, redondeado a decenas). */
fun kcalText(kcal: Double): String = String.format(Locale.US, "%,d", EnergyCalc.round10(kcal))

/** Lo que hace falta para calcular y, si está todo, los datos listos. */
class EnergyInputs(val weightKg: Double?, val heightCm: Double?, val age: Int?, val sex: String) {
    val missing: List<String> = buildList {
        if (weightKg == null) add("tu peso")
        if (heightCm == null) add("tu estatura")
        if (age == null) add("tu fecha de nacimiento")
    }
    val isMinor: Boolean get() = age != null && age < EnergyCalc.MIN_AGE
    val ready: Boolean get() = missing.isEmpty() && !isMinor
    val bmr: Double? get() = if (ready) EnergyCalc.bmr(weightKg!!, heightCm!!, age!!, sex) else null

    companion object {
        fun of(profile: Profile?, weights: List<Measurement>): EnergyInputs = EnergyInputs(
            weightKg = weights.maxByOrNull { it.timestamp }?.value,
            heightCm = profile?.heightCm,
            age = profile?.birthEpochDay?.let { Fmt.age(it) },
            sex = profile?.sex ?: "X",
        )
    }
}

/** Nivel elegido, o el sugerido por el ejercicio registrado, o null si no hay ninguno. */
fun effectiveLevel(chosen: String, logs: List<DailyLog>, today: Long): Pair<ActivityLevel?, Double?> {
    val perWeek = EnergyCalc.exerciseDaysPerWeek(logs.map { it.epochDay to it.exerciseMin }, today)
    return (ActivityLevel.of(chosen) ?: perWeek?.let { EnergyCalc.suggestLevel(it) }) to perWeek
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnergyScreen(nav: NavController) {
    val db = Savia.db
    val t = today()
    val profile by remember { db.profile().observe() }.collectAsStateWithLifecycle(initialValue = null)
    val weights by remember { db.measurements().observeByType("weight") }.collectAsStateWithLifecycle(initialValue = emptyList())
    val logs by remember { db.dailyLogs().observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val chosen by Savia.prefs.activityLevel.flow.collectAsStateWithLifecycle()
    val isPet = Savia.isPet

    SaviaScaffold(title = "Gasto de energía", subtitle = "Cuánto quemas al día, aproximadamente", onBack = { nav.back() }) { pad ->
        if (isPet) {
            EmptyState(Icons.Filled.LocalFireDepartment, "Solo para personas", "Este cálculo usa una fórmula para personas adultas; no aplica a mascotas.", Modifier.padding(pad))
            return@SaviaScaffold
        }
        val inputs = EnergyInputs.of(profile, weights)
        val (level, perWeek) = effectiveLevel(chosen, logs, t)
        val suggested = perWeek?.let { EnergyCalc.suggestLevel(it) }
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (inputs.isMinor) {
                InfoNote("La fórmula que usa Savia (Mifflin-St Jeor) está pensada para personas adultas. En niñas, niños y adolescentes el gasto de energía se valora con su pediatra.")
                return@Column
            }
            val bmr = inputs.bmr
            if (bmr == null) {
                SectionCard(title = "Faltan datos para calcular") {
                    Text(
                        "Para estimar tu gasto de energía hace falta ${inputs.missing.joinToString(", ").replaceLastComma()}.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (inputs.weightKg == null) Button(onClick = { nav.navigate(Routes.measure("weight", add = true)) }) { Text("Registrar peso") }
                        if (inputs.heightCm == null || inputs.age == null) OutlinedButton(onClick = { nav.navigate(Routes.PROFILE) }) { Text("Completar mi ficha") }
                    }
                }
            } else {
                // ── Resultado ──
                SectionCard {
                    Text("Tu gasto diario aproximado", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    if (level != null) {
                        Text("${kcalText(EnergyCalc.tdee(bmr, level))} kcal", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                        Text(
                            "Nivel de actividad: ${level.label.lowercase()}" + if (ActivityLevel.of(chosen) == null) " (sugerido por tu ejercicio registrado)" else "",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text("${kcalText(bmr)} kcal", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                        Text("Solo por existir (metabolismo basal). Elige abajo tu nivel de actividad para ver el total.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Calculado con tu peso más reciente (${Fmt.num(inputs.weightKg!!, 1)} kg), estatura (${Fmt.num(inputs.heightCm!!, 1)} cm) y edad (${inputs.age} años).",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (inputs.sex != "M" && inputs.sex != "F") {
                        Text(
                            "Como tu ficha no indica sexo, se usa el punto medio entre las dos versiones de la fórmula (la diferencia es de ±83 kcal).",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // ── Nivel de actividad ──
                SectionCard(title = "¿Qué tan activo es tu día?") {
                    for (l in ActivityLevel.entries) {
                        val sel = level == l && ActivityLevel.of(chosen) != null
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .background(if (sel) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                .padding(horizontal = 10.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(l.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text("${l.detail} · ${kcalText(EnergyCalc.tdee(bmr, l))} kcal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (sel) Text("Elegido", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(12.dp))
                            else TextButton(onClick = { Savia.prefs.activityLevel.value = l.key }) { Text("Elegir") }
                        }
                    }
                    if (suggested != null && perWeek != null) {
                        Spacer(Modifier.height(6.dp))
                        InfoNote(
                            "Según el ejercicio que has anotado en Bienestar (unos ${Fmt.num(perWeek, 1)} días por semana en el último mes), tu nivel sería: ${suggested.label.lowercase()}." +
                                if (ActivityLevel.of(chosen) == null) " Savia lo usa mientras no elijas otro." else "",
                        )
                    } else {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Si anotas tu ejercicio en Bienestar diario, Savia te sugiere el nivel automáticamente.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // ── Las tres partes ──
                if (level != null) {
                    val b = EnergyCalc.breakdown(bmr, level)
                    SectionCard(title = "En qué se va tu energía") {
                        PartRow("Solo por existir (metabolismo basal)", b.basal, b.share(b.basal), MaterialTheme.colorScheme.primary)
                        PartRow("Moverte y hacer ejercicio", b.movement, b.share(b.movement), SaviaTheme.colors.warn)
                        PartRow("Digerir la comida", b.digestion, b.share(b.digestion), SaviaTheme.colors.chart[2])
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Lo que haces sin entrenar (caminar, estar de pie, moverte en el trabajo) suele pesar más que el gimnasio en el total del día.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // ── Lo que dice tu peso ──
                val slope = remember(weights, t) {
                    EnergyCalc.weightSlopePerWeek(weights.filter { it.timestamp.millisToEpochDay() >= t - 42 }.map { it.timestamp / 86_400_000.0 to it.value })
                }
                if (slope != null && level != null) {
                    val balance = EnergyCalc.dailyBalanceFromTrend(slope)
                    val total = EnergyCalc.tdee(bmr, level)
                    SectionCard(title = "Lo que dice tu peso") {
                        val stable = abs(slope) < 0.1
                        Text(
                            when {
                                stable -> "Tu peso se ha mantenido estable en las últimas semanas: en promedio estás comiendo lo mismo que gastas."
                                slope < 0 -> "Tu peso baja unos ${Fmt.num(-slope, 2)} kg por semana. Eso equivale a comer alrededor de ${kcalText(-balance)} kcal menos de lo que gastas cada día."
                                else -> "Tu peso sube unos ${Fmt.num(slope, 2)} kg por semana. Eso equivale a comer alrededor de ${kcalText(balance)} kcal más de lo que gastas cada día."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (!stable) {
                            Text(
                                "Con tu gasto estimado, tu consumo promedio rondaría las ${kcalText(total + balance)} kcal al día.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "Basado en la tendencia de tus pesos de las últimas 6 semanas. Es la pista más confiable: si tu peso no cambia, lo que comes es lo que gastas.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // ── Un ejercicio en particular ──
                ExerciseCalculator(weightKg = inputs.weightKg!!, today = t)
            }

            InfoNote(
                "Son estimaciones con fórmulas de población (Mifflin-St Jeor y METs); el valor real de cada persona puede variar. " +
                    "El metabolismo basal exacto se mide con una calorimetría indirecta. Los relojes inteligentes cuentan bien pasos y pulso, " +
                    "pero en calorías pueden equivocarse entre 20 y 40 %. Esta información es orientativa y no sustituye la valoración de un profesional de la salud.",
            )
        }
    }
}

private fun String.replaceLastComma(): String {
    val i = lastIndexOf(", ")
    return if (i < 0) this else substring(0, i) + " y " + substring(i + 2)
}

@Composable
private fun PartRow(label: String, kcal: Double, percent: Int, color: Color) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("${kcalText(kcal)} kcal · $percent %", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Box(Modifier.fillMaxWidth((percent / 100f).coerceIn(0f, 1f)).height(10.dp).clip(RoundedCornerShape(50)).background(color))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseCalculator(weightKg: Double, today: Long) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    var minutes by rememberSaveable { mutableStateOf("30") }
    var open by remember { mutableStateOf(false) }
    var added by remember { mutableStateOf<String?>(null) }
    val activity = EnergyCalc.activities[index]
    val mins = minutes.toIntOrNull()?.takeIf { it in 1..1440 }
    SectionCard(title = "¿Cuánto quema un ejercicio?") {
        ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
            OutlinedTextField(
                value = activity.name,
                onValueChange = {},
                readOnly = true,
                label = { Text("Actividad") },
                supportingText = { Text("${Fmt.num(activity.met, 1)} METs (veces el gasto en reposo)") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                EnergyCalc.activities.forEachIndexed { i, a ->
                    DropdownMenuItem(text = { Text("${a.name} · ${Fmt.num(a.met, 1)} METs") }, onClick = { index = i; open = false; added = null })
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        NumberField(minutes, { minutes = it.take(4); added = null }, "Duración", suffix = "min", decimal = false, isError = minutes.isNotEmpty() && mins == null)
        Spacer(Modifier.height(8.dp))
        if (mins != null) {
            Text("≈ ${kcalText(EnergyCalc.exerciseKcal(activity.met, weightKg, mins))} kcal", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "${Fmt.num(activity.met, 1)} METs × ${Fmt.num(weightKg, 1)} kg × ${Fmt.duration(mins)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = {
                    Savia.io {
                        val dao = Savia.db.dailyLogs()
                        val cur = dao.get(today) ?: DailyLog(epochDay = today)
                        dao.save(cur.copy(exerciseMin = (cur.exerciseMin ?: 0) + mins))
                    }
                    added = "Sumados $mins min al ejercicio de hoy"
                }) { Text("Sumar a mi ejercicio de hoy") }
                Spacer(Modifier.width(10.dp))
                added?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = SaviaTheme.colors.good) }
            }
        }
    }
}
