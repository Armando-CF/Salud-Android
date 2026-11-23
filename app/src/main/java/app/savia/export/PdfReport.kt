package app.savia.export

import android.graphics.Paint
import android.graphics.RectF
import app.savia.data.CycleCatalog
import app.savia.data.MeasureType
import app.savia.data.Medication
import app.savia.data.Profile
import app.savia.data.SelfExamTypes
import app.savia.data.SexOptions
import app.savia.data.StudyCategories
import app.savia.data.SymptomCatalog
import app.savia.util.Fmt
import app.savia.util.millisToEpochDay
import app.savia.util.nowMillis
import java.io.OutputStream

/** Reporte de salud en PDF (tamaño carta) para imprimir o compartir con personal médico. */
object PdfReport {

    private fun d(v: Any?) = ExportTables.display(v)
    private fun col(title: String, weight: Float, right: Boolean = false) = PdfCol(title, weight, right)

    fun write(data: ExportData, out: OutputStream) = write(data, out, PdfDocumentSink())

    internal fun write(data: ExportData, out: OutputStream, sink: PageSink) {
        val name = data.profile?.name?.trim().orEmpty()
        val range = ExportTables.rangeLabel(data.options)
        val pdf = PdfBuilder(
            runningTitle = "Savia — Reporte de salud" + if (name.isNotEmpty()) " · $name" else "",
            runningRight = range,
            footerLeft = "Generado con Savia el ${Fmt.dateTime(data.generatedAt)}",
            sink = sink,
        )
        cover(pdf, data, name, range)

        if (data.has(ExportModule.PERFIL)) profile(pdf, data)
        if (data.has(ExportModule.CICLO)) cycle(pdf, data)
        if (data.has(ExportModule.MEDICIONES)) measurements(pdf, data)
        if (data.has(ExportModule.SINTOMAS)) symptoms(pdf, data)
        if (data.has(ExportModule.BIENESTAR)) wellbeing(pdf, data)
        if (data.has(ExportModule.ESTUDIOS)) studies(pdf, data)
        if (data.has(ExportModule.MEDICAMENTOS)) medications(pdf, data)
        if (data.has(ExportModule.CITAS)) appointments(pdf, data)
        if (data.has(ExportModule.AUTOEXPLORACION)) selfExams(pdf, data)

        pdf.space(8f)
        pdf.ensure(40f)
        pdf.paragraph(
            "Este reporte reúne información registrada por la persona usuaria en la app Savia. Es un apoyo para dar " +
                "seguimiento y conversar con personal de salud; no es un diagnóstico ni sustituye una valoración médica.",
            muted = true,
        )
        pdf.finish(out)
    }

    // ------------------------------------------------------------------ portada

    private fun cover(pdf: PdfBuilder, data: ExportData, name: String, range: String) {
        pdf.newPage()
        val c = pdf.canvas
        pdf.fillRect(pdf.left, pdf.y, pdf.left + pdf.contentW, pdf.y + 74f, PdfBuilder.C_PRIMARY, 10f)
        c.drawText("Savia", pdf.left + 18f, pdf.y + 30f, PdfBuilder.paint(22f, PdfBuilder.C_WHITE, bold = true))
        c.drawText("Reporte de salud", pdf.left + 18f, pdf.y + 54f, PdfBuilder.paint(15f, 0xFFD6F1E6.toInt()))
        val gen = "Generado el ${Fmt.date(data.generatedAt.millisToEpochDay())}"
        val pg = PdfBuilder.paint(9f, 0xFFD6F1E6.toInt())
        c.drawText(gen, pdf.left + pdf.contentW - 18f - pg.measureText(gen), pdf.y + 30f, pg)
        pdf.y += 88f
        val pairs = ArrayList<Pair<String, String>>()
        if (name.isNotEmpty()) pairs += "Nombre" to name
        pairs += "Periodo del reporte" to range
        pairs += "Contenido" to ExportModule.entries.filter { data.has(it) }.joinToString(", ") { it.label }
        pdf.keyValues(pairs, labelW = 130f)
    }

    // ------------------------------------------------------------------ ficha

    private fun profile(pdf: PdfBuilder, data: ExportData) {
        pdf.section("Mi ficha")
        val pairs = ExportTables.profilePairs(data, skipEmpty = true).map { it.first to d(it.second) }
        if (pairs.isEmpty()) pdf.paragraph("La ficha aún no tiene datos.", muted = true) else pdf.keyValues(pairs)
    }

