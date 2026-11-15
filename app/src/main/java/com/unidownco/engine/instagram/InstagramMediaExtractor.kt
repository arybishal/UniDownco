package com.unidownco.engine.instagram

import android.content.Context
import com.unidownco.UniDownApplication
import com.unidownco.core.common.Platform
import com.unidownco.core.common.Resource
import com.unidownco.core.logging.UniLog
import com.unidownco.data.extractor.YtDlpExtractorBridge
import com.unidownco.data.provider.ExtractionCapability
import com.unidownco.data.provider.ExtractorProvider
import com.unidownco.domain.models.FormatOption
import com.unidownco.domain.models.VideoExtractionInfo
import com.unidownco.engine.ytdlp.YtDlpEngineManager
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoFormat
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dedicated Instagram media extractor adapter powered by the existing in-app yt-dlp runtime.
 * Provides canonical URL normalization, format inspection, public extraction optimization,
 * and clear user-facing diagnostics.
 */
class InstagramMediaExtractor(
    private val context: Context? = null,
    private val jsonExtractor: (suspend (url: String) -> String?)? = null
) : ExtractorProvider {

    override val providerId: String = "instagram_ytdlp"
    override val displayName: String = "yt-dlp Instagram Engine"
    override val priority: Int = 105 // Takes priority for Instagram URLs over generic yt-dlp (100)
    override val isAvailable: Boolean = true

    override fun getCapabilities(platform: Platform): Set<ExtractionCapability> {
        return if (platform == Platform.INSTAGRAM) {
            setOf(
                ExtractionCapability.DIRECT_STREAMS,
                ExtractionCapability.ADAPTIVE_STREAMS
            )
        } else {
            emptySet()
        }
    }

    override fun canHandle(url: String): Boolean {
        return InstagramUrlResolver.isInstagramUrl(url)
    }

    override suspend fun extract(url: String): Resource<VideoExtractionInfo> = withContext(Dispatchers.IO) {
        val validation = InstagramUrlResolver.validateInstagramUrl(url)
        if (validation.isFailure) {
            return@withContext Resource.Error("Invalid Instagram URL: ${validation.exceptionOrNull()?.message}")
        }
        val canonicalUrl = validation.getOrThrow()

        // Fast path for mock/unit testing without Android native binaries
        val testExtractor = jsonExtractor
        if (testExtractor != null) {
            val json = testExtractor(canonicalUrl)
            if (!json.isNullOrBlank()) {
                val parsed = YtDlpExtractorBridge.parseYtDlpJson(json, canonicalUrl)
                if (parsed != null) {
                    return@withContext Resource.Success(parsed)
                }
            }
            return@withContext Resource.Error("Instagram mock extraction produced invalid JSON for: $canonicalUrl")
        }

        val appContext = context ?: try {
            UniDownApplication.appContext
        } catch (_: Exception) {
            null
        }

        if (appContext == null) {
            return@withContext Resource.Error("Application context not available for yt-dlp Instagram extractor")
        }

        val initialized = YtDlpEngineManager.ensureInitialized(appContext)
        if (!initialized) {
            val reason = YtDlpEngineManager.initializationError ?: "Runtime failed to initialize"
            return@withContext Resource.Error("yt-dlp engine unavailable: $reason")
        }

        try {
            UniLog.i(TAG, "Starting Instagram extraction for: $canonicalUrl")
            val request = YoutubeDLRequest(canonicalUrl)
            request.addOption("--no-playlist")
            request.addOption("--no-update")
            request.addOption("--socket-timeout", "20")

            val videoInfo: VideoInfo = YoutubeDL.getInstance().getInfo(request)
            UniLog.i(TAG, "yt-dlp Instagram extraction SUCCESS: title=${videoInfo.title}, formats=${videoInfo.formats?.size}")

            val domainInfo = mapInstagramVideoInfo(videoInfo, canonicalUrl)
            Resource.Success(domainInfo)
        } catch (ye: YoutubeDLException) {
            UniLog.e(TAG, "Instagram YoutubeDLException: ${ye.message}")
            val errorDetails = classifyError(ye.message, canonicalUrl)
            Resource.Error(errorDetails.second)
        } catch (e: Exception) {
            UniLog.e(TAG, "Instagram extraction exception: ${e.message}", e)
            val errorDetails = classifyError(e.message, canonicalUrl)
            Resource.Error(errorDetails.second)
        }
    }

    fun mapInstagramVideoInfo(videoInfo: VideoInfo, originalUrl: String): VideoExtractionInfo {
        val title = videoInfo.title ?: videoInfo.fulltitle ?: "Instagram Reel"
        val uploader = videoInfo.uploader ?: "Instagram User"
        val duration = videoInfo.duration.toLong()
        val thumbnail = videoInfo.thumbnail?.ifBlank { null }

        val rawFormats: List<VideoFormat> = videoInfo.formats ?: emptyList()
        val audioTracks = rawFormats.filter { f ->
            (f.vcodec == "none" || f.vcodec.isNullOrBlank()) &&
                    f.acodec != "none" && !f.acodec.isNullOrBlank() &&
                    !f.url.isNullOrBlank()
        }
        val bestAudio = audioTracks.maxByOrNull { it.abr }

        val videoOptions = mutableListOf<FormatOption>()
        for (f in rawFormats) {
            val hasVideo = (f.vcodec != "none" && !f.vcodec.isNullOrBlank()) ||
                    (f.width > 0 && f.height > 0) ||
                    (f.ext == "mp4" && f.vcodec != "none") ||
                    f.formatNote?.contains("video", ignoreCase = true) == true

            val streamUrl = f.url ?: f.manifestUrl
            if (hasVideo && !streamUrl.isNullOrBlank()) {
                val isSeparateAudio = (f.acodec == "none" || f.acodec.isNullOrBlank()) && bestAudio != null
                val pairedAudioUrl = if (isSeparateAudio) bestAudio?.url else null
                val resolvedAudioCodec = if (isSeparateAudio) bestAudio?.acodec else (f.acodec ?: "aac")

                val height = if (f.height > 0) f.height else 1080
                val width = if (f.width > 0) f.width else 1920
                val res = if (f.height > 0) "${f.height}p" else f.formatNote ?: "Original"
                val ext = f.ext ?: "mp4"

                videoOptions.add(
                    FormatOption(
                        formatId = f.formatId ?: "${height}p",
                        resolution = res,
                        width = width,
                        height = height,
                        fps = if (f.fps > 0) f.fps else 30,
                        videoCodec = f.vcodec ?: "h264",
                        audioCodec = resolvedAudioCodec,
                        container = if (ext.contains("webm")) "webm" else "mp4",
                        estimatedSizeBytes = if (f.fileSize > 0) f.fileSize else f.fileSizeApproximate,
                        videoUrl = streamUrl,
                        audioUrl = pairedAudioUrl,
                        isBestQuality = false
                    )
                )
            }
        }

        if (videoOptions.isEmpty()) {
            val directUrl = videoInfo.url ?: originalUrl
            videoOptions.add(
                FormatOption(
                    formatId = videoInfo.formatId ?: "ig_direct",
                    resolution = videoInfo.resolution ?: "1080p",
                    width = if (videoInfo.width > 0) videoInfo.width else 1080,
                    height = if (videoInfo.height > 0) videoInfo.height else 1920,
                    videoUrl = directUrl,
                    videoCodec = "h264",
                    audioCodec = "aac",
                    container = "mp4",
                    isBestQuality = true
                )
            )
        }

        // Sort: highest resolution first, prefer MP4, prefer higher size
        val sorted = videoOptions
            .sortedWith(
                compareByDescending<FormatOption> { it.height }
                    .thenByDescending { if (it.container == "mp4") 1 else 0 }
                    .thenByDescending { it.estimatedSizeBytes }
            )
            .distinctBy { "${it.height}_${it.container}" }
            .mapIndexed { idx, opt -> opt.copy(isBestQuality = idx == 0) }

        return VideoExtractionInfo(
            url = originalUrl,
            platform = Platform.INSTAGRAM,
            title = title,
            author = uploader,
            durationSeconds = duration,
            thumbnailUrl = thumbnail,
            formats = sorted,
            rawJsonMetadata = null
        )
    }

    companion object {
        private const val TAG = "InstagramMediaExtractor"

        fun classifyError(rawMsg: String?, url: String): Pair<InstagramErrorType, String> {
            if (rawMsg.isNullOrBlank()) {
                return Pair(InstagramErrorType.EXTRACTION_FAILED, "Extraction failed for Instagram media: $url")
            }

            return when {
                rawMsg.contains("This account is private", ignoreCase = true) ||
                        rawMsg.contains("private post", ignoreCase = true) ->
                    Pair(InstagramErrorType.PRIVATE_CONTENT, "This Instagram account or post is private. Cannot access private media without authorization.")

                rawMsg.contains("login required", ignoreCase = true) ||
                        rawMsg.contains("login page", ignoreCase = true) ||
                        rawMsg.contains("Sign in to confirm", ignoreCase = true) ||
                        rawMsg.contains("empty media response", ignoreCase = true) ->
                    Pair(InstagramErrorType.AUTHENTICATION_REQUIRED, "Instagram requires authentication or session login to view this restricted media.")

                rawMsg.contains("rate-limit", ignoreCase = true) ||
                        rawMsg.contains("rate limit", ignoreCase = true) ||
                        rawMsg.contains("too many requests", ignoreCase = true) ||
                        rawMsg.contains("HTTP Error 429", ignoreCase = true) ->
                    Pair(InstagramErrorType.RATE_LIMITED, "Instagram temporary rate limit reached. Please wait a moment and try again.")

                rawMsg.contains("Unsupported URL", ignoreCase = true) ||
                        rawMsg.contains("not a valid URL", ignoreCase = true) ->
                    Pair(InstagramErrorType.UNSUPPORTED_URL, "Unsupported or malformed Instagram URL: $url")

                rawMsg.contains("Video unavailable", ignoreCase = true) ||
                        rawMsg.contains("content is not available", ignoreCase = true) ||
                        rawMsg.contains("404", ignoreCase = true) ->
                    Pair(InstagramErrorType.UNAVAILABLE_OR_DELETED, "This Instagram video is unavailable, deleted, or expired.")

                rawMsg.contains("Unable to connect", ignoreCase = true) ||
                        rawMsg.contains("Connection refused", ignoreCase = true) ||
                        rawMsg.contains("timed out", ignoreCase = true) ||
                        rawMsg.contains("UnknownHostException", ignoreCase = true) ->
                    Pair(InstagramErrorType.NETWORK_ERROR, "Network connection error while contacting Instagram. Check your internet connection.")

                else -> {
                    val errorLine = rawMsg.lines().firstOrNull { it.contains("ERROR:", ignoreCase = true) }
                    val message = if (errorLine != null) {
                        errorLine.substringAfter("ERROR:").trim()
                    } else {
                        rawMsg.take(200).trim()
                    }
                    Pair(InstagramErrorType.EXTRACTION_FAILED, message)
                }
            }
        }
    }
}
