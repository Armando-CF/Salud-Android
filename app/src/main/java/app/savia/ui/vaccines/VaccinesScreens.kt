package app.savia.ui.vaccines

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.PestControl
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.savia.Savia
import app.savia.data.ProfileInfo
import app.savia.data.Vaccination
import app.savia.data.VaccinationTypes
import app.savia.ui.components.EmptyState
import app.savia.ui.components.InfoNote
import app.savia.ui.components.Pill
import app.savia.ui.components.SaviaScaffold
import app.savia.ui.components.SectionCard
import app.savia.ui.components.SectionLabel
import app.savia.ui.nav.Routes
import app.savia.ui.nav.back
import app.savia.ui.theme.SaviaTheme
import app.savia.util.Fmt
import app.savia.util.epochDayToDate
import app.savia.util.today
import kotlinx.coroutines.flow.map

/** Icono según el tipo de registro. */
internal fun vaccineTypeIcon(type: String): ImageVector = when (type) {
    VaccineCatalog.VACUNA -> Icons.Filled.Vaccines
    VaccineCatalog.DESP_INTERNA -> Icons.Filled.Medication
    VaccineCatalog.DESP_EXTERNA -> Icons.Filled.PestControl
    else -> Icons.Filled.HealthAndSafety
}

/** Color según el tipo de registro. */
@Composable
internal fun vaccineTypeColor(type: String): Color = when (type) {
    VaccineCatalog.VACUNA -> MaterialTheme.colorScheme.primary
    VaccineCatalog.DESP_INTERNA -> SaviaTheme.colors.warn
    VaccineCatalog.DESP_EXTERNA -> SaviaTheme.colors.ovulation
    else -> MaterialTheme.colorScheme.secondary
}

@Composable
internal fun dueStatusColor(status: DueStatus): Color = when (status) {
    DueStatus.VENCIDA -> SaviaTheme.colors.bad
    DueStatus.PROXIMA -> SaviaTheme.colors.warn
    DueStatus.PROGRAMADA -> MaterialTheme.colorScheme.primary
}

/** Insignia cuadrada con el icono del tipo. */
@Composable
internal fun VaccineTypeBadge(type: String, size: Dp = 44.dp) {
    val color = vaccineTypeColor(type)
    Box(
        Modifier.size(size).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(vaccineTypeIcon(type), contentDescription = VaccinationTypes.label(type), tint = color)
    }
}

/**
 * Resumen del perfil ACTIVO leído de su propia base de datos (fila única de `profile`).
 * Arranca con lo que Savia ya conoce de ese perfil para no parpadear y se corrige con la fila real,
 * así el módulo nunca muestra la variante equivocada (persona / mascota) justo después de cambiar de perfil.
 */
@Composable
internal fun rememberVaccineProfile(): ProfileInfo {
    val id = remember { Savia.activeId.value }
    val flow = remember(id) { Savia.dbFor(id).profile().observe().map { ProfileInfo.of(id, it) } }
    val info by flow.collectAsStateWithLifecycle(initialValue = remember(id) { Savia.infoOf(id) })
    return info
}

/** Título del módulo según el perfil. */
internal fun vaccinesTitle(isPet: Boolean): String = if (isPet) "Vacunas y desparasitación" else "Vacunas"

/**
 * Lista del módulo de vacunas: pendientes y próximas arriba, historial por año abajo.
 * Para mascotas incluye desparasitación y un filtro por tipo.
 */
