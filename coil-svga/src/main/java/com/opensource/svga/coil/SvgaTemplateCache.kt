package com.opensource.svga.coil

import androidx.collection.LruCache
import com.opensource.svga.core.model.SVGAVideoEntity

/**
 * SVGA 全局享元模板缓存池（Flyweight Template Cache）。
 * 保证相同动效资源（URL、Asset、文件）全局只解析一份 [SVGAVideoEntity] 并共享其切片与关键帧。
 * 多个实例（哪怕各自持有不同的 DynamicEntity）均复用该模板，从根本上解决重复解码导致的堆内存爆满与 OOM。
 */
object SvgaTemplateCache {

    // 根据当前 JVM 可用最大堆内存动态分配享元缓存配额（占最大堆的 25%，保底 64MB，上限 160MB）
    // 确保 18MB 级别的超大型巨无霸 SVGA 动效混排压测时兼具极致画质与 LRU 防 OOM 保障
    private val DEFAULT_MAX_SIZE_KB: Int = run {
        val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        (maxMemoryKb / 4).coerceIn(64 * 1024, 160 * 1024)
    }

    /**
     * 单个动效允许进入全局 L1 享元缓存的最大显存字节数阈值。
     * 超过此阈值的超大/全屏动效（如大火箭、巨型进场动效）坚决不入 L1 缓存，
     * 彻底避免大对象挤占导致常用高频小动效（头像框、勋章、麦位标）被强制驱逐（Cache Thrashing）。
     * 默认值：16MB (16 * 1024 * 1024 字节)。
     * 支持业务全局动态配置，如设为 Long.MAX_VALUE 则不设上限。
     */
    @Volatile
    var maxSingleObjectCacheableBytes: Long = 16 * 1024 * 1024L

