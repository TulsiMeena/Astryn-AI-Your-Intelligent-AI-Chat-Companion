package com.example.ui.screens.chat

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Expand
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.api.GeminiLiveVoiceSession
import com.example.data.api.LiveGirlfriendMood
import com.example.data.model.AttachmentKind
import com.example.data.model.ChatAttachment
import com.example.data.model.ChatMessage
import com.example.data.model.MessageRole
import com.example.data.model.MessageStatus
import com.example.data.model.WebCitation
import com.example.data.repository.AiPersona
import com.example.ui.components.AmbientGlassBackground
import com.example.ui.components.AstrynLogoEmblem
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassButtonVariant
import com.example.ui.components.GlassIconButton
import com.example.ui.components.GlassSurface
import com.example.ui.components.GlassTextField
import com.example.ui.components.ActiveQuoteReplyBanner
import com.example.ui.components.AiInstantSummaryOrPointsView
import com.example.ui.components.AiReasoningChainBox
import com.example.ui.components.AiResponseModeTabs
import com.example.ui.components.AiResponseViewMode
import com.example.ui.components.CodeRunnerPreviewDialog
import com.example.ui.components.InteractiveFlowchartCard
import com.example.ui.components.InteractiveQuoteSelectorPanel
import com.example.ui.components.MarkdownMessageContent
import com.example.ui.components.SafeMarkdownParser
import com.example.ui.components.SmartContextActionCardsRow
import com.example.ui.screens.quiz.InteractiveQuizDialog
import com.example.ui.screens.settings.AiProviderApiKeyPanel
import com.example.ui.theme.AppBorder
import com.example.ui.theme.AppRadius
import com.example.ui.theme.AppSpacing
import com.example.ui.theme.AppThemeTokens
import com.example.ui.theme.LocalReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Controller managing Android TextToSpeech read-aloud playback for AI responses,
 * including automatic Hindi/Devanagari locale selection and clean lifecycle teardown.
 */
class AstrynSpeechController(
  context: Context,
  private val onSpeakingMessageChanged: (String?) -> Unit,
) {
  private val appContext = context.applicationContext
  private val mainHandler = Handler(Looper.getMainLooper())
  private var tts: TextToSpeech? = null
  private var isEngineReady = false
  private var pendingUtterance: Pair<String, String>? = null
  private var pendingGirlfriendUtterance: Triple<String, String, LiveGirlfriendMood>? = null
  private var utteranceCompletionCallback: (() -> Unit)? = null
  private var lastSpokenTextByMessage: Pair<String, String>? = null
  var activeMessageId: String? = null
    private set
  var speechRate: Float = 1.0f
    private set
  var isWhisperMode: Boolean = false

  companion object {
    val SPEED_STEPS = listOf(1.0f, 1.25f, 1.5f, 0.85f)

    fun formatRate(rate: Float): String {
      return when {
        rate == 1.0f -> "1x"
        rate == 1.25f -> "1.25x"
        rate == 1.5f -> "1.5x"
        rate == 0.85f -> "0.85x"
        else -> "${rate}x"
      }
    }
  }

  init {
    initTtsEngine(preferGoogleEngine = true)
  }

  private fun initTtsEngine(preferGoogleEngine: Boolean) {
    try {
      val listener =
        TextToSpeech.OnInitListener { status ->
          if (status == TextToSpeech.SUCCESS) {
            isEngineReady = true
            try {
              val audioAttrs =
                AudioAttributes.Builder()
                  .setUsage(AudioAttributes.USAGE_MEDIA)
                  .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                  .build()
              tts?.setAudioAttributes(audioAttrs)
              tts?.let { engine ->
                applyZoyaFemaleVoiceAndLocale(
                  engine = engine,
                  targetPitch = LiveGirlfriendMood.NATKHAT.voicePitch,
                  targetRate = LiveGirlfriendMood.NATKHAT.voiceRate,
                )
              }
            } catch (_: Exception) {}
            tts?.setOnUtteranceProgressListener(
              object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                  mainHandler.post {
                    activeMessageId = utteranceId
                    onSpeakingMessageChanged(utteranceId)
                  }
                }

                override fun onDone(utteranceId: String?) {
                  mainHandler.postDelayed(
                    {
                      if (activeMessageId == utteranceId) {
                        activeMessageId = null
                        onSpeakingMessageChanged(null)
                        val cb = utteranceCompletionCallback
                        utteranceCompletionCallback = null
                        cb?.invoke()
                      }
                    },
                    320L,
                  )
                }

                private fun handleErrorWithFallback(utteranceId: String?) {
                  mainHandler.post {
                    val lastPair = lastSpokenTextByMessage
                    if (
                      utteranceId != null &&
                        !utteranceId.endsWith("_retry") &&
                        lastPair != null &&
                        lastPair.first == utteranceId
                    ) {
                      try {
                        val engine = tts
                        if (engine != null) {
                          val retryId = "${utteranceId}_retry"
                          activeMessageId = retryId
                          val indianEng = Locale.forLanguageTag("en-IN")
                          val langRes = engine.setLanguage(indianEng)
                          if (
                            langRes == TextToSpeech.LANG_MISSING_DATA ||
                              langRes == TextToSpeech.LANG_NOT_SUPPORTED
                          ) {
                            engine.language = Locale.US
                          }
                          engine.setPitch(1.08f)
                          engine.setSpeechRate(1.02f)
                          val fallbackText =
                            GeminiLiveVoiceSession.toHumanFriendSpokenPhonetics(lastPair.second)
                              .take(3900)
                          val params =
                            Bundle().apply {
                              putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                              putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
                            }
                          engine.speak(fallbackText, TextToSpeech.QUEUE_FLUSH, params, retryId)
                          return@post
                        }
                      } catch (_: Exception) {}
                    }
                    if (activeMessageId == utteranceId) {
                      activeMessageId = null
                      onSpeakingMessageChanged(null)
                      utteranceCompletionCallback = null
                    }
                  }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                  handleErrorWithFallback(utteranceId)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                  handleErrorWithFallback(utteranceId)
                }
              }
            )
            pendingGirlfriendUtterance?.let { (uttId, text, mood) ->
              pendingGirlfriendUtterance = null
              speakGirlfriendInternal(uttId, text, mood)
            }
            pendingUtterance?.let { (msgId, speechText) ->
              pendingUtterance = null
              speakInternal(msgId, speechText)
            }
          } else if (preferGoogleEngine) {
            initTtsEngine(preferGoogleEngine = false)
          }
        }

      tts =
        if (preferGoogleEngine) {
          TextToSpeech(appContext, listener, "com.google.android.tts")
        } else {
          TextToSpeech(appContext, listener)
        }
    } catch (_: Exception) {
      if (preferGoogleEngine) {
        initTtsEngine(preferGoogleEngine = false)
      } else {
        isEngineReady = false
      }
    }
  }

  /**
   * ZoyaVoiceSpeaker Female Voice Filter & Locale Lock:
   * 1. Attempts to lock onto `hi-IN` (Hindi - India) Female Voice (`hi-in-x-hie`, `hi-in-x-hia`, `hi-in-x-hic`, `female`, `f0`, `woman`).
   * 2. Falls back to `en-IN` (English - India) Female Voice (`en-in-x-end`, `en-in-x-ena`, `female`, `f0`, `woman`) if Hindi voice data is not installed.
   * 3. Applies `setPitch(1.08f)` and `setSpeechRate(1.02f)` (or mood/whisper tuning) for a warm, youthful female voice.
   * Returns `true` if a native Hindi (`hi-IN`) voice/locale is active.
   */
  private fun applyZoyaFemaleVoiceAndLocale(
    engine: TextToSpeech,
    targetPitch: Float,
    targetRate: Float,
  ): Boolean {
    var isHindiLocaleActive = false
    val hindiLocale = Locale.forLanguageTag("hi-IN")
    val indianEngLocale = Locale.forLanguageTag("en-IN")

    try {
      val hiRes = engine.setLanguage(hindiLocale)
      if (hiRes != TextToSpeech.LANG_MISSING_DATA && hiRes != TextToSpeech.LANG_NOT_SUPPORTED) {
        isHindiLocaleActive = true
      } else {
        val enInRes = engine.setLanguage(indianEngLocale)
        if (enInRes == TextToSpeech.LANG_MISSING_DATA || enInRes == TextToSpeech.LANG_NOT_SUPPORTED) {
          val usRes = engine.setLanguage(Locale.US)
          if (usRes == TextToSpeech.LANG_MISSING_DATA || usRes == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.language = Locale.getDefault()
          }
        }
      }
    } catch (_: Exception) {}

    try {
      val availableVoices = engine.voices
      if (!availableVoices.isNullOrEmpty()) {
        val installedVoices =
          availableVoices
            .filter { voice ->
              val features = voice.features ?: emptySet()
              !features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
            }
            .sortedWith(
              compareByDescending<android.speech.tts.Voice> { it.quality }
                .thenBy { it.isNetworkConnectionRequired }
            )

        val hindiFemaleMarkers =
          listOf("hi-in-x-hie", "hi-in-x-hia", "hi-in-x-hic", "female", "f0", "woman", "sfg")
        val englishFemaleMarkers =
          listOf("en-in-x-end", "en-in-x-ena", "en-us-x-sfg", "en-us-x-tpf", "female", "f0", "woman", "sfg")
        val maleExclusions = listOf("hi-in-x-hid", "hi-in-x-hib", "en-in-x-enc", "en-in-x-ene", "#male", "_male", "-male")

        val preferredHindiFemale =
          installedVoices.firstOrNull { v ->
            val lang = v.locale?.language?.lowercase(Locale.ROOT).orEmpty()
            val vName = v.name.lowercase(Locale.ROOT)
            lang == "hi" &&
              hindiFemaleMarkers.any { vName.contains(it) } &&
              maleExclusions.none { vName.contains(it) }
          }
            ?: installedVoices.firstOrNull { v ->
              val lang = v.locale?.language?.lowercase(Locale.ROOT).orEmpty()
              val vName = v.name.lowercase(Locale.ROOT)
              lang == "hi" && maleExclusions.none { vName.contains(it) }
            }

        val preferredIndianEngFemale =
          installedVoices.firstOrNull { v ->
            val lang = v.locale?.language?.lowercase(Locale.ROOT).orEmpty()
            val country = v.locale?.country?.lowercase(Locale.ROOT).orEmpty()
            val vName = v.name.lowercase(Locale.ROOT)
            lang == "en" &&
              country == "in" &&
              englishFemaleMarkers.any { vName.contains(it) } &&
              maleExclusions.none { vName.contains(it) }
          }
            ?: installedVoices.firstOrNull { v ->
              val lang = v.locale?.language?.lowercase(Locale.ROOT).orEmpty()
              val vName = v.name.lowercase(Locale.ROOT)
              lang == "en" &&
                englishFemaleMarkers.any { vName.contains(it) } &&
                maleExclusions.none { vName.contains(it) }
            }

        when {
          preferredHindiFemale != null -> {
            engine.voice = preferredHindiFemale
            isHindiLocaleActive = true
          }
          preferredIndianEngFemale != null -> {
            engine.voice = preferredIndianEngFemale
          }
        }
      }
    } catch (_: Exception) {}

    try {
      engine.setPitch(targetPitch)
      engine.setSpeechRate(targetRate)
    } catch (_: Exception) {}

    return isHindiLocaleActive
  }

  private fun ensureMediaStreamAudible() {
    try {
      val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
      val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
      val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
      if (currentVol <= 0 && maxVol > 0) {
        val targetVol = (maxVol * 0.70f).toInt().coerceAtLeast(1)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
      }
    } catch (_: Exception) {}
  }

  fun cycleSpeechRate(): Float {
    val currentIdx = SPEED_STEPS.indexOfFirst { kotlin.math.abs(it - speechRate) < 0.02f }
    val nextRate =
      if (currentIdx >= 0) SPEED_STEPS[(currentIdx + 1) % SPEED_STEPS.size] else 1.25f
    speechRate = nextRate
    try {
      tts?.setSpeechRate(nextRate)
    } catch (_: Exception) {}

    val currentActiveId = activeMessageId
    val lastSpoken = lastSpokenTextByMessage
    if (currentActiveId != null && lastSpoken != null && lastSpoken.first == currentActiveId) {
      if (isEngineReady) {
        speakInternal(currentActiveId, lastSpoken.second)
      }
    }
    return nextRate
  }

  fun toggleSpeak(message: ChatMessage): Boolean {
    if (activeMessageId == message.id) {
      stop()
      return false
    }
    val speechText = message.formatForSpeech()
    if (speechText.isBlank()) return false

    utteranceCompletionCallback = null
    activeMessageId = message.id
    lastSpokenTextByMessage = message.id to speechText
    onSpeakingMessageChanged(message.id)

    if (isEngineReady) {
      speakInternal(message.id, speechText)
    } else {
      pendingUtterance = message.id to speechText
    }
    return true
  }

  /**
   * Speaks [rawText] with ZoyaVoiceSpeaker's expressive female `hi-IN` / `en-IN` voice lock
   * and youthful pitch (`1.08f`) / speech rate (`1.02f`) tuning matching [mood].
   */
  fun speakAsGirlfriend(
    utteranceId: String,
    rawText: String,
    mood: LiveGirlfriendMood,
    onFinished: (() -> Unit)? = null,
  ): Boolean {
    val cleanSpeech = GeminiLiveVoiceSession.cleanForGirlfriendSpeech(rawText)
    if (cleanSpeech.isBlank()) return false

    activeMessageId = utteranceId
    lastSpokenTextByMessage = utteranceId to cleanSpeech
    utteranceCompletionCallback = onFinished
    onSpeakingMessageChanged(utteranceId)

    if (isEngineReady) {
      speakGirlfriendInternal(utteranceId, cleanSpeech, mood)
    } else {
      pendingGirlfriendUtterance = Triple(utteranceId, cleanSpeech, mood)
    }
    return true
  }

  private fun speakGirlfriendInternal(
    utteranceId: String,
    speechText: String,
    mood: LiveGirlfriendMood,
  ) {
    try {
      val engine = tts ?: return
      ensureMediaStreamAudible()

      val targetPitch =
        if (isWhisperMode) LiveGirlfriendMood.WHISPER_VOICE_PITCH else mood.voicePitch
      val targetRate =
        if (isWhisperMode) LiveGirlfriendMood.WHISPER_VOICE_RATE else mood.voiceRate
      val targetVolume = if (isWhisperMode) 0.68f else 1.0f

      val isHindiActive =
        applyZoyaFemaleVoiceAndLocale(
          engine = engine,
          targetPitch = targetPitch,
          targetRate = targetRate,
        )

      val speakableText =
        if (isHindiActive) {
          speechText
            .replace("...", ", ")
            .replace("…", ", ")
            .replace("—", ", ")
            .replace(" - ", ", ")
            .take(3900)
        } else {
          GeminiLiveVoiceSession.toHumanFriendSpokenPhonetics(speechText)
            .ifBlank { GeminiLiveVoiceSession.transliterateDevanagariToHinglish(speechText) }
            .take(3900)
        }

      val params =
        Bundle().apply {
          putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, targetVolume)
          putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
        }

      val speakRes =
        engine.speak(speakableText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
      if (speakRes == TextToSpeech.ERROR) {
        val fallbackText =
          GeminiLiveVoiceSession.toHumanFriendSpokenPhonetics(speechText)
            .ifBlank { speechText }
            .take(3900)
        engine.language = Locale.forLanguageTag("en-IN")
        engine.setPitch(targetPitch)
        engine.setSpeechRate(targetRate)
        engine.speak(fallbackText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
      }
    } catch (_: Exception) {}
  }

  private fun speakInternal(messageId: String, speechText: String) {
    try {
      val engine = tts ?: return
      ensureMediaStreamAudible()
      val effectiveRate = if (speechRate == 1.0f) 1.02f else speechRate
      val isHindiActive =
        applyZoyaFemaleVoiceAndLocale(
          engine = engine,
          targetPitch = 1.08f,
          targetRate = effectiveRate,
        )
      val cleaned = GeminiLiveVoiceSession.cleanForGirlfriendSpeech(speechText)
      val speakable =
        if (isHindiActive) {
          cleaned.ifBlank { speechText }
        } else {
          GeminiLiveVoiceSession.toHumanFriendSpokenPhonetics(cleaned)
            .ifBlank { speechText }
        }
      val params =
        Bundle().apply {
          putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
          putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
        }
      engine.speak(speakable.take(3900), TextToSpeech.QUEUE_FLUSH, params, messageId)
    } catch (_: Exception) {}
  }

  fun stop() {
    pendingUtterance = null
    pendingGirlfriendUtterance = null
    utteranceCompletionCallback = null
    activeMessageId = null
    onSpeakingMessageChanged(null)
    try {
      tts?.stop()
    } catch (_: Exception) {}
  }

  fun shutdown() {
    stop()
    try {
      tts?.shutdown()
    } catch (_: Exception) {}
    tts = null
  }
}

