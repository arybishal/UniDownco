package com.unidownco.engine.downloader

import android.util.Log
import com.unidownco.core.network.NetworkClient
import com.unidownco.domain.models.FormatOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.Response
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile

data class DownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val progressPercent: Int,
    val speedBytesPerSec: Long,
    val etaSeconds: Long,
    val statusMessage: String? = null
)

class DownloadEngine {

    companion object {
        private const val TAG = "DownloadEngine"
        private const val BUFFER_SIZE = 64 * 1024 // 64 KB
    }

    /**
     * Downloads a video to the destination file.
     * If the format provides separate video and audio URLs (DASH/adaptive streams),
     * downloads both streams concurrently or sequentially and losslessly muxes them via MediaStreamMuxer.
     */
    suspend fun downloadStream(
        format: FormatOption,
        destinationFile: File,
        onProgress: suspend (DownloadProgress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val audioUrl = format.audioUrl

        if (!audioUrl.isNullOrBlank()) {
            // Dual stream: video + separate audio
            downloadDualStreamAndMux(format, destinationFile, onProgress)
        } else {
            // Single combined stream
            downloadSingleStream(
                streamUrl = format.videoUrl,
                estimatedBytes = format.estimatedSizeBytes,
                destinationFile = destinationFile,
                onProgress = onProgress
            )
        }
    }

    private suspend fun downloadSingleStream(
        streamUrl: String,
        estimatedBytes: Long,
        destinationFile: File,
        onProgress: suspend (DownloadProgress) -> Unit
    ): Result<File> {
        val partFile = File(destinationFile.parentFile, "${destinationFile.name}.part")
        var existingBytes = 0L

        if (partFile.exists()) {
            existingBytes = partFile.length()
        }

        val request = NetworkClient.newRequest(
            url = streamUrl,
            rangeStart = existingBytes
        )

        var response: Response? = null
        return try {
            response = NetworkClient.execute(request)

            if (!response.isSuccessful && response.code != 206) {
                if (response.code == 416) {
                    // Range Not Satisfiable, restart fresh
                    partFile.delete()
                    existingBytes = 0L
                    val freshReq = NetworkClient.newRequest(streamUrl, rangeStart = 0L)
                    response.close()
                    response = NetworkClient.execute(freshReq)
                } else {
                    return Result.failure(Exception("HTTP error ${response.code}: ${response.message}"))
                }
            }

            val body = response.body ?: return Result.failure(Exception("Empty response body from server"))
            val contentLength = body.contentLength()
            val totalBytes = if (contentLength > 0) existingBytes + contentLength else estimatedBytes

            val inputStream: InputStream = body.byteStream()
            val output = RandomAccessFile(partFile, "rw")
            output.seek(existingBytes)

            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            var totalReadBytes = existingBytes

            var lastTime = System.currentTimeMillis()
            var bytesSinceLastTime = 0L
            var currentSpeed = 0L

            try {
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (!kotlin.coroutines.coroutineContext.isActive) {
                        throw CancellationException("Download cancelled by user.")
                    }

                    output.write(buffer, 0, bytesRead)
                    totalReadBytes += bytesRead
                    bytesSinceLastTime += bytesRead

                    val now = System.currentTimeMillis()
                    val elapsed = now - lastTime
                    if (elapsed >= 500) {
                        currentSpeed = (bytesSinceLastTime * 1000L) / elapsed
                        lastTime = now
                        bytesSinceLastTime = 0L

                        val progressPercent = if (totalBytes > 0) {
                            ((totalReadBytes * 100L) / totalBytes).toInt().coerceIn(0, 99)
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

            if (!partFile.exists() || partFile.length() == 0L) {
                return Result.failure(Exception("Downloaded part file is empty."))
            }

            if (destinationFile.exists()) destinationFile.delete()
            val renamed = partFile.renameTo(destinationFile)
            if (!renamed) {
                partFile.copyTo(destinationFile, overwrite = true)
                partFile.delete()
            }

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

    private suspend fun downloadDualStreamAndMux(
        format: FormatOption,
        destinationFile: File,
        onProgress: suspend (DownloadProgress) -> Unit
    ): Result<File> {
        val videoPart = File(destinationFile.parentFile, "${destinationFile.name}.video.part")
        val audioPart = File(destinationFile.parentFile, "${destinationFile.name}.audio.part")

        Log.i(TAG, "Starting dual-stream download: video=${format.videoUrl}, audio=${format.audioUrl}")

        // 1. Download video stream (progress mapped 0% to 75%)
        val videoResult = downloadStreamToFile(
            url = format.videoUrl,
            targetFile = videoPart,
            onSubProgress = { downloaded, total, speed, eta ->
                val totalEst = if (total > 0) total else (format.estimatedSizeBytes * 0.8).toLong()
                val pct = if (totalEst > 0) ((downloaded * 75L) / totalEst).toInt().coerceIn(0, 75) else 0
                onProgress(
                    DownloadProgress(
                        downloadedBytes = downloaded,
                        totalBytes = totalEst,
                        progressPercent = pct,
                        speedBytesPerSec = speed,
                        etaSeconds = eta,
                        statusMessage = "Downloading video stream ($pct%)"
                    )
                )
            }
        )

        if (videoResult.isFailure) {
            return Result.failure(videoResult.exceptionOrNull() ?: Exception("Video stream download failed"))
        }

        // 2. Download audio stream (progress mapped 75% to 92%)
        val audioResult = downloadStreamToFile(
            url = format.audioUrl!!,
            targetFile = audioPart,
            onSubProgress = { downloaded, total, speed, eta ->
                val totalEst = if (total > 0) total else (format.estimatedSizeBytes * 0.2).toLong()
                val subPct = if (totalEst > 0) ((downloaded * 17L) / totalEst).toInt().coerceIn(0, 17) else 0
                val pct = 75 + subPct
                onProgress(
                    DownloadProgress(
                        downloadedBytes = videoPart.length() + downloaded,
                        totalBytes = videoPart.length() + totalEst,
                        progressPercent = pct,
                        speedBytesPerSec = speed,
                        etaSeconds = eta,
                        statusMessage = "Downloading audio stream ($pct%)"
                    )
                )
            }
        )

        if (audioResult.isFailure) {
            return Result.failure(audioResult.exceptionOrNull() ?: Exception("Audio stream download failed"))
        }

        // 3. Mux video and audio into destinationFile
        onProgress(
            DownloadProgress(
                downloadedBytes = videoPart.length() + audioPart.length(),
                totalBytes = videoPart.length() + audioPart.length(),
                progressPercent = 95,
                speedBytesPerSec = 0L,
                etaSeconds = 0L,
                statusMessage = "Muxing audio & video streams..."
            )
        )

        val muxResult = MediaStreamMuxer.mux(
            videoFile = videoPart,
            audioFile = audioPart,
            outputFile = destinationFile
        )

        if (muxResult.isSuccess) {
            // Clean up temporary elementary stream part files
            videoPart.delete()
            audioPart.delete()

            onProgress(
                DownloadProgress(
                    downloadedBytes = destinationFile.length(),
                    totalBytes = destinationFile.length(),
                    progressPercent = 100,
                    speedBytesPerSec = 0L,
                    etaSeconds = 0L,
                    statusMessage = "Complete"
                )
            )
            return Result.success(destinationFile)
        } else {
            return Result.failure(muxResult.exceptionOrNull() ?: Exception("Failed to mux streams into MP4"))
        }
    }

    private suspend fun downloadStreamToFile(
        url: String,
        targetFile: File,
        onSubProgress: suspend (downloaded: Long, total: Long, speed: Long, eta: Long) -> Unit
    ): Result<File> {
        var existingBytes = 0L
        if (targetFile.exists()) {
            existingBytes = targetFile.length()
        }

        val request = NetworkClient.newRequest(url = url, rangeStart = existingBytes)
        var response: Response? = null

        return try {
            response = NetworkClient.execute(request)

            if (!response.isSuccessful && response.code != 206) {
                if (response.code == 416) {
                    targetFile.delete()
                    existingBytes = 0L
                    val freshReq = NetworkClient.newRequest(url, rangeStart = 0L)
                    response.close()
                    response = NetworkClient.execute(freshReq)
                } else {
                    return Result.failure(Exception("HTTP error ${response.code}: ${response.message}"))
                }
            }

            val body = response.body ?: return Result.failure(Exception("Empty response body"))
            val contentLength = body.contentLength()
            val totalBytes = if (contentLength > 0) existingBytes + contentLength else 0L

            val inputStream: InputStream = body.byteStream()
            val output = RandomAccessFile(targetFile, "rw")
            output.seek(existingBytes)

            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            var totalReadBytes = existingBytes

            var lastTime = System.currentTimeMillis()
            var bytesSinceLastTime = 0L
            var currentSpeed = 0L

            try {
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (!kotlin.coroutines.coroutineContext.isActive) {
                        throw CancellationException("Download cancelled.")
                    }

                    output.write(buffer, 0, bytesRead)
                    totalReadBytes += bytesRead
                    bytesSinceLastTime += bytesRead

                    val now = System.currentTimeMillis()
                    val elapsed = now - lastTime
                    if (elapsed >= 500) {
                        currentSpeed = (bytesSinceLastTime * 1000L) / elapsed
                        lastTime = now
                        bytesSinceLastTime = 0L

                        val remainingBytes = if (totalBytes > totalReadBytes) totalBytes - totalReadBytes else 0L
                        val etaSeconds = if (currentSpeed > 0) remainingBytes / currentSpeed else 0L

                        onSubProgress(totalReadBytes, totalBytes, currentSpeed, etaSeconds)
                    }
                }
            } finally {
                output.close()
                inputStream.close()
            }

            Result.success(targetFile)
        } catch (ce: CancellationException) {
            Result.failure(ce)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            response?.close()
        }
    }
}
