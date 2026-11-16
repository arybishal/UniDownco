package com.unidownco.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.unidownco.core.common.Formatters
import com.unidownco.core.design.components.UniCard
import com.unidownco.core.design.components.UniOutlinedButton
import com.unidownco.core.design.components.UniPrimaryButton
import com.unidownco.core.design.components.UniStatusBadge
import com.unidownco.core.design.components.UniTechBadge
import com.unidownco.core.design.components.UniTopAppBar
import com.unidownco.core.design.theme.AppTypography
import com.unidownco.core.design.theme.UniColors
import com.unidownco.core.design.theme.UniDownTheme
import com.unidownco.core.design.theme.UniMonoTypography
import com.unidownco.core.design.theme.UniSpacing
import com.unidownco.feature.library.LibraryViewModel
import java.io.File

@Composable
fun VideoDetailsScreen(
    downloadId: Long,
    viewModel: LibraryViewModel,
    onNavigateBack: () -> Unit,
    onInspectMedia: (File) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val completedVideos by viewModel.completedVideos.collectAsState()
    val item = completedVideos.firstOrNull { it.id == downloadId }
    val colors = UniDownTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        UniTopAppBar(
            title = "Video Details",
            subtitle = "Technical metadata & encoding pipeline inspection",
            actions = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.primaryText
                    )
                }
            }
        )

        if (item == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = "Video record not found.", style = AppTypography.headlineMedium, color = colors.secondaryText)
                Spacer(modifier = Modifier.height(16.dp))
                UniPrimaryButton(onClick = onNavigateBack) {
                    Text("BACK TO LIBRARY", style = AppTypography.labelLarge)
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

                // Main Info Card
                item {
                    UniCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                UniTechBadge(label = item.platform.displayName, isHighlighted = true)
                                Spacer(modifier = Modifier.width(8.dp))
                                UniStatusBadge(status = item.downloadStatus.name)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = item.title,
                                style = AppTypography.headlineMedium,
                                color = colors.primaryText
                            )
                            if (item.author.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Creator: ${item.author}",
                                    style = AppTypography.bodyMedium,
                                    color = colors.secondaryText
                                )
                            }
                        }
                    }
                }

                // Technical Specifications Card
                item {
                    UniCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "TECHNICAL METADATA",
                                style = AppTypography.labelLarge,
                                color = colors.primaryText
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            MetadataRow(label = "Resolution", value = item.resolution)
                            MetadataRow(label = "Frame Rate", value = "${item.fps} FPS")
                            MetadataRow(label = "Video Codec", value = item.codec.uppercase())
                            MetadataRow(label = "File Size", value = Formatters.formatBytes(item.fileSizeBytes))
                            MetadataRow(label = "Filename", value = item.originalFilename)
                            MetadataRow(label = "Path", value = item.downloadPath ?: "N/A")
                            MetadataRow(label = "Source URL", value = item.url)
                        }
                    }
                }

                // Encoding Engine Readiness (Step 2/3)
                item {
                    UniCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = colors.surfaceSubtle
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "ENCODING PIPELINE STATUS",
                                style = AppTypography.labelLarge,
                                color = colors.primaryText
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "This media is ready for automated Step 2 HEVC Main10 transcoding and Step 3 HDR color science mastering (PQ / BT.2020) with target Snapdragon 8 Gen 3 Adreno 750 acceleration.",
                                style = AppTypography.bodySmall,
                                color = colors.secondaryText
                            )
                        }
                    }
                }

                // Actions Card
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UniPrimaryButton(
                            onClick = { viewModel.playVideo(item) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PLAY", style = AppTypography.labelSmall)
                        }

                        UniOutlinedButton(
                            onClick = { viewModel.onExportToGallery(item) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("GALLERY", style = AppTypography.labelSmall)
                        }

                        UniOutlinedButton(
                            onClick = { viewModel.shareVideo(item) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SHARE", style = AppTypography.labelSmall)
                        }
                    }
                }

                // Media Inspector Action
                item {
                    val downloadPath = item.downloadPath
                    if (!downloadPath.isNullOrBlank()) {
                        val file = File(downloadPath)
                        if (file.exists()) {
                            UniOutlinedButton(
                                onClick = { onInspectMedia(file) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Analytics,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("INSPECT IN MEDIA INSPECTOR", style = AppTypography.labelSmall)
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(UniSpacing.lg)) }
            }
        }
    }
}

@Composable
fun MetadataRow(label: String, value: String) {
    val colors = UniDownTheme.colors
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
        Text(
            text = value,
            style = UniMonoTypography.TechnicalSmall,
            color = colors.primaryText,
            maxLines = 1
        )
    }
}
