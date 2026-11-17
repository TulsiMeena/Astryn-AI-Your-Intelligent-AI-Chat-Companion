package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppBorder
import com.example.ui.theme.AppSpacing
import com.example.ui.theme.AppThemeTokens
import kotlinx.coroutines.delay

enum class MatrixDelimiter {
  PLAIN,
  PAREN,
  BRACKET,
  BARS,
}

@Immutable
sealed class MathNode {
  @Immutable data class Text(val value: String) : MathNode()
  @Immutable data class Fraction(val numerator: List<MathNode>, val denominator: List<MathNode>) : MathNode()
  @Immutable data class Sqrt(val radicand: List<MathNode>, val index: List<MathNode>? = null) : MathNode()
  @Immutable data class Script(
    val base: List<MathNode>,
    val superscript: List<MathNode>? = null,
    val subscript: List<MathNode>? = null,
  ) : MathNode()
  @Immutable data class Matrix(
    val rows: List<List<List<MathNode>>>,
    val delimiter: MatrixDelimiter,
  ) : MathNode()
  @Immutable data class OperatorBig(
    val symbol: String,
    val lower: List<MathNode>? = null,
    val upper: List<MathNode>? = null,
  ) : MathNode()
}

@Immutable
sealed class MathParseResult {
  @Immutable data class Parsed(val nodes: List<MathNode>, val rawLatex: String) : MathParseResult()
  @Immutable data class Fallback(val readableText: String, val rawLatex: String) : MathParseResult()
}

data class InlineMathToken(val content: String, val isMath: Boolean)

/**
 * LaTeX-compatible mathematical formula parser supporting:
 * - Fractions (`\frac`, `\dfrac`)
 * - Roots (`\sqrt{...}`, `\sqrt[n]{...}`)
 * - Powers & subscripts (`x^2`, `x^{n+1}`, `H_2O`, `a_{ij}`)
 * - Summations, integrals, products, limits (`\sum`, `\int`, `\prod`, `\lim`)
 * - Matrices (`\begin{pmatrix}...\end{pmatrix}`, `bmatrix`, `vmatrix`, `matrix`)
 * - Greek letters, scientific notation, and safe currency preservation (`$50` stays currency).
 */
object AstrynMathParser {

  private val LATEX_COMMANDS: Map<String, String> =
    mapOf(
      "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "epsilon" to "ε",
      "varepsilon" to "ε", "zeta" to "ζ", "eta" to "η", "theta" to "θ", "iota" to "ι",
      "kappa" to "κ", "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ",
      "pi" to "π", "rho" to "ρ", "sigma" to "σ", "tau" to "τ", "upsilon" to "υ",
      "phi" to "φ", "varphi" to "ϕ", "chi" to "χ", "psi" to "ψ", "omega" to "ω",
      "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ", "Sigma" to "Σ",
      "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω",
      "infty" to "∞", "approx" to "≈", "neq" to "≠", "ne" to "≠", "leq" to "≤",
      "le" to "≤", "geq" to "≥", "ge" to "≥", "pm" to "±", "mp" to "∓",
      "times" to "×", "div" to "÷", "cdot" to "·", "to" to "→", "rightarrow" to "→",
      "leftarrow" to "←", "Rightarrow" to "⇒", "leftrightarrow" to "↔",
      "forall" to "∀", "exists" to "∃", "in" to "∈", "notin" to "∉",
      "subset" to "⊂", "subseteq" to "⊆", "cup" to "∪", "cap" to "∩",
      "partial" to "∂", "nabla" to "∇", "degree" to "°", "angle" to "∠",
      "sin" to "sin", "cos" to "cos", "tan" to "tan", "sec" to "sec", "csc" to "csc",
      "cot" to "cot", "log" to "log", "ln" to "ln", "exp" to "exp",
      "quad" to "  ", "qquad" to "    ", "," to " ", ";" to " ", " " to " ",
      "left" to "", "right" to "", "text" to "", "mathrm" to "", "mathbf" to "",
    )

  private val SUPERSCRIPT_MAP =
    mapOf(
      '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
      '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
      '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
      'n' to 'ⁿ', 'i' to 'ⁱ',
    )

