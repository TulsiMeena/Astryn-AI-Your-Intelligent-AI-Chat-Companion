package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.Immutable
import com.example.data.api.GeminiException
import com.example.data.api.SafeDiagnosticLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Real-time connection & verification status for the user's Gemini API key.
 */
enum class ApiConnectionStatus(
  val label: String,
  val isHealthy: Boolean,
  val isError: Boolean,
) {
  NOT_CONFIGURED(
    label = "Not Configured",
    isHealthy = false,
    isError = false,
  ),
  SAVED_UNTESTED(
    label = "Saved • Ready to Test",
    isHealthy = true,
    isError = false,
  ),
  TESTING(
    label = "Testing Connection…",
    isHealthy = false,
    isError = false,
  ),
  CONNECTED(
    label = "Connected",
    isHealthy = true,
    isError = false,
  ),
  AUTH_ERROR(
    label = "Authentication Failed (HTTP 401)",
    isHealthy = false,
    isError = true,
  ),
  RESTRICTED_ERROR(
    label = "Permission / Key Restricted (HTTP 403)",
    isHealthy = false,
    isError = true,
  ),
  INVALID_REQUEST_ERROR(
    label = "Invalid Key or Request (HTTP 400/404)",
    isHealthy = false,
    isError = true,
  ),
  QUOTA_ERROR(
    label = "Quota / Rate Limit (HTTP 429)",
    isHealthy = false,
    isError = true,
  ),
  NETWORK_ERROR(
    label = "Network / Connection Error",
    isHealthy = false,
    isError = true,
  );

  companion object {
    fun fromNameSafe(name: String?): ApiConnectionStatus {
      if (name.isNullOrBlank()) return NOT_CONFIGURED
      return entries.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) } ?: NOT_CONFIGURED
    }
  }
}

/**
 * Result of client-side format validation before saving a Gemini API key.
 * Format validation checks key structure only; live access is verified via Test Connection.
 */
@Immutable
data class ApiKeyFormatValidation(
  val isValidFormat: Boolean,
  val cleanedKey: String,
  val feedbackMessage: String,
)

/**
 * Immutable UI state representing the user's configured Gemini API key status.
 * Never exposes or stores the full plaintext API key; only a masked preview (e.g. `AIza••••••••1234`)
 * is exposed to the UI after saving.
 */
@Immutable
data class ApiKeyConfigState(
  val providerName: String = "Google Gemini",
  val hasSavedKey: Boolean = false,
  val maskedKeyPreview: String? = null,
  val connectionStatus: ApiConnectionStatus = ApiConnectionStatus.NOT_CONFIGURED,
  val statusDetailMessage: String =
    "No Gemini API key configured. Paste your personal key from Google AI Studio and tap Save API Key.",
  val lastTestedModelId: String? = null,
  val lastUpdatedTimestamp: Long = 0L,
  val isKeystoreBacked: Boolean = true,
)

/**
 * Secure, user-owned Gemini API Key manager for Astryn AI.
 *
 * Security & Storage Architecture:
 * - Encrypts the user's API key at rest using `AES/GCM/NoPadding` (128-bit authentication tag,
 *   unique 12-byte random IV per save).
 * - Uses Android Keystore (`AndroidKeyStore` hardware/TEE-backed 256-bit AES key where available on
 *   Android devices) so the encryption key material is non-exportable from the system keystore.
 * - Falls back cleanly to a private 256-bit AES-GCM key when running in local JVM/Robolectric unit
 *   test environments where the `AndroidKeyStore` JCA provider is not mounted.
 * - Never writes plaintext API keys to SharedPreferences, SQLite, logs, crash reports, or UI state.
 * - Never exposes the full saved API key after saving; only [maskApiKey] (`AIza••••••••1234`) is
 *   displayed in the UI.
 */
