package com.example.ui.screens.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.api.EmailSecurityValidator
import com.example.data.api.FirebaseRestAuthClient
import com.example.data.repository.UserPreferencesRepository
import com.example.ui.components.AmbientGlassBackground
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
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.RoyalViolet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Avatar color presets for 3D glowing profile rings & initials when no gallery photo is selected
 * or as the accent ring around a selected profile photo.
 */
enum class ProfileAvatarPreset(
  val id: String,
  val label: String,
  val primaryColor: Color,
  val secondaryColor: Color,
) {
  CYBER_CYAN("CYBER_CYAN", "Cyber Cyan", Color(0xFF06B6D4), Color(0xFF3B82F6)),
  ROYAL_VIOLET("ROYAL_VIOLET", "Royal Violet", Color(0xFF8B5CF6), Color(0xFFEC4899)),
  EMERALD_MATRIX("EMERALD_MATRIX", "Emerald", Color(0xFF10B981), Color(0xFF06B6D4)),
  GOLD_LUXE("GOLD_LUXE", "Gold Luxe", Color(0xFFF59E0B), Color(0xFFEF4444)),
  CRIMSON_PULSE("CRIMSON_PULSE", "Crimson", Color(0xFFF43F5E), Color(0xFF8B5CF6));

  companion object {
    fun fromId(id: String?): ProfileAvatarPreset {
      return entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: CYBER_CYAN
    }
  }
}

/**
 * Reusable User Avatar Badge that renders either the user's selected gallery photo
 * or a glowing 3D gradient avatar with their initials.
 */
@Composable
fun UserAvatarBadge(
  name: String,
  email: String,
  avatarPath: String,
  avatarPresetId: String,
  size: Dp = 40.dp,
  modifier: Modifier = Modifier,
) {
  val preset = remember(avatarPresetId) { ProfileAvatarPreset.fromId(avatarPresetId) }
  val bitmapState by
    produceState<ImageBitmap?>(initialValue = null, key1 = avatarPath) {
      value =
        if (avatarPath.isBlank()) {
          null
        } else {
          withContext(Dispatchers.IO) {
            try {
              val file = File(avatarPath)
              if (file.exists() && file.length() > 0L) {
                BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
              } else {
                null
              }
            } catch (_: Exception) {
              null
            }
          }
        }
    }

  val initials =
    remember(name, email) {
      val clean = name.trim().ifBlank { email.trim().substringBefore("@") }
      val parts = clean.split(" ").filter { it.isNotBlank() }
      when {
        parts.size >= 2 -> "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
        clean.isNotBlank() -> clean.take(2).uppercase()
        else -> "U"
      }
    }

  Box(
    modifier =
      modifier
        .size(size)
        .clip(CircleShape)
        .background(
          Brush.linearGradient(
            colors = listOf(preset.primaryColor, preset.secondaryColor)
          )
        )
        .border(
          width = 1.5.dp,
          brush =
            Brush.linearGradient(
              colors = listOf(Color.White.copy(alpha = 0.75f), preset.primaryColor)
            ),
          shape = CircleShape,
        ),
    contentAlignment = Alignment.Center,
  ) {
    val loadedBitmap = bitmapState
    if (loadedBitmap != null) {
      Image(
        bitmap = loadedBitmap,
        contentDescription = "User profile photo",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().clip(CircleShape),
      )
    } else {
      Text(
        text = initials,
        style =
          if (size >= 64.dp) MaterialTheme.typography.headlineSmall
          else if (size >= 40.dp) MaterialTheme.typography.titleSmall
          else MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.ExtraBold,
        color = Color.White,
      )
    }
  }
}

/**
 * Copies & downscales a selected gallery image URI into the app's private files directory
 * so the user's profile picture persists across app restarts without requiring storage permissions.
 */
private suspend fun copyAndSaveProfileImage(
  context: Context,
  sourceUri: Uri,
  uid: String,
): String? =
  withContext(Dispatchers.IO) {
    try {
      val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return@withContext null
      val originalBitmap = inputStream.use { BitmapFactory.decodeStream(it) } ?: return@withContext null
      val maxDim = 512
      val width = originalBitmap.width.coerceAtLeast(1)
      val height = originalBitmap.height.coerceAtLeast(1)
      val scale = minOf(maxDim.toFloat() / width, maxDim.toFloat() / height, 1f)
      val targetW = (width * scale).toInt().coerceAtLeast(1)
      val targetH = (height * scale).toInt().coerceAtLeast(1)
      val scaledBitmap =
        if (scale < 1f) {
          Bitmap.createScaledBitmap(originalBitmap, targetW, targetH, true)
        } else {
          originalBitmap
        }

      val safeUid = uid.replace(Regex("[^A-Za-z0-9_\\-]"), "_").ifBlank { "default" }
      val outFile = File(context.filesDir, "profile_avatar_${safeUid}_${System.currentTimeMillis()}.jpg")
      FileOutputStream(outFile).use { out ->
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
      }
      outFile.absolutePath
    } catch (_: Exception) {
      null
    }
  }

