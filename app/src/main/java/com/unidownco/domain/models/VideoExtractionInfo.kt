package com.unidownco.domain.models

import com.unidownco.core.common.Platform

data class FormatOption(
    val formatId: String,
    val resolution: String,      // e.g. "1080p", "720p", "4K"
    val width: Int = 1920,
    val height: Int = 1080,
    val fps: Int = 30,
    val videoCodec: String = "h264",
    val audioCodec: String? = "aac",
    val container: String = "mp4",
    val estimatedSizeBytes: Long = 0L,
    val videoUrl: String,
    val audioUrl: String? = null, // Set if video and audio streams are separate (DASH/YouTube)
    val isBestQuality: Boolean = false
)

data class VideoExtractionInfo(
    val url: String,
    val platform: Platform,
    val title: String,
    val author: String = "",
    val durationSeconds: Long = 0L,
    val thumbnailUrl: String? = null,
    val formats: List<FormatOption> = emptyList(),
    val rawJsonMetadata: String? = null
)
