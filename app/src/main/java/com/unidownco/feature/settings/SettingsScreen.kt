package com.unidownco.feature.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.unidownco.core.common.Formatters
import com.unidownco.core.design.components.UniCard
import com.unidownco.core.design.components.UniOutlinedButton
import com.unidownco.core.design.components.UniPrimaryButton
import com.unidownco.core.design.components.UniTechBadge
import com.unidownco.core.design.components.UniTopAppBar
import com.unidownco.core.design.theme.AppTypography
import com.unidownco.core.design.theme.UniColors
import com.unidownco.core.design.theme.UniDownTheme
import com.unidownco.core.design.theme.UniMonoTypography
import com.unidownco.core.design.theme.UniSpacing
import com.unidownco.core.logging.UniLog
import com.unidownco.core.storage.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val allLogs by UniLog.logsFlow.collectAsState()
    val colors = UniDownTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showLogViewer by remember { mutableStateOf(false) }

    // Quick SAF exporter from settings
    val quickExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val exportText = UniLog.exportToString()
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(exportText.toByteArray(Charsets.UTF_8))
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Logs successfully exported!", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    if (showLogViewer) {
        LogViewerScreen(
            onNavigateBack = { showLogViewer = false }
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        UniTopAppBar(
            title = "Settings",
            subtitle = "Downloader preferences, storage destination & diagnostics"
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = UniSpacing.screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(UniSpacing.md)
        ) {
            item { Spacer(modifier = Modifier.height(UniSpacing.xs)) }

            // Storage Destination Card
            item {
                UniCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = colors.primaryText,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "DOWNLOAD DESTINATION",
                                style = AppTypography.labelLarge,
                                color = colors.primaryText
                            )
                        }

                        Spacer(modifier = Modifier.height(UniSpacing.sm))

                        Text(
                            text = "Standard Gallery Folder:",
                            style = AppTypography.bodySmall,
                            color = colors.tertiaryText
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${StorageManager.RELATIVE_MOVIES_PATH}/",
                            style = UniMonoTypography.TechnicalMedium,
                            color = colors.primaryText
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "All completed video downloads are verified and automatically published into this consistent folder for system Gallery and file manager access.",
                            style = AppTypography.bodySmall,
                            color = colors.secondaryText
                        )
                    }
                }
            }

            // Diagnostics & Log Management Card
            item {
                UniCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Article,
                                contentDescription = null,
                                tint = colors.primaryText,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "DIAGNOSTICS & SYSTEM LOGS",
                                style = AppTypography.labelLarge,
                                color = colors.primaryText
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            UniTechBadge(
                                label = "${allLogs.size} records",
                                isHighlighted = allLogs.any { it.level.name == "ERROR" }
                            )
                        }

                        Spacer(modifier = Modifier.height(UniSpacing.sm))

                        Text(
                            text = "Inspect background download events, network requests, platform extractions, and error traces. Redacts credentials and API keys automatically.",
                            style = AppTypography.bodySmall,
                            color = colors.secondaryText
                        )

                        Spacer(modifier = Modifier.height(UniSpacing.md))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            UniPrimaryButton(
                                onClick = { showLogViewer = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("VIEW LOGS", style = AppTypography.labelSmall)
                            }

                            UniOutlinedButton(
                                onClick = {
                                    val timestamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
                                    quickExportLauncher.launch("UniDownco_Logs_$timestamp.txt")
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("EXPORT (.TXT)", style = AppTypography.labelSmall)
                            }
                        }
                    }
                }
            }

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

            // Video Enhancement & HDR10 Configuration (Stage 2.3.1)
            item {
                val caps = uiState.hardwareCaps
                val supportsMain10 = caps.supportsHardwareMain10

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
                                text = "VIDEO ENHANCEMENT & HDR10",
                                style = AppTypography.labelLarge,
                                color = colors.primaryText
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            UniTechBadge(
                                label = if (supportsMain10) "MAIN10 ACTIVE" else "8-BIT HW ONLY",
                                isHighlighted = supportsMain10
                            )
                        }

                        Spacer(modifier = Modifier.height(UniSpacing.sm))

                        Text(
                            text = "Configure genuine 10-bit HEVC Main10 encoding, color spaces, and dynamic range expansion.",
                            style = AppTypography.bodySmall,
                            color = colors.secondaryText
                        )

                        Spacer(modifier = Modifier.height(UniSpacing.md))

                        // Hardware Capability Diagnostics Line
                        Text(
                            text = "Encoder: ${caps.preferredEncoderName}\n" +
                                    "Profiles: ${if (supportsMain10) "Main (8-bit), Main10 (10-bit)" else "Main (8-bit only)"}\n" +
                                    "Hardware Status: ${if (supportsMain10) "10-bit HDR hardware encoding available" else "10-bit HDR hardware encoding unsupported on this SoC (${caps.deviceModel}) - Main 8-bit SDR active"}",
                            style = UniMonoTypography.TechnicalSmall,
                            color = colors.tertiaryText
                        )

                        Spacer(modifier = Modifier.height(UniSpacing.md))

                        // SDR Option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onSelectHdrMode("OFF") }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.defaultHdrMode == "OFF",
                                onClick = { viewModel.onSelectHdrMode("OFF") },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color.Black,
                                    unselectedColor = colors.tertiaryText
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Standard SDR (Rec.709 8-bit)",
                                    style = AppTypography.bodyMedium,
                                    color = colors.primaryText
                                )
                                Text(
                                    text = "Encodes in universal 8-bit HEVC Main profile compatible with all screens",
                                    style = AppTypography.bodySmall,
                                    color = colors.tertiaryText
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // HDR10 Option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = supportsMain10) { viewModel.onSelectHdrMode("HDR10") }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.defaultHdrMode == "HDR10" && supportsMain10,
                                onClick = { if (supportsMain10) viewModel.onSelectHdrMode("HDR10") },
                                enabled = supportsMain10,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color.Black,
                                    unselectedColor = colors.tertiaryText,
                                    disabledSelectedColor = colors.tertiaryText,
                                    disabledUnselectedColor = colors.surfaceSubtle
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Genuine HDR10 (BT.2020 ST 2084 10-bit)",
                                        style = AppTypography.bodyMedium,
                                        color = if (supportsMain10) colors.primaryText else colors.tertiaryText
                                    )
                                    if (!supportsMain10) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        UniTechBadge(label = "UNSUPPORTED")
                                    }
                                }
                                Text(
                                    text = if (supportsMain10) {
                                        "Transforms Rec.709 to Rec.2020 wide gamut with ITU-R BT.2408 inverse tone mapping and static metadata"
                                    } else {
                                        "Hardware HEVC Main10 encoder is absent on this SoC. To prevent fake HDR files, this option is disabled."
                                    },
                                    style = AppTypography.bodySmall,
                                    color = colors.tertiaryText
                                )
                            }
                        }
                    }
                }
            }

            // Extraction & Media Runtime
            item {
                UniCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = colors.primaryText,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "EXTRACTION & MEDIA RUNTIME",
                                style = AppTypography.labelLarge,
                                color = colors.primaryText
                            )
                        }

                        Spacer(modifier = Modifier.height(UniSpacing.sm))

                        Text(
                            text = "Engine: yt-dlp Python Runtime & FFmpeg\n" +
                                    "Version: ${uiState.ytDlpVersion}\n" +
                                    "Architecture: arm64-v8a / Native Binaries\n" +
                                    "Adaptive Remuxing: Enabled",
                            style = UniMonoTypography.TechnicalSmall,
                            color = colors.secondaryText
                        )

                        Spacer(modifier = Modifier.height(UniSpacing.md))

                        UniOutlinedButton(
                            onClick = viewModel::updateYtDlp,
                            enabled = !uiState.isUpdatingYtDlp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (uiState.isUpdatingYtDlp) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.Black
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("CHECKING UPSTREAM UPDATES...", style = AppTypography.labelSmall)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("UPDATE YT-DLP RUNTIME", style = AppTypography.labelSmall)
                            }
                        }
                    }
                }
            }

            // Storage & Cache Management
            item {
                UniCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = null,
                                tint = colors.primaryText,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "STORAGE & CACHE",
                                style = AppTypography.labelLarge,
                                color = colors.primaryText
                            )
                        }

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
                                Text("CLEAR CACHE", style = AppTypography.labelSmall)
                            }
                        }
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
                            text = "UniDownco v1.0.3-step1.3.2\n" +
                                    "Architecture: MVVM Clean Architecture\n" +
                                    "Target SDK: Android 15 (API 35) • Tested on API 36\n" +
                                    "Downloader Engine: Universal Extractor + yt-dlp 0.18.1\n" +
                                    "Storage Path: Movies/UniDownco/\n" +
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
