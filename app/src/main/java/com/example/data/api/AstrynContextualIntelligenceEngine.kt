package com.example.data.api

import com.example.data.model.ChatAttachment
import com.example.data.model.ChatMessage
import com.example.data.model.MessageRole
import com.example.data.model.MessageStatus
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * ASTRYN AI — PHASE 17 (PART 1)
 * CONTEXTUAL CONVERSATION STATE, MULTI-TOPIC RECOVERY & REFERENCE RESOLUTION ENGINE
 *
 * Designed as an additive, modular extension over existing AstrynIntelligenceEngine &
 * AstrynPersonalIntelligenceEngine systems:
 * - Zero duplicate Room databases or external memory stores
 * - Strict user + conversation scope isolation (`userUid::conversationId`)
 * - Thread-safe bounded LRU conversation state tracking
 * - Confidence-scored bilingual/trilingual (Hindi / Hinglish / English) reference resolution
 * - Multi-topic stack & topic-recovery without losing earlier context
 */
object AstrynContextualIntelligenceEngine {

  private const val MAX_TRACKED_CONVERSATIONS = 64
  private const val MAX_TOPIC_STACK_DEPTH = 10
  private const val MAX_INDEXED_TURNS = 160
  private const val REFERENCE_CONFIDENCE_THRESHOLD = 0.58

  // =========================================================================
  // DATA MODELS
  // =========================================================================

  enum class ReferenceTargetType {
    PREVIOUS_MESSAGE,
    ENTITY,
    PROJECT,
    DOCUMENT,
    TASK,
    OPTION,
    TIME_OR_DATE,
    PREVIOUS_TOPIC,
    NONE,
  }

  data class ConversationTopicFrame(
    val topicId: String,
    val topicLabel: String,
    val keywords: Set<String>,
    val subtopics: List<String> = emptyList(),
    val entities: List<String> = emptyList(),
    val numberedOptions: List<String> = emptyList(),
    val lastUserQuery: String? = null,
    val lastAssistantReply: String? = null,
    val pendingQuestions: List<String> = emptyList(),
    val unfinishedTasks: List<String> = emptyList(),
    val decisions: List<String> = emptyList(),
    val documentRefs: List<String> = emptyList(),
    val constraints: List<String> = emptyList(),
    val recentCorrections: List<String> = emptyList(),
    val updatedAtMs: Long = System.currentTimeMillis(),
  )

  data class IndexedTurnRecord(
    val turnIndex: Int,
    val messageId: String,
    val role: MessageRole,
    val topicLabel: String,
    val snippet: String,
    val preservedDetail: String,
    val attachmentNames: List<String>,
    val timestamp: Long,
  )

  data class ConversationContextSnapshot(
    val userId: String,
    val conversationId: String,
    val activeTopic: ConversationTopicFrame? = null,
    val topicStack: List<ConversationTopicFrame> = emptyList(),
    val activeConstraints: List<String> = emptyList(),
    val temporaryTurnConstraints: List<String> = emptyList(),
    val recentCorrections: List<String> = emptyList(),
    val unfinishedTasks: List<String> = emptyList(),
    val unresolvedQuestions: List<String> = emptyList(),
    val recentDecisions: List<String> = emptyList(),
    val indexedTurns: List<IndexedTurnRecord> = emptyList(),
    val lastUpdatedAtMs: Long = System.currentTimeMillis(),
  )

  data class ResolvedReferenceItem(
    val expression: String,
    val targetType: ReferenceTargetType,
    val resolvedTarget: String,
    val sourceTopicLabel: String? = null,
    val confidence: Double,
    val rationale: String,
  )

  data class ReferenceResolutionBundle(
    val references: List<ResolvedReferenceItem> = emptyList(),
    val hasExplicitOrImplicitReference: Boolean = false,
    val primaryTarget: ResolvedReferenceItem? = null,
    val overallConfidence: Double = 1.0,
    val isConfidenceSufficient: Boolean = true,
    val needsClarification: Boolean = false,
    val clarificationHint: String? = null,
    val recoveredTopicFrame: ConversationTopicFrame? = null,
  )

  data class ContextualUnderstandingReport(
    val snapshot: ConversationContextSnapshot,
    val referenceBundle: ReferenceResolutionBundle,
    val recoveredHistorySnippets: List<String>,
    val topicSwitched: Boolean,
    val topicRecovered: Boolean,
    val previousTopicLabel: String?,
    val activeTopicLabel: String?,
    val contextCompletenessScore: Double,
  )

  // =========================================================================
  // THREAD-SAFE USER + CONVERSATION STATE MANAGER
  // =========================================================================

  object AstrynConversationStateManager {
    private val states = ConcurrentHashMap<String, ConversationContextSnapshot>()

    internal fun normalizeUser(userUid: String?): String {
      return userUid?.trim()?.takeIf { it.isNotEmpty() } ?: "anonymous_local_user"
    }

    internal fun normalizeConversation(conversationId: String?): String {
      return conversationId?.trim()?.takeIf { it.isNotEmpty() } ?: "default_conversation"
    }

    internal fun scopeKey(userUid: String?, conversationId: String?): String {
      return "${normalizeUser(userUid)}::${normalizeConversation(conversationId)}"
    }

