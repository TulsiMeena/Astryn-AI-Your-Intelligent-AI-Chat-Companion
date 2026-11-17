package com.example

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.api.AstrynMathEngine
import com.example.data.api.GeminiLiveVoiceSession
import com.example.data.api.GeminiService
import com.example.data.local.NovaChatDatabase
import com.example.data.model.ChatMessage
import com.example.data.model.MessageRole
import com.example.data.model.MessageStatus
import com.example.data.repository.ChatHistoryRepository
import com.example.ui.components.AstrynChartParser
import com.example.ui.components.AstrynChartType
import com.example.ui.components.AstrynDiagramParser
import com.example.ui.components.AstrynMathParser
import com.example.ui.components.AstrynResponseFormatEngine
import com.example.ui.components.AstrynRichTextSanitizer
import com.example.ui.components.CalloutKind
import com.example.ui.components.ChartDataProvenance
import com.example.ui.components.ChartParseResult
import com.example.ui.components.CopyBlockCategory
import com.example.ui.components.DiagramDirection
import com.example.ui.components.DiagramNodeShape
import com.example.ui.components.DiagramParseResult
import com.example.ui.components.MarkdownBlock
import com.example.ui.components.MarkdownMessageContent
import com.example.ui.components.MathNode
import com.example.ui.components.MathParseResult
import com.example.ui.components.ResponseContentKind
import com.example.ui.components.SafeMarkdownParser
import com.example.ui.components.TableColumnAlignment
import com.example.ui.components.buildInlineMarkdownAnnotatedString
import com.example.ui.screens.chat.copyTextToSystemClipboard
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Comprehensive Phase 16 test suite (40 real scenarios) validating:
 * - Rich text formatting, nested lists, checklists, blockquotes, callouts, Hindi & Unicode
 * - Context-aware response format selection (never forcing rich widgets into ordinary chat)
 * - Dedicated copyable blocks & exact Android system clipboard integrity
 * - Syntax highlighting across all 14 languages + unsupported language safe fallback
 * - Table parsing, numeric right-alignment, expandable pagination, and empty cell handling
 * - Native Compose Canvas Charts (Bar, Line, Pie) & data integrity validation
 * - LaTeX Mathematical Formula parsing (inline + display fractions/roots/matrices) & currency preservation
 * - Mermaid Flowchart / Diagram parsing, cycles/bounds handling & readable text fallback
 * - Streaming stability, Room history persistence compatibility, security XSS/URL neutralization & Phase 1-15 regression
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AstrynPhase16ResponseRenderingTest {

  @get:Rule val composeTestRule = createComposeRule()

  // 1. Heading parsing H1, H2, H3
  @Test
  fun `01_heading parsing supports H1 H2 H3 hierarchy`() {
    val md = "# Primary H1\n## Secondary H2\n### Tertiary H3"
    val blocks = SafeMarkdownParser.parseBlocks(md, cacheResult = false)
    val headings = blocks.filterIsInstance<MarkdownBlock.Heading>()
    assertEquals(3, headings.size)
    assertEquals(1 to "Primary H1", headings[0].level to headings[0].text)
    assertEquals(2 to "Secondary H2", headings[1].level to headings[1].text)
    assertEquals(3 to "Tertiary H3", headings[2].level to headings[2].text)
  }

  // 2. Bold, italic, strikethrough & inline code rendering
  @Test
  fun `02_bold italic strikethrough and inline code render cleanly without raw markdown markers`() {
    val annotated =
      buildInlineMarkdownAnnotatedString(
        raw = "Use **Jetpack** with *Compose*, ~~deprecated XML~~, and `remember()`.",
        primaryColor = Color.White,
        linkColor = Color.Cyan,
        inlineCodeBg = Color.DarkGray,
        inlineCodeColor = Color.Yellow,
      )
    val plain = annotated.text
    assertFalse(plain.contains("**"))
    assertFalse(plain.contains("~~"))
    assertFalse(plain.contains("`"))
    assertTrue(plain.contains("Jetpack"))
    assertTrue(plain.contains("Compose"))
    assertTrue(plain.contains("deprecated XML"))
    assertTrue(plain.contains("remember()"))
  }

  // 3. Nested bullet and numbered list rendering
  @Test
  fun `03_nested bullet and ordered lists preserve indentation hierarchy`() {
    val md =
      """
      - Architecture
        - UI Layer
          1. Compose Screens
          2. Glass Components
      - Data Layer
      """.trimIndent()
    val blocks = SafeMarkdownParser.parseBlocks(md, cacheResult = false)
    val nested = blocks.filterIsInstance<MarkdownBlock.NestedBulletList>().single()
    assertEquals(5, nested.items.size)
    assertEquals(0, nested.items[0].indentLevel)
    assertEquals(1, nested.items[1].indentLevel)
    assertEquals(2, nested.items[2].indentLevel)
    assertTrue(nested.items[2].isOrdered)
    assertEquals(1, nested.items[2].orderNumber)
  }

  // 4. Checklist rendering (- [ ] / - [x])
  @Test
  fun `04_checklists parse checked and unchecked items accurately`() {
    val md =
      """
      - [x] Compile Kotlin modules
      - [ ] Deploy production build
      """.trimIndent()
    val blocks = SafeMarkdownParser.parseBlocks(md, cacheResult = false)
    val checklist = blocks.filterIsInstance<MarkdownBlock.Checklist>().single()
    assertEquals(2, checklist.items.size)
    assertTrue(checklist.items[0].checked)
    assertFalse(checklist.items[1].checked)
  }

  // 5. Blockquote and Callout rendering
  @Test
  fun `05_blockquotes and structured callouts separate plain quotes from Warning or Tip blocks`() {
    val md =
      """
      > Plain editorial quotation stays a Blockquote.

      > [!WARNING] Never log API keys in production telemetry.

      > [!TIP] Reuse remember for heavy parsers.
      """.trimIndent()
    val blocks = SafeMarkdownParser.parseBlocks(md, cacheResult = false)
    assertEquals(1, blocks.filterIsInstance<MarkdownBlock.Blockquote>().size)
    val callouts = blocks.filterIsInstance<MarkdownBlock.Callout>()
    assertEquals(2, callouts.size)
    assertEquals(CalloutKind.WARNING, callouts[0].kind)
    assertEquals(CalloutKind.TIP, callouts[1].kind)
  }

  // 6. Hindi Devanagari text rendering
  @Test
  fun `06_hindi devanagari headings lists and copy blocks preserve Unicode intact`() {
    val hindiMd =
      """
      # स्वागत संदेश

      - कृत्रिम बुद्धिमत्ता सीखने की रूपरेखा
      - **महत्वपूर्ण बिंदु:** डेटा सुरक्षा आवश्यक है।

      ```email
      विषय: परियोजना प्रगति अद्यतन

      नमस्ते टीम,
      आज का कार्य सफलतापूर्वक पूरा हो गया है।
      ```
      """.trimIndent()
    val blocks = SafeMarkdownParser.parseBlocks(hindiMd, cacheResult = false)
    val heading = blocks.filterIsInstance<MarkdownBlock.Heading>().single()
    assertEquals("स्वागत संदेश", heading.text)
    val copyBlock = blocks.filterIsInstance<MarkdownBlock.CopyableBlock>().single()
    assertEquals(CopyBlockCategory.EMAIL_DRAFT, copyBlock.category)
    assertTrue(copyBlock.payload.contains("परियोजना प्रगति अद्यतन"))
  }

  // 7. Mixed English + Hindi + Emoji text rendering
  @Test
  fun `07_mixed english hinglish emoji content renders cleanly inside compose`() {
    val msg = "Namaste 🚀 **Astryn AI** ready hai! Check status: ✅ 100% verified."
    composeTestRule.setContent {
      MyApplicationTheme(themeMode = AppThemeMode.DARK) {
        MarkdownMessageContent(
          messageId = "msg_mix",
          content = msg,
          onCopyCode = {},
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("md_paragraph_msg_mix_0").assertIsDisplayed()
  }

  // 8. Copyable block detection & UI rendering
  @Test
  fun `08_copyable content block renders copy button with intact indentation and newlines`() {
    val md =
      """
      Here is your launch email draft:

      ```email
      Subject: Q4 Product Launch Readiness

      Hi Team,
          Please verify all release checklists by Friday.

      Best,
      Alex
      ```
      """.trimIndent()
    var copiedPayload = ""
    composeTestRule.setContent {
      MyApplicationTheme(themeMode = AppThemeMode.DARK) {
        MarkdownMessageContent(
          messageId = "msg_copy",
          content = md,
          onCopyCode = { copiedPayload = it },
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("copy_block_msg_copy_0").assertIsDisplayed()
    composeTestRule.onNodeWithTag("copy_block_button_msg_copy_0").performClick()
    assertTrue(copiedPayload.startsWith("Subject: Q4 Product Launch Readiness"))
    assertTrue(copiedPayload.contains("    Please verify all release checklists"))
    assertFalse(copiedPayload.contains("EMAIL DRAFT"))
  }

  // 9. Exact clipboard content copy (without UI badges/chrome)
  @Test
  fun `09_copyTextToSystemClipboard stores exact payload into Android ClipboardManager`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val exactDraft = "Prompt: Summarize quarterly OKRs\n  - Keep tone crisp\n  - Hindi: हिंदी"
    val sanitized = AstrynRichTextSanitizer.sanitizeClipboardPayload(exactDraft)
    val ok = copyTextToSystemClipboard(context, "Draft", sanitized)
    assertTrue(ok)
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clipText = cm.primaryClip?.getItemAt(0)?.text?.toString()
    assertEquals(exactDraft, clipText)
  }

  // 10. Copyable block does NOT trigger for ordinary prose
  @Test
  fun `10_ordinary conversational response stays plain text without unnecessary copyable boxes`() {
    val plainReply = "Paris is the capital of France. Let me know if you need travel tips!"
    val analysis = AstrynResponseFormatEngine.analyzeResponse(plainReply)
    assertTrue(analysis.shouldRenderAsSimplePlainText)
    assertEquals(0, analysis.copyableBlockCount)
    assertEquals(0, analysis.chartCount)
    assertEquals(setOf(ResponseContentKind.PLAIN_TEXT), analysis.detectedKinds)
  }

  // 11. Code language identification across all 14 required languages
  @Test
  fun `11_syntax highlighter identifies and highlights all 14 core languages`() {
    val languages =
      listOf(
        "kotlin" to "fun greet() = 42",
        "java" to "public class Demo { int x = 1; }",
        "python" to "def solve(n): return n + 1",
        "javascript" to "const run = async () => 99;",
        "typescript" to "interface User { id: number }",
        "html" to "<div class=\"hero\">Hello</div>",
        "css" to ".card { color: #38BDF8; width: 100px; }",
        "sql" to "SELECT name FROM users WHERE id = 10;",
        "json" to "{\"active\": true, \"count\": 5}",
        "xml" to "<manifest package=\"com.example\"/>",
        "yaml" to "service:\n  enabled: true\n  port: 8080",
        "c" to "int main() { return 0; }",
        "cpp" to "std::vector<int> nums = {1, 2};",
        "bash" to "export ENV=prod\necho \$ENV",
      )
    languages.forEach { (lang, snippet) ->
      val spec = AstrynResponseFormatEngine.normalizeCodeLanguage(lang)
      assertTrue("Language $lang must be supported", spec.isHighlightedSupported)
      val highlighted =
        AstrynResponseFormatEngine.highlightCodeForLanguage(
          code = snippet,
          language = lang,
          isDark = true,
          defaultTextColor = Color.White,
        )
      assertEquals(snippet, highlighted.text)
      assertTrue("Expected syntax spans for $lang", highlighted.spanStyles.size >= 2)
    }
  }

  // 12. Unsupported code language graceful fallback
  @Test
  fun `12_unsupported code language falls back cleanly to plain readable text without crashing`() {
    val spec = AstrynResponseFormatEngine.normalizeCodeLanguage("brainfuck_custom")
    assertFalse(spec.isHighlightedSupported)
    val snippet = "++++[>++++<-]>."
    val rendered =
      AstrynResponseFormatEngine.highlightCodeForLanguage(
        code = snippet,
        language = "brainfuck_custom",
        isDark = true,
        defaultTextColor = Color.White,
      )
    assertEquals(snippet, rendered.text)
  }

  // 13. Long code block horizontal scrolling & line numbers render
  @Test
  fun `13_long code block renders line numbers toggle wrap toggle and horizontal scroll container`() {
    val longLine = "val veryLongVariableName = listOf(" + (1..20).joinToString(",") { "\"item_$it\"" } + ")"
    val md = "```kotlin\n$longLine\nprintln(veryLongVariableName.size)\n```"
    composeTestRule.setContent {
      MyApplicationTheme(themeMode = AppThemeMode.DARK) {
        MarkdownMessageContent(messageId = "msg_long_code", content = md, onCopyCode = {})
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("code_block_msg_long_code_0").assertIsDisplayed()
    composeTestRule.onNodeWithTag("code_line_numbers_button_msg_long_code_0").performClick()
    composeTestRule.onNodeWithTag("code_line_numbers_gutter_msg_long_code_0").assertIsDisplayed()
  }

  // 14. Table header/row parsing & numeric right alignment
  @Test
  fun `14_table parsing respects explicit dividers and right aligns numeric columns automatically`() {
    val md =
      """
      | Framework | Popularity | Revenue |
      | :--- | :---: | --- |
      | Compose | High | ₹12,500 |
      | SwiftUI | Medium | ₹9,800 |
      """.trimIndent()
    val blocks = SafeMarkdownParser.parseBlocks(md, cacheResult = false)
    val table = blocks.filterIsInstance<MarkdownBlock.Table>().single()
    assertEquals(listOf("Framework", "Popularity", "Revenue"), table.headers)
    assertEquals(2, table.rows.size)
    assertEquals(
      listOf(TableColumnAlignment.LEFT, TableColumnAlignment.CENTER, TableColumnAlignment.RIGHT),
      table.alignments,
    )
  }

  // 15. Wide table horizontal scroll + empty cells
  @Test
  fun `15_wide table handles empty cells cleanly with placeholder dash inside compose`() {
    val md =
      """
      | Col1 | Col2 | Col3 | Col4 | Col5 |
      | --- | --- | --- | --- | --- |
      | Alpha |  | 100 | Active | Verified |
      """.trimIndent()
    composeTestRule.setContent {
      MyApplicationTheme(themeMode = AppThemeMode.DARK) {
        MarkdownMessageContent(messageId = "msg_wide_tbl", content = md, onCopyCode = {})
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("md_table_msg_wide_tbl_0").assertIsDisplayed()
    composeTestRule.onNodeWithText("—").assertExists()
  }

  // 16. Large table expandable pagination (never drops data)
  @Test
  fun `16_large table with 40 rows renders expandable show all toggle preserving every row`() {
    val rowsMd = (1..40).joinToString("\n") { "| Row $it | ${it * 10} |" }
    val md = "| Item | Score |\n| --- | --- |\n$rowsMd"
    composeTestRule.setContent {
      MyApplicationTheme(themeMode = AppThemeMode.DARK) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          MarkdownMessageContent(messageId = "msg_big_tbl", content = md, onCopyCode = {})
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Show all 40 rows (15 more)").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Show fewer rows").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Row 40").assertExists()
  }

  // 17. Bar chart parsing & compose Canvas rendering
  @Test
  fun `17_bar chart parses valid numeric series and renders interactive compose chart card`() {
    val md =
      """
      ```chart
      {"title":"Quarterly Users","type":"bar","labels":["Q1","Q2","Q3","Q4"],"values":[120,185,240,310],"unit":"k","provenance":"observed"}
      ```
      """.trimIndent()
    val blocks = SafeMarkdownParser.parseBlocks(md, cacheResult = false)
    val chartBlock = blocks.filterIsInstance<MarkdownBlock.Chart>().single()
    val valid = chartBlock.parseResult as ChartParseResult.Valid
    assertEquals(AstrynChartType.BAR, valid.spec.type)
    assertEquals(ChartDataProvenance.OBSERVED, valid.spec.series.first().provenance)

    composeTestRule.setContent {
      MyApplicationTheme(themeMode = AppThemeMode.DARK) {
        MarkdownMessageContent(messageId = "msg_bar", content = md, onCopyCode = {})
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("chart_block_msg_bar_0").assertIsDisplayed()
    composeTestRule.onNodeWithText("Quarterly Users").assertIsDisplayed()
    composeTestRule.onNodeWithText("Observed").assertIsDisplayed()
  }

  // 18. Line / time-series chart parsing & forecast provenance labeling
  @Test
  fun `18_line time series chart labels forecast data honestly without fabricating points`() {
    val json =
      """{"title":"Latency Trend","type":"time_series","labels":["Mon","Tue","Wed"],"series":[{"name":"Forecast p95","values":[48,42,39],"provenance":"forecast"}],"unit":"ms"}"""
    val result = AstrynChartParser.parseChartBlock(json)
    assertTrue(result is ChartParseResult.Valid)
    val spec = (result as ChartParseResult.Valid).spec
    assertEquals(AstrynChartType.TIME_SERIES, spec.type)
    assertEquals(ChartDataProvenance.FORECAST_OR_ESTIMATE, spec.series.single().provenance)
    assertEquals(3, spec.series.single().values.size)
  }

  // 19. Pie chart parsing & percentage total validation
  @Test
  fun `19_pie chart parses valid proportions and rejects negative slice totals`() {
    val good = AstrynChartParser.parseChartBlock(
      """{"title":"Traffic Share","type":"pie","labels":["Mobile","Web"],"values":[75,25],"unit":"%"}"""
    )
    assertTrue(good is ChartParseResult.Valid)
    assertEquals(AstrynChartType.PIE, (good as ChartParseResult.Valid).spec.type)

    val negative = AstrynChartParser.parseChartBlock(
      """{"title":"Broken Pie","type":"pie","labels":["A","B"],"values":[-10,20]}"""
    )
    assertTrue(negative is ChartParseResult.InvalidOrEmpty)
  }

  // 20. Invalid / empty chart data handling (graceful fallback)
  @Test
  fun `20_invalid or non numeric chart payload returns safe fallback card without crashing`() {
    val md = "```chart\n{\"title\":\"Bad\",\"type\":\"bar\",\"values\":[\"N/A\",\"TBD\"]}\n```"
    composeTestRule.setContent {
      MyApplicationTheme(themeMode = AppThemeMode.DARK) {
        MarkdownMessageContent(messageId = "msg_bad_chart", content = md, onCopyCode = {})
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("chart_fallback_msg_bad_chart_0").assertIsDisplayed()
  }

  // 21. Inline LaTeX math conversion & currency safety
  @Test
  fun `21_inline latex renders Unicode math cleanly while preserving dollar currency values`() {
    assertFalse(AstrynMathParser.containsInlineMath("The budget is $50 and $100 today."))
    val line = "Energy follows \$E = mc^2\$ whereas price stays $50 to $100."
    val tokens = AstrynMathParser.tokenizeInlineMath(line)
    val mathTokens = tokens.filter { it.isMath }
    assertEquals(1, mathTokens.size)
    assertTrue(mathTokens.single().content.contains("mc²"))
    assertTrue(tokens.any { !it.isMath && it.content.contains("$50") })
  }

  // 22. Complex display LaTeX (fractions, roots, summation, integral, limits, matrices)
  @Test
  fun `22_display latex parses fractions square roots matrices summations and integrates with compose card`() {
    val latex = """x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a} + \sum_{i=1}^{n} i + \begin{pmatrix}1 & 0\\ 0 & 1\end{pmatrix}"""
    val parsed = AstrynMathParser.parseDisplayFormula(latex) as MathParseResult.Parsed
    assertTrue(parsed.nodes.any { it is MathNode.Fraction })
    assertTrue(parsed.nodes.any { it is MathNode.OperatorBig && it.symbol == "∑" })
    assertTrue(parsed.nodes.any { it is MathNode.Matrix })

    composeTestRule.setContent {
      MyApplicationTheme(themeMode = AppThemeMode.DARK) {
        MarkdownMessageContent(messageId = "msg_math", content = "$$\n$latex\n$$", onCopyCode = {})
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("math_block_msg_math_0").assertIsDisplayed()
  }

  // 23. Malformed LaTeX fallback safety
  @Test
  fun `23_malformed unbalanced latex never crashes and falls back to readable notation`() {
    val broken = """\frac{\alpha + \beta"""
    val readable = AstrynMathParser.convertLatexToReadableUnicode(broken)
    assertTrue(readable.contains("α"))
    assertTrue(readable.contains("β"))
  }

  // 24. Phase 9 Math Engine interoperability (no duplicated solver logic)
  @Test
  fun `24_phase 9 math engine verified solution table renders natively via markdown pipeline`() {
    val solution = AstrynMathEngine.solve("solve 2x + 6 = 14")
    assertNotNull(solution)
    val blocks = SafeMarkdownParser.parseBlocks(solution!!.formattedResponse, cacheResult = false)
    assertTrue(blocks.isNotEmpty())
  }

  // 25. Mermaid flowchart parsing (nodes, shapes, directional arrows, branch labels)
  @Test
  fun `25_mermaid flowchart parser extracts TD LR directions decision diamonds and branch labels`() {
    val src =
      """
      flowchart TD
        A([Request]) --> B{Authenticated?}
        B -->|Yes| C[Process Data]
        B -->|No| D[Show Login]
      """.trimIndent()
    val result = AstrynDiagramParser.parseDiagram(src) as DiagramParseResult.Valid
    assertEquals(DiagramDirection.TOP_DOWN, result.spec.direction)
    assertEquals(4, result.spec.nodes.size)
    assertEquals(DiagramNodeShape.DIAMOND, result.spec.nodes.first { it.id == "B" }.shape)
    assertEquals("Yes", result.spec.edges.first { it.toId == "C" }.label)
  }

  // 26. Mermaid flowchart Compose UI rendering
  @Test
  fun `26_mermaid flowchart renders inside compose card without external webview dependencies`() {
    val md =
      """
      ```mermaid
      flowchart LR
        Start([Prompt]) --> Analyze[Analyze Intent] --> Answer[Render Response]
      ```
      """.trimIndent()
    composeTestRule.setContent {
      MyApplicationTheme(themeMode = AppThemeMode.DARK) {
        MarkdownMessageContent(messageId = "msg_flow", content = md, onCopyCode = {})
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("diagram_block_msg_flow_0").assertIsDisplayed()
    composeTestRule.onNodeWithText("Analyze Intent").assertIsDisplayed()
  }

  // 27. Cyclic & oversized Mermaid resilience
  @Test
  fun `27_cyclic and bounded diagram inputs terminate cleanly without infinite loops`() {
    val cyclicSrc = "flowchart TD\nA[Step1] --> B[Step2]\nB --> A"
    val parsed = AstrynDiagramParser.parseDiagram(cyclicSrc) as DiagramParseResult.Valid
    val layers = AstrynDiagramParser.computeNodeLayers(parsed.spec)
    assertEquals(2, layers.flatten().size)

    // Oversized node generation respects MAX_DIAGRAM_NODES cap
    val hugeLines = (1..60).joinToString("\n") { "N$it[Node$it] --> N${it + 1}[Node${it + 1}]" }
    val capped = AstrynDiagramParser.parseDiagram("flowchart TD\n$hugeLines") as DiagramParseResult.Valid
    assertTrue(capped.spec.nodes.size <= AstrynDiagramParser.MAX_DIAGRAM_NODES)
  }

  // 28. Malformed diagram graceful fallback
  @Test
  fun `28_malformed or empty mermaid diagram degrades to readable fallback steps`() {
    val emptyResult = AstrynDiagramParser.parseDiagram("   ")
    assertTrue(emptyResult is DiagramParseResult.Fallback)
    val unknownResult = AstrynDiagramParser.parseDiagram("flowchart TD\n%% comment only")
    assertTrue(unknownResult is DiagramParseResult.Fallback)
  }

  // 29. Malformed Markdown recovery (unclosed bold/fence/table)
  @Test
  fun `29_malformed unclosed fences bold markers and half tables never crash parser`() {
    val brokenMd =
      """
      ### Broken Section
      **unclosed bold and *unclosed italic
      | Col A | Col B
      ```kotlin
      val x = 10
      """.trimIndent()
    val blocks = SafeMarkdownParser.parseBlocks(brokenMd, cacheResult = false)
    assertTrue(blocks.isNotEmpty())
    assertTrue(blocks.any { it is MarkdownBlock.Heading })
    assertTrue(blocks.any { it is MarkdownBlock.CodeBlock })
  }

  // 30. Streaming partial Markdown compatibility
  @Test
  fun `30_streaming partial chart mermaid and math blocks remain smooth progressive previews until closing fence`() {
    val partialChart = "Here is the trend:\n```chart\n{\"title\":\"Sales\",\"val"
    val preview = AstrynResponseFormatEngine.buildStreamingSafePreview(partialChart)
    assertTrue(preview.hasPendingChartBlock)
    val streamingBlocks = SafeMarkdownParser.parseBlocks(preview.safeMarkdown, cacheResult = false)
    assertTrue(streamingBlocks.none { it is MarkdownBlock.Chart })

    val partialMermaid = "```mermaid\nflowchart TD\nA --> "
    val diagramPreview = AstrynResponseFormatEngine.buildStreamingSafePreview(partialMermaid)
    assertTrue(diagramPreview.hasPendingDiagramBlock)
    assertTrue(SafeMarkdownParser.parseBlocks(diagramPreview.safeMarkdown, cacheResult = false).none { it is MarkdownBlock.Diagram })
  }

  // 31. Streaming Compose UI transition from partial -> complete visual chart
  @Test
  fun `31_compose streaming message transitions cleanly from streaming state to complete rich content`() {
    val stateText = mutableStateOf("```chart\n{\"title\":\"Growth\",\"type\":\"bar\",\"labels\":[\"A\"")
    val isStreamingState = mutableStateOf(true)
    composeTestRule.setContent {
      MyApplicationTheme(themeMode = AppThemeMode.DARK) {
        MarkdownMessageContent(
          messageId = "msg_stream",
          content = stateText.value,
          onCopyCode = {},
          isStreaming = isStreamingState.value,
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("code_block_msg_stream_0").assertExists()

    stateText.value = "```chart\n{\"title\":\"Growth\",\"type\":\"bar\",\"labels\":[\"A\",\"B\"],\"values\":[10,25]}\n```"
    isStreamingState.value = false
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("chart_block_msg_stream_0").assertExists()
  }

  // 32. Long multi-section response performance & stability
  @Test
  fun `32_long structured multi block response parses rapidly under memory bounds`() {
    val longMd =
      buildString {
        repeat(15) { section ->
          appendLine("## Section $section")
          appendLine("Paragraph with **bold** and \$x^2 + $section\$.")
          appendLine("- Point A\n  - Subpoint B")
          appendLine("| K | V |\n| --- | --- |\n| a | $section |")
          appendLine("```python\ndef fn_$section(): return $section\n```")
        }
      }
    val startNs = System.nanoTime()
    val blocks = SafeMarkdownParser.parseBlocks(longMd, cacheResult = false)
    val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
    assertTrue(blocks.size >= 60)
    assertTrue("Parsing long rich document took too long (${elapsedMs}ms)", elapsedMs < 2000L)
  }

  // 33. ChatMessage Room persistence & canonical history round-trip
  @Test
  fun `33_chat history repository persists and restores rich chart math copy and diagram markdown cleanly`() =
    runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val db = NovaChatDatabase.createInMemory(context)
      val repo = ChatHistoryRepository(chatDao = db.chatDao(), ioDispatcher = Dispatchers.Unconfined)

      val richPayload =
        """
        # Report
        ```chart
        {"title":"Metrics","type":"bar","labels":["X","Y"],"values":[5,9]}
        ```
        ```mermaid
        flowchart TD
          A[Start] --> B[End]
        ```
        $$ E = mc^2 $$
        """.trimIndent()

      repo.saveMessage(
        conversationId = "conv_phase16",
        message =
          ChatMessage(
            id = "msg_p16_saved",
            role = MessageRole.MODEL,
            content = richPayload,
            status = MessageStatus.SUCCESS,
          ),
      )

      val restored = repo.getMessagesForConversation("conv_phase16").single()
      assertEquals(richPayload, restored.content)
      val blocks = SafeMarkdownParser.parseBlocks(restored.content, cacheResult = false)
      assertTrue(blocks.any { it is MarkdownBlock.Chart })
      assertTrue(blocks.any { it is MarkdownBlock.Diagram })
      assertTrue(blocks.any { it is MarkdownBlock.MathFormula })
      db.close()
    }

  // 34. Security: HTML script/iframe/event-handler stripping & XSS link blocking
  @Test
  fun `34_security sanitizer strips script iframe onclick handlers while keeping safe math inequalities`() {
    val raw = "<script>alert('xss')</script><img src=x onerror=\"steal()\"> Check a < b and **safe** <b>text</b>."
    val clean = AstrynRichTextSanitizer.sanitizeDangerousHtml(raw)
    assertFalse(clean.contains("<script", ignoreCase = true))
    assertFalse(clean.contains("onerror", ignoreCase = true))
    assertFalse(clean.contains("<img", ignoreCase = true))
    assertTrue(clean.contains("a < b"))
    assertTrue(clean.contains("**text**"))

    assertFalse(SafeMarkdownParser.isSafeExternalUrl("javascript:alert(1)"))
    assertFalse(SafeMarkdownParser.isSafeExternalUrl("data:text/html,<script>1</script>"))
    assertTrue(SafeMarkdownParser.isSafeExternalUrl("https://kotlinlang.org/docs/home.html"))
  }

  // 35. Format detection engine classification coverage
  @Test
  fun `35_response format engine accurately classifies mixed rich formats vs comparison hints`() {
    val mixed =
      """
      # Options Comparison
      | Option | Cost |
      | --- | --- |
      | A | ₹500 |
      > **Pros:** Fast setup
      - [x] Verify SLA
      """.trimIndent()
    val summary = AstrynResponseFormatEngine.analyzeResponse(mixed, userQueryHint = "compare options")
    assertTrue(summary.hasRichFormatting)
    assertTrue(ResponseContentKind.TABLE in summary.detectedKinds)
    assertTrue(ResponseContentKind.COMPARISON in summary.detectedKinds)
    assertTrue(ResponseContentKind.CHECKLIST in summary.detectedKinds)
    assertTrue(ResponseContentKind.CALLOUT in summary.detectedKinds)
    assertEquals(1, summary.tableCount)
    assertEquals(1, summary.checklistCount)
    assertEquals(1, summary.calloutCount)
  }

  // 36. Conditional Gemini formatting hints stay silent on ordinary questions
  @Test
  fun `36_adaptive formatting hints stay empty on casual prompts and engage on diagram chart math draft queries`() {
    assertEquals("", AstrynResponseFormatEngine.buildAdaptiveFormattingHint("Hello, how are you?"))
    assertEquals("", AstrynResponseFormatEngine.buildAdaptiveFormattingHint("What is the capital of Japan?"))
    assertTrue(
      AstrynResponseFormatEngine.buildAdaptiveFormattingHint("Draw a bar chart of Q1 revenue")
        .contains("```chart")
    )
    assertTrue(
      AstrynResponseFormatEngine.buildAdaptiveFormattingHint("Create a flowchart for login workflow")
        .contains("```mermaid")
    )
    assertTrue(
      AstrynResponseFormatEngine.buildAdaptiveFormattingHint("Draft a launch email template")
        .contains("```copy")
    )
  }

  // 37. ChatMessage TTS speech stripping cleanly removes rich formatting symbols
  @Test
  fun `37_chat message formatForSpeech handles markdown symbols safely for accessibility`() {
    val msg =
      ChatMessage(
        role = MessageRole.MODEL,
        content = "# Title\n**Hello** [Astryn](https://example.com) `code`.",
      )
    val spoken = msg.formatForSpeech()
    assertFalse(spoken.contains("#"))
    assertFalse(spoken.contains("**"))
    assertTrue(spoken.contains("Astryn"))
  }

  // 38. Scroll & cancel regression: interrupted message preserves partial content without corrupting layout
  @Test
  fun `38_interrupted partial response renders safely without crashing on trailing broken tokens`() {
    val interrupted =
      ChatMessage(
        role = MessageRole.MODEL,
        content = "Partial reply:\n```python\ndef partial():\n    x = 1",
        status = MessageStatus.INTERRUPTED,
      )
    composeTestRule.setContent {
      MyApplicationTheme(themeMode = AppThemeMode.LIGHT) {
        MarkdownMessageContent(
          messageId = interrupted.id,
          content = interrupted.content,
          onCopyCode = {},
          isStreaming = false,
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("code_block_${interrupted.id}_0").assertIsDisplayed()
  }

  // 39. Table + Chart + Math combined single response rendering
  @Test
  fun `39_combined multi modal response renders heading table chart math and copy block together`() {
    val combined =
      """
      # Financial Projection

      | Quarter | Profit |
      | --- | --- |
      | Q1 | 120 |
      | Q2 | 180 |

      ```chart
      {"title":"Profit Trend","type":"line","labels":["Q1","Q2"],"values":[120,180],"provenance":"calculated"}
      ```

      $$ ROI = \frac{180 - 120}{120} \times 100\% $$

      ```copy
      Executive Summary: Profit grew from 120 to 180 (+50%).
      ```
      """.trimIndent()
    composeTestRule.setContent {
      MyApplicationTheme(themeMode = AppThemeMode.DARK) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          MarkdownMessageContent(messageId = "msg_all", content = combined, onCopyCode = {})
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("md_heading_msg_all_0").assertExists()
    composeTestRule.onNodeWithTag("chart_block_msg_all_0").performScrollTo().assertExists()
    composeTestRule.onNodeWithTag("math_block_msg_all_0").performScrollTo().assertExists()
    composeTestRule.onNodeWithTag("copy_block_msg_all_0").performScrollTo().assertExists()
  }

  // 40. Read-only regression guard for locked Jarvis voice-to-voice & core Gemini configuration
  @Test
  fun `40_voice to voice jarvis constants and GeminiService defaults remain untouched`() {
    assertNotNull(GeminiLiveVoiceSession.DEFAULT_LIVE_MODEL)
    assertEquals("gemini-2.5-flash", GeminiService.DEFAULT_MODEL)
    val ttsSafe = GeminiLiveVoiceSession.toHumanFriendSpokenPhonetics("**Astryn** ready")
    assertFalse(ttsSafe.contains("**"))
  }
}
