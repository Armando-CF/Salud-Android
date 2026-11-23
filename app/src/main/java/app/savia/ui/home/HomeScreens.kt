package app.savia.ui.home

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
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.runtime.produceState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.savia.data.PetSpecies
import app.savia.data.ProfileInfo
import app.savia.data.VaccinationTypes
import app.savia.ui.profile.ProfileAvatar
import app.savia.ui.profile.ProfileSwitch
import app.savia.ui.vaccines.VaccineLogic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material.icons.filled.SentimentNeutral
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.SentimentVeryDissatisfied
import androidx.compose.material.icons.filled.SentimentVerySatisfied
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.savia.Savia
import app.savia.data.DailyLog
import app.savia.data.Profile
import app.savia.ui.components.NavRow
import app.savia.ui.components.Pill
import app.savia.ui.components.SaviaScaffold
import app.savia.ui.components.SectionCard
import app.savia.ui.components.Sparkline
import app.savia.ui.cycle.CycleSummaryCard
import app.savia.ui.cycle.rememberCycleState
import app.savia.ui.measure.MeasureRefs
import app.savia.ui.measure.MeasureStats
import app.savia.ui.meds.MedActions
import app.savia.ui.meds.dosesForDay
import app.savia.ui.nav.Routes
import app.savia.ui.nav.navigateTab
import app.savia.ui.studies.upcomingFollowUps
import app.savia.ui.theme.SaviaTheme
import app.savia.util.Fmt
import app.savia.util.millisToEpochDay
import app.savia.util.nowMillis
import app.savia.util.today

internal val moodIcons: List<ImageVector> = listOf(
    Icons.Filled.SentimentVeryDissatisfied, Icons.Filled.SentimentDissatisfied, Icons.Filled.SentimentNeutral,
    Icons.Filled.SentimentSatisfied, Icons.Filled.SentimentVerySatisfied,
)
internal val moodLabels = listOf("Muy mal", "Mal", "Regular", "Bien", "Excelente")