    private val cache = object : LruCache<String, SVGAVideoEntity>(DEFAULT_MAX_SIZE_KB) {
        override fun sizeOf(key: String, value: SVGAVideoEntity): Int {
            val bytes = value.getAllocatedByteCount()
            // 至少占用 1KB 权重
            return (bytes / 1024).toInt().coerceAtLeast(1)
        }

        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: SVGAVideoEntity,
            newValue: SVGAVideoEntity?
        ) {
            super.entryRemoved(evicted, key, oldValue, newValue)
            // 当被 LRU 淘汰且没有新值替换时，可选择由 GC 自然回收
        }
    }

    /**
     * 根据 key 获取已缓存的 SVGA 动效只读模板（精确查找或 baseKey 兼容查找）
     */
    @Synchronized
    fun get(key: String): SVGAVideoEntity? {
        val exact = cache.get(key)
        if (exact != null) return exact

        // 若传入的是 baseKey（不含分级标记），自动按优先级回退检索已有分级（FULL -> MEDIUM -> THUMBNAIL）
        if (!key.contains('#')) {
            val fullMatch = cache.get(buildTierKey(key, SvgaResolutionTier.FULL))
            if (fullMatch != null) return fullMatch
            val medMatch = cache.get(buildTierKey(key, SvgaResolutionTier.MEDIUM))
            if (medMatch != null) return medMatch
            val thumbMatch = cache.get(buildTierKey(key, SvgaResolutionTier.THUMBNAIL))
            if (thumbMatch != null) return thumbMatch
        }
        return null
    }

    /**
     * 针对基础资源 key [baseKey] 与目标请求分级 [requestedTier] 寻找兼容的享元模板。
     * 核心复用策略（向下兼容 Downwards Reuse）：
     * 1. 优先寻找完全匹配的当前分级：baseKey#requestedTier
     * 2. 若未命中，寻找能够满足该分辨率的更高规格分级（例如请求 THUMBNAIL 时复用已有的 FULL 或 MEDIUM；请求 MEDIUM 时复用 FULL）。
     *    因为高分辨率切片在小尺寸 View 上绘制时，Canvas 缩放呈现无损超清画面，且无需重复解码切片，达成 0 新增内存分配！
     * 3. 兜底兼容不带分级后缀的旧 Key。
     *
     * @return 命中的模板实体与其所属的实际分级，未命中则返回 null
     */
    @Synchronized
    fun getCompatible(baseKey: String, requestedTier: SvgaResolutionTier): Pair<SVGAVideoEntity, SvgaResolutionTier>? {
        // 1. 精确匹配当前请求的分级模板
        val exactKey = buildTierKey(baseKey, requestedTier)
        val exactMatch = cache.get(exactKey)
        if (exactMatch != null) {
            return Pair(exactMatch, requestedTier)
        }

        // 2. 向下兼容复用智能判定：
        // 仅当已有更高分级模板且其显存占用轻量 (<= 2.5MB) 时，才向下复用；
        // 若大图显存巨大（如 18MB 巨无霸动效 FULL 占 35MB），坚决独立生成 THUMBNAIL，防止小挂件承载超大显存
        val maxSafeReuseBytes = 2560 * 1024L // 2.5MB
        when (requestedTier) {
            SvgaResolutionTier.THUMBNAIL -> {
                val fullKey = buildTierKey(baseKey, SvgaResolutionTier.FULL)
                val fullMatch = cache.get(fullKey)
                if (fullMatch != null && fullMatch.getAllocatedByteCount() <= maxSafeReuseBytes) {
                    return Pair(fullMatch, SvgaResolutionTier.FULL)
                }

                val mediumKey = buildTierKey(baseKey, SvgaResolutionTier.MEDIUM)
                val mediumMatch = cache.get(mediumKey)
                if (mediumMatch != null && mediumMatch.getAllocatedByteCount() <= maxSafeReuseBytes) {
                    return Pair(mediumMatch, SvgaResolutionTier.MEDIUM)
                }
            }
            SvgaResolutionTier.MEDIUM -> {
                val fullKey = buildTierKey(baseKey, SvgaResolutionTier.FULL)
                val fullMatch = cache.get(fullKey)
                if (fullMatch != null && fullMatch.getAllocatedByteCount() <= maxSafeReuseBytes) {
                    return Pair(fullMatch, SvgaResolutionTier.FULL)
                }
            }
            SvgaResolutionTier.FULL -> {
                // FULL 绝不向上复用低清图，保证原生全高清
            }
        }

        // 3. 兜底查不带分级标记的旧 baseKey
        val legacyMatch = cache.get(baseKey)
        if (legacyMatch != null && legacyMatch.getAllocatedByteCount() <= maxSafeReuseBytes) {
            return Pair(legacyMatch, SvgaResolutionTier.FULL)
        }

        return null
    }

    /**
     * 存入解析好的 SVGA 模板（指定分级）
     * @param maxCacheableBytes 允许入库的最大显存字节数限制。超过该限制将触发准入熔断拦截，返回 false。
     * @return true 表示成功入库；false 表示因超过单体阈值熔断拦截未入库
     */
    @Synchronized
    fun put(
        baseKey: String,
        tier: SvgaResolutionTier,
        entity: SVGAVideoEntity,
        maxCacheableBytes: Long = maxSingleObjectCacheableBytes
    ): Boolean {
        val totalBytes = entity.getAllocatedByteCount()
        if (maxCacheableBytes <= 0L || totalBytes > maxCacheableBytes) {
            // maxCacheableBytes <= 0 表示纯短暂生命周期强制不入缓存，或超过单体上限触发准入熔断拦截
            return false
        }
        val tierKey = buildTierKey(baseKey, tier)
        cache.put(tierKey, entity)
        return true
    }

    /**
     * 存入解析好的 SVGA 模板（兼容原始 key 接口）
     * @return true 表示成功入库；false 表示因超过单体阈值熔断拦截未入库
     */
    @Synchronized
    fun put(key: String, entity: SVGAVideoEntity, maxCacheableBytes: Long = maxSingleObjectCacheableBytes): Boolean {
        val totalBytes = entity.getAllocatedByteCount()
        if (maxCacheableBytes <= 0L || totalBytes > maxCacheableBytes) {
            return false
        }
        cache.put(key, entity)
        return true
    }

    /**
     * 移除指定的模板
     */
    @Synchronized
    fun remove(key: String): SVGAVideoEntity? {
        return cache.remove(key)
    }

    /**
     * 清空全部模板缓存
     */
    @Synchronized
    fun clear() {
        cache.evictAll()
    }

    /**
     * 获取当前缓存池中持有的模板实体条目总数（真实对象个数，如 1 个、2 个）
     */
    @Synchronized
    fun entryCount(): Int = cache.snapshot().size

    /**
     * 获取当前 LruCache 占用的权重总和（以 KB 为单位）
     */
    @Synchronized
    fun size(): Int = cache.size()

    /**
     * 获取当前已占用的显存估计值（字节）
     */
    @Synchronized
    fun totalAllocatedBytes(): Long {
        return cache.snapshot().values.sumOf { it.getAllocatedByteCount() }
    }

    /**
     * 获取当前缓存池内所有条目的元数据快照（供调试与看板查看）
     */
    @Synchronized
    fun getSnapshotEntries(): List<TemplateCacheEntry> {
        return cache.snapshot().map { (key, entity) ->
            val tier = SvgaResolutionTier.entries.find { key.endsWith("#${it.name}") }
            val baseKey = if (tier != null) key.substringBeforeLast("#") else key
            TemplateCacheEntry(
                fullKey = key,
                baseKey = baseKey,
                tier = tier,
                allocatedBytes = entity.getAllocatedByteCount()
            )
        }
    }

    private fun buildTierKey(baseKey: String, tier: SvgaResolutionTier): String {
        return "${baseKey}#${tier.name}"
    }
}

/**
 * 缓存池条目元数据快照
 */
data class TemplateCacheEntry(
    val fullKey: String,
    val baseKey: String,
    val tier: SvgaResolutionTier?,
    val allocatedBytes: Long
)
