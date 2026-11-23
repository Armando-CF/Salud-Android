package app.savia

import androidx.navigation.compose.rememberNavController
import app.savia.export.ExportPrefs
import app.savia.ui.export.ExportScreen
import app.savia.util.nowMillis
import org.junit.Test
import org.robolectric.annotation.Config

class ExportShots : ScreenshotBase() {
    @Test
    @Config(qualifiers = "w411dp-h2100dp-xhdpi")
    fun exportScreen() {
        DemoData.seed()
        ExportPrefs.setLastBackup(Savia.appContext, nowMillis() - 3 * 86_400_000L)
        shot("export_screen") { ExportScreen(rememberNavController()) }
    }

    @Test
    @Config(qualifiers = "w411dp-h2100dp-xhdpi")
    fun exportScreenDarkNoBackup() {
        DemoData.seed(cycle = false)
        Savia.appContext.getSharedPreferences("savia_export", 0).edit().clear().commit()
        shot("export_screen_dark", dark = true) { ExportScreen(rememberNavController()) }
    }

    @Test
    fun exportScreenTop() {
        DemoData.seed()
        ExportPrefs.setLastBackup(Savia.appContext, nowMillis() - 45 * 86_400_000L)
        shot("export_screen_top") { ExportScreen(rememberNavController()) }
    }
}
