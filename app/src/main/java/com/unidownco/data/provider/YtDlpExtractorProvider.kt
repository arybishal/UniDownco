package com.unidownco.data.provider

import android.content.Context
import com.unidownco.UniDownApplication
import com.unidownco.core.common.Platform
import com.unidownco.core.common.Resource
import com.unidownco.data.extractor.YtDlpExtractorBridge
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
 * Extractor provider powered by the native in-app yt-dlp and FFmpeg runtime.
 * Handles JavaScript signature deciphering, platform visitor tokens, adaptive DASH/HLS
 * stream extraction, and format pairing for major social media platforms.
 */
class YtDlpExtractorProvider(
    private val context: Context? = null,
    private val jsonExtractor: (suspend (url: String) -> String?)? = null
) : ExtractorProvider {

    override val providerId: String = "yt-dlp"
    override val displayName: String = "yt-dlp Native Engine"
    override val priority: Int = 100
    override val isAvailable: Boolean = true

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

    override suspend fun extract(url: String): Resource<VideoExtractionInfo> = withContext(Dispatchers.IO) {
        // Fast path for mock/unit testing without native binaries
        val testExtractor = jsonExtractor
        if (testExtractor != null) {
            val json = testExtractor(url)
            if (!json.isNullOrBlank()) {
                val parsed = YtDlpExtractorBridge.parseYtDlpJson(json, url)
                if (parsed != null) {
                    return@withContext Resource.Success(parsed)
                }
            }
            return@withContext Resource.Error("yt-dlp mock extraction produced invalid JSON for: $url")
        }

        val appContext = context ?: try {
            UniDownApplication.appContext
        } catch (_: Exception) {
            null
        }

        if (appContext == null) {
            return@withContext Resource.Error("Application context not available for yt-dlp runtime")
        }

        val initialized = YtDlpEngineManager.ensureInitialized(appContext)
        if (!initialized) {
            val reason = YtDlpEngineManager.initializationError ?: "Runtime failed to initialize"
            return@withContext Resource.Error("yt-dlp engine unavailable: $reason")
        }

        try {
            android.util.Log.i("YtDlpExtractorProvider", "Starting yt-dlp extraction for: $url")
            val request = YoutubeDLRequest(url)
            request.addOption("--no-playlist")
            request.addOption("--no-update")
            request.addOption("--socket-timeout", "20")

            val videoInfo: VideoInfo = YoutubeDL.getInstance().getInfo(request)
            android.util.Log.i("YtDlpExtractorProvider", "yt-dlp extraction SUCCESS: title=${videoInfo.title}, extractor=${videoInfo.extractorKey}, formats=${videoInfo.formats?.size}")
            val domainInfo = mapVideoInfoToDomain(videoInfo, url)
            Resource.Success(domainInfo)
        } catch (ye: YoutubeDLException) {
            android.util.Log.e("YtDlpExtractorProvider", "yt-dlp YoutubeDLException: ${ye.message}")
            Resource.Error(parseErrorMessage(ye.message, url))
        } catch (e: Exception) {
            android.util.Log.e("YtDlpExtractorProvider", "yt-dlp Exception: ${e.message}", e)
            Resource.Error(parseErrorMessage(e.message, url))
        }
    }

    private fun mapVideoInfoToDomain(videoInfo: VideoInfo, originalUrl: String): VideoExtractionInfo {
        val title = videoInfo.title ?: videoInfo.fulltitle ?: "Video"
        val uploader = videoInfo.uploader ?: ""
        val duration = videoInfo.duration.toLong()
        val thumbnail = videoInfo.thumbnail?.ifBlank { null }
        val extractorKey = videoInfo.extractorKey ?: videoInfo.extractor ?: ""

        val platform = when (extractorKey.lowercase()) {
            "youtube" -> Platform.YOUTUBE
            "tiktok" -> Platform.TIKTOK
            "instagram" -> Platform.INSTAGRAM
            "reddit" -> Platform.REDDIT
            "twitter" -> Platform.X_TWITTER
            "facebook" -> Platform.FACEBOOK
            else -> Platform.fromUrl(originalUrl)
        }

        val rawFormats: List<VideoFormat> = videoInfo.formats ?: emptyList()
        val audioTracks = rawFormats.filter { f ->
            (f.vcodec == "none" || f.vcodec.isNullOrBlank()) &&
            f.acodec != "none" && !f.acodec.isNullOrBlank() &&
            !f.url.isNullOrBlank()
        }
        val bestAudio = audioTracks.maxByOrNull { it.abr }

        val videoOptions = mutableListOf<FormatOption>()
        for (f in rawFormats) {
            val hasVideo = f.vcodec != "none" && !f.vcodec.isNullOrBlank()
            val streamUrl = f.url ?: f.manifestUrl
            if (hasVideo && !streamUrl.isNullOrBlank()) {
                val isSeparateAudio = (f.acodec == "none" || f.acodec.isNullOrBlank()) && bestAudio != null
                val pairedAudioUrl = if (isSeparateAudio) bestAudio?.url else null
                val resolvedAudioCodec = if (isSeparateAudio) bestAudio?.acodec else f.acodec

                val height = f.height
                val width = f.width
                val res = if (height > 0) "${height}p" else f.formatNote ?: "Standard"
                val ext = f.ext ?: "mp4"

                videoOptions.add(
                    FormatOption(
                        formatId = f.formatId ?: "${height}p",
                        resolution = res,
                        width = if (width > 0) width else 1920,
                        height = if (height > 0) height else 1080,
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
                    formatId = videoInfo.formatId ?: "direct_0",
                    resolution = videoInfo.resolution ?: "Original",
                    width = if (videoInfo.width > 0) videoInfo.width else 1920,
                    height = if (videoInfo.height > 0) videoInfo.height else 1080,
                    videoUrl = directUrl,
                    isBestQuality = true
                )
            )
        }

        // Sort: highest resolution first, prefer MP4 container, prefer higher estimated size / bitrate
        val sorted = videoOptions
            .sortedWith(
                compareByDescending<FormatOption> { it.height }
                    .thenByDescending { if (it.container == "mp4") 1 else 0 }
                    .thenByDescending { it.estimatedSizeBytes }
            )
            // Deduplicate by resolution and container to provide a clean, distinct list of choices
            .distinctBy { "${it.height}_${it.container}_${it.fps}" }
            .mapIndexed { idx, opt -> opt.copy(isBestQuality = idx == 0) }

        return VideoExtractionInfo(
            url = originalUrl,
            platform = platform,
            title = title,
            author = uploader,
            durationSeconds = duration,
            thumbnailUrl = thumbnail,
            formats = sorted,
            rawJsonMetadata = null
        )
    }

    private fun parseErrorMessage(rawMsg: String?, url: String): String {
        if (rawMsg.isNullOrBlank()) return "Extraction error occurred for: $url"
        return when {
            rawMsg.contains("Sign in to confirm", ignoreCase = true) ->
                "Platform bot protection: Account sign-in verification required by host"
            rawMsg.contains("Video unavailable", ignoreCase = true) ->
                "Video is unavailable or private"
            rawMsg.contains("Private video", ignoreCase = true) ->
                "This video is private"
            rawMsg.contains("Unsupported URL", ignoreCase = true) ->
                "Unsupported video URL format: $url"
            else -> {
                val errorLine = rawMsg.lines().firstOrNull { it.contains("ERROR:", ignoreCase = true) }
                if (errorLine != null) {
                    errorLine.substringAfter("ERROR:").trim()
                } else {
                    rawMsg.take(200).trim()
                }
            }
        }
    }
}
