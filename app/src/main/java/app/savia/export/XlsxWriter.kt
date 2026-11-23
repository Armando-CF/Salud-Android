package app.savia.export

import app.savia.util.millisToDateTime
import java.io.OutputStream
import java.math.BigDecimal
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Escritor mínimo de libros de Excel (.xlsx, formato OOXML) sin dependencias:
 * una hoja por tabla, encabezado con estilo y congelado, filtros, anchos de columna,
 * fechas como fechas reales de Excel y números como números.
 */
object XlsxWriter {
    private const val XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
    private const val NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val NS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val NS_PKG_REL = "http://schemas.openxmlformats.org/package/2006/relationships"

    // Índices de estilos (cellXfs) definidos en styles.xml
    private const val S_DEFAULT = 0
    private const val S_HEADER = 1
    private const val S_DATE = 2
    private const val S_DATETIME = 3
    private const val S_WRAP = 4

    /** Días entre 1899-12-30 (día 0 de Excel) y 1970-01-01. */
    private const val EXCEL_EPOCH_OFFSET = 25569L

    fun write(tables: List<ExportTable>, out: OutputStream, title: String = "Savia") {
        val sheets = tables.ifEmpty {
            listOf(ExportTable("vacio", "Sin datos", ExportModule.PERFIL, listOf(ExportColumn("Sin datos", 30)), emptyList()))
        }
        val names = uniqueSheetNames(sheets.map { it.title })
        ZipOutputStream(out).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("[Content_Types].xml", contentTypes(sheets.size))
            entry("_rels/.rels", rootRels())
            entry("docProps/core.xml", coreProps(title))
            entry("docProps/app.xml", appProps())
            entry("xl/workbook.xml", workbook(names))
            entry("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            entry("xl/styles.xml", styles())
            sheets.forEachIndexed { i, t ->
                zip.putNextEntry(ZipEntry("xl/worksheets/sheet${i + 1}.xml"))
                val w = zip.bufferedWriter(Charsets.UTF_8)
                writeSheet(t, w, selected = i == 0)
                w.flush()
                zip.closeEntry()
            }
        }
    }

    // ------------------------------------------------------------------ partes fijas