  private val SUBSCRIPT_MAP =
    mapOf(
      '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
      '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
      '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
      'i' to 'ᵢ', 'j' to 'ⱼ', 'n' to 'ₙ', 'k' to 'ₖ', 'x' to 'ₓ',
    )

  private val INLINE_MATH_REGEX =
    Regex("""\\\((.+?)\\\)|\$([^$\n]+?)\$""")

  fun containsInlineMath(text: String): Boolean {
    if (!text.contains('$') && !text.contains("\\(")) return false
    return tokenizeInlineMath(text).any { it.isMath }
  }

  /**
   * Splits inline text into plain segments vs math segments while carefully ignoring
   * currency amounts such as `$50` or `$50 to $100`.
   */
  fun tokenizeInlineMath(raw: String): List<InlineMathToken> {
    if (raw.isEmpty() || (!raw.contains('$') && !raw.contains("\\("))) {
      return listOf(InlineMathToken(raw, isMath = false))
    }
    val tokens = mutableListOf<InlineMathToken>()
    var cursor = 0
    for (match in INLINE_MATH_REGEX.findAll(raw)) {
      val parenBody = match.groups[1]?.value
      val dollarBody = match.groups[2]?.value
      val isRealMath =
        when {
          parenBody != null -> parenBody.isNotBlank()
          dollarBody != null -> isLikelyInlineMathExpression(dollarBody)
          else -> false
        }
      if (!isRealMath) continue

      if (match.range.first > cursor) {
        tokens.add(InlineMathToken(raw.substring(cursor, match.range.first), isMath = false))
      }
      val body = (parenBody ?: dollarBody ?: "").trim()
      tokens.add(InlineMathToken(convertLatexToReadableUnicode(body), isMath = true))
      cursor = match.range.last + 1
    }
    if (cursor < raw.length) {
      tokens.add(InlineMathToken(raw.substring(cursor), isMath = false))
    }
    return if (tokens.isEmpty()) listOf(InlineMathToken(raw, isMath = false)) else tokens
  }

  private fun isLikelyInlineMathExpression(body: String): Boolean {
    val trimmed = body.trim()
    if (trimmed.isEmpty() || trimmed.length > 160) return false
    // Currency rejection: starts with digits/comma/dot & contains spaces/currency prose like "$50 and $100"
    if (trimmed.first().isDigit() && !trimmed.contains('\\') && !trimmed.contains('^') &&
      !trimmed.contains('_') && !trimmed.contains('=') && !trimmed.contains('+') &&
      !trimmed.contains('/') && !trimmed.contains('*')
    ) {
      return false
    }
    if (trimmed.contains(" and ", ignoreCase = true) || trimmed.contains(" or ", ignoreCase = true)) {
      return false
    }
    // Single letter/variable like $x$, $E=mc^2$, $\frac{1}{2}$
    return trimmed.contains('\\') ||
      trimmed.contains('^') ||
      trimmed.contains('_') ||
      trimmed.contains('=') ||
      trimmed.contains('+') ||
      trimmed.contains('/') ||
      trimmed.length <= 2
  }

  /**
   * Converts inline LaTeX syntax to clean Unicode math typography (fractions -> `(a/b)` or `a/b`,
   * roots -> `√(...)`, powers -> superscripts, Greek symbols -> Unicode glyphs).
   */
  fun convertLatexToReadableUnicode(rawLatex: String): String {
    val result = parseDisplayFormula(rawLatex)
    return when (result) {
      is MathParseResult.Parsed -> nodesToReadableUnicode(result.nodes)
      is MathParseResult.Fallback -> result.readableText
    }
  }

  fun parseDisplayFormula(rawLatex: String): MathParseResult {
    val cleaned =
      rawLatex
        .trim()
        .removePrefix("$$")
        .removeSuffix("$$")
        .removePrefix("\\[")
        .removeSuffix("\\]")
        .trim()
    if (cleaned.isEmpty()) {
      return MathParseResult.Fallback(readableText = "", rawLatex = rawLatex)
    }

    return try {
      val reader = LatexReader(cleaned)
      val nodes = reader.parseSequence(stopAt = null)
      if (nodes.isEmpty()) {
        MathParseResult.Fallback(fallbackCleanUnicode(cleaned), rawLatex)
      } else {
        MathParseResult.Parsed(nodes = nodes, rawLatex = cleaned)
      }
    } catch (_: Exception) {
      MathParseResult.Fallback(fallbackCleanUnicode(cleaned), rawLatex)
    }
  }

