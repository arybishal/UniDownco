package com.unidownco

import com.google.common.truth.Truth.assertThat
import com.unidownco.engine.inspector.MediaInspectorEngineImpl
import com.unidownco.engine.inspector.model.HdrClassification
import com.unidownco.engine.inspector.model.WarningSeverity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileNotFoundException

class MediaInspectorEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val engine = MediaInspectorEngineImpl()

    private fun getFixture(name: String): File {
        val f1 = File("src/test/resources/fixtures/$name")
        if (f1.exists()) return f1
        val f2 = File("app/src/test/resources/fixtures/$name")
        if (f2.exists()) return f2
        throw IllegalStateException("Fixture not found: $name")
    }

    @Test
    fun testInspect_FixtureA_GenuineHdr10() = runTest {
        val file = getFixture("fixture_a_hdr10_main10_true2020.mp4")
        val result = engine.inspect(file)

        assertThat(result.isSuccess).isTrue()
        val report = result.getOrThrow()

        assertThat(report.hasVideo).isTrue()
        assertThat(report.overview.primaryVideoCodec).contains("HEVC")
        assertThat(report.hdrDetails.bitDepth).isEqualTo(10)
        assertThat(report.hdrDetails.colorPrimariesName).isEqualTo("BT.2020")
        assertThat(report.hdrDetails.colorTransferName).isEqualTo("SMPTE ST 2084 (PQ)")

        // Optical mastering metadata verified
        assertThat(report.hdrDetails.masteringDisplay).isNotNull()
        assertThat(report.hdrDetails.masteringDisplay?.maxLuminanceNits).isEqualTo(1000.0)
        assertThat(report.hdrDetails.masteringDisplay?.minLuminanceNits).isEqualTo(0.001)

        // Content light level verified
        assertThat(report.hdrDetails.contentLightLevel).isNotNull()
        assertThat(report.hdrDetails.contentLightLevel?.maxCllNits).isEqualTo(1000)
        assertThat(report.hdrDetails.contentLightLevel?.maxFallNits).isEqualTo(400)

        // Strict Genuine HDR10 classification
        assertThat(report.isGenuineHdr10).isTrue()
        assertThat(report.hdrDetails.classification).isEqualTo(HdrClassification.GENUINE_HDR10)
        assertThat(report.overview.hdrClassification).isEqualTo(HdrClassification.GENUINE_HDR10)
        assertThat(report.containerDetails.boxHierarchy).isNotEmpty()
        assertThat(report.isComplete).isTrue()
    }

    @Test
    fun testInspect_FixtureC_SdrBt709() = runTest {
        val file = getFixture("fixture_c_sdr_main_8bit.mp4")
        val result = engine.inspect(file)

        assertThat(result.isSuccess).isTrue()
        val report = result.getOrThrow()

        assertThat(report.hasVideo).isTrue()
        assertThat(report.hdrDetails.bitDepth).isEqualTo(8)
        assertThat(report.hdrDetails.classification).isEqualTo(HdrClassification.SDR_BT709)
        assertThat(report.overview.hdrClassification).isEqualTo(HdrClassification.SDR_BT709)
        assertThat(report.isGenuineHdr10).isFalse()
    }

    @Test
    fun testInspect_FixtureD_FakeHdrRejected() = runTest {
        val file = getFixture("fixture_d_fake_hdr_8bit.mp4")
        val result = engine.inspect(file)

        assertThat(result.isSuccess).isTrue()
        val report = result.getOrThrow()

        assertThat(report.hasVideo).isTrue()
        assertThat(report.hdrDetails.bitDepth).isEqualTo(8)

        // 8-bit stream with HDR signaling must be rejected with FAKE_HDR_REJECTED
        assertThat(report.hdrDetails.classification).isEqualTo(HdrClassification.FAKE_HDR_REJECTED)
        assertThat(report.overview.hdrClassification).isEqualTo(HdrClassification.FAKE_HDR_REJECTED)
        assertThat(report.isGenuineHdr10).isFalse()

        // Critical warning emitted
        val fakeWarning = report.warnings.firstOrNull { it.code == "FAKE_HDR_DETECTED" }
        assertThat(fakeWarning).isNotNull()
        assertThat(fakeWarning?.severity).isEqualTo(WarningSeverity.CRITICAL)
    }

    @Test
    fun testInspect_EmptyFile_ReturnsEmptyReportWithErrors() = runTest {
        val emptyFile = tempFolder.newFile("empty.mp4")
        val result = engine.inspect(emptyFile)

        assertThat(result.isSuccess).isTrue()
        val report = result.getOrThrow()

        assertThat(report.isComplete).isFalse()
        assertThat(report.errors).isNotEmpty()
        val err = report.errors.first()
        assertThat(err.code).isEqualTo("EMPTY_FILE")
        assertThat(err.isMalformed).isTrue()
    }

    @Test
    fun testInspect_NonExistentFile_ReturnsFailure() = runTest {
        val nonExistent = File(tempFolder.root, "ghost_video.mp4")
        val result = engine.inspect(nonExistent)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(FileNotFoundException::class.java)
    }

    @Test
    fun testInspect_CoroutineCancellation() = runTest {
        val file = getFixture("fixture_a_hdr10_main10_true2020.mp4")
        val job = launch(kotlinx.coroutines.Dispatchers.Default) {
            engine.inspect(file)
        }
        job.cancelAndJoin()
        assertThat(job.isCancelled).isTrue()
    }
}
