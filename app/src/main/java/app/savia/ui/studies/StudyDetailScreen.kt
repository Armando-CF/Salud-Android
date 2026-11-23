package app.savia.ui.studies

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.savia.Savia
import app.savia.data.Study
import app.savia.data.StudyCategories
import app.savia.data.StudyResult
import app.savia.ui.components.CenterBox
import app.savia.ui.components.ConfirmDialog
import app.savia.ui.components.InfoNote
import app.savia.ui.components.Pill
import app.savia.ui.components.SaviaScaffold
import app.savia.ui.components.SectionCard
import app.savia.ui.nav.Routes
import app.savia.ui.nav.back
import app.savia.ui.theme.SaviaTheme
import app.savia.util.Fmt
import app.savia.util.today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Envoltura para distinguir "aún cargando" de "no existe". */
private class Loaded(val study: Study?)

/** Detalle de un estudio: datos generales, resultados con su rango, adjuntos y seguimiento. */
@Composable
fun StudyDetailScreen(nav: NavController, id: Long) {
    val context = LocalContext.current
    val loaded by remember(id) { Savia.db.studies().observe(id).map { Loaded(it) } }.collectAsStateWithLifecycle(initialValue = null)
    val results by remember(id) { Savia.db.studies().observeResults(id) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val attachments by remember(id) { Savia.db.studies().observeAttachments(id) }.collectAsStateWithLifecycle(initialValue = emptyList())
    var confirmDelete by remember { mutableStateOf(false) }
    val study = loaded?.study

    SaviaScaffold(
        title = "Estudio",
        onBack = { nav.back() },
        actions = {
            if (study != null) {
                IconButton(onClick = { nav.navigate(Routes.studyEdit(id)) }) { Icon(Icons.Filled.Edit, contentDescription = "Editar") }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Eliminar") }
            }
        },
    ) { pad ->
        when {
            loaded == null -> Unit
            study == null -> CenterBox(Modifier.padding(pad)) {
                Text("Este estudio ya no existe.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> Column(
                Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HeaderCard(study)
                if (study.followUpEpochDay != null) FollowUpCard(study)
                if (study.summary.isNotBlank()) {
                    SectionCard(title = "Resumen y conclusiones") {
                        Text(study.summary, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (results.isNotEmpty()) ResultsCard(nav, results)
                if (attachments.isNotEmpty()) {
                    SectionCard(title = "Adjuntos") {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (a in attachments) {
                                val ref = AttachmentRef(a.id, a.fileName, a.displayName, a.mime)
                                AttachmentTile(ref, onClick = { StudyFiles.open(context, ref) })
                            }
                        }
                    }
                }
                if (results.isEmpty() && attachments.isEmpty() && study.summary.isBlank()) {
                    SectionCard {
                        Text(
                            "Este estudio aún no tiene resultados ni archivos. Puedes agregar los valores del reporte, una foto o el PDF.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = { nav.navigate(Routes.studyEdit(id)) }) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Agregar resultados")
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete && study != null) {
        ConfirmDialog(
            title = "Eliminar estudio",
            text = "Se eliminará \"${study.title}\" con sus resultados y archivos adjuntos. Esta acción no se puede deshacer.",
            onConfirm = {
                confirmDelete = false
                Savia.io {
                    val dao = Savia.db.studies()
                    dao.getAttachments(study.id).forEach { StudyFiles.delete(context, it.fileName) }
                    dao.delete(study)
                    withContext(Dispatchers.Main) { nav.back() }
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeaderCard(study: Study) {
    SectionCard {
        Row(verticalAlignment = Alignment.Top) {
            CategoryBadge(study.category)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(study.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "${Fmt.dateLong(study.epochDay)} · ${Fmt.daysFromNow(study.epochDay)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(StudyCategories.short(study.category), MaterialTheme.colorScheme.primary)
            StatusPill(study.status)
        }
        if (study.place.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            IconLine(Icons.Filled.Place, study.place)
        }
        if (study.doctor.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            IconLine(Icons.Filled.Person, study.doctor)
        }
    }
}

@Composable
private fun IconLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FollowUpCard(study: Study) {
    val day = study.followUpEpochDay ?: return
    val overdue = day < today()
    SectionCard(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f), contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.EventRepeat, contentDescription = null, tint = if (overdue) SaviaTheme.colors.warn else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Seguimiento", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${Fmt.date(day)} · ${Fmt.daysFromNow(day)}" + if (overdue) " (vencido)" else "",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (overdue) SaviaTheme.colors.warn else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ResultsCard(nav: NavController, results: List<StudyResult>) {
    val out = results.count { it.outOfRange }
    val withRange = results.count { it.flag() != RangeFlag.SIN_RANGO }
    SectionCard(
        title = "Resultados",
        action = {
            if (withRange > 0) {
                if (out == 0) Pill("Todo en rango", SaviaTheme.colors.good)
                else Pill(if (out == 1) "1 fuera de rango" else "$out fuera de rango", SaviaTheme.colors.warn)
            }
        },
    ) {
        results.forEachIndexed { i, r ->
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ResultRow(r, onClick = if (r.value != null) ({ nav.navigate(Routes.paramTrend(r.name)) }) else null)
        }
        Spacer(Modifier.height(10.dp))
        InfoNote(
            (if (results.any { it.value != null }) "Toca un resultado numérico para ver su evolución. " else "") +
                "Los rangos de referencia son orientativos; usa los de tu laboratorio y consulta cualquier duda con tu médico.",
        )
    }
}

@Composable
private fun ResultRow(r: StudyResult, onClick: (() -> Unit)?) {
    val f = r.flag()
    val range = rangeText(r.refMin, r.refMax)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp, horizontal = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (range.isNotEmpty()) {
                    Text(
                        "Referencia: $range" + (if (r.unit.isNotBlank()) " ${r.unit}" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    r.valueText(),
                    style = if (r.value != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (f == RangeFlag.ALTO || f == RangeFlag.BAJO) flagColor(f) else MaterialTheme.colorScheme.onSurface,
                )
                if (f != RangeFlag.SIN_RANGO) Pill(flagLabel(f), flagColor(f))
            }
            if (onClick != null) {
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = "Ver evolución", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
        val v = r.value
        if (v != null && f != RangeFlag.SIN_RANGO) {
            Spacer(Modifier.height(6.dp))
            RangeBar(v, r.refMin, r.refMax)
        }
        if (r.notes.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(r.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
