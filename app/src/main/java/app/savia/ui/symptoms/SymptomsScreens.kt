package app.savia.ui.symptoms

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import app.savia.ui.components.SaviaScaffold
import app.savia.ui.nav.back

@Composable
fun SymptomsScreen(nav: NavController, onBack: (() -> Unit)?) { SaviaScaffold("Síntomas", onBack) { Text("Pendiente") } }

@Composable
fun SymptomEditScreen(nav: NavController, id: Long?, regionId: String?, regionLabel: String?, name: String?) { SaviaScaffold("Síntoma", { nav.back() }) { Text("Pendiente") } }