/**
 * Copies plain text to the Android system ClipboardManager and returns true if successful.
 */
fun copyTextToSystemClipboard(
  context: Context,
  label: String,
  text: String,
): Boolean {
  return try {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    if (clipboard != null) {
      clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
      true
    } else {
      false
    }
  } catch (_: Exception) {
    false
  }
}

/**
 * Launches the native Android Share sheet (`Intent.ACTION_SEND` chooser) to share formatted
 * message content with apps such as WhatsApp, Telegram, Gmail, Notes, or Messages.
 */
fun shareTextViaAndroidSheet(
  context: Context,
  title: String,
  text: String,
): Boolean {
  if (text.isBlank()) return false
  return try {
    val sendIntent =
      Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
      }
    val chooserIntent =
      Intent.createChooser(sendIntent, title).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    context.startActivity(chooserIntent)
    true
  } catch (_: Exception) {
    false
  }
}

/**
 * Locked Astryn AI Chat Conversation Screen featuring:
 * - Deep navy atmospheric canvas with restrained cyan/blue and royal violet ambient lighting
 * - Frosted glass top header with Back button, official Astryn AI logo emblem, conversation title,
 *   glowing model subtitle indicator, AI Persona selector, In-Conversation Search, Live Voice mode,
 *   Saved Bookmarks, and slide-out Menu button
 * - Distinct right-aligned User gradient bubbles and left-aligned Astryn AI glass panels
 * - Multimodal Image, Camera Scan & PDF attachments, Prompt Templates, live Google Search grounding citations,
 *   and compact AI response actions (Copy, Share, Like, Save, Regenerate, Speak, Speed, Modify, Compare, Translate, Export)
 * - Fixed bottom glass composer dock with full keyboard inset handling and high-contrast text input
 * - Polished Empty, Streaming, Offline, Interrupted, and Retryable Error states
 */
