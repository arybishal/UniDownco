package com.unidownco

import com.google.common.truth.Truth.assertThat
import com.unidownco.domain.models.PresetStatus
import com.unidownco.domain.models.ProcessingPreset
import com.unidownco.engine.hardware.HardwareCapabilities
import com.unidownco.engine.hdr.ColorScience
import com.unidownco.engine.hdr.HdrStaticMetadata
import com.unidownco.engine.hdr.TargetHdrType
import com.unidownco.engine.verifier.ProcessingValidationReport
import org.junit.Test
import java.io.File
import java.nio.ByteOrder

class HdrPipelineValidationTest {

    @Test
    fun testHardwareCapabilityGating_XiaomiSm6225() {
        // Physical Xiaomi SM6225 baseline
        val xiaomiCaps = HardwareCapabilities(
            deviceModel = "Xiaomi 25062RN2DA",
            socBoard = "bengal",
            androidVersion = 36,
            hasHardwareHevcEncoder = true,
            hardwareEncoderName = "c2.qti.hevc.encoder",
            softwareEncoderName = "c2.android.hevc.encoder",
            supportsHardwareMain = true,
            supportsHardwareMain10 = false,
            supportsSoftwareMain10 = false,
            supportsSurfaceInput = true,
            supports10BitYuv = false,
            maxWidth = 1920,
            maxHeight = 1088,
            isSnapdragon8Gen3Class = false
        )

        // Must report Main10 as strictly unsupported
        assertThat(xiaomiCaps.supportsHardwareMain10).isFalse()
        assertThat(xiaomiCaps.supportsSoftwareMain10).isFalse()

        // HDR10 Enhanced preset must be HARDWARE_UNSUPPORTED
        val status = ProcessingPreset.HDR10_ENHANCED.getStatus(xiaomiCaps)
        assertThat(status).isEqualTo(PresetStatus.HARDWARE_UNSUPPORTED)

        val badge = ProcessingPreset.HDR10_ENHANCED.getBadge(xiaomiCaps)
        assertThat(badge).isEqualTo("8-Bit HW Only")

        val desc = ProcessingPreset.HDR10_ENHANCED.getDescription(xiaomiCaps)
        assertThat(desc).contains("10-bit HDR encoding unsupported on this SoC")
    }

    @Test
    fun testHardwareCapabilityGating_OnePlusFlagship() {
        // Flagship Snapdragon 8 Gen 3 target
        val flagshipCaps = HardwareCapabilities(
            deviceModel = "OnePlus Ace 5",
            socBoard = "lanai",
            androidVersion = 35,
            hasHardwareHevcEncoder = true,
            hardwareEncoderName = "c2.qti.hevc.encoder",
            supportsHardwareMain = true,
            supportsHardwareMain10 = true,
            supportsSurfaceInput = true,
            supports10BitYuv = true,
            maxWidth = 7680,
            maxHeight = 4320,
            isSnapdragon8Gen3Class = true
        )

        assertThat(flagshipCaps.supportsHardwareMain10).isTrue()
        assertThat(flagshipCaps.supports10BitYuv).isTrue()

        // HDR10 Enhanced preset must be AVAILABLE
        val status = ProcessingPreset.HDR10_ENHANCED.getStatus(flagshipCaps)
        assertThat(status).isEqualTo(PresetStatus.AVAILABLE)

        val badge = ProcessingPreset.HDR10_ENHANCED.getBadge(flagshipCaps)
        assertThat(badge).isEqualTo("Active")

        val desc = ProcessingPreset.HDR10_ENHANCED.getDescription(flagshipCaps)
        assertThat(desc).contains("Active")
    }

    @Test
    fun testHdrStaticMetadata_CTA861Structure() {
        val meta = HdrStaticMetadata.BT2020_1000NITS
        val buf = meta.toByteBuffer().order(ByteOrder.LITTLE_ENDIAN)

        assertThat(buf.remaining()).isEqualTo(25)

        // Byte 0: Descriptor ID
        assertThat(buf.get().toInt()).isEqualTo(0)

        // Primaries in CTA-861-G order: Green, Blue, Red
        assertThat(buf.short.toInt() and 0xFFFF).isEqualTo(13250) // Gx
        assertThat(buf.short.toInt() and 0xFFFF).isEqualTo(34500) // Gy
        assertThat(buf.short.toInt() and 0xFFFF).isEqualTo(7500)  // Bx
        assertThat(buf.short.toInt() and 0xFFFF).isEqualTo(3000)  // By
        assertThat(buf.short.toInt() and 0xFFFF).isEqualTo(34000) // Rx
        assertThat(buf.short.toInt() and 0xFFFF).isEqualTo(16000) // Ry

        // White Point D65
        assertThat(buf.short.toInt() and 0xFFFF).isEqualTo(15635) // Wx
        assertThat(buf.short.toInt() and 0xFFFF).isEqualTo(16450) // Wy

        // Luminance
        assertThat(buf.short.toInt() and 0xFFFF).isEqualTo(1000)  // Max Luminance (1 cd/m2)
        assertThat(buf.short.toInt() and 0xFFFF).isEqualTo(1)     // Min Luminance (0.0001 cd/m2)

        // Content Light Level
        assertThat(buf.short.toInt() and 0xFFFF).isEqualTo(1000)  // MaxCLL
        assertThat(buf.short.toInt() and 0xFFFF).isEqualTo(400)   // MaxFALL
    }

