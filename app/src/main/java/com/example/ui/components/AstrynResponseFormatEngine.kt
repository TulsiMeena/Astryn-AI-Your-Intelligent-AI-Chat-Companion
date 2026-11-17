package com.example.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import java.util.Locale

/**
 * Semantic content categories recognized by [AstrynResponseFormatEngine].
 * Strictly used to decide how an already-generated response or block should be
 * presented cleanly in Jetpack Compose without forcing rich formatting onto
 * simple conversational replies.
 */
enum class ResponseContentKind {
  PLAIN_TEXT,
  RICH_TEXT,
  COPYABLE_BLOCK,
  CODE_BLOCK,
  TABLE,
  CHART,
  MATH_EQUATION,
  FLOWCHART_DIAGRAM,
  CHECKLIST,
  CALLOUT,
  COMPARISON,
}

enum class CopyBlockCategory(val displayLabel: String) {
  EMAIL_DRAFT("Email Draft"),
  PROMPT_TEMPLATE("Prompt"),
  SOCIAL_CAPTION("Caption"),
  SHELL_COMMAND("Command"),
  CONFIG_OR_JSON("Config"),
  REUSABLE_TEMPLATE("Template"),
  GENERAL_DRAFT("Copyable Draft"),
}

enum class CalloutKind(val defaultTitle: String) {
  NOTE("Note"),
  TIP("Tip"),
  WARNING("Warning"),
  IMPORTANT("Important"),
  PROS("Pros"),
  CONS("Cons"),
  DEFINITION("Definition"),
  SUMMARY("Summary"),
}

enum class TableColumnAlignment {
  LEFT,
  CENTER,
  RIGHT,
}

data class NestedListEntry(
  val indentLevel: Int,
  val text: String,
  val isOrdered: Boolean = false,
  val orderNumber: Int = 1,
)

data class ChecklistEntry(
  val indentLevel: Int = 0,
  val checked: Boolean,
  val text: String,
)

data class CodeLanguageSpec(
  val canonicalId: String,
  val displayName: String,
  val isHighlightedSupported: Boolean,
)

data class StreamingSafePreview(
  val safeMarkdown: String,
  val hasPendingChartBlock: Boolean = false,
  val hasPendingDiagramBlock: Boolean = false,
  val hasPendingMathBlock: Boolean = false,
  val hasPendingTable: Boolean = false,
)

data class StructuredResponseAnalysis(
  val detectedKinds: Set<ResponseContentKind>,
  val hasRichFormatting: Boolean,
  val blockCount: Int,
  val detectedLanguages: List<String>,
  val chartCount: Int,
  val mathBlockCount: Int,
  val diagramCount: Int,
  val copyableBlockCount: Int,
  val tableCount: Int,
  val checklistCount: Int,
  val calloutCount: Int,
  val shouldRenderAsSimplePlainText: Boolean,
)

/**
 * Security & sanitization utilities for rich Markdown text and copyable payloads.
 * - Never interprets raw HTML or executes embedded JavaScript.
 * - Strips dangerous `<script>`, `<style>`, `<iframe>`, `<object>`, `<embed>`, and event handlers
 *   (`onclick=`, `onerror=`, `javascript:`) while keeping comparison math like `a < b` intact.
 */
object AstrynRichTextSanitizer {

  private val DANGEROUS_CONTAINER_REGEX =
    Regex(
      """<(script|style|iframe|object|embed|applet|noscript|form)\b[^>]*>[\s\S]*?</\1\s*>""",
      setOf(RegexOption.IGNORE_CASE),
    )

  private val SELF_CLOSING_DANGEROUS_REGEX =
    Regex(
      """<(script|iframe|object|embed|meta|link|base)\b[^>]*/?\s*>""",
      setOf(RegexOption.IGNORE_CASE),
    )

  private val INLINE_EVENT_ATTR_REGEX =
    Regex(
      """\bon[a-z]+\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+)""",
      setOf(RegexOption.IGNORE_CASE),
    )

  private val SAFE_KNOWN_HTML_TAG_REGEX =
    Regex(
      """</?(?:br|p|div|span|b|strong|i|em|u|s|del|code|pre|sub|sup|mark|small|ul|ol|li|h[1-6]|table|thead|tbody|tr|td|th|a|img|svg|path|hr)\b[^>]*>""",
      setOf(RegexOption.IGNORE_CASE),
    )

