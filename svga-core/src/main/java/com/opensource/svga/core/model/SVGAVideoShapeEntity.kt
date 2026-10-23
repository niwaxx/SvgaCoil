package com.opensource.svga.core.model

import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import com.opensource.svgaplayer.proto.ShapeEntity

class SVGAVideoShapeEntity(obj: ShapeEntity) {

    enum class Type {
        SHAPE,
        RECT,
        ELLIPSE,
        KEEP
    }

    class Styles {
        var fill: Int = 0x00000000
        var stroke: Int = 0x00000000
        var strokeWidth: Float = 0.0f
        var lineCap: String = "butt"
        var lineJoin: String = "miter"
        var miterLimit: Int = 0
        var lineDash: FloatArray = FloatArray(0)
    }

    var type: Type = Type.SHAPE
        private set

    var args: Map<String, Any>? = null
        private set

    var styles: Styles? = null
        private set

    var transform: Matrix? = null
        private set

    var shapePath: Path? = null
        private set

    val isKeep: Boolean
        get() = type == Type.KEEP

    init {
        parseType(obj)
        parseArgs(obj)
        parseStyles(obj)
        parseTransform(obj)
    }

    private fun parseType(obj: ShapeEntity) {
        type = when (obj.type) {
            ShapeEntity.ShapeType.SHAPE -> Type.SHAPE
            ShapeEntity.ShapeType.RECT -> Type.RECT
            ShapeEntity.ShapeType.ELLIPSE -> Type.ELLIPSE
            ShapeEntity.ShapeType.KEEP -> Type.KEEP
            else -> Type.SHAPE
        }
    }

    private fun parseArgs(obj: ShapeEntity) {
        val map = HashMap<String, Any>()
        if (obj.hasShape()) {
            map["d"] = obj.shape.d
        }
        if (obj.hasEllipse()) {
            map["x"] = obj.ellipse.x
            map["y"] = obj.ellipse.y
            map["radiusX"] = obj.ellipse.radiusX
            map["radiusY"] = obj.ellipse.radiusY
        }
        if (obj.hasRect()) {
            map["x"] = obj.rect.x
            map["y"] = obj.rect.y
            map["width"] = obj.rect.width
            map["height"] = obj.rect.height
            map["cornerRadius"] = obj.rect.cornerRadius
        }
        this.args = map
    }

    private fun parseStyles(obj: ShapeEntity) {
        if (!obj.hasStyles()) return
        val item = obj.styles
        val styles = Styles()

        if (item.hasFill()) {
            val fill = item.fill
            val mul = if (fill.r <= 1f && fill.g <= 1f && fill.b <= 1f) 255f else 1f
            val alphaMul = if (fill.a <= 1f) 255f else 1f
            styles.fill = Color.argb(
                (fill.a * alphaMul).toInt().coerceIn(0, 255),
                (fill.r * mul).toInt().coerceIn(0, 255),
                (fill.g * mul).toInt().coerceIn(0, 255),
                (fill.b * mul).toInt().coerceIn(0, 255)
            )
        }

        if (item.hasStroke()) {
            val stroke = item.stroke
            val mul = if (stroke.r <= 1f && stroke.g <= 1f && stroke.b <= 1f) 255f else 1f
            val alphaMul = if (stroke.a <= 1f) 255f else 1f
            styles.stroke = Color.argb(
                (stroke.a * alphaMul).toInt().coerceIn(0, 255),
                (stroke.r * mul).toInt().coerceIn(0, 255),
                (stroke.g * mul).toInt().coerceIn(0, 255),
                (stroke.b * mul).toInt().coerceIn(0, 255)
            )
        }

        styles.strokeWidth = item.strokeWidth
        when (item.lineCap) {
            ShapeEntity.ShapeStyle.LineCap.LineCap_BUTT -> styles.lineCap = "butt"
            ShapeEntity.ShapeStyle.LineCap.LineCap_ROUND -> styles.lineCap = "round"
            ShapeEntity.ShapeStyle.LineCap.LineCap_SQUARE -> styles.lineCap = "square"
            else -> styles.lineCap = "butt"
        }
        when (item.lineJoin) {
            ShapeEntity.ShapeStyle.LineJoin.LineJoin_BEVEL -> styles.lineJoin = "bevel"
            ShapeEntity.ShapeStyle.LineJoin.LineJoin_MITER -> styles.lineJoin = "miter"
            ShapeEntity.ShapeStyle.LineJoin.LineJoin_ROUND -> styles.lineJoin = "round"
            else -> styles.lineJoin = "miter"
        }
        styles.miterLimit = item.miterLimit.toInt()
        val dash = FloatArray(3)
        dash[0] = item.lineDashI
        dash[1] = item.lineDashII
        dash[2] = item.lineDashIII
        styles.lineDash = dash
        this.styles = styles
    }

    private fun parseTransform(obj: ShapeEntity) {
        if (!obj.hasTransform()) return
        val t = obj.transform
        val matrix = Matrix()
        val values = floatArrayOf(
            t.a, t.c, t.tx,
            t.b, t.d, t.ty,
            0f, 0f, 1f
        )
        try {
            matrix.setValues(values)
        } catch (_: Throwable) {
            // 忽略非 Android 环境下的本地单元测试 stub 异常
        }
        this.transform = matrix
    }

    fun buildPath() {
        if (this.shapePath != null) return
        val path = Path()
        when (type) {
            Type.SHAPE -> {
                (args?.get("d") as? String)?.let { d ->
                    SVGAPathEntity(d).buildPath(path)
                }
            }
            Type.ELLIPSE -> {
                val x = (args?.get("x") as? Number)?.toFloat() ?: return
                val y = (args?.get("y") as? Number)?.toFloat() ?: return
                val rx = (args?.get("radiusX") as? Number)?.toFloat() ?: return
                val ry = (args?.get("radiusY") as? Number)?.toFloat() ?: return
                path.addOval(RectF(x - rx, y - ry, x + rx, y + ry), Path.Direction.CW)
            }
            Type.RECT -> {
                val x = (args?.get("x") as? Number)?.toFloat() ?: return
                val y = (args?.get("y") as? Number)?.toFloat() ?: return
                val width = (args?.get("width") as? Number)?.toFloat() ?: return
                val height = (args?.get("height") as? Number)?.toFloat() ?: return
                val cornerRadius = (args?.get("cornerRadius") as? Number)?.toFloat() ?: 0f
                path.addRoundRect(
                    RectF(x, y, x + width, y + height),
                    cornerRadius,
                    cornerRadius,
                    Path.Direction.CW
                )
            }
            Type.KEEP -> {}
        }
        this.shapePath = path
    }
}
