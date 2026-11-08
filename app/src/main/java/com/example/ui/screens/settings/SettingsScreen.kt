package com.example.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MotionPhotosOff
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.data.api.GeminiService
import com.example.data.local.ApiConnectionStatus
import com.example.data.local.UserApiKeyManager
import com.example.data.repository.ChatBackgroundStyle
import com.example.data.repository.GeminiModelOption
import com.example.data.repository.ResponseLanguage
import com.example.data.repository.ResponseStyle
import com.example.data.repository.UserPreferences
import com.example.data.repository.UserPreferencesRepository
import com.example.ui.components.AmbientGlassBackground
import com.example.ui.components.AstrynBrandShowcase
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassButtonVariant
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassDivider
import com.example.ui.components.GlassIconButton
import com.example.ui.components.GlassSurface
import com.example.ui.components.GlassTextField
import com.example.ui.theme.AppBorder
import com.example.ui.theme.AppRadius
import com.example.ui.theme.AppSpacing
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.AppThemeTokens

/**
 * Production Settings & Personalization Screen (Prompt 8/15).
 *
 * Implements:
 * - Section A: Appearance (Theme: System / Dark / Light, Reduced Motion, Reduce Transparency)
 * - Section B: AI Response Preferences (Response Style, Preferred Language, Custom Instructions)
 * - Section C: Model Selection (Verified Gemini 3.5 models)
 * - Section D: General (About app version & status, Help & Troubleshooting, Reset Settings with confirmation)
 */
