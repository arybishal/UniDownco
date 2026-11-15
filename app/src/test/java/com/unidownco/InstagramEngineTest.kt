package com.unidownco

import com.google.common.truth.Truth.assertThat
import com.unidownco.core.common.Platform
import com.unidownco.core.common.Resource
import com.unidownco.data.extractor.UniversalExtractorRegistry
import com.unidownco.domain.models.VideoExtractionInfo
import com.unidownco.engine.instagram.InstagramErrorType
import com.unidownco.engine.instagram.InstagramMediaExtractor
import com.unidownco.engine.instagram.InstagramUrlResolver
import com.yausername.youtubedl_android.mapper.VideoFormat
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.test.runTest
import org.junit.Test

class InstagramEngineTest {

    @Test
    fun testInstagramUrlResolver_DetectsValidUrls() {
        assertThat(InstagramUrlResolver.isInstagramUrl("https://www.instagram.com/reel/Chunk8-jurw/")).isTrue()
        assertThat(InstagramUrlResolver.isInstagramUrl("https://instagram.com/p/aye83DjauH/")).isTrue()
        assertThat(InstagramUrlResolver.isInstagramUrl("https://www.instagram.com/tv/xyz123/")).isTrue()
        assertThat(InstagramUrlResolver.isInstagramUrl("https://www.instagram.com/share/reel/Chunk8-jurw/?igsh=MWt...&foo=bar")).isTrue()
        assertThat(InstagramUrlResolver.isInstagramUrl("https://www.instagram.com/reels/Chunk8-jurw")).isTrue()
    }

