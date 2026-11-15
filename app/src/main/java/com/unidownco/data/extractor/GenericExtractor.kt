package com.unidownco.data.extractor

import com.unidownco.core.common.Platform
import com.unidownco.core.common.Resource
import com.unidownco.core.network.NetworkClient
import com.unidownco.domain.models.FormatOption
import com.unidownco.domain.models.VideoExtractionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.util.regex.Pattern

class GenericExtractor : VideoExtractor {

    override val platform: Platform = Platform.GENERIC

    private val directExtensions = listOf(".mp4", ".webm", ".mkv", ".mov", ".m4v", ".m3u8")

    override fun canHandle(url: String): Boolean = true

    override suspend fun extract(url: String): Resource<VideoExtractionInfo> = withContext(Dispatchers.IO) {
        try {
            val uri = URI(url)
            val path = uri.path?.lowercase() ?: ""
            val isDirectVideo = directExtensions.any { path.endsWith(it) }

            var title = uri.path?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Web Video"
            if (title.isBlank()) title = "Web Video"

            var thumbnail: String? = null
            var videoStreamUrl: String = if (isDirectVideo) url else ""

            if (!isDirectVideo) {
                // Fetch web page and parse HTML5 video or OpenGraph video tag
                val req = NetworkClient.newRequest(url)
                try {
                    NetworkClient.execute(req).use { response ->
                        if (response.isSuccessful) {
                            val contentType = response.header("Content-Type", "") ?: ""
                            if (contentType.startsWith("video/")) {
                                videoStreamUrl = url
                            } else {
                                val html = response.body?.string() ?: ""

                                // OpenGraph video
                                val ogVideoPattern = Pattern.compile("<meta property=\"og:video\" content=\"([^\"]+)\"")
                                val ogMatcher = ogVideoPattern.matcher(html)
                                if (ogMatcher.find()) {
                                    videoStreamUrl = ogMatcher.group(1)?.replace("&amp;", "&") ?: ""
                                }

                                // OpenGraph title
                                val ogTitlePattern = Pattern.compile("<meta property=\"og:title\" content=\"([^\"]+)\"")
                                val titleMatcher = ogTitlePattern.matcher(html)
                                if (titleMatcher.find()) {
                                    val foundTitle = titleMatcher.group(1)?.replace("&amp;", "&")
                                    if (!foundTitle.isNullOrBlank()) title = foundTitle
                                }

                                // OpenGraph image
                                val ogImagePattern = Pattern.compile("<meta property=\"og:image\" content=\"([^\"]+)\"")
                                val imageMatcher = ogImagePattern.matcher(html)
                                if (imageMatcher.find()) {
                                    thumbnail = imageMatcher.group(1)?.replace("&amp;", "&")
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}

                if (videoStreamUrl.isBlank()) {
                    return@withContext Resource.Error("URL does not contain a direct video stream or supported media format.")
                }
            }

            val formats = listOf(
                FormatOption(
                    formatId = "generic_stream",
                    resolution = "Original",
                    width = 1920,
                    height = 1080,
                    fps = 30,
                    videoCodec = "h264",
                    audioCodec = "aac",
                    container = if (path.endsWith(".webm")) "webm" else "mp4",
                    videoUrl = videoStreamUrl,
                    isBestQuality = true
                )
            )

            Resource.Success(
                VideoExtractionInfo(
                    url = url,
                    platform = Platform.GENERIC,
                    title = title,
                    author = uri.host ?: "Web",
                    durationSeconds = 0L,
                    thumbnailUrl = thumbnail,
                    formats = formats
                )
            )
        } catch (e: Exception) {
            Resource.Error("Failed to extract web video: ${e.message}", e)
        }
    }
}
