package com.unidownco

import com.google.common.truth.Truth.assertThat
import com.unidownco.engine.hardware.HardwareCapabilities
import com.unidownco.engine.inspector.evaluator.DeviceCompatibilityEvaluatorImpl
import com.unidownco.engine.inspector.model.CompatibilityStatus
import com.unidownco.engine.inspector.model.ContainerDetails
import com.unidownco.engine.inspector.model.HdrClassification
import com.unidownco.engine.inspector.model.HdrColorDetails
import com.unidownco.engine.inspector.model.MediaInspectionReport
import com.unidownco.engine.inspector.model.MediaOverview
import com.unidownco.engine.inspector.model.VideoStreamDetails
import org.junit.Test

class DeviceCompatibilityEvaluatorTest {

    private val evaluator = DeviceCompatibilityEvaluatorImpl()

    private val xiaomiCapabilities = HardwareCapabilities(
        deviceModel = "Xiaomi 25062RN2DA",
        socBoard = "bengal",
        androidVersion = 36,
        hasHardwareHevcEncoder = true,
        hardwareEncoderName = "c2.qti.hevc.encoder",
        softwareEncoderName = "c2.android.hevc.encoder",
        supportsHardwareMain = true,
        supportsHardwareMain10 = false, // Honestly reported hardware limitation
        supportsSoftwareMain10 = false,
        supportsSurfaceInput = true,
        supports10BitYuv = false,
        maxWidth = 1920,
        maxHeight = 1088,
        maxFrameRate = 60,
        isSnapdragon8Gen3Class = false
    )

    private val flagshipCapabilities = HardwareCapabilities(
        deviceModel = "Flagship Snapdragon 8 Gen 3 Device",
        socBoard = "pineapple",
        androidVersion = 35,
        hasHardwareHevcEncoder = true,
        hardwareEncoderName = "c2.qti.hevc.encoder",
        supportsHardwareMain = true,
        supportsHardwareMain10 = true,
        supportsSoftwareMain10 = true,
        supportsSurfaceInput = true,
        supports10BitYuv = true,
        maxWidth = 3840,
        maxHeight = 2160,
        maxFrameRate = 120,
        isSnapdragon8Gen3Class = true
    )

    @Test
    fun testXiaomiSm6225_Main8Supported_Main10Unsupported() {
        val report = MediaInspectionReport(
            overview = MediaOverview(
                filename = "test.mp4",
                fileExtension = "mp4",
                containerFormat = "MP4",
                fileSizeBytes = 1000L,
                durationSeconds = 10L,
                hdrClassification = HdrClassification.SDR_BT709
            ),
            videoStreams = listOf(
                VideoStreamDetails(
                    trackIndex = 0,
                    trackId = 1,
                    codecName = "HEVC / H.265",
                    codecMime = "video/hevc",
                    width = 1920,
                    height = 1080,
                    bitDepth = 8
                )
            ),
            hdrDetails = HdrColorDetails(classification = HdrClassification.SDR_BT709),
            containerDetails = ContainerDetails(containerFormat = "MP4")
        )

        val result = evaluator.evaluate(report, xiaomiCapabilities)

        assertThat(result.deviceModel).isEqualTo("Xiaomi 25062RN2DA")
        assertThat(result.socPlatform).isEqualTo("bengal")
        assertThat(result.hevcMain8HardwareEncode).isEqualTo(CompatibilityStatus.AVAILABLE)
        assertThat(result.hevcMain10HardwareEncode).isEqualTo(CompatibilityStatus.HARDWARE_UNSUPPORTED)
        assertThat(result.videoDecodeSupport).isEqualTo(CompatibilityStatus.AVAILABLE)
        assertThat(result.explanationNotes.any { it.contains("lacks Main10 profile support") }).isTrue()
    }

    @Test
    fun testFlagship_Main10Supported() {
        val report = MediaInspectionReport(
            overview = MediaOverview(
                filename = "hdr_video.mp4",
                fileExtension = "mp4",
                containerFormat = "MP4",
                fileSizeBytes = 5000L,
                durationSeconds = 20L,
                hdrClassification = HdrClassification.GENUINE_HDR10
            ),
            videoStreams = listOf(
                VideoStreamDetails(
                    trackIndex = 0,
                    trackId = 1,
                    codecName = "HEVC / H.265",
                    codecMime = "video/hevc",
                    width = 3840,
                    height = 2160,
                    bitDepth = 10
                )
            ),
            hdrDetails = HdrColorDetails(classification = HdrClassification.GENUINE_HDR10),
            containerDetails = ContainerDetails(containerFormat = "MP4")
        )

        val result = evaluator.evaluate(report, flagshipCapabilities)

        assertThat(result.hevcMain8HardwareEncode).isEqualTo(CompatibilityStatus.AVAILABLE)
        assertThat(result.hevcMain10HardwareEncode).isEqualTo(CompatibilityStatus.AVAILABLE)
        assertThat(result.tenBitYuvProcessingSupport).isEqualTo(CompatibilityStatus.AVAILABLE)
        assertThat(result.explanationNotes.any { it.contains("supports the full 10-bit pipeline") }).isTrue()
    }

    @Test
    fun testResolutionExceedingHardwareDecoder() {
        val report = MediaInspectionReport(
            overview = MediaOverview(
                filename = "4k_video.mp4",
                fileExtension = "mp4",
                containerFormat = "MP4",
                fileSizeBytes = 10000L,
                durationSeconds = 30L
            ),
            videoStreams = listOf(
                VideoStreamDetails(
                    trackIndex = 0,
                    trackId = 1,
                    codecName = "HEVC / H.265",
                    codecMime = "video/hevc",
                    width = 3840,
                    height = 2160,
                    bitDepth = 8
                )
            ),
            hdrDetails = HdrColorDetails(classification = HdrClassification.SDR_BT709),
            containerDetails = ContainerDetails(containerFormat = "MP4")
        )

        val result = evaluator.evaluate(report, xiaomiCapabilities)

        assertThat(result.videoDecodeSupport).isEqualTo(CompatibilityStatus.HARDWARE_UNSUPPORTED)
        assertThat(result.explanationNotes.any { it.contains("exceeds hardware limits") }).isTrue()
    }

    @Test
    fun testSourceGenuineHdr10_OnNonHdrEncoder_DisclosesLimitation() {
        val report = MediaInspectionReport(
            overview = MediaOverview(
                filename = "hdr_stream.mp4",
                fileExtension = "mp4",
                containerFormat = "MP4",
                fileSizeBytes = 2000L,
                durationSeconds = 15L,
                hdrClassification = HdrClassification.GENUINE_HDR10
            ),
            videoStreams = listOf(
                VideoStreamDetails(
                    trackIndex = 0,
                    trackId = 1,
                    codecName = "HEVC / H.265",
                    codecMime = "video/hevc",
                    width = 1920,
                    height = 1080,
                    bitDepth = 10
                )
            ),
            hdrDetails = HdrColorDetails(classification = HdrClassification.GENUINE_HDR10),
            containerDetails = ContainerDetails(containerFormat = "MP4")
        )

        val result = evaluator.evaluate(report, xiaomiCapabilities)

        assertThat(result.explanationNotes.any { it.contains("cannot re-encode it into Main10 hardware HDR") }).isTrue()
    }
}