    @Test
    fun testInstagramUrlResolver_RejectsNonInstagramUrls() {
        assertThat(InstagramUrlResolver.isInstagramUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ")).isFalse()
        assertThat(InstagramUrlResolver.isInstagramUrl("https://www.facebook.com/reel/123456789")).isFalse()
        assertThat(InstagramUrlResolver.isInstagramUrl("https://www.tiktok.com/@user/video/12345")).isFalse()
        assertThat(InstagramUrlResolver.isInstagramUrl("https://reddit.com/r/videos/comments/xyz")).isFalse()
        assertThat(InstagramUrlResolver.isInstagramUrl("https://twitter.com/user/status/123")).isFalse()
        assertThat(InstagramUrlResolver.isInstagramUrl("https://example.com/video.mp4")).isFalse()
    }

    @Test
    fun testInstagramUrlResolver_NormalizesShareUrls() {
        val shareUrl = "https://www.instagram.com/share/reel/Chunk8-jurw/?igsh=MWt3bzVn&utm_source=ig_web_copy_link"
        val normalized = InstagramUrlResolver.normalizeUrl(shareUrl)
        assertThat(normalized).isEqualTo("https://www.instagram.com/reel/Chunk8-jurw/")
    }

    @Test
    fun testInstagramUrlResolver_NormalizesPostAndReelUrls() {
        val postWithTracking = "https://instagram.com/p/aye83DjauH/?utm_medium=copy_link"
        assertThat(InstagramUrlResolver.normalizeUrl(postWithTracking)).isEqualTo("https://www.instagram.com/p/aye83DjauH/")

        val tvUrl = "https://www.instagram.com/tv/aye83DjauH/"
        assertThat(InstagramUrlResolver.normalizeUrl(tvUrl)).isEqualTo("https://www.instagram.com/tv/aye83DjauH/")
    }

    @Test
    fun testInstagramUrlResolver_ExtractsShortcode() {
        assertThat(InstagramUrlResolver.extractShortcode("https://www.instagram.com/reel/Chunk8-jurw/")).isEqualTo("Chunk8-jurw")
        assertThat(InstagramUrlResolver.extractShortcode("https://instagram.com/p/aye83DjauH/")).isEqualTo("aye83DjauH")
        assertThat(InstagramUrlResolver.extractShortcode("https://www.instagram.com/share/reel/Chunk8-jurw/?igsh=123")).isEqualTo("Chunk8-jurw")
    }

    @Test
    fun testInstagramUrlResolver_ValidationFailsOnMalformed() {
        val result = InstagramUrlResolver.validateInstagramUrl("https://www.instagram.com/explore/")
        assertThat(result.isFailure).isTrue()

        val valid = InstagramUrlResolver.validateInstagramUrl("https://www.instagram.com/reel/Chunk8-jurw/")
        assertThat(valid.isSuccess).isTrue()
        assertThat(valid.getOrThrow()).isEqualTo("https://www.instagram.com/reel/Chunk8-jurw/")
    }

    @Test
    fun testInstagramMediaExtractor_PropertiesAndCapabilities() {
        val extractor = InstagramMediaExtractor()
        assertThat(extractor.providerId).isEqualTo("instagram_ytdlp")
        assertThat(extractor.priority).isEqualTo(105)
        assertThat(extractor.isAvailable).isTrue()

        assertThat(extractor.canHandle("https://www.instagram.com/reel/Chunk8-jurw/")).isTrue()
        assertThat(extractor.canHandle("https://www.youtube.com/watch?v=12345")).isFalse()
        assertThat(extractor.canHandle("https://www.facebook.com/reel/12345")).isFalse()
    }

    @Test
    fun testInstagramMediaExtractor_ErrorClassification() {
        val (type1, msg1) = InstagramMediaExtractor.classifyError("This account is private", "https://instagram.com/p/abc/")
        assertThat(type1).isEqualTo(InstagramErrorType.PRIVATE_CONTENT)
        assertThat(msg1).contains("private")

        val (type2, msg2) = InstagramMediaExtractor.classifyError("login required to view this content", "https://instagram.com/p/abc/")
        assertThat(type2).isEqualTo(InstagramErrorType.AUTHENTICATION_REQUIRED)
        assertThat(msg2).contains("authentication or session login")

        val (type3, msg3) = InstagramMediaExtractor.classifyError("HTTP Error 429: Too Many Requests", "https://instagram.com/p/abc/")
        assertThat(type3).isEqualTo(InstagramErrorType.RATE_LIMITED)
        assertThat(msg3).contains("rate limit")

        val (type4, msg4) = InstagramMediaExtractor.classifyError("Video unavailable: 404", "https://instagram.com/p/abc/")
        assertThat(type4).isEqualTo(InstagramErrorType.UNAVAILABLE_OR_DELETED)

        val (type5, msg5) = InstagramMediaExtractor.classifyError("UnknownHostException: Unable to connect", "https://instagram.com/p/abc/")
        assertThat(type5).isEqualTo(InstagramErrorType.NETWORK_ERROR)
    }

    @Test
    fun testInstagramMediaExtractor_MapInstagramVideoInfo() {
        val extractor = InstagramMediaExtractor()
        val formats = listOf(
            createFormat(
                formatId = "1080p_mp4",
                height = 1080,
                width = 1920,
                ext = "mp4",
                url = "https://cdninstagram.com/video_1080.mp4",
                vcodec = "h264",
                acodec = "aac",
                fileSize = 12000000L
            ),
            createFormat(
                formatId = "720p_mp4",
                height = 720,
                width = 1280,
                ext = "mp4",
                url = "https://cdninstagram.com/video_720.mp4",
                vcodec = "h264",
                acodec = "aac",
                fileSize = 6000000L
            )
        )
        val videoInfo = createVideoInfo(
            title = "Test Reel",
            uploader = "test_creator",
            duration = 15,
            thumbnail = "https://instagram.com/thumb.jpg",
            formats = formats
        )

        val result = extractor.mapInstagramVideoInfo(videoInfo, "https://www.instagram.com/reel/Chunk8-jurw/")
        assertThat(result.platform).isEqualTo(Platform.INSTAGRAM)
        assertThat(result.title).isEqualTo("Test Reel")
        assertThat(result.author).isEqualTo("test_creator")
        assertThat(result.durationSeconds).isEqualTo(15L)
        assertThat(result.formats.size).isEqualTo(2)

        // Best quality should be first (1080p)
        val best = result.formats.first()
        assertThat(best.height).isEqualTo(1080)
        assertThat(best.isBestQuality).isTrue()
        assertThat(best.videoUrl).isEqualTo("https://cdninstagram.com/video_1080.mp4")

        // Second should be 720p
        val second = result.formats[1]
        assertThat(second.height).isEqualTo(720)
        assertThat(second.isBestQuality).isFalse()
    }

    private fun createFormat(
        formatId: String,
        height: Int,
        width: Int,
        ext: String,
        url: String,
        vcodec: String?,
        acodec: String?,
        fileSize: Long
    ): VideoFormat {
        val f = VideoFormat()
        setField(f, "formatId", formatId)
        setField(f, "height", height)
        setField(f, "width", width)
        setField(f, "ext", ext)
        setField(f, "url", url)
        setField(f, "vcodec", vcodec)
        setField(f, "acodec", acodec)
        setField(f, "fileSize", fileSize)
        return f
    }

    private fun createVideoInfo(
        title: String,
        uploader: String,
        duration: Int,
        thumbnail: String,
        formats: List<VideoFormat>
    ): VideoInfo {
        val info = VideoInfo()
        setField(info, "title", title)
        setField(info, "uploader", uploader)
        setField(info, "duration", duration)
        setField(info, "thumbnail", thumbnail)
        setField(info, "formats", java.util.ArrayList(formats))
        return info
    }

    private fun setField(target: Any, fieldName: String, value: Any?) {
        val field = target::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }

    @Test
    fun testUniversalExtractorRegistry_InstagramRouting() {
        val registry = UniversalExtractorRegistry()
        val providers = registry.getProviders()

        // Verify Instagram provider is registered and has highest priority
        val first = providers.first()
        assertThat(first.providerId).isEqualTo("instagram_ytdlp")
        assertThat(first.priority).isEqualTo(105)

        // For Instagram URL, candidates contain Instagram provider first
        val igUrl = "https://www.instagram.com/reel/Chunk8-jurw/"
        val igCandidates = providers.filter { it.isAvailable && it.canHandle(igUrl) }
        assertThat(igCandidates.first().providerId).isEqualTo("instagram_ytdlp")

        // For YouTube URL, Instagram provider is NOT a candidate
        val ytUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        val ytCandidates = providers.filter { it.isAvailable && it.canHandle(ytUrl) }
        assertThat(ytCandidates.none { it.providerId == "instagram_ytdlp" }).isTrue()
        assertThat(ytCandidates.first().providerId).isEqualTo("yt-dlp")

        // For Facebook URL, Instagram provider is NOT a candidate
        val fbUrl = "https://www.facebook.com/reel/12345"
        val fbCandidates = providers.filter { it.isAvailable && it.canHandle(fbUrl) }
        assertThat(fbCandidates.none { it.providerId == "instagram_ytdlp" }).isTrue()
        assertThat(fbCandidates.first().providerId).isEqualTo("yt-dlp")
    }

    @Test
    fun testInstagramMediaExtractor_MockJsonExtraction() = runTest {
        val mockJson = """
            {
              "id": "Chunk8-jurw",
              "title": "Public Reel by photographer",
              "uploader": "nature_lens",
              "duration": 30,
              "thumbnail": "https://cdn.example.com/thumb.jpg",
              "extractor": "Instagram",
              "extractor_key": "Instagram",
              "webpage_url": "https://www.instagram.com/reel/Chunk8-jurw/",
              "formats": [
                {
                  "format_id": "dash-video",
                  "height": 1080,
                  "width": 1920,
                  "ext": "mp4",
                  "url": "https://cdn.example.com/reel_1080.mp4",
                  "vcodec": "avc1.640028",
                  "acodec": "none",
                  "filesize": 15000000
                },
                {
                  "format_id": "dash-audio",
                  "ext": "m4a",
                  "url": "https://cdn.example.com/audio.m4a",
                  "vcodec": "none",
                  "acodec": "mp4a.40.2",
                  "abr": 128,
                  "filesize": 1000000
                }
              ]
            }
        """.trimIndent()

        val mockExtractor = InstagramMediaExtractor(jsonExtractor = { _ -> mockJson })
        val result = mockExtractor.extract("https://www.instagram.com/share/reel/Chunk8-jurw/?igsh=123")

        assertThat(result).isInstanceOf(Resource.Success::class.java)
        val info = (result as Resource.Success<VideoExtractionInfo>).data
        assertThat(info.title).isEqualTo("Public Reel by photographer")
        assertThat(info.author).isEqualTo("nature_lens")
        assertThat(info.platform).isEqualTo(Platform.INSTAGRAM)
        assertThat(info.formats).isNotEmpty()
        assertThat(info.formats.first().height).isEqualTo(1080)
        assertThat(info.formats.first().audioUrl).isEqualTo("https://cdn.example.com/audio.m4a")
    }
}
