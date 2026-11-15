package com.unidownco.core.common

import java.util.Locale

object Formatters {

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, index.toDouble()), units[index])
    }

    fun formatSpeed(bytesPerSec: Long): String {
        return "${formatBytes(bytesPerSec)}/s"
    }

    fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return "00:00"
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, secs)
        }
    }

    fun formatEta(seconds: Long): String {
        if (seconds <= 0) return "Done"
        if (seconds < 60) return "${seconds}s left"
        val minutes = seconds / 60
        val remainingSecs = seconds % 60
        return if (minutes < 60) {
            "${minutes}m ${remainingSecs}s left"
        } else {
            val hours = minutes / 60
            "${hours}h ${minutes % 60}m left"
        }
    }
}
