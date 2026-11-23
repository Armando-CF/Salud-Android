package app.savia.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.savia.MainActivity
import app.savia.R
import app.savia.Savia
import app.savia.cycle.CycleEngine
import app.savia.data.MedIntake
import app.savia.ui.meds.MedSchedule
import app.savia.util.today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Programa los recordatorios (medicamentos, citas, periodo, autoexploración).
 *
 * Funciona con UNA sola alarma que siempre apunta al siguiente aviso pendiente. Cuando suena,
 * [ReminderReceiver] publica las notificaciones que tocan y vuelve a programar la siguiente.
 */
object ReminderScheduler {
    internal const val ACTION_ALARM = "app.savia.reminders.ALARM"
    internal const val ACTION_TAKEN = "app.savia.reminders.TAKEN"
    internal const val EXTRA_MED_ID = "med_id"
    internal const val EXTRA_DAY = "day"
    internal const val EXTRA_SLOT = "slot"
    internal const val EXTRA_NOTIFICATION_ID = "notification_id"

    private const val SP = "savia_reminders"
    private const val KEY_LAST_CHECK = "last_check"
    private const val KEY_SELF_EXAM_ANCHOR = "self_exam_anchor"
    private const val ALARM_REQUEST = 7001

    /** Al abrir la app o cambiar datos solo se recuperan avisos muy recientes (alarma retrasada). */
    private const val LOOKBACK_APP_MS = 10 * 60 * 1000L

    /** Al sonar la alarma o reiniciar el teléfono se recuperan avisos de hasta 12 h atrás. */
    internal const val LOOKBACK_ALARM_MS = 12 * 60 * 60 * 1000L

    /** Una toma con más de 6 h de retraso ya no se avisa. */
    private const val MED_MAX_AGE_MS = 6 * 60 * 60 * 1000L
    private const val AHEAD_MS = 60 * 1000L

    private val mutex = Mutex()

    internal fun ensureInit(app: Context) {
        if (!Savia.isReady) Savia.init(app)
    }

    /**
     * Recalcula y reprograma todo. Llamar al cambiar medicamentos/citas/ajustes y al arrancar.
     * Se puede llamar desde cualquier hilo: el trabajo se hace en segundo plano.
     */
    fun rescheduleAll(context: Context) {
        val app = context.applicationContext
        ensureInit(app)
        Savia.io { runCatching { sync(app, LOOKBACK_APP_MS) } }
    }