    fun getSnapshot(userUid: String?, conversationId: String?): ConversationContextSnapshot? {
      val cleanUser = normalizeUser(userUid)
      val snap = states[scopeKey(cleanUser, conversationId)] ?: return null
      return if (AstrynSecurityGuard.verifyUserOwnership(cleanUser, snap.userId)) snap else null
    }

    fun updateFromHistoryAndTurn(
      userUid: String?,
      conversationId: String?,
      history: List<ChatMessage>,
      currentPrompt: String,
      currentAttachments: List<ChatAttachment> = emptyList(),
      temporaryConstraints: List<String> = emptyList(),
      persistentConstraints: List<String> = emptyList(),
      userCorrections: List<String> = emptyList(),
    ): Triple<ConversationContextSnapshot, Boolean, Boolean> {
      val cleanUser = normalizeUser(userUid)
      val cleanConv = normalizeConversation(conversationId)
      val key = scopeKey(cleanUser, cleanConv)
      val existing = states[key]?.takeIf {
        AstrynSecurityGuard.verifyUserOwnership(cleanUser, it.userId)
      }

      val validHistory = history.filter {
        (it.status == MessageStatus.SUCCESS || it.status == MessageStatus.INTERRUPTED) &&
          (it.content.isNotBlank() || it.attachments.isNotEmpty())
      }

      // 1. Index full conversation turns so omitted long-history turns remain recoverable
      val indexedTurns = buildIndexedTurns(validHistory)

      // 2. Build or update topic frames across the conversation
      var topicStack = (existing?.topicStack ?: emptyList()).toMutableList()
      var currentTopic = existing?.activeTopic

      // Seed topic stack from history if existing snapshot has fewer turns or is fresh
      if (validHistory.isNotEmpty()) {
        val recentUserMsgs = validHistory.filter { it.role == MessageRole.USER }
        val recentModelMsgs = validHistory.filter { it.role == MessageRole.MODEL }

        // Replay major topic transitions from chronological user turns if state is new/behind
        if (currentTopic == null || indexedTurns.size > (existing?.indexedTurns?.size ?: 0)) {
          val rebuilt = rebuildTopicProgression(validHistory)
          if (rebuilt.first != null) {
            currentTopic = rebuilt.first
            topicStack = rebuilt.second.toMutableList()
          }
        }

        // Ensure latest assistant reply and options are anchored cleanly
        val lastModel = recentModelMsgs.lastOrNull()
        val lastUser = recentUserMsgs.lastOrNull()
        if (currentTopic != null && (lastModel != null || lastUser != null)) {
          val extractedOptions =
            lastModel?.let { extractNumberedOrBulletedOptions(it.content) }.orEmpty()
          val lastOpenQuestions = extractOpenQuestions(lastModel?.content.orEmpty())
          val combinedEntities =
            (currentTopic.entities +
              extractEntityPhrases(lastUser?.content.orEmpty()) +
              extractEntityPhrases(lastModel?.content.orEmpty()))
              .distinct()
              .takeLast(10)
          currentTopic = currentTopic.copy(
            entities = combinedEntities,
            numberedOptions = if (extractedOptions.isNotEmpty()) extractedOptions else currentTopic.numberedOptions,
            lastUserQuery = lastUser?.content?.take(300) ?: currentTopic.lastUserQuery,
            lastAssistantReply = lastModel?.content?.take(500) ?: currentTopic.lastAssistantReply,
            pendingQuestions = lastOpenQuestions,
            documentRefs = (currentTopic.documentRefs +
              validHistory.flatMap { m -> m.attachments.map { it.fileName } }).distinct().takeLast(8),
            updatedAtMs = System.currentTimeMillis(),
          )
        }
      }

      // 3. Evaluate current prompt for topic recovery OR topic switch
      val cleanPrompt = currentPrompt.trim()
      val promptKeywords = extractSemanticKeywords(cleanPrompt)
      val isRecoveryCue = detectsTopicRecoveryCue(cleanPrompt)
      val isFollowUp = detectsFollowUpOrDeicticCue(cleanPrompt)
      var topicSwitched = false
      var topicRecovered = false

      if (isRecoveryCue && topicStack.isNotEmpty()) {
        val matchedPrevious = findBestMatchingTopic(promptKeywords, cleanPrompt, topicStack)
        if (matchedPrevious != null) {
          currentTopic?.let { active ->
            if (active.topicId != matchedPrevious.topicId) {
              topicStack.removeAll { it.topicId == active.topicId }
              topicStack.add(active)
            }
          }
          topicStack.removeAll { it.topicId == matchedPrevious.topicId }
          currentTopic = matchedPrevious.copy(
            keywords = (matchedPrevious.keywords + promptKeywords).take(16).toSet(),
            updatedAtMs = System.currentTimeMillis(),
          )
          topicRecovered = true
        }
      } else if (cleanPrompt.isNotBlank() && !isFollowUp) {
        val active = currentTopic
        if (active == null) {
          currentTopic = ConversationTopicFrame(
            topicId = "topic_${System.nanoTime()}",
            topicLabel = deriveTopicLabel(cleanPrompt, currentAttachments),
            keywords = promptKeywords,
            entities = extractEntityPhrases(cleanPrompt),
            documentRefs = currentAttachments.map { it.fileName },
            constraints = (persistentConstraints + temporaryConstraints).distinct(),
            recentCorrections = userCorrections,
          )
        } else {
          val overlap = jaccardOverlap(active.keywords, promptKeywords)
          val sameAttachment = currentAttachments.any { it.fileName in active.documentRefs }
          val mentionsEntity = active.entities.any { ent ->
            ent.length >= 3 && cleanPrompt.contains(ent, ignoreCase = true)
          }
          if (promptKeywords.size >= 2 && overlap < 0.08 && !sameAttachment && !mentionsEntity) {
            // Genuine topic switch: push old topic frame onto topicStack for later recovery
            topicStack.removeAll { it.topicId == active.topicId }
            topicStack.add(active)
            while (topicStack.size > MAX_TOPIC_STACK_DEPTH) {
              topicStack.removeAt(0)
            }
            currentTopic = ConversationTopicFrame(
              topicId = "topic_${System.nanoTime()}",
              topicLabel = deriveTopicLabel(cleanPrompt, currentAttachments),
              keywords = promptKeywords,
              entities = extractEntityPhrases(cleanPrompt),
              documentRefs = currentAttachments.map { it.fileName },
              constraints = (persistentConstraints + temporaryConstraints).distinct(),
              recentCorrections = userCorrections,
            )
            topicSwitched = true
          } else {
            // Continue enriching active topic
            currentTopic = active.copy(
              keywords = (active.keywords + promptKeywords).toList().takeLast(18).toSet(),
              entities = (active.entities + extractEntityPhrases(cleanPrompt)).distinct().takeLast(10),
              documentRefs = (active.documentRefs + currentAttachments.map { it.fileName }).distinct().takeLast(8),
              constraints = (active.constraints + persistentConstraints).distinct().takeLast(8),
              recentCorrections = (active.recentCorrections + userCorrections).distinct().takeLast(6),
              updatedAtMs = System.currentTimeMillis(),
            )
          }
        }
      }

      // Extract decisions and unfinished tasks from history + current turn
      val extractedDecisions = extractDecisions(validHistory, cleanPrompt)
      val extractedTasks = extractUnfinishedTasks(validHistory, cleanPrompt)
      val mergedConstraints =
        ((existing?.activeConstraints ?: emptyList()) + persistentConstraints)
          .distinct()
          .takeLast(10)
      val mergedCorrections =
        ((existing?.recentCorrections ?: emptyList()) + userCorrections)
          .distinct()
          .takeLast(8)

      val updatedSnapshot = ConversationContextSnapshot(
        userId = cleanUser,
        conversationId = cleanConv,
        activeTopic = currentTopic,
        topicStack = topicStack.toList(),
        activeConstraints = mergedConstraints,
        temporaryTurnConstraints = temporaryConstraints.distinct().takeLast(6),
        recentCorrections = mergedCorrections,
        unfinishedTasks = extractedTasks,
        unresolvedQuestions = currentTopic?.pendingQuestions ?: emptyList(),
        recentDecisions = extractedDecisions,
        indexedTurns = indexedTurns,
        lastUpdatedAtMs = System.currentTimeMillis(),
      )

      states[key] = updatedSnapshot
      evictOldestIfNeeded()
      return Triple(updatedSnapshot, topicSwitched, topicRecovered)
    }