    // ------------------------------------------------------------------ ciclo

    private fun cycle(pdf: PdfBuilder, data: ExportData) {
        pdf.section("Ciclo menstrual", "Las fechas estimadas son aproximaciones estadísticas; no sirven como método anticonceptivo.")
        val a = data.cycleAnalysis
        if (a == null || (!a.hasData && data.cycleDays.isEmpty())) {
            pdf.paragraph("Sin registros del ciclo.", muted = true)
            return
        }
        pdf.subheading("Resumen")
        pdf.keyValues(
            ExportTables.cycleStatPairs(data).filter { it.second != null && it.first != "Aviso" }.map { it.first to d(it.second) },
            labelW = 250f,
        )

        pdf.subheading("Historial de ciclos")
        val summary = ExportTables.cycleSummary(data)
        pdf.table(
            listOf(
                col("N.º", 26f, true), col("Inicio del periodo", 78f), col("Fin del periodo", 78f), col("Periodo (días)", 52f, true),
                col("Ciclo (días)", 52f, true), col("Ovulación estimada", 80f), col("Observación", 150f),
            ),
            summary.rows.reversed().map { r -> r.map { d(it) } },
            emptyText = "Aún no hay periodos registrados en este rango.",
        )

        pdf.subheading("Registro diario")
        val rows = data.cycleDays.sortedBy { it.epochDay }.map { day ->
            val info = a.info(day.epochDay)
            listOf(
                Fmt.date(day.epochDay),
                info.cycleDay?.toString() ?: "",
                if (info.cycleDay != null) info.phase.label else "",
                if (day.flow > 0) CycleCatalog.flowLabel(day.flow) else "",
                CycleCatalog.split(day.symptoms).joinToString(", ") { CycleCatalog.symptomLabel(it) },
                CycleCatalog.split(day.moods).joinToString(", ") { CycleCatalog.moodLabel(it) },
                ExportTables.cycleDayExtras(day) ?: "",
                day.notes.trim(),
            )
        }
        pdf.table(
            listOf(
                col("Fecha", 60f), col("Día", 24f, true), col("Fase estimada", 62f), col("Sangrado", 52f),
                col("Síntomas", 104f), col("Ánimo", 68f), col("Otros registros", 86f), col("Notas", 76f),
            ),
            rows,
            fontSize = 7.8f,
            emptyText = "Sin días registrados en este rango.",
        )
    }

    // ------------------------------------------------------------------ mediciones

    private fun measurements(pdf: PdfBuilder, data: ExportData) {
        pdf.section("Peso y mediciones")
        if (data.measurements.isEmpty()) {
            pdf.paragraph("Sin registros en este periodo.", muted = true)
            return
        }
        val groups = data.measurements.groupBy { it.type }
        val order = MeasureType.entries.map { it.key } + groups.keys.filter { MeasureType.of(it) == null }
        for (key in order) {
            val list = groups[key]?.sortedBy { it.timestamp } ?: continue
            val t = MeasureType.of(key)
            val dec = t?.decimals ?: 1
            val unit = t?.unit.orEmpty()
            pdf.subheading((t?.label ?: key) + if (unit.isNotEmpty()) " ($unit)" else "", keepWithNext = 90f)
            fun v(m: app.savia.data.Measurement) =
                if (m.value2 != null) "${Fmt.num(m.value, dec)}/${Fmt.num(m.value2, dec)}" else Fmt.num(m.value, dec)
            val vals = list.map { it.value }
            val summary = buildString {
                append("${list.size} ${if (list.size == 1) "registro" else "registros"}")
                append(" · Último: ${v(list.last())} $unit (${Fmt.date(list.last().timestamp.millisToEpochDay())})")
                if (list.size > 1) {
                    if (list.first().value2 == null) {
                        append(" · Mín: ${Fmt.num(vals.min(), dec)} · Máx: ${Fmt.num(vals.max(), dec)} · Promedio: ${Fmt.num(vals.average(), dec)}")
                        append(" · Cambio: ${Fmt.signed(list.last().value - list.first().value, dec)} $unit")
                    } else {
                        val v2 = list.mapNotNull { it.value2 }
                        append(" · Promedio: ${Fmt.num(vals.average(), dec)}/${Fmt.num(v2.average(), dec)}")
                    }
                }
            }
            pdf.paragraph(summary, muted = true)
            if (list.size >= 3 && list.first().value2 == null) {
                pdf.lineChart(
                    list.map { it.timestamp.toDouble() to it.value },
                    xLabel = { Fmt.date(it.toLong().millisToEpochDay()) },
                    yLabel = { Fmt.num(it, dec) },
                )
            }
            pdf.table(
                listOf(col("Fecha y hora", 110f), col("Valor", 70f, true), col("Contexto", 140f), col("Notas", 212f)),
                list.reversed().map { m -> listOf(Fmt.dateTime(m.timestamp), v(m) + if (unit.isNotEmpty()) " $unit" else "", m.tag, m.notes) },
            )
        }
    }

