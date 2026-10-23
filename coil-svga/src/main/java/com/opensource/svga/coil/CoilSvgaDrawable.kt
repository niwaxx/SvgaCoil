package com.opensource.svga.coil

import android.graphics.drawable.Animatable2
import android.graphics.drawable.Drawable
import coil3.DrawableImage
import com.opensource.svga.core.drawable.SvgaAnimatableDrawable
import com.opensource.svga.core.dynamic.SVGADynamicEntity
import com.opensource.svga.core.model.SVGAVideoEntity

/**
 * 接入 Coil 内存体系的自驱动 Drawable。
 * 实现 [DrawableImage.SizeProvider]，精确将底层所有已解码 Bitmap 的实际显存占用上报给 Coil MemoryCache。
 */
class CoilSvgaDrawable(
    videoItem: SVGAVideoEntity,
    dynamicItem: SVGADynamicEntity? = null
) : SvgaAnimatableDrawable(videoItem, dynamicItem), DrawableImage.SizeProvider {

    override val size: Long
        get() = videoItem.getAllocatedByteCount()

    /**
     * 是否属于未进入全局 L1 享元缓存的短暂生命周期动效（例如超过 16MB 熔断阈值的巨型动效）
     */
    var isEphemeral: Boolean = false

    /**
     * 是否在动画播放结束后自动回收底层切片显存（播后即焚）
     */
    var autoRecycleOnEnd: Boolean = false

    init {
        registerAnimationCallback(object : Animatable2.AnimationCallback() {
            override fun onAnimationEnd(drawable: Drawable?) {
                // 仅当未进入全局 L1 享元缓存池 (isEphemeral == true) 时才允许在动画结束时清空底层位图，
                // 严防将全局共享模板池中的 SVGAVideoEntity 掏空成无图空壳。
                if (autoRecycleOnEnd && isEphemeral) {
                    videoItem.clearBitmaps()
                }
            }
        })
    }

    override fun getConstantState(): ConstantState {
        return CoilSvgaConstantState(
            videoItem = videoItem,
            dynamicItem = dynamicItem,
            scaleType = scaleType,
            repeatCount = repeatCount,
            isEphemeral = isEphemeral,
            autoRecycleOnEnd = autoRecycleOnEnd
        )
    }

    private class CoilSvgaConstantState(
        private val videoItem: SVGAVideoEntity,
        private val dynamicItem: SVGADynamicEntity?,
        private val scaleType: android.widget.ImageView.ScaleType,
        private val repeatCount: Int,
        private val isEphemeral: Boolean,
        private val autoRecycleOnEnd: Boolean
    ) : ConstantState() {
        override fun newDrawable(): Drawable {
            return CoilSvgaDrawable(videoItem, dynamicItem).also {
                it.scaleType = scaleType
                it.repeatCount = repeatCount
                it.isEphemeral = isEphemeral
                it.autoRecycleOnEnd = autoRecycleOnEnd
            }
        }

        override fun getChangingConfigurations(): Int = 0
    }
}
