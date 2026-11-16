package com.unidownco.engine.inspector.model

/**
 * High-level executive summary of the media file's primary characteristics.
 */
data class MediaOverview(
    val filename: String,
    val fileExtension: String,
    val containerFormat: String,
    val fileSizeBytes: Long,
    val durationSeconds: Long,
    val overallBitrateKbps: Long? = null,
    val primaryResolution: String? = null,
    val primaryFrameRate: Double? = null,
    val primaryVideoCodec: String? = null,
    val primaryAudioCodec: String? = null,
    val hdrClassification: HdrClassification = HdrClassification.UNKNOWN,
    val sha256Digest: String? = null
)

/**
 * Root immutable data model representing the complete technical inspection of a media file.
 */
data class MediaInspectionReport(
    val overview: MediaOverview,
    val videoStreams: List<VideoStreamDetails> = emptyList(),
    val audioStreams: List<AudioStreamDetails> = emptyList(),
    val subtitleStreams: List<SubtitleStreamDetails> = emptyList(),
    val hdrDetails: HdrColorDetails,
    val containerDetails: ContainerDetails,
    val deviceCompatibility: DeviceCompatibilityDetails? = null,
    val warnings: List<InspectionWarning> = emptyList(),
    val errors: List<InspectionError> = emptyList(),
    val isComplete: Boolean = true,
    val inspectionTimestamp: Long = System.currentTimeMillis(),
    val appVersion: String = "1.0.0"
) {
    val hasVideo: Boolean get() = videoStreams.isNotEmpty()
    val hasAudio: Boolean get() = audioStreams.isNotEmpty()
    val hasSubtitles: Boolean get() = subtitleStreams.isNotEmpty()
    val isGenuineHdr10: Boolean get() = hdrDetails.classification == HdrClassification.GENUINE_HDR10
    val totalTrackCount: Int get() = videoStreams.size + audioStreams.size + subtitleStreams.size
}
