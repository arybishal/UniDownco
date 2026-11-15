package com.unidownco.feature.queue

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material3.Icon
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.unidownco.core.common.Formatters
import com.unidownco.core.database.ProcessingJobEntity
import com.unidownco.core.design.components.UniAccentButton
import com.unidownco.core.design.components.UniCard
import com.unidownco.core.design.components.UniDestructiveButton
import com.unidownco.core.design.components.UniOutlinedButton
import com.unidownco.core.design.components.UniPrimaryButton
import com.unidownco.core.design.components.UniProgressBar
import com.unidownco.core.design.components.UniStatusBadge
import com.unidownco.core.design.components.UniTechBadge
import com.unidownco.core.design.components.UniTopAppBar
import com.unidownco.core.design.theme.AppTypography
import com.unidownco.core.design.theme.UniColors
import com.unidownco.core.design.theme.UniDownTheme
import com.unidownco.core.design.theme.UniMonoTypography
import com.unidownco.core.design.theme.UniShapes
import com.unidownco.core.design.theme.UniSpacing
import com.unidownco.domain.models.DownloadItem
import com.unidownco.domain.models.DownloadStatus
import com.unidownco.domain.models.ProcessingPreset
import com.unidownco.engine.hardware.HardwareCapabilityRegistry

@Composable
fun QueueScreen(
    viewModel: QueueViewModel,
    modifier: Modifier = Modifier
) {
    val activeQueue by viewModel.activeQueue.collectAsState()
    val allDownloads by viewModel.allDownloads.collectAsState()
    val activeProcessingJobs by viewModel.activeProcessingJobs.collectAsState()
    val colors = UniDownTheme.colors

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("DOWNLOAD QUEUE (${activeQueue.size})", "PROCESSING QUEUE (${activeProcessingJobs.size})")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        UniTopAppBar(
            title = "Queue Management",
            subtitle = "Active tasks, stream extraction & background worker"
        )

        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = colors.surface,
            contentColor = colors.primaryText,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = UniColors.AccentYellow,
                    height = 3.dp
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = title,
                            style = AppTypography.labelLarge,
                            color = if (selectedTabIndex == index) colors.primaryText else colors.tertiaryText
                        )
                    }
                )
            }
        }

        if (selectedTabIndex == 0) {
            // Download Queue Tab
            if (activeQueue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(UniSpacing.screenHorizontalPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "DOWNLOAD QUEUE EMPTY",
                            style = AppTypography.headlineMedium,
                            color = colors.secondaryText
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "New downloads will appear here with live speed, ETA, and progress.",
                            style = AppTypography.bodyMedium,
                            color = colors.tertiaryText
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = UniSpacing.screenHorizontalPadding),
                    verticalArrangement = Arrangement.spacedBy(UniSpacing.md)
                ) {
                    item { Spacer(modifier = Modifier.height(UniSpacing.xs)) }
                    items(activeQueue, key = { it.id }) { item ->
                        QueueItemCard(
                            item = item,
                            onPause = { viewModel.onPause(item.id) },
                            onResume = { viewModel.onResume(item.id) },
                            onCancel = { viewModel.onCancel(item.id) },
                            onRetry = { viewModel.onRetry(item.id) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(UniSpacing.lg)) }
                }
            }
        } else {
            // Processing Queue Tab (Step 2.2 Production MediaCodec Pipeline)
            ProcessingQueueContent(
                jobs = activeProcessingJobs,
                onCancel = { viewModel.onCancelProcessing(it) },
                onRetry = { viewModel.onRetryProcessing(it) }
            )
        }
    }
}

