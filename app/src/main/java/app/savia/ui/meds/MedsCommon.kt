package app.savia.ui.meds

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.savia.Savia
import app.savia.data.MedIntake
import app.savia.reminders.ReminderScheduler
import kotlinx.coroutines.delay

/** Acciones sobre tomas, reutilizables desde cualquier pantalla (p. ej. la de inicio). */
object MedActions {
    /**
     * Registra una toma programada. [status] = "taken", "skipped" o null para deshacer el registro.
     * Quita la notificación de esa toma y reprograma recordatorios.
     */
    fun setDose(context: Context, medId: Long, epochDay: Long, slot: String, status: String?) {
        val app = context.applicationContext
        Savia.io {
            val dao = Savia.db.medications()
            dao.deleteIntakeSlot(medId, epochDay, slot)
            if (status != null) {
                dao.insertIntake(
                    MedIntake(medicationId = medId, epochDay = epochDay, slot = slot, timestamp = System.currentTimeMillis(), status = status),
                )
                ReminderScheduler.cancelDoseNotification(app, medId, epochDay, slot)
            }
            ReminderScheduler.rescheduleAll(app)
        }
    }

    /** Marca una toma programada como tomada. */
    fun take(context: Context, dose: DoseItem, epochDay: Long) = setDose(context, dose.med.id, epochDay, dose.slot, MedSchedule.TAKEN)

    /** Marca una toma programada como omitida. */
    fun skip(context: Context, dose: DoseItem, epochDay: Long) = setDose(context, dose.med.id, epochDay, dose.slot, MedSchedule.SKIPPED)

    /** Deshace el registro de una toma programada. */
    fun undo(context: Context, dose: DoseItem, epochDay: Long) = setDose(context, dose.med.id, epochDay, dose.slot, null)

    /** Registra ahora una toma libre (medicamentos "según sea necesario"). */
    fun logNow(medId: Long, epochDay: Long) {
        Savia.io {
            Savia.db.medications().insertIntake(
                MedIntake(medicationId = medId, epochDay = epochDay, slot = "", timestamp = System.currentTimeMillis(), status = MedSchedule.TAKEN),
            )
        }
    }

    fun deleteIntake(context: Context, intake: MedIntake) {
        val app = context.applicationContext
        Savia.io {
            Savia.db.medications().deleteIntake(intake)
            ReminderScheduler.rescheduleAll(app)
        }
    }
}

/** Hora actual en milisegundos que se refresca cada medio minuto (para "atrasada", "en X días"…). */
@Composable
internal fun rememberNowMillis(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        delay(30_000)
        value = System.currentTimeMillis()
    }
}

/** Estado del permiso de notificaciones y cómo pedirlo. */
internal class NotificationAccess(
    val allowed: Boolean,
    /** Pide el permiso (Android 13+) o abre los ajustes de notificaciones de la app. */
    val request: () -> Unit,
    /** Texto del botón según lo que hará [request]. */
    val buttonLabel: String,
    /** true si [request] mostrará el diálogo de permiso del sistema (Android 13+ y aún no negado). */
    val canAsk: Boolean,
)

internal fun openNotificationSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/**
 * Observa si la app puede notificar (se vuelve a revisar al regresar a la pantalla) y ofrece
 * la acción adecuada: pedir el permiso la primera vez y, si se negó, abrir los ajustes.
 * [onResult] se llama después de que la persona responde al diálogo de permiso.
 */
@Composable
internal fun rememberNotificationAccess(onResult: (Boolean) -> Unit = {}): NotificationAccess {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(ReminderScheduler.notificationsAllowed(context)) }
    var denied by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        allowed = ReminderScheduler.notificationsAllowed(context)
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = ReminderScheduler.notificationsAllowed(context)
        if (!granted) denied = true
        if (granted) ReminderScheduler.rescheduleAll(context)
        onResult(granted)
    }
    val canAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !denied
    return NotificationAccess(
        allowed = allowed,
        request = {
            if (canAsk) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else openNotificationSettings(context)
        },
        buttonLabel = if (canAsk) "Permitir notificaciones" else "Abrir ajustes de notificaciones",
        canAsk = canAsk,
    )
}

/** Fila con título, descripción e interruptor. */
@Composable
internal fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
