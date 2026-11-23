package app.savia

import app.savia.data.MeasureType
import app.savia.data.Measurement
import app.savia.ui.measure.Level
import app.savia.ui.measure.MeasureStats
import app.savia.ui.measure.TPoint
import app.savia.util.epochDayToMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasureStatsTest {
    private fun line(n: Int, start: Double, slopePerDay: Double, stepDays: Int = 1, x0: Double = 20000.0) =
        (0 until n).map { TPoint(x0 + it * stepDays, start + slopePerDay * it * stepDays) }

    @Test
    fun regressionRecoversSlopeAndIntercept() {
        val r = MeasureStats.regression(line(30, 80.0, -0.1))!!
        assertEquals(-0.1, r.slopePerDay, 1e-9)
        assertEquals(-0.7, r.perWeek, 1e-9)
        assertEquals(80.0, r.at(20000.0), 1e-6)
        assertNull(MeasureStats.regression(listOf(TPoint(1.0, 2.0))))
        assertNull(MeasureStats.regression(listOf(TPoint(1.0, 2.0), TPoint(1.0, 3.0))))
    }

    @Test
    fun movingAverageUsesSevenDayWindow() {
        val pts = (0..9).map { TPoint(100.0 + it, it.toDouble()) }
        val ma = MeasureStats.movingAverage(pts)
        assertEquals(10, ma.size)
        assertEquals(0.0, ma[0].y, 1e-9)
        assertEquals(1.0, ma[2].y, 1e-9) // (0+1+2)/3
        assertEquals(6.0, ma[9].y, 1e-9) // promedio de 3..9
        assertTrue(MeasureStats.movingAverage(emptyList()).isEmpty())
    }

    @Test
    fun changeOverComparesWithClosestMeasurement() {
        val pts = line(21, 70.0, -0.1, stepDays = 3) // cada 3 días, 60 días
        assertEquals(-0.6, MeasureStats.changeOver(pts, 7)!!, 1e-9) // referencia: hace 6 días
        assertEquals(-3.0, MeasureStats.changeOver(pts, 30)!!, 1e-9)
        // Sin referencia cercana → null
        assertNull(MeasureStats.changeOver(listOf(TPoint(0.0, 70.0), TPoint(100.0, 69.0)), 7))
        assertNull(MeasureStats.changeOver(listOf(TPoint(0.0, 70.0)), 7))
    }

    @Test
    fun summaryOfValues() {
        val s = MeasureStats.summary(listOf(3.0, 1.0, 5.0))!!
        assertEquals(1.0, s.min, 0.0)
        assertEquals(5.0, s.max, 0.0)
        assertEquals(3.0, s.avg, 1e-9)
        assertEquals(2.0, s.change, 0.0)
        assertNull(MeasureStats.summary(emptyList()))
    }

    @Test
    fun bmiAndCategories() {
        assertEquals(24.22, MeasureStats.bmi(70.0, 170.0)!!, 0.01)
        assertNull(MeasureStats.bmi(70.0, null))
        assertEquals("Bajo peso", MeasureStats.bmiCategory(18.4).label)
        assertEquals(Level.GOOD, MeasureStats.bmiCategory(22.0).level)
        assertEquals("Sobrepeso", MeasureStats.bmiCategory(27.0).label)
        assertEquals("Obesidad grado I", MeasureStats.bmiCategory(31.0).label)
        assertEquals("Obesidad grado III", MeasureStats.bmiCategory(41.0).label)
        val (lo, hi) = MeasureStats.healthyWeightRange(170.0)
        assertEquals(53.5, lo, 0.1)
        assertEquals(72.0, hi, 0.1)
    }

    @Test
    fun bloodPressureClasses() {
        assertEquals("Normal", MeasureStats.bloodPressure(115.0, 75.0).label)
        assertEquals("Elevada", MeasureStats.bloodPressure(125.0, 75.0).label)
        assertEquals("Alta (grado 1)", MeasureStats.bloodPressure(118.0, 84.0).label)
        assertEquals("Alta (grado 1)", MeasureStats.bloodPressure(135.0, 70.0).label)
        assertEquals("Alta (grado 2)", MeasureStats.bloodPressure(150.0, 85.0).label)
        assertEquals("Revisar con urgencia", MeasureStats.bloodPressure(185.0, 100.0).label)
        assertEquals("Baja", MeasureStats.bloodPressure(85.0, 55.0).label)
    }

    @Test
    fun glucoseDependsOnContext() {
        assertEquals("Normal", MeasureStats.glucose(95.0, "En ayunas").label)
        assertEquals("Elevada", MeasureStats.glucose(110.0, "En ayunas").label)
        assertEquals("Alta", MeasureStats.glucose(130.0, "En ayunas").label)
        assertEquals("Normal", MeasureStats.glucose(130.0, "2 h después de comer").label)
        assertEquals("Elevada", MeasureStats.glucose(160.0, "2 h después de comer").label)
        assertEquals("Baja", MeasureStats.glucose(65.0, "").label)
        assertEquals(Level.BAD, MeasureStats.glucose(50.0, "En ayunas").level)
        assertEquals(Level.GOOD, MeasureStats.glucose(120.0, "").level)
    }

    @Test
    fun otherClassifications() {
        assertEquals(Level.GOOD, MeasureStats.spo2(97.0).level)
        assertEquals(Level.WARN, MeasureStats.spo2(93.0).level)
        assertEquals(Level.BAD, MeasureStats.spo2(89.0).level)
        assertEquals("Normal", MeasureStats.temperature(36.6).label)
        assertEquals("Febrícula", MeasureStats.temperature(37.7).label)
        assertEquals("Fiebre", MeasureStats.temperature(38.4).label)
        assertEquals("Normal", MeasureStats.heartRate(72.0, "En reposo").label)
        assertEquals(Level.WARN, MeasureStats.heartRate(110.0, "").level)
        assertEquals(Level.NEUTRAL, MeasureStats.heartRate(150.0, "Después de ejercicio").level)
        assertEquals(Level.GOOD, MeasureStats.waistToHeight(78.0, 168.0).level)
        assertEquals(Level.WARN, MeasureStats.waistToHeight(90.0, 168.0).level)
    }

    @Test
    fun assessByType() {
        val bp = Measurement(timestamp = 0, type = "bp", value = 150.0, value2 = 95.0)
        assertEquals("Alta (grado 2)", MeasureStats.assess(MeasureType.BLOOD_PRESSURE, bp)!!.label)
        val w = Measurement(timestamp = 0, type = "weight", value = 70.0)
        assertNull(MeasureStats.assess(MeasureType.WEIGHT, w, null))
        assertEquals("Peso saludable", MeasureStats.assess(MeasureType.WEIGHT, w, 170.0)!!.label)
        assertNull(MeasureStats.assess(MeasureType.HIP, Measurement(timestamp = 0, type = "hip", value = 95.0), 170.0))
    }

    @Test
    fun goalProjectionOnlyWhenTrendingTowardsGoal() {
        val pts = line(31, 73.0, -0.1) // baja 0.1 kg/día; último = 70.0 en el día 20030
        val p = MeasureStats.goalProjection(pts, 68.0, today = 20030)!!
        assertEquals(20, p.daysLeft)
        assertEquals(20050L, p.epochDay)
        // La meta está arriba pero la tendencia baja → sin proyección
        assertNull(MeasureStats.goalProjection(pts, 75.0, today = 20030))
        // Tendencia plana
        assertNull(MeasureStats.goalProjection(line(10, 70.0, 0.0), 68.0, today = 20010))
        // Ya en la meta
        assertEquals(0, MeasureStats.goalProjection(pts, 70.0, today = 20030)!!.daysLeft)
        // Demasiado lejos (más de 3 años)
        assertNull(MeasureStats.goalProjection(line(31, 100.0, -0.005), 60.0, today = 20030))
    }

    @Test
    fun goalReachedAndProgress() {
        assertTrue(MeasureStats.goalReached(first = 80.0, current = 69.9, goal = 70.0))
        assertFalse(MeasureStats.goalReached(first = 80.0, current = 72.0, goal = 70.0))
        assertTrue(MeasureStats.goalReached(first = 50.0, current = 55.0, goal = 55.0))
        assertEquals(0.5f, MeasureStats.goalProgress(80.0, 75.0, 70.0), 1e-6f)
        assertEquals(0f, MeasureStats.goalProgress(80.0, 82.0, 70.0), 1e-6f)
        assertEquals(1f, MeasureStats.goalProgress(80.0, 68.0, 70.0), 1e-6f)
    }

    @Test
    fun weightSummaryForCards() {
        val t = 20000L
        val ws = (0..20).map { Measurement(timestamp = epochDayToMillis(t + it * 3L, 8 * 60), type = "weight", value = 72.0 - it * 0.1) }
        val s = MeasureStats.weightSummary(ws.shuffled(), heightCm = 170.0, goal = 68.0)!!
        assertEquals(70.0, s.latest, 1e-9)
        assertEquals(-0.2, s.change7!!, 1e-9)
        assertEquals(-1.0, s.change30!!, 1e-9)
        assertEquals(-2.0, s.toGoal!!, 1e-9)
        assertEquals(0.5f, s.goalProgress!!, 1e-4f)
        assertFalse(s.goalReached)
        assertNotNull(s.bmiAssessment)
        assertEquals(20, s.spark.size)
        assertEquals(70.0, s.spark.last(), 1e-9)
        assertNull(MeasureStats.weightSummary(emptyList(), 170.0, null))
    }

    @Test
    fun validation() {
        val now = 1_000_000_000_000L
        assertNotNull(MeasureStats.validate(MeasureType.WEIGHT, null, null, now, now))
        assertNotNull(MeasureStats.validate(MeasureType.WEIGHT, 900.0, null, now, now))
        assertNull(MeasureStats.validate(MeasureType.WEIGHT, 72.5, null, now, now))
        assertNotNull(MeasureStats.validate(MeasureType.BLOOD_PRESSURE, 120.0, null, now, now))
        assertNotNull(MeasureStats.validate(MeasureType.BLOOD_PRESSURE, 80.0, 120.0, now, now))
        assertNull(MeasureStats.validate(MeasureType.BLOOD_PRESSURE, 120.0, 80.0, now, now))
        assertNotNull(MeasureStats.validate(MeasureType.WEIGHT, 72.5, null, now + 3_600_000L, now))
    }
}
