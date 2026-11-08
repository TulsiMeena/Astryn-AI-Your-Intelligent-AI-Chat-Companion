package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.AppBorder
import com.example.ui.theme.AppThemeTokens

/**
 * Official Astryn AI Geometric Emblem ('A' ribbon + diagonal orbital ring + glowing cyan sphere).
 *
 * Uses hardware-accelerated [Modifier.drawWithCache] so all Paths and Gradient Brushes are cached
 * once per layout size and rendered losslessly at any dimension (from 20dp chat avatar badges
 * to 72dp empty-state hero emblems).
 */
@Composable
fun AstrynLogoEmblem(
  modifier: Modifier = Modifier,
  size: Dp = 36.dp,
  showContainer: Boolean = true,
  contentDescription: String? = "Astryn AI logo",
) {
  val glass = AppThemeTokens.glass
  val cornerRadius = (size.value * 0.28f).dp
  val shape = RoundedCornerShape(cornerRadius)

  val semanticsModifier =
    if (contentDescription != null) {
      Modifier.semantics {
        this.contentDescription = contentDescription
        this.role = Role.Image
      }
    } else {
      Modifier
    }

  val containerModifier =
    if (showContainer) {
      Modifier.clip(shape)
        .background(
          brush =
            Brush.linearGradient(
              colors =
                listOf(
                  Color(0xFF060919),
                  Color(0xFF0E1632),
                  Color(0xFF080C1F),
                )
            ),
          shape = shape,
        )
        .border(
          width = AppBorder.hairline,
          brush =
            Brush.linearGradient(
              colors =
                listOf(
                  Color(0xFF00F2FE).copy(alpha = 0.75f),
                  Color(0xFF3B82F6).copy(alpha = 0.55f),
                  Color(0xFFA855F7).copy(alpha = 0.70f),
                )
            ),
          shape = shape,
        )
    } else {
      Modifier
    }

  Box(
    modifier =
      modifier
        .size(size)
        .then(containerModifier)
        .then(semanticsModifier)
        .drawWithCache {
          val w = this.size.width
          val h = this.size.height
          val center = Offset(w / 2f, h / 2f)

          // Right Ribbon Leg Path (Indigo -> Violet -> Magenta)
          val rightLegPath =
            Path().apply {
              moveTo(w * 0.475f, h * 0.22f)
              cubicTo(w * 0.495f, h * 0.18f, w * 0.525f, h * 0.18f, w * 0.545f, h * 0.22f)
              lineTo(w * 0.735f, h * 0.74f)
              cubicTo(w * 0.748f, h * 0.775f, w * 0.728f, h * 0.80f, w * 0.692f, h * 0.80f)
              lineTo(w * 0.600f, h * 0.80f)
              cubicTo(w * 0.575f, h * 0.80f, w * 0.558f, h * 0.782f, w * 0.548f, h * 0.755f)
              lineTo(w * 0.442f, h * 0.435f)
              close()
            }

          val rightLegBrush =
            Brush.linearGradient(
              colors =
                listOf(
                  Color(0xFF3B82F6),
                  Color(0xFF6366F1),
                  Color(0xFFA855F7),
                  Color(0xFFEC4899),
                ),
              start = Offset(w * 0.46f, h * 0.22f),
              end = Offset(w * 0.72f, h * 0.80f),
            )

          // Left Ribbon Leg Path (Luminous Cyan -> Electric Blue)
          val leftLegPath =
            Path().apply {
              moveTo(w * 0.468f, h * 0.21f)
              cubicTo(w * 0.488f, h * 0.172f, w * 0.532f, h * 0.172f, w * 0.552f, h * 0.21f)
              lineTo(w * 0.582f, h * 0.295f)
              cubicTo(w * 0.538f, h * 0.29f, w * 0.502f, h * 0.315f, w * 0.478f, h * 0.375f)
              lineTo(w * 0.342f, h * 0.755f)
              cubicTo(w * 0.330f, h * 0.782f, w * 0.312f, h * 0.80f, w * 0.285f, h * 0.80f)
              lineTo(w * 0.208f, h * 0.80f)
              cubicTo(w * 0.175f, h * 0.80f, w * 0.155f, h * 0.775f, w * 0.170f, h * 0.74f)
              close()
            }

          val leftLegBrush =
            Brush.linearGradient(
              colors =
                listOf(
                  Color(0xFF00F2FE),
                  Color(0xFF38BDF8),
                  Color(0xFF3B82F6),
                  Color(0xFF1D4ED8),
                ),
              start = Offset(w * 0.52f, h * 0.19f),
              end = Offset(w * 0.24f, h * 0.80f),
            )

          // Orbital Ring Geometry
          val orbitWidth = w * 0.76f
          val orbitHeight = h * 0.24f
          val orbitTopLeft = Offset(center.x - orbitWidth / 2f, center.y - orbitHeight / 2f)
          val orbitSize = Size(orbitWidth, orbitHeight)
          val backOrbitStroke =
            Stroke(width = (w * 0.032f).coerceAtLeast(1.2f), cap = StrokeCap.Round)
          val frontOrbitStroke =
            Stroke(
              width = (w * 0.048f).coerceAtLeast(1.8f),
              cap = StrokeCap.Round,
              join = StrokeJoin.Round,
            )

          val orbitBrush =
            Brush.linearGradient(
              colors =
                listOf(
                  Color(0xFF00F2FE),
                  Color(0xFF60A5FA),
                  Color(0xFFC084FC),
                  Color(0xFF00F2FE),
                ),
              start = Offset(w * 0.15f, h * 0.65f),
              end = Offset(w * 0.85f, h * 0.32f),
            )

          // Orbiting Cyan Sphere (Upper-Right Orbit)
          val orbCenter = Offset(w * 0.72f, h * 0.32f)
          val orbRadius = (w * 0.068f).coerceAtLeast(2.2f)
          val orbGlowRadius = orbRadius * 2.1f
          val orbGlowBrush =
            Brush.radialGradient(
              colors =
                listOf(
                  Color(0xFF00F2FE).copy(alpha = 0.55f),
                  Color(0xFF3B82F6).copy(alpha = 0.20f),
                  Color.Transparent,
                ),
              center = orbCenter,
              radius = orbGlowRadius,
            )
          val orbCoreBrush =
            Brush.radialGradient(
              colors =
                listOf(
                  Color.White,
                  Color(0xFF00F2FE),
                  Color(0xFF3B82F6),
                  Color(0xFF1E3A8A),
                ),
              center = Offset(orbCenter.x - orbRadius * 0.28f, orbCenter.y - orbRadius * 0.28f),
              radius = orbRadius * 1.35f,
            )

          onDrawBehind {
            // 1. Back Orbital Arc
            rotate(degrees = -23f, pivot = center) {
              drawArc(
                brush = orbitBrush,
                startAngle = 185f,
                sweepAngle = 170f,
                useCenter = false,
                topLeft = orbitTopLeft,
                size = orbitSize,
                alpha = 0.55f,
                style = backOrbitStroke,
              )
            }

            // 2. Right Ribbon Leg of 'A'
            drawPath(path = rightLegPath, brush = rightLegBrush)

            // 3. Left Ribbon Leg of 'A'
            drawPath(path = leftLegPath, brush = leftLegBrush)

            // 4. Foreground Orbital Arc Sweeping Across 'A'
            rotate(degrees = -23f, pivot = center) {
              drawArc(
                brush = orbitBrush,
                startAngle = -5f,
                sweepAngle = 190f,
                useCenter = false,
                topLeft = orbitTopLeft,
                size = orbitSize,
                style = frontOrbitStroke,
              )
            }

            // 5. Glowing Cyan Planet Sphere on Upper-Right Orbit
            drawCircle(brush = orbGlowBrush, radius = orbGlowRadius, center = orbCenter)
            drawCircle(brush = orbCoreBrush, radius = orbRadius, center = orbCenter)
          }
        },
  )
}

