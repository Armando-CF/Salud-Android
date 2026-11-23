package app.savia.ui.measure

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import app.savia.ui.components.SaviaScaffold
import app.savia.ui.nav.back

@Composable
fun MeasurementsScreen(nav: NavController, onBack: (() -> Unit)?) { SaviaScaffold("Mediciones", onBack) { Text("Pendiente") } }

@Composable
fun MeasurementDetailScreen(nav: NavController, typeKey: String, openAdd: Boolean) { SaviaScaffold("Medición", { nav.back() }) { Text("Pendiente") } }
