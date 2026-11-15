package com.unidownco.data.repository

import android.content.Context
import com.unidownco.core.database.DownloadDao
import com.unidownco.core.database.ProcessingJobDao
import com.unidownco.core.database.ProcessingJobEntity
import com.unidownco.core.database.UniDownDatabase
import com.unidownco.core.logging.UniLog
import com.unidownco.core.network.DownloadForegroundService
import com.unidownco.core.storage.StorageManager
import com.unidownco.domain.models.EncodingStatus
import com.unidownco.domain.models.ProcessingPreset
import com.unidownco.engine.encoder.QualityProfile
import com.unidownco.engine.hardware.HardwareCapabilityRegistry
import com.unidownco.engine.processor.VideoProcessingEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Repository coordinating video processing jobs, Room persistence,
 * foreground service updates, and execution via [VideoProcessingEngine].
 */
class ProcessingJobRepository(
    private val context: Context,
    private val processingJobDao: ProcessingJobDao,
    private val downloadDao: DownloadDao,
    private val storageManager: StorageManager,
    private val engine: VideoProcessingEngine = VideoProcessingEngine()
) {
    private val tag = "ProcessingJobRepository"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<Long, Job>()

    init {
        scope.launch {
            reconcileJobsOnStartup()
        }
    }

    fun getActiveJobs(): Flow<List<ProcessingJobEntity>> =
        processingJobDao.getActiveJobs()

    fun observeJobByDownloadId(downloadId: Long): Flow<ProcessingJobEntity?> =
        processingJobDao.observeJobByDownloadId(downloadId)

    suspend fun getJobByDownloadId(downloadId: Long): ProcessingJobEntity? =
        processingJobDao.getJobByDownloadId(downloadId)

    /**
     * Starts execution of a processing job for the specified download.
     */
    fun startProcessingJob(
        downloadId: Long,
        sourceFile: File,
        title: String,
        preset: ProcessingPreset
    ) {
        if (preset == ProcessingPreset.DIRECT) {
            UniLog.i(tag, "Preset is DIRECT pass-through, skipping transcoding for download $downloadId")
            return
        }

        activeJobs[downloadId]?.cancel()

        val job = scope.launch {
            var jobId = 0L
            try {
                // Ensure processing job record exists in Room
                val existing = processingJobDao.getJobByDownloadId(downloadId)
                jobId = if (existing != null) {
                    existing.id
                } else {
                    val caps = HardwareCapabilityRegistry.getCapabilities()
                    val entity = ProcessingJobEntity(
                        downloadId = downloadId,
                        preset = preset.id,
                        targetCodec = "hevc",
                        targetHdrType = if (caps.supportsHardwareMain10) "HDR10" else "SDR_BT709",
                        targetFps = 30,
                        stage = "PREPARING",
                        startedAt = System.currentTimeMillis()
                    )
                    processingJobDao.insertJob(entity)
                }

                UniLog.i(tag, "Starting processing job $jobId for download $downloadId: ${sourceFile.name}")
                processingJobDao.updateProgress(jobId, "PREPARING", 0, 0.0)

                // Enforce destination in Movies/downloads/
                val destinationFile = storageManager.createProcessingFile(title, "mp4")

                val result = engine.encode(
                    sourceFile = sourceFile,
                    outputFile = destinationFile,
                    profile = QualityProfile.BALANCED
                ) { progress ->
                    scope.launch {
                        try {
                            processingJobDao.updateProgress(
                                id = jobId,
                                stage = progress.stage,
                                progress = progress.percent,
                                fps = progress.speedFps
                            )

                            // Update foreground service notification
                            DownloadForegroundService.updateProgress(
                                context = context,
                                downloadId = downloadId,
                                title = "$title [Processing]",
                                progress = progress.percent,
                                speed = "${progress.stage} • ${progress.speedFps.toInt()} FPS"
                            )
                        } catch (e: Exception) {
                            UniLog.w(tag, "Failed updating progress: ${e.message}")
                        }
                    }
                }

                if (result.isSuccess) {
                    val encResult = result.getOrThrow()
                    UniLog.i(tag, "Job $jobId completed successfully: ${encResult.outputFile.name}")
                    processingJobDao.markCompleted(jobId, encResult.outputFile.absolutePath, System.currentTimeMillis())

                    // Export processed file to Gallery
                    try {
                        storageManager.exportToGallery(encResult.outputFile, "$title (HEVC)")
                    } catch (e: Exception) {
                        UniLog.w(tag, "Failed exporting processed video to gallery: ${e.message}")
                    }
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Transcode failed"
                    UniLog.e(tag, "Job $jobId failed: $err")
                    processingJobDao.markFailed(jobId, err)
                }
            } catch (ce: CancellationException) {
                UniLog.w(tag, "Job for download $downloadId was cancelled")
                if (jobId != 0L) {
                    processingJobDao.updateProgress(jobId, "CANCELLED", 0, 0.0)
                }
            } catch (e: Exception) {
                UniLog.e(tag, "Unexpected error in job for download $downloadId", e)
                if (jobId != 0L) {
                    processingJobDao.markFailed(jobId, e.message ?: "Unexpected error")
                }
            } finally {
                activeJobs.remove(downloadId)
                if (activeJobs.isEmpty()) {
                    DownloadForegroundService.stop(context)
                }
            }
        }

        activeJobs[downloadId] = job
    }

    /**
     * Cancels an active processing job.
     */
    fun cancelJob(downloadId: Long) {
        UniLog.i(tag, "Cancelling processing job for download $downloadId")
        activeJobs[downloadId]?.cancel()
        activeJobs.remove(downloadId)
        scope.launch {
            val job = processingJobDao.getJobByDownloadId(downloadId)
            if (job != null) {
                processingJobDao.updateProgress(job.id, "CANCELLED", 0, 0.0)
            }
        }
    }

    /**
     * Retries a failed processing job.
     */
    fun retryJob(downloadId: Long) {
        scope.launch {
            val download = downloadDao.getDownloadById(downloadId) ?: return@launch
            val path = download.downloadPath ?: return@launch
            val sourceFile = File(path)
            if (!sourceFile.exists()) {
                UniLog.e(tag, "Cannot retry job: source file does not exist at $path")
                return@launch
            }

            val job = processingJobDao.getJobByDownloadId(downloadId)
            val preset = ProcessingPreset.fromId(job?.preset ?: ProcessingPreset.HDR10_ENHANCED.id)
            startProcessingJob(downloadId, sourceFile, download.title, preset)
        }
    }

    /**
     * Reconciles interrupted jobs on startup.
     */
    private suspend fun reconcileJobsOnStartup() {
        try {
            // Processing jobs stuck in transitional stages are safely marked FAILED on app restart
            processingJobDao.failInterruptedJobs("Interrupted by application restart")
            val cleanedFiles = storageManager.cleanOrphanedTempProcessingFiles()
            UniLog.i(tag, "Reconciled processing jobs on startup: cleaned $cleanedFiles orphaned temp files")
        } catch (e: Exception) {
            UniLog.w(tag, "Failed reconciling processing jobs on startup: ${e.message}")
        }
    }

    companion object {
        @Volatile
        private var instance: ProcessingJobRepository? = null

        fun getInstance(context: Context): ProcessingJobRepository {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val db = UniDownDatabase.getInstance(context)
                    val storage = StorageManager(context.applicationContext)
                    val engine = VideoProcessingEngine(context = context.applicationContext)
                    ProcessingJobRepository(
                        context = context.applicationContext,
                        processingJobDao = db.processingJobDao(),
                        downloadDao = db.downloadDao(),
                        storageManager = storage,
                        engine = engine
                    ).also { instance = it }
                }
            }
        }
    }
}
