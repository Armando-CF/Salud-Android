package app.savia

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.savia.data.Profile
import app.savia.data.SaviaDb
import app.savia.data.Vaccination
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Comprueba que una base creada con la versión 1 (la del primer APK) se actualiza a la versión
 * actual SIN perder datos. Usa el esquema exportado en app/schemas.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    /** Crea un archivo de base con el esquema exacto de la versión [version]. */
    private fun createFromSchema(name: String, version: Int): SQLiteDatabase {
        val json = JSONObject(File("schemas/app.savia.data.SaviaDb/$version.json").readText()).getJSONObject("database")
        val file = ctx.getDatabasePath(name).also { it.parentFile?.mkdirs(); it.delete() }
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        val entities = json.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            val table = e.getString("tableName")
            db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
            val idx = e.optJSONArray("indices")
            if (idx != null) for (j in 0 until idx.length()) db.execSQL(idx.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
        }
        val setup = json.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
        db.version = version
        return db
    }

    @Test
    fun migratesVersion1KeepingData() = runBlocking {
        val name = "migracion-prueba.db"
        createFromSchema(name, 1).use { db ->
            db.execSQL("INSERT INTO profile (id, name, sex, birthEpochDay, heightCm, goalWeightKg, bloodType, allergies, conditions, emergencyName, emergencyPhone, doctorName, doctorPhone, insurance, notes) VALUES (1, 'Ana', 'F', 12000, 165.0, NULL, 'O+', 'Penicilina', '', '', '', '', '', '', 'nota')")
            db.execSQL("INSERT INTO symptoms (timestamp, name, intensity, regionId, regionLabel, durationMin, tags, notes) VALUES (1700000000000, 'Dolor de cabeza', 6, NULL, NULL, NULL, '', '')")
            db.execSQL("INSERT INTO cycle_days (epochDay, flow, symptoms, moods, mucus, basalTemp, sex, ovulationTest, pregnancyTest, contraceptive, notes) VALUES (20000, 3, 'colicos', '', '', NULL, 0, 0, 0, 0, '')")
            db.execSQL("INSERT INTO measurements (timestamp, type, value, value2, tag, notes) VALUES (1700000000000, 'weight', 64.5, NULL, '', '')")
        }
        val room = Room.databaseBuilder(ctx, SaviaDb::class.java, name).addMigrations(*SaviaDb.MIGRATIONS).build()
        try {
            // Abrir valida el esquema: si la migración no deja la base idéntica a lo que Room espera, lanza excepción.
            val p = room.profile().get()!!
            assertEquals("Ana", p.name)
            assertEquals("O+", p.bloodType)
            assertEquals("Penicilina", p.allergies)
            assertEquals("person", p.kind)
            assertFalse(p.isPet)
            assertNull(p.sterilized)
            assertEquals(0, p.avatarColor)
            assertEquals(1, room.symptoms().getAll().size)
            assertEquals(3, room.cycle().getAll().single().flow)
            assertEquals(64.5, room.measurements().getAll().single().value, 0.0)
            // La tabla nueva funciona.
            val id = room.vaccinations().insert(Vaccination(epochDay = 20000, name = "Influenza", nextDueEpochDay = 20365))
            assertEquals("Influenza", room.vaccinations().get(id)!!.name)
            room.profile().save(p.copy(kind = "pet", species = "perro", sterilized = true))
            assertTrue(room.profile().get()!!.isPet)
        } finally {
            room.close()
        }
    }

    @Test
    fun freshDatabaseMatchesMigratedSchema() = runBlocking {
        // Una instalación nueva (sin migración) también abre y guarda perfiles con los campos nuevos.
        val room = Room.databaseBuilder(ctx, SaviaDb::class.java, "nueva.db").addMigrations(*SaviaDb.MIGRATIONS).build()
        try {
            room.profile().save(Profile(name = "Firulais", kind = "pet", species = "perro", breed = "Mestizo", sterilized = false))
            assertEquals("perro", room.profile().get()!!.species)
        } finally {
            room.close()
        }
    }
}