  private fun nodesToReadableUnicode(nodes: List<MathNode>): String = buildString {
    for (node in nodes) {
      when (node) {
        is MathNode.Text -> append(node.value)
        is MathNode.Fraction -> {
          val num = nodesToReadableUnicode(node.numerator).trim()
          val den = nodesToReadableUnicode(node.denominator).trim()
          append(if (num.contains(' ') || num.contains('+')) "($num)" else num)
          append("/")
          append(if (den.contains(' ') || den.contains('+')) "($den)" else den)
        }
        is MathNode.Sqrt -> {
          val rad = nodesToReadableUnicode(node.radicand).trim()
          val idx = node.index?.let { nodesToReadableUnicode(it).trim() }
          if (!idx.isNullOrBlank()) append(toSuperscriptString(idx))
          append("√(").append(rad).append(")")
        }
        is MathNode.Script -> {
          append(nodesToReadableUnicode(node.base))
          node.subscript?.let { append(toSubscriptString(nodesToReadableUnicode(it))) }
          node.superscript?.let { append(toSuperscriptString(nodesToReadableUnicode(it))) }
        }
        is MathNode.OperatorBig -> {
          append(node.symbol)
          node.lower?.let { append(toSubscriptString(nodesToReadableUnicode(it))) }
          node.upper?.let { append(toSuperscriptString(nodesToReadableUnicode(it))) }
          append(" ")
        }
        is MathNode.Matrix -> {
          append("[")
          append(
            node.rows.joinToString("; ") { row ->
              row.joinToString(", ") { cell -> nodesToReadableUnicode(cell) }
            }
          )
          append("]")
        }
      }
    }
  }

  private fun toSuperscriptString(raw: String): String {
    val clean = raw.trim()
    if (clean.isEmpty()) return ""
    val mapped = clean.map { SUPERSCRIPT_MAP[it] }
    return if (mapped.all { it != null }) mapped.joinToString("") { it.toString() } else "^($clean)"
  }

  private fun toSubscriptString(raw: String): String {
    val clean = raw.trim()
    if (clean.isEmpty()) return ""
    val mapped = clean.map { SUBSCRIPT_MAP[it] }
    return if (mapped.all { it != null }) mapped.joinToString("") { it.toString() } else "_($clean)"
  }

  private fun fallbackCleanUnicode(raw: String): String {
    var s = raw
    for ((cmd, sym) in LATEX_COMMANDS) {
      if (cmd.all { it.isLetter() }) {
        s = s.replace("\\$cmd", sym)
      }
    }
    return s.replace("{", "").replace("}", "").replace("\\", "").trim()
  }

  private class LatexReader(private val input: String) {
    private var pos = 0

