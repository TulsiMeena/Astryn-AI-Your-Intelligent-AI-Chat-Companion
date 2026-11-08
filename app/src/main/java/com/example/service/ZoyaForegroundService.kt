package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.api.LiveGirlfriendMood
import com.example.data.api.LiveVoiceInteractionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground Service managing the Astryn AI — Zoya / Aanya Real-Time Bidirectional Live Voice
 * session across app backgrounding, screen locking, and notification controls.
 *
 * Complies with Android 10–15 (`API 29..36`) `FOREGROUND_SERVICE_TYPE_MICROPHONE` requirements:
 * - Verifies `RECORD_AUDIO` permission before promoting with `FOREGROUND_SERVICE_TYPE_MICROPHONE`
 * - Prevents duplicate foreground service instances or orphaned wake locks
 * - Provides notification actions for Mute/Unmute and End Call
 */
class ZoyaForegroundService : Service() {

  inner class LocalBinder : Binder() {
    fun getService(): ZoyaForegroundService = this@ZoyaForegroundService
  }

  private val binder = LocalBinder()
  private var wakeLock: PowerManager.WakeLock? = null
  private var currentMoodName: String = LiveGirlfriendMood.NATKHAT.name
  private var currentInteractionState: String = LiveVoiceInteractionState.LISTENING.badgeText
  private var isMuted: Boolean = false

