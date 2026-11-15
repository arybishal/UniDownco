package com.unidownco.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "processing_jobs",
    foreignKeys = [
        ForeignKey(
            entity = DownloadEntity::class,
            parentColumns = ["id"],
            childColumns = ["downloadId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["downloadId"]),
        Index(value = ["stage"])
    ]
)
data class ProcessingJobEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val downloadId: Long,
    val preset: String, // DIRECT, HDR10_ENHANCED, ULTRA_HDR_60FPS
    val targetCodec: String = "hevc",
    val targetHdrType: String = "HDR10",
    val targetFps: Int = 30,
    val stage: String = "QUEUED", // QUEUED, ANALYZING, ENCODING, VERIFYING, COMPLETED, FAILED, CANCELLED, SKIPPED
    val progressPercent: Int = 0,
    val currentFps: Double = 0.0,
    val intermediatePath: String? = null,
    val finalOutputPath: String? = null,
    val errorMessage: String? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null
)
