package com.unidownco

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.unidownco.core.storage.StorageManager
import com.unidownco.engine.downloader.MediaStreamMuxer
import com.unidownco.engine.verifier.AndroidMediaVerifier
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
class MediaStreamMuxerDeviceTest {

    @Test
    fun testOnDevice_StreamMuxerAndMediaVerifier() {
        runBlocking {
            val appContext = InstrumentationRegistry.getInstrumentation().targetContext
            val cacheDir = appContext.cacheDir

            // Locate existing downloaded test video on device
            val storage = StorageManager(appContext)
            val existingVideo = listOf(
                File(storage.originalsDir, "bottle.mp4"),
                File(storage.originalsDir, "zoo.mp4"),
                File("/sdcard/Android/data/com.unidownco/files/Movies/originals/bottle.mp4"),
                File("/sdcard/Android/data/com.unidownco.debug/files/Movies/originals/bottle.mp4"),
                File("/data/local/tmp/bottle.mp4")
            ).firstOrNull { it.exists() && it.length() > 0 } ?: throw IllegalStateException("No test video found")

        val videoSource = File(cacheDir, "test_video_track.mp4")
        val audioSource = File(cacheDir, "test_audio_track.mp4")
        val muxedOutput = File(cacheDir, "test_muxed_output.mp4")

        existingVideo.copyTo(videoSource, overwrite = true)
        existingVideo.copyTo(audioSource, overwrite = true)

        // 1. Losslessly mux video and audio tracks
        val muxResult = MediaStreamMuxer.mux(
            videoFile = videoSource,
            audioFile = audioSource,
            outputFile = muxedOutput
        )

        assertTrue("Muxing should succeed on Android runtime", muxResult.isSuccess)
        assertTrue("Output file must exist", muxedOutput.exists())
        assertTrue("Output file size should be > 500KB", muxedOutput.length() > 500_000)

        // 2. Verify media properties via AndroidMediaVerifier
        val verifier = AndroidMediaVerifier()
        val report = verifier.verifyMedia(muxedOutput)

        assertTrue("Verified media must be valid", report.isValid)
        assertTrue("Container must be verified", report.isFileIntegrityVerified)
        assertNotNull("Resolution should be detected", report.resolution)

        // Clean up
        videoSource.delete()
        audioSource.delete()
        muxedOutput.delete()
        }
    }

    @Test
    fun testOnDevice_CorruptedFileVerificationFails() {
        runBlocking {
            val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val corruptFile = File(appContext.cacheDir, "corrupted_test.mp4")

        FileOutputStream(corruptFile).use { fos ->
            fos.write("<!DOCTYPE html><html><body>Error</body></html>".toByteArray())
        }

        val verifier = AndroidMediaVerifier()
        val report = verifier.verifyMedia(corruptFile)

        assertFalse("Corrupted file must not be valid", report.isValid)
        corruptFile.delete()
        }
    }
}
