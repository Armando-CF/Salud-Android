package app.savia.ui.wellbeing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pets
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.savia.Savia
import app.savia.ui.components.EmptyState
import app.savia.ui.components.SaviaScaffold

/**
 * Pantalla que se muestra cuando se llega con un perfil de mascota a una sección pensada solo para
 * personas (bienestar diario, autoexploración, ciclo menstrual). Uso al inicio de la pantalla:
 *
 *     if (Savia.isPet) { PeopleOnlyScreen("Bienestar diario") { nav.back() }; return }
 *
 * El tipo de perfil no cambia mientras vive su interfaz, así que el `return` temprano es estable.
 */
@Composable
internal fun PeopleOnlyScreen(title: String, onBack: (() -> Unit)?) {
    val name = Savia.active.value.displayName
    SaviaScaffold(title = title, onBack = onBack) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            EmptyState(
                icon = Icons.Filled.Pets,
                title = "Esta sección es para perfiles de personas",
                message = "\"$title\" no aplica al perfil de $name. Para $name puedes usar síntomas, peso y mediciones, " +
                    "medicamentos, vacunas, estudios y citas.",
            )
        }
    }
}
