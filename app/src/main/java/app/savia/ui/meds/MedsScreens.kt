package app.savia.ui.meds

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.savia.Savia
import app.savia.data.Appointment
import app.savia.data.MedIntake
import app.savia.data.Medication
import app.savia.ui.components.EmptyState
import app.savia.ui.components.NavRow
import app.savia.ui.components.Pill
import app.savia.ui.components.SaviaScaffold
import app.savia.ui.components.SectionCard
import app.savia.ui.components.SectionLabel
import app.savia.ui.nav.Routes
import app.savia.ui.nav.back
import app.savia.ui.theme.SaviaTheme
import app.savia.util.Fmt
import app.savia.util.millisToDateTime
import app.savia.util.millisToEpochDay

/** Minutos de tolerancia antes de marcar una toma como atrasada. */
private const val LATE_AFTER_MIN = 15

@Composable
fun MedsScreen(nav: NavController) {
    val context = LocalContext.current
    val dao = Savia.db.medications()
    val now by rememberNowMillis()
    val today = now.millisToEpochDay()
    val nowMinutes = now.millisToDateTime().let { it.hour * 60 + it.minute }

    val meds by remember { dao.observeAll() }.collectAsStateWithLifecycle(initialValue = null as List<Medication>?)
    val intakes by remember(today) { dao.observeIntakesOfDay(today) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val upcoming by remember { Savia.db.appointments().observeUpcoming(System.currentTimeMillis(), 1) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val access = rememberNotificationAccess()
    var showInactive by rememberSaveable { mutableStateOf(false) }

    SaviaScaffold(
        title = "Medicamentos",
        onBack = { nav.back() },
        actions = {
            IconButton(onClick = { nav.navigate(Routes.APPOINTMENTS) }) {
                Icon(Icons.Filled.Event, contentDescription = rememberMedsText().appointmentsTitle)
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.medEdit()) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Medicamento") },
            )
        },
    ) { pad ->
        val list = meds ?: return@SaviaScaffold
        val current = list.filter { it.active && !MedSchedule.isFinished(it, today) }
        val inactive = list.filter { !it.active || MedSchedule.isFinished(it, today) }
        val doses = dosesForDay(current, intakes, today)
        val asNeeded = current.filter { MedSchedule.isAsNeeded(it) }
        val needsPermission = !access.allowed && current.any { it.remind && !MedSchedule.isAsNeeded(it) }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (list.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Medication,
                        title = "Aún no tienes medicamentos",
                        message = rememberMedsText().medsEmpty,
                        actionLabel = "Agregar medicamento",
                        onAction = { nav.navigate(Routes.medEdit()) },
                    )
                }
            }
            if (needsPermission) {
                item {
                    SectionCard(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.NotificationsOff, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Las notificaciones están desactivadas",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                                Text(
                                    "Savia no podrá avisarte a la hora de las tomas hasta que las permitas.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = access.request, modifier = Modifier.align(Alignment.End)) { Text(access.buttonLabel) }
                    }
                }
            }
            if (current.isNotEmpty()) {
                item { TodayCard(doses, today, nowMinutes, hasScheduled = current.any { !MedSchedule.isAsNeeded(it) }) }
            }
            if (asNeeded.isNotEmpty()) {
                item {
                    SectionCard(title = "Según sea necesario") {
                        asNeeded.forEachIndexed { i, med ->
                            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            AsNeededRow(med, today)
                        }
                    }
                }
            }
            if (current.isNotEmpty()) {
                item { SectionLabel("Mis medicamentos") }
                items(current, key = { it.id }) { med ->
                    MedCard(med, today, nowMinutes, dimmed = false) { nav.navigate(Routes.medEdit(med.id)) }
                }
            }
            if (inactive.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { showInactive = !showInactive }.padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Terminados o inactivos (${inactive.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            if (showInactive) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (showInactive) "Ocultar" else "Mostrar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (showInactive) {
                    items(inactive, key = { it.id }) { med ->
                        MedCard(med, today, nowMinutes, dimmed = true) { nav.navigate(Routes.medEdit(med.id)) }
                    }
                }
            }
            item {
                SectionCard(contentPadding = 8.dp) {
                    val next = upcoming.firstOrNull()
                    NavRow(
                        icon = Icons.Filled.Event,
                        title = rememberMedsText().appointmentsTitle,
                        onClick = { nav.navigate(Routes.APPOINTMENTS) },
                        subtitle = next?.let { "Próxima: ${appointmentTitle(it)} · ${Fmt.relativeDateTime(it.timestamp)}" } ?: "Sin citas próximas",
                    )
                }
            }
        }
    }
}

internal fun appointmentTitle(a: Appointment): String = a.title.ifBlank { MedsText.current().appointmentDefault }

