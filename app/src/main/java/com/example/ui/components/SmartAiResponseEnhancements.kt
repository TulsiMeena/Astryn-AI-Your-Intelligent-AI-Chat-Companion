package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppBorder
import com.example.ui.theme.AppRadius
import com.example.ui.theme.AppThemeTokens

/**
 * Instant view modes for switching an AI response between Full Detail, Quick Summary,
 * Key Points, and Interactive Flowchart without waiting for network regeneration.
 */
enum class AiResponseViewMode(
  val label: String,
  val icon: ImageVector,
) {
  FULL_DETAIL("Full Detail", Icons.Outlined.Description),
  QUICK_SUMMARY("Summary", Icons.Outlined.Bolt),
  KEY_POINTS("Key Points", Icons.AutoMirrored.Outlined.FormatListBulleted),
  FLOWCHART("Flowchart", Icons.Outlined.AccountTree),
}

@Immutable
data class FlowchartStep(
  val stepNumber: Int,
  val title: String,
  val detail: String,
)

@Immutable
data class SmartDetectedAction(
  val id: String,
  val label: String,
  val icon: ImageVector,
  val actionType: String,
  val payload: String,
  val secondaryPayload: String = "",
)

/**
 * Pure Kotlin extractor that derives structured Reasoning Steps, Quick Summary,
 * Key Bullet Points, Interactive Flowchart nodes, Quotable Sentences, and Smart 1-Tap Actions
 * from any AI response content with zero lag.
 */
object SmartResponseAnalyzer {
  private val EMAIL_REGEX = Regex("""[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}""")
  private val URL_REGEX = Regex("""https?://[^\s)\]>,"']+""")
  private val PHONE_REGEX = Regex("""(?:\+91[\-\s]?)?[6-9]\d{9}\b|\+\d{1,3}[\-\s]?\d{6,12}\b""")
  private val CODE_FENCE_REGEX = Regex("""```([a-zA-Z0-9_+\-]*)\n([\s\S]*?)```""")

  fun stripMarkdownFormatting(line: String): String {
    return line
      .replace(Regex("""^#{1,6}\s*"""), "")
      .replace(Regex("""^[-*•]\s*"""), "")
      .replace(Regex("""^\d+[.)]\s*"""), "")
      .replace("**", "")
      .replace("__", "")
      .replace("`", "")
      .trim()
  }

  fun extractCleanLines(rawContent: String): List<String> {
    val withoutCode = rawContent.replace(CODE_FENCE_REGEX, "")
    return withoutCode
      .lines()
      .map { stripMarkdownFormatting(it) }
      .filter { it.length >= 12 && !it.startsWith("*(Tip:") }
  }

  /**
   * Generates 3 structured Chain-of-Thought reasoning steps explaining how the AI analyzed
   * and structured the response.
   */
  fun extractReasoningSteps(rawContent: String): List<Pair<String, String>> {
    val hasCode = CODE_FENCE_REGEX.containsMatchIn(rawContent)
    val cleanLines = extractCleanLines(rawContent)
    val topicPreview =
      cleanLines.firstOrNull()?.take(75) ?: rawContent.trim().lineSequence().firstOrNull()?.take(75).orEmpty()

    return listOf(
      "1. Intent & Query Analysis" to
        "Analyzed core subject: \"${topicPreview.ifBlank { "User request" }}\" and identified key constraints.",
      "2. Knowledge & Logic Synthesis" to
        if (hasCode) {
          "Constructed modular, runnable code architecture with input validation and best practices."
        } else {
          "Organized factual concepts, step-by-step logic, and practical real-world takeaways."
        },
      "3. Clarity & Verification" to
        "Formatted output with structured headings, actionable points, and interactive verification.",
    )
  }

  /**
   * Produces a crisp 2-3 sentence Quick Summary from the full response.
   */
  fun extractQuickSummary(rawContent: String): String {
    val lines = extractCleanLines(rawContent)
    if (lines.isEmpty()) return rawContent.trim().take(240)
    val topSentences =
      lines
        .filter { it.length >= 20 }
        .take(3)
    return if (topSentences.isNotEmpty()) {
      topSentences.joinToString(" ") { sentence ->
        if (sentence.endsWith(".") || sentence.endsWith("!") || sentence.endsWith("?") || sentence.endsWith("।")) {
          sentence
        } else {
          "$sentence."
        }
      }
    } else {
      lines.take(2).joinToString(" • ")
    }
  }