@Composable
fun SettingsScreen(
  preferencesRepository: UserPreferencesRepository,
  onOpenMenu: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  onClearAllHistory: (() -> Unit)? = null,
  onOpenApiConfig: (() -> Unit)? = null,
  onOpenAuth: ((Boolean) -> Unit)? = null,
  apiKeyManager: UserApiKeyManager = UserApiKeyManager.getInstance(),
  geminiService: GeminiService = remember { GeminiService() },
) {
  val glass = AppThemeTokens.glass
  val preferences by preferencesRepository.preferencesFlow.collectAsState()
  val storageError by preferencesRepository.storageErrorFlow.collectAsState()
  val apiKeyState by apiKeyManager.configStateFlow.collectAsState()

  var draftInstructions by rememberSaveable { mutableStateOf(preferences.customInstructions) }
  var instructionFeedback by rememberSaveable { mutableStateOf<String?>(null) }
  var isInstructionError by rememberSaveable { mutableStateOf(false) }
  var showHelpDialog by rememberSaveable { mutableStateOf(false) }
  var showResetConfirmDialog by rememberSaveable { mutableStateOf(false) }
  var showClearHistoryConfirmDialog by rememberSaveable { mutableStateOf(false) }

  // Synchronize draft text when preferences are reset or externally updated
  LaunchedEffect(preferences.customInstructions) {
    draftInstructions = preferences.customInstructions
  }

  val scrollState = rememberScrollState()

  AmbientGlassBackground(modifier = modifier.testTag("settings_screen")) {
    Scaffold(
      modifier = Modifier.fillMaxSize(),
      containerColor = Color.Transparent,
      contentWindowInsets = WindowInsets.systemBars,
    ) { innerPadding ->
      Column(
        modifier =
          Modifier.fillMaxSize()
            .padding(innerPadding)
            .imePadding(),
      ) {
        // -----------------------------------------------------------------
        // Top Navigation Bar
        // -----------------------------------------------------------------
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.sm),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
          ) {
            GlassIconButton(
              icon = Icons.AutoMirrored.Outlined.ArrowBack,
              contentDescription = stringResource(R.string.chat_back_desc),
              onClick = onBack,
              modifier = Modifier.testTag("settings_back_button"),
            )
            Column {
              Text(
                text = stringResource(R.string.settings_screen_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = glass.textPrimary,
              )
              Text(
                text = stringResource(R.string.settings_screen_subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = glass.accentCyan,
              )
            }
          }

          GlassIconButton(
            icon = Icons.Outlined.Menu,
            contentDescription = stringResource(R.string.home_menu_desc),
            onClick = onOpenMenu,
            modifier = Modifier.testTag("settings_menu_button"),
          )
        }

        // -----------------------------------------------------------------
        // Storage Error Banner (if local storage fails)
        // -----------------------------------------------------------------
        AnimatedVisibility(
          visible = storageError != null,
          enter = fadeIn(),
          exit = fadeOut(),
        ) {
          storageError?.let { errorMsg ->
            GlassSurface(
              modifier =
                Modifier.fillMaxWidth()
                  .padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.xs)
                  .semantics { liveRegion = LiveRegionMode.Assertive }
                  .testTag("settings_storage_error_banner"),
              elevated = true,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
              ) {
                Icon(
                  imageVector = Icons.Outlined.WarningAmber,
                  contentDescription = null,
                  tint = glass.statusWarning,
                  modifier = Modifier.size(20.dp),
                )
                Text(
                  text = errorMsg,
                  style = MaterialTheme.typography.bodySmall,
                  color = glass.textPrimary,
                )
              }
            }
          }
        }

        // -----------------------------------------------------------------
        // Scrollable Settings Content
        // -----------------------------------------------------------------
        Column(
          modifier =
            Modifier.weight(1f)
              .fillMaxWidth()
              .verticalScroll(scrollState)
              .padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.sm),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = AppSpacing.maxContentWidth),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
          ) {
            // ===============================================================
            // SECTION 00: ACCOUNT & CLOUD IDENTITY (LOGIN / SIGN UP)
            // ===============================================================
            SettingsSectionCard(
              title = "Account & Cloud Identity",
              subtitle =
                if (preferences.isLoggedIn) {
                  "Signed in via ${preferences.authProvider.ifBlank { "Email" }} • ${preferences.userRoleBadge}"
                } else {
                  "Sign in or create your Astryn ID (Firebase Auth Ready)"
                },
              icon = Icons.Outlined.Person,
              testTag = "settings_section_account",
            ) {
              if (preferences.isLoggedIn) {
                Row(
                  modifier =
                    Modifier.fillMaxWidth()
                      .clip(RoundedCornerShape(14.dp))
                      .background(glass.surfaceStandard)
                      .border(AppBorder.hairline, glass.borderAccent, RoundedCornerShape(14.dp))
                      .clickable { onOpenAuth?.invoke(false) }
                      .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  com.example.ui.screens.profile.UserAvatarBadge(
                    name = preferences.userName,
                    email = preferences.userEmail,
                    avatarPath = preferences.userAvatarPath,
                    avatarPresetId = preferences.userAvatarPreset,
                    size = 44.dp,
                  )
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = preferences.userName.ifBlank { "Astryn Member" },
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.Bold,
                      color = glass.textPrimary,
                    )
                    Text(
                      text = preferences.userEmail.ifBlank { "Verified Account" },
                      style = MaterialTheme.typography.bodySmall,
                      color = glass.accentCyan,
                    )
                  }
                  Box(
                    modifier =
                      Modifier.clip(AppRadius.shapePill)
                        .background(glass.accentCyan.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                  ) {
                    Text(
                      text = preferences.userRoleBadge,
                      style = MaterialTheme.typography.labelSmall,
                      color = glass.accentCyan,
                      fontWeight = FontWeight.Bold,
                    )
                  }
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                ) {
                  GlassButton(
                    text = "Edit Profile & Security",
                    variant = GlassButtonVariant.PRIMARY,
                    onClick = { onOpenAuth?.invoke(false) },
                    modifier = Modifier.weight(1f).testTag("settings_manage_account_button"),
                  )
                  GlassButton(
                    text = "Sign Out",
                    variant = GlassButtonVariant.SECONDARY,
                    onClick = { preferencesRepository.signOutUser() },
                    modifier = Modifier.weight(1f).testTag("settings_sign_out_button"),
                  )
                }
              } else {
                Text(
                  text = "Unlock personalized AI personas, synced settings, and cloud identity.",
                  style = MaterialTheme.typography.bodySmall,
                  color = glass.textSecondary,
                )
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                ) {
                  GlassButton(
                    text = "Sign In",
                    variant = GlassButtonVariant.PRIMARY,
                    onClick = { onOpenAuth?.invoke(false) },
                    modifier = Modifier.weight(1f).testTag("settings_open_signin_button"),
                  )
                  GlassButton(
                    text = "Create Account",
                    variant = GlassButtonVariant.SECONDARY,
                    onClick = { onOpenAuth?.invoke(true) },
                    modifier = Modifier.weight(1f).testTag("settings_open_signup_button"),
                  )
                }
              }
            }

            // ===============================================================
            // SECTION 0: AI PROVIDER & API KEY (USER-OWNED GEMINI KEY SYSTEM)
            // ===============================================================
            AiProviderApiKeyPanel(
              apiKeyManager = apiKeyManager,
              preferencesRepository = preferencesRepository,
              geminiService = geminiService,
              onOpenDedicatedScreen = onOpenApiConfig,
            )

            // ===============================================================
            // SECTION A: APPEARANCE
            // ===============================================================
            SettingsSectionCard(
              title = stringResource(R.string.settings_section_appearance),
              subtitle = stringResource(R.string.settings_section_appearance_sub),
              icon = Icons.Outlined.Palette,
              testTag = "settings_section_appearance",
            ) {
              Text(
                text = stringResource(R.string.settings_theme_label),
                style = MaterialTheme.typography.labelLarge,
                color = glass.textPrimary,
                fontWeight = FontWeight.SemiBold,
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
              ) {
                ThemeModePill(
                  label = stringResource(R.string.settings_theme_system),
                  icon = Icons.Outlined.SettingsBrightness,
                  isSelected = preferences.themeMode == AppThemeMode.SYSTEM,
                  onClick = { preferencesRepository.setThemeMode(AppThemeMode.SYSTEM) },
                  modifier = Modifier.weight(1f).testTag("theme_option_SYSTEM"),
                )
                ThemeModePill(
                  label = stringResource(R.string.settings_theme_dark),
                  icon = Icons.Outlined.DarkMode,
                  isSelected = preferences.themeMode == AppThemeMode.DARK,
                  onClick = { preferencesRepository.setThemeMode(AppThemeMode.DARK) },
                  modifier = Modifier.weight(1f).testTag("theme_option_DARK"),
                )
                ThemeModePill(
                  label = stringResource(R.string.settings_theme_light),
                  icon = Icons.Outlined.LightMode,
                  isSelected = preferences.themeMode == AppThemeMode.LIGHT,
                  onClick = { preferencesRepository.setThemeMode(AppThemeMode.LIGHT) },
                  modifier = Modifier.weight(1f).testTag("theme_option_LIGHT"),
                )
              }

              GlassDivider()

              // Chat Background (BG) Style Selector
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Chat Background (BG) Style",
                    style = MaterialTheme.typography.labelLarge,
                    color = glass.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                  )
                  Text(
                    text = "Choose your AI Chat Page atmosphere & visual canvas",
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.textSecondary,
                  )
                }
                Box(
                  modifier =
                    Modifier.clip(AppRadius.shapePill)
                      .background(glass.accentCyan.copy(alpha = 0.16f))
                      .border(
                        width = AppBorder.hairline,
                        color = glass.accentCyan.copy(alpha = 0.45f),
                        shape = AppRadius.shapePill,
                      )
                      .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                  Text(
                    text = preferences.chatBgStyle.badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = glass.accentCyan,
                    fontWeight = FontWeight.Bold,
                  )
                }
              }

              Column(
                modifier = Modifier.fillMaxWidth().testTag("settings_chat_bg_style_group"),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
              ) {
                ChatBackgroundStyle.entries.forEach { bgStyle ->
                  ChatBackgroundOptionCard(
                    bgStyle = bgStyle,
                    isSelected = preferences.chatBgStyle == bgStyle,
                    onClick = { preferencesRepository.setChatBgStyle(bgStyle) },
                    testTag = "bg_style_option_${bgStyle.name}",
                  )
                }
              }

              GlassDivider()

              // Mood & AI State Reactive Background Toggle
              SettingsToggleRow(
                title = "Mood & AI Voice Reactive BG",
                subtitle = "Automatically shift background aura for Romantic, Fun/Roast, Coding & AI Live Voice",
                icon = Icons.Outlined.AutoAwesome,
                checked = preferences.moodReactiveBg,
                onCheckedChange = { preferencesRepository.setMoodReactiveBg(it) },
                testTag = "settings_toggle_mood_reactive_bg",
              )

              GlassDivider()

              // Reduced Motion Toggle
              SettingsToggleRow(
                title = stringResource(R.string.settings_reduced_motion_title),
                subtitle = stringResource(R.string.settings_reduced_motion_sub),
                icon = Icons.Outlined.MotionPhotosOff,
                checked = preferences.reducedMotion,
                onCheckedChange = { preferencesRepository.setReducedMotion(it) },
                testTag = "settings_toggle_reduced_motion",
              )

              GlassDivider()

              // Reduce Transparency Toggle
              SettingsToggleRow(
                title = stringResource(R.string.settings_reduce_transparency_title),
                subtitle = stringResource(R.string.settings_reduce_transparency_sub),
                icon = Icons.Outlined.Contrast,
                checked = preferences.reduceTransparency,
                onCheckedChange = { preferencesRepository.setReduceTransparency(it) },
                testTag = "settings_toggle_reduce_transparency",
              )
            }

            // ===============================================================
            // SECTION B: AI RESPONSE PREFERENCES
            // ===============================================================
            SettingsSectionCard(
              title = stringResource(R.string.settings_section_ai_prefs),
              subtitle = stringResource(R.string.settings_section_ai_prefs_sub),
              icon = Icons.Outlined.Tune,
              testTag = "settings_section_ai_preferences",
            ) {
              // 1. Response Style
              Text(
                text = stringResource(R.string.settings_response_style_label),
                style = MaterialTheme.typography.labelLarge,
                color = glass.textPrimary,
                fontWeight = FontWeight.SemiBold,
              )

              Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                ResponseStyle.entries.forEach { styleOption ->
                  SelectablePreferenceRow(
                    title = styleOption.label,
                    subtitle = styleOption.description,
                    isSelected = preferences.responseStyle == styleOption,
                    onClick = { preferencesRepository.setResponseStyle(styleOption) },
                    testTag = "style_option_${styleOption.name}",
                  )
                }
              }

              GlassDivider()

              // 2. Preferred Response Language
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
              ) {
                Icon(
                  imageVector = Icons.Outlined.Language,
                  contentDescription = null,
                  tint = glass.accentCyan,
                  modifier = Modifier.size(18.dp),
                )
                Text(
                  text = stringResource(R.string.settings_response_language_label),
                  style = MaterialTheme.typography.labelLarge,
                  color = glass.textPrimary,
                  fontWeight = FontWeight.SemiBold,
                )
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
              ) {
                ResponseLanguage.entries.forEach { langOption ->
                  LanguageOptionPill(
                    language = langOption,
                    isSelected = preferences.responseLanguage == langOption,
                    onClick = { preferencesRepository.setResponseLanguage(langOption) },
                    modifier = Modifier.weight(1f).testTag("lang_option_${langOption.name}"),
                  )
                }
              }

              GlassDivider()

              // 2B. Live Web Search Grounding (Google Search)
              SettingsToggleRow(
                title = "Web Search Grounding",
                subtitle = "Fetch live facts and clickable source citations via Google Search when needed",
                icon = Icons.Outlined.Public,
                checked = preferences.webSearchEnabled,
                onCheckedChange = { preferencesRepository.setWebSearchEnabled(it) },
                testTag = "settings_toggle_web_search",
              )

              GlassDivider()

              // 3. Optional Custom Instructions
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = stringResource(R.string.settings_custom_instructions_label),
                  style = MaterialTheme.typography.labelLarge,
                  color = glass.textPrimary,
                  fontWeight = FontWeight.SemiBold,
                )

                val charCount = draftInstructions.length
                val maxChars = UserPreferencesRepository.MAX_CUSTOM_INSTRUCTIONS_LENGTH
                val isOverLimit = charCount > maxChars
                Text(
                  text = "$charCount / $maxChars",
                  style = MaterialTheme.typography.labelSmall,
                  color = if (isOverLimit) glass.statusError else glass.textMuted,
                  modifier =
                    Modifier.semantics {
                        contentDescription = "$charCount of $maxChars characters used"
                      }
                      .testTag("custom_instructions_counter"),
                )
              }

              Text(
                text = stringResource(R.string.settings_custom_instructions_helper),
                style = MaterialTheme.typography.bodySmall,
                color = glass.textSecondary,
              )

              GlassTextField(
                value = draftInstructions,
                onValueChange = { updated ->
                  draftInstructions = updated
                  instructionFeedback = null
                  isInstructionError =
                    updated.trim().length > UserPreferencesRepository.MAX_CUSTOM_INSTRUCTIONS_LENGTH
                },
                placeholder = stringResource(R.string.settings_custom_instructions_placeholder),
                singleLine = false,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth().testTag("custom_instructions_input"),
              )

              if (instructionFeedback != null) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                  modifier =
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                      .testTag("custom_instructions_feedback"),
                ) {
                  Icon(
                    imageVector =
                      if (isInstructionError) Icons.Outlined.WarningAmber
                      else Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = if (isInstructionError) glass.statusError else glass.statusSuccess,
                    modifier = Modifier.size(14.dp),
                  )
                  Text(
                    text = instructionFeedback.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isInstructionError) glass.statusError else glass.statusSuccess,
                  )
                }
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
              ) {
                GlassButton(
                  text = stringResource(R.string.settings_custom_instructions_save),
                  leadingIcon = Icons.Outlined.Check,
                  variant = GlassButtonVariant.PRIMARY,
                  onClick = {
                    val result = preferencesRepository.setCustomInstructions(draftInstructions)
                    result
                      .onSuccess {
                        isInstructionError = false
                        instructionFeedback = "Custom instructions saved for future responses."
                      }
                      .onFailure { err ->
                        isInstructionError = true
                        instructionFeedback = err.message ?: "Exceeded character limit."
                      }
                  },
                  modifier = Modifier.weight(1f).testTag("custom_instructions_save_button"),
                )

                GlassButton(
                  text = stringResource(R.string.settings_custom_instructions_clear),
                  variant = GlassButtonVariant.SECONDARY,
                  enabled = draftInstructions.isNotEmpty() || preferences.customInstructions.isNotEmpty(),
                  onClick = {
                    draftInstructions = ""
                    preferencesRepository.clearCustomInstructions()
                    isInstructionError = false
                    instructionFeedback = "Custom instructions cleared."
                  },
                  modifier = Modifier.testTag("custom_instructions_clear_button"),
                )
              }
            }

            // ===============================================================
            // SECTION C: MODEL SELECTION
            // ===============================================================
            SettingsSectionCard(
              title = stringResource(R.string.settings_section_model),
              subtitle = stringResource(R.string.settings_section_model_sub),
              icon = Icons.Outlined.Memory,
              testTag = "settings_section_model",
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                GeminiModelOption.entries.forEach { modelOption ->
                  ModelOptionCard(
                    modelOption = modelOption,
                    isSelected = preferences.selectedModel == modelOption,
                    onClick = { preferencesRepository.setSelectedModel(modelOption) },
                    testTag = "model_option_${modelOption.modelId}",
                  )
                }
              }
            }

            // ===============================================================
            // SECTION D: GENERAL (About, Help & Troubleshooting, Reset)
            // ===============================================================
            SettingsSectionCard(
              title = stringResource(R.string.settings_section_general),
              subtitle = stringResource(R.string.settings_section_general_sub),
              icon = Icons.Outlined.Info,
              testTag = "settings_section_general",
            ) {
              // 1. Official Astryn AI Brand & About Row (Brand-only attribution)
              Row(
                modifier = Modifier.fillMaxWidth().testTag("settings_about_row"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                AstrynBrandShowcase(
                  versionName = BuildConfig.VERSION_NAME,
                  modifier = Modifier.weight(1f),
                )

                val keyConfigured = apiKeyState.hasSavedKey
                val isKeyConnected = apiKeyState.connectionStatus == ApiConnectionStatus.CONNECTED
                val isKeyError = apiKeyState.connectionStatus.isError
                val badgeColor =
                  when {
                    isKeyConnected -> glass.statusSuccess
                    isKeyError -> glass.statusError
                    keyConfigured -> glass.accentCyan
                    else -> glass.statusWarning
                  }
                val badgeText =
                  when {
                    isKeyConnected -> "Connected"
                    isKeyError -> "API Key Error"
                    keyConfigured -> "API Key Saved"
                    else -> "API Key Needed"
                  }
                Row(
                  modifier =
                    Modifier.clip(AppRadius.shapePill)
                      .background(badgeColor.copy(alpha = 0.16f))
                      .border(
                        AppBorder.hairline,
                        badgeColor,
                        AppRadius.shapePill,
                      )
                      .padding(horizontal = AppSpacing.sm, vertical = 4.dp)
                      .testTag("settings_api_status_badge"),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Icon(
                    imageVector =
                      if (keyConfigured && !isKeyError) Icons.Outlined.CheckCircle
                      else Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(13.dp),
                  )
                  Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeColor,
                    fontWeight = FontWeight.SemiBold,
                  )
                }
              }

              GlassDivider()

              // 2. Help & Troubleshooting
              GlassButton(
                text = stringResource(R.string.settings_help_button_label),
                leadingIcon = Icons.AutoMirrored.Outlined.HelpOutline,
                variant = GlassButtonVariant.SECONDARY,
                onClick = { showHelpDialog = true },
                modifier = Modifier.fillMaxWidth().testTag("settings_help_button"),
              )

              // 3. Clear All Chat History (with Confirmation Dialog)
              if (onClearAllHistory != null) {
                GlassButton(
                  text = stringResource(R.string.settings_clear_history_button_label),
                  leadingIcon = Icons.Outlined.DeleteOutline,
                  variant = GlassButtonVariant.DESTRUCTIVE,
                  onClick = { showClearHistoryConfirmDialog = true },
                  modifier = Modifier.fillMaxWidth().testTag("settings_clear_history_button"),
                )
              }

              // 4. Reset Settings to Default
              GlassButton(
                text = stringResource(R.string.settings_reset_button_label),
                leadingIcon = Icons.Outlined.RestartAlt,
                variant = GlassButtonVariant.DESTRUCTIVE,
                onClick = { showResetConfirmDialog = true },
                modifier = Modifier.fillMaxWidth().testTag("settings_reset_button"),
              )
            }

            Spacer(modifier = Modifier.height(AppSpacing.xl))
          }
        }
      }
    }

    // ---------------------------------------------------------------------
    // Help & Troubleshooting Modal Dialog
    // ---------------------------------------------------------------------
    if (showHelpDialog) {
      AlertDialog(
        onDismissRequest = { showHelpDialog = false },
        modifier = Modifier.testTag("settings_help_dialog"),
        containerColor = glass.surfaceElevated.copy(alpha = 0.98f),
        titleContentColor = glass.textPrimary,
        textContentColor = glass.textSecondary,
        title = {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
              contentDescription = null,
              tint = glass.accentCyan,
            )
            Text(
              text = stringResource(R.string.settings_help_dialog_title),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
          }
        },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            Text(
              text = "• API Configuration: Paste your personal Google AI Studio Gemini API key under 'AI Provider & API Key', tap Save API Key, and tap Test Connection to verify live access.",
              style = MaterialTheme.typography.bodySmall,
              color = glass.textPrimary,
            )
            Text(
              text = "• Personalization: Response Style, Language, Custom Instructions, and Model choices apply to all future messages without altering your saved history.",
              style = MaterialTheme.typography.bodySmall,
              color = glass.textPrimary,
            )
            Text(
              text = "• Local History: Conversations are stored on-device using Room SQLite. Resetting preferences will not delete your saved chats.",
              style = MaterialTheme.typography.bodySmall,
              color = glass.textPrimary,
            )
          }
        },
        confirmButton = {
          GlassButton(
            text = stringResource(R.string.settings_help_dialog_close),
            variant = GlassButtonVariant.PRIMARY,
            onClick = { showHelpDialog = false },
            modifier = Modifier.testTag("settings_help_close_button"),
          )
        },
      )
    }

    // ---------------------------------------------------------------------
    // Reset Settings Confirmation Dialog
    // ---------------------------------------------------------------------
    if (showResetConfirmDialog) {
      AlertDialog(
        onDismissRequest = { showResetConfirmDialog = false },
        modifier = Modifier.testTag("reset_settings_dialog"),
        containerColor = glass.surfaceElevated.copy(alpha = 0.98f),
        titleContentColor = glass.textPrimary,
        textContentColor = glass.textSecondary,
        title = {
          Text(
            text = stringResource(R.string.settings_reset_dialog_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
        },
        text = {
          Text(
            text = stringResource(R.string.settings_reset_dialog_message),
            style = MaterialTheme.typography.bodyMedium,
            color = glass.textSecondary,
          )
        },
        confirmButton = {
          GlassButton(
            text = stringResource(R.string.settings_reset_confirm),
            variant = GlassButtonVariant.DESTRUCTIVE,
            onClick = {
              preferencesRepository.resetToDefaults()
              draftInstructions = ""
              isInstructionError = false
              instructionFeedback = "All settings restored to defaults."
              showResetConfirmDialog = false
            },
            modifier = Modifier.testTag("reset_confirm_button"),
          )
        },
        dismissButton = {
          GlassButton(
            text = stringResource(R.string.dialog_cancel),
            variant = GlassButtonVariant.SUBTLE,
            onClick = { showResetConfirmDialog = false },
            modifier = Modifier.testTag("reset_cancel_button"),
          )
        },
      )
    }

    // ---------------------------------------------------------------------
    // Clear All Chat History Confirmation Dialog
    // ---------------------------------------------------------------------
    if (showClearHistoryConfirmDialog) {
      AlertDialog(
        onDismissRequest = { showClearHistoryConfirmDialog = false },
        modifier = Modifier.testTag("clear_history_dialog"),
        containerColor = glass.surfaceElevated.copy(alpha = 0.98f),
        titleContentColor = glass.textPrimary,
        textContentColor = glass.textSecondary,
        title = {
          Text(
            text = stringResource(R.string.settings_clear_history_dialog_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
        },
        text = {
          Text(
            text = stringResource(R.string.settings_clear_history_dialog_message),
            style = MaterialTheme.typography.bodyMedium,
            color = glass.textSecondary,
          )
        },
        confirmButton = {
          GlassButton(
            text = stringResource(R.string.settings_clear_history_confirm),
            variant = GlassButtonVariant.DESTRUCTIVE,
            onClick = {
              onClearAllHistory?.invoke()
              showClearHistoryConfirmDialog = false
            },
            modifier = Modifier.testTag("clear_history_confirm_button"),
          )
        },
        dismissButton = {
          GlassButton(
            text = stringResource(R.string.dialog_cancel),
            variant = GlassButtonVariant.SUBTLE,
            onClick = { showClearHistoryConfirmDialog = false },
            modifier = Modifier.testTag("clear_history_cancel_button"),
          )
        },
      )
    }
  }
}

@Composable
private fun SettingsSectionCard(
  title: String,
  subtitle: String,
  icon: ImageVector,
  testTag: String,
  content: @Composable () -> Unit,
) {
  val glass = AppThemeTokens.glass
  GlassCard(
    elevated = true,
    modifier = Modifier.fillMaxWidth().testTag(testTag),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
      ) {
        Box(
          modifier =
            Modifier.size(36.dp)
              .clip(CircleShape)
              .background(glass.accentPrimary.copy(alpha = 0.16f))
              .border(AppBorder.hairline, glass.borderAccent, CircleShape),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = glass.accentCyan,
            modifier = Modifier.size(18.dp),
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = glass.textPrimary,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = glass.textSecondary,
          )
        }
      }

      GlassDivider()

      content()
    }
  }
}

