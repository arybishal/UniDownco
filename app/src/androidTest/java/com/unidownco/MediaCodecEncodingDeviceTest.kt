package com.unidownco

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.unidownco.core.database.DownloadEntity
import com.unidownco.core.database.ProcessingJobEntity
import com.unidownco.core.database.UniDownDatabase
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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

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

    @Test
    fun test08_SourceFileSha256ChecksumUntouched() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val sourceVideo = findVideoWithAudioOnDevice()
            val beforeSha = computeSha256(sourceVideo)

            val cacheDir = context.cacheDir
            val outputFile = File(cacheDir, "test08_checksum_out.mp4")

            val engine = VideoProcessingEngine(context = context)
            val result = engine.encode(
                sourceFile = sourceVideo,
                outputFile = outputFile,
                profile = QualityProfile.FAST
            ) { _ -> }

            assertTrue("Encoding should succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)

            val afterSha = computeSha256(sourceVideo)
            assertEquals("Source file SHA-256 must remain identical before and after transcode", beforeSha, afterSha)

            outputFile.delete()
        }
    }

    @Test
    fun test09_OrphanedTempFileCleanupOnDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storage = StorageManager(context)

        // Create dummy orphaned temp files
        val dummyTemp1 = File(storage.downloadsDir, "temp_hevc_simulated_crash_1.mp4")
        dummyTemp1.writeText("simulated partial output")
        val dummyTemp2 = File(context.cacheDir, "temp_hevc_simulated_crash_2.mp4")
        dummyTemp2.writeText("simulated partial output")

        assertTrue("Dummy temp 1 must exist", dummyTemp1.exists())
        assertTrue("Dummy temp 2 must exist", dummyTemp2.exists())

        val cleanedCount = storage.cleanOrphanedTempProcessingFiles()
        assertTrue("At least 2 orphaned files should be cleaned", cleanedCount >= 2)
        assertFalse("Dummy temp 1 must be deleted", dummyTemp1.exists())
        assertFalse("Dummy temp 2 must be deleted", dummyTemp2.exists())
    }

    @Test
    fun test10_ThermalStatusQueryOnDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        assertNotNull("PowerManager should be accessible", powerManager)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val thermal = powerManager!!.currentThermalStatus
            assertTrue("Thermal status should be in valid range (0..6)", thermal in 0..6)
            // Xiaomi SM6225 normal operating condition
            assertTrue("Device should not be in critical thermal shutdown during test", thermal < PowerManager.THERMAL_STATUS_SHUTDOWN)
        }
    }

    @Test
    fun test11_InterruptedJobReconciliationOnDevice() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val db = UniDownDatabase.getInstance(context)
            val dao = db.processingJobDao()
            val downloadDao = db.downloadDao()

            // Insert a parent DownloadEntity to satisfy Room Foreign Key constraint
            val testDownloadId = downloadDao.insertDownload(
                DownloadEntity(
                    url = "https://example.com/test_interrupted.mp4",
                    platform = "direct",
                    title = "Test Interrupted Download",
                    originalFilename = "test_interrupted.mp4",
                    downloadStatus = "COMPLETED"
                )
            )

            // Clean any pre-existing job for this test id
            dao.deleteByDownloadId(testDownloadId)

            val interruptedJob = ProcessingJobEntity(
                downloadId = testDownloadId,
                preset = "hdr10_enhanced",
                targetCodec = "hevc",
                targetHdrType = "SDR_BT709",
                targetFps = 30,
                stage = "ENCODING",
                progressPercent = 42,
                currentFps = 48.0,
                startedAt = System.currentTimeMillis()
            )
            val id = dao.insertJob(interruptedJob)

            val inserted = dao.getJobByDownloadId(testDownloadId)
            assertNotNull(inserted)
            assertEquals("ENCODING", inserted!!.stage)

            // Simulate startup reconciliation
            dao.failInterruptedJobs("Interrupted by application restart")

            val reconciled = dao.getJobByDownloadId(testDownloadId)
            assertNotNull(reconciled)
            assertEquals("FAILED", reconciled!!.stage)
            assertEquals("Interrupted by application restart", reconciled.errorMessage)

            dao.deleteByDownloadId(testDownloadId)
            downloadDao.deleteById(testDownloadId)
        }
    }

    @Test
    fun test12_VideoRotationAndOrientationMetadataIntegrity() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val sourceVideo = findVideoWithAudioOnDevice()
            val cacheDir = context.cacheDir
            val outputFile = File(cacheDir, "test12_rotation_out.mp4")
            if (outputFile.exists()) outputFile.delete()

            val engine = VideoProcessingEngine(context = context)
            val result = engine.encode(
                sourceFile = sourceVideo,
                outputFile = outputFile,
                profile = QualityProfile.BALANCED
            ) { _ -> }

            assertTrue("Encoding must succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)

            // Strict validation of orientation metadata, dimensions, and decodability
            val validation = ProcessingOutputValidator.validate(
                outputFile = outputFile,
                expectedHasAudio = true,
                expectedRotationDegrees = 0
            )

            assertTrue("Validation must pass with rotation verification: ${validation.errorDetails}", validation.isValid)
            assertTrue("Output codec must be HEVC", validation.isHevc)
            assertEquals("Rotation must be 0 degrees", 0, validation.rotationDegrees)
            assertTrue("Frame decode probe must succeed", validation.canDecodeBitmap)

            outputFile.delete()
        }
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(64 * 1024)
            var n: Int
            while (fis.read(buf).also { n = it } != -1) {
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
