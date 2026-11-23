package app.savia.ui.symptoms

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.savia.Savia
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import app.savia.data.SymptomEntry
import app.savia.ui.components.BarChart
import app.savia.ui.components.BarEntry
import app.savia.ui.components.ChartRange
import app.savia.ui.components.ChartRangeSelector
import app.savia.ui.components.EmptyState
import app.savia.ui.components.HBarList
import app.savia.ui.components.InfoNote
import app.savia.ui.components.Pill
import app.savia.ui.components.SaviaScaffold
import app.savia.ui.components.SectionCard
import app.savia.ui.components.StatTile
import app.savia.ui.nav.Routes
import app.savia.ui.theme.SaviaTheme
import app.savia.util.Fmt
import app.savia.util.LocaleEs
import app.savia.util.epochDayToDate
import app.savia.util.millisToEpochDay
import app.savia.util.today
import kotlin.math.roundToInt

/**
 * Historial y resumen de síntomas.
 * @param initialTab 0 = Historial, 1 = Resumen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SymptomsScreen(nav: NavController, onBack: (() -> Unit)?, initialTab: Int = 0) {
    val entries by remember { Savia.db.symptoms().observeAll() }.collectAsStateWithLifecycle(initialValue = null)
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    val kit = remember { SymptomKit.current() }

    SaviaScaffold(
        title = "Síntomas",
        onBack = onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.symptomEdit()) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Registrar") },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            val list = entries
            when {
                list == null -> Unit
                list.isEmpty() -> EmptyState(
                    icon = Icons.Filled.EditNote,
                    title = "Aún no registras síntomas",
                    message = if (kit.isPet) kit.emptyMessage
                    else "Cuando algo te moleste, anótalo aquí: qué sientes, qué tan fuerte y en qué parte del cuerpo. " +
                        "Con el tiempo podrás ver patrones y contárselos mejor a tu médico.",
                    actionLabel = "Registrar un síntoma",
                    onAction = { nav.navigate(Routes.symptomEdit()) },
                )
                else -> {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 4.dp)) {
                        listOf("Historial", "Resumen").forEachIndexed { i, label ->
                            SegmentedButton(
                                selected = tab == i,
                                onClick = { tab = i },
                                shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
                            ) { Text(label) }
                        }
                    }
                    if (tab == 0) HistoryTab(list, nav) else SummaryTab(list)
                }
            }
        }
    }
}

@Composable
private fun HistoryTab(list: List<SymptomEntry>, nav: NavController) {
    var query by rememberSaveable { mutableStateOf("") }
    var nameFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val topNames = remember(list) {
        list.groupingBy { it.name }.eachCount().entries.sortedByDescending { it.value }.take(6).map { it.key }
    }
    val filtered = remember(list, query, nameFilter) {
        list.filter { (nameFilter == null || it.name == nameFilter) && SymptomStats.matches(it, query) }
    }
    val grouped = remember(filtered) { filtered.groupBy { it.timestamp.millisToEpochDay() }.toList() }
    val focus = LocalFocusManager.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "search") {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                // La tecla "Buscar" del teclado lo cierra para dejar ver los resultados.
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                placeholder = { Text("Buscar por síntoma, zona o nota") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Borrar búsqueda") }
                    }
                },
                shape = MaterialTheme.shapes.extraLarge,
            )
        }
        if (topNames.size > 1) {
            item(key = "chips") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (n in topNames) {
                        FilterChip(
                            selected = nameFilter == n,
                            onClick = { nameFilter = if (nameFilter == n) null else n },
                            label = { Text(n, maxLines = 1) },
                        )
                    }
                }
            }
        }
        if (filtered.isEmpty()) {
            item(key = "none") {
                Text(
                    "No hay registros que coincidan con tu búsqueda.",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        for ((day, items) in grouped) {
            item(key = "h$day") { DayHeader(day, items.size) }
            items(items, key = { it.id }) { e ->
                SymptomListItem(e, onClick = { nav.navigate(Routes.symptomEdit(e.id)) })
            }
        }
    }
}

@Composable
private fun DayHeader(day: Long, count: Int) {
    val t = today()
    val year = day.epochDayToDate().year
    val weekday = Fmt.dateWeekday(day) + if (year != t.epochDayToDate().year) " $year" else ""
    val title = when (day - t) {
        0L -> "Hoy · $weekday"
        -1L -> "Ayer · $weekday"
        else -> weekday.replaceFirstChar { it.titlecase(LocaleEs) }
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (count > 1) {
            Text("$count registros", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Círculo con la intensidad (1–10) coloreado según su gravedad. */
