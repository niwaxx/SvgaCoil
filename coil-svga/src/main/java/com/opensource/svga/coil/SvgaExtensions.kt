package com.opensource.svga.coil

import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.graphics.drawable.LayerDrawable
import android.widget.ImageView
import coil3.Extras
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.transition.CrossfadeDrawable
import com.opensource.svga.core.dynamic.SVGADynamicEntity

const val SVGA_DYNAMIC_MEMORY_CACHE_EXTRA_KEY = "svga_dyn"

val SVGA_DYNAMIC_ENTITY_KEY = Extras.Key<SVGADynamicEntity?>(default = null)
val SVGA_ALLOW_HARDWARE_KEY = Extras.Key<Boolean?>(default = null)
val SVGA_REPEAT_COUNT_KEY = Extras.Key<Int>(default = 0)
val SVGA_CACHE_KEY = Extras.Key<String?>(default = null)

fun ImageRequest.Builder.svgaDynamic(dynamic: SVGADynamicEntity): ImageRequest.Builder = apply {
    extras[SVGA_DYNAMIC_ENTITY_KEY] = dynamic
    // 使用 Coil 3 官方原生 memoryCacheKeyExtra 将动态实体特征指纹混入缓存键，
    // 彻底消除反射读取私有字段 data 带来的链式调用顺序耦合与 R8 混淆失效隐患。
    memoryCacheKeyExtra(SVGA_DYNAMIC_MEMORY_CACHE_EXTRA_KEY, dynamic.computeSignature())
}

fun ImageRequest.Builder.svgaDynamic(block: SVGADynamicEntity.() -> Unit): ImageRequest.Builder = apply {
    val dynamic = SVGADynamicEntity().apply(block)
    svgaDynamic(dynamic)
}

fun ImageRequest.Builder.svgaAllowHardware(allow: Boolean): ImageRequest.Builder = apply {
    extras[SVGA_ALLOW_HARDWARE_KEY] = allow
}

fun ImageRequest.Builder.svgaRepeatCount(count: Int): ImageRequest.Builder = apply {
    extras[SVGA_REPEAT_COUNT_KEY] = count
}

fun ImageRequest.Builder.svgaCacheKey(key: String): ImageRequest.Builder = apply {
    extras[SVGA_CACHE_KEY] = key
}

val SVGA_MAX_CACHEABLE_BYTES_KEY = Extras.Key<Long?>(default = null)
val SVGA_AUTO_RECYCLE_ON_END_KEY = Extras.Key<Boolean>(default = false)

/**
 * 设置单个动效允许进入全局 L1 享元缓存的最大显存字节数上限。
 * 超过该阈值自动触发准入熔断拦截，不入 L1 缓存，保护常用高频小动效；
 * 设为 0 表示强制不入 L1 缓存（纯短暂生命周期）；
 * 传 null（默认）表示使用全局 [SvgaTemplateCache.maxSingleObjectCacheableBytes]。
 */
fun ImageRequest.Builder.svgaMaxCacheableBytes(bytes: Long): ImageRequest.Builder = apply {
    extras[SVGA_MAX_CACHEABLE_BYTES_KEY] = bytes
}

/**
 * 设置播放完毕后是否自动回收底层切片显存（适用于播后即焚的全屏大礼物动效）。
 */
fun ImageRequest.Builder.svgaAutoRecycleOnEnd(autoRecycle: Boolean): ImageRequest.Builder = apply {
    extras[SVGA_AUTO_RECYCLE_ON_END_KEY] = autoRecycle
}

/**
 * 标记为短暂生命周期的动效（播后即焚）：
 * 1. 强制不入全局 L1 享元模板缓存池（svgaMaxCacheableBytes = 0L）；
 * 2. 禁用 Coil MemoryCache 写入，防止大体积短暂动效污染图片缓存池；
 * 3. 动画播放完毕后自动回收底层切片显存（autoRecycleOnEnd = true）。
 */
fun ImageRequest.Builder.svgaEphemeral(autoRecycleOnEnd: Boolean = true): ImageRequest.Builder = apply {
    svgaMaxCacheableBytes(0L)
    svgaAutoRecycleOnEnd(autoRecycleOnEnd)
    memoryCachePolicy(CachePolicy.DISABLED)
}

/**
 * 从 [ImageView] 当前挂载的 Drawable（含 [CrossfadeDrawable] 等过渡包装层）中提取底层 [CoilSvgaDrawable] 实例。
 * 方便传统 View 页面直接控制动效播放状态（start/stop）、动态插槽或读取显存指标。
 */
fun ImageView.findSvgaDrawable(): CoilSvgaDrawable? {
    return drawable?.unwrapSvgaDrawable()
}

/**
 * 递归剥离包装层提取 [CoilSvgaDrawable]
 */
fun Drawable.unwrapSvgaDrawable(): CoilSvgaDrawable? {
    return when (this) {
        is CoilSvgaDrawable -> this
        is CrossfadeDrawable -> (this.end as? Drawable)?.unwrapSvgaDrawable()
            ?: (this.start as? Drawable)?.unwrapSvgaDrawable()
        is DrawableWrapper -> this.drawable?.unwrapSvgaDrawable()
        is LayerDrawable -> {
            for (i in numberOfLayers - 1 downTo 0) {
                getDrawable(i)?.unwrapSvgaDrawable()?.let { return it }
            }
            null
        }
        else -> null
    }
}

