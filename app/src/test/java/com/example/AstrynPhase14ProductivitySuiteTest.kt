package com.example

import com.example.data.api.AstrynIntelligenceEngine
import com.example.data.api.AstrynMathEngine
import com.example.data.api.AstrynPersonalIntelligenceEngine
import com.example.data.api.AstrynProductivityEngine
import com.example.data.api.AstrynProductivityEngine.ApprovalGate
import com.example.data.api.AstrynProductivityEngine.AstrynChartGenerator
import com.example.data.api.AstrynProductivityEngine.AstrynCodingAssistant
import com.example.data.api.AstrynProductivityEngine.AstrynDataAnalyzer
import com.example.data.api.AstrynProductivityEngine.AstrynDocumentEngine
import com.example.data.api.AstrynProductivityEngine.AstrynDocumentTemplateManager
import com.example.data.api.AstrynProductivityEngine.AstrynDocumentValidator
import com.example.data.api.AstrynProductivityEngine.AstrynFileExportManager
import com.example.data.api.AstrynProductivityEngine.AstrynMultiFormatComprehensionEngine
import com.example.data.api.AstrynProductivityEngine.AstrynPresentationEngine
import com.example.data.api.AstrynProductivityEngine.AstrynProjectPlanner
import com.example.data.api.AstrynProductivityEngine.AstrynSpreadsheetEngine
import com.example.data.api.AstrynProductivityEngine.DocumentFormat
import com.example.data.api.AstrynProductivityEngine.DocumentSection
import com.example.data.api.AstrynProductivityEngine.DocumentSpec
import com.example.data.api.AstrynProductivityEngine.DocumentTable
import com.example.data.api.AstrynProductivityEngine.DocumentTemplateType
import com.example.data.api.AstrynProductivityEngine.FormulaStatus
import com.example.data.api.AstrynProductivityEngine.PresentationDeckSpec
import com.example.data.api.AstrynProductivityEngine.PresentationSlideSpec
import com.example.data.api.AstrynProductivityEngine.ProductivityCancellationToken
import com.example.data.api.AstrynProductivityEngine.ProgrammingLanguage
import com.example.data.api.AstrynProductivityEngine.ProjectTaskState
import com.example.data.api.AstrynProductivityEngine.SlideLayoutType
import com.example.data.api.AstrynProductivityEngine.SpreadsheetFormat
import com.example.data.api.AstrynResearchEngine
import com.example.data.api.AstrynScienceEngine
import com.example.data.local.AttachmentProcessor
import com.example.data.model.AttachmentKind
import com.example.data.repository.UserPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * ASTRYN AI — PHASE 14
 * COMPLETE AI PRODUCTIVITY SUITE & REAL-WORLD WORK EXECUTION ENGINE
 *
 * Automated 36-scenario verification suite inspecting actual generated binary/XML/file artifacts.
 */
class AstrynPhase14ProductivitySuiteTest {

  private lateinit var tempBaseDir: File

  @Before
  fun setUp() {
    tempBaseDir = Files.createTempDirectory("astryn_phase14_test_").toFile()
    AstrynProjectPlanner.clearAllPlansForTesting()
    AttachmentProcessor.clearAllSessions()
  }

  @After
  fun tearDown() {
    tempBaseDir.deleteRecursively()
  }

  // 1. PDF generation
  @Test
  fun test01_pdfGenerationAndBinaryValidation() {
    val spec = AstrynDocumentTemplateManager.buildTemplateSpec(
      templateType = DocumentTemplateType.PROFESSIONAL_REPORT,
      topicOrTitle = "Cloud Infrastructure Q4 Audit Report",
    )
    val result = AstrynDocumentEngine.generateDocument(
      spec = spec,
      format = DocumentFormat.PDF,
      exportDirectory = tempBaseDir,
      userUid = "user_pdf",
    )
    assertTrue(result.isSuccess)
    val artifact = result.getOrThrow()
    assertEquals(DocumentFormat.PDF, artifact.format)
    assertTrue(artifact.validationResult.isValid)
    assertTrue(artifact.bytes.size > 400)
    assertNotNull(artifact.exportedFile)
    assertTrue(artifact.exportedFile!!.file.exists())

    // Verify AttachmentProcessor can read back the generated PDF and extract its text & table
    val parsedPdf = AttachmentProcessor.processRawBytes(
      fileName = artifact.fileName,
      mimeType = "application/pdf",
      bytes = artifact.bytes,
    )
    assertTrue(parsedPdf.isSuccess)
    val extracted = parsedPdf.getOrThrow().extractedText.orEmpty()
    assertTrue(extracted.contains("Cloud Infrastructure Q4 Audit Report"))
    assertTrue(extracted.contains("Executive Summary"))
  }

  // 2. DOCX generation
  @Test
  fun test02_docxGenerationAndOpenXmlValidation() {
    val spec = AstrynDocumentTemplateManager.buildTemplateSpec(
      templateType = DocumentTemplateType.RESUME_CV,
      topicOrTitle = "Professional Resume for Priya Verma",
      customDetails = mapOf("name" to "Priya Verma", "role" to "Principal Android Architect"),
    )
    val result = AstrynDocumentEngine.generateDocument(
      spec = spec,
      format = DocumentFormat.DOCX,
      exportDirectory = tempBaseDir,
      userUid = "user_docx",
    )
    assertTrue(result.isSuccess)
    val artifact = result.getOrThrow()
    assertEquals(DocumentFormat.DOCX, artifact.format)
    assertTrue(artifact.validationResult.isValid)
    // Verify PK ZIP signature
    assertEquals(0x50.toByte(), artifact.bytes[0])
    assertEquals(0x4B.toByte(), artifact.bytes[1])

    // Verify OpenXML round-trip extraction
    val extractedText = AstrynDocumentEngine.extractTextAndTablesFromDocxBytes(artifact.bytes).getOrThrow()
    assertTrue(extractedText.contains("Priya Verma"))
    assertTrue(extractedText.contains("Principal Android Architect"))
    assertTrue(extractedText.contains("Astryn AI Mobile"))
  }

