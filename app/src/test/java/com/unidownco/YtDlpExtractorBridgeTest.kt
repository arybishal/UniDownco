package com.unidownco

import com.google.common.truth.Truth.assertThat
import com.unidownco.core.common.Platform
import com.unidownco.data.extractor.YtDlpExtractorBridge
import org.junit.Test

class YtDlpExtractorBridgeTest {

    @Test
    fun testParseYtDlpDumpJson_ProducesValidExtractionInfo() {
        val mockJson = """
            {
              "title": "Snapdragon 8 Gen 3 Adreno 750 Demo",
              "uploader": "Qualcomm Tech",
              "duration": 120,
              "thumbnail": "https://example.com/thumb.webp",
              "extractor_key": "youtube",
              "formats": [
                {
                  "format_id": "18",
                  "url": "https://example.com/stream_360p.mp4",
                  "height": 360,
                  "width": 640,
                  "fps": 30,
                  "vcodec": "avc1.42001E",
                  "acodec": "mp4a.40.2",
                  "ext": "mp4",
                  "filesize": 10000000
                },
                {
                  "format_id": "137",
                  "url": "https://example.com/stream_1080p.mp4",
                  "height": 1080,
                  "width": 1920,
                  "fps": 60,
                  "vcodec": "avc1.640028",
                  "acodec": "mp4a.40.2",
                  "ext": "mp4",
                  "filesize": 50000000
                }
              ]
            }
        """.trimIndent()

        val result = YtDlpExtractorBridge.parseYtDlpJson(
            jsonString = mockJson,
            originalUrl = "https://youtube.com/watch?v=sample"
        )

        assertThat(result).isNotNull()
        assertThat(result!!.title).isEqualTo("Snapdragon 8 Gen 3 Adreno 750 Demo")
        assertThat(result.author).isEqualTo("Qualcomm Tech")
        assertThat(result.platform).isEqualTo(Platform.YOUTUBE)
        assertThat(result.durationSeconds).isEqualTo(120L)
        assertThat(result.formats).hasSize(2)

        // Best format is highest resolution (1080p)
        val best = result.formats.first { it.isBestQuality }
        assertThat(best.resolution).isEqualTo("1080p")
        assertThat(best.height).isEqualTo(1080)
        assertThat(best.fps).isEqualTo(60)
    }

    @Test
    fun testParseYtDlpDumpJson_PairsAdaptiveVideoAndAudioStreams() {
        val mockAdaptiveJson = """
            {
              "title": "Adaptive Stream Test",
              "uploader": "Streamer",
              "duration": 60,
              "extractor_key": "youtube",
              "formats": [
                {
                  "format_id": "137",
                  "url": "https://example.com/video_1080p.mp4",
                  "height": 1080,
                  "width": 1920,
                  "fps": 60,
                  "vcodec": "avc1.640028",
                  "acodec": "none",
                  "ext": "mp4",
                  "filesize": 40000000
                },
                {
                  "format_id": "140",
                  "url": "https://example.com/audio_128k.m4a",
                  "vcodec": "none",
                  "acodec": "mp4a.40.2",
                  "abr": 128,
                  "ext": "m4a",
                  "filesize": 5000000
                }
              ]
            }
        """.trimIndent()

        val result = YtDlpExtractorBridge.parseYtDlpJson(
            jsonString = mockAdaptiveJson,
            originalUrl = "https://youtube.com/watch?v=adaptive"
        )

        assertThat(result).isNotNull()
        assertThat(result!!.formats).hasSize(1) // Only video stream represented as playable option
        val option = result.formats.first()
        assertThat(option.videoUrl).isEqualTo("https://example.com/video_1080p.mp4")
        assertThat(option.audioUrl).isEqualTo("https://example.com/audio_128k.m4a")
        assertThat(option.audioCodec).isEqualTo("mp4a.40.2")
    }
}
