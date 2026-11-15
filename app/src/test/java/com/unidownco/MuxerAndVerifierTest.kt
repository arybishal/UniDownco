package com.unidownco

import com.unidownco.domain.models.FormatOption
import com.unidownco.engine.verifier.AndroidMediaVerifier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MuxerAndVerifierTest {

    @Test
    fun testFormatOption_DualStreamDetected() {
        val format = FormatOption(
            formatId = "137",
            resolution = "1080p",
            width = 1920,
            height = 1080,
            fps = 60,
            videoCodec = "h264",
            audioCodec = "aac",
            container = "mp4",
            videoUrl = "https://example.com/video.mp4",
            audioUrl = "https://example.com/audio.mp4"
        )

        assertNotNull(format.audioUrl)
        assertEquals("https://example.com/audio.mp4", format.audioUrl)
        assertEquals("1080p", format.resolution)
    }

    @Test
    fun testFormatOption_SingleCombinedStream() {
        val format = FormatOption(
            formatId = "22",
            resolution = "720p",
            videoUrl = "https://example.com/combined.mp4",
            audioUrl = null
        )

        assertEquals(null, format.audioUrl)
        assertEquals("720p", format.resolution)
    }

    @Test
    fun testVerifier_NonExistentFileReportsInvalid() = runBlocking {
        val verifier = AndroidMediaVerifier()
        val nonExistent = File("non_existent_file_xyz.mp4")
        val report = verifier.verifyMedia(nonExistent)

        assertFalse(report.isValid)
        assertFalse(report.isFileIntegrityVerified)
        assertTrue(report.errorDetails?.contains("does not exist") == true)
    }
}
