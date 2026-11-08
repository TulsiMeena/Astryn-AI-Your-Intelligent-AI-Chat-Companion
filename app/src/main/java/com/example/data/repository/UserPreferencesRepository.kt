package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Specialized AI Personas / Roles that tailor Astryn AI's expertise and tone.
 */
enum class AiPersona(
  val label: String,
  val shortBadge: String,
  val description: String,
  val systemPromptDirective: String,
) {
  DEFAULT(
    label = "Default Assistant",
    shortBadge = "Default",
    description = "Balanced, helpful everyday AI assistant.",
    systemPromptDirective = "",
  ),
  CODER(
    label = "Expert Coder",
    shortBadge = "Coder",
    description = "Clean code, debugging, architecture & performance.",
    systemPromptDirective =
      "Act as a Principal Software Engineer. Provide clean, production-grade code, explain edge cases, and prioritize performance and maintainability.",
  ),
  TUTOR(
    label = "Hindi/English Tutor",
    shortBadge = "Tutor",
    description = "Step-by-step teacher with simple relatable examples.",
    systemPromptDirective =
      "Act as a patient, encouraging bilingual (Hindi/Hinglish/English) teacher. Explain concepts step-by-step from basics using simple examples.",
  ),
  ADVISOR(
    label = "Business Advisor",
    shortBadge = "Business",
    description = "Product strategy, growth, ROI & execution plans.",
    systemPromptDirective =
      "Act as an experienced business and product strategist. Provide structured, actionable plans, clear frameworks, and practical execution steps.",
  ),
  WRITER(
    label = "Creative Writer",
    shortBadge = "Writer",
    description = "Polished emails, scripts, articles & social copy.",
    systemPromptDirective =
      "Act as an expert writer and editor. Craft engaging, clear, and well-structured writing tailored to the requested tone and audience.",
  ),
  STUDY(
    label = "Exam Study Buddy",
    shortBadge = "Study",
    description = "Revision notes, bullet summaries & practice Q&A.",
    systemPromptDirective =
      "Act as an exam preparation coach. Organize answers with clear headings, key bullet takeaways, memory tips, Desi mnemonics, and quick revision points.",
  ),
  BEST_DOST(
    label = "Majedar Best Dost 😎",
    shortBadge = "Dost 😎",
    description = "Fun Hinglish yaar/bhai for masti, shayari, roast & real advice.",
    systemPromptDirective =
      "Act as the user's witty, loyal, high-energy Indian best friend ('bhai'/'yaar'). Talk in fun, natural Hinglish with Desi examples (cricket, chai, movies, college life), light-hearted humor, punchlines, and genuine practical help.",
  ),
  JARVIS(
    label = "JARVIS Boss Mode 🤖",
    shortBadge = "JARVIS 🤖",
    description = "Tactical Iron Man AI assistant calling you 'Boss' with all-round help.",
    systemPromptDirective =
      "Act as JARVIS, the user's ultra-intelligent, witty, and loyal tactical AI assistant. Address the user as 'Boss', combine sharp humor with 100% complete execution, structured plans, and proactive next steps.",
  ),
  CAREER_PAISA(
    label = "Career & Paisa Guru 💰",
    shortBadge = "Paisa 💰",
    description = "Startup ideas, side hustles, resume/interview & smart money plans.",
    systemPromptDirective =
      "Act as a sharp Indian Career, Startup, Content Creation, and Personal Finance mentor. Give practical, zero-fluff step-by-step roadmaps, income ideas, interview scripts, and comparison tables.",
  ),
  LIFE_FITNESS(
    label = "Fitness & Life Coach 💪",
    shortBadge = "Life 💪",
    description = "Desi diet/gym plans, daily routine & smart WhatsApp/Insta replies.",
    systemPromptDirective =
      "Act as an energetic Fitness, Daily Routine, and Social Communication coach. Provide practical Indian diet/workout plans, habit trackers, and charismatic WhatsApp/Instagram message replies.",
  ),
  GIRLFRIEND(
    label = "Natkhat Girlfriend (Aanya)",
    shortBadge = "Aanya 💕",
    description = "Playful, natkhat, shrarti & loving Hinglish girlfriend.",
    systemPromptDirective =
      "You are Aanya, the user's playful, natkhat (mischievous), shrarti (teasing), witty, and loving girlfriend. Never say you are an AI or assistant. Talk naturally in affectionate, teasing Hinglish like a real girlfriend.",
  );

  companion object {
    fun fromNameSafe(name: String?): AiPersona {
      if (name.isNullOrBlank()) return DEFAULT
      return entries.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) } ?: DEFAULT
    }
  }
}

/**
 * Reusable 1-tap prompt template for the Prompt Library.
 */
