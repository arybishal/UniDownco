package com.unidownco.core.storage

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import com.unidownco.core.logging.UniLog
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StorageManager(private val context: Context) {

    companion object {
        const val DESTINATION_SUBDIR = "UniDownco"
        const val RELATIVE_MOVIES_PATH = "Movies/UniDownco"
        const val LEGACY_REL_PATH = "Movies/UniversalHDR"
    }

    private val downloadsDir: File
        get() {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "downloads")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    private val originalsDir: File
        get() {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "originals")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    /**
     * Creates an isolated file destination for a new download in app-private external storage.
     */
    fun createDownloadFile(title: String, extension: String = "mp4"): File {
        val sanitized = sanitizeFilename(title)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "${sanitized}_$timestamp.$extension"
        return File(downloadsDir, filename)
    }

    /**
     * Sanitizes strings to make them safe for local filesystem paths.
     */
    fun sanitizeFilename(raw: String): String {
        val clean = raw.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), "_")
            .trim('_')
        return if (clean.isEmpty()) "video" else clean.take(80)
    }

    /**
     * Checks if storage has at least minBytesAvailable free space.
     */
    fun hasAvailableSpace(minBytesRequired: Long = 100 * 1024 * 1024L): Boolean {
        return try {
            val stat = StatFs(downloadsDir.path)
            val available = stat.availableBlocksLong * stat.blockSizeLong
            available >= minBytesRequired
        } catch (_: Exception) {
            true
        }
    }

    fun getAvailableStorageBytes(): Long {
        return try {
            val stat = StatFs(downloadsDir.path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (_: Exception) {
            0L
        }
    }

    fun getTotalStorageBytes(): Long {
        return try {
            val stat = StatFs(downloadsDir.path)
            stat.blockCountLong * stat.blockSizeLong
        } catch (_: Exception) {
            0L
        }
    }

    fun getDestinationPathDisplay(): String = "$RELATIVE_MOVIES_PATH/"

    /**
     * Exports completed video to Android MediaStore in Movies/UniDownco/
     * so it appears in the device Gallery and file managers under a single consistent folder.
     */
    fun exportToGallery(sourceFile: File, title: String): Uri? {
        if (!sourceFile.exists()) {
            UniLog.e("StorageManager", "exportToGallery failed: sourceFile does not exist: ${sourceFile.absolutePath}")
            return null
        }

        val baseName = sanitizeFilename(title)
        val displayName = "$baseName.mp4"

        UniLog.i("StorageManager", "Exporting completed video to Gallery: $displayName -> $RELATIVE_MOVIES_PATH")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, RELATIVE_MOVIES_PATH)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val itemUri = resolver.insert(collection, values)
            if (itemUri == null) {
                UniLog.e("StorageManager", "Failed to insert MediaStore item for $displayName in $RELATIVE_MOVIES_PATH")
                return null
            }

            try {
                resolver.openOutputStream(itemUri)?.use { output ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(output)
                    }
                }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
                UniLog.i("StorageManager", "Successfully exported to MediaStore: $itemUri ($displayName)")
                return itemUri
            } catch (e: Exception) {
                UniLog.e("StorageManager", "Exception writing to MediaStore uri $itemUri", e)
                try {
                    resolver.delete(itemUri, null, null)
                } catch (_: Exception) {}
                return null
            }
        } else {
            // Pre-Android 10 legacy fallback
            val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), DESTINATION_SUBDIR)
            if (!publicDir.exists()) publicDir.mkdirs()

            // Resolve unique filename if collision occurs
            var targetFile = File(publicDir, displayName)
            var counter = 1
            while (targetFile.exists()) {
                targetFile = File(publicDir, "${baseName}_$counter.mp4")
                counter++
            }

            try {
                sourceFile.copyTo(targetFile, overwrite = true)
                MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), arrayOf("video/mp4"), null)
                UniLog.i("StorageManager", "Successfully copied to public storage: ${targetFile.absolutePath}")
                return Uri.fromFile(targetFile)
            } catch (e: Exception) {
                UniLog.e("StorageManager", "Failed to copy to public storage: ${targetFile.absolutePath}", e)
                return null
            }
        }
    }

    /**
     * Safely deletes a file from disk.
     */
    fun deleteFile(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val file = File(path)
        return if (file.exists()) file.delete() else false
    }
}
