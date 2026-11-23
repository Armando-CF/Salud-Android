package app.savia

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Arranque real de la app: bienvenida → pantalla de inicio. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class MainActivityTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun onboardingLeadsToHome() {
        val skip = hasText("Omitir") and hasClickAction()
        compose.waitUntil(15_000) { compose.onAllNodes(skip).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(skip).onFirst().performClick()
        compose.waitForIdle()
        compose.waitUntil(15_000) { compose.onAllNodes(hasText("Empieza por aquí")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(Savia.prefs.onboardingDone.value)
    }
}