    fun parseSequence(stopAt: String?): List<MathNode> {
      val nodes = mutableListOf<MathNode>()
      val textBuf = StringBuilder()

      fun flushText() {
        if (textBuf.isNotEmpty()) {
          nodes.add(MathNode.Text(textBuf.toString()))
          textBuf.clear()
        }
      }

      while (pos < input.length) {
        if (stopAt != null && input.startsWith(stopAt, startIndex = pos)) {
          flushText()
          return nodes
        }
        val ch = input[pos]
        when {
          ch == '}' -> {
            pos++
            flushText()
            return nodes
          }
          ch == '{' -> {
            pos++
            val inner = parseSequence(stopAt = null)
            flushText()
            nodes.addAll(inner)
          }
          ch == '\\' -> {
            val startPos = pos
            pos++
            if (pos >= input.length) break
            val next = input[pos]
            if (!next.isLetter()) {
              pos++
              when (next) {
                '\\' -> {
                  flushText()
                  nodes.add(MathNode.Text("\n"))
                }
                else -> textBuf.append(LATEX_COMMANDS[next.toString()] ?: next.toString())
              }
              continue
            }
            val cmdStart = pos
            while (pos < input.length && input[pos].isLetter()) pos++
            val cmd = input.substring(cmdStart, pos)
            when (cmd) {
              "frac", "dfrac", "tfrac" -> {
                flushText()
                val num = readGroupOrSingleToken()
                val den = readGroupOrSingleToken()
                nodes.add(MathNode.Fraction(num, den))
              }
              "sqrt" -> {
                flushText()
                val idx = readOptionalBracketGroup()
                val rad = readGroupOrSingleToken()
                nodes.add(MathNode.Sqrt(radicand = rad, index = idx))
              }
              "begin" -> {
                flushText()
                val env = readPlainGroup().lowercase()
                val delimiter =
                  when (env) {
                    "pmatrix" -> MatrixDelimiter.PAREN
                    "bmatrix" -> MatrixDelimiter.BRACKET
                    "vmatrix" -> MatrixDelimiter.BARS
                    else -> MatrixDelimiter.PLAIN
                  }
                val endMarker = "\\end{$env}"
                val rawBodyStart = pos
                val endIdx = input.indexOf(endMarker, startIndex = rawBodyStart, ignoreCase = true)
                val matrixBody =
                  if (endIdx >= 0) {
                    val body = input.substring(rawBodyStart, endIdx)
                    pos = endIdx + endMarker.length
                    body
                  } else {
                    val body = input.substring(rawBodyStart)
                    pos = input.length
                    body
                  }
                val rows =
                  matrixBody.split("""\\""")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { rowText ->
                      rowText.split("&").map { cell ->
                        LatexReader(cell.trim()).parseSequence(null)
                      }
                    }
                nodes.add(MathNode.Matrix(rows = rows, delimiter = delimiter))
              }
              "sum", "int", "prod", "lim" -> {
                flushText()
                val sym =
                  when (cmd) {
                    "sum" -> "∑"
                    "int" -> "∫"
                    "prod" -> "∏"
                    else -> "lim"
                  }
                var lower: List<MathNode>? = null
                var upper: List<MathNode>? = null
                repeat(2) {
                  skipSpaces()
                  if (pos < input.length && input[pos] == '_') {
                    pos++
                    lower = readGroupOrSingleToken()
                  } else if (pos < input.length && input[pos] == '^') {
                    pos++
                    upper = readGroupOrSingleToken()
                  }
                }
                nodes.add(MathNode.OperatorBig(sym, lower = lower, upper = upper))
              }
              "text", "mathrm", "mathbf" -> {
                val txt = readPlainGroup()
                textBuf.append(txt)
              }
              else -> {
                val mapped = LATEX_COMMANDS[cmd]
                if (mapped != null) {
                  textBuf.append(mapped)
                } else {
                  textBuf.append(cmd)
                }
              }
            }
            if (pos == startPos) pos++
          }
          ch == '^' || ch == '_' -> {
            // Attach script to the preceding node or trailing char in textBuf
            val baseNodes: List<MathNode> =
              if (textBuf.isNotEmpty()) {
                val lastChar = textBuf[textBuf.lastIndex].toString()
                textBuf.deleteCharAt(textBuf.lastIndex)
                flushText()
                listOf(MathNode.Text(lastChar))
              } else if (nodes.isNotEmpty()) {
                val last = nodes.removeAt(nodes.lastIndex)
                listOf(last)
              } else {
                listOf(MathNode.Text(""))
              }

            var sup: List<MathNode>? = null
            var sub: List<MathNode>? = null
            repeat(2) {
              skipSpaces()
              if (pos < input.length && input[pos] == '^' && sup == null) {
                pos++
                sup = readGroupOrSingleToken()
              } else if (pos < input.length && input[pos] == '_' && sub == null) {
                pos++
                sub = readGroupOrSingleToken()
              }
            }
            nodes.add(MathNode.Script(base = baseNodes, superscript = sup, subscript = sub))
          }
          else -> {
            textBuf.append(ch)
            pos++
          }
        }
      }

      flushText()
      return nodes
    }

    private fun skipSpaces() {
      while (pos < input.length && input[pos].isWhitespace()) pos++
    }

    private fun readOptionalBracketGroup(): List<MathNode>? {
      skipSpaces()
      if (pos >= input.length || input[pos] != '[') return null
      pos++
      val end = input.indexOf(']', startIndex = pos)
      return if (end >= 0) {
        val inside = input.substring(pos, end)
        pos = end + 1
        LatexReader(inside).parseSequence(null)
      } else null
    }

