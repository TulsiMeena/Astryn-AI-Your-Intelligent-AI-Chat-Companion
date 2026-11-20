package com.example.data.api

import com.example.data.model.ChatAttachment
import com.example.data.model.ChatMessage
import com.example.data.model.MessageRole
import com.example.data.repository.UserPreferences
import java.util.Locale

/**
 * ASTRYN AI — PHASE 17 (PART 2)
 * ADAPTIVE INTENT, AMBIGUITY DETECTION, EXPLANATION ADAPTATION, FOLLOW-UP INTELLIGENCE,
 * FEEDBACK LEARNING, RESPONSE CONFIDENCE & CONTEXTUAL TOOL ROUTING ENGINE
 */
object AstrynAdaptiveIntelligenceEngines {

  // =========================================================================
  // 1. MULTI-CATEGORY INTENT UNDERSTANDING ENGINE (23 INTENTS)
  // =========================================================================

  enum class AdaptiveIntent {
    GENERAL_CONVERSATION,
    INFORMATION_SEEKING,
    EXPLANATION,
    LEARNING,
    COMPARISON,
    CALCULATION,
    RESEARCH,
    CODING,
    DEBUGGING,
    DOCUMENT_CREATION,
    FILE_ANALYSIS,
    PLANNING,
    TASK_EXECUTION,
    CREATIVE_WRITING,
    TRANSLATION,
    SUMMARIZATION,
    FOLLOW_UP,
    CORRECTION,
    CONTINUATION,
    CLARIFICATION,
    FEEDBACK,
    EMOTIONAL_SUPPORT,
    TROUBLESHOOTING,
  }

  data class IntentClassificationResult(
    val primaryIntent: AdaptiveIntent,
    val secondaryIntents: List<AdaptiveIntent> = emptyList(),
    val confidence: Double,
    val isContextDependent: Boolean,
    val reason: String,
  )

  object AstrynIntentUnderstandingEngine {

