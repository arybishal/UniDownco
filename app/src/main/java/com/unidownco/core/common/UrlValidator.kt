package com.unidownco.core.common

import java.net.URI
import java.util.regex.Pattern

data class ValidationResult(
    val isValid: Boolean,
    val sanitizedUrl: String,
    val platform: Platform,
    val errorMessage: String? = null
)

object UrlValidator {

    private val URL_PATTERN = Pattern.compile(
        "^https?://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$",
        Pattern.CASE_INSENSITIVE
    )

    private val YOUTUBE_HOSTS = listOf("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be", "music.youtube.com")
    private val TIKTOK_HOSTS = listOf("tiktok.com", "www.tiktok.com", "m.tiktok.com", "vt.tiktok.com", "vm.tiktok.com")
    private val INSTAGRAM_HOSTS = listOf("instagram.com", "www.instagram.com", "instagr.am")
    private val REDDIT_HOSTS = listOf("reddit.com", "www.reddit.com", "old.reddit.com", "v.redd.it", "redd.it")
    private val TWITTER_HOSTS = listOf("twitter.com", "www.twitter.com", "x.com", "www.x.com", "mobile.twitter.com")
    private val FACEBOOK_HOSTS = listOf("facebook.com", "www.facebook.com", "m.facebook.com", "fb.watch", "web.facebook.com")

    private val DIRECT_VIDEO_EXTENSIONS = listOf(".mp4", ".webm", ".mkv", ".mov", ".m4v", ".m3u8")

    /**
     * Extracts and validates the first URL found in raw shared or pasted text.
     */
    fun validateAndExtract(rawInput: String?): ValidationResult {
        if (rawInput.isNullOrBlank()) {
            return ValidationResult(
                isValid = false,
                sanitizedUrl = "",
                platform = Platform.UNSUPPORTED,
                errorMessage = "URL cannot be empty."
            )
        }

        // Extract URL if text contains surrounding text (e.g. from Android Share Intent)
        val extractedUrl = extractUrlFromText(rawInput.trim())
        if (extractedUrl.isNullOrBlank()) {
            return ValidationResult(
                isValid = false,
                sanitizedUrl = "",
                platform = Platform.UNSUPPORTED,
                errorMessage = "No valid web URL found in input."
            )
        }

        // Sanitize URL (trim, normalize scheme)
        val normalizedUrl = if (!extractedUrl.startsWith("http://", ignoreCase = true) &&
            !extractedUrl.startsWith("https://", ignoreCase = true)
        ) {
            "https://$extractedUrl"
        } else {
            extractedUrl
        }

        val uri = try {
            URI(normalizedUrl)
        } catch (e: Exception) {
            return ValidationResult(
                isValid = false,
                sanitizedUrl = normalizedUrl,
                platform = Platform.UNSUPPORTED,
                errorMessage = "Malformed URL structure: ${e.message}"
            )
        }

        val host = uri.host?.lowercase() ?: ""
        if (host.isEmpty()) {
            return ValidationResult(
                isValid = false,
                sanitizedUrl = normalizedUrl,
                platform = Platform.UNSUPPORTED,
                errorMessage = "Invalid URL domain host."
            )
        }

        val path = uri.path?.lowercase() ?: ""
        val platform = detectPlatform(host, path)

        if (platform == Platform.UNSUPPORTED) {
            return ValidationResult(
                isValid = false,
                sanitizedUrl = normalizedUrl,
                platform = Platform.UNSUPPORTED,
                errorMessage = "Unsupported video platform or non-video web page."
            )
        }

        return ValidationResult(
            isValid = true,
            sanitizedUrl = normalizedUrl,
            platform = platform,
            errorMessage = null
        )
    }

    fun detectPlatform(host: String, path: String): Platform {
        return when {
            YOUTUBE_HOSTS.any { host == it || host.endsWith(".$it") } -> Platform.YOUTUBE
            TIKTOK_HOSTS.any { host == it || host.endsWith(".$it") } -> Platform.TIKTOK
            INSTAGRAM_HOSTS.any { host == it || host.endsWith(".$it") } -> Platform.INSTAGRAM
            REDDIT_HOSTS.any { host == it || host.endsWith(".$it") } -> Platform.REDDIT
            TWITTER_HOSTS.any { host == it || host.endsWith(".$it") } -> Platform.X_TWITTER
            FACEBOOK_HOSTS.any { host == it || host.endsWith(".$it") } -> Platform.FACEBOOK
            DIRECT_VIDEO_EXTENSIONS.any { path.endsWith(it) } -> Platform.GENERIC
            // Check if generic domain has video path or direct media link
            else -> Platform.GENERIC
        }
    }

    private fun extractUrlFromText(text: String): String? {
        val words = text.split("\\s+".toRegex())
        for (word in words) {
            val candidate = word.trim().removeSurrounding("<", ">").removeSurrounding("\"", "\"")
            if (candidate.startsWith("http://", ignoreCase = true) ||
                candidate.startsWith("https://", ignoreCase = true)
            ) {
                return candidate
            }
            if (candidate.contains(".") && (candidate.startsWith("youtu.be") || candidate.startsWith("v.redd.it") || candidate.startsWith("fb.watch"))) {
                return "https://$candidate"
            }
        }
        return null
    }
}
