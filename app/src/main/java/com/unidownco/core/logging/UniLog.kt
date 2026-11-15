package com.unidownco.core.logging

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

data class LogEntry(
    val id: Long,
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String
) {
    fun formattedTime(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
    }

    fun toExportString(): String {
        return "[${formattedTime()}] [${level.name}] [$tag] $message"
    }
}

object UniLog {
    private const val MAX_MEMORY_LOGS = 500
    private const val MAX_LOG_FILE_BYTES = 2 * 1024 * 1024L // 2 MB
    private val nextId = AtomicLong(1)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = _logsFlow.asStateFlow()

    private var logFile: File? = null
    private var isInitialized = false

    // Sensitive patterns for redaction
    private val sensitivePatterns = listOf(
        Regex("(?i)(token|access_token|refresh_token|id_token)=([^&\\s]+)") to "$1=[REDACTED]",
        Regex("(?i)(api_?key|apikey)=([^&\\s]+)") to "$1=[REDACTED]",
        Regex("(?i)(secret|client_secret)=([^&\\s]+)") to "$1=[REDACTED]",
        Regex("(?i)(sig|signature)=([^&\\s]+)") to "$1=[REDACTED]",
        Regex("(?i)(password|passwd)=([^&\\s]+)") to "$1=[REDACTED]",
        Regex("(?i)(authorization:\\s*Bearer\\s+)([^\\s\r\n]+)") to "$1[REDACTED]",
        Regex("(?i)(cookie:\\s*)([^\r\n]+)") to "$1[REDACTED]"
    )

    fun init(context: Context) {
        if (isInitialized) return
        val logDir = File(context.cacheDir, "logs")
        if (!logDir.exists()) logDir.mkdirs()
        logFile = File(logDir, "unidownco.log")
        isInitialized = true
        i("UniLog", "Logger initialized. Bounded memory: $MAX_MEMORY_LOGS, Max disk: ${MAX_LOG_FILE_BYTES / 1024}KB")
    }

    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String, tr: Throwable? = null) = log(LogLevel.WARN, tag, formatMessage(message, tr))
    fun e(tag: String, message: String, tr: Throwable? = null) = log(LogLevel.ERROR, tag, formatMessage(message, tr))

    fun log(level: LogLevel, tag: String, rawMessage: String) {
        val sanitized = redact(rawMessage)

        // Forward to Android logcat
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, sanitized)
            LogLevel.INFO -> Log.i(tag, sanitized)
            LogLevel.WARN -> Log.w(tag, sanitized)
            LogLevel.ERROR -> Log.e(tag, sanitized)
        }

        val entry = LogEntry(
            id = nextId.getAndIncrement(),
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = sanitized
        )

        // Update in-memory flow thread-safely
        synchronized(_logsFlow) {
            val current = _logsFlow.value.toMutableList()
            if (current.size >= MAX_MEMORY_LOGS) {
                current.removeAt(0)
            }
            current.add(entry)
            _logsFlow.value = current
        }

        // Persist asynchronously to disk with rotation
        scope.launch {
            writeToFile(entry)
        }
    }

    fun redact(input: String): String {
        var result = input
        for ((pattern, replacement) in sensitivePatterns) {
            result = pattern.replace(result, replacement)
        }
        return result
    }

    private fun formatMessage(message: String, tr: Throwable?): String {
        return if (tr != null) {
            "$message\n${Log.getStackTraceString(tr)}"
        } else {
            message
        }
    }

    @Synchronized
    private fun writeToFile(entry: LogEntry) {
        val file = logFile ?: return
        try {
            if (file.exists() && file.length() > MAX_LOG_FILE_BYTES) {
                // Rotate log
                val backup = File(file.parentFile, "unidownco.log.1")
                if (backup.exists()) backup.delete()
                file.renameTo(backup)
            }
            PrintWriter(FileWriter(file, true)).use { out ->
                out.println(entry.toExportString())
            }
        } catch (_: Exception) {
            // Avoid recursion if log writing fails
        }
    }

    fun clearLogs() {
        synchronized(_logsFlow) {
            _logsFlow.value = emptyList()
        }
        scope.launch {
            try {
                logFile?.let { if (it.exists()) it.delete() }
                val backup = File(logFile?.parentFile, "unidownco.log.1")
                if (backup.exists()) backup.delete()
            } catch (_: Exception) { }
        }
    }

    fun exportToString(filterLevel: LogLevel? = null, searchQuery: String? = null): String {
        val currentLogs = _logsFlow.value
        val filtered = currentLogs.filter { entry ->
            val levelMatch = filterLevel == null || entry.level == filterLevel
            val queryMatch = searchQuery.isNullOrBlank() ||
                    entry.message.contains(searchQuery, ignoreCase = true) ||
                    entry.tag.contains(searchQuery, ignoreCase = true)
            levelMatch && queryMatch
        }

        val sb = StringBuilder()
        sb.append("=================================================================\n")
        sb.append("UniDownco Diagnostic Logs Export\n")
        sb.append("Export Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n")
        sb.append("Total Visible Entries: ${filtered.size} / ${currentLogs.size}\n")
        sb.append("=================================================================\n\n")

        for (entry in filtered) {
            sb.append(entry.toExportString()).append("\n")
        }
        return sb.toString()
    }
}
