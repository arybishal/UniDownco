package com.unidownco.feature.inspector

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unidownco.core.common.Formatters
import com.unidownco.core.design.components.UniAccentButton
import com.unidownco.core.design.components.UniCard
import com.unidownco.core.design.components.UniOutlinedButton
import com.unidownco.core.design.components.UniPrimaryButton
import com.unidownco.core.design.components.UniTechBadge
import com.unidownco.core.design.components.UniTopAppBar
import com.unidownco.core.design.theme.AppTypography
import com.unidownco.core.design.theme.UniColors
import com.unidownco.core.design.theme.UniDownTheme
import com.unidownco.core.design.theme.UniMonoTypography
import com.unidownco.core.design.theme.UniShapes
import com.unidownco.core.design.theme.UniSpacing
import com.unidownco.engine.inspector.model.AudioStreamDetails
import com.unidownco.engine.inspector.model.BoxNode
import com.unidownco.engine.inspector.model.CompatibilityStatus
import com.unidownco.engine.inspector.model.DeviceCompatibilityDetails
import com.unidownco.engine.inspector.model.FrameRateMode
import com.unidownco.engine.inspector.model.HdrClassification
import com.unidownco.engine.inspector.model.HdrColorDetails
import com.unidownco.engine.inspector.model.MediaInspectionReport
import com.unidownco.engine.inspector.model.MediaOverview
import com.unidownco.engine.inspector.model.SubtitleStreamDetails
import com.unidownco.engine.inspector.model.VideoStreamDetails
import java.util.Locale

@Composable
fun MediaInspectorScreen(
    viewModel: MediaInspectorViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val colors = UniDownTheme.colors

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.inspectUri(uri, context)
        }
    }

    val openPicker = {
        documentPickerLauncher.launch(
            arrayOf(
                "video/*",
                "audio/*",
                "application/octet-stream",
                "*/*"
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        UniTopAppBar(
            title = "Media Inspector",
            subtitle = "Container, Bitstream & Color Science Engine",
            actions = {
                if (uiState is MediaInspectorUiState.Success) {
                    IconButton(onClick = { viewModel.copySummaryToClipboard(context) }) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Report",
                            tint = colors.primaryText
                        )
                    }
                    IconButton(onClick = openPicker) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Open Media",
                            tint = colors.primaryText
                        )
                    }
                    IconButton(onClick = { viewModel.reset() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Report",
                            tint = colors.primaryText
                        )
                    }
                } else if (uiState is MediaInspectorUiState.Idle) {
                    IconButton(onClick = openPicker) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Open Media",
                            tint = colors.primaryText
                        )
                    }
                }
            }
        )

        when (val state = uiState) {
            is MediaInspectorUiState.Idle -> {
                InspectorIdleContent(onOpenPicker = openPicker)
            }
            is MediaInspectorUiState.Loading -> {
                InspectorLoadingContent(sourceName = state.sourceName)
            }
            is MediaInspectorUiState.Error -> {
                InspectorErrorContent(
                    message = state.message,
                    technicalDetails = state.technicalDetails,
                    canRetry = state.canRetry,
                    onOpenPicker = openPicker,
                    onReset = { viewModel.reset() }
                )
            }
            is MediaInspectorUiState.Success -> {
                InspectorReportContent(
                    report = state.report,
                    onOpenPicker = openPicker,
                    onCopyReport = { viewModel.copySummaryToClipboard(context) }
                )
            }
        }
    }
}

@Composable
private fun InspectorIdleContent(onOpenPicker: () -> Unit) {
    val colors = UniDownTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = UniSpacing.screenHorizontalPadding, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surfaceSubtle)
                .border(BorderStroke(1.dp, colors.subtleBorder), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Analytics,
                contentDescription = null,
                tint = colors.primaryText,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "INSPECT MEDIA CONTAINER & BITSTREAM",
            style = AppTypography.headlineMedium,
            color = colors.primaryText,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Deep inspection of MP4 / MOV containers, HEVC Main10 elementary streams, true HDR10 optical mastering metadata, VFR timing, and device hardware decode/encode compatibility.",
            style = AppTypography.bodyMedium,
            color = colors.secondaryText,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        UniPrimaryButton(onClick = onOpenPicker) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("SELECT MEDIA FILE (SAF)", style = AppTypography.labelLarge)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Tip: You can also inspect downloaded files directly from the Video Library tab.",
            style = AppTypography.bodySmall,
            color = colors.tertiaryText
        )
    }
}

