package app.savia

import app.savia.cycle.CycleEngine
import app.savia.cycle.Phase
import app.savia.data.CycleDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CycleEngineTest {
    private fun bleed(start: Long, n: Int) = (0 until n).map { CycleDay(epochDay = start + it, flow = 3) }

    @Test
    fun groupsPeriodsAllowingShortGaps() {
        val days = listOf(CycleDay(100, 3), CycleDay(101, 4), CycleDay(103, 2), CycleDay(104, 1), CycleDay(128, 3), CycleDay(129, 3))
        val p = CycleEngine.periods(days)
        assertEquals(2, p.size)
        assertEquals(100L, p[0].start); assertEquals(103L, p[0].end)
        assertEquals(128L, p[1].start)
    }

    @Test
    fun spottingAloneDoesNotStartAPeriod() {
        assertTrue(CycleEngine.periods(listOf(CycleDay(100, 1), CycleDay(101, 1))).isEmpty())
    }

    @Test
    fun predictsFromAverageOfRecentCycles() {
        // Ciclos de 28, 30, 26 → promedio 28
        val days = bleed(1000, 5) + bleed(1028, 5) + bleed(1058, 4) + bleed(1084, 5)
        val a = CycleEngine.analyze(days, 28, 5, 14, today = 1095)
        assertEquals(4, a.periods.size)
        assertEquals(28, a.cycleLength)
        assertEquals(28.0, a.avgCycle!!, 0.001)
        assertEquals(1084L + 28, a.nextPeriodStart)
        assertEquals(17, a.daysUntilNext)
        assertEquals(12, a.currentCycleDay)
        assertEquals(0, a.lateDays)
        // Ovulación estimada = siguiente periodo − 14
        assertEquals(1112L - 14, a.nextOvulation)
        assertEquals(Phase.OVULACION, a.info(1098).phase)
        assertTrue(a.info(1095).fertile)
        assertEquals(Phase.LUTEA, a.info(1105).phase)
        assertTrue(a.info(1113).predictedPeriod)
        assertEquals(Phase.MENSTRUAL, a.info(1085).phase)
        assertEquals(26, a.shortestCycle); assertEquals(30, a.longestCycle)
        assertEquals(true, a.regular)
    }

    @Test
    fun usesDefaultsWithOnePeriodAndReportsLateness() {
        val a = CycleEngine.analyze(bleed(1000, 5), 30, 5, 14, today = 1034)
        assertNull(a.avgCycle)
        assertEquals(30, a.cycleLength)
        assertEquals(4, a.lateDays)
        // Con retraso, la predicción se ancla a hoy.
        assertEquals(1034L, a.nextPeriodStart)
        assertEquals(0, a.daysUntilNext)
        // Hoy sigue contando desde el último periodo real (día 35), no como día 1 de un periodo estimado.
        assertEquals(35, a.currentCycleDay)
        assertTrue(a.info(1034).predictedPeriod)
        assertEquals(2, a.info(1035).cycleDay)
    }

    @Test
    fun ignoresOutlierCyclesInAverage() {
        // 28, 120 (meses sin registrar), 28
        val days = bleed(1000, 5) + bleed(1028, 5) + bleed(1148, 5) + bleed(1176, 5)
        val a = CycleEngine.analyze(days, 28, 5, 14, today = 1180)
        assertEquals(28.0, a.avgCycle!!, 0.001)
        assertFalse(a.cycles[1].valid)
    }

    @Test
    fun emptyHistory() {
        val a = CycleEngine.analyze(emptyList(), 28, 5, 14, today = 500)
        assertFalse(a.hasData)
        assertNull(a.nextPeriodStart)
        assertNull(a.currentCycleDay)
        assertEquals(Phase.DESCONOCIDA, a.info(500).phase)
    }

    @Test
    fun ongoingPeriodIsExcludedFromPeriodAverage() {
        val days = bleed(1000, 5) + bleed(1028, 2)
        val a = CycleEngine.analyze(days, 28, 5, 14, today = 1029)
        assertEquals(5.0, a.avgPeriod!!, 0.001)
        assertTrue(a.periodOngoing)
    }
}
