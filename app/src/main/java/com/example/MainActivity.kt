package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ui.widget.AstrynGlassWidgetProvider
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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MotionPhotosOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.example.ui.components.AmbientGlassBackground
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassButtonVariant
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassDivider
import com.example.ui.components.GlassIconButton
import com.example.ui.components.GlassSurface
import com.example.ui.navigation.AppScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.AppSpacing
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.AppThemeTokens
import com.example.ui.theme.CodeTypography
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import com.example.ui.screens.chat.ChatScreen

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.example.data.api.NetworkConnectivityMonitor
import com.example.data.local.NovaChatDatabase
import com.example.data.local.UserApiKeyManager
import com.example.data.repository.ChatHistoryRepository
import com.example.data.repository.UserPreferencesRepository
import com.example.ui.components.GlassSidebarContent
import com.example.ui.navigation.RecentConversationItem
import com.example.ui.screens.auth.AuthScreen
import com.example.ui.screens.settings.ApiKeyConfigScreen
import com.example.ui.screens.settings.SettingsScreen
import java.util.UUID

class MainActivity : ComponentActivity() {
  private var pendingWidgetAction by mutableStateOf<String?>(null)

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    pendingWidgetAction = intent.action
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    pendingWidgetAction = intent?.action

    val database = NovaChatDatabase.getInstance(applicationContext)
    val historyRepository = ChatHistoryRepository.getInstance(database.chatDao())
    val preferencesRepository = UserPreferencesRepository.getInstance(applicationContext)
    val apiKeyManager = UserApiKeyManager.getInstance(applicationContext)
    NetworkConnectivityMonitor.getInstance(applicationContext)

