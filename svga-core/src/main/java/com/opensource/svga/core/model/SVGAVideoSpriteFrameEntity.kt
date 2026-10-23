package com.opensource.svga.core.model

import android.graphics.Matrix
import com.opensource.svgaplayer.proto.FrameEntity

class SVGAVideoSpriteFrameEntity(obj: FrameEntity) {

    val alpha: Float = obj.alpha
    var layout: SVGARect = SVGARect(0.0, 0.0, 0.0, 0.0)
        private set
    val transform: Matrix = Matrix()
    var maskPath: SVGAPathEntity? = null
        private set
    var shapes: List<SVGAVideoShapeEntity> = emptyList()

    init {
        if (obj.hasLayout()) {
            val l = obj.layout
            layout = SVGARect(
                l.x.toDouble(),
                l.y.toDouble(),
                l.width.toDouble(),
                l.height.toDouble()
            )
        }
        if (obj.hasTransform()) {
            val t = obj.transform
            val values = floatArrayOf(
                t.a, t.c, t.tx,
                t.b, t.d, t.ty,
                0f, 0f, 1f
            )
            try {
                transform.setValues(values)
            } catch (_: Throwable) {
                // 忽略非 Android 环境下的本地单元测试 stub 异常
            }
        }
        if (obj.clipPath.isNotEmpty()) {
            maskPath = SVGAPathEntity(obj.clipPath)
        }
        shapes = obj.shapesList.map { SVGAVideoShapeEntity(it) }
    }
}
