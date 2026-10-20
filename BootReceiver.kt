package com.myra.ai.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences(MyraWakeWordManager.PREFS_WAKE_WORD, Context.MODE_PRIVATE)
            val shouldStart = prefs.getBoolean(MyraWakeWordManager.KEY_ENABLED, false)
            if (shouldStart) {
                MyraForegroundService.start(context)
            }
        }
    }
}