  private val HTML_BREAK_REGEX = Regex("""<br\s*/?\s*>""", RegexOption.IGNORE_CASE)
  private val BOLD_TAG_REGEX =
    Regex("""<(b|strong)(?:\s+[^>]*)?>(.*?)</\1\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
  private val ITALIC_TAG_REGEX =
    Regex("""<(i|em)(?:\s+[^>]*)?>(.*?)</\1\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
  private val CODE_TAG_REGEX =
    Regex("""<code(?:\s+[^>]*)?>(.*?)</code>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

  /**
   * Sanitizes a single non-code-block line or segment: strips dangerous HTML/script payloads
   * cleanly and translates benign HTML emphasis tags into safe Markdown while preserving
   * math expressions such as `x < y` or `2 > 1`.
   */
  fun sanitizeDangerousHtml(raw: String): String {
    if (raw.isEmpty() || (!raw.contains('<') && !raw.contains("javascript:", ignoreCase = true))) {
      return raw
    }
    var cleaned = raw
    cleaned = DANGEROUS_CONTAINER_REGEX.replace(cleaned, "")
    cleaned = SELF_CLOSING_DANGEROUS_REGEX.replace(cleaned, "")
    cleaned = INLINE_EVENT_ATTR_REGEX.replace(cleaned, "")
    cleaned = HTML_BREAK_REGEX.replace(cleaned, "\n")
    cleaned = BOLD_TAG_REGEX.replace(cleaned) { "**${it.groupValues[2].trim()}**" }
    cleaned = ITALIC_TAG_REGEX.replace(cleaned) { "*${it.groupValues[2].trim()}*" }
    cleaned = CODE_TAG_REGEX.replace(cleaned) { "`${it.groupValues[1].trim()}`" }
    cleaned = SAFE_KNOWN_HTML_TAG_REGEX.replace(cleaned, "")
    // Strip zero-width invisible bidi/control spoofing chars outside legitimate Hindi/Unicode
    cleaned = cleaned.replace("\u0000", "").replace("\u200E", "").replace("\u202E", "")
    return cleaned
  }

  /**
   * Produces an exact, uncorrupted clipboard payload for copyable blocks:
   * preserves whitespace, indentation, line breaks, Unicode/Devanagari, and symbols
   * without including synthetic UI badges or header labels.
   */
  fun sanitizeClipboardPayload(rawPayload: String): String {
    if (rawPayload.isEmpty()) return ""
    return rawPayload
      .replace("\r\n", "\n")
      .replace("\r", "\n")
      .replace("\u0000", "")
      .trimEnd('\n')
  }
}

/**
 * Pure response-structure & presentation intelligence engine for Phase 16.
 * Inspects content structure cleanly without inventing answers or executing code.
 */
object AstrynResponseFormatEngine {

  private val COPYABLE_FENCE_MAP =
    mapOf(
      "copy" to CopyBlockCategory.GENERAL_DRAFT,
      "copyable" to CopyBlockCategory.GENERAL_DRAFT,
      "email" to CopyBlockCategory.EMAIL_DRAFT,
      "mail" to CopyBlockCategory.EMAIL_DRAFT,
      "prompt" to CopyBlockCategory.PROMPT_TEMPLATE,
      "caption" to CopyBlockCategory.SOCIAL_CAPTION,
      "template" to CopyBlockCategory.REUSABLE_TEMPLATE,
      "draft" to CopyBlockCategory.REUSABLE_TEMPLATE,
      "command" to CopyBlockCategory.SHELL_COMMAND,
      "cmd" to CopyBlockCategory.SHELL_COMMAND,
    )

  private val CALLOUT_TAG_REGEX =
    Regex("""^\[!(NOTE|TIP|WARNING|IMPORTANT|CAUTION|PROS|CONS|DEFINITION|SUMMARY)\]\s*(.*)$""", RegexOption.IGNORE_CASE)

  private val CALLOUT_BOLD_PREFIX_REGEX =
    Regex(
      """^(?:\*\*)?(Note|Tip|Warning|Important|Pros|Cons|Advantages|Disadvantages|Definition|Summary)(?:\*\*)?\s*:\s*(.+)$""",
      setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

  private val COPY_HEADING_HINT_REGEX =
    Regex("""\b(email|subject|template|prompt|caption|bio|message|script|cover letter|bio|tweet|post)\b""", RegexOption.IGNORE_CASE)

  private val NUMERIC_CELL_REGEX =
    Regex("""^[+-]?(?:[₹$€£¥]\s?)?(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d+)?(?:\s?[%xX])?$""")

  private val SUPPORTED_LANGUAGES: Map<String, CodeLanguageSpec> =
    buildMap {
      fun register(id: String, display: String, vararg aliases: String) {
        val spec = CodeLanguageSpec(canonicalId = id, displayName = display, isHighlightedSupported = true)
        put(id, spec)
        aliases.forEach { put(it, spec) }
      }
      register("kotlin", "KOTLIN", "kt", "kts")
      register("java", "JAVA")
      register("python", "PYTHON", "py", "python3")
      register("javascript", "JAVASCRIPT", "js", "jsx", "mjs", "cjs")
      register("typescript", "TYPESCRIPT", "ts", "tsx")
      register("html", "HTML", "htm")
      register("css", "CSS", "scss", "sass")
      register("sql", "SQL", "mysql", "postgresql", "postgres", "sqlite")
      register("json", "JSON", "jsonc")
      register("xml", "XML", "svg", "plist")
      register("yaml", "YAML", "yml")
      register("c", "C")
      register("cpp", "C++", "c++", "cc", "cxx", "hpp", "h")
      register("bash", "BASH", "sh", "zsh", "shell", "console", "terminal")
    }

  fun normalizeCodeLanguage(rawLang: String): CodeLanguageSpec {
    val key = rawLang.trim().lowercase(Locale.ROOT)
    SUPPORTED_LANGUAGES[key]?.let { return it }
    val display = if (key.isBlank() || key == "code") "CODE" else key.uppercase(Locale.ROOT)
    return CodeLanguageSpec(
      canonicalId = key.ifBlank { "plain" },
      displayName = display,
      isHighlightedSupported = false,
    )
  }

  fun detectCopyableFenceCategory(
    fenceLanguage: String,
    body: String,
    precedingHeading: String? = null,
  ): CopyBlockCategory? {
    val lang = fenceLanguage.trim().lowercase(Locale.ROOT)
    COPYABLE_FENCE_MAP[lang]?.let { return it }

    // Recognize fenced `text`/`markdown` drafts ONLY when clearly framed as an email/prompt/template/caption
    if (lang == "text" || lang == "txt" || lang == "plaintext") {
      val trimmed = body.trim()
      val looksLikeEmail =
        trimmed.startsWith("Subject:", ignoreCase = true) ||
          (trimmed.contains("Subject:", ignoreCase = true) && trimmed.contains("Dear ", ignoreCase = true))
      if (looksLikeEmail) return CopyBlockCategory.EMAIL_DRAFT
      if (!precedingHeading.isNullOrBlank() && COPY_HEADING_HINT_REGEX.containsMatchIn(precedingHeading)) {
        return CopyBlockCategory.REUSABLE_TEMPLATE
      }
      if (trimmed.contains("{{") && trimmed.contains("}}")) {
        return CopyBlockCategory.PROMPT_TEMPLATE
      }
    }
    return null
  }

  fun detectCalloutFromQuoteLines(lines: List<String>): Triple<CalloutKind, String, String>? {
    if (lines.isEmpty()) return null
    val first = lines.first().trim()
    val tagMatch = CALLOUT_TAG_REGEX.matchEntire(first)
    if (tagMatch != null) {
      val kind =
        when (tagMatch.groupValues[1].uppercase(Locale.ROOT)) {
          "TIP" -> CalloutKind.TIP
          "WARNING", "CAUTION" -> CalloutKind.WARNING
          "IMPORTANT" -> CalloutKind.IMPORTANT
          "PROS" -> CalloutKind.PROS
          "CONS" -> CalloutKind.CONS
          "DEFINITION" -> CalloutKind.DEFINITION
          "SUMMARY" -> CalloutKind.SUMMARY
          else -> CalloutKind.NOTE
        }
      val restFirst = tagMatch.groupValues[2].trim()
      val bodyLines =
        buildList {
          if (restFirst.isNotEmpty()) add(restFirst)
          lines.drop(1).forEach { if (it.isNotBlank()) add(it.trim()) }
        }
      if (bodyLines.isNotEmpty()) {
        return Triple(kind, kind.defaultTitle, bodyLines.joinToString("\n"))
      }
    }

    val joined = lines.joinToString("\n").trim()
    val boldMatch = CALLOUT_BOLD_PREFIX_REGEX.matchEntire(joined)
    if (boldMatch != null && (joined.startsWith("**") || first.startsWith("Warning:", ignoreCase = true))) {
      val label = boldMatch.groupValues[1]
      val kind =
        when (label.lowercase(Locale.ROOT)) {
          "tip" -> CalloutKind.TIP
          "warning" -> CalloutKind.WARNING
          "important" -> CalloutKind.IMPORTANT
          "pros", "advantages" -> CalloutKind.PROS
          "cons", "disadvantages" -> CalloutKind.CONS
          "definition" -> CalloutKind.DEFINITION
          "summary" -> CalloutKind.SUMMARY
          else -> CalloutKind.NOTE
        }
      val body = boldMatch.groupValues[2].trim()
      if (body.isNotEmpty()) {
        return Triple(kind, label.replaceFirstChar { it.titlecase(Locale.ROOT) }, body)
      }
    }
    return null
  }

  fun parseTableAlignments(dividerLine: String, columnCount: Int): List<TableColumnAlignment> {
    if (columnCount <= 0) return emptyList()
    val cells =
      dividerLine.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }
    return (0 until columnCount).map { idx ->
      val spec = cells.getOrNull(idx).orEmpty()
      val left = spec.startsWith(":")
      val right = spec.endsWith(":")
      when {
        left && right -> TableColumnAlignment.CENTER
        right -> TableColumnAlignment.RIGHT
        else -> TableColumnAlignment.LEFT
      }
    }
  }

  fun isNumericCell(value: String): Boolean {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return false
    return NUMERIC_CELL_REGEX.matches(trimmed)
  }

  fun buildAdaptiveFormattingHint(userPrompt: String): String {
    val lower = userPrompt.lowercase(Locale.ROOT)
    if (lower.isBlank()) return ""
    val hints = mutableListOf<String>()

    if (Regex("""\b(chart|graph|bar chart|line chart|pie chart|plot|trend|visuali[sz]e)\b""").containsMatchIn(lower)) {
      hints.add(
        "If charting verified/provided numeric data, emit a ```chart JSON block: {\"title\":\"...\",\"type\":\"bar|line|pie\",\"labels\":[...],\"values\":[...],\"unit\":\"...\",\"provenance\":\"observed|calculated|forecast\"}. Never invent numbers."
      )
    }
    if (Regex("""\b(flowchart|diagram|workflow|architecture flow|sequence|process flow|mermaid)\b""").containsMatchIn(lower)) {
      hints.add(
        "If a visual flowchart clarifies steps or decisions, emit a ```mermaid block starting with flowchart TD or flowchart LR."
      )
    }
    if (Regex("""\b(email|draft|template|prompt|caption|cover letter|bio|copyable)\b""").containsMatchIn(lower)) {
      hints.add(
        "When providing a standalone ready-to-use email, caption, template, or prompt, wrap that exact reusable text in ```copy ... ```."
      )
    }
    if (Regex("""\b(formula|equation|derive|integral|summation|matrix|latex|quadratic)\b""").containsMatchIn(lower)) {
      hints.add(
        "Format multi-step or complex equations using LaTeX $$...$$ blocks and inline math with \$...\$."
      )
    }
    return hints.joinToString(" ")
  }

  fun resolveEffectiveTableAlignments(
    explicitAlignments: List<TableColumnAlignment>,
    headers: List<String>,
    rows: List<List<String>>,
  ): List<TableColumnAlignment> {
    return headers.indices.map { colIdx ->
      val explicit = explicitAlignments.getOrNull(colIdx)
      if (explicit != null && explicit != TableColumnAlignment.LEFT) {
        return@map explicit
      }
      // If column wasn't forced center/right via markdown divider, auto right-align pure numeric columns
      val nonEmptyCells = rows.mapNotNull { row -> row.getOrNull(colIdx)?.trim()?.takeIf { it.isNotEmpty() } }
      if (nonEmptyCells.isNotEmpty() && nonEmptyCells.all { isNumericCell(it) }) {
        TableColumnAlignment.RIGHT
      } else {
        explicit ?: TableColumnAlignment.LEFT
      }
    }
  }

  /**
   * Safeguards partial SSE streaming responses so incomplete charts, Mermaid flowcharts,
   * LaTeX display math blocks, or 1-line table headers render gracefully as plain/code
   * text until the closing delimiter arrives, avoiding visual flicker or parse errors.
   */
  fun buildStreamingSafePreview(rawStreaming: String): StreamingSafePreview {
    if (rawStreaming.isEmpty()) return StreamingSafePreview(safeMarkdown = "")
    val normalized = rawStreaming.replace("\r\n", "\n").replace("\r", "\n")
    val lines = normalized.split("\n")

    var insideFence = false
    var fenceMarker = ""
    var fenceLang = ""
    var fenceStartIdx = -1

    for (idx in lines.indices) {
      val trimmed = lines[idx].trim()
      if (!insideFence && (trimmed.startsWith("```") || trimmed.startsWith("~~~"))) {
        insideFence = true
        fenceMarker = if (trimmed.startsWith("```")) "```" else "~~~"
        fenceLang = trimmed.removePrefix(fenceMarker).trim().lowercase(Locale.ROOT)
        fenceStartIdx = idx
      } else if (insideFence && trimmed.startsWith(fenceMarker)) {
        insideFence = false
        fenceMarker = ""
        fenceLang = ""
        fenceStartIdx = -1
      }
    }

    var pendingChart = false
    var pendingDiagram = false
    var pendingMath = false
    var safeText = normalized

    // If an interactive chart/mermaid/math block fence is still unclosed while streaming,
    // downgrade its fence tag temporarily to `text` so it streams smoothly without broken visual placeholders.
    if (insideFence && fenceStartIdx in lines.indices) {
      when (fenceLang) {
        "chart", "astryn-chart", "graph-data" -> {
          pendingChart = true
          val mutable = lines.toMutableList()
          mutable[fenceStartIdx] = "${fenceMarker}text"
          safeText = mutable.joinToString("\n")
        }
        "mermaid", "flowchart", "diagram" -> {
          pendingDiagram = true
          val mutable = lines.toMutableList()
          mutable[fenceStartIdx] = "${fenceMarker}text"
          safeText = mutable.joinToString("\n")
        }
        "math", "latex", "tex" -> {
          pendingMath = true
          val mutable = lines.toMutableList()
          mutable[fenceStartIdx] = "${fenceMarker}text"
          safeText = mutable.joinToString("\n")
        }
      }
    }

    // Check unclosed $$ block delimiter outside code fences
    val dollarFenceCount = Regex("""(?m)^\s*\$\$""").findAll(safeText).count()
    if (dollarFenceCount % 2 != 0) {
      pendingMath = true
    }

    // Check incomplete single pipe line at tail before divider arrives
    val lastTrimmed = lines.lastOrNull()?.trim().orEmpty()
    val pendingTable =
      !insideFence &&
        lastTrimmed.contains('|') &&
        lines.size >= 2 &&
        !lines[lines.lastIndex - 1].contains('|')

    return StreamingSafePreview(
      safeMarkdown = safeText,
      hasPendingChartBlock = pendingChart,
      hasPendingDiagramBlock = pendingDiagram,
      hasPendingMathBlock = pendingMath,
      hasPendingTable = pendingTable,
    )
  }

  /**
   * Analyzes parsed blocks to classify present rich formats and determine whether a reply
   * is genuinely conversational plain text vs structured rich content.
   */
  fun analyzeResponse(content: String, userQueryHint: String? = null): StructuredResponseAnalysis {
    if (content.isBlank()) {
      return StructuredResponseAnalysis(
        detectedKinds = setOf(ResponseContentKind.PLAIN_TEXT),
        hasRichFormatting = false,
        blockCount = 0,
        detectedLanguages = emptyList(),
        chartCount = 0,
        mathBlockCount = 0,
        diagramCount = 0,
        copyableBlockCount = 0,
        tableCount = 0,
        checklistCount = 0,
        calloutCount = 0,
        shouldRenderAsSimplePlainText = true,
      )
    }

    val blocks = SafeMarkdownParser.parseBlocks(content, cacheResult = false)
    val kinds = linkedSetOf<ResponseContentKind>()
    val languages = linkedSetOf<String>()
    var charts = 0
    var mathBlocks = 0
    var diagrams = 0
    var copyables = 0
    var tables = 0
    var checklists = 0
    var callouts = 0

    for (block in blocks) {
      when (block) {
        is MarkdownBlock.Heading -> kinds.add(ResponseContentKind.RICH_TEXT)
        is MarkdownBlock.BulletList -> kinds.add(ResponseContentKind.RICH_TEXT)
        is MarkdownBlock.NestedBulletList -> kinds.add(ResponseContentKind.RICH_TEXT)
        is MarkdownBlock.OrderedList -> kinds.add(ResponseContentKind.RICH_TEXT)
        is MarkdownBlock.Blockquote -> kinds.add(ResponseContentKind.RICH_TEXT)
        is MarkdownBlock.HorizontalRule -> kinds.add(ResponseContentKind.RICH_TEXT)
        is MarkdownBlock.Paragraph -> {
          if (AstrynMathParser.containsInlineMath(block.text)) {
            kinds.add(ResponseContentKind.MATH_EQUATION)
            mathBlocks++
          }
          if (block.text.contains("**") || block.text.contains("`") || block.text.contains("](")) {
            kinds.add(ResponseContentKind.RICH_TEXT)
          }
        }
        is MarkdownBlock.CodeBlock -> {
          kinds.add(ResponseContentKind.CODE_BLOCK)
          languages.add(normalizeCodeLanguage(block.language).canonicalId)
        }
        is MarkdownBlock.CopyableBlock -> {
          kinds.add(ResponseContentKind.COPYABLE_BLOCK)
          copyables++
        }
        is MarkdownBlock.Table -> {
          kinds.add(ResponseContentKind.TABLE)
          if (block.headers.any { h ->
              h.contains("vs", ignoreCase = true) ||
                h.contains("pro", ignoreCase = true) ||
                h.contains("feature", ignoreCase = true) ||
                h.contains("option", ignoreCase = true)
            } || userQueryHint?.contains("compare", ignoreCase = true) == true
          ) {
            kinds.add(ResponseContentKind.COMPARISON)
          }
          tables++
        }
        is MarkdownBlock.Chart -> {
          kinds.add(ResponseContentKind.CHART)
          charts++
        }
        is MarkdownBlock.MathFormula -> {
          kinds.add(ResponseContentKind.MATH_EQUATION)
          mathBlocks++
        }
        is MarkdownBlock.Diagram -> {
          kinds.add(ResponseContentKind.FLOWCHART_DIAGRAM)
          diagrams++
        }
        is MarkdownBlock.Checklist -> {
          kinds.add(ResponseContentKind.CHECKLIST)
          checklists++
        }
        is MarkdownBlock.Callout -> {
          kinds.add(ResponseContentKind.CALLOUT)
          if (block.kind == CalloutKind.PROS || block.kind == CalloutKind.CONS) {
            kinds.add(ResponseContentKind.COMPARISON)
          }
          callouts++
        }
      }
    }

    if (kinds.isEmpty()) {
      kinds.add(ResponseContentKind.PLAIN_TEXT)
    }

    val onlyPlainParagraphs =
      blocks.all { it is MarkdownBlock.Paragraph } &&
        !kinds.contains(ResponseContentKind.MATH_EQUATION) &&
        !kinds.contains(ResponseContentKind.RICH_TEXT)

    return StructuredResponseAnalysis(
      detectedKinds = kinds,
      hasRichFormatting = !onlyPlainParagraphs,
      blockCount = blocks.size,
      detectedLanguages = languages.toList(),
      chartCount = charts,
      mathBlockCount = mathBlocks,
      diagramCount = diagrams,
      copyableBlockCount = copyables,
      tableCount = tables,
      checklistCount = checklists,
      calloutCount = callouts,
      shouldRenderAsSimplePlainText = onlyPlainParagraphs,
    )
  }

  /**
   * Language-aware code syntax highlighter supporting:
   * Kotlin, Java, Python, JavaScript, TypeScript, HTML, CSS, SQL, JSON, XML, YAML, C, C++, Bash,
   * with clean plain-text fallback for unknown/unsupported languages.
   */
  fun highlightCodeForLanguage(
    code: String,
    language: String,
    isDark: Boolean,
    defaultTextColor: Color,
  ): AnnotatedString {
    if (code.isEmpty()) return AnnotatedString("")
    val spec = normalizeCodeLanguage(language)
    if (!spec.isHighlightedSupported && language.isNotBlank() && language != "code") {
      // Unsupported language -> safe, crisp plain text without brittle guessing
      return AnnotatedString(code)
    }

    val cacheKey = "${spec.canonicalId}|$isDark|${defaultTextColor.value}|$code"
    SafeMarkdownParser.getCachedCode(cacheKey)?.let { return it }

    val palette = SyntaxPalette.forTheme(isDark, defaultTextColor)
    val highlighted =
      when (spec.canonicalId) {
        "json" -> highlightJson(code, palette)
        "html", "xml" -> highlightMarkup(code, palette)
        "css" -> highlightCss(code, palette)
        "sql" -> highlightSql(code, palette)
        "yaml" -> highlightYaml(code, palette)
        "bash" -> highlightBash(code, palette)
        else -> {
          val keywordRegex =
            when (spec.canonicalId) {
              "python" -> PYTHON_TOKEN_REGEX
              "javascript", "typescript" -> JS_TS_TOKEN_REGEX
              "c", "cpp" -> C_CPP_TOKEN_REGEX
              else -> JVM_TOKEN_REGEX
            }
          highlightGenericTokens(code, keywordRegex, palette)
        }
      }

    SafeMarkdownParser.putCachedCode(cacheKey, highlighted)
    return highlighted
  }

  private data class SyntaxPalette(
    val defaultText: Color,
    val comment: Color,
    val string: Color,
    val keyword: Color,
    val annotationOrKey: Color,
    val number: Color,
    val tagOrType: Color,
  ) {
    companion object {
      fun forTheme(isDark: Boolean, defaultText: Color): SyntaxPalette =
        if (isDark) {
          SyntaxPalette(
            defaultText = defaultText,
            comment = Color(0xFF64748B),
            string = Color(0xFF34D399),
            keyword = Color(0xFF818CF8),
            annotationOrKey = Color(0xFFFBBF24),
            number = Color(0xFF38BDF8),
            tagOrType = Color(0xFFF472B6),
          )
        } else {
          SyntaxPalette(
            defaultText = defaultText,
            comment = Color(0xFF64748B),
            string = Color(0xFF047857),
            keyword = Color(0xFF4F46E5),
            annotationOrKey = Color(0xFFB45309),
            number = Color(0xFF0284C7),
            tagOrType = Color(0xFFBE185D),
          )
        }
    }
  }

  private fun highlightGenericTokens(
    code: String,
    regex: Regex,
    palette: SyntaxPalette,
  ): AnnotatedString = buildAnnotatedString {
    pushStyle(SpanStyle(color = palette.defaultText))
    var cursor = 0
    for (match in regex.findAll(code)) {
      if (match.range.first > cursor) append(code.substring(cursor, match.range.first))
      val style =
        when {
          match.groups[1] != null -> SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic)
          match.groups[2] != null -> SpanStyle(color = palette.string)
          match.groups[5] != null -> SpanStyle(color = palette.annotationOrKey)
          match.groups[6] != null -> SpanStyle(color = palette.keyword, fontWeight = FontWeight.SemiBold)
          match.groups[7] != null -> SpanStyle(color = palette.number)
          else -> SpanStyle(color = palette.defaultText)
        }
      pushStyle(style)
      append(match.value)
      pop()
      cursor = match.range.last + 1
    }
    if (cursor < code.length) append(code.substring(cursor))
    pop()
  }

  private fun highlightJson(code: String, palette: SyntaxPalette): AnnotatedString =
    buildAnnotatedString {
      pushStyle(SpanStyle(color = palette.defaultText))
      var cursor = 0
      for (match in JSON_TOKEN_REGEX.findAll(code)) {
        if (match.range.first > cursor) append(code.substring(cursor, match.range.first))
        val style =
          when {
            match.groups[1] != null -> SpanStyle(color = palette.annotationOrKey, fontWeight = FontWeight.Medium)
            match.groups[2] != null -> SpanStyle(color = palette.string)
            match.groups[3] != null -> SpanStyle(color = palette.keyword, fontWeight = FontWeight.SemiBold)
            match.groups[4] != null -> SpanStyle(color = palette.number)
            else -> SpanStyle(color = palette.defaultText)
          }
        pushStyle(style)
        append(match.value)
        pop()
        cursor = match.range.last + 1
      }
      if (cursor < code.length) append(code.substring(cursor))
      pop()
    }

  private fun highlightMarkup(code: String, palette: SyntaxPalette): AnnotatedString =
    buildAnnotatedString {
      pushStyle(SpanStyle(color = palette.defaultText))
      var cursor = 0
      for (match in MARKUP_TOKEN_REGEX.findAll(code)) {
        if (match.range.first > cursor) append(code.substring(cursor, match.range.first))
        val style =
          when {
            match.groups[1] != null -> SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic)
            match.groups[2] != null -> SpanStyle(color = palette.tagOrType, fontWeight = FontWeight.SemiBold)
            match.groups[3] != null -> SpanStyle(color = palette.string)
            match.groups[4] != null -> SpanStyle(color = palette.annotationOrKey)
            else -> SpanStyle(color = palette.defaultText)
          }
        pushStyle(style)
        append(match.value)
        pop()
        cursor = match.range.last + 1
      }
      if (cursor < code.length) append(code.substring(cursor))
      pop()
    }

  private fun highlightCss(code: String, palette: SyntaxPalette): AnnotatedString =
    buildAnnotatedString {
      pushStyle(SpanStyle(color = palette.defaultText))
      var cursor = 0
      for (match in CSS_TOKEN_REGEX.findAll(code)) {
        if (match.range.first > cursor) append(code.substring(cursor, match.range.first))
        val style =
          when {
            match.groups[1] != null -> SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic)
            match.groups[2] != null -> SpanStyle(color = palette.string)
            match.groups[3] != null -> SpanStyle(color = palette.keyword, fontWeight = FontWeight.SemiBold)
            match.groups[4] != null -> SpanStyle(color = palette.number)
            else -> SpanStyle(color = palette.defaultText)
          }
        pushStyle(style)
        append(match.value)
        pop()
        cursor = match.range.last + 1
      }
      if (cursor < code.length) append(code.substring(cursor))
      pop()
    }

  private fun highlightSql(code: String, palette: SyntaxPalette): AnnotatedString =
    buildAnnotatedString {
      pushStyle(SpanStyle(color = palette.defaultText))
      var cursor = 0
      for (match in SQL_TOKEN_REGEX.findAll(code)) {
        if (match.range.first > cursor) append(code.substring(cursor, match.range.first))
        val style =
          when {
            match.groups[1] != null -> SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic)
            match.groups[2] != null -> SpanStyle(color = palette.string)
            match.groups[3] != null -> SpanStyle(color = palette.keyword, fontWeight = FontWeight.Bold)
            match.groups[4] != null -> SpanStyle(color = palette.number)
            else -> SpanStyle(color = palette.defaultText)
          }
        pushStyle(style)
        append(match.value)
        pop()
        cursor = match.range.last + 1
      }
      if (cursor < code.length) append(code.substring(cursor))
      pop()
    }

  private fun highlightYaml(code: String, palette: SyntaxPalette): AnnotatedString =
    buildAnnotatedString {
      pushStyle(SpanStyle(color = palette.defaultText))
      var cursor = 0
      for (match in YAML_TOKEN_REGEX.findAll(code)) {
        if (match.range.first > cursor) append(code.substring(cursor, match.range.first))
        val style =
          when {
            match.groups[1] != null -> SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic)
            match.groups[2] != null -> SpanStyle(color = palette.annotationOrKey, fontWeight = FontWeight.SemiBold)
            match.groups[3] != null -> SpanStyle(color = palette.string)
            match.groups[4] != null -> SpanStyle(color = palette.keyword, fontWeight = FontWeight.SemiBold)
            match.groups[5] != null -> SpanStyle(color = palette.number)
            else -> SpanStyle(color = palette.defaultText)
          }
        pushStyle(style)
        append(match.value)
        pop()
        cursor = match.range.last + 1
      }
      if (cursor < code.length) append(code.substring(cursor))
      pop()
    }

  private fun highlightBash(code: String, palette: SyntaxPalette): AnnotatedString =
    buildAnnotatedString {
      pushStyle(SpanStyle(color = palette.defaultText))
      var cursor = 0
      for (match in BASH_TOKEN_REGEX.findAll(code)) {
        if (match.range.first > cursor) append(code.substring(cursor, match.range.first))
        val style =
          when {
            match.groups[1] != null -> SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic)
            match.groups[2] != null -> SpanStyle(color = palette.string)
            match.groups[3] != null -> SpanStyle(color = palette.annotationOrKey)
            match.groups[4] != null -> SpanStyle(color = palette.keyword, fontWeight = FontWeight.SemiBold)
            match.groups[5] != null -> SpanStyle(color = palette.number)
            else -> SpanStyle(color = palette.defaultText)
          }
        pushStyle(style)
        append(match.value)
        pop()
        cursor = match.range.last + 1
      }
      if (cursor < code.length) append(code.substring(cursor))
      pop()
    }

  private val JVM_TOKEN_REGEX =
    Regex(
      """(//[^\n]*|/\*[\s\S]*?\*/)|("([^"\\]|\\.)*"|'([^'\\]|\\.)*')|(@[A-Za-z_][A-Za-z0-9_]*)|\b(fun|val|var|class|object|interface|return|if|else|when|for|while|do|import|package|suspend|override|private|public|internal|protected|data|sealed|enum|companion|true|false|null|try|catch|finally|throw|new|void|int|long|double|float|boolean|char|final|static|abstract|extends|implements|String|List|Map|Set|Flow|StateFlow)\b|\b(\d+(?:\.\d+)?[fFlL]?)\b"""
    )

  private val PYTHON_TOKEN_REGEX =
    Regex(
      """(#[^\n]*)|(""" + "\"\"\"[\\s\\S]*?\"\"\"" + """|"([^"\\]|\\.)*"|'([^'\\]|\\.)*')|(@[A-Za-z_][A-Za-z0-9_.]*)|\b(def|class|return|if|elif|else|for|while|in|not|and|or|is|import|from|as|with|try|except|finally|raise|pass|break|continue|lambda|yield|async|await|True|False|None|self|cls|print|len|range)\b|\b(\d+(?:\.\d+)?)\b"""
    )

  private val JS_TS_TOKEN_REGEX =
    Regex(
      """(//[^\n]*|/\*[\s\S]*?\*/)|("([^"\\]|\\.)*"|'([^'\\]|\\.)*'|`[^`]*`)|(@[A-Za-z_][A-Za-z0-9_]*)|\b(const|let|var|function|return|if|else|switch|case|for|while|import|export|from|default|class|interface|type|extends|implements|enum|async|await|try|catch|finally|throw|new|typeof|instanceof|true|false|null|undefined|string|number|boolean|Promise)\b|\b(\d+(?:\.\d+)?)\b"""
    )

  private val C_CPP_TOKEN_REGEX =
    Regex(
      """(//[^\n]*|/\*[\s\S]*?\*/)|("([^"\\]|\\.)*"|'([^'\\]|\\.)*')|(#(?:include|define|ifdef|ifndef|endif|pragma)\b[^\n]*)|\b(int|long|short|char|float|double|void|bool|const|static|struct|class|public|private|protected|virtual|override|template|typename|namespace|using|return|if|else|switch|case|for|while|do|break|continue|sizeof|nullptr|true|false|std|vector|string|auto)\b|\b(\d+(?:\.\d+)?[uUlLfF]?)\b"""
    )

  private val JSON_TOKEN_REGEX =
    Regex("""("([^"\\]|\\.)*")(?=\s*:)|("([^"\\]|\\.)*")|\b(true|false|null)\b|(-?\b\d+(?:\.\d+)?(?:[eE][+-]?\d+)?\b)""")

  private val MARKUP_TOKEN_REGEX =
    Regex("""(<!--[\s\S]*?-->)|(</?[A-Za-z][A-Za-z0-9:-]*|/?>)|("[^"]*"|'[^']*')|\b([A-Za-z_:][A-Za-z0-9_:-]*)(?=\s*=)""")

  private val CSS_TOKEN_REGEX =
    Regex("""(/\*[\s\S]*?\*/)|("[^"]*"|'[^']*')|([a-z-]+(?=\s*:)|@[a-z-]+|!important)\b|(#[0-9A-Fa-f]{3,8}\b|\b\d+(?:\.\d+)?(?:px|rem|em|%|vh|vw|dp|s|ms)?\b)""")

  private val SQL_TOKEN_REGEX =
    Regex(
      """(--[^\n]*|/\*[\s\S]*?\*/)|('([^']|'')*')|\b(SELECT|FROM|WHERE|INSERT|INTO|VALUES|UPDATE|SET|DELETE|CREATE|TABLE|ALTER|DROP|JOIN|INNER|LEFT|RIGHT|ON|GROUP|BY|ORDER|ASC|DESC|HAVING|LIMIT|AS|AND|OR|NOT|NULL|IS|IN|EXISTS|COUNT|SUM|AVG|DISTINCT|PRIMARY|KEY)\b|(\b\d+(?:\.\d+)?\b)""",
      setOf(RegexOption.IGNORE_CASE),
    )

  private val YAML_TOKEN_REGEX =
    Regex("""(#[^\n]*)|(^|\n\s*)([A-Za-z0-9_.-]+(?=\s*:))|("[^"]*"|'[^']*')|\b(true|false|yes|no|null)\b|\b(\d+(?:\.\d+)?)\b""")

  private val BASH_TOKEN_REGEX =
    Regex("""(#[^\n]*)|("([^"\\]|\\.)*"|'[^']*')|(\$\{?[A-Za-z_][A-Za-z0-9_]*\}?)|\b(if|then|else|elif|fi|for|in|do|done|while|case|esac|function|export|local|echo|cd|ls|grep|awk|sed|curl|git|gradle|./gradlew|npm|python|sudo|chmod|mkdir|rm|cp|mv|cat|exit|return)\b|\b(\d+)\b""")
}
