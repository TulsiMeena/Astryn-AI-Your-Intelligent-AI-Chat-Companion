package com.example.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.WrapText
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppBorder
import com.example.ui.theme.AppRadius
import com.example.ui.theme.AppSpacing
import com.example.ui.theme.AppThemeTokens
import com.example.ui.theme.CodeTypography
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicLong

/**
 * Diagnostic metrics for bounded Markdown & syntax-highlighting LRU caches.
 */
data class MarkdownCacheMetrics(
  val blockCacheSize: Int,
  val codeCacheSize: Int,
  val inlineCacheSize: Int,
  val blockHits: Long,
  val blockMisses: Long,
  val codeHits: Long,
  val codeMisses: Long,
  val inlineHits: Long,
  val inlineMisses: Long,
)

/**
 * Structured representation of parsed Markdown blocks for safe native Compose rendering.
 * Never interprets or executes raw HTML, scripts, or event handlers.
 */
@Immutable
sealed class MarkdownBlock {
  @Immutable
  data class Heading(val level: Int, val text: String) : MarkdownBlock()

  @Immutable
  data class Paragraph(val text: String) : MarkdownBlock()

  @Immutable
  data class BulletList(val items: List<String>) : MarkdownBlock()

  @Immutable
  data class OrderedList(val items: List<Pair<Int, String>>) : MarkdownBlock()

  @Immutable
  data class Blockquote(val text: String) : MarkdownBlock()

  @Immutable
  data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock()

  @Immutable
  data class CodeBlock(
    val language: String,
    val code: String,
    val blockIndex: Int,
  ) : MarkdownBlock()

  @Immutable
  object HorizontalRule : MarkdownBlock()
}

/**
 * Pure Kotlin Markdown parser, security sanitizer, and bounded LRU cache manager.
 */
object SafeMarkdownParser {
  const val MAX_BLOCK_CACHE_SIZE = 128
  const val MAX_BLOCK_CACHE_ENTRIES = 128
  const val MAX_CODE_CACHE_ENTRIES = 64
  const val MAX_INLINE_CACHE_ENTRIES = 256
  private const val MAX_CACHEABLE_MARKDOWN_LENGTH = 32_000

  private val HEADING_REGEX = Regex("""^(#{1,6})\s+(.+)$""")
  private val BULLET_REGEX = Regex("""^\s*[-*+]\s+(.+)$""")
  private val ORDERED_REGEX = Regex("""^\s*(\d+)\.\s+(.+)$""")
  private val HR_REGEX = Regex("""^\s*([-*_])\1\1+\s*$""")
  private val TABLE_DIVIDER_REGEX = Regex("""^\s*\|?(\s*:?-{2,}:?\s*\|)+\s*:?-{2,}:?\s*\|?\s*$""")

  private val blockCacheHits = AtomicLong(0L)
  private val blockCacheMisses = AtomicLong(0L)
  private val codeCacheHits = AtomicLong(0L)
  private val codeCacheMisses = AtomicLong(0L)
  private val inlineCacheHits = AtomicLong(0L)
  private val inlineCacheMisses = AtomicLong(0L)

  private val blockLruCache =
    object : LinkedHashMap<String, List<MarkdownBlock>>(64, 0.75f, true) {
      override fun removeEldestEntry(
        eldest: MutableMap.MutableEntry<String, List<MarkdownBlock>>?,
      ): Boolean = size > MAX_BLOCK_CACHE_ENTRIES
    }

  internal val codeLruCache =
    object : LinkedHashMap<String, AnnotatedString>(32, 0.75f, true) {
      override fun removeEldestEntry(
        eldest: MutableMap.MutableEntry<String, AnnotatedString>?,
      ): Boolean = size > MAX_CODE_CACHE_ENTRIES
    }

  internal val inlineLruCache =
    object : LinkedHashMap<String, AnnotatedString>(128, 0.75f, true) {
      override fun removeEldestEntry(
        eldest: MutableMap.MutableEntry<String, AnnotatedString>?,
      ): Boolean = size > MAX_INLINE_CACHE_ENTRIES
    }

  internal fun getCachedCode(key: String): AnnotatedString? {
    if (key.length > MAX_CACHEABLE_MARKDOWN_LENGTH) return null
    synchronized(codeLruCache) {
      codeLruCache[key]?.let { cached ->
        codeCacheHits.incrementAndGet()
        return cached
      }
    }
    codeCacheMisses.incrementAndGet()
    return null
  }

  internal fun putCachedCode(key: String, value: AnnotatedString) {
    if (key.length <= MAX_CACHEABLE_MARKDOWN_LENGTH) {
      synchronized(codeLruCache) {
        codeLruCache[key] = value
      }
    }
  }

  internal fun getCachedInline(key: String): AnnotatedString? {
    if (key.length > MAX_CACHEABLE_MARKDOWN_LENGTH) return null
    synchronized(inlineLruCache) {
      inlineLruCache[key]?.let { cached ->
        inlineCacheHits.incrementAndGet()
        return cached
      }
    }
    inlineCacheMisses.incrementAndGet()
    return null
  }

