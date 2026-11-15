package com.unidownco.engine.instagram

import com.unidownco.core.logging.UniLog
import com.unidownco.domain.models.FormatOption
import com.unidownco.engine.downloader.DownloadEngine
import com.unidownco.engine.downloader.DownloadProgress
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Dedicated Instagram download engine coordinating progressive CDN downloads,
 * adaptive DASH stream assembly, and output verification.
 */
class InstagramDownloadEngine(
    private val downloadEngine: DownloadEngine = DownloadEngine()
) {

    companion object {
        private const val TAG = "InstagramDownloadEngine"
    }

    /**
     * Executes Instagram download to the destination file.
     * Prefers direct progressive stream download when available, falling back to
     * yt-dlp adaptive stream remuxing.
     */
    suspend fun downloadStream(
        format: FormatOption,
        destinationFile: File,
        originalUrl: String,
        downloadId: Long? = null,
        onProgress: suspend (DownloadProgress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val canonicalUrl = InstagramUrlResolver.normalizeUrl(originalUrl)

        val isDirectProgressive = (format.videoUrl.contains(".mp4", ignoreCase = true) ||
                format.videoUrl.contains("fbcdn.net", ignoreCase = true) ||
                format.videoUrl.contains("cdninstagram.com", ignoreCase = true)) &&
                format.audioUrl.isNullOrBlank()

        if (isDirectProgressive) {
            UniLog.i(TAG, "Attempting direct progressive stream download for Instagram media: ${format.resolution}")
            val directResult = downloadEngine.downloadSingleStream(
                streamUrl = format.videoUrl,
                estimatedBytes = format.estimatedSizeBytes,
                destinationFile = destinationFile,
                onProgress = onProgress
            )
            if (directResult.isSuccess && verifyOutputFile(destinationFile)) {
                UniLog.i(TAG, "Direct progressive download succeeded: ${destinationFile.name} (${destinationFile.length()} bytes)")
                return@withContext directResult
            }
            UniLog.w(TAG, "Direct stream download failed, falling back to yt-dlp runner for: $canonicalUrl")
        }

        // Adaptive DASH or paired stream fallback via yt-dlp runtime
        val targetDir = destinationFile.parentFile ?: return@withContext Result.failure(Exception("Invalid destination parent directory"))
        if (!targetDir.exists()) targetDir.mkdirs()

        val processId = "ig_dl_${downloadId ?: System.currentTimeMillis()}"
        val tempPrefix = "ig_temp_${downloadId ?: System.currentTimeMillis()}"
        val request = YoutubeDLRequest(canonicalUrl)
        request.addOption("-o", "${targetDir.absolutePath}/$tempPrefix.%(ext)s")

        if (format.formatId.isNotBlank() &&
            format.formatId != "ig_direct" &&
            format.formatId != "direct_0" &&
            format.formatId != "Standard"
        ) {
            request.addOption("-f", "${format.formatId}+bestaudio/best")
        } else {
            request.addOption("-f", "bestvideo+bestaudio/best")
        }

        request.addOption("--merge-output-format", "mp4")
        request.addOption("--no-playlist")
        request.addOption("--no-update")
        request.addOption("--no-mtime")
        request.addOption("--socket-timeout", "20")

        try {
            UniLog.i(TAG, "Executing yt-dlp Instagram download: format=${format.formatId}, processId=$processId")
            YoutubeDL.getInstance().execute(request, processId) { progress, eta, line ->
                val percent = progress.toInt().coerceIn(0, 100)
                val speed = DownloadEngine.parseSpeedFromLine(line)
                val estimatedTotal = if (format.estimatedSizeBytes > 0) format.estimatedSizeBytes else 15_000_000L
                val downloaded = (estimatedTotal * (percent / 100.0)).toLong()

                val progressObj = DownloadProgress(
                    downloadedBytes = downloaded,
                    totalBytes = estimatedTotal,
                    progressPercent = percent,
                    speedBytesPerSec = speed,
                    etaSeconds = eta,
                    statusMessage = line
                )
                runBlocking {
                    onProgress(progressObj)
                }
            }

            val downloadedFile = targetDir.listFiles { _, name ->
                name.startsWith(tempPrefix)
            }?.firstOrNull()

            if (downloadedFile != null && downloadedFile.exists() && downloadedFile.length() > 0) {
                if (destinationFile.exists()) destinationFile.delete()
                val renamed = downloadedFile.renameTo(destinationFile)
                if (!renamed) {
                    downloadedFile.copyTo(destinationFile, overwrite = true)
                    downloadedFile.delete()
                }

                if (verifyOutputFile(destinationFile)) {
                    UniLog.i(TAG, "yt-dlp Instagram download verified: ${destinationFile.name} (${destinationFile.length()} bytes)")
                    Result.success(destinationFile)
                } else {
                    val err = "Downloaded Instagram output file failed verification: zero bytes or missing."
                    UniLog.e(TAG, err)
                    destinationFile.delete()
                    Result.failure(Exception(err))
                }
            } else {
                val err = "yt-dlp Instagram download finished but output file not found in ${targetDir.absolutePath}"
                UniLog.e(TAG, err)
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            UniLog.e(TAG, "yt-dlp Instagram download execution failed: ${e.message}", e)
            cleanupTempFiles(targetDir, tempPrefix)
            val classified = InstagramMediaExtractor.classifyError(e.message, canonicalUrl)
            Result.failure(Exception(classified.second, e))
        }
    }

    private fun verifyOutputFile(file: File): Boolean {
        return file.exists() && file.isFile && file.length() > 1024L
    }

    private fun cleanupTempFiles(dir: File, prefix: String) {
        try {
            dir.listFiles { _, name -> name.startsWith(prefix) }?.forEach { it.delete() }
        } catch (_: Exception) {}
    }
}