    fun recordAssistantResponse(
      userUid: String?,
      conversationId: String?,
      userPrompt: String,
      assistantReply: String,
    ) {
      if (assistantReply.isBlank()) return
      val cleanUser = normalizeUser(userUid)
      val cleanConv = normalizeConversation(conversationId)
      val key = scopeKey(cleanUser, cleanConv)
      val existing = states[key]?.takeIf {
        AstrynSecurityGuard.verifyUserOwnership(cleanUser, it.userId)
      } ?: return

      val options = extractNumberedOrBulletedOptions(assistantReply)
      val entities = (existing.activeTopic?.entities.orEmpty() + extractEntityPhrases(assistantReply))
        .distinct()
        .takeLast(12)
      val openQ = extractOpenQuestions(assistantReply)
      val updatedTopic = existing.activeTopic?.copy(
        entities = entities,
        numberedOptions = if (options.isNotEmpty()) options else existing.activeTopic.numberedOptions,
        lastUserQuery = userPrompt.trim().take(300),
        lastAssistantReply = assistantReply.trim().take(500),
        pendingQuestions = openQ,
        updatedAtMs = System.currentTimeMillis(),
      )
      states[key] = existing.copy(
        activeTopic = updatedTopic,
        unresolvedQuestions = openQ,
        lastUpdatedAtMs = System.currentTimeMillis(),
      )
    }

    /**
     * Recovers relevant original messages from omitted or earlier turns in long conversations
     * so concrete facts, numbers, or user specifications are never silently lost.
     */
    fun recoverOriginalMessagesForQuery(
      snapshot: ConversationContextSnapshot,
      query: String,
      maxResults: Int = 3,
    ): List<IndexedTurnRecord> {
      if (snapshot.indexedTurns.isEmpty()) return emptyList()
      val queryTokens = extractSemanticKeywords(query)
      if (queryTokens.isEmpty()) {
        return snapshot.indexedTurns.take(2)
      }
      return snapshot.indexedTurns
        .map { rec ->
          val recTokens = extractSemanticKeywords(rec.preservedDetail)
          val overlap = queryTokens.count { t -> t in recTokens || rec.preservedDetail.contains(t, ignoreCase = true) }
          rec to overlap
        }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .take(maxResults)
        .map { it.first }
    }

    fun clearConversation(userUid: String?, conversationId: String?) {
      states.remove(scopeKey(userUid, conversationId))
    }

