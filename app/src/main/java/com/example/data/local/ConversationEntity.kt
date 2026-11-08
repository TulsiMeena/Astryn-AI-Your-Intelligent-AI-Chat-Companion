package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room SQLite entity representing a saved conversation metadata record.
 */
@Entity(tableName = "conversations")
data class ConversationEntity(
  @PrimaryKey val id: String,
  val title: String,
  val preview: String,
  val createdAt: Long,
  val updatedAt: Long,
  val isPinned: Boolean = false,
  val isLiked: Boolean = false,
)
