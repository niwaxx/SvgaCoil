package com.opensource.svga.coil

import com.opensource.svga.core.parser.SVGAParser
import okio.buffer
import okio.source
import org.junit.Test
import java.io.File

class SvgaAssetBatchTest {

    @Test
    fun testScanAllAssets() {
        val assetDir = File("../app/src/main/assets")
        if (!assetDir.exists()) {
            println("Asset directory not found at ${assetDir.absolutePath}")
            return
        }

        val files = assetDir.listFiles() ?: return
        println("=== 扫描 assets 目录下共 ${files.size} 个素材 ===")

        var validCount = 0
        var invalidCount = 0

        files.sortedBy { it.length() }.forEach { file ->
            if (file.isDirectory) return@forEach
            val fileName = file.name
            val sizeKb = file.length() / 1024

            try {
                val entity = kotlinx.coroutines.runBlocking {
                    val source = file.source().buffer()
                    SVGAParser.parse(source)
                }
                validCount++
                val slices = entity.rawImages.size
                println("✅ [成功] $fileName (${sizeKb}KB) -> 尺寸: ${entity.videoSize.width.toInt()}x${entity.videoSize.height.toInt()}, 帧数: ${entity.frames}, 切片数: $slices")
            } catch (t: Throwable) {
                invalidCount++
                println("⚠️ [非SVGA或解析失败] $fileName (${sizeKb}KB) -> ${t.message ?: t.javaClass.simpleName}")
            }
        }

        println("=== 汇总: 成功解析 $validCount 个，失败/非SVGA $invalidCount 个 ===")
    }

    @Test
    fun testJinSeDownsample() {
        val file = File("../app/src/main/assets/金色方案一.svga")
        if (!file.exists()) {
            println("金色方案一 not found")
            return
        }
        val entity = kotlinx.coroutines.runBlocking {
            val source = file.source().buffer()
            SVGAParser.parse(source)
        }
        println("金色方案一 videoSize: ${entity.videoSize.width}x${entity.videoSize.height}, 切片数: ${entity.rawImages.size}")
        val targetWidth = 120
        val targetHeight = 120
        val canvasScale = maxOf(
            targetWidth.toDouble() / entity.videoSize.width,
            targetHeight.toDouble() / entity.videoSize.height
        )
        println("canvasScale = $canvasScale")

        entity.rawImages.entries.take(5).forEach { (k, bytes) ->
            val opt = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opt)
            val inSample = com.opensource.svga.core.bitmap.SvgaBitmapDecoder.calculateInSampleSize(
                rawWidth = opt.outWidth,
                rawHeight = opt.outHeight,
                canvasScale = canvasScale
            )
            println("切片 $k: ${opt.outWidth}x${opt.outHeight}, inSample = $inSample")
        }
    }
}
