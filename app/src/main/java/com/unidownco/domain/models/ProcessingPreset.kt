package com.unidownco.domain.models

enum class ProcessingPreset(
    val id: String,
    val title: String,
    val description: String,
    val badge: String
) {
    DIRECT(
        id = "direct",
        title = "Direct Pass-through",
        description = "Saves original video stream without re-encoding (Fastest)",
        badge = "Fastest"
    ),
    HDR10_ENHANCED(
        id = "hdr10_enhanced",
        title = "HDR10 Enhanced",
        description = "Converts to 10-bit HEVC with BT.2020 wide color gamut & PQ HDR",
        badge = "Recommended"
    ),
    ULTRA_HDR_60FPS(
        id = "ultra_hdr_60fps",
        title = "Ultra HDR 60 FPS",
        description = "HEVC 10-bit HDR with 60 FPS motion enhancement",
        badge = "Pro"
    );

    companion object {
        fun fromId(id: String): ProcessingPreset {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: HDR10_ENHANCED
        }
    }
}
