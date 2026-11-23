package app.savia

import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import app.savia.data.Profile
import app.savia.data.SymptomEntry
import app.savia.ui.atlas.AtlasRepo
import app.savia.ui.atlas.BodyAtlasScreen
import app.savia.util.nowMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Atlas de mascotas (perro y gato): datos, toques y capturas. */
class AtlasPetShots : ScreenshotBase() {
    private val atlas get() = AtlasRepo.load(ApplicationProvider.getApplicationContext())

    private fun asPet(name: String, species: String) = runBlocking {
        val profile = Profile(name = name, kind = "pet", species = species)
        // Se guarda la ficha ANTES de registrar el perfil: así el observador de perfiles de Savia nunca
        // llega a leer una fila vacía (que dejaría `Savia.active` como "persona" si la notificación de
        // Room no llega en pruebas).
        val next = (Savia.profileIds.maxOrNull() ?: 1) + 1
        Savia.dbFor(next).profile().save(profile)
        val id = Savia.createProfile(profile)
        assertEquals(next, id)
        Savia.switchTo(id)
        // El observador de perfiles de Savia puede reescribir `Savia.active` con el perfil anterior si emite
        // justo durante el cambio; se espera a que se asiente y se reafirma el perfil activo.
        repeat(12) {
            delay(50)
            if (!Savia.isPet) Savia.switchTo(id)
        }
        assertTrue(Savia.isPet)
        assertEquals(species, Savia.active.value.species)
    }

    @Test
    fun everyPetShapeHasInfoAndSpeciesSuffix() {
        val pets = atlas.views.filter { it.species != null }
        assertEquals(setOf("pet_side_perro", "pet_organs_perro", "pet_side_gato", "pet_organs_gato"), pets.map { it.id }.toSet())
        for (v in pets) {
            for (s in v.shapes) {
                val id = s.id ?: continue
                assertTrue("id sin especie: $id", id.startsWith("pet_") && id.endsWith("_${v.species}"))
                val info = atlas.info[id]
                assertNotNull("sin ficha: $id", info)
                assertTrue("ficha vacía: $id", info!!.name.isNotBlank() && info.description.isNotBlank() && info.tip != null)
            }
        }
        // Todas las zonas pedidas existen en ambas especies.
        val zones = listOf(
            "cabeza", "oreja", "ojo", "hocico", "boca", "cuello", "lomo", "lumbar", "grupa", "cola", "pecho", "abdomen", "flanco", "genital",
            "hombro", "brazo", "codo", "antebrazo", "carpo", "mano", "muslo", "rodilla", "pierna", "corvejon", "pie",
        )
        for (sp in listOf("perro", "gato")) {
            val ids = atlas.view("pet_side", "F", sp)!!.shapes.mapNotNull { it.id }.toSet()
            for (z in zones) assertTrue("falta pet_${z}_$sp", "pet_${z}_$sp" in ids)
        }
        // Los textos distinguen especie donde importa.
        assertTrue(atlas.info["pet_abdomen_perro"]!!.tip!!.contains("torsión"))
        assertTrue(atlas.info["pet_genital_gato"]!!.tip!!.contains("arenero"))
    }

    @Test
    fun hitTestsDog() {
        val side = atlas.view("pet_side", "F", "perro")!!
        assertEquals("pet_pecho_perro", side.hit(270f, 215f, 3f)?.id)
        assertEquals("pet_abdomen_perro", side.hit(330f, 226f, 3f)?.id)
        assertEquals("pet_lomo_perro", side.hit(270f, 140f, 3f)?.id)
        assertEquals("pet_cola_perro", side.hit(469f, 170f, 3f)?.id)
        assertEquals("pet_muslo_perro", side.hit(395f, 205f, 3f)?.id)
        assertEquals("pet_ojo_perro", side.hit(110f, 120f, 3f)?.id)
        assertEquals("pet_cabeza_perro", side.hit(116f, 107f, 3f)?.id)
        assertNull(side.hit(300f, 330f, 3f)) // aire bajo el vientre
        assertEquals("pet_o_corazon_perro", atlas.view("pet_organs", "F", "perro")!!.hit(234f, 220f, 3f)?.id)
    }

