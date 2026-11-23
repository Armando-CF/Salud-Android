package app.savia.export

import app.savia.util.Fmt
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** CSV según RFC 4180: separador coma, comillas dobles, saltos CRLF, UTF-8 con BOM (para que Excel respete los acentos). */
object CsvWriter {
    private val BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    /** Escapa un campo: se entrecomilla si trae coma, comillas, saltos de línea o espacios en los extremos. */
    fun field(s: String): String {
        val needsQuotes = s.any { it == ',' || it == '"' || it == '\n' || it == '\r' } ||
            (s.isNotEmpty() && (s.first() == ' ' || s.last() == ' '))
        return if (needsQuotes) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    fun line(values: List<String>): String = values.joinToString(",") { field(it) } + "\r\n"

    /** Texto CSV completo de una tabla (sin BOM). */
    fun text(table: ExportTable): String = buildString {
        append(line(table.columns.map { it.title }))
        for (row in table.rows) {
            append(line(table.columns.indices.map { ExportTables.plain(row.getOrNull(it)) }))
        }
    }

    /** Un solo archivo .csv. */
    fun write(table: ExportTable, out: OutputStream) {
        out.write(BOM)
        out.write(text(table).toByteArray(Charsets.UTF_8))
        out.flush()
    }

    /** Un .zip con un CSV por tabla. */
    fun writeZip(tables: List<ExportTable>, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            val used = HashSet<String>()
            for (t in tables) {
                var name = t.id
                var n = 2
                while (!used.add(name)) name = "${t.id}_${n++}"
                zip.putNextEntry(ZipEntry("$name.csv"))
                zip.write(BOM)
                zip.write(text(t).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("LEEME.txt"))
            zip.write(
                ("Exportación de Savia\r\n" +
                    "Cada archivo .csv es una tabla (UTF-8, separado por comas).\r\n" +
                    "Las fechas van en formato AAAA-MM-DD y los decimales con punto.\r\n").toByteArray(Charsets.UTF_8),
            )
            zip.closeEntry()
        }
    }
}

/**
 * JSON legible y abierto: las mismas tablas que Excel, con encabezados en español.
 * (El respaldo completo para restaurar es otro formato; ver [Backup].)
 */
object JsonExportWriter {
    private fun value(v: Any?): Any = when (v) {
        null -> JSONObject.NULL
        is DateCell -> Fmt.isoDate(v.epochDay)
        is DateTimeCell -> Fmt.isoDateTime(v.millis)
        is Double -> if (v.isNaN() || v.isInfinite()) JSONObject.NULL else v
        is Float -> v.toDouble()
        is Number, is Boolean -> v
        else -> v.toString()
    }

    fun build(data: ExportData, tables: List<ExportTable> = ExportTables.build(data)): JSONObject {
        val root = JSONObject()
        val meta = JSONObject()
        meta.put("aplicacion", "Savia")
        meta.put("tipo", "exportacion")
        meta.put("version_formato", 1)
        meta.put("generado", Fmt.isoDateTime(data.generatedAt))
        meta.put("rango", ExportTables.rangeLabel(data.options))
        meta.put("desde", data.options.fromDay?.let { Fmt.isoDate(it) } ?: JSONObject.NULL)
        meta.put("hasta", data.options.toDay?.let { Fmt.isoDate(it) } ?: JSONObject.NULL)
        meta.put("modulos", JSONArray(ExportModule.entries.filter { data.has(it) }.map { it.label }))
        meta.put("nota", "Fechas en formato AAAA-MM-DD (hora local). Los valores vacíos van como null.")
        root.put("savia", meta)
        val tablas = JSONObject()
        for (t in tables) {
            val arr = JSONArray()
            for (row in t.rows) {
                val o = JSONObject()
                t.columns.forEachIndexed { i, c -> o.put(c.title, value(row.getOrNull(i))) }
                arr.put(o)
            }
            tablas.put(t.title, arr)
        }
        root.put("tablas", tablas)
        return root
    }

    fun write(data: ExportData, out: OutputStream) {
        out.write(build(data).toString(2).toByteArray(Charsets.UTF_8))
        out.flush()
    }
}
