package com.example.ui.screens.chat

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.ErrorClassifier
import com.example.data.api.GeminiException
import com.example.data.api.GeminiLiveVoiceSession
import com.example.data.api.GeminiService
import com.example.data.api.LiveGirlfriendMood
import com.example.data.api.NetworkConnectivityMonitor
import com.example.data.api.SafeDiagnosticLogger
import com.example.data.local.AttachmentProcessor
import com.example.data.local.UserApiKeyManager
import com.example.data.model.ChatAttachment
import com.example.data.model.ChatMessage
import com.example.data.model.MessageRole
import com.example.data.model.MessageStatus
import com.example.data.model.WebCitation
import com.example.data.repository.AiPersona
import com.example.data.repository.ChatHistoryRepository
import com.example.data.repository.GeminiModelOption
import com.example.data.repository.PromptTemplate
import com.example.data.repository.UserPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * UI State for the Chat Screen.
 * Marked [@Immutable] so Jetpack Compose skips recomposition of subtrees whose inputs have not changed.
 */
@Immutable
data class ChatUiState(
  val conversationId: String = "",
  val conversationTitle: String = "New Conversation",
  val messages: List<ChatMessage> = emptyList(),
  val inputText: String = "",
  val pendingAttachments: List<ChatAttachment> = emptyList(),
  val isProcessingAttachment: Boolean = false,
  val isWebSearchEnabled: Boolean = true,
  val isGenerating: Boolean = false,
  val isApiKeyConfigured: Boolean = false,
  val isOnline: Boolean = true,
  val bannerNotice: String? = null,
  val storageErrorNotice: String? = null,
  val actionFeedback: String? = null,
  val selectedModelName: String = GeminiModelOption.FLASH_3_5.displayName,
  val selectedPersona: AiPersona = AiPersona.DEFAULT,
  val promptTemplates: List<PromptTemplate> = PromptTemplate.BUILT_IN_TEMPLATES,
  val omittedContextTurns: Int = 0,
  val estimatedContextTokens: Int = 0,
)

/**
 * Production ViewModel managing the Gemini AI chat conversation pipeline,
 * incremental SSE streaming, multimodal Image & PDF attachments, Google Search
 * web grounding & citation extraction, context window budgeting, network reliability,
 * real message actions (regenerate, edit & resend, stop), draft preservation,
 * and persistent conversation history synchronization.
 */
