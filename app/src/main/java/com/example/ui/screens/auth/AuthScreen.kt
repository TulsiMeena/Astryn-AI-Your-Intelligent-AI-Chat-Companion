package com.example.ui.screens.auth

import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.api.EmailSecurityValidator
import com.example.data.api.FirebaseRestAuthClient
import com.example.data.repository.UserPreferencesRepository
import com.example.ui.audio.AstrynSoundEffect
import com.example.ui.audio.AstrynSoundEngine
import com.example.ui.components.AmbientGlassBackground
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassButtonVariant
import com.example.ui.components.GlassDivider
import com.example.ui.components.GlassSurface
import com.example.ui.components.GlassTextField
import com.example.ui.theme.AppBorder
import com.example.ui.theme.AppRadius
import com.example.ui.theme.AppSpacing
import com.example.ui.theme.AppThemeTokens
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.RoyalViolet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private tailrec fun Context.findActivity(): Activity? =
  when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
  }

@Composable
fun AuthScreen(
  preferencesRepository: UserPreferencesRepository,
  initialIsSignUp: Boolean = false,
  onAuthComplete: () -> Unit,
  onBackOrSkip: () -> Unit = {},
) {
  val glass = AppThemeTokens.glass
  val context = LocalContext.current
  val focusManager = LocalFocusManager.current
  val scope = rememberCoroutineScope()
  val preferences by preferencesRepository.preferencesFlow.collectAsState()

  var isSignUpMode by rememberSaveable { mutableStateOf(initialIsSignUp) }
  var fullName by rememberSaveable {
    mutableStateOf(
      if (preferences.isLoggedIn && preferences.userName != "Guest User") preferences.userName
      else ""
    )
  }
  var email by rememberSaveable { mutableStateOf(preferences.userEmail) }
  var password by rememberSaveable { mutableStateOf("") }
  var confirmPassword by rememberSaveable { mutableStateOf("") }

  val defaultRoleBadge = "Pro Member"

  var passwordVisible by rememberSaveable { mutableStateOf(false) }
  var confirmPasswordVisible by rememberSaveable { mutableStateOf(false) }
  var isSubmitting by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var successBannerMessage by remember { mutableStateOf<String?>(null) }

  // Pending Email Verification Gate State
  var pendingVerificationEmail by rememberSaveable { mutableStateOf("") }
  var pendingVerificationUid by rememberSaveable { mutableStateOf("") }
  var pendingVerificationIdToken by rememberSaveable { mutableStateOf("") }
  var pendingVerificationName by rememberSaveable { mutableStateOf("") }
  var isCheckingVerification by remember { mutableStateOf(false) }
  var isResendingVerification by remember { mutableStateOf(false) }
  var resendCooldownSeconds by rememberSaveable { mutableIntStateOf(0) }

  // Silently warm up Firebase Web API key auto-discovery if not already provided via BuildConfig
  LaunchedEffect(Unit) {
    FirebaseRestAuthClient.resolveConfiguredApiKeyWithAutoDiscovery()
  }

  // Brute-Force Rate Limiter State
  var failedAttemptsCount by rememberSaveable { mutableIntStateOf(0) }
  var lockoutSecondsRemaining by rememberSaveable { mutableIntStateOf(0) }

  LaunchedEffect(lockoutSecondsRemaining) {
    if (lockoutSecondsRemaining > 0) {
      delay(1000L)
      lockoutSecondsRemaining -= 1
      if (lockoutSecondsRemaining == 0) {
        errorMessage = null
      }
    }
  }

  LaunchedEffect(resendCooldownSeconds) {
    if (resendCooldownSeconds > 0) {
      delay(1000L)
      resendCooldownSeconds -= 1
    }
  }

  // Auto-poll Firebase every 4 seconds while on the Email Verification screen
  LaunchedEffect(pendingVerificationEmail, pendingVerificationIdToken) {
    if (pendingVerificationEmail.isNotBlank()) {
      while (pendingVerificationEmail.isNotBlank()) {
        delay(4000L)
        if (!isCheckingVerification && pendingVerificationEmail.isNotBlank()) {
          val pollRes =
            FirebaseRestAuthClient.checkAccountEmailVerified(
              idToken = pendingVerificationIdToken,
              email = pendingVerificationEmail,
              password = password,
              uid = pendingVerificationUid,
              name = pendingVerificationName,
              roleBadge = defaultRoleBadge,
            )
          val verifiedResult = pollRes.getOrNull()
          if (verifiedResult != null && verifiedResult.isVerified) {
            AstrynSoundEngine.play(context, AstrynSoundEffect.SUCCESS)
            preferencesRepository.signInUser(
              uid = pendingVerificationUid,
              idToken = verifiedResult.refreshedIdToken,
              refreshToken = verifiedResult.refreshToken,
              name = pendingVerificationName,
              email = pendingVerificationEmail,
              roleBadge = defaultRoleBadge,
              provider = "Email",
            )
            pendingVerificationEmail = ""
            onAuthComplete()
            break
          }
        }
      }
    }
  }

  // Forgot Password Dialog State
  var showForgotPasswordDialog by rememberSaveable { mutableStateOf(false) }
  var resetEmailInput by rememberSaveable { mutableStateOf("") }
  var resetConfirmationMessage by remember { mutableStateOf<String?>(null) }

  // Privacy Policy & Rules Dialog State
  var showPrivacyRulesDialog by rememberSaveable { mutableStateOf(false) }

  // Google Sign-In Dialog State
  var showGoogleAccountDialog by rememberSaveable { mutableStateOf(false) }
  var googleEmailDraft by rememberSaveable { mutableStateOf("") }
  var googleNameDraft by rememberSaveable { mutableStateOf("") }
  var googleDialogError by remember { mutableStateOf<String?>(null) }

  if (preferences.isLoggedIn) {
    BackHandler(onBack = onBackOrSkip)
  }

  val emailSecurityResult by
    remember(email) {
      derivedStateOf { EmailSecurityValidator.validateEmail(email) }
    }

  val passwordStrengthScore by
    remember(password) {
      derivedStateOf {
        val hasMinLength = password.length >= 8
        val hasLetters = password.any { it.isLetter() }
        val hasDigitOrSymbol = password.any { it.isDigit() || !it.isLetterOrDigit() }
        when {
          password.isEmpty() -> 0
          !hasMinLength || !hasLetters -> 1
          hasMinLength && hasLetters && !hasDigitOrSymbol -> 3
          else -> 4
        }
      }
    }

  fun handlePrimarySubmit() {
    if (isSubmitting || lockoutSecondsRemaining > 0) return
    focusManager.clearFocus()
    errorMessage = null
    successBannerMessage = null

    val trimmedName = fullName.trim()
    val trimmedEmail = email.trim().lowercase()

    if (isSignUpMode && trimmedName.length < 2) {
      AstrynSoundEngine.play(context, AstrynSoundEffect.ERROR)
      errorMessage = "Please enter your full name."
      return
    }

    val emailValidation = EmailSecurityValidator.validateEmail(trimmedEmail)
    if (!emailValidation.isValidRealEmail) {
      AstrynSoundEngine.play(context, AstrynSoundEffect.ERROR)
      errorMessage = emailValidation.statusMessage
      return
    }

    val passwordValidationError =
      EmailSecurityValidator.validatePasswordSecurity(
        password = password,
        email = trimmedEmail,
        isSignUp = isSignUpMode,
      )
    if (passwordValidationError != null) {
      AstrynSoundEngine.play(context, AstrynSoundEffect.ERROR)
      errorMessage = passwordValidationError
      return
    }

    if (isSignUpMode && password != confirmPassword) {
      AstrynSoundEngine.play(context, AstrynSoundEffect.ERROR)
      errorMessage = "Passwords do not match."
      return
    }

    isSubmitting = true
    scope.launch {
      val authResult =
        if (isSignUpMode) {
          FirebaseRestAuthClient.signUpWithEmail(
            fullName = trimmedName,
            email = trimmedEmail,
            password = password,
            roleBadge = defaultRoleBadge,
          )
        } else {
          FirebaseRestAuthClient.signInWithEmail(
            email = trimmedEmail,
            password = password,
            fallbackName = trimmedName,
            fallbackRoleBadge = defaultRoleBadge,
          )
        }

      authResult
        .onSuccess { session ->
          failedAttemptsCount = 0
          isSubmitting = false
          AstrynSoundEngine.play(context, AstrynSoundEffect.SUCCESS)

          if (!session.isEmailVerified) {
            pendingVerificationUid = session.uid
            pendingVerificationIdToken = session.idToken
            pendingVerificationEmail = session.email
            pendingVerificationName = session.displayName
            resendCooldownSeconds = 30
            successBannerMessage = session.statusMessage
          } else {
            preferencesRepository.signInUser(
              uid = session.uid,
              idToken = session.idToken,
              refreshToken = session.refreshToken,
              name = session.displayName,
              email = session.email,
              roleBadge = session.roleBadge,
              provider = "Email",
            )
            onAuthComplete()
          }
        }
        .onFailure { throwable ->
          isSubmitting = false
          AstrynSoundEngine.play(context, AstrynSoundEffect.ERROR)
          val msg = throwable.message ?: "Authentication failed. Please try again."
          if (
            msg.contains("FIREBASE_KEY_MISSING", ignoreCase = true) ||
              msg.contains("API key not valid", ignoreCase = true)
          ) {
            errorMessage =
              msg.removePrefix("FIREBASE_KEY_MISSING:").trim()
          } else {
            failedAttemptsCount += 1
            if (failedAttemptsCount >= 5) {
              lockoutSecondsRemaining = 30
              failedAttemptsCount = 0
              errorMessage = "Too many failed attempts. Please wait 30 seconds."
            } else {
              errorMessage = msg
            }
          }
        }
    }
  }

  fun executeVerifiedGoogleSignIn(
    targetEmail: String,
    targetName: String,
    googleIdToken: String = "",
  ) {
    val normalizedEmail = FirebaseRestAuthClient.normalizeGoogleEmailInput(targetEmail)
    val check = EmailSecurityValidator.validateEmail(normalizedEmail)
    if (!check.isValidRealEmail) {
      AstrynSoundEngine.play(context, AstrynSoundEffect.ERROR)
      googleDialogError = check.statusMessage
      return
    }

    showGoogleAccountDialog = false
    googleDialogError = null
    errorMessage = null
    isSubmitting = true

    scope.launch {
      val resolvedName =
        UserPreferencesRepository.deriveCleanNameFromEmail(
          email = normalizedEmail,
          rawName = targetName,
        )
      val result =
        FirebaseRestAuthClient.signInWithGoogleAccount(
          googleEmail = normalizedEmail,
          googleDisplayName = resolvedName,
          roleBadge = defaultRoleBadge,
          googleIdToken = googleIdToken,
        )

      result
        .onSuccess { session ->
          AstrynSoundEngine.play(context, AstrynSoundEffect.SUCCESS)
          preferencesRepository.signInUser(
            uid = session.uid,
            idToken = session.idToken,
            refreshToken = session.refreshToken,
            name = session.displayName,
            email = session.email,
            roleBadge = session.roleBadge,
            provider = "Google",
          )
          isSubmitting = false
          onAuthComplete()
        }
        .onFailure { throwable ->
          isSubmitting = false
          AstrynSoundEngine.play(context, AstrynSoundEffect.ERROR)
          errorMessage = throwable.message ?: "Google Sign-In failed."
        }
    }
  }

  val googleAccountChooserLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
      if (activityResult.resultCode == Activity.RESULT_OK) {
        val chosenEmail =
          activityResult.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)?.trim().orEmpty()
        if (chosenEmail.isNotBlank()) {
          val rememberedName =
            preferences.knownAccounts
              .firstOrNull { it.email.equals(chosenEmail, ignoreCase = true) }
              ?.displayName
              .orEmpty()
          executeVerifiedGoogleSignIn(
            targetEmail = chosenEmail,
            targetName = rememberedName,
          )
        } else {
          showGoogleAccountDialog = true
        }
      }
    }

  fun launchNativeGoogleChooserOrFallback() {
    val hasGoogleAuthenticator =
      try {
        AccountManager.get(context).authenticatorTypes.any { it.type == "com.google" }
      } catch (_: Exception) {
        false
      }
    if (hasGoogleAuthenticator) {
      val chooserIntent =
        AccountManager.newChooseAccountIntent(
          null,
          null,
          arrayOf("com.google"),
          null,
          null,
          null,
          null,
        )
      val canLaunchSystemPicker =
        try {
          chooserIntent.resolveActivity(context.packageManager) != null
        } catch (_: Exception) {
          false
        }
      if (canLaunchSystemPicker) {
        try {
          googleAccountChooserLauncher.launch(chooserIntent)
          return
        } catch (_: Exception) {}
      }
    }
    showGoogleAccountDialog = true
  }

  fun triggerGoogleSignInOrChooser() {
    if (isSubmitting || lockoutSecondsRemaining > 0) return
    focusManager.clearFocus()
    errorMessage = null
    successBannerMessage = null

    val existingValidEmail =
      preferences.userEmail.trim().lowercase().takeIf {
        it.isNotBlank() && EmailSecurityValidator.validateEmail(it).isValidRealEmail
      }
    googleEmailDraft = existingValidEmail ?: email.trim()
    googleNameDraft =
      preferences.userName
        .takeIf { it.isNotBlank() && !it.equals("Guest User", ignoreCase = true) }
        ?: fullName.trim()
    googleDialogError = null
    launchNativeGoogleChooserOrFallback()
  }

  AmbientGlassBackground {
    Scaffold(
      containerColor = Color.Transparent,
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
      BoxWithConstraints(
        modifier =
          Modifier.fillMaxSize()
            .padding(innerPadding)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .testTag("auth_screen_root"),
        contentAlignment = Alignment.Center,
      ) {
        val isWideScreen = maxWidth >= 600.dp

        Column(
          modifier =
            Modifier.fillMaxWidth()
              .widthIn(max = if (isWideScreen) 460.dp else 420.dp)
              .verticalScroll(rememberScrollState())
              .padding(
                horizontal = if (isWideScreen) AppSpacing.xl else AppSpacing.lg,
                vertical = AppSpacing.md,
              ),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
          // Top Bar: Privacy Policy & Rules Icon Button
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier =
                Modifier.size(48.dp)
                  .clip(CircleShape)
                  .clickable {
                    AstrynSoundEngine.play(context, AstrynSoundEffect.TAP)
                    showPrivacyRulesDialog = true
                  }
                  .testTag("auth_privacy_policy_icon_button"),
              contentAlignment = Alignment.Center,
            ) {
              Box(
                modifier =
                  Modifier.size(38.dp)
                    .clip(CircleShape)
                    .background(glass.surfaceStandard)
                    .border(
                      width = AppBorder.hairline,
                      color = glass.accentCyan.copy(alpha = 0.55f),
                      shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Outlined.Policy,
                  contentDescription = "Privacy Policy & Rules",
                  tint = glass.accentCyan,
                  modifier = Modifier.size(19.dp),
                )
              }
            }
          }

          // Large Circular (Gol) App Logo Header
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Box(
              modifier =
                Modifier.size(88.dp)
                  .clip(CircleShape)
                  .background(
                    Brush.radialGradient(
                      colors =
                        listOf(
                          Color(0xFF0F1E3E),
                          Color(0xFF080E20),
                        )
                    )
                  )
                  .border(
                    width = 2.dp,
                    brush =
                      Brush.linearGradient(
                        colors = listOf(glass.accentCyan, ElectricBlue, RoyalViolet)
                      ),
                    shape = CircleShape,
                  )
                  .testTag("auth_app_logo_circle"),
              contentAlignment = Alignment.Center,
            ) {
              Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "Astryn AI App Logo",
                modifier = Modifier.size(78.dp),
              )
            }
            Text(
              text =
                when {
                  pendingVerificationEmail.isNotBlank() -> "Verify Your Email"
                  isSignUpMode -> "Create Account"
                  else -> "Welcome Back"
                },
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold,
              color = glass.textPrimary,
              textAlign = TextAlign.Center,
            )
            Text(
              text =
                when {
                  pendingVerificationEmail.isNotBlank() ->
                    "Confirm your email link to finish signing in"
                  isSignUpMode -> "Sign up with your email or Google account"
                  else -> "Sign in to your Astryn AI account"
                },
              style = MaterialTheme.typography.bodySmall,
              color = glass.textSecondary,
              textAlign = TextAlign.Center,
            )
          }

          // =================================================================
          // EMAIL VERIFICATION GATE (SHOWN AFTER SIGN-UP UNTIL VERIFIED)
          // =================================================================
          AnimatedVisibility(
            visible = pendingVerificationEmail.isNotBlank(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
          ) {
            GlassSurface(
              elevated = true,
              modifier = Modifier.fillMaxWidth().testTag("auth_email_verification_gate_card"),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  Box(
                    modifier =
                      Modifier.size(44.dp)
                        .clip(CircleShape)
                        .background(
                          Brush.radialGradient(
                            colors = listOf(Color(0xFF0F1E3E), Color(0xFF080E20))
                          )
                        )
                        .border(
                          width = 1.5.dp,
                          brush =
                            Brush.linearGradient(
                              colors = listOf(glass.accentCyan, ElectricBlue, RoyalViolet)
                            ),
                          shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                  ) {
                    Image(
                      painter = painterResource(id = R.drawable.ic_launcher_foreground),
                      contentDescription = "Astryn AI Logo",
                      modifier = Modifier.size(38.dp),
                    )
                  }
                  Column(horizontalAlignment = Alignment.Start) {
                    Text(
                      text = "Astryn AI",
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold,
                      color = glass.textPrimary,
                    )
                    Text(
                      text = "Official Account Verification",
                      style = MaterialTheme.typography.labelSmall,
                      color = glass.accentCyan,
                      fontWeight = FontWeight.SemiBold,
                    )
                  }
                }

                Row(
                  modifier =
                    Modifier.clip(AppRadius.shapePill)
                      .background(glass.surfaceStandard)
                      .border(
                        width = AppBorder.hairline,
                        color = glass.accentCyan.copy(alpha = 0.55f),
                        shape = AppRadius.shapePill,
                      )
                      .padding(horizontal = 14.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Email,
                    contentDescription = null,
                    tint = glass.accentCyan,
                    modifier = Modifier.size(15.dp),
                  )
                  Text(
                    text = pendingVerificationEmail,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = glass.accentCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }

                Text(
                  text =
                    "Astryn AI sent a verification link to your email inbox. Open your Email App (Inbox or Spam), tap the link from Astryn AI, and then tap Verify below.",
                  style = MaterialTheme.typography.bodySmall,
                  color = glass.textSecondary,
                  textAlign = TextAlign.Center,
                )

                if (errorMessage != null || successBannerMessage != null) {
                  val isErr = errorMessage != null
                  val bannerClr = if (isErr) glass.statusError else glass.statusSuccess
                  Row(
                    modifier =
                      Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(bannerClr.copy(alpha = 0.14f))
                        .border(
                          width = AppBorder.hairline,
                          color = bannerClr.copy(alpha = 0.5f),
                          shape = RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Icon(
                      imageVector =
                        if (isErr) Icons.Outlined.WarningAmber else Icons.Outlined.CheckCircle,
                      contentDescription = null,
                      tint = bannerClr,
                      modifier = Modifier.size(18.dp),
                    )
                    Text(
                      text = errorMessage ?: successBannerMessage.orEmpty(),
                      style = MaterialTheme.typography.bodySmall,
                      color = glass.textPrimary,
                      fontWeight = FontWeight.Medium,
                    )
                  }
                }

                // Primary Verify Button
                Box(
                  modifier =
                    Modifier.fillMaxWidth()
                      .heightIn(min = 52.dp)
                      .clip(RoundedCornerShape(14.dp))
                      .background(
                        brush =
                          Brush.horizontalGradient(
                            colors = listOf(ElectricBlue, Color(0xFF00C6FF), RoyalViolet)
                          )
                      )
                      .clickable(enabled = !isCheckingVerification) {
                        AstrynSoundEngine.play(context, AstrynSoundEffect.TAP)
                        errorMessage = null
                        successBannerMessage = null
                        isCheckingVerification = true
                        scope.launch {
                          val verifyRes =
                            FirebaseRestAuthClient.checkAccountEmailVerified(
                              idToken = pendingVerificationIdToken,
                              email = pendingVerificationEmail,
                              password = password,
                              uid = pendingVerificationUid,
                              name = pendingVerificationName,
                              roleBadge = defaultRoleBadge,
                            )
                          isCheckingVerification = false
                          verifyRes
                            .onSuccess { check ->
                              pendingVerificationIdToken = check.refreshedIdToken
                              if (check.isVerified) {
                                AstrynSoundEngine.play(context, AstrynSoundEffect.SUCCESS)
                                preferencesRepository.signInUser(
                                  uid = pendingVerificationUid,
                                  idToken = check.refreshedIdToken,
                                  refreshToken = check.refreshToken,
                                  name = pendingVerificationName,
                                  email = pendingVerificationEmail,
                                  roleBadge = defaultRoleBadge,
                                  provider = "Email",
                                )
                                pendingVerificationEmail = ""
                                onAuthComplete()
                              } else {
                                AstrynSoundEngine.play(context, AstrynSoundEffect.ERROR)
                                errorMessage = check.statusMessage
                              }
                            }
                            .onFailure { err ->
                              AstrynSoundEngine.play(context, AstrynSoundEffect.ERROR)
                              errorMessage =
                                err.message ?: "Unable to check verification status."
                            }
                        }
                      }
                      .padding(horizontal = AppSpacing.md, vertical = 14.dp)
                      .testTag("auth_verify_and_enter_button"),
                  contentAlignment = Alignment.Center,
                ) {
                  if (isCheckingVerification) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                      CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                      )
                      Text(
                        text = "Checking Verification…",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                      )
                    }
                  } else {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                      Icon(
                        imageVector = Icons.Outlined.VerifiedUser,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                      )
                      Text(
                        text = "Verify & Enter App",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                      )
                    }
                  }
                }

                // Resend Verification Link & Change Email Row
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Row(
                    modifier =
                      Modifier.weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(glass.surfaceStandard)
                        .border(AppBorder.hairline, glass.borderSubtle, RoundedCornerShape(12.dp))
                        .clickable(
                          enabled = !isResendingVerification && resendCooldownSeconds == 0
                        ) {
                          AstrynSoundEngine.play(context, AstrynSoundEffect.TAP)
                          errorMessage = null
                          isResendingVerification = true
                          scope.launch {
                            val resendRes =
                              FirebaseRestAuthClient.sendVerificationEmailToUser(
                                idToken = pendingVerificationIdToken,
                                email = pendingVerificationEmail,
                                uid = pendingVerificationUid,
                              )
                            isResendingVerification = false
                            resendRes
                              .onSuccess { msg ->
                                AstrynSoundEngine.play(context, AstrynSoundEffect.SUCCESS)
                                resendCooldownSeconds = 30
                                successBannerMessage = msg
                              }
                              .onFailure { err ->
                                AstrynSoundEngine.play(context, AstrynSoundEffect.ERROR)
                                errorMessage =
                                  err.message ?: "Could not resend verification link."
                              }
                          }
                        }
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                        .testTag("auth_resend_verification_button"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Icon(
                      imageVector = Icons.Outlined.Refresh,
                      contentDescription = null,
                      tint =
                        if (resendCooldownSeconds > 0) glass.textMuted else glass.accentCyan,
                      modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text =
                        if (resendCooldownSeconds > 0) "Resend (${resendCooldownSeconds}s)"
                        else "Resend Link",
                      style = MaterialTheme.typography.labelMedium,
                      fontWeight = FontWeight.SemiBold,
                      color =
                        if (resendCooldownSeconds > 0) glass.textMuted else glass.textPrimary,
                    )
                  }

                  Row(
                    modifier =
                      Modifier.weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(glass.surfaceStandard)
                        .border(AppBorder.hairline, glass.borderSubtle, RoundedCornerShape(12.dp))
                        .clickable {
                          AstrynSoundEngine.play(context, AstrynSoundEffect.TAP)
                          pendingVerificationEmail = ""
                          errorMessage = null
                          successBannerMessage = null
                        }
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                        .testTag("auth_verify_change_email_button"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Icon(
                      imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                      contentDescription = null,
                      tint = glass.textSecondary,
                      modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = "Change Email",
                      style = MaterialTheme.typography.labelMedium,
                      fontWeight = FontWeight.SemiBold,
                      color = glass.textSecondary,
                    )
                  }
                }

                Row(
                  modifier =
                    Modifier.fillMaxWidth()
                      .heightIn(min = 44.dp)
                      .clip(RoundedCornerShape(12.dp))
                      .background(glass.surfaceStandard)
                      .border(
                        width = AppBorder.hairline,
                        color = glass.accentCyan.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(12.dp),
                      )
                      .clickable {
                        AstrynSoundEngine.play(context, AstrynSoundEffect.TAP)
                        try {
                          val emailIntent =
                            Intent(Intent.ACTION_MAIN).apply {
                              addCategory(Intent.CATEGORY_APP_EMAIL)
                              addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                          context.startActivity(emailIntent)
                        } catch (_: Exception) {
                          successBannerMessage =
                            "Please open your Gmail or Mail app to tap the Astryn AI link."
                        }
                      }
                      .padding(horizontal = 12.dp, vertical = 10.dp)
                      .testTag("auth_open_email_app_button"),
                  horizontalArrangement = Arrangement.Center,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(
                    imageVector = Icons.Outlined.MarkEmailRead,
                    contentDescription = null,
                    tint = glass.accentCyan,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "Open Email App",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = glass.accentCyan,
                  )
                }
              }
            }
          }

          if (pendingVerificationEmail.isBlank()) {
            // =================================================================
            // CLEAN 2-TAB MODE SWITCHER (SIGN IN | SIGN UP)
            // =================================================================
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(14.dp))
                  .background(glass.surfaceStandard)
                  .border(AppBorder.hairline, glass.borderSpecular, RoundedCornerShape(14.dp))
                  .padding(4.dp)
                  .testTag("auth_mode_switcher"),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              AuthModeTabPill(
                title = "Sign In",
                isSelected = !isSignUpMode,
                onClick = {
                  AstrynSoundEngine.play(context, AstrynSoundEffect.TAP)
                  isSignUpMode = false
                  errorMessage = null
                  successBannerMessage = null
                },
                modifier = Modifier.weight(1f).testTag("auth_tab_sign_in"),
              )
              AuthModeTabPill(
                title = "Sign Up",
                isSelected = isSignUpMode,
                onClick = {
                  AstrynSoundEngine.play(context, AstrynSoundEffect.TAP)
                  isSignUpMode = true
                  errorMessage = null
                  successBannerMessage = null
                },
                modifier = Modifier.weight(1f).testTag("auth_tab_sign_up"),
              )
            }

            // =================================================================
            // PROFESSIONAL AUTHENTICATION FORM CARD
            // =================================================================
            GlassSurface(
              elevated = true,
              modifier = Modifier.fillMaxWidth().testTag("auth_form_card"),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
              ) {
                // Error or Success Feedback Banner
                AnimatedVisibility(
                  visible = errorMessage != null || successBannerMessage != null,
                  enter = fadeIn() + expandVertically(),
                  exit = fadeOut() + shrinkVertically(),
                ) {
                  val isError = errorMessage != null
                  val bannerColor = if (isError) glass.statusError else glass.statusSuccess
                  val bannerText = errorMessage ?: successBannerMessage.orEmpty()

                  Row(
                    modifier =
                      Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(bannerColor.copy(alpha = 0.14f))
                        .border(
                          width = AppBorder.hairline,
                          color = bannerColor.copy(alpha = 0.55f),
                          shape = RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag(if (isError) "auth_error_banner" else "auth_success_banner"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Icon(
                      imageVector =
                        if (isError) Icons.Outlined.WarningAmber else Icons.Outlined.CheckCircle,
                      contentDescription = null,
                      tint = bannerColor,
                      modifier = Modifier.size(18.dp),
                    )
                    Text(
                      text = bannerText,
                      style = MaterialTheme.typography.bodySmall,
                      fontWeight = FontWeight.Medium,
                      color = glass.textPrimary,
                    )
                  }
                }

                // Sign Up Only: Full Name
                AnimatedVisibility(
                  visible = isSignUpMode,
                  enter = fadeIn() + expandVertically(),
                  exit = fadeOut() + shrinkVertically(),
                ) {
                  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                      text = "Full Name",
                      style = MaterialTheme.typography.labelMedium,
                      fontWeight = FontWeight.SemiBold,
                      color = glass.textSecondary,
                    )
                    GlassTextField(
                      value = fullName,
                      onValueChange = {
                        fullName = it
                        errorMessage = null
                      },
                      placeholder = "Enter your full name",
                      singleLine = true,
                      keyboardOptions =
                        KeyboardOptions(
                          keyboardType = KeyboardType.Text,
                          imeAction = ImeAction.Next,
                        ),
                      leadingContent = {
                        Icon(
                          imageVector = Icons.Outlined.Person,
                          contentDescription = null,
                          tint = glass.accentCyan,
                          modifier = Modifier.size(18.dp),
                        )
                      },
                      modifier = Modifier.fillMaxWidth().testTag("auth_name_input"),
                    )
                  }
                }

                // Email Field
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  Text(
                    text = "Email Address",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = glass.textSecondary,
                  )

                  GlassTextField(
                    value = email,
                    onValueChange = {
                      email = it
                      errorMessage = null
                    },
                    placeholder = "name@gmail.com",
                    singleLine = true,
                    keyboardOptions =
                      KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                      ),
                    leadingContent = {
                      Icon(
                        imageVector =
                          when {
                            emailSecurityResult.isTempOrDisposableBlocked -> Icons.Outlined.Block
                            emailSecurityResult.isValidRealEmail -> Icons.Outlined.VerifiedUser
                            else -> Icons.Outlined.Email
                          },
                        contentDescription = null,
                        tint =
                          when {
                            emailSecurityResult.isTempOrDisposableBlocked ||
                              emailSecurityResult.isTypoDomainBlocked -> glass.statusError
                            emailSecurityResult.isValidRealEmail -> glass.accentCyan
                            else -> glass.textMuted
                          },
                        modifier = Modifier.size(18.dp),
                      )
                    },
                    modifier = Modifier.fillMaxWidth().testTag("auth_email_input"),
                  )

                  // Show alert only if email is blocked (disposable/temp mail or typo domain)
                  AnimatedVisibility(
                    visible =
                      email.isNotBlank() &&
                        (emailSecurityResult.isTempOrDisposableBlocked ||
                          emailSecurityResult.isTypoDomainBlocked),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                  ) {
                    Row(
                      modifier =
                        Modifier.fillMaxWidth()
                          .clip(RoundedCornerShape(10.dp))
                          .background(glass.statusError.copy(alpha = 0.12f))
                          .border(
                            width = AppBorder.hairline,
                            color = glass.statusError.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(10.dp),
                          )
                          .padding(horizontal = 10.dp, vertical = 7.dp)
                          .testTag("auth_temp_mail_blocked_banner"),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                      Icon(
                        imageVector = Icons.Outlined.Block,
                        contentDescription = null,
                        tint = glass.statusError,
                        modifier = Modifier.size(15.dp),
                      )
                      Text(
                        text = emailSecurityResult.statusMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = glass.statusError,
                        fontWeight = FontWeight.SemiBold,
                      )
                    }
                  }
                }

                // Password Field
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Text(
                      text = "Password",
                      style = MaterialTheme.typography.labelMedium,
                      fontWeight = FontWeight.SemiBold,
                      color = glass.textSecondary,
                    )
                    if (!isSignUpMode) {
                      Text(
                        text = "Forgot Password?",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = glass.accentCyan,
                        modifier =
                          Modifier.clip(RoundedCornerShape(6.dp))
                            .clickable {
                              AstrynSoundEngine.play(context, AstrynSoundEffect.TAP)
                              resetEmailInput = email
                              resetConfirmationMessage = null
                              showForgotPasswordDialog = true
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                            .testTag("auth_forgot_password_button"),
                      )
                    }
                  }

                  GlassTextField(
                    value = password,
                    onValueChange = {
                      password = it
                      errorMessage = null
                    },
                    placeholder =
                      if (isSignUpMode) "8+ characters with letters (e.g. rahul123)"
                      else "Enter your password",
                    singleLine = true,
                    visualTransformation =
                      if (passwordVisible) VisualTransformation.None
                      else PasswordVisualTransformation(),
                    keyboardOptions =
                      KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = if (isSignUpMode) ImeAction.Next else ImeAction.Done,
                      ),
                    keyboardActions =
                      KeyboardActions(
                        onDone = {
                          if (!isSignUpMode) handlePrimarySubmit()
                        },
                      ),
                    leadingContent = {
                      Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = if (password.length >= 8) glass.accentCyan else glass.textMuted,
                        modifier = Modifier.size(18.dp),
                      )
                    },
                    trailingContent = {
                      Box(
                        modifier =
                          Modifier.size(36.dp)
                            .clip(CircleShape)
                            .clickable {
                              AstrynSoundEngine.play(context, AstrynSoundEffect.TAP)
                              passwordVisible = !passwordVisible
                            }
                            .testTag("auth_toggle_password_visibility"),
                        contentAlignment = Alignment.Center,
                      ) {
                        Icon(
                          imageVector =
                            if (passwordVisible) Icons.Outlined.VisibilityOff
                            else Icons.Outlined.Visibility,
                          contentDescription =
                            if (passwordVisible) "Hide password" else "Show password",
                          tint = glass.textSecondary,
                          modifier = Modifier.size(18.dp),
                        )
                      }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("auth_password_input"),
                  )

                  AnimatedVisibility(
                    visible = isSignUpMode && password.isNotEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                  ) {
                    PasswordStrengthIndicator(
                      score = passwordStrengthScore,
                      password = password,
                    )
                  }
                }

                // Confirm Password (Sign Up Mode Only)
                AnimatedVisibility(
                  visible = isSignUpMode,
                  enter = fadeIn() + expandVertically(),
                  exit = fadeOut() + shrinkVertically(),
                ) {
                  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Text(
                        text = "Confirm Password",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = glass.textSecondary,
                      )
                      if (confirmPassword.isNotEmpty()) {
                        val matches = password == confirmPassword
                        Text(
                          text = if (matches) "Matches ✓" else "Does not match",
                          style = MaterialTheme.typography.labelSmall,
                          color = if (matches) glass.statusSuccess else glass.statusError,
                        )
                      }
                    }

                    GlassTextField(
                      value = confirmPassword,
                      onValueChange = {
                        confirmPassword = it
                        errorMessage = null
                      },
                      placeholder = "Re-enter your password",
                      singleLine = true,
                      visualTransformation =
                        if (confirmPasswordVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                      keyboardOptions =
                        KeyboardOptions(
                          keyboardType = KeyboardType.Password,
                          imeAction = ImeAction.Done,
                        ),
                      keyboardActions =
                        KeyboardActions(
                          onDone = { handlePrimarySubmit() },
                        ),
                      leadingContent = {
                        Icon(
                          imageVector = Icons.Outlined.Key,
                          contentDescription = null,
                          tint =
                            if (confirmPassword.isNotEmpty() && confirmPassword == password) {
                              glass.statusSuccess
                            } else {
                              glass.textMuted
                            },
                          modifier = Modifier.size(18.dp),
                        )
                      },
                      trailingContent = {
                        Box(
                          modifier =
                            Modifier.size(36.dp)
                              .clip(CircleShape)
                              .clickable {
                                AstrynSoundEngine.play(context, AstrynSoundEffect.TAP)
                                confirmPasswordVisible = !confirmPasswordVisible
                              },
                          contentAlignment = Alignment.Center,
                        ) {
                          Icon(
                            imageVector =
                              if (confirmPasswordVisible) Icons.Outlined.VisibilityOff
                              else Icons.Outlined.Visibility,
                            contentDescription =
                              if (confirmPasswordVisible) "Hide confirm password"
                              else "Show confirm password",
                            tint = glass.textSecondary,
                            modifier = Modifier.size(18.dp),
                          )
                        }
                      },
                      modifier = Modifier.fillMaxWidth().testTag("auth_confirm_password_input"),
                    )
                  }
                }

                // Primary Submit Button
                val canClickSubmit = !isSubmitting && lockoutSecondsRemaining == 0
                Box(
                  modifier =
                    Modifier.fillMaxWidth()
                      .heightIn(min = 52.dp)
                      .clip(RoundedCornerShape(14.dp))
                      .background(
                        brush =
                          Brush.horizontalGradient(
                            colors =
                              if (canClickSubmit) {
                                listOf(ElectricBlue, Color(0xFF00C6FF), RoyalViolet)
                              } else {
                                listOf(glass.borderSubtle, glass.borderSubtle)
                              },
                          ),
                      )
                      .border(
                        width = AppBorder.hairline,
                        color = Color.White.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(14.dp),
                      )
                      .clickable(enabled = canClickSubmit) {
                        AstrynSoundEngine.play(context, AstrynSoundEffect.TAP)
                        handlePrimarySubmit()
                      }
                      .padding(horizontal = AppSpacing.lg, vertical = 14.dp)
                      .testTag("auth_submit_button"),
                  contentAlignment = Alignment.Center,
                ) {
                  when {
                    lockoutSecondsRemaining > 0 -> {
                      Text(
                        text = "Please wait (${lockoutSecondsRemaining}s)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = glass.statusError,
                      )
                    }
                    isSubmitting -> {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                      ) {
                        CircularProgressIndicator(
                          modifier = Modifier.size(18.dp),
                          color = Color.White,
                          strokeWidth = 2.dp,
                        )
                        Text(
                          text =
                            if (isSignUpMode) "Creating Account…"
                            else "Signing In…",
                          style = MaterialTheme.typography.titleSmall,
                          fontWeight = FontWeight.Bold,
                          color = Color.White,
                        )
                      }
                    }
                    else -> {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                      ) {
                        Icon(
                          imageVector = Icons.Outlined.AutoAwesome,
                          contentDescription = null,
                          tint = Color.White,
                          modifier = Modifier.size(18.dp),
                        )
                        Text(
                          text =
                            if (isSignUpMode) "Create Account"
                            else "Sign In",
                          style = MaterialTheme.typography.titleSmall,
                          fontWeight = FontWeight.Bold,
                          color = Color.White,
                        )
                      }
                    }
                  }
                }

                // Divider
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                ) {
                  GlassDivider(modifier = Modifier.weight(1f))
                  Text(
                    text = "OR",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = glass.textMuted,
                  )
                  GlassDivider(modifier = Modifier.weight(1f))
                }

                // Single Clean Continue with Google Button
                Row(
                  modifier =
                    Modifier.fillMaxWidth()
                      .heightIn(min = 50.dp)
                      .clip(RoundedCornerShape(14.dp))
                      .background(glass.surfaceElevated)
                      .border(
                        width = AppBorder.hairline,
                        color = glass.borderAccent,
                        shape = RoundedCornerShape(14.dp),
                      )
                      .clickable(enabled = !isSubmitting && lockoutSecondsRemaining == 0) {
                        AstrynSoundEngine.play(context, AstrynSoundEffect.TAP)
                        triggerGoogleSignInOrChooser()
                      }
                      .padding(horizontal = AppSpacing.md, vertical = 12.dp)
                      .testTag("auth_google_button"),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.Center,
                ) {
                  GoogleBrandEmblem(modifier = Modifier.size(20.dp))
                  Spacer(modifier = Modifier.width(10.dp))
                  Text(
                    text = "Continue with Google",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = glass.textPrimary,
                  )
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(AppSpacing.sm))
        }
      }
    }
  }

  // =========================================================================
  // GOOGLE ACCOUNT CHOOSER DIALOG
  // =========================================================================
  if (showGoogleAccountDialog) {
    val quickAccounts =
      remember(preferences.knownAccounts, preferences.userEmail, preferences.userName) {
        buildList {
          val seen = HashSet<String>()
          for (acct in preferences.knownAccounts) {
            val clean = acct.email.trim().lowercase()
            if (clean.isNotBlank() && seen.add(clean)) {
              add(
                clean to
                  acct.displayName.ifBlank {
                    UserPreferencesRepository.deriveCleanNameFromEmail(clean)
                  }
              )
            }
          }
          val activeClean = preferences.userEmail.trim().lowercase()
          if (
            activeClean.isNotBlank() &&
              EmailSecurityValidator.validateEmail(activeClean).isValidRealEmail &&
              seen.add(activeClean)
          ) {
            add(
              activeClean to
                preferences.userName.ifBlank {
                  UserPreferencesRepository.deriveCleanNameFromEmail(activeClean)
                }
            )
          }
        }
      }

    AlertDialog(
      onDismissRequest = { showGoogleAccountDialog = false },
      containerColor = glass.surfaceElevated,
      titleContentColor = glass.textPrimary,
      textContentColor = glass.textSecondary,
      title = {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          GoogleBrandEmblem(modifier = Modifier.size(22.dp))
          Text(
            text = "Continue with Google",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
        }
      },
      text = {
        Column(
          modifier =
            Modifier.fillMaxWidth()
              .verticalScroll(rememberScrollState())
              .testTag("auth_google_account_dialog"),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = "Choose a Google account or sign in with another Gmail address:",
            style = MaterialTheme.typography.bodySmall,
            color = glass.textSecondary,
          )
          quickAccounts.forEachIndexed { idx, (acctEmail, acctName) ->
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(12.dp))
                  .background(glass.surfaceStandard)
                  .border(
                    width = AppBorder.focused,
                    color = glass.accentCyan.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp),
                  )
                  .clickable {
                    AstrynSoundEngine.play(context, AstrynSoundEffect.TAP)
                    executeVerifiedGoogleSignIn(
                      targetEmail = acctEmail,
                      targetName = googleNameDraft.ifBlank { acctName },
                    )
                  }
                  .padding(horizontal = 12.dp, vertical = 10.dp)
                  .testTag("auth_google_quick_account_$idx"),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Box(
                modifier =
                  Modifier.size(34.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(ElectricBlue, RoyalViolet))),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = acctEmail.firstOrNull()?.uppercase() ?: "G",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = Color.White,
                )
              }
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = acctName,
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold,
                  color = glass.textPrimary,
                )
                Text(
                  text = acctEmail,
                  style = MaterialTheme.typography.bodySmall,
                  color = glass.accentCyan,
                )
              }
            }
          }

          GlassDivider()

          GlassTextField(
            value = googleNameDraft,
            onValueChange = {
              googleNameDraft = it
              googleDialogError = null
            },
            placeholder = "Your Name",
            singleLine = true,
            leadingContent = {
              Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                tint = glass.accentCyan,
                modifier = Modifier.size(18.dp),
              )
            },
            modifier = Modifier.fillMaxWidth().testTag("auth_google_name_input"),
          )
          GlassTextField(
            value = googleEmailDraft,
            onValueChange = {
              googleEmailDraft = it
              googleDialogError = null
            },
            placeholder = "your.email@gmail.com",
            singleLine = true,
            keyboardOptions =
              KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
              ),
            keyboardActions =
              KeyboardActions(
                onDone = {
                  if (googleEmailDraft.isNotBlank()) {
                    executeVerifiedGoogleSignIn(
                      targetEmail = googleEmailDraft,
                      targetName = googleNameDraft,
                    )
                  }
                }
              ),
            leadingContent = {
              Icon(
                imageVector = Icons.Outlined.Email,
                contentDescription = null,
                tint = glass.accentCyan,
                modifier = Modifier.size(18.dp),
              )
            },
            modifier = Modifier.fillMaxWidth().testTag("auth_google_email_input"),
          )

          googleDialogError?.let { err ->
            Text(
              text = err,
              style = MaterialTheme.typography.labelSmall,
              color = glass.statusError,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      },
      confirmButton = {
        GlassButton(
          text = "Continue",
          variant = GlassButtonVariant.PRIMARY,
          onClick = {
            val targetEmail =
              googleEmailDraft.trim().ifBlank {
                quickAccounts.firstOrNull()?.first.orEmpty()
              }
            val targetName =
              googleNameDraft.trim().ifBlank {
                quickAccounts
                  .firstOrNull { it.first.equals(targetEmail, ignoreCase = true) }
                  ?.second
                  .orEmpty()
              }
            executeVerifiedGoogleSignIn(
              targetEmail = targetEmail,
              targetName = targetName,
            )
          },
          modifier = Modifier.testTag("auth_google_confirm_button"),
        )
      },
      dismissButton = {
        GlassButton(
          text = "Cancel",
          variant = GlassButtonVariant.SECONDARY,
          onClick = { showGoogleAccountDialog = false },
        )
      },
    )
  }

  // =========================================================================
  // FORGOT PASSWORD DIALOG
  // =========================================================================
  if (showForgotPasswordDialog) {
    AlertDialog(
      onDismissRequest = { showForgotPasswordDialog = false },
      containerColor = glass.surfaceElevated,
      titleContentColor = glass.textPrimary,
      textContentColor = glass.textSecondary,
      title = {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.Key,
            contentDescription = null,
            tint = glass.accentCyan,
          )
          Text(
            text = "Reset Password",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
        }
      },
      text = {
        Column(
          modifier = Modifier.fillMaxWidth().testTag("auth_forgot_password_dialog"),
          verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
          Text(
            text = "Enter your registered email address to receive a password reset link.",
            style = MaterialTheme.typography.bodySmall,
            color = glass.textSecondary,
          )
          GlassTextField(
            value = resetEmailInput,
            onValueChange = {
              resetEmailInput = it
              resetConfirmationMessage = null
            },
            placeholder = "Enter your email address",
            singleLine = true,
            leadingContent = {
              Icon(
                imageVector = Icons.Outlined.Email,
                contentDescription = null,
                tint = glass.accentCyan,
                modifier = Modifier.size(18.dp),
              )
            },
            modifier = Modifier.fillMaxWidth().testTag("auth_reset_email_input"),
          )

          resetConfirmationMessage?.let { msg ->
            Text(
              text = msg,
              style = MaterialTheme.typography.labelMedium,
              color = glass.statusSuccess,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      },
      confirmButton = {
        GlassButton(
          text = "Send Reset Link",
          variant = GlassButtonVariant.PRIMARY,
          onClick = {
            AstrynSoundEngine.play(context, AstrynSoundEffect.TAP)
            val target = resetEmailInput.trim()
            scope.launch {
              FirebaseRestAuthClient.sendPasswordResetEmail(target)
                .onSuccess { msg ->
                  AstrynSoundEngine.play(context, AstrynSoundEffect.SUCCESS)
                  resetConfirmationMessage = msg
                }
                .onFailure { err ->
                  AstrynSoundEngine.play(context, AstrynSoundEffect.ERROR)
                  resetConfirmationMessage =
                    err.message ?: "Please enter a valid email address."
                }
            }
          },
          modifier = Modifier.testTag("auth_send_reset_link_button"),
        )
      },
      dismissButton = {
        GlassButton(
          text = "Close",
          variant = GlassButtonVariant.SECONDARY,
          onClick = { showForgotPasswordDialog = false },
        )
      },
    )
  }

  // =========================================================================
  // PRIVACY POLICY & ACCOUNT RULES DIALOG (OPENED VIA TOP-CORNER ICON)
  // =========================================================================
  if (showPrivacyRulesDialog) {
    AlertDialog(
      onDismissRequest = { showPrivacyRulesDialog = false },
      containerColor = glass.surfaceElevated,
      titleContentColor = glass.textPrimary,
      textContentColor = glass.textSecondary,
      title = {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.Security,
            contentDescription = null,
            tint = glass.accentCyan,
          )
          Text(
            text = "Privacy Policy & Account Rules",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
        }
      },
      text = {
        Column(
          modifier = Modifier.fillMaxWidth().testTag("auth_privacy_rules_dialog"),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          PrivacyRuleItem(
            title = "1. Isolated Per-User Chat History",
            description =
              "Har user ki chat history sirf uske apne account (UID) mein rehti hai. Dusra user aapki chats kabhi nahi dekh sakta.",
          )
          PrivacyRuleItem(
            title = "2. Real Email Verification",
            description =
              "Account create karne par aapki email ID par verification link bheja jata hai. Link verify karne ke baad hi app mein entry milti hai.",
          )
          PrivacyRuleItem(
            title = "3. Simple 8-Character Password Rule",
            description =
              "Password kam se kam 8 characters (8 अंकों) ka hona chahiye jismein alphabets (A–Z / a–z) शामिल हों.",
          )
          PrivacyRuleItem(
            title = "4. Encrypted Cloud Vault",
            description =
              "Aapka account aur conversations Astryn AI Cloud authentication aur Firestore mein सुरक्षित rehte hain.",
          )
        }
      },
      confirmButton = {
        GlassButton(
          text = "Got It",
          variant = GlassButtonVariant.PRIMARY,
          onClick = { showPrivacyRulesDialog = false },
          modifier = Modifier.testTag("auth_privacy_rules_close_button"),
        )
      },
    )
  }
}

