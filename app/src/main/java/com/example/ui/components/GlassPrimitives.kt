package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.example.data.repository.AiPersona
import com.example.data.repository.ChatBackgroundStyle
import com.example.data.repository.UserPreferencesRepository
import com.example.ui.theme.AppBorder
import com.example.ui.theme.AppMotion
import com.example.ui.theme.AppOpacity
import com.example.ui.theme.AppRadius
import com.example.ui.theme.AppSpacing
import com.example.ui.theme.AppThemeTokens
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.RoyalViolet
import com.example.ui.theme.SemanticStatus
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Deterministic star/neural dust particle descriptor for Cosmic & Aurora backgrounds.
 */
private data class StarParticle(
  val xFraction: Float,
  val yFraction: Float,
  val radiusDp: Float,
  val baseAlpha: Float,
  val isCyan: Boolean,
)

private val PREMIUM_STARFIELD_PARTICLES: List<StarParticle> =
  listOf(
    StarParticle(0.12f, 0.14f, 1.4f, 0.48f, true),
    StarParticle(0.28f, 0.09f, 1.0f, 0.35f, false),
    StarParticle(0.46f, 0.18f, 1.8f, 0.55f, true),
    StarParticle(0.68f, 0.11f, 1.2f, 0.40f, false),
    StarParticle(0.86f, 0.16f, 1.6f, 0.52f, true),
    StarParticle(0.08f, 0.32f, 1.1f, 0.32f, false),
    StarParticle(0.22f, 0.38f, 1.7f, 0.50f, true),
    StarParticle(0.54f, 0.29f, 1.2f, 0.36f, false),
    StarParticle(0.76f, 0.34f, 1.5f, 0.46f, true),
    StarParticle(0.91f, 0.27f, 1.0f, 0.30f, false),
    StarParticle(0.15f, 0.52f, 1.6f, 0.44f, true),
    StarParticle(0.37f, 0.48f, 1.1f, 0.34f, false),
    StarParticle(0.63f, 0.55f, 1.8f, 0.52f, true),
    StarParticle(0.84f, 0.49f, 1.2f, 0.38f, false),
    StarParticle(0.11f, 0.71f, 1.3f, 0.40f, true),
    StarParticle(0.31f, 0.67f, 1.5f, 0.46f, false),
    StarParticle(0.49f, 0.74f, 1.1f, 0.33f, true),
    StarParticle(0.72f, 0.69f, 1.7f, 0.50f, true),
    StarParticle(0.89f, 0.76f, 1.2f, 0.38f, false),
    StarParticle(0.19f, 0.88f, 1.4f, 0.42f, true),
    StarParticle(0.58f, 0.86f, 1.6f, 0.48f, false),
    StarParticle(0.81f, 0.91f, 1.2f, 0.36f, true),
  )

/**
 * Full-screen flagship atmospheric background supporting:
 * 1. Liquid Aurora Silk Waves (`LIQUID_AURORA`)
 * 2. Cosmic Nebula & Floating Micro-Stars (`COSMIC_NEBULA`)
 * 3. Pure AMOLED Pitch Black (`AMOLED_PITCH`)
 * 4. Romantic Velvet Glow (`ROMANTIC_VELVET`)
 * 5. Jarvis 3D Cyber HUD Grid (`JARVIS_CYBER_HUD`)
 * Plus Mood-Reactive ambient color shifts and AI State-Reactive breathing pulse.
 */
