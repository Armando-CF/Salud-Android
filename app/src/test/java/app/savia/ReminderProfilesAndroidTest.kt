package app.savia

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.savia.data.MedIntake
import app.savia.data.Medication
import app.savia.data.Profile
import app.savia.data.SaviaDb
import app.savia.data.Vaccination
import app.savia.reminders.ReminderEvent
import app.savia.reminders.ReminderKind
import app.savia.reminders.ReminderReceiver
import app.savia.reminders.ReminderScheduler
import app.savia.ui.meds.MedSchedule
import app.savia.util.formatHm
import app.savia.util.millisToDateTime
import app.savia.util.millisToEpochDay
import app.savia.util.today
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

/** Recordatorios con varios perfiles (parte Android: alarma, notificaciones y acción "Tomada") con Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReminderProfilesAndroidTest {
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
    private fun lastCheck(millis: Long) =
        ctx.getSharedPreferences("savia_reminders", Context.MODE_PRIVATE).edit().putLong("last_check", millis).commit()

    private inline fun waitFor(what: () -> Boolean) {
        var tries = 0
        while (!what() && tries++ < 75) Thread.sleep(40)
    }

    @Test
    fun notifiesDosesOfEveryProfileWithTheirNamesAndOneAlarm() = runBlocking {
        Savia.db.profile().save(Profile(name = "Ana"))
        val dog = Savia.createProfile(Profile(name = "Firulais", kind = "pet", species = "perro"))
        val cat = Savia.createProfile(Profile(name = "Garfield", kind = "pet", species = "gato")) // sin datos
        val now = System.currentTimeMillis()
        val due = now - 2 * minute
        val day = due.millisToEpochDay()
        val slot = slotOf(due)
        // El mismo id de medicamento (1) y el mismo horario en dos perfiles.
        Savia.db.medications().insert(Medication(name = "Loratadina", dose = "10 mg", times = slot, startEpochDay = day - 1))
        Savia.dbFor(dog).medications().insert(Medication(name = "Amoxicilina", dose = "250 mg", times = slot, startEpochDay = day - 1))
        lastCheck(now - 5 * minute)

        ReminderScheduler.sync(ctx, 12 * 60 * minute)

        val titles = notifications().map { shadowOf(it).contentTitle.toString() }.toSet()
        assertEquals(setOf("Ana · Loratadina $slot", "Firulais · Amoxicilina $slot"), titles)
        val dogNote = notifications().first { shadowOf(it).contentTitle.toString().startsWith("Firulais") }
        assertEquals("Hora del medicamento: Amoxicilina 250 mg", shadowOf(dogNote).contentText.toString())
        assertEquals("Tomada", dogNote.actions.single().title.toString())
        // Tocar el aviso abre la app con el id del perfil como extra (sin cambiar de perfil por su cuenta).
        val open = shadowOf(dogNote.contentIntent).savedIntent
        assertEquals(dog, open.getIntExtra(ReminderScheduler.EXTRA_PROFILE_ID, -1))
        assertEquals(1, Savia.activeId.value)
        // Una sola alarma, para la siguiente toma (mañana, misma hora).
        val next = alarms().scheduledAlarms.single()
        assertEquals(MedSchedule.millisOf(day + 1, MedSchedule.parseTimes(slot)[0]), next.triggerAtMs)
        assertTrue(cat in Savia.profileIds)

        // Segunda pasada: nada se repite.
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancelAll()
        ReminderScheduler.sync(ctx, 12 * 60 * minute)
        assertTrue(notifications().isEmpty())
    }

    @Test
    fun singleProfileKeepsPlainTitles() = runBlocking {
        Savia.db.profile().save(Profile(name = "Ana"))
        val now = System.currentTimeMillis()
        val due = now - 2 * minute
        val slot = slotOf(due)
        Savia.db.medications().insert(Medication(name = "Loratadina", dose = "10 mg", times = slot, startEpochDay = due.millisToEpochDay() - 1))
        lastCheck(now - 5 * minute)
        ReminderScheduler.sync(ctx, 12 * 60 * minute)
        val shown = notifications().single()
        assertEquals("Hora de tu medicamento", shadowOf(shown).contentTitle.toString())
        assertEquals("Loratadina 10 mg · $slot", shadowOf(shown).contentText.toString())
    }

    @Test
    fun takenActionWritesToTheOwningProfileEvenIfNotActive() = runBlocking {
        val kid = Savia.createProfile(Profile(name = "Leo"))
        val now = System.currentTimeMillis()
        val due = now - 2 * minute
        val day = due.millisToEpochDay()
        val slot = slotOf(due)
        // Mismo id de medicamento en ambos perfiles: la toma solo debe caer en el de Leo.
        Savia.db.medications().insert(Medication(name = "Vitamina D", times = slot, startEpochDay = day - 1))
        val medId = Savia.dbFor(kid).medications().insert(Medication(name = "Paracetamol", times = slot, startEpochDay = day - 1))
        assertEquals(1, Savia.activeId.value)

        val id = ReminderEvent(0, ReminderKind.MED, medId, day, slot, "", "", kid).notificationId
        ReminderReceiver().onReceive(
            ctx,
            Intent(ctx, ReminderReceiver::class.java).setAction("app.savia.reminders.TAKEN")
                .putExtra("med_id", medId).putExtra("day", day).putExtra("slot", slot)
                .putExtra(ReminderScheduler.EXTRA_PROFILE_ID, kid).putExtra("notification_id", id),
        )
        var intake: MedIntake? = null
        waitFor { intake = Savia.dbFor(kid).medications().getIntakesOfDay(day).firstOrNull(); intake != null }
        assertNotNull(intake)
        assertEquals(slot, intake!!.slot)
        assertEquals("taken", intake!!.status)
        assertTrue(Savia.db.medications().getIntakesOfDay(day).isEmpty())
        assertEquals(1, Savia.activeId.value)

        // Con la toma registrada, el aviso de Leo ya no sale; el del perfil 1 sí.
        lastCheck(now - 5 * minute)
        ReminderScheduler.sync(ctx, 12 * 60 * minute)
        assertEquals(1, notifications().size)
        assertTrue(shadowOf(notifications().single()).contentTitle.toString().contains("Vitamina D"))
    }

    @Test
    fun takenActionWithoutProfileExtraGoesToFirstProfileAndUnknownProfileIsIgnored() = runBlocking {
        val day = today()
        val medId = Savia.db.medications().insert(Medication(name = "Loratadina", times = "08:00", startEpochDay = day - 1))
        // Notificación antigua (sin extra de perfil): es del perfil 1.
        ReminderReceiver().onReceive(
            ctx,
            Intent(ctx, ReminderReceiver::class.java).setAction("app.savia.reminders.TAKEN")
                .putExtra("med_id", medId).putExtra("day", day).putExtra("slot", "08:00").putExtra("notification_id", 1),
        )
        waitFor { Savia.db.medications().getIntakesOfDay(day).isNotEmpty() }
        assertEquals(1, Savia.db.medications().getIntakesOfDay(day).size)
        // Perfil que ya no existe: no falla ni crea nada.
        ReminderReceiver().onReceive(
            ctx,
            Intent(ctx, ReminderReceiver::class.java).setAction("app.savia.reminders.TAKEN")
                .putExtra("med_id", medId).putExtra("day", day).putExtra("slot", "20:00")
                .putExtra(ReminderScheduler.EXTRA_PROFILE_ID, 99).putExtra("notification_id", 2),
        )
        Thread.sleep(300)
        assertEquals(1, Savia.db.medications().getIntakesOfDay(day).size)
        assertEquals(listOf(1), Savia.profileIds)
    }

    @Test
    fun vaccineOfAPetSchedulesTheAlarmAndDeletedProfilesStopReminding() = runBlocking {
        val dog = Savia.createProfile(Profile(name = "Firulais", kind = "pet", species = "perro"))
        val day = today()
        Savia.dbFor(dog).vaccinations().insert(Vaccination(epochDay = day - 300, name = "Rabia", nextDueEpochDay = day + 30))
        ReminderScheduler.sync(ctx, 10 * minute)
        // Primer aviso: 7 días antes de la fecha prevista, a las 09:00.
        assertEquals(MedSchedule.millisOf(day + 23, 9 * 60), alarms().scheduledAlarms.single().triggerAtMs)
        assertTrue(notifications().isEmpty())

        // Ya aplicada de nuevo: deja de avisar por esa fecha y apunta al refuerzo siguiente.
        Savia.dbFor(dog).vaccinations().insert(Vaccination(epochDay = day, name = "rabia", nextDueEpochDay = day + 365))
        ReminderScheduler.sync(ctx, 10 * minute)
        assertEquals(MedSchedule.millisOf(day + 358, 9 * 60), alarms().scheduledAlarms.single().triggerAtMs)

        // Al eliminar el perfil ya no queda nada que avisar.
        Savia.deleteProfile(dog)
        ReminderScheduler.sync(ctx, 10 * minute)
        assertNull(alarms().peekNextScheduledAlarm())
        assertEquals(listOf(1), Savia.profileIds)
    }

    @Test
    fun dueVaccineIsNotifiedWithProfileName() = runBlocking {
        Savia.db.profile().save(Profile(name = "Ana"))
        val kid = Savia.createProfile(Profile(name = "Sofía"))
        val now = System.currentTimeMillis()
        val day = today()
        val nine = MedSchedule.millisOf(day, 9 * 60)
        Savia.dbFor(kid).vaccinations().insert(Vaccination(epochDay = day - 180, name = "Influenza", nextDueEpochDay = day))
        // Solo se puede comprobar si hoy ya pasaron las 09:00 y siguen dentro de la ventana de recuperación de 12 h.
        if (now > nine && now - nine < 11 * 60 * minute) {
            lastCheck(nine - minute)
            ReminderScheduler.sync(ctx, 12 * 60 * minute)
            val shown = notifications().single()
            assertEquals("Sofía · Hoy toca: Influenza", shadowOf(shown).contentTitle.toString())
            assertTrue(shown.actions == null || shown.actions.isEmpty())
            assertEquals(kid, shadowOf(shown.contentIntent).savedIntent.getIntExtra(ReminderScheduler.EXTRA_PROFILE_ID, -1))
        } else {
            ReminderScheduler.sync(ctx, 10 * minute)
            assertTrue(notifications().isEmpty())
        }
    }
}
