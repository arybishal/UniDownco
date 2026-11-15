package com.unidownco.feature.settings

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import com.unidownco.core.storage.StorageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class SettingsUiState(
    val autoDownloadOnShare: Boolean = true,
    val autoEncodeToHevc: Boolean = true,
    val wifiOnlyDownload: Boolean = false,
    val defaultResolution: String = "1080p",
    val storageLocation: String = "Movies/downloads",
    val cacheSizeBytes: Long = 0L
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val storageManager = StorageManager(application)
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        calculateCacheSize()
    }

    fun onToggleAutoDownload(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoDownloadOnShare = enabled)
    }

    fun onToggleAutoEncode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoEncodeToHevc = enabled)
    }

    fun onToggleWifiOnly(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(wifiOnlyDownload = enabled)
    }

    fun onSelectResolution(resolution: String) {
        _uiState.value = _uiState.value.copy(defaultResolution = resolution)
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
}
