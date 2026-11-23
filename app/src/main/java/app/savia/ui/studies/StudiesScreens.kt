package app.savia.ui.studies

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import app.savia.data.Study
import app.savia.data.StudyCategories
import app.savia.data.StudyResult
import app.savia.ui.components.EmptyState
import app.savia.ui.components.InfoNote
import app.savia.ui.components.Pill
import app.savia.ui.components.SaviaScaffold
import app.savia.ui.components.SectionCard
import app.savia.ui.components.Sparkline
import app.savia.ui.nav.Routes
import app.savia.ui.nav.back
import app.savia.ui.theme.SaviaTheme
import app.savia.util.Fmt
import app.savia.util.epochDayToDate
import app.savia.util.today

/** Lista de estudios y chequeos, con pestaña de evolución de parámetros. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudiesScreen(nav: NavController) {
    val studies by remember { Savia.db.studies().observeAll() }.collectAsStateWithLifecycle(initialValue = null)
    val results by remember { Savia.db.studies().observeAllResults() }.collectAsStateWithLifecycle(initialValue = emptyList())
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }

    SaviaScaffold(
        title = "Estudios y chequeos",
        onBack = { nav.back() },
        actions = {
            if (tab == 0 && !studies.isNullOrEmpty()) {
                IconButton(onClick = {
                    searching = !searching
                    if (!searching) query = ""
                }) {
                    Icon(if (searching) Icons.Filled.SearchOff else Icons.Filled.Search, contentDescription = if (searching) "Cerrar búsqueda" else "Buscar")
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.studyEdit()) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nuevo estudio") },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Estudios") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Parámetros") })
            }
            val list = studies
            when {
                list == null -> Unit
                tab == 0 -> StudyList(
                    nav = nav,
                    studies = list,
                    results = results,
                    searching = searching,
                    query = query,
                    onQuery = { query = it },
                    category = category,
                    onCategory = { category = it },
                )
                else -> ParamList(nav, list, results)
            }
        }
    }
}

@Composable
private fun StudyList(
    nav: NavController,
    studies: List<Study>,
    results: List<StudyResult>,
    searching: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    category: String?,
    onCategory: (String?) -> Unit,
) {
    if (studies.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Biotech,
            title = "Aún no hay estudios",
            message = "Guarda aquí tus análisis, radiografías, chequeos y vacunas con sus resultados para ver cómo evolucionan con el tiempo.",
            actionLabel = "Agregar estudio",
            onAction = { nav.navigate(Routes.studyEdit()) },
        )
        return
    }
    val today = today()
    val byStudy = remember(results) { results.groupBy { it.studyId } }
    val categories = remember(studies) {
        val used = studies.map { it.category }.toSet()
        StudyCategories.all.map { it.first }.filter { it in used }
    }
    val activeCategory = category?.takeIf { it in categories }
    val q = normalizeName(query)
    val filtered = remember(studies, byStudy, activeCategory, q) {
        studies.filter { s ->
            (activeCategory == null || s.category == activeCategory) &&
                (q.isEmpty() || listOf(s.title, s.place, s.doctor, s.summary, StudyCategories.label(s.category))
                    .any { normalizeName(it).contains(q) } ||
                    byStudy[s.id].orEmpty().any { normalizeName(it.name).contains(q) })
        }
    }
    val followUps = remember(studies, today) { upcomingFollowUps(studies, today) }
    val grouped = remember(filtered) { filtered.groupBy { it.epochDay.epochDayToDate().year }.toSortedMap(compareByDescending { it }) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (searching) {
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Buscar por título, lugar, parámetro…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, contentDescription = "Borrar búsqueda") }
                        }
                    },
                    shape = RoundedCornerShape(50),
                )
            }
        }
        if (categories.size > 1) {
            item(key = "cats") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = activeCategory == null, onClick = { onCategory(null) }, label = { Text("Todos") })
                    for (c in categories) {
                        FilterChip(
                            selected = activeCategory == c,
                            onClick = { onCategory(if (activeCategory == c) null else c) },
                            label = { Text(StudyCategories.short(c)) },
                        )
                    }
                }
            }
        }
        if (followUps.isNotEmpty() && q.isEmpty() && activeCategory == null) {
            item(key = "followups") { FollowUpsCard(nav, followUps, today) }
        }
        if (filtered.isEmpty()) {
            item(key = "none") {
                EmptyState(
                    icon = Icons.Filled.SearchOff,
                    title = "Sin coincidencias",
                    message = "Ningún estudio coincide con el filtro o la búsqueda.",
                )
            }
        }
        for ((year, items) in grouped) {
            item(key = "year_$year") {
                Text(
                    "$year",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                )
            }
            items(items, key = { it.id }) { s ->
                StudyCard(s, byStudy[s.id].orEmpty()) { nav.navigate(Routes.study(s.id)) }
            }
        }
    }
}

@Composable
private fun FollowUpsCard(nav: NavController, followUps: List<Study>, today: Long) {
    SectionCard(title = "Seguimientos próximos", containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)) {
        followUps.forEachIndexed { i, s ->
            val day = s.followUpEpochDay ?: return@forEachIndexed
            val overdue = day < today
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { nav.navigate(Routes.study(s.id)) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(s.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${Fmt.date(day)} · ${Fmt.daysFromNow(day)}" + if (overdue) " (vencido)" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (overdue) SaviaTheme.colors.warn else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (overdue) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
                TextButton(onClick = { Savia.io { Savia.db.studies().update(s.copy(followUpEpochDay = null)) } }) { Text("Hecho") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudyCard(study: Study, results: List<StudyResult>, onClick: () -> Unit) {
    val out = results.count { it.outOfRange }
    SectionCard(onClick = onClick, contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.Top) {
            CategoryBadge(study.category)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(study.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${Fmt.date(study.epochDay)} · ${StudyCategories.short(study.category)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val where = listOf(study.place, study.doctor).filter { it.isNotBlank() }.joinToString(" · ")
                if (where.isNotEmpty()) {
                    Text(where, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (study.status.isNotEmpty() || results.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusPill(study.status)
                        if (out > 0) {
                            Pill(if (out == 1) "1 fuera de rango" else "$out fuera de rango", SaviaTheme.colors.warn)
                        }
                        if (results.isNotEmpty()) {
                            Pill(if (results.size == 1) "1 resultado" else "${results.size} resultados", MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Pestaña "Parámetros": cada parámetro numérico con su último valor; tocar abre su evolución. */
