package com.unidownco.data.extractor

import com.unidownco.core.common.Platform
import com.unidownco.core.common.Resource
import com.unidownco.core.network.NetworkClient
import com.unidownco.domain.models.FormatOption
import com.unidownco.domain.models.VideoExtractionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.regex.Pattern

class InstagramExtractor : VideoExtractor {

    override val platform: Platform = Platform.INSTAGRAM

    private val shortcodePattern = Pattern.compile("(?:p|reel|tv)/([A-Za-z0-9_-]+)", Pattern.CASE_INSENSITIVE)

    override fun canHandle(url: String): Boolean {
        return url.contains("instagram.com") || url.contains("instagr.am")
    }

    override suspend fun extract(url: String): Resource<VideoExtractionInfo> = withContext(Dispatchers.IO) {
        try {
            val matcher = shortcodePattern.matcher(url)
            val shortcode = if (matcher.find()) matcher.group(1) else "video"

            var title = "Instagram Reel"
            var author = "Instagram User"
            var thumbnail: String? = null
            var videoStreamUrl: String? = null

            // 1. Check Instagram embed endpoint
            val embedUrl = "https://www.instagram.com/p/$shortcode/embed/captioned/"
            val embedReq = NetworkClient.newRequest(embedUrl)

            try {
                NetworkClient.execute(embedReq).use { response ->
                    if (response.isSuccessful) {
                        val html = response.body?.string() ?: ""

                        // Extract video URL
                        val videoPattern = Pattern.compile("class=\"EmbeddedVideo\"[^>]*src=\"([^\"]+)\"")
                        val videoMatcher = videoPattern.matcher(html)
                        if (videoMatcher.find()) {
                            videoStreamUrl = videoMatcher.group(1)?.replace("&amp;", "&")
                        }

                        // Extract video poster / thumbnail
                        val posterPattern = Pattern.compile("class=\"EmbeddedVideoImage\"[^>]*src=\"([^\"]+)\"")
                        val posterMatcher = posterPattern.matcher(html)
                        if (posterMatcher.find()) {
                            thumbnail = posterMatcher.group(1)?.replace("&amp;", "&")
                        }

                        // Extract caption title
                        val captionPattern = Pattern.compile("class=\"Caption\"[^>]*>([^<]+)<")
                        val captionMatcher = captionPattern.matcher(html)
                        if (captionMatcher.find()) {
                            val cap = captionMatcher.group(1)?.trim()
                            if (!cap.isNullOrBlank()) {
                                title = cap.take(60)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore and proceed to fallback
            }

            val formats = mutableListOf<FormatOption>()
            if (!videoStreamUrl.isNullOrBlank()) {
                formats.add(
                    FormatOption(
                        formatId = "ig_reel",
                        resolution = "1080p",
                        width = 1080,
                        height = 1920,
                        fps = 30,
                        videoCodec = "h264",
                        audioCodec = "aac",
                        container = "mp4",
                        videoUrl = videoStreamUrl!!,
                        isBestQuality = true
                    )
                )
            }
            if (formats.isEmpty()) {
                return@withContext Resource.Error("Instagram video stream requires session authentication or yt-dlp bridge. Direct access restricted.")
            }

            Resource.Success(
                VideoExtractionInfo(
                    url = url,
                    platform = Platform.INSTAGRAM,
                    title = title,
                    author = author,
                    durationSeconds = 0L,
                    thumbnailUrl = thumbnail,
                    formats = formats
                )
            )
        } catch (e: Exception) {
            Resource.Error("Failed to extract Instagram video: ${e.message}", e)
        }
    }
}
