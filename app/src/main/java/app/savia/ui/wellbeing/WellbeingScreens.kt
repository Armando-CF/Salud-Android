package app.savia.ui.wellbeing

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Battery1Bar
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material.icons.filled.SentimentNeutral
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.SentimentVeryDissatisfied
import androidx.compose.material.icons.filled.SentimentVerySatisfied
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.savia.Savia
import app.savia.data.DailyLog
import app.savia.data.SaviaDb
import app.savia.ui.components.ChartLegend
import app.savia.ui.components.ChartPoint
import app.savia.ui.components.ChartRange
import app.savia.ui.components.ChartRangeSelector
import app.savia.ui.components.ChartSeries
import app.savia.ui.components.LineChart
import app.savia.ui.components.SaviaDatePickerDialog
import app.savia.ui.components.SaviaScaffold
import app.savia.ui.components.SaviaTextField
import app.savia.ui.components.SectionCard
import app.savia.ui.components.StatTile
import app.savia.ui.nav.back
import app.savia.ui.theme.SaviaTheme
import app.savia.util.Fmt
import app.savia.util.today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Etiquetas e iconos de las escalas 1–5 del bienestar diario (reutilizables en otras pantallas). */
object WellbeingScale {
    val moodLabels = listOf("Muy mal", "Mal", "Regular", "Bien", "Excelente")
    val energyLabels = listOf("Sin energía", "Poca", "Normal", "Buena", "Mucha")
    val stressLabels = listOf("Nada", "Poco", "Moderado", "Alto", "Muy alto")

    val moodIcons: List<ImageVector> = listOf(
        Icons.Filled.SentimentVeryDissatisfied, Icons.Filled.SentimentDissatisfied, Icons.Filled.SentimentNeutral,
        Icons.Filled.SentimentSatisfied, Icons.Filled.SentimentVerySatisfied,
    )
    val energyIcons: List<ImageVector> = listOf(
        Icons.Filled.Battery1Bar, Icons.Filled.Battery2Bar, Icons.Filled.Battery4Bar, Icons.Filled.Battery5Bar, Icons.Filled.BatteryFull,
    )

    fun moodLabel(level: Int): String = moodLabels.getOrElse(level - 1) { "" }
    fun energyLabel(level: Int): String = energyLabels.getOrElse(level - 1) { "" }
    fun stressLabel(level: Int): String = stressLabels.getOrElse(level - 1) { "" }
    fun moodIcon(level: Int): ImageVector = moodIcons[(level - 1).coerceIn(0, 4)]

    /** Color para escalas donde más es mejor (ánimo, energía). */
    @Composable
    fun goodColor(level: Int): Color = when {
        level <= 1 -> SaviaTheme.colors.bad
        level == 2 -> SaviaTheme.colors.warn
        level == 3 -> SaviaTheme.colors.fertile
        else -> SaviaTheme.colors.good
    }

    /** Color para escalas donde más es peor (estrés). */
    @Composable
    fun stressColor(level: Int): Color = when {
        level <= 2 -> SaviaTheme.colors.good
        level <= 4 -> SaviaTheme.colors.warn
        else -> SaviaTheme.colors.bad
    }

    /** ¿El registro no tiene ningún dato? */
    fun isEmpty(l: DailyLog): Boolean =
        l.mood == null && l.energy == null && l.stress == null && l.sleepHours == null &&
            l.waterGlasses == null && l.exerciseMin == null && l.notes.isBlank()
}

/** Escribe los registros diarios en orden (un solo consumidor), para que el guardado automático nunca se desordene. */
private object WellbeingWriter {
    private val channel = Channel<Pair<SaviaDb, DailyLog>>(Channel.UNLIMITED)

    init {
        Savia.scope.launch(Dispatchers.IO) {
            for ((db, log) in channel) {
                runCatching {
                    val dao = db.dailyLogs()
                    if (WellbeingScale.isEmpty(log)) dao.deleteDay(log.epochDay) else dao.save(log.copy(notes = log.notes.trim()))
                }
            }
        }
    }

    /**
     * La base se toma AL ENVIAR (la del perfil en el que se hizo la edición), no al escribir: así un cambio
     * de perfil inmediatamente después nunca manda el registro a la base de otro perfil.
     */
    fun submit(log: DailyLog, db: SaviaDb = Savia.db) {
        channel.trySend(db to log)
    }
}

/**
 * Bienestar diario: ánimo, energía, estrés, sueño, agua y ejercicio de un día, con guardado automático,
 * y tendencias debajo.
 * @param initialDay día mostrado al abrir (por defecto hoy).
 */