@Composable
private fun InspectorLoadingContent(sourceName: String) {
    val colors = UniDownTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = UniColors.AccentYellow,
            strokeWidth = 3.dp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "ANALYZING MEDIA BITSTREAM",
            style = AppTypography.headlineMedium,
            color = colors.primaryText,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = sourceName,
            style = UniMonoTypography.TechnicalMedium,
            color = colors.secondaryText,
            maxLines = 2
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Parsing container atoms, scanning SEI NAL units, verifying HDR10 optical metadata and evaluating hardware compatibility...",
            style = AppTypography.bodySmall,
            color = colors.tertiaryText
        )
    }
}

@Composable
private fun InspectorErrorContent(
    message: String,
    technicalDetails: String?,
    canRetry: Boolean,
    onOpenPicker: () -> Unit,
    onReset: () -> Unit
) {
    val colors = UniDownTheme.colors
    var detailsExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = UniSpacing.screenHorizontalPadding, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.errorBackground)
                .border(BorderStroke(1.dp, colors.error), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = colors.error,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "INSPECTION FAILED",
            style = AppTypography.headlineMedium,
            color = colors.primaryText,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            style = AppTypography.bodyMedium,
            color = colors.secondaryText
        )

        if (!technicalDetails.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            UniOutlinedButton(
                onClick = { detailsExpanded = !detailsExpanded },
                modifier = Modifier.height(36.dp)
            ) {
                Text(
                    text = if (detailsExpanded) "HIDE TECHNICAL LOG" else "SHOW TECHNICAL LOG",
                    style = AppTypography.labelSmall
                )
            }

            AnimatedVisibility(
                visible = detailsExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceSubtle)
                            .border(BorderStroke(1.dp, colors.subtleBorder), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = technicalDetails,
                            style = UniMonoTypography.TechnicalSmall,
                            color = colors.tertiaryText
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            UniPrimaryButton(onClick = onOpenPicker) {
                Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("PICK ANOTHER FILE", style = AppTypography.labelSmall)
            }
            UniOutlinedButton(onClick = onReset) {
                Text("DISMISS", style = AppTypography.labelSmall)
            }
        }
    }
}

@Composable
private fun InspectorReportContent(
    report: MediaInspectionReport,
    onOpenPicker: () -> Unit,
    onCopyReport: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = UniSpacing.screenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(UniSpacing.md)
    ) {
        item { Spacer(modifier = Modifier.height(UniSpacing.xs)) }

        // 1. Overview Card
        item {
            OverviewCard(overview = report.overview, report = report)
        }

        // 2. HDR & Color Science Card
        item {
            HdrColorScienceCard(hdr = report.hdrDetails)
        }

        // 3. Video Streams Card
        if (report.videoStreams.isNotEmpty()) {
            item {
                VideoStreamsCard(streams = report.videoStreams)
            }
        }

        // 4. Audio Streams Card
        if (report.audioStreams.isNotEmpty()) {
            item {
                AudioStreamsCard(streams = report.audioStreams)
            }
        }

        // 5. Subtitle Streams Card
        if (report.subtitleStreams.isNotEmpty()) {
            item {
                SubtitleStreamsCard(streams = report.subtitleStreams)
            }
        }

        // 6. Device Hardware Compatibility Card
        report.deviceCompatibility?.let { dev ->
            item {
                DeviceCompatibilityCard(dev = dev)
            }
        }

        // 7. Container Hierarchy Card (Box Atoms)
        if (report.containerDetails.boxHierarchy.isNotEmpty()) {
            item {
                ContainerHierarchyCard(
                    format = report.containerDetails.containerFormat,
                    boxHierarchy = report.containerDetails.boxHierarchy
                )
            }
        }

        // 8. Technical Diagnostics Card
        if (report.warnings.isNotEmpty() || report.errors.isNotEmpty()) {
            item {
                TechnicalDiagnosticsCard(warnings = report.warnings, errors = report.errors)
            }
        }

        // 9. Bottom Actions
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                UniAccentButton(
                    onClick = onCopyReport,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("COPY REPORT", style = AppTypography.labelSmall)
                }

                UniOutlinedButton(
                    onClick = onOpenPicker,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("OPEN ANOTHER", style = AppTypography.labelSmall)
                }
            }
        }

        item { Spacer(modifier = Modifier.height(UniSpacing.lg)) }
    }
}

