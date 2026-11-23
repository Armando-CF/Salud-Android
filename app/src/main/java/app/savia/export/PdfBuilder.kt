package app.savia.export

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream

/**
 * Destino de las páginas. En la app es un [PdfDocument]; en pruebas puede ser un mapa de bits
 * para revisar la maquetación sin depender del generador de PDF del sistema.
 */
internal interface PageSink {
    fun startPage(width: Int, height: Int, number: Int): Canvas
    fun finishPage()
    fun finish(out: OutputStream)
}

/** Implementación real sobre [PdfDocument]. */
internal class PdfDocumentSink : PageSink {
    private val doc = PdfDocument()
    private var page: PdfDocument.Page? = null

    override fun startPage(width: Int, height: Int, number: Int): Canvas {
        val p = doc.startPage(PdfDocument.PageInfo.Builder(width, height, number).create())
        page = p
        return p.canvas
    }

    override fun finishPage() {
        page?.let { doc.finishPage(it) }
        page = null
    }

    override fun finish(out: OutputStream) {
        try {
            doc.writeTo(out)
            out.flush()
        } finally {
            doc.close()
        }
    }
}

/** Columna de una tabla en PDF. [weight] reparte el ancho disponible. */
internal class PdfCol(val title: String, val weight: Float, val alignRight: Boolean = false)

/**
 * Pequeño motor de maquetación sobre [PdfDocument]: páginas tamaño carta, encabezado y pie,
 * párrafos con ajuste de línea y tablas con salto de página automático.
 * Unidades en puntos (1/72 de pulgada).
 */