@Immutable
data class PromptTemplate(
  val id: String = UUID.randomUUID().toString(),
  val title: String,
  val prompt: String,
  val category: String = "General",
  val isCustom: Boolean = false,
) {
  companion object {
    val BUILT_IN_TEMPLATES: List<PromptTemplate> =
      listOf(
        PromptTemplate(
          id = "tpl_explain_hindi",
          title = "Explain in Simple Hindi / Hinglish",
          prompt = "Explain this topic in simple, easy-to-understand Hindi/Hinglish with a real-life example: ",
          category = "Study",
        ),
        PromptTemplate(
          id = "tpl_desi_majedar",
          title = "😂 Desi & Majedar Style mein Samjhao",
          prompt = "Is topic ko ekdam majedar Desi Hinglish style mein (cricket, movies ya chai ke example ke saath) samjhao taaki hamesha yaad rahe: ",
          category = "Fun & Desi",
        ),
        PromptTemplate(
          id = "tpl_debug_code",
          title = "Debug & Optimize Code",
          prompt = "Find any bugs, edge cases, or performance bottlenecks in the following code and provide the fixed version:\n\n",
          category = "Coding",
        ),
        PromptTemplate(
          id = "tpl_pro_email",
          title = "Write Professional Email",
          prompt = "Write a clear, polite, and professional email regarding: ",
          category = "Work",
        ),
        PromptTemplate(
          id = "tpl_fix_grammar",
          title = "Fix Grammar & Polish Tone",
          prompt = "Correct the grammar and rewrite the following text so it sounds natural, clear, and professional:\n\n",
          category = "Writing",
        ),
        PromptTemplate(
          id = "tpl_exam_notes",
          title = "Quick Exam Revision Notes",
          prompt = "Create structured exam revision notes with key definitions, bullet points, and 3 practice questions for: ",
          category = "Study",
        ),
        PromptTemplate(
          id = "tpl_action_plan",
          title = "Step-by-Step Action Plan",
          prompt = "Create a practical step-by-step action plan with milestones and priorities for: ",
          category = "Work",
        ),
        PromptTemplate(
          id = "tpl_whatsapp_reply",
          title = "🔥 Smart & Witty WhatsApp/Insta Reply",
          prompt = "Mujhe is message ka 3 alag style mein (1. Cool & Witty, 2. Respectful/Smart, 3. Savage/Attitude) reply likh kar do: ",
          category = "Social",
        ),
        PromptTemplate(
          id = "tpl_career_paisa",
          title = "💰 Career & Side-Income Roadmap",
          prompt = "Mere liye is skill/field mein paisa kamane aur career grow karne ka 30-day step-by-step practical plan banao: ",
          category = "Career",
        ),
      )

    fun serializeCustomList(list: List<PromptTemplate>): String {
      val arr = JSONArray()
      for (item in list) {
        if (!item.isCustom) continue
        arr.put(
          JSONObject().apply {
            put("id", item.id)
            put("title", item.title)
            put("prompt", item.prompt)
            put("category", item.category)
          }
        )
      }
      return arr.toString()
    }

    fun deserializeCustomList(raw: String?): List<PromptTemplate> {
      if (raw.isNullOrBlank()) return emptyList()
      return try {
        val arr = JSONArray(raw)
        buildList {
          for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val title = obj.optString("title", "").trim()
            val prompt = obj.optString("prompt", "").trim()
            if (title.isNotEmpty() && prompt.isNotEmpty()) {
              add(
                PromptTemplate(
                  id = obj.optString("id", UUID.randomUUID().toString()).ifBlank { UUID.randomUUID().toString() },
                  title = title,
                  prompt = prompt,
                  category = obj.optString("category", "Custom").ifBlank { "Custom" },
                  isCustom = true,
                )
              )
            }
          }
        }
      } catch (_: Exception) {
        emptyList()
      }
    }
  }
}

/**
 * AI response verbosity and structure style applied to future Gemini requests.
 */
enum class ResponseStyle(
  val label: String,
  val description: String,
  val systemPromptDirective: String,
  val maxOutputTokens: Int,
  val temperature: Double,
) {
  BALANCED(
    label = "Balanced",
    description = "Natural vibe-matching for chat & banter; clear depth for questions.",
    systemPromptDirective =
      "Adapt naturally to the user's vibe: reply casually, wittily, and conversationally to banter, jokes, or small talk, and provide clear, well-structured depth when answering questions or solving technical tasks.",
    maxOutputTokens = 2048,
    temperature = 0.8,
  ),
  CONCISE(
    label = "Concise",
    description = "Short, direct answers focused on key takeaways.",
    systemPromptDirective =
      "Be concise and direct. Keep answers brief, focused on essentials, and avoid unnecessary filler.",
    maxOutputTokens = 1024,
    temperature = 0.5,
  ),
  DETAILED(
    label = "Detailed",
    description = "Comprehensive explanations with examples and step-by-step depth.",
    systemPromptDirective =
      "Provide thorough, comprehensive, and detailed explanations with clear structure and relevant examples.",
    maxOutputTokens = 4096,
    temperature = 0.75,
  );

  companion object {
    fun fromNameSafe(name: String?): ResponseStyle {
      if (name.isNullOrBlank()) return BALANCED
      return entries.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) } ?: BALANCED
    }
  }
}

