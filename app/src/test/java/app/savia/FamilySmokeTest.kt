package app.savia

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import app.savia.data.SaviaDb
import app.savia.ui.components.DismissKeyboardOnTapOutside
import app.savia.ui.nav.AppNav
import app.savia.ui.theme.SaviaTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Recorrido de punta a punta con varios perfiles: cambiar entre una persona, un hijo y un perro,
 * abrir cada sección de la mascota y crear una mascota nueva desde la interfaz. Usa la misma
 * estructura que MainActivity (la navegación se recrea al cambiar de perfil).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class FamilySmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        Savia.init(ctx, SaviaDb.createInMemory(ctx))
    }

    private fun start() {
        compose.setContent {
            SaviaTheme("light") {
                Surface {
                    DismissKeyboardOnTapOutside {
                        val active by Savia.activeId.collectAsStateWithLifecycle()
                        key(active) { AppNav() }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun clickable(text: String, exact: Boolean = false): SemanticsMatcher = hasText(text, substring = !exact) and hasClickAction()

    private fun waitFor(matcher: SemanticsMatcher) {
        compose.waitUntil(20_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitText(text: String) = waitFor(hasText(text, substring = true))

    private fun tap(text: String, scroll: Boolean = false, exact: Boolean = false) {
        waitFor(clickable(text, exact))
        val node = compose.onAllNodes(clickable(text, exact)).onFirst()
        if (scroll) node.performScrollTo()
        node.performClick()
        compose.waitForIdle()
    }

    private fun idleMain() = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun switchesBetweenFamilyMembersAndPets() {
        val (kid, dog) = DemoData.seedFamily()
        start()
        // Inicio del perfil principal avisa de lo pendiente de los demás.
        waitText("Tu familia y mascotas")
        waitText("Firulais")
        waitText("Leo")

        // Cambiar al perro tocando su fila.
        tap("Firulais")
        compose.waitUntil(20_000) { Savia.activeId.value == dog }
        assertTrue(Savia.isPet)
        waitFor(clickable("Vacuna", exact = true))            // acceso rápido propio de mascotas
        waitText("Desparasitación interna")                    // aplicación vencida en Inicio
        assertTrue(compose.onAllNodes(hasText("¿Cómo te sientes hoy?")).fetchSemanticsNodes().isEmpty())

        tap("Síntomas", exact = true); waitText("Vómito")
        tap("Mediciones", exact = true); waitText("Peso")
        assertTrue("presión no aplica a mascotas", compose.onAllNodes(hasText("Presión arterial")).fetchSemanticsNodes().isEmpty())
        tap("Cuerpo", exact = true); waitText("Órganos")
        tap("Más", exact = true)
        val rows = listOf(
            "Vacunas y desparasitación" to "Rabia", "Medicamentos" to "Omeprazol", "Citas con el veterinario" to "Vacuna anual",
            "Estudios y chequeos" to "Química sanguínea", "Exportar y respaldo" to "Exportando datos de: Firulais", "Ajustes" to "Ajustes",
        )
        for ((row, expect) in rows) {
            tap(row, scroll = true)
            waitText(expect)
            back()
            waitFor(clickable("Exportar y respaldo"))
        }
        assertTrue(compose.onAllNodes(hasText("Autoexploración")).fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodes(hasText("Bienestar diario")).fetchSemanticsNodes().isEmpty())

        // Cambiar al hijo desde la lista de perfiles.
        tap("Perfiles", scroll = true, exact = true)
        waitFor(clickable("Agregar mascota"))
        tap("Leo")
        compose.waitUntil(20_000) { Savia.activeId.value == kid }
        assertFalse(Savia.isPet)
        waitText("Medicamentos de hoy")
        waitText("Paracetamol infantil")
        // Los datos no se mezclan.
        assertEquals(2, runBlocking { Savia.db.symptoms().getAll().size })
        assertEquals(3, runBlocking { Savia.dbFor(dog).symptoms().getAll().size })
        assertEquals(12, runBlocking { Savia.dbFor(1).symptoms().getAll().size })
    }

    @Test
    fun createsAPetFromTheUi() {
        DemoData.seed()
        start()
        waitText("Síntomas recientes")
        tap("Más", exact = true)
        tap("Perfiles", scroll = true, exact = true)
        tap("Agregar mascota", scroll = true)
        waitFor(hasSetTextAction())
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Garfield")
        tap("Gato", scroll = true, exact = true)
        tap("Guardar", exact = true)
        // El cambio de perfil se publica en el hilo principal desde un hilo de fondo: hay que dejarlo correr.
        compose.waitUntil(20_000) { idleMain(); Savia.profileIds.size == 2 && Savia.activeId.value != 1 }
        compose.waitUntil(20_000) { Savia.isPet }
        assertEquals("Garfield", Savia.active.value.displayName)
        assertEquals("gato", Savia.active.value.species)
        val p = runBlocking { Savia.db.profile().get()!! }
        assertEquals("pet", p.kind)
        assertFalse(Savia.prefs.cycleEnabled.value)
        // La interfaz se recreó en Inicio del perfil nuevo, vacío.
        waitText("Empieza por aquí")
        waitText("Registra sus vacunas")
        // El perfil original conserva sus datos.
        assertEquals(12, runBlocking { Savia.dbFor(1).symptoms().getAll().size })
    }
}
