package com.unidownco.engine.downloader

import com.unidownco.core.network.NetworkClient
import com.unidownco.domain.models.FormatOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile

data class DownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val progressPercent: Int,
    val speedBytesPerSec: Long,
    val etaSeconds: Long
)

class DownloadEngine {

    /**
     * Downloads a video stream to a target destination file with resume support,
     * speed estimation, and cancellation.
     */
    suspend fun downloadStream(
        format: FormatOption,
        destinationFile: File,
        onProgress: suspend (DownloadProgress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val partFile = File(destinationFile.parentFile, "${destinationFile.name}.part")
        var existingBytes = 0L

        if (partFile.exists()) {
            existingBytes = partFile.length()
        }

        val request = NetworkClient.newRequest(
            url = format.videoUrl,
            rangeStart = existingBytes
        )

        var response: Response? = null
        try {
            response = NetworkClient.execute(request)

            if (!response.isSuccessful && response.code != 206) {
                // If 416 (Range Not Satisfiable), restart from beginning
                if (response.code == 416) {
                    partFile.delete()
                    existingBytes = 0L
                    val freshReq = NetworkClient.newRequest(format.videoUrl, rangeStart = 0L)
                    response.close()
                    response = NetworkClient.execute(freshReq)
                } else {
                    return@withContext Result.failure(
                        Exception("HTTP error ${response.code}: ${response.message}")
                    )
                }
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Empty response body"))
            val contentLength = body.contentLength()
            val totalBytes = if (contentLength > 0) existingBytes + contentLength else format.estimatedSizeBytes

            val inputStream: InputStream = body.byteStream()
            val output = RandomAccessFile(partFile, "rw")
            output.seek(existingBytes)

            val buffer = ByteArray(64 * 1024) // 64KB chunk buffer
            var bytesRead: Int
            var totalReadBytes = existingBytes

            var lastTime = System.currentTimeMillis()
            var bytesSinceLastTime = 0L
            var currentSpeed = 0L

            try {
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (!coroutineContext.isActive) {
                        throw CancellationException("Download cancelled by user.")
                    }

                    output.write(buffer, 0, bytesRead)
                    totalReadBytes += bytesRead
                    bytesSinceLastTime += bytesRead

                    val now = System.currentTimeMillis()
                    val elapsed = now - lastTime
                    if (elapsed >= 500) { // Update metrics every 500ms
                        currentSpeed = (bytesSinceLastTime * 1000L) / elapsed
                        lastTime = now
                        bytesSinceLastTime = 0L

                        val progressPercent = if (totalBytes > 0) {
                            ((totalReadBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            0
                        }

                        val remainingBytes = if (totalBytes > totalReadBytes) totalBytes - totalReadBytes else 0L
                        val etaSeconds = if (currentSpeed > 0) remainingBytes / currentSpeed else 0L

                        onProgress(
                            DownloadProgress(
                                downloadedBytes = totalReadBytes,
                                totalBytes = totalBytes,
                                progressPercent = progressPercent,
                                speedBytesPerSec = currentSpeed,
                                etaSeconds = etaSeconds
                            )
                        )
                    }
                }
            } finally {
                output.close()
                inputStream.close()
            }

            // Verify completed part file
            if (!partFile.exists() || partFile.length() == 0L) {
                return@withContext Result.failure(Exception("Downloaded file is empty."))
            }

            // Rename .part to destinationFile
            if (destinationFile.exists()) destinationFile.delete()
            val renamed = partFile.renameTo(destinationFile)
            if (!renamed) {
                // If rename fails, copy and delete part
                partFile.copyTo(destinationFile, overwrite = true)
                partFile.delete()
            }

            // Final 100% progress update
            onProgress(
                DownloadProgress(
                    downloadedBytes = destinationFile.length(),
                    totalBytes = destinationFile.length(),
                    progressPercent = 100,
                    speedBytesPerSec = 0L,
                    etaSeconds = 0L
                )
            )

            Result.success(destinationFile)
        } catch (ce: CancellationException) {
            Result.failure(ce)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            response?.close()
        }
    }
}
