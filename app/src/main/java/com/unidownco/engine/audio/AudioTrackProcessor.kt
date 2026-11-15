package com.unidownco.engine.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.unidownco.core.logging.UniLog
import com.unidownco.engine.muxer.MediaMuxerController
import java.io.File
import java.nio.ByteBuffer

/**
 * Handles lossless audio elementary stream passthrough from a source media file
 * into [MediaMuxerController], keeping audio timestamps synchronized with video.
 */
class AudioTrackProcessor(
    private val sourceFile: File
) {
    private val tag = "AudioTrackProcessor"

    private var extractor: MediaExtractor? = null
    var audioTrackIndex: Int = -1
        private set
    var audioFormat: MediaFormat? = null
        private set

    val hasAudioTrack: Boolean
        get() = audioTrackIndex != -1 && audioFormat != null

    var lastAudioPtsUs: Long = 0L
        private set
    var isAudioEosReached: Boolean = false
        private set
    var audioSampleCount: Long = 0L
        private set

    private var audioBuffer: ByteBuffer? = null
    private val bufferInfo = MediaCodec.BufferInfo()

    init {
        probeAudioTrack()
    }

    private fun probeAudioTrack() {
        val ext = MediaExtractor()
        try {
            ext.setDataSource(sourceFile.absolutePath)
            for (i in 0 until ext.trackCount) {
                val format = ext.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    ext.selectTrack(i)
                    break
                }
            }

            if (audioTrackIndex != -1 && audioFormat != null) {
                val maxInputSize = if (audioFormat!!.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    audioFormat!!.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(128 * 1024)
                } else {
                    128 * 1024
                }
                audioBuffer = ByteBuffer.allocateDirect(maxInputSize)
                extractor = ext
                val mime = audioFormat!!.getString(MediaFormat.KEY_MIME)
                val channels = if (audioFormat!!.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) audioFormat!!.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
                val sampleRate = if (audioFormat!!.containsKey(MediaFormat.KEY_SAMPLE_RATE)) audioFormat!!.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
                UniLog.i(tag, "Audio track found: $mime ($channels ch, $sampleRate Hz)")
            } else {
                UniLog.i(tag, "No audio track found in ${sourceFile.name} (video-only stream)")
                ext.release()
            }
        } catch (e: Exception) {
            UniLog.w(tag, "Failed to inspect audio tracks in ${sourceFile.name}: ${e.message}")
            try { ext.release() } catch (_: Exception) {}
        }
    }

    /**
     * Registers the audio track in [muxer]. Must be called before [muxer] starts.
     */
    fun registerWithMuxer(muxer: MediaMuxerController): Int {
        val format = audioFormat ?: return -1
        return muxer.addAudioTrack(format)
    }

    /**
     * Transfers audio samples from source into [muxer] up to [maxPtsUs].
     * Keeps audio and video timestamps interleaved, preventing MediaMuxer buffer overruns.
     * @return count of audio samples transferred in this call.
     */
    fun transferSamplesUpTo(maxPtsUs: Long, muxer: MediaMuxerController): Int {
        val ext = extractor ?: return 0
        val buf = audioBuffer ?: return 0
        if (!hasAudioTrack || isAudioEosReached || !muxer.isStarted()) return 0

        var transferred = 0

        while (true) {
            val sampleTime = ext.sampleTime
            if (sampleTime < 0) {
                isAudioEosReached = true
                UniLog.i(tag, "Audio passthrough reached End-Of-Stream (EOS). Total samples: $audioSampleCount")
                break
            }

            // If audio PTS has caught up with or is ahead of target video PTS + 500ms, pause transfer
            if (sampleTime > maxPtsUs + 500_000L) {
                break
            }

            buf.clear()
            val sampleSize = ext.readSampleData(buf, 0)
            if (sampleSize < 0) {
                isAudioEosReached = true
                break
            }

            bufferInfo.offset = 0
            bufferInfo.size = sampleSize
            bufferInfo.presentationTimeUs = sampleTime
            bufferInfo.flags = ext.sampleFlags

            muxer.writeAudioSample(buf, bufferInfo)

            lastAudioPtsUs = sampleTime
            audioSampleCount++
            transferred++

            ext.advance()
        }

        return transferred
    }

    /**
     * Transfers all remaining audio samples until EOS.
     */
    fun transferRemaining(muxer: MediaMuxerController): Int {
        val ext = extractor ?: return 0
        val buf = audioBuffer ?: return 0
        if (!hasAudioTrack || isAudioEosReached || !muxer.isStarted()) return 0

        var transferred = 0

        while (true) {
            val sampleTime = ext.sampleTime
            if (sampleTime < 0) {
                isAudioEosReached = true
                break
            }

            buf.clear()
            val sampleSize = ext.readSampleData(buf, 0)
            if (sampleSize < 0) {
                isAudioEosReached = true
                break
            }

            bufferInfo.offset = 0
            bufferInfo.size = sampleSize
            bufferInfo.presentationTimeUs = sampleTime
            bufferInfo.flags = ext.sampleFlags

            muxer.writeAudioSample(buf, bufferInfo)

            lastAudioPtsUs = sampleTime
            audioSampleCount++
            transferred++

            ext.advance()
        }

        return transferred
    }

    /**
     * Safely releases audio extractor.
     */
    fun release() {
        try {
            extractor?.release()
        } catch (_: Exception) {}
        extractor = null
        audioBuffer = null
        UniLog.i(tag, "AudioTrackProcessor released")
    }
}
