package com.unidownco.engine.hdr

/**
 * Validation result for [HdrEnhancementConfig].
 */
data class HdrConfigValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)

/**
 * Standard HDR10 mastering and rendering target presets.
 */
enum class HdrPreset(
    val displayName: String,
    val targetPeakNits: Double,
    val defaultMaxCll: Int,
    val defaultMaxFall: Int,
    val defaultMasteringPeakNits: Double
) {
    STANDARD_HDR10(
        displayName = "Standard HDR10 (1000 nits)",
        targetPeakNits = 1000.0,
        defaultMaxCll = 1000,
        defaultMaxFall = 400,
        defaultMasteringPeakNits = 1000.0
    ),
    HDR10_BRIGHT(
        displayName = "HDR10 Bright (1500 nits)",
        targetPeakNits = 1500.0,
        defaultMaxCll = 1500,
        defaultMaxFall = 600,
        defaultMasteringPeakNits = 1500.0
    ),
    HDR10_HIGH_BRIGHTNESS(
        displayName = "HDR10 High Brightness (2000 nits)",
        targetPeakNits = 2000.0,
        defaultMaxCll = 2000,
        defaultMaxFall = 800,
        defaultMasteringPeakNits = 2000.0
    ),
    HDR10_ULTRA(
        displayName = "HDR10 Ultra (4000 nits)",
        targetPeakNits = 4000.0,
        defaultMaxCll = 4000,
        defaultMaxFall = 1000,
        defaultMasteringPeakNits = 4000.0
    );

    fun toConfig(
        dynamicColorEnabled: Boolean = false,
        dynamicColorIntensity: Float = 0.5f
    ): HdrEnhancementConfig = HdrEnhancementConfig(
        targetPeakNits = targetPeakNits,
        maxCll = defaultMaxCll,
        maxFall = defaultMaxFall,
        masteringDisplayPeakNits = defaultMasteringPeakNits,
        dynamicColorEnabled = dynamicColorEnabled,
        dynamicColorIntensity = dynamicColorIntensity,
        provenance = MetadataProvenance.USER_CONFIGURED
    )
}

/**
 * Unified domain configuration model governing HDR rendering, tone-mapping peak,
 * static metadata generation (CTA-861.3, HEVC SEI, MP4 atoms), and Dynamic Color enhancement.
 */
data class HdrEnhancementConfig(
    val targetPeakNits: Double = 1000.0,
    val maxCll: Int = 1000,
    val maxFall: Int = 400,
    val masteringDisplayPeakNits: Double = 1000.0,
    val dynamicColorEnabled: Boolean = false,
    val dynamicColorIntensity: Float = 0.5f,
    val provenance: MetadataProvenance = MetadataProvenance.USER_CONFIGURED
) {
    /**
     * Validates configuration parameters against SMPTE ST 2086, CTA-861.3, and BT.2408 standards.
     * Enforces the critical HDR authoring rule: MaxFALL must not exceed MaxCLL when MaxCLL is defined.
     */
    fun validate(): HdrConfigValidationResult {
        if (targetPeakNits !in MIN_TARGET_PEAK_NITS..MAX_TARGET_PEAK_NITS) {
            return HdrConfigValidationResult(
                isValid = false,
                errorMessage = "Target peak brightness (${targetPeakNits.toInt()} nits) must be between ${MIN_TARGET_PEAK_NITS.toInt()} and ${MAX_TARGET_PEAK_NITS.toInt()} nits."
            )
        }

        if (maxCll !in MIN_MAX_CLL..MAX_MAX_CLL) {
            return HdrConfigValidationResult(
                isValid = false,
                errorMessage = "MaxCLL ($maxCll nits) must be between $MIN_MAX_CLL and $MAX_MAX_CLL nits (0 = unspecified)."
            )
        }

        if (maxFall !in MIN_MAX_FALL..MAX_MAX_FALL) {
            return HdrConfigValidationResult(
                isValid = false,
                errorMessage = "MaxFALL ($maxFall nits) must be between $MIN_MAX_FALL and $MAX_MAX_FALL nits."
            )
        }

        if (masteringDisplayPeakNits !in MIN_MASTERING_PEAK_NITS..MAX_MASTERING_PEAK_NITS) {
            return HdrConfigValidationResult(
                isValid = false,
                errorMessage = "Mastering display peak (${masteringDisplayPeakNits.toInt()} nits) must be between ${MIN_MASTERING_PEAK_NITS.toInt()} and ${MAX_MASTERING_PEAK_NITS.toInt()} nits (0 = unspecified)."
            )
        }

        if (dynamicColorIntensity !in 0.0f..1.0f) {
            return HdrConfigValidationResult(
                isValid = false,
                errorMessage = "Dynamic Color intensity must be between 0% and 100%."
            )
        }

        // Critical metadata validation rule: MaxFALL cannot exceed MaxCLL
        if (maxCll > 0 && maxFall > maxCll) {
            return HdrConfigValidationResult(
                isValid = false,
                errorMessage = "MaxFALL ($maxFall nits) cannot exceed MaxCLL ($maxCll nits)."
            )
        }

        return HdrConfigValidationResult(isValid = true, errorMessage = null)
    }

    val isValid: Boolean get() = validate().isValid
    val validationError: String? get() = validate().errorMessage

    /**
     * Converts this configuration into a standards-compliant [HdrStaticMetadata] container,
     * maintaining explicit metadata provenance and avoiding fabrication.
     */
    fun toHdrStaticMetadata(): HdrStaticMetadata {
        val effectiveMasteringPeak = if (masteringDisplayPeakNits > 0.0) {
            masteringDisplayPeakNits.toInt()
        } else {
            targetPeakNits.toInt()
        }

        val mastering = HdrMasteringDisplay.BT2020_TRUE_1000NITS.copy(
            maxMasteringLuminanceNits = effectiveMasteringPeak,
            provenance = provenance,
            profileName = "BT.2020 (${effectiveMasteringPeak}n user target)"
        )

        val cll = if (maxCll > 0 || maxFall > 0) {
            HdrContentLightLevel(
                maxCllNits = maxCll,
                maxFallNits = maxFall,
                provenance = provenance,
                calculationMethod = "User-configured metadata override (${maxCll}n / ${maxFall}n)"
            )
        } else {
            HdrContentLightLevel.UNAVAILABLE_INSTANCE
        }

        return HdrStaticMetadata(
            masteringDisplay = mastering,
            contentLightLevel = cll
        )
    }

    companion object {
        const val MIN_TARGET_PEAK_NITS = 100.0
        const val MAX_TARGET_PEAK_NITS = 4000.0
        const val DEFAULT_TARGET_PEAK_NITS = 1000.0

        const val MIN_MAX_CLL = 0
        const val MAX_MAX_CLL = 4000
        const val DEFAULT_MAX_CLL = 1000

        const val MIN_MAX_FALL = 100
        const val MAX_MAX_FALL = 1468
        const val DEFAULT_MAX_FALL = 400

        const val MIN_MASTERING_PEAK_NITS = 0.0
        const val MAX_MASTERING_PEAK_NITS = 4000.0
        const val DEFAULT_MASTERING_PEAK_NITS = 1000.0

        val QUICK_SELECT_PEAKS = listOf(1000.0, 1200.0, 1500.0, 2000.0, 2500.0, 3000.0, 4000.0)

        val DEFAULT = HdrEnhancementConfig()

        fun fromPreset(preset: HdrPreset): HdrEnhancementConfig = preset.toConfig()
    }
}
