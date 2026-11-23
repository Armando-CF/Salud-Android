package app.savia.export

import app.savia.Savia
import app.savia.cycle.CycleAnalysis
import app.savia.cycle.CycleEngine
import app.savia.cycle.CycleRecord
import app.savia.data.Appointment
import app.savia.data.CycleDay
import app.savia.data.DailyLog
import app.savia.data.MedIntake
import app.savia.data.Measurement
import app.savia.data.Medication
import app.savia.data.Prefs
import app.savia.data.Profile
import app.savia.data.SaviaDb
import app.savia.data.SelfExam
import app.savia.data.Study
import app.savia.data.StudyAttachment
import app.savia.data.StudyResult
import app.savia.data.SymptomEntry
import app.savia.util.millisToEpochDay
import app.savia.util.nowMillis
import app.savia.util.today

/** Bloques de información que se pueden exportar. */
enum class ExportModule(val label: String, val slug: String, val description: String) {
    PERFIL("Mi ficha", "ficha", "Datos personales y de emergencia"),
    CICLO("Ciclo menstrual", "ciclo", "Días registrados, ciclos y estadísticas"),
    SINTOMAS("Síntomas", "sintomas", "Malestares con intensidad y zona del cuerpo"),
    MEDICIONES("Peso y mediciones", "mediciones", "Peso, presión, glucosa y demás"),
    BIENESTAR("Bienestar diario", "bienestar", "Ánimo, energía, sueño, agua y ejercicio"),
    ESTUDIOS("Estudios y chequeos", "estudios", "Estudios con sus resultados"),
    MEDICAMENTOS("Medicamentos", "medicamentos", "Tratamientos y tomas registradas"),
    CITAS("Citas médicas", "citas", "Citas pasadas y próximas"),
    AUTOEXPLORACION("Autoexploración", "autoexploracion", "Registro de autoexploraciones"),
}

/** Formatos de salida. */
enum class ExportFormat(val label: String, val description: String) {
    XLSX("Excel", "Hoja de cálculo (.xlsx) para Excel o Google Sheets"),
    PDF("PDF", "Reporte listo para imprimir o enviar a tu médico"),
    CSV("CSV", "Texto separado por comas, compatible con casi todo"),
    JSON("JSON", "Formato abierto para otros programas"),
}

/**
 * Qué exportar.
 * @param fromDay / toDay límites inclusivos en epochDay; null = sin límite.
 */
data class ExportOptions(
    val modules: Set<ExportModule> = ExportModule.entries.toSet(),
    val fromDay: Long? = null,
    val toDay: Long? = null,
) {
    fun inRange(epochDay: Long): Boolean =
        (fromDay == null || epochDay >= fromDay) && (toDay == null || epochDay <= toDay)

    companion object {
        /** Todo, sin límite de fechas. */
        val ALL = ExportOptions()

        /** Solo el ciclo menstrual (todo el historial). */
        val CYCLE_ONLY = ExportOptions(modules = setOf(ExportModule.CICLO))
    }
}

