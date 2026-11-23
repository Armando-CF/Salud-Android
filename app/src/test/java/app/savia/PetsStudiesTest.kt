package app.savia

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.savia.data.Profile
import app.savia.data.SaviaDb
import app.savia.data.StudyResult
import app.savia.ui.meds.MedsText
import app.savia.ui.studies.StudyFiles
import app.savia.ui.studies.StudyProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.time.LocalDate

/** Estudios y medicamentos adaptados al tipo de perfil (mascota, menor de edad) y adjuntos por perfil. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PetsStudiesTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        Savia.init(ctx, SaviaDb.createInMemory(ctx))
    }

    private fun createPet(species: String = "perro"): Int = createAndActivate(Profile(name = "Firulais", kind = "pet", species = species))

    companion object {
        /**
         * Crea un perfil, lo activa y espera a que `Savia.active` refleje su ficha.
         *
         * Hace falta esperar porque el observador de perfiles de Savia publica los resúmenes desde otro
         * hilo: justo después de crear un perfil puede dejar por un instante (o, si coincide con el
         * cambio de perfil, de forma permanente) un resumen que no corresponde. Volver a guardar la
         * ficha una vez activado el perfil fuerza una publicación ya con el perfil correcto.
         */
        fun createAndActivate(profile: Profile): Int = runBlocking {
            val id = Savia.createProfile(profile)
            Thread.sleep(200)
            Savia.switchTo(id)
            val deadline = System.currentTimeMillis() + 5_000
            fun settled() = Savia.active.value.let { it.id == id && it.kind == profile.kind && it.birthEpochDay == profile.birthEpochDay }
            while (!settled() && System.currentTimeMillis() < deadline) {
                Savia.db.profile().save(profile.copy(id = 1))
                Thread.sleep(50)
            }
            check(settled()) { "Savia.active no refleja el perfil $id: ${Savia.active.value}" }
            id
        }
    }

    @Test
    fun petsGetOnlyTheUnitFromTheCatalogButReuseTheirOwnRanges() {
        val pet = StudyProfile(isPet = true, species = "perro")
        // Catálogo: solo unidad, sin rango (los rangos del catálogo son de personas adultas).
        assertEquals(Triple("mg/dL", null, null), pet.defaultsFor("Glucosa", emptyList()))
        assertEquals(Triple("mg/dL", null, null), pet.defaultsFor("creatinina", emptyList()))
        assertNull(pet.defaultsFor("Parámetro inventado", emptyList()))
        // Si ya capturó un rango para ese nombre, ese sí se reutiliza (aunque varíen mayúsculas/acentos).
        val past = listOf(
            StudyResult(id = 1, studyId = 1, name = "Glucosa", value = 90.0, unit = "mg/dL", refMin = 65.0, refMax = 118.0),
            StudyResult(id = 2, studyId = 1, name = "Observaciones", textValue = "Sin alteraciones"),
        )
        assertEquals(Triple("mg/dL", 65.0, 118.0), pet.defaultsFor("GLUCOSA", past))
        // Una persona adulta sí recibe el rango orientativo, según su sexo.
        assertEquals(Triple("mg/dL", 70.0, 99.0), StudyProfile().defaultsFor("Glucosa", emptyList()))
        assertEquals(Triple("mg/dL", 0.5, 1.1), StudyProfile(sex = "F").defaultsFor("Creatinina", emptyList()))
    }

    @Test
    fun categoriesAndTitlesDependOnTheProfile() {
        val person = StudyProfile()
        val dog = StudyProfile(isPet = true, species = "perro")
        val cat = StudyProfile(isPet = true, species = "gato")

        // "Vacuna" ya no se ofrece a nadie…
        assertFalse(person.categories().any { it.first == "vacuna" })
        assertFalse(dog.categories().any { it.first == "vacuna" })
        // …salvo al editar un estudio que ya la tenía, para no cambiarle la categoría sin avisar.
        assertEquals("vacuna", person.categories(current = "vacuna").last().first)
        assertEquals("vacuna", dog.categories(current = "vacuna").last().first)
        assertTrue(person.titlesFor("vacuna").isEmpty())

        // Personas: todas las demás categorías.
        assertTrue(person.categories().map { it.first }.containsAll(listOf("lab", "gine", "uro", "vista", "oido")))
        // Mascotas: sin las que no aplican.
        val petKeys = dog.categories().map { it.first }
        assertEquals(listOf("lab", "imagen", "chequeo", "cardio", "dental", "piel", "otro"), petKeys)
        assertEquals("Consulta o revisión", dog.categoryShort("chequeo"))
        assertEquals("Chequeo general", person.categoryShort("chequeo"))

        assertTrue(dog.titlesFor("lab").containsAll(listOf("Química sanguínea", "Coproparasitoscópico", "Prueba de parvovirus y moquillo")))
        assertTrue(cat.titlesFor("lab").contains("Prueba de leucemia e inmunodeficiencia felina"))
        assertFalse(cat.titlesFor("lab").contains("Prueba de parvovirus y moquillo"))
        assertTrue(dog.titlesFor("chequeo").contains("Consulta veterinaria"))
        assertTrue(dog.titlesFor("otro").contains("Esterilización"))
        val allPetTitles = petKeys.flatMap { dog.titlesFor(it) + cat.titlesFor(it) }
        for (bad in listOf("Papanicolaou", "Mastografía", "Antígeno prostático", "Examen de la vista", "Audiometría")) {
            assertFalse("no debe sugerirse a mascotas: $bad", allPetTitles.contains(bad))
        }
        assertTrue(person.titlesFor("gine").contains("Papanicolaou"))

        // Los grupos de mascotas solo usan parámetros que existen en el catálogo (para rellenar la unidad).
        for (g in dog.groups) for (p in g.params) assertNotNull("sin unidad en catálogo: $p", dog.defaultsFor(p, emptyList()))
        assertTrue(dog.groups.none { it.name == "Perfil de lípidos" })
    }

    @Test
    fun labelsAndNotes() {
        val pet = StudyProfile(isPet = true)
        assertEquals("Veterinaria o veterinario", pet.doctorLabel)
        assertTrue(pet.rangesNote.startsWith("Usa los rangos de referencia que vienen en el reporte del laboratorio veterinario"))
        assertTrue(StudyProfile(isMinor = true).rangesNote.startsWith("Rangos de referencia de adultos: verifica los del reporte, cambian con la edad"))
        assertTrue(StudyProfile().rangesNote.contains("orientativos para adultos"))
        assertEquals("tu veterinaria o veterinario", pet.whoToAsk)

        val today = LocalDate.of(2026, 11, 23).toEpochDay()
        assertTrue(StudyProfile.isMinor(LocalDate.of(2015, 3, 1).toEpochDay(), today))
        assertTrue(StudyProfile.isMinor(LocalDate.of(2008, 11, 24).toEpochDay(), today)) // cumple 18 mañana
        assertFalse(StudyProfile.isMinor(LocalDate.of(2008, 11, 23).toEpochDay(), today))
        assertFalse(StudyProfile.isMinor(null, today))
    }

    @Test
    fun currentReflectsTheActiveProfile() = runBlocking {
        assertFalse(StudyProfile.current().isPet)
        assertEquals("Citas médicas", MedsText.current().appointmentsTitle)

        createAndActivate(Profile(name = "Leo", sex = "M", birthEpochDay = LocalDate.now().minusYears(7).toEpochDay()))
        val k = StudyProfile.current()
        assertTrue(k.isMinor); assertFalse(k.isPet); assertEquals("M", k.sex)

        createPet("gato")
        val p = StudyProfile.current()
        assertTrue(p.isPet); assertFalse(p.isMinor); assertEquals("gato", p.species)
        val txt = MedsText.current()
        assertEquals("Citas veterinarias", txt.appointmentsTitle)
        assertEquals("Veterinaria o veterinario", txt.doctorLabel)
        assertTrue(txt.reasonPlaceholder.contains("Vacuna anual"))
    }

    @Test
    fun attachmentsAreStoredInTheActiveProfilesFolder() {
        fun importSample(name: String): String {
            val uri = Uri.parse("content://prueba/$name")
            shadowOf(ctx.contentResolver).registerInputStream(uri, ByteArrayInputStream("contenido de $name".toByteArray()))
            return StudyFiles.import(ctx, uri)!!.fileName
        }

        // Perfil 1: carpeta de siempre.
        val first = importSample("reporte.pdf")
        assertTrue(File(ctx.filesDir, "attachments/$first").isFile)

        // Mascota: su propia subcarpeta.
        val pet = createPet()
        val second = importSample("radiografia.jpg")
        val petFile = File(ctx.filesDir, "attachments/p$pet/$second")
        assertTrue("el adjunto debe quedar en attachments/p$pet", petFile.isFile)
        assertEquals("contenido de radiografia.jpg", petFile.readText())
        assertEquals(petFile, StudyFiles.file(ctx, second))
        assertFalse(File(ctx.filesDir, "attachments/$second").exists())
        // Desde la mascota no se ve ni se borra el adjunto del perfil 1.
        assertFalse(StudyFiles.file(ctx, first).exists())
        StudyFiles.delete(ctx, first)
        assertTrue(File(ctx.filesDir, "attachments/$first").isFile)

        // Borrar desde el perfil dueño sí lo elimina.
        StudyFiles.delete(ctx, second)
        assertFalse(petFile.exists())
        Savia.switchTo(1)
        assertTrue(StudyFiles.file(ctx, first).isFile)
        StudyFiles.delete(ctx, first)
        assertFalse(File(ctx.filesDir, "attachments/$first").exists())
    }
}
