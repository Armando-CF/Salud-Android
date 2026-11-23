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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.savia.cycle.CycleAnalysis
import app.savia.cycle.CycleRecord
import app.savia.ui.components.BarChart
import app.savia.ui.components.BarEntry
import app.savia.ui.components.ChartPoint
import app.savia.ui.components.ChartSeries
import app.savia.ui.components.EmptyState
import app.savia.ui.components.HBarList
import app.savia.ui.components.InfoNote
import app.savia.ui.components.LineChart
import app.savia.ui.components.Pill
import app.savia.ui.components.SaviaScaffold
import app.savia.ui.components.SectionCard
import app.savia.ui.components.StatTile
import app.savia.ui.nav.Routes
import app.savia.ui.nav.back
import app.savia.ui.theme.SaviaTheme
import app.savia.util.Fmt

@Composable
fun CycleStatsScreen(nav: NavController) {
    if (app.savia.Savia.isPet) {
        app.savia.ui.wellbeing.PeopleOnlyScreen("Estadísticas del ciclo") { nav.back() }
        return
    }
    val state = rememberCycleState()
    SaviaScaffold(title = "Estadísticas del ciclo", onBack = { nav.back() }) { pad ->
        if (!state.loaded) return@SaviaScaffold
        val a = state.analysis
        if (!a.hasData) {
            Column(Modifier.padding(pad)) {
                EmptyState(
                    Icons.Filled.BarChart,
                    "Aún no hay ciclos registrados",
                    "Cuando registres tus periodos aquí verás promedios, regularidad, historial y los síntomas que más se repiten.",
                    actionLabel = "Registrar hoy",
                    onAction = { nav.navigate(Routes.cycleDay(state.today)) },
                )
            }
            return@SaviaScaffold
        }
        val symptomStats = remember(state.days, a) { CycleLogic.symptomStats(state.days, a) }
        val moodStats = remember(state.days, a) { CycleLogic.moodStats(state.days, a) }
        val temps = remember(state.days) {
            state.days.filter { it.basalTemp != null }.map { ChartPoint(it.epochDay.toDouble(), it.basalTemp!!) }
        }
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SummaryCard(a)
            LengthsCard(a)
            HistoryCard(a)
            if (symptomStats.isNotEmpty()) FrequencyCard("Síntomas más frecuentes", symptomStats, SaviaTheme.colors.period)
            if (moodStats.isNotEmpty()) FrequencyCard("Ánimo más frecuente", moodStats, MaterialTheme.colorScheme.primary)
            if (temps.size >= 2) {
                SectionCard(title = "Temperatura basal") {
                    LineChart(
                        listOf(ChartSeries("Temperatura", temps.takeLast(90), SaviaTheme.colors.ovulation)),
                        yFormatter = { Fmt.num(it, 2) },
                        unit = "°C",
                    )
                    Text(
                        "Tras la ovulación la temperatura basal suele subir entre 0.2 y 0.5 °C y mantenerse así hasta el siguiente periodo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            InfoNote(
                "Conviene consultar a una persona profesional de la salud si, de forma sostenida, tus ciclos duran menos de 21 " +
                    "o más de 35 días, el sangrado dura más de 7 días o es muy abundante, pasan más de 90 días sin periodo " +
                    "(sin embarazo), hay sangrado entre periodos o el dolor te impide hacer tu vida normal. " +
                    "Esta información es orientativa y no sustituye una valoración médica.",
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SummaryCard(a: CycleAnalysis) {
    val validCount = a.cycles.count { it.valid }
    val recent = minOf(validCount, 6)
    SectionCard(title = "Resumen") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    "Ciclo promedio", a.avgCycle?.let { Fmt.num(it, 1) + " días" } ?: "—", Modifier.weight(1f),
                    sub = if (a.avgCycle != null) "últimos ${if (recent == 1) "1 ciclo" else "$recent ciclos"}" else "Faltan ciclos completos",
                )
                StatTile(
                    "Periodo promedio", a.avgPeriod?.let { Fmt.num(it, 1) + " días" } ?: "—", Modifier.weight(1f),
                    sub = if (a.avgPeriod != null) "de sangrado" else "Aún en curso",
                    valueColor = SaviaTheme.colors.period,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Ciclo más corto", a.shortestCycle?.let { CycleLogic.dias(it) } ?: "—", Modifier.weight(1f))
                StatTile("Ciclo más largo", a.longestCycle?.let { CycleLogic.dias(it) } ?: "—", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val variation = if (a.shortestCycle != null && a.longestCycle != null && recent >= 2) a.longestCycle - a.shortestCycle else null
                StatTile(
                    "Variación", variation?.let { CycleLogic.dias(it) } ?: "—", Modifier.weight(1f),
                    sub = a.cycleStdDev?.let { "± ${Fmt.num(it, 1)} días" } ?: "entre ciclos",
                )
                StatTile(
                    "Regularidad", CycleLogic.regularityText(a), Modifier.weight(1f),
                    sub = when (a.regular) {
                        true -> "Varía 7 días o menos"
                        false -> "Varía más de 7 días"
                        null -> "Se necesitan 3 ciclos"
                    },
                    valueColor = when (a.regular) {
                        true -> SaviaTheme.colors.good
                        false -> SaviaTheme.colors.warn
                        null -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            Text(
                "Periodos registrados: ${a.periods.size} · Ciclos completos: ${a.cycles.count { it.length != null }}" +
                    (a.periods.firstOrNull()?.let { " · Desde ${Fmt.date(it.start)}" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LengthsCard(a: CycleAnalysis) {
    val bars = a.cycles.filter { it.valid }.takeLast(12)
    SectionCard(title = "Duración de tus ciclos") {
        if (bars.isEmpty()) {
            Text(
                "Cuando registres el inicio de dos periodos seguidos aparecerá aquí la duración de cada ciclo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            BarChart(
                bars.map { BarEntry(Fmt.dateShort(it.start), it.length!!.toDouble()) },
                color = SaviaTheme.colors.period,
                valueFormatter = { Fmt.num(it, 0) },
                reference = a.avgCycle,
            )
            Text(
                "Días por ciclo, según la fecha en que empezó. La línea punteada es tu promedio reciente" +
                    (a.avgCycle?.let { " (${Fmt.num(it, 1)} días)." } ?: "."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HistoryCard(a: CycleAnalysis) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    val all = remember(a) { a.cycles.reversed() }
    val shown = if (showAll) all else all.take(8)
    SectionCard(title = "Historial de ciclos") {
        shown.forEachIndexed { i, c ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            HistoryRow(c)
        }
        if (all.size > 8) {
            TextButton(onClick = { showAll = !showAll }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showAll) "Ver menos" else "Ver los ${all.size} ciclos")
            }
        }
    }
}

@Composable
private fun HistoryRow(c: CycleRecord) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(Fmt.date(c.start), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                "Periodo de ${CycleLogic.dias(c.periodLength)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            c.length == null -> Pill("En curso", MaterialTheme.colorScheme.primary)
            c.valid -> Text(CycleLogic.dias(c.length), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            else -> Column(horizontalAlignment = Alignment.End) {
                Text(CycleLogic.dias(c.length), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Pill("Atípico · no cuenta", SaviaTheme.colors.warn)
            }
        }
    }
}

@Composable
private fun FrequencyCard(title: String, stats: List<PhaseStat>, color: androidx.compose.ui.graphics.Color) {
    val top = stats.take(8)
    SectionCard(title = title) {
        HBarList(top.map { it.label to it.count }, color = color)
        val hints = top.take(4).mapNotNull { s -> s.topPhase?.let { "${s.label}: sobre todo en ${CycleLogic.phaseShort(it)}" } }
        if (hints.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("¿Cuándo aparecen?", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            for (h in hints) {
                Text(h, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