@Composable
fun AmbientGlassBackground(
  modifier: Modifier = Modifier,
  isAiActive: Boolean = false,
  moodHint: String = "",
  bgStyleOverride: ChatBackgroundStyle? = null,
  content: @Composable BoxScope.() -> Unit,
) {
  val glass = AppThemeTokens.glass
  val reducedMotion = AppThemeTokens.reducedMotion
  val prefs by UserPreferencesRepository.getInstance().preferencesFlow.collectAsState()

  val activeStyle = bgStyleOverride ?: prefs.chatBgStyle
  val isMoodReactive = prefs.moodReactiveBg

  // Determine mood-reactive accent palette when enabled
  val lowerMood = moodHint.lowercase()
  val isRomanticMood =
    isMoodReactive &&
      (prefs.selectedPersona == AiPersona.GIRLFRIEND ||
        lowerMood.contains("romantic") ||
        lowerMood.contains("love") ||
        lowerMood.contains("pyaar") ||
        lowerMood.contains("aanya") ||
        lowerMood.contains("zoya"))
  val isMastiMood =
    isMoodReactive &&
      (lowerMood.contains("masti") ||
        lowerMood.contains("majak") ||
        lowerMood.contains("roast") ||
        lowerMood.contains("joke"))

  val targetPrimaryGlowColor =
    when {
      !glass.isDark -> glass.accentPrimary
      isRomanticMood || activeStyle == ChatBackgroundStyle.ROMANTIC_VELVET -> Color(0xFFF43F5E)
      isMastiMood -> Color(0xFFA855F7)
      activeStyle == ChatBackgroundStyle.JARVIS_CYBER_HUD -> Color(0xFF00F2FE)
      activeStyle == ChatBackgroundStyle.COSMIC_NEBULA -> Color(0xFF6366F1)
      else -> glass.accentCyan
    }

  val targetSecondaryGlowColor =
    when {
      !glass.isDark -> glass.accentSecondary
      isRomanticMood || activeStyle == ChatBackgroundStyle.ROMANTIC_VELVET -> Color(0xFF9333EA)
      isMastiMood -> Color(0xFFF59E0B)
      activeStyle == ChatBackgroundStyle.JARVIS_CYBER_HUD -> Color(0xFF2563EB)
      activeStyle == ChatBackgroundStyle.COSMIC_NEBULA -> Color(0xFF00F2FE)
      else -> glass.accentSecondary
    }

  val primaryAuraColor by
    animateColorAsState(
      targetValue = targetPrimaryGlowColor,
      animationSpec = tween(durationMillis = if (reducedMotion) 0 else 450),
      label = "bg_primary_aura_color",
    )

  val secondaryAuraColor by
    animateColorAsState(
      targetValue = targetSecondaryGlowColor,
      animationSpec = tween(durationMillis = if (reducedMotion) 0 else 450),
      label = "bg_secondary_aura_color",
    )

  val aiPulseBoost by
    animateFloatAsState(
      targetValue = if (isAiActive) 1f else 0f,
      animationSpec = tween(durationMillis = if (reducedMotion) 0 else 380),
      label = "bg_ai_pulse_boost",
    )

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .drawWithCache {
          val baseColors =
            when {
              !glass.isDark ->
                listOf(glass.backgroundCanvas, glass.backgroundSecondary, glass.backgroundCanvas)
              activeStyle == ChatBackgroundStyle.AMOLED_PITCH ->
                listOf(Color(0xFF000000), Color(0xFF02040A), Color(0xFF000000))
              activeStyle == ChatBackgroundStyle.ROMANTIC_VELVET ->
                listOf(Color(0xFF0D0614), Color(0xFF170B24), Color(0xFF09040E))
              activeStyle == ChatBackgroundStyle.COSMIC_NEBULA ->
                listOf(Color(0xFF040716), Color(0xFF0B102B), Color(0xFF040612))
              activeStyle == ChatBackgroundStyle.JARVIS_CYBER_HUD ->
                listOf(Color(0xFF030914), Color(0xFF061326), Color(0xFF020710))
              else ->
                listOf(glass.backgroundCanvas, glass.backgroundSecondary, glass.backgroundCanvas)
            }

          val baseBrush = Brush.verticalGradient(colors = baseColors)
          val topAuraRadius = (size.minDimension * (0.78f + aiPulseBoost * 0.14f)).coerceAtLeast(1f)
          val bottomAuraRadius = (size.minDimension * (0.68f + aiPulseBoost * 0.12f)).coerceAtLeast(1f)
          val centerPulseRadius = (size.minDimension * (0.58f + aiPulseBoost * 0.18f)).coerceAtLeast(1f)

          val baseAlphaMultiplier =
            when {
              !glass.isDark -> 0.55f
              activeStyle == ChatBackgroundStyle.AMOLED_PITCH -> 0.52f
              else -> 1f
            }

          val topAuraBrush =
            Brush.radialGradient(
              colors =
                listOf(
                  primaryAuraColor.copy(
                    alpha = ((0.16f + aiPulseBoost * 0.08f) * baseAlphaMultiplier).coerceAtMost(0.35f)
                  ),
                  Color.Transparent,
                ),
              center = Offset(x = size.width * 0.82f, y = size.height * 0.12f),
              radius = topAuraRadius,
            )

          val bottomAuraBrush =
            Brush.radialGradient(
              colors =
                listOf(
                  secondaryAuraColor.copy(
                    alpha = ((0.14f + aiPulseBoost * 0.07f) * baseAlphaMultiplier).coerceAtMost(0.32f)
                  ),
                  Color.Transparent,
                ),
              center = Offset(x = size.width * 0.18f, y = size.height * 0.86f),
              radius = bottomAuraRadius,
            )

          // Middle Silk Aurora Ribbon / Breathing AI Halo
          val middleSilkBrush =
            Brush.radialGradient(
              colors =
                listOf(
                  primaryAuraColor.copy(
                    alpha = ((0.08f + aiPulseBoost * 0.10f) * baseAlphaMultiplier).coerceAtMost(0.26f)
                  ),
                  secondaryAuraColor.copy(
                    alpha = ((0.05f + aiPulseBoost * 0.06f) * baseAlphaMultiplier).coerceAtMost(0.18f)
                  ),
                  Color.Transparent,
                ),
              center = Offset(x = size.width * 0.50f, y = size.height * 0.44f),
              radius = centerPulseRadius,
            )

          onDrawBehind {
            drawRect(brush = baseBrush)

            if (!glass.reduceTransparency) {
              drawRect(brush = topAuraBrush)
              drawRect(brush = bottomAuraBrush)

              if (
                activeStyle == ChatBackgroundStyle.LIQUID_AURORA ||
                  activeStyle == ChatBackgroundStyle.ROMANTIC_VELVET ||
                  activeStyle == ChatBackgroundStyle.COSMIC_NEBULA ||
                  aiPulseBoost > 0.01f
              ) {
                drawRect(brush = middleSilkBrush)
              }

              // Subtle Liquid Aurora Diagonal Silk Wave Bands
              if (
                glass.isDark &&
                  (activeStyle == ChatBackgroundStyle.LIQUID_AURORA ||
                    activeStyle == ChatBackgroundStyle.ROMANTIC_VELVET)
              ) {
                val waveBrush =
                  Brush.linearGradient(
                    colors =
                      listOf(
                        Color.Transparent,
                        primaryAuraColor.copy(alpha = 0.05f + aiPulseBoost * 0.04f),
                        secondaryAuraColor.copy(alpha = 0.06f + aiPulseBoost * 0.04f),
                        Color.Transparent,
                      ),
                    start = Offset(0f, size.height * 0.22f),
                    end = Offset(size.width, size.height * 0.78f),
                  )
                drawRect(brush = waveBrush)
              }

              // 3D Jarvis Cyber HUD Holographic Perspective Grid
              if (glass.isDark && activeStyle == ChatBackgroundStyle.JARVIS_CYBER_HUD) {
                val gridColor = primaryAuraColor.copy(alpha = 0.09f + aiPulseBoost * 0.05f)
                val horizonY = size.height * 0.72f
                val bottomY = size.height
                // Horizontal perspective lines in bottom third
                for (i in 0..5) {
                  val t = i / 5f
                  val y = horizonY + (bottomY - horizonY) * (t * t)
                  drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                  )
                }
                // Perspective radiating lines from vanishing point
                val centerX = size.width * 0.5f
                for (i in -4..4) {
                  val bottomX = centerX + (i * size.width * 0.18f)
                  drawLine(
                    color = gridColor,
                    start = Offset(centerX, horizonY),
                    end = Offset(bottomX, bottomY),
                    strokeWidth = 1.dp.toPx(),
                  )
                }
              }

              // Floating Micro-Stars & Neural Dust (in Cosmic Nebula, Liquid Aurora, and Jarvis HUD)
              if (
                glass.isDark &&
                  activeStyle != ChatBackgroundStyle.AMOLED_PITCH
              ) {
                val starMultiplier =
                  if (activeStyle == ChatBackgroundStyle.COSMIC_NEBULA) 1.25f else 0.75f
                val verticalShift = aiPulseBoost * 6.dp.toPx()
                for (star in PREMIUM_STARFIELD_PARTICLES) {
                  val starColor =
                    if (star.isCyan) primaryAuraColor else secondaryAuraColor
                  val alpha =
                    (star.baseAlpha * starMultiplier + aiPulseBoost * 0.15f).coerceIn(0.08f, 0.78f)
                  val cx = size.width * star.xFraction
                  val cy =
                    (size.height * star.yFraction - verticalShift).coerceIn(0f, size.height)
                  drawCircle(
                    color = Color.White.copy(alpha = alpha * 0.75f),
                    radius = star.radiusDp.dp.toPx(),
                    center = Offset(cx, cy),
                  )
                  drawCircle(
                    color = starColor.copy(alpha = alpha * 0.35f),
                    radius = (star.radiusDp * 2.4f).dp.toPx(),
                    center = Offset(cx, cy),
                  )
                }
              }
            }
          }
        },
    content = content,
  )
}