    // ------------------------------------------------------------------ síntomas

    private fun symptoms(pdf: PdfBuilder, data: ExportData) {
        pdf.section("Síntomas")
        if (data.symptoms.isEmpty()) {
            pdf.paragraph("Sin registros en este periodo.", muted = true)
            return
        }
        val top = data.symptoms.groupBy { it.name.trim() }.map { it.key to it.value }.sortedByDescending { it.second.size }.take(6)
        pdf.paragraph(
            "Más frecuentes: " + top.joinToString("; ") { (n, l) ->
                "$n (${l.size} ${if (l.size == 1) "vez" else "veces"}, intensidad promedio ${Fmt.num(l.map { it.intensity }.average(), 1)})"
            },
            muted = true,
        )
        pdf.table(
            listOf(
                col("Fecha y hora", 78f), col("Síntoma", 84f), col("Intensidad", 62f), col("Zona del cuerpo", 78f),
                col("Duración", 46f), col("Desencadenantes", 76f), col("Notas", 108f),
            ),
            data.symptoms.sortedByDescending { it.timestamp }.map { s ->
                listOf(
                    Fmt.dateTime(s.timestamp), s.name, "${s.intensity}/10 · ${SymptomCatalog.intensityLabel(s.intensity)}",
                    s.regionLabel.orEmpty(), s.durationMin?.let { Fmt.duration(it) }.orEmpty(),
                    CycleCatalog.split(s.tags).joinToString(", "), s.notes.trim(),
                )
            },
        )
    }

    // ------------------------------------------------------------------ bienestar

    private fun wellbeing(pdf: PdfBuilder, data: ExportData) {
        pdf.section("Bienestar diario", "Ánimo, energía y estrés en escala de 1 a 5.")
        val logs = data.dailyLogs
        if (logs.isEmpty()) {
            pdf.paragraph("Sin registros en este periodo.", muted = true)
            return
        }
        fun avg(values: List<Double>) = if (values.isEmpty()) null else Fmt.num(values.average(), 1)
        val parts = listOfNotNull(
            avg(logs.mapNotNull { it.mood?.toDouble() })?.let { "ánimo $it" },
            avg(logs.mapNotNull { it.energy?.toDouble() })?.let { "energía $it" },
            avg(logs.mapNotNull { it.stress?.toDouble() })?.let { "estrés $it" },
            avg(logs.mapNotNull { it.sleepHours })?.let { "sueño $it h" },
            avg(logs.mapNotNull { it.waterGlasses?.toDouble() })?.let { "agua $it vasos" },
        )
        if (parts.isNotEmpty()) pdf.paragraph("Promedios de ${logs.size} días: " + parts.joinToString(" · "), muted = true)
        pdf.table(
            listOf(
                col("Fecha", 66f), col("Ánimo", 38f, true), col("Energía", 40f, true), col("Estrés", 38f, true), col("Sueño (h)", 46f, true),
                col("Agua (vasos)", 48f, true), col("Ejercicio (min)", 52f, true), col("Notas", 204f),
            ),
            logs.sortedByDescending { it.epochDay }.map { l ->
                listOf(
                    Fmt.date(l.epochDay), l.mood?.toString().orEmpty(), l.energy?.toString().orEmpty(), l.stress?.toString().orEmpty(),
                    l.sleepHours?.let { Fmt.num(it, 1) }.orEmpty(), l.waterGlasses?.toString().orEmpty(), l.exerciseMin?.toString().orEmpty(), l.notes.trim(),
                )
            },
        )
    }

    // ------------------------------------------------------------------ estudios