    fun classifyIntent(
      prompt: String,
      contextReport: AstrynContextualIntelligenceEngine.ContextualUnderstandingReport,
      attachments: List<ChatAttachment> = emptyList(),
    ): IntentClassificationResult {
      val clean = prompt.trim()
      val lower = clean.lowercase(Locale.ROOT)
      val tokens = AstrynContextualIntelligenceEngine.tokenizeWords(lower)
      val secondary = mutableListOf<AdaptiveIntent>()
      val hasHistory = contextReport.snapshot.activeTopic != null || contextReport.snapshot.indexedTurns.isNotEmpty()

      // 1. Check Correction / Feedback / Continuation / Clarification / Follow-up first when context exists
      val isCorrection =
        lower.containsAny(
          "nahi main", "nhi main", "galat hai", "not that", "i meant", "mera matlab",
          "use this instead", "ye nahi", "wo nahi", "instead of", "correction:",
          "maine kotlin bola", "nahi java", "nahi hindi mein", "wrong answer", "incorrect",
          "गलत है", "नहीं मेरा मतलब", "बदल दो",
        )
      if (isCorrection) {
        return IntentClassificationResult(
          primaryIntent = AdaptiveIntent.CORRECTION,
          secondaryIntents = listOf(AdaptiveIntent.FOLLOW_UP),
          confidence = 0.92,
          isContextDependent = true,
          reason = "User explicitly corrected an earlier statement, assumption, or constraint",
        )
      }

      val isPositiveOrQualityFeedback =
        lower.containsAny(
          "good answer", "great explanation", "nice!", "bahut badhiya", "sahi hai", "perfect",
          "too long", "bahut lamba", "bahut hard", "too technical", "confusing hai", "samajh nahi aaya",
          "aur simple", "shorter please", "brief please",
        ) && tokens.size <= 10
      if (isPositiveOrQualityFeedback && hasHistory) {
        val sec = if (lower.containsAny("aur simple", "shorter", "too long", "too technical", "samajh nahi aaya", "brief")) {
          listOf(AdaptiveIntent.FOLLOW_UP)
        } else emptyList()
        return IntentClassificationResult(
          primaryIntent = AdaptiveIntent.FEEDBACK,
          secondaryIntents = sec,
          confidence = 0.89,
          isContextDependent = true,
          reason = "User provided feedback on response quality, length, or tone",
        )
      }

      val isContinuation =
        lower.containsAny(
          "continue", "aage batao", "next step", "continue from there", "baaki batao",
          "proceed", "go on", "आगे बताओ", "जारी रखो",
        ) && tokens.size <= 9
      if (isContinuation) {
        return IntentClassificationResult(
          primaryIntent = AdaptiveIntent.CONTINUATION,
          secondaryIntents = listOf(AdaptiveIntent.FOLLOW_UP),
          confidence = 0.91,
          isContextDependent = true,
          reason = "User asked to continue the active explanation or workflow",
        )
      }

      val isEmotionalSupport =
        lower.containsAny(
          "stressed", "stress ho raha", "demotivated", "anxious", "tension ho rahi", "pareshan hun",
          "sad feel", "depressed", "akela", "burnt out", "burnout", "har maan", "give up",
          "bahut dar lag raha", "nervous hun", "परेसान", "तनाव",
        )
      if (isEmotionalSupport) {
        return IntentClassificationResult(
          primaryIntent = AdaptiveIntent.EMOTIONAL_SUPPORT,
          confidence = 0.88,
          isContextDependent = false,
          reason = "User expressed emotional stress or need for supportive encouragement",
        )
      }

      val isTranslation =
        lower.containsAny("translate", "anuvad", "anuvaad", "convert to hindi", "convert to english", "english mein convert", "hindi mein translate", "अनुवाद")
      if (isTranslation) {
        return IntentClassificationResult(
          primaryIntent = AdaptiveIntent.TRANSLATION,
          confidence = 0.92,
          isContextDependent = hasHistory && tokens.size <= 6,
          reason = "User requested language translation",
        )
      }

      val isSummarization =
        lower.containsAny("summarize", "summary", "saraansh", "tl;dr", "tldr", "short summary", "सारांश")
      if (isSummarization) {
        if (attachments.isNotEmpty() || contextReport.referenceBundle.references.any { it.targetType == AstrynContextualIntelligenceEngine.ReferenceTargetType.DOCUMENT }) {
          secondary.add(AdaptiveIntent.FILE_ANALYSIS)
        }
        return IntentClassificationResult(
          primaryIntent = AdaptiveIntent.SUMMARIZATION,
          secondaryIntents = secondary,
          confidence = 0.91,
          isContextDependent = attachments.isEmpty() && hasHistory,
          reason = "User requested a concise summary",
        )
      }

      val isDebugging =
        lower.containsAny(
          "exception", "stacktrace", "nullpointer", "crash", "compile error", "syntax error",
          "bug", "fix this code", "ye error kyun", "error aa raha", "not compiling", "failed test",
        )
      if (isDebugging) {
        return IntentClassificationResult(
          primaryIntent = AdaptiveIntent.DEBUGGING,
          secondaryIntents = listOf(AdaptiveIntent.TROUBLESHOOTING),
          confidence = 0.91,
          isContextDependent = contextReport.referenceBundle.hasExplicitOrImplicitReference,
          reason = "User reported a code bug, exception, or build failure",
        )
      }

      val isTroubleshooting =
        lower.containsAny(
          "not working", "kaam nahi kar raha", "wahi problem", "same problem", "still failing",
          "hang ho raha", "slow chal raha", "connection fail", "issue aa raha", "dikkat",
        )
      if (isTroubleshooting) {
        return IntentClassificationResult(
          primaryIntent = AdaptiveIntent.TROUBLESHOOTING,
          confidence = 0.87,
          isContextDependent = contextReport.referenceBundle.hasExplicitOrImplicitReference || hasHistory,
          reason = "User is troubleshooting a malfunctioning workflow or technical problem",
        )
      }

      val isFileAnalysis =
        attachments.isNotEmpty() ||
          contextReport.referenceBundle.references.any {
            it.targetType == AstrynContextualIntelligenceEngine.ReferenceTargetType.DOCUMENT
          } ||
          lower.containsAny("pdf mein", "is pdf", "is image", "is photo", "screenshot mein", "document analyze")
      if (isFileAnalysis && !lower.containsAny("create pdf", "generate report", "make spreadsheet")) {
        return IntentClassificationResult(
          primaryIntent = AdaptiveIntent.FILE_ANALYSIS,
          confidence = 0.90,
          isContextDependent = attachments.isEmpty(),
          reason = "Turn targets an uploaded or referenced document/image",
        )
      }

      val isCalculation =
        AstrynIntelligenceEngine.evaluateMathAndDiscountQuery(clean) != null ||
          Regex("""\b(calculate|calculation|emi|gst|discount|percentage|profit|loss|interest|compound|hisab|integral|derivative|matrix)\b""").containsMatchIn(lower) ||
          lower.contains("solve equation")
      if (isCalculation) {
        return IntentClassificationResult(
          primaryIntent = AdaptiveIntent.CALCULATION,
          confidence = 0.91,
          isContextDependent = hasHistory && contextReport.referenceBundle.hasExplicitOrImplicitReference,
          reason = "Prompt requires deterministic numerical, financial, or scientific calculation",
        )
      }

      val isComparison =
        lower.containsAny(" vs ", "versus", "compare", "comparison", "difference between", "farak", "antar", "better between", "konsa best", "kaun sa best")
      if (isComparison) {
        return IntentClassificationResult(
          primaryIntent = AdaptiveIntent.COMPARISON,
          confidence = 0.89,
          isContextDependent = contextReport.referenceBundle.hasExplicitOrImplicitReference,
          reason = "User requested side-by-side comparison or trade-off analysis",
        )
      }

      val isDocumentCreation =
        lower.containsAny(
          "create a document", "write a report", "generate pdf", "spreadsheet", "excel sheet",
          "presentation", "ppt", "slides", "resume banao", "proposal likho", "sop banao", "invoice banao",
          "email draft", "stakeholder email",
        )
      if (isDocumentCreation) {
        return IntentClassificationResult(
          primaryIntent = AdaptiveIntent.DOCUMENT_CREATION,
          confidence = 0.89,
          isContextDependent = contextReport.referenceBundle.hasExplicitOrImplicitReference,
          reason = "User requested structured document, report, sheet, or slide generation",
        )
      }

      val isCoding =
        lower.containsAny(
          "write code", "kotlin function", "python script", "compose ui", "jetpack compose",
          "algorithm", "sql query", "regex", "refactor", "class likho", "code likho", "implement",
          "api client", "viewmodel",
        )
      if (isCoding) {
        return IntentClassificationResult(
          primaryIntent = AdaptiveIntent.CODING,
          confidence = 0.88,
          isContextDependent = contextReport.referenceBundle.hasExplicitOrImplicitReference,
          reason = "User requested software implementation, code snippet, or refactoring",
        )
      }

      val isPlanning =
        lower.containsAny(
          "roadmap", "study plan", "schedule banao", "project plan", "milestones", "action plan",
          "step-by-step plan", "strategy", "planner", "daily plan", "yojana",
        )
      if (isPlanning) {
        return IntentClassificationResult(
          primaryIntent = AdaptiveIntent.PLANNING,
          confidence = 0.87,
          isContextDependent = hasHistory,
          reason = "User asked for structured planning, scheduling, or roadmap design",
        )
      }

      val isTaskExecution =
        lower.containsAny("delete", "send email", "share", "post ", "execute", "run workflow", "save to knowledge", "export ")
      if (isTaskExecution) {
        return IntentClassificationResult(
          primaryIntent = AdaptiveIntent.TASK_EXECUTION,
          confidence = 0.85,
          isContextDependent = contextReport.referenceBundle.hasExplicitOrImplicitReference,
          reason = "User requested executing a tool or state-changing action",
        )
      }

      val researchDetection =
        AstrynResearchEngine.ResearchPlanner.detectResearchIntent(
          prompt = clean,
          conversationHistory = emptyList(),
          attachments = attachments,
        )
      val isResearch =
        researchDetection.requiresDetailedReport ||
          researchDetection.requiresExternalSearch ||
          lower.containsAny(
            "research", "latest news", "latest ", "model updates", "current price",
            "live weather", "official notification", "verify with sources", "verify karo",
          )
      if (isResearch) {
        return IntentClassificationResult(
          primaryIntent = AdaptiveIntent.RESEARCH,
          secondaryIntents = listOf(AdaptiveIntent.INFORMATION_SEEKING),
          confidence = 0.87,
          isContextDependent = false,
          reason = "Query benefits from evidence verification, live grounding, or structured research",
        )
      }

      // Context follow-up check (short questions anchored to previous turn)
      if (hasHistory && AstrynContextualIntelligenceEngine.detectsFollowUpOrDeicticCue(clean)) {
        return IntentClassificationResult(
          primaryIntent = AdaptiveIntent.FOLLOW_UP,
          secondaryIntents = listOf(AdaptiveIntent.EXPLANATION),
          confidence = 0.88,
          isContextDependent = true,
          reason = "Short follow-up or deictic reference anchored to active conversation topic",
        )
      }

      val isClarification =
        lower.containsAny("what do you mean", "ye kya hota hai exactly", "confirm karo", "clarify", "did you mean", "iska matlab")
      if (isClarification) {
        return IntentClassificationResult(
          primaryIntent = AdaptiveIntent.CLARIFICATION,
          confidence = 0.84,
          isContextDependent = hasHistory,
          reason = "User asked for clarification on an earlier statement or term",
        )
      }

      val isLearning =
        lower.containsAny("learn", "seekhna", "teach me", "padhao", "syllabus", "interview prep", "exam", "quiz", "practice questions")
      if (isLearning) {
        return IntentClassificationResult(
          primaryIntent = AdaptiveIntent.LEARNING,
          secondaryIntents = listOf(AdaptiveIntent.EXPLANATION),
          confidence = 0.85,
          isContextDependent = false,
          reason = "User has an explicit learning or exam/interview preparation goal",
        )
      }

      val isExplanation =
        lower.containsAny("explain", "how does", "why does", "samjhao", "kaise kaam karta", "kya hota hai", "samjha do", "meaning of")
      if (isExplanation) {
        return IntentClassificationResult(
          primaryIntent = AdaptiveIntent.EXPLANATION,
          confidence = 0.86,
          isContextDependent = contextReport.referenceBundle.hasExplicitOrImplicitReference,
          reason = "User asked for a clear conceptual or practical explanation",
        )
      }

      val isCreative =
        lower.containsAny("story", "poem", "kavita", "script", "tagline", "slogan", "caption", "brainstorm", "ideas", "creative")
      if (isCreative) {
        return IntentClassificationResult(
          primaryIntent = AdaptiveIntent.CREATIVE_WRITING,
          confidence = 0.86,
          isContextDependent = false,
          reason = "User requested creative writing or ideation",
        )
      }

      val isGreeting =
        tokens.size <= 4 && lower.containsAny("hi", "hello", "hey", "namaste", "hii", "good morning", "good evening", "kaise ho", "how are you", "thanks", "thank you", "shukriya")
      if (isGreeting) {
        return IntentClassificationResult(
          primaryIntent = AdaptiveIntent.GENERAL_CONVERSATION,
          confidence = 0.93,
          isContextDependent = false,
          reason = "Natural greeting or conversational courtesy",
        )
      }

      val isQuestion = clean.endsWith("?") || lower.containsAny("what", "who", "when", "where", "which", "kya", "kab", "kaun", "kahan", "kitna")
      return if (isQuestion) {
        IntentClassificationResult(
          primaryIntent = AdaptiveIntent.INFORMATION_SEEKING,
          confidence = 0.78,
          isContextDependent = contextReport.referenceBundle.hasExplicitOrImplicitReference,
          reason = "Direct informational query",
        )
      } else {
        IntentClassificationResult(
          primaryIntent = AdaptiveIntent.GENERAL_CONVERSATION,
          confidence = 0.72,
          isContextDependent = false,
          reason = "Open conversational message",
        )
      }
    }