/**
 * Foundational translucent glass surface with a subtle diagonal specular border
 * and automatic solid fallback when transparency is reduced.
 */
@Composable
fun GlassSurface(
  modifier: Modifier = Modifier,
  shape: Shape = AppRadius.shapeLg,
  elevated: Boolean = false,
  isFocused: Boolean = false,
  isHighlighted: Boolean = false,
  borderWidth: Dp = AppBorder.hairline,
  contentPadding: PaddingValues = PaddingValues(AppSpacing.lg),
  content: @Composable BoxScope.() -> Unit,
) {
  val glass = AppThemeTokens.glass
  val fillBrush = remember(glass, elevated) { glass.glassSurfaceBrush(elevated = elevated) }
  val borderBrush =
    remember(glass, isFocused, isHighlighted) {
      glass.glassBorderBrush(isFocused = isFocused, isHighlighted = isHighlighted)
    }

  Box(
    modifier =
      modifier
        .clip(shape)
        .background(brush = fillBrush, shape = shape)
        .border(BorderStroke(borderWidth, borderBrush), shape = shape)
        .padding(contentPadding),
  ) {
    CompositionLocalProvider(LocalContentColor provides glass.textPrimary) { content() }
  }
}

/**
 * Reusable interactive or static Glass Card for feature cards, history items, and settings panels.
 * Provides consistent press, hover, focus, and disabled visual states.
 */
