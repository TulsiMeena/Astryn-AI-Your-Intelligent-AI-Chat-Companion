package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.ui.navigation.RecentConversationItem
import com.example.ui.theme.AppBorder
import com.example.ui.theme.AppRadius
import com.example.ui.theme.AppSpacing
import com.example.ui.theme.AppThemeTokens
import kotlinx.coroutines.delay

/**
 * Slide-out Glassmorphism Sidebar content for the application's ModalNavigationDrawer.
 *
 * Implements:
 * A. Header with App Identity and Close dismiss action.
 * B. Prominent "New Chat" primary action.
 * C. Search input filtering saved conversations by title, preview, and full message content,
 *    plus smart category filter tags (All, Pinned, Liked, Coding, Study, Work).
 * D. Recent chats list where long-pressing a conversation reveals the full 6-action context menu:
 *    Pin, Like, Rename, Share, Download, and Delete.
 * E. Bottom navigation destinations for Home, Explore Prompts, and Settings.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun GlassSidebarContent(
  recentConversations: List<RecentConversationItem>,
  activeConversationId: String?,
  onNewChat: () -> Unit,
  onSelectConversation: (String) -> Unit,
  onNavigateHome: () -> Unit,
  onNavigateExplore: () -> Unit,
  onNavigateSettings: () -> Unit,
  onCloseDrawer: () -> Unit,
  modifier: Modifier = Modifier,
  onRenameConversation: (String, String) -> Unit = { _, _ -> },
  onDeleteConversation: (String) -> Unit = {},
  onTogglePinConversation: (String) -> Unit = {},
  onToggleLikeConversation: (String) -> Unit = {},
  onShareConversation: (String) -> Unit = {},
  onDownloadConversation: (String) -> Unit = {},
  sidebarFeedbackMessage: String? = null,
  onClearSidebarFeedback: () -> Unit = {},
  onNavigateAuth: () -> Unit = {},
) {
  val glass = AppThemeTokens.glass
  val preferences by UserPreferencesRepository.getInstance().preferencesFlow.collectAsState()
  var searchQuery by rememberSaveable { mutableStateOf("") }
  var selectedCategory by rememberSaveable { mutableStateOf("ALL") }

  var longPressedConversationId by rememberSaveable { mutableStateOf<String?>(null) }
  var conversationToRename by remember { mutableStateOf<RecentConversationItem?>(null) }
  var renameDraftText by remember { mutableStateOf("") }
  var conversationToDelete by remember { mutableStateOf<RecentConversationItem?>(null) }
  var localFeedback by remember { mutableStateOf<String?>(null) }

  val effectiveFeedback = sidebarFeedbackMessage ?: localFeedback
  LaunchedEffect(effectiveFeedback) {
    if (effectiveFeedback != null) {
      delay(2600L)
      localFeedback = null
      onClearSidebarFeedback()
    }
  }

  val filteredConversations =
    remember(searchQuery, selectedCategory, recentConversations) {
      val query = searchQuery.trim()
      recentConversations.filter { item ->
        val matchesCategory = item.matchesCategory(selectedCategory)
        val matchesSearch = if (query.isBlank()) true else item.matchesQuery(query)
        matchesCategory && matchesSearch
      }
    }

  val drawerBrush =
    remember(glass) {
      Brush.horizontalGradient(
        colors = listOf(glass.backgroundCanvas, glass.surfaceElevated)
      )
    }

  Box(
    modifier =
      modifier
        .fillMaxHeight()
        .fillMaxWidth(0.85f)
        .widthIn(min = 280.dp, max = 340.dp)
        .clip(AppRadius.shapeXl)
        .background(drawerBrush)
        .border(AppBorder.hairline, glass.borderSpecular, AppRadius.shapeXl)
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(vertical = AppSpacing.lg, horizontal = AppSpacing.md)
        .testTag("glass_sidebar_drawer"),
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      // -------------------------------------------------------------------
      // Upper Content: Header, New Chat, Search, Category Tags, Recent Chats
      // -------------------------------------------------------------------
      Column(
        modifier =
          Modifier.weight(1f)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
      ) {
        // Section A: Header with App Brand and Close Button
        Row(
          modifier = Modifier.fillMaxWidth().testTag("sidebar_header"),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
          ) {
            AstrynLogoEmblem(
              size = 38.dp,
              contentDescription = "Astryn AI logo",
              modifier = Modifier.testTag("sidebar_brand_logo"),
            )

            Column {
              Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = glass.textPrimary,
              )
              Text(
                text = "Astryn AI • v1.0",
                style = MaterialTheme.typography.labelSmall,
                color = glass.textMuted,
              )
            }
          }

          GlassIconButton(
            icon = Icons.Outlined.Close,
            contentDescription = stringResource(R.string.sidebar_close_desc),
            onClick = onCloseDrawer,
            modifier = Modifier.testTag("sidebar_close_button"),
          )
        }

        // Section B: Prominent "New Chat" Action
        GlassButton(
          text = stringResource(R.string.sidebar_new_chat),
          leadingIcon = Icons.Outlined.Add,
          variant = GlassButtonVariant.PRIMARY,
          onClick = {
            onCloseDrawer()
            onNewChat()
          },
          modifier = Modifier.fillMaxWidth().testTag("sidebar_new_chat_button"),
        )

        // Section C: Search Input Field (searches titles and message contents)
        GlassTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = stringResource(R.string.sidebar_search_placeholder),
          singleLine = true,
          leadingContent = {
            Icon(
              imageVector = Icons.Outlined.Search,
              contentDescription = null,
              tint = glass.textMuted,
              modifier = Modifier.size(18.dp),
            )
          },
          trailingContent =
            if (searchQuery.isNotEmpty()) {
              {
                Box(
                  modifier =
                    Modifier.minimumInteractiveComponentSize()
                      .size(28.dp)
                      .semantics {
                        role = Role.Button
                        contentDescription = "Clear search query"
                      }
                      .clickable { searchQuery = "" }
                      .testTag("sidebar_search_clear"),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Clear,
                    contentDescription = null,
                    tint = glass.textMuted,
                    modifier = Modifier.size(18.dp),
                  )
                }
              }
            } else {
              null
            },
          modifier = Modifier.testTag("sidebar_search_input"),
        )

        // Section C2: Smart Category Filter Chips (All, Pinned, Liked, Coding, Study, Work)
        val categories =
          remember {
            listOf(
              "ALL" to "All",
              "PINNED" to "Pinned",
              "LIKED" to "Liked",
              "CODING" to "Coding",
              "STUDY" to "Study",
              "WORK" to "Work",
            )
          }
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .horizontalScroll(rememberScrollState())
              .testTag("sidebar_category_row"),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          categories.forEach { (catKey, catLabel) ->
            val isCatSelected = selectedCategory == catKey
            Box(
              modifier =
                Modifier.clip(AppRadius.shapePill)
                  .background(
                    if (isCatSelected) glass.accentCyan.copy(alpha = 0.18f)
                    else glass.surfaceElevated
                  )
                  .border(
                    AppBorder.hairline,
                    if (isCatSelected) glass.accentCyan else glass.borderSubtle,
                    AppRadius.shapePill,
                  )
                  .semantics {
                    role = Role.Tab
                    selected = isCatSelected
                    contentDescription = "Filter by $catLabel"
                  }
                  .clickable { selectedCategory = catKey }
                  .padding(horizontal = 10.dp, vertical = 5.dp)
                  .testTag("sidebar_category_$catKey"),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = catLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isCatSelected) glass.accentCyan else glass.textSecondary,
              )
            }
          }
        }

        AnimatedVisibility(
          visible = effectiveFeedback != null,
          enter = fadeIn(),
          exit = fadeOut(),
        ) {
          effectiveFeedback?.let { feedback ->
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(10.dp))
                  .background(glass.surfaceElevated)
                  .border(AppBorder.hairline, glass.accentCyan, RoundedCornerShape(10.dp))
                  .padding(horizontal = 10.dp, vertical = 6.dp)
                  .testTag("sidebar_action_feedback"),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = glass.accentCyan,
                modifier = Modifier.size(14.dp),
              )
              Text(
                text = feedback,
                style = MaterialTheme.typography.labelSmall,
                color = glass.textPrimary,
                fontWeight = FontWeight.Medium,
              )
            }
          }
        }

        GlassDivider()

        // Section D: Recent Conversations Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = stringResource(R.string.sidebar_recent_header),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = glass.accentCyan,
          )

          if (recentConversations.isNotEmpty()) {
            Text(
              text = "Hold item for options • ${recentConversations.size}",
              style = MaterialTheme.typography.labelSmall,
              color = glass.textMuted,
            )
          }
        }

        // Recent Conversations List or Empty State
        if (recentConversations.isEmpty()) {
          // Empty State
          Column(
            modifier =
              Modifier.fillMaxWidth()
                .padding(vertical = AppSpacing.lg)
                .testTag("sidebar_empty_state"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
          ) {
            Icon(
              imageVector = Icons.Outlined.ChatBubbleOutline,
              contentDescription = null,
              tint = glass.textDisabled,
              modifier = Modifier.size(32.dp),
            )
            Text(
              text = stringResource(R.string.sidebar_empty_title),
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Medium,
              color = glass.textSecondary,
            )
            Text(
              text = stringResource(R.string.sidebar_empty_subtitle),
              style = MaterialTheme.typography.bodySmall,
              color = glass.textMuted,
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(horizontal = AppSpacing.sm),
            )
          }
        } else if (filteredConversations.isEmpty()) {
          // No search results state
          Text(
            text = stringResource(R.string.sidebar_search_no_results),
            style = MaterialTheme.typography.bodySmall,
            color = glass.textMuted,
            modifier = Modifier.padding(vertical = AppSpacing.md).testTag("sidebar_no_results"),
          )
        } else {
          // List of actual conversation sessions
          Column(
            modifier = Modifier.fillMaxWidth().testTag("sidebar_conversations_list"),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
          ) {
            filteredConversations.forEach { conversation ->
              val isSelected = activeConversationId == conversation.id
              val isLongPressMenuOpen = longPressedConversationId == conversation.id

              Column(
                modifier =
                  Modifier.fillMaxWidth()
                    .clip(AppRadius.shapeSm)
                    .background(
                      if (isSelected || isLongPressMenuOpen) glass.surfaceElevated else Color.Transparent,
                      AppRadius.shapeSm,
                    )
                    .border(
                      width = if (isSelected || isLongPressMenuOpen) AppBorder.focused else AppBorder.hairline,
                      color =
                        when {
                          isLongPressMenuOpen -> glass.accentCyan
                          isSelected -> glass.borderAccent
                          conversation.isPinned -> glass.accentCyan.copy(alpha = 0.45f)
                          else -> glass.borderSubtle
                        },
                      shape = AppRadius.shapeSm,
                    ),
              ) {
                Row(
                  modifier =
                    Modifier.fillMaxWidth()
                      .semantics {
                        role = Role.Button
                        selected = isSelected
                        stateDescription =
                          if (isSelected) "Active conversation" else "Saved conversation"
                      }
                      .combinedClickable(
                        onClick = {
                          if (isLongPressMenuOpen) {
                            longPressedConversationId = null
                          } else {
                            onCloseDrawer()
                            onSelectConversation(conversation.id)
                          }
                        },
                        onLongClick = {
                          longPressedConversationId =
                            if (longPressedConversationId == conversation.id) null
                            else conversation.id
                        },
                      )
                      .padding(
                        horizontal = AppSpacing.md,
                        vertical = 10.dp,
                      )
                      .testTag("sidebar_conversation_${conversation.id}"),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                ) {
                  Icon(
                    imageVector =
                      if (conversation.isPinned) Icons.Filled.PushPin
                      else Icons.Outlined.ChatBubbleOutline,
                    contentDescription = null,
                    tint =
                      if (isSelected || conversation.isPinned) glass.accentCyan
                      else glass.textMuted,
                    modifier = Modifier.size(16.dp),
                  )

                  Column(modifier = Modifier.weight(1f)) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Text(
                        text = conversation.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected || conversation.isPinned) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) glass.textPrimary else glass.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                      )

                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                      ) {
                        if (conversation.isPinned) {
                          Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = "Pinned",
                            tint = glass.accentCyan,
                            modifier =
                              Modifier.size(13.dp)
                                .testTag("sidebar_pinned_badge_${conversation.id}"),
                          )
                        }
                        if (conversation.isLiked) {
                          Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = "Liked",
                            tint = Color(0xFFF43F5E),
                            modifier =
                              Modifier.size(13.dp)
                                .testTag("sidebar_liked_badge_${conversation.id}"),
                          )
                        }
                      }
                    }

                    val subtitleText =
                      if (conversation.preview.isNotBlank()) {
                        "${conversation.timestampFormatted} • ${conversation.preview}"
                      } else {
                        conversation.timestampFormatted
                      }

                    Text(
                      text = subtitleText,
                      style = MaterialTheme.typography.bodySmall,
                      color = glass.textMuted,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                }

                // -----------------------------------------------------------
                // Long-Click Context Options Menu (Only visible after long press)
                // Includes: Pin, Like, Rename, Share, Download, Delete
                // -----------------------------------------------------------
                AnimatedVisibility(
                  visible = isLongPressMenuOpen,
                  enter = fadeIn() + expandVertically(),
                  exit = fadeOut() + shrinkVertically(),
                ) {
                  Column(
                    modifier =
                      Modifier.fillMaxWidth()
                        .background(glass.surfaceStandard.copy(alpha = 0.90f))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("sidebar_long_press_menu_${conversation.id}"),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Text(
                        text = "CONVERSATION OPTIONS",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        fontWeight = FontWeight.Bold,
                        color = glass.accentCyan,
                        letterSpacing = 0.8.sp,
                      )
                      Box(
                        modifier =
                          Modifier.clip(AppRadius.shapePill)
                            .clickable { longPressedConversationId = null }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("sidebar_close_menu_${conversation.id}"),
                      ) {
                        Icon(
                          imageVector = Icons.Outlined.Close,
                          contentDescription = "Close options menu",
                          tint = glass.textMuted,
                          modifier = Modifier.size(14.dp),
                        )
                      }
                    }

                    FlowRow(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.spacedBy(6.dp),
                      verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                      // 1. Pin / Unpin Option
                      SidebarOptionChip(
                        icon = if (conversation.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        label = if (conversation.isPinned) "Unpin" else "Pin",
                        tint = if (conversation.isPinned) glass.accentCyan else glass.textPrimary,
                        onClick = {
                          onTogglePinConversation(conversation.id)
                          localFeedback =
                            if (conversation.isPinned) "Conversation unpinned"
                            else "Conversation pinned to top"
                          longPressedConversationId = null
                        },
                        testTag = "sidebar_pin_${conversation.id}",
                      )

                      // 2. Like / Unlike Option
                      SidebarOptionChip(
                        icon =
                          if (conversation.isLiked) Icons.Filled.Favorite
                          else Icons.Outlined.FavoriteBorder,
                        label = if (conversation.isLiked) "Liked" else "Like",
                        tint = if (conversation.isLiked) Color(0xFFF43F5E) else glass.textPrimary,
                        onClick = {
                          onToggleLikeConversation(conversation.id)
                          localFeedback =
                            if (conversation.isLiked) "Removed from liked chats"
                            else "Added to liked chats"
                          longPressedConversationId = null
                        },
                        testTag = "sidebar_like_${conversation.id}",
                      )

                      // 3. Rename Option
                      SidebarOptionChip(
                        icon = Icons.Outlined.Edit,
                        label = "Rename",
                        tint = glass.textPrimary,
                        onClick = {
                          renameDraftText = conversation.title
                          conversationToRename = conversation
                          longPressedConversationId = null
                        },
                        testTag = "sidebar_rename_${conversation.id}",
                      )

                      // 4. Share Option
                      SidebarOptionChip(
                        icon = Icons.Outlined.Share,
                        label = "Share",
                        tint = glass.textPrimary,
                        onClick = {
                          onShareConversation(conversation.id)
                          localFeedback = "Sharing conversation…"
                          longPressedConversationId = null
                        },
                        testTag = "sidebar_share_${conversation.id}",
                      )

                      // 5. Download Option
                      SidebarOptionChip(
                        icon = Icons.Outlined.Download,
                        label = "Download",
                        tint = glass.accentCyan,
                        onClick = {
                          onDownloadConversation(conversation.id)
                          localFeedback = "Downloading transcript…"
                          longPressedConversationId = null
                        },
                        testTag = "sidebar_download_${conversation.id}",
                      )

                      // 6. Delete Option
                      SidebarOptionChip(
                        icon = Icons.Outlined.DeleteOutline,
                        label = "Delete",
                        tint = glass.statusError,
                        onClick = {
                          conversationToDelete = conversation
                          longPressedConversationId = null
                        },
                        testTag = "sidebar_delete_${conversation.id}",
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }

      // -------------------------------------------------------------------
      // Section E: Bottom Navigation Links (Home, Explore, Settings)
      // -------------------------------------------------------------------
      Column(
        modifier = Modifier.fillMaxWidth().testTag("sidebar_nav_section"),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
      ) {
        GlassDivider(modifier = Modifier.padding(bottom = AppSpacing.xs))

        SidebarNavLink(
          title = stringResource(R.string.sidebar_nav_home),
          icon = Icons.Outlined.Home,
          onClick = {
            onCloseDrawer()
            onNavigateHome()
          },
          testTag = "sidebar_nav_home",
        )

        SidebarNavLink(
          title = stringResource(R.string.sidebar_nav_explore),
          icon = Icons.Outlined.Explore,
          onClick = {
            onCloseDrawer()
            onNavigateExplore()
          },
          testTag = "sidebar_nav_explore",
        )

        SidebarNavLink(
          title = stringResource(R.string.sidebar_nav_settings),
          icon = Icons.Outlined.Settings,
          onClick = {
            onCloseDrawer()
            onNavigateSettings()
          },
          testTag = "sidebar_nav_settings",
        )

        // User Profile Card (replaces generic "Verified Google Account • AI Explorer" row)
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .background(glass.surfaceElevated)
              .border(AppBorder.hairline, glass.borderAccent, RoundedCornerShape(14.dp))
              .clickable {
                onCloseDrawer()
                onNavigateAuth()
              }
              .padding(horizontal = 10.dp, vertical = 9.dp)
              .testTag("sidebar_nav_auth"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          com.example.ui.screens.profile.UserAvatarBadge(
            name = preferences.userName,
            email = preferences.userEmail,
            avatarPath = preferences.userAvatarPath,
            avatarPresetId = preferences.userAvatarPreset,
            size = 38.dp,
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text =
                if (preferences.isLoggedIn) {
                  preferences.userName.ifBlank { "My Profile" }
                } else {
                  "Sign In / Create Account"
                },
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
              color = glass.textPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text =
                if (preferences.isLoggedIn) {
                  preferences.userEmail.ifBlank { preferences.userRoleBadge }
                } else {
                  "Tap to open your private vault"
                },
              style = MaterialTheme.typography.bodySmall,
              color = glass.accentCyan,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Box(
            modifier =
              Modifier.clip(AppRadius.shapePill)
                .background(glass.accentPrimary.copy(alpha = 0.2f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
          ) {
            Text(
              text = "Profile",
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
              fontWeight = FontWeight.Bold,
              color = glass.accentCyan,
            )
          }
        }
      }
    }
  }

  // -------------------------------------------------------------------
  // Accessible Rename Conversation Dialog
  // -------------------------------------------------------------------
  conversationToRename?.let { target ->
    Dialog(onDismissRequest = { conversationToRename = null }) {
      GlassSurface(
        elevated = true,
        modifier =
          Modifier.fillMaxWidth()
            .testTag("rename_conversation_dialog"),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
          Text(
            text = stringResource(R.string.dialog_rename_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = glass.textPrimary,
          )

          GlassTextField(
            value = renameDraftText,
            onValueChange = { renameDraftText = it },
            placeholder = stringResource(R.string.dialog_rename_placeholder),
            singleLine = true,
            modifier =
              Modifier.fillMaxWidth()
                .testTag("rename_conversation_input"),
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm, Alignment.End),
          ) {
            GlassButton(
              text = stringResource(R.string.dialog_cancel),
              variant = GlassButtonVariant.SUBTLE,
              onClick = { conversationToRename = null },
              modifier = Modifier.testTag("rename_cancel_button"),
            )

            GlassButton(
              text = stringResource(R.string.dialog_rename_save),
              variant = GlassButtonVariant.PRIMARY,
              enabled = renameDraftText.trim().isNotBlank(),
              onClick = {
                val cleanTitle = renameDraftText.trim()
                if (cleanTitle.isNotBlank()) {
                  onRenameConversation(target.id, cleanTitle)
                  localFeedback = "Conversation renamed"
                  conversationToRename = null
                }
              },
              modifier = Modifier.testTag("rename_confirm_button"),
            )
          }
        }
      }
    }
  }

  // -------------------------------------------------------------------
  // Accessible Delete Conversation Confirmation Dialog
  // -------------------------------------------------------------------
  conversationToDelete?.let { target ->
    Dialog(onDismissRequest = { conversationToDelete = null }) {
      GlassSurface(
        elevated = true,
        modifier =
          Modifier.fillMaxWidth()
            .testTag("delete_conversation_dialog"),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
          ) {
            Icon(
              imageVector = Icons.Outlined.WarningAmber,
              contentDescription = null,
              tint = glass.statusError,
              modifier = Modifier.size(22.dp),
            )
            Text(
              text = stringResource(R.string.dialog_delete_title),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = glass.textPrimary,
            )
          }

          Text(
            text = "\"${target.title}\"",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = glass.accentCyan,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )

          Text(
            text = stringResource(R.string.dialog_delete_message),
            style = MaterialTheme.typography.bodySmall,
            color = glass.textSecondary,
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm, Alignment.End),
          ) {
            GlassButton(
              text = stringResource(R.string.dialog_cancel),
              variant = GlassButtonVariant.SUBTLE,
              onClick = { conversationToDelete = null },
              modifier = Modifier.testTag("delete_cancel_button"),
            )

            GlassButton(
              text = stringResource(R.string.dialog_delete_confirm),
              variant = GlassButtonVariant.DESTRUCTIVE,
              onClick = {
                onDeleteConversation(target.id)
                localFeedback = "Conversation deleted"
                conversationToDelete = null
              },
              modifier = Modifier.testTag("delete_confirm_button"),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SidebarOptionChip(
  icon: ImageVector,
  label: String,
  tint: Color,
  onClick: () -> Unit,
  testTag: String,
) {
  val glass = AppThemeTokens.glass
  Row(
    modifier =
      Modifier.minimumInteractiveComponentSize()
        .clip(AppRadius.shapePill)
        .background(glass.surfaceElevated)
        .border(AppBorder.hairline, tint.copy(alpha = 0.45f), AppRadius.shapePill)
        .semantics {
          role = Role.Button
          contentDescription = label
        }
        .clickable(onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 5.dp)
        .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = tint,
      modifier = Modifier.size(13.dp),
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.SemiBold,
      color = tint,
    )
  }
}

/**
 * Accessible navigation link item inside the sidebar drawer.
 */
@Composable
private fun SidebarNavLink(
  title: String,
  icon: ImageVector,
  onClick: () -> Unit,
  testTag: String,
  modifier: Modifier = Modifier,
) {
  val glass = AppThemeTokens.glass
  val interactionSource = remember { MutableInteractionSource() }

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .height(48.dp) // Guarantees >= 48dp minimum touch target
        .clip(AppRadius.shapeSm)
        .clickable(
          interactionSource = interactionSource,
          indication = androidx.compose.material3.ripple(),
          role = Role.Button,
          onClick = onClick,
        )
        .padding(horizontal = AppSpacing.md)
        .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = glass.textSecondary,
      modifier = Modifier.size(20.dp),
    )
    Text(
      text = title,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Medium,
      color = glass.textPrimary,
    )
  }
}
