package com.unidownco.engine.inspector.model

import com.unidownco.engine.hdr.MetadataProvenance

/**
 * Standard dynamic range and high dynamic range classification states.
 */
enum class HdrClassification(val label: String, val isHdr: Boolean) {
    GENUINE_HDR10("Genuine HDR10 (Main10 + BT.2020 + PQ + Metadata)", true),
    HDR10_PLUS("HDR10+ (Dynamic Metadata Present)", true),
    DOLBY_VISION("Dolby Vision", true),
    HLG("HLG (Hybrid Log-Gamma)", true),
    PARTIAL_HDR("Partial HDR (Signaling Incomplete / Non-Standard)", true),
    FAKE_HDR_REJECTED("Fake HDR Rejected (8-bit with HDR Signaling)", false),
    SDR_BT709("SDR (BT.709 Standard Dynamic Range)", false),
    MALFORMED_OR_CONFLICTING("Malformed / Conflicting Metadata", false),
    UNKNOWN("Unknown / Unspecified", false)
}

/**
 * SMPTE ST 2086 Mastering Display Colour Volume (MDCV) optical characteristics.
 */
data class MasteringDisplayReport(
    val greenX: Double,
    val greenY: Double,
    val blueX: Double,
    val blueY: Double,
    val redX: Double,
    val redY: Double,
    val whitePointX: Double,
    val whitePointY: Double,
    val maxLuminanceNits: Double,
    val minLuminanceNits: Double,
    val provenance: MetadataProvenance,
    val sourceLocation: String
)

/**
 * CTA-861.3 Content Light Level Information (CLLI) parameters.
 */
data class ContentLightLevelReport(
    val maxCllNits: Int,
    val maxFallNits: Int,
    val provenance: MetadataProvenance,
    val sourceLocation: String
)

/**
 * Proprietary or standard Dolby Vision stream signaling report.
 */
data class DolbyVisionReport(
    val profile: Int,
    val level: Int,
    val rpuDetected: Boolean,
    val elType: String? = null,
    val compatibilityId: Int? = null
)

/**
 * Explicit alert describing a conflict between container atoms and elementary bitstream headers.
 */
data class MetadataConflictAlert(
    val fieldName: String,
    val containerValue: String,
    val bitstreamValue: String,
    val frameworkValue: String? = null,
    val resolutionDescription: String
)

/**
 * Consolidated colorimetry, dynamic range, and mastering metadata model.
 */
data class HdrColorDetails(
    val classification: HdrClassification,
    val colorPrimariesName: String? = null,
    val colorTransferName: String? = null,
    val matrixCoefficientsName: String? = null,
    val colorRangeName: String? = null,
    val bitDepth: Int? = null,
    val masteringDisplay: MasteringDisplayReport? = null,
    val contentLightLevel: ContentLightLevelReport? = null,
    val dolbyVisionInfo: DolbyVisionReport? = null,
    val containerAtomVerified: Boolean = false,
    val bitstreamSeiVerified: Boolean = false,
    val conflicts: List<MetadataConflictAlert> = emptyList()
)
