package com.example.data.api

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Distinct, production-grade error categories for the AI Chat application.
 */
enum class AppErrorCategory {
  NO_INTERNET,
  CONNECTION_DROPPED,
  TIMEOUT,
  MISSING_CREDENTIALS,
  UNAUTHORIZED,
  RATE_LIMITED,
  UNSUPPORTED_MODEL,
  INVALID_PAYLOAD,
  CONTEXT_LENGTH_EXCEEDED,
  SAFETY_BLOCKED,
  SERVER_ERROR,
  MALFORMED_OR_EMPTY_RESPONSE,
  CANCELLED_OR_INTERRUPTED,
  STORAGE_ERROR,
}

/**
 * Safe, user-facing error model mapped from domain and platform exceptions.
 * Never contains raw stack traces, internal URLs, or API credentials.
 */
data class AppErrorNotice(
  val category: AppErrorCategory,
  val title: String,
  val userMessage: String,
  val actionHint: String? = null,
  val isRetryableManually: Boolean = true,
  val isAutoRetryEligible: Boolean = false,
  val retryAfterMillis: Long? = null,
)

/**
 * Bounded retry configuration with exponential backoff, jitter, and server Retry-After support.
 */
data class RetryPolicyConfig(
  val maxAttempts: Int = 3,
  val baseDelayMillis: Long = 500L,
  val maxDelayMillis: Long = 4_000L,
  val maxAutoRetryAfterMillis: Long = 5_000L,
  val jitterRatio: Double = 0.20,
) {
  /**
   * Computes the delay in milliseconds before the next retry attempt (`attemptIndex` is 1-based:
   * 1 = after first failure, 2 = after second failure).
   */
  fun computeDelayMillis(
    attemptIndex: Int,
    serverRetryAfterMillis: Long? = null,
    random: Random = Random.Default,
  ): Long {
    if (serverRetryAfterMillis != null && serverRetryAfterMillis > 0L) {
      return min(serverRetryAfterMillis, maxAutoRetryAfterMillis)
    }
    val exponential =
      (baseDelayMillis * 2.0.pow((attemptIndex - 1).coerceAtLeast(0).toDouble())).toLong()
    val capped = min(exponential, maxDelayMillis)
    if (jitterRatio <= 0.0) return capped
    val jitterSpan = (capped * jitterRatio).toLong().coerceAtLeast(1L)
    val jitterOffset = random.nextLong(-jitterSpan, jitterSpan + 1L)
    return (capped + jitterOffset).coerceIn(50L, maxDelayMillis)
  }
}

/**
 * Security-conscious diagnostic logger that strips API keys, query tokens,
 * and authorization headers before recording any diagnostic event.
 */
object SafeDiagnosticLogger {
  private const val TAG = "NovaReliability"

  private val KEY_QUERY_REGEX =
    Regex("""([?&](?:key|api_key|token|access_token)=)[^&\s"]+""", RegexOption.IGNORE_CASE)
  private val GOOGLE_API_KEY_REGEX = Regex("""AIza[0-9A-Za-z\-_]{15,}""")
  private val SK_KEY_REGEX = Regex("""\bsk-[A-Za-z0-9\-_]{16,}\b""")
  private val GOOG_HEADER_REGEX =
    Regex("""(x-goog-api-key\s*[:=]\s*)[^\s,"']+""", RegexOption.IGNORE_CASE)
  private val BEARER_TOKEN_REGEX =
    Regex("""(Bearer\s+)[A-Za-z0-9\-._~+/]+=*""", RegexOption.IGNORE_CASE)

  /**
   * Returns true if the input string appears to contain a raw API key or authorization token.
   */
  fun containsSecretCredential(input: String?): Boolean {
    if (input.isNullOrBlank()) return false
    val withoutRedactedMarkers =
      input
        .replace("[REDACTED]", "")
        .replace("[REDACTED_API_KEY]", "")
        .replace("[REDACTED_TOKEN]", "")
    return GOOGLE_API_KEY_REGEX.containsMatchIn(withoutRedactedMarkers) ||
      SK_KEY_REGEX.containsMatchIn(withoutRedactedMarkers) ||
      BEARER_TOKEN_REGEX.containsMatchIn(withoutRedactedMarkers) ||
      GOOG_HEADER_REGEX.containsMatchIn(withoutRedactedMarkers) ||
      KEY_QUERY_REGEX.containsMatchIn(withoutRedactedMarkers)
  }