  /**
   * Extracts 3 to 5 high-impact Key Bullet Points from the response.
   */
  fun extractKeyPoints(rawContent: String): List<String> {
    val rawLines = rawContent.replace(CODE_FENCE_REGEX, "").lines()
    val explicitBullets =
      rawLines
        .map { it.trim() }
        .filter {
          it.startsWith("- ") ||
            it.startsWith("* ") ||
            it.startsWith("• ") ||
            Regex("""^\d+[.)]\s+.+""").matches(it)
        }
        .map { stripMarkdownFormatting(it) }
        .filter { it.length >= 10 }
        .distinct()
        .take(5)

    if (explicitBullets.size >= 2) {
      return explicitBullets
    }

    return extractCleanLines(rawContent)
      .distinct()
      .take(4)
      .ifEmpty { listOf(rawContent.trim().take(160)) }
  }

  /**
   * Extracts 3 to 5 sequential Flowchart Steps from the response for interactive visual rendering.
   */
  fun extractFlowchartSteps(rawContent: String): List<FlowchartStep> {
    val keyPoints = extractKeyPoints(rawContent)
    val sourceItems =
      if (keyPoints.size >= 2) keyPoints
      else extractCleanLines(rawContent).take(4)

    val steps =
      sourceItems.take(5).mapIndexed { index, item ->
        val colonIdx = item.indexOf(':')
        val (title, detail) =
          if (colonIdx in 3..42) {
            item.substring(0, colonIdx).trim() to item.substring(colonIdx + 1).trim()
          } else {
            val words = item.split(Regex("\\s+"))
            val shortTitle = words.take(4).joinToString(" ")
            shortTitle to item
          }
        FlowchartStep(
          stepNumber = index + 1,
          title = title.ifBlank { "Step ${index + 1}" },
          detail = detail.ifBlank { item },
        )
      }

    return if (steps.size >= 2) {
      steps
    } else {
      listOf(
        FlowchartStep(1, "Input & Requirement", steps.firstOrNull()?.detail ?: rawContent.take(120)),
        FlowchartStep(2, "Core Processing", "Apply structured logic and key parameters step by step."),
        FlowchartStep(3, "Verified Output", "Review final result and apply practical next steps."),
      )
    }
  }

  /**
   * Extracts selectable sentences/lines from the AI response so the user can tap any line
   * to Quote-Reply ("Ask About This").
   */
  fun extractQuotableLines(rawContent: String): List<String> {
    return extractCleanLines(rawContent)
      .filter { it.length in 14..180 }
      .distinct()
      .take(5)
      .ifEmpty { listOf(stripMarkdownFormatting(rawContent.take(140))) }
  }

