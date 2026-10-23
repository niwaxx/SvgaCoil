package com.example.svgacoil.ui.demos

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.DrawableImage
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import com.example.svgacoil.AssetSvgaFinder
import com.example.svgacoil.FullscreenSvgaItem
import com.example.svgacoil.ui.theme.SvgaCoilTheme
import com.opensource.svga.coil.CoilSvgaDrawable
import com.opensource.svga.coil.SvgaTemplateCache
import com.opensource.svga.coil.svgaAutoRecycleOnEnd
import com.opensource.svga.coil.svgaDynamic
import com.opensource.svga.coil.svgaMaxCacheableBytes
import com.opensource.svga.coil.svgaRepeatCount
import com.opensource.svga.core.dynamic.DynamicOverlayPosition
import java.util.Locale
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * 循环播放模式枚举
 */
enum class FullscreenLoopMode(val label: String, val icon: String) {
    SEQUENCE("顺序轮播", "🔁"),
    RANDOM("随机穿插", "🔀"),
    SINGLE("单曲循环", "🔂")
}

/**
 * 🎬 全屏 SVGA 特效循环轮播播放器
 * 支持循环播放 assets/svga 下 562+ 款进场与大礼物动效，具备防 OOM 限流与动态注入
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullscreenLoopPlayerDemo(
    isPureFullscreen: Boolean = false,
    onTogglePureFullscreen: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val isInspection = LocalInspectionMode.current

    // 素材列表与加载状态（Preview 模式下直接注入示例素材，避免停留在扫描加载态）
    var isLoading by remember { mutableStateOf(!isInspection) }
    var allItems by remember {
        mutableStateOf(
            if (isInspection) {
                listOf(
                    FullscreenSvgaItem(
                        assetPath = "gift_royal_gold_18m.svga",
                        fullUrl = "file:///android_asset/gift_royal_gold_18m.svga",
                        fileName = "gift_royal_gold_18m.svga",
                        cleanName = "皇家金冠巨型礼物 (gift_royal_gold_18m)",
                        sizeBytes = 18_450_000L
                    ),
                    FullscreenSvgaItem(
                        assetPath = "angel.svga",
                        fullUrl = "file:///android_asset/angel.svga",
                        fileName = "angel.svga",
                        cleanName = "天使降临 (angel)",
                        sizeBytes = 214_016L
                    )
                )
            } else {
                emptyList()
            }
        )
    }

    // 播放器状态
    var currentIndex by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(true) }
    var loopMode by remember { mutableStateOf(FullscreenLoopMode.SEQUENCE) }
    var advanceIntervalSec by remember { mutableIntStateOf(0) } // 0: 播完一遍即切(原生单次时长)；>0: 固定秒数
    var currentDurationMs by remember { mutableLongStateOf(3000L) }
    var currentFramesInfo by remember { mutableStateOf<String?>(if (isInspection) "120帧 @ 30fps (4.0s)" else null) }
    var elapsedMillis by remember { mutableLongStateOf(if (isInspection) 1200L else 0L) }
    var enableDynamicInject by remember { mutableStateOf(true) }

    // 🌟 L1 享元缓存准入熔断控制状态（默认 16MB 阈值，支持动态调节）
    var thresholdMb by remember { mutableIntStateOf(16) }
    var isCurrentEphemeral by remember { mutableStateOf(isInspection) }
    var currentAllocatedBytes by remember { mutableLongStateOf(if (isInspection) 22 * 1024 * 1024L else 0L) }
    var autoRecycleOnEnd by remember { mutableStateOf(true) }
    val thresholdBytes = if (thresholdMb > 0) thresholdMb * 1024 * 1024L else Long.MAX_VALUE

    // 同步更新全局默认阈值
    LaunchedEffect(thresholdBytes) {
        SvgaTemplateCache.maxSingleObjectCacheableBytes = thresholdBytes
    }

    // UI 交互状态
    var showControls by remember { mutableStateOf(true) }
    var showDrawer by remember { mutableStateOf(false) }
    var searchKeyword by remember { mutableStateOf("") }
    var sortByDesc by remember { mutableStateOf(true) }

    // 异步深度扫描 assets/svga 目录（Preview 模式下跳过异步扫描）
    LaunchedEffect(Unit) {
        if (!isInspection) {
            isLoading = true
            val list = AssetSvgaFinder.scanSvgaAssets(context)
            allItems = list
            isLoading = false
        }
    }

    val targetTotalMs = if (advanceIntervalSec == 0) currentDurationMs else advanceIntervalSec * 1000L

    // 自动轮播计时驱动核心逻辑（支持按原生单次动画时长精准播完即切）
    LaunchedEffect(currentIndex, isPlaying, loopMode, advanceIntervalSec, currentDurationMs, currentAllocatedBytes, allItems.size) {
        elapsedMillis = 0L
        if (!isPlaying || loopMode == FullscreenLoopMode.SINGLE || allItems.isEmpty()) {
            return@LaunchedEffect
        }
        // 等待切片解码完成并获取到真实时长后再开始计时轮播
        while (currentAllocatedBytes == 0L) {
            delay(50L)
            if (!isPlaying) return@LaunchedEffect
        }
        val totalMs = if (advanceIntervalSec == 0) currentDurationMs else advanceIntervalSec * 1000L
        val tickMs = 50L
        while (elapsedMillis < totalMs) {
            delay(tickMs)
            elapsedMillis += tickMs
        }
        // 倒计时结束，自动进入下一个特效
        when (loopMode) {
            FullscreenLoopMode.SEQUENCE -> {
                currentIndex = (currentIndex + 1) % allItems.size
            }
            FullscreenLoopMode.RANDOM -> {
                if (allItems.size > 1) {
                    var next = Random.nextInt(allItems.size)
                    if (next == currentIndex) {
                        next = (currentIndex + 1) % allItems.size
                    }
                    currentIndex = next
                }
            }
            FullscreenLoopMode.SINGLE -> {}
        }
    }

    // 计算当前倒计时进度 (0f..1f)
    val progress = if (targetTotalMs > 0L && isPlaying && loopMode != FullscreenLoopMode.SINGLE) {
        (elapsedMillis.toFloat() / targetTotalMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0C0E14)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(color = Color(0xFF6366F1))
                Text(
                    text = "正在扫描 assets 精选特效素材库...",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "自动解析多层目录并排除临时系统文件",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
        return
    }

    if (allItems.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0C0E14)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "未在 assets/svga 目录下找到有效的 .svga 特效文件", color = Color.White)
        }
        return
    }

    val safeIndex = currentIndex.coerceIn(0, allItems.size - 1)
    val currentItem = allItems[safeIndex]

    // 切换特效时重置瞬态解析指标（Preview 模式下保留预览指标）
    LaunchedEffect(safeIndex) {
        if (!isInspection) {
            currentAllocatedBytes = 0L
            currentFramesInfo = null
            isCurrentEphemeral = false
        }
    }

    // 构建 Coil 请求，支持进场文字与尊贵头衔动态注入，以及显存准入熔断控制与播后即焚
    val request = remember(currentItem.fullUrl, enableDynamicInject, thresholdBytes, autoRecycleOnEnd) {
        ImageRequest.Builder(context)
            .data(currentItem.fullUrl)
            .svgaRepeatCount(0) // 无限循环单体动画帧，由轮播计时器负责切换
            .svgaMaxCacheableBytes(thresholdBytes)
            .svgaAutoRecycleOnEnd(autoRecycleOnEnd)
            .apply {
                if (enableDynamicInject) {
                    svgaDynamic {
                        setDynamicText("👑 榜一大哥 乘驾神龙 降临直播间！", "banner")
                        setDynamicText("👑 榜一大哥", "user_name")
                        setDynamicText("👑 尊贵贵宾", "name")
                        setDynamicText("欢迎 帝王·冷月 驾临直播间！", "text")
                        setDynamicText("极速狂飙 豪华入场", "title")
                        setOverlayText("👑 榜一大哥 乘驾神龙 降临直播间！", DynamicOverlayPosition.CENTER)
                    }
                }
            }
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090A10)) // 沉浸暗黑舞台背景
            .pointerInput(Unit) {
                detectTapGestures {
                    // 轻触切换浮层显隐
                    showControls = !showControls
                }
            }
            .pointerInput(allItems.size) {
                var totalDragX = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDragX = 0f },
                    onDragEnd = {
                        if (allItems.isNotEmpty()) {
                            if (totalDragX < -60f) {
                                // 左滑切换下一个特效
                                currentIndex = (safeIndex + 1) % allItems.size
                            } else if (totalDragX > 60f) {
                                // 右滑切换上一个特效
                                currentIndex = if (safeIndex > 0) safeIndex - 1 else allItems.size - 1
                            }
                        }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        totalDragX += dragAmount
                    }
                )
            }
    ) {
        // 1. 全屏沉浸式 SVGA 动效播放
        AsyncImage(
            model = request,
            contentDescription = currentItem.cleanName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center),
            onState = { state ->
                if (state is AsyncImagePainter.State.Success) {
                    val img = state.result.image
                    if (img is DrawableImage) {
                        val drawable = img.drawable as? CoilSvgaDrawable
                        if (drawable != null) {
                            isCurrentEphemeral = drawable.isEphemeral
                            currentAllocatedBytes = drawable.size
                            if (drawable.videoItem.fps > 0) {
                                val dur = (drawable.videoItem.frames * 1000L) / drawable.videoItem.fps
                                currentDurationMs = dur.coerceAtLeast(300L)
                                currentFramesInfo = "${drawable.videoItem.frames}帧 @ ${drawable.videoItem.fps}fps (${String.format(Locale.US, "%.1f", dur / 1000f)}s)"
                            }
                        }
                    }
                }
            }
        )

        // 2. 顶部悬浮控制卡片（特效信息、倒计时进度、快速切歌）
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xDD181B26)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {} // 阻止点击穿透
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // 第 1 行：序号名称 + 全屏切换按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "[${safeIndex + 1}/${allItems.size}]",
                                color = Color(0xFF818CF8),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = currentItem.cleanName,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // 全屏沉浸切换按钮
                        onTogglePureFullscreen?.let { toggle ->
                            OutlinedButton(
                                onClick = toggle,
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(if (isPureFullscreen) "🪟 退出全屏" else "📺 纯净全屏", fontSize = 11.sp, color = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 第 2 行：状态标签组（熔断隔离、L1缓存、巨型标记、播完即切、播后即焚）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (currentItem.sizeBytes > 10 * 1024 * 1024) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFEF4444).copy(alpha = 0.2f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text("🔥 巨型", color = Color(0xFFF87171), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (currentAllocatedBytes == 0L) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF3B82F6).copy(alpha = 0.25f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("⏳ 解析切片中...", color = Color(0xFF93C5FD), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        } else if (isCurrentEphemeral) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFDC2626).copy(alpha = 0.25f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (thresholdMb > 0) "⛔ 熔断隔离 (>${thresholdMb}M 不入L1)" else "⛔ 纯短暂播放 (不入L1)",
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF10B981).copy(alpha = 0.25f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("💾 L1享元缓存池", color = Color(0xFF6EE7B7), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (advanceIntervalSec == 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF6366F1).copy(alpha = 0.25f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text("🎬 播完即切", color = Color(0xFFA5B4FC), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (autoRecycleOnEnd) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFF59E0B).copy(alpha = 0.2f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text("🔥 播后即焚", color = Color(0xFFFBBF24), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 第 3 行：显存体积与时长数据
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "文件: ${currentItem.sizeText}",
                            color = if (currentItem.sizeBytes > 10 * 1024 * 1024) Color(0xFFF59E0B) else Color.LightGray,
                            fontSize = 11.sp,
                            fontWeight = if (currentItem.sizeBytes > 10 * 1024 * 1024) FontWeight.Bold else FontWeight.Normal
                        )
                        if (currentAllocatedBytes > 0) {
                            val allocMb = currentAllocatedBytes / (1024 * 1024f)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "显存: ${String.format(Locale.US, "%.1f", allocMb)}MB",
                                color = if (isCurrentEphemeral) Color(0xFFF87171) else Color(0xFF34D399),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        currentFramesInfo?.let { framesText ->
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "时长: $framesText",
                                color = Color(0xFF38BDF8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 自动轮播倒计时进度条
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFF6366F1),
                        trackColor = Color(0xFF334155),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 快速控制按钮行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                currentIndex = if (safeIndex > 0) safeIndex - 1 else allItems.size - 1
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("⏮ 上一个", fontSize = 11.sp, color = Color.White)
                        }

                        Button(
                            onClick = { isPlaying = !isPlaying },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPlaying) Color(0xFF4F46E5) else Color(0xFFD97706)
                            ),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(if (isPlaying) "⏸ 暂停轮播" else "▶ 继续轮播", fontSize = 11.sp, color = Color.White)
                        }

                        OutlinedButton(
                            onClick = {
                                currentIndex = (safeIndex + 1) % allItems.size
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("⏭ 下一个", fontSize = 11.sp, color = Color.White)
                        }

                        OutlinedButton(
                            onClick = {
                                if (allItems.size > 1) {
                                    var r = Random.nextInt(allItems.size)
                                    if (r == safeIndex) r = (safeIndex + 1) % allItems.size
                                    currentIndex = r
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("🔀 随机切", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }
            }
        }

        // 3. 底部悬浮控制台（时长选择、模式切换、562 款素材库检索、进场文本开关）
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xDD181B26)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {} // 阻止事件穿透
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // 第 1 行：切换节奏选择（播完即切原生时长 / 固定秒数）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("切换节奏:", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = advanceIntervalSec == 0,
                                onClick = { advanceIntervalSec = 0 },
                                label = {
                                    Text(
                                        "🎬 播完即切",
                                        fontSize = 11.sp,
                                        fontWeight = if (advanceIntervalSec == 0) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier.height(28.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF6366F1),
                                    selectedLabelColor = Color.White
                                )
                            )
                            listOf(3, 5, 8, 10).forEach { sec ->
                                FilterChip(
                                    selected = advanceIntervalSec == sec,
                                    onClick = { advanceIntervalSec = sec },
                                    label = { Text("${sec}s", fontSize = 11.sp) },
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 第 2 行：循环播放模式
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("循环模式:", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FullscreenLoopMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = loopMode == mode,
                                    onClick = { loopMode = mode },
                                    label = { Text("${mode.icon} ${mode.label}", fontSize = 11.sp) },
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 第 3 行：L1 缓存准入门槛（熔断隔离超大动效）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("L1 准入门槛:", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            val thresholdOptions = listOf(8 to "8MB", 16 to "16MB (默认)", 32 to "32MB", 0 to "不设限")
                            thresholdOptions.forEach { (mb, label) ->
                                FilterChip(
                                    selected = thresholdMb == mb,
                                    onClick = {
                                        thresholdMb = mb
                                        SvgaTemplateCache.clear() // 清空以便重新按照新门槛加载测试
                                    },
                                    label = {
                                        Text(
                                            text = label,
                                            fontSize = 10.sp,
                                            fontWeight = if (thresholdMb == mb) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    modifier = Modifier.height(28.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = if (mb == 16) Color(0xFF4F46E5) else Color(0xFF0284C7),
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 第 4 行：播后即焚 & 动态注入开关 & 素材库目录抽屉入口
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = autoRecycleOnEnd,
                                onCheckedChange = { autoRecycleOnEnd = it },
                                modifier = Modifier.height(26.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("🔥 播后即焚", color = Color.White, fontSize = 11.sp)

                            Spacer(modifier = Modifier.width(10.dp))

                            Switch(
                                checked = enableDynamicInject,
                                onCheckedChange = { enableDynamicInject = it },
                                modifier = Modifier.height(26.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("👑 动态注入", color = Color.White, fontSize = 11.sp)
                        }

                        Button(
                            onClick = { showDrawer = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF3B82F6)
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("📚 素材库 (${allItems.size})", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }

    // 4. 特效素材选择与搜索抽屉（ModalBottomSheet）
    if (showDrawer) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showDrawer = false },
            sheetState = sheetState,
            containerColor = Color(0xFF1E222D),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 4.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.Gray)
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${allItems.size}款全量特效素材库",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedButton(
                        onClick = { sortByDesc = !sortByDesc },
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(if (sortByDesc) "按体积降序 ⬇️" else "按名称升序 🔤", fontSize = 11.sp, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = searchKeyword,
                    onValueChange = { searchKeyword = it },
                    placeholder = { Text("搜索特效名称/路径 (如 car, 龙, 女神, rocket...)", fontSize = 12.sp, color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color.DarkGray
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 筛选与排序列表
                val filteredItems = remember(searchKeyword, sortByDesc, allItems) {
                    val list = if (searchKeyword.isBlank()) {
                        allItems
                    } else {
                        val q = searchKeyword.trim().lowercase()
                        allItems.filter { it.cleanName.lowercase().contains(q) || it.assetPath.lowercase().contains(q) }
                    }
                    if (sortByDesc) {
                        list.sortedByDescending { it.sizeBytes }
                    } else {
                        list.sortedBy { it.cleanName.lowercase() }
                    }
                }

                Text(
                    text = "匹配到 ${filteredItems.size} 款特效（点击即可立即切入播放）",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(filteredItems) { idx, item ->
                        val isCurrent = allItems.getOrNull(safeIndex)?.assetPath == item.assetPath
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrent) Color(0xFF4F46E5).copy(alpha = 0.4f) else Color(0xFF282D3C)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val realIdx = allItems.indexOfFirst { it.assetPath == item.assetPath }
                                    if (realIdx >= 0) {
                                        currentIndex = realIdx
                                    }
                                    showDrawer = false
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isCurrent) {
                                            Text("▶ ", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Text(
                                            text = "${idx + 1}. ${item.cleanName}",
                                            color = if (isCurrent) Color(0xFF38BDF8) else Color.White,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = item.assetPath,
                                        color = Color.Gray,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            if (item.sizeBytes > 10 * 1024 * 1024) Color(0xFFEF4444).copy(alpha = 0.25f)
                                            else Color(0xFF334155)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = item.sizeText,
                                        color = if (item.sizeBytes > 10 * 1024 * 1024) Color(0xFFF87171) else Color.LightGray,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Preview(name = "全屏特效播放器 - 标准模式", showBackground = true, heightDp = 760)
@Composable
private fun FullscreenLoopPlayerDemoPreview() {
    SvgaCoilTheme {
        FullscreenLoopPlayerDemo(
            isPureFullscreen = false,
            onTogglePureFullscreen = {}
        )
    }
}

@Preview(name = "全屏特效播放器 - 纯净全屏模式", showBackground = true, heightDp = 760)
@Composable
private fun FullscreenLoopPlayerPurePreview() {
    SvgaCoilTheme {
        FullscreenLoopPlayerDemo(
            isPureFullscreen = true,
            onTogglePureFullscreen = {}
        )
    }
}
