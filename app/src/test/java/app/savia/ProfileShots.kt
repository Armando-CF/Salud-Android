package app.savia

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.navigation.compose.rememberNavController
import app.savia.ui.lock.LockScreen
import app.savia.ui.profile.OnboardingContent
import app.savia.ui.profile.OnboardingScreen
import app.savia.ui.profile.ProfileScreen
import app.savia.ui.profile.SettingsScreen
import app.savia.ui.theme.SaviaTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.robolectric.annotation.Config

/** Capturas de Bienvenida, Mi ficha, Ajustes y pantalla de bloqueo. */
class ProfileShots : ScreenshotBase() {

    

    @Test
    fun onboarding1Welcome() = shot("profile_onboarding_1") { OnboardingScreen(onDone = {}) }

    @Test
    fun onboarding2AboutYou() = shot("profile_onboarding_2") { OnboardingContent(onDone = {}, initialStep = 1, initialSex = "F") }

    @Test
    fun onboarding3Modules() = shot("profile_onboarding_3") { OnboardingContent(onDone = {}, initialStep = 2, initialSex = "F") }

    @Test
    fun onboarding3ModulesOff() = shot("profile_onboarding_3_off", dark = true) { OnboardingContent(onDone = {}, initialStep = 2) }

    @Test
    fun onboarding4Notice() = shot("profile_onboarding_4") { OnboardingContent(onDone = {}, initialStep = 3) }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h2300dp-xhdpi")
    fun profile() {
        DemoData.seed()
        shotWhen("profile_ficha", waitText = "Tarjeta de emergencia") { ProfileScreen(rememberNavController()) }
    }

    @Test
    fun profileTopDark() {
        DemoData.seed(cycle = false)
        shotWhen("profile_ficha_dark", waitText = "Tarjeta de emergencia", dark = true) { ProfileScreen(rememberNavController()) }
    }

    @Test
    fun profileEmpty() = shotWhen("profile_ficha_vacia", waitText = "Tarjeta de emergencia") { ProfileScreen(rememberNavController()) }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h2000dp-xhdpi")
    fun settings() {
        DemoData.seed()
        shot("profile_ajustes") { SettingsScreen(rememberNavController()) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h2000dp-xhdpi")
    fun settingsDark() {
        shot("profile_ajustes_dark", dark = true) { SettingsScreen(rememberNavController()) }
    }

    @Test
    fun lockScreen() = shot("profile_bloqueo") { LockScreen(error = null, onUnlock = {}) }

    @Test
    fun lockScreenErrorDark() = shot("profile_bloqueo_dark", dark = true) {
        LockScreen(error = "Demasiados intentos. Vuelve a intentarlo más tarde.", onUnlock = {})
    }
}
