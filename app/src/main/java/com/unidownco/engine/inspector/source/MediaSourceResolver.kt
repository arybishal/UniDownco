package com.unidownco.engine.inspector.source

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.unidownco.core.logging.UniLog
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Resolves heterogeneous media assets (File, SAF URI, MediaStore URI, content URI)
 * into a uniform, readable [MediaSource].
 *
 * Adheres strictly to Android scoped storage rules without requesting broad permissions.
 */
object MediaSourceResolver {

    private const val TAG = "MediaSourceResolver"

    /**
     * Resolves a local or downloaded [File] into a [LocalFileMediaSource].
     */
    fun resolve(file: File): Result<MediaSource> {
        if (!file.exists()) {
            return Result.failure(FileNotFoundException("File does not exist: ${file.absolutePath}"))
        }
        if (!file.canRead()) {
            return Result.failure(SecurityException("Cannot read file (permission denied): ${file.absolutePath}"))
        }
        if (file.isDirectory) {
            return Result.failure(IllegalArgumentException("Target is a directory, not a media file: ${file.absolutePath}"))
        }

        val ext = file.extension.lowercase()
        val mime = runCatching { MimeTypeMap.getSingleton()?.getMimeTypeFromExtension(ext) }.getOrNull() ?: inferMimeFromExtension(ext)

        return Result.success(LocalFileMediaSource(file, mime))
    }

    /**
     * Resolves an Android [Uri] (file://, content://, SAF, MediaStore) into a [MediaSource].
     */
    fun resolve(uri: Uri, context: Context): Result<MediaSource> {
        val scheme = uri.scheme?.lowercase()

        if (scheme == "file") {
            val path = uri.path ?: return Result.failure(IllegalArgumentException("Invalid file URI with null path: $uri"))
            return resolve(File(path))
        }

        if (scheme != "content") {
            return Result.failure(IllegalArgumentException("Unsupported URI scheme '$scheme' for media source: $uri"))
        }

        var displayName = uri.lastPathSegment ?: "media_file"
        var sizeBytes = -1L

        // Query OpenableColumns if provider supports it
        try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1 && !cursor.isNull(nameIdx)) {
                        displayName = cursor.getString(nameIdx)
                    }
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx != -1 && !cursor.isNull(sizeIdx)) {
                        sizeBytes = cursor.getLong(sizeIdx)
                    }
                }
            }
        } catch (e: Exception) {
            UniLog.w(TAG, "ContentResolver metadata query failed for $uri: ${e.message}")
        }

        val mimeType = try {
            context.contentResolver.getType(uri)
        } catch (_: Exception) {
            null
        } ?: inferMimeFromExtension(displayName.substringAfterLast('.', ""))

        // Open ParcelFileDescriptor
        val pfd = try {
            context.contentResolver.openFileDescriptor(uri, "r")
        } catch (e: SecurityException) {
            return Result.failure(SecurityException("Permission denied to access content URI: $uri", e))
        } catch (e: FileNotFoundException) {
            return Result.failure(e)
        } catch (e: Exception) {
            return Result.failure(IOException("Failed opening descriptor for content URI $uri: ${e.message}", e))
        } ?: return Result.failure(FileNotFoundException("Content provider returned null descriptor for $uri"))

        if (sizeBytes <= 0L && pfd.statSize > 0L) {
            sizeBytes = pfd.statSize
        }

        return Result.success(
            ContentUriMediaSource(
                uri = uri,
                context = context,
                displayName = displayName,
                lengthBytes = sizeBytes,
                mimeType = mimeType,
                pfd = pfd
            )
        )
    }

    /**
     * Polymorphic resolver for arbitrary source objects (File, Uri, or String path).
     */
    fun resolveAny(source: Any, context: Context?): Result<MediaSource> {
        return when (source) {
            is File -> resolve(source)
            is Uri -> {
                if (context == null) Result.failure(IllegalArgumentException("Context required to resolve Uri: $source"))
                else resolve(source, context)
            }
            is String -> {
                if (source.startsWith("content://") || source.startsWith("file://")) {
                    if (context == null) Result.failure(IllegalArgumentException("Context required to resolve Uri string: $source"))
                    else resolve(Uri.parse(source), context)
                } else {
                    resolve(File(source))
                }
            }
            else -> Result.failure(IllegalArgumentException("Unsupported media source type: ${source::class.java.name}"))
        }
    }

    private fun inferMimeFromExtension(ext: String): String? = when (ext.lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "mov" -> "video/quicktime"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "3gp" -> "video/3gpp"
        "ts" -> "video/mp2t"
        "m4a", "aac" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "opus" -> "audio/opus"
        "flac" -> "audio/flac"
        "ogg" -> "audio/ogg"
        "wav" -> "audio/x-wav"
        else -> null
    }
}
