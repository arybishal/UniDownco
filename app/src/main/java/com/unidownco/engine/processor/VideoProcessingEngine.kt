package com.unidownco.engine.processor

import android.media.MediaExtractor
import android.media.MediaFormat
import com.unidownco.core.logging.UniLog
import com.unidownco.engine.audio.AudioTrackProcessor
import com.unidownco.engine.decoder.MediaCodecVideoDecoder
import com.unidownco.engine.encoder.EncoderBackend
import com.unidownco.engine.encoder.EncodingProgress
import com.unidownco.engine.encoder.EncodingResult
import com.unidownco.engine.encoder.MediaCodecVideoEncoder
import com.unidownco.engine.encoder.QualityProfile
import com.unidownco.engine.encoder.VideoEncoder
import com.unidownco.engine.hardware.HardwareCapabilities
import com.unidownco.engine.hardware.HardwareCapabilityRegistry
import com.unidownco.engine.muxer.MediaMuxerController
import com.unidownco.engine.verifier.ProcessingOutputValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Production-quality Android MediaCodec hardware-accelerated HEVC video processing engine.
 * Decodes source streams, encodes to HEVC Main (8-bit) or Main10 (10-bit where genuinely supported),
 * passes lossless audio tracks through, enforces backpressure, and performs post-processing validation.
 */
