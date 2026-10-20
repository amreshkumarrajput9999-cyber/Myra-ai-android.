package com.myra.ai.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.myra.ai.MainActivity
import com.myra.ai.MyraApplication
import com.myra.ai.R
import com.myra.ai.VoiceInteractionActivity

class MyraForegroundService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.myra.ai.action.START_SERVICE"
        const val ACTION_STOP = "com.myra.ai.action.STOP_SERVICE"
        const val ACTION_TRIGGER_VOICE = "com.myra.ai.action.TRIGGER_VOICE"

        fun start(context: Context) {
            val intent = Intent(context, MyraForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MyraForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var wakeWordManager: MyraWakeWordManager? = null

    override fun onCreate() {
        super.onCreate()
        wakeWordManager = MyraWakeWordManager(this) { wakeWord ->
            // Launch VoiceInteractionActivity immediately upon wake word
            val voiceIntent = Intent(this, VoiceInteractionActivity::class.java).apply {
                action = VoiceInteractionActivity.ACTION_INSTANT_LISTEN
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(voiceIntent)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                wakeWordManager?.stopListening()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TRIGGER_VOICE -> {
                val voiceIntent = Intent(this, VoiceInteractionActivity::class.java).apply {
                    action = VoiceInteractionActivity.ACTION_INSTANT_LISTEN
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(voiceIntent)
            }
            else -> {
                startForegroundWithNotification()
                wakeWordManager?.startListening()
            }
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        // Tap notification to open MainActivity
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Talk Now
        val talkNowIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, VoiceInteractionActivity::class.java).apply {
                action = VoiceInteractionActivity.ACTION_INSTANT_LISTEN
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Stop Listening
        val stopServiceIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, MyraForegroundService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, MyraApplication.CHANNEL_WAKE_WORD_SERVICE)
            .setSmallIcon(R.drawable.ic_myra_mic)
            .setContentTitle("MYRA AI is listening")
            .setContentText("Say \"Hey MYRA\" or tap to talk")
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_myra_mic, "Talk Now", talkNowIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopServiceIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        wakeWordManager?.stopListening()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
