# SvgaCoil — 基于 Coil 3 的高性能 Android SVGA 动效引擎

**SvgaCoil** 是一套专为 Android 打造的高性能、低显存 SVGA 动效加载与渲染 SDK。它通过双层解耦架构（`:svga-core` 纯净渲染引擎 + `:coil-svga` 适配层）深度接入 **Coil 3** 管线，抹平了静态图与 SVGA 动效的调用差异，无论是 **Jetpack Compose (`AsyncImage`)** 还是 **传统 View (`ImageView.load`)** 均可一行代码无缝加载。

---

## ✨ 核心特性

- **🎯 动静统一 API**：无需专门的 `SVGAImageView`，直接使用 `AsyncImage(model = url)` 或 `imageView.load(url)` 即可加载本地/网络 SVGA。
- **📉 内存立降 60%~85%（防 OOM）**：
  - **连续比例动态下采样（Downsampling）**：综合画布缩放比与目标控件尺寸计算 `inSampleSize`，小挂件加载 1080p 设计稿切片时显存缩减至 $1/16 \sim 1/64$。
  - **RGB_565 智能降级**：对不透明（无 Alpha 通道）切片自动采用 `Bitmap.Config.RGB_565` 解码，单张显存直接减半。
- **🧩 双层享元缓存与向下兼容复用**：
  - **L0 动画骨架池**：Protobuf `MovieEntity` 几何路径与帧矩阵无视尺寸全局仅存一份。
  - **L1 分辨率分级缓存（`THUMBNAIL` / `MEDIUM` / `FULL`）**：支持**向下兼容复用（Downwards Reuse）**——当内存中已存在高清大图模板时，同源小尺寸挂件 100% 零拷贝复用，杜绝重复解码。
- **🛡️ 巨型礼物熔断隔离与播后即焚**：
  - 支持通过 `svgaMaxCacheableBytes(threshold)` 或 `svgaEphemeral()` 拦截超过阈值（如 16MB+）的直播间全屏大礼物，使其不挤占 L1 共享缓存池，并在播放完毕后自动释放位图引用。
- **⏱️ VSYNC 余数保留精准帧率控制**：
  - 自驱动 `SvgaAnimatableDrawable` 基于 `Choreographer` 帧时钟与余数保留算法（Remainder-Preserving Frame Pacing），在 60Hz/90Hz/120Hz 屏幕上均能精准对齐 20fps/24fps/30fps 设计帧率，并在 View 离屏时自动断电休眠。
- **🔍 魔数嗅探（支持伪装后缀）**：
  - 基于文件头魔数（ZIP `50 4B 03 04` 与 RFC1950 ZLIB 头部校验），即使服务端将 `.svga` 伪装为 `.mp4` / `.m4a` 或返回 `application/octet-stream` 也能 100% 精准识别，同时快速放行普通 PNG/JPEG/GIF/WebP 静态图。

---

## 📦 模块结构

```text
SvgaCoil/
├── svga-core/     # 核心解析与渲染引擎（纯 Kotlin + Okio + Protobuf-lite，零图片库依赖）
├── coil-svga/     # Coil 3 适配扩展层（SvgaDecoder、二级享元缓存、DSL 扩展函数）
└── app/           # 综合演示与极限压测工程（含 Compose 8 大场景 + 传统 View 专场 Activity）
```

| 模块 | 职责说明 |
| :--- | :--- |
| **[`:svga-core`](./svga-core)** | ZIP/ZLIB 内存流式解压、`movie.binary` Protobuf 解析、`SvgaBitmapDecoder` 动态下采样、`SVGAPathEntity` 矢量路径（含 `S`/`s` 平滑三次贝塞尔）、`SVGACanvasDrawer` 零分配画布渲染器、`SvgaAnimatableDrawable` 自驱动动画状态机。 |
| **[`:coil-svga`](./coil-svga)** | 实现 Coil 3 `SvgaDecoder` 与 `Factory`、封装 `SvgaTemplateCache`（L0 骨架池 + L1 分级模板池）、提供 `svgaDynamic` / `svgaRepeatCount` / `svgaEphemeral` / `ImageView.findSvgaDrawable()` 等扩展 API。 |
| **[`:app`](./app)** | 示例与压测应用，包含 [`App.kt`](./app/src/main/java/com/example/svgacoil/App.kt) 全局初始化、[`MainActivity.kt`](./app/src/main/java/com/example/svgacoil/MainActivity.kt)（Compose 演示）与 [`LegacyViewDemoActivity.kt`](./app/src/main/java/com/example/svgacoil/LegacyViewDemoActivity.kt)（原生 View 演示）。 |