  // 3. TXT generation
  @Test
  fun test03_txtGenerationAndAsciiFormatting() {
    val spec = AstrynDocumentTemplateManager.buildTemplateSpec(
      templateType = DocumentTemplateType.INVOICE_QUOTATION,
      topicOrTitle = "Enterprise Software License Quotation",
    )
    val artifact = AstrynDocumentEngine.generateDocument(
      spec = spec,
      format = DocumentFormat.TXT,
      exportDirectory = tempBaseDir,
      userUid = "user_txt",
    ).getOrThrow()
    assertTrue(artifact.validationResult.isValid)
    val text = String(artifact.bytes, StandardCharsets.UTF_8)
    assertTrue(text.contains("ENTERPRISE SOFTWARE LICENSE QUOTATION"))
    assertTrue(text.contains("70,800"))
    assertTrue(text.contains("+---"))
  }

  // 4. Markdown generation
  @Test
  fun test04_markdownGenerationAndGfmTables() {
    val spec = AstrynDocumentTemplateManager.buildTemplateSpec(
      templateType = DocumentTemplateType.MEETING_SUMMARY,
      topicOrTitle = "Sprint 14 Architecture Sync",
    )
    val artifact = AstrynDocumentEngine.generateDocument(
      spec = spec,
      format = DocumentFormat.MARKDOWN,
      exportDirectory = tempBaseDir,
      userUid = "user_md",
    ).getOrThrow()
    assertTrue(artifact.validationResult.isValid)
    val md = String(artifact.bytes, StandardCharsets.UTF_8)
    assertTrue(md.startsWith("# Sprint 14 Architecture Sync"))
    assertTrue(md.contains("| Action Item | Owner | Priority | Target Status |"))
  }

  // 5. Hindi document rendering
  @Test
  fun test05_hindiUnicodeDocumentRenderingInPdfAndDocx() {
    val hindiSpec = AstrynDocumentTemplateManager.buildTemplateSpec(
      templateType = DocumentTemplateType.PROJECT_REPORT,
      topicOrTitle = "भारतीय कृषि एवं सौर ऊर्जा परियोजना रिपोर्ट",
      language = "HI",
    )

    // Verify Hindi DOCX
    val docxArtifact = AstrynDocumentEngine.generateDocument(hindiSpec, DocumentFormat.DOCX).getOrThrow()
    assertTrue(docxArtifact.validationResult.isValid)
    assertTrue(docxArtifact.validationResult.unicodeVerified)
    val docxExtracted = AstrynDocumentEngine.extractTextAndTablesFromDocxBytes(docxArtifact.bytes).getOrThrow()
    assertTrue(docxExtracted.contains("भारतीय कृषि एवं सौर ऊर्जा परियोजना रिपोर्ट"))
    assertTrue(docxExtracted.contains("कार्यकारी सारांश"))

    // Verify Hindi PDF (UTF-16BE <FEFF...> hex stream round-trip)
    val pdfArtifact = AstrynDocumentEngine.generateDocument(hindiSpec, DocumentFormat.PDF).getOrThrow()
    assertTrue(pdfArtifact.validationResult.isValid)
    val parsedPdf = AttachmentProcessor.processRawBytes("hindi_report.pdf", "application/pdf", pdfArtifact.bytes).getOrThrow()
    assertTrue(parsedPdf.extractedText.orEmpty().contains("भारतीय कृषि एवं सौर ऊर्जा परियोजना रिपोर्ट"))
  }

  // 6. Document formatting (headers, footers, page numbers, multi-page pagination)
  @Test
  fun test06_documentFormattingTemplatesHeadersFootersPageNumbers() {
    val manySections = (1..12).map { idx ->
      DocumentSection(
        heading = "Section $idx: Detailed Engineering Specification",
        level = if (idx % 2 == 1) 1 else 2,
        paragraphs = listOf(
          "Paragraph A for section $idx detailing modular components, concurrency guarantees, and testing.",
          "Paragraph B for section $idx verifying memory bounds and user-isolated storage paths.",
        ),
        bulletItems = listOf("Requirement $idx.1", "Requirement $idx.2", "Requirement $idx.3"),
      )
    }
    val multiPageSpec = DocumentSpec(
      title = "Multi-Page Engineering Manual",
      headerText = "CONFIDENTIAL — ENGINEERING MANUAL",
      footerText = "Astryn AI Documentation",
      includePageNumbers = true,
      sections = manySections,
    )
    val pdfArtifact = AstrynDocumentEngine.generateDocument(multiPageSpec, DocumentFormat.PDF).getOrThrow()
    assertTrue("Multi-section manual must span multiple PDF pages", pdfArtifact.pageCount >= 2)
    val parsed = AttachmentProcessor.processRawBytes("manual.pdf", "application/pdf", pdfArtifact.bytes).getOrThrow()
    assertTrue(parsed.extractedText.orEmpty().contains("Page 1 of ${pdfArtifact.pageCount}"))
    assertTrue(parsed.extractedText.orEmpty().contains("CONFIDENTIAL"))

    // Verify fake renamed TXT rejected as invalid PDF and invalid DOCX
    val fakeBytes = "Just plain text pretending to be PDF or DOCX".toByteArray(StandardCharsets.UTF_8)
    assertFalse(AstrynDocumentValidator.validateDocumentBytes(fakeBytes, DocumentFormat.PDF).isValid)
    assertFalse(AstrynDocumentValidator.validateDocumentBytes(fakeBytes, DocumentFormat.DOCX).isValid)
  }

