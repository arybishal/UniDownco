package com.unidownco.domain.models

import com.unidownco.engine.hardware.HardwareCapabilities

enum class PresetStatus {
    AVAILABLE,
    PLANNED_STEP_2_2,
    PLANNED_STEP_2_3,
    PLANNED_STEP_2_4,
    HARDWARE_UNSUPPORTED
}

enum class ProcessingPreset(
    val id: String,
    val title: String,
    val defaultBadge: String
) {
    DIRECT(
        id = "direct",
        title = "Direct Pass-through",
        defaultBadge = "Active"
    ),
    HDR10_ENHANCED(
        id = "hdr10_enhanced",
        title = "HDR10 Enhanced",
        defaultBadge = "Planned"
    ),
    ULTRA_HDR_60FPS(
        id = "ultra_hdr_60fps",
        title = "Ultra HDR 60 FPS",
        defaultBadge = "Planned"
    );

    fun getStatus(caps: HardwareCapabilities): PresetStatus {
        return when (this) {
            DIRECT -> PresetStatus.AVAILABLE
            HDR10_ENHANCED -> {
                if (caps.supportsHardwareMain10) {
                    PresetStatus.PLANNED_STEP_2_3
                } else {
                    PresetStatus.HARDWARE_UNSUPPORTED
                }
            }
            ULTRA_HDR_60FPS -> PresetStatus.PLANNED_STEP_2_4
        }
    }

    fun getBadge(caps: HardwareCapabilities): String {
        return when (getStatus(caps)) {
            PresetStatus.AVAILABLE -> "Active"
            PresetStatus.PLANNED_STEP_2_2 -> "Step 2.2"
            PresetStatus.PLANNED_STEP_2_3 -> "Step 2.3"
            PresetStatus.PLANNED_STEP_2_4 -> "Step 2.4"
            PresetStatus.HARDWARE_UNSUPPORTED -> "8-Bit HW Only"
        }
    }

    fun getDescription(caps: HardwareCapabilities): String {
        return when (this) {
            DIRECT -> "Saves original video stream without re-encoding (Operational)"
            HDR10_ENHANCED -> {
                if (caps.supportsHardwareMain10) {
                    "Hardware 10-bit HEVC BT.2020 HDR pipeline (Coming in Step 2.3)"
                } else {
                    "10-bit HDR encoding unsupported on this SoC (Snapdragon 680/685 supports 8-bit Main only)"
                }
            }
            ULTRA_HDR_60FPS -> "Motion interpolation and high frame-rate pipeline (Coming in Step 2.4)"
        }
    }

    companion object {
        fun fromId(id: String): ProcessingPreset {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DIRECT
        }
    }
}
