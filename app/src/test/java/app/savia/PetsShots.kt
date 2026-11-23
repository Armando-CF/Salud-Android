package app.savia

import androidx.navigation.compose.rememberNavController
import app.savia.data.Measurement
import app.savia.data.Profile
import app.savia.data.SymptomEntry
import app.savia.ui.cycle.CycleScreen
import app.savia.ui.measure.MeasurementDetailScreen
import app.savia.ui.measure.MeasurementsScreen
import app.savia.ui.measure.recordHeight
import app.savia.ui.measure.syncProfileHeight
import app.savia.ui.symptoms.SymptomEditScreen
import app.savia.ui.symptoms.SymptomsScreen
import app.savia.ui.wellbeing.WellbeingScreen
import app.savia.util.epochDayToMillis
import app.savia.util.today
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/** Capturas de los módulos con un perfil de mascota y con un perfil de niña/niño. */
class PetsShots : ScreenshotBase() {

    /** Crea y activa un perro con peso (≈12 kg) y temperatura. */
    private fun dog(): Int = runBlocking {
        val id = Savia.createProfile(Profile(name = "Firulais", kind = "pet", species = "perro", breed = "Mestizo", sex = "M", goalWeightKg = 11.5))
        Savia.switchTo(id)
        val t = today()
        for (i in 0..11) {
            val w = 13.1 - i * 0.12 + listOf(0.0, 0.08, -0.05)[i % 3]
            Savia.db.measurements().insert(Measurement(timestamp = epochDayToMillis(t - 110 + i * 10, 10 * 60), type = "weight", value = Math.round(w * 100) / 100.0))
        }
        Savia.db.measurements().insert(Measurement(timestamp = epochDayToMillis(t - 3, 18 * 60), type = "temp", value = 38.7))
        Savia.db.measurements().insert(Measurement(timestamp = epochDayToMillis(t - 20, 18 * 60), type = "temp", value = 39.4))
        Savia.db.symptoms().insert(SymptomEntry(timestamp = epochDayToMillis(t - 1, 9 * 60), name = "Vómito", intensity = 6, tags = "Comió algo raro"))
        id
    }

    /** Crea y activa una niña de 7 años con peso y estatura de varios meses. */
    private fun child(): Int = runBlocking {
        val t = today()
        val id = Savia.createProfile(Profile(name = "Sofía", sex = "F", relation = "Hija", birthEpochDay = t - 7 * 365 - 30))
        Savia.switchTo(id)
        for (i in 0..7) {
            Savia.db.measurements().insert(Measurement(timestamp = epochDayToMillis(t - 420 + i * 60, 9 * 60), type = "weight", value = 21.0 + i * 0.45))
            Savia.db.measurements().insert(Measurement(timestamp = epochDayToMillis(t - 420 + i * 60, 9 * 60 + 5), type = "height", value = 116.0 + i * 0.9))
        }
        syncProfileHeight(Savia.db)
        id
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1900dp-xhdpi")
    fun petSymptomEditor() {
        dog()
        shot("pets_symptom_edit") { SymptomEditScreen(rememberNavController(), id = null, regionId = null, regionLabel = null, name = null) }
    }

    @Test
    fun petSymptomEditorOtherSpeciesHasNoBodyMap() {
        runBlocking { Savia.switchTo(Savia.createProfile(Profile(name = "Piolín", kind = "pet", species = "ave"))) }
        compose.setContent { app.savia.ui.theme.SaviaTheme("light") { SymptomEditScreen(rememberNavController(), null, null, null, null) } }
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasText("Elegir en el cuerpo")).fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasText("¿Qué le notas?")).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun petSymptomsEmptyDark() {
        runBlocking { Savia.switchTo(Savia.createProfile(Profile(name = "Garfield", kind = "pet", species = "gato"))) }
        shotWhen("pets_symptoms_empty_dark", "Garfield", dark = true) { SymptomsScreen(rememberNavController(), onBack = null) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1100dp-xhdpi")
    fun petMeasureHub() {
        dog()
        shotWhen("pets_measure_hub", "Temperatura") { MeasurementsScreen(rememberNavController(), onBack = null) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1900dp-xhdpi")
    fun petWeightDetail() {
        dog()
        shotWhen("pets_weight_detail", "Historial") { MeasurementDetailScreen(rememberNavController(), "weight", openAdd = false) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1700dp-xhdpi")
    fun smallPetWeightInGrams() {
        runBlocking {
            Savia.switchTo(Savia.createProfile(Profile(name = "Canela", kind = "pet", species = "roedor")))
            val t = today()
            for (i in 0..6) Savia.db.measurements().insert(Measurement(timestamp = epochDayToMillis(t - 60 + i * 10, 10 * 60), type = "weight", value = 0.031 + i * 0.002))
        }
        shotWhen("pets_weight_grams_dark", "Historial", dark = true) { MeasurementDetailScreen(rememberNavController(), "weight", openAdd = false) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1500dp-xhdpi")
    fun petTemperatureDetail() {
        dog()
        shotWhen("pets_temp_detail", "Historial") { MeasurementDetailScreen(rememberNavController(), "temp", openAdd = false) }
    }

    @Test
    fun petWellbeingIsPeopleOnly() {
        dog()
        shot("pets_people_only") { WellbeingScreen(rememberNavController()) }
    }

    @Test
    fun petCycleIsPeopleOnly() {
        dog()
        compose.setContent { app.savia.ui.theme.SaviaTheme("light") { CycleScreen(rememberNavController(), onBack = null) } }
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasText("Esta sección es para perfiles de personas")).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h2100dp-xhdpi")
    fun childWeightDetail() {
        child()
        shotWhen("pets_child_weight", "Historial") { MeasurementDetailScreen(rememberNavController(), "weight", openAdd = false) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1500dp-xhdpi")
    fun childHeightDetail() {
        child()
        shotWhen("pets_child_height", "Historial") { MeasurementDetailScreen(rememberNavController(), "height", openAdd = false) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1500dp-xhdpi")
    fun childMeasureHub() {
        child()
        shotWhen("pets_child_hub", "Estatura") { MeasurementsScreen(rememberNavController(), onBack = null) }
    }

    @Test
    fun heightHistoryKeepsTheProfileInSync() = runBlocking {
        val db = Savia.db
        val t = today()
        db.profile().save(Profile(name = "Ana", heightCm = 160.0))
        // Guardar desde el diálogo de estatura: ficha + historial.
        recordHeight(db, 121.5, now = epochDayToMillis(t - 10))
        assertEquals(121.5, db.profile().get()!!.heightCm!!, 0.0)
        assertEquals(1, db.measurements().getAll().count { it.type == "height" })
        // El mismo valor no duplica el registro.
        recordHeight(db, 121.5, now = epochDayToMillis(t - 9))
        assertEquals(1, db.measurements().getAll().count { it.type == "height" })
        // Una medición más reciente manda en la ficha; una más vieja no.
        db.measurements().insert(Measurement(timestamp = epochDayToMillis(t - 1), type = "height", value = 123.0))
        db.measurements().insert(Measurement(timestamp = epochDayToMillis(t - 300), type = "height", value = 115.0))
        syncProfileHeight(db)
        assertEquals(123.0, db.profile().get()!!.heightCm!!, 0.0)
        assertEquals("Ana", db.profile().get()!!.name)
    }
}
