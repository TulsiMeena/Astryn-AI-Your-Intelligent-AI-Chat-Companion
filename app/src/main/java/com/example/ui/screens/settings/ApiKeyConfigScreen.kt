package com.example.ui.screens.settings

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.api.GeminiService
import com.example.data.local.ApiConnectionStatus
import com.example.data.local.UserApiKeyManager
import com.example.data.repository.GeminiModelOption
import com.example.data.repository.UserPreferencesRepository
import com.example.ui.components.AmbientGlassBackground
import com.example.ui.components.AstrynLogoEmblem
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassButtonVariant
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassDivider
import com.example.ui.components.GlassIconButton
import com.example.ui.components.GlassTextField
import com.example.ui.theme.AppBorder
import com.example.ui.theme.AppRadius
import com.example.ui.theme.AppSpacing
import com.example.ui.theme.AppThemeTokens
import kotlinx.coroutines.launch

/**
 * Dedicated full-screen API Configuration Screen ("AI Provider & API Key") in the Astryn AI
 * dark navy glassmorphism style with cyan and violet accents.
 */
@Composable
fun ApiKeyConfigScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  apiKeyManager: UserApiKeyManager = UserApiKeyManager.getInstance(),
  preferencesRepository: UserPreferencesRepository = UserPreferencesRepository.getInstance(),
  geminiService: GeminiService = remember { GeminiService() },
) {
  val glass = AppThemeTokens.glass
  val scrollState = rememberScrollState()

  AmbientGlassBackground(modifier = modifier.testTag("api_key_config_screen")) {
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
        // Top Header Bar
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.sm)
              .testTag("api_config_top_bar"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
          ) {
            GlassIconButton(
              icon = Icons.AutoMirrored.Outlined.ArrowBack,
              contentDescription = "Back",
              onClick = onBack,
              modifier = Modifier.testTag("api_config_back_button"),
            )
            AstrynLogoEmblem(
              size = 34.dp,
              contentDescription = "Astryn AI logo",
            )
            Column {
              Text(
                text = "AI Provider & API Key",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = glass.textPrimary,
              )
              Text(
                text = "User-Owned Google Gemini Configuration",
                style = MaterialTheme.typography.labelSmall,
                color = glass.accentCyan,
              )
            }
          }
        }

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
            AiProviderApiKeyPanel(
              apiKeyManager = apiKeyManager,
              preferencesRepository = preferencesRepository,
              geminiService = geminiService,
              onOpenDedicatedScreen = null,
            )

            Spacer(modifier = Modifier.height(AppSpacing.xl))
          }
        }
      }
    }
  }
}

/**
 * Reusable Astryn AI "AI Provider & API Key" glass panel used both in the dedicated
 * [ApiKeyConfigScreen] and under Settings (`SettingsScreen`).
 *
 * Includes:
 * - Provider: Google Gemini
 * - API key input field with Show/Hide toggle (never exposes full saved key after saving)
 * - Save API Key button (saves only when explicitly clicked)
 * - Test Connection button (runs a real minimal request to the selected Gemini model)
 * - Clear/Replace API Key option
 * - Real-time connection status & diagnostic message
 * - Verified Gemini model selector (`gemini-2.5-flash`, `gemini-2.5-pro`, `gemini-2.5-flash-lite`)
 * - Setup instructions & official Google AI Studio link (`https://aistudio.google.com/app/apikey`)
 * - Honest Android Keystore security explanation
 */
