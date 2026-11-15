package com.unidownco.engine.verifier

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import com.unidownco.core.logging.UniLog
import java.io.File

data class ProcessingValidationReport(
    val isValid: Boolean,
    val isHevc: Boolean,
    val hasVideoTrack: Boolean,
    val hasAudioTrack: Boolean,
    val width: Int,
    val height: Int,
    val durationSeconds: Long,
    val fileSizeBytes: Long,
    val canDecodeBitmap: Boolean,
    val errorDetails: String? = null
)

/**
 * Strict post-processing validator for transcoded HEVC files.
 * Validates container structure, video codec, track presence, dimensions, duration,
 * and performs a decode probe to confirm decodability.
 */
object ProcessingOutputValidator {

    private const val TAG = "ProcessingOutputValidator"

    fun validate(
        outputFile: File,
        expectedHasAudio: Boolean = false,
        expectedDurationSeconds: Long? = null
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
                durationSeconds = durationMs / 1000L,
                fileSizeBytes = outputFile.length(),
                canDecodeBitmap = false,
                errorDetails = msg
            )
        }

        // Perform bitmap decode check using MediaMetadataRetriever
        var canDecode = false
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(outputFile.absolutePath)
            val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            canDecode = bitmap != null && bitmap.width > 0 && bitmap.height > 0
            if (durationMs <= 0L) {
                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                durationMs = durStr?.toLongOrNull() ?: 0L
            }
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

        val isValid = hasVideo && isHevc && width > 0 && height > 0

        UniLog.i(
            TAG,
            "Validation result for ${outputFile.name}: valid=$isValid, hevc=$isHevc, " +
            "res=${width}x${height}, dur=${durationSeconds}s, audio=$hasAudio, decodeProbe=$canDecode, size=${outputFile.length()} bytes"
        )

        return ProcessingValidationReport(
            isValid = isValid,
            isHevc = isHevc,
            hasVideoTrack = hasVideo,
            hasAudioTrack = hasAudio,
            width = width,
            height = height,
            durationSeconds = durationSeconds,
            fileSizeBytes = outputFile.length(),
            canDecodeBitmap = canDecode,
            errorDetails = if (isValid) null else "Validation checks failed"
        )
    }
}
