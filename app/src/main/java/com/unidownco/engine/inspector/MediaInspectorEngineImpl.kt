package com.unidownco.engine.inspector

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.unidownco.core.logging.UniLog
import com.unidownco.engine.hardware.HardwareCapabilityRegistry
import com.unidownco.engine.inspector.classifier.HdrClassificationEngine
import com.unidownco.engine.inspector.classifier.HdrClassificationEngineImpl
import com.unidownco.engine.inspector.evaluator.DeviceCompatibilityEvaluator
import com.unidownco.engine.inspector.evaluator.DeviceCompatibilityEvaluatorImpl
import com.unidownco.engine.inspector.model.AudioStreamDetails
import com.unidownco.engine.inspector.model.BoxNode
import com.unidownco.engine.inspector.model.ContainerDetails
import com.unidownco.engine.inspector.model.DeviceCompatibilityDetails
import com.unidownco.engine.inspector.model.FrameRateMode
import com.unidownco.engine.inspector.model.HdrClassification
import com.unidownco.engine.inspector.model.HdrColorDetails
import com.unidownco.engine.inspector.model.InspectionError
import com.unidownco.engine.inspector.model.InspectionWarning
import com.unidownco.engine.inspector.model.MediaInspectionReport
import com.unidownco.engine.inspector.model.MediaOverview
import com.unidownco.engine.inspector.model.SubtitleStreamDetails
import com.unidownco.engine.inspector.model.VideoStreamDetails
import com.unidownco.engine.inspector.model.WarningSeverity
import com.unidownco.engine.inspector.source.ContentUriMediaSource
import com.unidownco.engine.inspector.source.LocalFileMediaSource
import com.unidownco.engine.inspector.source.MediaSource
import com.unidownco.engine.inspector.source.MediaSourceResolver
import com.unidownco.engine.parser.HevcBitstreamParser
import com.unidownco.engine.parser.HevcBitstreamReport
import com.unidownco.engine.parser.Mp4MetadataParser
import com.unidownco.engine.parser.Mp4MetadataReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Default implementation of [MediaInspectorEngine].
 *
 * Provides safe, multi-source, multi-track bounded inspection across:
 * 1. Container structure ('moov', 'trak', 'stsd', 'colr', 'mdcv', 'clli', 'hvcC', 'avcC')
 * 2. Media framework tracks via [MediaExtractor] and [MediaMetadataRetriever]
 * 3. Timing, nominal frame rate, average frame rate, and VFR detection
 * 4. HEVC bitstream verification (SPS VUI colorimetry, SEI 137 MDCV, SEI 144 CLLI)
 * 5. Authoritative HDR classification via [HdrClassificationEngine]
 * 6. Silicon device compatibility evaluation via [DeviceCompatibilityEvaluator]
 */
