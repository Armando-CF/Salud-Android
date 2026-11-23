package app.savia.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.savia.data.ProfileInfo
import app.savia.util.today
import java.time.LocalDate
import java.time.Period

/** Paleta de 8 colores para distinguir perfiles (índice = `Profile.avatarColor`). */
object ProfileColors {
    val all: List<Color> = listOf(
        Color(0xFF1B6B58), // verde Savia
        Color(0xFFB5446E), // rosa
        Color(0xFF2E7FA8), // azul
        Color(0xFFB8770A), // ámbar
        Color(0xFF7A55C7), // morado
        Color(0xFF5E7A2F), // olivo
        Color(0xFFC8553D), // coral
        Color(0xFF50656E), // pizarra
    )

    val names: List<String> = listOf("Verde", "Rosa", "Azul", "Ámbar", "Morado", "Olivo", "Coral", "Pizarra")

    /** Color del índice dado (cualquier entero; se ajusta al tamaño de la paleta). */
    fun of(index: Int): Color = all[Math.floorMod(index, all.size)]

    /** Color del contenido (inicial o icono) sobre cualquier color de la paleta. */
    val onColor: Color = Color.White
}

/**
 * Avatar de un perfil: círculo de color con la inicial del nombre (personas) o una huella (mascotas).
 */
@Composable
fun ProfileAvatar(info: ProfileInfo, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val initial = info.name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()
    Box(
        modifier.size(size).clip(CircleShape).background(ProfileColors.of(info.avatarColor)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            info.isPet -> Icon(Icons.Filled.Pets, contentDescription = null, tint = ProfileColors.onColor, modifier = Modifier.size(size * 0.56f))
            initial != null -> Text(
                initial.toString(),
                color = ProfileColors.onColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = with(LocalDensity.current) { (size * 0.46f).toSp() },
            )
            else -> Icon(Icons.Filled.Person, contentDescription = null, tint = ProfileColors.onColor, modifier = Modifier.size(size * 0.6f))
        }
    }
}

/** Selector del color del avatar. */
@Composable
internal fun AvatarColorPicker(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val sel = Math.floorMod(selected, ProfileColors.all.size)
    // Una sola fila que reparte el ancho: los 8 colores caben en cualquier pantalla.
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ProfileColors.all.forEachIndexed { i, c ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .widthIn(max = 40.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .then(if (i == sel) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape).padding(5.dp) else Modifier.padding(2.dp))
                        .clip(CircleShape)
                        .background(c)
                        .clickable(role = Role.RadioButton) { onSelect(i) }
                        .semantics { contentDescription = "Color " + ProfileColors.names[i].lowercase() + if (i == sel) ", elegido" else "" },
                    contentAlignment = Alignment.Center,
                ) {
                    if (i == sel) Icon(Icons.Filled.Check, contentDescription = null, tint = ProfileColors.onColor, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/**
 * Edad en palabras a partir de la fecha de nacimiento: "3 semanas", "8 meses", "2 años y 4 meses", "34 años".
 * @param detailed si es true siempre incluye los meses (útil para mascotas); si no, solo en menores de 3 años.
 */
internal fun ageText(birthEpochDay: Long, today: Long = today(), detailed: Boolean = false): String {
    if (birthEpochDay > today) return ""
    val p = Period.between(LocalDate.ofEpochDay(birthEpochDay), LocalDate.ofEpochDay(today))
    val y = p.years
    val m = p.months
    fun years(n: Int) = if (n == 1) "1 año" else "$n años"
    fun months(n: Int) = if (n == 1) "1 mes" else "$n meses"
    return when {
        y == 0 && m == 0 -> {
            val d = (today - birthEpochDay).toInt()
            when {
                d < 14 -> if (d == 1) "1 día" else "$d días"
                else -> "${d / 7} semanas"
            }
        }
        y == 0 -> months(m)
        m > 0 && (detailed || y < 3) -> "${years(y)} y ${months(m)}"
        else -> years(y)
    }
}

/** Fecha de nacimiento aproximada a partir de una edad en años y meses. */
internal fun birthFromAge(years: Int, months: Int, today: Long = today()): Long =
    LocalDate.ofEpochDay(today).minusYears(years.toLong()).minusMonths(months.toLong()).toEpochDay()

@Composable
internal fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * Campo de texto de formulario con control de la acción del teclado:
 * [last] = true → la tecla de acción es "Listo" y cierra el teclado (úsalo cuando lo que sigue no es
 * otro campo de texto); si no, "Siguiente" salta al campo siguiente. Los campos de varias líneas
 * conservan Intro como salto de línea.
 */
@Composable
internal fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    last: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    suffix: String? = null,
) {
    val focus = LocalFocusManager.current
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
            imeAction = when {
                !singleLine -> ImeAction.Default
                last -> ImeAction.Done
                else -> ImeAction.Next
            },
        ),
        keyboardActions = KeyboardActions(
            onDone = { focus.clearFocus() },
            onNext = { if (!focus.moveFocus(FocusDirection.Next)) focus.clearFocus() },
        ),
    )
}

/** Campo numérico de formulario (filtra lo que no sea número) con la misma lógica de teclado que [FormTextField]. */
@Composable
internal fun FormNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    last: Boolean = false,
    suffix: String? = null,
    decimal: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    maxLength: Int = 8,
) {
    FormTextField(
        value = value,
        onValueChange = { raw -> onValueChange(raw.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }.take(maxLength)) },
        label = label,
        modifier = modifier,
        last = last,
        suffix = suffix,
        isError = isError,
        supportingText = supportingText,
        keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        capitalization = KeyboardCapitalization.None,
    )
}

/**
 * Ruta pendiente tras cambiar de perfil. Al cambiar de perfil la navegación se recrea en Inicio; quien
 * quiera aterrizar en otra pantalla (p. ej. "Editar ficha" de otro perfil) deja aquí la ruta, y la
 * pantalla de inicio puede consumirla con [consume] y navegar. Si nadie la consume, no pasa nada.
 */
object ProfileSwitch {
    @Volatile
    var pendingRoute: String? = null

    /** Devuelve la ruta pendiente (si hay) y la olvida. */
    fun consume(): String? = pendingRoute.also { pendingRoute = null }
}