@Composable
fun GlassCard(
  modifier: Modifier = Modifier,
  shape: Shape = AppRadius.shapeLg,
  elevated: Boolean = false,
  isHighlighted: Boolean = false,
  enabled: Boolean = true,
  onClick: (() -> Unit)? = null,
  contentPadding: PaddingValues = PaddingValues(AppSpacing.lg),
  content: @Composable ColumnScope.() -> Unit,
) {
  val glass = AppThemeTokens.glass
  val reducedMotion = AppThemeTokens.reducedMotion
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val isFocused by interactionSource.collectIsFocusedAsState()
  val isHovered by interactionSource.collectIsHoveredAsState()

  val scale by
    animateFloatAsState(
      targetValue = if (onClick != null && enabled && isPressed) AppMotion.pressScale else 1f,
      animationSpec = AppMotion.fastSpec(reducedMotion),
      label = "glass_card_scale",
    )

  val fillBrush = remember(glass, elevated) { glass.glassSurfaceBrush(elevated = elevated) }
  val borderBrush =
    remember(glass, isFocused, isHovered, isHighlighted) {
      glass.glassBorderBrush(
        isFocused = isFocused,
        isHighlighted = isHighlighted || isHovered,
      )
    }

  val clickModifier =
    if (onClick != null) {
      Modifier.defaultMinSize(minHeight = AppSpacing.minTouchTarget)
        .clickable(
          interactionSource = interactionSource,
          indication = androidx.compose.material3.ripple(),
          enabled = enabled,
          role = Role.Button,
          onClick = onClick,
        )
    } else {
      Modifier
    }

  Column(
    modifier =
      modifier
        .graphicsLayer {
          scaleX = scale
          scaleY = scale
          alpha = if (enabled) 1f else AppOpacity.disabledContent
        }
        .clip(shape)
        .background(brush = fillBrush, shape = shape)
        .border(
          BorderStroke(
            width = if (isFocused) AppBorder.focused else AppBorder.hairline,
            brush = borderBrush,
          ),
          shape = shape,
        )
        .then(clickModifier)
        .padding(contentPadding),
    content = content,
  )
}

