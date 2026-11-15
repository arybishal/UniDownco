package com.unidownco.engine.encoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaFormat
import android.view.Surface
import com.unidownco.core.logging.UniLog
import com.unidownco.engine.hardware.HardwareCapabilities
import com.unidownco.engine.hardware.HardwareCapabilityRegistry
import com.unidownco.engine.muxer.MediaMuxerController
import java.nio.ByteBuffer

/**
 * Production-grade hardware HEVC (H.265) video encoder using Android MediaCodec.
 * Supports Surface input (COLOR_FormatSurface), dynamic bitrate calculation,
 * hardware capability probing via [HardwareCapabilityRegistry], and safe EOS drain loops.
 */
class MediaCodecVideoEncoder(
    private val width: Int,
    private val height: Int,
    private val fps: Int,
    private val qualityProfile: QualityProfile = QualityProfile.BALANCED,
    private val capabilities: HardwareCapabilities = HardwareCapabilityRegistry.getCapabilities()
) {
    private val tag = "MediaCodecVideoEncoder"

    private var encoder: MediaCodec? = null
    var inputSurface: Surface? = null
        private set

    var actualEncoderName: String = "unknown"
        private set
    var actualProfile: String = "Main (8-bit)"
        private set
    var targetBitrate: Int = 0
        private set

    private var isStarted = false
    private var isReleased = false
    var encodedFrameCount: Long = 0L
        private set
    var isEncoderEosReached: Boolean = false
        private set

    private val bufferInfo = MediaCodec.BufferInfo()

    init {
        initEncoder()
    }

    private fun initEncoder() {
        val mime = MediaFormat.MIMETYPE_VIDEO_HEVC

        // Align dimensions to even integers required by hardware encoders
        val alignedWidth = (width / 2) * 2
        val alignedHeight = (height / 2) * 2

        // Calculate bitrate based on resolution, frame rate, and quality profile
        targetBitrate = calculateBitrate(alignedWidth, alignedHeight, fps, qualityProfile)

        val format = MediaFormat.createVideoFormat(mime, alignedWidth, alignedHeight).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, targetBitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // 1 second keyframe interval

            // Bitrate mode: prefer VBR
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)

            // Select Profile: strictly gated by HardwareCapabilityRegistry
            if (capabilities.supportsHardwareMain10) {
                setInteger(MediaFormat.KEY_PROFILE, CodecProfileLevel.HEVCProfileMain10)
                actualProfile = "Main10 (10-bit)"
            } else {
                setInteger(MediaFormat.KEY_PROFILE, CodecProfileLevel.HEVCProfileMain)
                actualProfile = "Main (8-bit)"
            }
        }

        val selectedEncoderName = capabilities.hardwareEncoderName
        val codec = if (!selectedEncoderName.isNullOrBlank()) {
            try {
                MediaCodec.createByCodecName(selectedEncoderName).also {
                    actualEncoderName = selectedEncoderName
                }
            } catch (e: Exception) {
                UniLog.w(tag, "Failed initializing hardware codec by name $selectedEncoderName, falling back to default: ${e.message}")
                MediaCodec.createEncoderByType(mime).also {
                    actualEncoderName = it.name
                }
            }
        } else {
            MediaCodec.createEncoderByType(mime).also {
                actualEncoderName = it.name
            }
        }

        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = codec.createInputSurface()
            codec.start()
            encoder = codec
            isStarted = true
            UniLog.i(tag, "HEVC Encoder started: $actualEncoderName, ${alignedWidth}x${alignedHeight} @ ${fps}fps, $actualProfile, ${targetBitrate / 1000} kbps")
        } catch (e: Exception) {
            codec.release()
            throw IllegalStateException("Failed to configure and start HEVC encoder: ${e.message}", e)
        }
    }

    /**
     * Drains available encoded output buffers and writes them to [muxer].
     * @param timeoutUs Dequeue timeout in microseconds.
     * @return true if output buffers were processed, false if timed out (TRY_AGAIN_LATER).
     */
    fun drainEncoder(muxer: MediaMuxerController, timeoutUs: Long = 10_000L): Boolean {
        val codec = encoder ?: return false
        if (!isStarted || isReleased) return false

        var processedAny = false

        while (true) {
            val status = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)

            when {
                status == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    break
                }
                status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val newFormat = codec.outputFormat
                    UniLog.i(tag, "Encoder output format changed: $newFormat")
                    muxer.addVideoTrack(newFormat)
                    processedAny = true
                }
                status >= 0 -> {
                    processedAny = true
                    val outputBuffer = codec.getOutputBuffer(status)
                        ?: throw IllegalStateException("Encoder output buffer $status was null")

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        // Codec config data (SPS/PPS/VPS) is already passed in newFormat, ignore data size
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size > 0 && muxer.isStarted()) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeVideoSample(outputBuffer, bufferInfo)
                        encodedFrameCount++
                    }

                    codec.releaseOutputBuffer(status, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        UniLog.i(tag, "Encoder output reached End-Of-Stream (EOS). Total encoded frames: $encodedFrameCount")
                        isEncoderEosReached = true
                        break
                    }
                }
            }
        }

        return processedAny
    }

    /**
     * Signals End-of-Stream to the encoder input surface.
     */
    fun signalEndOfInputStream() {
        if (!isStarted || isReleased) return
        try {
            encoder?.signalEndOfInputStream()
            UniLog.i(tag, "Signaled End-Of-Stream to encoder input surface")
        } catch (e: Exception) {
            UniLog.w(tag, "Error signaling End-of-Stream to encoder: ${e.message}")
        }
    }

    /**
     * Safely releases the encoder and its input surface.
     */
    fun release() {
        if (isReleased) return
        isReleased = true
        isStarted = false

        try {
            encoder?.stop()
        } catch (_: Exception) {}

        try {
            encoder?.release()
        } catch (_: Exception) {}
        encoder = null

        try {
            inputSurface?.release()
        } catch (_: Exception) {}
        inputSurface = null

        UniLog.i(tag, "HEVC Encoder released")
    }

    companion object {
        /**
         * Calculates target video bitrate for HEVC encoding.
         */
        fun calculateBitrate(width: Int, height: Int, fps: Int, profile: QualityProfile): Int {
            val pixelsPerSecond = width.toLong() * height.toLong() * fps.toLong()

            // HEVC is ~50% more efficient than H.264. Bits-per-pixel factors:
            val bpp = when (profile) {
                QualityProfile.MAXIMUM_QUALITY -> 0.100 // High quality archival
                QualityProfile.BALANCED -> 0.070        // Optimal size/quality tradeoff
                QualityProfile.FAST -> 0.045            // Fast lightweight transcode
            }

            val calculated = (pixelsPerSecond * bpp).toInt()

            // Bound minimum and maximum bitrates based on resolution tiers
            return when {
                width >= 3840 || height >= 2160 -> calculated.coerceIn(8_000_000, 25_000_000)
                width >= 1920 || height >= 1080 -> calculated.coerceIn(2_500_000, 8_000_000)
                width >= 1280 || height >= 720  -> calculated.coerceIn(1_200_000, 4_500_000)
                else -> calculated.coerceIn(500_000, 2_000_000)
            }
        }
    }
}