    fun mapToLegacyPrimaryIntent(adaptive: AdaptiveIntent): AstrynIntelligenceEngine.PrimaryIntent {
      return when (adaptive) {
        AdaptiveIntent.GENERAL_CONVERSATION -> AstrynIntelligenceEngine.PrimaryIntent.CASUAL_CONVERSATION
        AdaptiveIntent.INFORMATION_SEEKING -> AstrynIntelligenceEngine.PrimaryIntent.SIMPLE_FACTUAL_OR_DIRECT
        AdaptiveIntent.EXPLANATION, AdaptiveIntent.LEARNING -> AstrynIntelligenceEngine.PrimaryIntent.EDUCATIONAL_CONCEPT_EXPLANATION
        AdaptiveIntent.CALCULATION -> AstrynIntelligenceEngine.PrimaryIntent.MATH_AND_DISCOUNT_CALCULATION
        AdaptiveIntent.RESEARCH, AdaptiveIntent.COMPARISON -> AstrynIntelligenceEngine.PrimaryIntent.CURRENT_INFO_AND_RESEARCH
        AdaptiveIntent.DEBUGGING, AdaptiveIntent.TROUBLESHOOTING -> AstrynIntelligenceEngine.PrimaryIntent.CODE_DEBUGGING_AND_FIX
        AdaptiveIntent.FILE_ANALYSIS -> AstrynIntelligenceEngine.PrimaryIntent.MULTIMODAL_DOCUMENT_OR_IMAGE_ANALYSIS
        AdaptiveIntent.PLANNING, AdaptiveIntent.TASK_EXECUTION -> AstrynIntelligenceEngine.PrimaryIntent.AUTONOMOUS_TASK_PLANNING
        AdaptiveIntent.CREATIVE_WRITING -> AstrynIntelligenceEngine.PrimaryIntent.CREATIVE_CONCEPT_GENERATION
        AdaptiveIntent.FOLLOW_UP, AdaptiveIntent.CONTINUATION, AdaptiveIntent.CLARIFICATION, AdaptiveIntent.CORRECTION, AdaptiveIntent.FEEDBACK ->
          AstrynIntelligenceEngine.PrimaryIntent.OPTION_EXPANSION_FOLLOWUP
        else -> AstrynIntelligenceEngine.PrimaryIntent.GENERAL_KNOWLEDGE_OR_ASSISTANCE
      }
    }
  }

  // =========================================================================
  // 2. SMART AMBIGUITY DETECTION ENGINE (ZERO UNNECESSARY QUESTIONS)
  // =========================================================================

  enum class AmbiguityReason {
    NONE,
    UNDERSPECIFIED_TARGET_OR_PRONOUN,
    MISSING_CALCULATION_NUMBERS,
    AMBIGUOUS_DESTRUCTIVE_ACTION,
    AMBIGUOUS_EXTERNAL_SEND_OR_SHARE,
    MISSING_FILE_TARGET,
  }

  data class AmbiguityAssessment(
    val isAmbiguous: Boolean,
    val reason: AmbiguityReason = AmbiguityReason.NONE,
    val clarificationQuestions: List<String> = emptyList(),
    val blocksAutoExecution: Boolean = false,
    val explanation: String = "",
  )

  object AstrynAmbiguityDetectionEngine {

    fun assessAmbiguity(
      prompt: String,
      intent: IntentClassificationResult,
      contextReport: AstrynContextualIntelligenceEngine.ContextualUnderstandingReport,
      language: AstrynIntelligenceEngine.DetectedLanguage,
      attachments: List<ChatAttachment> = emptyList(),
    ): AmbiguityAssessment {
      val clean = prompt.trim()
      val lower = clean.lowercase(Locale.ROOT)
      val tokens = AstrynContextualIntelligenceEngine.tokenizeWords(lower)
      val refBundle = contextReport.referenceBundle

      // 1. Risky / Destructive or External Actions without an explicit unambiguous target -> ALWAYS block & ask 1 crisp confirmation
      val hasDestructiveVerb = lower.containsAny("delete", "remove", "wipe", "clear all", "hata do", "mita do", "delte")
      val hasExternalActionVerb = lower.containsAny("send email", "bhej do", "bhejo", "post publicly", "share externally", "forward ")

      val hasVaguePronoun = tokens.any {
        it in setOf("isko", "usko", "ye", "yeh", "wo", "woh", "it", "this", "that", "wahi", "same", "उसको", "इसको", "वह", "यह")
      }
      val namedSpecificTarget =
        attachments.isNotEmpty() ||
          refBundle.references.any { it.confidence >= 0.85 && it.resolvedTarget.length >= 6 }

      if (hasDestructiveVerb && (hasVaguePronoun || !namedSpecificTarget && contextReport.snapshot.activeTopic == null)) {
        val question = when (language) {
          AstrynIntelligenceEngine.DetectedLanguage.HINDI_DEVANAGARI ->
            "कृपया साफ़ बताएं कि आप किस फ़ाइल, मैसेज या डेटा को डिलीट करना चाहते हैं?"
          AstrynIntelligenceEngine.DetectedLanguage.HINGLISH ->
            "Kripya confirm karein — aap exact kis file, message, ya item ko delete karna chahte hain?"
          AstrynIntelligenceEngine.DetectedLanguage.ENGLISH ->
            "Could you confirm the exact item, file, or record you want to delete before I proceed?"
        }
        return AmbiguityAssessment(
          isAmbiguous = true,
          reason = AmbiguityReason.AMBIGUOUS_DESTRUCTIVE_ACTION,
          clarificationQuestions = listOf(question),
          blocksAutoExecution = true,
          explanation = "Prevented automatic destructive action on an ambiguous reference.",
        )
      }

      if (hasExternalActionVerb && (hasVaguePronoun || lower.containsAny("wahi file", "that file", "wo file"))) {
        val attachedCount = contextReport.snapshot.activeTopic?.documentRefs?.size ?: 0
        if (attachments.isEmpty() && attachedCount != 1) {
          val question = when (language) {
            AstrynIntelligenceEngine.DetectedLanguage.HINDI_DEVANAGARI ->
              "आप कौनसी फ़ाइल और किस प्राप्तकर्ता (recipient) को भेजना चाहते हैं?"
            AstrynIntelligenceEngine.DetectedLanguage.HINGLISH ->
              "Aap kaunsi exact file aur kisko bhejna chahte hain? Kripya naam bataiye."
            AstrynIntelligenceEngine.DetectedLanguage.ENGLISH ->
              "Which exact file do you want to send, and who is the recipient?"
          }
          return AmbiguityAssessment(
            isAmbiguous = true,
            reason = AmbiguityReason.AMBIGUOUS_EXTERNAL_SEND_OR_SHARE,
            clarificationQuestions = listOf(question),
            blocksAutoExecution = true,
            explanation = "External send/share request lacked an unambiguous file or recipient target.",
          )
        }
      }

      // 2. Calculation request missing numbers AND no active math/document context
      val hasNumbers = Regex("""\d""").containsMatchIn(clean)
      if (intent.primaryIntent == AdaptiveIntent.CALCULATION &&
        !hasNumbers &&
        AstrynIntelligenceEngine.evaluateMathAndDiscountQuery(clean) == null &&
        contextReport.snapshot.activeTopic?.documentRefs.isNullOrEmpty()
      ) {
        val question = when (language) {
          AstrynIntelligenceEngine.DetectedLanguage.HINDI_DEVANAGARI ->
            "कृपया वे संख्याएँ या मान (numbers/values) साझा करें जिनकी गणना करनी है।"
          AstrynIntelligenceEngine.DetectedLanguage.HINGLISH ->
            "Kripya numbers aur values bataiye (jaise principal, rate ya price), taaki main exact calculation kar sakun."
          AstrynIntelligenceEngine.DetectedLanguage.ENGLISH ->
            "Please share the numbers or values you want me to calculate."
        }
        return AmbiguityAssessment(
          isAmbiguous = true,
          reason = AmbiguityReason.MISSING_CALCULATION_NUMBERS,
          clarificationQuestions = listOf(question),
          blocksAutoExecution = false,
          explanation = "Calculation requested without numeric inputs or active table context.",
        )
      }

      val hasPastRecallPhrase =
        lower.containsAny("kal jo", "pichle chat", "pichle saal", "earlier conversation", "secret code humne", "last conversation", "unrecorded meeting")
      if (hasPastRecallPhrase && contextReport.snapshot.indexedTurns.isEmpty()) {
        return AmbiguityAssessment(isAmbiguous = false)
      }

      // 3. Bare pronoun / vague follow-up with insufficient reference resolution confidence
      if (refBundle.needsClarification) {
        val question = when (language) {
          AstrynIntelligenceEngine.DetectedLanguage.HINDI_DEVANAGARI ->
            refBundle.clarificationHint ?: "कृपया बताएं कि आप किस विषय या संदेश के बारे में पूछ रहे हैं?"
          AstrynIntelligenceEngine.DetectedLanguage.HINGLISH ->
            refBundle.clarificationHint ?: "Kripya thoda clear karein — aap kis topic, code, ya file ki baat kar rahe hain?"
          AstrynIntelligenceEngine.DetectedLanguage.ENGLISH ->
            "Could you clarify which topic, option, or item you are referring to?"
        }
        return AmbiguityAssessment(
          isAmbiguous = true,
          reason = AmbiguityReason.UNDERSPECIFIED_TARGET_OR_PRONOUN,
          clarificationQuestions = listOf(question),
          blocksAutoExecution = false,
          explanation = "Reference confidence (${refBundle.overallConfidence}) was below threshold.",
        )
      }

      // Context resolved cleanly -> NO unnecessary questions!
      return AmbiguityAssessment(isAmbiguous = false)
    }
  }

