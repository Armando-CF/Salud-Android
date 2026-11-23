package app.savia.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.savia.util.Fmt
import app.savia.util.epochDayToMillis
import app.savia.util.formatHm
import app.savia.util.millisToDateTime
import app.savia.util.millisToEpochDay

private const val DAY_MS = 86_400_000L

/** Campo de texto estándar. */
@Composable
fun SaviaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    suffix: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else minLines,
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
        isError = isError,
        suffix = suffix?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            capitalization = capitalization,
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
        ),
    )
}

/**
 * Campo numérico (texto controlado por quien llama; convertir con `String.toDoubleLenient()`).
 * Filtra caracteres no numéricos.
 */
@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    decimal: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            val cleaned = raw.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }.take(8)
            onValueChange(cleaned)
        },
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        isError = isError,
        suffix = suffix?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = ImeAction.Next,
        ),
    )
}

/** Campo de solo lectura que abre algo al tocarlo. */
@Composable
private fun PickerField(
    label: String,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    LaunchedEffect(src) {
        src.interactions.collect { if (it is PressInteraction.Release) onClick() }
    }
    OutlinedTextField(
        value = text,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        interactionSource = src,
        trailingIcon = trailing,
    )
}

/** Selector de fecha (epochDay). [maxDay] limita el máximo elegible (p. ej. hoy). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    epochDay: Long,
    onChange: (Long) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    var show by remember { mutableStateOf(false) }
    PickerField(label, Fmt.date(epochDay), { show = true }, modifier) {
        Icon(Icons.Filled.Event, contentDescription = null)
    }
    if (show) {
        SaviaDatePickerDialog(initial = epochDay, onPick = { onChange(it); show = false }, onDismiss = { show = false })
    }
}

/** Selector de fecha opcional (se puede borrar). */
@Composable
fun OptionalDateField(
    label: String,
    epochDay: Long?,
    onChange: (Long?) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    placeholder: String = "Sin fecha",
) {
    var show by remember { mutableStateOf(false) }
    PickerField(label, epochDay?.let { Fmt.date(it) } ?: placeholder, { show = true }, modifier) {
        if (epochDay != null) {
            IconButton(onClick = { onChange(null) }) { Icon(Icons.Filled.Clear, contentDescription = "Quitar fecha") }
        } else {
            Icon(Icons.Filled.Event, contentDescription = null)
        }
    }
    if (show) {
        SaviaDatePickerDialog(
            initial = epochDay ?: app.savia.util.today(),
            onPick = { onChange(it); show = false },
            onDismiss = { show = false },
        )
    }
}

/** Diálogo de calendario. Devuelve epochDay. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaviaDatePickerDialog(initial: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial * DAY_MS)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPick(Math.floorDiv(it, DAY_MS)) } ?: onDismiss() }) { Text("Aceptar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    ) {
        DatePicker(state = state, showModeToggle = true)
    }
}

/** Selector de hora en minutos del día (0..1439). */
@Composable
fun TimeField(
    label: String,
    minutesOfDay: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    var show by remember { mutableStateOf(false) }
    PickerField(label, formatHm(minutesOfDay), { show = true }, modifier) {
        Icon(Icons.Filled.Schedule, contentDescription = null)
    }
    if (show) {
        SaviaTimePickerDialog(initial = minutesOfDay, onPick = { onChange(it); show = false }, onDismiss = { show = false })
    }
}

/** Diálogo de reloj. Devuelve minutos del día. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaviaTimePickerDialog(initial: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = (initial / 60).coerceIn(0, 23), initialMinute = (initial % 60).coerceIn(0, 59), is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("Aceptar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        text = { TimePicker(state = state) },
    )
}

/** Fecha + hora en una fila (valor en milisegundos). */
@Composable
fun DateTimeField(
    millis: Long,
    onChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    dateLabel: String = "Fecha",
    timeLabel: String = "Hora",
) {
    val day = millis.millisToEpochDay()
    val dt = millis.millisToDateTime()
    val minutes = dt.hour * 60 + dt.minute
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        DateField(dateLabel, day, { onChange(epochDayToMillis(it, minutes)) }, Modifier.weight(1.4f))
        androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
        TimeField(timeLabel, minutes, { onChange(epochDayToMillis(day, it)) }, Modifier.weight(1f))
    }
}

/** Deslizador de 1 a [max] con etiqueta y valor a la derecha. */
@Composable
fun LevelSlider(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    max: Int = 10,
    valueText: String = "$value / $max",
    color: Color = MaterialTheme.colorScheme.primary,
) {
    androidx.compose.foundation.layout.Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(kotlin.math.round(it).toInt().coerceIn(1, max)) },
            valueRange = 1f..max.toFloat(),
            steps = max - 2,
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color),
        )
    }
}
