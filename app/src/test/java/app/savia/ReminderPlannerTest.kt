package app.savia

import app.savia.data.Appointment
import app.savia.data.MedIntake
import app.savia.data.Medication
import app.savia.reminders.ReminderInput
import app.savia.reminders.ReminderKind
import app.savia.reminders.ReminderPlanner
import app.savia.ui.meds.MedSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReminderPlannerTest {
    private val zone = ZoneId.of("America/Mexico_City")
    private val day = LocalDate.of(2026, 11, 2).toEpochDay() // lunes

    private fun at(d: Long, h: Int, m: Int = 0) = MedSchedule.millisOf(d, h * 60 + m, zone)
    private val min = 60_000L

    private fun med(id: Long, times: String, remind: Boolean = true, active: Boolean = true, mask: Int = 127, end: Long? = null) =
        Medication(id = id, name = "Med$id", dose = "10 mg", times = times, daysMask = mask, startEpochDay = day - 10, endEpochDay = end, active = active, remind = remind)

    @Test
    fun medEventsInWindowSkipRegisteredAndNonReminding() {
        val input = ReminderInput(
            meds = listOf(med(1, "08:00,20:00"), med(2, "08:00", remind = false), med(3, "08:00", active = false), med(4, "08:00")),
            intakes = listOf(MedIntake(medicationId = 4, epochDay = day, slot = "08:00", timestamp = 0)),
        )
        val events = ReminderPlanner.eventsBetween(input, at(day, 7, 59), at(day, 8, 1), zone)
        assertEquals(1, events.size)
        val e = events[0]
        assertEquals(ReminderKind.MED, e.kind)
        assertEquals(1L, e.refId)
        assertEquals("08:00", e.slot)
        assertEquals(at(day, 8), e.at)
        assertEquals("Med1 10 mg · 08:00", e.text)
        // La ventana es (después, hasta]: un aviso justo en "después" ya no entra.
        assertTrue(ReminderPlanner.eventsBetween(input, at(day, 8), at(day, 8, 1), zone).isEmpty())
        // Identidad estable y distinta por horario/día.
        assertEquals(e.notificationId, ReminderPlanner.eventsBetween(input, at(day, 7), at(day, 9), zone)[0].notificationId)
        val evening = ReminderPlanner.eventsBetween(input, at(day, 19), at(day, 21), zone)[0]
        assertNotEquals(e.key, evening.key)
    }

    @Test
    fun nextEventPicksEarliestAcrossSources() {
        val appt = Appointment(id = 9, timestamp = at(day + 1, 10, 30), title = "Revisión", doctor = "Dra. Rivera")
        val input = ReminderInput(meds = listOf(med(1, "08:00,20:00")), appointments = listOf(appt))
        // Lunes 09:00 → primero el aviso "un día antes" de la cita (lunes 10:30).
        assertEquals(at(day, 10, 30), ReminderPlanner.nextEventTime(input, at(day, 9), zone))
        // Después de ese → la toma de las 20:00.
        assertEquals(at(day, 20), ReminderPlanner.nextEventTime(input, at(day, 10, 30), zone))
        // Martes 08:01 → aviso de 2 horas antes (08:30).
        assertEquals(at(day + 1, 8, 30), ReminderPlanner.nextEventTime(input, at(day + 1, 8, 1), zone))
        assertNull(ReminderPlanner.nextEventTime(ReminderInput(), at(day, 9), zone))
    }

    @Test
    fun appointmentRemindersDayBeforeAndTwoHoursBefore() {
        val appt = Appointment(id = 9, timestamp = at(day + 1, 10, 30), title = "Revisión", doctor = "Dra. Rivera", place = "Consultorio 2")
        val input = ReminderInput(appointments = listOf(appt))
        val before = ReminderPlanner.eventsBetween(input, at(day, 10, 29), at(day, 10, 31), zone)
        assertEquals(listOf(ReminderKind.APPT_DAY_BEFORE), before.map { it.kind })
        assertEquals("Revisión · mañana a las 10:30 · Dra. Rivera · Consultorio 2", before[0].text)
        val soon = ReminderPlanner.eventsBetween(input, at(day + 1, 8, 29), at(day + 1, 8, 31), zone)
        assertEquals(listOf(ReminderKind.APPT_SOON), soon.map { it.kind })
        // Realizada o sin recordatorio: nada.
        val none = ReminderInput(appointments = listOf(appt.copy(done = true), appt.copy(id = 10, remind = false)))
        assertTrue(ReminderPlanner.eventsBetween(none, at(day - 3, 0), at(day + 3, 0), zone).isEmpty())
        assertNull(ReminderPlanner.nextEventTime(none, at(day - 3, 0), zone))
    }

    @Test
    fun periodReminderTwoDaysBeforeAtNine() {
        val input = ReminderInput(periodStarts = listOf(day + 5, day + 33))
        assertEquals(at(day + 3, 9), ReminderPlanner.nextEventTime(input, at(day, 12), zone))
        assertEquals(at(day + 31, 9), ReminderPlanner.nextEventTime(input, at(day + 3, 9), zone))
        val events = ReminderPlanner.eventsBetween(input, at(day + 3, 8, 59), at(day + 3, 9, 1), zone)
        assertEquals(1, events.size)
        assertEquals(ReminderKind.PERIOD, events[0].kind)
        assertEquals(day + 5, events[0].epochDay)
    }

    @Test
    fun selfExamRepeatsEvery30DaysFromBase() {
        val input = ReminderInput(selfExamBaseDay = day - 10)
        // Siguiente: base + 30 a las 10:00.
        assertEquals(at(day + 20, 10), ReminderPlanner.nextEventTime(input, at(day, 12), zone))
        // Pasado ese, el siguiente es base + 60.
        assertEquals(at(day + 50, 10), ReminderPlanner.nextEventTime(input, at(day + 20, 10), zone))
        // Con una base muy vieja sigue cayendo en múltiplos de 30 días.
        val old = ReminderInput(selfExamBaseDay = day - 95)
        assertEquals(at(day + 25, 10), ReminderPlanner.nextEventTime(old, at(day, 12), zone))
        val events = ReminderPlanner.eventsBetween(input, at(day + 20, 9, 59), at(day + 20, 10, 1), zone)
        assertEquals(listOf(ReminderKind.SELF_EXAM), events.map { it.kind })
        assertTrue(ReminderPlanner.eventsBetween(input, at(day, 0), at(day + 19, 23), zone).isEmpty())
    }

    @Test
    fun treatmentEndAndDaysMaskAffectNextEvent() {
        // Termina hoy: la toma de la noche sigue, mañana ya no.
        val ending = ReminderInput(meds = listOf(med(1, "08:00,20:00", end = day)))
        assertEquals(at(day, 20), ReminderPlanner.nextEventTime(ending, at(day, 9), zone))
        assertNull(ReminderPlanner.nextEventTime(ending, at(day, 20), zone))
        // Solo miércoles (bit 2).
        val wed = ReminderInput(meds = listOf(med(2, "07:00", mask = 1 shl 2)))
        assertEquals(at(day + 2, 7), ReminderPlanner.nextEventTime(wed, at(day, 9), zone))
        assertEquals(at(day + 9, 7), ReminderPlanner.nextEventTime(wed, at(day + 2, 7), zone))
    }

    @Test
    fun windowSpanningMidnightCollectsBothDays() {
        val input = ReminderInput(meds = listOf(med(1, "23:30,00:15")))
        val events = ReminderPlanner.eventsBetween(input, at(day, 23), at(day + 1, 1), zone)
        assertEquals(listOf(at(day, 23, 30), at(day + 1, 0, 15)), events.map { it.at })
        assertEquals(listOf(day, day + 1), events.map { it.epochDay })
        assertTrue(events.all { it.at - at(day, 23) > 0 && it.at - at(day, 23) <= 120 * min })
    }
}
