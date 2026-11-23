package app.savia.ui.profile

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import app.savia.ui.components.SaviaScaffold
import app.savia.ui.nav.back

@Composable
fun ProfileScreen(nav: NavController) { SaviaScaffold("Mi ficha", { nav.back() }) { Text("Pendiente") } }

@Composable
fun SettingsScreen(nav: NavController) { SaviaScaffold("Ajustes", { nav.back() }) { Text("Pendiente") } }
