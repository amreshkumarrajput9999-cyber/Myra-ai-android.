package com.myra.ai.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.myra.ai.R
import com.myra.ai.VoiceInteractionActivity

class MyraWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            // Intent to launch VoiceInteractionActivity with instant listening
            val intent = Intent(context, VoiceInteractionActivity::class.java).apply {
                action = VoiceInteractionActivity.ACTION_INSTANT_LISTEN
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val views = RemoteViews(context.packageName, R.layout.myra_widget_layout).apply {
                setOnClickPendingIntent(R.id.widget_container, pendingIntent)
                setOnClickPendingIntent(R.id.widget_mic_button, pendingIntent)
                setTextViewText(R.id.widget_status, "Tap to Speak")
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
