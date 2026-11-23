package app.savia.ui.cycle

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import app.savia.ui.components.SaviaScaffold
import app.savia.ui.nav.back

@Composable
fun CycleScreen(nav: NavController, onBack: (() -> Unit)?) { SaviaScaffold("Ciclo", onBack) { Text("Pendiente") } }

@Composable
fun CycleDayScreen(nav: NavController, epochDay: Long) { SaviaScaffold("Día", { nav.back() }) { Text("Pendiente") } }

@Composable
fun CycleStatsScreen(nav: NavController) { SaviaScaffold("Estadísticas", { nav.back() }) { Text("Pendiente") } }

@Composable
fun CycleSettingsScreen(nav: NavController) { SaviaScaffold("Ajustes del ciclo", { nav.back() }) { Text("Pendiente") } }