    private fun readPlainGroup(): String {
      skipSpaces()
      if (pos >= input.length) return ""
      if (input[pos] != '{') {
        val ch = input[pos].toString()
        pos++
        return ch
      }
      pos++
      val start = pos
      var depth = 1
      while (pos < input.length && depth > 0) {
        if (input[pos] == '{') depth++
        else if (input[pos] == '}') depth--
        pos++
      }
      val end = (pos - 1).coerceAtLeast(start)
      return input.substring(start, end)
    }

    private fun readGroupOrSingleToken(): List<MathNode> {
      skipSpaces()
      if (pos >= input.length) return emptyList()
      return if (input[pos] == '{') {
        pos++
        parseSequence(stopAt = null)
      } else if (input[pos] == '\\') {
        val cmdStart = pos
        pos++
        while (pos < input.length && input[pos].isLetter()) pos++
        val cmd = input.substring(cmdStart + 1, pos)
        listOf(MathNode.Text(LATEX_COMMANDS[cmd] ?: cmd))
      } else {
        val ch = input[pos].toString()
        pos++
        listOf(MathNode.Text(ch))
      }
    }
  }
}

/**
 * Pure Compose mathematical equation display card rendering stacked fractions, roots with overbar,
 * matrices, summations/integrals, superscripts/subscripts, and copyable raw LaTeX.
 */
@Composable
fun AstrynMathFormulaCard(
  messageId: String,
  blockIndex: Int,
  rawLatex: String,
  onCopyFormula: ((String) -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val glass = AppThemeTokens.glass
  val parseResult = remember(rawLatex) { AstrynMathParser.parseDisplayFormula(rawLatex) }
  var copiedRecently by remember { mutableStateOf(false) }

  LaunchedEffect(copiedRecently) {
    if (copiedRecently) {
      delay(1800L)
      copiedRecently = false
    }
  }

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(glass.surfaceElevated.copy(alpha = 0.72f))
        .border(AppBorder.hairline, glass.accentCyan.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
        .padding(AppSpacing.md)
        .semantics {
          contentDescription = "Mathematical formula block"
        }
        .testTag("math_block_${messageId}_$blockIndex"),
    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        Icon(
          imageVector = Icons.Outlined.Functions,
          contentDescription = null,
          tint = glass.accentCyan,
          modifier = Modifier.size(14.dp),
        )
        Text(
          text = "FORMULA",
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
          fontWeight = FontWeight.Bold,
          color = glass.textSecondary,
        )
      }
      if (onCopyFormula != null) {
        Row(
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .clip(RoundedCornerShape(6.dp))
              .background(glass.surfaceStandard.copy(alpha = 0.65f))
              .border(AppBorder.hairline, glass.borderSubtle, RoundedCornerShape(6.dp))
              .semantics {
                role = Role.Button
                contentDescription = if (copiedRecently) "Formula copied" else "Copy formula"
              }
              .clickable {
                onCopyFormula(rawLatex.trim())
                copiedRecently = true
              }
              .padding(horizontal = 7.dp, vertical = 3.dp)
              .testTag("math_copy_button_${messageId}_$blockIndex"),
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
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = if (copiedRecently) glass.statusSuccess else glass.textPrimary,
          )
        }
      }
    }

    Box(
      modifier =
        Modifier.fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(vertical = 4.dp),
      contentAlignment = Alignment.CenterStart,
    ) {
      when (parseResult) {
        is MathParseResult.Parsed -> MathNodesRow(nodes = parseResult.nodes, scale = 1.0f)
        is MathParseResult.Fallback -> {
          Text(
            text = parseResult.readableText.ifBlank { rawLatex },
            style = MaterialTheme.typography.bodyLarge.copy(
              fontFamily = FontFamily.Serif,
              fontStyle = FontStyle.Italic,
            ),
            color = glass.textPrimary,
          )
        }
      }
    }
  }
}