@Composable
fun VaccinesScreen(nav: NavController) {
    val profile = rememberVaccineProfile()
    val isPet = profile.isPet
    val t = today()
    val flow = remember { Savia.db.vaccinations().observeAll().map<List<Vaccination>, List<Vaccination>?> { it } }
    val loadedList by flow.collectAsStateWithLifecycle(initialValue = null)
    var filter by rememberSaveable { mutableStateOf("") }

    val openNew: (VaccineDraft.Draft?) -> Unit = { draft ->
        if (draft != null) VaccineDraft.put(draft) else VaccineDraft.take()
        nav.navigate(Routes.vaccineEdit())
    }

    SaviaScaffold(
        title = vaccinesTitle(isPet),
        onBack = { nav.back() },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { openNew(null) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Registrar") },
            )
        },
    ) { pad ->
        val all = loadedList ?: return@SaviaScaffold
        if (all.isEmpty()) {
            Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp)) {
                EmptyState(
                    icon = Icons.Filled.Vaccines,
                    title = if (isPet) "Sin vacunas ni desparasitaciones" else "Lleva aquí tu cartilla de vacunación",
                    message = if (isPet) {
                        "Registra las vacunas y desparasitaciones de ${profile.displayName} con su fecha y la próxima aplicación, y Savia te avisa cuando toque."
                    } else {
                        "Anota cada vacuna con su fecha, dosis y lote. Si tiene refuerzo, guarda la próxima fecha y Savia te avisa cuando se acerque."
                    },
                    actionLabel = if (isPet) "Registrar la primera" else "Registrar una vacuna",
                    onAction = { openNew(null) },
                )
                InfoNote(disclaimer(isPet))
            }
            return@SaviaScaffold
        }

        val pending = remember(all, t) { VaccineLogic.upcoming(all, t) }
        val pendingIds = remember(pending) { pending.map { it.vaccination.id }.toSet() }
        val typesPresent = remember(all) { VaccinationTypes.all.map { it.first }.filter { k -> all.any { it.type == k } } }
        val effectiveFilter = if (filter in typesPresent) filter else ""
        val history = remember(all, effectiveFilter) { if (effectiveFilter.isEmpty()) all else all.filter { it.type == effectiveFilter } }
        val byYear = remember(history) { history.groupBy { it.epochDay.epochDayToDate().year }.toSortedMap(compareByDescending { it }) }

        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (pending.isNotEmpty()) {
                item(key = "pending") {
                    PendingCard(
                        pending = pending,
                        today = t,
                        onOpen = { nav.navigate(Routes.vaccineEdit(it.vaccination.id)) },
                        onRegister = {
                            val v = it.vaccination
                            openNew(VaccineDraft.Draft(type = v.type, name = v.name, dose = VaccineLogic.nextDose(v.dose), place = v.place))
                        },
                    )
                }
            }
            if (isPet && typesPresent.size > 1) {
                item(key = "filter") {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(selected = effectiveFilter.isEmpty(), onClick = { filter = "" }, label = { Text("Todo") })
                        for (k in typesPresent) {
                            FilterChip(
                                selected = effectiveFilter == k,
                                onClick = { filter = if (effectiveFilter == k) "" else k },
                                label = { Text(filterLabel(k)) },
                            )
                        }
                    }
                }
            }
            for ((year, entries) in byYear) {
                item(key = "y$year") { SectionLabel("$year · ${countText(entries.size)}") }
                items(entries, key = { it.id }) { v ->
                    HistoryCard(
                        v = v,
                        showType = isPet || v.type != VaccineCatalog.VACUNA,
                        pendingDue = if (v.id in pendingIds) v.nextDueEpochDay else null,
                        today = t,
                        onClick = { nav.navigate(Routes.vaccineEdit(v.id)) },
                    )
                }
            }
            item(key = "note") { InfoNote(disclaimer(isPet)) }
        }
    }
}

private fun countText(n: Int): String = if (n == 1) "1 registro" else "$n registros"

private fun filterLabel(type: String): String = when (type) {
    VaccineCatalog.VACUNA -> "Vacunas"
    VaccineCatalog.DESP_INTERNA -> "Desp. interna"
    VaccineCatalog.DESP_EXTERNA -> "Pulgas y garrapatas"
    else -> "Otros"
}

