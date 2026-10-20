package com.myra.ai.actions

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class ActionResult {
    data class Handled(val responseMessage: String) : ActionResult()
    data class RequiresConfirmation(val prompt: String, val action: () -> Unit) : ActionResult()
    data class RequiresDeviceUnlock(val message: String) : ActionResult()
    object NotHandled : ActionResult()
}

class DeviceActionHandler(private val context: Context) {

    private val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

    fun isDeviceLocked(): Boolean = keyguardManager.isKeyguardLocked

    fun processCommand(commandText: String, activity: Activity? = null): ActionResult {
        val lower = commandText.lowercase(Locale.ROOT).trim()

        // 1. Time & Date Queries
        if (lower.contains("time") || lower.contains("samay") || lower.contains("kitne baje")) {
            val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            return ActionResult.Handled("The current time is $time, Sir.")
        }

        // 2. Battery Status Query
        if (lower.contains("battery") || lower.contains("charge") || lower.contains("battery kitni")) {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            return ActionResult.Handled("Battery level is currently at $level percent, Sir.")
        }

        // 3. Open Camera
        if (lower.startsWith("open camera") || lower.contains("camera kholo") || lower.contains("take a picture")) {
            if (isDeviceLocked()) {
                // Android allows secure camera from lockscreen via MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE
                val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return ActionResult.Handled("Opening Camera now, Sir.")
            } else {
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return ActionResult.Handled("Opening Camera.")
            }
        }

        // 4. Open YouTube
        if (lower.contains("open youtube") || lower.contains("youtube kholo") || lower.contains("play on youtube")) {
            if (isDeviceLocked()) {
                return promptUnlock(activity, "Opening YouTube requires unlocking your device, Sir.")
            }
            return launchAppOrStore("com.google.android.youtube", "YouTube")
        }

        // 5. Open WhatsApp
        if (lower.contains("open whatsapp") || lower.contains("whatsapp kholo")) {
            if (isDeviceLocked()) {
                return promptUnlock(activity, "WhatsApp contains personal messages. Please unlock your device, Sir.")
            }
            return launchAppOrStore("com.whatsapp", "WhatsApp")
        }

        // 6. Open Google Maps / Navigation
        if (lower.contains("open maps") || lower.contains("open google maps") || lower.contains("maps kholo")) {
            if (isDeviceLocked()) {
                return promptUnlock(activity, "Please unlock your device to view navigation.")
            }
            return launchAppOrStore("com.google.android.apps.maps", "Google Maps")
        }

        // 7. Open Device Settings
        if (lower.contains("open settings") || lower.contains("phone settings")) {
            if (isDeviceLocked()) {
                return promptUnlock(activity, "Accessing device settings requires device authentication.")
            }
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            return ActionResult.Handled("Opening Device Settings, Sir.")
        }

        // 8. Set Timer or Alarm
        if (lower.contains("set alarm") || lower.contains("alarm lagao") || lower.contains("set a timer")) {
            // Safe to run even on lock screen using standard AlarmClock intent
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_MESSAGE, "MYRA Alarm")
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(intent)
                return ActionResult.Handled("Opening Alarm Clock to set your alarm, Sir.")
            } catch (e: Exception) {
                return ActionResult.Handled("Could not open Alarm application.")
            }
        }

        // 9. Phone Call / Dial (Requires Confirmation)
        if (lower.startsWith("call ") || lower.contains("phone lagao")) {
            val target = commandText.replace(Regex("(?i)^(call|phone lagao)\\s*"), "").trim()
            return ActionResult.RequiresConfirmation(
                prompt = "Would you like me to open the phone dialer for '$target'?"
            ) {
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$target")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(dialIntent)
            }
        }

        return ActionResult.NotHandled
    }

    private fun promptUnlock(activity: Activity?, message: String): ActionResult {
        activity?.let {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                keyguardManager.requestDismissKeyguard(it, null)
            }
        }
        return ActionResult.RequiresDeviceUnlock(message)
    }

    private fun launchAppOrStore(packageName: String, appName: String): ActionResult {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(packageName)
        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            ActionResult.Handled("Opening $appName, Sir.")
        } else {
            // App not installed: provide Play Store link
            val storeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(storeIntent)
                ActionResult.Handled("$appName is not installed. Opening Google Play Store.")
            } catch (e: Exception) {
                ActionResult.Handled("$appName is not installed on your device.")
            }
        }
    }
}
