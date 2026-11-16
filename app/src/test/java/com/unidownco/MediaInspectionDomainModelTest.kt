package com.unidownco

import com.google.common.truth.Truth.assertThat
import com.unidownco.engine.hdr.MetadataProvenance
import com.unidownco.engine.inspector.model.AudioStreamDetails
import com.unidownco.engine.inspector.model.BoxNode
import com.unidownco.engine.inspector.model.CompatibilityStatus
import com.unidownco.engine.inspector.model.ContainerDetails
import com.unidownco.engine.inspector.model.ContentLightLevelReport
import com.unidownco.engine.inspector.model.DeviceCompatibilityDetails
import com.unidownco.engine.inspector.model.HdrClassification
import com.unidownco.engine.inspector.model.HdrColorDetails
import com.unidownco.engine.inspector.model.InspectionError
import com.unidownco.engine.inspector.model.InspectionWarning
import com.unidownco.engine.inspector.model.MasteringDisplayReport
import com.unidownco.engine.inspector.model.MediaInspectionReport
import com.unidownco.engine.inspector.model.MediaOverview
import com.unidownco.engine.inspector.model.MetadataConflictAlert
import com.unidownco.engine.inspector.model.SubtitleStreamDetails
import com.unidownco.engine.inspector.model.VideoStreamDetails
import com.unidownco.engine.inspector.model.WarningSeverity
import org.junit.Test

class MediaInspectionDomainModelTest {

    @Test
    fun testMultiStreamEncapsulation_VideoAudioAndSubtitles() {
        val videoTrack1 = VideoStreamDetails(
            trackIndex = 0,
            trackId = 1,
            codecName = "HEVC",
            codecMime = "video/hevc",
            codecFourCc = "hvc1",
            width = 3840,
            height = 2160,
            nominalFrameRate = 60.0,
            bitDepth = 10,
            profileName = "Main 10",
            profileIdc = 2,
            colorPrimaries = "bt2020",
            colorTransfer = "smpte2084",
            matrixCoefficients = "bt2020nc",
            colorRange = "Limited"
        )

        val audioTrack1 = AudioStreamDetails(
            trackIndex = 1,
            trackId = 2,
            codecName = "AAC",
            codecMime = "audio/mp4a-latm",
            codecFourCc = "mp4a",
            channelCount = 6,
            channelLayout = "5.1 Surround",
            sampleRateHz = 48000,
            bitrateKbps = 384,
            language = "eng",
            isDefaultTrack = true
        )

        val audioTrack2 = AudioStreamDetails(
            trackIndex = 2,
            trackId = 3,
            codecName = "AAC",
            codecMime = "audio/mp4a-latm",
            codecFourCc = "mp4a",
            channelCount = 2,
            channelLayout = "Stereo (L/R)",
            sampleRateHz = 48000,
            bitrateKbps = 192,
            language = "nep",
            isDefaultTrack = false
        )

        val subTrack1 = SubtitleStreamDetails(
            trackIndex = 3,
            trackId = 4,
            codecName = "Timed Text",
            codecMime = "application/x-quicktime-tx3g",
            formatName = "Timed Text (tx3g)",
            language = "eng",
            isForced = false
        )

        val overview = MediaOverview(
            filename = "sample_4k_hdr10.mp4",
            fileExtension = "mp4",
            containerFormat = "MP4",
            fileSizeBytes = 104_857_600L,
            durationSeconds = 120L,
            primaryResolution = "3840x2160",
            primaryFrameRate = 60.0,
            primaryVideoCodec = "HEVC Main10",
            primaryAudioCodec = "AAC 5.1",
            hdrClassification = HdrClassification.GENUINE_HDR10
        )

        val hdrDetails = HdrColorDetails(
            classification = HdrClassification.GENUINE_HDR10,
            colorPrimariesName = "BT.2020",
            colorTransferName = "SMPTE ST 2084 (PQ)",
            matrixCoefficientsName = "BT.2020 non-constant luminance",
            colorRangeName = "Limited",
            bitDepth = 10,
            masteringDisplay = MasteringDisplayReport(
                greenX = 0.1700, greenY = 0.7970,
                blueX = 0.1310, blueY = 0.0460,
                redX = 0.7080, redY = 0.2920,
                whitePointX = 0.3127, whitePointY = 0.3290,
                maxLuminanceNits = 1000.0, minLuminanceNits = 0.0001,
                provenance = MetadataProvenance.SOURCE_DECLARED,
                sourceLocation = "HEVC SEI 137"
            ),
            contentLightLevel = ContentLightLevelReport(
                maxCllNits = 1000,
                maxFallNits = 400,
                provenance = MetadataProvenance.SOURCE_DECLARED,
                sourceLocation = "HEVC SEI 144"
            ),
            containerAtomVerified = true,
            bitstreamSeiVerified = true
        )

        val container = ContainerDetails(
            containerFormat = "MP4",
            majorBrand = "mp42",
            minorVersion = 0L,
            compatibleBrands = listOf("mp42", "isom"),
            totalTracks = 4,
            videoTrackCount = 1,
            audioTrackCount = 2,
            subtitleTrackCount = 1
        )

        val report = MediaInspectionReport(
            overview = overview,
            videoStreams = listOf(videoTrack1),
            audioStreams = listOf(audioTrack1, audioTrack2),
            subtitleStreams = listOf(subTrack1),
            hdrDetails = hdrDetails,
            containerDetails = container
        )

        assertThat(report.hasVideo).isTrue()
        assertThat(report.hasAudio).isTrue()
        assertThat(report.hasSubtitles).isTrue()
        assertThat(report.totalTrackCount).isEqualTo(4)
        assertThat(report.isGenuineHdr10).isTrue()
        assertThat(report.audioStreams).hasSize(2)
        assertThat(report.audioStreams[1].language).isEqualTo("nep")
        assertThat(report.subtitleStreams[0].formatName).contains("tx3g")
    }