  // =========================================================================
  // 3. ADAPTIVE EXPLANATION ENGINE (8 DEPTH / STYLE MODES)
  // =========================================================================

  enum class ExplanationStyle {
    SIMPLE,
    DETAILED,
    STEP_BY_STEP,
    TECHNICAL,
    BEGINNER_FRIENDLY,
    PROFESSIONAL,
    EXAMPLE_BASED,
    EXAM_ORIENTED,
  }

  data class ExplanationProfile(
    val style: ExplanationStyle,
    val language: AstrynIntelligenceEngine.DetectedLanguage,
    val keepConcise: Boolean,
    val directiveHint: String,
  )

  object AstrynAdaptiveExplanationEngine {

    fun determineExplanationProfile(
      prompt: String,
      intent: IntentClassificationResult,
      detectedLanguage: AstrynIntelligenceEngine.DetectedLanguage,
      expertiseLevel: AstrynIntelligenceEngine.UserExpertiseLevel,
      snapshot: AstrynContextualIntelligenceEngine.ConversationContextSnapshot,
      userPreferences: UserPreferences? = null,
    ): ExplanationProfile {
      val lower = prompt.lowercase(Locale.ROOT)
      val allConstraintText = (snapshot.temporaryTurnConstraints + snapshot.activeConstraints).joinToString(" ").lowercase(Locale.ROOT)

      val wantsShort =
        lower.containsAny("short", "brief", "concise", "ek line", "2 line", "chhota", "tl;dr", "tldr", "कम शब्दों") ||
          allConstraintText.containsAny("short", "brief", "concise")

      val style = when {
        lower.containsAny("example", "udahaaran", "udaharan", "उदाहरण", "practical use", "use case") ->
          ExplanationStyle.EXAMPLE_BASED
        lower.containsAny("exam", "ssc", "upsc", "mcq", "board", "viva", "definition aur points", "marks", "परीक्षा") ->
          ExplanationStyle.EXAM_ORIENTED
        lower.containsAny("step by step", "step-by-step", "steps mein", "kaise banau", "roadmap", "checklist") ->
          ExplanationStyle.STEP_BY_STEP
        lower.containsAny("beginner", "bilkul basic", "asan bhasha", "easy language", "saral", "बिल्कुल आसान", "bacche") ||
          expertiseLevel == AstrynIntelligenceEngine.UserExpertiseLevel.BEGINNER ->
          ExplanationStyle.BEGINNER_FRIENDLY
        lower.containsAny("architecture", "production", "thread-safe", "internals", "benchmark", "deep technical", "oom", "complexity") ||
          intent.primaryIntent == AdaptiveIntent.DEBUGGING ||
          expertiseLevel == AstrynIntelligenceEngine.UserExpertiseLevel.ADVANCED ->
          ExplanationStyle.TECHNICAL
        lower.containsAny("executive", "stakeholder", "formal", "client", "business", "professional", "report") ||
          intent.primaryIntent == AdaptiveIntent.DOCUMENT_CREATION ->
          ExplanationStyle.PROFESSIONAL
        lower.containsAny("detail", "deep dive", "in-depth", "vistar", "pura explain", "detail mein") ->
          ExplanationStyle.DETAILED
        else -> ExplanationStyle.SIMPLE
      }

      // Honor explicit temporary language request or stored personal preference
      val effectiveLang = when {
        lower.containsAny("hindi mein", "हिन्दी में", "शुद्ध हिंदी") || prompt.any { it in '\u0900'..'\u097F' } ->
          AstrynIntelligenceEngine.DetectedLanguage.HINDI_DEVANAGARI
        lower.containsAny("hinglish mein", "hinglish") ->
          AstrynIntelligenceEngine.DetectedLanguage.HINGLISH
        lower.containsAny("in english", "only english") ->
          AstrynIntelligenceEngine.DetectedLanguage.ENGLISH
        else -> detectedLanguage
      }

      val directive = buildString {
        append("Style=${style.name}")
        if (wantsShort) append(" (Concise)")
        append(", Lang=${effectiveLang.name}")
        when (style) {
          ExplanationStyle.SIMPLE -> append(" -> Clear direct explanation without unnecessary jargon.")
          ExplanationStyle.DETAILED -> append(" -> Thorough multi-aspect breakdown with nuances and edge cases.")
          ExplanationStyle.STEP_BY_STEP -> append(" -> Ordered actionable steps from start to verification.")
          ExplanationStyle.TECHNICAL -> append(" -> Accurate engineering terminology, edge cases, and complexity/safety notes.")
          ExplanationStyle.BEGINNER_FRIENDLY -> append(" -> Everyday analogies, simple vocabulary, zero assumed jargon.")
          ExplanationStyle.PROFESSIONAL -> append(" -> Crisp executive clarity suitable for stakeholders/clients.")
          ExplanationStyle.EXAMPLE_BASED -> append(" -> Lead with concrete, relatable real-world examples and code/numbers.")
          ExplanationStyle.EXAM_ORIENTED -> append(" -> Crisp definition, key bullet points, formulas, and exam-ready takeaways.")
        }
      }

      return ExplanationProfile(
        style = style,
        language = effectiveLang,
        keepConcise = wantsShort,
        directiveHint = directive,
      )
    }
  }

  // =========================================================================
  // 4. SMART FOLLOW-UP CONVERSATION ENGINE
  // =========================================================================