@Composable
private fun ThemeModePill(
  label: String,
  icon: ImageVector,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val glass = AppThemeTokens.glass
  val bgColor =
    if (isSelected) glass.accentPrimary.copy(alpha = 0.24f) else glass.surfaceStandard
  val borderColor = if (isSelected) glass.accentCyan else glass.borderSubtle

  Column(
    modifier =
      modifier
        .heightIn(min = 64.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(bgColor)
        .border(
          width = if (isSelected) AppBorder.focused else AppBorder.hairline,
          color = borderColor,
          shape = RoundedCornerShape(14.dp),
        )
        .semantics {
          role = Role.RadioButton
          selected = isSelected
          contentDescription = "$label theme"
        }
        .clickable(onClick = onClick)
        .padding(vertical = AppSpacing.sm, horizontal = AppSpacing.xs),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = if (isSelected) glass.accentCyan else glass.textSecondary,
      modifier = Modifier.size(20.dp),
    )
    Spacer(modifier = Modifier.height(4.dp))
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      if (isSelected) {
        Icon(
          imageVector = Icons.Outlined.Check,
          contentDescription = null,
          tint = glass.accentCyan,
          modifier = Modifier.size(12.dp),
        )
      }
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
        color = if (isSelected) glass.textPrimary else glass.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun SettingsToggleRow(
  title: String,
  subtitle: String,
  icon: ImageVector,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  testTag: String,
) {
  val glass = AppThemeTokens.glass
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .heightIn(min = AppSpacing.minTouchTarget)
        .clip(RoundedCornerShape(12.dp))
        .semantics(mergeDescendants = true) {
          role = Role.Switch
          stateDescription = if (checked) "Enabled" else "Disabled"
        }
        .clickable { onCheckedChange(!checked) }
        .padding(vertical = 4.dp)
        .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(
      modifier = Modifier.weight(1f),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (checked) glass.accentCyan else glass.textSecondary,
        modifier = Modifier.size(20.dp),
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.bodyMedium,
          color = glass.textPrimary,
          fontWeight = FontWeight.Medium,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = glass.textSecondary,
        )
      }
    }

    Spacer(modifier = Modifier.width(AppSpacing.sm))

    Switch(
      checked = checked,
      onCheckedChange = null,
      colors =
        SwitchDefaults.colors(
          checkedThumbColor = Color.White,
          checkedTrackColor = glass.accentPrimary,
          uncheckedThumbColor = glass.textSecondary,
          uncheckedTrackColor = glass.surfaceStandard,
        ),
    )
  }
}