    fun onUserLogoutOrAccountSwitch(previousUserUid: String?) {
      val clean = previousUserUid?.trim()?.takeIf { it.isNotEmpty() } ?: return
      val prefix = "$clean::"
      states.keys.filter { it.startsWith(prefix) }.forEach { states.remove(it) }
    }

    fun clearAllForTesting() {
      states.clear()
    }

    private fun evictOldestIfNeeded() {
      while (states.size > MAX_TRACKED_CONVERSATIONS) {
        val oldest = states.entries.minByOrNull { it.value.lastUpdatedAtMs }?.key ?: break
        states.remove(oldest)
      }
    }

    private fun buildIndexedTurns(history: List<ChatMessage>): List<IndexedTurnRecord> {
      var runningTopic = "General Conversation"
      val bounded = history.takeLast(MAX_INDEXED_TURNS)
      return bounded.mapIndexed { idx, msg ->
        if (msg.role == MessageRole.USER && !detectsFollowUpOrDeicticCue(msg.content)) {
          val candidate = deriveTopicLabel(msg.content, msg.attachments)
          if (candidate.isNotBlank() && candidate != "Follow-up") {
            runningTopic = candidate
          }
        }
        val clean = msg.content.replace(Regex("\\s+"), " ").trim()
        IndexedTurnRecord(
          turnIndex = idx,
          messageId = msg.id,
          role = msg.role,
          topicLabel = runningTopic,
          snippet = clean.take(150),
          preservedDetail = clean.take(450),
          attachmentNames = msg.attachments.map { it.fileName },
          timestamp = msg.timestamp,
        )
      }
    }

    private fun rebuildTopicProgression(
      history: List<ChatMessage>,
    ): Pair<ConversationTopicFrame?, List<ConversationTopicFrame>> {
      var current: ConversationTopicFrame? = null
      val stack = mutableListOf<ConversationTopicFrame>()
      var lastUserText = ""
      var lastModelText = ""

      for (msg in history) {
        if (msg.role == MessageRole.USER) {
          lastUserText = msg.content.trim()
          val kws = extractSemanticKeywords(lastUserText)
          val isFollowUp = detectsFollowUpOrDeicticCue(lastUserText)
          if (current == null) {
            current = ConversationTopicFrame(
              topicId = "topic_${msg.id}",
              topicLabel = deriveTopicLabel(lastUserText, msg.attachments),
              keywords = kws,
              entities = extractEntityPhrases(lastUserText),
              lastUserQuery = lastUserText.take(300),
              documentRefs = msg.attachments.map { it.fileName },
              updatedAtMs = msg.timestamp,
            )
          } else if (!isFollowUp && kws.size >= 2 && jaccardOverlap(current.keywords, kws) < 0.08) {
            stack.removeAll { it.topicId == current!!.topicId }
            stack.add(current)
            if (stack.size > MAX_TOPIC_STACK_DEPTH) stack.removeAt(0)
            current = ConversationTopicFrame(
              topicId = "topic_${msg.id}",
              topicLabel = deriveTopicLabel(lastUserText, msg.attachments),
              keywords = kws,
              entities = extractEntityPhrases(lastUserText),
              lastUserQuery = lastUserText.take(300),
              documentRefs = msg.attachments.map { it.fileName },
              updatedAtMs = msg.timestamp,
            )
          } else {
            current = current.copy(
              keywords = (current.keywords + kws).toList().takeLast(18).toSet(),
              entities = (current.entities + extractEntityPhrases(lastUserText)).distinct().takeLast(10),
              lastUserQuery = lastUserText.take(300),
              documentRefs = (current.documentRefs + msg.attachments.map { it.fileName }).distinct(),
              updatedAtMs = msg.timestamp,
            )
          }
        } else if (msg.role == MessageRole.MODEL) {
          lastModelText = msg.content.trim()
          if (current != null) {
            val opts = extractNumberedOrBulletedOptions(lastModelText)
            current = current.copy(
              numberedOptions = if (opts.isNotEmpty()) opts else current.numberedOptions,
              lastAssistantReply = lastModelText.take(500),
              entities = (current.entities + extractEntityPhrases(lastModelText)).distinct().takeLast(10),
              pendingQuestions = extractOpenQuestions(lastModelText),
            )
          }
        }
      }
      return current to stack
    }
  }

  // =========================================================================
  // CONFIDENCE-SCORED REFERENCE RESOLUTION ENGINE
  // =========================================================================

  object AstrynReferenceResolutionEngine {

    private val PRONOUN_TOKENS = setOf(
      "ye", "yeh", "wo", "woh", "isko", "usko", "ismein", "usmein", "isme", "usme",
      "iska", "uska", "iske", "uske", "ispe", "uspe", "wahi", "wohi", "usi", "inhone",
      "unhone", "inmein", "unmein", "pichhla", "pichhle", "pichle", "pichli", "pehle",
      "it", "this", "that", "they", "them", "those", "these", "same", "previous",
      "earlier", "above", "there", "that_one",
      "यह", "वह", "ये", "वो", "इसमें", "उसमें", "इसका", "उसका", "इसके", "उसके",
      "इसको", "उसको", "वही", "उसी", "पिछला", "पिछले", "पिछली", "पहले",
    )

