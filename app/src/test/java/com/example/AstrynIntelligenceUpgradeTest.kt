package com.example

import com.example.data.api.AstrynIntelligenceEngine
import com.example.data.api.GeminiException
import com.example.data.api.GeminiLiveVoiceSession
import com.example.data.api.GeminiService
import com.example.data.local.AttachmentProcessor
import com.example.data.model.AttachmentKind
import com.example.data.model.ChatAttachment
import com.example.data.model.ChatMessage
import com.example.data.model.MessageRole
import com.example.data.model.MessageStatus
import com.example.data.repository.GeminiModelOption
import com.example.data.repository.ResponseStyle
import com.example.data.repository.UserPreferences
import com.example.ui.navigation.RecentConversationItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.charset.StandardCharsets

/**
 * ASTRYN AI — Phase 2 & Phase 3 (Prompt 1 & Prompt 2) Verification Suite.
 *
 * Verifies:
 * - Phase 3 Prompt 1: All 10 intelligence, typo/Hinglish understanding, adaptive Gemini 2.5 thinkingConfig,
 *   math verification, code fence repair, and grounding categories (`test01`–`test11`).
 * - Phase 3 Prompt 2: All 20 Multimodal Image (A1–A8), PDF (B9–B15), and Context/Memory/Isolation (C16–C20)
 *   capabilities (`test12`–`test16`).
 */
class AstrynIntelligenceUpgradeTest {

  private lateinit var geminiService: GeminiService

  @Before
  fun setUp() {
    geminiService = GeminiService(apiKeyProvider = { "AIzaSyTestKeyForUnitVerification123456" })
    AstrynIntelligenceEngine.deleteUserMemory("uid_user_a")
    AstrynIntelligenceEngine.deleteUserMemory("uid_user_b")
    AstrynIntelligenceEngine.deleteUserMemory("uid_alice")
    AstrynIntelligenceEngine.deleteUserMemory("uid_bob")
    AttachmentProcessor.clearAllSessions()
  }

  /**
   * Builds a valid binary PNG header with custom [width] and [height] in the IHDR chunk.
   */
  private fun buildValidPngBytes(width: Int = 1080, height: Int = 1920): ByteArray {
    val header = ByteArray(33)
    // PNG signature: 89 50 4E 47 0D 0A 1A 0A
    header[0] = 0x89.toByte()
    header[1] = 0x50.toByte()
    header[2] = 0x4E.toByte()
    header[3] = 0x47.toByte()
    header[4] = 0x0D.toByte()
    header[5] = 0x0A.toByte()
    header[6] = 0x1A.toByte()
    header[7] = 0x0A.toByte()
    // IHDR length (13)
    header[8] = 0
    header[9] = 0
    header[10] = 0
    header[11] = 13
    // "IHDR"
    header[12] = 'I'.code.toByte()
    header[13] = 'H'.code.toByte()
    header[14] = 'D'.code.toByte()
    header[15] = 'R'.code.toByte()
    // Width big-endian
    header[16] = ((width shr 24) and 0xFF).toByte()
    header[17] = ((width shr 16) and 0xFF).toByte()
    header[18] = ((width shr 8) and 0xFF).toByte()
    header[19] = (width and 0xFF).toByte()
    // Height big-endian
    header[20] = ((height shr 24) and 0xFF).toByte()
    header[21] = ((height shr 16) and 0xFF).toByte()
    header[22] = ((height shr 8) and 0xFF).toByte()
    header[23] = (height and 0xFF).toByte()
    // Bit depth (8), Color type (6 = RGBA)
    header[24] = 8
    header[25] = 6
    return header
  }

  /**
   * Builds a valid binary JPEG header with an SOF0 marker specifying [width] and [height].
   */
  private fun buildValidJpegBytes(width: Int = 1600, height: Int = 1200): ByteArray {
    val bytes = ByteArray(24)
    bytes[0] = 0xFF.toByte()
    bytes[1] = 0xD8.toByte() // SOI
    bytes[2] = 0xFF.toByte()
    bytes[3] = 0xC0.toByte() // SOF0
    bytes[4] = 0x00.toByte()
    bytes[5] = 0x11.toByte() // Length 17
    bytes[6] = 0x08.toByte() // Precision 8
    bytes[7] = ((height shr 8) and 0xFF).toByte()
    bytes[8] = (height and 0xFF).toByte()
    bytes[9] = ((width shr 8) and 0xFF).toByte()
    bytes[10] = (width and 0xFF).toByte()
    bytes[11] = 0x03.toByte() // 3 components
    bytes[22] = 0xFF.toByte()
    bytes[23] = 0xD9.toByte() // EOI
    return bytes
  }

  // ==========================================================================
  // 1. Simple Factual Question & Adaptive Thinking Budget (Phase 3 Section 3 & 6)
  // ==========================================================================
  @Test
  fun test01_simpleFactualQuestion_usesFastDirectModeWithZeroThinkingOverheadOnFlash() {
    val prompt = "What is the capital of Japan?"
    val analysis = AstrynIntelligenceEngine.analyzeIntentAndContext(prompt = prompt)

    assertEquals(
      AstrynIntelligenceEngine.PrimaryIntent.SIMPLE_FACTUAL_OR_DIRECT,
      analysis.primaryIntent,
    )
    assertEquals(
      AstrynIntelligenceEngine.ReasoningMode.FAST_DIRECT,
      analysis.reasoningMode,
    )

    val flashConfig =
      AstrynIntelligenceEngine.computeAdaptiveModelConfig(
        intentResult = analysis,
        selectedModel = GeminiModelOption.FLASH_2_5,
        responseStyle = ResponseStyle.BALANCED,
      )
    assertEquals("gemini-2.5-flash", flashConfig.activeModelId)
    assertEquals(
      "Simple factual questions must use thinkingBudget = 0 on gemini-2.5-flash to avoid unnecessary latency",
      0,
      flashConfig.thinkingBudgetTokens,
    )

    val proConfig =
      AstrynIntelligenceEngine.computeAdaptiveModelConfig(
        intentResult = analysis,
        selectedModel = GeminiModelOption.PRO_2_5,
        responseStyle = ResponseStyle.BALANCED,
      )
    assertEquals("gemini-2.5-pro", proConfig.activeModelId)
    assertEquals(128, proConfig.thinkingBudgetTokens)
  }