/**
 * Semantic button hierarchy for Primary, Secondary, Subtle, and Destructive actions.
 */
enum class GlassButtonVariant {
  PRIMARY,
  SECONDARY,
  SUBTLE,
  DESTRUCTIVE,
}

/**
 * Accessible, touch-friendly button primitive with consistent focus, pressed, loading,
 * and disabled states. Guarantees >= 48dp minimum touch target height.
 */
@Composable
fun GlassButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  variant: GlassButtonVariant = GlassButtonVariant.PRIMARY,
  enabled: Boolean = true,
  isLoading: Boolean = false,
  leadingIcon: ImageVector? = null,
  shape: Shape = AppRadius.shapeMd,
) {
  val glass = AppThemeTokens.glass
  val reducedMotion = AppThemeTokens.reducedMotion
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val isFocused by interactionSource.collectIsFocusedAsState()

  val isInteractive = enabled && !isLoading
  val scale by
    animateFloatAsState(
      targetValue = if (isInteractive && isPressed) AppMotion.pressScale else 1f,
      animationSpec = AppMotion.fastSpec(reducedMotion),
      label = "glass_button_scale",
    )

  val backgroundBrush =
    remember(variant, isInteractive, glass) {
      when (variant) {
        GlassButtonVariant.PRIMARY ->
          if (isInteractive) {
            glass.primaryActionBrush
          } else {
            Brush.horizontalGradient(
              listOf(
                ElectricBlue.copy(alpha = AppOpacity.disabledContainer),
                RoyalViolet.copy(alpha = AppOpacity.disabledContainer),
              )
            )
          }
        GlassButtonVariant.SECONDARY -> glass.glassSurfaceBrush(elevated = true)
        GlassButtonVariant.SUBTLE ->
          Brush.verticalGradient(
            listOf(glass.surfaceSubtle.copy(alpha = 0.4f), glass.surfaceSubtle.copy(alpha = 0.25f))
          )
        GlassButtonVariant.DESTRUCTIVE ->
          Brush.verticalGradient(
            listOf(
              glass.statusErrorContainer,
              glass.statusError.copy(alpha = if (glass.isDark) 0.28f else 0.18f),
            )
          )
      }
    }

  val borderBrush =
    remember(isFocused, variant, glass) {
      when {
        isFocused -> Brush.linearGradient(listOf(glass.accentPrimary, glass.accentCyan))
        variant == GlassButtonVariant.PRIMARY ->
          Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.08f))
          )
        variant == GlassButtonVariant.DESTRUCTIVE ->
          Brush.linearGradient(
            listOf(glass.statusError.copy(alpha = 0.65f), glass.statusError.copy(alpha = 0.30f))
          )
        else -> glass.glassBorderBrush(isHighlighted = variant == GlassButtonVariant.SECONDARY)
      }
    }

  val contentColor =
    when (variant) {
      GlassButtonVariant.PRIMARY -> Color.White
      GlassButtonVariant.SECONDARY -> glass.textPrimary
      GlassButtonVariant.SUBTLE -> glass.textSecondary
      GlassButtonVariant.DESTRUCTIVE -> glass.statusErrorContent
    }.let { base -> if (isInteractive) base else base.copy(alpha = AppOpacity.disabledContent) }

  Box(
    modifier =
      modifier
        .minimumInteractiveComponentSize()
        .defaultMinSize(minHeight = AppSpacing.minTouchTarget)
        .graphicsLayer {
          scaleX = scale
          scaleY = scale
        }
        .clip(shape)
        .background(brush = backgroundBrush, shape = shape)
        .border(
          BorderStroke(
            width = if (isFocused) AppBorder.focused else AppBorder.hairline,
            brush = borderBrush,
          ),
          shape = shape,
        )
        .clickable(
          interactionSource = interactionSource,
          indication = androidx.compose.material3.ripple(),
          enabled = isInteractive,
          role = Role.Button,
          onClick = onClick,
        )
        .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
    contentAlignment = Alignment.Center,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      if (isLoading) {
        CircularProgressIndicator(
          modifier = Modifier.size(18.dp),
          color = contentColor,
          strokeWidth = 2.dp,
        )
        Spacer(modifier = Modifier.width(AppSpacing.sm))
      } else if (leadingIcon != null) {
        Icon(
          imageVector = leadingIcon,
          contentDescription = null,
          tint = contentColor,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(AppSpacing.sm))
      }
      Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = contentColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/**
 * Accessible 48x48dp glass icon button with mandatory [contentDescription] for TalkBack.
 */
@Composable
fun GlassIconButton(
  icon: ImageVector,
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  isActive: Boolean = false,
  subtle: Boolean = false,
) {
  val glass = AppThemeTokens.glass
  val reducedMotion = AppThemeTokens.reducedMotion
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val isFocused by interactionSource.collectIsFocusedAsState()

  val scale by
    animateFloatAsState(
      targetValue = if (enabled && isPressed) AppMotion.pressScale else 1f,
      animationSpec = AppMotion.fastSpec(reducedMotion),
      label = "glass_icon_btn_scale",
    )

  val backgroundColor by
    animateColorAsState(
      targetValue =
        when {
          isActive -> glass.accentPrimary.copy(alpha = 0.22f)
          subtle -> Color.Transparent
          else -> glass.surfaceStandard
        },
      animationSpec = AppMotion.fastSpec(reducedMotion),
      label = "glass_icon_btn_bg",
    )

  val borderColor =
    when {
      isFocused || isActive -> glass.borderAccent
      subtle -> Color.Transparent
      else -> glass.borderSpecular
    }

  val iconTint =
    when {
      !enabled -> glass.textDisabled
      isActive -> glass.accentPrimary
      else -> glass.textPrimary
    }

  Box(
    modifier =
      modifier
        .minimumInteractiveComponentSize()
        .size(AppSpacing.minTouchTarget)
        .graphicsLayer {
          scaleX = scale
          scaleY = scale
        }
        .clip(AppRadius.shapeMd)
        .background(backgroundColor, AppRadius.shapeMd)
        .border(
          width = if (isFocused) AppBorder.focused else AppBorder.hairline,
          color = borderColor,
          shape = AppRadius.shapeMd,
        )
        .clickable(
          interactionSource = interactionSource,
          indication = androidx.compose.material3.ripple(),
          enabled = enabled,
          role = Role.Button,
          onClick = onClick,
        )
        .semantics {
          this.contentDescription = contentDescription
          this.role = Role.Button
        },
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = iconTint,
      modifier = Modifier.size(20.dp),
    )
  }
}