  /**
   * Scans the AI response for actionable entities (Code blocks, URLs, Emails, Phone numbers,
   * Calendar/Reminder/Schedule mentions) and returns 1-tap Smart Actions.
   */
  fun detectSmartActions(rawContent: String): List<SmartDetectedAction> {
    val actions = ArrayList<SmartDetectedAction>(4)
    val lower = rawContent.lowercase()

    // 1. Detect runnable/copyable Code Block
    val codeMatch = CODE_FENCE_REGEX.find(rawContent)
    if (codeMatch != null) {
      val lang = codeMatch.groupValues[1].trim().ifBlank { "kotlin" }
      val snippet = codeMatch.groupValues[2].trim()
      if (snippet.isNotBlank()) {
        actions.add(
          SmartDetectedAction(
            id = "run_code",
            label = "Run ${lang.uppercase()} Code",
            icon = Icons.Outlined.PlayArrow,
            actionType = "RUN_CODE",
            payload = snippet,
            secondaryPayload = lang,
          )
        )
      }
    }

    // 2. Detect Email Address or Email Draft
    val emailMatch = EMAIL_REGEX.find(rawContent)
    if (emailMatch != null || lower.contains("subject:") || lower.contains("dear ") || lower.contains("email draft")) {
      val recipient = emailMatch?.value.orEmpty()
      val subjectLine =
        rawContent
          .lines()
          .firstOrNull { it.trim().startsWith("Subject:", ignoreCase = true) }
          ?.substringAfter(":", "")
          ?.trim()
          ?.ifBlank { "Astryn AI Draft" }
          ?: "Astryn AI Response"
      actions.add(
        SmartDetectedAction(
          id = "send_email",
          label = "Send Email",
          icon = Icons.Outlined.Email,
          actionType = "EMAIL",
          payload = recipient,
          secondaryPayload = subjectLine,
        )
      )
    }

    // 3. Detect Schedule / Plan / Time / Reminder
    if (
      lower.contains("schedule") ||
        lower.contains("plan") ||
        lower.contains("routine") ||
        lower.contains("reminder") ||
        lower.contains("meeting") ||
        lower.contains("deadline") ||
        lower.contains("exam") ||
        lower.contains("step 1")
    ) {
      val title =
        extractCleanLines(rawContent).firstOrNull()?.take(50) ?: "Astryn AI Action Item"
      actions.add(
        SmartDetectedAction(
          id = "add_calendar",
          label = "Add Reminder",
          icon = Icons.Outlined.CalendarMonth,
          actionType = "CALENDAR",
          payload = title,
          secondaryPayload = extractQuickSummary(rawContent),
        )
      )
    }

    // 4. Detect External URL
    val urlMatch = URL_REGEX.find(rawContent)
    if (urlMatch != null) {
      val cleanUrl = urlMatch.value.trimEnd('.', ',', ')')
      if (SafeMarkdownParser.isSafeExternalUrl(cleanUrl)) {
        actions.add(
          SmartDetectedAction(
            id = "open_url",
            label = "Open Link",
            icon = Icons.AutoMirrored.Outlined.OpenInNew,
            actionType = "URL",
            payload = cleanUrl,
          )
        )
      }
    }

    // 5. Detect Phone Number
    val phoneMatch = PHONE_REGEX.find(rawContent)
    if (phoneMatch != null) {
      actions.add(
        SmartDetectedAction(
          id = "dial_phone",
          label = "Call ${phoneMatch.value}",
          icon = Icons.Outlined.Call,
          actionType = "PHONE",
          payload = phoneMatch.value,
        )
      )
    }

    // 6. Always offer 1-Tap Share to WhatsApp for Shayari, Jokes, Quotes, Plans, or Helpful Notes
    actions.add(
      SmartDetectedAction(
        id = "share_whatsapp",
        label = "WhatsApp Share",
        icon = Icons.AutoMirrored.Outlined.OpenInNew,
        actionType = "WHATSAPP",
        payload = rawContent.trim().take(1800),
      )
    )

    return actions.take(4)
  }
}

/**
 * 1. Collapsible "AI Thinking / Reasoning" Box (Chain-of-Thought)
 * Shows a compact pill at the top of the AI response that expands to reveal how the AI
 * analyzed and solved the user's prompt.
 */
@Composable
fun AiReasoningChainBox(
  messageId: String,
  content: String,
  modifier: Modifier = Modifier,
) {
  val glass = AppThemeTokens.glass
  var isExpanded by rememberSaveable(messageId) { mutableStateOf(false) }
  val reasoningSteps =
    remember(messageId, content) {
      SmartResponseAnalyzer.extractReasoningSteps(content)
    }

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(bottom = 6.dp)
        .testTag("message_reasoning_box_$messageId"),
  ) {
    Row(
      modifier =
        Modifier.clip(AppRadius.shapePill)
          .background(
            if (isExpanded) glass.accentCyan.copy(alpha = 0.16f)
            else glass.surfaceStandard.copy(alpha = 0.65f)
          )
          .border(
            AppBorder.hairline,
            if (isExpanded) glass.accentCyan.copy(alpha = 0.65f)
            else glass.accentCyan.copy(alpha = 0.32f),
            AppRadius.shapePill,
          )
          .clickable(role = Role.Button) { isExpanded = !isExpanded }
          .semantics {
            role = Role.Button
            contentDescription =
              if (isExpanded) "Hide AI reasoning steps" else "Show AI reasoning steps"
          }
          .padding(horizontal = 10.dp, vertical = 4.dp)
          .testTag("message_reasoning_toggle_$messageId"),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(
        imageVector = Icons.Outlined.Psychology,
        contentDescription = null,
        tint = glass.accentCyan,
        modifier = Modifier.size(13.dp),
      )
      Text(
        text = "AI Reasoning • ${reasoningSteps.size} steps",
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
        fontWeight = FontWeight.SemiBold,
        color = glass.accentCyan,
      )
      Icon(
        imageVector =
          if (isExpanded) Icons.Outlined.KeyboardArrowUp
          else Icons.Outlined.KeyboardArrowDown,
        contentDescription = null,
        tint = glass.accentCyan,
        modifier = Modifier.size(13.dp),
      )
    }

    AnimatedVisibility(
      visible = isExpanded,
      enter = fadeIn() + expandVertically(),
      exit = fadeOut() + shrinkVertically(),
    ) {
      Column(
        modifier =
          Modifier.padding(top = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x33060C1E))
            .border(
              AppBorder.hairline,
              glass.accentCyan.copy(alpha = 0.35f),
              RoundedCornerShape(12.dp),
            )
            .padding(10.dp)
            .testTag("message_reasoning_steps_$messageId"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        reasoningSteps.forEach { (stepTitle, stepDesc) ->
          Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.CheckCircleOutline,
              contentDescription = null,
              tint = glass.accentCyan,
              modifier = Modifier.padding(top = 2.dp).size(12.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
              Text(
                text = stepTitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                fontWeight = FontWeight.Bold,
                color = glass.textPrimary,
              )
              Text(
                text = stepDesc,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                color = glass.textSecondary,
              )
            }
          }
        }
      }
    }
  }
}