  internal fun putCachedInline(key: String, value: AnnotatedString) {
    if (key.length <= MAX_CACHEABLE_MARKDOWN_LENGTH) {
      synchronized(inlineLruCache) {
        inlineLruCache[key] = value
      }
    }
  }

  fun getCacheMetrics(): MarkdownCacheMetrics {
    val bSize = synchronized(blockLruCache) { blockLruCache.size }
    val cSize = synchronized(codeLruCache) { codeLruCache.size }
    val iSize = synchronized(inlineLruCache) { inlineLruCache.size }
    return MarkdownCacheMetrics(
      blockCacheSize = bSize,
      codeCacheSize = cSize,
      inlineCacheSize = iSize,
      blockHits = blockCacheHits.get(),
      blockMisses = blockCacheMisses.get(),
      codeHits = codeCacheHits.get(),
      codeMisses = codeCacheMisses.get(),
      inlineHits = inlineCacheHits.get(),
      inlineMisses = inlineCacheMisses.get(),
    )
  }

  fun clearCaches() {
    synchronized(blockLruCache) { blockLruCache.clear() }
    synchronized(codeLruCache) { codeLruCache.clear() }
    synchronized(inlineLruCache) { inlineLruCache.clear() }
    blockCacheHits.set(0L)
    blockCacheMisses.set(0L)
    codeCacheHits.set(0L)
    codeCacheMisses.set(0L)
    inlineCacheHits.set(0L)
    inlineCacheMisses.set(0L)
  }

  /**
   * Validates whether a URL is safe to open externally.
   * Strictly allows only http:// and https:// schemes with a non-empty host,
   * rejecting control characters, whitespace, embedded credentials (user:pass@host),
   * and dangerous schemes (javascript:, data:, file:, vbscript:, intent:, content:, etc.).
   */
  fun isSafeExternalUrl(rawUrl: String): Boolean {
    val trimmed = rawUrl.trim()
    if (trimmed.isEmpty() || trimmed.length > 2048) return false
    for (ch in trimmed) {
      if (ch.code <= 0x20 || ch.code == 0x7F || ch == '\\' || ch == '<' || ch == '>' || ch == '"' || ch == '\'') {
        return false
      }
    }
    val lower = trimmed.lowercase()
    if (!lower.startsWith("https://") && !lower.startsWith("http://")) {
      return false
    }
    val authorityAndPath = trimmed.substringAfter("://", "")
    val authority = authorityAndPath.substringBefore("/").substringBefore("?").substringBefore("#")
    // Reject embedded credentials (https://trusted.com@evil.com) or empty authority
    if (authority.isBlank() || authority.contains('@')) {
      return false
    }
    return try {
      val uri = Uri.parse(trimmed)
      val scheme = uri.scheme?.lowercase()
      val host = uri.host
      val userInfo = uri.userInfo
      (scheme == "https" || scheme == "http") &&
        userInfo.isNullOrEmpty() &&
        !host.isNullOrBlank() &&
        !host.startsWith(".") &&
        !host.endsWith(".")
    } catch (_: Exception) {
      val hostPart = authority.substringBefore(":").trim()
      hostPart.isNotEmpty() && !hostPart.startsWith(".") && !hostPart.endsWith(".")
    }
  }

