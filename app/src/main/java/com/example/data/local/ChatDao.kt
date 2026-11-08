package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object for conversations and messages.
 *
 * Uses INSERT OR IGNORE + UPDATE for parent [ConversationEntity] records so that
 * updating a conversation never triggers SQLite REPLACE (which would fire ON DELETE CASCADE
 * on child [MessageEntity] rows).
 */
@Dao
interface ChatDao {
  @Query("SELECT * FROM conversations ORDER BY isPinned DESC, updatedAt DESC")
  fun observeAllConversations(): Flow<List<ConversationEntity>>

  @Query("SELECT * FROM conversations ORDER BY isPinned DESC, updatedAt DESC")
  suspend fun getAllConversations(): List<ConversationEntity>

  @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
  suspend fun getConversationById(id: String): ConversationEntity?

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertConversationIfAbsent(conversation: ConversationEntity): Long

  @Query(
    "UPDATE conversations SET title = :title, preview = :preview, updatedAt = :updatedAt WHERE id = :id"
  )
  suspend fun updateConversationMetadata(
    id: String,
    title: String,
    preview: String,
    updatedAt: Long,
  )

  @Query("UPDATE conversations SET title = :title, updatedAt = :updatedAt WHERE id = :id")
  suspend fun renameConversation(id: String, title: String, updatedAt: Long)

  @Query("UPDATE conversations SET isPinned = :isPinned, updatedAt = :updatedAt WHERE id = :id")
  suspend fun updateConversationPinned(id: String, isPinned: Boolean, updatedAt: Long)

  @Query("UPDATE conversations SET isLiked = :isLiked, updatedAt = :updatedAt WHERE id = :id")
  suspend fun updateConversationLiked(id: String, isLiked: Boolean, updatedAt: Long)

  @Query("DELETE FROM conversations WHERE id = :id")
  suspend fun deleteConversationById(id: String)

  @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
  fun observeMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

  @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
  suspend fun getMessagesForConversation(conversationId: String): List<MessageEntity>

  @Query("SELECT * FROM messages ORDER BY timestamp ASC")
  suspend fun getAllMessages(): List<MessageEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertMessage(message: MessageEntity)

  @Query("UPDATE messages SET isLiked = :isLiked WHERE id = :messageId")
  suspend fun updateMessageLiked(messageId: String, isLiked: Boolean)

  @Query("UPDATE messages SET isBookmarked = :isBookmarked WHERE id = :messageId")
  suspend fun updateMessageBookmarked(messageId: String, isBookmarked: Boolean)

  @Query("DELETE FROM messages WHERE conversationId = :conversationId AND id IN (:messageIds)")
  suspend fun deleteMessagesByIds(conversationId: String, messageIds: List<String>)

  @Query("DELETE FROM messages WHERE conversationId = :conversationId")
  suspend fun deleteAllMessagesForConversation(conversationId: String)

  @Query("DELETE FROM messages")
  suspend fun deleteAllMessages()

  @Query("DELETE FROM conversations")
  suspend fun deleteAllConversations()

  @Query(
    """
    SELECT DISTINCT c.* FROM conversations c
    LEFT JOIN messages m ON c.id = m.conversationId
    WHERE c.title LIKE '%' || :query || '%'
       OR c.preview LIKE '%' || :query || '%'
       OR m.content LIKE '%' || :query || '%'
    ORDER BY c.isPinned DESC, c.updatedAt DESC
    """
  )
  suspend fun searchConversationsByTitleOrContent(query: String): List<ConversationEntity>
}