  // ==========================================================================
  // 2. Incomplete Hinglish Question & Implicit Zero-Budget Constraint
  // ==========================================================================
  @Test
  fun test02_incompleteHinglishQuestion_understandsZeroBudgetIntentAndOptions() {
    val fullPrompt = "Main apna kaam online start karna chahta hu lekin paise nahi hain."
    val incompletePrompt = "Online kaam start bina paise?"

    for (prompt in listOf(fullPrompt, incompletePrompt)) {
      val analysis = AstrynIntelligenceEngine.analyzeIntentAndContext(prompt = prompt)
      assertEquals(
        AstrynIntelligenceEngine.PrimaryIntent.LOW_BUDGET_BUSINESS_OR_CAREER,
        analysis.primaryIntent,
      )
      assertEquals(
        AstrynIntelligenceEngine.ReasoningMode.AUTONOMOUS_MULTI_STEP_PLAN,
        analysis.reasoningMode,
      )
      assertTrue(
        "Must detect implicit ₹0 budget constraint for: $prompt",
        analysis.implicitConstraints.any { it.contains("₹0") },
      )
    }

    val reply = GeminiLiveVoiceSession.buildSmartJarvisChatReply(userPrompt = fullPrompt)
    assertTrue(reply.contains("₹0"))
    assertTrue(reply.contains("Option 1") && reply.contains("Option 2"))
  }

  // ==========================================================================
  // 3. Misspelled Question Understanding (Phase 3 Section 4 & 10)
  // ==========================================================================
  @Test
  fun test03_misspelledQuestion_normalizesTyposAndRoutesToRootCauseEngineering() {
    val misspelledPrompt =
      "Mera andriod app crsh ho rha hai nulpointer eror se, kase permanantly fix karun?"
    val normalized = AstrynIntelligenceEngine.normalizeTyposAndInformalHinglish(misspelledPrompt)

    assertTrue(normalized.contains("android"))
    assertTrue(normalized.contains("crash"))
    assertTrue(normalized.contains("NullPointerException"))
    assertTrue(normalized.contains("error"))
    assertTrue(normalized.contains("kaise"))
    assertTrue(normalized.contains("permanently"))

    val analysis = AstrynIntelligenceEngine.analyzeIntentAndContext(prompt = misspelledPrompt)
    assertEquals(
      AstrynIntelligenceEngine.PrimaryIntent.CODE_DEBUGGING_AND_FIX,
      analysis.primaryIntent,
    )
    assertEquals(
      AstrynIntelligenceEngine.ReasoningMode.ROOT_CAUSE_ENGINEERING,
      analysis.reasoningMode,
    )
    assertTrue(
      analysis.implicitConstraints.any { it.contains("permanent root-cause", ignoreCase = true) },
    )
  }

  // ==========================================================================
  // 4. Complex Multi-Step Reasoning Question & Adaptive Model Config
  // ==========================================================================
  @Test
  fun test04_complexMultiStepReasoning_allocatesDeepThinkingBudgetAndRecommendsPro() {
    val prompt =
      "Create a multi-step architecture strategy and roadmap for an offline-first Android fintech app."
    val analysis = AstrynIntelligenceEngine.analyzeIntentAndContext(prompt = prompt)

    assertEquals(
      AstrynIntelligenceEngine.PrimaryIntent.AUTONOMOUS_TASK_PLANNING,
      analysis.primaryIntent,
    )
    assertEquals(
      AstrynIntelligenceEngine.ReasoningMode.AUTONOMOUS_MULTI_STEP_PLAN,
      analysis.reasoningMode,
    )

    val adaptiveConfig =
      AstrynIntelligenceEngine.computeAdaptiveModelConfig(
        intentResult = analysis,
        selectedModel = GeminiModelOption.FLASH_2_5,
        responseStyle = ResponseStyle.DETAILED,
      )
    assertEquals("gemini-2.5-pro", adaptiveConfig.recommendedModelId)
    assertEquals(1024, adaptiveConfig.thinkingBudgetTokens)
    assertEquals(4096, adaptiveConfig.maxOutputTokens)
    assertEquals(0.75, adaptiveConfig.temperature, 0.001)
  }

  // ==========================================================================
  // 5. Mathematical Question & Misspelled Discount Verification
  // ==========================================================================
  @Test
  fun test05_mathematicalQuestion_calculatesExactDiscountAndRepairsIncompleteOutput() {
    val standardPrompt = "Ek product 800 rupaye ka hai aur uspe 15% discount hai. Final price kya hoga?"
    val misspelledMathPrompt = "Ek product 800 rupye ka hai aur uspe 15% discont hai. Final price calcluate karo"

    for (prompt in listOf(standardPrompt, misspelledMathPrompt)) {
      val analysis = AstrynIntelligenceEngine.analyzeIntentAndContext(prompt = prompt)
      assertEquals(
        AstrynIntelligenceEngine.PrimaryIntent.MATH_AND_DISCOUNT_CALCULATION,
        analysis.primaryIntent,
      )
      assertNotNull(analysis.mathComputation)
      assertEquals(680.0, analysis.mathComputation!!.numericAnswer, 0.001)
      assertEquals("₹680", analysis.mathComputation!!.formattedResult)
    }

    val reply = GeminiLiveVoiceSession.buildSmartJarvisChatReply(userPrompt = standardPrompt)
    assertTrue(reply.contains("120") && reply.contains("680"))

    val analysis = AstrynIntelligenceEngine.analyzeIntentAndContext(prompt = standardPrompt)
    val candidateWithLeakedThought =
      "<think>Internal chain of thought: 800 * 0.15 = 120</think>\nDiscount ke baad price kam ho jayega."
    val verified =
      AstrynIntelligenceEngine.verifyAndRepairResponse(
        prompt = standardPrompt,
        rawResponse = candidateWithLeakedThought,
        intentResult = analysis,
      )
    assertFalse("Must strip <think> block", verified.verifiedContent.contains("<think>"))
    assertTrue("Must include verified ₹680 result", verified.verifiedContent.contains("₹680"))
  }

