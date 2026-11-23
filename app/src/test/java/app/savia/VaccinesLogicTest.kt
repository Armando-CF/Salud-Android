package app.savia

import app.savia.data.ProfileInfo
import app.savia.data.Vaccination
import app.savia.ui.vaccines.DueStatus
import app.savia.ui.vaccines.VaccineCatalog
import app.savia.ui.vaccines.VaccineLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class VaccinesLogicTest {
    private val today = LocalDate.of(2026, 11, 23).toEpochDay()

    private fun vac(id: Long, name: String, day: Long, next: Long? = null, type: String = "vacuna", dose: String = "", remind: Boolean = true) =
        Vaccination(id = id, epochDay = day, type = type, name = name, dose = dose, nextDueEpochDay = next, remind = remind)

    @Test
    fun statusByDistanceToToday() {
        assertEquals(DueStatus.VENCIDA, VaccineLogic.status(today - 1, today))
        assertEquals(DueStatus.PROXIMA, VaccineLogic.status(today, today))
        assertEquals(DueStatus.PROXIMA, VaccineLogic.status(today + 30, today))
        assertEquals(DueStatus.PROGRAMADA, VaccineLogic.status(today + 31, today))
    }

    @Test
    fun upcomingIsSortedAndFlagsOverdue() {
        val list = listOf(
            vac(1, "Rabia", today - 400, today - 35),
            vac(2, "Pipeta", today - 20, today + 10, type = "desp_externa"),
            vac(3, "Múltiple (quíntuple)", today - 100, today + 265),
            vac(4, "Bordetella", today - 50),
        )
        val up = VaccineLogic.upcoming(list, today)
        assertEquals(listOf(1L, 2L, 3L), up.map { it.vaccination.id })
        assertTrue(up[0].overdue)
        assertFalse(up[1].overdue)
        assertEquals(today + 10, up[1].dueDay)
        assertEquals(listOf(1L, 2L), VaccineLogic.dueSoon(list, today).map { it.vaccination.id })
    }

    @Test
    fun aLaterRecordWithTheSameNameFulfilsTheDueDate() {
        // Rabia aplicada hace un año con refuerzo que tocaba hace 5 días; se aplicó hace 3 días (sin próxima fecha).
        val old = vac(1, "Rabia", today - 370, today - 5)
        val done = vac(2, "rabia ", today - 3)
        assertTrue(VaccineLogic.isFulfilled(old, listOf(old, done)))
        assertTrue(VaccineLogic.upcoming(listOf(old, done), today).isEmpty())
        // Sin distinguir acentos ni mayúsculas.
        val a = vac(3, "Múltiple", today - 370, today - 5)
        val b = vac(4, "MULTIPLE", today - 20)
        assertTrue(VaccineLogic.isFulfilled(a, listOf(a, b)))
        // Otra vacuna distinta, u otro tipo con el mismo nombre, no la cumple.
        assertFalse(VaccineLogic.isFulfilled(old, listOf(old, vac(5, "Moquillo", today - 3))))
        assertFalse(VaccineLogic.isFulfilled(old, listOf(old, vac(6, "Rabia", today - 3, type = "otro"))))
        // Un registro anterior tampoco.
        assertFalse(VaccineLogic.isFulfilled(old, listOf(old, vac(7, "Rabia", today - 800))))
    }

    @Test
    fun anEarlierDoseOfASeriesDoesNotFulfilUnlessItCarriesTheSchedule() {
        // 1.ª dosis con 2.ª a los 60 días; a los 10 días se registra otra "igual" sin próxima fecha: no cumple (muy pronto).
        val first = vac(1, "Hepatitis B", today - 30, today + 30)
        val tooEarly = vac(2, "Hepatitis B", today - 20)
        assertFalse(VaccineLogic.isFulfilled(first, listOf(first, tooEarly)))
        // Pero si el registro posterior trae su propia próxima fecha, es el que lleva el calendario.
        val carries = vac(3, "Hepatitis B", today - 20, today + 160)
        val up = VaccineLogic.upcoming(listOf(first, carries), today)
        assertEquals(listOf(3L), up.map { it.vaccination.id })
        // Adelantarse dentro del margen sí cuenta.
        val annual = vac(4, "Influenza", today - 350, today + 15)
        val early = vac(5, "Influenza", today - 2)
        assertTrue(VaccineLogic.isFulfilled(annual, listOf(annual, early)))
    }

    @Test
    fun recordsWithoutNextDateNeverAppear() {
        assertTrue(VaccineLogic.upcoming(listOf(vac(1, "BCG", today - 3000)), today).isEmpty())
    }

    @Test
    fun nextDoseLabels() {
        assertEquals("2.ª dosis", VaccineLogic.nextDose("1.ª dosis"))
        assertEquals("3.ª dosis", VaccineLogic.nextDose("2.ª dosis"))
        assertEquals("Refuerzo", VaccineLogic.nextDose("3.ª dosis"))
        assertEquals("Refuerzo", VaccineLogic.nextDose("Refuerzo"))
        assertEquals("Anual", VaccineLogic.nextDose("Anual"))
        assertEquals("Refuerzo", VaccineLogic.nextDose("Dosis única"))
        assertEquals("", VaccineLogic.nextDose("  "))
        assertEquals("1 tableta", VaccineLogic.nextDose("1 tableta"))
    }

    @Test
    fun intervalsUseCalendarMonthsAndYears() {
        val jan31 = LocalDate.of(2026, 1, 31).toEpochDay()
        assertEquals(LocalDate.of(2026, 2, 28).toEpochDay(), VaccineLogic.plusMonths(jan31, 1))
        assertEquals(LocalDate.of(2027, 1, 31).toEpochDay(), VaccineLogic.plusInterval(jan31, 365))
        assertEquals(LocalDate.of(2026, 4, 30).toEpochDay(), VaccineLogic.plusInterval(jan31, 90))
        assertEquals(jan31 + 21, VaccineLogic.plusInterval(jan31, 21))
        assertEquals(LocalDate.of(2036, 1, 31).toEpochDay(), VaccineLogic.plusInterval(jan31, 3650))
    }

    @Test
    fun dueTexts() {
        assertEquals("Toca hoy", VaccineLogic.dueText(today, today))
        assertEquals("Toca mañana", VaccineLogic.dueText(today + 1, today))
        assertEquals("Venció ayer", VaccineLogic.dueText(today - 1, today))
        assertEquals("Vencida hace 12 días", VaccineLogic.dueText(today - 12, today))
        assertEquals("En 5 días", VaccineLogic.dueText(today + 5, today))
        assertEquals("En 6 meses", VaccineLogic.dueText(today + 182, today))
        assertEquals("En 1 año", VaccineLogic.dueText(today + 365, today))
        assertEquals("En 10 años", VaccineLogic.dueText(today + 3650, today))
    }

    @Test
    fun catalogDependsOnTheProfile() {
        val dog = ProfileInfo(2, "Firulais", kind = "pet", species = "perro")
        val cat = ProfileInfo(3, "Garfield", kind = "pet", species = "gato")
        val fish = ProfileInfo(4, "Nemo", kind = "pet", species = "pez")
        val adult = ProfileInfo(1, "Ana", birthEpochDay = today - 365L * 35)
        val baby = ProfileInfo(5, "Leo", birthEpochDay = today - 200)

        assertTrue(VaccineCatalog.suggestions(dog).any { it.name.startsWith("Múltiple") })
        assertTrue(VaccineCatalog.suggestions(cat).any { it.name == "Triple felina" })
        assertFalse(VaccineCatalog.suggestions(cat).any { it.name.startsWith("Múltiple") })
        assertTrue(VaccineCatalog.suggestions(fish).isNotEmpty())
        // Personas: sin desparasitación de mascotas, y la etapa de su edad va primero.
        assertTrue(VaccineCatalog.suggestions(adult).none { it.type == "desp_interna" || it.type == "desp_externa" })
        assertEquals("Td (tétanos y difteria)", VaccineCatalog.suggestions(adult).first().name)
        assertEquals("BCG", VaccineCatalog.suggestions(baby).first().name)

        // Búsqueda sin acentos, por nombre o por lo que cubre.
        assertEquals(listOf("Múltiple (quíntuple)"), VaccineCatalog.filter(dog, "vacuna", "multiple").map { it.name })
        assertTrue(VaccineCatalog.filter(dog, "vacuna", "moquillo").size >= 2)
        assertTrue(VaccineCatalog.filter(dog, "desp_externa", "").any { it.name == "Pipeta" })
        assertTrue(VaccineCatalog.filter(adult, "vacuna", "tetanos").isNotEmpty())

        // Intervalos orientativos.
        assertEquals(365, VaccineCatalog.find(dog, "rabia")!!.intervalDays)
        assertEquals("cada año", VaccineCatalog.find(dog, "RABIA", "vacuna")!!.intervalLabel)
        assertEquals("cada 3 meses", VaccineCatalog.find(dog, "Desparasitante en tableta")!!.intervalLabel)
        assertEquals("cada 10 años", VaccineCatalog.find(adult, "Td (tétanos y difteria)")!!.intervalLabel)
        assertNull(VaccineCatalog.find(adult, "BCG")!!.intervalDays)
        assertNull(VaccineCatalog.find(dog, "Rabia", "desp_interna"))
        assertNotNull(VaccineCatalog.find(adult, "influenza"))
        assertEquals("en 3 semanas", VaccineCatalog.inLabel(21))
        assertEquals("en 6 meses", VaccineCatalog.inLabel(180))
    }
}
