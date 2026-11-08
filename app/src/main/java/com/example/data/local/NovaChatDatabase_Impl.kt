package com.example.data.local

import android.annotation.SuppressLint
import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import kotlin.reflect.KClass

@SuppressLint("RestrictedApi")
@Suppress("UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL")
public class NovaChatDatabase_Impl : NovaChatDatabase() {
  private val _chatDao: Lazy<ChatDao> = lazy {
    ChatDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate =
      object :
        RoomOpenDelegate(
          4,
          "61d4012509ef8b3a8ee8c6f833deb2f0",
          "954f94a3c1908de11f83abde5fe77be0",
        ) {
        public override fun createAllTables(connection: SQLiteConnection) {
          connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `conversations` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `preview` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `isPinned` INTEGER NOT NULL, `isLiked` INTEGER NOT NULL, PRIMARY KEY(`id`))"
          )
          connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `messages` (`id` TEXT NOT NULL, `conversationId` TEXT NOT NULL, `role` TEXT NOT NULL, `content` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `status` TEXT NOT NULL, `errorMessage` TEXT, `attachmentsJson` TEXT, `citationsJson` TEXT, `isLiked` INTEGER NOT NULL, `isBookmarked` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
          )
          connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_messages_conversationId` ON `messages` (`conversationId`)"
          )
          connection.execSQL(
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)"
          )
          connection.execSQL(
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '61d4012509ef8b3a8ee8c6f833deb2f0')"
          )
        }

        public override fun dropAllTables(connection: SQLiteConnection) {
          connection.execSQL("DROP TABLE IF EXISTS `conversations`")
          connection.execSQL("DROP TABLE IF EXISTS `messages`")
        }

        public override fun onCreate(connection: SQLiteConnection) {}

        public override fun onOpen(connection: SQLiteConnection) {
          connection.execSQL("PRAGMA foreign_keys = ON")
          internalInitInvalidationTracker(connection)
        }

        public override fun onPreMigrate(connection: SQLiteConnection) {
          dropFtsSyncTriggers(connection)
        }

        public override fun onPostMigrate(connection: SQLiteConnection) {}

        public override fun onValidateSchema(
          connection: SQLiteConnection
        ): RoomOpenDelegate.ValidationResult {
          val _columnsConversations: MutableMap<String, TableInfo.Column> = mutableMapOf()
          _columnsConversations.put(
            "id",
            TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY),
          )
          _columnsConversations.put(
            "title",
            TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY),
          )
          _columnsConversations.put(
            "preview",
            TableInfo.Column("preview", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY),
          )
          _columnsConversations.put(
            "createdAt",
            TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY),
          )
          _columnsConversations.put(
            "updatedAt",
            TableInfo.Column("updatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY),
          )
          _columnsConversations.put(
            "isPinned",
            TableInfo.Column("isPinned", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY),
          )
          _columnsConversations.put(
            "isLiked",
            TableInfo.Column("isLiked", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY),
          )
          val _foreignKeysConversations: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
          val _indicesConversations: MutableSet<TableInfo.Index> = mutableSetOf()
          val _infoConversations: TableInfo =
            TableInfo(
              "conversations",
              _columnsConversations,
              _foreignKeysConversations,
              _indicesConversations,
            )
          val _existingConversations: TableInfo = read(connection, "conversations")
          if (!_infoConversations.equals(_existingConversations)) {
            return RoomOpenDelegate.ValidationResult(
              false,
              """
              |conversations(com.example.data.local.ConversationEntity).
              | Expected:
              |"""
                .trimMargin() +
                _infoConversations +
                """
              |
              | Found:
              |"""
                  .trimMargin() +
                _existingConversations,
            )
          }
          val _columnsMessages: MutableMap<String, TableInfo.Column> = mutableMapOf()
          _columnsMessages.put(
            "id",
            TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY),
          )
          _columnsMessages.put(
            "conversationId",
            TableInfo.Column("conversationId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY),
          )
          _columnsMessages.put(
            "role",
            TableInfo.Column("role", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY),
          )
          _columnsMessages.put(
            "content",
            TableInfo.Column("content", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY),
          )
          _columnsMessages.put(
            "timestamp",
            TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY),
          )
          _columnsMessages.put(
            "status",
            TableInfo.Column("status", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY),
          )
          _columnsMessages.put(
            "errorMessage",
            TableInfo.Column("errorMessage", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY),
          )
          _columnsMessages.put(
            "attachmentsJson",
            TableInfo.Column(
              "attachmentsJson",
              "TEXT",
              false,
              0,
              null,
              TableInfo.CREATED_FROM_ENTITY,
            ),
          )
          _columnsMessages.put(
            "citationsJson",
            TableInfo.Column("citationsJson", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY),
          )
          _columnsMessages.put(
            "isLiked",
            TableInfo.Column("isLiked", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY),
          )
          _columnsMessages.put(
            "isBookmarked",
            TableInfo.Column("isBookmarked", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY),
          )
          val _foreignKeysMessages: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
          _foreignKeysMessages.add(
            TableInfo.ForeignKey(
              "conversations",
              "CASCADE",
              "NO ACTION",
              listOf("conversationId"),
              listOf("id"),
            )
          )
          val _indicesMessages: MutableSet<TableInfo.Index> = mutableSetOf()
          _indicesMessages.add(
            TableInfo.Index(
              "index_messages_conversationId",
              false,
              listOf("conversationId"),
              listOf("ASC"),
            )
          )
          val _infoMessages: TableInfo =
            TableInfo("messages", _columnsMessages, _foreignKeysMessages, _indicesMessages)
          val _existingMessages: TableInfo = read(connection, "messages")
          if (!_infoMessages.equals(_existingMessages)) {
            return RoomOpenDelegate.ValidationResult(
              false,
              """
              |messages(com.example.data.local.MessageEntity).
              | Expected:
              |"""
                .trimMargin() +
                _infoMessages +
                """
              |
              | Found:
              |"""
                  .trimMargin() +
                _existingMessages,
            )
          }
          return RoomOpenDelegate.ValidationResult(true, null)
        }
      }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "conversations", "messages")
  }

  public override fun clearAllTables() {
    super.performClear(true, "conversations", "messages")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(ChatDao::class, ChatDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override fun createAutoMigrations(
    autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>
  ): List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun chatDao(): ChatDao = _chatDao.value
}
