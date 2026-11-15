package com.unidownco

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.unidownco.core.storage.StorageManager
import com.unidownco.engine.encoder.QualityProfile
import com.unidownco.engine.hardware.HardwareCapabilityRegistry
import com.unidownco.engine.processor.VideoProcessingEngine
import com.unidownco.engine.verifier.ProcessingOutputValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class MediaCodecEncodingDeviceTest {

    private fun findTestVideoOnDevice(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storage = StorageManager(context)
        val candidates = listOf(
            File(storage.originalsDir, "bottle.mp4"),
            File(storage.originalsDir, "zoo.mp4"),
            File("/sdcard/Android/data/com.unidownco/files/Movies/originals/bottle.mp4"),
            File("/sdcard/Android/data/com.unidownco.debug/files/Movies/originals/bottle.mp4"),
            File("/data/local/tmp/bottle.mp4")
        )
        for (c in candidates) {
            if (c.exists() && c.length() > 0) return c
        }
        throw IllegalStateException("No test video found on device")
    }

    private fun findVideoWithAudioOnDevice(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storage = StorageManager(context)
        val candidates = listOf(
            File(storage.originalsDir, "zoo.mp4"),
            File("/sdcard/Android/data/com.unidownco/files/Movies/originals/zoo.mp4"),
            File("/sdcard/Android/data/com.unidownco.debug/files/Movies/originals/zoo.mp4"),
            File("/data/local/tmp/zoo.mp4")
        )
        for (c in candidates) {
            if (c.exists() && c.length() > 0) return c
        }
        return findTestVideoOnDevice()
    }

    @Test
    fun test01_HardwareCapabilitiesDetectionOnDevice() {
        val caps = HardwareCapabilityRegistry.getCapabilities()
        assertNotNull(caps)
        assertTrue("Device should have hardware HEVC encoder", caps.hasHardwareHevcEncoder)
        assertEquals("c2.qti.hevc.encoder", caps.hardwareEncoderName)
        assertEquals(1920, caps.maxWidth)
        assertEquals(1920, caps.maxHeight)
        assertTrue("Hardware should support 8-bit Main", caps.supportsHardwareMain)
        assertFalse("Qualcomm SM6225 does not support 10-bit Main10", caps.supportsHardwareMain10)
        assertFalse("Software Main10 should be unsupported", caps.supportsSoftwareMain10)
        assertTrue("Surface input must be supported", caps.supportsSurfaceInput)
    }

    @Test
    fun test02_TranscodeShortSdrVideoToHevc() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val cacheDir = context.cacheDir
            val sourceVideo = findTestVideoOnDevice()
            assertTrue("Test video must exist", sourceVideo.exists())
            val originalLen = sourceVideo.length()

            val localSource = File(cacheDir, "test02_source.mp4")
            sourceVideo.copyTo(localSource, overwrite = true)

            val outputFile = File(cacheDir, "test02_hevc_output.mp4")
            if (outputFile.exists()) outputFile.delete()

            val engine = VideoProcessingEngine()
            var lastPercent = 0
            var maxFps = 0.0

            val result = engine.encode(
                sourceFile = localSource,
                outputFile = outputFile,
                profile = QualityProfile.BALANCED
            ) { progress ->
                lastPercent = progress.percent
                if (progress.speedFps > maxFps) maxFps = progress.speedFps
            }

            assertTrue("Encoding should succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
            val encResult = result.getOrThrow()
            assertTrue("Output file must exist", encResult.outputFile.exists())
            assertTrue("Output file size should be non-empty", encResult.outputFile.length() > 0)
            assertEquals(100, lastPercent)

            // Strict post-transcode validation
            val validation = ProcessingOutputValidator.validate(encResult.outputFile)
            assertTrue("Output must pass validation: ${validation.errorDetails}", validation.isValid)
            assertTrue("Output codec must be HEVC", validation.isHevc)
            assertTrue("Must have video track", validation.hasVideoTrack)
            assertTrue("Must be able to decode bitmap frame", validation.canDecodeBitmap)

            // Verify original source was never touched or modified
            assertEquals("Original file length must remain identical", originalLen, sourceVideo.length())

            localSource.delete()
            outputFile.delete()
        }
    }

    @Test
    fun test03_TranscodeVideoWithAudioPreservesAudioTrack() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val cacheDir = context.cacheDir
            val sourceVideo = findVideoWithAudioOnDevice()
            assertTrue("Video with audio must exist", sourceVideo.exists())

            val localSource = File(cacheDir, "test03_source_audio.mp4")
            sourceVideo.copyTo(localSource, overwrite = true)

            val outputFile = File(cacheDir, "test03_hevc_audio_output.mp4")
            if (outputFile.exists()) outputFile.delete()

            val engine = VideoProcessingEngine()
            val result = engine.encode(
                sourceFile = localSource,
                outputFile = outputFile,
                profile = QualityProfile.BALANCED
            ) { _ -> }

            assertTrue("Encoding with audio must succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
            val encResult = result.getOrThrow()
            assertTrue("Output file must exist", encResult.outputFile.exists())

            // Strict validation asserting audio track is preserved
            val validation = ProcessingOutputValidator.validate(encResult.outputFile, expectedHasAudio = true)
            assertTrue("Validation must pass with audio: ${validation.errorDetails}", validation.isValid)
            assertTrue("Video must be HEVC", validation.isHevc)
            assertTrue("Audio track must be retained", validation.hasAudioTrack)
            assertTrue("Must decode keyframe", validation.canDecodeBitmap)

            localSource.delete()
            outputFile.delete()
        }
    }

    @Test
    fun test04_VideoWithoutAudioHandlesGracefully() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val cacheDir = context.cacheDir
            val sourceVideo = findTestVideoOnDevice()
            assertTrue("Test video must exist", sourceVideo.exists())

            val localSource = File(cacheDir, "test04_source_no_audio.mp4")
            sourceVideo.copyTo(localSource, overwrite = true)

            val outputFile = File(cacheDir, "test04_hevc_no_audio_output.mp4")
            if (outputFile.exists()) outputFile.delete()

            val engine = VideoProcessingEngine()
            val result = engine.encode(
                sourceFile = localSource,
                outputFile = outputFile,
                profile = QualityProfile.FAST
            ) { _ -> }

            assertTrue("Encoding video-only stream must succeed", result.isSuccess)
            val validation = ProcessingOutputValidator.validate(outputFile, expectedHasAudio = false)
            assertTrue("Validation must pass for video-only stream: ${validation.errorDetails}", validation.isValid)
            assertTrue("Output is HEVC", validation.isHevc)
            assertFalse("Output has no audio track", validation.hasAudioTrack)

            localSource.delete()
            outputFile.delete()
        }
    }

    @Test
    fun test05_MalformedOrCorruptedInputFailsGracefully() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val cacheDir = context.cacheDir
            val corruptFile = File(cacheDir, "corrupted_media.mp4")
            FileOutputStream(corruptFile).use {
                it.write("THIS_IS_NOT_A_VALID_MP4_HEADER_123456789".toByteArray())
            }

            val outputFile = File(cacheDir, "corrupted_output.mp4")
            val engine = VideoProcessingEngine()
            val result = engine.encode(
                sourceFile = corruptFile,
                outputFile = outputFile,
                profile = QualityProfile.BALANCED
            ) { _ -> }

            assertFalse("Corrupted input must fail encoding", result.isSuccess)
            assertFalse("Output file must not exist if encoding failed", outputFile.exists())

            corruptFile.delete()
        }
    }

    @Test
    fun test06_CancellationCleansUpTemporaryFiles() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val cacheDir = context.cacheDir
            val sourceVideo = findVideoWithAudioOnDevice()

            val localSource = File(cacheDir, "test06_source_cancel.mp4")
            sourceVideo.copyTo(localSource, overwrite = true)

            val outputFile = File(cacheDir, "test06_output_cancel.mp4")
            val engine = VideoProcessingEngine()

            val job: Job = launch {
                try {
                    engine.encode(
                        sourceFile = localSource,
                        outputFile = outputFile,
                        profile = QualityProfile.BALANCED
                    ) { _ -> }
                } catch (_: CancellationException) {
                    // Expected on cancel
                }
            }

            // Cancel job shortly after start
            delay(150L)
            job.cancelAndJoin()

            // Verify output file does not remain as completed file
            assertFalse("Incomplete output file must not be left behind", outputFile.exists())

            localSource.delete()
        }
    }

    @Test
    fun test07_StorageProtectionRejectsIdenticalSourceAndOutput() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val cacheDir = context.cacheDir
            val dummyFile = File(cacheDir, "dummy_same_path.mp4")
            dummyFile.writeText("test")

            val engine = VideoProcessingEngine()
            val result = engine.encode(
                sourceFile = dummyFile,
                outputFile = dummyFile,
                profile = QualityProfile.BALANCED
            ) { _ -> }

            assertFalse("Engine must reject identical source and output paths", result.isSuccess)
            assertTrue(result.exceptionOrNull()?.message?.contains("same path") == true)

            dummyFile.delete()
        }
    }
}
