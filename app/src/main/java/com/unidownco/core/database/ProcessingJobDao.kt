package com.unidownco.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProcessingJobDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: ProcessingJobEntity): Long

    @Update
    suspend fun updateJob(job: ProcessingJobEntity)

    @Query("SELECT * FROM processing_jobs WHERE id = :id LIMIT 1")
    suspend fun getJobById(id: Long): ProcessingJobEntity?

    @Query("SELECT * FROM processing_jobs WHERE downloadId = :downloadId LIMIT 1")
    suspend fun getJobByDownloadId(downloadId: Long): ProcessingJobEntity?

    @Query("SELECT * FROM processing_jobs WHERE downloadId = :downloadId LIMIT 1")
    fun observeJobByDownloadId(downloadId: Long): Flow<ProcessingJobEntity?>

    @Query("SELECT * FROM processing_jobs WHERE stage IN ('QUEUED', 'PREPARING', 'ANALYZING', 'DECODING', 'ENCODING', 'MUXING', 'VALIDATING', 'VERIFYING') ORDER BY id ASC")
    fun getActiveJobs(): Flow<List<ProcessingJobEntity>>

    @Query("UPDATE processing_jobs SET stage = :stage, progressPercent = :progress, currentFps = :fps WHERE id = :id")
    suspend fun updateProgress(id: Long, stage: String, progress: Int, fps: Double)

    @Query("UPDATE processing_jobs SET stage = 'COMPLETED', finalOutputPath = :outputPath, completedAt = :completedAt WHERE id = :id")
    suspend fun markCompleted(id: Long, outputPath: String, completedAt: Long)

    @Query("UPDATE processing_jobs SET stage = 'FAILED', errorMessage = :error WHERE id = :id")
    suspend fun markFailed(id: Long, error: String)

    @Query("UPDATE processing_jobs SET stage = 'FAILED', errorMessage = :reason WHERE stage IN ('PREPARING', 'DECODING', 'ENCODING', 'MUXING', 'VALIDATING')")
    suspend fun failInterruptedJobs(reason: String = "Interrupted by application restart")

    @Query("DELETE FROM processing_jobs WHERE downloadId = :downloadId")
    suspend fun deleteByDownloadId(downloadId: Long)
}
