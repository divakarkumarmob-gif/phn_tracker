package com.tracker.phonetracker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class PhoneTrackerApp : Application() {

    companion object {
        const val TRACKING_CHANNEL_ID = "tracking_channel"
        const val TRACKING_CHANNEL_NAME = "Live Location Tracking"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                TRACKING_CHANNEL_ID,
                TRACKING_CHANNEL_NAME,
                // Low importance ensures ongoing background notification doesn't make sound/vibrate every 10 seconds
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows persistent status while background location tracking is active"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
}