---

## 🚀 快速接入

### 1. 添加 JitPack 仓库

在项目根目录 `settings.gradle.kts` 中添加 JitPack 仓库：
```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### 2. 按项目类型引入依赖

`:coil-svga` 与 `:svga-core` **底层完全不依赖 Jetpack Compose**，你可以根据自己的项目形态按需引入，不会带入任何多余的 Compose 依赖包：

#### 场景 A：纯传统 Android View 项目（XML / Activity / Fragment / RecyclerView，无 Compose）
只需引入 `coil-svga`（内部已自动传递包含 `:svga-core` 与 `io.coil-kt.coil3:coil`，自带 `ImageView.load` 与 `SingletonImageLoader`）：
```kotlin
dependencies {
    // 核心库（已包含 svga-core 与 coil3 基础 View 扩展，零 Compose 依赖）
    implementation("com.github.niwaxx.SvgaCoil:coil-svga:v1.0.0")

    // 若需加载 http/https 在线网络动效，添加网络支持模块
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
}
```

#### 场景 B：Jetpack Compose 项目（或 View + Compose 混合项目）
在场景 A 的基础上额外加上 `coil-compose` 即可使用 `AsyncImage`：
```kotlin
dependencies {
    implementation("com.github.niwaxx.SvgaCoil:coil-svga:v1.0.0")
    // Compose AsyncImage 支持
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    // 网络动效加载支持
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
}
```

#### 场景 C：不使用 Coil，仅单独使用底层 `:svga-core` 渲染引擎
如果你项目中使用的是 Glide 或希望直接通过原生 `Drawable` 手动解析播放，可只引入 **144KB** 的 `:svga-core`（零图片加载库依赖）：
```kotlin
dependencies {
    implementation("com.github.niwaxx.SvgaCoil:svga-core:v1.0.0")
}
```

---

### 3. 全局规范初始化与自定义 `OkHttpClient`（[`App.kt`](./app/src/main/java/com/example/svgacoil/App.kt)）

在 `Application` 中实现 Coil 3 的 `SingletonImageLoader.Factory` 接口，使全应用共享同一套解码器与缓存池：

#### 3.1 标准初始化
```kotlin
class App : Application(), SingletonImageLoader.Factory {

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory())
                add(SvgaDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            SvgaTemplateCache.clear()
        }
    }
}
```

#### 3.2 自定义 `OkHttpClient`（超时、请求头拦截器、HTTPDNS、复用项目现有实例）
在 Coil 3 中，如需自定义 `OkHttpClient`，只需将其传入 `OkHttpNetworkFetcherFactory(callFactory = { ... })`（若需在代码中直接构建 `OkHttpClient`，请确保 `build.gradle.kts` 中声明了 `implementation("com.squareup.okhttp3:okhttp:4.12.0")`）：
```kotlin
class App : Application(), SingletonImageLoader.Factory {

