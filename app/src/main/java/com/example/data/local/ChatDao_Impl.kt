package com.example.data.local

import android.annotation.SuppressLint
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.appendPlaceholders
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@SuppressLint("RestrictedApi")
@Suppress("UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL")
public class ChatDao_Impl(
  __db: RoomDatabase,
) : ChatDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfConversationEntity: EntityInsertAdapter<ConversationEntity>

  private val __insertAdapterOfMessageEntity: EntityInsertAdapter<MessageEntity>

  init {
    this.__db = __db
    this.__insertAdapterOfConversationEntity =
      object : EntityInsertAdapter<ConversationEntity>() {
        protected override fun createQuery(): String =
          "INSERT OR IGNORE INTO `conversations` (`id`,`title`,`preview`,`createdAt`,`updatedAt`,`isPinned`,`isLiked`) VALUES (?,?,?,?,?,?,?)"

        protected override fun bind(statement: SQLiteStatement, entity: ConversationEntity) {
          statement.bindText(1, entity.id)
          statement.bindText(2, entity.title)
          statement.bindText(3, entity.preview)
          statement.bindLong(4, entity.createdAt)
          statement.bindLong(5, entity.updatedAt)
          val _tmp: Int = if (entity.isPinned) 1 else 0
          statement.bindLong(6, _tmp.toLong())
          val _tmp_1: Int = if (entity.isLiked) 1 else 0
          statement.bindLong(7, _tmp_1.toLong())
        }
      }
    this.__insertAdapterOfMessageEntity =
      object : EntityInsertAdapter<MessageEntity>() {
        protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `messages` (`id`,`conversationId`,`role`,`content`,`timestamp`,`status`,`errorMessage`,`attachmentsJson`,`citationsJson`,`isLiked`,`isBookmarked`) VALUES (?,?,?,?,?,?,?,?,?,?,?)"

        protected override fun bind(statement: SQLiteStatement, entity: MessageEntity) {
          statement.bindText(1, entity.id)
          statement.bindText(2, entity.conversationId)
          statement.bindText(3, entity.role)
          statement.bindText(4, entity.content)
          statement.bindLong(5, entity.timestamp)
          statement.bindText(6, entity.status)
          val _tmpErrorMessage: String? = entity.errorMessage
          if (_tmpErrorMessage == null) {
            statement.bindNull(7)
          } else {
            statement.bindText(7, _tmpErrorMessage)
          }
          val _tmpAttachmentsJson: String? = entity.attachmentsJson
          if (_tmpAttachmentsJson == null) {
            statement.bindNull(8)
          } else {
            statement.bindText(8, _tmpAttachmentsJson)
          }
          val _tmpCitationsJson: String? = entity.citationsJson
          if (_tmpCitationsJson == null) {
            statement.bindNull(9)
          } else {
            statement.bindText(9, _tmpCitationsJson)
          }
          val _tmp: Int = if (entity.isLiked) 1 else 0
          statement.bindLong(10, _tmp.toLong())
          val _tmp_1: Int = if (entity.isBookmarked) 1 else 0
          statement.bindLong(11, _tmp_1.toLong())
        }
      }
  }

  public override suspend fun insertConversationIfAbsent(conversation: ConversationEntity): Long =
    performSuspending(__db, false, true) { _connection ->
      val _result: Long =
        __insertAdapterOfConversationEntity.insertAndReturnId(_connection, conversation)
      _result
    }

  public override suspend fun upsertMessage(message: MessageEntity): Unit =
    performSuspending(__db, false, true) { _connection ->
      __insertAdapterOfMessageEntity.insert(_connection, message)
    }

  public override fun observeAllConversations(): Flow<List<ConversationEntity>> {
    val _sql: String = "SELECT * FROM conversations ORDER BY isPinned DESC, updatedAt DESC"
    return createFlow(__db, false, arrayOf("conversations")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfPreview: Int = getColumnIndexOrThrow(_stmt, "preview")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updatedAt")
        val _columnIndexOfIsPinned: Int = getColumnIndexOrThrow(_stmt, "isPinned")
        val _columnIndexOfIsLiked: Int = getColumnIndexOrThrow(_stmt, "isLiked")
        val _result: MutableList<ConversationEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: ConversationEntity
          val _tmpId: String = _stmt.getText(_columnIndexOfId)
          val _tmpTitle: String = _stmt.getText(_columnIndexOfTitle)
          val _tmpPreview: String = _stmt.getText(_columnIndexOfPreview)
          val _tmpCreatedAt: Long = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmp: Int = _stmt.getLong(_columnIndexOfIsPinned).toInt()
          val _tmpIsPinned: Boolean = _tmp != 0
          val _tmp_1: Int = _stmt.getLong(_columnIndexOfIsLiked).toInt()
          val _tmpIsLiked: Boolean = _tmp_1 != 0
          _item =
            ConversationEntity(
              _tmpId,
              _tmpTitle,
              _tmpPreview,
              _tmpCreatedAt,
              _tmpUpdatedAt,
              _tmpIsPinned,
              _tmpIsLiked,
            )
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getAllConversations(): List<ConversationEntity> {
    val _sql: String = "SELECT * FROM conversations ORDER BY isPinned DESC, updatedAt DESC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfPreview: Int = getColumnIndexOrThrow(_stmt, "preview")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updatedAt")
        val _columnIndexOfIsPinned: Int = getColumnIndexOrThrow(_stmt, "isPinned")
        val _columnIndexOfIsLiked: Int = getColumnIndexOrThrow(_stmt, "isLiked")
        val _result: MutableList<ConversationEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: ConversationEntity
          val _tmpId: String = _stmt.getText(_columnIndexOfId)
          val _tmpTitle: String = _stmt.getText(_columnIndexOfTitle)
          val _tmpPreview: String = _stmt.getText(_columnIndexOfPreview)
          val _tmpCreatedAt: Long = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmp: Int = _stmt.getLong(_columnIndexOfIsPinned).toInt()
          val _tmpIsPinned: Boolean = _tmp != 0
          val _tmp_1: Int = _stmt.getLong(_columnIndexOfIsLiked).toInt()
          val _tmpIsLiked: Boolean = _tmp_1 != 0
          _item =
            ConversationEntity(
              _tmpId,
              _tmpTitle,
              _tmpPreview,
              _tmpCreatedAt,
              _tmpUpdatedAt,
              _tmpIsPinned,
              _tmpIsLiked,
            )
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getConversationById(id: String): ConversationEntity? {
    val _sql: String = "SELECT * FROM conversations WHERE id = ? LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfPreview: Int = getColumnIndexOrThrow(_stmt, "preview")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updatedAt")
        val _columnIndexOfIsPinned: Int = getColumnIndexOrThrow(_stmt, "isPinned")
        val _columnIndexOfIsLiked: Int = getColumnIndexOrThrow(_stmt, "isLiked")
        val _result: ConversationEntity?
        if (_stmt.step()) {
          val _tmpId: String = _stmt.getText(_columnIndexOfId)
          val _tmpTitle: String = _stmt.getText(_columnIndexOfTitle)
          val _tmpPreview: String = _stmt.getText(_columnIndexOfPreview)
          val _tmpCreatedAt: Long = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmp: Int = _stmt.getLong(_columnIndexOfIsPinned).toInt()
          val _tmpIsPinned: Boolean = _tmp != 0
          val _tmp_1: Int = _stmt.getLong(_columnIndexOfIsLiked).toInt()
          val _tmpIsLiked: Boolean = _tmp_1 != 0
          _result =
            ConversationEntity(
              _tmpId,
              _tmpTitle,
              _tmpPreview,
              _tmpCreatedAt,
              _tmpUpdatedAt,
              _tmpIsPinned,
              _tmpIsLiked,
            )
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeMessagesForConversation(
    conversationId: String
  ): Flow<List<MessageEntity>> {
    val _sql: String = "SELECT * FROM messages WHERE conversationId = ? ORDER BY timestamp ASC"
    return createFlow(__db, false, arrayOf("messages")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, conversationId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfConversationId: Int = getColumnIndexOrThrow(_stmt, "conversationId")
        val _columnIndexOfRole: Int = getColumnIndexOrThrow(_stmt, "role")
        val _columnIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfErrorMessage: Int = getColumnIndexOrThrow(_stmt, "errorMessage")
        val _columnIndexOfAttachmentsJson: Int = getColumnIndexOrThrow(_stmt, "attachmentsJson")
        val _columnIndexOfCitationsJson: Int = getColumnIndexOrThrow(_stmt, "citationsJson")
        val _columnIndexOfIsLiked: Int = getColumnIndexOrThrow(_stmt, "isLiked")
        val _columnIndexOfIsBookmarked: Int = getColumnIndexOrThrow(_stmt, "isBookmarked")
        val _result: MutableList<MessageEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: MessageEntity
          val _tmpId: String = _stmt.getText(_columnIndexOfId)
          val _tmpConversationId: String = _stmt.getText(_columnIndexOfConversationId)
          val _tmpRole: String = _stmt.getText(_columnIndexOfRole)
          val _tmpContent: String = _stmt.getText(_columnIndexOfContent)
          val _tmpTimestamp: Long = _stmt.getLong(_columnIndexOfTimestamp)
          val _tmpStatus: String = _stmt.getText(_columnIndexOfStatus)
          val _tmpErrorMessage: String? =
            if (_stmt.isNull(_columnIndexOfErrorMessage)) null
            else _stmt.getText(_columnIndexOfErrorMessage)
          val _tmpAttachmentsJson: String? =
            if (_stmt.isNull(_columnIndexOfAttachmentsJson)) null
            else _stmt.getText(_columnIndexOfAttachmentsJson)
          val _tmpCitationsJson: String? =
            if (_stmt.isNull(_columnIndexOfCitationsJson)) null
            else _stmt.getText(_columnIndexOfCitationsJson)
          val _tmp: Int = _stmt.getLong(_columnIndexOfIsLiked).toInt()
          val _tmpIsLiked: Boolean = _tmp != 0
          val _tmp_1: Int = _stmt.getLong(_columnIndexOfIsBookmarked).toInt()
          val _tmpIsBookmarked: Boolean = _tmp_1 != 0
          _item =
            MessageEntity(
              _tmpId,
              _tmpConversationId,
              _tmpRole,
              _tmpContent,
              _tmpTimestamp,
              _tmpStatus,
              _tmpErrorMessage,
              _tmpAttachmentsJson,
              _tmpCitationsJson,
              _tmpIsLiked,
              _tmpIsBookmarked,
            )
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getMessagesForConversation(
    conversationId: String
  ): List<MessageEntity> {
    val _sql: String = "SELECT * FROM messages WHERE conversationId = ? ORDER BY timestamp ASC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, conversationId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfConversationId: Int = getColumnIndexOrThrow(_stmt, "conversationId")
        val _columnIndexOfRole: Int = getColumnIndexOrThrow(_stmt, "role")
        val _columnIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfErrorMessage: Int = getColumnIndexOrThrow(_stmt, "errorMessage")
        val _columnIndexOfAttachmentsJson: Int = getColumnIndexOrThrow(_stmt, "attachmentsJson")
        val _columnIndexOfCitationsJson: Int = getColumnIndexOrThrow(_stmt, "citationsJson")
        val _columnIndexOfIsLiked: Int = getColumnIndexOrThrow(_stmt, "isLiked")
        val _columnIndexOfIsBookmarked: Int = getColumnIndexOrThrow(_stmt, "isBookmarked")
        val _result: MutableList<MessageEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: MessageEntity
          val _tmpId: String = _stmt.getText(_columnIndexOfId)
          val _tmpConversationId: String = _stmt.getText(_columnIndexOfConversationId)
          val _tmpRole: String = _stmt.getText(_columnIndexOfRole)
          val _tmpContent: String = _stmt.getText(_columnIndexOfContent)
          val _tmpTimestamp: Long = _stmt.getLong(_columnIndexOfTimestamp)
          val _tmpStatus: String = _stmt.getText(_columnIndexOfStatus)
          val _tmpErrorMessage: String? =
            if (_stmt.isNull(_columnIndexOfErrorMessage)) null
            else _stmt.getText(_columnIndexOfErrorMessage)
          val _tmpAttachmentsJson: String? =
            if (_stmt.isNull(_columnIndexOfAttachmentsJson)) null
            else _stmt.getText(_columnIndexOfAttachmentsJson)
          val _tmpCitationsJson: String? =
            if (_stmt.isNull(_columnIndexOfCitationsJson)) null
            else _stmt.getText(_columnIndexOfCitationsJson)
          val _tmp: Int = _stmt.getLong(_columnIndexOfIsLiked).toInt()
          val _tmpIsLiked: Boolean = _tmp != 0
          val _tmp_1: Int = _stmt.getLong(_columnIndexOfIsBookmarked).toInt()
          val _tmpIsBookmarked: Boolean = _tmp_1 != 0
          _item =
            MessageEntity(
              _tmpId,
              _tmpConversationId,
              _tmpRole,
              _tmpContent,
              _tmpTimestamp,
              _tmpStatus,
              _tmpErrorMessage,
              _tmpAttachmentsJson,
              _tmpCitationsJson,
              _tmpIsLiked,
              _tmpIsBookmarked,
            )
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getAllMessages(): List<MessageEntity> {
    val _sql: String = "SELECT * FROM messages ORDER BY timestamp ASC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfConversationId: Int = getColumnIndexOrThrow(_stmt, "conversationId")
        val _columnIndexOfRole: Int = getColumnIndexOrThrow(_stmt, "role")
        val _columnIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfErrorMessage: Int = getColumnIndexOrThrow(_stmt, "errorMessage")
        val _columnIndexOfAttachmentsJson: Int = getColumnIndexOrThrow(_stmt, "attachmentsJson")
        val _columnIndexOfCitationsJson: Int = getColumnIndexOrThrow(_stmt, "citationsJson")
        val _columnIndexOfIsLiked: Int = getColumnIndexOrThrow(_stmt, "isLiked")
        val _columnIndexOfIsBookmarked: Int = getColumnIndexOrThrow(_stmt, "isBookmarked")
        val _result: MutableList<MessageEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: MessageEntity
          val _tmpId: String = _stmt.getText(_columnIndexOfId)
          val _tmpConversationId: String = _stmt.getText(_columnIndexOfConversationId)
          val _tmpRole: String = _stmt.getText(_columnIndexOfRole)
          val _tmpContent: String = _stmt.getText(_columnIndexOfContent)
          val _tmpTimestamp: Long = _stmt.getLong(_columnIndexOfTimestamp)
          val _tmpStatus: String = _stmt.getText(_columnIndexOfStatus)
          val _tmpErrorMessage: String? =
            if (_stmt.isNull(_columnIndexOfErrorMessage)) null
            else _stmt.getText(_columnIndexOfErrorMessage)
          val _tmpAttachmentsJson: String? =
            if (_stmt.isNull(_columnIndexOfAttachmentsJson)) null
            else _stmt.getText(_columnIndexOfAttachmentsJson)
          val _tmpCitationsJson: String? =
            if (_stmt.isNull(_columnIndexOfCitationsJson)) null
            else _stmt.getText(_columnIndexOfCitationsJson)
          val _tmp: Int = _stmt.getLong(_columnIndexOfIsLiked).toInt()
          val _tmpIsLiked: Boolean = _tmp != 0
          val _tmp_1: Int = _stmt.getLong(_columnIndexOfIsBookmarked).toInt()
          val _tmpIsBookmarked: Boolean = _tmp_1 != 0
          _item =
            MessageEntity(
              _tmpId,
              _tmpConversationId,
              _tmpRole,
              _tmpContent,
              _tmpTimestamp,
              _tmpStatus,
              _tmpErrorMessage,
              _tmpAttachmentsJson,
              _tmpCitationsJson,
              _tmpIsLiked,
              _tmpIsBookmarked,
            )
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun searchConversationsByTitleOrContent(
    query: String
  ): List<ConversationEntity> {
    val _sql: String =
      """
        |
        |    SELECT DISTINCT c.* FROM conversations c
        |    LEFT JOIN messages m ON c.id = m.conversationId
        |    WHERE c.title LIKE '%' || ? || '%'
        |       OR c.preview LIKE '%' || ? || '%'
        |       OR m.content LIKE '%' || ? || '%'
        |    ORDER BY c.isPinned DESC, c.updatedAt DESC
        |    
        """
        .trimMargin()
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, query)
        _argIndex = 2
        _stmt.bindText(_argIndex, query)
        _argIndex = 3
        _stmt.bindText(_argIndex, query)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfPreview: Int = getColumnIndexOrThrow(_stmt, "preview")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updatedAt")
        val _columnIndexOfIsPinned: Int = getColumnIndexOrThrow(_stmt, "isPinned")
        val _columnIndexOfIsLiked: Int = getColumnIndexOrThrow(_stmt, "isLiked")
        val _result: MutableList<ConversationEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: ConversationEntity
          val _tmpId: String = _stmt.getText(_columnIndexOfId)
          val _tmpTitle: String = _stmt.getText(_columnIndexOfTitle)
          val _tmpPreview: String = _stmt.getText(_columnIndexOfPreview)
          val _tmpCreatedAt: Long = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long = _stmt.getLong(_columnIndexOfUpdatedAt)
          val _tmp: Int = _stmt.getLong(_columnIndexOfIsPinned).toInt()
          val _tmpIsPinned: Boolean = _tmp != 0
          val _tmp_1: Int = _stmt.getLong(_columnIndexOfIsLiked).toInt()
          val _tmpIsLiked: Boolean = _tmp_1 != 0
          _item =
            ConversationEntity(
              _tmpId,
              _tmpTitle,
              _tmpPreview,
              _tmpCreatedAt,
              _tmpUpdatedAt,
              _tmpIsPinned,
              _tmpIsLiked,
            )
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateConversationMetadata(
    id: String,
    title: String,
    preview: String,
    updatedAt: Long,
  ) {
    val _sql: String =
      "UPDATE conversations SET title = ?, preview = ?, updatedAt = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, title)
        _argIndex = 2
        _stmt.bindText(_argIndex, preview)
        _argIndex = 3
        _stmt.bindLong(_argIndex, updatedAt)
        _argIndex = 4
        _stmt.bindText(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun renameConversation(
    id: String,
    title: String,
    updatedAt: Long,
  ) {
    val _sql: String = "UPDATE conversations SET title = ?, updatedAt = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, title)
        _argIndex = 2
        _stmt.bindLong(_argIndex, updatedAt)
        _argIndex = 3
        _stmt.bindText(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateConversationPinned(
    id: String,
    isPinned: Boolean,
    updatedAt: Long,
  ) {
    val _sql: String = "UPDATE conversations SET isPinned = ?, updatedAt = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        val _tmp: Int = if (isPinned) 1 else 0
        _stmt.bindLong(_argIndex, _tmp.toLong())
        _argIndex = 2
        _stmt.bindLong(_argIndex, updatedAt)
        _argIndex = 3
        _stmt.bindText(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateConversationLiked(
    id: String,
    isLiked: Boolean,
    updatedAt: Long,
  ) {
    val _sql: String = "UPDATE conversations SET isLiked = ?, updatedAt = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        val _tmp: Int = if (isLiked) 1 else 0
        _stmt.bindLong(_argIndex, _tmp.toLong())
        _argIndex = 2
        _stmt.bindLong(_argIndex, updatedAt)
        _argIndex = 3
        _stmt.bindText(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteConversationById(id: String) {
    val _sql: String = "DELETE FROM conversations WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateMessageLiked(messageId: String, isLiked: Boolean) {
    val _sql: String = "UPDATE messages SET isLiked = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        val _tmp: Int = if (isLiked) 1 else 0
        _stmt.bindLong(_argIndex, _tmp.toLong())
        _argIndex = 2
        _stmt.bindText(_argIndex, messageId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateMessageBookmarked(messageId: String, isBookmarked: Boolean) {
    val _sql: String = "UPDATE messages SET isBookmarked = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        val _tmp: Int = if (isBookmarked) 1 else 0
        _stmt.bindLong(_argIndex, _tmp.toLong())
        _argIndex = 2
        _stmt.bindText(_argIndex, messageId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteMessagesByIds(
    conversationId: String,
    messageIds: List<String>,
  ) {
    val _stringBuilder: StringBuilder = StringBuilder()
    _stringBuilder.append("DELETE FROM messages WHERE conversationId = ")
    _stringBuilder.append("?")
    _stringBuilder.append(" AND id IN (")
    val _inputSize: Int = messageIds.size
    appendPlaceholders(_stringBuilder, _inputSize)
    _stringBuilder.append(")")
    val _sql: String = _stringBuilder.toString()
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, conversationId)
        _argIndex = 2
        for (_item: String in messageIds) {
          _stmt.bindText(_argIndex, _item)
          _argIndex++
        }
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteAllMessagesForConversation(conversationId: String) {
    val _sql: String = "DELETE FROM messages WHERE conversationId = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, conversationId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteAllMessages() {
    val _sql: String = "DELETE FROM messages"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteAllConversations() {
    val _sql: String = "DELETE FROM conversations"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