  /**
   * Redacts any potential API key, URL query token, header key, or Bearer credential from a string.
   */
  fun redactSecrets(input: String?): String {
    if (input.isNullOrBlank()) return ""
    return input
      .replace(KEY_QUERY_REGEX, "$1[REDACTED]")
      .replace(GOOG_HEADER_REGEX, "$1[REDACTED]")
      .replace(GOOGLE_API_KEY_REGEX, "[REDACTED_API_KEY]")
      .replace(SK_KEY_REGEX, "[REDACTED_API_KEY]")
      .replace(BEARER_TOKEN_REGEX, "$1[REDACTED_TOKEN]")
  }

  fun logWarning(category: AppErrorCategory, diagnosticSummary: String) {
    val safeText = redactSecrets(diagnosticSummary)
    try {
      Log.w(TAG, "[${category.name}] $safeText")
    } catch (_: Throwable) {
      // Safe no-op in plain JVM unit test environments
    }
  }
}

/**
 * Centralized error mapper and retry eligibility evaluator.
 */
object ErrorClassifier {
  /**
   * Parses an HTTP `Retry-After` header value (in seconds) into milliseconds.
   */
  fun parseRetryAfterMillis(headerValue: String?): Long? {
    if (headerValue.isNullOrBlank()) return null
    val seconds = headerValue.trim().toLongOrNull() ?: return null
    if (seconds <= 0L) return null
    return (seconds * 1_000L).coerceAtMost(60_000L)
  }

