package com.unidownco

import com.google.common.truth.Truth.assertThat
import com.unidownco.core.common.Platform
import com.unidownco.core.common.Resource
import com.unidownco.data.extractor.UniversalExtractorRegistry
import com.unidownco.data.provider.ExtractionCapability
import com.unidownco.data.provider.ExtractorProvider
import com.unidownco.data.provider.NativeExtractorProvider
import com.unidownco.data.provider.YtDlpExtractorProvider
import com.unidownco.domain.models.FormatOption
import com.unidownco.domain.models.VideoExtractionInfo
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ExtractorProviderTest {

    @Test
    fun testNativeExtractorProvider_ReportsExpectedCapabilities() {
        val provider = NativeExtractorProvider()
        assertThat(provider.providerId).isEqualTo("native")
        assertThat(provider.isAvailable).isTrue()

        val ytCapabilities = provider.getCapabilities(Platform.YOUTUBE)
        assertThat(ytCapabilities).contains(ExtractionCapability.DIRECT_STREAMS)
        assertThat(ytCapabilities).contains(ExtractionCapability.ADAPTIVE_STREAMS)

        val redditCapabilities = provider.getCapabilities(Platform.REDDIT)
        assertThat(redditCapabilities).contains(ExtractionCapability.ADAPTIVE_STREAMS)
    }

    @Test
    fun testRegistry_OrdersProvidersByPriority() {
        val highPriority = object : ExtractorProvider {
            override val providerId: String = "high"
            override val displayName: String = "High Priority"
            override val priority: Int = 200
            override val isAvailable: Boolean = true
            override fun getCapabilities(platform: Platform) = emptySet<ExtractionCapability>()
            override fun canHandle(url: String) = true
            override suspend fun extract(url: String) = Resource.Error("high failed")
        }

        val lowPriority = object : ExtractorProvider {
            override val providerId: String = "low"
            override val displayName: String = "Low Priority"
            override val priority: Int = 10
            override val isAvailable: Boolean = true
            override fun getCapabilities(platform: Platform) = emptySet<ExtractionCapability>()
            override fun canHandle(url: String) = true
            override suspend fun extract(url: String) = Resource.Success(
                VideoExtractionInfo(url, Platform.GENERIC, "Low Success")
            )
        }

        val registry = UniversalExtractorRegistry(listOf(lowPriority, highPriority))
        val providers = registry.getProviders()

        assertThat(providers.first().providerId).isEqualTo("high")
        assertThat(providers.last().providerId).isEqualTo("low")
    }

    @Test
    fun testRegistry_FallsBackToNextProviderOnFailure() = runTest {
        val failingPrimary = object : ExtractorProvider {
            override val providerId: String = "primary"
            override val displayName: String = "Primary"
            override val priority: Int = 100
            override val isAvailable: Boolean = true
            override fun getCapabilities(platform: Platform) = emptySet<ExtractionCapability>()
            override fun canHandle(url: String) = true
            override suspend fun extract(url: String) = Resource.Error("Primary bot gate blocked")
        }

        val fallbackSuccess = object : ExtractorProvider {
            override val providerId: String = "fallback"
            override val displayName: String = "Fallback Engine"
            override val priority: Int = 50
            override val isAvailable: Boolean = true
            override fun getCapabilities(platform: Platform) = setOf(ExtractionCapability.JS_DECIPHER)
            override fun canHandle(url: String) = true
            override suspend fun extract(url: String) = Resource.Success(
                VideoExtractionInfo(
                    url = url,
                    platform = Platform.YOUTUBE,
                    title = "Fallback Rescued Title",
                    formats = listOf(
                        FormatOption(
                            formatId = "18",
                            resolution = "360p",
                            videoUrl = "https://example.com/stream.mp4"
                        )
                    )
                )
            )
        }

        val registry = UniversalExtractorRegistry(listOf(failingPrimary, fallbackSuccess))
        val result = registry.extract("https://youtube.com/watch?v=fallback_test")

        assertThat(result).isInstanceOf(Resource.Success::class.java)
        val info = (result as Resource.Success).data
        assertThat(info.title).isEqualTo("Fallback Rescued Title")
    }

    @Test
    fun testYtDlpProvider_WithMockExtractor_SuccessfullyExtracts() = runTest {
        val mockJson = """
            {
              "title": "Mock Video Title",
              "extractor_key": "youtube",
              "formats": [
                {
                  "format_id": "18",
                  "url": "https://example.com/mock.mp4",
                  "height": 360,
                  "vcodec": "h264",
                  "acodec": "aac"
                }
              ]
            }
        """.trimIndent()

        val ytDlpProvider = YtDlpExtractorProvider(
            jsonExtractor = { mockJson }
        )

        val result = ytDlpProvider.extract("https://youtube.com/watch?v=mock")
        assertThat(result).isInstanceOf(Resource.Success::class.java)
        val data = (result as Resource.Success).data
        assertThat(data.title).isEqualTo("Mock Video Title")
        assertThat(data.formats).isNotEmpty()
    }
}
