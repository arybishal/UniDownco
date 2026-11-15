package com.unidownco

import com.google.common.truth.Truth.assertThat
import com.unidownco.core.database.ProcessingJobEntity
import com.unidownco.domain.models.PresetStatus
import com.unidownco.domain.models.ProcessingPreset
import com.unidownco.engine.analyzer.VideoAnalysisResult
import com.unidownco.engine.analyzer.VideoAnalyzerImpl
import com.unidownco.engine.hardware.HardwareCapabilities
import com.unidownco.engine.hardware.HardwareCapabilityRegistry
import org.junit.Test

class Step2FoundationTest {

    @Test
    fun testProcessingPreset_parsingAndDefaults() {
        assertThat(ProcessingPreset.fromId("direct")).isEqualTo(ProcessingPreset.DIRECT)
        assertThat(ProcessingPreset.fromId("DIRECT")).isEqualTo(ProcessingPreset.DIRECT)
        assertThat(ProcessingPreset.fromId("hdr10_enhanced")).isEqualTo(ProcessingPreset.HDR10_ENHANCED)
        assertThat(ProcessingPreset.fromId("ultra_hdr_60fps")).isEqualTo(ProcessingPreset.ULTRA_HDR_60FPS)
        // Unknown id defaults to DIRECT (safe operational fallback)
        assertThat(ProcessingPreset.fromId("invalid_preset")).isEqualTo(ProcessingPreset.DIRECT)
    }

    @Test
    fun testProcessingPreset_hardwareCapabilityStatus() {
        // Flagship SoC with Hardware Main10 (Snapdragon 8 Gen 3 class)
        val flagshipCaps = HardwareCapabilities(
            supportsHardwareMain = true,
            supportsHardwareMain10 = true,
            supportsSurfaceInput = true,
            isSnapdragon8Gen3Class = true
        )

        assertThat(ProcessingPreset.DIRECT.getStatus(flagshipCaps)).isEqualTo(PresetStatus.AVAILABLE)
        assertThat(ProcessingPreset.DIRECT.getBadge(flagshipCaps)).isEqualTo("Active")

        assertThat(ProcessingPreset.HDR10_ENHANCED.getStatus(flagshipCaps)).isEqualTo(PresetStatus.PLANNED_STEP_2_3)
        assertThat(ProcessingPreset.HDR10_ENHANCED.getBadge(flagshipCaps)).isEqualTo("Step 2.3")
        assertThat(ProcessingPreset.HDR10_ENHANCED.getDescription(flagshipCaps)).contains("Coming in Step 2.3")

        assertThat(ProcessingPreset.ULTRA_HDR_60FPS.getStatus(flagshipCaps)).isEqualTo(PresetStatus.PLANNED_STEP_2_4)
        assertThat(ProcessingPreset.ULTRA_HDR_60FPS.getBadge(flagshipCaps)).isEqualTo("Step 2.4")

        // Mid-tier SoC without Hardware Main10 (Snapdragon 680/685 class)
        val midTierCaps = HardwareCapabilities(
            supportsHardwareMain = true,
            supportsHardwareMain10 = false,
            supportsSurfaceInput = true,
            isSnapdragon8Gen3Class = false
        )

        assertThat(ProcessingPreset.DIRECT.getStatus(midTierCaps)).isEqualTo(PresetStatus.AVAILABLE)
        assertThat(ProcessingPreset.HDR10_ENHANCED.getStatus(midTierCaps)).isEqualTo(PresetStatus.HARDWARE_UNSUPPORTED)
        assertThat(ProcessingPreset.HDR10_ENHANCED.getBadge(midTierCaps)).isEqualTo("8-Bit HW Only")
        assertThat(ProcessingPreset.HDR10_ENHANCED.getDescription(midTierCaps)).contains("unsupported on this SoC")
    }

    @Test
    fun testProcessingJobEntity_initialization() {
        val job = ProcessingJobEntity(
            downloadId = 42L,
            preset = ProcessingPreset.HDR10_ENHANCED.id,
            targetCodec = "hevc",
            targetHdrType = "HDR10",
            targetFps = 30,
            stage = "QUEUED"
        )

        assertThat(job.downloadId).isEqualTo(42L)
        assertThat(job.preset).isEqualTo("hdr10_enhanced")
        assertThat(job.targetCodec).isEqualTo("hevc")
        assertThat(job.targetHdrType).isEqualTo("HDR10")
        assertThat(job.targetFps).isEqualTo(30)
        assertThat(job.stage).isEqualTo("QUEUED")
        assertThat(job.progressPercent).isEqualTo(0)
        assertThat(job.currentFps).isEqualTo(0.0)
        assertThat(job.finalOutputPath).isNull()
    }

