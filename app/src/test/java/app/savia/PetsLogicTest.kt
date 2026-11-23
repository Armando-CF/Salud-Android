package app.savia

import app.savia.data.MeasureType
import app.savia.data.Measurement
import app.savia.data.PetSymptomCatalog
import app.savia.data.SymptomCatalog
import app.savia.ui.measure.Level
import app.savia.ui.measure.MeasureRefs
import app.savia.ui.measure.MeasureStats
import app.savia.ui.measure.MeasureWho
import app.savia.ui.symptoms.SymptomKit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Lógica pura de la adaptación a mascotas y a menores de edad (sin Android). */
class PetsLogicTest {
    private val today = LocalDate.of(2026, 11, 23).toEpochDay()
    private fun born(yearsAgo: Long, extraDays: Long = 0) = LocalDate.of(2026, 11, 23).minusYears(yearsAgo).plusDays(extraDays).toEpochDay()

    private val adult = MeasureWho()
    private val child = MeasureWho(isMinor = true)
    private val dog = MeasureWho(isPet = true, species = "perro", name = "Firulais")
    private val cat = MeasureWho(isPet = true, species = "gato")
    private val bird = MeasureWho(isPet = true, species = "ave")

    private fun m(type: MeasureType, v: Double, v2: Double? = null, tag: String = "") =
        Measurement(timestamp = 0L, type = type.key, value = v, value2 = v2, tag = tag)

    // ── Peso de mascotas: gramos bajo 1 kg, 2 decimales arriba ──

    @Test
    fun weightIsShownInGramsBelowOneKiloForPets() {
        assertEquals("350 g", MeasureRefs.weightText(0.35, isPet = true))
        assertEquals("28 g", MeasureRefs.weightText(0.028, isPet = true))
        assertEquals("999 g", MeasureRefs.weightText(0.999, isPet = true))
        assertEquals("1 kg", MeasureRefs.weightText(1.0, isPet = true))
        assertEquals("12.35 kg", MeasureRefs.weightText(12.345, isPet = true))
        assertEquals("4.2 kg", MeasureRefs.weightText(4.2, isPet = true))
        assertEquals("350" to "g", MeasureRefs.weightParts(0.35, isPet = true))
    }

    @Test
    fun peopleWeightKeepsOneDecimalInKilos() {
        assertEquals("72.5 kg", MeasureRefs.weightText(72.5, isPet = false))
        assertEquals("0.4 kg", MeasureRefs.weightText(0.35, isPet = false))   // nunca en gramos
        assertEquals("72.5" to "kg", MeasureRefs.weightParts(72.5, isPet = false))
        assertEquals("+0.4 kg", MeasureRefs.weightDelta(0.4, isPet = false))
    }

    @Test
    fun weightChangesUseGramsWhenSmallForPets() {
        assertEquals("+25 g", MeasureRefs.weightDelta(0.025, isPet = true))
        assertEquals("−120 g", MeasureRefs.weightDelta(-0.12, isPet = true))
        assertEquals("+1.25 kg", MeasureRefs.weightDelta(1.25, isPet = true))
        assertEquals("0 g", MeasureRefs.weightDelta(0.0, isPet = true))
    }

    @Test
    fun petWeightCanBeEnteredAndEditedWithGramPrecision() {
        assertEquals(3, MeasureRefs.inputDecimals(MeasureType.WEIGHT, isPet = true))
        assertEquals(1, MeasureRefs.inputDecimals(MeasureType.WEIGHT, isPet = false))
        assertEquals(1, MeasureRefs.inputDecimals(MeasureType.TEMPERATURE, isPet = true))
        val now = 1_000_000L
        // Un hámster de 35 g es válido como mascota, pero no como peso de una persona.
        assertNull(MeasureStats.validate(MeasureType.WEIGHT, 0.035, null, now, now, isPet = true))
        assertNotNull(MeasureStats.validate(MeasureType.WEIGHT, 0.035, null, now, now, isPet = false))
        assertNotNull(MeasureStats.validate(MeasureType.WEIGHT, 0.035, null, now, now))
        assertNull(MeasureStats.validate(MeasureType.WEIGHT, 3.2, null, now, now))            // recién nacido
        assertNotNull(MeasureStats.validate(MeasureType.WEIGHT, 0.0, null, now, now, isPet = true))
    }

