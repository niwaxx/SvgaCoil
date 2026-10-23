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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.svgacoil.ui.theme.SvgaCoilTheme
import com.opensource.svga.coil.svgaRepeatCount
import java.util.Locale

/**
 * 动态头像框资产配置
 */
data class AvatarFrameAsset(
    val id: String,
    val name: String,
    val url: String?,
    val sizeText: String,
    val tagColor: Color,
    val isHeavy: Boolean = false
)

/**
 * 动静混合排行榜成员
 */
data class RankMember(
    val id: Int,
    val rank: Int,
    val nickname: String,
    val avatarUrl: String,
    val frameAsset: AvatarFrameAsset,
    val vipTitle: String,
    val vipColor: Color,
    val heatScore: String,
    val bio: String,
    val isLiveStreaming: Boolean = false
)

/**
 * 👑 核心动静混合组件：圆形静态头像 + SVGA 动态头像框
 */
@Composable
fun AvatarWithSvgaFrame(
    avatarUrl: String,
    frameUrl: String?,
    avatarSizeDp: Int = 50,
    frameScale: Float = 1.35f,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val frameSizeDp = (avatarSizeDp * frameScale).dp

    Box(
        modifier = modifier.size(frameSizeDp),
        contentAlignment = Alignment.Center
    ) {
        // 1. 底层：静态圆形头像（普通图片解码，带抗锯齿与圆角裁剪）
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(avatarUrl)
                .crossfade(true)
                .build(),
            contentDescription = "User Avatar",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(avatarSizeDp.dp)
                .clip(CircleShape)
                .background(Color(0xFFCBD5E1))
        )

        // 2. 上层：SVGA 动态头像框（动效解码，无限循环，离屏自动休眠）
        if (!frameUrl.isNullOrEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(frameUrl)
                    .svgaRepeatCount(0)
                    .build(),
                contentDescription = "Avatar Frame",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(frameSizeDp)
                    .clipToBounds()
            )
        }
    }
}

/**
 * 👑 演示：头像框 + 头像的图片 SVGA 动静混合列表模式
 * 模拟直播间/语音房排行榜与成员麦位列表，展示数十位用户佩戴不同动态头像框的流畅渲染与零 OOM
 */
