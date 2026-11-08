package com.example.ui.navigation

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.saveable.Saver

/**
 * Type-safe navigation screen destinations for Nova AI Chat.
 */
@Immutable
sealed interface AppScreen {
  /**
   * Primary Home destination featuring the AI Orb, welcome hero, quick composer,
   * and starter suggestion cards.
   */
  data object Home : AppScreen

  /**
   * Chat conversation destination.
   *
   * @param initialPrompt Optional pre-filled text passed from Home Screen suggestions or quick composer.
   * @param conversationId Optional existing conversation UUID to reopen.
   */
  data class Chat(
    val initialPrompt: String? = null,
    val conversationId: String? = null,
    val startLiveVoice: Boolean = false,
    val startVoiceMic: Boolean = false,
  ) : AppScreen

  /**
   * Explore destination for curated capability and prompt template cards.
   */
  data object Explore : AppScreen

  /**
   * Settings destination for theme, model preferences, and history controls.
   */
  data object Settings : AppScreen

  /**
   * Dedicated AI Provider & API Key configuration destination.
   */
  data object ApiKeyConfig : AppScreen

  /**
   * Dedicated User Profile & Account Security Center destination.
   */
  data object Profile : AppScreen

  /**
   * Premium Login & Sign Up authentication destination (Firebase-ready architecture).
   */
  data class Auth(val initialIsSignUp: Boolean = false) : AppScreen

  companion object {
    /**
     * Saver implementation enabling rememberSaveable to safely serialize and restore
     * the active navigation destination and conversation draft parameters across configuration
     * changes and process recreation.
     */
    val Saver: Saver<AppScreen, Any> =
      Saver(
        save = { screen ->
          when (screen) {
            is Home -> arrayListOf("HOME")
            is Chat ->
              arrayListOf(
                "CHAT",
                screen.initialPrompt ?: "",
                screen.conversationId ?: "",
                screen.startLiveVoice.toString(),
                screen.startVoiceMic.toString(),
              )
            is Explore -> arrayListOf("EXPLORE")
            is Settings -> arrayListOf("SETTINGS")
            is ApiKeyConfig -> arrayListOf("API_KEY_CONFIG")
            is Profile -> arrayListOf("PROFILE")
            is Auth -> arrayListOf("AUTH", screen.initialIsSignUp.toString())
          }
        },
        restore = { value ->
          val list = value as? List<*>
          when (list?.getOrNull(0) as? String) {
            "CHAT" -> {
              val prompt = (list.getOrNull(1) as? String)?.ifEmpty { null }
              val convId = (list.getOrNull(2) as? String)?.ifEmpty { null }
              val startLive = list.getOrNull(3) as? String == "true"
              val startMic = list.getOrNull(4) as? String == "true"
              Chat(
                initialPrompt = prompt,
                conversationId = convId,
                startLiveVoice = startLive,
                startVoiceMic = startMic,
              )
            }
            "EXPLORE" -> Explore
            "SETTINGS" -> Settings
            "API_KEY_CONFIG" -> ApiKeyConfig
            "PROFILE" -> Profile
            "AUTH" -> {
              val isSignUp = list.getOrNull(1) as? String == "true"
              Auth(initialIsSignUp = isSignUp)
            }
            else -> Home
          }
        },
      )
  }
}

/**
 * Representation of an active or saved conversation session for the sidebar and history manager.
 */
@Immutable
data class RecentConversationItem(
  val id: String,
  val title: String,
  val preview: String = "",
  val timestampFormatted: String = "Just now",
  val updatedAt: Long = System.currentTimeMillis(),
  val searchableContent: String = "",
  val isPinned: Boolean = false,
  val isLiked: Boolean = false,
) {
  /**
   * Fast short-circuiting search check across title, preview, and full message content.
   */
  fun matchesQuery(cleanQuery: String): Boolean {
    if (cleanQuery.isEmpty()) return true
    return title.contains(cleanQuery, ignoreCase = true) ||
      preview.contains(cleanQuery, ignoreCase = true) ||
      searchableContent.contains(cleanQuery, ignoreCase = true)
  }

  /**
   * Fast category classification for sidebar category filter chips (ALL, PINNED, LIKED, CODING, STUDY, WORK).
   */
  fun matchesCategory(category: String): Boolean {
    return when (category.uppercase()) {
      "ALL" -> true
      "PINNED" -> isPinned
      "LIKED" -> isLiked
      "CODING" -> {
        val combined = "$title $preview $searchableContent".lowercase()
        combined.contains("code") ||
          combined.contains("kotlin") ||
          combined.contains("python") ||
          combined.contains("javascript") ||
          combined.contains("bug") ||
          combined.contains("api") ||
          combined.contains("function") ||
          combined.contains("class") ||
          combined.contains("sql") ||
          combined.contains("```") ||
          combined.contains("android") ||
          combined.contains("coroutine")
      }
      "STUDY" -> {
        val combined = "$title $preview $searchableContent".lowercase()
        combined.contains("explain") ||
          combined.contains("study") ||
          combined.contains("exam") ||
          combined.contains("tutor") ||
          combined.contains("learn") ||
          combined.contains("notes") ||
          combined.contains("concept") ||
          combined.contains("quantum") ||
          combined.contains("समझाएं") ||
          combined.contains("नोट्स")
      }
      "WORK" -> {
        val combined = "$title $preview $searchableContent".lowercase()
        combined.contains("email") ||
          combined.contains("product") ||
          combined.contains("launch") ||
          combined.contains("business") ||
          combined.contains("plan") ||
          combined.contains("strategy") ||
          combined.contains("report") ||
          combined.contains("meeting") ||
          combined.contains("project") ||
          combined.contains("arr")
      }
      else -> true
    }
  }
}