@Composable
fun ChatScreen(
  conversationId: String,
  initialPrompt: String? = null,
  onOpenMenu: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: ChatViewModel = viewModel(),
  onOpenApiConfig: (() -> Unit)? = null,
  startWithLiveVoice: Boolean = false,
  startWithVoiceMic: Boolean = false,
) {
  val glass = AppThemeTokens.glass
  val reducedMotion = LocalReducedMotion.current
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val uiState by viewModel.uiState.collectAsState()
  val listState = rememberLazyListState()

  var activeActionMessageId by rememberSaveable { mutableStateOf<String?>(null) }
  var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
  var editDraftText by rememberSaveable { mutableStateOf("") }
  var showApiKeySetupDialog by rememberSaveable { mutableStateOf(false) }
  var showBookmarksDialog by rememberSaveable { mutableStateOf(false) }
  var showPersonaDialog by rememberSaveable { mutableStateOf(false) }
  var showTemplatesDialog by rememberSaveable { mutableStateOf(false) }
  var showAddTemplateForm by rememberSaveable { mutableStateOf(false) }
  var newTemplateTitle by rememberSaveable { mutableStateOf("") }
  var newTemplatePrompt by rememberSaveable { mutableStateOf("") }

  // In-App Code Runner & Live Web Preview State
  var runningCodeSnippet by remember { mutableStateOf<Pair<String, String>?>(null) }

  // Interactive Exam Quiz & Flashcard Test State
  var showQuizDialog by rememberSaveable { mutableStateOf(false) }
  var quizInitialTopic by rememberSaveable { mutableStateOf("") }

  // Quote & Ask (Word/Line Highlight Reply) State
  var activeQuotedText by rememberSaveable { mutableStateOf("") }

  // In-Conversation Search State
  var showInConvSearch by rememberSaveable { mutableStateOf(false) }
  var inConvSearchQuery by rememberSaveable { mutableStateOf("") }
  var activeSearchMatchOrdinal by rememberSaveable { mutableIntStateOf(0) }

  // Voice & Live Girlfriend Voice-to-Voice Call Mode State
  var isLiveVoiceMode by rememberSaveable { mutableStateOf(startWithLiveVoice) }
  var liveGirlfriendMood by remember { mutableStateOf(LiveGirlfriendMood.NATKHAT) }
  var liveWhisperMode by rememberSaveable { mutableStateOf(false) }
  var liveAutoHandsFree by rememberSaveable { mutableStateOf(true) }
  var liveSessionOpenedAt by rememberSaveable { mutableStateOf(0L) }
  var lastLiveUserSpokenText by rememberSaveable { mutableStateOf("") }
  var lastAutoSpokenMessageId by rememberSaveable { mutableStateOf<String?>(null) }
  var speakingMessageId by remember { mutableStateOf<String?>(null) }
  var isNativeLiveAudioPlaying by remember { mutableStateOf(false) }
  var speechRate by remember { mutableStateOf(1.0f) }
  var isListeningVoice by remember { mutableStateOf(false) }
  var voicePrefixDraft by remember { mutableStateOf("") }
  var activeSpeechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

  val speechController =
    remember(context) {
      AstrynSpeechController(context) { currentSpeakingId ->
        speakingMessageId = currentSpeakingId
      }
    }

  DisposableEffect(Unit) {
    onDispose {
      speechController.shutdown()
      try {
        activeSpeechRecognizer?.destroy()
      } catch (_: Exception) {}
      activeSpeechRecognizer = null
    }
  }

  val handleOpenApiConfig: () -> Unit = {
    if (onOpenApiConfig != null) {
      onOpenApiConfig()
    } else {
      showApiKeySetupDialog = true
    }
  }

  val attachmentPickerLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
      uris.forEach { uri ->
        viewModel.addAttachmentUri(context, uri)
      }
    }

  val handlePickAttachments: () -> Unit = {
    try {
      attachmentPickerLauncher.launch(
        arrayOf("image/jpeg", "image/png", "image/webp", "application/pdf")
      )
    } catch (_: Exception) {
      viewModel.showActionFeedback("Unable to open file picker on this device")
    }
  }

  // Camera Direct Photo Capture Launcher (OCR / Homework / Bill Scanner)
  val cameraCaptureLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
      if (bitmap != null) {
        viewModel.addCameraBitmapAttachment(bitmap, context.cacheDir)
      } else {
        viewModel.showActionFeedback("Camera capture cancelled")
      }
    }

  val cameraPermissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
      if (granted) {
        try {
          cameraCaptureLauncher.launch(null)
        } catch (_: Exception) {
          viewModel.showActionFeedback("Unable to launch camera on this device")
        }
      } else {
        viewModel.showActionFeedback("Camera permission is required to scan photos")
      }
    }

  val handleCaptureCameraPhoto: () -> Unit = {
    val hasCamPerm =
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    if (hasCamPerm) {
      try {
        cameraCaptureLauncher.launch(null)
      } catch (_: Exception) {
        viewModel.showActionFeedback("Unable to open camera on this device")
      }
    } else {
      try {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
      } catch (_: Exception) {
        viewModel.showActionFeedback("Unable to request camera permission")
      }
    }
  }

  // Fallback Activity-based Speech Recognizer when inline SpeechRecognizer service is unavailable
  val voiceIntentLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
      isListeningVoice = false
      if (result.resultCode == Activity.RESULT_OK) {
        val matches =
          result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        val bestMatch = matches?.firstOrNull { it.isNotBlank() }?.trim()
        if (!bestMatch.isNullOrBlank()) {
          if (isLiveVoiceMode) {
            lastLiveUserSpokenText = bestMatch
            viewModel.sendMessage(bestMatch, liveVoiceMood = liveGirlfriendMood)
          } else {
            viewModel.appendVoiceTranscript(bestMatch, voicePrefixDraft)
            viewModel.showActionFeedback("Voice input added")
          }
        }
      }
    }

  val launchVoiceRecognitionSession: () -> Unit = {
    voicePrefixDraft = uiState.inputText
    val recognizeIntent =
      Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
          RecognizerIntent.EXTRA_LANGUAGE_MODEL,
          RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
        )
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        putExtra(
          RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
          1200L,
        )
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Aanya sun rahi hai… bolo yaar!")
      }

    if (SpeechRecognizer.isRecognitionAvailable(context)) {
      try {
        activeSpeechRecognizer?.destroy()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        activeSpeechRecognizer = recognizer
        var latestPartialSpeech = ""
        var hasDispatchedSpeechTurn = false
        recognizer.setRecognitionListener(
          object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
              isListeningVoice = true
            }

            override fun onBeginningOfSpeech() {
              isListeningVoice = true
              speechController.stop()
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
              isListeningVoice = false
            }

            override fun onError(error: Int) {
              isListeningVoice = false
              val fallbackSpoken = latestPartialSpeech.trim()
              if (!hasDispatchedSpeechTurn && fallbackSpoken.isNotBlank()) {
                hasDispatchedSpeechTurn = true
                if (isLiveVoiceMode) {
                  lastLiveUserSpokenText = fallbackSpoken
                  viewModel.sendMessage(fallbackSpoken, liveVoiceMood = liveGirlfriendMood)
                } else {
                  viewModel.appendVoiceTranscript(fallbackSpoken, voicePrefixDraft)
                }
              }
              try {
                recognizer.destroy()
              } catch (_: Exception) {}
              if (activeSpeechRecognizer === recognizer) {
                activeSpeechRecognizer = null
              }
            }

            override fun onResults(results: Bundle?) {
              isListeningVoice = false
              val matches =
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
              val best =
                matches?.firstOrNull { it.isNotBlank() }?.trim()
                  ?: latestPartialSpeech.trim().takeIf { it.isNotBlank() }
              if (!hasDispatchedSpeechTurn && !best.isNullOrBlank()) {
                hasDispatchedSpeechTurn = true
                if (isLiveVoiceMode) {
                  lastLiveUserSpokenText = best
                  viewModel.sendMessage(best, liveVoiceMood = liveGirlfriendMood)
                } else {
                  viewModel.appendVoiceTranscript(best, voicePrefixDraft)
                  viewModel.showActionFeedback("Voice input captured")
                }
              }
              try {
                recognizer.destroy()
              } catch (_: Exception) {}
              if (activeSpeechRecognizer === recognizer) {
                activeSpeechRecognizer = null
              }
            }

            override fun onPartialResults(partialResults: Bundle?) {
              val partial =
                partialResults
                  ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                  ?.firstOrNull { it.isNotBlank() }
                  ?.trim()
              if (!partial.isNullOrBlank()) {
                latestPartialSpeech = partial
                if (isLiveVoiceMode) {
                  lastLiveUserSpokenText = partial
                } else {
                  viewModel.appendVoiceTranscript(partial, voicePrefixDraft)
                }
              }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
          }
        )
        isListeningVoice = true
        recognizer.startListening(recognizeIntent)
      } catch (_: Exception) {
        try {
          isListeningVoice = true
          voiceIntentLauncher.launch(recognizeIntent)
        } catch (_: Exception) {
          isListeningVoice = false
          viewModel.showActionFeedback("Voice input is not supported on this device")
        }
      }
    } else {
      try {
        isListeningVoice = true
        voiceIntentLauncher.launch(recognizeIntent)
      } catch (_: Exception) {
        isListeningVoice = false
        viewModel.showActionFeedback("Voice input is not available on this device")
      }
    }
  }

  val audioPermissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
      if (granted) {
        launchVoiceRecognitionSession()
      } else {
        isListeningVoice = false
        viewModel.showActionFeedback("Microphone permission is required for voice input")
      }
    }

  val handleToggleVoiceInput: () -> Unit = {
    if (isListeningVoice) {
      isListeningVoice = false
      try {
        activeSpeechRecognizer?.stopListening()
        activeSpeechRecognizer?.destroy()
      } catch (_: Exception) {}
      activeSpeechRecognizer = null
    } else {
      speechController.stop()
      val hasAudioPerm =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
          PackageManager.PERMISSION_GRANTED
      if (hasAudioPerm) {
        launchVoiceRecognitionSession()
      } else {
        try {
          audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } catch (_: Exception) {
          launchVoiceRecognitionSession()
        }
      }
    }
  }

  // Auto-read aloud completed AI responses in JARVIS Hindi voice and auto-listen next turn
  LaunchedEffect(uiState.isGenerating, uiState.messages.size, isLiveVoiceMode, isNativeLiveAudioPlaying) {
    if (isLiveVoiceMode && !uiState.isGenerating && !isNativeLiveAudioPlaying) {
      val latestAssistant =
        uiState.messages.lastOrNull {
          it.role == MessageRole.MODEL &&
            it.status == MessageStatus.SUCCESS &&
            it.content.isNotBlank()
        }
      if (latestAssistant != null && latestAssistant.id != lastAutoSpokenMessageId) {
        lastAutoSpokenMessageId = latestAssistant.id
        // Ensure SpeechRecognizer is stopped while TTS speaks so Android does not mute STREAM_MUSIC
        if (isListeningVoice) {
          isListeningVoice = false
          try {
            activeSpeechRecognizer?.stopListening()
            activeSpeechRecognizer?.destroy()
          } catch (_: Exception) {}
          activeSpeechRecognizer = null
        }
        speechController.speakAsGirlfriend(
          utteranceId = latestAssistant.id,
          rawText = latestAssistant.content,
          mood = liveGirlfriendMood,
          onFinished = {
            if (isLiveVoiceMode && liveAutoHandsFree && !isListeningVoice && !uiState.isGenerating) {
              val hasAudioPerm =
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                  PackageManager.PERMISSION_GRANTED
              if (hasAudioPerm) {
                launchVoiceRecognitionSession()
              }
            }
          },
        )
      }
    }
  }

  // Greet the user with "Welcome boss! How can I help you?" when Live Voice Mode opens
  LaunchedEffect(isLiveVoiceMode) {
    if (isLiveVoiceMode) {
      liveSessionOpenedAt = System.currentTimeMillis()
      lastLiveUserSpokenText = ""
      if (isListeningVoice) {
        isListeningVoice = false
        try {
          activeSpeechRecognizer?.stopListening()
          activeSpeechRecognizer?.destroy()
        } catch (_: Exception) {}
        activeSpeechRecognizer = null
      }
      lastAutoSpokenMessageId =
        uiState.messages
          .lastOrNull { it.role == MessageRole.MODEL && it.status == MessageStatus.SUCCESS }
          ?.id
      speechController.speakAsGirlfriend(
        utteranceId = "live_jarvis_greeting_${System.currentTimeMillis()}",
        rawText = LiveGirlfriendMood.DEFAULT_OPENING_GREETING,
        mood = liveGirlfriendMood,
      )
    }
  }

  // Initialize conversation once per conversation ID
  LaunchedEffect(conversationId) {
    viewModel.initConversation(conversationId, initialPrompt)
  }

  // Auto-start microphone dictation if triggered from widget
  LaunchedEffect(startWithVoiceMic) {
    if (startWithVoiceMic && !isListeningVoice) {
      handleToggleVoiceInput()
    }
  }

  // Auto-dismiss transient action feedback banner after 2.5s
  LaunchedEffect(uiState.actionFeedback) {
    if (uiState.actionFeedback != null) {
      delay(2500L)
      viewModel.clearActionFeedback()
    }
  }

  // Determine whether the user is currently near the bottom of the message list
  val isNearBottom by remember {
    derivedStateOf {
      val layoutInfo = listState.layoutInfo
      val totalItems = layoutInfo.totalItemsCount
      if (totalItems <= 1) {
        true
      } else {
        val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        lastVisibleIndex >= totalItems - 2
      }
    }
  }

  // Smart auto-scroll: only scroll when user is near the bottom (never yank away from reading history)
  val lastMessageId = uiState.messages.lastOrNull()?.id
  val lastMessageLength = uiState.messages.lastOrNull()?.content?.length ?: 0

  LaunchedEffect(uiState.messages.size, lastMessageId, lastMessageLength) {
    if (uiState.messages.isNotEmpty() && isNearBottom) {
      val targetIndex = uiState.messages.lastIndex
      if (reducedMotion || uiState.isGenerating) {
        listState.scrollToItem(targetIndex)
      } else {
        listState.animateScrollToItem(targetIndex)
      }
    }
  }

  val handleCopyText: (String, String) -> Unit = { label, text ->
    val ok = copyTextToSystemClipboard(context, label, text)
    if (ok) {
      viewModel.showActionFeedback("$label copied to clipboard")
    } else {
      viewModel.showActionFeedback("Unable to access clipboard")
    }
  }

  val handleShareMessage: (ChatMessage) -> Unit = { message ->
    val formatted = message.formatForCopyOrShare()
    val shared = shareTextViaAndroidSheet(context, "Astryn AI Response", formatted)
    if (shared) {
      viewModel.showActionFeedback("Opening Share sheet…")
    } else {
      val copied = copyTextToSystemClipboard(context, "Astryn AI Response", formatted)
      if (copied) {
        viewModel.showActionFeedback("Response copied to clipboard for sharing")
      } else {
        viewModel.showActionFeedback("Unable to share response")
      }
    }
  }

  val matchingSearchIndices =
    remember(uiState.messages, inConvSearchQuery) {
      val q = inConvSearchQuery.trim()
      if (q.isBlank()) {
        emptyList()
      } else {
        uiState.messages.mapIndexedNotNull { idx, msg ->
          if (msg.content.contains(q, ignoreCase = true)) idx else null
        }
      }
    }

  val latestMoodHint =
    remember(uiState.messages, uiState.inputText) {
      uiState.inputText.ifBlank { uiState.messages.lastOrNull()?.content.orEmpty() }
    }

  AmbientGlassBackground(
    isAiActive = uiState.isGenerating || speakingMessageId != null || isListeningVoice,
    moodHint = latestMoodHint,
    modifier =
      modifier
        .drawWithCache {
          val headerGlow =
            Brush.radialGradient(
              colors =
                listOf(
                  Color(0xFF00F2FE).copy(alpha = if (glass.isDark) 0.10f else 0.04f),
                  Color(0xFF8B5CF6).copy(alpha = if (glass.isDark) 0.08f else 0.03f),
                  Color.Transparent,
                ),
              center = Offset(size.width * 0.5f, 0f),
              radius = (size.width * 0.85f).coerceAtLeast(1f),
            )
          onDrawBehind {
            if (!glass.reduceTransparency) {
              drawRect(brush = headerGlow)
            }
          }
        }
        .testTag("chat_screen"),
  ) {
    Scaffold(
      modifier = Modifier.fillMaxSize(),
      containerColor = Color.Transparent,
      contentWindowInsets = WindowInsets.systemBars,
    ) { innerPadding ->
      Column(
        modifier =
          Modifier.fillMaxSize()
            .padding(innerPadding)
            .imePadding(),
      ) {
        val bookmarkedMessages =
          remember(uiState.messages) {
            uiState.messages.filter { it.isBookmarked && it.content.isNotBlank() }
          }

        // -----------------------------------------------------------------
        // 1. Locked Astryn AI Glass Top Header Bar
        // -----------------------------------------------------------------
        ChatTopHeaderBar(
          conversationTitle =
            uiState.conversationTitle.ifBlank { stringResource(R.string.app_name) },
          selectedModelName =
            uiState.selectedModelName.ifBlank { stringResource(R.string.chat_model_indicator) },
          selectedPersona = uiState.selectedPersona,
          isOnline = uiState.isOnline,
          isGenerating = uiState.isGenerating,
          bookmarkCount = bookmarkedMessages.size,
          isSearchActive = showInConvSearch,
          isLiveVoiceMode = isLiveVoiceMode,
          onOpenPersonaSelector = { showPersonaDialog = true },
          onToggleInConvSearch = {
            showInConvSearch = !showInConvSearch
            if (!showInConvSearch) {
              inConvSearchQuery = ""
              activeSearchMatchOrdinal = 0
            }
          },
          onToggleLiveVoiceMode = {
            val next = !isLiveVoiceMode
            isLiveVoiceMode = next
            if (!next) {
              speechController.stop()
            }
          },
          onOpenBookmarks = { showBookmarksDialog = true },
          onBack = onBack,
          onOpenMenu = onOpenMenu,
        )

        // -----------------------------------------------------------------
        // 1B. Collapsible In-Conversation Search Bar (Chat ke andar Search)
        // -----------------------------------------------------------------
        AnimatedVisibility(
          visible = showInConvSearch,
          enter = fadeIn(),
          exit = fadeOut(),
        ) {
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .padding(horizontal = AppSpacing.screenHorizontal, vertical = 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(glass.surfaceElevated.copy(alpha = 0.92f))
                .border(AppBorder.hairline, glass.accentCyan.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("chat_in_conv_search_bar"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            GlassTextField(
              value = inConvSearchQuery,
              onValueChange = {
                inConvSearchQuery = it
                activeSearchMatchOrdinal = 0
              },
              placeholder = "Search in chat…",
              singleLine = true,
              leadingContent = {
                Icon(
                  imageVector = Icons.Outlined.Search,
                  contentDescription = null,
                  tint = glass.accentCyan,
                  modifier = Modifier.size(15.dp),
                )
              },
              modifier = Modifier.weight(1f).testTag("chat_in_conv_search_input"),
            )

            val safeOrdinal =
              if (matchingSearchIndices.isEmpty()) 0
              else activeSearchMatchOrdinal.coerceIn(0, matchingSearchIndices.lastIndex)
            val countText =
              if (inConvSearchQuery.trim().isBlank() || matchingSearchIndices.isEmpty()) {
                "0/0"
              } else {
                "${safeOrdinal + 1}/${matchingSearchIndices.size}"
              }

            Text(
              text = countText,
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
              fontWeight = FontWeight.SemiBold,
              color = if (matchingSearchIndices.isNotEmpty()) glass.accentCyan else glass.textMuted,
              modifier = Modifier.testTag("chat_in_conv_search_count"),
            )

            Box(
              modifier =
                Modifier.size(26.dp)
                  .clip(CircleShape)
                  .background(glass.surfaceStandard)
                  .clickable(enabled = matchingSearchIndices.isNotEmpty()) {
                    if (matchingSearchIndices.isNotEmpty()) {
                      val prevOrd =
                        if (safeOrdinal - 1 < 0) matchingSearchIndices.lastIndex
                        else safeOrdinal - 1
                      activeSearchMatchOrdinal = prevOrd
                      val targetMsgIdx = matchingSearchIndices[prevOrd]
                      coroutineScope.launch { listState.scrollToItem(targetMsgIdx) }
                    }
                  }
                  .testTag("chat_in_conv_search_prev"),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Outlined.KeyboardArrowUp,
                contentDescription = "Previous match",
                tint = if (matchingSearchIndices.isNotEmpty()) glass.accentCyan else glass.textDisabled,
                modifier = Modifier.size(15.dp),
              )
            }

            Box(
              modifier =
                Modifier.size(26.dp)
                  .clip(CircleShape)
                  .background(glass.surfaceStandard)
                  .clickable(enabled = matchingSearchIndices.isNotEmpty()) {
                    if (matchingSearchIndices.isNotEmpty()) {
                      val nextOrd = (safeOrdinal + 1) % matchingSearchIndices.size
                      activeSearchMatchOrdinal = nextOrd
                      val targetMsgIdx = matchingSearchIndices[nextOrd]
                      coroutineScope.launch { listState.scrollToItem(targetMsgIdx) }
                    }
                  }
                  .testTag("chat_in_conv_search_next"),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = "Next match",
                tint = if (matchingSearchIndices.isNotEmpty()) glass.accentCyan else glass.textDisabled,
                modifier = Modifier.size(15.dp),
              )
            }

            Box(
              modifier =
                Modifier.size(26.dp)
                  .clip(CircleShape)
                  .background(glass.surfaceStandard)
                  .clickable {
                    showInConvSearch = false
                    inConvSearchQuery = ""
                    activeSearchMatchOrdinal = 0
                  }
                  .testTag("chat_in_conv_search_close"),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Close search",
                tint = glass.textSecondary,
                modifier = Modifier.size(14.dp),
              )
            }
          }
        }

        // -----------------------------------------------------------------
        // 2. Non-Blocking Offline Connectivity Banner
        // -----------------------------------------------------------------
        AnimatedVisibility(
          visible = !uiState.isOnline,
          enter = fadeIn(),
          exit = fadeOut(),
        ) {
          GlassSurface(
            modifier =
              Modifier.fillMaxWidth()
                .padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.xs)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag("chat_offline_banner"),
            elevated = true,
            contentPadding = PaddingValues(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
              Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = "Offline status",
                tint = glass.statusWarning,
                modifier = Modifier.size(20.dp),
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = stringResource(R.string.chat_offline_banner_title),
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.SemiBold,
                  color = glass.statusWarning,
                )
                Text(
                  text = stringResource(R.string.chat_offline_banner_subtitle),
                  style = MaterialTheme.typography.bodySmall,
                  color = glass.textSecondary,
                )
              }
            }
          }
        }

        // -----------------------------------------------------------------
        // 3. Local Storage Persistence Error Banner (Dismissible)
        // -----------------------------------------------------------------
        AnimatedVisibility(
          visible = uiState.storageErrorNotice != null,
          enter = fadeIn(),
          exit = fadeOut(),
        ) {
          uiState.storageErrorNotice?.let { storageNotice ->
            GlassSurface(
              modifier =
                Modifier.fillMaxWidth()
                  .padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.xs)
                  .semantics { liveRegion = LiveRegionMode.Assertive }
                  .testTag("chat_storage_error_banner"),
              elevated = true,
              contentPadding = PaddingValues(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
              ) {
                Icon(
                  imageVector = Icons.Outlined.WarningAmber,
                  contentDescription = null,
                  tint = glass.statusWarning,
                  modifier = Modifier.size(20.dp),
                )
                Text(
                  text = storageNotice,
                  style = MaterialTheme.typography.bodySmall,
                  color = glass.textPrimary,
                  modifier = Modifier.weight(1f),
                )
                GlassIconButton(
                  icon = Icons.Outlined.Close,
                  contentDescription = "Dismiss storage notice",
                  onClick = { viewModel.dismissStorageError() },
                )
              }
            }
          }
        }

        // -----------------------------------------------------------------
        // 4. API Key Configuration Banner (Informative & Dismissible)
        // -----------------------------------------------------------------
        AnimatedVisibility(
          visible = uiState.bannerNotice != null,
          enter = fadeIn(),
          exit = fadeOut(),
        ) {
          uiState.bannerNotice?.let { notice ->
            GlassSurface(
              modifier =
                Modifier.fillMaxWidth()
                  .padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.xs)
                  .semantics { liveRegion = LiveRegionMode.Polite }
                  .testTag("chat_config_banner"),
              elevated = true,
              contentPadding = PaddingValues(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = glass.accentCyan,
                    modifier = Modifier.size(20.dp),
                  )
                  Text(
                    text = notice,
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.textPrimary,
                    modifier = Modifier.weight(1f),
                  )
                  GlassIconButton(
                    icon = Icons.Outlined.Close,
                    contentDescription = "Dismiss notice",
                    onClick = { viewModel.dismissBanner() },
                  )
                }
                if (!uiState.isApiKeyConfigured) {
                  GlassButton(
                    text = "Configure API Key",
                    leadingIcon = Icons.Outlined.Key,
                    variant = GlassButtonVariant.SECONDARY,
                    onClick = handleOpenApiConfig,
                    modifier = Modifier.testTag("chat_configure_api_key_button"),
                  )
                }
              }
            }
          }
        }

        // -----------------------------------------------------------------
        // 5. Context Window Omission Notice
        // -----------------------------------------------------------------
        AnimatedVisibility(
          visible = uiState.omittedContextTurns > 0,
          enter = fadeIn(),
          exit = fadeOut(),
        ) {
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .padding(horizontal = AppSpacing.screenHorizontal, vertical = 2.dp),
            contentAlignment = Alignment.Center,
          ) {
            Row(
              modifier =
                Modifier.clip(AppRadius.shapePill)
                  .background(glass.surfaceStandard.copy(alpha = 0.78f))
                  .border(
                    AppBorder.hairline,
                    glass.accentCyan.copy(alpha = 0.35f),
                    AppRadius.shapePill,
                  )
                  .padding(horizontal = AppSpacing.md, vertical = 4.dp)
                  .testTag("chat_context_omission_banner"),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = glass.accentCyan,
                modifier = Modifier.size(13.dp),
              )
              Text(
                text =
                  "Recent context active (${uiState.omittedContextTurns} older ${if (uiState.omittedContextTurns == 1) "turn" else "turns"} omitted from request; full history saved)",
                style = MaterialTheme.typography.labelSmall,
                color = glass.textSecondary,
              )
            }
          }
        }

        // -----------------------------------------------------------------
        // 6. Action Feedback Pill (Copy / Share / Attach / Edit Confirmation)
        // -----------------------------------------------------------------
        AnimatedVisibility(
          visible = uiState.actionFeedback != null,
          enter = fadeIn(),
          exit = fadeOut(),
        ) {
          uiState.actionFeedback?.let { feedback ->
            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .padding(horizontal = AppSpacing.screenHorizontal, vertical = 2.dp),
              contentAlignment = Alignment.Center,
            ) {
              Row(
                modifier =
                  Modifier.clip(AppRadius.shapePill)
                    .background(glass.surfaceElevated)
                    .border(AppBorder.hairline, glass.accentCyan, AppRadius.shapePill)
                    .padding(horizontal = AppSpacing.md, vertical = 4.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag("chat_action_feedback_pill"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Icon(
                  imageVector = Icons.Outlined.Check,
                  contentDescription = null,
                  tint = glass.accentCyan,
                  modifier = Modifier.size(14.dp),
                )
                Text(
                  text = feedback,
                  style = MaterialTheme.typography.labelSmall,
                  color = glass.textPrimary,
                  fontWeight = FontWeight.Medium,
                )
              }
            }
          }
        }

        // -----------------------------------------------------------------
        // 7. Messages Scroll Area + Jump to Latest Floating Button
        // -----------------------------------------------------------------
        Box(
          modifier =
            Modifier.weight(1f)
              .fillMaxWidth(),
        ) {
          if (uiState.messages.isEmpty()) {
            ChatEmptyConversationState(
              onSelectQuickPrompt = { starterPrompt ->
                viewModel.onInputChanged(starterPrompt)
              }
            )
          } else {
            val lastUserMessageIndex =
              remember(uiState.messages) {
                uiState.messages.indexOfLast { it.role == MessageRole.USER }
              }

            val trimmedSearch = inConvSearchQuery.trim()

            LazyColumn(
              state = listState,
              modifier =
                Modifier.fillMaxSize()
                  .testTag("chat_messages_list"),
              contentPadding =
                PaddingValues(
                  horizontal = AppSpacing.screenHorizontal,
                  vertical = AppSpacing.md,
                ),
              verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
              itemsIndexed(
                items = uiState.messages,
                key = { _, msg -> msg.id },
                contentType = { _, msg -> msg.role },
              ) { index, message ->
                val isLastMessage = index == uiState.messages.lastIndex
                val isLastUserMessage = index == lastUserMessageIndex
                val showActions =
                  message.role == MessageRole.MODEL ||
                    isLastMessage ||
                    isLastUserMessage ||
                    activeActionMessageId == message.id ||
                    message.status == MessageStatus.ERROR ||
                    message.status == MessageStatus.INTERRUPTED

                val isSearchMatch =
                  trimmedSearch.isNotBlank() &&
                    message.content.contains(trimmedSearch, ignoreCase = true)

                ChatMessageItem(
                  message = message,
                  isLastMessage = isLastMessage,
                  showActions = showActions,
                  isGenerating = uiState.isGenerating,
                  isSpeaking = speakingMessageId == message.id,
                  speechRateLabel = AstrynSpeechController.formatRate(speechRate),
                  isSearchMatch = isSearchMatch,
                  onToggleActions = {
                    activeActionMessageId =
                      if (activeActionMessageId == message.id) null else message.id
                  },
                  onCopyMessage = {
                    handleCopyText("Message", message.formatForCopyOrShare())
                  },
                  onShareMessage = {
                    handleShareMessage(message)
                  },
                  onToggleLike = {
                    viewModel.toggleMessageLike(message.id)
                  },
                  onToggleBookmark = {
                    viewModel.toggleMessageBookmark(message.id)
                  },
                  onToggleSpeak = {
                    val started = speechController.toggleSpeak(message)
                    if (started) {
                      viewModel.showActionFeedback(
                        "Reading aloud (${AstrynSpeechController.formatRate(speechRate)})…"
                      )
                    } else {
                      viewModel.showActionFeedback("Stopped reading aloud")
                    }
                  },
                  onCycleSpeechSpeed = {
                    val nextRate = speechController.cycleSpeechRate()
                    speechRate = nextRate
                    viewModel.showActionFeedback(
                      "Voice speed: ${AstrynSpeechController.formatRate(nextRate)}"
                    )
                  },
                  onModifyResponse = { mode ->
                    viewModel.modifyAssistantMessage(message.id, mode)
                  },
                  onCompareResponse = {
                    viewModel.compareAssistantMessage(message.id)
                  },
                  onTranslateResponse = { targetLanguage ->
                    viewModel.translateAssistantMessage(message.id, targetLanguage)
                  },
                  onExportResponse = { asPdf ->
                    viewModel.exportAssistantMessage(context, message.id, asPdf = asPdf)
                  },
                  onSelectFollowUp = { followUpPrompt ->
                    viewModel.sendMessage(followUpPrompt)
                  },
                  onCopyCode = { codeSnippet ->
                    handleCopyText("Code", codeSnippet)
                  },
                  onDownloadCode = { codeSnippet, language ->
                    viewModel.downloadCodeSnippet(context, codeSnippet, language)
                  },
                  onRunCode = { codeSnippet, language ->
                    runningCodeSnippet = codeSnippet to language
                  },
                  onGenerateQuiz = { topicText ->
                    quizInitialTopic = topicText
                    showQuizDialog = true
                  },
                  onRegenerate = {
                    speechController.stop()
                    viewModel.regenerateAssistantMessage(message.id)
                  },
                  onEditMessage = {
                    editingMessage = message
                    editDraftText = message.content
                  },
                  onQuoteLine = { quotedLine ->
                    activeQuotedText = quotedLine
                    viewModel.showActionFeedback("Line quoted — type your question or tap Explain Deeply")
                  },
                  onActionFeedback = viewModel::showActionFeedback,
                  onConfigureApiKey = handleOpenApiConfig,
                )
              }
            }

            // Accessible "Jump to latest" floating pill when scrolled up in history
            if (!isNearBottom && uiState.messages.isNotEmpty()) {
              Box(
                modifier =
                  Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = AppSpacing.sm),
              ) {
                Row(
                  modifier =
                    Modifier.minimumInteractiveComponentSize()
                      .defaultMinSize(minHeight = 40.dp)
                      .clip(AppRadius.shapePill)
                      .background(
                        brush =
                          Brush.horizontalGradient(
                            colors =
                              listOf(
                                glass.surfaceElevated,
                                glass.accentPrimary.copy(alpha = 0.25f),
                              )
                          ),
                        shape = AppRadius.shapePill,
                      )
                      .border(AppBorder.focused, glass.accentCyan, AppRadius.shapePill)
                      .semantics {
                        role = Role.Button
                        contentDescription = "Jump to latest message"
                      }
                      .clickable {
                        coroutineScope.launch {
                          val lastIdx = uiState.messages.lastIndex
                          if (lastIdx >= 0) {
                            if (reducedMotion) {
                              listState.scrollToItem(lastIdx)
                            } else {
                              listState.animateScrollToItem(lastIdx)
                            }
                          }
                        }
                      }
                      .padding(horizontal = AppSpacing.md, vertical = 8.dp)
                      .testTag("chat_scroll_to_bottom_button"),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.ArrowDownward,
                    contentDescription = null,
                    tint = glass.accentCyan,
                    modifier = Modifier.size(16.dp),
                  )
                  Text(
                    text = stringResource(R.string.chat_jump_to_latest),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = glass.textPrimary,
                  )
                }
              }
            }
          }
        }

        // -----------------------------------------------------------------
        // 7b. Active Quote & Ask Banner (when a line/sentence is highlighted)
        // -----------------------------------------------------------------
        if (activeQuotedText.isNotBlank()) {
          ActiveQuoteReplyBanner(
            quotedText = activeQuotedText,
            onClearQuote = { activeQuotedText = "" },
            onAskDeepDiveNow = {
              val quoted = activeQuotedText
              activeQuotedText = ""
              viewModel.sendMessage(
                "Explain this specific line in deep detail with practical examples:\n\"$quoted\""
              )
            },
          )
        }

        // -----------------------------------------------------------------
        // 8. Fixed Bottom Multiline Glass Composer Bar
        // -----------------------------------------------------------------
        ChatComposerBar(
          inputText = uiState.inputText,
          pendingAttachments = uiState.pendingAttachments,
          isProcessingAttachment = uiState.isProcessingAttachment,
          isGenerating = uiState.isGenerating,
          isListeningVoice = isListeningVoice,
          onInputChanged = viewModel::onInputChanged,
          onPickAttachments = handlePickAttachments,
          onCaptureCameraPhoto = handleCaptureCameraPhoto,
          onOpenPromptTemplates = { showTemplatesDialog = true },
          onOpenQuiz = {
            quizInitialTopic = ""
            showQuizDialog = true
          },
          onRemoveAttachment = viewModel::removePendingAttachment,
          onToggleVoiceInput = handleToggleVoiceInput,
          onSendMessage = {
            if (isListeningVoice) {
              handleToggleVoiceInput()
            }
            if (activeQuotedText.isNotBlank()) {
              val quoted = activeQuotedText
              val userQuestion = uiState.inputText.trim()
              activeQuotedText = ""
              viewModel.onInputChanged("")
              val combinedPrompt =
                if (userQuestion.isNotEmpty()) {
                  "Regarding this part of your response: \"$quoted\"\n\nMy question: $userQuestion"
                } else {
                  "Explain this specific part in detail: \"$quoted\""
                }
              viewModel.sendMessage(combinedPrompt)
            } else {
              viewModel.sendMessage()
            }
          },
          onStopGeneration = viewModel::stopGeneration,
        )
      }
    }

    // ---------------------------------------------------------------------
    // Full-Screen Premium Voice-to-Voice Live AI Girlfriend Page ("Aanya")
    // ---------------------------------------------------------------------
    if (isLiveVoiceMode) {
      val latestAssistantReply =
        remember(uiState.messages, liveSessionOpenedAt, lastLiveUserSpokenText) {
          if (lastLiveUserSpokenText.isBlank()) {
            ""
          } else {
            uiState.messages
              .lastOrNull {
                it.role == MessageRole.MODEL &&
                  it.timestamp >= liveSessionOpenedAt &&
                  (it.status == MessageStatus.SUCCESS || it.status == MessageStatus.STREAMING) &&
                  it.content.isNotBlank() &&
                  !GeminiLiveVoiceSession.isErrorOrSystemDiagnosticText(it.content)
              }
              ?.content
              .orEmpty()
          }
        }

      LiveGirlfriendVoiceCallScreen(
        selectedMood = liveGirlfriendMood,
        isListeningVoice = isListeningVoice,
        isGenerating = uiState.isGenerating,
        isSpeakingVoice = speakingMessageId != null,
        autoHandsFree = liveAutoHandsFree,
        lastUserSpokenText = lastLiveUserSpokenText,
        lastGirlfriendReplyText = latestAssistantReply,
        isWhisperMode = liveWhisperMode,
        onToggleWhisperMode = { enabled ->
          liveWhisperMode = enabled
          speechController.isWhisperMode = enabled
          if (!isNativeLiveAudioPlaying) {
            val line =
              if (enabled) LiveGirlfriendMood.WHISPER_GREETING_LINE
              else liveGirlfriendMood.greetingLine
            speechController.speakAsGirlfriend(
              utteranceId = "live_gf_whisper_${System.currentTimeMillis()}",
              rawText = line,
              mood = liveGirlfriendMood,
            )
          }
        },
        onSelectMood = { newMood ->
          liveGirlfriendMood = newMood
          if (!isNativeLiveAudioPlaying) {
            speechController.speakAsGirlfriend(
              utteranceId = "live_gf_mood_${newMood.name}",
              rawText = newMood.greetingLine,
              mood = newMood,
            )
          }
        },
        onToggleTalkMic = handleToggleVoiceInput,
        onToggleAutoHandsFree = { liveAutoHandsFree = !liveAutoHandsFree },
        onReplayLastVoice = {
          val textToReplay =
            latestAssistantReply.ifBlank { liveGirlfriendMood.greetingLine }
          speechController.speakAsGirlfriend(
            utteranceId = "live_gf_replay_${System.currentTimeMillis()}",
            rawText = textToReplay,
            mood = liveGirlfriendMood,
          )
        },
        onSendLivePrompt = { spokenOrQuickPrompt ->
          speechController.stop()
          lastLiveUserSpokenText = spokenOrQuickPrompt
          viewModel.sendMessage(
            promptOverride = spokenOrQuickPrompt,
            liveVoiceMood = liveGirlfriendMood,
          )
        },
        onEndCall = {
          isLiveVoiceMode = false
          isNativeLiveAudioPlaying = false
          if (isListeningVoice) {
            handleToggleVoiceInput()
          }
          speechController.stop()
        },
        onStopFallbackTts = {
          speechController.stop()
        },
        onSpeakFallbackTts = { textToSpeak, mood ->
          if (!isNativeLiveAudioPlaying) {
            speechController.speakAsGirlfriend(
              utteranceId = "live_gf_ws_text_${System.currentTimeMillis()}",
              rawText = textToSpeak,
              mood = mood,
            )
          }
        },
        onNativeAudioActiveChanged = { isActive ->
          isNativeLiveAudioPlaying = isActive
          if (isActive) {
            speechController.stop()
          }
        },
      )
    }

    // ---------------------------------------------------------------------
    // Edit & Resend User Message Dialog
    // ---------------------------------------------------------------------
    editingMessage?.let { targetMsg ->
      AlertDialog(
        onDismissRequest = { editingMessage = null },
        modifier = Modifier.testTag("edit_message_dialog"),
        containerColor = glass.surfaceElevated.copy(alpha = 0.98f),
        titleContentColor = glass.textPrimary,
        textContentColor = glass.textSecondary,
        title = {
          Text(
            text = stringResource(R.string.dialog_edit_message_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
        },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            Text(
              text = stringResource(R.string.dialog_edit_message_subtitle),
              style = MaterialTheme.typography.bodySmall,
              color = glass.textSecondary,
            )
            GlassTextField(
              value = editDraftText,
              onValueChange = { editDraftText = it },
              placeholder = stringResource(R.string.chat_composer_placeholder),
              singleLine = false,
              maxLines = 6,
              modifier = Modifier.fillMaxWidth().testTag("edit_message_input"),
            )
          }
        },
        confirmButton = {
          GlassButton(
            text = stringResource(R.string.dialog_edit_message_confirm),
            leadingIcon = Icons.AutoMirrored.Outlined.Send,
            variant = GlassButtonVariant.PRIMARY,
            enabled = editDraftText.trim().isNotEmpty() && !uiState.isGenerating,
            onClick = {
              val submitted =
                viewModel.editAndResendUserMessage(
                  userMessageId = targetMsg.id,
                  updatedPrompt = editDraftText,
                )
              if (submitted) {
                editingMessage = null
              }
            },
            modifier = Modifier.testTag("edit_message_confirm_button"),
          )
        },
        dismissButton = {
          GlassButton(
            text = stringResource(R.string.dialog_cancel),
            variant = GlassButtonVariant.SUBTLE,
            onClick = { editingMessage = null },
            modifier = Modifier.testTag("edit_message_cancel_button"),
          )
        },
      )
    }

    // ---------------------------------------------------------------------
    // Standalone API Key Setup Modal (when ChatScreen is hosted without external nav callback)
    // ---------------------------------------------------------------------
    if (showApiKeySetupDialog) {
      AlertDialog(
        onDismissRequest = { showApiKeySetupDialog = false },
        modifier = Modifier.testTag("chat_api_key_setup_dialog"),
        containerColor = glass.surfaceElevated.copy(alpha = 0.98f),
        titleContentColor = glass.textPrimary,
        textContentColor = glass.textSecondary,
        title = {
          Text(
            text = "AI Provider & API Key",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
        },
        text = {
          Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
          ) {
            AiProviderApiKeyPanel()
          }
        },
        confirmButton = {
          GlassButton(
            text = "Done",
            variant = GlassButtonVariant.SUBTLE,
            onClick = { showApiKeySetupDialog = false },
            modifier = Modifier.testTag("chat_api_key_setup_done_button"),
          )
        },
      )
    }

    // ---------------------------------------------------------------------
    // AI Personas / Custom Modes Selector Dialog
    // ---------------------------------------------------------------------
    if (showPersonaDialog) {
      AlertDialog(
        onDismissRequest = { showPersonaDialog = false },
        modifier = Modifier.testTag("chat_persona_dialog"),
        containerColor = glass.surfaceElevated.copy(alpha = 0.98f),
        titleContentColor = glass.textPrimary,
        textContentColor = glass.textSecondary,
        title = {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.AutoAwesome,
              contentDescription = null,
              tint = glass.accentCyan,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = "AI Persona & Role Mode",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
          }
        },
        text = {
          Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            AiPersona.entries.forEach { persona ->
              val isSelected = uiState.selectedPersona == persona
              Row(
                modifier =
                  Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                      if (isSelected) glass.accentCyan.copy(alpha = 0.16f)
                      else glass.surfaceStandard.copy(alpha = 0.78f)
                    )
                    .border(
                      AppBorder.hairline,
                      if (isSelected) glass.accentCyan else glass.borderSubtle,
                      RoundedCornerShape(12.dp),
                    )
                    .clickable {
                      viewModel.selectPersona(persona)
                      showPersonaDialog = false
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("chat_persona_option_${persona.name}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Text(
                      text = persona.label,
                      style = MaterialTheme.typography.labelLarge,
                      fontWeight = FontWeight.Bold,
                      color = if (isSelected) glass.accentCyan else glass.textPrimary,
                    )
                    Text(
                      text = persona.shortBadge,
                      style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                      color = glass.accentCyan,
                      modifier =
                        Modifier.clip(AppRadius.shapePill)
                          .background(glass.accentPrimary.copy(alpha = 0.18f))
                          .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                  }
                  Text(
                    text = persona.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.textSecondary,
                  )
                }
                if (isSelected) {
                  Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "Selected",
                    tint = glass.accentCyan,
                    modifier = Modifier.size(18.dp),
                  )
                }
              }
            }
          }
        },
        confirmButton = {
          GlassButton(
            text = "Close",
            variant = GlassButtonVariant.SUBTLE,
            onClick = { showPersonaDialog = false },
            modifier = Modifier.testTag("chat_persona_close_button"),
          )
        },
      )
    }

    // ---------------------------------------------------------------------
    // Prompt Library / Saved 1-Tap Templates Dialog
    // ---------------------------------------------------------------------
    if (showTemplatesDialog) {
      AlertDialog(
        onDismissRequest = {
          showTemplatesDialog = false
          showAddTemplateForm = false
        },
        modifier = Modifier.testTag("chat_templates_dialog"),
        containerColor = glass.surfaceElevated.copy(alpha = 0.98f),
        titleContentColor = glass.textPrimary,
        textContentColor = glass.textSecondary,
        title = {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.Bolt,
                contentDescription = null,
                tint = glass.accentCyan,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = "1-Tap Prompt Templates",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
            }
            MessageActionChip(
              icon = if (showAddTemplateForm) Icons.Outlined.Close else Icons.Outlined.Add,
              label = if (showAddTemplateForm) "Cancel" else "+ New",
              contentDescription = "Create custom prompt template",
              onClick = { showAddTemplateForm = !showAddTemplateForm },
              isHighlighted = showAddTemplateForm,
              testTag = "chat_template_add_toggle",
            )
          }
        },
        text = {
          Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            if (showAddTemplateForm) {
              Column(
                modifier =
                  Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(glass.surfaceStandard)
                    .border(AppBorder.hairline, glass.accentCyan, RoundedCornerShape(12.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                GlassTextField(
                  value = newTemplateTitle,
                  onValueChange = { newTemplateTitle = it },
                  placeholder = "Template Title (e.g., Hindi Summary)",
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth().testTag("chat_template_title_input"),
                )
                GlassTextField(
                  value = newTemplatePrompt,
                  onValueChange = { newTemplatePrompt = it },
                  placeholder = "Prompt text to insert…",
                  singleLine = false,
                  maxLines = 4,
                  modifier = Modifier.fillMaxWidth().testTag("chat_template_prompt_input"),
                )
                GlassButton(
                  text = "Save Template",
                  leadingIcon = Icons.Outlined.Check,
                  variant = GlassButtonVariant.PRIMARY,
                  enabled = newTemplateTitle.isNotBlank() && newTemplatePrompt.isNotBlank(),
                  onClick = {
                    val saved =
                      viewModel.addCustomPromptTemplate(newTemplateTitle, newTemplatePrompt)
                    if (saved) {
                      newTemplateTitle = ""
                      newTemplatePrompt = ""
                      showAddTemplateForm = false
                    }
                  },
                  modifier = Modifier.fillMaxWidth().testTag("chat_template_save_button"),
                )
              }
            }

            uiState.promptTemplates.forEach { template ->
              Row(
                modifier =
                  Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(glass.surfaceStandard.copy(alpha = 0.8f))
                    .border(
                      AppBorder.hairline,
                      glass.accentCyan.copy(alpha = 0.35f),
                      RoundedCornerShape(12.dp),
                    )
                    .clickable {
                      val currentDraft = uiState.inputText.trim()
                      val nextInput =
                        if (currentDraft.isEmpty()) template.prompt
                        else "${template.prompt}\n\n$currentDraft"
                      viewModel.onInputChanged(nextInput)
                      viewModel.showActionFeedback("Applied: ${template.title}")
                      showTemplatesDialog = false
                    }
                    .padding(10.dp)
                    .testTag("chat_template_item_${template.id}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column(
                  modifier = Modifier.weight(1f),
                  verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Text(
                      text = template.title,
                      style = MaterialTheme.typography.labelLarge,
                      fontWeight = FontWeight.Bold,
                      color = glass.textPrimary,
                    )
                    Text(
                      text = template.category,
                      style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                      color = glass.accentCyan,
                      modifier =
                        Modifier.clip(AppRadius.shapePill)
                          .background(glass.accentPrimary.copy(alpha = 0.16f))
                          .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                  }
                  Text(
                    text = template.prompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                if (template.isCustom) {
                  Box(
                    modifier =
                      Modifier.size(28.dp)
                        .clip(CircleShape)
                        .clickable { viewModel.deleteCustomPromptTemplate(template.id) }
                        .testTag("chat_template_delete_${template.id}"),
                    contentAlignment = Alignment.Center,
                  ) {
                    Icon(
                      imageVector = Icons.Outlined.DeleteOutline,
                      contentDescription = "Delete template",
                      tint = glass.statusError,
                      modifier = Modifier.size(16.dp),
                    )
                  }
                }
              }
            }
          }
        },
        confirmButton = {
          GlassButton(
            text = "Close",
            variant = GlassButtonVariant.SUBTLE,
            onClick = {
              showTemplatesDialog = false
              showAddTemplateForm = false
            },
            modifier = Modifier.testTag("chat_templates_close_button"),
          )
        },
      )
    }

    // ---------------------------------------------------------------------
    // Saved Bookmarks Dialog (View & Copy Bookmarked AI Responses)
    // ---------------------------------------------------------------------
    if (showBookmarksDialog) {
      val bookmarkedItems =
        remember(uiState.messages) {
          uiState.messages.filter { it.isBookmarked && it.content.isNotBlank() }
        }
      AlertDialog(
        onDismissRequest = { showBookmarksDialog = false },
        modifier = Modifier.testTag("chat_bookmarks_dialog"),
        containerColor = glass.surfaceElevated.copy(alpha = 0.98f),
        titleContentColor = glass.textPrimary,
        textContentColor = glass.textSecondary,
        title = {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Bookmark,
              contentDescription = null,
              tint = glass.accentCyan,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = "Saved Bookmarks (${bookmarkedItems.size})",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
          }
        },
        text = {
          if (bookmarkedItems.isEmpty()) {
            Text(
              text = "No bookmarked responses yet. Tap the small 'Save' icon below any AI response to bookmark important answers here.",
              style = MaterialTheme.typography.bodySmall,
              color = glass.textSecondary,
              modifier = Modifier.testTag("chat_bookmarks_empty_text"),
            )
          } else {
            Column(
              modifier = Modifier.verticalScroll(rememberScrollState()),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              bookmarkedItems.forEach { item ->
                Column(
                  modifier =
                    Modifier.fillMaxWidth()
                      .clip(RoundedCornerShape(12.dp))
                      .background(glass.surfaceStandard.copy(alpha = 0.8f))
                      .border(
                        AppBorder.hairline,
                        glass.accentCyan.copy(alpha = 0.4f),
                        RoundedCornerShape(12.dp),
                      )
                      .padding(10.dp)
                      .testTag("chat_bookmark_item_${item.id}"),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Text(
                    text = item.content.take(260) + if (item.content.length > 260) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.textPrimary,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    MessageActionChip(
                      icon = Icons.Outlined.ContentCopy,
                      label = "Copy",
                      contentDescription = "Copy bookmarked response",
                      onClick = {
                        handleCopyText("Bookmark", item.formatForCopyOrShare())
                      },
                      testTag = "chat_bookmark_copy_${item.id}",
                    )
                    MessageActionChip(
                      icon = Icons.Filled.Bookmark,
                      label = "Remove",
                      contentDescription = "Remove bookmark",
                      onClick = {
                        viewModel.toggleMessageBookmark(item.id)
                      },
                      isHighlighted = true,
                      testTag = "chat_bookmark_remove_${item.id}",
                    )
                  }
                }
              }
            }
          }
        },
        confirmButton = {
          GlassButton(
            text = "Close",
            variant = GlassButtonVariant.SUBTLE,
            onClick = { showBookmarksDialog = false },
            modifier = Modifier.testTag("chat_bookmarks_close_button"),
          )
        },
      )
    }

    // ---------------------------------------------------------------------
    // In-App Code Runner & Live Web Preview Dialog
    // ---------------------------------------------------------------------
    runningCodeSnippet?.let { (code, lang) ->
      CodeRunnerPreviewDialog(
        initialCode = code,
        language = lang,
        onDismiss = { runningCodeSnippet = null },
        onCopyCode = { codeToCopy ->
          handleCopyText("Code", codeToCopy)
        },
      )
    }

    // ---------------------------------------------------------------------
    // Interactive Exam Quiz & Flashcard Test Dialog
    // ---------------------------------------------------------------------
    if (showQuizDialog) {
      InteractiveQuizDialog(
        initialTopic = quizInitialTopic,
        onDismiss = { showQuizDialog = false },
        onRequestAiQuiz = { topicText ->
          showQuizDialog = false
          val prompt =
            "Generate an interactive 5-question exam quiz with multiple choice options (A, B, C, D) and explanations on topic: $topicText"
          viewModel.sendMessage(prompt)
        },
      )
    }
  }
}

