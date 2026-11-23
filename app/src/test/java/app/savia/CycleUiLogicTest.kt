package app.savia

import app.savia.cycle.CycleEngine
import app.savia.cycle.Phase
import app.savia.data.CycleDay
import app.savia.ui.cycle.CycleLogic
import app.savia.ui.cycle.QuickState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CycleUiLogicTest {
    private fun bleed(start: Long, n: Int) = (0 until n).map { CycleDay(epochDay = start + it, flow = 3) }
    private fun analyze(days: List<CycleDay>, today: Long, cycle: Int = 28) = CycleEngine.analyze(days, cycle, 5, 14, today)

    @Test
    fun monthGridStartsOnMondayAndHasSixWeeks() {
        // Noviembre de 2026 empieza en domingo → 6 huecos antes del día 1.
        val nov = LocalDate.of(2026, 11, 1)
        val cells = CycleLogic.monthGrid(nov)
        assertEquals(42, cells.size)
        assertEquals(6, cells.indexOfFirst { it != null })
        assertEquals(nov.toEpochDay(), cells[6])
        assertEquals(30, cells.count { it != null })
        assertEquals(LocalDate.of(2026, 11, 30).toEpochDay(), cells.last { it != null })
        // Febrero de 2021 empieza en lunes y dura 28 días: 4 semanas exactas sin relleno.
        val feb = CycleLogic.monthGrid(LocalDate.of(2021, 2, 14), fixedWeeks = false)
        assertEquals(28, feb.size)
        assertTrue(feb.none { it == null })
    }

    @Test
    fun monthIndexRoundTrip() {
        val d = LocalDate.of(2026, 11, 23)
        val i = CycleLogic.monthIndex(d)
        assertEquals(LocalDate.of(2026, 11, 1), CycleLogic.monthOfIndex(i))
        assertEquals(LocalDate.of(2026, 12, 1), CycleLogic.monthOfIndex(i + 1))
        assertEquals(LocalDate.of(2027, 1, 1), CycleLogic.monthOfIndex(i + 2))
        assertEquals(LocalDate.of(1990, 1, 1), CycleLogic.monthOfIndex(0))
    }

    @Test
    fun emptyAndExtras() {
        assertTrue(CycleLogic.isEmpty(CycleDay(epochDay = 1)))
        assertFalse(CycleLogic.isEmpty(CycleDay(epochDay = 1, flow = 1)))
        assertFalse(CycleLogic.isEmpty(CycleDay(epochDay = 1, contraceptive = true)))
        assertFalse(CycleLogic.hasExtras(CycleDay(epochDay = 1, flow = 3)))
        assertTrue(CycleLogic.hasExtras(CycleDay(epochDay = 1, flow = 3, notes = "x")))
        assertTrue(CycleLogic.hasExtras(CycleDay(epochDay = 1, symptoms = "colicos")))
    }

    @Test
    fun withFlowKeepsOtherFieldsAndDeletesWhenEmpty() {
        assertNull(CycleLogic.withFlow(CycleDay(epochDay = 5, flow = 3), 5, 0))
        assertNull(CycleLogic.withFlow(null, 5, 0))
        val kept = CycleLogic.withFlow(CycleDay(epochDay = 5, flow = 3, notes = "nota"), 5, 0)
        assertNotNull(kept)
        assertEquals(0, kept!!.flow)
        assertEquals("nota", kept.notes)
        assertEquals(3, CycleLogic.withFlow(null, 7, 3)!!.flow)
        assertEquals(7L, CycleLogic.withFlow(null, 7, 3)!!.epochDay)
    }

    @Test
    fun summaryListsEverythingRecorded() {
        val d = CycleDay(
            epochDay = 1, flow = 3, symptoms = "colicos,fatiga", moods = "triste", mucus = "clara", basalTemp = 36.55,
            sex = 1, ovulationTest = 2, pregnancyTest = 1, contraceptive = true, notes = " hola ",
        )
        val s = CycleLogic.summary(d)
        assertEquals(
            listOf(
                "Flujo moderado", "Síntomas: cólicos, fatiga", "Ánimo: tristeza", "Flujo cervical: clara de huevo",
                "Temperatura basal: 36.55 °C", "Relaciones: con protección", "Prueba de ovulación: positiva",
                "Prueba de embarazo: negativa", "Anticonceptivo tomado o aplicado", "Notas: hola",
            ),
            s,
        )
        assertEquals(listOf("Manchado"), CycleLogic.summary(CycleDay(epochDay = 1, flow = 1)))
        assertTrue(CycleLogic.summary(CycleDay(epochDay = 1)).isEmpty())
    }

    @Test
    fun statusCountdown() {
        val days = bleed(1000, 5) + bleed(1028, 5) + bleed(1056, 5)
        val s = CycleLogic.todayStatus(analyze(days, today = 1066))
        assertEquals(11, s.cycleDay)
        assertEquals("Periodo en 18 días", s.headline)
        assertFalse(s.inPeriod)
        assertEquals(Phase.FERTIL.label, s.phaseLabel)
    }

    @Test
    fun statusDuringPeriod() {
        val s = CycleLogic.todayStatus(analyze(bleed(1000, 5) + bleed(1028, 3), today = 1030))
        assertTrue(s.inPeriod)
        assertEquals(3, s.cycleDay)
        assertEquals("Día 3 de tu periodo", s.headline)
        assertEquals(Phase.MENSTRUAL.label, s.phaseLabel)
    }

    @Test
    fun statusExpectedTodayAndLateKeepRealCycleDay() {
        val days = bleed(1000, 5) + bleed(1028, 5)
        val expected = CycleLogic.todayStatus(analyze(days, today = 1056))
        assertEquals("Tu periodo podría llegar hoy", expected.headline)
        assertEquals(29, expected.cycleDay)
        val late = CycleLogic.todayStatus(analyze(days, today = 1059))
        assertEquals("3 días de retraso", late.headline)
        assertEquals(32, late.cycleDay)
        assertEquals("Periodo retrasado", late.phaseLabel)
        val late1 = CycleLogic.todayStatus(analyze(days, today = 1057))
        assertEquals("1 día de retraso", late1.headline)
    }

    @Test
    fun statusWithoutDataOrStale() {
        val none = CycleLogic.todayStatus(analyze(emptyList(), today = 500))
        assertNull(none.cycleDay)
        val stale = CycleLogic.todayStatus(analyze(bleed(1000, 5), today = 1400))
        assertNull(stale.cycleDay)
        assertEquals("Sin registros recientes", stale.phaseLabel)
    }

    @Test
    fun quickStates() {
        val days = bleed(1000, 5) + bleed(1028, 3)
        // Ayer hubo sangrado, hoy sin registro
        assertEquals(QuickState.CONTINUE, CycleLogic.quickState(analyze(days, today = 1031), null))
        // Hoy ya registrado
        assertEquals(QuickState.LOGGED, CycleLogic.quickState(analyze(days, today = 1030), CycleDay(1030, 3)))
        assertEquals(QuickState.LOGGED, CycleLogic.quickState(analyze(days, today = 1040), CycleDay(1040, 1)))
        // Sin periodo en curso
        assertEquals(QuickState.START, CycleLogic.quickState(analyze(days, today = 1045), CycleDay(1045, 0, notes = "x")))
        assertEquals(QuickState.START, CycleLogic.quickState(analyze(emptyList(), today = 1045), null))
    }

    @Test
    fun symptomStatsCountAndPhase() {
        val days = bleed(1000, 5).mapIndexed { i, d -> if (i < 2) d.copy(symptoms = "colicos") else d } +
            bleed(1028, 5).mapIndexed { i, d -> if (i == 0) d.copy(symptoms = "colicos,fatiga") else d } +
            CycleDay(epochDay = 1024, symptoms = "senos", moods = "irritable") +
            CycleDay(epochDay = 1014, symptoms = "dolor_ovulacion")
        val a = analyze(days, today = 1040)
        val stats = CycleLogic.symptomStats(days, a)
        assertEquals("colicos", stats[0].key)
        assertEquals(3, stats[0].count)
        assertEquals(Phase.MENSTRUAL, stats[0].topPhase)
        assertEquals("Cólicos", stats[0].label)
        assertEquals(Phase.LUTEA, stats.first { it.key == "senos" }.topPhase)
        // La ovulación (día 1014 = 1028 − 14) cuenta como ventana fértil.
        assertEquals(Phase.FERTIL, stats.first { it.key == "dolor_ovulacion" }.topPhase)
        val moods = CycleLogic.moodStats(days, a)
        assertEquals(1, moods.size)
        assertEquals("Irritabilidad", moods[0].label)
    }

    @Test
    fun gridDaysFillAdjacentMonths() {
        val cells = CycleLogic.monthGridDays(LocalDate.of(2026, 11, 1))
        assertEquals(42, cells.size)
        // Lunes 26 de octubre … domingo 1 de noviembre
        assertEquals(LocalDate.of(2026, 10, 26).toEpochDay() to false, cells[0])
        assertEquals(LocalDate.of(2026, 11, 1).toEpochDay() to true, cells[6])
        assertEquals(LocalDate.of(2026, 12, 6).toEpochDay() to false, cells[41])
        // Días consecutivos
        assertTrue(cells.zipWithNext().all { (a, b) -> b.first == a.first + 1 })
    }

    @Test
    fun rangeTextShortensSameMonth() {
        val a = LocalDate.of(2026, 11, 21).toEpochDay()
        assertEquals("21 – 27 nov", CycleLogic.rangeText(a..(a + 6)))
        assertEquals("28 nov – 4 dic", CycleLogic.rangeText((a + 7)..(a + 13)))
    }

    @Test
    fun plurals() {
        assertEquals("1 día", CycleLogic.dias(1))
        assertEquals("28 días", CycleLogic.dias(28))
    }
}
