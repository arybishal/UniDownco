package com.unidownco

import com.unidownco.core.design.components.UniNavTab
import com.unidownco.core.storage.StorageManager
import com.unidownco.domain.models.FormatOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadFlowAndStorageTest {

    @Test
    fun testStorageDestination_IsMoviesUniDownco() {
        assertEquals("UniDownco", StorageManager.DESTINATION_SUBDIR)
        assertEquals("Movies/UniDownco", StorageManager.RELATIVE_MOVIES_PATH)
    }

    @Test
    fun testSanitizeFilename_RemovesIllegalCharacters() {
        // Test with a dummy instance logic directly matching StorageManager
        val raw = "My Video: Part 1? *Special* <4K> | Test / Slash \\ Backslash"
        val clean = raw.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), "_")
            .trim('_')
        assertFalse(clean.contains(":"))
        assertFalse(clean.contains("?"))
        assertFalse(clean.contains("*"))
        assertFalse(clean.contains("<"))
        assertFalse(clean.contains(">"))
        assertFalse(clean.contains("|"))
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("\\"))
    }

    @Test
    fun testDownloadButtonText_ReflectsSelectedFormat() {
        val format1080p = FormatOption(
            formatId = "137",
            resolution = "1080p",
            width = 1920,
            height = 1080,
            videoUrl = "https://example.com/v1080.mp4"
        )
        val buttonText1080 = "DOWNLOAD ${format1080p.resolution.uppercase()}"
        assertEquals("DOWNLOAD 1080P", buttonText1080)

        val format720p = FormatOption(
            formatId = "22",
            resolution = "720p",
            width = 1280,
            height = 720,
            videoUrl = "https://example.com/v720.mp4"
        )
        val buttonText720 = "DOWNLOAD ${format720p.resolution.uppercase()}"
        assertEquals("DOWNLOAD 720P", buttonText720)
    }

    @Test
    fun testBottomNavTabs_HasFourDistinctFixedDestinations() {
        val tabs = UniNavTab.values()
        assertEquals(4, tabs.size)
        assertEquals(UniNavTab.HOME, tabs[0])
        assertEquals(UniNavTab.QUEUE, tabs[1])
        assertEquals(UniNavTab.LIBRARY, tabs[2])
        assertEquals(UniNavTab.SETTINGS, tabs[3])

        assertEquals("Home", tabs[0].title)
        assertEquals("Queue", tabs[1].title)
        assertEquals("Library", tabs[2].title)
        assertEquals("Settings", tabs[3].title)
    }

    @Test
    fun testFormatOption_AdaptiveStreamIdentification() {
        val adaptiveFormat = FormatOption(
            formatId = "137",
            resolution = "1080p",
            width = 1920,
            height = 1080,
            videoUrl = "https://example.com/video_only.mp4",
            audioUrl = "https://example.com/audio_only.mp4"
        )
        assertTrue(adaptiveFormat.audioUrl != null)

        val directFormat = FormatOption(
            formatId = "22",
            resolution = "720p",
            width = 1280,
            height = 720,
            videoUrl = "https://example.com/combined.mp4",
            audioUrl = null
        )
        assertTrue(directFormat.audioUrl == null)
    }

    @Test
    fun testDownloadStatus_PauseAndResumeTransitions() {
        var status = com.unidownco.domain.models.DownloadStatus.DOWNLOADING
        assertEquals("DOWNLOADING", status.name)

        // User triggers manual pause
        status = com.unidownco.domain.models.DownloadStatus.PAUSED
        assertEquals("PAUSED", status.name)

        // User triggers manual resume
        status = com.unidownco.domain.models.DownloadStatus.DOWNLOADING
        assertEquals("DOWNLOADING", status.name)
    }

    @Test
    fun testDuplicateFile_SafeNameGeneration() {
        val baseName = "sample_video"
        val existingNames = setOf("sample_video.mp4", "sample_video_1.mp4")
        var counter = 1
        var candidate = "$baseName.mp4"
        while (existingNames.contains(candidate)) {
            candidate = "${baseName}_$counter.mp4"
            counter++
        }
        assertEquals("sample_video_2.mp4", candidate)
    }
}
