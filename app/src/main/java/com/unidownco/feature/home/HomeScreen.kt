package com.unidownco.feature.home

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.unidownco.core.common.Formatters
import com.unidownco.core.common.Platform
import com.unidownco.core.design.components.UniCard
import com.unidownco.core.design.components.UniPrimaryButton
import com.unidownco.core.design.components.UniStatusBadge
import com.unidownco.core.design.components.UniTechBadge
import com.unidownco.core.design.components.UniTopAppBar
import com.unidownco.core.design.components.UniUrlInputField
import com.unidownco.core.design.theme.AppTypography
import com.unidownco.core.design.theme.UniColors
import com.unidownco.core.design.theme.UniDownTheme
import com.unidownco.core.design.theme.UniMonoTypography
import com.unidownco.core.design.theme.UniShapes
import com.unidownco.core.design.theme.UniSpacing
import com.unidownco.domain.models.DownloadItem
import com.unidownco.domain.models.FormatOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val recentList by viewModel.recentDownloads.collectAsState()
    val activeQueue by viewModel.activeQueue.collectAsState()
    val colors = UniDownTheme.colors

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        UniTopAppBar(
            title = "UniDownco",
            subtitle = "Universal Video Downloader & HDR Pipeline"
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = UniSpacing.screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(UniSpacing.md)
        ) {
            item { Spacer(modifier = Modifier.height(UniSpacing.xs)) }

            // Storage & System Overview Card
            item {
                StorageMetricsCard(
                    freeBytes = uiState.freeStorageBytes,
                    activeCount = activeQueue.size,
                    completedCount = recentList.count { it.downloadStatus.name == "COMPLETED" },
                    onQueueClick = onNavigateToQueue
                )
            }

            // URL Input Card
            item {
                UniCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "DOWNLOAD ANY ONLINE VIDEO",
                            style = AppTypography.labelLarge,
                            color = colors.primaryText
                        )
                        Text(
                            text = "Supports YouTube, TikTok, Instagram Reels, Reddit, X (Twitter), Facebook & direct streams.",
                            style = AppTypography.bodySmall,
                            color = colors.secondaryText
                        )

                        Spacer(modifier = Modifier.height(UniSpacing.md))

                        UniUrlInputField(
                            value = uiState.urlInput,
                            onValueChange = viewModel::onUrlChanged,
                            onPasteClick = viewModel::onPasteFromClipboard,
                            onClearClick = viewModel::onClearUrl,
                            onSubmit = viewModel::onStartAnalysis
                        )

                        if (uiState.detectedPlatform != Platform.UNSUPPORTED) {
                            Spacer(modifier = Modifier.height(UniSpacing.xs))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Detected Platform: ",
                                    style = AppTypography.bodySmall,
                                    color = colors.tertiaryText
                                )
                                UniTechBadge(
                                    label = uiState.detectedPlatform.displayName,
                                    isHighlighted = true
                                )
                            }
                        }

                        if (!uiState.extractionError.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(UniSpacing.xs))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(colors.errorBackground, UniShapes.BadgeShape)
                                    .padding(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = colors.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = uiState.extractionError!!,
                                    style = AppTypography.bodySmall,
                                    color = colors.error
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(UniSpacing.md))

                        UniPrimaryButton(
                            onClick = viewModel::onStartAnalysis,
                            enabled = uiState.isUrlValid && !uiState.isExtracting,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (uiState.isExtracting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.Black,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("ANALYZING SOURCE STREAM...", style = AppTypography.labelLarge)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("ANALYZE & DOWNLOAD", style = AppTypography.labelLarge)
                            }
                        }
                    }
                }
            }

            // Recent Downloads Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECENT TASKS",
                        style = AppTypography.titleMedium,
                        color = colors.primaryText
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${recentList.size} items",
                        style = UniMonoTypography.TechnicalSmall,
                        color = colors.tertiaryText
                    )
                }
            }

            if (recentList.isEmpty()) {
                item {
                    UniCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = colors.subtleBorder
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "NO DOWNLOADS YET",
                                style = AppTypography.labelLarge,
                                color = colors.secondaryText
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Share a video URL from any app or paste above to begin.",
                                style = AppTypography.bodySmall,
                                color = colors.tertiaryText
                            )
                        }
                    }
                }
            } else {
                items(recentList.take(5)) { item ->
                    RecentTaskCard(item = item)
                }
            }

            item { Spacer(modifier = Modifier.height(UniSpacing.lg)) }
        }
    }

    // Format Selection Modal Bottom Sheet
    if (uiState.showFormatSheet && uiState.extractedInfo != null) {
        val info = uiState.extractedInfo!!
        ModalBottomSheet(
            onDismissRequest = viewModel::onDismissFormatSheet,
            sheetState = sheetState,
            containerColor = colors.surface,
            shape = UniShapes.BottomSheetShape
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = UniSpacing.screenHorizontalPadding)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "SELECT DOWNLOAD STREAM",
                    style = AppTypography.titleLarge,
                    color = colors.primaryText
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = info.title,
                    style = AppTypography.bodyMedium,
                    color = colors.secondaryText,
                    maxLines = 2
                )
                if (info.author.isNotBlank()) {
                    Text(
                        text = "By ${info.author} • ${info.platform.displayName}",
                        style = AppTypography.bodySmall,
                        color = colors.tertiaryText
                    )
                }

                Spacer(modifier = Modifier.height(UniSpacing.md))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    info.formats.forEach { format ->
                        val isSelected = uiState.selectedFormat?.formatId == format.formatId
                        FormatOptionRow(
                            format = format,
                            isSelected = isSelected,
                            onSelect = { viewModel.onSelectFormat(format) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(UniSpacing.lg))

                UniPrimaryButton(
                    onClick = viewModel::onConfirmDownload,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("START DOWNLOAD", style = AppTypography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun StorageMetricsCard(
    freeBytes: Long,
    activeCount: Int,
    completedCount: Int,
    onQueueClick: () -> Unit
) {
    val colors = UniDownTheme.colors
    UniCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = colors.surfaceSubtle
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = colors.primaryText,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "FREE STORAGE",
                        style = AppTypography.labelSmall,
                        color = colors.secondaryText
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = Formatters.formatBytes(freeBytes),
                    style = UniMonoTypography.TechnicalLarge,
                    color = colors.primaryText
                )
            }

            Column(
                modifier = Modifier.clickable { onQueueClick() },
                horizontalAlignment = Alignment.End
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "ACTIVE QUEUE",
                        style = AppTypography.labelSmall,
                        color = colors.secondaryText
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UniTechBadge(
                        label = "$activeCount active",
                        isHighlighted = activeCount > 0
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$completedCount saved",
                        style = UniMonoTypography.TechnicalSmall,
                        color = colors.tertiaryText
                    )
                }
            }
        }
    }
}

