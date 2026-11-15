package com.unidownco

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.unidownco.core.logging.UniLog
import com.unidownco.core.network.DownloadForegroundService
import com.unidownco.engine.hardware.HardwareCapabilityRegistry
import com.unidownco.engine.ytdlp.YtDlpEngineManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class UniDownApplication : Application() {

    companion object {
        lateinit var appContext: Context
            private set

        val isYtDlpInitialized: Boolean
            get() = YtDlpEngineManager.isReady()
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        UniLog.init(this)
        createNotificationChannels()
        YtDlpEngineManager.init(this)

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val report = HardwareCapabilityRegistry.getDiagnosticReport()
                UniLog.i("HardwareProfile", "\n$report")
            } catch (e: Exception) {
                UniLog.e("HardwareProfile", "Failed to probe hardware capabilities", e)
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DownloadForegroundService.CHANNEL_ID,
                "Downloads & Processing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live download and video processing notifications"
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