/**
 * Dedicated User Profile & Account Security Center Screen.
 *
 * Provides complete control over:
 * 1. Profile Image (Select photo from gallery via Android Photo Picker, remove photo, or pick 3D avatar theme)
 * 2. Personal Identity (Full Name, Role Badge, Bio/Status, Recovery Phone) synced to Cloud Firestore
 * 3. Email ID Management (View verified Email ID, Change Email ID with Anti-Temp-Mail Firewall, Send Verification Email)
 * 4. Password & Security (Change Password with entropy meter, Send Password Reset Email)
 * 5. Private Chat Vault Controls (View isolated chat count, Clear My Chats, Open Settings)
 * 6. Account Lifecycle (Sign Out, Delete Account Permanently from Firebase + Firestore + Device)
 */
@Composable
fun UserProfileScreen(
  preferencesRepository: UserPreferencesRepository,
  savedChatsCount: Int,
  onOpenMenu: () -> Unit,
  onBack: () -> Unit,
  onOpenSettings: () -> Unit,
  onClearAllMyChats: () -> Unit,
  onAccountSignedOutOrDeleted: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val glass = AppThemeTokens.glass
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val preferences by preferencesRepository.preferencesFlow.collectAsState()

  var nameDraft by rememberSaveable { mutableStateOf(preferences.userName) }
  var roleDraft by rememberSaveable { mutableStateOf(preferences.userRoleBadge) }
  var bioDraft by rememberSaveable { mutableStateOf(preferences.userBio) }
  var phoneDraft by rememberSaveable { mutableStateOf(preferences.userPhone) }

  var newEmailDraft by rememberSaveable { mutableStateOf(preferences.userEmail) }
  var newPasswordDraft by rememberSaveable { mutableStateOf("") }
  var confirmPasswordDraft by rememberSaveable { mutableStateOf("") }
  var newPasswordVisible by rememberSaveable { mutableStateOf(false) }

  var isBusy by remember { mutableStateOf(false) }
  var statusBannerMessage by remember { mutableStateOf<String?>(null) }
  var isStatusError by remember { mutableStateOf(false) }

  var showDeleteAccountDialog by rememberSaveable { mutableStateOf(false) }
  var deleteConfirmInput by rememberSaveable { mutableStateOf("") }
  var showClearChatsDialog by rememberSaveable { mutableStateOf(false) }

  LaunchedEffect(preferences.userName, preferences.userEmail, preferences.userRoleBadge) {
    if (nameDraft.isBlank()) nameDraft = preferences.userName
    if (newEmailDraft.isBlank()) newEmailDraft = preferences.userEmail
  }

  // Zero-permission Android Photo Picker for selecting profile picture
  val photoPickerLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
      if (uri != null) {
        scope.launch {
          isBusy = true
          val savedPath =
            copyAndSaveProfileImage(
              context = context,
              sourceUri = uri,
              uid = preferences.userUid,
            )
          isBusy = false
          if (savedPath != null) {
            preferencesRepository.updateUserAvatarPath(savedPath)
            isStatusError = false
            statusBannerMessage = "Profile photo updated & saved to your account ✓"
          } else {
            isStatusError = true
            statusBannerMessage = "Could not load the selected image. Please try another photo."
          }
        }
      }
    }

  val roleOptions =
    remember {
      listOf(
        "Pro Member",
        "Developer",
        "AI Creator",
        "Founder",
        "Student",
        "Researcher",
      )
    }

  val newEmailValidation =
    remember(newEmailDraft) {
      EmailSecurityValidator.validateEmail(newEmailDraft)
    }

  val passwordStrengthScore =
    remember(newPasswordDraft) {
      var score = 0
      if (newPasswordDraft.length >= 8) score++
      if (newPasswordDraft.any { it.isUpperCase() } && newPasswordDraft.any { it.isLowerCase() }) score++
      if (newPasswordDraft.any { it.isDigit() }) score++
      if (newPasswordDraft.any { !it.isLetterOrDigit() } || newPasswordDraft.length >= 12) score++
      score
    }

  AmbientGlassBackground(
    isAiActive = isBusy,
    moodHint = "cyber intelligence",
    modifier = modifier.testTag("user_profile_screen"),
  ) {
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
              contentDescription = "Back",
              onClick = onBack,
              modifier = Modifier.testTag("profile_back_button"),
            )
            Column {
              Text(
                text = "My Profile & Account",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = glass.textPrimary,
              )
              Text(
                text = "Firebase Vault (${FirebaseRestAuthClient.PROJECT_ID}) • UID Isolated",
                style = MaterialTheme.typography.labelSmall,
                color = glass.accentCyan,
              )
            }
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
          ) {
            GlassIconButton(
              icon = Icons.Outlined.Settings,
              contentDescription = "Settings",
              onClick = onOpenSettings,
              modifier = Modifier.testTag("profile_top_settings_button"),
            )
            GlassIconButton(
              icon = Icons.Outlined.Menu,
              contentDescription = "Open Menu",
              onClick = onOpenMenu,
              modifier = Modifier.testTag("profile_menu_button"),
            )
          }
        }

        // -----------------------------------------------------------------
        // Status Feedback Banner
        // -----------------------------------------------------------------
        AnimatedVisibility(
          visible = statusBannerMessage != null,
          enter = fadeIn() + expandVertically(),
          exit = fadeOut() + shrinkVertically(),
        ) {
          statusBannerMessage?.let { msg ->
            val bannerColor = if (isStatusError) glass.statusError else glass.statusSuccess
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .padding(horizontal = AppSpacing.screenHorizontal, vertical = 4.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .background(bannerColor.copy(alpha = 0.16f))
                  .border(AppBorder.hairline, bannerColor.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                  .padding(horizontal = 12.dp, vertical = 10.dp)
                  .testTag("profile_status_banner"),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = if (isStatusError) Icons.Outlined.WarningAmber else Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = bannerColor,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = glass.textPrimary,
                modifier = Modifier.weight(1f),
              )
            }
          }
        }

        // -----------------------------------------------------------------
        // Scrollable Profile Sections
        // -----------------------------------------------------------------
        Column(
          modifier =
            Modifier.weight(1f)
              .fillMaxWidth()
              .verticalScroll(rememberScrollState())
              .padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.sm),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = AppSpacing.maxContentWidth),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
          ) {
            // ===============================================================
            // 1. HERO PROFILE CARD & IMAGE SELECTOR
            // ===============================================================
            GlassSurface(
              elevated = true,
              modifier = Modifier.fillMaxWidth().testTag("profile_hero_card"),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
              ) {
                // Interactive Avatar + Camera Overlay Button
                Box(
                  contentAlignment = Alignment.BottomEnd,
                  modifier =
                    Modifier.clickable {
                      photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                      )
                    },
                ) {
                  UserAvatarBadge(
                    name = preferences.userName,
                    email = preferences.userEmail,
                    avatarPath = preferences.userAvatarPath,
                    avatarPresetId = preferences.userAvatarPreset,
                    size = 92.dp,
                    modifier = Modifier.testTag("profile_avatar_preview"),
                  )
                  Box(
                    modifier =
                      Modifier.size(30.dp)
                        .clip(CircleShape)
                        .background(glass.accentPrimary)
                        .border(1.5.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                  ) {
                    Icon(
                      imageVector = Icons.Outlined.CameraAlt,
                      contentDescription = "Select profile photo",
                      tint = Color.White,
                      modifier = Modifier.size(16.dp),
                    )
                  }
                }

                // Name, Email & Verified Badges
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Text(
                    text = preferences.userName.ifBlank { "Astryn Member" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = glass.textPrimary,
                    textAlign = TextAlign.Center,
                  )
                  Text(
                    text = preferences.userEmail.ifBlank { "Verified Account" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = glass.accentCyan,
                    fontWeight = FontWeight.SemiBold,
                  )
                  if (preferences.userBio.isNotBlank()) {
                    Text(
                      text = preferences.userBio,
                      style = MaterialTheme.typography.bodySmall,
                      color = glass.textSecondary,
                      textAlign = TextAlign.Center,
                    )
                  }
                }

                // Account Stats Row (Role Badge, Saved Chats, Vault ID)
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceEvenly,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  ProfileStatPill(
                    label = "Role",
                    value = preferences.userRoleBadge,
                  )
                  ProfileStatPill(
                    label = "My Chats",
                    value = "$savedChatsCount Isolated",
                  )
                  ProfileStatPill(
                    label = "Vault ID",
                    value = preferences.userUid.takeLast(6).uppercase().ifBlank { "ACTIVE" },
                  )
                }

                // Select Image & Remove Image Buttons
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                ) {
                  GlassButton(
                    text = "Select Profile Image",
                    leadingIcon = Icons.Outlined.PhotoLibrary,
                    variant = GlassButtonVariant.PRIMARY,
                    onClick = {
                      photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                      )
                    },
                    modifier = Modifier.weight(1f).testTag("profile_select_image_button"),
                  )
                  if (preferences.userAvatarPath.isNotBlank()) {
                    GlassButton(
                      text = "Remove Photo",
                      variant = GlassButtonVariant.SECONDARY,
                      onClick = {
                        preferencesRepository.updateUserAvatarPath("")
                        isStatusError = false
                        statusBannerMessage = "Custom photo removed. Using 3D glowing avatar."
                      },
                      modifier = Modifier.testTag("profile_remove_image_button"),
                    )
                  }
                }

                // 3D Avatar Theme / Ring Color Selector
                Column(
                  modifier = Modifier.fillMaxWidth(),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Text(
                    text = "Avatar Glow & Ring Theme",
                    style = MaterialTheme.typography.labelSmall,
                    color = glass.textSecondary,
                    fontWeight = FontWeight.SemiBold,
                  )
                  Row(
                    modifier =
                      Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    ProfileAvatarPreset.entries.forEach { preset ->
                      val isSelected =
                        preferences.userAvatarPreset.equals(preset.id, ignoreCase = true)
                      Row(
                        modifier =
                          Modifier.clip(AppRadius.shapePill)
                            .background(
                              if (isSelected) preset.primaryColor.copy(alpha = 0.22f)
                              else glass.surfaceStandard
                            )
                            .border(
                              width = if (isSelected) AppBorder.focused else AppBorder.hairline,
                              color = if (isSelected) preset.primaryColor else glass.borderSubtle,
                              shape = AppRadius.shapePill,
                            )
                            .clickable {
                              preferencesRepository.updateUserAvatarPreset(preset.id)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("profile_avatar_preset_${preset.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                      ) {
                        Box(
                          modifier =
                            Modifier.size(12.dp)
                              .clip(CircleShape)
                              .background(
                                Brush.linearGradient(
                                  listOf(preset.primaryColor, preset.secondaryColor)
                                )
                              )
                        )
                        Text(
                          text = preset.label,
                          style = MaterialTheme.typography.labelSmall,
                          fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                          color = if (isSelected) glass.textPrimary else glass.textSecondary,
                        )
                      }
                    }
                  }
                }
              }
            }

            // ===============================================================
            // 2. EDIT PERSONAL PROFILE DETAILS (NAME, ROLE, BIO, PHONE)
            // ===============================================================
            GlassSurface(
              elevated = false,
              modifier = Modifier.fillMaxWidth().testTag("profile_edit_info_card"),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Badge,
                    contentDescription = null,
                    tint = glass.accentCyan,
                    modifier = Modifier.size(20.dp),
                  )
                  Column {
                    Text(
                      text = "Personal Profile Details",
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.Bold,
                      color = glass.textPrimary,
                    )
                    Text(
                      text = "Update your display name, role badge, bio & recovery contact",
                      style = MaterialTheme.typography.labelSmall,
                      color = glass.textSecondary,
                    )
                  }
                }

                GlassTextField(
                  value = nameDraft,
                  onValueChange = { nameDraft = it },
                  placeholder = "Your Full Name",
                  singleLine = true,
                  leadingContent = {
                    Icon(
                      imageVector = Icons.Outlined.Person,
                      contentDescription = null,
                      tint = glass.accentCyan,
                      modifier = Modifier.size(18.dp),
                    )
                  },
                  modifier = Modifier.fillMaxWidth().testTag("profile_name_input"),
                )

                // Role Badge Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  Text(
                    text = "Profile Role Badge",
                    style = MaterialTheme.typography.labelMedium,
                    color = glass.textSecondary,
                    fontWeight = FontWeight.SemiBold,
                  )
                  Row(
                    modifier =
                      Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    roleOptions.forEach { role ->
                      val selected = roleDraft == role
                      Box(
                        modifier =
                          Modifier.clip(AppRadius.shapePill)
                            .background(
                              if (selected) glass.accentPrimary.copy(alpha = 0.24f)
                              else glass.surfaceStandard
                            )
                            .border(
                              width = if (selected) AppBorder.focused else AppBorder.hairline,
                              color = if (selected) glass.accentCyan else glass.borderSubtle,
                              shape = AppRadius.shapePill,
                            )
                            .clickable { roleDraft = role }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("profile_role_chip_$role"),
                      ) {
                        Text(
                          text = role,
                          style = MaterialTheme.typography.labelSmall,
                          fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                          color = if (selected) glass.accentCyan else glass.textSecondary,
                        )
                      }
                    }
                  }
                }

                GlassTextField(
                  value = bioDraft,
                  onValueChange = { bioDraft = it },
                  placeholder = "Your Bio / Status (e.g. Building next-gen AI apps)",
                  singleLine = true,
                  leadingContent = {
                    Icon(
                      imageVector = Icons.Outlined.Edit,
                      contentDescription = null,
                      tint = glass.accentCyan,
                      modifier = Modifier.size(18.dp),
                    )
                  },
                  modifier = Modifier.fillMaxWidth().testTag("profile_bio_input"),
                )

                GlassTextField(
                  value = phoneDraft,
                  onValueChange = { phoneDraft = it },
                  placeholder = "Recovery Phone Number (optional)",
                  singleLine = true,
                  keyboardOptions =
                    KeyboardOptions(
                      keyboardType = KeyboardType.Phone,
                      imeAction = ImeAction.Done,
                    ),
                  leadingContent = {
                    Icon(
                      imageVector = Icons.Outlined.Phone,
                      contentDescription = null,
                      tint = glass.accentCyan,
                      modifier = Modifier.size(18.dp),
                    )
                  },
                  modifier = Modifier.fillMaxWidth().testTag("profile_phone_input"),
                )

                GlassButton(
                  text = if (isBusy) "Saving Profile…" else "Save Profile Changes",
                  leadingIcon = Icons.Outlined.CloudDone,
                  variant = GlassButtonVariant.PRIMARY,
                  enabled = !isBusy && nameDraft.trim().length >= 2,
                  onClick = {
                    scope.launch {
                      isBusy = true
                      preferencesRepository.updateUserProfile(
                        name = nameDraft,
                        roleBadge = roleDraft,
                        bio = bioDraft,
                        phone = phoneDraft,
                      )
                      val res =
                        FirebaseRestAuthClient.updateUserProfileInCloud(
                          uid = preferences.userUid,
                          idToken = preferences.authIdToken,
                          name = nameDraft,
                          email = preferences.userEmail,
                          roleBadge = roleDraft,
                          bio = bioDraft,
                          phone = phoneDraft,
                          provider = preferences.authProvider,
                        )
                      isBusy = false
                      res
                        .onSuccess { msg ->
                          isStatusError = false
                          statusBannerMessage = msg
                        }
                        .onFailure { err ->
                          isStatusError = true
                          statusBannerMessage = err.message ?: "Failed to update profile."
                        }
                    }
                  },
                  modifier = Modifier.fillMaxWidth().testTag("profile_save_info_button"),
                )
              }
            }

            // ===============================================================
            // 3. EMAIL ID MANAGEMENT & VERIFICATION
            // ===============================================================
            GlassSurface(
              elevated = false,
              modifier = Modifier.fillMaxWidth().testTag("profile_email_section_card"),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Email,
                    contentDescription = null,
                    tint = glass.accentCyan,
                    modifier = Modifier.size(20.dp),
                  )
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "Email ID & Verification",
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.Bold,
                      color = glass.textPrimary,
                    )
                    Text(
                      text = "Protected by Anti-Temp-Mail Firewall • Only real Email IDs allowed",
                      style = MaterialTheme.typography.labelSmall,
                      color = glass.textSecondary,
                    )
                  }
                  Box(
                    modifier =
                      Modifier.clip(AppRadius.shapePill)
                        .background(glass.statusSuccess.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                  ) {
                    Text(
                      text = "Real Email ✓",
                      style = MaterialTheme.typography.labelSmall,
                      color = glass.statusSuccess,
                      fontWeight = FontWeight.Bold,
                    )
                  }
                }

                GlassTextField(
                  value = newEmailDraft,
                  onValueChange = { newEmailDraft = it },
                  placeholder = "Enter new real Email ID (e.g. name@gmail.com)",
                  singleLine = true,
                  keyboardOptions =
                    KeyboardOptions(
                      keyboardType = KeyboardType.Email,
                      imeAction = ImeAction.Done,
                    ),
                  leadingContent = {
                    Icon(
                      imageVector = Icons.Outlined.Email,
                      contentDescription = null,
                      tint =
                        if (newEmailValidation.isTempOrDisposableBlocked) glass.statusError
                        else glass.accentCyan,
                      modifier = Modifier.size(18.dp),
                    )
                  },
                  modifier = Modifier.fillMaxWidth().testTag("profile_new_email_input"),
                )

                if (newEmailDraft.isNotBlank() && !newEmailValidation.isValidRealEmail) {
                  Text(
                    text = newEmailValidation.statusMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = glass.statusError,
                    fontWeight = FontWeight.SemiBold,
                  )
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                ) {
                  GlassButton(
                    text = "Update Email ID",
                    variant = GlassButtonVariant.PRIMARY,
                    enabled =
                      !isBusy &&
                        newEmailValidation.isValidRealEmail &&
                        !newEmailDraft.trim().equals(preferences.userEmail, ignoreCase = true),
                    onClick = {
                      scope.launch {
                        isBusy = true
                        val result =
                          FirebaseRestAuthClient.changeUserEmailInCloud(
                            uid = preferences.userUid,
                            idToken = preferences.authIdToken,
                            newEmail = newEmailDraft,
                            name = preferences.userName,
                            roleBadge = preferences.userRoleBadge,
                            provider = preferences.authProvider,
                          )
                        isBusy = false
                        result
                          .onSuccess { (updatedEmail, updatedToken) ->
                            preferencesRepository.updateUserEmail(updatedEmail, updatedToken)
                            isStatusError = false
                            statusBannerMessage =
                              "Email ID updated to $updatedEmail & synced to Firebase ✓"
                          }
                          .onFailure { err ->
                            isStatusError = true
                            statusBannerMessage = err.message ?: "Could not update Email ID."
                          }
                      }
                    },
                    modifier = Modifier.weight(1f).testTag("profile_update_email_button"),
                  )

                  GlassButton(
                    text = "Verify Email",
                    leadingIcon = Icons.Outlined.MarkEmailRead,
                    variant = GlassButtonVariant.SECONDARY,
                    enabled = !isBusy,
                    onClick = {
                      scope.launch {
                        isBusy = true
                        val res =
                          FirebaseRestAuthClient.sendVerificationEmailToUser(
                            idToken = preferences.authIdToken,
                            email = preferences.userEmail,
                          )
                        isBusy = false
                        res
                          .onSuccess { msg ->
                            isStatusError = false
                            statusBannerMessage = msg
                          }
                          .onFailure { err ->
                            isStatusError = true
                            statusBannerMessage = err.message ?: "Could not send verification link."
                          }
                      }
                    },
                    modifier = Modifier.weight(1f).testTag("profile_verify_email_button"),
                  )
                }
              }
            }

            // ===============================================================
            // 4. PASSWORD CHANGE & ACCOUNT SECURITY
            // ===============================================================
            GlassSurface(
              elevated = false,
              modifier = Modifier.fillMaxWidth().testTag("profile_password_section_card"),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = glass.accentCyan,
                    modifier = Modifier.size(20.dp),
                  )
                  Column {
                    Text(
                      text = "Password Change & Security",
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.Bold,
                      color = glass.textPrimary,
                    )
                    Text(
                      text = "Set a new password or send a password reset link to your email",
                      style = MaterialTheme.typography.labelSmall,
                      color = glass.textSecondary,
                    )
                  }
                }

                GlassTextField(
                  value = newPasswordDraft,
                  onValueChange = { newPasswordDraft = it },
                  placeholder = "New Password (min 8 chars, letters + numbers/symbols)",
                  singleLine = true,
                  visualTransformation =
                    if (newPasswordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                  keyboardOptions =
                    KeyboardOptions(
                      keyboardType = KeyboardType.Password,
                      imeAction = ImeAction.Next,
                    ),
                  leadingContent = {
                    Icon(
                      imageVector = Icons.Outlined.Key,
                      contentDescription = null,
                      tint = glass.accentCyan,
                      modifier = Modifier.size(18.dp),
                    )
                  },
                  trailingContent = {
                    Icon(
                      imageVector =
                        if (newPasswordVisible) Icons.Outlined.VisibilityOff
                        else Icons.Outlined.Visibility,
                      contentDescription = "Toggle password visibility",
                      tint = glass.textSecondary,
                      modifier =
                        Modifier.size(20.dp)
                          .clickable { newPasswordVisible = !newPasswordVisible },
                    )
                  },
                  modifier = Modifier.fillMaxWidth().testTag("profile_new_password_input"),
                )

                // Password Strength Bar
                if (newPasswordDraft.isNotEmpty()) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    repeat(4) { step ->
                      val active = step < passwordStrengthScore
                      val barColor =
                        when {
                          passwordStrengthScore <= 1 -> glass.statusError
                          passwordStrengthScore == 2 -> glass.statusWarning
                          else -> glass.statusSuccess
                        }
                      Box(
                        modifier =
                          Modifier.weight(1f)
                            .height(5.dp)
                            .clip(AppRadius.shapePill)
                            .background(if (active) barColor else glass.borderSubtle)
                      )
                    }
                  }
                }

                GlassTextField(
                  value = confirmPasswordDraft,
                  onValueChange = { confirmPasswordDraft = it },
                  placeholder = "Confirm New Password",
                  singleLine = true,
                  visualTransformation =
                    if (newPasswordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                  keyboardOptions =
                    KeyboardOptions(
                      keyboardType = KeyboardType.Password,
                      imeAction = ImeAction.Done,
                    ),
                  leadingContent = {
                    Icon(
                      imageVector = Icons.Outlined.Shield,
                      contentDescription = null,
                      tint = glass.accentCyan,
                      modifier = Modifier.size(18.dp),
                    )
                  },
                  modifier = Modifier.fillMaxWidth().testTag("profile_confirm_password_input"),
                )

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                ) {
                  GlassButton(
                    text = "Change Password",
                    variant = GlassButtonVariant.PRIMARY,
                    enabled = !isBusy && newPasswordDraft.length >= 8,
                    onClick = {
                      if (newPasswordDraft != confirmPasswordDraft) {
                        isStatusError = true
                        statusBannerMessage =
                          "New password and confirmation password do not match."
                        return@GlassButton
                      }
                      scope.launch {
                        isBusy = true
                        val res =
                          FirebaseRestAuthClient.changeUserPasswordInCloud(
                            idToken = preferences.authIdToken,
                            email = preferences.userEmail,
                            newPassword = newPasswordDraft,
                          )
                        isBusy = false
                        res
                          .onSuccess { (msg, refreshedToken) ->
                            if (refreshedToken.isNotBlank()) {
                              preferencesRepository.updateAuthIdToken(refreshedToken)
                            }
                            newPasswordDraft = ""
                            confirmPasswordDraft = ""
                            isStatusError = false
                            statusBannerMessage = msg
                          }
                          .onFailure { err ->
                            isStatusError = true
                            statusBannerMessage = err.message ?: "Failed to change password."
                          }
                      }
                    },
                    modifier = Modifier.weight(1f).testTag("profile_change_password_button"),
                  )

                  GlassButton(
                    text = "Reset via Email",
                    variant = GlassButtonVariant.SECONDARY,
                    enabled = !isBusy && preferences.userEmail.isNotBlank(),
                    onClick = {
                      scope.launch {
                        isBusy = true
                        val res =
                          FirebaseRestAuthClient.sendPasswordResetEmail(preferences.userEmail)
                        isBusy = false
                        res
                          .onSuccess { msg ->
                            isStatusError = false
                            statusBannerMessage = msg
                          }
                          .onFailure { err ->
                            isStatusError = true
                            statusBannerMessage = err.message ?: "Failed to send reset email."
                          }
                      }
                    },
                    modifier = Modifier.weight(1f).testTag("profile_send_reset_button"),
                  )
                }
              }
            }

            // ===============================================================
            // 5. PRIVATE CHAT VAULT & ACCOUNT DATA CONTROLS
            // ===============================================================
            GlassSurface(
              elevated = false,
              modifier = Modifier.fillMaxWidth().testTag("profile_vault_section_card"),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.VerifiedUser,
                    contentDescription = null,
                    tint = glass.accentCyan,
                    modifier = Modifier.size(20.dp),
                  )
                  Column {
                    Text(
                      text = "My Private Chat Vault & Workspace",
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.Bold,
                      color = glass.textPrimary,
                    )
                    Text(
                      text = "Your chats are 100% isolated to your account (${preferences.userEmail})",
                      style = MaterialTheme.typography.labelSmall,
                      color = glass.textSecondary,
                    )
                  }
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                ) {
                  GlassButton(
                    text = "AI Settings & BG",
                    leadingIcon = Icons.Outlined.Settings,
                    variant = GlassButtonVariant.SECONDARY,
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f).testTag("profile_open_settings_button"),
                  )
                  GlassButton(
                    text = "Clear My Chats ($savedChatsCount)",
                    leadingIcon = Icons.Outlined.DeleteOutline,
                    variant = GlassButtonVariant.SECONDARY,
                    onClick = { showClearChatsDialog = true },
                    modifier = Modifier.weight(1f).testTag("profile_clear_chats_button"),
                  )
                }
              }
            }

            // ===============================================================
            // 6. DANGER ZONE: SIGN OUT & DELETE ACCOUNT PERMANENTLY
            // ===============================================================
            GlassSurface(
              elevated = false,
              modifier = Modifier.fillMaxWidth().testTag("profile_danger_zone_card"),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Security,
                    contentDescription = null,
                    tint = glass.statusError,
                    modifier = Modifier.size(20.dp),
                  )
                  Column {
                    Text(
                      text = "Session & Account Deletion",
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.Bold,
                      color = glass.textPrimary,
                    )
                    Text(
                      text = "Sign out safely or permanently delete your account & cloud data",
                      style = MaterialTheme.typography.labelSmall,
                      color = glass.textSecondary,
                    )
                  }
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                ) {
                  GlassButton(
                    text = "Sign Out",
                    leadingIcon = Icons.AutoMirrored.Outlined.Logout,
                    variant = GlassButtonVariant.SECONDARY,
                    onClick = {
                      preferencesRepository.signOutUser()
                      onAccountSignedOutOrDeleted()
                    },
                    modifier = Modifier.weight(1f).testTag("profile_sign_out_button"),
                  )

                  GlassButton(
                    text = "Delete Account",
                    leadingIcon = Icons.Outlined.DeleteForever,
                    variant = GlassButtonVariant.DESTRUCTIVE,
                    onClick = {
                      deleteConfirmInput = ""
                      showDeleteAccountDialog = true
                    },
                    modifier = Modifier.weight(1f).testTag("profile_delete_account_button"),
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(AppSpacing.lg))
          }
        }
      }
    }
  }

  // =========================================================================
  // CLEAR MY CHATS CONFIRMATION DIALOG
  // =========================================================================
  if (showClearChatsDialog) {
    AlertDialog(
      onDismissRequest = { showClearChatsDialog = false },
      containerColor = glass.surfaceElevated,
      titleContentColor = glass.textPrimary,
      textContentColor = glass.textSecondary,
      title = {
        Text(
          text = "Clear All Your Chats?",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
      },
      text = {
        Text(
          text = "This will permanently delete all conversations belonging to ${preferences.userEmail} from this device and your private cloud vault.",
          style = MaterialTheme.typography.bodySmall,
        )
      },
      confirmButton = {
        GlassButton(
          text = "Clear My Chats",
          variant = GlassButtonVariant.DESTRUCTIVE,
          onClick = {
            showClearChatsDialog = false
            onClearAllMyChats()
            isStatusError = false
            statusBannerMessage = "All chats for your account have been cleared ✓"
          },
          modifier = Modifier.testTag("profile_confirm_clear_chats_button"),
        )
      },
      dismissButton = {
        GlassButton(
          text = "Cancel",
          variant = GlassButtonVariant.SECONDARY,
          onClick = { showClearChatsDialog = false },
        )
      },
    )
  }

  // =========================================================================
  // PERMANENT DELETE ACCOUNT CONFIRMATION DIALOG
  // =========================================================================
  if (showDeleteAccountDialog) {
    AlertDialog(
      onDismissRequest = { showDeleteAccountDialog = false },
      containerColor = glass.surfaceElevated,
      titleContentColor = glass.textPrimary,
      textContentColor = glass.textSecondary,
      title = {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.DeleteForever,
            contentDescription = null,
            tint = glass.statusError,
          )
          Text(
            text = "Delete Account Permanently?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
        }
      },
      text = {
        Column(
          modifier = Modifier.fillMaxWidth().testTag("profile_delete_account_dialog"),
          verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
          Text(
            text = "This will permanently delete your account (${preferences.userEmail}), profile, and all isolated chat history from Firebase (${FirebaseRestAuthClient.PROJECT_ID}), Cloud Firestore, and this device.",
            style = MaterialTheme.typography.bodySmall,
            color = glass.textSecondary,
          )
          Text(
            text = "Type DELETE below to confirm permanent account deletion:",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = glass.statusError,
          )
          GlassTextField(
            value = deleteConfirmInput,
            onValueChange = { deleteConfirmInput = it },
            placeholder = "Type DELETE to confirm",
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("profile_delete_confirm_input"),
          )
        }
      },
      confirmButton = {
        GlassButton(
          text = if (isBusy) "Deleting…" else "Delete My Account",
          variant = GlassButtonVariant.DESTRUCTIVE,
          enabled =
            !isBusy &&
              (deleteConfirmInput.trim().equals("DELETE", ignoreCase = true) ||
                deleteConfirmInput.trim().equals(preferences.userEmail, ignoreCase = true)),
          onClick = {
            scope.launch {
              isBusy = true
              val uidToDelete = preferences.userUid
              val tokenToDelete = preferences.authIdToken
              onClearAllMyChats()
              FirebaseRestAuthClient.deleteUserAccountFromCloud(
                uid = uidToDelete,
                idToken = tokenToDelete,
              )
              preferencesRepository.deleteUserAccount()
              isBusy = false
              showDeleteAccountDialog = false
              onAccountSignedOutOrDeleted()
            }
          },
          modifier = Modifier.testTag("profile_confirm_delete_account_button"),
        )
      },
      dismissButton = {
        GlassButton(
          text = "Cancel",
          variant = GlassButtonVariant.SECONDARY,
          onClick = { showDeleteAccountDialog = false },
        )
      },
    )
  }
}

@Composable
private fun ProfileStatPill(
  label: String,
  value: String,
) {
  val glass = AppThemeTokens.glass
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier =
      Modifier.clip(RoundedCornerShape(12.dp))
        .background(glass.surfaceStandard)
        .border(AppBorder.hairline, glass.borderSubtle, RoundedCornerShape(12.dp))
        .padding(horizontal = 12.dp, vertical = 8.dp),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
      color = glass.textMuted,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Bold,
      color = glass.accentCyan,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}
