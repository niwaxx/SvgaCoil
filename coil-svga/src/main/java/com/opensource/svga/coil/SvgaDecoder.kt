package com.opensource.svga.coil

import coil3.ImageLoader
import coil3.asImage
import coil3.decode.AssetMetadata
import coil3.decode.ContentMetadata
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.decode.ResourceMetadata
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.request.allowHardware
import coil3.size.pxOrElse
import com.opensource.svga.core.parser.SVGAParser
import com.opensource.svga.core.util.SvgaLogger
import kotlinx.coroutines.sync.withPermit
import okio.use

class SvgaDecoder(
    private val source: ImageSource,
    private val options: Options
) : Decoder {

    override suspend fun decode(): DecodeResult {
        val startTime = System.currentTimeMillis()

        // 1. 尝试提取模板享元缓存 Key（优先顺序：自定义 Key -> diskCacheKey -> 元数据 URI -> 文件路径 -> 二进制特征指纹）
        val cacheKey = options.extras[SVGA_CACHE_KEY]
            ?: options.diskCacheKey
            ?: resolveMetadataCacheKey(source.metadata)
            ?: source.fileOrNull()?.toString()
            ?: computeSourceHash(source.source())

        val targetWidth = options.size.width.pxOrElse { 0 }
        val targetHeight = options.size.height.pxOrElse { 0 }
        val allowHardware = options.extras[SVGA_ALLOW_HARDWARE_KEY] ?: options.allowHardware

        // 2. 根据目标容器尺寸与屏幕 density 计算请求的分辨率等级（Tier）
        val density = try {
            options.context.resources.displayMetrics.density
        } catch (_: Throwable) {
            2.5f
        }
        val requestedTier = SvgaResolutionTier.from(targetWidth, targetHeight, density)

        SvgaLogger.debug {
            "SvgaDecoder.decode start: key=$cacheKey, target=${targetWidth}x${targetHeight}, tier=$requestedTier, allowHardware=$allowHardware"
        }

        val autoRecycleOnEnd = options.extras[SVGA_AUTO_RECYCLE_ON_END_KEY] ?: false
        // 若调用方显式开启了 autoRecycleOnEnd 但未指定 maxCacheableBytes，默认不入全局 L1 享元缓存（0L），
        // 确保单次播后即焚动效不会进入全局缓存池，同时兼容显式设置了 maxCacheableBytes 的分级熔断场景。
        val maxCacheableBytes = options.extras[SVGA_MAX_CACHEABLE_BYTES_KEY]
            ?: if (autoRecycleOnEnd) 0L else SvgaTemplateCache.maxSingleObjectCacheableBytes

        // 3. 查阅享元模板缓存池（仅当允许入缓存即 maxCacheableBytes > 0 时才查缓存，支持向下兼容复用）
        val compatibleMatch = if (maxCacheableBytes > 0L) {
            SvgaTemplateCache.getCompatible(cacheKey, requestedTier)
        } else {
            null
        }
        var isCached = compatibleMatch != null
        if (compatibleMatch != null) {
            SvgaLogger.debug {
                "Cache HIT (${compatibleMatch.second} for $requestedTier): key=$cacheKey"
            }
        }

        val videoEntity = compatibleMatch?.first ?: parseSemaphore.withPermit {
            // 双重检查锁（DCL）：排队等待期间，可能前序协程已解析完成并存入缓存
            val doubleCheck = if (maxCacheableBytes > 0L) {
                SvgaTemplateCache.getCompatible(cacheKey, requestedTier)
            } else {
                null
            }
            if (doubleCheck != null) {
                isCached = true
                SvgaLogger.debug {
                    "Cache HIT on DCL (${doubleCheck.second} for $requestedTier): key=$cacheKey"
                }
                doubleCheck.first
            } else {
                SvgaLogger.debug { "Cache MISS: parsing and decoding slices for key=$cacheKey (tier=$requestedTier)..." }
                val parsed = source.source().use { bufferedSource ->
                    SVGAParser.parse(bufferedSource)
                }
                parsed.prepareImages(
                    targetWidth = targetWidth,
                    targetHeight = targetHeight,
                    allowHardware = allowHardware,
                    releaseRawBytes = true
                )
                // 写入缓存池，超过 maxCacheableBytes 阈值将熔断拦截返回 false，不入 L1 全局缓存
                val putSuccess = SvgaTemplateCache.put(cacheKey, requestedTier, parsed, maxCacheableBytes)
                isCached = putSuccess
                parsed
            }
        }

        SvgaLogger.info {
            "SVGA decode finished in ${System.currentTimeMillis() - startTime}ms: key=$cacheKey, tier=$requestedTier, cached=$isCached, allocated=${videoEntity.getAllocatedByteCount() / 1024}KB"
        }

        // 4. 提取动态实体配置与重复次数
        val dynamic = options.extras[SVGA_DYNAMIC_ENTITY_KEY]
        val repeatCount = options.extras[SVGA_REPEAT_COUNT_KEY] ?: 0

        // 5. 组装具备显存精确统计的 CoilSvgaDrawable 实例
        // 注意：asImage(shareable = false) 确保每个视图拥有独立的 Drawable 动画状态机与 Callback，
        // 底层重量级位图与几何数据则通过 SvgaTemplateCache 实现 100% 享元共享。
        val drawable = CoilSvgaDrawable(
            videoItem = videoEntity,
            dynamicItem = dynamic
        ).apply {
            this.repeatCount = repeatCount
            this.isEphemeral = !isCached
            this.autoRecycleOnEnd = autoRecycleOnEnd
        }

        return DecodeResult(
            image = drawable.asImage(shareable = false),
            isSampled = targetWidth > 0 && targetHeight > 0
        )
    }

    private fun resolveMetadataCacheKey(metadata: ImageSource.Metadata?): String? {
        return when (metadata) {
            is AssetMetadata -> "file:///android_asset/${metadata.filePath}"
            is ResourceMetadata -> "android.resource://${metadata.packageName}/${metadata.resId}"
            is ContentMetadata -> metadata.uri.toString()
            else -> null
        }
    }

    private fun computeSourceHash(bufferedSource: okio.BufferedSource): String {
        return try {
            val peek = bufferedSource.peek()
            val buffer = okio.Buffer()
            val readLen = peek.read(buffer, 8192L)
            if (readLen > 0) "svga_hash_" + buffer.sha256().hex() else "empty_svga"
        } catch (_: Throwable) {
            "unknown_svga"
        }
    }

    companion object {
        // 全局限制最多 2 个动效并发进行反序列化与切片解码，防止快速滑屏时多个 18MB 巨型动效并发叠加引发瞬间 OOM
        private val parseSemaphore = kotlinx.coroutines.sync.Semaphore(2)
    }

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader
        ): Decoder? {
            if (!isApplicable(result)) return null
            return SvgaDecoder(result.source, options)
        }

        private fun isApplicable(result: SourceFetchResult): Boolean {
            val mime = result.mimeType
            if (mime != null) {
                if (mime.contains("svga", ignoreCase = true) || mime.contains("svga-animation", ignoreCase = true)) {
                    return true
                }
                // 快速放行常见标准图片 MIME（如 image/png, image/jpeg, image/webp, image/gif），避免无意义的流读取与误判
                if (mime.startsWith("image/", ignoreCase = true)) {
                    return false
                }
            }

            // 读取前 4 个字节判定是否为 ZIP 魔数 (PK\x03\x04) 或标准 ZLIB 压缩魔数
            val source = result.source.source()
            if (source.request(4)) {
                val buffer = source.buffer
                val b0 = buffer[0].toInt() and 0xFF
                val b1 = buffer[1].toInt() and 0xFF
                val b2 = buffer[2].toInt() and 0xFF
                val b3 = buffer[3].toInt() and 0xFF

                // 快速排除常见图片格式文件头 (PNG, JPEG, GIF, RIFF/WebP)
                if ((b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47) || // PNG
                    (b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF) ||               // JPEG
                    (b0 == 0x47 && b1 == 0x49 && b2 == 0x46 && b3 == 0x38) || // GIF
                    (b0 == 0x52 && b1 == 0x49 && b2 == 0x46 && b3 == 0x46)    // RIFF/WebP
                ) {
                    return false
                }

                // ZIP: PK\x03\x04 (SVGA 1.x)
                if (b0 == 0x50 && b1 == 0x4B && b2 == 0x03 && b3 == 0x04) {
                    return true
                }

                // ZLIB (SVGA 2.0+, RFC 1950):
                // CM (b0 低 4 位) == 8 (Deflate)，CINFO (b0 高 4 位) 位于 1..7 之间 (通常为 7 即 0x78)，
                // FDICT (b1 第 5 位) == 0，且 (b0 * 256 + b1) % 31 == 0
                val cm = b0 and 0x0F
                val cinfo = (b0 ushr 4) and 0x0F
                val fdict = (b1 ushr 5) and 0x01
                if (cm == 8 && cinfo in 1..7 && fdict == 0 && (b0 * 256 + b1) % 31 == 0) {
                    return true
                }
            }
            return false
        }
    }
}
