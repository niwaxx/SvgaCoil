package com.opensource.svga.core.dynamic

import android.graphics.Bitmap
import android.text.TextPaint

/**
 * SVGA 动态实体上下文（包含动态文字、动态替换头像等）。
 * 每次请求/每个视图独立持有，不污染底层共享的 SVGAVideoEntity 模板。
 */
class SVGADynamicEntity {

    val dynamicImage: MutableMap<String, Bitmap> = HashMap()
    val dynamicText: MutableMap<String, String> = HashMap()
    val dynamicTextPaint: MutableMap<String, TextPaint> = HashMap()
    val dynamicHidden: MutableMap<String, Boolean> = HashMap()

    var isTextDirty: Boolean = true

    companion object {
        const val KEY_OVERLAY_BOTTOM = "__svga_overlay_bottom__"
        const val KEY_OVERLAY_CENTER = "__svga_overlay_center__"
        const val KEY_OVERLAY_TOP = "__svga_overlay_top__"

        /**
         * 创建高可视度的默认动态文字画笔（金色粗体带黑影，确保在任何动效/背景均清晰）
         */
        fun createDefaultTextPaint(): TextPaint {
            return TextPaint().apply {
                isAntiAlias = true
                isFakeBoldText = true
                color = android.graphics.Color.parseColor("#FFD700") // 耀眼金
                textSize = 36f
                setShadowLayer(6f, 0f, 2f, android.graphics.Color.parseColor("#80000000"))
            }
        }
    }

    /**
     * 计算当前动态配置的特征指纹。
     * 用于精确感知动态内容变化，辅助 Coil MemoryCache 判定是否需要刷新视图。
     */
    fun computeSignature(): String {
        val sb = StringBuilder()
        dynamicText.entries.sortedBy { it.key }.forEach { (k, v) ->
            sb.append("T:$k=$v;")
        }
        dynamicHidden.entries.sortedBy { it.key }.forEach { (k, v) ->
            sb.append("H:$k=$v;")
        }
        dynamicImage.entries.sortedBy { it.key }.forEach { (k, v) ->
            sb.append("I:$k=${System.identityHashCode(v)};")
        }
        return sb.toString().hashCode().toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SVGADynamicEntity) return false
        return computeSignature() == other.computeSignature()
    }

    override fun hashCode(): Int {
        return computeSignature().hashCode()
    }

    /**
     * 动态替换某个图层的 Bitmap（例如送礼人头像）
     */
    fun setDynamicImage(bitmap: Bitmap, forKey: String): SVGADynamicEntity {
        dynamicImage[forKey] = bitmap
        return this
    }

    /**
     * 动态替换某个图层的文字（例如等级标、昵称）
     */
    fun setDynamicText(
        text: String,
        forKey: String,
        textPaint: TextPaint? = null
    ): SVGADynamicEntity {
        dynamicText[forKey] = text
        dynamicTextPaint[forKey] = textPaint ?: createDefaultTextPaint()
        isTextDirty = true
        return this
    }

    /**
     * 设置全局浮动文本（无论动效内部是否有预留 key，均可在顶部/居中/底部完美展示）
     */
    fun setOverlayText(
        text: String,
        position: DynamicOverlayPosition = DynamicOverlayPosition.BOTTOM,
        textPaint: TextPaint? = null
    ): SVGADynamicEntity {
        val key = when (position) {
            DynamicOverlayPosition.BOTTOM -> KEY_OVERLAY_BOTTOM
            DynamicOverlayPosition.CENTER -> KEY_OVERLAY_CENTER
            DynamicOverlayPosition.TOP -> KEY_OVERLAY_TOP
        }
        return setDynamicText(text, key, textPaint)
    }

    /**
     * 动态隐藏或显示指定图层
     */
    fun setDynamicHidden(hidden: Boolean, forKey: String): SVGADynamicEntity {
        dynamicHidden[forKey] = hidden
        return this
    }

    /**
     * 清空动态配置
     */
    fun clear() {
        dynamicImage.clear()
        dynamicText.clear()
        dynamicTextPaint.clear()
        dynamicHidden.clear()
        isTextDirty = true
    }
}

enum class DynamicOverlayPosition {
    BOTTOM,
    CENTER,
    TOP
}