    private fun contentTypes(n: Int) = buildString {
        append(XML)
        append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
        for (i in 1..n) {
            append("<Override PartName=\"/xl/worksheets/sheet$i.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
        }
        append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
        append("<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>")
        append("<Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/>")
        append("</Types>")
    }

    private fun rootRels() = buildString {
        append(XML)
        append("<Relationships xmlns=\"$NS_PKG_REL\">")
        append("<Relationship Id=\"rId1\" Type=\"$NS_REL/officeDocument\" Target=\"xl/workbook.xml\"/>")
        append("<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>")
        append("<Relationship Id=\"rId3\" Type=\"$NS_REL/extended-properties\" Target=\"docProps/app.xml\"/>")
        append("</Relationships>")
    }

    private fun coreProps(title: String): String {
        val now = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString()
        return buildString {
            append(XML)
            append("<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" ")
            append("xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\" ")
            append("xmlns:dcmitype=\"http://purl.org/dc/dcmitype/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">")
            append("<dc:title>").append(esc(title)).append("</dc:title>")
            append("<dc:creator>Savia</dc:creator>")
            append("<dcterms:created xsi:type=\"dcterms:W3CDTF\">").append(now).append("</dcterms:created>")
            append("<dcterms:modified xsi:type=\"dcterms:W3CDTF\">").append(now).append("</dcterms:modified>")
            append("</cp:coreProperties>")
        }
    }

    private fun appProps() = XML +
        "<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\"><Application>Savia</Application></Properties>"

    private fun workbook(names: List<String>) = buildString {
        append(XML)
        append("<workbook xmlns=\"$NS_MAIN\" xmlns:r=\"$NS_REL\">")
        append("<bookViews><workbookView xWindow=\"0\" yWindow=\"0\" windowWidth=\"24000\" windowHeight=\"12000\"/></bookViews>")
        append("<sheets>")
        names.forEachIndexed { i, n ->
            append("<sheet name=\"").append(esc(n)).append("\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRels(n: Int) = buildString {
        append(XML)
        append("<Relationships xmlns=\"$NS_PKG_REL\">")
        for (i in 1..n) {
            append("<Relationship Id=\"rId$i\" Type=\"$NS_REL/worksheet\" Target=\"worksheets/sheet$i.xml\"/>")
        }
        append("<Relationship Id=\"rId${n + 1}\" Type=\"$NS_REL/styles\" Target=\"styles.xml\"/>")
        append("</Relationships>")
    }

    private fun styles() = buildString {
        append(XML)
        append("<styleSheet xmlns=\"$NS_MAIN\">")
        append("<numFmts count=\"2\">")
        append("<numFmt numFmtId=\"164\" formatCode=\"yyyy\\-mm\\-dd\"/>")
        append("<numFmt numFmtId=\"165\" formatCode=\"yyyy\\-mm\\-dd\\ hh:mm\"/>")
        append("</numFmts>")
        append("<fonts count=\"2\">")
        append("<font><sz val=\"11\"/><name val=\"Calibri\"/><family val=\"2\"/></font>")
        append("<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/><family val=\"2\"/></font>")
        append("</fonts>")
        append("<fills count=\"3\">")
        append("<fill><patternFill patternType=\"none\"/></fill>")
        append("<fill><patternFill patternType=\"gray125\"/></fill>")
        append("<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF1B6B58\"/><bgColor indexed=\"64\"/></patternFill></fill>")
        append("</fills>")
        append("<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>")
        append("<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>")
        append("<cellXfs count=\"5\">")
        // 0: normal
        append("<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyAlignment=\"1\"><alignment vertical=\"top\"/></xf>")
        // 1: encabezado
        append("<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyAlignment=\"1\"><alignment vertical=\"center\" wrapText=\"1\"/></xf>")
        // 2: fecha
        append("<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\" applyAlignment=\"1\"><alignment horizontal=\"left\" vertical=\"top\"/></xf>")
        // 3: fecha y hora
        append("<xf numFmtId=\"165\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\" applyAlignment=\"1\"><alignment horizontal=\"left\" vertical=\"top\"/></xf>")
        // 4: texto ajustado
        append("<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyAlignment=\"1\"><alignment vertical=\"top\" wrapText=\"1\"/></xf>")
        append("</cellXfs>")
        append("<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>")
        append("</styleSheet>")
    }

    // ------------------------------------------------------------------ hojas

    private fun writeSheet(t: ExportTable, w: Appendable, selected: Boolean) {
        val cols = t.columns.size.coerceAtLeast(1)
        val lastCol = colName(cols - 1)
        val lastRow = t.rows.size + 1
        w.append(XML)
        w.append("<worksheet xmlns=\"$NS_MAIN\" xmlns:r=\"$NS_REL\">")
        w.append("<dimension ref=\"A1:$lastCol$lastRow\"/>")
        w.append("<sheetViews><sheetView workbookViewId=\"0\"")
        if (selected) w.append(" tabSelected=\"1\"")
        w.append("><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>")
        w.append("<selection pane=\"bottomLeft\" activeCell=\"A2\" sqref=\"A2\"/></sheetView></sheetViews>")
        w.append("<sheetFormatPr defaultRowHeight=\"15\"/>")
        w.append("<cols>")
        t.columns.forEachIndexed { i, c ->
            val width = c.width.coerceIn(6, 80) + 2
            w.append("<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"$width\" customWidth=\"1\"/>")
        }
        w.append("</cols>")
        w.append("<sheetData>")
        // Encabezado
        w.append("<row r=\"1\" ht=\"32\" customHeight=\"1\">")
        t.columns.forEachIndexed { i, c -> stringCell(w, colName(i) + "1", c.title, S_HEADER) }
        w.append("</row>")
        // Datos
        t.rows.forEachIndexed { ri, row ->
            val r = ri + 2
            w.append("<row r=\"$r\">")
            for (ci in 0 until minOf(row.size, cols)) {
                val ref = colName(ci) + r
                val wrap = t.columns.getOrNull(ci)?.wrap == true
                when (val v = row[ci]) {
                    null -> {}
                    is DateCell -> numberCell(w, ref, (v.epochDay + EXCEL_EPOCH_OFFSET).toString(), S_DATE)
                    is DateTimeCell -> numberCell(w, ref, excelDateTime(v.millis), S_DATETIME)
                    is Int, is Long, is Short, is Byte -> numberCell(w, ref, v.toString(), S_DEFAULT)
                    is Number -> {
                        val d = v.toDouble()
                        if (d.isNaN() || d.isInfinite()) {} else numberCell(w, ref, BigDecimal(d.toString()).toPlainString(), S_DEFAULT)
                    }
                    is Boolean -> stringCell(w, ref, if (v) "Sí" else "No", S_DEFAULT)
                    else -> {
                        val s = v.toString()
                        if (s.isNotEmpty()) stringCell(w, ref, s, if (wrap) S_WRAP else S_DEFAULT)
                    }
                }
            }
            w.append("</row>")
        }
        w.append("</sheetData>")
        if (t.rows.isNotEmpty()) w.append("<autoFilter ref=\"A1:$lastCol$lastRow\"/>")
        w.append("<pageMargins left=\"0.5\" right=\"0.5\" top=\"0.75\" bottom=\"0.75\" header=\"0.3\" footer=\"0.3\"/>")
        w.append("<pageSetup orientation=\"landscape\"/>")
        w.append("</worksheet>")
    }

    private fun stringCell(w: Appendable, ref: String, text: String, style: Int) {
        w.append("<c r=\"").append(ref).append("\" s=\"").append(style.toString()).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
        w.append(esc(text.take(32000)))
        w.append("</t></is></c>")
    }

    private fun numberCell(w: Appendable, ref: String, value: String, style: Int) {
        w.append("<c r=\"").append(ref).append("\" s=\"").append(style.toString()).append("\"><v>").append(value).append("</v></c>")
    }

    /** Milisegundos epoch → número de serie de Excel en hora local. */
    private fun excelDateTime(millis: Long): String {
        val dt = millis.millisToDateTime()
        val serial = dt.toLocalDate().toEpochDay() + EXCEL_EPOCH_OFFSET + dt.toLocalTime().toSecondOfDay() / 86400.0
        return BigDecimal(serial).setScale(8, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }

    // ------------------------------------------------------------------ utilidades

    /** 0 → A, 25 → Z, 26 → AA… */
    fun colName(index: Int): String {
        var i = index
        val sb = StringBuilder()
        do {
            sb.append('A' + (i % 26))
            i = i / 26 - 1
        } while (i >= 0)
        return sb.reverse().toString()
    }

    /** Escapa texto para XML y quita caracteres que XML 1.0 no permite. */
    fun esc(s: String): String {
        val sb = StringBuilder(s.length + 16)
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch == '\'' -> sb.append("&apos;")
                ch == '\r' -> { /* se normaliza a \n */ if (i + 1 >= s.length || s[i + 1] != '\n') sb.append('\n') }
                ch == '\t' || ch == '\n' -> sb.append(ch)
                ch < ' ' -> {}
                ch == '￾' || ch == '￿' -> {}
                Character.isHighSurrogate(ch) -> {
                    if (i + 1 < s.length && Character.isLowSurrogate(s[i + 1])) {
                        sb.append(ch).append(s[i + 1])
                        i++
                    }
                }
                Character.isLowSurrogate(ch) -> {}
                else -> sb.append(ch)
            }
            i++
        }
        return sb.toString()
    }

    /** Nombres de hoja válidos para Excel: sin []:*?/\, máx. 31 caracteres, únicos. */
    fun uniqueSheetNames(titles: List<String>): List<String> {
        val used = HashSet<String>()
        return titles.map { raw ->
            var base = raw.replace(Regex("[\\[\\]:*?/\\\\]"), " ").trim().trim('\'').take(31).ifEmpty { "Hoja" }
            var name = base
            var n = 2
            while (!used.add(name.lowercase())) {
                val suffix = " ($n)"
                name = base.take(31 - suffix.length) + suffix
                n++
            }
            base = name
            base
        }
    }
}