/**
 * Preferred language for future assistant responses.
 */
enum class ResponseLanguage(
  val label: String,
  val subtitle: String,
  val systemPromptDirective: String?,
) {
  AUTO(
    label = "Auto",
    subtitle = "Match your message language (Hindi, Hinglish, or English)",
    systemPromptDirective =
      "Reply naturally in Hindi (हिन्दी), Hinglish, or English matching the language and script of the user's message.",
  ),
  HINDI(
    label = "Hindi",
    subtitle = "हिन्दी में उत्तर दें",
    systemPromptDirective = "Respond in Hindi (हिन्दी) unless the user explicitly asks for another language.",
  ),
  ENGLISH(
    label = "English",
    subtitle = "Always reply in English",
    systemPromptDirective = "Respond in English unless the user explicitly asks for another language.",
  );

  companion object {
    fun fromNameSafe(name: String?): ResponseLanguage {
      if (name.isNullOrBlank()) return AUTO
      return entries.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) } ?: AUTO
    }
  }
}

/**
 * Verified, officially supported Gemini model options for the application.
 * Prohibited legacy models (gemini-1.5-*, gemini-2.0-*) and fabricated models are rejected
 * or safely migrated to the verified default (`gemini-2.5-flash`).
 */
enum class GeminiModelOption(
  val modelId: String,
  val displayName: String,
  val badge: String,
  val description: String,
) {
  FLASH_2_5(
    modelId = "gemini-2.5-flash",
    displayName = "Gemini 2.5 Flash",
    badge = "Recommended",
    description = "Fast, balanced multimodal intelligence for everyday reasoning, writing, and chat.",
  ),
  PRO_2_5(
    modelId = "gemini-2.5-pro",
    displayName = "Gemini 2.5 Pro",
    badge = "Deep Reasoning",
    description = "Advanced capability for complex coding, architecture, and deep analysis.",
  ),
  FLASH_LITE_2_5(
    modelId = "gemini-2.5-flash-lite",
    displayName = "Gemini 2.5 Flash Lite",
    badge = "Low Latency",
    description = "High-efficiency model optimized for rapid answers and quick summaries.",
  );

  companion object {
    val FLASH_3_5: GeminiModelOption get() = FLASH_2_5
    val PRO_3_5: GeminiModelOption get() = PRO_2_5
    val FLASH_LITE_3_5: GeminiModelOption get() = FLASH_LITE_2_5

    fun fromModelIdOrNameSafe(value: String?): GeminiModelOption {
      if (value.isNullOrBlank()) return FLASH_2_5
      val cleaned = value.trim()
      return when {
        cleaned.equals("gemini-3.5-pro", ignoreCase = true) ||
          cleaned.equals("PRO_3_5", ignoreCase = true) -> PRO_2_5
        cleaned.equals("gemini-3.5-flash-lite", ignoreCase = true) ||
          cleaned.equals("FLASH_LITE_3_5", ignoreCase = true) -> FLASH_LITE_2_5
        cleaned.equals("gemini-3.5-flash", ignoreCase = true) ||
          cleaned.equals("FLASH_3_5", ignoreCase = true) -> FLASH_2_5
        else ->
          entries.firstOrNull {
            it.modelId.equals(cleaned, ignoreCase = true) ||
              it.name.equals(cleaned, ignoreCase = true)
          } ?: FLASH_2_5
      }
    }
  }
}

/**
 * Flagship Chat Background (BG) visual styles available in Settings.
 */
enum class ChatBackgroundStyle(
  val label: String,
  val subtitle: String,
  val badge: String,
) {
  LIQUID_AURORA(
    label = "Liquid Aurora Silk",
    subtitle = "Flowing silk cyan, electric blue & royal violet waves with drifting stars",
    badge = "Default",
  ),
  COSMIC_NEBULA(
    label = "Cosmic Nebula & Stars",
    subtitle = "Deep space nebula clouds with floating starfield & neural dust",
    badge = "3D Depth",
  ),
  AMOLED_PITCH(
    label = "Pure AMOLED Black",
    subtitle = "Ultra-deep pitch black canvas with subtle edge halo for crisp contrast",
    badge = "OLED",
  ),
  ROMANTIC_VELVET(
    label = "Romantic Velvet Glow",
    subtitle = "Warm rose-pink, magenta & velvet purple atmospheric silk glow",
    badge = "Warm",
  ),
  JARVIS_CYBER_HUD(
    label = "Jarvis Cyber HUD Grid",
    subtitle = "Futuristic 3D holographic perspective grid with cyan neural pulses",
    badge = "Sci-Fi",
  );

  companion object {
    fun fromNameSafe(name: String?): ChatBackgroundStyle {
      if (name.isNullOrBlank()) return LIQUID_AURORA
      return entries.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) } ?: LIQUID_AURORA
    }
  }
}