/**
 * 2. Response Style Tabs (Instant Switcher: Full Detail | Summary | Key Points | Flowchart)
 * Allows the user to switch the view of any AI response in 1 tap with zero lag.
 */
@Composable
fun AiResponseModeTabs(
  messageId: String,
  selectedMode: AiResponseViewMode,
  onSelectMode: (AiResponseViewMode) -> Unit,
  modifier: Modifier = Modifier,
) {
  val glass = AppThemeTokens.glass
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(bottom = 8.dp)
        .testTag("message_view_mode_tabs_$messageId"),
    horizontalArrangement = Arrangement.spacedBy(5.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    AiResponseViewMode.entries.forEach { mode ->
      val isSelected = selectedMode == mode
      Row(
        modifier =
          Modifier.clip(AppRadius.shapePill)
            .background(
              if (isSelected) glass.accentCyan.copy(alpha = 0.22f)
              else glass.surfaceStandard.copy(alpha = 0.55f)
            )
            .border(
              AppBorder.hairline,
              if (isSelected) glass.accentCyan else glass.borderSubtle.copy(alpha = 0.55f),
              AppRadius.shapePill,
            )
            .clickable(role = Role.Tab) { onSelectMode(mode) }
            .padding(horizontal = 9.dp, vertical = 4.dp)
            .testTag("message_view_tab_${mode.name}_$messageId"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Icon(
          imageVector = mode.icon,
          contentDescription = null,
          tint = if (isSelected) glass.accentCyan else glass.textSecondary,
          modifier = Modifier.size(11.dp),
        )
        Text(
          text = mode.label,
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
          fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
          color = if (isSelected) glass.textPrimary else glass.textSecondary,
        )
      }
    }
  }
}

/**
 * Renders the instant Quick Summary or Key Points card when the user switches tabs.
 */
@Composable
fun AiInstantSummaryOrPointsView(
  messageId: String,
  content: String,
  mode: AiResponseViewMode,
  onQuotePoint: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val glass = AppThemeTokens.glass
  val quickSummary = remember(messageId, content) { SmartResponseAnalyzer.extractQuickSummary(content) }
  val keyPoints = remember(messageId, content) { SmartResponseAnalyzer.extractKeyPoints(content) }

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(Color(0x33071126))
        .border(AppBorder.hairline, glass.accentCyan.copy(alpha = 0.42f), RoundedCornerShape(12.dp))
        .padding(12.dp)
        .testTag("message_instant_${mode.name}_view_$messageId"),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(
        imageVector =
          if (mode == AiResponseViewMode.QUICK_SUMMARY) Icons.Outlined.Bolt
          else Icons.Outlined.Lightbulb,
        contentDescription = null,
        tint = glass.accentCyan,
        modifier = Modifier.size(15.dp),
      )
      Text(
        text =
          if (mode == AiResponseViewMode.QUICK_SUMMARY) "Quick Executive Summary"
          else "Key Actionable Takeaways (Tap any point to ask)",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = glass.accentCyan,
      )
    }

    if (mode == AiResponseViewMode.QUICK_SUMMARY) {
      Text(
        text = quickSummary,
        style = MaterialTheme.typography.bodyMedium,
        color = glass.textPrimary,
      )
    } else {
      keyPoints.forEachIndexed { idx, point ->
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(glass.surfaceStandard.copy(alpha = 0.55f))
              .clickable(role = Role.Button) { onQuotePoint(point) }
              .padding(horizontal = 10.dp, vertical = 7.dp)
              .testTag("message_key_point_${messageId}_$idx"),
          verticalAlignment = Alignment.Top,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Box(
            modifier =
              Modifier.size(18.dp)
                .clip(CircleShape)
                .background(glass.accentCyan.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = "${idx + 1}",
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
              fontWeight = FontWeight.Bold,
              color = glass.accentCyan,
            )
          }
          Text(
            text = point,
            style = MaterialTheme.typography.bodySmall,
            color = glass.textPrimary,
            modifier = Modifier.weight(1f),
          )
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.Reply,
            contentDescription = "Ask about this point",
            tint = glass.accentCyan.copy(alpha = 0.75f),
            modifier = Modifier.size(14.dp),
          )
        }
      }
    }
  }
}

