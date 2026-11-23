package app.savia

import androidx.test.core.app.ApplicationProvider
import app.savia.data.SaviaDb
import app.savia.export.Backup
import app.savia.export.BackupTarget
import app.savia.export.RestoreMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Compatibilidad real de respaldos entre la app de Android y Savia Web (carpeta build/xplat en la raíz):
 * escribe respaldos de Android para que la web los lea y restaura uno escrito por la web.
 * Pareja de web/src/modules/backup/xplat.test.ts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExportCrossPlatformTest {
    private val dir = File("../build/xplat")
    private val ctx: android.content.Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        Savia.init(ctx, SaviaDb.createInMemory(ctx))
        dir.mkdirs()
    }

    @Test
    fun writesBackupsForTheWebApp() = runBlocking {
        DemoData.seedFamily()
        File(dir, "android-profile.zip").outputStream().use { Backup.write(ctx, it, BackupTarget.of(1)) }
        File(dir, "android-full.zip").outputStream().use { Backup.writeAll(ctx, it) }
        assertTrue(File(dir, "android-full.zip").length() > 1000)
    }

    @Test
    fun restoresABackupWrittenByTheWebApp() = runBlocking {
        val zip = File(dir, "web-to-android.zip")
        assumeTrue("falta el respaldo generado por la web", zip.exists())
        zip.inputStream().use { Backup.restore(ctx, it, RestoreMode.REPLACE, BackupTarget.of(1)) }
        val db = Savia.db
        val p = db.profile().get()!!
        assertEquals("Alex", p.name)
        assertEquals("O+", p.bloodType)
        assertEquals("Penicilina, \"polvo\"", p.allergies)
        assertEquals(2, db.symptoms().getAll().size)
        assertEquals("Con\nsalto, coma y \"comillas\"", db.symptoms().getAll().first { it.name == "Dolor de cabeza" }.notes)
        assertEquals(17, db.cycle().getAll().size)
        assertEquals(1, db.cycle().getAll().single { it.epochDay == 19660L }.pregnancyTest)
        assertEquals(76.0, db.measurements().getAll().single { it.type == "bp" }.value2!!, 0.0)
        assertEquals(3, db.studies().getAllResults().size)
        assertEquals("Influenza", db.vaccinations().getAll().single().name)
        assertEquals(1, db.medications().getAllIntakes().size)
        assertTrue(Savia.prefs.cycleEnabled.value)
        assertEquals(30, Savia.prefs.cycleLength.value)
        // el adjunto llegó con sus bytes
        val att = db.studies().getAllAttachments().single()
        val file = Savia.attachmentFile(att.fileName)
        assertTrue(file.exists())
        assertEquals(listOf(37, 80, 68, 70, 0, 255, 1, 2, 3), file.readBytes().map { it.toInt() and 0xFF })
    }
}
