package com.unidownco.data.extractor

import com.unidownco.core.common.Platform
import com.unidownco.core.common.Resource
import com.unidownco.core.network.NetworkClient
import com.unidownco.domain.models.FormatOption
import com.unidownco.domain.models.VideoExtractionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLDecoder
import java.util.regex.Pattern

class TikTokExtractor : VideoExtractor {

    override val platform: Platform = Platform.TIKTOK

    private val videoIdPattern = Pattern.compile("(?:video/|v/|embed/|/v/|/t/)([0-9]{15,22})", Pattern.CASE_INSENSITIVE)

    override fun canHandle(url: String): Boolean {
        return url.contains("tiktok.com")
    }

    override suspend fun extract(url: String): Resource<VideoExtractionInfo> = withContext(Dispatchers.IO) {
        try {
            // 1. Fetch metadata via oembed
            val oembedUrl = "https://www.tiktok.com/oembed?url=$url"
            val oembedReq = NetworkClient.newRequest(oembedUrl)
            var title = "TikTok Video"
            var author = "TikTok Creator"
            var thumbnail: String? = null

            try {
                NetworkClient.execute(oembedReq).use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrEmpty()) {
                            val json = JSONObject(body)
                            title = json.optString("title", title).ifBlank { "TikTok Video" }
                            author = json.optString("author_name", author)
                            thumbnail = json.optString("thumbnail_url", "").ifBlank { null }
                        }
                    }
                }
            } catch (_: Exception) {
                // Fallback
            }

            // 2. Fetch HTML page to locate direct high-res MP4 video URL
            val pageReq = NetworkClient.newRequest(url)
            var videoStreamUrl: String? = null
            var width = 1080
            var height = 1920

            NetworkClient.execute(pageReq).use { response ->
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: ""
                    // Try to match playAddr or downloadAddr in JSON script tags
                    val playAddrPattern = Pattern.compile("\"playAddr\":\"(https?:\\\\/\\\\/[^\"]+)\"")
                    val playMatcher = playAddrPattern.matcher(html)
                    if (playMatcher.find()) {
                        val rawUrl = playMatcher.group(1)
                        if (rawUrl != null) {
                            videoStreamUrl = rawUrl.replace("\\u0026", "&").replace("\\/", "/")
                        }
                    }

                    // Fallback to meta tag video
                    if (videoStreamUrl == null) {
                        val ogVideoPattern = Pattern.compile("<meta property=\"og:video\" content=\"([^\"]+)\"")
                        val ogMatcher = ogVideoPattern.matcher(html)
                        if (ogMatcher.find()) {
                            videoStreamUrl = ogMatcher.group(1)?.replace("&amp;", "&")
                        }
                    }
                }
            }

            val formats = mutableListOf<FormatOption>()
            if (!videoStreamUrl.isNullOrBlank()) {
                formats.add(
                    FormatOption(
                        formatId = "tiktok_hd",
                        resolution = "1080p",
                        width = width,
                        height = height,
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
                return@withContext Resource.Error("Could not extract direct stream URL from TikTok. Platform requires anti-bot signature token or yt-dlp bridge.")
            }

            Resource.Success(
                VideoExtractionInfo(
                    url = url,
                    platform = Platform.TIKTOK,
                    title = title,
                    author = author,
                    durationSeconds = 0L,
                    thumbnailUrl = thumbnail,
                    formats = formats
                )
            )
        } catch (e: Exception) {
            Resource.Error("Failed to extract TikTok video: ${e.message}", e)
        }
    }
}
