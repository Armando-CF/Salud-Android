package app.savia.ui.export

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import app.savia.ui.components.SaviaScaffold
import app.savia.ui.nav.back

@Composable
fun ExportScreen(nav: NavController) { SaviaScaffold("Exportar y respaldo", { nav.back() }) { Text("Pendiente") } }
