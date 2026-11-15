package com.unidownco.data.provider

import com.unidownco.core.common.Platform
import com.unidownco.core.common.Resource
import com.unidownco.data.extractor.FacebookExtractor
import com.unidownco.data.extractor.GenericExtractor
import com.unidownco.data.extractor.InstagramExtractor
import com.unidownco.data.extractor.RedditExtractor
import com.unidownco.data.extractor.TikTokExtractor
import com.unidownco.data.extractor.TwitterExtractor
import com.unidownco.data.extractor.VideoExtractor
import com.unidownco.data.extractor.YouTubeExtractor
import com.unidownco.domain.models.VideoExtractionInfo

/**
 * Native in-app extractor provider running zero-dependency Kotlin extractors.
 * Provides high-speed, lightweight extraction without external binaries or GPL bloat.
 */
class NativeExtractorProvider(
    private val extractors: List<VideoExtractor> = listOf(
        YouTubeExtractor(),
        TikTokExtractor(),
        InstagramExtractor(),
        RedditExtractor(),
        TwitterExtractor(),
        FacebookExtractor(),
        GenericExtractor()
    )
) : ExtractorProvider {

    override val providerId: String = "native"
    override val displayName: String = "UniDownco Native Extractor"
    override val priority: Int = 80
    override val isAvailable: Boolean = true

    override fun getCapabilities(platform: Platform): Set<ExtractionCapability> {
        return when (platform) {
            Platform.YOUTUBE -> setOf(
                ExtractionCapability.DIRECT_STREAMS,
                ExtractionCapability.ADAPTIVE_STREAMS,
                ExtractionCapability.AUDIO_ONLY
            )
            Platform.REDDIT -> setOf(
                ExtractionCapability.DIRECT_STREAMS,
                ExtractionCapability.ADAPTIVE_STREAMS
            )
            else -> setOf(ExtractionCapability.DIRECT_STREAMS)
        }
    }

    override fun canHandle(url: String): Boolean = true

    fun findExtractor(url: String): VideoExtractor {
        return extractors.firstOrNull { it.canHandle(url) && it !is GenericExtractor }
            ?: extractors.last()
    }

    override suspend fun extract(url: String): Resource<VideoExtractionInfo> {
        val extractor = findExtractor(url)
        return extractor.extract(url)
    }
}