@Composable
private fun MathNodesRow(
  nodes: List<MathNode>,
  scale: Float,
) {
  val glass = AppThemeTokens.glass
  val baseFontSize = (16f * scale).sp

  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    nodes.forEach { node ->
      when (node) {
        is MathNode.Text -> {
          Text(
            text = node.value,
            fontSize = baseFontSize,
            fontFamily = FontFamily.Serif,
            color = glass.textPrimary,
          )
        }
        is MathNode.Fraction -> {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 4.dp),
          ) {
            MathNodesRow(nodes = node.numerator, scale = scale * 0.85f)
            HorizontalDivider(
              modifier = Modifier.width(36.dp).padding(vertical = 2.dp),
              thickness = 1.2.dp,
              color = glass.accentCyan.copy(alpha = 0.85f),
            )
            MathNodesRow(nodes = node.denominator, scale = scale * 0.85f)
          }
        }
        is MathNode.Sqrt -> {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 2.dp),
          ) {
            node.index?.let { idx ->
              Box(modifier = Modifier.padding(bottom = 10.dp)) {
                MathNodesRow(nodes = idx, scale = scale * 0.65f)
              }
            }
            Canvas(modifier = Modifier.width(12.dp).height(24.dp)) {
              val p = Path().apply {
                moveTo(0f, size.height * 0.62f)
                lineTo(size.width * 0.35f, size.height * 0.90f)
                lineTo(size.width, 2f)
              }
              drawPath(p, color = glass.accentCyan, style = Stroke(width = 1.6.dp.toPx()))
            }
            Column(modifier = Modifier.padding(end = 3.dp)) {
              HorizontalDivider(thickness = 1.2.dp, color = glass.accentCyan)
              Box(modifier = Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp)) {
                MathNodesRow(nodes = node.radicand, scale = scale * 0.92f)
              }
            }
          }
        }
        is MathNode.Script -> {
          Row(verticalAlignment = Alignment.CenterVertically) {
            MathNodesRow(nodes = node.base, scale = scale)
            Column(verticalArrangement = Arrangement.Center) {
              node.superscript?.let { sup ->
                Box(modifier = Modifier.padding(bottom = 4.dp)) {
                  MathNodesRow(nodes = sup, scale = scale * 0.72f)
                }
              }
              node.subscript?.let { sub ->
                Box(modifier = Modifier.padding(top = 4.dp)) {
                  MathNodesRow(nodes = sub, scale = scale * 0.72f)
                }
              }
            }
          }
        }
        is MathNode.OperatorBig -> {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 3.dp),
          ) {
            node.upper?.let { MathNodesRow(nodes = it, scale = scale * 0.68f) }
            Text(
              text = node.symbol,
              fontSize = (21f * scale).sp,
              fontWeight = FontWeight.Medium,
              color = glass.accentCyan,
            )
            node.lower?.let { MathNodesRow(nodes = it, scale = scale * 0.68f) }
          }
        }
        is MathNode.Matrix -> {
          val leftBracket = when (node.delimiter) {
            MatrixDelimiter.PAREN -> "("
            MatrixDelimiter.BRACKET -> "["
            MatrixDelimiter.BARS -> "|"
            MatrixDelimiter.PLAIN -> ""
          }
          val rightBracket = when (node.delimiter) {
            MatrixDelimiter.PAREN -> ")"
            MatrixDelimiter.BRACKET -> "]"
            MatrixDelimiter.BARS -> "|"
            MatrixDelimiter.PLAIN -> ""
          }
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            if (leftBracket.isNotEmpty()) {
              Text(
                text = leftBracket,
                fontSize = (24f * scale * node.rows.size.coerceIn(1, 3) * 0.65f).sp,
                color = glass.accentCyan,
                fontWeight = FontWeight.Light,
              )
            }
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(4.dp),
              modifier = Modifier.padding(horizontal = 6.dp),
            ) {
              node.rows.forEach { rowCells ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                  rowCells.forEach { cell ->
                    MathNodesRow(nodes = cell, scale = scale * 0.9f)
                  }
                }
              }
            }
            if (rightBracket.isNotEmpty()) {
              Text(
                text = rightBracket,
                fontSize = (24f * scale * node.rows.size.coerceIn(1, 3) * 0.65f).sp,
                color = glass.accentCyan,
                fontWeight = FontWeight.Light,
              )
            }
          }
        }
      }
    }
  }
}
