package com.unidownco.feature.settings

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
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.unidownco.core.common.Formatters
import com.unidownco.core.design.components.UniCard
import com.unidownco.core.design.components.UniOutlinedButton
import com.unidownco.core.design.components.UniTechBadge
import com.unidownco.core.design.components.UniTopAppBar
import com.unidownco.core.design.theme.AppTypography
import com.unidownco.core.design.theme.UniColors
import com.unidownco.core.design.theme.UniDownTheme
import com.unidownco.core.design.theme.UniMonoTypography
import com.unidownco.core.design.theme.UniSpacing

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = UniDownTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        UniTopAppBar(
            title = "Settings",
            subtitle = "Downloader preferences, hardware profiles & storage"
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = UniSpacing.screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(UniSpacing.md)
        ) {
            item { Spacer(modifier = Modifier.height(UniSpacing.xs)) }

            // Automation & Pipeline Preferences
            item {
                UniCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = colors.primaryText,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "WORKFLOW AUTOMATION",
                                style = AppTypography.labelLarge,
                                color = colors.primaryText
                            )
                        }

                        Spacer(modifier = Modifier.height(UniSpacing.md))

                        SettingToggleRow(
                            title = "Auto-Analyze on Share",
                            subtitle = "Automatically inspect stream formats when shared from other apps",
                            checked = uiState.autoDownloadOnShare,
                            onCheckedChange = viewModel::onToggleAutoDownload
                        )

                        Spacer(modifier = Modifier.height(UniSpacing.sm))

                        SettingToggleRow(
                            title = "Auto-Encode to HEVC Main10",
                            subtitle = "Prepare videos for automatic hardware HEVC HDR transcoding (Step 2/3)",
                            checked = uiState.autoEncodeToHevc,
                            onCheckedChange = viewModel::onToggleAutoEncode
                        )

                        Spacer(modifier = Modifier.height(UniSpacing.sm))

                        SettingToggleRow(
                            title = "Download over Wi-Fi Only",
                            subtitle = "Pause queue when on metered mobile cellular data",
                            checked = uiState.wifiOnlyDownload,
                            onCheckedChange = viewModel::onToggleWifiOnly
                        )
                    }
                }
            }

            // Hardware Optimization Profile
            item {
                UniCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = colors.surfaceSubtle
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                tint = UniColors.AccentYellow,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "PRIMARY HARDWARE TARGET",
                                style = AppTypography.labelLarge,
                                color = colors.primaryText
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Target Device: OnePlus Ace 5 / OnePlus 13R\n" +
                                    "Chipset: Qualcomm Snapdragon 8 Gen 3 (SM8650-AB)\n" +
                                    "GPU: Adreno 750 (Vulkan 1.3 / OpenCL 3.0 / OpenGL ES 3.2)\n" +
                                    "Hardware HEVC: 10-bit Main10 c2.qti.hevc.encoder\n" +
                                    "Target Android: Android 15 (API 35+)",
                            style = UniMonoTypography.TechnicalSmall,
                            color = colors.secondaryText
                        )
                    }
                }
            }

            // Storage & Cache Management
            item {
                UniCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "STORAGE & CACHE",
                            style = AppTypography.labelLarge,
                            color = colors.primaryText
                        )

                        Spacer(modifier = Modifier.height(UniSpacing.sm))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Temporary Cache",
                                    style = AppTypography.bodyMedium,
                                    color = colors.primaryText
                                )
                                Text(
                                    text = Formatters.formatBytes(uiState.cacheSizeBytes),
                                    style = UniMonoTypography.TechnicalSmall,
                                    color = colors.tertiaryText
                                )
                            }

                            UniOutlinedButton(
                                onClick = viewModel::clearCache,
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CleaningServices,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("CLEAR", style = AppTypography.labelSmall)
                            }
                        }
                    }
                }
            }

            // About UniDownco
            item {
                UniCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = colors.primaryText,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ABOUT UNIDOWNCO",
                                style = AppTypography.labelLarge,
                                color = colors.primaryText
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "UniDownco v1.0.0 (Build 1)\n" +
                                    "Architecture: MVVM Clean Architecture\n" +
                                    "Target SDK: Android 15 (API 35)\n" +
                                    "Downloader Engine: Universal Extractor Engine\n" +
                                    "Database: Room 2.6.1\n" +
                                    "Design System: White Canvas, Black Structure, Yellow Accent",
                            style = UniMonoTypography.TechnicalSmall,
                            color = colors.secondaryText
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(UniSpacing.lg)) }
        }
    }
}

@Composable
fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = UniDownTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppTypography.bodyMedium,
                color = colors.primaryText
            )
            Text(
                text = subtitle,
                style = AppTypography.bodySmall,
                color = colors.tertiaryText
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = UniColors.AccentYellow,
                uncheckedThumbColor = colors.tertiaryText,
                uncheckedTrackColor = colors.surfaceSubtle
            )
        )
    }
}