  enum class FollowUpAction {
    NONE,
    FOLLOW_UP_QUESTION,
    ADDITIONAL_EXAMPLE,
    SIMPLIFICATION,
    EXPANSION,
    CORRECTION,
    ALTERNATIVE_METHOD,
    CONTINUATION,
    COMPARISON,
    REFORMAT_TABLE_OR_LIST,
    NEXT_STEP,
  }

  data class FollowUpInterpretation(
    val isFollowUp: Boolean,
    val action: FollowUpAction = FollowUpAction.NONE,
    val anchoredTopic: String? = null,
    val avoidRepeatingPriorIntro: Boolean = true,
    val directive: String = "",
  )

  object AstrynFollowUpIntelligenceEngine {

    fun interpretFollowUp(
      prompt: String,
      intent: IntentClassificationResult,
      contextReport: AstrynContextualIntelligenceEngine.ContextualUnderstandingReport,
    ): FollowUpInterpretation {
      val clean = prompt.trim()
      val lower = clean.lowercase(Locale.ROOT)
      val activeTopic = contextReport.snapshot.activeTopic?.topicLabel

      if (intent.primaryIntent == AdaptiveIntent.CORRECTION) {
        return FollowUpInterpretation(
          isFollowUp = true,
          action = FollowUpAction.CORRECTION,
          anchoredTopic = activeTopic,
          directive = "Apply user's correction immediately to active topic '${activeTopic ?: "current thread"}' without defending the mistake.",
        )
      }

      if (intent.primaryIntent == AdaptiveIntent.CONTINUATION) {
        val nextStepAction = if (lower.containsAny("next step", "ab aage kya", "is ke baad", "after this")) {
          FollowUpAction.NEXT_STEP
        } else {
          FollowUpAction.CONTINUATION
        }
        return FollowUpInterpretation(
          isFollowUp = true,
          action = nextStepAction,
          anchoredTopic = activeTopic,
          directive = "Continue directly from the last covered step of '${activeTopic ?: "active task"}' without repeating earlier steps.",
        )
      }

      val action = when {
        lower.containsAny("example do", "give example", "ek aur example", "more example", "practical use", "real life use", "उदाहरण") ->
          FollowUpAction.ADDITIONAL_EXAMPLE
        lower.containsAny("aur easy", "easy language", "aur simple", "simpler", "samajh nahi aaya", "bacche ki tarah", "आसान भाषा") ->
          FollowUpAction.SIMPLIFICATION
        lower.containsAny("table mein", "table format", "comparison table", "list mein", "bullet points", "तालिका") ->
          FollowUpAction.REFORMAT_TABLE_OR_LIST
        lower.containsAny("koi aur tarika", "alternative", "another way", "better method", "is ke alawa") ->
          FollowUpAction.ALTERNATIVE_METHOD
        lower.containsAny("next step", "ab aage kya", "is ke baad", "after this") ->
          FollowUpAction.NEXT_STEP
        lower.containsAny("compare", "farak", "difference", "vs ") && contextReport.referenceBundle.hasExplicitOrImplicitReference ->
          FollowUpAction.COMPARISON
        lower.containsAny("detail mein", "expand", "explain more", "second option", "first option", "dusra option", "elaborate") ->
          FollowUpAction.EXPANSION
        intent.primaryIntent == AdaptiveIntent.FOLLOW_UP || contextReport.referenceBundle.hasExplicitOrImplicitReference ->
          FollowUpAction.FOLLOW_UP_QUESTION
        else -> FollowUpAction.NONE
      }

      if (action == FollowUpAction.NONE) {
        return FollowUpInterpretation(isFollowUp = false)
      }

      val resolvedSnippet = contextReport.referenceBundle.primaryTarget?.resolvedTarget?.take(120)
      val hint = buildString {
        append("FollowUp=${action.name}")
        if (!activeTopic.isNullOrBlank()) append(" | Topic='$activeTopic'")
        if (!resolvedSnippet.isNullOrBlank()) append(" | Target='$resolvedSnippet'")
        append(" | Build directly upon prior response without repeating introductory definitions.")
      }

      return FollowUpInterpretation(
        isFollowUp = true,
        action = action,
        anchoredTopic = activeTopic,
        avoidRepeatingPriorIntro = true,
        directive = hint,
      )
    }
  }

  // =========================================================================
  // 5. FEEDBACK & CORRECTION LEARNING ENGINE (WITH SECRET GUARD)
  // =========================================================================

  enum class FeedbackScope {
    TEMPORARY_TURN_ONLY,
    CONVERSATION_RULE,
    CORRECTION,
    PERSISTENT_USER_PREFERENCE,
  }

  data class LearnedFeedbackOutcome(
    val corrections: List<String> = emptyList(),
    val temporaryConstraints: List<String> = emptyList(),
    val conversationRules: List<String> = emptyList(),
    val persistentPreferencesApplied: List<String> = emptyList(),
    val blockedSecretOrUnsafeData: Boolean = false,
  )

  object AstrynFeedbackLearningEngine {

    private val EXTRA_SECRET_PATTERNS = listOf(
      Regex("""AIza[0-9A-Za-z\-_]{25,}"""),
      Regex("""AQ\.[0-9A-Za-z\-_.]{15,}"""),
      Regex("""sk-[0-9A-Za-z\-_]{16,}"""),
      Regex("""(?i)\b(aadhaar|pan\s*card|bank\s*account|ifsc|routing\s*number|atm\s*pin)\b\s*[:=]?\s*\S+"""),
    )

    fun processPromptForLearning(
      userUid: String?,
      prompt: String,
    ): LearnedFeedbackOutcome {
      val clean = prompt.trim()
      if (clean.isBlank()) return LearnedFeedbackOutcome()
      val lower = clean.lowercase(Locale.ROOT)

      val hasSecret = AstrynPersonalIntelligenceEngine.containsSensitiveOrForbiddenMemoryData(clean) ||
        EXTRA_SECRET_PATTERNS.any { it.containsMatchIn(clean) }

      val corrections = mutableListOf<String>()
      val tempConstraints = mutableListOf<String>()
      val convRules = mutableListOf<String>()
      val persistent = mutableListOf<String>()

      // 1. Temporary single-turn constraints (MUST NOT pollute long-term profile)
      if (lower.containsAny("is question ka", "is answer mein", "right now", "abhi ke liye", "for this turn", "bas iska")) {
        if (lower.containsAny("short", "brief", "2 line", "simple", "table", "hindi", "english")) {
          tempConstraints.add(clean.take(140))
        }
      } else if (lower.containsAny("shorter", "short answer", "table mein batao") && !lower.containsAny("always", "hamesha", "remember", "yaad rakh")) {
        tempConstraints.add(clean.take(140))
      }

      // 2. Explicit Corrections
      if (lower.containsAny("nahi", "galat", "wrong", "i meant", "instead of", "use kotlin", "use java", "बदल दो", "गलत")) {
        corrections.add(clean.take(160))
      }

      // 3. Project / Conversation rules (keep UI / voice lock etc.)
      if (lower.containsAny("same rakhna", "change mat karna", "touch mat karna", "preserve", "do not modify", "keep existing")) {
        convRules.add(clean.take(160))
      }

      // 4. Persistent Preferences (strictly non-secret, explicitly generalizable)
      val requestsPersistent =
        lower.containsAny("always", "hamesha", "remember", "yaad rakh", "prefer ", "mera naam", "my name is", "from now on")
      if (requestsPersistent && !hasSecret) {
        val cleanUser = userUid?.trim()?.takeIf { it.isNotEmpty() } ?: "anonymous_local_user"
        AstrynPersonalIntelligenceEngine.AstrynPreferenceEngine.ingestTurnForPreferencesAndMemory(
          userId = cleanUser,
          userPrompt = clean,
        )
        persistent.add(clean.take(140))
      }

      return LearnedFeedbackOutcome(
        corrections = corrections,
        temporaryConstraints = tempConstraints,
        conversationRules = convRules,
        persistentPreferencesApplied = persistent,
        blockedSecretOrUnsafeData = hasSecret,
      )
    }
  }

