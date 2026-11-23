package app.savia.ui.meds

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import app.savia.ui.components.SaviaScaffold
import app.savia.ui.nav.back

@Composable
fun MedsScreen(nav: NavController) { SaviaScaffold("Medicamentos", { nav.back() }) { Text("Pendiente") } }

@Composable
fun MedEditScreen(nav: NavController, id: Long?) { SaviaScaffold("Medicamento", { nav.back() }) { Text("Pendiente") } }

@Composable
fun AppointmentsScreen(nav: NavController) { SaviaScaffold("Citas", { nav.back() }) { Text("Pendiente") } }

@Composable
fun AppointmentEditScreen(nav: NavController, id: Long?) { SaviaScaffold("Cita", { nav.back() }) { Text("Pendiente") } }
