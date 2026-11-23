package app.savia.ui.measure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.savia.Savia
import app.savia.data.MeasureType
import app.savia.data.Measurement
import app.savia.ui.components.ChartBand
import app.savia.ui.components.ChartHLine
import app.savia.ui.components.ChartLegend
import app.savia.ui.components.ChartPoint
import app.savia.ui.components.ChartRange
import app.savia.ui.components.ChartRangeSelector
import app.savia.ui.components.ChartSeries
import app.savia.ui.components.ConfirmDialog
import app.savia.ui.components.EmptyState
import app.savia.ui.components.InfoNote
import app.savia.ui.components.LineChart
import app.savia.ui.components.Pill
import app.savia.ui.components.SaviaScaffold
import app.savia.ui.components.SectionCard
import app.savia.ui.components.SectionLabel
import app.savia.ui.components.StatTile
import app.savia.ui.nav.back
import app.savia.ui.theme.SaviaTheme
import app.savia.util.Fmt
import app.savia.util.millisToEpochDay
import app.savia.util.today
import kotlinx.coroutines.flow.Flow
import kotlin.math.abs
import kotlin.math.sign

/** Detalle de un tipo de medición: gráfica, estadísticas, interpretación orientativa e historial. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementDetailScreen(nav: NavController, typeKey: String, openAdd: Boolean) {
    val type = MeasureType.of(typeKey) ?: MeasureType.WEIGHT
    val flow: Flow<List<Measurement>?> = remember(type) { Savia.db.measurements().observeByType(type.key) }
    val loaded by flow.collectAsStateWithLifecycle(initialValue = null)
    val profile by remember { Savia.db.profile().observe() }.collectAsStateWithLifecycle(initialValue = null)
    val heightCm = profile?.heightCm
    val goal = profile?.goalWeightKg

    var range by rememberSaveable { mutableStateOf(ChartRange.M3) }
    var formOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf(0L) }
    var addConsumed by rememberSaveable { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Measurement?>(null) }
    var goalDialog by rememberSaveable { mutableStateOf(false) }
    var heightDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (openAdd && !addConsumed) {
            addConsumed = true
            editingId = 0L
            formOpen = true
        }
    }
    val openNew = { editingId = 0L; formOpen = true }

    SaviaScaffold(
        title = type.label,
        onBack = { nav.back() },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = openNew,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Agregar") },
            )
        },
    ) { pad ->
        val items = loaded
        when {
            items == null -> Box(Modifier.fillMaxSize().padding(pad))
            items.isEmpty() -> Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
                EmptyState(
                    icon = type.icon(),
                    title = "Sin registros de ${type.label.lowercase()}",
                    message = "Agrega tu primera medición para ver aquí tu gráfica, tus estadísticas y tu historial.",
                    actionLabel = "Registrar ${type.label.lowercase()}",
                    onAction = openNew,
                )
            }
            else -> {
                val asc = remember(items) { items.sortedBy { it.timestamp } }
                val today = today()
                val inRange = remember(asc, range, today) {
                    val from = range.fromDay(today)
                    if (from == null) asc else asc.filter { MeasureStats.dayX(it.timestamp) >= from }
                }
                val deltas = remember(asc) { asc.zipWithNext().associate { (a, b) -> b.id to (b.value - a.value) } }
                LazyColumn(
                    Modifier.fillMaxSize().padding(pad),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "chart") { ChartCard(type, asc, inRange, range, { range = it }, goal) }
                    item(key = "stats") { StatsSection(type, asc.last(), inRange, range, heightCm, goal) }
                    if (type == MeasureType.WEIGHT) {
                        item(key = "goal") { GoalCard(asc, goal, onEdit = { goalDialog = true }) }
                        item(key = "bmi") { BmiCard(asc.last(), heightCm, onEditHeight = { heightDialog = true }) }
                    } else {
                        val last = asc.last()
                        val a = MeasureStats.assess(type, last, heightCm)
                        if (a != null) {
                            item(key = "assessment") { AssessmentCard(type, last, a) }
                        } else if (type == MeasureType.WAIST && heightCm == null) {
                            item(key = "assessment") {
                                SectionCard(title = "Referencia") {
                                    Text(
                                        "Con tu estatura puedo mostrarte la relación cintura/estatura, una referencia orientativa.",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(onClick = { heightDialog = true }) { Text("Agregar estatura") }
                                }
                            }
                        }
                    }
                    item(key = "history_label") {
                        SectionLabel(if (items.size == 1) "Historial · 1 registro" else "Historial · ${items.size} registros")
                    }
                    items(items, key = { it.id }) { m ->
                        HistoryRow(
                            type = type,
                            m = m,
                            assessment = if (type == MeasureType.WEIGHT) null else MeasureStats.assess(type, m, heightCm),
                            delta = if (type.secondLabel == null) deltas[m.id] else null,
                            onClick = { editingId = m.id; formOpen = true },
                        )
                    }
                }
            }
        }
    }

    if (formOpen) {
        val initial = loaded?.firstOrNull { it.id == editingId && editingId != 0L }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { formOpen = false }, sheetState = sheetState) {
            MeasureFormContent(
                type = type,
                initial = initial,
                onSave = { m ->
                    Savia.io {
                        if (m.id == 0L) Savia.db.measurements().insert(m) else Savia.db.measurements().update(m)
                    }
                    formOpen = false
                },
                onCancel = { formOpen = false },
                onDelete = { deleteTarget = initial },
                modifier = Modifier.verticalScroll(rememberScrollState()).imePadding(),
            )
        }
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "Eliminar medición",
            text = "Se eliminará ${type.valueWithUnit(target)} del ${Fmt.dateTime(target.timestamp)}. Esta acción no se puede deshacer.",
            onConfirm = {
                Savia.io { Savia.db.measurements().delete(target) }
                deleteTarget = null
                formOpen = false
            },
            onDismiss = { deleteTarget = null },
        )
    }

    if (goalDialog) {
        NumberDialog(
            title = "Meta de peso",
            label = "Meta",
            unit = "kg",
            initial = goal,
            min = 20.0,
            max = 400.0,
            text = "Elige un peso objetivo realista; si tienes dudas, platícalo con un profesional de la salud.",
            onSave = { v -> updateProfile { it.copy(goalWeightKg = v) }; goalDialog = false },
            onDismiss = { goalDialog = false },
            clearLabel = "Quitar meta",
            onClear = { updateProfile { it.copy(goalWeightKg = null) }; goalDialog = false },
        )
    }
    if (heightDialog) {
        NumberDialog(
            title = "Tu estatura",
            label = "Estatura",
            unit = "cm",
            initial = heightCm,
            min = 50.0,
            max = 250.0,
            text = "Se guarda en tu ficha y se usa para calcular el IMC.",
            onSave = { v -> updateProfile { it.copy(heightCm = v) }; heightDialog = false },
            onDismiss = { heightDialog = false },
        )
    }
}

/** "36.2–37.1", o un solo valor si mínimo y máximo coinciden. */
private fun minMax(min: Double, max: Double, decimals: Int): String {
    val a = Fmt.num(min, decimals)
    val b = Fmt.num(max, decimals)
    return if (a == b) a else "$a–$b"
}

