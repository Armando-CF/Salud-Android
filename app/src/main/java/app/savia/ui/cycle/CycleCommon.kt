package app.savia.ui.cycle

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.savia.Savia
import app.savia.cycle.CycleAnalysis
import app.savia.cycle.CycleEngine
import app.savia.data.CycleDay
import app.savia.reminders.ReminderScheduler
import app.savia.ui.components.SectionCard
import app.savia.ui.theme.SaviaTheme
import app.savia.util.today
import kotlinx.coroutines.flow.map
import kotlin.math.cos
import kotlin.math.sin

/** Todo lo que las pantallas del ciclo necesitan saber, ya calculado. */
class CycleUiState(
    /** false mientras llega la primera lectura de la base (evita parpadear el estado vacío). */
    val loaded: Boolean,
    val days: List<CycleDay>,
    val byDay: Map<Long, CycleDay>,
    val analysis: CycleAnalysis,
    val today: Long,
) {
    val status: TodayStatus get() = CycleLogic.todayStatus(analysis)
}

/** Observa los registros y ajustes del ciclo y devuelve el análisis vigente. */
@Composable
fun rememberCycleState(): CycleUiState {
    val flow = remember { Savia.db.cycle().observeAll().map<List<CycleDay>, List<CycleDay>?> { it } }
    val days by flow.collectAsStateWithLifecycle(initialValue = null)
    val cycleLen by Savia.prefs.cycleLength.flow.collectAsStateWithLifecycle()
    val periodLen by Savia.prefs.periodLength.flow.collectAsStateWithLifecycle()
    val luteal by Savia.prefs.lutealLength.flow.collectAsStateWithLifecycle()
    val t = today()
    return remember(days, cycleLen, periodLen, luteal, t) {
        val list = days ?: emptyList()
        CycleUiState(
            loaded = days != null,
            days = list,
            byDay = list.associateBy { it.epochDay },
            analysis = CycleEngine.analyze(list, cycleLen, periodLen, luteal, t),
            today = t,
        )
    }
}

/** Cambia el flujo de un día conservando lo demás; borra la fila si queda vacía. */
internal fun setFlow(context: Context, day: Long, flow: Int) {
    val app = context.applicationContext
    Savia.io {
        val dao = Savia.db.cycle()
        val n = CycleLogic.withFlow(dao.get(day), day, flow)
        if (n == null) dao.deleteDay(day) else dao.save(n)
        ReminderScheduler.rescheduleAll(app)
    }
}

/** Marca varios días seguidos con flujo moderado (sin pisar un flujo ya registrado). */
internal fun markPeriod(context: Context, start: Long, length: Int, lastAllowed: Long) {
    val app = context.applicationContext
    Savia.io {
        val dao = Savia.db.cycle()
        for (d in start until start + length) {
            if (d > lastAllowed) break
            val existing = dao.get(d)
            if ((existing?.flow ?: 0) >= 2) continue
            dao.save((existing ?: CycleDay(epochDay = d)).copy(flow = 3))
        }
        ReminderScheduler.rescheduleAll(app)
    }
}

/** Color del texto sobre un relleno de color "periodo". */
@Composable
internal fun onPeriodColor(): Color = if (SaviaTheme.colors.isDark) Color(0xFF3A0A18) else Color.White

/**
 * Anillo del ciclo: una vuelta = un ciclo. Marca los días de periodo, la ventana fértil,
 * la ovulación estimada y la posición de hoy. El contenido central lo pone quien llama.
 */
@Composable
fun CycleRing(
    analysis: CycleAnalysis,
    cycleDay: Int?,
    showFertility: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 196.dp,
    stroke: Dp = 16.dp,
    center: @Composable () -> Unit = {},
) {
    val colors = SaviaTheme.colors
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val surface = MaterialTheme.colorScheme.surfaceContainerLowest
    val marker = MaterialTheme.colorScheme.onSurface
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val inset = sw / 2f + 6.dp.toPx()
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            val radius = arcSize.width / 2f
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val total = analysis.cycleLength.coerceAtLeast(1)
            val per = 360f / total
            fun pointAt(dayPos: Float): Offset {
                val ang = Math.toRadians((-90f + dayPos * per).toDouble())
                return Offset(c.x + radius * cos(ang).toFloat(), c.y + radius * sin(ang).toFloat())
            }
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(sw))
            // Ventana fértil y ovulación (día de ovulación = duración − fase lútea + 1)
            val ovDay = total - analysis.lutealLength + 1
            if (showFertility && analysis.hasData && ovDay - 5 > 1) {
                val startDay = (ovDay - 5).coerceAtLeast(1)
                val endDay = (ovDay + 1).coerceAtMost(total)
                drawArc(
                    colors.fertile, -90f + (startDay - 1) * per + 1f, (endDay - startDay + 1) * per - 2f, false,
                    topLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Butt),
                )
            }
            // Periodo
            if (analysis.hasData) {
                val p = analysis.periodLength.coerceIn(1, total)
                drawArc(colors.period, -90f, p * per - 1f, false, topLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Butt))
            }
            if (showFertility && analysis.hasData && ovDay - 5 > 1) {
                val o = pointAt(ovDay - 0.5f)
                drawCircle(surface, sw * 0.36f, o)
                drawCircle(colors.ovulation, sw * 0.26f, o)
            }
            // Hoy
            if (cycleDay != null) {
                val pos = pointAt((cycleDay.coerceIn(1, total)) - 0.5f)
                drawCircle(surface, sw * 0.82f, pos)
                drawCircle(marker, sw * 0.82f, pos, style = Stroke(3.dp.toPx()))
            }
        }
        center()
    }
}

/**
 * Tarjeta-resumen del ciclo para la pantalla de inicio: anillo pequeño con el día del ciclo,
 * mensaje principal ("Periodo en 5 días") y detalle.
 */
@Composable
fun CycleSummaryCard(analysis: CycleAnalysis, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val status = remember(analysis) { CycleLogic.todayStatus(analysis) }
    val showFert by Savia.prefs.showFertility.flow.collectAsStateWithLifecycle()
    SectionCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CycleRing(analysis, status.cycleDay, showFert, size = 76.dp, stroke = 8.dp) {
                Text(
                    status.cycleDay?.toString() ?: "–",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (status.cycleDay != null) "Ciclo · ${status.phaseLabel}" else "Ciclo",
                    style = MaterialTheme.typography.labelLarge,
                    color = SaviaTheme.colors.period,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(status.headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                status.detail?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