  companion object {
    const val CHANNEL_ID = "astryn_zoya_live_voice_channel"
    const val NOTIFICATION_ID = 4409

    const val ACTION_START_OR_UPDATE = "com.example.service.action.START_OR_UPDATE_ZOYA_VOICE"
    const val ACTION_TOGGLE_MUTE = "com.example.service.action.TOGGLE_MUTE_ZOYA_VOICE"
    const val ACTION_STOP_SERVICE = "com.example.service.action.STOP_ZOYA_VOICE"

    const val EXTRA_MOOD_NAME = "extra_mood_name"
    const val EXTRA_STATUS_TEXT = "extra_status_text"
    const val EXTRA_IS_MUTED = "extra_is_muted"

    private val _isRunningFlow = MutableStateFlow(false)
    val isRunningFlow: StateFlow<Boolean> = _isRunningFlow.asStateFlow()

    @Volatile var onNotificationToggleMute: (() -> Unit)? = null
    @Volatile var onNotificationEndCall: (() -> Unit)? = null

    /**
     * Starts or updates the [ZoyaForegroundService] safely when `RECORD_AUDIO` permission is granted.
     */
    fun startOrUpdate(
      context: Context,
      mood: LiveGirlfriendMood,
      statusText: String,
      isMuted: Boolean,
    ) {
      val hasAudioPerm =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
          PackageManager.PERMISSION_GRANTED
      if (!hasAudioPerm) {
        // Android 14+ prohibits starting a microphone foreground service without RECORD_AUDIO
        return
      }

      val intent =
        Intent(context, ZoyaForegroundService::class.java).apply {
          action = ACTION_START_OR_UPDATE
          putExtra(EXTRA_MOOD_NAME, mood.badge)
          putExtra(EXTRA_STATUS_TEXT, statusText)
          putExtra(EXTRA_IS_MUTED, isMuted)
        }
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context.startForegroundService(intent)
        } else {
          context.startService(intent)
        }
      } catch (_: Exception) {
        // Fallback if app is in background restricted state
        try {
          context.startService(intent)
        } catch (_: Exception) {}
      }
    }

    /**
     * Stops the [ZoyaForegroundService] cleanly.
     */
    fun stop(context: Context) {
      try {
        val intent = Intent(context, ZoyaForegroundService::class.java)
        context.stopService(intent)
      } catch (_: Exception) {}
      _isRunningFlow.value = false
    }
  }

  override fun onCreate() {
    super.onCreate()
    createNotificationChannelIfNeeded()
  }

  override fun onBind(intent: Intent?): IBinder = binder

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP_SERVICE -> {
        onNotificationEndCall?.invoke()
        stopSelfCleanly()
        return START_NOT_STICKY
      }

      ACTION_TOGGLE_MUTE -> {
        onNotificationToggleMute?.invoke()
        isMuted = !isMuted
        val notification = buildNotification(currentMoodName, currentInteractionState, isMuted)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(NOTIFICATION_ID, notification)
        return START_NOT_STICKY
      }

      else -> {
        currentMoodName =
          intent?.getStringExtra(EXTRA_MOOD_NAME) ?: LiveGirlfriendMood.NATKHAT.badge
        currentInteractionState =
          intent?.getStringExtra(EXTRA_STATUS_TEXT)
            ?: LiveVoiceInteractionState.LISTENING.badgeText
        isMuted = intent?.getBooleanExtra(EXTRA_IS_MUTED, false) ?: false

        val hasAudioPerm =
          ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasAudioPerm) {
          stopSelfCleanly()
          return START_NOT_STICKY
        }

        val notification = buildNotification(currentMoodName, currentInteractionState, isMuted)
        try {
          val fgsType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
              ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
              0
            }
          ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
          _isRunningFlow.value = true
          acquireWakeLockIfNeeded()
        } catch (_: Exception) {
          stopSelfCleanly()
          return START_NOT_STICKY
        }
      }
    }
    return START_NOT_STICKY
  }

  private fun acquireWakeLockIfNeeded() {
    if (wakeLock?.isHeld == true) return
    try {
      val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
      wakeLock =
        pm.newWakeLock(
          PowerManager.PARTIAL_WAKE_LOCK,
          "AstrynAI:ZoyaLiveVoiceWakeLock",
        ).apply {
          setReferenceCounted(false)
          acquire(30 * 60 * 1000L) // Max 30 minutes safety timeout
        }
    } catch (_: Exception) {}
  }

  private fun releaseWakeLock() {
    try {
      val wl = wakeLock
      wakeLock = null
      if (wl != null && wl.isHeld) {
        wl.release()
      }
    } catch (_: Exception) {}
  }

  private fun stopSelfCleanly() {
    releaseWakeLock()
    _isRunningFlow.value = false
    try {
      ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    } catch (_: Exception) {}
    try {
      stopSelf()
    } catch (_: Exception) {}
  }

  override fun onDestroy() {
    releaseWakeLock()
    _isRunningFlow.value = false
    super.onDestroy()
  }

  private fun createNotificationChannelIfNeeded() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
      val existing = nm.getNotificationChannel(CHANNEL_ID)
      if (existing == null) {
        val channel =
          NotificationChannel(
            CHANNEL_ID,
            "Astryn AI Live Voice Call",
            NotificationManager.IMPORTANCE_LOW,
          ).apply {
            description = "Active bidirectional live voice session with Aanya"
            setShowBadge(false)
          }
        nm.createNotificationChannel(channel)
      }
    }
  }

  private fun buildNotification(
    moodBadge: String,
    statusText: String,
    muted: Boolean,
  ): Notification {
    val openAppIntent =
      Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
    val openPendingIntent =
      PendingIntent.getActivity(
        this,
        201,
        openAppIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val muteIntent =
      Intent(this, ZoyaForegroundService::class.java).apply {
        action = ACTION_TOGGLE_MUTE
      }
    val mutePendingIntent =
      PendingIntent.getService(
        this,
        202,
        muteIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val stopIntent =
      Intent(this, ZoyaForegroundService::class.java).apply {
        action = ACTION_STOP_SERVICE
      }
    val stopPendingIntent =
      PendingIntent.getService(
        this,
        203,
        stopIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val subtitle =
      if (muted) "Mic Muted • $moodBadge" else "$statusText • $moodBadge"

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle("Aanya • Astryn Live Voice")
      .setContentText(subtitle)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setCategory(NotificationCompat.CATEGORY_CALL)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setContentIntent(openPendingIntent)
      .addAction(
        0,
        if (muted) "Unmute" else "Mute",
        mutePendingIntent,
      )
      .addAction(
        0,
        "End Call",
        stopPendingIntent,
      )
      .build()
  }
}