@Composable
private fun PrivacyRuleItem(
  title: String,
  description: String,
) {
  val glass = AppThemeTokens.glass
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(
      text = title,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.Bold,
      color = glass.accentCyan,
    )
    Text(
      text = description,
      style = MaterialTheme.typography.bodySmall,
      color = glass.textSecondary,
    )
  }
}

@Composable
private fun AuthModeTabPill(
  title: String,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val glass = AppThemeTokens.glass
  val bgColor by
    animateColorAsState(
      targetValue =
        if (isSelected) glass.accentPrimary.copy(alpha = 0.25f) else Color.Transparent,
      animationSpec = tween(200),
      label = "auth_tab_bg",
    )
  val borderColor by
    animateColorAsState(
      targetValue = if (isSelected) glass.accentCyan else Color.Transparent,
      animationSpec = tween(200),
      label = "auth_tab_border",
    )

  Box(
    modifier =
      modifier
        .heightIn(min = 44.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(bgColor)
        .border(
          width = if (isSelected) AppBorder.focused else AppBorder.hairline,
          color = borderColor,
          shape = RoundedCornerShape(10.dp),
        )
        .semantics {
          role = Role.Tab
          selected = isSelected
        }
        .clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 10.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
      color = if (isSelected) glass.textPrimary else glass.textSecondary,
    )
  }
}