    setContent {
      val userPreferences by preferencesRepository.preferencesFlow.collectAsState()
      val isUserAuthenticated =
        (userPreferences.isLoggedIn && userPreferences.userUid.isNotBlank()) ||
          userPreferences.hasSkippedAuth

      var currentScreen by
        rememberSaveable(stateSaver = AppScreen.Saver) {
          val initialPrefs = preferencesRepository.preferencesFlow.value
          val startScreen =
            if (
              (!initialPrefs.isLoggedIn || initialPrefs.userUid.isBlank()) &&
                !initialPrefs.hasSkippedAuth
            ) {
              AppScreen.Auth(initialIsSignUp = false)
            } else {
              AppScreen.Home
            }
          mutableStateOf<AppScreen>(startScreen)
        }

      // Bind ChatHistoryRepository strictly to the authenticated user's UID
      // (or "guest_local_session" in Guest mode) so User B can never see User A's conversations.
      LaunchedEffect(
        userPreferences.isLoggedIn,
        userPreferences.hasSkippedAuth,
        userPreferences.userUid,
        userPreferences.authIdToken,
      ) {
        historyRepository.loadFromStorage()
        val effectiveUid =
          when {
            userPreferences.isLoggedIn && userPreferences.userUid.isNotBlank() ->
              userPreferences.userUid
            userPreferences.hasSkippedAuth -> "guest_local_session"
            else -> "__signed_out__"
          }
        historyRepository.setActiveUser(
          uid = effectiveUid,
          idToken = if (userPreferences.isLoggedIn) userPreferences.authIdToken else "",
          context = applicationContext,
        )
        if (!isUserAuthenticated) {
          currentScreen = AppScreen.Auth(initialIsSignUp = false)
        }
      }

      LaunchedEffect(pendingWidgetAction, isUserAuthenticated) {
        val action = pendingWidgetAction
        if (action != null && isUserAuthenticated) {
          pendingWidgetAction = null
          when (action) {
            AstrynGlassWidgetProvider.ACTION_OPEN_LIVE_VOICE -> {
              currentScreen =
                AppScreen.Chat(
                  conversationId = UUID.randomUUID().toString(),
                  startLiveVoice = true,
                )
            }
            AstrynGlassWidgetProvider.ACTION_OPEN_VOICE_MIC -> {
              currentScreen =
                AppScreen.Chat(
                  conversationId = UUID.randomUUID().toString(),
                  startVoiceMic = true,
                )
            }
            AstrynGlassWidgetProvider.ACTION_NEW_CHAT -> {
              currentScreen =
                AppScreen.Chat(
                  conversationId = UUID.randomUUID().toString(),
                )
            }
          }
        }
      }

      val recentConversations by historyRepository.conversationsFlow.collectAsState()

      MyApplicationTheme(
        themeMode = userPreferences.themeMode,
        reducedMotion = userPreferences.reducedMotion,
        reduceTransparency = userPreferences.reduceTransparency,
      ) {
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
        val textToolbar = androidx.compose.ui.platform.LocalTextToolbar.current

        // Dismiss any active text selection toolbar or focused input when switching screens or opening drawer
        LaunchedEffect(currentScreen, drawerState.isOpen, isUserAuthenticated) {
          if (textToolbar.status == androidx.compose.ui.platform.TextToolbarStatus.Shown) {
            textToolbar.hide()
          }
          focusManager.clearFocus(force = true)
        }

        // Close drawer automatically if user signs out
        LaunchedEffect(isUserAuthenticated) {
          if (!isUserAuthenticated && drawerState.isOpen) {
            drawerState.close()
          }
        }

        // Android Back Priority 1: If sidebar drawer is open, Back closes the drawer first
        BackHandler(enabled = drawerState.isOpen) {
          scope.launch { drawerState.close() }
        }

        // Android Back Priority 2: If drawer is closed and on a sub-screen (when authenticated), Back returns to parent/Home
        BackHandler(
          enabled =
            isUserAuthenticated &&
              !drawerState.isOpen &&
              currentScreen != AppScreen.Home,
        ) {
          currentScreen =
            if (currentScreen == AppScreen.ApiKeyConfig) {
              AppScreen.Settings
            } else {
              AppScreen.Home
            }
        }

        val startChatWithSession: (String?) -> Unit = { prompt ->
          val newId = UUID.randomUUID().toString()
          currentScreen = AppScreen.Chat(initialPrompt = prompt, conversationId = newId)
        }

        var sidebarFeedback by remember { mutableStateOf<String?>(null) }

        ModalNavigationDrawer(
          drawerState = drawerState,
          gesturesEnabled = isUserAuthenticated && currentScreen !is AppScreen.Auth,
          drawerContent = {
            GlassSidebarContent(
              recentConversations = recentConversations,
              activeConversationId = (currentScreen as? AppScreen.Chat)?.conversationId,
              onNewChat = {
                startChatWithSession(null)
              },
              onSelectConversation = { convId ->
                currentScreen = AppScreen.Chat(conversationId = convId)
              },
              onNavigateHome = {
                currentScreen = AppScreen.Home
              },
              onNavigateExplore = {
                currentScreen = AppScreen.Explore
              },
              onNavigateSettings = {
                currentScreen = AppScreen.Settings
              },
              onNavigateAuth = {
                currentScreen =
                  if (userPreferences.isLoggedIn) AppScreen.Profile
                  else AppScreen.Auth(initialIsSignUp = false)
              },
              onCloseDrawer = {
                scope.launch { drawerState.close() }
              },
              onRenameConversation = { convId, newTitle ->
                scope.launch {
                  historyRepository.renameConversation(convId, newTitle)
                }
              },
              onDeleteConversation = { convId ->
                scope.launch {
                  historyRepository.deleteConversation(convId)
                }
                val activeId = (currentScreen as? AppScreen.Chat)?.conversationId
                if (activeId == convId) {
                  currentScreen = AppScreen.Chat(conversationId = UUID.randomUUID().toString())
                }
              },
              onTogglePinConversation = { convId ->
                scope.launch {
                  historyRepository.togglePinConversation(convId)
                }
              },
              onToggleLikeConversation = { convId ->
                scope.launch {
                  historyRepository.toggleLikeConversation(convId)
                }
              },
              onShareConversation = { convId ->
                scope.launch {
                  val transcript = historyRepository.formatConversationForExport(convId)
                  val title = historyRepository.getConversationTitle(convId) ?: "Astryn AI Chat"
                  val shared =
                    com.example.ui.screens.chat.shareTextViaAndroidSheet(
                      context = applicationContext,
                      title = title,
                      text = transcript,
                    )
                  if (!shared) {
                    com.example.ui.screens.chat.copyTextToSystemClipboard(
                      context = applicationContext,
                      label = title,
                      text = transcript,
                    )
                    sidebarFeedback = "Transcript copied to clipboard for sharing"
                  }
                }
              },
              onDownloadConversation = { convId ->
                scope.launch {
                  val result =
                    historyRepository.downloadConversationToDevice(
                      context = applicationContext,
                      conversationId = convId,
                    )
                  result
                    .onSuccess { savedName ->
                      sidebarFeedback = "Downloaded: $savedName"
                    }
                    .onFailure {
                      sidebarFeedback = "Unable to download conversation"
                    }
                }
              },
              sidebarFeedbackMessage = sidebarFeedback,
              onClearSidebarFeedback = { sidebarFeedback = null },
            )
          },
        ) {
          Box(modifier = Modifier.fillMaxSize()) {
            if (!isUserAuthenticated) {
              val initialSignUp = (currentScreen as? AppScreen.Auth)?.initialIsSignUp ?: false
              AuthScreen(
                preferencesRepository = preferencesRepository,
                initialIsSignUp = initialSignUp,
                onAuthComplete = { currentScreen = AppScreen.Home },
                onBackOrSkip = { currentScreen = AppScreen.Home },
              )
            } else {
              androidx.compose.runtime.key(userPreferences.userUid) {
                when (val screen = currentScreen) {
                  is AppScreen.Home -> {
                    HomeScreen(
                      onStartNewChat = { initialPrompt ->
                        startChatWithSession(initialPrompt)
                      },
                      onOpenMenu = {
                        scope.launch { drawerState.open() }
                      },
                      onOpenExplore = {
                        currentScreen = AppScreen.Explore
                      },
                      onOpenSettings = {
                        currentScreen = AppScreen.Settings
                      },
                      onOpenAuth = {
                        currentScreen =
                          if (userPreferences.isLoggedIn) AppScreen.Profile
                          else AppScreen.Auth(initialIsSignUp = false)
                      },
                    )
                  }

                  is AppScreen.Chat -> {
                    ChatScreen(
                      conversationId = screen.conversationId ?: "session_${System.currentTimeMillis()}",
                      initialPrompt = screen.initialPrompt,
                      startWithLiveVoice = screen.startLiveVoice,
                      startWithVoiceMic = screen.startVoiceMic,
                      onOpenMenu = {
                        scope.launch { drawerState.open() }
                      },
                      onBack = { currentScreen = AppScreen.Home },
                      onOpenApiConfig = { currentScreen = AppScreen.ApiKeyConfig },
                    )
                  }

                  is AppScreen.Explore -> {
                    TransitionalDestinationScreen(
                      title = stringResource(R.string.explore_placeholder_title),
                      subtitle = "Select a curated prompt template to start a new conversation with Astryn AI.",
                      onOpenMenu = {
                        scope.launch { drawerState.open() }
                      },
                      onBack = { currentScreen = AppScreen.Home },
                      onSelectPrompt = { prompt ->
                        startChatWithSession(prompt)
                      },
                    )
                  }

                  is AppScreen.Settings -> {
                    SettingsScreen(
                      preferencesRepository = preferencesRepository,
                      apiKeyManager = apiKeyManager,
                      onOpenMenu = {
                        scope.launch { drawerState.open() }
                      },
                      onBack = { currentScreen = AppScreen.Home },
                      onOpenApiConfig = { currentScreen = AppScreen.ApiKeyConfig },
                      onOpenAuth = { isSignUp ->
                        currentScreen =
                          if (userPreferences.isLoggedIn) AppScreen.Profile
                          else AppScreen.Auth(initialIsSignUp = isSignUp)
                      },
                      onClearAllHistory = {
                        scope.launch {
                          historyRepository.clearAllConversations()
                        }
                      },
                    )
                  }

                  is AppScreen.ApiKeyConfig -> {
                    ApiKeyConfigScreen(
                      onBack = { currentScreen = AppScreen.Settings },
                      apiKeyManager = apiKeyManager,
                      preferencesRepository = preferencesRepository,
                    )
                  }

                  is AppScreen.Profile -> {
                    com.example.ui.screens.profile.UserProfileScreen(
                      preferencesRepository = preferencesRepository,
                      savedChatsCount = recentConversations.size,
                      onOpenMenu = {
                        scope.launch { drawerState.open() }
                      },
                      onBack = { currentScreen = AppScreen.Home },
                      onOpenSettings = { currentScreen = AppScreen.Settings },
                      onClearAllMyChats = {
                        scope.launch {
                          historyRepository.clearAllConversations()
                        }
                      },
                      onAccountSignedOutOrDeleted = {
                        currentScreen = AppScreen.Auth(initialIsSignUp = false)
                      },
                    )
                  }

                  is AppScreen.Auth -> {
                    if (userPreferences.isLoggedIn) {
                      com.example.ui.screens.profile.UserProfileScreen(
                        preferencesRepository = preferencesRepository,
                        savedChatsCount = recentConversations.size,
                        onOpenMenu = {
                          scope.launch { drawerState.open() }
                        },
                        onBack = { currentScreen = AppScreen.Home },
                        onOpenSettings = { currentScreen = AppScreen.Settings },
                        onClearAllMyChats = {
                          scope.launch {
                            historyRepository.clearAllConversations()
                          }
                        },
                        onAccountSignedOutOrDeleted = {
                          currentScreen = AppScreen.Auth(initialIsSignUp = false)
                        },
                      )
                    } else {
                      AuthScreen(
                        preferencesRepository = preferencesRepository,
                        initialIsSignUp = screen.initialIsSignUp,
                        onAuthComplete = { currentScreen = AppScreen.Home },
                        onBackOrSkip = { currentScreen = AppScreen.Home },
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

/**
 * Preserved existing Greeting composable for backward compatibility.
 */
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(
    text = "Hello $name!",
    style = MaterialTheme.typography.labelMedium,
    color = AppThemeTokens.glass.textSecondary,
    modifier = modifier.testTag("greeting_text"),
  )
}

/**
 * Transitional Chat Screen destination verifying real navigation flow from Home.
 * Passes the drafted prompt or starter suggestion cleanly to the chat workflow.
 */
@Composable
fun TransitionalChatScreen(
  initialPrompt: String?,
  onOpenMenu: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val glass = AppThemeTokens.glass

  AmbientGlassBackground(modifier = modifier.testTag("chat_screen_container")) {
    Scaffold(
      modifier = Modifier.fillMaxSize(),
      containerColor = Color.Transparent,
      contentWindowInsets = WindowInsets.systemBars,
    ) { innerPadding ->
      Column(
        modifier =
          Modifier.fillMaxSize()
            .padding(innerPadding)
            .padding(AppSpacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        // Navigation Bar
        Row(
          modifier = Modifier.fillMaxWidth(),
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
              modifier = Modifier.testTag("chat_back_button"),
            )

            Column {
              Text(
                text = stringResource(R.string.chat_placeholder_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = glass.textPrimary,
              )
              Text(
                text = "Session active",
                style = MaterialTheme.typography.labelSmall,
                color = glass.accentCyan,
              )
            }
          }

          GlassIconButton(
            icon = Icons.Outlined.Menu,
            contentDescription = stringResource(R.string.home_menu_desc),
            onClick = onOpenMenu,
            modifier = Modifier.testTag("chat_menu_button"),
          )
        }

        GlassSurface(
          elevated = true,
          modifier = Modifier.fillMaxWidth().widthIn(max = AppSpacing.maxContentWidth),
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            Greeting(name = "Android")

            Text(
              text = "Conversation Draft Initialized",
              style = MaterialTheme.typography.headlineSmall,
              color = glass.textPrimary,
              fontWeight = FontWeight.SemiBold,
            )

            if (!initialPrompt.isNullOrBlank()) {
              Text(
                text = "Pre-filled Prompt from Home:",
                style = MaterialTheme.typography.labelMedium,
                color = glass.accentCyan,
              )
              GlassCard(elevated = false, modifier = Modifier.fillMaxWidth()) {
                Text(
                  text = initialPrompt,
                  style = MaterialTheme.typography.bodyMedium,
                  color = glass.textPrimary,
                  modifier = Modifier.testTag("chat_draft_prompt_text"),
                )
              }
            } else {
              Text(
                text = "Started a blank new conversation.",
                style = MaterialTheme.typography.bodyMedium,
                color = glass.textSecondary,
                modifier = Modifier.testTag("chat_draft_prompt_text"),
              )
            }

            Text(
              text = stringResource(R.string.chat_placeholder_desc),
              style = MaterialTheme.typography.bodySmall,
              color = glass.textSecondary,
            )

            GlassButton(
              text = "Back to Home",
              variant = GlassButtonVariant.SECONDARY,
              onClick = onBack,
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      }
    }
  }
}

/**
 * Transitional destination for Explore or sub-screens.
 */
@Composable
fun TransitionalDestinationScreen(
  title: String,
  subtitle: String,
  onOpenMenu: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  onSelectPrompt: (String) -> Unit = {},
) {
  val glass = AppThemeTokens.glass
  val curatedPrompts =
    remember {
      listOf(
        Triple(
          "explore_prompt_coding",
          "Kotlin & Compose Architecture",
          "Design a clean MVVM state holder in Kotlin using StateFlow, coroutines, and sealed UI states.",
        ),
        Triple(
          "explore_prompt_writing",
          "Executive Technical Brief",
          "Draft a structured engineering release-readiness summary highlighting architecture, security, and test coverage.",
        ),
        Triple(
          "explore_prompt_analysis",
          "Algorithm & Performance Audit",
          "Analyze time and space complexity trade-offs between bounded LRU caches and full-list recomputation.",
        ),
        Triple(
          "explore_prompt_hindi",
          "हिन्दी तकनीकी मार्गदर्शिका (Hindi Guide)",
          "एंड्रॉइड जेटपैक कंपोज़ में स्टेट मैनेजमेंट और परफॉर्मेंस ऑप्टिमाइजेशन को सरल उदाहरणों के साथ समझाएं।",
        ),
      )
    }

  AmbientGlassBackground(modifier = modifier.testTag("explore_screen")) {
    Scaffold(
      modifier = Modifier.fillMaxSize(),
      containerColor = Color.Transparent,
      contentWindowInsets = WindowInsets.systemBars,
    ) { innerPadding ->
      Column(
        modifier =
          Modifier.fillMaxSize()
            .padding(innerPadding)
            .padding(AppSpacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
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
              modifier = Modifier.testTag("explore_back_button"),
            )
            Text(
              text = title,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = glass.textPrimary,
            )
          }

          GlassIconButton(
            icon = Icons.Outlined.Menu,
            contentDescription = stringResource(R.string.home_menu_desc),
            onClick = onOpenMenu,
            modifier = Modifier.testTag("explore_menu_button"),
          )
        }

        GlassSurface(elevated = true, modifier = Modifier.fillMaxWidth()) {
          Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            Text(
              text = title,
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold,
              color = glass.textPrimary,
            )
            Text(
              text = subtitle,
              style = MaterialTheme.typography.bodyMedium,
              color = glass.textSecondary,
            )
          }
        }

        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
          curatedPrompts.forEach { (tag, cardTitle, promptText) ->
            GlassCard(
              elevated = false,
              onClick = { onSelectPrompt(promptText) },
              modifier = Modifier.fillMaxWidth().testTag(tag),
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                Text(
                  text = cardTitle,
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold,
                  color = glass.accentCyan,
                )
                Text(
                  text = promptText,
                  style = MaterialTheme.typography.bodySmall,
                  color = glass.textPrimary,
                )
              }
            }
          }
        }
      }
    }
  }
}

/**
 * Transitional Settings screen allowing theme and motion toggle testing.
 */
@Composable
fun TransitionalSettingsScreen(
  isDarkTheme: Boolean,
  reducedMotion: Boolean,
  onToggleTheme: () -> Unit,
  onToggleReducedMotion: () -> Unit,
  onOpenMenu: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val glass = AppThemeTokens.glass

  AmbientGlassBackground(modifier = modifier) {
    Scaffold(
      modifier = Modifier.fillMaxSize(),
      containerColor = Color.Transparent,
      contentWindowInsets = WindowInsets.systemBars,
    ) { innerPadding ->
      Column(
        modifier =
          Modifier.fillMaxSize()
            .padding(innerPadding)
            .padding(AppSpacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
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
            )
            Text(
              text = stringResource(R.string.settings_placeholder_title),
              style = MaterialTheme.typography.titleMedium,
              color = glass.textPrimary,
            )
          }

          GlassIconButton(
            icon = Icons.Outlined.Menu,
            contentDescription = stringResource(R.string.home_menu_desc),
            onClick = onOpenMenu,
          )
        }

        GlassCard(
          elevated = true,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            Text(
              text = "Visual & Motion Settings",
              style = MaterialTheme.typography.titleSmall,
              color = glass.textPrimary,
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column {
                Text(
                  text = "Dark Theme",
                  style = MaterialTheme.typography.bodyMedium,
                  color = glass.textPrimary,
                )
                Text(
                  text = if (isDarkTheme) "Dark Glass (Active)" else "Light Glass",
                  style = MaterialTheme.typography.bodySmall,
                  color = glass.textSecondary,
                )
              }
              GlassIconButton(
                icon = Icons.Outlined.Contrast,
                contentDescription = "Toggle theme",
                isActive = !isDarkTheme,
                onClick = onToggleTheme,
              )
            }

            GlassDivider()

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column {
                Text(
                  text = "Reduced Motion",
                  style = MaterialTheme.typography.bodyMedium,
                  color = glass.textPrimary,
                )
                Text(
                  text = if (reducedMotion) "Motion disabled (Static 3D Orb)" else "Fluid animations active",
                  style = MaterialTheme.typography.bodySmall,
                  color = glass.textSecondary,
                )
              }
              GlassIconButton(
                icon = Icons.Outlined.MotionPhotosOff,
                contentDescription = "Toggle reduced motion",
                isActive = reducedMotion,
                onClick = onToggleReducedMotion,
              )
            }
          }
        }
      }
    }
  }
}


