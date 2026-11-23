package app.savia

import androidx.test.core.app.ApplicationProvider
import app.savia.data.Measurement
import app.savia.data.Profile
import app.savia.data.SaviaDb
import app.savia.data.SymptomEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Perfiles múltiples: cada perfil tiene su propia base y sus propios ajustes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfilesCoreTest {
    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        Savia.init(ctx, SaviaDb.createInMemory(ctx))
    }

    @Test
    fun dataAndSettingsAreIsolatedPerProfile() = runBlocking {
        Savia.db.profile().save(Profile(name = "Ana", sex = "F"))
        Savia.db.symptoms().insert(SymptomEntry(timestamp = 1L, name = "Dolor de cabeza"))
        Savia.prefs.cycleEnabled.value = true
        Savia.prefs.themeMode.value = "dark"

        val kid = Savia.createProfile(Profile(name = "Leo", sex = "M", relation = "Hijo"))
        val dog = Savia.createProfile(Profile(name = "Firulais", kind = "pet", species = "perro"))
        assertEquals(listOf(1, kid, dog), Savia.profileIds)
        assertEquals(1, Savia.activeId.value)

        Savia.switchTo(dog)
        assertEquals(dog, Savia.activeId.value)
        assertTrue(Savia.isPet)
        assertEquals("Firulais", Savia.active.value.displayName)
        assertEquals("Perro", Savia.active.value.subtitle)
        assertTrue(Savia.db.symptoms().getAll().isEmpty())
        assertFalse(Savia.prefs.cycleEnabled.value)          // ajuste por perfil
        assertEquals("dark", Savia.prefs.themeMode.value)    // ajuste global
        Savia.db.measurements().insert(Measurement(timestamp = 2L, type = "weight", value = 12.4))

        Savia.switchTo(kid)
        assertFalse(Savia.isPet)
        assertTrue(Savia.db.measurements().getAll().isEmpty())
        assertEquals("Hijo", Savia.active.value.subtitle)

        Savia.switchTo(1)
        assertEquals(1, Savia.db.symptoms().getAll().size)
        assertTrue(Savia.prefs.cycleEnabled.value)
        assertEquals(12.4, Savia.dbFor(dog).measurements().getAll().single().value, 0.0)

        val names = Savia.summaries.first().map { it.displayName }
        assertEquals(listOf("Ana", "Leo", "Firulais"), names)
    }

    @Test
    fun deletingAProfileRemovesItAndFallsBackToTheFirst() = runBlocking {
        val cat = Savia.createProfile(Profile(name = "Garfield", kind = "pet", species = "gato")) { it.atlasSex.value = "M" }
        Savia.switchTo(cat)
        Savia.attachmentsDir().mkdirs()
        assertTrue(Savia.attachmentsDir().path.endsWith("attachments/p$cat"))
        Savia.deleteProfile(cat)
        assertEquals(1, Savia.activeId.value)
        assertEquals(listOf(1), Savia.profileIds)
        assertFalse(Savia.attachmentsDir(cat).exists())
        // El perfil 1 no se puede eliminar y los ids no se reutilizan.
        Savia.deleteProfile(1)
        assertEquals(listOf(1), Savia.profileIds)
        val next = Savia.createProfile(Profile(name = "Otro"))
        assertTrue(next > cat)
        assertEquals("F", Savia.prefsFor(next).atlasSex.value)
    }
}