@Composable
private fun ParamList(nav: NavController, studies: List<Study>, results: List<StudyResult>) {
    val summaries = remember(studies, results) { paramSummaries(studies, results) }
    if (summaries.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Biotech,
            title = "Sin parámetros todavía",
            message = "Cuando captures resultados numéricos en tus estudios (glucosa, colesterol, hemoglobina…) aquí verás la evolución de cada uno.",
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "note") {
            InfoNote("Evolución de cada parámetro a través de tus estudios. Los rangos son orientativos; usa los de tu laboratorio.")
        }
        items(summaries, key = { normalizeName(it.name) }) { p ->
            val last = p.latest
            val f = last.result.flag()
            SectionCard(onClick = { nav.navigate(Routes.paramTrend(p.name)) }, contentPadding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                last.result.valueText(),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (f == RangeFlag.ALTO || f == RangeFlag.BAJO) flagColor(f) else MaterialTheme.colorScheme.onSurface,
                            )
                            if (f != RangeFlag.SIN_RANGO) {
                                Spacer(Modifier.width(8.dp))
                                Pill(flagLabel(f), flagColor(f))
                            }
                        }
                        Text(
                            "${Fmt.date(last.epochDay)} · " + if (p.points.size == 1) "1 valor" else "${p.points.size} valores",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (p.points.size >= 2) {
                        Sparkline(p.points.map { it.value }, Modifier.width(72.dp).height(32.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