class MediaInspectorEngineImpl(
    private val classificationEngine: HdrClassificationEngine = HdrClassificationEngineImpl(),
    private val compatibilityEvaluator: DeviceCompatibilityEvaluator = DeviceCompatibilityEvaluatorImpl()
) : MediaInspectorEngine {

    companion object {
        private const val TAG = "MediaInspectorEngine"
        private const val MAX_SHA256_FILE_SIZE = 32L * 1024L * 1024L // 32 MB
        private const val MAX_VFR_PROBE_SAMPLES = 90
    }

    override suspend fun inspect(file: File): Result<MediaInspectionReport> = inspect(file, null)

    override suspend fun inspect(file: File, context: Context?): Result<MediaInspectionReport> = withContext(Dispatchers.IO) {
        val sourceResult = MediaSourceResolver.resolve(file)
        val source = sourceResult.getOrElse { return@withContext Result.failure(it) }
        source.use { s ->
            inspect(s, context)
        }
    }

    override suspend fun inspect(uri: Uri, context: Context): Result<MediaInspectionReport> = withContext(Dispatchers.IO) {
        val sourceResult = MediaSourceResolver.resolve(uri, context)
        val source = sourceResult.getOrElse { return@withContext Result.failure(it) }
        source.use { s ->
            inspect(s, context)
        }
    }

    override suspend fun inspect(source: MediaSource): Result<MediaInspectionReport> = inspect(source, null)

    override suspend fun inspect(source: MediaSource, context: Context?): Result<MediaInspectionReport> = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()

        val warnings = mutableListOf<InspectionWarning>()
        val errors = mutableListOf<InspectionError>()

        val displayName = source.displayName
        val fileExtension = displayName.substringAfterLast('.', "").lowercase()
        val fileSizeBytes = source.lengthBytes

        if (fileSizeBytes == 0L) {
            val err = InspectionError(
                code = "EMPTY_FILE",
                message = "Media source has 0 bytes: $displayName",
                isMalformed = true
            )
            return@withContext Result.success(
                buildEmptyReport(displayName, fileExtension, listOf(err))
            )
        }

        // 1. Container parsing via SeekableDataSource (pure Kotlin, works everywhere)
        val mp4Report = runCatching {
            source.openSeekable().use { seekable ->
                Mp4MetadataParser.parse(seekable)
            }
        }.getOrElse { e ->
            UniLog.w(TAG, "Mp4MetadataParser failed: ${e.message}")
            null
        }

        coroutineContext.ensureActive()

        // 2. Framework-level extraction via MediaExtractor & MediaMetadataRetriever
        val frameworkData = inspectWithFramework(source)

        coroutineContext.ensureActive()

        // 3. Synthesize video, audio, and subtitle streams
        val videoStreams = resolveVideoStreams(source, mp4Report, frameworkData)
        val audioStreams = resolveAudioStreams(source, mp4Report, frameworkData)
        val subtitleStreams = resolveSubtitleStreams(source, mp4Report, frameworkData)

        // 4. Bitstream verification (HEVC SPS & SEIs)
        val primaryVideo = videoStreams.firstOrNull()
        val isHevc = primaryVideo?.codecMime?.equals("video/hevc", ignoreCase = true) == true ||
            primaryVideo?.codecFourCc in listOf("hvc1", "hev1") ||
            mp4Report?.hvcCBox != null

        val bitstreamReport = if (isHevc) {
            resolveHevcBitstream(mp4Report, frameworkData)
        } else {
            null
        }

        coroutineContext.ensureActive()

        // 5. Container details
        val containerFormat = when {
            mp4Report?.hasMoov == true -> "MP4 (ISOBMFF)"
            source.mimeType != null -> source.mimeType!!
            fileExtension.isNotBlank() -> fileExtension.uppercase()
            else -> "Unknown Container"
        }

        val containerDetails = ContainerDetails(
            containerFormat = containerFormat,
            majorBrand = mp4Report?.ftypBox?.majorBrand,
            minorVersion = mp4Report?.ftypBox?.minorVersion,
            compatibleBrands = mp4Report?.ftypBox?.compatibleBrands ?: emptyList(),
            durationMs = frameworkData.durationMs ?: mp4Report?.trackHeaders?.maxOfOrNull { it.duration },
            totalTracks = videoStreams.size + audioStreams.size + subtitleStreams.size,
            videoTrackCount = videoStreams.size,
            audioTrackCount = audioStreams.size,
            subtitleTrackCount = subtitleStreams.size,
            boxHierarchy = mp4Report?.boxHierarchy ?: emptyList(),
            rawMimeType = source.mimeType
        )

        // 6. Dedicated HDR Classification via HdrClassificationEngine
        val classificationResult = classificationEngine.evaluate(mp4Report, bitstreamReport, primaryVideo)
        val hdrDetails = classificationResult.hdrColorDetails
        warnings.addAll(classificationResult.warnings)
        errors.addAll(classificationResult.errors)

        // Collect container warnings
        mp4Report?.warnings?.forEach { w ->
            warnings.add(
                InspectionWarning(
                    severity = WarningSeverity.INFO,
                    code = "CONTAINER_WARNING",
                    message = w,
                    technicalImpact = "Optional box parsing skipped or container specification deviation."
                )
            )
        }

        // Collect bitstream warnings
        bitstreamReport?.warnings?.forEach { w ->
            warnings.add(
                InspectionWarning(
                    severity = WarningSeverity.INFO,
                    code = "BITSTREAM_WARNING",
                    message = w,
                    technicalImpact = "Parameter set or SEI parsing deviation."
                )
            )
        }

        // Check if no media tracks were found
        val isRecognized = videoStreams.isNotEmpty() || audioStreams.isNotEmpty() || (mp4Report?.hasMoov == true)
        if (!isRecognized) {
            errors.add(
                InspectionError(
                    code = "UNRECOGNIZED_MEDIA_FORMAT",
                    message = "No recognizable audio or video tracks detected in $displayName",
                    isUnsupportedFormat = true
                )
            )
        }

        // Compute bounded SHA-256 digest
        val sha256 = computeSha256(source)

        // Duration calculation
        val durationMs = containerDetails.durationMs ?: 0L
        val durationSeconds = durationMs / 1000L

        // Primary track overviews
        val primaryResolution = if (primaryVideo != null && primaryVideo.width > 0 && primaryVideo.height > 0) {
            "${primaryVideo.width}x${primaryVideo.height}"
        } else null

        val primaryFrameRate = primaryVideo?.averageFrameRate ?: primaryVideo?.nominalFrameRate
        val primaryVideoCodec = primaryVideo?.codecName
        val primaryAudioCodec = audioStreams.firstOrNull()?.codecName

        val overview = MediaOverview(
            filename = displayName,
            fileExtension = fileExtension,
            containerFormat = containerFormat,
            fileSizeBytes = fileSizeBytes,
            durationSeconds = durationSeconds,
            overallBitrateKbps = frameworkData.bitrateKbps,
            primaryResolution = primaryResolution,
            primaryFrameRate = primaryFrameRate,
            primaryVideoCodec = primaryVideoCodec,
            primaryAudioCodec = primaryAudioCodec,
            hdrClassification = hdrDetails.classification,
            sha256Digest = sha256
        )

        // Interim report for device compatibility evaluation
        val interimReport = MediaInspectionReport(
            overview = overview,
            videoStreams = videoStreams,
            audioStreams = audioStreams,
            subtitleStreams = subtitleStreams,
            hdrDetails = hdrDetails,
            containerDetails = containerDetails,
            deviceCompatibility = null,
            warnings = warnings,
            errors = errors,
            isComplete = errors.isEmpty()
        )

        // 7. Device Compatibility Evaluation via DeviceCompatibilityEvaluator
        val activeContext = context ?: (source as? ContentUriMediaSource)?.context
        val deviceCompatibility = if (activeContext != null) {
            val capabilities = HardwareCapabilityRegistry.getCapabilities()
            compatibilityEvaluator.evaluate(interimReport, capabilities)
        } else {
            null
        }

        val finalReport = interimReport.copy(deviceCompatibility = deviceCompatibility)
        Result.success(finalReport)
    }

    private fun buildEmptyReport(
        displayName: String,
        fileExtension: String,
        errors: List<InspectionError>
    ): MediaInspectionReport {
        return MediaInspectionReport(
            overview = MediaOverview(
                filename = displayName,
                fileExtension = fileExtension,
                containerFormat = "Unknown",
                fileSizeBytes = 0L,
                durationSeconds = 0L,
                hdrClassification = HdrClassification.UNKNOWN
            ),
            hdrDetails = HdrColorDetails(classification = HdrClassification.UNKNOWN),
            containerDetails = ContainerDetails(containerFormat = "Unknown"),
            errors = errors,
            isComplete = false
        )
    }

    private data class FrameworkTrack(
        val index: Int,
        val mime: String,
        val format: MediaFormat
    )

    private data class FrameworkData(
        val tracks: List<FrameworkTrack> = emptyList(),
        val durationMs: Long? = null,
        val bitrateKbps: Long? = null,
        val rotationDegrees: Int = 0,
        val averageFps: Double? = null,
        val isVfr: Boolean? = null,
        val frameRateMode: FrameRateMode = FrameRateMode.UNKNOWN_FRAME_RATE
    )

    private fun inspectWithFramework(source: MediaSource): FrameworkData {
        val tracks = mutableListOf<FrameworkTrack>()
        var durationMs: Long? = null
        var bitrateKbps: Long? = null
        var rotationDegrees = 0
        var avgFps: Double? = null
        var isVfr: Boolean? = null
        var frameRateMode = FrameRateMode.UNKNOWN_FRAME_RATE

        // MediaMetadataRetriever
        try {
            val retriever = MediaMetadataRetriever()
            try {
                setRetrieverDataSource(retriever, source)
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
                if (bitrate != null && bitrate > 0) {
                    bitrateKbps = bitrate / 1000L
                }
                rotationDegrees = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            } finally {
                retriever.release()
            }
        } catch (e: Exception) {
            UniLog.w(TAG, "MediaMetadataRetriever inspection failed: ${e.message}")
        }

        // MediaExtractor
        try {
            val extractor = MediaExtractor()
            try {
                setExtractorDataSource(extractor, source)
                val trackCount = extractor.trackCount
                var primaryVideoTrackIndex = -1

                for (i in 0 until trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    tracks.add(FrameworkTrack(i, mime, format))
                    if (mime.startsWith("video/") && primaryVideoTrackIndex == -1) {
                        primaryVideoTrackIndex = i
                    }
                }

                // Sample bounded presentation timestamps for VFR detection
                if (primaryVideoTrackIndex != -1) {
                    val timingResult = probeFrameRateAndVfr(extractor, primaryVideoTrackIndex)
                    avgFps = timingResult.averageFps
                    isVfr = timingResult.isVfr
                    frameRateMode = timingResult.frameRateMode
                }
            } finally {
                extractor.release()
            }
        } catch (e: Exception) {
            UniLog.w(TAG, "MediaExtractor inspection failed: ${e.message}")
        }

        return FrameworkData(
            tracks = tracks,
            durationMs = durationMs,
            bitrateKbps = bitrateKbps,
            rotationDegrees = rotationDegrees,
            averageFps = avgFps,
            isVfr = isVfr,
            frameRateMode = frameRateMode
        )
    }

    private fun setExtractorDataSource(extractor: MediaExtractor, source: MediaSource) {
        if (source is LocalFileMediaSource) {
            extractor.setDataSource(source.file.absolutePath)
        } else {
            val fd = source.getFileDescriptor()
            val len = source.fileDescriptorLength
            if (len > 0) {
                extractor.setDataSource(fd, source.fileDescriptorOffset, len)
            } else {
                extractor.setDataSource(fd)
            }
        }
    }

    private fun setRetrieverDataSource(retriever: MediaMetadataRetriever, source: MediaSource) {
        if (source is LocalFileMediaSource) {
            retriever.setDataSource(source.file.absolutePath)
        } else {
            val fd = source.getFileDescriptor()
            val len = source.fileDescriptorLength
            if (len > 0) {
                retriever.setDataSource(fd, source.fileDescriptorOffset, len)
            } else {
                retriever.setDataSource(fd)
            }
        }
    }

    private data class TimingProbeResult(
        val averageFps: Double?,
        val isVfr: Boolean?,
        val frameRateMode: FrameRateMode
    )

    private fun probeFrameRateAndVfr(extractor: MediaExtractor, trackIndex: Int): TimingProbeResult {
        return try {
            extractor.selectTrack(trackIndex)
            val rawTimestampsUs = mutableListOf<Long>()
            var sampleCount = 0

            while (sampleCount < MAX_VFR_PROBE_SAMPLES) {
                val sampleTime = extractor.sampleTime
                if (sampleTime < 0) break
                rawTimestampsUs.add(sampleTime)
                sampleCount++
                if (!extractor.advance()) break
            }
            extractor.unselectTrack(trackIndex)

            if (rawTimestampsUs.size < 2) {
                return TimingProbeResult(null, null, FrameRateMode.UNKNOWN_FRAME_RATE)
            }

            // Filter out negative and duplicate presentation timestamps, ensuring monotonic order
            val validTimestamps = rawTimestampsUs.filter { it >= 0 }.distinct().sorted()
            if (validTimestamps.size < 2) {
                return TimingProbeResult(null, null, FrameRateMode.UNKNOWN_FRAME_RATE)
            }

            val totalDurationUs = validTimestamps.last() - validTimestamps.first()
            val avgFps = if (totalDurationUs > 0) {
                val rawFps = ((validTimestamps.size - 1) * 1_000_000.0) / totalDurationUs
                Math.round(rawFps * 1000.0) / 1000.0
            } else null

            val deltas = mutableListOf<Long>()
            for (k in 1 until validTimestamps.size) {
                val d = validTimestamps[k] - validTimestamps[k - 1]
                if (d > 0) deltas.add(d)
            }

            if (deltas.size < 5) {
                return TimingProbeResult(avgFps, null, FrameRateMode.UNKNOWN_FRAME_RATE)
            }

            // Filter out large discontinuity/gap outliers (> 5x median) from regular frame cadence
            val sortedDeltas = deltas.sorted()
            val medianDelta = sortedDeltas[sortedDeltas.size / 2]
            val normalCadenceDeltas = deltas.filter { it <= medianDelta * 5 }

            if (normalCadenceDeltas.size < 5) {
                return TimingProbeResult(avgFps, null, FrameRateMode.UNKNOWN_FRAME_RATE)
            }

            val mean = normalCadenceDeltas.average()
            val variance = normalCadenceDeltas.map { (it - mean) * (it - mean) }.average()
            val stdDev = Math.sqrt(variance)
            val cv = if (mean > 0) stdDev / mean else 0.0

            // Fractional cadences (29.97, 59.94) exhibit rounding jitter with CV ~ 0.005 - 0.03.
            // True variable frame rate (VFR) content has CV > 0.08.
            val (isVfr, mode) = when {
                cv <= 0.08 -> Pair(false, FrameRateMode.CONSTANT_FRAME_RATE)
                normalCadenceDeltas.size >= 25 -> Pair(true, FrameRateMode.VARIABLE_FRAME_RATE_CONFIRMED)
                else -> Pair(true, FrameRateMode.VARIABLE_FRAME_RATE_SUSPECTED)
            }

            TimingProbeResult(avgFps, isVfr, mode)
        } catch (_: Exception) {
            try { extractor.unselectTrack(trackIndex) } catch (_: Exception) {}
            TimingProbeResult(null, null, FrameRateMode.UNKNOWN_FRAME_RATE)
        }
    }

    private fun resolveVideoStreams(
        source: MediaSource,
        mp4Report: Mp4MetadataReport?,
        frameworkData: FrameworkData
    ): List<VideoStreamDetails> {
        val videoTracks = frameworkData.tracks.filter { it.mime.startsWith("video/") }

        if (videoTracks.isNotEmpty()) {
            return videoTracks.mapIndexed { idx, track ->
                val f = track.format
                val width = if (f.containsKey(MediaFormat.KEY_WIDTH)) f.getInteger(MediaFormat.KEY_WIDTH) else 0
                val height = if (f.containsKey(MediaFormat.KEY_HEIGHT)) f.getInteger(MediaFormat.KEY_HEIGHT) else 0
                val duration = if (f.containsKey(MediaFormat.KEY_DURATION)) f.getLong(MediaFormat.KEY_DURATION) / 1000L else frameworkData.durationMs
                val nominalFps = if (f.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                    runCatching { f.getInteger(MediaFormat.KEY_FRAME_RATE).toDouble() }
                        .getOrElse { runCatching { f.getFloat(MediaFormat.KEY_FRAME_RATE).toDouble() }.getOrNull() }
                } else null

                val bitrate = if (f.containsKey(MediaFormat.KEY_BIT_RATE)) {
                    f.getInteger(MediaFormat.KEY_BIT_RATE) / 1000L
                } else null

                val profile = if (f.containsKey(MediaFormat.KEY_PROFILE)) f.getInteger(MediaFormat.KEY_PROFILE) else null
                val level = if (f.containsKey(MediaFormat.KEY_LEVEL)) f.getInteger(MediaFormat.KEY_LEVEL) else null

                var bitDepth: Int? = null
                if (profile != null) {
                    if (track.mime.equals("video/hevc", ignoreCase = true) && profile == 2) {
                        bitDepth = 10
                    }
                }
                if (bitDepth == null && mp4Report?.hvcCBox != null) {
                    bitDepth = mp4Report.hvcCBox.bitDepthLuma
                }

                val colorPrimaries = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && f.containsKey(MediaFormat.KEY_COLOR_STANDARD)) {
                    mapAndroidColorStandard(f.getInteger(MediaFormat.KEY_COLOR_STANDARD))
                } else {
                    mapColorPrimaries(mp4Report?.colrBox?.colorPrimaries)
                }

                val colorTransfer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && f.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) {
                    mapAndroidColorTransfer(f.getInteger(MediaFormat.KEY_COLOR_TRANSFER))
                } else {
                    mapColorTransfer(mp4Report?.colrBox?.transferCharacteristics)
                }

                val colorRange = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && f.containsKey(MediaFormat.KEY_COLOR_RANGE)) {
                    mapAndroidColorRange(f.getInteger(MediaFormat.KEY_COLOR_RANGE))
                } else {
                    mapColorRange(mp4Report?.colrBox?.fullRangeFlag)
                }

                val rotation = if (f.containsKey(MediaFormat.KEY_ROTATION)) {
                    f.getInteger(MediaFormat.KEY_ROTATION)
                } else {
                    frameworkData.rotationDegrees
                }

                val codecName = mapVideoCodecName(track.mime, mp4Report?.videoCodecFourCc)

                VideoStreamDetails(
                    trackIndex = track.index,
                    trackId = track.index + 1,
                    codecName = codecName,
                    codecMime = track.mime,
                    codecFourCc = mp4Report?.videoCodecFourCc,
                    isDefaultTrack = idx == 0,
                    durationMs = duration,
                    width = width,
                    height = height,
                    displayWidth = if (rotation == 90 || rotation == 270) height else width,
                    displayHeight = if (rotation == 90 || rotation == 270) width else height,
                    displayAspectRatio = if (width > 0 && height > 0) calculateAspectRatio(width, height) else null,
                    nominalFrameRate = nominalFps,
                    averageFrameRate = if (idx == 0) frameworkData.averageFps else null,
                    isVariableFrameRate = if (idx == 0) frameworkData.isVfr else null,
                    frameRateMode = if (idx == 0) frameworkData.frameRateMode else FrameRateMode.UNKNOWN_FRAME_RATE,
                    bitrateKbps = bitrate,
                    bitDepth = bitDepth ?: 8,
                    profileName = mapProfileName(track.mime, profile),
                    profileIdc = profile,
                    levelName = if (level != null) "Level $level" else null,
                    levelIdc = level,
                    chromaSubsampling = "4:2:0",
                    colorPrimaries = colorPrimaries,
                    colorTransfer = colorTransfer,
                    matrixCoefficients = mapMatrixCoefficients(mp4Report?.colrBox?.matrixCoefficients),
                    colorRange = colorRange,
                    rotationDegrees = rotation
                )
            }
        }

        // Fallback to container-parsed MP4 track headers if MediaExtractor was unavailable (e.g. host test)
        if (mp4Report != null && mp4Report.trackHeaders.any { it.isVideo }) {
            return mp4Report.trackHeaders.filter { it.isVideo }.mapIndexed { idx, th ->
                val bitDepth = mp4Report.hvcCBox?.bitDepthLuma ?: 8
                val fourCc = mp4Report.videoCodecFourCc ?: (if (mp4Report.hvcCBox != null) "hvc1" else "avc1")
                val mime = if (fourCc in listOf("hvc1", "hev1")) "video/hevc" else "video/avc"

                VideoStreamDetails(
                    trackIndex = idx,
                    trackId = th.trackId,
                    codecName = mapVideoCodecName(mime, fourCc),
                    codecMime = mime,
                    codecFourCc = fourCc,
                    isDefaultTrack = idx == 0,
                    durationMs = th.duration,
                    width = th.width.toInt(),
                    height = th.height.toInt(),
                    displayWidth = th.width.toInt(),
                    displayHeight = th.height.toInt(),
                    displayAspectRatio = calculateAspectRatio(th.width.toInt(), th.height.toInt()),
                    bitDepth = bitDepth,
                    colorPrimaries = mapColorPrimaries(mp4Report.colrBox?.colorPrimaries),
                    colorTransfer = mapColorTransfer(mp4Report.colrBox?.transferCharacteristics),
                    matrixCoefficients = mapMatrixCoefficients(mp4Report.colrBox?.matrixCoefficients),
                    colorRange = mapColorRange(mp4Report.colrBox?.fullRangeFlag)
                )
            }
        }

        return emptyList()
    }

    private fun resolveAudioStreams(
        source: MediaSource,
        mp4Report: Mp4MetadataReport?,
        frameworkData: FrameworkData
    ): List<AudioStreamDetails> {
        val audioTracks = frameworkData.tracks.filter { it.mime.startsWith("audio/") }

        if (audioTracks.isNotEmpty()) {
            return audioTracks.mapIndexed { idx, track ->
                val f = track.format
                val channels = if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) f.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
                val sampleRate = if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) f.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
                val bitrate = if (f.containsKey(MediaFormat.KEY_BIT_RATE)) f.getInteger(MediaFormat.KEY_BIT_RATE) / 1000L else null
                val duration = if (f.containsKey(MediaFormat.KEY_DURATION)) f.getLong(MediaFormat.KEY_DURATION) / 1000L else frameworkData.durationMs
                val lang = if (f.containsKey(MediaFormat.KEY_LANGUAGE)) f.getString(MediaFormat.KEY_LANGUAGE) else null

                AudioStreamDetails(
                    trackIndex = track.index,
                    trackId = track.index + 1,
                    codecName = mapAudioCodecName(track.mime),
                    codecMime = track.mime,
                    isDefaultTrack = idx == 0,
                    durationMs = duration,
                    channelCount = channels,
                    channelLayout = mapChannelLayout(channels),
                    sampleRateHz = sampleRate,
                    bitrateKbps = bitrate,
                    language = lang
                )
            }
        }

        // Fallback to container audio track headers
        if (mp4Report != null && mp4Report.trackHeaders.any { it.isAudio }) {
            return mp4Report.trackHeaders.filter { it.isAudio }.mapIndexed { idx, th ->
                AudioStreamDetails(
                    trackIndex = idx,
                    trackId = th.trackId,
                    codecName = "AAC",
                    codecMime = "audio/mp4a-latm",
                    isDefaultTrack = idx == 0,
                    durationMs = th.duration,
                    channelCount = 2,
                    channelLayout = "Stereo (2.0)",
                    sampleRateHz = 44100
                )
            }
        }

        return emptyList()
    }

    private fun resolveSubtitleStreams(
        source: MediaSource,
        mp4Report: Mp4MetadataReport?,
        frameworkData: FrameworkData
    ): List<SubtitleStreamDetails> {
        val subTracks = frameworkData.tracks.filter {
            it.mime.startsWith("text/") ||
                it.mime.startsWith("application/cea-") ||
                it.mime.startsWith("application/x-subrip")
        }

        return subTracks.mapIndexed { idx, track ->
            val f = track.format
            val lang = if (f.containsKey(MediaFormat.KEY_LANGUAGE)) f.getString(MediaFormat.KEY_LANGUAGE) else null
            val duration = if (f.containsKey(MediaFormat.KEY_DURATION)) f.getLong(MediaFormat.KEY_DURATION) / 1000L else frameworkData.durationMs

            SubtitleStreamDetails(
                trackIndex = track.index,
                trackId = track.index + 1,
                codecName = mapSubtitleCodecName(track.mime),
                codecMime = track.mime,
                formatName = mapSubtitleCodecName(track.mime),
                language = lang,
                durationMs = duration
            )
        }
    }

    private fun resolveHevcBitstream(
        mp4Report: Mp4MetadataReport?,
        frameworkData: FrameworkData
    ): HevcBitstreamReport? {
        // Option A: Extract parameter sets from hvcC box
        if (mp4Report?.hvcCBox != null && mp4Report.hvcCBox.nalUnits.isNotEmpty()) {
            return HevcBitstreamParser.parseNalUnits(mp4Report.hvcCBox.nalUnits)
        }

        // Option B: Check csd-0 / csd-1 from MediaFormat
        val videoTrack = frameworkData.tracks.firstOrNull { it.mime.equals("video/hevc", ignoreCase = true) }
        if (videoTrack != null) {
            val csd0 = if (videoTrack.format.containsKey("csd-0")) videoTrack.format.getByteBuffer("csd-0") else null
            if (csd0 != null && csd0.remaining() > 0) {
                val bytes = ByteArray(csd0.remaining())
                csd0.duplicate().get(bytes)
                val nals = HevcBitstreamParser.extractAnnexBNalUnits(bytes)
                if (nals.isNotEmpty()) {
                    return HevcBitstreamParser.parseNalUnits(nals)
                }
            }
        }

        return null
    }

    private fun computeSha256(source: MediaSource): String? {
        if (source.lengthBytes !in 1L..MAX_SHA256_FILE_SIZE) {
            return null
        }
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            source.openInputStream().use { input ->
                val buf = ByteArray(8192)
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    digest.update(buf, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    // --- Color, Codec, and Formatting Helpers ---

    private fun mapVideoCodecName(mime: String, fourCc: String?): String = when {
        mime.equals("video/hevc", ignoreCase = true) || fourCc in listOf("hvc1", "hev1") -> "HEVC / H.265"
        mime.equals("video/avc", ignoreCase = true) || fourCc == "avc1" -> "AVC / H.264"
        mime.equals("video/x-vnd.on2.vp9", ignoreCase = true) || fourCc == "vp09" -> "VP9"
        mime.equals("video/av01", ignoreCase = true) || fourCc == "av01" -> "AV1"
        mime.equals("video/mp4v-es", ignoreCase = true) -> "MPEG-4 Part 2"
        else -> fourCc ?: mime
    }

    private fun mapAudioCodecName(mime: String): String = when (mime.lowercase()) {
        "audio/mp4a-latm", "audio/mp4" -> "AAC"
        "audio/opus" -> "Opus"
        "audio/mpeg" -> "MP3"
        "audio/flac" -> "FLAC"
        "audio/vorbis" -> "Vorbis"
        "audio/ac3" -> "Dolby Digital (AC-3)"
        "audio/eac3" -> "Dolby Digital Plus (E-AC-3)"
        else -> mime.substringAfter('/')
    }

    private fun mapSubtitleCodecName(mime: String): String = when (mime.lowercase()) {
        "text/3gpp-tt" -> "Timed Text (tx3g)"
        "application/x-subrip", "text/x-subrip" -> "SubRip (SRT)"
        "text/vtt" -> "WebVTT"
        else -> mime
    }

    private fun mapChannelLayout(channels: Int): String = when (channels) {
        1 -> "Mono (1.0)"
        2 -> "Stereo (2.0)"
        6 -> "5.1 Surround (6 ch)"
        8 -> "7.1 Surround (8 ch)"
        else -> "$channels Channels"
    }

    private fun calculateAspectRatio(width: Int, height: Int): String {
        val gcd = gcd(width, height)
        val wRatio = width / gcd
        val hRatio = height / gcd
        return if (wRatio == 16 && hRatio == 9) "16:9"
        else if (wRatio == 4 && hRatio == 3) "4:3"
        else if (wRatio == 1 && hRatio == 1) "1:1"
        else if (wRatio == 9 && hRatio == 16) "9:16"
        else "$wRatio:$hRatio"
    }

    private fun gcd(a: Int, b: Int): Int {
        var x = Math.abs(a)
        var y = Math.abs(b)
        while (y != 0) {
            val t = y
            y = x % y
            x = t
        }
        return if (x == 0) 1 else x
    }

    private fun mapProfileName(mime: String, profile: Int?): String? {
        if (profile == null) return null
        return if (mime.equals("video/hevc", ignoreCase = true)) {
            when (profile) {
                1 -> "Main"
                2 -> "Main 10"
                3 -> "Main Still Picture"
                else -> "Profile $profile"
            }
        } else if (mime.equals("video/avc", ignoreCase = true)) {
            when (profile) {
                1 -> "Baseline"
                2 -> "Main"
                8 -> "High"
                else -> "Profile $profile"
            }
        } else {
            "Profile $profile"
        }
    }

    private fun mapColorPrimaries(idc: Int?): String? = when (idc) {
        1 -> "BT.709"
        4 -> "FCC 47 CFR 73.682"
        5 -> "BT.601 PAL"
        6 -> "BT.601 NTSC"
        7 -> "SMPTE 240M"
        9 -> "BT.2020"
        12 -> "DCI-P3 (SMPTE EG 432-1)"
        null -> null
        else -> "Primaries ($idc)"
    }

    private fun mapColorTransfer(idc: Int?): String? = when (idc) {
        1 -> "BT.709"
        4 -> "Gamma 2.2"
        5 -> "Gamma 2.8"
        6 -> "SMPTE 170M"
        8 -> "Linear"
        13 -> "sRGB"
        16 -> "SMPTE ST 2084 (PQ)"
        18 -> "ARIB STD-B67 (HLG)"
        null -> null
        else -> "Transfer ($idc)"
    }

    private fun mapMatrixCoefficients(idc: Int?): String? = when (idc) {
        0 -> "Identity (RGB)"
        1 -> "BT.709"
        6 -> "BT.601"
        9 -> "BT.2020 non-constant"
        10 -> "BT.2020 constant"
        null -> null
        else -> "Matrix ($idc)"
    }

    private fun mapColorRange(fullRange: Boolean?): String? = when (fullRange) {
        true -> "Full Range (0-255 / 0-1023)"
        false -> "Limited / Studio Range (16-235 / 64-940)"
        null -> null
    }

    private fun mapAndroidColorStandard(standard: Int): String = when (standard) {
        MediaFormat.COLOR_STANDARD_BT2020 -> "BT.2020"
        MediaFormat.COLOR_STANDARD_BT709 -> "BT.709"
        MediaFormat.COLOR_STANDARD_BT601_PAL -> "BT.601 PAL"
        MediaFormat.COLOR_STANDARD_BT601_NTSC -> "BT.601 NTSC"
        else -> "Standard ($standard)"
    }

    private fun mapAndroidColorTransfer(transfer: Int): String = when (transfer) {
        MediaFormat.COLOR_TRANSFER_ST2084 -> "SMPTE ST 2084 (PQ)"
        MediaFormat.COLOR_TRANSFER_HLG -> "ARIB STD-B67 (HLG)"
        MediaFormat.COLOR_TRANSFER_SDR_VIDEO -> "BT.709"
        MediaFormat.COLOR_TRANSFER_LINEAR -> "Linear"
        else -> "Transfer ($transfer)"
    }

    private fun mapAndroidColorRange(range: Int): String = when (range) {
        MediaFormat.COLOR_RANGE_FULL -> "Full Range (0-255 / 0-1023)"
        MediaFormat.COLOR_RANGE_LIMITED -> "Limited / Studio Range (16-235 / 64-940)"
        else -> "Range ($range)"
    }
}
