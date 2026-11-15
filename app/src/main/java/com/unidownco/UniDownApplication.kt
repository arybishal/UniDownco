package com.unidownco

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.unidownco.core.network.DownloadForegroundService

class UniDownApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
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