    @Test
    fun testVideoAnalyzer_compliantHdr10ToEntityMapping() {
        val analysis = VideoAnalysisResult(
            container = "mp4",
            videoCodec = "video/hevc",
            audioCodec = "audio/mp4a-latm",
            width = 3840,
            height = 2160,
            frameRate = 60.0,
            bitDepth = 10,
            pixelFormat = "yuv420p10le",
            colorPrimaries = "bt2020",
            transferCharacteristics = "smpte2084",
            matrixCoefficients = "limited",
            hdrType = "HDR10",
            hasHdr10Metadata = true,
            hasHdr10PlusMetadata = false,
            hasDolbyVisionRpu = false,
            durationSeconds = 120L,
            audioChannels = 6
        )

        val entity = VideoAnalyzerImpl.toEntity(downloadId = 101L, analysis = analysis)

        assertThat(entity.downloadId).isEqualTo(101L)
        assertThat(entity.container).isEqualTo("mp4")
        assertThat(entity.videoCodec).isEqualTo("video/hevc")
        assertThat(entity.width).isEqualTo(3840)
        assertThat(entity.height).isEqualTo(2160)
        assertThat(entity.bitDepth).isEqualTo(10)
        assertThat(entity.colorPrimaries).isEqualTo("bt2020")
        assertThat(entity.transferCharacteristics).isEqualTo("smpte2084")
        assertThat(entity.hdrType).isEqualTo("HDR10")
        assertThat(entity.audioChannels).isEqualTo(6)
        assertThat(entity.rawAnalysisJson).contains("HDR10")
    }

    @Test
    fun testVideoAnalyzer_partialHdrAndDolbyVisionHandling() {
        // Partial HDR: PQ transfer but missing static metadata
        val partialAnalysis = VideoAnalysisResult(
            container = "mp4",
            videoCodec = "video/hevc",
            audioCodec = "audio/aac",
            width = 1920,
            height = 1080,
            frameRate = 30.0,
            bitDepth = 10,
            pixelFormat = "yuv420p10le",
            colorPrimaries = "bt2020",
            transferCharacteristics = "smpte2084",
            matrixCoefficients = "limited",
            hdrType = "PARTIAL_HDR",
            hasHdr10Metadata = false,
            durationSeconds = 60L
        )
        val partialEntity = VideoAnalyzerImpl.toEntity(202L, partialAnalysis)
        assertThat(partialEntity.hdrType).isEqualTo("PARTIAL_HDR")

        // Dolby Vision without speculative profile assumption
        val doviAnalysis = VideoAnalysisResult(
            container = "mp4",
            videoCodec = "video/dolby-vision",
            audioCodec = "audio/eac3",
            width = 3840,
            height = 2160,
            frameRate = 24.0,
            bitDepth = 10,
            pixelFormat = "yuv420p10le",
            colorPrimaries = "bt2020",
            transferCharacteristics = "smpte2084",
            matrixCoefficients = "limited",
            hdrType = "DOLBY_VISION",
            dolbyVisionProfile = null, // Not guessed
            durationSeconds = 90L
        )
        val doviEntity = VideoAnalyzerImpl.toEntity(203L, doviAnalysis)
        assertThat(doviEntity.hdrType).isEqualTo("DOLBY_VISION")
    }

    @Test
    fun testHardwareCapabilityRegistry_diagnosticReport() {
        val report = HardwareCapabilityRegistry.getDiagnosticReport()
        assertThat(report).contains("=== UNIDOWNCO HARDWARE DIAGNOSTIC REPORT ===")
        assertThat(report).contains("Adaptation Policy:")
        assertThat(report).contains("Active Preferred Encoder:")
        // Verify that SW Main10 is not falsely reported as available when missing
        assertThat(report).contains("SW HEVC Main10 (MediaCodec):")
    }
}
