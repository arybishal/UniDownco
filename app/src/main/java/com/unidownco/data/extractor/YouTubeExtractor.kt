package com.unidownco.data.extractor

import com.unidownco.core.common.Platform
import com.unidownco.core.common.Resource
import com.unidownco.core.network.NetworkClient
import com.unidownco.domain.models.FormatOption
import com.unidownco.domain.models.VideoExtractionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.regex.Pattern

class YouTubeExtractor : VideoExtractor {

    override val platform: Platform = Platform.YOUTUBE

    private val videoIdPattern = Pattern.compile(
        "(?:youtu\\.be/|youtube\\.com/(?:embed/|v/|watch\\?v=|watch\\?.+&v=|shorts/))([\\w-]{11})",
        Pattern.CASE_INSENSITIVE
    )

    override fun canHandle(url: String): Boolean {
        return url.contains("youtube.com") || url.contains("youtu.be")
    }

    override suspend fun extract(url: String): Resource<VideoExtractionInfo> = withContext(Dispatchers.IO) {
        try {
            val matcher = videoIdPattern.matcher(url)
            val videoId = if (matcher.find()) matcher.group(1) else null
                ?: return@withContext Resource.Error("Could not extract YouTube video ID from URL: $url")

            // 1. Fetch metadata via oembed for high-fidelity title, author, thumbnail
            val oembedUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json"
            val oembedReq = NetworkClient.newRequest(oembedUrl)
            var title = "YouTube Video ($videoId)"
            var author = "YouTube Creator"
            var thumbnail = "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"

            try {
                NetworkClient.execute(oembedReq).use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrEmpty()) {
                            val json = JSONObject(body)
                            title = json.optString("title", title)
                            author = json.optString("author_name", author)
                            thumbnail = json.optString("thumbnail_url", thumbnail)
                        }
                    }
                }
            } catch (_: Exception) {
                // Fallback to default metadata
            }

            // 2. Fetch streaming formats via Innertube API
            val innertubeUrl = "https://www.youtube.com/youtubei/v1/player"
            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "ANDROID")
                        put("clientVersion", "19.29.37")
                        put("androidSdkVersion", 34)
                        put("osName", "Android")
                        put("osVersion", "14")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
                put("videoId", videoId)
            }

            val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
            val innertubeReq = okhttp3.Request.Builder()
                .url(innertubeUrl)
                .post(requestBody)
                .header("User-Agent", "com.google.android.youtube/19.29.37 (Linux; U; Android 14)")
                .header("Content-Type", "application/json")
                .build()

            val formats = mutableListOf<FormatOption>()
            var duration = 0L
            var playabilityReason: String? = null
            var hasCipherProtectedFormats = false

            try {
                NetworkClient.execute(innertubeReq).use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrEmpty()) {
                            val json = JSONObject(body)

                            val playabilityStatus = json.optJSONObject("playabilityStatus")
                            if (playabilityStatus != null) {
                                val status = playabilityStatus.optString("status")
                                if (status != "OK") {
                                    playabilityReason = playabilityStatus.optString("reason", status)
                                }
                            }

                            val videoDetails = json.optJSONObject("videoDetails")
                            if (videoDetails != null) {
                                title = videoDetails.optString("title", title)
                                author = videoDetails.optString("author", author)
                                duration = videoDetails.optLong("lengthSeconds", 0L)
                            }

                            val streamingData = json.optJSONObject("streamingData")
                            if (streamingData != null) {
                                // Direct combined formats
                                val combinedFormats = streamingData.optJSONArray("formats")
                                if (combinedFormats != null) {
                                    for (i in 0 until combinedFormats.length()) {
                                        val f = combinedFormats.getJSONObject(i)
                                        if (f.has("signatureCipher") || f.has("cipher")) {
                                            hasCipherProtectedFormats = true
                                        }
                                        val streamUrl = f.optString("url")
                                        if (streamUrl.isNotBlank()) {
                                            val quality = f.optString("qualityLabel", "${f.optInt("height", 720)}p")
                                            val width = f.optInt("width", 1280)
                                            val height = f.optInt("height", 720)
                                            val fps = f.optInt("fps", 30)
                                            val mime = f.optString("mimeType", "video/mp4")
                                            val approxBytes = f.optLong("contentLength", 0L)

                                            formats.add(
                                                FormatOption(
                                                    formatId = f.optString("itag", i.toString()),
                                                    resolution = quality,
                                                    width = width,
                                                    height = height,
                                                    fps = fps,
                                                    videoCodec = if (mime.contains("av01")) "av01" else if (mime.contains("vp9")) "vp9" else "h264",
                                                    audioCodec = "aac",
                                                    container = if (mime.contains("webm")) "webm" else "mp4",
                                                    estimatedSizeBytes = approxBytes,
                                                    videoUrl = streamUrl,
                                                    isBestQuality = i == 0
                                                )
                                            )
                                        }
                                    }
                                }

                                // Adaptive video formats (1080p, 1440p, 4K)
                                val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats")
                                if (adaptiveFormats != null) {
                                    var bestAudioUrl: String? = null
                                    var bestAudioBitrate = 0L

                                    for (i in 0 until adaptiveFormats.length()) {
                                        val f = adaptiveFormats.getJSONObject(i)
                                        if (f.has("signatureCipher") || f.has("cipher")) {
                                            hasCipherProtectedFormats = true
                                        }
                                        val mime = f.optString("mimeType", "")
                                        val streamUrl = f.optString("url")
                                        if (mime.startsWith("audio/") && streamUrl.isNotBlank()) {
                                            val bitrate = f.optLong("bitrate", 0L)
                                            if (bitrate > bestAudioBitrate || bestAudioUrl == null) {
                                                bestAudioBitrate = bitrate
                                                bestAudioUrl = streamUrl
                                            }
                                        }
                                    }

                                    for (i in 0 until adaptiveFormats.length()) {
                                        val f = adaptiveFormats.getJSONObject(i)
                                        val mime = f.optString("mimeType", "")
                                        val streamUrl = f.optString("url")
                                        if (mime.startsWith("video/") && streamUrl.isNotBlank()) {
                                            val quality = f.optString("qualityLabel", "${f.optInt("height", 1080)}p")
                                            val width = f.optInt("width", 1920)
                                            val height = f.optInt("height", 1080)
                                            formats.add(
                                                FormatOption(
                                                    formatId = f.optString("itag", "adaptive_$i"),
                                                    resolution = quality,
                                                    width = width,
                                                    height = height,
                                                    fps = f.optInt("fps", 30),
                                                    videoCodec = if (mime.contains("av01")) "av01" else if (mime.contains("vp9")) "vp9" else "h264",
                                                    audioCodec = if (bestAudioUrl != null) "aac" else null,
                                                    container = "mp4",
                                                    estimatedSizeBytes = f.optLong("contentLength", 0L),
                                                    videoUrl = streamUrl,
                                                    audioUrl = bestAudioUrl,
                                                    isBestQuality = formats.isEmpty()
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else if (response.code == 400) {
                        playabilityReason = "YouTube bot-protection active (HTTP 400 Client Attestation required)."
                    }
                }
            } catch (netEx: Exception) {
                // Continue to diagnostic handling
            }

            if (formats.isNotEmpty()) {
                return@withContext Resource.Success(
                    VideoExtractionInfo(
                        url = url,
                        platform = Platform.YOUTUBE,
                        title = title,
                        author = author,
                        durationSeconds = duration,
                        thumbnailUrl = thumbnail,
                        formats = formats
                    )
                )
            }

            val errorMessage = when {
                playabilityReason != null ->
                    "YouTube playback blocked ($playabilityReason). JS decipher runtime or yt-dlp companion required."
                hasCipherProtectedFormats ->
                    "YouTube streaming formats require JavaScript signature deciphering (bot-protection active). Direct stream extraction blocked."
                else ->
                    "YouTube streaming formats blocked by platform bot-protection (Proof-of-Origin / signature cipher required). yt-dlp companion engine required."
            }

            Resource.Error(errorMessage)
        } catch (e: Exception) {
            Resource.Error("Failed to extract YouTube video: ${e.message}", e)
        }
    }
}