    // 构建专属 OkHttpClient，或直接复用你项目网络层已有的 OkHttpClient 单例
    private val customOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS) // 针对 15MB+ 大体积全屏礼物适当放宽读取超时
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "SvgaCoil-Android/1.0")
                    // .header("Authorization", "Bearer xxx")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                // 传入自定义 OkHttpClient（lambda 懒加载，不阻塞 Application.onCreate 主线程）
                add(OkHttpNetworkFetcherFactory(callFactory = { customOkHttpClient }))
                add(SvgaDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }
}
```

---

### 4. 各场景代码调用示例

#### 4.1 传统 Android View (`ImageView`) 完整用法（无需 Compose）

```kotlin
import android.graphics.drawable.Animatable2
import android.graphics.drawable.Drawable
import coil3.load
import coil3.request.crossfade
import com.opensource.svga.coil.findSvgaDrawable
import com.opensource.svga.coil.svgaDynamic
import com.opensource.svga.coil.svgaEphemeral
import com.opensource.svga.coil.svgaRepeatCount

// 1. 一行代码直接加载至原生 ImageView（完美兼容 FIT_CENTER / CENTER_CROP / FIT_XY 等 ScaleType）
imageView.load("file:///android_asset/angel.svga") {
    crossfade(true)
    svgaRepeatCount(0) // 0 = 无限循环，1 = 单次播放
    svgaDynamic {
        setDynamicText("VIP.99", "GuangHuan")
    }
}

// 2. 穿透 CrossfadeDrawable / ScaleDrawable 获取底层控制器（暂停/继续/读取帧信息）
imageView.findSvgaDrawable()?.let { svga ->
    if (svga.isRunning) svga.stop() else svga.start()
}

// 3. 监听单次大礼物播放完毕回调 + 播后即焚（自动释放显存）
imageView.load("https://cdn.example.com/fullscreen_gift.svga") {
    crossfade(false)
    svgaRepeatCount(1)
    svgaEphemeral(autoRecycleOnEnd = true) // 不入 L1 缓存，播完自动释放位图
    listener(
        onSuccess = { _, _ ->
            imageView.post {
                imageView.findSvgaDrawable()?.registerAnimationCallback(
                    object : Animatable2.AnimationCallback() {
                        override fun onAnimationEnd(drawable: Drawable?) {
                            // 动画播放结束，可在此隐藏 ImageView 或播放队列中的下一个礼物
                        }
                    }
                )
            }
        }
    )
}
```

#### 4.2 Jetpack Compose (`AsyncImage`) 完整用法

```kotlin
// 1. 基础加载（本地 assets / 网络 URL / 伪装后缀均可）
AsyncImage(
    model = "file:///android_asset/angel.svga",
    contentDescription = "SVGA 动效",
    modifier = Modifier.size(140.dp)
)

