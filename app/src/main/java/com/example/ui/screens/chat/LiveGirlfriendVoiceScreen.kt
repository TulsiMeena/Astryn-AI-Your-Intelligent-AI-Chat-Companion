package com.example.ui.screens.chat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.api.GeminiLiveVoiceSession
import com.example.data.api.LiveConnectionState
import com.example.data.api.LiveGirlfriendMood
import com.example.data.api.LiveVoiceInteractionState
import com.example.service.ZoyaForegroundService
import com.example.ui.theme.AppBorder
import com.example.ui.theme.AppRadius
import com.example.ui.theme.AppSpacing
import com.example.ui.theme.LocalReducedMotion
import kotlinx.coroutines.delay

/**
 * Full-Screen Futuristic JARVIS-Level Voice-to-Voice Live AI Assistant Screen ("Aanya • JARVIS Core").
 *
 * Features:
 * - Speaks fluent, natural Hindi & Hinglish with JARVIS-grade analytical intelligence and live web grounding.
 * - Holographic Arc-Reactor Centerpiece (`220.dp`) with counter-rotating cybernetic HUD rings and
 *   real-time audio spectrum waveform bars.
 * - Instant Barge-In: Tapping the Arc-Reactor or Mic immediately interrupts ongoing speech and starts
 *   listening to the user's new Hindi/Hinglish voice command.
 * - Clean, uncluttered layout with a 3-button bottom call dock (Mute, Center Arc-Mic, End Call).
 */