    // ── Tipos por perfil ──

    @Test
    fun petsOnlySeeTheMeasurementsThatApplyToThem() {
        val pet = MeasureRefs.types(isPet = true)
        assertEquals(listOf(MeasureType.WEIGHT, MeasureType.HEART_RATE, MeasureType.TEMPERATURE), pet)
        val people = MeasureRefs.types(isPet = false)
        assertTrue(MeasureType.HEIGHT in people)
        assertTrue(MeasureType.BLOOD_PRESSURE in people)
        assertEquals(MeasureType.entries.size, people.size)
    }

    // ── Menores de edad ──

    @Test
    fun minorIsDecidedByBirthDate() {
        assertTrue(MeasureWho.isMinor(born(7), today))
        assertTrue(MeasureWho.isMinor(born(18, extraDays = 1), today))      // cumple 18 mañana
        assertFalse(MeasureWho.isMinor(born(18), today))                    // cumple 18 hoy
        assertFalse(MeasureWho.isMinor(born(40), today))
        assertFalse(MeasureWho.isMinor(null, today))                        // sin fecha: se asume persona adulta
        assertTrue(MeasureWho.of(isPet = false, species = "", birthEpochDay = born(7), today = today).isMinor)
        // Una mascota joven no es "menor": simplemente no lleva referencias humanas.
        val puppy = MeasureWho.of(isPet = true, species = "perro", birthEpochDay = born(1), today = today)
        assertFalse(puppy.isMinor)
        assertFalse(puppy.isAdult)
        assertTrue(puppy.isPet)
    }

    @Test
    fun minorsGetTheBmiNumberButNoAdultCategory() {
        val weight = m(MeasureType.WEIGHT, 24.0)
        // El número se sigue pudiendo calcular…
        assertEquals(16.3, MeasureStats.bmi(24.0, 121.5)!!, 0.05)
        assertTrue(MeasureRefs.showBmi(child))
        // …pero sin categoría de la OMS para personas adultas.
        assertFalse(MeasureRefs.showBmiCategory(child))
        assertNull(MeasureRefs.assess(child, MeasureType.WEIGHT, weight, 121.5))
        assertEquals(MeasureRefs.MINOR_BMI_NOTE, MeasureRefs.note(child, MeasureType.WEIGHT))
        assertTrue(MeasureRefs.MINOR_BMI_NOTE.contains("pediatra"))
        // En personas adultas todo sigue igual.
        assertTrue(MeasureRefs.showBmiCategory(adult))
        assertEquals("Peso saludable", MeasureRefs.assess(adult, MeasureType.WEIGHT, m(MeasureType.WEIGHT, 64.0), 168.0)!!.label)
        assertNull(MeasureRefs.note(adult, MeasureType.WEIGHT))
    }

    @Test
    fun minorsAreNotClassifiedWithAdultPressureOrPulseRanges() {
        assertNull(MeasureRefs.assess(child, MeasureType.BLOOD_PRESSURE, m(MeasureType.BLOOD_PRESSURE, 150.0, 95.0), null))
        assertNull(MeasureRefs.assess(child, MeasureType.HEART_RATE, m(MeasureType.HEART_RATE, 110.0), null))
        assertNotNull(MeasureRefs.note(child, MeasureType.BLOOD_PRESSURE))
        assertNotNull(MeasureRefs.note(child, MeasureType.HEART_RATE))
        assertNull(MeasureRefs.band(child, MeasureType.HEART_RATE, emptyList()))
        // Fiebre, oxigenación y glucosa sí se orientan igual.
        assertEquals(Level.BAD, MeasureRefs.assess(child, MeasureType.TEMPERATURE, m(MeasureType.TEMPERATURE, 38.6), null)!!.level)
        assertNotNull(MeasureRefs.assess(child, MeasureType.SPO2, m(MeasureType.SPO2, 97.0), null))
        // Personas adultas: sin cambios.
        assertEquals("Alta (grado 2)", MeasureRefs.assess(adult, MeasureType.BLOOD_PRESSURE, m(MeasureType.BLOOD_PRESSURE, 150.0, 95.0), null)!!.label)
        assertEquals(60.0 to 100.0, MeasureRefs.band(adult, MeasureType.HEART_RATE, emptyList()))
    }

