package com.example.svgacoil

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.svgacoil.ui.demos.AvatarFrameListDemo
import com.example.svgacoil.ui.demos.BasicUnifiedDemo
import com.example.svgacoil.ui.demos.DynamicInjectionDemo
import com.example.svgacoil.ui.demos.FullscreenLoopPlayerDemo
import com.example.svgacoil.ui.demos.LegacyViewBridgeDemo
import com.example.svgacoil.ui.demos.ListRecyclingDemo
import com.example.svgacoil.ui.demos.MultiSizeRecyclingDemo
import com.example.svgacoil.ui.demos.OnlineSvgaDemo
import com.example.svgacoil.ui.theme.SvgaCoilTheme
import com.opensource.svga.coil.SvgaTemplateCache
import kotlinx.coroutines.delay

/**
 * 主演示入口 Activity。
 * 全局 Coil ImageLoader 已在 [App] 中通过 [coil3.SingletonImageLoader.Factory] 规范初始化。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SvgaCoilTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DemoScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        "动静统一",
        "📱 传统View",
        "在线动效",
        "动态注入",
        "多尺寸复用",
        "列表压测",
        "🎬 全屏特效",
        "👑 动态头像框"
    )

    // 全局实时堆内存与享元模板指标（500ms 实时刷新；Preview 模式下使用示例指标）
    val isInspection = LocalInspectionMode.current
    var usedMemoryMb by remember { mutableLongStateOf(if (isInspection) 76L else 0L) }
    var maxMemoryMb by remember { mutableLongStateOf(if (isInspection) 512L else 0L) }
    var templateCount by remember { mutableIntStateOf(if (isInspection) 4 else 0) }
    var templateKb by remember { mutableLongStateOf(if (isInspection) 4280L else 0L) }

    LaunchedEffect(Unit) {
        while (true) {
            val runtime = Runtime.getRuntime()
            usedMemoryMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
            maxMemoryMb = runtime.maxMemory() / (1024 * 1024)
            templateCount = SvgaTemplateCache.entryCount()
            templateKb = SvgaTemplateCache.totalAllocatedBytes() / 1024
            delay(500)
        }
    }

    var isPureFullscreen by remember { mutableStateOf(false) }

    if (selectedTab == 6 && isPureFullscreen) {
        // 🌟 100% 纯净全屏沉浸模式：占满整屏，隐藏外层全局栏
        FullscreenLoopPlayerDemo(
            isPureFullscreen = true,
            onTogglePureFullscreen = { isPureFullscreen = false }
        )
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            // 顶部标题行 + 传统 View Activity 快速跳转按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Coil-SVGA 高性能动效方案",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Button(
                    onClick = {
                        context.startActivity(Intent(context, LegacyViewDemoActivity::class.java))
                    },
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                ) {
                    Text("📱 传统View专场", fontSize = 11.sp)
                }
            }

            // 🌟 固定展示在标题下方的实时堆内存与模板缓存状态栏
            GlobalMemoryMonitorBar(
                usedMb = usedMemoryMb,
                maxMb = maxMemoryMb,
                templateCount = templateCount,
                templateKb = templateKb
            )

            PrimaryScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 12.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> BasicUnifiedDemo()
                1 -> LegacyViewBridgeDemo()
                2 -> OnlineSvgaDemo()
                3 -> DynamicInjectionDemo()
                4 -> MultiSizeRecyclingDemo()
                5 -> ListRecyclingDemo()
                6 -> FullscreenLoopPlayerDemo(
                    isPureFullscreen = false,
                    onTogglePureFullscreen = { isPureFullscreen = true }
                )
                7 -> AvatarFrameListDemo()
            }
        }
    }
}

/**
 * 固定在顶部的实时内存监控看板（跨所有 Tab 全局可见）
 */
@Composable
fun GlobalMemoryMonitorBar(
    usedMb: Long,
    maxMb: Long,
    templateCount: Int,
    templateKb: Long
) {
    val memoryRatio = if (maxMb > 0) (usedMb.toFloat() / maxMb.toFloat()).coerceIn(0f, 1f) else 0f
    val memoryPercent = (memoryRatio * 100).toInt()

    val statusColor = when {
        memoryRatio > 0.8f -> Color(0xFFEF4444) // 红色警戒
        memoryRatio > 0.5f -> Color(0xFFF59E0B) // 橙色注意
        else -> Color(0xFF10B981) // 绿色健康
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "JVM 堆内存: ${usedMb}MB / ${maxMb}MB ($memoryPercent%)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { System.gc() },
                        modifier = Modifier.height(26.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("手动 GC", fontSize = 10.sp)
                    }

                    OutlinedButton(
                        onClick = { SvgaTemplateCache.clear() },
                        modifier = Modifier.height(26.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("清缓存", fontSize = 10.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 内存比例指示条
            LinearProgressIndicator(
                progress = { memoryRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = statusColor,
                trackColor = Color(0xFFE2E8F0)
            )

            Spacer(modifier = Modifier.height(4.dp))

            val thresholdText = if (SvgaTemplateCache.maxSingleObjectCacheableBytes == Long.MAX_VALUE) {
                "无限制"
            } else {
                "${SvgaTemplateCache.maxSingleObjectCacheableBytes / (1024 * 1024)}MB"
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "🧩 模板: $templateCount 个 (${templateKb}KB) | L1门槛: $thresholdText",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                Text(
                    text = "离屏自动休眠: 已生效",
                    fontSize = 11.sp,
                    color = Color(0xFF0284C7),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Preview(name = "主页整体预览", showBackground = true, showSystemUi = true)
@Composable
private fun DemoScreenPreview() {
    SvgaCoilTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            DemoScreen(modifier = Modifier.padding(innerPadding))
        }
    }
}

@Preview(name = "内存监控看板 - 健康状态", showBackground = true)
@Composable
private fun GlobalMemoryMonitorBarHealthyPreview() {
    SvgaCoilTheme {
        GlobalMemoryMonitorBar(
            usedMb = 64L,
            maxMb = 512L,
            templateCount = 3,
            templateKb = 2450L
        )
    }
}

@Preview(name = "内存监控看板 - 高水位警戒", showBackground = true)
@Composable
private fun GlobalMemoryMonitorBarWarningPreview() {
    SvgaCoilTheme {
        GlobalMemoryMonitorBar(
            usedMb = 430L,
            maxMb = 512L,
            templateCount = 12,
            templateKb = 38900L
        )
    }
}
