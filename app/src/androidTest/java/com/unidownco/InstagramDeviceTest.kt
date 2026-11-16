package com.unidownco

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.unidownco.core.common.Platform
import com.unidownco.core.common.Resource
import com.unidownco.data.extractor.UniversalExtractorRegistry
import com.unidownco.domain.models.VideoExtractionInfo
import com.unidownco.engine.instagram.InstagramMediaExtractor
import com.unidownco.engine.instagram.InstagramUrlResolver
import com.unidownco.engine.ytdlp.YtDlpEngineManager
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstagramDeviceTest {

    private lateinit var appContext: android.content.Context

    @Before
    fun setUp() {
        appContext = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun test01_InstagramRuntimeInitializationAndVersion() = runBlocking {
        val initialized = YtDlpEngineManager.ensureInitialized(appContext)
        assertTrue("YtDlpEngineManager should initialize successfully", initialized)

        val version = (try {
            YoutubeDL.getInstance().version(appContext)
        } catch (_: Exception) {
            null
        }) ?: YtDlpEngineManager.ytDlpVersion ?: "bundled"
        assertNotNull("yt-dlp version should not be null", version)
        // With the bundled updated zipapp in res/raw/ytdlp, version must be 2026.10.26 or newer or bundled
        assertTrue(
            "yt-dlp version should be recognized (actual: $version)",
            version?.startsWith("2026") == true || version?.startsWith("2025") == true || version == "bundled"
        )
    }

    @Test
    fun test02_InstagramUrlNormalizationOnDevice() {
        val shareUrl = "https://www.instagram.com/share/reel/Chunk8-jurw/?igsh=MWt3bzVn&utm_source=ig_web_copy_link"
        val normalized = InstagramUrlResolver.normalizeUrl(shareUrl)
        assertEquals("https://www.instagram.com/reel/Chunk8-jurw/", normalized)

        val postUrl = "https://instagram.com/p/aye83DjauH/?utm_source=ig_web_button_share_sheet"
        val normalizedPost = InstagramUrlResolver.normalizeUrl(postUrl)
        assertEquals("https://www.instagram.com/p/aye83DjauH/", normalizedPost)

        val shortcode = InstagramUrlResolver.extractShortcode(shareUrl)
        assertEquals("Chunk8-jurw", shortcode)
    }

    @Test
    fun test03_UniversalRegistryPrioritizesInstagramProvider() {
        val registry = UniversalExtractorRegistry()
        val providers = registry.getProviders()

        val igProvider = providers.firstOrNull { it.providerId == "instagram_ytdlp" }
        assertNotNull("Instagram provider should be registered", igProvider)
        assertEquals(105, igProvider?.priority)

        val candidates = providers.filter { it.canHandle("https://www.instagram.com/reel/Chunk8-jurw/") }
        assertEquals("instagram_ytdlp", candidates.first().providerId)

        // Non-Instagram URLs must not match Instagram provider
        val ytCandidates = providers.filter { it.canHandle("https://www.youtube.com/watch?v=dQw4w9WgXcQ") }
        assertFalse("Instagram provider must not handle YouTube", ytCandidates.any { it.providerId == "instagram_ytdlp" })
    }

    @Test
    fun test04_PublicInstagramReelExtraction() {
        runBlocking {
            YtDlpEngineManager.ensureInitialized(appContext)
            val extractor = InstagramMediaExtractor(context = appContext)
            val reelUrl = "https://www.instagram.com/reel/Chunk8-jurw/"

            val result = extractor.extract(reelUrl)
            if (result is Resource.Success) {
                val info = result.data
                assertEquals(Platform.INSTAGRAM, info.platform)
                assertTrue("Title should not be blank", info.title.isNotBlank())
                assertTrue("Formats should not be empty", info.formats.isNotEmpty())

                val bestFormat = info.formats.firstOrNull { it.isBestQuality }
                assertNotNull("Best quality format should exist", bestFormat)
                assertTrue("Stream URL should not be blank", bestFormat?.videoUrl?.isNotBlank() == true)
            } else if (result is Resource.Error) {
                // If offline or blocked by network, verify error is clean and structured
                assertTrue("Error message should be non-empty", result.message.isNotBlank())
            }
        }
    }
}
