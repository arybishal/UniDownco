package com.unidownco

import com.google.common.truth.Truth.assertThat
import com.unidownco.engine.hdr.MetadataProvenance
import com.unidownco.engine.inspector.classifier.HdrClassificationEngineImpl
import com.unidownco.engine.inspector.model.HdrClassification
import com.unidownco.engine.inspector.model.VideoStreamDetails
import com.unidownco.engine.inspector.model.WarningSeverity
import com.unidownco.engine.parser.HevcBitstreamReport
import com.unidownco.engine.parser.HevcSeiClli
import com.unidownco.engine.parser.HevcSeiMdcv
import com.unidownco.engine.parser.HevcSpsInfo
import com.unidownco.engine.parser.Mp4ClliBox
import com.unidownco.engine.parser.Mp4ColrBox
import com.unidownco.engine.parser.Mp4HvcCBox
import com.unidownco.engine.parser.Mp4MdcvBox
import com.unidownco.engine.parser.Mp4MetadataReport
import org.junit.Test

class HdrClassificationEngineTest {

    private val classifier = HdrClassificationEngineImpl()

    @Test
    fun testGenuineHdr10_Main10_Bt2020_Pq_WithMasteringDisplay() {
        val mp4Report = Mp4MetadataReport(
            hasMoov = true,
            videoCodecFourCc = "hvc1",
            colrBox = Mp4ColrBox("nclx", colorPrimaries = 9, transferCharacteristics = 16, matrixCoefficients = 9, fullRangeFlag = false),
            mdcvBox = Mp4MdcvBox(17000, 34000, 8500, 3000, 35400, 14600, 15635, 16450, maxMasteringLuminance = 10000000L, minMasteringLuminance = 10L),
            clliBox = Mp4ClliBox(maxCll = 1000, maxFall = 400),
            hvcCBox = Mp4HvcCBox(1, 0, false, profileIdc = 2, profileCompatibilityFlags = 0L, levelIdc = 120, bitDepthLuma = 10, bitDepthChroma = 10, emptyList()),
            isMain10 = true,
            isBt2020 = true,
            isSmpte2084 = true,
            isGenuineHdr10 = true
        )

        val result = classifier.evaluate(mp4Report, null, null)

        assertThat(result.classification).isEqualTo(HdrClassification.GENUINE_HDR10)
        assertThat(result.hdrColorDetails.bitDepth).isEqualTo(10)
        assertThat(result.hdrColorDetails.colorPrimariesName).isEqualTo("BT.2020")
        assertThat(result.hdrColorDetails.colorTransferName).isEqualTo("SMPTE ST 2084 (PQ)")
        assertThat(result.hdrColorDetails.masteringDisplay).isNotNull()
        assertThat(result.hdrColorDetails.masteringDisplay?.provenance).isEqualTo(MetadataProvenance.SOURCE_DECLARED)
        assertThat(result.hdrColorDetails.masteringDisplay?.maxLuminanceNits).isEqualTo(1000.0)
        assertThat(result.hdrColorDetails.masteringDisplay?.minLuminanceNits).isEqualTo(0.001)
        assertThat(result.hdrColorDetails.contentLightLevel?.maxCllNits).isEqualTo(1000)
        assertThat(result.hdrColorDetails.contentLightLevel?.maxFallNits).isEqualTo(400)
    }

    @Test
    fun testPartialHdr_Main10_Bt2020_Pq_MissingMasteringDisplay() {
        val mp4Report = Mp4MetadataReport(
            hasMoov = true,
            videoCodecFourCc = "hvc1",
            colrBox = Mp4ColrBox("nclx", colorPrimaries = 9, transferCharacteristics = 16, matrixCoefficients = 9, fullRangeFlag = false),
            mdcvBox = null,
            clliBox = null,
            hvcCBox = Mp4HvcCBox(1, 0, false, profileIdc = 2, profileCompatibilityFlags = 0L, levelIdc = 120, bitDepthLuma = 10, bitDepthChroma = 10, emptyList()),
            isMain10 = true,
            isBt2020 = true,
            isSmpte2084 = true,
            isGenuineHdr10 = false
        )

        val result = classifier.evaluate(mp4Report, null, null)

        assertThat(result.classification).isEqualTo(HdrClassification.PARTIAL_HDR)
        assertThat(result.hdrColorDetails.masteringDisplay).isNull()
        assertThat(result.warnings.any { it.code == "HDR10_METADATA_MISSING" }).isTrue()
    }

