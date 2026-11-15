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

class TwitterExtractor : VideoExtractor {

    override val platform: Platform = Platform.X_TWITTER

    private val statusIdPattern = Pattern.compile("status/([0-9]{15,22})", Pattern.CASE_INSENSITIVE)

    override fun canHandle(url: String): Boolean {
        return url.contains("twitter.com") || url.contains("x.com")
    }

    override suspend fun extract(url: String): Resource<VideoExtractionInfo> = withContext(Dispatchers.IO) {
        try {
            val matcher = statusIdPattern.matcher(url)
            val tweetId = if (matcher.find()) matcher.group(1) else null
                ?: return@withContext Resource.Error("Could not extract Tweet ID from URL: $url")

            val syndicationUrl = "https://cdn.syndication.twimg.com/tweet-result?id=$tweetId&lang=en"
            val req = NetworkClient.newRequest(syndicationUrl)

            var title = "X Video ($tweetId)"
            var author = "X User"
            var thumbnail: String? = null
            val formats = mutableListOf<FormatOption>()

            NetworkClient.execute(req).use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    if (body.startsWith("{")) {
                        val json = JSONObject(body)
                        val text = json.optString("text")
                        if (text.isNotBlank()) {
                            title = text.take(60)
                        }

                        val user = json.optJSONObject("user")
                        if (user != null) {
                            author = "@" + user.optString("screen_name", "user")
                        }

                        // Parse mediaDetails / video
                        val mediaDetails = json.optJSONArray("mediaDetails")
                        if (mediaDetails != null && mediaDetails.length() > 0) {
                            for (i in 0 until mediaDetails.length()) {
                                val item = mediaDetails.getJSONObject(i)
                                thumbnail = item.optString("media_url_https", "")
                                if (thumbnail.isNullOrBlank()) thumbnail = null
                                val videoInfo = item.optJSONObject("video_info")
                                if (videoInfo != null) {
                                    val variants = videoInfo.optJSONArray("variants")
                                    if (variants != null) {
                                        for (j in 0 until variants.length()) {
                                            val v = variants.getJSONObject(j)
                                            val contentType = v.optString("content_type")
                                            val streamUrl = v.optString("url")
                                            val bitrate = v.optLong("bitrate", 0L)

                                            if (contentType == "video/mp4" && streamUrl.isNotBlank()) {
                                                val resLabel = if (bitrate > 1_500_000) "1080p" else if (bitrate > 800_000) "720p" else "480p"
                                                formats.add(
                                                    FormatOption(
                                                        formatId = "x_${bitrate}",
                                                        resolution = resLabel,
                                                        width = if (resLabel == "1080p") 1920 else 1280,
                                                        height = if (resLabel == "1080p") 1080 else 720,
                                                        fps = 30,
                                                        videoCodec = "h264",
                                                        audioCodec = "aac",
                                                        container = "mp4",
                                                        videoUrl = streamUrl,
                                                        isBestQuality = false
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Sort formats by resolution / bitrate descending
            val sortedFormats = formats.sortedByDescending { it.estimatedSizeBytes }
                .mapIndexed { idx, item -> item.copy(isBestQuality = idx == 0) }

            val finalFormats = if (sortedFormats.isNotEmpty()) sortedFormats else listOf(
                FormatOption(
                    formatId = "x_default",
                    resolution = "Original",
                    videoUrl = url,
                    isBestQuality = true
                )
            )

            Resource.Success(
                VideoExtractionInfo(
                    url = url,
                    platform = Platform.X_TWITTER,
                    title = title,
                    author = author,
                    durationSeconds = 0L,
                    thumbnailUrl = thumbnail,
                    formats = finalFormats
                )
            )
        } catch (e: Exception) {
            Resource.Error("Failed to extract X / Twitter video: ${e.message}", e)
        }
    }
}
