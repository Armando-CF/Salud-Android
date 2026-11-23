package app.savia

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import app.savia.data.SelfExam
import app.savia.ui.symptoms.SymptomEditScreen
import app.savia.ui.symptoms.SymptomsScreen
import app.savia.ui.theme.SaviaTheme
import app.savia.ui.wellbeing.GuideContent
import app.savia.ui.wellbeing.SelfExamForm
import app.savia.ui.wellbeing.SelfExamGuides
import app.savia.ui.wellbeing.SelfExamScreen
import app.savia.ui.wellbeing.WellbeingScreen
import app.savia.util.today
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.robolectric.annotation.Config

/** Capturas de Síntomas, Bienestar diario y Autoexploración. */
class SymptomsShots : ScreenshotBase() {
    /** Como `shot`, pero espera a que Room entregue los datos (los Flow emiten desde otro hilo). */
    private fun shotAsync(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            SaviaTheme(if (dark) "dark" else "light") { Surface { content() } }
        }
        repeat(8) {
            compose.waitForIdle()
            Thread.sleep(60)
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/shots/$name.png")
    }

    @Test
    fun symptomsEmpty() = shotAsync("symptoms_empty") { SymptomsScreen(rememberNavController(), null) }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1500dp-xhdpi")
    fun symptomsHistory() {
        DemoData.seed()
        shotAsync("symptoms_history") { SymptomsScreen(rememberNavController(), null) }
    }

    @Test
    fun symptomsHistoryDark() {
        DemoData.seed()
        shotAsync("symptoms_history_dark", dark = true) { SymptomsScreen(rememberNavController(), {}) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h2100dp-xhdpi")
    fun symptomsSummary() {
        DemoData.seed()
        shotAsync("symptoms_summary") { SymptomsScreen(rememberNavController(), null, initialTab = 1) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h2100dp-xhdpi")
    fun symptomsSummaryDark() {
        DemoData.seed()
        shotAsync("symptoms_summary_dark", dark = true) { SymptomsScreen(rememberNavController(), null, initialTab = 1) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h2000dp-xhdpi")
    fun symptomNew() {
        DemoData.seed()
        shotAsync("symptom_edit_new") {
            SymptomEditScreen(rememberNavController(), id = null, regionId = "abd_epigastrio", regionLabel = "Epigastrio", name = null)
        }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h2000dp-xhdpi")
    fun symptomEdit() {
        DemoData.seed()
        // id 1 = "Dolor de cabeza" de hoy (con duración, etiquetas y nota).
        shotAsync("symptom_edit_existing") { SymptomEditScreen(rememberNavController(), id = 1, regionId = null, regionLabel = null, name = null) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h2000dp-xhdpi")
    fun symptomEditDark() {
        DemoData.seed()
        shotAsync("symptom_edit_existing_dark", dark = true) {
            SymptomEditScreen(rememberNavController(), id = 3, regionId = null, regionLabel = null, name = null)
        }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h2300dp-xhdpi")
    fun wellbeing() {
        DemoData.seed()
        shotAsync("wellbeing") { WellbeingScreen(rememberNavController()) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h2300dp-xhdpi")
    fun wellbeingDark() {
        DemoData.seed()
        shotAsync("wellbeing_dark", dark = true) { WellbeingScreen(rememberNavController(), initialDay = today() - 1) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1400dp-xhdpi")
    fun wellbeingEmpty() = shotAsync("wellbeing_empty") { WellbeingScreen(rememberNavController()) }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1700dp-xhdpi")
    fun selfExam() {
        DemoData.seed()
        shotAsync("selfexam") { SelfExamScreen(rememberNavController()) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1400dp-xhdpi")
    fun selfExamEmptyDark() = shotAsync("selfexam_empty_dark", dark = true) { SelfExamScreen(rememberNavController()) }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1700dp-xhdpi")
    fun selfExamFormAndGuide() = shotAsync("selfexam_form_guide") {
        var exam by remember { mutableStateOf(SelfExam(epochDay = today(), type = "piel", normal = false, notes = "Lunar nuevo en el hombro.")) }
        androidx.compose.foundation.layout.Column(Modifier.padding(24.dp)) {
            SelfExamForm(exam, listOf("mama", "testicular", "piel"), { exam = it })
            GuideContent(SelfExamGuides.of("piel")!!, Modifier.padding(top = 24.dp))
            GuideContent(SelfExamGuides.of("mama")!!, Modifier.padding(top = 24.dp))
        }
    }
}
