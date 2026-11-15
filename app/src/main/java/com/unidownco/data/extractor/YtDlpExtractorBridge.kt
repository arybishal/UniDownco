package com.unidownco.data.extractor

import com.unidownco.core.common.Platform
import com.unidownco.domain.models.FormatOption
import com.unidownco.domain.models.VideoExtractionInfo
import org.json.JSONObject

/**
 * Architectural Bridge for yt-dlp format schemas and compatibility.
 * Parses yt-dlp dump-json outputs and maps formats to UniDownco domain models.
 */
object YtDlpExtractorBridge {

    fun parseYtDlpJson(jsonString: String, originalUrl: String): VideoExtractionInfo? {
        return try {
            val json = JSONObject(jsonString)
            val title = json.optString("title", "Video")
            val uploader = json.optString("uploader", json.optString("channel", ""))
            val duration = json.optLong("duration", 0L)
            val thumbnail = json.optString("thumbnail", "").ifBlank { null }
            val extractorKey = json.optString("extractor_key", "")

            val platform = when (extractorKey.lowercase()) {
                "youtube" -> Platform.YOUTUBE
                "tiktok" -> Platform.TIKTOK
                "instagram" -> Platform.INSTAGRAM
                "reddit" -> Platform.REDDIT
                "twitter" -> Platform.X_TWITTER
                "facebook" -> Platform.FACEBOOK
                else -> Platform.GENERIC
            }

            val formatsArray = json.optJSONArray("formats")
            val formats = mutableListOf<FormatOption>()

            if (formatsArray != null) {
                for (i in 0 until formatsArray.length()) {
                    val f = formatsArray.getJSONObject(i)
                    val formatId = f.optString("format_id", i.toString())
                    val url = f.optString("url")
                    if (url.isNotBlank()) {
                        val height = f.optInt("height", 0)
                        val width = f.optInt("width", 0)
                        val res = if (height > 0) "${height}p" else f.optString("format_note", "Standard")
                        val vcodec = f.optString("vcodec", "none")
                        val acodec = f.optString("acodec", "none")
                        val ext = f.optString("ext", "mp4")
                        val fps = f.optInt("fps", 30)
                        val filesize = f.optLong("filesize", f.optLong("filesize_approx", 0L))

                        // Only include valid video formats
                        if (vcodec != "none") {
                            formats.add(
                                FormatOption(
                                    formatId = formatId,
                                    resolution = res,
                                    width = if (width > 0) width else 1920,
                                    height = if (height > 0) height else 1080,
                                    fps = if (fps > 0) fps else 30,
                                    videoCodec = vcodec,
                                    audioCodec = if (acodec != "none") acodec else null,
                                    container = ext,
                                    estimatedSizeBytes = filesize,
                                    videoUrl = url,
                                    isBestQuality = false
                                )
                            )
                        }
                    }
                }
            }

            // Mark best format
            val sortedFormats = formats.sortedByDescending { it.height }
                .mapIndexed { index, option -> option.copy(isBestQuality = index == 0) }

            VideoExtractionInfo(
                url = originalUrl,
                platform = platform,
                title = title,
                author = uploader,
                durationSeconds = duration,
                thumbnailUrl = thumbnail,
                formats = sortedFormats,
                rawJsonMetadata = jsonString
            )
        } catch (e: Exception) {
            null
        }
    }
}
