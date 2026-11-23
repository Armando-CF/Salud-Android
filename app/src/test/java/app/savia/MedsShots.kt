package app.savia

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.onRoot
import androidx.navigation.compose.rememberNavController
import app.savia.ui.meds.AppointmentEditScreen
import app.savia.ui.meds.AppointmentsScreen
import app.savia.ui.meds.MedEditScreen
import app.savia.ui.meds.MedsScreen
import app.savia.ui.theme.SaviaTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Capturas del módulo de medicamentos y citas (app/build/shots/meds_*.png). */
class MedsShots : ScreenshotBase() {

    /**
     * Como [shot], pero da tiempo a que lleguen los datos de Room (los Flow emiten desde hilos
     * de fondo, así que una sola pasada de waitForIdle puede capturar la pantalla aún vacía).
     */
    private fun shotLoaded(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            SaviaTheme(if (dark) "dark" else "light") { Surface { content() } }
        }
        repeat(8) {
            compose.waitForIdle()
            Thread.sleep(60)
            shadowOf(android.os.Looper.getMainLooper()).idle()
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/shots/$name.png")
    }

    private fun firstMedId(name: String): Long = runBlocking { Savia.db.medications().getAll().first { it.name == name }.id }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1800dp-xhdpi")
    fun meds() {
        DemoData.seed()
        shotLoaded("meds_list") { MedsScreen(rememberNavController()) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1800dp-xhdpi")
    fun medsDark() {
        DemoData.seed()
        shotLoaded("meds_list_dark", dark = true) { MedsScreen(rememberNavController()) }
    }

    @Test
    fun medsEmpty() {
        shotLoaded("meds_empty") { MedsScreen(rememberNavController()) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1900dp-xhdpi")
    fun medEditExisting() {
        DemoData.seed()
        val id = firstMedId("Vitamina D")
        shotLoaded("meds_edit_existing") { MedEditScreen(rememberNavController(), id) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1500dp-xhdpi")
    fun medEditNew() {
        shotLoaded("meds_edit_new") { MedEditScreen(rememberNavController(), null) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1200dp-xhdpi")
    fun appointments() {
        DemoData.seed()
        shotLoaded("meds_appointments") { AppointmentsScreen(rememberNavController()) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1200dp-xhdpi")
    fun appointmentsDark() {
        DemoData.seed()
        shotLoaded("meds_appointments_dark", dark = true) { AppointmentsScreen(rememberNavController()) }
    }

    @Test
    fun appointmentsEmpty() {
        shotLoaded("meds_appointments_empty") { AppointmentsScreen(rememberNavController()) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1500dp-xhdpi")
    fun appointmentEdit() {
        DemoData.seed()
        val id = runBlocking { Savia.db.appointments().getAll().first { it.title == "Revisión anual" }.id }
        shotLoaded("meds_appointment_edit") { AppointmentEditScreen(rememberNavController(), id) }
    }
}