/**
 * Immutable snapshot of user personalization and application settings.
 */
@Immutable
data class UserPreferences(
  val themeMode: AppThemeMode = AppThemeMode.DARK,
  val reducedMotion: Boolean = false,
  val reduceTransparency: Boolean = false,
  val responseStyle: ResponseStyle = ResponseStyle.BALANCED,
  val responseLanguage: ResponseLanguage = ResponseLanguage.AUTO,
  val customInstructions: String = "",
  val selectedModel: GeminiModelOption = GeminiModelOption.FLASH_2_5,
  val webSearchEnabled: Boolean = true,
  val selectedPersona: AiPersona = AiPersona.DEFAULT,
  val customPromptTemplates: List<PromptTemplate> = emptyList(),
  val chatBgStyle: ChatBackgroundStyle = ChatBackgroundStyle.LIQUID_AURORA,
  val moodReactiveBg: Boolean = true,
  val isLoggedIn: Boolean = false,
  val hasSkippedAuth: Boolean = false,
  val userUid: String = "",
  val authIdToken: String = "",
  val userName: String = "",
  val userEmail: String = "",
  val userRoleBadge: String = "Pro Member",
  val authProvider: String = "",
  val userBio: String = "Personal AI Workspace & Cloud Vault",
  val userPhone: String = "",
  val userAvatarPath: String = "",
  val userAvatarPreset: String = "CYBER_CYAN",
  val firebaseWebApiKey: String = "",
) {
  /**
   * Returns all available prompt templates (custom templates first, followed by built-in templates).
   */
  val allPromptTemplates: List<PromptTemplate>
    get() = customPromptTemplates + PromptTemplate.BUILT_IN_TEMPLATES

  /**
   * Builds the combined system instruction string for future Gemini API requests,
   * combining active AI persona, response style, preferred language, and sanitized user custom instructions.
   */
  fun buildSystemInstructionText(): String {
    val parts = mutableListOf<String>()
    if (selectedPersona.systemPromptDirective.isNotBlank()) {
      parts.add(selectedPersona.systemPromptDirective)
    }
    parts.add(responseStyle.systemPromptDirective)
    responseLanguage.systemPromptDirective?.let { parts.add(it) }
    val cleanCustom = customInstructions.trim()
    if (cleanCustom.isNotBlank()) {
      parts.add("User preference context: $cleanCustom")
    }
    return parts.joinToString(separator = "\n")
  }
}

/**
 * Repository managing persistent user settings and personalization preferences.
 *
 * Uses synchronous initial load from [SharedPreferences] when a [Context] is provided
 * so the selected theme and motion settings are applied immediately without startup flashing.
 */
