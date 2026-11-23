package app.savia

import app.savia.data.Appointment
import app.savia.data.Medication
import app.savia.data.Vaccination
import app.savia.reminders.ReminderEvent
import app.savia.reminders.ReminderInput
import app.savia.reminders.ReminderKind
import app.savia.reminders.ReminderPlanner
import app.savia.reminders.VaccineReminders
import app.savia.ui.meds.MedSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Planificador con varios perfiles (personas y mascotas) y avisos de vacunas. Lógica pura. */
class ReminderMultiProfileTest {
    private val zone = ZoneId.of("America/Mexico_City")
    private val day = LocalDate.of(2026, 11, 2).toEpochDay() // lunes

    private fun at(d: Long, h: Int, m: Int = 0) = MedSchedule.millisOf(d, h * 60 + m, zone)

    private fun med(id: Long, name: String, times: String, dose: String = "") =
        Medication(id = id, name = name, dose = dose, times = times, startEpochDay = day - 10)

    private fun vac(id: Long, name: String, applied: Long, next: Long?, type: String = "vacuna", remind: Boolean = true, dose: String = "") =
        Vaccination(id = id, epochDay = applied, type = type, name = name, dose = dose, nextDueEpochDay = next, remind = remind)

    // ── Varios perfiles ──

    @Test
    fun eventsOfSeveralProfilesAreInterleavedAndCarryTheProfile() {
        val ana = ReminderInput(meds = listOf(med(1, "Loratadina", "08:00,20:00", "10 mg")), profileId = 1, profileName = "Ana")
        val dog = ReminderInput(meds = listOf(med(1, "Amoxicilina", "09:00", "250 mg")), profileId = 3, profileName = "Firulais")
        val events = ReminderPlanner.eventsBetween(listOf(dog, ana), at(day, 7), at(day, 21), zone)

        assertEquals(listOf(at(day, 8), at(day, 9), at(day, 20)), events.map { it.at })
        assertEquals(listOf(1, 3, 1), events.map { it.profileId })
        assertEquals("Ana · Loratadina 08:00", events[0].title)
        assertEquals("Hora del medicamento: Loratadina 10 mg", events[0].text)
        assertEquals("Firulais · Amoxicilina 09:00", events[1].title)
        // Mismo id de medicamento y mismo horario en perfiles distintos: avisos distintos.
        val same = ReminderPlanner.eventsBetween(
            listOf(ana.copy(meds = listOf(med(1, "A", "09:00"))), dog), at(day, 8, 30), at(day, 9, 30), zone,
        )
        assertEquals(2, same.size)
        assertNotEquals(same[0].key, same[1].key)
        assertNotEquals(same[0].notificationId, same[1].notificationId)
    }

    @Test
    fun nextEventIsTheEarliestAcrossProfiles() {
        val ana = ReminderInput(meds = listOf(med(1, "Loratadina", "20:00")), profileId = 1, profileName = "Ana")
        val kid = ReminderInput(
            appointments = listOf(Appointment(id = 5, timestamp = at(day, 16), title = "Pediatra")), profileId = 2, profileName = "Sofía",
        )
        val empty = ReminderInput(profileId = 4, profileName = "Garfield")
        // Lo primero es la cita de Sofía (2 h antes = 14:00), no la toma de Ana a las 20:00.
        assertEquals(at(day, 14), ReminderPlanner.nextEventTime(listOf(ana, empty, kid), at(day, 9), zone))
        assertEquals(at(day, 20), ReminderPlanner.nextEventTime(listOf(ana, empty, kid), at(day, 14), zone))
        assertNull(ReminderPlanner.nextEventTime(listOf(empty), at(day, 9), zone))
        assertNull(ReminderPlanner.nextEventTime(emptyList<ReminderInput>(), at(day, 9), zone))
        assertTrue(ReminderPlanner.eventsBetween(emptyList<ReminderInput>(), at(day, 0), at(day, 23), zone).isEmpty())
    }