@Composable
fun WellbeingScreen(nav: NavController, initialDay: Long? = null) {
    if (Savia.isPet) {
        PeopleOnlyScreen("Bienestar diario") { nav.back() }
        return
    }
    val db = remember { Savia.db }
    val dao = db.dailyLogs()
    val t = today()
    var day by rememberSaveable { mutableLongStateOf((initialDay ?: t).coerceAtMost(t)) }
    var log by remember { mutableStateOf<DailyLog?>(null) }
    var showPicker by remember { mutableStateOf(false) }

    LaunchedEffect(day) {
        // Mientras carga se sigue mostrando el día anterior (sus ediciones conservan su propio epochDay).
        log = dao.get(day) ?: DailyLog(epochDay = day)
    }

    val update: (DailyLog) -> Unit = { new ->
        log = new
        WellbeingWriter.submit(new, db)
    }

    SaviaScaffold(title = "Bienestar diario", onBack = { nav.back() }) { pad ->
        // SaviaScaffold ya ajusta el contenido a la altura del teclado (la nota del día es el único campo de texto).
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DayNavigator(day = day, today = t, onPrev = { day -= 1 }, onNext = { if (day < t) day += 1 }, onPick = { showPicker = true })

            val l = log
            if (l != null) {
                SectionCard(title = "¿Cómo te sientes?") {
                    IconScale(
                        title = "Ánimo", icons = WellbeingScale.moodIcons, labels = WellbeingScale.moodLabels,
                        value = l.mood, onChange = { update(l.copy(mood = it)) }, colorOf = { WellbeingScale.goodColor(it) },
                    )
                    Spacer(Modifier.height(14.dp))
                    IconScale(
                        title = "Energía", icons = WellbeingScale.energyIcons, labels = WellbeingScale.energyLabels,
                        value = l.energy, onChange = { update(l.copy(energy = it)) }, colorOf = { WellbeingScale.goodColor(it) },
                    )
                    Spacer(Modifier.height(14.dp))
                    IconScale(
                        title = "Estrés", icons = null, labels = WellbeingScale.stressLabels,
                        value = l.stress, onChange = { update(l.copy(stress = it)) }, colorOf = { WellbeingScale.stressColor(it) },
                    )
                }

                SectionCard(title = "Hábitos del día") {
                    StepperRow(
                        icon = Icons.Filled.Bedtime, label = "Sueño", tint = SaviaTheme.colors.ovulation,
                        valueText = l.sleepHours?.let { "${Fmt.num(it, 1)} h" },
                        onMinus = { update(l.copy(sleepHours = l.sleepHours?.let { v -> if (v <= 0.0) null else (v - 0.5).coerceAtLeast(0.0) })) },
                        onPlus = { update(l.copy(sleepHours = l.sleepHours?.let { v -> (v + 0.5).coerceAtMost(24.0) } ?: 7.0)) },
                    )
                    Spacer(Modifier.height(10.dp))
                    StepperRow(
                        icon = Icons.Filled.WaterDrop, label = "Agua", tint = SaviaTheme.colors.fertile,
                        valueText = l.waterGlasses?.let { if (it == 1) "1 vaso" else "$it vasos" },
                        onMinus = { update(l.copy(waterGlasses = l.waterGlasses?.let { v -> if (v <= 0) null else v - 1 })) },
                        onPlus = { update(l.copy(waterGlasses = ((l.waterGlasses ?: 0) + 1).coerceAtMost(30))) },
                    )
                    Spacer(Modifier.height(10.dp))
                    StepperRow(
                        icon = Icons.AutoMirrored.Filled.DirectionsRun, label = "Ejercicio", tint = SaviaTheme.colors.good,
                        valueText = l.exerciseMin?.let { Fmt.duration(it) },
                        onMinus = { update(l.copy(exerciseMin = l.exerciseMin?.let { v -> if (v <= 0) null else (v - 5).coerceAtLeast(0) })) },
                        onPlus = { update(l.copy(exerciseMin = l.exerciseMin?.let { v -> (v + 5).coerceAtMost(600) } ?: 10)) },
                    )
                }

                SectionCard(title = "Nota del día") {
                    SaviaTextField(
                        value = l.notes,
                        onValueChange = { update(l.copy(notes = it.take(1000))) },
                        label = "¿Algo que quieras recordar?",
                        singleLine = false,
                        minLines = 2,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Los cambios se guardan automáticamente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (!WellbeingScale.isEmpty(l)) {
                        TextButton(onClick = { update(DailyLog(epochDay = l.epochDay)) }) {
                            Text("Borrar este día", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            TrendsSection()
        }
    }

    if (showPicker) {
        SaviaDatePickerDialog(
            initial = day,
            onPick = { day = it.coerceAtMost(t); showPicker = false },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun DayNavigator(day: Long, today: Long, onPrev: () -> Unit, onNext: () -> Unit, onPick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Día anterior") }
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onPick).padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val rel = when (day - today) {
                0L -> "Hoy"
                -1L -> "Ayer"
                else -> Fmt.daysFromNow(day).replaceFirstChar { it.titlecase() }
            }
            Text(rel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(Fmt.dateLong(day), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onNext, enabled = day < today) { Icon(Icons.Filled.ChevronRight, contentDescription = "Día siguiente") }
    }
}

/** Escala de 1 a 5 con iconos (o números si [icons] es null). Tocar el valor elegido lo quita. */
@Composable
private fun IconScale(
    title: String,
    icons: List<ImageVector>?,
    labels: List<String>,
    value: Int?,
    onChange: (Int?) -> Unit,
    colorOf: @Composable (Int) -> Color,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (value != null) {
                Text(labels.getOrElse(value - 1) { "" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = colorOf(value))
            } else {
                Text("Sin registrar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (level in 1..5) {
                val selected = value == level
                val color = colorOf(level)
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (selected) color.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceContainer)
                        .clickable(onClickLabel = "${labels[level - 1]} ($level de 5)") { onChange(if (selected) null else level) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (icons != null) {
                        Icon(
                            icons[level - 1],
                            contentDescription = labels[level - 1],
                            tint = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(if (selected) 34.dp else 28.dp),
                        )
                    } else {
                        Text(
                            "$level",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (icons == null) {
            Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                Text(labels.first(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(labels.last(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
            }
        }
    }
}

/** Fila con icono, etiqueta y contador − valor +. */
@Composable
private fun StepperRow(icon: ImageVector, label: String, tint: Color, valueText: String?, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint)
        }
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = onMinus, enabled = valueText != null) { Icon(Icons.Filled.Remove, contentDescription = "Menos $label") }
        Text(
            valueText ?: "—",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = if (valueText != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(92.dp),
            maxLines = 1,
        )
        FilledTonalIconButton(onClick = onPlus) { Icon(Icons.Filled.Add, contentDescription = "Más $label") }
    }
}

@Composable
private fun TrendsSection() {
    val logs by remember { Savia.db.dailyLogs().observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList())
    var range by rememberSaveable { mutableStateOf(ChartRange.M1) }
    val t = today()
    val from = range.fromDay(t)
    val inRange = remember(logs, from) { logs.filter { from == null || it.epochDay >= from }.sortedBy { it.epochDay } }
    val colors = SaviaTheme.colors

    SectionCard(title = "Tendencias") {
        ChartRangeSelector(range, { range = it })
        Spacer(Modifier.height(8.dp))
        if (inRange.isEmpty()) {
            Text(
                "Aún no hay registros en este periodo. Anota cómo te sientes unos días y aquí verás tu evolución.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            return@SectionCard
        }

        fun avg(sel: (DailyLog) -> Number?): Double? = inRange.mapNotNull { sel(it)?.toDouble() }.takeIf { it.isNotEmpty() }?.average()
        val mood = avg { it.mood }
        val energy = avg { it.energy }
        val stress = avg { it.stress }
        val sleep = avg { it.sleepHours }
        val water = avg { it.waterGlasses }
        val exerciseTotal = inRange.sumOf { it.exerciseMin ?: 0 }
        val spanDays = ((t - inRange.first().epochDay) + 1).coerceAtLeast(1)
        val exercisePerWeek = exerciseTotal * 7.0 / spanDays

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Ánimo", mood?.let { Fmt.num(it, 1) } ?: "—", Modifier.weight(1f), sub = "promedio de 5")
            StatTile("Energía", energy?.let { Fmt.num(it, 1) } ?: "—", Modifier.weight(1f), sub = "promedio de 5")
            StatTile("Estrés", stress?.let { Fmt.num(it, 1) } ?: "—", Modifier.weight(1f), sub = "promedio de 5")
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Sueño", sleep?.let { "${Fmt.num(it, 1)} h" } ?: "—", Modifier.weight(1f), sub = "por noche")
            StatTile("Agua", water?.let { Fmt.num(it, 1) } ?: "—", Modifier.weight(1f), sub = "vasos al día")
            StatTile("Ejercicio", if (exerciseTotal > 0) Fmt.num(exercisePerWeek, 0) else "—", Modifier.weight(1f), sub = "min por semana")
        }

        fun points(sel: (DailyLog) -> Number?): List<ChartPoint> =
            inRange.mapNotNull { l -> sel(l)?.let { ChartPoint(l.epochDay.toDouble(), it.toDouble()) } }

        val moodPts = points { it.mood }
        val energyPts = points { it.energy }
        val stressPts = points { it.stress }
        if (moodPts.size + energyPts.size + stressPts.size > 0) {
            Spacer(Modifier.height(14.dp))
            Text("Ánimo, energía y estrés", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            LineChart(
                series = listOf(
                    ChartSeries("Ánimo", moodPts, colors.chart[0], dots = moodPts.size <= 31),
                    ChartSeries("Energía", energyPts, colors.chart[3], dots = false, strokeWidth = 2f),
                    ChartSeries("Estrés", stressPts, colors.chart[1], dots = false, dashed = true, strokeWidth = 2f),
                ).filter { it.points.isNotEmpty() },
                modifier = Modifier.fillMaxWidth().height(200.dp),
                yMin = 1.0,
                yMax = 5.0,
                yFormatter = { Fmt.num(it, 1) },
            )
            ChartLegend(listOf("Ánimo" to colors.chart[0], "Energía" to colors.chart[3], "Estrés (línea punteada)" to colors.chart[1]))
        }

        val sleepPts = points { it.sleepHours }
        if (sleepPts.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Horas de sueño", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            LineChart(
                series = listOf(ChartSeries("Sueño", sleepPts, colors.ovulation, dots = sleepPts.size <= 31, fill = true)),
                modifier = Modifier.fillMaxWidth().height(180.dp),
                unit = "h",
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Toca o arrastra sobre una gráfica para ver el valor de cada día.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