  /**
   * Classifies any [Throwable] into a structured, safe [AppErrorNotice].
   */
  fun classify(
    throwable: Throwable,
    isDeviceOnline: Boolean = true,
    hadPartialStreamBeforeFailure: Boolean = false,
  ): AppErrorNotice {
    if (throwable is CancellationException) {
      return AppErrorNotice(
        category = AppErrorCategory.CANCELLED_OR_INTERRUPTED,
        title = "Generation Stopped",
        userMessage = "Response generation was stopped.",
        isRetryableManually = true,
        isAutoRetryEligible = false,
      )
    }

    return when (throwable) {
      is GeminiException.MissingApiKey ->
        AppErrorNotice(
          category = AppErrorCategory.MISSING_CREDENTIALS,
          title = "API Key Needed",
          userMessage =
            "Gemini API key is not configured. Open Settings → AI Provider & API Key to paste, save, and test your personal Google Gemini API key.",
          actionHint = "Tap 'Configure API Key' or open Settings → AI Provider & API Key.",
          isRetryableManually = true,
          isAutoRetryEligible = false,
        )

      is GeminiException.InvalidApiKey -> {
        val cleanDetail = SafeDiagnosticLogger.redactSecrets(throwable.detail).trim()
        val detailSuffix = if (cleanDetail.isNotBlank()) " ($cleanDetail)" else ""
        if (throwable.statusCode == 403) {
          AppErrorNotice(
            category = AppErrorCategory.UNAUTHORIZED,
            title = "Permission or Key Restriction (HTTP 403)",
            userMessage =
              "The configured Gemini API key was rejected with HTTP 403.$detailSuffix Verify that the Generative Language API is enabled and remove any blocking Android/IP restrictions in Google AI Studio.",
            actionHint = "Open Settings → AI Provider & API Key to replace or re-test your key.",
            isRetryableManually = false,
            isAutoRetryEligible = false,
          )
        } else {
          AppErrorNotice(
            category = AppErrorCategory.UNAUTHORIZED,
            title = "Authentication Failed (HTTP ${throwable.statusCode})",
            userMessage =
              "The configured Gemini API key was rejected (HTTP ${throwable.statusCode}).$detailSuffix Please verify or replace your key in Settings → AI Provider & API Key.",
            actionHint = "Open Settings → AI Provider & API Key to enter a valid Google AI Studio key.",
            isRetryableManually = false,
            isAutoRetryEligible = false,
          )
        }
      }

      is GeminiException.QuotaExceeded -> {
        val retryAfter = throwable.retryAfterMillis
        val waitSeconds = retryAfter?.let { (it / 1000L).coerceAtLeast(1L) }
        val waitSuffix =
          if (waitSeconds != null) " Please wait ~${waitSeconds}s before retrying."
          else " Please wait a moment and try again."
        AppErrorNotice(
          category = AppErrorCategory.RATE_LIMITED,
          title = "Rate Limit Reached",
          userMessage = "Gemini API rate limit or quota was exceeded (HTTP 429).$waitSuffix",
          actionHint = "You can also switch to Gemini 2.5 Flash Lite in Settings for higher throughput.",
          isRetryableManually = true,
          isAutoRetryEligible =
            !hadPartialStreamBeforeFailure && (retryAfter == null || retryAfter <= 5_000L),
          retryAfterMillis = retryAfter,
        )
      }

      is GeminiException.UnsupportedModel ->
        AppErrorNotice(
          category = AppErrorCategory.UNSUPPORTED_MODEL,
          title = "Model Unavailable",
          userMessage =
            "The selected model (${throwable.modelId}) is currently unavailable or unsupported for this request (HTTP 404).",
          actionHint = "Open Settings → AI Provider & API Key and select Gemini 2.5 Flash.",
          isRetryableManually = false,
          isAutoRetryEligible = false,
        )

      is GeminiException.ContextLengthExceeded ->
        AppErrorNotice(
          category = AppErrorCategory.CONTEXT_LENGTH_EXCEEDED,
          title = "Context Limit Reached",
          userMessage =
            "This message exceeds the model's context length limit. Try shortening your prompt or starting a new chat.",
          actionHint = "Your existing conversation history remains safely saved.",
          isRetryableManually = false,
          isAutoRetryEligible = false,
        )

      is GeminiException.SafetyBlocked ->
        AppErrorNotice(
          category = AppErrorCategory.SAFETY_BLOCKED,
          title = "Content Filtered",
          userMessage =
            "The response could not be completed due to safety guidelines (${throwable.reason}).",
          actionHint = "Try rephrasing your prompt.",
          isRetryableManually = false,
          isAutoRetryEligible = false,
        )

      is GeminiException.ValidationError ->
        AppErrorNotice(
          category = AppErrorCategory.INVALID_PAYLOAD,
          title = "Invalid Request",
          userMessage = SafeDiagnosticLogger.redactSecrets(throwable.message)
            .ifBlank { "The request could not be processed. Please check your message input." },
          isRetryableManually = false,
          isAutoRetryEligible = false,
        )

      is GeminiException.TimeoutError ->
        AppErrorNotice(
          category =
            if (!isDeviceOnline) AppErrorCategory.NO_INTERNET
            else if (hadPartialStreamBeforeFailure) AppErrorCategory.CONNECTION_DROPPED
            else AppErrorCategory.TIMEOUT,
          title =
            if (!isDeviceOnline) "No Internet Connection"
            else if (hadPartialStreamBeforeFailure) "Connection Interrupted"
            else "Request Timed Out",
          userMessage =
            if (!isDeviceOnline) {
              "Your device appears to be offline. Saved conversations remain readable; reconnect to send new prompts."
            } else if (hadPartialStreamBeforeFailure) {
              "The network connection timed out while streaming the response."
            } else {
              "The request to Gemini timed out. Please check your connection and try again."
            },
          isRetryableManually = true,
          isAutoRetryEligible = isDeviceOnline && !hadPartialStreamBeforeFailure,
        )

      is GeminiException.ServerError -> {
        val transientCodes = setOf(500, 502, 503, 504)
        val isTransient = throwable.statusCode in transientCodes
        AppErrorNotice(
          category = AppErrorCategory.SERVER_ERROR,
          title = "Gemini Service Unavailable",
          userMessage =
            "The Gemini service encountered a temporary server error (HTTP ${throwable.statusCode}). Please try again shortly.",
          isRetryableManually = true,
          isAutoRetryEligible = isTransient && !hadPartialStreamBeforeFailure,
          retryAfterMillis = throwable.retryAfterMillis,
        )
      }

      is GeminiException.EmptyResponse,
      is GeminiException.MalformedResponse ->
        AppErrorNotice(
          category = AppErrorCategory.MALFORMED_OR_EMPTY_RESPONSE,
          title = "Incomplete Model Output",
          userMessage =
            "The model returned an empty or unreadable response. Please tap Retry to request a new answer.",
          isRetryableManually = true,
          isAutoRetryEligible = false,
        )

      is GeminiException.OfflineError ->
        AppErrorNotice(
          category = AppErrorCategory.NO_INTERNET,
          title = "Offline • No Internet",
          userMessage =
            "No internet connection detected. You can browse your saved chat history offline, and send your draft once reconnected.",
          isRetryableManually = true,
          isAutoRetryEligible = false,
        )

      is GeminiException.NetworkError ->
        if (!isDeviceOnline) {
          AppErrorNotice(
            category = AppErrorCategory.NO_INTERNET,
            title = "Offline • No Internet",
            userMessage =
              "No internet connection detected. You can browse your saved chat history offline, and send your draft once reconnected.",
            isRetryableManually = true,
            isAutoRetryEligible = false,
          )
        } else if (hadPartialStreamBeforeFailure) {
          AppErrorNotice(
            category = AppErrorCategory.CONNECTION_DROPPED,
            title = "Connection Lost Mid-Stream",
            userMessage =
              "Network connection was interrupted while receiving the response. Partial output has been preserved.",
            isRetryableManually = true,
            isAutoRetryEligible = false,
          )
        } else {
          AppErrorNotice(
            category = AppErrorCategory.CONNECTION_DROPPED,
            title = "Network Connection Error",
            userMessage =
              "Unable to reach the Gemini service. Please check your internet connection and tap Retry.",
            isRetryableManually = true,
            isAutoRetryEligible = true,
          )
        }

      else ->
        AppErrorNotice(
          category = if (!isDeviceOnline) AppErrorCategory.NO_INTERNET else AppErrorCategory.SERVER_ERROR,
          title = if (!isDeviceOnline) "Offline • No Internet" else "Request Failed",
          userMessage =
            if (!isDeviceOnline) {
              "No internet connection detected. Reconnect to send messages."
            } else {
              "An unexpected error occurred while communicating with the AI service. Please try again."
            },
          isRetryableManually = true,
          isAutoRetryEligible = false,
        )
    }
  }
}

