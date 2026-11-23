package app.savia.ui.wellbeing

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import app.savia.ui.components.SaviaScaffold
import app.savia.ui.nav.back

@Composable
fun WellbeingScreen(nav: NavController) { SaviaScaffold("Bienestar diario", { nav.back() }) { Text("Pendiente") } }

@Composable
fun SelfExamScreen(nav: NavController) { SaviaScaffold("Autoexploración", { nav.back() }) { Text("Pendiente") } }
