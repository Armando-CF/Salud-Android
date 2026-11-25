package app.savia

import androidx.navigation.compose.rememberNavController
import app.savia.data.Profile
import app.savia.ui.energy.EnergyScreen
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.robolectric.annotation.Config

class EnergyShots : ScreenshotBase() {
    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h2500dp-xhdpi")
    fun suggested() {
        DemoData.seed()
        shotWhen("energy_suggested", "En qué se va tu energía") { EnergyScreen(rememberNavController()) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h2500dp-xhdpi")
    fun chosenDark() {
        DemoData.seed(cycle = false)
        Savia.prefs.activityLevel.value = "moderate"
        shotWhen("energy_chosen_dark", "En qué se va tu energía", dark = true) { EnergyScreen(rememberNavController()) }
    }

    @Test
    fun missingData() {
        runBlocking { Savia.db.profile().save(Profile(name = "Sin datos")) }
        shotWhen("energy_missing", "Faltan datos") { EnergyScreen(rememberNavController()) }
    }
}