  // ==========================================================================
  // 6. Programming Debugging Question
  // ==========================================================================
  @Test
  fun test06_programmingDebugging_diagnosesRootCauseAndClosesCodeFences() {
    val history =
      listOf(
        ChatMessage(
          role = MessageRole.USER,
          content = "java.lang.NullPointerException: Attempt to invoke virtual method String.trim() on a null object reference",
          status = MessageStatus.SUCCESS,
        ),
      )
    val prompt = "Ye error kyu aa raha hai aur ise permanently kaise fix karun?"
    val analysis =
      AstrynIntelligenceEngine.analyzeIntentAndContext(
        prompt = prompt,
        conversationHistory = history,
      )

    assertEquals(
      AstrynIntelligenceEngine.PrimaryIntent.CODE_DEBUGGING_AND_FIX,
      analysis.primaryIntent,
    )
    val reply =
      GeminiLiveVoiceSession.buildSmartJarvisChatReply(
        userPrompt = prompt,
        conversationHistory = history,
      )
    assertTrue(reply.contains("Root Cause", ignoreCase = true))
    assertTrue(reply.contains("```kotlin"))
    assertTrue(reply.contains("NullPointerException"))

    val brokenCodeResponse = "Here is the fix:\n```kotlin\nval safe = input?.trim().orEmpty()"
    val repaired =
      AstrynIntelligenceEngine.verifyAndRepairResponse(
        prompt = prompt,
        rawResponse = brokenCodeResponse,
        intentResult = analysis,
      )
    assertEquals(2, Regex("```").findAll(repaired.verifiedContent).count())
  }

  // ==========================================================================
  // 7. Contextual Follow-Up & Ambiguous Reference Resolution
  // ==========================================================================
  @Test
  fun test07_contextualFollowUp_resolvesSecondOptionAndPreviousProblemReferences() {
    val firstPrompt = "Main apna kaam online start karna chahta hu lekin paise nahi hain."
    val firstReply = GeminiLiveVoiceSession.buildSmartJarvisChatReply(userPrompt = firstPrompt)
    val historyWithOptions =
      listOf(
        ChatMessage(role = MessageRole.USER, content = firstPrompt, status = MessageStatus.SUCCESS),
        ChatMessage(role = MessageRole.MODEL, content = firstReply, status = MessageStatus.SUCCESS),
      )

    val followUpPrompt = "Iska second option bhi batao."
    val optionAnalysis =
      AstrynIntelligenceEngine.analyzeIntentAndContext(
        prompt = followUpPrompt,
        conversationHistory = historyWithOptions,
      )
    assertEquals(
      AstrynIntelligenceEngine.PrimaryIntent.OPTION_EXPANSION_FOLLOWUP,
      optionAnalysis.primaryIntent,
    )
    assertNotNull(optionAnalysis.extractedOptionTarget)
    assertTrue(optionAnalysis.extractedOptionTarget!!.contains("Content Creation", ignoreCase = true))

    val problemPrompt = "Usmein wahi problem aa rahi hai."
    val noContextAnalysis =
      AstrynIntelligenceEngine.analyzeIntentAndContext(
        prompt = problemPrompt,
        conversationHistory = emptyList(),
      )
    assertTrue(noContextAnalysis.isAmbiguousWithoutContext)

    val errorHistory =
      listOf(
        ChatMessage(
          role = MessageRole.USER,
          content = "Mere Android Room database migration mein IllegalStateException crash aa raha hai.",
          status = MessageStatus.SUCCESS,
        ),
      )
    val withContextAnalysis =
      AstrynIntelligenceEngine.analyzeIntentAndContext(
        prompt = problemPrompt,
        conversationHistory = errorHistory,
      )
    assertFalse(withContextAnalysis.isAmbiguousWithoutContext)
    assertTrue(
      withContextAnalysis.resolvedReferences["previous_context_or_problem"]
        ?.contains("IllegalStateException") == true,
    )
  }

  // ==========================================================================
  // 8. Educational Explanation Request (Including Misspelled Input)
  // ==========================================================================
  @Test
  fun test08_educationalExplanation_adaptsToBeginnerHindiAndNormalizesSpelling() {
    val prompt = "Photosynthesys ko bilkul basic se Hindi mein smjhao."
    val analysis = AstrynIntelligenceEngine.analyzeIntentAndContext(prompt = prompt)

    assertEquals(
      AstrynIntelligenceEngine.PrimaryIntent.EDUCATIONAL_CONCEPT_EXPLANATION,
      analysis.primaryIntent,
    )
    assertEquals(
      AstrynIntelligenceEngine.DetectedLanguage.HINDI_DEVANAGARI,
      analysis.detectedLanguage,
    )
    assertEquals(
      AstrynIntelligenceEngine.UserExpertiseLevel.BEGINNER,
      analysis.expertiseLevel,
    )

    val reply = GeminiLiveVoiceSession.buildSmartJarvisChatReply(userPrompt = prompt)
    assertTrue(reply.contains("प्रकाश संश्लेषण"))
    assertTrue(reply.contains("सूरज की रोशनी") && reply.contains("क्लोरोफिल") && reply.contains("ऑक्सीजन"))
  }

  // ==========================================================================
  // 9. Creative Writing Request
  // ==========================================================================
  @Test
  fun test09_creativeWriting_respects15SecondDurationAndGeneratesOriginalConcept() {
    val prompt = "Mujhe 15 second ki funny video ka unique concept do."
    val analysis = AstrynIntelligenceEngine.analyzeIntentAndContext(prompt = prompt)

    assertEquals(
      AstrynIntelligenceEngine.PrimaryIntent.CREATIVE_CONCEPT_GENERATION,
      analysis.primaryIntent,
    )
    assertTrue(analysis.implicitConstraints.any { it.contains("15 seconds") })

    val reply = GeminiLiveVoiceSession.buildSmartJarvisChatReply(userPrompt = prompt)
    assertTrue(reply.contains("0:00") && reply.contains("0:15"))
    assertTrue(reply.contains("Hook") && reply.contains("Punchline"))
  }

  // ==========================================================================
  // 10. Current Information & Google Search Grounding
  // ==========================================================================
  @Test
  fun test10_currentInformation_triggersWebGroundingAndAvoidsUnnecessarySearchOnCasual() {
    val currentInfoPrompt = "Google ke latest AI model ke baare mein batao."
    val casualPrompt = "Hello bro kya haal hai"
    val preferences = UserPreferences(webSearchEnabled = true)

    assertTrue(GeminiService.shouldUseWebGrounding(currentInfoPrompt, emptyList(), preferences))
    assertFalse(GeminiService.shouldUseWebGrounding(casualPrompt, emptyList(), preferences))

    val analysis =
      AstrynIntelligenceEngine.analyzeIntentAndContext(
        prompt = currentInfoPrompt,
        webSearchEnabled = true,
      )
    assertEquals(
      AstrynIntelligenceEngine.PrimaryIntent.CURRENT_INFO_AND_RESEARCH,
      analysis.primaryIntent,
    )
    assertTrue(analysis.requiresWebGrounding)
  }

