package com.unidownco.domain.models

enum class DownloadStatus {
    PENDING,
    FETCHING_METADATA,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED;

    val isActive: Boolean
        get() = this == PENDING || this == FETCHING_METADATA || this == DOWNLOADING

    val isFinished: Boolean
        get() = this == COMPLETED || this == FAILED || this == CANCELLED
}

enum class EncodingStatus {
    NOT_STARTED,
    ANALYZING,
    ENCODING,
    COMPLETED,
    FAILED,
    SKIPPED
}
