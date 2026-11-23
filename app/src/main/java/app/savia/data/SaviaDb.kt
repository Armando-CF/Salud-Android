package app.savia.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base de datos local. Todo vive en el teléfono; la app no usa internet.
 *
 * IMPORTANTE para el futuro: al cambiar el esquema hay que subir [version] y añadir una
 * Migration (nunca usar migración destructiva: son datos de salud de toda una vida).
 */
@Database(
    entities = [
        Profile::class, SymptomEntry::class, Measurement::class, DailyLog::class,
        Study::class, StudyResult::class, StudyAttachment::class, CycleDay::class,
        Medication::class, MedIntake::class, Appointment::class, SelfExam::class,
    ],
    version = 1,
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

    companion object {
        const val NAME = "savia.db"

        fun create(context: Context): SaviaDb =
            Room.databaseBuilder(context.applicationContext, SaviaDb::class.java, NAME).build()

        fun createInMemory(context: Context): SaviaDb =
            Room.inMemoryDatabaseBuilder(context.applicationContext, SaviaDb::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
