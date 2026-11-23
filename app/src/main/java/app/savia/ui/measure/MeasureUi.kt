package app.savia.ui.measure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.savia.Savia
import app.savia.data.MeasureType
import app.savia.data.Measurement
import app.savia.data.Profile
import app.savia.ui.components.ChoiceChips
import app.savia.ui.components.DateTimeField
import app.savia.ui.components.NumberField
import app.savia.ui.components.SaviaTextField
import app.savia.ui.theme.SaviaTheme
import app.savia.util.Fmt
import app.savia.util.nowMillis
import app.savia.util.toDoubleLenient

internal fun MeasureType.icon(): ImageVector = when (this) {
    MeasureType.WEIGHT -> Icons.Filled.MonitorWeight
    MeasureType.BLOOD_PRESSURE -> Icons.Filled.Favorite
    MeasureType.HEART_RATE -> Icons.Filled.MonitorHeart
    MeasureType.GLUCOSE -> Icons.Filled.Bloodtype
    MeasureType.TEMPERATURE -> Icons.Filled.Thermostat
    MeasureType.SPO2 -> Icons.Filled.Air
    MeasureType.WAIST, MeasureType.HIP -> Icons.Filled.Straighten
    MeasureType.HEIGHT -> Icons.Filled.Height
    MeasureType.BODY_FAT -> Icons.Filled.Percent
}

/** "72.5" o "120/80" (sin unidad). */
internal fun MeasureType.valueText(m: Measurement): String {
    val v2 = m.value2
    return if (secondLabel != null && v2 != null) "${Fmt.num(m.value, decimals)}/${Fmt.num(v2, decimals)}" else Fmt.num(m.value, decimals)
}

/** "72.5 kg" o "120/80 mmHg". */
internal fun MeasureType.valueWithUnit(m: Measurement): String = "${valueText(m)} $unit"

@Composable
internal fun Level.color(): Color = when (this) {
    Level.GOOD -> SaviaTheme.colors.good
    Level.WARN -> SaviaTheme.colors.warn
    Level.BAD -> SaviaTheme.colors.bad
    Level.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Guarda un cambio en el perfil aunque todavía no exista la fila. */
internal fun updateProfile(change: (Profile) -> Profile) {
    Savia.io {
        val dao = Savia.db.profile()
        dao.save(change(dao.get() ?: Profile()))
    }
}

/**
 * Formulario de alta / edición de una medición (contenido de la hoja inferior).
 * [initial] = null → nueva medición.
 */
@Composable
internal fun MeasureFormContent(
    type: MeasureType,
    initial: Measurement?,
    onSave: (Measurement) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
) {
    val key = initial?.id ?: 0L
    var v1 by rememberSaveable(key) { mutableStateOf(initial?.let { Fmt.num(it.value, type.decimals) } ?: "") }
    var v2 by rememberSaveable(key) { mutableStateOf(initial?.value2?.let { Fmt.num(it, type.decimals) } ?: "") }
    var ts by rememberSaveable(key) { mutableStateOf(initial?.timestamp ?: nowMillis()) }
    var tag by rememberSaveable(key) { mutableStateOf(initial?.tag ?: "") }
    var notes by rememberSaveable(key) { mutableStateOf(initial?.notes ?: "") }
    var error by remember(key) { mutableStateOf<String?>(null) }

    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (initial == null) "Registrar ${type.label.lowercase()}" else "Editar ${type.label.lowercase()}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        if (type.secondLabel != null) {
            Row(verticalAlignment = Alignment.Top) {
                NumberField(v1, { v1 = it; error = null }, type.valueLabel, Modifier.weight(1f), suffix = type.unit, decimal = type.decimals > 0)
                Spacer(Modifier.width(12.dp))
                NumberField(v2, { v2 = it; error = null }, type.secondLabel, Modifier.weight(1f), suffix = type.unit, decimal = type.decimals > 0)
            }
        } else {
            NumberField(v1, { v1 = it; error = null }, "${type.valueLabel} (${type.unit})", decimal = type.decimals > 0)
        }
        DateTimeField(ts, { ts = it; error = null })
        if (type.tags.isNotEmpty() || tag.isNotEmpty()) {
            val options = (type.tags + listOfNotNull(tag.takeIf { it.isNotEmpty() && it !in type.tags })).map { it to it }
            Column {
                Text("Contexto (opcional)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ChoiceChips(options, tag.takeIf { it.isNotEmpty() }, { tag = it ?: "" }, allowDeselect = true)
            }
        }
        SaviaTextField(notes, { notes = it.take(500) }, "Nota (opcional)", singleLine = false, minLines = 2)
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (initial != null && onDelete != null) {
                TextButton(onClick = onDelete) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onCancel) { Text("Cancelar") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val a = v1.toDoubleLenient()
                    val b = if (type.secondLabel != null) v2.toDoubleLenient() else null
                    val problem = MeasureStats.validate(type, a, b, ts, nowMillis())
                    if (problem != null || a == null) {
                        error = problem
                    } else {
                        onSave(
                            (initial ?: Measurement(timestamp = ts, type = type.key, value = a)).copy(
                                timestamp = ts, type = type.key, value = a, value2 = b, tag = tag, notes = notes.trim(),
                            ),
                        )
                    }
                },
            ) { Text("Guardar") }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/**
 * Diálogo para capturar un número (meta de peso, estatura).
 * @param onSave recibe el valor validado; [onClear] (opcional) muestra un botón para quitar el valor.
 */
@Composable
internal fun NumberDialog(
    title: String,
    label: String,
    unit: String,
    initial: Double?,
    min: Double,
    max: Double,
    onSave: (Double) -> Unit,
    onDismiss: () -> Unit,
    text: String? = null,
    clearLabel: String? = null,
    onClear: (() -> Unit)? = null,
) {
    var value by rememberSaveable { mutableStateOf(initial?.let { Fmt.num(it, 1) } ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (text != null) Text(text, style = MaterialTheme.typography.bodyMedium)
                NumberField(value, { value = it; error = null }, label, suffix = unit, isError = error != null, supportingText = error)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val v = value.toDoubleLenient()
                    if (v == null || v < min || v > max) {
                        error = "Escribe un valor entre ${Fmt.num(min, 0)} y ${Fmt.num(max, 0)} $unit."
                    } else {
                        onSave(v)
                    }
                },
            ) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (clearLabel != null && onClear != null && initial != null) {
                    TextButton(onClick = onClear) { Text(clearLabel, color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}
