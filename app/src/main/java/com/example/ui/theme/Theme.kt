package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

private val DarkColorScheme =
  darkColorScheme(
    primary = ElectricBlueBright,
    onPrimary = ObsidianBlack,
    primaryContainer = ElevatedNavySurface,
    onPrimaryContainer = TextPrimaryDark,
    secondary = RoyalViolet,
    onSecondary = TextPrimaryDark,
    secondaryContainer = SubtleBlueGraySurface,
    onSecondaryContainer = TextPrimaryDark,
    tertiary = LuminousCyanSoft,
    onTertiary = ObsidianBlack,
    background = ObsidianBlack,
    onBackground = TextPrimaryDark,
    surface = DeepNavyBackground,
    onSurface = TextPrimaryDark,
    surfaceVariant = MidnightSurface,
    onSurfaceVariant = TextSecondaryDark,
    outline = GlassBorderSpecularDark,
    outlineVariant = GlassBorderSubtleDark,
    error = StatusError,
    onError = Color.White,
    errorContainer = StatusErrorContainerDark,
    onErrorContainer = StatusErrorContentDark,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = ElectricBlueDeep,
    onPrimary = Color.White,
    primaryContainer = LightElevatedSurface,
    onPrimaryContainer = TextPrimaryLight,
    secondary = DeepIndigo,
    onSecondary = Color.White,
    secondaryContainer = LightSubtleSurface,
    onSecondaryContainer = TextPrimaryLight,
    tertiary = LuminousCyan,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightElevatedSurface,
    onSurfaceVariant = TextSecondaryLight,
    outline = GlassBorderSpecularLight,
    outlineVariant = GlassBorderSubtleLight,
    error = StatusError,
    onError = Color.White,
    errorContainer = StatusErrorContainerLight,
    onErrorContainer = StatusErrorContentLight,
  )

val AppShapes =
  Shapes(
    extraSmall = AppRadius.shapeXs,
    small = AppRadius.shapeSm,
    medium = AppRadius.shapeMd,
    large = AppRadius.shapeLg,
    extraLarge = AppRadius.shapeXl,
  )

/**
 * Compose-native TextToolbar implementation that avoids Android DecorView's
 * FloatingActionMode race condition ("Destroying unexpected ActionMode instance of TYPE_FLOATING").
 */
class SafeGlassTextToolbar : TextToolbar {
  private var _status by mutableStateOf(TextToolbarStatus.Hidden)
  override val status: TextToolbarStatus
    get() = _status

  var menuRect by mutableStateOf(Rect.Zero)
    private set
  var onCopy by mutableStateOf<(() -> Unit)?>(null)
    private set
  var onPaste by mutableStateOf<(() -> Unit)?>(null)
    private set
  var onCut by mutableStateOf<(() -> Unit)?>(null)
    private set
  var onSelectAll by mutableStateOf<(() -> Unit)?>(null)
    private set

  override fun showMenu(
    rect: Rect,
    onCopyRequested: (() -> Unit)?,
    onPasteRequested: (() -> Unit)?,
    onCutRequested: (() -> Unit)?,
    onSelectAllRequested: (() -> Unit)?,
  ) {
    menuRect = rect
    onCopy = onCopyRequested
    onPaste = onPasteRequested
    onCut = onCutRequested
    onSelectAll = onSelectAllRequested
    _status =
      if (
        onCopyRequested != null ||
          onPasteRequested != null ||
          onCutRequested != null ||
          onSelectAllRequested != null
      ) {
        TextToolbarStatus.Shown
      } else {
        TextToolbarStatus.Hidden
      }
  }

  override fun hide() {
    _status = TextToolbarStatus.Hidden
    onCopy = null
    onPaste = null
    onCut = null
    onSelectAll = null
  }
}