@Composable
private fun SelectablePreferenceRow(
  title: String,
  subtitle: String,
  isSelected: Boolean,
  onClick: () -> Unit,
  testTag: String,
) {
  val glass = AppThemeTokens.glass
  val bgColor =
    if (isSelected) glass.accentPrimary.copy(alpha = 0.20f) else glass.surfaceStandard
  val borderColor = if (isSelected) glass.accentCyan else glass.borderSubtle

  Row(
    modifier =
      Modifier.fillMaxWidth()
        .heightIn(min = AppSpacing.minTouchTarget)
        .clip(RoundedCornerShape(12.dp))
        .background(bgColor)
        .border(
          width = if (isSelected) AppBorder.focused else AppBorder.hairline,
          color = borderColor,
          shape = RoundedCornerShape(12.dp),
        )
        .semantics {
          role = Role.RadioButton
          selected = isSelected
        }
        .clickable(onClick = onClick)
        .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
        .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
        color = glass.textPrimary,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = glass.textSecondary,
      )
    }

    if (isSelected) {
      Icon(
        imageVector = Icons.Outlined.CheckCircle,
        contentDescription = "Selected",
        tint = glass.accentCyan,
        modifier = Modifier.size(20.dp),
      )
    }
  }
}

@Composable
private fun LanguageOptionPill(
  language: ResponseLanguage,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val glass = AppThemeTokens.glass
  val bgColor =
    if (isSelected) glass.accentPrimary.copy(alpha = 0.24f) else glass.surfaceStandard
  val borderColor = if (isSelected) glass.accentCyan else glass.borderSubtle

  Column(
    modifier =
      modifier
        .heightIn(min = 56.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(bgColor)
        .border(
          width = if (isSelected) AppBorder.focused else AppBorder.hairline,
          color = borderColor,
          shape = RoundedCornerShape(12.dp),
        )
        .semantics {
          role = Role.RadioButton
          selected = isSelected
          contentDescription = "${language.label} response language"
        }
        .clickable(onClick = onClick)
        .padding(horizontal = AppSpacing.xs, vertical = AppSpacing.sm),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      if (isSelected) {
        Icon(
          imageVector = Icons.Outlined.Check,
          contentDescription = null,
          tint = glass.accentCyan,
          modifier = Modifier.size(12.dp),
        )
      }
      Text(
        text = language.label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
        color = if (isSelected) glass.textPrimary else glass.textSecondary,
        maxLines = 1,
      )
    }
    Text(
      text = language.subtitle,
      style = MaterialTheme.typography.labelSmall,
      color = if (isSelected) glass.accentCyan else glass.textMuted,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun ModelOptionCard(
  modelOption: GeminiModelOption,
  isSelected: Boolean,
  onClick: () -> Unit,
  testTag: String,
) {
  val glass = AppThemeTokens.glass
  val bgColor =
    if (isSelected) glass.accentPrimary.copy(alpha = 0.20f) else glass.surfaceStandard
  val borderColor = if (isSelected) glass.accentCyan else glass.borderSubtle

  Row(
    modifier =
      Modifier.fillMaxWidth()
        .heightIn(min = 64.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(bgColor)
        .border(
          width = if (isSelected) AppBorder.focused else AppBorder.hairline,
          color = borderColor,
          shape = RoundedCornerShape(14.dp),
        )
        .semantics {
          role = Role.RadioButton
          selected = isSelected
        }
        .clickable(onClick = onClick)
        .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
        .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
      ) {
        Icon(
          imageVector = Icons.Outlined.AutoAwesome,
          contentDescription = null,
          tint = if (isSelected) glass.accentCyan else glass.textSecondary,
          modifier = Modifier.size(16.dp),
        )
        Text(
          text = modelOption.displayName,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Bold,
          color = glass.textPrimary,
        )
        Box(
          modifier =
            Modifier.clip(AppRadius.shapePill)
              .background(glass.accentSecondary.copy(alpha = 0.22f))
              .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
          Text(
            text = modelOption.badge,
            style = MaterialTheme.typography.labelSmall,
            color = glass.accentCyan,
          )
        }
      }

      Text(
        text = modelOption.description,
        style = MaterialTheme.typography.bodySmall,
        color = glass.textSecondary,
      )
    }

    Spacer(modifier = Modifier.width(AppSpacing.sm))

    if (isSelected) {
      Icon(
        imageVector = Icons.Outlined.CheckCircle,
        contentDescription = "Selected model",
        tint = glass.accentCyan,
        modifier = Modifier.size(22.dp),
      )
    }
  }
}

@Composable
private fun ChatBackgroundOptionCard(
  bgStyle: ChatBackgroundStyle,
  isSelected: Boolean,
  onClick: () -> Unit,
  testTag: String,
) {
  val glass = AppThemeTokens.glass
  val (primaryDot, secondaryDot) =
    remember(bgStyle) {
      when (bgStyle) {
        ChatBackgroundStyle.LIQUID_AURORA -> Color(0xFF00F2FE) to Color(0xFF7C3AED)
        ChatBackgroundStyle.COSMIC_NEBULA -> Color(0xFF6366F1) to Color(0xFFEC4899)
        ChatBackgroundStyle.AMOLED_PITCH -> Color(0xFF0A0E17) to Color(0xFF00F2FE)
        ChatBackgroundStyle.ROMANTIC_VELVET -> Color(0xFFFF2A85) to Color(0xFFA855F7)
        ChatBackgroundStyle.JARVIS_CYBER_HUD -> Color(0xFF00F2FE) to Color(0xFF0EA5E9)
      }
    }

  val bgColor =
    if (isSelected) primaryDot.copy(alpha = 0.16f) else glass.surfaceStandard
  val borderColor = if (isSelected) primaryDot else glass.borderSubtle

  Row(
    modifier =
      Modifier.fillMaxWidth()
        .heightIn(min = 60.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(bgColor)
        .border(
          width = if (isSelected) AppBorder.focused else AppBorder.hairline,
          color = borderColor,
          shape = RoundedCornerShape(14.dp),
        )
        .semantics {
          role = Role.RadioButton
          selected = isSelected
        }
        .clickable(onClick = onClick)
        .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
        .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(
      modifier = Modifier.weight(1f),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
      // Live dual-tone swatch preview
      Box(
        modifier =
          Modifier.size(34.dp)
            .clip(CircleShape)
            .background(primaryDot.copy(alpha = 0.25f))
            .border(
              width = 1.5.dp,
              color = if (isSelected) primaryDot else glass.borderSubtle,
              shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
          Box(
            modifier =
              Modifier.size(13.dp)
                .clip(CircleShape)
                .background(primaryDot),
          )
          Box(
            modifier =
              Modifier.size(13.dp)
                .clip(CircleShape)
                .background(secondaryDot),
          )
        }
      }

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = bgStyle.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
            color = glass.textPrimary,
          )
          Box(
            modifier =
              Modifier.clip(AppRadius.shapePill)
                .background(secondaryDot.copy(alpha = 0.20f))
                .padding(horizontal = 7.dp, vertical = 1.dp),
          ) {
            Text(
              text = bgStyle.badge,
              style = MaterialTheme.typography.labelSmall,
              color = if (isSelected) primaryDot else glass.textSecondary,
            )
          }
        }
        Text(
          text = bgStyle.subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = glass.textSecondary,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

    Spacer(modifier = Modifier.width(AppSpacing.xs))

    if (isSelected) {
      Icon(
        imageVector = Icons.Outlined.CheckCircle,
        contentDescription = "Selected background style",
        tint = glass.accentCyan,
        modifier = Modifier.size(20.dp),
      )
    }
  }
}

