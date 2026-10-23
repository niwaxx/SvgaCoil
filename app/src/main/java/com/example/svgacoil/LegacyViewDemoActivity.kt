package com.example.svgacoil

import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.Animatable2
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import coil3.load
import coil3.request.crossfade
import com.opensource.svga.coil.SvgaTemplateCache
import com.opensource.svga.coil.findSvgaDrawable
import com.opensource.svga.coil.svgaDynamic
import com.opensource.svga.coil.svgaEphemeral
import com.opensource.svga.coil.svgaRepeatCount

/**
 * 传统 Kotlin Android View 体系（ImageView.load + ViewHolder 列表复用 + Animatable2 控制）测试与展示页面。
 * 全程采用原生 View 构建（零 Compose 依赖），验证 Coil-SVGA 在传统 View 架构下的完整性与性能表现。
 */
class LegacyViewDemoActivity : ComponentActivity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var tvMemoryInfo: TextView
    private lateinit var tvCacheInfo: TextView
    private lateinit var progressMemory: ProgressBar
    private lateinit var tvDrawableStatus: TextView

    private lateinit var ivUnifiedStatic: ImageView
    private lateinit var ivUnifiedSvga: ImageView
    private lateinit var ivUnifiedOnlineM4a: ImageView
    private lateinit var ivInteractiveSvga: ImageView

    private var currentInteractiveAsset = "file:///android_asset/angel.svga"
    private var currentSlotKey = "GuangHuan"
    private var currentDynamicText = "LV.99 尊贵战神"
    private var currentRepeatCount = 0
    private var useEphemeralMode = false
    private var lastCallbackEvent = "等待触发..."

    private val monitorRunnable = object : Runnable {
        override fun run() {
            updateMetricsBar()
            updateInteractiveDrawableInspect()
            mainHandler.postDelayed(this, 400L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F8FAFC"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 处理系统状态栏与导航栏安全边距
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 1. 顶部标题栏 + 实时内存监控看板
        rootLayout.addView(buildHeaderAndMonitorCard())

        // 2. 子页面切换栏（交互实验室 vs 传统 ListView 压测）
        val contentContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val labScrollView = buildInteractiveLabView()
        val listViewPage = buildLegacyListViewPage()
        listViewPage.visibility = View.GONE

        contentContainer.addView(labScrollView)
        contentContainer.addView(listViewPage)

        rootLayout.addView(buildModeSwitchBar(
            onSelectLab = {
                labScrollView.visibility = View.VISIBLE
                listViewPage.visibility = View.GONE
            },
            onSelectList = {
                labScrollView.visibility = View.GONE
                listViewPage.visibility = View.VISIBLE
            }
        ))
        rootLayout.addView(contentContainer)

        setContentView(rootLayout)

        // 触发初始加载
        reloadUnifiedPreview()
        reloadInteractiveSvga()
    }

    override fun onResume() {
        super.onResume()
        mainHandler.post(monitorRunnable)
    }

    override fun onPause() {
        super.onPause()
        mainHandler.removeCallbacks(monitorRunnable)
    }

    private fun buildHeaderAndMonitorCard(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(6))
        }

        // 标题行
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val btnBack = createStyledButton("⬅ 返回 Compose", "#E2E8F0", "#1E293B").apply {
            setOnClickListener { finish() }
        }
        titleRow.addView(btnBack, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)))

        val tvTitle = TextView(this).apply {
            text = "传统 Kotlin View 专场测试"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
            setPadding(dp(10), 0, 0, 0)
        }
        titleRow.addView(tvTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        container.addView(titleRow)

        // 实时内存监控卡片
        val monitorCard = createCardLayout("#EEF2FF").apply {
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(8)
            layoutParams = lp
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        tvMemoryInfo = TextView(this).apply {
            text = "JVM 堆内存: 计算中..."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#1E293B"))
        }
        topRow.addView(tvMemoryInfo, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val btnGc = createStyledButton("手动 GC", "#FFFFFF", "#334155").apply {
            setOnClickListener {
                System.gc()
                updateMetricsBar()
            }
        }
        val btnClearCache = createStyledButton("清缓存", "#FFFFFF", "#DC2626").apply {
            setOnClickListener {
                SvgaTemplateCache.clear()
                updateMetricsBar()
            }
        }
        topRow.addView(btnGc, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(26)).apply { rightMargin = dp(6) })
        topRow.addView(btnClearCache, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(26)))
        monitorCard.addView(topRow)

        progressMemory = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 20
        }
        monitorCard.addView(
            progressMemory,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6)).apply {
                topMargin = dp(6)
                bottomMargin = dp(4)
            }
        )

        tvCacheInfo = TextView(this).apply {
            text = "🧩 享元模板池: 0 个 (0 KB) | SingletonImageLoader 已就绪"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(Color.parseColor("#475569"))
        }
        monitorCard.addView(tvCacheInfo)

        container.addView(monitorCard)
        return container
    }

    private fun buildModeSwitchBar(onSelectLab: () -> Unit, onSelectList: () -> Unit): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), dp(4), dp(14), dp(8))
        }

        lateinit var btnLab: Button
        lateinit var btnList: Button

        fun updateTabStyles(isLabSelected: Boolean) {
            btnLab.background = createRoundRectDrawable(
                if (isLabSelected) "#4F46E5" else "#E2E8F0",
                dp(8).toFloat()
            )
            btnLab.setTextColor(if (isLabSelected) Color.WHITE else Color.parseColor("#334155"))

            btnList.background = createRoundRectDrawable(
                if (!isLabSelected) "#4F46E5" else "#E2E8F0",
                dp(8).toFloat()
            )
            btnList.setTextColor(if (!isLabSelected) Color.WHITE else Color.parseColor("#334155"))
        }

        btnLab = createStyledButton("🧪 ImageView 交互实验室", "#4F46E5", "#FFFFFF").apply {
            setOnClickListener {
                updateTabStyles(true)
                onSelectLab()
            }
        }
        btnList = createStyledButton("📜 传统 ListView 50项复用压测", "#E2E8F0", "#334155").apply {
            setOnClickListener {
                updateTabStyles(false)
                onSelectList()
            }
        }

        bar.addView(btnLab, LinearLayout.LayoutParams(0, dp(36), 1f).apply { rightMargin = dp(8) })
        bar.addView(btnList, LinearLayout.LayoutParams(0, dp(36), 1f))
        return bar
    }

    private fun buildInteractiveLabView(): ScrollView {
        val scrollView = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(4), dp(14), dp(24))
        }

        // ==================== 卡片 1：ImageView.load 动静统一 + ScaleType 验证 ====================
        val cardUnified = createCardLayout("#FFFFFF").apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }

        cardUnified.addView(TextView(this).apply {
            text = "1. 原生 ImageView.load(url) 动静统一与 ScaleType 校验"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
        })
        cardUnified.addView(TextView(this).apply {
            text = "无需自定义 SVGAImageView，直接使用系统原生 android.widget.ImageView.load() 加载静态 PNG、本地 SVGA 与在线伪装 .m4a 动效。"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, dp(4), 0, dp(10))
        })

        val previewRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        ivUnifiedStatic = createPreviewImageView()
        ivUnifiedSvga = createPreviewImageView()
        ivUnifiedOnlineM4a = createPreviewImageView()

        previewRow.addView(wrapImageViewWithCaption(ivUnifiedStatic, "静态 PNG 图", "普通 Bitmap"))
        previewRow.addView(wrapImageViewWithCaption(ivUnifiedSvga, "本地 .svga", "angel.svga"))
        previewRow.addView(wrapImageViewWithCaption(ivUnifiedOnlineM4a, "在线伪装 .m4a", "魔数嗅探 SVGA"))
        cardUnified.addView(previewRow)

        // ScaleType 切换按钮组
        cardUnified.addView(TextView(this).apply {
            text = "切换 ImageView.ScaleType（实时作用于上方 3 个原生 ImageView）："
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(Color.parseColor("#475569"))
            setPadding(0, dp(10), 0, dp(6))
        })

        val scaleTypes = listOf(
            "FIT_CENTER" to ImageView.ScaleType.FIT_CENTER,
            "CENTER_CROP" to ImageView.ScaleType.CENTER_CROP,
            "FIT_XY" to ImageView.ScaleType.FIT_XY,
            "CENTER_INSIDE" to ImageView.ScaleType.CENTER_INSIDE
        )
        val scaleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        scaleTypes.forEachIndexed { idx, (label, st) ->
            val btn = createStyledButton(label, "#F1F5F9", "#1E293B").apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setOnClickListener {
                    listOf(ivUnifiedStatic, ivUnifiedSvga, ivUnifiedOnlineM4a).forEach { iv ->
                        iv.scaleType = st
                        iv.findSvgaDrawable()?.scaleType = st
                        iv.invalidate()
                    }
                }
            }
            scaleRow.addView(
                btn,
                LinearLayout.LayoutParams(0, dp(30), 1f).apply {
                    if (idx < scaleTypes.size - 1) rightMargin = dp(4)
                }
            )
        }
        cardUnified.addView(scaleRow)
        content.addView(cardUnified)

        // ==================== 卡片 2：动态插槽注入 + Animatable2 状态机操控 ====================
        val cardDynamic = createCardLayout("#FFFFFF").apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        cardDynamic.addView(TextView(this).apply {
            text = "2. 动态插槽 (svgaDynamic) + Animatable2 播放状态机操控"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
        })
        cardDynamic.addView(TextView(this).apply {
            text = "通过 imageView.findSvgaDrawable() 穿透获取底层 CoilSvgaDrawable，直接控制 start() / stop()、监听播放完成回调、验证单次播放与播后即焚。"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, dp(4), 0, dp(10))
        })

        // 中央大号交互 ImageView
        val interactiveStage = FrameLayout(this).apply {
            background = createRoundRectDrawable("#0F172A", dp(12).toFloat())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(190))
        }
        ivInteractiveSvga = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(dp(170), dp(170), Gravity.CENTER)
        }
        interactiveStage.addView(ivInteractiveSvga)
        cardDynamic.addView(interactiveStage)

        // Drawable 实时探针信息栏
        tvDrawableStatus = TextView(this).apply {
            text = "帧状态: 检测中..."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(Color.parseColor("#0284C7"))
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(6))
        }
        cardDynamic.addView(tvDrawableStatus)

        // 播放控制按钮行：播放 / 暂停 / 无限循环 / 单次播放 / 播后即焚
        val controlRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val btnStart = createStyledButton("▶ 播放", "#10B981", "#FFFFFF").apply {
            setOnClickListener {
                ivInteractiveSvga.findSvgaDrawable()?.start()
            }
        }
        val btnStop = createStyledButton("⏸ 暂停", "#F59E0B", "#FFFFFF").apply {
            setOnClickListener {
                ivInteractiveSvga.findSvgaDrawable()?.stop()
            }
        }
        val btnSinglePlay = createStyledButton("🔂 播1次停", "#6366F1", "#FFFFFF").apply {
            setOnClickListener {
                currentRepeatCount = 1
                useEphemeralMode = false
                reloadInteractiveSvga()
            }
        }
        val btnLoopPlay = createStyledButton("🔁 无限循环", "#3B82F6", "#FFFFFF").apply {
            setOnClickListener {
                currentRepeatCount = 0
                useEphemeralMode = false
                reloadInteractiveSvga()
            }
        }
        val btnEphemeral = createStyledButton("🔥 播后即焚", "#EF4444", "#FFFFFF").apply {
            setOnClickListener {
                currentRepeatCount = 1
                useEphemeralMode = true
                reloadInteractiveSvga()
            }
        }

        listOf(btnStart, btnStop, btnSinglePlay, btnLoopPlay, btnEphemeral).forEachIndexed { i, btn ->
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            controlRow.addView(
                btn,
                LinearLayout.LayoutParams(0, dp(32), 1f).apply {
                    if (i < 4) rightMargin = dp(4)
                }
            )
        }
        cardDynamic.addView(controlRow)

        // 动效源切换行
        cardDynamic.addView(TextView(this).apply {
            text = "切换测试动效源（含 18MB 巨无霸）："
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(Color.parseColor("#475569"))
            setPadding(0, dp(10), 0, dp(4))
        })

        val assetOptions = listOf(
            "天使 (209K)" to "file:///android_asset/angel.svga",
            "玫瑰 (307K)" to "file:///android_asset/rose.svga",
            "皇家金冠 (18M)" to "file:///android_asset/gift_royal_gold_18m.svga",
            "在线 .svga" to "https://media.happychatyy.com/MTc3OTE3NzI5OTI4NSM3ODQjc3ZnYQ==.svga"
        )
        val assetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        assetOptions.forEachIndexed { idx, (title, url) ->
            val btn = createStyledButton(title, "#F1F5F9", "#0F172A").apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setOnClickListener {
                    currentInteractiveAsset = url
                    reloadInteractiveSvga()
                }
            }
            assetRow.addView(
                btn,
                LinearLayout.LayoutParams(0, dp(30), 1f).apply {
                    if (idx < assetOptions.size - 1) rightMargin = dp(4)
                }
            )
        }
        cardDynamic.addView(assetRow)

        // 插槽 Key 切换行
        cardDynamic.addView(TextView(this).apply {
            text = "选择动态注入插槽 Key："
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(Color.parseColor("#475569"))
            setPadding(0, dp(8), 0, dp(4))
        })
        val slots = listOf(
            "头顶光环(GuangHuan)" to "GuangHuan",
            "爱心(Xin)" to "Xin",
            "底部浮层(Overlay)" to "__svga_overlay_bottom__"
        )
        val slotRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        slots.forEachIndexed { idx, (label, slot) ->
            val btn = createStyledButton(label, "#E0E7FF", "#3730A3").apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setOnClickListener {
                    currentSlotKey = slot
                    reloadInteractiveSvga()
                }
            }
            slotRow.addView(
                btn,
                LinearLayout.LayoutParams(0, dp(30), 1f).apply {
                    if (idx < slots.size - 1) rightMargin = dp(4)
                }
            )
        }
        cardDynamic.addView(slotRow)

        // 动态文本输入与提交更新
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, 0)
        }
        val editDynamic = EditText(this).apply {
            setText(currentDynamicText)
            hint = "输入动态文字..."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setSingleLine(true)
            background = createRoundRectDrawable("#F1F5F9", dp(6).toFloat())
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        val btnApplyDynamic = createStyledButton("注入更新", "#4F46E5", "#FFFFFF").apply {
            setOnClickListener {
                currentDynamicText = editDynamic.text?.toString()?.ifBlank { "SVGA 666" } ?: "SVGA 666"
                reloadInteractiveSvga()
            }
        }
        inputRow.addView(editDynamic, LinearLayout.LayoutParams(0, dp(38), 1f).apply { rightMargin = dp(8) })
        inputRow.addView(btnApplyDynamic, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)))
        cardDynamic.addView(inputRow)

        content.addView(cardDynamic)
        scrollView.addView(content)
        return scrollView
    }

    private fun buildLegacyListViewPage(): View {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val banner = createCardLayout("#FEF3C7").apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dp(14)
                rightMargin = dp(14)
                bottomMargin = dp(6)
            }
        }
        banner.addView(TextView(this).apply {
            text = "📜 传统 ListView + ViewHolder 复用池压测 (50 项)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#92400E"))
        })
        banner.addView(TextView(this).apply {
            text = "每个 Item 包含：底层圆形裁剪静态头像 ImageView + 顶层动态头像框 ImageView + 右侧带动态编号勋章的挂件 ImageView。快速滑动时自动取消旧请求并复用享元模板。"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(Color.parseColor("#78350F"))
            setPadding(0, dp(2), 0, 0)
        })
        page.addView(banner)

        val listView = ListView(this).apply {
            divider = null
            dividerHeight = dp(6)
            setPadding(dp(14), dp(4), dp(14), dp(16))
            clipToPadding = false
            adapter = LegacySvgaListAdapter()
        }
        page.addView(listView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return page
    }

    private fun reloadUnifiedPreview() {
        ivUnifiedStatic.load("file:///android_asset/avatars/avatar_01_lucky_star.png") {
            crossfade(true)
        }
        ivUnifiedSvga.load("file:///android_asset/angel.svga") {
            crossfade(true)
        }
        ivUnifiedOnlineM4a.load("https://media.happychatyy.com/MTczOTg4NDAzMTA4OSM3MTgjbTRh.m4a") {
            crossfade(true)
        }
    }

    private fun reloadInteractiveSvga() {
        lastCallbackEvent = "加载中..."
        ivInteractiveSvga.load(currentInteractiveAsset) {
            crossfade(false)
            svgaRepeatCount(currentRepeatCount)
            if (useEphemeralMode) {
                svgaEphemeral(autoRecycleOnEnd = true)
            }
            svgaDynamic {
                setDynamicText(currentDynamicText, currentSlotKey)
            }
            listener(
                onSuccess = { _, _ ->
                    lastCallbackEvent = "onAnimationStart (播放中)"
                    ivInteractiveSvga.post {
                        val svgaDrawable = ivInteractiveSvga.findSvgaDrawable()
                        svgaDrawable?.registerAnimationCallback(object : Animatable2.AnimationCallback() {
                            override fun onAnimationStart(drawable: Drawable?) {
                                lastCallbackEvent = "onAnimationStart (播放中)"
                            }

                            override fun onAnimationEnd(drawable: Drawable?) {
                                lastCallbackEvent = if (useEphemeralMode) {
                                    "onAnimationEnd (已结束，播后即焚已释放位图)"
                                } else {
                                    "onAnimationEnd (单次播放完毕)"
                                }
                                updateInteractiveDrawableInspect()
                            }
                        })
                    }
                },
                onError = { _, result ->
                    lastCallbackEvent = "加载失败: ${result.throwable.message}"
                }
            )
        }
    }

    private fun updateMetricsBar() {
        val runtime = Runtime.getRuntime()
        val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMb = runtime.maxMemory() / (1024 * 1024)
        val percent = if (maxMb > 0) ((usedMb * 100) / maxMb).toInt().coerceIn(0, 100) else 0
        val count = SvgaTemplateCache.entryCount()
        val kb = SvgaTemplateCache.totalAllocatedBytes() / 1024

        tvMemoryInfo.text = "JVM 堆内存: ${usedMb}MB / ${maxMb}MB ($percent%)"
        progressMemory.progress = percent
        tvCacheInfo.text = "🧩 享元模板池: $count 个 (${kb}KB) | App 全局 SingletonImageLoader"
    }

    private fun updateInteractiveDrawableInspect() {
        val d = ivInteractiveSvga.findSvgaDrawable()
        if (d == null) {
            tvDrawableStatus.text = "状态: $lastCallbackEvent"
            return
        }
        val allocKb = d.size / 1024
        val modeTag = if (d.isEphemeral) "短暂隔离(不入L1)" else "L1享元缓存"
        tvDrawableStatus.text = "帧: ${d.currentFrame + 1}/${d.videoItem.frames} (${d.videoItem.fps}fps) | 显存: ${allocKb}KB [$modeTag] | $lastCallbackEvent"
    }

    // ==================== 传统 ListView Adapter + ViewHolder ====================

    private inner class LegacySvgaListAdapter : BaseAdapter() {
        private val avatars = listOf(
            "file:///android_asset/avatars/avatar_01_lucky_star.png",
            "file:///android_asset/avatars/avatar_02_new_year.png",
            "file:///android_asset/avatars/avatar_03_lantern.png",
            "file:///android_asset/avatars/avatar_04_royal_crown.png",
            "file:///android_asset/avatars/avatar_05_charm_queen.png"
        )
        private val frames = listOf(
            "file:///android_asset/avatar_frame_light.svga" to "炫光流转框 (278K)",
            "file:///android_asset/angel.svga" to "天使双翼框 (209K)",
            "file:///android_asset/rose.svga" to "浪漫玫瑰框 (307K)",
            "file:///android_asset/avatar_frame_hd_galaxy.svga" to "星空粒子框 (15MB)"
        )
        private val badges = listOf(
            "file:///android_asset/banner_jackpot_gold.svga",
            "file:///android_asset/banner_jackpot_purple.svga",
            "file:///android_asset/angel.svga"
        )

        override fun getCount(): Int = 50
        override fun getItem(position: Int): Any = position
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val holder: ViewHolder
            val rowView: View
            if (convertView == null) {
                val card = createCardLayout("#FFFFFF").apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                // 左侧动静叠加头像容器 (64dp x 64dp)
                val avatarBox = FrameLayout(this@LegacyViewDemoActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(64), dp(64))
                }
                val ivAvatar = ImageView(this@LegacyViewDemoActivity).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    outlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: Outline) {
                            outline.setOval(0, 0, view.width, view.height)
                        }
                    }
                    clipToOutline = true
                    setBackgroundColor(Color.parseColor("#CBD5E1"))
                    layoutParams = FrameLayout.LayoutParams(dp(46), dp(46), Gravity.CENTER)
                }
                val ivFrame = ImageView(this@LegacyViewDemoActivity).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    layoutParams = FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER)
                }
                avatarBox.addView(ivAvatar)
                avatarBox.addView(ivFrame)
                card.addView(avatarBox)

                // 中间文本列
                val textCol = LinearLayout(this@LegacyViewDemoActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(10), 0, dp(8), 0)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                val tvTitle = TextView(this@LegacyViewDemoActivity).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.parseColor("#0F172A"))
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }
                val tvSubtitle = TextView(this@LegacyViewDemoActivity).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setTextColor(Color.parseColor("#64748B"))
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }
                textCol.addView(tvTitle)
                textCol.addView(tvSubtitle)
                card.addView(textCol)

                // 右侧挂件 ImageView（带动态文字注入）
                val ivBadge = ImageView(this@LegacyViewDemoActivity).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    background = createRoundRectDrawable("#F1F5F9", dp(8).toFloat())
                    layoutParams = LinearLayout.LayoutParams(dp(72), dp(48))
                }
                card.addView(ivBadge)

                holder = ViewHolder(ivAvatar, ivFrame, tvTitle, tvSubtitle, ivBadge)
                card.tag = holder
                rowView = card
            } else {
                rowView = convertView
                holder = convertView.tag as ViewHolder
            }

            val avatarUrl = avatars[position % avatars.size]
            val (frameUrl, frameName) = frames[position % frames.size]
            val badgeUrl = badges[position % badges.size]

            holder.tvTitle.text = "传统 ViewHolder 用户 #${position + 1}"
            holder.tvSubtitle.text = "佩戴: $frameName"

            holder.ivAvatar.load(avatarUrl) {
                crossfade(true)
            }
            holder.ivFrame.load(frameUrl) {
                crossfade(false)
                svgaRepeatCount(0)
            }
            holder.ivBadge.load(badgeUrl) {
                crossfade(false)
                svgaDynamic {
                    setDynamicText("TOP.${position + 1}", "banner")
                }
            }

            return rowView
        }
    }

    private class ViewHolder(
        val ivAvatar: ImageView,
        val ivFrame: ImageView,
        val tvTitle: TextView,
        val tvSubtitle: TextView,
        val ivBadge: ImageView
    )

    // ==================== UI 辅助构建方法 ====================

    private fun createPreviewImageView(): ImageView {
        return ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = createRoundRectDrawable("#F1F5F9", dp(10).toFloat())
            layoutParams = LinearLayout.LayoutParams(dp(96), dp(96))
        }
    }

    private fun wrapImageViewWithCaption(imageView: ImageView, title: String, subtitle: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(imageView)
            addView(TextView(this@LegacyViewDemoActivity).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#1E293B"))
                setPadding(0, dp(4), 0, 0)
            })
            addView(TextView(this@LegacyViewDemoActivity).apply {
                text = subtitle
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setTextColor(Color.parseColor("#64748B"))
            })
        }
    }

    private fun createCardLayout(bgHex: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createRoundRectDrawable(bgHex, dp(12).toFloat(), "#E2E8F0")
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
    }

    private fun createStyledButton(text: String, bgHex: String, textHex: String): Button {
        return Button(this).apply {
            this.text = text
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(Color.parseColor(textHex))
            background = createRoundRectDrawable(bgHex, dp(6).toFloat())
            setPadding(dp(8), 0, dp(8), 0)
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
        }
    }

    private fun createRoundRectDrawable(
        fillHex: String,
        radiusPx: Float,
        strokeHex: String? = null
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(Color.parseColor(fillHex))
            if (strokeHex != null) {
                setStroke(dp(1), Color.parseColor(strokeHex))
            }
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
