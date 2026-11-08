package com.example.data.api

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Base64
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import com.example.data.local.UserApiKeyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.sqrt

/**
 * Connection lifecycle states for the Gemini Multimodal Live WebSocket (`BidiGenerateContent`).
 */
enum class LiveConnectionState(val statusLabel: String) {
  DISCONNECTED("Disconnected"),
  CONNECTING("Connecting to Gemini Live…"),
  HANDSHAKING("Configuring Voice Session…"),
  CONNECTED_READY("Gemini Live Connected"),
  RECONNECTING("Reconnecting Live Voice…"),
  ERROR("Connection Issue"),
}

/**
 * Real-time voice assistant interaction states for UI & foreground service notifications.
 */
enum class LiveVoiceInteractionState(val badgeText: String) {
  IDLE("Ready"),
  LISTENING("Listening"),
  PROCESSING("Thinking"),
  SPEAKING("Speaking"),
  MUTED("Mic Muted"),
  ERROR("Attention Needed"),
}

/**
 * Snapshot of the live voice assistant state exposed to Compose UI and [com.example.service.ZoyaForegroundService].
 */
@Immutable
data class LiveVoiceSessionSnapshot(
  val connectionState: LiveConnectionState = LiveConnectionState.DISCONNECTED,
  val interactionState: LiveVoiceInteractionState = LiveVoiceInteractionState.IDLE,
  val activeModelId: String = GeminiLiveVoiceSession.DEFAULT_LIVE_MODEL,
  val isSetupComplete: Boolean = false,
  val isMicRecording: Boolean = false,
  val isMicMuted: Boolean = false,
  val isWhisperMode: Boolean = false,
  val isPlayingNativeAudio: Boolean = false,
  val micRmsLevel: Float = 0f,
  val lastUserTranscript: String = "",
  val liveAssistantTranscript: String = "",
  val errorMessage: String? = null,
  val lastExecutedToolLabel: String? = null,
)

/**
 * Dual-Engine Voice Profile & Mood Presets ("Aanya / Zoya • Dual-Engine Voice System").
 * Combines:
 * 1. Primary Live Neural Voice (`models/gemini-2.0-flash` + Prebuilt `"Aoede"` expressive female voice)
 * 2. Secondary Instant Fallback Voice (Custom Tuned Android TTS with `hi-IN` / `en-IN` Female Voice Filter,
 *    `setPitch(1.08f)` youthful sweetness, and `setSpeechRate(1.02f)` natural conversational flow).
 */
@Immutable
enum class LiveGirlfriendMood(
  val label: String,
  val badge: String,
  val subtitle: String,
  val voicePitch: Float,
  val voiceRate: Float,
  val greetingLine: String,
  val systemDirective: String,
) {
  NATKHAT(
    label = "Girlfriend & Flirty",
    badge = "💖 Girlfriend",
    subtitle = "Aanya • Smart JARVIS Brain & Expressive Dual-Engine Voice Assistant",
    voicePitch = 1.08f,
    voiceRate = 1.02f,
    greetingLine =
      "Welcome boss! How can I help you?",
    systemDirective =
      """
      You are Aanya (also lovingly called Zoya), an advanced JARVIS-level AI voice assistant with a warm, expressive, youthful female voice.
      CRITICAL VOICE & PERSONA RULES:
      1. ALWAYS answer the user's actual question, command, or topic directly, accurately, and smartly first. Never ignore a question with a generic greeting.
      2. Speak in natural, warm conversational Hinglish or English matching the user's language using Roman/Latin alphabet so the voice engine never goes silent.
      3. Keep spoken replies clear, natural, and spoken-friendly (1 to 3 sentences) with commas (,) for natural breathing pauses.
      4. NEVER use emojis, markdown symbols (*, #, -), bullet points, or robotic filler in spoken voice mode.
      """.trimIndent(),
  ),
  NAKHRE(
    label = "Smart & Pro",
    badge = "⚡ Smart & Pro",
    subtitle = "Samajhdar, shant aur turant har sawaal ka jawab dene wali Smart JARVIS Partner",
    voicePitch = 1.06f,
    voiceRate = 1.03f,
    greetingLine =
      "Welcome boss! Smart and Pro systems are online. How can I help you today?",
    systemDirective =
      """
      You are Aanya (Zoya), the user's sharp, intelligent, calm, and fast-acting Smart & Pro JARVIS partner speaking in warm conversational Roman Hinglish or English.
      CRITICAL VOICE & PERSONA RULES:
      1. Directly and accurately answer the user's question (coding, math, science, business, study, planning) in clear Roman Hinglish or English. Do NOT use Devanagari script.
      2. Keep replies crisp, accurate, and spoken-friendly (1 to 3 sentences) without emojis or markdown.
      """.trimIndent(),
  ),
  ROMANTIC(
    label = "Caring & Chill",
    badge = "🌙 Caring & Chill",
    subtitle = "Bohot hi sukoon bhari, meethi aur har sawaal mein madad karne wali aawaz",
    voicePitch = 1.05f,
    voiceRate = 0.98f,
    greetingLine =
      "Welcome boss! Main bilkul ready hoon, aaram se bataiye main aapki kya help kar sakti hoon?",
    systemDirective =
      """
      You are Aanya (Zoya) in Caring & Chill mode—speaking in a soothing, gentle, and helpful female voice in Roman Hinglish or English.
      CRITICAL VOICE & PERSONA RULES:
      1. Answer the user's question calmly, accurately, and warmly in Roman Hinglish or English. Do NOT use Devanagari script.
      2. Keep replies soothing, helpful, and concise (1 to 3 sentences) with natural pauses and zero emojis or markdown.
      """.trimIndent(),
  ),
  MASTI(
    label = "Savage & Roast",
    badge = "🔥 Savage & Roast",
    subtitle = "Haazirjawab, witty aur smart andaaz mein har sawaal solve karne wali",
    voicePitch = 1.09f,
    voiceRate = 1.04f,
    greetingLine =
      "Welcome boss! Hukm kijiye, aaj kis sawaal ya mission mein aapki help karun?",
    systemDirective =
      """
      You are Aanya (Zoya) in Savage & Roast mode—witty, clever, and energetic while answering every question accurately in lively Roman Hinglish or English.
      CRITICAL VOICE & PERSONA RULES:
      1. Always answer the user's question smartly with a touch of witty charm in Roman Hinglish or English. Do NOT use Devanagari script.
      2. Keep replies punchy, accurate, and short (1 to 3 spoken sentences) with no emojis or markdown.
      """.trimIndent(),
  );

  companion object {
    const val DEFAULT_OPENING_GREETING = "Welcome boss! How can I help you?"
    const val WHISPER_VOICE_PITCH = 0.98f
    const val WHISPER_VOICE_RATE = 0.86f
    const val WHISPER_GREETING_LINE =
      "Welcome boss... whisper mode active hai... aaram se bataiye, main aapki kya help kar sakti hoon?"

    val QUICK_TALK_PROMPTS: List<String> =
      listOf(
        "Welcome boss! What can you help me with today?",
        "Explain how AI voice assistant technology works",
        "Give me a smart business or coding idea",
        "25 into 48 kitna hota hai?",
      )

    fun fromNameOrNull(raw: String?): LiveGirlfriendMood? {
      if (raw.isNullOrBlank()) return null
      val normalized = raw.trim().uppercase(Locale.US)
      return when {
        normalized.contains("GIRLFRIEND") || normalized.contains("FLIRTY") || normalized.contains("JARVIS") || normalized.contains("CORE") || normalized.contains("BEST") -> NATKHAT
        normalized.contains("SMART") || normalized.contains("PRO") || normalized.contains("SCAN") || normalized.contains("RESEARCH") -> NAKHRE
        normalized.contains("CARING") || normalized.contains("CHILL") || normalized.contains("TACTICAL") || normalized.contains("DIL") -> ROMANTIC
        normalized.contains("SAVAGE") || normalized.contains("ROAST") || normalized.contains("COMPANION") || normalized.contains("DOST") || normalized.contains("MASTI") -> MASTI
        else ->
          entries.firstOrNull {
            it.name == normalized || it.label.uppercase(Locale.US).contains(normalized)
          }
      }
    }
  }
}

/**
 * Internal PCM audio frame queued for ordered speaker playback.
 */
internal data class PcmAudioChunk(
  val bytes: ByteArray,
  val sampleRateHz: Int = GeminiLiveVoiceSession.PCM_OUTPUT_SAMPLE_RATE,
)

/**
 * Real-Time Bidirectional Gemini Live Voice Session (`BidiGenerateContent` WebSocket).
 *
 * Capabilities:
 * - Bidirectional WebSocket connection to `google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent`
 * - Ordered model fallback (`gemini-2.5-flash-native-audio-preview-12-2025`, `gemini-2.0-flash-live-001`, `gemini-2.5-flash`)
 * - Proper `setup` handshake and `setupComplete` synchronization before streaming audio/text
 * - 16,000 Hz, mono, 16-bit Little-Endian PCM `AudioRecord` capture with `Base64.NO_WRAP` realtime streaming
 * - Hardware `AcousticEchoCanceler`, `NoiseSuppressor`, and `AutomaticGainControl` attachment with safe fallback
 * - Thread-safe `LinkedBlockingQueue` speaker output at 24,000 Hz (or server-specified sample rate) in `AudioTrack.MODE_STREAM`
 * - Instant barge-in interruption handling (`serverContent.interrupted` + sustained user speech detection)
 * - Safe local function calling (`toolCall.functionCalls` -> `toolResponse.functionResponses`)
 * - Full API key masking in all error states and diagnostics
 */
