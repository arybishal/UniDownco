package com.unidownco.engine.analyzer

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import com.unidownco.core.database.VideoMetadataEntity
import com.unidownco.core.logging.UniLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class VideoAnalyzerImpl : VideoAnalyzer {

    private val tag = "VideoAnalyzerImpl"

    override suspend fun analyze(videoFile: File): Result<VideoAnalysisResult> = withContext(Dispatchers.IO) {
        if (!videoFile.exists() || videoFile.length() == 0L) {
            return@withContext Result.failure(IllegalArgumentException("Video file does not exist or is empty: ${videoFile.absolutePath}"))
        }

        val extractor = MediaExtractor()
        val retriever = MediaMetadataRetriever()
        try {
            extractor.setDataSource(videoFile.absolutePath)
            try {
                retriever.setDataSource(videoFile.absolutePath)
            } catch (e: Exception) {
                UniLog.w(tag, "MediaMetadataRetriever datasource warning: ${e.message}")
            }

            var videoMime = "unknown"
            var audioMime = "none"
            var width = 0
            var height = 0
            var frameRate = 30.0
            var durationSeconds = 0L
            var bitDepth = 8
            var colorPrimaries = "bt709"
            var colorTransfer = "bt709"
            var matrixCoefficients = "bt709"
            var hdrType = "SDR"
            var hasHdr10Metadata = false
            var hasHdr10PlusMetadata = false
            var hasDolbyVisionRpu = false
            var dolbyVisionProfile: String? = null
            var audioChannels = 2

            val numTracks = extractor.trackCount
            for (i in 0 until numTracks) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue

                if (mime.startsWith("video/") && videoMime == "unknown") {
                    videoMime = mime
                    width = format.getInteger(MediaFormat.KEY_WIDTH, 0)
                    height = format.getInteger(MediaFormat.KEY_HEIGHT, 0)

                    if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        try {
                            frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE).toDouble()
                        } catch (_: Exception) {
                            try {
                                frameRate = format.getFloat(MediaFormat.KEY_FRAME_RATE).toDouble()
                            } catch (_: Exception) {}
                        }
                    }

                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        durationSeconds = format.getLong(MediaFormat.KEY_DURATION) / 1_000_000L
                    }

                    // Query Color Standards (API 24+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        if (format.containsKey(MediaFormat.KEY_COLOR_STANDARD)) {
                            colorPrimaries = when (format.getInteger(MediaFormat.KEY_COLOR_STANDARD)) {
                                MediaFormat.COLOR_STANDARD_BT2020 -> "bt2020"
                                MediaFormat.COLOR_STANDARD_BT709 -> "bt709"
                                MediaFormat.COLOR_STANDARD_BT601_NTSC, MediaFormat.COLOR_STANDARD_BT601_PAL -> "bt601"
                                else -> "bt709"
                            }
                        }

                        if (format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) {
                            colorTransfer = when (format.getInteger(MediaFormat.KEY_COLOR_TRANSFER)) {
                                MediaFormat.COLOR_TRANSFER_ST2084 -> "smpte2084"
                                MediaFormat.COLOR_TRANSFER_HLG -> "arib-std-b67"
                                MediaFormat.COLOR_TRANSFER_SDR_VIDEO -> "bt709"
                                MediaFormat.COLOR_TRANSFER_LINEAR -> "linear"
                                else -> "bt709"
                            }
                        }

                        if (format.containsKey(MediaFormat.KEY_COLOR_RANGE)) {
                            matrixCoefficients = when (format.getInteger(MediaFormat.KEY_COLOR_RANGE)) {
                                MediaFormat.COLOR_RANGE_FULL -> "full"
                                MediaFormat.COLOR_RANGE_LIMITED -> "limited"
                                else -> "limited"
                            }
                        }

                        if (format.containsKey(MediaFormat.KEY_HDR_STATIC_INFO)) {
                            hasHdr10Metadata = true
                        }
                    }

                    // Check profile / bit depth
                    if (format.containsKey(MediaFormat.KEY_PROFILE)) {
                        val profile = format.getInteger(MediaFormat.KEY_PROFILE)
                        if (mime.equals("video/hevc", ignoreCase = true) && profile == 2) { // HEVCProfileMain10 = 2
                            bitDepth = 10
                        } else if (mime.contains("vp9", ignoreCase = true) && profile >= 2) {
                            bitDepth = 10
                        }
                    }

                    // Detect Dolby Vision
                    if (mime.equals("video/dolby-vision", ignoreCase = true) || mime.contains("dolby", ignoreCase = true)) {
                        hasDolbyVisionRpu = true
                        hdrType = "DOLBY_VISION"
                        dolbyVisionProfile = "Profile 8.1"
                    } else if (colorTransfer == "smpte2084") {
                        hdrType = if (hasHdr10PlusMetadata) "HDR10+" else "HDR10"
                        bitDepth = 10
                    } else if (colorTransfer == "arib-std-b67") {
                        hdrType = "HLG"
                        bitDepth = 10
                    } else {
                        hdrType = "SDR"
                    }
                } else if (mime.startsWith("audio/") && audioMime == "none") {
                    audioMime = mime
                    if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        audioChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            }

            // Fallback via MediaMetadataRetriever if dimensions or duration were not resolved
            if (width == 0 || height == 0) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()?.let { width = it }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()?.let { height = it }
            }
            if (durationSeconds == 0L) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let {
                    durationSeconds = it / 1000L
                }
            }

            val container = videoFile.extension.lowercase().ifBlank { "mp4" }
            val pixelFormat = if (bitDepth == 10) "yuv420p10le" else "yuv420p"

            val result = VideoAnalysisResult(
                container = container,
                videoCodec = videoMime,
                audioCodec = audioMime,
                width = if (width > 0) width else 1920,
                height = if (height > 0) height else 1080,
                frameRate = if (frameRate > 0.0) frameRate else 30.0,
                bitDepth = bitDepth,
                pixelFormat = pixelFormat,
                colorPrimaries = colorPrimaries,
                transferCharacteristics = colorTransfer,
                matrixCoefficients = matrixCoefficients,
                hdrType = hdrType,
                hasHdr10Metadata = hasHdr10Metadata,
                hasHdr10PlusMetadata = hasHdr10PlusMetadata,
                hasDolbyVisionRpu = hasDolbyVisionRpu,
                dolbyVisionProfile = dolbyVisionProfile,
                durationSeconds = durationSeconds,
                audioChannels = audioChannels
            )

            UniLog.i(tag, "Video Analysis for ${videoFile.name}: ${result.width}x${result.height}, ${result.videoCodec}, ${result.frameRate}fps, ${result.hdrType} (${result.bitDepth}-bit)")
            Result.success(result)
        } catch (e: Exception) {
            UniLog.e(tag, "Failed to analyze video: ${videoFile.absolutePath}", e)
            Result.failure(e)
        } finally {
            try { extractor.release() } catch (_: Exception) {}
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    companion object {
        fun toEntity(downloadId: Long, analysis: VideoAnalysisResult): VideoMetadataEntity {
            return VideoMetadataEntity(
                downloadId = downloadId,
                container = analysis.container,
                videoCodec = analysis.videoCodec,
                audioCodec = analysis.audioCodec,
                width = analysis.width,
                height = analysis.height,
                frameRate = analysis.frameRate,
                bitDepth = analysis.bitDepth,
                pixelFormat = analysis.pixelFormat,
                colorPrimaries = analysis.colorPrimaries,
                transferCharacteristics = analysis.transferCharacteristics,
                matrixCoefficients = analysis.matrixCoefficients,
                hdrType = analysis.hdrType,
                audioChannels = analysis.audioChannels,
                rawAnalysisJson = """{"hdrType":"${analysis.hdrType}","bitDepth":${analysis.bitDepth},"hasHdr10":${analysis.hasHdr10Metadata}}"""
            )
        }
    }
}
