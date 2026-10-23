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

### 1. 引入依赖

#### 方式 A：通过 JitPack 远程依赖（推荐）
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
在业务模块的 `build.gradle.kts` 中一行引入（自动传递依赖 `:svga-core` 与 `coil`）：
```kotlin
dependencies {
    implementation("com.github.niwaxx.SvgaCoil:coil-svga:v1.0.0")
    // 若使用 Jetpack Compose
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    // 若加载网络动效
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
}
```

#### 方式 B：源码子模块或本地 Maven (`mavenLocal()`) 依赖
```kotlin
dependencies {
    // 源码模块依赖
    implementation(project(":coil-svga"))
    // 或执行 ./gradlew publishToMavenLocal 后使用本地 Maven 坐标：
    // implementation("com.github.niwaxx.SvgaCoil:coil-svga:1.0.0")
}
```

### 2. 全局规范初始化（[`App.kt`](./app/src/main/java/com/example/svgacoil/App.kt)）

在 `Application` 中实现 Coil 3 的 `SingletonImageLoader.Factory` 接口，使全应用 Compose 与传统 View 共享同一套解码器与缓存池：

```kotlin
class App : Application(), SingletonImageLoader.Factory {

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(SvgaDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND) {
            SvgaTemplateCache.clear()
        }
    }
}
```

### 3. Jetpack Compose 使用方式

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
        .svgaRepeatCount(0) // 0 = 无限循环
        .svgaDynamic {
            setDynamicText(userTitle, forKey = "GuangHuan")
        }
        .build()
}
AsyncImage(model = request, contentDescription = null, modifier = Modifier.size(180.dp))
```

### 4. 传统 Android View (`ImageView`) 使用方式

```kotlin
// 1. 一行代码直接加载至原生 ImageView（完美兼容 FIT_CENTER / CENTER_CROP / FIT_XY 等 ScaleType）
imageView.load("file:///android_asset/angel.svga") {
    crossfade(true)
    svgaRepeatCount(0)
    svgaDynamic {
        setDynamicText("VIP.99", "GuangHuan")
    }
}

// 2. 穿透 CrossfadeDrawable / ScaleDrawable 获取底层动效控制器
imageView.findSvgaDrawable()?.let { svga ->
    if (svga.isRunning) svga.stop() else svga.start()
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
