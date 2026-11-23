package app.savia.export

import app.savia.cycle.CycleEngine
import app.savia.data.CycleCatalog
import app.savia.data.CycleDay
import app.savia.data.MeasureType
import app.savia.data.PetSpecies
import app.savia.data.SelfExamTypes
import app.savia.data.SexOptions
import app.savia.data.StudyCategories
import app.savia.data.StudyResult
import app.savia.data.SymptomCatalog
import app.savia.data.VaccinationTypes
import app.savia.util.Fmt

/** Convierte [ExportData] en tablas legibles (encabezados en español, catálogos traducidos). */
object ExportTables {

    private fun c(title: String, width: Int = 14, wrap: Boolean = false) = ExportColumn(title, width, wrap)
    private fun String.orNull(): String? = trim().ifEmpty { null }
    private fun yesNo(b: Boolean) = if (b) "Sí" else "No"

    private val dayNames = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")

    /** Máscara de días (bit 0 = lunes) → texto. */
    fun daysMaskLabel(mask: Int): String {
        val m = mask and 127
        if (m == 127) return "Todos los días"
        if (m == 0) return "Ningún día"
        return dayNames.filterIndexed { i, _ -> m and (1 shl i) != 0 }.joinToString(", ")
    }

    /** "a,b" → "Etiqueta A, Etiqueta B". */
    private fun labels(csv: String, label: (String) -> String): String? =
        CycleCatalog.split(csv).joinToString(", ") { label(it) }.orNull()

    fun resultInterpretation(r: StudyResult): String? {
        val v = r.value ?: return null
        return when {
            r.refMin != null && v < r.refMin -> "Bajo"
            r.refMax != null && v > r.refMax -> "Alto"
            r.refMin != null || r.refMax != null -> "En rango"
            else -> null
        }
    }

    /** Cuántas tablas produce cada módulo (sirve para saber si un CSV será un solo archivo). */
    fun tableCount(module: ExportModule): Int = when (module) {
        ExportModule.CICLO -> 3
        ExportModule.ESTUDIOS, ExportModule.MEDICAMENTOS -> 2
        else -> 1
    }

    fun tableCount(options: ExportOptions): Int = options.modules.sumOf { tableCount(it) }

    /** Texto corto del tipo de perfil: "Persona", "Mascota (perro)". */
    fun kindLabel(kind: String, species: String): String =
        if (kind == "pet") "Mascota" + (if (species.isNotBlank()) " (${PetSpecies.label(species).lowercase()})" else "") else "Persona"

    /** Todas las tablas de los módulos pedidos, en un orden estable. */
    fun build(data: ExportData): List<ExportTable> {
        val out = ArrayList<ExportTable>()
        if (data.has(ExportModule.CICLO)) {
            out += cycleDays(data)
            out += cycleSummary(data)
            out += cycleStats(data)
        }
        if (data.has(ExportModule.MEDICIONES)) out += measurements(data)
        if (data.has(ExportModule.SINTOMAS)) out += symptoms(data)
        if (data.has(ExportModule.BIENESTAR)) out += wellbeing(data)
        if (data.has(ExportModule.ESTUDIOS)) {
            out += studies(data)
            out += studyResults(data)
        }
        if (data.has(ExportModule.VACUNAS)) out += vaccinations(data)
        if (data.has(ExportModule.MEDICAMENTOS)) {
            out += medications(data)
            out += intakes(data)
        }
        if (data.has(ExportModule.CITAS)) out += appointments(data)
        if (data.has(ExportModule.AUTOEXPLORACION)) out += selfExams(data)
        if (data.has(ExportModule.PERFIL)) out += profile(data)
        return out
    }

    // ---------------------------------------------------------------- Ciclo

    /** Otros registros de un día del ciclo, en texto ("Relaciones: con protección; Anticonceptivo"). */
    fun cycleDayExtras(d: CycleDay): String? = buildList {
        if (d.mucus.isNotEmpty()) add("Flujo cervical: ${CycleCatalog.mucusLabel(d.mucus).lowercase()}")
        d.basalTemp?.let { add("Temp. basal: ${Fmt.num(it, 2)} °C") }
        if (d.sex != 0) add("Relaciones: ${CycleCatalog.sexLabel(d.sex).lowercase()}")
        if (d.ovulationTest != 0) add("Prueba de ovulación: ${CycleCatalog.testLabel(d.ovulationTest).lowercase()}")
        if (d.pregnancyTest != 0) add("Prueba de embarazo: ${CycleCatalog.testLabel(d.pregnancyTest).lowercase()}")
        if (d.contraceptive) add("Anticonceptivo")
    }.joinToString("; ").orNull()

