package com.unidownco.engine.hardware

import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.os.Build
import com.unidownco.core.logging.UniLog

data class HardwareCapabilities(
    val deviceModel: String = "Generic Android",
    val socBoard: String = "generic",
    val androidVersion: Int = 34,
    val hasHardwareHevcEncoder: Boolean = false,
    val hardwareEncoderName: String? = null,
    val softwareEncoderName: String? = null,
    val supportsHardwareMain: Boolean = false,
    val supportsHardwareMain10: Boolean = false,
    val supportsSoftwareMain10: Boolean = false,
    val supportsSurfaceInput: Boolean = false,
    val supports10BitYuv: Boolean = false,
    val maxWidth: Int = 1920,
    val maxHeight: Int = 1080,
    val maxFrameRate: Int = 60,
    val supportedBitrateModes: List<String> = emptyList(),
    val isSnapdragon8Gen3Class: Boolean = false,
    val preferredEncoderName: String = "c2.android.hevc.encoder",
    val preferredProfile: Int = 1, // CodecProfileLevel.HEVCProfileMain
    val adaptationSummary: String = ""
)

object HardwareCapabilityRegistry {

    private const val TAG = "HardwareCapabilityRegistry"
    private const val MIME_HEVC = "video/hevc"
    private const val MIME_AVC = "video/avc"

    @Volatile
    private var cachedCapabilities: HardwareCapabilities? = null

    /**
     * Probes device MediaCodec encoders and caches the capability assessment.
     */
    fun getCapabilities(): HardwareCapabilities {
        return cachedCapabilities ?: synchronized(this) {
            cachedCapabilities ?: probeDeviceCapabilities().also { cachedCapabilities = it }
        }
    }