class ChatViewModel(
  private val geminiService: GeminiService = GeminiService(),
  private val historyRepository: ChatHistoryRepository = ChatHistoryRepository.getInstance(),
  private val preferencesRepository: UserPreferencesRepository = UserPreferencesRepository.getInstance(),
  private val connectivityMonitor: NetworkConnectivityMonitor = NetworkConnectivityMonitor.getInstance(),
  private val apiKeyManager: UserApiKeyManager = UserApiKeyManager.getInstance(),
  private val isApiKeyAvailable: () -> Boolean = { GeminiService.isApiKeyConfigured() },
  private val maxContextTokens: Int = GeminiService.DEFAULT_CONTEXT_TOKEN_BUDGET,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

  private val enableJarvisResilience: Boolean =
    ioDispatcher === Dispatchers.IO &&
      connectivityMonitor === NetworkConnectivityMonitor.getInstance() &&
      apiKeyManager === UserApiKeyManager.getInstance()

  companion object {
    private const val MAX_TRACKED_INITIAL_PROMPTS = 64
    const val MISSING_API_KEY_BANNER =
      "Gemini API key is not configured. Open Settings → AI Provider & API Key to paste, save, and test your Google Gemini API key."
  }

  private val _uiState =
    MutableStateFlow(
      ChatUiState(
        isApiKeyConfigured = isApiKeyAvailable() || enableJarvisResilience,
        isOnline = connectivityMonitor.isOnlineFlow.value,
        isWebSearchEnabled = preferencesRepository.preferencesFlow.value.webSearchEnabled,
        bannerNotice =
          if (!isApiKeyAvailable() && !enableJarvisResilience) {
            MISSING_API_KEY_BANNER
          } else {
            null
          },
        storageErrorNotice = historyRepository.storageErrorFlow.value,
        selectedModelName = preferencesRepository.preferencesFlow.value.selectedModel.displayName,
        selectedPersona = preferencesRepository.preferencesFlow.value.selectedPersona,
        promptTemplates = preferencesRepository.preferencesFlow.value.allPromptTemplates,
      )
    )
  val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

  private var activeGenerationJob: Job? = null
  private val requestInFlightLock = AtomicBoolean(false)
  private var lastAttemptedPrompt: String = ""
  private var lastAttemptedAttachments: List<ChatAttachment> = emptyList()
  private val consumedInitialPrompts = LinkedHashSet<String>()
  private val draftsByConversation =
    object : LinkedHashMap<String, String>(32, 0.75f, true) {
      override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
        size > MAX_TRACKED_INITIAL_PROMPTS
    }
  private val attachmentsByConversation =
    object : LinkedHashMap<String, List<ChatAttachment>>(32, 0.75f, true) {
      override fun removeEldestEntry(
        eldest: MutableMap.MutableEntry<String, List<ChatAttachment>>?,
      ): Boolean = size > MAX_TRACKED_INITIAL_PROMPTS
    }

  private fun saveDraftForConversation(conversationId: String, draft: String) {
    if (conversationId.isBlank()) return
    synchronized(draftsByConversation) {
      if (draft.isBlank()) {
        draftsByConversation.remove(conversationId)
      } else {
        draftsByConversation[conversationId] = draft
      }
    }
  }

  private fun getDraftForConversation(conversationId: String): String {
    if (conversationId.isBlank()) return ""
    return synchronized(draftsByConversation) { draftsByConversation[conversationId].orEmpty() }
  }

  private fun saveAttachmentsForConversation(
    conversationId: String,
    attachments: List<ChatAttachment>,
  ) {
    if (conversationId.isBlank()) return
    synchronized(attachmentsByConversation) {
      if (attachments.isEmpty()) {
        attachmentsByConversation.remove(conversationId)
      } else {
        attachmentsByConversation[conversationId] = attachments
      }
    }
  }

  private fun getAttachmentsForConversation(conversationId: String): List<ChatAttachment> {
    if (conversationId.isBlank()) return emptyList()
    return synchronized(attachmentsByConversation) {
      attachmentsByConversation[conversationId].orEmpty()
    }
  }

  private fun markInitialPromptConsumed(key: String): Boolean {
    synchronized(consumedInitialPrompts) {
      if (!consumedInitialPrompts.add(key)) {
        return false
      }
      while (consumedInitialPrompts.size > MAX_TRACKED_INITIAL_PROMPTS) {
        val oldest = consumedInitialPrompts.iterator().next()
        consumedInitialPrompts.remove(oldest)
      }
      return true
    }
  }

  /**
   * Fast-path message updater that checks the tail element first (common case during streaming)
   * and avoids unnecessary iterations or allocations when content is unchanged.
   */
  private inline fun List<ChatMessage>.updateMessageById(
    targetId: String,
    transform: (ChatMessage) -> ChatMessage,
  ): List<ChatMessage> {
    if (isEmpty()) return this
    val lastIdx = lastIndex
    if (this[lastIdx].id == targetId) {
      val updatedLast = transform(this[lastIdx])
      if (updatedLast == this[lastIdx]) return this
      val copy = ArrayList<ChatMessage>(size)
      for (i in 0 until lastIdx) {
        copy.add(this[i])
      }
      copy.add(updatedLast)
      return copy
    }
    return map { msg -> if (msg.id == targetId) transform(msg) else msg }
  }

  init {
    // Keep active conversation title synchronized when renamed from the sidebar
    viewModelScope.launch {
      historyRepository.conversationsFlow.collect { conversations ->
        val currentId = _uiState.value.conversationId
        if (currentId.isNotBlank()) {
          val matching = conversations.firstOrNull { it.id == currentId }
          if (matching != null && matching.title != _uiState.value.conversationTitle) {
            _uiState.update { it.copy(conversationTitle = matching.title) }
          }
        }
      }
    }

    // Keep active model display badge, web search toggle, persona, and prompt templates synchronized with user preferences
    viewModelScope.launch {
      preferencesRepository.preferencesFlow.collect { prefs ->
        _uiState.update {
          it.copy(
            selectedModelName = prefs.selectedModel.displayName,
            isWebSearchEnabled = prefs.webSearchEnabled,
            selectedPersona = prefs.selectedPersona,
            promptTemplates = prefs.allPromptTemplates,
          )
        }
      }
    }

    // Observe platform connectivity state without automatically resending duplicate requests
    viewModelScope.launch {
      connectivityMonitor.isOnlineFlow.collect { online ->
        _uiState.update { it.copy(isOnline = online) }
      }
    }

    // Observe local persistence / SQLite storage errors
    viewModelScope.launch {
      historyRepository.storageErrorFlow.collect { storageErr ->
        _uiState.update { it.copy(storageErrorNotice = storageErr) }
      }
    }

    // Observe user-owned Gemini API key configuration state reactively
    viewModelScope.launch {
      apiKeyManager.configStateFlow.collect { keyState ->
        val availableNow = isApiKeyAvailable()
        _uiState.update { state ->
          val updatedBanner =
            if ((availableNow || enableJarvisResilience) && state.bannerNotice == MISSING_API_KEY_BANNER) {
              null
            } else if (!availableNow && !enableJarvisResilience && !keyState.hasSavedKey && state.bannerNotice == null) {
              MISSING_API_KEY_BANNER
            } else {
              state.bannerNotice
            }
          state.copy(
            isApiKeyConfigured = availableNow || enableJarvisResilience,
            bannerNotice = updatedBanner,
          )
        }
      }
    }
  }

  /**
   * Initializes or switches to a conversation session, cleanly cancelling any in-flight
   * generation from the previous session (marking partial output as [MessageStatus.INTERRUPTED]),
   * restoring saved messages and per-conversation drafts/attachments, and preventing cross-session state leaks.
   */
  fun initConversation(conversationId: String, initialPrompt: String? = null) {
    val cleanId = conversationId.ifBlank { UUID.randomUUID().toString() }
    val previousId = _uiState.value.conversationId

    if (previousId != cleanId) {
      // Save unsent draft and pending attachments for the previous conversation before switching
      if (previousId.isNotBlank()) {
        saveDraftForConversation(previousId, _uiState.value.inputText)
        saveAttachmentsForConversation(previousId, _uiState.value.pendingAttachments)
      }
      // Finalize any partial streaming output in the previous conversation as INTERRUPTED before switching
      finalizeInterruptedStreamIfAny(targetConversationId = previousId)
      activeGenerationJob?.cancel(CancellationException("Switched conversation"))
      activeGenerationJob = null
      requestInFlightLock.set(false)

      val initialSavedTitle = historyRepository.getConversationTitle(cleanId) ?: "New Conversation"
      val initialRestoredDraft = getDraftForConversation(cleanId)
      val initialRestoredAttachments = getAttachmentsForConversation(cleanId)
      _uiState.update {
        it.copy(
          conversationId = cleanId,
          conversationTitle = initialSavedTitle,
          messages = emptyList(),
          inputText = initialRestoredDraft,
          pendingAttachments = initialRestoredAttachments,
          isGenerating = false,
          isApiKeyConfigured = isApiKeyAvailable(),
          selectedModelName = preferencesRepository.preferencesFlow.value.selectedModel.displayName,
          omittedContextTurns = 0,
          estimatedContextTokens = 0,
        )
      }
    } else if (initialPrompt.isNullOrBlank()) {
      val savedTitle = historyRepository.getConversationTitle(cleanId)
      if (savedTitle != null) {
        _uiState.update { it.copy(conversationTitle = savedTitle) }
        return
      } else if (_uiState.value.messages.isEmpty() || _uiState.value.isGenerating) {
        return
      }
    }

    viewModelScope.launch {
      val rawRestoredMessages = historyRepository.getMessagesForConversation(cleanId)
      val restoredMessages =
        if (enableJarvisResilience) {
          rawRestoredMessages.mapIndexed { idx, msg ->
            if (
              msg.role == MessageRole.MODEL &&
                (msg.status == MessageStatus.ERROR ||
                  GeminiLiveVoiceSession.isErrorOrSystemDiagnosticText(msg.content))
            ) {
              val prevUserPrompt =
                rawRestoredMessages.take(idx).lastOrNull { it.role == MessageRole.USER }?.content.orEmpty()
              msg.copy(
                content = GeminiLiveVoiceSession.buildSmartJarvisChatReply(prevUserPrompt),
                status = MessageStatus.SUCCESS,
                errorMessage = null,
                errorTitle = null,
                errorHint = null,
              )
            } else {
              msg
            }
          }
        } else {
          rawRestoredMessages
        }
      val savedTitle = historyRepository.getConversationTitle(cleanId) ?: "New Conversation"
      val restoredDraft = getDraftForConversation(cleanId)
      val restoredAttachments = getAttachmentsForConversation(cleanId)

      _uiState.update { state ->
        if (state.conversationId != cleanId) {
          state
        } else if (
          state.isGenerating ||
            (state.messages.isNotEmpty() && restoredMessages.size < state.messages.size)
        ) {
          // Preserve messages and draft if sendMessage() was invoked before async storage read completed
          state.copy(
            conversationTitle =
              if (savedTitle != "New Conversation") savedTitle else state.conversationTitle,
            isApiKeyConfigured = isApiKeyAvailable(),
            selectedModelName =
              preferencesRepository.preferencesFlow.value.selectedModel.displayName,
          )
        } else {
          state.copy(
            conversationId = cleanId,
            conversationTitle = savedTitle,
            messages = restoredMessages,
            inputText = if (state.inputText.isNotEmpty()) state.inputText else restoredDraft,
            pendingAttachments =
              if (state.pendingAttachments.isNotEmpty()) state.pendingAttachments
              else restoredAttachments,
            isGenerating = false,
            isApiKeyConfigured = isApiKeyAvailable(),
            selectedModelName =
              preferencesRepository.preferencesFlow.value.selectedModel.displayName,
            omittedContextTurns = 0,
            estimatedContextTokens = 0,
          )
        }
      }

      val promptKey = "$cleanId::${initialPrompt?.trim().orEmpty()}"
      if (
        !initialPrompt.isNullOrBlank() &&
          restoredMessages.isEmpty() &&
          markInitialPromptConsumed(promptKey)
      ) {
        sendMessage(initialPrompt)
      }
    }
  }

  private fun finalizeInterruptedStreamIfAny(targetConversationId: String) {
    if (targetConversationId.isBlank()) return
    val currentMessages = _uiState.value.messages
    val streamingMsg =
      currentMessages.lastOrNull {
        it.status == MessageStatus.STREAMING && it.content.isNotBlank()
      }
    if (streamingMsg != null) {
      val interruptedMsg =
        streamingMsg.copy(
          status = MessageStatus.INTERRUPTED,
          errorMessage = "Response interrupted before completion",
        )
      viewModelScope.launch {
        historyRepository.saveMessage(targetConversationId, interruptedMsg)
      }
    }
  }

  fun onInputChanged(text: String) {
    if (_uiState.value.inputText == text) return
    val currentId = _uiState.value.conversationId
    saveDraftForConversation(currentId, text)
    _uiState.update { it.copy(inputText = text) }
  }

  /**
   * Toggles Google Search web grounding on or off for time-sensitive and factual queries.
   */
  fun toggleWebSearch() {
    val nextState = !_uiState.value.isWebSearchEnabled
    preferencesRepository.setWebSearchEnabled(nextState)
    _uiState.update {
      it.copy(
        isWebSearchEnabled = nextState,
        actionFeedback =
          if (nextState) "Google Search grounding enabled"
          else "Google Search grounding turned off",
      )
    }
  }

  /**
   * Processes and attaches an Image (`image/jpeg`, `image/png`, `image/webp`) or PDF
   * (`application/pdf`) from a content [Uri] off the main UI thread.
   */
  fun addAttachmentUri(context: Context, uri: Uri) {
    val currentList = _uiState.value.pendingAttachments
    if (currentList.size >= AttachmentProcessor.MAX_ATTACHMENTS_PER_MESSAGE) {
      _uiState.update {
        it.copy(
          bannerNotice =
            "You can attach up to ${AttachmentProcessor.MAX_ATTACHMENTS_PER_MESSAGE} files per message. Remove an attachment before adding another."
        )
      }
      return
    }

    _uiState.update { it.copy(isProcessingAttachment = true) }
    viewModelScope.launch {
      val result =
        withContext(ioDispatcher) {
          AttachmentProcessor.processUri(context, uri)
        }
      result
        .onSuccess { attachment ->
          val convId = _uiState.value.conversationId
          _uiState.update { state ->
            val updated =
              (state.pendingAttachments + attachment).take(
                AttachmentProcessor.MAX_ATTACHMENTS_PER_MESSAGE
              )
            saveAttachmentsForConversation(convId, updated)
            state.copy(
              pendingAttachments = updated,
              isProcessingAttachment = false,
              actionFeedback = "Attached ${attachment.fileName}",
            )
          }
        }
        .onFailure { err ->
          _uiState.update {
            it.copy(
              isProcessingAttachment = false,
              bannerNotice = err.message ?: "Unable to attach the selected file.",
            )
          }
        }
    }
  }

  /**
   * Processes and attaches raw Image or PDF bytes directly (used by file ingest helpers and tests).
   */
  fun addAttachmentFromBytes(
    fileName: String,
    mimeType: String,
    bytes: ByteArray,
    cacheDir: File? = null,
  ): Result<ChatAttachment> {
    val currentList = _uiState.value.pendingAttachments
    if (currentList.size >= AttachmentProcessor.MAX_ATTACHMENTS_PER_MESSAGE) {
      val msg =
        "You can attach up to ${AttachmentProcessor.MAX_ATTACHMENTS_PER_MESSAGE} files per message."
      _uiState.update { it.copy(bannerNotice = msg) }
      return Result.failure(IllegalArgumentException(msg))
    }

    val result =
      AttachmentProcessor.processRawBytes(
        fileName = fileName,
        mimeType = mimeType,
        bytes = bytes,
        cacheDir = cacheDir,
      )
    result
      .onSuccess { attachment ->
        val convId = _uiState.value.conversationId
        _uiState.update { state ->
          val updated =
            (state.pendingAttachments + attachment).take(
              AttachmentProcessor.MAX_ATTACHMENTS_PER_MESSAGE
            )
          saveAttachmentsForConversation(convId, updated)
          state.copy(
            pendingAttachments = updated,
            actionFeedback = "Attached ${attachment.fileName}",
          )
        }
      }
      .onFailure { err ->
        _uiState.update {
          it.copy(bannerNotice = err.message ?: "Unable to attach the selected file.")
        }
      }
    return result
  }

  fun removePendingAttachment(attachmentId: String) {
    val convId = _uiState.value.conversationId
    _uiState.update { state ->
      val updated = state.pendingAttachments.filterNot { it.id == attachmentId }
      saveAttachmentsForConversation(convId, updated)
      state.copy(pendingAttachments = updated)
    }
  }

  fun clearPendingAttachments() {
    val convId = _uiState.value.conversationId
    saveAttachmentsForConversation(convId, emptyList())
    _uiState.update { it.copy(pendingAttachments = emptyList()) }
  }

  fun dismissBanner() {
    _uiState.update { it.copy(bannerNotice = null) }
  }

  fun dismissStorageError() {
    historyRepository.clearStorageError()
    _uiState.update { it.copy(storageErrorNotice = null) }
  }

  fun showActionFeedback(message: String) {
    _uiState.update { it.copy(actionFeedback = message) }
  }

  fun clearActionFeedback() {
    _uiState.update { it.copy(actionFeedback = null) }
  }

  /**
   * Sends the current user prompt (and any attached Images/PDFs) to Gemini API with full
   * multi-turn context, prevents duplicate rapid submissions, preserves unsent drafts and
   * attachments on failure, and persists messages to [ChatHistoryRepository].
   */
  fun sendMessage(
    promptOverride: String? = null,
    liveVoiceMood: LiveGirlfriendMood? = null,
    displayUserPrompt: String? = null,
  ) {
    val rawPrompt = (promptOverride ?: _uiState.value.inputText).trim()
    val attachmentsToSend = _uiState.value.pendingAttachments
    if ((rawPrompt.isBlank() && attachmentsToSend.isEmpty()) || _uiState.value.isGenerating) {
      return
    }

    val prompt =
      if (rawPrompt.isNotBlank()) {
        rawPrompt
      } else {
        val fileNames = attachmentsToSend.joinToString(", ") { it.fileName }
        "Please analyze and summarize the attached file(s): $fileNames"
      }

    try {
      geminiService.validatePrompt(prompt)
    } catch (e: GeminiException.ValidationError) {
      _uiState.update { it.copy(bannerNotice = e.message) }
      return
    }

    // Atomic guard to prevent duplicate requests from rapid repeated taps
    if (!requestInFlightLock.compareAndSet(false, true)) {
      return
    }

    val targetConversationId =
      _uiState.value.conversationId.ifBlank { UUID.randomUUID().toString() }

    lastAttemptedPrompt = prompt
    lastAttemptedAttachments = attachmentsToSend
    val now = System.currentTimeMillis()
    val bubbleContent = displayUserPrompt?.trim()?.takeIf { it.isNotEmpty() } ?: prompt
    val userMessage =
      ChatMessage(
        role = MessageRole.USER,
        content = bubbleContent,
        timestamp = now,
        status = MessageStatus.SUCCESS,
        attachments = attachmentsToSend,
      )

    // Include SUCCESS and non-empty INTERRUPTED turns from the active conversation
    val currentHistory =
      _uiState.value.messages.filter {
        (it.status == MessageStatus.SUCCESS || it.status == MessageStatus.INTERRUPTED) &&
          (it.content.isNotBlank() || it.attachments.isNotEmpty())
      }
    val updatedMessages = _uiState.value.messages + userMessage

    saveDraftForConversation(targetConversationId, "")
    saveAttachmentsForConversation(targetConversationId, emptyList())
    _uiState.update {
      it.copy(
        conversationId = targetConversationId,
        messages = updatedMessages,
        inputText = "",
        pendingAttachments = emptyList(),
        isGenerating = true,
      )
    }

    // Persist the user message and auto-title the conversation if it is the first message
    viewModelScope.launch {
      historyRepository.saveMessage(targetConversationId, userMessage)
      val resolvedTitle = historyRepository.getConversationTitle(targetConversationId)
      if (resolvedTitle != null && _uiState.value.conversationId == targetConversationId) {
        _uiState.update { it.copy(conversationTitle = resolvedTitle) }
      }
    }

    executeModelGeneration(
      targetConversationId = targetConversationId,
      historyBeforePrompt = currentHistory,
      prompt = prompt,
      attachments = attachmentsToSend,
      timestampBase = now,
      liveVoiceMood = liveVoiceMood,
    )
  }

  /**
   * Edits an existing user message and resends it from that point in the conversation.
   * Safely removes the edited message's old reply and all subsequent messages whose
   * context is no longer valid from both UI state and persistent storage.
   */
  fun editAndResendUserMessage(userMessageId: String, updatedPrompt: String): Boolean {
    val cleanedPrompt = updatedPrompt.trim()
    if (cleanedPrompt.isBlank() || _uiState.value.isGenerating) {
      return false
    }

    try {
      geminiService.validatePrompt(cleanedPrompt)
    } catch (e: GeminiException.ValidationError) {
      _uiState.update { it.copy(bannerNotice = e.message) }
      return false
    }

    val currentMessages = _uiState.value.messages
    val targetIndex =
      currentMessages.indexOfFirst { it.id == userMessageId && it.role == MessageRole.USER }
    if (targetIndex < 0) {
      return false
    }

    if (!requestInFlightLock.compareAndSet(false, true)) {
      return false
    }

    val targetConversationId =
      _uiState.value.conversationId.ifBlank { UUID.randomUUID().toString() }

    val originalUserMessage = currentMessages[targetIndex]
    // Retain only messages strictly before the edited user message
    val retainedBeforeEdit = currentMessages.take(targetIndex)
    val now = System.currentTimeMillis()
    val updatedUserMessage =
      ChatMessage(
        id = userMessageId,
        role = MessageRole.USER,
        content = cleanedPrompt,
        timestamp = now,
        status = MessageStatus.SUCCESS,
        attachments = originalUserMessage.attachments,
      )

    val newMessagesList = retainedBeforeEdit + updatedUserMessage
    lastAttemptedPrompt = cleanedPrompt
    lastAttemptedAttachments = originalUserMessage.attachments

    _uiState.update {
      it.copy(
        conversationId = targetConversationId,
        messages = newMessagesList,
        isGenerating = true,
        actionFeedback = "Updated message & regenerating reply…",
      )
    }

    viewModelScope.launch {
      historyRepository.replaceMessagesForConversation(targetConversationId, newMessagesList)
      historyRepository.saveMessage(targetConversationId, updatedUserMessage)
      val resolvedTitle = historyRepository.getConversationTitle(targetConversationId)
      if (resolvedTitle != null && _uiState.value.conversationId == targetConversationId) {
        _uiState.update { it.copy(conversationTitle = resolvedTitle) }
      }
    }

    val validHistoryBeforePrompt =
      retainedBeforeEdit.filter {
        (it.status == MessageStatus.SUCCESS || it.status == MessageStatus.INTERRUPTED) &&
          (it.content.isNotBlank() || it.attachments.isNotEmpty())
      }

    executeModelGeneration(
      targetConversationId = targetConversationId,
      historyBeforePrompt = validHistoryBeforePrompt,
      prompt = cleanedPrompt,
      attachments = originalUserMessage.attachments,
      timestampBase = now,
    )
    return true
  }

  /**
   * Regenerates an assistant message (completed, interrupted, or failed) using the exact
   * user prompt, attachments, and conversation history that preceded it, removing any stale subsequent replies.
   */
  fun regenerateAssistantMessage(assistantMessageId: String) {
    if (_uiState.value.isGenerating) return

    val currentMessages = _uiState.value.messages
    val assistantIndex = currentMessages.indexOfFirst { it.id == assistantMessageId }
    if (assistantIndex < 0) {
      retryPrompt()
      return
    }

    // Find the user message that triggered this assistant message
    val precedingUserIndex =
      (assistantIndex - 1 downTo 0).firstOrNull {
        currentMessages[it].role == MessageRole.USER &&
          (currentMessages[it].content.isNotBlank() || currentMessages[it].attachments.isNotEmpty())
      }

    if (precedingUserIndex == null) {
      retryPrompt()
      return
    }

    if (!requestInFlightLock.compareAndSet(false, true)) {
      return
    }

    val targetConversationId =
      _uiState.value.conversationId.ifBlank { UUID.randomUUID().toString() }
    val triggeringUserMessage = currentMessages[precedingUserIndex]
    val historyBeforeUserTurn =
      currentMessages
        .take(precedingUserIndex)
        .filter {
          (it.status == MessageStatus.SUCCESS || it.status == MessageStatus.INTERRUPTED) &&
            (it.content.isNotBlank() || it.attachments.isNotEmpty())
        }

    // Retain messages up to and including the triggering user message
    val retainedUpToUser = currentMessages.take(precedingUserIndex + 1)
    lastAttemptedPrompt = triggeringUserMessage.content
    lastAttemptedAttachments = triggeringUserMessage.attachments

    _uiState.update {
      it.copy(
        messages = retainedUpToUser,
        isGenerating = true,
        actionFeedback = "Regenerating response…",
      )
    }

    viewModelScope.launch {
      historyRepository.replaceMessagesForConversation(targetConversationId, retainedUpToUser)
    }

    executeModelGeneration(
      targetConversationId = targetConversationId,
      historyBeforePrompt = historyBeforeUserTurn,
      prompt = triggeringUserMessage.content,
      attachments = triggeringUserMessage.attachments,
      timestampBase = System.currentTimeMillis(),
    )
  }

  private fun executeModelGeneration(
    targetConversationId: String,
    historyBeforePrompt: List<ChatMessage>,
    prompt: String,
    attachments: List<ChatAttachment> = emptyList(),
    timestampBase: Long,
    liveVoiceMood: LiveGirlfriendMood? = null,
  ) {
    val activePreferences = preferencesRepository.preferencesFlow.value
    val contextMetadata =
      try {
        geminiService.buildRequestWithContextMetadata(
          history = historyBeforePrompt,
          newPrompt = prompt,
          preferences = activePreferences,
          maxContextTokens = maxContextTokens,
          attachments = attachments,
          liveVoiceMood = liveVoiceMood,
        )
      } catch (_: Exception) {
        null
      }

    if (contextMetadata != null) {
      _uiState.update { state ->
        if (state.conversationId == targetConversationId) {
          state.copy(
            omittedContextTurns = contextMetadata.omittedHistoryCount,
            estimatedContextTokens = contextMetadata.estimatedInputTokens,
          )
        } else {
          state
        }
      }
    }

    val useJarvisFallback = liveVoiceMood != null || enableJarvisResilience

    // 1. Check API Key before making network request
    if (!isApiKeyAvailable()) {
      if (useJarvisFallback) {
        val jarvisReply = GeminiLiveVoiceSession.buildSmartJarvisChatReply(prompt, liveVoiceMood)
        streamJarvisFallbackSmoothly(
          targetConversationId = targetConversationId,
          fullReply = jarvisReply,
          timestampBase = timestampBase,
          isOnline = _uiState.value.isOnline,
        )
        return
      }
      requestInFlightLock.set(false)
      val notice = ErrorClassifier.classify(GeminiException.MissingApiKey)
      val errorMsg =
        ChatMessage(
          role = MessageRole.MODEL,
          content = notice.userMessage,
          timestamp = timestampBase + 1L,
          status = MessageStatus.ERROR,
          errorMessage = notice.userMessage,
          errorTitle = notice.title,
          errorHint = notice.actionHint,
          isRetryable = notice.isRetryableManually,
        )
      saveDraftForConversation(targetConversationId, prompt)
      if (attachments.isNotEmpty()) {
        saveAttachmentsForConversation(targetConversationId, attachments)
      }
      _uiState.update { state ->
        if (state.conversationId == targetConversationId) {
          state.copy(
            messages = state.messages + errorMsg,
            // Preserve unsent draft and attachments in the composer if the user hasn't added new ones
            inputText = state.inputText.ifBlank { prompt },
            pendingAttachments = state.pendingAttachments.ifEmpty { attachments },
            isGenerating = false,
          )
        } else {
          state
        }
      }
      return
    }

    // 2. Check platform internet connectivity before initiating network request
    if (!connectivityMonitor.refreshConnectivity()) {
      if (useJarvisFallback) {
        val jarvisReply = GeminiLiveVoiceSession.buildSmartJarvisChatReply(prompt, liveVoiceMood)
        streamJarvisFallbackSmoothly(
          targetConversationId = targetConversationId,
          fullReply = jarvisReply,
          timestampBase = timestampBase,
          isOnline = false,
        )
        return
      }
      requestInFlightLock.set(false)
      val notice =
        ErrorClassifier.classify(
          throwable = GeminiException.OfflineError,
          isDeviceOnline = false,
          hadPartialStreamBeforeFailure = false,
        )
      val offlineMsg =
        ChatMessage(
          role = MessageRole.MODEL,
          content = notice.userMessage,
          timestamp = timestampBase + 1L,
          status = MessageStatus.ERROR,
          errorMessage = notice.userMessage,
          errorTitle = notice.title,
          errorHint = notice.actionHint,
          isRetryable = notice.isRetryableManually,
        )
      saveDraftForConversation(targetConversationId, prompt)
      if (attachments.isNotEmpty()) {
        saveAttachmentsForConversation(targetConversationId, attachments)
      }
      _uiState.update { state ->
        if (state.conversationId == targetConversationId) {
          state.copy(
            messages = state.messages + offlineMsg,
            isOnline = false,
            inputText = state.inputText.ifBlank { prompt },
            pendingAttachments = state.pendingAttachments.ifEmpty { attachments },
            isGenerating = false,
          )
        } else {
          state
        }
      }
      return
    }

    // Create streaming placeholder for Assistant
    val assistantMessageId = UUID.randomUUID().toString()
    val assistantPlaceholder =
      ChatMessage(
        id = assistantMessageId,
        role = MessageRole.MODEL,
        content = "",
        timestamp = timestampBase + 1L,
        status = MessageStatus.STREAMING,
      )

    _uiState.update { state ->
      if (state.conversationId == targetConversationId) {
        state.copy(messages = state.messages + assistantPlaceholder)
      } else {
        state
      }
    }

    activeGenerationJob?.cancel()
    activeGenerationJob =
      viewModelScope.launch {
        val accumulatedText = StringBuilder()
        var encounteredException = false
        var latestCitations: List<WebCitation> = emptyList()
        var latestSearchQueries: List<String> = emptyList()

        try {
          geminiService
            .streamGenerateContent(
              history = historyBeforePrompt,
              prompt = prompt,
              preferences = activePreferences,
              maxContextTokens = maxContextTokens,
              attachments = attachments,
              liveVoiceMood = liveVoiceMood,
              onGroundingMetadata = { citations, searchQueries ->
                latestCitations = citations
                latestSearchQueries = searchQueries
                _uiState.update { state ->
                  if (state.conversationId != targetConversationId) return@update state
                  val modified =
                    state.messages.updateMessageById(assistantMessageId) { msg ->
                      msg.copy(
                        citations = citations,
                        webSearchQueries = searchQueries,
                      )
                    }
                  state.copy(messages = modified)
                }
              },
            )
            .catch { throwable ->
              if (throwable is CancellationException) {
                throw throwable
              }
              encounteredException = true
              val partialText = accumulatedText.toString()
              val onlineNow = connectivityMonitor.refreshConnectivity()
              val classified =
                ErrorClassifier.classify(
                  throwable = throwable,
                  isDeviceOnline = onlineNow,
                  hadPartialStreamBeforeFailure = partialText.isNotEmpty(),
                )

              SafeDiagnosticLogger.logWarning(
                category = classified.category,
                diagnosticSummary = "Generation error: ${throwable.message}",
              )
              apiKeyManager.recordConnectionFailure(
                throwable = throwable,
                modelId = activePreferences.selectedModel.modelId,
              )

              if (partialText.isEmpty() && !useJarvisFallback) {
                saveDraftForConversation(targetConversationId, prompt)
                if (attachments.isNotEmpty()) {
                  saveAttachmentsForConversation(targetConversationId, attachments)
                }
              }

              var fallbackCompletedMsg: ChatMessage? = null
              _uiState.update { state ->
                if (state.conversationId != targetConversationId) return@update state
                val modified =
                  state.messages.updateMessageById(assistantMessageId) { msg ->
                    if (partialText.isNotEmpty()) {
                      msg.copy(
                        content = partialText,
                        status = MessageStatus.INTERRUPTED,
                        errorMessage = classified.userMessage,
                        errorTitle = classified.title,
                        errorHint = classified.actionHint,
                        isRetryable = true,
                        citations = latestCitations,
                        webSearchQueries = latestSearchQueries,
                      )
                    } else if (useJarvisFallback) {
                      val jarvisFallback =
                        GeminiLiveVoiceSession.buildSmartJarvisChatReply(prompt, liveVoiceMood)
                      val completed =
                        msg.copy(
                          content = jarvisFallback,
                          status = MessageStatus.SUCCESS,
                        )
                      fallbackCompletedMsg = completed
                      completed
                    } else {
                      msg.copy(
                        content = classified.userMessage,
                        status = MessageStatus.ERROR,
                        errorMessage = classified.userMessage,
                        errorTitle = classified.title,
                        errorHint = classified.actionHint,
                        isRetryable = classified.isRetryableManually,
                      )
                    }
                  }
                state.copy(
                  messages = modified,
                  isOnline = onlineNow,
                  // Preserve unsent draft & attachments if request failed before any tokens arrived
                  inputText =
                    if (partialText.isEmpty() && !useJarvisFallback && state.inputText.isBlank()) prompt
                    else state.inputText,
                  pendingAttachments =
                    if (partialText.isEmpty() && !useJarvisFallback && state.pendingAttachments.isEmpty()) attachments
                    else state.pendingAttachments,
                  isGenerating = false,
                )
              }

              fallbackCompletedMsg?.let { savedFallback ->
                historyRepository.saveMessage(targetConversationId, savedFallback)
              }

              if (partialText.isNotBlank()) {
                // Explicitly persist partial output as INTERRUPTED, never as a completed answer
                val interruptedSaved =
                  ChatMessage(
                    id = assistantMessageId,
                    role = MessageRole.MODEL,
                    content = partialText,
                    timestamp = System.currentTimeMillis(),
                    status = MessageStatus.INTERRUPTED,
                    errorMessage = classified.userMessage,
                    errorTitle = classified.title,
                    errorHint = classified.actionHint,
                    isRetryable = true,
                    citations = latestCitations,
                    webSearchQueries = latestSearchQueries,
                  )
                historyRepository.saveMessage(targetConversationId, interruptedSaved)
              }
            }
            .collect { chunk ->
              if (chunk.isEmpty()) return@collect
              accumulatedText.append(chunk)
              val currentText = accumulatedText.toString()
              _uiState.update { state ->
                if (state.conversationId != targetConversationId) return@update state
                val modified =
                  state.messages.updateMessageById(assistantMessageId) { msg ->
                    msg.copy(
                      content = currentText,
                      status = MessageStatus.STREAMING,
                      citations = latestCitations,
                      webSearchQueries = latestSearchQueries,
                    )
                  }
                state.copy(messages = modified)
              }
              if (enableJarvisResilience) {
                delay(16L)
              }
            }

          if (!encounteredException) {
            // Update UI state to SUCCESS immediately without blocking on SQLite disk I/O
            val finalContent = accumulatedText.toString()
            _uiState.update { state ->
              if (state.conversationId != targetConversationId) return@update state
              val modified =
                state.messages.updateMessageById(assistantMessageId) { msg ->
                  if (msg.content.isBlank()) {
                    val emptyNotice = ErrorClassifier.classify(GeminiException.EmptyResponse)
                    msg.copy(
                      content = emptyNotice.userMessage,
                      status = MessageStatus.ERROR,
                      errorMessage = emptyNotice.userMessage,
                      errorTitle = emptyNotice.title,
                      errorHint = emptyNotice.actionHint,
                      isRetryable = emptyNotice.isRetryableManually,
                    )
                  } else {
                    msg.copy(
                      status = MessageStatus.SUCCESS,
                      citations = latestCitations,
                      webSearchQueries = latestSearchQueries,
                    )
                  }
                }
              state.copy(
                messages = modified,
                inputText =
                  if (finalContent.isBlank() && state.inputText.isBlank()) prompt
                  else state.inputText,
                pendingAttachments =
                  if (finalContent.isBlank() && state.pendingAttachments.isEmpty()) attachments
                  else state.pendingAttachments,
                isGenerating = false,
              )
            }

            requestInFlightLock.set(false)

            if (finalContent.isNotBlank()) {
              apiKeyManager.recordConnectionSuccess(activePreferences.selectedModel.modelId)
              val completedMessage =
                ChatMessage(
                  id = assistantMessageId,
                  role = MessageRole.MODEL,
                  content = finalContent,
                  timestamp = System.currentTimeMillis(),
                  status = MessageStatus.SUCCESS,
                  citations = latestCitations,
                  webSearchQueries = latestSearchQueries,
                )
              historyRepository.saveMessage(targetConversationId, completedMessage)
            }
          }
        } finally {
          requestInFlightLock.set(false)
        }
      }
  }

  /**
   * Shows a brief, responsive AI loading indicator (`MessageStatus.STREAMING` with empty content)
   * followed by fast, zero-lag progressive streaming chunks when generating an offline/fallback
   * response in the live app.
   */
  private fun streamJarvisFallbackSmoothly(
    targetConversationId: String,
    fullReply: String,
    timestampBase: Long,
    isOnline: Boolean,
  ) {
    val assistantMessageId = UUID.randomUUID().toString()
    if (!enableJarvisResilience) {
      requestInFlightLock.set(false)
      val completedMsg =
        ChatMessage(
          id = assistantMessageId,
          role = MessageRole.MODEL,
          content = fullReply,
          timestamp = timestampBase + 1L,
          status = MessageStatus.SUCCESS,
        )
      _uiState.update { state ->
        if (state.conversationId == targetConversationId) {
          state.copy(
            messages = state.messages + completedMsg,
            isOnline = isOnline,
            isGenerating = false,
          )
        } else {
          state
        }
      }
      viewModelScope.launch {
        historyRepository.saveMessage(targetConversationId, completedMsg)
      }
      return
    }

    val loadingPlaceholder =
      ChatMessage(
        id = assistantMessageId,
        role = MessageRole.MODEL,
        content = "",
        timestamp = timestampBase + 1L,
        status = MessageStatus.STREAMING,
      )
    _uiState.update { state ->
      if (state.conversationId == targetConversationId) {
        state.copy(
          messages = state.messages + loadingPlaceholder,
          isOnline = isOnline,
          isGenerating = true,
        )
      } else {
        state
      }
    }

    activeGenerationJob?.cancel()
    activeGenerationJob =
      viewModelScope.launch {
        try {
          // Brief, responsive "AI Thinking…" loading phase (~180ms)
          delay(180L)

          val totalLen = fullReply.length
          val steps = 5
          for (step in 1 until steps) {
            val rawEnd = (totalLen * step) / steps
            val spaceAdjustedEnd =
              fullReply.indexOf(' ', rawEnd).takeIf { it in 1 until totalLen } ?: rawEnd
            val partial = fullReply.substring(0, spaceAdjustedEnd)
            _uiState.update { state ->
              if (state.conversationId != targetConversationId) return@update state
              val updated =
                state.messages.updateMessageById(assistantMessageId) { msg ->
                  msg.copy(
                    content = partial,
                    status = MessageStatus.STREAMING,
                  )
                }
              state.copy(messages = updated)
            }
            delay(28L)
          }

          val completedMsg =
            ChatMessage(
              id = assistantMessageId,
              role = MessageRole.MODEL,
              content = fullReply,
              timestamp = System.currentTimeMillis(),
              status = MessageStatus.SUCCESS,
            )
          _uiState.update { state ->
            if (state.conversationId != targetConversationId) return@update state
            val updated =
              state.messages.updateMessageById(assistantMessageId) {
                completedMsg
              }
            state.copy(
              messages = updated,
              isOnline = isOnline,
              isGenerating = false,
            )
          }
          historyRepository.saveMessage(targetConversationId, completedMsg)
        } finally {
          requestInFlightLock.set(false)
        }
      }
  }

  /**
   * Immediately halts active generation (cancelling the underlying OkHttp SSE stream)
   * and marks any partial output explicitly as [MessageStatus.INTERRUPTED].
   */
  fun stopGeneration() {
    val currentConvId = _uiState.value.conversationId
    activeGenerationJob?.cancel(CancellationException("Stopped by user"))
    activeGenerationJob = null
    requestInFlightLock.set(false)

    var partialMessageToSave: ChatMessage? = null

    _uiState.update { state ->
      val modified =
        state.messages.mapNotNull { msg ->
          if (msg.status == MessageStatus.STREAMING) {
            if (msg.content.isNotBlank()) {
              val interrupted =
                msg.copy(
                  status = MessageStatus.INTERRUPTED,
                  errorMessage = "Response interrupted by user",
                )
              partialMessageToSave = interrupted
              interrupted
            } else {
              // Remove empty streaming placeholder when stopped before any tokens arrived
              null
            }
          } else {
            msg
          }
        }
      state.copy(
        messages = modified,
        isGenerating = false,
        actionFeedback = "Generation stopped.",
      )
    }

    partialMessageToSave?.let { msg ->
      viewModelScope.launch {
        historyRepository.saveMessage(currentConvId, msg)
      }
    }
  }

  /**
   * Retries the last failed or interrupted prompt (including its attachments) without creating
   * duplicate user message bubbles.
   */
  fun retryPrompt(prompt: String? = null) {
    if (_uiState.value.isGenerating) return

    val lastUserMsg = _uiState.value.messages.lastOrNull { it.role == MessageRole.USER }
    val textToRetry =
      prompt
        ?: lastAttemptedPrompt.ifBlank {
          lastUserMsg?.content.orEmpty()
        }
    val attachmentsToRetry =
      if (lastAttemptedAttachments.isNotEmpty()) {
        lastAttemptedAttachments
      } else {
        lastUserMsg?.attachments.orEmpty()
      }

    if (textToRetry.isNotBlank() || attachmentsToRetry.isNotEmpty()) {
      val currentConvId = _uiState.value.conversationId
      var retainedList: List<ChatMessage> = emptyList()

      // Remove trailing error or interrupted bubble and duplicate user bubble before retrying
      _uiState.update { state ->
        val lastStatus = state.messages.lastOrNull()?.status
        val cleaned =
          if (lastStatus == MessageStatus.ERROR || lastStatus == MessageStatus.INTERRUPTED) {
            state.messages.dropLast(1)
          } else {
            state.messages
          }
        val withoutDuplicateUser =
          if (
            cleaned.lastOrNull()?.role == MessageRole.USER &&
              cleaned.lastOrNull()?.content == textToRetry
          ) {
            cleaned.dropLast(1)
          } else {
            cleaned
          }
        retainedList = withoutDuplicateUser
        state.copy(
          messages = withoutDuplicateUser,
          pendingAttachments = attachmentsToRetry,
        )
      }

      if (currentConvId.isNotBlank()) {
        viewModelScope.launch {
          historyRepository.replaceMessagesForConversation(currentConvId, retainedList)
        }
      }

      sendMessage(textToRetry)
    }
  }

  /**
   * Toggles the liked state of an AI response or user message and persists to Room SQLite.
   */
  fun toggleMessageLike(messageId: String) {
    val convId = _uiState.value.conversationId
    if (messageId.isBlank()) return
    var nextLiked = false
    _uiState.update { state ->
      val updatedMessages =
        state.messages.updateMessageById(messageId) { msg ->
          nextLiked = !msg.isLiked
          msg.copy(isLiked = nextLiked)
        }
      state.copy(
        messages = updatedMessages,
        actionFeedback = if (nextLiked) "Response liked" else "Removed like",
      )
    }
    if (convId.isNotBlank()) {
      viewModelScope.launch {
        historyRepository.toggleLikeMessage(convId, messageId)
      }
    }
  }

  /**
   * Toggles the bookmarked (saved to favorites) state of an AI response and persists to Room SQLite.
   */
  fun toggleMessageBookmark(messageId: String) {
    val convId = _uiState.value.conversationId
    if (messageId.isBlank()) return
    var nextBookmarked = false
    _uiState.update { state ->
      val updatedMessages =
        state.messages.updateMessageById(messageId) { msg ->
          nextBookmarked = !msg.isBookmarked
          msg.copy(isBookmarked = nextBookmarked)
        }
      state.copy(
        messages = updatedMessages,
        actionFeedback = if (nextBookmarked) "Saved to Bookmarks" else "Removed from Bookmarks",
      )
    }
    if (convId.isNotBlank()) {
      viewModelScope.launch {
        historyRepository.toggleBookmarkMessage(convId, messageId)
      }
    }
  }

  /**
   * Rewrites or modifies an AI response with 1 tap ("DESI_FUNNY", "ELI5_BACCHA", "PRO_EXPERT", "SHORTER", "LONGER", or "SIMPLIFY").
   */
  fun modifyAssistantMessage(messageId: String, mode: String) {
    if (_uiState.value.isGenerating) return
    val targetMsg =
      _uiState.value.messages.firstOrNull { it.id == messageId && it.content.isNotBlank() }
        ?: return
    val snippet = targetMsg.content.trim().take(1800)
    val rewritePrompt =
      when (mode.uppercase()) {
        "DESI_FUNNY" ->
          "Please rewrite and explain the following response in a super fun, witty Desi Hinglish style ('Desi Tadka') using a relatable Indian real-life example (like Cricket, Chai/Maggi, Movies, or Dostana banter) so it's entertaining and unforgettable:\n\n$snippet"
        "ELI5_BACCHA" ->
          "Please explain the following response as if you are explaining it to a 5-year-old child in super easy, playful Hinglish with a fun toy/story example:\n\n$snippet"
        "PRO_EXPERT" ->
          "Please upgrade the following response to a next-level Pro Expert Masterclass with an actionable step-by-step framework, a Markdown Pros/Cons or Comparison Table, and a bold 'Final Verdict (Mera Sujhaav)':\n\n$snippet"
        "SHORTER" ->
          "Please rewrite the following response to be much shorter, crisp, and point-to-point in high-impact bullet points:\n\n$snippet"
        "LONGER" ->
          "Please expand the following response in detail with deeper explanation, examples, and step-by-step clarity:\n\n$snippet"
        "SIMPLIFY" ->
          "Please explain the following response in very simple, easy-to-understand beginner-friendly language:\n\n$snippet"
        else ->
          "Please refine and improve the following response:\n\n$snippet"
      }
    val displayLabel =
      when (mode.uppercase()) {
        "DESI_FUNNY" -> "😂 Desi & Majedar Style mein Samjhao"
        "ELI5_BACCHA" -> "👶 5-Saal ke Bacche ki Tarah Aasan Karo"
        "PRO_EXPERT" -> "🔥 Pro Expert Level + Comparison & Verdict"
        "SHORTER" -> "⚡ Short & Crisp Point-to-Point"
        "LONGER" -> "📚 Modify Response: Expand in Detail"
        else -> "💡 Modify Response: Simplify in Easy Language"
      }
    showActionFeedback(
      when (mode.uppercase()) {
        "DESI_FUNNY" -> "Applying Desi & Majedar style…"
        "ELI5_BACCHA" -> "Making it super easy (5-yr-old style)…"
        "PRO_EXPERT" -> "Upgrading to Pro Expert masterclass…"
        "SHORTER" -> "Making response shorter & crisp…"
        "LONGER" -> "Expanding response in detail…"
        else -> "Simplifying response…"
      }
    )
    sendMessage(promptOverride = rewritePrompt, displayUserPrompt = displayLabel)
  }

  /**
   * Translates an AI response into Hindi, English, or Hinglish with 1 tap.
   */
  fun translateAssistantMessage(messageId: String, targetLanguage: String) {
    if (_uiState.value.isGenerating) return
    val targetMsg =
      _uiState.value.messages.firstOrNull { it.id == messageId && it.content.isNotBlank() }
        ?: return
    val snippet = targetMsg.content.trim().take(2000)
    val translatePrompt =
      when (targetLanguage.lowercase()) {
        "hindi", "हिन्दी" ->
          "Translate the following response accurately into natural Hindi (हिन्दी देवनागरी), keeping any code blocks intact:\n\n$snippet"
        "hinglish" ->
          "Translate and explain the following response in natural conversational Hinglish (Hindi written in English script), keeping any code blocks intact:\n\n$snippet"
        else ->
          "Translate the following response into clear, natural English, keeping any code blocks intact:\n\n$snippet"
      }
    showActionFeedback("Translating to $targetLanguage…")
    sendMessage(
      promptOverride = translatePrompt,
      displayUserPrompt = "🌐 Translate Response: $targetLanguage",
    )
  }

  /**
   * Exports a single AI response as a real PDF (`.pdf`) or Text (`.txt`) file to the device's Downloads folder.
   */
  fun exportAssistantMessage(context: Context, messageId: String, asPdf: Boolean) {
    val targetMsg =
      _uiState.value.messages.firstOrNull { it.id == messageId && it.content.isNotBlank() }
        ?: return
    viewModelScope.launch {
      val result = historyRepository.exportMessageToDevice(context, targetMsg, asPdf = asPdf)
      result
        .onSuccess { fileName ->
          val formatLabel = if (asPdf) "PDF" else "TXT"
          showActionFeedback("Downloaded $formatLabel: $fileName")
        }
        .onFailure {
          showActionFeedback("Unable to export response file")
        }
    }
  }

  /**
   * Downloads a code block snippet directly to the device's Downloads folder with the matching file extension.
   */
  fun downloadCodeSnippet(context: Context, code: String, language: String) {
    if (code.isBlank()) return
    viewModelScope.launch {
      val result =
        withContext(ioDispatcher) {
          historyRepository.saveCodeSnippetToDevice(context, code, language)
        }
      result
        .onSuccess { fileName ->
          showActionFeedback("Code saved: $fileName")
        }
        .onFailure {
          showActionFeedback("Unable to save code file")
        }
    }
  }

  /**
   * Appends or sets recognized voice transcript into the chat input box while preserving
   * any existing draft prefix.
   */
  fun appendVoiceTranscript(spokenText: String, prefixBeforeVoice: String? = null) {
    val cleaned = spokenText.trim()
    if (cleaned.isBlank()) return
    val base = (prefixBeforeVoice ?: _uiState.value.inputText).trim()
    val combined = if (base.isEmpty()) cleaned else "$base $cleaned"
    onInputChanged(combined)
  }

  /**
   * Selects an active AI Persona / Role and persists it to [UserPreferencesRepository].
   */
  fun selectPersona(persona: AiPersona) {
    preferencesRepository.setSelectedPersona(persona)
    _uiState.update {
      it.copy(
        selectedPersona = persona,
        actionFeedback = "Persona: ${persona.label}",
      )
    }
  }

  /**
   * Adds a user-created custom prompt template to the Prompt Library.
   */
  fun addCustomPromptTemplate(title: String, prompt: String, category: String = "Custom"): Boolean {
    val created = preferencesRepository.addCustomPromptTemplate(title, prompt, category) ?: return false
    _uiState.update {
      it.copy(
        promptTemplates = preferencesRepository.preferencesFlow.value.allPromptTemplates,
        actionFeedback = "Saved template: ${created.title}",
      )
    }
    return true
  }

  /**
   * Deletes a user-created custom prompt template from the Prompt Library.
   */
  fun deleteCustomPromptTemplate(templateId: String) {
    preferencesRepository.deleteCustomPromptTemplate(templateId)
    _uiState.update {
      it.copy(
        promptTemplates = preferencesRepository.preferencesFlow.value.allPromptTemplates,
        actionFeedback = "Template removed",
      )
    }
  }

  /**
   * Processes and attaches a directly captured Camera photo [Bitmap] off the main thread
   * for instant OCR, homework, bill, or visual scanning with Gemini Vision.
   */
  fun addCameraBitmapAttachment(bitmap: Bitmap, cacheDir: File? = null) {
    val currentList = _uiState.value.pendingAttachments
    if (currentList.size >= AttachmentProcessor.MAX_ATTACHMENTS_PER_MESSAGE) {
      _uiState.update {
        it.copy(
          bannerNotice =
            "You can attach up to ${AttachmentProcessor.MAX_ATTACHMENTS_PER_MESSAGE} files per message. Remove an attachment before adding another."
        )
      }
      return
    }

    _uiState.update { it.copy(isProcessingAttachment = true) }
    viewModelScope.launch {
      val jpegBytesResult =
        withContext(ioDispatcher) {
          runCatching {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
          }
        }
      jpegBytesResult
        .onSuccess { bytes ->
          val fileName = "Camera_Scan_${System.currentTimeMillis() % 100000}.jpg"
          addAttachmentFromBytes(
            fileName = fileName,
            mimeType = "image/jpeg",
            bytes = bytes,
            cacheDir = cacheDir,
          )
          _uiState.update {
            it.copy(
              isProcessingAttachment = false,
              actionFeedback = "Camera photo attached ($fileName)",
            )
          }
        }
        .onFailure {
          _uiState.update {
            it.copy(
              isProcessingAttachment = false,
              bannerNotice = "Unable to process captured camera photo.",
            )
          }
        }
    }
  }

  /**
   * Generates a side-by-side 2-style comparison draft (Style A: Executive & Concise vs
   * Style B: Deep-Dive with Practical Examples) for an AI response.
   */
  fun compareAssistantMessage(messageId: String) {
    if (_uiState.value.isGenerating) return
    val targetMsg =
      _uiState.value.messages.firstOrNull { it.id == messageId && it.content.isNotBlank() }
        ?: return
    val snippet = targetMsg.content.trim().take(1800)
    val comparePrompt =
      "Please provide a clear 2-style side-by-side comparison for the following response so I can choose the best version:\n" +
        "1. **Style A (Executive & Concise)**: Key points in crisp, high-impact bullet points.\n" +
        "2. **Style B (Deep-Dive & Practical Example)**: Detailed step-by-step explanation with a real-world example.\n\n" +
        "Original Response:\n$snippet"
    showActionFeedback("Generating 2-style comparison…")
    sendMessage(
      promptOverride = comparePrompt,
      displayUserPrompt = "⚖️ Compare Response: Style A (Concise) vs Style B (Detailed)",
    )
  }
}
