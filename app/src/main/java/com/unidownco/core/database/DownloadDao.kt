package com.unidownco.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(entity: DownloadEntity): Long

    @Update
    suspend fun updateDownload(entity: DownloadEntity)

    @Delete
    suspend fun deleteDownload(entity: DownloadEntity)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getDownloadById(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    fun observeDownloadById(id: Long): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE downloadStatus IN ('PENDING', 'FETCHING_METADATA', 'DOWNLOADING', 'PAUSED') ORDER BY createdAt DESC")
    fun getActiveQueue(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE downloadStatus = 'COMPLETED' ORDER BY completedAt DESC, createdAt DESC")
    fun getCompletedLibrary(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE url = :url ORDER BY createdAt DESC LIMIT 1")
    suspend fun findByUrl(url: String): DownloadEntity?

    @Query("""
        UPDATE downloads 
        SET downloadedBytes = :downloadedBytes, 
            progressPercent = :progress, 
            downloadSpeedBytesPerSec = :speed, 
            etaSeconds = :eta 
        WHERE id = :id
    """)
    suspend fun updateProgress(id: Long, downloadedBytes: Long, progress: Int, speed: Long, eta: Long)

    @Query("UPDATE downloads SET downloadStatus = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE downloads SET downloadStatus = :status, downloadPath = :path, completedAt = :completedAt WHERE id = :id")
    suspend fun markCompleted(id: Long, status: String, path: String, completedAt: Long)

    @Query("UPDATE downloads SET downloadStatus = 'FAILED', errorMessage = :error WHERE id = :id")
    suspend fun markFailed(id: Long, error: String)

    @Query("UPDATE downloads SET downloadStatus = 'PAUSED' WHERE downloadStatus = 'DOWNLOADING'")
    suspend fun pauseAllActiveDownloads()
}

@Dao
interface VideoMetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: VideoMetadataEntity): Long

    @Query("SELECT * FROM video_metadata WHERE downloadId = :downloadId LIMIT 1")
    suspend fun getMetadataForDownload(downloadId: Long): VideoMetadataEntity?
}
