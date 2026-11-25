package app.savia.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.savia.Savia
import app.savia.ui.atlas.BodyAtlasScreen
import app.savia.ui.cycle.CycleDayScreen
import app.savia.ui.cycle.CycleScreen
import app.savia.ui.cycle.CycleSettingsScreen
import app.savia.ui.cycle.CycleStatsScreen
import app.savia.ui.energy.EnergyScreen
import app.savia.ui.export.ExportScreen
import app.savia.ui.home.HomeScreen
import app.savia.ui.home.MoreScreen
import app.savia.ui.measure.MeasurementDetailScreen
import app.savia.ui.measure.MeasurementsScreen
import app.savia.ui.meds.AppointmentEditScreen
import app.savia.ui.meds.AppointmentsScreen
import app.savia.ui.meds.MedEditScreen
import app.savia.ui.meds.MedsScreen
import app.savia.ui.profile.NewProfileScreen
import app.savia.ui.profile.ProfileScreen
import app.savia.ui.profile.ProfilesScreen
import app.savia.ui.profile.SettingsScreen
import app.savia.ui.studies.ParamTrendScreen
import app.savia.ui.studies.StudiesScreen
import app.savia.ui.studies.StudyDetailScreen
import app.savia.ui.studies.StudyEditScreen
import app.savia.ui.symptoms.SymptomEditScreen
import app.savia.ui.vaccines.VaccineEditScreen
import app.savia.ui.vaccines.VaccinesScreen
import app.savia.ui.symptoms.SymptomsScreen
import app.savia.ui.wellbeing.SelfExamScreen
import app.savia.ui.wellbeing.WellbeingScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

/** Especies de mascota que tienen figura en el atlas corporal. */
val ATLAS_PET_SPECIES = setOf("perro", "gato")

private fun tabs(cycleEnabled: Boolean, isPet: Boolean, species: String): List<Tab> = buildList {
    add(Tab(Routes.HOME, "Hoy", Icons.Filled.Today))
    add(Tab(Routes.SYMPTOMS, "Síntomas", Icons.Filled.EditNote))
    if (cycleEnabled && !isPet) add(Tab(Routes.CYCLE, "Ciclo", Icons.Filled.WaterDrop))
    else add(Tab(Routes.MEASURES, "Mediciones", Icons.Filled.MonitorWeight))
    if (!isPet || species in ATLAS_PET_SPECIES) add(Tab(Routes.BODY, "Cuerpo", if (isPet) Icons.Filled.Pets else Icons.Filled.Accessibility))
    add(Tab(Routes.MORE, "Más", Icons.Filled.Apps))
}