/**
 * Reusable Glass TextField / Composer input field primitive with clear focus highlight,
 * accessible contrast, synchronous IME composition state, visible cursor/selection handles,
 * optional leading/trailing slots, and keyboard handling.
 */
@Composable
fun GlassTextField(
  value: String,
  onValueChange: (String) -> Unit,
  placeholder: String,
  modifier: Modifier = Modifier,
  label: String? = null,
  enabled: Boolean = true,
  singleLine: Boolean = false,
  maxLines: Int = if (singleLine) 1 else 5,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  keyboardActions: KeyboardActions = KeyboardActions.Default,
  visualTransformation: VisualTransformation = VisualTransformation.None,
  leadingContent: (@Composable () -> Unit)? = null,
  trailingContent: (@Composable () -> Unit)? = null,
) {
  val glass = AppThemeTokens.glass
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()
  val textToolbar = androidx.compose.ui.platform.LocalTextToolbar.current

  androidx.compose.runtime.LaunchedEffect(isFocused, enabled) {
    if ((!isFocused || !enabled) && textToolbar.status == androidx.compose.ui.platform.TextToolbarStatus.Shown) {
      textToolbar.hide()
    }
  }

  androidx.compose.runtime.DisposableEffect(Unit) {
    onDispose {
      if (textToolbar.status == androidx.compose.ui.platform.TextToolbarStatus.Shown) {
        textToolbar.hide()
      }
    }
  }

  // Synchronous local state mirror prevents async StateFlow round-trip lag from dropping
  // or hiding characters during rapid Android soft-keyboard / Hindi IME composition.
  // Tracking lastDispatchedValue ensures a 1-frame-delayed StateFlow echo of an earlier
  // keystroke never overwrites newer characters typed by the user, while genuine external
  // programmatic updates (e.g., clearing on Send, restoring a draft, or selecting a starter prompt)
  // immediately synchronize localText.
  var localText by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(value) }
  var lastDispatchedValue by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(value) }
  if (value != localText && value != lastDispatchedValue) {
    localText = value
    lastDispatchedValue = value
  }

  val resolvedTextColor =
    if (enabled) {
      glass.textPrimary.copy(alpha = 1f)
    } else {
      glass.textDisabled.copy(alpha = 0.75f)
    }

  val selectionColors =
    remember(glass.accentCyan) {
      androidx.compose.foundation.text.selection.TextSelectionColors(
        handleColor = glass.accentCyan,
        backgroundColor = glass.accentCyan.copy(alpha = 0.32f),
      )
    }

  val borderBrush =
    remember(glass, isFocused) {
      glass.glassBorderBrush(isFocused = isFocused, isHighlighted = isFocused)
    }

  val textFieldContent: @Composable (Modifier) -> Unit = { fieldModifier ->
    CompositionLocalProvider(
      LocalContentColor provides resolvedTextColor,
      androidx.compose.foundation.text.selection.LocalTextSelectionColors provides selectionColors,
    ) {
      BasicTextField(
        value = localText,
        onValueChange = { updatedText ->
          localText = updatedText
          lastDispatchedValue = updatedText
          onValueChange(updatedText)
        },
        modifier = fieldModifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = singleLine,
        maxLines = maxLines,
        interactionSource = interactionSource,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        textStyle =
          MaterialTheme.typography.bodyLarge.copy(
            color = resolvedTextColor,
          ),
        cursorBrush = SolidColor(glass.accentCyan),
        decorationBox = { innerTextField ->
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .defaultMinSize(minHeight = AppSpacing.minTouchTarget)
                .clip(AppRadius.shapeLg)
                .background(
                  brush = glass.glassSurfaceBrush(elevated = true),
                  shape = AppRadius.shapeLg,
                )
                .border(
                  BorderStroke(
                    width = if (isFocused) AppBorder.focused else AppBorder.hairline,
                    brush = borderBrush,
                  ),
                  shape = AppRadius.shapeLg,
                )
                .padding(horizontal = AppSpacing.md, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            if (leadingContent != null) {
              leadingContent()
              Spacer(modifier = Modifier.width(AppSpacing.sm))
            }
            Box(
              modifier = Modifier.weight(1f).fillMaxWidth(),
              contentAlignment = Alignment.CenterStart,
            ) {
              if (localText.isEmpty()) {
                Text(
                  text = placeholder,
                  style = MaterialTheme.typography.bodyLarge,
                  color = glass.textMuted,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              Box(modifier = Modifier.fillMaxWidth()) {
                innerTextField()
              }
            }
            if (trailingContent != null) {
              Spacer(modifier = Modifier.width(AppSpacing.sm))
              trailingContent()
            }
          }
        },
      )
    }
  }

  if (!label.isNullOrBlank()) {
    Column(modifier = modifier.fillMaxWidth()) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (isFocused) glass.accentPrimary else glass.textSecondary,
        modifier = Modifier.padding(bottom = AppSpacing.xs),
      )
      textFieldContent(Modifier.fillMaxWidth())
    }
  } else {
    textFieldContent(modifier)
  }
}

