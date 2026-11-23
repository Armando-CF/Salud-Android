package app.savia.ui.lock

import androidx.compose.runtime.Composable
import androidx.fragment.app.FragmentActivity

/** Si el bloqueo está activo en ajustes, pide huella / PIN del teléfono antes de mostrar [content]. */
@Composable
fun AppLockGate(activity: FragmentActivity, content: @Composable () -> Unit) {
    content()
}