    // ── Mascotas: sin referencias humanas ──

    @Test
    fun petsNeverGetHumanClassifications() {
        assertFalse(MeasureRefs.showBmi(dog))
        for (type in MeasureType.entries) {
            assertNull("$type", MeasureRefs.assess(dog, type, m(type, 39.0, 20.0), 170.0))
        }
        // 39 °C sería "fiebre" en una persona y es normal en un perro: no debe clasificarse.
        assertEquals(Level.BAD, MeasureRefs.assess(adult, MeasureType.TEMPERATURE, m(MeasureType.TEMPERATURE, 39.0), null)!!.level)
    }

    @Test
    fun petNotesAndBandsExistOnlyForDogsAndCats() {
        assertTrue(MeasureRefs.note(dog, MeasureType.TEMPERATURE)!!.contains("38"))
        assertTrue(MeasureRefs.note(cat, MeasureType.HEART_RATE)!!.contains("140"))
        assertNotNull(MeasureRefs.note(dog, MeasureType.WEIGHT))
        assertNull(MeasureRefs.note(bird, MeasureType.TEMPERATURE))
        assertNull(MeasureRefs.note(bird, MeasureType.WEIGHT))
        assertEquals(38.0 to 39.2, MeasureRefs.band(dog, MeasureType.TEMPERATURE, emptyList()))
        assertEquals(140.0 to 220.0, MeasureRefs.band(cat, MeasureType.HEART_RATE, emptyList()))
        assertEquals(60.0 to 140.0, MeasureRefs.band(dog, MeasureType.HEART_RATE, emptyList()))
        assertNull(MeasureRefs.band(bird, MeasureType.TEMPERATURE, emptyList()))
        // La banda humana de temperatura no se aplica a mascotas.
        assertEquals(36.0 to 37.4, MeasureRefs.band(adult, MeasureType.TEMPERATURE, emptyList()))
    }

    // ── Síntomas ──

    @Test
    fun symptomCatalogDependsOnTheProfile() {
        val person = SymptomKit(isPet = false)
        val pet = SymptomKit(isPet = true, species = "perro", name = "Firulais")
        assertEquals(SymptomCatalog.groups, person.groups)
        assertEquals(PetSymptomCatalog.groups, pet.groups)
        assertTrue("Vómito" in pet.all)
        assertTrue("Cojera" in pet.all)
        assertFalse("Migraña" in pet.all)
        assertTrue("Cambio de alimento" in pet.triggers)
        assertFalse("Pantallas" in pet.triggers)
        assertEquals("¿Qué le notas?", pet.whatTitle)
        assertEquals("¿Qué sientes?", person.whatTitle)
        assertTrue(pet.alarmNote.contains("veterinari"))
        assertTrue(person.alarmNote.contains("atención médica"))
        assertTrue(pet.emptyMessage.contains("Firulais"))
    }

    @Test
    fun bodyMapIsOfferedOnlyWhereThereIsAFigure() {
        assertTrue(SymptomKit(isPet = false).canPickBodyRegion)
        assertTrue(SymptomKit(isPet = true, species = "perro").canPickBodyRegion)
        assertTrue(SymptomKit(isPet = true, species = "gato").canPickBodyRegion)
        assertFalse(SymptomKit(isPet = true, species = "ave").canPickBodyRegion)
        assertFalse(SymptomKit(isPet = true, species = "").canPickBodyRegion)
    }

    @Test
    fun petCatalogIsWellFormed() {
        assertEquals(8, PetSymptomCatalog.groups.size)
        assertEquals("sin duplicados", PetSymptomCatalog.all.size, PetSymptomCatalog.all.distinct().size)
        assertTrue(PetSymptomCatalog.all.none { it.isBlank() })
        assertEquals("Apenas se nota", PetSymptomCatalog.intensityLabel(1))
        assertEquals("Moderado", PetSymptomCatalog.intensityLabel(5))
        assertEquals("Muy fuerte", PetSymptomCatalog.intensityLabel(10))
        assertTrue(PetSymptomCatalog.alarmSummary.startsWith("Acude a urgencias veterinarias"))
    }
}
