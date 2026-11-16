package com.unidownco

import android.app.Application
import android.content.Context
import com.unidownco.engine.inspector.MediaInspectorEngine
import com.unidownco.engine.inspector.model.AudioStreamDetails
import com.unidownco.engine.inspector.model.BoxNode
import com.unidownco.engine.inspector.model.CompatibilityStatus
import com.unidownco.engine.inspector.model.ContainerDetails
import com.unidownco.engine.inspector.model.DeviceCompatibilityDetails
import com.unidownco.engine.inspector.model.FrameRateMode
import com.unidownco.engine.inspector.model.HdrClassification
import com.unidownco.engine.inspector.model.HdrColorDetails
import com.unidownco.engine.inspector.model.MasteringDisplayReport
import com.unidownco.engine.inspector.model.MediaInspectionReport
import com.unidownco.engine.inspector.model.MediaOverview
import com.unidownco.engine.inspector.model.VideoStreamDetails
import com.unidownco.engine.inspector.source.MediaSource
import com.unidownco.engine.hdr.MetadataProvenance
import com.unidownco.feature.inspector.MediaInspectorUiState
import com.unidownco.feature.inspector.MediaInspectorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class MediaInspectorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockApplication = Application()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createSampleReport(): MediaInspectionReport {
        return MediaInspectionReport(
            overview = MediaOverview(
                filename = "sample_hdr10.mp4",
                fileExtension = "mp4",
                containerFormat = "MP4",
                fileSizeBytes = 52428800L,
                durationSeconds = 120L,
                overallBitrateKbps = 3500L,
                primaryResolution = "3840x2160",
                primaryFrameRate = 29.970,
                primaryVideoCodec = "HEVC Main10",
                primaryAudioCodec = "AAC-LC",
                hdrClassification = HdrClassification.GENUINE_HDR10
            ),
            videoStreams = listOf(
                VideoStreamDetails(
                    trackIndex = 0,
                    trackId = 1,
                    codecName = "hevc",
                    codecMime = "video/hevc",
                    codecFourCc = "hvc1",
                    width = 3840,
                    height = 2160,
                    displayAspectRatio = "16:9",
                    pixelAspectRatio = "1:1",
                    nominalFrameRate = 29.970,
                    averageFrameRate = 29.970,
                    frameRateMode = FrameRateMode.CONSTANT_FRAME_RATE,
                    bitrateKbps = 3200L,
                    bitDepth = 10,
                    profileName = "Main 10",
                    levelName = "5.1",
                    colorPrimaries = "BT.2020",
                    colorTransfer = "SMPTE ST 2084 (PQ)",
                    matrixCoefficients = "BT.2020 non-constant",
                    colorRange = "Limited",
                    rotationDegrees = 0
                )
            ),
            audioStreams = listOf(
                AudioStreamDetails(
                    trackIndex = 1,
                    trackId = 2,
                    codecName = "aac",
                    codecMime = "audio/mp4a-latm",
                    codecFourCc = "mp4a",
                    profileName = "LC",
                    channelCount = 2,
                    channelLayout = "Stereo",
                    sampleRateHz = 48000,
                    bitrateKbps = 192L,
                    language = "eng"
                )
            ),
            hdrDetails = HdrColorDetails(
                classification = HdrClassification.GENUINE_HDR10,
                colorPrimariesName = "BT.2020",
                colorTransferName = "SMPTE ST 2084 (PQ)",
                matrixCoefficientsName = "BT.2020 non-constant",
                colorRangeName = "Limited",
                bitDepth = 10,
                masteringDisplay = MasteringDisplayReport(
                    redX = 0.6800, redY = 0.3200,
                    greenX = 0.2650, greenY = 0.6900,
                    blueX = 0.1500, blueY = 0.0600,
                    whitePointX = 0.3127, whitePointY = 0.3290,
                    minLuminanceNits = 0.005,
                    maxLuminanceNits = 1000.0,
                    provenance = MetadataProvenance.SOURCE_DECLARED,
                    sourceLocation = "SEI mastering_display_colour_volume"
                ),
                containerAtomVerified = true,
                bitstreamSeiVerified = true
            ),
            containerDetails = ContainerDetails(
                containerFormat = "MP4",
                majorBrand = "mp42",
                boxHierarchy = listOf(
                    BoxNode("ftyp", 0, 32, "File Type and Compatibility"),
                    BoxNode("moov", 32, 4096, "Movie Metadata Container")
                )
            ),
            deviceCompatibility = DeviceCompatibilityDetails(
                deviceModel = "Redmi 25062RN2DA",
                socPlatform = "Qualcomm SM6225 (bengal)",
                videoDecodeSupport = CompatibilityStatus.AVAILABLE,
                audioDecodeSupport = CompatibilityStatus.AVAILABLE,
                hevcMain8HardwareEncode = CompatibilityStatus.AVAILABLE,
                hevcMain10HardwareEncode = CompatibilityStatus.HARDWARE_UNSUPPORTED,
                surfaceInputPipelineSupport = CompatibilityStatus.AVAILABLE,
                tenBitYuvProcessingSupport = CompatibilityStatus.AVAILABLE_WITH_LIMITATIONS,
                maxResolutionSupported = "1920x1080",
                maxFrameRateSupported = 60,
                explanationNotes = listOf(
                    "Qualcomm SM6225 lacks hardware HEVC Main10 encoder.",
                    "Playback is hardware accelerated via c2.qti.hevc.decoder."
                )
            )
        )
    }

    private class FakeMediaInspectorEngine(
        private val reportResult: Result<MediaInspectionReport>
    ) : MediaInspectorEngine {
        override suspend fun inspect(source: MediaSource): Result<MediaInspectionReport> = reportResult
        override suspend fun inspect(source: MediaSource, context: Context?): Result<MediaInspectionReport> = reportResult
        override suspend fun inspect(file: File): Result<MediaInspectionReport> = reportResult
        override suspend fun inspect(file: File, context: Context?): Result<MediaInspectionReport> = reportResult
        override suspend fun inspect(uri: android.net.Uri, context: Context): Result<MediaInspectionReport> = reportResult
    }

    @Test
    fun testInitialState_isIdle() {
        val engine = FakeMediaInspectorEngine(Result.success(createSampleReport()))
        val viewModel = MediaInspectorViewModel(mockApplication, engine, testDispatcher)

        assertEquals(MediaInspectorUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun testInspectFile_success_transitionsToSuccessState() = runTest {
        val sampleReport = createSampleReport()
        val engine = FakeMediaInspectorEngine(Result.success(sampleReport))
        val viewModel = MediaInspectorViewModel(mockApplication, engine, testDispatcher)

        val dummyFile = File("sample_hdr10.mp4")
        viewModel.inspectFile(dummyFile)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected Success state but was $state", state is MediaInspectorUiState.Success)
        val successState = state as MediaInspectorUiState.Success
        assertEquals("sample_hdr10.mp4", successState.report.overview.filename)
        assertEquals(HdrClassification.GENUINE_HDR10, successState.report.hdrDetails.classification)
    }

    @Test
    fun testInspectFile_failure_transitionsToErrorState() = runTest {
        val errorMsg = "Malformed box structure at offset 128"
        val engine = FakeMediaInspectorEngine(Result.failure(IllegalArgumentException(errorMsg)))
        val viewModel = MediaInspectorViewModel(mockApplication, engine, testDispatcher)

        val dummyFile = File("corrupted.mp4")
        viewModel.inspectFile(dummyFile)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected Error state but was $state", state is MediaInspectorUiState.Error)
        val errorState = state as MediaInspectorUiState.Error
        assertEquals(errorMsg, errorState.message)
        assertTrue(errorState.canRetry)
        assertNotNull(errorState.technicalDetails)
    }

    @Test
    fun testReset_returnsToIdleState() = runTest {
        val engine = FakeMediaInspectorEngine(Result.success(createSampleReport()))
        val viewModel = MediaInspectorViewModel(mockApplication, engine, testDispatcher)

        viewModel.inspectFile(File("sample.mp4"))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is MediaInspectorUiState.Success)

        viewModel.reset()
        assertEquals(MediaInspectorUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun testGenerateShareableSummary_containsFullReport() = runTest {
        val sampleReport = createSampleReport()
        val engine = FakeMediaInspectorEngine(Result.success(sampleReport))
        val viewModel = MediaInspectorViewModel(mockApplication, engine, testDispatcher)

        viewModel.inspectFile(File("sample_hdr10.mp4"))
        advanceUntilIdle()

        val summary = viewModel.generateShareableSummary()
        assertTrue("Summary should contain report title", summary.contains("UNIDOWNCO MEDIA INSPECTION REPORT"))
        assertTrue("Summary should contain filename", summary.contains("sample_hdr10.mp4"))
        assertTrue("Summary should contain HDR verdict", summary.contains("GENUINE_HDR10"))
        assertTrue("Summary should contain SMPTE ST 2086", summary.contains("Mastering Display Optics (SMPTE ST 2086)"))
        assertTrue("Summary should contain SM6225 device", summary.contains("Qualcomm SM6225 (bengal)"))
        assertTrue("Summary should contain hardware unsupported disclosure", summary.contains("Hardware Unsupported"))
        assertTrue("Summary should contain container atoms", summary.contains("[ftyp]"))
    }

    @Test
    fun testGenerateShareableSummary_whenNotSuccess_returnsEmpty() {
        val engine = FakeMediaInspectorEngine(Result.success(createSampleReport()))
        val viewModel = MediaInspectorViewModel(mockApplication, engine, testDispatcher)

        val summary = viewModel.generateShareableSummary()
        assertEquals("", summary)
    }
}
