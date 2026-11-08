package com.example.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Centralized spacing and layout dimension scale to prevent horizontal overflow
 * and ensure consistent visual rhythm across compact and expanded Android screens.
 */
object AppSpacing {
  val xxs: Dp = 2.dp
  val xs: Dp = 4.dp
  val sm: Dp = 8.dp
  val md: Dp = 12.dp
  val lg: Dp = 16.dp
  val xl: Dp = 20.dp
  val xxl: Dp = 24.dp
  val xxxl: Dp = 32.dp
  val section: Dp = 40.dp

  val screenHorizontal: Dp = 16.dp
  val screenVertical: Dp = 16.dp
  val maxContentWidth: Dp = 680.dp
  val minTouchTarget: Dp = 48.dp
}

/**
 * Centralized corner radius tokens and reusable shapes.
 */
object AppRadius {
  val xs: Dp = 8.dp
  val sm: Dp = 12.dp
  val md: Dp = 16.dp
  val lg: Dp = 20.dp
  val xl: Dp = 26.dp
  val pill: Dp = 999.dp

  val shapeXs = RoundedCornerShape(xs)
  val shapeSm = RoundedCornerShape(sm)
  val shapeMd = RoundedCornerShape(md)
  val shapeLg = RoundedCornerShape(lg)
  val shapeXl = RoundedCornerShape(xl)
  val shapePill = RoundedCornerShape(percent = 50)
}

/**
 * Border width and elevation tokens.
 */
object AppBorder {
  val hairline: Dp = 1.dp
  val focused: Dp = 1.5.dp
  val emphasis: Dp = 2.dp
}

object AppElevation {
  val none: Dp = 0.dp
  val subtle: Dp = 2.dp
  val medium: Dp = 6.dp
  val high: Dp = 12.dp
}

/**
 * Opacity tokens for glass surfaces and interaction states.
 */
object AppOpacity {
  const val glassSubtle: Float = 0.55f
  const val glassStandard: Float = 0.72f
  const val glassElevated: Float = 0.85f
  const val glassFallbackSolid: Float = 0.96f
  const val disabledContent: Float = 0.48f
  const val disabledContainer: Float = 0.30f
}

/**
 * Lightweight animation tokens with first-class support for reduced motion.
 */
object AppMotion {
  const val fastDurationMs: Int = 140
  const val standardDurationMs: Int = 220
  const val emphasizedDurationMs: Int = 300
  const val pressScale: Float = 0.97f

  fun <T> standardSpec(reducedMotion: Boolean): AnimationSpec<T> =
    if (reducedMotion) {
      SnapSpec()
    } else {
      tween(durationMillis = standardDurationMs, easing = FastOutSlowInEasing)
    }

  fun <T> fastSpec(reducedMotion: Boolean): AnimationSpec<T> =
    if (reducedMotion) {
      SnapSpec()
    } else {
      tween(durationMillis = fastDurationMs, easing = FastOutSlowInEasing)
    }

  fun <T> interactiveSpring(reducedMotion: Boolean): AnimationSpec<T> =
    if (reducedMotion) {
      SnapSpec()
    } else {
      spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
      )
    }
}

/**
 * Supported theme preferences for the application.
 */
enum class AppThemeMode {
  DARK,
  LIGHT,
  SYSTEM,
}

/**
 * Semantic status variants for banners, badges, and inline notifications.
 */
enum class SemanticStatus {
  INFO,
  SUCCESS,
  WARNING,
  ERROR,
}

/**
 * Immutable holder for semantic Glassmorphism surface, border, gradient, and status tokens.
 */
