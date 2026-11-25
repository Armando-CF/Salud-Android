package app.savia

import app.savia.energy.ActivityLevel
import app.savia.energy.EnergyCalc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnergyCalcTest {
    @Test
    fun mifflinStJeor() {
        // Hombre de 80 kg, 180 cm, 30 años: 800 + 1125 − 150 + 5 = 1780
        assertEquals(1780.0, EnergyCalc.bmr(80.0, 180.0, 30, "M"), 0.001)
        // Mujer de 65 kg, 165 cm, 30 años: 650 + 1031.25 − 150 − 161 = 1370.25
        assertEquals(1370.25, EnergyCalc.bmr(65.0, 165.0, 30, "F"), 0.001)
        // Sin sexo indicado: punto medio entre las dos fórmulas
        val mid = (EnergyCalc.bmr(70.0, 170.0, 40, "M") + EnergyCalc.bmr(70.0, 170.0, 40, "F")) / 2
        assertEquals(mid, EnergyCalc.bmr(70.0, 170.0, 40, "X"), 0.001)
    }

    @Test
    fun totalByActivityLevel() {
        assertEquals(2136.0, EnergyCalc.tdee(1780.0, ActivityLevel.SEDENTARY), 0.001)
        assertEquals(2447.5, EnergyCalc.tdee(1780.0, ActivityLevel.LIGHT), 0.001)
        assertEquals(2759.0, EnergyCalc.tdee(1780.0, ActivityLevel.MODERATE), 0.001)
        assertEquals(3070.5, EnergyCalc.tdee(1780.0, ActivityLevel.VERY), 0.001)
        assertEquals(ActivityLevel.MODERATE, ActivityLevel.of("moderate"))
        assertNull(ActivityLevel.of(""))
    }

    @Test
    fun breakdownAddsUpToTheTotal() {
        val b = EnergyCalc.breakdown(1780.0, ActivityLevel.MODERATE)
        assertEquals(2759.0, b.total, 0.001)
        assertEquals(275.9, b.digestion, 0.001)
        assertEquals(1780.0, b.basal, 0.001)
        assertEquals(703.1, b.movement, 0.001)
        assertEquals(100, b.share(b.basal) + b.share(b.digestion) + b.share(b.movement))
        // En sedentario casi todo es basal y el movimiento no puede salir negativo.
        assertEquals(true, EnergyCalc.breakdown(1780.0, ActivityLevel.SEDENTARY).movement >= 0)
    }

    @Test
    fun exerciseWithMets() {
        // Caminar (3.5 METs) 60 min con 70 kg = 245 kcal; correr a 8 km/h 30 min = 280 kcal
        assertEquals(245.0, EnergyCalc.exerciseKcal(3.5, 70.0, 60), 0.001)
        assertEquals(280.0, EnergyCalc.exerciseKcal(8.0, 70.0, 30), 0.001)
        assertEquals(250, EnergyCalc.round10(245.0))
        assertEquals(2760, EnergyCalc.round10(2759.0))
    }

    @Test
    fun suggestsLevelFromLoggedExercise() {
        assertEquals(ActivityLevel.SEDENTARY, EnergyCalc.suggestLevel(0.5))
        assertEquals(ActivityLevel.LIGHT, EnergyCalc.suggestLevel(2.0))
        assertEquals(ActivityLevel.MODERATE, EnergyCalc.suggestLevel(4.0))
        assertEquals(ActivityLevel.VERY, EnergyCalc.suggestLevel(6.0))
        // 28 días registrados, ejercicio cada tercer día (10 días) → 2.5 días por semana
        val logs = (0 until 28).map { (1000L - it) to (if (it % 3 == 0) 40 else 0) }
        assertEquals(2.5, EnergyCalc.exerciseDaysPerWeek(logs, today = 1000)!!, 0.001)
        // Con pocos días registrados no se sugiere nada
        assertNull(EnergyCalc.exerciseDaysPerWeek(logs.take(5), today = 1000))
        // Los días viejos no cuentan
        assertNull(EnergyCalc.exerciseDaysPerWeek(logs.map { (it.first - 100) to it.second }, today = 1000))
    }

    @Test
    fun balanceFromWeightTrend() {
        // Baja 0.5 kg por semana → déficit de 550 kcal al día
        val pts = (0..6).map { (it * 7).toDouble() to (80.0 - it * 0.5) }
        val slope = EnergyCalc.weightSlopePerWeek(pts)!!
        assertEquals(-0.5, slope, 0.0001)
        assertEquals(-550.0, EnergyCalc.dailyBalanceFromTrend(slope), 0.01)
        // Peso estable → balance cero
        assertEquals(0.0, EnergyCalc.dailyBalanceFromTrend(EnergyCalc.weightSlopePerWeek((0..5).map { (it * 5).toDouble() to 70.0 })!!), 0.001)
        // Pocos datos o pocos días: no se calcula
        assertNull(EnergyCalc.weightSlopePerWeek(pts.take(3)))
        assertNull(EnergyCalc.weightSlopePerWeek((0..5).map { it.toDouble() to 70.0 }))
    }
}
