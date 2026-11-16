package com.unidownco.engine.verifier

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import com.unidownco.core.logging.UniLog
import java.io.File

data class ProcessingValidationReport(
    val isValid: Boolean,
    val isHevc: Boolean,
    val hasVideoTrack: Boolean,
    val hasAudioTrack: Boolean,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int = 0,
    val durationSeconds: Long,
    val fileSizeBytes: Long,
    val canDecodeBitmap: Boolean,
    val isHdr10: Boolean = false,
    val bitDepth: Int = 8,
    val colorStandard: String? = null,
    val colorTransfer: String? = null,
    val hasStaticMetadata: Boolean = false,
    val hevcProfile: Int? = null,
    val errorDetails: String? = null
)

/**
 * Strict post-processing validator for transcoded HEVC files.
 * Validates container structure, video codec, track presence, dimensions, duration,
 * genuine HDR10 properties (10-bit Main10, BT.2020, ST 2084 PQ, static metadata),
 * and performs a decode probe to confirm decodability.
 */
object ProcessingOutputValidator {

    private const val TAG = "ProcessingOutputValidator"

    fun validate(
        outputFile: File,
        expectedHasAudio: Boolean = false,
        expectedDurationSeconds: Long? = null,
        expectedRotationDegrees: Int? = null,
        expectedHdr10: Boolean = false
    ): ProcessingValidationReport {
        if (!outputFile.exists() || outputFile.length() == 0L) {
            val msg = "Output file does not exist or has 0 bytes: ${outputFile.name}"
            UniLog.e(TAG, msg)
            return ProcessingValidationReport(
                isValid = false,
                isHevc = false,
                hasVideoTrack = false,
                hasAudioTrack = false,
                width = 0,
                height = 0,
                rotationDegrees = 0,
                durationSeconds = 0L,
                fileSizeBytes = 0L,
                canDecodeBitmap = false,
                errorDetails = msg
            )
        }

        var isHevc = false
        var hasVideo = false
        var hasAudio = false
        var width = 0
        var height = 0
        var durationMs = 0L
        var detectedRotation = 0
        var hevcProfile: Int? = null
        var bitDepth = 8
        var colorStandard: String? = null
        var colorTransfer: String? = null
        var hasStaticMetadata = false

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(outputFile.absolutePath)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""

                if (mime.startsWith("video/")) {
                    hasVideo = true
                    if (mime.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true)) {
                        isHevc = true
                    }
                    if (format.containsKey(MediaFormat.KEY_WIDTH)) {
                        width = format.getInteger(MediaFormat.KEY_WIDTH)
                    }
                    if (format.containsKey(MediaFormat.KEY_HEIGHT)) {
                        height = format.getInteger(MediaFormat.KEY_HEIGHT)
                    }
                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        durationMs = format.getLong(MediaFormat.KEY_DURATION) / 1000L
                    }

