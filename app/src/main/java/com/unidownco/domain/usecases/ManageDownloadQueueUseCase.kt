package com.unidownco.domain.usecases

import com.unidownco.data.repository.DownloadRepository
import com.unidownco.domain.models.DownloadItem
import kotlinx.coroutines.flow.Flow

class ManageDownloadQueueUseCase(private val repository: DownloadRepository) {

    fun getActiveQueue(): Flow<List<DownloadItem>> = repository.getActiveQueue()

    fun getCompletedLibrary(): Flow<List<DownloadItem>> = repository.getCompletedLibrary()

    suspend fun pause(id: Long) = repository.pauseDownload(id)

    suspend fun resume(id: Long) = repository.resumeDownload(id)

    suspend fun cancel(id: Long) = repository.cancelDownload(id)

    suspend fun retry(id: Long) = repository.retryDownload(id)

    suspend fun delete(id: Long) = repository.deleteDownload(id)
}
