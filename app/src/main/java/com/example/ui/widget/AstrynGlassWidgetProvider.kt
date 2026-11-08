package com.example.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R

/**
 * Android Home-Screen Glass Widget Provider for Astryn AI.
 *
 * Provides instant 1-tap home-screen access to:
 * 1. Live Voice Call with Aanya ("Aanya Call")
 * 2. Instant Voice Dictation ("Voice Mic")
 * 3. Fresh New Conversation ("New Chat")
 */
class AstrynGlassWidgetProvider : AppWidgetProvider() {

  companion object {
    const val ACTION_OPEN_LIVE_VOICE = "com.example.action.OPEN_LIVE_VOICE"
    const val ACTION_OPEN_VOICE_MIC = "com.example.action.OPEN_VOICE_MIC"
    const val ACTION_NEW_CHAT = "com.example.action.NEW_CHAT"

    fun updateAppWidget(
      context: Context,
      appWidgetManager: AppWidgetManager,
      appWidgetId: Int,
    ) {
      val views = RemoteViews(context.packageName, R.layout.widget_astryn_glass)

      // 1. Aanya Call PendingIntent
      val aanyaIntent =
        Intent(context, MainActivity::class.java).apply {
          action = ACTION_OPEN_LIVE_VOICE
          flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
      val aanyaPending =
        PendingIntent.getActivity(
          context,
          101,
          aanyaIntent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
      views.setOnClickPendingIntent(R.id.widget_btn_aanya, aanyaPending)

      // 2. Voice Mic PendingIntent
      val micIntent =
        Intent(context, MainActivity::class.java).apply {
          action = ACTION_OPEN_VOICE_MIC
          flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
      val micPending =
        PendingIntent.getActivity(
          context,
          102,
          micIntent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
      views.setOnClickPendingIntent(R.id.widget_btn_mic, micPending)

      // 3. New Chat PendingIntent
      val chatIntent =
        Intent(context, MainActivity::class.java).apply {
          action = ACTION_NEW_CHAT
          flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
      val chatPending =
        PendingIntent.getActivity(
          context,
          103,
          chatIntent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
      views.setOnClickPendingIntent(R.id.widget_btn_chat, chatPending)
      views.setOnClickPendingIntent(R.id.widget_root, chatPending)

      appWidgetManager.updateAppWidget(appWidgetId, views)
    }
  }

  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray,
  ) {
    for (appWidgetId in appWidgetIds) {
      updateAppWidget(context, appWidgetManager, appWidgetId)
    }
  }
}
