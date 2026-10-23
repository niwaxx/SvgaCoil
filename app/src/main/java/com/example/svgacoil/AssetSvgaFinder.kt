package com.example.svgacoil

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import java.util.Locale

/**
 * 全屏进场与礼物特效素材项
 */
data class FullscreenSvgaItem(
    val assetPath: String,   // 如 "svga/84c315dd5e67bc6e9d9e45bfc422377a/1.svga"
    val fullUrl: String,     // 如 "file:///android_asset/svga/..."
    val fileName: String,    // 如 "1.svga"
    val cleanName: String,   // 友好的显示名称
    val sizeBytes: Long
) {
    val sizeText: String
        get() = when {
            sizeBytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", sizeBytes / (1024f * 1024f))
            else -> "${sizeBytes / 1024} KB"
        }
}

/**
 * assets/svga 目录动效发现器
 */
object AssetSvgaFinder {

    // 已知非目录媒体文件扩展名，防止盲目调用 list()
    private val KNOWN_FILE_EXTENSIONS = setOf(
        "png", "jpg", "jpeg", "webp", "mp3", "wav", "m4a", "mp4", "svga", "txt", "json", "xml"
    )

    // Android 系统内置虚拟 assets 目录及静态头像目录，无需递归扫描 SVGA
    private val IGNORED_ROOT_DIRS = setOf("images", "sounds", "webkit", "avatars")

    // 规范化英文资产文件名到友好中文标题映射表
    private val FRIENDLY_TITLES = mapOf(
        "angel" to "天使降临 (angel)",
        "rose" to "浪漫玫瑰 (rose)",
        "avatar_frame_light" to "炫光流转头像框 (avatar_frame_light)",
        "avatar_frame_charm" to "妩媚妖姬头像框 (avatar_frame_charm)",
        "avatar_frame_hd_galaxy" to "星空粒子超清框 (avatar_frame_hd_galaxy)",
        "gift_royal_gold_18m" to "皇家金冠巨型礼物 (gift_royal_gold_18m)",
        "banner_jackpot_purple" to "紫金奖池横幅 (banner_jackpot_purple)",
        "banner_jackpot_gold" to "黄金奖池横幅 (banner_jackpot_gold)",
        "badge_star_crest" to "星耀王座勋章 (badge_star_crest)",
        "voice_room_cosmic_seat" to "星际麦位光环 (voice_room_cosmic_seat)",
        "entry_sakura_festival" to "樱花盛典进场 (entry_sakura_festival)",
        "entry_pk_top1" to "PK 巅峰榜首进场 (entry_pk_top1)",
        "entry_romantic_spring" to "浪漫春日进场 (entry_romantic_spring)",
        "entry_cute_puppy" to "萌犬座驾进场 (entry_cute_puppy)",
        "entry_purple_romance" to "紫晶浪漫进场 (entry_purple_romance)",
        "entry_vintage_car" to "复古老爷车进场 (entry_vintage_car)",
        "entry_lantern_festival" to "元宵佳节进场 (entry_lantern_festival)",
        "entry_sweet_date" to "甜蜜约会进场 (entry_sweet_date)",
        "entry_moonlight_scroll" to "古风月老进场条 (entry_moonlight_scroll)",
        "entry_royal_guard" to "皇家骑士高级进场 (entry_royal_guard)",
        "entry_floating_banner" to "尊贵帝王飘屏 (entry_floating_banner)",
        "entry_silver_knight" to "白银骑士进场 (entry_silver_knight)",
        "entry_golden_feather" to "金羽圣翼进场 (entry_golden_feather)",
        "entry_lucky_top2" to "幸运星 TOP2 进场 (entry_lucky_top2)",
        "entry_cute_cat_vip" to "萌猫贵族进场 (entry_cute_cat_vip)",
        "gift_fullscreen_castle_6m" to "全屏梦幻城堡礼物 (gift_fullscreen_castle_6m)"
    )

    /**
     * 异步深度递归扫描 assets 根目录及 svga 子目录，搜集全部真实有效动效素材（过滤 __MACOSX 与以 ._ 开头的隐藏文件）
     */
    suspend fun scanSvgaAssets(context: Context): List<FullscreenSvgaItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<FullscreenSvgaItem>()
        val queue = ArrayDeque<String>()
        queue.add("")

        val assetManager = context.assets

        while (queue.isNotEmpty()) {
            val dir = queue.poll() ?: continue
            val entries = try {
                assetManager.list(dir) ?: emptyArray()
            } catch (_: Throwable) {
                emptyArray()
            }

            for (entry in entries) {
                if (entry.startsWith("._") || entry == "__MACOSX") continue
                if (dir.isEmpty() && IGNORED_ROOT_DIRS.contains(entry)) continue
                val fullPath = if (dir.isEmpty()) entry else "$dir/$entry"
                val ext = entry.substringAfterLast(".", "").lowercase()

                if (ext == "svga") {
                    val size = try {
                        assetManager.open(fullPath).use { it.available().toLong() }
                    } catch (_: Throwable) {
                        0L
                    }
                    if (size > 0L) {
                        val rawName = entry.substringBeforeLast(".")
                        val clean = sanitizeDisplayName(rawName, dir)
                        result.add(
                            FullscreenSvgaItem(
                                assetPath = fullPath,
                                fullUrl = "file:///android_asset/$fullPath",
                                fileName = entry,
                                cleanName = clean.ifBlank { entry },
                                sizeBytes = size
                            )
                        )
                    }
                } else if (!KNOWN_FILE_EXTENSIONS.contains(ext)) {
                    // 没有已知扩展名，作为子目录加入队列继续扫描
                    queue.add(fullPath)
                }
            }
        }

        // 默认按文件体积降序排列，方便压测大礼物和进场大特效
        result.sortedByDescending { it.sizeBytes }
    }

    /**
     * 将规范化英文文件名映射为“中文友好标题 + 规范英文标识”，同时兼容兜底乱码清洗
     */
    internal fun sanitizeDisplayName(rawName: String, parentDir: String): String {
        FRIENDLY_TITLES[rawName]?.let { return it }
        if (!rawName.contains('\ufffd')) return rawName
        val validChars = rawName.replace("\ufffd", "").trim()
        val shortHash = parentDir.substringAfterLast("/").take(6).uppercase(Locale.US)
        return if (validChars.isNotEmpty()) {
            "特效_${validChars}_$shortHash"
        } else {
            "特效_$shortHash"
        }
    }
}
