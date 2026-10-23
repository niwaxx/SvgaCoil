package com.opensource.svga.coil

/**
 * SVGA 目标展示尺寸分级枚举。
 * 针对不同大小的 View / 容器实行尺寸分级管控，解决“小控件过度占用内存”与“大控件画质发虚”的矛盾。
 */
enum class SvgaResolutionTier(val maxDimensionDp: Int) {
    /**
     * 极小尺寸挂件（<= 96dp，如列表头像挂件、小徽章、迷你点缀）
     * 采用极限尺寸下采样，切片内存降至原图的 1/16 ~ 1/64。
     */
    THUMBNAIL(96),

    /**
     * 中等尺寸挂件（96dp ~ 240dp，如弹窗、消息气泡动效、中型卡片）
     * 兼顾画质与显存，适度下采样。
     */
    MEDIUM(240),

    /**
     * 大尺寸/全屏动效（> 240dp，如全屏送礼、横幅大动效、全屏播放）
     * 保证原生全高清细节。
     */
    FULL(Int.MAX_VALUE);

    /**
     * 当前分辨率级别是否可以满足指定的目标请求级别（向下兼容判定）。
     * 高分辨率版本（例如 FULL）完全向下兼容较低分辨率请求（如 MEDIUM 或 THUMBNAIL），
     * 因为高分辨率在小容器上缩小绘制无损锐利，无需重复解码小尺寸切片。
     */
    fun canSatisfy(requested: SvgaResolutionTier): Boolean {
        return this.ordinal >= requested.ordinal
    }

    companion object {
        fun from(widthPx: Int, heightPx: Int, density: Float = 2.5f): SvgaResolutionTier {
            val maxPx = maxOf(widthPx, heightPx)
            val maxDp = if (density > 0f) (maxPx / density).toInt() else maxPx
            return when {
                maxDp <= 0 -> FULL // 未指定明确尺寸时按原生 FULL 处理
                maxDp <= THUMBNAIL.maxDimensionDp -> THUMBNAIL
                maxDp <= MEDIUM.maxDimensionDp -> MEDIUM
                else -> FULL
            }
        }
    }
}
