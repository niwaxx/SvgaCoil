package com.example.svgacoil.ui.demos

import android.content.Intent
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import coil3.load
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.svgacoil.LegacyViewDemoActivity
import com.example.svgacoil.ui.theme.SvgaCoilTheme
import com.opensource.svga.coil.svgaDynamic
import com.opensource.svga.coil.svgaRepeatCount

/**
 * 演示 1：静态图与 SVGA 动效统一 API
 */
@Composable
fun BasicUnifiedDemo() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "统一 AsyncImage / ImageView.load 接口",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "无论传入的是普通图片还是 .svga 动效，业务端都调用完全一致的 AsyncImage 或 imageView.load()，抹平格式差异。",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // 普通 SVGA 动效 1 (angel.svga)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AsyncImage(
                    model = "file:///android_asset/angel.svga",
                    contentDescription = "Angel SVGA",
                    modifier = Modifier
                        .size(140.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF0F4F8))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("angel.svga", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }

            // 普通 SVGA 动效 2 (rose.svga)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AsyncImage(
                    model = "file:///android_asset/rose.svga",
                    contentDescription = "Rose SVGA",
                    modifier = Modifier
                        .size(140.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF0F4F8))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("rose.svga", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }

        // 传统 Kotlin View 专场测试快捷入口卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "📱 传统 Kotlin View 体系测试页",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF1E293B)
                    )
                    Text(
                        text = "验证原生 ImageView.load()、ScaleType 矩阵、Animatable2 与 ListView 复用",
                        fontSize = 11.sp,
                        color = Color(0xFF475569)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        context.startActivity(Intent(context, LegacyViewDemoActivity::class.java))
                    }
                ) {
                    Text("打开专场", fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * 演示 2：在线 SVGA 动效（包含伪装为 .m4a 后缀的 SVGA）
 */
@Composable
fun OnlineSvgaDemo() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "网络在线 SVGA 解析（支持伪装格式）",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "无论服务端返回 Content-Type 是 application/octet-stream 还是 audio/mp4，或者文件名后缀为 .m4a，底层基于魔数嗅探（ZLIB / ZIP）均可 100% 正确识别并解析为 SVGA 动效。",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // 在线 SVGA 1: .svga
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AsyncImage(
                    model = "https://media.happychatyy.com/MTc3OTE3NzI5OTI4NSM3ODQjc3ZnYQ==.svga",
                    contentDescription = "Online SVGA 1",
                    modifier = Modifier
                        .size(150.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF0F4F8))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("在线 .svga 动效", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("1.3MB / 30帧", fontSize = 11.sp, color = Color.Gray)
            }

            // 在线 SVGA 2: .m4a
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AsyncImage(
                    model = "https://media.happychatyy.com/MTczOTg4NDAzMTA4OSM3MTgjbTRh.m4a",
                    contentDescription = "Online SVGA 2 (m4a)",
                    modifier = Modifier
                        .size(150.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF0F4F8))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("在线 .m4a 动效", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("144KB / 90帧 (伪装后缀)", fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}

/**
 * 演示 3：动态插槽（DynamicEntity）文本注入
 */
@Composable
fun DynamicInjectionDemo() {
    val context = LocalContext.current
    var dynamicTitle by remember { mutableStateOf("LV.99 战神") }
    var inputTitle by remember { mutableStateOf("LV.99 战神") }

    // 可选插槽：包含动效真实 key 以及通用全局浮层
    val slotOptions = listOf(
        "智能浮层(Overlay)" to "__svga_overlay_bottom__",
        "头顶光环(GuangHuan)" to "GuangHuan",
        "天使爱心(Xin)" to "Xin",
        "不存在的key(banner)" to "banner"
    )
    var selectedSlotIndex by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "享元二级缓存 + 动态插槽（智能图层映射）",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "• 图层精准锚定：若 SVGA 内存在该切片（如 GuangHuan/Xin），文字精准居中跟随 Sprite 运动\n• 智能降级浮层：若动效内无此 Key（如 banner），自动以高对比度胶囊悬浮框展示，100% 确保可见",
                    fontSize = 12.sp,
                    color = Color.DarkGray
                )
            }
        }

        // 动态注入请求
        val currentSlotKey = slotOptions[selectedSlotIndex].second
        val request = remember(dynamicTitle, currentSlotKey) {
            ImageRequest.Builder(context)
                .data("file:///android_asset/angel.svga")
                .svgaDynamic {
                    setDynamicText(dynamicTitle, currentSlotKey)
                }
                .svgaRepeatCount(0)
                .build()
        }

        AsyncImage(
            model = request,
            contentDescription = "Dynamic Angel",
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFF0F4F8))
        )

        Text(
            text = "当前绑定插槽: ${slotOptions[selectedSlotIndex].first}",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        // 插槽切换按钮组
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            slotOptions.forEachIndexed { index, (label, _) ->
                val isSelected = selectedSlotIndex == index
                Button(
                    onClick = { selectedSlotIndex = index },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = label.substringBefore("("),
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // 快捷预设文字
        val presetTitles = listOf("LV.99 战神", "榜一大哥", "欧皇附体", "恭喜发财 666")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            presetTitles.forEach { title ->
                OutlinedButton(
                    onClick = {
                        inputTitle = title
                        dynamicTitle = title
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                ) {
                    Text(text = title, fontSize = 10.sp, maxLines = 1)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputTitle,
                onValueChange = { inputTitle = it },
                label = { Text("动态文字注入内容") },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { dynamicTitle = inputTitle }) {
                Text("更新")
            }
        }
    }
}

/**
 * 演示：传统 Kotlin View (ImageView.load) 桥接预览与独立 Activity 入口
 */
@Composable
fun LegacyViewBridgeDemo() {
    val context = LocalContext.current
    var selectedUrl by remember { mutableStateOf("file:///android_asset/angel.svga") }
    var badgeText by remember { mutableStateOf("传统 ImageView.load") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "📱 传统 Kotlin View 体系 (ImageView.load)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "• 全局初始化：在 App.kt 中实现 SingletonImageLoader.Factory，Compose 与传统 View 共享同一实例\n" +
                            "• 原生兼容：直接调用 android.widget.ImageView.load(url) { svgaDynamic { ... } }\n" +
                            "• 穿透控制：提供 imageView.findSvgaDrawable() 扩展，一键操控 start/stop 与监听回调",
                    fontSize = 12.sp,
                    color = Color.DarkGray,
                    lineHeight = 17.sp
                )
            }
        }

        // 内嵌原生 android.widget.ImageView 实时预览
        AndroidView(
            factory = { ctx ->
                ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
            },
            update = { imageView ->
                imageView.load(selectedUrl) {
                    crossfade(true)
                    svgaDynamic {
                        setDynamicText(badgeText, "GuangHuan")
                    }
                }
            },
            modifier = Modifier
                .size(180.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0F172A))
                .padding(8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    selectedUrl = "file:///android_asset/angel.svga"
                    badgeText = "ImageView 天使"
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("切换天使 SVGA", fontSize = 11.sp)
            }
            OutlinedButton(
                onClick = {
                    selectedUrl = "file:///android_asset/rose.svga"
                    badgeText = "ImageView 玫瑰"
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("切换玫瑰 SVGA", fontSize = 11.sp)
            }
        }

        Button(
            onClick = {
                context.startActivity(Intent(context, LegacyViewDemoActivity::class.java))
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
        ) {
            Text(
                text = "🚀 启动纯原生 Kotlin View 测试专场 Activity (含 ListView 50项压测)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Preview(name = "1. 动静统一 API 预览", showBackground = true)
@Composable
private fun BasicUnifiedDemoPreview() {
    SvgaCoilTheme {
        BasicUnifiedDemo()
    }
}

@Preview(name = "2. 在线 SVGA 动效预览", showBackground = true)
@Composable
private fun OnlineSvgaDemoPreview() {
    SvgaCoilTheme {
        OnlineSvgaDemo()
    }
}

@Preview(name = "3. 动态插槽文本注入预览", showBackground = true)
@Composable
private fun DynamicInjectionDemoPreview() {
    SvgaCoilTheme {
        DynamicInjectionDemo()
    }
}

@Preview(name = "4. 传统 View 桥接页预览", showBackground = true)
@Composable
private fun LegacyViewBridgeDemoPreview() {
    SvgaCoilTheme {
        LegacyViewBridgeDemo()
    }
}
