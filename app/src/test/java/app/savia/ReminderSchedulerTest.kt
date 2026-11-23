package app.savia

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.savia.data.Appointment
import app.savia.data.Medication
import app.savia.data.SaviaDb
import app.savia.reminders.ReminderEvent
import app.savia.reminders.ReminderKind
import app.savia.reminders.ReminderReceiver
import app.savia.reminders.ReminderScheduler
import app.savia.ui.meds.MedSchedule
import app.savia.util.formatHm
import app.savia.util.millisToDateTime
import app.savia.util.millisToEpochDay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Prueba de humo de la parte Android de los recordatorios (alarma + notificaciones) con Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReminderSchedulerTest {
    private lateinit var ctx: Context
    private val minute = 60_000L

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        Savia.init(ctx, SaviaDb.createInMemory(ctx))
        shadowOf(ctx as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ctx.getSharedPreferences("savia_reminders", Context.MODE_PRIVATE).edit().clear().commit()
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancelAll()
    }

    private fun alarms() = shadowOf(ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
    private fun notifications() = shadowOf(ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).allNotifications

    private fun slotOf(millis: Long): String = millis.millisToDateTime().let { formatHm(it.hour * 60 + it.minute) }

    @Test
    fun schedulesOneAlarmForTheNextDose() = runBlocking {
        val target = System.currentTimeMillis() + 5 * minute
        val day = target.millisToEpochDay()
        val slot = slotOf(target)
        Savia.db.medications().insert(Medication(name = "Loratadina", dose = "10 mg", times = slot, startEpochDay = day - 1))
        ReminderScheduler.sync(ctx, 10 * minute)

        val scheduled = alarms().scheduledAlarms
        assertEquals(1, scheduled.size)
        assertEquals(MedSchedule.millisOf(day, MedSchedule.parseTimes(slot)[0]), scheduled[0].triggerAtMs)
        assertEquals(AlarmManager.RTC_WAKEUP, scheduled[0].type)
        // Aún no toca: no hay notificaciones.
        assertTrue(notifications().isEmpty())
    }

    @Test
    fun notifiesDueDoseOnceAndReschedules() = runBlocking {
        val now = System.currentTimeMillis()
        val due = now - 2 * minute
        val day = due.millisToEpochDay()
        val slot = slotOf(due)
        val medId = Savia.db.medications().insert(Medication(name = "Loratadina", dose = "10 mg", times = slot, startEpochDay = day - 1))
        // Última revisión hace 5 minutos: la toma de hace 2 minutos está pendiente de avisar.
        ctx.getSharedPreferences("savia_reminders", Context.MODE_PRIVATE).edit().putLong("last_check", now - 5 * minute).commit()

        ReminderScheduler.sync(ctx, 12 * 60 * minute)
        val shown = notifications()
        assertEquals(1, shown.size)
        assertEquals("Hora de tu medicamento", shadowOf(shown[0]).contentTitle.toString())
        assertEquals("Loratadina 10 mg · $slot", shadowOf(shown[0]).contentText.toString())
        assertEquals(1, shown[0].actions.size)
        assertEquals("Tomada", shown[0].actions[0].title.toString())
        // La siguiente alarma es la misma toma del día siguiente.
        val next = alarms().scheduledAlarms.single()
        assertEquals(MedSchedule.millisOf(day + 1, MedSchedule.parseTimes(slot)[0]), next.triggerAtMs)

        // Una segunda pasada no repite el aviso.
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancelAll()
        ReminderScheduler.sync(ctx, 12 * 60 * minute)
        assertTrue(notifications().isEmpty())

        // Acción "Tomada" de la notificación: registra la toma.
        val id = ReminderEvent(0, ReminderKind.MED, medId, day, slot, "", "").notificationId
        ReminderReceiver().onReceive(
            ctx,
            Intent(ctx, ReminderReceiver::class.java).setAction("app.savia.reminders.TAKEN")
                .putExtra("med_id", medId).putExtra("day", day).putExtra("slot", slot).putExtra("notification_id", id),
        )
        var intake = Savia.db.medications().getIntakesOfDay(day).firstOrNull()
        var tries = 0
        while (intake == null && tries++ < 50) {
            Thread.sleep(40)
            intake = Savia.db.medications().getIntakesOfDay(day).firstOrNull()
        }
        assertNotNull(intake)
        assertEquals(slot, intake!!.slot)
        assertEquals("taken", intake.status)
    }

    @Test
    fun doesNotNotifyRegisteredDosesAndCancelsAlarmWhenNothingPending() = runBlocking {
        val now = System.currentTimeMillis()
        // Cita que ya pasó y medicamento sin recordatorio: no hay nada que programar.
        Savia.db.appointments().insert(Appointment(timestamp = now - 60 * minute, title = "Revisión"))
        Savia.db.medications().insert(Medication(name = "Vitamina D", times = "08:00", startEpochDay = now.millisToEpochDay() - 3, remind = false))
        ReminderScheduler.sync(ctx, 10 * minute)
        assertNull(alarms().peekNextScheduledAlarm())
        assertTrue(notifications().isEmpty())
    }

    @Test
    fun appointmentTwoHoursBeforeIsScheduled() = runBlocking {
        val now = System.currentTimeMillis()
        val appt = now + 5 * 60 * minute
        Savia.db.appointments().insert(Appointment(timestamp = appt, title = "Revisión anual"))
        ReminderScheduler.sync(ctx, 10 * minute)
        assertEquals(appt - 2 * 60 * minute, alarms().scheduledAlarms.single().triggerAtMs)
    }

    @Test
    fun withoutPermissionNothingIsPosted() = runBlocking {
        shadowOf(ctx as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val now = System.currentTimeMillis()
        val due = now - 2 * minute
        Savia.db.medications().insert(Medication(name = "Loratadina", times = slotOf(due), startEpochDay = due.millisToEpochDay() - 1))
        ctx.getSharedPreferences("savia_reminders", Context.MODE_PRIVATE).edit().putLong("last_check", now - 5 * minute).commit()
        ReminderScheduler.sync(ctx, 12 * 60 * minute)
        assertTrue(notifications().isEmpty())
        // Aun sin permiso, la alarma se sigue programando (por si se concede después).
        assertNotNull(alarms().peekNextScheduledAlarm())
    }
}