  /**
   * Parses raw Markdown text into a list of [MarkdownBlock] elements, utilizing a
   * bounded thread-safe LRU cache so unchanged messages never re-parse during scrolling
   * or recomposition.
   * Preserves Unicode, Hindi (Devanagari), multiline formatting, and code indentation.
   */
  fun parseBlocks(markdown: String): List<MarkdownBlock> {
    if (markdown.isEmpty()) return emptyList()

    val isCacheable = markdown.length <= MAX_CACHEABLE_MARKDOWN_LENGTH
    if (isCacheable) {
      synchronized(blockLruCache) {
        blockLruCache[markdown]?.let { cached ->
          blockCacheHits.incrementAndGet()
          return cached
        }
      }
    }
    blockCacheMisses.incrementAndGet()

    val normalized =
      if (markdown.contains('\r')) {
        markdown.replace("\r\n", "\n").replace("\r", "\n")
      } else {
        markdown
      }
    val lines = normalized.split("\n")
    val blocks = mutableListOf<MarkdownBlock>()

    var i = 0
    var codeBlockCounter = 0

    while (i < lines.size) {
      val line = lines[i]
      val trimmed = line.trim()

      // 1. Skip empty lines between blocks first (fastest path)
      if (trimmed.isEmpty()) {
        i++
        continue
      }

      val firstChar = trimmed[0]

      // 2. Fenced code block (``` or ~~~)
      if (
        (firstChar == '`' && trimmed.startsWith("```")) ||
          (firstChar == '~' && trimmed.startsWith("~~~"))
      ) {
        val fence = if (firstChar == '`') "```" else "~~~"
        val language = trimmed.removePrefix(fence).trim().lowercase()
        val codeLines = mutableListOf<String>()
        i++
        while (i < lines.size && !lines[i].trim().startsWith(fence)) {
          codeLines.add(lines[i])
          i++
        }
        // Skip closing fence if present
        if (i < lines.size && lines[i].trim().startsWith(fence)) {
          i++
        }
        blocks.add(
          MarkdownBlock.CodeBlock(
            language = language.ifBlank { "code" },
            code = codeLines.joinToString("\n"),
            blockIndex = codeBlockCounter++,
          )
        )
        continue
      }

      // 3. Horizontal Rule (only check regex if line starts with -, *, or _)
      if (
        (firstChar == '-' || firstChar == '*' || firstChar == '_') &&
          trimmed.length >= 3 &&
          HR_REGEX.matches(trimmed)
      ) {
        blocks.add(MarkdownBlock.HorizontalRule)
        i++
        continue
      }

      // 4. Heading (# .. ######)
      if (firstChar == '#') {
        val headingMatch = HEADING_REGEX.matchEntire(trimmed)
        if (headingMatch != null) {
          val level = headingMatch.groupValues[1].length.coerceIn(1, 6)
          val headingText = headingMatch.groupValues[2].trim()
          blocks.add(MarkdownBlock.Heading(level = level, text = headingText))
          i++
          continue
        }
      }

      // 5. Markdown Table (Header line with '|' followed by divider line '--- | ---')
      if (
        trimmed.contains('|') &&
          i + 1 < lines.size &&
          TABLE_DIVIDER_REGEX.matches(lines[i + 1].trim())
      ) {
        val headers = parseTableRow(lines[i])
        i += 2 // Skip header and divider row
        val rows = mutableListOf<List<String>>()
        while (i < lines.size && lines[i].trim().let { it.isNotEmpty() && it.contains('|') }) {
          val rowCells = parseTableRow(lines[i])
          if (rowCells.isNotEmpty()) {
            rows.add(rowCells)
          }
          i++
        }
        if (headers.isNotEmpty()) {
          blocks.add(MarkdownBlock.Table(headers = headers, rows = rows))
          continue
        }
      }

      // 6. Blockquote (> ...)
      if (firstChar == '>') {
        val quoteLines = mutableListOf<String>()
        while (i < lines.size && lines[i].trim().startsWith(">")) {
          quoteLines.add(lines[i].trim().removePrefix(">").trimStart())
          i++
        }
        blocks.add(MarkdownBlock.Blockquote(text = quoteLines.joinToString("\n")))
        continue
      }

      // 7. Unordered List (-, *, +)
      if (
        (firstChar == '-' || firstChar == '*' || firstChar == '+') &&
          BULLET_REGEX.matches(line)
      ) {
        val items = mutableListOf<String>()
        while (i < lines.size) {
          val m = BULLET_REGEX.matchEntire(lines[i])
          if (m != null) {
            items.add(m.groupValues[1].trim())
            i++
          } else {
            break
          }
        }
        blocks.add(MarkdownBlock.BulletList(items = items))
        continue
      }

      // 8. Ordered List (1., 2., ...)
      if (firstChar.isDigit() && ORDERED_REGEX.matches(line)) {
        val items = mutableListOf<Pair<Int, String>>()
        while (i < lines.size) {
          val m = ORDERED_REGEX.matchEntire(lines[i])
          if (m != null) {
            val number = m.groupValues[1].toIntOrNull() ?: (items.size + 1)
            items.add(number to m.groupValues[2].trim())
            i++
          } else {
            break
          }
        }
        blocks.add(MarkdownBlock.OrderedList(items = items))
        continue
      }

      // 9. Standard Paragraph (accumulate consecutive non-special lines with fast char pre-checks)
      val paragraphLines = mutableListOf<String>()
      while (i < lines.size) {
        val current = lines[i]
        val curTrim = current.trim()
        if (curTrim.isEmpty()) break
        val fc = curTrim[0]
        val isSpecialBlockStart =
          when {
            fc == '`' && curTrim.startsWith("```") -> true
            fc == '~' && curTrim.startsWith("~~~") -> true
            fc == '#' && HEADING_REGEX.matches(curTrim) -> true
            fc == '>' -> true
            (fc == '-' || fc == '*' || fc == '_') && curTrim.length >= 3 && HR_REGEX.matches(curTrim) -> true
            (fc == '-' || fc == '*' || fc == '+') && BULLET_REGEX.matches(current) -> true
            fc.isDigit() && ORDERED_REGEX.matches(current) -> true
            curTrim.contains('|') &&
              i + 1 < lines.size &&
              TABLE_DIVIDER_REGEX.matches(lines[i + 1].trim()) -> true
            else -> false
          }
        if (isSpecialBlockStart) {
          break
        }
        paragraphLines.add(current)
        i++
      }
      if (paragraphLines.isNotEmpty()) {
        blocks.add(MarkdownBlock.Paragraph(text = paragraphLines.joinToString("\n")))
      } else {
        i++
      }
    }

    val immutableBlocks = blocks.toList()
    if (isCacheable) {
      synchronized(blockLruCache) {
        blockLruCache[markdown] = immutableBlocks
      }
    }
    return immutableBlocks
  }

  private fun parseTableRow(line: String): List<String> {
    val stripped = line.trim().removePrefix("|").removeSuffix("|")
    return stripped.split("|").map { it.trim() }
  }
}

