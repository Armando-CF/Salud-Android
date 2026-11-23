package app.savia.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Convenciones de tiempo:
 *  - "epochDay": días desde 1970-01-01 (LocalDate.toEpochDay) para cosas que ocurren "en un día".
 *  - "timestamp": milisegundos epoch para cosas con hora.
 * Se eligieron tipos simples (Long/String/Double) para que la base sea estable a muy largo plazo
 * y fácil de exportar.
 */

/** Ficha personal. Siempre existe una sola fila con id = 1. */
@Entity(tableName = "profile")
data class Profile(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    /** "F" mujer, "M" hombre, "X" sin especificar. */
    val sex: String = "X",
    val birthEpochDay: Long? = null,
    val heightCm: Double? = null,
    val goalWeightKg: Double? = null,
    val bloodType: String = "",
    val allergies: String = "",
    val conditions: String = "",
    val emergencyName: String = "",
    val emergencyPhone: String = "",
    val doctorName: String = "",
    val doctorPhone: String = "",
    val insurance: String = "",
    val notes: String = "",
    // ── Desde la versión 2 de la base (perfiles múltiples y mascotas) ──
    /** "person" o "pet". No cambia después de crear el perfil. */
    val kind: String = "person",
    /** Solo mascotas: clave de PetSpecies ("perro", "gato", "ave", "conejo", "roedor", "reptil", "otro"). */
    val species: String = "",
    /** Solo mascotas: raza. */
    val breed: String = "",
    /** Solo mascotas: esterilizado/a (null = no se sabe). */
    val sterilized: Boolean? = null,
    /** Solo mascotas: número de microchip o placa. */
    val microchip: String = "",
    /** Solo mascotas: color y señas particulares. */
    val markings: String = "",
    /** Solo personas: parentesco con quien usa la app ("Yo", "Hija", "Papá"…). Texto libre. */
    val relation: String = "",
    /** Índice del color del avatar (0..7). */
    val avatarColor: Int = 0,
) {
    val isPet: Boolean get() = kind == "pet"
}

/** Un síntoma o malestar registrado. */
@Entity(tableName = "symptoms", indices = [Index("timestamp")])
data class SymptomEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val name: String,
    /** 1 (muy leve) a 10 (insoportable). */
    val intensity: Int = 5,
    /** Id de la zona del atlas corporal (opcional), p. ej. "abd_fosa_iliaca_der". */
    val regionId: String? = null,
    /** Nombre legible de la zona, p. ej. "Fosa ilíaca derecha". */
    val regionLabel: String? = null,
    val durationMin: Int? = null,
    /** Posibles desencadenantes / etiquetas, separados por coma. */
    val tags: String = "",
    val notes: String = "",
)

/** Medición numérica (peso, presión, glucosa...). Ver [MeasureType]. */
@Entity(tableName = "measurements", indices = [Index("type", "timestamp")])
data class Measurement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    /** Clave de [MeasureType]. */
    val type: String,
    val value: Double,
    /** Segundo valor (p. ej. presión diastólica). */
    val value2: Double? = null,
    /** Contexto libre (p. ej. "En ayunas"). */
    val tag: String = "",
    val notes: String = "",
)

/** Bienestar diario: una fila por día. */
@Entity(tableName = "daily_logs")
data class DailyLog(
    @PrimaryKey val epochDay: Long,
    /** 1 (muy mal) a 5 (excelente). */
    val mood: Int? = null,
    /** 1 a 5. */
    val energy: Int? = null,
    /** 1 (nada) a 5 (mucho). */
    val stress: Int? = null,
    val sleepHours: Double? = null,
    val waterGlasses: Int? = null,
    val exerciseMin: Int? = null,
    val notes: String = "",
)

/** Estudio o chequeo médico. */
@Entity(tableName = "studies", indices = [Index("epochDay")])
data class Study(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val title: String,
    /** Clave de categoría, ver StudyCategories. */
    val category: String = "lab",
    val place: String = "",
    val doctor: String = "",
    /** "normal", "seguimiento", "pendiente" o "". */
    val status: String = "",
    val summary: String = "",
    val followUpEpochDay: Long? = null,
)

