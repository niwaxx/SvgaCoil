package com.example.svgacoil.ui.demos

import android.content.Context
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.example.svgacoil.ui.theme.SvgaCoilTheme
import com.opensource.svga.coil.svgaDynamic
import java.util.Locale

data class SvgaSampleItem(
    val url: String,
    val displayName: String,
    val sizeText: String,
    val typeTag: String,
    val tagColor: Color,
    val category: Int // 0: 全部, 1: 巨型动效(>=8MB), 2: 伪装格式(.mp4/.m4a), 3: 常规与微型
)

fun loadAllSvgaSamples(context: Context): List<SvgaSampleItem> {
    val list = mutableListOf<SvgaSampleItem>()

    // 1. 网络在线动效
    list.add(
        SvgaSampleItem(
            url = "https://media.happychatyy.com/MTc3OTE3NzI5OTI4NSM3ODQjc3ZnYQ==.svga",
            displayName = "在线 .svga (1.3MB)",
            sizeText = "在线网络 1.3MB / 30帧",
            typeTag = "在线动效",
            tagColor = Color(0xFF0284C7),
            category = 3
        )
    )
    list.add(
        SvgaSampleItem(
            url = "https://media.happychatyy.com/MTczOTg4NDAzMTA4OSM3MTgjbTRh.m4a",
            displayName = "在线 .m4a 伪装 (144KB)",
            sizeText = "在线音频后缀 144KB / 90帧",
            typeTag = "伪装 .m4a",
            tagColor = Color(0xFFD97706),
            category = 2
        )
    )

    // 2. 本地 assets 素材自动发现与分类
    try {
        val assetFiles = context.assets.list("") ?: emptyArray()
        assetFiles.filter { name ->
            name.endsWith(".svga", ignoreCase = true) ||
                name.endsWith(".mp4", ignoreCase = true) ||
                name.endsWith(".m4a", ignoreCase = true)
        }.sortedByDescending { name ->
            try {
                context.assets.open(name).use { it.available().toLong() }
            } catch (_: Throwable) {
                0L
            }
        }.forEach { name ->
            val size = try {
                context.assets.open(name).use { it.available().toLong() }
            } catch (_: Throwable) {
                0L
            }
            val sizeKb = size / 1024
            val sizeMb = sizeKb / 1024f

            val (typeTag, tagColor, category) = when {
                sizeMb >= 8.0f -> Triple("巨型 ${String.format(Locale.US, "%.1f", sizeMb)}M", Color(0xFFDC2626), 1)
                name.endsWith(".mp4", ignoreCase = true) || name.endsWith(".m4a", ignoreCase = true) -> Triple("伪装格式", Color(0xFF7C3AED), 2)
                sizeKb <= 100 -> Triple("微型 ${sizeKb}K", Color(0xFF0D9488), 3)
                else -> Triple("常规 ${sizeKb}K", Color(0xFF16A34A), 3)
            }

            list.add(
                SvgaSampleItem(
                    url = "file:///android_asset/$name",
                    displayName = name,
                    sizeText = if (sizeMb >= 1.0f) "${String.format(Locale.US, "%.1f", sizeMb)}MB 本地动效" else "${sizeKb}KB 本地动效",
                    typeTag = typeTag,
                    tagColor = tagColor,
                    category = category
                )
            )
        }
    } catch (e: Throwable) {
        e.printStackTrace()
    }

    return list
}

/**
 * 演示：全素材真实业务级高频混排与防 OOM 极限压测
 */
@Composable
fun ListRecyclingDemo() {
    val context = LocalContext.current
    val allSamples = remember(context) { loadAllSvgaSamples(context) }

    // 0: 全量混排, 1: 极限巨型(8M~18M), 2: 伪装格式, 3: 常规轻量
    var selectedCategory by remember { mutableIntStateOf(0) }
    val categories = listOf("🔥 全量混排", "💥 极限巨无霸", "🎭 伪装格式", "🍃 常规动效")

    val filteredSamples = remember(selectedCategory, allSamples) {
        when (selectedCategory) {
            1 -> allSamples.filter { it.category == 1 }
            2 -> allSamples.filter { it.category == 2 }
            3 -> allSamples.filter { it.category == 3 }
            else -> allSamples
        }
    }

    // 扩充至 100 项高频大列表
    val totalCount = 100
    val listItems = remember(filteredSamples) {
        if (filteredSamples.isEmpty()) emptyList()
        else List(totalCount) { index ->
            filteredSamples[index % filteredSamples.size]
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🚀 全素材极限压测看板",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "素材库: ${allSamples.size} 款 | 混排: ${listItems.size} 项",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• 真实素材库：已接入 assets 目录下全部 ${allSamples.size} 款动效（涵盖 18MB 全屏礼物巨无霸、伪装格式、微型挂件、在线网络动效）\n" +
                            "• 智能时钟解耦：离屏 250ms 自动断开 Choreographer 时钟，滑入无缝续播\n" +
                            "• 享元弹性缓存：内存受 LRU 25% 堆配额保护，高频快速滑屏永不 OOM",
                        fontSize = 12.sp,
                        color = Color.DarkGray,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 模式过滤筛选栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.forEachIndexed { index, title ->
                    val isSelected = selectedCategory == index
                    Button(
                        onClick = { selectedCategory = index },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        ),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                    ) {
                        Text(text = title, fontSize = 10.sp, maxLines = 1)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "100 项高频混排列表（请观察顶部 JVM 堆内存与切片显存）：",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }

        val density = context.resources.displayMetrics.density
        val badgeSizePx = (56 * density).toInt()

        items(listItems.size) { index ->
            val sample = listItems[index]
            val itemRequest = remember(sample.url, index, badgeSizePx) {
                ImageRequest.Builder(context)
                    .data(sample.url)
                    .size(badgeSizePx, badgeSizePx)
                    .svgaDynamic {
                        setDynamicText("No.${index + 1}", "banner")
                    }
                    .build()
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE2E8F0)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = itemRequest,
                            contentDescription = sample.displayName,
                            modifier = Modifier.size(56.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "用户 ${index + 1} 号动效挂件",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(sample.tagColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = sample.typeTag,
                                    color = sample.tagColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = sample.displayName,
                            fontSize = 11.sp,
                            color = Color.DarkGray,
                            maxLines = 1
                        )
                        Text(
                            text = sample.sizeText,
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Preview(name = "100项高频混排列表压测预览", showBackground = true)
@Composable
private fun ListRecyclingDemoPreview() {
    SvgaCoilTheme {
        ListRecyclingDemo()
    }
}
