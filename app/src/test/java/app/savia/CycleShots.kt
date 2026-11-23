package app.savia

import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import app.savia.data.CycleDay
import app.savia.ui.cycle.CycleDayScreen
import app.savia.ui.cycle.CycleScreen
import app.savia.ui.cycle.CycleSettingsScreen
import app.savia.ui.cycle.CycleStatsScreen
import app.savia.ui.cycle.CycleSummaryCard
import app.savia.ui.cycle.rememberCycleState
import app.savia.ui.theme.SaviaTheme
import app.savia.util.today
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Capturas del módulo de ciclo. Salen en app/build/shots/cycle_*.png */
class CycleShots : ScreenshotBase() {

    /** Como [shot], pero espera a que lleguen los datos de Room (que emite desde otro hilo). */
    private fun asyncShot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            SaviaTheme(if (dark) "dark" else "light") { Surface { content() } }
        }
        repeat(10) {
            Thread.sleep(50)
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
        }
        compose.onRoot().captureRoboImage("build/shots/$name.png")
    }

    private fun seedPeriods(vararg startsAgo: Pair<Long, Int>) = runBlocking {
        val t = today()
        for ((ago, len) in startsAgo) {
            for (d in 0 until len) {
                if (t - ago + d > t) break
                Savia.db.cycle().save(CycleDay(epochDay = t - ago + d, flow = if (d < 2) 3 else 2))
            }
        }
        Savia.prefs.cycleEnabled.value = true
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1900dp-xhdpi")
    fun main() {
        DemoData.seed()
        asyncShot("cycle_main") { CycleScreen(rememberNavController(), null) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1900dp-xhdpi")
    fun mainDark() {
        DemoData.seed()
        asyncShot("cycle_main_dark", dark = true) { CycleScreen(rememberNavController(), null) }
    }

    @Test
    fun mainPhone() {
        DemoData.seed()
        asyncShot("cycle_main_phone") { CycleScreen(rememberNavController(), null) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1500dp-xhdpi")
    fun empty() {
        asyncShot("cycle_empty") { CycleScreen(rememberNavController(), {}) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1900dp-xhdpi")
    fun inPeriod() {
        seedPeriods(58L to 5, 30L to 5, 2L to 5)
        asyncShot("cycle_in_period") { CycleScreen(rememberNavController(), null) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1900dp-xhdpi")
    fun continuePrompt() {
        seedPeriods(59L to 5, 31L to 5, 3L to 3)
        asyncShot("cycle_continue") { CycleScreen(rememberNavController(), null) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1900dp-xhdpi")
    fun late() {
        seedPeriods(88L to 5, 60L to 5, 32L to 5)
        asyncShot("cycle_late", dark = true) { CycleScreen(rememberNavController(), null) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h2300dp-xhdpi")
    fun day() {
        DemoData.seed()
        runBlocking {
            Savia.db.cycle().save(
                CycleDay(
                    epochDay = today(), flow = 0, symptoms = "fatiga,senos", moods = "tranquila", mucus = "cremoso",
                    basalTemp = 36.6, sex = 1, pregnancyTest = 2, contraceptive = true, notes = "Todo bien.",
                ),
            )
        }
        asyncShot("cycle_day") { CycleDayScreen(rememberNavController(), today()) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h2300dp-xhdpi")
    fun dayFutureDark() {
        DemoData.seed()
        asyncShot("cycle_day_future_dark", dark = true) { CycleDayScreen(rememberNavController(), today() + 20) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h2900dp-xhdpi")
    fun stats() {
        DemoData.seed()
        runBlocking {
            val t = today()
            for (i in 0..24) {
                val d = t - 30 + i
                val existing = Savia.db.cycle().get(d) ?: CycleDay(epochDay = d)
                Savia.db.cycle().save(existing.copy(basalTemp = if (i < 14) 36.3 + (i % 3) * 0.05 else 36.7 + (i % 3) * 0.05))
            }
        }
        asyncShot("cycle_stats") { CycleStatsScreen(rememberNavController()) }
    }

    @Test
    fun statsEmpty() {
        asyncShot("cycle_stats_empty") { CycleStatsScreen(rememberNavController()) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1400dp-xhdpi")
    fun settings() {
        DemoData.seed()
        asyncShot("cycle_settings") { CycleSettingsScreen(rememberNavController()) }
    }

    @Test
    fun summaryCard() {
        DemoData.seed()
        asyncShot("cycle_summary_card") {
            val state = rememberCycleState()
            Column(Modifier.padding(16.dp)) {
                if (state.loaded) CycleSummaryCard(state.analysis, onClick = {})
            }
        }
    }
}
