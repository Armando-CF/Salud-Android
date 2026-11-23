package app.savia.ui.measure

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.savia.Savia
import app.savia.data.MeasureType
import app.savia.data.Measurement
import app.savia.ui.components.InfoNote
import app.savia.ui.components.Pill
import app.savia.ui.components.SaviaScaffold
import app.savia.ui.components.SectionCard
import app.savia.ui.components.SectionLabel
import app.savia.ui.components.Sparkline
import app.savia.ui.components.StatTile
import app.savia.ui.nav.Routes
import app.savia.ui.theme.SaviaTheme
import app.savia.util.Fmt
import app.savia.util.millisToEpochDay
import kotlin.math.abs

/** Hub de mediciones: peso destacado y una fila por cada tipo de medición. */
@Composable
fun MeasurementsScreen(nav: NavController, onBack: (() -> Unit)?) {
    val latest by remember { Savia.db.measurements().observeLatestPerType() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val weights by remember { Savia.db.measurements().observeByType(MeasureType.WEIGHT.key) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val profile by remember { Savia.db.profile().observe() }.collectAsStateWithLifecycle(initialValue = null)
    val height = profile?.heightCm
    val summary = remember(weights, height, profile?.goalWeightKg) { MeasureStats.weightSummary(weights, height, profile?.goalWeightKg) }
    val latestByType = remember(latest) { latest.associateBy { it.type } }

    SaviaScaffold("Mediciones", onBack) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WeightCard(
                summary = summary,
                onOpen = { nav.navigate(Routes.measure(MeasureType.WEIGHT.key)) },
                onAdd = { nav.navigate(Routes.measure(MeasureType.WEIGHT.key, add = true)) },
            )
            SectionLabel("Otras mediciones")
            SectionCard(contentPadding = 6.dp) {
                val others = MeasureType.entries.filter { it != MeasureType.WEIGHT }
                others.forEachIndexed { i, type ->
                    MeasureRow(
                        type = type,
                        latest = latestByType[type.key],
                        heightCm = height,
                        onOpen = { nav.navigate(Routes.measure(type.key)) },
                        onAdd = { nav.navigate(Routes.measure(type.key, add = true)) },
                    )
                    if (i < others.lastIndex) {
                        HorizontalDivider(Modifier.padding(start = 62.dp, end = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
            InfoNote("Las clasificaciones son orientativas y no sustituyen la valoración de un profesional de la salud.")
        }
    }
}

@Composable
private fun WeightCard(summary: WeightSummary?, onOpen: () -> Unit, onAdd: () -> Unit) {
    val colors = SaviaTheme.colors
    SectionCard(
        title = "Peso",
        onClick = onOpen,
        action = { Icon(Icons.Filled.ChevronRight, contentDescription = "Ver detalle", tint = MaterialTheme.colorScheme.onSurfaceVariant) },
    ) {
        if (summary == null) {
            Text(
                "Aún no registras tu peso. Anótalo con regularidad para ver tu tendencia, tu IMC y cuánto te falta para tu meta.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Registrar peso")
            }
            return@SectionCard
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(Fmt.num(summary.latest, 1), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    Text("kg", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                }
                Text(
                    Fmt.relativeDay(summary.latestTimestamp.millisToEpochDay()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (summary.spark.size >= 2) {
                Sparkline(summary.spark, Modifier.width(120.dp).height(48.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("7 días", summary.change7?.let { Fmt.signed(it, 1) + " kg" } ?: "—", Modifier.weight(1f))
            StatTile("30 días", summary.change30?.let { Fmt.signed(it, 1) + " kg" } ?: "—", Modifier.weight(1f))
            val bmi = summary.bmi
            if (bmi != null) {
                StatTile("IMC", Fmt.num(bmi, 1), Modifier.weight(1f), valueColor = summary.bmiAssessment?.level?.color() ?: MaterialTheme.colorScheme.onSurface)
            } else {
                StatTile("IMC", "—", Modifier.weight(1f))
            }
        }
        val assessment = summary.bmiAssessment
        if (assessment != null) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(assessment.label, assessment.level.color())
                Spacer(Modifier.width(8.dp))
                Text("según tu IMC (orientativo)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                "Agrega tu estatura en el detalle de peso para calcular tu IMC.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val goal = summary.goal
        val toGoal = summary.toGoal
        if (goal != null && toGoal != null) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Meta: ${Fmt.num(goal, 1)} kg", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(
                    if (summary.goalReached) "¡Meta alcanzada!" else "Faltan ${Fmt.num(abs(toGoal), 1)} kg",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (summary.goalReached) colors.good else MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { if (summary.goalReached) 1f else summary.goalProgress ?: 0f },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = if (summary.goalReached) colors.good else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                strokeCap = StrokeCap.Round,
                drawStopIndicator = {},
            )
        }
        Spacer(Modifier.height(14.dp))
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Registrar peso")
        }
    }
}

@Composable
private fun MeasureRow(type: MeasureType, latest: Measurement?, heightCm: Double?, onOpen: () -> Unit, onAdd: () -> Unit) {
    val tint = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpen).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(type.icon(), contentDescription = null, tint = tint)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(type.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (latest == null) {
                Text("Sin registros", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(type.valueWithUnit(latest), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        Fmt.daysFromNow(latest.timestamp.millisToEpochDay()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val a = MeasureStats.assess(type, latest, heightCm)
                if (a != null) {
                    Spacer(Modifier.height(4.dp))
                    Pill(a.label, a.level.color())
                }
            }
        }
        FilledTonalIconButton(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = "Agregar ${type.label.lowercase()}")
        }
    }
}
