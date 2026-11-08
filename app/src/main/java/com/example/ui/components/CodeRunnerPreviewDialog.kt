package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.AppBorder
import com.example.ui.theme.AppRadius
import com.example.ui.theme.AppSpacing
import com.example.ui.theme.AppThemeTokens
import kotlinx.coroutines.delay

enum class CodeRunnerTab {
  PREVIEW,
  CONSOLE,
  SOURCE,
}

data class ConsoleLogEntry(
  val level: String,
  val message: String,
  val timestamp: Long = System.currentTimeMillis(),
)

/**
 * In-App Code Runner and Live Web/HTML Sandbox Preview.
 *
 * Runs and renders HTML/CSS/JavaScript, SVG vector graphics, and Web UI components
 * in a real interactive Android WebView sandbox with live console logs capture,
 * refresh/re-run capability, and syntax source view.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CodeRunnerPreviewDialog(
  initialCode: String,
  language: String,
  onDismiss: () -> Unit,
  onCopyCode: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val glass = AppThemeTokens.glass
  val context = LocalContext.current
  var activeTab by remember { mutableStateOf(CodeRunnerTab.PREVIEW) }
  var isFullscreen by remember { mutableStateOf(false) }
  var editedCode by remember { mutableStateOf(initialCode) }
  var reloadTrigger by remember { mutableStateOf(0) }
  var isWebViewLoading by remember { mutableStateOf(true) }
  var copiedFeedback by remember { mutableStateOf(false) }

  val consoleLogs = remember { mutableStateListOf<ConsoleLogEntry>() }

  LaunchedEffect(copiedFeedback) {
    if (copiedFeedback) {
      delay(2000L)
      copiedFeedback = false
    }
  }

  // Generate clean runnable HTML payload
  val htmlPayload =
    remember(editedCode, language, reloadTrigger) {
      buildRunnableHtml(
        rawCode = editedCode,
        lang = language,
        isDark = glass.isDark,
      )
    }

  Dialog(
    onDismissRequest = onDismiss,
    properties =
      DialogProperties(
        usePlatformDefaultWidth = false,
        dismissOnBackPress = true,
      ),
  ) {
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(Color.Black.copy(alpha = 0.85f))
          .padding(if (isFullscreen) 0.dp else 12.dp)
          .testTag("code_runner_dialog"),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        modifier =
          Modifier.fillMaxWidth(if (isFullscreen) 1.0f else 0.98f)
            .fillMaxHeight(if (isFullscreen) 1.0f else 0.92f)
            .clip(RoundedCornerShape(if (isFullscreen) 0.dp else 18.dp))
            .background(if (glass.isDark) Color(0xFF0D1326) else Color(0xFFF8FAFC))
            .border(
              AppBorder.hairline,
              if (glass.isDark) glass.accentCyan.copy(alpha = 0.45f) else glass.borderSpecular,
              RoundedCornerShape(if (isFullscreen) 0.dp else 18.dp),
            ),
      ) {
        // -------------------------------------------------------------
        // Top Runner Navigation Bar
        // -------------------------------------------------------------
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .background(if (glass.isDark) Color(0xFF131A33) else Color(0xFFE2E8F0))
              .padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Box(
              modifier =
                Modifier.size(28.dp)
                  .clip(CircleShape)
                  .background(glass.accentCyan.copy(alpha = 0.20f)),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Outlined.PlayArrow,
                contentDescription = null,
                tint = glass.accentCyan,
                modifier = Modifier.size(16.dp),
              )
            }

            Column {
              Text(
                text = "Live Code Runner",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = glass.textPrimary,
              )
              Text(
                text = "${language.uppercase()} Sandbox • Interactive",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = glass.accentCyan,
              )
            }
          }

          // Top Right Controls (Copy, Fullscreen, Close)
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            // Copy Code Button
            Box(
              modifier =
                Modifier.size(30.dp)
                  .clip(CircleShape)
                  .background(glass.surfaceStandard.copy(alpha = 0.7f))
                  .clickable {
                    onCopyCode(editedCode)
                    copiedFeedback = true
                  }
                  .semantics {
                    role = Role.Button
                    contentDescription = "Copy source code"
                  }
                  .testTag("code_runner_copy_button"),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = if (copiedFeedback) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                contentDescription = null,
                tint = if (copiedFeedback) glass.statusSuccess else glass.textPrimary,
                modifier = Modifier.size(14.dp),
              )
            }

            // Reload / Re-Run Button
            Box(
              modifier =
                Modifier.size(30.dp)
                  .clip(CircleShape)
                  .background(glass.surfaceStandard.copy(alpha = 0.7f))
                  .clickable {
                    consoleLogs.clear()
                    reloadTrigger++
                  }
                  .semantics {
                    role = Role.Button
                    contentDescription = "Re-run code"
                  }
                  .testTag("code_runner_reload_button"),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = null,
                tint = glass.accentCyan,
                modifier = Modifier.size(15.dp),
              )
            }

            // Fullscreen Toggle Button
            Box(
              modifier =
                Modifier.size(30.dp)
                  .clip(CircleShape)
                  .background(glass.surfaceStandard.copy(alpha = 0.7f))
                  .clickable { isFullscreen = !isFullscreen }
                  .semantics {
                    role = Role.Button
                    contentDescription = if (isFullscreen) "Exit fullscreen" else "Fullscreen preview"
                  }
                  .testTag("code_runner_fullscreen_button"),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = if (isFullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                contentDescription = null,
                tint = glass.textPrimary,
                modifier = Modifier.size(16.dp),
              )
            }

            // Close Button
            Box(
              modifier =
                Modifier.size(30.dp)
                  .clip(CircleShape)
                  .background(glass.surfaceStandard.copy(alpha = 0.7f))
                  .clickable(role = Role.Button, onClick = onDismiss)
                  .semantics {
                    role = Role.Button
                    contentDescription = "Close preview"
                  }
                  .testTag("code_runner_close_button"),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = null,
                tint = glass.textPrimary,
                modifier = Modifier.size(15.dp),
              )
            }
          }
        }

        // -------------------------------------------------------------
        // Tab Selector Row (Preview | Console Logs | Source Code)
        // -------------------------------------------------------------
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .background(if (glass.isDark) Color(0xFF0F1528) else Color(0xFFECEFF1))
              .padding(horizontal = 10.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          // Tab 1: Preview
          RunnerTabChip(
            title = "Preview",
            icon = Icons.Outlined.Visibility,
            isSelected = activeTab == CodeRunnerTab.PREVIEW,
            onClick = { activeTab = CodeRunnerTab.PREVIEW },
            testTag = "code_runner_tab_preview",
          )

          // Tab 2: Console Logs
          val logCountText = if (consoleLogs.isNotEmpty()) " (${consoleLogs.size})" else ""
          RunnerTabChip(
            title = "Console$logCountText",
            icon = Icons.Outlined.Terminal,
            isSelected = activeTab == CodeRunnerTab.CONSOLE,
            onClick = { activeTab = CodeRunnerTab.CONSOLE },
            testTag = "code_runner_tab_console",
          )

          // Tab 3: Source Code
          RunnerTabChip(
            title = "Source",
            icon = Icons.Outlined.Code,
            isSelected = activeTab == CodeRunnerTab.SOURCE,
            onClick = { activeTab = CodeRunnerTab.SOURCE },
            testTag = "code_runner_tab_source",
          )
        }

        // -------------------------------------------------------------
        // Tab Content Area
        // -------------------------------------------------------------
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .weight(1f)
              .background(if (glass.isDark) Color(0xFF070B18) else Color.White),
        ) {
          when (activeTab) {
            CodeRunnerTab.PREVIEW -> {
              Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                  factory = { ctx ->
                    WebView(ctx).apply {
                      settings.javaScriptEnabled = true
                      settings.domStorageEnabled = true
                      settings.loadWithOverviewMode = true
                      settings.useWideViewPort = true
                      settings.allowContentAccess = true

                      webChromeClient =
                        object : WebChromeClient() {
                          override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            if (consoleMessage != null) {
                              consoleLogs.add(
                                ConsoleLogEntry(
                                  level = consoleMessage.messageLevel().name,
                                  message = consoleMessage.message().orEmpty(),
                                )
                              )
                            }
                            return true
                          }
                        }

                      webViewClient =
                        object : WebViewClient() {
                          override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            isWebViewLoading = true
                          }

                          override fun onPageFinished(view: WebView?, url: String?) {
                            isWebViewLoading = false
                          }

                          override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                          ): Boolean {
                            return false // Keep inside sandbox
                          }
                        }

                      loadDataWithBaseURL("https://astryn.local/", htmlPayload, "text/html", "UTF-8", null)
                    }
                  },
                  update = { webView ->
                    webView.loadDataWithBaseURL("https://astryn.local/", htmlPayload, "text/html", "UTF-8", null)
                  },
                  modifier = Modifier.fillMaxSize().testTag("code_runner_webview"),
                )

                if (isWebViewLoading) {
                  Box(
                    modifier =
                      Modifier.align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(AppRadius.shapePill)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                      CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = glass.accentCyan,
                      )
                      Text(
                        text = "Rendering…",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = Color.White,
                      )
                    }
                  }
                }
              }
            }

            CodeRunnerTab.CONSOLE -> {
              Column(
                modifier =
                  Modifier.fillMaxSize()
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag("code_runner_console_list"),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                if (consoleLogs.isEmpty()) {
                  Box(
                    modifier = Modifier.fillMaxSize().padding(top = 40.dp),
                    contentAlignment = Alignment.Center,
                  ) {
                    Text(
                      text = "No console logs yet. Any console.log() or JS errors will appear here.",
                      style = MaterialTheme.typography.bodySmall,
                      color = glass.textSecondary,
                    )
                  }
                } else {
                  consoleLogs.forEachIndexed { idx, log ->
                    Row(
                      modifier =
                        Modifier.fillMaxWidth()
                          .clip(RoundedCornerShape(6.dp))
                          .background(
                            when (log.level) {
                              "ERROR" -> Color(0x33EF4444)
                              "WARNING" -> Color(0x33F59E0B)
                              else -> Color.White.copy(alpha = 0.05f)
                            }
                          )
                          .padding(horizontal = 8.dp, vertical = 4.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                      Text(
                        text = "[$idx]",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = glass.accentCyan,
                        fontFamily = FontFamily.Monospace,
                      )
                      Text(
                        text = log.message,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color =
                          when (log.level) {
                            "ERROR" -> Color(0xFFFCA5A5)
                            "WARNING" -> Color(0xFFFDE68A)
                            else -> glass.textPrimary
                          },
                        fontFamily = FontFamily.Monospace,
                      )
                    }
                  }
                }
              }
            }

            CodeRunnerTab.SOURCE -> {
              Column(
                modifier =
                  Modifier.fillMaxSize()
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .testTag("code_runner_source_view"),
              ) {
                Text(
                  text = editedCode,
                  style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 18.sp),
                  color = if (glass.isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B),
                  fontFamily = FontFamily.Monospace,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun RunnerTabChip(
  title: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  isSelected: Boolean,
  onClick: () -> Unit,
  testTag: String,
) {
  val glass = AppThemeTokens.glass
  Row(
    modifier =
      Modifier.clip(RoundedCornerShape(8.dp))
        .background(
          if (isSelected) glass.accentCyan.copy(alpha = 0.20f)
          else Color.Transparent
        )
        .border(
          width = if (isSelected) 1.dp else 0.dp,
          color = if (isSelected) glass.accentCyan else Color.Transparent,
          shape = RoundedCornerShape(8.dp),
        )
        .clickable(role = Role.Tab, onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 5.dp)
        .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = if (isSelected) glass.accentCyan else glass.textSecondary,
      modifier = Modifier.size(13.dp),
    )
    Text(
      text = title,
      style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
      color = if (isSelected) glass.accentCyan else glass.textSecondary,
    )
  }
}

/**
 * Builds a secure, sandboxed, self-contained HTML page for the WebView runner.
 */
