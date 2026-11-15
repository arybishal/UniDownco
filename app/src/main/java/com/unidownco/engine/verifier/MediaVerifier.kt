package com.unidownco.engine.verifier

import java.io.File

data class VerificationReport(
    val isValid: Boolean,
    val bitDepth: Int,
    val hevcProfile: String?,
    val pixelFormat: String?,
    val colorPrimaries: String?,
    val transferCharacteristics: String?,
    val matrixCoefficients: String?,
    val frameRate: Double,
    val resolution: String,
    val isAudioSynced: Boolean,
    val isFileIntegrityVerified: Boolean,
    val errorDetails: String? = null
)

/**
 * Interface prepared for Step 2 and Step 3 Media Verification
 */
interface MediaVerifier {
    suspend fun verifyMedia(file: File): VerificationReport
}