    @Test
    fun testFakeHdrRejected_8BitWithHdrSignaling() {
        val mp4Report = Mp4MetadataReport(
            hasMoov = true,
            videoCodecFourCc = "hvc1",
            colrBox = Mp4ColrBox("nclx", colorPrimaries = 9, transferCharacteristics = 16, matrixCoefficients = 9, fullRangeFlag = false),
            mdcvBox = null,
            clliBox = null,
            hvcCBox = Mp4HvcCBox(1, 0, false, profileIdc = 1, profileCompatibilityFlags = 0L, levelIdc = 120, bitDepthLuma = 8, bitDepthChroma = 8, emptyList()),
            isMain10 = false,
            isBt2020 = true,
            isSmpte2084 = true,
            isGenuineHdr10 = false
        )

        val result = classifier.evaluate(mp4Report, null, null)

        assertThat(result.classification).isEqualTo(HdrClassification.FAKE_HDR_REJECTED)
        val warning = result.warnings.firstOrNull { it.code == "FAKE_HDR_DETECTED" }
        assertThat(warning).isNotNull()
        assertThat(warning?.severity).isEqualTo(WarningSeverity.CRITICAL)
    }

    @Test
    fun testFakeHdrRejected_ContradictoryBitDepth() {
        val mp4Report = Mp4MetadataReport(
            hasMoov = true,
            videoCodecFourCc = "hvc1",
            colrBox = Mp4ColrBox("nclx", colorPrimaries = 9, transferCharacteristics = 16, matrixCoefficients = 9, fullRangeFlag = false),
            mdcvBox = null,
            clliBox = null,
            hvcCBox = Mp4HvcCBox(1, 0, false, profileIdc = 2, profileCompatibilityFlags = 0L, levelIdc = 120, bitDepthLuma = 10, bitDepthChroma = 10, emptyList()),
            isMain10 = true,
            isBt2020 = true,
            isSmpte2084 = true,
            isGenuineHdr10 = false
        )

        // SPS explicitly confirms 8-bit Main profile
        val sps = HevcSpsInfo(
            profileIdc = 1,
            profileName = "Main",
            bitDepthLuma = 8,
            bitDepthChroma = 8,
            picWidth = 1920,
            picHeight = 1080,
            hasVui = true,
            colorPrimaries = 9,
            transferCharacteristics = 16,
            matrixCoefficients = 9
        )
        val bitstreamReport = HevcBitstreamReport(
            spsList = listOf(sps),
            mdcvSeiList = emptyList(),
            clliSeiList = emptyList(),
            isMain10 = false,
            is10Bit = false,
            isBt2020 = true,
            isSmpte2084 = true,
            hasStaticMetadata = false
        )

        val result = classifier.evaluate(mp4Report, bitstreamReport, null)

        assertThat(result.classification).isEqualTo(HdrClassification.FAKE_HDR_REJECTED)
        assertThat(result.warnings.any { it.code == "CONTRADICTORY_BIT_DEPTH_HDR" }).isTrue()
    }

    @Test
    fun testSdr_Bt709_8Bit() {
        val mp4Report = Mp4MetadataReport(
            hasMoov = true,
            videoCodecFourCc = "avc1",
            colrBox = Mp4ColrBox("nclx", colorPrimaries = 1, transferCharacteristics = 1, matrixCoefficients = 1, fullRangeFlag = false),
            mdcvBox = null,
            clliBox = null,
            hvcCBox = null,
            isMain10 = false,
            isBt2020 = false,
            isSmpte2084 = false,
            isGenuineHdr10 = false
        )

        val videoStream = VideoStreamDetails(
            trackIndex = 0,
            trackId = 1,
            codecName = "AVC / H.264",
            codecMime = "video/avc",
            width = 1920,
            height = 1080,
            bitDepth = 8
        )

        val result = classifier.evaluate(mp4Report, null, videoStream)

        assertThat(result.classification).isEqualTo(HdrClassification.SDR_BT709)
        assertThat(result.hdrColorDetails.colorPrimariesName).isEqualTo("BT.709")
        assertThat(result.hdrColorDetails.colorTransferName).isEqualTo("BT.709")
    }

