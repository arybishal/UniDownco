package com.unidownco.data.extractor

import com.unidownco.core.common.Platform
import com.unidownco.core.common.Resource
import com.unidownco.domain.models.VideoExtractionInfo

/**
 * Platform-independent extraction interface.
 * Implements the extractor pattern specified in Master Spec 1.4:
 * VideoExtractor
 *   ├── FacebookExtractor
 *   ├── InstagramExtractor
 *   ├── TikTokExtractor
 *   ├── YouTubeExtractor
 *   ├── RedditExtractor
 *   └── GenericExtractor
 */
interface VideoExtractor {
    val platform: Platform
    fun canHandle(url: String): Boolean
    suspend fun extract(url: String): Resource<VideoExtractionInfo>
}
