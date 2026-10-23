package com.opensource.svga.core.model

import android.graphics.Path
import java.util.StringTokenizer

private val VALID_METHODS: Set<String> = setOf(
    "M", "L", "H", "V", "C", "S", "Q", "R", "A", "Z",
    "m", "l", "h", "v", "c", "s", "q", "r", "a", "z"
)

class SVGAPathEntity(originValue: String) {

    private val replacedValue: String = if (originValue.contains(",")) {
        originValue.replace(",", " ")
    } else {
        originValue
    }

    private var cachedPath: Path? = null

    private class PathCursor {
        var currentX: Float = 0f
        var currentY: Float = 0f
        var lastControlX: Float = 0f
        var lastControlY: Float = 0f
    }

    fun buildPath(toPath: Path) {
        cachedPath?.let {
            toPath.set(it)
            return
        }
        val path = Path()
        val cursor = PathCursor()
        val segments = StringTokenizer(this.replacedValue, "MLHVCSQRAZmlhvcsqraz", true)
        var currentMethod = ""
        while (segments.hasMoreTokens()) {
            val segment = segments.nextToken()
            if (segment.isEmpty()) continue
            if (VALID_METHODS.contains(segment)) {
                currentMethod = segment
                if (currentMethod.equals("Z", ignoreCase = true)) {
                    operate(path, cursor, currentMethod, StringTokenizer("", ""))
                }
            } else {
                operate(path, cursor, currentMethod, StringTokenizer(segment, " "))
            }
        }
        this.cachedPath = path
        toPath.set(path)
    }

    private fun operate(finalPath: Path, cursor: PathCursor, method: String, args: StringTokenizer) {
        var x0 = 0.0f
        var y0 = 0.0f
        var x1 = 0.0f
        var y1 = 0.0f
        var x2 = 0.0f
        var y2 = 0.0f
        try {
            var index = 0
            while (args.hasMoreTokens()) {
                val s = args.nextToken()
                if (s.isEmpty()) continue
                when (index) {
                    0 -> x0 = s.toFloat()
                    1 -> y0 = s.toFloat()
                    2 -> x1 = s.toFloat()
                    3 -> y1 = s.toFloat()
                    4 -> x2 = s.toFloat()
                    5 -> y2 = s.toFloat()
                }
                index++
            }
        } catch (_: Exception) {}

        when (method) {
            "M" -> {
                finalPath.moveTo(x0, y0)
                cursor.currentX = x0
                cursor.currentY = y0
                cursor.lastControlX = x0
                cursor.lastControlY = y0
            }
            "m" -> {
                finalPath.rMoveTo(x0, y0)
                cursor.currentX += x0
                cursor.currentY += y0
                cursor.lastControlX = cursor.currentX
                cursor.lastControlY = cursor.currentY
            }
            "L" -> {
                finalPath.lineTo(x0, y0)
                cursor.currentX = x0
                cursor.currentY = y0
                cursor.lastControlX = x0
                cursor.lastControlY = y0
            }
            "l" -> {
                finalPath.rLineTo(x0, y0)
                cursor.currentX += x0
                cursor.currentY += y0
                cursor.lastControlX = cursor.currentX
                cursor.lastControlY = cursor.currentY
            }
            "C" -> {
                finalPath.cubicTo(x0, y0, x1, y1, x2, y2)
                cursor.lastControlX = x1
                cursor.lastControlY = y1
                cursor.currentX = x2
                cursor.currentY = y2
            }
            "c" -> {
                finalPath.rCubicTo(x0, y0, x1, y1, x2, y2)
                cursor.lastControlX = cursor.currentX + x1
                cursor.lastControlY = cursor.currentY + y1
                cursor.currentX += x2
                cursor.currentY += y2
            }
            "S" -> {
                val cx1 = 2 * cursor.currentX - cursor.lastControlX
                val cy1 = 2 * cursor.currentY - cursor.lastControlY
                finalPath.cubicTo(cx1, cy1, x0, y0, x1, y1)
                cursor.lastControlX = x0
                cursor.lastControlY = y0
                cursor.currentX = x1
                cursor.currentY = y1
            }
            "s" -> {
                val dx1 = cursor.currentX - cursor.lastControlX
                val dy1 = cursor.currentY - cursor.lastControlY
                finalPath.rCubicTo(dx1, dy1, x0, y0, x1, y1)
                cursor.lastControlX = cursor.currentX + x0
                cursor.lastControlY = cursor.currentY + y0
                cursor.currentX += x1
                cursor.currentY += y1
            }
            "Q" -> {
                finalPath.quadTo(x0, y0, x1, y1)
                cursor.lastControlX = x0
                cursor.lastControlY = y0
                cursor.currentX = x1
                cursor.currentY = y1
            }
            "q" -> {
                finalPath.rQuadTo(x0, y0, x1, y1)
                cursor.lastControlX = cursor.currentX + x0
                cursor.lastControlY = cursor.currentY + y0
                cursor.currentX += x1
                cursor.currentY += y1
            }
            "H" -> {
                finalPath.lineTo(x0, cursor.currentY)
                cursor.currentX = x0
                cursor.lastControlX = cursor.currentX
                cursor.lastControlY = cursor.currentY
            }
            "h" -> {
                finalPath.rLineTo(x0, 0f)
                cursor.currentX += x0
                cursor.lastControlX = cursor.currentX
                cursor.lastControlY = cursor.currentY
            }
            "V" -> {
                finalPath.lineTo(cursor.currentX, x0)
                cursor.currentY = x0
                cursor.lastControlX = cursor.currentX
                cursor.lastControlY = cursor.currentY
            }
            "v" -> {
                finalPath.rLineTo(0f, x0)
                cursor.currentY += x0
                cursor.lastControlX = cursor.currentX
                cursor.lastControlY = cursor.currentY
            }
            "Z", "z" -> {
                finalPath.close()
                cursor.lastControlX = cursor.currentX
                cursor.lastControlY = cursor.currentY
            }
        }
    }
}
