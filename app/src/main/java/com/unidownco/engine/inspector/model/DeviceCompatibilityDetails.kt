package com.unidownco.engine.inspector.model

enum class CompatibilityStatus(val label: String, val badgeColor: String) {
    AVAILABLE("Available (Hardware)", "#107C41"),
    AVAILABLE_WITH_LIMITATIONS("Available (With Limitations)", "#FFD429"),
    SOFTWARE_ONLY("Software Only (CPU Fallback)", "#777777"),
    HARDWARE_UNSUPPORTED("Hardware Unsupported", "#D83B01"),
    NOT_VERIFIED("Not Physically Verified", "#8E8E93"),
    UNKNOWN("Unknown", "#333333")
}

/**
 * Silicon and MediaCodec capabilities cross-referenced against the inspected media file.
 */
data class DeviceCompatibilityDetails(
    val deviceModel: String,
    val socPlatform: String,
    val videoDecodeSupport: CompatibilityStatus,
    val audioDecodeSupport: CompatibilityStatus,
    val hevcMain8HardwareEncode: CompatibilityStatus,
    val hevcMain10HardwareEncode: CompatibilityStatus,
    val surfaceInputPipelineSupport: CompatibilityStatus,
    val tenBitYuvProcessingSupport: CompatibilityStatus,
    val maxResolutionSupported: String,
    val maxFrameRateSupported: Int,
    val explanationNotes: List<String> = emptyList()
)
