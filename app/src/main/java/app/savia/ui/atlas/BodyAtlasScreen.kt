package app.savia.ui.atlas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.savia.Savia
import app.savia.data.SymptomEntry
import app.savia.ui.components.CenterBox
import app.savia.ui.components.EmptyState
import app.savia.ui.components.InfoNote
import app.savia.ui.components.SaviaScaffold
import app.savia.ui.nav.Routes
import app.savia.ui.nav.back
import app.savia.ui.theme.SaviaTheme
import app.savia.util.Fmt
import app.savia.util.millisToEpochDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Atlas corporal interactivo. En [pickMode] sirve para elegir la zona de un síntoma:
 * deja el resultado "id|Nombre" en el savedStateHandle de la pantalla anterior.
 */
@Composable
fun BodyAtlasScreen(
    nav: NavController,
    pickMode: Boolean,
    onBack: (() -> Unit)?,
    initialGroup: String = "front",
    initialSelection: String? = null,
) {
    val context = LocalContext.current
    val atlas by produceState<Atlas?>(null) { value = withContext(Dispatchers.IO) { AtlasRepo.load(context) } }
    val sex by Savia.prefs.atlasSex.flow.collectAsStateWithLifecycle()
    val profile by Savia.active.collectAsStateWithLifecycle()
    val isPet = profile.isPet
    val species = if (isPet) profile.species else null
    var requestedGroup by rememberSaveable { mutableStateOf(initialGroup) }
    var selectedId by rememberSaveable { mutableStateOf(initialSelection) }
    val symptoms by Savia.db.symptoms().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())

    SaviaScaffold(
        title = if (pickMode) "Elige la zona" else "Cuerpo",
        subtitle = when {
            !pickMode -> "Toca una zona para saber cómo se llama"
            isPet -> "Toca la zona donde notas la molestia"
            else -> "Toca dónde sientes la molestia"
        },
        onBack = onBack,
    ) { pad ->
        val a = atlas
        if (a == null) {
            CenterBox(Modifier.padding(pad)) { Text("Cargando…") }
            return@SaviaScaffold
        }
        // Personas: figura humana. Mascotas: solo las figuras de su especie (perro o gato).
        val groups = a.groupsFor(isPet, profile.species)
        val group = if (groups.any { it.first == requestedGroup }) requestedGroup else groups.firstOrNull()?.first
        val view = group?.let { a.view(it, sex, species) }
        if (group == null || view == null) {
            Column(Modifier.padding(pad).fillMaxSize(), verticalArrangement = Arrangement.Center) {
                EmptyState(
                    icon = Icons.Filled.Pets,
                    title = "Aún no tenemos una figura para esta especie",
                    message = "Por ahora hay figuras de perro y de gato. Puedes registrar síntomas sin indicar la zona.",
                    actionLabel = if (pickMode) "Regresar" else "Registrar un síntoma",
                    onAction = { if (pickMode) nav.back() else nav.navigate(Routes.symptomEdit()) },
                )
            }
            return@SaviaScaffold
        }
        val selected = view.shapes.firstOrNull { it.id == selectedId }
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (groups.size > 1) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for ((g, label) in groups) {
                        FilterChip(selected = g == group, onClick = { requestedGroup = g; selectedId = null }, label = { Text(label) })
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AtlasCanvas(
                    view = view,
                    selectedId = selectedId,
                    symptomCounts = remember(symptoms) { recentCounts(symptoms) },
                    onSelect = { selectedId = it },
                    modifier = Modifier.fillMaxSize(),
                )
                if (a.hasSexVariants(group, species)) {
                    SingleChoiceSegmentedButtonRow(Modifier.align(Alignment.TopEnd).padding(end = 12.dp, top = 4.dp).width(132.dp)) {
                        listOf("F" to "Mujer", "M" to "Hombre").forEachIndexed { i, (k, label) ->
                            SegmentedButton(
                                selected = sex == k,
                                onClick = { Savia.prefs.atlasSex.value = k; selectedId = null },
                                shape = SegmentedButtonDefaults.itemShape(i, 2),
                                icon = {},
                                label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false) },
                            )
                        }
                    }
                }
            }
            InfoPanel(
                atlas = a,
                group = group,
                selected = selected,
                symptoms = symptoms,
                pickMode = pickMode,
                isPet = isPet,
                groups = groups,
                onClear = { selectedId = null },
                onOpenGroup = { requestedGroup = it; selectedId = null },
                onLog = { id, name -> nav.navigate(Routes.symptomEdit(regionId = id, regionLabel = name)) },
                onPick = { id, name ->
                    nav.previousBackStackEntry?.savedStateHandle?.set(Routes.RESULT_REGION, "$id|$name")
                    nav.back()
                },
            )
        }
    }
}