    fun resolveReferences(
      prompt: String,
      snapshot: ConversationContextSnapshot,
      history: List<ChatMessage>,
      attachments: List<ChatAttachment> = emptyList(),
      userUid: String? = null,
      conversationId: String = "",
    ): ReferenceResolutionBundle {
      val clean = prompt.trim()
      if (clean.isBlank()) {
        return ReferenceResolutionBundle()
      }
      val lower = clean.lowercase(Locale.ROOT)
      val tokens = tokenizeWords(lower)
      val foundRefs = mutableListOf<ResolvedReferenceItem>()
      var recoveredFrame: ConversationTopicFrame? = null

      val activeTopic = snapshot.activeTopic
      val lastUser = history.lastOrNull { it.role == MessageRole.USER }?.content?.trim()
      val lastModel = history.lastOrNull { it.role == MessageRole.MODEL }?.content?.trim()
      val options = activeTopic?.numberedOptions.orEmpty().ifEmpty {
        lastModel?.let { extractNumberedOrBulletedOptions(it) }.orEmpty()
      }

      // 1. Numbered / Ordinal Option References ("second option", "दूसरा वाला", "last option")
      val optionIndex = detectOptionIndex(lower)
      if (optionIndex != null) {
        val resolvedIdx = if (optionIndex == Int.MAX_VALUE) (options.size - 1) else optionIndex
        if (options.isNotEmpty() && resolvedIdx in options.indices) {
          foundRefs.add(
            ResolvedReferenceItem(
              expression = if (optionIndex == Int.MAX_VALUE) "last option" else "option ${resolvedIdx + 1}",
              targetType = ReferenceTargetType.OPTION,
              resolvedTarget = options[resolvedIdx],
              sourceTopicLabel = activeTopic?.topicLabel,
              confidence = 0.94,
              rationale = "Matched ordinal option #${resolvedIdx + 1} from prior assistant response",
            )
          )
        } else {
          foundRefs.add(
            ResolvedReferenceItem(
              expression = "option reference",
              targetType = ReferenceTargetType.OPTION,
              resolvedTarget = "",
              confidence = 0.25,
              rationale = "User referenced an option number without active numbered options in history",
            )
          )
        }
      }

      // 2. Multi-Topic Recovery Cue ("wapas pehle wale topic par", "back to earlier android issue")
      if (detectsTopicRecoveryCue(clean) && snapshot.topicStack.isNotEmpty()) {
        val kws = extractSemanticKeywords(clean)
        val matched = findBestMatchingTopic(kws, clean, snapshot.topicStack)
        if (matched != null) {
          recoveredFrame = matched
          foundRefs.add(
            ResolvedReferenceItem(
              expression = "previous topic",
              targetType = ReferenceTargetType.PREVIOUS_TOPIC,
              resolvedTarget = matched.lastUserQuery ?: matched.topicLabel,
              sourceTopicLabel = matched.topicLabel,
              confidence = 0.88,
              rationale = "Recovered earlier topic '${matched.topicLabel}' from conversation topic stack",
            )
          )
        }
      }

      // 3. Document / File References ("wahi pdf", "previous file", "is photo", "us document")
      val attachmentRefResolution = AstrynIntelligenceEngine.resolveActiveAttachmentsForTurn(
        prompt = clean,
        currentAttachments = attachments,
        conversationHistory = history,
        userUid = userUid,
        conversationId = conversationId,
      )
      if (attachmentRefResolution.resolvedAttachments.isNotEmpty()) {
        val names = attachmentRefResolution.resolvedAttachments.joinToString(", ") { it.fileName }
        foundRefs.add(
          ResolvedReferenceItem(
            expression = if (attachmentRefResolution.isFromEarlierTurn) "referenced document/image" else "attached file",
            targetType = ReferenceTargetType.DOCUMENT,
            resolvedTarget = names,
            sourceTopicLabel = activeTopic?.topicLabel,
            confidence = if (attachmentRefResolution.isFromEarlierTurn) 0.89 else 0.95,
            rationale = attachmentRefResolution.disambiguationReason,
          )
        )
      }

      // 4. Time & Relative Date References ("aaj", "kal", "today", "yesterday", "tomorrow")
      val hasTimeRef =
        tokens.any { it in setOf("aaj", "kal", "parso", "today", "tomorrow", "yesterday", "abhi", "आज", "कल") }
      if (hasTimeRef) {
        val timeSnap = AstrynPersonalIntelligenceEngine.AstrynTimeIntelligence.getCurrentSnapshot()
        val timeSummary = when {
          tokens.any { it in setOf("kal", "कल", "tomorrow") } ->
            "Tomorrow: ${timeSnap.tomorrowFormattedDate} (${timeSnap.tomorrowDayOfWeekEnglish})"
          tokens.any { it in setOf("yesterday") } ->
            "Reference relative to today ${timeSnap.formattedDate} (yesterday)"
          else ->
            "Today: ${timeSnap.formattedDate} (${timeSnap.dayOfWeekEnglish}), Local Time=${timeSnap.formattedTime12h}"
        }
        foundRefs.add(
          ResolvedReferenceItem(
            expression = "relative time/date",
            targetType = ReferenceTargetType.TIME_OR_DATE,
            resolvedTarget = timeSummary,
            sourceTopicLabel = activeTopic?.topicLabel,
            confidence = 0.85,
            rationale = "Anchored relative date/time against device clock",
          )
        )
      }

      // 5. Entity / Pronoun / Deictic References ("iska", "usmein", "this", "that", "it", "continue from there")
      val hasDeictic =
        tokens.any { it in PRONOUN_TOKENS } ||
          lower.contains("previous version") ||
          lower.contains("earlier explanation") ||
          lower.contains("continue from there") ||
          lower.contains("pichla jawab") ||
          lower.contains("pichli baar") ||
          lower.contains("example do") ||
          lower.contains("aur easy") ||
          lower.contains("table mein") ||
          lower.contains("practical use") ||
          lower.contains("shorter") ||
          lower.contains("continue")

      if (hasDeictic && foundRefs.none { it.targetType == ReferenceTargetType.OPTION || it.targetType == ReferenceTargetType.PREVIOUS_TOPIC }) {
        val isProjectRef =
          lower.contains("project") ||
            lower.contains("app") ||
            lower.contains("astryn") ||
            lower.contains("feature") ||
            lower.contains("design")
        val isTaskRef =
          lower.contains("task") ||
            lower.contains("step") ||
            lower.contains("kaam") ||
            lower.contains("continue")

        val primaryEntity = activeTopic?.entities?.lastOrNull()
        val anchorText = when {
          isTaskRef && snapshot.unfinishedTasks.isNotEmpty() ->
            snapshot.unfinishedTasks.last()
          activeTopic?.lastUserQuery != null && !lastModel.isNullOrBlank() ->
            "${activeTopic.topicLabel}: ${activeTopic.lastUserQuery.take(120)}"
          !lastUser.isNullOrBlank() ->
            lastUser.take(140)
          !lastModel.isNullOrBlank() ->
            lastModel.take(140)
          else -> ""
        }

        if (anchorText.isNotBlank()) {
          val resolvedTargetType = when {
            isProjectRef -> ReferenceTargetType.PROJECT
            isTaskRef && snapshot.unfinishedTasks.isNotEmpty() -> ReferenceTargetType.TASK
            primaryEntity != null && lower.containsAny("uska", "iska", "this", "that", "it", "यह", "वह") ->
              ReferenceTargetType.ENTITY
            else -> ReferenceTargetType.PREVIOUS_MESSAGE
          }
          val confidence = when {
            activeTopic != null && activeTopic.entities.isNotEmpty() -> 0.86
            !lastModel.isNullOrBlank() -> 0.82
            else -> 0.72
          }
          foundRefs.add(
            ResolvedReferenceItem(
              expression = tokens.firstOrNull { it in PRONOUN_TOKENS } ?: "contextual_followup",
              targetType = resolvedTargetType,
              resolvedTarget = if (primaryEntity != null && resolvedTargetType == ReferenceTargetType.ENTITY) {
                "$primaryEntity ($anchorText)"
              } else {
                anchorText
              },
              sourceTopicLabel = activeTopic?.topicLabel,
              confidence = confidence,
              rationale = "Resolved deictic reference to active conversation topic '${activeTopic?.topicLabel ?: "Recent Turn"}'",
            )
          )
        } else if (tokens.any { it in PRONOUN_TOKENS }) {
          // Pronoun with empty conversation history -> explicitly insufficient confidence!
          foundRefs.add(
            ResolvedReferenceItem(
              expression = tokens.first { it in PRONOUN_TOKENS },
              targetType = ReferenceTargetType.NONE,
              resolvedTarget = "",
              confidence = 0.20,
              rationale = "Pronoun reference used without preceding conversation context",
            )
          )
        }
      }

      val hasRefs = foundRefs.isNotEmpty()
      val primary = foundRefs.maxByOrNull { it.confidence }
      val overallConf = primary?.confidence ?: 1.0
      val sufficient = !hasRefs || overallConf >= REFERENCE_CONFIDENCE_THRESHOLD
      val needsClarification = hasRefs && !sufficient

      val clarificationHint = if (needsClarification) {
        when (primary?.targetType) {
          ReferenceTargetType.OPTION -> "Kripya batayein aap kaunse option ya topic ki baat kar rahe hain."
          else -> "Kripya thoda clear karein ki aap kis topic, file, ya item ki baat kar rahe hain."
        }
      } else {
        null
      }

      return ReferenceResolutionBundle(
        references = foundRefs,
        hasExplicitOrImplicitReference = hasRefs,
        primaryTarget = primary,
        overallConfidence = overallConf,
        isConfidenceSufficient = sufficient,
        needsClarification = needsClarification,
        clarificationHint = clarificationHint,
        recoveredTopicFrame = recoveredFrame,
      )
    }

