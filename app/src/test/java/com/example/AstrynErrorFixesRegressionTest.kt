package com.example

import com.example.data.api.AstrynIntelligenceEngine
import com.example.data.api.FirebaseRestAuthClient
import com.example.data.api.GeminiException
import com.example.data.api.GeminiLiveVoiceSession
import com.example.data.api.GeminiService
import com.example.data.api.LiveConnectionState
import com.example.data.api.LiveGirlfriendMood
import com.example.data.api.NetworkConnectivityMonitor
import com.example.data.local.ApiConnectionStatus
import com.example.data.local.UserApiKeyManager
import com.example.data.model.MessageStatus
import com.example.data.repository.AiPersona
import com.example.data.repository.ChatHistoryRepository
import com.example.data.repository.ResponseStyle
import com.example.data.repository.UserPreferences
import com.example.data.repository.UserPreferencesRepository
import com.example.ui.screens.chat.ChatViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AstrynErrorFixesRegressionTest {

  private lateinit var isolatedKeyManager: UserApiKeyManager
  private lateinit var isolatedPrefs: UserPreferencesRepository

  @Before
  fun setUp() {
    isolatedKeyManager = UserApiKeyManager(sharedPreferences = null)
    UserApiKeyManager.setInstanceForTesting(isolatedKeyManager)
    isolatedPrefs = UserPreferencesRepository(sharedPreferences = null)
  }

  @After
  fun tearDown() {
    UserApiKeyManager.setInstanceForTesting(null)
  }

  @Test
  fun geminiService_routesAiStudioAndVertexExpressKeysToCorrectEndpoints() {
    val aiStudioKey = "AIzaSyTestStudioKey12345678901234567890"
    val vertexExpressKey = "AQ.Ab8RN6TestVertexExpressKey1234567890"

    assertFalse(GeminiService.isVertexExpressApiKey(aiStudioKey))
    assertTrue(GeminiService.isVertexExpressApiKey(vertexExpressKey))

    assertEquals(
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
      GeminiService.resolveEndpointUrl(aiStudioKey, "gemini-2.5-flash", "generateContent"),
    )
    assertEquals(
      "https://aiplatform.googleapis.com/v1/publishers/google/models/gemini-2.5-flash:generateContent",
      GeminiService.resolveEndpointUrl(vertexExpressKey, "gemini-2.5-flash", "generateContent"),
    )
    assertEquals(
      "https://aiplatform.googleapis.com/v1/publishers/google/models/gemini-2.5-flash:streamGenerateContent?alt=sse",
      GeminiService.resolveEndpointUrl(
        vertexExpressKey,
        "gemini-2.5-flash",
        "streamGenerateContent",
        "?alt=sse",
      ),
    )
  }

  @Test
  fun testConnection_vertexExpressKeyUsesAiplatformEndpointAndThinkingSafeConfig() = runBlocking {
    val vertexKey = "AQ.Ab8RN6TestVertexExpressKey9876543210"
    var capturedUrl = ""
    var capturedKeyHeader: String? = null
    var capturedBody = ""

    val client =
      OkHttpClient.Builder()
        .addInterceptor(
          Interceptor { chain ->
            val req = chain.request()
            capturedUrl = req.url.toString()
            capturedKeyHeader = req.header("x-goog-api-key")
            val buf = Buffer()
            req.body?.writeTo(buf)
            capturedBody = buf.readUtf8()
            val okJson =
              """{"candidates":[{"content":{"parts":[{"text":"OK"}]}}]}"""
            Response.Builder()
              .request(req)
              .protocol(Protocol.HTTP_1_1)
              .code(200)
              .message("OK")
              .body(okJson.toResponseBody("application/json".toMediaType()))
              .build()
          }
        )
        .build()

    val service = GeminiService(okHttpClient = client, apiKeyProvider = { vertexKey })
    val res = service.testConnection(apiKeyOverride = vertexKey, model = "gemini-2.5-flash")

    assertTrue("Expected testConnection to succeed", res.isSuccess)
    assertEquals("OK", res.getOrNull())
    assertEquals(
      "https://aiplatform.googleapis.com/v1/publishers/google/models/gemini-2.5-flash:generateContent",
      capturedUrl,
    )
    assertEquals(vertexKey, capturedKeyHeader)

    val genConfig = JSONObject(capturedBody).getJSONObject("generationConfig")
    assertTrue("maxOutputTokens must be >= 64 for thinking models", genConfig.getInt("maxOutputTokens") >= 64)
    assertEquals(0, genConfig.getJSONObject("thinkingConfig").getInt("thinkingBudget"))
  }

  @Test
  fun testConnection_retriesWithoutThinkingConfigOn400AndListsModelsOn404WithoutLeakingKey() =
    runBlocking {
      val aiStudioKey = "AIzaSySecretUnitTestKeyShouldNeverLeak1234"
      val callCount = AtomicInteger(0)
      val seenUrls = mutableListOf<String>()

      val client =
        OkHttpClient.Builder()
          .addInterceptor(
            Interceptor { chain ->
              val req = chain.request()
              seenUrls.add(req.url.toString())
              val n = callCount.incrementAndGet()
              when {
                n == 1 ->
                  Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(400)
                    .message("Bad Request")
                    .body(
                      """{"error":{"code":400,"message":"Unknown name \"thinkingConfig\""}}"""
                        .toResponseBody("application/json".toMediaType())
                    )
                    .build()
                n == 2 ->
                  Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(404)
                    .message("Not Found")
                    .body(
                      """{"error":{"code":404,"message":"models/gemini-2.5-flash is not found for key $aiStudioKey"}}"""
                        .toResponseBody("application/json".toMediaType())
                    )
                    .build()
                else ->
                  Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                      """{"models":[{"name":"models/gemini-2.5-flash","supportedGenerationMethods":["generateContent"]},{"name":"models/gemini-2.5-flash-lite","supportedGenerationMethods":["generateContent"]}]}"""
                        .toResponseBody("application/json".toMediaType())
                    )
                    .build()
              }
            }
          )
          .build()

      val service = GeminiService(okHttpClient = client, apiKeyProvider = { aiStudioKey })
      val res = service.testConnection(apiKeyOverride = aiStudioKey, model = "gemini-2.5-flash")

      assertTrue("Expected failure on 404", res.isFailure)
      val err = res.exceptionOrNull()
      assertTrue(err is GeminiException.UnsupportedModel)
      val detail = (err as GeminiException.UnsupportedModel).detail
      assertTrue("Should list available compatible models", detail.contains("gemini-2.5-flash-lite"))
      assertFalse("Must never leak API key in diagnostics", detail.contains(aiStudioKey))
      assertTrue(
        "Should query /v1beta/models on 404",
        seenUrls.any { it.contains("/v1beta/models?pageSize=50") },
      )
    }

  @Test
  fun streamGenerateContent_routesVertexExpressKeyAndEmitsRealTokens() = runBlocking {
    val vertexKey = "AQ.Ab8RN6StreamingTestKey12345678901234"
    var capturedUrl = ""
    val client =
      OkHttpClient.Builder()
        .addInterceptor(
          Interceptor { chain ->
            val req = chain.request()
            capturedUrl = req.url.toString()
            val sse =
              "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Mera naam Astryn hai, bro!\"}]}}]}\n\n" +
                "data: [DONE]\n\n"
            Response.Builder()
              .request(req)
              .protocol(Protocol.HTTP_1_1)
              .code(200)
              .message("OK")
              .body(sse.toResponseBody("text/event-stream".toMediaType()))
              .build()
          }
        )
        .build()

    val service = GeminiService(okHttpClient = client, apiKeyProvider = { vertexKey })
    val output =
      service
        .streamGenerateContent(
          history = emptyList(),
          prompt = "tumhara Naam kya hai",
          preferences = UserPreferences(webSearchEnabled = false),
        )
        .toList()
        .joinToString("")

    assertEquals("Mera naam Astryn hai, bro!", output)
    assertEquals(
      "https://aiplatform.googleapis.com/v1/publishers/google/models/gemini-2.5-flash:streamGenerateContent?alt=sse",
      capturedUrl,
    )
  }

  @Test
  fun astrynIdentityQuestion_isClassifiedAsCasualDirectWithZeroPromptBloat() {
    val prompts = listOf("tumhara Naam kya hai", "Tumhara naam kya h?", "what is your name", "apna naam batao")
    for (p in prompts) {
      val analysis = AstrynIntelligenceEngine.analyzeIntentAndContext(p)
      assertEquals(
        "Expected CASUAL_CONVERSATION for '$p'",
        AstrynIntelligenceEngine.PrimaryIntent.CASUAL_CONVERSATION,
        analysis.primaryIntent,
      )
      assertEquals(
        AstrynIntelligenceEngine.ReasoningMode.FAST_DIRECT,
        analysis.reasoningMode,
      )
      val directive =
        AstrynIntelligenceEngine.buildSystemIntelligenceDirective(
          intentResult = analysis,
          userUid = null,
          prompt = p,
          persona = AiPersona.DEFAULT,
          responseStyle = ResponseStyle.BALANCED,
        )
      assertEquals("Simple identity question must not inject coordination bloat", "", directive)
    }

    assertTrue(GeminiService.SYSTEM_BASELINE_DIRECTIVE.contains("Astryn"))
    assertTrue(GeminiService.SYSTEM_BASELINE_DIRECTIVE.contains("ATSYN AI"))
  }

  @Test
  fun chatViewModel_surfacesRealMissingApiKeyErrorInsteadOfFakeLocalReply() {
    val testDispatcher = UnconfinedTestDispatcher()
    val vm =
      ChatViewModel(
        geminiService = GeminiService(apiKeyProvider = { throw GeminiException.MissingApiKey }),
        historyRepository = ChatHistoryRepository(),
        preferencesRepository = isolatedPrefs,
        connectivityMonitor = NetworkConnectivityMonitor(initialOnline = true),
        apiKeyManager = isolatedKeyManager,
        isApiKeyAvailable = { false },
        ioDispatcher = testDispatcher,
      )
    vm.initConversation("conv_error_check")
    vm.sendMessage("tumhara Naam kya hai")

    val lastMsg = vm.uiState.value.messages.last()
    assertEquals(MessageStatus.ERROR, lastMsg.status)
    assertEquals("API Key Needed", lastMsg.errorTitle)
    assertFalse("Must not return canned local reply on missing key", lastMsg.content.contains("Seedha Matlab"))
  }

  @Test
  fun firebaseAuth_doesNotReuseUnrelatedGeminiKeyAndDoesNotFakePasswordResetSuccess() = runBlocking {
    val singletonPrefs = UserPreferencesRepository.getInstance()
    val previousFirebaseKey = singletonPrefs.preferencesFlow.value.firebaseWebApiKey
    try {
      singletonPrefs.updateFirebaseWebApiKey("")
      FirebaseRestAuthClient.resetCachedKeyForTesting()

      val geminiOnlyKey = "AIzaSyGeminiOnlyKeyNotFirebase123456789"
      isolatedKeyManager.saveApiKey(geminiOnlyKey)
      assertTrue(isolatedKeyManager.hasSavedKey())

      // FirebaseRestAuthClient must NOT grab the unrelated Gemini key from UserApiKeyManager
      val resolvedFirebaseKey = FirebaseRestAuthClient.resolveConfiguredApiKey()
      assertTrue(
        "Firebase must not reuse unrelated Gemini API key",
        resolvedFirebaseKey != geminiOnlyKey,
      )

      // Password reset must NEVER return the old fake offline success message
      val resetResult = FirebaseRestAuthClient.sendPasswordResetEmail("nonexistent.invalid.user.test@gmail.com")
      val msg = resetResult.getOrNull() ?: resetResult.exceptionOrNull()?.message.orEmpty()
      assertFalse(
        "Password reset must never return fake offline queued success",
        msg.contains("Password recovery link queued", ignoreCase = true),
      )
      if (resolvedFirebaseKey == null) {
        // Without a Firebase key (and if auto-discovery didn't supply one), must fail honestly
        if (resetResult.isSuccess) {
          // Only possible if live init.json auto-discovery succeeded and Firebase accepted the email
          assertFalse(msg.contains("queued", ignoreCase = true))
        }
      }
    } finally {
      singletonPrefs.updateFirebaseWebApiKey(previousFirebaseKey)
      FirebaseRestAuthClient.resetCachedKeyForTesting()
    }
  }

  @Test
  fun liveVoiceSession_vertexExpressKeySwitchesImmediatelyToDeviceVoicePipeline() {
    val vertexKey = "AQ.Ab8RN6LiveVoiceTestKey12345678901234"
    val session = GeminiLiveVoiceSession(apiKeyProvider = { vertexKey })
    session.connectIfPossible(LiveGirlfriendMood.NATKHAT)

    val snap = session.stateFlow.value
    assertFalse(session.isLiveWebSocketReady())
    assertEquals(LiveConnectionState.DISCONNECTED, snap.connectionState)
    assertTrue(snap.isUsingFallbackVoiceEngine)
    session.shutdown()

    val redacted = GeminiLiveVoiceSession.sanitizeDiagnosticMessage("Error using $vertexKey", vertexKey)
    assertFalse(redacted.contains(vertexKey))
  }

  @Test
  fun userApiKeyManager_preservesSavedKeyOn404AndIncludesDiagnosticDetail() {
    val testKey = "AQ.Ab8RN6SavedKeyMustStayIntact12345678"
    val saved = isolatedKeyManager.saveApiKey(testKey)
    assertTrue(saved.isSuccess)

    isolatedKeyManager.recordConnectionFailure(
      throwable =
        GeminiException.UnsupportedModel(
          modelId = "gemini-2.5-flash",
          detail = "Model not enabled in region.",
        ),
      modelId = "gemini-2.5-flash",
    )

    val state = isolatedKeyManager.configStateFlow.value
    assertTrue("Saved key must NOT be auto-deleted on failure", state.hasSavedKey)
    assertEquals(testKey, isolatedKeyManager.getDecryptedApiKey())
    assertEquals(ApiConnectionStatus.INVALID_REQUEST_ERROR, state.connectionStatus)
    assertTrue(state.statusDetailMessage.contains("HTTP 404 Model Unavailable"))
    assertTrue(state.statusDetailMessage.contains("Model not enabled in region."))
  }

  @Test
  fun validateKeyFormat_sanitizesInvisibleCharsAndQuotesButRejectsInternalSpaces() {
    val cleanKey = "AQ.Ab8RN6CleanVertexKey12345678901234"
    val dirtyInput = "\uFEFF\"$cleanKey\"\u200B\u00A0"
    val validRes = UserApiKeyManager.validateKeyFormat(dirtyInput)
    assertTrue("Quoted/invisible-char wrapped key must validate cleanly", validRes.isValidFormat)
    assertEquals(cleanKey, validRes.cleanedKey)

    val badSpaceKey = UserApiKeyManager.validateKeyFormat("AIzaSyBad SpaceKey12345678901234567")
    assertFalse("Key with internal spaces must remain rejected", badSpaceKey.isValidFormat)
  }

  @Test
  fun streamGenerateContent_vertexExpressRetriesCleanlyWhenGroundingToolReturns400() = runBlocking {
    val vertexKey = "AQ.Ab8RN6GroundingRetryKey12345678901"
    val calls = AtomicInteger(0)
    val client =
      OkHttpClient.Builder()
        .addInterceptor(
          Interceptor { chain ->
            val req = chain.request()
            if (calls.incrementAndGet() == 1) {
              Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(400)
                .message("Bad Request")
                .body(
                  """{"error":{"code":400,"message":"Grounding via googleSearch is not enabled for this express tier"}}"""
                    .toResponseBody("application/json".toMediaType())
                )
                .build()
            } else {
              val sse =
                "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Aaj ka update ready hai!\"}]}}]}\n\n" +
                  "data: [DONE]\n\n"
              Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(sse.toResponseBody("text/event-stream".toMediaType()))
                .build()
            }
          }
        )
        .build()

    val service = GeminiService(okHttpClient = client, apiKeyProvider = { vertexKey })
    val reply =
      service
        .streamGenerateContent(
          history = emptyList(),
          prompt = "Aaj ka latest weather update kya hai?",
          preferences = UserPreferences(webSearchEnabled = true),
        )
        .toList()
        .joinToString("")

    assertEquals(2, calls.get())
    assertEquals("Aaj ka update ready hai!", reply)
  }
}
