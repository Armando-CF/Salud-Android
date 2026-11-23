package app.savia.ui.studies

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import app.savia.ui.components.SaviaScaffold
import app.savia.ui.nav.back

@Composable
fun StudiesScreen(nav: NavController) { SaviaScaffold("Estudios", { nav.back() }) { Text("Pendiente") } }

@Composable
fun StudyDetailScreen(nav: NavController, id: Long) { SaviaScaffold("Estudio", { nav.back() }) { Text("Pendiente") } }

@Composable
fun StudyEditScreen(nav: NavController, id: Long?) { SaviaScaffold("Estudio", { nav.back() }) { Text("Pendiente") } }

@Composable
fun ParamTrendScreen(nav: NavController, name: String) { SaviaScaffold(name, { nav.back() }) { Text("Pendiente") } }
