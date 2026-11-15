package com.unidownco.engine.analyzer

import java.io.File

data class VideoAnalysisResult(
    val container: String,
    val videoCodec: String,
    val audioCodec: String,
    val width: Int,
    val height: Int,
    val frameRate: Double,
    val bitDepth: Int,
    val pixelFormat: String,
    val colorPrimaries: String,
    val transferCharacteristics: String,
    val matrixCoefficients: String,
    val hdrType: String,
    val hasHdr10Metadata: Boolean = false,
    val hasHdr10PlusMetadata: Boolean = false,
    val hasDolbyVisionRpu: Boolean = false,
    val dolbyVisionProfile: String? = null,
    val durationSeconds: Long = 0L,
    val audioChannels: Int = 2
)

/**
 * Interface prepared for Step 2 Video Analysis
 */
interface VideoAnalyzer {
    suspend fun analyze(videoFile: File): Result<VideoAnalysisResult>
}