/**
 * Lightweight, event-driven platform connectivity monitor using Android's [ConnectivityManager].
 * Avoids polling loops and allows tests to override or drive online/offline transitions cleanly.
 */
class NetworkConnectivityMonitor(
  context: Context? = null,
  initialOnline: Boolean = true,
) {
  private val appContext = context?.applicationContext
  private val connectivityManager =
    appContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

  private var manualOverride: Boolean? = null
  private val _isOnlineFlow = MutableStateFlow(checkCurrentConnectivity(initialOnline))
  val isOnlineFlow: StateFlow<Boolean> = _isOnlineFlow.asStateFlow()

  private var networkCallbackRegistered = false

  init {
    registerCallbackSafely()
  }

  companion object {
    @Volatile private var defaultInstance: NetworkConnectivityMonitor? = null

    fun getInstance(context: Context? = null): NetworkConnectivityMonitor {
      val current = defaultInstance
      if (current != null && (context == null || current.appContext != null)) {
        return current
      }
      return synchronized(this) {
        val existing = defaultInstance
        if (existing != null && (context == null || existing.appContext != null)) {
          existing
        } else {
          NetworkConnectivityMonitor(context = context).also { defaultInstance = it }
        }
      }
    }
  }

  /**
   * Synchronously refreshes and returns whether the device has active internet capability.
   */
  fun refreshConnectivity(): Boolean {
    val online = checkCurrentConnectivity(_isOnlineFlow.value)
    _isOnlineFlow.value = online
    return online
  }

  /**
   * Allows tests or manual overrides to simulate offline/online transitions deterministically.
   */
  fun setOnlineForTesting(isOnline: Boolean?) {
    manualOverride = isOnline
    if (isOnline != null) {
      _isOnlineFlow.value = isOnline
    } else {
      _isOnlineFlow.value = checkCurrentConnectivity(true)
    }
  }

  private fun checkCurrentConnectivity(fallback: Boolean): Boolean {
    manualOverride?.let { return it }
    val cm = connectivityManager ?: return fallback
    return try {
      val activeNetwork = cm.activeNetwork ?: return false
      val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
      capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (_: Exception) {
      fallback
    }
  }

  private fun registerCallbackSafely() {
    val cm = connectivityManager ?: return
    if (networkCallbackRegistered) return
    try {
      cm.registerDefaultNetworkCallback(
        object : ConnectivityManager.NetworkCallback() {
          override fun onAvailable(network: Network) {
            _isOnlineFlow.value = true
          }

          override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
          ) {
            _isOnlineFlow.value =
              networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
          }

          override fun onLost(network: Network) {
            _isOnlineFlow.value = checkCurrentConnectivity(false)
          }
        }
      )
      networkCallbackRegistered = true
    } catch (_: Exception) {
      // Safe fallback if ConnectivityManager callback registration is unavailable in test runner
    }
  }
}
