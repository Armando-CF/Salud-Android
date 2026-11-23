package app.savia

import app.savia.data.MedIntake
import app.savia.data.Medication
import app.savia.ui.meds.MedSchedule
import app.savia.ui.meds.dosesForDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class MedsScheduleTest {
    private val zone = ZoneId.of("America/Mexico_City")

    /** Lunes 2 de noviembre de 2026. */
    private val monday = LocalDate.of(2026, 11, 2).toEpochDay()

    private fun med(id: Long, times: String, mask: Int = 127, start: Long = monday - 30, end: Long? = null, active: Boolean = true, name: String = "Med$id") =
        Medication(id = id, name = name, times = times, daysMask = mask, startEpochDay = start, endEpochDay = end, active = active)

    private fun intake(medId: Long, day: Long, slot: String, status: String = "taken", ts: Long = 0) =
        MedIntake(medicationId = medId, epochDay = day, slot = slot, timestamp = ts, status = status)

    @Test
    fun parsesAndNormalizesTimes() {
        assertEquals(listOf(480, 1200), MedSchedule.parseTimes("20:00, 8:00,xx,08:00,25:00"))
        assertEquals("08:00,20:00", MedSchedule.joinTimes(listOf(1200, 480, 480)))
        assertTrue(MedSchedule.isAsNeeded(med(1, "")))
        assertFalse(MedSchedule.isAsNeeded(med(1, "08:00")))
    }

    @Test
    fun dosesAreSortedAndMatchedWithIntakes() {
        val meds = listOf(med(1, "20:00,08:00", name = "B"), med(2, "08:00", name = "A"), med(3, ""))
        val intakes = listOf(intake(1, monday, "08:00"), intake(2, monday, "08:00", "skipped"), intake(1, monday - 1, "20:00"), intake(3, monday, ""))
        val doses = dosesForDay(meds, intakes, monday)
        assertEquals(listOf("A 08:00", "B 08:00", "B 20:00"), doses.map { "${it.med.name} ${it.slot}" })
        assertTrue(doses[0].skipped)
        assertTrue(doses[1].taken)
        assertTrue(doses[2].pending)
        assertEquals(1200, doses[2].minutes)
    }

    @Test
    fun respectsDaysMaskWithBitZeroAsMonday() {
        val monWedFri = 0b0010101
        val m = med(1, "09:00", mask = monWedFri)
        assertEquals(1, dosesForDay(listOf(m), emptyList(), monday).size)
        assertEquals(0, dosesForDay(listOf(m), emptyList(), monday + 1).size) // martes
        assertEquals(1, dosesForDay(listOf(m), emptyList(), monday + 2).size) // miércoles
        assertEquals(0, dosesForDay(listOf(m), emptyList(), monday + 5).size) // sábado
        val sundayOnly = med(2, "09:00", mask = 1 shl 6)
        assertEquals(1, dosesForDay(listOf(sundayOnly), emptyList(), monday + 6).size)
        assertEquals(0, dosesForDay(listOf(sundayOnly), emptyList(), monday).size)
    }

    @Test
    fun respectsStartEndAndActive() {
        val m = med(1, "09:00", start = monday, end = monday + 2)
        assertEquals(0, dosesForDay(listOf(m), emptyList(), monday - 1).size)
        assertEquals(1, dosesForDay(listOf(m), emptyList(), monday).size)
        assertEquals(1, dosesForDay(listOf(m), emptyList(), monday + 2).size)
        assertEquals(0, dosesForDay(listOf(m), emptyList(), monday + 3).size)
        assertEquals(0, dosesForDay(listOf(m.copy(active = false)), emptyList(), monday).size)
        assertTrue(MedSchedule.isFinished(m, monday + 3))
        assertFalse(MedSchedule.isFinished(m, monday + 2))
    }

    @Test
    fun adherenceCountsOnlyDueDoses() {
        val m = med(1, "08:00,20:00", start = monday)
        val intakes = listOf(
            intake(1, monday, "08:00"), intake(1, monday, "20:00"),
            intake(1, monday + 1, "08:00"), intake(1, monday + 1, "20:00", "skipped"),
            intake(1, monday + 2, "08:00"),
        )
        // Miércoles a mediodía: 2 + 2 + 1 tomas ya vencidas; la de las 20:00 de hoy aún no cuenta.
        val a = MedSchedule.adherence(m, intakes, monday - 6, monday + 2, nowDay = monday + 2, nowMinutes = 12 * 60)
        assertEquals(5, a.scheduled)
        assertEquals(4, a.taken)
        assertEquals(80, a.percent)
        // Sin nada programado no hay porcentaje.
        assertNull(MedSchedule.adherence(med(2, ""), emptyList(), monday, monday + 2).percent)
        // Una toma adelantada (registrada antes de su hora) sí cuenta.
        val early = MedSchedule.adherence(m, listOf(intake(1, monday, "08:00"), intake(1, monday, "20:00")), monday, monday, nowDay = monday, nowMinutes = 9 * 60)
        assertEquals(2, early.scheduled)
        assertEquals(100, early.percent)
    }

    @Test
    fun recentAdherenceStartsAtFirstRecord() {
        val m = med(1, "08:00", start = monday - 30)
        // Sin registros no hay dato.
        assertNull(MedSchedule.recentAdherence(m, emptyList(), 7, monday + 3, 12 * 60).percent)
        // Primer registro el martes; hoy jueves a mediodía: martes ✓, miércoles ✗, jueves ✓.
        val intakes = listOf(intake(1, monday + 1, "08:00"), intake(1, monday + 3, "08:00"))
        val a = MedSchedule.recentAdherence(m, intakes, 7, monday + 3, 12 * 60)
        assertEquals(3, a.scheduled)
        assertEquals(2, a.taken)
    }

    @Test
    fun nextDoseSkipsRegisteredAndNonScheduledDays() {
        val m = med(1, "08:00,20:00")
        val at9 = MedSchedule.millisOf(monday, 9 * 60, zone)
        val next = MedSchedule.nextDose(listOf(m), emptyList(), at9, zone)!!
        assertEquals(MedSchedule.millisOf(monday, 20 * 60, zone), next.first)
        assertEquals("20:00", next.second.slot)
        // Si la de las 20:00 ya está registrada, sigue la de mañana a las 08:00.
        val next2 = MedSchedule.nextDose(listOf(m), listOf(intake(1, monday, "20:00")), at9, zone)!!
        assertEquals(MedSchedule.millisOf(monday + 1, 8 * 60, zone), next2.first)
        // Solo viernes: desde el lunes, toca el viernes.
        val fri = med(2, "07:30", mask = 1 shl 4)
        assertEquals(MedSchedule.millisOf(monday + 4, 7 * 60 + 30, zone), MedSchedule.nextDose(listOf(fri), emptyList(), at9, zone)!!.first)
        // Tratamiento terminado o según sea necesario: nada.
        assertNull(MedSchedule.nextDose(listOf(med(3, "08:00", end = monday - 1), med(4, "")), emptyList(), at9, zone))
        // Empieza en el futuro.
        val future = med(5, "08:00", start = monday + 20)
        assertEquals(MedSchedule.millisOf(monday + 20, 8 * 60, zone), MedSchedule.nextDose(listOf(future), emptyList(), at9, zone)!!.first)
    }

    @Test
    fun summaries() {
        assertEquals("Todos los días", MedSchedule.daysSummary(127))
        assertEquals("Lunes a viernes", MedSchedule.daysSummary(0b0011111))
        assertEquals("lun, mié", MedSchedule.daysSummary(0b0000101))
        assertEquals("08:00, 20:00 · Todos los días", MedSchedule.scheduleSummary(med(1, "20:00,08:00")))
        assertEquals("Según sea necesario", MedSchedule.scheduleSummary(med(1, "")))
    }
}