fun buildRunnableHtml(
  rawCode: String,
  lang: String,
  isDark: Boolean,
): String {
  val lowerLang = lang.lowercase().trim()
  val bgCol = if (isDark) "#070b18" else "#ffffff"
  val textCol = if (isDark) "#e2e8f0" else "#0f172a"

  return when {
    lowerLang in listOf("html", "htm", "web") -> {
      if (rawCode.contains("<html", ignoreCase = true)) {
        rawCode
      } else {
        """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
          <style>
            * { box-sizing: border-box; }
            body {
              margin: 0;
              padding: 16px;
              background-color: $bgCol;
              color: $textCol;
              font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
            }
          </style>
        </head>
        <body>
          $rawCode
        </body>
        </html>
        """.trimIndent()
      }
    }

    lowerLang == "svg" -> {
      """
      <!DOCTYPE html>
      <html>
      <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>
          body {
            margin: 0;
            padding: 24px;
            display: flex;
            align-items: center;
            justify-content: center;
            background-color: $bgCol;
          }
          svg { max-width: 100%; height: auto; }
        </style>
      </head>
      <body>
        $rawCode
      </body>
      </html>
      """.trimIndent()
    }

    lowerLang in listOf("javascript", "js") -> {
      """
      <!DOCTYPE html>
      <html>
      <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>
          body {
            margin: 0;
            padding: 16px;
            background-color: $bgCol;
            color: $textCol;
            font-family: monospace;
          }
          #output {
            white-space: pre-wrap;
            line-height: 1.5;
            background: ${if (isDark) "#111827" else "#f1f5f9"};
            padding: 14px;
            border-radius: 8px;
            border: 1px solid ${if (isDark) "#374151" else "#cbd5e1"};
          }
        </style>
      </head>
      <body>
        <h3 style="margin-top:0; color:#00F2FE;">JavaScript Output</h3>
        <div id="output">Running...</div>
        <script>
          const out = document.getElementById('output');
          out.textContent = '';
          const originalLog = console.log;
          console.log = function(...args) {
            originalLog.apply(console, args);
            out.textContent += args.map(a => typeof a === 'object' ? JSON.stringify(a, null, 2) : String(a)).join(' ') + '\n';
          };
          try {
            $rawCode
          } catch(e) {
            out.textContent += '\nError: ' + e.message;
            console.error(e);
          }
        </script>
      </body>
      </html>
      """.trimIndent()
    }

    else -> {
      // General code output viewer for Python, Kotlin, JSON, etc.
      val escaped =
        rawCode
          .replace("&", "&amp;")
          .replace("<", "&lt;")
          .replace(">", "&gt;")

      """
      <!DOCTYPE html>
      <html>
      <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>
          body {
            margin: 0;
            padding: 16px;
            background-color: $bgCol;
            color: $textCol;
            font-family: monospace;
          }
          .box {
            background: ${if (isDark) "#0f172a" else "#f8fafc"};
            border: 1px solid ${if (isDark) "#334155" else "#cbd5e1"};
            padding: 16px;
            border-radius: 10px;
            white-space: pre-wrap;
            line-height: 1.5;
          }
        </style>
      </head>
      <body>
        <h4 style="margin-top:0; color:#00F2FE;">$lang Output Preview</h4>
        <div class="box">$escaped</div>
      </body>
      </html>
      """.trimIndent()
    }
  }
}
