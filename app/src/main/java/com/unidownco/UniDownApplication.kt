package com.unidownco

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.unidownco.core.network.DownloadForegroundService
import com.unidownco.engine.ytdlp.YtDlpEngineManager

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
        createNotificationChannels()
        YtDlpEngineManager.init(this)
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