@Composable
private fun SafeGlassTextToolbarBar(
  toolbar: SafeGlassTextToolbar,
  glass: GlassDesignTokens,
  modifier: Modifier = Modifier,
) {
  val pillShape = RoundedCornerShape(999.dp)
  Row(
    modifier =
      modifier
        .zIndex(100f)
        .statusBarsPadding()
        .padding(top = 8.dp, start = 16.dp, end = 16.dp)
        .clip(pillShape)
        .background(
          color = if (glass.isDark) Color(0xF210192B) else Color(0xF5FFFFFF),
          shape = pillShape,
        )
        .border(
          BorderStroke(1.dp, glass.accentCyan.copy(alpha = 0.55f)),
          shape = pillShape,
        )
        .padding(horizontal = 8.dp, vertical = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    toolbar.onCut?.let { cutAction ->
      TextToolbarActionChip(
        label = "Cut",
        glass = glass,
        onClick = {
          cutAction()
          toolbar.hide()
        },
      )
    }
    toolbar.onCopy?.let { copyAction ->
      TextToolbarActionChip(
        label = "Copy",
        glass = glass,
        onClick = {
          copyAction()
          toolbar.hide()
        },
      )
    }
    toolbar.onPaste?.let { pasteAction ->
      TextToolbarActionChip(
        label = "Paste",
        glass = glass,
        onClick = {
          pasteAction()
          toolbar.hide()
        },
      )
    }
    toolbar.onSelectAll?.let { selectAllAction ->
      TextToolbarActionChip(
        label = "Select All",
        glass = glass,
        onClick = {
          selectAllAction()
          toolbar.hide()
        },
      )
    }
    TextToolbarActionChip(
      label = "✕",
      glass = glass,
      isDismiss = true,
      onClick = { toolbar.hide() },
    )
  }
}

@Composable
private fun TextToolbarActionChip(
  label: String,
  glass: GlassDesignTokens,
  isDismiss: Boolean = false,
  onClick: () -> Unit,
) {
  val chipShape = RoundedCornerShape(999.dp)
  Box(
    modifier =
      Modifier.clip(chipShape)
        .clickable(onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 6.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.SemiBold,
      color = if (isDismiss) glass.textSecondary else glass.accentCyan,
    )
  }
}

/**
 * Convenience accessor for the active GlassDesignTokens and motion preferences.
 */
object AppThemeTokens {
  val glass: GlassDesignTokens
    @Composable @ReadOnlyComposable get() = LocalGlassTokens.current

  val reducedMotion: Boolean
    @Composable @ReadOnlyComposable get() = LocalReducedMotion.current
}

/**
 * Application theme supporting Dark (default for the AI interface), Light, and System modes,
 * plus reduced-motion and reduced-transparency accessibility fallbacks.
 * Preserves backward compatibility with existing callers of MyApplicationTheme.
 */
@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  themeMode: AppThemeMode? = null,
  reducedMotion: Boolean = false,
  reduceTransparency: Boolean = false,
  content: @Composable () -> Unit,
) {
  val systemDark = isSystemInDarkTheme()
  val resolvedDarkTheme =
    when (themeMode) {
      AppThemeMode.DARK -> true
      AppThemeMode.LIGHT -> false
      AppThemeMode.SYSTEM -> systemDark
      null -> darkTheme
    }

  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (resolvedDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      resolvedDarkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  val glassTokens =
    remember(resolvedDarkTheme, reduceTransparency) {
      if (resolvedDarkTheme) {
        darkGlassTokens(reduceTransparency = reduceTransparency)
      } else {
        lightGlassTokens(reduceTransparency = reduceTransparency)
      }
    }

  val safeTextToolbar = remember { SafeGlassTextToolbar() }

  CompositionLocalProvider(
    LocalGlassTokens provides glassTokens,
    LocalReducedMotion provides reducedMotion,
    LocalTextToolbar provides safeTextToolbar,
  ) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      shapes = AppShapes,
    ) {
      Box {
        content()
        if (safeTextToolbar.status == TextToolbarStatus.Shown) {
          SafeGlassTextToolbarBar(
            toolbar = safeTextToolbar,
            glass = glassTokens,
            modifier = Modifier.align(Alignment.TopCenter),
          )
        }
      }
    }
  }
}