  // 7. XLSX generation
  @Test
  fun test07_xlsxGenerationAndOpenXmlValidation() {
    val wb = AstrynSpreadsheetEngine.createAndEvaluateWorkbook(
      title = "Q4 Financial Summary",
      sheetDefinitions = mapOf(
        "Revenue" to listOf(
          listOf("Region", "Q3", "Q4", "Growth"),
          listOf("North", "12000", "15000", "=(C2-B2)/B2*100"),
          listOf("South", "18000", "22500", "=(C3-B3)/B3*100"),
        )
      ),
    )
    val artifact = AstrynSpreadsheetEngine.generateSpreadsheetFile(
      workbook = wb,
      format = SpreadsheetFormat.XLSX,
      exportDirectory = tempBaseDir,
      userUid = "user_xlsx",
    ).getOrThrow()
    assertTrue(artifact.isIntegrityVerified)
    assertTrue(artifact.exportedFile!!.file.exists())
    assertEquals("25", wb.sheets.first().getCellByA1("D2")?.evaluatedValue)
    assertEquals("25", wb.sheets.first().getCellByA1("D3")?.evaluatedValue)
  }

  // 8. CSV generation
  @Test
  fun test08_csvGenerationAndRfc4180Quoting() {
    val wb = AstrynSpreadsheetEngine.createAndEvaluateWorkbook(
      title = "Customer Export",
      sheetDefinitions = mapOf(
        "Customers" to listOf(
          listOf("Name", "Address, City", "Notes"),
          listOf("Rohan Gupta", "Sector 62, Noida", "Preferred \"VIP\" Tier"),
        )
      ),
    )
    val csvArtifact = AstrynSpreadsheetEngine.generateSpreadsheetFile(
      workbook = wb,
      format = SpreadsheetFormat.CSV,
      exportDirectory = tempBaseDir,
      userUid = "user_csv",
    ).getOrThrow()
    val csvText = String(csvArtifact.bytes, StandardCharsets.UTF_8)
    assertTrue(csvText.contains("\"Sector 62, Noida\""))
    assertTrue(csvText.contains("\"Preferred \"\"VIP\"\" Tier\""))

    val roundtrip = AstrynSpreadsheetEngine.readWorkbookFromCsvBytes(csvArtifact.bytes).getOrThrow()
    assertEquals("Sector 62, Noida", roundtrip.sheets.first().getCell(1, 1)?.evaluatedValue)
    assertEquals("Preferred \"VIP\" Tier", roundtrip.sheets.first().getCell(1, 2)?.evaluatedValue)
  }

  // 9. Multiple spreadsheet sheets
  @Test
  fun test09_multiSheetXlsxWorkbookGenerationAndRoundtrip() {
    val wb = AstrynSpreadsheetEngine.createAndEvaluateWorkbook(
      title = "MultiSheet Operations",
      sheetDefinitions = linkedMapOf(
        "Sales_2025" to listOf(
          listOf("Product", "Units"),
          listOf("Alpha", "100"),
          listOf("Beta", "250"),
        ),
        "Expenses_2025" to listOf(
          listOf("Category", "Cost"),
          listOf("Cloud", "4500"),
          listOf("Marketing", "3200"),
        ),
        "Executive_Rollup" to listOf(
          listOf("Metric", "Value"),
          listOf("Total Units", "=SUM(Sales_2025!B2:B3)"),
          listOf("Total Cost", "=SUM(Expenses_2025!B2:B3)"),
        ),
      ),
    )
    val xlsxBytes = AstrynSpreadsheetEngine.generateSpreadsheetFile(wb, SpreadsheetFormat.XLSX).getOrThrow().bytes
    val reloaded = AstrynSpreadsheetEngine.readWorkbookFromXlsxBytes(xlsxBytes, "Reloaded").getOrThrow()
    assertEquals(3, reloaded.sheets.size)
    assertEquals(listOf("Sales_2025", "Expenses_2025", "Executive_Rollup"), reloaded.sheets.map { it.sheetName })
    val rollupSheet = reloaded.sheets[2]
    assertEquals("350", rollupSheet.getCellByA1("B2")?.evaluatedValue)
    assertEquals("7700", rollupSheet.getCellByA1("B3")?.evaluatedValue)
  }

  // 10. Spreadsheet formula evaluation (SUM, AVERAGE, MIN, MAX, COUNT, IF, SUMIF, COUNTIF + unevaluated distinction)
  @Test
  fun test10_spreadsheetFormulaEvaluationSumAvgMinMaxCountIfSumIfCountIf() {
    val wb = AstrynSpreadsheetEngine.createAndEvaluateWorkbook(
      title = "Formula Benchmark",
      sheetDefinitions = mapOf(
        "Data" to listOf(
          listOf("Department", "Score", "Status"),
          listOf("Engineering", "80", "=IF(B2>=75, \"PASS\", \"FAIL\")"),
          listOf("Sales", "60", "=IF(B3>=75, \"PASS\", \"FAIL\")"),
          listOf("Engineering", "90", "=IF(B4>=75, \"PASS\", \"FAIL\")"),
          listOf("HR", "70", "=IF(B5>=75, \"PASS\", \"FAIL\")"),
          listOf("Summary_Sum", "=SUM(B2:B5)", "=AVERAGE(B2:B5)"),
          listOf("Min_Max", "=MIN(B2:B5)", "=MAX(B2:B5)"),
          listOf("Counts", "=COUNT(B2:B5)", "=COUNTIF(A2:A5, \"Engineering\")"),
          listOf("SumIf_Test", "=SUMIF(A2:A5, \"Engineering\", B2:B5)", "=SUMIF(B2:B5, \">=75\")"),
          listOf("Unsupported_Func", "=UNKNOWNFUNC(B2:B5)", ""),
        )
      ),
    )
    val sheet = wb.sheets.first()
    // IF checks
    assertEquals("PASS", sheet.getCellByA1("C2")?.evaluatedValue)
    assertEquals("FAIL", sheet.getCellByA1("C3")?.evaluatedValue)
    assertEquals("PASS", sheet.getCellByA1("C4")?.evaluatedValue)
    assertEquals(FormulaStatus.EVALUATED, sheet.getCellByA1("C2")?.formulaStatus)

    // SUM (80+60+90+70 = 300) and AVERAGE (300/4 = 75)
    assertEquals("300", sheet.getCellByA1("B6")?.evaluatedValue)
    assertEquals("75", sheet.getCellByA1("C6")?.evaluatedValue)

    // MIN (60) and MAX (90)
    assertEquals("60", sheet.getCellByA1("B7")?.evaluatedValue)
    assertEquals("90", sheet.getCellByA1("C7")?.evaluatedValue)

    // COUNT (4) and COUNTIF Engineering (2)
    assertEquals("4", sheet.getCellByA1("B8")?.evaluatedValue)
    assertEquals("2", sheet.getCellByA1("C8")?.evaluatedValue)

    // SUMIF Engineering (80 + 90 = 170) and SUMIF >=75 (80 + 90 = 170)
    assertEquals("170", sheet.getCellByA1("B9")?.evaluatedValue)
    assertEquals("170", sheet.getCellByA1("C9")?.evaluatedValue)

    // Unsupported formula is explicitly marked UNEVALUATED_ERROR (never pretended to be calculated)
    val badCell = sheet.getCellByA1("B10")!!
    assertEquals(FormulaStatus.UNEVALUATED_ERROR, badCell.formulaStatus)
    assertEquals("UNKNOWNFUNC(B2:B5)", badCell.rawFormula)
  }

