package com.unidownco.engine.ytdlp

import android.content.Context
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages lifecycle, initialization, and dynamic updates for the bundled
 * yt-dlp and FFmpeg native Android runtime.
 */
object YtDlpEngineManager {

    private const val TAG = "YtDlpEngineManager"

    private val initDeferred = CompletableDeferred<Boolean>()
    private val isInitializing = AtomicBoolean(false)
    private val updateMutex = Mutex()

    @Volatile
    private var isEngineReady: Boolean = false

    @Volatile
    var initializationError: String? = null
        private set

    @Volatile
    var ytDlpVersion: String? = null
        private set

    /**
     * Initializes the yt-dlp Python runtime and FFmpeg binaries asynchronously.
     */
    fun init(context: Context) {
        if (isInitializing.compareAndSet(false, true)) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val startTime = System.currentTimeMillis()
                    val appContext = context.applicationContext
                    Log.i(TAG, "Initializing yt-dlp runtime...")
                    YoutubeDL.getInstance().init(appContext)

                    Log.i(TAG, "Initializing FFmpeg binary wrapper...")
                    FFmpeg.getInstance().init(appContext)

                    ytDlpVersion = try {
                        YoutubeDL.getInstance().version(appContext) ?: "bundled"
                    } catch (_: Exception) {
                        "bundled"
                    }

                    val elapsed = System.currentTimeMillis() - startTime
                    Log.i(TAG, "yt-dlp (v$ytDlpVersion) and FFmpeg initialized successfully in ${elapsed}ms")
                    isEngineReady = true
                    initDeferred.complete(true)
                } catch (e: Exception) {
                    val msg = e.message ?: "Unknown initialization failure"
                    Log.e(TAG, "Failed to initialize yt-dlp engine: $msg", e)
                    initializationError = msg
                    isEngineReady = false
                    initDeferred.complete(false)
                }
            }
        }
    }

    /**
     * Suspends until the engine is fully initialized.
     */
    suspend fun ensureInitialized(context: Context): Boolean {
        if (initDeferred.isCompleted) {
            return initDeferred.await()
        }
        init(context)
        return initDeferred.await()
    }

    fun isReady(): Boolean = isEngineReady

    /**
     * Checks for and downloads updates to the yt-dlp zipapp directly from upstream.
     */
    suspend fun updateEngine(context: Context): Result<String> = withContext(Dispatchers.IO) {
        updateMutex.withLock {
            try {
                ensureInitialized(context)
                val status = YoutubeDL.getInstance().updateYoutubeDL(
                    context.applicationContext,
                    YoutubeDL.UpdateChannel._STABLE
                )
                val newVersion = YoutubeDL.getInstance().version(context.applicationContext)
                ytDlpVersion = newVersion
                Log.i(TAG, "yt-dlp update status: $status, current version: $newVersion")
                Result.success("Updated yt-dlp to $newVersion ($status)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update yt-dlp engine", e)
                Result.failure(e)
            }
        }
    }
}