    fun cycleDays(data: ExportData): ExportTable {
        val a = data.cycleAnalysis
        return ExportTable(
            "ciclo_dias", "Ciclo - días", ExportModule.CICLO,
            listOf(
                c("Fecha", 12), c("Día del ciclo", 8), c("Fase estimada", 18), c("Sangrado", 14),
                c("Síntomas", 34, true), c("Ánimo", 24, true), c("Flujo cervical", 14), c("Temp. basal (°C)", 10),
                c("Relaciones", 16), c("Prueba de ovulación", 12), c("Prueba de embarazo", 12), c("Anticonceptivo", 10),
                c("Notas", 40, true),
            ),
            data.cycleDays.sortedBy { it.epochDay }.map { d ->
                val info = a?.info(d.epochDay)
                listOf(
                    DateCell(d.epochDay),
                    info?.cycleDay,
                    info?.takeIf { it.cycleDay != null }?.phase?.label,
                    if (d.flow > 0) CycleCatalog.flowLabel(d.flow) else null,
                    labels(d.symptoms, CycleCatalog::symptomLabel),
                    labels(d.moods, CycleCatalog::moodLabel),
                    d.mucus.orNull()?.let { CycleCatalog.mucusLabel(it) },
                    d.basalTemp,
                    if (d.sex != 0) CycleCatalog.sexLabel(d.sex) else null,
                    if (d.ovulationTest != 0) CycleCatalog.testLabel(d.ovulationTest) else null,
                    if (d.pregnancyTest != 0) CycleCatalog.testLabel(d.pregnancyTest) else null,
                    if (d.contraceptive) "Sí" else null,
                    d.notes.orNull(),
                )
            },
        )
    }

    fun cycleSummary(data: ExportData): ExportTable {
        val luteal = data.cycleAnalysis?.lutealLength ?: 14
        return ExportTable(
            "ciclo_resumen", "Ciclo - resumen", ExportModule.CICLO,
            listOf(
                c("Ciclo n.º", 8), c("Inicio del periodo", 14), c("Fin del periodo", 14), c("Duración del periodo (días)", 12),
                c("Duración del ciclo (días)", 12), c("Ovulación estimada", 14), c("Observación", 36, true),
            ),
            data.cycles.map { (i, r) ->
                val len = r.length
                listOf(
                    i + 1,
                    DateCell(r.start),
                    DateCell(r.start + r.periodLength - 1),
                    r.periodLength,
                    len,
                    if (len != null && r.valid && len - luteal >= 5) DateCell(r.start + len - luteal) else null,
                    when {
                        len == null -> "Ciclo en curso"
                        !r.valid -> "Atípico: no se usa en los promedios (menos de ${CycleEngine.MIN_VALID_CYCLE} o más de ${CycleEngine.MAX_VALID_CYCLE} días)"
                        else -> null
                    },
                )
            },
        )
    }

    /** Indicadores del ciclo como pares (indicador, valor). */
    fun cycleStatPairs(data: ExportData): List<Pair<String, Any?>> {
        val a = data.cycleAnalysis ?: return emptyList()
        fun r1(v: Double) = Math.round(v * 10) / 10.0
        val out = ArrayList<Pair<String, Any?>>()
        out += "Periodos registrados" to a.periods.size
        out += "Ciclos completos" to a.cycles.count { it.length != null }
        out += "Duración promedio del ciclo (días)" to a.avgCycle?.let { r1(it) }
        out += "Duración promedio del periodo (días)" to a.avgPeriod?.let { r1(it) }
        out += "Ciclo más corto reciente (días)" to a.shortestCycle
        out += "Ciclo más largo reciente (días)" to a.longestCycle
        out += "Variación entre ciclos (desviación estándar, días)" to a.cycleStdDev?.let { r1(it) }
        out += "Regularidad" to when (a.regular) {
            true -> "Regular (variación de 7 días o menos)"
            false -> "Irregular (variación mayor a 7 días)"
            null -> "Aún no hay suficientes ciclos para saberlo"
        }
        out += "Inicio del último periodo" to a.lastPeriod?.let { DateCell(it.start) }
        out += "Próximo periodo estimado" to a.nextPeriodStart?.let { DateCell(it) }
        out += "Duración de ciclo usada para estimar (días)" to a.cycleLength
        out += "Fase lútea usada para estimar (días)" to a.lutealLength
        out += "Promedios calculados con" to "Los últimos ${CycleEngine.RECENT} ciclos válidos"
        out += "Aviso" to "Las fechas estimadas son aproximaciones estadísticas; no sirven como método anticonceptivo."
        return out
    }

