package app.savia.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun observe(): Flow<Profile?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun get(): Profile?

    @Upsert
    suspend fun save(profile: Profile)
}

@Dao
interface SymptomDao {
    @Query("SELECT * FROM symptoms ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<SymptomEntry>>

    @Query("SELECT * FROM symptoms WHERE timestamp >= :from ORDER BY timestamp DESC")
    fun observeSince(from: Long): Flow<List<SymptomEntry>>

    @Query("SELECT * FROM symptoms ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SymptomEntry>>

    @Query("SELECT * FROM symptoms WHERE id = :id")
    suspend fun get(id: Long): SymptomEntry?

    @Query("SELECT * FROM symptoms ORDER BY timestamp")
    suspend fun getAll(): List<SymptomEntry>

    /** Nombres de síntomas ya usados, del más frecuente al menos. */
    @Query("SELECT name FROM symptoms GROUP BY name ORDER BY COUNT(*) DESC")
    fun observeUsedNames(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SymptomEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SymptomEntry>)

    @Update
    suspend fun update(item: SymptomEntry)

    @Delete
    suspend fun delete(item: SymptomEntry)

    @Query("DELETE FROM symptoms")
    suspend fun clear()
}

@Dao
interface MeasurementDao {
    @Query("SELECT * FROM measurements WHERE type = :type ORDER BY timestamp DESC")
    fun observeByType(type: String): Flow<List<Measurement>>

    /** Última medición de cada tipo. */
    @Query("SELECT * FROM measurements m WHERE timestamp = (SELECT MAX(timestamp) FROM measurements WHERE type = m.type) GROUP BY type")
    fun observeLatestPerType(): Flow<List<Measurement>>

    @Query("SELECT * FROM measurements WHERE type = :type ORDER BY timestamp DESC LIMIT 1")
    fun observeLatest(type: String): Flow<Measurement?>

    @Query("SELECT * FROM measurements WHERE id = :id")
    suspend fun get(id: Long): Measurement?

    @Query("SELECT * FROM measurements ORDER BY timestamp")
    suspend fun getAll(): List<Measurement>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: Measurement): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<Measurement>)

    @Update
    suspend fun update(item: Measurement)

    @Delete
    suspend fun delete(item: Measurement)

    @Query("DELETE FROM measurements")
    suspend fun clear()
}

@Dao
interface DailyLogDao {
    @Query("SELECT * FROM daily_logs ORDER BY epochDay DESC")
    fun observeAll(): Flow<List<DailyLog>>

    @Query("SELECT * FROM daily_logs WHERE epochDay = :day")
    fun observeDay(day: Long): Flow<DailyLog?>

    @Query("SELECT * FROM daily_logs WHERE epochDay = :day")
    suspend fun get(day: Long): DailyLog?

    @Query("SELECT * FROM daily_logs ORDER BY epochDay")
    suspend fun getAll(): List<DailyLog>

    @Upsert
    suspend fun save(item: DailyLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<DailyLog>)

    @Query("DELETE FROM daily_logs WHERE epochDay = :day")
    suspend fun deleteDay(day: Long)

    @Query("DELETE FROM daily_logs")
    suspend fun clear()
}

@Dao
interface StudyDao {
    @Query("SELECT * FROM studies ORDER BY epochDay DESC, id DESC")
    fun observeAll(): Flow<List<Study>>

    @Query("SELECT * FROM studies WHERE id = :id")
    fun observe(id: Long): Flow<Study?>

    @Query("SELECT * FROM studies WHERE id = :id")
    suspend fun get(id: Long): Study?

    @Query("SELECT * FROM studies ORDER BY epochDay")
    suspend fun getAll(): List<Study>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: Study): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<Study>)

    @Update
    suspend fun update(item: Study)

    @Delete
    suspend fun delete(item: Study)

    // Resultados
    @Query("SELECT * FROM study_results WHERE studyId = :studyId ORDER BY id")
    fun observeResults(studyId: Long): Flow<List<StudyResult>>

    @Query("SELECT * FROM study_results WHERE studyId = :studyId ORDER BY id")
    suspend fun getResults(studyId: Long): List<StudyResult>

    @Query("SELECT * FROM study_results ORDER BY id")
    fun observeAllResults(): Flow<List<StudyResult>>

    @Query("SELECT * FROM study_results ORDER BY id")
    suspend fun getAllResults(): List<StudyResult>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(item: StudyResult): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResults(items: List<StudyResult>)

    @Delete
    suspend fun deleteResult(item: StudyResult)

    @Query("DELETE FROM study_results WHERE studyId = :studyId")
    suspend fun deleteResultsOf(studyId: Long)

    /** Reemplaza todos los resultados de un estudio. */
    @Transaction
    suspend fun replaceResults(studyId: Long, items: List<StudyResult>) {
        deleteResultsOf(studyId)
        insertResults(items.map { it.copy(id = 0, studyId = studyId) })
    }

    // Adjuntos
    @Query("SELECT * FROM study_attachments WHERE studyId = :studyId ORDER BY id")
    fun observeAttachments(studyId: Long): Flow<List<StudyAttachment>>

    @Query("SELECT * FROM study_attachments ORDER BY id")
    suspend fun getAllAttachments(): List<StudyAttachment>

    @Query("SELECT * FROM study_attachments WHERE studyId = :studyId ORDER BY id")
    suspend fun getAttachments(studyId: Long): List<StudyAttachment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachment(item: StudyAttachment): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachments(items: List<StudyAttachment>)

    @Delete
    suspend fun deleteAttachment(item: StudyAttachment)

    @Query("DELETE FROM study_attachments")
    suspend fun clearAttachments()

    @Query("DELETE FROM study_results")
    suspend fun clearResults()

    @Query("DELETE FROM studies")
    suspend fun clear()
}

