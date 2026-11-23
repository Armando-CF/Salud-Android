package app.savia.ui.atlas

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import app.savia.ui.components.SaviaScaffold
import app.savia.ui.nav.back

@Composable
fun BodyAtlasScreen(nav: NavController, pickMode: Boolean, onBack: (() -> Unit)?) { SaviaScaffold("Cuerpo", onBack) { Text("Pendiente") } }
