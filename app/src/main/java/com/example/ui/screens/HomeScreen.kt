package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.data.repository.UserPreferencesRepository
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.AiOrb
import com.example.ui.components.AiOrbState
import com.example.ui.components.AmbientGlassBackground
import com.example.ui.components.AstrynLogoEmblem
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
import com.example.ui.theme.AppThemeTokens

/**
 * Starter suggestion data model for high-value user entry points.
 */
@Immutable
data class StarterSuggestion(
  val id: String,
  val title: String,
  val subtitle: String,
  val prompt: String,
  val icon: ImageVector,
)

/**
 * Ultra-premium, production-oriented Home Screen for Nova AI Chat.
 *
 * Architecture & Composition:
 * - Top Navigation Bar: Corner menu trigger (ready for sidebar drawer), title, Explore and Settings icons.
 * - Hero Area: Centerpiece 2.5D visual [AiOrb] with luminous blue/cyan/violet corona and particle dust.
 * - Status Pill: Real-time intelligence online indicator.
 * - Welcome Typography: High-contrast, clean headline and supporting text.
 * - Quick Input Composer: Immediate conversation starter field that passes drafted text directly to Chat.
 * - Starter Suggestions: Four curated capability cards that open the real Chat interface with pre-filled prompts.
 */
@Composable
fun HomeScreen(
  onStartNewChat: (initialPrompt: String?) -> Unit,
  onOpenMenu: () -> Unit,
  onOpenExplore: () -> Unit,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier,
  onOpenAuth: () -> Unit = {},
) {
  val glass = AppThemeTokens.glass
  val preferences by UserPreferencesRepository.getInstance().preferencesFlow.collectAsState()
  var quickPromptText by rememberSaveable { mutableStateOf("") }

  val suggestions =
    remember {
      listOf(
        StarterSuggestion(
          id = "explain",
          title = "Explain a concept",
          subtitle = "Break down complex topics with simple, intuitive analogies.",
          prompt = "Explain how neural networks learn in simple, intuitive terms with a practical analogy.",
          icon = Icons.Outlined.Lightbulb,
        ),
        StarterSuggestion(
          id = "write",
          title = "Help me write",
          subtitle = "Draft emails, essays, code, or structured documents.",
          prompt = "Help me draft a concise, persuasive product launch email to executive stakeholders.",
          icon = Icons.Outlined.EditNote,
        ),
        StarterSuggestion(
          id = "brainstorm",
          title = "Brainstorm ideas",
          subtitle = "Generate novel feature concepts, names, and strategies.",
          prompt = "Brainstorm 5 innovative, user-friendly features for a mobile AI productivity app.",
          icon = Icons.Outlined.Psychology,
        ),
        StarterSuggestion(
          id = "summarize",
          title = "Summarize text",
          subtitle = "Extract core bullet points from lengthy notes or articles.",
          prompt = "Summarize the key takeaways and actionable next steps from these meeting notes:\n- ",
          icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
        ),
      )
    }

  AmbientGlassBackground(modifier = modifier.testTag("home_screen_background")) {
    Scaffold(
      modifier = Modifier.fillMaxSize(),
      containerColor = Color.Transparent,
      contentWindowInsets = WindowInsets.systemBars,
    ) { innerPadding ->
      Box(
        modifier =
          Modifier.fillMaxSize()
            .padding(innerPadding)
            .imePadding(),
        contentAlignment = Alignment.TopCenter,
      ) {
        Column(
          modifier =
            Modifier.fillMaxWidth()
              .widthIn(max = AppSpacing.maxContentWidth)
              .verticalScroll(rememberScrollState())
              .padding(
                horizontal = AppSpacing.screenHorizontal,
                vertical = AppSpacing.screenVertical,
              ),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(AppSpacing.xl),
        ) {
          // -----------------------------------------------------------------
          // 1. Top Navigation Bar (Corner Menu + Branding + Actions)
          // -----------------------------------------------------------------
          Row(
            modifier = Modifier.fillMaxWidth().testTag("home_top_bar"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
              GlassIconButton(
                icon = Icons.Outlined.Menu,
                contentDescription = stringResource(R.string.home_menu_desc),
                onClick = onOpenMenu,
                modifier = Modifier.testTag("home_menu_button"),
              )

              AstrynLogoEmblem(
                size = 36.dp,
                contentDescription = "Astryn AI logo",
                modifier = Modifier.testTag("home_brand_logo"),
              )

              Column {
                Text(
                  text = stringResource(R.string.app_name),
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = glass.textPrimary,
                )
                Text(
                  text = "Astryn Intelligence",
                  style = MaterialTheme.typography.labelSmall,
                  color = glass.accentCyan,
                )
              }
            }

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
            ) {
              // User Profile Pill (shows selected profile image / 3D avatar & opens UserProfileScreen)
              Row(
                modifier =
                  Modifier.clip(AppRadius.shapePill)
                    .background(
                      if (preferences.isLoggedIn) {
                        glass.accentPrimary.copy(alpha = 0.22f)
                      } else {
                        glass.surfaceStandard
                      },
                    )
                    .border(
                      width = AppBorder.hairline,
                      color = if (preferences.isLoggedIn) glass.accentCyan else glass.borderAccent,
                      shape = AppRadius.shapePill,
                    )
                    .clickable(onClick = onOpenAuth)
                    .padding(horizontal = 8.dp, vertical = 5.dp)
                    .testTag("home_auth_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                if (preferences.isLoggedIn) {
                  com.example.ui.screens.profile.UserAvatarBadge(
                    name = preferences.userName,
                    email = preferences.userEmail,
                    avatarPath = preferences.userAvatarPath,
                    avatarPresetId = preferences.userAvatarPreset,
                    size = 24.dp,
                  )
                } else {
                  Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = "My Profile",
                    tint = glass.accentCyan,
                    modifier = Modifier.size(16.dp),
                  )
                }
                Text(
                  text =
                    if (preferences.isLoggedIn) {
                      preferences.userName.substringBefore(" ").ifBlank { "My Profile" }
                    } else {
                      "Sign In"
                    },
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = glass.textPrimary,
                )
              }

              GlassIconButton(
                icon = Icons.Outlined.Explore,
                contentDescription = stringResource(R.string.home_explore_desc),
                onClick = onOpenExplore,
                modifier = Modifier.testTag("home_explore_button"),
              )
              GlassIconButton(
                icon = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.home_settings_desc),
                onClick = onOpenSettings,
                modifier = Modifier.testTag("home_settings_button"),
              )
            }
          }

          // -----------------------------------------------------------------
          // 2. Hero Section: AI Orb & Welcome Typography
          // -----------------------------------------------------------------
          Column(
            modifier = Modifier.fillMaxWidth().testTag("home_hero_section"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
          ) {
            // Interactive 3D AI Orb
            AiOrb(
              size = 200.dp,
              state = AiOrbState.IDLE,
              onClick = {
                onStartNewChat(null)
              },
              modifier = Modifier.testTag("home_ai_orb"),
            )

            // Status Badge Pill
            Row(
              modifier =
                Modifier.clip(AppRadius.shapePill)
                  .background(glass.surfaceStandard, AppRadius.shapePill)
                  .border(AppBorder.hairline, glass.borderAccent, AppRadius.shapePill)
                  .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
            ) {
              Box(
                modifier =
                  Modifier.size(8.dp)
                    .clip(AppRadius.shapePill)
                    .background(glass.statusSuccess)
              )
              Text(
                text = stringResource(R.string.home_status_ready),
                style = MaterialTheme.typography.labelSmall,
                color = glass.textSecondary,
                fontWeight = FontWeight.Medium,
              )
            }

            // Welcome Headline
            Text(
              text = stringResource(R.string.home_welcome_title),
              style = MaterialTheme.typography.headlineLarge,
              color = glass.textPrimary,
              textAlign = TextAlign.Center,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.testTag("home_welcome_title"),
            )

            // Supporting Subtitle
            Text(
              text = stringResource(R.string.home_welcome_subtitle),
              style = MaterialTheme.typography.bodyMedium,
              color = glass.textSecondary,
              textAlign = TextAlign.Center,
              modifier =
                Modifier.padding(horizontal = AppSpacing.lg)
                  .testTag("home_welcome_subtitle"),
            )
          }

          // -----------------------------------------------------------------
          // 3. Quick Entry Composer Card
          // -----------------------------------------------------------------
          GlassSurface(
            elevated = true,
            shape = AppRadius.shapeXl,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(AppSpacing.md),
            modifier = Modifier.fillMaxWidth().testTag("home_quick_composer_card"),
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
              GlassTextField(
                value = quickPromptText,
                onValueChange = { quickPromptText = it },
                placeholder = stringResource(R.string.home_composer_placeholder),
                singleLine = false,
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions =
                  KeyboardActions(
                    onGo = {
                      val textToSend = quickPromptText.trim().ifEmpty { null }
                      onStartNewChat(textToSend)
                    }
                  ),
                modifier = Modifier.testTag("home_quick_input_field"),
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "Press enter or tap to start",
                  style = MaterialTheme.typography.labelSmall,
                  color = glass.textMuted,
                  modifier = Modifier.padding(start = AppSpacing.xs),
                )

                GlassButton(
                  text =
                    if (quickPromptText.isNotBlank()) {
                      stringResource(R.string.home_btn_send)
                    } else {
                      stringResource(R.string.home_btn_new_chat)
                    },
                  leadingIcon =
                    if (quickPromptText.isNotBlank()) Icons.AutoMirrored.Outlined.Send else Icons.Outlined.AutoAwesome,
                  variant = GlassButtonVariant.PRIMARY,
                  onClick = {
                    val textToSend = quickPromptText.trim().ifEmpty { null }
                    onStartNewChat(textToSend)
                  },
                  modifier = Modifier.testTag("home_new_chat_button"),
                )
              }
            }
          }

          GlassDivider()

          // -----------------------------------------------------------------
          // 4. Starter Suggestions Grid / Capability Cards
          // -----------------------------------------------------------------
          Column(
            modifier = Modifier.fillMaxWidth().testTag("home_suggestions_section"),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
          ) {
            Text(
              text = stringResource(R.string.home_suggestions_header),
              style = MaterialTheme.typography.labelLarge,
              color = glass.accentCyan,
              fontWeight = FontWeight.SemiBold,
            )

            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
              suggestions.forEach { suggestion ->
                GlassCard(
                  elevated = false,
                  shape = AppRadius.shapeMd,
                  onClick = {
                    onStartNewChat(suggestion.prompt)
                  },
                  modifier =
                    Modifier.fillMaxWidth().testTag("home_suggestion_${suggestion.id}"),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
                  ) {
                    Box(
                      modifier =
                        Modifier.size(40.dp)
                          .clip(AppRadius.shapeSm)
                          .background(glass.surfaceElevated, AppRadius.shapeSm)
                          .border(AppBorder.hairline, glass.borderSpecular, AppRadius.shapeSm),
                      contentAlignment = Alignment.Center,
                    ) {
                      Icon(
                        imageVector = suggestion.icon,
                        contentDescription = null,
                        tint = glass.accentPrimary,
                        modifier = Modifier.size(20.dp),
                      )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = suggestion.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = glass.textPrimary,
                      )
                      Spacer(modifier = Modifier.height(2.dp))
                      Text(
                        text = suggestion.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = glass.textSecondary,
                      )
                    }

                    Icon(
                      imageVector = Icons.Outlined.AutoAwesome,
                      contentDescription = null,
                      tint = glass.accentCyan.copy(alpha = 0.6f),
                      modifier = Modifier.size(16.dp),
                    )
                  }
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(AppSpacing.lg))
        }
      }
    }
  }
}