    @Test
    fun appointmentTitlesNameTheProfile() {
        val appt = Appointment(id = 9, timestamp = at(day + 1, 10, 30), title = "Revisión", doctor = "Dra. Rivera")
        val kid = ReminderInput(appointments = listOf(appt), profileId = 2, profileName = "Sofía")
        val events = ReminderPlanner.eventsBetween(kid, at(day, 0), at(day + 1, 23), zone)
        assertEquals(listOf("Sofía · Cita mañana 10:30", "Sofía · Cita en 2 horas"), events.map { it.title })
        assertEquals("Revisión · mañana a las 10:30 · Dra. Rivera", events[0].text)
        assertTrue(events.all { it.profileId == 2 })
    }

    @Test
    fun singleProfileKeepsOriginalWordingAndKeys() {
        val input = ReminderInput(
            meds = listOf(med(1, "Loratadina", "08:00", "10 mg")),
            appointments = listOf(Appointment(id = 9, timestamp = at(day + 1, 10, 30), title = "Revisión")),
            periodStarts = listOf(day + 5),
            selfExamBaseDay = day - 27,
        )
        val events = ReminderPlanner.eventsBetween(input, at(day, 0), at(day + 3, 23), zone)
        val byKind = events.associateBy { it.kind }
        assertEquals("Hora de tu medicamento", byKind[ReminderKind.MED]!!.title)
        assertEquals("Loratadina 10 mg · 08:00", byKind[ReminderKind.MED]!!.text)
        assertEquals("Mañana tienes cita", byKind[ReminderKind.APPT_DAY_BEFORE]!!.title)
        assertEquals("Tu cita es en 2 horas", byKind[ReminderKind.APPT_SOON]!!.title)
        assertEquals("Recordatorio de tu ciclo", byKind[ReminderKind.PERIOD]!!.title)
        assertTrue(byKind[ReminderKind.PERIOD]!!.text.startsWith("Tu periodo"))
        assertEquals("Autoexploración mensual", byKind[ReminderKind.SELF_EXAM]!!.title)
        assertTrue(events.all { it.profileId == 1 })
        // La clave del perfil 1 es la de siempre (sin prefijo): las notificaciones ya publicadas siguen casando.
        assertEquals("MED:1:$day:08:00", ReminderEvent(0, ReminderKind.MED, 1, day, "08:00", "", "").key)
        assertEquals("p2:MED:1:$day:08:00", ReminderEvent(0, ReminderKind.MED, 1, day, "08:00", "", "", 2).key)
    }

    @Test
    fun cycleAndSelfExamAreNeutralWhenThereAreSeveralProfiles() {
        val input = ReminderInput(periodStarts = listOf(day + 5), selfExamBaseDay = day - 27, profileId = 2, profileName = "Sofía")
        val events = ReminderPlanner.eventsBetween(input, at(day, 0), at(day + 3, 23), zone)
        assertEquals(listOf("Sofía · Recordatorio del ciclo", "Sofía · Autoexploración mensual"), events.map { it.title })
        assertTrue(events[0].text.startsWith("El periodo"))
    }

    // ── Vacunas ──

    @Test
    fun vaccineRemindsSevenDaysBeforeAndOnTheDayAtNine() {
        val input = ReminderInput(vaccinations = listOf(vac(1, "Rabia", day - 358, day + 7, dose = "Anual")))
        val events = ReminderPlanner.eventsBetween(input, at(day - 1, 12), at(day + 8, 0), zone)
        assertEquals(2, events.size)
        assertEquals(ReminderKind.VACCINE_SOON, events[0].kind)
        assertEquals(at(day, 9), events[0].at)
        assertEquals("Próxima vacuna: Rabia en 7 días", events[0].title)
        assertTrue(events[0].text.startsWith("Vacuna prevista para el lun 9 nov"))
        assertTrue(events[0].text.contains("Anual"))
        assertEquals(ReminderKind.VACCINE_DUE, events[1].kind)
        assertEquals(at(day + 7, 9), events[1].at)
        assertEquals("Hoy toca: Rabia", events[1].title)
        assertEquals(1L, events[1].refId)
        assertEquals(day + 7, events[1].epochDay)
        assertNotEquals(events[0].notificationId, events[1].notificationId)
        // La alarma apunta primero al aviso anticipado y después al del día.
        assertEquals(at(day, 9), ReminderPlanner.nextEventTime(input, at(day - 1, 12), zone))
        assertEquals(at(day + 7, 9), ReminderPlanner.nextEventTime(input, at(day, 9), zone))
        assertNull(ReminderPlanner.nextEventTime(input, at(day + 7, 9), zone))
    }