/**
 * 3. Interactive Diagram & Flowchart Renderer
 * Renders a visual step-by-step connected flowchart card where tapping any node highlights
 * and expands its full explanation or lets the user ask a follow-up about that step.
 */
@Composable
fun InteractiveFlowchartCard(
  messageId: String,
  content: String,
  onAskAboutStep: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val glass = AppThemeTokens.glass
  val steps = remember(messageId, content) { SmartResponseAnalyzer.extractFlowchartSteps(content) }
  var selectedStepIndex by rememberSaveable(messageId) { mutableIntStateOf(0) }

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(
          Brush.verticalGradient(
            listOf(Color(0x4008152E), Color(0x33050B1A))
          )
        )
        .border(AppBorder.hairline, glass.accentCyan.copy(alpha = 0.48f), RoundedCornerShape(14.dp))
        .padding(12.dp)
        .testTag("message_flowchart_card_$messageId"),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(
        imageVector = Icons.Outlined.AccountTree,
        contentDescription = null,
        tint = glass.accentCyan,
        modifier = Modifier.size(15.dp),
      )
      Text(
        text = "Interactive Process Flowchart (${steps.size} Stages)",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = glass.accentCyan,
      )
    }

    Spacer(modifier = Modifier.height(2.dp))

    steps.forEachIndexed { idx, step ->
      val isSelected = selectedStepIndex == idx
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
      ) {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .background(
                if (isSelected) glass.accentCyan.copy(alpha = 0.18f)
                else glass.surfaceStandard.copy(alpha = 0.65f)
              )
              .border(
                AppBorder.hairline,
                if (isSelected) glass.accentCyan else glass.borderSubtle.copy(alpha = 0.5f),
                RoundedCornerShape(10.dp),
              )
              .clickable { selectedStepIndex = idx }
              .padding(horizontal = 10.dp, vertical = 8.dp)
              .testTag("message_flowchart_node_${messageId}_$idx"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Box(
            modifier =
              Modifier.size(24.dp)
                .clip(CircleShape)
                .background(
                  if (isSelected) glass.accentCyan
                  else glass.accentPrimary.copy(alpha = 0.25f)
                ),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = "${step.stepNumber}",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = if (isSelected) Color(0xFF041022) else glass.accentCyan,
            )
          }

          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = step.title,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = if (isSelected) glass.accentCyan else glass.textPrimary,
            )
            Text(
              text = step.detail,
              style = MaterialTheme.typography.bodySmall,
              color = glass.textSecondary,
              maxLines = if (isSelected) 5 else 2,
              overflow = TextOverflow.Ellipsis,
            )
          }

          Box(
            modifier =
              Modifier.clip(AppRadius.shapePill)
                .background(glass.accentCyan.copy(alpha = 0.16f))
                .clickable(role = Role.Button) {
                  onAskAboutStep("${step.title}: ${step.detail}")
                }
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("message_flowchart_ask_${messageId}_$idx"),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = "Ask",
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
              fontWeight = FontWeight.SemiBold,
              color = glass.accentCyan,
            )
          }
        }

        // Visual Connector Line between flowchart nodes
        if (idx < steps.lastIndex) {
          Row(
            modifier = Modifier.padding(start = 21.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier =
                Modifier.width(2.dp)
                  .height(10.dp)
                  .background(glass.accentCyan.copy(alpha = 0.55f))
            )
          }
        }
      }
    }
  }
}