  // ==========================================================================
  // 11. Multi-User Memory Isolation & Multimodal Context Preservation
  // ==========================================================================
  @Test
  fun test11_multiUserMemoryAndMultimodalContext_strictlyIsolatesUsersAndPreservesAttachments() {
    val userA = "uid_user_a"
    val userB = "uid_user_b"

    AstrynIntelligenceEngine.saveOrUpdateUserMemory(
      userUid = userA,
      category = AstrynIntelligenceEngine.MemoryCategory.ONGOING_PROJECT,
      key = "fitness_tracker_app",
      value = "Building an AI Calorie & Workout Tracker app in Kotlin Compose",
    )
    AstrynIntelligenceEngine.updateUserConversationSnapshots(
      userUid = userA,
      conversations =
        listOf(
          RecentConversationItem(
            id = "conv_a1",
            title = "AI Fitness & Diet Tracker App",
            preview = "Kal humne Kotlin Compose fitness app ka database plan kiya tha.",
            updatedAt = System.currentTimeMillis(),
          ),
        ),
    )

    AstrynIntelligenceEngine.saveOrUpdateUserMemory(
      userUid = userB,
      category = AstrynIntelligenceEngine.MemoryCategory.PREFERENCE,
      key = "preferred_stack",
      value = "Prefers Python FastAPI backend tutorials",
    )

    val userBMemories = AstrynIntelligenceEngine.retrieveRelevantMemories(userB, "fitness tracker app")
    assertTrue(userBMemories.none { it.value.contains("Calorie") || it.userUid == userA })

    val recallPrompt = "Mujhe wo wala app banana hai jo kal bataya tha."
    val userARecall = AstrynIntelligenceEngine.analyzeIntentAndContext(prompt = recallPrompt, userUid = userA)
    val userBRecall = AstrynIntelligenceEngine.analyzeIntentAndContext(prompt = recallPrompt, userUid = userB)

    assertNotNull(userARecall.crossConversationMatch)
    assertTrue(userARecall.crossConversationMatch!!.contains("AI Fitness & Diet Tracker App"))
    assertNull(userBRecall.crossConversationMatch)
    assertTrue(userBRecall.unverifiedMemoryRecallRequested)

    assertFalse(
      AstrynIntelligenceEngine.saveOrUpdateUserMemory(
        userUid = userA,
        category = AstrynIntelligenceEngine.MemoryCategory.USER_CONTEXT,
        key = "secret_key",
        value = "My key is AIzaSyD9876543210abcdefghijklmnopqrstuv",
      ),
    )

    val pdfAttachment =
      ChatAttachment(
        id = "att_pdf_1",
        fileName = "architecture_spec.pdf",
        mimeType = "application/pdf",
        fileSizeBytes = 1024L,
        kind = AttachmentKind.PDF,
        inlineBase64Data = "JVBERi0xLjQK",
        extractedText = "Section 4: Three-Tier Memory Architecture and Verification Rules.",
        pageCount = 3,
      )
    val multimodalAnalysis =
      AstrynIntelligenceEngine.analyzeIntentAndContext(
        prompt = "Is PDF document ke key points summarize karo.",
        attachments = listOf(pdfAttachment),
        userUid = userA,
      )
    assertEquals(
      AstrynIntelligenceEngine.PrimaryIntent.MULTIMODAL_DOCUMENT_OR_IMAGE_ANALYSIS,
      multimodalAnalysis.primaryIntent,
    )

    var rejectedInvalidModel = false
    try {
      geminiService.validateModelId("../v1alpha/models/evil")
    } catch (_: GeminiException.UnsupportedModel) {
      rejectedInvalidModel = true
    }
    assertTrue(rejectedInvalidModel)
  }