    @Test
    fun dewormingAndOtherTypesUseTheirOwnWordAndProfileName() {
        val dog = ReminderInput(
            vaccinations = listOf(
                vac(1, "Bravecto", day - 83, day + 7, type = "desp_externa"),
                vac(2, "Drontal", day - 90, day, type = "desp_interna"),
                vac(3, "Revisión dental", day - 100, day + 7, type = "otro"),
            ),
            profileId = 3, profileName = "Firulais",
        )
        val titles = ReminderPlanner.eventsBetween(dog, at(day, 8), at(day, 10), zone).map { it.title }.toSet()
        assertEquals(
            setOf(
                "Firulais · Próxima desparasitación: Bravecto en 7 días",
                "Firulais · Hoy toca: Drontal",
                "Firulais · Próxima aplicación: Revisión dental en 7 días",
            ),
            titles,
        )
        val drontal = ReminderPlanner.eventsBetween(dog, at(day, 8), at(day, 10), zone).first { it.refId == 2L }
        assertTrue(drontal.text.startsWith("Desparasitación prevista para hoy"))
        assertEquals(3, drontal.profileId)
    }

    @Test
    fun noVaccineReminderWhenOffOrWithoutNextDate() {
        val input = ReminderInput(
            vaccinations = listOf(vac(1, "Rabia", day - 358, day + 7, remind = false), vac(2, "Influenza", day - 10, null)),
        )
        assertTrue(ReminderPlanner.eventsBetween(input, at(day - 30, 0), at(day + 30, 0), zone).isEmpty())
        assertNull(ReminderPlanner.nextEventTime(input, at(day - 30, 0), zone))
    }

    @Test
    fun fulfilledVaccineDoesNotRemind() {
        val first = vac(1, "Rabia", day - 365, day + 7)
        // Se aplicó de nuevo hace 5 días (dentro de los 30 días previos a la fecha prevista): ya está cumplida.
        val again = vac(2, "  rabía ", day - 5, day + 360)
        val input = ReminderInput(vaccinations = listOf(first, again))
        val events = ReminderPlanner.eventsBetween(input, at(day - 1, 0), at(day + 8, 0), zone)
        assertTrue(events.none { it.refId == 1L })
        assertTrue(VaccineReminders.isFulfilled(first, listOf(first, again)))
        assertFalse(VaccineReminders.isFulfilled(again, listOf(first, again)))
        assertEquals(listOf(again), VaccineReminders.pending(listOf(first, again)))
        // El siguiente aviso ya es el del refuerzo nuevo (7 días antes).
        assertEquals(at(day + 353, 9), ReminderPlanner.nextEventTime(input, at(day, 0), zone))
    }

    @Test
    fun fulfilmentNeedsSameNameLaterDateAndWithinThirtyDays() {
        val v = vac(1, "Múltiple", day - 365, day + 7)
        // Otra vacuna distinta no la cumple.
        assertFalse(VaccineReminders.isFulfilled(v, listOf(v, vac(2, "Rabia", day - 5, null))))
        // Una dosis del mismo nombre demasiado anterior (más de 30 días antes de la fecha prevista) tampoco.
        assertFalse(VaccineReminders.isFulfilled(v, listOf(v, vac(2, "Multiple", day - 40, null))))
        // Un registro anterior al propio (historial viejo) no cuenta aunque se llame igual.
        assertFalse(VaccineReminders.isFulfilled(v, listOf(vac(2, "Múltiple", day - 400, null), v)))
        // Justo en el límite (fecha prevista − 30) sí cuenta, igual que una aplicación tardía.
        assertTrue(VaccineReminders.isFulfilled(v, listOf(v, vac(2, "MULTIPLE", day + 7 - 30, null))))
        assertTrue(VaccineReminders.isFulfilled(v, listOf(v, vac(2, "múltiple", day + 20, null))))
        // No se cumple a sí misma.
        assertFalse(VaccineReminders.isFulfilled(v, listOf(v)))
        assertEquals("multiple felina", VaccineReminders.normalize("  Múltiple   FELINA "))
        assertEquals("desparasitación", VaccineReminders.noun("desp_interna"))
        assertEquals("vacuna", VaccineReminders.noun("vacuna"))
        assertEquals("aplicación", VaccineReminders.noun("otro"))
    }
}