    private fun detectOptionIndex(lower: String): Int? {
      return when {
        lower.containsAny("first option", "1st option", "option 1", "pehla option", "pehla wala", "पहला वाला", "पहला ऑप्शन") -> 0
        lower.containsAny("second option", "2nd option", "option 2", "dusra option", "doosra option", "2nd wala", "dusra wala", "दूसरा वाला", "दूसरा ऑप्शन") -> 1
        lower.containsAny("third option", "3rd option", "option 3", "teesra option", "teesra wala", "तीसरा वाला", "तीसरा ऑप्शन") -> 2
        lower.containsAny("fourth option", "4th option", "option 4", "chautha option", "chautha wala") -> 3
        lower.containsAny("last option", "aakhri option", "aakhri wala", "last wala", "आखिरी वाला") -> Int.MAX_VALUE
        else -> null
      }
    }
  }

  // =========================================================================
  // UNIFIED CONTEXT UNDERSTANDING ENGINE
  // =========================================================================

  object AstrynContextUnderstandingEngine {

    fun understandContext(
      prompt: String,
      conversationHistory: List<ChatMessage>,
      attachments: List<ChatAttachment> = emptyList(),
      userUid: String? = null,
      conversationId: String = "",
      temporaryConstraints: List<String> = emptyList(),
      persistentConstraints: List<String> = emptyList(),
      userCorrections: List<String> = emptyList(),
    ): ContextualUnderstandingReport {
      val (snapshot, switched, recovered) = AstrynConversationStateManager.updateFromHistoryAndTurn(
        userUid = userUid,
        conversationId = conversationId,
        history = conversationHistory,
        currentPrompt = prompt,
        currentAttachments = attachments,
        temporaryConstraints = temporaryConstraints,
        persistentConstraints = persistentConstraints,
        userCorrections = userCorrections,
      )

      val refBundle = AstrynReferenceResolutionEngine.resolveReferences(
        prompt = prompt,
        snapshot = snapshot,
        history = conversationHistory,
        attachments = attachments,
        userUid = userUid,
        conversationId = conversationId,
      )

      // If conversation is long (> 8 turns) or references an earlier topic, recover exact matching earlier turns
      val recoveredRecords =
        if (conversationHistory.size > 8 || recovered || refBundle.hasExplicitOrImplicitReference) {
          AstrynConversationStateManager.recoverOriginalMessagesForQuery(snapshot, prompt, maxResults = 3)
        } else {
          emptyList()
        }

      val recoveredSnippets = recoveredRecords.map { rec ->
        val role = if (rec.role == MessageRole.USER) "User" else "Astryn"
        "[${rec.topicLabel}] $role: ${rec.preservedDetail.take(180)}"
      }

      val completenessScore = when {
        refBundle.needsClarification -> 0.35
        snapshot.activeTopic != null && refBundle.isConfidenceSufficient -> 0.92
        conversationHistory.isNotEmpty() -> 0.85
        else -> 0.80
      }

      return ContextualUnderstandingReport(
        snapshot = snapshot,
        referenceBundle = refBundle,
        recoveredHistorySnippets = recoveredSnippets,
        topicSwitched = switched,
        topicRecovered = recovered,
        previousTopicLabel = snapshot.topicStack.lastOrNull()?.topicLabel,
        activeTopicLabel = snapshot.activeTopic?.topicLabel,
        contextCompletenessScore = completenessScore,
      )
    }
  }

