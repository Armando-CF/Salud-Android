package app.savia.ui.cycle

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.savia.Savia
import app.savia.ui.wellbeing.PeopleOnlyScreen
import app.savia.cycle.DayInfo
import app.savia.data.CycleCatalog
import app.savia.data.CycleDay
import app.savia.ui.components.ChoiceChips
import app.savia.ui.components.InfoNote
import app.savia.ui.components.SaviaDatePickerDialog
import app.savia.ui.components.SaviaScaffold
import app.savia.ui.components.SectionCard
import app.savia.ui.nav.Routes
import app.savia.ui.theme.SaviaTheme
import app.savia.util.Fmt
import app.savia.util.epochDayToDate
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val UI_PREFS = "savia_cycle_ui"
private const val KEY_END_ACK = "period_end_ack_day"

@Composable
fun CycleScreen(nav: NavController, onBack: (() -> Unit)?) {
    if (Savia.isPet) {
        PeopleOnlyScreen("Ciclo menstrual", onBack)
        return
    }
    val state = rememberCycleState()
    val showFert by Savia.prefs.showFertility.flow.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editMode by rememberSaveable { mutableStateOf(false) }
    var pickStart by remember { mutableStateOf(false) }

    SaviaScaffold(
        title = "Ciclo",
        onBack = onBack,
        actions = {
            IconButton(onClick = { nav.navigate(Routes.CYCLE_STATS) }) {
                Icon(Icons.Filled.BarChart, contentDescription = "Estadísticas del ciclo")
            }
            IconButton(onClick = { nav.navigate(Routes.CYCLE_SETTINGS) }) {
                Icon(Icons.Filled.Settings, contentDescription = "Ajustes del ciclo")
            }
        },
    ) { pad ->
        if (!state.loaded) return@SaviaScaffold
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.analysis.hasData) {
                HeaderCard(state, showFert)
                QuickActionsCard(state, context) { nav.navigate(Routes.cycleDay(state.today)) }
            } else {
                FirstTimeCard(onPickStart = { pickStart = true }, onEdit = { editMode = true })
            }
            CalendarCard(
                state = state,
                showFert = showFert,
                editMode = editMode,
                onEditMode = { editMode = it },
                onDayClick = { day ->
                    if (editMode) {
                        if (day <= state.today) {
                            val current = state.byDay[day]?.flow ?: 0
                            setFlow(context, day, if (current >= 1) 0 else 3)
                        }
                    } else {
                        nav.navigate(Routes.cycleDay(day))
                    }
                },
            )
            if (state.analysis.hasData) {
                TodayCard(state.byDay[state.today]) { nav.navigate(Routes.cycleDay(state.today)) }
            }
            InfoNote(
                "Las fechas de periodo, ovulación y ventana fértil son estimaciones basadas en tu historial. " +
                    "No sirven como método anticonceptivo ni sustituyen una consulta médica.",
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    if (pickStart) {
        SaviaDatePickerDialog(
            initial = state.today,
            onPick = { day ->
                pickStart = false
                if (day <= state.today) markPeriod(context, day, Savia.prefs.periodLength.value, state.today)
            },
            onDismiss = { pickStart = false },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Encabezado
// ---------------------------------------------------------------------------------------------

@Composable
private fun HeaderCard(state: CycleUiState, showFert: Boolean) {
    val a = state.analysis
    val status = state.status
    val colors = SaviaTheme.colors
    SectionCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            CycleRing(a, status.cycleDay, showFert) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (status.cycleDay != null) {
                        Text("Día", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${status.cycleDay}",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (status.inPeriod) colors.period else MaterialTheme.colorScheme.onSurface,
                        )
                        Text("del ciclo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = colors.period, modifier = Modifier.size(40.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                status.phaseLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (status.inPeriod || a.lateDays > 0) colors.period else MaterialTheme.colorScheme.primary,
            )
            Text(
                status.headline,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            status.detail?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (showFert && status.cycleDay != null) {
                val window = a.nextFertileWindow
                val ov = a.nextOvulation
                if (window != null || ov != null) {
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (window != null) {
                            val now = state.today in window
                            MiniTile(
                                "Ventana fértil", CycleLogic.rangeText(window),
                                if (now) "En curso (estimada)" else "Estimada · " + Fmt.daysFromNow(window.first),
                                colors.fertile, Modifier.weight(1f),
                            )
                        }
                        if (ov != null) {
                            MiniTile(
                                "Ovulación", Fmt.dateShort(ov), "Estimada · " + Fmt.daysFromNow(ov),
                                colors.ovulation, Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniTile(label: String, value: String, sub: String, valueColor: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = valueColor, maxLines = 1)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun FirstTimeCard(onPickStart: () -> Unit, onEdit: () -> Unit) {
    SectionCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(72.dp).clip(RoundedCornerShape(50)).background(SaviaTheme.colors.periodSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = SaviaTheme.colors.period, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("Empieza a seguir tu ciclo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                "Registra los días de tu periodo y Savia estimará el siguiente, tu ventana fértil y la ovulación. " +
                    "Entre más ciclos registres, mejores serán las estimaciones.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onPickStart, colors = ButtonDefaults.buttonColors(containerColor = SaviaTheme.colors.period, contentColor = onPeriodColor())) {
                Text("¿Cuándo empezó tu último periodo?")
            }
            TextButton(onClick = onEdit) { Text("Marcar días en el calendario") }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Acciones rápidas
// ---------------------------------------------------------------------------------------------

@Composable
private fun QuickActionsCard(state: CycleUiState, context: Context, onLogToday: () -> Unit) {
    val today = state.today
    val rec = state.byDay[today]
    // Preferencias de interfaz separadas por perfil ("savia_cycle_ui" para el perfil 1, "savia_cycle_ui_p<N>" para los demás).
    val sp = remember { context.getSharedPreferences(UI_PREFS + Savia.profileSuffix(), Context.MODE_PRIVATE) }
    var ackDay by remember { mutableLongStateOf(sp.getLong(KEY_END_ACK, -1L)) }
    var quick = CycleLogic.quickState(state.analysis, rec)
    if (quick == QuickState.CONTINUE && ackDay == today) quick = QuickState.START
    val periodColors = ButtonDefaults.buttonColors(containerColor = SaviaTheme.colors.period, contentColor = onPeriodColor())

    SectionCard(title = "Hoy · " + Fmt.dateWeekday(today)) {
        when (quick) {
            QuickState.START -> {
                Button(onClick = { setFlow(context, today, 3) }, colors = periodColors, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.WaterDrop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Empezó mi periodo")
                }
            }
            QuickState.CONTINUE -> {
                Text(
                    "Ayer registraste sangrado. ¿Tu periodo sigue hoy?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { setFlow(context, today, (state.byDay[today - 1]?.flow ?: 3).coerceAtLeast(2)) },
                        colors = periodColors,
                        modifier = Modifier.weight(1f),
                    ) { Text("Sigue hoy") }
                    OutlinedButton(
                        onClick = {
                            sp.edit().putLong(KEY_END_ACK, today).apply()
                            ackDay = today
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Ya terminó") }
                }
            }
            QuickState.LOGGED -> {
                Text("Flujo de hoy", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ChoiceChips(
                    options = CycleCatalog.flows.filter { it.first > 0 },
                    selected = rec?.flow?.takeIf { it > 0 },
                    onSelect = { setFlow(context, today, it ?: 0) },
                )
                TextButton(onClick = { setFlow(context, today, 0) }) { Text("Terminó: hoy no hubo sangrado") }
            }
        }
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = onLogToday, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Registrar hoy (síntomas, ánimo y más)")
        }
    }
}

@Composable
private fun TodayCard(rec: CycleDay?, onOpen: () -> Unit) {
    val lines = remember(rec) { rec?.let { CycleLogic.summary(it) } ?: emptyList() }
    SectionCard(title = if (lines.isEmpty()) "Registro de hoy" else "Hoy registraste", onClick = onOpen) {
        if (lines.isEmpty()) {
            Text(
                "Aún no registras nada hoy. Toca para anotar cómo te sientes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            for (l in lines) {
                Row(Modifier.padding(vertical = 2.dp)) {
                    Text("•  ", color = SaviaTheme.colors.period, fontWeight = FontWeight.Bold)
                    Text(l, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Calendario
// ---------------------------------------------------------------------------------------------

private val WEEKDAYS = listOf("L", "M", "M", "J", "V", "S", "D")

@Composable
private fun CalendarCard(
    state: CycleUiState,
    showFert: Boolean,
    editMode: Boolean,
    onEditMode: (Boolean) -> Unit,
    onDayClick: (Long) -> Unit,
) {
    val todayDate = remember(state.today) { state.today.epochDayToDate() }
    val currentPage = CycleLogic.monthIndex(todayDate)
    // Desde enero de 1990 hasta 5 años adelante.
    val pager = rememberPagerState(initialPage = currentPage) { currentPage + 61 }
    val scope = rememberCoroutineScope()
    val shown = CycleLogic.monthOfIndex(pager.currentPage)

    SectionCard(contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) } }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Mes anterior")
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Fmt.monthYear(shown), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (pager.currentPage != currentPage) {
                    Text(
                        "Ir a hoy",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { scope.launch { pager.animateScrollToPage(currentPage) } }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            IconButton(onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(pager.pageCount - 1)) } }) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Mes siguiente")
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            for (w in WEEKDAYS) {
                Text(
                    w,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()) { page ->
            MonthGrid(CycleLogic.monthOfIndex(page), state, showFert, editMode, onDayClick)
        }
        Spacer(Modifier.height(6.dp))
        if (editMode) {
            InfoNote(
                "Modo edición: toca los días en que tuviste sangrado para marcarlos o desmarcarlos. " +
                    "Para detallar el flujo de un día, sal del modo edición y tócalo.",
                color = SaviaTheme.colors.periodSoft,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = editMode,
                onClick = { onEditMode(!editMode) },
                label = { Text(if (editMode) "Listo" else "Editar periodo") },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (editMode) "Tocando días…" else "Toca un día para ver o registrar",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Legend(showFert)
    }
}

@Composable
private fun MonthGrid(
    month: LocalDate,
    state: CycleUiState,
    showFert: Boolean,
    editMode: Boolean,
    onDayClick: (Long) -> Unit,
) {
    val cells = remember(month) { CycleLogic.monthGridDays(month) }
    Column(Modifier.fillMaxWidth()) {
        for (week in cells.chunked(7)) {
            Row(Modifier.fillMaxWidth()) {
                for ((day, inMonth) in week) {
                    DayCell(
                        day = day,
                        rec = state.byDay[day],
                        info = state.analysis.info(day),
                        isToday = day == state.today,
                        showFert = showFert,
                        dim = !inMonth || (editMode && day > state.today),
                        onClick = { onDayClick(day) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Long,
    rec: CycleDay?,
    info: DayInfo,
    isToday: Boolean,
    showFert: Boolean,
    dim: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaviaTheme.colors
    val onPeriod = onPeriodColor()
    val onSurface = MaterialTheme.colorScheme.onSurface
    val dotNeutral = MaterialTheme.colorScheme.onSurfaceVariant
    val flow = rec?.flow ?: 0
    val extras = rec != null && CycleLogic.hasExtras(rec)

    var fill: Color? = null
    var ring: Color? = null
    var dashed = false
    var textColor = onSurface
    when {
        flow >= 2 -> {
            fill = colors.period.copy(alpha = when (flow) { 2 -> 0.62f; 3 -> 0.82f; else -> 1f })
            textColor = onPeriod
        }
        flow == 1 || info.inPeriod -> {
            fill = colors.periodSoft
            textColor = if (colors.isDark) onSurface else colors.period
        }
        info.predictedPeriod -> {
            ring = colors.period
            dashed = true
            textColor = colors.period
        }
        showFert && info.ovulation -> {
            fill = colors.fertileSoft
            ring = colors.ovulation
        }
        showFert && info.fertile -> fill = colors.fertileSoft
    }
    val alpha = if (dim) 0.34f else 1f

    Box(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .drawBehind {
                val c = Offset(size.width / 2f, size.height / 2f)
                val r = minOf(size.width, size.height) / 2f - 4.dp.toPx()
                fill?.let { drawCircle(it.copy(alpha = it.alpha * alpha), r, c) }
                ring?.let {
                    drawCircle(
                        it.copy(alpha = alpha), r - 1.dp.toPx(), c,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null,
                        ),
                    )
                }
                if (isToday) drawCircle(onSurface, r + 2.5.dp.toPx(), c, style = Stroke(2.dp.toPx()))
                if (extras) {
                    drawCircle(
                        (if (flow >= 2) onPeriod else dotNeutral).copy(alpha = alpha),
                        2.dp.toPx(),
                        Offset(c.x, c.y + 12.dp.toPx()),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${LocalDate.ofEpochDay(day).dayOfMonth}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday || flow >= 2) FontWeight.Bold else FontWeight.Normal,
            color = textColor.copy(alpha = alpha),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(showFert: Boolean) {
    val colors = SaviaTheme.colors
    val onSurface = MaterialTheme.colorScheme.onSurface
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LegendItem("Periodo") { drawCircle(colors.period) }
        LegendItem("Periodo estimado") {
            drawCircle(
                colors.period, size.minDimension / 2f - 1.dp.toPx(),
                style = Stroke(1.6.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))),
            )
        }
        if (showFert) {
            LegendItem("Ventana fértil") { drawCircle(colors.fertileSoft) }
            LegendItem("Ovulación") {
                drawCircle(colors.fertileSoft)
                drawCircle(colors.ovulation, size.minDimension / 2f - 1.dp.toPx(), style = Stroke(1.8.dp.toPx()))
            }
        }
        LegendItem("Hoy") { drawCircle(onSurface, size.minDimension / 2f - 1.dp.toPx(), style = Stroke(1.8.dp.toPx())) }
        LegendItem("Con notas") { drawCircle(neutral, 2.5.dp.toPx()) }
    }
}

@Composable
private fun LegendItem(label: String, draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).drawBehind(draw))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
