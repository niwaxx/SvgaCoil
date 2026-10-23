package com.opensource.svga.core.drawable

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Animatable2
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.widget.ImageView
import com.opensource.svga.core.drawer.SVGACanvasDrawer
import com.opensource.svga.core.dynamic.SVGADynamicEntity
import com.opensource.svga.core.model.SVGAVideoEntity
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 自驱动的 SVGA 动效 Drawable。
 * 实现原生 [Animatable2]，可直接接入 Coil 以及 Jetpack Compose 的 AsyncImage / rememberAsyncImagePainter。
 */
open class SvgaAnimatableDrawable(
    val videoItem: SVGAVideoEntity,
    initialDynamicItem: SVGADynamicEntity? = null
) : Drawable(), Animatable2 {

    private val drawer = SVGACanvasDrawer(videoItem, initialDynamicItem)

    var dynamicItem: SVGADynamicEntity? = initialDynamicItem
        set(value) {
            field = value
            drawer.dynamicItem = value
            invalidateSelf()
        }

    var scaleType: ImageView.ScaleType = ImageView.ScaleType.FIT_CENTER

    /**
     * 重复播放次数，0 表示无限循环播放（默认）
     */
    var repeatCount: Int = 0

    var currentFrame: Int = 0
        private set

    private var currentIteration: Int = 0
    private var isRunning: Boolean = false
    @Volatile private var isSleeping: Boolean = false
    @Volatile private var lastDrawTime: Long = 0L

    private var drawableAlpha: Int = 255
    private var drawableColorFilter: ColorFilter? = null

    private val callbacks = CopyOnWriteArrayList<Animatable2.AnimationCallback>()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastFrameTimeNanos: Long = 0L
    private val frameIntervalNanos: Long = if (videoItem.fps > 0) {
        1_000_000_000L / videoItem.fps
    } else {
        1_000_000_000L / 20
    }

    private val weakFrameCallback = WeakFrameCallback(this)

    private class WeakFrameCallback(drawable: SvgaAnimatableDrawable) : Choreographer.FrameCallback {
        private val ref = java.lang.ref.WeakReference(drawable)

        override fun doFrame(frameTimeNanos: Long) {
            val d = ref.get() ?: return
            d.doFrameInternal(frameTimeNanos)
        }
    }

    private fun doFrameInternal(frameTimeNanos: Long) {
        if (!isRunning) return

        // 智能休眠检测：若超过 INACTIVE_TIMEOUT_MS 未发生实际 draw 绘制（如滑出屏幕），则停止帧循环
        val now = android.os.SystemClock.uptimeMillis()
        if (lastDrawTime > 0L && (now - lastDrawTime) > INACTIVE_TIMEOUT_MS) {
            isSleeping = true
            return
        }

        if (lastFrameTimeNanos == 0L) {
            lastFrameTimeNanos = frameTimeNanos
        }

        val elapsed = frameTimeNanos - lastFrameTimeNanos
        if (elapsed >= frameIntervalNanos) {
            val rawStepCount = (elapsed / frameIntervalNanos).toInt()
            // 补偿余数消除 60Hz VSYNC 下的非整除帧率（如 24fps）漂移；
            // 若发生超长停顿（> MAX_FRAME_SKIP 帧），重置时钟基准并限制单次最多跳帧数，防止画面瞬移
            val stepCount = if (rawStepCount > MAX_FRAME_SKIP) {
                lastFrameTimeNanos = frameTimeNanos
                MAX_FRAME_SKIP
            } else {
                lastFrameTimeNanos += rawStepCount * frameIntervalNanos
                rawStepCount
            }
            stepFrames(stepCount)
            invalidateSelf()
        }

        if (isRunning && !isSleeping) {
            Choreographer.getInstance().postFrameCallback(weakFrameCallback)
        }
    }

    internal fun stepFrames(count: Int = 1) {
        val totalFrames = videoItem.frames
        if (totalFrames <= 0 || count <= 0) return

        repeat(count) {
            currentFrame++
            if (currentFrame >= totalFrames) {
                currentIteration++
                if (repeatCount > 0 && currentIteration >= repeatCount) {
                    // 达到指定循环次数，结束动画
                    currentFrame = totalFrames - 1
                    stop()
                    notifyAnimationEnd()
                    return
                } else {
                    // 循环播放，从第 0 帧重新开始
                    currentFrame = 0
                }
            }
        }
    }

    override fun draw(canvas: Canvas) {
        lastDrawTime = android.os.SystemClock.uptimeMillis()
        if (isSleeping && isRunning) {
            // 重新在屏幕上可见并绘制，自动唤醒帧回调
            isSleeping = false
            lastFrameTimeNanos = 0L
            Choreographer.getInstance().postFrameCallback(weakFrameCallback)
        }
        drawer.drawFrame(canvas, currentFrame, scaleType, bounds)
    }

    override fun start() {
        if (isRunning && !isSleeping) return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { start() }
            return
        }

        isRunning = true
        isSleeping = false
        lastDrawTime = android.os.SystemClock.uptimeMillis()
        currentIteration = 0
        lastFrameTimeNanos = 0L
        notifyAnimationStart()
        Choreographer.getInstance().removeFrameCallback(weakFrameCallback)
        Choreographer.getInstance().postFrameCallback(weakFrameCallback)
    }

    override fun stop() {
        if (!isRunning) return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { stop() }
            return
        }

        isRunning = false
        isSleeping = false
        Choreographer.getInstance().removeFrameCallback(weakFrameCallback)
    }

    override fun isRunning(): Boolean = isRunning && !isSleeping
    val isSleepingState: Boolean get() = isSleeping

    companion object {
        /**
         * 当超过 250 毫秒没有收到 draw() 回调时，判定该动效已离开屏幕或不可见，自动进入休眠停止空转
         */
        const val INACTIVE_TIMEOUT_MS = 250L

        /**
         * 主线程卡顿补偿单次最大跳帧上限，避免长阻塞恢复后动画瞬间跳过整个周期
         */
        const val MAX_FRAME_SKIP = 5
    }

    override fun registerAnimationCallback(callback: Animatable2.AnimationCallback) {
        callbacks.add(callback)
    }

    override fun unregisterAnimationCallback(callback: Animatable2.AnimationCallback): Boolean {
        return callbacks.remove(callback)
    }

    override fun clearAnimationCallbacks() {
        callbacks.clear()
    }

    private fun notifyAnimationStart() {
        callbacks.forEach { it.onAnimationStart(this) }
    }

    private fun notifyAnimationEnd() {
        callbacks.forEach { it.onAnimationEnd(this) }
    }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val changed = super.setVisible(visible, restart)
        if (visible) {
            if (restart) {
                currentFrame = 0
                start()
            } else if (!isRunning) {
                start()
            }
        } else {
            stop()
        }
        return changed
    }

    override fun getIntrinsicWidth(): Int {
        val width = videoItem.videoSize.width.toInt()
        return if (width > 0) width else -1
    }

    override fun getIntrinsicHeight(): Int {
        val height = videoItem.videoSize.height.toInt()
        return if (height > 0) height else -1
    }

    override fun getAlpha(): Int = drawableAlpha

    override fun setAlpha(alpha: Int) {
        val clamped = alpha.coerceIn(0, 255)
        if (drawableAlpha != clamped) {
            drawableAlpha = clamped
            drawer.drawableAlpha = clamped
            invalidateSelf()
        }
    }

    override fun getColorFilter(): ColorFilter? = drawableColorFilter

    override fun setColorFilter(colorFilter: ColorFilter?) {
        if (drawableColorFilter !== colorFilter) {
            drawableColorFilter = colorFilter
            drawer.drawableColorFilter = colorFilter
            invalidateSelf()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getConstantState(): ConstantState {
        return SvgaConstantState(videoItem, dynamicItem, scaleType, repeatCount)
    }

    private class SvgaConstantState(
        private val videoItem: SVGAVideoEntity,
        private val dynamicItem: SVGADynamicEntity?,
        private val scaleType: ImageView.ScaleType,
        private val repeatCount: Int
    ) : ConstantState() {
        override fun newDrawable(): Drawable {
            return SvgaAnimatableDrawable(videoItem, dynamicItem).also {
                it.scaleType = scaleType
                it.repeatCount = repeatCount
            }
        }

        override fun getChangingConfigurations(): Int = 0
    }
}