    private fun studies(pdf: PdfBuilder, data: ExportData) {
        pdf.section("Estudios y chequeos")
        if (data.studies.isEmpty()) {
            pdf.paragraph("Sin registros en este periodo.", muted = true)
            return
        }
        val results = data.studyResults.groupBy { it.studyId }
        val attachments = data.studyAttachments.groupBy { it.studyId }
        for (s in data.studies.sortedWith(compareByDescending<app.savia.data.Study> { it.epochDay }.thenByDescending { it.id })) {
            pdf.subheading("${Fmt.date(s.epochDay)} — ${s.title}", keepWithNext = 70f)
            val pairs = ArrayList<Pair<String, String>>()
            pairs += "Categoría" to StudyCategories.short(s.category)
            if (s.place.isNotBlank()) pairs += "Lugar" to s.place.trim()
            if (s.doctor.isNotBlank()) pairs += "Médico" to s.doctor.trim()
            StudyCategories.statusLabel(s.status).takeIf { it.isNotEmpty() }?.let { pairs += "Estado" to it }
            if (s.summary.isNotBlank()) pairs += "Resumen" to s.summary.trim()
            s.followUpEpochDay?.let { pairs += "Seguimiento" to Fmt.date(it) }
            attachments[s.id]?.takeIf { it.isNotEmpty() }?.let { list -> pairs += "Archivos adjuntos" to list.joinToString(", ") { it.displayName } }
            pdf.keyValues(pairs, labelW = 110f)
            val rs = results[s.id].orEmpty()
            if (rs.isNotEmpty()) {
                pdf.table(
                    listOf(col("Parámetro", 150f), col("Resultado", 90f), col("Unidad", 52f), col("Referencia", 80f), col("Interpretación", 66f), col("Notas", 94f)),
                    rs.map { r ->
                        val ref = when {
                            r.refMin != null && r.refMax != null -> "${Fmt.num(r.refMin, 2)} – ${Fmt.num(r.refMax, 2)}"
                            r.refMin != null -> "≥ ${Fmt.num(r.refMin, 2)}"
                            r.refMax != null -> "≤ ${Fmt.num(r.refMax, 2)}"
                            else -> ""
                        }
                        listOf(
                            r.name, listOfNotNull(r.value?.let { Fmt.num(it, 2) }, r.textValue.trim().ifEmpty { null }).joinToString(" "),
                            r.unit, ref, ExportTables.resultInterpretation(r).orEmpty(), r.notes.trim(),
                        )
                    },
                )
            }
        }
    }

    // ------------------------------------------------------------------ medicamentos

    private fun medications(pdf: PdfBuilder, data: ExportData) {
        pdf.section("Medicamentos")
        if (data.medications.isEmpty()) {
            pdf.paragraph("Sin registros en este periodo.", muted = true)
            return
        }
        pdf.table(
            listOf(col("Medicamento", 96f), col("Dosis", 58f), col("Horarios", 70f), col("Días", 62f), col("Periodo", 96f), col("Estado", 44f), col("Indicaciones y notas", 106f)),
            data.medications.sortedWith(compareBy({ !it.active }, { it.name.lowercase() })).map { m ->
                listOf(
                    m.name, m.dose,
                    CycleCatalog.split(m.times).joinToString(", ").ifEmpty { "Según sea necesario" },
                    ExportTables.daysMaskLabel(m.daysMask),
                    "Desde " + Fmt.date(m.startEpochDay) + (m.endEpochDay?.let { " hasta ${Fmt.date(it)}" } ?: ""),
                    if (m.active) "Activo" else "Inactivo",
                    listOf(m.instructions.trim(), m.notes.trim()).filter { it.isNotEmpty() }.joinToString(". "),
                )
            },
        )
        if (data.medIntakes.isNotEmpty()) {
            pdf.subheading("Tomas registradas en el periodo")
            val byMed = data.medIntakes.groupBy { it.medicationId }
            pdf.table(
                listOf(col("Medicamento", 170f), col("Tomadas", 60f, true), col("Omitidas", 60f, true), col("Primera registrada", 120f), col("Última registrada", 120f)),
                data.medications.filter { byMed[it.id] != null }.map { m ->
                    val l = byMed[m.id].orEmpty()
                    listOf(
                        m.name, l.count { it.status != "skipped" }.toString(), l.count { it.status == "skipped" }.toString(),
                        Fmt.date(l.minOf { it.epochDay }), Fmt.date(l.maxOf { it.epochDay }),
                    )
                },
            )
            pdf.paragraph("El detalle de cada toma está disponible en la exportación a Excel o CSV.", muted = true)
        }
    }