internal class PdfBuilder(
    private val runningTitle: String,
    private val runningRight: String,
    private val footerLeft: String,
    private val sink: PageSink = PdfDocumentSink(),
) {
    companion object {
        const val PAGE_W = 612f
        const val PAGE_H = 792f
        const val MARGIN = 40f

        const val C_PRIMARY = 0xFF1B6B58.toInt()
        const val C_TEXT = 0xFF1A1F1C.toInt()
        const val C_MUTED = 0xFF5A6560.toInt()
        const val C_LINE = 0xFFC9D3CD.toInt()
        const val C_HEADER_FILL = 0xFFDCEFE7.toInt()
        const val C_ZEBRA = 0xFFF4F8F6.toInt()
        const val C_ROSE = 0xFFB5345A.toInt()
        const val C_WARN_FILL = 0xFFFFF1DC.toInt()
        const val C_WARN = 0xFF9A5B00.toInt()
        const val C_WHITE = 0xFFFFFFFF.toInt()

        private val REGULAR: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        private val BOLD: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

        fun paint(size: Float, color: Int = C_TEXT, bold: Boolean = false): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) BOLD else REGULAR
            isSubpixelText = true
        }

        /** Parte [text] en renglones que quepan en [maxW]; respeta saltos de línea y corta palabras larguísimas. */
        fun wrap(text: String, paint: Paint, maxW: Float): List<String> {
            val out = ArrayList<String>()
            for (para in text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
                if (para.isBlank()) {
                    out += ""
                    continue
                }
                var line = ""
                for (word in para.split(' ')) {
                    if (word.isEmpty()) continue
                    val candidate = if (line.isEmpty()) word else "$line $word"
                    if (paint.measureText(candidate) <= maxW) {
                        line = candidate
                        continue
                    }
                    if (line.isNotEmpty()) {
                        out += line
                        line = ""
                    }
                    var w = word
                    while (w.length > 1 && paint.measureText(w) > maxW) {
                        val n = paint.breakText(w, true, maxW, null).coerceIn(1, w.length - 1)
                        out += w.substring(0, n)
                        w = w.substring(n)
                    }
                    line = w
                }
                out += line
            }
            // Sin renglones vacíos al final.
            while (out.size > 1 && out.last().isEmpty()) out.removeAt(out.lastIndex)
            return out
        }
    }

    private var pageOpen = false
    lateinit var canvas: Canvas
        private set
    var pageNo = 0
        private set

    /** Posición vertical actual (borde superior del siguiente elemento). */
    var y = MARGIN

    val left = MARGIN
    val contentW = PAGE_W - 2 * MARGIN
    val bottom = PAGE_H - MARGIN - 14f

    private val pBody = paint(9.5f)
    private val pBodyBold = paint(9.5f, bold = true)
    private val pMuted = paint(8.5f, C_MUTED)
    private val pSection = paint(14f, C_PRIMARY, bold = true)
    private val pSub = paint(11f, C_TEXT, bold = true)
    private val pRun = paint(8f, C_MUTED)
    private val pLine = Paint().apply { color = C_LINE; strokeWidth = 0.6f; style = Paint.Style.STROKE }
    private val pFill = Paint().apply { style = Paint.Style.FILL }

    fun newPage() {
        finishPage()
        pageNo++
        canvas = sink.startPage(PAGE_W.toInt(), PAGE_H.toInt(), pageNo)
        pageOpen = true
        // Pie
        canvas.drawLine(left, PAGE_H - MARGIN - 4f, left + contentW, PAGE_H - MARGIN - 4f, pLine)
        canvas.drawText(footerLeft, left, PAGE_H - MARGIN + 8f, pRun)
        val pn = "Página $pageNo"
        canvas.drawText(pn, left + contentW - pRun.measureText(pn), PAGE_H - MARGIN + 8f, pRun)
        // Encabezado corrido (a partir de la segunda página)
        if (pageNo > 1) {
            canvas.drawText(runningTitle, left, MARGIN - 6f, pRun)
            canvas.drawText(runningRight, left + contentW - pRun.measureText(runningRight), MARGIN - 6f, pRun)
            canvas.drawLine(left, MARGIN, left + contentW, MARGIN, pLine)
            y = MARGIN + 14f
        } else {
            y = MARGIN
        }
    }

    private fun finishPage() {
        if (pageOpen) sink.finishPage()
        pageOpen = false
    }

    /** Garantiza [h] puntos libres; si no caben, salta de página. Devuelve true si saltó. */
    fun ensure(h: Float): Boolean {
        if (!pageOpen || y + h > bottom) {
            newPage()
            return true
        }
        return false
    }

    fun space(h: Float) {
        y += h
    }

    fun fillRect(l: Float, t: Float, r: Float, b: Float, color: Int, radius: Float = 0f) {
        pFill.color = color
        if (radius > 0) canvas.drawRoundRect(RectF(l, t, r, b), radius, radius, pFill) else canvas.drawRect(l, t, r, b, pFill)
    }

    fun hLine(color: Int = C_LINE) {
        pLine.color = color
        canvas.drawLine(left, y, left + contentW, y, pLine)
        pLine.color = C_LINE
    }

    /** Título de sección (módulo). */
    fun section(title: String, subtitle: String? = null) {
        ensure(64f)
        if (y > MARGIN + 20f) y += 12f
        fillRect(left, y + 1f, left + 4f, y + 17f, C_PRIMARY, 2f)
        canvas.drawText(title, left + 11f, y + 14f, pSection)
        y += 22f
        if (subtitle != null) {
            for (l in wrap(subtitle, pMuted, contentW)) {
                canvas.drawText(l, left, y + 9f, pMuted)
                y += 11.5f
            }
        }
        pLine.color = C_PRIMARY
        canvas.drawLine(left, y + 2f, left + contentW, y + 2f, pLine)
        pLine.color = C_LINE
        y += 10f
    }

    /** Subtítulo dentro de una sección. */
    fun subheading(text: String, keepWithNext: Float = 46f) {
        ensure(keepWithNext)
        y += 4f
        for (l in wrap(text, pSub, contentW)) {
            canvas.drawText(l, left, y + 11f, pSub)
            y += 14f
        }
        y += 3f
    }

    fun paragraph(text: String, muted: Boolean = false, bold: Boolean = false, indent: Float = 0f) {
        val p = if (muted) pMuted else if (bold) pBodyBold else pBody
        val lh = p.textSize * 1.32f
        for (l in wrap(text, p, contentW - indent)) {
            ensure(lh)
            canvas.drawText(l, left + indent, y + p.textSize, p)
            y += lh
        }
        y += 3f
    }

    /** Lista "Etiqueta   valor" con valores ajustados. */
    fun keyValues(pairs: List<Pair<String, String>>, labelW: Float = 190f) {
        val lh = pBody.textSize * 1.32f
        for ((k, v) in pairs) {
            val kl = wrap(k, pMuted, labelW - 8f)
            val vl = wrap(v, pBody, contentW - labelW)
            val h = maxOf(kl.size, vl.size) * lh + 4f
            ensure(h)
            var ty = y
            for (l in kl) {
                canvas.drawText(l, left, ty + pBody.textSize, pMuted)
                ty += lh
            }
            ty = y
            for (l in vl) {
                canvas.drawText(l, left + labelW, ty + pBody.textSize, pBody)
                ty += lh
            }
            y += h
            pLine.color = 0xFFE4EAE6.toInt()
            canvas.drawLine(left, y - 1.5f, left + contentW, y - 1.5f, pLine)
            pLine.color = C_LINE
        }
        y += 4f
    }

    /** Tabla con encabezado repetido en cada página y renglones de alto variable. */
    fun table(cols: List<PdfCol>, rows: List<List<String>>, fontSize: Float = 8.2f, emptyText: String = "Sin registros en este periodo.") {
        if (rows.isEmpty()) {
            paragraph(emptyText, muted = true)
            return
        }
        val pCell = paint(fontSize)
        val pHead = paint(fontSize, C_PRIMARY, bold = true)
        val pad = 3.5f
        val lh = fontSize * 1.28f
        val total = cols.sumOf { it.weight.toDouble() }.toFloat()
        val widths = cols.map { contentW * it.weight / total }
        val xs = FloatArray(cols.size)
        var acc = left
        for (i in cols.indices) {
            xs[i] = acc
            acc += widths[i]
        }
        val headLines = cols.mapIndexed { i, c -> wrap(c.title, pHead, widths[i] - 2 * pad) }
        val headH = headLines.maxOf { it.size } * lh + 2 * pad

        fun drawHeader() {
            fillRect(left, y, left + contentW, y + headH, C_HEADER_FILL)
            for (i in cols.indices) {
                var ty = y + pad + fontSize
                for (l in headLines[i]) {
                    val x = if (cols[i].alignRight) xs[i] + widths[i] - pad - pHead.measureText(l) else xs[i] + pad
                    canvas.drawText(l, x, ty, pHead)
                    ty += lh
                }
            }
            y += headH
        }

        ensure(headH + lh * 2 + 2 * pad)
        drawHeader()
        val maxLines = (((bottom - MARGIN - 14f - headH - 2 * pad) / lh).toInt()).coerceAtLeast(3)
        rows.forEachIndexed { ri, row ->
            val cells = cols.indices.map { i ->
                val lines = wrap(row.getOrNull(i) ?: "", pCell, widths[i] - 2 * pad)
                if (lines.size > maxLines) lines.take(maxLines - 1) + "…" else lines
            }
            val h = cells.maxOf { it.size }.coerceAtLeast(1) * lh + 2 * pad
            if (y + h > bottom) {
                newPage()
                drawHeader()
            }
            if (ri % 2 == 1) fillRect(left, y, left + contentW, y + h, C_ZEBRA)
            for (i in cols.indices) {
                var ty = y + pad + fontSize
                for (l in cells[i]) {
                    val x = if (cols[i].alignRight) xs[i] + widths[i] - pad - pCell.measureText(l) else xs[i] + pad
                    canvas.drawText(l, x, ty, pCell)
                    ty += lh
                }
            }
            y += h
            canvas.drawLine(left, y, left + contentW, y, pLine)
        }
        y += 8f
    }

    /**
     * Gráfica de líneas sencilla. [points] = (x, y) con x creciente; [xLabel] da la etiqueta de un x.
     */
    fun lineChart(points: List<Pair<Double, Double>>, height: Float = 120f, xLabel: (Double) -> String, yLabel: (Double) -> String, color: Int = C_PRIMARY) {
        if (points.size < 2) return
        ensure(height + 22f)
        val pAxis = paint(7.5f, C_MUTED)
        val padL = 34f
        val padB = 14f
        val l = left + padL
        val t = y + 4f
        val w = contentW - padL - 6f
        val h = height - padB - 4f
        var lo = points.minOf { it.second }
        var hi = points.maxOf { it.second }
        if (hi - lo < 1e-9) {
            lo -= 1.0
            hi += 1.0
        }
        val padV = (hi - lo) * 0.1
        lo -= padV
        hi += padV
        val x0 = points.first().first
        val x1 = points.last().first.let { if (it - x0 < 1e-9) x0 + 1 else it }
        fun px(x: Double) = l + ((x - x0) / (x1 - x0)).toFloat() * w
        fun py(v: Double) = t + (1f - ((v - lo) / (hi - lo)).toFloat()) * h
        for (i in 0..3) {
            val v = lo + (hi - lo) * i / 3
            val yy = py(v)
            canvas.drawLine(l, yy, l + w, yy, pLine)
            val s = yLabel(v)
            canvas.drawText(s, l - 5f - pAxis.measureText(s), yy + 2.5f, pAxis)
        }
        for (i in 0..2) {
            val xv = x0 + (x1 - x0) * i / 2
            val s = xLabel(xv)
            val tx = (px(xv) - pAxis.measureText(s) / 2f).coerceIn(l, l + w - pAxis.measureText(s))
            canvas.drawText(s, tx, t + h + 10f, pAxis)
        }
        val path = Path()
        points.forEachIndexed { i, (x, v) -> if (i == 0) path.moveTo(px(x), py(v)) else path.lineTo(px(x), py(v)) }
        val pStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 1.4f
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawPath(path, pStroke)
        if (points.size <= 80) {
            pFill.color = color
            for ((x, v) in points) canvas.drawCircle(px(x), py(v), 1.7f, pFill)
        }
        y += height + 8f
    }

    fun finish(out: OutputStream) {
        if (pageNo == 0) newPage()
        finishPage()
        sink.finish(out)
    }
}
