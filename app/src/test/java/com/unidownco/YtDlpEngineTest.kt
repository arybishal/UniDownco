package com.unidownco

import com.google.common.truth.Truth.assertThat
import com.unidownco.core.common.Platform
import com.unidownco.core.common.Resource
import com.unidownco.data.provider.YtDlpExtractorProvider
import com.unidownco.engine.downloader.DownloadEngine
import com.unidownco.engine.ytdlp.YtDlpEngineManager
import kotlinx.coroutines.test.runTest
import org.junit.Test

class YtDlpEngineTest {

    @Test
    fun testParseSpeedFromLine_VariousUnits() {
        val speedKib = DownloadEngine.parseSpeedFromLine("[download]  10.0% of 10.00MiB at 512.0KiB/s ETA 00:10")
        assertThat(speedKib).isEqualTo(512 * 1024L)

        val speedMib = DownloadEngine.parseSpeedFromLine("[download]  50.0% of 50.00MiB at 4.50MiB/s ETA 00:05")
        assertThat(speedMib).isEqualTo((4.50 * 1024 * 1024).toLong())

        val speedGib = DownloadEngine.parseSpeedFromLine("[download]  90.0% of 1.00GiB at 1.20GiB/s ETA 00:01")
        assertThat(speedGib).isEqualTo((1.20 * 1024 * 1024 * 1024).toLong())

        val speedNull = DownloadEngine.parseSpeedFromLine(null)
        assertThat(speedNull).isEqualTo(0L)

        val speedBlank = DownloadEngine.parseSpeedFromLine("")
        assertThat(speedBlank).isEqualTo(0L)
    }

    @Test
    fun testYtDlpExtractorProvider_MockExtractionSuccess() = runTest {
        val mockJson = """
            {
              "title": "YouTube Test Video",
              "uploader": "Test Channel",
              "duration": 180,
              "thumbnail": "https://i.ytimg.com/vi/test/hqdefault.jpg",
              "extractor_key": "youtube",
              "formats": [
                {
                  "format_id": "18",
                  "url": "https://example.com/stream360.mp4",
                  "height": 360,
                  "width": 640,
                  "fps": 30,
                  "vcodec": "avc1.42001E",
                  "acodec": "mp4a.40.2",
                  "ext": "mp4",
                  "filesize": 12000000
                },
                {
                  "format_id": "137",
                  "url": "https://example.com/stream1080.mp4",
                  "height": 1080,
                  "width": 1920,
                  "fps": 60,
                  "vcodec": "avc1.640028",
                  "acodec": "none",
                  "ext": "mp4",
                  "filesize": 45000000
                },
                {
                  "format_id": "140",
                  "url": "https://example.com/audio128.m4a",
                  "vcodec": "none",
                  "acodec": "mp4a.40.2",
                  "abr": 128,
                  "ext": "m4a",
                  "filesize": 3500000
                }
              ]
            }
        """.trimIndent()

        val provider = YtDlpExtractorProvider(jsonExtractor = { mockJson })
        val result = provider.extract("https://www.youtube.com/watch?v=mock_yt")

        assertThat(result).isInstanceOf(Resource.Success::class.java)
        val data = (result as Resource.Success).data
        assertThat(data.title).isEqualTo("YouTube Test Video")
        assertThat(data.author).isEqualTo("Test Channel")
        assertThat(data.durationSeconds).isEqualTo(180L)
        assertThat(data.platform).isEqualTo(Platform.YOUTUBE)

        // Best format should be 1080p and paired with audio
        val best = data.formats.first { it.isBestQuality }
        assertThat(best.resolution).isEqualTo("1080p")
        assertThat(best.videoUrl).isEqualTo("https://example.com/stream1080.mp4")
        assertThat(best.audioUrl).isEqualTo("https://example.com/audio128.m4a")
        assertThat(best.audioCodec).isEqualTo("mp4a.40.2")
    }

    @Test
    fun testYtDlpExtractorProvider_CapabilitiesIncludeAllAdvancedModes() {
        val provider = YtDlpExtractorProvider()
        val caps = provider.getCapabilities(Platform.YOUTUBE)
        assertThat(caps).contains(com.unidownco.data.provider.ExtractionCapability.DIRECT_STREAMS)
        assertThat(caps).contains(com.unidownco.data.provider.ExtractionCapability.ADAPTIVE_STREAMS)
        assertThat(caps).contains(com.unidownco.data.provider.ExtractionCapability.AUDIO_ONLY)
        assertThat(caps).contains(com.unidownco.data.provider.ExtractionCapability.JS_DECIPHER)
        assertThat(caps).contains(com.unidownco.data.provider.ExtractionCapability.HDR_METADATA)
    }
}
