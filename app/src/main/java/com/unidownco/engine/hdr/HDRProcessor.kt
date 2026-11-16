package com.unidownco.engine.hdr

import java.io.File

enum class TargetHdrType {
    SDR_PASSTHROUGH,
    SDR_TO_HDR10,
    HDR10_PRESERVE,
    HDR10_PLUS,
    DOLBY_VISION_COMPATIBLE
}

data class HdrProcessResult(
    val processedFile: File,
    val targetType: TargetHdrType,
    val colorPrimaries: String = "bt2020",
    val transferCharacteristics: String = "smpte2084",
    val bitDepth: Int = 10
)

/**
 * Interface prepared for Step 3 HDR color science pipeline
 */
interface HDRProcessor {
    suspend fun processHdr(
        sourceFile: File,
        targetType: TargetHdrType
    ): Result<HdrProcessResult>
}
