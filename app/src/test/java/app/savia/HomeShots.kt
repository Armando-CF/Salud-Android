package app.savia

import androidx.navigation.compose.rememberNavController
import app.savia.ui.home.HomeScreen
import app.savia.ui.home.MoreScreen
import org.junit.Test
import org.robolectric.annotation.Config

class HomeShots : ScreenshotBase() {
    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1700dp-xhdpi")
    fun home() {
        DemoData.seed()
        shotWhen("home", "Síntomas recientes") { HomeScreen(rememberNavController()) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1500dp-xhdpi")
    fun homeDarkNoCycle() {
        DemoData.seed(cycle = false)
        shotWhen("home_dark_m", "Síntomas recientes", dark = true) { HomeScreen(rememberNavController()) }
    }

    @Test
    fun homeEmpty() = shotWhen("home_empty", "Empieza por aquí") { HomeScreen(rememberNavController()) }

    @Test
    fun more() {
        DemoData.seed()
        shotWhen("more", "Alex") { MoreScreen(rememberNavController()) }
    }
}