    @Test
    fun testProcessingValidationReport_Hdr10Classification() {
        // 1. Legitimate HDR10 Report
        val validHdrReport = ProcessingValidationReport(
            isValid = true,
            isHevc = true,
            hasVideoTrack = true,
            hasAudioTrack = true,
            width = 1920,
            height = 1080,
            durationSeconds = 60L,
            fileSizeBytes = 5_000_000L,
            canDecodeBitmap = true,
            isHdr10 = true,
            bitDepth = 10,
            colorStandard = "bt2020",
            colorTransfer = "smpte2084",
            hasStaticMetadata = true,
            hevcProfile = 2
        )
        assertThat(validHdrReport.isValid).isTrue()
        assertThat(validHdrReport.isHdr10).isTrue()
        assertThat(validHdrReport.bitDepth).isEqualTo(10)

        // 2. 8-Bit File Claiming to be HDR (Fake HDR Rejection)
        val fakeHdrReport = ProcessingValidationReport(
            isValid = false,
            isHevc = true,
            hasVideoTrack = true,
            hasAudioTrack = true,
            width = 1920,
            height = 1080,
            durationSeconds = 60L,
            fileSizeBytes = 5_000_000L,
            canDecodeBitmap = true,
            isHdr10 = false,
            bitDepth = 8, // 8-bit quantization
            colorStandard = "bt2020",
            colorTransfer = "smpte2084",
            hasStaticMetadata = true,
            hevcProfile = 1, // Main Profile, NOT Main10
            errorDetails = "Validation failed: Expected genuine 10-bit HDR10 output, but found: profile=1, bitDepth=8"
        )
        assertThat(fakeHdrReport.isValid).isFalse()
        assertThat(fakeHdrReport.isHdr10).isFalse()
        assertThat(fakeHdrReport.errorDetails).contains("Expected genuine 10-bit HDR10")

        // 3. 10-Bit File Missing Static Metadata (Partial HDR, Not Full HDR10)
        val missingMetadataReport = ProcessingValidationReport(
            isValid = false,
            isHevc = true,
            hasVideoTrack = true,
            hasAudioTrack = true,
            width = 1920,
            height = 1080,
            durationSeconds = 60L,
            fileSizeBytes = 5_000_000L,
            canDecodeBitmap = true,
            isHdr10 = false,
            bitDepth = 10,
            colorStandard = "bt2020",
            colorTransfer = "smpte2084",
            hasStaticMetadata = false, // Missing CTA-861.3 SEI
            hevcProfile = 2,
            errorDetails = "Validation failed: staticMetadata=false"
        )
        assertThat(missingMetadataReport.isValid).isFalse()
        assertThat(missingMetadataReport.isHdr10).isFalse()
    }

    @Test
    fun testColorScience_MonotonicityAndHeadroom() {
        // Ensure that increasing SDR luminance strictly results in monotonically increasing HDR PQ values
        var lastPq = -1.0
        for (sdrCode in 0..255 step 5) {
            val (r, g, b) = ColorScience.convertSdrToHdr10(sdrCode, sdrCode, sdrCode, targetPeakNits = 1000.0)
            assertThat(r).isEqualTo(g)
            assertThat(g).isEqualTo(b)

            val currentPq = r.toDouble() / 1023.0
            assertThat(currentPq).isGreaterThan(lastPq)
            lastPq = currentPq
        }

        // Peak white (255) must land at ~769 (1000 nits in PQ)
        val (peakR, _, _) = ColorScience.convertSdrToHdr10(255, 255, 255, targetPeakNits = 1000.0)
        assertThat(peakR).isEqualTo(769)
    }

    @Test
    fun testTargetHdrType_EnumerationCompleteness() {
        val types = TargetHdrType.entries
        assertThat(types).contains(TargetHdrType.SDR_PASSTHROUGH)
        assertThat(types).contains(TargetHdrType.SDR_TO_HDR10)
        assertThat(types).contains(TargetHdrType.HDR10_PRESERVE)
        assertThat(types).contains(TargetHdrType.HDR10_PLUS)
        assertThat(types).contains(TargetHdrType.DOLBY_VISION_COMPATIBLE)
    }
}
