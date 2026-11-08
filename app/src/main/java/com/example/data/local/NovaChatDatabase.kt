package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room SQLite Database for Astryn AI Chat history and message storage.
 * Includes non-destructive migration from v1 to v2 so existing user conversations are preserved.
 */
@Database(
  entities = [ConversationEntity::class, MessageEntity::class],
  version = 4,
  exportSchema = false,
)
abstract class NovaChatDatabase : RoomDatabase() {
  abstract fun chatDao(): ChatDao

  companion object {
    private const val DATABASE_NAME = "nova_chat_history.db"

    /**
     * Non-destructive migration from schema v1 to v2 adding attachment and citation columns
     * without deleting existing user conversations or messages.
     */
    val MIGRATION_1_2: Migration =
      object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE messages ADD COLUMN attachmentsJson TEXT")
          db.execSQL("ALTER TABLE messages ADD COLUMN citationsJson TEXT")
        }
      }

    /**
     * Non-destructive migration from schema v2 to v3 adding pin and like columns
     * to conversations and messages without deleting existing user history.
     */
    val MIGRATION_2_3: Migration =
      object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE conversations ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
          db.execSQL("ALTER TABLE conversations ADD COLUMN isLiked INTEGER NOT NULL DEFAULT 0")
          db.execSQL("ALTER TABLE messages ADD COLUMN isLiked INTEGER NOT NULL DEFAULT 0")
        }
      }

    /**
     * Non-destructive migration from schema v3 to v4 adding bookmark column to messages.
     */
    val MIGRATION_3_4: Migration =
      object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE messages ADD COLUMN isBookmarked INTEGER NOT NULL DEFAULT 0")
        }
      }

    @Volatile private var INSTANCE: NovaChatDatabase? = null

    fun getInstance(context: Context): NovaChatDatabase {
      return INSTANCE
        ?: synchronized(this) {
          INSTANCE
            ?: Room.databaseBuilder(
                context.applicationContext,
                NovaChatDatabase::class.java,
                DATABASE_NAME,
              )
              .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
              .fallbackToDestructiveMigration(dropAllTables = true)
              .build()
              .also { INSTANCE = it }
        }
    }

    /**
     * Creates an isolated in-memory Room database instance for unit/Robolectric testing.
     */
    fun createInMemory(context: Context): NovaChatDatabase {
      return Room.inMemoryDatabaseBuilder(
          context.applicationContext,
          NovaChatDatabase::class.java,
        )
        .allowMainThreadQueries()
        .build()
    }
  }
}