    fun cycleStats(data: ExportData): ExportTable = ExportTable(
        "ciclo_estadisticas", "Ciclo - estadísticas", ExportModule.CICLO,
        listOf(c("Indicador", 46), c("Valor", 46, true)),
        cycleStatPairs(data).map { listOf(it.first, it.second) },
    )

    // ---------------------------------------------------------------- Mediciones

    fun measurements(data: ExportData): ExportTable = ExportTable(
        "mediciones", "Peso y mediciones", ExportModule.MEDICIONES,
        listOf(c("Fecha y hora", 18), c("Tipo", 20), c("Valor", 10), c("Segundo valor", 10), c("Unidad", 9), c("Contexto", 22), c("Notas", 40, true)),
        data.measurements.sortedWith(compareBy({ MeasureType.of(it.type)?.ordinal ?: 99 }, { it.type }, { it.timestamp })).map { m ->
            val t = MeasureType.of(m.type)
            listOf(DateTimeCell(m.timestamp), t?.label ?: m.type, m.value, m.value2, t?.unit, m.tag.orNull(), m.notes.orNull())
        },
    )

    // ---------------------------------------------------------------- Síntomas

    fun symptoms(data: ExportData): ExportTable = ExportTable(
        "sintomas", "Síntomas", ExportModule.SINTOMAS,
        listOf(
            c("Fecha y hora", 18), c("Síntoma", 24, true), c("Intensidad (1-10)", 10), c("Nivel", 12), c("Zona del cuerpo", 24, true),
            c("Duración (min)", 10), c("Posibles desencadenantes", 26, true), c("Notas", 40, true),
        ),
        data.symptoms.sortedBy { it.timestamp }.map { s ->
            listOf(
                DateTimeCell(s.timestamp), s.name, s.intensity, SymptomCatalog.intensityLabel(s.intensity), s.regionLabel?.orNull(),
                s.durationMin, CycleCatalog.split(s.tags).joinToString(", ").orNull(), s.notes.orNull(),
            )
        },
    )

    // ---------------------------------------------------------------- Bienestar

    fun wellbeing(data: ExportData): ExportTable = ExportTable(
        "bienestar", "Bienestar", ExportModule.BIENESTAR,
        listOf(
            c("Fecha", 12), c("Ánimo (1-5)", 9), c("Energía (1-5)", 9), c("Estrés (1-5)", 9), c("Sueño (horas)", 9),
            c("Vasos de agua", 9), c("Ejercicio (min)", 9), c("Notas", 44, true),
        ),
        data.dailyLogs.sortedBy { it.epochDay }.map { d ->
            listOf(DateCell(d.epochDay), d.mood, d.energy, d.stress, d.sleepHours, d.waterGlasses, d.exerciseMin, d.notes.orNull())
        },
    )

    // ---------------------------------------------------------------- Estudios

    fun studies(data: ExportData): ExportTable {
        val attachments = data.studyAttachments.groupBy { it.studyId }
        return ExportTable(
            "estudios", "Estudios", ExportModule.ESTUDIOS,
            listOf(
                c("Fecha", 12), c("Estudio", 30, true), c("Categoría", 16), c("Lugar", 22, true), c("Médico", 20, true),
                c("Estado", 20), c("Resumen", 44, true), c("Seguimiento", 12), c("Archivos adjuntos", 24, true),
            ),
            data.studies.sortedBy { it.epochDay }.map { s ->
                listOf(
                    DateCell(s.epochDay), s.title, StudyCategories.short(s.category), s.place.orNull(), s.doctor.orNull(),
                    StudyCategories.statusLabel(s.status).orNull(), s.summary.orNull(), s.followUpEpochDay?.let { DateCell(it) },
                    attachments[s.id]?.joinToString(", ") { it.displayName }?.orNull(),
                )
            },
        )
    }

    fun studyResults(data: ExportData): ExportTable {
        val byId = data.studies.associateBy { it.id }
        return ExportTable(
            "estudios_resultados", "Resultados", ExportModule.ESTUDIOS,
            listOf(
                c("Fecha", 12), c("Estudio", 28, true), c("Parámetro", 24, true), c("Valor", 10), c("Resultado (texto)", 20, true),
                c("Unidad", 9), c("Ref. mínima", 9), c("Ref. máxima", 9), c("Interpretación", 12), c("Notas", 30, true),
            ),
            data.studyResults
                .sortedWith(compareBy({ byId[it.studyId]?.epochDay ?: 0L }, { it.studyId }, { it.id }))
                .map { r ->
                    val s = byId[r.studyId]
                    listOf(
                        s?.let { DateCell(it.epochDay) }, s?.title, r.name, r.value, r.textValue.orNull(), r.unit.orNull(),
                        r.refMin, r.refMax, resultInterpretation(r), r.notes.orNull(),
                    )
                },
        )
    }