@Composable
fun IntensityBadge(intensity: Int, modifier: Modifier = Modifier) {
    val color = SaviaTheme.colors.intensity(intensity)
    Box(modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Text("$intensity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

/**
 * Tarjeta de un síntoma registrado (reutilizable, p. ej. en la pantalla de inicio).
 * @param showDate muestra "Hoy, 14:05" en vez de solo la hora.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SymptomListItem(entry: SymptomEntry, onClick: () -> Unit, modifier: Modifier = Modifier, showDate: Boolean = false) {
    SectionCard(modifier = modifier, onClick = onClick, contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.Top) {
            IntensityBadge(entry.intensity)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = buildList {
                    add(if (showDate) Fmt.relativeDateTime(entry.timestamp) else Fmt.time(entry.timestamp))
                    add(SymptomKit.current().intensityLabel(entry.intensity))
                    entry.durationMin?.let { add("duró ${Fmt.duration(it)}") }
                }.joinToString(" · ")
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val tags = SymptomStats.splitTags(entry.tags)
                val region = entry.regionLabel?.takeIf { it.isNotBlank() }
                if (region != null || tags.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (region != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Accessibility,
                                    contentDescription = "Zona del cuerpo",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(2.dp))
                                Pill(region, MaterialTheme.colorScheme.primary)
                            }
                        }
                        for (tag in tags) Pill(tag, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (entry.notes.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(entry.notes, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun SummaryTab(list: List<SymptomEntry>) {
    var range by rememberSaveable { mutableStateOf(ChartRange.M3) }
    val t = today()
    val s = remember(list, range, t) { SymptomStats.summarize(list, range.fromDay(t), t) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ChartRangeSelector(range, { range = it })
        if (s.total == 0) {
            Text(
                "No hay registros en este periodo. Prueba con un rango más amplio.",
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val avg = s.avgIntensity ?: 0.0
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Registros", "${s.total}", Modifier.weight(1f), sub = if (s.total == 1) "síntoma" else "síntomas")
                StatTile(
                    "Intensidad", Fmt.num(avg, 1), Modifier.weight(1f), sub = "promedio de 10",
                    valueColor = SaviaTheme.colors.intensity(avg.roundToInt()),
                )
                StatTile("Días", "${s.daysWithSymptoms}", Modifier.weight(1f), sub = "con molestias")
            }
            SectionCard(title = "Más frecuentes") {
                HBarList(s.topNames)
            }
            SectionCard(title = if (s.monthly) "Registros por mes" else "Registros por semana") {
                BarChart(
                    bars = s.buckets.map { BarEntry(it.first, it.second.toDouble()) },
                    valueFormatter = { if (it == 0.0) "" else Fmt.num(it, 0) },
                )
                if (!s.monthly) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Cada barra es una semana; la fecha indica el lunes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SectionCard(title = "Más intensos") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((name, a) in s.strongest) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${Fmt.num(a, 1)} · ${SymptomKit.current().intensityLabel(a.roundToInt())}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = SaviaTheme.colors.intensity(a.roundToInt()),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Intensidad promedio de cada síntoma, de 1 a 10.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (s.topRegions.isNotEmpty()) {
                SectionCard(title = "Zonas del cuerpo") {
                    HBarList(s.topRegions, color = SaviaTheme.colors.chart[2])
                }
            }
            if (s.topTriggers.isNotEmpty()) {
                SectionCard(title = "Posibles desencadenantes") {
                    HBarList(s.topTriggers, color = SaviaTheme.colors.chart[3])
                }
            }
            InfoNote(
                "Este resumen es orientativo y se basa solo en lo que has registrado. Si un síntoma se repite, " +
                    "empeora o te preocupa, coméntalo con un profesional de la salud.",
            )
        }
    }
}
