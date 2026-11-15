package com.unidownco.domain.models

import com.unidownco.core.common.Platform

data class DownloadItem(
    val id: Long = 0L,
    val url: String,
    val platform: Platform,
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
    val downloadStatus: DownloadStatus = DownloadStatus.PENDING,
    val encodingStatus: EncodingStatus = EncodingStatus.NOT_STARTED,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)
