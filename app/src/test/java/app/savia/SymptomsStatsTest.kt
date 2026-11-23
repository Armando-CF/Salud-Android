package app.savia

import app.savia.data.SelfExam
import app.savia.data.SymptomEntry
import app.savia.ui.symptoms.SymptomStats
import app.savia.ui.wellbeing.SelfExamGuides
import app.savia.util.epochDayToMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class SymptomsStatsTest {
    private val today = LocalDate.of(2026, 11, 23).toEpochDay() // lunes

    private fun e(daysAgo: Long, name: String, intensity: Int, region: String? = null, tags: String = "", notes: String = "") =
        SymptomEntry(timestamp = epochDayToMillis(today - daysAgo), name = name, intensity = intensity, regionLabel = region, tags = tags, notes = notes)

    @Test
    fun weekStartIsMonday() {
        for (d in today - 20..today + 20) {
            val ws = SymptomStats.weekStart(d)
            assertEquals(DayOfWeek.MONDAY, LocalDate.ofEpochDay(ws).dayOfWeek)
            assertTrue(ws <= d && d - ws < 7)
        }
        assertEquals(today, SymptomStats.weekStart(today))
    }

    @Test
    fun normIgnoresAccentsAndCase() {
        assertEquals("nauseas", SymptomStats.norm("  Náuseas "))
        assertTrue(SymptomStats.matches(e(0, "Migraña", 5, region = "Fosa ilíaca derecha"), "iliaca"))
        assertTrue(SymptomStats.matches(e(0, "Migraña", 5, tags = "Estrés"), "ESTRES"))
        assertFalse(SymptomStats.matches(e(0, "Migraña", 5), "tos"))
    }

    @Test
    fun summarizesWeekly() {
        val list = listOf(
            e(0, "Dolor de cabeza", 6, tags = "Estrés,Pantallas"),
            e(0, "Fatiga", 4),
            e(7, "Dolor de cabeza", 8, region = "Cabeza", tags = "Estrés"),
            e(200, "Tos", 3),
        )
        val s = SymptomStats.summarize(list, fromDay = today - 30, today = today)
        assertEquals(3, s.total)
        assertEquals(6.0, s.avgIntensity!!, 0.001)
        assertEquals(2, s.daysWithSymptoms)
        assertEquals("Dolor de cabeza" to 2, s.topNames.first())
        assertEquals("Dolor de cabeza", s.strongest.first().first)
        assertEquals(7.0, s.strongest.first().second, 0.001)
        assertEquals(listOf("Cabeza" to 1), s.topRegions)
        assertEquals("Estrés" to 2, s.topTriggers.first())
        assertFalse(s.monthly)
        // 30 días atrás (sáb 24 oct) → 6 semanas de lunes a lunes (19 oct … 23 nov); la última con 2 y la anterior con 1
        assertEquals(6, s.buckets.size)
        assertEquals(2, s.buckets.last().second)
        assertEquals(1, s.buckets[s.buckets.size - 2].second)
        assertEquals(3, s.buckets.sumOf { it.second })
    }

    @Test
    fun summarizesMonthlyForLongRanges() {
        val list = listOf(e(0, "Tos", 3), e(40, "Tos", 5), e(200, "Fatiga", 2))
        val s = SymptomStats.summarize(list, fromDay = null, today = today)
        assertTrue(s.monthly)
        assertEquals(3, s.total)
        assertEquals(3, s.buckets.sumOf { it.second })
        assertEquals(1, s.buckets.last().second)
    }

    @Test
    fun emptySummary() {
        val s = SymptomStats.summarize(emptyList(), fromDay = today - 30, today = today)
        assertEquals(0, s.total)
        assertNull(s.avgIntensity)
        assertTrue(s.topNames.isEmpty())
    }

    @Test
    fun selfExamHelpers() {
        assertEquals("mama", SelfExamGuides.orderedTypes("F").first())
        assertEquals("testicular", SelfExamGuides.orderedTypes("M").first())
        assertEquals(3, SelfExamGuides.orderedTypes("X").size)
        assertEquals(3, SelfExamGuides.orderedTypes("M").distinct().size)
        val last = SelfExamGuides.lastByType(listOf(SelfExam(epochDay = 10, type = "piel"), SelfExam(epochDay = 40, type = "piel"), SelfExam(epochDay = 5, type = "mama")))
        assertEquals(40L, last["piel"])
        assertTrue(SelfExamGuides.isDue(10, 40))
        assertFalse(SelfExamGuides.isDue(11, 40))
        assertFalse(SelfExamGuides.isDue(null, 40))
    }
}
