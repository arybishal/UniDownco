package com.unidownco.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.unidownco.core.common.Platform
import com.unidownco.domain.models.DownloadItem
import com.unidownco.domain.models.DownloadStatus
import com.unidownco.domain.models.EncodingStatus

@Entity(
    tableName = "downloads",
    indices = [
        Index(value = ["url"]),
        Index(value = ["downloadStatus"]),
        Index(value = ["createdAt"])
    ]
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val url: String,
    val platform: String,
    val title: String,
    val author: String = "",
    val thumbnailUrl: String? = null,
    val originalFilename: String,
    val downloadPath: String? = null,
    val outputPath: String? = null,
    val resolution: String = "1080p",
    val fps: Int = 30,
    val codec: String = "h264",
    val durationSeconds: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val progressPercent: Int = 0,
    val downloadSpeedBytesPerSec: Long = 0L,
    val etaSeconds: Long = 0L,
    val downloadStatus: String = DownloadStatus.PENDING.name,
    val encodingStatus: String = EncodingStatus.NOT_STARTED.name,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
) {
    fun toDomain(): DownloadItem {
        return DownloadItem(
            id = id,
            url = url,
            platform = Platform.fromIdentifier(platform),
            title = title,
            author = author,
            thumbnailUrl = thumbnailUrl,
            originalFilename = originalFilename,
            downloadPath = downloadPath,
            outputPath = outputPath,
            resolution = resolution,
            fps = fps,
            codec = codec,
            durationSeconds = durationSeconds,
            fileSizeBytes = fileSizeBytes,
            downloadedBytes = downloadedBytes,
            progressPercent = progressPercent,
            downloadSpeedBytesPerSec = downloadSpeedBytesPerSec,
            etaSeconds = etaSeconds,
            downloadStatus = try { DownloadStatus.valueOf(downloadStatus) } catch (_: Exception) { DownloadStatus.FAILED },
            encodingStatus = try { EncodingStatus.valueOf(encodingStatus) } catch (_: Exception) { EncodingStatus.NOT_STARTED },
            errorMessage = errorMessage,
            createdAt = createdAt,
            completedAt = completedAt
        )
    }

    companion object {
        fun fromDomain(item: DownloadItem): DownloadEntity {
            return DownloadEntity(
                id = item.id,
                url = item.url,
                platform = item.platform.identifier,
                title = item.title,
                author = item.author,
                thumbnailUrl = item.thumbnailUrl,
                originalFilename = item.originalFilename,
                downloadPath = item.downloadPath,
                outputPath = item.outputPath,
                resolution = item.resolution,
                fps = item.fps,
                codec = item.codec,
                durationSeconds = item.durationSeconds,
                fileSizeBytes = item.fileSizeBytes,
                downloadedBytes = item.downloadedBytes,
                progressPercent = item.progressPercent,
                downloadSpeedBytesPerSec = item.downloadSpeedBytesPerSec,
                etaSeconds = item.etaSeconds,
                downloadStatus = item.downloadStatus.name,
                encodingStatus = item.encodingStatus.name,
                errorMessage = item.errorMessage,
                createdAt = item.createdAt,
                completedAt = item.completedAt
            )
        }
    }
}