    @Test
    fun hitTestsCat() {
        val side = atlas.view("pet_side", "M", "gato")!!
        assertEquals("pet_pecho_gato", side.hit(255f, 225f, 3f)?.id)
        assertEquals("pet_abdomen_gato", side.hit(335f, 236f, 3f)?.id)
        assertEquals("pet_lomo_gato", side.hit(270f, 160f, 3f)?.id)
        assertEquals("pet_cola_gato", side.hit(470f, 162f, 3f)?.id)
        assertEquals("pet_ojo_gato", side.hit(86f, 149f, 3f)?.id)
        assertEquals("pet_cabeza_gato", side.hit(122f, 152f, 3f)?.id)
        assertEquals("pet_o_pulmones_gato", atlas.view("pet_organs", "F", "gato")!!.hit(250f, 175f, 3f)?.id)
    }

    @Test
    fun peopleAndPetsSeeDifferentGroups() {
        val a = atlas
        val person = a.groupsFor(isPet = false, species = "").map { it.first }
        assertEquals(listOf("front", "back", "abdomen", "organs", "skeleton", "hand", "foot"), person)
        assertEquals(listOf("pet_side", "pet_organs"), a.groupsFor(true, "perro").map { it.first })
        assertEquals(listOf("pet_side", "pet_organs"), a.groupsFor(true, "gato").map { it.first })
        assertTrue(a.groupsFor(true, "ave").isEmpty())
        // Las vistas humanas no llevan especie y las de mascota no se cuelan en la búsqueda de personas.
        assertNull(a.view("front", "F")!!.species)
        assertNull(a.view("pet_side", "F"))
        assertNull(a.view("front", "F", "perro"))
        assertTrue(a.hasSexVariants("front"))
        assertTrue(!a.hasSexVariants("pet_side", "perro"))
    }

    @Test
    fun dog() {
        asPet("Firulais", "perro")
        runBlocking {
            Savia.db.symptoms().insert(SymptomEntry(timestamp = nowMillis() - 3_600_000L, name = "Cojera", intensity = 5, regionId = "pet_rodilla_perro", regionLabel = "Babilla (rodilla)"))
        }
        shotWhen("atlas_pet_dog", "Abdomen (vientre)") {
            BodyAtlasScreen(rememberNavController(), pickMode = false, onBack = null, initialSelection = "pet_abdomen_perro")
        }
    }

    @Test
    fun dogOrgansDark() {
        asPet("Firulais", "perro")
        shotWhen("atlas_pet_dog_organs_dark", "Corazón", dark = true) {
            BodyAtlasScreen(rememberNavController(), pickMode = false, onBack = null, initialGroup = "pet_organs", initialSelection = "pet_o_corazon_perro")
        }
    }

    @Test
    fun catDark() {
        asPet("Garfield", "gato")
        shotWhen("atlas_pet_cat_dark", "Mano: dedos", dark = true) {
            BodyAtlasScreen(rememberNavController(), pickMode = false, onBack = null, initialSelection = "pet_mano_gato")
        }
    }

    @Test
    fun catPick() {
        asPet("Garfield", "gato")
        shotWhen("atlas_pet_cat_pick", "Toca la zona donde notas la molestia") {
            BodyAtlasScreen(rememberNavController(), pickMode = true, onBack = {})
        }
    }

    @Test
    fun catOrgans() {
        asPet("Garfield", "gato")
        shotWhen("atlas_pet_cat_organs", "Riñones") {
            BodyAtlasScreen(rememberNavController(), pickMode = false, onBack = null, initialGroup = "pet_organs", initialSelection = "pet_o_rinones_gato")
        }
    }

    @Test
    fun otherSpeciesShowsFriendlyEmptyState() {
        asPet("Piolín", "ave")
        shotWhen("atlas_pet_bird_empty", "Aún no tenemos una figura") {
            BodyAtlasScreen(rememberNavController(), pickMode = false, onBack = null)
        }
    }
}
