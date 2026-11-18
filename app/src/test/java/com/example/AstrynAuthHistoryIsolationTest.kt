package com.example

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.api.FirebaseRestAuthClient
import com.example.data.local.ConversationEntity
import com.example.data.local.NovaChatDatabase
import com.example.data.local.UserApiKeyManager
import com.example.data.model.ChatMessage
import com.example.data.model.MessageRole
import com.example.data.model.MessageStatus
import com.example.data.repository.ChatHistoryRepository
import com.example.data.repository.UserPreferencesRepository
import com.example.ui.screens.auth.AuthScreen
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AstrynAuthHistoryIsolationTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun testRoomMigration4To5PreservesExistingDataAndSupportsOwnerUid() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dbName = "migration_test_${System.currentTimeMillis()}.db"
    context.deleteDatabase(dbName)

    val callback =
      object : SupportSQLiteOpenHelper.Callback(4) {
        override fun onCreate(db: SupportSQLiteDatabase) {
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `conversations` (
              `id` TEXT NOT NULL,
              `title` TEXT NOT NULL,
              `preview` TEXT NOT NULL,
              `createdAt` INTEGER NOT NULL,
              `updatedAt` INTEGER NOT NULL,
              `isPinned` INTEGER NOT NULL DEFAULT 0,
              `isLiked` INTEGER NOT NULL DEFAULT 0,
              PRIMARY KEY(`id`)
            )
            """.trimIndent()
          )
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `messages` (
              `id` TEXT NOT NULL,
              `conversationId` TEXT NOT NULL,
              `role` TEXT NOT NULL,
              `content` TEXT NOT NULL,
              `timestamp` INTEGER NOT NULL,
              `status` TEXT NOT NULL,
              `errorMessage` TEXT,
              `attachmentsJson` TEXT NOT NULL DEFAULT '[]',
              `citationsJson` TEXT NOT NULL DEFAULT '[]',
              `isLiked` INTEGER NOT NULL DEFAULT 0,
              `isBookmarked` INTEGER NOT NULL DEFAULT 0,
              PRIMARY KEY(`id`)
            )
            """.trimIndent()
          )
          db.execSQL(
            "INSERT INTO conversations (id, title, preview, createdAt, updatedAt, isPinned, isLiked) VALUES ('conv_v4', 'Legacy V4 Chat', 'Hello from v4', 100, 200, 1, 0)"
          )
        }

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
      }

    val helper =
      FrameworkSQLiteOpenHelperFactory()
        .create(
          SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(callback)
            .build()
        )

    val writable = helper.writableDatabase
    NovaChatDatabase.MIGRATION_4_5.migrate(writable)

    var hasOwnerUidColumn = false
    writable.query("PRAGMA table_info(conversations)").use { cursor ->
      val nameIdx = cursor.getColumnIndex("name")
      while (cursor.moveToNext()) {
        if (nameIdx >= 0 && cursor.getString(nameIdx) == "ownerUid") {
          hasOwnerUidColumn = true
        }
      }
    }
    assertTrue("MIGRATION_4_5 must add ownerUid column", hasOwnerUidColumn)

    writable.query("SELECT id, title, ownerUid FROM conversations WHERE id = 'conv_v4'").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals("Legacy V4 Chat", cursor.getString(1))
      assertEquals("", cursor.getString(2))
    }
    helper.close()
    context.deleteDatabase(dbName)

    // Verify Room v5 DAO operations & per-owner SQL migration
    val roomDb = NovaChatDatabase.createInMemory(context)
    val dao = roomDb.chatDao()
    val aliasUid = FirebaseRestAuthClient.fallbackUidForEmail("alice@gmail.com")
    dao.insertConversationIfAbsent(
      ConversationEntity(
        id = "conv_alias_1",
        title = "Alice Offline Chat",
        preview = "Before online token sync",
        createdAt = 1000L,
        updatedAt = 2000L,
        ownerUid = aliasUid,
      )
    )
    dao.insertConversationIfAbsent(
      ConversationEntity(
        id = "conv_bob_1",
        title = "Bob Private Chat",
        preview = "Secret Bob notes",
        createdAt = 1000L,
        updatedAt = 2500L,
        ownerUid = "uid_bob_canonical",
      )
    )

    dao.migrateConversationOwner(oldOwnerUid = aliasUid, newOwnerUid = "uid_alice_canonical")

    assertEquals("uid_alice_canonical", dao.getConversationById("conv_alias_1")?.ownerUid)
    assertEquals("uid_bob_canonical", dao.getConversationById("conv_bob_1")?.ownerUid)
    roomDb.close()
  }

  @Test
  fun testMultiAccountChatHistoryPersistsAcrossGoogleReLoginsAndRemainsIsolated() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = context.getSharedPreferences("auth_isolation_test_prefs", Context.MODE_PRIVATE)
    prefs.edit().clear().commit()
    val ownerPrefs = context.getSharedPreferences("astryn_conversation_owners", Context.MODE_PRIVATE)
    ownerPrefs.edit().clear().commit()

    val prefsRepo = UserPreferencesRepository(sharedPreferences = prefs)
    val db = NovaChatDatabase.createInMemory(context)
    val repo = ChatHistoryRepository(chatDao = db.chatDao(), ioDispatcher = Dispatchers.Unconfined)

    val aliceEmail = "alice.astryn@gmail.com"
    val aliceUid = "firebase_uid_alice_123"
    val aliceAlias = FirebaseRestAuthClient.fallbackUidForEmail(aliceEmail)
    val bobEmail = "bob.astryn@gmail.com"
    val bobUid = "firebase_uid_bob_456"

    // 1. Alice creates a chat offline under her fallback alias, then logs in with her canonical Google UID
    repo.setActiveUser(uid = aliceAlias, idToken = "", context = context)
    val convAliceAlias = "${aliceAlias}_chat_1"
    repo.saveMessage(
      convAliceAlias,
      ChatMessage(
        id = "m_a1",
        role = MessageRole.USER,
        content = "Alice's offline question that must survive login sync",
        timestamp = 1000L,
        status = MessageStatus.SUCCESS,
      ),
    )

    prefsRepo.signInUser(
      name = "Alice Astryn",
      email = aliceEmail,
      provider = "Google",
      uid = aliceUid,
      idToken = "id_token_alice",
      refreshToken = "refresh_token_alice",
    )
    repo.setActiveUser(
      uid = aliceUid,
      idToken = "id_token_alice",
      context = context,
      aliasUidsToMigrate = setOf(aliceAlias),
    )

    // Also save a second chat directly under Alice's canonical Google UID
    val convAliceMain = "${aliceUid}_chat_2"
    repo.saveMessage(
      convAliceMain,
      ChatMessage(
        id = "m_a2",
        role = MessageRole.USER,
        content = "Alice's second private prompt",
        timestamp = 2000L,
        status = MessageStatus.SUCCESS,
      ),
    )
    assertEquals(2, repo.conversationsFlow.value.size)

    // 2. Alice signs out -> knownAccounts preserves Alice's identity & canonical UID, active list is empty
    prefsRepo.signOutUser()
    repo.setActiveUser(uid = "__signed_out__", idToken = "", context = context)
    assertFalse(prefsRepo.preferencesFlow.value.isLoggedIn)
    assertEquals(0, repo.conversationsFlow.value.size)
    val reloadedPrefsRepo = UserPreferencesRepository(sharedPreferences = prefs)
    assertEquals(aliceUid, reloadedPrefsRepo.getCanonicalUidForEmail(aliceEmail))
    assertEquals("refresh_token_alice", reloadedPrefsRepo.getRefreshTokenForEmail(aliceEmail))

    // 3. Bob signs in -> must see ZERO of Alice's conversations
    prefsRepo.signInUser(
      name = "Bob Astryn",
      email = bobEmail,
      provider = "Google",
      uid = bobUid,
      idToken = "id_token_bob",
      refreshToken = "refresh_token_bob",
    )
    repo.setActiveUser(
      uid = bobUid,
      idToken = "id_token_bob",
      context = context,
      aliasUidsToMigrate = setOf(FirebaseRestAuthClient.fallbackUidForEmail(bobEmail)),
    )
    assertEquals(0, repo.conversationsFlow.value.size)
    assertTrue(repo.getMessagesForConversation(convAliceMain).isEmpty())
    assertTrue(repo.renameConversation(convAliceMain, "Hijacked").isFailure)

    // Bob creates his own conversation
    val convBob = "${bobUid}_chat_1"
    repo.saveMessage(
      convBob,
      ChatMessage(
        id = "m_b1",
        role = MessageRole.USER,
        content = "Bob's private notes",
        timestamp = 3000L,
        status = MessageStatus.SUCCESS,
      ),
    )
    assertEquals(1, repo.conversationsFlow.value.size)

    // 4. Bob signs out; simulate full app restart and Alice signs back in via Google
    prefsRepo.signOutUser()
    val restartedRepo =
      ChatHistoryRepository(chatDao = db.chatDao(), ioDispatcher = Dispatchers.Unconfined)
    restartedRepo.setActiveUser(
      uid = aliceUid,
      idToken = "id_token_alice_new",
      context = context,
      aliasUidsToMigrate = setOf(aliceAlias),
    )

    val aliceRestoredIds = restartedRepo.conversationsFlow.value.map { it.id }.toSet()
    assertEquals(setOf(convAliceAlias, convAliceMain), aliceRestoredIds)
    assertFalse("Alice must never see Bob's conversation", aliceRestoredIds.contains(convBob))
    assertTrue(restartedRepo.getMessagesForConversation(convBob).isEmpty())
    assertEquals(
      "Alice's second private prompt",
      restartedRepo.getMessagesForConversation(convAliceMain).first().content,
    )

    // 5. Settings reset preserves logged-in user and known accounts; Delete Account removes only current user
    prefsRepo.signInUser(
      name = "Alice Astryn",
      email = aliceEmail,
      provider = "Google",
      uid = aliceUid,
      idToken = "id_token_alice_new",
      refreshToken = "refresh_token_alice",
    )
    prefsRepo.resetToDefaults()
    assertTrue(prefsRepo.preferencesFlow.value.isLoggedIn)
    assertEquals(2, prefsRepo.preferencesFlow.value.knownAccounts.size)

    prefsRepo.deleteUserAccount()
    assertNull(prefsRepo.getCanonicalUidForEmail(aliceEmail))
    assertEquals(bobUid, prefsRepo.getCanonicalUidForEmail(bobEmail))

    // 6. Per-user API key isolation check
    val keyManager = UserApiKeyManager(sharedPreferences = null)
    keyManager.bindToUser(aliceUid)
    assertTrue(keyManager.saveApiKey("AIzaSyAliceTestKey1234567890abcdefg1234").isSuccess)
    keyManager.bindToUser(bobUid)
    assertFalse(keyManager.hasSavedKey())

    db.close()
  }

  @Test
  fun testVerificationPayloadUsesAstrynContinueUrl() {
    val payload = FirebaseRestAuthClient.buildVerificationEmailPayloadJson("sample_id_token")
    assertEquals("VERIFY_EMAIL", payload.getString("requestType"))
    assertEquals("sample_id_token", payload.getString("idToken"))
    val continueUrl = payload.getString("continueUrl")
    assertTrue(continueUrl.contains(FirebaseRestAuthClient.AUTH_DOMAIN))
    assertTrue(continueUrl.contains("Astryn+AI"))
    assertFalse(payload.getBoolean("canHandleCodeInApp"))
  }

  @Test
  fun testAuthScreenGooglePickerShowsAllKnownAccountsWithoutHardcodedEmail() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = context.getSharedPreferences("auth_ui_multi_account_prefs", Context.MODE_PRIVATE)
    prefs.edit().clear().commit()
    val prefsRepo = UserPreferencesRepository(sharedPreferences = prefs)

    prefsRepo.registerKnownAccount(
      email = "one.user@gmail.com",
      displayName = "One User",
      canonicalUid = "uid_one",
      provider = "Google",
    )
    prefsRepo.registerKnownAccount(
      email = "two.user@gmail.com",
      displayName = "Two User",
      canonicalUid = "uid_two",
      provider = "Google",
    )
    prefsRepo.registerKnownAccount(
      email = "three.user@gmail.com",
      displayName = "Three User",
      canonicalUid = "uid_three",
      provider = "Google",
    )

    composeTestRule.setContent {
      MyApplicationTheme(themeMode = AppThemeMode.DARK) {
        AuthScreen(
          preferencesRepository = prefsRepo,
          initialIsSignUp = false,
          onAuthComplete = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("auth_google_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    // In Robolectric (without GMS chooser activity), fallback chooser opens displaying all 3 accounts
    composeTestRule.onNodeWithTag("auth_google_account_dialog").assertExists()
    composeTestRule.onNodeWithTag("auth_google_quick_account_0").assertExists()
    composeTestRule.onNodeWithTag("auth_google_quick_account_1").assertExists()
    composeTestRule.onNodeWithTag("auth_google_quick_account_2").assertExists()
    composeTestRule.onNodeWithText("one.user@gmail.com").assertExists()
    composeTestRule.onNodeWithText("two.user@gmail.com").assertExists()
    composeTestRule.onNodeWithText("three.user@gmail.com").assertExists()
    composeTestRule.onNodeWithText("am7620711@gmail.com").assertDoesNotExist()
  }
}
