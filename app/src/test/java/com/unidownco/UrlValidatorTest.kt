package com.unidownco

import com.google.common.truth.Truth.assertThat
import com.unidownco.core.common.Platform
import com.unidownco.core.common.UrlValidator
import org.junit.Test

class UrlValidatorTest {

    @Test
    fun testYouTubeUrls_DetectedCorrectly() {
        val testUrls = listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ",
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://www.youtube.com/shorts/3f5v0a1B2cD"
        )

        testUrls.forEach { url ->
            val result = UrlValidator.validateAndExtract(url)
            assertThat(result.isValid).isTrue()
            assertThat(result.platform).isEqualTo(Platform.YOUTUBE)
        }
    }

    @Test
    fun testTikTokUrls_DetectedCorrectly() {
        val testUrls = listOf(
            "https://www.tiktok.com/@creator/video/7234567890123456789",
            "https://vt.tiktok.com/ZS123456/",
            "https://m.tiktok.com/v/7234567890123456789.html"
        )

        testUrls.forEach { url ->
            val result = UrlValidator.validateAndExtract(url)
            assertThat(result.isValid).isTrue()
            assertThat(result.platform).isEqualTo(Platform.TIKTOK)
        }
    }

    @Test
    fun testInstagramUrls_DetectedCorrectly() {
        val testUrls = listOf(
            "https://www.instagram.com/reel/C1234567890/",
            "https://instagram.com/p/B1234567890/",
            "https://www.instagram.com/tv/A1234567890/"
        )

        testUrls.forEach { url ->
            val result = UrlValidator.validateAndExtract(url)
            assertThat(result.isValid).isTrue()
            assertThat(result.platform).isEqualTo(Platform.INSTAGRAM)
        }
    }

    @Test
    fun testRedditUrls_DetectedCorrectly() {
        val testUrls = listOf(
            "https://www.reddit.com/r/technology/comments/123456/exciting_tech_video/",
            "https://v.redd.it/abcdef123456"
        )

        testUrls.forEach { url ->
            val result = UrlValidator.validateAndExtract(url)
            assertThat(result.isValid).isTrue()
            assertThat(result.platform).isEqualTo(Platform.REDDIT)
        }
    }

    @Test
    fun testTwitterXUrls_DetectedCorrectly() {
        val testUrls = listOf(
            "https://twitter.com/OpenAI/status/1234567890123456789",
            "https://x.com/AndroidDev/status/9876543210987654321"
        )

        testUrls.forEach { url ->
            val result = UrlValidator.validateAndExtract(url)
            assertThat(result.isValid).isTrue()
            assertThat(result.platform).isEqualTo(Platform.X_TWITTER)
        }
    }

    @Test
    fun testFacebookUrls_DetectedCorrectly() {
        val testUrls = listOf(
            "https://www.facebook.com/watch/?v=123456789012345",
            "https://fb.watch/abcdef1234/"
        )

        testUrls.forEach { url ->
            val result = UrlValidator.validateAndExtract(url)
            assertThat(result.isValid).isTrue()
            assertThat(result.platform).isEqualTo(Platform.FACEBOOK)
        }
    }

    @Test
    fun testDirectVideoUrls_DetectedAsGeneric() {
        val testUrls = listOf(
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            "https://example.com/assets/presentation.webm"
        )

        testUrls.forEach { url ->
            val result = UrlValidator.validateAndExtract(url)
            assertThat(result.isValid).isTrue()
            assertThat(result.platform).isEqualTo(Platform.GENERIC)
        }
    }

    @Test
    fun testShareIntentTextWithSurroundingWords_ExtractedCorrectly() {
        val sharedText = "Check out this amazing video! https://youtu.be/dQw4w9WgXcQ it blew my mind!"
        val result = UrlValidator.validateAndExtract(sharedText)
        assertThat(result.isValid).isTrue()
        assertThat(result.sanitizedUrl).isEqualTo("https://youtu.be/dQw4w9WgXcQ")
        assertThat(result.platform).isEqualTo(Platform.YOUTUBE)
    }

    @Test
    fun testInvalidAndEmptyUrls_ProperlyRejected() {
        val emptyResult = UrlValidator.validateAndExtract("")
        assertThat(emptyResult.isValid).isFalse()

        val whitespaceResult = UrlValidator.validateAndExtract("   ")
        assertThat(whitespaceResult.isValid).isFalse()

        val garbageResult = UrlValidator.validateAndExtract("just some random text without links")
        assertThat(garbageResult.isValid).isFalse()
    }
}