class UserApiKeyManager(
  private val sharedPreferences: SharedPreferences? = null,
) {
  private val inMemoryStore = LinkedHashMap<String, String>()
  private var isHardwareKeystoreActive: Boolean = true

  private val _configStateFlow = MutableStateFlow(loadInitialState())
  val configStateFlow: StateFlow<ApiKeyConfigState> = _configStateFlow.asStateFlow()

  companion object {
    const val PROVIDER_GOOGLE_GEMINI = "Google Gemini"
    const val OFFICIAL_AI_STUDIO_KEY_URL = "https://aistudio.google.com/app/apikey"

    private const val PREFS_FILE_NAME = "astryn_secure_api_key_store"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEYSTORE_ALIAS = "astryn_user_gemini_key_aes_v1"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128

    private const val KEY_CIPHERTEXT_B64 = "enc_api_key_ciphertext_b64"
    private const val KEY_IV_B64 = "enc_api_key_iv_b64"
    private const val KEY_MASKED_PREVIEW = "enc_api_key_masked_preview"
    private const val KEY_CONNECTION_STATUS = "enc_api_key_conn_status"
    private const val KEY_STATUS_DETAIL = "enc_api_key_status_detail"
    private const val KEY_LAST_TESTED_MODEL = "enc_api_key_last_model"
    private const val KEY_FALLBACK_AES_SECRET_B64 = "enc_fallback_aes_secret_b64"

    private val VALID_KEY_CHARS_REGEX = Regex("""^[A-Za-z0-9\-_]{20,100}$""")

    @Volatile private var defaultInstance: UserApiKeyManager? = null

    fun getInstance(context: Context? = null): UserApiKeyManager {
      val current = defaultInstance
      if (current != null && (context == null || current.sharedPreferences != null)) {
        return current
      }
      return synchronized(this) {
        val existing = defaultInstance
        if (existing != null && (context == null || existing.sharedPreferences != null)) {
          existing
        } else {
          val prefs =
            context?.applicationContext?.getSharedPreferences(
              PREFS_FILE_NAME,
              Context.MODE_PRIVATE,
            )
          UserApiKeyManager(sharedPreferences = prefs).also { defaultInstance = it }
        }
      }
    }

    /**
     * Resets the singleton instance for isolated unit testing.
     */
    fun setInstanceForTesting(manager: UserApiKeyManager?) {
      defaultInstance = manager
    }

    /**
     * Masks an API key so the full secret is never displayed in the UI after saving.
     * Example: `AIzaSyD1234567890abcdefghijklmnop9876` -> `AIza••••••••9876`.
     */
    fun maskApiKey(rawKey: String): String {
      val trimmed = rawKey.trim()
      if (trimmed.isEmpty()) return ""
      if (trimmed.length < 12) return "••••••••"
      val prefix = trimmed.take(4)
      val suffix = trimmed.takeLast(4)
      return "${prefix}••••••••${suffix}"
    }

    /**
     * Validates the user-entered Gemini API key format prior to saving.
     * Explicitly notes that format validation checks structure only and does not prove live validity.
     */
    fun validateKeyFormat(rawInput: String): ApiKeyFormatValidation {
      val trimmed = rawInput.trim()
      if (trimmed.isEmpty()) {
        return ApiKeyFormatValidation(
          isValidFormat = false,
          cleanedKey = "",
          feedbackMessage = "Please paste your Gemini API key from Google AI Studio.",
        )
      }
      if (
        trimmed.equals("MY_GEMINI_API_KEY", ignoreCase = true) ||
          trimmed.equals("YOUR_API_KEY", ignoreCase = true) ||
          trimmed.contains("PLACEHOLDER", ignoreCase = true)
      ) {
        return ApiKeyFormatValidation(
          isValidFormat = false,
          cleanedKey = "",
          feedbackMessage =
            "Placeholder values cannot be used. Please generate a real key in Google AI Studio.",
        )
      }
      if (trimmed.any { it.isWhitespace() || it.code < 0x20 || it.code == 0x7F }) {
        return ApiKeyFormatValidation(
          isValidFormat = false,
          cleanedKey = "",
          feedbackMessage = "API key must not contain spaces, tabs, or line breaks.",
        )
      }
      if (!trimmed.startsWith("AIza")) {
        return ApiKeyFormatValidation(
          isValidFormat = false,
          cleanedKey = "",
          feedbackMessage =
            "Invalid format: Google AI Studio Gemini API keys begin with 'AIza' (for example, AIzaSy...).",
        )
      }
      if (trimmed.length < 24 || trimmed.length > 80 || !VALID_KEY_CHARS_REGEX.matches(trimmed)) {
        return ApiKeyFormatValidation(
          isValidFormat = false,
          cleanedKey = "",
          feedbackMessage =
            "Invalid length or characters: Gemini API keys are typically 39 alphanumeric characters (AIza...).",
        )
      }
      return ApiKeyFormatValidation(
        isValidFormat = true,
        cleanedKey = trimmed,
        feedbackMessage =
          "Key format looks valid (${trimmed.length} chars). Format check does not verify account access — click Save API Key, then Test Connection.",
      )
    }
  }

  /**
   * Returns true if the user has saved an encrypted Gemini API key.
   */
  fun hasSavedKey(): Boolean {
    return _configStateFlow.value.hasSavedKey && !getDecryptedApiKey().isNullOrBlank()
  }

  /**
   * Explicitly validates, encrypts via AES-GCM, and saves the user's Gemini API key.
   * Only called when the user clicks "Save API Key".
   */
  @Synchronized
  fun saveApiKey(rawInput: String): Result<ApiKeyConfigState> {
    val validation = validateKeyFormat(rawInput)
    if (!validation.isValidFormat) {
      return Result.failure(IllegalArgumentException(validation.feedbackMessage))
    }

    val cleanKey = validation.cleanedKey
    return try {
      val (ciphertextB64, ivB64) = encryptPlaintext(cleanKey)
      val masked = maskApiKey(cleanKey)
      val status = ApiConnectionStatus.SAVED_UNTESTED
      val detail =
        "API key saved and encrypted locally ($masked). Tap 'Test Connection' to verify live Gemini access."

      writeString(KEY_CIPHERTEXT_B64, ciphertextB64)
      writeString(KEY_IV_B64, ivB64)
      writeString(KEY_MASKED_PREVIEW, masked)
      writeString(KEY_CONNECTION_STATUS, status.name)
      writeString(KEY_STATUS_DETAIL, detail)

      val updatedState =
        ApiKeyConfigState(
          providerName = PROVIDER_GOOGLE_GEMINI,
          hasSavedKey = true,
          maskedKeyPreview = masked,
          connectionStatus = status,
          statusDetailMessage = detail,
          lastTestedModelId = readString(KEY_LAST_TESTED_MODEL),
          lastUpdatedTimestamp = System.currentTimeMillis(),
          isKeystoreBacked = isHardwareKeystoreActive,
        )
      _configStateFlow.value = updatedState
      Result.success(updatedState)
    } catch (e: Exception) {
      Result.failure(
        IllegalStateException("Unable to encrypt and save API key on device storage.", e)
      )
    }
  }

  /**
   * Decrypts and returns the user's saved Gemini API key in memory for authenticating
   * requests to `generativelanguage.googleapis.com`. Returns null if no key is configured.
   */
  @Synchronized
  fun getDecryptedApiKey(): String? {
    val ciphertextB64 = readString(KEY_CIPHERTEXT_B64) ?: return null
    val ivB64 = readString(KEY_IV_B64) ?: return null
    if (ciphertextB64.isBlank() || ivB64.isBlank()) return null

    return try {
      val decrypted = decryptCiphertext(ciphertextB64, ivB64).trim()
      decrypted.ifBlank { null }
    } catch (_: Exception) {
      null
    }
  }

  /**
   * Deletes the saved encrypted API key and resets the configuration state to [ApiConnectionStatus.NOT_CONFIGURED].
   */
  @Synchronized
  fun clearApiKey() {
    removeKey(KEY_CIPHERTEXT_B64)
    removeKey(KEY_IV_B64)
    removeKey(KEY_MASKED_PREVIEW)
    removeKey(KEY_CONNECTION_STATUS)
    removeKey(KEY_STATUS_DETAIL)
    removeKey(KEY_LAST_TESTED_MODEL)

    _configStateFlow.value =
      ApiKeyConfigState(
        providerName = PROVIDER_GOOGLE_GEMINI,
        hasSavedKey = false,
        maskedKeyPreview = null,
        connectionStatus = ApiConnectionStatus.NOT_CONFIGURED,
        statusDetailMessage =
          "API key cleared. Paste a personal Gemini API key from Google AI Studio to enable AI chat.",
        lastTestedModelId = null,
        lastUpdatedTimestamp = System.currentTimeMillis(),
        isKeystoreBacked = isHardwareKeystoreActive,
      )
  }

  /**
   * Marks the connection state as [ApiConnectionStatus.TESTING] while a live verification call is running.
   */
  @Synchronized
  fun markTesting(modelId: String) {
    val current = _configStateFlow.value
    _configStateFlow.value =
      current.copy(
        connectionStatus = ApiConnectionStatus.TESTING,
        statusDetailMessage = "Testing live connection with Google Gemini ($modelId)…",
        lastUpdatedTimestamp = System.currentTimeMillis(),
      )
  }

  /**
   * Records a verified live response from the Gemini API (`HTTP 200 OK`).
   * Only called after a real network request succeeds.
   */
  @Synchronized
  fun recordConnectionSuccess(modelId: String) {
    val current = _configStateFlow.value
    if (!current.hasSavedKey) return
    val status = ApiConnectionStatus.CONNECTED
    val detail = "Connected • Verified live response from $modelId."
    writeString(KEY_CONNECTION_STATUS, status.name)
    writeString(KEY_STATUS_DETAIL, detail)
    writeString(KEY_LAST_TESTED_MODEL, modelId)

    _configStateFlow.value =
      current.copy(
        connectionStatus = status,
        statusDetailMessage = detail,
        lastTestedModelId = modelId,
        lastUpdatedTimestamp = System.currentTimeMillis(),
      )
  }

  /**
   * Maps a live connection or chat failure into an accurate [ApiConnectionStatus] and
   * user-friendly diagnostic explanation without ever exposing or logging the API key.
   */
  @Synchronized
  fun recordConnectionFailure(throwable: Throwable, modelId: String) {
    val current = _configStateFlow.value
    val (newStatus, detailMessage) = classifyConnectionFailure(throwable, modelId)

    if (current.hasSavedKey) {
      writeString(KEY_CONNECTION_STATUS, newStatus.name)
      writeString(KEY_STATUS_DETAIL, detailMessage)
      writeString(KEY_LAST_TESTED_MODEL, modelId)
    }

    _configStateFlow.value =
      current.copy(
        connectionStatus = if (current.hasSavedKey) newStatus else ApiConnectionStatus.NOT_CONFIGURED,
        statusDetailMessage = detailMessage,
        lastTestedModelId = modelId,
        lastUpdatedTimestamp = System.currentTimeMillis(),
      )
  }

  private fun classifyConnectionFailure(
    throwable: Throwable,
    modelId: String,
  ): Pair<ApiConnectionStatus, String> {
    return when (throwable) {
      is GeminiException.MissingApiKey ->
        ApiConnectionStatus.NOT_CONFIGURED to
          "No Gemini API key is saved. Paste your key and tap Save API Key first."

      is GeminiException.InvalidApiKey -> {
        val cleanDetail = SafeDiagnosticLogger.redactSecrets(throwable.detail).trim()
        val detailSuffix = if (cleanDetail.isNotBlank()) " ($cleanDetail)" else ""
        when (throwable.statusCode) {
          403 ->
            ApiConnectionStatus.RESTRICTED_ERROR to
              "HTTP 403 Permission Denied: Your key is restricted or the Generative Language API is disabled for its project.$detailSuffix Check key restrictions in Google AI Studio or replace the key."
          400 ->
            ApiConnectionStatus.AUTH_ERROR to
              "HTTP 400 Invalid API Key: Google Gemini rejected this key as invalid.$detailSuffix Verify that you copied the full AIza... key from Google AI Studio."
          else ->
            ApiConnectionStatus.AUTH_ERROR to
              "HTTP 401 Authentication Failed: The API key was rejected as unauthorized or revoked.$detailSuffix Please replace your key with an active key from Google AI Studio."
        }
      }

      is GeminiException.UnsupportedModel ->
        ApiConnectionStatus.INVALID_REQUEST_ERROR to
          "HTTP 404 Model Unavailable: The selected model ($modelId) was not found or is not supported for your key. Select Gemini 2.5 Flash and try again."

      is GeminiException.ValidationError ->
        ApiConnectionStatus.INVALID_REQUEST_ERROR to
          "HTTP 400 Invalid Request: ${SafeDiagnosticLogger.redactSecrets(throwable.reason)}"

      is GeminiException.QuotaExceeded ->
        ApiConnectionStatus.QUOTA_ERROR to
          "HTTP 429 Quota or Rate Limit Exceeded: Your key is valid, but your Google AI Studio quota or rate limit was reached. Wait a moment or switch to Gemini 2.5 Flash Lite."

      is GeminiException.TimeoutError ->
        ApiConnectionStatus.NETWORK_ERROR to
          "Request Timed Out: Could not complete the connection test in time. Check your internet connection and try again."

      is GeminiException.OfflineError,
      is GeminiException.NetworkError ->
        ApiConnectionStatus.NETWORK_ERROR to
          "Network Error: Unable to reach generativelanguage.googleapis.com. Check your device's internet connection and try again."

      else ->
        ApiConnectionStatus.NETWORK_ERROR to
          "Connection test failed: ${SafeDiagnosticLogger.redactSecrets(throwable.message ?: "Unexpected service error")}."
    }
  }

  private fun loadInitialState(): ApiKeyConfigState {
    val ciphertext = readString(KEY_CIPHERTEXT_B64)
    val iv = readString(KEY_IV_B64)
    val hasEncryptedKey = !ciphertext.isNullOrBlank() && !iv.isNullOrBlank()
    if (!hasEncryptedKey) {
      return ApiKeyConfigState(
        providerName = PROVIDER_GOOGLE_GEMINI,
        hasSavedKey = false,
        maskedKeyPreview = null,
        connectionStatus = ApiConnectionStatus.NOT_CONFIGURED,
        statusDetailMessage =
          "No Gemini API key configured. Paste your personal key from Google AI Studio and tap Save API Key.",
        isKeystoreBacked = isHardwareKeystoreActive,
      )
    }

    val decrypted = getDecryptedApiKey()
    if (decrypted.isNullOrBlank()) {
      return ApiKeyConfigState(
        providerName = PROVIDER_GOOGLE_GEMINI,
        hasSavedKey = false,
        maskedKeyPreview = null,
        connectionStatus = ApiConnectionStatus.NOT_CONFIGURED,
        statusDetailMessage =
          "Saved API key could not be decrypted. Please re-enter and save your Gemini API key.",
        isKeystoreBacked = isHardwareKeystoreActive,
      )
    }

    val masked = readString(KEY_MASKED_PREVIEW)?.ifBlank { null } ?: maskApiKey(decrypted)
    val savedStatus =
      ApiConnectionStatus.fromNameSafe(readString(KEY_CONNECTION_STATUS)).let { status ->
        if (status == ApiConnectionStatus.NOT_CONFIGURED || status == ApiConnectionStatus.TESTING) {
          ApiConnectionStatus.SAVED_UNTESTED
        } else {
          status
        }
      }
    val detail =
      readString(KEY_STATUS_DETAIL)?.ifBlank { null }
        ?: "API key saved and encrypted locally ($masked)."

    return ApiKeyConfigState(
      providerName = PROVIDER_GOOGLE_GEMINI,
      hasSavedKey = true,
      maskedKeyPreview = masked,
      connectionStatus = savedStatus,
      statusDetailMessage = detail,
      lastTestedModelId = readString(KEY_LAST_TESTED_MODEL),
      lastUpdatedTimestamp = System.currentTimeMillis(),
      isKeystoreBacked = isHardwareKeystoreActive,
    )
  }

  private fun encryptPlaintext(plaintext: String): Pair<String, String> {
    val secretKey = getOrCreateAesSecretKey()
    val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, secretKey)
    val ivBytes = cipher.iv
    val ciphertextBytes = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
    val cipherB64 = Base64.encodeToString(ciphertextBytes, Base64.NO_WRAP)
    val ivB64 = Base64.encodeToString(ivBytes, Base64.NO_WRAP)
    return cipherB64 to ivB64
  }

  private fun decryptCiphertext(ciphertextB64: String, ivB64: String): String {
    val secretKey = getOrCreateAesSecretKey()
    val cipherBytes = Base64.decode(ciphertextB64, Base64.NO_WRAP)
    val ivBytes = Base64.decode(ivB64, Base64.NO_WRAP)
    val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
    val spec = GCMParameterSpec(GCM_TAG_BITS, ivBytes)
    cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
    val decryptedBytes = cipher.doFinal(cipherBytes)
    return String(decryptedBytes, StandardCharsets.UTF_8)
  }

  private fun getOrCreateAesSecretKey(): SecretKey {
    try {
      val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
      keyStore.load(null)
      val existingEntry = keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
      if (existingEntry != null) {
        isHardwareKeystoreActive = true
        return existingEntry.secretKey
      }

      val keyGenerator =
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
      val keyGenSpec =
        KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
          )
          .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
          .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
          .setKeySize(256)
          .build()
      keyGenerator.init(keyGenSpec)
      isHardwareKeystoreActive = true
      return keyGenerator.generateKey()
    } catch (_: Throwable) {
      // Fallback for local JVM / Robolectric unit tests where AndroidKeyStore JCA provider is absent
      isHardwareKeystoreActive = false
      val existingSecretB64 = readString(KEY_FALLBACK_AES_SECRET_B64)
      if (!existingSecretB64.isNullOrBlank()) {
        val decoded = Base64.decode(existingSecretB64, Base64.NO_WRAP)
        if (decoded.size == 32) {
          return SecretKeySpec(decoded, "AES")
        }
      }
      val randomBytes = ByteArray(32)
      SecureRandom().nextBytes(randomBytes)
      val encoded = Base64.encodeToString(randomBytes, Base64.NO_WRAP)
      writeString(KEY_FALLBACK_AES_SECRET_B64, encoded)
      return SecretKeySpec(randomBytes, "AES")
    }
  }

  private fun readString(key: String): String? {
    val prefs = sharedPreferences
    return if (prefs != null) {
      try {
        prefs.getString(key, null)
      } catch (_: Exception) {
        inMemoryStore[key]
      }
    } else {
      inMemoryStore[key]
    }
  }

  private fun writeString(key: String, value: String) {
    inMemoryStore[key] = value
    try {
      sharedPreferences?.edit()?.putString(key, value)?.apply()
    } catch (_: Exception) {
      // In-memory store retains value if SharedPreferences is unavailable
    }
  }

  private fun removeKey(key: String) {
    inMemoryStore.remove(key)
    try {
      sharedPreferences?.edit()?.remove(key)?.apply()
    } catch (_: Exception) {
      // Safe no-op
    }
  }
}
