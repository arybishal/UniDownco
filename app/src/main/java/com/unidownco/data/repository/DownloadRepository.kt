package com.unidownco.data.repository

import android.content.Context
import android.net.Uri
import com.unidownco.core.common.Platform
import com.unidownco.core.common.Resource
import com.unidownco.core.database.DownloadDao
import com.unidownco.core.database.DownloadEntity
import com.unidownco.core.logging.UniLog
import com.unidownco.core.network.DownloadForegroundService
import com.unidownco.core.storage.StorageManager
import com.unidownco.data.extractor.UniversalExtractorRegistry
import com.unidownco.domain.models.DownloadItem
import com.unidownco.domain.models.DownloadStatus
import com.unidownco.domain.models.EncodingStatus
import com.unidownco.domain.models.FormatOption
import com.unidownco.domain.models.VideoExtractionInfo
import com.unidownco.engine.downloader.DownloadEngine
import com.unidownco.engine.verifier.AndroidMediaVerifier
import com.unidownco.engine.verifier.MediaVerifier
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class DownloadRepository(
    private val context: Context,
    private val downloadDao: DownloadDao,
    private val storageManager: StorageManager,
    private val extractorRegistry: UniversalExtractorRegistry = UniversalExtractorRegistry(),
    private val downloadEngine: DownloadEngine = DownloadEngine(),
    private val verifier: MediaVerifier = AndroidMediaVerifier()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<Long, Job>()

    init {
        scope.launch {
            try {
                downloadDao.pauseAllActiveDownloads()
            } catch (e: Exception) {
                UniLog.e("DownloadRepository", "Failed to reconcile active downloads on startup", e)
            }
        }
    }

    fun getAllDownloads(): Flow<List<DownloadItem>> =
        downloadDao.getAllDownloads().map { list -> list.map { it.toDomain() } }

    fun getActiveQueue(): Flow<List<DownloadItem>> =
        downloadDao.getActiveQueue().map { list -> list.map { it.toDomain() } }

    fun getCompletedLibrary(): Flow<List<DownloadItem>> =
        downloadDao.getCompletedLibrary().map { list -> list.map { it.toDomain() } }

    suspend fun getDownloadById(id: Long): DownloadItem? =
        downloadDao.getDownloadById(id)?.toDomain()

    suspend fun extractMetadata(url: String): Resource<VideoExtractionInfo> {
        return extractorRegistry.extract(url)
    }

    /**
     * Enqueues a video for download with the selected format option.
     */
    suspend fun enqueueDownload(info: VideoExtractionInfo, selectedFormat: FormatOption): Long {
        if (!storageManager.hasAvailableSpace()) {
            throw IllegalStateException("Low storage space. At least 100MB required.")
        }

        val filename = "${storageManager.sanitizeFilename(info.title)}.${selectedFormat.container}"
        val targetFile = storageManager.createDownloadFile(info.title, selectedFormat.container)

        val entity = DownloadEntity(
            url = info.url,
            platform = info.platform.identifier,
            title = info.title,
            author = info.author,
            thumbnailUrl = info.thumbnailUrl,
            originalFilename = filename,
            streamUrl = selectedFormat.videoUrl,
            audioStreamUrl = selectedFormat.audioUrl,
            downloadPath = targetFile.absolutePath,
            resolution = selectedFormat.resolution,
            fps = selectedFormat.fps,
            codec = selectedFormat.videoCodec,
            durationSeconds = info.durationSeconds,
            fileSizeBytes = selectedFormat.estimatedSizeBytes,
            downloadStatus = DownloadStatus.PENDING.name,
            encodingStatus = EncodingStatus.NOT_STARTED.name,
            createdAt = System.currentTimeMillis()
        )

        val downloadId = downloadDao.insertDownload(entity)
        startDownload(downloadId, selectedFormat, targetFile, info.title, info.url)
        return downloadId
    }

    private fun startDownload(
        downloadId: Long,
        format: FormatOption,
        targetFile: File,
        title: String,
        originalUrl: String? = null
    ) {
        // Cancel existing job if running
        activeJobs[downloadId]?.cancel()

        val job = scope.launch {
            try {
                UniLog.i("DownloadRepository", "Starting download: id=$downloadId, title='$title', format=${format.resolution}")
                downloadDao.updateStatus(downloadId, DownloadStatus.DOWNLOADING.name)
                DownloadForegroundService.start(context, downloadId, title)

                val result = downloadEngine.downloadStream(
                    format = format,
                    destinationFile = targetFile,
                    originalUrl = originalUrl,
                    downloadId = downloadId
                ) { progress ->
                    downloadDao.updateProgress(
                        id = downloadId,
                        downloadedBytes = progress.downloadedBytes,
                        progress = progress.progressPercent,
                        speed = progress.speedBytesPerSec,
                        eta = progress.etaSeconds
                    )

                    DownloadForegroundService.updateProgress(
                        context = context,
                        downloadId = downloadId,
                        title = title,
                        progress = progress.progressPercent,
                        speed = "${progress.progressPercent}% (${progress.speedBytesPerSec / 1024} KB/s)"
                    )
                }

                if (result.isSuccess) {
                    val finalFile = result.getOrThrow()
                    UniLog.i("DownloadRepository", "Download finished, running hardware verification: ${finalFile.name}")
                    val verification = verifier.verifyMedia(finalFile)
                    if (verification.isValid) {
                        downloadDao.markCompleted(
                            id = downloadId,
                            status = DownloadStatus.COMPLETED.name,
                            path = finalFile.absolutePath,
                            completedAt = System.currentTimeMillis()
                        )
                        UniLog.i("DownloadRepository", "Verification PASSED, publishing to ${StorageManager.RELATIVE_MOVIES_PATH}/")
                        // Auto-export to Gallery so video appears in system gallery
                        val galleryUri = storageManager.exportToGallery(finalFile, title)
                        UniLog.i("DownloadRepository", "Exported to Gallery: $galleryUri")
                    } else {
                        val errMsg = "Media verification error: ${verification.errorDetails}"
                        UniLog.e("DownloadRepository", errMsg)
                        downloadDao.markFailed(downloadId, errMsg)
                    }
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Unknown download error"
                    UniLog.e("DownloadRepository", "Download failed for id=$downloadId: $err")
                    downloadDao.markFailed(downloadId, err)
                }
            } catch (ce: CancellationException) {
                UniLog.w("DownloadRepository", "Download cancelled for id=$downloadId")
                downloadDao.updateStatus(downloadId, DownloadStatus.CANCELLED.name)
            } catch (e: Exception) {
                UniLog.e("DownloadRepository", "Download error for id=$downloadId", e)
                downloadDao.markFailed(downloadId, e.message ?: "Download failed")
            } finally {
                activeJobs.remove(downloadId)
                if (activeJobs.isEmpty()) {
                    DownloadForegroundService.stop(context)
                }
            }
        }

        activeJobs[downloadId] = job
    }

    suspend fun pauseDownload(id: Long) {
        try {
            YoutubeDL.getInstance().destroyProcessById("dl_$id")
        } catch (_: Exception) {}
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        downloadDao.updateStatus(id, DownloadStatus.PAUSED.name)
        if (activeJobs.isEmpty()) {
            DownloadForegroundService.stop(context)
        }
    }

    suspend fun resumeDownload(id: Long) {
        val entity = downloadDao.getDownloadById(id) ?: return
        val targetPath = entity.downloadPath ?: return
        val targetFile = File(targetPath)

        val streamUrl = entity.streamUrl ?: entity.url
        val format = FormatOption(
            formatId = "resume",
            resolution = entity.resolution,
            fps = entity.fps,
            videoCodec = entity.codec,
            videoUrl = streamUrl,
            audioUrl = entity.audioStreamUrl,
            estimatedSizeBytes = entity.fileSizeBytes
        )

        startDownload(id, format, targetFile, entity.title, entity.url)
    }

    suspend fun cancelDownload(id: Long) {
        try {
            YoutubeDL.getInstance().destroyProcessById("dl_$id")
        } catch (_: Exception) {}
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        downloadDao.updateStatus(id, DownloadStatus.CANCELLED.name)
        if (activeJobs.isEmpty()) {
            DownloadForegroundService.stop(context)
        }
    }

    suspend fun retryDownload(id: Long) {
        resumeDownload(id)
    }

    suspend fun deleteDownload(id: Long) {
        val entity = downloadDao.getDownloadById(id)
        try {
            YoutubeDL.getInstance().destroyProcessById("dl_$id")
        } catch (_: Exception) {}
        activeJobs[id]?.cancel()
        activeJobs.remove(id)

        if (entity != null) {
            storageManager.deleteFile(entity.downloadPath)
            if (entity.downloadPath != null) {
                storageManager.deleteFile("${entity.downloadPath}.part")
                storageManager.deleteFile("${entity.downloadPath}.video.part")
                storageManager.deleteFile("${entity.downloadPath}.audio.part")
                val parent = File(entity.downloadPath).parentFile
                parent?.listFiles { _, name -> name.startsWith("ytdl_$id") }?.forEach { it.delete() }
            }
            downloadDao.deleteById(id)
        }
    }

    fun exportToGallery(item: DownloadItem): Uri? {
        val path = item.downloadPath ?: return null
        val file = File(path)
        return storageManager.exportToGallery(file, item.title)
    }
}