@Composable
private fun PasswordStrengthIndicator(
  score: Int,
  password: String,
) {
  val glass = AppThemeTokens.glass
  val hasLetters = password.any { it.isLetter() }
  val (label, statusColor) =
    when {
      password.length < 8 -> "Minimum 8 characters required" to glass.statusError
      !hasLetters -> "Add alphabet letters (A–Z / a–z)" to Color(0xFFF59E0B)
      score == 3 -> "Valid Password (8+ chars & letters) ✓" to Color(0xFF38BDF8)
      else -> "Strong Password ✓" to glass.statusSuccess
    }

  Column(
    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      for (index in 1..4) {
        val active = index <= score.coerceAtLeast(1)
        Box(
          modifier =
            Modifier.weight(1f)
              .height(4.dp)
              .clip(AppRadius.shapePill)
              .background(if (active) statusColor else glass.borderSubtle),
        )
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = statusColor,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        text = "${password.length}/8+ chars",
        style = MaterialTheme.typography.labelSmall,
        color = glass.textMuted,
      )
    }
  }
}

@Composable
private fun GoogleBrandEmblem(modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val stroke = size.minDimension * 0.22f
    val arcStyle = Stroke(width = stroke, cap = StrokeCap.Butt)
    drawArc(
      color = Color(0xFFEA4335),
      startAngle = -145f,
      sweepAngle = 105f,
      useCenter = false,
      style = arcStyle,
    )
    drawArc(
      color = Color(0xFFFBBC05),
      startAngle = 140f,
      sweepAngle = 75f,
      useCenter = false,
      style = arcStyle,
    )
    drawArc(
      color = Color(0xFF34A853),
      startAngle = 45f,
      sweepAngle = 95f,
      useCenter = false,
      style = arcStyle,
    )
    drawArc(
      color = Color(0xFF4285F4),
      startAngle = -15f,
      sweepAngle = 60f,
      useCenter = false,
      style = arcStyle,
    )
    drawLine(
      color = Color(0xFF4285F4),
      startPt = Offset(size.width * 0.52f, size.height * 0.5f),
      end = Offset(size.width * 0.92f, size.height * 0.5f),
      strokeWidth = stroke * 0.9f,
    )
  }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLine(
  color: Color,
  startPt: Offset,
  end: Offset,
  strokeWidth: Float,
) {
  drawLine(
    color = color,
    start = startPt,
    end = end,
    strokeWidth = strokeWidth,
  )
}