/** Datos ya cargados y filtrados, listos para escribirse en cualquier formato. */
class ExportData(
    val options: ExportOptions,
    val generatedAt: Long,
    val today: Long,
    /** Perfil completo si se pidió PERFIL; si no, solo trae el nombre (para encabezados). */
    val profile: Profile?,
    val symptoms: List<SymptomEntry>,
    val measurements: List<Measurement>,
    val dailyLogs: List<DailyLog>,
    val studies: List<Study>,
    val studyResults: List<StudyResult>,
    val studyAttachments: List<StudyAttachment>,
    /** Días del ciclo dentro del rango. */
    val cycleDays: List<CycleDay>,
    /** Análisis hecho con TODO el historial (las estadísticas no dependen del rango). */
    val cycleAnalysis: CycleAnalysis?,
    /** Ciclos derivados cuyo inicio cae en el rango; index + 1 = número consecutivo (1 = el más antiguo). */
    val cycles: List<IndexedValue<CycleRecord>>,
    val medications: List<Medication>,
    val medIntakes: List<MedIntake>,
    val appointments: List<Appointment>,
    val selfExams: List<SelfExam>,
) {
    fun has(module: ExportModule): Boolean = module in options.modules

    /** No hay ningún registro que exportar (el perfil solo no cuenta si está en blanco). */
    val isEmpty: Boolean
        get() = symptoms.isEmpty() && measurements.isEmpty() && dailyLogs.isEmpty() && studies.isEmpty() &&
            cycleDays.isEmpty() && medications.isEmpty() && appointments.isEmpty() && selfExams.isEmpty() &&
            !(has(ExportModule.PERFIL) && profile != null && profile != Profile())

    companion object {
        /** Lee de la base de datos lo que pidan las opciones. Llamar fuera del hilo principal. */
        suspend fun load(
            options: ExportOptions,
            db: SaviaDb = Savia.db,
            prefs: Prefs = Savia.prefs,
            today: Long = today(),
        ): ExportData {
            fun on(m: ExportModule) = m in options.modules
            val symptoms = if (on(ExportModule.SINTOMAS)) db.symptoms().getAll().filter { options.inRange(it.timestamp.millisToEpochDay()) } else emptyList()
            val measurements = if (on(ExportModule.MEDICIONES)) db.measurements().getAll().filter { options.inRange(it.timestamp.millisToEpochDay()) } else emptyList()
            val logs = if (on(ExportModule.BIENESTAR)) db.dailyLogs().getAll().filter { options.inRange(it.epochDay) } else emptyList()

            val studies = if (on(ExportModule.ESTUDIOS)) db.studies().getAll().filter { options.inRange(it.epochDay) } else emptyList()
            val studyIds = studies.map { it.id }.toSet()
            val results = if (studies.isEmpty()) emptyList() else db.studies().getAllResults().filter { it.studyId in studyIds }
            val attachments = if (studies.isEmpty()) emptyList() else db.studies().getAllAttachments().filter { it.studyId in studyIds }

            var cycleDays = emptyList<CycleDay>()
            var analysis: CycleAnalysis? = null
            var cycles = emptyList<IndexedValue<CycleRecord>>()
            if (on(ExportModule.CICLO)) {
                val all = db.cycle().getAll()
                cycleDays = all.filter { options.inRange(it.epochDay) }
                analysis = CycleEngine.analyze(all, prefs.cycleLength.value, prefs.periodLength.value, prefs.lutealLength.value, today)
                cycles = analysis.cycles.withIndex().filter { options.inRange(it.value.start) }
            }

            var meds = emptyList<Medication>()
            var intakes = emptyList<MedIntake>()
            if (on(ExportModule.MEDICAMENTOS)) {
                // Un tratamiento entra si se traslapa con el rango pedido.
                meds = db.medications().getAll().filter { m ->
                    (options.toDay == null || m.startEpochDay <= options.toDay) &&
                        (options.fromDay == null || m.endEpochDay == null || m.endEpochDay >= options.fromDay)
                }
                val ids = meds.map { it.id }.toSet()
                intakes = db.medications().getAllIntakes().filter { it.medicationId in ids && options.inRange(it.epochDay) }
            }
            val appointments = if (on(ExportModule.CITAS)) db.appointments().getAll().filter { options.inRange(it.timestamp.millisToEpochDay()) } else emptyList()
            val exams = if (on(ExportModule.AUTOEXPLORACION)) db.selfExams().getAll().filter { options.inRange(it.epochDay) } else emptyList()
            val profile = db.profile().get()

            return ExportData(
                options = options,
                generatedAt = nowMillis(),
                today = today,
                profile = if (on(ExportModule.PERFIL)) profile else profile?.let { Profile(name = it.name) },
                symptoms = symptoms,
                measurements = measurements,
                dailyLogs = logs,
                studies = studies,
                studyResults = results,
                studyAttachments = attachments,
                cycleDays = cycleDays,
                cycleAnalysis = analysis,
                cycles = cycles,
                medications = meds,
                medIntakes = intakes,
                appointments = appointments,
                selfExams = exams,
            )
        }
    }
}

/** Celda de fecha (epochDay). */
data class DateCell(val epochDay: Long)

/** Celda de fecha y hora (milisegundos epoch). */
data class DateTimeCell(val millis: Long)

/**
 * Columna de una tabla exportable.
 * @param width ancho aproximado en caracteres (Excel) y peso relativo (PDF).
 * @param wrap texto largo que debe ajustarse.
 */
class ExportColumn(val title: String, val width: Int = 14, val wrap: Boolean = false)

/**
 * Tabla genérica: la misma estructura alimenta Excel, CSV, PDF y JSON.
 * Las celdas pueden ser String, Number (Int/Long/Double), Boolean, [DateCell], [DateTimeCell] o null.
 */
class ExportTable(
    /** Nombre de archivo sin extensión (para CSV), sin acentos ni espacios. */
    val id: String,
    /** Título legible (nombre de hoja en Excel, sección en PDF). Máx. 31 caracteres. */
    val title: String,
    val module: ExportModule,
    val columns: List<ExportColumn>,
    val rows: List<List<Any?>>,
)