/**
 * Locked Astryn AI Chat Header Bar matching the approved design reference:
 * Back button on left, official Astryn AI logo + title + glowing model subtitle + compact Persona selector pill,
 * compact In-Conversation Search, Live Voice Mode, Saved Bookmarks triggers, and menu trigger button on right.
 */
@Composable
private fun ChatTopHeaderBar(
  conversationTitle: String,
  selectedModelName: String,
  selectedPersona: AiPersona,
  isOnline: Boolean,
  isGenerating: Boolean,
  bookmarkCount: Int,
  isSearchActive: Boolean,
  isLiveVoiceMode: Boolean,
  onOpenPersonaSelector: () -> Unit,
  onToggleInConvSearch: () -> Unit,
  onToggleLiveVoiceMode: () -> Unit,
  onOpenBookmarks: () -> Unit,
  onBack: () -> Unit,
  onOpenMenu: () -> Unit,
) {
  val glass = AppThemeTokens.glass
  val headerBgBrush =
    remember(glass) {
      if (glass.reduceTransparency) {
        Brush.verticalGradient(listOf(glass.surfaceSolidFallback, glass.surfaceSolidFallback))
      } else if (glass.isDark) {
        Brush.verticalGradient(
          colors =
            listOf(
              Color(0xE60B1124),
              Color(0xCC0D152C),
            )
        )
      } else {
        glass.glassSurfaceBrush(elevated = true)
      }
    }

  val bottomBorderBrush =
    remember(glass) {
      Brush.horizontalGradient(
        colors =
          listOf(
            Color.Transparent,
            glass.accentCyan.copy(alpha = 0.45f),
            glass.accentSecondary.copy(alpha = 0.45f),
            Color.Transparent,
          )
      )
    }

  Column(
    modifier =
      Modifier.fillMaxWidth()
        .background(brush = headerBgBrush)
        .testTag("chat_top_header_bar"),
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        GlassIconButton(
          icon = Icons.AutoMirrored.Outlined.ArrowBack,
          contentDescription = stringResource(R.string.chat_back_desc),
          onClick = onBack,
          modifier = Modifier.testTag("chat_back_button"),
        )

        AstrynLogoEmblem(
          size = 30.dp,
          contentDescription = "Astryn AI logo",
          modifier = Modifier.testTag("chat_brand_logo"),
        )

        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          Text(
            text = conversationTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = glass.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("chat_conversation_title"),
          )

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
          ) {
            Box(
              modifier =
                Modifier.size(6.dp)
                  .clip(CircleShape)
                  .background(
                    when {
                      !isOnline -> glass.statusWarning
                      isGenerating -> glass.accentSecondary
                      else -> glass.accentCyan
                    }
                  )
            )
            Text(
              text =
                when {
                  !isOnline -> stringResource(R.string.chat_offline_status)
                  isGenerating -> stringResource(R.string.chat_generating_status)
                  else -> selectedModelName
                },
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
              fontWeight = FontWeight.Medium,
              color =
                when {
                  !isOnline -> glass.statusWarning
                  isGenerating -> glass.accentPrimary
                  else -> glass.accentCyan
                },
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.testTag("chat_model_indicator_text"),
            )
          }
        }
      }

      Spacer(modifier = Modifier.width(4.dp))

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        // Compact AI Persona Mode Pill
        Row(
          modifier =
            Modifier.clip(AppRadius.shapePill)
              .background(
                if (selectedPersona != AiPersona.DEFAULT) glass.accentCyan.copy(alpha = 0.18f)
                else glass.surfaceStandard.copy(alpha = 0.75f)
              )
              .border(
                AppBorder.hairline,
                if (selectedPersona != AiPersona.DEFAULT) glass.accentCyan
                else glass.borderSubtle,
                AppRadius.shapePill,
              )
              .clickable(role = Role.Button, onClick = onOpenPersonaSelector)
              .semantics {
                role = Role.Button
                contentDescription = "Select AI Persona (${selectedPersona.label})"
              }
              .padding(horizontal = 5.dp, vertical = 3.dp)
              .testTag("chat_persona_selector_button"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = glass.accentCyan,
            modifier = Modifier.size(10.dp),
          )
          Text(
            text = selectedPersona.shortBadge,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
            fontWeight = FontWeight.Bold,
            color = glass.accentCyan,
            maxLines = 1,
          )
        }

        // Compact In-Conversation Search Trigger
        Box(
          modifier =
            Modifier.size(24.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(
                if (isSearchActive) glass.accentPrimary.copy(alpha = 0.22f)
                else glass.surfaceStandard.copy(alpha = 0.72f)
              )
              .border(
                AppBorder.hairline,
                if (isSearchActive) glass.accentCyan else glass.borderSubtle,
                RoundedCornerShape(8.dp),
              )
              .clickable(role = Role.Button, onClick = onToggleInConvSearch)
              .semantics {
                role = Role.Button
                contentDescription = "Search in conversation"
              }
              .testTag("chat_in_conv_search_button"),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = if (isSearchActive) glass.accentCyan else glass.textSecondary,
            modifier = Modifier.size(13.dp),
          )
        }

        // Compact Live Voice-to-Voice Mode Trigger
        Box(
          modifier =
            Modifier.size(24.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(
                if (isLiveVoiceMode) glass.accentPrimary.copy(alpha = 0.22f)
                else glass.surfaceStandard.copy(alpha = 0.72f)
              )
              .border(
                AppBorder.hairline,
                if (isLiveVoiceMode) glass.accentCyan else glass.borderSubtle,
                RoundedCornerShape(8.dp),
              )
              .clickable(role = Role.Button, onClick = onToggleLiveVoiceMode)
              .semantics {
                role = Role.Button
                contentDescription = "Toggle Live Voice mode"
              }
              .testTag("chat_live_voice_button"),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Outlined.GraphicEq,
            contentDescription = null,
            tint = if (isLiveVoiceMode) glass.accentCyan else glass.textSecondary,
            modifier = Modifier.size(13.dp),
          )
        }

        // Compact Saved Bookmarks Header Button
        Box(
          modifier =
            Modifier.size(24.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(
                if (bookmarkCount > 0) glass.accentPrimary.copy(alpha = 0.18f)
                else glass.surfaceStandard.copy(alpha = 0.72f)
              )
              .border(
                AppBorder.hairline,
                if (bookmarkCount > 0) glass.accentCyan else glass.borderSubtle,
                RoundedCornerShape(8.dp),
              )
              .clickable(role = Role.Button, onClick = onOpenBookmarks)
              .semantics {
                role = Role.Button
                contentDescription = "Saved bookmarks ($bookmarkCount)"
              }
              .testTag("chat_bookmarks_button"),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector =
              if (bookmarkCount > 0) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
            contentDescription = null,
            tint = if (bookmarkCount > 0) glass.accentCyan else glass.textSecondary,
            modifier = Modifier.size(13.dp),
          )
        }

        GlassIconButton(
          icon = Icons.Outlined.Menu,
          contentDescription = stringResource(R.string.home_menu_desc),
          onClick = onOpenMenu,
          modifier = Modifier.testTag("chat_menu_button"),
        )
      }
    }

    Box(
      modifier =
        Modifier.fillMaxWidth()
          .height(1.dp)
          .background(brush = bottomBorderBrush)
    )
  }
}

