package app.savia

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.rememberNavController
import app.savia.data.Profile
import app.savia.ui.components.DismissKeyboardOnTapOutside
import app.savia.ui.profile.NewProfileScreen
import app.savia.ui.profile.OnboardingContent
import app.savia.ui.profile.ProfileScreen
import app.savia.ui.profile.ProfilesScreen
import app.savia.ui.profile.SettingsScreen
import app.savia.ui.theme.SaviaTheme
import app.savia.util.today
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test
import org.robolectric.annotation.Config

/** Capturas y flujos de perfiles múltiples (familia y mascotas) y pruebas de teclado de la zona de perfil. */
class ProfilesShots : ScreenshotBase() {

    /** Perfil 1 = Alex (datos de ejemplo), más una hija, un perro y un gato. Devuelve los ids nuevos. */
    private fun seedFamily(): List<Int> = runBlocking {
        DemoData.seed()
        val t = today()
        Savia.db.profile().save(Savia.db.profile().get()!!.copy(relation = "Yo"))
        val kid = Savia.createProfile(Profile(name = "Sofía", sex = "F", relation = "Hija", birthEpochDay = t - 365L * 8 - 120, heightCm = 128.0, avatarColor = 1))
        val dog = Savia.createProfile(
            Profile(
                name = "Firulais", kind = "pet", species = "perro", breed = "Mestizo", sex = "M", birthEpochDay = t - 365L * 3 - 70,
                sterilized = true, microchip = "985 112 004 567 890", markings = "Café con el pecho blanco", allergies = "Pollo",
                doctorName = "Clínica Huellitas", doctorPhone = "55 2222 3333", goalWeightKg = 14.0, avatarColor = 3,
            ),
        )
        val cat = Savia.createProfile(Profile(name = "Garfield", kind = "pet", species = "gato", sex = "M", avatarColor = 6))
        listOf(kid, dog, cat)
    }

    @Test
    fun profilesList() {
        seedFamily()
        shotWhen("profiles_lista", waitText = "Garfield") { ProfilesScreen(rememberNavController()) }
    }

    @Test
    fun profilesListDarkPetActive() {
        val (_, dog, _) = seedFamily()
        Savia.switchTo(dog)
        shotWhen("profiles_lista_dark", waitText = "Garfield", dark = true) { ProfilesScreen(rememberNavController()) }
    }

    @Test
    fun profilesListOnlyMe() = shotWhen("profiles_lista_solo", waitText = "Agregar mascota") { ProfilesScreen(rememberNavController()) }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1500dp-xhdpi")
    fun newPerson() = shot("profiles_nueva_persona") { NewProfileScreen(rememberNavController(), kind = "person") }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1700dp-xhdpi")
    fun newPet() = shot("profiles_nueva_mascota") { NewProfileScreen(rememberNavController(), kind = "pet") }

    @Test
    fun newPetDark() = shot("profiles_nueva_mascota_dark", dark = true) { NewProfileScreen(rememberNavController(), kind = "pet") }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h2300dp-xhdpi")
    fun petSheet() {
        val (_, dog, _) = seedFamily()
        Savia.switchTo(dog)
        shotWhen("profiles_ficha_mascota", waitText = "Ficha de mascota") { ProfileScreen(rememberNavController()) }
    }

    @Test
    fun petSheetDark() {
        val (_, _, cat) = seedFamily()
        Savia.switchTo(cat)
        shotWhen("profiles_ficha_mascota_dark", waitText = "Ficha de mascota", dark = true) { ProfileScreen(rememberNavController()) }
    }

