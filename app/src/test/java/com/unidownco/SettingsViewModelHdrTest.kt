package com.unidownco

import com.google.common.truth.Truth.assertThat
import com.unidownco.engine.hdr.HdrPreset
import com.unidownco.feature.settings.SettingsUiState
import org.junit.Test

class SettingsViewModelHdrTest {

    @Test
    fun testSettingsUiState_DefaultHdrConfiguration() {
        val state = SettingsUiState()
        assertThat(state.hdrTargetPeakNits).isEqualTo(1000f)
        assertThat(state.hdrMaxCll).isEqualTo(1000)
        assertThat(state.hdrMaxFall).isEqualTo(400)
        assertThat(state.hdrMasteringPeakNits).isEqualTo(1000f)
        assertThat(state.hdrDynamicColorEnabled).isFalse()
        assertThat(state.hdrDynamicColorIntensity).isEqualTo(0.5f)
        assertThat(state.selectedHdrPreset).isEqualTo(HdrPreset.STANDARD_HDR10)
        assertThat(state.hdrValidationError).isNull()

        val config = state.toHdrEnhancementConfig()
        assertThat(config.isValid).isTrue()
        assertThat(config.targetPeakNits).isEqualTo(1000.0)
    }

    @Test
    fun testSettingsUiState_PresetMapping() {
        val brightPreset = HdrPreset.HDR10_BRIGHT
        val state = SettingsUiState(
            hdrTargetPeakNits = brightPreset.targetPeakNits.toFloat(),
            hdrMaxCll = brightPreset.defaultMaxCll,
            hdrMaxFall = brightPreset.defaultMaxFall,
            hdrMasteringPeakNits = brightPreset.defaultMasteringPeakNits.toFloat(),
            selectedHdrPreset = brightPreset
        )

        val config = state.toHdrEnhancementConfig()
        assertThat(config.targetPeakNits).isEqualTo(1500.0)
        assertThat(config.maxCll).isEqualTo(1500)
        assertThat(config.maxFall).isEqualTo(600)
        assertThat(config.isValid).isTrue()
    }

    @Test
    fun testSettingsUiState_ValidationMismatchDetected() {
        // MaxFALL (1200) > MaxCLL (1000) -> invalid
        val invalidState = SettingsUiState(
            hdrMaxCll = 1000,
            hdrMaxFall = 1200,
            hdrValidationError = "MaxFALL (1200 nits) cannot exceed MaxCLL (1000 nits)."
        )

        val config = invalidState.toHdrEnhancementConfig()
        assertThat(config.isValid).isFalse()
        assertThat(config.validationError).contains("MaxFALL (1200 nits) cannot exceed MaxCLL (1000 nits)")
    }

    @Test
    fun testSettingsUiState_DynamicColorConfiguration() {
        val dynamicState = SettingsUiState(
            hdrDynamicColorEnabled = true,
            hdrDynamicColorIntensity = 0.85f
        )

        val config = dynamicState.toHdrEnhancementConfig()
        assertThat(config.dynamicColorEnabled).isTrue()
        assertThat(config.dynamicColorIntensity).isEqualTo(0.85f)
        assertThat(config.isValid).isTrue()
    }
}