    // ---------------------------------------------------------------- Medicamentos

    fun medications(data: ExportData): ExportTable = ExportTable(
        "medicamentos", "Medicamentos", ExportModule.MEDICAMENTOS,
        listOf(
            c("Medicamento", 24, true), c("Dosis", 14), c("Indicaciones", 28, true), c("Horarios", 18, true), c("Días", 16),
            c("Inicio", 12), c("Fin", 12), c("Activo", 7), c("Recordatorio", 9), c("Notas", 30, true),
        ),
        data.medications.sortedWith(compareBy({ !it.active }, { it.name.lowercase() })).map { m ->
            listOf(
                m.name, m.dose.orNull(), m.instructions.orNull(),
                CycleCatalog.split(m.times).joinToString(", ").ifEmpty { "Según sea necesario" },
                daysMaskLabel(m.daysMask), DateCell(m.startEpochDay), m.endEpochDay?.let { DateCell(it) },
                yesNo(m.active), yesNo(m.remind), m.notes.orNull(),
            )
        },
    )

    fun intakes(data: ExportData): ExportTable {
        val byId = data.medications.associateBy { it.id }
        return ExportTable(
            "medicamentos_tomas", "Tomas", ExportModule.MEDICAMENTOS,
            listOf(c("Fecha", 12), c("Hora programada", 10), c("Registrada", 18), c("Medicamento", 24, true), c("Dosis", 14), c("Estado", 10)),
            data.medIntakes.sortedBy { it.timestamp }.map { i ->
                val m = byId[i.medicationId]
                listOf(
                    DateCell(i.epochDay), i.slot.ifEmpty { "Libre" }, DateTimeCell(i.timestamp), m?.name, m?.dose?.orNull(),
                    if (i.status == "skipped") "Omitida" else "Tomada",
                )
            },
        )
    }

    // ---------------------------------------------------------------- Citas

    fun appointments(data: ExportData): ExportTable = ExportTable(
        "citas", "Citas", ExportModule.CITAS,
        listOf(c("Fecha y hora", 18), c("Motivo", 28, true), c(if (data.isPet) "Veterinario" else "Médico", 20, true), c("Especialidad", 18, true), c("Lugar", 28, true), c("Realizada", 9), c("Notas", 36, true)),
        data.appointments.sortedBy { it.timestamp }.map { a ->
            listOf(DateTimeCell(a.timestamp), a.title, a.doctor.orNull(), a.specialty.orNull(), a.place.orNull(), yesNo(a.done), a.notes.orNull())
        },
    )

    // ---------------------------------------------------------------- Vacunas

    fun vaccinations(data: ExportData): ExportTable = ExportTable(
        "vacunas", "Vacunas", ExportModule.VACUNAS,
        listOf(
            c("Fecha", 12), c("Tipo", 22), c("Nombre", 28, true), c("Dosis", 16), c("Próxima", 12),
            c("Lugar", 26, true), c("Lote", 14), c("Recordatorio", 11), c("Notas", 40, true),
        ),
        data.vaccinations.sortedWith(compareBy({ it.epochDay }, { it.id })).map { v ->
            listOf(
                DateCell(v.epochDay), VaccinationTypes.label(v.type), v.name, v.dose.orNull(), v.nextDueEpochDay?.let { DateCell(it) },
                v.place.orNull(), v.lot.orNull(), if (v.nextDueEpochDay != null) yesNo(v.remind) else null, v.notes.orNull(),
            )
        },
    )

    // ---------------------------------------------------------------- Autoexploración

    fun selfExams(data: ExportData): ExportTable = ExportTable(
        "autoexploracion", "Autoexploración", ExportModule.AUTOEXPLORACION,
        listOf(c("Fecha", 12), c("Tipo", 18), c("Resultado", 16), c("Notas", 50, true)),
        data.selfExams.sortedBy { it.epochDay }.map { e ->
            listOf(DateCell(e.epochDay), SelfExamTypes.label(e.type), if (e.normal) "Sin cambios" else "Noté algo", e.notes.orNull())
        },
    )

    // ---------------------------------------------------------------- Perfil