  // ==========================================================================
  // 12. Phase 3 Prompt 2 — Section 10A: All 8 Real Image Understanding Scenarios (1–8)
  // ==========================================================================
  @Test
  fun test12_imageUnderstandingScenarios_supportsAll8VisualCategoriesAndHighResHeaders() {
    // 1. Ordinary photograph (1600x1200 JPEG)
    val photoAtt =
      AttachmentProcessor.processRawBytes(
        fileName = "mountain_landscape.jpg",
        mimeType = "image/jpeg",
        bytes = buildValidJpegBytes(1600, 1200),
      ).getOrThrow()
    assertEquals(1600, photoAtt.imageWidth)
    assertEquals(1200, photoAtt.imageHeight)
    val photoIntent =
      AstrynIntelligenceEngine.analyzeIntentAndContext(
        prompt = "Describe this photograph.",
        attachments = listOf(photoAtt),
      )
    assertEquals(
      AstrynIntelligenceEngine.MultimodalSubTask.IMAGE_ORDINARY_PHOTO,
      photoIntent.attachmentResolution.multimodalSubTask,
    )

    // 2. Screenshot error diagnosis (1080x2400 mobile tall screenshot PNG)
    val errorScreenshotAtt =
      AttachmentProcessor.processRawBytes(
        fileName = "android_crash_screenshot.png",
        mimeType = "image/png",
        bytes = buildValidPngBytes(1080, 2400),
      ).getOrThrow()
    val screenshotIntent =
      AstrynIntelligenceEngine.analyzeIntentAndContext(
        prompt = "Ismein error kyon aa raha hai?",
        attachments = listOf(errorScreenshotAtt),
      )
    assertEquals(
      AstrynIntelligenceEngine.MultimodalSubTask.IMAGE_SCREENSHOT_ERROR_DIAGNOSIS,
      screenshotIntent.attachmentResolution.multimodalSubTask,
    )
    assertEquals(
      AstrynIntelligenceEngine.ReasoningMode.ROOT_CAUSE_ENGINEERING,
      screenshotIntent.reasoningMode,
    )

    // 3. Handwritten Hindi text
    val hindiHandwrittenAtt =
      AttachmentProcessor.processRawBytes(
        fileName = "hindi_note_question.jpg",
        mimeType = "image/jpeg",
        bytes = buildValidJpegBytes(1200, 1600),
      ).getOrThrow()
    val handwrittenIntent =
      AstrynIntelligenceEngine.analyzeIntentAndContext(
        prompt = "Is handwritten note mein kya likha hai aur iska answer batao.",
        attachments = listOf(hindiHandwrittenAtt),
      )
    assertEquals(
      AstrynIntelligenceEngine.MultimodalSubTask.IMAGE_HANDWRITTEN_NOTES_OR_SOLVE,
      handwrittenIntent.attachmentResolution.multimodalSubTask,
    )

    // 4. Printed English document
    val printedDocAtt =
      AttachmentProcessor.processRawBytes(
        fileName = "printed_invoice_doc.png",
        mimeType = "image/png",
        bytes = buildValidPngBytes(1240, 1754),
      ).getOrThrow()
    val printedIntent =
      AstrynIntelligenceEngine.analyzeIntentAndContext(
        prompt = "Is photo mein kya likha hai? Iska meaning samjhao.",
        attachments = listOf(printedDocAtt),
      )
    assertEquals(
      AstrynIntelligenceEngine.MultimodalSubTask.IMAGE_PRINTED_DOCUMENT,
      printedIntent.attachmentResolution.multimodalSubTask,
    )

    // 5. Chart interpretation
    val chartAtt =
      AttachmentProcessor.processRawBytes(
        fileName = "revenue_growth_chart.png",
        mimeType = "image/png",
        bytes = buildValidPngBytes(1920, 1080),
      ).getOrThrow()
    val chartIntent =
      AstrynIntelligenceEngine.analyzeIntentAndContext(
        prompt = "Is chart aur graph ka trend samjhao.",
        attachments = listOf(chartAtt),
      )
    assertEquals(
      AstrynIntelligenceEngine.MultimodalSubTask.IMAGE_CHART_OR_GRAPH,
      chartIntent.attachmentResolution.multimodalSubTask,
    )

    // 6. Table extraction
    val tableImgAtt =
      AttachmentProcessor.processRawBytes(
        fileName = "monthly_budget_table.png",
        mimeType = "image/png",
        bytes = buildValidPngBytes(1800, 900),
      ).getOrThrow()
    val tableIntent =
      AstrynIntelligenceEngine.analyzeIntentAndContext(
        prompt = "Is table ka calculation karo.",
        attachments = listOf(tableImgAtt),
      )
    assertEquals(
      AstrynIntelligenceEngine.MultimodalSubTask.IMAGE_TABLE_EXTRACTION,
      tableIntent.attachmentResolution.multimodalSubTask,
    )

    // 7. Diagram explanation
    val diagramImgAtt =
      AttachmentProcessor.processRawBytes(
        fileName = "microservice_flowchart.png",
        mimeType = "image/png",
        bytes = buildValidPngBytes(2000, 1400),
      ).getOrThrow()
    val diagramIntent =
      AstrynIntelligenceEngine.analyzeIntentAndContext(
        prompt = "Is diagram ko detail mein samjhao.",
        attachments = listOf(diagramImgAtt),
      )
    assertEquals(
      AstrynIntelligenceEngine.MultimodalSubTask.IMAGE_DIAGRAM_OR_FLOWCHART,
      diagramIntent.attachmentResolution.multimodalSubTask,
    )

    // 8. Comparison of two images
    val uiDesignV1 =
      AttachmentProcessor.processRawBytes(
        fileName = "ui_design_v1.png",
        mimeType = "image/png",
        bytes = buildValidPngBytes(1080, 1920),
      ).getOrThrow()
    val uiDesignV2 =
      AttachmentProcessor.processRawBytes(
        fileName = "ui_design_v2.png",
        mimeType = "image/png",
        bytes = buildValidPngBytes(1080, 1920),
      ).getOrThrow()
    val compareIntent =
      AstrynIntelligenceEngine.analyzeIntentAndContext(
        prompt = "Dono images mein kya difference hai?",
        attachments = listOf(uiDesignV1, uiDesignV2),
      )
    assertEquals(
      AstrynIntelligenceEngine.MultimodalSubTask.IMAGE_MULTI_COMPARISON,
      compareIntent.attachmentResolution.multimodalSubTask,
    )
    assertEquals(2, compareIntent.attachmentResolution.resolvedAttachments.size)
  }

