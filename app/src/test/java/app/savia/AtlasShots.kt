package app.savia

import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import app.savia.ui.atlas.AtlasRepo
import app.savia.ui.atlas.BodyAtlasScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AtlasShots : ScreenshotBase() {
    @Test
    fun parsesAndHitTests() {
        val atlas = AtlasRepo.load(ApplicationProvider.getApplicationContext())
        assertEquals(11, atlas.views.size)
        for (v in atlas.views) for (s in v.shapes) if (s.id != null) assertNotNull("sin info: ${s.id}", atlas.info[s.id])
        val front = atlas.view("front", "F")!!
        assertEquals("abd_mesogastrio", front.hit(200f, 340f, 4f)?.id)
        // lado derecho de la persona = izquierda de la pantalla
        assertEquals("abd_fosa_iliaca_der", front.hit(165f, 385f, 4f)?.id)
        assertEquals("rodilla_izq", front.hit(238f, 606f, 4f)?.id)
        assertTrue(atlas.info["abd_fosa_iliaca_der"]!!.organs.any { it.contains("Apéndice") })
        val back = atlas.view("back", "M")!!
        assertEquals("lumbar_der", back.hit(235f, 350f, 4f)?.id)
        assertEquals("o_corazon", atlas.view("organs", "M")!!.hit(212f, 240f, 4f)?.id)
    }

    @Test
    fun front() {
        DemoData.seed()
        shot("atlas_front") { BodyAtlasScreen(rememberNavController(), pickMode = false, onBack = null) }
    }

    private fun atlasShot(name: String, group: String, sel: String?, sex: String = "F", dark: Boolean = false, pick: Boolean = false) {
        Savia.prefs.atlasSex.value = sex
        shot(name, dark) { BodyAtlasScreen(rememberNavController(), pickMode = pick, onBack = if (pick) ({}) else null, initialGroup = group, initialSelection = sel) }
    }

    @Test fun abdomen() = atlasShot("atlas_abdomen", "abdomen", "abd_fosa_iliaca_der")
    @Test fun organs() = atlasShot("atlas_organs_m_dark", "organs", "o_higado", sex = "M", dark = true)
    @Test fun skeleton() = atlasShot("atlas_skeleton", "skeleton", "h_femur_izq")
    @Test fun hand() = atlasShot("atlas_hand", "hand", "m_falange_media_indice")
    @Test fun foot() = atlasShot("atlas_foot_dark", "foot", "p_calcaneo", dark = true)
    @Test fun backPick() = atlasShot("atlas_back_pick", "back", "lumbar_der", sex = "M", pick = true)
}
