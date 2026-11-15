package com.unidownco.feature.home

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unidownco.core.common.Platform
import com.unidownco.core.common.Resource
import com.unidownco.core.common.UrlValidator
import com.unidownco.core.database.UniDownDatabase
import com.unidownco.core.storage.StorageManager
import com.unidownco.data.repository.DownloadRepository
import com.unidownco.domain.models.DownloadItem
import com.unidownco.domain.models.FormatOption
import com.unidownco.domain.models.VideoExtractionInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val urlInput: String = "",
    val detectedPlatform: Platform = Platform.UNSUPPORTED,
    val isUrlValid: Boolean = false,
    val isExtracting: Boolean = false,
    val extractionError: String? = null,
    val extractedInfo: VideoExtractionInfo? = null,
    val selectedFormat: FormatOption? = null,
    val showFormatSheet: Boolean = false,
    val freeStorageBytes: Long = 0L,
    val totalStorageBytes: Long = 0L
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val db = UniDownDatabase.getInstance(application)
    private val storageManager = StorageManager(application)
    val repository = DownloadRepository(application, db.downloadDao(), storageManager)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val recentDownloads: StateFlow<List<DownloadItem>> = repository.getAllDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeQueue: StateFlow<List<DownloadItem>> = repository.getActiveQueue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        updateStorageMetrics()
    }

    fun onUrlChanged(newUrl: String) {
        val validation = UrlValidator.validateAndExtract(newUrl)
        _uiState.value = _uiState.value.copy(
            urlInput = newUrl,
            detectedPlatform = validation.platform,
            isUrlValid = validation.isValid,
            extractionError = null
        )
    }

    fun onPasteFromClipboard() {
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clipData = clipboard?.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            val text = clipData.getItemAt(0)?.text?.toString() ?: ""
            onUrlChanged(text)
        }
    }

    fun onClearUrl() {
        _uiState.value = _uiState.value.copy(
            urlInput = "",
            detectedPlatform = Platform.UNSUPPORTED,
            isUrlValid = false,
            extractionError = null,
            extractedInfo = null,
            showFormatSheet = false
        )
    }

    fun onStartAnalysis() {
        val url = _uiState.value.urlInput
        val validation = UrlValidator.validateAndExtract(url)
        if (!validation.isValid) {
            _uiState.value = _uiState.value.copy(extractionError = validation.errorMessage ?: "Invalid URL")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExtracting = true, extractionError = null)
            when (val res = repository.extractMetadata(validation.sanitizedUrl)) {
                is Resource.Success -> {
                    val info = res.data
                    val defaultFormat = info.formats.firstOrNull { it.isBestQuality }
                        ?: info.formats.firstOrNull()
                    _uiState.value = _uiState.value.copy(
                        isExtracting = false,
                        extractedInfo = info,
                        selectedFormat = defaultFormat,
                        showFormatSheet = true
                    )
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isExtracting = false,
                        extractionError = res.message
                    )
                }
                is Resource.Loading -> {
                    _uiState.value = _uiState.value.copy(isExtracting = true)
                }
            }
        }
    }

    fun onSelectFormat(format: FormatOption) {
        _uiState.value = _uiState.value.copy(selectedFormat = format)
    }

    fun onDismissFormatSheet() {
        _uiState.value = _uiState.value.copy(showFormatSheet = false)
    }

    fun onConfirmDownload() {
        val info = _uiState.value.extractedInfo ?: return
        val format = _uiState.value.selectedFormat ?: info.formats.firstOrNull() ?: return

        viewModelScope.launch {
            try {
                repository.enqueueDownload(info, format)
                _uiState.value = _uiState.value.copy(
                    showFormatSheet = false,
                    urlInput = "",
                    extractedInfo = null,
                    isUrlValid = false,
                    detectedPlatform = Platform.UNSUPPORTED
                )
                updateStorageMetrics()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(extractionError = e.message)
            }
        }
    }

    fun updateStorageMetrics() {
        _uiState.value = _uiState.value.copy(
            freeStorageBytes = storageManager.getAvailableStorageBytes(),
            totalStorageBytes = storageManager.getTotalStorageBytes()
        )
    }
}
