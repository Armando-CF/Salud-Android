package app.savia.ui.studies

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.savia.Savia
import app.savia.ui.components.ChartBand
import app.savia.ui.components.ChartHLine
import app.savia.ui.components.ChartLegend
import app.savia.ui.components.ChartPoint
import app.savia.ui.components.ChartSeries
import app.savia.ui.components.EmptyState
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
import kotlin.math.abs

/** Evolución de un parámetro de laboratorio (por nombre) a través de todos los estudios. */
@Composable
fun ParamTrendScreen(nav: NavController, name: String) {
    val studies by remember { Savia.db.studies().observeAll() }.collectAsStateWithLifecycle(initialValue = null)
    val results by remember { Savia.db.studies().observeAllResults() }.collectAsStateWithLifecycle(initialValue = null)
    val st = studies
    val rs = results
    val points = remember(st, rs, name) { if (st == null || rs == null) null else paramSeries(name, st, rs) }
    val displayName = points?.lastOrNull()?.result?.name?.trim()?.takeIf { it.isNotEmpty() } ?: name

    SaviaScaffold(title = displayName, onBack = { nav.back() }, subtitle = "Evolución del parámetro") { pad ->
        when {
            points == null -> Unit
            points.isEmpty() -> EmptyState(
                icon = Icons.Filled.QueryStats,
                title = "Sin valores",
                message = "Todavía no hay resultados numéricos de \"$name\" en tus estudios.",
                modifier = Modifier.padding(pad),
            )
            else -> TrendContent(nav, points, Modifier.padding(pad))
        }
    }
}

@Composable
private fun TrendContent(nav: NavController, points: List<ParamPoint>, modifier: Modifier) {
    val latest = points.last()
    val previous = points.getOrNull(points.size - 2)
    val unit = latest.result.unit
    val refMin = latest.result.refMin
    val refMax = latest.result.refMax
    val f = latest.result.flag()
    val values = points.map { it.value }
    val lo = values.min()
    val hi = values.max()
    val decimals = if (maxOf(abs(lo), abs(hi)) < 10) 2 else 1
    fun withUnit(v: Double) = labNum(v) + (if (unit.isNotBlank()) " $unit" else "")
    val good = SaviaTheme.colors.good
    val primary = MaterialTheme.colorScheme.primary

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    label = "Último",
                    value = withUnit(latest.value),
                    modifier = Modifier.weight(1f),
                    sub = Fmt.date(latest.epochDay),
                    valueColor = if (f == RangeFlag.ALTO || f == RangeFlag.BAJO) flagColor(f) else MaterialTheme.colorScheme.onSurface,
                )
                StatTile(
                    label = "Cambio",
                    value = previous?.let { Fmt.signed(latest.value - it.value, 3) } ?: "—",
                    modifier = Modifier.weight(1f),
                    sub = previous?.let { "vs. ${Fmt.date(it.epochDay)}" } ?: "Sin valor anterior",
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Mínimo", withUnit(lo), Modifier.weight(1f), sub = Fmt.date(points.minBy { it.value }.epochDay))
                StatTile("Máximo", withUnit(hi), Modifier.weight(1f), sub = Fmt.date(points.maxBy { it.value }.epochDay))
            }
            val range = rangeText(refMin, refMax)
            if (range.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Referencia: $range" + (if (unit.isNotBlank()) " $unit" else ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Pill(flagLabel(f), flagColor(f))
                }
                Spacer(Modifier.height(6.dp))
                RangeBar(latest.value, refMin, refMax)
            }
        }

        SectionCard(title = "Evolución") {
            // Los límites de referencia se dibujan como líneas solo si quedan cerca de los datos,
            // para no aplastar la gráfica cuando el rango es muy amplio.
            val span = maxOf(hi - lo, abs(hi) * 0.1, 1e-6)
            val near = { v: Double -> v >= lo - 1.5 * span && v <= hi + 1.5 * span }
            val hLines = buildList {
                if (refMax != null && near(refMax)) add(ChartHLine(refMax, good, "Máx. ${labNum(refMax)}"))
                if (refMin != null && near(refMin)) add(ChartHLine(refMin, good, "Mín. ${labNum(refMin)}"))
            }
            LineChart(
                series = listOf(ChartSeries(latest.result.name, points.map { ChartPoint(it.epochDay.toDouble(), it.value) }, primary)),
                bands = if (refMin != null || refMax != null) listOf(ChartBand(refMin, refMax, good.copy(alpha = 0.12f))) else emptyList(),
                hLines = hLines,
                xFormatter = { Fmt.date(Math.floor(it).toLong()).substringAfter(' ') },
                yFormatter = { Fmt.num(it, decimals) },
                unit = unit,
            )
            Spacer(Modifier.height(8.dp))
            ChartLegend(
                buildList {
                    add(latest.result.name to primary)
                    if (refMin != null || refMax != null) add("Rango de referencia" to good)
                },
            )
            if (points.size == 1) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Con un solo valor aún no hay tendencia. Cuando registres otro estudio con este parámetro verás la línea.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard(title = "Valores") {
            points.reversed().forEachIndexed { i, p ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                val pf = p.result.flag()
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .clickable { nav.navigate(Routes.study(p.study.id)) }
                        .padding(vertical = 6.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(Fmt.date(p.epochDay), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            p.study.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            p.result.valueText(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (pf == RangeFlag.ALTO || pf == RangeFlag.BAJO) flagColor(pf) else MaterialTheme.colorScheme.onSurface,
                        )
                        if (pf != RangeFlag.SIN_RANGO) Pill(flagLabel(pf), flagColor(pf))
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        InfoNote("Los rangos de referencia son orientativos y pueden variar entre laboratorios; usa los de tu reporte. Esta gráfica no sustituye la valoración de ${rememberStudyProfile().whoToAsk}.")
    }
}