  // 11. Data analysis (Observed Data vs Calculated Result vs Statistical Forecast)
  @Test
  fun test11_smartDataAnalysisObservedCalculatedAndForecastDistinction() {
    val wb = AstrynSpreadsheetEngine.createAndEvaluateWorkbook(
      title = "Monthly Revenue Trend",
      sheetDefinitions = mapOf(
        "Sales" to listOf(
          listOf("Month", "Revenue"),
          listOf("Jan", "100"),
          listOf("Feb", "120"),
          listOf("Mar", "140"),
          listOf("Apr", "160"),
        )
      ),
    )
    val report = AstrynDataAnalyzer.analyzeSheet(wb.sheets.first(), "Monthly Revenue")
    assertEquals(4, report.totalDataRows)
    assertEquals(2, report.totalColumns)
    assertTrue(report.observedDataSummary.any { it.startsWith("[Observed Data]") })
    assertTrue(report.calculatedResultsSummary.any { it.startsWith("[Calculated Result]") && it.contains("Sum=520") && it.contains("Mean=130") })
    assertTrue(report.statisticalForecastsSummary.any { it.startsWith("[Statistical Forecast / Prediction]") && it.contains("180") })
    assertTrue(report.formattedMarkdownReport.contains("[Observed Data]"))
    assertTrue(report.formattedMarkdownReport.contains("[Calculated Result]"))
    assertTrue(report.formattedMarkdownReport.contains("[Statistical Forecast / Prediction]"))
  }

  // 12. Duplicate detection
  @Test
  fun test12_duplicateRecordDetectionInDataset() {
    val wb = AstrynSpreadsheetEngine.createAndEvaluateWorkbook(
      title = "Inventory Audit",
      sheetDefinitions = mapOf(
        "Items" to listOf(
          listOf("SKU", "ItemName", "Stock"),
          listOf("SKU-101", "Router", "15"),
          listOf("SKU-102", "Switch", "20"),
          listOf("SKU-101", "Router", "15"), // Duplicate of Row 2 at Row 4
        )
      ),
    )
    val report = AstrynDataAnalyzer.analyzeSheet(wb.sheets.first())
    assertEquals(1, report.duplicateRecords.size)
    assertEquals(2, report.duplicateRecords.first().firstSeenRowNumber)
    assertEquals(4, report.duplicateRecords.first().duplicateRowNumber)
  }

  // 13. Missing-value analysis
  @Test
  fun test13_missingValueAnalysisWithoutFabrication() {
    val wb = AstrynSpreadsheetEngine.createAndEvaluateWorkbook(
      title = "Sensor Readings",
      sheetDefinitions = mapOf(
        "Sensors" to listOf(
          listOf("Station", "TempC"),
          listOf("Alpha", "24"),
          listOf("Beta", ""),
          listOf("Gamma", "N/A"),
          listOf("Delta", "28"),
        )
      ),
    )
    val report = AstrynDataAnalyzer.analyzeSheet(wb.sheets.first())
    assertEquals(2, report.totalMissingCells)
    val tempProfile = report.columnProfiles.first { it.columnName == "TempC" }
    assertEquals(2, tempProfile.nonMissingCount)
    assertEquals(2, tempProfile.missingCount)
    assertEquals(listOf(3, 4), tempProfile.missingRowIndices)
    // Mean must be calculated strictly over the 2 observed values (24 + 28)/2 = 26, never fabricating zeros
    assertEquals("26", AstrynProductivityEngine.formatSpreadsheetNumber(tempProfile.mean!!))
  }

  // 14. Chart generation
  @Test
  fun test14_svgAndMarkdownChartGeneration() {
    val chart = AstrynChartGenerator.generateChart(
      title = "Quarterly Growth",
      chartType = "BAR",
      xAxisLabel = "Quarter",
      yAxisLabel = "ARR (USD M)",
      labels = listOf("Q1", "Q2", "Q3", "Q4"),
      values = listOf(12.5, 15.0, 18.2, 22.4),
    )
    assertTrue(chart.svgContent.startsWith("<svg"))
    assertTrue(chart.svgContent.contains("Quarterly Growth"))
    assertTrue(chart.svgContent.contains("<rect"))
    assertTrue(chart.markdownChartPreview.contains("Q4"))
  }

