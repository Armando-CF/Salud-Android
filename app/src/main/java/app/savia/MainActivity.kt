package app.savia

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.savia.reminders.ReminderScheduler
import app.savia.ui.profile.OnboardingScreen
import app.savia.ui.lock.AppLockGate
import app.savia.ui.nav.AppNav
import app.savia.ui.theme.SaviaTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Savia.io { ReminderScheduler.rescheduleAll(applicationContext) }
        setContent {
            val themeMode by Savia.prefs.themeMode.flow.collectAsStateWithLifecycle()
            val onboarded by Savia.prefs.onboardingDone.flow.collectAsStateWithLifecycle()
            // Iconos de las barras del sistema acordes al tema elegido en la app (no solo al del teléfono).
            val dark = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            SaviaTheme(themeMode) {
                if (!onboarded) {
                    OnboardingScreen(onDone = { Savia.prefs.onboardingDone.value = true })
                } else {
                    AppLockGate(this) { AppNav() }
                }
            }
        }
    }
}
