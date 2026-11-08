package com.example.data.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.data.local.ChatDao
import com.example.data.local.ConversationEntity
import com.example.data.local.MessageEntity
import com.example.data.model.ChatAttachment
import com.example.data.model.ChatMessage
import com.example.data.model.MessageRole
import com.example.data.model.MessageStatus
import com.example.data.model.WebCitation
import com.example.ui.navigation.RecentConversationItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Production repository managing persistent conversation history, message storage,
 * full-text search across titles and message bodies, renaming, and deletion.
 *
 * Uses Room SQLite ([ChatDao]) when available and maintains a synchronized reactive
 * [StateFlow] cache for immediate UI consistency and resilience against storage errors.
 */
class ChatHistoryRepository(
  private val chatDao: ChatDao? = null,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
  private val conversationsMap = ConcurrentHashMap<String, ConversationEntity>()
  private val messagesByConversation = ConcurrentHashMap<String, MutableList<ChatMessage>>()
  private val searchableContentByConversation = ConcurrentHashMap<String, String>()
  private val conversationOwners = ConcurrentHashMap<String, String>()
  private val monotonicClock = AtomicLong(0L)

  @Volatile private var activeUserUid: String = ""
  @Volatile private var activeAuthIdToken: String = ""
  @Volatile private var ownerPrefs: android.content.SharedPreferences? = null

  private val _conversationsFlow = MutableStateFlow<List<RecentConversationItem>>(emptyList())
  val conversationsFlow: StateFlow<List<RecentConversationItem>> = _conversationsFlow.asStateFlow()

  private val _storageErrorFlow = MutableStateFlow<String?>(null)
  val storageErrorFlow: StateFlow<String?> = _storageErrorFlow.asStateFlow()

  /**
   * Returns true if [conversationId] belongs to the currently active authenticated user.
   * - When `activeUserUid` is empty (`""`, used in standalone unit tests), all local conversations are accessible.
   * - When `activeUserUid` is `"__signed_out__"`, zero conversations are accessible.
   * - When `activeUserUid` is an authenticated UID, ONLY conversations bound to that exact UID are accessible.
   */
  private fun isOwnedByActiveUser(conversationId: String): Boolean {
    val uid = activeUserUid
    if (uid.isBlank()) return true
    if (uid == "__signed_out__") return false
    val owner = conversationOwners[conversationId]
    return owner == uid || conversationId.startsWith("${uid}_")
  }

  private fun bindConversationToActiveUser(conversationId: String) {
    val uid = activeUserUid
    if (uid.isNotBlank() && uid != "__signed_out__") {
      conversationOwners[conversationId] = uid
      try {
        ownerPrefs?.edit()?.putString("owner_$conversationId", uid)?.apply()
      } catch (_: Exception) {}
    }
  }

  /**
   * Sets the currently authenticated user's UID and Auth ID token, immediately filtering
   * visible conversations to ONLY those owned by [uid] and syncing their private cloud vault.
   */
  suspend fun setActiveUser(
    uid: String,
    idToken: String = "",
    context: Context? = null,
  ) =
    withContext(ioDispatcher) {
      if (context != null && ownerPrefs == null) {
        try {
          val prefs =
            context.applicationContext.getSharedPreferences(
              "astryn_conversation_owners",
              Context.MODE_PRIVATE,
            )
          ownerPrefs = prefs
          for ((k, v) in prefs.all) {
            if (k.startsWith("owner_") && v is String && v.isNotBlank()) {
              conversationOwners[k.removePrefix("owner_")] = v
            }
          }
        } catch (_: Exception) {}
      }

      activeUserUid = uid.trim()
      activeAuthIdToken = idToken.trim()
      emitSortedConversations()

      // If a real user is signed in, pull their isolated conversations from Firebase Cloud Vault
      if (activeUserUid.isNotBlank() && activeUserUid != "__signed_out__") {
        val currentUid = activeUserUid
        val cloudItems =
          com.example.data.api.FirebaseRestAuthClient.fetchUserConversationsFromCloud(
            uid = currentUid,
            idToken = activeAuthIdToken,
          )
        if (cloudItems.isNotEmpty() && activeUserUid == currentUid) {
          for (cloudConv in cloudItems) {
            val convId = cloudConv.conversation.id
            val existingOwner = conversationOwners[convId]
            if (existingOwner != null && existingOwner != currentUid) continue

            conversationOwners[convId] = currentUid
            try {
              ownerPrefs?.edit()?.putString("owner_$convId", currentUid)?.apply()
            } catch (_: Exception) {}

            val localConv = conversationsMap[convId]
            if (localConv == null || cloudConv.conversation.updatedAt >= localConv.updatedAt) {
              conversationsMap[convId] = cloudConv.conversation
              if (cloudConv.messages.isNotEmpty()) {
                messagesByConversation[convId] =
                  cloudConv.messages.sortedBy { it.timestamp }.toMutableList()
                rebuildSearchableContentForConversation(convId)
              }
              try {
                if (chatDao != null) {
                  val inserted = chatDao.insertConversationIfAbsent(cloudConv.conversation)
                  if (inserted == -1L) {
                    chatDao.updateConversationMetadata(
                      id = cloudConv.conversation.id,
                      title = cloudConv.conversation.title,
                      preview = cloudConv.conversation.preview,
                      updatedAt = cloudConv.conversation.updatedAt,
                    )
                  }
                  for (msg in cloudConv.messages) {
                    chatDao.upsertMessage(msg.toEntity(convId))
                  }
                }
              } catch (_: Exception) {}
            }
          }
          emitSortedConversations()
        }
      }
    }

  private fun rebuildSearchableContentForConversation(conversationId: String) {
    val list = messagesByConversation[conversationId]
    if (list == null) {
      searchableContentByConversation.remove(conversationId)
      return
    }
    val joined =
      synchronized(list) {
        if (list.isEmpty()) {
          ""
        } else {
          buildString(capacity = list.sumOf { it.content.length + 1 }) {
            for (i in list.indices) {
              if (i > 0) append('\n')
              append(list[i].content)
              for (att in list[i].attachments) {
                append(' ')
                append(att.fileName)
              }
            }
          }
        }
      }
    searchableContentByConversation[conversationId] = joined
  }

  private fun nextTimestamp(candidate: Long = 0L): Long {
    val wallNow = System.currentTimeMillis()
    val base = maxOf(wallNow, candidate)
    while (true) {
      val prev = monotonicClock.get()
      val next = maxOf(base, prev + 1L)
      if (monotonicClock.compareAndSet(prev, next)) {
        return next
      }
    }
  }

  companion object {
    const val MAX_TITLE_LENGTH = 80
    private const val AUTO_TITLE_MAX_LENGTH = 38
    private const val PREVIEW_MAX_LENGTH = 80

    @Volatile private var defaultInstance: ChatHistoryRepository? = null

    /**
     * Returns the shared repository instance, upgrading it with [ChatDao] when provided.
     */
    fun getInstance(chatDao: ChatDao? = null): ChatHistoryRepository {
      val current = defaultInstance
      if (current != null && (chatDao == null || current.chatDao != null)) {
        return current
      }
      return synchronized(this) {
        val existing = defaultInstance
        if (existing != null && (chatDao == null || existing.chatDao != null)) {
          existing
        } else {
          ChatHistoryRepository(chatDao = chatDao).also { defaultInstance = it }
        }
      }
    }

    /**
     * Generates a clean, deterministic conversation title from the first meaningful user message.
     */
    fun generateTitleFromMessage(rawText: String): String {
      val cleaned =
        rawText
          .replace(Regex("[#*`_~>]+"), " ")
          .replace(Regex("\\s+"), " ")
          .trim()
          .trimStart('.', ',', ':', ';', '-', '!', '?')
          .trim()

      if (cleaned.isBlank()) {
        return "New Conversation"
      }

      val firstClause =
        cleaned
          .split(Regex("[.!?\\n]"))
          .firstOrNull { it.trim().length >= 3 }
          ?.trim()
          ?: cleaned

      val capitalized =
        firstClause.replaceFirstChar { ch ->
          if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
        }

      if (capitalized.length <= AUTO_TITLE_MAX_LENGTH) {
        return capitalized
      }

      val truncated = capitalized.take(AUTO_TITLE_MAX_LENGTH)
      val lastSpace = truncated.lastIndexOf(' ')
      return if (lastSpace >= 16) {
        truncated.substring(0, lastSpace).trimEnd() + "…"
      } else {
        truncated.trimEnd() + "…"
      }
    }

    /**
     * Formats a timestamp into a compact human-readable relative label.
     */
    fun formatRelativeTimestamp(
      timestampMillis: Long,
      nowMillis: Long = System.currentTimeMillis(),
    ): String {
      if (timestampMillis <= 0L) return "Just now"
      val diff = (nowMillis - timestampMillis).coerceAtLeast(0L)
      return when {
        diff < 60_000L -> "Just now"
        diff < 3_600_000L -> "${diff / 60_000L}m ago"
        diff < 86_400_000L -> "${diff / 3_600_000L}h ago"
        diff < 604_800_000L -> "${diff / 86_400_000L}d ago"
        else -> {
          try {
            SimpleDateFormat("MMM d", Locale.US).format(Date(timestampMillis))
          } catch (_: Exception) {
            "Saved"
          }
        }
      }
    }
  }

  /**
   * Loads saved conversations and messages from Room SQLite into the reactive cache.
   * Handles malformed records or SQLite exceptions gracefully without crashing.
   */
  suspend fun loadFromStorage() =
    withContext(ioDispatcher) {
      val dao = chatDao ?: return@withContext
      try {
        val savedConversations = dao.getAllConversations()
        val savedMessages = dao.getAllMessages()

        val groupedMessages = savedMessages.groupBy { it.conversationId }

        for (conv in savedConversations) {
          if (conv.id.isBlank()) continue
          val safeUpdated = if (conv.updatedAt > 0L) conv.updatedAt else System.currentTimeMillis()
          monotonicClock.updateAndGet { prev -> maxOf(prev, safeUpdated) }
          conversationsMap[conv.id] =
            conv.copy(
              title = conv.title.ifBlank { "Untitled Conversation" },
              updatedAt = safeUpdated,
            )
          val mappedMessages =
            groupedMessages[conv.id]
              ?.mapNotNull { entity -> entity.toDomainOrNull() }
              ?.sortedBy { it.timestamp }
              ?: emptyList()
          messagesByConversation[conv.id] = mappedMessages.toMutableList()
          rebuildSearchableContentForConversation(conv.id)
        }

        emitSortedConversations()
        _storageErrorFlow.value = null
      } catch (e: Exception) {
        _storageErrorFlow.value = "Unable to load saved history from device storage."
      }
    }

  /**
   * Saves or updates a message inside a conversation, automatically creating or updating
   * the parent conversation metadata and sorting order when meaningful content is present.
   */
  suspend fun saveMessage(
    conversationId: String,
    message: ChatMessage,
  ): Result<Unit> =
    withContext(ioDispatcher) {
      if (conversationId.isBlank()) {
        return@withContext Result.failure(IllegalArgumentException("Conversation ID is blank."))
      }
      if (message.content.isBlank() && message.status != MessageStatus.STREAMING) {
        return@withContext Result.success(Unit)
      }

      val safeMessage =
        if (com.example.data.api.SafeDiagnosticLogger.containsSecretCredential(message.content)) {
          message.copy(
            content = com.example.data.api.SafeDiagnosticLogger.redactSecrets(message.content)
          )
        } else {
          message
        }

      try {
        val existingOwner = conversationOwners[conversationId]
        if (
          activeUserUid.isNotBlank() &&
            activeUserUid != "__signed_out__" &&
            existingOwner != null &&
            existingOwner != activeUserUid
        ) {
          return@withContext Result.failure(
            IllegalStateException("Security Policy: Cannot write to another user's conversation.")
          )
        }
        bindConversationToActiveUser(conversationId)

        val list =
          messagesByConversation.getOrPut(conversationId) { mutableListOf() }

        synchronized(list) {
          val existingIndex = list.indexOfFirst { it.id == safeMessage.id }
          if (existingIndex >= 0) {
            list[existingIndex] = safeMessage
          } else {
            // Fast path: if timestamp is >= last message, append without full O(M log M) sort
            val lastTs = list.lastOrNull()?.timestamp ?: Long.MIN_VALUE
            list.add(safeMessage)
            if (safeMessage.timestamp < lastTs) {
              list.sortBy { it.timestamp }
            }
          }
        }

        // Only persist conversation metadata once at least one meaningful message exists
        val hasMeaningfulMessage =
          synchronized(list) {
            list.any { it.content.isNotBlank() }
          }

        if (!hasMeaningfulMessage) {
          return@withContext Result.success(Unit)
        }

        // Update searchable text only for this modified conversation
        rebuildSearchableContentForConversation(conversationId)

        val existingConv = conversationsMap[conversationId]
        val guaranteedUpdatedAt =
          nextTimestamp(maxOf(safeMessage.timestamp, existingConv?.updatedAt ?: 0L))

        val resolvedTitle =
          if (existingConv == null || existingConv.title.isBlank() || existingConv.title == "New Conversation") {
            val firstUserMsg =
              synchronized(list) {
                list.firstOrNull { it.role == MessageRole.USER && it.content.isNotBlank() }
              }
            if (firstUserMsg != null) {
              generateTitleFromMessage(firstUserMsg.content)
            } else {
              existingConv?.title ?: generateTitleFromMessage(safeMessage.content)
            }
          } else {
            existingConv.title
          }

        val previewSnippet =
          safeMessage.content
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(PREVIEW_MAX_LENGTH)
            .ifBlank { existingConv?.preview ?: "" }

        val updatedConv =
          ConversationEntity(
            id = conversationId,
            title = resolvedTitle,
            preview = previewSnippet,
            createdAt = existingConv?.createdAt ?: guaranteedUpdatedAt,
            updatedAt = guaranteedUpdatedAt,
            isPinned = existingConv?.isPinned ?: false,
            isLiked = existingConv?.isLiked ?: false,
          )

        conversationsMap[conversationId] = updatedConv
        emitSortedConversations()

        // Persist to SQLite if available and message is not an empty streaming placeholder
        if (chatDao != null && safeMessage.content.isNotBlank()) {
          val insertedRowId = chatDao.insertConversationIfAbsent(updatedConv)
          if (insertedRowId == -1L) {
            chatDao.updateConversationMetadata(
              id = updatedConv.id,
              title = updatedConv.title,
              preview = updatedConv.preview,
              updatedAt = updatedConv.updatedAt,
            )
          }
          chatDao.upsertMessage(safeMessage.toEntity(conversationId))
        }

        // Sync to authenticated user's isolated cloud vault when message is complete
        if (
          activeUserUid.isNotBlank() &&
            activeUserUid != "__signed_out__" &&
            safeMessage.status != MessageStatus.STREAMING &&
            safeMessage.content.isNotBlank()
        ) {
          val snapshotMessages = synchronized(list) { list.toList() }
          com.example.data.api.FirebaseRestAuthClient.syncUserConversationToCloud(
            uid = activeUserUid,
            idToken = activeAuthIdToken,
            conversation = updatedConv,
            messages = snapshotMessages,
          )
        }

        _storageErrorFlow.value = null
        Result.success(Unit)
      } catch (e: Exception) {
        _storageErrorFlow.value = "Failed to save message to local storage."
        Result.failure(e)
      }
    }

  /**
   * Retrieves the ordered messages for a specific conversation ID without mixing sessions.
   */
  suspend fun getMessagesForConversation(conversationId: String): List<ChatMessage> =
    withContext(ioDispatcher) {
      if (conversationId.isBlank()) return@withContext emptyList()
      val existingOwner = conversationOwners[conversationId]
      if (
        activeUserUid.isNotBlank() &&
          (activeUserUid == "__signed_out__" ||
            (existingOwner != null && existingOwner != activeUserUid))
      ) {
        return@withContext emptyList()
      }

      val cached = messagesByConversation[conversationId]
      if (cached != null) {
        return@withContext synchronized(cached) { cached.sortedBy { it.timestamp }.toList() }
      }

      val dao = chatDao ?: return@withContext emptyList()
      try {
        val loaded =
          dao
            .getMessagesForConversation(conversationId)
            .mapNotNull { it.toDomainOrNull() }
            .sortedBy { it.timestamp }
        if (loaded.isNotEmpty()) {
          messagesByConversation[conversationId] = loaded.toMutableList()
          rebuildSearchableContentForConversation(conversationId)
        }
        loaded
      } catch (e: Exception) {
        _storageErrorFlow.value = "Could not restore messages for this conversation."
        emptyList()
      }
    }

  /**
   * Replaces the message list of a conversation (used when editing a user message or
   * regenerating an assistant response) so invalidated subsequent replies are removed
   * consistently from both memory and Room SQLite.
   */
  suspend fun replaceMessagesForConversation(
    conversationId: String,
    retainedMessages: List<ChatMessage>,
  ): Result<Unit> =
    withContext(ioDispatcher) {
      if (conversationId.isBlank()) {
        return@withContext Result.failure(IllegalArgumentException("Conversation ID is blank."))
      }
      try {
        val currentList = messagesByConversation.getOrPut(conversationId) { mutableListOf() }
        val retainedIds = retainedMessages.map { it.id }.toSet()
        val removedIds: List<String>

        synchronized(currentList) {
          removedIds = currentList.map { it.id }.filter { it !in retainedIds }
          currentList.clear()
          currentList.addAll(retainedMessages.sortedBy { it.timestamp })
        }

        rebuildSearchableContentForConversation(conversationId)

        if (chatDao != null && removedIds.isNotEmpty()) {
          chatDao.deleteMessagesByIds(conversationId, removedIds)
        }

        val lastMeaningful = retainedMessages.lastOrNull { it.content.isNotBlank() }
        val existingConv = conversationsMap[conversationId]
        if (existingConv != null && lastMeaningful != null) {
          val updatedAt = nextTimestamp(maxOf(lastMeaningful.timestamp, existingConv.updatedAt))
          val previewSnippet =
            lastMeaningful.content
              .replace(Regex("\\s+"), " ")
              .trim()
              .take(PREVIEW_MAX_LENGTH)
          val updatedConv =
            existingConv.copy(
              preview = previewSnippet,
              updatedAt = updatedAt,
            )
          conversationsMap[conversationId] = updatedConv
          chatDao?.updateConversationMetadata(
            id = updatedConv.id,
            title = updatedConv.title,
            preview = updatedConv.preview,
            updatedAt = updatedConv.updatedAt,
          )
        }
        emitSortedConversations()
        Result.success(Unit)
      } catch (e: Exception) {
        _storageErrorFlow.value = "Failed to update conversation messages."
        Result.failure(e)
      }
    }

  /**
   * Returns the current title of a saved conversation, or null if not yet saved.
   */
  fun getConversationTitle(conversationId: String): String? {
    return conversationsMap[conversationId]?.title
  }

  /**
   * Renames an existing conversation and updates its modified timestamp.
   */
  suspend fun renameConversation(
    conversationId: String,
    newTitle: String,
  ): Result<String> =
    withContext(ioDispatcher) {
      val sanitized = newTitle.replace(Regex("\\s+"), " ").trim().take(MAX_TITLE_LENGTH)
      if (sanitized.isBlank()) {
        return@withContext Result.failure(
          IllegalArgumentException("Conversation title cannot be empty.")
        )
      }

      val existing =
        conversationsMap[conversationId]
          ?: return@withContext Result.failure(
            IllegalStateException("Conversation not found.")
          )

      try {
        val updatedAt = nextTimestamp(existing.updatedAt)
        val updated = existing.copy(title = sanitized, updatedAt = updatedAt)
        conversationsMap[conversationId] = updated
        emitSortedConversations()

        chatDao?.renameConversation(
          id = conversationId,
          title = sanitized,
          updatedAt = updatedAt,
        )
        _storageErrorFlow.value = null
        Result.success(sanitized)
      } catch (e: Exception) {
        _storageErrorFlow.value = "Failed to rename conversation."
        Result.failure(e)
      }
    }

  /**
   * Deletes a conversation, all of its messages, its search index, and any cached Markdown blocks.
   */
  suspend fun deleteConversation(conversationId: String): Result<Unit> =
    withContext(ioDispatcher) {
      if (conversationId.isBlank()) {
        return@withContext Result.failure(IllegalArgumentException("Invalid conversation ID."))
      }

      try {
        conversationsMap.remove(conversationId)
        messagesByConversation.remove(conversationId)
        searchableContentByConversation.remove(conversationId)
        conversationOwners.remove(conversationId)
        try {
          ownerPrefs?.edit()?.remove("owner_$conversationId")?.apply()
        } catch (_: Exception) {}
        com.example.ui.components.SafeMarkdownParser.clearCaches()
        emitSortedConversations()

        chatDao?.deleteAllMessagesForConversation(conversationId)
        chatDao?.deleteConversationById(conversationId)

        if (activeUserUid.isNotBlank() && activeUserUid != "__signed_out__") {
          com.example.data.api.FirebaseRestAuthClient.deleteUserConversationFromCloud(
            uid = activeUserUid,
            idToken = activeAuthIdToken,
            conversationId = conversationId,
          )
        }
        _storageErrorFlow.value = null
        Result.success(Unit)
      } catch (e: Exception) {
        _storageErrorFlow.value = "Failed to delete conversation from storage."
        Result.failure(e)
      }
    }

  /**
   * Permanently deletes conversations for the active user (or all conversations in unit tests)
   * without altering another user's private history.
   */
  suspend fun clearAllConversations(): Result<Unit> =
    withContext(ioDispatcher) {
      try {
        val currentUid = activeUserUid
        if (currentUid.isNotBlank() && currentUid != "__signed_out__") {
          val ownedIds =
            conversationsMap.keys.filter { convId -> isOwnedByActiveUser(convId) }
          for (convId in ownedIds) {
            conversationsMap.remove(convId)
            messagesByConversation.remove(convId)
            searchableContentByConversation.remove(convId)
            conversationOwners.remove(convId)
            try {
              ownerPrefs?.edit()?.remove("owner_$convId")?.apply()
            } catch (_: Exception) {}
            chatDao?.deleteAllMessagesForConversation(convId)
            chatDao?.deleteConversationById(convId)
            com.example.data.api.FirebaseRestAuthClient.deleteUserConversationFromCloud(
              uid = currentUid,
              idToken = activeAuthIdToken,
              conversationId = convId,
            )
          }
          com.example.ui.components.SafeMarkdownParser.clearCaches()
          emitSortedConversations()
        } else {
          conversationsMap.clear()
          messagesByConversation.clear()
          searchableContentByConversation.clear()
          com.example.ui.components.SafeMarkdownParser.clearCaches()
          emitSortedConversations()

          chatDao?.deleteAllMessages()
          chatDao?.deleteAllConversations()
        }
        _storageErrorFlow.value = null
        Result.success(Unit)
      } catch (e: Exception) {
        _storageErrorFlow.value = "Failed to clear chat history from storage."
        Result.failure(e)
      }
    }

  /**
   * Searches conversations by title, preview snippet, and full message content.
   * Returns results sorted by most recently updated.
   */
  fun searchConversations(query: String): List<RecentConversationItem> {
    val cleanQuery = query.trim()
    val allItems = _conversationsFlow.value
    if (cleanQuery.isBlank()) {
      return allItems
    }
    return allItems.filter { item -> item.matchesQuery(cleanQuery) }
  }

  /**
   * Toggles the pinned state of an existing conversation so pinned threads stay at the top of the list.
   */
  suspend fun togglePinConversation(conversationId: String): Result<Boolean> =
    withContext(ioDispatcher) {
      val existing =
        conversationsMap[conversationId]
          ?: return@withContext Result.failure(IllegalStateException("Conversation not found."))
      try {
        val nextPinned = !existing.isPinned
        val updatedAt = nextTimestamp(existing.updatedAt)
        val updated = existing.copy(isPinned = nextPinned, updatedAt = updatedAt)
        conversationsMap[conversationId] = updated
        emitSortedConversations()
        chatDao?.updateConversationPinned(
          id = conversationId,
          isPinned = nextPinned,
          updatedAt = updatedAt,
        )
        _storageErrorFlow.value = null
        Result.success(nextPinned)
      } catch (e: Exception) {
        _storageErrorFlow.value = "Failed to update conversation pin state."
        Result.failure(e)
      }
    }

  /**
   * Toggles the liked/favorite state of an existing conversation.
   */
  suspend fun toggleLikeConversation(conversationId: String): Result<Boolean> =
    withContext(ioDispatcher) {
      val existing =
        conversationsMap[conversationId]
          ?: return@withContext Result.failure(IllegalStateException("Conversation not found."))
      try {
        val nextLiked = !existing.isLiked
        val updatedAt = nextTimestamp(existing.updatedAt)
        val updated = existing.copy(isLiked = nextLiked, updatedAt = updatedAt)
        conversationsMap[conversationId] = updated
        emitSortedConversations()
        chatDao?.updateConversationLiked(
          id = conversationId,
          isLiked = nextLiked,
          updatedAt = updatedAt,
        )
        _storageErrorFlow.value = null
        Result.success(nextLiked)
      } catch (e: Exception) {
        _storageErrorFlow.value = "Failed to update conversation like state."
        Result.failure(e)
      }
    }

  /**
   * Toggles the liked state of a specific message inside a conversation and persists to Room SQLite.
   */
  suspend fun toggleLikeMessage(
    conversationId: String,
    messageId: String,
  ): Result<Boolean> =
    withContext(ioDispatcher) {
      if (conversationId.isBlank() || messageId.isBlank()) {
        return@withContext Result.failure(IllegalArgumentException("Invalid ID."))
      }
      try {
        val list = messagesByConversation[conversationId]
        var nextLikedState = false
        var updatedMessage: ChatMessage? = null
        if (list != null) {
          synchronized(list) {
            val idx = list.indexOfFirst { it.id == messageId }
            if (idx >= 0) {
              val current = list[idx]
              nextLikedState = !current.isLiked
              val modified = current.copy(isLiked = nextLikedState)
              list[idx] = modified
              updatedMessage = modified
            }
          }
        }
        if (updatedMessage != null) {
          chatDao?.updateMessageLiked(messageId = messageId, isLiked = nextLikedState)
          Result.success(nextLikedState)
        } else {
          Result.failure(IllegalStateException("Message not found."))
        }
      } catch (e: Exception) {
        _storageErrorFlow.value = "Failed to update message like state."
        Result.failure(e)
      }
    }

  /**
   * Toggles the bookmarked (saved to favorites) state of a specific message and persists to Room SQLite.
   */
  suspend fun toggleBookmarkMessage(
    conversationId: String,
    messageId: String,
  ): Result<Boolean> =
    withContext(ioDispatcher) {
      if (conversationId.isBlank() || messageId.isBlank()) {
        return@withContext Result.failure(IllegalArgumentException("Invalid ID."))
      }
      try {
        val list = messagesByConversation[conversationId]
        var nextBookmarkedState = false
        var updatedMessage: ChatMessage? = null
        if (list != null) {
          synchronized(list) {
            val idx = list.indexOfFirst { it.id == messageId }
            if (idx >= 0) {
              val current = list[idx]
              nextBookmarkedState = !current.isBookmarked
              val modified = current.copy(isBookmarked = nextBookmarkedState)
              list[idx] = modified
              updatedMessage = modified
            }
          }
        }
        if (updatedMessage != null) {
          chatDao?.updateMessageBookmarked(
            messageId = messageId,
            isBookmarked = nextBookmarkedState,
          )
          Result.success(nextBookmarkedState)
        } else {
          Result.failure(IllegalStateException("Message not found."))
        }
      } catch (e: Exception) {
        _storageErrorFlow.value = "Failed to update message bookmark state."
        Result.failure(e)
      }
    }

  /**
   * Formats a complete, human-readable transcript of a conversation for sharing or downloading.
   */
  suspend fun formatConversationForExport(conversationId: String): String =
    withContext(ioDispatcher) {
      val conv = conversationsMap[conversationId]
      val title = conv?.title?.ifBlank { "Astryn AI Conversation" } ?: "Astryn AI Conversation"
      val messages = getMessagesForConversation(conversationId)
      buildString {
        append("========================================\n")
        append("ASTRYN AI — CONVERSATION TRANSCRIPT\n")
        append("Title: ").append(title).append('\n')
        if (conv != null && conv.updatedAt > 0L) {
          val dateStr =
            try {
              SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(conv.updatedAt))
            } catch (_: Exception) {
              ""
            }
          if (dateStr.isNotBlank()) {
            append("Updated: ").append(dateStr).append('\n')
          }
        }
        append("========================================\n\n")
        if (messages.isEmpty()) {
          val preview = conv?.preview.orEmpty()
          if (preview.isNotBlank()) {
            append(preview).append('\n')
          } else {
            append("(No messages in conversation)\n")
          }
        } else {
          messages.forEachIndexed { idx, msg ->
            if (idx > 0) append("\n----------------------------------------\n\n")
            val speaker =
              when (msg.role) {
                MessageRole.USER -> "You"
                MessageRole.MODEL -> "Astryn AI"
                MessageRole.SYSTEM -> "System"
              }
            append("[").append(speaker).append("]\n")
            if (msg.attachments.isNotEmpty()) {
              msg.attachments.forEach { att ->
                append("• Attachment: ").append(att.fileName).append(" (").append(att.formattedFileSize).append(")\n")
              }
              append('\n')
            }
            append(msg.formatForCopyOrShare()).append('\n')
          }
        }
      }.trimEnd()
    }

  /**
   * Exports and downloads a conversation transcript as a `.txt` file into the device's Downloads
   * directory (using MediaStore.Downloads on Android 10+ and app Downloads directory fallback)
   * without requiring legacy storage permissions.
   */
  suspend fun downloadConversationToDevice(
    context: Context,
    conversationId: String,
  ): Result<String> =
    withContext(ioDispatcher) {
      try {
        val conv = conversationsMap[conversationId]
        val rawTitle = conv?.title?.ifBlank { "Conversation" } ?: "Conversation"
        val transcript = formatConversationForExport(conversationId)
        val safeSlug =
          rawTitle
            .replace(Regex("[^a-zA-Z0-9_\\- ]"), "")
            .trim()
            .replace(Regex("\\s+"), "_")
            .take(36)
            .ifBlank { "Chat" }
        val fileName = "Astryn_AI_${safeSlug}_${System.currentTimeMillis() % 100000}.txt"
        val bytes = transcript.toByteArray(Charsets.UTF_8)

        var savedViaMediaStore = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          try {
            val resolver = context.contentResolver
            val contentValues =
              ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
              }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
              resolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
                out.flush()
              }
              contentValues.clear()
              contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
              resolver.update(uri, contentValues, null, null)
              savedViaMediaStore = true
            }
          } catch (_: Exception) {
            savedViaMediaStore = false
          }
        }

        // Always also write a local accessible copy in app Downloads / filesDir for reliable verification
        val downloadsDir =
          context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "Downloads").apply { mkdirs() }
        if (!downloadsDir.exists()) {
          downloadsDir.mkdirs()
        }
        val targetFile = File(downloadsDir, fileName)
        targetFile.writeBytes(bytes)

        if (savedViaMediaStore || targetFile.exists()) {
          Result.success(fileName)
        } else {
          Result.failure(IllegalStateException("Could not write file to Downloads."))
        }
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  /**
   * Exports a single AI response as a real multi-page PDF document (`.pdf`) or Markdown/Text file (`.txt`)
   * into the device's Downloads folder.
   */
  suspend fun exportMessageToDevice(
    context: Context,
    message: ChatMessage,
    asPdf: Boolean,
  ): Result<String> =
    withContext(ioDispatcher) {
      try {
        val dateStr =
          SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(message.timestamp))
        val bodyText = message.formatForCopyOrShare().ifBlank { message.content }
        val extension = if (asPdf) "pdf" else "txt"
        val mimeType = if (asPdf) "application/pdf" else "text/plain"
        val fileName =
          "Astryn_Response_${message.id.take(6)}_${System.currentTimeMillis() % 100000}.$extension"

        val bytes: ByteArray =
          if (asPdf) {
            generatePdfBytesForMessage(dateStr = dateStr, bodyText = bodyText)
          } else {
            buildString {
              append("Astryn AI Response\n")
              append("Timestamp: ").append(dateStr).append("\n")
              append("========================================\n\n")
              append(bodyText).append("\n")
            }.toByteArray(Charsets.UTF_8)
          }

        saveBytesToDownloads(
          context = context,
          fileName = fileName,
          mimeType = mimeType,
          bytes = bytes,
        )
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  private fun generatePdfBytesForMessage(dateStr: String, bodyText: String): ByteArray {
    return try {
      val pdfDocument = PdfDocument()
      val pageWidth = 595
      val pageHeight = 842
      val margin = 44f
      val usableWidth = pageWidth - margin * 2

      val titlePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = AndroidColor.rgb(15, 23, 42)
          textSize = 16f
          isFakeBoldText = true
        }
      val metaPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = AndroidColor.rgb(100, 116, 139)
          textSize = 10.5f
        }
      val bodyPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = AndroidColor.rgb(30, 41, 59)
          textSize = 11.5f
        }

      val wrappedLines = mutableListOf<String>()
      for (paragraph in bodyText.split('\n')) {
        if (paragraph.isBlank()) {
          wrappedLines.add("")
          continue
        }
        var start = 0
        while (start < paragraph.length) {
          val count =
            bodyPaint
              .breakText(paragraph, start, paragraph.length, true, usableWidth, null)
              .coerceAtLeast(minOf(80, paragraph.length - start))
          wrappedLines.add(paragraph.substring(start, start + count))
          start += count
        }
      }

      val lineHeight = 17f
      var lineCursor = 0
      var pageNumber = 1

      while (lineCursor < wrappedLines.size || pageNumber == 1) {
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        var y = margin + 14f

        if (pageNumber == 1) {
          canvas.drawText("Astryn AI Response", margin, y, titlePaint)
          y += 18f
          canvas.drawText("Generated: $dateStr", margin, y, metaPaint)
          y += 24f
        }

        while (lineCursor < wrappedLines.size && y < pageHeight - margin) {
          canvas.drawText(wrappedLines[lineCursor], margin, y, bodyPaint)
          y += lineHeight
          lineCursor++
        }

        pdfDocument.finishPage(page)
        pageNumber++
        if (lineCursor >= wrappedLines.size) break
      }

      val out = ByteArrayOutputStream()
      pdfDocument.writeTo(out)
      pdfDocument.close()
      val raw = out.toByteArray()
      if (raw.isNotEmpty()) {
        raw
      } else {
        "%PDF-1.4\n% Astryn AI Response ($dateStr)\n${bodyText}\n%%EOF".toByteArray(Charsets.UTF_8)
      }
    } catch (_: Exception) {
      "%PDF-1.4\n% Astryn AI Response ($dateStr)\n${bodyText}\n%%EOF".toByteArray(Charsets.UTF_8)
    }
  }

  /**
   * Saves a code block snippet with the appropriate programming language file extension
   * into the device's Downloads folder.
   */
  fun saveCodeSnippetToDevice(
    context: Context,
    code: String,
    language: String,
  ): Result<String> {
    val ext =
      when (language.trim().lowercase()) {
        "kotlin", "kt" -> "kt"
        "python", "py" -> "py"
        "javascript", "js" -> "js"
        "typescript", "ts" -> "ts"
        "html" -> "html"
        "css" -> "css"
        "json" -> "json"
        "sql" -> "sql"
        "bash", "sh", "shell" -> "sh"
        "java" -> "java"
        "cpp", "c++" -> "cpp"
        "xml" -> "xml"
        else -> "txt"
      }
    val fileName = "Astryn_Code_${System.currentTimeMillis() % 100000}.$ext"
    return saveBytesToDownloads(
      context = context,
      fileName = fileName,
      mimeType = "text/plain",
      bytes = code.toByteArray(Charsets.UTF_8),
    )
  }

  private fun saveBytesToDownloads(
    context: Context,
    fileName: String,
    mimeType: String,
    bytes: ByteArray,
  ): Result<String> {
    return try {
      var savedViaMediaStore = false
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
          val resolver = context.contentResolver
          val contentValues =
            ContentValues().apply {
              put(MediaStore.Downloads.DISPLAY_NAME, fileName)
              put(MediaStore.Downloads.MIME_TYPE, mimeType)
              put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
              put(MediaStore.Downloads.IS_PENDING, 1)
            }
          val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
          if (uri != null) {
            resolver.openOutputStream(uri)?.use { out ->
              out.write(bytes)
              out.flush()
            }
            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
            savedViaMediaStore = true
          }
        } catch (_: Exception) {
          savedViaMediaStore = false
        }
      }

      val downloadsDir =
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
          ?: File(context.filesDir, "Downloads").apply { mkdirs() }
      if (!downloadsDir.exists()) {
        downloadsDir.mkdirs()
      }
      val targetFile = File(downloadsDir, fileName)
      targetFile.writeBytes(bytes)

      if (savedViaMediaStore || targetFile.exists()) {
        Result.success(fileName)
      } else {
        Result.failure(IllegalStateException("Could not write file to Downloads."))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  /**
   * Clears any transient storage error banner message.
   */
  fun clearStorageError() {
    _storageErrorFlow.value = null
  }

  private fun emitSortedConversations() {
    val now = System.currentTimeMillis()
    val sorted =
      conversationsMap.values
        .filter { conv -> isOwnedByActiveUser(conv.id) }
        .sortedWith(
          compareByDescending<ConversationEntity> { it.isPinned }
            .thenByDescending { it.updatedAt }
        )
        .map { conv ->
          val fullText = searchableContentByConversation[conv.id] ?: conv.preview
          RecentConversationItem(
            id = conv.id,
            title = conv.title,
            preview = conv.preview,
            timestampFormatted = formatRelativeTimestamp(conv.updatedAt, now),
            updatedAt = conv.updatedAt,
            searchableContent = fullText,
            isPinned = conv.isPinned,
            isLiked = conv.isLiked,
          )
        }
    _conversationsFlow.value = sorted
  }

  private fun ChatMessage.toEntity(conversationId: String): MessageEntity {
    return MessageEntity(
      id = id,
      conversationId = conversationId,
      role = role.name,
      content = content,
      timestamp = timestamp,
      status = status.name,
      errorMessage = errorMessage,
      attachmentsJson = ChatAttachment.serializeList(attachments),
      citationsJson = WebCitation.serializeList(citations),
      isLiked = isLiked,
      isBookmarked = isBookmarked,
    )
  }

  private fun MessageEntity.toDomainOrNull(): ChatMessage? {
    val parsedAttachments = ChatAttachment.deserializeList(attachmentsJson)
    val parsedCitations = WebCitation.deserializeList(citationsJson)
    if (id.isBlank() || (content.isBlank() && parsedAttachments.isEmpty())) return null
    val parsedRole =
      try {
        MessageRole.valueOf(role)
      } catch (_: Exception) {
        MessageRole.USER
      }
    val parsedStatus =
      try {
        val rawStatus = MessageStatus.valueOf(status)
        if (rawStatus == MessageStatus.STREAMING || rawStatus == MessageStatus.SENDING) {
          MessageStatus.INTERRUPTED
        } else {
          rawStatus
        }
      } catch (_: Exception) {
        MessageStatus.SUCCESS
      }
    val resolvedError =
      if (parsedStatus == MessageStatus.INTERRUPTED && errorMessage.isNullOrBlank()) {
        "Response interrupted before completion"
      } else {
        errorMessage
      }
    return ChatMessage(
      id = id,
      role = parsedRole,
      content = content,
      timestamp = if (timestamp > 0L) timestamp else System.currentTimeMillis(),
      status = parsedStatus,
      errorMessage = resolvedError,
      isLiked = isLiked,
      isBookmarked = isBookmarked,
      attachments = parsedAttachments,
      citations = parsedCitations,
    )
  }
}
