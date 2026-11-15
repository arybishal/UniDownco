package com.unidownco.engine.muxer

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import com.unidownco.core.logging.UniLog
import java.io.File
import java.nio.ByteBuffer

/**
 * Thread-safe wrapper around Android [MediaMuxer] that coordinates
 * video and audio track registration, ensures tracks are registered before starting,
 * handles orientation hints, and writes sample data safely.
 */
class MediaMuxerController(
    val outputFile: File
) {
    private val tag = "MediaMuxerController"

    private var muxer: MediaMuxer? = null
    private var isStarted = false
    private var isReleased = false

    private var videoTrackIndex = -1
    private var audioTrackIndex = -1

    private var expectedTrackCount = 1
    private var registeredTrackCount = 0

    private val lock = Any()

    init {
        if (outputFile.exists()) {
            outputFile.delete()
        }
        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    /**
     * Sets the number of expected tracks (1 for video-only, 2 for video + audio).
     */
    fun setExpectedTracks(count: Int) {
        synchronized(lock) {
            expectedTrackCount = count.coerceIn(1, 2)
        }
    }

    /**
     * Sets display orientation hint (0, 90, 180, 270 degrees) before start.
     */
    fun setOrientationHint(degrees: Int) {
        synchronized(lock) {
            if (!isStarted && !isReleased) {
                try {
                    muxer?.setOrientationHint(degrees)
                    UniLog.d(tag, "Set orientation hint: $degrees°")
                } catch (e: Exception) {
                    UniLog.w(tag, "Failed to set orientation hint: ${e.message}")
                }
            }
        }
    }

    /**
     * Adds the video track with the encoder's output MediaFormat.
     */
    fun addVideoTrack(format: MediaFormat): Int {
        synchronized(lock) {
            if (isStarted) {
                throw IllegalStateException("Cannot add video track after muxer has started")
            }
            videoTrackIndex = muxer?.addTrack(format) ?: -1
            registeredTrackCount++
            UniLog.i(tag, "Registered video track index: $videoTrackIndex (registered $registeredTrackCount / $expectedTrackCount)")
            checkAndStartMuxerLocked()
            return videoTrackIndex
        }
    }

    /**
     * Adds the audio track with the extractor's audio MediaFormat.
     */
    fun addAudioTrack(format: MediaFormat): Int {
        synchronized(lock) {
            if (isStarted) {
                throw IllegalStateException("Cannot add audio track after muxer has started")
            }
            audioTrackIndex = muxer?.addTrack(format) ?: -1
            registeredTrackCount++
            UniLog.i(tag, "Registered audio track index: $audioTrackIndex (registered $registeredTrackCount / $expectedTrackCount)")
            checkAndStartMuxerLocked()
            return audioTrackIndex
        }
    }

    private fun checkAndStartMuxerLocked() {
        if (!isStarted && registeredTrackCount >= expectedTrackCount) {
            muxer?.start()
            isStarted = true
            UniLog.i(tag, "MediaMuxer started successfully with $registeredTrackCount tracks")
        }
    }

    fun isStarted(): Boolean {
        synchronized(lock) {
            return isStarted
        }
    }

    /**
     * Writes video sample data to MediaMuxer.
     */
    fun writeVideoSample(byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        synchronized(lock) {
            if (!isStarted || isReleased || videoTrackIndex == -1) return
            if (bufferInfo.size < 0) return

            try {
                muxer?.writeSampleData(videoTrackIndex, byteBuffer, bufferInfo)
            } catch (e: Exception) {
                UniLog.e(tag, "Error writing video sample at PTS ${bufferInfo.presentationTimeUs} us", e)
                throw e
            }
        }
    }

    /**
     * Writes audio sample data to MediaMuxer.
     */
    fun writeAudioSample(byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        synchronized(lock) {
            if (!isStarted || isReleased || audioTrackIndex == -1) return
            if (bufferInfo.size < 0) return

            try {
                muxer?.writeSampleData(audioTrackIndex, byteBuffer, bufferInfo)
            } catch (e: Exception) {
                UniLog.e(tag, "Error writing audio sample at PTS ${bufferInfo.presentationTimeUs} us", e)
                throw e
            }
        }
    }

    /**
     * Finishes muxing, stops and releases the underlying MediaMuxer safely.
     */
    fun finish(): Boolean {
        synchronized(lock) {
            if (isReleased) return true
            var success = false
            try {
                if (isStarted) {
                    muxer?.stop()
                    success = true
                    UniLog.i(tag, "MediaMuxer stopped successfully. File size: ${outputFile.length()} bytes")
                } else {
                    UniLog.w(tag, "MediaMuxer was never started before finish()")
                }
            } catch (e: Exception) {
                UniLog.e(tag, "Failed stopping MediaMuxer: ${e.message}", e)
            } finally {
                try {
                    muxer?.release()
                } catch (_: Exception) {}
                muxer = null
                isReleased = true
            }
            return success
        }
    }

    /**
     * Aborts muxing and releases resources without throwing.
     */
    fun releaseSilently() {
        synchronized(lock) {
            if (isReleased) return
            try {
                if (isStarted) {
                    muxer?.stop()
                }
            } catch (_: Exception) {}
            try {
                muxer?.release()
            } catch (_: Exception) {}
            muxer = null
            isReleased = true
        }
    }
}