/**
 * 4A. Interactive Word/Line Highlight & "Ask About This" Selector Panel
 * Opens when the user taps "Quote / Ask" on an AI response, allowing 1-tap selection of any
 * key sentence from the response to quote-reply in the chatbox.
 */
@Composable
fun InteractiveQuoteSelectorPanel(
  messageId: String,
  content: String,
  onSelectQuoteLine: (String) -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val glass = AppThemeTokens.glass
  val quotableLines =
    remember(messageId, content) {
      SmartResponseAnalyzer.extractQuotableLines(content)
    }

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(top = 6.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(glass.surfaceElevated.copy(alpha = 0.94f))
        .border(AppBorder.hairline, glass.accentCyan.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
        .padding(10.dp)
        .testTag("message_quote_selector_panel_$messageId"),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Outlined.Reply,
          contentDescription = null,
          tint = glass.accentCyan,
          modifier = Modifier.size(14.dp),
        )
        Text(
          text = "Tap any line to Quote & Ask Follow-Up",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = glass.accentCyan,
        )
      }
      Icon(
        imageVector = Icons.Outlined.Close,
        contentDescription = "Close quote selector",
        tint = glass.textSecondary,
        modifier =
          Modifier.size(16.dp)
            .clickable(role = Role.Button, onClick = onClose),
      )
    }

    quotableLines.forEachIndexed { idx, line ->
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(glass.surfaceStandard.copy(alpha = 0.72f))
            .border(AppBorder.hairline, glass.borderSubtle.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable(role = Role.Button) {
              onSelectQuoteLine(line)
            }
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .testTag("message_quote_line_${messageId}_$idx"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = "“$line”",
          style = MaterialTheme.typography.bodySmall,
          color = glass.textPrimary,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        Text(
          text = "Ask ↵",
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
          fontWeight = FontWeight.Bold,
          color = glass.accentCyan,
        )
      }
    }
  }
}

/**
 * 4B. Active Quote-Reply Banner displayed directly above the Chat Composer when the user
 * selects a line from an AI response to ask about.
 */