    /** Datos de la ficha como pares (campo, valor). Omite los vacíos si [skipEmpty]. */
    fun profilePairs(data: ExportData, skipEmpty: Boolean = false): List<Pair<String, Any?>> {
        val p = data.profile ?: return emptyList()
        val all: List<Pair<String, Any?>> = if (p.isPet) {
            listOf(
                "Nombre" to p.name.orNull(),
                "Tipo de perfil" to "Mascota",
                "Especie" to p.species.orNull()?.let { PetSpecies.label(it) },
                "Raza" to p.breed.orNull(),
                "Sexo" to PetSpecies.sexes.firstOrNull { it.first == p.sex && p.sex != "X" }?.second,
                "Fecha de nacimiento" to p.birthEpochDay?.let { DateCell(it) },
                "Edad (años)" to p.birthEpochDay?.let { Fmt.age(it) },
                "Esterilizado" to p.sterilized?.let { yesNo(it) },
                "Microchip o placa" to p.microchip.orNull(),
                "Color y señas" to p.markings.orNull(),
                "Peso meta (kg)" to p.goalWeightKg,
                "Alergias" to p.allergies.orNull(),
                "Padecimientos" to p.conditions.orNull(),
                "Veterinario" to p.doctorName.orNull(),
                "Teléfono del veterinario" to p.doctorPhone.orNull(),
                "Notas" to p.notes.orNull(),
            )
        } else {
            listOf(
                "Nombre" to p.name.orNull(),
                "Tipo de perfil" to "Persona",
                "Parentesco" to p.relation.orNull(),
                "Sexo" to SexOptions.label(p.sex).orNull(),
                "Fecha de nacimiento" to p.birthEpochDay?.let { DateCell(it) },
                "Edad (años)" to p.birthEpochDay?.let { Fmt.age(it) },
                "Estatura (cm)" to p.heightCm,
                "Peso meta (kg)" to p.goalWeightKg,
                "Tipo de sangre" to p.bloodType.orNull(),
                "Alergias" to p.allergies.orNull(),
                "Padecimientos" to p.conditions.orNull(),
                "Contacto de emergencia" to p.emergencyName.orNull(),
                "Teléfono de emergencia" to p.emergencyPhone.orNull(),
                "Médico de cabecera" to p.doctorName.orNull(),
                "Teléfono del médico" to p.doctorPhone.orNull(),
                "Seguro médico" to p.insurance.orNull(),
                "Notas" to p.notes.orNull(),
            )
        }
        if (!skipEmpty) return all
        // "Tipo de perfil" siempre trae valor: si es lo único (ficha de persona en blanco), no hay nada que mostrar.
        val filled = all.filter { it.second != null }
        return if (!p.isPet && filled.all { it.first == "Tipo de perfil" || (it.first == "Sexo" && p.sex == "X") }) emptyList() else filled
    }

    fun profile(data: ExportData): ExportTable = ExportTable(
        "ficha", "Perfil", ExportModule.PERFIL,
        listOf(c("Campo", 26), c("Valor", 50, true)),
        profilePairs(data).map { listOf(it.first, it.second) },
    )

    // ---------------------------------------------------------------- Texto

    /** Valor de celda como texto legible (PDF) — fechas en español. */
    fun display(v: Any?): String = when (v) {
        null -> ""
        is DateCell -> Fmt.date(v.epochDay)
        is DateTimeCell -> Fmt.dateTime(v.millis)
        is Double -> Fmt.num(v, 2)
        is Float -> Fmt.num(v.toDouble(), 2)
        is Boolean -> yesNo(v)
        else -> v.toString()
    }

    /** Valor de celda como texto neutro para máquinas (CSV) — fechas ISO, punto decimal. */
    fun plain(v: Any?): String = when (v) {
        null -> ""
        is DateCell -> Fmt.isoDate(v.epochDay)
        is DateTimeCell -> Fmt.isoDateTime(v.millis)
        is Double -> Fmt.num(v, 4)
        is Float -> Fmt.num(v.toDouble(), 4)
        is Boolean -> yesNo(v)
        else -> v.toString()
    }

    /** Texto del rango de fechas de unas opciones. */
    fun rangeLabel(o: ExportOptions): String = when {
        o.fromDay == null && o.toDay == null -> "Todo el historial"
        o.fromDay != null && o.toDay != null -> "Del ${Fmt.date(o.fromDay)} al ${Fmt.date(o.toDay)}"
        o.fromDay != null -> "Desde el ${Fmt.date(o.fromDay)}"
        else -> "Hasta el ${Fmt.date(o.toDay!!)}"
    }
}