// ----------------------------------------------------------------------------
// Card Implementations
// ----------------------------------------------------------------------------

@Composable
private fun OverviewCard(overview: MediaOverview, report: MediaInspectionReport) {
    val colors = UniDownTheme.colors
    UniCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                UniTechBadge(label = overview.containerFormat.uppercase(), isHighlighted = true)
                HdrBadge(classification = overview.hdrClassification)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = overview.filename,
                style = AppTypography.titleLarge,
                color = colors.primaryText,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(12.dp))

            InspectorSpecRow(label = "Container Format", value = overview.containerFormat)
            InspectorSpecRow(label = "File Size", value = "${Formatters.formatBytes(overview.fileSizeBytes)} (${overview.fileSizeBytes} B)")
            InspectorSpecRow(label = "Duration", value = formatDurationSeconds(overview.durationSeconds))
            overview.overallBitrateKbps?.let {
                InspectorSpecRow(label = "Overall Bitrate", value = "$it kbps")
            }
            overview.primaryResolution?.let {
                InspectorSpecRow(label = "Primary Resolution", value = it)
            }
            overview.primaryFrameRate?.let {
                InspectorSpecRow(label = "Primary Frame Rate", value = "${String.format(Locale.US, "%.3f", it)} FPS")
            }
            overview.primaryVideoCodec?.let {
                InspectorSpecRow(label = "Video Codec", value = it)
            }
            overview.primaryAudioCodec?.let {
                InspectorSpecRow(label = "Audio Codec", value = it)
            }
            InspectorSpecRow(label = "Total Tracks", value = "${report.totalTrackCount} (${report.videoStreams.size}v / ${report.audioStreams.size}a / ${report.subtitleStreams.size}s)")
        }
    }
}