@Composable
fun AvatarFrameListDemo() {
    // 头像框素材库
    val frameLibrary = remember {
        listOf(
            AvatarFrameAsset(
                "light",
                "炫光流转框",
                "file:///android_asset/avatar_frame_light.svga",
                "278KB",
                Color(0xFF059669)
            ),
            AvatarFrameAsset(
                "hd",
                "星空粒子框",
                "file:///android_asset/avatar_frame_hd_galaxy.svga",
                "15MB 极清",
                Color(0xFFDC2626),
                isHeavy = true
            ),
            AvatarFrameAsset(
                "angel",
                "天使双翼框",
                "file:///android_asset/angel.svga",
                "209KB",
                Color(0xFF2563EB)
            ),
            AvatarFrameAsset(
                "rose",
                "浪漫玫瑰框",
                "file:///android_asset/rose.svga",
                "307KB",
                Color(0xFFDB2777)
            ),
            AvatarFrameAsset(
                "yaoji",
                "妩媚妖姬框",
                "file:///android_asset/avatar_frame_charm.svga",
                "283KB",
                Color(0xFF9333EA)
            ),
            AvatarFrameAsset(
                "gold_crown",
                "尊享金冠框",
                "file:///android_asset/gift_royal_gold_18m.svga",
                "18MB 豪华",
                Color(0xFFD97706),
                isHeavy = true
            ),
            AvatarFrameAsset("none", "无框(纯图片)", null, "0KB", Color.Gray)
        )
    }

    // 本地真实头像素材池（规范化命名）
    val localAvatarPool = remember {
        listOf(
            "file:///android_asset/avatars/avatar_01_lucky_star.png",
            "file:///android_asset/avatars/avatar_02_new_year.png",
            "file:///android_asset/avatars/avatar_03_lantern.png",
            "file:///android_asset/avatars/avatar_04_royal_crown.png",
            "file:///android_asset/avatars/avatar_05_charm_queen.png",
            "file:///android_asset/avatars/avatar_06_cute_kitty.png",
            "file:///android_asset/avatars/avatar_07_couple_boy.png",
            "file:///android_asset/avatars/avatar_08_kitty_badge.png",
            "file:///android_asset/avatars/avatar_09_spring_wing.png",
            "file:///android_asset/avatars/avatar_10_silver_moon.png"
        )
    }

    // 交互状态
    var wearModeIndex by remember { mutableIntStateOf(0) } // 0: 100%全员佩戴, 1: 60%真实混排, 2: 仅Top3佩戴
    var avatarSizeDp by remember { mutableIntStateOf(50) } // 44, 50, 60
    var refreshSeed by remember { mutableIntStateOf(0) }
    var isPanelExpanded by remember { mutableStateOf(false) }

    // 用户数据生成
    val memberList = remember(wearModeIndex, refreshSeed) {
        val names = listOf(
            "👑 帝王·夜华", "✨ 青丘白浅", "💎 冷月剑仙", "🌸 洛神赋", "🚀 银河漫游者",
            "🔥 战神归来", "🐉 傲视苍穹", "⭐ 摘星揽月", "🎵 绝美歌姬", "🍭 甜心喵喵",
            "⚡ 极速闪电", "🏹 风暴之眼", "🛡️ 不灭狂神", "🌹 暗夜蔷薇", "🍀 幸运锦鲤"
        )
        val vips = listOf(
            "SVIP 9" to Color(0xFFFFB703),
            "公爵" to Color(0xFFEC4899),
            "至尊" to Color(0xFFF97316),
            "钻石" to Color(0xFF0284C7),
            "VIP 5" to Color(0xFF10B981)
        )
        val bios = listOf(
            "👑 全服第一神豪，守护整个直播间",
            "✨ 正在麦位上与主播连麦互动中...",
            "🔥 刚刚赠送了 10 组浪漫城堡！",
            "💎 尊贵 VIP 会员，享有进场特效",
            "🌟 人气常驻粉丝，今日榜前列"
        )

        List(60) { index ->
            val rank = index + 1
            val nickname = if (index < names.size) names[index] else "贵宾_${1000 + index}"
            val avatar = localAvatarPool[index % localAvatarPool.size]
            val vip = vips[index % vips.size]
            val bio = bios[index % bios.size]
            val score = String.format(Locale.US, "%,d", (100 - index) * 128500 + 999)

            // 根据佩戴模式分配头像框
            val frame = when (wearModeIndex) {
                0 -> {
                    val validFrames = frameLibrary.filter { it.url != null }
                    validFrames[(index + refreshSeed) % validFrames.size]
                }

                1 -> {
                    if (index % 10 < 6) {
                        val validFrames = frameLibrary.filter { it.url != null }
                        validFrames[(index + refreshSeed) % validFrames.size]
                    } else {
                        frameLibrary.last()
                    }
                }

                2 -> {
                    if (rank <= 3) {
                        frameLibrary[rank - 1]
                    } else {
                        frameLibrary.last()
                    }
                }

                else -> frameLibrary.first()
            }

            RankMember(
                id = index,
                rank = rank,
                nickname = nickname,
                avatarUrl = avatar,
                frameAsset = frame,
                vipTitle = vip.first,
                vipColor = vip.second,
                heatScore = score,
                bio = bio,
                isLiveStreaming = index % 4 == 0
            )
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))

            // 顶部技术说明与控制看板
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "👑 动静头像混合列表 (Avatar + SVGA Frame)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "底层静态头像 + 顶层SVGA动态框 | 享元复用零OOM",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }

                        OutlinedButton(
                            onClick = { isPanelExpanded = !isPanelExpanded },
                            modifier = Modifier.height(26.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                        ) {
                            Text(if (isPanelExpanded) "收起 🔼" else "配置 ⚙️", fontSize = 10.sp)
                        }
                    }

                    if (isPanelExpanded) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "• 动静双层分离：底层圆形普通图片解码，顶层动态头像框由 Coil-SVGA 离屏休眠播放\n• 享元模板复用：无论 60 位还是 100 位用户，同名头像框仅占用一份底层切片与模板\n• 严丝合缝对齐：外扩 1.35x 契合光晕，兼顾翅膀、皇冠与外延粒子动效",
                            fontSize = 11.sp,
                            color = Color.DarkGray,
                            lineHeight = 15.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("头像尺寸:", fontSize = 11.sp, color = Color.DarkGray)
                            listOf(
                                44 to "紧凑(44dp)",
                                50 to "标准(50dp)",
                                60 to "大气(60dp)"
                            ).forEach { (dpVal, label) ->
                                FilterChip(
                                    selected = avatarSizeDp == dpVal,
                                    onClick = { avatarSizeDp = dpVal },
                                    label = { Text(label, fontSize = 10.sp) },
                                    modifier = Modifier.height(26.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 穿戴模式切换
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            listOf(
                                "🔥 全员动态",
                                "✨ 60%混排",
                                "👑 仅Top3"
                            ).forEachIndexed { idx, label ->
                                FilterChip(
                                    selected = wearModeIndex == idx,
                                    onClick = { wearModeIndex = idx },
                                    label = { Text(label, fontSize = 10.sp) },
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { refreshSeed++ },
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text(
                                "🔀 随机换装",
                                fontSize = 10.sp,
                                lineHeight = 10.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // 成员列表
        itemsIndexed(memberList, key = { _, item -> item.id }) { _, member ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when (member.rank) {
                        1 -> Color(0xFFFFFBEB) // 冠军金
                        2 -> Color(0xFFF8FAFC) // 亚军银
                        3 -> Color(0xFFFFF7ED) // 季军铜
                        else -> MaterialTheme.colorScheme.surface
                    }
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (member.rank <= 3) 2.dp else 0.5.dp),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. 排名标识 (固定 28dp)
                    Box(
                        modifier = Modifier.width(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when (member.rank) {
                            1 -> Text("🥇", fontSize = 18.sp)
                            2 -> Text("🥈", fontSize = 18.sp)
                            3 -> Text("🥉", fontSize = 18.sp)
                            else -> Text(
                                text = "${member.rank}",
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // 2. 动静混合头像（静态圆形头像 + SVGA 动态头像框）
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size((avatarSizeDp * 1.35f).dp)
                    ) {
                        AvatarWithSvgaFrame(
                            avatarUrl = member.avatarUrl,
                            frameUrl = member.frameAsset.url,
                            avatarSizeDp = avatarSizeDp,
                            frameScale = 1.35f
                        )

                        // LIVE 正在连麦徽标
                        if (member.isLiveStreaming) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFFEF4444))
                                    .padding(horizontal = 3.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    "LIVE",
                                    color = Color.White,
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // 3. 用户信息
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = member.nickname,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(member.vipColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = member.vipTitle,
                                    color = member.vipColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // 佩戴框名称与属性
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (member.frameAsset.url != null) {
                                Text(
                                    text = member.frameAsset.name,
                                    color = member.frameAsset.tagColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "(${member.frameAsset.sizeText})",
                                    color = Color.Gray,
                                    fontSize = 9.sp,
                                    maxLines = 1
                                )
                            } else {
                                Text(
                                    text = "普通静态圆形头像",
                                    color = Color.Gray,
                                    fontSize = 10.sp,
                                    maxLines = 1
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = member.bio,
                            color = Color.DarkGray,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // 4. 右侧热度值
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "🔥 ${member.heatScore}",
                            color = Color(0xFFEF4444),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "热力值",
                            color = Color.Gray,
                            fontSize = 9.sp,
                            maxLines = 1
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

@Preview(name = "单体动静混合头像框组件", showBackground = true)
@Composable
private fun AvatarWithSvgaFramePreview() {
    SvgaCoilTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            AvatarWithSvgaFrame(
                avatarUrl = "file:///android_asset/avatars/avatar_05_charm_queen.png",
                frameUrl = "file:///android_asset/avatar_frame_light.svga",
                avatarSizeDp = 60,
                frameScale = 1.35f
            )
        }
    }
}

@Preview(name = "动静混合排行榜列表预览", showBackground = true)
@Composable
private fun AvatarFrameListDemoPreview() {
    SvgaCoilTheme {
        AvatarFrameListDemo()
    }
}
