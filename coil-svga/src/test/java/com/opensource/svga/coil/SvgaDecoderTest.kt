package com.opensource.svga.coil

import android.content.ContextWrapper
import coil3.ImageLoader
import coil3.asDrawable
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.size.Dimension
import coil3.size.Size
import com.google.protobuf.ByteString
import com.opensource.svga.core.dynamic.SVGADynamicEntity
import com.opensource.svgaplayer.proto.MovieEntity
import com.opensource.svgaplayer.proto.MovieParams
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.FileSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater

class SvgaDecoderTest {

    private val fakeContext = ContextWrapper(null)

    private fun createTestSvgaBytes(): ByteArray {
        val movie = MovieEntity.newBuilder()
            .setVersion("2.0")
            .setParams(
                MovieParams.newBuilder()
                    .setViewBoxWidth(400f)
                    .setViewBoxHeight(400f)
                    .setFps(20)
                    .setFrames(30)
                    .build()
            )
            .putImages("avatar_slot", ByteString.copyFrom(byteArrayOf(1, 2, 3, 4)))
            .build()

        val rawBytes = movie.toByteArray()
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
        return out.toByteArray()
    }

    @Test
    fun testMemoryCacheKeyWithDynamic() {
        val req1 = ImageRequest.Builder(fakeContext)
            .data("file:///android_asset/angel.svga")
            .svgaDynamic { setDynamicText("Title 1", "key1") }
            .build()

        val req2 = ImageRequest.Builder(fakeContext)
            .data("file:///android_asset/angel.svga")
            .svgaDynamic { setDynamicText("Title 2", "key1") }
            .build()

        // 验证先调用 svgaDynamic 再调用 data 时顺序无关，不会丢失 URL 或碰撞
        val reqReverseOrder = ImageRequest.Builder(fakeContext)
            .svgaDynamic { setDynamicText("Title 1", "key1") }
            .data("file:///android_asset/angel.svga")
            .build()

        val extra1 = req1.memoryCacheKeyExtras[SVGA_DYNAMIC_MEMORY_CACHE_EXTRA_KEY]
        val extra2 = req2.memoryCacheKeyExtras[SVGA_DYNAMIC_MEMORY_CACHE_EXTRA_KEY]
        val extraReverse = reqReverseOrder.memoryCacheKeyExtras[SVGA_DYNAMIC_MEMORY_CACHE_EXTRA_KEY]

        println("req1.memoryCacheKeyExtras: ${req1.memoryCacheKeyExtras}")
        println("req2.memoryCacheKeyExtras: ${req2.memoryCacheKeyExtras}")
        assertNotNull(extra1)
        assertNotNull(extra2)
        assertTrue(extra1 != extra2)
        assertEquals(extra1, extraReverse)
        assertEquals(req1.data, reqReverseOrder.data)
    }

    @Test
    fun testFactoryApplicable() {
        val svgaBytes = createTestSvgaBytes()
        val factory = SvgaDecoder.Factory()

        val buffer = Buffer().write(svgaBytes)
        val imageSource = ImageSource(source = buffer, fileSystem = FileSystem.SYSTEM)
        val result = SourceFetchResult(
            source = imageSource,
            mimeType = null,
            dataSource = DataSource.NETWORK
        )

        val options = Options(
            context = fakeContext,
            size = Size(Dimension(100), Dimension(100))
        )
        val imageLoader = ImageLoader.Builder(fakeContext).build()

        val decoder = factory.create(result, options, imageLoader)
        assertNotNull(decoder)
    }

    @Test
    fun testFactoryNotApplicableForPlainBytes() {
        val plainBytes = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7)
        val factory = SvgaDecoder.Factory()

        val buffer = Buffer().write(plainBytes)
        val imageSource = ImageSource(source = buffer, fileSystem = FileSystem.SYSTEM)
        val result = SourceFetchResult(
            source = imageSource,
            mimeType = null,
            dataSource = DataSource.NETWORK
        )