  // =========================================================================
  // 6. INTERNAL RESPONSE CONFIDENCE & UNCERTAINTY ENGINE (NO RAW % UI SPAM)
  // =========================================================================

  enum class ConfidenceTier {
    HIGH_CONFIDENCE,
    PARTIAL_CONTEXT,
    LOW_CONFIDENCE,
    HONEST_ABSTENTION,
  }

  data class ConfidenceEvaluation(
    val tier: ConfidenceTier,
    val score: Double,
    val riskFactors: List<String> = emptyList(),
    val behaviorDirective: String,
    val shouldAskClarificationFirst: Boolean = false,
  )

  object AstrynResponseConfidenceEngine {

    fun evaluateConfidence(
      prompt: String,
      intent: IntentClassificationResult,
      ambiguity: AmbiguityAssessment,
      contextReport: AstrynContextualIntelligenceEngine.ContextualUnderstandingReport,
      attachments: List<ChatAttachment> = emptyList(),
    ): ConfidenceEvaluation {
      val lower = prompt.lowercase(Locale.ROOT)
      val risks = mutableListOf<String>()

      // 1. Honest abstention check: asking for specific unrecorded past conversation without any session match
      val claimsUnrecordedMemory =
        lower.containsAny("kal jo", "pichle chat", "pichle saal", "earlier conversation", "secret code humne", "private bank otp", "unrecorded meeting") &&
          contextReport.snapshot.indexedTurns.isEmpty()

      if (claimsUnrecordedMemory) {
        risks.add("unverified_prior_memory")
        return ConfidenceEvaluation(
          tier = ConfidenceTier.HONEST_ABSTENTION,
          score = 0.18,
          riskFactors = risks,
          behaviorDirective = "Abstain honestly: state that no matching record exists in current session/memory and ask the user to share details.",
          shouldAskClarificationFirst = false,
        )
      }

      if (ambiguity.isAmbiguous) {
        risks.add(ambiguity.reason.name)
        return ConfidenceEvaluation(
          tier = ConfidenceTier.LOW_CONFIDENCE,
          score = 0.35,
          riskFactors = risks,
          behaviorDirective = "Ambiguity detected: ask exactly 1 clear clarification question before acting or guessing.",
          shouldAskClarificationFirst = true,
        )
      }

      if (claimsUnrecordedMemory) {
        risks.add("unverified_prior_memory")
        return ConfidenceEvaluation(
          tier = ConfidenceTier.HONEST_ABSTENTION,
          score = 0.18,
          riskFactors = risks,
          behaviorDirective = "Abstain honestly: state that no matching record exists in current session/memory and ask the user to share details.",
          shouldAskClarificationFirst = false,
        )
      }

      val isTimeSensitive =
        lower.containsAny("today's score", "live price", "stock price", "today weather", "aaj ka mausam", "breaking news", "official last date")
      if (isTimeSensitive) {
        risks.add("live_time_or_web_dependency")
      }

      val score = when {
        intent.primaryIntent == AdaptiveIntent.CALCULATION &&
          AstrynIntelligenceEngine.evaluateMathAndDiscountQuery(prompt) != null -> 0.96
        contextReport.referenceBundle.hasExplicitOrImplicitReference &&
          !contextReport.referenceBundle.isConfidenceSufficient -> 0.42
        isTimeSensitive && attachments.isEmpty() -> 0.68
        else -> 0.90
      }

      val tier = when {
        score >= 0.85 -> ConfidenceTier.HIGH_CONFIDENCE
        score >= 0.58 -> ConfidenceTier.PARTIAL_CONTEXT
        score >= 0.30 -> ConfidenceTier.LOW_CONFIDENCE
        else -> ConfidenceTier.HONEST_ABSTENTION
      }

      val directive = when (tier) {
        ConfidenceTier.HIGH_CONFIDENCE ->
          "Respond directly and authoritatively without hedging or exposing confidence metrics."
        ConfidenceTier.PARTIAL_CONTEXT ->
          "Give the clearest grounded answer and transparently note any time-sensitive or external verification limits."
        ConfidenceTier.LOW_CONFIDENCE ->
          "Provide minimal safe help and ask 1 clarifying question rather than guessing."
        ConfidenceTier.HONEST_ABSTENTION ->
          "Be honest about missing information; do not invent facts, numbers, or past chat contents."
      }

      return ConfidenceEvaluation(
        tier = tier,
        score = score,
        riskFactors = risks,
        behaviorDirective = directive,
        shouldAskClarificationFirst = false,
      )
    }
  }

  // =========================================================================
  // 7. CONTEXTUAL TOOL ROUTER & PROJECT STAGE AWARENESS
  // =========================================================================

  enum class RoutingTarget {
    DIRECT_CONVERSATION,
    MATH_OR_SCIENCE_ENGINE,
    RESEARCH_ENGINE,
    DOCUMENT_ANALYSIS,
    KNOWLEDGE_RAG,
    PRODUCTIVITY_SUITE,
    PERSONAL_OR_ENVIRONMENT,
    USER_CLARIFICATION,
    APPROVAL_REQUIRED_ACTION,
  }

  data class ToolRoutingDecision(
    val target: RoutingTarget,
    val requiresUserApproval: Boolean = false,
    val dedupFingerprint: String,
    val reason: String,
  )

  object AstrynContextualToolRouter {

    fun routeTurn(
      prompt: String,
      intent: IntentClassificationResult,
      ambiguity: AmbiguityAssessment,
      userUid: String?,
      conversationId: String,
      attachments: List<ChatAttachment> = emptyList(),
    ): ToolRoutingDecision {
      val clean = prompt.trim()
      val fingerprint = "${userUid.orEmpty()}|$conversationId|${clean.lowercase(Locale.ROOT).hashCode()}"

      if (ambiguity.blocksAutoExecution) {
        return ToolRoutingDecision(
          target = RoutingTarget.APPROVAL_REQUIRED_ACTION,
          requiresUserApproval = true,
          dedupFingerprint = fingerprint,
          reason = ambiguity.explanation,
        )
      }

      if (ambiguity.isAmbiguous) {
        return ToolRoutingDecision(
          target = RoutingTarget.USER_CLARIFICATION,
          requiresUserApproval = false,
          dedupFingerprint = fingerprint,
          reason = ambiguity.explanation,
        )
      }

      val researchDet = AstrynResearchEngine.ResearchPlanner.detectResearchIntent(
        prompt = clean,
        conversationHistory = emptyList(),
        attachments = attachments,
        userUid = userUid,
        conversationId = conversationId,
      )

      val target = when {
        AstrynPersonalIntelligenceEngine.shouldHandlePersonalOrEnvironmentQuery(clean) ->
          RoutingTarget.PERSONAL_OR_ENVIRONMENT
        attachments.isNotEmpty() || intent.primaryIntent == AdaptiveIntent.FILE_ANALYSIS ->
          RoutingTarget.DOCUMENT_ANALYSIS
        AstrynPhase15Coordinator.isKnowledgeQueryRequest(clean, userUid, attachments) ->
          RoutingTarget.KNOWLEDGE_RAG
        AstrynProductivityEngine.isProductivityRequest(clean, attachments) ||
          intent.primaryIntent == AdaptiveIntent.DOCUMENT_CREATION ->
          RoutingTarget.PRODUCTIVITY_SUITE
        intent.primaryIntent == AdaptiveIntent.CALCULATION ->
          RoutingTarget.MATH_OR_SCIENCE_ENGINE
        researchDet.requiresDetailedReport || researchDet.requiresExternalSearch ||
          intent.primaryIntent == AdaptiveIntent.RESEARCH ->
          RoutingTarget.RESEARCH_ENGINE
        else -> RoutingTarget.DIRECT_CONVERSATION
      }

      return ToolRoutingDecision(
        target = target,
        requiresUserApproval = false,
        dedupFingerprint = fingerprint,
        reason = "Routed ${intent.primaryIntent.name} to ${target.name}",
      )
    }

