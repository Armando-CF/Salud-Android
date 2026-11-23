package app.savia.ui.profile

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import app.savia.ui.components.SaviaScaffold
import app.savia.ui.nav.back

/** Lista de perfiles (personas y mascotas): cambiar de perfil, crear y eliminar. */
@Composable
fun ProfilesScreen(nav: NavController) { SaviaScaffold("Perfiles", { nav.back() }) { Text("Pendiente") } }

/** Alta de un perfil nuevo. [kind] = "person" o "pet". Al guardar crea el perfil y lo activa. */
@Composable
fun NewProfileScreen(nav: NavController, kind: String) { SaviaScaffold("Nuevo perfil", { nav.back() }) { Text("Pendiente") } }
