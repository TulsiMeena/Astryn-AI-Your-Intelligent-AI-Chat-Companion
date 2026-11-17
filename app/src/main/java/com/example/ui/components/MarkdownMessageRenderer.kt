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
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.text.style.TextAlign
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
  data class NestedBulletList(val items: List<NestedListEntry>) : MarkdownBlock()

  @Immutable
  data class Checklist(val items: List<ChecklistEntry>) : MarkdownBlock()

  @Immutable
  data class OrderedList(val items: List<Pair<Int, String>>) : MarkdownBlock()

  @Immutable
  data class Blockquote(val text: String) : MarkdownBlock()

  @Immutable
  data class Callout(
    val kind: CalloutKind,
    val title: String,
    val body: String,
  ) : MarkdownBlock()

  @Immutable
  data class CopyableBlock(
    val category: CopyBlockCategory,
    val title: String,
    val payload: String,
    val blockIndex: Int,
  ) : MarkdownBlock()

  @Immutable
  data class Table(
    val headers: List<String>,
    val rows: List<List<String>>,
    val alignments: List<TableColumnAlignment> = emptyList(),
  ) : MarkdownBlock()

  @Immutable
  data class CodeBlock(
    val language: String,
    val code: String,
    val blockIndex: Int,
  ) : MarkdownBlock()

  @Immutable
  data class MathFormula(
    val latex: String,
    val blockIndex: Int,
  ) : MarkdownBlock()

  @Immutable
  data class Chart(
    val rawSpec: String,
    val parseResult: ChartParseResult,
    val blockIndex: Int,
  ) : MarkdownBlock()

  @Immutable
  data class Diagram(
    val rawSource: String,
    val parseResult: DiagramParseResult,
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
  private val CHECKLIST_REGEX = Regex("""^(\s*)[-*+]\s+\[([ xX])\]\s+(.+)$""")
  private val BULLET_REGEX = Regex("""^(\s*)[-*+]\s+(.+)$""")
  private val ORDERED_REGEX = Regex("""^(\s*)(\d+)\.\s+(.+)$""")
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
  fun parseBlocks(markdown: String, cacheResult: Boolean = true): List<MarkdownBlock> {
    if (markdown.isEmpty()) return emptyList()

    val isCacheable = cacheResult && markdown.length <= MAX_CACHEABLE_MARKDOWN_LENGTH
    if (isCacheable) {
      synchronized(blockLruCache) {
        blockLruCache[markdown]?.let { cached ->
          blockCacheHits.incrementAndGet()
          return cached
        }
      }
      blockCacheMisses.incrementAndGet()
    } else if (markdown.length <= MAX_CACHEABLE_MARKDOWN_LENGTH) {
      synchronized(blockLruCache) {
        blockLruCache[markdown]?.let { cached ->
          blockCacheHits.incrementAndGet()
          return cached
        }
      }
    }

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
    var copyBlockCounter = 0
    var mathBlockCounter = 0
    var chartBlockCounter = 0
    var diagramBlockCounter = 0
    var lastHeadingText: String? = null

    while (i < lines.size) {
      val line = lines[i]
      val trimmed = line.trim()

      // 1. Skip empty lines between blocks first (fastest path)
      if (trimmed.isEmpty()) {
        i++
        continue
      }

      val firstChar = trimmed[0]

      // 2. Fenced blocks (``` or ~~~): Code, Chart, Diagram, Math, or Copyable Content
      if (
        (firstChar == '`' && trimmed.startsWith("```")) ||
          (firstChar == '~' && trimmed.startsWith("~~~"))
      ) {
        val fence = if (firstChar == '`') "```" else "~~~"
        val language = trimmed.removePrefix(fence).trim().lowercase()
        val bodyLines = mutableListOf<String>()
        i++
        while (i < lines.size && !lines[i].trim().startsWith(fence)) {
          bodyLines.add(lines[i])
          i++
        }
        if (i < lines.size && lines[i].trim().startsWith(fence)) {
          i++
        }
        val rawBody = bodyLines.joinToString("\n")
        when (language) {
          "chart", "astryn-chart", "graph-data" -> {
            blocks.add(
              MarkdownBlock.Chart(
                rawSpec = rawBody,
                parseResult = AstrynChartParser.parseChartBlock(rawBody),
                blockIndex = chartBlockCounter++,
              )
            )
          }
          "mermaid", "flowchart", "diagram" -> {
            blocks.add(
              MarkdownBlock.Diagram(
                rawSource = rawBody,
                parseResult = AstrynDiagramParser.parseDiagram(rawBody),
                blockIndex = diagramBlockCounter++,
              )
            )
          }
          "math", "latex", "tex" -> {
            blocks.add(
              MarkdownBlock.MathFormula(
                latex = rawBody,
                blockIndex = mathBlockCounter++,
              )
            )
          }
          else -> {
            val copyCategory =
              AstrynResponseFormatEngine.detectCopyableFenceCategory(
                fenceLanguage = language,
                body = rawBody,
                precedingHeading = lastHeadingText,
              )
            if (copyCategory != null) {
              blocks.add(
                MarkdownBlock.CopyableBlock(
                  category = copyCategory,
                  title = copyCategory.displayLabel,
                  payload = AstrynRichTextSanitizer.sanitizeClipboardPayload(rawBody),
                  blockIndex = copyBlockCounter++,
                )
              )
            } else {
              blocks.add(
                MarkdownBlock.CodeBlock(
                  language = language.ifBlank { "code" },
                  code = rawBody,
                  blockIndex = codeBlockCounter++,
                )
              )
            }
          }
        }
        continue
      }

      // 2b. Display Math Block ($$ ... $$ or \[ ... \])
      if (trimmed.startsWith("$$") || trimmed.startsWith("\\[")) {
        val endDelimiter = if (trimmed.startsWith("$$")) "$$" else "\\]"
        if (trimmed.length > 2 && trimmed.endsWith(endDelimiter)) {
          val expr = trimmed.removePrefix(if (endDelimiter == "$$") "$$" else "\\[").removeSuffix(endDelimiter).trim()
          blocks.add(MarkdownBlock.MathFormula(latex = expr, blockIndex = mathBlockCounter++))
          i++
          continue
        }
        val mathLines = mutableListOf<String>()
        val firstRemainder = trimmed.removePrefix(if (endDelimiter == "$$") "$$" else "\\[").trim()
        if (firstRemainder.isNotEmpty()) mathLines.add(firstRemainder)
        i++
        while (i < lines.size && !lines[i].trim().endsWith(endDelimiter)) {
          mathLines.add(lines[i])
          i++
        }
        if (i < lines.size && lines[i].trim().endsWith(endDelimiter)) {
          val lastSeg = lines[i].trim().removeSuffix(endDelimiter).trim()
          if (lastSeg.isNotEmpty()) mathLines.add(lastSeg)
          i++
        }
        blocks.add(
          MarkdownBlock.MathFormula(
            latex = mathLines.joinToString("\n").trim(),
            blockIndex = mathBlockCounter++,
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
          val headingText = AstrynRichTextSanitizer.sanitizeDangerousHtml(headingMatch.groupValues[2].trim())
          lastHeadingText = headingText
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
        val headers = parseTableRow(lines[i]).map { AstrynRichTextSanitizer.sanitizeDangerousHtml(it) }
        val dividerAlignments = AstrynResponseFormatEngine.parseTableAlignments(lines[i + 1], headers.size)
        i += 2 // Skip header and divider row
        val rows = mutableListOf<List<String>>()
        while (i < lines.size && lines[i].trim().let { it.isNotEmpty() && it.contains('|') }) {
          val rowCells = parseTableRow(lines[i]).map { AstrynRichTextSanitizer.sanitizeDangerousHtml(it) }
          if (rowCells.isNotEmpty()) {
            rows.add(rowCells)
          }
          i++
        }
        if (headers.isNotEmpty()) {
          val effectiveAlignments =
            AstrynResponseFormatEngine.resolveEffectiveTableAlignments(
              explicitAlignments = dividerAlignments,
              headers = headers,
              rows = rows,
            )
          blocks.add(
            MarkdownBlock.Table(
              headers = headers,
              rows = rows,
              alignments = effectiveAlignments,
            )
          )
          continue
        }
      }

      // 6. Blockquote (> ...) or Explicit Callout (> [!NOTE] / > **Warning:** ...)
      if (firstChar == '>') {
        val quoteLines = mutableListOf<String>()
        while (i < lines.size && lines[i].trim().startsWith(">")) {
          quoteLines.add(AstrynRichTextSanitizer.sanitizeDangerousHtml(lines[i].trim().removePrefix(">").trimStart()))
          i++
        }
        val callout = AstrynResponseFormatEngine.detectCalloutFromQuoteLines(quoteLines)
        if (callout != null) {
          blocks.add(MarkdownBlock.Callout(kind = callout.first, title = callout.second, body = callout.third))
        } else {
          blocks.add(MarkdownBlock.Blockquote(text = quoteLines.joinToString("\n")))
        }
        continue
      }

      // 7a. Checklist (- [ ] / - [x])
      if ((firstChar == '-' || firstChar == '*' || firstChar == '+') && CHECKLIST_REGEX.matches(line)) {
        val items = mutableListOf<ChecklistEntry>()
        while (i < lines.size) {
          val cm = CHECKLIST_REGEX.matchEntire(lines[i])
          if (cm != null) {
            val indent = (cm.groupValues[1].length / 2).coerceIn(0, 3)
            val checked = cm.groupValues[2].equals("x", ignoreCase = true)
            val text = AstrynRichTextSanitizer.sanitizeDangerousHtml(cm.groupValues[3].trim())
            items.add(ChecklistEntry(indentLevel = indent, checked = checked, text = text))
            i++
          } else {
            break
          }
        }
        blocks.add(MarkdownBlock.Checklist(items = items))
        continue
      }

      // 7b. Unordered / Nested List (-, *, +)
      if ((firstChar == '-' || firstChar == '*' || firstChar == '+') && BULLET_REGEX.matches(line)) {
        val nestedEntries = mutableListOf<NestedListEntry>()
        var hasSubIndentation = false
        while (i < lines.size) {
          if (CHECKLIST_REGEX.matches(lines[i])) break
          val bm = BULLET_REGEX.matchEntire(lines[i])
          if (bm != null) {
            val indent = (bm.groupValues[1].length / 2).coerceIn(0, 3)
            if (indent > 0) hasSubIndentation = true
            val itemText = AstrynRichTextSanitizer.sanitizeDangerousHtml(bm.groupValues[2].trim())
            nestedEntries.add(NestedListEntry(indentLevel = indent, text = itemText, isOrdered = false))
            i++
          } else if (nestedEntries.isNotEmpty() && lines[i].startsWith("  ") && ORDERED_REGEX.matches(lines[i])) {
            val om = ORDERED_REGEX.matchEntire(lines[i]) ?: break
            val indent = (om.groupValues[1].length / 2).coerceIn(1, 3)
            hasSubIndentation = true
            val num = om.groupValues[2].toIntOrNull() ?: 1
            val itemText = AstrynRichTextSanitizer.sanitizeDangerousHtml(om.groupValues[3].trim())
            nestedEntries.add(NestedListEntry(indentLevel = indent, text = itemText, isOrdered = true, orderNumber = num))
            i++
          } else {
            break
          }
        }
        if (hasSubIndentation) {
          blocks.add(MarkdownBlock.NestedBulletList(items = nestedEntries))
        } else {
          blocks.add(MarkdownBlock.BulletList(items = nestedEntries.map { it.text }))
        }
        continue
      }

      // 8. Ordered List (1., 2., ...) with optional indented sub-bullets
      if (firstChar.isDigit() && ORDERED_REGEX.matches(line)) {
        val rawEntries = mutableListOf<NestedListEntry>()
        var hasSubIndentation = false
        while (i < lines.size) {
          val om = ORDERED_REGEX.matchEntire(lines[i])
          if (om != null) {
            val indent = (om.groupValues[1].length / 2).coerceIn(0, 3)
            if (indent > 0) hasSubIndentation = true
            val number = om.groupValues[2].toIntOrNull() ?: (rawEntries.size + 1)
            val txt = AstrynRichTextSanitizer.sanitizeDangerousHtml(om.groupValues[3].trim())
            rawEntries.add(NestedListEntry(indentLevel = indent, text = txt, isOrdered = true, orderNumber = number))
            i++
          } else if (rawEntries.isNotEmpty() && lines[i].startsWith("  ") && BULLET_REGEX.matches(lines[i])) {
            val bm = BULLET_REGEX.matchEntire(lines[i]) ?: break
            val indent = (bm.groupValues[1].length / 2).coerceIn(1, 3)
            hasSubIndentation = true
            val txt = AstrynRichTextSanitizer.sanitizeDangerousHtml(bm.groupValues[2].trim())
            rawEntries.add(NestedListEntry(indentLevel = indent, text = txt, isOrdered = false))
            i++
          } else {
            break
          }
        }
        if (hasSubIndentation) {
          blocks.add(MarkdownBlock.NestedBulletList(items = rawEntries))
        } else {
          blocks.add(MarkdownBlock.OrderedList(items = rawEntries.map { it.orderNumber to it.text }))
        }
        continue
      }

      // 9. Standard Paragraph (accumulate consecutive non-special lines with HTML sanitization)
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
            curTrim.startsWith("$$") || curTrim.startsWith("\\[") -> true
            fc == '#' && HEADING_REGEX.matches(curTrim) -> true
            fc == '>' -> true
            (fc == '-' || fc == '*' || fc == '_') && curTrim.length >= 3 && HR_REGEX.matches(curTrim) -> true
            (fc == '-' || fc == '*' || fc == '+') && (CHECKLIST_REGEX.matches(current) || BULLET_REGEX.matches(current)) -> true
            fc.isDigit() && ORDERED_REGEX.matches(current) -> true
            curTrim.contains('|') &&
              i + 1 < lines.size &&
              TABLE_DIVIDER_REGEX.matches(lines[i + 1].trim()) -> true
            else -> false
          }
        if (isSpecialBlockStart) {
          break
        }
        val cleanedLine = AstrynRichTextSanitizer.sanitizeDangerousHtml(current)
        if (cleanedLine.isNotEmpty()) {
          paragraphLines.add(cleanedLine)
        }
        i++
      }
      if (paragraphLines.isNotEmpty()) {
        val joined = paragraphLines.joinToString("\n")
        val calloutPrefix =
          if (joined.startsWith("**Warning:**", ignoreCase = true) ||
            joined.startsWith("**Note:**", ignoreCase = true) ||
            joined.startsWith("**Tip:**", ignoreCase = true) ||
            joined.startsWith("**Important:**", ignoreCase = true)
          ) {
            AstrynResponseFormatEngine.detectCalloutFromQuoteLines(listOf(joined))
          } else null

        if (calloutPrefix != null) {
          blocks.add(MarkdownBlock.Callout(calloutPrefix.first, calloutPrefix.second, calloutPrefix.third))
        } else {
          blocks.add(MarkdownBlock.Paragraph(text = joined))
        }
      } else if (i < lines.size && paragraphLines.isEmpty() && lines[i].trim().isNotEmpty()) {
        // Pure stripped dangerous line; advance safely
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
 * During active token streaming ([isStreaming] == true), skips [SelectionContainer] registration
 * overhead for 120Hz zero-lag frame rendering and appends a smooth glowing AI cursor (`▍`).
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
  isStreaming: Boolean = false,
) {
  val effectiveMarkdown =
    remember(content, isStreaming) {
      if (isStreaming) {
        AstrynResponseFormatEngine.buildStreamingSafePreview(content).safeMarkdown
      } else {
        content
      }
    }

  val blocks =
    remember(effectiveMarkdown, isStreaming) {
      SafeMarkdownParser.parseBlocks(effectiveMarkdown, cacheResult = !isStreaming)
    }

  val markdownBlocksColumn: @Composable () -> Unit = {
    Column(
      modifier = modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
      val lastIndex = blocks.lastIndex
      blocks.forEachIndexed { index, block ->
        val isTrailingStreamingBlock = isStreaming && index == lastIndex
        when (block) {
          is MarkdownBlock.Heading -> {
            MarkdownHeadingItem(
              level = block.level,
              text = if (isTrailingStreamingBlock) "${block.text} ▍" else block.text,
              textColorOverride = textColorOverride,
              modifier = Modifier.testTag("md_heading_${messageId}_$index"),
            )
          }

          is MarkdownBlock.Paragraph -> {
            MarkdownInlineText(
              text = if (isTrailingStreamingBlock) "${block.text} ▍" else block.text,
              style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
              textColorOverride = textColorOverride,
              modifier = Modifier.testTag("md_paragraph_${messageId}_$index"),
            )
          }

          is MarkdownBlock.BulletList -> {
            val itemsToRender =
              if (isTrailingStreamingBlock && block.items.isNotEmpty()) {
                block.items.dropLast(1) + ("${block.items.last()} ▍")
              } else {
                block.items
              }
            MarkdownBulletList(
              items = itemsToRender,
              textColorOverride = textColorOverride,
              modifier = Modifier.testTag("md_bullet_list_${messageId}_$index"),
            )
          }

          is MarkdownBlock.NestedBulletList -> {
            MarkdownNestedBulletList(
              items = block.items,
              textColorOverride = textColorOverride,
              modifier = Modifier.testTag("md_nested_list_${messageId}_$index"),
            )
          }

          is MarkdownBlock.Checklist -> {
            MarkdownChecklistBlock(
              items = block.items,
              textColorOverride = textColorOverride,
              modifier = Modifier.testTag("md_checklist_${messageId}_$index"),
            )
          }

          is MarkdownBlock.OrderedList -> {
            val itemsToRender =
              if (isTrailingStreamingBlock && block.items.isNotEmpty()) {
                val lastPair = block.items.last()
                block.items.dropLast(1) + (lastPair.first to "${lastPair.second} ▍")
              } else {
                block.items
              }
            MarkdownOrderedList(
              items = itemsToRender,
              textColorOverride = textColorOverride,
              modifier = Modifier.testTag("md_ordered_list_${messageId}_$index"),
            )
          }

          is MarkdownBlock.Blockquote -> {
            MarkdownBlockquoteItem(
              text = if (isTrailingStreamingBlock) "${block.text} ▍" else block.text,
              textColorOverride = textColorOverride,
              modifier = Modifier.testTag("md_blockquote_${messageId}_$index"),
            )
          }

          is MarkdownBlock.Callout -> {
            MarkdownCalloutCard(
              kind = block.kind,
              title = block.title,
              body = if (isTrailingStreamingBlock) "${block.body} ▍" else block.body,
              modifier = Modifier.testTag("md_callout_${messageId}_$index"),
            )
          }

          is MarkdownBlock.CopyableBlock -> {
            val card: @Composable () -> Unit = {
              MarkdownCopyBlockCard(
                messageId = messageId,
                block = block,
                onCopyPayload = onCopyCode,
              )
            }
            if (isStreaming) card()
            else androidx.compose.foundation.text.selection.DisableSelection { card() }
          }

          is MarkdownBlock.Table -> {
            MarkdownTableItem(
              headers = block.headers,
              rows = block.rows,
              alignments = block.alignments,
              modifier = Modifier.testTag("md_table_${messageId}_$index"),
            )
          }

          is MarkdownBlock.CodeBlock -> {
            if (isStreaming) {
              MarkdownCodeBlockCard(
                messageId = messageId,
                block = block,
                onCopyCode = onCopyCode,
                onDownloadCode = onDownloadCode,
                onRunCode = onRunCode,
              )
            } else {
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
          }

          is MarkdownBlock.MathFormula -> {
            androidx.compose.foundation.text.selection.DisableSelection {
              AstrynMathFormulaCard(
                messageId = messageId,
                blockIndex = block.blockIndex,
                rawLatex = block.latex,
                onCopyFormula = onCopyCode,
              )
            }
          }

          is MarkdownBlock.Chart -> {
            androidx.compose.foundation.text.selection.DisableSelection {
              AstrynChartCard(
                messageId = messageId,
                blockIndex = block.blockIndex,
                parseResult = block.parseResult,
              )
            }
          }

          is MarkdownBlock.Diagram -> {
            androidx.compose.foundation.text.selection.DisableSelection {
              AstrynDiagramCard(
                messageId = messageId,
                blockIndex = block.blockIndex,
                parseResult = block.parseResult,
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

  if (isStreaming) {
    markdownBlocksColumn()
  } else {
    SelectionContainer {
      markdownBlocksColumn()
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
private fun MarkdownNestedBulletList(
  items: List<NestedListEntry>,
  modifier: Modifier = Modifier,
  textColorOverride: Color? = null,
) {
  val glass = AppThemeTokens.glass
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    items.forEach { entry ->
      val startPad = (entry.indentLevel * 16).dp
      val marker =
        if (entry.isOrdered) "${entry.orderNumber}."
        else when (entry.indentLevel) {
          0 -> "•"
          1 -> "◦"
          else -> "▪"
        }
      Row(
        modifier = Modifier.fillMaxWidth().padding(start = startPad),
        verticalAlignment = Alignment.Top,
      ) {
        Text(
          text = marker,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Bold,
          color = glass.accentCyan,
          modifier = Modifier.widthIn(min = 18.dp).padding(end = 6.dp),
        )
        MarkdownInlineText(
          text = entry.text,
          style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
          textColorOverride = textColorOverride,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun MarkdownChecklistBlock(
  items: List<ChecklistEntry>,
  modifier: Modifier = Modifier,
  textColorOverride: Color? = null,
) {
  val glass = AppThemeTokens.glass
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    items.forEach { entry ->
      val startPad = (entry.indentLevel * 16).dp
      Row(
        modifier = Modifier.fillMaxWidth().padding(start = startPad),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector =
            if (entry.checked) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
          contentDescription = if (entry.checked) "Checked" else "Unchecked",
          tint = if (entry.checked) glass.statusSuccess else glass.accentCyan,
          modifier = Modifier.padding(top = 2.dp).size(17.dp),
        )
        MarkdownInlineText(
          text = entry.text,
          style =
            MaterialTheme.typography.bodyMedium.copy(
              lineHeight = 22.sp,
              textDecoration = if (entry.checked) TextDecoration.LineThrough else TextDecoration.None,
            ),
          textColorOverride =
            if (entry.checked) (textColorOverride ?: glass.textSecondary).copy(alpha = 0.72f)
            else textColorOverride,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun MarkdownCalloutCard(
  kind: CalloutKind,
  title: String,
  body: String,
  modifier: Modifier = Modifier,
) {
  val glass = AppThemeTokens.glass
  val accentColor =
    when (kind) {
      CalloutKind.WARNING, CalloutKind.CONS -> glass.statusWarning
      CalloutKind.PROS, CalloutKind.TIP -> glass.statusSuccess
      CalloutKind.IMPORTANT -> glass.accentPrimary
      CalloutKind.NOTE, CalloutKind.DEFINITION, CalloutKind.SUMMARY -> glass.accentCyan
    }
  val iconVector =
    when (kind) {
      CalloutKind.WARNING -> Icons.Outlined.WarningAmber
      CalloutKind.TIP -> Icons.Outlined.Lightbulb
      else -> Icons.Outlined.Info
    }

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(accentColor.copy(alpha = 0.09f))
        .border(AppBorder.hairline, accentColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
        .padding(AppSpacing.sm),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(
        imageVector = iconVector,
        contentDescription = null,
        tint = accentColor,
        modifier = Modifier.size(15.dp),
      )
      Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = accentColor,
      )
    }
    MarkdownInlineText(
      text = body,
      style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
    )
  }
}

/**
 * Dedicated copyable draft/template/prompt/command card preserving exact line breaks, indentation,
 * and Unicode characters without leaking decorative UI chrome into the clipboard payload.
 */
@Composable
fun MarkdownCopyBlockCard(
  messageId: String,
  block: MarkdownBlock.CopyableBlock,
  onCopyPayload: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val glass = AppThemeTokens.glass
  var copiedRecently by remember { mutableStateOf(false) }

  LaunchedEffect(copiedRecently) {
    if (copiedRecently) {
      delay(2000L)
      copiedRecently = false
    }
  }

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(glass.surfaceElevated.copy(alpha = 0.80f))
        .border(AppBorder.hairline, glass.accentCyan.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
        .testTag("copy_block_${messageId}_${block.blockIndex}"),
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .background(glass.surfaceStandard.copy(alpha = 0.72f))
          .padding(horizontal = AppSpacing.sm, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = block.title.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
        fontWeight = FontWeight.Bold,
        color = glass.accentCyan,
      )
      Row(
        modifier =
          Modifier.minimumInteractiveComponentSize()
            .defaultMinSize(minHeight = 26.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(
              if (copiedRecently) glass.statusSuccess.copy(alpha = 0.18f)
              else glass.surfaceElevated.copy(alpha = 0.8f)
            )
            .border(
              AppBorder.hairline,
              if (copiedRecently) glass.statusSuccess else glass.borderSubtle,
              RoundedCornerShape(7.dp),
            )
            .semantics {
              role = Role.Button
              contentDescription = if (copiedRecently) "Copied to clipboard" else "Copy ${block.title}"
            }
            .clickable {
              onCopyPayload(block.payload)
              copiedRecently = true
            }
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("copy_block_button_${messageId}_${block.blockIndex}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Icon(
          imageVector = if (copiedRecently) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
          contentDescription = null,
          tint = if (copiedRecently) glass.statusSuccess else glass.accentCyan,
          modifier = Modifier.size(12.dp),
        )
        Text(
          text = if (copiedRecently) "Copied" else "Copy",
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
          fontWeight = FontWeight.SemiBold,
          color = if (copiedRecently) glass.statusSuccess else glass.textPrimary,
        )
      }
    }
    GlassDivider()
    SelectionContainer {
      Text(
        text = block.payload,
        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
        color = glass.textPrimary,
        modifier = Modifier.fillMaxWidth().padding(AppSpacing.md),
      )
    }
  }
}

private const val DEFAULT_VISIBLE_TABLE_ROWS = 25

@Composable
private fun MarkdownTableItem(
  headers: List<String>,
  rows: List<List<String>>,
  alignments: List<TableColumnAlignment> = emptyList(),
  modifier: Modifier = Modifier,
) {
  val glass = AppThemeTokens.glass
  val horizontalScroll = rememberScrollState()
  var showAllRows by rememberSaveable(headers, rows.size) { mutableStateOf(false) }

  val effectiveAlignments =
    remember(headers, rows, alignments) {
      AstrynResponseFormatEngine.resolveEffectiveTableAlignments(alignments, headers, rows)
    }

  val displayedRows =
    if (showAllRows || rows.size <= DEFAULT_VISIBLE_TABLE_ROWS) {
      rows
    } else {
      rows.take(DEFAULT_VISIBLE_TABLE_ROWS)
    }

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
        .border(AppBorder.hairline, glass.borderSubtle, RoundedCornerShape(10.dp))
        .background(glass.surfaceSubtle.copy(alpha = 0.7f)),
  ) {
    Box(
      modifier = Modifier.fillMaxWidth().horizontalScroll(horizontalScroll),
    ) {
      Column {
        // Header Row
        Row(
          modifier =
            Modifier.background(glass.surfaceElevated.copy(alpha = 0.9f))
              .padding(vertical = 6.dp, horizontal = 4.dp),
        ) {
          headers.forEachIndexed { colIndex, header ->
            val align = effectiveAlignments.getOrNull(colIndex) ?: TableColumnAlignment.LEFT
            val textAlign =
              when (align) {
                TableColumnAlignment.RIGHT -> TextAlign.End
                TableColumnAlignment.CENTER -> TextAlign.Center
                TableColumnAlignment.LEFT -> TextAlign.Start
              }
            Box(
              modifier =
                Modifier.widthIn(min = 92.dp, max = 230.dp)
                  .padding(horizontal = AppSpacing.sm, vertical = 2.dp),
            ) {
              MarkdownInlineText(
                text = header,
                style =
                  MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = glass.accentCyan,
                    textAlign = textAlign,
                  ),
                modifier = Modifier.fillMaxWidth(),
              )
            }
          }
        }

        // Data Rows
        displayedRows.forEachIndexed { rowIndex, rowCells ->
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
              val align = effectiveAlignments.getOrNull(colIndex) ?: TableColumnAlignment.LEFT
              val textAlign =
                when (align) {
                  TableColumnAlignment.RIGHT -> TextAlign.End
                  TableColumnAlignment.CENTER -> TextAlign.Center
                  TableColumnAlignment.LEFT -> TextAlign.Start
                }
              Box(
                modifier =
                  Modifier.widthIn(min = 92.dp, max = 230.dp)
                    .padding(horizontal = AppSpacing.sm, vertical = 2.dp),
              ) {
                MarkdownInlineText(
                  text = cellText.ifEmpty { "—" },
                  style = MaterialTheme.typography.bodySmall.copy(textAlign = textAlign),
                  textColorOverride = if (cellText.isEmpty()) glass.textMuted else null,
                  modifier = Modifier.fillMaxWidth(),
                )
              }
            }
          }
        }
      }
    }

    if (rows.size > DEFAULT_VISIBLE_TABLE_ROWS) {
      GlassDivider()
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable { showAllRows = !showAllRows }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text =
            if (showAllRows) "Show fewer rows"
            else "Show all ${rows.size} rows (${rows.size - DEFAULT_VISIBLE_TABLE_ROWS} more)",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.SemiBold,
          color = glass.accentCyan,
        )
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

  val langSpec =
    remember(block.language) {
      AstrynResponseFormatEngine.normalizeCodeLanguage(block.language)
    }

  val highlightedCode =
    remember(block.code, block.language, glass.isDark) {
      AstrynResponseFormatEngine.highlightCodeForLanguage(
        code = block.code,
        language = block.language,
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
          text = langSpec.displayName,
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
      '`', '*', '_', '~', '[', ':', '$', '\\' -> return true
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

  // First tokenize safe inline math ($...$ or \(...\)) outside currency expressions
  val mathTokens = AstrynMathParser.tokenizeInlineMath(raw)
  val computed = buildAnnotatedString {
    for (mathSeg in mathTokens) {
      if (mathSeg.isMath) {
        pushStyle(
          SpanStyle(
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Medium,
            color = linkColor,
          )
        )
        append(mathSeg.content)
        pop()
        continue
      }

      val segment = mathSeg.content
      var cursor = 0
      val matches = INLINE_TOKEN_REGEX.findAll(segment)

      for (match in matches) {
        if (match.range.first > cursor) {
          append(segment.substring(cursor, match.range.first))
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

      if (cursor < segment.length) {
        append(segment.substring(cursor))
      }
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