@Composable
fun RecentTaskCard(item: DownloadItem) {
    val colors = UniDownTheme.colors
    UniCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = colors.subtleBorder
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = AppTypography.titleSmall,
                    color = colors.primaryText,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UniTechBadge(label = item.platform.displayName)
                    Spacer(modifier = Modifier.width(6.dp))
                    UniTechBadge(label = item.resolution)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = Formatters.formatBytes(item.fileSizeBytes),
                        style = UniMonoTypography.TechnicalSmall,
                        color = colors.tertiaryText
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            UniStatusBadge(status = item.downloadStatus.name)
        }

        if (!item.errorMessage.isNullOrBlank() && item.downloadStatus.name == "FAILED") {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = item.errorMessage!!,
                style = AppTypography.bodySmall,
                color = colors.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.errorBackground, UniShapes.BadgeShape)
                    .padding(6.dp)
            )
        }
    }
}

@Composable
fun FormatOptionRow(
    format: FormatOption,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val colors = UniDownTheme.colors
    val bg = if (isSelected) colors.surfaceSubtle else colors.surface
    val border = if (isSelected) Color.Black else colors.subtleBorder

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, UniShapes.CardShape)
            .border(1.dp, border, UniShapes.CardShape)
            .clickable { onSelect() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(
                selectedColor = Color.Black,
                unselectedColor = colors.tertiaryText
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = format.resolution,
                    style = AppTypography.titleMedium,
                    color = colors.primaryText
                )
                if (format.isBestQuality) {
                    Spacer(modifier = Modifier.width(8.dp))
                    UniTechBadge(label = "BEST QUALITY", isHighlighted = true)
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${format.container.uppercase()} • ${format.videoCodec.uppercase()} • ${format.fps} FPS" +
                        if (format.estimatedSizeBytes > 0) " • ~${Formatters.formatBytes(format.estimatedSizeBytes)}" else "",
                style = UniMonoTypography.TechnicalSmall,
                color = colors.secondaryText
            )
        }
    }
}
