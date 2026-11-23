package app.savia.ui.studies

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType

/**
 * Campo de una línea para el ÚLTIMO dato de un formulario: la tecla del teclado es "Listo" y lo cierra,
 * en vez de "Siguiente" (que no tendría a dónde ir y dejaría el teclado tapando la pantalla).
 * Con [number] acepta solo dígitos y separador decimal, igual que NumberField.
 */
@Composable
internal fun LastField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    number: Boolean = false,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = { raw -> onValueChange(if (number) raw.filter { it.isDigit() || it == '.' || it == ',' }.take(8) else raw) },
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = placeholder?.let { { Text(it) } },
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text,
            capitalization = if (number) KeyboardCapitalization.None else capitalization,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
    )
}