  // ==========================================================================
  // 13. Phase 3 Prompt 2 — Section 10B: All 7 Real PDF Comprehension Scenarios (9–15)
  // ==========================================================================
  @Test
  fun test13_pdfUnderstandingScenarios_supportsTextMultiPageScannedTablesDiagramsAndQuestions() {
    // 9 & 10. Multi-page PDF with Chapters, Tables (TJ kerning), Diagrams, and Question Paper items across 5 pages
    val fivePagePdfBytes =
      """
      %PDF-1.4
      1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj
      2 0 obj << /Type /Pages /Kids [3 0 R 4 0 R 5 0 R 6 0 R 7 0 R] /Count 5 >> endobj
      3 0 obj << /Type /Page /Parent 2 0 R /Contents 8 0 R >> endobj
      4 0 obj << /Type /Page /Parent 2 0 R /Contents 9 0 R >> endobj
      5 0 obj << /Type /Page /Parent 2 0 R /Contents 10 0 R >> endobj
      6 0 obj << /Type /Page /Parent 2 0 R /Contents 11 0 R >> endobj
      7 0 obj << /Type /Page /Parent 2 0 R /Contents 12 0 R >> endobj
      8 0 obj << /Length 110 >>
      stream
      BT (Chapter 1: Executive Overview) Tj T* (Important Date: 15 August 2026 is the launch deadline.) Tj ET
      endstream
      endobj
      9 0 obj << /Length 130 >>
      stream
      BT (Chapter 2: Neural Memory Architecture) Tj T* (Three-tier working, session, and long-term user memory.) Tj ET
      endstream
      endobj
      10 0 obj << /Length 160 >>
      stream
      BT (Table 1: Quarterly Cloud Expenses) Tj T* [(Server Cluster) -320 (450)] TJ T* [(Database Storage) -320 (300)] TJ T* [(CDN Bandwidth) -320 (250)] TJ ET
      endstream
      endobj
      11 0 obj << /Length 140 >>
      stream
      100 200 m 300 400 l S
      BT (Figure 1: Real-Time Multimodal Pipeline Diagram) Tj T* (Shows image and PDF ingestion flow.) Tj ET
      endstream
      endobj
      12 0 obj << /Length 155 >>
      stream
      BT (Page 5 Assessment Questions) Tj T* (Q1. Explain the role of Android Keystore AES-256-GCM.) Tj T* (Q2. How does page-indexed PDF retrieval work?) Tj ET
      endstream
      endobj
      %%EOF
      """.trimIndent().toByteArray(StandardCharsets.ISO_8859_1)

    val pdfAttachment =
      AttachmentProcessor.processRawBytes(
        fileName = "Astryn_System_Manual.pdf",
        mimeType = "application/pdf",
        bytes = fivePagePdfBytes,
      ).getOrThrow()

    assertEquals(AttachmentKind.PDF, pdfAttachment.kind)
    assertEquals(5, pdfAttachment.pageCount)
    assertEquals(5, pdfAttachment.analyzedPageCount)
    assertEquals(0, pdfAttachment.omittedPageCount)
    assertFalse(pdfAttachment.isScannedOrImageBasedPdf)
    assertNotNull(pdfAttachment.documentSummary)
    assertTrue(pdfAttachment.documentSummary!!.contains("Tables on page(s): 3"))
    assertTrue(pdfAttachment.documentSummary!!.contains("Questions detected: 2"))

    // Verify TJ kerning parsed table columns with " | " separator on Page 3
    val page3Text = pdfAttachment.pageTextMap[3].orEmpty()
    assertTrue("TJ kerning must preserve table column separator", page3Text.contains("Server Cluster | 450"))
    assertTrue(page3Text.contains("Database Storage | 300"))
    assertTrue(page3Text.contains("CDN Bandwidth | 250"))

    // 9. PDF Summary query
    val summaryIntent =
      AstrynIntelligenceEngine.analyzeIntentAndContext(
        prompt = "Is PDF ka summary banao.",
        attachments = listOf(pdfAttachment),
      )
    assertEquals(
      AstrynIntelligenceEngine.MultimodalSubTask.PDF_SUMMARY_AND_OVERVIEW,
      summaryIntent.attachmentResolution.multimodalSubTask,
    )

    // 11. Scanned PDF (Image XObject only, no selectable text stream)
    val scannedPdfBytes =
      """
      %PDF-1.4
      1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj
      2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj
      3 0 obj << /Type /Page /Parent 2 0 R /Resources << /XObject << /Im0 4 0 R >> >> >> endobj
      4 0 obj << /Type /XObject /Subtype /Image /Width 1200 /Height 1600 >>
      stream
      BINARY_SCAN_DATA
      endstream
      endobj
      %%EOF
      """.trimIndent().toByteArray(StandardCharsets.ISO_8859_1)

    val scannedPdfAtt =
      AttachmentProcessor.processRawBytes(
        fileName = "Scanned_Handwritten_Deed.pdf",
        mimeType = "application/pdf",
        bytes = scannedPdfBytes,
      ).getOrThrow()
    assertTrue(scannedPdfAtt.isScannedOrImageBasedPdf)
    assertTrue(scannedPdfAtt.extractedText.orEmpty().contains("scanned or image-based document"))
    val scannedIntent =
      AstrynIntelligenceEngine.analyzeIntentAndContext(
        prompt = "Is PDF mein kya diya hai?",
        attachments = listOf(scannedPdfAtt),
      )
    assertEquals(
      AstrynIntelligenceEngine.MultimodalSubTask.PDF_SCANNED_DOCUMENT_ANALYSIS,
      scannedIntent.attachmentResolution.multimodalSubTask,
    )

    // 12. PDF containing tables + deterministic table sum calculation (450 + 300 + 250 = 1000)
    val tableCalcIntent =
      AstrynIntelligenceEngine.analyzeIntentAndContext(
        prompt = "Is table ka calculation karo.",
        attachments = listOf(pdfAttachment),
      )
    assertEquals(
      AstrynIntelligenceEngine.MultimodalSubTask.PDF_TABLE_CALCULATION,
      tableCalcIntent.attachmentResolution.multimodalSubTask,
    )
    assertNotNull(tableCalcIntent.mathComputation)
    assertEquals(1000.0, tableCalcIntent.mathComputation!!.numericAnswer, 0.001)
    assertEquals("1000", tableCalcIntent.mathComputation!!.formattedResult)

    // 13. PDF containing diagrams
    val diagramPdfIntent =
      AstrynIntelligenceEngine.analyzeIntentAndContext(
        prompt = "Is PDF ke diagram ko detail mein samjhao.",
        attachments = listOf(pdfAttachment),
      )
    assertEquals(
      AstrynIntelligenceEngine.MultimodalSubTask.PDF_DIAGRAM_EXPLANATION,
      diagramPdfIntent.attachmentResolution.multimodalSubTask,
    )

    // 14. Question paper solving
    val questionPaperIntent =
      AstrynIntelligenceEngine.analyzeIntentAndContext(
        prompt = "Is question paper ke questions solve karo.",
        attachments = listOf(pdfAttachment),
      )
    assertEquals(
      AstrynIntelligenceEngine.MultimodalSubTask.PDF_QUESTION_PAPER_SOLVING,
      questionPaperIntent.attachmentResolution.multimodalSubTask,
    )

    // 15. Specific page & chapter targeting ("Page 5 par kya diya hai?" & "Ab chapter 2 samjhao")
    val page5Intent =
      AstrynIntelligenceEngine.analyzeIntentAndContext(
        prompt = "Page 5 par kya diya hai?",
        attachments = listOf(pdfAttachment),
      )
    assertEquals(listOf(5), page5Intent.attachmentResolution.targetPageNumbers)
    val page5TargetedText =
      pdfAttachment.extractTargetedPagesOrSections(targetPageNumbers = listOf(5))
    assertTrue(page5TargetedText.contains("[Targeted Page 5 of 5]"))
    assertTrue(page5TargetedText.contains("Android Keystore AES-256-GCM"))
  }