@Composable
fun LiveGirlfriendVoiceCallScreen(
  selectedMood: LiveGirlfriendMood,
  isListeningVoice: Boolean,
  isGenerating: Boolean,
  isSpeakingVoice: Boolean,
  autoHandsFree: Boolean,
  lastUserSpokenText: String,
  lastGirlfriendReplyText: String,
  onSelectMood: (LiveGirlfriendMood) -> Unit,
  onToggleTalkMic: () -> Unit,
  onToggleAutoHandsFree: () -> Unit,
  onReplayLastVoice: () -> Unit,
  onSendLivePrompt: (String) -> Unit,
  onEndCall: () -> Unit,
  modifier: Modifier = Modifier,
  isWhisperMode: Boolean = false,
  onToggleWhisperMode: (Boolean) -> Unit = {},
  onStopFallbackTts: () -> Unit = {},
  onSpeakFallbackTts: (String, LiveGirlfriendMood) -> Unit = { _, _ -> },
  onNativeAudioActiveChanged: (Boolean) -> Unit = {},
) {
  val context = LocalContext.current
  val reducedMotion = LocalReducedMotion.current
  var callSeconds by remember { mutableIntStateOf(0) }
  var permissionFeedbackMessage by remember { mutableStateOf<String?>(null) }

  // Real-time Bidirectional Gemini Live WebSocket + AudioRecord/AudioTrack session
  val liveWsSession = remember {
    GeminiLiveVoiceSession(
      onNativeAudioPlayingChanged = { isPlayingNative ->
        onNativeAudioActiveChanged(isPlayingNative)
        if (isPlayingNative) {
          onStopFallbackTts()
        }
      },
      onLiveTurnCompleteWithAudioFlag = { completedText, playedNativeAudio ->
        if (!playedNativeAudio && completedText.isNotBlank()) {
          onSpeakFallbackTts(completedText, selectedMood)
        }
      },
      onInterrupted = {
        onStopFallbackTts()
      },
      onMoodChangedByTool = { toolMood ->
        onSelectMood(toolMood)
      },
    )
  }

  val liveSnap by liveWsSession.stateFlow.collectAsState()

  val isEffectiveListening =
    (liveSnap.isMicRecording && !liveSnap.isMicMuted) || isListeningVoice
  val isEffectiveSpeaking = liveSnap.isPlayingNativeAudio || isSpeakingVoice
  val isEffectiveProcessing =
    liveSnap.interactionState == LiveVoiceInteractionState.PROCESSING || isGenerating

  val notificationPermissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

  val audioPermissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
      if (granted) {
        permissionFeedbackMessage = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          val hasNotifPerm =
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
              PackageManager.PERMISSION_GRANTED
          if (!hasNotifPerm) {
            try {
              notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } catch (_: Exception) {}
          }
        }
        ZoyaForegroundService.startOrUpdate(
          context = context,
          mood = selectedMood,
          statusText = LiveVoiceInteractionState.LISTENING.badgeText,
          isMuted = liveSnap.isMicMuted,
        )
        if (liveWsSession.isLiveWebSocketReady()) {
          liveWsSession.startMicrophoneStreaming(context)
        } else {
          onToggleTalkMic()
        }
      } else {
        permissionFeedbackMessage =
          "माइक परमिशन ज़रूरी है ताकि आन्या जार्विस आपकी आवाज़ सुन सके।"
      }
    }

  val handleToggleLiveMicrophone: () -> Unit = {
    // Barge-in: if Aanya JARVIS is speaking, interrupt playback immediately so user can command
    if (isEffectiveSpeaking) {
      liveWsSession.interruptPlayback()
      onStopFallbackTts()
    }

    if (liveSnap.isMicRecording || isListeningVoice) {
      if (liveSnap.isMicRecording) {
        liveWsSession.stopMicrophoneStreaming()
      }
      if (isListeningVoice) {
        onToggleTalkMic()
      }
      ZoyaForegroundService.stop(context)
    } else {
      val hasAudioPerm =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
          PackageManager.PERMISSION_GRANTED
      if (hasAudioPerm) {
        permissionFeedbackMessage = null
        if (liveSnap.isMicMuted) {
          liveWsSession.toggleMicMuted()
        }
        ZoyaForegroundService.startOrUpdate(
          context = context,
          mood = selectedMood,
          statusText = LiveVoiceInteractionState.LISTENING.badgeText,
          isMuted = false,
        )
        if (liveWsSession.isLiveWebSocketReady()) {
          liveWsSession.startMicrophoneStreaming(context)
        } else {
          onToggleTalkMic()
        }
      } else {
        try {
          audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } catch (_: Exception) {
          permissionFeedbackMessage = "माइक परमिशन शुरू नहीं हो सकी।"
        }
      }
    }
  }

  val handleCleanEndCall: () -> Unit = {
    liveWsSession.interruptPlayback()
    liveWsSession.disconnect()
    onStopFallbackTts()
    ZoyaForegroundService.stop(context)
    onNativeAudioActiveChanged(false)
    onEndCall()
  }

  BackHandler(onBack = handleCleanEndCall)

  LaunchedEffect(selectedMood) {
    liveWsSession.connectIfPossible(selectedMood)
  }

  LaunchedEffect(isSpeakingVoice) {
    liveWsSession.setExternalTtsSpeaking(isSpeakingVoice)
  }

  LaunchedEffect(isWhisperMode) {
    liveWsSession.setWhisperMode(isWhisperMode)
  }

  LaunchedEffect(
    isEffectiveListening,
    liveSnap.isMicMuted,
    isEffectiveSpeaking,
    selectedMood,
  ) {
    if (isEffectiveListening || isEffectiveSpeaking) {
      val badge =
        when {
          liveSnap.isMicMuted -> LiveVoiceInteractionState.MUTED.badgeText
          isEffectiveListening -> LiveVoiceInteractionState.LISTENING.badgeText
          isEffectiveSpeaking -> LiveVoiceInteractionState.SPEAKING.badgeText
          else -> LiveVoiceInteractionState.IDLE.badgeText
        }
      ZoyaForegroundService.startOrUpdate(
        context = context,
        mood = selectedMood,
        statusText = badge,
        isMuted = liveSnap.isMicMuted,
      )
    }
  }

  DisposableEffect(Unit) {
    ZoyaForegroundService.onNotificationToggleMute = {
      liveWsSession.toggleMicMuted()
    }
    ZoyaForegroundService.onNotificationEndCall = {
      liveWsSession.disconnect()
      onStopFallbackTts()
      onEndCall()
    }
    onDispose {
      ZoyaForegroundService.onNotificationToggleMute = null
      ZoyaForegroundService.onNotificationEndCall = null
      ZoyaForegroundService.stop(context)
      liveWsSession.shutdown()
      onNativeAudioActiveChanged(false)
    }
  }

  LaunchedEffect(Unit) {
    while (true) {
      delay(1000L)
      callSeconds++
    }
  }

  val formattedDuration =
    remember(callSeconds) {
      val mins = callSeconds / 60
      val secs = callSeconds % 60
      String.format("%02d:%02d", mins, secs)
    }

  val infiniteTransition = rememberInfiniteTransition(label = "jarvis_arc_reactor")
  val pulseFast by
    infiniteTransition.animateFloat(
      initialValue = 0.94f,
      targetValue = if (reducedMotion) 1.0f else 1.15f,
      animationSpec =
        infiniteRepeatable(
          animation =
            tween(
              durationMillis =
                when {
                  isEffectiveSpeaking || isEffectiveListening -> 580
                  isEffectiveProcessing -> 750
                  else -> 1500
                },
              easing = FastOutSlowInEasing,
            ),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "arc_pulse_fast",
    )

  val pulseOuter by
    infiniteTransition.animateFloat(
      initialValue = 0.92f,
      targetValue = if (reducedMotion) 1.0f else 1.24f,
      animationSpec =
        infiniteRepeatable(
          animation =
            tween(
              durationMillis =
                when {
                  isEffectiveSpeaking || isEffectiveListening -> 850
                  isEffectiveProcessing -> 1050
                  else -> 2000
                },
              easing = FastOutSlowInEasing,
            ),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "arc_pulse_outer",
    )

  val outerRingRotation by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = if (reducedMotion) 0f else 360f,
      animationSpec =
        infiniteRepeatable(
          animation =
            tween(
              durationMillis = if (isEffectiveProcessing || isEffectiveSpeaking) 6000 else 14000,
              easing = LinearEasing,
            ),
          repeatMode = RepeatMode.Restart,
        ),
      label = "hud_outer_rotation",
    )

  val innerRingRotation by
    infiniteTransition.animateFloat(
      initialValue = 360f,
      targetValue = if (reducedMotion) 360f else 0f,
      animationSpec =
        infiniteRepeatable(
          animation =
            tween(
              durationMillis = if (isEffectiveListening || isEffectiveSpeaking) 4800 else 10000,
              easing = LinearEasing,
            ),
          repeatMode = RepeatMode.Restart,
        ),
      label = "hud_inner_rotation",
    )

  // Futuristic JARVIS Arc-Reactor Color Palette
  val primaryAuraColor =
    when {
      liveSnap.isMicMuted -> Color(0xFFF59E0B) // Tactical Amber when muted
      isEffectiveListening -> Color(0xFF00F2FE) // Arc-Reactor Electric Cyan when listening
      isEffectiveProcessing -> Color(0xFF38BDF8) // Deep Hologram Sky-Blue when scanning/thinking
      isEffectiveSpeaking -> Color(0xFF06B6D4) // High-Energy Cyan-Teal when JARVIS speaks
      else -> Color(0xFF0EA5E9)
    }

  val secondaryAuraColor =
    when {
      liveSnap.isMicMuted -> Color(0xFFD97706)
      isEffectiveListening -> Color(0xFF10B981)
      isEffectiveProcessing -> Color(0xFF818CF8)
      isEffectiveSpeaking -> Color(0xFF3B82F6)
      else -> Color(0xFF2563EB)
    }

  val backgroundBrush =
    remember(primaryAuraColor) {
      Brush.verticalGradient(
        colors =
          listOf(
            Color(0xFF030712),
            Color(0xFF071326),
            Color(0xFF0A192F),
            Color(0xFF020617),
          )
      )
    }

  val displayedUserText =
    liveSnap.lastUserTranscript.ifBlank { lastUserSpokenText }

  val displayedGirlfriendText =
    when {
      liveSnap.liveAssistantTranscript.isNotBlank() ->
        GeminiLiveVoiceSession.cleanForGirlfriendSpeech(liveSnap.liveAssistantTranscript)
      lastGirlfriendReplyText.isNotBlank() ->
        GeminiLiveVoiceSession.cleanForGirlfriendSpeech(lastGirlfriendReplyText)
      else -> selectedMood.greetingLine
    }

  Scaffold(
    modifier =
      modifier
        .fillMaxSize()
        .background(backgroundBrush)
        .testTag("chat_live_voice_banner"),
    containerColor = Color.Transparent,
    contentWindowInsets = WindowInsets.systemBars,
  ) { innerPadding ->
    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(innerPadding)
          .padding(horizontal = AppSpacing.screenHorizontal, vertical = 16.dp)
          .testTag("chat_live_voice_fullscreen"),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      // -------------------------------------------------------------------
      // 1. Top JARVIS Holographic HUD Bar + Protocol Selector
      // -------------------------------------------------------------------
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            modifier =
              Modifier.clip(AppRadius.shapePill)
                .background(Color(0x2600F2FE))
                .border(AppBorder.hairline, Color(0xFF00F2FE), AppRadius.shapePill)
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("live_voice_status_pill"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Box(
              modifier =
                Modifier.size(7.dp)
                  .clip(CircleShape)
                  .background(
                    if (liveSnap.connectionState == LiveConnectionState.CONNECTED_READY) {
                      Color(0xFF10B981)
                    } else {
                      primaryAuraColor
                    }
                  )
            )
            Text(
              text = "JARVIS OS • $formattedDuration",
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
              fontWeight = FontWeight.Bold,
              color = Color(0xFFE0F2FE),
            )
          }

          Box(
            modifier =
              Modifier.size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .border(AppBorder.hairline, Color(0x6600F2FE), CircleShape)
                .clickable(role = Role.Button, onClick = handleCleanEndCall)
                .semantics {
                  role = Role.Button
                  contentDescription = "Close live voice assistant"
                }
                .testTag("live_voice_close_header_button"),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Outlined.Close,
              contentDescription = null,
              tint = Color(0xFFE0F2FE),
              modifier = Modifier.size(18.dp),
            )
          }
        }

        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          Text(
            text = "AANYA • JARVIS CORE",
            style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 1.4.sp),
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            modifier = Modifier.testTag("live_voice_persona_title"),
          )
          Text(
            text = selectedMood.subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF7DD3FC),
            textAlign = TextAlign.Center,
          )
        }

        // 4 Voice Mood Selector Pills + Whisper / Late-Night ASMR Mode Pill
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          LiveGirlfriendMood.entries.forEach { mood ->
            val isSelected = mood == selectedMood
            Box(
              modifier =
                Modifier.clip(AppRadius.shapePill)
                  .background(
                    if (isSelected) {
                      Brush.horizontalGradient(listOf(Color(0xFF0284C7), Color(0xFF2563EB)))
                    } else {
                      Brush.horizontalGradient(
                        listOf(Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.04f))
                      )
                    }
                  )
                  .border(
                    width = if (isSelected) AppBorder.focused else AppBorder.hairline,
                    color = if (isSelected) Color(0xFF00F2FE) else Color.White.copy(alpha = 0.16f),
                    shape = AppRadius.shapePill,
                  )
                  .clickable(role = Role.Button) {
                    liveWsSession.interruptPlayback()
                    onSelectMood(mood)
                  }
                  .padding(horizontal = 12.dp, vertical = 6.dp)
                  .testTag("live_voice_mood_${mood.name}"),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = mood.badge,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = Color.White,
              )
            }
          }

          // Whisper / Late-Night ASMR Mode Toggle Pill
          val activeWhisper = isWhisperMode || liveSnap.isWhisperMode
          Box(
            modifier =
              Modifier.clip(AppRadius.shapePill)
                .background(
                  if (activeWhisper) {
                    Brush.horizontalGradient(listOf(Color(0xFF7C3AED), Color(0xFFEC4899)))
                  } else {
                    Brush.horizontalGradient(
                      listOf(Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.04f))
                    )
                  }
                )
                .border(
                  width = if (activeWhisper) AppBorder.focused else AppBorder.hairline,
                  color = if (activeWhisper) Color(0xFFF472B6) else Color.White.copy(alpha = 0.16f),
                  shape = AppRadius.shapePill,
                )
                .clickable(role = Role.Button) {
                  liveWsSession.interruptPlayback()
                  val nextWhisper = !activeWhisper
                  liveWsSession.setWhisperMode(nextWhisper)
                  onToggleWhisperMode(nextWhisper)
                }
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("live_voice_whisper_toggle"),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = if (activeWhisper) "🤫 Whisper ON" else "🤫 Whisper ASMR",
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
              fontWeight = if (activeWhisper) FontWeight.Bold else FontWeight.Medium,
              color = Color.White,
            )
          }
        }

        if (!permissionFeedbackMessage.isNullOrBlank()) {
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x33F59E0B))
                .border(AppBorder.hairline, Color(0xFFFBBF24), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("live_voice_error_banner"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.WarningAmber,
              contentDescription = null,
              tint = Color(0xFFFBBF24),
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = permissionFeedbackMessage.orEmpty(),
              style = MaterialTheme.typography.labelSmall,
              color = Color.White,
              modifier = Modifier.weight(1f),
            )
          }
        }
      }

      // -------------------------------------------------------------------
      // 2. Centerpiece: Holographic Arc-Reactor HUD + Hindi Live Transcript
      // -------------------------------------------------------------------
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        val dynamicRmsBoost = (liveSnap.micRmsLevel * 0.25f).coerceIn(0f, 0.25f)

        Box(
          modifier =
            Modifier.size(220.dp)
              .clickable(role = Role.Button, onClick = handleToggleLiveMicrophone)
              .semantics {
                role = Role.Button
                contentDescription = "Tap JARVIS Arc Reactor to speak in Hindi"
              }
              .testTag("live_voice_orb"),
          contentAlignment = Alignment.Center,
        ) {
          // Ambient radial glow behind the Arc-Reactor
          Box(
            modifier =
              Modifier.size(210.dp)
                .scale(pulseOuter + dynamicRmsBoost)
                .clip(CircleShape)
                .background(
                  Brush.radialGradient(
                    colors =
                      listOf(
                        primaryAuraColor.copy(alpha = 0.32f),
                        secondaryAuraColor.copy(alpha = 0.12f),
                        Color.Transparent,
                      )
                  )
                )
          )

          // Rotating Cybernetic JARVIS Arc-Reactor Reticle Rings
          Canvas(modifier = Modifier.size(204.dp)) {
            val strokeOuter = 2.dp.toPx()
            val strokeInner = 3.dp.toPx()

            // Outer rotating segmented HUD arc ring
            rotate(degrees = outerRingRotation) {
              for (angle in 0 until 360 step 45) {
                drawArc(
                  color = primaryAuraColor.copy(alpha = 0.72f),
                  startAngle = angle.toFloat(),
                  sweepAngle = 24f,
                  useCenter = false,
                  style = Stroke(width = strokeOuter, cap = StrokeCap.Round),
                )
              }
            }

            // Counter-rotating inner tactical ring
            val inset = 18.dp.toPx()
            rotate(degrees = innerRingRotation) {
              for (angle in 0 until 360 step 60) {
                drawArc(
                  color = secondaryAuraColor.copy(alpha = 0.85f),
                  startAngle = angle.toFloat(),
                  sweepAngle = 36f,
                  useCenter = false,
                  topLeft = Offset(inset, inset),
                  size = Size(size.width - inset * 2, size.height - inset * 2),
                  style = Stroke(width = strokeInner, cap = StrokeCap.Round),
                )
              }
            }
          }

          // Middle glowing energy ring
          Box(
            modifier =
              Modifier.size(148.dp)
                .scale(pulseFast + (dynamicRmsBoost * 0.6f))
                .clip(CircleShape)
                .background(
                  Brush.linearGradient(
                    colors =
                      listOf(
                        primaryAuraColor.copy(alpha = 0.38f),
                        secondaryAuraColor.copy(alpha = 0.28f),
                      )
                  )
                )
                .border(1.5.dp, primaryAuraColor.copy(alpha = 0.85f), CircleShape)
          )

          // Inner Arc-Reactor Core with Live Audio Spectrum Bars
          Box(
            modifier =
              Modifier.size(110.dp)
                .clip(CircleShape)
                .background(
                  Brush.radialGradient(
                    colors =
                      listOf(
                        primaryAuraColor,
                        secondaryAuraColor,
                        Color(0xFF0A192F),
                      )
                  )
                )
                .border(2.dp, Color(0xFFE0F2FE), CircleShape),
            contentAlignment = Alignment.Center,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.5.dp),
            ) {
              val barMultipliers = listOf(0.45f, 0.78f, 1.0f, 0.68f, 0.92f, 0.58f, 0.42f)
              barMultipliers.forEach { mult ->
                val isActiveWave =
                  isEffectiveSpeaking || isEffectiveListening || isEffectiveProcessing
                val rmsFactor = 1f + (liveSnap.micRmsLevel * 0.85f)
                val animatedHeight =
                  if (isActiveWave) {
                    (14.dp.value + (30.dp.value * mult * pulseFast * rmsFactor)).dp
                  } else {
                    (10.dp.value + (6.dp.value * mult)).dp
                  }
                Box(
                  modifier =
                    Modifier.width(4.dp)
                      .height(animatedHeight.coerceIn(8.dp, 46.dp))
                      .clip(AppRadius.shapePill)
                      .background(Color.White)
                )
              }
            }
          }
        }

        // Dynamic Hindi Human-Friend + JARVIS Status Readout
        Text(
          text =
            when {
              liveSnap.isMicMuted -> "माइक म्यूट है — बात करने के लिए अनम्यूट करो यार"
              isEffectiveListening -> "सुन रही हूँ यार… आराम से बोलो"
              isEffectiveProcessing -> "आन्या सोच रही है…"
              isEffectiveSpeaking -> "आन्या बोल रही है… (बीच में बोलने के लिए टैप करो)"
              else -> "ऑर्ब या माइक दबाओ और अपने दोस्त की तरह बात करो"
            },
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Bold,
          color = Color(0xFFE0F2FE),
          textAlign = TextAlign.Center,
          modifier = Modifier.testTag("live_voice_state_text"),
        )

        // Holographic HUD Live Hindi Subtitle & Command Readout Card (Tap to replay voice)
        Column(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(18.dp))
              .background(Color(0x1400F2FE))
              .border(AppBorder.hairline, Color(0x6600F2FE), RoundedCornerShape(18.dp))
              .clickable(role = Role.Button) {
                onReplayLastVoice()
              }
              .padding(horizontal = 16.dp, vertical = 14.dp)
              .testTag("live_voice_transcript_card"),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (displayedUserText.isNotBlank()) {
            Text(
              text = "तुम: \"$displayedUserText\"",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
              color = Color(0xFF38BDF8),
              textAlign = TextAlign.Center,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }

          val safeSubtitleText =
            if (GeminiLiveVoiceSession.isErrorOrSystemDiagnosticText(displayedGirlfriendText)) {
              selectedMood.greetingLine
            } else {
              displayedGirlfriendText
            }

          Text(
            text = safeSubtitleText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("live_voice_girlfriend_subtitle"),
          )
        }

        // 1-Tap Interactive Voice Command & JARVIS Assistant Chips
        val quickCommandItems =
          remember {
            listOf(
              "⚡ System Status" to "Check JARVIS system status and tell me how you can help me today",
              "🎙️ AI Voice Info" to "Tumhari Dual-Engine AI voice kaise kaam karti hai?",
              "🧠 Smart Idea" to "Mujhe koi naya smart business aur AI startup idea batao",
              "💻 Coding Help" to "Coding aur app development mein clean logic kaise likhein?",
              "🔢 25 * 48 Hisaab" to "25 into 48 kitna hota hai?",
            )
          }

        Row(
          modifier =
            Modifier.fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          quickCommandItems.forEachIndexed { idx, (label, promptText) ->
            Box(
              modifier =
                Modifier.clip(AppRadius.shapePill)
                  .background(Color(0x1F00F2FE))
                  .border(AppBorder.hairline, Color(0x6600F2FE), AppRadius.shapePill)
                  .clickable(role = Role.Button) {
                    liveWsSession.interruptPlayback()
                    onStopFallbackTts()
                    if (!liveWsSession.isLiveWebSocketReady() || !liveWsSession.sendClientTextTurn(promptText)) {
                      onSendLivePrompt(promptText)
                    }
                  }
                  .padding(horizontal = 12.dp, vertical = 7.dp)
                  .testTag("live_voice_quick_chip_$idx"),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFE0F2FE),
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(4.dp))

      // -------------------------------------------------------------------
      // 3. Minimal 3-Button JARVIS Call Dock (Mute, Center Arc-Mic, Disconnect)
      // -------------------------------------------------------------------
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
      ) {
        // 1. Mute / Unmute Microphone
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Box(
            modifier =
              Modifier.size(50.dp)
                .clip(CircleShape)
                .background(
                  if (liveSnap.isMicMuted) Color(0x33F59E0B) else Color.White.copy(alpha = 0.08f)
                )
                .border(
                  AppBorder.hairline,
                  if (liveSnap.isMicMuted) Color(0xFFF59E0B) else Color(0x6600F2FE),
                  CircleShape,
                )
                .clickable(role = Role.Button) {
                  liveWsSession.toggleMicMuted()
                  if (isListeningVoice) {
                    onToggleTalkMic()
                  }
                }
                .semantics {
                  role = Role.Button
                  contentDescription =
                    if (liveSnap.isMicMuted) "Unmute microphone" else "Mute microphone"
                }
                .testTag("chat_live_voice_mute_button"),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = if (liveSnap.isMicMuted) Icons.Outlined.MicOff else Icons.Outlined.Mic,
              contentDescription = null,
              tint = if (liveSnap.isMicMuted) Color(0xFFFBBF24) else Color(0xFFE0F2FE),
              modifier = Modifier.size(22.dp),
            )
          }
          Text(
            text = if (liveSnap.isMicMuted) "Unmute" else "Mute",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFBAE6FD),
          )
        }

        // 2. Central Arc-Reactor Mic / Voice Command Button
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Box(
            modifier =
              Modifier.size(68.dp)
                .clip(CircleShape)
                .background(
                  Brush.linearGradient(
                    if (isEffectiveListening) {
                      listOf(Color(0xFF00F2FE), Color(0xFF10B981))
                    } else {
                      listOf(Color(0xFF0284C7), Color(0xFF2563EB))
                    }
                  )
                )
                .border(2.dp, Color(0xFFE0F2FE), CircleShape)
                .clickable(role = Role.Button, onClick = handleToggleLiveMicrophone)
                .semantics {
                  role = Role.Button
                  contentDescription =
                    if (isEffectiveListening) "Stop listening" else "Give voice command"
                }
                .testTag("chat_live_voice_talk_button"),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector =
                if (isEffectiveListening) Icons.Outlined.GraphicEq else Icons.Outlined.Mic,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(30.dp),
            )
          }
          Text(
            text = if (isEffectiveListening) "Listening…" else "Voice Command",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
          )
        }

        // 3. Red Disconnect Button
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Box(
            modifier =
              Modifier.size(50.dp)
                .clip(CircleShape)
                .background(Color(0xFFEF4444))
                .border(1.5.dp, Color(0xFFFCA5A5), CircleShape)
                .clickable(role = Role.Button, onClick = handleCleanEndCall)
                .semantics {
                  role = Role.Button
                  contentDescription = "End JARVIS voice session"
                }
                .testTag("chat_live_voice_stop_button"),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Outlined.CallEnd,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(22.dp),
            )
          }
          Text(
            text = "End Call",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFCA5A5),
          )
        }
      }
    }
  }
}