    /**
     * Answers project-stage & implementation-status queries honestly from verified Astryn project memory.
     */
    fun resolveProjectStatusSummary(userUid: String?, query: String): String {
      val cleanUser = userUid?.trim()?.takeIf { it.isNotEmpty() } ?: "anonymous_local_user"
      val project = AstrynPersonalIntelligenceEngine.AstrynContextResolver.resolveTargetProject(cleanUser, query)
      return buildString {
        appendLine("### ${project.projectName} — Verified Stage Status")
        appendLine("- **Active Objective:** ${project.objective}")
        if (project.completedStages.isNotEmpty()) {
          appendLine("- **Verified Completed Stages:**")
          project.completedStages.forEach { appendLine("  - $it") }
        }
        if (project.pendingWork.isNotEmpty()) {
          appendLine("- **Current / Pending Work:**")
          project.pendingWork.forEach { appendLine("  - $it") }
        }
        if (project.lockedDecisions.isNotEmpty()) {
          appendLine("- **Locked Architecture Rules:** ${project.lockedDecisions.joinToString("; ")}")
        }
      }.trim()
    }
  }

  // =========================================================================
  // 8. UNIFIED PHASE 17 COORDINATOR
  // =========================================================================

  object AstrynPhase17Coordinator {

    data class Phase17TurnAnalysis(
      val intentResult: IntentClassificationResult,
      val contextReport: AstrynContextualIntelligenceEngine.ContextualUnderstandingReport,
      val ambiguity: AmbiguityAssessment,
      val explanationProfile: ExplanationProfile,
      val followUp: FollowUpInterpretation,
      val feedbackOutcome: LearnedFeedbackOutcome,
      val confidenceEvaluation: ConfidenceEvaluation,
      val routingDecision: ToolRoutingDecision,
    )

    fun analyzeTurn(
      prompt: String,
      conversationHistory: List<ChatMessage> = emptyList(),
      attachments: List<ChatAttachment> = emptyList(),
      userUid: String? = null,
      conversationId: String = "",
      detectedLanguage: AstrynIntelligenceEngine.DetectedLanguage? = null,
      expertiseLevel: AstrynIntelligenceEngine.UserExpertiseLevel = AstrynIntelligenceEngine.UserExpertiseLevel.INTERMEDIATE,
      userPreferences: UserPreferences? = null,
    ): Phase17TurnAnalysis {
      val resolvedLanguage = detectedLanguage ?: detectLanguageQuick(prompt)
      val feedback = AstrynFeedbackLearningEngine.processPromptForLearning(userUid, prompt)

      val contextReport = AstrynContextualIntelligenceEngine.AstrynContextUnderstandingEngine.understandContext(
        prompt = prompt,
        conversationHistory = conversationHistory,
        attachments = attachments,
        userUid = userUid,
        conversationId = conversationId,
        temporaryConstraints = feedback.temporaryConstraints,
        persistentConstraints = feedback.conversationRules + feedback.persistentPreferencesApplied,
        userCorrections = feedback.corrections,
      )

      val intent = AstrynIntentUnderstandingEngine.classifyIntent(
        prompt = prompt,
        contextReport = contextReport,
        attachments = attachments,
      )

      val ambiguity = AstrynAmbiguityDetectionEngine.assessAmbiguity(
        prompt = prompt,
        intent = intent,
        contextReport = contextReport,
        language = resolvedLanguage,
        attachments = attachments,
      )

      val explanationProfile = AstrynAdaptiveExplanationEngine.determineExplanationProfile(
        prompt = prompt,
        intent = intent,
        detectedLanguage = resolvedLanguage,
        expertiseLevel = expertiseLevel,
        snapshot = contextReport.snapshot,
        userPreferences = userPreferences,
      )

      val followUp = AstrynFollowUpIntelligenceEngine.interpretFollowUp(
        prompt = prompt,
        intent = intent,
        contextReport = contextReport,
      )

      val confidence = AstrynResponseConfidenceEngine.evaluateConfidence(
        prompt = prompt,
        intent = intent,
        ambiguity = ambiguity,
        contextReport = contextReport,
        attachments = attachments,
      )

      val routing = AstrynContextualToolRouter.routeTurn(
        prompt = prompt,
        intent = intent,
        ambiguity = ambiguity,
        userUid = userUid,
        conversationId = conversationId,
        attachments = attachments,
      )

      return Phase17TurnAnalysis(
        intentResult = intent,
        contextReport = contextReport,
        ambiguity = ambiguity,
        explanationProfile = explanationProfile,
        followUp = followUp,
        feedbackOutcome = feedback,
        confidenceEvaluation = confidence,
        routingDecision = routing,
      )
    }

    /**
     * Produces a crisp, high-signal, token-budget-conscious system directive.
     * For trivial single-turn greetings or self-contained factual questions, emits minimal guidance.
     */
    fun buildConciseSystemDirective(analysis: Phase17TurnAnalysis): String {
      val needsExtraContextDirective =
        analysis.followUp.isFollowUp ||
          analysis.ambiguity.isAmbiguous ||
          analysis.contextReport.topicRecovered ||
          analysis.feedbackOutcome.corrections.isNotEmpty() ||
          analysis.feedbackOutcome.temporaryConstraints.isNotEmpty() ||
          analysis.confidenceEvaluation.tier == ConfidenceTier.HONEST_ABSTENTION

      if (!needsExtraContextDirective) {
        return ""
      }

      return buildString {
        append("- Phase17: Intent=${analysis.intentResult.primaryIntent.name}")
        analysis.contextReport.activeTopicLabel?.let { append(" | Topic='${it.take(36)}'") }
        if (analysis.contextReport.topicRecovered) {
          append(" | RecoveredEarlierTopic=true")
        } else if (analysis.contextReport.topicSwitched && !analysis.contextReport.previousTopicLabel.isNullOrBlank()) {
          append(" | SwitchedFrom='${analysis.contextReport.previousTopicLabel.take(32)}'")
        }
        appendLine()
        if (analysis.followUp.isFollowUp && analysis.followUp.directive.isNotBlank()) {
          appendLine("- ${analysis.followUp.directive.take(140)}")
        }
        if (analysis.feedbackOutcome.temporaryConstraints.isNotEmpty()) {
          appendLine("- Turn Constraint: ${analysis.feedbackOutcome.temporaryConstraints.joinToString("; ").take(100)}")
        }
        if (analysis.ambiguity.isAmbiguous && analysis.ambiguity.clarificationQuestions.isNotEmpty()) {
          appendLine("- Clarification Needed (1 brief question): ${analysis.ambiguity.clarificationQuestions.first().take(120)}")
        }
        if (analysis.confidenceEvaluation.tier == ConfidenceTier.HONEST_ABSTENTION) {
          append("- Honesty: ${analysis.confidenceEvaluation.behaviorDirective.take(120)}")
        }
      }.trim()
    }

