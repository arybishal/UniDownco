package com.unidownco.data.extractor

import com.unidownco.core.common.Platform
import com.unidownco.core.common.Resource
import com.unidownco.core.network.NetworkClient
import com.unidownco.domain.models.FormatOption
import com.unidownco.domain.models.VideoExtractionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.util.regex.Pattern

class FacebookExtractor : VideoExtractor {

    override val platform: Platform = Platform.FACEBOOK

    override fun canHandle(url: String): Boolean {
        return url.contains("facebook.com") || url.contains("fb.watch")
    }

    override suspend fun extract(url: String): Resource<VideoExtractionInfo> = withContext(Dispatchers.IO) {
        try {
            val req = NetworkClient.newRequest(
                url = url,
                headers = mapOf("User-Agent" to NetworkClient.USER_AGENT_DESKTOP)
            )

            var title = "Facebook Video"
            var author = "Facebook Creator"
            var thumbnail: String? = null
            var hdUrl: String? = null
            var sdUrl: String? = null

            NetworkClient.execute(req).use { response ->
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: ""

                    // Extract HD video url
                    val hdPattern = Pattern.compile("(?:hd_src|browser_native_hd_url):\"([^\"]+)\"")
                    val hdMatcher = hdPattern.matcher(html)
                    if (hdMatcher.find()) {
                        hdUrl = hdMatcher.group(1)?.replace("\\/", "/")
                    }

                    // Extract SD video url
                    val sdPattern = Pattern.compile("(?:sd_src|browser_native_sd_url):\"([^\"]+)\"")
                    val sdMatcher = sdPattern.matcher(html)
                    if (sdMatcher.find()) {
                        sdUrl = sdMatcher.group(1)?.replace("\\/", "/")
                    }

                    // Extract OpenGraph tags
                    val ogTitlePattern = Pattern.compile("<meta property=\"og:title\" content=\"([^\"]+)\"")
                    val ogTitleMatcher = ogTitlePattern.matcher(html)
                    if (ogTitleMatcher.find()) {
                        title = ogTitleMatcher.group(1)?.replace("&amp;", "&") ?: title
                    }

                    val ogImagePattern = Pattern.compile("<meta property=\"og:image\" content=\"([^\"]+)\"")
                    val ogImageMatcher = ogImagePattern.matcher(html)
                    if (ogImageMatcher.find()) {
                        thumbnail = ogImageMatcher.group(1)?.replace("&amp;", "&")
                    }
                }
            }

            val formats = mutableListOf<FormatOption>()
            if (!hdUrl.isNullOrBlank()) {
                formats.add(
                    FormatOption(
                        formatId = "fb_hd",
                        resolution = "1080p HD",
                        width = 1920,
                        height = 1080,
                        fps = 30,
                        videoCodec = "h264",
                        audioCodec = "aac",
                        container = "mp4",
                        videoUrl = hdUrl!!,
                        isBestQuality = true
                    )
                )
            }
            if (!sdUrl.isNullOrBlank()) {
                formats.add(
                    FormatOption(
                        formatId = "fb_sd",
                        resolution = "720p SD",
                        width = 1280,
                        height = 720,
                        fps = 30,
                        videoCodec = "h264",
                        audioCodec = "aac",
                        container = "mp4",
                        videoUrl = sdUrl!!,
                        isBestQuality = hdUrl.isNullOrBlank()
                    )
                )
            }

            if (formats.isEmpty()) {
                return@withContext Resource.Error("Could not extract direct stream URL from Facebook. Video may be private or platform changed layout.")
            }

            Resource.Success(
                VideoExtractionInfo(
                    url = url,
                    platform = Platform.FACEBOOK,
                    title = title,
                    author = author,
                    durationSeconds = 0L,
                    thumbnailUrl = thumbnail,
                    formats = formats
                )
            )
        } catch (e: Exception) {
            Resource.Error("Failed to extract Facebook video: ${e.message}", e)
        }
    }
}