class VideoProcessingEngine(
    private val capabilities: HardwareCapabilities = HardwareCapabilityRegistry.getCapabilities()
) : VideoEncoder {

    private val tag = "VideoProcessingEngine"

    override suspend fun encode(
        sourceFile: File,
        outputFile: File,
        profile: QualityProfile,
        onProgress: (EncodingProgress) -> Unit
    ): Result<EncodingResult> = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            return@withContext Result.failure(
                IllegalArgumentException("Source file does not exist or is empty: ${sourceFile.absolutePath}")
            )
        }

        // Safeguard: Never overwrite source file
        if (sourceFile.canonicalPath == outputFile.canonicalPath) {
            return@withContext Result.failure(
                IllegalArgumentException("Output file cannot have the same path as source: ${sourceFile.canonicalPath}")
            )
        }

        UniLog.i(tag, "Starting HEVC hardware transcode: ${sourceFile.name} -> ${outputFile.name}")

        var tempOutputFile: File? = null
        var videoExtractor: MediaExtractor? = null
        var audioProcessor: AudioTrackProcessor? = null
        var muxerController: MediaMuxerController? = null
        var encoder: MediaCodecVideoEncoder? = null
        var decoder: MediaCodecVideoDecoder? = null

        val startTimeMs = System.currentTimeMillis()

        try {
            // -----------------------------------------------------------------
            // STAGE 1: PREPARING
            // -----------------------------------------------------------------
            onProgress(
                EncodingProgress(
                    stage = "PREPARING",
                    percent = 0,
                    speedFps = 0.0,
                    backend = EncoderBackend.HARDWARE_MEDIACODEC
                )
            )

            val vExt = MediaExtractor()
            vExt.setDataSource(sourceFile.absolutePath)
            videoExtractor = vExt

            var videoTrackIndex = -1
            var sourceVideoFormat: MediaFormat? = null

            for (i in 0 until vExt.trackCount) {
                val format = vExt.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i
                    sourceVideoFormat = format
                    break
                }
            }

            if (videoTrackIndex == -1 || sourceVideoFormat == null) {
                return@withContext Result.failure(
                    IllegalStateException("No video track found in source file: ${sourceFile.name}")
                )
            }

            val sourceWidth = sourceVideoFormat.getInteger(MediaFormat.KEY_WIDTH)
            val sourceHeight = sourceVideoFormat.getInteger(MediaFormat.KEY_HEIGHT)
            val sourceFps = if (sourceVideoFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                sourceVideoFormat.getInteger(MediaFormat.KEY_FRAME_RATE).coerceIn(15, 60)
            } else {
                30
            }
            val rotationDegrees = if (sourceVideoFormat.containsKey(MediaFormat.KEY_ROTATION)) {
                sourceVideoFormat.getInteger(MediaFormat.KEY_ROTATION)
            } else {
                0
            }

            val durationUs = if (sourceVideoFormat.containsKey(MediaFormat.KEY_DURATION)) {
                sourceVideoFormat.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(1_000_000L)
            } else {
                10_000_000L
            }

            // Align dimensions to even numbers
            val targetWidth = (sourceWidth / 2) * 2
            val targetHeight = (sourceHeight / 2) * 2

            // Guard against hardware encoder resolution limits
            if (targetWidth > capabilities.maxWidth || targetHeight > capabilities.maxHeight) {
                return@withContext Result.failure(
                    IllegalStateException(
                        "Source resolution (${sourceWidth}x${sourceHeight}) exceeds device hardware encoder limits " +
                        "(max ${capabilities.maxWidth}x${capabilities.maxHeight} on ${capabilities.deviceModel}). " +
                        "Use Direct Pass-through to preserve original stream."
                    )
                )
            }

            // Probe audio track
            audioProcessor = AudioTrackProcessor(sourceFile)

            // Setup temporary output file to ensure original is protected
            val parentDir = outputFile.parentFile ?: sourceFile.parentFile ?: File(".")
            if (!parentDir.exists()) parentDir.mkdirs()
            tempOutputFile = File(parentDir, "temp_hevc_${System.currentTimeMillis()}_${outputFile.name}")

            val muxer = MediaMuxerController(tempOutputFile)
            muxerController = muxer
            muxer.setExpectedTracks(if (audioProcessor.hasAudioTrack) 2 else 1)
            muxer.setOrientationHint(rotationDegrees)

            if (audioProcessor.hasAudioTrack) {
                audioProcessor.registerWithMuxer(muxer)
            }

            // Initialize hardware encoder with surface input
            val enc = MediaCodecVideoEncoder(
                width = targetWidth,
                height = targetHeight,
                fps = sourceFps,
                qualityProfile = profile,
                capabilities = capabilities
            )
            encoder = enc

            val inputSurface = enc.inputSurface
                ?: throw IllegalStateException("Encoder input surface was null")

            // Initialize decoder with encoder input surface
            val dec = MediaCodecVideoDecoder(
                extractor = vExt,
                videoTrackIndex = videoTrackIndex,
                sourceFormat = sourceVideoFormat,
                outputSurface = inputSurface
            )
            decoder = dec

            // -----------------------------------------------------------------
            // STAGE 2: ENCODING
            // -----------------------------------------------------------------
            onProgress(
                EncodingProgress(
                    stage = "ENCODING",
                    percent = 5,
                    speedFps = 0.0,
                    backend = EncoderBackend.HARDWARE_MEDIACODEC
                )
            )

            var lastProgressUpdate = System.currentTimeMillis()
            var lastFpsCalcTime = System.currentTimeMillis()
            var framesSinceLastFps = 0L
            var currentFps = 0.0
            var isEncoderEosSignaled = false

            while (!enc.isEncoderEosReached) {
                currentCoroutineContext().ensureActive()

                var madeProgress = false

                // 1. Feed decoder input from extractor
                if (!dec.isInputEos) {
                    val fed = dec.feedInput(timeoutUs = 2_000L)
                    if (fed) madeProgress = true
                }

                // 2. Drain decoder output to encoder surface
                if (!dec.isOutputEos) {
                    val rendered = dec.drainOutput(timeoutUs = 2_000L)
                    if (rendered) {
                        framesSinceLastFps++
                        madeProgress = true
                    }
                }

                // 3. Signal encoder EOS once decoder output completes
                if (dec.isOutputEos && !isEncoderEosSignaled) {
                    enc.signalEndOfInputStream()
                    isEncoderEosSignaled = true
                    madeProgress = true
                }

                // 4. Drain encoder output buffers into MediaMuxer
                val drained = enc.drainEncoder(muxer, timeoutUs = 5_000L)
                if (drained) madeProgress = true

                // 5. Transfer audio in lockstep with video PTS
                if (audioProcessor.hasAudioTrack && muxer.isStarted()) {
                    val transferredAudio = audioProcessor.transferSamplesUpTo(dec.lastDecodedPtsUs, muxer)
                    if (transferredAudio > 0) madeProgress = true
                }

                // 6. Real-time metrics & progress calculation
                val now = System.currentTimeMillis()
                if (now - lastFpsCalcTime >= 1000L) {
                    val elapsedSec = (now - lastFpsCalcTime) / 1000.0
                    currentFps = if (elapsedSec > 0) (framesSinceLastFps / elapsedSec) else 0.0
                    framesSinceLastFps = 0L
                    lastFpsCalcTime = now
                }

                if (now - lastProgressUpdate >= 300L) {
                    val progressPercent = ((dec.lastDecodedPtsUs.toDouble() / durationUs.toDouble()) * 90.0)
                        .toInt().coerceIn(5, 95)
                    onProgress(
                        EncodingProgress(
                            stage = "ENCODING",
                            percent = progressPercent,
                            speedFps = currentFps,
                            backend = EncoderBackend.HARDWARE_MEDIACODEC
                        )
                    )
                    lastProgressUpdate = now
                }

                // Brief backpressure sleep if no buffers were ready in this cycle
                if (!madeProgress) {
                    Thread.sleep(2)
                }
            }

            // -----------------------------------------------------------------
            // STAGE 3: MUXING
            // -----------------------------------------------------------------
            onProgress(
                EncodingProgress(
                    stage = "MUXING",
                    percent = 95,
                    speedFps = currentFps,
                    backend = EncoderBackend.HARDWARE_MEDIACODEC
                )
            )

            // Transfer all remaining audio samples up to EOS
            if (audioProcessor.hasAudioTrack && muxer.isStarted()) {
                audioProcessor.transferRemaining(muxer)
            }

            val muxerStopped = muxer.finish()
            if (!muxerStopped) {
                throw IllegalStateException("MediaMuxer failed to finalize output file")
            }

            // -----------------------------------------------------------------
            // STAGE 4: VALIDATING
            // -----------------------------------------------------------------
            onProgress(
                EncodingProgress(
                    stage = "VALIDATING",
                    percent = 98,
                    speedFps = currentFps,
                    backend = EncoderBackend.HARDWARE_MEDIACODEC
                )
            )

            val validation = ProcessingOutputValidator.validate(
                outputFile = tempOutputFile,
                expectedHasAudio = audioProcessor.hasAudioTrack,
                expectedDurationSeconds = durationUs / 1_000_000L
            )

            if (!validation.isValid) {
                tempOutputFile.delete()
                return@withContext Result.failure(
                    IllegalStateException("Output validation failed: ${validation.errorDetails}")
                )
            }

            // -----------------------------------------------------------------
            // STAGE 5: COMPLETED - Atomic Rename to Destination
            // -----------------------------------------------------------------
            if (outputFile.exists()) {
                outputFile.delete()
            }

            val moved = tempOutputFile.renameTo(outputFile)
            if (!moved) {
                copyAndDelete(tempOutputFile, outputFile)
            }

            val durationSeconds = validation.durationSeconds
            val finalSizeBytes = outputFile.length()

            onProgress(
                EncodingProgress(
                    stage = "COMPLETED",
                    percent = 100,
                    speedFps = currentFps,
                    backend = EncoderBackend.HARDWARE_MEDIACODEC
                )
            )

            val elapsedTotalSec = (System.currentTimeMillis() - startTimeMs) / 1000.0
            UniLog.i(
                tag,
                "Transcode COMPLETED in ${elapsedTotalSec}s: ${outputFile.name} " +
                "(${finalSizeBytes / 1024} KB, ${validation.width}x${validation.height}, " +
                "${enc.actualProfile}, avg FPS=${(enc.encodedFrameCount / elapsedTotalSec.coerceAtLeast(0.1)).toInt()})"
            )

            Result.success(
                EncodingResult(
                    outputFile = outputFile,
                    durationSeconds = durationSeconds,
                    finalSizeBytes = finalSizeBytes,
                    backendUsed = EncoderBackend.HARDWARE_MEDIACODEC
                )
            )
        } catch (ce: CancellationException) {
            UniLog.w(tag, "Transcode job was cancelled: ${sourceFile.name}")
            tempOutputFile?.delete()
            throw ce
        } catch (e: Exception) {
            UniLog.e(tag, "Transcode job failed: ${e.message}", e)
            tempOutputFile?.delete()
            Result.failure(e)
        } finally {
            try { decoder?.release() } catch (_: Exception) {}
            try { encoder?.release() } catch (_: Exception) {}
            try { audioProcessor?.release() } catch (_: Exception) {}
            try { muxerController?.releaseSilently() } catch (_: Exception) {}
            try { videoExtractor?.release() } catch (_: Exception) {}
        }
    }

    private fun copyAndDelete(source: File, destination: File) {
        FileInputStream(source).use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        }
        source.delete()
    }
}