internal fun disclaimer(isPet: Boolean): String =
    if (isPet) {
        "Los nombres y fechas sugeridas son orientativos. Sigue la cartilla de tu mascota y lo que indique su veterinaria o veterinario."
    } else {
        "Los nombres y fechas sugeridas son orientativos. Sigue tu cartilla de vacunación y lo que indique tu médica o médico."
    }

/** Tarjeta "Pendientes y próximas". */
@Composable
private fun PendingCard(
    pending: List<DueItem>,
    today: Long,
    onOpen: (DueItem) -> Unit,
    onRegister: (DueItem) -> Unit,
) {
    val overdue = pending.count { it.overdue }
    SectionCard(
        title = "Pendientes y próximas",
        action = {
            if (overdue > 0) Pill(if (overdue == 1) "1 vencida" else "$overdue vencidas", SaviaTheme.colors.bad)
        },
    ) {
        pending.forEachIndexed { i, item ->
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            DueRow(item, today, onOpen = { onOpen(item) }, onRegister = { onRegister(item) })
        }
    }
}

/**
 * Fila de una próxima aplicación pendiente (reutilizable, p. ej. en la pantalla de inicio).
 * @param onRegister abre el alta prellenada; si es null no se muestra el botón.
 */
@Composable
fun DueRow(item: DueItem, today: Long, onOpen: () -> Unit, onRegister: (() -> Unit)?, modifier: Modifier = Modifier) {
    val v = item.vaccination
    val status = VaccineLogic.status(item, today)
    val color = dueStatusColor(status)
    val next = VaccineLogic.nextDose(v.dose)
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpen).padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VaccineTypeBadge(v.type, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    v.name + if (next.isNotEmpty()) " · $next" else "",
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${VaccineLogic.dueText(item.dueDay, today)} · ${Fmt.date(item.dueDay)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status == DueStatus.PROGRAMADA) MaterialTheme.colorScheme.onSurfaceVariant else color,
                    fontWeight = if (status == DueStatus.PROGRAMADA) FontWeight.Normal else FontWeight.Medium,
                )
            }
            Spacer(Modifier.width(8.dp))
            Pill(status.label, color)
        }
        if (onRegister != null) {
            Row(Modifier.fillMaxWidth().padding(start = 52.dp), horizontalArrangement = Arrangement.Start) {
                if (status == DueStatus.PROGRAMADA) {
                    TextButton(onClick = onRegister, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("Registrar aplicación")
                    }
                } else {
                    FilledTonalButton(onClick = onRegister, modifier = Modifier.padding(top = 4.dp).height(36.dp), contentPadding = PaddingValues(horizontal = 14.dp)) {
                        Icon(Icons.Filled.EventAvailable, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Registrar aplicación")
                    }
                }
            }
        }
    }
}

/** Tarjeta de un registro del historial. */
@Composable
private fun HistoryCard(v: Vaccination, showType: Boolean, pendingDue: Long?, today: Long, onClick: () -> Unit) {
    SectionCard(onClick = onClick, contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.Top) {
            VaccineTypeBadge(v.type)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        v.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    if (v.dose.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Pill(v.dose.trim(), vaccineTypeColor(v.type))
                    }
                }
                Text(
                    listOfNotNull(
                        Fmt.date(v.epochDay),
                        VaccinationTypes.label(v.type).takeIf { showType },
                        v.place.trim().takeIf { it.isNotEmpty() },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (v.lot.isNotBlank()) {
                    Text("Lote ${v.lot.trim()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (pendingDue != null) {
                    val status = VaccineLogic.status(pendingDue, today)
                    Text(
                        "Próxima: ${Fmt.date(pendingDue)} · ${VaccineLogic.dueText(pendingDue, today).lowercase()}",
                        style = MaterialTheme.typography.bodySmall, color = dueStatusColor(status), fontWeight = FontWeight.Medium,
                    )
                }
                if (v.notes.isNotBlank()) {
                    Text(v.notes.trim(), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
