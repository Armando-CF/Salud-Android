package app.savia.ui.atlas

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.hypot

/** Tipo de forma dentro de una vista del atlas. */
enum class ShapeKind { REGION, SKIN, BONE, ORGAN, DECO }

/** Una forma: uno o más polígonos cerrados (o líneas abiertas si [open]). */
class AtlasShape(
    val id: String?,
    val kind: ShapeKind,
    val polys: List<FloatArray>, // cada polígono: x0, y0, x1, y1, ...
    val open: Boolean,
    val color: Long?,            // ARGB para órganos
    val alpha: Float,
    val dashed: Boolean,
    val inkFill: Boolean,
) {
    val path: Path by lazy {
        Path().apply {
            for (p in polys) {
                if (p.size < 4) continue
                moveTo(p[0], p[1])
                var i = 2
                while (i < p.size) { lineTo(p[i], p[i + 1]); i += 2 }
                if (!open) close()
            }
        }
    }

    val bounds: Rect by lazy {
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (p in polys) {
            var i = 0
            while (i < p.size) {
                if (p[i] < l) l = p[i]; if (p[i] > r) r = p[i]
                if (p[i + 1] < t) t = p[i + 1]; if (p[i + 1] > b) b = p[i + 1]
                i += 2
            }
        }
        Rect(l, t, r, b)
    }

    /** ¿El punto cae dentro de algún polígono? (regla par-impar por polígono) */
    fun contains(x: Float, y: Float): Boolean {
        if (open || !bounds.contains(Offset(x, y))) return false
        for (p in polys) {
            var inside = false
            val n = p.size / 2
            var j = n - 1
            for (i in 0 until n) {
                val xi = p[i * 2]; val yi = p[i * 2 + 1]
                val xj = p[j * 2]; val yj = p[j * 2 + 1]
                if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
                j = i
            }
            if (inside) return true
        }
        return false
    }

    /** Distancia mínima del punto al contorno (para tolerancia al tocar huesos delgados). */
    fun distanceTo(x: Float, y: Float): Float {
        var best = Float.MAX_VALUE
        for (p in polys) {
            val n = p.size / 2
            for (i in 0 until n) {
                val j = (i + 1) % n
                val ax = p[i * 2]; val ay = p[i * 2 + 1]
                val bx = p[j * 2]; val by = p[j * 2 + 1]
                val dx = bx - ax; val dy = by - ay
                val len2 = dx * dx + dy * dy
                val t = if (len2 <= 0f) 0f else (((x - ax) * dx + (y - ay) * dy) / len2).coerceIn(0f, 1f)
                val d = hypot(x - (ax + t * dx), y - (ay + t * dy))
                if (d < best) best = d
            }
        }
        return best
    }
}

class AtlasText(val x: Float, val y: Float, val text: String, val size: Float)

class AtlasView(
    val id: String,
    val group: String,
    /** "F", "M" o null si es igual para todos. */
    val sex: String?,
    val title: String,
    val box: Rect,
    val shapes: List<AtlasShape>,
    val texts: List<AtlasText>,
) {
    /** Forma tocable en el punto (la de más arriba), con tolerancia para formas pequeñas. */
    fun hit(x: Float, y: Float, tolerance: Float): AtlasShape? {
        for (i in shapes.indices.reversed()) {
            val s = shapes[i]
            if (s.id != null && s.contains(x, y)) return s
        }
        var best: AtlasShape? = null
        var bestD = tolerance
        for (s in shapes) {
            if (s.id == null) continue
            val b = s.bounds
            if (x < b.left - tolerance || x > b.right + tolerance || y < b.top - tolerance || y > b.bottom + tolerance) continue
            val d = s.distanceTo(x, y)
            if (d < bestD) { bestD = d; best = s }
        }
        return best
    }
}

/** Ficha educativa de una zona, hueso u órgano. */
class AtlasInfo(
    val name: String,
    val description: String,
    val bones: List<String>,
    val muscles: List<String>,
    val organs: List<String>,
    /** Cuándo conviene consultar (orientativo). */
    val tip: String?,
    /** Grupo de vista relacionado ("hand", "foot", "organs", "skeleton"...). */
    val link: String?,
)

class Atlas(val views: List<AtlasView>, val info: Map<String, AtlasInfo>, val note: String) {
    /** Grupos en el orden en que se muestran. */
    val groups: List<Pair<String, String>> = listOf(
        "front" to "Frente", "back" to "Espalda", "abdomen" to "Abdomen", "organs" to "Órganos",
        "skeleton" to "Esqueleto", "hand" to "Mano", "foot" to "Pie",
    )