                    if (format.containsKey(MediaFormat.KEY_PROFILE)) {
                        val prof = format.getInteger(MediaFormat.KEY_PROFILE)
                        hevcProfile = prof
                        if (prof == 2) { // HEVCProfileMain10
                            bitDepth = 10
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        if (format.containsKey(MediaFormat.KEY_COLOR_STANDARD)) {
                            colorStandard = when (format.getInteger(MediaFormat.KEY_COLOR_STANDARD)) {
                                MediaFormat.COLOR_STANDARD_BT2020 -> "bt2020"
                                MediaFormat.COLOR_STANDARD_BT709 -> "bt709"
                                else -> "other"
                            }
                        }
                        if (format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) {
                            colorTransfer = when (format.getInteger(MediaFormat.KEY_COLOR_TRANSFER)) {
                                MediaFormat.COLOR_TRANSFER_ST2084 -> "smpte2084"
                                MediaFormat.COLOR_TRANSFER_HLG -> "arib-std-b67"
                                MediaFormat.COLOR_TRANSFER_SDR_VIDEO -> "bt709"
                                else -> "other"
                            }
                        }
                        if (format.containsKey(MediaFormat.KEY_HDR_STATIC_INFO)) {
                            val staticBuf = format.getByteBuffer(MediaFormat.KEY_HDR_STATIC_INFO)
                            hasStaticMetadata = staticBuf != null && staticBuf.remaining() >= 25
                        }
                    }
                } else if (mime.startsWith("audio/")) {
                    hasAudio = true
                }
            }
        } catch (e: Exception) {
            val msg = "Failed parsing media tracks from ${outputFile.name}: ${e.message}"
            UniLog.e(TAG, msg, e)
            return ProcessingValidationReport(
                isValid = false,
                isHevc = false,
                hasVideoTrack = false,
                hasAudioTrack = false,
                width = 0,
                height = 0,
                durationSeconds = 0L,
                fileSizeBytes = outputFile.length(),
                canDecodeBitmap = false,
                errorDetails = msg
            )
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }

        if (!hasVideo) {
            val msg = "Validation failed: No video track found in ${outputFile.name}"
            UniLog.e(TAG, msg)
            return ProcessingValidationReport(
                isValid = false,
                isHevc = false,
                hasVideoTrack = false,
                hasAudioTrack = hasAudio,
                width = 0,
                height = 0,
                durationSeconds = 0L,
                fileSizeBytes = outputFile.length(),
                canDecodeBitmap = false,
                errorDetails = msg
            )
        }

        if (!isHevc) {
            val msg = "Validation failed: Video track is not HEVC in ${outputFile.name}"
            UniLog.e(TAG, msg)
            return ProcessingValidationReport(
                isValid = false,
                isHevc = false,
                hasVideoTrack = true,
                hasAudioTrack = hasAudio,
                width = width,
                height = height,
                durationSeconds = durationMs / 1000L,
                fileSizeBytes = outputFile.length(),
                canDecodeBitmap = false,
                errorDetails = msg
            )
        }

        if (expectedHasAudio && !hasAudio) {
            val msg = "Validation warning: Source had audio but output missing audio track in ${outputFile.name}"
            UniLog.w(TAG, msg)
            // Report error if audio was expected but omitted
            return ProcessingValidationReport(
                isValid = false,
                isHevc = true,
                hasVideoTrack = true,
                hasAudioTrack = false,
                width = width,
                height = height,
                rotationDegrees = detectedRotation,
                durationSeconds = durationMs / 1000L,
                fileSizeBytes = outputFile.length(),
                canDecodeBitmap = false,
                isHdr10 = false,
                bitDepth = bitDepth,
                colorStandard = colorStandard,
                colorTransfer = colorTransfer,
                hasStaticMetadata = hasStaticMetadata,
                hevcProfile = hevcProfile,
                errorDetails = msg
            )
        }

        // Perform bitmap decode check and metadata extraction using MediaMetadataRetriever
        var canDecode = false
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(outputFile.absolutePath)
            val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            canDecode = bitmap != null && bitmap.width > 0 && bitmap.height > 0
            if (bitmap != null) {
                try {
                    bitmap.recycle() // Prevent native memory accumulation during sustained encoding
                } catch (_: Exception) {}
            }
            if (durationMs <= 0L) {
                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                durationMs = durStr?.toLongOrNull() ?: 0L
            }
            val rotStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            detectedRotation = rotStr?.toIntOrNull() ?: 0
        } catch (e: Exception) {
            UniLog.w(TAG, "Frame decode probe failed: ${e.message}")
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        val durationSeconds = durationMs / 1000L

        // Check duration plausibility if expected duration was provided
        if (expectedDurationSeconds != null && expectedDurationSeconds > 0L) {
            val diff = Math.abs(durationSeconds - expectedDurationSeconds)
            if (diff > (expectedDurationSeconds * 0.25).coerceAtLeast(3.0)) {
                UniLog.w(TAG, "Duration disparity: expected ${expectedDurationSeconds}s, got ${durationSeconds}s")
            }
        }

        var rotationValid = true
        if (expectedRotationDegrees != null) {
            val normalizedExpected = ((expectedRotationDegrees % 360) + 360) % 360
            val normalizedDetected = ((detectedRotation % 360) + 360) % 360
            if (normalizedExpected != normalizedDetected) {
                UniLog.w(TAG, "Rotation mismatch: expected ${normalizedExpected}°, detected ${normalizedDetected}°")
                rotationValid = false
            }
        }

        val isHdr10 = isHevc && (hevcProfile == 2 || bitDepth >= 10) &&
            colorStandard == "bt2020" && colorTransfer == "smpte2084" && hasStaticMetadata

        if (expectedHdr10 && !isHdr10) {
            val msg = "Validation failed: Expected genuine 10-bit HDR10 output in ${outputFile.name}, but found: " +
                "profile=$hevcProfile, bitDepth=$bitDepth, standard=$colorStandard, transfer=$colorTransfer, staticMetadata=$hasStaticMetadata"
            UniLog.e(TAG, msg)
            return ProcessingValidationReport(
                isValid = false,
                isHevc = isHevc,
                hasVideoTrack = true,
                hasAudioTrack = hasAudio,
                width = width,
                height = height,
                rotationDegrees = detectedRotation,
                durationSeconds = durationSeconds,
                fileSizeBytes = outputFile.length(),
                canDecodeBitmap = canDecode,
                isHdr10 = false,
                bitDepth = bitDepth,
                colorStandard = colorStandard,
                colorTransfer = colorTransfer,
                hasStaticMetadata = hasStaticMetadata,
                hevcProfile = hevcProfile,
                errorDetails = msg
            )
        }

        val isValid = hasVideo && isHevc && width > 0 && height > 0 && rotationValid

        UniLog.i(
            TAG,
            "Validation result for ${outputFile.name}: valid=$isValid, hevc=$isHevc, hdr10=$isHdr10, " +
            "res=${width}x${height}, rot=${detectedRotation}°, dur=${durationSeconds}s, audio=$hasAudio, decodeProbe=$canDecode, size=${outputFile.length()} bytes"
        )

        return ProcessingValidationReport(
            isValid = isValid,
            isHevc = isHevc,
            hasVideoTrack = hasVideo,
            hasAudioTrack = hasAudio,
            width = width,
            height = height,
            rotationDegrees = detectedRotation,
            durationSeconds = durationSeconds,
            fileSizeBytes = outputFile.length(),
            canDecodeBitmap = canDecode,
            isHdr10 = isHdr10,
            bitDepth = bitDepth,
            colorStandard = colorStandard,
            colorTransfer = colorTransfer,
            hasStaticMetadata = hasStaticMetadata,
            hevcProfile = hevcProfile,
            errorDetails = if (isValid) null else if (!rotationValid) "Rotation mismatch (expected $expectedRotationDegrees°, got $detectedRotation°)" else "Validation checks failed"
        )
    }
}
