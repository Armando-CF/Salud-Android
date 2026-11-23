package app.savia

import androidx.test.core.app.ApplicationProvider
import app.savia.data.Measurement
import app.savia.data.Profile
import app.savia.data.ProfileInfo
import app.savia.data.SaviaDb
import app.savia.data.SymptomEntry
import app.savia.data.Vaccination
import app.savia.ui.lock.AppLock
import app.savia.ui.profile.ageText
import app.savia.ui.profile.birthFromAge
import app.savia.ui.profile.profileSubtitle
import app.savia.ui.profile.sterilizedKey
import app.savia.ui.profile.sterilizedValue
import app.savia.ui.profile.suggestCycle
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
    fun wipeAllDataClearsEveryTableAndAttachmentsButKeepsPrefsAndIdentity() = runBlocking {
        DemoData.seed()
        val attachments = Savia.attachmentsDir().apply { mkdirs() }
        assertEquals(File(ctx.filesDir, "attachments"), attachments)
        File(attachments, "estudio.pdf").writeText("x")
        // Adjuntos de OTRO perfil (subcarpeta dentro de la del perfil 1): no deben tocarse.
        val other = File(attachments, "p2").apply { mkdirs() }
        File(other, "radiografia.jpg").writeText("y")
        Savia.prefs.themeMode.value = "dark"
        val db = Savia.db
        db.vaccinations().insert(Vaccination(epochDay = 20000, name = "Influenza"))
        db.profile().save(db.profile().get()!!.copy(relation = "Yo", avatarColor = 3))
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
        assertTrue(db.vaccinations().getAll().isEmpty())
        // La ficha se vacía, pero el perfil conserva su identidad (nombre, parentesco, color).
        assertEquals(Profile(name = "Alex", relation = "Yo", avatarColor = 3), db.profile().get())
        assertFalse(File(attachments, "estudio.pdf").exists())
        assertTrue("Los adjuntos de otros perfiles se conservan", File(other, "radiografia.jpg").exists())
        // Los ajustes se conservan (no se reinicia la bienvenida).
        assertEquals("dark", Savia.prefs.themeMode.value)
        assertTrue(Savia.prefs.onboardingDone.value)
    }

    @Test
    fun wipingAPetKeepsItAPetAndOnlyTouchesItsOwnData() = runBlocking {
        Savia.db.symptoms().insert(SymptomEntry(timestamp = 1L, name = "Dolor de cabeza"))
        val dog = Savia.createProfile(Profile(name = "Firulais", kind = "pet", species = "perro", breed = "Mestizo", avatarColor = 5))
        Savia.switchTo(dog)
        Savia.db.measurements().insert(Measurement(timestamp = 2L, type = "weight", value = 12.4))
        val dir = Savia.attachmentsDir().apply { mkdirs() }
        File(dir, "placa.jpg").writeText("z")

        wipeAllData(ctx)

        assertTrue(Savia.db.measurements().getAll().isEmpty())
        val p = Savia.db.profile().get()!!
        assertTrue(p.isPet)
        assertEquals("Firulais", p.name)
        assertEquals("perro", p.species)
        assertEquals(5, p.avatarColor)
        assertEquals("", p.breed)
        assertFalse(File(dir, "placa.jpg").exists())
        // El otro perfil sigue intacto.
        assertEquals(1, Savia.dbFor(1).symptoms().getAll().size)
    }

    @Test
    fun ageTextAndHelpers() {
        val today = java.time.LocalDate.of(2026, 11, 23).toEpochDay()
        fun born(y: Int, m: Int, d: Int) = java.time.LocalDate.of(y, m, d).toEpochDay()
        assertEquals("34 años", ageText(born(1992, 3, 10), today))
        assertEquals("34 años y 8 meses", ageText(born(1992, 3, 10), today, detailed = true))
        assertEquals("2 años y 4 meses", ageText(born(2024, 7, 20), today))
        assertEquals("8 meses", ageText(born(2026, 3, 20), today))
        assertEquals("1 año", ageText(born(2025, 11, 23), today))
        assertEquals("3 semanas", ageText(today - 21, today))
        assertEquals("5 días", ageText(today - 5, today))
        assertEquals("", ageText(today + 3, today))
        assertEquals(born(2023, 5, 23), birthFromAge(3, 6, today))
        // El ciclo solo se propone para mujeres de 10 años o más (o sin fecha).
        assertTrue(suggestCycle("F", null))
        assertFalse(suggestCycle("M", null))
        assertFalse(suggestCycle(null, null))
        assertFalse(suggestCycle("F", java.time.LocalDate.now().minusYears(6).toEpochDay()))
        assertTrue(suggestCycle("F", java.time.LocalDate.now().minusYears(14).toEpochDay()))
        assertEquals(true, sterilizedValue(sterilizedKey(true)))
        assertEquals(null, sterilizedValue(sterilizedKey(null)))
        assertEquals("Perro · 2 años y 4 meses", profileSubtitle(ProfileInfo(3, "Firulais", kind = "pet", species = "perro", birthEpochDay = born(2024, 7, 20)), today))
        assertEquals("Hija · 8 años", profileSubtitle(ProfileInfo(2, "Sofía", relation = "Hija", birthEpochDay = born(2018, 5, 2)), today))
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
