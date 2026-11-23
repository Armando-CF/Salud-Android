package app.savia.ui.cycle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.savia.Savia
import app.savia.ui.wellbeing.PeopleOnlyScreen
import app.savia.reminders.ReminderScheduler
import app.savia.ui.components.InfoNote
import app.savia.ui.components.SaviaScaffold
import app.savia.ui.components.SectionCard
import app.savia.ui.nav.back

@Composable
fun CycleSettingsScreen(nav: NavController) {
    if (Savia.isPet) {
        PeopleOnlyScreen("Ajustes del ciclo") { nav.back() }
        return
    }
    val context = LocalContext.current
    val prefs = Savia.prefs
    val enabled by prefs.cycleEnabled.flow.collectAsStateWithLifecycle()
    val cycleLen by prefs.cycleLength.flow.collectAsStateWithLifecycle()
    val periodLen by prefs.periodLength.flow.collectAsStateWithLifecycle()
    val luteal by prefs.lutealLength.flow.collectAsStateWithLifecycle()
    val showFert by prefs.showFertility.flow.collectAsStateWithLifecycle()
    val reminder by prefs.periodReminder.flow.collectAsStateWithLifecycle()
    val state = rememberCycleState()

    fun reschedule() {
        val app = context.applicationContext
        Savia.io { ReminderScheduler.rescheduleAll(app) }
    }

    SaviaScaffold(title = "Ajustes del ciclo", onBack = { nav.back() }) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard {
                SwitchRow(
                    "Seguimiento del ciclo menstrual",
                    "Muestra el ciclo en la barra inferior y en la pantalla de inicio. Si lo desactivas, " +
                        "tus registros NO se borran: se conservan y vuelven a aparecer al activarlo.",
                    enabled,
                ) {
                    prefs.cycleEnabled.value = it
                    reschedule()
                }
            }

            SectionCard(title = "Tu ciclo típico") {
                Text(
                    "Estos valores se usan para estimar mientras no haya historial suficiente. " +
                        "En cuanto registres ciclos completos, Savia usa el promedio de tus últimos 6.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Stepper("Duración del ciclo", "Del primer día de un periodo al día anterior del siguiente", cycleLen, 15..60) {
                    prefs.cycleLength.value = it
                    reschedule()
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Stepper("Duración del periodo", "Días de sangrado", periodLen, 1..14) { prefs.periodLength.value = it }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Stepper("Fase lútea", "Días entre la ovulación y el siguiente periodo (lo habitual es 14)", luteal, 8..18) {
                    prefs.lutealLength.value = it
                }
                if (state.loaded && state.analysis.avgCycle != null) {
                    Spacer(Modifier.height(8.dp))
                    InfoNote(
                        "Según tu historial, ahora mismo Savia usa un ciclo de ${CycleLogic.dias(state.analysis.cycleLength)} " +
                            "y un periodo de ${CycleLogic.dias(state.analysis.periodLength)} para estimar.",
                    )
                }
            }

            SectionCard(title = "Predicciones y avisos") {
                SwitchRow(
                    "Mostrar fertilidad",
                    "Ventana fértil y ovulación estimadas en el calendario y el resumen.",
                    showFert,
                ) { prefs.showFertility.value = it }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SwitchRow(
                    "Recordatorio del periodo",
                    "Una notificación unos días antes de la fecha estimada de tu siguiente periodo.",
                    reminder,
                ) {
                    prefs.periodReminder.value = it
                    reschedule()
                }
            }

            InfoNote(
                "Importante: las predicciones de periodo, ovulación y ventana fértil son estimaciones estadísticas. " +
                    "NO sirven como método anticonceptivo ni para confirmar o descartar un embarazo, y no sustituyen " +
                    "una consulta médica.",
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            InfoNote("Tus datos del ciclo se guardan solo en este teléfono. Puedes exportarlos o respaldarlos desde Más → Exportar y respaldo.")
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Stepper(title: String, subtitle: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalIconButton(onClick = { onChange((value - 1).coerceIn(range)) }, enabled = value > range.first) {
            Icon(Icons.Filled.Remove, contentDescription = "Menos")
        }
        Column(Modifier.widthIn(min = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$value", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(if (value == 1) "día" else "días", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalIconButton(onClick = { onChange((value + 1).coerceIn(range)) }, enabled = value < range.last) {
            Icon(Icons.Filled.Add, contentDescription = "Más")
        }
    }
}
