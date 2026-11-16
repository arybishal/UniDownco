package com.unidownco.feature.settings

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unidownco.core.storage.StorageManager
import com.unidownco.engine.hardware.HardwareCapabilities
import com.unidownco.engine.hardware.HardwareCapabilityRegistry
import com.unidownco.engine.hdr.HdrEnhancementConfig
import com.unidownco.engine.hdr.HdrPreset
import com.unidownco.engine.hdr.MetadataProvenance
import com.unidownco.engine.ytdlp.YtDlpEngineManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class SettingsUiState(
    val autoDownloadOnShare: Boolean = true,
    val autoEncodeToHevc: Boolean = true,
    val wifiOnlyDownload: Boolean = false,
    val defaultResolution: String = "1080p",
    val defaultHdrMode: String = "OFF", // OFF (SDR BT.709) or HDR10 (BT.2020 ST 2084 PQ)
    val storageLocation: String = "Movies/downloads",
    val cacheSizeBytes: Long = 0L,
    val ytDlpVersion: String = YtDlpEngineManager.ytDlpVersion ?: "Bundled",
    val isUpdatingYtDlp: Boolean = false,
    val ytDlpUpdateMessage: String? = null,
    val hardwareCaps: HardwareCapabilities = HardwareCapabilityRegistry.getCapabilities(),

    // Stage 2.3.1.2 HDR Enhancement Controls
    val hdrTargetPeakNits: Float = 1000f,
    val hdrMaxCll: Int = 1000,
    val hdrMaxFall: Int = 400,
    val hdrMasteringPeakNits: Float = 1000f,
    val hdrDynamicColorEnabled: Boolean = false,
    val hdrDynamicColorIntensity: Float = 0.5f,
    val selectedHdrPreset: HdrPreset? = HdrPreset.STANDARD_HDR10,
    val hdrValidationError: String? = null
) {
    fun toHdrEnhancementConfig(): HdrEnhancementConfig = HdrEnhancementConfig(
        targetPeakNits = hdrTargetPeakNits.toDouble(),
        maxCll = hdrMaxCll,
        maxFall = hdrMaxFall,
        masteringDisplayPeakNits = hdrMasteringPeakNits.toDouble(),
        dynamicColorEnabled = hdrDynamicColorEnabled,
        dynamicColorIntensity = hdrDynamicColorIntensity,
        provenance = MetadataProvenance.USER_CONFIGURED
    )
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs: SharedPreferences = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val storageManager = StorageManager(application)
    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        calculateCacheSize()
    }

    private fun loadInitialState(): SettingsUiState {
        val targetPeak = prefs.getFloat(KEY_HDR_TARGET_PEAK_NITS, 1000f)
        val maxCll = prefs.getInt(KEY_HDR_MAX_CLL, 1000)
        val maxFall = prefs.getInt(KEY_HDR_MAX_FALL, 400)
        val masteringPeak = prefs.getFloat(KEY_HDR_MASTERING_PEAK_NITS, 1000f)
        val dynamicColorEnabled = prefs.getBoolean(KEY_HDR_DYNAMIC_COLOR_ENABLED, false)
        val dynamicColorIntensity = prefs.getFloat(KEY_HDR_DYNAMIC_COLOR_INTENSITY, 0.5f)

        val config = HdrEnhancementConfig(
            targetPeakNits = targetPeak.toDouble(),
            maxCll = maxCll,
            maxFall = maxFall,
            masteringDisplayPeakNits = masteringPeak.toDouble(),
            dynamicColorEnabled = dynamicColorEnabled,
            dynamicColorIntensity = dynamicColorIntensity
        )
        val validation = config.validate()

        val matchingPreset = HdrPreset.entries.find {
            it.targetPeakNits.toFloat() == targetPeak &&
                    it.defaultMaxCll == maxCll &&
                    it.defaultMaxFall == maxFall &&
                    it.defaultMasteringPeakNits.toFloat() == masteringPeak
        }

        return SettingsUiState(
            autoDownloadOnShare = prefs.getBoolean(KEY_AUTO_DOWNLOAD_ON_SHARE, true),
            autoEncodeToHevc = prefs.getBoolean(KEY_AUTO_ENCODE_TO_HEVC, true),
            wifiOnlyDownload = prefs.getBoolean(KEY_WIFI_ONLY_DOWNLOAD, false),
            defaultResolution = prefs.getString(KEY_DEFAULT_RESOLUTION, "1080p") ?: "1080p",
            defaultHdrMode = prefs.getString(KEY_DEFAULT_HDR_MODE, "OFF") ?: "OFF",
            hdrTargetPeakNits = targetPeak,
            hdrMaxCll = maxCll,
            hdrMaxFall = maxFall,
            hdrMasteringPeakNits = masteringPeak,
            hdrDynamicColorEnabled = dynamicColorEnabled,
            hdrDynamicColorIntensity = dynamicColorIntensity,
            selectedHdrPreset = matchingPreset,
            hdrValidationError = validation.errorMessage
        )
    }

    fun onToggleAutoDownload(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_DOWNLOAD_ON_SHARE, enabled).apply()
        _uiState.value = _uiState.value.copy(autoDownloadOnShare = enabled)
    }

    fun onToggleAutoEncode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_ENCODE_TO_HEVC, enabled).apply()
        _uiState.value = _uiState.value.copy(autoEncodeToHevc = enabled)
    }

    fun onToggleWifiOnly(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY_DOWNLOAD, enabled).apply()
        _uiState.value = _uiState.value.copy(wifiOnlyDownload = enabled)
    }

    fun onSelectResolution(resolution: String) {
        prefs.edit().putString(KEY_DEFAULT_RESOLUTION, resolution).apply()
        _uiState.value = _uiState.value.copy(defaultResolution = resolution)
    }

    fun onSelectHdrMode(mode: String) {
        prefs.edit().putString(KEY_DEFAULT_HDR_MODE, mode).apply()
        _uiState.value = _uiState.value.copy(defaultHdrMode = mode)
    }

    fun onSelectHdrPreset(preset: HdrPreset) {
        val targetPeak = preset.targetPeakNits.toFloat()
        val maxCll = preset.defaultMaxCll
        val maxFall = preset.defaultMaxFall
        val masteringPeak = preset.defaultMasteringPeakNits.toFloat()

        prefs.edit()
            .putFloat(KEY_HDR_TARGET_PEAK_NITS, targetPeak)
            .putInt(KEY_HDR_MAX_CLL, maxCll)
            .putInt(KEY_HDR_MAX_FALL, maxFall)
            .putFloat(KEY_HDR_MASTERING_PEAK_NITS, masteringPeak)
            .apply()

        val config = HdrEnhancementConfig(
            targetPeakNits = targetPeak.toDouble(),
            maxCll = maxCll,
            maxFall = maxFall,
            masteringDisplayPeakNits = masteringPeak.toDouble(),
            dynamicColorEnabled = _uiState.value.hdrDynamicColorEnabled,
            dynamicColorIntensity = _uiState.value.hdrDynamicColorIntensity
        )
        val validation = config.validate()

        _uiState.value = _uiState.value.copy(
            hdrTargetPeakNits = targetPeak,
            hdrMaxCll = maxCll,
            hdrMaxFall = maxFall,
            hdrMasteringPeakNits = masteringPeak,
            selectedHdrPreset = preset,
            hdrValidationError = validation.errorMessage
        )
    }

    fun onHdrTargetPeakChanged(nits: Float) {
        val clamped = nits.coerceIn(100f, 4000f)
        prefs.edit().putFloat(KEY_HDR_TARGET_PEAK_NITS, clamped).apply()

        val updatedConfig = _uiState.value.copy(hdrTargetPeakNits = clamped).toHdrEnhancementConfig()
        val validation = updatedConfig.validate()
        val matchingPreset = HdrPreset.entries.find {
            it.targetPeakNits.toFloat() == clamped &&
                    it.defaultMaxCll == _uiState.value.hdrMaxCll &&
                    it.defaultMaxFall == _uiState.value.hdrMaxFall &&
                    it.defaultMasteringPeakNits.toFloat() == _uiState.value.hdrMasteringPeakNits
        }

        _uiState.value = _uiState.value.copy(
            hdrTargetPeakNits = clamped,
            selectedHdrPreset = matchingPreset,
            hdrValidationError = validation.errorMessage
        )
    }

    fun onMaxCllChanged(cll: Int) {
        val clamped = cll.coerceIn(0, 4000)
        prefs.edit().putInt(KEY_HDR_MAX_CLL, clamped).apply()

        val updatedConfig = _uiState.value.copy(hdrMaxCll = clamped).toHdrEnhancementConfig()
        val validation = updatedConfig.validate()

        _uiState.value = _uiState.value.copy(
            hdrMaxCll = clamped,
            selectedHdrPreset = null,
            hdrValidationError = validation.errorMessage
        )
    }

    fun onMaxFallChanged(fall: Int) {
        val clamped = fall.coerceIn(100, 1468)
        prefs.edit().putInt(KEY_HDR_MAX_FALL, clamped).apply()

        val updatedConfig = _uiState.value.copy(hdrMaxFall = clamped).toHdrEnhancementConfig()
        val validation = updatedConfig.validate()

        _uiState.value = _uiState.value.copy(
            hdrMaxFall = clamped,
            selectedHdrPreset = null,
            hdrValidationError = validation.errorMessage
        )
    }

    fun onMasteringPeakChanged(nits: Float) {
        val clamped = nits.coerceIn(0f, 4000f)
        prefs.edit().putFloat(KEY_HDR_MASTERING_PEAK_NITS, clamped).apply()

        val updatedConfig = _uiState.value.copy(hdrMasteringPeakNits = clamped).toHdrEnhancementConfig()
        val validation = updatedConfig.validate()

        _uiState.value = _uiState.value.copy(
            hdrMasteringPeakNits = clamped,
            selectedHdrPreset = null,
            hdrValidationError = validation.errorMessage
        )
    }

    fun onToggleDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HDR_DYNAMIC_COLOR_ENABLED, enabled).apply()
        _uiState.value = _uiState.value.copy(hdrDynamicColorEnabled = enabled)
    }

    fun onDynamicColorIntensityChanged(intensity: Float) {
        val clamped = intensity.coerceIn(0.0f, 1.0f)
        prefs.edit().putFloat(KEY_HDR_DYNAMIC_COLOR_INTENSITY, clamped).apply()
        _uiState.value = _uiState.value.copy(hdrDynamicColorIntensity = clamped)
    }

    fun updateYtDlp() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUpdatingYtDlp = true, ytDlpUpdateMessage = null)
            val result = YtDlpEngineManager.updateEngine(getApplication())
            val msg = result.getOrNull() ?: ("Update failed: " + result.exceptionOrNull()?.message)
            _uiState.value = _uiState.value.copy(
                isUpdatingYtDlp = false,
                ytDlpVersion = YtDlpEngineManager.ytDlpVersion ?: "Bundled",
                ytDlpUpdateMessage = msg
            )
            Toast.makeText(getApplication(), msg, Toast.LENGTH_LONG).show()
        }
    }

    fun clearCache() {
        try {
            val cacheDir = getApplication<Application>().cacheDir
            cacheDir.deleteRecursively()
            cacheDir.mkdirs()
            calculateCacheSize()
            Toast.makeText(getApplication(), "Cache cleared successfully.", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    private fun calculateCacheSize() {
        val cacheDir = getApplication<Application>().cacheDir
        val size = cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        _uiState.value = _uiState.value.copy(cacheSizeBytes = size)
    }

    companion object {
        const val PREFS_NAME = "unidownco_user_settings"
        const val KEY_AUTO_DOWNLOAD_ON_SHARE = "auto_download_on_share"
        const val KEY_AUTO_ENCODE_TO_HEVC = "auto_encode_to_hevc"
        const val KEY_WIFI_ONLY_DOWNLOAD = "wifi_only_download"
        const val KEY_DEFAULT_RESOLUTION = "default_resolution"
        const val KEY_DEFAULT_HDR_MODE = "default_hdr_mode"
        const val KEY_HDR_TARGET_PEAK_NITS = "hdr_target_peak_nits"
        const val KEY_HDR_MAX_CLL = "hdr_max_cll"
        const val KEY_HDR_MAX_FALL = "hdr_max_fall"
        const val KEY_HDR_MASTERING_PEAK_NITS = "hdr_mastering_peak_nits"
        const val KEY_HDR_DYNAMIC_COLOR_ENABLED = "hdr_dynamic_color_enabled"
        const val KEY_HDR_DYNAMIC_COLOR_INTENSITY = "hdr_dynamic_color_intensity"
    }
}