@Composable
fun AiProviderApiKeyPanel(
  apiKeyManager: UserApiKeyManager = UserApiKeyManager.getInstance(),
  preferencesRepository: UserPreferencesRepository = UserPreferencesRepository.getInstance(),
  geminiService: GeminiService = remember { GeminiService() },
  onOpenDedicatedScreen: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val glass = AppThemeTokens.glass
  val configState by apiKeyManager.configStateFlow.collectAsState()
  val preferences by preferencesRepository.preferencesFlow.collectAsState()
  val coroutineScope = rememberCoroutineScope()
  val uriHandler = LocalUriHandler.current

  var draftApiKey by rememberSaveable { mutableStateOf("") }
  var isDraftKeyVisible by rememberSaveable { mutableStateOf(false) }
  var localActionNotice by rememberSaveable { mutableStateOf<String?>(null) }
  var isLocalActionError by rememberSaveable { mutableStateOf(false) }

  val liveFormatValidation =
    remember(draftApiKey) {
      if (draftApiKey.isBlank()) null else UserApiKeyManager.validateKeyFormat(draftApiKey)
    }

  GlassCard(
    elevated = true,
    modifier = modifier.fillMaxWidth().testTag("settings_section_api_key"),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
      // -------------------------------------------------------------------
      // 1. Header & Optional Dedicated Screen Entry Button
      // -------------------------------------------------------------------
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          modifier = Modifier.weight(1f),
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
              imageVector = Icons.Outlined.Key,
              contentDescription = null,
              tint = glass.accentCyan,
              modifier = Modifier.size(18.dp),
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "AI Provider & API Key",
              style = MaterialTheme.typography.titleSmall,
              color = glass.textPrimary,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Configure and verify your personal Google Gemini API key",
              style = MaterialTheme.typography.bodySmall,
              color = glass.textSecondary,
            )
          }
        }

        if (onOpenDedicatedScreen != null) {
          Spacer(modifier = Modifier.width(AppSpacing.xs))
          GlassButton(
            text = "Open Setup",
            variant = GlassButtonVariant.SECONDARY,
            onClick = onOpenDedicatedScreen,
            modifier = Modifier.testTag("settings_ai_provider_entry_button"),
          )
        }
      }

      GlassDivider()

      // -------------------------------------------------------------------
      // 2. Provider Badge & Live Connection Status Pill
      // -------------------------------------------------------------------
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = "AI PROVIDER",
            style = MaterialTheme.typography.labelSmall,
            color = glass.textMuted,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = configState.providerName,
            style = MaterialTheme.typography.bodyMedium,
            color = glass.textPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("api_provider_name"),
          )
        }

        val status = configState.connectionStatus
        val (badgeBg, badgeBorder, badgeTextTint) =
          when {
            status == ApiConnectionStatus.CONNECTED ->
              Triple(
                glass.statusSuccess.copy(alpha = 0.16f),
                glass.statusSuccess,
                glass.statusSuccess,
              )
            status == ApiConnectionStatus.SAVED_UNTESTED ||
              status == ApiConnectionStatus.TESTING ->
              Triple(
                glass.accentPrimary.copy(alpha = 0.16f),
                glass.accentCyan,
                glass.accentCyan,
              )
            status.isError ->
              Triple(
                glass.statusError.copy(alpha = 0.16f),
                glass.statusError,
                glass.statusError,
              )
            else ->
              Triple(
                glass.statusWarning.copy(alpha = 0.16f),
                glass.statusWarning,
                glass.statusWarning,
              )
          }

        Row(
          modifier =
            Modifier.clip(AppRadius.shapePill)
              .background(badgeBg)
              .border(AppBorder.hairline, badgeBorder, AppRadius.shapePill)
              .padding(horizontal = AppSpacing.sm, vertical = 5.dp)
              .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
              .testTag("api_connection_status_badge"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          when {
            status == ApiConnectionStatus.TESTING -> {
              CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                color = badgeTextTint,
                strokeWidth = 1.6.dp,
              )
            }
            status == ApiConnectionStatus.CONNECTED -> {
              Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = badgeTextTint,
                modifier = Modifier.size(14.dp),
              )
            }
            status.isError -> {
              Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = badgeTextTint,
                modifier = Modifier.size(14.dp),
              )
            }
            else -> {
              Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = badgeTextTint,
                modifier = Modifier.size(14.dp),
              )
            }
          }
          Text(
            text = status.label,
            style = MaterialTheme.typography.labelSmall,
            color = badgeTextTint,
            fontWeight = FontWeight.Bold,
          )
        }
      }

      // -------------------------------------------------------------------
      // 3. Masked Saved Key Card (Never exposes full saved API key)
      // -------------------------------------------------------------------
      if (configState.hasSavedKey && !configState.maskedKeyPreview.isNullOrBlank()) {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(glass.surfaceStandard)
              .border(AppBorder.hairline, glass.borderAccent, RoundedCornerShape(12.dp))
              .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
              .testTag("api_saved_key_card"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
          ) {
            Icon(
              imageVector = Icons.Outlined.Lock,
              contentDescription = null,
              tint = glass.accentCyan,
              modifier = Modifier.size(18.dp),
            )
            Column {
              Text(
                text = "Saved Key (Masked • Encrypted at Rest)",
                style = MaterialTheme.typography.labelSmall,
                color = glass.textSecondary,
              )
              Text(
                text = configState.maskedKeyPreview.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = glass.textPrimary,
                modifier = Modifier.testTag("api_saved_key_masked_text"),
              )
            }
          }

          Text(
            text = "AES-256-GCM",
            style = MaterialTheme.typography.labelSmall,
            color = glass.accentCyan,
            fontWeight = FontWeight.Medium,
          )
        }
      }

      if (!configState.hasSavedKey && GeminiService.hasBuiltInPlatformKey()) {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(glass.accentPrimary.copy(alpha = 0.14f))
              .border(AppBorder.hairline, glass.accentCyan.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
              .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
              .testTag("api_builtin_key_active_banner"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
          Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = glass.accentCyan,
            modifier = Modifier.size(18.dp),
          )
          Text(
            text =
              "Built-in AI Studio API Active — Astryn AI is ready to chat immediately. When creating or sharing a standalone app, you can enter a separate Gemini API key below.",
            style = MaterialTheme.typography.bodySmall,
            color = glass.textPrimary,
            modifier = Modifier.weight(1f),
          )
        }
      }

      // -------------------------------------------------------------------
      // 4. Connection Status Detail / Error Diagnostic Banner
      // -------------------------------------------------------------------
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
              when {
                configState.connectionStatus == ApiConnectionStatus.CONNECTED ->
                  glass.statusSuccess.copy(alpha = 0.12f)
                configState.connectionStatus.isError ->
                  glass.statusError.copy(alpha = 0.12f)
                else -> glass.surfaceStandard.copy(alpha = 0.65f)
              }
            )
            .border(
              AppBorder.hairline,
              when {
                configState.connectionStatus == ApiConnectionStatus.CONNECTED ->
                  glass.statusSuccess.copy(alpha = 0.5f)
                configState.connectionStatus.isError ->
                  glass.statusError.copy(alpha = 0.5f)
                else -> glass.borderSubtle
              },
              RoundedCornerShape(12.dp),
            )
            .padding(AppSpacing.sm)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .testTag("api_connection_status_detail"),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
      ) {
        Icon(
          imageVector =
            when {
              configState.connectionStatus == ApiConnectionStatus.CONNECTED ->
                Icons.Outlined.CheckCircle
              configState.connectionStatus.isError -> Icons.Outlined.WarningAmber
              else -> Icons.Outlined.Info
            },
          contentDescription = null,
          tint =
            when {
              configState.connectionStatus == ApiConnectionStatus.CONNECTED -> glass.statusSuccess
              configState.connectionStatus.isError -> glass.statusError
              else -> glass.accentCyan
            },
          modifier = Modifier.size(18.dp).padding(top = 1.dp),
        )
        Text(
          text = configState.statusDetailMessage,
          style = MaterialTheme.typography.bodySmall,
          color = glass.textPrimary,
          modifier = Modifier.weight(1f),
        )
      }

      // -------------------------------------------------------------------
      // 5. API Key Input Field + Show/Hide Toggle
      // -------------------------------------------------------------------
      Text(
        text =
          if (configState.hasSavedKey) "Replace Gemini API Key"
          else "Enter Your Gemini API Key (Optional Override)",
        style = MaterialTheme.typography.labelLarge,
        color = glass.textPrimary,
        fontWeight = FontWeight.SemiBold,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
      ) {
        GlassTextField(
          value = draftApiKey,
          onValueChange = { updated ->
            draftApiKey = updated
            localActionNotice = null
            isLocalActionError = false
          },
          placeholder =
            if (configState.hasSavedKey) {
              "Paste a new Gemini API key (AIza…) to replace saved key"
            } else {
              "Paste your Gemini API key (AIza…)"
            },
          singleLine = true,
          maxLines = 1,
          visualTransformation =
            if (isDraftKeyVisible) VisualTransformation.None
            else PasswordVisualTransformation(),
          keyboardOptions =
            KeyboardOptions(
              capitalization = KeyboardCapitalization.None,
              autoCorrectEnabled = false,
              keyboardType = KeyboardType.Password,
              imeAction = ImeAction.Done,
            ),
          modifier = Modifier.weight(1f).testTag("api_key_input_field"),
        )

        GlassIconButton(
          icon =
            if (isDraftKeyVisible) Icons.Outlined.VisibilityOff
            else Icons.Outlined.Visibility,
          contentDescription =
            if (isDraftKeyVisible) "Hide API key input" else "Show API key input",
          isActive = isDraftKeyVisible,
          onClick = { isDraftKeyVisible = !isDraftKeyVisible },
          modifier = Modifier.testTag("api_key_visibility_toggle"),
        )
      }

      // Format validation or action feedback
      val feedbackToShow = localActionNotice ?: liveFormatValidation?.feedbackMessage
      val isFeedbackError =
        if (localActionNotice != null) isLocalActionError
        else (liveFormatValidation?.isValidFormat == false)

      if (!feedbackToShow.isNullOrBlank()) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier =
            Modifier.semantics { liveRegion = LiveRegionMode.Polite }
              .testTag("api_key_format_feedback"),
        ) {
          Icon(
            imageVector =
              if (isFeedbackError) Icons.Outlined.WarningAmber
              else Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = if (isFeedbackError) glass.statusError else glass.accentCyan,
            modifier = Modifier.size(15.dp),
          )
          Text(
            text = feedbackToShow,
            style = MaterialTheme.typography.labelSmall,
            color = if (isFeedbackError) glass.statusError else glass.textSecondary,
          )
        }
      }

      // -------------------------------------------------------------------
      // 6. Save API Key, Test Connection, and Clear/Replace Controls
      // -------------------------------------------------------------------
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
      ) {
        GlassButton(
          text = "Save API Key",
          leadingIcon = Icons.Outlined.Save,
          variant = GlassButtonVariant.PRIMARY,
          enabled =
            draftApiKey.trim().isNotEmpty() &&
              configState.connectionStatus != ApiConnectionStatus.TESTING,
          onClick = {
            val result = apiKeyManager.saveApiKey(draftApiKey)
            result
              .onSuccess { savedState ->
                // Clear plaintext draft from the input box so the full saved key is never exposed
                draftApiKey = ""
                isDraftKeyVisible = false
                isLocalActionError = false
                localActionNotice =
                  "Key saved (${savedState.maskedKeyPreview}). Tap 'Test Connection' to verify live Gemini access."
              }
              .onFailure { err ->
                isLocalActionError = true
                localActionNotice = err.message ?: "Invalid API key format."
              }
          },
          modifier = Modifier.weight(1f).testTag("api_key_save_button"),
        )

        GlassButton(
          text = "Test Connection",
          leadingIcon = Icons.Outlined.NetworkCheck,
          variant = GlassButtonVariant.SECONDARY,
          isLoading = configState.connectionStatus == ApiConnectionStatus.TESTING,
          enabled =
            (configState.hasSavedKey ||
              draftApiKey.trim().isNotEmpty() ||
              GeminiService.hasBuiltInPlatformKey()) &&
              configState.connectionStatus != ApiConnectionStatus.TESTING,
          onClick = {
            val candidateKey =
              if (draftApiKey.trim().isNotEmpty()) {
                val formatCheck = UserApiKeyManager.validateKeyFormat(draftApiKey.trim())
                if (!formatCheck.isValidFormat) {
                  isLocalActionError = true
                  localActionNotice = formatCheck.feedbackMessage
                  return@GlassButton
                }
                draftApiKey.trim()
              } else {
                apiKeyManager.getDecryptedApiKey()?.takeIf { it.isNotBlank() }
                  ?: GeminiService.getBuiltInPlatformKey()
              }
            val targetModelId = preferences.selectedModel.modelId
            apiKeyManager.markTesting(targetModelId)
            localActionNotice = null
            coroutineScope.launch {
              val testResult =
                geminiService.testConnection(
                  apiKeyOverride = candidateKey,
                  model = targetModelId,
                )
              testResult
                .onSuccess {
                  apiKeyManager.recordConnectionSuccess(targetModelId)
                  isLocalActionError = false
                  localActionNotice =
                    "Connection verified! Live response received from $targetModelId."
                }
                .onFailure { err ->
                  apiKeyManager.recordConnectionFailure(err, targetModelId)
                  isLocalActionError = true
                  localActionNotice =
                    "Connection test failed. Review the diagnostic message above or replace your key."
                }
            }
          },
          modifier = Modifier.weight(1f).testTag("api_key_test_connection_button"),
        )
      }

      if (configState.hasSavedKey || draftApiKey.isNotEmpty()) {
        GlassButton(
          text = "Clear / Remove Saved API Key",
          leadingIcon = Icons.Outlined.DeleteOutline,
          variant = GlassButtonVariant.DESTRUCTIVE,
          enabled = configState.connectionStatus != ApiConnectionStatus.TESTING,
          onClick = {
            draftApiKey = ""
            isDraftKeyVisible = false
            apiKeyManager.clearApiKey()
            isLocalActionError = false
            localActionNotice = "Saved API key cleared and configuration reset."
          },
          modifier = Modifier.fillMaxWidth().testTag("api_key_clear_button"),
        )
      }

      GlassDivider()

      // -------------------------------------------------------------------
      // 7.Current Model Selector (Verified Supported Gemini Models Only)
      // -------------------------------------------------------------------
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
      ) {
        Icon(
          imageVector = Icons.Outlined.Memory,
          contentDescription = null,
          tint = glass.accentCyan,
          modifier = Modifier.size(18.dp),
        )
        Column {
          Text(
            text = "Current Gemini Model",
            style = MaterialTheme.typography.labelLarge,
            color = glass.textPrimary,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = "Only verified, supported Google Gemini v1beta models are selectable",
            style = MaterialTheme.typography.bodySmall,
            color = glass.textSecondary,
          )
        }
      }

      Column(
        modifier = Modifier.fillMaxWidth().testTag("api_key_model_selector"),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
      ) {
        GeminiModelOption.entries.forEach { modelOption ->
          val isSelected = preferences.selectedModel == modelOption
          val bgColor =
            if (isSelected) glass.accentPrimary.copy(alpha = 0.20f) else glass.surfaceStandard
          val borderColor = if (isSelected) glass.accentCyan else glass.borderSubtle

          Row(
            modifier =
              Modifier.fillMaxWidth()
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
                }
                .clickable { preferencesRepository.setSelectedModel(modelOption) }
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
                .testTag("api_model_option_${modelOption.modelId}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
              ) {
                Text(
                  text = modelOption.displayName,
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                  color = glass.textPrimary,
                )
                Text(
                  text = modelOption.badge,
                  style = MaterialTheme.typography.labelSmall,
                  color = glass.accentCyan,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              Text(
                text = "ID: ${modelOption.modelId}",
                style = MaterialTheme.typography.labelSmall,
                color = glass.textMuted,
              )
            }

            if (isSelected) {
              Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = "Selected model",
                tint = glass.accentCyan,
                modifier = Modifier.size(20.dp),
              )
            }
          }
        }
      }

      // Google Search Web Grounding Toggle
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
              if (preferences.webSearchEnabled) glass.accentPrimary.copy(alpha = 0.14f)
              else glass.surfaceStandard
            )
            .border(
              width = AppBorder.hairline,
              color = if (preferences.webSearchEnabled) glass.accentCyan else glass.borderSubtle,
              shape = RoundedCornerShape(12.dp),
            )
            .clickable {
              preferencesRepository.setWebSearchEnabled(!preferences.webSearchEnabled)
            }
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
            .testTag("settings_web_search_toggle"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Google Search Grounding (Live Web Info)",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = glass.textPrimary,
          )
          Text(
            text =
              "Automatically grounds time-sensitive or factual queries with real Google Search sources and citations when online",
            style = MaterialTheme.typography.labelSmall,
            color = glass.textSecondary,
          )
        }
        Spacer(modifier = Modifier.width(AppSpacing.sm))
        Text(
          text = if (preferences.webSearchEnabled) "ENABLED" else "OFF",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = if (preferences.webSearchEnabled) glass.accentCyan else glass.textMuted,
        )
      }

      GlassDivider()

      // -------------------------------------------------------------------
      // 8. Setup Instructions & Official Google AI Studio Link
      // -------------------------------------------------------------------
      Column(
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        modifier = Modifier.fillMaxWidth().testTag("api_key_setup_instructions"),
      ) {
        Text(
          text = "How to Get Your Personal Gemini API Key",
          style = MaterialTheme.typography.labelLarge,
          color = glass.textPrimary,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = "1. Open the official Google AI Studio API key page below and sign in with your Google account.",
          style = MaterialTheme.typography.bodySmall,
          color = glass.textSecondary,
        )
        Text(
          text = "2. Tap 'Create API key', copy your key (starts with AIza…), and paste it into the field above.",
          style = MaterialTheme.typography.bodySmall,
          color = glass.textSecondary,
        )
        Text(
          text = "3. Tap 'Save API Key', then tap 'Test Connection' to confirm live access with your selected Gemini model.",
          style = MaterialTheme.typography.bodySmall,
          color = glass.textSecondary,
        )

        Spacer(modifier = Modifier.height(4.dp))

        GlassButton(
          text = "Get API Key in Google AI Studio",
          leadingIcon = Icons.AutoMirrored.Outlined.OpenInNew,
          variant = GlassButtonVariant.SECONDARY,
          onClick = {
            try {
              uriHandler.openUri(UserApiKeyManager.OFFICIAL_AI_STUDIO_KEY_URL)
            } catch (_: Exception) {
              localActionNotice =
                "Visit ${UserApiKeyManager.OFFICIAL_AI_STUDIO_KEY_URL} in your browser to create a key."
            }
          },
          modifier = Modifier.fillMaxWidth().testTag("api_key_open_aistudio_link"),
        )
      }

      // -------------------------------------------------------------------
      // 9. Honest Android Keystore Security Note
      // -------------------------------------------------------------------
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(glass.surfaceStandard.copy(alpha = 0.5f))
            .padding(AppSpacing.sm)
            .testTag("api_key_security_notice"),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
      ) {
        Icon(
          imageVector = Icons.Outlined.Lock,
          contentDescription = null,
          tint = glass.accentCyan,
          modifier = Modifier.size(15.dp).padding(top = 2.dp),
        )
        Text(
          text =
            "Security Note: Your API key is encrypted at rest using Android Keystore AES-256-GCM and sent exclusively via the x-goog-api-key HTTPS header directly to Google Gemini. While local encryption protects keys at rest on your device, client-side storage cannot make a key impossible to extract on a rooted or compromised device.",
          style = MaterialTheme.typography.labelSmall,
          color = glass.textMuted,
        )
      }
    }
  }
}