    /**
     * Generates intelligent contextual replies for offline / fallback / clarification / topic-recovery /
     * short follow-up / project-stage turns when live Gemini network streaming isn't invoked.
     */
    fun generateContextualOfflineReply(
      prompt: String,
      analysis: Phase17TurnAnalysis,
      userUid: String?,
      conversationHistory: List<ChatMessage> = emptyList(),
    ): String? {
      val lower = prompt.lowercase(Locale.ROOT)

      // 1. Honest abstention on unverified prior session memory (MUST take precedence over generic pronoun ambiguity)
      if (analysis.confidenceEvaluation.tier == ConfidenceTier.HONEST_ABSTENTION) {
        return when (analysis.explanationProfile.language) {
          AstrynIntelligenceEngine.DetectedLanguage.HINDI_DEVANAGARI ->
            "मेरे पास अभी उस पिछले रिकॉर्ड या बातचीत का डेटा उपलब्ध नहीं है। कृपया वह जानकारी साझा करें ताकि मैं सही मदद कर सकूँ।"
          AstrynIntelligenceEngine.DetectedLanguage.HINGLISH ->
            "Mere paas us pichle conversation ya private record ka data available nahi hai. Kripya woh details share karein taaki main bina kuch invent kiye sahi madad kar sakun."
          AstrynIntelligenceEngine.DetectedLanguage.ENGLISH ->
            "I don't have access to that prior record in the current session. Please share the key details so I can help accurately without guessing."
        }
      }

      // 2. Ambiguity or sensitive action guard -> never guess or execute silently
      if (analysis.ambiguity.isAmbiguous && analysis.ambiguity.clarificationQuestions.isNotEmpty()) {
        return analysis.ambiguity.clarificationQuestions.first()
      }

      // 3. Emotional support adaptation
      if (analysis.intentResult.primaryIntent == AdaptiveIntent.EMOTIONAL_SUPPORT) {
        return when (analysis.explanationProfile.language) {
          AstrynIntelligenceEngine.DetectedLanguage.HINGLISH, AstrynIntelligenceEngine.DetectedLanguage.HINDI_DEVANAGARI ->
            "Main samajh sakta hun ki abhi pressure zyada feel ho raha hai. Ek saath sab solve karne ke bajaye hum sabse chhota ek step abhi lete hain — bataiye abhi sabse pehle kis cheez ko halka karna hai?"
          AstrynIntelligenceEngine.DetectedLanguage.ENGLISH ->
            "It sounds like a lot is weighing on you right now. You don't have to solve everything at once—want to pick the single smallest step to lighten the load first?"
        }
      }

      // 4. Project stage & continuation queries ("Kya Phase 16 complete ho gaya?", "Abhi kya pending hai?")
      if (lower.containsAny("phase 16", "phase 15", "phase 14", "kaunsa phase", "which phase", "what is pending", "kya pending hai") &&
        lower.containsAny("astryn", "project", "phase", "complete", "pending", "feature")
      ) {
        return AstrynContextualToolRouter.resolveProjectStatusSummary(userUid, prompt)
      }

      // 5. Short contextual follow-ups anchored to active conversation topic or resolved entity
      if (analysis.followUp.isFollowUp) {
        val refTarget = analysis.contextReport.referenceBundle.primaryTarget?.resolvedTarget
        val activeTopic = analysis.followUp.anchoredTopic ?: analysis.contextReport.activeTopicLabel
        val lastModel = conversationHistory.lastOrNull { it.role == MessageRole.MODEL }?.content?.trim()

        if (analysis.followUp.action == FollowUpAction.CORRECTION) {
          return buildString {
            appendLine("**Understood — updated immediately:** ${prompt.trim()}")
            if (!activeTopic.isNullOrBlank()) {
              appendLine("Continuing on **$activeTopic** using your corrected specification without keeping the earlier mistake.")
            }
          }.trim()
        }

        if (analysis.contextReport.topicRecovered && !activeTopic.isNullOrBlank()) {
          val recoveredSummary = analysis.contextReport.snapshot.activeTopic?.lastUserQuery
            ?: analysis.contextReport.snapshot.activeTopic?.lastAssistantReply
            ?: refTarget
          return buildString {
            appendLine("### Back to Topic: $activeTopic")
            if (!recoveredSummary.isNullOrBlank()) {
              appendLine("- **Resumed Context:** ${recoveredSummary.take(220)}")
            }
            if (analysis.contextReport.snapshot.activeConstraints.isNotEmpty()) {
              appendLine("- **Active Constraints:** ${analysis.contextReport.snapshot.activeConstraints.joinToString("; ")}")
            }
            append("Aap ismein aage kaunsa exact change ya next step chahte hain?")
          }.trim()
        }

        if (analysis.followUp.action == FollowUpAction.ADDITIONAL_EXAMPLE && !activeTopic.isNullOrBlank()) {
          return buildString {
            appendLine("### Practical Example ($activeTopic)")
            if (!refTarget.isNullOrBlank()) {
              appendLine("- **Context:** $refTarget")
            }
            appendLine("1. **Real-World Scenario:** Apply **$activeTopic** to a concrete everyday input-to-output flow so you can see the exact before/after difference.")
            appendLine("2. **Key Takeaway:** Watch how the core rule directly prevents errors or saves effort in practice.")
          }.trim()
        }

        if (analysis.followUp.action == FollowUpAction.SIMPLIFICATION && !activeTopic.isNullOrBlank()) {
          val baseLine = lastModel?.lines()?.firstOrNull { it.isNotBlank() }?.replace("#", "")?.trim()
            ?: refTarget
            ?: activeTopic
          return buildString {
            appendLine("### Simple Summary: $activeTopic")
            appendLine("**Aasan Bhasha Mein:** $baseLine — isko sirf ek real-life step mein samjhein: input do, core rule lagao, aur seedha clean result pao.")
          }.trim()
        }

        if (analysis.followUp.action == FollowUpAction.REFORMAT_TABLE_OR_LIST && !activeTopic.isNullOrBlank()) {
          return buildString {
            appendLine("### Quick Comparison Table: $activeTopic")
            appendLine("| Aspect | Key Point | Practical Impact |")
            appendLine("| :--- | :--- | :--- |")
            appendLine("| **Core Concept** | ${activeTopic.take(60)} | Clear structured behavior |")
            appendLine("| **Best Use Case** | ${(refTarget ?: "Everyday workflow").take(60)} | Faster, reliable execution |")
          }.trim()
        }

        if (analysis.followUp.action == FollowUpAction.CONTINUATION && !activeTopic.isNullOrBlank()) {
          return buildString {
            appendLine("### Continuing: $activeTopic")
            if (analysis.contextReport.snapshot.unfinishedTasks.isNotEmpty()) {
              appendLine("- **Next Pending Step:** ${analysis.contextReport.snapshot.unfinishedTasks.last()}")
            } else if (!refTarget.isNullOrBlank()) {
              appendLine("- **Next Step From:** $refTarget")
            }
            appendLine("Proceeding directly to the next stage without repeating the earlier overview.")
          }.trim()
        }
      }

      return null
    }

    fun onUserLogoutOrAccountSwitch(previousUserUid: String?) {
      AstrynContextualIntelligenceEngine.AstrynConversationStateManager.onUserLogoutOrAccountSwitch(previousUserUid)
    }

    private fun detectLanguageQuick(prompt: String): AstrynIntelligenceEngine.DetectedLanguage {
      if (prompt.any { it in '\u0900'..'\u097F' } || prompt.lowercase(Locale.ROOT).contains("hindi mein")) {
        return AstrynIntelligenceEngine.DetectedLanguage.HINDI_DEVANAGARI
      }
      val tokens = AstrynContextualIntelligenceEngine.tokenizeWords(prompt.lowercase(Locale.ROOT))
      val hinglishMarkers = setOf(
        "kya", "hai", "hain", "mein", "karo", "batao", "samjhao", "nahi", "nhi",
        "kal", "aaj", "humne", "maine", "mujhe", "iska", "uska", "isko", "usko",
        "wahi", "pehle", "dusra", "woh", "ye", "aur", "yaar", "bhai", "kaun", "kaunsa",
      )
      return if (tokens.count { it in hinglishMarkers } >= 1) {
        AstrynIntelligenceEngine.DetectedLanguage.HINGLISH
      } else {
        AstrynIntelligenceEngine.DetectedLanguage.ENGLISH
      }
    }
  }

  private fun String.containsAny(vararg needles: String): Boolean {
    return needles.any { this.contains(it, ignoreCase = true) }
  }
}
