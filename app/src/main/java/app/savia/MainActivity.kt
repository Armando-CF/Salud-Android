package app.savia

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.savia.reminders.ReminderScheduler
import app.savia.ui.home.OnboardingScreen
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