/**
 * Polished Empty Conversation State with official Astryn AI emblem, radial cyan/violet halo,
 * and quick-tap starter prompt pills.
 */
@Composable
private fun ChatEmptyConversationState(
  onSelectQuickPrompt: (String) -> Unit,
) {
  val glass = AppThemeTokens.glass
  val quickPrompts =
    remember {
      listOf(
        "Explain quantum computing in simple terms",
        "Write a clean Kotlin Coroutine retry helper",
        "Summarize key product launch priorities",
      )
    }

  Column(
    modifier =
      Modifier.fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = AppSpacing.xl, vertical = AppSpacing.sm)
        .testTag("chat_empty_state"),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier =
        Modifier.size(80.dp)
          .drawWithCache {
            val haloBrush =
              Brush.radialGradient(
                colors =
                  listOf(
                    Color(0xFF00F2FE).copy(alpha = if (glass.isDark) 0.24f else 0.14f),
                    Color(0xFF8B5CF6).copy(alpha = if (glass.isDark) 0.16f else 0.08f),
                    Color.Transparent,
                  ),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = (size.minDimension / 2f).coerceAtLeast(1f),
              )
            onDrawBehind {
              if (!glass.reduceTransparency) {
                drawCircle(brush = haloBrush)
              }
            }
          },
    ) {
      AstrynLogoEmblem(
        size = 60.dp,
        contentDescription = "Astryn AI emblem",
        modifier = Modifier.testTag("chat_empty_brand_logo"),
      )
    }

    Spacer(modifier = Modifier.height(6.dp))

    Row(
      modifier =
        Modifier.clip(AppRadius.shapePill)
          .background(glass.surfaceStandard.copy(alpha = 0.75f))
          .border(AppBorder.hairline, glass.accentCyan.copy(alpha = 0.45f), AppRadius.shapePill)
          .padding(horizontal = 12.dp, vertical = 3.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(
        imageVector = Icons.Outlined.AutoAwesome,
        contentDescription = null,
        tint = glass.accentCyan,
        modifier = Modifier.size(13.dp),
      )
      Text(
        text = "ASTRYN AI • READY",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = glass.accentCyan,
        letterSpacing = 1.1.sp,
      )
    }

    Spacer(modifier = Modifier.height(AppSpacing.xs))

    Text(
      text = stringResource(R.string.chat_empty_title),
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
      color = glass.textPrimary,
      textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.height(4.dp))

    Text(
      text = stringResource(R.string.chat_empty_subtitle),
      style = MaterialTheme.typography.bodyMedium,
      color = glass.textSecondary,
      textAlign = TextAlign.Center,
      modifier = Modifier.widthIn(max = 420.dp),
    )

    Spacer(modifier = Modifier.height(AppSpacing.sm))

    Column(
      modifier = Modifier.widthIn(max = 420.dp),
      verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      quickPrompts.forEachIndexed { idx, promptText ->
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clip(AppRadius.shapePill)
              .background(glass.surfaceStandard.copy(alpha = 0.72f))
              .border(
                width = AppBorder.hairline,
                brush =
                  Brush.horizontalGradient(
                    listOf(
                      glass.accentCyan.copy(alpha = 0.38f),
                      glass.accentSecondary.copy(alpha = 0.32f),
                    )
                  ),
                shape = AppRadius.shapePill,
              )
              .clickable { onSelectQuickPrompt(promptText) }
              .padding(horizontal = AppSpacing.md, vertical = 10.dp)
              .testTag("chat_empty_suggestion_$idx"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
          Icon(
            imageVector = Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = glass.accentCyan,
            modifier = Modifier.size(15.dp),
          )
          Text(
            text = promptText,
            style = MaterialTheme.typography.bodySmall,
            color = glass.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
        }
      }
    }
  }
}

/**
 * Fixed Bottom Multiline Composer Dock matching the locked Astryn AI reference:
 * Frosted dark navy dock with top specular highlight, pending Image/PDF attachment chips,
 * compact + (Plus) button for Image & PDF upload, pill-shaped glass input field (always enabled for continuous typing),
 * and glowing cyan-to-violet rounded Send / Stop action button.
 */
