package com.unidownco.engine.instagram

import java.util.regex.Pattern

/**
 * Normalizes Instagram URLs and extracts canonical shortcodes.
 * Handles Reels, Posts, IGTV, Share URLs (/share/reel/), trailing slashes,
 * and stripped tracking query parameters (?igsh=..., ?utm_source=...).
 */
object InstagramUrlResolver {

    private val INSTAGRAM_SHORTCODE_PATTERN = Pattern.compile(
        """(?:/(?:p|reel|reels|tv|share/reel)/)([A-Za-z0-9_-]+)""",
        Pattern.CASE_INSENSITIVE
    )

    private val BARE_SHORTCODE_PATTERN = Pattern.compile("""^[A-Za-z0-9_-]{5,35}$""")

    /**
     * Determines whether the given URL belongs to Instagram.
     */
    fun isInstagramUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase().trim()
        return lower.contains("instagram.com") || lower.contains("instagr.am")
    }

    /**
     * Extracts the post shortcode from an Instagram URL or raw shortcode.
     */
    fun extractShortcode(urlOrShortcode: String?): String? {
        if (urlOrShortcode.isNullOrBlank()) return null
        val trimmed = urlOrShortcode.trim()

        val matcher = INSTAGRAM_SHORTCODE_PATTERN.matcher(trimmed)
        if (matcher.find()) {
            return matcher.group(1)
        }

        if (BARE_SHORTCODE_PATTERN.matcher(trimmed).matches() && !trimmed.startsWith("http", ignoreCase = true)) {
            return trimmed
        }

        return null
    }

    /**
     * Normalizes any Instagram URL variant into a clean, canonical URL
     * recognizable by yt-dlp's InstagramIE regex.
     * Strips tracking parameters, query strings, and redirects /share/reel/ to /reel/.
     */
    fun normalizeUrl(url: String): String {
        val shortcode = extractShortcode(url) ?: return url.trim()
        val lower = url.lowercase()
        return when {
            lower.contains("/reel") || lower.contains("/share/reel") -> "https://www.instagram.com/reel/$shortcode/"
            lower.contains("/tv") -> "https://www.instagram.com/tv/$shortcode/"
            else -> "https://www.instagram.com/p/$shortcode/"
        }
    }

    /**
     * Validates whether the given string is a supported Instagram media link.
     */
    fun validateInstagramUrl(url: String?): Result<String> {
        if (url.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("URL cannot be empty"))
        }
        if (!isInstagramUrl(url)) {
            return Result.failure(IllegalArgumentException("Not an Instagram URL: $url"))
        }
        val shortcode = extractShortcode(url)
            ?: return Result.failure(IllegalArgumentException("Could not extract a valid Instagram post or reel ID from: $url"))

        return Result.success(normalizeUrl(url))
    }
}
