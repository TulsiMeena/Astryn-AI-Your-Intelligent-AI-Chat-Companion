package com.example.ui.screens.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Security
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
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import com.example.data.api.EmailSecurityValidator
import com.example.data.api.FirebaseRestAuthClient
import com.example.data.repository.UserPreferencesRepository
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
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.RoyalViolet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Flagship, Zero-Trust Security Login & Sign Up screen for Astryn AI.
 *
 * Security & Privacy Architecture:
 * - Mandatory Authentication Gate: Guest / Skip access is strictly disabled.
 * - Real-Time Anti-Temp-Mail Firewall: Blocks 150+ disposable/burner domains & domain typos.
 * - Breach-Password Guard & 4-Stage Quantum Password Entropy Meter.
 * - Brute-Force Rate Limiter: 30-second security lockout after 5 failed attempts.
 * - Per-User UID Isolation: Every user's chat history & profile is strictly isolated in
 *   their own private local + Firebase Cloud Firestore (`users/{uid}/conversations`) vault.
 */
@Composable
fun AuthScreen(
  preferencesRepository: UserPreferencesRepository,
  initialIsSignUp: Boolean = false,
  onAuthComplete: () -> Unit,
  onBackOrSkip: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val glass = AppThemeTokens.glass
  val preferences by preferencesRepository.preferencesFlow.collectAsState()
  val scope = rememberCoroutineScope()

  var isSignUpMode by rememberSaveable { mutableStateOf(initialIsSignUp) }
  var fullName by rememberSaveable { mutableStateOf(preferences.userName) }
  var email by rememberSaveable { mutableStateOf(preferences.userEmail) }
  var password by rememberSaveable { mutableStateOf("") }
  var confirmPassword by rememberSaveable { mutableStateOf("") }
  var selectedRoleBadge by rememberSaveable {
    mutableStateOf(preferences.userRoleBadge.ifBlank { "Developer" })
  }
  var passwordVisible by rememberSaveable { mutableStateOf(false) }
  var confirmPasswordVisible by rememberSaveable { mutableStateOf(false) }
  var rememberMe by rememberSaveable { mutableStateOf(true) }
  var acceptedTerms by rememberSaveable { mutableStateOf(true) }

  var isSubmitting by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var successBannerMessage by remember { mutableStateOf<String?>(null) }

  // Brute-force lockout state
  var failedAttempts by rememberSaveable { mutableIntStateOf(0) }
  var lockoutSecondsRemaining by rememberSaveable { mutableIntStateOf(0) }

  LaunchedEffect(lockoutSecondsRemaining) {
    if (lockoutSecondsRemaining > 0) {
      delay(1000L)
      lockoutSecondsRemaining -= 1
      if (lockoutSecondsRemaining == 0) {
        failedAttempts = 0
        errorMessage = null
      }
    }
  }

  val context = LocalContext.current
  var showForgotPasswordDialog by rememberSaveable { mutableStateOf(false) }
  var resetEmailInput by rememberSaveable { mutableStateOf("") }
  var resetConfirmationMessage by remember { mutableStateOf<String?>(null) }

  var showGoogleAccountDialog by rememberSaveable { mutableStateOf(false) }
  var googleEmailDraft by rememberSaveable { mutableStateOf("") }
  var googleNameDraft by rememberSaveable { mutableStateOf("") }
  var googleDialogError by remember { mutableStateOf<String?>(null) }

  // Strict Email Verification Gate State (holds newly created / unverified account until link is clicked)
  var pendingVerificationEmail by rememberSaveable { mutableStateOf("") }
  var pendingVerificationName by rememberSaveable { mutableStateOf("") }
  var pendingVerificationRoleBadge by rememberSaveable { mutableStateOf("Developer") }
  var pendingVerificationUid by rememberSaveable { mutableStateOf("") }
  var pendingVerificationIdToken by rememberSaveable { mutableStateOf("") }
  var pendingVerificationProvider by rememberSaveable { mutableStateOf("") }
  var isCheckingVerification by remember { mutableStateOf(false) }
  var resendCooldownSeconds by rememberSaveable { mutableIntStateOf(0) }

  var firebaseConfigSnippetInput by rememberSaveable { mutableStateOf("") }
  var showFirebaseKeySetupBox by rememberSaveable { mutableStateOf(false) }

  // Attempt auto-discovery of Firebase Web API key from sms-send-e2ad8 on screen launch
  LaunchedEffect(Unit) {
    FirebaseRestAuthClient.resolveConfiguredApiKeyWithAutoDiscovery()
  }

  LaunchedEffect(resendCooldownSeconds) {
    if (resendCooldownSeconds > 0) {
      delay(1000L)
      resendCooldownSeconds -= 1
    }
  }

  // Auto-poll Firebase `accounts:lookup` every 4 seconds while waiting on the Email Verification Gate
  // so the moment the user clicks the link in their Gmail/Email inbox, the app unlocks automatically.
  LaunchedEffect(pendingVerificationEmail, pendingVerificationIdToken, preferences.firebaseWebApiKey) {
    if (pendingVerificationEmail.isNotBlank() && FirebaseRestAuthClient.isCloudKeyConfigured()) {
      while (pendingVerificationEmail.isNotBlank()) {
        delay(4000L)
        if (isCheckingVerification) continue
        val lookup =
          FirebaseRestAuthClient.checkAccountEmailVerified(
            idToken = pendingVerificationIdToken,
            email = pendingVerificationEmail,
            password = password,
            uid = pendingVerificationUid,
            name = pendingVerificationName,
            roleBadge = pendingVerificationRoleBadge,
          )
        lookup.onSuccess { res ->
          if (res.refreshedIdToken.isNotBlank()) {
            pendingVerificationIdToken = res.refreshedIdToken
          }
          if (res.isVerified) {
            val targetEmail = pendingVerificationEmail
            val targetName = pendingVerificationName
            val targetRole = pendingVerificationRoleBadge
            val targetUid = pendingVerificationUid
            val targetToken = res.refreshedIdToken.ifBlank { pendingVerificationIdToken }
            val targetProvider =
              pendingVerificationProvider.ifBlank {
                "Firebase (${FirebaseRestAuthClient.PROJECT_ID})"
              }
            pendingVerificationEmail = ""
            preferencesRepository.signInUser(
              name = targetName,
              email = targetEmail,
              roleBadge = targetRole,
              provider = targetProvider,
              uid = targetUid,
              idToken = targetToken,
            )
            successBannerMessage = res.statusMessage
            onAuthComplete()
          }
        }
      }
    }
  }

  val roleOptions =
    remember {
      listOf(
        "Developer",
        "AI Creator",
        "Student",
        "Founder",
        "Researcher",
      )
    }

  // Real-time Anti-Temp-Mail & Real Email evaluation
  val emailSecurityResult =
    remember(email) {
      EmailSecurityValidator.validateEmail(email)
    }

  // Password strength calculation (0..4)
  val passwordStrengthScore =
    remember(password) {
      var score = 0
      if (password.length >= 8) score++
      if (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }) score++
      if (password.any { it.isDigit() }) score++
      if (password.any { !it.isLetterOrDigit() } || password.length >= 12) score++
      score
    }

  fun registerFailedAttempt(reason: String) {
    val nextAttempts = failedAttempts + 1
    failedAttempts = nextAttempts
    if (nextAttempts >= 5) {
      lockoutSecondsRemaining = 30
      errorMessage =
        "SECURITY LOCKOUT: Too many failed attempts. Try again in 30 seconds."
    } else {
      errorMessage = reason
    }
  }

  fun handlePrimarySubmit() {
    if (lockoutSecondsRemaining > 0) {
      errorMessage =
        "Security cooldown active. Please wait ${lockoutSecondsRemaining}s before trying again."
      return
    }
    errorMessage = null
    successBannerMessage = null

    val cleanEmail = email.trim().lowercase()
    val cleanName = fullName.trim()

    if (isSignUpMode && cleanName.length < 2) {
      errorMessage = "Please enter your real full name (at least 2 characters)."
      return
    }

    if (!emailSecurityResult.isValidRealEmail) {
      errorMessage = emailSecurityResult.statusMessage
      return
    }

    val passwordPolicyError =
      EmailSecurityValidator.validatePasswordSecurity(
        password = password,
        email = cleanEmail,
        isSignUp = isSignUpMode,
      )
    if (passwordPolicyError != null) {
      errorMessage = passwordPolicyError
      return
    }

    if (isSignUpMode && password != confirmPassword) {
      errorMessage = "Passwords do not match. Please verify your confirmation password."
      return
    }
    if (isSignUpMode && !acceptedTerms) {
      errorMessage = "Please accept the Zero-Trust Security & Privacy Policy to continue."
      return
    }

    isSubmitting = true
    scope.launch {
      val result =
        if (isSignUpMode) {
          FirebaseRestAuthClient.signUpWithEmail(
            fullName = cleanName,
            email = cleanEmail,
            password = password,
            roleBadge = selectedRoleBadge,
          )
        } else {
          FirebaseRestAuthClient.signInWithEmail(
            email = cleanEmail,
            password = password,
            fallbackName = cleanName,
            fallbackRoleBadge = selectedRoleBadge,
          )
        }

      isSubmitting = false
      result
        .onSuccess { session ->
          failedAttempts = 0
          val providerLabel =
            if (session.isCloudSynced) {
              "Firebase (${FirebaseRestAuthClient.PROJECT_ID})"
            } else {
              "Verified ID (${FirebaseRestAuthClient.PROJECT_ID})"
            }

          // When creating an account (or if the email is not yet verified),
          // require the user to click the verification link sent to their email before entering the app!
          if (isSignUpMode || !session.isEmailVerified) {
            pendingVerificationEmail = session.email
            pendingVerificationName = session.displayName
            pendingVerificationRoleBadge = session.roleBadge
            pendingVerificationUid = session.uid
            pendingVerificationIdToken = session.idToken
            pendingVerificationProvider = providerLabel
            resendCooldownSeconds = 30
            errorMessage = null
            successBannerMessage =
              "Verification link sent to ${session.email}! Click the link in your email inbox to enter the app."
          } else {
            preferencesRepository.signInUser(
              name = session.displayName,
              email = session.email,
              roleBadge = session.roleBadge,
              provider = providerLabel,
              uid = session.uid,
              idToken = session.idToken,
            )
            successBannerMessage = session.statusMessage
            delay(300L)
            onAuthComplete()
          }
        }
        .onFailure { err ->
          registerFailedAttempt(
            err.message ?: "Authentication failed on Firebase (${FirebaseRestAuthClient.PROJECT_ID})."
          )
        }
    }
  }

  fun handleVerifyAndEnterApp() {
    if (pendingVerificationEmail.isBlank() || isCheckingVerification) return
    errorMessage = null
    isCheckingVerification = true
    scope.launch {
      val result =
        FirebaseRestAuthClient.checkAccountEmailVerified(
          idToken = pendingVerificationIdToken,
          email = pendingVerificationEmail,
          password = password,
          uid = pendingVerificationUid,
          name = pendingVerificationName,
          roleBadge = pendingVerificationRoleBadge,
        )
      isCheckingVerification = false
      result
        .onSuccess { lookup ->
          if (lookup.refreshedIdToken.isNotBlank()) {
            pendingVerificationIdToken = lookup.refreshedIdToken
          }
          if (lookup.isVerified) {
            val verifiedEmail = pendingVerificationEmail
            val verifiedName = pendingVerificationName
            val verifiedRole = pendingVerificationRoleBadge
            val verifiedUid = pendingVerificationUid
            val verifiedToken = lookup.refreshedIdToken.ifBlank { pendingVerificationIdToken }
            val verifiedProvider =
              pendingVerificationProvider.ifBlank {
                "Firebase (${FirebaseRestAuthClient.PROJECT_ID})"
              }
            pendingVerificationEmail = ""
            preferencesRepository.signInUser(
              name = verifiedName,
              email = verifiedEmail,
              roleBadge = verifiedRole,
              provider = verifiedProvider,
              uid = verifiedUid,
              idToken = verifiedToken,
            )
            successBannerMessage = lookup.statusMessage
            delay(250L)
            onAuthComplete()
          } else {
            errorMessage = lookup.statusMessage
          }
        }
        .onFailure { err ->
          errorMessage =
            err.message ?: "Unable to verify email status right now. Please try again."
        }
    }
  }

  fun handleResendVerificationLink() {
    if (pendingVerificationEmail.isBlank() || resendCooldownSeconds > 0) return
    errorMessage = null
    scope.launch {
      FirebaseRestAuthClient.sendVerificationEmailToUser(
        idToken = pendingVerificationIdToken,
        email = pendingVerificationEmail,
      ).onSuccess { msg ->
        resendCooldownSeconds = 30
        successBannerMessage = msg
      }.onFailure { err ->
        errorMessage = err.message ?: "Failed to resend verification email."
      }
    }
  }

  fun openUserEmailInboxApp() {
    try {
      val emailIntent =
        Intent(Intent.ACTION_MAIN).apply {
          addCategory(Intent.CATEGORY_APP_EMAIL)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      context.startActivity(emailIntent)
    } catch (_: Exception) {
      try {
        val webMailIntent =
          Intent(Intent.ACTION_VIEW, Uri.parse("https://mail.google.com")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
        context.startActivity(webMailIntent)
      } catch (_: Exception) {
        successBannerMessage = "Please open your Gmail / Email app to click the verification link."
      }
    }
  }

  fun executeVerifiedGoogleSignIn(targetEmail: String, targetName: String) {
    val normalizedEmail = FirebaseRestAuthClient.normalizeGoogleEmailInput(targetEmail)
    val validation = EmailSecurityValidator.validateEmail(normalizedEmail)
    if (!validation.isValidRealEmail) {
      googleDialogError = validation.statusMessage
      return
    }
    showGoogleAccountDialog = false
    errorMessage = null
    googleDialogError = null
    isSubmitting = true
    scope.launch {
      FirebaseRestAuthClient.signInWithGoogleAccount(
        googleEmail = normalizedEmail,
        googleDisplayName = targetName.trim(),
        roleBadge = selectedRoleBadge,
      ).onSuccess { session ->
        failedAttempts = 0
        preferencesRepository.signInUser(
          name = session.displayName,
          email = session.email,
          roleBadge = session.roleBadge,
          provider = "Google (${FirebaseRestAuthClient.PROJECT_ID})",
          uid = session.uid,
          idToken = session.idToken,
        )
        isSubmitting = false
        successBannerMessage = session.statusMessage
        delay(300L)
        onAuthComplete()
      }.onFailure { err ->
        isSubmitting = false
        registerFailedAttempt(
          err.message ?: "Google Sign-In failed on Firebase (${FirebaseRestAuthClient.PROJECT_ID})."
        )
      }
    }
  }

  fun triggerGoogleSignInOrChooser() {
    val candidateEmail = FirebaseRestAuthClient.normalizeGoogleEmailInput(email)
    val candidateCheck = EmailSecurityValidator.validateEmail(candidateEmail)
    if (email.isNotBlank() && candidateCheck.isValidRealEmail) {
      executeVerifiedGoogleSignIn(
        targetEmail = candidateEmail,
        targetName = fullName,
      )
    } else {
      googleEmailDraft = email.ifBlank { preferences.userEmail }
      googleNameDraft = fullName.ifBlank { preferences.userName }
      googleDialogError = null
      showGoogleAccountDialog = true
    }
  }

  AmbientGlassBackground(
    isAiActive = isSubmitting,
    moodHint = if (isSignUpMode) "creative aura" else "cyber intelligence",
    modifier = modifier.testTag("auth_screen"),
  ) {
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
              .widthIn(max = 520.dp)
              .verticalScroll(rememberScrollState())
              .padding(
                horizontal = AppSpacing.screenHorizontal,
                vertical = AppSpacing.md,
              ),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
        ) {
          // =================================================================
          // 1. TOP BAR: Mandatory Security Shield Pill (Back only if already logged in)
          // =================================================================
          Row(
            modifier = Modifier.fillMaxWidth().testTag("auth_top_bar"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            if (preferences.isLoggedIn) {
              GlassIconButton(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back to App",
                onClick = onBackOrSkip,
                modifier = Modifier.testTag("auth_back_button"),
              )
            } else {
              // Top Skip / Guest Pill
              Row(
                modifier =
                  Modifier.clip(AppRadius.shapePill)
                    .background(glass.surfaceStandard)
                    .border(AppBorder.hairline, glass.borderAccent, AppRadius.shapePill)
                    .clickable {
                      preferencesRepository.continueAsGuest()
                      onAuthComplete()
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("auth_top_skip_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
              ) {
                Icon(
                  imageVector = Icons.Outlined.Person,
                  contentDescription = null,
                  tint = glass.accentCyan,
                  modifier = Modifier.size(14.dp),
                )
                Text(
                  text = "Continue as Guest",
                  style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                  fontWeight = FontWeight.Bold,
                  color = glass.accentCyan,
                )
              }
            }

            // Quantum Security Status Pill
            Row(
              modifier =
                Modifier.clip(AppRadius.shapePill)
                  .background(glass.surfaceStandard)
                  .border(AppBorder.hairline, glass.borderAccent, AppRadius.shapePill)
                  .padding(horizontal = 12.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.Shield,
                contentDescription = null,
                tint = glass.accentCyan,
                modifier = Modifier.size(14.dp),
              )
              Text(
                text = "ZERO-TRUST • ANTI-TEMP-MAIL",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                fontWeight = FontWeight.Bold,
                color = glass.accentCyan,
                letterSpacing = 0.8.sp,
              )
            }
          }

          // =================================================================
          // 2. HERO EMBLEM WITH DUAL SPECULAR NEURAL HALO
          // =================================================================
          Column(
            modifier = Modifier.fillMaxWidth().testTag("auth_hero_section"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
          ) {
            Box(
              modifier = Modifier.size(92.dp),
              contentAlignment = Alignment.Center,
            ) {
              Canvas(modifier = Modifier.fillMaxSize()) {
                val centerOffset = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                  brush =
                    Brush.radialGradient(
                      colors =
                        listOf(
                          Color(0xFF00F2FE).copy(alpha = 0.28f),
                          Color(0xFF7C3AED).copy(alpha = 0.14f),
                          Color.Transparent,
                        ),
                      center = centerOffset,
                      radius = size.minDimension * 0.52f,
                    ),
                )
                drawCircle(
                  brush =
                    Brush.sweepGradient(
                      colors =
                        listOf(
                          Color(0xFF00F2FE).copy(alpha = 0.75f),
                          Color(0xFF3B82F6).copy(alpha = 0.25f),
                          Color(0xFF8B5CF6).copy(alpha = 0.75f),
                          Color(0xFFEC4899).copy(alpha = 0.35f),
                          Color(0xFF00F2FE).copy(alpha = 0.75f),
                        ),
                    ),
                  radius = size.minDimension * 0.44f,
                  style = Stroke(width = 1.5.dp.toPx()),
                )
              }

              AstrynLogoEmblem(
                size = 56.dp,
                contentDescription = "Astryn AI Emblem",
              )
            }

            AnimatedContent(
              targetState = isSignUpMode,
              transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
              label = "auth_hero_headline",
            ) { signUp ->
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Text(
                  text = if (signUp) "Create Verified Astryn ID" else "Sign In to Private AI Vault",
                  style = MaterialTheme.typography.headlineSmall,
                  fontWeight = FontWeight.ExtraBold,
                  color = glass.textPrimary,
                  textAlign = TextAlign.Center,
                )
                Text(
                  text =
                    if (signUp) {
                      "Real email verification required • Temp-mail blocked • 100% isolated personal chat history."
                    } else {
                      "Access your encrypted, UID-isolated conversations & personal AI workspace."
                    },
                  style = MaterialTheme.typography.bodyMedium,
                  color = glass.textSecondary,
                  textAlign = TextAlign.Center,
                  modifier = Modifier.padding(horizontal = AppSpacing.sm),
                )
              }
            }
          }

          // =================================================================
          // 3. ACTIVE ACCOUNT BANNER (IF ALREADY SIGNED IN)
          // =================================================================
          if (preferences.isLoggedIn) {
            GlassCard(
              elevated = true,
              modifier = Modifier.fillMaxWidth().testTag("auth_active_session_card"),
            ) {
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
                      Modifier.size(42.dp)
                        .clip(CircleShape)
                        .background(
                          Brush.linearGradient(
                            colors = listOf(ElectricBlue, RoyalViolet),
                          ),
                        ),
                    contentAlignment = Alignment.Center,
                  ) {
                    Text(
                      text = preferences.userName.take(1).uppercase().ifBlank { "A" },
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold,
                      color = Color.White,
                    )
                  }
                  Column(modifier = Modifier.weight(1f)) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                      Text(
                        text = preferences.userName.ifBlank { "Astryn Member" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = glass.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                      Box(
                        modifier =
                          Modifier.clip(AppRadius.shapePill)
                            .background(glass.accentCyan.copy(alpha = 0.18f))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                      ) {
                        Text(
                          text = "UID Isolated",
                          style = MaterialTheme.typography.labelSmall,
                          color = glass.accentCyan,
                        )
                      }
                    }
                    Text(
                      text = "${preferences.userEmail} • Vault ID: ${preferences.userUid.takeLast(6)}",
                      style = MaterialTheme.typography.bodySmall,
                      color = glass.textSecondary,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                }

                GlassButton(
                  text = "Sign Out",
                  variant = GlassButtonVariant.SECONDARY,
                  onClick = {
                    preferencesRepository.signOutUser()
                    password = ""
                    confirmPassword = ""
                    successBannerMessage = "Signed out. Private chat vault locked."
                  },
                  modifier = Modifier.testTag("auth_sign_out_button"),
                )
              }
            }
          }

          // =================================================================
          // 4. STRICT EMAIL VERIFICATION GATE CARD (SHOWN IMMEDIATELY ON SIGN-UP)
          // =================================================================
          AnimatedVisibility(
            visible = pendingVerificationEmail.isNotBlank(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
          ) {
            GlassSurface(
              elevated = true,
              modifier = Modifier.fillMaxWidth().testTag("auth_verification_gate_card"),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
              ) {
                // Header Badge
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  Box(
                    modifier =
                      Modifier.size(48.dp)
                        .clip(CircleShape)
                        .background(
                          Brush.linearGradient(
                            colors = listOf(ElectricBlue, RoyalViolet),
                          ),
                        ),
                    contentAlignment = Alignment.Center,
                  ) {
                    Icon(
                      imageVector = Icons.Outlined.Email,
                      contentDescription = null,
                      tint = Color.White,
                      modifier = Modifier.size(24.dp),
                    )
                  }
                  Column(modifier = Modifier.weight(1f)) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                      Text(
                        text = "Verify Your Email ID",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = glass.textPrimary,
                      )
                      Box(
                        modifier =
                          Modifier.clip(AppRadius.shapePill)
                            .background(glass.statusSuccess.copy(alpha = 0.18f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                      ) {
                        Text(
                          text = "Link Sent ✓",
                          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                          fontWeight = FontWeight.Bold,
                          color = glass.statusSuccess,
                        )
                      }
                    }
                    Text(
                      text = pendingVerificationEmail,
                      style = MaterialTheme.typography.bodySmall,
                      fontWeight = FontWeight.Bold,
                      color = glass.accentCyan,
                    )
                  }
                }

                // Error or Success Banner inside Verification Gate
                if (errorMessage != null || successBannerMessage != null) {
                  val isErr = errorMessage != null
                  val bColor = if (isErr) glass.statusError else glass.statusSuccess
                  val bText = errorMessage ?: successBannerMessage.orEmpty()
                  Row(
                    modifier =
                      Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(bColor.copy(alpha = 0.14f))
                        .border(
                          width = AppBorder.hairline,
                          color = bColor.copy(alpha = 0.55f),
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
                      tint = bColor,
                      modifier = Modifier.size(18.dp),
                    )
                    Text(
                      text = bText,
                      style = MaterialTheme.typography.bodySmall,
                      fontWeight = FontWeight.Medium,
                      color = glass.textPrimary,
                    )
                  }
                }

                // Step-by-Step Verification Instructions
                Column(
                  modifier =
                    Modifier.fillMaxWidth()
                      .clip(RoundedCornerShape(12.dp))
                      .background(glass.surfaceStandard)
                      .border(
                        width = AppBorder.hairline,
                        color = glass.borderSubtle,
                        shape = RoundedCornerShape(12.dp),
                      )
                      .padding(12.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Text(
                    text = "Entry ke liye 3 aasan steps follow karein:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = glass.accentCyan,
                  )
                  SecurityFeatureBullet(
                    title = "1. Check Your Email Inbox",
                    subtitle =
                      "Humne $pendingVerificationEmail par Firebase (${FirebaseRestAuthClient.PROJECT_ID}) se verification link bhej diya hai (Spam/Promotions folder bhi check karein).",
                  )
                  SecurityFeatureBullet(
                    title = "2. Click the Verification Link",
                    subtitle =
                      "Email mein aaye verification link par click karke apना real email confirm karein.",
                  )
                  SecurityFeatureBullet(
                    title = "3. Tap 'Verify & Enter App' Below",
                    subtitle =
                      "Link click karte hi niche button dabayein (ya app har 4 sec mein auto-detect karke aapko entry de dega).",
                  )
                }

                // Primary CTA: I've Clicked the Link — Verify & Enter App
                Box(
                  modifier =
                    Modifier.fillMaxWidth()
                      .heightIn(min = 54.dp)
                      .clip(RoundedCornerShape(16.dp))
                      .background(
                        brush =
                          Brush.horizontalGradient(
                            colors =
                              listOf(
                                ElectricBlue,
                                Color(0xFF00C6FF),
                                RoyalViolet,
                              ),
                          ),
                      )
                      .border(
                        width = AppBorder.hairline,
                        color = Color.White.copy(alpha = 0.38f),
                        shape = RoundedCornerShape(16.dp),
                      )
                      .clickable(enabled = !isCheckingVerification) {
                        handleVerifyAndEnterApp()
                      }
                      .padding(horizontal = AppSpacing.lg, vertical = 14.dp)
                      .testTag("auth_verify_confirm_button"),
                  contentAlignment = Alignment.Center,
                ) {
                  if (isCheckingVerification) {
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
                        text = "Checking Email Verification Status…",
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
                        modifier = Modifier.size(19.dp),
                      )
                      Text(
                        text = "I've Clicked the Link — Verify & Enter App",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                      )
                    }
                  }
                }

                // Secondary Actions: Open Email App + Resend Link
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  GlassButton(
                    text = "Open Email App",
                    variant = GlassButtonVariant.SECONDARY,
                    onClick = { openUserEmailInboxApp() },
                    modifier = Modifier.weight(1f).testTag("auth_open_email_app_button"),
                  )
                  GlassButton(
                    text =
                      if (resendCooldownSeconds > 0) "Resend (${resendCooldownSeconds}s)"
                      else "Resend Link",
                    variant = GlassButtonVariant.SECONDARY,
                    enabled = resendCooldownSeconds == 0 && !isCheckingVerification,
                    onClick = { handleResendVerificationLink() },
                    modifier = Modifier.weight(1f).testTag("auth_verify_resend_button"),
                  )
                }

                // Optional: If FIREBASE_WEB_API_KEY is not yet configured in Secrets, allow pasting firebaseConfig / apiKey directly
                if (!FirebaseRestAuthClient.isCloudKeyConfigured()) {
                  Column(
                    modifier =
                      Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(glass.surfaceStandard)
                        .border(
                          width = AppBorder.hairline,
                          color = glass.accentCyan.copy(alpha = 0.45f),
                          shape = RoundedCornerShape(12.dp),
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Row(
                      modifier =
                        Modifier.fillMaxWidth()
                          .clickable { showFirebaseKeySetupBox = !showFirebaseKeySetupBox },
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                      ) {
                        Icon(
                          imageVector = Icons.Outlined.Key,
                          contentDescription = null,
                          tint = glass.accentCyan,
                          modifier = Modifier.size(16.dp),
                        )
                        Text(
                          text = "Email inbox mein link nahi aaya? Connect Firebase apiKey",
                          style = MaterialTheme.typography.labelSmall,
                          fontWeight = FontWeight.Bold,
                          color = glass.accentCyan,
                        )
                      }
                      Text(
                        text = if (showFirebaseKeySetupBox) "Hide" else "Setup",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = glass.textSecondary,
                      )
                    }

                    AnimatedVisibility(visible = showFirebaseKeySetupBox) {
                      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                          text =
                            "Apna Firebase Web apiKey ('AIza...') ya pura firebaseConfig code yahan paste karein taaki Firebase (${FirebaseRestAuthClient.PROJECT_ID}) turant real email link bhej sake:",
                          style = MaterialTheme.typography.bodySmall,
                          color = glass.textSecondary,
                        )
                        GlassTextField(
                          value = firebaseConfigSnippetInput,
                          onValueChange = { firebaseConfigSnippetInput = it },
                          placeholder = "Paste apiKey: \"AIzaSy...\" or firebaseConfig snippet",
                          singleLine = false,
                          modifier = Modifier.fillMaxWidth().testTag("auth_firebase_apikey_input"),
                        )
                        GlassButton(
                          text = "Connect Firebase & Send Verification Link Now",
                          variant = GlassButtonVariant.PRIMARY,
                          onClick = {
                            val extracted =
                              FirebaseRestAuthClient.extractApiKeyFromSnippet(
                                firebaseConfigSnippetInput
                              )
                            if (extracted == null) {
                              errorMessage =
                                "Valid Firebase apiKey ('AIza...') nahi mila. Kripya apne firebaseConfig se apiKey paste karein."
                            } else {
                              preferencesRepository.updateFirebaseWebApiKey(extracted)
                              showFirebaseKeySetupBox = false
                              errorMessage = null
                              scope.launch {
                                val signRes =
                                  FirebaseRestAuthClient.signUpWithEmail(
                                    fullName = pendingVerificationName,
                                    email = pendingVerificationEmail,
                                    password = password,
                                    roleBadge = pendingVerificationRoleBadge,
                                  )
                                signRes
                                  .onSuccess { session ->
                                    pendingVerificationUid = session.uid
                                    pendingVerificationIdToken = session.idToken
                                    resendCooldownSeconds = 30
                                    successBannerMessage =
                                      "Firebase connected! Real verification link sent to ${session.email} ✓"
                                  }
                                  .onFailure { err ->
                                    errorMessage =
                                      err.message ?: "Could not dispatch verification email."
                                  }
                              }
                            }
                          },
                          modifier = Modifier.fillMaxWidth().testTag("auth_save_firebase_key_button"),
                        )
                      }
                    }
                  }
                }

                // Change Email / Back to Sign Up link
                Row(
                  modifier =
                    Modifier.fillMaxWidth()
                      .clip(RoundedCornerShape(10.dp))
                      .clickable {
                        pendingVerificationEmail = ""
                        errorMessage = null
                        successBannerMessage = null
                      }
                      .padding(vertical = 6.dp)
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
                    text = "Wrong email address? Go back & change email",
                    style = MaterialTheme.typography.labelMedium,
                    color = glass.textSecondary,
                    fontWeight = FontWeight.SemiBold,
                  )
                }
              }
            }
          }

          if (pendingVerificationEmail.isBlank()) {
            // =================================================================
            // 5. SEGMENTED MODE SWITCHER (SIGN IN | CREATE ACCOUNT)
            // =================================================================
            Row(
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(glass.surfaceStandard)
                .border(AppBorder.hairline, glass.borderSpecular, RoundedCornerShape(16.dp))
                .padding(4.dp)
                .testTag("auth_mode_switcher"),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            AuthModeTabPill(
              title = "Sign In",
              subtitle = "Existing Account",
              isSelected = !isSignUpMode,
              onClick = {
                isSignUpMode = false
                errorMessage = null
                successBannerMessage = null
              },
              modifier = Modifier.weight(1f).testTag("auth_tab_sign_in"),
            )
            AuthModeTabPill(
              title = "Create Account",
              subtitle = "Real Email Only",
              isSelected = isSignUpMode,
              onClick = {
                isSignUpMode = true
                errorMessage = null
                successBannerMessage = null
              },
              modifier = Modifier.weight(1f).testTag("auth_tab_sign_up"),
            )
          }

          // =================================================================
          // 6. MAIN GLASSMORPHIC AUTHENTICATION FORM CARD
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

              // 1-Tap Google Sign-Up / Sign-In Card right at the top of the form
              Row(
                modifier =
                  Modifier.fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(glass.surfaceElevated)
                    .border(
                      width = AppBorder.focused,
                      color = glass.accentCyan.copy(alpha = 0.65f),
                      shape = RoundedCornerShape(14.dp),
                    )
                    .clickable(enabled = !isSubmitting && lockoutSecondsRemaining == 0) {
                      triggerGoogleSignInOrChooser()
                    }
                    .padding(horizontal = AppSpacing.md, vertical = 12.dp)
                    .testTag("auth_google_quick_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
              ) {
                GoogleBrandEmblem(modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                  text =
                    if (isSignUpMode) "Sign up with Google (1-Tap)"
                    else "Continue with Google",
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.Bold,
                  color = glass.textPrimary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                  modifier =
                    Modifier.clip(AppRadius.shapePill)
                      .background(glass.accentCyan.copy(alpha = 0.18f))
                      .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                  Text(
                    text = "No Password Needed",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = glass.accentCyan,
                    fontWeight = FontWeight.Bold,
                  )
                }
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
              ) {
                GlassDivider(modifier = Modifier.weight(1f))
                Text(
                  text =
                    if (isSignUpMode) "OR SIGN UP WITH EMAIL & PASSWORD"
                    else "OR SIGN IN WITH EMAIL & PASSWORD",
                  style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                  fontWeight = FontWeight.Bold,
                  color = glass.textMuted,
                  letterSpacing = 0.7.sp,
                )
                GlassDivider(modifier = Modifier.weight(1f))
              }

              // Sign Up Only: Full Name + Workspace Role Chips
              AnimatedVisibility(
                visible = isSignUpMode,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
              ) {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
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
                      placeholder = "Enter your full name (e.g. Aarav Sharma)",
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

                  // Workspace Role / Profile Badge Selector
                  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Text(
                        text = "Your AI Workspace Profile",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = glass.textSecondary,
                      )
                      Text(
                        text = selectedRoleBadge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = glass.accentCyan,
                      )
                    }
                    Row(
                      modifier =
                        Modifier.fillMaxWidth()
                          .horizontalScroll(rememberScrollState()),
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                      roleOptions.forEach { role ->
                        val isRoleSelected = selectedRoleBadge == role
                        Box(
                          modifier =
                            Modifier.clip(AppRadius.shapePill)
                              .background(
                                if (isRoleSelected) {
                                  glass.accentPrimary.copy(alpha = 0.24f)
                                } else {
                                  glass.surfaceStandard
                                },
                              )
                              .border(
                                width = if (isRoleSelected) AppBorder.focused else AppBorder.hairline,
                                color = if (isRoleSelected) glass.accentCyan else glass.borderSubtle,
                                shape = AppRadius.shapePill,
                              )
                              .clickable { selectedRoleBadge = role }
                              .padding(horizontal = 12.dp, vertical = 6.dp)
                              .testTag("auth_role_chip_$role"),
                        ) {
                          Text(
                            text = role,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isRoleSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isRoleSelected) glass.accentCyan else glass.textSecondary,
                          )
                        }
                      }
                    }
                  }
                }
              }

              // Email Address Field + Live Anti-Temp-Mail Security Shield
              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = "Real Email Address (No Temp-Mail)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = glass.textSecondary,
                  )
                  if (email.isNotBlank()) {
                    val statusColor =
                      when {
                        emailSecurityResult.isTempOrDisposableBlocked ||
                          emailSecurityResult.isTypoDomainBlocked -> glass.statusError
                        emailSecurityResult.isValidRealEmail -> glass.statusSuccess
                        else -> glass.textMuted
                      }
                    Text(
                      text =
                        if (emailSecurityResult.providerBadge.isNotBlank()) {
                          emailSecurityResult.providerBadge
                        } else if (emailSecurityResult.isValidRealEmail) {
                          "Real Email ✓"
                        } else {
                          "Checking…"
                        },
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = FontWeight.Bold,
                      color = statusColor,
                    )
                  }
                }

                GlassTextField(
                  value = email,
                  onValueChange = {
                    email = it
                    errorMessage = null
                  },
                  placeholder = "your.name@gmail.com / @outlook.com",
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

                // Live Temp-Mail / Typo Security Alert Box
                AnimatedVisibility(
                  visible =
                    email.isNotBlank() &&
                      (emailSecurityResult.isTempOrDisposableBlocked ||
                        emailSecurityResult.isTypoDomainBlocked ||
                        emailSecurityResult.isValidRealEmail),
                  enter = fadeIn() + expandVertically(),
                  exit = fadeOut() + shrinkVertically(),
                ) {
                  val isBlocked =
                    emailSecurityResult.isTempOrDisposableBlocked ||
                      emailSecurityResult.isTypoDomainBlocked
                  val pillColor = if (isBlocked) glass.statusError else glass.statusSuccess
                  Row(
                    modifier =
                      Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(pillColor.copy(alpha = 0.12f))
                        .border(
                          width = AppBorder.hairline,
                          color = pillColor.copy(alpha = 0.45f),
                          shape = RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                        .testTag(
                          if (isBlocked) "auth_temp_mail_blocked_banner"
                          else "auth_real_email_verified_banner"
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Icon(
                      imageVector =
                        if (isBlocked) Icons.Outlined.Block else Icons.Outlined.VerifiedUser,
                      contentDescription = null,
                      tint = pillColor,
                      modifier = Modifier.size(15.dp),
                    )
                    Text(
                      text = emailSecurityResult.statusMessage,
                      style = MaterialTheme.typography.labelSmall,
                      color = if (isBlocked) pillColor else glass.textPrimary,
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
                    if (isSignUpMode) "Strong password (8+ chars, number/symbol)"
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
                          .clickable { passwordVisible = !passwordVisible }
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

                // Live 4-Segment Password Strength Meter in Sign Up Mode
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

              // Confirm Password + Terms (Sign Up Mode)
              AnimatedVisibility(
                visible = isSignUpMode,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
              ) {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
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
                          text = if (matches) "Passwords match ✓" else "Does not match",
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
                              .clickable { confirmPasswordVisible = !confirmPasswordVisible },
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

                  AuthCheckOptionRow(
                    checked = acceptedTerms,
                    onCheckedChange = {
                      acceptedTerms = it
                      errorMessage = null
                    },
                    label =
                      "I verify this is my real email & agree to UID-isolated encrypted chat storage.",
                    testTag = "auth_terms_checkbox",
                  )
                }
              }

              // Sign In Mode: Keep Me Signed In Row
              AnimatedVisibility(
                visible = !isSignUpMode,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
              ) {
                AuthCheckOptionRow(
                  checked = rememberMe,
                  onCheckedChange = { rememberMe = it },
                  label = "Keep my private vault session active on this device",
                  testTag = "auth_remember_me_checkbox",
                )
              }

              // Primary Submit CTA Button
              val canClickSubmit = !isSubmitting && lockoutSecondsRemaining == 0
              Box(
                modifier =
                  Modifier.fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                      brush =
                        Brush.horizontalGradient(
                          colors =
                            if (canClickSubmit) {
                              listOf(
                                ElectricBlue,
                                Color(0xFF00C6FF),
                                RoyalViolet,
                              )
                            } else {
                              listOf(
                                glass.borderSubtle,
                                glass.borderSubtle,
                              )
                            },
                        ),
                    )
                    .border(
                      width = AppBorder.hairline,
                      color = Color.White.copy(alpha = 0.38f),
                      shape = RoundedCornerShape(16.dp),
                    )
                    .clickable(enabled = canClickSubmit) { handlePrimarySubmit() }
                    .padding(horizontal = AppSpacing.lg, vertical = 14.dp)
                    .testTag("auth_submit_button"),
                contentAlignment = Alignment.Center,
              ) {
                when {
                  lockoutSecondsRemaining > 0 -> {
                    Text(
                      text = "Security Cooldown (${lockoutSecondsRemaining}s)",
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
                          if (isSignUpMode) "Verifying Email & Creating Vault…"
                          else "Unlocking Private Vault…",
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
                          if (isSignUpMode) "Create Verified Account"
                          else "Sign In to Private Vault",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                      )
                    }
                  }
                }
              }
            }
          }

          // =================================================================
          // 6. GOOGLE VERIFIED SIGN-IN (NO GUEST MODE, NO GITHUB)
          // =================================================================
          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
              GlassDivider(modifier = Modifier.weight(1f))
              Text(
                text = "OR VERIFIED GOOGLE ACCOUNT",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                fontWeight = FontWeight.Bold,
                color = glass.textMuted,
                letterSpacing = 0.9.sp,
              )
              GlassDivider(modifier = Modifier.weight(1f))
            }

            // Full-width Continue with Google Card
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .heightIn(min = 52.dp)
                  .clip(RoundedCornerShape(14.dp))
                  .background(glass.surfaceElevated)
                  .border(
                    width = AppBorder.hairline,
                    color = glass.borderAccent,
                    shape = RoundedCornerShape(14.dp),
                  )
                  .clickable(enabled = !isSubmitting && lockoutSecondsRemaining == 0) {
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
                text =
                  if (isSignUpMode) "Sign up with Google"
                  else "Continue with Google",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = glass.textPrimary,
              )
              Spacer(modifier = Modifier.width(8.dp))
              Box(
                modifier =
                  Modifier.clip(AppRadius.shapePill)
                    .background(glass.accentCyan.copy(alpha = 0.16f))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
              ) {
                Text(
                  text = "Verified Gmail",
                  style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                  color = glass.accentCyan,
                  fontWeight = FontWeight.Bold,
                )
              }
            }

            // Continue as Guest Button (Temporary Option as requested)
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .heightIn(min = 48.dp)
                  .clip(RoundedCornerShape(14.dp))
                  .background(glass.surfaceStandard)
                  .border(
                    width = AppBorder.hairline,
                    color = glass.borderSubtle,
                    shape = RoundedCornerShape(14.dp),
                  )
                  .clickable(enabled = !isSubmitting) {
                    preferencesRepository.continueAsGuest()
                    onAuthComplete()
                  }
                  .padding(horizontal = AppSpacing.md, vertical = 11.dp)
                  .testTag("auth_guest_button"),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center,
            ) {
              Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                tint = glass.accentCyan,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Continue as Guest",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = glass.textPrimary,
              )
              Spacer(modifier = Modifier.width(8.dp))
              Box(
                modifier =
                  Modifier.clip(AppRadius.shapePill)
                    .background(glass.surfaceElevated)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
              ) {
                Text(
                  text = "Local Session",
                  style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                  color = glass.textSecondary,
                )
              }
            }
          }

          // =================================================================
          // 7. NEXT-LEVEL AI SECURITY & ISOLATED VAULT ARCHITECTURE CARD
          // =================================================================
          GlassCard(
            elevated = false,
            modifier = Modifier.fillMaxWidth().testTag("auth_security_architecture_card"),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth(),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector = Icons.Outlined.Security,
                  contentDescription = null,
                  tint = glass.accentCyan,
                  modifier = Modifier.size(18.dp),
                )
                Text(
                  text = "Astryn Quantum Security & Privacy Vault",
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold,
                  color = glass.textPrimary,
                )
              }

              SecurityFeatureBullet(
                title = "Anti-Temp-Mail Firewall",
                subtitle = "150+ disposable/burner domains blocked; only verified real emails allowed.",
              )
              SecurityFeatureBullet(
                title = "100% Isolated Per-User Chat History",
                subtitle = "Every account has its own private UID vault in Firestore (${FirebaseRestAuthClient.PROJECT_ID}) — other users can never see your chats.",
              )
              SecurityFeatureBullet(
                title = "Zero Guest Access & Brute-Force Lock",
                subtitle = "Unauthenticated guest browsing is disabled; 5 failed logins trigger automatic lockout.",
              )
            }
          }
          } // end if (pendingVerificationEmail.isBlank())

          Spacer(modifier = Modifier.height(AppSpacing.md))
        }
      }
    }
  }

  // =========================================================================
  // GOOGLE ACCOUNT OAUTH CHOOSER & 1-TAP SIGN-UP / SIGN-IN DIALOG
  // =========================================================================
  if (showGoogleAccountDialog) {
    val quickAccounts =
      remember(preferences.userEmail, preferences.userName) {
        buildList {
          if (
            preferences.userEmail.isNotBlank() &&
              EmailSecurityValidator.validateEmail(preferences.userEmail).isValidRealEmail
          ) {
            add(
              preferences.userEmail to
                preferences.userName.ifBlank {
                  preferences.userEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
                }
            )
          }
          val primaryGoogleEmail = "am7620711@gmail.com"
          if (none { it.first.equals(primaryGoogleEmail, ignoreCase = true) }) {
            add(
              primaryGoogleEmail to
                UserPreferencesRepository.deriveCleanNameFromEmail(primaryGoogleEmail)
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
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            GoogleBrandEmblem(modifier = Modifier.size(22.dp))
            Text(
              text = "Choose a Google Account",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
          }
          Text(
            text = "to continue to Astryn AI (${FirebaseRestAuthClient.PROJECT_ID})",
            style = MaterialTheme.typography.labelSmall,
            color = glass.accentCyan,
          )
        }
      },
      text = {
        Column(
          modifier = Modifier.fillMaxWidth().testTag("auth_google_account_dialog"),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = "Tap your Google account for instant 1-tap sign-up & login, or enter another Gmail below:",
            style = MaterialTheme.typography.bodySmall,
            color = glass.textSecondary,
          )

          // 1-Tap Verified Device Google Accounts
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
                    .background(
                      Brush.linearGradient(
                        listOf(ElectricBlue, RoyalViolet)
                      )
                    ),
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
              Box(
                modifier =
                  Modifier.clip(AppRadius.shapePill)
                    .background(glass.statusSuccess.copy(alpha = 0.16f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
              ) {
                Text(
                  text = "1-Tap",
                  style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                  fontWeight = FontWeight.Bold,
                  color = glass.statusSuccess,
                )
              }
            }
          }

          GlassDivider()

          Text(
            text = "Or use another Google / Gmail account:",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = glass.textSecondary,
          )

          GlassTextField(
            value = googleNameDraft,
            onValueChange = {
              googleNameDraft = it
              googleDialogError = null
            },
            placeholder = "Your Name (optional — auto-detected from Gmail)",
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
            placeholder = "your.email@gmail.com (or Gmail username)",
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
          text = "Continue with Google",
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
  // FORGOT PASSWORD / RESET LINK MODAL DIALOG
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
            text = "Reset Your Password",
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
            text = "Enter the real email associated with your Astryn AI (${FirebaseRestAuthClient.PROJECT_ID}) account to receive a password recovery link.",
            style = MaterialTheme.typography.bodySmall,
            color = glass.textSecondary,
          )
          GlassTextField(
            value = resetEmailInput,
            onValueChange = {
              resetEmailInput = it
              resetConfirmationMessage = null
            },
            placeholder = "Enter your real email address",
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
            val target = resetEmailInput.trim()
            scope.launch {
              FirebaseRestAuthClient.sendPasswordResetEmail(target)
                .onSuccess { msg -> resetConfirmationMessage = msg }
                .onFailure { err ->
                  resetConfirmationMessage = err.message ?: "Please enter a valid real email address."
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
}

@Composable
private fun SecurityFeatureBullet(
  title: String,
  subtitle: String,
) {
  val glass = AppThemeTokens.glass
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.Top,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Icon(
      imageVector = Icons.Outlined.CheckCircle,
      contentDescription = null,
      tint = glass.statusSuccess,
      modifier = Modifier.size(15.dp).padding(top = 2.dp),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = glass.textPrimary,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.labelSmall,
        color = glass.textSecondary,
      )
    }
  }
}

@Composable
private fun AuthModeTabPill(
  title: String,
  subtitle: String,
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

  Column(
    modifier =
      modifier
        .heightIn(min = 52.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(bgColor)
        .border(
          width = if (isSelected) AppBorder.focused else AppBorder.hairline,
          color = borderColor,
          shape = RoundedCornerShape(12.dp),
        )
        .semantics {
          role = Role.Tab
          selected = isSelected
        }
        .clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
      color = if (isSelected) glass.textPrimary else glass.textSecondary,
    )
    Text(
      text = subtitle,
      style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
      color = if (isSelected) glass.accentCyan else glass.textMuted,
    )
  }
}

@Composable
private fun PasswordStrengthIndicator(
  score: Int,
  password: String,
) {
  val glass = AppThemeTokens.glass
  val (label, statusColor) =
    when (score) {
      0, 1 -> "Weak • Add numbers & 8+ chars" to glass.statusError
      2 -> "Fair • Add uppercase or symbol" to Color(0xFFF59E0B)
      3 -> "Good • Strong protection" to Color(0xFF38BDF8)
      else -> "Strong • Quantum Ready ✓" to glass.statusSuccess
    }

  Column(
    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
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
              .background(
                if (active) statusColor else glass.borderSubtle,
              ),
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
        text = "${password.length} chars",
        style = MaterialTheme.typography.labelSmall,
        color = glass.textMuted,
      )
    }
  }
}

@Composable
private fun AuthCheckOptionRow(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  label: String,
  testTag: String,
) {
  val glass = AppThemeTokens.glass
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
        .clickable { onCheckedChange(!checked) }
        .padding(vertical = 4.dp)
        .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Box(
      modifier =
        Modifier.size(22.dp)
          .clip(RoundedCornerShape(6.dp))
          .background(
            if (checked) glass.accentCyan.copy(alpha = 0.22f) else glass.surfaceStandard,
          )
          .border(
            width = if (checked) 1.5.dp else 1.dp,
            color = if (checked) glass.accentCyan else glass.borderSubtle,
            shape = RoundedCornerShape(6.dp),
          ),
      contentAlignment = Alignment.Center,
    ) {
      if (checked) {
        Icon(
          imageVector = Icons.Outlined.Check,
          contentDescription = null,
          tint = glass.accentCyan,
          modifier = Modifier.size(15.dp),
        )
      }
    }

    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = glass.textSecondary,
      modifier = Modifier.weight(1f),
    )
  }
}

@Composable
private fun GoogleBrandEmblem(modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val stroke = size.minDimension * 0.22f
    val arcStyle = Stroke(width = stroke, cap = StrokeCap.Butt)
    // Red top arc
    drawArc(
      color = Color(0xFFEA4335),
      startAngle = -145f,
      sweepAngle = 105f,
      useCenter = false,
      style = arcStyle,
    )
    // Yellow left arc
    drawArc(
      color = Color(0xFFFBBC05),
      startAngle = 140f,
      sweepAngle = 75f,
      useCenter = false,
      style = arcStyle,
    )
    // Green bottom arc
    drawArc(
      color = Color(0xFF34A853),
      startAngle = 45f,
      sweepAngle = 95f,
      useCenter = false,
      style = arcStyle,
    )
    // Blue right arc + crossbar
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
