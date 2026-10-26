package com.opensource.svga.core.parser

import com.opensource.svga.core.model.SVGAVideoEntity
import com.opensource.svga.core.util.SvgaLogger
import com.opensource.svgaplayer.proto.MovieEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.BufferedSource
import okio.buffer
import okio.source
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.InflaterInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object SVGAParser {

    /**
     * 判断字节头是否为 ZIP 文件特征魔数 (PK\x03\x04)
     */
    fun isZipFile(bytes: ByteArray): Boolean {
        return bytes.size >= 4 &&
                bytes[0] == 0x50.toByte() && // 'P'
                bytes[1] == 0x4B.toByte() && // 'K'
                bytes[2] == 0x03.toByte() &&
                bytes[3] == 0x04.toByte()
    }

    /**
     * 判断是否为 MP3 ID3 文件头 (73, 68, 51)
     */
    private fun isMp3(bytes: ByteArray): Boolean {
        return bytes.size >= 3 &&
                bytes[0] == 73.toByte() &&
                bytes[1] == 68.toByte() &&
                bytes[2] == 51.toByte()
    }

    /**
     * 协程挂起函数：从 Okio [BufferedSource] 中流式解析 SVGA 动效（零中间全量字节数组拷贝）
     */
    suspend fun parse(source: BufferedSource): SVGAVideoEntity = withContext(Dispatchers.Default) {
        val peek = source.peek()
        val isZip = if (peek.request(4)) {
            val buf = peek.buffer
            buf[0] == 0x50.toByte() && buf[1] == 0x4B.toByte() && buf[2] == 0x03.toByte() && buf[3] == 0x04.toByte()
        } else {
            false
        }

        SvgaLogger.debug { "Parsing SVGA stream (isZip=$isZip)..." }

        val entity = try {
            if (isZip) {
                parseFromZipStream(source.inputStream())
            } else {
                parseFromDeflateStream(source.inputStream())
            }
        } catch (t: Throwable) {
            SvgaLogger.error(throwable = t) { "Failed to parse SVGA stream: ${t.message}" }
            throw t
        }

        SvgaLogger.info {
            "SVGA parsed successfully: ${entity.videoSize.width.toInt()}x${entity.videoSize.height.toInt()}, fps=${entity.fps}, frames=${entity.frames}, sprites=${entity.spriteList.size}, slices=${entity.currentRawImages.size}"
        }
        entity
    }

    /**
     * 协程挂起函数：从 [InputStream] 中解析 SVGA 动效
     */
    suspend fun parse(inputStream: InputStream): SVGAVideoEntity = withContext(Dispatchers.Default) {
        val source = inputStream.source().buffer()
        parse(source)
    }

    /**
     * 从字节数组中解析 SVGA 动效（纯内存解析，兼容离线测试）
     */
    fun parse(bytes: ByteArray): SVGAVideoEntity {
        return if (isZipFile(bytes)) {
            parseFromZipStream(ByteArrayInputStream(bytes))
        } else {
            parseFromDeflateStream(ByteArrayInputStream(bytes))
        }
    }

    /**
     * 解析 ZIP 格式的 SVGA 文件（流式解析，不预先分配全量字节）
     */
    private fun parseFromZipStream(inputStream: InputStream): SVGAVideoEntity {
        var movieBytes: ByteArray? = null
        val rawImages = HashMap<String, ByteArray>()

        ZipInputStream(inputStream).use { zipStream ->
            var entry: ZipEntry? = zipStream.nextEntry
            val buffer = ByteArray(4096)
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory) {
                    val out = ByteArrayOutputStream()
                    var len: Int
                    while (zipStream.read(buffer).also { len = it } > 0) {
                        out.write(buffer, 0, len)
                    }
                    val entryBytes = out.toByteArray()

                    if (name.equals("movie.binary", ignoreCase = true)) {
                        movieBytes = entryBytes
                    } else if (name.endsWith(".png", ignoreCase = true)) {
                        val key = name.substringBeforeLast(".png")
                        if (!isMp3(entryBytes)) {
                            rawImages[key] = entryBytes
                        }
                    } else if (!isMp3(entryBytes) && entryBytes.isNotEmpty()) {
                        rawImages[name] = entryBytes
                    }
                }
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
        }

        if (movieBytes == null) {
            SvgaLogger.error { "SVGA parse failed: zip archive does not contain 'movie.binary'" }
        }
        requireNotNull(movieBytes) { "SVGA zip archive does not contain 'movie.binary'" }

        val movieEntity = MovieEntity.parseFrom(movieBytes)

        movieEntity.imagesMap.forEach { (key, byteString) ->
            val imgBytes = byteString.toByteArray()
            if (!rawImages.containsKey(key) && !isMp3(imgBytes)) {
                rawImages[key] = imgBytes
            }
        }

        return SVGAVideoEntity(movieEntity, rawImages)
    }

    /**
     * 解析 ZLIB Deflated 压缩的 SVGA 2.0+ 二进制流。
     * 【核心性能优化】：使用 InflaterInputStream 边解压边直接送入 Protobuf parseFrom，
     * 彻底杜绝一次性分配 70MB+ 临时解压 ByteArray 导致的瞬时内存雪崩与 OOM！
     */
    private fun parseFromDeflateStream(inputStream: InputStream): SVGAVideoEntity {
        val inflaterStream = InflaterInputStream(inputStream)
        val movieEntity = MovieEntity.parseFrom(inflaterStream)
        val rawImages = HashMap<String, ByteArray>(movieEntity.imagesCount)

        movieEntity.imagesMap.forEach { (key, byteString) ->
            val imgBytes = byteString.toByteArray()
            if (!isMp3(imgBytes)) {
                rawImages[key] = imgBytes
            }
        }

        return SVGAVideoEntity(movieEntity, rawImages)
    }
}
