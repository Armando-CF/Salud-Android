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

    /** Varios perfiles, viendo los datos de una mascota: aviso de perfil, secciones propias y respaldo de todos. */
    @Test
    @Config(qualifiers = "w411dp-h2300dp-xhdpi")
    fun exportScreenPetWithSeveralProfiles() {
        DemoData.seed()
        val dog = kotlinx.coroutines.runBlocking {
            Savia.createProfile(app.savia.data.Profile(name = "Leo", sex = "M", relation = "Hijo"))
            Savia.createProfile(app.savia.data.Profile(name = "Firulais", kind = "pet", species = "perro", breed = "Mestizo"))
        }
        Savia.switchTo(dog)
        shotWhen("export_screen_pet", "Firulais") { ExportScreen(rememberNavController()) }
    }

    @Test
    @Config(qualifiers = "w411dp-h2300dp-xhdpi")
    fun exportScreenSeveralProfilesDark() {
        DemoData.seed()
        kotlinx.coroutines.runBlocking { Savia.createProfile(app.savia.data.Profile(name = "Firulais", kind = "pet", species = "perro")) }
        ExportPrefs.setLastBackup(Savia.appContext, nowMillis() - 40 * 86_400_000L)
        shot("export_screen_multi_dark", dark = true) { ExportScreen(rememberNavController()) }
    }

    @Test
    fun exportScreenTop() {
        DemoData.seed()
        ExportPrefs.setLastBackup(Savia.appContext, nowMillis() - 45 * 86_400_000L)
        shot("export_screen_top") { ExportScreen(rememberNavController()) }
    }
}