@Composable
fun QueueItemCard(
    item: DownloadItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    val colors = UniDownTheme.colors
    UniCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = AppTypography.titleMedium,
                        color = colors.primaryText,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UniTechBadge(label = item.platform.displayName)
                        Spacer(modifier = Modifier.width(6.dp))
                        UniTechBadge(label = item.resolution)
                        Spacer(modifier = Modifier.width(6.dp))
                        UniTechBadge(label = "${item.fps} FPS")
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                UniStatusBadge(status = item.downloadStatus.name)
            }

            Spacer(modifier = Modifier.height(UniSpacing.md))

            // Progress Bar
            UniProgressBar(
                progressPercent = item.progressPercent,
                speedText = if (item.downloadStatus == DownloadStatus.DOWNLOADING)
                    Formatters.formatSpeed(item.downloadSpeedBytesPerSec) else null,
                etaText = if (item.downloadStatus == DownloadStatus.DOWNLOADING)
                    Formatters.formatEta(item.etaSeconds) else null,
                sizeText = "${Formatters.formatBytes(item.downloadedBytes)} / ${Formatters.formatBytes(item.fileSizeBytes)}"
            )

            Spacer(modifier = Modifier.height(UniSpacing.md))

            if (!item.errorMessage.isNullOrBlank() && item.downloadStatus == DownloadStatus.FAILED) {
                Text(
                    text = item.errorMessage!!,
                    style = AppTypography.bodySmall,
                    color = colors.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.errorBackground, UniShapes.BadgeShape)
                        .padding(8.dp)
                )
                Spacer(modifier = Modifier.height(UniSpacing.xs))
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.downloadStatus == DownloadStatus.DOWNLOADING) {
                    UniOutlinedButton(
                        onClick = onPause,
                        modifier = Modifier.height(38.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PAUSE", style = AppTypography.labelSmall)
                    }
                } else if (item.downloadStatus == DownloadStatus.PAUSED) {
                    UniAccentButton(
                        onClick = onResume,
                        modifier = Modifier.height(38.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("RESUME", style = AppTypography.labelSmall, color = Color.Black)
                    }
                } else if (item.downloadStatus == DownloadStatus.FAILED) {
                    UniAccentButton(
                        onClick = onRetry,
                        modifier = Modifier.height(38.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("RETRY", style = AppTypography.labelSmall, color = Color.Black)
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                UniDestructiveButton(
                    onClick = onCancel,
                    modifier = Modifier.height(38.dp)
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("CANCEL", style = AppTypography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun ProcessingQueueContent(
    jobs: List<ProcessingJobEntity>,
    onCancel: (Long) -> Unit,
    onRetry: (Long) -> Unit
) {
    val colors = UniDownTheme.colors
    val caps = remember { HardwareCapabilityRegistry.getCapabilities() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = UniSpacing.screenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(UniSpacing.md)
    ) {
        item { Spacer(modifier = Modifier.height(UniSpacing.xs)) }

        if (jobs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "NO ACTIVE PROCESSING JOBS",
                            style = AppTypography.headlineMedium,
                            color = colors.secondaryText
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Downloads queued with encoding presets will automatically transcode through the MediaCodec hardware pipeline.",
                            style = AppTypography.bodyMedium,
                            color = colors.tertiaryText
                        )
                    }
                }
            }
        } else {
            items(jobs, key = { it.id }) { job ->
                ProcessingJobCard(
                    job = job,
                    onCancel = { onCancel(job.downloadId) },
                    onRetry = { onRetry(job.downloadId) }
                )
            }
        }

        // Hardware Capability Summary Card
        item {
            UniCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = colors.surfaceSubtle
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.MovieFilter,
                            contentDescription = null,
                            tint = UniColors.AccentYellow,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "MEDIACODEC HARDWARE PIPELINE",
                            style = AppTypography.titleLarge,
                            color = colors.primaryText
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Real-time hardware HEVC (H.265) encoding with lossless audio passthrough and post-transcode validation.",
                        style = AppTypography.bodyMedium,
                        color = colors.secondaryText
                    )

                    Spacer(modifier = Modifier.height(UniSpacing.md))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UniTechBadge(
                            label = if (caps.supportsHardwareMain10) "10-BIT MAIN10" else "8-BIT HEVC MAIN",
                            isHighlighted = true
                        )
                        UniTechBadge(label = caps.hardwareEncoderName ?: "MEDIACODEC HW")
                        UniTechBadge(label = "${caps.maxWidth}x${caps.maxHeight}")
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(UniSpacing.lg)) }
    }
}

@Composable
fun ProcessingJobCard(
    job: ProcessingJobEntity,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    val colors = UniDownTheme.colors
    val caps = remember { HardwareCapabilityRegistry.getCapabilities() }
    val preset = ProcessingPreset.fromId(job.preset)

    UniCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Job #${job.id} • Download #${job.downloadId}",
                        style = AppTypography.titleMedium,
                        color = colors.primaryText,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UniTechBadge(label = preset.getBadge(caps).uppercase(), isHighlighted = true)
                        Spacer(modifier = Modifier.width(6.dp))
                        UniTechBadge(label = job.targetCodec.uppercase())
                        Spacer(modifier = Modifier.width(6.dp))
                        UniTechBadge(label = "${job.targetFps} FPS")
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                UniStatusBadge(status = job.stage)
            }

            Spacer(modifier = Modifier.height(UniSpacing.md))

            // Progress Bar
            UniProgressBar(
                progressPercent = job.progressPercent,
                speedText = if (job.currentFps > 0) "${job.currentFps.toInt()} FPS" else null,
                etaText = null,
                sizeText = job.stage
            )

            if (!job.errorMessage.isNullOrBlank() && job.stage == "FAILED") {
                Spacer(modifier = Modifier.height(UniSpacing.xs))
                Text(
                    text = job.errorMessage!!,
                    style = AppTypography.bodySmall,
                    color = colors.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.errorBackground, UniShapes.BadgeShape)
                        .padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(UniSpacing.md))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (job.stage == "FAILED") {
                    UniAccentButton(
                        onClick = onRetry,
                        modifier = Modifier.height(38.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("RETRY", style = AppTypography.labelSmall, color = Color.Black)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                if (job.stage in listOf("QUEUED", "PREPARING", "ENCODING", "MUXING", "VALIDATING")) {
                    UniDestructiveButton(
                        onClick = onCancel,
                        modifier = Modifier.height(38.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("CANCEL", style = AppTypography.labelSmall)
                    }
                }
            }
        }
    }
}
