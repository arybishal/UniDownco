package com.unidownco.feature.inspector

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unidownco.core.common.Formatters
import com.unidownco.core.logging.UniLog
import com.unidownco.engine.inspector.MediaInspectorEngine
import com.unidownco.engine.inspector.MediaInspectorEngineImpl
import com.unidownco.engine.inspector.model.BoxNode
import com.unidownco.engine.inspector.model.HdrClassification
import com.unidownco.engine.inspector.model.MediaInspectionReport
import com.unidownco.engine.inspector.source.MediaSource
import com.unidownco.engine.inspector.source.MediaSourceResolver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface MediaInspectorUiState {
    data object Idle : MediaInspectorUiState
    data class Loading(val sourceName: String) : MediaInspectorUiState
    data class Success(val report: MediaInspectionReport) : MediaInspectorUiState
    data class Error(
        val message: String,
        val technicalDetails: String? = null,
        val canRetry: Boolean = true
    ) : MediaInspectorUiState
}

class MediaInspectorViewModel(
    application: Application,
    private val inspectorEngine: MediaInspectorEngine = MediaInspectorEngineImpl(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MediaInspectorViewModel"
    }

    private val _uiState = MutableStateFlow<MediaInspectorUiState>(MediaInspectorUiState.Idle)
    val uiState: StateFlow<MediaInspectorUiState> = _uiState.asStateFlow()

    fun reset() {
        _uiState.value = MediaInspectorUiState.Idle
    }

    fun inspectFile(file: File, context: Context = getApplication()) {
        viewModelScope.launch {
            _uiState.value = MediaInspectorUiState.Loading(file.name)
            try {
                val result = withContext(ioDispatcher) {
                    inspectorEngine.inspect(file, context)
                }
                result.fold(
                    onSuccess = { report ->
                        UniLog.i(TAG, "File inspection succeeded: ${file.name}, HDR=${report.hdrDetails.classification}")
                        _uiState.value = MediaInspectorUiState.Success(report)
                    },
                    onFailure = { error ->
                        UniLog.e(TAG, "File inspection failed: ${file.name}", error)
                        _uiState.value = MediaInspectorUiState.Error(
                            message = error.message ?: "Failed to inspect file.",
                            technicalDetails = error.stackTraceToString(),
                            canRetry = true
                        )
                    }
                )
            } catch (t: Throwable) {
                UniLog.e(TAG, "Unexpected error inspecting file: ${file.name}", t)
                _uiState.value = MediaInspectorUiState.Error(
                    message = t.message ?: "Unexpected error occurred.",
                    technicalDetails = t.stackTraceToString(),
                    canRetry = true
                )
            }
        }
    }

    fun inspectUri(uri: Uri, context: Context = getApplication()) {
        viewModelScope.launch {
            val displayName = uri.lastPathSegment ?: "Selected Media"
            _uiState.value = MediaInspectorUiState.Loading(displayName)
            try {
                val sourceResult = withContext(ioDispatcher) {
                    MediaSourceResolver.resolve(uri, context)
                }
                sourceResult.fold(
                    onSuccess = { mediaSource ->
                        inspectSourceInternal(mediaSource, context)
                    },
                    onFailure = { error ->
                        UniLog.e(TAG, "Failed to resolve URI: $uri", error)
                        _uiState.value = MediaInspectorUiState.Error(
                            message = "Could not open selected media: ${error.message}",
                            technicalDetails = error.stackTraceToString(),
                            canRetry = false
                        )
                    }
                )
            } catch (t: Throwable) {
                UniLog.e(TAG, "Unexpected error resolving URI: $uri", t)
                _uiState.value = MediaInspectorUiState.Error(
                    message = t.message ?: "Failed to open media URI.",
                    technicalDetails = t.stackTraceToString(),
                    canRetry = false
                )
            }
        }
    }

    fun inspectSource(source: MediaSource, context: Context = getApplication()) {
        viewModelScope.launch {
            _uiState.value = MediaInspectorUiState.Loading(source.displayName)
            inspectSourceInternal(source, context)
        }
    }

    private suspend fun inspectSourceInternal(source: MediaSource, context: Context) {
        val result = withContext(ioDispatcher) {
            inspectorEngine.inspect(source, context)
        }
        result.fold(
            onSuccess = { report ->
                UniLog.i(TAG, "Source inspection succeeded: ${source.displayName}, HDR=${report.hdrDetails.classification}")
                _uiState.value = MediaInspectorUiState.Success(report)
            },
            onFailure = { error ->
                UniLog.e(TAG, "Source inspection failed: ${source.displayName}", error)
                _uiState.value = MediaInspectorUiState.Error(
                    message = error.message ?: "Failed to inspect media source.",
                    technicalDetails = error.stackTraceToString(),
                    canRetry = true
                )
            }
        )
    }

    fun generateShareableSummary(): String {
        val current = _uiState.value
        if (current !is MediaInspectorUiState.Success) return ""
        val r = current.report

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val formattedDate = dateFormat.format(Date(r.inspectionTimestamp))

        val sb = StringBuilder()
        sb.appendLine("==================================================")
        sb.appendLine("        UNIDOWNCO MEDIA INSPECTION REPORT")
        sb.appendLine("==================================================")
        sb.appendLine("Date/Time:      $formattedDate")
        sb.appendLine("Engine Version: ${r.appVersion}")
        sb.appendLine()

        // Overview
        sb.appendLine("--- FILE OVERVIEW ---")
        sb.appendLine("Filename:       ${r.overview.filename}")
        sb.appendLine("Container:      ${r.overview.containerFormat}")
        sb.appendLine("File Size:      ${Formatters.formatBytes(r.overview.fileSizeBytes)} (${r.overview.fileSizeBytes} bytes)")
        sb.appendLine("Duration:       ${formatDuration(r.overview.durationSeconds)}")
        if (r.overview.overallBitrateKbps != null) {
            sb.appendLine("Overall Bitrate:${r.overview.overallBitrateKbps} kbps")
        }
        sb.appendLine("HDR Verdict:    ${r.hdrDetails.classification.name} (${r.hdrDetails.classification.label})")
        sb.appendLine()

        // Video Streams
        if (r.videoStreams.isNotEmpty()) {
            sb.appendLine("--- VIDEO STREAMS (${r.videoStreams.size}) ---")
            r.videoStreams.forEachIndexed { idx, v ->
                sb.appendLine("Stream #$idx [Track ID ${v.trackId}]:")
                sb.appendLine("  Codec:        ${v.codecName}${v.codecFourCc?.let { " ($it)" } ?: ""}")
                sb.appendLine("  Profile/Lvl:  ${v.profileName ?: "N/A"}${v.levelName?.let { " @ Level $it" } ?: ""}")
                sb.appendLine("  Resolution:   ${v.width}x${v.height} (DAR ${v.displayAspectRatio ?: "N/A"}, PAR ${v.pixelAspectRatio ?: "N/A"})")
                val nomFpsStr = v.nominalFrameRate?.let { String.format(Locale.US, "%.3f", it) } ?: "N/A"
                val avgFpsStr = v.averageFrameRate?.let { String.format(Locale.US, "%.3f", it) } ?: "N/A"
                sb.appendLine("  Frame Rate:   $nomFpsStr FPS (Avg: $avgFpsStr FPS)")
                sb.appendLine("  Timing Mode:  ${v.frameRateMode.label}")
                sb.appendLine("  Bit Depth:    ${v.bitDepth ?: "N/A"}-bit")
                sb.appendLine("  Color Prim.:  ${v.colorPrimaries ?: "Unspecified"}")
                sb.appendLine("  Transfer:     ${v.colorTransfer ?: "Unspecified"}")
                sb.appendLine("  Matrix Coeff: ${v.matrixCoefficients ?: "Unspecified"}")
                sb.appendLine("  Color Range:  ${v.colorRange ?: "Unspecified"}")
                sb.appendLine("  Rotation:     ${v.rotationDegrees}°")
                if (v.bitrateKbps != null) {
                    sb.appendLine("  Bitrate:      ${v.bitrateKbps} kbps")
                }
            }
            sb.appendLine()
        }

        // Audio Streams
        if (r.audioStreams.isNotEmpty()) {
            sb.appendLine("--- AUDIO STREAMS (${r.audioStreams.size}) ---")
            r.audioStreams.forEachIndexed { idx, a ->
                sb.appendLine("Stream #$idx [Track ID ${a.trackId}]:")
                sb.appendLine("  Codec:        ${a.codecName}${a.codecFourCc?.let { " ($it)" } ?: ""}")
                if (a.profileName != null) {
                    sb.appendLine("  Profile:      ${a.profileName}")
                }
                sb.appendLine("  Channels:     ${a.channelCount} (${a.channelLayout ?: "Stereo"})")
                sb.appendLine("  Sample Rate:  ${a.sampleRateHz} Hz")
                if (a.bitrateKbps != null) {
                    sb.appendLine("  Bitrate:      ${a.bitrateKbps} kbps")
                }
                sb.appendLine("  Language:     ${a.language ?: "und"}")
            }
            sb.appendLine()
        }

        // Subtitle Streams
        if (r.subtitleStreams.isNotEmpty()) {
            sb.appendLine("--- SUBTITLE STREAMS (${r.subtitleStreams.size}) ---")
            r.subtitleStreams.forEachIndexed { idx, s ->
                sb.appendLine("Stream #$idx [Track ID ${s.trackId}]: Format=${s.formatName}, Language=${s.language ?: "und"}")
            }
            sb.appendLine()
        }

        // HDR & Color Science
        sb.appendLine("--- HDR & COLOR SCIENCE ---")
        sb.appendLine("Classification: ${r.hdrDetails.classification.label}")
        sb.appendLine("Primaries:      ${r.hdrDetails.colorPrimariesName ?: "N/A"}")
        sb.appendLine("Transfer:       ${r.hdrDetails.colorTransferName ?: "N/A"}")
        sb.appendLine("Matrix:         ${r.hdrDetails.matrixCoefficientsName ?: "N/A"}")
        sb.appendLine("Range:          ${r.hdrDetails.colorRangeName ?: "N/A"}")
        sb.appendLine("Container Box:  ${if (r.hdrDetails.containerAtomVerified) "Verified (colr)" else "Not Present"}")
        sb.appendLine("Bitstream SEI:  ${if (r.hdrDetails.bitstreamSeiVerified) "Verified (Prefix SEI)" else "Not Present"}")

        val md = r.hdrDetails.masteringDisplay
        if (md != null) {
            sb.appendLine("Mastering Display Optics (SMPTE ST 2086):")
            sb.appendLine("  Red:          (${String.format(Locale.US, "%.4f", md.redX)}, ${String.format(Locale.US, "%.4f", md.redY)})")
            sb.appendLine("  Green:        (${String.format(Locale.US, "%.4f", md.greenX)}, ${String.format(Locale.US, "%.4f", md.greenY)})")
            sb.appendLine("  Blue:         (${String.format(Locale.US, "%.4f", md.blueX)}, ${String.format(Locale.US, "%.4f", md.blueY)})")
            sb.appendLine("  White Point:  (${String.format(Locale.US, "%.4f", md.whitePointX)}, ${String.format(Locale.US, "%.4f", md.whitePointY)})")
            sb.appendLine("  Min Luminance:${md.minLuminanceNits} nits")
            sb.appendLine("  Max Luminance:${md.maxLuminanceNits} nits")
            sb.appendLine("  Provenance:   ${md.provenance.name} (${md.sourceLocation})")
        }

        val clli = r.hdrDetails.contentLightLevel
        if (clli != null) {
            sb.appendLine("Content Light Level (CTA-861.3):")
            sb.appendLine("  MaxCLL:       ${clli.maxCllNits} nits")
            sb.appendLine("  MaxFALL:      ${clli.maxFallNits} nits")
            sb.appendLine("  Provenance:   ${clli.provenance.name} (${clli.sourceLocation})")
        }

        if (r.hdrDetails.conflicts.isNotEmpty()) {
            sb.appendLine("Color Conflicts:")
            r.hdrDetails.conflicts.forEach { c ->
                sb.appendLine("  [!] ${c.fieldName}: Container='${c.containerValue}' vs Bitstream='${c.bitstreamValue}' -> ${c.resolutionDescription}")
            }
        }
        sb.appendLine()

        // Device Compatibility
        val dev = r.deviceCompatibility
        if (dev != null) {
            sb.appendLine("--- DEVICE HARDWARE COMPATIBILITY ---")
            sb.appendLine("Device Model:   ${dev.deviceModel} [${dev.socPlatform}]")
            sb.appendLine("Video Decode:   ${dev.videoDecodeSupport.label}")
            sb.appendLine("Audio Decode:   ${dev.audioDecodeSupport.label}")
            sb.appendLine("HEVC 8-bit Enc: ${dev.hevcMain8HardwareEncode.label}")
            sb.appendLine("HEVC 10-bit Enc:${dev.hevcMain10HardwareEncode.label}")
            sb.appendLine("Surface Input:  ${dev.surfaceInputPipelineSupport.label}")
            sb.appendLine("10-bit YUV:     ${dev.tenBitYuvProcessingSupport.label}")
            sb.appendLine("Max Resolution: ${dev.maxResolutionSupported}")
            if (dev.explanationNotes.isNotEmpty()) {
                sb.appendLine("Notes:")
                dev.explanationNotes.forEach { note ->
                    sb.appendLine("  * $note")
                }
            }
            sb.appendLine()
        }

        // Container Hierarchy Summary
        if (r.containerDetails.boxHierarchy.isNotEmpty()) {
            sb.appendLine("--- CONTAINER ATOMS (${r.containerDetails.containerFormat}) ---")
            fun printBoxes(nodes: List<BoxNode>, indent: String) {
                for (b in nodes) {
                    sb.appendLine("$indent[${b.type}] offset=${b.offset}, size=${b.size} bytes - ${b.description}")
                    if (b.children.isNotEmpty()) {
                        printBoxes(b.children, "$indent  ")
                    }
                }
            }
            printBoxes(r.containerDetails.boxHierarchy, "  ")
            sb.appendLine()
        }

        // Diagnostics
        if (r.warnings.isNotEmpty() || r.errors.isNotEmpty()) {
            sb.appendLine("--- DIAGNOSTICS ---")
            r.warnings.forEach { w ->
                sb.appendLine("[WARNING][${w.code}] ${w.message} (${w.technicalImpact})")
            }
            r.errors.forEach { e ->
                sb.appendLine("[ERROR][${e.code}] ${e.message}${e.cause?.let { " Cause: $it" } ?: ""}")
            }
            sb.appendLine()
        }

        sb.appendLine("==================================================")
        return sb.toString()
    }

    fun copySummaryToClipboard(context: Context) {
        val summary = generateShareableSummary()
        if (summary.isBlank()) {
            Toast.makeText(context, "No inspection report available to copy.", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("UniDownco Media Inspection Report", summary)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(context, "Inspection report copied to clipboard!", Toast.LENGTH_SHORT).show()
    }

    private fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, secs)
        }
    }
}