@Composable
fun HomeScreen(nav: NavController) {
    val t = today()
    val db = Savia.db
    val context = LocalContext.current
    val profile by db.profile().observe().collectAsStateWithLifecycle(initialValue = null)
    val cycleEnabled by Savia.prefs.cycleEnabled.flow.collectAsStateWithLifecycle()
    val symptoms by remember { db.symptoms().observeRecent(4) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val weights by remember { db.measurements().observeByType("weight") }.collectAsStateWithLifecycle(initialValue = emptyList())
    val log by remember(t) { db.dailyLogs().observeDay(t) }.collectAsStateWithLifecycle(initialValue = null)
    val meds by remember { db.medications().observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val intakes by remember(t) { db.medications().observeIntakesOfDay(t) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val appts by remember { db.appointments().observeUpcoming(nowMillis() - 3_600_000L, 2) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val studies by remember { db.studies().observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val cycle = rememberCycleState()

    val active by Savia.active.collectAsStateWithLifecycle()
    val isPet = active.isPet
    val allProfiles by remember { Savia.summaries }.collectAsStateWithLifecycle(initialValue = emptyList())
    val vaccines by remember { db.vaccinations().observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val others by produceState(emptyList<OtherProfileStatus>(), allProfiles, t) {
        value = withContext(Dispatchers.IO) { othersToday(allProfiles.filter { it.id != active.id }, t) }
    }

    // Si se llegó aquí por "Editar ficha" de otro perfil, abre la pantalla pendiente.
    LaunchedEffect(Unit) { ProfileSwitch.consume()?.let { nav.navigate(it) } }

    val name = profile?.name?.trim().orEmpty()
    val title = when {
        isPet || active.id != 1 -> active.displayName
        name.isEmpty() -> "Hola"
        else -> "Hola, ${name.substringBefore(' ')}"
    }
    SaviaScaffold(
        title = title,
        subtitle = listOf(active.subtitle.takeIf { isPet || active.id != 1 }.orEmpty(), Fmt.dateLong(t)).filter { it.isNotBlank() }.joinToString(" · "),
        actions = {
            // Perfil activo: toca para cambiar de persona o mascota
            Surface(
                onClick = { nav.navigate(Routes.PROFILES) },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.semantics { contentDescription = "Cambiar de perfil" },
            ) {
                Row(Modifier.padding(start = 4.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(active, size = 32.dp)
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Icon(Icons.Filled.Settings, contentDescription = "Ajustes") }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Accesos rápidos
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction(Icons.Filled.EditNote, "Síntoma", MaterialTheme.colorScheme.primary, Modifier.weight(1f)) { nav.navigate(Routes.symptomEdit()) }
                QuickAction(Icons.Filled.MonitorWeight, "Peso", SaviaTheme.colors.chart[2], Modifier.weight(1f)) { nav.navigate(Routes.measure("weight", add = true)) }
                if (isPet) {
                    QuickAction(Icons.Filled.Vaccines, "Vacuna", SaviaTheme.colors.good, Modifier.weight(1f)) { nav.navigate(Routes.vaccineEdit()) }
                    QuickAction(Icons.Filled.Event, "Cita", MaterialTheme.colorScheme.tertiary, Modifier.weight(1f)) { nav.navigate(Routes.appointmentEdit()) }
                } else {
                    QuickAction(Icons.Filled.Mood, "Bienestar", SaviaTheme.colors.warn, Modifier.weight(1f)) { nav.navigate(Routes.WELLBEING) }
                    if (cycleEnabled) {
                        QuickAction(Icons.Filled.WaterDrop, "Periodo", SaviaTheme.colors.period, Modifier.weight(1f)) { nav.navigate(Routes.cycleDay(t)) }
                    } else {
                        QuickAction(Icons.Filled.Favorite, "Presión", SaviaTheme.colors.period, Modifier.weight(1f)) { nav.navigate(Routes.measure("bp", add = true)) }
                    }
                }
            }

            // Otros perfiles con algo pendiente hoy (familia y mascotas)
            if (others.isNotEmpty()) {
                SectionCard(title = "Tu familia y mascotas", action = { TextButton(onClick = { nav.navigate(Routes.PROFILES) }) { Text("Perfiles") } }) {
                    for (o in others) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { Savia.switchTo(o.info.id) }.padding(vertical = 7.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ProfileAvatar(o.info, size = 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(o.info.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(o.notes.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = if (o.urgent) SaviaTheme.colors.warn else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (cycleEnabled && !isPet && cycle.loaded) {
                CycleSummaryCard(cycle.analysis, onClick = { nav.navigateTab(Routes.CYCLE) })
            }

            // ¿Cómo te sientes hoy?
            if (!isPet) MoodCard(log, onPick = { mood -> Savia.io { db.dailyLogs().save((db.dailyLogs().get(t) ?: DailyLog(epochDay = t)).copy(mood = mood)) } }, onOpen = { nav.navigate(Routes.WELLBEING) })

            // Medicamentos de hoy
            val doses = remember(meds, intakes, t) { dosesForDay(meds, intakes, t) }
            if (doses.isNotEmpty()) {
                val pending = doses.count { it.intake == null }
                SectionCard(
                    title = "Medicamentos de hoy",
                    action = { TextButton(onClick = { nav.navigate(Routes.MEDS) }) { Text("Ver todo") } },
                ) {
                    Text(
                        if (pending == 0) "Todo al día. ¡Bien!" else if (pending == 1) "Falta 1 toma" else "Faltan $pending tomas",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    for (d in doses.take(6)) {
                        val taken = d.intake?.status == "taken"
                        val skipped = d.intake?.status == "skipped"
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                if (d.intake != null) MedActions.undo(context, d, t) else MedActions.take(context, d, t)
                            }.padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(26.dp).clip(CircleShape).background(if (taken) SaviaTheme.colors.good else Color.Transparent),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (taken) Icon(Icons.Filled.Check, contentDescription = "Tomada", tint = Color.White, modifier = Modifier.size(18.dp))
                                else Icon(Icons.Outlined.Circle, contentDescription = "Pendiente", tint = MaterialTheme.colorScheme.outline)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(d.slot, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                d.med.name + if (d.med.dose.isNotBlank()) " · ${d.med.dose}" else "",
                                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            if (skipped) Pill("Omitida", MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }

            // Próxima cita
            appts.firstOrNull()?.let { a ->
                SectionCard(onClick = { nav.navigate(Routes.APPOINTMENTS) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Filled.Event, MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Próxima cita · ${Fmt.daysFromNow(a.timestamp.millisToEpochDay())}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold)
                            Text(a.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOf(Fmt.dateWeekday(a.timestamp.millisToEpochDay()) + ", " + Fmt.time(a.timestamp), a.doctor).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Vacunas pendientes o próximas (30 días)
            val dueVaccines = remember(vaccines, t) { VaccineLogic.dueSoon(vaccines, t, 30).take(3) }
            for (d in dueVaccines) {
                SectionCard(onClick = { nav.navigate(Routes.VACCINES) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Filled.Vaccines, if (d.overdue) SaviaTheme.colors.bad else SaviaTheme.colors.good)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                (if (d.overdue) "Vencida · " else "Próxima · ") + Fmt.daysFromNow(d.dueDay),
                                style = MaterialTheme.typography.labelMedium, color = if (d.overdue) SaviaTheme.colors.bad else SaviaTheme.colors.good, fontWeight = FontWeight.SemiBold,
                            )
                            Text(d.vaccination.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(VaccinationTypes.label(d.vaccination.type) + " · " + Fmt.date(d.dueDay), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Peso
            val ws = remember(weights, profile, isPet) { MeasureStats.weightSummary(weights, if (isPet) null else profile?.heightCm, profile?.goalWeightKg) }
            if (ws != null) {
                SectionCard(onClick = { nav.navigate(Routes.measure("weight")) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Peso", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            Text(weightText(ws.latest, isPet), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                            val parts = ArrayList<String>()
                            ws.change30?.let { parts += "${Fmt.signed(it, if (isPet) 2 else 1)} kg en 30 días" }
                            if (!isPet) ws.bmi?.let { parts += "IMC ${Fmt.num(it, 1)}" }
                            if (parts.isEmpty()) parts += Fmt.relativeDay(ws.latestTimestamp.millisToEpochDay())
                            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (ws.spark.size >= 2) Sparkline(ws.spark)
                    }
                }
            }

            // Síntomas recientes
            if (symptoms.isNotEmpty()) {
                SectionCard(
                    title = "Síntomas recientes",
                    action = { TextButton(onClick = { nav.navigateTab(Routes.SYMPTOMS) }) { Text("Ver todos") } },
                ) {
                    for (s in symptoms.take(3)) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { nav.navigate(Routes.symptomEdit(s.id)) }.padding(vertical = 7.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(SaviaTheme.colors.intensity(s.intensity)))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(Fmt.relativeDateTime(s.timestamp), s.regionLabel).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text("${s.intensity}/10", style = MaterialTheme.typography.labelLarge, color = SaviaTheme.colors.intensity(s.intensity), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Seguimientos de estudios
            val followUps = remember(studies, t) { upcomingFollowUps(studies, t).filter { (it.followUpEpochDay ?: 0) <= t + 45 }.take(2) }
            for (st in followUps) {
                SectionCard(onClick = { nav.navigate(Routes.study(st.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Filled.Science, SaviaTheme.colors.warn)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Seguimiento · ${Fmt.daysFromNow(st.followUpEpochDay!!)}", style = MaterialTheme.typography.labelMedium, color = SaviaTheme.colors.warn, fontWeight = FontWeight.SemiBold)
                            Text(st.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            // Primeros pasos (cuando aún no hay nada)
            if (symptoms.isEmpty() && ws == null && doses.isEmpty() && appts.isEmpty() && vaccines.isEmpty()) {
                SectionCard(title = "Empieza por aquí") {
                    if (isPet) {
                        Text(
                            "Aquí llevarás el historial de salud de ${active.displayName}: lo que anotes se queda solo en tu teléfono.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        NavRow(Icons.Filled.Vaccines, "Registra sus vacunas", { nav.navigate(Routes.VACCINES) }, subtitle = "Vacunas y desparasitación, con recordatorios")
                        NavRow(Icons.Filled.MonitorWeight, "Registra su peso", { nav.navigate(Routes.measure("weight", add = true)) }, subtitle = "Con gráfica y tendencia")
                        NavRow(Icons.Filled.EditNote, "Anota lo que le notes", { nav.navigate(Routes.symptomEdit()) }, subtitle = "Vómito, cojera, comezón, falta de apetito…")
                        NavRow(Icons.Filled.Pets, "Completa su ficha", { nav.navigate(Routes.PROFILE) }, subtitle = "Raza, edad, microchip, veterinario")
                    } else {
                        Text(
                            "Savia es tu bitácora de salud: todo lo que anotes se queda solo en tu teléfono.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        NavRow(Icons.Filled.EditNote, "Anota cómo te sientes", { nav.navigate(Routes.symptomEdit()) }, subtitle = "Síntomas, con intensidad y zona del cuerpo")
                        NavRow(Icons.Filled.MonitorWeight, "Registra tu peso", { nav.navigate(Routes.measure("weight", add = true)) }, subtitle = "Con gráficas y tendencias")
                        NavRow(Icons.Filled.Accessibility, "Explora el cuerpo humano", { nav.navigateTab(Routes.BODY) }, subtitle = "Nombres de zonas, huesos y órganos")
                        NavRow(Icons.Filled.AccountCircle, "Completa tu ficha", { nav.navigate(Routes.PROFILE) }, subtitle = "Tipo de sangre, alergias, contacto de emergencia")
                        if (allProfiles.size <= 1) {
                            NavRow(Icons.Filled.Groups, "Agrega a tu familia y mascotas", { nav.navigate(Routes.PROFILES) }, subtitle = "Cada quien con su propio historial")
                        }
                    }
                }
            }
        }
    }
}

/** Peso para tarjetas: en mascotas pequeñas se muestra en gramos. */
internal fun weightText(kg: Double, isPet: Boolean): String = MeasureRefs.weightText(kg, isPet)

/** Lo pendiente HOY de otro perfil, para avisarlo en la pantalla de inicio del perfil activo. */
internal data class OtherProfileStatus(val info: ProfileInfo, val notes: List<String>, val urgent: Boolean)

/** Revisa los demás perfiles: tomas pendientes de hoy, citas en las próximas 48 h y vacunas vencidas o cercanas. */
internal suspend fun othersToday(others: List<ProfileInfo>, today: Long): List<OtherProfileStatus> {
    val now = nowMillis()
    val out = ArrayList<OtherProfileStatus>()
    for (info in others) {
        val db = Savia.dbFor(info.id)
        val notes = ArrayList<String>()
        var urgent = false
        val pending = dosesForDay(db.medications().getAll(), db.medications().getIntakesOfDay(today), today).count { it.intake == null }
        if (pending > 0) { notes += if (pending == 1) "1 toma pendiente" else "$pending tomas pendientes"; urgent = true }
        db.appointments().getAll().filter { !it.done && it.timestamp >= now - 3_600_000L && it.timestamp <= now + 2 * 86_400_000L }.minByOrNull { it.timestamp }?.let {
            notes += "Cita ${Fmt.relativeDay(it.timestamp.millisToEpochDay()).lowercase()} ${Fmt.time(it.timestamp)}"
        }
        VaccineLogic.upcoming(db.vaccinations().getAll(), today).firstOrNull { it.dueDay <= today + 7 }?.let {
            notes += if (it.overdue) "${it.vaccination.name}: vencida" else "${it.vaccination.name} ${Fmt.daysFromNow(it.dueDay)}"
            if (it.overdue) urgent = true
        }
        if (notes.isNotEmpty()) out += OtherProfileStatus(info, notes, urgent)
    }
    return out
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(18.dp), color = tint.copy(alpha = 0.13f)) {
        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun IconBadge(icon: ImageVector, tint: Color) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint)
    }
}

@Composable
private fun MoodCard(log: DailyLog?, onPick: (Int) -> Unit, onOpen: () -> Unit) {
    val mood = log?.mood
    SectionCard(
        title = if (mood == null) "¿Cómo te sientes hoy?" else "Hoy te sientes: ${moodLabels[(mood - 1).coerceIn(0, 4)].lowercase()}",
        action = { TextButton(onClick = onOpen) { Text(if (mood == null) "Más" else "Detalles") } },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val colors = SaviaTheme.colors
            val tints = listOf(colors.bad, colors.warn, MaterialTheme.colorScheme.outline, colors.good, colors.good)
            for (i in 1..5) {
                val sel = mood == i
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(if (sel) tints[i - 1].copy(alpha = 0.2f) else Color.Transparent).clickable { onPick(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(moodIcons[i - 1], contentDescription = moodLabels[i - 1], tint = if (sel || mood == null) tints[i - 1] else MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(if (sel) 40.dp else 34.dp))
                }
            }
        }
    }
}

@Composable
fun MoreScreen(nav: NavController) {
    val cycleEnabled by Savia.prefs.cycleEnabled.flow.collectAsStateWithLifecycle()
    val profile by Savia.db.profile().observe().collectAsStateWithLifecycle(initialValue = null)
    val active by Savia.active.collectAsStateWithLifecycle()
    val isPet = active.isPet
    val count by Savia.profiles.ids.collectAsStateWithLifecycle()
    SaviaScaffold("Más") { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Perfil activo
            SectionCard(onClick = { nav.navigate(Routes.PROFILES) }, contentPadding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(active, size = 48.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(active.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (count.size > 1) "Viendo este perfil · toca para cambiar (${count.size} perfiles)" else "Agrega a tu familia y a tus mascotas",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Filled.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            SectionCard(title = "Registro y seguimiento", contentPadding = 12.dp) {
                if (!isPet) {
                    if (cycleEnabled) {
                        NavRow(Icons.Filled.MonitorWeight, "Mediciones", { nav.navigate(Routes.MEASURES) }, subtitle = "Peso, estatura, presión, glucosa…", iconTint = SaviaTheme.colors.chart[2])
                    } else {
                        NavRow(Icons.Filled.WaterDrop, "Ciclo menstrual", { nav.navigate(Routes.CYCLE_SETTINGS) }, subtitle = "Activa el seguimiento del periodo", iconTint = SaviaTheme.colors.period)
                    }
                }
                NavRow(Icons.Filled.Vaccines, if (isPet) "Vacunas y desparasitación" else "Vacunas", { nav.navigate(Routes.VACCINES) }, subtitle = if (isPet) "Cartilla, próximas aplicaciones y recordatorios" else "Cartilla de vacunación y próximos refuerzos", iconTint = SaviaTheme.colors.good)
                if (!isPet) NavRow(Icons.Filled.Mood, "Bienestar diario", { nav.navigate(Routes.WELLBEING) }, subtitle = "Ánimo, energía, sueño, agua y ejercicio", iconTint = SaviaTheme.colors.warn)
                NavRow(Icons.Filled.Medication, "Medicamentos", { nav.navigate(Routes.MEDS) }, subtitle = "Tomas del día y recordatorios", iconTint = SaviaTheme.colors.chart[0])
                NavRow(Icons.Filled.Event, if (isPet) "Citas con el veterinario" else "Citas médicas", { nav.navigate(Routes.APPOINTMENTS) }, subtitle = "Próximas consultas y recordatorios", iconTint = MaterialTheme.colorScheme.tertiary)
                NavRow(Icons.Filled.Science, "Estudios y chequeos", { nav.navigate(Routes.STUDIES) }, subtitle = "Resultados de laboratorio y su evolución", iconTint = SaviaTheme.colors.chart[4])
                if (!isPet) NavRow(Icons.AutoMirrored.Filled.FactCheck, "Autoexploración", { nav.navigate(Routes.SELF_EXAM) }, subtitle = "Mamaria, testicular y de piel", iconTint = SaviaTheme.colors.ovulation)
            }
            SectionCard(title = if (isPet) "Información de ${active.displayName}" else "Tu información", contentPadding = 12.dp) {
                NavRow(if (isPet) Icons.Filled.Pets else Icons.Filled.AccountCircle, if (isPet) "Ficha" else "Mi ficha", { nav.navigate(Routes.PROFILE) }, subtitle = profileSubtitle(profile))
                NavRow(Icons.Filled.Groups, "Perfiles", { nav.navigate(Routes.PROFILES) }, subtitle = "Familia y mascotas, cada quien con su historial")
                NavRow(Icons.Filled.FileDownload, "Exportar y respaldo", { nav.navigate(Routes.EXPORT) }, subtitle = "Excel, PDF, CSV y copia de seguridad")
                NavRow(Icons.Filled.Settings, "Ajustes", { nav.navigate(Routes.SETTINGS) }, subtitle = "Tema, módulos, bloqueo y notificaciones")
            }
            Text(
                "Savia no usa internet: tus datos viven solo en este teléfono. Haz un respaldo de vez en cuando.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

private fun profileSubtitle(p: Profile?): String {
    if (p == null) return "Datos personales y de emergencia"
    if (p.isPet) {
        val pet = listOfNotNull(PetSpecies.label(p.species), p.breed.takeIf { it.isNotBlank() }, p.birthEpochDay?.let { "${Fmt.age(it)} años" })
        return pet.joinToString(" · ")
    }
    val parts = listOfNotNull(
        p.name.takeIf { it.isNotBlank() },
        p.birthEpochDay?.let { "${Fmt.age(it)} años" },
        p.bloodType.takeIf { it.isNotBlank() }?.let { "Sangre $it" },
    )
    return if (parts.isEmpty()) "Datos personales y de emergencia" else parts.joinToString(" · ")
}
