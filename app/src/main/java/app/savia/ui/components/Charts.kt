package app.savia.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.savia.util.Fmt
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Punto de una gráfica. Para ejes de fecha, x = epochDay (puede llevar fracción de día). */
data class ChartPoint(val x: Double, val y: Double)

data class ChartSeries(
    val name: String,
    val points: List<ChartPoint>,
    val color: Color,
    val dots: Boolean = true,
    val dashed: Boolean = false,
    val strokeWidth: Float = 2.5f,
    /** Rellenar suavemente bajo la línea. */
    val fill: Boolean = false,
    /** Si es false no participa en la selección al tocar (p. ej. líneas de tendencia). */
    val selectable: Boolean = true,
)

/** Banda horizontal (rango de referencia). min/max null = abierto hacia ese lado. */
data class ChartBand(val min: Double?, val max: Double?, val color: Color)

/** Línea horizontal (meta). */
data class ChartHLine(val y: Double, val color: Color, val label: String? = null)

/** Rangos de tiempo para filtrar gráficas. */
enum class ChartRange(val label: String, val days: Int?) {
    M1("1 mes", 30), M3("3 meses", 90), M6("6 meses", 180), Y1("1 año", 365), ALL("Todo", null);

    /** epochDay mínimo incluido, o null para todo. */
    fun fromDay(today: Long): Long? = days?.let { today - it }
}

@Composable
fun ChartRangeSelector(selected: ChartRange, onSelect: (ChartRange) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (r in ChartRange.entries) {
            FilterChip(selected = r == selected, onClick = { onSelect(r) }, label = { Text(r.label, maxLines = 1, softWrap = false) })
        }
    }
}

private fun niceStep(range: Double, targetTicks: Int): Double {
    if (range <= 0) return 1.0
    val raw = range / targetTicks
    val mag = 10.0.pow(floor(log10(raw)))
    val norm = raw / mag
    val nice = when {
        norm <= 1.0 -> 1.0
        norm <= 2.0 -> 2.0
        norm <= 2.5 -> 2.5
        norm <= 5.0 -> 5.0
        else -> 10.0
    }
    return nice * mag
}

/**
 * Gráfica de líneas con eje X continuo (por defecto fechas en epochDay).
 * Tocar o arrastrar muestra el valor más cercano.
 *
 * @param xFormatter etiqueta de un valor X (por defecto fecha corta).
 * @param yFormatter etiqueta de un valor Y.
 * @param yMin / yMax fuerzan límites del eje Y (si son null se calculan).
 */