/** Un parámetro/resultado dentro de un estudio (p. ej. Glucosa 92 mg/dL). */
@Entity(
    tableName = "study_results",
    foreignKeys = [ForeignKey(entity = Study::class, parentColumns = ["id"], childColumns = ["studyId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("studyId"), Index("name")],
)
data class StudyResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studyId: Long,
    val name: String,
    val value: Double? = null,
    /** Resultado no numérico (p. ej. "Negativo"). */
    val textValue: String = "",
    val unit: String = "",
    val refMin: Double? = null,
    val refMax: Double? = null,
    val notes: String = "",
)

/** Archivo adjunto (foto/PDF) de un estudio, copiado a filesDir/attachments. */
@Entity(
    tableName = "study_attachments",
    foreignKeys = [ForeignKey(entity = Study::class, parentColumns = ["id"], childColumns = ["studyId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("studyId")],
)
data class StudyAttachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studyId: Long,
    /** Nombre del archivo dentro de filesDir/attachments. */
    val fileName: String,
    val displayName: String,
    val mime: String = "",
)

/** Registro de un día del ciclo menstrual: una fila por día. */
@Entity(tableName = "cycle_days")
data class CycleDay(
    @PrimaryKey val epochDay: Long,
    /** 0 sin sangrado, 1 manchado, 2 ligero, 3 moderado, 4 abundante. */
    val flow: Int = 0,
    /** Claves de síntomas separadas por coma (ver CycleCatalog). */
    val symptoms: String = "",
    /** Claves de estados de ánimo separadas por coma. */
    val moods: String = "",
    /** Flujo cervical: "", "seco", "pegajoso", "cremoso", "acuoso", "clara". */
    val mucus: String = "",
    val basalTemp: Double? = null,
    /** 0 sin registro, 1 con protección, 2 sin protección. */
    val sex: Int = 0,
    /** 0 sin registro, 1 negativa, 2 positiva. */
    val ovulationTest: Int = 0,
    /** 0 sin registro, 1 negativa, 2 positiva. */
    val pregnancyTest: Int = 0,
    /** Anticonceptivo tomado/aplicado ese día. */
    val contraceptive: Boolean = false,
    val notes: String = "",
)

/** Medicamento o tratamiento. */
@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dose: String = "",
    val instructions: String = "",
    /** Horas de toma "HH:mm" separadas por coma, p. ej. "08:00,20:00". Vacío = según sea necesario. */
    val times: String = "",
    /** Máscara de días: bit 0 = lunes ... bit 6 = domingo. 127 = todos los días. */
    val daysMask: Int = 127,
    val startEpochDay: Long,
    val endEpochDay: Long? = null,
    val active: Boolean = true,
    val remind: Boolean = true,
    val notes: String = "",
)

/** Toma registrada de un medicamento. */
@Entity(
    tableName = "med_intakes",
    foreignKeys = [ForeignKey(entity = Medication::class, parentColumns = ["id"], childColumns = ["medicationId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("medicationId"), Index("epochDay")],
)
data class MedIntake(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    val epochDay: Long,
    /** Hora programada "HH:mm" a la que corresponde, o "" si fue una toma libre. */
    val slot: String = "",
    /** Momento real en que se registró. */
    val timestamp: Long,
    /** "taken" u "skipped". */
    val status: String = "taken",
)

/** Cita médica. */
@Entity(tableName = "appointments", indices = [Index("timestamp")])
data class Appointment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val title: String,
    val doctor: String = "",
    val specialty: String = "",
    val place: String = "",
    val notes: String = "",
    val remind: Boolean = true,
    val done: Boolean = false,
)

/** Autoexploración (mamaria, testicular, de piel...). */
@Entity(tableName = "self_exams", indices = [Index("epochDay")])
data class SelfExam(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    /** "mama", "testicular", "piel". */
    val type: String,
    /** true = sin cambios; false = noté algo. */
    val normal: Boolean = true,
    val notes: String = "",
)

/** Vacuna, desparasitación u otra aplicación preventiva (para personas y mascotas). */
@Entity(tableName = "vaccinations", indices = [Index("epochDay")])
data class Vaccination(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Fecha de aplicación. */
    val epochDay: Long,
    /** "vacuna", "desp_interna" (desparasitación interna), "desp_externa" (pulgas/garrapatas) u "otro". */
    val type: String = "vacuna",
    val name: String,
    /** Dosis o esquema: "1.ª dosis", "Refuerzo", "Anual"… */
    val dose: String = "",
    /** Próxima aplicación o refuerzo (opcional). */
    val nextDueEpochDay: Long? = null,
    /** Lugar, clínica o veterinaria. */
    val place: String = "",
    val lot: String = "",
    val notes: String = "",
    /** Avisar cuando se acerque [nextDueEpochDay]. */
    val remind: Boolean = true,
)