    @Test
    fun kidSheet() {
        val (kid, _, _) = seedFamily()
        Savia.switchTo(kid)
        shotWhen("profiles_ficha_hija", waitText = "Ficha de Sofía") { ProfileScreen(rememberNavController()) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1700dp-xhdpi")
    fun settingsForPet() {
        val (_, dog, _) = seedFamily()
        Savia.switchTo(dog)
        shotWhen("profiles_ajustes_mascota", waitText = "Viendo a Firulais") { SettingsScreen(rememberNavController()) }
    }

    /**
     * Deja terminar las consultas en curso (los flujos de Room siguen emitiendo un instante después del
     * último cambio) antes de que la siguiente prueba reinicie Savia y cierre estas bases en memoria.
     */
    @After
    fun settle() {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        Thread.sleep(200)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    // ───────── Flujos ─────────

    /** El cambio de perfil se publica en el hilo principal desde un hilo de fondo: hay que dejar correr el looper. */
    private fun waitForNewActiveProfile() {
        compose.waitUntil(15_000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            Savia.profileIds.size == 2 && Savia.activeId.value != 1
        }
    }

    private fun waitFor(text: String) {
        compose.waitUntil(15_000) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun creatingAPetCreatesTheProfileAndSwitchesToIt() {
        compose.setContent { SaviaTheme("light") { DismissKeyboardOnTapOutside { NewProfileScreen(rememberNavController(), kind = "pet") } } }
        // Sin nombre no se guarda.
        compose.onAllNodes(hasText("Crear perfil de mascota") and hasClickAction()).onFirst().performScrollTo().performClick()
        waitFor("Falta el nombre")
        assertEquals(listOf(1), Savia.profileIds)

        val nameField = compose.onAllNodes(hasSetTextAction() and hasText("Nombre")).onFirst()
        nameField.performScrollTo().performClick()
        nameField.performTextInput("Firulais")
        nameField.assertIsFocused()
        // Tocar un chip cierra el teclado (el campo pierde el foco) y elige la especie.
        compose.onAllNodes(hasText("Gato") and hasClickAction()).onFirst().performClick()
        nameField.assertIsNotFocused()
        compose.onAllNodes(hasText("Crear perfil de mascota") and hasClickAction()).onFirst().performScrollTo().performClick()

        waitForNewActiveProfile()
        val id = Savia.activeId.value
        val p = runBlocking { Savia.dbFor(id).profile().get()!! }
        assertTrue(p.isPet)
        assertEquals("Firulais", p.name)
        assertEquals("gato", p.species)
        assertTrue(Savia.isPet)
        assertFalse(Savia.prefsFor(id).cycleEnabled.value)
    }

    @Test
    fun creatingAPersonKeepsCycleOffUnlessSuggested() {
        compose.setContent { SaviaTheme("light") { DismissKeyboardOnTapOutside { NewProfileScreen(rememberNavController(), kind = "person") } } }
        val nameField = compose.onAllNodes(hasSetTextAction() and hasText("Nombre")).onFirst()
        nameField.performClick()
        nameField.performTextInput("Sofía")
        compose.onAllNodes(hasText("Hija") and hasClickAction()).onFirst().performClick()
        compose.onAllNodes(hasText("Mujer") and hasClickAction()).onFirst().performScrollTo().performClick()
        compose.onAllNodes(hasText("Crear perfil") and hasClickAction()).onFirst().performScrollTo().performClick()
        waitForNewActiveProfile()
        val id = Savia.activeId.value
        val p = runBlocking { Savia.dbFor(id).profile().get()!! }
        assertEquals("Sofía", p.name)
        assertEquals("Hija", p.relation)
        assertEquals("F", p.sex)
        assertFalse(p.isPet)
        // Mujer sin fecha de nacimiento → se propone el ciclo; el modelo del atlas sigue al sexo.
        assertTrue(Savia.prefsFor(id).cycleEnabled.value)
        assertEquals("F", Savia.prefsFor(id).atlasSex.value)
        // El perfil 1 no cambia.
        assertFalse(Savia.prefsFor(1).cycleEnabled.value)
    }

    @Test
    fun switchingProfileFromTheList() {
        val (kid, _, _) = seedFamily()
        compose.setContent { SaviaTheme("light") { ProfilesScreen(rememberNavController()) } }
        waitFor("Garfield")
        assertEquals(1, Savia.activeId.value)
        compose.onAllNodes(hasText("Sofía", substring = true) and hasClickAction()).onFirst().performClick()
        compose.waitUntil(15_000) { Savia.activeId.value == kid }
        assertEquals("Sofía", Savia.active.value.displayName)
    }

    /** El problema reportado: en la bienvenida el teclado se quedaba abierto tapando los campos de abajo. */
    @Test
    fun onboardingKeyboardClosesWhenTappingAChip() {
        compose.setContent { SaviaTheme("light") { DismissKeyboardOnTapOutside { OnboardingContent(onDone = {}, initialStep = 1) } } }
        val nameField = compose.onAllNodes(hasSetTextAction() and hasText("¿Cómo te llamas?")).onFirst()
        nameField.performClick()
        nameField.performTextInput("Armando")
        nameField.assertIsFocused()
        compose.onAllNodes(hasText("Hombre") and hasClickAction()).onFirst().performClick()
        nameField.assertIsNotFocused()
        // El chip sí quedó elegido y lo escrito se conserva.
        waitFor("Armando")
        // Tocar el campo de fecha (un selector) tampoco deja el teclado abierto.
        nameField.performClick()
        nameField.assertIsFocused()
        compose.onAllNodes(hasText("Todo es opcional", substring = true)).onFirst().performClick()
        nameField.assertIsNotFocused()
    }
}
