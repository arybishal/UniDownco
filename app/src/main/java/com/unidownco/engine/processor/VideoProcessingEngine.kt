package com.unidownco.engine.processor

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.os.PowerManager
import android.view.Surface
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
import com.unidownco.engine.hdr.HdrMasteringDisplay
import com.unidownco.engine.hdr.HdrStaticMetadata
import com.unidownco.engine.hdr.MetadataProvenance
import com.unidownco.engine.hdr.TargetHdrType
import com.unidownco.engine.hdr.gpu.HdrRenderCore
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
import java.security.MessageDigest

/**
 * Production-quality Android MediaCodec hardware-accelerated HEVC video processing engine.
 * Decodes source streams, encodes to HEVC Main (8-bit) or Main10 (10-bit where genuinely supported),
 * executes GPU-accelerated SDR-to-HDR10 color expansion via [HdrRenderCore], passes lossless audio
 * tracks through, enforces backpressure, and performs post-processing validation.
 */
class VideoProcessingEngine(
    private val capabilities: HardwareCapabilities = HardwareCapabilityRegistry.getCapabilities(),
    private val context: Context? = null
) : VideoEncoder {

    private val tag = "VideoProcessingEngine"

    override suspend fun encode(
        sourceFile: File,
        outputFile: File,
        profile: QualityProfile,
        onProgress: (EncodingProgress) -> Unit
    ): Result<EncodingResult> = encode(
        sourceFile = sourceFile,
        outputFile = outputFile,
        profile = profile,
        targetHdrType = TargetHdrType.SDR_PASSTHROUGH,
        hdrStaticMetadata = null,
        onProgress = onProgress
    )

    suspend fun encode(
        sourceFile: File,
        outputFile: File,
        profile: QualityProfile = QualityProfile.BALANCED,
        targetHdrType: TargetHdrType = TargetHdrType.SDR_PASSTHROUGH,
        hdrStaticMetadata: HdrStaticMetadata? = null,
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

        val sourceChecksumBefore = computeSha256(sourceFile)
        UniLog.i(tag, "Starting HEVC hardware transcode: ${sourceFile.name} (SHA-256: ${sourceChecksumBefore.take(12)}...) -> ${outputFile.name}")

        var tempOutputFile: File? = null
        var videoExtractor: MediaExtractor? = null
        var audioProcessor: AudioTrackProcessor? = null
        var muxerController: MediaMuxerController? = null
        var renderCore: HdrRenderCore? = null
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
            if (audioProcessor.hasAudioTrack && !audioProcessor.isFormatSupportedForMp4) {
                return@withContext Result.failure(
                    IllegalStateException(audioProcessor.unsupportedFormatReason ?: "Unsupported audio format for MP4 muxing")
                )
            }

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

            // Detect if source is already genuine HDR (BT.2020 + ST 2084 PQ or HLG)
            var isSourceAlreadyHdr = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val colorStd = if (sourceVideoFormat.containsKey(MediaFormat.KEY_COLOR_STANDARD)) {
                    sourceVideoFormat.getInteger(MediaFormat.KEY_COLOR_STANDARD)
                } else -1
                val colorTrans = if (sourceVideoFormat.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) {
                    sourceVideoFormat.getInteger(MediaFormat.KEY_COLOR_TRANSFER)
                } else -1

                if (colorStd == MediaFormat.COLOR_STANDARD_BT2020 &&
                    (colorTrans == MediaFormat.COLOR_TRANSFER_ST2084 || colorTrans == MediaFormat.COLOR_TRANSFER_HLG)) {
                    isSourceAlreadyHdr = true
                }
            }

            // Determine actual target HDR type based on hardware capability and source
            val actualTargetHdrType = when {
                targetHdrType == TargetHdrType.SDR_TO_HDR10 && !capabilities.supportsHardwareMain10 -> {
                    UniLog.w(
                        tag,
                        "HDR10 requested, but hardware encoder on ${capabilities.deviceModel} lacks HEVC Main10 support. " +
                        "Adapting pipeline to 8-bit HEVC Main SDR mode."
                    )
                    TargetHdrType.SDR_PASSTHROUGH
                }
                targetHdrType == TargetHdrType.SDR_TO_HDR10 && isSourceAlreadyHdr -> {
                    UniLog.i(tag, "Source is already HDR. Preserving source HDR characteristics without inverse tone mapping.")
                    TargetHdrType.HDR10_PRESERVE
                }
                else -> targetHdrType
            }

            val needsGpuRender = (actualTargetHdrType == TargetHdrType.SDR_TO_HDR10)

            // Resolve HDR metadata with explicit, non-fabricated provenance
            val resolvedHdrMetadata: HdrStaticMetadata? = when {
                hdrStaticMetadata != null -> hdrStaticMetadata
                actualTargetHdrType == TargetHdrType.SDR_TO_HDR10 -> {
                    // Mathematically estimated from inverse tone mapping curve (target peak 1000 nits)
                    HdrStaticMetadata.createMathematicallyEstimated(
                        mastering = HdrMasteringDisplay.BT2020_TRUE_1000NITS,
                        peakNits = 1000,
                        averageNits = 203
                    )
                }
                actualTargetHdrType == TargetHdrType.HDR10_PRESERVE -> {
                    // Extract declared static metadata from source if present
                    var srcMeta: HdrStaticMetadata? = null
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                        sourceVideoFormat.containsKey(MediaFormat.KEY_HDR_STATIC_INFO)) {
                        val staticBuf = sourceVideoFormat.getByteBuffer(MediaFormat.KEY_HDR_STATIC_INFO)
                        if (staticBuf != null && staticBuf.remaining() >= HdrStaticMetadata.CTA_BUFFER_SIZE) {
                            srcMeta = HdrStaticMetadata.fromByteBuffer(staticBuf, MetadataProvenance.SOURCE_DECLARED)
                        }
                    }
                    srcMeta ?: HdrStaticMetadata.BT2020_TRUE_1000NITS
                }
                else -> null
            }

            // Initialize hardware encoder with surface input
            val enc = MediaCodecVideoEncoder(
                width = targetWidth,
                height = targetHeight,
                fps = sourceFps,
                qualityProfile = profile,
                capabilities = capabilities,
                targetHdrType = actualTargetHdrType,
                hdrStaticMetadata = resolvedHdrMetadata
            )
            encoder = enc

            val encoderInputSurface = enc.inputSurface
                ?: throw IllegalStateException("Encoder input surface was null")

            var decoderOutputSurface: Surface = encoderInputSurface
            if (needsGpuRender) {
                try {
                    val core = HdrRenderCore(
                        outputSurface = encoderInputSurface,
                        width = targetWidth,
                        height = targetHeight,
                        targetHdrType = actualTargetHdrType,
                        prefer10Bit = capabilities.supportsHardwareMain10
                    )
                    renderCore = core
                    decoderOutputSurface = core.decoderInputSurface
                        ?: throw IllegalStateException("HdrRenderCore decoder input surface was null")
                    UniLog.i(tag, "Initialized HdrRenderCore for SDR-to-HDR10 conversion (10-bit surface: ${core.isActually10BitSurface})")
                } catch (e: Exception) {
                    UniLog.e(tag, "Failed initializing HdrRenderCore: ${e.message}, falling back to direct surface", e)
                    decoderOutputSurface = encoderInputSurface
                }
            }

            // Initialize decoder with decoder output surface
            val dec = MediaCodecVideoDecoder(
                extractor = vExt,
                videoTrackIndex = videoTrackIndex,
                sourceFormat = sourceVideoFormat,
                outputSurface = decoderOutputSurface
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
            var emptyEosDrainCycles = 0
            val maxEmptyEosCycles = 300 // ~6 seconds max drain timeout after EOS signaled

            val powerManager = context?.getSystemService(Context.POWER_SERVICE) as? PowerManager

            while (!enc.isEncoderEosReached) {
                currentCoroutineContext().ensureActive()

                var madeProgress = false

                // Thermal check
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
                    val thermal = powerManager.currentThermalStatus
                    if (thermal >= PowerManager.THERMAL_STATUS_SEVERE) {
                        UniLog.w(tag, "Device thermal status is SEVERE ($thermal). Applying safety backpressure.")
                        Thread.sleep(10)
                    }
                }

                // 1. Feed decoder input from extractor
                if (!dec.isInputEos) {
                    val fed = dec.feedInput(timeoutUs = 2_000L)
                    if (fed) madeProgress = true
                }

                // 2. Drain decoder output to render surface / encoder surface
                if (!dec.isOutputEos) {
                    val rendered = dec.drainOutput(timeoutUs = 2_000L)
                    if (rendered) {
                        framesSinceLastFps++
                        madeProgress = true
                        if (renderCore != null) {
                            renderCore.renderFrame(dec.lastDecodedPtsUs * 1000L)
                        }
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
                if (drained) {
                    madeProgress = true
                    emptyEosDrainCycles = 0
                } else if (isEncoderEosSignaled) {
                    emptyEosDrainCycles++
                    if (emptyEosDrainCycles > maxEmptyEosCycles) {
                        UniLog.w(tag, "Encoder drain timeout reached after EOS ($emptyEosDrainCycles empty cycles). Concluding encoding stage.")
                        break
                    }
                }

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

            val expectedHdr = (actualTargetHdrType == TargetHdrType.SDR_TO_HDR10 || actualTargetHdrType == TargetHdrType.HDR10_PRESERVE) && capabilities.supportsHardwareMain10

            val validation = ProcessingOutputValidator.validate(
                outputFile = tempOutputFile,
                expectedHasAudio = audioProcessor.hasAudioTrack,
                expectedDurationSeconds = durationUs / 1_000_000L,
                expectedRotationDegrees = rotationDegrees,
                expectedHdr10 = expectedHdr
            )

            if (!validation.isValid) {
                tempOutputFile.delete()
                return@withContext Result.failure(
                    IllegalStateException("Output validation failed: ${validation.errorDetails}")
                )
            }

            // -----------------------------------------------------------------
            // STAGE 5: COMPLETED - Atomic Promotion to Destination
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

            // Verify source file integrity was 100% preserved
            val sourceChecksumAfter = computeSha256(sourceFile)
            if (sourceChecksumBefore != "unknown" && sourceChecksumAfter != "unknown") {
                if (sourceChecksumBefore != sourceChecksumAfter) {
                    UniLog.e(tag, "CRITICAL: Source file modified during transcode ($sourceChecksumBefore vs $sourceChecksumAfter)")
                } else {
                    UniLog.i(tag, "Verified source file integrity preserved: $sourceChecksumBefore")
                }
            }

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
                "(${finalSizeBytes / 1024} KB, ${validation.width}x${validation.height}, rot=${validation.rotationDegrees}°, " +
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
            try { renderCore?.release() } catch (_: Exception) {}
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
        if (destination.exists() && destination.length() == source.length()) {
            source.delete()
        } else {
            throw IllegalStateException("Failed copying temporary file to destination: size mismatch or copy failed")
        }
    }

    private fun computeSha256(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { fis ->
                val buf = ByteArray(64 * 1024)
                var n: Int
                while (fis.read(buf).also { n = it } != -1) {
                    digest.update(buf, 0, n)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            "unknown"
        }
    }
}