private fun List<TPoint>.toChart(): List<ChartPoint> = map { ChartPoint(it.x, it.y) }

@Composable
private fun ChartCard(
    type: MeasureType,
    asc: List<Measurement>,
    inRange: List<Measurement>,
    range: ChartRange,
    onRange: (ChartRange) -> Unit,
    goal: Double?,
) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val colors = SaviaTheme.colors
    val bandAlpha = if (colors.isDark) 0.16f else 0.13f

    SectionCard {
        ChartRangeSelector(range, onRange)
        Spacer(Modifier.height(8.dp))
        if (inRange.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No hay registros en este periodo.\nElige un rango mayor.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            return@SectionCard
        }
        val series = ArrayList<ChartSeries>()
        val bands = ArrayList<ChartBand>()
        val hLines = ArrayList<ChartHLine>()
        val legend = ArrayList<Pair<String, Color>>()
        val firstX = MeasureStats.dayX(inRange.first().timestamp)

        when (type) {
            MeasureType.WEIGHT -> {
                val pts = MeasureStats.points(inRange)
                series += ChartSeries("Peso", pts.toChart(), primary, fill = true)
                legend += "Peso" to primary
                if (pts.size >= 5) {
                    val ma = remember(asc, firstX) { MeasureStats.movingAverage(MeasureStats.points(asc)).filter { it.x >= firstX } }
                    series += ChartSeries("Media 7 días", ma.toChart(), colors.chart[3], dots = false, strokeWidth = 2f, selectable = false)
                    legend += "Media móvil 7 días" to colors.chart[3]
                }
                val reg = if (pts.size >= 3) MeasureStats.regression(pts) else null
                if (reg != null) {
                    val a = pts.first().x
                    val b = pts.last().x
                    series += ChartSeries(
                        "Tendencia", listOf(ChartPoint(a, reg.at(a)), ChartPoint(b, reg.at(b))), outline,
                        dots = false, dashed = true, strokeWidth = 1.5f, selectable = false,
                    )
                    legend += "Tendencia" to outline
                }
                if (goal != null) {
                    hLines += ChartHLine(goal, colors.good, "Meta ${Fmt.num(goal, 1)}")
                    legend += "Meta" to colors.good
                }
            }
            MeasureType.BLOOD_PRESSURE -> {
                val sysColor = colors.chart[1]
                val diaColor = colors.chart[2]
                series += ChartSeries("Sistólica", MeasureStats.points(inRange).toChart(), sysColor)
                series += ChartSeries("Diastólica", MeasureStats.points(inRange, second = true).toChart(), diaColor)
                bands += ChartBand(90.0, 120.0, sysColor.copy(alpha = bandAlpha))
                bands += ChartBand(60.0, 80.0, diaColor.copy(alpha = bandAlpha))
                legend += "Sistólica (alta)" to sysColor
                legend += "Diastólica (baja)" to diaColor
            }
            else -> {
                series += ChartSeries(type.label, MeasureStats.points(inRange).toChart(), primary, fill = bandFor(type, inRange) == null)
                bandFor(type, inRange)?.let { (lo, hi) ->
                    bands += ChartBand(lo, hi, colors.good.copy(alpha = bandAlpha))
                    legend += type.label to primary
                    legend += "Rango de referencia" to colors.good
                }
            }
        }
        LineChart(
            series = series,
            bands = bands,
            hLines = hLines,
            yFormatter = { Fmt.num(it, 1) },
            unit = type.unit,
        )
        if (legend.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            ChartLegend(legend)
        }
        if (type == MeasureType.BLOOD_PRESSURE) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Las franjas marcan el rango de referencia de cada valor (90–120 y 60–80 mmHg).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "Toca o arrastra sobre la gráfica para ver cada valor.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Rango de referencia orientativo para sombrear en la gráfica (o null si no aplica). */
private fun bandFor(type: MeasureType, inRange: List<Measurement>): Pair<Double, Double>? = when (type) {
    MeasureType.GLUCOSE -> {
        val fasting = inRange.count { it.tag.contains("ayunas", true) || it.tag.contains("antes de comer", true) }
        if (fasting * 2 >= inRange.size) 70.0 to 99.0 else 70.0 to 139.0
    }
    MeasureType.HEART_RATE -> 60.0 to 100.0
    MeasureType.SPO2 -> 95.0 to 100.0
    MeasureType.TEMPERATURE -> 36.0 to 37.4
    else -> null
}

@Composable
private fun StatsSection(
    type: MeasureType,
    latest: Measurement,
    inRange: List<Measurement>,
    range: ChartRange,
    heightCm: Double?,
    goal: Double?,
) {
    val d = type.decimals
    val rangeText = if (range == ChartRange.ALL) "todo el historial" else "últimos ${range.label}"
    val s1 = remember(inRange) { MeasureStats.summary(inRange.map { it.value }) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (type.secondLabel != null) {
            val s2 = remember(inRange) { MeasureStats.summary(inRange.mapNotNull { it.value2 }) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Última", type.valueText(latest), Modifier.weight(1f), sub = Fmt.relativeDay(latest.timestamp.millisToEpochDay()))
                StatTile(
                    "Promedio",
                    if (s1 != null && s2 != null) "${Fmt.num(s1.avg, 0)}/${Fmt.num(s2.avg, 0)}" else "—",
                    Modifier.weight(1f), sub = rangeText,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Sistólica", s1?.let { minMax(it.min, it.max, d) } ?: "—", Modifier.weight(1f), sub = "mín – máx")
                StatTile("Diastólica", s2?.let { minMax(it.min, it.max, d) } ?: "—", Modifier.weight(1f), sub = "mín – máx")
            }
        } else {
            val change = s1?.takeIf { it.count >= 2 }?.change
            val changeColor = when {
                type != MeasureType.WEIGHT || goal == null || change == null || s1 == null || abs(change) < 0.05 -> MaterialTheme.colorScheme.onSurface
                sign(goal - s1.first) == sign(change) -> SaviaTheme.colors.good
                else -> SaviaTheme.colors.warn
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(if (type == MeasureType.WEIGHT) "Actual" else "Última", "${Fmt.num(latest.value, d)} ${type.unit}", Modifier.weight(1f), sub = Fmt.relativeDay(latest.timestamp.millisToEpochDay()))
                StatTile(
                    "Cambio",
                    change?.let { "${Fmt.signed(it, d.coerceAtLeast(1))} ${type.unit}" } ?: "—",
                    Modifier.weight(1f), sub = rangeText, valueColor = changeColor,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Promedio", s1?.let { "${Fmt.num(it.avg, d.coerceAtLeast(1))} ${type.unit}" } ?: "—", Modifier.weight(1f), sub = rangeText)
                StatTile("Mín – máx", s1?.let { minMax(it.min, it.max, d) } ?: "—", Modifier.weight(1f), sub = type.unit)
            }
            if (type == MeasureType.WEIGHT) {
                val reg = remember(inRange) { MeasureStats.points(inRange).takeIf { it.size >= 3 }?.let { MeasureStats.regression(it) } }
                val bmi = MeasureStats.bmi(latest.value, heightCm)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(
                        "Ritmo",
                        reg?.let { "${Fmt.signed(it.perWeek, 2)} kg/sem" } ?: "—",
                        Modifier.weight(1f), sub = "tendencia, $rangeText",
                    )
                    StatTile(
                        "IMC",
                        bmi?.let { Fmt.num(it, 1) } ?: "—",
                        Modifier.weight(1f),
                        sub = bmi?.let { MeasureStats.bmiCategory(it).label } ?: "falta tu estatura",
                        valueColor = bmi?.let { MeasureStats.bmiCategory(it).level.color() } ?: MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun AssessmentCard(type: MeasureType, latest: Measurement, a: Assessment) {
    SectionCard(title = "Última medición") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(type.valueWithUnit(latest), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(10.dp))
            Pill(a.label, a.level.color())
        }
        val context = listOfNotNull(Fmt.relativeDateTime(latest.timestamp), latest.tag.takeIf { it.isNotEmpty() }).joinToString(" · ")
        Text(context, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (a.detail.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(a.detail, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(10.dp))
        InfoNote("Referencia orientativa para personas adultas; no es un diagnóstico. Tu médico puede indicarte otros valores objetivo.")
    }
}

@Composable
private fun GoalCard(asc: List<Measurement>, goal: Double?, onEdit: () -> Unit) {
    val colors = SaviaTheme.colors
    SectionCard(
        title = "Meta de peso",
        action = if (goal != null) ({ TextButton(onClick = onEdit) { Text("Editar") } }) else null,
    ) {
        if (goal == null) {
            Text(
                "Define una meta para ver cuánto te falta y una fecha estimada según tu tendencia.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onEdit) { Text("Definir meta") }
            return@SectionCard
        }
        val first = asc.first().value
        val current = asc.last().value
        val reached = MeasureStats.goalReached(first, current, goal)
        val today = today()
        val pts = remember(asc) { MeasureStats.points(asc) }
        val projection = remember(pts, goal, today) { MeasureStats.goalProjection(pts, goal, today) }
        val recentRate = remember(pts) {
            val last = pts.last()
            pts.filter { it.x >= last.x - 60 }.takeIf { it.size >= 3 }?.let { MeasureStats.regression(it) }?.perWeek
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${Fmt.num(goal, 1)} kg", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(
                if (reached) "¡Meta alcanzada!" else "Te faltan ${Fmt.num(abs(goal - current), 1)} kg",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (reached) colors.good else MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { if (reached) 1f else MeasureStats.goalProgress(first, current, goal) },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = if (reached) colors.good else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            strokeCap = StrokeCap.Round,
            drawStopIndicator = {},
        )
        Spacer(Modifier.height(4.dp))
        Row {
            Text("Inicio ${Fmt.num(first, 1)} kg", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("Actual ${Fmt.num(current, 1)} kg", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        val message = when {
            reached -> "Llegaste a tu meta. Puedes mantenerla o definir una nueva."
            projection != null && projection.daysLeft > 0 -> {
                val rate = recentRate?.let { " (${Fmt.signed(it, 2)} kg por semana)" } ?: ""
                "A tu ritmo reciente$rate llegarías alrededor del ${Fmt.date(projection.epochDay)}, ${Fmt.daysFromNow(projection.epochDay)}."
            }
            pts.size < 3 -> "Registra tu peso varias veces para estimar una fecha."
            else -> "Con la tendencia de las últimas semanas no te estás acercando a la meta, así que no hay fecha estimada."
        }
        Text(message, style = MaterialTheme.typography.bodyMedium)
        if (!reached && projection != null && projection.daysLeft > 0) {
            Text(
                "Es una estimación: el peso cambia de forma natural día con día.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun BmiCard(latest: Measurement, heightCm: Double?, onEditHeight: () -> Unit) {
    SectionCard(
        title = "Índice de masa corporal",
        action = if (heightCm != null) ({ TextButton(onClick = onEditHeight) { Text("Estatura") } }) else null,
    ) {
        val bmi = MeasureStats.bmi(latest.value, heightCm)
        if (bmi == null || heightCm == null) {
            Text(
                "Agrega tu estatura para calcular tu IMC a partir de tu peso.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onEditHeight) { Text("Agregar estatura") }
            return@SectionCard
        }
        val a = MeasureStats.bmiCategory(bmi)
        val (lo, hi) = MeasureStats.healthyWeightRange(heightCm)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Fmt.num(bmi, 1), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(10.dp))
            Pill(a.label, a.level.color())
        }
        Text(
            "Con ${Fmt.num(latest.value, 1)} kg y ${Fmt.num(heightCm, 1)} cm de estatura. ${a.detail}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Para tu estatura, un IMC de 18.5 a 24.9 equivale a entre ${Fmt.num(lo, 1)} y ${Fmt.num(hi, 1)} kg.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(10.dp))
        InfoNote("El IMC es una referencia orientativa para personas adultas: no distingue músculo de grasa ni aplica igual en el embarazo, la niñez o en deportistas.")
    }
}

@Composable
private fun HistoryRow(type: MeasureType, m: Measurement, assessment: Assessment?, delta: Double?, onClick: () -> Unit) {
    SectionCard(onClick = onClick, contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(type.valueWithUnit(m), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(Fmt.relativeDateTime(m.timestamp), m.tag.takeIf { it.isNotEmpty() }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (m.notes.isNotEmpty()) {
                    Text(m.notes, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                }
            }
            if (assessment != null) {
                Pill(assessment.label, assessment.level.color())
            } else if (delta != null && abs(delta) >= 0.05) {
                Text(
                    Fmt.signed(delta, type.decimals.coerceAtLeast(1)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.Edit, contentDescription = "Editar", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.height(18.dp).width(18.dp))
        }
    }
}
