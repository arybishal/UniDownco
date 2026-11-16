package com.unidownco

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.unidownco.engine.inspector.MediaInspectorEngineImpl
import com.unidownco.engine.inspector.model.HdrClassification
import com.unidownco.engine.inspector.model.WarningSeverity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MediaInspectorDeviceTest {

    private val engine = MediaInspectorEngineImpl()

    @Test
    fun testDeviceInspection_RealMediaExtractor_FixtureA() {
        runBlocking {
            val file = File("/data/local/tmp/fixture_a_hdr10_main10_true2020.mp4")
            assertTrue("Fixture A must exist on device", file.exists())

            val result = engine.inspect(file)
            assertTrue("Inspection must succeed", result.isSuccess)

            val report = result.getOrThrow()
            assertTrue(report.isComplete)
            assertTrue(report.hasVideo)
            assertTrue(report.isGenuineHdr10)
            assertEquals(HdrClassification.GENUINE_HDR10, report.hdrDetails.classification)
            assertEquals(10, report.hdrDetails.bitDepth)
            assertEquals("BT.2020", report.hdrDetails.colorPrimariesName)
            assertEquals("SMPTE ST 2084 (PQ)", report.hdrDetails.colorTransferName)

            // Check optical mastering metadata
            assertNotNull(report.hdrDetails.masteringDisplay)
            assertEquals(1000.0, report.hdrDetails.masteringDisplay?.maxLuminanceNits ?: 0.0, 0.001)

            // Check content light level
            assertNotNull(report.hdrDetails.contentLightLevel)
            assertEquals(1000, report.hdrDetails.contentLightLevel?.maxCllNits)
            assertEquals(400, report.hdrDetails.contentLightLevel?.maxFallNits)
        }
    }

    @Test
    fun testDeviceInspection_MultiTrackAndTimingAnalysis_RealMedia() {
        runBlocking {
            val file = File("/data/local/tmp/zoo.mp4")
            if (!file.exists()) return@runBlocking

            val result = engine.inspect(file)
            assertTrue("Inspection of real video on device must succeed", result.isSuccess)

            val report = result.getOrThrow()
            assertTrue(report.hasVideo)

            val video = report.videoStreams.first()
            assertTrue(video.width > 0)
            assertTrue(video.height > 0)
            assertTrue(video.displayWidth ?: 0 > 0)
            assertTrue(video.displayHeight ?: 0 > 0)

            // Frame rate analysis on physical hardware
            val fps = video.averageFrameRate ?: video.nominalFrameRate
            assertNotNull("FPS must not be null", fps)
            assertTrue(fps!! > 0.0)

            // Audio track inspection
            if (report.hasAudio) {
                val audio = report.audioStreams.first()
                assertTrue(audio.channelCount >= 1)
                assertTrue(audio.sampleRateHz >= 8000)
                assertTrue(audio.codecName.isNotEmpty())
            }
        }
    }

    @Test
    fun testDeviceInspection_FakeHdrRejection_FixtureD() {
        runBlocking {
            val file = File("/data/local/tmp/fixture_d_fake_hdr_8bit.mp4")
            assertTrue("Fixture D must exist on device", file.exists())

            val result = engine.inspect(file)
            assertTrue(result.isSuccess)

            val report = result.getOrThrow()
            assertEquals(HdrClassification.FAKE_HDR_REJECTED, report.hdrDetails.classification)
            assertFalse(report.isGenuineHdr10)

            val fakeWarning = report.warnings.firstOrNull { it.code == "FAKE_HDR_DETECTED" }
            assertNotNull(fakeWarning)
            assertEquals(WarningSeverity.CRITICAL, fakeWarning?.severity)
        }
    }

    @Test
    fun testDeviceInspection_SdrBt709_FixtureC() {
        runBlocking {
            val file = File("/data/local/tmp/fixture_c_sdr_main_8bit.mp4")
            assertTrue("Fixture C must exist on device", file.exists())

            val result = engine.inspect(file)
            assertTrue(result.isSuccess)

            val report = result.getOrThrow()
            assertEquals(HdrClassification.SDR_BT709, report.hdrDetails.classification)
            assertFalse(report.isGenuineHdr10)
            assertEquals(8, report.hdrDetails.bitDepth)
        }
    }
}