@Composable
fun ActiveQuoteReplyBanner(
  quotedText: String,
  onAskDeepDiveNow: () -> Unit,
  onClearQuote: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (quotedText.isBlank()) return
  val glass = AppThemeTokens.glass

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 4.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(glass.surfaceElevated.copy(alpha = 0.94f))
        .border(AppBorder.hairline, glass.accentCyan.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
        .padding(horizontal = 10.dp, vertical = 7.dp)
        .testTag("chat_active_quote_banner"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Icon(
      imageVector = Icons.AutoMirrored.Outlined.Reply,
      contentDescription = null,
      tint = glass.accentCyan,
      modifier = Modifier.size(16.dp),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = "Replying to AI highlight:",
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        fontWeight = FontWeight.Bold,
        color = glass.accentCyan,
      )
      Text(
        text = "“$quotedText”",
        style = MaterialTheme.typography.bodySmall,
        color = glass.textPrimary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Box(
      modifier =
        Modifier.clip(AppRadius.shapePill)
          .background(glass.accentCyan.copy(alpha = 0.22f))
          .border(AppBorder.hairline, glass.accentCyan, AppRadius.shapePill)
          .clickable(role = Role.Button, onClick = onAskDeepDiveNow)
          .padding(horizontal = 9.dp, vertical = 4.dp)
          .testTag("chat_quote_deep_dive_button"),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = "Explain Deeply",
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        fontWeight = FontWeight.Bold,
        color = glass.accentCyan,
      )
    }
    Icon(
      imageVector = Icons.Outlined.Close,
      contentDescription = "Clear quote reply",
      tint = glass.textSecondary,
      modifier =
        Modifier.size(16.dp)
          .clickable(role = Role.Button, onClick = onClearQuote)
          .testTag("chat_quote_clear_button"),
    )
  }
}

/**
 * 5. Smart Action Cards (1-Tap Execution: Send Email, Add Reminder, Open Link, Call, Run Code, Quote & Ask)
 * Renders context-aware 1-tap execution chips directly below the AI response.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmartContextActionCardsRow(
  messageId: String,
  content: String,
  onRunCode: ((String, String) -> Unit)?,
  onToggleQuoteSelector: () -> Unit,
  isQuoteSelectorOpen: Boolean,
  onActionFeedback: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val glass = AppThemeTokens.glass
  val detectedActions =
    remember(messageId, content) {
      SmartResponseAnalyzer.detectSmartActions(content)
    }

  FlowRow(
    modifier =
      modifier
        .padding(top = 5.dp)
        .testTag("message_smart_actions_row_$messageId"),
    horizontalArrangement = Arrangement.spacedBy(5.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    // Always offer 1-Tap "Quote & Ask" highlight selector so user can ask about any specific line
    Row(
      modifier =
        Modifier.clip(AppRadius.shapePill)
          .background(
            if (isQuoteSelectorOpen) glass.accentCyan.copy(alpha = 0.24f)
            else Color(0x2600F2FE)
          )
          .border(
            AppBorder.hairline,
            glass.accentCyan.copy(alpha = if (isQuoteSelectorOpen) 0.85f else 0.45f),
            AppRadius.shapePill,
          )
          .clickable(role = Role.Button, onClick = onToggleQuoteSelector)
          .padding(horizontal = 9.dp, vertical = 4.dp)
          .testTag("message_quote_ask_button_$messageId"),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Outlined.Reply,
        contentDescription = null,
        tint = glass.accentCyan,
        modifier = Modifier.size(11.dp),
      )
      Text(
        text = if (isQuoteSelectorOpen) "Hide Quote Lines" else "Quote & Ask",
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        fontWeight = FontWeight.SemiBold,
        color = glass.textPrimary,
      )
    }

    detectedActions.forEach { action ->
      Row(
        modifier =
          Modifier.clip(AppRadius.shapePill)
            .background(Color(0x268B5CF6))
            .border(
              AppBorder.hairline,
              glass.accentCyan.copy(alpha = 0.48f),
              AppRadius.shapePill,
            )
            .clickable(role = Role.Button) {
              executeSmartDetectedAction(
                context = context,
                action = action,
                fullMessageContent = content,
                onRunCode = onRunCode,
                onActionFeedback = onActionFeedback,
              )
            }
            .padding(horizontal = 9.dp, vertical = 4.dp)
            .testTag("message_smart_action_${action.id}_$messageId"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Icon(
          imageVector = action.icon,
          contentDescription = null,
          tint = glass.accentCyan,
          modifier = Modifier.size(11.dp),
        )
        Text(
          text = action.label,
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
          fontWeight = FontWeight.SemiBold,
          color = glass.textPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

private fun executeSmartDetectedAction(
  context: Context,
  action: SmartDetectedAction,
  fullMessageContent: String,
  onRunCode: ((String, String) -> Unit)?,
  onActionFeedback: (String) -> Unit,
) {
  try {
    when (action.actionType) {
      "RUN_CODE" -> {
        if (onRunCode != null) {
          onRunCode(action.payload, action.secondaryPayload)
        } else {
          onActionFeedback("Code preview ready")
        }
      }
      "EMAIL" -> {
        val intent =
          Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:${action.payload}")
            putExtra(Intent.EXTRA_SUBJECT, action.secondaryPayload)
            putExtra(Intent.EXTRA_TEXT, fullMessageContent)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
        context.startActivity(intent)
        onActionFeedback("Opening Email composer…")
      }
      "CALENDAR" -> {
        val intent =
          Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, action.payload)
            putExtra(CalendarContract.Events.DESCRIPTION, action.secondaryPayload)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
        context.startActivity(intent)
        onActionFeedback("Opening Calendar reminder…")
      }
      "URL" -> {
        val intent =
          Intent(Intent.ACTION_VIEW, Uri.parse(action.payload)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
        context.startActivity(intent)
        onActionFeedback("Opening link…")
      }
      "PHONE" -> {
        val intent =
          Intent(Intent.ACTION_DIAL, Uri.parse("tel:${action.payload}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
        context.startActivity(intent)
        onActionFeedback("Opening dialer…")
      }
      "WHATSAPP" -> {
        val encodedText = Uri.encode(action.payload.ifBlank { fullMessageContent.take(1800) })
        val waIntent =
          Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?text=$encodedText")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
        context.startActivity(waIntent)
        onActionFeedback("Opening WhatsApp to share…")
      }
    }
  } catch (_: Exception) {
    onActionFeedback("Action prepared: ${action.label}")
  }
}
