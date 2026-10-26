package com.opensource.svga.core.model

import android.graphics.Bitmap
import com.opensource.svga.core.util.SvgaLogger
import com.opensource.svgaplayer.proto.MovieEntity

class SVGAVideoEntity(
    val movieItem: MovieEntity,
    val rawImages: Map<String, ByteArray>
) {
    var videoSize: SVGARect = SVGARect(0.0, 0.0, 0.0, 0.0)
        private set

    var fps: Int = 20
        private set

    var frames: Int = 0
        private set

    var antiAlias: Boolean = true

    val spriteList: List<SVGAVideoSpriteEntity>

    /**
     * 当前已解码的 Bitmap 素材缓存。
     * Key 为素材 imageKey，Value 为 Bitmap。
     */
    val imageMap: MutableMap<String, Bitmap> = HashMap()

    private val _rawImages: MutableMap<String, ByteArray> = HashMap(rawImages)

    /**
     * 原始切片二进制数据，在 [prepareImages] 后若选择释放则为空。
     */
    val currentRawImages: Map<String, ByteArray>
        get() = _rawImages

    init {
        if (movieItem.hasParams()) {
            val params = movieItem.params
            videoSize = SVGARect(
                0.0,
                0.0,
                params.viewBoxWidth.toDouble(),
                params.viewBoxHeight.toDouble()
            )
            fps = if (params.fps > 0) params.fps else 20
            frames = params.frames
        }

        spriteList = movieItem.spritesList.map { SVGAVideoSpriteEntity(it) }
    }

    /**
     * 精确统计当前实体在内存中持有的所有 Bitmap 的实际字节大小。
     * 用于向 Coil MemoryCache 或外部缓存池真实报告体积，辅助 LRU 淘汰。
     */
    fun getAllocatedByteCount(): Long {
        return imageMap.values.sumOf { it.allocationByteCount.toLong() }
    }

    /**
     * 根据目标尺寸按需下采样解码切片素材，并可选释放原始未解码字节流以节省堆内存
     */
    fun prepareImages(
        targetWidth: Int = 0,
        targetHeight: Int = 0,
        preferredConfig: Bitmap.Config? = null,
        allowHardware: Boolean = false,
        releaseRawBytes: Boolean = true
    ) {
        if (imageMap.isNotEmpty()) return

        SvgaLogger.debug { "prepareImages started: target=${targetWidth}x${targetHeight}, rawSliceCount=${_rawImages.size}" }

        // 计算画布相较于目标展示尺寸的缩放比率
        val canvasScale = if (videoSize.width > 0 && videoSize.height > 0 && targetWidth > 0 && targetHeight > 0) {
            maxOf(
                targetWidth.toDouble() / videoSize.width,
                targetHeight.toDouble() / videoSize.height
            )
        } else {
            1.0
        }

        val iterator = _rawImages.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val bitmap = com.opensource.svga.core.bitmap.SvgaBitmapDecoder.decodeBitmap(
                bytes = entry.value,
                targetWidth = targetWidth,
                targetHeight = targetHeight,
                canvasScale = canvasScale,
                preferredConfig = preferredConfig,
                allowHardware = allowHardware
            )
            if (bitmap != null) {
                imageMap[entry.key] = bitmap
            }
            // 关键优化：若指示释放原始字节，每解码成功一张立即释放一张，杜绝未解码字节与 Bitmap 产生 2 倍内存重叠
            if (releaseRawBytes) {
                iterator.remove()
            }
        }

        SvgaLogger.info {
            "prepareImages completed: decoded ${imageMap.size} bitmaps, total allocated memory: ${getAllocatedByteCount() / 1024} KB"
        }
    }

    /**
     * 手动释放原始未解码的切片字节流
     */
    fun releaseRawImages() {
        _rawImages.clear()
    }

    /**
     * 清理所有已解码的 Bitmap 引用。
     * @param recycleImmediately 是否立即调用 [Bitmap.recycle]。默认 false，仅断开强引用交由 GC 与
     * NativeAllocationRegistry 安全回收，避免 RenderThread 异步绘制未完成时触发
     * "Canvas: trying to use a recycled bitmap" 崩溃。
     */
    fun clearBitmaps(recycleImmediately: Boolean = false) {
        val count = imageMap.size
        if (recycleImmediately) {
            imageMap.values.forEach {
                if (!it.isRecycled) {
                    it.recycle()
                }
            }
        }
        imageMap.clear()
        SvgaLogger.debug { "clearBitmaps (recycleImmediately=$recycleImmediately): cleared $count bitmaps" }
    }
}
