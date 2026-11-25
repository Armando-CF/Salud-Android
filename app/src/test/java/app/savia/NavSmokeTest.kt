package app.savia

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import app.savia.data.SaviaDb
import app.savia.ui.nav.AppNav
import app.savia.ui.theme.SaviaTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Recorre la app real (AppNav) abriendo cada pantalla para detectar fallos al navegar. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class NavSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        Savia.init(ctx, SaviaDb.createInMemory(ctx))
    }

    private fun start() {
        compose.setContent { SaviaTheme("light") { Surface { AppNav() } } }
        compose.waitForIdle()
    }

    private fun clickable(text: String): SemanticsMatcher = hasText(text, substring = true) and hasClickAction()

    private fun waitFor(matcher: SemanticsMatcher) {
        compose.waitUntil(15_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun tap(text: String, scroll: Boolean = false) {
        waitFor(clickable(text))
        val node = compose.onAllNodes(clickable(text)).onFirst()
        if (scroll) node.performScrollTo()
        node.performClick()
        compose.waitForIdle()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun visitsEveryScreenWithData() {
        DemoData.seed()
        start()
        waitFor(hasText("Síntomas recientes"))
        for (tab in listOf("Síntomas", "Ciclo", "Cuerpo", "Más", "Hoy")) tap(tab)
        tap("Más")
        val rows = listOf(
            "Mediciones" to "Peso", "Gasto de energía" to "Tu gasto diario aproximado", "Bienestar diario" to "Bienestar", "Medicamentos" to "Loratadina", "Citas médicas" to "Revisión anual",
            "Estudios y chequeos" to "Química sanguínea", "Autoexploración" to "Autoexploración", "Mi ficha" to "Alex",
            "Exportar y respaldo" to "Exportar", "Ajustes" to "Ajustes",
        )
        for ((row, expect) in rows) {
            tap(row, scroll = true)
            waitFor(hasText(expect, substring = true))
            back()
            waitFor(clickable("Exportar y respaldo"))
        }
        // Detalles
        tap("Mediciones", scroll = true)
        tap("Presión arterial", scroll = true)
        waitFor(hasText("mmHg", substring = true))
        back(); back()
        tap("Estudios y chequeos", scroll = true)
        tap("Química sanguínea")
        waitFor(hasText("Glucosa", substring = true))
        back(); back()
        tap("Ciclo")
        compose.onAllNodesWithContentDescription("Estadísticas", substring = true).onFirst().performClick()
        compose.waitForIdle()
        back()
    }

    @Test
    fun logsASymptomFromTheBodyAtlas() {
        runBlocking { Savia.prefs.cycleEnabled.value = false }
        start()
        waitFor(hasText("Síntomas") and hasClickAction())
        compose.onAllNodes(hasText("Síntomas") and hasClickAction()).onFirst().performClick()
        compose.waitForIdle()
        // Nuevo síntoma desde el botón flotante
        waitFor(hasClickAction() and (hasText("Registrar", substring = true) or hasText("Agregar", substring = true) or hasText("Nuevo", substring = true)))
        compose.onAllNodes(hasClickAction() and (hasText("Registrar", substring = true) or hasText("Agregar", substring = true) or hasText("Nuevo", substring = true))).onFirst().performClick()
        compose.waitForIdle()
        waitFor(hasSetTextAction())
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Dolor de rodilla")
        tap("Elegir en el cuerpo", scroll = true)
        waitFor(hasText("Toca la zona donde sientes la molestia", substring = true))
        // Toca el centro de la figura (torso) y usa esa zona
        compose.onRoot().performTouchInput { click(Offset(width / 2f, height * 0.42f)) }
        compose.waitForIdle()
        tap("Usar esta zona")
        tap("Guardar", scroll = true)
        compose.waitUntil(15_000) { runBlocking { Savia.db.symptoms().getAll().isNotEmpty() } }
        val saved = runBlocking { Savia.db.symptoms().getAll() }
        assertEquals(1, saved.size)
        assertTrue(saved[0].name.contains("rodilla"))
        assertTrue("zona elegida: ${saved[0].regionId} / ${saved[0].regionLabel}", !saved[0].regionId.isNullOrEmpty() && !saved[0].regionLabel.isNullOrEmpty())
        println("ZONA ELEGIDA: ${saved[0].regionId} / ${saved[0].regionLabel}")
    }
}
