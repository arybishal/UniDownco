package com.unidownco.engine.inspector.model

enum class WarningSeverity {
    INFO,
    WARNING,
    CRITICAL
}

/**
 * Structured, non-fatal advisory issued during media inspection.
 * Examples: 8-bit fake HDR signaling, container vs bitstream color mismatches,
 * unmeasured light level omission, truncated optional metadata.
 */
data class InspectionWarning(
    val severity: WarningSeverity,
    val code: String,
    val message: String,
    val technicalImpact: String
)

/**
 * Structured error encountered during file ingestion, container parsing, or bitstream extraction.
 * Distinguishes unparseable / malformed files from recognized but unsupported media formats.
 */
data class InspectionError(
    val code: String,
    val message: String,
    val isMalformed: Boolean = false,
    val isUnsupportedFormat: Boolean = false,
    val cause: String? = null
)