/**
 * Renders structured Markdown content safely inside a user or assistant message bubble.
 */
@Composable
fun MarkdownMessageContent(
  messageId: String,
  content: String,
  onCopyCode: (String) -> Unit,
  modifier: Modifier = Modifier,
  textColorOverride: Color? = null,
  onDownloadCode: ((code: String, language: String) -> Unit)? = null,
  onRunCode: ((code: String, language: String) -> Unit)? = null,
) {
  val blocks = remember(content) { SafeMarkdownParser.parseBlocks(content) }

  SelectionContainer {
    Column(
      modifier = modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
      blocks.forEachIndexed { index, block ->
        when (block) {
          is MarkdownBlock.Heading -> {
            MarkdownHeadingItem(
              level = block.level,
              text = block.text,
              textColorOverride = textColorOverride,
              modifier = Modifier.testTag("md_heading_${messageId}_$index"),
            )
          }

          is MarkdownBlock.Paragraph -> {
            MarkdownInlineText(
              text = block.text,
              style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
              textColorOverride = textColorOverride,
              modifier = Modifier.testTag("md_paragraph_${messageId}_$index"),
            )
          }

          is MarkdownBlock.BulletList -> {
            MarkdownBulletList(
              items = block.items,
              textColorOverride = textColorOverride,
              modifier = Modifier.testTag("md_bullet_list_${messageId}_$index"),
            )
          }

          is MarkdownBlock.OrderedList -> {
            MarkdownOrderedList(
              items = block.items,
              textColorOverride = textColorOverride,
              modifier = Modifier.testTag("md_ordered_list_${messageId}_$index"),
            )
          }

          is MarkdownBlock.Blockquote -> {
            MarkdownBlockquoteItem(
              text = block.text,
              textColorOverride = textColorOverride,
              modifier = Modifier.testTag("md_blockquote_${messageId}_$index"),
            )
          }

          is MarkdownBlock.Table -> {
            MarkdownTableItem(
              headers = block.headers,
              rows = block.rows,
              modifier = Modifier.testTag("md_table_${messageId}_$index"),
            )
          }

          is MarkdownBlock.CodeBlock -> {
            androidx.compose.foundation.text.selection.DisableSelection {
              MarkdownCodeBlockCard(
                messageId = messageId,
                block = block,
                onCopyCode = onCopyCode,
                onDownloadCode = onDownloadCode,
                onRunCode = onRunCode,
              )
            }
          }

          is MarkdownBlock.HorizontalRule -> {
            GlassDivider(modifier = Modifier.padding(vertical = AppSpacing.xs))
          }
        }
      }
    }
  }
}

@Composable
private fun MarkdownHeadingItem(
  level: Int,
  text: String,
  modifier: Modifier = Modifier,
  textColorOverride: Color? = null,
) {
  val glass = AppThemeTokens.glass
  val resolvedColor = textColorOverride ?: glass.textPrimary
  val baseStyle =
    when (level) {
      1 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
      2 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
      3 -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
      else -> MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
    }

  MarkdownInlineText(
    text = text,
    style = baseStyle.copy(color = resolvedColor),
    textColorOverride = resolvedColor,
    modifier = modifier.padding(top = 2.dp),
  )
}

