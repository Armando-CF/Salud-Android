package app.savia

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import app.savia.ui.studies.ParamTrendScreen
import app.savia.ui.studies.StudiesScreen
import app.savia.ui.studies.StudyDetailScreen
import app.savia.ui.studies.StudyEditScreen
import app.savia.ui.theme.SaviaTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.robolectric.annotation.Config

/** Capturas del módulo de estudios: ./gradlew :app:testDebugUnitTest --tests 'app.savia.StudiesShots' */
class StudiesShots : ScreenshotBase() {

    /**
     * Como [shot], pero espera a que aparezca [waitFor] (los datos llegan por Flow desde Room,
     * fuera del reloj de Compose) y permite interactuar antes de capturar.
     */
    private fun shotWhen(
        name: String,
        waitFor: String,
        dark: Boolean = false,
        before: () -> Unit = {},
        content: @Composable () -> Unit,
    ) {
        compose.setContent { SaviaTheme(if (dark) "dark" else "light") { Surface { content() } } }
        compose.waitUntil(5_000) { compose.onAllNodesWithText(waitFor, substring = true).fetchSemanticsNodes().isNotEmpty() }
        before()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/shots/$name.png")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1500dp-xhdpi")
    fun list() {
        DemoData.seed()
        shotWhen("studies_list", "Ultrasonido abdominal") { StudiesScreen(rememberNavController()) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1500dp-xhdpi")
    fun listDark() {
        DemoData.seed()
        shotWhen("studies_list_dark", "Ultrasonido abdominal", dark = true) { StudiesScreen(rememberNavController()) }
    }

    @Test
    fun listEmpty() {
        shotWhen("studies_list_empty", "Aún no hay estudios") { StudiesScreen(rememberNavController()) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1500dp-xhdpi")
    fun params() {
        DemoData.seed()
        shotWhen(
            "studies_params", "Ultrasonido abdominal",
            before = {
                compose.onNodeWithText("Parámetros").performClick()
                compose.waitUntil(5_000) { compose.onAllNodesWithText("Colesterol total").fetchSemanticsNodes().isNotEmpty() }
            },
        ) { StudiesScreen(rememberNavController()) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1800dp-xhdpi")
    fun detail() {
        DemoData.seed()
        shotWhen("studies_detail", "Colesterol total") { StudyDetailScreen(rememberNavController(), 1L) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1800dp-xhdpi")
    fun detailDark() {
        DemoData.seed()
        shotWhen("studies_detail_dark", "Colesterol total", dark = true) { StudyDetailScreen(rememberNavController(), 1L) }
    }

    @Test
    fun detailSimple() {
        DemoData.seed()
        shotWhen("studies_detail_simple", "Limpieza dental") { StudyDetailScreen(rememberNavController(), 5L) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h2000dp-xhdpi")
    fun editNew() {
        DemoData.seed()
        shotWhen("studies_edit_new", "Nombre del estudio") { StudyEditScreen(rememberNavController(), null) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h3600dp-xhdpi")
    fun editExisting() {
        DemoData.seed()
        shotWhen("studies_edit_existing", "Laboratorio Central") { StudyEditScreen(rememberNavController(), 1L) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1800dp-xhdpi")
    fun trend() {
        DemoData.seed()
        shotWhen("studies_trend", "Valores") { ParamTrendScreen(rememberNavController(), "colesterol total") }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1800dp-xhdpi")
    fun trendDark() {
        DemoData.seed()
        shotWhen("studies_trend_dark", "Valores", dark = true) { ParamTrendScreen(rememberNavController(), "Glucosa") }
    }
}