@Composable
private fun HdrColorScienceCard(hdr: HdrColorDetails) {
    val colors = UniDownTheme.colors
    var expanded by remember { mutableStateOf(true) }

    UniCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "HDR & COLOR SCIENCE",
                        style = AppTypography.labelLarge,
                        color = colors.primaryText
                    )
                    Text(
                        text = "Optical mastering volume & bitstream signaling",
                        style = AppTypography.bodySmall,
                        color = colors.secondaryText
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = colors.primaryText
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    // Authoritative Classification Badge Block
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (hdr.classification.isHdr) UniColors.AccentYellow else colors.surfaceSubtle)
                            .border(BorderStroke(1.dp, Color.Black), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "AUTHORITATIVE VERDICT",
                                style = AppTypography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = hdr.classification.label,
                                style = AppTypography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.Black
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    InspectorSpecRow(label = "Color Primaries", value = hdr.colorPrimariesName ?: "Unspecified")
                    InspectorSpecRow(label = "Transfer Function", value = hdr.colorTransferName ?: "Unspecified")
                    InspectorSpecRow(label = "Matrix Coefficients", value = hdr.matrixCoefficientsName ?: "Unspecified")
                    InspectorSpecRow(label = "Color Range", value = hdr.colorRangeName ?: "Unspecified")
                    InspectorSpecRow(label = "Container Box (colr)", value = if (hdr.containerAtomVerified) "Verified Present" else "Absent")
                    InspectorSpecRow(label = "Bitstream SEI Header", value = if (hdr.bitstreamSeiVerified) "Verified Present" else "Absent")

                    // Mastering Display (ST 2086)
                    hdr.masteringDisplay?.let { md ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "MASTERING DISPLAY OPTICS (SMPTE ST 2086)",
                            style = AppTypography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.primaryText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        InspectorSpecRow(label = "Red Primary (x, y)", value = "(${String.format(Locale.US, "%.4f", md.redX)}, ${String.format(Locale.US, "%.4f", md.redY)})")
                        InspectorSpecRow(label = "Green Primary (x, y)", value = "(${String.format(Locale.US, "%.4f", md.greenX)}, ${String.format(Locale.US, "%.4f", md.greenY)})")
                        InspectorSpecRow(label = "Blue Primary (x, y)", value = "(${String.format(Locale.US, "%.4f", md.blueX)}, ${String.format(Locale.US, "%.4f", md.blueY)})")
                        InspectorSpecRow(label = "White Point (x, y)", value = "(${String.format(Locale.US, "%.4f", md.whitePointX)}, ${String.format(Locale.US, "%.4f", md.whitePointY)})")
                        InspectorSpecRow(label = "Min Luminance", value = "${md.minLuminanceNits} cd/m² (nits)")
                        InspectorSpecRow(label = "Max Luminance", value = "${md.maxLuminanceNits} cd/m² (nits)")
                        InspectorSpecRow(label = "Optics Provenance", value = "${md.provenance.name} (${md.sourceLocation})")
                    }

                    // Content Light Level (CTA-861.3)
                    hdr.contentLightLevel?.let { clli ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "CONTENT LIGHT LEVEL (CTA-861.3 / CLLI)",
                            style = AppTypography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.primaryText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        InspectorSpecRow(label = "MaxCLL", value = "${clli.maxCllNits} cd/m² (nits)")
                        InspectorSpecRow(label = "MaxFALL", value = "${clli.maxFallNits} cd/m² (nits)")
                        InspectorSpecRow(label = "CLLI Provenance", value = "${clli.provenance.name} (${clli.sourceLocation})")
                    }

                    // Conflicts
                    if (hdr.conflicts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "METADATA CONFLICT DETECTIONS",
                            style = AppTypography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.error
                        )
                        hdr.conflicts.forEach { conflict ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(colors.errorBackground)
                                    .border(BorderStroke(1.dp, colors.error), RoundedCornerShape(6.dp))
                                    .padding(8.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "${conflict.fieldName} Mismatch",
                                        style = AppTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = colors.error
                                    )
                                    Text(
                                        text = "Container: ${conflict.containerValue} | Bitstream: ${conflict.bitstreamValue}",
                                        style = UniMonoTypography.TechnicalSmall,
                                        color = colors.primaryText
                                    )
                                    Text(
                                        text = conflict.resolutionDescription,
                                        style = AppTypography.bodySmall,
                                        color = colors.secondaryText
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoStreamsCard(streams: List<VideoStreamDetails>) {
    val colors = UniDownTheme.colors
    var expanded by remember { mutableStateOf(true) }

    UniCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "VIDEO STREAMS (${streams.size})",
                        style = AppTypography.labelLarge,
                        color = colors.primaryText
                    )
                    Text(
                        text = "Dimensions, timing cadences & bit depth",
                        style = AppTypography.bodySmall,
                        color = colors.secondaryText
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = colors.primaryText
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    streams.forEachIndexed { index, v ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.surfaceSubtle)
                                .border(BorderStroke(1.dp, colors.subtleBorder), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Track #$index [ID: ${v.trackId}]",
                                        style = AppTypography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = colors.primaryText
                                    )
                                    FrameRateBadge(mode = v.frameRateMode)
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                val profileLevelStr = listOfNotNull(v.profileName, v.levelName?.let { "Level $it" }).joinToString(" @ ").ifEmpty { "N/A" }
                                val nomFpsStr = v.nominalFrameRate?.let { String.format(Locale.US, "%.3f", it) } ?: "N/A"
                                val avgFpsStr = v.averageFrameRate?.let { String.format(Locale.US, "%.3f", it) } ?: "N/A"

                                InspectorSpecRow(label = "Codec", value = "${v.codecName}${v.codecFourCc?.let { " ($it)" } ?: ""}")
                                InspectorSpecRow(label = "Profile & Level", value = profileLevelStr)
                                InspectorSpecRow(label = "Resolution", value = "${v.width} x ${v.height}")
                                InspectorSpecRow(label = "Aspect Ratio", value = "DAR ${v.displayAspectRatio ?: "N/A"} | PAR ${v.pixelAspectRatio ?: "N/A"}")
                                InspectorSpecRow(label = "Nominal Frame Rate", value = "$nomFpsStr FPS")
                                InspectorSpecRow(label = "Average Frame Rate", value = "$avgFpsStr FPS")
                                InspectorSpecRow(label = "Bit Depth", value = "${v.bitDepth ?: "N/A"}-bit")
                                InspectorSpecRow(label = "Color Primaries", value = v.colorPrimaries ?: "Unspecified")
                                InspectorSpecRow(label = "Color Transfer", value = v.colorTransfer ?: "Unspecified")
                                InspectorSpecRow(label = "Matrix Coefficients", value = v.matrixCoefficients ?: "Unspecified")
                                InspectorSpecRow(label = "Color Range", value = v.colorRange ?: "Unspecified")
                                InspectorSpecRow(label = "Rotation", value = "${v.rotationDegrees}°")
                                if (v.bitrateKbps != null) {
                                    InspectorSpecRow(label = "Stream Bitrate", value = "${v.bitrateKbps} kbps")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioStreamsCard(streams: List<AudioStreamDetails>) {
    val colors = UniDownTheme.colors
    var expanded by remember { mutableStateOf(false) }

    UniCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "AUDIO STREAMS (${streams.size})",
                        style = AppTypography.labelLarge,
                        color = colors.primaryText
                    )
                    Text(
                        text = "Channel layout, sampling frequency & bitrates",
                        style = AppTypography.bodySmall,
                        color = colors.secondaryText
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = colors.primaryText
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    streams.forEachIndexed { index, a ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.surfaceSubtle)
                                .border(BorderStroke(1.dp, colors.subtleBorder), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Track #$index [ID: ${a.trackId}]",
                                    style = AppTypography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = colors.primaryText
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                InspectorSpecRow(label = "Codec", value = "${a.codecName}${a.codecFourCc?.let { " ($it)" } ?: ""}")
                                if (a.profileName != null) {
                                    InspectorSpecRow(label = "Profile", value = a.profileName)
                                }
                                InspectorSpecRow(label = "Channels", value = "${a.channelCount} (${a.channelLayout ?: "Stereo"})")
                                InspectorSpecRow(label = "Sample Rate", value = "${a.sampleRateHz} Hz")
                                if (a.bitrateKbps != null) {
                                    InspectorSpecRow(label = "Bitrate", value = "${a.bitrateKbps} kbps")
                                }
                                InspectorSpecRow(label = "Language", value = a.language ?: "und")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitleStreamsCard(streams: List<SubtitleStreamDetails>) {
    val colors = UniDownTheme.colors
    var expanded by remember { mutableStateOf(false) }

    UniCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "SUBTITLE STREAMS (${streams.size})",
                    style = AppTypography.labelLarge,
                    color = colors.primaryText
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = colors.primaryText
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    streams.forEachIndexed { index, s ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.surfaceSubtle)
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Track #$index [ID: ${s.trackId}] Format: ${s.formatName}",
                                style = UniMonoTypography.TechnicalSmall,
                                color = colors.primaryText
                            )
                            UniTechBadge(label = s.language ?: "und")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceCompatibilityCard(dev: DeviceCompatibilityDetails) {
    val colors = UniDownTheme.colors
    var expanded by remember { mutableStateOf(true) }

    UniCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "DEVICE COMPATIBILITY",
                        style = AppTypography.labelLarge,
                        color = colors.primaryText
                    )
                    Text(
                        text = "${dev.deviceModel} [${dev.socPlatform}]",
                        style = AppTypography.bodySmall,
                        color = colors.secondaryText
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = colors.primaryText
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    CompatibilityRow(label = "Video Decoding", status = dev.videoDecodeSupport)
                    CompatibilityRow(label = "Audio Decoding", status = dev.audioDecodeSupport)
                    CompatibilityRow(label = "HEVC 8-bit Main Encode", status = dev.hevcMain8HardwareEncode)
                    CompatibilityRow(label = "HEVC 10-bit Main10 Encode", status = dev.hevcMain10HardwareEncode)
                    CompatibilityRow(label = "Surface Input Pipeline", status = dev.surfaceInputPipelineSupport)
                    CompatibilityRow(label = "10-bit YUV Processing", status = dev.tenBitYuvProcessingSupport)
                    InspectorSpecRow(label = "Max Hardware Resolution", value = dev.maxResolutionSupported)

                    if (dev.explanationNotes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "HONEST HARDWARE DISCLOSURES",
                            style = AppTypography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.primaryText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        dev.explanationNotes.forEach { note ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(text = "• ", style = AppTypography.bodySmall, color = colors.secondaryText)
                                Text(
                                    text = note,
                                    style = AppTypography.bodySmall,
                                    color = colors.secondaryText
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContainerHierarchyCard(format: String, boxHierarchy: List<BoxNode>) {
    val colors = UniDownTheme.colors
    var expanded by remember { mutableStateOf(false) }

    UniCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "CONTAINER ATOM TREE ($format)",
                        style = AppTypography.labelLarge,
                        color = colors.primaryText
                    )
                    Text(
                        text = "Parsed ISOBMFF / MP4 box structures & byte offsets",
                        style = AppTypography.bodySmall,
                        color = colors.secondaryText
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = colors.primaryText
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    boxHierarchy.forEach { node ->
                        BoxTreeNodeItem(node = node, depth = 0)
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxTreeNodeItem(node: BoxNode, depth: Int) {
    val colors = UniDownTheme.colors
    var expanded by remember { mutableStateOf(depth < 1) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 14).dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(if (depth == 0) colors.surfaceSubtle else Color.Transparent)
                .clickable(enabled = node.isContainer) { expanded = !expanded }
                .padding(vertical = 4.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (node.isContainer) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = colors.tertiaryText,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            } else {
                Spacer(modifier = Modifier.width(20.dp))
            }

            Box(
                modifier = Modifier
                    .background(Color.Black, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    text = node.type,
                    style = UniMonoTypography.TechnicalSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.description,
                    style = AppTypography.bodySmall,
                    color = colors.primaryText,
                    maxLines = 1
                )
                Text(
                    text = "offset: ${node.offset} B | size: ${node.size} B",
                    style = UniMonoTypography.TechnicalSmall.copy(fontSize = 10.sp),
                    color = colors.tertiaryText
                )
            }
        }

        if (node.isContainer) {
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    node.children.forEach { child ->
                        BoxTreeNodeItem(node = child, depth = depth + 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun TechnicalDiagnosticsCard(
    warnings: List<com.unidownco.engine.inspector.model.InspectionWarning>,
    errors: List<com.unidownco.engine.inspector.model.InspectionError>
) {
    val colors = UniDownTheme.colors
    var expanded by remember { mutableStateOf(true) }

    UniCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "DIAGNOSTICS & ADVISORIES (${warnings.size + errors.size})",
                        style = AppTypography.labelLarge,
                        color = if (errors.isNotEmpty()) colors.error else colors.primaryText
                    )
                    Text(
                        text = "Warnings, container quirks and non-fatal bitstream anomalies",
                        style = AppTypography.bodySmall,
                        color = colors.secondaryText
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = colors.primaryText
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    errors.forEach { err ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.errorBackground)
                                .border(BorderStroke(1.dp, colors.error), RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Column {
                                Text(
                                    text = "[ERROR: ${err.code}] ${err.message}",
                                    style = AppTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = colors.error
                                )
                                err.cause?.let {
                                    Text(
                                        text = "Cause: $it",
                                        style = UniMonoTypography.TechnicalSmall,
                                        color = colors.secondaryText
                                    )
                                }
                            }
                        }
                    }

                    warnings.forEach { warn ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.surfaceSubtle)
                                .border(BorderStroke(1.dp, colors.subtleBorder), RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    UniTechBadge(label = warn.code)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = warn.message,
                                        style = AppTypography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = colors.primaryText
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Impact: ${warn.technicalImpact}",
                                    style = AppTypography.bodySmall,
                                    color = colors.secondaryText
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// Helper Components & Rows
// ----------------------------------------------------------------------------

@Composable
fun InspectorSpecRow(label: String, value: String) {
    val colors = UniDownTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = AppTypography.bodySmall,
            color = colors.secondaryText
        )
        Text(
            text = value,
            style = UniMonoTypography.TechnicalSmall,
            color = colors.primaryText,
            maxLines = 1
        )
    }
}

@Composable
private fun CompatibilityRow(label: String, status: CompatibilityStatus) {
    val colors = UniDownTheme.colors
    val badgeColor = try {
        Color(android.graphics.Color.parseColor(status.badgeColor))
    } catch (_: Exception) {
        colors.primaryText
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = AppTypography.bodySmall,
            color = colors.secondaryText
        )
        Box(
            modifier = Modifier
                .clip(UniShapes.BadgeShape)
                .background(badgeColor.copy(alpha = 0.15f))
                .border(BorderStroke(1.dp, badgeColor), UniShapes.BadgeShape)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = status.label,
                style = UniMonoTypography.TechnicalSmall.copy(fontWeight = FontWeight.Bold),
                color = badgeColor
            )
        }
    }
}

@Composable
private fun HdrBadge(classification: HdrClassification) {
    val colors = UniDownTheme.colors
    val (bg, fg, border) = when (classification) {
        HdrClassification.GENUINE_HDR10 -> Triple(UniColors.AccentYellow, Color.Black, Color.Black)
        HdrClassification.HDR10_PLUS -> Triple(UniColors.AccentYellow, Color.Black, Color.Black)
        HdrClassification.DOLBY_VISION -> Triple(UniColors.AccentYellow, Color.Black, Color.Black)
        HdrClassification.HLG -> Triple(colors.surfaceSubtle, colors.primaryText, colors.subtleBorder)
        HdrClassification.PARTIAL_HDR -> Triple(UniColors.AccentYellowLight, Color.Black, Color.Black)
        HdrClassification.FAKE_HDR_REJECTED -> Triple(colors.errorBackground, colors.error, colors.error)
        HdrClassification.MALFORMED_OR_CONFLICTING -> Triple(colors.errorBackground, colors.error, colors.error)
        HdrClassification.SDR_BT709 -> Triple(colors.surfaceSubtle, colors.primaryText, colors.subtleBorder)
        HdrClassification.UNKNOWN -> Triple(colors.surfaceSubtle, colors.tertiaryText, colors.subtleBorder)
    }

    Box(
        modifier = Modifier
            .background(bg, UniShapes.BadgeShape)
            .border(BorderStroke(1.dp, border), UniShapes.BadgeShape)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = classification.name.replace("_", " "),
            style = AppTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = fg
        )
    }
}

@Composable
private fun FrameRateBadge(mode: FrameRateMode) {
    val colors = UniDownTheme.colors
    val (bg, fg, border) = when (mode) {
        FrameRateMode.CONSTANT_FRAME_RATE -> Triple(colors.successBackground, colors.success, colors.success)
        FrameRateMode.VARIABLE_FRAME_RATE_CONFIRMED -> Triple(UniColors.AccentYellow, Color.Black, Color.Black)
        FrameRateMode.VARIABLE_FRAME_RATE_SUSPECTED -> Triple(colors.surfaceSubtle, colors.primaryText, colors.subtleBorder)
        FrameRateMode.UNKNOWN_FRAME_RATE -> Triple(colors.surfaceSubtle, colors.tertiaryText, colors.subtleBorder)
    }

    Box(
        modifier = Modifier
            .background(bg, UniShapes.BadgeShape)
            .border(BorderStroke(1.dp, border), UniShapes.BadgeShape)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = mode.label,
            style = UniMonoTypography.TechnicalSmall.copy(fontWeight = FontWeight.Bold),
            color = fg
        )
    }
}

private fun formatDurationSeconds(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, secs)
    }
}
