package com.opensource.svga.core.drawer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.widget.ImageView
import com.opensource.svga.core.dynamic.SVGADynamicEntity
import com.opensource.svga.core.model.SVGAVideoEntity
import com.opensource.svga.core.model.SVGAVideoSpriteEntity

class SVGACanvasDrawer(
    private val videoItem: SVGAVideoEntity,
    var dynamicItem: SVGADynamicEntity? = null
) {
    val scaleInfo = SvgaScaleInfo()

    var drawableAlpha: Int = 255
    var drawableColorFilter: ColorFilter? = null

    // 复用对象池，实现单帧零对象分配（Zero-Allocation per Frame），消除高频渲染 GC 抖动
    private val frameMatrix = Matrix()
    private val shapeMatrix = Matrix()
    private val sharedPaint = Paint()
    private val sharedPath = Path()
    private val sharedClipPath = Path()
    private val textRect = Rect()
    private val sharedBgRect = RectF()
    private val matchedKeys = HashSet<String>()

    fun drawFrame(
        canvas: Canvas,
        frameIndex: Int,
        scaleType: ImageView.ScaleType = ImageView.ScaleType.FIT_CENTER,
        bounds: Rect? = null
    ) {
        if (frameIndex < 0 || frameIndex >= videoItem.frames) return
        if (drawableAlpha <= 0) return

        val hasBounds = bounds != null && bounds.width() > 0 && bounds.height() > 0
        val targetWidth = if (hasBounds) bounds!!.width().toFloat() else canvas.width.toFloat()
        val targetHeight = if (hasBounds) bounds!!.height().toFloat() else canvas.height.toFloat()
        val offsetX = if (hasBounds) bounds!!.left.toFloat() else 0f
        val offsetY = if (hasBounds) bounds!!.top.toFloat() else 0f

        scaleInfo.performScaleType(
            canvasWidth = targetWidth,
            canvasHeight = targetHeight,
            videoWidth = videoItem.videoSize.width.toFloat(),
            videoHeight = videoItem.videoSize.height.toFloat(),
            scaleType = scaleType
        )

        val needTranslateBounds = offsetX != 0f || offsetY != 0f
        if (needTranslateBounds) {
            canvas.save()
            canvas.translate(offsetX, offsetY)
        }

        // 遍历所有 sprite 绘制（复用 matchedKeys 集合）
        matchedKeys.clear()
        val sprites = videoItem.spriteList
        for (i in 0 until sprites.size) {
            drawSprite(sprites[i], canvas, frameIndex, matchedKeys)
        }

        // 绘制未被 Sprite 消费的全局 Overlay 浮层文本与降级文本（确保任何 key 均能 100% 可见呈现）
        drawOverlayAndFallbackTexts(canvas, matchedKeys, targetWidth, targetHeight)

        if (needTranslateBounds) {
            canvas.restore()
        }
    }

    private fun shareFrameMatrix(transform: Matrix): Matrix {
        frameMatrix.reset()
        frameMatrix.postScale(scaleInfo.scaleFx, scaleInfo.scaleFy)
        frameMatrix.postTranslate(scaleInfo.tranFx, scaleInfo.tranFy)
        frameMatrix.preConcat(transform)
        return frameMatrix
    }

    private fun drawSprite(
        sprite: SVGAVideoSpriteEntity,
        canvas: Canvas,
        frameIndex: Int,
        matchedKeys: MutableSet<String>
    ) {
        if (frameIndex >= sprite.frames.size) return
        val frameEntity = sprite.frames[frameIndex]

        if (frameEntity.alpha <= 0.0f) return

        drawImage(sprite, canvas, frameIndex, matchedKeys)
        drawShapes(sprite, canvas, frameIndex)
    }

    private fun drawImage(
        sprite: SVGAVideoSpriteEntity,
        canvas: Canvas,
        frameIndex: Int,
        matchedKeys: MutableSet<String>
    ) {
        val imageKey = sprite.imageKey ?: return
        if (dynamicItem?.dynamicHidden?.get(imageKey) == true) return

        val bitmapKey = if (imageKey.endsWith(".matte")) {
            imageKey.substringBefore(".matte")
        } else {
            imageKey
        }

        // 运行时覆盖：优先取 Dynamic 替换图片，未命中再取底层共享模板图片
        val drawingBitmap = dynamicItem?.dynamicImage?.get(bitmapKey)
            ?: videoItem.imageMap[bitmapKey]
            ?: return

        if (drawingBitmap.isRecycled) return

        val frameEntity = sprite.frames[frameIndex]
        val matrix = shareFrameMatrix(frameEntity.transform)

        val effectiveAlpha = frameEntity.alpha * (drawableAlpha / 255f)
        sharedPaint.reset()
        sharedPaint.isAntiAlias = videoItem.antiAlias
        sharedPaint.isFilterBitmap = true
        sharedPaint.isDither = true
        sharedPaint.alpha = (effectiveAlpha * 255).toInt().coerceIn(0, 255)
        sharedPaint.colorFilter = drawableColorFilter

        val layoutWidth = frameEntity.layout.width.toFloat()
        val layoutHeight = frameEntity.layout.height.toFloat()

        if (layoutWidth > 0f && layoutHeight > 0f && drawingBitmap.width > 0 && drawingBitmap.height > 0) {
            matrix.preScale(layoutWidth / drawingBitmap.width, layoutHeight / drawingBitmap.height)
        }

        if (frameEntity.maskPath != null) {
            canvas.save()
            sharedClipPath.reset()
            frameEntity.maskPath?.buildPath(sharedClipPath)
            sharedClipPath.transform(matrix)
            canvas.clipPath(sharedClipPath)
            canvas.drawBitmap(drawingBitmap, matrix, sharedPaint)
            canvas.restore()
        } else {
            canvas.drawBitmap(drawingBitmap, matrix, sharedPaint)
        }

        // 绘制图层绑定的动态文本（如果有注入）
        if (dynamicItem?.dynamicText?.containsKey(imageKey) == true) {
            matchedKeys.add(imageKey)
            drawDynamicText(canvas, drawingBitmap, imageKey, matrix, effectiveAlpha)
        }
    }

    private val overlayBgPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = android.graphics.Color.parseColor("#B3000000") // 70% 黑色胶囊底色
    }

    private fun drawDynamicText(
        canvas: Canvas,
        drawingBitmap: Bitmap,
        imageKey: String,
        matrix: Matrix,
        alpha: Float
    ) {
        val dynamic = dynamicItem ?: return
        val text = dynamic.dynamicText[imageKey] ?: return
        val textPaint = dynamic.dynamicTextPaint[imageKey] ?: SVGADynamicEntity.createDefaultTextPaint()

        textRect.set(0, 0, drawingBitmap.width, drawingBitmap.height)
        val fontMetrics = textPaint.fontMetrics
        val baseline = textRect.centerY() - (fontMetrics.top + fontMetrics.bottom) / 2

        canvas.save()
        canvas.concat(matrix)

        // 绘制带半透明背景的微型药丸底框，提升任何底色下的文字辨识度
        val textWidth = textPaint.measureText(text)
        val textHeight = fontMetrics.bottom - fontMetrics.top
        val paddingX = 14f
        val paddingY = 6f
        sharedBgRect.set(
            textRect.centerX() - textWidth / 2f - paddingX,
            textRect.centerY() - textHeight / 2f - paddingY,
            textRect.centerX() + textWidth / 2f + paddingX,
            textRect.centerY() + textHeight / 2f + paddingY
        )
        val oldBgAlpha = overlayBgPaint.alpha
        overlayBgPaint.alpha = (alpha * oldBgAlpha).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(sharedBgRect, sharedBgRect.height() / 2f, sharedBgRect.height() / 2f, overlayBgPaint)
        overlayBgPaint.alpha = oldBgAlpha

        val oldAlpha = textPaint.alpha
        textPaint.alpha = (alpha * oldAlpha).toInt().coerceIn(0, 255)
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(text, textRect.centerX().toFloat(), baseline, textPaint)
        textPaint.alpha = oldAlpha
        canvas.restore()
    }

    private fun drawOverlayAndFallbackTexts(
        canvas: Canvas,
        matchedKeys: Set<String>,
        targetWidth: Float = canvas.width.toFloat(),
        targetHeight: Float = canvas.height.toFloat()
    ) {
        val dynamic = dynamicItem ?: return
        if (dynamic.dynamicText.isEmpty()) return

        val canvasW = targetWidth
        val canvasH = targetHeight
        if (canvasW <= 0f || canvasH <= 0f) return

        val drawableAlphaScale = drawableAlpha / 255f
        for ((key, text) in dynamic.dynamicText) {
            if (!key.startsWith("__svga_overlay_") && matchedKeys.contains(key)) continue
            val paint = dynamic.dynamicTextPaint[key] ?: SVGADynamicEntity.createDefaultTextPaint()

            val yAnchor = when (key) {
                SVGADynamicEntity.KEY_OVERLAY_TOP -> canvasH * 0.18f
                SVGADynamicEntity.KEY_OVERLAY_CENTER -> canvasH * 0.5f
                else -> canvasH * 0.82f // 默认置于画布底部靠上位置
            }

            val textWidth = paint.measureText(text)
            val fontMetrics = paint.fontMetrics
            val textHeight = fontMetrics.bottom - fontMetrics.top
            val centerX = canvasW / 2f

            val paddingX = 20f
            val paddingY = 8f
            sharedBgRect.set(
                centerX - textWidth / 2f - paddingX,
                yAnchor - textHeight / 2f - paddingY,
                centerX + textWidth / 2f + paddingX,
                yAnchor + textHeight / 2f + paddingY
            )
            val oldBgAlpha = overlayBgPaint.alpha
            overlayBgPaint.alpha = (drawableAlphaScale * oldBgAlpha).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(sharedBgRect, sharedBgRect.height() / 2f, sharedBgRect.height() / 2f, overlayBgPaint)
            overlayBgPaint.alpha = oldBgAlpha

            val oldPaintAlpha = paint.alpha
            paint.alpha = (drawableAlphaScale * oldPaintAlpha).toInt().coerceIn(0, 255)
            paint.textAlign = Paint.Align.CENTER
            val baseline = yAnchor - (fontMetrics.top + fontMetrics.bottom) / 2f
            canvas.drawText(text, centerX, baseline, paint)
            paint.alpha = oldPaintAlpha
        }
    }

    private fun drawShapes(sprite: SVGAVideoSpriteEntity, canvas: Canvas, frameIndex: Int) {
        val frameEntity = sprite.frames[frameIndex]
        if (frameEntity.shapes.isEmpty()) return

        val matrix = shareFrameMatrix(frameEntity.transform)
        val effectiveAlpha = frameEntity.alpha * (drawableAlpha / 255f)
        val shapes = frameEntity.shapes

        for (i in 0 until shapes.size) {
            val shape = shapes[i]
            shape.buildPath()
            val shapePath = shape.shapePath ?: continue

            sharedPath.reset()
            sharedPath.set(shapePath)

            shapeMatrix.reset()
            shape.transform?.let { shapeMatrix.postConcat(it) }
            shapeMatrix.postConcat(matrix)
            sharedPath.transform(shapeMatrix)

            val styles = shape.styles

            // 1. 填充样式
            if (styles != null && styles.fill != 0) {
                sharedPaint.reset()
                sharedPaint.isAntiAlias = videoItem.antiAlias
                sharedPaint.style = Paint.Style.FILL
                sharedPaint.color = styles.fill
                sharedPaint.alpha = (effectiveAlpha * (styles.fill ushr 24)).toInt().coerceIn(0, 255)
                sharedPaint.colorFilter = drawableColorFilter

                if (frameEntity.maskPath != null) {
                    canvas.save()
                    sharedClipPath.reset()
                    frameEntity.maskPath?.buildPath(sharedClipPath)
                    sharedClipPath.transform(matrix)
                    canvas.clipPath(sharedClipPath)
                    canvas.drawPath(sharedPath, sharedPaint)
                    canvas.restore()
                } else {
                    canvas.drawPath(sharedPath, sharedPaint)
                }
            }

            // 2. 描边样式
            if (styles != null && styles.strokeWidth > 0f) {
                sharedPaint.reset()
                sharedPaint.isAntiAlias = videoItem.antiAlias
                sharedPaint.style = Paint.Style.STROKE
                sharedPaint.color = styles.stroke
                sharedPaint.alpha = (effectiveAlpha * (styles.stroke ushr 24)).toInt().coerceIn(0, 255)
                sharedPaint.colorFilter = drawableColorFilter

                val scale = Math.hypot(scaleInfo.scaleFx.toDouble(), scaleInfo.scaleFy.toDouble()).toFloat()
                sharedPaint.strokeWidth = styles.strokeWidth * (if (scale > 0f) scale else 1f)

                when (styles.lineCap) {
                    "round" -> sharedPaint.strokeCap = Paint.Cap.ROUND
                    "square" -> sharedPaint.strokeCap = Paint.Cap.SQUARE
                    else -> sharedPaint.strokeCap = Paint.Cap.BUTT
                }

                when (styles.lineJoin) {
                    "round" -> sharedPaint.strokeJoin = Paint.Join.ROUND
                    "bevel" -> sharedPaint.strokeJoin = Paint.Join.BEVEL
                    else -> sharedPaint.strokeJoin = Paint.Join.MITER
                }

                if (styles.miterLimit > 0) {
                    sharedPaint.strokeMiter = styles.miterLimit.toFloat()
                }

                if (styles.lineDash.size >= 2 && (styles.lineDash[0] > 0f || styles.lineDash[1] > 0f)) {
                    sharedPaint.pathEffect = DashPathEffect(
                        floatArrayOf(
                            styles.lineDash[0] * scale,
                            styles.lineDash[1] * scale
                        ),
                        if (styles.lineDash.size > 2) styles.lineDash[2] * scale else 0f
                    )
                }

                if (frameEntity.maskPath != null) {
                    canvas.save()
                    sharedClipPath.reset()
                    frameEntity.maskPath?.buildPath(sharedClipPath)
                    sharedClipPath.transform(matrix)
                    canvas.clipPath(sharedClipPath)
                    canvas.drawPath(sharedPath, sharedPaint)
                    canvas.restore()
                } else {
                    canvas.drawPath(sharedPath, sharedPaint)
                }
            }
        }
    }
}