/**
 * Restrained translucent gradient divider for separating sections and list items.
 */
@Composable
fun GlassDivider(modifier: Modifier = Modifier) {
  val glass = AppThemeTokens.glass
  val brush =
    remember(glass) {
      Brush.horizontalGradient(
        colors = listOf(Color.Transparent, glass.divider, glass.borderSpecular, glass.divider, Color.Transparent)
      )
    }
  Box(modifier = modifier.fillMaxWidth().height(1.dp).background(brush = brush))
}

/**
 * Lightweight, accessible loading indicator primitive that respects reduced-motion settings.
 */
@Composable
fun GlassLoadingIndicator(
  label: String,
  modifier: Modifier = Modifier,
) {
  val glass = AppThemeTokens.glass
  val reducedMotion = AppThemeTokens.reducedMotion

  val pulseAlpha =
    if (reducedMotion) {
      0.85f
    } else {
      val transition = rememberInfiniteTransition(label = "glass_loading_pulse")
      val animated by
        transition.animateFloat(
          initialValue = 0.45f,
          targetValue = 1.0f,
          animationSpec =
            infiniteRepeatable(
              animation = tween(durationMillis = 900, easing = LinearEasing),
              repeatMode = RepeatMode.Reverse,
            ),
          label = "glass_loading_alpha",
        )
      animated
    }

  Row(
    modifier =
      modifier
        .semantics {
          liveRegion = LiveRegionMode.Polite
          contentDescription = label
        }
        .clip(AppRadius.shapePill)
        .background(glass.surfaceStandard, AppRadius.shapePill)
        .border(AppBorder.hairline, glass.borderSpecular, AppRadius.shapePill)
        .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
  ) {
    if (reducedMotion) {
      Box(
        modifier =
          Modifier.size(10.dp)
            .clip(AppRadius.shapePill)
            .background(glass.accentPrimary)
      )
    } else {
      CircularProgressIndicator(
        modifier = Modifier.size(16.dp),
        color = glass.accentPrimary,
        strokeWidth = 2.dp,
      )
    }
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = glass.textSecondary,
      modifier = Modifier.alpha(pulseAlpha),
    )
  }
}