    // ------------------------------------------------------------------ citas

    private fun appointments(pdf: PdfBuilder, data: ExportData) {
        pdf.section("Citas médicas")
        pdf.table(
            listOf(col("Fecha y hora", 84f), col("Motivo", 104f), col("Médico", 84f), col("Especialidad", 76f), col("Lugar", 94f), col("Realizada", 44f), col("Notas", 46f)),
            data.appointments.sortedByDescending { it.timestamp }.map { a ->
                listOf(Fmt.dateTime(a.timestamp), a.title, a.doctor, a.specialty, a.place, if (a.done) "Sí" else "No", a.notes.trim())
            },
        )
    }

    // ------------------------------------------------------------------ autoexploración

    private fun selfExams(pdf: PdfBuilder, data: ExportData) {
        pdf.section("Autoexploración")
        pdf.table(
            listOf(col("Fecha", 80f), col("Tipo", 100f), col("Resultado", 90f), col("Notas", 262f)),
            data.selfExams.sortedByDescending { it.epochDay }.map { e ->
                listOf(Fmt.date(e.epochDay), SelfExamTypes.label(e.type), if (e.normal) "Sin cambios" else "Noté algo", e.notes.trim())
            },
        )
    }
}

/**
 * "Ficha médica" de una página con los datos esenciales del perfil: pensada para imprimirse,
 * llevarse en la cartera o mostrarse en una urgencia.
 *
 * @param medications medicamentos a listar (normalmente los activos).
 * @param weightKg peso más reciente, si se conoce.
 */
fun writeMedicalCardPdf(
    profile: Profile?,
    medications: List<Medication>,
    weightKg: Double?,
    out: OutputStream,
    generatedAt: Long = nowMillis(),
) = writeMedicalCardPdf(profile, medications, weightKg, out, generatedAt, PdfDocumentSink())