    fun view(group: String, sex: String): AtlasView? =
        views.firstOrNull { it.group == group && it.sex == sex } ?: views.firstOrNull { it.group == group }

    fun hasSexVariants(group: String): Boolean = views.count { it.group == group } > 1

    /** Nota de orientación izquierda/derecha para cada grupo. */
    fun orientationNote(group: String): String = when (group) {
        "back" -> "Vista de espalda: su lado derecho queda a tu derecha."
        "hand" -> "Mano derecha vista por el dorso (los huesos son iguales en la izquierda, en espejo)."
        "foot" -> "Pie derecho visto desde arriba (el izquierdo es igual, en espejo)."
        else -> "Vista de frente, como en un espejo invertido: su lado derecho queda a tu izquierda."
    }
}

object AtlasRepo {
    @Volatile private var cached: Atlas? = null

    fun load(context: Context): Atlas {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val json = context.assets.open("atlas.json").bufferedReader().use { it.readText() }
            return parse(json).also { cached = it }
        }
    }

    fun parse(json: String): Atlas {
        val root = JSONObject(json)
        val views = ArrayList<AtlasView>()
        val vs = root.getJSONArray("views")
        for (i in 0 until vs.length()) {
            val v = vs.getJSONObject(i)
            val box = v.getJSONArray("box")
            val shapes = ArrayList<AtlasShape>()
            val ss = v.getJSONArray("shapes")
            for (j in 0 until ss.length()) {
                val s = ss.getJSONObject(j)
                val d = s.getString("d")
                val kind = when (s.getString("kind")) {
                    "region" -> ShapeKind.REGION
                    "skin" -> ShapeKind.SKIN
                    "bone" -> ShapeKind.BONE
                    "organ" -> ShapeKind.ORGAN
                    else -> ShapeKind.DECO
                }
                shapes += AtlasShape(
                    id = s.optString("id").takeIf { it.isNotEmpty() },
                    kind = kind,
                    polys = parsePolys(d),
                    open = !d.trimEnd().endsWith("Z"),
                    color = s.optString("color").takeIf { it.startsWith("#") }?.let { 0xFF000000 or it.substring(1).toLong(16) },
                    alpha = s.optDouble("alpha", 1.0).toFloat(),
                    dashed = s.optBoolean("dashed", false),
                    inkFill = s.optString("fill") == "ink",
                )
            }
            val texts = ArrayList<AtlasText>()
            val ts = v.optJSONArray("texts") ?: JSONArray()
            for (j in 0 until ts.length()) {
                val t = ts.getJSONObject(j)
                texts += AtlasText(t.getDouble("x").toFloat(), t.getDouble("y").toFloat(), t.getString("t"), t.getDouble("size").toFloat())
            }
            views += AtlasView(
                id = v.getString("id"),
                group = v.getString("group"),
                sex = if (v.isNull("sex")) null else v.getString("sex"),
                title = v.getString("title"),
                box = Rect(box.getDouble(0).toFloat(), box.getDouble(1).toFloat(), (box.getDouble(0) + box.getDouble(2)).toFloat(), (box.getDouble(1) + box.getDouble(3)).toFloat()),
                shapes = shapes,
                texts = texts,
            )
        }
        val info = HashMap<String, AtlasInfo>()
        val io = root.getJSONObject("info")
        for (key in io.keys()) {
            val o = io.getJSONObject(key)
            fun list(k: String): List<String> = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
            info[key] = AtlasInfo(
                name = o.getString("n"),
                description = o.optString("d"),
                bones = list("b"), muscles = list("m"), organs = list("o"),
                tip = o.optString("t").takeIf { it.isNotEmpty() },
                link = o.optString("link").takeIf { it.isNotEmpty() },
            )
        }
        return Atlas(views, info, root.optString("note"))
    }

    /** "M x yL x y...Z M..." → lista de polígonos. */
    private fun parsePolys(d: String): List<FloatArray> {
        val out = ArrayList<FloatArray>()
        for (sub in d.split('M')) {
            val body = sub.trim().removeSuffix("Z")
            if (body.isEmpty()) continue
            val pts = body.split('L')
            val arr = FloatArray(pts.size * 2)
            var k = 0
            for (pt in pts) {
                val sp = pt.trim().indexOf(' ')
                val t = pt.trim()
                arr[k++] = t.substring(0, sp).toFloat()
                arr[k++] = t.substring(sp + 1).toFloat()
            }
            out += arr
        }
        return out
    }
}