@Composable
private fun ChatComposerBar(
  inputText: String,
  pendingAttachments: List<ChatAttachment>,
  isProcessingAttachment: Boolean,
  isGenerating: Boolean,
  isListeningVoice: Boolean,
  onInputChanged: (String) -> Unit,
  onPickAttachments: () -> Unit,
  onCaptureCameraPhoto: () -> Unit,
  onOpenPromptTemplates: () -> Unit,
  onOpenQuiz: (() -> Unit)? = null,
  onRemoveAttachment: (String) -> Unit,
  onToggleVoiceInput: () -> Unit,
  onSendMessage: () -> Unit,
  onStopGeneration: () -> Unit,
) {
  val glass = AppThemeTokens.glass
  val canSend =
    (inputText.isNotBlank() || pendingAttachments.isNotEmpty()) &&
      !isGenerating &&
      !isProcessingAttachment

  val dockTopBorderBrush =
    remember(glass) {
      Brush.horizontalGradient(
        colors =
          listOf(
            Color.Transparent,
            glass.accentCyan.copy(alpha = 0.42f),
            glass.accentSecondary.copy(alpha = 0.42f),
            Color.Transparent,
          )
      )
    }

  val dockSurfaceBrush =
    remember(glass) {
      if (glass.reduceTransparency) {
        Brush.verticalGradient(listOf(glass.surfaceSolidFallback, glass.surfaceSolidFallback))
      } else if (glass.isDark) {
        Brush.verticalGradient(
          colors =
            listOf(
              Color(0xE60B1226),
              Color(0xF5070B18),
            )
        )
      } else {
        glass.glassSurfaceBrush(elevated = true)
      }
    }

  Column(
    modifier =
      Modifier.fillMaxWidth()
        .background(brush = dockSurfaceBrush)
        .testTag("chat_composer_surface"),
  ) {
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .height(1.dp)
          .background(brush = dockTopBorderBrush)
    )

    // Pending Attachments Preview Strip (Images & PDFs)
    if (pendingAttachments.isNotEmpty() || isProcessingAttachment) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.screenHorizontal, vertical = 6.dp)
            .testTag("chat_pending_attachments_row"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        pendingAttachments.forEach { attachment ->
          PendingAttachmentPreviewChip(
            attachment = attachment,
            onRemove = { onRemoveAttachment(attachment.id) },
          )
        }

        if (isProcessingAttachment) {
          Row(
            modifier =
              Modifier.clip(RoundedCornerShape(12.dp))
                .background(glass.surfaceStandard.copy(alpha = 0.85f))
                .border(AppBorder.hairline, glass.accentCyan, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("chat_attachment_processing_chip"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            CircularProgressIndicator(
              modifier = Modifier.size(14.dp),
              color = glass.accentCyan,
              strokeWidth = 1.6.dp,
            )
            Text(
              text = "Processing file…",
              style = MaterialTheme.typography.labelSmall,
              color = glass.textPrimary,
            )
          }
        }
      }
    }

    var showPlusToolsMenu by remember { mutableStateOf(false) }
    val compactIconShape = RoundedCornerShape(10.dp)
    val hasAttachments = pendingAttachments.isNotEmpty()

    // Expandable Plus (+) Tools Tray: Files/PDF, Camera Scan, and Prompt Templates
    if (showPlusToolsMenu) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.screenHorizontal, vertical = 6.dp)
            .testTag("chat_plus_tools_menu"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        // 1. Upload Image or PDF Document
        Row(
          modifier =
            Modifier.clip(RoundedCornerShape(10.dp))
              .background(glass.surfaceStandard.copy(alpha = 0.85f))
              .border(AppBorder.hairline, glass.accentCyan.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
              .clickable(
                enabled = !isProcessingAttachment,
                role = Role.Button,
                onClick = {
                  showPlusToolsMenu = false
                  onPickAttachments()
                },
              )
              .semantics {
                role = Role.Button
                contentDescription = "Attach image or PDF document"
              }
              .padding(horizontal = 10.dp, vertical = 6.dp)
              .testTag("chat_plus_file_picker_button"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.Description,
            contentDescription = null,
            tint = glass.accentCyan,
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = "Files / PDF",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            fontWeight = FontWeight.SemiBold,
            color = glass.textPrimary,
          )
        }

        // 2. Camera Direct Photo Capture (OCR / Homework / Bill Scanner)
        Row(
          modifier =
            Modifier.clip(RoundedCornerShape(10.dp))
              .background(glass.surfaceStandard.copy(alpha = 0.85f))
              .border(AppBorder.hairline, glass.accentCyan.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
              .clickable(
                enabled = !isProcessingAttachment,
                role = Role.Button,
                onClick = {
                  showPlusToolsMenu = false
                  onCaptureCameraPhoto()
                },
              )
              .semantics {
                role = Role.Button
                contentDescription = "Capture photo with camera"
              }
              .padding(horizontal = 10.dp, vertical = 6.dp)
              .testTag("chat_camera_button"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.PhotoCamera,
            contentDescription = null,
            tint = glass.accentCyan,
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = "Camera",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            fontWeight = FontWeight.SemiBold,
            color = glass.textPrimary,
          )
        }

        // 3. 1-Tap Prompt Templates Library
        Row(
          modifier =
            Modifier.clip(RoundedCornerShape(10.dp))
              .background(glass.surfaceStandard.copy(alpha = 0.85f))
              .border(AppBorder.hairline, glass.accentCyan.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
              .clickable(
                role = Role.Button,
                onClick = {
                  showPlusToolsMenu = false
                  onOpenPromptTemplates()
                },
              )
              .semantics {
                role = Role.Button
                contentDescription = "Open 1-tap prompt templates"
              }
              .padding(horizontal = 10.dp, vertical = 6.dp)
              .testTag("chat_templates_button"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.Bolt,
            contentDescription = null,
            tint = glass.accentCyan,
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = "Templates",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            fontWeight = FontWeight.SemiBold,
            color = glass.textPrimary,
          )
        }

        // 4. Interactive Exam Quiz & Test Generator
        if (onOpenQuiz != null) {
          Row(
            modifier =
              Modifier.clip(RoundedCornerShape(10.dp))
                .background(glass.surfaceStandard.copy(alpha = 0.85f))
                .border(AppBorder.hairline, glass.accentCyan.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                .clickable(
                  role = Role.Button,
                  onClick = {
                    showPlusToolsMenu = false
                    onOpenQuiz()
                  },
                )
                .semantics {
                  role = Role.Button
                  contentDescription = "Open interactive exam quiz and test generator"
                }
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("chat_quiz_button"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.School,
              contentDescription = null,
              tint = glass.accentCyan,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = "Quiz / Test",
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
              fontWeight = FontWeight.SemiBold,
              color = glass.textPrimary,
            )
          }
        }
      }
    }

    Row(
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = AppSpacing.screenHorizontal, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      // Compact Plus (+) Icon Button that opens/closes the attachment & tools menu
      Box(
        modifier =
          Modifier.size(30.dp)
            .clip(compactIconShape)
            .background(
              if (hasAttachments || showPlusToolsMenu) glass.accentPrimary.copy(alpha = 0.22f)
              else glass.surfaceStandard.copy(alpha = 0.78f),
              shape = compactIconShape,
            )
            .border(
              width = if (hasAttachments || showPlusToolsMenu) AppBorder.focused else AppBorder.hairline,
              color = if (hasAttachments || showPlusToolsMenu) glass.accentCyan else glass.borderSpecular,
              shape = compactIconShape,
            )
            .clickable(
              enabled = !isProcessingAttachment,
              role = Role.Button,
              onClick = { showPlusToolsMenu = !showPlusToolsMenu },
            )
            .semantics {
              role = Role.Button
              contentDescription = "Open attachment and tools menu"
            }
            .testTag("chat_attach_button"),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = if (showPlusToolsMenu) Icons.Outlined.Close else Icons.Outlined.Add,
          contentDescription = null,
          tint = glass.accentCyan,
          modifier = Modifier.size(15.dp),
        )
      }

      GlassTextField(
        value = inputText,
        onValueChange = onInputChanged,
        placeholder = stringResource(R.string.chat_composer_placeholder),
        singleLine = false,
        maxLines = 5,
        modifier =
          Modifier.weight(1f)
            .testTag("chat_input_field"),
        // Keep enabled = true during streaming so soft keyboard and rapid typing never freeze
        enabled = true,
      )

      // Compact Mic Icon Button for Voice Input (Speech-to-Text)
      Box(
        modifier =
          Modifier.size(30.dp)
            .clip(compactIconShape)
            .background(
              if (isListeningVoice) Color(0x33F43F5E)
              else glass.surfaceStandard.copy(alpha = 0.78f),
              shape = compactIconShape,
            )
            .border(
              width = if (isListeningVoice) AppBorder.focused else AppBorder.hairline,
              color = if (isListeningVoice) Color(0xFFF43F5E) else glass.borderSpecular,
              shape = compactIconShape,
            )
            .clickable(
              role = Role.Button,
              onClick = onToggleVoiceInput,
            )
            .semantics {
              role = Role.Button
              contentDescription =
                if (isListeningVoice) "Stop voice input" else "Voice input microphone"
            }
            .testTag("chat_mic_button"),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = if (isListeningVoice) Icons.Outlined.GraphicEq else Icons.Outlined.Mic,
          contentDescription = null,
          tint = if (isListeningVoice) Color(0xFFF43F5E) else glass.accentCyan,
          modifier = Modifier.size(15.dp),
        )
      }

      if (isGenerating) {
        // Active Stop Generation Button (cancels live SSE stream)
        val stopButtonShape = RoundedCornerShape(16.dp)
        Box(
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .size(48.dp)
              .clip(stopButtonShape)
              .background(
                brush =
                  Brush.linearGradient(
                    colors =
                      listOf(
                        Color(0xFF7C3AED),
                        Color(0xFF3B82F6),
                      )
                  ),
                shape = stopButtonShape,
              )
              .border(
                width = AppBorder.focused,
                color = glass.accentCyan,
                shape = stopButtonShape,
              )
              .clickable(
                role = Role.Button,
                onClick = onStopGeneration,
              )
              .semantics {
                contentDescription = "Stop generating"
                role = Role.Button
              }
              .testTag("chat_stop_button"),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Outlined.Stop,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
          )
        }
      } else {
        // Gradient Send Prompt Button matching locked reference
        val sendButtonShape = RoundedCornerShape(16.dp)
        val sendBrush =
          remember(canSend, glass) {
            if (canSend) {
              Brush.linearGradient(
                colors =
                  listOf(
                    Color(0xFF00F2FE),
                    Color(0xFF3B82F6),
                    Color(0xFF8B5CF6),
                  )
              )
            } else {
              Brush.linearGradient(
                colors =
                  listOf(
                    glass.surfaceStandard,
                    glass.surfaceSubtle,
                  )
              )
            }
          }

        val sendBorderBrush =
          remember(canSend, glass) {
            if (canSend) {
              Brush.linearGradient(
                colors =
                  listOf(
                    Color(0xFF00F2FE),
                    Color(0xFFC084FC),
                  )
              )
            } else {
              Brush.linearGradient(
                colors =
                  listOf(
                    glass.borderSpecular,
                    glass.borderSubtle,
                  )
              )
            }
          }

        Box(
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .size(48.dp)
              .clip(sendButtonShape)
              .background(brush = sendBrush, shape = sendButtonShape)
              .border(
                BorderStroke(
                  width = if (canSend) AppBorder.focused else AppBorder.hairline,
                  brush = sendBorderBrush,
                ),
                shape = sendButtonShape,
              )
              .clickable(
                enabled = canSend,
                role = Role.Button,
                onClick = onSendMessage,
              )
              .semantics {
                contentDescription = "Send prompt"
                role = Role.Button
              }
              .testTag("chat_send_button"),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.Send,
            contentDescription = null,
            tint = if (canSend) Color.White else glass.textDisabled,
            modifier = Modifier.size(20.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun PendingAttachmentPreviewChip(
  attachment: ChatAttachment,
  onRemove: () -> Unit,
) {
  val glass = AppThemeTokens.glass
  val decodedThumbnail =
    remember(attachment.id, attachment.thumbnailBase64) {
      val b64 = attachment.thumbnailBase64
      if (!b64.isNullOrBlank()) {
        try {
          val bytes = Base64.decode(b64, Base64.DEFAULT)
          BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (_: Exception) {
          null
        }
      } else {
        null
      }
    }

  Row(
    modifier =
      Modifier.clip(RoundedCornerShape(12.dp))
        .background(glass.surfaceElevated)
        .border(AppBorder.hairline, glass.accentCyan.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
        .padding(horizontal = 10.dp, vertical = 6.dp)
        .testTag("chat_pending_attachment_${attachment.id}"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (decodedThumbnail != null) {
      Image(
        bitmap = decodedThumbnail,
        contentDescription = attachment.fileName,
        contentScale = ContentScale.Crop,
        modifier =
          Modifier.size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(AppBorder.hairline, glass.borderSubtle, RoundedCornerShape(8.dp)),
      )
    } else {
      Box(
        modifier =
          Modifier.size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(glass.accentPrimary.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector =
            if (attachment.kind == AttachmentKind.PDF) Icons.Outlined.Description
            else Icons.Outlined.Image,
          contentDescription = null,
          tint = glass.accentCyan,
          modifier = Modifier.size(18.dp),
        )
      }
    }

    Column(modifier = Modifier.widthIn(max = 170.dp)) {
      Text(
        text = attachment.fileName,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = glass.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      val metaLabel =
        buildString {
          append(attachment.formattedFileSize)
          if (attachment.kind == AttachmentKind.PDF && (attachment.pageCount ?: 0) > 0) {
            append(" • ${attachment.pageCount}p")
          }
        }
      Text(
        text = metaLabel,
        style = MaterialTheme.typography.labelSmall,
        color = glass.accentCyan,
        maxLines = 1,
      )
    }

    Box(
      modifier =
        Modifier.size(26.dp)
          .clip(CircleShape)
          .background(glass.surfaceStandard)
          .semantics {
            role = Role.Button
            contentDescription = "Remove ${attachment.fileName}"
          }
          .clickable(onClick = onRemove)
          .testTag("chat_remove_attachment_${attachment.id}"),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = Icons.Outlined.Close,
        contentDescription = null,
        tint = glass.textSecondary,
        modifier = Modifier.size(14.dp),
      )
    }
  }
}

private val UserBubbleShape =
  RoundedCornerShape(
    topStart = 22.dp,
    topEnd = 22.dp,
    bottomStart = 22.dp,
    bottomEnd = 6.dp,
  )

private val AssistantBubbleShape =
  RoundedCornerShape(
    topStart = 6.dp,
    topEnd = 22.dp,
    bottomStart = 22.dp,
    bottomEnd = 22.dp,
  )

/**
 * Individual message bubble item matching the locked Astryn AI design reference:
 * - User messages right-aligned in a rich Electric Blue-to-Royal Violet gradient bubble
 *   with a luminous cyan/violet border and crisp white typography.
 * - Assistant messages left-aligned with the Astryn AI emblem avatar, model header badge,
 *   and a deep navy frosted glass panel with subtle cyan/blue border.
 * - Supports attached Image/PDF cards, verified Google Search source chips, and Copy + Share actions.
 * - Error and Interrupted messages use distinct semantic borders, clear explanations,
 *   and prominent Retry affordances so errors never masquerade as normal AI replies.
 */
@Composable
private fun ChatMessageItem(
  message: ChatMessage,
  isLastMessage: Boolean,
  showActions: Boolean,
  isGenerating: Boolean,
  isSpeaking: Boolean,
  speechRateLabel: String,
  isSearchMatch: Boolean = false,
  onToggleActions: () -> Unit,
  onCopyMessage: () -> Unit,
  onShareMessage: () -> Unit,
  onToggleLike: () -> Unit,
  onToggleBookmark: () -> Unit,
  onToggleSpeak: () -> Unit,
  onCycleSpeechSpeed: () -> Unit,
  onModifyResponse: (String) -> Unit,
  onCompareResponse: () -> Unit = {},
  onTranslateResponse: (String) -> Unit,
  onExportResponse: (Boolean) -> Unit,
  onSelectFollowUp: (String) -> Unit,
  onCopyCode: (String) -> Unit,
  onDownloadCode: (String, String) -> Unit,
  onRunCode: ((String, String) -> Unit)? = null,
  onGenerateQuiz: ((String) -> Unit)? = null,
  onRegenerate: () -> Unit,
  onEditMessage: () -> Unit,
  onQuoteLine: (String) -> Unit = {},
  onActionFeedback: (String) -> Unit = {},
  onConfigureApiKey: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val glass = AppThemeTokens.glass
  val uriHandler = LocalUriHandler.current
  val isUser = message.role == MessageRole.USER
  var copiedRecently by remember { mutableStateOf(false) }
  var showMoreOptions by rememberSaveable { mutableStateOf(false) }
  var activeSubMenu by rememberSaveable { mutableStateOf<String?>(null) }
  var responseViewMode by rememberSaveable(message.id) { mutableStateOf(AiResponseViewMode.FULL_DETAIL) }
  var showQuoteSelector by rememberSaveable(message.id) { mutableStateOf(false) }

  LaunchedEffect(copiedRecently) {
    if (copiedRecently) {
      delay(1800L)
      copiedRecently = false
    }
  }

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(
          start = if (isUser) 48.dp else 0.dp,
          end = if (isUser) 0.dp else 4.dp,
        )
        .testTag("chat_message_${message.id}"),
    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
  ) {
    val isFramelessAiResponse =
      !isUser && message.status != MessageStatus.ERROR && !isSearchMatch

    // Bubble Fill Brush (only used for User bubble, Error state, or Search match)
    val bubbleBrush =
      remember(isUser, message.status, isFramelessAiResponse, glass) {
        when {
          isFramelessAiResponse ->
            Brush.linearGradient(colors = listOf(Color.Transparent, Color.Transparent))
          message.status == MessageStatus.ERROR ->
            Brush.linearGradient(
              colors =
                if (glass.isDark) {
                  listOf(Color(0xE622111D), Color(0xE618122B))
                } else {
                  listOf(glass.statusErrorContainer, glass.surfaceElevated)
                }
            )
          isUser ->
            if (glass.reduceTransparency) {
              Brush.linearGradient(
                colors = listOf(Color(0xFF2563EB), Color(0xFF6D28D9))
              )
            } else {
              Brush.linearGradient(
                colors =
                  listOf(
                    Color(0xFF1D4ED8),
                    Color(0xFF3B82F6),
                    Color(0xFF6D28D9),
                    Color(0xFF7C3AED),
                  )
              )
            }
          else ->
            if (glass.reduceTransparency) {
              Brush.verticalGradient(
                colors = listOf(glass.surfaceSolidFallback, glass.surfaceSolidFallback)
              )
            } else if (glass.isDark) {
              Brush.linearGradient(
                colors =
                  listOf(
                    Color(0xE6111A36),
                    Color(0xD90C1328),
                  )
              )
            } else {
              Brush.linearGradient(
                colors = listOf(glass.surfaceElevated, glass.surfaceStandard)
              )
            }
        }
      }

    val bubbleBorderBrush =
      remember(isUser, message.status, glass) {
        when {
          message.status == MessageStatus.ERROR ->
            Brush.linearGradient(
              colors = listOf(glass.statusError, glass.statusError.copy(alpha = 0.55f))
            )
          message.status == MessageStatus.INTERRUPTED ->
            Brush.linearGradient(
              colors = listOf(glass.statusWarning, glass.statusWarning.copy(alpha = 0.55f))
            )
          isUser ->
            Brush.linearGradient(
              colors =
                listOf(
                  Color(0xFF00F2FE).copy(alpha = 0.80f),
                  Color(0xFF60A5FA).copy(alpha = 0.65f),
                  Color(0xFFC084FC).copy(alpha = 0.75f),
                )
            )
          else ->
            Brush.linearGradient(
              colors =
                if (glass.isDark) {
                  listOf(
                    Color(0xFF00F2FE).copy(alpha = 0.42f),
                    Color(0xFF3B82F6).copy(alpha = 0.28f),
                    Color(0xFF8B5CF6).copy(alpha = 0.30f),
                  )
                } else {
                  listOf(glass.borderAccent, glass.borderSpecular)
                }
            )
        }
      }

    val bubbleShape = if (isUser) UserBubbleShape else AssistantBubbleShape
    val userTextColor = if (isUser) Color(0xFFF8FAFC) else null

    val formattedTime =
      remember(message.timestamp) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestamp))
      }

    Column(
      modifier =
        if (isUser) Modifier.widthIn(max = 520.dp)
        else Modifier.fillMaxWidth(),
      horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
      // Clean ChatGPT-Style Header Row (Avatar + "Astryn" + quiet time for AI, or quiet time for User)
      Row(
        modifier =
          Modifier.padding(bottom = if (isUser) 3.dp else 6.dp, start = 2.dp, end = 2.dp)
            .testTag("chat_message_time_${message.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        if (!isUser) {
          AstrynLogoEmblem(
            size = 20.dp,
            contentDescription = "Astryn AI Assistant",
            modifier = Modifier.testTag("chat_assistant_avatar_${message.id}"),
          )
          Text(
            text = "Astryn",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = glass.textPrimary,
          )
        }
        Text(
          text = formattedTime,
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
          fontWeight = FontWeight.Normal,
          color = glass.textMuted,
        )
        if (isSearchMatch) {
          Text(
            text = "Match",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
            fontWeight = FontWeight.Bold,
            color = glass.accentCyan,
            modifier =
              Modifier.clip(AppRadius.shapePill)
                .background(glass.accentCyan.copy(alpha = 0.18f))
                .padding(horizontal = 5.dp, vertical = 1.dp)
                .testTag("chat_message_search_match_${message.id}"),
          )
        }
      }

      val containerModifier =
        if (isFramelessAiResponse) {
          // ChatGPT-Style Frameless Open-Canvas Container (NO box, NO border)
          Modifier.fillMaxWidth()
            .clickable(onClick = onToggleActions)
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .testTag("chat_assistant_bubble_${message.id}")
        } else {
          // User Pill Bubble or Error/Search Highlight Card
          Modifier.clip(bubbleShape)
            .background(brush = bubbleBrush, shape = bubbleShape)
            .border(
              BorderStroke(
                width =
                  if (isSearchMatch || isUser || message.status == MessageStatus.ERROR)
                    AppBorder.focused
                  else AppBorder.hairline,
                brush = bubbleBorderBrush,
              ),
              shape = bubbleShape,
            )
            .clickable(onClick = onToggleActions)
            .padding(horizontal = 16.dp, vertical = 11.dp)
            .testTag(
              if (isUser) "chat_user_bubble_${message.id}"
              else "chat_assistant_bubble_${message.id}"
            )
        }

      Box(modifier = containerModifier) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
          if (message.status == MessageStatus.ERROR) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
            ) {
              Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = "Error",
                tint = glass.statusError,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = message.errorTitle ?: stringResource(R.string.chat_error_badge),
                style = MaterialTheme.typography.labelSmall,
                color = glass.statusError,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("chat_error_title_${message.id}"),
              )
            }
          }

          // Attached Images / PDFs
          if (message.attachments.isNotEmpty()) {
            Column(
              modifier =
                Modifier.padding(bottom = 4.dp)
                  .testTag("chat_message_attachments_${message.id}"),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              message.attachments.forEach { attachment ->
                MessageAttachmentCard(attachment = attachment)
              }
            }
          }

          if (message.content.isNotBlank()) {
            val isCompletedAiMessage =
              !isUser &&
                message.status != MessageStatus.STREAMING &&
                message.status != MessageStatus.ERROR

            // Only show the view switcher tabs when the user opens ••• More menu or selects a custom view mode
            if (isCompletedAiMessage && (showMoreOptions || responseViewMode != AiResponseViewMode.FULL_DETAIL)) {
              AiReasoningChainBox(
                messageId = message.id,
                content = message.content,
              )
              if (message.content.length >= 60) {
                AiResponseModeTabs(
                  messageId = message.id,
                  selectedMode = responseViewMode,
                  onSelectMode = { responseViewMode = it },
                )
              }
            }

            when {
              !isCompletedAiMessage || responseViewMode == AiResponseViewMode.FULL_DETAIL -> {
                MarkdownMessageContent(
                  messageId = message.id,
                  content = message.content,
                  onCopyCode = onCopyCode,
                  textColorOverride = userTextColor,
                  onDownloadCode = onDownloadCode,
                  onRunCode = onRunCode,
                )
              }
              responseViewMode == AiResponseViewMode.QUICK_SUMMARY ||
                responseViewMode == AiResponseViewMode.KEY_POINTS -> {
                AiInstantSummaryOrPointsView(
                  messageId = message.id,
                  content = message.content,
                  mode = responseViewMode,
                  onQuotePoint = { quotedLine ->
                    onQuoteLine(quotedLine)
                  },
                )
              }
              responseViewMode == AiResponseViewMode.FLOWCHART -> {
                InteractiveFlowchartCard(
                  messageId = message.id,
                  content = message.content,
                  onAskAboutStep = { stepFollowUp ->
                    onSelectFollowUp(stepFollowUp)
                  },
                )
              }
            }
          }

            if (message.status == MessageStatus.ERROR && !message.errorHint.isNullOrBlank()) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier =
                  Modifier.padding(top = 2.dp)
                    .testTag("chat_error_hint_${message.id}"),
              ) {
                Icon(
                  imageVector = Icons.Outlined.Info,
                  contentDescription = null,
                  tint = glass.accentCyan,
                  modifier = Modifier.size(14.dp),
                )
                Text(
                  text = message.errorHint,
                  style = MaterialTheme.typography.labelSmall,
                  color = glass.textSecondary,
                )
              }
            }

            if (message.status == MessageStatus.STREAMING) {
              val isInitialThinking = message.content.isBlank()
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                  Modifier.padding(vertical = if (isInitialThinking) 4.dp else 2.dp)
                    .semantics {
                      liveRegion = LiveRegionMode.Polite
                      contentDescription =
                        if (isInitialThinking) "Assistant is thinking"
                        else "Assistant is streaming response"
                    }
                    .testTag("chat_streaming_indicator"),
              ) {
                CircularProgressIndicator(
                  modifier = Modifier.size(if (isInitialThinking) 15.dp else 12.dp),
                  color = glass.accentCyan,
                  strokeWidth = 1.8.dp,
                )
                Text(
                  text =
                    if (isInitialThinking) "Astryn AI is thinking…"
                    else stringResource(R.string.chat_streaming_status),
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = if (isInitialThinking) FontWeight.SemiBold else FontWeight.Medium,
                  color = glass.accentCyan,
                )
              }
            }

            if (message.status == MessageStatus.INTERRUPTED) {
              Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                  modifier = Modifier.testTag("chat_interrupted_badge"),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = "Interrupted response",
                    tint = glass.statusWarning,
                    modifier = Modifier.size(14.dp),
                  )
                  Text(
                    text =
                      message.errorTitle?.let { "$it • Incomplete response" }
                        ?: stringResource(R.string.chat_interrupted_status),
                    style = MaterialTheme.typography.labelSmall,
                    color = glass.statusWarning,
                    fontWeight = FontWeight.SemiBold,
                  )
                }
                if (!message.errorMessage.isNullOrBlank()) {
                  Text(
                    text = message.errorMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = glass.textSecondary,
                  )
                }
              }
            }

            if (message.status == MessageStatus.ERROR && message.isRetryable) {
              GlassButton(
                text = stringResource(R.string.chat_retry_button),
                leadingIcon = Icons.Outlined.Refresh,
                variant = GlassButtonVariant.SECONDARY,
                enabled = !isGenerating,
                onClick = onRegenerate,
                modifier = Modifier.padding(top = 4.dp).testTag("chat_retry_button"),
              )
            }

            val isCredentialError =
              message.status == MessageStatus.ERROR &&
                (message.errorTitle?.contains("API Key", ignoreCase = true) == true ||
                  message.errorTitle?.contains("Authentication", ignoreCase = true) == true ||
                  message.errorTitle?.contains("Permission", ignoreCase = true) == true ||
                  message.errorHint?.contains("AI Provider & API Key", ignoreCase = true) == true)

            if (isCredentialError && onConfigureApiKey != null) {
              GlassButton(
                text = "Configure API Key",
                leadingIcon = Icons.Outlined.Key,
                variant = GlassButtonVariant.PRIMARY,
                enabled = !isGenerating,
                onClick = onConfigureApiKey,
                modifier = Modifier.padding(top = 4.dp).testTag("chat_error_configure_key_button"),
              )
            }
          }
        }

        // Verified Google Search Grounding Citations (rendered below the bubble for independent click targets)
        if (!isUser && message.citations.isNotEmpty()) {
          MessageCitationsSection(
            messageId = message.id,
            citations = message.citations,
            onOpenUrl = { rawUrl ->
              if (SafeMarkdownParser.isSafeExternalUrl(rawUrl)) {
                try {
                  uriHandler.openUri(rawUrl)
                } catch (_: Exception) {}
              }
            },
          )
        }

        // Smart 1-Tap Context Action Cards + Interactive Quote & Ask Selector
        // Shown cleanly only when the user opens ••• More options or Quote Selector so main chat stays uncluttered
        if (!isUser &&
          message.status != MessageStatus.STREAMING &&
          message.status != MessageStatus.ERROR &&
          message.content.isNotBlank() &&
          (showMoreOptions || showQuoteSelector)
        ) {
          SmartContextActionCardsRow(
            messageId = message.id,
            content = message.content,
            onRunCode = onRunCode,
            onToggleQuoteSelector = { showQuoteSelector = !showQuoteSelector },
            isQuoteSelectorOpen = showQuoteSelector,
            onActionFeedback = onActionFeedback,
          )

          AnimatedVisibility(visible = showQuoteSelector) {
            InteractiveQuoteSelectorPanel(
              messageId = message.id,
              content = message.content,
              onSelectQuoteLine = { selectedSentence ->
                showQuoteSelector = false
                onQuoteLine(selectedSentence)
              },
              onClose = { showQuoteSelector = false },
            )
          }
        }

        // -----------------------------------------------------------------
        // ChatGPT-Style Minimal Quiet Action Row Below Response:
        // Only small, clean borderless icons for Copy, Speak, Like, Regenerate, and •••
        // -----------------------------------------------------------------
        if (message.status != MessageStatus.STREAMING && message.content.isNotBlank()) {
          if (showActions || !isUser) {
            Row(
              modifier =
                Modifier.padding(top = 4.dp)
                  .testTag("message_actions_row_${message.id}"),
              horizontalArrangement = Arrangement.spacedBy(2.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              // 1. Copy Action (Both User & Assistant)
              MessageMinimalIconAction(
                icon = if (copiedRecently) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                contentDescription = "Copy message text",
                onClick = {
                  onCopyMessage()
                  copiedRecently = true
                },
                isHighlighted = copiedRecently,
                testTag = "message_copy_button_${message.id}",
              )

              if (!isUser && message.status != MessageStatus.ERROR) {
                // 2. Speak / Read Aloud AI Response Action (TTS)
                MessageMinimalIconAction(
                  icon =
                    if (isSpeaking) Icons.Outlined.Stop
                    else Icons.AutoMirrored.Outlined.VolumeUp,
                  contentDescription =
                    if (isSpeaking) "Stop reading response aloud"
                    else "Read AI response aloud",
                  onClick = onToggleSpeak,
                  isHighlighted = isSpeaking,
                  testTag = "message_speak_button_${message.id}",
                )

                // 3. Like AI Response Action
                MessageMinimalIconAction(
                  icon = if (message.isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                  contentDescription =
                    if (message.isLiked) "Unlike AI response" else "Like AI response",
                  onClick = onToggleLike,
                  isHighlighted = message.isLiked,
                  testTag = "message_like_button_${message.id}",
                )
              }

              if (isUser) {
                // Edit & Resend Action (User Messages)
                MessageMinimalIconAction(
                  icon = Icons.Outlined.Edit,
                  contentDescription = "Edit and resend message",
                  enabled = !isGenerating,
                  onClick = onEditMessage,
                  testTag = "message_edit_button_${message.id}",
                )
              } else if (message.status != MessageStatus.ERROR || message.isRetryable) {
                // 4. Regenerate Action (Assistant Messages)
                MessageMinimalIconAction(
                  icon = Icons.Outlined.Refresh,
                  contentDescription = "Regenerate assistant response",
                  enabled = !isGenerating,
                  onClick = onRegenerate,
                  testTag = "message_regenerate_button_${message.id}",
                )
              }

              if (!isUser && message.status != MessageStatus.ERROR) {
                // 5. Three-Dot (•••) More Actions Menu Toggle Button
                MessageMinimalIconAction(
                  icon = if (showMoreOptions) Icons.Outlined.Close else Icons.Outlined.MoreHoriz,
                  contentDescription =
                    if (showMoreOptions) "Hide more response actions"
                    else "More response actions (Share, Save, Translate, Modify, Export)",
                  onClick = {
                    val next = !showMoreOptions
                    showMoreOptions = next
                    if (!next) {
                      activeSubMenu = null
                    }
                  },
                  isHighlighted = showMoreOptions || activeSubMenu != null,
                  testTag = "message_more_options_button_${message.id}",
                )
              }
            }

            // -----------------------------------------------------------------
            // 3-Dot (•••) Secondary Actions Menu for AI Responses:
            // Share, Save (Bookmark), Speed (1x/1.25x/1.5x), Modify, Compare,
            // Translate, Export, and Quiz
            // -----------------------------------------------------------------
            if (!isUser && message.status != MessageStatus.ERROR && showMoreOptions) {
              @OptIn(ExperimentalLayoutApi::class)
              FlowRow(
                modifier =
                  Modifier.padding(top = 5.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(glass.surfaceElevated.copy(alpha = 0.88f))
                    .border(
                      AppBorder.hairline,
                      glass.accentCyan.copy(alpha = 0.35f),
                      RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag("message_more_options_menu_${message.id}"),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
              ) {
                // Share AI Response Action
                MessageActionChip(
                  icon = Icons.Outlined.Share,
                  label = "Share",
                  contentDescription = "Share AI response",
                  onClick = onShareMessage,
                  testTag = "message_share_button_${message.id}",
                )

                // Bookmark / Save AI Response Action
                MessageActionChip(
                  icon =
                    if (message.isBookmarked) Icons.Filled.Bookmark
                    else Icons.Outlined.BookmarkBorder,
                  label = if (message.isBookmarked) "Saved" else "Save",
                  contentDescription =
                    if (message.isBookmarked) "Remove response from bookmarks"
                    else "Save response to bookmarks",
                  onClick = onToggleBookmark,
                  isHighlighted = message.isBookmarked,
                  testTag = "message_bookmark_button_${message.id}",
                )

                // Voice Speed Control Chip (1x -> 1.25x -> 1.5x -> 0.85x)
                MessageActionChip(
                  icon = Icons.Outlined.Speed,
                  label = speechRateLabel,
                  contentDescription = "Change voice playback speed ($speechRateLabel)",
                  onClick = onCycleSpeechSpeed,
                  isHighlighted = isSpeaking && speechRateLabel != "1x",
                  testTag = "message_speech_speed_button_${message.id}",
                )

                // Quick Modify / Tone & Length Toggle Chip
                MessageActionChip(
                  icon = Icons.Outlined.Tune,
                  label = "Modify",
                  contentDescription = "Modify response length or tone",
                  enabled = !isGenerating,
                  onClick = {
                    activeSubMenu = if (activeSubMenu == "MODIFY") null else "MODIFY"
                  },
                  isHighlighted = activeSubMenu == "MODIFY",
                  testTag = "message_modify_button_${message.id}",
                )

                // Compare 2 Alternative Draft Styles Chip
                MessageActionChip(
                  icon = Icons.AutoMirrored.Outlined.CompareArrows,
                  label = "Compare",
                  contentDescription = "Compare 2 alternative response styles",
                  enabled = !isGenerating,
                  onClick = onCompareResponse,
                  testTag = "message_compare_button_${message.id}",
                )

                // Instant Translate Toggle Chip (Hindi / English / Hinglish)
                MessageActionChip(
                  icon = Icons.Outlined.Translate,
                  label = "Translate",
                  contentDescription = "Translate response to Hindi, English, or Hinglish",
                  enabled = !isGenerating,
                  onClick = {
                    activeSubMenu = if (activeSubMenu == "TRANSLATE") null else "TRANSLATE"
                  },
                  isHighlighted = activeSubMenu == "TRANSLATE",
                  testTag = "message_translate_button_${message.id}",
                )

                // Export / Download as PDF or TXT Toggle Chip
                MessageActionChip(
                  icon = Icons.Outlined.Download,
                  label = "Export",
                  contentDescription = "Download response as PDF or TXT file",
                  onClick = {
                    activeSubMenu = if (activeSubMenu == "EXPORT") null else "EXPORT"
                  },
                  isHighlighted = activeSubMenu == "EXPORT",
                  testTag = "message_export_button_${message.id}",
                )

                // Quiz / Interactive Test Generator from this Message
                if (onGenerateQuiz != null) {
                  MessageActionChip(
                    icon = Icons.Outlined.School,
                    label = "Quiz",
                    contentDescription = "Generate interactive quiz from this answer",
                    enabled = !isGenerating,
                    onClick = {
                      onGenerateQuiz(message.content.take(600))
                    },
                    testTag = "message_quiz_button_${message.id}",
                  )
                }
              }
            }

            // Expandable Compact Sub-Row for Quick Modify (Desi/Funny, 5-Saal Baccha, Pro Expert, Shorter, Longer, Simplify)
            if (!isUser && activeSubMenu == "MODIFY") {
              @OptIn(ExperimentalLayoutApi::class)
              FlowRow(
                modifier =
                  Modifier.padding(top = 4.dp)
                    .testTag("message_modify_menu_${message.id}"),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                MessageActionChip(
                  icon = Icons.Outlined.AutoAwesome,
                  label = "😂 Desi / Funny",
                  contentDescription = "Explain in fun Desi Hinglish style",
                  enabled = !isGenerating,
                  onClick = {
                    activeSubMenu = null
                    onModifyResponse("DESI_FUNNY")
                  },
                  isHighlighted = true,
                  testTag = "message_modify_desi_${message.id}",
                )
                MessageActionChip(
                  icon = Icons.Outlined.Lightbulb,
                  label = "👶 5-Saal Baccha",
                  contentDescription = "Explain like I am 5 years old",
                  enabled = !isGenerating,
                  onClick = {
                    activeSubMenu = null
                    onModifyResponse("ELI5_BACCHA")
                  },
                  isHighlighted = true,
                  testTag = "message_modify_eli5_${message.id}",
                )
                MessageActionChip(
                  icon = Icons.Outlined.Bolt,
                  label = "🔥 Pro Expert",
                  contentDescription = "Upgrade to Pro Expert with comparison table and verdict",
                  enabled = !isGenerating,
                  onClick = {
                    activeSubMenu = null
                    onModifyResponse("PRO_EXPERT")
                  },
                  isHighlighted = true,
                  testTag = "message_modify_pro_${message.id}",
                )
                MessageActionChip(
                  icon = Icons.Outlined.Compress,
                  label = "⚡ Shorter",
                  contentDescription = "Make response shorter and concise",
                  enabled = !isGenerating,
                  onClick = {
                    activeSubMenu = null
                    onModifyResponse("SHORTER")
                  },
                  isHighlighted = true,
                  testTag = "message_modify_shorter_${message.id}",
                )
                MessageActionChip(
                  icon = Icons.Outlined.Expand,
                  label = "Longer",
                  contentDescription = "Expand response in detail",
                  enabled = !isGenerating,
                  onClick = {
                    activeSubMenu = null
                    onModifyResponse("LONGER")
                  },
                  isHighlighted = true,
                  testTag = "message_modify_longer_${message.id}",
                )
                MessageActionChip(
                  icon = Icons.Outlined.Lightbulb,
                  label = "Simplify",
                  contentDescription = "Explain in simple language",
                  enabled = !isGenerating,
                  onClick = {
                    activeSubMenu = null
                    onModifyResponse("SIMPLIFY")
                  },
                  isHighlighted = true,
                  testTag = "message_modify_simplify_${message.id}",
                )
              }
            }

            // Expandable Compact Sub-Row for Instant Translate (Hindi / English / Hinglish)
            if (!isUser && activeSubMenu == "TRANSLATE") {
              @OptIn(ExperimentalLayoutApi::class)
              FlowRow(
                modifier =
                  Modifier.padding(top = 4.dp)
                    .testTag("message_translate_menu_${message.id}"),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                MessageActionChip(
                  icon = Icons.Outlined.Translate,
                  label = "हिन्दी",
                  contentDescription = "Translate response to Hindi",
                  enabled = !isGenerating,
                  onClick = {
                    activeSubMenu = null
                    onTranslateResponse("Hindi")
                  },
                  isHighlighted = true,
                  testTag = "message_translate_hindi_${message.id}",
                )
                MessageActionChip(
                  icon = Icons.Outlined.Translate,
                  label = "English",
                  contentDescription = "Translate response to English",
                  enabled = !isGenerating,
                  onClick = {
                    activeSubMenu = null
                    onTranslateResponse("English")
                  },
                  isHighlighted = true,
                  testTag = "message_translate_english_${message.id}",
                )
                MessageActionChip(
                  icon = Icons.Outlined.Translate,
                  label = "Hinglish",
                  contentDescription = "Translate response to Hinglish",
                  enabled = !isGenerating,
                  onClick = {
                    activeSubMenu = null
                    onTranslateResponse("Hinglish")
                  },
                  isHighlighted = true,
                  testTag = "message_translate_hinglish_${message.id}",
                )
              }
            }

            // Expandable Compact Sub-Row for Export (PDF / TXT)
            if (!isUser && activeSubMenu == "EXPORT") {
              @OptIn(ExperimentalLayoutApi::class)
              FlowRow(
                modifier =
                  Modifier.padding(top = 4.dp)
                    .testTag("message_export_menu_${message.id}"),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                MessageActionChip(
                  icon = Icons.Outlined.PictureAsPdf,
                  label = "PDF",
                  contentDescription = "Download response as PDF file",
                  onClick = {
                    activeSubMenu = null
                    onExportResponse(true)
                  },
                  isHighlighted = true,
                  testTag = "message_export_pdf_${message.id}",
                )
                MessageActionChip(
                  icon = Icons.Outlined.Description,
                  label = "TXT",
                  contentDescription = "Download response as TXT file",
                  onClick = {
                    activeSubMenu = null
                    onExportResponse(false)
                  },
                  isHighlighted = true,
                  testTag = "message_export_txt_${message.id}",
                )
              }
            }

            // 1-Tap Tone Switcher ("Majedar Banayein") + Smart Follow-Up Suggestions below the latest completed AI response
            if (!isUser && isLastMessage && message.status == MessageStatus.SUCCESS) {
              // 1-Tap Tone Switcher Pills
              @OptIn(ExperimentalLayoutApi::class)
              FlowRow(
                modifier =
                  Modifier.padding(top = 6.dp)
                    .testTag("message_tone_switcher_row_${message.id}"),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                val toneSwitches =
                  listOf(
                    "😂 Desi / Funny" to "DESI_FUNNY",
                    "👶 5-Saal Baccha" to "ELI5_BACCHA",
                    "⚡ Short & Crisp" to "SHORTER",
                    "🔥 Pro Expert" to "PRO_EXPERT",
                  )
                toneSwitches.forEach { (label, modeKey) ->
                  Row(
                    modifier =
                      Modifier.defaultMinSize(minHeight = 24.dp)
                        .clip(AppRadius.shapePill)
                        .background(glass.accentPrimary.copy(alpha = 0.14f))
                        .border(
                          AppBorder.hairline,
                          glass.accentCyan.copy(alpha = 0.45f),
                          AppRadius.shapePill,
                        )
                        .semantics {
                          role = Role.Button
                          contentDescription = "Switch tone: $label"
                        }
                        .clickable(enabled = !isGenerating) {
                          onModifyResponse(modeKey)
                        }
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                        .testTag("message_tone_chip_${modeKey}_${message.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Text(
                      text = label,
                      style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                      fontWeight = FontWeight.SemiBold,
                      color = glass.accentCyan,
                      maxLines = 1,
                    )
                  }
                }
              }

              val followUpSuggestions =
                remember(message.id, message.content) {
                  message.generateFollowUpSuggestions()
                }
              if (followUpSuggestions.isNotEmpty()) {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                  modifier =
                    Modifier.padding(top = 5.dp)
                      .testTag("message_followups_row_${message.id}"),
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  followUpSuggestions.forEachIndexed { idx, suggestion ->
                    Row(
                      modifier =
                        Modifier.defaultMinSize(minHeight = 24.dp)
                          .clip(AppRadius.shapePill)
                          .background(glass.surfaceStandard.copy(alpha = 0.55f))
                          .border(
                            AppBorder.hairline,
                            glass.borderSubtle.copy(alpha = 0.65f),
                            AppRadius.shapePill,
                          )
                          .semantics {
                            role = Role.Button
                            contentDescription = "Ask follow-up: $suggestion"
                          }
                          .clickable(enabled = !isGenerating) {
                            onSelectFollowUp(suggestion)
                          }
                          .padding(horizontal = 10.dp, vertical = 3.dp)
                          .testTag("message_followup_chip_${message.id}_$idx"),
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Text(
                        text = "$suggestion ↗",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        fontWeight = FontWeight.Medium,
                        color = glass.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                  }
                }
              }
            }
          } else {
            // Subtle compact trigger to reveal actions on older user messages without clutter
            Row(
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .padding(top = 2.dp)
                  .clip(AppRadius.shapePill)
                  .semantics {
                    role = Role.Button
                    contentDescription = "Show message actions"
                  }
                  .clickable(onClick = onToggleActions)
                  .padding(horizontal = 6.dp, vertical = 2.dp)
                  .testTag("message_actions_toggle_${message.id}"),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Outlined.MoreHoriz,
                contentDescription = null,
                tint = glass.textMuted,
                modifier = Modifier.size(14.dp),
              )
            }
          }
        }
      }
    }
  }

@Composable
private fun MessageAttachmentCard(
  attachment: ChatAttachment,
) {
  val glass = AppThemeTokens.glass
  val decodedThumbnail =
    remember(attachment.id, attachment.thumbnailBase64) {
      val b64 = attachment.thumbnailBase64
      if (!b64.isNullOrBlank()) {
        try {
          val bytes = Base64.decode(b64, Base64.DEFAULT)
          BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (_: Exception) {
          null
        }
      } else {
        null
      }
    }

  Row(
    modifier =
      Modifier.clip(RoundedCornerShape(10.dp))
        .background(Color(0x40070B18))
        .border(AppBorder.hairline, glass.accentCyan.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
        .padding(horizontal = 10.dp, vertical = 6.dp)
        .testTag("chat_message_attachment_${attachment.id}"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (decodedThumbnail != null) {
      Image(
        bitmap = decodedThumbnail,
        contentDescription = attachment.fileName,
        contentScale = ContentScale.Crop,
        modifier =
          Modifier.size(38.dp)
            .clip(RoundedCornerShape(6.dp)),
      )
    } else {
      Box(
        modifier =
          Modifier.size(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(glass.accentCyan.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector =
            if (attachment.kind == AttachmentKind.PDF) Icons.Outlined.Description
            else Icons.Outlined.Image,
          contentDescription = null,
          tint = glass.accentCyan,
          modifier = Modifier.size(18.dp),
        )
      }
    }

    Column {
      Text(
        text = attachment.fileName,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFFF8FAFC),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      val detailText =
        buildString {
          append(if (attachment.kind == AttachmentKind.PDF) "PDF Document" else "Image")
          append(" • ${attachment.formattedFileSize}")
          if (attachment.kind == AttachmentKind.PDF && (attachment.pageCount ?: 0) > 0) {
            append(" • ${attachment.pageCount} ${if (attachment.pageCount == 1) "page" else "pages"}")
          }
        }
      Text(
        text = detailText,
        style = MaterialTheme.typography.labelSmall,
        color = glass.accentCyan,
        maxLines = 1,
      )
    }
  }
}

@Composable
private fun MessageCitationsSection(
  messageId: String,
  citations: List<WebCitation>,
  onOpenUrl: (String) -> Unit,
) {
  val glass = AppThemeTokens.glass
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .padding(top = 6.dp)
        .testTag("chat_citations_section_$messageId"),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Icon(
        imageVector = Icons.Outlined.Public,
        contentDescription = null,
        tint = glass.accentCyan,
        modifier = Modifier.size(13.dp),
      )
      Text(
        text = "VERIFIED WEB SOURCES (${citations.size})",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = glass.accentCyan,
        letterSpacing = 0.6.sp,
      )
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      citations.forEachIndexed { idx, citation ->
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .background(glass.surfaceStandard.copy(alpha = 0.72f))
              .border(
                AppBorder.hairline,
                glass.accentCyan.copy(alpha = 0.35f),
                RoundedCornerShape(10.dp),
              )
              .semantics {
                role = Role.Button
                contentDescription = "Open source ${citation.title} (${citation.domain})"
              }
              .clickable { onOpenUrl(citation.url) }
              .padding(horizontal = 10.dp, vertical = 6.dp)
              .testTag("chat_citation_chip_${messageId}_$idx"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = citation.title,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
              color = glass.textPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = citation.domain,
              style = MaterialTheme.typography.labelSmall,
              color = glass.accentCyan,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Spacer(modifier = Modifier.width(6.dp))
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = null,
            tint = glass.accentCyan,
            modifier = Modifier.size(14.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun MessageMinimalIconAction(
  icon: ImageVector,
  contentDescription: String,
  onClick: () -> Unit,
  testTag: String,
  enabled: Boolean = true,
  isHighlighted: Boolean = false,
) {
  val glass = AppThemeTokens.glass
  val tint =
    when {
      !enabled -> glass.textDisabled
      isHighlighted -> glass.accentCyan
      else -> glass.textMuted
    }

  Box(
    modifier =
      Modifier.size(28.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(
          if (isHighlighted) glass.accentPrimary.copy(alpha = 0.14f)
          else Color.Transparent
        )
        .semantics {
          role = Role.Button
          this.contentDescription = contentDescription
        }
        .clickable(enabled = enabled, onClick = onClick)
        .testTag(testTag),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = tint,
      modifier = Modifier.size(15.dp),
    )
  }
}

@Composable
private fun MessageActionChip(
  icon: ImageVector,
  label: String,
  contentDescription: String,
  onClick: () -> Unit,
  testTag: String,
  enabled: Boolean = true,
  isHighlighted: Boolean = false,
) {
  val glass = AppThemeTokens.glass
  val tint =
    when {
      !enabled -> glass.textDisabled
      isHighlighted -> glass.accentCyan
      else -> glass.textSecondary
    }

  Row(
    modifier =
      Modifier.defaultMinSize(minHeight = 22.dp)
        .clip(AppRadius.shapePill)
        .background(
          if (isHighlighted) glass.accentPrimary.copy(alpha = 0.16f)
          else glass.surfaceStandard.copy(alpha = 0.72f)
        )
        .border(
          AppBorder.hairline,
          if (isHighlighted) glass.accentCyan else glass.borderSubtle,
          AppRadius.shapePill,
        )
        .semantics {
          role = Role.Button
          this.contentDescription = contentDescription
        }
        .clickable(enabled = enabled, onClick = onClick)
        .padding(horizontal = 6.dp, vertical = 2.dp)
        .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(3.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = tint,
      modifier = Modifier.size(11.dp),
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
      fontWeight = FontWeight.Medium,
      color = tint,
    )
  }
}
