package com.unidownco.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "video_metadata",
    foreignKeys = [
        ForeignKey(
            entity = DownloadEntity::class,
            parentColumns = ["id"],
            childColumns = ["downloadId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["downloadId"])]
)
data class VideoMetadataEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val downloadId: Long,
    val container: String = "mp4",
    val videoCodec: String = "h264",
    val audioCodec: String = "aac",
    val width: Int = 1920,
    val height: Int = 1080,
    val frameRate: Double = 30.0,
    val bitDepth: Int = 8,
    val pixelFormat: String = "yuv420p",
    val colorPrimaries: String = "bt709",
    val transferCharacteristics: String = "bt709",
    val matrixCoefficients: String = "bt709",
    val hdrType: String = "SDR", // SDR, HDR10, HDR10+, HLG, DOLBY_VISION
    val audioChannels: Int = 2,
    val audioSampleRate: Int = 48000,
    val rawAnalysisJson: String? = null
)
