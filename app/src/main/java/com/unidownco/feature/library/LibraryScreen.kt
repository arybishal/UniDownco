package com.unidownco.feature.library

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.unidownco.core.design.components.UniOutlinedButton
import com.unidownco.core.design.components.UniPrimaryButton
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

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onNavigateToDetails: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val completedVideos by viewModel.completedVideos.collectAsState()
    val selectedPlatform by viewModel.selectedPlatform.collectAsState()
    val colors = UniDownTheme.colors

    val filterPlatforms = listOf(
        null to "ALL",
        Platform.YOUTUBE to "YouTube",
        Platform.TIKTOK to "TikTok",
        Platform.INSTAGRAM to "Instagram",
        Platform.REDDIT to "Reddit",
        Platform.X_TWITTER to "X",
        Platform.FACEBOOK to "Facebook"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        UniTopAppBar(
            title = "Video Library",
            subtitle = "Saved media, original streams & gallery exports"
        )

        // Platform Filter Chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = UniSpacing.screenHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filterPlatforms) { (platform, label) ->
                val isSelected = selectedPlatform == platform
                val bg = if (isSelected) UniColors.AccentYellow else colors.surfaceSubtle
                val fg = if (isSelected) Color.Black else colors.primaryText

                Box(
                    modifier = Modifier
                        .background(bg, UniShapes.BadgeShape)
                        .clickable { viewModel.onFilterPlatform(platform) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = label,
                        style = AppTypography.labelSmall,
                        color = fg
                    )
                }
            }
        }

        if (completedVideos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(UniSpacing.screenHorizontalPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = null,
                        tint = colors.tertiaryText,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "NO COMPLETED VIDEOS",
                        style = AppTypography.headlineMedium,
                        color = colors.secondaryText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Downloaded videos ready for viewing or HDR encoding appear here.",
                        style = AppTypography.bodySmall,
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
                items(completedVideos, key = { it.id }) { item ->
                    LibraryVideoCard(
                        item = item,
                        onPlay = { viewModel.playVideo(item) },
                        onShare = { viewModel.shareVideo(item) },
                        onExport = { viewModel.onExportToGallery(item) },
                        onDelete = { viewModel.onDelete(item.id) },
                        onClick = { onNavigateToDetails(item.id) }
                    )
                }
                item { Spacer(modifier = Modifier.height(UniSpacing.lg)) }
            }
        }
    }
}

@Composable
fun LibraryVideoCard(
    item: DownloadItem,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val colors = UniDownTheme.colors
    UniCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
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
                        maxLines = 2
                    )
                    if (item.author.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.author,
                            style = AppTypography.bodySmall,
                            color = colors.tertiaryText
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                UniStatusBadge(status = "SAVED")
            }

            Spacer(modifier = Modifier.height(UniSpacing.sm))

            // Technical Specs Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UniTechBadge(label = item.platform.displayName)
                Spacer(modifier = Modifier.width(6.dp))
                UniTechBadge(label = item.resolution, isHighlighted = true)
                Spacer(modifier = Modifier.width(6.dp))
                UniTechBadge(label = "${item.fps} FPS")
                Spacer(modifier = Modifier.width(6.dp))
                UniTechBadge(label = item.codec.uppercase())
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = Formatters.formatBytes(item.fileSizeBytes),
                    style = UniMonoTypography.TechnicalMedium,
                    color = colors.primaryText
                )
            }

            Spacer(modifier = Modifier.height(UniSpacing.md))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UniPrimaryButton(
                    onClick = onPlay,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PLAY", style = AppTypography.labelSmall)
                }

                UniOutlinedButton(
                    onClick = onExport,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("GALLERY", style = AppTypography.labelSmall)
                }

                UniOutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("SHARE", style = AppTypography.labelSmall)
                }

                UniOutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.width(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = colors.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