/** Tarjeta "Hoy": tomas programadas del día con sus acciones. */
@Composable
private fun TodayCard(doses: List<DoseItem>, today: Long, nowMinutes: Int, hasScheduled: Boolean) {
    val context = LocalContext.current
    SectionCard(title = "Hoy", action = {
        Text(Fmt.dateWeekday(today), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }) {
        if (doses.isEmpty()) {
            Text(
                if (hasScheduled) "Hoy no hay tomas programadas." else rememberMedsText().noScheduledDoses,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }
        val done = doses.count { !it.pending }
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { done / doses.size.toFloat() },
                modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(50)),
                color = SaviaTheme.colors.good,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                drawStopIndicator = {},
            )
            Spacer(Modifier.width(12.dp))
            Text(
                if (done == doses.size) "¡Listo por hoy!" else "$done de ${doses.size} registradas",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        doses.forEachIndexed { i, dose ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            DoseRow(
                dose = dose,
                late = dose.pending && dose.minutes < nowMinutes - LATE_AFTER_MIN,
                onTake = { MedActions.take(context, dose, today) },
                onSkip = { MedActions.skip(context, dose, today) },
                onUndo = { MedActions.undo(context, dose, today) },
            )
        }
    }
}

/** Una toma programada: hora, medicamento, estado y acciones. Reutilizable en otras pantallas. */
@Composable
fun DoseRow(
    dose: DoseItem,
    late: Boolean,
    onTake: () -> Unit,
    onSkip: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaviaTheme.colors
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                dose.slot,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (late) colors.warn else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(60.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(dose.med.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val detail = listOf(dose.med.dose, dose.med.instructions).filter { it.isNotBlank() }.joinToString(" · ")
                if (detail.isNotEmpty()) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            val intake = dose.intake
            if (intake != null) {
                Spacer(Modifier.width(8.dp))
                if (dose.taken) {
                    Pill("Tomada ${Fmt.time(intake.timestamp)}", colors.good)
                } else {
                    Pill("Omitida", MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onUndo) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Deshacer", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (late) {
                Spacer(Modifier.width(8.dp))
                Pill("Atrasada", colors.warn)
            }
        }
        if (dose.pending) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onSkip) { Text("Omitir") }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = onTake) { Text("Tomada") }
            }
        }
    }
}

/** Medicamento sin horario: se registra cada toma cuando ocurre. */
@Composable
private fun AsNeededRow(med: Medication, today: Long) {
    val context = LocalContext.current
    val intakes by remember(med.id) { Savia.db.medications().observeIntakesOf(med.id) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val free = intakes.filter { it.status == MedSchedule.TAKEN }
    val todayOnes = free.filter { it.epochDay == today }
    val last = free.firstOrNull()
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    listOf(med.name, med.dose).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                val status = when {
                    todayOnes.isNotEmpty() -> "Hoy: ${todayOnes.size} ${if (todayOnes.size == 1) "toma" else "tomas"} · última a las ${Fmt.time(todayOnes.first().timestamp)}"
                    last != null -> "Última toma: ${Fmt.relativeDateTime(last.timestamp)}"
                    else -> "Sin tomas registradas"
                }
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { MedActions.logNow(med.id, today) }) { Text("Registrar toma") }
        }
        if (todayOnes.isNotEmpty()) {
            TextButton(onClick = { MedActions.deleteIntake(context, todayOnes.first()) }, modifier = Modifier.align(Alignment.End)) {
                Text("Deshacer última")
            }
        }
    }
}

/** Tarjeta de un medicamento con su horario y adherencia. */
@Composable
private fun MedCard(med: Medication, today: Long, nowMinutes: Int, dimmed: Boolean, onClick: () -> Unit) {
    val intakes by remember(med.id) { Savia.db.medications().observeIntakesOf(med.id) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val colors = SaviaTheme.colors
    val asNeeded = MedSchedule.isAsNeeded(med)
    val tint = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
    SectionCard(onClick = onClick, contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Medication, contentDescription = null, tint = tint) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    listOf(med.name, med.dose).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(MedSchedule.scheduleSummary(med), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val period = when {
                    med.endEpochDay != null && med.endEpochDay < today -> "Terminó el ${Fmt.date(med.endEpochDay)}"
                    med.endEpochDay != null -> "Hasta el ${Fmt.date(med.endEpochDay)}"
                    med.startEpochDay > today -> "Empieza el ${Fmt.date(med.startEpochDay)}"
                    !med.active -> "Inactivo"
                    else -> null
                }
                if (period != null) {
                    Text(period, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!asNeeded && !dimmed) {
                Icon(
                    if (med.remind) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                    contentDescription = if (med.remind) "Con recordatorio" else "Sin recordatorio",
                    tint = if (med.remind) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (!dimmed) {
            Spacer(Modifier.height(10.dp))
            if (asNeeded) {
                val count = intakes.count { it.status == MedSchedule.TAKEN && it.epochDay > today - 30 }
                Text(
                    "$count ${if (count == 1) "toma" else "tomas"} en los últimos 30 días",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val a7 = MedSchedule.recentAdherence(med, intakes, 7, today, nowMinutes)
                val a30 = MedSchedule.recentAdherence(med, intakes, 30, today, nowMinutes)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AdherencePill("7 días", a7)
                    AdherencePill("30 días", a30)
                    Text("tomas cumplidas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AdherencePill(label: String, a: Adherence) {
    val colors = SaviaTheme.colors
    val p = a.percent
    val color = when {
        p == null -> MaterialTheme.colorScheme.onSurfaceVariant
        p >= 80 -> colors.good
        p >= 50 -> colors.warn
        else -> colors.bad
    }
    Pill("$label: ${p?.let { "$it%" } ?: "—"}", color)
}

/** Historial reciente de tomas de un medicamento (se usa en el editor). */
@Composable
internal fun IntakeHistory(intakes: List<MedIntake>, onDelete: (MedIntake) -> Unit) {
    val colors = SaviaTheme.colors
    Column {
        intakes.forEachIndexed { i, intake ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Fmt.relativeDateTime(intake.timestamp), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (intake.slot.isEmpty()) "Toma libre" else "Toma de las ${intake.slot} · ${Fmt.dateShort(intake.epochDay)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (intake.status == MedSchedule.TAKEN) Pill("Tomada", colors.good) else Pill("Omitida", MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = { onDelete(intake) }) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Quitar registro", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