  // ==========================================================================
  // 14. Phase 3 Prompt 2 — Section 10C (16, 17, 18): Multi-Turn Attachment Retention & Disambiguation
  // ==========================================================================
  @Test
  fun test14_attachmentContextRetentionAndSwitching_retainsAcrossTurnsAndDisambiguatesCorrectly() {
    val userUid = "uid_alice"
    val convId = "conv_multimodal_session"

    val designImg =
      AttachmentProcessor.processRawBytes(
        fileName = "dashboard_design.png",
        mimeType = "image/png",
        bytes = buildValidPngBytes(1440, 900),
      ).getOrThrow()

    val manualPdfBytes =
      """
      %PDF-1.4
      1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj
      2 0 obj << /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 >> endobj
      3 0 obj << /Type /Page /Parent 2 0 R /Contents 5 0 R >> endobj
      4 0 obj << /Type /Page /Parent 2 0 R /Contents 6 0 R >> endobj
      5 0 obj << /Length 95 >>
      stream
      BT (Chapter 1: Setup Guide) Tj T* (Install JDK 17 and configure API Key Vault.) Tj ET
      endstream
      endobj
      6 0 obj << /Length 120 >>
      stream
      BT (Chapter 2: Security Rules) Tj T* (Important Question: Why is per-user UID isolation mandatory?) Tj ET
      endstream
      endobj
      %%EOF
      """.trimIndent().toByteArray(StandardCharsets.ISO_8859_1)

    val manualPdf =
      AttachmentProcessor.processRawBytes(
        fileName = "security_manual.pdf",
        mimeType = "application/pdf",
        bytes = manualPdfBytes,
      ).getOrThrow()

    // Register both in the conversation session (simulating Turn 1 Image upload and Turn 2 PDF upload)
    AttachmentProcessor.registerConversationAttachments(userUid, convId, listOf(designImg, manualPdf))

    val history =
      listOf(
        ChatMessage(
          role = MessageRole.USER,
          content = "Ye mera current app UI design hai.",
          attachments = listOf(designImg),
        ),
        ChatMessage(
          role = MessageRole.MODEL,
          content = "Design clean hai, glassmorphism cards dikh rahe hain.",
        ),
        ChatMessage(
          role = MessageRole.USER,
          content = "Aur ye security manual PDF hai.",
          attachments = listOf(manualPdf),
        ),
        ChatMessage(
          role = MessageRole.MODEL,
          content = "Maine security_manual.pdf padh liya hai.",
        ),
      )

    // Test 17: Multi-turn PDF follow-up sequence without re-uploading:
    // Turn A: "Iska summary batao." -> resolves to most recent attachment (security_manual.pdf)
    val summaryTurn =
      AstrynIntelligenceEngine.analyzeIntentAndContext(
        prompt = "Iska summary batao.",
        conversationHistory = history,
        userUid = userUid,
        conversationId = convId,
      )
    assertTrue(summaryTurn.attachmentResolution.isFromEarlierTurn)
    assertEquals("security_manual.pdf", summaryTurn.attachmentResolution.resolvedAttachments.SingleFileName())

    // Turn B: "Ab chapter 2 samjhao." -> resolves to security_manual.pdf and targets "chapter 2"
    val chapter2Turn =
      AstrynIntelligenceEngine.analyzeIntentAndContext(
        prompt = "Ab chapter 2 samjhao.",
        conversationHistory = history,
        userUid = userUid,
        conversationId = convId,
      )
    assertEquals("security_manual.pdf", chapter2Turn.attachmentResolution.resolvedAttachments.SingleFileName())
    assertTrue(chapter2Turn.attachmentResolution.targetSectionKeywords.any { it.contains("chapter 2") })
    val chapter2Extracted =
      manualPdf.extractTargetedPagesOrSections(
        targetSectionKeywords = chapter2Turn.attachmentResolution.targetSectionKeywords,
      )
    assertTrue(chapter2Extracted.contains("Chapter 2: Security Rules"))

    // Turn C: "Ismein important questions kaun-se hain?" -> resolves to security_manual.pdf
    val questionsTurn =
      AstrynIntelligenceEngine.analyzeIntentAndContext(
        prompt = "Ismein important questions kaun-se hain?",
        conversationHistory = history,
        userUid = userUid,
        conversationId = convId,
      )
    assertEquals("security_manual.pdf", questionsTurn.attachmentResolution.resolvedAttachments.SingleFileName())

    // Test 16 & 18: Switching between the two attachments in the same conversation!
    // User explicitly asks about the image/design: "Us photo ke design ko premium kaise bana sakte hain?"
    val switchToImageTurn =
      AstrynIntelligenceEngine.analyzeIntentAndContext(
        prompt = "Us photo ke design ko premium kaise bana sakte hain?",
        conversationHistory = history,
        userUid = userUid,
        conversationId = convId,
      )
    assertEquals("dashboard_design.png", switchToImageTurn.attachmentResolution.resolvedAttachments.SingleFileName())
    assertEquals(
      AstrynIntelligenceEngine.MultimodalSubTask.IMAGE_UI_DESIGN_CRITIQUE,
      switchToImageTurn.attachmentResolution.multimodalSubTask,
    )

    // User switches back to the PDF: "PDF ke according iska answer kya hoga?"
    val switchBackToPdfTurn =
      AstrynIntelligenceEngine.analyzeIntentAndContext(
        prompt = "PDF ke according iska answer kya hoga?",
        conversationHistory = history,
        userUid = userUid,
        conversationId = convId,
      )
    assertEquals("security_manual.pdf", switchBackToPdfTurn.attachmentResolution.resolvedAttachments.SingleFileName())
  }