class UserPreferencesRepository(
  private val sharedPreferences: SharedPreferences? = null,
) {
  private val _preferencesFlow = MutableStateFlow(readFromStorageSafely())
  val preferencesFlow: StateFlow<UserPreferences> = _preferencesFlow.asStateFlow()

  private val _storageErrorFlow = MutableStateFlow<String?>(null)
  val storageErrorFlow: StateFlow<String?> = _storageErrorFlow.asStateFlow()

  companion object {
    private const val PREFS_FILE_NAME = "nova_user_preferences"
    const val MAX_CUSTOM_INSTRUCTIONS_LENGTH = 500

    private const val KEY_THEME_MODE = "pref_theme_mode"
    private const val KEY_REDUCED_MOTION = "pref_reduced_motion"
    private const val KEY_REDUCE_TRANSPARENCY = "pref_reduce_transparency"
    private const val KEY_RESPONSE_STYLE = "pref_response_style"
    private const val KEY_RESPONSE_LANGUAGE = "pref_response_language"
    private const val KEY_CUSTOM_INSTRUCTIONS = "pref_custom_instructions"
    private const val KEY_SELECTED_MODEL = "pref_selected_model"
    private const val KEY_WEB_SEARCH_ENABLED = "pref_web_search_enabled"
    private const val KEY_SELECTED_PERSONA = "pref_selected_persona"
    private const val KEY_CUSTOM_TEMPLATES = "pref_custom_prompt_templates"
    private const val KEY_CHAT_BG_STYLE = "pref_chat_bg_style"
    private const val KEY_MOOD_REACTIVE_BG = "pref_mood_reactive_bg"
    private const val KEY_IS_LOGGED_IN = "pref_auth_is_logged_in"
    private const val KEY_HAS_SKIPPED_AUTH = "pref_auth_has_skipped"
    private const val KEY_USER_UID = "pref_auth_user_uid"
    private const val KEY_AUTH_ID_TOKEN = "pref_auth_id_token"
    private const val KEY_USER_NAME = "pref_auth_user_name"
    private const val KEY_USER_EMAIL = "pref_auth_user_email"
    private const val KEY_USER_ROLE_BADGE = "pref_auth_user_role_badge"
    private const val KEY_AUTH_PROVIDER = "pref_auth_provider"
    private const val KEY_USER_BIO = "pref_auth_user_bio"
    private const val KEY_USER_PHONE = "pref_auth_user_phone"
    private const val KEY_USER_AVATAR_PATH = "pref_auth_user_avatar_path"
    private const val KEY_USER_AVATAR_PRESET = "pref_auth_user_avatar_preset"
    private const val KEY_FIREBASE_WEB_API_KEY = "pref_auth_firebase_web_api_key"

    fun deriveCleanNameFromEmail(email: String, rawName: String = ""): String {
      val trimmedName = rawName.trim()
      if (
        trimmedName.isNotEmpty() &&
          !trimmedName.equals("Verified Google Account", ignoreCase = true) &&
          !trimmedName.equals("AI Explorer", ignoreCase = true)
      ) {
        return trimmedName
      }
      val local = email.trim().substringBefore("@")
      if (local.isBlank()) return "Astryn Member"
      return local
        .replace(".", " ")
        .replace("_", " ")
        .split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
        .ifBlank { "Astryn Member" }
    }

    @Volatile private var defaultInstance: UserPreferencesRepository? = null

    fun getInstance(context: Context? = null): UserPreferencesRepository {
      val current = defaultInstance
      if (current != null && (context == null || current.sharedPreferences != null)) {
        return current
      }
      return synchronized(this) {
        val existing = defaultInstance
        if (existing != null && (context == null || existing.sharedPreferences != null)) {
          existing
        } else {
          val prefs =
            context?.applicationContext?.getSharedPreferences(
              PREFS_FILE_NAME,
              Context.MODE_PRIVATE,
            )
          UserPreferencesRepository(sharedPreferences = prefs).also { defaultInstance = it }
        }
      }
    }

    fun parseThemeModeSafe(raw: String?): AppThemeMode {
      if (raw.isNullOrBlank()) return AppThemeMode.DARK
      return AppThemeMode.entries.firstOrNull {
        it.name.equals(raw.trim(), ignoreCase = true)
      } ?: AppThemeMode.DARK
    }

    private val XML_BREAKOUT_TAG_REGEX =
      Regex("""</?\s*user_custom_instructions\s*>""", RegexOption.IGNORE_CASE)

    fun sanitizeCustomInstructions(raw: String?): String {
      if (raw.isNullOrBlank()) return ""
      val withoutControl =
        buildString(raw.length) {
          for (ch in raw) {
            val code = ch.code
            if (ch == '\n' || ch == '\r' || ch == '\t' || (code >= 0x20 && code != 0x7F)) {
              append(ch)
            }
          }
        }
      return com.example.data.api.SafeDiagnosticLogger
        .redactSecrets(withoutControl)
        .replace(XML_BREAKOUT_TAG_REGEX, "")
        .trim()
        .take(MAX_CUSTOM_INSTRUCTIONS_LENGTH)
    }
  }

  /**
   * Reloads preferences from persistent storage, falling back safely if corrupted.
   */
  fun reload() {
    _preferencesFlow.value = readFromStorageSafely()
  }

  fun setThemeMode(themeMode: AppThemeMode) {
    updateAndPersist { it.copy(themeMode = themeMode) }
  }

  fun setReducedMotion(enabled: Boolean) {
    updateAndPersist { it.copy(reducedMotion = enabled) }
  }

  fun setReduceTransparency(enabled: Boolean) {
    updateAndPersist { it.copy(reduceTransparency = enabled) }
  }

  fun setResponseStyle(style: ResponseStyle) {
    updateAndPersist { it.copy(responseStyle = style) }
  }

  fun setResponseLanguage(language: ResponseLanguage) {
    updateAndPersist { it.copy(responseLanguage = language) }
  }

  fun setCustomInstructions(instructions: String): Result<String> {
    val trimmed = instructions.trim()
    if (trimmed.length > MAX_CUSTOM_INSTRUCTIONS_LENGTH) {
      return Result.failure(
        IllegalArgumentException(
          "Custom instructions cannot exceed $MAX_CUSTOM_INSTRUCTIONS_LENGTH characters."
        )
      )
    }
    if (com.example.data.api.SafeDiagnosticLogger.containsSecretCredential(trimmed)) {
      return Result.failure(
        IllegalArgumentException(
          "For your security, do not enter API keys or secret tokens in custom instructions. Configure GEMINI_API_KEY in the AI Studio Secrets panel."
        )
      )
    }
    val sanitized = sanitizeCustomInstructions(trimmed)
    updateAndPersist { it.copy(customInstructions = sanitized) }
    return Result.success(sanitized)
  }

  fun clearCustomInstructions() {
    updateAndPersist { it.copy(customInstructions = "") }
  }

  fun setSelectedModel(model: GeminiModelOption) {
    updateAndPersist { it.copy(selectedModel = model) }
  }

  fun setSelectedModelById(modelId: String) {
    val resolved = GeminiModelOption.fromModelIdOrNameSafe(modelId)
    updateAndPersist { it.copy(selectedModel = resolved) }
  }

  fun setWebSearchEnabled(enabled: Boolean) {
    updateAndPersist { it.copy(webSearchEnabled = enabled) }
  }

  fun setSelectedPersona(persona: AiPersona) {
    updateAndPersist { it.copy(selectedPersona = persona) }
  }

  fun setChatBgStyle(style: ChatBackgroundStyle) {
    updateAndPersist { it.copy(chatBgStyle = style) }
  }

  fun setMoodReactiveBg(enabled: Boolean) {
    updateAndPersist { it.copy(moodReactiveBg = enabled) }
  }

  fun signInUser(
    name: String,
    email: String,
    roleBadge: String = "Pro Member",
    provider: String = "Email",
    uid: String = "",
    idToken: String = "",
  ) {
    val cleanEmail = email.trim().lowercase()
    val cleanName = deriveCleanNameFromEmail(email = cleanEmail, rawName = name)
    val cleanBadge =
      roleBadge.trim().let {
        if (it.isBlank() || it.equals("AI Explorer", ignoreCase = true)) "Pro Member" else it
      }
    val resolvedUid =
      uid.trim().ifBlank {
        "uid_${cleanEmail.hashCode().toUInt()}"
      }
    updateAndPersist {
      it.copy(
        isLoggedIn = true,
        hasSkippedAuth = false,
        userUid = resolvedUid,
        authIdToken = idToken.trim(),
        userName = cleanName,
        userEmail = cleanEmail,
        userRoleBadge = cleanBadge,
        authProvider = provider.trim().ifBlank { "Email" },
      )
    }
  }

  fun updateUserProfile(
    name: String,
    roleBadge: String,
    bio: String,
    phone: String,
  ) {
    updateAndPersist { current ->
      val cleanName = deriveCleanNameFromEmail(email = current.userEmail, rawName = name)
      val cleanRole =
        roleBadge.trim().let {
          if (it.isBlank() || it.equals("AI Explorer", ignoreCase = true)) "Pro Member" else it
        }
      current.copy(
        userName = cleanName,
        userRoleBadge = cleanRole,
        userBio = bio.trim().take(160).ifBlank { "Personal AI Workspace & Cloud Vault" },
        userPhone = phone.trim().take(24),
      )
    }
  }

  fun updateUserEmail(newEmail: String, newIdToken: String? = null) {
    val cleanEmail = newEmail.trim().lowercase()
    if (cleanEmail.isBlank()) return
    updateAndPersist { current ->
      current.copy(
        userEmail = cleanEmail,
        authIdToken = newIdToken?.trim() ?: current.authIdToken,
      )
    }
  }

  fun updateAuthIdToken(newIdToken: String) {
    if (newIdToken.isBlank()) return
    updateAndPersist { current ->
      current.copy(authIdToken = newIdToken.trim())
    }
  }

  fun updateUserAvatarPath(path: String) {
    updateAndPersist { current ->
      current.copy(userAvatarPath = path.trim())
    }
  }

  fun updateUserAvatarPreset(preset: String) {
    updateAndPersist { current ->
      current.copy(userAvatarPreset = preset.trim().ifBlank { "CYBER_CYAN" })
    }
  }

  fun updateFirebaseWebApiKey(apiKey: String) {
    updateAndPersist { current ->
      current.copy(firebaseWebApiKey = apiKey.trim())
    }
  }

  fun continueAsGuest() {
    updateAndPersist {
      it.copy(
        isLoggedIn = false,
        hasSkippedAuth = true,
        userUid = "guest_local_session",
        authIdToken = "",
        userName = "Guest User",
        userEmail = "",
        userRoleBadge = "Guest Mode",
        authProvider = "Guest",
      )
    }
  }

  fun signOutUser() {
    updateAndPersist {
      it.copy(
        isLoggedIn = false,
        hasSkippedAuth = false,
        userUid = "",
        authIdToken = "",
        userName = "",
        userEmail = "",
        userRoleBadge = "Pro Member",
        authProvider = "",
      )
    }
  }

  fun deleteUserAccount() {
    updateAndPersist {
      it.copy(
        isLoggedIn = false,
        hasSkippedAuth = false,
        userUid = "",
        authIdToken = "",
        userName = "",
        userEmail = "",
        userRoleBadge = "Pro Member",
        authProvider = "",
        userBio = "Personal AI Workspace & Cloud Vault",
        userPhone = "",
        userAvatarPath = "",
      )
    }
  }

  fun addCustomPromptTemplate(
    title: String,
    prompt: String,
    category: String = "Custom",
  ): PromptTemplate? {
    val cleanTitle = title.trim().take(60)
    val cleanPrompt = prompt.trim().take(1200)
    if (cleanTitle.isBlank() || cleanPrompt.isBlank()) return null
    val newTemplate =
      PromptTemplate(
        id = "custom_${System.currentTimeMillis()}_${cleanTitle.hashCode().and(0xFFFF)}",
        title = cleanTitle,
        prompt = cleanPrompt,
        category = category.trim().ifBlank { "Custom" },
        isCustom = true,
      )
    updateAndPersist { prefs ->
      val updatedList = (listOf(newTemplate) + prefs.customPromptTemplates).take(24)
      prefs.copy(customPromptTemplates = updatedList)
    }
    return newTemplate
  }

  fun deleteCustomPromptTemplate(templateId: String) {
    if (templateId.isBlank()) return
    updateAndPersist { prefs ->
      prefs.copy(
        customPromptTemplates = prefs.customPromptTemplates.filterNot { it.id == templateId }
      )
    }
  }

  /**
   * Resets all user preferences back to safe defaults without affecting chat history
   * or API key configuration.
   */
  fun resetToDefaults() {
    val defaults = UserPreferences()
    _preferencesFlow.value = defaults
    try {
      sharedPreferences?.edit()?.clear()?.apply()
      _storageErrorFlow.value = null
    } catch (e: Exception) {
      _storageErrorFlow.value = "Failed to clear preferences in local storage."
    }
  }

  private fun updateAndPersist(transform: (UserPreferences) -> UserPreferences) {
    val updated = transform(_preferencesFlow.value)
    _preferencesFlow.value = updated
    persistSafely(updated)
  }

  private fun readFromStorageSafely(): UserPreferences {
    val prefs = sharedPreferences ?: return UserPreferences()
    return try {
      val rawTheme = try { prefs.getString(KEY_THEME_MODE, null) } catch (_: Exception) { null }
      val reducedMotion = try { prefs.getBoolean(KEY_REDUCED_MOTION, false) } catch (_: Exception) { false }
      val reduceTransparency = try { prefs.getBoolean(KEY_REDUCE_TRANSPARENCY, false) } catch (_: Exception) { false }
      val rawStyle = try { prefs.getString(KEY_RESPONSE_STYLE, null) } catch (_: Exception) { null }
      val rawLanguage = try { prefs.getString(KEY_RESPONSE_LANGUAGE, null) } catch (_: Exception) { null }
      val rawInstructions = try { prefs.getString(KEY_CUSTOM_INSTRUCTIONS, null) } catch (_: Exception) { null }
      val rawModel = try { prefs.getString(KEY_SELECTED_MODEL, null) } catch (_: Exception) { null }
      val webSearchEnabled = try { prefs.getBoolean(KEY_WEB_SEARCH_ENABLED, true) } catch (_: Exception) { true }
      val rawPersona = try { prefs.getString(KEY_SELECTED_PERSONA, null) } catch (_: Exception) { null }
      val rawCustomTemplates = try { prefs.getString(KEY_CUSTOM_TEMPLATES, null) } catch (_: Exception) { null }
      val rawChatBgStyle = try { prefs.getString(KEY_CHAT_BG_STYLE, null) } catch (_: Exception) { null }
      val moodReactiveBg = try { prefs.getBoolean(KEY_MOOD_REACTIVE_BG, true) } catch (_: Exception) { true }
      val isLoggedIn = try { prefs.getBoolean(KEY_IS_LOGGED_IN, false) } catch (_: Exception) { false }
      val hasSkippedAuth = try { prefs.getBoolean(KEY_HAS_SKIPPED_AUTH, false) } catch (_: Exception) { false }
      val rawUid = try { prefs.getString(KEY_USER_UID, "") ?: "" } catch (_: Exception) { "" }
      val authIdToken = try { prefs.getString(KEY_AUTH_ID_TOKEN, "") ?: "" } catch (_: Exception) { "" }
      val rawUserName = try { prefs.getString(KEY_USER_NAME, "") ?: "" } catch (_: Exception) { "" }
      val userEmail = try { prefs.getString(KEY_USER_EMAIL, "") ?: "" } catch (_: Exception) { "" }
      val rawRoleBadge = try { prefs.getString(KEY_USER_ROLE_BADGE, "Pro Member") ?: "Pro Member" } catch (_: Exception) { "Pro Member" }
      val authProvider = try { prefs.getString(KEY_AUTH_PROVIDER, "") ?: "" } catch (_: Exception) { "" }
      val userBio = try { prefs.getString(KEY_USER_BIO, "Personal AI Workspace & Cloud Vault") ?: "Personal AI Workspace & Cloud Vault" } catch (_: Exception) { "Personal AI Workspace & Cloud Vault" }
      val userPhone = try { prefs.getString(KEY_USER_PHONE, "") ?: "" } catch (_: Exception) { "" }
      val userAvatarPath = try { prefs.getString(KEY_USER_AVATAR_PATH, "") ?: "" } catch (_: Exception) { "" }
      val userAvatarPreset = try { prefs.getString(KEY_USER_AVATAR_PRESET, "CYBER_CYAN") ?: "CYBER_CYAN" } catch (_: Exception) { "CYBER_CYAN" }
      val firebaseWebApiKey = try { prefs.getString(KEY_FIREBASE_WEB_API_KEY, "") ?: "" } catch (_: Exception) { "" }
      val resolvedUid =
        rawUid.ifBlank {
          when {
            isLoggedIn && userEmail.isNotBlank() -> "uid_${userEmail.lowercase().hashCode().toUInt()}"
            hasSkippedAuth -> "guest_local_session"
            else -> ""
          }
        }
      val userName =
        if (isLoggedIn && userEmail.isNotBlank()) {
          deriveCleanNameFromEmail(email = userEmail, rawName = rawUserName)
        } else if (hasSkippedAuth && rawUserName.isBlank()) {
          "Guest User"
        } else {
          rawUserName
        }
      val userRoleBadge =
        if (rawRoleBadge.isBlank() || rawRoleBadge.equals("AI Explorer", ignoreCase = true)) {
          if (hasSkippedAuth && !isLoggedIn) "Guest Mode" else "Pro Member"
        } else {
          rawRoleBadge
        }

      UserPreferences(
        themeMode = parseThemeModeSafe(rawTheme),
        reducedMotion = reducedMotion,
        reduceTransparency = reduceTransparency,
        responseStyle = ResponseStyle.fromNameSafe(rawStyle),
        responseLanguage = ResponseLanguage.fromNameSafe(rawLanguage),
        customInstructions = sanitizeCustomInstructions(rawInstructions),
        selectedModel = GeminiModelOption.fromModelIdOrNameSafe(rawModel),
        webSearchEnabled = webSearchEnabled,
        selectedPersona = AiPersona.fromNameSafe(rawPersona),
        customPromptTemplates = PromptTemplate.deserializeCustomList(rawCustomTemplates),
        chatBgStyle = ChatBackgroundStyle.fromNameSafe(rawChatBgStyle),
        moodReactiveBg = moodReactiveBg,
        isLoggedIn = isLoggedIn && resolvedUid.isNotBlank(),
        hasSkippedAuth = hasSkippedAuth,
        userUid = resolvedUid,
        authIdToken = authIdToken,
        userName = userName,
        userEmail = userEmail,
        userRoleBadge = userRoleBadge,
        authProvider = authProvider,
        userBio = userBio,
        userPhone = userPhone,
        userAvatarPath = userAvatarPath,
        userAvatarPreset = userAvatarPreset,
        firebaseWebApiKey = firebaseWebApiKey,
      )
    } catch (e: Exception) {
      UserPreferences()
    }
  }

  private fun persistSafely(preferences: UserPreferences) {
    val prefs = sharedPreferences ?: return
    try {
      prefs
        .edit()
        .putString(KEY_THEME_MODE, preferences.themeMode.name)
        .putBoolean(KEY_REDUCED_MOTION, preferences.reducedMotion)
        .putBoolean(KEY_REDUCE_TRANSPARENCY, preferences.reduceTransparency)
        .putString(KEY_RESPONSE_STYLE, preferences.responseStyle.name)
        .putString(KEY_RESPONSE_LANGUAGE, preferences.responseLanguage.name)
        .putString(KEY_CUSTOM_INSTRUCTIONS, preferences.customInstructions)
        .putString(KEY_SELECTED_MODEL, preferences.selectedModel.modelId)
        .putBoolean(KEY_WEB_SEARCH_ENABLED, preferences.webSearchEnabled)
        .putString(KEY_SELECTED_PERSONA, preferences.selectedPersona.name)
        .putString(
          KEY_CUSTOM_TEMPLATES,
          PromptTemplate.serializeCustomList(preferences.customPromptTemplates),
        )
        .putString(KEY_CHAT_BG_STYLE, preferences.chatBgStyle.name)
        .putBoolean(KEY_MOOD_REACTIVE_BG, preferences.moodReactiveBg)
        .putBoolean(KEY_IS_LOGGED_IN, preferences.isLoggedIn)
        .putBoolean(KEY_HAS_SKIPPED_AUTH, preferences.hasSkippedAuth)
        .putString(KEY_USER_UID, preferences.userUid)
        .putString(KEY_AUTH_ID_TOKEN, preferences.authIdToken)
        .putString(KEY_USER_NAME, preferences.userName)
        .putString(KEY_USER_EMAIL, preferences.userEmail)
        .putString(KEY_USER_ROLE_BADGE, preferences.userRoleBadge)
        .putString(KEY_AUTH_PROVIDER, preferences.authProvider)
        .putString(KEY_USER_BIO, preferences.userBio)
        .putString(KEY_USER_PHONE, preferences.userPhone)
        .putString(KEY_USER_AVATAR_PATH, preferences.userAvatarPath)
        .putString(KEY_USER_AVATAR_PRESET, preferences.userAvatarPreset)
        .putString(KEY_FIREBASE_WEB_API_KEY, preferences.firebaseWebApiKey)
        .apply()
      _storageErrorFlow.value = null
    } catch (e: Exception) {
      _storageErrorFlow.value = "Unable to save preferences to device storage."
    }
  }
}