// 2. 动态插槽（文本/头像替换）+ 循环次数控制
val context = LocalContext.current
val request = remember(userTitle) {
    ImageRequest.Builder(context)
        .data("file:///android_asset/angel.svga")
        .svgaRepeatCount(0)
        .svgaDynamic {
            setDynamicText(userTitle, forKey = "GuangHuan")
        }
        .build()
}
AsyncImage(model = request, contentDescription = null, modifier = Modifier.size(180.dp))
```

#### 4.3 仅单独使用 `:svga-core`（不通过 Coil）

```kotlin
val parser = SVGAParser(context)
lifecycleScope.launch {
    val result = parser.decodeFromAssets(
        assetName = "angel.svga",
        reqWidth = 300,
        reqHeight = 300
    )
    result.onSuccess { videoEntity ->
        val drawable = SvgaAnimatableDrawable(
            videoItem = videoEntity,
            dynamicItem = SVGADynamicEntity().apply {
                setDynamicText("SVGA-Core", "GuangHuan")
            }
        ).apply {
            repeatCount = 0
        }
        imageView.setImageDrawable(drawable)
        drawable.start()
    }
}
```

---

## 🛠️ 高级配置与扩展 DSL 速查

| 扩展方法 | 默认值 | 适用场景与作用 |
| :--- | :--- | :--- |
| `svgaRepeatCount(count: Int)` | `0`（无限循环） | 设置播放次数，`0` 表示无限循环，`1` 表示单次播放。 |
| `svgaDynamic { ... }` | `null` | 注入运行时动态文本（`setDynamicText`）、动态图片（`setDynamicImage`）或全局横幅浮层（`setOverlayText`），自动生成差异化缓存签名。 |
| `svgaMaxCacheableBytes(bytes: Long)` | `16MB` | 单体模板进入 L1 享元缓存的显存上限；超出上限的大动效自动转为瞬态模式（`isEphemeral = true`），防止挤爆常规挂件缓存。 |
| `svgaAutoRecycleOnEnd(enabled: Boolean)` | `false` | 单次动效播放结束时自动清理瞬态位图引用，适合全屏入场座驾/大礼物。 |
| `svgaEphemeral(autoRecycleOnEnd: Boolean)` | `true` | 一键开启瞬态大礼物模式（等价于禁用 L1/Coil 内存缓存 + 播完自动释放位图）。 |
| `imageView.findSvgaDrawable()` | - | 从 `ImageView` 当前挂载的 Drawable（含 `CrossfadeDrawable` 过渡层）中剥离出底层 `CoilSvgaDrawable`。 |

---

### 5. 日志系统与 Release 静默策略（`SvgaLogger`）

SDK 内置轻量、高性能的日志门面 [`SvgaLogger`](./svga-core/src/main/java/com/opensource/svga/core/util/SvgaLogger.kt)，覆盖二进制流解析、切片降采样计算、OOM 降级、享元二级缓存命中以及动画掉帧/休眠监控：

- **🛡️ 默认彻底静默（零反射、零合规风险）**：
  - **默认级别**：`SvgaLogLevel.NONE`。未经显式开启时，无论是 Debug 还是 Release 均**完全不输出任何日志**，彻底杜绝隐藏 API 反射与审计风险。
  - **零开销保障**：结合 Kotlin 内联高阶函数，在未开启状态下**绝不执行任何 Lambda 字符串拼接与对象分配**，CPU 与内存开销严格为 0。
- **⚙️ 外部显式开启与配置**：

```kotlin
// 推荐做法：在 Application.onCreate() 中绑定主工程的 BuildConfig.DEBUG
SvgaLogger.init(isDebug = BuildConfig.DEBUG)

// 或显式指定精确级别（支持 VERBOSE, DEBUG, INFO, WARN, ERROR, NONE）
SvgaLogger.minLevel = SvgaLogLevel.INFO

// 接入统一日志框架（如 Timber / XLog / APM）
SvgaLogger.delegate = SvgaLogDelegate { priority, tag, message, throwable ->
    Timber.tag(tag).log(priority, throwable, message)
}
```

---

## 📱 示例工程与验证命令

本仓库 `:app` 模块内置完整的交互式调试与极限压测功能：

1. **Compose 主演示页（[`MainActivity.kt`](./app/src/main/java/com/example/svgacoil/MainActivity.kt)）**：
   - 顶部常驻 **JVM 堆内存水位 + L1 享元模板池实时监控栏**（支持一键手动 GC 与清空缓存）。
   - 涵盖 8 个专题 Tab：`动静统一`、`📱 传统View`、`在线动效`、`动态注入`、`多尺寸复用`、`列表压测 (100项混排)`、`🎬 全屏特效轮播`、`👑 动态头像框排行榜`。
2. **传统 View 专场页（[`LegacyViewDemoActivity.kt`](./app/src/main/java/com/example/svgacoil/LegacyViewDemoActivity.kt)）**：
   - 纯原生 `Activity` + `LinearLayout` / `ImageView` / `ListView` 实现，包含交互式 `ScaleType` / `Animatable2` 实验室与 50 项 `ListView` + `ViewHolder` 高频滑动复用压测。

### 常用构建与测试命令

```bash
# 运行核心引擎与 Coil 适配层全部单元测试
./gradlew :svga-core:testDebugUnitTest :coil-svga:testDebugUnitTest

# 编译打包 Debug 安装包
./gradlew :app:assembleDebug
```

---

## 📄 延伸阅读

- [《Coil-SVGA 高性能动效 SDK 技术方案与深度架构设计》](./COIL_SVGA_TECHNICAL_PROPOSAL.md)