/** Síntomas por zona en los últimos 30 días. */
private fun recentCounts(symptoms: List<SymptomEntry>): Map<String, Int> {
    val from = app.savia.util.today() - 30
    return symptoms.filter { it.regionId != null && it.timestamp.millisToEpochDay() >= from }.groupingBy { it.regionId!! }.eachCount()
}

@Composable
private fun AtlasCanvas(
    view: AtlasView,
    selectedId: String?,
    symptomCounts: Map<String, Int>,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaviaTheme.colors
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val bg = MaterialTheme.colorScheme.background
    val measurer = rememberTextMeasurer()
    var zoom by remember(view.id) { mutableFloatStateOf(1f) }
    var pan by remember(view.id) { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(Offset.Zero) }

    fun fit(w: Float, h: Float): Triple<Float, Float, Float> {
        val padPx = 8f
        val s0 = minOf((w - 2 * padPx) / view.box.width, (h - 2 * padPx) / view.box.height)
        return Triple(s0, (w - view.box.width * s0) / 2f, (h - view.box.height * s0) / 2f)
    }

    fun clampPan(p: Offset, z: Float): Offset {
        val w = canvasSize.x; val h = canvasSize.y
        return Offset(p.x.coerceIn(w - w * z, 0f), p.y.coerceIn(h - h * z, 0f))
    }

    Box(modifier.clipToBounds()) {
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(view.id) {
                    detectTransformGestures { centroid, panChange, zoomChange, _ ->
                        val newZoom = (zoom * zoomChange).coerceIn(1f, 6f)
                        val newPan = centroid - (centroid - pan) * (newZoom / zoom) + panChange
                        zoom = newZoom
                        pan = clampPan(newPan, newZoom)
                    }
                }
                .pointerInput(view.id) {
                    detectTapGestures { pos ->
                        val (s0, mx, my) = fit(size.width.toFloat(), size.height.toFloat())
                        val ax = ((pos.x - pan.x) / zoom - mx) / s0 + view.box.left
                        val ay = ((pos.y - pan.y) / zoom - my) / s0 + view.box.top
                        val tolerance = 14.dp.toPx() / (s0 * zoom)
                        onSelect(view.hit(ax, ay, tolerance)?.id)
                    }
                },
        ) {
            canvasSize = Offset(size.width, size.height)
            val (s0, mx, my) = fit(size.width, size.height)
            val k = s0 * zoom
            val hair = 1.dp.toPx() / k
            withTransform({
                translate(pan.x, pan.y)
                scale(zoom, zoom, pivot = Offset.Zero)
                translate(mx, my)
                scale(s0, s0, pivot = Offset.Zero)
                translate(-view.box.left, -view.box.top)
            }) {
                for (s in view.shapes) {
                    val isSel = s.id != null && s.id == selectedId
                    when (s.kind) {
                        ShapeKind.SKIN -> drawPath(s.path, colors.bodyFill.copy(alpha = s.alpha))
                        ShapeKind.REGION -> {
                            val n = symptomCounts[s.id] ?: 0
                            val base = if (n > 0) lerp(colors.bodyFill, colors.bad, (0.28f + 0.12f * n).coerceAtMost(0.7f)) else colors.bodyFill
                            drawPath(s.path, if (isSel) lerp(base, primary, 0.72f) else base)
                            drawPath(s.path, if (isSel) primary else colors.bodyStroke, style = Stroke(if (isSel) hair * 2.2f else hair, join = StrokeJoin.Round))
                        }
                        ShapeKind.BONE -> {
                            drawPath(s.path, if (isSel) lerp(colors.bone, primary, 0.65f) else colors.bone)
                            drawPath(s.path, if (isSel) primary else lerp(colors.bone, Color.Black, 0.38f), style = Stroke(if (isSel) hair * 2f else hair * 0.9f, join = StrokeJoin.Round))
                        }
                        ShapeKind.ORGAN -> {
                            val c = Color(s.color ?: 0xFFCCCCCC)
                            val fill = if (colors.isDark) lerp(c, Color.Black, 0.18f) else c
                            drawPath(s.path, fill.copy(alpha = s.alpha))
                            drawPath(
                                s.path,
                                if (isSel) onSurface else lerp(c, Color.Black, 0.45f),
                                style = Stroke(
                                    if (isSel) hair * 2.6f else hair * 0.9f, join = StrokeJoin.Round,
                                    pathEffect = if (s.dashed && !isSel) PathEffect.dashPathEffect(floatArrayOf(hair * 4, hair * 3)) else null,
                                ),
                            )
                        }
                        ShapeKind.DECO -> {
                            if (s.inkFill) drawPath(s.path, colors.bodyStroke)
                            else drawPath(
                                s.path, colors.bodyStroke,
                                style = Stroke(hair, join = StrokeJoin.Round, pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(hair * 4, hair * 4)) else null),
                            )
                        }
                    }
                }
            }
            // Etiquetas (en píxeles de pantalla para que el texto se vea nítido)
            for (t in view.texts) {
                val px = t.size * k
                if (px < 7f) continue
                val layout = measurer.measure(t.text, TextStyle(fontSize = (px / density / fontScale).sp, color = onSurface, fontWeight = FontWeight.Medium))
                val sx = pan.x + (mx + (t.x - view.box.left) * s0) * zoom
                val sy = pan.y + (my + (t.y - view.box.top) * s0) * zoom
                drawText(layout, topLeft = Offset(sx - layout.size.width / 2f, sy - layout.size.height * 0.75f))
            }
            // Marcas de lado
            if (view.group in setOf("front", "back", "organs", "skeleton", "abdomen")) {
                val leftLabel = if (view.group == "back") "Izq." else "Der."
                val rightLabel = if (view.group == "back") "Der." else "Izq."
                val style = TextStyle(fontSize = 11.sp, color = labelColor)
                val l = measurer.measure(leftLabel, style)
                val r = measurer.measure(rightLabel, style)
                drawText(l, topLeft = Offset(12.dp.toPx(), size.height - l.size.height - 6.dp.toPx()))
                drawText(r, topLeft = Offset(size.width - r.size.width - 12.dp.toPx(), size.height - r.size.height - 6.dp.toPx()))
            }
        }
        if (zoom > 1.01f) {
            FilledTonalIconButton(onClick = { zoom = 1f; pan = Offset.Zero }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)) {
                Icon(Icons.Filled.ZoomOutMap, contentDescription = "Restablecer zoom")
            }
        }
        // degradado superior sutil para que los chips no "floten" sobre la figura
        Box(Modifier.fillMaxWidth().height(4.dp).background(bg))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoPanel(
    atlas: Atlas,
    group: String,
    selected: AtlasShape?,
    symptoms: List<SymptomEntry>,
    pickMode: Boolean,
    isPet: Boolean,
    groups: List<Pair<String, String>>,
    onClear: () -> Unit,
    onOpenGroup: (String) -> Unit,
    onLog: (String, String) -> Unit,
    onPick: (String, String) -> Unit,
) {
    val info = selected?.id?.let { atlas.info[it] }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        tonalElevation = 2.dp,
    ) {
        if (info == null || selected?.id == null) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.TouchApp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        when {
                            !pickMode -> "Toca cualquier parte para ver su nombre y qué hay ahí"
                            isPet -> "Toca la zona donde notas la molestia"
                            else -> "Toca la zona donde sientes la molestia"
                        },
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                    )
                    Text(
                        atlas.orientationNote(group) + " Pellizca para acercar.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@Surface
        }
        val id = selected.id
        val here = remember(symptoms, id) { symptoms.filter { it.regionId == id } }
        Column(Modifier.heightIn(max = 330.dp).verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 10.dp, top = 8.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(info.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onClear) { Icon(Icons.Filled.Close, contentDescription = "Cerrar") }
            }
            Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (info.description.isNotEmpty()) Text(info.description, style = MaterialTheme.typography.bodyMedium)
                ListLine("Órganos y estructuras", info.organs)
                ListLine("Huesos", info.bones)
                ListLine("Músculos", info.muscles)
                if (here.isNotEmpty()) {
                    val last = here.maxBy { it.timestamp }
                    Text(
                        "Has registrado ${here.size} ${if (here.size == 1) "síntoma" else "síntomas"} aquí · último: ${last.name}, ${Fmt.relativeDay(last.timestamp.millisToEpochDay()).lowercase()}",
                        style = MaterialTheme.typography.bodySmall, color = SaviaTheme.colors.bad, fontWeight = FontWeight.Medium,
                    )
                }
                if (info.tip != null) InfoNote((if (isPet) "Cuándo ir al veterinario: " else "Cuándo consultar: ") + info.tip)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (pickMode) {
                        Button(onClick = { onPick(id, info.name) }) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Usar esta zona")
                        }
                    } else {
                        Button(onClick = { onLog(id, info.name) }) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Registrar síntoma aquí")
                        }
                    }
                    val link = info.link
                    if (link != null && link != group && groups.any { it.first == link }) {
                        OutlinedButton(onClick = { onOpenGroup(link) }) {
                            Text("Ver " + (atlas.groupLabel(link) ?: "detalle").lowercase())
                        }
                    }
                }
                Text(
                    if (isPet) "Información educativa; no sustituye una revisión veterinaria." else "Información educativa; no sustituye una valoración médica.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ListLine(label: String, items: List<String>) {
    if (items.isEmpty()) return
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        Text(items.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
