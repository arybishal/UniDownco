package com.unidownco.feature.queue

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unidownco.core.database.ProcessingJobEntity
import com.unidownco.core.database.UniDownDatabase
import com.unidownco.core.storage.StorageManager
import com.unidownco.data.repository.DownloadRepository
import com.unidownco.data.repository.ProcessingJobRepository
import com.unidownco.domain.models.DownloadItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class QueueViewModel(application: Application) : AndroidViewModel(application) {

    private val db = UniDownDatabase.getInstance(application)
    private val storageManager = StorageManager(application)
    val repository = DownloadRepository(application, db.downloadDao(), storageManager)
    val processingRepository = ProcessingJobRepository.getInstance(application)

    val activeQueue: StateFlow<List<DownloadItem>> = repository.getActiveQueue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDownloads: StateFlow<List<DownloadItem>> = repository.getAllDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProcessingJobs: StateFlow<List<ProcessingJobEntity>> = processingRepository.getActiveJobs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onPause(id: Long) {
        viewModelScope.launch { repository.pauseDownload(id) }
    }

    fun onResume(id: Long) {
        viewModelScope.launch { repository.resumeDownload(id) }
    }

    fun onCancel(id: Long) {
        viewModelScope.launch { repository.cancelDownload(id) }
    }

    fun onRetry(id: Long) {
        viewModelScope.launch { repository.retryDownload(id) }
    }

    fun onDelete(id: Long) {
        viewModelScope.launch { repository.deleteDownload(id) }
    }

    fun onCancelProcessing(downloadId: Long) {
        processingRepository.cancelJob(downloadId)
    }

    fun onRetryProcessing(downloadId: Long) {
        processingRepository.retryJob(downloadId)
    }
}
