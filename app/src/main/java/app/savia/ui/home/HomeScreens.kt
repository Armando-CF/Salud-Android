package app.savia.ui.home

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import app.savia.ui.components.SaviaScaffold
import app.savia.ui.nav.back

@Composable
fun HomeScreen(nav: NavController) { SaviaScaffold("Hoy") { Text("Pendiente") } }

@Composable
fun MoreScreen(nav: NavController) { SaviaScaffold("Más") { Text("Pendiente") } }
