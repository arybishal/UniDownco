package com.unidownco.feature.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unidownco.core.design.components.UniAccentButton
import com.unidownco.core.design.components.UniCard
import com.unidownco.core.design.components.UniDestructiveButton
import com.unidownco.core.design.components.UniOutlinedButton
import com.unidownco.core.design.components.UniPrimaryButton
import com.unidownco.core.design.components.UniTechBadge
import com.unidownco.core.design.theme.AppTypography
import com.unidownco.core.design.theme.UniColors
import com.unidownco.core.design.theme.UniDownTheme
import com.unidownco.core.design.theme.UniMonoTypography
import com.unidownco.core.design.theme.UniShapes
import com.unidownco.core.design.theme.UniSpacing
import com.unidownco.core.logging.LogEntry
import com.unidownco.core.logging.LogLevel
import com.unidownco.core.logging.UniLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogViewerScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val colors = UniDownTheme.colors

    val allLogs by UniLog.logsFlow.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf<LogLevel?>(null) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    // SAF Document creation launcher for exporting log file
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val exportContent = UniLog.exportToString(selectedLevel, searchQuery)
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(exportContent.toByteArray(Charsets.UTF_8))
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Logs successfully exported to file!", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    UniLog.e("LogViewer", "Failed to export logs", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to export logs: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val filteredLogs = remember(allLogs, searchQuery, selectedLevel) {
        allLogs.filter { entry ->
            val levelMatches = selectedLevel == null || entry.level == selectedLevel
            val queryMatches = searchQuery.isBlank() ||
                    entry.message.contains(searchQuery, ignoreCase = true) ||
                    entry.tag.contains(searchQuery, ignoreCase = true)
            levelMatches && queryMatches
        }.reversed() // Show newest logs first
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Top App Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .border(1.dp, colors.subtleBorder)
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.primaryText
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DIAGNOSTIC LOGS",
                        style = AppTypography.headlineMedium,
                        color = colors.primaryText
                    )
                    Text(
                        text = "${filteredLogs.size} visible / ${allLogs.size} total entries",
                        style = UniMonoTypography.TechnicalSmall,
                        color = colors.tertiaryText
                    )
                }

                IconButton(
                    onClick = {
                        val text = UniLog.exportToString(selectedLevel, searchQuery)
                        clipboardManager.setText(AnnotatedString(text))
                        Toast.makeText(context, "Copied ${filteredLogs.size} log entries to clipboard", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy all logs",
                        tint = colors.primaryText
                    )
                }

                IconButton(
                    onClick = {
                        val timestamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
                        exportLauncher.launch("UniDownco_Logs_$timestamp.txt")
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "Export logs to .txt",
                        tint = colors.primaryText
                    )
                }

                IconButton(
                    onClick = { showClearConfirmDialog = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear logs",
                        tint = colors.error
                    )
                }
            }
        }

        // Search Input Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UniSpacing.screenHorizontalPadding, vertical = 8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("Filter logs by keyword or component...", style = AppTypography.bodySmall, color = colors.tertiaryText)
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = colors.secondaryText, modifier = Modifier.size(20.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = colors.secondaryText, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = UniShapes.ButtonShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Black,
                    unfocusedBorderColor = colors.subtleBorder,
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.surface
                )
            )
        }

        // Level Filter Chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UniSpacing.screenHorizontalPadding, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChipItem(
                    label = "ALL (${allLogs.size})",
                    isSelected = selectedLevel == null,
                    onClick = { selectedLevel = null }
                )
            }
            LogLevel.values().forEach { level ->
                val count = allLogs.count { it.level == level }
                item {
                    FilterChipItem(
                        label = "${level.name} ($count)",
                        isSelected = selectedLevel == level,
                        onClick = { selectedLevel = if (selectedLevel == level) null else level }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Log List
        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "NO MATCHING LOGS",
                        style = AppTypography.titleMedium,
                        color = colors.secondaryText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (allLogs.isEmpty()) "Logs will record here as network requests, extraction and downloads occur."
                        else "No log entries match the current filter or search criteria.",
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                items(filteredLogs, key = { it.id }) { logEntry ->
                    LogEntryCard(
                        entry = logEntry,
                        onCopyEntry = {
                            clipboardManager.setText(AnnotatedString(logEntry.toExportString()))
                            Toast.makeText(context, "Log entry copied", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.navigationBarsPadding())
                }
            }
        }
    }

    // Confirmation dialog for clearing logs
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Text("CLEAR DIAGNOSTIC LOGS?", style = AppTypography.titleLarge)
            },
            text = {
                Text(
                    "Are you sure you want to clear all in-memory and on-disk diagnostic records? This action cannot be undone.",
                    style = AppTypography.bodyMedium
                )
            },
            confirmButton = {
                UniDestructiveButton(
                    onClick = {
                        UniLog.clearLogs()
                        showClearConfirmDialog = false
                        Toast.makeText(context, "Diagnostic logs cleared", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("CLEAR ALL LOGS", style = AppTypography.labelSmall)
                }
            },
            dismissButton = {
                UniOutlinedButton(
                    onClick = { showClearConfirmDialog = false }
                ) {
                    Text("CANCEL", style = AppTypography.labelSmall)
                }
            },
            shape = UniShapes.BottomSheetShape,
            containerColor = colors.surface
        )
    }
}

