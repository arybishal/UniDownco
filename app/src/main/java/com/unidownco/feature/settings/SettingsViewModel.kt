package com.unidownco.feature.settings

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unidownco.core.storage.StorageManager
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
    val storageLocation: String = "Movies/downloads",
    val cacheSizeBytes: Long = 0L,
    val ytDlpVersion: String = YtDlpEngineManager.ytDlpVersion ?: "Bundled",
    val isUpdatingYtDlp: Boolean = false,
    val ytDlpUpdateMessage: String? = null
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
}
