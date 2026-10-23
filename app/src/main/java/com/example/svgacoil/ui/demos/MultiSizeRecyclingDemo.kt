package com.example.svgacoil.ui.demos

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.example.svgacoil.ui.theme.SvgaCoilTheme
import com.opensource.svga.coil.SvgaResolutionTier
import com.opensource.svga.coil.SvgaTemplateCache
import com.opensource.svga.coil.TemplateCacheEntry
import kotlinx.coroutines.delay

private data class TierItemConfig(
    val dimensionDp: Int,
    val tier: SvgaResolutionTier,
    val roleTitle: String,
    val description: String
)

/**
 * 演示：同一个 SVGA 在不同大小尺寸 View 上展示的内存分级与复用架构
 */
@Composable
fun MultiSizeRecyclingDemo() {
    val context = LocalContext.current

    // 可切换的动效测试素材（涵盖常规动效、18MB 巨无霸动效与网络动效）
    val sourceOptions = listOf(
        "本地 angel (209K)" to "file:///android_asset/angel.svga",
        "皇家金冠 (18MB巨无霸)" to "file:///android_asset/gift_royal_gold_18m.svga",
        "星空超清框 (15MB超大)" to "file:///android_asset/avatar_frame_hd_galaxy.svga",
        "本地 rose (307K)" to "file:///android_asset/rose.svga",
        "在线 .svga (1.3MB)" to "https://media.happychatyy.com/MTc3OTE3NzI5OTI4NSM3ODQjc3ZnYQ==.svga",
        "在线 .m4a (144KB)" to "https://media.happychatyy.com/MTczOTg4NDAzMTA4OSM3MTgjbTRh.m4a"
    )
    var selectedSourceIndex by remember { mutableIntStateOf(0) }
    val currentSource = sourceOptions[selectedSourceIndex].second

    // 演练模式：0: 全阶梯同时展示；1: 先加载大尺寸 (260dp)；2: 先加载小尺寸 (40dp)
    var demoMode by remember { mutableIntStateOf(0) }

    // 缓存池快照列表（每 500ms 刷新；Preview 模式下展示示例缓存池条目）
    val isInspection = LocalInspectionMode.current
    var snapshotEntries by remember {
        mutableStateOf(
            if (isInspection) {
                listOf(
                    TemplateCacheEntry(
                        fullKey = "file:///android_asset/angel.svga#tier=FULL",
                        baseKey = "file:///android_asset/angel.svga",
                        tier = SvgaResolutionTier.FULL,
                        allocatedBytes = 1480 * 1024L
                    ),
                    TemplateCacheEntry(
                        fullKey = "file:///android_asset/rose.svga#tier=THUMBNAIL",
                        baseKey = "file:///android_asset/rose.svga",
                        tier = SvgaResolutionTier.THUMBNAIL,
                        allocatedBytes = 96 * 1024L
                    )
                )
            } else {
                emptyList()
            }
        )
    }
    LaunchedEffect(Unit) {
        while (true) {
            snapshotEntries = SvgaTemplateCache.getSnapshotEntries()
            delay(500)
        }
    }

    val ladderItems = remember {
        listOf(
            TierItemConfig(
                dimensionDp = 40,
                tier = SvgaResolutionTier.THUMBNAIL,
                roleTitle = "微型角标 / 头像挂件",
                description = "40dp 极小尺寸。单独解码时采样率达 8x，显存仅占 1/64；若内存中已有大图则直接共享复用。"
            ),
            TierItemConfig(
                dimensionDp = 72,
                tier = SvgaResolutionTier.THUMBNAIL,
                roleTitle = "列表徽章 / 迷你动效",
                description = "72dp 挂件。同属 THUMBNAIL 桶 (<=96dp)，向下兼容已有的高清 FULL 模板。"
            ),
            TierItemConfig(
                dimensionDp = 120,
                tier = SvgaResolutionTier.MEDIUM,
                roleTitle = "公屏卡片 / 气泡动效",
                description = "120dp 卡片。归属 MEDIUM 桶 (96~240dp)，适度下采样兼顾画质与低显存。"
            ),
            TierItemConfig(
                dimensionDp = 180,
                tier = SvgaResolutionTier.MEDIUM,
                roleTitle = "中型弹窗 / 礼物预览",
                description = "180dp 弹窗。同属 MEDIUM 桶，若存在 FULL 模板则向下兼容零显存复用。"
            ),
            TierItemConfig(
                dimensionDp = 260,
                tier = SvgaResolutionTier.FULL,
                roleTitle = "全屏送礼 / 展台横幅",
                description = "260dp 超大尺寸。归属 FULL 桶 (>240dp)，全高清 1:1 解码，充当所有小容器的向下兼容母版！"
            )
        )
    }

    val displayItems = when (demoMode) {
        1 -> ladderItems.filter { it.dimensionDp >= 240 } // 仅大图
        2 -> ladderItems.filter { it.dimensionDp <= 40 }  // 仅小图
        else -> ladderItems
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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "📐 同一 SVGA 多尺寸展示时的内存设计",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "1. 几何骨架 100% 共享：MovieEntity 动画曲线、矩阵变换无视尺寸全局仅存 1 份，零冗余。\n" +
                            "2. 切片分辨率分级 (Tier)：按 THUMBNAIL (<=96dp)、MEDIUM (<=240dp)、FULL (>240dp) 归类，小挂件内存缩减至 1/64。\n" +
                            "3. 向下兼容复用 (Downwards Reuse)：若内存已有大图模板，后续小尺寸 100% 直接复用大图（Canvas 缩小绘制无损锐利），零二次解码、零新增显存！",
                        fontSize = 12.sp,
                        color = Color.DarkGray,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        // 测试素材切换栏（支持水平滑动选择）
        item {
            Text(text = "测试素材切换（含 18MB 巨无霸）：", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(sourceOptions.size) { index ->
                    val (label, _) = sourceOptions[index]
                    val isSelected = selectedSourceIndex == index
                    Button(
                        onClick = { selectedSourceIndex = index },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(text = label, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }

        // 核心演练动作按钮
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        SvgaTemplateCache.clear()
                        demoMode = 1 // 仅先看大图
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Text("① 先加载大图 (260dp)", fontSize = 10.sp)
                }

                OutlinedButton(
                    onClick = {
                        SvgaTemplateCache.clear()
                        demoMode = 2 // 仅先看小图
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Text("② 先加载小图 (40dp)", fontSize = 10.sp)
                }

                Button(
                    onClick = { demoMode = 0 },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Text("③ 全阶梯对比展示", fontSize = 10.sp)
                }
            }
        }

        // 实时享元模板池快照监控
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🧩 享元缓存池条目 (${snapshotEntries.size} 项)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "切片显存: ${snapshotEntries.sumOf { it.allocatedBytes } / 1024} KB",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (snapshotEntries.isEmpty()) {
                        Text("当前模板池为空", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                    } else {
                        Column(modifier = Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            snapshotEntries.forEach { entry ->
                                val tierColor = when (entry.tier) {
                                    SvgaResolutionTier.FULL -> Color(0xFFEF4444)
                                    SvgaResolutionTier.MEDIUM -> Color(0xFFF59E0B)
                                    else -> Color(0xFF10B981)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(tierColor.copy(alpha = 0.2f))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = entry.tier?.name ?: "LEGACY",
                                                color = tierColor,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = entry.fullKey.substringAfterLast("/"),
                                            fontSize = 11.sp,
                                            maxLines = 1
                                        )
                                    }
                                    Text(
                                        text = "${entry.allocatedBytes / 1024} KB",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.DarkGray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        items(displayItems.size) { index ->
            val item = displayItems[index]
            val tierColor = when (item.tier) {
                SvgaResolutionTier.FULL -> Color(0xFFEF4444)
                SvgaResolutionTier.MEDIUM -> Color(0xFFF59E0B)
                SvgaResolutionTier.THUMBNAIL -> Color(0xFF10B981)
            }

            val density = context.resources.displayMetrics.density
            val itemPx = (item.dimensionDp * density).toInt()

            val request = remember(currentSource, itemPx) {
                ImageRequest.Builder(context)
                    .data(currentSource)
                    .size(itemPx, itemPx)
                    .build()
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                if (item.dimensionDp >= 200) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(item.dimensionDp.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFE2E8F0)),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = request,
                                contentDescription = item.roleTitle,
                                modifier = Modifier.size(item.dimensionDp.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "${item.dimensionDp}dp - ${item.roleTitle}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(tierColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = item.tier.name,
                                    color = tierColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.description,
                            fontSize = 11.sp,
                            color = Color.Gray,
                            lineHeight = 15.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(item.dimensionDp.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFE2E8F0)),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = request,
                                contentDescription = item.roleTitle,
                                modifier = Modifier.size(item.dimensionDp.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${item.dimensionDp}dp - ${item.roleTitle}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(tierColor.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = item.tier.name,
                                        color = tierColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.description,
                                fontSize = 11.sp,
                                color = Color.Gray,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Preview(name = "多尺寸分级与向下兼容复用预览", showBackground = true, heightDp = 920)
@Composable
private fun MultiSizeRecyclingDemoPreview() {
    SvgaCoilTheme {
        MultiSizeRecyclingDemo()
    }
}