  // ==========================================================================
  // 15. Phase 3 Prompt 2 — Section 2 & 10C (19): Long Conversation Memory, Corrections & Non-Fabrication
  // ==========================================================================
  @Test
  fun test15_longConversationMemoryAndUserCorrections_retainsAttachmentsAndOverridesStaleConstraints() {
    val earlyPdf =
      ChatAttachment(
        id = "att_early_spec",
        fileName = "fintech_spec.pdf",
        mimeType = "application/pdf",
        fileSizeBytes = 4096L,
        kind = AttachmentKind.PDF,
        pageCount = 12,
        documentSummary = "Pages: 12/12 analyzed | Structure: P.1: Glassmorphism Theme; P.4: Offline Room Sync",
      )

    val omittedMessages =
      buildList {
        add(
          ChatMessage(
            role = MessageRole.USER,
            content = "Main ek fintech app bana raha hu jismein Glassmorphism UI design rahega aur zero budget hai.",
            attachments = listOf(earlyPdf),
          )
        )
        add(
          ChatMessage(
            role = MessageRole.MODEL,
            content = "Hum Glassmorphism UI aur SQLite Room database use karenge.",
          )
        )
        for (i in 1..10) {
          add(ChatMessage(role = MessageRole.USER, content = "Feature module step $i discuss karte hain."))
          add(ChatMessage(role = MessageRole.MODEL, content = "Module $i architecture ready hai."))
        }
      }

    // Verify Long Conversation Summary retains earlier uploaded attachment summary & design constraints
    val longSummary = AstrynIntelligenceEngine.buildLongConversationSummary(omittedMessages)
    assertTrue(longSummary.attachmentSummaries.any { it.contains("fintech_spec.pdf") })
    assertTrue(longSummary.formattedSummaryBlock.contains("fintech_spec.pdf"))

    // Verify "Usi design ko maintain karna" & "Maine pehle jo bataya tha, uske hisab se batao"
    val recentHistory =
      listOf(
        ChatMessage(
          role = MessageRole.USER,
          content = "Mera app dark neon glassmorphism design aur 15 second animation use karta hai.",
        ),
        ChatMessage(
          role = MessageRole.MODEL,
          content = "Samajh gaya, dark neon glassmorphism design set hai.",
        ),
      )

    val maintainDesignAnalysis =
      AstrynIntelligenceEngine.analyzeIntentAndContext(
        prompt = "Usi design ko maintain karna aur maine pehle jo bataya tha, uske hisab se batao.",
        conversationHistory = recentHistory,
      )
    assertTrue(
      maintainDesignAnalysis.implicitConstraints.any { it.contains("Maintain the previously established UI/visual design", ignoreCase = true) },
    )
    assertTrue(
      maintainDesignAnalysis.resolvedReferences["active_project_or_design"]
        ?.contains("dark neon glassmorphism") == true,
    )

    // Verify User Correction handling overrides earlier budget and duration constraints
    val correctedAnalysis =
      AstrynIntelligenceEngine.analyzeIntentAndContext(
        prompt = "Nahi, ab mera budget 5000 rupaye hai aur 30 second ka video chahiye instead of 15 second.",
        conversationHistory = recentHistory,
      )
    assertTrue(correctedAnalysis.userCorrections.isNotEmpty())
    assertTrue(correctedAnalysis.implicitConstraints.any { it.contains("₹5000") })
    assertTrue(correctedAnalysis.implicitConstraints.any { it.contains("30 seconds") })
  }

  // ==========================================================================
  // 16. Phase 3 Prompt 2 — Section 8, 10C (20) & 11: User Isolation, Privacy & Corrupted File Rejection
  // ==========================================================================
  @Test
  fun test16_accountIsolationAndCorruptedFileHandling_preventsCrossUserAttachmentLeakAndRejectsBadFiles() {
    val aliceUid = "uid_alice"
    val bobUid = "uid_bob"
    val sharedConvId = "conv_100"

    val aliceSecretPdf =
      ChatAttachment(
        id = "att_alice_private",
        fileName = "alice_tax_statement.pdf",
        mimeType = "application/pdf",
        fileSizeBytes = 8192L,
        kind = AttachmentKind.PDF,
        pageCount = 4,
        inlineBase64Data = "JVBERi0xLjQKQUxJQ0VfUFJJVkFURQ==",
        extractedText = "[Page 1 of 4]\nAlice Confidential Tax Return PAN ABCDE1234F",
      )

    AttachmentProcessor.registerConversationAttachments(aliceUid, sharedConvId, listOf(aliceSecretPdf))

    // Alice can retrieve her attachment in conv_100
    val aliceResolved =
      AstrynIntelligenceEngine.resolveActiveAttachmentsForTurn(
        prompt = "Is PDF ka summary batao.",
        currentAttachments = emptyList(),
        conversationHistory = emptyList(),
        userUid = aliceUid,
        conversationId = sharedConvId,
      )
    assertEquals(1, aliceResolved.resolvedAttachments.size)
    assertEquals("alice_tax_statement.pdf", aliceResolved.resolvedAttachments.first().fileName)

    // Bob querying the exact same conversationId MUST NOT see or enrich Alice's attachment
    val bobResolved =
      AstrynIntelligenceEngine.resolveActiveAttachmentsForTurn(
        prompt = "Is PDF ka summary batao.",
        currentAttachments = emptyList(),
        conversationHistory = emptyList(),
        userUid = bobUid,
        conversationId = sharedConvId,
      )
    assertTrue("Bob must never receive Alice's attachments", bobResolved.resolvedAttachments.isEmpty())

    val bobEnrichAttempt =
      AttachmentProcessor.enrichAttachmentFromSession(
        userUid = bobUid,
        conversationId = sharedConvId,
        attachment = aliceSecretPdf.copy(inlineBase64Data = null, extractedText = null),
      )
    assertNull("Bob must not be able to enrich binary data from Alice's session", bobEnrichAttempt.inlineBase64Data)

    // Logout / account switch clears Alice's session cache cleanly
    AstrynIntelligenceEngine.onUserLogoutOrAccountSwitch(aliceUid)
    assertTrue(AttachmentProcessor.getConversationAttachments(aliceUid, sharedConvId).isEmpty())

    // Corrupted image and corrupted/unsupported files must be rejected with clear errors
    val corruptedImgResult =
      AttachmentProcessor.processRawBytes(
        fileName = "broken_photo.jpg",
        mimeType = "image/jpeg",
        bytes = "NOT_A_VALID_JPEG_BINARY".toByteArray(),
      )
    assertTrue(corruptedImgResult.isFailure)

    val corruptedPdfResult =
      AttachmentProcessor.processRawBytes(
        fileName = "corrupted.pdf",
        mimeType = "application/pdf",
        bytes = "%PDF-1.4\ngarbage_without_pages_or_eof".toByteArray(),
      )
    assertTrue(corruptedPdfResult.isFailure)

    val unsupportedExeResult =
      AttachmentProcessor.processRawBytes(
        fileName = "malware.exe",
        mimeType = "application/octet-stream",
        bytes = ByteArray(128) { 1 },
      )
    assertTrue(unsupportedExeResult.isFailure)
  }

  private fun List<ChatAttachment>.SingleFileName(): String = this.single().fileName
}
