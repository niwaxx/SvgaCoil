package com.opensource.svga.core.util

import android.util.Log

/**
 * SVGA 日志级别枚举
 */
enum class SvgaLogLevel(val priority: Int) {
    VERBOSE(Log.VERBOSE),
    DEBUG(Log.DEBUG),
    INFO(Log.INFO),
    WARN(Log.WARN),
    ERROR(Log.ERROR),
    NONE(Int.MAX_VALUE) // 默认彻底静默，不输出任何日志
}

/**
 * 自定义日志委托接口，便于业务方将 SVGA 日志对齐到统一日志框架（如 Timber, XLog 等）
 */
fun interface SvgaLogDelegate {
    fun log(priority: Int, tag: String, message: String, throwable: Throwable?)
}

/**
 * SVGA 全局日志控制器。
 *
 * 【设计哲学】：
 * 1. 默认彻底静默（[minLevel] 为 [SvgaLogLevel.NONE]），无论 Debug 还是 Release 均不产生任何日志干扰；
 * 2. 彻底移除任何隐藏 API 反射与隐式环境探测，零合规风险与零性能开销；
 * 3. 外部完全自主可控：调用方可在初始化时按需开启，如 `SvgaLogger.init(isDebug = BuildConfig.DEBUG)` 或 `SvgaLogger.minLevel = SvgaLogLevel.DEBUG`；
 * 4. 内联高阶函数按需求值：未开启时绝不执行 Lambda，无任何字符串拼接与对象分配。
 */
object SvgaLogger {

    const val DEFAULT_TAG = "SvgaCoil"

    /**
     * 全局日志总开关。如果设为 false，则拦截所有日志。
     */
    @Volatile
    var isEnabled: Boolean = true

    /**
     * 当前生效的最低日志级别。默认彻底静默 [SvgaLogLevel.NONE]。
     */
    @Volatile
    var minLevel: SvgaLogLevel = SvgaLogLevel.NONE

    /**
     * 自定义日志委托实现。
     */
    @Volatile
    var delegate: SvgaLogDelegate? = null

    /**
     * 便捷初始化方法。
     * @param isDebug 若为 true 则设置日志级别为 [SvgaLogLevel.DEBUG]，若为 false 则保持 [SvgaLogLevel.NONE]（静默）。
     * 常见用法：在 Application.onCreate() 中调用 `SvgaLogger.init(isDebug = BuildConfig.DEBUG)`
     */
    fun init(isDebug: Boolean) {
        minLevel = if (isDebug) SvgaLogLevel.DEBUG else SvgaLogLevel.NONE
    }

    /**
     * 判断当前是否允许输出对应级别的日志。
     */
    @PublishedApi
    internal fun isLoggable(level: SvgaLogLevel): Boolean {
        return isEnabled && level.priority >= minLevel.priority
    }

    inline fun verbose(
        tag: String = DEFAULT_TAG,
        throwable: Throwable? = null,
        message: () -> String
    ) {
        log(SvgaLogLevel.VERBOSE, tag, throwable, message)
    }

    inline fun debug(
        tag: String = DEFAULT_TAG,
        throwable: Throwable? = null,
        message: () -> String
    ) {
        log(SvgaLogLevel.DEBUG, tag, throwable, message)
    }

    inline fun info(
        tag: String = DEFAULT_TAG,
        throwable: Throwable? = null,
        message: () -> String
    ) {
        log(SvgaLogLevel.INFO, tag, throwable, message)
    }

    inline fun warn(
        tag: String = DEFAULT_TAG,
        throwable: Throwable? = null,
        message: () -> String
    ) {
        log(SvgaLogLevel.WARN, tag, throwable, message)
    }

    inline fun error(
        tag: String = DEFAULT_TAG,
        throwable: Throwable? = null,
        message: () -> String
    ) {
        log(SvgaLogLevel.ERROR, tag, throwable, message)
    }

    inline fun log(
        level: SvgaLogLevel,
        tag: String = DEFAULT_TAG,
        throwable: Throwable? = null,
        message: () -> String
    ) {
        if (isLoggable(level)) {
            val msg = message()
            val custom = delegate
            if (custom != null) {
                custom.log(level.priority, tag, msg, throwable)
            } else {
                try {
                    when (level) {
                        SvgaLogLevel.VERBOSE -> Log.v(tag, msg, throwable)
                        SvgaLogLevel.DEBUG -> Log.d(tag, msg, throwable)
                        SvgaLogLevel.INFO -> Log.i(tag, msg, throwable)
                        SvgaLogLevel.WARN -> Log.w(tag, msg, throwable)
                        SvgaLogLevel.ERROR -> Log.e(tag, msg, throwable)
                        SvgaLogLevel.NONE -> Unit
                    }
                } catch (_: Throwable) {
                    // 兼容纯 JVM 环境与单元测试
                }
            }
        }
    }
}