/** Navega a una pestaña de primer nivel conservando el estado de las demás. */
fun NavController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val cycleEnabled by Savia.prefs.cycleEnabled.flow.collectAsStateWithLifecycle()
    val active by Savia.active.collectAsStateWithLifecycle()
    val tabs = tabs(cycleEnabled, active.isPet, active.species)
    val tabRoutes = tabs.map { it.route }
    val entry by nav.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentRoute in tabRoutes) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    for (t in tabs) {
                        NavigationBarItem(
                            selected = currentRoute == t.route,
                            onClick = { if (currentRoute != t.route) nav.navigateTab(t.route) },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label, maxLines = 1) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            NavHost(navController = nav, startDestination = Routes.HOME) {
                /** onBack = null cuando la pantalla es una pestaña de la barra inferior. */
                fun backFor(route: String): (() -> Unit)? = if (route in tabRoutes) null else ({ nav.back() })

                composable(Routes.HOME) { HomeScreen(nav) }
                composable(Routes.MORE) { MoreScreen(nav) }
                composable(Routes.SYMPTOMS) { SymptomsScreen(nav, backFor(Routes.SYMPTOMS)) }
                composable(Routes.CYCLE) { CycleScreen(nav, backFor(Routes.CYCLE)) }
                composable(Routes.MEASURES) { MeasurementsScreen(nav, backFor(Routes.MEASURES)) }
                composable(Routes.BODY) { BodyAtlasScreen(nav, pickMode = false, onBack = backFor(Routes.BODY)) }
                composable("body_pick") { BodyAtlasScreen(nav, pickMode = true, onBack = { nav.back() }) }

                composable(
                    "symptom_edit?id={id}&regionId={regionId}&regionLabel={regionLabel}&name={name}",
                    arguments = listOf(
                        navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                        navArgument("regionId") { type = NavType.StringType; defaultValue = "" },
                        navArgument("regionLabel") { type = NavType.StringType; defaultValue = "" },
                        navArgument("name") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { e ->
                    val a = e.arguments
                    SymptomEditScreen(
                        nav,
                        id = a?.getLong("id")?.takeIf { it > 0 },
                        regionId = a?.getString("regionId")?.takeIf { it.isNotEmpty() },
                        regionLabel = a?.getString("regionLabel")?.takeIf { it.isNotEmpty() },
                        name = a?.getString("name")?.takeIf { it.isNotEmpty() },
                    )
                }
                composable(Routes.WELLBEING) { WellbeingScreen(nav) }
                composable(Routes.SELF_EXAM) { SelfExamScreen(nav) }

                composable(
                    "measure/{type}?add={add}",
                    arguments = listOf(
                        navArgument("type") { type = NavType.StringType },
                        navArgument("add") { type = NavType.BoolType; defaultValue = false },
                    ),
                ) { e ->
                    MeasurementDetailScreen(nav, typeKey = e.arguments?.getString("type") ?: "weight", openAdd = e.arguments?.getBoolean("add") ?: false)
                }

                composable("cycle_day/{day}", arguments = listOf(navArgument("day") { type = NavType.LongType })) { e ->
                    CycleDayScreen(nav, epochDay = e.arguments?.getLong("day") ?: 0L)
                }
                composable(Routes.CYCLE_STATS) { CycleStatsScreen(nav) }
                composable(Routes.CYCLE_SETTINGS) { CycleSettingsScreen(nav) }

                composable(Routes.STUDIES) { StudiesScreen(nav) }
                composable("study/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                    StudyDetailScreen(nav, id = e.arguments?.getLong("id") ?: 0L)
                }
                composable("study_edit?id={id}", arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })) { e ->
                    StudyEditScreen(nav, id = e.arguments?.getLong("id")?.takeIf { it > 0 })
                }
                composable("param_trend?name={name}", arguments = listOf(navArgument("name") { type = NavType.StringType; defaultValue = "" })) { e ->
                    ParamTrendScreen(nav, name = e.arguments?.getString("name") ?: "")
                }

                composable(Routes.MEDS) { MedsScreen(nav) }
                composable("med_edit?id={id}", arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })) { e ->
                    MedEditScreen(nav, id = e.arguments?.getLong("id")?.takeIf { it > 0 })
                }
                composable(Routes.APPOINTMENTS) { AppointmentsScreen(nav) }
                composable("appointment_edit?id={id}", arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })) { e ->
                    AppointmentEditScreen(nav, id = e.arguments?.getLong("id")?.takeIf { it > 0 })
                }

                composable(Routes.PROFILES) { ProfilesScreen(nav) }
                composable("profile_new/{kind}", arguments = listOf(navArgument("kind") { type = NavType.StringType })) { e ->
                    NewProfileScreen(nav, kind = e.arguments?.getString("kind") ?: "person")
                }
                composable(Routes.VACCINES) { VaccinesScreen(nav) }
                composable(Routes.ENERGY) { EnergyScreen(nav) }
                composable("vaccine_edit?id={id}", arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })) { e ->
                    VaccineEditScreen(nav, id = e.arguments?.getLong("id")?.takeIf { it > 0 })
                }

                composable(Routes.EXPORT) { ExportScreen(nav) }
                composable(Routes.PROFILE) { ProfileScreen(nav) }
                composable(Routes.SETTINGS) { SettingsScreen(nav) }
            }
        }
    }
}