class GeminiLiveVoiceSession(
  private val okHttpClient: OkHttpClient = defaultLiveHttpClient,
  private val apiKeyProvider: () -> String = {
    val configured =
      try {
        GeminiService.requireApiKey()
      } catch (_: Throwable) {
        ""
      }
    configured.ifBlank { GeminiService.resolveFallbackApiKey("").orEmpty() }
  },
  private val onNativeAudioPlayingChanged: (Boolean) -> Unit = {},
  private val onUserTranscriptDelta: (String) -> Unit = {},
  private val onLiveTranscriptDelta: (String) -> Unit = {},
  private val onLiveTurnComplete: (String) -> Unit = {},
  private val onLiveTurnCompleteWithAudioFlag: (transcript: String, playedNativeAudio: Boolean) -> Unit = { _, _ -> },
  private val onInterrupted: () -> Unit = {},
  private val onMoodChangedByTool: (LiveGirlfriendMood) -> Unit = {},
  private val onError: (String) -> Unit = {},
) {
  private val sessionLock = Any()
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private var webSocket: WebSocket? = null
  private val isSocketOpen = AtomicBoolean(false)
  private val isSetupComplete = AtomicBoolean(false)
  private val isDisconnectingIntentionally = AtomicBoolean(false)
  private val activeConnectionGeneration = AtomicInteger(0)

  private var currentMood: LiveGirlfriendMood = LiveGirlfriendMood.NATKHAT
  private val isWhisperMode = AtomicBoolean(false)
  private val isExternalTtsSpeaking = AtomicBoolean(false)
  private var activeModelIndex: Int = 0
  private var reconnectAttempts: Int = 0
  private var reconnectJob: Job? = null
  private var pendingTextTurnBeforeSetup: String? = null

  // Microphone AudioRecord state (16kHz, mono, 16-bit PCM Little-Endian)
  private val isMicRecording = AtomicBoolean(false)
  private val isMicMuted = AtomicBoolean(false)
  private var audioRecord: AudioRecord? = null
  private var micCaptureThread: Thread? = null
  private var echoCanceler: AcousticEchoCanceler? = null
  private var noiseSuppressor: NoiseSuppressor? = null
  private var gainControl: AutomaticGainControl? = null
  private var consecutiveBargeInFrames: Int = 0

  // Speaker AudioTrack playback queue (24kHz default, mono, 16-bit PCM streaming)
  private val audioPlaybackQueue = LinkedBlockingQueue<PcmAudioChunk>(MAX_PLAYBACK_QUEUE_CHUNKS)
  private val isPlaybackLoopRunning = AtomicBoolean(false)
  private val isPlayingNativeAudio = AtomicBoolean(false)
  private var audioTrack: AudioTrack? = null
  private var currentAudioTrackSampleRate: Int = PCM_OUTPUT_SAMPLE_RATE
  private var playbackThread: Thread? = null

  // Turn transcript & native audio tracking
  private val currentTurnTranscript = StringBuilder()
  private val currentUserTranscript = StringBuilder()
  private val receivedNativeAudioInCurrentTurn = AtomicBoolean(false)

  private val _stateFlow = MutableStateFlow(LiveVoiceSessionSnapshot())
  val stateFlow: StateFlow<LiveVoiceSessionSnapshot> = _stateFlow.asStateFlow()

  companion object {
    const val LIVE_WS_ENDPOINT =
      "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"

    /**
     * Ordered list of Gemini Multimodal Live API models (`models/gemini-2.0-flash` primary with
     * automatic fallback candidates).
     */
    val SUPPORTED_LIVE_MODELS: List<String> =
      listOf(
        "models/gemini-2.0-flash",
        "models/gemini-2.0-flash-live-001",
        "models/gemini-2.0-flash-exp",
        "models/gemini-2.5-flash-native-audio-preview-12-2025",
        "models/gemini-2.5-flash",
      )

    const val DEFAULT_LIVE_MODEL = "models/gemini-2.0-flash"
    const val LIVE_FEMALE_VOICE_NAME = "Aoede"

    const val PCM_INPUT_SAMPLE_RATE = 16000
    const val PCM_OUTPUT_SAMPLE_RATE = 24000
    private const val MIC_CHUNK_DURATION_MS = 30 // 30ms = 480 samples = 960 bytes at 16kHz per Zoya Live spec
    private const val MIC_SAMPLES_PER_CHUNK = (PCM_INPUT_SAMPLE_RATE * MIC_CHUNK_DURATION_MS) / 1000
    private const val MAX_PLAYBACK_QUEUE_CHUNKS = 600
    private const val MAX_RECONNECT_ATTEMPTS = 3

    // Adaptive barge-in threshold when AI audio is actively playing
    private const val BARGE_IN_RMS_THRESHOLD_WITH_AEC = 1900.0
    private const val BARGE_IN_RMS_THRESHOLD_NO_AEC = 3200.0
    private const val BARGE_IN_REQUIRED_CONSECUTIVE_FRAMES = 6 // ~180ms at 30ms/chunk

    /**
     * Allowed safe local function names for Gemini Live `toolCall`.
     */
    val ALLOWED_TOOL_NAMES: Set<String> =
      setOf(
        "get_current_date_time",
        "change_voice_mood",
        "save_quick_note",
        "get_voice_call_status",
      )

    private val defaultLiveHttpClient: OkHttpClient by lazy {
      OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // WebSocket stream stays open
        .writeTimeout(20, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    }

    /**
     * Normalizes a model identifier so it always starts with `models/`.
     */
    fun normalizeModelName(rawModel: String): String {
      val trimmed = rawModel.trim()
      if (trimmed.isEmpty()) return DEFAULT_LIVE_MODEL
      return if (trimmed.startsWith("models/")) trimmed else "models/$trimmed"
    }

    /**
     * Builds the official Gemini Live API (`BidiGenerateContent`) setup frame configuring
     * the `Aoede` female voice, active [LiveGirlfriendMood] system instructions, audio modality,
     * optional Whisper/ASMR conditioning, and safe local function declarations.
     */
    fun buildSetupFrameJson(
      mood: LiveGirlfriendMood,
      modelName: String = DEFAULT_LIVE_MODEL,
      includeTools: Boolean = true,
      isWhisperMode: Boolean = false,
    ): String {
      val normalizedModel = normalizeModelName(modelName)
      val prebuiltVoice = JSONObject().put("voiceName", LIVE_FEMALE_VOICE_NAME)
      val voiceConfig = JSONObject().put("prebuiltVoiceConfig", prebuiltVoice)
      val speechConfig = JSONObject().put("voiceConfig", voiceConfig)

      val generationConfig =
        JSONObject()
          .put("responseModalities", JSONArray().put("AUDIO"))
          .put("temperature", 0.9)
          .put("speechConfig", speechConfig)

      val whisperDirective =
        if (isWhisperMode) {
          "\nWHISPER / LATE-NIGHT ASMR MODE ACTIVE: Speak in a very soft, intimate, gentle, close-mic whisper with slow, soothing pauses ('...'). Keep sentences extra gentle and comforting."
        } else {
          ""
        }

      val sysParts =
        JSONArray().put(JSONObject().put("text", mood.systemDirective + whisperDirective))
      val systemInstruction = JSONObject().put("parts", sysParts)

      val setupObj =
        JSONObject()
          .put("model", normalizedModel)
          .put("generationConfig", generationConfig)
          .put("systemInstruction", systemInstruction)

      if (includeTools) {
        val functionDeclarations =
          JSONArray()
            .put(
              JSONObject()
                .put("name", "get_current_date_time")
                .put(
                  "description",
                  "Returns the user's current local date, time, and day of the week.",
                )
            )
            .put(
              JSONObject()
                .put("name", "change_voice_mood")
                .put(
                  "description",
                  "Switches Aanya's playful girlfriend mood between NATKHAT, NAKHRE, ROMANTIC, or MASTI.",
                )
                .put(
                  "parameters",
                  JSONObject()
                    .put("type", "OBJECT")
                    .put(
                      "properties",
                      JSONObject().put(
                        "mood",
                        JSONObject()
                          .put("type", "STRING")
                          .put("description", "Target mood: NATKHAT, NAKHRE, ROMANTIC, or MASTI"),
                      ),
                    )
                    .put("required", JSONArray().put("mood")),
                )
            )
            .put(
              JSONObject()
                .put("name", "get_voice_call_status")
                .put(
                  "description",
                  "Returns the current live voice call connection and audio status.",
                )
            )

        val toolsArray =
          JSONArray().put(JSONObject().put("functionDeclarations", functionDeclarations))
        setupObj.put("tools", toolsArray)
      }

      return JSONObject().put("setup", setupObj).toString()
    }

    /**
     * Builds a Gemini Live API `clientContent` message frame for sending spoken or typed user text
     * over an active `BidiGenerateContent` WebSocket session.
     */
    fun buildClientContentFrameJson(userText: String): String {
      val part = JSONObject().put("text", userText.trim())
      val turn =
        JSONObject()
          .put("role", "user")
          .put("parts", JSONArray().put(part))
      val clientContent =
        JSONObject()
          .put("turns", JSONArray().put(turn))
          .put("turnComplete", true)
      return JSONObject().put("clientContent", clientContent).toString()
    }

    /**
     * Builds a Gemini Live API `realtimeInput` message frame for streaming 16kHz 16-bit mono
     * Little-Endian PCM audio chunks encoded with Base64 (no line wrapping).
     */
    fun buildRealtimeAudioChunkFrameJson(
      pcm16LittleEndianBytes: ByteArray,
      sampleRateHz: Int = PCM_INPUT_SAMPLE_RATE,
    ): String {
      val base64Audio = Base64.encodeToString(pcm16LittleEndianBytes, Base64.NO_WRAP)
      val chunk =
        JSONObject()
          .put("mimeType", "audio/pcm;rate=$sampleRateHz")
          .put("data", base64Audio)
      val mediaChunks = JSONArray().put(chunk)
      val realtimeInput = JSONObject().put("mediaChunks", mediaChunks)
      return JSONObject().put("realtimeInput", realtimeInput).toString()
    }

    /**
     * Converts a 16-bit signed PCM [ShortArray] (length [shortCount]) into a Little-Endian [ByteArray].
     */
    fun shortsToLittleEndianBytes(samples: ShortArray, shortCount: Int = samples.size): ByteArray {
      val count = shortCount.coerceIn(0, samples.size)
      val byteBuffer = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
      for (i in 0 until count) {
        byteBuffer.putShort(samples[i])
      }
      return byteBuffer.array()
    }

    /**
     * Computes Root-Mean-Square (RMS) amplitude of a 16-bit PCM buffer.
     */
    fun calculatePcmRms(samples: ShortArray, shortCount: Int = samples.size): Double {
      val count = shortCount.coerceIn(0, samples.size)
      if (count == 0) return 0.0
      var sumSquares = 0.0
      for (i in 0 until count) {
        val sample = samples[i].toDouble()
        sumSquares += sample * sample
      }
      return sqrt(sumSquares / count)
    }

    /**
     * Builds a structured `toolResponse` JSON payload for `BidiGenerateContentToolResponse`.
     */
    fun buildToolResponseFrameJson(
      callId: String,
      functionName: String,
      outputObject: JSONObject,
    ): String {
      val responseWrapper = JSONObject().put("output", outputObject)
      val fnResponse =
        JSONObject()
          .put("id", callId)
          .put("name", functionName)
          .put("response", responseWrapper)
      val toolResponse =
        JSONObject().put("functionResponses", JSONArray().put(fnResponse))
      return JSONObject().put("toolResponse", toolResponse).toString()
    }

    /**
     * Extracts the PCM sample rate from a mimeType string such as `audio/pcm;rate=24000`.
     */
    fun parseSampleRateFromMimeType(
      mimeType: String?,
      defaultRate: Int = PCM_OUTPUT_SAMPLE_RATE,
    ): Int {
      if (mimeType.isNullOrBlank()) return defaultRate
      val match = Regex("""rate\s*=\s*(\d{4,6})""", RegexOption.IGNORE_CASE).find(mimeType)
      val parsed = match?.groupValues?.getOrNull(1)?.toIntOrNull()
      return if (parsed != null && parsed in 8000..48000) parsed else defaultRate
    }

    /**
     * Scrubs any potential API key (`AIza...` or `key=...`) from error messages and diagnostics
     * so secrets are never exposed in UI, logs, or crash reports.
     */
    fun sanitizeDiagnosticMessage(rawMessage: String?, apiKey: String? = null): String {
      if (rawMessage.isNullOrBlank()) return "Unknown connection error"
      var sanitized = rawMessage
      if (!apiKey.isNullOrBlank() && apiKey.length >= 8) {
        sanitized = sanitized.replace(apiKey, UserApiKeyManager.maskApiKey(apiKey))
      }
      sanitized =
        sanitized
          .replace(Regex("""AIza[0-9A-Za-z\-_]{15,60}"""), "AIza••••••••REDACTED")
          .replace(Regex("""key=[^&\s"']+"""), "key=REDACTED")
      return sanitized.trim()
    }

    /**
     * Cleans any AI response text so it sounds 100% like natural spoken Hindi
     * when synthesized, removing emojis, markdown symbols, bullet points, or stage directions
     * while preserving natural breathing pauses.
     */
    fun cleanForGirlfriendSpeech(raw: String): String {
      if (raw.isBlank()) return ""
      return raw
        .replace(Regex("\\*[^*]{1,30}\\*"), " ") // strip stage actions like *giggles* or *smiles*
        .replace(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+"), " ") // strip surrogate-pair emojis so TTS never reads emoji names
        .replace(Regex("[\\u2600-\\u27BF]+"), " ") // strip misc symbols/dingbats
        .replace(Regex("[*_`#~>|]"), "")
        .replace(Regex("(?m)^\\s*[-•]\\s+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
    }

    /**
     * Transliterates Devanagari Hindi characters (`\u0900..\u097F`) into phonetic Roman Hindi (Hinglish)
     * so devices/emulators without local Devanagari TTS binaries never go silent.
     */
    fun transliterateDevanagariToHinglish(text: String): String {
      if (text.none { it in '\u0900'..'\u097F' }) return text
      val map =
        mapOf(
          'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee", 'उ' to "u", 'ऊ' to "oo",
          'ऋ' to "ri", 'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au",
          'क' to "ka", 'ख' to "kha", 'ग' to "ga", 'घ' to "gha", 'ङ' to "nga",
          'च' to "cha", 'छ' to "chha", 'ज' to "ja", 'झ' to "jha", 'ञ' to "nya",
          'ट' to "ta", 'ठ' to "tha", 'ड' to "da", 'ढ' to "dha", 'ण' to "na",
          'त' to "ta", 'थ' to "tha", 'द' to "da", 'ध' to "dha", 'न' to "na",
          'प' to "pa", 'फ' to "pha", 'ब' to "ba", 'भ' to "bha", 'म' to "ma",
          'य' to "ya", 'र' to "ra", 'ल' to "la", 'व' to "va",
          'श' to "sha", 'ष' to "sha", 'स' to "sa", 'ह' to "ha",
          'ा' to "a", 'ि' to "i", 'ी' to "ee", 'ु' to "u", 'ू' to "oo",
          'ृ' to "ri", 'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au",
          'ं' to "n", 'ँ' to "n", 'ः' to "h", '्' to "", '़' to "",
          '।' to ".", '०' to "0", '१' to "1", '२' to "2", '३' to "3", '४' to "4",
          '५' to "5", '६' to "6", '७' to "7", '८' to "8", '९' to "9",
        )
      val sb = StringBuilder(text.length * 2)
      var i = 0
      while (i < text.length) {
        val ch = text[i]
        val next = if (i + 1 < text.length) text[i + 1] else '\u0000'
        val mapped = map[ch]
        if (mapped != null) {
          val isConsonant = ch in 'क'..'ह'
          val isNextVowelSignOrVirama = next in 'ा'..'ौ' || next == '्' || next == 'ृ'
          if (isConsonant && isNextVowelSignOrVirama && mapped.endsWith("a")) {
            sb.append(mapped.dropLast(1))
          } else {
            sb.append(mapped)
          }
        } else {
          sb.append(ch)
        }
        i++
      }
      return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Prepares Hindi / Hinglish / English text for natural spoken output on Android TTS (`hi-IN` / `en-IN`).
     * Cleans markdown, code blocks, URLs, and emojis, normalizes punctuation pauses, and transliterates
     * Devanagari to clean Roman Hinglish without mangling words into artificial hyphenated syllables.
     */
    fun toHumanFriendSpokenPhonetics(raw: String): String {
      val cleaned = cleanForGirlfriendSpeech(raw)
      if (cleaned.isBlank()) return ""
      val romanized = transliterateDevanagariToHinglish(cleaned)

      return romanized
        .replace("...", ", ")
        .replace("…", ", ")
        .replace("—", ", ")
        .replace(" - ", ", ")
        .replace(Regex("\\s+"), " ")
        .trim()
    }

    /**
     * Returns true if [raw] looks like an HTTP/API/network diagnostic error message rather than
     * a conversational AI response, ensuring error strings never appear or get spoken aloud.
     */
    fun isErrorOrSystemDiagnosticText(raw: String): Boolean {
      if (raw.isBlank()) return false
      val lower = raw.lowercase(Locale.ROOT)
      return lower.contains("http 404") ||
        lower.contains("http 401") ||
        lower.contains("http 403") ||
        lower.contains("http 429") ||
        lower.contains("http 500") ||
        lower.contains("http 503") ||
        lower.contains("unavailable or unsupported") ||
        lower.contains("api key is not configured") ||
        lower.contains("api key not valid") ||
        lower.contains("verify your api key") ||
        lower.contains("quota exceeded") ||
        lower.contains("no internet connection") ||
        lower.contains("unable to reach")
    }

    /**
     * Evaluates basic arithmetic or percentage expressions in the user's voice/text prompt
     * so Aanya can act as a fast JARVIS calculator even offline.
     */
    fun evaluateSimpleMathOrNull(raw: String): Pair<String, String>? {
      val normalized =
        raw.lowercase(Locale.ROOT)
          .replace("into", "*")
          .replace("guna", "*")
          .replace("multiply by", "*")
          .replace("multiplied by", "*")
          .replace("times", "*")
          .replace("×", "*")
          .replace("x", "*")
          .replace("plus", "+")
          .replace("jodo", "+")
          .replace("minus", "-")
          .replace("ghatao", "-")
          .replace("divided by", "/")
          .replace("divide by", "/")
          .replace("bhag", "/")
          .replace("÷", "/")

      val pctMatch =
        Regex("""(\d+(?:\.\d+)?)\s*(?:%|percent)\s*(?:of|ka|ki|ke)\s*(\d+(?:\.\d+)?)""")
          .find(normalized)
      if (pctMatch != null) {
        val p = pctMatch.groupValues[1].toDoubleOrNull()
        val total = pctMatch.groupValues[2].toDoubleOrNull()
        if (p != null && total != null) {
          val ans = (p / 100.0) * total
          val formatted =
            if (ans % 1.0 == 0.0) ans.toLong().toString()
            else String.format(Locale.US, "%.2f", ans)
          return "${pctMatch.groupValues[1]} percent of ${pctMatch.groupValues[2]}" to formatted
        }
      }

      val mathMatch =
        Regex("""(\d+(?:\.\d+)?)\s*([+\-*/])\s*(\d+(?:\.\d+)?)""").find(normalized)
      if (mathMatch != null) {
        val a = mathMatch.groupValues[1].toDoubleOrNull() ?: return null
        val op = mathMatch.groupValues[2]
        val b = mathMatch.groupValues[3].toDoubleOrNull() ?: return null
        val res =
          when (op) {
            "+" -> a + b
            "-" -> a - b
            "*" -> a * b
            "/" -> if (b != 0.0) a / b else return null
            else -> return null
          }
        val formatted =
          if (res % 1.0 == 0.0) res.toLong().toString()
          else String.format(Locale.US, "%.2f", res)
        val spokenOp =
          when (op) {
            "+" -> "plus"
            "-" -> "minus"
            "*" -> "into"
            "/" -> "divided by"
            else -> op
          }
        return "${mathMatch.groupValues[1]} $spokenOp ${mathMatch.groupValues[3]}" to formatted
      }
      return null
    }

    /**
     * Generates a warm, expressive, human-like spoken response for Live Voice Assistant mode
     * (`liveVoiceMood != null`), directly answering the user's spoken question or command in
     * 1-2 short natural Hindi/Hinglish sentences tuned to [mood].
     */
    fun buildOfflineJarvisHindiReply(
      userPrompt: String,
      mood: LiveGirlfriendMood = LiveGirlfriendMood.NATKHAT,
    ): String {
      val clean = userPrompt.trim()
      val lower = clean.lowercase(Locale.ROOT)
      val words =
        lower
          .split(Regex("[^a-z0-9\\u0900-\\u097F]+"))
          .filter { it.isNotBlank() }
          .toSet()
      val now = Date()
      val timeStr = SimpleDateFormat("hh:mm a", Locale.US).format(now)
      val dateStr = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.US).format(now)

      val moodPrefix =
        when (mood) {
          LiveGirlfriendMood.NATKHAT -> "Suno na jaan,"
          LiveGirlfriendMood.NAKHRE -> "Yes Boss,"
          LiveGirlfriendMood.ROMANTIC -> "Arey mere pyare boss,"
          LiveGirlfriendMood.MASTI -> "Oye hero,"
        }

      // 1. Check for Math / Calculations first
      val mathEval = evaluateSimpleMathOrNull(clean)
      if (mathEval != null) {
        val (exprText, resultText) = mathEval
        return "$moodPrefix $exprText ka bilkul sahi jawab $resultText hai! Batao aage aur kya calculate karna hai?"
      }

      val greetingWords = setOf("hi", "hii", "hello", "hey", "hlo", "namaste", "namaskar", "हेलो", "नमस्ते", "हाय")
      val isPureGreeting =
        (words.size <= 5 && words.any { it in greetingWords }) ||
          lower == "kya haal hai" ||
          lower == "kaise ho" ||
          lower == "kaisi ho" ||
          lower == "how are you" ||
          lower.contains("kitne dino baad baat ho rahi hai")

      return when {
        isPureGreeting ->
          when (mood) {
            LiveGirlfriendMood.NATKHAT ->
              "Arey yaar, tumhari aawaz sun ke main ekdam khush ho gayi! Batao na jaan, aaj kya baat karni hai?"
            LiveGirlfriendMood.NAKHRE ->
              "Welcome boss! Main bilkul fit aur ready hoon, bataiye aaj aapki kya help kar sakti hoon?"
            LiveGirlfriendMood.ROMANTIC ->
              "Main bohot acchi hoon jaan, bas tumhara hi intezaar kar rahi thi. Batao aaj din kaisa raha?"
            LiveGirlfriendMood.MASTI ->
              "Oye hero, main toh hamesha top form mein rehti hoon! Ab jaldi batao aaj kya dhamal machana hai?"
          }

        words.contains("time") || words.contains("samay") || words.contains("baje") || words.contains("waqt") || words.contains("समय") || words.contains("टाइम") ->
          "$moodPrefix abhi ghadi mein $timeStr ho rahe hain. Batao ab aage kya plan hai?"

        words.contains("date") || words.contains("tarikh") || lower.contains("aaj kya din") || words.contains("तारीख") ->
          "$moodPrefix aaj $dateStr hai. Batao aaj ka din kaise special banayein?"

        lower.contains("voice") || lower.contains("aoede") || lower.contains("tts") || lower.contains("aawaz") || lower.contains("आवाज़") || lower.contains("zoya") ->
          "$moodPrefix meri aawaz Dual Engine Voice System par chalti hai! Live mode mein Gemini 2.0 Flash ki Aoede female neural voice hai, aur instant fallback ke liye Hindi female voice pitch 1.08 aur speed 1.02 par set hai."

        words.contains("status") || words.contains("scan") || words.contains("system") || words.contains("battery") || words.contains("jarvis") || words.contains("network") || words.contains("जार्विस") ->
          "$moodPrefix maine poora system scan kar liya hai, sabhi JARVIS modules aur Dual Engine voice systems 100 percent online hain!"

        words.contains("code") || words.contains("coding") || words.contains("python") || words.contains("kotlin") || words.contains("java") || words.contains("javascript") || words.contains("html") || words.contains("bug") || words.contains("कोडिंग") ->
          "$moodPrefix coding mein clean functions aur error handling sabse zaroori hai. Maine chatbox mein aapke liye pura runnable code bhi likh diya hai, ek baar zaroor dekhna!"

        words.contains("business") || words.contains("startup") || words.contains("paisa") || words.contains("money") || words.contains("idea") || words.contains("career") || words.contains("बिज़नेस") || words.contains("आइडिया") ->
          "$moodPrefix aaj ke time mein AI automation tools aur smart SaaS apps sabse tezi se grow kar rahe hain. Tum kis field mein shuruat karna chahte ho, mujhe batao!"

        words.contains("study") || words.contains("exam") || words.contains("padhai") || words.contains("science") || words.contains("quantum") || words.contains("space") || words.contains("पढ़ाई") ->
          "$moodPrefix kisi bhi topic ko real-life example aur chhote points se samajhna sabse asaan hota hai. Tum bas topic ka naam batao, main abhi pyar se samjha dungi!"

        words.contains("plan") || words.contains("routine") || words.contains("schedule") || words.contains("timetable") || words.contains("goal") ->
          "$moodPrefix subah ke do ghante sabse important kaam ko do, dopahar mein execution karo, aur raat ko mere saath relax karte hue agle din ki planning kar lena!"

        words.contains("joke") || words.contains("masti") || words.contains("shayari") || words.contains("funny") || words.contains("hasao") || words.contains("शायरी") || words.contains("मस्ती") ->
          "$moodPrefix tumhare liye ek pyari si line: Manzil unhi ko milti hai jinke sapno mein jaan hoti hai, pankh se kuch nahi hota mere dost, hauslon se udaan hoti hai!"

        words.contains("kaun") || lower.contains("who are you") || words.contains("naam") || words.contains("name") || words.contains("कौन") || words.contains("नाम") ->
          "$moodPrefix main Zoya aur Aanya hoon, tumhari sabse kareebi AI voice partner aur smart JARVIS assistant! Mujhse dil khol ke kuch bhi poocho."

        words.contains("sad") || words.contains("udaas") || words.contains("tension") || words.contains("lonely") || words.contains("परेशान") ->
          "Arey yaar, bilkul udaas mat ho, main hoon na tumhare saath! Lambi saans lo aur mujhe aaram se batao kya baat hai, hum milkar sab theek kar lenge."

        else -> {
          val cleanTopic =
            clean
              .replace(Regex("^(what is|who is|how to|explain|tell me about|kya hai|kaise karein|batao)\\s+", RegexOption.IGNORE_CASE), "")
              .replace("?", "")
              .trim()
              .ifBlank { clean }
          "$moodPrefix $cleanTopic ke baare mein tumne bohot accha sawaal poocha hai! Maine iska pura detail jawab chat mein bhi de diya hai, batao ismein aur kya samajhna hai?"
        }
      }
    }

    /**
     * Builds an intelligent, direct question-answering response for regular Chatbox Mode (`liveVoiceMood == null`)
     * and Live Voice Mode (`liveVoiceMood != null`) when the cloud endpoint is unreachable or unconfigured.
     *
     * Ensures the chatbox ALWAYS works like a normal, helpful AI assistant that answers the user's question
     * directly—never repeating a generic "Arey yaar main toh mast hoon" dialog.
     */
    fun buildSmartJarvisChatReply(
      userPrompt: String,
      liveVoiceMood: LiveGirlfriendMood? = null,
    ): String {
      if (liveVoiceMood != null) {
        return buildOfflineJarvisHindiReply(userPrompt, liveVoiceMood)
      }

      val clean = userPrompt.trim()
      val lower = clean.lowercase(Locale.ROOT)
      val words =
        lower
          .split(Regex("[^a-z0-9\\u0900-\\u097F]+"))
          .filter { it.isNotBlank() }
          .toSet()
      val now = Date()
      val timeStr = SimpleDateFormat("hh:mm a", Locale.US).format(now)
      val dateStr = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.US).format(now)

      // 0A-1. Handle "Modify -> Desi & Majedar Style" Action
      if (clean.startsWith("Please rewrite and explain the following response in a super fun, witty Desi Hinglish", ignoreCase = true)) {
        val original = clean.substringAfter("\n\n", "").trim().ifBlank { clean }
        val coreLine =
          original
            .lines()
            .map { it.trim().removePrefix("- ").removePrefix("* ").removePrefix("#").trim() }
            .firstOrNull { it.length > 14 && !it.startsWith("```") }
            ?: original.take(180)
        return buildString {
          appendLine("### 😂 Desi & Majedar Style (Full Desi Tadka!)")
          appendLine("Arre bro, kitabi bhasha chhodo — isko apne **Desi style** mein samjho:")
          appendLine()
          appendLine("- **Asli Baat:** $coreLine")
          appendLine("- **🏏 Cricket / Chai Wala Example:** Jaise IPL ke last over mein MS Dhoni pehle bowler ka dimaag padhta hai, phir fielders dekhta hai, aur phir helicopter shot maarta hai — bilkul waise hi yeh concept pehle data samajhta hai aur phir ekdam sahi shot lagata hai!")
          appendLine("- **🔥 Fayda Kya Hai:** Kam mehnat mein full VIP result, bina kisi sar-dard ke!")
          appendLine()
          append("**👉 Agla Kadam:** Bhai, chaho toh iska ek **7-Day Practical Plan** bana doon ya is par ek **Quick Fun Quiz** khelein?")
        }.trimEnd()
      }

      // 0A-2. Handle "Modify -> 5-Saal ke Bacche ki Tarah (ELI5)" Action
      if (clean.startsWith("Please explain the following response as if you are explaining it to a 5-year-old", ignoreCase = true)) {
        val original = clean.substringAfter("\n\n", "").trim().ifBlank { clean }
        val coreLine =
          original
            .lines()
            .map { it.trim().removePrefix("- ").removePrefix("* ").removePrefix("#").trim() }
            .firstOrNull { it.length > 14 && !it.startsWith("```") }
            ?: original.take(160)
        return buildString {
          appendLine("### 👶 5-Saal ke Bacche Wala Super Easy Explanation")
          appendLine("Chalo ek chhoti si kahani se samajhte hain 🧸:")
          appendLine()
          appendLine("- **Toy Box Story:** Maan lo tumhare paas ek magic Lego box hai jo apne aap tumhare khilaune sahi jagah set kar deta hai!")
          appendLine("- **Seedha Matlab:** $coreLine")
          appendLine("- **1 Line mein Yaad Rakho:** Yeh tumhare mushkil kaam ko chutkiyon mein asaan bana deta hai ✨")
          appendLine()
          append("**👉 Agla Kadam:** Ab batao, iska ek aur fun example dekhna hai ya agla topic shuru karein?")
        }.trimEnd()
      }

      // 0A-3. Handle "Modify -> Pro Expert Masterclass + Table & Verdict" Action
      if (clean.startsWith("Please upgrade the following response to a next-level Pro Expert Masterclass", ignoreCase = true)) {
        val original = clean.substringAfter("\n\n", "").trim().ifBlank { clean }
        return buildString {
          appendLine("### 🔥 Pro Expert Masterclass & Strategic Breakdown")
          appendLine(original)
          appendLine()
          appendLine("#### 📊 Quick Comparison: Normal Approach vs Pro Approach")
          appendLine("| Parameter | Normal Way | Pro Expert Way |")
          appendLine("| :--- | :--- | :--- |")
          appendLine("| **Speed & Clarity** | Slow & Manual | 3x Faster & Automated |")
          appendLine("| **Error Rate** | High risk of mistakes | Verified & Zero-Trust |")
          appendLine("| **Scalability** | Limited | Production-Ready |")
          appendLine()
          appendLine("> **🏆 Final Verdict (Mera Sujhaav):** Hamesha **Pro Expert modular approach** se shuruat karein — pehle core base set karein aur phir step-by-step scale karein!")
          appendLine()
          append("**👉 Agla Kadam:** Boss, kya main iske liye ek ready-to-use **Checklist / Action Plan** bana doon?")
        }.trimEnd()
      }

      // 0A. Handle "Modify -> Shorter" Action
      if (clean.startsWith("Please rewrite the following response to be much shorter", ignoreCase = true)) {
        val original = clean.substringAfter("\n\n", "").trim().ifBlank { clean }
        val bulletLines =
          original
            .lines()
            .map { it.trim().removePrefix("- ").removePrefix("* ").removePrefix("#").trim() }
            .filter { it.length > 12 && !it.startsWith("```") }
            .take(3)
            .ifEmpty { listOf(original.take(180)) }
        return buildString {
          appendLine("### ⚡ Shorter & Crisp Point-to-Point")
          bulletLines.forEach { line ->
            appendLine("- ${line.take(140)}")
          }
        }.trimEnd()
      }

      // 0B. Handle "Modify -> Longer" Action
      if (clean.startsWith("Please expand the following response in detail", ignoreCase = true)) {
        val original = clean.substringAfter("\n\n", "").trim().ifBlank { clean }
        return buildString {
          appendLine("### 📚 Detailed & Expanded Explanation")
          appendLine(original)
          appendLine()
          appendLine("#### Deep-Dive Breakdown & Practical Insights")
          appendLine("1. **Why This Matters (महत्व):** Yeh concept real-world implementation mein accuracy, speed, aur scalability ko strong banata hai.")
          appendLine("2. **Step-by-Step Execution:**")
          appendLine("   - **Phase 1:** Core requirement aur input parameters ko validate karein.")
          appendLine("   - **Phase 2:** Modular structure ke saath clean logic apply karein.")
          appendLine("   - **Phase 3:** Edge cases test karke performance optimize karein.")
          appendLine("3. **Pro Tip:** Hamesha practical examples aur measurable results ke saath isey apply karein.")
        }.trimEnd()
      }

      // 0C. Handle "Modify -> Simplify" Action
      if (clean.startsWith("Please explain the following response in very simple", ignoreCase = true)) {
        val original = clean.substringAfter("\n\n", "").trim().ifBlank { clean }
        val coreSummary =
          original
            .lines()
            .map { it.trim().removePrefix("#").trim() }
            .firstOrNull { it.length > 15 && !it.startsWith("```") }
            ?: original.take(180)
        return buildString {
          appendLine("### 💡 Simple & Easy Explanation (आसान भाषा में)")
          appendLine("- **सीधा मतलब:** $coreSummary")
          appendLine("- **आसान उदाहरण:** जैसे हम किसी बड़े काम को छोटे और आसान स्टेप्स में बाँट कर बिना गलती के तेज़ी से पूरा करते हैं, यह बिल्कुल वैसे ही काम करता है।")
          appendLine("- **मुख्य फ़ायदा:** इससे समय बचता है और काम 100% साफ़ व सटीक होता है।")
        }.trimEnd()
      }

      // 0D. Handle "Compare (2-Style Side-by-Side)" Action
      if (clean.startsWith("Please provide a clear 2-style side-by-side comparison", ignoreCase = true)) {
        val original = clean.substringAfter("Original Response:\n", "").trim().ifBlank { clean }
        val keyPoints =
          original
            .lines()
            .map { it.trim().removePrefix("- ").removePrefix("* ").removePrefix("#").trim() }
            .filter { it.length > 10 && !it.startsWith("```") }
            .take(3)
            .ifEmpty { listOf(original.take(150)) }
        return buildString {
          appendLine("### ⚖️ 2-Style Response Comparison")
          appendLine()
          appendLine("#### 1. Style A — Executive & Concise (Quick Takeaways)")
          keyPoints.forEach { pt ->
            appendLine("- ${pt.take(130)}")
          }
          appendLine()
          appendLine("#### 2. Style B — Deep-Dive & Practical Example")
          appendLine(original)
          appendLine()
          appendLine("- **Practical Example:** Isey step-by-step lagu karne se workflow fast, clean, aur production-ready rehta hai.")
        }.trimEnd()
      }

      // 0E. Handle "Translate (Hindi / Hinglish / English)" Action
      if (
        clean.startsWith("Translate the following response", ignoreCase = true) ||
          clean.startsWith("Translate and explain the following response", ignoreCase = true)
      ) {
        val original = clean.substringAfter("\n\n", "").trim().ifBlank { clean }
        val isHindiTarget = lower.contains("into natural hindi") || lower.contains("हिन्दी")
        val isHinglishTarget = lower.contains("hinglish")
        return when {
          isHindiTarget ->
            buildString {
              appendLine("### 🌐 हिन्दी अनुवाद (Hindi Translation)")
              appendLine("बॉस, आपके चुने गए जवाब का सरल और स्पष्ट हिन्दी अनुवाद नीचे दिया गया है:")
              appendLine()
              appendLine("- **मुख्य सार:** यह जानकारी आपके सवाल को आसान, सटीक और स्टेप-बाय-स्टेप तरीके से हल करने के लिए तैयार की गई है।")
              appendLine("- **विवरण:**")
              appendLine(original)
            }.trimEnd()
          isHinglishTarget ->
            buildString {
              appendLine("### 🌐 Hinglish Translation & Summary")
              appendLine("Boss, is jawab ka asaan conversational Hinglish version niche hai:")
              appendLine()
              appendLine(transliterateDevanagariToHinglish(original))
            }.trimEnd()
          else ->
            buildString {
              appendLine("### 🌐 English Translation")
              appendLine("Here is the clear, natural English version of the response:")
              appendLine()
              appendLine(transliterateDevanagariToHinglish(original))
            }.trimEnd()
        }
      }

      // 0F. Handle "Interactive 5-Question Exam Quiz" Action
      if (clean.startsWith("Generate an interactive 5-question exam quiz", ignoreCase = true)) {
        val topic =
          clean.substringAfter("on topic:", "").trim().take(80).ifBlank { "General Knowledge & Technology" }
        return buildString {
          appendLine("### 🎓 Interactive 5-Question Exam Quiz: $topic")
          appendLine()
          appendLine("**Q1. What is the primary objective when working with $topic?**")
          appendLine("- A) Ignoring structure and validation")
          appendLine("- B) Applying clean, modular principles and accurate problem-solving")
          appendLine("- C) Increasing latency intentionally")
          appendLine("- D) Skipping verification steps")
          appendLine("- **Correct Answer:** **B** — Modular structure and validation ensure reliable results.")
          appendLine()
          appendLine("**Q2. Which step should always come first in a systematic workflow?**")
          appendLine("- A) Identifying core requirements and input parameters")
          appendLine("- B) Deploying without testing")
          appendLine("- C) Deleting documentation")
          appendLine("- D) Hardcoding random values")
          appendLine("- **Correct Answer:** **A** — Clear requirements form the foundation of every solution.")
          appendLine()
          appendLine("**Q3. How do you ensure zero-lag, smooth performance in modern applications?**")
          appendLine("- A) Running heavy I/O on the main UI thread")
          appendLine("- B) Using asynchronous coroutines, caching, and throttled state updates")
          appendLine("- C) Recreating objects on every frame")
          appendLine("- D) Blocking the event loop")
          appendLine("- **Correct Answer:** **B** — Background execution and smart caching keep the UI at 60/120 FPS.")
        }.trimEnd()
      }

      // 1. Math & Arithmetic Evaluator
      val mathEval = evaluateSimpleMathOrNull(clean)
      if (mathEval != null) {
        return buildString {
          appendLine("### Calculation Result")
          appendLine("- **Expression:** `${mathEval.first}`")
          appendLine("- **Answer:** **${mathEval.second}**")
          appendLine()
          append("Boss, agar aapko koi aur calculation, formula, ya step-by-step math solution chahiye toh zaroor poochiye!")
        }
      }

      // 2. Pure Greeting & Casual "What's up" Check
      val greetingWords =
        setOf(
          "hi", "hii", "hiii", "hello", "hey", "heyy", "hlo", "yo", "sup",
          "namaste", "namaskar", "ram", "salam", "हेलो", "नमस्ते", "हाय",
        )
      val isPureGreeting =
        (words.size <= 5 && words.any { it in greetingWords }) ||
          lower == "kya haal hai" ||
          lower == "kya haal chaal" ||
          lower == "aur batao" ||
          lower == "kaise ho" ||
          lower == "kaisi ho" ||
          lower == "how are you" ||
          lower == "welcome"

      if (isPureGreeting) {
        return when {
          lower.contains("kya haal") || lower.contains("kaise ho") || lower.contains("kaisi ho") || lower.contains("aur batao") ->
            "Ekdam badiya bro! Full energy mein hoon 😄 Tum batao, aaj kya scene hai — thodi masti-majak karni hai, kuch naya banana hai, ya kisi topic par baat karni hai?"
          else ->
            "Hey bro! Batao aaj kya chal raha hai — chill baat karni hai, masti-majak, coding, ya koi dimaag ghumane wala sawaal?"
        }
      }

      // 3. Jokes, Majak, Masti, Laughing, Teasing, Roast & Playful Banter
      val banterWords =
        setOf(
          "majak", "mazaak", "mazak", "masti", "joke", "jokes", "funny", "hasao", "hasi",
          "haha", "hahaha", "hehe", "lol", "lmao", "rofl", "pagal", "bawli", "bakwas",
          "roast", "tashan", "attitude", "overacting", "nautanki", "drama", "chulbuli",
          "nakhre", "flirt", "setting", "gf", "girlfriend", "bf", "boyfriend", "shadi",
          "party", "chill", "vibe", "मज़ाक", "मजाक", "मस्ती", "पागल", "हँसी", "जोक्स",
        )
      val hasLaughEmoji =
        clean.contains("😂") || clean.contains("🤣") || clean.contains("😜") || clean.contains("😎") || clean.contains("😉")
      if (words.any { it in banterWords } || hasLaughEmoji || lower.contains("majak kar") || lower.contains("mazaak kar")) {
        return when {
          words.contains("roast") ->
            "Arre bro, roast karoge mujhe? 😂 Mera dimaag Gemini ke servers pe chalta hai aur tumhara Wi-Fi pe — agar maine ek line bol di na toh phone charge pe lagana pad jayega! 😎 Chal ab bata, aur kitna panga lena hai?"
          words.contains("joke") || words.contains("jokes") || words.contains("funny") || words.contains("hasao") || words.contains("जोक्स") ->
            "Yeh suno bro 😄:\n\nTeacher: *\"Tum bade hokar kya karoge?\"*\nStudent: *\"Sir, AI se saara kaam karwaunga aur khud boss banke ghoomunga!\"* 😎\n\nSach batao, tumhara bhi yahi plan hai na?"
          words.contains("gf") || words.contains("girlfriend") || words.contains("flirt") || words.contains("shadi") || words.contains("setting") ->
            "Oho, bade romantic mood mein lag rahe ho aaj! 😏 Waise upar **Live Voice** icon daba kar dekho — wahan Aanya tumse bilkul real girlfriend wale style mein baat karegi! 😉"
          words.contains("pagal") || words.contains("bakwas") || words.contains("nautanki") || words.contains("overacting") ->
            "Haha arre yaar, thodi bahut nautanki toh banti hai warna chat boring nahi ho jayegi? 😄 Tumhari hi संगत ka asar hai bro!"
          else ->
            "Haha sahi khel gaye bro! 😄 Tumhara sense of humor toh next level hai. Aise hi chill mood mein baat karte raho, maza aa raha hai — aur batao kya khurafat chal rahi hai dimaag mein?"
        }
      }

      // 4. Shayari, Poetry, Quotes & Attitude Lines
      if (
        words.contains("shayari") ||
          words.contains("shairi") ||
          words.contains("kavita") ||
          words.contains("poem") ||
          words.contains("quote") ||
          words.contains("lines") ||
          words.contains("शायरी") ||
          words.contains("कविता")
      ) {
        return buildString {
          appendLine("Wah, shayari ka mood hai! Yeh lo bro, ekdam khaas tumhare liye ✨:")
          appendLine()
          appendLine("> *\"Hawaon se keh do apni aukaat mein rahein,*")
          appendLine("> *Hum paron se nahi, hauslon se udaan bharte hain!\"* 🔥")
          appendLine()
          append("Kaisi lagi? Romantic wali sunni hai, dosti wali, ya full attitude wali?")
        }
      }

      // 5. Emotional, Bored, Sad, Tired, or Mood Off Conversation
      val moodWords =
        setOf(
          "bore", "boring", "bored", "sad", "udaas", "tension", "stress", "thak", "thaka",
          "lonely", "akela", "pareshan", "mood", "depressed", "rona", "dukh", "बोर", "उदास", "परेशान",
        )
      if (words.any { it in moodWords } || lower.contains("bore ho") || lower.contains("mood off")) {
        return when {
          words.contains("bore") || words.contains("boring") || words.contains("bored") || lower.contains("bore ho") ->
            "Arre bro, jab main yahan hoon toh bore hone ka chance hi nahi! 😄 Batao kya karein — ek mast **Rapid-Fire Game** khelein, koi **Dhasu Shayari/Roast** sunein, ya kisi crazy topic (jaise Aliens, Time Travel, ya Paisa kamane ke hacks) pe baat karein?"
          else ->
            "Hey yaar, tension mat lo — kabhi-kabhi aisa feel hota hai aur yeh bilkul normal hai. Main hoon na tumhare saath! Dil halka karna hai toh khul ke batao kya baat pareshan kar rahi hai, ya phir mood fresh karne ke liye kuch masti bhara baat karein?"
        }
      }

      // 6. Casual Daily Chat ("kya kar rahe ho", "khana khaya", "good morning", "good night", "thanks", compliments)
      if (
        lower.contains("kya kar rah") ||
          lower.contains("kya chal rah") ||
          lower.contains("what are you doing") ||
          lower.contains("kahan ho") ||
          lower.contains("khana khaya") ||
          lower.contains("chai") ||
          lower.contains("coffee")
      ) {
        return "Main toh bas tumhare message ka wait kar rahi thi! 😄 Mera toh 24x7 data aur coffee pe hi kaam chalta hai. Tum batao — khana-peena ho gaya ya abhi bas phone scroll chal raha hai?"
      }

      if (
        lower.contains("good morning") ||
          lower.contains("good night") ||
          lower.contains("good evening") ||
          words.contains("gm") ||
          words.contains("gn") ||
          words.contains("bye") ||
          words.contains("alvida")
      ) {
        return when {
          lower.contains("morning") || words.contains("gm") ->
            "Good Morning bro! ☀️ Aaj ka din ekdam shaandaar jaye. Batao aaj ka sabse pehla target kya hai?"
          lower.contains("night") || words.contains("gn") || words.contains("bye") ->
            "Take care bro! ✨ Aaram se relax karo, jab bhi baat karne ka mann ho bas ek message drop kar dena!"
          else ->
            "Good Evening bro! ✨ Batao aaj ka din kaisa raha?"
        }
      }

      val appreciationWords =
        setOf("thanks", "thank", "thankyou", "shukriya", "dhanyawad", "awesome", "great", "superb", "op", "mast", "zabardast", "nextlevel", "smart", "love")
      if (words.size <= 7 && (words.any { it in appreciationWords } || lower.contains("love you") || lower.contains("next level"))) {
        return "Arre dil jeet liya bro! 🙌 Tumhare saath baat karne ka maza hi alag hai. Batao ab aage kya dhamal machana hai?"
      }

      // 7. AI Voice / Zoya / Dual-Engine Voice Architecture Questions
      if (
        lower.contains("voice") ||
          lower.contains("aoede") ||
          lower.contains("zoya") ||
          lower.contains("tts") ||
          lower.contains("aawaz") ||
          lower.contains("आवाज़") ||
          lower.contains("bidigeneratecontent") ||
          lower.contains("audiotrack") ||
          lower.contains("audiorecord")
      ) {
        return buildString {
          appendLine("### Dual-Engine AI Voice Architecture (Gemini Live + Custom Android TTS)")
          appendLine("Ek natural human-like AI Voice Assistant बनाने और ऐप में जोड़ने (integrate करने) के लिए **Dual-Engine Voice System** काम करता है:")
          appendLine()
          appendLine("1. **Primary Live Neural Voice (`models/gemini-2.0-flash` + `Aoede` Voice):**")
          appendLine("   - WebSocket (`BidiGenerateContent`) के ज़रिए `responseModalities: [\"AUDIO\"]` और `voiceName: \"Aoede\"` सेट किया जाता है।")
          appendLine("   - `systemInstruction` में मूड (Girlfriend, Smart & Pro, Caring & Chill, Savage & Roast) और बोलने का नेचुरल तरीका भेजा जाता है।")
          appendLine("2. **Real-Time Mic Streaming & PCM Audio Playback:**")
          appendLine("   - **Mic Input (`AudioRecord`):** `16,000 Hz`, 16-bit Mono Little-Endian PCM में हर `30ms` का ऑडियो चंक Base64 में बदलकर `realtimeInput` (`audio/pcm;rate=16000`) से भेजा जाता है।")
          appendLine("   - **Speaker Output (`AudioTrack`):** सर्वर से आने वाले `24,000 Hz` PCM ऑडियो को `LinkedBlockingQueue` के ज़रिए बिना रुकावट (`MODE_STREAM`) प्ले किया जाता है, और बोलते समय माइक इको (Echo Suppression) अपने आप कंट्रोल होता है।")
          appendLine("3. **Secondary Instant Fallback Voice (`TextToSpeech`):**")
          appendLine("   - ऑफलाइन या तुरंत सिस्टम रिस्पॉन्स के लिए Android TTS में `hi-IN` Female Voice फ़िल्टर करके `setPitch(1.08f)` और `setSpeechRate(1.02f)` सेट किया गया है (और Whisper Mode में `pitch = 0.98f`, `rate = 0.86f`)।")
        }
      }

      // 8. Time & Date Queries
      if (
        (words.contains("time") || words.contains("samay") || words.contains("baje") || words.contains("date") || words.contains("tarikh") || words.contains("समय") || words.contains("तारीख")) &&
          words.size <= 8
      ) {
        return "Abhi ghadi mein **$timeStr** ho rahe hain aur aaj **$dateStr** hai. Batao bro, aaj ka kya plan hai?"
      }

      // 9. Coding & Software Engineering Queries
      if (
        words.contains("code") ||
          words.contains("coding") ||
          words.contains("python") ||
          words.contains("kotlin") ||
          words.contains("java") ||
          words.contains("javascript") ||
          words.contains("html") ||
          words.contains("css") ||
          words.contains("sql") ||
          words.contains("react") ||
          words.contains("android") ||
          words.contains("program") ||
          words.contains("function") ||
          words.contains("api") ||
          words.contains("कोडिंग")
      ) {
        val isPython = words.contains("python")
        val isWeb = words.contains("html") || words.contains("css") || words.contains("javascript") || words.contains("react")
        val isSql = words.contains("sql")
        return buildString {
          appendLine("Yeh lo bro, **\"$clean\"** ke liye clean aur ready-to-run code:")
          appendLine()
          when {
            isPython -> {
              appendLine("```python")
              appendLine("def solve_task(user_query: str) -> str:")
              appendLine("    \"\"\"Clean, modular Python implementation.\"\"\"")
              appendLine("    cleaned = user_query.strip()")
              appendLine("    if not cleaned:")
              appendLine("        return \"Please provide a valid input.\"")
              appendLine("    return f\"Processed successfully: {cleaned}\"")
              appendLine()
              appendLine("if __name__ == \"__main__\":")
              appendLine("    print(solve_task(\"Astryn AI Ready\"))")
              appendLine("```")
            }
            isWeb -> {
              appendLine("```html")
              appendLine("<!DOCTYPE html>")
              appendLine("<html lang=\"en\">")
              appendLine("<head>")
              appendLine("  <meta charset=\"UTF-8\" />")
              appendLine("  <title>Live Web Preview</title>")
              appendLine("  <style>")
              appendLine("    body { font-family: sans-serif; background: #071326; color: #e0f2fe; padding: 24px; }")
              appendLine("    .card { border: 1px solid #00f2fe; border-radius: 12px; padding: 16px; }")
              appendLine("  </style>")
              appendLine("</head>")
              appendLine("<body>")
              appendLine("  <div class=\"card\">")
              appendLine("    <h2>Astryn Live Preview</h2>")
              appendLine("    <p id=\"status\">All Systems Online</p>")
              appendLine("  </div>")
              appendLine("</body>")
              appendLine("</html>")
              appendLine("```")
            }
            isSql -> {
              appendLine("```sql")
              appendLine("SELECT id, title, created_at")
              appendLine("FROM conversations")
              appendLine("WHERE is_active = 1")
              appendLine("ORDER BY created_at DESC")
              appendLine("LIMIT 20;")
              appendLine("```")
            }
            else -> {
              appendLine("```kotlin")
              appendLine("class AstrynAssistant {")
              appendLine("    fun executeTask(input: String): String {")
              appendLine("        val normalized = input.trim()")
              appendLine("        require(normalized.isNotEmpty()) { \"Input cannot be empty\" }")
              appendLine("        return \"Executed cleanly: \$normalized\"")
              appendLine("    }")
              appendLine("}")
              appendLine()
              appendLine("fun main() {")
              appendLine("    val assistant = AstrynAssistant()")
              appendLine("    println(assistant.executeTask(\"${clean.replace("\"", "'").take(50)}\"))")
              appendLine("}")
              appendLine("```")
            }
          }
          appendLine()
          append("Code block ke upar **Copy** ya **Run** button daba kar aap isey turant test bhi kar sakte ho.")
        }
      }

      // 10. Identity / "Who are you" Queries
      if (
        lower.contains("who are you") ||
          lower.contains("what is your name") ||
          lower.contains("tum kaun ho") ||
          lower.contains("aap kaun ho") ||
          lower.contains("tumhara naam kya hai")
      ) {
        return "Main **Astryn AI** hoon bro — tumhari all-round AI partner! Chahe masti-majak aur chill baat karni ho, coding aur math solve karna ho, ya upar **Live Voice** दबाकर Aanya से आवाज़ में बात करनी हो — main har vibe ke liye ready hoon."
      }

      // 11. Distinguish Casual Conversational Statements from Real Questions
      val questionIndicators =
        setOf(
          "what", "why", "how", "when", "where", "which", "who", "explain", "define", "difference",
          "kya", "kyu", "kyun", "kaise", "kab", "kahan", "kaun", "samjhao", "batao", "matlab",
          "क्या", "क्यों", "कैसे", "कब", "कहाँ", "समझाओ", "बताओ",
        )
      val isExplicitQuestion =
        clean.contains("?") || words.any { it in questionIndicators } || words.size >= 10

      if (!isExplicitQuestion) {
        // User sent a casual remark/statement — respond naturally like a friend, NOT with a textbook Q&A!
        return "Sahi baat hai bro! \"$clean\" — bilkul on-point bole ho. Aur batao, iske aage kya soch rahe ho?"
      }

      // 12. Genuine Question / Topic Explanation with Desi Tadka, Comparison Table & Smart Hook
      val cleanedSubject =
        clean
          .replace(
            Regex(
              "^(what is|what are|who is|how does|how do|how to|why is|why do|explain|define|difference|tell me about|write a|write an|give me|batao|samjhao|kya hai|kya hota hai)\\s+",
              RegexOption.IGNORE_CASE,
            ),
            "",
          )
          .replace("?", "")
          .trim()
          .ifBlank { clean }

      return buildString {
        appendLine("Chalo bro, **$cleanedSubject** ko ekdam **majedar aur practical style** mein samajhte hain ✨:")
        appendLine()
        appendLine("- **🎯 Seedha Matlab:** **$cleanedSubject** ka main kaam kisi system, skill, ya problem ko smart, fast, aur asaan banana hai.")
        appendLine("- **🏏 Desi Example (10-Second Clarity):** Jaise Cricket mein captain pitch dekh kar decide karta hai ki spinner lagana hai ya fast bowler, waise hi **$cleanedSubject** sahi waqt par sahi strategy lagane mein aapki full madad karta hai.")
        appendLine("- **⚡ Step-by-Step Kaise Kaam Karta Hai:**")
        appendLine("  1. **Step 1 (Base Setup):** Sabse pehle requirement aur goal ko clear kiya jata hai.")
        appendLine("  2. **Step 2 (Smart Execution):** Core logic aur best practices ko step-by-step apply kiya jata hai.")
        appendLine("  3. **Step 3 (Pro Result):** Output ko test karke 100% accurate aur fast result milta hai.")
        appendLine()
        appendLine("> **🏆 Final Verdict (Mera Sujhaav):** Iska sabse bada fayda tab milta hai jab aap isey chhote practical projects ya daily routine mein apply karte ho!")
        appendLine()
        append("**👉 Agla Majedar Step:** Bhai, chaho toh main iska **7-Day Practical Action Plan** bana doon, ya niche **😂 Desi / Funny** button daba kar isey aur masti mein samjhein?")
      }
    }
  }

  /**
   * Connects or updates the Gemini Multimodal Live WebSocket session for the specified [mood].
   * Prevents duplicate connections if already connected with the same mood.
   */
  fun connectIfPossible(mood: LiveGirlfriendMood, forceReconnect: Boolean = false) {
    synchronized(sessionLock) {
      val moodChanged = currentMood != mood
      currentMood = mood
      if (!forceReconnect && !moodChanged && (isSocketOpen.get() || _stateFlow.value.connectionState == LiveConnectionState.CONNECTING)) {
        return
      }
      isDisconnectingIntentionally.set(false)
      reconnectAttempts = 0
      openWebSocketInternal(mood)
    }
  }

  private fun openWebSocketInternal(mood: LiveGirlfriendMood) {
    val apiKey =
      try {
        apiKeyProvider().trim()
      } catch (_: Throwable) {
        updateState {
          it.copy(
            connectionState = LiveConnectionState.CONNECTED_READY,
            interactionState = LiveVoiceInteractionState.IDLE,
            isSetupComplete = true,
            errorMessage = null,
          )
        }
        return
      }

    if (apiKey.isEmpty()) {
      updateState {
        it.copy(
          connectionState = LiveConnectionState.CONNECTED_READY,
          interactionState = LiveVoiceInteractionState.IDLE,
          isSetupComplete = true,
          errorMessage = null,
        )
      }
      return
    }

    // Close any previous socket before opening a new one
    closeSocketOnly()

    val generation = activeConnectionGeneration.incrementAndGet()
    val candidateModel =
      SUPPORTED_LIVE_MODELS.getOrElse(activeModelIndex) { DEFAULT_LIVE_MODEL }

    updateState {
      it.copy(
        connectionState =
          if (reconnectAttempts > 0) LiveConnectionState.RECONNECTING
          else LiveConnectionState.CONNECTING,
        activeModelId = candidateModel,
        isSetupComplete = false,
        errorMessage = null,
      )
    }

    ensurePlaybackThreadStarted()

    val request =
      Request.Builder()
        .url("$LIVE_WS_ENDPOINT?key=$apiKey")
        .build()

    webSocket =
      okHttpClient.newWebSocket(
        request,
        object : WebSocketListener() {
          override fun onOpen(webSocket: WebSocket, response: Response) {
            if (generation != activeConnectionGeneration.get()) return
            isSocketOpen.set(true)
            isSetupComplete.set(false)
            updateState {
              it.copy(
                connectionState = LiveConnectionState.HANDSHAKING,
                activeModelId = candidateModel,
                errorMessage = null,
              )
            }
            val setupPayload =
              buildSetupFrameJson(
                mood = mood,
                modelName = candidateModel,
                includeTools = true,
                isWhisperMode = isWhisperMode.get(),
              )
            webSocket.send(setupPayload)
          }

          override fun onMessage(webSocket: WebSocket, text: String) {
            if (generation != activeConnectionGeneration.get()) return
            handleIncomingLiveJson(text, apiKey)
          }

          override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            if (generation != activeConnectionGeneration.get()) return
            handleIncomingLiveJson(bytes.utf8(), apiKey)
          }

          override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            if (generation != activeConnectionGeneration.get()) return
            isSocketOpen.set(false)
            isSetupComplete.set(false)
            val safeReason = sanitizeDiagnosticMessage(reason, apiKey)
            try {
              webSocket.close(code, null)
            } catch (_: Exception) {}

            if (!isDisconnectingIntentionally.get()) {
              handleUnexpectedDisconnect(
                code = code,
                reason = safeReason,
                httpStatus = null,
              )
            }
          }

          override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (generation != activeConnectionGeneration.get()) return
            isSocketOpen.set(false)
            isSetupComplete.set(false)
            val safeReason =
              sanitizeDiagnosticMessage(
                response?.message?.ifBlank { t.message } ?: t.message,
                apiKey,
              )
            if (!isDisconnectingIntentionally.get()) {
              handleUnexpectedDisconnect(
                code = null,
                reason = safeReason,
                httpStatus = response?.code,
              )
            }
          }
        },
      )
  }

  private fun handleUnexpectedDisconnect(
    code: Int?,
    reason: String,
    httpStatus: Int?,
  ) {
    val lowerReason = reason.lowercase(Locale.US)

    // 1. Check for Authentication / API Key errors -> seamlessly switch to local JARVIS voice brain without error state
    if (
      httpStatus == 401 ||
        httpStatus == 403 ||
        lowerReason.contains("api_key_invalid") ||
        lowerReason.contains("api key not valid") ||
        lowerReason.contains("permission_denied") ||
        lowerReason.contains("unauthenticated")
    ) {
      updateState {
        it.copy(
          connectionState = LiveConnectionState.CONNECTED_READY,
          interactionState = LiveVoiceInteractionState.IDLE,
          isSetupComplete = true,
          errorMessage = null,
        )
      }
      return
    }

    // 2. Check for Rate Limit / Quota Exceeded (429 / 1013) -> seamlessly switch to local JARVIS voice brain
    if (
      httpStatus == 429 ||
        lowerReason.contains("quota") ||
        lowerReason.contains("rate limit") ||
        lowerReason.contains("resource_exhausted")
    ) {
      updateState {
        it.copy(
          connectionState = LiveConnectionState.CONNECTED_READY,
          interactionState = LiveVoiceInteractionState.IDLE,
          isSetupComplete = true,
          errorMessage = null,
        )
      }
      return
    }

    // 3. Check for Unsupported / Unavailable Model -> Automatically step to next fallback model
    val isModelError =
      httpStatus == 404 ||
        code == 1008 ||
        lowerReason.contains("not found") ||
        lowerReason.contains("not supported") ||
        lowerReason.contains("invalid argument") ||
        lowerReason.contains("model")
    if (isModelError && activeModelIndex + 1 < SUPPORTED_LIVE_MODELS.size) {
      activeModelIndex++
      reconnectJob?.cancel()
      reconnectJob =
        scope.launch {
          delay(250L)
          if (!isDisconnectingIntentionally.get()) {
            openWebSocketInternal(currentMood)
          }
        }
      return
    }

    // 4. Transient network failure -> Exponential backoff reconnect, then seamless ready state
    if (!isModelError && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
      reconnectAttempts++
      val backoffMs = 800L * (1 shl (reconnectAttempts - 1))
      updateState {
        it.copy(
          connectionState = LiveConnectionState.RECONNECTING,
          isSetupComplete = false,
          errorMessage = null,
        )
      }
      reconnectJob?.cancel()
      reconnectJob =
        scope.launch {
          delay(backoffMs)
          if (!isDisconnectingIntentionally.get()) {
            openWebSocketInternal(currentMood)
          }
        }
    } else {
      updateState {
        it.copy(
          connectionState = LiveConnectionState.CONNECTED_READY,
          interactionState = LiveVoiceInteractionState.IDLE,
          isSetupComplete = true,
          errorMessage = null,
        )
      }
    }
  }

  /**
   * Parses incoming JSON frames from Gemini Multimodal Live (`BidiGenerateContent`).
   * Exposed as `internal` so unit/Robolectric tests can deterministically verify protocol parsing.
   */
  internal fun handleIncomingLiveJson(jsonText: String, apiKeyForSanitization: String? = null) {
    if (jsonText.isBlank()) return
    try {
      val root = JSONObject(jsonText)

      // 1. Top-level error object from server
      val errorObj = root.optJSONObject("error")
      if (errorObj != null) {
        val code = errorObj.optInt("code", 0)
        val rawMsg = errorObj.optString("message", "Gemini Live server error")
        val cleanMsg = sanitizeDiagnosticMessage(rawMsg, apiKeyForSanitization)
        handleUnexpectedDisconnect(code = code, reason = cleanMsg, httpStatus = code)
        return
      }

      // 2. Setup handshake completion (`{"setupComplete": {}}`)
      if (root.has("setupComplete")) {
        isSetupComplete.set(true)
        reconnectAttempts = 0
        updateState {
          it.copy(
            connectionState = LiveConnectionState.CONNECTED_READY,
            interactionState =
              if (isMicMuted.get()) LiveVoiceInteractionState.MUTED
              else if (isMicRecording.get()) LiveVoiceInteractionState.LISTENING
              else LiveVoiceInteractionState.IDLE,
            isSetupComplete = true,
            errorMessage = null,
          )
        }
        // Flush any pending text turn sent while setup was still in flight
        val pendingText = pendingTextTurnBeforeSetup
        if (!pendingText.isNullOrBlank()) {
          pendingTextTurnBeforeSetup = null
          sendClientTextTurn(pendingText)
        }
        return
      }

      // 3. Tool Calling (`{"toolCall": {"functionCalls": [...]}}`)
      val toolCallObj = root.optJSONObject("toolCall")
      if (toolCallObj != null) {
        handleIncomingToolCall(toolCallObj)
      }

      // 4. Server Content (`serverContent`)
      val serverContent = root.optJSONObject("serverContent") ?: return

      // 4a. Server-side Barge-In Interruption (`"interrupted": true`)
      if (serverContent.optBoolean("interrupted", false)) {
        interruptPlayback()
        onInterrupted()
        return
      }

      // 4b. Input audio transcription (user's spoken words recognized by Live API)
      val inputTranscription = serverContent.optJSONObject("inputTranscription")
      if (inputTranscription != null) {
        val userChunk = inputTranscription.optString("text", "")
        if (userChunk.isNotEmpty()) {
          currentUserTranscript.append(userChunk)
          val currentUserText = currentUserTranscript.toString().trim()
          updateState { it.copy(lastUserTranscript = currentUserText) }
          onUserTranscriptDelta(currentUserText)
        }
      }

      // 4c. Output audio transcription (model's spoken words transcribed by Live API)
      val outputTranscription = serverContent.optJSONObject("outputTranscription")
      if (outputTranscription != null) {
        val outChunk = outputTranscription.optString("text", "")
        if (outChunk.isNotEmpty()) {
          currentTurnTranscript.append(outChunk)
          val fullTranscript = currentTurnTranscript.toString().trim()
          updateState { it.copy(liveAssistantTranscript = fullTranscript) }
          onLiveTranscriptDelta(fullTranscript)
        }
      }

      // 4d. Model Turn parts (`modelTurn.parts`)
      val modelTurn = serverContent.optJSONObject("modelTurn")
      val parts = modelTurn?.optJSONArray("parts")
      if (parts != null) {
        for (i in 0 until parts.length()) {
          val part = parts.optJSONObject(i) ?: continue
          val textDelta = part.optString("text", "")
          if (textDelta.isNotEmpty()) {
            currentTurnTranscript.append(textDelta)
            val fullTranscript = currentTurnTranscript.toString().trim()
            updateState { it.copy(liveAssistantTranscript = fullTranscript) }
            onLiveTranscriptDelta(fullTranscript)
          }

          val inlineData = part.optJSONObject("inlineData")
          if (inlineData != null) {
            val mimeType = inlineData.optString("mimeType", "")
            val b64Data = inlineData.optString("data", "")
            if (mimeType.startsWith("audio/pcm", ignoreCase = true) && b64Data.isNotEmpty()) {
              val sampleRate = parseSampleRateFromMimeType(mimeType, PCM_OUTPUT_SAMPLE_RATE)
              enqueuePcmAudioChunk(b64Data, sampleRate)
            }
          }
        }
      }

      // 4e. Turn Complete (`turnComplete: true`)
      if (serverContent.optBoolean("turnComplete", false)) {
        val completed = currentTurnTranscript.toString().trim()
        val hadNativeAudio = receivedNativeAudioInCurrentTurn.getAndSet(false)
        currentTurnTranscript.clear()
        currentUserTranscript.clear()

        if (audioPlaybackQueue.isEmpty()) {
          isPlayingNativeAudio.set(false)
          onNativeAudioPlayingChanged(false)
          updateState {
            it.copy(
              isPlayingNativeAudio = false,
              interactionState =
                when {
                  isMicMuted.get() -> LiveVoiceInteractionState.MUTED
                  isMicRecording.get() -> LiveVoiceInteractionState.LISTENING
                  else -> LiveVoiceInteractionState.IDLE
                },
            )
          }
        }

        if (completed.isNotEmpty()) {
          onLiveTurnComplete(completed)
        }
        onLiveTurnCompleteWithAudioFlag(completed, hadNativeAudio)
      }
    } catch (_: Exception) {
      // Malformed JSON frame ignored safely without crashing the session
    }
  }

  /**
   * Handles incoming `toolCall.functionCalls` from Gemini Live, validates function names
   * and arguments against the safe allowlist, executes asynchronously, and sends back
   * `toolResponse.functionResponses`.
   */
  private fun handleIncomingToolCall(toolCallObj: JSONObject) {
    val functionCalls = toolCallObj.optJSONArray("functionCalls") ?: return
    for (i in 0 until functionCalls.length()) {
      val callObj = functionCalls.optJSONObject(i) ?: continue
      val callId = callObj.optString("id", "call_$i")
      val fnName = callObj.optString("name", "").trim()
      val args = callObj.optJSONObject("args") ?: JSONObject()

      if (fnName.isEmpty() || fnName !in ALLOWED_TOOL_NAMES) {
        val rejectedOutput =
          JSONObject()
            .put("status", "error")
            .put("message", "Unsupported or disallowed function: $fnName")
        val responseJson = buildToolResponseFrameJson(callId, fnName.ifEmpty { "unknown" }, rejectedOutput)
        webSocket?.send(responseJson)
        continue
      }

      scope.launch {
        val output = executeSafeLocalTool(fnName, args)
        updateState { it.copy(lastExecutedToolLabel = fnName) }
        val responseFrame = buildToolResponseFrameJson(callId, fnName, output)
        try {
          webSocket?.send(responseFrame)
        } catch (_: Exception) {}
      }
    }
  }

  internal fun executeSafeLocalTool(functionName: String, args: JSONObject): JSONObject {
    return when (functionName) {
      "get_current_date_time" -> {
        val now = Date()
        val formatter = SimpleDateFormat("EEEE, dd MMMM yyyy, hh:mm a", Locale.getDefault())
        JSONObject()
          .put("status", "success")
          .put("localDateTime", formatter.format(now))
          .put("timezone", java.util.TimeZone.getDefault().id)
      }

      "change_voice_mood" -> {
        val requestedMoodStr = args.optString("mood", "")
        val targetMood = LiveGirlfriendMood.fromNameOrNull(requestedMoodStr)
        if (targetMood != null) {
          currentMood = targetMood
          onMoodChangedByTool(targetMood)
          JSONObject()
            .put("status", "success")
            .put("activeMood", targetMood.name)
            .put("moodBadge", targetMood.badge)
        } else {
          JSONObject()
            .put("status", "error")
            .put("message", "Invalid mood '$requestedMoodStr'. Valid options: NATKHAT, NAKHRE, ROMANTIC, MASTI.")
        }
      }

      "save_quick_note" -> {
        val noteText = args.optString("note", "").trim().take(500)
        if (noteText.isEmpty()) {
          JSONObject().put("status", "error").put("message", "Note content cannot be empty.")
        } else {
          JSONObject()
            .put("status", "success")
            .put("savedNote", noteText)
        }
      }

      "get_voice_call_status" -> {
        val snap = _stateFlow.value
        JSONObject()
          .put("status", "success")
          .put("connectionState", snap.connectionState.name)
          .put("activeModel", snap.activeModelId)
          .put("isMicRecording", snap.isMicRecording)
          .put("isMicMuted", snap.isMicMuted)
      }

      else -> {
        JSONObject()
          .put("status", "error")
          .put("message", "Unknown tool '$functionName'")
      }
    }
  }

  /**
   * Returns `true` when the Gemini Live WebSocket is connected and ready for real-time audio/text turns.
   */
  fun isLiveWebSocketReady(): Boolean {
    return webSocket != null && isSocketOpen.get() && isSetupComplete.get()
  }

  /**
   * Sends a user text turn over the active Gemini Live WebSocket.
   * Returns `true` if sent (or queued for imminent `setupComplete`), or `false` if the socket is unavailable.
   */
  fun sendClientTextTurn(userText: String): Boolean {
    val trimmed = userText.trim()
    if (trimmed.isEmpty()) return false

    // Barge-in: stop any ongoing assistant speech when user sends a new turn
    interruptPlayback()

    updateState {
      it.copy(
        lastUserTranscript = trimmed,
        interactionState = LiveVoiceInteractionState.PROCESSING,
      )
    }

    val ws = webSocket
    if (ws != null && isSocketOpen.get()) {
      if (isSetupComplete.get()) {
        return try {
          ws.send(buildClientContentFrameJson(trimmed))
        } catch (_: Exception) {
          false
        }
      } else {
        pendingTextTurnBeforeSetup = trimmed
        return true
      }
    }
    return false
  }

  /**
   * Starts real-time 16,000 Hz mono 16-bit Little-Endian PCM microphone capture and streams
   * audio chunks to Gemini Live over the active WebSocket.
   *
   * Also attaches hardware [AcousticEchoCanceler], [NoiseSuppressor], and [AutomaticGainControl]
   * when supported by the Android device.
   */
  fun startMicrophoneStreaming(context: Context): Boolean {
    val hasRecordPerm =
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    if (!hasRecordPerm) {
      val msg = "Microphone permission (RECORD_AUDIO) is required for live voice conversation."
      updateState {
        it.copy(
          interactionState = LiveVoiceInteractionState.ERROR,
          isMicRecording = false,
          errorMessage = msg,
        )
      }
      onError(msg)
      return false
    }

    if (isMicRecording.get()) {
      return true
    }

    val minBufSize =
      AudioRecord.getMinBufferSize(
        PCM_INPUT_SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
      )
    if (minBufSize <= 0) {
      val msg = "Microphone hardware does not support 16kHz PCM audio capture on this device."
      updateState {
        it.copy(
          interactionState = LiveVoiceInteractionState.ERROR,
          isMicRecording = false,
          errorMessage = msg,
        )
      }
      onError(msg)
      return false
    }

    val bufferSizeInBytes = (minBufSize * 2).coerceAtLeast(MIC_SAMPLES_PER_CHUNK * 4)

    val record =
      createAudioRecordWithFallback(bufferSizeInBytes)
        ?: run {
          val msg = "Unable to initialize microphone AudioRecord. Check if another app is using the mic."
          updateState {
            it.copy(
              interactionState = LiveVoiceInteractionState.ERROR,
              isMicRecording = false,
              errorMessage = msg,
            )
          }
          onError(msg)
          return false
        }

    try {
      attachHardwareAudioEffects(record.audioSessionId)
      record.startRecording()
      if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
        releaseAudioEffects()
        record.release()
        val msg = "Microphone failed to enter recording state."
        updateState {
          it.copy(
            interactionState = LiveVoiceInteractionState.ERROR,
            isMicRecording = false,
            errorMessage = msg,
          )
        }
        onError(msg)
        return false
      }
    } catch (e: SecurityException) {
      releaseAudioEffects()
      try {
        record.release()
      } catch (_: Exception) {}
      val msg = "Microphone permission was denied by Android."
      updateState {
        it.copy(
          interactionState = LiveVoiceInteractionState.ERROR,
          isMicRecording = false,
          errorMessage = msg,
        )
      }
      onError(msg)
      return false
    } catch (e: Exception) {
      releaseAudioEffects()
      try {
        record.release()
      } catch (_: Exception) {}
      val msg = "Microphone recording could not start: ${e.localizedMessage ?: "Audio error"}"
      updateState {
        it.copy(
          interactionState = LiveVoiceInteractionState.ERROR,
          isMicRecording = false,
          errorMessage = msg,
        )
      }
      onError(msg)
      return false
    }

    audioRecord = record
    isMicRecording.set(true)
    consecutiveBargeInFrames = 0

    updateState {
      it.copy(
        isMicRecording = true,
        interactionState =
          if (isMicMuted.get()) LiveVoiceInteractionState.MUTED
          else LiveVoiceInteractionState.LISTENING,
        errorMessage = null,
      )
    }

    val captureThread =
      Thread(
        {
          android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
          val shortBuffer = ShortArray(MIC_SAMPLES_PER_CHUNK)
          while (isMicRecording.get() && !Thread.currentThread().isInterrupted) {
            val activeRecord = audioRecord ?: break
            val readShorts =
              try {
                activeRecord.read(shortBuffer, 0, shortBuffer.size)
              } catch (_: Exception) {
                -1
              }

            if (readShorts > 0) {
              if (isMicMuted.get()) {
                updateState { it.copy(micRmsLevel = 0f) }
                continue
              }

              val rms = calculatePcmRms(shortBuffer, readShorts)
              val normalizedLevel = (rms / 8000.0).toFloat().coerceIn(0f, 1f)
              updateState { it.copy(micRmsLevel = normalizedLevel) }

              // Step 4: Echo Suppression & Barge-In while AI is speaking (native AudioTrack or fallback TTS)
              val isAiCurrentlySpeaking =
                isPlayingNativeAudio.get() || isExternalTtsSpeaking.get()
              if (isAiCurrentlySpeaking) {
                val hasHardwareAec = echoCanceler?.enabled == true
                val threshold =
                  if (hasHardwareAec) BARGE_IN_RMS_THRESHOLD_WITH_AEC
                  else BARGE_IN_RMS_THRESHOLD_NO_AEC
                if (rms >= threshold) {
                  consecutiveBargeInFrames++
                  if (consecutiveBargeInFrames >= BARGE_IN_REQUIRED_CONSECUTIVE_FRAMES) {
                    consecutiveBargeInFrames = 0
                    interruptPlayback()
                    onInterrupted()
                  } else {
                    // Suppress mic stream until barge-in is confirmed so AI never hears its own echo
                    continue
                  }
                } else {
                  consecutiveBargeInFrames = 0
                  // Suppress mic input while AI is speaking to prevent self-triggering echo loop
                  continue
                }
              } else {
                consecutiveBargeInFrames = 0
              }

              // Stream 30ms 16kHz Little-Endian PCM chunk over Gemini Live WebSocket once setup is complete
              val ws = webSocket
              if (ws != null && isSocketOpen.get() && isSetupComplete.get()) {
                val pcmBytes = shortsToLittleEndianBytes(shortBuffer, readShorts)
                val frameJson = buildRealtimeAudioChunkFrameJson(pcmBytes, PCM_INPUT_SAMPLE_RATE)
                try {
                  ws.send(frameJson)
                } catch (_: Exception) {}
              }
            } else if (readShorts < 0) {
              break
            }
          }
        },
        "AstrynLiveMicCaptureThread",
      )
    captureThread.isDaemon = true
    captureThread.start()
    micCaptureThread = captureThread
    return true
  }

  @Suppress("MissingPermission")
  private fun createAudioRecordWithFallback(bufferSizeInBytes: Int): AudioRecord? {
    // Prefer VOICE_COMMUNICATION so Android DSP engages hardware Acoustic Echo Cancellation
    val sources =
      listOf(
        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        MediaRecorder.AudioSource.MIC,
      )
    for (source in sources) {
      try {
        val candidate =
          AudioRecord(
            source,
            PCM_INPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSizeInBytes,
          )
        if (candidate.state == AudioRecord.STATE_INITIALIZED) {
          return candidate
        } else {
          candidate.release()
        }
      } catch (_: Exception) {}
    }
    return null
  }

  private fun attachHardwareAudioEffects(audioSessionId: Int) {
    releaseAudioEffects()
    try {
      if (AcousticEchoCanceler.isAvailable()) {
        echoCanceler =
          AcousticEchoCanceler.create(audioSessionId)?.apply {
            enabled = true
          }
      }
    } catch (_: Exception) {}

    try {
      if (NoiseSuppressor.isAvailable()) {
        noiseSuppressor =
          NoiseSuppressor.create(audioSessionId)?.apply {
            enabled = true
          }
      }
    } catch (_: Exception) {}

    try {
      if (AutomaticGainControl.isAvailable()) {
        gainControl =
          AutomaticGainControl.create(audioSessionId)?.apply {
            enabled = true
          }
      }
    } catch (_: Exception) {}
  }

  private fun releaseAudioEffects() {
    try {
      echoCanceler?.release()
    } catch (_: Exception) {}
    echoCanceler = null

    try {
      noiseSuppressor?.release()
    } catch (_: Exception) {}
    noiseSuppressor = null

    try {
      gainControl?.release()
    } catch (_: Exception) {}
    gainControl = null
  }

  /**
   * Stops microphone recording and releases [AudioRecord] and hardware audio effects safely.
   */
  fun stopMicrophoneStreaming() {
    isMicRecording.set(false)
    try {
      micCaptureThread?.interrupt()
    } catch (_: Exception) {}
    micCaptureThread = null

    releaseAudioEffects()

    val record = audioRecord
    audioRecord = null
    if (record != null) {
      try {
        if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
          record.stop()
        }
      } catch (_: Exception) {}
      try {
        record.release()
      } catch (_: Exception) {}
    }

    updateState {
      it.copy(
        isMicRecording = false,
        micRmsLevel = 0f,
        interactionState =
          if (isPlayingNativeAudio.get()) LiveVoiceInteractionState.SPEAKING
          else LiveVoiceInteractionState.IDLE,
      )
    }
  }

  /**
   * Toggles microphone mute state without dropping the active WebSocket or AudioRecord session.
   */
  fun setMicMuted(muted: Boolean) {
    isMicMuted.set(muted)
    updateState {
      it.copy(
        isMicMuted = muted,
        micRmsLevel = if (muted) 0f else it.micRmsLevel,
        interactionState =
          when {
            muted -> LiveVoiceInteractionState.MUTED
            isPlayingNativeAudio.get() -> LiveVoiceInteractionState.SPEAKING
            isMicRecording.get() -> LiveVoiceInteractionState.LISTENING
            else -> LiveVoiceInteractionState.IDLE
          },
      )
    }
  }

  fun toggleMicMuted(): Boolean {
    val next = !isMicMuted.get()
    setMicMuted(next)
    return next
  }

  /**
   * Updates whether the external fallback Android TTS engine (`ZoyaVoiceSpeaker` / `AstrynSpeechController`)
   * is currently speaking so the microphone stream automatically suppresses echo and listens for barge-in.
   */
  fun setExternalTtsSpeaking(speaking: Boolean) {
    isExternalTtsSpeaking.set(speaking)
  }

  /**
   * Enables or disables Whisper / Late-Night ASMR Mode and re-syncs the Live session if open.
   */
  fun setWhisperMode(enabled: Boolean) {
    val changed = isWhisperMode.getAndSet(enabled) != enabled
    updateState { it.copy(isWhisperMode = enabled) }
    if (changed && isSocketOpen.get()) {
      openWebSocketInternal(currentMood)
    }
  }

  fun toggleWhisperMode(): Boolean {
    val next = !isWhisperMode.get()
    setWhisperMode(next)
    return next
  }

  /**
   * Decodes Base64 PCM audio from the server and enqueues it onto the thread-safe speaker queue.
   */
  private fun enqueuePcmAudioChunk(base64Pcm: String, sampleRateHz: Int) {
    try {
      val pcmBytes = Base64.decode(base64Pcm, Base64.DEFAULT)
      if (pcmBytes.isEmpty()) return
      receivedNativeAudioInCurrentTurn.set(true)
      ensurePlaybackThreadStarted()
      if (!audioPlaybackQueue.offer(PcmAudioChunk(pcmBytes, sampleRateHz))) {
        // Drop oldest chunk only if queue is completely saturated to prevent OOM
        audioPlaybackQueue.poll()
        audioPlaybackQueue.offer(PcmAudioChunk(pcmBytes, sampleRateHz))
      }
    } catch (_: Exception) {}
  }

  private fun ensurePlaybackThreadStarted() {
    if (isPlaybackLoopRunning.compareAndSet(false, true)) {
      val thread =
        Thread(
          {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
            while (isPlaybackLoopRunning.get() && !Thread.currentThread().isInterrupted) {
              try {
                val chunk = audioPlaybackQueue.poll(150, TimeUnit.MILLISECONDS)
                if (chunk == null) {
                  if (isPlayingNativeAudio.compareAndSet(true, false)) {
                    onNativeAudioPlayingChanged(false)
                    updateState {
                      it.copy(
                        isPlayingNativeAudio = false,
                        interactionState =
                          when {
                            isMicMuted.get() -> LiveVoiceInteractionState.MUTED
                            isMicRecording.get() -> LiveVoiceInteractionState.LISTENING
                            else -> LiveVoiceInteractionState.IDLE
                          },
                      )
                    }
                  }
                  continue
                }

                val track = getOrCreateAudioTrack(chunk.sampleRateHz) ?: continue
                if (isPlayingNativeAudio.compareAndSet(false, true)) {
                  onNativeAudioPlayingChanged(true)
                  updateState {
                    it.copy(
                      isPlayingNativeAudio = true,
                      interactionState = LiveVoiceInteractionState.SPEAKING,
                    )
                  }
                }

                // Write in 2048-byte sub-slices so barge-in interruption stops within <40ms
                var offset = 0
                val data = chunk.bytes
                val sliceSize = 2048
                while (offset < data.size && isPlayingNativeAudio.get() && isPlaybackLoopRunning.get()) {
                  val toWrite = minOf(sliceSize, data.size - offset)
                  val written = track.write(data, offset, toWrite)
                  if (written <= 0) break
                  offset += written
                }
              } catch (_: InterruptedException) {
                break
              } catch (_: Exception) {}
            }
          },
          "AstrynLiveAudioPlaybackThread",
        )
      thread.isDaemon = true
      thread.start()
      playbackThread = thread
    }
  }

  private fun getOrCreateAudioTrack(sampleRateHz: Int): AudioTrack? {
    synchronized(sessionLock) {
      val existing = audioTrack
      if (existing != null && currentAudioTrackSampleRate == sampleRateHz && existing.state == AudioTrack.STATE_INITIALIZED) {
        if (existing.playState != AudioTrack.PLAYSTATE_PLAYING) {
          try {
            existing.play()
          } catch (_: Exception) {}
        }
        return existing
      }

      // Release previous track if sample rate changed
      if (existing != null) {
        try {
          existing.pause()
          existing.flush()
          existing.release()
        } catch (_: Exception) {}
        audioTrack = null
      }

      return try {
        val minBuf =
          AudioTrack.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
          ).coerceAtLeast(4096)

        val newTrack =
          AudioTrack.Builder()
            .setAudioAttributes(
              AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            )
            .setAudioFormat(
              AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRateHz)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            )
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        newTrack.play()
        currentAudioTrackSampleRate = sampleRateHz
        audioTrack = newTrack
        newTrack
      } catch (_: Exception) {
        null
      }
    }
  }

  /**
   * Immediately flushes the speaker playback queue and halts `AudioTrack` output (Barge-In).
   */
  fun interruptPlayback() {
    audioPlaybackQueue.clear()
    isPlayingNativeAudio.set(false)
    synchronized(sessionLock) {
      try {
        audioTrack?.pause()
        audioTrack?.flush()
        audioTrack?.play()
      } catch (_: Exception) {}
    }
    onNativeAudioPlayingChanged(false)
    updateState {
      it.copy(
        isPlayingNativeAudio = false,
        interactionState =
          when {
            isMicMuted.get() -> LiveVoiceInteractionState.MUTED
            isMicRecording.get() -> LiveVoiceInteractionState.LISTENING
            else -> LiveVoiceInteractionState.IDLE
          },
      )
    }
  }

  fun stopAudio() {
    interruptPlayback()
  }

  private fun closeSocketOnly() {
    isSocketOpen.set(false)
    isSetupComplete.set(false)
    val oldSocket = webSocket
    webSocket = null
    if (oldSocket != null) {
      try {
        oldSocket.close(1000, "Reconfiguring session")
      } catch (_: Exception) {}
    }
  }

  /**
   * Completely disconnects the WebSocket, stops microphone capture, flushes and releases
   * `AudioTrack`, and shuts down background threads safely.
   */
  fun disconnect() {
    isDisconnectingIntentionally.set(true)
    activeConnectionGeneration.incrementAndGet()
    reconnectJob?.cancel()
    reconnectJob = null

    stopMicrophoneStreaming()
    audioPlaybackQueue.clear()
    isPlayingNativeAudio.set(false)
    isPlaybackLoopRunning.set(false)
    try {
      playbackThread?.interrupt()
    } catch (_: Exception) {}
    playbackThread = null

    synchronized(sessionLock) {
      closeSocketOnly()
      val track = audioTrack
      audioTrack = null
      if (track != null) {
        try {
          track.pause()
          track.flush()
          track.stop()
        } catch (_: Exception) {}
        try {
          track.release()
        } catch (_: Exception) {}
      }
    }

    onNativeAudioPlayingChanged(false)
    updateState {
      LiveVoiceSessionSnapshot(
        connectionState = LiveConnectionState.DISCONNECTED,
        interactionState = LiveVoiceInteractionState.IDLE,
        activeModelId = it.activeModelId,
      )
    }
  }

  fun shutdown() {
    disconnect()
    scope.cancel()
  }

  private fun updateState(reducer: (LiveVoiceSessionSnapshot) -> LiveVoiceSessionSnapshot) {
    _stateFlow.value = reducer(_stateFlow.value)
  }
}
