package com.opensource.svga.core

import android.widget.ImageView
import com.opensource.svga.core.bitmap.SvgaBitmapDecoder
import com.opensource.svga.core.drawer.SvgaScaleInfo
import com.opensource.svga.core.dynamic.SVGADynamicEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SvgaMilestone2Test {

    @Test
    fun testCalculateInSampleSize() {
        // 1080p 原图，目标 120dp 控件 (例如 270x270 px)
        val sample1 = SvgaBitmapDecoder.calculateInSampleSize(
            rawWidth = 1080,
            rawHeight = 1920,
            targetWidth = 270,
            targetHeight = 270
        )
        // 期望采样率为 4 (1080/4 = 270)
        assertEquals(4, sample1)

        // 目标尺寸大于原图，不采样
        val sample2 = SvgaBitmapDecoder.calculateInSampleSize(
            rawWidth = 200,
            rawHeight = 200,
            targetWidth = 500,
            targetHeight = 500
        )
        assertEquals(1, sample2)

        // 目标为 0 或非法参数，不采样
        val sample3 = SvgaBitmapDecoder.calculateInSampleSize(
            rawWidth = 1080,
            rawHeight = 1920,
            targetWidth = 0,
            targetHeight = 0
        )
        assertEquals(1, sample3)

        // 56dp 头像框 (168px @ 3x) 展示 750 画布动效 (canvasScale = 168/750 = 0.224)
        // 640x640 高清切片：屏幕显示约 143px，下采样 4x 后 160px >= 143px，视网膜保底且显存缩减 1/16
        val sampleAvatar = SvgaBitmapDecoder.calculateInSampleSize(
            rawWidth = 640,
            rawHeight = 640,
            canvasScale = 0.224
        )
        assertEquals(4, sampleAvatar)

        // 金色方案一 240x240 切片：屏幕显示约 53.7px，下采样 4x 后 60px >= 53px，显存从 36MB 降至 2.2MB
        val sampleJinSe = SvgaBitmapDecoder.calculateInSampleSize(
            rawWidth = 240,
            rawHeight = 240,
            canvasScale = 0.224
        )
        assertEquals(4, sampleJinSe)

        // 微型切片 48x48：触发保底线，不采样，避免丢失形态
        val sampleTiny = SvgaBitmapDecoder.calculateInSampleSize(
            rawWidth = 48,
            rawHeight = 48,
            canvasScale = 0.224
        )
        assertEquals(1, sampleTiny)
    }

    @Test
    fun testSvgaScaleInfoFitCenter() {
        val scaleInfo = SvgaScaleInfo()
        scaleInfo.performScaleType(
            canvasWidth = 500f,
            canvasHeight = 1000f,
            videoWidth = 200f,
            videoHeight = 200f,
            scaleType = ImageView.ScaleType.FIT_CENTER
        )

        // 200x200 放入 500x1000，受宽度限制，scale 应该是 500 / 200 = 2.5
        assertEquals(2.5f, scaleInfo.scaleFx, 0.01f)
        assertEquals(2.5f, scaleInfo.scaleFy, 0.01f)
        // Y 轴居中偏移：(1000 - 200 * 2.5) / 2 = 250
        assertEquals(250f, scaleInfo.tranFy, 0.01f)
        assertEquals(0f, scaleInfo.tranFx, 0.01f)
    }

    @Test
    fun testSvgaScaleInfoCenterCrop() {
        val scaleInfo = SvgaScaleInfo()
        scaleInfo.performScaleType(
            canvasWidth = 400f,
            canvasHeight = 400f,
            videoWidth = 200f,
            videoHeight = 100f,
            scaleType = ImageView.ScaleType.CENTER_CROP
        )

        // 宽长比 2:1，容器 1:1，CROP 模式按高度 400/100 = 4.0 缩放
        assertEquals(4.0f, scaleInfo.scaleFx, 0.01f)
        assertEquals(4.0f, scaleInfo.scaleFy, 0.01f)
        // X 轴负偏移裁切：(400 - 200 * 4) / 2 = -200
        assertEquals(-200f, scaleInfo.tranFx, 0.01f)
    }

    @Test
    fun testDynamicEntityOperations() {
        val dynamic = SVGADynamicEntity()
        dynamic.setDynamicText("Hello World", "title_key")
        dynamic.setDynamicHidden(true, "icon_key")

        assertEquals("Hello World", dynamic.dynamicText["title_key"])
        assertEquals(true, dynamic.dynamicHidden["icon_key"])
        assertTrue(dynamic.isTextDirty)

        dynamic.clear()
        assertNull(dynamic.dynamicText["title_key"])
        assertNull(dynamic.dynamicHidden["icon_key"])
    }

    @Test
    fun testRawImagesReleaseAndMemoryOptimization() {
        val movie = com.opensource.svgaplayer.proto.MovieEntity.newBuilder()
            .setVersion("2.0")
            .setParams(
                com.opensource.svgaplayer.proto.MovieParams.newBuilder()
                    .setViewBoxWidth(500f)
                    .setViewBoxHeight(500f)
                    .setFps(20)
                    .setFrames(30)
                    .build()
            )
            .build()

        val rawImages = mapOf(
            "img1" to byteArrayOf(1, 2, 3),
            "img2" to byteArrayOf(4, 5, 6)
        )

        val entity = com.opensource.svga.core.model.SVGAVideoEntity(movie, rawImages)
        assertEquals(2, entity.currentRawImages.size)

        // 验证手动释放原始未解码字节流
        entity.releaseRawImages()
        assertEquals(0, entity.currentRawImages.size)
    }

    @Test
    fun testOversizedSliceDownsamplingWhenCanvasScaleNearOne() {
        // 当画布尺寸 300x300 缩放到 285x285 容器时 (canvasScale = 0.95 < 1.0)，
        // 若动效内部存在超大图层切片 (1200x1200)，应结合 targetWidth/targetHeight 降采样至 4x (300x300 >= 285x285)
        val sampleOversized = SvgaBitmapDecoder.calculateInSampleSize(
            rawWidth = 1200,
            rawHeight = 1200,
            targetWidth = 285,
            targetHeight = 285,
            canvasScale = 0.95
        )
        assertEquals(4, sampleOversized)
    }

    @Test
    fun testDrawableStepFramesAndAlphaSupport() {
        val movie = com.opensource.svgaplayer.proto.MovieEntity.newBuilder()
            .setVersion("2.0")
            .setParams(
                com.opensource.svgaplayer.proto.MovieParams.newBuilder()
                    .setViewBoxWidth(300f)
                    .setViewBoxHeight(300f)
                    .setFps(24)
                    .setFrames(10)
                    .build()
            )
            .build()
        val entity = com.opensource.svga.core.model.SVGAVideoEntity(movie, emptyMap())
        val drawable = com.opensource.svga.core.drawable.SvgaAnimatableDrawable(entity).apply {
            repeatCount = 2
        }

        // 验证 alpha 设置与边界约束
        assertEquals(255, drawable.alpha)
        drawable.alpha = 128
        assertEquals(128, drawable.alpha)
        drawable.alpha = 500
        assertEquals(255, drawable.alpha)
        drawable.alpha = -10
        assertEquals(0, drawable.alpha)

        // 验证多帧步进补偿与循环终止
        assertEquals(0, drawable.currentFrame)
        drawable.stepFrames(3)
        assertEquals(3, drawable.currentFrame)
        // 再步进 8 帧跨越第 1 轮 (3 + 8 = 11 -> 第 2 轮 frame 1)
        drawable.stepFrames(8)
        assertEquals(1, drawable.currentFrame)
        // 再步进 15 帧达到 repeatCount = 2 上限，应停留在最后一帧 (frame 9)
        drawable.stepFrames(15)
        assertEquals(9, drawable.currentFrame)
    }

    @Test
    fun testPathEntityBuildWithRelativeAndSmoothCommands() {
        val pathEntity = com.opensource.svga.core.model.SVGAPathEntity(
            "M 10 20 L 30 40 H 60 h 10 V 80 v 20 C 10 10 20 20 30 30 S 50 50 60 60 s 10 10 20 20 Q 15 15 25 25 q 5 5 10 10 Z"
        )
        val outPath = android.graphics.Path()
        pathEntity.buildPath(outPath)
        // 第二次调用应命中 cachedPath
        pathEntity.buildPath(outPath)
    }
}
