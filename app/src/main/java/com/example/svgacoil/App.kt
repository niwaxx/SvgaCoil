package com.example.svgacoil

import android.app.Application
import android.content.ComponentCallbacks2
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.opensource.svga.coil.SvgaDecoder
import com.opensource.svga.coil.SvgaTemplateCache
import com.opensource.svga.core.util.SvgaLogger

/**
 * 全局 Application，实现 [SingletonImageLoader.Factory] 规范化初始化 Coil 3 单例加载器。
 * 使得全应用内的 Jetpack Compose (AsyncImage) 与传统 Android View (ImageView.load)
 * 共享同一套网络加载器、SVGA 解码器与内存缓存体系。
 */
class App : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        // 外部显式开启/关闭日志：Debug 环境开启 DEBUG 级日志，Release 环境保持默认静默（NONE）
        SvgaLogger.init(isDebug = BuildConfig.DEBUG)
    }

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

    override fun onLowMemory() {
        super.onLowMemory()
        SvgaTemplateCache.clear()
    }
}
