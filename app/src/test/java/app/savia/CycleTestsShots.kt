package app.savia

import androidx.navigation.compose.rememberNavController
import app.savia.data.CycleDay
import app.savia.ui.cycle.CycleDayScreen
import app.savia.ui.cycle.CycleScreen
import app.savia.ui.cycle.CycleStatsScreen
import app.savia.util.today
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.robolectric.annotation.Config

/** Pruebas de embarazo en el módulo del ciclo: tarjeta propia, marca en el calendario e historial. */
class CycleTestsShots : ScreenshotBase() {
    private fun seedTests() = runBlocking {
        DemoData.seed()
        val t = today()
        val dao = Savia.db.cycle()
        dao.save((dao.get(t - 3) ?: CycleDay(epochDay = t - 3)).copy(pregnancyTest = 1, sex = 2))
        dao.save((dao.get(t) ?: CycleDay(epochDay = t)).copy(pregnancyTest = 1))
        dao.save((dao.get(t - 70) ?: CycleDay(epochDay = t - 70)).copy(pregnancyTest = 2))
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h2300dp-xhdpi")
    fun dayEditor() {
        seedTests()
        shotWhen("cycle_tests_day", "Prueba de embarazo") { CycleDayScreen(rememberNavController(), today()) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1900dp-xhdpi")
    fun calendar() {
        seedTests()
        shotWhen("cycle_tests_calendar", "Prueba de embarazo") { CycleScreen(rememberNavController(), onBack = null) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h2600dp-xhdpi")
    fun stats() {
        seedTests()
        shotWhen("cycle_tests_stats", "Pruebas de embarazo") { CycleStatsScreen(rememberNavController()) }
    }
}
