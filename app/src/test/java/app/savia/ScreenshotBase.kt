package app.savia

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import app.savia.data.SaviaDb
import app.savia.ui.theme.SaviaTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Base para "pruebas" que renderizan pantallas a PNG (sin emulador) en app/build/shots/.
 * Ejecutar una clase:  ./gradlew :app:testDebugUnitTest --tests 'app.savia.MiClaseShots'
 *
 * Cada método @Test puede llamar a [shot] UNA sola vez. La base de datos es en memoria y
 * empieza vacía en cada prueba: siembra los datos que necesites con `runBlocking { Savia.db... }`
 * ANTES de llamar a [shot].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
abstract class ScreenshotBase {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUpSavia() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        Savia.init(ctx, SaviaDb.createInMemory(ctx))
    }

    fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            SaviaTheme(if (dark) "dark" else "light") { Surface { content() } }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/shots/$name.png")
    }
}
