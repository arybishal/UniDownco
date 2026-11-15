package com.unidownco.data.provider

import com.unidownco.core.common.Platform
import com.unidownco.core.common.Resource
import com.unidownco.domain.models.VideoExtractionInfo

/**
 * Pluggable Extractor Provider interface for multi-engine video extraction.
 * Enables decoupling the core native downloader from external or companion extraction engines (such as yt-dlp).
 */
interface ExtractorProvider {
    val providerId: String
    val displayName: String
    val priority: Int
    val isAvailable: Boolean

    fun getCapabilities(platform: Platform): Set<ExtractionCapability>
    fun canHandle(url: String): Boolean
    suspend fun extract(url: String): Resource<VideoExtractionInfo>
}