/**
 * Semantic status banner for Info, Success, Warning, and Error states.
 */
@Composable
fun GlassStatusBanner(
  title: String,
  message: String,
  status: SemanticStatus,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
) {
  val glass = AppThemeTokens.glass
  val (containerColor, borderColor, contentColor) =
    when (status) {
      SemanticStatus.INFO ->
        Triple(glass.statusInfoContainer, glass.statusInfo, glass.statusInfoContent)
      SemanticStatus.SUCCESS ->
        Triple(glass.statusSuccessContainer, glass.statusSuccess, glass.statusSuccessContent)
      SemanticStatus.WARNING ->
        Triple(glass.statusWarningContainer, glass.statusWarning, glass.statusWarningContent)
      SemanticStatus.ERROR ->
        Triple(glass.statusErrorContainer, glass.statusError, glass.statusErrorContent)
    }

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(AppRadius.shapeMd)
        .background(containerColor, AppRadius.shapeMd)
        .border(AppBorder.hairline, borderColor.copy(alpha = 0.55f), AppRadius.shapeMd)
        .padding(AppSpacing.md),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
  ) {
    if (icon != null) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = borderColor,
        modifier = Modifier.size(20.dp),
      )
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = contentColor,
      )
      Spacer(modifier = Modifier.height(AppSpacing.xxs))
      Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = contentColor.copy(alpha = 0.9f),
      )
    }
  }
}
