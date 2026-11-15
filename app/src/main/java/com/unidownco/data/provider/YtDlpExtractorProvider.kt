package com.unidownco.data.provider

import com.unidownco.core.common.Platform
import com.unidownco.core.common.Resource
import com.unidownco.data.extractor.YtDlpExtractorBridge
import com.unidownco.domain.models.VideoExtractionInfo

/**
 * Extractor provider for yt-dlp compatibility.
 * Acts as an architectural decoupling layer for yt-dlp execution (IPC companion or local engine).
 * Decouples GPL-3.0 copyleft code and heavy binary dependencies from the core UniDownco application.
 */
class YtDlpExtractorProvider(
    private val companionServiceAvailable: Boolean = false,
    private val jsonExtractor: (suspend (url: String) -> String?)? = null
) : ExtractorProvider {

    override val providerId: String = "yt-dlp"
    override val displayName: String = "yt-dlp Companion Engine"
    override val priority: Int = 50
    override val isAvailable: Boolean = companionServiceAvailable || jsonExtractor != null

    override fun getCapabilities(platform: Platform): Set<ExtractionCapability> {
        return setOf(
            ExtractionCapability.DIRECT_STREAMS,
            ExtractionCapability.ADAPTIVE_STREAMS,
            ExtractionCapability.AUDIO_ONLY,
            ExtractionCapability.HDR_METADATA,
            ExtractionCapability.JS_DECIPHER
        )
    }

    override fun canHandle(url: String): Boolean = true

    override suspend fun extract(url: String): Resource<VideoExtractionInfo> {
        val extractor = jsonExtractor
        if (extractor != null) {
            val json = extractor(url)
            if (!json.isNullOrBlank()) {
                val parsed = YtDlpExtractorBridge.parseYtDlpJson(json, url)
                if (parsed != null) {
                    return Resource.Success(parsed)
                }
            }
            return Resource.Error("yt-dlp JSON extraction produced invalid or unparseable output for: $url")
        }

        return Resource.Error(
            "yt-dlp extraction engine is not connected. " +
            "To preserve Apache/MIT licensing and avoid W^X binary restrictions, yt-dlp runs via optional companion service."
        )
    }
}
