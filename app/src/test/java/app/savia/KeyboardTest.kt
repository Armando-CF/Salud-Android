package app.savia

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.savia.ui.components.DismissKeyboardOnTapOutside
import app.savia.ui.components.SaviaTextField
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** El teclado se cierra (el campo pierde el foco) al tocar algo que no es un campo de texto. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class KeyboardTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun tappingOutsideATextFieldClearsFocusButOtherFieldsAndButtonsStillWork() {
        var chipClicks = 0
        compose.setContent {
            DismissKeyboardOnTapOutside {
                Column {
                    var a by remember { mutableStateOf("") }
                    var b by remember { mutableStateOf("") }
                    SaviaTextField(a, { a = it }, "Nombre", Modifier.testTag("a"))
                    OutlinedTextField(b, { b = it }, modifier = Modifier.testTag("b"))
                    FilterChip(selected = false, onClick = { chipClicks++ }, label = { Text("Mujer") })
                    Text("Texto suelto")
                }
            }
        }
        compose.onNodeWithTag("a").performClick()
        compose.onNodeWithTag("a").assertIsFocused()
        // Tocar otro campo pasa el foco a ese campo (el teclado sigue abierto).
        compose.onNodeWithTag("b").performClick()
        compose.onNodeWithTag("b").assertIsFocused()
        // Tocar un chip lo activa Y cierra el teclado.
        compose.onNodeWithText("Mujer").performClick()
        compose.onNodeWithTag("b").assertIsNotFocused()
        assertEquals(1, chipClicks)
        // Tocar un espacio vacío / texto también lo cierra.
        compose.onNodeWithTag("a").performClick()
        compose.onNodeWithTag("a").assertIsFocused()
        compose.onNodeWithText("Texto suelto").performClick()
        compose.onNodeWithTag("a").assertIsNotFocused()
    }
}
