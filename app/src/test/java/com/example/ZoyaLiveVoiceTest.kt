package com.example

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.example.data.api.GeminiLiveVoiceSession
import com.example.data.api.LiveConnectionState
import com.example.data.api.LiveGirlfriendMood
import com.example.data.api.LiveVoiceInteractionState
import com.example.service.ZoyaForegroundService
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ZoyaLiveVoiceTest {

  @Test
  fun setupFrameJson_containsModelVoiceSystemInstructionAndTools() {
    val jsonStr =
      GeminiLiveVoiceSession.buildSetupFrameJson(
        mood = LiveGirlfriendMood.NATKHAT,
        modelName = "gemini-2.5-flash-native-audio-preview-12-2025",
        includeTools = true,
      )
    val root = JSONObject(jsonStr)
    val setup = root.getJSONObject("setup")

    assertEquals(
      "models/gemini-2.5-flash-native-audio-preview-12-2025",
      setup.getString("model"),
    )

    val genConfig = setup.getJSONObject("generationConfig")
    val modalities = genConfig.getJSONArray("responseModalities")
    assertEquals("AUDIO", modalities.getString(0))

    val voiceName =
      genConfig
        .getJSONObject("speechConfig")
        .getJSONObject("voiceConfig")
        .getJSONObject("prebuiltVoiceConfig")
        .getString("voiceName")
    assertEquals("Aoede", voiceName)

    val sysText =
      setup
        .getJSONObject("systemInstruction")
        .getJSONArray("parts")
        .getJSONObject(0)
        .getString("text")
    assertTrue(sysText.contains("Aanya"))

    val fnDecls =
      setup
        .getJSONArray("tools")
        .getJSONObject(0)
        .getJSONArray("functionDeclarations")
    assertTrue(fnDecls.length() >= 3)
  }

  @Test
  fun pcm16LittleEndianAndRealtimeInputEncoding_areFormattedCorrectly() {
    // 0x0102 -> Little Endian [0x02, 0x01], -32768 (0x8000) -> [0x00, 0x80]
    val samples = shortArrayOf(0x0102.toShort(), (-32768).toShort(), 0x7FFF.toShort())
    val leBytes = GeminiLiveVoiceSession.shortsToLittleEndianBytes(samples, samples.size)

    assertEquals(6, leBytes.size)
    assertEquals(0x02.toByte(), leBytes[0])
    assertEquals(0x01.toByte(), leBytes[1])
    assertEquals(0x00.toByte(), leBytes[2])
    assertEquals(0x80.toByte(), leBytes[3])
    assertEquals(0xFF.toByte(), leBytes[4])
    assertEquals(0x7F.toByte(), leBytes[5])

    val frameJson =
      GeminiLiveVoiceSession.buildRealtimeAudioChunkFrameJson(
        pcm16LittleEndianBytes = leBytes,
        sampleRateHz = 16000,
      )
    val root = JSONObject(frameJson)
    val mediaChunk =
      root.getJSONObject("realtimeInput").getJSONArray("mediaChunks").getJSONObject(0)

    assertEquals("audio/pcm;rate=16000", mediaChunk.getString("mimeType"))
    val encodedData = mediaChunk.getString("data")
    assertFalse("Base64 must not contain newlines", encodedData.contains("\n"))
    val decoded = Base64.decode(encodedData, Base64.NO_WRAP)
    assertEquals(6, decoded.size)
    assertEquals(0x02.toByte(), decoded[0])
  }

  @Test
  fun incomingMessages_setupComplete_hindiAndEnglishTranscripts_andBargeIn() {
    var lastDelta = ""
    var completedTurn = ""
    var interruptedCalled = false

    val session =
      GeminiLiveVoiceSession(
        apiKeyProvider = { "AIzaSyTestDummyKeyForUnitTesting123456" },
        onLiveTranscriptDelta = { lastDelta = it },
        onLiveTurnComplete = { completedTurn = it },
        onInterrupted = { interruptedCalled = true },
      )

    // 1. Verify setupComplete transitions state to CONNECTED_READY
    session.handleIncomingLiveJson("""{"setupComplete": {}}""")
    assertTrue(session.stateFlow.value.isSetupComplete)
    assertEquals(LiveConnectionState.CONNECTED_READY, session.stateFlow.value.connectionState)

    // 2. Verify Hindi / Hinglish & English modelTurn + inputTranscription + outputTranscription
    session.handleIncomingLiveJson(
      """
      {
        "serverContent": {
          "inputTranscription": { "text": "Kya haal hai Aanya?" },
          "modelTurn": {
            "parts": [
              { "text": "Arre hero! Main bilkul mast hoon, " },
              { "text": "tell me how your day went!" }
            ]
          },
          "turnComplete": true
        }
      }
      """.trimIndent()
    )

    assertEquals("Kya haal hai Aanya?", session.stateFlow.value.lastUserTranscript)
    assertEquals(
      "Arre hero! Main bilkul mast hoon, tell me how your day went!",
      lastDelta,
    )
    assertEquals(
      "Arre hero! Main bilkul mast hoon, tell me how your day went!",
      completedTurn,
    )

    // 3. Verify server barge-in interruption signal
    session.handleIncomingLiveJson(
      """
      {
        "serverContent": {
          "interrupted": true
        }
      }
      """.trimIndent()
    )
    assertTrue(interruptedCalled)
    assertFalse(session.stateFlow.value.isPlayingNativeAudio)
    session.shutdown()
  }

  @Test
  fun toolCalling_validatesAndExecutesAllowedTools_andRejectsUnsafeTools() {
    var updatedMood: LiveGirlfriendMood? = null
    val session =
      GeminiLiveVoiceSession(
        apiKeyProvider = { "AIzaSyTestDummyKeyForUnitTesting123456" },
        onMoodChangedByTool = { updatedMood = it },
      )

    // Allowed tool: change_voice_mood
    val moodResult =
      session.executeSafeLocalTool(
        "change_voice_mood",
        JSONObject().put("mood", "ROMANTIC"),
      )
    assertEquals("success", moodResult.getString("status"))
    assertEquals("ROMANTIC", moodResult.getString("activeMood"))
    assertEquals(LiveGirlfriendMood.ROMANTIC, updatedMood)

    // Allowed tool: get_current_date_time
    val timeResult = session.executeSafeLocalTool("get_current_date_time", JSONObject())
    assertEquals("success", timeResult.getString("status"))
    assertTrue(timeResult.getString("localDateTime").isNotBlank())

    // Verify toolResponse frame format
    val toolResponseFrame =
      GeminiLiveVoiceSession.buildToolResponseFrameJson(
        callId = "call_99",
        functionName = "change_voice_mood",
        outputObject = moodResult,
      )
    val parsedResp =
      JSONObject(toolResponseFrame)
        .getJSONObject("toolResponse")
        .getJSONArray("functionResponses")
        .getJSONObject(0)
    assertEquals("call_99", parsedResp.getString("id"))
    assertEquals("change_voice_mood", parsedResp.getString("name"))
    assertEquals(
      "ROMANTIC",
      parsedResp.getJSONObject("response").getJSONObject("output").getString("activeMood"),
    )

    // Disallowed tool must return error status
    val unsafeResult = session.executeSafeLocalTool("execute_shell_command", JSONObject())
    assertEquals("error", unsafeResult.getString("status"))
    session.shutdown()
  }

  @Test
  fun apiKeySanitization_neverLeaksPlaintextKey() {
    val fakeSecret = "AIzaSyBwI3MuSTx9XtIhkJ1FZEvjSJ0V5rGJUe4"
    val rawError =
      "HTTP 401 Unauthorized at wss://generativelanguage.googleapis.com/ws?key=$fakeSecret with token $fakeSecret"
    val sanitized = GeminiLiveVoiceSession.sanitizeDiagnosticMessage(rawError, fakeSecret)

    assertFalse("Plaintext API key must never appear in sanitized output", sanitized.contains(fakeSecret))
    assertTrue(sanitized.contains("REDACTED") || sanitized.contains("••••"))
  }

  @Test
  fun parseSampleRateFromMimeType_extractsRateOrDefaultsTo24kHz() {
    assertEquals(24000, GeminiLiveVoiceSession.parseSampleRateFromMimeType("audio/pcm;rate=24000"))
    assertEquals(16000, GeminiLiveVoiceSession.parseSampleRateFromMimeType("audio/pcm; rate=16000"))
    assertEquals(24000, GeminiLiveVoiceSession.parseSampleRateFromMimeType("audio/pcm"))
    assertEquals(24000, GeminiLiveVoiceSession.parseSampleRateFromMimeType(null))
  }

  @Test
  fun muteToggle_andForegroundServicePermissionGuard_workSafely() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val session =
      GeminiLiveVoiceSession(
        apiKeyProvider = { "AIzaSyTestDummyKeyForUnitTesting123456" }
      )

    assertFalse(session.stateFlow.value.isMicMuted)
    val muted = session.toggleMicMuted()
    assertTrue(muted)
    assertEquals(LiveVoiceInteractionState.MUTED, session.stateFlow.value.interactionState)

    val unmuted = session.toggleMicMuted()
    assertFalse(unmuted)

    // Without RECORD_AUDIO granted in default Robolectric context, startOrUpdate must not crash
    ZoyaForegroundService.startOrUpdate(
      context = context,
      mood = LiveGirlfriendMood.NATKHAT,
      statusText = "Listening",
      isMuted = false,
    )
    ZoyaForegroundService.stop(context)
    session.shutdown()
  }

  @Test
  fun openingGreeting_andNormalChatQuestionAnswering_workAccurately() {
    assertEquals(
      "Welcome boss! How can I help you?",
      LiveGirlfriendMood.DEFAULT_OPENING_GREETING,
    )
    assertEquals(
      "Welcome boss! How can I help you?",
      LiveGirlfriendMood.NATKHAT.greetingLine,
    )

    // Ensure prompts containing 'hi' or 'kaise' inside words answer the question instead of returning a canned greeting
    val reply1 =
      GeminiLiveVoiceSession.buildSmartJarvisChatReply(
        "What is this machine learning architecture?",
        liveVoiceMood = null,
      )
    assertFalse(reply1.contains("Arey yaar, main toh bilkul mast hoon"))
    assertTrue(reply1.contains("machine learning architecture", ignoreCase = true))

    val reply2 =
      GeminiLiveVoiceSession.buildSmartJarvisChatReply(
        "AI voice kaise kaam karti hai?",
        liveVoiceMood = null,
      )
    assertFalse(reply2.contains("Arey yaar, main toh bilkul mast hoon"))
    assertTrue(reply2.contains("Aoede") && reply2.contains("Dual-Engine"))
  }
}