    /** Publica los avisos vencidos en la ventana y programa la alarma del siguiente. */
    internal suspend fun sync(app: Context, lookbackMs: Long) = mutex.withLock {
        val sp = app.getSharedPreferences(SP, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val until = now + AHEAD_MS
        val last = sp.getLong(KEY_LAST_CHECK, now)
        // Si el reloj se movió hacia atrás, "last" queda en el futuro: se parte de ahora.
        val after = if (last > until) now else maxOf(last, now - lookbackMs)

        val input = loadInput(app, after, until)
        for (e in ReminderPlanner.eventsBetween(input, after, until)) {
            if (e.kind == ReminderKind.MED && e.at < now - MED_MAX_AGE_MS) continue
            ReminderNotifier.show(app, e)
        }
        sp.edit().putLong(KEY_LAST_CHECK, until).apply()
        setAlarm(app, ReminderPlanner.nextEventTime(input, until))
    }

    private suspend fun loadInput(app: Context, after: Long, until: Long): ReminderInput {
        val db = Savia.db
        val prefs = Savia.prefs
        val sp = app.getSharedPreferences(SP, Context.MODE_PRIVATE)
        val today = today()

        val meds = db.medications().getAll()
        val intakes = ArrayList<MedIntake>()
        if (meds.isNotEmpty()) {
            val from = maxOf(MedSchedule.dayOf(after), today - 2)
            for (d in from..(MedSchedule.dayOf(until) + 1)) intakes += db.medications().getIntakesOfDay(d)
        }

        val periodStarts = if (prefs.cycleEnabled.value && prefs.periodReminder.value) {
            CycleEngine.analyze(
                db.cycle().getAll(), prefs.cycleLength.value, prefs.periodLength.value, prefs.lutealLength.value, today,
            ).predictedStarts
        } else {
            emptyList()
        }

        val selfExamBase: Long? = if (prefs.selfExamReminder.value) {
            val lastExam = db.selfExams().getAll().maxOfOrNull { it.epochDay }
            if (lastExam != null) {
                if (sp.contains(KEY_SELF_EXAM_ANCHOR)) sp.edit().remove(KEY_SELF_EXAM_ANCHOR).apply()
                lastExam
            } else {
                // Sin registros: el primer aviso es mañana y luego cada 30 días.
                val fresh = today + 1 - ReminderPlanner.SELF_EXAM_EVERY_DAYS
                if (!sp.contains(KEY_SELF_EXAM_ANCHOR)) sp.edit().putLong(KEY_SELF_EXAM_ANCHOR, fresh).apply()
                sp.getLong(KEY_SELF_EXAM_ANCHOR, fresh)
            }
        } else {
            if (sp.contains(KEY_SELF_EXAM_ANCHOR)) sp.edit().remove(KEY_SELF_EXAM_ANCHOR).apply()
            null
        }

        return ReminderInput(meds, intakes, db.appointments().getAll(), periodStarts, selfExamBase)
    }

    private fun setAlarm(app: Context, at: Long?) {
        val am = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = PendingIntent.getBroadcast(
            app, ALARM_REQUEST,
            Intent(app, ReminderReceiver::class.java).setAction(ACTION_ALARM),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (at == null) {
            am.cancel(pi)
            return
        }
        try {
            val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            if (exact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (_: SecurityException) {
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
        }
    }

    /** ¿La app puede mostrar notificaciones ahora mismo? (permiso en Android 13+ y no bloqueadas). */
    fun notificationsAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Quita la notificación de una toma (p. ej. al marcarla como tomada dentro de la app). */
    fun cancelDoseNotification(context: Context, medId: Long, epochDay: Long, slot: String) {
        val id = ReminderEvent(0, ReminderKind.MED, medId, epochDay, slot, "", "").notificationId
        runCatching { NotificationManagerCompat.from(context.applicationContext).cancel(id) }
    }
}

/** Canales y publicación de notificaciones. */
internal object ReminderNotifier {
    private const val CH_MEDS = "meds"
    private const val CH_APPOINTMENTS = "appointments"
    private const val CH_HEALTH = "health"
    private const val COLOR = 0xFF1B6B58.toInt()

    private fun ensureChannels(app: Context) {
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_MEDS, "Medicamentos", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Avisos a la hora de cada toma"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_APPOINTMENTS, "Citas médicas", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Avisos un día antes y 2 horas antes de tus citas"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_HEALTH, "Recordatorios de salud", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Ciclo menstrual y autoexploración"
            },
        )
    }

    fun show(app: Context, e: ReminderEvent) {
        if (!ReminderScheduler.notificationsAllowed(app)) return
        ensureChannels(app)
        val open = PendingIntent.getActivity(
            app, 0,
            Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val isAppointment = e.kind == ReminderKind.APPT_DAY_BEFORE || e.kind == ReminderKind.APPT_SOON
        val channel = when {
            e.kind == ReminderKind.MED -> CH_MEDS
            isAppointment -> CH_APPOINTMENTS
            else -> CH_HEALTH
        }
        val b = NotificationCompat.Builder(app, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(COLOR)
            .setContentTitle(e.title)
            .setContentText(e.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(e.text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setWhen(e.at)
            .setShowWhen(true)
            .setCategory(if (isAppointment) NotificationCompat.CATEGORY_EVENT else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (channel == CH_HEALTH) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_HIGH)

        if (e.kind == ReminderKind.PERIOD || e.kind == ReminderKind.SELF_EXAM) {
            // En la pantalla de bloqueo solo se muestra un texto genérico.
            b.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            b.setPublicVersion(
                NotificationCompat.Builder(app, channel)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setColor(COLOR)
                    .setContentTitle("Savia")
                    .setContentText("Tienes un recordatorio de salud")
                    .build(),
            )
        }
        if (e.kind == ReminderKind.MED) {
            val taken = PendingIntent.getBroadcast(
                app, e.notificationId,
                Intent(app, ReminderReceiver::class.java)
                    .setAction(ReminderScheduler.ACTION_TAKEN)
                    .putExtra(ReminderScheduler.EXTRA_MED_ID, e.refId)
                    .putExtra(ReminderScheduler.EXTRA_DAY, e.epochDay)
                    .putExtra(ReminderScheduler.EXTRA_SLOT, e.slot)
                    .putExtra(ReminderScheduler.EXTRA_NOTIFICATION_ID, e.notificationId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            b.addAction(0, "Tomada", taken)
        }
        try {
            NotificationManagerCompat.from(app).notify(e.notificationId, b.build())
        } catch (_: SecurityException) {
            // Sin permiso de notificaciones: no se avisa.
        }
    }
}

/** Recibe la alarma del siguiente aviso y la acción "Tomada" de las notificaciones. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending: PendingResult? = goAsync()
        ReminderScheduler.ensureInit(app)
        Savia.scope.launch(Dispatchers.IO) {
            try {
                if (intent.action == ReminderScheduler.ACTION_TAKEN) {
                    markTaken(app, intent)
                } else {
                    ReminderScheduler.sync(app, ReminderScheduler.LOOKBACK_ALARM_MS)
                }
            } catch (_: Throwable) {
            } finally {
                pending?.finish()
            }
        }
    }

    private suspend fun markTaken(app: Context, intent: Intent) {
        val medId = intent.getLongExtra(ReminderScheduler.EXTRA_MED_ID, -1)
        val day = intent.getLongExtra(ReminderScheduler.EXTRA_DAY, -1)
        val slot = intent.getStringExtra(ReminderScheduler.EXTRA_SLOT).orEmpty()
        val notificationId = intent.getIntExtra(ReminderScheduler.EXTRA_NOTIFICATION_ID, 0)
        if (medId > 0 && day >= 0 && slot.isNotEmpty()) {
            val dao = Savia.db.medications()
            val exists = dao.getIntakesOfDay(day).any { it.medicationId == medId && it.slot == slot }
            if (!exists && dao.get(medId) != null) {
                dao.insertIntake(
                    MedIntake(medicationId = medId, epochDay = day, slot = slot, timestamp = System.currentTimeMillis(), status = MedSchedule.TAKEN),
                )
            }
        }
        runCatching { NotificationManagerCompat.from(app).cancel(notificationId) }
    }
}

/** Al reiniciar el teléfono, actualizar la app o cambiar la hora, se reprograma todo. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending: PendingResult? = goAsync()
        ReminderScheduler.ensureInit(app)
        Savia.scope.launch(Dispatchers.IO) {
            try {
                ReminderScheduler.sync(app, ReminderScheduler.LOOKBACK_ALARM_MS)
            } catch (_: Throwable) {
            } finally {
                pending?.finish()
            }
        }
    }
}