@Composable
private fun MarkdownBulletList(
  items: List<String>,
  modifier: Modifier = Modifier,
  textColorOverride: Color? = null,
) {
  val glass = AppThemeTokens.glass
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    items.forEach { itemText ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
      ) {
        Text(
          text = "•",
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Bold,
          color = glass.accentCyan,
          modifier = Modifier.padding(end = AppSpacing.sm, top = 1.dp),
        )
        MarkdownInlineText(
          text = itemText,
          style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
          textColorOverride = textColorOverride,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun MarkdownOrderedList(
  items: List<Pair<Int, String>>,
  modifier: Modifier = Modifier,
  textColorOverride: Color? = null,
) {
  val glass = AppThemeTokens.glass
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    items.forEach { (num, itemText) ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
      ) {
        Text(
          text = "$num.",
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
          color = glass.accentCyan,
          modifier = Modifier.widthIn(min = 22.dp).padding(end = 6.dp),
        )
        MarkdownInlineText(
          text = itemText,
          style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
          textColorOverride = textColorOverride,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun MarkdownBlockquoteItem(
  text: String,
  modifier: Modifier = Modifier,
  textColorOverride: Color? = null,
) {
  val glass = AppThemeTokens.glass
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .height(IntrinsicSize.Min)
        .clip(RoundedCornerShape(8.dp))
        .background(glass.surfaceSubtle.copy(alpha = 0.65f))
        .padding(end = AppSpacing.sm, top = AppSpacing.xs, bottom = AppSpacing.xs),
  ) {
    Box(
      modifier =
        Modifier.width(3.dp)
          .fillMaxHeight()
          .background(glass.accentCyan, RoundedCornerShape(2.dp))
    )
    Spacer(modifier = Modifier.width(AppSpacing.sm))
    MarkdownInlineText(
      text = text,
      style =
        MaterialTheme.typography.bodyMedium.copy(
          fontStyle = FontStyle.Italic,
          color = textColorOverride?.copy(alpha = 0.92f) ?: glass.textSecondary,
        ),
      textColorOverride = textColorOverride?.copy(alpha = 0.92f),
      modifier = Modifier.weight(1f),
    )
  }
}

@Composable
private fun MarkdownTableItem(
  headers: List<String>,
  rows: List<List<String>>,
  modifier: Modifier = Modifier,
) {
  val glass = AppThemeTokens.glass
  val horizontalScroll = rememberScrollState()

  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
        .border(AppBorder.hairline, glass.borderSubtle, RoundedCornerShape(10.dp))
        .background(glass.surfaceSubtle.copy(alpha = 0.7f))
        .horizontalScroll(horizontalScroll)
  ) {
    Column {
      // Header Row
      Row(
        modifier =
          Modifier.background(glass.surfaceElevated.copy(alpha = 0.9f))
            .padding(vertical = 6.dp, horizontal = 4.dp),
      ) {
        headers.forEach { header ->
          Box(
            modifier =
              Modifier.widthIn(min = 96.dp, max = 220.dp)
                .padding(horizontal = AppSpacing.sm, vertical = 2.dp),
          ) {
            MarkdownInlineText(
              text = header,
              style =
                MaterialTheme.typography.labelMedium.copy(
                  fontWeight = FontWeight.Bold,
                  color = glass.accentCyan,
                ),
            )
          }
        }
      }

      // Data Rows
      rows.forEachIndexed { rowIndex, rowCells ->
        GlassDivider()
        Row(
          modifier =
            Modifier.background(
                if (rowIndex % 2 == 0) Color.Transparent
                else glass.surfaceStandard.copy(alpha = 0.35f)
              )
              .padding(vertical = 6.dp, horizontal = 4.dp),
        ) {
          headers.indices.forEach { colIndex ->
            val cellText = rowCells.getOrNull(colIndex).orEmpty()
            Box(
              modifier =
                Modifier.widthIn(min = 96.dp, max = 220.dp)
                  .padding(horizontal = AppSpacing.sm, vertical = 2.dp),
            ) {
              MarkdownInlineText(
                text = cellText,
                style = MaterialTheme.typography.bodySmall,
              )
            }
          }
        }
      }
    }
  }
}

/**
 * Fenced code block card with language badge, compact Line Numbers toggle, Word Wrap toggle,
 * Download Code File action, Copy Code action, and syntax highlighting.
 */
@Composable
fun MarkdownCodeBlockCard(
  messageId: String,
  block: MarkdownBlock.CodeBlock,
  onCopyCode: (String) -> Unit,
  modifier: Modifier = Modifier,
  onDownloadCode: ((code: String, language: String) -> Unit)? = null,
  onRunCode: ((code: String, language: String) -> Unit)? = null,
) {
  val glass = AppThemeTokens.glass
  var copiedRecently by remember { mutableStateOf(false) }
  var showLineNumbers by rememberSaveable { mutableStateOf(false) }
  var softWrapLines by rememberSaveable { mutableStateOf(false) }
  val horizontalScrollState = rememberScrollState()

  LaunchedEffect(copiedRecently) {
    if (copiedRecently) {
      delay(2000L)
      copiedRecently = false
    }
  }

  val highlightedCode =
    remember(block.code, block.language, glass.isDark) {
      highlightCodeSyntax(
        code = block.code,
        isDark = glass.isDark,
        defaultTextColor = glass.textPrimary,
      )
    }

  val lineCount =
    remember(block.code) {
      block.code.split('\n').size.coerceAtLeast(1)
    }

  val lineNumbersString =
    remember(lineCount) {
      (1..lineCount).joinToString("\n")
    }

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(
          if (glass.isDark) Color(0xFF070B18) else Color(0xFFF1F5F9)
        )
        .border(
          width = AppBorder.hairline,
          color = if (glass.isDark) glass.accentCyan.copy(alpha = 0.32f) else glass.borderSpecular,
          shape = RoundedCornerShape(14.dp),
        )
        .testTag("code_block_${messageId}_${block.blockIndex}"),
  ) {
    // Top Header Bar with Language & Compact Small-Icon Controls
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .background(glass.surfaceElevated.copy(alpha = 0.85f))
          .padding(horizontal = AppSpacing.sm, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        Icon(
          imageVector = Icons.Outlined.Code,
          contentDescription = null,
          tint = glass.accentCyan,
          modifier = Modifier.size(13.dp),
        )
        Text(
          text = block.language.uppercase(),
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
          fontWeight = FontWeight.Bold,
          color = glass.textSecondary,
        )
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        // 1. Compact Line Numbers Toggle (#)
        Row(
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .defaultMinSize(minHeight = 26.dp)
              .clip(RoundedCornerShape(7.dp))
              .background(
                if (showLineNumbers) glass.accentPrimary.copy(alpha = 0.18f)
                else glass.surfaceStandard.copy(alpha = 0.65f)
              )
              .border(
                AppBorder.hairline,
                if (showLineNumbers) glass.accentCyan else glass.borderSubtle,
                RoundedCornerShape(7.dp),
              )
              .semantics {
                role = Role.Button
                contentDescription =
                  if (showLineNumbers) "Hide code line numbers" else "Show code line numbers"
              }
              .clickable { showLineNumbers = !showLineNumbers }
              .padding(horizontal = 6.dp, vertical = 4.dp)
              .testTag("code_line_numbers_button_${messageId}_${block.blockIndex}"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.FormatListNumbered,
            contentDescription = null,
            tint = if (showLineNumbers) glass.accentCyan else glass.textSecondary,
            modifier = Modifier.size(12.dp),
          )
          Text(
            text = "#",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
            fontWeight = FontWeight.SemiBold,
            color = if (showLineNumbers) glass.accentCyan else glass.textSecondary,
          )
        }

        // 2. Compact Word Wrap Toggle
        Row(
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .defaultMinSize(minHeight = 26.dp)
              .clip(RoundedCornerShape(7.dp))
              .background(
                if (softWrapLines) glass.accentPrimary.copy(alpha = 0.18f)
                else glass.surfaceStandard.copy(alpha = 0.65f)
              )
              .border(
                AppBorder.hairline,
                if (softWrapLines) glass.accentCyan else glass.borderSubtle,
                RoundedCornerShape(7.dp),
              )
              .semantics {
                role = Role.Button
                contentDescription =
                  if (softWrapLines) "Disable code word wrap" else "Enable code word wrap"
              }
              .clickable { softWrapLines = !softWrapLines }
              .padding(horizontal = 6.dp, vertical = 4.dp)
              .testTag("code_wrap_button_${messageId}_${block.blockIndex}"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.WrapText,
            contentDescription = null,
            tint = if (softWrapLines) glass.accentCyan else glass.textSecondary,
            modifier = Modifier.size(12.dp),
          )
          Text(
            text = "Wrap",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
            fontWeight = FontWeight.SemiBold,
            color = if (softWrapLines) glass.accentCyan else glass.textSecondary,
          )
        }

        // 3. Compact Download Code File Button
        if (onDownloadCode != null) {
          Row(
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .defaultMinSize(minHeight = 26.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(glass.surfaceStandard.copy(alpha = 0.65f))
                .border(AppBorder.hairline, glass.borderSubtle, RoundedCornerShape(7.dp))
                .semantics {
                  role = Role.Button
                  contentDescription = "Download ${block.language} code file"
                }
                .clickable { onDownloadCode(block.code, block.language) }
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .testTag("code_download_button_${messageId}_${block.blockIndex}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.Download,
              contentDescription = null,
              tint = glass.accentCyan,
              modifier = Modifier.size(12.dp),
            )
            Text(
              text = "Save",
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
              fontWeight = FontWeight.SemiBold,
              color = glass.textPrimary,
            )
          }
        }

        // Compact Run / Preview Code Button
        if (onRunCode != null) {
          Row(
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .defaultMinSize(minHeight = 26.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(glass.accentPrimary.copy(alpha = 0.22f))
                .border(AppBorder.hairline, glass.accentCyan, RoundedCornerShape(7.dp))
                .semantics {
                  role = Role.Button
                  contentDescription = "Run and live preview ${block.language} code"
                }
                .clickable { onRunCode(block.code, block.language) }
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .testTag("code_run_button_${messageId}_${block.blockIndex}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.PlayArrow,
              contentDescription = null,
              tint = glass.accentCyan,
              modifier = Modifier.size(12.dp),
            )
            Text(
              text = "Run",
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
              fontWeight = FontWeight.Bold,
              color = glass.accentCyan,
            )
          }
        }

        // 4. Compact Copy Code Action
        Row(
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .defaultMinSize(minHeight = 26.dp)
              .clip(RoundedCornerShape(7.dp))
              .background(
                if (copiedRecently) glass.statusSuccess.copy(alpha = 0.18f)
                else glass.surfaceStandard.copy(alpha = 0.7f)
              )
              .border(
                AppBorder.hairline,
                if (copiedRecently) glass.statusSuccess else glass.borderSubtle,
                RoundedCornerShape(7.dp),
              )
              .semantics {
                role = Role.Button
                contentDescription =
                  if (copiedRecently) "Code copied to clipboard"
                  else "Copy ${block.language} code block"
              }
              .clickable {
                onCopyCode(block.code)
                copiedRecently = true
              }
              .padding(horizontal = 7.dp, vertical = 4.dp)
              .testTag("copy_code_button_${messageId}_${block.blockIndex}"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          Icon(
            imageVector = if (copiedRecently) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
            contentDescription = null,
            tint = if (copiedRecently) glass.statusSuccess else glass.accentCyan,
            modifier = Modifier.size(12.dp),
          )
          Text(
            text = if (copiedRecently) "Copied" else "Copy Code",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
            fontWeight = FontWeight.SemiBold,
            color = if (copiedRecently) glass.statusSuccess else glass.textPrimary,
          )
        }
      }
    }

    GlassDivider()

    // Code Body with optional Line Numbers and optional Horizontal Scroll / Soft Wrap
    val bodyModifier =
      if (softWrapLines) {
        Modifier.fillMaxWidth()
          .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
          .testTag("code_block_scroll_${messageId}_${block.blockIndex}")
      } else {
        Modifier.fillMaxWidth()
          .horizontalScroll(horizontalScrollState)
          .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
          .testTag("code_block_scroll_${messageId}_${block.blockIndex}")
      }

    Box(modifier = bodyModifier) {
      Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (showLineNumbers) {
          Text(
            text = lineNumbersString,
            style = CodeTypography.copy(color = glass.textMuted.copy(alpha = 0.65f)),
            softWrap = false,
            modifier = Modifier.testTag("code_line_numbers_gutter_${messageId}_${block.blockIndex}"),
          )
        }
        Text(
          text = highlightedCode,
          style = CodeTypography,
          softWrap = softWrapLines,
        )
      }
    }
  }
}

private const val URL_ANNOTATION_TAG = "SAFE_EXTERNAL_URL"

/**
 * Renders inline Markdown formatting (**bold**, *italic*, ~~strike~~, `inline code`, and [links](url))
 * and safely handles external URL clicks using [LocalUriHandler].
 */
@Composable
fun MarkdownInlineText(
  text: String,
  style: TextStyle = MaterialTheme.typography.bodyMedium,
  modifier: Modifier = Modifier,
  textColorOverride: Color? = null,
) {
  val glass = AppThemeTokens.glass
  val uriHandler = LocalUriHandler.current
  val effectiveTextColor = textColorOverride ?: glass.textPrimary

  val inlineCodeBg =
    if (glass.isDark || textColorOverride != null) Color(0xFF131C33) else Color(0xFFE2E8F0)
  val inlineCodeColor =
    if (glass.isDark || textColorOverride != null) Color(0xFF7DD3FC) else Color(0xFF0369A1)

  val annotated =
    remember(text, effectiveTextColor, glass.accentCyan, inlineCodeBg, inlineCodeColor) {
      buildInlineMarkdownAnnotatedString(
        raw = text,
        primaryColor = effectiveTextColor,
        linkColor = glass.accentCyan,
        inlineCodeBg = inlineCodeBg,
        inlineCodeColor = inlineCodeColor,
      )
    }

  val hasLinks =
    remember(annotated) {
      annotated
        .getStringAnnotations(tag = URL_ANNOTATION_TAG, start = 0, end = annotated.length)
        .isNotEmpty()
    }

  if (hasLinks) {
    @Suppress("DEPRECATION")
    ClickableText(
      text = annotated,
      style = style.copy(color = effectiveTextColor),
      modifier = modifier,
      onClick = { offset ->
        annotated
          .getStringAnnotations(tag = URL_ANNOTATION_TAG, start = offset, end = offset)
          .firstOrNull()
          ?.let { annotation ->
            val targetUrl = annotation.item
            if (SafeMarkdownParser.isSafeExternalUrl(targetUrl)) {
              try {
                uriHandler.openUri(targetUrl)
              } catch (_: Exception) {
                // Ignore if no browser activity is installed on the host device
              }
            }
          }
      },
    )
  } else {
    Text(
      text = annotated,
      style = style.copy(color = effectiveTextColor),
      modifier = modifier,
    )
  }
}

private val INLINE_TOKEN_REGEX =
  Regex(
    pattern =
      """(`[^`\n]+`)|(\*\*[^*\n]+\*\*)|(__[^_\n]+__)|(~~[^~\n]+~~)|(\[([^\]]+)\]\(([^)\s]+)\))|(\*[^*\n]+\*)|(\bhttps?://[^\s<>()]+)"""
  )

private fun containsPotentialInlineMarkdown(raw: String): Boolean {
  for (i in 0 until raw.length) {
    when (raw[i]) {
      '`', '*', '_', '~', '[', ':' -> return true
    }
  }
  return false
}

internal fun buildInlineMarkdownAnnotatedString(
  raw: String,
  primaryColor: Color,
  linkColor: Color,
  inlineCodeBg: Color,
  inlineCodeColor: Color,
): AnnotatedString {
  if (raw.isEmpty()) return AnnotatedString("")
  if (!containsPotentialInlineMarkdown(raw)) {
    return AnnotatedString(raw)
  }

  val cacheKey =
    "$raw|${primaryColor.value}|${linkColor.value}|${inlineCodeBg.value}|${inlineCodeColor.value}"
  SafeMarkdownParser.getCachedInline(cacheKey)?.let { return it }

  val computed = buildAnnotatedString {
    var cursor = 0
    val matches = INLINE_TOKEN_REGEX.findAll(raw)

    for (match in matches) {
      if (match.range.first > cursor) {
        append(raw.substring(cursor, match.range.first))
      }

      val token = match.value
      when {
        // Inline `code`
        token.startsWith("`") && token.endsWith("`") && token.length >= 2 -> {
          val codeContent = token.substring(1, token.length - 1)
          pushStyle(
            SpanStyle(
              fontFamily = FontFamily.Monospace,
              fontSize = 13.sp,
              background = inlineCodeBg,
              color = inlineCodeColor,
              fontWeight = FontWeight.Medium,
            )
          )
          append(" $codeContent ")
          pop()
        }

        // **Bold** or __Bold__
        (token.startsWith("**") && token.endsWith("**")) ||
          (token.startsWith("__") && token.endsWith("__")) -> {
          val inner = token.substring(2, token.length - 2)
          pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = primaryColor))
          append(inner)
          pop()
        }

        // ~~Strikethrough~~
        token.startsWith("~~") && token.endsWith("~~") -> {
          val inner = token.substring(2, token.length - 2)
          pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
          append(inner)
          pop()
        }

        // [Label](url)
        token.startsWith("[") && match.groups[6] != null && match.groups[7] != null -> {
          val label = match.groups[6]!!.value
          val url = match.groups[7]!!.value.trim()
          if (SafeMarkdownParser.isSafeExternalUrl(url)) {
            pushStringAnnotation(tag = URL_ANNOTATION_TAG, annotation = url)
            pushStyle(
              SpanStyle(
                color = linkColor,
                fontWeight = FontWeight.SemiBold,
                textDecoration = TextDecoration.Underline,
              )
            )
            append(label)
            pop()
            pop()
          } else {
            // Render unsafe URL scheme as inert plain text
            append(label)
          }
        }

        // *Italic*
        token.startsWith("*") && token.endsWith("*") && token.length >= 2 -> {
          val inner = token.substring(1, token.length - 1)
          pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
          append(inner)
          pop()
        }

        // Bare https:// or http:// link
        token.startsWith("http://") || token.startsWith("https://") -> {
          val cleanedUrl = token.trimEnd('.', ',', ';', ':')
          val trailingPunct = token.substring(cleanedUrl.length)
          if (SafeMarkdownParser.isSafeExternalUrl(cleanedUrl)) {
            pushStringAnnotation(tag = URL_ANNOTATION_TAG, annotation = cleanedUrl)
            pushStyle(
              SpanStyle(
                color = linkColor,
                textDecoration = TextDecoration.Underline,
              )
            )
            append(cleanedUrl)
            pop()
            pop()
            if (trailingPunct.isNotEmpty()) {
              append(trailingPunct)
            }
          } else {
            append(token)
          }
        }

        else -> append(token)
      }

      cursor = match.range.last + 1
    }

    if (cursor < raw.length) {
      append(raw.substring(cursor))
    }
  }

  SafeMarkdownParser.putCachedInline(cacheKey, computed)
  return computed
}

private val CODE_SYNTAX_REGEX =
  Regex(
    pattern =
      """(//[^\n]*|#[^\n]*)|("([^"\\]|\\.)*"|'([^'\\]|\\.)*')|(@[A-Za-z_][A-Za-z0-9_]*)|\b(fun|val|var|class|object|interface|return|if|else|when|for|while|import|package|suspend|override|private|public|internal|protected|data|sealed|enum|companion|true|false|null|const|async|await|function|let|def|from|try|catch|finally|throw|new|void|int|boolean|String|List|Map|Flow|StateFlow)\b|\b(\d+(\.\d+)?)\b"""
  )

/**
 * Safe, lightweight syntax highlighter producing an [AnnotatedString] for code blocks.
 * Uses a bounded LRU cache in [SafeMarkdownParser] so scrolling code blocks off and on screen
 * does not re-run regex syntax highlighting.
 */
internal fun highlightCodeSyntax(
  code: String,
  isDark: Boolean,
  defaultTextColor: Color,
): AnnotatedString {
  if (code.isEmpty()) return AnnotatedString("")

  val cacheKey = "$isDark|${defaultTextColor.value}|$code"
  SafeMarkdownParser.getCachedCode(cacheKey)?.let { return it }

  val commentColor = if (isDark) Color(0xFF64748B) else Color(0xFF64748B)
  val stringColor = if (isDark) Color(0xFF34D399) else Color(0xFF047857)
  val annotationColor = if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309)
  val keywordColor = if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5)
  val numberColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)

  val highlighted = buildAnnotatedString {
    pushStyle(SpanStyle(color = defaultTextColor))
    var cursor = 0
    for (match in CODE_SYNTAX_REGEX.findAll(code)) {
      if (match.range.first > cursor) {
        append(code.substring(cursor, match.range.first))
      }
      val style =
        when {
          match.groups[1] != null -> SpanStyle(color = commentColor, fontStyle = FontStyle.Italic)
          match.groups[2] != null -> SpanStyle(color = stringColor)
          match.groups[5] != null -> SpanStyle(color = annotationColor)
          match.groups[6] != null -> SpanStyle(color = keywordColor, fontWeight = FontWeight.SemiBold)
          match.groups[7] != null -> SpanStyle(color = numberColor)
          else -> SpanStyle(color = defaultTextColor)
        }
      pushStyle(style)
      append(match.value)
      pop()
      cursor = match.range.last + 1
    }
    if (cursor < code.length) {
      append(code.substring(cursor))
    }
    pop()
  }

  SafeMarkdownParser.putCachedCode(cacheKey, highlighted)
  return highlighted
}