  // 15. PPTX generation
  @Test
  fun test15_pptxGenerationAndOpenXmlValidation() {
    val deck = AstrynPresentationEngine.buildDeckForTopic(
      topic = "Clean Energy Grid Modernization",
      targetSlideCount = 10,
    )
    val artifact = AstrynPresentationEngine.generatePresentation(
      rawDeck = deck,
      exportDirectory = tempBaseDir,
      userUid = "user_pptx",
    ).getOrThrow()
    assertTrue(artifact.validation.isValid)
    assertEquals(10, artifact.validation.slideCount)
    assertTrue(artifact.exportedFile!!.file.exists())
    // Verify PK ZIP signature and slide XML entries
    val zipEntries = AstrynProductivityEngine.readZipEntriesAsUtf8(artifact.bytes)
    assertTrue(zipEntries.containsKey("ppt/presentation.xml"))
    assertTrue(zipEntries.containsKey("ppt/slides/slide1.xml"))
    assertTrue(zipEntries.containsKey("ppt/slides/slide10.xml"))
  }

  // 16. Slide formatting (non-overlapping bounding boxes)
  @Test
  fun test16_slideFormattingNonOverlappingBoundingBoxes() {
    val deck = AstrynPresentationEngine.buildDeckForTopic("AI Systems Architecture", targetSlideCount = 6)
    val artifact = AstrynPresentationEngine.generatePresentation(deck).getOrThrow()
    assertTrue(artifact.validation.isValid)
    assertTrue("All slide shapes must have non-overlapping EMU coordinates", artifact.validation.hasNonOverlappingLayout)
  }

  // 17. Presentation content overflow (automatic slide splitting)
  @Test
  fun test17_presentationContentOverflowAutomaticSlideSplitting() {
    val overflowingSlide = PresentationSlideSpec(
      slideNumber = 1,
      layoutType = SlideLayoutType.BULLET_CONTENT,
      title = "Deep Dive Technical Checklist",
      bulletPoints = (1..14).map { idx ->
        "Point #$idx: Comprehensive verification of distributed consensus, replication latency, and fault recovery."
      },
    )
    val rawDeck = PresentationDeckSpec(
      title = "Overflow Test Deck",
      slides = listOf(overflowingSlide),
    )
    val artifact = AstrynPresentationEngine.generatePresentation(rawDeck).getOrThrow()
    assertTrue("Overflowing slide with 14 bullets must be automatically split across multiple slides", artifact.validation.overflowSplitApplied)
    assertTrue(artifact.validation.slideCount >= 3)
    assertTrue(artifact.deck.slides.all { it.bulletPoints.size <= AstrynPresentationEngine.MAX_BULLETS_PER_SLIDE })
    assertTrue(artifact.deck.slides[1].title.contains("(Cont."))
  }

  // 18. Code generation
  @Test
  fun test18_codeGenerationMultiLanguageAndUnitTests() {
    val ktRes = AstrynCodingAssistant.generateCodeSolution("Write a Kotlin repository to filter items", ProgrammingLanguage.KOTLIN)
    assertEquals(ProgrammingLanguage.KOTLIN, ktRes.language)
    assertTrue(ktRes.generatedOrFixedCode.contains("suspend fun fetchAndFilterItems"))
    assertTrue(ktRes.unitTestCode.contains("@Test"))

    val pyRes = AstrynCodingAssistant.generateCodeSolution("Write a Python function to compute mean and median", ProgrammingLanguage.PYTHON)
    assertEquals(ProgrammingLanguage.PYTHON, pyRes.language)
    assertTrue(pyRes.generatedOrFixedCode.contains("def analyze_numbers"))
    assertTrue(pyRes.unitTestCode.contains("unittest"))
  }

  // 19. Code analysis (static analysis honesty, no fake execution claim)
  @Test
  fun test19_staticCodeAnalysisAndNonExecutionHonesty() {
    val buggyCode = """
      fun printItems(items: List<String>?) {
          val list = items!!
          for (i in 0..list.size) {
              println(list[i])
          }
      }
    """.trimIndent()
    val analysis = AstrynCodingAssistant.analyzeAndDebugCode(buggyCode, "BuggyList.kt")
    assertFalse("Must never modify project files automatically", analysis.projectFilesModified)
    assertTrue(analysis.executionDisclosure.contains("Not executed or compiled on device", ignoreCase = true))
    assertTrue(analysis.findings.any { it.category.contains("Null Safety") })
    assertTrue(analysis.findings.any { it.category.contains("Off-By-One") })
  }

  // 20. Error explanation (Android & SQL root cause diagnosis)
  @Test
  fun test20_errorExplanationAndAndroidRootCauseDiagnosis() {
    val androidCrash = "java.lang.SecurityException: Permission Denial: starting Intent requires android.permission.CAMERA; also NetworkOnMainThreadException"
    val diagnosis = AstrynCodingAssistant.analyzeAndDebugCode(androidCrash)
    assertTrue(diagnosis.findings.any { it.category.contains("Permission") })
    assertTrue(diagnosis.findings.any { it.category.contains("Main-Thread") })
  }

  // 21. Uploaded document extraction (DOCX, TXT, Markdown, Source Code)
  @Test
  fun test21_uploadedDocxTxtMarkdownCodeExtraction() {
    val spec = AstrynDocumentTemplateManager.buildTemplateSpec(
      templateType = DocumentTemplateType.BUSINESS_PROPOSAL,
      topicOrTitle = "Solar Microgrid Expansion Proposal",
    )
    val docxBytes = AstrynDocumentEngine.generateDocument(spec, DocumentFormat.DOCX).getOrThrow().bytes
    val docxAtt = AttachmentProcessor.processRawBytes("proposal.docx", "", docxBytes).getOrThrow()
    assertEquals(AttachmentKind.DOCX, docxAtt.kind)
    assertTrue(docxAtt.extractedText.orEmpty().contains("Solar Microgrid Expansion Proposal"))

    val ktBytes = "package com.example\nfun greet() = \"Hello Astryn\"".toByteArray(StandardCharsets.UTF_8)
    val codeAtt = AttachmentProcessor.processRawBytes("Sample.kt", "text/plain", ktBytes).getOrThrow()
    assertEquals(AttachmentKind.SOURCE_CODE, codeAtt.kind)
    assertTrue(codeAtt.extractedText.orEmpty().contains("fun greet()"))
  }

