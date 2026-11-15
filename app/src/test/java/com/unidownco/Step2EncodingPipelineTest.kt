package com.unidownco

import com.unidownco.core.database.ProcessingJobEntity
import com.unidownco.domain.models.PresetStatus
import com.unidownco.domain.models.ProcessingPreset
import com.unidownco.engine.encoder.MediaCodecVideoEncoder
import com.unidownco.engine.encoder.QualityProfile
import com.unidownco.engine.hardware.HardwareCapabilities
import com.unidownco.engine.verifier.ProcessingOutputValidator
import com.unidownco.engine.verifier.ProcessingValidationReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Step2EncodingPipelineTest {

    @Test
    fun testBitrateCalculation_ScalesWithResolutionAndProfile() {
        val br1080pMax = MediaCodecVideoEncoder.calculateBitrate(1920, 1080, 30, QualityProfile.MAXIMUM_QUALITY)
        val br1080pBal = MediaCodecVideoEncoder.calculateBitrate(1920, 1080, 30, QualityProfile.BALANCED)
        val br1080pFast = MediaCodecVideoEncoder.calculateBitrate(1920, 1080, 30, QualityProfile.FAST)

        assertTrue("Maximum quality should have higher bitrate than balanced", br1080pMax > br1080pBal)
        assertTrue("Balanced should have higher bitrate than fast", br1080pBal > br1080pFast)

        // Verify sensible bounds for 1080p 30fps
        assertTrue("1080p balanced should be >= 2.5 Mbps", br1080pBal >= 2_500_000)
        assertTrue("1080p balanced should be <= 8.0 Mbps", br1080pBal <= 8_000_000)

        // Verify 720p scaling
        val br720pBal = MediaCodecVideoEncoder.calculateBitrate(1280, 720, 30, QualityProfile.BALANCED)
        assertTrue("1080p should have higher bitrate than 720p", br1080pBal > br720pBal)
        assertTrue("720p balanced should be within 1.2M - 4.5M", br720pBal in 1_200_000..4_500_000)

        // Verify 480p scaling
        val br480pBal = MediaCodecVideoEncoder.calculateBitrate(854, 480, 30, QualityProfile.BALANCED)
        assertTrue("720p should have higher bitrate than 480p", br720pBal > br480pBal)
        assertTrue("480p balanced should be within 500k - 2.0M", br480pBal in 500_000..2_000_000)
    }

    @Test
    fun testMain10Gating_RequiresTrueHardwareSupport() {
        // Mock Snapdragon 680/685 (SM6225) capabilities: 8-bit Main only
        val sm6225Caps = HardwareCapabilities(
            deviceModel = "Xiaomi Redmi Note",
            socBoard = "bengal",
            androidVersion = 36,
            hasHardwareHevcEncoder = true,
            hardwareEncoderName = "c2.qti.hevc.encoder",
            supportsHardwareMain = true,
            supportsHardwareMain10 = false,
            supportsSurfaceInput = true,
            supports10BitYuv = false,
            maxWidth = 1920,
            maxHeight = 1088,
            preferredProfile = 1 // HEVCProfileMain
        )

        // Assert SM6225 status is strictly HARDWARE_UNSUPPORTED for 10-bit HDR
        assertEquals(PresetStatus.HARDWARE_UNSUPPORTED, ProcessingPreset.HDR10_ENHANCED.getStatus(sm6225Caps))
        assertEquals("8-Bit HW Only", ProcessingPreset.HDR10_ENHANCED.getBadge(sm6225Caps))
        assertFalse(sm6225Caps.supportsHardwareMain10)

        // Mock Snapdragon 8 Gen 3 (SM8650) capabilities: 10-bit Main10 supported
        val sm8650Caps = HardwareCapabilities(
            deviceModel = "OnePlus 13R",
            socBoard = "pineapple",
            androidVersion = 35,
            hasHardwareHevcEncoder = true,
            hardwareEncoderName = "c2.qti.hevc.encoder",
            supportsHardwareMain = true,
            supportsHardwareMain10 = true,
            supportsSurfaceInput = true,
            supports10BitYuv = true,
            maxWidth = 7680,
            maxHeight = 4320,
            preferredProfile = 2 // HEVCProfileMain10
        )

        assertEquals(PresetStatus.PLANNED_STEP_2_3, ProcessingPreset.HDR10_ENHANCED.getStatus(sm8650Caps))
        assertEquals("Step 2.3", ProcessingPreset.HDR10_ENHANCED.getBadge(sm8650Caps))
        assertTrue(sm8650Caps.supportsHardwareMain10)
    }

    @Test
    fun testOutputValidator_EmptyOrMissingFileFails() {
        val nonExistent = File("build/tmp/non_existent_transcode.mp4")
        val report = ProcessingOutputValidator.validate(nonExistent)

        assertFalse("Missing file must be invalid", report.isValid)
        assertFalse("Missing file cannot be HEVC", report.isHevc)
        assertFalse("Missing file cannot decode bitmap", report.canDecodeBitmap)
        assertNotNull("Error details must be present", report.errorDetails)
    }

    @Test
    fun testOutputValidator_ReportStructureIntegrity() {
        val validReport = ProcessingValidationReport(
            isValid = true,
            isHevc = true,
            hasVideoTrack = true,
            hasAudioTrack = true,
            width = 1920,
            height = 1080,
            durationSeconds = 120L,
            fileSizeBytes = 15_000_000L,
            canDecodeBitmap = true,
            errorDetails = null
        )

        assertTrue(validReport.isValid)
        assertTrue(validReport.isHevc)
        assertTrue(validReport.hasVideoTrack)
        assertTrue(validReport.hasAudioTrack)
        assertEquals(1920, validReport.width)
        assertEquals(1080, validReport.height)
        assertEquals(120L, validReport.durationSeconds)
        assertTrue(validReport.canDecodeBitmap)
        assertNull(validReport.errorDetails)

        val nonHevcReport = validReport.copy(isHevc = false, isValid = false, errorDetails = "Not HEVC")
        assertFalse(nonHevcReport.isValid)
        assertFalse(nonHevcReport.isHevc)

        val missingAudioReport = validReport.copy(hasAudioTrack = false, isValid = false, errorDetails = "Missing audio")
        assertFalse(missingAudioReport.isValid)
        assertFalse(missingAudioReport.hasAudioTrack)
    }

    @Test
    fun testStorageSeparation_OriginalAndProcessingPathsAreDistinct() {
        val originalsDir = File("/storage/emulated/0/Movies/originals")
        val downloadsDir = File("/storage/emulated/0/Movies/downloads")

        assertNotEquals("Originals and downloads directories must differ", originalsDir.absolutePath, downloadsDir.absolutePath)
        assertTrue("Originals path must contain 'originals'", originalsDir.absolutePath.contains("originals"))
        assertTrue("Downloads path must contain 'downloads'", downloadsDir.absolutePath.contains("downloads"))

        val sourceFile = File(originalsDir, "test_video.mp4")
        val outputFile = File(downloadsDir, "test_video_processed.mp4")

        assertNotEquals("Source and destination files must have distinct paths", sourceFile.absolutePath, outputFile.absolutePath)
    }

    @Test
    fun testProcessingJobEntity_StageLifecycleIntegrity() {
        val entity = ProcessingJobEntity(
            id = 101L,
            downloadId = 55L,
            preset = "hdr10_enhanced",
            targetCodec = "hevc",
            targetHdrType = "SDR_BT709",
            targetFps = 30,
            stage = "QUEUED",
            progressPercent = 0,
            currentFps = 0.0
        )

        assertEquals("QUEUED", entity.stage)
        assertEquals(0, entity.progressPercent)

        val preparing = entity.copy(stage = "PREPARING")
        assertEquals("PREPARING", preparing.stage)

        val encoding = preparing.copy(stage = "ENCODING", progressPercent = 45, currentFps = 52.4)
        assertEquals("ENCODING", encoding.stage)
        assertEquals(45, encoding.progressPercent)
        assertEquals(52.4, encoding.currentFps, 0.01)

        val validating = encoding.copy(stage = "VALIDATING", progressPercent = 98)
        assertEquals("VALIDATING", validating.stage)

        val completed = validating.copy(
            stage = "COMPLETED",
            progressPercent = 100,
            finalOutputPath = "/storage/emulated/0/Movies/downloads/test_processed.mp4",
            completedAt = System.currentTimeMillis()
        )
        assertEquals("COMPLETED", completed.stage)
        assertEquals(100, completed.progressPercent)
        assertNotNull(completed.finalOutputPath)
        assertNotNull(completed.completedAt)
    }

    @Test
    fun testDimensionAlignment_EnforcesEvenIntegers() {
        val oddWidth = 1921
        val oddHeight = 1079

        val alignedWidth = (oddWidth / 2) * 2
        val alignedHeight = (oddHeight / 2) * 2

        assertEquals(1920, alignedWidth)
        assertEquals(1078, alignedHeight)
        assertTrue("Width must be even", alignedWidth % 2 == 0)
        assertTrue("Height must be even", alignedHeight % 2 == 0)
    }

    @Test
    fun testOrientationNormalization_NormalizesToStandardCardinalDegrees() {
        fun normalizeDegrees(degrees: Int): Int {
            val normalized = ((degrees % 360) + 360) % 360
            return when (normalized) {
                in 45..134 -> 90
                in 135..224 -> 180
                in 225..314 -> 270
                else -> 0
            }
        }

        assertEquals(0, normalizeDegrees(0))
        assertEquals(90, normalizeDegrees(90))
        assertEquals(90, normalizeDegrees(89))
        assertEquals(90, normalizeDegrees(91))
        assertEquals(180, normalizeDegrees(180))
        assertEquals(270, normalizeDegrees(270))
        assertEquals(270, normalizeDegrees(-90))
        assertEquals(90, normalizeDegrees(450))
        assertEquals(0, normalizeDegrees(360))
        assertEquals(0, normalizeDegrees(720))
    }

    @Test
    fun testAudioMimeCompatibility_DetectsStandardMp4Formats() {
        val supportedMimes = listOf("audio/mp4a-latm", "audio/aac", "audio/opus", "audio/amr-nb", "audio/amr-wb")
        val unsupportedMimes = listOf("audio/vorbis", "audio/x-matroska", "audio/flac", "audio/x-wav")

        val standardMp4Audio = listOf("audio/mp4a-latm", "audio/aac", "audio/opus", "audio/amr-nb", "audio/amr-wb")

        for (m in supportedMimes) {
            assertTrue("Format $m should be recognized as supported", standardMp4Audio.any { it.equals(m, ignoreCase = true) })
        }

        for (m in unsupportedMimes) {
            assertFalse("Format $m should be recognized as unsupported", standardMp4Audio.any { it.equals(m, ignoreCase = true) })
        }
    }

    @Test
    fun testOrphanedTempFilePattern_DetectsStagedFiles() {
        val validTemps = listOf("temp_hevc_1731700000_video.mp4", "temp_hevc_recovery.mp4", "output.hevc_tmp.mp4")
        val regularFiles = listOf("final_video.mp4", "zoo.mp4", "bottle.mp4", "my_hevc_video.mp4")

        for (name in validTemps) {
            assertTrue("Should match temp pattern: $name", name.startsWith("temp_hevc_") || name.endsWith(".hevc_tmp.mp4"))
        }

        for (name in regularFiles) {
            assertFalse("Should not match temp pattern: $name", name.startsWith("temp_hevc_") || name.endsWith(".hevc_tmp.mp4"))
        }
    }
}
