package app.savia.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Oculta el teclado cuando se toca cualquier cosa que no sea un campo de texto (un chip, un botón,
 * un espacio vacío…). Así el teclado nunca se queda tapando el resto del formulario.
 *
 * Envuelve TODA la interfaz (ver MainActivity). Observa los toques sin consumirlos, de modo que
 * botones, listas y campos siguen funcionando igual. Deslizar para desplazarse no cierra el teclado.
 */
@Composable
fun DismissKeyboardOnTapOutside(content: @Composable () -> Unit) {
    val focus = LocalFocusManager.current
    val view = LocalView.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow() }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val up = waitForUpOrCancellation(pass = PointerEventPass.Initial) ?: return@awaitEachGesture
                    val p = up.position + origin
                    if (!isOnTextInput(view, p)) focus.clearFocus()
                }
            },
    ) { content() }
}

/**
 * Lo mismo que [DismissKeyboardOnTapOutside] pero como modificador, para el contenido de diálogos y
 * hojas inferiores (que viven en su propia ventana y no quedan cubiertos por el envoltorio global):
 * `Column(Modifier.dismissKeyboardOnTapOutside()) { … }`.
 */
fun Modifier.dismissKeyboardOnTapOutside(): Modifier = composed {
    val focus = LocalFocusManager.current
    val view = LocalView.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    this
        .onGloballyPositioned { origin = it.positionInWindow() }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val up = waitForUpOrCancellation(pass = PointerEventPass.Initial) ?: return@awaitEachGesture
                if (!isOnTextInput(view, up.position + origin)) focus.clearFocus()
            }
        }
}

/** ¿El punto (en coordenadas de ventana) cae sobre un campo de texto editable? */
private fun isOnTextInput(view: android.view.View, p: Offset): Boolean {
    val owner = (view as? RootForTest)?.semanticsOwner ?: return false
    return runCatching {
        owner.getAllSemanticsNodes(mergingEnabled = false).any { node ->
            node.config.contains(SemanticsActions.SetText) && node.boundsInWindow.contains(p)
        }
    }.getOrDefault(false)
}