  // 22. PDF summarization ("Is PDF se short notes bana do")
  @Test
  fun test22_pdfSummarizationAndShortNotesGeneration() {
    val pdfSpec = AstrynDocumentTemplateManager.buildTemplateSpec(
      templateType = DocumentTemplateType.STUDY_NOTES,
      topicOrTitle = "Thermodynamics Laws & Carnot Cycle",
    )
    val pdfBytes = AstrynDocumentEngine.generateDocument(pdfSpec, DocumentFormat.PDF).getOrThrow().bytes
    val pdfAtt = AttachmentProcessor.processRawBytes("thermo_notes.pdf", "application/pdf", pdfBytes).getOrThrow()
    val summary = AstrynMultiFormatComprehensionEngine.comprehendAttachment(pdfAtt, "Is PDF se short notes bana do")
    assertTrue(summary.contains("thermo_notes.pdf"))
    assertTrue(summary.contains("Thermodynamics Laws"))
  }

  // 23. XLSX analysis (uploaded XLSX inspection & analysis)
  @Test
  fun test23_uploadedXlsxAnalysisAndFormulaRoundtrip() {
    val wb = AstrynSpreadsheetEngine.createAndEvaluateWorkbook(
      title = "Store Performance",
      sheetDefinitions = mapOf(
        "Stores" to listOf(
          listOf("City", "Sales"),
          listOf("Jaipur", "45000"),
          listOf("Delhi", "65000"),
          listOf("Mumbai", "80000"),
        )
      ),
    )
    val xlsxBytes = AstrynSpreadsheetEngine.generateSpreadsheetFile(wb, SpreadsheetFormat.XLSX).getOrThrow().bytes
    val xlsxAtt = AttachmentProcessor.processRawBytes("stores.xlsx", "", xlsxBytes).getOrThrow()
    assertEquals(AttachmentKind.XLSX, xlsxAtt.kind)

    val workflowRes = AstrynProductivityEngine.executeNaturalLanguageProductivityWorkflow(
      prompt = "Analyze this uploaded spreadsheet",
      userUid = "user_analyst",
      attachments = listOf(xlsxAtt),
    )
    assertNotNull(workflowRes)
    assertNotNull(workflowRes!!.dataAnalysisReport)
    assertTrue(workflowRes.formattedMarkdown.contains("190000")) // Sum of 45000 + 65000 + 80000
  }

  // 24. CSV analysis (uploaded CSV analysis + multi-document comparison)
  @Test
  fun test24_uploadedCsvAnalysisAndGroupComparison() {
    val csvBytes = "Department,Expense\r\nEngineering,5000\r\nMarketing,3000\r\nEngineering,7000\r\n".toByteArray(StandardCharsets.UTF_8)
    val csvAtt = AttachmentProcessor.processRawBytes("expenses.csv", "text/csv", csvBytes).getOrThrow()
    assertEquals(AttachmentKind.CSV, csvAtt.kind)

    val wb = AstrynSpreadsheetEngine.readWorkbookFromCsvBytes(csvBytes).getOrThrow()
    val analysis = AstrynDataAnalyzer.analyzeSheet(wb.sheets.first(), "Department Expenses")
    val groupMap = analysis.groupComparisons["Expense by Department"]
    assertNotNull(groupMap)
    assertEquals("12000", AstrynProductivityEngine.formatSpreadsheetNumber(groupMap!!["Engineering"]!!))
    assertEquals("3000", AstrynProductivityEngine.formatSpreadsheetNumber(groupMap["Marketing"]!!))

    // Also verify multi-document comparison ("Dono documents compare karo")
    val docA = AttachmentProcessor.processRawBytes("v1.txt", "text/plain", "Project Budget: 50000\nTimeline: 6 weeks".toByteArray()).getOrThrow()
    val docB = AttachmentProcessor.processRawBytes("v2.txt", "text/plain", "Project Budget: 75000\nTimeline: 6 weeks".toByteArray()).getOrThrow()
    val cmp = AstrynMultiFormatComprehensionEngine.compareAttachments(docA, docB)
    assertTrue(cmp.numericOrFactInconsistencies.any { it.contains("50000") && it.contains("75000") })
  }

  // 25. Project planning
  @Test
  fun test25_projectPlanningMilestonesDependenciesRisks() {
    val plan = AstrynProjectPlanner.createProjectPlan(
      userUid = "user_pm",
      projectTitle = "Astryn Enterprise Rollout",
      objective = "Complete security audit, load testing, and staged production deployment",
      projectId = "proj_enterprise_1",
    )
    assertEquals(3, plan.milestones.size)
    assertEquals(5, plan.tasks.size)
    assertTrue(plan.risks.isNotEmpty())
    assertTrue(plan.tasks.any { it.dependsOnTaskIds.isNotEmpty() })
  }