        val options = Options(
            context = fakeContext,
            size = Size.ORIGINAL
        )
        val imageLoader = ImageLoader.Builder(fakeContext).build()

        val decoder = factory.create(result, options, imageLoader)
        assertNull(decoder)
    }

    @Test
    fun testDecodeAndSizeProvider() = runBlocking {
        val svgaBytes = createTestSvgaBytes()
        val buffer = Buffer().write(svgaBytes)
        val imageSource = ImageSource(source = buffer, fileSystem = FileSystem.SYSTEM)

        val dynamicEntity = SVGADynamicEntity().apply {
            setDynamicText("VIP 9", "title_slot")
        }

        val request = ImageRequest.Builder(fakeContext)
            .size(100, 100)
            .svgaDynamic(dynamicEntity)
            .svgaRepeatCount(3)
            .build()

        val options = Options(
            context = fakeContext,
            size = Size(Dimension(100), Dimension(100)),
            extras = request.extras
        )

        val decoder = SvgaDecoder(imageSource, options)
        val decodeResult = decoder.decode()

        assertNotNull(decodeResult)
        assertTrue(decodeResult.isSampled)

        val image = decodeResult.image
        assertEquals(400, image.width)
        assertEquals(400, image.height)
    }

    @Test
    fun testDecodeOnlineSvga1() = runBlocking {
        val stream = javaClass.classLoader?.getResourceAsStream("online_test1.svga")
        assertNotNull(stream)
        val buffer = Buffer().readFrom(stream!!)
        val imageSource = ImageSource(source = buffer, fileSystem = FileSystem.SYSTEM)
        val result = SourceFetchResult(
            source = imageSource,
            mimeType = "application/octet-stream", // 测试服务端返回的非 svga mime
            dataSource = DataSource.NETWORK
        )

        val factory = SvgaDecoder.Factory()
        val options = Options(
            context = fakeContext,
            size = Size(Dimension(150), Dimension(150))
        )
        val imageLoader = ImageLoader.Builder(fakeContext).build()

        val decoder = factory.create(result, options, imageLoader)
        assertNotNull("Factory should detect ZLIB SVGA even with application/octet-stream mime", decoder)

        val decodeResult = decoder!!.decode()
        assertNotNull(decodeResult)
        assertEquals(300, decodeResult!!.image.width)
        assertEquals(300, decodeResult.image.height)
    }

    @Test
    fun testDecodeOnlineSvga2WithM4aExtension() = runBlocking {
        val stream = javaClass.classLoader?.getResourceAsStream("online_test2.m4a")
        assertNotNull(stream)
        val buffer = Buffer().readFrom(stream!!)
        val imageSource = ImageSource(source = buffer, fileSystem = FileSystem.SYSTEM)
        val result = SourceFetchResult(
            source = imageSource,
            mimeType = "audio/mp4", // 测试服务端返回的伪装音频 mime
            dataSource = DataSource.NETWORK
        )

        val factory = SvgaDecoder.Factory()
        val options = Options(
            context = fakeContext,
            size = Size(Dimension(120), Dimension(120))
        )
        val imageLoader = ImageLoader.Builder(fakeContext).build()

        val decoder = factory.create(result, options, imageLoader)
        assertNotNull("Factory should detect ZLIB SVGA even with .m4a extension and audio/mp4 mime", decoder)

        val decodeResult = decoder!!.decode()
        assertNotNull(decodeResult)
        assertEquals(240, decodeResult!!.image.width)
        assertEquals(240, decodeResult.image.height)
    }

    @Test
    fun testFlyweightTemplateCacheHits() = runBlocking {
        SvgaTemplateCache.clear()

        val svgaBytes = createTestSvgaBytes()
        val key = "test_shared_angel_key"

        // 模拟第 1 个列表项（带有用户 1 的动态文字）
        val request1 = ImageRequest.Builder(fakeContext)
            .svgaCacheKey(key)
            .svgaDynamic { setDynamicText("User 1", "avatar_slot") }
            .build()
        val options1 = Options(
            context = fakeContext,
            size = Size(Dimension(100), Dimension(100)),
            diskCacheKey = key,
            extras = request1.extras
        )
        val decoder1 = SvgaDecoder(
            ImageSource(source = Buffer().write(svgaBytes), fileSystem = FileSystem.SYSTEM),
            options1
        )
        val result1 = decoder1.decode()

        // 模拟第 2 个列表项（带有用户 2 的动态文字，但请求同一个 SVGA 资源）
        val request2 = ImageRequest.Builder(fakeContext)
            .svgaCacheKey(key)
            .svgaDynamic { setDynamicText("User 2", "avatar_slot") }
            .build()
        val options2 = Options(
            context = fakeContext,
            size = Size(Dimension(100), Dimension(100)),
            diskCacheKey = key,
            extras = request2.extras
        )
        val decoder2 = SvgaDecoder(
            ImageSource(source = Buffer().write(svgaBytes), fileSystem = FileSystem.SYSTEM),
            options2
        )
        val result2 = decoder2.decode()

        // 验证缓存命中：SvgaTemplateCache 中有且仅有 1 个模板实例
        val cachedEntity = SvgaTemplateCache.get(key)
        assertNotNull(cachedEntity)
        assertEquals(1, SvgaTemplateCache.size())

        // 验证两次请求产出的 drawable 其底层引用的 SVGAVideoEntity 严格为同一享元实例（===）
        val d1 = (result1.image as coil3.DrawableImage).drawable as CoilSvgaDrawable
        val d2 = (result2.image as coil3.DrawableImage).drawable as CoilSvgaDrawable
        assertTrue("Two drawables must share the exact same flyweight SVGAVideoEntity instance", d1.videoItem === d2.videoItem)
        assertTrue("Shared entity must equal the cached entity", d1.videoItem === cachedEntity)

        // 验证各自的动态插槽互不干扰
        assertEquals("User 1", d1.dynamicItem?.dynamicText?.get("avatar_slot"))
        assertEquals("User 2", d2.dynamicItem?.dynamicText?.get("avatar_slot"))
    }

    @Test
    fun testResolutionTierDownwardsReuse() = runBlocking {
        SvgaTemplateCache.clear()

        val svgaBytes = createTestSvgaBytes()
        val key = "test_tier_reuse_key"

        // 1. 先在大尺寸（FULL，800x800px，对应 320dp > 240dp）下加载
        val fullRequest = ImageRequest.Builder(fakeContext)
            .svgaCacheKey(key)
            .build()
        val fullOptions = Options(
            context = fakeContext,
            size = Size(Dimension(800), Dimension(800)),
            diskCacheKey = key,
            extras = fullRequest.extras
        )
        val fullDecoder = SvgaDecoder(
            ImageSource(source = Buffer().write(svgaBytes), fileSystem = FileSystem.SYSTEM),
            fullOptions
        )
        val fullResult = fullDecoder.decode()

        assertEquals(1, SvgaTemplateCache.size())
        val fullEntity = SvgaTemplateCache.get("${key}#FULL")
        assertNotNull("Should create and cache FULL tier template", fullEntity)

        // 2. 接着在小尺寸（THUMBNAIL，60x60px，对应 24dp <= 96dp）下加载同一个动效
        val thumbRequest = ImageRequest.Builder(fakeContext)
            .svgaCacheKey(key)
            .build()
        val thumbOptions = Options(
            context = fakeContext,
            size = Size(Dimension(60), Dimension(60)),
            diskCacheKey = key,
            extras = thumbRequest.extras
        )
        val thumbDecoder = SvgaDecoder(
            ImageSource(source = Buffer().write(svgaBytes), fileSystem = FileSystem.SYSTEM),
            thumbOptions
        )
        val thumbResult = thumbDecoder.decode()

        // 验证向下兼容：小尺寸直接复用大尺寸模板，不需要生成 THUMBNAIL 切片，缓存总数仍为 1
        assertEquals("Template cache count should still be 1 because THUMBNAIL reuses FULL", 1, SvgaTemplateCache.size())
        assertNull("Should NOT create duplicate THUMBNAIL template", SvgaTemplateCache.get("${key}#THUMBNAIL"))

        val dFull = (fullResult.image as coil3.DrawableImage).drawable as CoilSvgaDrawable
        val dThumb = (thumbResult.image as coil3.DrawableImage).drawable as CoilSvgaDrawable
        assertTrue("Thumb request must reuse existing FULL SVGAVideoEntity instance", dThumb.videoItem === dFull.videoItem)
    }

    @Test
    fun testResolutionTierSmallToBigUpgrade() = runBlocking {
        SvgaTemplateCache.clear()

        val svgaBytes = createTestSvgaBytes()
        val key = "test_tier_upgrade_key"

        // 1. 先在极小尺寸（THUMBNAIL，60x60px，对应 24dp）下加载
        val thumbOptions = Options(
            context = fakeContext,
            size = Size(Dimension(60), Dimension(60)),
            diskCacheKey = key
        )
        val thumbDecoder = SvgaDecoder(
            ImageSource(source = Buffer().write(svgaBytes), fileSystem = FileSystem.SYSTEM),
            thumbOptions
        )
        val thumbResult = thumbDecoder.decode()

        assertEquals(1, SvgaTemplateCache.size())
        val thumbEntity = SvgaTemplateCache.get("${key}#THUMBNAIL")
        assertNotNull("Should cache THUMBNAIL tier template", thumbEntity)

        // 2. 随后在大尺寸（FULL，800x800px）下加载
        // 由于低清 THUMBNAIL 无法满足大尺寸的画质要求（防止发虚模糊），此时必须补充解码一个 FULL 模板
        val fullOptions = Options(
            context = fakeContext,
            size = Size(Dimension(800), Dimension(800)),
            diskCacheKey = key
        )
        val fullDecoder = SvgaDecoder(
            ImageSource(source = Buffer().write(svgaBytes), fileSystem = FileSystem.SYSTEM),
            fullOptions
        )
        val fullResult = fullDecoder.decode()

        assertEquals("Template cache should now hold 2 tiers (THUMBNAIL and FULL)", 2, SvgaTemplateCache.size())
        val fullEntity = SvgaTemplateCache.get("${key}#FULL")
        assertNotNull("Should have FULL tier template", fullEntity)

        val dThumb = (thumbResult.image as coil3.DrawableImage).drawable as CoilSvgaDrawable
        val dFull = (fullResult.image as coil3.DrawableImage).drawable as CoilSvgaDrawable
        assertTrue("Thumb and Full have their respective tier-optimized entities", dThumb.videoItem !== dFull.videoItem)
    }

    @Test
    fun testFactoryRejectsStandardImageMimesAndMagicNumbers() {
        val factory = SvgaDecoder.Factory()
        val options = Options(context = fakeContext, size = Size.ORIGINAL)
        val imageLoader = ImageLoader.Builder(fakeContext).build()

        // 1. 标准 image/png MIME 即使内部流是任意字节也应立即放行返回 null
        val pngMimeResult = SourceFetchResult(
            source = ImageSource(source = Buffer().write(createTestSvgaBytes()), fileSystem = FileSystem.SYSTEM),
            mimeType = "image/png",
            dataSource = DataSource.NETWORK
        )
        assertNull(factory.create(pngMimeResult, options, imageLoader))

        // 2. PNG 魔数头 (\x89PNG) 且无 MIME 时应返回 null
        val pngHeader = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val pngMagicResult = SourceFetchResult(
            source = ImageSource(source = Buffer().write(pngHeader), fileSystem = FileSystem.SYSTEM),
            mimeType = null,
            dataSource = DataSource.NETWORK
        )
        assertNull(factory.create(pngMagicResult, options, imageLoader))

        // 3. 伪造满足 (b0 & 0x0F == 8) 且整除 31 但 CINFO=0 的非标准字节头 (0x08, 0x1D) 应被拒绝
        val fakeZlibHeader = byteArrayOf(0x08, 0x1D, 0x00, 0x00)
        val fakeZlibResult = SourceFetchResult(
            source = ImageSource(source = Buffer().write(fakeZlibHeader), fileSystem = FileSystem.SYSTEM),
            mimeType = null,
            dataSource = DataSource.NETWORK
        )
        assertNull(factory.create(fakeZlibResult, options, imageLoader))
    }

    @Test
    fun testAutoRecycleOnEndProtectsSharedCacheAndCleansEphemeral() = runBlocking {
        SvgaTemplateCache.clear()
        val svgaBytes = createTestSvgaBytes()
        val key = "test_auto_recycle_protection_key"

        // 1. 显式设置较大阈值使模板进入 L1 缓存 (isEphemeral = false)，同时开启 autoRecycleOnEnd
        val cachedReq = ImageRequest.Builder(fakeContext)
            .svgaCacheKey(key)
            .svgaMaxCacheableBytes(16 * 1024 * 1024L)
            .svgaAutoRecycleOnEnd(true)
            .build()
        val cachedOptions = Options(
            context = fakeContext,
            size = Size(Dimension(200), Dimension(200)),
            diskCacheKey = key,
            extras = cachedReq.extras
        )
        val cachedResult = SvgaDecoder(
            ImageSource(source = Buffer().write(svgaBytes), fileSystem = FileSystem.SYSTEM),
            cachedOptions
        ).decode()
        val cachedDrawable = (cachedResult.image as coil3.DrawableImage).drawable as CoilSvgaDrawable
        assertEquals(false, cachedDrawable.isEphemeral)
        assertEquals(1, SvgaTemplateCache.entryCount())

        // 2. 纯短暂模式 (svgaEphemeral -> isEphemeral = true)，不应进入全局 L1 缓存
        val ephemeralKey = "test_ephemeral_only_key"
        val ephemeralReq = ImageRequest.Builder(fakeContext)
            .svgaCacheKey(ephemeralKey)
            .svgaEphemeral(autoRecycleOnEnd = true)
            .build()
        val ephemeralOptions = Options(
            context = fakeContext,
            size = Size(Dimension(200), Dimension(200)),
            diskCacheKey = ephemeralKey,
            extras = ephemeralReq.extras
        )
        val ephemeralResult = SvgaDecoder(
            ImageSource(source = Buffer().write(svgaBytes), fileSystem = FileSystem.SYSTEM),
            ephemeralOptions
        ).decode()
        val ephemeralDrawable = (ephemeralResult.image as coil3.DrawableImage).drawable as CoilSvgaDrawable
        assertTrue(ephemeralDrawable.isEphemeral)
        assertNull(SvgaTemplateCache.get(ephemeralKey))
    }

    @Test
    fun testConstantStateCreatesIndependentDrawableSharingEntity() = runBlocking {
        SvgaTemplateCache.clear()
        val svgaBytes = createTestSvgaBytes()
        val options = Options(
            context = fakeContext,
            size = Size(Dimension(200), Dimension(200)),
            diskCacheKey = "test_constant_state"
        )
        val result = SvgaDecoder(
            ImageSource(source = Buffer().write(svgaBytes), fileSystem = FileSystem.SYSTEM),
            options
        ).decode()

        // 验证 DecodeResult.image 默认 shareable = false，避免多视图共用同一个 Drawable 实例
        assertEquals(false, result.image.shareable)

        val d1 = (result.image as coil3.DrawableImage).drawable as CoilSvgaDrawable
        val d2 = d1.constantState.newDrawable() as CoilSvgaDrawable

        assertTrue("newDrawable() must create a distinct Drawable instance", d1 !== d2)
        assertTrue("Cloned Drawable must share the exact same flyweight SVGAVideoEntity", d1.videoItem === d2.videoItem)
    }
}