@Immutable
data class GlassDesignTokens(
  val isDark: Boolean,
  val reduceTransparency: Boolean,
  val backgroundCanvas: Color,
  val backgroundSecondary: Color,
  val surfaceSubtle: Color,
  val surfaceStandard: Color,
  val surfaceElevated: Color,
  val surfaceSolidFallback: Color,
  val borderSpecular: Color,
  val borderSubtle: Color,
  val borderAccent: Color,
  val divider: Color,
  val textPrimary: Color,
  val textSecondary: Color,
  val textMuted: Color,
  val textDisabled: Color,
  val accentPrimary: Color,
  val accentSecondary: Color,
  val accentCyan: Color,
  val statusSuccess: Color,
  val statusSuccessContent: Color,
  val statusSuccessContainer: Color,
  val statusWarning: Color,
  val statusWarningContent: Color,
  val statusWarningContainer: Color,
  val statusError: Color,
  val statusErrorContent: Color,
  val statusErrorContainer: Color,
  val statusInfo: Color,
  val statusInfoContent: Color,
  val statusInfoContainer: Color,
) {
  /**
   * Returns a readable glass fill brush, automatically falling back to an opaque surface
   * when reduceTransparency is enabled.
   */
  fun glassSurfaceBrush(elevated: Boolean = false): Brush {
    if (reduceTransparency) {
      return Brush.verticalGradient(listOf(surfaceSolidFallback, surfaceSolidFallback))
    }
    val topColor = if (elevated) surfaceElevated else surfaceStandard
    val bottomColor = if (elevated) surfaceStandard else surfaceSubtle
    return Brush.linearGradient(listOf(topColor, bottomColor))
  }

  /**
   * Subtle diagonal specular border brush for glass panels and cards.
   */
  fun glassBorderBrush(isFocused: Boolean = false, isHighlighted: Boolean = false): Brush {
    return when {
      isFocused -> Brush.linearGradient(listOf(accentPrimary, accentSecondary))
      isHighlighted -> Brush.linearGradient(listOf(borderAccent, borderSubtle))
      else -> Brush.linearGradient(listOf(borderSpecular, borderSubtle))
    }
  }

  /**
   * Primary brand gradient for CTA buttons and active indicators.
   */
  val primaryActionBrush: Brush
    get() = Brush.horizontalGradient(listOf(ElectricBlue, RoyalViolet))
}

fun darkGlassTokens(reduceTransparency: Boolean = false): GlassDesignTokens =
  GlassDesignTokens(
    isDark = true,
    reduceTransparency = reduceTransparency,
    backgroundCanvas = ObsidianBlack,
    backgroundSecondary = DeepNavyBackground,
    surfaceSubtle = GlassSurfaceSubtleDark,
    surfaceStandard = GlassSurfaceStandardDark,
    surfaceElevated = GlassSurfaceElevatedDark,
    surfaceSolidFallback = GlassSurfaceSolidFallbackDark,
    borderSpecular = GlassBorderSpecularDark,
    borderSubtle = GlassBorderSubtleDark,
    borderAccent = GlassBorderAccentDark,
    divider = GlassDividerDark,
    textPrimary = TextPrimaryDark,
    textSecondary = TextSecondaryDark,
    textMuted = TextMutedDark,
    textDisabled = TextDisabledDark,
    accentPrimary = ElectricBlueBright,
    accentSecondary = RoyalViolet,
    accentCyan = LuminousCyanSoft,
    statusSuccess = StatusSuccess,
    statusSuccessContent = StatusSuccessContentDark,
    statusSuccessContainer = StatusSuccessContainerDark,
    statusWarning = StatusWarning,
    statusWarningContent = StatusWarningContentDark,
    statusWarningContainer = StatusWarningContainerDark,
    statusError = StatusError,
    statusErrorContent = StatusErrorContentDark,
    statusErrorContainer = StatusErrorContainerDark,
    statusInfo = StatusInfo,
    statusInfoContent = StatusInfoContentDark,
    statusInfoContainer = StatusInfoContainerDark,
  )

fun lightGlassTokens(reduceTransparency: Boolean = false): GlassDesignTokens =
  GlassDesignTokens(
    isDark = false,
    reduceTransparency = reduceTransparency,
    backgroundCanvas = LightBackground,
    backgroundSecondary = LightElevatedSurface,
    surfaceSubtle = GlassSurfaceSubtleLight,
    surfaceStandard = GlassSurfaceStandardLight,
    surfaceElevated = GlassSurfaceElevatedLight,
    surfaceSolidFallback = GlassSurfaceSolidFallbackLight,
    borderSpecular = GlassBorderSpecularLight,
    borderSubtle = GlassBorderSubtleLight,
    borderAccent = GlassBorderAccentLight,
    divider = GlassDividerLight,
    textPrimary = TextPrimaryLight,
    textSecondary = TextSecondaryLight,
    textMuted = TextMutedLight,
    textDisabled = TextDisabledLight,
    accentPrimary = ElectricBlueDeep,
    accentSecondary = DeepIndigo,
    accentCyan = LuminousCyan,
    statusSuccess = StatusSuccess,
    statusSuccessContent = StatusSuccessContentLight,
    statusSuccessContainer = StatusSuccessContainerLight,
    statusWarning = StatusWarning,
    statusWarningContent = StatusWarningContentLight,
    statusWarningContainer = StatusWarningContainerLight,
    statusError = StatusError,
    statusErrorContent = StatusErrorContentLight,
    statusErrorContainer = StatusErrorContainerLight,
    statusInfo = StatusInfo,
    statusInfoContent = StatusInfoContentLight,
    statusInfoContainer = StatusInfoContainerLight,
  )

val LocalGlassTokens = staticCompositionLocalOf { darkGlassTokens() }
val LocalReducedMotion = staticCompositionLocalOf { false }