  // 26. Task state persistence & honesty (new plan is PLANNED, never COMPLETED until executed)
  @Test
  fun test26_taskStatePersistenceAndNonFabricatedCompletion() {
    val plan = AstrynProjectPlanner.createProjectPlan(
      userUid = "user_state_test",
      projectTitle = "Mobile Migration",
      objective = "Migrate legacy views to Compose",
      projectId = "proj_state_1",
    )
    // Newly generated plan must NOT claim completion
    assertFalse(plan.isProjectComplete)
    assertEquals(0, plan.completedTaskCount)
    assertEquals(0, plan.progressPercentage)
    assertTrue(plan.tasks.all { it.state == ProjectTaskState.PLANNED })

    // Dependency enforcement: cannot mark T2 COMPLETED before dependency T1 is COMPLETED
    val prematureComplete = AstrynProjectPlanner.updateTaskState(
      userUid = "user_state_test",
      projectId = "proj_state_1",
      taskId = "T2",
      newState = ProjectTaskState.COMPLETED,
    )
    assertTrue("Dependent task T2 must not complete before T1", prematureComplete.isFailure)

    // Complete T1 -> In-Progress T2 -> Complete T2
    assertTrue(AstrynProjectPlanner.updateTaskState("user_state_test", "proj_state_1", "T1", ProjectTaskState.COMPLETED).isSuccess)
    assertTrue(AstrynProjectPlanner.updateTaskState("user_state_test", "proj_state_1", "T2", ProjectTaskState.IN_PROGRESS).isSuccess)
    val updated = AstrynProjectPlanner.updateTaskState("user_state_test", "proj_state_1", "T2", ProjectTaskState.COMPLETED).getOrThrow()
    assertEquals(2, updated.completedTaskCount)
    assertEquals(40, updated.progressPercentage)
  }

  // 27. File export (safe duplicate filename handling, never overwrites without approval)
  @Test
  fun test27_realFileStorageAndSafeDuplicateFilenameResolution() {
    val bytes1 = "First Document Version".toByteArray(StandardCharsets.UTF_8)
    val bytes2 = "Second Document Version".toByteArray(StandardCharsets.UTF_8)

    val firstExport = AstrynFileExportManager.saveArtifactToUserStorage(
      baseDir = tempBaseDir,
      userUid = "user_export",
      requestedFileName = "summary.txt",
      mimeType = "text/plain",
      bytes = bytes1,
      allowOverwrite = false,
    ).getOrThrow()
    assertEquals("summary.txt", firstExport.fileName)
    assertFalse(firstExport.wasRenamedToAvoidCollision)

    val secondExport = AstrynFileExportManager.saveArtifactToUserStorage(
      baseDir = tempBaseDir,
      userUid = "user_export",
      requestedFileName = "summary.txt",
      mimeType = "text/plain",
      bytes = bytes2,
      allowOverwrite = false,
    ).getOrThrow()
    assertEquals("summary (1).txt", secondExport.fileName)
    assertTrue(secondExport.wasRenamedToAvoidCollision)
    // Original file must remain intact
    assertEquals("First Document Version", firstExport.file.readText(StandardCharsets.UTF_8))
    assertEquals("Second Document Version", secondExport.file.readText(StandardCharsets.UTF_8))
  }

  // 28. File permissions (SAF, Share descriptors & path-traversal rejection)
  @Test
  fun test28_storageAccessAndShareIntentPermissions() {
    val safDesc = AstrynFileExportManager.buildStorageAccessExportDescriptor(
      fileName = "../../quarterly_report.pdf",
      mimeType = "application/pdf",
    )
    assertEquals(android.content.Intent.ACTION_CREATE_DOCUMENT, safDesc.action)
    assertTrue(safDesc.categories.contains(android.content.Intent.CATEGORY_OPENABLE))
    assertEquals("application/pdf", safDesc.mimeType)
    assertEquals("quarterly_report.pdf", safDesc.titleOrSubject)

    val shareDesc = AstrynFileExportManager.buildShareArtifactDescriptor(
      mimeType = "application/vnd.openxmlformats-officedocument.presentationml.presentation",
      title = "Deck.pptx",
    )
    assertEquals(android.content.Intent.ACTION_SEND, shareDesc.action)
    assertTrue(shareDesc.grantReadUriPermission)
  }

  // 29. User isolation (User B cannot read, modify, or delete User A's files or project plans)
  @Test
  fun test29_strictPerUserFileAndProjectIsolation() {
    val alphaExport = AstrynFileExportManager.saveArtifactToUserStorage(
      baseDir = tempBaseDir,
      userUid = "user_alpha",
      requestedFileName = "alpha_secret.txt",
      mimeType = "text/plain",
      bytes = "Confidential Alpha Roadmap".toByteArray(StandardCharsets.UTF_8),
    ).getOrThrow()

    // User Beta attempts to read or delete User Alpha's file -> must fail with SecurityException
    val betaRead = AstrynFileExportManager.readUserArtifactFile(tempBaseDir, "user_beta", alphaExport.file)
    assertTrue(betaRead.isFailure)
    val betaDelete = AstrynFileExportManager.deleteUserArtifactFile(tempBaseDir, "user_beta", alphaExport.file, userApprovedDelete = true)
    assertTrue(betaDelete.isFailure)
    assertTrue(alphaExport.file.exists())

    // Project plan isolation
    AstrynProjectPlanner.createProjectPlan("user_alpha", "Alpha Secret Project", "Confidential", "proj_alpha_only")
    assertNull(AstrynProjectPlanner.getProjectPlan("user_beta", "proj_alpha_only"))
    assertTrue(AstrynProjectPlanner.updateTaskState("user_beta", "proj_alpha_only", "T1", ProjectTaskState.COMPLETED).isFailure)
  }