@Composable
fun LineChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier.fillMaxWidth().height(220.dp),
    bands: List<ChartBand> = emptyList(),
    hLines: List<ChartHLine> = emptyList(),
    xFormatter: (Double) -> String = { Fmt.dateShort(floor(it).toLong()) },
    yFormatter: (Double) -> String = { Fmt.num(it, 1) },
    yMin: Double? = null,
    yMax: Double? = null,
    unit: String = "",
) {
    val all = series.flatMap { it.points }
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val tooltipBg = MaterialTheme.colorScheme.inverseSurface
    val tooltipFg = MaterialTheme.colorScheme.inverseOnSurface
    val surface = MaterialTheme.colorScheme.surfaceContainerLowest
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    var touchX by remember { mutableStateOf<Float?>(null) }

    if (all.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("Aún no hay datos para graficar", style = MaterialTheme.typography.bodyMedium, color = labelColor)
        }
        return
    }

    var loY = all.minOf { it.y }
    var hiY = all.maxOf { it.y }
    for (h in hLines) { loY = minOf(loY, h.y); hiY = maxOf(hiY, h.y) }
    if (hiY - loY < 1e-9) { loY -= 1.0; hiY += 1.0 }
    val pad = (hiY - loY) * 0.12
    val step = niceStep((hiY - loY) + 2 * pad, 4)
    val axisLo = yMin ?: (floor((loY - pad) / step) * step)
    val axisHi = yMax ?: (ceil((hiY + pad) / step) * step)
    var loX = all.minOf { it.x }
    var hiX = all.maxOf { it.x }
    if (hiX - loX < 1e-9) { loX -= 1.0; hiX += 1.0 }

    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTapGestures { o -> touchX = if (touchX != null && abs((touchX ?: 0f) - o.x) < 24f) null else o.x }
            }
            .pointerInput(Unit) {
                detectDragGestures(onDragStart = { touchX = it.x }) { change, _ -> touchX = change.position.x }
            },
    ) {
        val leftPad = 38.dp.toPx()
        val rightPad = 10.dp.toPx()
        val topPad = 10.dp.toPx()
        val bottomPad = 24.dp.toPx()
        val w = size.width - leftPad - rightPad
        val h = size.height - topPad - bottomPad
        if (w <= 0 || h <= 0) return@Canvas
        fun px(x: Double) = leftPad + ((x - loX) / (hiX - loX)).toFloat() * w
        fun py(y: Double) = topPad + (1f - ((y - axisLo) / (axisHi - axisLo)).toFloat()) * h

        // Bandas de referencia
        for (b in bands) {
            val top = py((b.max ?: axisHi).coerceIn(axisLo, axisHi))
            val bottom = py((b.min ?: axisLo).coerceIn(axisLo, axisHi))
            if (bottom > top) drawRect(b.color, Offset(leftPad, top), Size(w, bottom - top))
        }

        // Rejilla y etiquetas Y
        var t = ceil(axisLo / step) * step
        var guard = 0
        while (t <= axisHi + 1e-9 && guard++ < 12) {
            val y = py(t)
            drawLine(axisColor, Offset(leftPad, y), Offset(leftPad + w, y), strokeWidth = 1f)
            val layout = measurer.measure(yFormatter(t), labelStyle)
            drawText(layout, topLeft = Offset(leftPad - layout.size.width - 6f, y - layout.size.height / 2f))
            t += step
        }

        // Etiquetas X (3 a 4 repartidas)
        val xTicks = if (w > 260.dp.toPx()) 4 else 3
        for (i in 0 until xTicks) {
            val xv = loX + (hiX - loX) * i / (xTicks - 1)
            val layout = measurer.measure(xFormatter(xv), labelStyle)
            val x = (px(xv) - layout.size.width / 2f).coerceIn(leftPad - 6.dp.toPx(), size.width - layout.size.width)
            drawText(layout, topLeft = Offset(x, topPad + h + 7.dp.toPx()))
        }

        // Líneas horizontales (meta)
        for (hl in hLines) {
            val y = py(hl.y)
            drawLine(hl.color, Offset(leftPad, y), Offset(leftPad + w, y), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            if (hl.label != null) {
                val layout = measurer.measure(hl.label, labelStyle.copy(color = hl.color, fontWeight = FontWeight.SemiBold))
                drawText(layout, topLeft = Offset(leftPad + w - layout.size.width - 4f, y - layout.size.height - 2f))
            }
        }

        // Series
        for (s in series) {
            val pts = s.points.sortedBy { it.x }
            if (pts.isEmpty()) continue
            val path = Path()
            pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(px(p.x), py(p.y)) else path.lineTo(px(p.x), py(p.y)) }
            if (s.fill && pts.size > 1) {
                val area = Path().apply {
                    addPath(path)
                    lineTo(px(pts.last().x), topPad + h)
                    lineTo(px(pts.first().x), topPad + h)
                    close()
                }
                drawPath(area, s.color.copy(alpha = 0.12f))
            }
            drawPath(
                path, s.color,
                style = Stroke(
                    width = s.strokeWidth.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round,
                    pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(12f, 10f)) else null,
                ),
            )
            if (s.dots && pts.size <= 60) {
                for (p in pts) {
                    drawCircle(surface, radius = 4.dp.toPx(), center = Offset(px(p.x), py(p.y)))
                    drawCircle(s.color, radius = 2.8.dp.toPx(), center = Offset(px(p.x), py(p.y)))
                }
            } else if (pts.size == 1) {
                drawCircle(s.color, radius = 3.5.dp.toPx(), center = Offset(px(pts[0].x), py(pts[0].y)))
            }
        }

        // Selección
        val tx = touchX
        if (tx != null) {
            val selectable = series.filter { it.selectable && it.points.isNotEmpty() }
            val nearest = selectable.flatMap { it.points }.minByOrNull { abs(px(it.x) - tx) }
            if (nearest != null) {
                val x = px(nearest.x)
                drawLine(labelColor, Offset(x, topPad), Offset(x, topPad + h), strokeWidth = 1.5f)
                val lines = ArrayList<Pair<String, Color?>>()
                lines += xFormatter(nearest.x) to null
                for (s in selectable) {
                    val p = s.points.minByOrNull { abs(it.x - nearest.x) } ?: continue
                    if (abs(px(p.x) - x) > 1.5f) continue
                    drawCircle(s.color, radius = 5.dp.toPx(), center = Offset(x, py(p.y)))
                    drawCircle(surface, radius = 2.dp.toPx(), center = Offset(x, py(p.y)))
                    val v = yFormatter(p.y) + (if (unit.isNotEmpty()) " $unit" else "")
                    lines += (if (selectable.size > 1) "${s.name}: $v" else v) to s.color
                }
                val layouts = lines.map { (text, _) -> measurer.measure(text, TextStyle(fontSize = 11.sp, color = tooltipFg, fontWeight = FontWeight.Medium)) }
                val bw = layouts.maxOf { it.size.width } + 16.dp.toPx()
                val bh = layouts.sumOf { it.size.height } + 10.dp.toPx()
                val bx = (x - bw / 2f).coerceIn(0f, size.width - bw)
                drawRoundRect(tooltipBg, Offset(bx, 0f), Size(bw, bh), CornerRadius(8.dp.toPx()))
                var ty = 5.dp.toPx()
                for (l in layouts) {
                    drawText(l, topLeft = Offset(bx + 8.dp.toPx(), ty))
                    ty += l.size.height
                }
            }
        }
    }
}

