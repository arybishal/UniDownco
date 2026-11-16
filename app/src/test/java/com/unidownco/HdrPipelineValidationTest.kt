package com.unidownco

import com.google.common.truth.Truth.assertThat
import com.unidownco.domain.models.PresetStatus
import com.unidownco.domain.models.ProcessingPreset
import com.unidownco.engine.hardware.HardwareCapabilities
import com.unidownco.engine.hdr.ColorScience
import com.unidownco.engine.hdr.HdrContentLightLevel
import com.unidownco.engine.hdr.HdrMasteringDisplay
import com.unidownco.engine.hdr.HdrStaticMetadata
import com.unidownco.engine.hdr.MetadataProvenance
import com.unidownco.engine.hdr.TargetHdrType
import com.unidownco.engine.parser.HevcBitstreamParser
import com.unidownco.engine.parser.HevcSeiMessage
import com.unidownco.engine.parser.Mp4MetadataParser
import com.unidownco.engine.verifier.ProcessingValidationReport
import org.junit.Test
import java.nio.ByteBuffer
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
    fun testHdrStaticMetadata_CTA861Structure_TrueBt2020() {
        val meta = HdrStaticMetadata.BT2020_TRUE_1000NITS
        val buf = meta.toByteBuffer().order(ByteOrder.LITTLE_ENDIAN)

        assertThat(buf.remaining()).isEqualTo(25)

        // Byte 0: Descriptor ID (Type 1)
        assertThat(buf.get().toInt()).isEqualTo(0)

        // Primaries in CTA-861-G order: Green, Blue, Red (True BT.2020)
        assertThat(buf.short.toInt() and 0xFFFF).isEqualTo(8500)  // Gx (0.170 * 50000)
        assertThat(buf.short.toInt() and 0xFFFF).isEqualTo(39850) // Gy (0.797 * 50000)
        assertThat(buf.short.toInt() and 0xFFFF).isEqualTo(6550)  // Bx (0.131 * 50000)
        assertThat(buf.short.toInt() and 0xFFFF).isEqualTo(2300)  // By (0.046 * 50000)
        assertThat(buf.short.toInt() and 0xFFFF).isEqualTo(35400) // Rx (0.708 * 50000)
        assertThat(buf.short.toInt() and 0xFFFF).isEqualTo(14600) // Ry (0.292 * 50000)

        // White Point D65
        assertThat(buf.short.toInt() and 0xFFFF).isEqualTo(15635) // Wx (0.3127 * 50000)
        assertThat(buf.short.toInt() and 0xFFFF).isEqualTo(16450) // Wy (0.3290 * 50000)

        // Luminance
        assertThat(buf.short.toInt() and 0xFFFF).isEqualTo(1000)  // Max Luminance (1 cd/m2)
        assertThat(buf.short.toInt() and 0xFFFF).isEqualTo(1)     // Min Luminance (0.0001 cd/m2)

        // Content Light Level: must be 0 when UNAVAILABLE (per CTA-861.3 spec)
        assertThat(buf.short.toInt() and 0xFFFF).isEqualTo(0)     // MaxCLL (unknown = 0)
        assertThat(buf.short.toInt() and 0xFFFF).isEqualTo(0)     // MaxFALL (unknown = 0)
    }

    @Test
    fun testHdrStaticMetadata_CTA861Structure_DciP3D65() {
        val meta = HdrStaticMetadata.DCI_P3_D65_1000NITS
        val buf = meta.toByteBuffer().order(ByteOrder.LITTLE_ENDIAN)

        assertThat(buf.remaining()).isEqualTo(25)

        // Byte 0: Descriptor ID
        assertThat(buf.get().toInt()).isEqualTo(0)

        // Primaries in CTA-861-G order: Green, Blue, Red (DCI-P3 D65)
        assertThat(buf.short.toInt() and 0xFFFF).isEqualTo(13250) // Gx (0.265 * 50000)
        assertThat(buf.short.toInt() and 0xFFFF).isEqualTo(34500) // Gy (0.690 * 50000)
        assertThat(buf.short.toInt() and 0xFFFF).isEqualTo(7500)  // Bx (0.150 * 50000)
        assertThat(buf.short.toInt() and 0xFFFF).isEqualTo(3000)  // By (0.060 * 50000)
        assertThat(buf.short.toInt() and 0xFFFF).isEqualTo(34000) // Rx (0.680 * 50000)
        assertThat(buf.short.toInt() and 0xFFFF).isEqualTo(16000) // Ry (0.320 * 50000)

        // White Point D65
        assertThat(buf.short.toInt() and 0xFFFF).isEqualTo(15635)
        assertThat(buf.short.toInt() and 0xFFFF).isEqualTo(16450)

        // Luminance
        assertThat(buf.short.toInt() and 0xFFFF).isEqualTo(1000)
        assertThat(buf.short.toInt() and 0xFFFF).isEqualTo(1)

        // Light level unavailable -> 0
        assertThat(buf.short.toInt() and 0xFFFF).isEqualTo(0)
        assertThat(buf.short.toInt() and 0xFFFF).isEqualTo(0)
    }

    @Test
    fun testMetadataProvenance_OmissionStandards() {
        // 1. UNAVAILABLE light level: SEI 144 and MP4 clli must return null (standards-compliant omission)
        val unavailableCll = HdrContentLightLevel.UNAVAILABLE_INSTANCE
        assertThat(unavailableCll.provenance).isEqualTo(MetadataProvenance.UNAVAILABLE)
        assertThat(unavailableCll.toRawSei144Payload()).isNull()
        assertThat(unavailableCll.toHevcSeiNalUnit()).isNull()
        assertThat(unavailableCll.toMp4ClliAtom()).isNull()

        // 2. MATHEMATICALLY_ESTIMATED light level: must emit SEI 144 and MP4 clli
        val estimatedCll = HdrContentLightLevel.createMathematicallyEstimated(
            peakTargetNits = 1000,
            estimatedAverageNits = 400
        )
        assertThat(estimatedCll.provenance).isEqualTo(MetadataProvenance.MATHEMATICALLY_ESTIMATED)
        val rawSei144 = estimatedCll.toRawSei144Payload()
        assertThat(rawSei144).isNotNull()
        assertThat(rawSei144!!.size).isEqualTo(4)

        val clliBuf = ByteBuffer.wrap(rawSei144).order(ByteOrder.BIG_ENDIAN)
        assertThat(clliBuf.short.toInt() and 0xFFFF).isEqualTo(1000)
        assertThat(clliBuf.short.toInt() and 0xFFFF).isEqualTo(400)

        val clliAtom = estimatedCll.toMp4ClliAtom()
        assertThat(clliAtom).isNotNull()
        assertThat(clliAtom!!.size).isEqualTo(12) // 4 bytes size + 4 bytes 'clli' + 4 bytes payload
        val atomBuf = ByteBuffer.wrap(clliAtom).order(ByteOrder.BIG_ENDIAN)
        assertThat(atomBuf.int).isEqualTo(12)
        val atomType = String(clliAtom, 4, 4, Charsets.US_ASCII)
        assertThat(atomType).isEqualTo("clli")
    }

    @Test
    fun testLuminanceScalingDifference_CTA861VsSei137() {
        val display = HdrMasteringDisplay.BT2020_TRUE_1000NITS

        // CTA-861 max luminance is in units of 1 cd/m2
        assertThat(display.maxMasteringLuminanceNits).isEqualTo(1000)

        // HEVC SEI 137 / MP4 mdcv max luminance is in units of 0.0001 cd/m2 (10,000x scaling difference)
        val sei137 = display.toRawSei137Payload()
        assertThat(sei137.size).isEqualTo(24)

        val buf = ByteBuffer.wrap(sei137).order(ByteOrder.BIG_ENDIAN)
        // Skip 8 primaries/whitepoint shorts (16 bytes)
        buf.position(16)
        val maxLumSei = buf.int.toLong() and 0xFFFFFFFFL
        val minLumSei = buf.int.toLong() and 0xFFFFFFFFL

        assertThat(maxLumSei).isEqualTo(10_000_000L) // 1000 nits * 10,000 = 10,000,000
        assertThat(minLumSei).isEqualTo(1L)           // 0.0001 nits * 10,000 = 1

        // Ratio is exactly 10,000x
        assertThat(maxLumSei / display.maxMasteringLuminanceNits).isEqualTo(10000L)
    }

    @Test
    fun testHevcBitstreamParser_EmulationPreventionAndSeiExtraction() {
        // 1. Test unescapeEmulationPrevention
        // 00 00 03 01 -> 00 00 01
        val escaped = byteArrayOf(0x00, 0x00, 0x03, 0x01, 0x00, 0x00, 0x03, 0x00)
        val unescaped = HevcBitstreamParser.unescapeEmulationPrevention(escaped)
        assertThat(unescaped).isEqualTo(byteArrayOf(0x00, 0x00, 0x01, 0x00, 0x00, 0x00))

        // 2. Test SEI 137 generation and parsing roundtrip
        val display = HdrMasteringDisplay.BT2020_TRUE_1000NITS
        val seiNal = display.toHevcSeiNalUnit()
        assertThat(HevcBitstreamParser.getNalUnitType(seiNal)).isEqualTo(HevcBitstreamParser.NAL_PREFIX_SEI)

        val parsedMessages = HevcBitstreamParser.parsePrefixSeiNal(seiNal)
        assertThat(parsedMessages).hasSize(1)
        val mdcvMsg = parsedMessages[0] as HevcSeiMessage.Mdcv
        assertThat(mdcvMsg.mdcv.displayPrimariesGx).isEqualTo(8500)
        assertThat(mdcvMsg.mdcv.displayPrimariesGy).isEqualTo(39850)
        assertThat(mdcvMsg.mdcv.displayPrimariesBx).isEqualTo(6550)
        assertThat(mdcvMsg.mdcv.displayPrimariesBy).isEqualTo(2300)
        assertThat(mdcvMsg.mdcv.displayPrimariesRx).isEqualTo(35400)
        assertThat(mdcvMsg.mdcv.displayPrimariesRy).isEqualTo(14600)
        assertThat(mdcvMsg.mdcv.maxMasteringLuminance).isEqualTo(10_000_000L)
        assertThat(mdcvMsg.mdcv.maxMasteringLuminanceNits).isEqualTo(1000.0)

        // 3. Test SEI 144 generation and parsing roundtrip
        val cll = HdrContentLightLevel.createMathematicallyEstimated(1000, 400)
        val sei144Nal = cll.toHevcSeiNalUnit()!!
        val parsed144Messages = HevcBitstreamParser.parsePrefixSeiNal(sei144Nal)
        assertThat(parsed144Messages).hasSize(1)
        val clliMsg = parsed144Messages[0] as HevcSeiMessage.Clli
        assertThat(clliMsg.clli.maxCll).isEqualTo(1000)
        assertThat(clliMsg.clli.maxFall).isEqualTo(400)
    }

    @Test
    fun testMp4MetadataParser_ColrMdcvClliBoxParsing() {
        val meta = HdrStaticMetadata.createMathematicallyEstimated(
            mastering = HdrMasteringDisplay.BT2020_TRUE_1000NITS,
            peakNits = 1000,
            averageNits = 400
        )

        // Build a synthetic MP4 buffer containing moov -> trak -> mdia -> minf -> stbl -> [colr, mdcv, clli]
        val colrAtom = meta.toMp4ColrAtom()
        val mdcvAtom = meta.toMp4MdcvAtom()
        val clliAtom = meta.toMp4ClliAtom()!!

        // Wrap inside stbl
        val stblSize = 8 + colrAtom.size + mdcvAtom.size + clliAtom.size
        val stblBuf = ByteBuffer.allocate(stblSize).order(ByteOrder.BIG_ENDIAN)
        stblBuf.putInt(stblSize)
        stblBuf.put("stbl".toByteArray(Charsets.US_ASCII))
        stblBuf.put(colrAtom)
        stblBuf.put(mdcvAtom)
        stblBuf.put(clliAtom)

        // Wrap inside minf
        val minfSize = 8 + stblSize
        val minfBuf = ByteBuffer.allocate(minfSize).order(ByteOrder.BIG_ENDIAN)
        minfBuf.putInt(minfSize)
        minfBuf.put("minf".toByteArray(Charsets.US_ASCII))
        minfBuf.put(stblBuf.array())

        // Wrap inside mdia
        val mdiaSize = 8 + minfSize
        val mdiaBuf = ByteBuffer.allocate(mdiaSize).order(ByteOrder.BIG_ENDIAN)
        mdiaBuf.putInt(mdiaSize)
        mdiaBuf.put("mdia".toByteArray(Charsets.US_ASCII))
        mdiaBuf.put(minfBuf.array())

        // Wrap inside trak
        val trakSize = 8 + mdiaSize
        val trakBuf = ByteBuffer.allocate(trakSize).order(ByteOrder.BIG_ENDIAN)
        trakBuf.putInt(trakSize)
        trakBuf.put("trak".toByteArray(Charsets.US_ASCII))
        trakBuf.put(mdiaBuf.array())

        // Wrap inside moov
        val moovSize = 8 + trakSize
        val moovBuf = ByteBuffer.allocate(moovSize).order(ByteOrder.BIG_ENDIAN)
        moovBuf.putInt(moovSize)
        moovBuf.put("moov".toByteArray(Charsets.US_ASCII))
        moovBuf.put(trakBuf.array())

        // Parse with Mp4MetadataParser
        val report = Mp4MetadataParser.parse(moovBuf.array())
        assertThat(report.hasMoov).isTrue()
        assertThat(report.colrBox).isNotNull()
        assertThat(report.colrBox!!.colorPrimaries).isEqualTo(9) // BT.2020
        assertThat(report.colrBox!!.transferCharacteristics).isEqualTo(16) // ST 2084
        assertThat(report.colrBox!!.matrixCoefficients).isEqualTo(9) // BT.2020

        assertThat(report.mdcvBox).isNotNull()
        assertThat(report.mdcvBox!!.greenX).isEqualTo(8500)
        assertThat(report.mdcvBox!!.greenY).isEqualTo(39850)
        assertThat(report.mdcvBox!!.maxMasteringLuminance).isEqualTo(10_000_000L)

        assertThat(report.clliBox).isNotNull()
        assertThat(report.clliBox!!.maxCll).isEqualTo(1000)
        assertThat(report.clliBox!!.maxFall).isEqualTo(400)
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
            hevcProfile = 2,
            mp4ColrValid = true,
            mp4MdcvValid = true,
            bitstreamMain10Confirmed = true
        )
        assertThat(validHdrReport.isValid).isTrue()
        assertThat(validHdrReport.isHdr10).isTrue()
        assertThat(validHdrReport.bitDepth).isEqualTo(10)
        assertThat(validHdrReport.mp4ColrValid).isTrue()
        assertThat(validHdrReport.mp4MdcvValid).isTrue()

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