    @Test
    fun testHlg_Transfer18() {
        val mp4Report = Mp4MetadataReport(
            hasMoov = true,
            videoCodecFourCc = "hvc1",
            colrBox = Mp4ColrBox("nclx", colorPrimaries = 9, transferCharacteristics = 18, matrixCoefficients = 9, fullRangeFlag = false),
            mdcvBox = null,
            clliBox = null,
            hvcCBox = Mp4HvcCBox(1, 0, false, profileIdc = 2, profileCompatibilityFlags = 0L, levelIdc = 120, bitDepthLuma = 10, bitDepthChroma = 10, emptyList()),
            isMain10 = true,
            isBt2020 = true,
            isSmpte2084 = false,
            isGenuineHdr10 = false
        )

        val result = classifier.evaluate(mp4Report, null, null)

        assertThat(result.classification).isEqualTo(HdrClassification.HLG)
        assertThat(result.hdrColorDetails.colorTransferName).isEqualTo("ARIB STD-B67 (HLG)")
    }

    @Test
    fun testMalformed_MinLuminanceGreaterThanMax() {
        val mp4Report = Mp4MetadataReport(
            hasMoov = true,
            videoCodecFourCc = "hvc1",
            colrBox = Mp4ColrBox("nclx", colorPrimaries = 9, transferCharacteristics = 16, matrixCoefficients = 9, fullRangeFlag = false),
            mdcvBox = Mp4MdcvBox(17000, 34000, 8500, 3000, 35400, 14600, 15635, 16450, maxMasteringLuminance = 10000000L, minMasteringLuminance = 20000000L),
            clliBox = null,
            hvcCBox = Mp4HvcCBox(1, 0, false, profileIdc = 2, profileCompatibilityFlags = 0L, levelIdc = 120, bitDepthLuma = 10, bitDepthChroma = 10, emptyList()),
            isMain10 = true,
            isBt2020 = true,
            isSmpte2084 = true,
            isGenuineHdr10 = false
        )

        val result = classifier.evaluate(mp4Report, null, null)

        assertThat(result.classification).isEqualTo(HdrClassification.MALFORMED_OR_CONFLICTING)
        assertThat(result.errors.any { it.code == "MALFORMED_MASTERING_LUMINANCE" }).isTrue()
    }

    @Test
    fun testMetadataConflictAlert_ContainerVsBitstreamPrimaries() {
        val mp4Report = Mp4MetadataReport(
            hasMoov = true,
            videoCodecFourCc = "hvc1",
            colrBox = Mp4ColrBox("nclx", colorPrimaries = 1, transferCharacteristics = 16, matrixCoefficients = 1, fullRangeFlag = false),
            mdcvBox = null,
            clliBox = null,
            hvcCBox = Mp4HvcCBox(1, 0, false, profileIdc = 2, profileCompatibilityFlags = 0L, levelIdc = 120, bitDepthLuma = 10, bitDepthChroma = 10, emptyList()),
            isMain10 = true,
            isBt2020 = false,
            isSmpte2084 = true,
            isGenuineHdr10 = false
        )

        val sps = HevcSpsInfo(
            profileIdc = 2,
            profileName = "Main 10",
            bitDepthLuma = 10,
            bitDepthChroma = 10,
            picWidth = 1920,
            picHeight = 1080,
            hasVui = true,
            colorPrimaries = 9, // BT.2020
            transferCharacteristics = 16,
            matrixCoefficients = 9
        )
        val bitstreamReport = HevcBitstreamReport(
            spsList = listOf(sps),
            mdcvSeiList = emptyList(),
            clliSeiList = emptyList(),
            isMain10 = true,
            is10Bit = true,
            isBt2020 = true,
            isSmpte2084 = true,
            hasStaticMetadata = false
        )

        val result = classifier.evaluate(mp4Report, bitstreamReport, null)

        assertThat(result.conflicts).isNotEmpty()
        val primaryConflict = result.conflicts.firstOrNull { it.fieldName == "color_primaries" }
        assertThat(primaryConflict).isNotNull()
        assertThat(primaryConflict?.containerValue).contains("BT.709")
        assertThat(primaryConflict?.bitstreamValue).contains("BT.2020")
        assertThat(result.warnings.any { it.code == "METADATA_CONFLICT" }).isTrue()
    }

    @Test
    fun testUnknown_NoColorMetadata() {
        val mp4Report = Mp4MetadataReport(
            hasMoov = true,
            videoCodecFourCc = null,
            colrBox = null,
            mdcvBox = null,
            clliBox = null,
            hvcCBox = null,
            isMain10 = false,
            isBt2020 = false,
            isSmpte2084 = false,
            isGenuineHdr10 = false
        )

        val result = classifier.evaluate(mp4Report, null, null)

        assertThat(result.classification).isEqualTo(HdrClassification.UNKNOWN)
    }
}
