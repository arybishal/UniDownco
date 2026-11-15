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

    override fun canHandle(url: String): Boolean {
        return url.contains("reddit.com") || url.contains("v.redd.it") || url.contains("redd.it")
    }

    override suspend fun extract(url: String): Resource<VideoExtractionInfo> = withContext(Dispatchers.IO) {
        try {
            // Normalize Reddit post JSON URL
            val cleanUrl = url.split("?")[0].trimEnd('/')
            val jsonUrl = if (cleanUrl.endsWith(".json")) cleanUrl else "$cleanUrl.json"

            val req = NetworkClient.newRequest(
                url = jsonUrl,
                headers = mapOf("User-Agent" to "UniDownco/1.0 (Android 15; OnePlus 13R)")
            )

            var title = "Reddit Video"
            var author = "Reddit User"
            var thumbnail: String? = null
            var duration = 0L
            val formats = mutableListOf<FormatOption>()

            NetworkClient.execute(req).use { response ->
                if (response.isSuccessful) {
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
                                    val isDash = redditVideo.optBoolean("is_gif", false)

                                    if (fallbackUrl.isNotBlank()) {
                                        // Standard Reddit audio stream is DASH_audio.mp4 or DASH_AUDIO_128.mp4
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
                                                audioCodec = if (isDash) null else "aac",
                                                container = "mp4",
                                                videoUrl = fallbackUrl,
                                                audioUrl = if (isDash) null else audioUrl,
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

            if (formats.isEmpty()) {
                formats.add(
                    FormatOption(
                        formatId = "reddit_default",
                        resolution = "Original",
                        width = 1920,
                        height = 1080,
                        fps = 30,
                        videoCodec = "h264",
                        audioCodec = "aac",
                        container = "mp4",
                        videoUrl = url,
                        isBestQuality = true
                    )
                )
            }

            Resource.Success(
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
        } catch (e: Exception) {
            Resource.Error("Failed to extract Reddit video: ${e.message}", e)
        }
    }
}