    @Test
    fun testMissingMetadata_PreservesNullWithoutFabricatedDefaults() {
        val sdrTrack = VideoStreamDetails(
            trackIndex = 0,
            trackId = 1,
            codecName = "AVC",
            codecMime = "video/avc",
            width = 1280,
            height = 720
        )

        // Nullable fields must be null, not 0 or false defaults
        assertThat(sdrTrack.nominalFrameRate).isNull()
        assertThat(sdrTrack.bitDepth).isNull()
        assertThat(sdrTrack.colorPrimaries).isNull()
        assertThat(sdrTrack.colorTransfer).isNull()
        assertThat(sdrTrack.matrixCoefficients).isNull()
        assertThat(sdrTrack.keyframeCount).isNull()

        val hdrDetails = HdrColorDetails(
            classification = HdrClassification.SDR_BT709
        )
        assertThat(hdrDetails.masteringDisplay).isNull()
        assertThat(hdrDetails.contentLightLevel).isNull()
        assertThat(hdrDetails.dolbyVisionInfo).isNull()
    }

    @Test
    fun testConflictingMetadata_AlertRecord() {
        val conflict = MetadataConflictAlert(
            fieldName = "color_primaries",
            containerValue = "BT.709 (colr atom)",
            bitstreamValue = "BT.2020 (SPS VUI)",
            frameworkValue = "BT.709",
            resolutionDescription = "Elementary bitstream SPS overrides container tag for decoding"
        )

        val hdrDetails = HdrColorDetails(
            classification = HdrClassification.PARTIAL_HDR,
            conflicts = listOf(conflict)
        )

        assertThat(hdrDetails.conflicts).hasSize(1)
        assertThat(hdrDetails.conflicts[0].fieldName).isEqualTo("color_primaries")
        assertThat(hdrDetails.conflicts[0].containerValue).contains("BT.709")
        assertThat(hdrDetails.conflicts[0].bitstreamValue).contains("BT.2020")
    }

