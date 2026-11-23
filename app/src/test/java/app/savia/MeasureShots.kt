package app.savia

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import app.savia.data.MeasureType
import app.savia.data.Measurement
import app.savia.ui.measure.MeasureFormContent
import app.savia.ui.measure.MeasurementDetailScreen
import app.savia.ui.measure.MeasurementsScreen
import app.savia.ui.theme.SaviaTheme
import app.savia.util.epochDayToMillis
import app.savia.util.today
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.robolectric.annotation.Config

/** Capturas del módulo de mediciones (app/build/shots/measure_*.png). */
class MeasureShots : ScreenshotBase() {

    /** Como [shot], pero espera a que los Flow de Room hayan pintado [waitText] antes de capturar. */
    private fun shotWhen(name: String, waitText: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            SaviaTheme(if (dark) "dark" else "light") { Surface { content() } }
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText(waitText, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/shots/$name.png")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1500dp-xhdpi")
    fun hub() {
        DemoData.seed()
        shotWhen("measure_hub", "Faltan") { MeasurementsScreen(rememberNavController(), onBack = null) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1500dp-xhdpi")
    fun hubDark() {
        DemoData.seed()
        shotWhen("measure_hub_dark", "Faltan", dark = true) { MeasurementsScreen(rememberNavController(), onBack = {}) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1300dp-xhdpi")
    fun hubEmpty() = shot("measure_hub_empty") { MeasurementsScreen(rememberNavController(), onBack = null) }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h2500dp-xhdpi")
    fun weightDetail() {
        DemoData.seed()
        shotWhen("measure_weight", "Historial") { MeasurementDetailScreen(rememberNavController(), "weight", openAdd = false) }
    }

    @Test
    fun weightDetailPhone() {
        DemoData.seed()
        shotWhen("measure_weight_phone", "Toca o arrastra") { MeasurementDetailScreen(rememberNavController(), "weight", openAdd = false) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h2200dp-xhdpi")
    fun weightDetailDark() {
        DemoData.seed()
        shotWhen("measure_weight_dark", "Historial", dark = true) { MeasurementDetailScreen(rememberNavController(), "weight", openAdd = false) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1900dp-xhdpi")
    fun pressureDetail() {
        DemoData.seed()
        shotWhen("measure_bp", "Historial") { MeasurementDetailScreen(rememberNavController(), "bp", openAdd = false) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1900dp-xhdpi")
    fun glucoseDetailDark() {
        DemoData.seed()
        shotWhen("measure_glucose_dark", "Historial", dark = true) { MeasurementDetailScreen(rememberNavController(), "glucose", openAdd = false) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1500dp-xhdpi")
    fun temperatureSingleRecord() {
        DemoData.seed()
        shotWhen("measure_temp_single", "Historial") { MeasurementDetailScreen(rememberNavController(), "temp", openAdd = false) }
    }

    @Test
    fun detailEmpty() = shotWhen("measure_detail_empty", "Sin registros") {
        MeasurementDetailScreen(rememberNavController(), "spo2", openAdd = false)
    }

    @Test
    fun formNew() = shot("measure_form_new") {
        Column(Modifier.padding(vertical = 16.dp)) {
            MeasureFormContent(MeasureType.GLUCOSE, initial = null, onSave = {}, onCancel = {})
        }
    }

    @Test
    fun formEditPressure() = shot("measure_form_edit_bp", dark = true) {
        Column(Modifier.padding(vertical = 16.dp)) {
            MeasureFormContent(
                MeasureType.BLOOD_PRESSURE,
                initial = Measurement(id = 7, timestamp = epochDayToMillis(today() - 1, 8 * 60), type = "bp", value = 128.0, value2 = 82.0, tag = "Por la mañana", notes = "Después de caminar."),
                onSave = {}, onCancel = {}, onDelete = {},
            )
        }
    }
}
