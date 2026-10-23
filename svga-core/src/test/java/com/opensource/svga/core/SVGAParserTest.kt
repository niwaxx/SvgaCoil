package com.opensource.svga.core

import com.google.protobuf.ByteString
import com.opensource.svga.core.parser.SVGAParser
import com.opensource.svgaplayer.proto.MovieEntity
import com.opensource.svgaplayer.proto.MovieParams
import kotlinx.coroutines.runBlocking
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SVGAParserTest {

    @Test
    fun testIsZipFile() {
        val zipHeader = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00)
        assertTrue(SVGAParser.isZipFile(zipHeader))

        val nonZip = byteArrayOf(0x12, 0x34, 0x56, 0x78)
        assertFalse(SVGAParser.isZipFile(nonZip))
    }

    @Test
    fun testParseFromDeflate() = runBlocking {
        // 创建一个测试 MovieEntity
        val movie = MovieEntity.newBuilder()
            .setVersion("2.0")
            .setParams(
                MovieParams.newBuilder()
                    .setViewBoxWidth(500f)
                    .setViewBoxHeight(500f)
                    .setFps(30)
                    .setFrames(60)
                    .build()
            )
            .putImages("avatar", ByteString.copyFrom(byteArrayOf(1, 2, 3, 4)))
            .build()

        val rawBytes = movie.toByteArray()

        // 用 Deflater 压缩（模拟网络下发的 SVGA 2.0+ 数据）
        val deflater = Deflater()
        deflater.setInput(rawBytes)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!deflater.finished()) {
            val count = deflater.deflate(buf)
            out.write(buf, 0, count)
        }
        deflater.end()
        val deflatedBytes = out.toByteArray()

        // 验证通过 BufferedSource 解析
        val buffer = Buffer().write(deflatedBytes)
        val entity = SVGAParser.parse(buffer)

        assertNotNull(entity)
        assertEquals(500.0, entity.videoSize.width, 0.01)
        assertEquals(500.0, entity.videoSize.height, 0.01)
        assertEquals(30, entity.fps)
        assertEquals(60, entity.frames)
        assertTrue(entity.rawImages.containsKey("avatar"))
    }

    @Test
    fun testParseFromZip() = runBlocking {
        // 创建一个包含 movie.binary 和 test_img.png 的 ZIP 文件
        val movie = MovieEntity.newBuilder()
            .setVersion("2.0")
            .setParams(
                MovieParams.newBuilder()
                    .setViewBoxWidth(300f)
                    .setViewBoxHeight(600f)
                    .setFps(25)
                    .setFrames(100)
                    .build()
            )
            .build()

        val zipOut = ByteArrayOutputStream()
        ZipOutputStream(zipOut).use { zos ->
            zos.putNextEntry(ZipEntry("movie.binary"))
            zos.write(movie.toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("banner.png"))
            zos.write(byteArrayOf(9, 8, 7, 6))
            zos.closeEntry()
        }

        val zipBytes = zipOut.toByteArray()
        assertTrue(SVGAParser.isZipFile(zipBytes))

        val entity = SVGAParser.parse(zipBytes)
        assertNotNull(entity)
        assertEquals(300.0, entity.videoSize.width, 0.01)
        assertEquals(600.0, entity.videoSize.height, 0.01)
        assertEquals(25, entity.fps)
        assertEquals(100, entity.frames)
        assertTrue(entity.rawImages.containsKey("banner"))
    }

    @Test
    fun testParseRealAngelSvga() = runBlocking {
        val stream = javaClass.classLoader?.getResourceAsStream("angel.svga")
        assertNotNull("angel.svga should be in test resources", stream)
        val entity = SVGAParser.parse(stream!!)
        println("angel.svga imageKeys: " + entity.currentRawImages.keys)
        println("angel.svga sprites: " + entity.spriteList.mapNotNull { it.imageKey }.distinct())
        entity.spriteList.forEach { sprite ->
            val activeFrame = sprite.frames.firstOrNull { it.layout.width > 0 && it.alpha > 0.0 }
            if (activeFrame != null) {
                println("Active Sprite: ${sprite.imageKey}, layout: ${activeFrame.layout}, alpha: ${activeFrame.alpha}")
            }
        }
        assertNotNull(entity)
        assertTrue(entity.videoSize.width > 0)
        assertTrue(entity.videoSize.height > 0)
        assertTrue(entity.frames > 0)
        assertTrue(entity.spriteList.isNotEmpty())
    }

    @Test
    fun testParseRealRoseSvga() = runBlocking {
        val stream = javaClass.classLoader?.getResourceAsStream("rose.svga")
        assertNotNull("rose.svga should be in test resources", stream)
        val entity = SVGAParser.parse(stream!!)
        println("rose.svga imageKeys: " + entity.currentRawImages.keys)
        println("rose.svga sprites: " + entity.spriteList.mapNotNull { it.imageKey }.distinct())
        assertNotNull(entity)
        assertTrue(entity.videoSize.width > 0)
        assertTrue(entity.videoSize.height > 0)
        assertTrue(entity.frames > 0)
    }

    @Test
    fun testParseOnlineSvga1() = runBlocking {
        val stream = javaClass.classLoader?.getResourceAsStream("online_test1.svga")
        assertNotNull("online_test1.svga should be in test resources", stream)
        val entity = SVGAParser.parse(stream!!)
        println("online_test1.svga imageKeys: " + entity.currentRawImages.keys)
        println("online_test1.svga sprites: " + entity.spriteList.mapNotNull { it.imageKey }.distinct())
        assertNotNull(entity)
        assertTrue(entity.videoSize.width > 0)
        assertTrue(entity.videoSize.height > 0)
        assertTrue(entity.frames > 0)
        assertTrue(entity.spriteList.isNotEmpty())
    }

    @Test
    fun testParseOnlineSvga2M4a() = runBlocking {
        val stream = javaClass.classLoader?.getResourceAsStream("online_test2.m4a")
        assertNotNull("online_test2.m4a should be in test resources", stream)
        val entity = SVGAParser.parse(stream!!)
        println("online_test2.m4a imageKeys: " + entity.currentRawImages.keys)
        println("online_test2.m4a sprites: " + entity.spriteList.mapNotNull { it.imageKey }.distinct())
        assertNotNull(entity)
        assertTrue(entity.videoSize.width > 0)
        assertTrue(entity.videoSize.height > 0)
        assertTrue(entity.frames > 0)
        assertTrue(entity.spriteList.isNotEmpty())
    }
}