    @Test
    fun testStructuredWarningsAndErrors() {
        val warning = InspectionWarning(
            severity = WarningSeverity.CRITICAL,
            code = "FAKE_HDR_SIGNALING",
            message = "BT.2020 primaries and ST 2084 PQ curve signaled on 8-bit stream",
            technicalImpact = "Stream will experience severe luminance banding and color clipping"
        )

        val error = InspectionError(
            code = "CONTAINER_CORRUPTED",
            message = "Atom 'moov' truncated before reaching declared box size",
            isMalformed = true,
            isUnsupportedFormat = false,
            cause = "Unexpected EOF at byte offset 4096"
        )

        val overview = MediaOverview(
            filename = "broken.mp4",
            fileExtension = "mp4",
            containerFormat = "MP4",
            fileSizeBytes = 4096L,
            durationSeconds = 0L,
            hdrClassification = HdrClassification.FAKE_HDR_REJECTED
        )

        val report = MediaInspectionReport(
            overview = overview,
            hdrDetails = HdrColorDetails(classification = HdrClassification.FAKE_HDR_REJECTED),
            containerDetails = ContainerDetails(containerFormat = "MP4"),
            warnings = listOf(warning),
            errors = listOf(error),
            isComplete = false
        )

        assertThat(report.isComplete).isFalse()
        assertThat(report.warnings).hasSize(1)
        assertThat(report.warnings[0].severity).isEqualTo(WarningSeverity.CRITICAL)
        assertThat(report.errors).hasSize(1)
        assertThat(report.errors[0].isMalformed).isTrue()
        assertThat(report.errors[0].isUnsupportedFormat).isFalse()
    }

    @Test
    fun testBoxNodeHierarchyTraversal() {
        val hvcC = BoxNode("hvcC", 100, 72, "HEVC Configuration Box")
        val colr = BoxNode("colr", 172, 19, "Color Parameter Box")
        val mdcv = BoxNode("mdcv", 191, 32, "Mastering Display Box")
        val hev1 = BoxNode("hev1", 20, 203, "HEVC Visual Sample Entry", listOf(hvcC, colr, mdcv))
        val stsd = BoxNode("stsd", 12, 211, "Sample Description Box", listOf(hev1))
        val stbl = BoxNode("stbl", 4, 219, "Sample Table Box", listOf(stsd))
        val moov = BoxNode("moov", 0, 223, "Movie Box", listOf(stbl))

        assertThat(moov.isContainer).isTrue()
        assertThat(hvcC.isContainer).isFalse()
        assertThat(moov.children).hasSize(1)
        assertThat(moov.totalDescendants).isEqualTo(6) // stbl, stsd, hev1, hvcC, colr, mdcv
    }

    @Test
    fun testDeviceCompatibilityDetailsModel() {
        val compat = DeviceCompatibilityDetails(
            deviceModel = "Xiaomi Redmi 25062RN2DA",
            socPlatform = "Snapdragon 680 (SM6225)",
            videoDecodeSupport = CompatibilityStatus.AVAILABLE,
            audioDecodeSupport = CompatibilityStatus.AVAILABLE,
            hevcMain8HardwareEncode = CompatibilityStatus.AVAILABLE,
            hevcMain10HardwareEncode = CompatibilityStatus.HARDWARE_UNSUPPORTED,
            surfaceInputPipelineSupport = CompatibilityStatus.AVAILABLE,
            tenBitYuvProcessingSupport = CompatibilityStatus.HARDWARE_UNSUPPORTED,
            maxResolutionSupported = "1920x1080",
            maxFrameRateSupported = 60,
            explanationNotes = listOf("Hardware Main10 encoder is not present on this SoC")
        )

        assertThat(compat.hevcMain8HardwareEncode).isEqualTo(CompatibilityStatus.AVAILABLE)
        assertThat(compat.hevcMain10HardwareEncode).isEqualTo(CompatibilityStatus.HARDWARE_UNSUPPORTED)
        assertThat(compat.explanationNotes).hasSize(1)
    }
}