  // =========================================================================
  // INTERNAL EXTRACTION & TOPIC MATCHING HELPERS
  // =========================================================================

  private val STOP_WORDS = setOf(
    "what", "where", "when", "which", "who", "how", "why", "this", "that", "these", "those",
    "with", "from", "have", "has", "had", "will", "would", "could", "should", "about", "into",
    "there", "their", "they", "them", "then", "than", "also", "just", "only", "please", "help",
    "explain", "tell", "give", "make", "show", "kya", "kyu", "kyun", "kaise", "kahan", "kab",
    "hai", "hain", "tha", "thi", "the", "aur", "bhi", "mein", "main", "mujhe", "mera", "meri",
    "mere", "isko", "usko", "ismein", "usmein", "iska", "uska", "wahi", "wala", "wali", "wale",
    "karo", "karna", "batao", "samjhao", "do", "de", "ek", "ko", "ka", "ki", "ke", "se", "par",
    "pe", "toh", "agar", "lekin", "nahi", "haan", "yes", "no", "not", "and", "the", "for",
    "example", "shorter", "continue", "next", "step", "option", "second", "first", "third",
    "क्या", "कैसे", "कहाँ", "कब", "है", "हैं", "और", "में", "का", "की", "के", "को", "से", "पर", "बताओ", "समझाओ",
  )

  internal fun tokenizeWords(lowerText: String): Set<String> {
    return lowerText
      .split(Regex("""[^a-z0-9\u0900-\u097F_]+"""))
      .filter { it.isNotBlank() }
      .toSet()
  }

  internal fun extractSemanticKeywords(text: String): Set<String> {
    return text.lowercase(Locale.ROOT)
      .split(Regex("""[^a-z0-9\u0900-\u097F]+"""))
      .filter { it.length >= 3 && it !in STOP_WORDS }
      .toSet()
  }

  internal fun extractEntityPhrases(text: String): List<String> {
    val clean = text.trim()
    if (clean.isBlank()) return emptyList()
    val results = mutableListOf<String>()
    // Capitalized proper/technical nouns or CamelCase identifiers
    Regex("""\b([A-Z][a-zA-Z0-9_]{2,28}(?:\s+[A-Z][a-zA-Z0-9_]{2,20})?)\b""")
      .findAll(clean)
      .forEach { m ->
        val candidate = m.groupValues[1].trim()
        if (candidate.lowercase(Locale.ROOT) !in setOf("the", "what", "how", "why", "please", "can", "explain", "yes", "user", "astryn")) {
          results.add(candidate)
        }
      }
    // Backtick code symbols
    Regex("""`([^`]{2,40})`""").findAll(clean).forEach { m ->
      results.add(m.groupValues[1].trim())
    }
    return results.distinct().take(8)
  }