    private fun probeDeviceCapabilities(): HardwareCapabilities {
        val manufacturer = try { Build.MANUFACTURER ?: "Android" } catch (_: Throwable) { "Android" }
        val model = try { Build.MODEL ?: "Device" } catch (_: Throwable) { "Device" }
        val board = try { Build.BOARD ?: "generic" } catch (_: Throwable) { "generic" }
        val apiLevel = try { Build.VERSION.SDK_INT } catch (_: Throwable) { 34 }

        UniLog.i(TAG, "Probing hardware encoding capabilities for $manufacturer $model (Board: $board, API: $apiLevel)")

        var hwEncoderName: String? = null
        var swEncoderName: String? = null
        var hwSupportsMain = false
        var hwSupportsMain10 = false
        var swSupportsMain10 = false
        var supportsSurface = false
        var supports10Bit = false
        var maxWidth = 1920
        var maxHeight = 1080
        var maxFps = 60
        val bitrateModes = mutableListOf<String>()

        try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val codecInfos = codecList.codecInfos ?: emptyArray()

            for (info in codecInfos) {
                if (!info.isEncoder) continue
                val supportedTypes = info.supportedTypes ?: emptyArray()
                if (!supportedTypes.any { it.equals(MIME_HEVC, ignoreCase = true) }) continue

                val isHw = if (apiLevel >= 29) {
                    try {
                        info.isHardwareAccelerated && info.isVendor
                    } catch (_: Throwable) {
                        !info.name.startsWith("c2.android.")
                    }
                } else {
                    !info.name.startsWith("OMX.google.") && !info.name.startsWith("c2.android.")
                }

                val caps = try {
                    info.getCapabilitiesForType(MIME_HEVC)
                } catch (e: Exception) {
                    UniLog.w(TAG, "Could not query capabilities for ${info.name}: ${e.message}")
                    null
                } ?: continue

                var hasMain = false
                var hasMain10 = false

                caps.profileLevels?.forEach { pl ->
                    if (pl.profile == CodecProfileLevel.HEVCProfileMain) hasMain = true
                    if (pl.profile == CodecProfileLevel.HEVCProfileMain10) hasMain10 = true
                }

                if (isHw) {
                    val currentMaxPixels = maxWidth.toLong() * maxHeight.toLong()
                    val thisWidth = caps.videoCapabilities?.supportedWidths?.upper ?: 0
                    val thisHeight = caps.videoCapabilities?.supportedHeights?.upper ?: 0
                    val thisMaxPixels = thisWidth.toLong() * thisHeight.toLong()

                    // Prefer modern Codec2 encoder (c2.) over legacy OMX, and prefer larger resolution support
                    val isCurrentC2 = hwEncoderName?.startsWith("c2.") == true
                    val isThisC2 = info.name.startsWith("c2.")

                    val shouldPickThis = hwEncoderName == null ||
                        (isThisC2 && !isCurrentC2) ||
                        (isThisC2 == isCurrentC2 && thisMaxPixels > currentMaxPixels)

                    if (shouldPickThis) {
                        hwEncoderName = info.name
                        hwSupportsMain = hasMain
                        hwSupportsMain10 = hasMain10
                        if (thisWidth > 0) maxWidth = thisWidth
                        if (thisHeight > 0) maxHeight = thisHeight
                        caps.videoCapabilities?.supportedFrameRates?.upper?.toInt()?.let { maxFps = it }
                    }

                    // Color formats
                    caps.colorFormats?.forEach { format ->
                        if (format == CodecCapabilities.COLOR_FormatSurface) supportsSurface = true
                        if (format == CodecCapabilities.COLOR_FormatYUVP010) supports10Bit = true
                    }

                    // Bitrate modes
                    caps.encoderCapabilities?.let { ec ->
                        if (ec.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)) bitrateModes.add("VBR")
                        if (ec.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)) bitrateModes.add("CBR")
                        if (ec.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ)) bitrateModes.add("CQ")
                    }
                } else {
                    swEncoderName = info.name
                    if (hasMain10) swSupportsMain10 = true
                }
            }
        } catch (e: Exception) {
            UniLog.e(TAG, "Failed probing MediaCodecList", e)
        }

        // Determine Snapdragon 8 Gen 3 class (Snapdragon 8 Gen 2 / 8 Gen 3 / 8 Elite)
        val isFlagshipSoC = hwSupportsMain10 && (maxWidth >= 3840 || maxHeight >= 2160)
        val preferredEncoder = hwEncoderName ?: swEncoderName ?: "c2.android.hevc.encoder"
        
        // Adaptation decision based on Option A:
        // If hardware supports Main10, use hardware Main10.
        // If hardware only supports Main (8-bit) like Snapdragon 680/685, use hardware Main 8-bit for daily operations.
        val preferredProfile: Int
        val adaptation: String

        if (hwSupportsMain10) {
            preferredProfile = CodecProfileLevel.HEVCProfileMain10
            adaptation = "Full Hardware HEVC Main10 active ($preferredEncoder). Native 10-bit HDR encoding enabled."
        } else if (hwSupportsMain) {
            preferredProfile = CodecProfileLevel.HEVCProfileMain
            adaptation = if (swSupportsMain10) {
                "Hardware HEVC Main (8-bit) active ($preferredEncoder). 10-bit Main10 available via software encoder."
            } else {
                "Hardware HEVC Main (8-bit) active ($preferredEncoder). 10-bit Main10 is unsupported on this SoC in hardware and software MediaCodec. HDR10 10-bit encoding is unsupported on this device."
            }
        } else if (swSupportsMain10) {
            preferredProfile = CodecProfileLevel.HEVCProfileMain10
            adaptation = "Software HEVC Main10 active ($preferredEncoder). No hardware HEVC encoder detected."
        } else {
            preferredProfile = CodecProfileLevel.HEVCProfileMain
            adaptation = "Software HEVC Main (8-bit) active ($preferredEncoder). 10-bit Main10 unsupported."
        }

        val capabilities = HardwareCapabilities(
            deviceModel = "$manufacturer $model",
            socBoard = board,
            androidVersion = apiLevel,
            hasHardwareHevcEncoder = hwEncoderName != null,
            hardwareEncoderName = hwEncoderName,
            softwareEncoderName = swEncoderName,
            supportsHardwareMain = hwSupportsMain,
            supportsHardwareMain10 = hwSupportsMain10,
            supportsSoftwareMain10 = swSupportsMain10,
            supportsSurfaceInput = supportsSurface,
            supports10BitYuv = supports10Bit,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            maxFrameRate = maxFps,
            supportedBitrateModes = bitrateModes,
            isSnapdragon8Gen3Class = isFlagshipSoC,
            preferredEncoderName = preferredEncoder,
            preferredProfile = preferredProfile,
            adaptationSummary = adaptation
        )

        UniLog.i(TAG, "Hardware Probe Complete: $capabilities")
        return capabilities
    }

    /**
     * Generates a comprehensive human-readable diagnostic report for logging and UI inspection.
     */
    fun getDiagnosticReport(): String {
        val caps = getCapabilities()
        return buildString {
            appendLine("=== UNIDOWNCO HARDWARE DIAGNOSTIC REPORT ===")
            appendLine("Device: ${caps.deviceModel}")
            appendLine("Board/SoC: ${caps.socBoard}")
            appendLine("Android API: ${caps.androidVersion}")
            appendLine("Hardware HEVC Encoder: ${caps.hardwareEncoderName ?: "None"}")
            appendLine("Software HEVC Encoder: ${caps.softwareEncoderName ?: "None"}")
            appendLine("HW HEVC Main (8-bit): ${if (caps.supportsHardwareMain) "YES" else "NO"}")
            appendLine("HW HEVC Main10 (10-bit): ${if (caps.supportsHardwareMain10) "YES (Flagship)" else "UNSUPPORTED"}")
            appendLine("SW HEVC Main10 (MediaCodec): ${if (caps.supportsSoftwareMain10) "YES" else "UNSUPPORTED"}")
            appendLine("Surface Input (EGL/Zero-Copy): ${if (caps.supportsSurfaceInput) "YES" else "NO"}")
            appendLine("10-bit P010 YUV: ${if (caps.supports10BitYuv) "YES" else "UNSUPPORTED"}")
            appendLine("Max Resolution: ${caps.maxWidth}x${caps.maxHeight} @ ${caps.maxFrameRate} FPS")
            appendLine("Bitrate Modes: ${caps.supportedBitrateModes.joinToString(", ")}")
            appendLine("Active Preferred Encoder: ${caps.preferredEncoderName}")
            appendLine("Active Preferred Profile: ${if (caps.preferredProfile == CodecProfileLevel.HEVCProfileMain10) "Main10 (10-bit)" else "Main (8-bit)"}")
            appendLine("Adaptation Policy: ${caps.adaptationSummary}")
            appendLine("=============================================")
        }
    }
}
