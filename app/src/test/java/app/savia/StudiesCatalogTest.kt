package app.savia

import app.savia.data.Study
import app.savia.data.StudyResult
import app.savia.ui.studies.RangeFlag
import app.savia.ui.studies.StudyCatalog
import app.savia.ui.studies.flag
import app.savia.ui.studies.normalizeName
import app.savia.ui.studies.paramSeries
import app.savia.ui.studies.paramSummaries
import app.savia.ui.studies.rangeText
import app.savia.ui.studies.upcomingFollowUps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudiesCatalogTest {
    @Test
    fun flagClassifiesAgainstRange() {
        assertEquals(RangeFlag.NORMAL, flag(92.0, 70.0, 99.0))
        assertEquals(RangeFlag.NORMAL, flag(70.0, 70.0, 99.0))
        assertEquals(RangeFlag.NORMAL, flag(99.0, 70.0, 99.0))
        assertEquals(RangeFlag.BAJO, flag(69.9, 70.0, 99.0))
        assertEquals(RangeFlag.ALTO, flag(99.1, 70.0, 99.0))
    }

    @Test
    fun flagWithOpenRanges() {
        assertEquals(RangeFlag.ALTO, flag(212.0, null, 200.0))
        assertEquals(RangeFlag.NORMAL, flag(185.0, null, 200.0))
        assertEquals(RangeFlag.BAJO, flag(35.0, 40.0, null))
        assertEquals(RangeFlag.NORMAL, flag(55.0, 40.0, null))
    }

    @Test
    fun flagWithoutRangeOrValue() {
        assertEquals(RangeFlag.SIN_RANGO, flag(5.0, null, null))
        assertEquals(RangeFlag.SIN_RANGO, flag(null, 1.0, 2.0))
    }

    @Test
    fun normalizeIgnoresCaseAccentsAndSpaces() {
        assertEquals("trigliceridos", normalizeName("  Triglicéridos "))
        assertEquals("acido urico", normalizeName("ÁCIDO   Úrico"))
    }

    @Test
    fun catalogFindsByNameAndAlias() {
        assertEquals("Glucosa", StudyCatalog.find("glucosa")?.name)
        assertEquals("Colesterol HDL", StudyCatalog.find("hdl")?.name)
        assertEquals("Triglicéridos", StudyCatalog.find("TRIGLICERIDOS")?.name)
        assertNull(StudyCatalog.find("no existe"))
        assertNull(StudyCatalog.find(""))
    }

    @Test
    fun catalogRangesDependOnSex() {
        val hb = StudyCatalog.find("Hemoglobina")!!
        assertEquals(12.0 to 16.0, hb.rangeFor("F"))
        assertEquals(13.5 to 17.5, hb.rangeFor("M"))
        assertEquals(12.0 to 17.5, hb.rangeFor("X"))
        val hdl = StudyCatalog.find("Colesterol HDL")!!
        assertEquals(50.0 to null, hdl.rangeFor("F"))
    }

    @Test
    fun catalogIsConsistent() {
        for (p in StudyCatalog.params) {
            if (p.refMin != null && p.refMax != null) assertTrue(p.name, p.refMin < p.refMax)
        }
        for (g in StudyCatalog.groups) for (n in g.params) assertNotNull("$n en ${g.name}", StudyCatalog.find(n))
        assertEquals(StudyCatalog.params.size, StudyCatalog.params.map { normalizeName(it.name) }.distinct().size)
    }

    @Test
    fun suggestionsPreferPrefixAndIncludeUserNames() {
        val s = StudyCatalog.suggest("col", extraNames = listOf("Colinesterasa"))
        assertEquals("Colesterol total", s.first())
        assertTrue("Colinesterasa" in s)
        assertTrue(StudyCatalog.suggest("").isEmpty())
        // Un nombre ya completo no se sugiere a sí mismo.
        assertTrue("Glucosa" !in StudyCatalog.suggest("glucosa"))
    }

    @Test
    fun rangeTextFormats() {
        assertEquals("70 – 99", rangeText(70.0, 99.0))
        assertEquals("< 200", rangeText(null, 200.0))
        assertEquals("> 40", rangeText(40.0, null))
        assertEquals("", rangeText(null, null))
    }

    @Test
    fun seriesMatchesNamesLooselyAndSortsByDate() {
        val studies = listOf(Study(id = 1, epochDay = 300, title = "B"), Study(id = 2, epochDay = 100, title = "A"))
        val results = listOf(
            StudyResult(id = 1, studyId = 1, name = "Triglicéridos", value = 138.0),
            StudyResult(id = 2, studyId = 2, name = "trigliceridos ", value = 160.0),
            StudyResult(id = 3, studyId = 2, name = "Glucosa", value = 95.0),
            StudyResult(id = 4, studyId = 2, name = "Orina", textValue = "Normal"),
            StudyResult(id = 5, studyId = 99, name = "Glucosa", value = 1.0),
        )
        val series = paramSeries("TRIGLICERIDOS", studies, results)
        assertEquals(listOf(160.0, 138.0), series.map { it.value })
        val summaries = paramSummaries(studies, results)
        assertEquals(listOf("Glucosa", "Triglicéridos"), summaries.map { it.name })
        assertEquals(138.0, summaries[1].latest.value, 0.0)
        assertEquals(160.0, summaries[1].previous!!.value, 0.0)
    }

    @Test
    fun followUpsAreSortedAndOldOnesDropped() {
        val studies = listOf(
            Study(id = 1, epochDay = 0, title = "Lejano", followUpEpochDay = 1200),
            Study(id = 2, epochDay = 0, title = "Próximo", followUpEpochDay = 1010),
            Study(id = 3, epochDay = 0, title = "Vencido hace poco", followUpEpochDay = 990),
            Study(id = 4, epochDay = 0, title = "Muy viejo", followUpEpochDay = 500),
            Study(id = 5, epochDay = 0, title = "Sin seguimiento"),
        )
        assertEquals(listOf(3L, 2L, 1L), upcomingFollowUps(studies, today = 1000).map { it.id })
    }
}
