package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room SQLite entity representing a single message belonging to a conversation.
 * Configured with ForeignKey CASCADE so deleting a conversation removes all its messages.
 */
@Entity(
  tableName = "messages",
  foreignKeys = [
    ForeignKey(
      entity = ConversationEntity::class,
      parentColumns = ["id"],
      childColumns = ["conversationId"],
      onDelete = ForeignKey.CASCADE,
    )
  ],
  indices = [Index(value = ["conversationId"])],
)
data class MessageEntity(
  @PrimaryKey val id: String,
  val conversationId: String,
  val role: String,
  val content: String,
  val timestamp: Long,
  val status: String,
  val errorMessage: String? = null,
  val attachmentsJson: String? = null,
  val citationsJson: String? = null,
  val isLiked: Boolean = false,
  val isBookmarked: Boolean = false,
)
