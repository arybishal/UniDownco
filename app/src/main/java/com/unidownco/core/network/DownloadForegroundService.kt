package com.unidownco.core.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.unidownco.MainActivity
import com.unidownco.R

class DownloadForegroundService : Service {

    constructor() : super()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val downloadId = intent?.getLongExtra(EXTRA_DOWNLOAD_ID, -1L) ?: -1L
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Downloading video"
        val progress = intent?.getIntExtra(EXTRA_PROGRESS, 0) ?: 0
        val speed = intent?.getStringExtra(EXTRA_SPEED) ?: ""

        when (action) {
            ACTION_START -> {
                val notification = buildNotification(downloadId, title, progress, speed)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
            ACTION_UPDATE -> {
                val notification = buildNotification(downloadId, title, progress, speed)
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(downloadId: Long, title: String, progress: Int, speed: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("UniDownco: $title")
            .setContentText(if (speed.isNotBlank()) "$progress% • $speed" else "$progress%")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Active Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time progress for active downloads"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "unidownco_downloads"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.unidownco.action.START_DOWNLOAD_SERVICE"
        const val ACTION_UPDATE = "com.unidownco.action.UPDATE_DOWNLOAD_PROGRESS"
        const val ACTION_STOP = "com.unidownco.action.STOP_DOWNLOAD_SERVICE"

        const val EXTRA_DOWNLOAD_ID = "extra_download_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_SPEED = "extra_speed"

        fun start(context: Context, downloadId: Long, title: String) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_PROGRESS, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateProgress(context: Context, downloadId: Long, title: String, progress: Int, speed: String) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_PROGRESS, progress)
                putExtra(EXTRA_SPEED, speed)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
