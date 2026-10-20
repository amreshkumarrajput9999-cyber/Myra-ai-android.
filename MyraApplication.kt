package com.myra.ai

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class MyraApplication : Application() {

    companion object {
        const val CHANNEL_WAKE_WORD_SERVICE = "myra_wake_word_channel"
        const val CHANNEL_REMINDERS = "myra_reminders_channel"
        lateinit var instance: MyraApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Background wake-word listening service channel
            val serviceChannel = NotificationChannel(
                CHANNEL_WAKE_WORD_SERVICE,
                "MYRA Voice Assistant Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows persistent status when MYRA background wake-word listening is active"
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            // Reminders channel
            val reminderChannel = NotificationChannel(
                CHANNEL_REMINDERS,
                "MYRA Reminders & Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for reminders and scheduled tasks created with MYRA"
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(reminderChannel)
        }
    }
}
