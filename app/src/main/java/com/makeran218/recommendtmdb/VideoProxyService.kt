package com.makeran218.recommendtmdb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * Foreground Service that runs the LocalVideoProxy server.
 * This ensures the proxy keeps running even when the activity is in the background.
 */
class VideoProxyService : Service() {

    companion object {
        private const val TAG = "VideoProxyService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "video_proxy_channel"
        private const val CHANNEL_NAME = "Video Proxy Server"
        private const val CHANNEL_DESC = "Local YouTube video proxy server"

        fun startService(context: Context) {
            val intent = Intent(context, VideoProxyService::class.java)
            context.startForegroundService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, VideoProxyService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")

        // Create notification channel (required for Android 8.0+)
        createNotificationChannel()

        // Start the proxy server
        LocalVideoProxy.start(applicationContext)

        // Start as foreground service with notification
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    @Suppress("DEPRECATION")
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")
        LocalVideoProxy.stop()
        stopForeground(true)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = CHANNEL_DESC
            setShowBadge(false)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    fun createNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Video Proxy Server")
            .setContentText("Running on port 18080")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }
}
