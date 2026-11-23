package app.savia.ui.vaccines

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import app.savia.ui.components.SaviaScaffold
import app.savia.ui.nav.back

@Composable
fun VaccinesScreen(nav: NavController) { SaviaScaffold("Vacunas", { nav.back() }) { Text("Pendiente") } }

@Composable
fun VaccineEditScreen(nav: NavController, id: Long?) { SaviaScaffold("Vacuna", { nav.back() }) { Text("Pendiente") } }
