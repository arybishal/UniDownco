package com.unidownco.data.extractor

import com.unidownco.core.common.Platform
import com.unidownco.core.common.Resource
import com.unidownco.core.network.NetworkClient
import com.unidownco.domain.models.FormatOption
import com.unidownco.domain.models.VideoExtractionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.regex.Pattern

class RedditExtractor : VideoExtractor {

    override val platform: Platform = Platform.REDDIT

    private val vredditPattern = Pattern.compile("v\\.redd\\.it/([a-zA-Z0-9_-]+)", Pattern.CASE_INSENSITIVE)

    override fun canHandle(url: String): Boolean {
        return url.contains("reddit.com") || url.contains("v.redd.it") || url.contains("redd.it")
    }

    override suspend fun extract(url: String): Resource<VideoExtractionInfo> = withContext(Dispatchers.IO) {
        try {
            // 1. Check for direct v.redd.it URL
            val vredditMatcher = vredditPattern.matcher(url)
            if (vredditMatcher.find()) {
                val mediaId = vredditMatcher.group(1)
                if (!mediaId.isNullOrBlank()) {
                    return@withContext extractFromVRedditId(mediaId, url)
                }
            }

            // 2. Normalize Reddit post JSON URL
            val cleanUrl = url.split("?")[0].trimEnd('/')
            val jsonUrl = if (cleanUrl.endsWith(".json")) cleanUrl else "$cleanUrl.json"

            val req = NetworkClient.newRequest(
                url = jsonUrl,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
                    "Accept" to "application/json, text/plain, */*"
                )
            )

            var title = "Reddit Video"
            var author = "Reddit User"
            var thumbnail: String? = null
            var duration = 0L
            val formats = mutableListOf<FormatOption>()
            var isBlocked = false

            NetworkClient.execute(req).use { response ->
                if (response.code == 403) {
                    isBlocked = true
                } else if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    if (body.startsWith("[")) {
                        val rootArray = JSONArray(body)
                        if (rootArray.length() > 0) {
                            val postListing = rootArray.getJSONObject(0)
                            val data = postListing.getJSONObject("data")
                            val children = data.getJSONArray("children")
                            if (children.length() > 0) {
                                val postData = children.getJSONObject(0).getJSONObject("data")
                                title = postData.optString("title", title)
                                author = "u/" + postData.optString("author", "redditor")
                                thumbnail = postData.optString("thumbnail", "").ifBlank { null }

                                val secureMedia = postData.optJSONObject("secure_media")
                                    ?: postData.optJSONObject("media")
                                val redditVideo = secureMedia?.optJSONObject("reddit_video")

                                if (redditVideo != null) {
                                    val fallbackUrl = redditVideo.optString("fallback_url")
                                    val width = redditVideo.optInt("width", 1920)
                                    val height = redditVideo.optInt("height", 1080)
                                    duration = redditVideo.optLong("duration", 0L)
                                    val isGif = redditVideo.optBoolean("is_gif", false)

                                    if (fallbackUrl.isNotBlank()) {
                                        val baseDashUrl = fallbackUrl.substringBeforeLast('/')
                                        val audioUrl = "$baseDashUrl/DASH_AUDIO_128.mp4"

                                        formats.add(
                                            FormatOption(
                                                formatId = "reddit_${height}p",
                                                resolution = "${height}p",
                                                width = width,
                                                height = height,
                                                fps = 30,
                                                videoCodec = "h264",
                                                audioCodec = if (isGif) null else "aac",
                                                container = "mp4",
                                                videoUrl = fallbackUrl,
                                                audioUrl = if (isGif) null else audioUrl,
                                                isBestQuality = true
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (formats.isNotEmpty()) {
                return@withContext Resource.Success(
                    VideoExtractionInfo(
                        url = url,
                        platform = Platform.REDDIT,
                        title = title,
                        author = author,
                        durationSeconds = duration,
                        thumbnailUrl = thumbnail,
                        formats = formats
                    )
                )
            }

            if (isBlocked) {
                return@withContext Resource.Error(
                    "Reddit unauthenticated API access blocked (HTTP 403 / JS bot challenge). " +
                    "Try sharing the direct v.redd.it stream link or use yt-dlp companion."
                )
            }

            Resource.Error(
                "Could not extract video streams from Reddit post. Post may not contain a native video, or access is restricted."
            )
        } catch (e: Exception) {
            Resource.Error("Failed to extract Reddit video: ${e.message}", e)
        }
    }

    private fun extractFromVRedditId(mediaId: String, originalUrl: String): Resource<VideoExtractionInfo> {
        val videoUrl = "https://v.redd.it/$mediaId/DASH_720.mp4"
        val audioUrl = "https://v.redd.it/$mediaId/DASH_AUDIO_128.mp4"

        val format = FormatOption(
            formatId = "vreddit_720p",
            resolution = "720p",
            width = 1280,
            height = 720,
            fps = 30,
            videoCodec = "h264",
            audioCodec = "aac",
            container = "mp4",
            videoUrl = videoUrl,
            audioUrl = audioUrl,
            isBestQuality = true
        )

        return Resource.Success(
            VideoExtractionInfo(
                url = originalUrl,
                platform = Platform.REDDIT,
                title = "Reddit Video ($mediaId)",
                author = "Reddit User",
                formats = listOf(format)
            )
        )
    }
}
