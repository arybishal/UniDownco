package com.unidownco.engine.decoder

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.view.Surface
import com.unidownco.core.logging.UniLog

/**
 * Hardware-accelerated video decoder using Android MediaCodec.
 * Decodes frames directly onto an input [Surface] (typically an encoder input surface)
 * with precise nanosecond timestamp synchronization.
 */
class MediaCodecVideoDecoder(
    private val extractor: MediaExtractor,
    private val videoTrackIndex: Int,
    private val sourceFormat: MediaFormat,
    private val outputSurface: Surface
) {
    private val tag = "MediaCodecVideoDecoder"

    private var decoder: MediaCodec? = null
    private var isStarted = false
    private var isReleased = false

    var isInputEos: Boolean = false
        private set
    var isOutputEos: Boolean = false
        private set

    var decodedFrameCount: Long = 0L
        private set
    var lastDecodedPtsUs: Long = 0L
        private set

    private val bufferInfo = MediaCodec.BufferInfo()

    init {
        initDecoder()
    }

    private fun initDecoder() {
        val mime = sourceFormat.getString(MediaFormat.KEY_MIME)
            ?: throw IllegalArgumentException("Source video track format has no MIME type")

        extractor.selectTrack(videoTrackIndex)

        val codec = MediaCodec.createDecoderByType(mime)
        try {
            codec.configure(sourceFormat, outputSurface, null, 0)
            codec.start()
            decoder = codec
            isStarted = true
            UniLog.i(tag, "Video decoder started for $mime: ${codec.name}, outputting directly to Surface")
        } catch (e: Exception) {
            codec.release()
            throw IllegalStateException("Failed to configure and start video decoder: ${e.message}", e)
        }
    }

    /**
     * Feeds compressed video packets from [extractor] into decoder input buffers.
     * @return true if input packet was read, false if full or EOF reached.
     */
    fun feedInput(timeoutUs: Long = 5_000L): Boolean {
        val codec = decoder ?: return false
        if (!isStarted || isReleased || isInputEos) return false

        val inputIndex = codec.dequeueInputBuffer(timeoutUs)
        if (inputIndex < 0) return false

        val inputBuffer = codec.getInputBuffer(inputIndex)
            ?: throw IllegalStateException("Decoder input buffer $inputIndex was null")

        val sampleSize = extractor.readSampleData(inputBuffer, 0)

        if (sampleSize < 0) {
            // End of stream in extractor
            codec.queueInputBuffer(
                inputIndex,
                0,
                0,
                0L,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM
            )
            isInputEos = true
            UniLog.i(tag, "Decoder input reached End-Of-Stream (EOS)")
            return false
        } else {
            val pts = extractor.sampleTime
            val flags = extractor.sampleFlags
            codec.queueInputBuffer(inputIndex, 0, sampleSize, pts, flags)
            extractor.advance()
            return true
        }
    }

    /**
     * Drains available decoded frames and renders them to [outputSurface]
     * with exact nanosecond presentation timestamps.
     * @return true if a frame was rendered, false if TRY_AGAIN_LATER.
     */
    fun drainOutput(timeoutUs: Long = 5_000L): Boolean {
        val codec = decoder ?: return false
        if (!isStarted || isReleased || isOutputEos) return false

        val status = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)

        when {
            status == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                return false
            }
            status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                UniLog.i(tag, "Decoder output format changed: ${codec.outputFormat}")
                return true
            }
            status >= 0 -> {
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    UniLog.i(tag, "Decoder output reached End-Of-Stream (EOS). Total decoded: $decodedFrameCount")
                    isOutputEos = true
                    codec.releaseOutputBuffer(status, false)
                    return false
                }

                // Render frame directly to Surface with exact nanosecond presentation timestamp
                val presentationTimeNs = bufferInfo.presentationTimeUs * 1000L
                lastDecodedPtsUs = bufferInfo.presentationTimeUs
                decodedFrameCount++
                codec.releaseOutputBuffer(status, presentationTimeNs)
                return true
            }
            else -> return false
        }
    }

    /**
     * Releases decoder safely.
     */
    fun release() {
        if (isReleased) return
        isReleased = true
        isStarted = false

        try {
            decoder?.stop()
        } catch (_: Exception) {}

        try {
            decoder?.release()
        } catch (_: Exception) {}
        decoder = null

        UniLog.i(tag, "Video decoder released")
    }
}