internal fun writeMedicalCardPdf(
    profile: Profile?,
    medications: List<Medication>,
    weightKg: Double?,
    out: OutputStream,
    generatedAt: Long,
    sink: PageSink,
) {
    val p = profile ?: Profile()
    val pdf = PdfBuilder("Savia — Ficha médica", p.name.trim(), "Generada con Savia el ${Fmt.date(generatedAt.millisToEpochDay())}. Verifica que la información siga vigente.", sink)
    pdf.newPage()
    val c = pdf.canvas
    val left = pdf.left
    val w = pdf.contentW

    // Encabezado
    pdf.fillRect(left, pdf.y, left + w, pdf.y + 86f, PdfBuilder.C_PRIMARY, 12f)
    c.drawText("FICHA MÉDICA", left + 20f, pdf.y + 28f, PdfBuilder.paint(11f, 0xFFD6F1E6.toInt(), bold = true))
    val namePaint = PdfBuilder.paint(26f, PdfBuilder.C_WHITE, bold = true)
    val name = p.name.trim().ifEmpty { "Sin nombre" }
    while (namePaint.measureText(name) > w - 40f && namePaint.textSize > 12f) namePaint.textSize -= 1f
    c.drawText(name, left + 20f, pdf.y + 62f, namePaint)
    pdf.y += 100f

    // Datos rápidos en recuadros
    val bmi = if (weightKg != null && p.heightCm != null && p.heightCm > 0) weightKg / ((p.heightCm / 100) * (p.heightCm / 100)) else null
    val tiles = listOf(
        "Tipo de sangre" to p.bloodType.trim().ifEmpty { "—" },
        "Edad" to (p.birthEpochDay?.let { "${Fmt.age(it)} años" } ?: "—"),
        "Sexo" to (SexOptions.label(p.sex).takeIf { p.sex != "X" && it.isNotEmpty() } ?: "—"),
        "Estatura" to (p.heightCm?.let { "${Fmt.num(it, 0)} cm" } ?: "—"),
        "Peso" to (weightKg?.let { "${Fmt.num(it, 1)} kg" } ?: "—"),
    )
    val gap = 8f
    val tw = (w - gap * (tiles.size - 1)) / tiles.size
    val pTileLabel = PdfBuilder.paint(8f, PdfBuilder.C_MUTED)
    tiles.forEachIndexed { i, (label, value) ->
        val x = left + i * (tw + gap)
        val first = i == 0
        pdf.fillRect(x, pdf.y, x + tw, pdf.y + 50f, if (first) 0xFFFDE3E9.toInt() else PdfBuilder.C_ZEBRA, 8f)
        c.drawText(label, x + 9f, pdf.y + 16f, pTileLabel)
        val pv = PdfBuilder.paint(if (first) 18f else 14f, if (first) PdfBuilder.C_ROSE else PdfBuilder.C_TEXT, bold = true)
        while (pv.measureText(value) > tw - 16f && pv.textSize > 8f) pv.textSize -= 0.5f
        c.drawText(value, x + 9f, pdf.y + 38f, pv)
    }
    pdf.y += 62f
    if (bmi != null || p.birthEpochDay != null) {
        val extra = listOfNotNull(
            p.birthEpochDay?.let { "Fecha de nacimiento: ${Fmt.date(it)}" },
            bmi?.let { "IMC: ${Fmt.num(it, 1)}" },
        ).joinToString("   ·   ")
        c.drawText(extra, left, pdf.y + 8f, PdfBuilder.paint(9f, PdfBuilder.C_MUTED))
        pdf.y += 20f
    }

    /** Bloque con título y texto; [highlight] lo resalta (alergias, emergencia). */
    fun block(title: String, body: String, highlight: Int? = null, titleColor: Int = PdfBuilder.C_PRIMARY, bodySize: Float = 11f) {
        val pBody = PdfBuilder.paint(bodySize)
        val lines = PdfBuilder.wrap(body, pBody, w - 24f)
        val lh = bodySize * 1.35f
        val h = 24f + lines.size * lh + 8f
        if (pdf.ensure(h + 8f)) { /* nueva página */ }
        val cv = pdf.canvas
        if (highlight != null) {
            pdf.fillRect(left, pdf.y, left + w, pdf.y + h, highlight, 8f)
        } else {
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.8f; color = PdfBuilder.C_LINE }
            cv.drawRoundRect(RectF(left, pdf.y, left + w, pdf.y + h), 8f, 8f, stroke)
        }
        cv.drawText(title.uppercase(), left + 12f, pdf.y + 16f, PdfBuilder.paint(8.5f, titleColor, bold = true))
        var ty = pdf.y + 24f + bodySize
        for (l in lines) {
            cv.drawText(l, left + 12f, ty, pBody)
            ty += lh
        }
        pdf.y += h + 8f
    }

    val emergency = listOf(p.emergencyName.trim(), p.emergencyPhone.trim()).filter { it.isNotEmpty() }.joinToString("  ·  Tel. ")
    block("Contacto de emergencia", emergency.ifEmpty { "Sin registrar" }, highlight = 0xFFFDE3E9.toInt(), titleColor = PdfBuilder.C_ROSE, bodySize = 13f)
    block("Alergias", p.allergies.trim().ifEmpty { "Sin alergias registradas" }, highlight = PdfBuilder.C_WARN_FILL, titleColor = PdfBuilder.C_WARN, bodySize = 12f)
    block("Padecimientos", p.conditions.trim().ifEmpty { "Sin padecimientos registrados" })
    val meds = medications.sortedBy { it.name.lowercase() }.joinToString("\n") { m ->
        val times = CycleCatalog.split(m.times).joinToString(", ")
        "• " + listOf(m.name.trim(), m.dose.trim()).filter { it.isNotEmpty() }.joinToString(" ") +
            (if (times.isNotEmpty()) " — $times" else " — según sea necesario") +
            (if (m.instructions.isNotBlank()) " (${m.instructions.trim()})" else "")
    }
    block("Medicamentos actuales", meds.ifEmpty { "Sin medicamentos activos registrados" })
    val doctor = listOf(p.doctorName.trim(), p.doctorPhone.trim()).filter { it.isNotEmpty() }.joinToString("  ·  Tel. ")
    if (doctor.isNotEmpty()) block("Médico de cabecera", doctor)
    if (p.insurance.isNotBlank()) block("Seguro médico", p.insurance.trim())
    if (p.notes.isNotBlank()) block("Notas", p.notes.trim())

    pdf.finish(out)
}
