package com.example

import com.example.data.api.AstrynAdaptiveIntelligenceEngines.AdaptiveIntent
import com.example.data.api.AstrynAdaptiveIntelligenceEngines.AmbiguityReason
import com.example.data.api.AstrynAdaptiveIntelligenceEngines.AstrynAdaptiveExplanationEngine
import com.example.data.api.AstrynAdaptiveIntelligenceEngines.AstrynFeedbackLearningEngine
import com.example.data.api.AstrynAdaptiveIntelligenceEngines.AstrynFollowUpIntelligenceEngine
import com.example.data.api.AstrynAdaptiveIntelligenceEngines.AstrynIntentUnderstandingEngine
import com.example.data.api.AstrynAdaptiveIntelligenceEngines.AstrynPhase17Coordinator
import com.example.data.api.AstrynAdaptiveIntelligenceEngines.AstrynResponseConfidenceEngine
import com.example.data.api.AstrynAdaptiveIntelligenceEngines.ConfidenceTier
import com.example.data.api.AstrynAdaptiveIntelligenceEngines.ExplanationStyle
import com.example.data.api.AstrynAdaptiveIntelligenceEngines.FollowUpAction
import com.example.data.api.AstrynAdaptiveIntelligenceEngines.RoutingTarget
import com.example.data.api.AstrynContextualIntelligenceEngine.AstrynContextUnderstandingEngine
import com.example.data.api.AstrynContextualIntelligenceEngine.AstrynConversationStateManager
import com.example.data.api.AstrynContextualIntelligenceEngine.AstrynReferenceResolutionEngine
import com.example.data.api.AstrynContextualIntelligenceEngine.ReferenceTargetType
import com.example.data.api.AstrynIntelligenceEngine
import com.example.data.api.AstrynPersonalIntelligenceEngine
import com.example.data.api.GeminiLiveVoiceSession
import com.example.data.api.GeminiService
import com.example.data.model.AttachmentKind
import com.example.data.model.ChatAttachment
import com.example.data.model.ChatMessage
import com.example.data.model.MessageRole
import com.example.data.model.MessageStatus
import com.example.data.repository.UserPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ASTRYN AI — PHASE 17 COMPREHENSIVE TEST SUITE (48 TESTS)
 * Validates adaptive contextual intelligence, multi-topic tracking/recovery,
 * bilingual (Hindi / Hinglish / English) pronoun & reference resolution,
 * 23-intent recognition, smart ambiguity detection, 8 adaptive explanation profiles,
 * follow-up continuity, feedback/correction learning, secret-exclusion safety,
 * internal confidence awareness, tool routing, project stage awareness,
 * GeminiService integration, account isolation, and Voice-to-Voice Jarvis lock preservation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AstrynPhase17ContextualIntelligenceTest {

  private fun userMsg(text: String, id: String = "u_${System.nanoTime()}", ts: Long = System.currentTimeMillis(), atts: List<ChatAttachment> = emptyList()) =
    ChatMessage(id = id, role = MessageRole.USER, content = text, timestamp = ts, status = MessageStatus.SUCCESS, attachments = atts)

  private fun modelMsg(text: String, id: String = "m_${System.nanoTime()}", ts: Long = System.currentTimeMillis()) =
    ChatMessage(id = id, role = MessageRole.MODEL, content = text, timestamp = ts, status = MessageStatus.SUCCESS)

  @Before
  fun setup() {
    AstrynConversationStateManager.clearAllForTesting()
    AstrynPersonalIntelligenceEngine.clearAllPersonalIntelligenceStateForTesting()
  }

  @After
  fun tearDown() {
    AstrynConversationStateManager.clearAllForTesting()
    AstrynPersonalIntelligenceEngine.clearAllPersonalIntelligenceStateForTesting()
  }

  // 1–6: Pronoun & Reference Resolution (Hindi / Hinglish / English)

  @Test
  fun test01_hinglishSecondOptionReferenceResolution() {
    val history = listOf(
      userMsg("Online earning ke best zero-investment options batao"),
      modelMsg("1. Freelancing Content Writing\n2. Short-Form Video Editing\n3. Online Tuition")
    )
    val analysis = AstrynPhase17Coordinator.analyzeTurn(
      prompt = "Iska second option explain karo",
      conversationHistory = history,
      userUid = "u1",
      conversationId = "c1",
    )
    val primary = analysis.contextReport.referenceBundle.primaryTarget
    assertNotNull(primary)
    assertEquals(ReferenceTargetType.OPTION, primary?.targetType)
    assertTrue(primary!!.resolvedTarget.contains("Short-Form Video Editing"))
    assertTrue(primary.confidence >= 0.85)
    assertFalse(analysis.ambiguity.isAmbiguous)
  }

  @Test
  fun test02_hindiOrdinalOptionResolutionDevanagari() {
    val history = listOf(
      userMsg("Python aur Kotlin ke options batao"),
      modelMsg("1. Kotlin Coroutines\n2. Kotlin Flow\n3. Compose State")
    )
    val report = AstrynContextUnderstandingEngine.understandContext(
      prompt = "पहला वाला उदाहरण के साथ समझाओ",
      conversationHistory = history,
      userUid = "u1",
      conversationId = "c2",
    )
    val primary = report.referenceBundle.primaryTarget
    assertEquals(ReferenceTargetType.OPTION, primary?.targetType)
    assertTrue(primary!!.resolvedTarget.contains("Kotlin Coroutines"))
  }

  @Test
  fun test03_hindiPronounEntityReferenceResolvedAgainstActiveTopic() {
    val history = listOf(
      userMsg("Kotlin Coroutines kya hota hai?"),
      modelMsg("Kotlin Coroutines lightweight threads hote hain jo async kaam suspend karte hain.")
    )
    val analysis = AstrynPhase17Coordinator.analyzeTurn(
      prompt = "यह कैसे काम करता है और इसमें Dispatcher क्यों ज़रूरी है?",
      conversationHistory = history,
      userUid = "u1",
      conversationId = "c3",
    )
    assertTrue(analysis.contextReport.referenceBundle.hasExplicitOrImplicitReference)
    assertTrue(analysis.contextReport.referenceBundle.isConfidenceSufficient)
    assertFalse(analysis.ambiguity.isAmbiguous)
  }

  @Test
  fun test04_englishContinueFromThereAnchoredToActiveWorkflow() {
    val history = listOf(
      userMsg("Let's plan a Jetpack Compose login flow. Pending next step: biometric prompt."),
      modelMsg("Step 1 completed: Credential Manager Google Sign-In wired.")
    )
    val analysis = AstrynPhase17Coordinator.analyzeTurn(
      prompt = "Continue from there",
      conversationHistory = history,
      userUid = "u1",
      conversationId = "c4",
    )
    assertEquals(AdaptiveIntent.CONTINUATION, analysis.intentResult.primaryIntent)
    assertTrue(analysis.followUp.isFollowUp)
    assertFalse(analysis.ambiguity.isAmbiguous)
  }

  @Test
  fun test05_documentPronounReferenceResolvesToPdfInsteadOfNoise() {
    val pdf = ChatAttachment(
      id = "doc_1",
      fileName = "Annual_Budget_Report.pdf",
      mimeType = "application/pdf",
      fileSizeBytes = 24_576L,
      kind = AttachmentKind.PDF,
      extractedText = "Page 1: Revenue 120 Cr\nPage 2: Q3 Marketing spend 18 Cr",
      pageCount = 2,
    )
    val history = listOf(
      userMsg("Here is our budget PDF", atts = listOf(pdf)),
      modelMsg("I received Annual_Budget_Report.pdf (2 pages).")
    )
    val analysis = AstrynPhase17Coordinator.analyzeTurn(
      prompt = "उसमें page 2 ka summary batao",
      conversationHistory = history,
      userUid = "u1",
      conversationId = "c5",
    )
    assertTrue(
      analysis.contextReport.referenceBundle.references.any {
        it.targetType == ReferenceTargetType.DOCUMENT && it.resolvedTarget.contains("Annual_Budget_Report.pdf")
      }
    )
    assertFalse(analysis.ambiguity.isAmbiguous)
  }

  @Test
  fun test06_relativeTimeReferenceResolvesAgainstLiveClock() {
    val history = listOf(userMsg("Kal ke task ki planning karni hai"))
    val analysis = AstrynPhase17Coordinator.analyzeTurn(
      prompt = "Kal ka schedule kya hona chahiye?",
      conversationHistory = history,
      userUid = "u1",
      conversationId = "c6",
    )
    assertTrue(
      analysis.contextReport.referenceBundle.references.any {
        it.targetType == ReferenceTargetType.TIME_OR_DATE && it.resolvedTarget.contains("Tomorrow:")
      }
    )
  }

  // 7–11: Multi-Topic Tracking, Topic Switching, Recovery & Long Conversations

  @Test
  fun test07_multiTopicSwitchPushesPreviousTopicToStack() {
    val history = listOf(
      userMsg("Jetpack Compose LazyColumn scroll performance kaise optimize karein?"),
      modelMsg("Use stable keys and remember blocks inside LazyColumn items.")
    )
    val switched = AstrynPhase17Coordinator.analyzeTurn(
      prompt = "GST 18% on ₹50000 invoice calculate karo",
      conversationHistory = history,
      userUid = "u1",
      conversationId = "c7",
    )
    assertTrue(switched.contextReport.topicSwitched)
    assertTrue(switched.contextReport.snapshot.topicStack.isNotEmpty())
  }

  @Test
  fun test08_topicRecoveryRestoresEarlierTopicFrame() {
    val history = listOf(
      userMsg("Jetpack Compose LazyColumn scroll performance optimize karna hai"),
      modelMsg("Use stable item keys in LazyColumn."),
      userMsg("GST 18% calculate on 5000"),
      modelMsg("GST amount is 900, Total = 5900.")
    )
    val recovery = AstrynPhase17Coordinator.analyzeTurn(
      prompt = "Wapas pehle wale Compose topic par aate hain",
      conversationHistory = history,
      userUid = "u1",
      conversationId = "c8",
    )
    assertTrue(recovery.contextReport.topicRecovered)
    assertTrue(recovery.contextReport.activeTopicLabel?.contains("Compose", ignoreCase = true) == true ||
      recovery.contextReport.referenceBundle.recoveredTopicFrame != null)
  }

  @Test
  fun test09_longConversationRetainsEarlyConstraintsAndRecoversOriginalMessages() {
    val longHistory = mutableListOf<ChatMessage>()
    longHistory.add(userMsg("Must keep Glassmorphism UI same and maintain ₹50,000 budget cap for Astryn module"))
    longHistory.add(modelMsg("Noted: Glassmorphism UI preserved and ₹50,000 budget respected."))
    for (i in 1..20) {
      longHistory.add(userMsg("Follow up technical question number $i about Kotlin flows"))
      longHistory.add(modelMsg("Response $i covering StateFlow and SharedFlow details."))
    }
    val analysis = AstrynPhase17Coordinator.analyzeTurn(
      prompt = "Humne starting mein jo budget cap aur UI rule decide kiya tha wo yaad hai?",
      conversationHistory = longHistory,
      userUid = "u1",
      conversationId = "c9",
    )
    assertTrue(analysis.contextReport.snapshot.indexedTurns.size >= 40)
    assertTrue(analysis.contextReport.recoveredHistorySnippets.any { it.contains("budget", ignoreCase = true) || it.contains("Glassmorphism", ignoreCase = true) })
  }

  @Test
  fun test10_geminiServiceSummarizeOmittedTurnsPreservesEarliestGoalAndBound() {
    val omitted = (1..15).map { idx ->
      userMsg("Turn $idx: Keep Jetpack Compose UI preserved while optimizing Room queries.")
    }
    val summary = GeminiService.summarizeOmittedTurns(omitted, maxSummaryChars = 900)
    assertTrue(summary.contains("<earlier_conversation_summary>"))
    assertTrue(summary.contains("Turn 1:"))
    assertTrue(summary.length <= 930)
  }

  @Test
  fun test11_conversationStateUpdatesOnAssistantResponseFinalized() {
    val history = listOf(userMsg("Best databases for Android offline sync?"))
    AstrynPhase17Coordinator.analyzeTurn("Best databases for Android offline sync?", history, userUid = "u1", conversationId = "c11")
    AstrynConversationStateManager.recordAssistantResponse(
      userUid = "u1",
      conversationId = "c11",
      userPrompt = "Best databases for Android offline sync?",
      assistantReply = "1. Room SQLite\n2. DataStore Preferences\n3. Firestore Offline Cache",
    )
    val snap = AstrynConversationStateManager.getSnapshot("u1", "c11")
    assertNotNull(snap)
    assertEquals(3, snap?.activeTopic?.numberedOptions?.size)
  }

  // 12–21: 23-Intent Understanding Coverage

  @Test
  fun test12_classifiesGeneralConversationGreeting() {
    val res = AstrynPhase17Coordinator.analyzeTurn("Namaste bhai, kaise ho?")
    assertEquals(AdaptiveIntent.GENERAL_CONVERSATION, res.intentResult.primaryIntent)
  }

  @Test
  fun test13_classifiesCorrectionIntentWithContextDependency() {
    val history = listOf(userMsg("Android service banana hai"), modelMsg("Here is a Java implementation..."))
    val res = AstrynPhase17Coordinator.analyzeTurn("Nahi main Java nahi Kotlin bol raha tha", conversationHistory = history)
    assertEquals(AdaptiveIntent.CORRECTION, res.intentResult.primaryIntent)
    assertTrue(res.intentResult.isContextDependent)
  }

  @Test
  fun test14_classifiesDebuggingAndTroubleshootingIntents() {
    val debug = AstrynPhase17Coordinator.analyzeTurn("NullPointerException aa raha hai ViewModel init mein, fix this code")
    assertEquals(AdaptiveIntent.DEBUGGING, debug.intentResult.primaryIntent)

    val trouble = AstrynPhase17Coordinator.analyzeTurn("Sync kaam nahi kar raha, still failing after restart")
    assertEquals(AdaptiveIntent.TROUBLESHOOTING, trouble.intentResult.primaryIntent)
  }

  @Test
  fun test15_classifiesTranslationAndSummarizationIntents() {
    val trans = AstrynPhase17Coordinator.analyzeTurn("Translate this paragraph into simple Hindi")
    assertEquals(AdaptiveIntent.TRANSLATION, trans.intentResult.primaryIntent)

    val sum = AstrynPhase17Coordinator.analyzeTurn("Summarize these meeting notes into 3 bullet points")
    assertEquals(AdaptiveIntent.SUMMARIZATION, sum.intentResult.primaryIntent)
  }

  @Test
  fun test16_classifiesCalculationAndComparisonIntents() {
    val calc = AstrynPhase17Coordinator.analyzeTurn("20% discount on ₹2500 kitna hoga?")
    assertEquals(AdaptiveIntent.CALCULATION, calc.intentResult.primaryIntent)

    val cmp = AstrynPhase17Coordinator.analyzeTurn("Room vs Firestore comparison for offline caching")
    assertEquals(AdaptiveIntent.COMPARISON, cmp.intentResult.primaryIntent)
  }

  @Test
  fun test17_classifiesDocumentCreationPlanningAndCoding() {
    val doc = AstrynPhase17Coordinator.analyzeTurn("Create a document proposal for Q4 mobile app rollout")
    assertEquals(AdaptiveIntent.DOCUMENT_CREATION, doc.intentResult.primaryIntent)

    val plan = AstrynPhase17Coordinator.analyzeTurn("30-day DSA interview study plan roadmap banao")
    assertEquals(AdaptiveIntent.PLANNING, plan.intentResult.primaryIntent)

    val code = AstrynPhase17Coordinator.analyzeTurn("Write code for a thread-safe Kotlin LRU Cache class")
    assertEquals(AdaptiveIntent.CODING, code.intentResult.primaryIntent)
  }

  @Test
  fun test18_classifiesEmotionalSupportIntent() {
    val res = AstrynPhase17Coordinator.analyzeTurn("Yaar bahut stress ho raha hai exam ka, samajh nahi aa raha")
    assertEquals(AdaptiveIntent.EMOTIONAL_SUPPORT, res.intentResult.primaryIntent)
    val offline = AstrynPhase17Coordinator.generateContextualOfflineReply("Yaar bahut stress ho raha hai", res, "u1")
    assertNotNull(offline)
    assertTrue(offline!!.contains("step", ignoreCase = true))
  }

  @Test
  fun test19_classifiesLearningClarificationAndFeedbackIntents() {
    val learn = AstrynPhase17Coordinator.analyzeTurn("Mujhe Kotlin Coroutines bilkul basic se padhao interview prep ke liye")
    assertEquals(AdaptiveIntent.LEARNING, learn.intentResult.primaryIntent)

    val clarify = AstrynPhase17Coordinator.analyzeTurn("What do you mean by structured concurrency?", listOf(modelMsg("Use structured concurrency.")))
    assertEquals(AdaptiveIntent.CLARIFICATION, clarify.intentResult.primaryIntent)

    val fb = AstrynPhase17Coordinator.analyzeTurn("Bahut lamba tha, aur simple karo", listOf(modelMsg("Long text...")))
    assertEquals(AdaptiveIntent.FEEDBACK, fb.intentResult.primaryIntent)
  }

  @Test
  fun test20_classifiesResearchCreativeAndTaskExecution() {
    val research = AstrynPhase17Coordinator.analyzeTurn("Latest Google Gemini model updates verify with sources")
    assertEquals(AdaptiveIntent.RESEARCH, research.intentResult.primaryIntent)

    val creative = AstrynPhase17Coordinator.analyzeTurn("Write a short Hindi poem on persistence")
    assertEquals(AdaptiveIntent.CREATIVE_WRITING, creative.intentResult.primaryIntent)

    val exec = AstrynPhase17Coordinator.analyzeTurn("Send email externally with the report")
    assertEquals(AdaptiveIntent.TASK_EXECUTION, exec.intentResult.primaryIntent)
  }

  @Test
  fun test21_legacyIntentMappingBackwardsCompatible() {
    assertEquals(
      AstrynIntelligenceEngine.PrimaryIntent.MATH_AND_DISCOUNT_CALCULATION,
      AstrynIntentUnderstandingEngine.mapToLegacyPrimaryIntent(AdaptiveIntent.CALCULATION)
    )
    assertEquals(
      AstrynIntelligenceEngine.PrimaryIntent.CODE_DEBUGGING_AND_FIX,
      AstrynIntentUnderstandingEngine.mapToLegacyPrimaryIntent(AdaptiveIntent.DEBUGGING)
    )
  }

  // 22–26: Smart Ambiguity Detection & Safety Gating

  @Test
  fun test22_ambiguousDestructiveDeleteBlocksAutoExecutionWithOneQuestion() {
    val res = AstrynPhase17Coordinator.analyzeTurn("Usko delete kar do")
    assertTrue(res.ambiguity.isAmbiguous)
    assertTrue(res.ambiguity.blocksAutoExecution)
    assertEquals(AmbiguityReason.AMBIGUOUS_DESTRUCTIVE_ACTION, res.ambiguity.reason)
    assertEquals(1, res.ambiguity.clarificationQuestions.size)
    assertEquals(RoutingTarget.APPROVAL_REQUIRED_ACTION, res.routingDecision.target)
    assertTrue(res.routingDecision.requiresUserApproval)
  }

  @Test
  fun test23_ambiguousExternalFileSendBlocksSilentExecution() {
    val res = AstrynPhase17Coordinator.analyzeTurn("Wahi file email par bhej do")
    assertTrue(res.ambiguity.isAmbiguous)
    assertTrue(res.ambiguity.blocksAutoExecution)
    assertEquals(AmbiguityReason.AMBIGUOUS_EXTERNAL_SEND_OR_SHARE, res.ambiguity.reason)
    assertEquals(1, res.ambiguity.clarificationQuestions.size)
  }

  @Test
  fun test24_calculationMissingNumbersAsksForNumbersClarification() {
    val res = AstrynPhase17Coordinator.analyzeTurn("Iska EMI calculation karo")
    assertTrue(res.ambiguity.isAmbiguous)
    assertEquals(AmbiguityReason.MISSING_CALCULATION_NUMBERS, res.ambiguity.reason)
    assertEquals(1, res.ambiguity.clarificationQuestions.size)
  }

  @Test
  fun test25_barePronounWithEmptyHistoryRequestsClarification() {
    val res = AstrynPhase17Coordinator.analyzeTurn("Isko improve karo", conversationHistory = emptyList())
    assertTrue(res.ambiguity.isAmbiguous)
    assertTrue(res.contextReport.referenceBundle.needsClarification)
    assertEquals(1, res.ambiguity.clarificationQuestions.size)
  }

  @Test
  fun test26_clearContextualFollowUpDoesNotAskRedundantClarification() {
    val history = listOf(
      userMsg("Binary Search kaise kaam karta hai?"),
      modelMsg("Binary Search sorted array ko half mein divide karke O(log n) mein element dhundhta hai.")
    )
    val res = AstrynPhase17Coordinator.analyzeTurn("Iska ek example do", conversationHistory = history)
    assertFalse("Should NOT ask unnecessary clarification when topic is crystal clear", res.ambiguity.isAmbiguous)
    assertEquals(FollowUpAction.ADDITIONAL_EXAMPLE, res.followUp.action)
  }

  // 27–32: Adaptive Explanation Modes (8 Styles + Short Constraint)

  @Test
  fun test27_adaptiveExplanationDetectsBeginnerAndExampleModes() {
    val beginner = AstrynPhase17Coordinator.analyzeTurn("Recursion bilkul basic se easy language mein samjhao")
    assertEquals(ExplanationStyle.BEGINNER_FRIENDLY, beginner.explanationProfile.style)

    val example = AstrynPhase17Coordinator.analyzeTurn("Dependency injection ka practical use aur example do", listOf(userMsg("DI kya hai?")))
    assertEquals(ExplanationStyle.EXAMPLE_BASED, example.explanationProfile.style)
  }

  @Test
  fun test28_adaptiveExplanationDetectsTechnicalAndExamOrientedModes() {
    val tech = AstrynPhase17Coordinator.analyzeTurn("Design a thread-safe production architecture with OOM handling")
    assertEquals(ExplanationStyle.TECHNICAL, tech.explanationProfile.style)

    val exam = AstrynPhase17Coordinator.analyzeTurn("Photosynthesis exam definition aur important MCQ points batao")
    assertEquals(ExplanationStyle.EXAM_ORIENTED, exam.explanationProfile.style)
  }

  @Test
  fun test29_adaptiveExplanationDetectsStepByStepProfessionalAndDetailedModes() {
    val step = AstrynPhase17Coordinator.analyzeTurn("Step by step roadmap banao Android app launch ke liye")
    assertEquals(ExplanationStyle.STEP_BY_STEP, step.explanationProfile.style)

    val prof = AstrynPhase17Coordinator.analyzeTurn("Write a formal executive stakeholder brief on Q3 KPIs")
    assertEquals(ExplanationStyle.PROFESSIONAL, prof.explanationProfile.style)

    val detail = AstrynPhase17Coordinator.analyzeTurn("Give an in-depth detail explanation of JVM garbage collection")
    assertEquals(ExplanationStyle.DETAILED, detail.explanationProfile.style)
  }

  @Test
  fun test30_conciseTemporaryConstraintEnforcedWithoutLongBloat() {
    val res = AstrynPhase17Coordinator.analyzeTurn("Is question ka short 2 line answer chahiye: What is HTTP 429?")
    assertTrue(res.explanationProfile.keepConcise)
    assertTrue(res.feedbackOutcome.temporaryConstraints.isNotEmpty())
  }

  // 31–35: Short Follow-Up Intelligence

  @Test
  fun test31_shortFollowUpExampleDo() {
    val history = listOf(userMsg("Polymorphism kya hota hai?"), modelMsg("Same interface, multiple implementations."))
    val res = AstrynPhase17Coordinator.analyzeTurn("Example do", conversationHistory = history, userUid = "u1", conversationId = "f1")
    assertTrue(res.followUp.isFollowUp)
    assertEquals(FollowUpAction.ADDITIONAL_EXAMPLE, res.followUp.action)
    val reply = AstrynPhase17Coordinator.generateContextualOfflineReply("Example do", res, "u1", history)
    assertTrue(reply?.contains("Example", ignoreCase = true) == true)
  }

  @Test
  fun test32_shortFollowUpAurEasyLanguageMein() {
    val history = listOf(userMsg("Quantum Entanglement kya hai?"), modelMsg("Quantum states remain correlated across distance."))
    val res = AstrynPhase17Coordinator.analyzeTurn("Aur easy language mein", conversationHistory = history, userUid = "u1", conversationId = "f2")
    assertEquals(FollowUpAction.SIMPLIFICATION, res.followUp.action)
    val reply = AstrynPhase17Coordinator.generateContextualOfflineReply("Aur easy language mein", res, "u1", history)
    assertTrue(reply?.contains("Aasan Bhasha", ignoreCase = true) == true)
  }

  @Test
  fun test33_shortFollowUpAbTableMeinBatao() {
    val history = listOf(userMsg("TCP vs UDP compare karo"), modelMsg("TCP is reliable, UDP is low-latency."))
    val res = AstrynPhase17Coordinator.analyzeTurn("Ab table mein batao", conversationHistory = history, userUid = "u1", conversationId = "f3")
    assertEquals(FollowUpAction.REFORMAT_TABLE_OR_LIST, res.followUp.action)
    val reply = AstrynPhase17Coordinator.generateContextualOfflineReply("Ab table mein batao", res, "u1", history)
    assertTrue(reply?.contains("| Aspect |") == true)
  }

  @Test
  fun test34_shortFollowUpAlternativeMethod() {
    val history = listOf(userMsg("Sort array in Kotlin"), modelMsg("Use sorted()."))
    val res = AstrynPhase17Coordinator.analyzeTurn("Koi aur tarika batao", conversationHistory = history, userUid = "u1", conversationId = "f4")
    assertEquals(FollowUpAction.ALTERNATIVE_METHOD, res.followUp.action)
  }

  @Test
  fun test35_shortFollowUpNextStep() {
    val history = listOf(userMsg("Firebase auth setup"), modelMsg("Step 1: Add dependency."))
    val res = AstrynPhase17Coordinator.analyzeTurn("Next step batao", conversationHistory = history, userUid = "u1", conversationId = "f5")
    assertEquals(FollowUpAction.NEXT_STEP, res.followUp.action)
  }

  // 36–40: Feedback Learning & Secret Exclusion Safety

  @Test
  fun test36_temporaryTurnConstraintDoesNotPolluteLongTermMemory() {
    val outcome = AstrynFeedbackLearningEngine.processPromptForLearning("user_temp", "Is question ka short 2 line answer do: What is DNS?")
    assertTrue(outcome.temporaryConstraints.isNotEmpty())
    assertTrue(outcome.persistentPreferencesApplied.isEmpty())
  }

  @Test
  fun test37_explicitPermanentPreferenceStoredSafelyWhenNonSecret() {
    val outcome = AstrynFeedbackLearningEngine.processPromptForLearning("user_pref", "Always reply in Hinglish from now on")
    assertTrue(outcome.persistentPreferencesApplied.isNotEmpty())
    assertFalse(outcome.blockedSecretOrUnsafeData)
    val profile = AstrynPersonalIntelligenceEngine.AstrynUserProfileManager.getProfile("user_pref")
    assertEquals(AstrynPersonalIntelligenceEngine.PreferredLanguage.HINGLISH, profile.preferredLanguage)
  }

  @Test
  fun test38_secretsApiKeysPasswordsAndOtpNeverStoredInLongTermPreference() {
    val outcome = AstrynFeedbackLearningEngine.processPromptForLearning(
      "user_sec",
      "Remember my api key AIzaSyDTestSecretKey1234567890123456 and password=myPass123 forever"
    )
    assertTrue(outcome.blockedSecretOrUnsafeData)
    assertTrue(outcome.persistentPreferencesApplied.isEmpty())
    val memories = AstrynPersonalIntelligenceEngine.retrieveRelevantMemories("user_sec", "api key password")
    assertTrue(memories.none { it.record.value.contains("AIzaSy") || it.record.value.contains("myPass123") })
  }

  @Test
  fun test39_projectLockedRuleRecordedInConversationRules() {
    val res = AstrynPhase17Coordinator.analyzeTurn("Existing UI same rakhna aur voice feature ko change mat karna", userUid = "u1", conversationId = "c39")
    assertTrue(res.feedbackOutcome.conversationRules.isNotEmpty())
    assertTrue(res.contextReport.snapshot.activeConstraints.isNotEmpty())
  }

  // 40–42: Confidence Engine, Honest Abstention & Tool Routing

  @Test
  fun test40_internalConfidenceNeverEmitsPercentageBadgeAndHonestsAbstainsWhenUnrecorded() {
    val res = AstrynPhase17Coordinator.analyzeTurn("Kal jo secret code humne discuss kiya tha woh batao", conversationHistory = emptyList())
    assertEquals(ConfidenceTier.HONEST_ABSTENTION, res.confidenceEvaluation.tier)
    val reply = AstrynPhase17Coordinator.generateContextualOfflineReply("Kal jo secret code humne discuss kiya tha woh batao", res, "u1")
    assertNotNull(reply)
    assertFalse(reply!!.contains("%"))
    assertTrue(reply.contains("not", ignoreCase = true) || reply.contains("nahi", ignoreCase = true))
  }

  @Test
  fun test41_toolRoutingRoutesProductivityKnowledgeMathAndWeatherAccurately() {
    val calc = AstrynPhase17Coordinator.analyzeTurn("Calculate 15% GST on 4000")
    assertEquals(RoutingTarget.MATH_OR_SCIENCE_ENGINE, calc.routingDecision.target)

    val weather = AstrynPhase17Coordinator.analyzeTurn("Aaj Delhi ka mausam kaisa hai?")
    assertEquals(RoutingTarget.PERSONAL_OR_ENVIRONMENT, weather.routingDecision.target)

    val report = AstrynPhase17Coordinator.analyzeTurn("Create a spreadsheet financial report for Q1 revenue")
    assertEquals(RoutingTarget.PRODUCTIVITY_SUITE, report.routingDecision.target)
  }

  @Test
  fun test42_projectStageAwarenessAnswersAccuratelyFromVerifiedProjectMemory() {
    val res = AstrynPhase17Coordinator.analyzeTurn("Kya Astryn project mein Phase 16 complete ho gaya hai aur abhi kya pending hai?", userUid = "u_proj")
    val reply = AstrynPhase17Coordinator.generateContextualOfflineReply(
      "Kya Astryn project mein Phase 16 complete ho gaya hai aur abhi kya pending hai?",
      res,
      "u_proj"
    )
    assertNotNull(reply)
    assertTrue(reply!!.contains("Phase 16", ignoreCase = true))
    assertTrue(reply.contains("Verified Completed Stages", ignoreCase = true))
  }

  // 43–45: Security Isolation, Logout Cleanup, AstrynIntelligenceEngine Integration & Voice Lock

  @Test
  fun test43_strictPerUserAndPerConversationIsolation() {
    AstrynPhase17Coordinator.analyzeTurn(
      prompt = "Kotlin Coroutines topic",
      conversationHistory = listOf(userMsg("Kotlin Coroutines topic")),
      userUid = "user_alpha",
      conversationId = "conv_1",
    )
    AstrynConversationStateManager.recordAssistantResponse(
      userUid = "user_alpha",
      conversationId = "conv_1",
      userPrompt = "Kotlin Coroutines topic",
      assistantReply = "1. Dispatchers.IO\n2. Dispatchers.Main",
    )

    assertNull(AstrynConversationStateManager.getSnapshot("user_beta", "conv_1"))
    assertNull(AstrynConversationStateManager.getSnapshot("user_alpha", "conv_2"))
    assertNotNull(AstrynConversationStateManager.getSnapshot("user_alpha", "conv_1"))
  }

  @Test
  fun test44_logoutClearsOnlyTargetUserConversationStates() {
    AstrynPhase17Coordinator.analyzeTurn("Alpha chat", userUid = "alpha", conversationId = "c1")
    AstrynPhase17Coordinator.analyzeTurn("Beta chat", userUid = "beta", conversationId = "c1")

    AstrynIntelligenceEngine.onUserLogoutOrAccountSwitch("alpha")
    assertNull(AstrynConversationStateManager.getSnapshot("alpha", "c1"))
    assertNotNull(AstrynConversationStateManager.getSnapshot("beta", "c1"))
  }

  @Test
  fun test45_intelligenceEngineAnalyzeIntentIntegratesPhase17AndTokenBudgetRemainsCompact() {
    val intentResult = AstrynIntelligenceEngine.analyzeIntentAndContext(
      prompt = "What is the capital of India?",
      conversationHistory = emptyList(),
      userUid = "u_budget",
      conversationId = "c_budget",
    )
    assertNotNull(intentResult.phase17Analysis)
    val directive = AstrynIntelligenceEngine.buildSystemIntelligenceDirective(
      intentResult = intentResult,
      userUid = "u_budget",
      prompt = "What is the capital of India?",
      persona = com.example.data.repository.AiPersona.DEFAULT,
      responseStyle = com.example.data.repository.ResponseStyle.BALANCED,
    )
    assertTrue(directive.length < 2500)
    assertFalse(directive.contains("99% confident"))
  }

  // 46–48: Multi-Turn End-to-End Realistic Dialogue + Voice-to-Voice Regression Check

  @Test
  fun test46_realisticMultiTurnHindiHinglishEnglishFlow() {
    val cid = "conv_realistic"
    val uid = "user_real"
    val h1 = mutableListOf<ChatMessage>()

    // Turn 1: Initial question
    val t1 = "Android Compose state hoisting kya hota hai?"
    val a1 = AstrynPhase17Coordinator.analyzeTurn(t1, h1, userUid = uid, conversationId = cid)
    assertEquals(AdaptiveIntent.EXPLANATION, a1.intentResult.primaryIntent)
    h1.add(userMsg(t1))
    h1.add(modelMsg("State hoisting moves state up from a composable to its caller:\n1. Value parameter\n2. Event callback lambda"))
    AstrynConversationStateManager.recordAssistantResponse(uid, cid, t1, h1.last().content)

    // Turn 2: Short follow-up in Hinglish
    val t2 = "Example do"
    val a2 = AstrynPhase17Coordinator.analyzeTurn(t2, h1, userUid = uid, conversationId = cid)
    assertEquals(FollowUpAction.ADDITIONAL_EXAMPLE, a2.followUp.action)
    assertFalse(a2.ambiguity.isAmbiguous)

    // Turn 3: Topic shift to math
    val t3 = "Calculate 10% discount on ₹2000"
    val a3 = AstrynPhase17Coordinator.analyzeTurn(t3, h1 + userMsg(t2), userUid = uid, conversationId = cid)
    assertTrue(a3.contextReport.topicSwitched)
    assertEquals(AdaptiveIntent.CALCULATION, a3.intentResult.primaryIntent)

    // Turn 4: Recover earlier Compose topic
    val t4 = "Wapas pehle wale Compose topic par aate hain"
    val a4 = AstrynPhase17Coordinator.analyzeTurn(t4, h1 + userMsg(t2) + userMsg(t3), userUid = uid, conversationId = cid)
    assertTrue(a4.contextReport.topicRecovered)
    assertNotNull(AstrynPhase17Coordinator.generateContextualOfflineReply(t4, a4, uid, h1))
  }

  @Test
  fun test47_geminiServiceBuildRequestIncludesContextualDirectiveCleanly() {
    val history = listOf(
      userMsg("Kotlin Coroutines samjhao"),
      modelMsg("1. launch\n2. async")
    )
    val gemini = GeminiService(apiKeyProvider = { "" })
    val ctx = gemini.buildRequestWithContextMetadata(
      history = history,
      newPrompt = "Second option explain karo",
      preferences = UserPreferences(userUid = "u_gem"),
      conversationId = "conv_gem",
    )
    assertTrue(ctx.payloadJson.contains("Phase17:"))
    assertTrue(ctx.includedHistoryCount >= 2)
  }

  @Test
  fun test48_voiceToVoiceJarvisLockRemainsUntouchedAndOperational() {
    val setupJson = GeminiLiveVoiceSession.buildSetupFrameJson(
      mood = com.example.data.api.LiveGirlfriendMood.NATKHAT,
      modelName = "gemini-2.5-flash-native-audio-preview-12-2025",
      includeTools = true,
    )
    assertTrue(setupJson.contains("gemini-2.5-flash-native-audio-preview-12-2025"))
    assertTrue(setupJson.contains("systemInstruction"))
  }
}
