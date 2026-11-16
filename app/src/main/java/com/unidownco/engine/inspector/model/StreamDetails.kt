package com.unidownco.engine.inspector.model

/**
 * Technical classification of the temporal presentation cadence.
 */
enum class FrameRateMode(val label: String) {
    CONSTANT_FRAME_RATE("Constant Frame Rate (CFR)"),
    VARIABLE_FRAME_RATE_CONFIRMED("Variable Frame Rate (VFR) Confirmed"),
    VARIABLE_FRAME_RATE_SUSPECTED("Variable Frame Rate (VFR) Suspected"),
    UNKNOWN_FRAME_RATE("Unknown Frame Rate")
}

/**
 * Common abstraction for all media tracks (video, audio, subtitle) within a container.
 */
sealed interface StreamDetails {
    val trackIndex: Int
    val trackId: Int
    val codecName: String
    val codecMime: String
    val codecFourCc: String?
    val isDefaultTrack: Boolean
    val durationMs: Long?
}

/**
 * Detailed technical specifications of a discrete video track.
 */
data class VideoStreamDetails(
    override val trackIndex: Int,
    override val trackId: Int,
    override val codecName: String,
    override val codecMime: String,
    override val codecFourCc: String? = null,
    override val isDefaultTrack: Boolean = true,
    override val durationMs: Long? = null,
    val width: Int,
    val height: Int,
    val displayWidth: Int? = null,
    val displayHeight: Int? = null,
    val displayAspectRatio: String? = null,
    val pixelAspectRatio: String? = null,
    val nominalFrameRate: Double? = null,
    val averageFrameRate: Double? = null,
    val isVariableFrameRate: Boolean? = null,
    val frameRateMode: FrameRateMode = FrameRateMode.UNKNOWN_FRAME_RATE,
    val bitrateKbps: Long? = null,
    val bitDepth: Int? = null,
    val profileName: String? = null,
    val profileIdc: Int? = null,
    val levelName: String? = null,
    val levelIdc: Int? = null,
    val chromaSubsampling: String? = null,
    val pixelFormat: String? = null,
    val colorPrimaries: String? = null,
    val colorTransfer: String? = null,
    val matrixCoefficients: String? = null,
    val colorRange: String? = null,
    val rotationDegrees: Int = 0,
    val keyframeCount: Int? = null
) : StreamDetails

/**
 * Detailed technical specifications of a discrete audio track.
 */
data class AudioStreamDetails(
    override val trackIndex: Int,
    override val trackId: Int,
    override val codecName: String,
    override val codecMime: String,
    override val codecFourCc: String? = null,
    override val isDefaultTrack: Boolean = true,
    override val durationMs: Long? = null,
    val profileName: String? = null,
    val channelCount: Int,
    val channelLayout: String? = null,
    val sampleRateHz: Int,
    val bitrateKbps: Long? = null,
    val bitDepth: Int? = null,
    val language: String? = null
) : StreamDetails

/**
 * Specifications of an embedded or referenced subtitle track.
 */
data class SubtitleStreamDetails(
    override val trackIndex: Int,
    override val trackId: Int,
    override val codecName: String,
    override val codecMime: String,
    override val codecFourCc: String? = null,
    override val isDefaultTrack: Boolean = false,
    override val durationMs: Long? = null,
    val formatName: String,
    val language: String? = null,
    val isForced: Boolean = false
) : StreamDetails
