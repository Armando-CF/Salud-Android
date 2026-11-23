package app.savia

import androidx.navigation.compose.rememberNavController
import app.savia.data.Profile
import app.savia.data.Vaccination
import app.savia.ui.vaccines.VaccineEditScreen
import app.savia.ui.vaccines.VaccinesScreen
import app.savia.util.today
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.robolectric.annotation.Config

class VaccinesShots : ScreenshotBase() {
    private val t get() = today()

    private fun asPet(species: String = "perro", name: String = "Firulais") = runBlocking {
        val id = Savia.createProfile(Profile(name = name, kind = "pet", species = species, sex = "M"))
        Savia.switchTo(id)
    }

    private fun seedPerson() = runBlocking {
        Savia.db.profile().save(Profile(name = "Sofía", sex = "F", birthEpochDay = t - 365L * 6))
        val dao = Savia.db.vaccinations()
        dao.insert(Vaccination(epochDay = t - 365L * 6 + 2, name = "BCG", dose = "Dosis única", place = "Hospital General"))
        dao.insert(Vaccination(epochDay = t - 365L * 6 + 60, name = "Hexavalente", dose = "1.ª dosis", place = "Centro de salud"))
        dao.insert(Vaccination(epochDay = t - 365L * 6 + 120, name = "Hexavalente", dose = "2.ª dosis", place = "Centro de salud", lot = "HX2041"))
        dao.insert(Vaccination(epochDay = t - 365L * 5, name = "SRP (triple viral)", dose = "1.ª dosis", nextDueEpochDay = t + 12, place = "Centro de salud"))
        dao.insert(Vaccination(epochDay = t - 380, name = "Influenza", dose = "Anual", nextDueEpochDay = t - 15, place = "Escuela", notes = "Sin reacciones."))
        dao.insert(Vaccination(epochDay = t - 200, name = "Desparasitación", type = "otro", nextDueEpochDay = t + 150))
    }

    private fun seedDog() = runBlocking {
        asPet()
        val dao = Savia.db.vaccinations()
        dao.insert(Vaccination(epochDay = t - 372, name = "Rabia", dose = "Anual", nextDueEpochDay = t - 7, place = "Veterinaria Patitas", lot = "RB-7781"))
        dao.insert(Vaccination(epochDay = t - 340, name = "Múltiple (quíntuple)", dose = "Refuerzo", nextDueEpochDay = t + 25, place = "Veterinaria Patitas"))
        dao.insert(Vaccination(epochDay = t - 80, type = "desp_interna", name = "Desparasitante en tableta", dose = "1 tableta", nextDueEpochDay = t + 10))
        dao.insert(Vaccination(epochDay = t - 170, type = "desp_interna", name = "Desparasitante en tableta", dose = "1 tableta", nextDueEpochDay = t - 80))
        dao.insert(Vaccination(epochDay = t - 20, type = "desp_externa", name = "Pipeta", nextDueEpochDay = t + 10, notes = "Marca nueva, sin irritación."))
        dao.insert(Vaccination(epochDay = t - 700, name = "Puppy (moquillo y parvovirus)", dose = "1.ª dosis", place = "Veterinaria Patitas"))
        dao.insert(Vaccination(epochDay = t - 300, type = "otro", name = "Revisión preventiva", nextDueEpochDay = t + 65))
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1500dp-xhdpi")
    fun personList() {
        seedPerson()
        shotWhen("vaccines_person", "Pendientes y próximas") { VaccinesScreen(rememberNavController()) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1900dp-xhdpi")
    fun petList() {
        seedDog()
        shotWhen("vaccines_pet", "Pulgas y garrapatas") { VaccinesScreen(rememberNavController()) }
    }

    @Test
    fun petListDark() {
        seedDog()
        shotWhen("vaccines_pet_dark", "Pulgas y garrapatas", dark = true) { VaccinesScreen(rememberNavController()) }
    }

    @Test
    fun emptyPerson() = shotWhen("vaccines_empty_person", "cartilla de vacunación") { VaccinesScreen(rememberNavController()) }

    @Test
    fun emptyPet() {
        asPet("gato", "Garfield")
        shotWhen("vaccines_empty_pet_dark", "Sin vacunas", dark = true) { VaccinesScreen(rememberNavController()) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1900dp-xhdpi")
    fun editNewPerson() = shotWhen("vaccines_edit_new_person", "Guardar registro") { VaccineEditScreen(rememberNavController(), id = null) }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1900dp-xhdpi")
    fun editExistingPet() {
        seedDog()
        val id = runBlocking { Savia.db.vaccinations().getAll().first { it.name == "Rabia" }.id }
        shotWhen("vaccines_edit_pet", "Veterinaria o clínica") { VaccineEditScreen(rememberNavController(), id = id) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1900dp-xhdpi")
    fun editExistingPetDark() {
        seedDog()
        val id = runBlocking { Savia.db.vaccinations().getAll().first { it.name == "Pipeta" }.id }
        shotWhen("vaccines_edit_pet_dark", "Veterinaria o clínica", dark = true) { VaccineEditScreen(rememberNavController(), id = id) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1900dp-xhdpi")
    fun editNewPetPrefilledFromPending() {
        seedDog()
        // Lo que hace la lista al tocar "Registrar aplicación" en un pendiente.
        app.savia.ui.vaccines.VaccineDraft.put(app.savia.ui.vaccines.VaccineDraft.Draft("vacuna", "Rabia", "Anual", "Veterinaria Patitas"))
        shotWhen("vaccines_edit_new_pet_prefilled", "Sugerido: en 1 año") { VaccineEditScreen(rememberNavController(), id = null) }
        org.junit.Assert.assertNull(app.savia.ui.vaccines.VaccineDraft.take())
    }

    @Test
    fun seededPendingMatchesExpectation() = runBlocking {
        seedDog()
        val up = app.savia.ui.vaccines.VaccineLogic.upcoming(Savia.db.vaccinations().getAll(), t)
        // La desparasitación de hace 170 días quedó cumplida por la de hace 80.
        assertEquals(listOf("Rabia", "Desparasitante en tableta", "Pipeta", "Múltiple (quíntuple)", "Revisión preventiva"), up.map { it.vaccination.name })
    }
}