/** Leyenda de colores para una gráfica. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChartLegend(items: List<Pair<String, Color>>, modifier: Modifier = Modifier) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((label, color) in items) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

data class BarEntry(val label: String, val value: Double, val color: Color? = null)

/** Barras verticales simples con etiqueta abajo y valor arriba. */
@Composable
fun BarChart(
    bars: List<BarEntry>,
    modifier: Modifier = Modifier.fillMaxWidth().height(180.dp),
    color: Color = MaterialTheme.colorScheme.primary,
    valueFormatter: (Double) -> String = { Fmt.num(it, 1) },
    /** Línea horizontal de referencia (p. ej. promedio). */
    reference: Double? = null,
) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val refColor = MaterialTheme.colorScheme.outline
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontSize = 10.sp, color = labelColor)
    if (bars.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("Aún no hay datos para graficar", style = MaterialTheme.typography.bodyMedium, color = labelColor)
        }
        return
    }
    val maxV = maxOf(bars.maxOf { it.value }, reference ?: 0.0).takeIf { it > 0 } ?: 1.0
    Canvas(modifier) {
        val bottomPad = 18.dp.toPx()
        val topPad = 16.dp.toPx()
        val h = size.height - bottomPad - topPad
        val slot = size.width / bars.size
        val bw = (slot * 0.62f).coerceAtMost(36.dp.toPx())
        val showEvery = ceil(bars.size / 8.0).toInt().coerceAtLeast(1)
        bars.forEachIndexed { i, b ->
            val bh = (b.value / maxV).toFloat() * h
            val x = slot * i + (slot - bw) / 2f
            val y = topPad + h - bh
            drawRoundRect(b.color ?: color, Offset(x, y), Size(bw, bh.coerceAtLeast(2f)), CornerRadius(6.dp.toPx()))
            if (bars.size <= 14 || i % showEvery == 0) {
                val v = measurer.measure(valueFormatter(b.value), style)
                drawText(v, topLeft = Offset((x + bw / 2f - v.size.width / 2f).coerceIn(0f, size.width - v.size.width), y - v.size.height - 1f))
            }
            if (i % showEvery == 0) {
                val l = measurer.measure(b.label, style)
                drawText(l, topLeft = Offset((x + bw / 2f - l.size.width / 2f).coerceIn(0f, size.width - l.size.width), topPad + h + 3f))
            }
        }
        if (reference != null) {
            val y = topPad + h - (reference / maxV).toFloat() * h
            drawLine(refColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        }
    }
}

/** Lista de barras horizontales (p. ej. "síntomas más frecuentes"). */
@Composable
fun HBarList(
    items: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val max = (items.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((label, count) in items) {
            Column {
                Row {
                    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("$count", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(3.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(count / max.toFloat()).clip(RoundedCornerShape(50)).background(color))
                }
            }
        }
    }
}

/** Mini gráfica sin ejes para tarjetas de resumen. */
@Composable
fun Sparkline(values: List<Double>, modifier: Modifier = Modifier.width(96.dp).height(36.dp), color: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val lo = values.min()
        val hi = values.max()
        val span = (hi - lo).takeIf { it > 1e-9 } ?: 1.0
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.size - 1)
            val y = size.height * (1f - ((v - lo) / span).toFloat()) * 0.8f + size.height * 0.1f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val lastY = size.height * (1f - ((values.last() - lo) / span).toFloat()) * 0.8f + size.height * 0.1f
        drawCircle(color, radius = 3.dp.toPx(), center = Offset(size.width, lastY))
    }
}