/**
 * Official Astryn AI Brand Wordmark & Emblem Lockup for About / Settings / Header sections.
 * Displays the official uploaded reference logo asset along with brand-only attribution.
 */
@Composable
fun AstrynBrandShowcase(
  versionName: String,
  modifier: Modifier = Modifier,
) {
  val glass = AppThemeTokens.glass

  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Box(
      modifier =
        Modifier.size(56.dp)
          .clip(RoundedCornerShape(16.dp))
          .border(
            width = AppBorder.hairline,
            brush =
              Brush.linearGradient(
                listOf(
                  Color(0xFF00F2FE).copy(alpha = 0.7f),
                  Color(0xFFA855F7).copy(alpha = 0.7f),
                )
              ),
            shape = RoundedCornerShape(16.dp),
          )
          .testTag("astryn_official_logo_image"),
      contentAlignment = Alignment.Center,
    ) {
      Image(
        painter = painterResource(id = R.drawable.astryn_logo_official),
        contentDescription = "Astryn AI Official Logo",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )
    }

    Column {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = "ASTRYN",
          style =
            MaterialTheme.typography.titleMedium.copy(
              letterSpacing = 2.2.sp,
              fontWeight = FontWeight.ExtraBold,
            ),
          color = glass.textPrimary,
        )
        Text(
          text = "AI",
          style =
            MaterialTheme.typography.labelLarge.copy(
              letterSpacing = 1.6.sp,
              fontWeight = FontWeight.Bold,
            ),
          color = glass.accentCyan,
        )
      }
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = "Astryn AI • Intelligent Assistant",
        style = MaterialTheme.typography.labelSmall,
        color = glass.accentPrimary,
        fontWeight = FontWeight.Medium,
      )
      Text(
        text = "Version $versionName • Google Gemini API (v1beta)",
        style = MaterialTheme.typography.bodySmall,
        color = glass.textSecondary,
        modifier = Modifier.testTag("settings_version_text"),
      )
    }
  }
}
