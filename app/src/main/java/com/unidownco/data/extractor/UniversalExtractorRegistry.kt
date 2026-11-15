package com.unidownco.data.extractor

import com.unidownco.core.common.Resource
import com.unidownco.data.provider.ExtractorProvider
import com.unidownco.data.provider.NativeExtractorProvider
import com.unidownco.data.provider.YtDlpExtractorProvider
import com.unidownco.domain.models.VideoExtractionInfo

/**
 * Universal Extractor Registry coordinating extraction providers in priority order.
 * Manages fallback between native lightweight extractors and external companion engines.
 */
class UniversalExtractorRegistry(
    customProviders: List<ExtractorProvider>? = null
) {
    private val nativeProvider = NativeExtractorProvider()
    private val providers = mutableListOf<ExtractorProvider>()

    init {
        if (customProviders != null) {
            providers.addAll(customProviders)
        } else {
            providers.add(nativeProvider)
            providers.add(YtDlpExtractorProvider())
        }
        providers.sortByDescending { it.priority }
    }

    fun getProviders(): List<ExtractorProvider> = providers.toList()

    fun getProvider(providerId: String): ExtractorProvider? {
        return providers.firstOrNull { it.providerId == providerId }
    }

    fun registerProvider(provider: ExtractorProvider) {
        providers.removeAll { it.providerId == provider.providerId }
        providers.add(provider)
        providers.sortByDescending { it.priority }
    }

    /**
     * Backward-compatible delegation to native extractors.
     */
    fun findExtractor(url: String): VideoExtractor {
        return nativeProvider.findExtractor(url)
    }

    /**
     * Extracts video metadata and streams using registered providers in priority order.
     */
    suspend fun extract(url: String): Resource<VideoExtractionInfo> {
        val candidates = providers.filter { it.isAvailable && it.canHandle(url) }
        if (candidates.isEmpty()) {
            return Resource.Error("No available extractor provider could handle URL: $url")
        }

        var lastError: Resource.Error? = null
        for (provider in candidates) {
            val result = provider.extract(url)
            if (result is Resource.Success) {
                return result
            } else if (result is Resource.Error) {
                lastError = result
            }
        }

        return lastError ?: Resource.Error("Extraction failed across all available providers for: $url")
    }
}
