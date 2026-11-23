package app.savia

import androidx.test.core.app.ApplicationProvider
import app.savia.data.Profile
import app.savia.data.SaviaDb
import app.savia.ui.lock.AppLock
import app.savia.ui.profile.wipeAllData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfileDataTest {
    private lateinit var ctx: android.content.Context

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        Savia.init(ctx, SaviaDb.createInMemory(ctx))
    }

    @Test
    fun wipeAllDataClearsEveryTableAndAttachmentsButKeepsPrefs() = runBlocking {
        DemoData.seed()
        val attachments = File(ctx.filesDir, "attachments").apply { mkdirs() }
        File(attachments, "estudio.pdf").writeText("x")
        Savia.prefs.themeMode.value = "dark"
        val db = Savia.db
        assertTrue(db.symptoms().getAll().isNotEmpty())
        assertTrue(db.studies().getAllResults().isNotEmpty())

        wipeAllData(ctx)

        assertTrue(db.symptoms().getAll().isEmpty())
        assertTrue(db.measurements().getAll().isEmpty())
        assertTrue(db.dailyLogs().getAll().isEmpty())
        assertTrue(db.studies().getAll().isEmpty())
        assertTrue(db.studies().getAllResults().isEmpty())
        assertTrue(db.studies().getAllAttachments().isEmpty())
        assertTrue(db.cycle().getAll().isEmpty())
        assertTrue(db.medications().getAll().isEmpty())
        assertTrue(db.medications().getAllIntakes().isEmpty())
        assertTrue(db.appointments().getAll().isEmpty())
        assertTrue(db.selfExams().getAll().isEmpty())
        assertEquals(Profile(), db.profile().get())
        assertFalse(attachments.exists())
        // Los ajustes se conservan (no se reinicia la bienvenida).
        assertEquals("dark", Savia.prefs.themeMode.value)
        assertTrue(Savia.prefs.onboardingDone.value)
    }

    @Test
    fun relocksOnlyAfterLongBackground() {
        AppLock.markUnlocked()
        AppLock.onStop(changingConfigurations = false)
        AppLock.onStart()
        assertTrue("Una salida breve no debe volver a bloquear", AppLock.unlocked)

        AppLock.onStop(changingConfigurations = false)
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(AppLock.RELOCK_AFTER_MS + 1_000))
        AppLock.onStart()
        assertFalse("Tras más de un minuto en segundo plano debe bloquearse", AppLock.unlocked)

        // Girar la pantalla (cambio de configuración) nunca cuenta como salir.
        AppLock.markUnlocked()
        AppLock.onStop(changingConfigurations = true)
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(AppLock.RELOCK_AFTER_MS + 1_000))
        AppLock.onStart()
        assertTrue(AppLock.unlocked)
    }
}
