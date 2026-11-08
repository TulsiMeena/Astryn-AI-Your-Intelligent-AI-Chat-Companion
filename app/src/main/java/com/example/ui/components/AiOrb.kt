package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.example.ui.theme.AppThemeTokens
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueBright
import com.example.ui.theme.LuminousCyan
import com.example.ui.theme.LuminousCyanSoft
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.RoyalViolet
import com.example.ui.theme.SoftViolet
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * State representing the visual activity level of the AI Orb.
 */
enum class AiOrbState {
  IDLE,
  LISTENING,
  THINKING,
}

/**
 * Ultra-premium, hardware-accelerated 2.5D visual AI Orb.
 *
 * Performance & Memory Safeguards (Prompt 12):
 * - Respects [AppThemeTokens.reducedMotion] and Activity [Lifecycle.State.RESUMED]: all continuous
 *   frame invalidations and infinite transitions halt immediately when reduced motion is enabled
 *   or when the screen is backgrounded/paused.
 * - All [Brush], [Shader], [Stroke], [Offset], and [Size] allocations are hoisted into the outer
 *   [Modifier.drawWithCache] scope outside `onDrawBehind`, resulting in zero shader/gradient
 *   allocations during 60fps animation frames.
 */
@Composable
fun AiOrb(
  modifier: Modifier = Modifier,
  size: Dp = 210.dp,
  state: AiOrbState = AiOrbState.IDLE,
  onClick: (() -> Unit)? = null,
) {
  val glass = AppThemeTokens.glass
  val reducedMotion = AppThemeTokens.reducedMotion
  val lifecycleOwner = LocalLifecycleOwner.current
  val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
  val isAnimationActive = !reducedMotion && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

  val coroutineScope = rememberCoroutineScope()
  val touchPulse = remember { Animatable(1f) }

  // Continuous animation transitions are only instantiated when active and visible
  val pulseFactor: Float
  val ringRotation1: Float
  val ringRotation2: Float
  val plasmaShift: Float

  if (!isAnimationActive) {
    pulseFactor = 1f
    ringRotation1 = 25f
    ringRotation2 = -35f
    plasmaShift = 0f
  } else {
    val infiniteTransition = rememberInfiniteTransition(label = "ai_orb_loop")

    val animatedPulse by
      infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec =
          infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
          ),
        label = "ai_orb_pulse",
      )
    val animatedRing1 by
      infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
          infiniteRepeatable(
            animation =
              tween(
                durationMillis = if (state == AiOrbState.THINKING) 4000 else 9000,
                easing = LinearEasing,
              ),
            repeatMode = RepeatMode.Restart,
          ),
        label = "ai_orb_ring1",
      )
    val animatedRing2 by
      infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec =
          infiniteRepeatable(
            animation =
              tween(
                durationMillis = if (state == AiOrbState.THINKING) 5500 else 12000,
                easing = LinearEasing,
              ),
            repeatMode = RepeatMode.Restart,
          ),
        label = "ai_orb_ring2",
      )
    val animatedPlasma by
      infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
          infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
          ),
        label = "ai_orb_plasma",
      )

    pulseFactor = animatedPulse
    ringRotation1 = animatedRing1
    ringRotation2 = animatedRing2
    plasmaShift = animatedPlasma
  }

  val clickModifier =
    if (onClick != null) {
      Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        role = Role.Button,
        onClick = {
          if (!reducedMotion) {
            coroutineScope.launch {
              touchPulse.animateTo(1.15f, tween(120))
              touchPulse.animateTo(1f, tween(300))
            }
          }
          onClick()
        },
      )
    } else {
      Modifier
    }

  val isDark = glass.isDark

  Box(
    modifier =
      modifier
        .size(size)
        .testTag("ai_orb_container")
        .semantics {
          contentDescription = "Astryn AI core, online and ready"
          role = Role.Image
        }
        .then(clickModifier),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier =
        Modifier.size(size)
          .drawWithCache {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseSphereRadius = (this.size.minDimension / 2f) * 0.68f
            val baseOuterBloomRadius = ((this.size.minDimension / 2f) * 0.98f).coerceAtLeast(1f)

            // Pre-cached Brushes and Geometries (allocated once per layout size / theme change,
            // never re-allocated inside onDrawBehind during 60fps animation frames)
            val outerBloomBrush =
              Brush.radialGradient(
                colors =
                  listOf(
                    ElectricBlueBright.copy(alpha = if (isDark) 0.38f else 0.22f),
                    RoyalViolet.copy(alpha = if (isDark) 0.22f else 0.12f),
                    LuminousCyan.copy(alpha = if (isDark) 0.10f else 0.05f),
                    Color.Transparent,
                  ),
                center = center,
                radius = baseOuterBloomRadius,
              )

            val ringRect1 = Size(width = baseSphereRadius * 2.35f, height = baseSphereRadius * 0.85f)
            val ringOffset1 =
              Offset(
                x = center.x - (ringRect1.width / 2f),
                y = center.y - (ringRect1.height / 2f),
              )
            val ringBrush1 =
              Brush.linearGradient(
                colors =
                  listOf(
                    Color.Transparent,
                    LuminousCyanSoft.copy(alpha = 0.85f),
                    ElectricBlue.copy(alpha = 0.60f),
                    RoyalViolet.copy(alpha = 0.20f),
                    Color.Transparent,
                  )
              )
            val ringStroke1 = Stroke(width = 2.2f)

            val ringRect2 = Size(width = baseSphereRadius * 2.15f, height = baseSphereRadius * 0.72f)
            val ringOffset2 =
              Offset(
                x = center.x - (ringRect2.width / 2f),
                y = center.y - (ringRect2.height / 2f),
              )
            val ringBrush2 =
              Brush.linearGradient(
                colors =
                  listOf(
                    Color.Transparent,
                    SoftViolet.copy(alpha = 0.75f),
                    ElectricBlueBright.copy(alpha = 0.50f),
                    LuminousCyan.copy(alpha = 0.15f),
                    Color.Transparent,
                  )
              )
            val ringStroke2 = Stroke(width = 1.8f)

            val lightSource =
              Offset(
                x = center.x - (baseSphereRadius * 0.34f),
                y = center.y - (baseSphereRadius * 0.34f),
              )
            val coreSphereBrush =
              Brush.radialGradient(
                colorStops =
                  arrayOf(
                    0.00f to Color.White.copy(alpha = 0.95f),
                    0.12f to LuminousCyanSoft,
                    0.32f to ElectricBlueBright,
                    0.56f to RoyalViolet,
                    0.78f to Color(0xFF2E1065),
                    0.94f to ObsidianBlack.copy(alpha = 0.98f),
                    1.00f to ElectricBlue.copy(alpha = 0.35f),
                  ),
                center = lightSource,
                radius = (baseSphereRadius * 1.55f).coerceAtLeast(1f),
              )

            val coronaBrush1 =
              Brush.sweepGradient(
                listOf(
                  Color.Transparent,
                  LuminousCyan.copy(alpha = 0.85f),
                  ElectricBlueBright.copy(alpha = 0.45f),
                  Color.Transparent,
                ),
                center = center,
              )
            val coronaTopLeft1 =
              Offset(center.x - (baseSphereRadius * 0.82f), center.y - (baseSphereRadius * 0.82f))
            val coronaSize1 = Size(baseSphereRadius * 1.64f, baseSphereRadius * 1.64f)
            val plasmaStroke1 = Stroke(width = 3.5f, cap = StrokeCap.Round)

            val coronaBrush2 =
              Brush.sweepGradient(
                listOf(
                  Color.Transparent,
                  SoftViolet.copy(alpha = 0.80f),
                  Color.Transparent,
                ),
                center = center,
              )
            val coronaTopLeft2 =
              Offset(center.x - (baseSphereRadius * 0.65f), center.y - (baseSphereRadius * 0.65f))
            val coronaSize2 = Size(baseSphereRadius * 1.30f, baseSphereRadius * 1.30f)
            val plasmaStroke2 = Stroke(width = 2.5f, cap = StrokeCap.Round)

            val specularGlintCenter =
              Offset(
                x = center.x - (baseSphereRadius * 0.38f),
                y = center.y - (baseSphereRadius * 0.38f),
              )
            val glintRadius = (baseSphereRadius * 0.28f).coerceAtLeast(1f)
            val specularGlintBrush =
              Brush.radialGradient(
                colors =
                  listOf(
                    Color.White.copy(alpha = 0.85f),
                    Color.White.copy(alpha = 0.35f),
                    Color.Transparent,
                  ),
                center = specularGlintCenter,
                radius = glintRadius,
              )

            val fresnelRimBrush =
              Brush.linearGradient(
                colors =
                  listOf(
                    Color.White.copy(alpha = 0.55f),
                    LuminousCyan.copy(alpha = 0.45f),
                    ElectricBlue.copy(alpha = 0.25f),
                    Color.Transparent,
                  ),
                start = Offset(center.x - baseSphereRadius, center.y - baseSphereRadius),
                end = Offset(center.x + baseSphereRadius, center.y + baseSphereRadius),
              )
            val fresnelStroke = Stroke(width = 1.6f)

            onDrawBehind {
              val activeTouchScale = touchPulse.value

              // Layer 1: Ambient Outer Celestial Bloom
              scale(scale = pulseFactor, pivot = center) {
                drawCircle(
                  brush = outerBloomBrush,
                  radius = baseOuterBloomRadius,
                  center = center,
                )
              }

              scale(scale = activeTouchScale, pivot = center) {
                // Layer 2: Orbital Energy Ring 1
                rotate(degrees = ringRotation1, pivot = center) {
                  drawOval(
                    brush = ringBrush1,
                    topLeft = ringOffset1,
                    size = ringRect1,
                    style = ringStroke1,
                  )
                }

                // Layer 3: Orbital Energy Ring 2
                rotate(degrees = ringRotation2, pivot = center) {
                  drawOval(
                    brush = ringBrush2,
                    topLeft = ringOffset2,
                    size = ringRect2,
                    style = ringStroke2,
                  )
                }

                // Layer 4: Volumetric 3D Shaded Core Sphere
                drawCircle(
                  brush = coreSphereBrush,
                  radius = baseSphereRadius,
                  center = center,
                )

                // Layer 5: Dynamic Corona Plasma Swirls (Surface Detail)
                if (isAnimationActive) {
                  val waveAngle = plasmaShift * 360f
                  drawArc(
                    brush = coronaBrush1,
                    startAngle = waveAngle,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = coronaTopLeft1,
                    size = coronaSize1,
                    style = plasmaStroke1,
                  )

                  drawArc(
                    brush = coronaBrush2,
                    startAngle = -waveAngle * 1.3f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = coronaTopLeft2,
                    size = coronaSize2,
                    style = plasmaStroke2,
                  )
                }

                // Layer 6: Specular Highlight Glint & Fresnel Rim
                drawCircle(
                  brush = specularGlintBrush,
                  radius = glintRadius,
                  center = specularGlintCenter,
                )

                drawCircle(
                  brush = fresnelRimBrush,
                  radius = baseSphereRadius,
                  center = center,
                  style = fresnelStroke,
                )

                // Layer 7: Orbiting Particle Spark Embers
                if (isAnimationActive) {
                  val particleCount = 4
                  for (i in 0 until particleCount) {
                    val particleAngle =
                      (plasmaShift * 2 * PI + (i * (2 * PI / particleCount))).toFloat()
                    val orbitRadiusX = baseSphereRadius * (1.18f + (i * 0.08f))
                    val orbitRadiusY = baseSphereRadius * (0.58f + (i * 0.06f))

                    val px = center.x + cos(particleAngle) * orbitRadiusX
                    val py = center.y + sin(particleAngle) * orbitRadiusY
                    val pAlpha = (0.45f + 0.45f * sin(particleAngle + i)).coerceIn(0.2f, 1f)

                    drawCircle(
                      color =
                        if (i % 2 == 0) LuminousCyanSoft.copy(alpha = pAlpha)
                        else SoftViolet.copy(alpha = pAlpha),
                      radius = if (i % 2 == 0) 3.2f else 2.5f,
                      center = Offset(px, py),
                    )
                  }
                }
              }
            }
          }
    )

    // Official Astryn AI geometric 'A' + orbital emblem suspended inside the 3D core
    AstrynLogoEmblem(
      size = size * 0.44f,
      showContainer = false,
      contentDescription = null,
    )
  }
}