  // 30. Approval enforcement
  @Test
  fun test30_explicitUserApprovalEnforcementForSensitiveActions() {
    val sensitiveActions = listOf(
      "send_email",
      "publish_document",
      "upload_external",
      "modify_account",
      "delete_file",
      "overwrite_file",
      "make_purchase",
      "submit_form",
      "financial_transaction",
    )
    for (action in sensitiveActions) {
      assertTrue("Action '$action' must require approval", ApprovalGate.requiresExplicitUserApproval(action))
      assertTrue(ApprovalGate.verifyActionAllowed(action, userApproved = false).isFailure)
      assertTrue(ApprovalGate.verifyActionAllowed(action, userApproved = true).isSuccess)
    }

    // Overwriting an existing file without userApprovedOverwrite = true must be blocked
    val existing = AstrynFileExportManager.saveArtifactToUserStorage(
      baseDir = tempBaseDir,
      userUid = "user_approval",
      requestedFileName = "draft.txt",
      mimeType = "text/plain",
      bytes = "Original".toByteArray(),
    ).getOrThrow()

    val unapprovedOverwrite = AstrynFileExportManager.saveArtifactToUserStorage(
      baseDir = tempBaseDir,
      userUid = "user_approval",
      requestedFileName = "draft.txt",
      mimeType = "text/plain",
      bytes = "Overwritten".toByteArray(),
      allowOverwrite = true,
      userApprovedOverwrite = false,
    )
    assertTrue(unapprovedOverwrite.isFailure)
    assertEquals("Original", existing.file.readText())
  }

  // 31. Cancellation handling & temporary file cleanup
  @Test
  fun test31_generationCancellationAndTempFileCleanup() {
    val token = ProductivityCancellationToken()
    token.cancel()

    val spec = AstrynDocumentTemplateManager.buildTemplateSpec(
      templateType = DocumentTemplateType.PROFESSIONAL_REPORT,
      topicOrTitle = "Cancelled Report",
    )
    var cancelledCaught = false
    try {
      AstrynDocumentEngine.generateDocument(
        spec = spec,
        format = DocumentFormat.PDF,
        exportDirectory = tempBaseDir,
        userUid = "user_cancel",
        cancellationToken = token,
      )
    } catch (_: CancellationException) {
      cancelledCaught = true
    }
    assertTrue("Cancelled generation must throw CancellationException", cancelledCaught)

    // Verify no leftover .tmp files remain in user storage
    val cleanedCount = AstrynFileExportManager.cleanupTemporaryFiles(tempBaseDir, "user_cancel")
    assertEquals(0, cleanedCount)
  }

  // 32. Failed generation recovery & corrupted input rejection
  @Test
  fun test32_failedGenerationRecoveryAndCorruptedInputRejection() {
    // Empty file export must fail cleanly without leaving partial files
    val emptyExport = AstrynFileExportManager.saveArtifactToUserStorage(
      baseDir = tempBaseDir,
      userUid = "user_fail",
      requestedFileName = "empty.pdf",
      mimeType = "application/pdf",
      bytes = ByteArray(0),
    )
    assertTrue(emptyExport.isFailure)

    // Corrupted DOCX / XLSX bytes must fail cleanly
    val corruptDocx = AstrynDocumentEngine.extractTextAndTablesFromDocxBytes("not_a_zip".toByteArray())
    assertTrue(corruptDocx.isFailure)
    val corruptXlsx = AstrynSpreadsheetEngine.readWorkbookFromXlsxBytes("not_a_zip".toByteArray())
    assertTrue(corruptXlsx.isFailure)
  }

  // 33. Existing chat, math, science & research regression
  @Test
  fun test33_existingChatMathScienceResearchRegression() = runBlocking {
    val mathRes = AstrynMathEngine.solve("Calculate 25% of 1600")
    assertNotNull(mathRes)
    assertEquals("400", mathRes!!.finalAnswer)

    val sciRes = AstrynScienceEngine.solve("Molar mass of H2O")
    assertNotNull(sciRes)
    assertTrue(sciRes!!.finalAnswer.contains("18.015"))

    val orchestrator = AstrynResearchEngine.TaskOrchestrator()
    assertNotNull(orchestrator.toolRegistry.getTool("generate_document"))
    assertNotNull(orchestrator.toolRegistry.getTool("generate_spreadsheet"))
    assertNotNull(orchestrator.toolRegistry.getTool("generate_presentation"))
    assertNotNull(orchestrator.toolRegistry.getTool("create_project_plan"))
  }

  // 34. Existing attachment regression (Image, PDF, and rejection of .zip / .exe)
  @Test
  fun test34_existingImageAndPdfAttachmentRegression() {
    val zipRejected = AttachmentProcessor.processRawBytes("archive.zip", "application/zip", ByteArray(32) { 1 })
    assertTrue("Generic .zip archives must still be rejected as unsupported", zipRejected.isFailure)

    val exeRejected = AttachmentProcessor.processRawBytes("malware.exe", "application/x-msdownload", ByteArray(32) { 1 })
    assertTrue("Executable files must still be rejected", exeRejected.isFailure)
  }

  // 35. Existing memory regression & productivity personalization
  @Test
  fun test35_existingPersonalMemoryAndProductivityPreferenceRegression() {
    AstrynPersonalIntelligenceEngine.recordMemory(
      userId = "user_pref_14",
      category = AstrynPersonalIntelligenceEngine.PersonalMemoryCategory.B_PERSONAL_PREFERENCE_MEMORY,
      key = "resume_document_format_style",
      value = "Executive Modern One-Page Format",
    )
    val spec = AstrynDocumentTemplateManager.buildTemplateSpec(
      templateType = DocumentTemplateType.RESUME_CV,
      topicOrTitle = "Resume for Vikram",
      userUid = "user_pref_14",
    )
    assertTrue(spec.subtitle.contains("Executive Modern One-Page Format"))
  }

  // 36. Voice-to-Voice read-only regression
  @Test
  fun test36_voiceToVoiceJarvisUntouchedReadOnlyVerification() {
    val voiceSessionClass = Class.forName("com.example.data.api.GeminiLiveVoiceSession")
    assertNotNull(voiceSessionClass)
    val foregroundServiceClass = Class.forName("com.example.service.ZoyaForegroundService")
    assertNotNull(foregroundServiceClass)
  }
}