@Dao
interface CycleDao {
    @Query("SELECT * FROM cycle_days ORDER BY epochDay")
    fun observeAll(): Flow<List<CycleDay>>

    @Query("SELECT * FROM cycle_days WHERE epochDay = :day")
    fun observeDay(day: Long): Flow<CycleDay?>

    @Query("SELECT * FROM cycle_days WHERE epochDay = :day")
    suspend fun get(day: Long): CycleDay?

    @Query("SELECT * FROM cycle_days ORDER BY epochDay")
    suspend fun getAll(): List<CycleDay>

    @Upsert
    suspend fun save(item: CycleDay)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CycleDay>)

    @Query("DELETE FROM cycle_days WHERE epochDay = :day")
    suspend fun deleteDay(day: Long)

    @Query("DELETE FROM cycle_days")
    suspend fun clear()
}

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications ORDER BY active DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<Medication>>

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun get(id: Long): Medication?

    @Query("SELECT * FROM medications ORDER BY id")
    suspend fun getAll(): List<Medication>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: Medication): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<Medication>)

    @Update
    suspend fun update(item: Medication)

    @Delete
    suspend fun delete(item: Medication)

    // Tomas
    @Query("SELECT * FROM med_intakes WHERE epochDay = :day")
    fun observeIntakesOfDay(day: Long): Flow<List<MedIntake>>

    @Query("SELECT * FROM med_intakes WHERE epochDay = :day")
    suspend fun getIntakesOfDay(day: Long): List<MedIntake>

    @Query("SELECT * FROM med_intakes WHERE medicationId = :medId ORDER BY timestamp DESC")
    fun observeIntakesOf(medId: Long): Flow<List<MedIntake>>

    @Query("SELECT * FROM med_intakes ORDER BY timestamp")
    suspend fun getAllIntakes(): List<MedIntake>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIntake(item: MedIntake): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIntakes(items: List<MedIntake>)

    @Delete
    suspend fun deleteIntake(item: MedIntake)

    @Query("DELETE FROM med_intakes WHERE medicationId = :medId AND epochDay = :day AND slot = :slot")
    suspend fun deleteIntakeSlot(medId: Long, day: Long, slot: String)

    @Query("DELETE FROM med_intakes")
    suspend fun clearIntakes()

    @Query("DELETE FROM medications")
    suspend fun clear()
}

@Dao
interface AppointmentDao {
    @Query("SELECT * FROM appointments ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<Appointment>>

    @Query("SELECT * FROM appointments WHERE timestamp >= :from AND done = 0 ORDER BY timestamp LIMIT :limit")
    fun observeUpcoming(from: Long, limit: Int): Flow<List<Appointment>>

    @Query("SELECT * FROM appointments WHERE id = :id")
    suspend fun get(id: Long): Appointment?

    @Query("SELECT * FROM appointments ORDER BY timestamp")
    suspend fun getAll(): List<Appointment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: Appointment): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<Appointment>)

    @Update
    suspend fun update(item: Appointment)

    @Delete
    suspend fun delete(item: Appointment)

    @Query("DELETE FROM appointments")
    suspend fun clear()
}

@Dao
interface SelfExamDao {
    @Query("SELECT * FROM self_exams ORDER BY epochDay DESC, id DESC")
    fun observeAll(): Flow<List<SelfExam>>

    @Query("SELECT * FROM self_exams ORDER BY epochDay")
    suspend fun getAll(): List<SelfExam>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SelfExam): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SelfExam>)

    @Update
    suspend fun update(item: SelfExam)

    @Delete
    suspend fun delete(item: SelfExam)

    @Query("DELETE FROM self_exams")
    suspend fun clear()
}

@Dao
interface VaccinationDao {
    @Query("SELECT * FROM vaccinations ORDER BY epochDay DESC, id DESC")
    fun observeAll(): Flow<List<Vaccination>>

    @Query("SELECT * FROM vaccinations WHERE id = :id")
    suspend fun get(id: Long): Vaccination?

    @Query("SELECT * FROM vaccinations ORDER BY epochDay")
    suspend fun getAll(): List<Vaccination>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: Vaccination): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<Vaccination>)

    @Update
    suspend fun update(item: Vaccination)

    @Delete
    suspend fun delete(item: Vaccination)

    @Query("DELETE FROM vaccinations")
    suspend fun clear()
}
