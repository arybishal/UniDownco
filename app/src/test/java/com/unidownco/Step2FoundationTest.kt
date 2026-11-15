package com.unidownco

import com.google.common.truth.Truth.assertThat
import com.unidownco.core.database.ProcessingJobEntity
import com.unidownco.domain.models.ProcessingPreset
import com.unidownco.engine.analyzer.VideoAnalysisResult
import com.unidownco.engine.analyzer.VideoAnalyzerImpl
import com.unidownco.engine.hardware.HardwareCapabilityRegistry
import org.junit.Test

class Step2FoundationTest {

    @Test
    fun testProcessingPreset_parsingAndDefaults() {
        assertThat(ProcessingPreset.fromId("direct")).isEqualTo(ProcessingPreset.DIRECT)
        assertThat(ProcessingPreset.fromId("DIRECT")).isEqualTo(ProcessingPreset.DIRECT)
        assertThat(ProcessingPreset.fromId("hdr10_enhanced")).isEqualTo(ProcessingPreset.HDR10_ENHANCED)
        assertThat(ProcessingPreset.fromId("ultra_hdr_60fps")).isEqualTo(ProcessingPreset.ULTRA_HDR_60FPS)
        // Unknown id defaults to HDR10_ENHANCED (recommended)
        assertThat(ProcessingPreset.fromId("invalid_preset")).isEqualTo(ProcessingPreset.HDR10_ENHANCED)

        assertThat(ProcessingPreset.HDR10_ENHANCED.badge).isEqualTo("Recommended")
        assertThat(ProcessingPreset.DIRECT.badge).isEqualTo("Fastest")
        assertThat(ProcessingPreset.ULTRA_HDR_60FPS.badge).isEqualTo("Pro")
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
    fun testVideoAnalyzer_toEntityMapping() {
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
    fun testHardwareCapabilityRegistry_diagnosticReport() {
        val report = HardwareCapabilityRegistry.getDiagnosticReport()
        assertThat(report).contains("=== UNIDOWNCO HARDWARE DIAGNOSTIC REPORT ===")
        assertThat(report).contains("Adaptation Policy:")
        assertThat(report).contains("Active Preferred Encoder:")
    }
}