@Composable
fun FilterChipItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = UniDownTheme.colors
    val bg = if (isSelected) UniColors.AccentYellow else colors.surfaceSubtle
    val fg = if (isSelected) Color.Black else colors.primaryText
    val border = if (isSelected) Color.Black else colors.subtleBorder

    Box(
        modifier = Modifier
            .background(bg, UniShapes.BadgeShape)
            .border(1.dp, border, UniShapes.BadgeShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = AppTypography.labelSmall,
            color = fg,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun LogEntryCard(
    entry: LogEntry,
    onCopyEntry: () -> Unit
) {
    val colors = UniDownTheme.colors
    var isExpanded by remember { mutableStateOf(false) }

    val (badgeBg, badgeFg) = when (entry.level) {
        LogLevel.DEBUG -> colors.surfaceSubtle to colors.tertiaryText
        LogLevel.INFO -> colors.surfaceSubtle to colors.primaryText
        LogLevel.WARN -> UniColors.AccentYellow to Color.Black
        LogLevel.ERROR -> colors.errorBackground to colors.error
    }

    UniCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (entry.level == LogLevel.ERROR) colors.error else colors.subtleBorder
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Level badge
                Box(
                    modifier = Modifier
                        .background(badgeBg, UniShapes.BadgeShape)
                        .border(1.dp, if (entry.level == LogLevel.WARN) Color.Black else badgeFg, UniShapes.BadgeShape)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = entry.level.name,
                        style = AppTypography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = badgeFg
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Component tag
                Text(
                    text = "[${entry.tag}]",
                    style = UniMonoTypography.TechnicalSmall,
                    color = colors.primaryText,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.weight(1f))

                // Timestamp
                Text(
                    text = entry.formattedTime().substringAfter(" "),
                    style = UniMonoTypography.TechnicalSmall.copy(fontSize = 10.sp),
                    color = colors.tertiaryText
                )

                Spacer(modifier = Modifier.width(6.dp))

                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy entry",
                    tint = colors.tertiaryText,
                    modifier = Modifier
                        .size(14.dp)
                        .clickable(onClick = onCopyEntry)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Log Message Body (clickable to expand long lines)
            Text(
                text = entry.message,
                style = UniMonoTypography.TechnicalSmall,
                color = if (entry.level == LogLevel.ERROR) colors.error else colors.secondaryText,
                maxLines = if (isExpanded) Int.MAX_VALUE else 4,
                modifier = Modifier.clickable { isExpanded = !isExpanded }
            )

            if (!isExpanded && entry.message.lines().size > 4) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "... tap to expand full message",
                    style = AppTypography.labelSmall.copy(fontSize = 10.sp),
                    color = colors.tertiaryText,
                    modifier = Modifier.clickable { isExpanded = true }
                )
            }
        }
    }
}
