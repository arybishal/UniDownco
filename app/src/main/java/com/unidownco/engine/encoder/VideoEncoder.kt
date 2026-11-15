package com.unidownco.engine.encoder

import java.io.File

enum class QualityProfile {
    MAXIMUM_QUALITY,
    BALANCED,
    FAST
}

enum class EncoderBackend {
    HARDWARE_MEDIACODEC,
    SOFTWARE_FFMPEG
}

data class EncodingProgress(
    val stage: String,
    val percent: Int,
    val speedFps: Double,
    val backend: EncoderBackend
)

data class EncodingResult(
    val outputFile: File,
    val durationSeconds: Long,
    val finalSizeBytes: Long,
    val backendUsed: EncoderBackend
)

/**
 * Interface prepared for Step 2 HEVC Main10 encoding
 */
interface VideoEncoder {
    suspend fun encode(
        sourceFile: File,
        outputFile: File,
        profile: QualityProfile,
        onProgress: (EncodingProgress) -> Unit
    ): Result<EncodingResult>
}
