package app.savia.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Base de datos local de UN perfil. Cada perfil (persona o mascota) tiene su propio archivo:
 * "savia.db" para el perfil 1 y "savia_p<N>.db" para los demás (ver [app.savia.Savia]).
 * Todo vive en el teléfono; la app no usa internet.
 *
 * IMPORTANTE para el futuro: al cambiar el esquema hay que subir [version] y añadir una
 * Migration (nunca usar migración destructiva: son datos de salud de toda una vida).
 */
@Database(
    entities = [
        Profile::class, SymptomEntry::class, Measurement::class, DailyLog::class,
        Study::class, StudyResult::class, StudyAttachment::class, CycleDay::class,
        Medication::class, MedIntake::class, Appointment::class, SelfExam::class,
        Vaccination::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class SaviaDb : RoomDatabase() {
    abstract fun profile(): ProfileDao
    abstract fun symptoms(): SymptomDao
    abstract fun measurements(): MeasurementDao
    abstract fun dailyLogs(): DailyLogDao
    abstract fun studies(): StudyDao
    abstract fun cycle(): CycleDao
    abstract fun medications(): MedicationDao
    abstract fun appointments(): AppointmentDao
    abstract fun selfExams(): SelfExamDao
    abstract fun vaccinations(): VaccinationDao

    companion object {
        const val NAME = "savia.db"

        /** v1 → v2: campos de perfil para mascotas/perfiles múltiples y tabla de vacunas. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profile ADD COLUMN kind TEXT NOT NULL DEFAULT 'person'")
                db.execSQL("ALTER TABLE profile ADD COLUMN species TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE profile ADD COLUMN breed TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE profile ADD COLUMN sterilized INTEGER")
                db.execSQL("ALTER TABLE profile ADD COLUMN microchip TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE profile ADD COLUMN markings TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE profile ADD COLUMN relation TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE profile ADD COLUMN avatarColor INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS vaccinations (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "epochDay INTEGER NOT NULL, type TEXT NOT NULL, name TEXT NOT NULL, dose TEXT NOT NULL, " +
                        "nextDueEpochDay INTEGER, place TEXT NOT NULL, lot TEXT NOT NULL, notes TEXT NOT NULL, remind INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_vaccinations_epochDay ON vaccinations (epochDay)")
            }
        }

        val MIGRATIONS = arrayOf(MIGRATION_1_2)

        fun create(context: Context, name: String = NAME): SaviaDb =
            Room.databaseBuilder(context.applicationContext, SaviaDb::class.java, name)
                .addMigrations(*MIGRATIONS)
                .build()

        fun createInMemory(context: Context): SaviaDb =
            Room.inMemoryDatabaseBuilder(context.applicationContext, SaviaDb::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
