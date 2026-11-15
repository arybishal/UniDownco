package com.unidownco.data.extractor

import com.unidownco.core.common.Platform
import com.unidownco.core.common.Resource
import com.unidownco.domain.models.VideoExtractionInfo

class UniversalExtractorRegistry {

    private val extractors: List<VideoExtractor> = listOf(
        YouTubeExtractor(),
        TikTokExtractor(),
        InstagramExtractor(),
        RedditExtractor(),
        TwitterExtractor(),
        FacebookExtractor(),
        GenericExtractor()
    )

    fun findExtractor(url: String): VideoExtractor {
        return extractors.firstOrNull { it.canHandle(url) && it !is GenericExtractor }
            ?: extractors.last() // GenericExtractor is last
    }

    suspend fun extract(url: String): Resource<VideoExtractionInfo> {
        val extractor = findExtractor(url)
        return extractor.extract(url)
    }
}
