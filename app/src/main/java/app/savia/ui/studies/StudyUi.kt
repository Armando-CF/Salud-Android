package app.savia.ui.studies

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.savia.Savia
import app.savia.data.StudyCategories
import app.savia.ui.components.Pill
import app.savia.ui.theme.SaviaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Color para la bandera de rango: verde en rango, ámbar fuera de rango, neutro sin rango. */
@Composable
fun flagColor(flag: RangeFlag): Color = when (flag) {
    RangeFlag.NORMAL -> SaviaTheme.colors.good
    RangeFlag.BAJO, RangeFlag.ALTO -> SaviaTheme.colors.warn
    RangeFlag.SIN_RANGO -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** "Alto ↑", "Bajo ↓", "En rango". */
fun flagLabel(flag: RangeFlag): String = when (flag) {
    RangeFlag.ALTO -> "Alto ↑"
    RangeFlag.BAJO -> "Bajo ↓"
    RangeFlag.NORMAL -> "En rango"
    RangeFlag.SIN_RANGO -> ""
}

@Composable
fun statusColor(status: String): Color = when (status) {
    "normal" -> SaviaTheme.colors.good
    "seguimiento" -> SaviaTheme.colors.warn
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Etiqueta de estado del estudio (no dibuja nada si no hay estado). */
@Composable
fun StatusPill(status: String, modifier: Modifier = Modifier) {
    val label = StudyCategories.statusLabel(status)
    if (label.isNotEmpty()) Pill(label, statusColor(status), modifier)
}

fun categoryIcon(category: String): ImageVector = when (category) {
    "lab" -> Icons.Filled.Science
    "imagen" -> Icons.Filled.ImageSearch
    "chequeo" -> Icons.Filled.MedicalServices
    "cardio" -> Icons.Filled.MonitorHeart
    "gine" -> Icons.Filled.Female
    "uro" -> Icons.Filled.WaterDrop
    "dental" -> Icons.Filled.Mood
    "vista" -> Icons.Filled.Visibility
    "oido" -> Icons.Filled.Hearing
    "piel" -> Icons.Filled.Face
    "vacuna" -> Icons.Filled.Vaccines
    else -> Icons.Filled.Description
}

/** Icono de categoría dentro de un cuadro redondeado. */
@Composable
fun CategoryBadge(category: String, modifier: Modifier = Modifier) {
    val tint = MaterialTheme.colorScheme.primary
    Box(
        modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) { Icon(categoryIcon(category), contentDescription = null, tint = tint) }
}

/**
 * Barra que muestra dónde cae [value] respecto a su rango de referencia:
 * la zona verde es el rango y el punto es el valor.
 */
@Composable
fun RangeBar(value: Double, refMin: Double?, refMax: Double?, modifier: Modifier = Modifier) {
    if (refMin == null && refMax == null) return
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val zone = SaviaTheme.colors.good.copy(alpha = 0.45f)
    val marker = flagColor(flag(value, refMin, refMax))
    val ring = MaterialTheme.colorScheme.surfaceContainerLowest
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val lo: Double
        val hi: Double
        when {
            refMin != null && refMax != null -> {
                val span = (refMax - refMin).takeIf { it > 0 } ?: 1.0
                lo = refMin - span * 0.5
                hi = refMax + span * 0.5
            }
            refMax != null -> {
                lo = 0.0
                hi = if (refMax > 0) refMax * 1.5 else refMax + 1.0
            }
            else -> {
                val m = refMin!!
                lo = if (m > 0) m * 0.5 else m - 1.0
                hi = if (m > 0) m * 2.0 else m + 1.0
            }
        }
        fun frac(v: Double): Float = ((v - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f)
        val barH = 6.dp.toPx()
        val top = (size.height - barH) / 2f
        val r = CornerRadius(barH / 2f)
        drawRoundRect(track, Offset(0f, top), Size(size.width, barH), r)
        val z0 = frac(refMin ?: lo) * size.width
        val z1 = frac(refMax ?: hi) * size.width
        if (z1 > z0) drawRoundRect(zone, Offset(z0, top), Size(z1 - z0, barH), r)
        val radius = 6.dp.toPx()
        val cx = (frac(value) * size.width).coerceIn(radius, size.width - radius)
        drawCircle(ring, radius = radius, center = Offset(cx, size.height / 2f))
        drawCircle(marker, radius = radius - 1.5.dp.toPx(), center = Offset(cx, size.height / 2f))
    }
}

/** Datos mínimos de un adjunto (los ya guardados tienen id > 0; los recién agregados, id = 0). */
data class AttachmentRef(val id: Long, val fileName: String, val displayName: String, val mime: String) {
    val isImage: Boolean get() = mime.startsWith("image/") || fileName.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "heic")
    val isPdf: Boolean get() = mime == "application/pdf" || fileName.endsWith(".pdf", ignoreCase = true)
}

/**
 * Manejo de los archivos adjuntos. Cada perfil tiene su propia carpeta (ver [Savia.attachmentsDir]):
 * filesDir/attachments para el perfil 1 y filesDir/attachments/p<N> para los demás. Todas las
 * funciones trabajan sobre el perfil ACTIVO en el momento de la llamada.
 */
object StudyFiles {
    @Suppress("UNUSED_PARAMETER")
    fun dir(context: Context): File = Savia.attachmentsDir().apply { mkdirs() }

    fun file(context: Context, fileName: String): File = File(dir(context), fileName)

    fun delete(context: Context, fileName: String) {
        runCatching { file(context, fileName).delete() }
    }

    /** Copia el documento elegido a la carpeta de adjuntos con un nombre único. Llamar fuera del hilo principal. */
    fun import(context: Context, uri: Uri): AttachmentRef? = runCatching {
        val cr = context.contentResolver
        val mime = cr.getType(uri) ?: ""
        var display = ""
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) display = c.getString(0) ?: ""
        }
        if (display.isBlank()) display = uri.lastPathSegment?.substringAfterLast('/') ?: "Adjunto"
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            ?: display.substringAfterLast('.', "").takeIf { it.length in 1..5 }
            ?: "bin"
        val fileName = "est_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.$ext"
        val target = file(context, fileName)
        val input = cr.openInputStream(uri) ?: return@runCatching null
        input.use { i -> target.outputStream().use { o -> i.copyTo(o) } }
        AttachmentRef(0, fileName, display, mime)
    }.getOrNull()

    /** Abre el adjunto con la app que el teléfono tenga para ese tipo de archivo. */
    fun open(context: Context, att: AttachmentRef) {
        val f = file(context, att.fileName)
        if (!f.exists()) {
            Toast.makeText(context, "No se encontró el archivo", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(context, "app.savia.files", f)
            val mime = att.mime.ifBlank {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
            }
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No hay una app instalada para abrir este archivo", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "No se pudo abrir el archivo", Toast.LENGTH_SHORT).show()
        }
    }

    /** Miniatura de una imagen o de la primera página de un PDF (null si no se puede). */
    fun thumbnail(context: Context, att: AttachmentRef, maxPx: Int = 320): Bitmap? = runCatching {
        val f = file(context, att.fileName)
        if (!f.exists()) return@runCatching null
        when {
            att.isImage -> {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.path, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
                BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
            }
            att.isPdf -> {
                ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (renderer.pageCount == 0) return@runCatching null
                        renderer.openPage(0).use { page ->
                            val scale = maxPx.toFloat() / maxOf(page.width, page.height)
                            val bmp = Bitmap.createBitmap(
                                (page.width * scale).toInt().coerceAtLeast(1),
                                (page.height * scale).toInt().coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888,
                            )
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bmp
                        }
                    }
                }
            }
            else -> null
        }
    }.getOrNull()
}

/** Cuadro de un adjunto: miniatura (o icono) y nombre. [onRemove] muestra una "x" para quitarlo. */
@Composable
fun AttachmentTile(
    att: AttachmentRef,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val thumb by produceState<Bitmap?>(initialValue = null, att.fileName) {
        value = withContext(Dispatchers.IO) { StudyFiles.thumbnail(context, att) }
    }
    Column(modifier.width(104.dp)) {
        Box(
            Modifier.size(104.dp).clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = thumb
            if (bmp != null) {
                Image(bmp.asImageBitmap(), contentDescription = att.displayName, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(
                    when {
                        att.isPdf -> Icons.Filled.PictureAsPdf
                        att.isImage -> Icons.Filled.Image
                        else -> Icons.Filled.Description
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp),
                )
            }
            if (onRemove != null) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.8f))
                        .clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Quitar adjunto", tint = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.size(18.dp))
                }
            }
        }
        Text(
            att.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