  internal fun extractNumberedOrBulletedOptions(text: String): List<String> {
    val lines = text.lines()
    val numbered = mutableListOf<String>()
    val numRegex = Regex("""^\s*(?:\*{0,2})?(?:Option\s*)?(\d+)[.)\]:\-]\s*(.+)$""", RegexOption.IGNORE_CASE)
    for (line in lines) {
      val m = numRegex.find(line)
      if (m != null) {
        val body = m.groupValues[2].replace("**", "").trim()
        if (body.length >= 4) numbered.add(body)
      }
    }
    if (numbered.isNotEmpty()) return numbered
    val bulleted = mutableListOf<String>()
    for (line in lines) {
      val t = line.trim()
      if (t.startsWith("- ") || t.startsWith("* ") || t.startsWith("• ")) {
        val body = t.drop(2).replace("**", "").trim()
        if (body.length >= 6) bulleted.add(body)
      }
    }
    return bulleted
  }

  private fun extractOpenQuestions(text: String): List<String> {
    return text.lines()
      .map { it.trim() }
      .filter { it.endsWith("?") && it.length in 10..180 }
      .takeLast(3)
  }

  private fun extractDecisions(history: List<ChatMessage>, currentPrompt: String): List<String> {
    val combined = history.map { it.content } + currentPrompt
    return combined
      .filter { text ->
        val l = text.lowercase(Locale.ROOT)
        l.containsAny("lock", "final", "decided", "same rakhna", "preserve", "do not change", "change mat karna", "use kotlin", "use room", "must keep")
      }
      .map { it.replace(Regex("\\s+"), " ").trim().take(130) }
      .distinct()
      .takeLast(6)
  }

  private fun extractUnfinishedTasks(history: List<ChatMessage>, currentPrompt: String): List<String> {
    val combined = history.filter { it.role == MessageRole.USER }.map { it.content } + currentPrompt
    return combined
      .filter { text ->
        val l = text.lowercase(Locale.ROOT)
        l.containsAny("pending", "baaki", "next step", "todo", "later", "abhi karna", "continue", "remaining")
      }
      .map { it.replace(Regex("\\s+"), " ").trim().take(120) }
      .distinct()
      .takeLast(5)
  }

  internal fun detectsTopicRecoveryCue(text: String): Boolean {
    val l = text.lowercase(Locale.ROOT)
    return l.containsAny(
      "pehle wale topic",
      "pehle wala topic",
      "wapas",
      "back to",
      "earlier topic",
      "previous topic",
      "pehle jo baat",
      "pehle jo hum",
      "earlier conversation",
      "earlier android",
      "earlier code",
      "wapas aate hain",
      "return to",
      "पहले वाले टॉपिक",
      "वापस",
    )
  }

  internal fun detectsFollowUpOrDeicticCue(text: String): Boolean {
    val l = text.lowercase(Locale.ROOT).trim()
    val tokens = tokenizeWords(l)
    if (tokens.size <= 7 && tokens.any {
        it in setOf(
          "ye", "yeh", "wo", "woh", "isko", "usko", "ismein", "usmein", "iska", "uska",
          "example", "shorter", "continue", "more", "why", "how", "kyu", "kyun", "next",
          "table", "aur", "easy", "simple", "detail", "details", "repeat",
        )
      }
    ) {
      return true
    }
    return l.containsAny(
      "example do", "aur easy", "easy language", "table mein", "practical use",
      "second option", "first option", "third option", "dusra option", "pehla option",
      "next step", "aage batao", "continue", "shorter", "brief", "step by step",
      "iska matlab", "usmein", "ismein", "same problem", "koi aur tarika",
    )
  }

  private fun deriveTopicLabel(text: String, attachments: List<ChatAttachment>): String {
    if (attachments.isNotEmpty() && text.length < 25) {
      return "Document/Image: ${attachments.first().fileName}"
    }
    val kws = extractSemanticKeywords(text)
    if (kws.isEmpty()) return if (detectsFollowUpOrDeicticCue(text)) "Follow-up" else text.trim().take(40).ifBlank { "General Chat" }
    return kws.take(4).joinToString(" ") { w ->
      w.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase(Locale.ROOT) else ch.toString() }
    }
  }

  private fun findBestMatchingTopic(
    queryKeywords: Set<String>,
    rawQuery: String,
    stack: List<ConversationTopicFrame>,
  ): ConversationTopicFrame? {
    if (stack.isEmpty()) return null
    if (queryKeywords.isEmpty()) return stack.lastOrNull()
    val scored = stack.map { frame ->
      val kwMatches = queryKeywords.count { q ->
        q in frame.keywords ||
          frame.topicLabel.contains(q, ignoreCase = true) ||
          frame.entities.any { it.contains(q, ignoreCase = true) } ||
          (frame.lastUserQuery?.contains(q, ignoreCase = true) == true)
      }
      frame to kwMatches
    }
    val best = scored.maxByOrNull { it.second }
    return if (best != null && best.second > 0) {
      best.first
    } else if (rawQuery.lowercase(Locale.ROOT).containsAny("pehle", "earlier", "previous", "wapas", "back")) {
      stack.lastOrNull()
    } else {
      null
    }
  }

  private fun jaccardOverlap(a: Set<String>, b: Set<String>): Double {
    if (a.isEmpty() || b.isEmpty()) return 0.0
    val inter = a.intersect(b).size.toDouble()
    val union = a.union(b).size.toDouble()
    return inter / union
  }

  private fun String.containsAny(vararg needles: String): Boolean {
    return needles.any { this.contains(it, ignoreCase = true) }
  }
}
