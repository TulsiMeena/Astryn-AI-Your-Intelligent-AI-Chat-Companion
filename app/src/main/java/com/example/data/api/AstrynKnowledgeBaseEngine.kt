package com.example.data.api

import com.example.data.local.AttachmentProcessor
import com.example.data.model.AttachmentKind
import com.example.data.model.ChatAttachment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * ASTRYN AI — PHASE 15
 * PERSONAL KNOWLEDGE BASE ENGINE & INTELLIGENT DOCUMENT CHUNKER
 *
 * Manages per-user document ingestion, format-aware intelligent chunking,
 * versioned document lifecycle (add, update, re-index, delete, rebuild,
 * interrupted recovery), atomic JSON persistence, OCR limitation honesty,
 * and strict ownership & Memory-vs-Knowledge separation.
 */
enum class KnowledgeFileFormat(val extension: String, val displayName: String) {
  PDF("pdf", "PDF Document"),
  DOCX("docx", "Word Document (DOCX)"),
  TXT("txt", "Plain Text"),
  MARKDOWN("md", "Markdown"),
  CSV("csv", "CSV Dataset"),
  XLSX("xlsx", "Excel Workbook (XLSX)"),
  SOURCE_CODE("code", "Source Code"),
  ;

  companion object {
    fun fromAttachmentKind(kind: AttachmentKind): KnowledgeFileFormat? =
      when (kind) {
        AttachmentKind.PDF -> PDF
        AttachmentKind.DOCX -> DOCX
        AttachmentKind.TXT -> TXT
        AttachmentKind.MARKDOWN -> MARKDOWN
        AttachmentKind.CSV -> CSV
        AttachmentKind.XLSX -> XLSX
        AttachmentKind.SOURCE_CODE -> SOURCE_CODE
        AttachmentKind.IMAGE -> null
      }
  }
}

enum class ExtractionStatus {
  SUCCESS,
  PARTIAL_OCR_UNAVAILABLE,
  PARTIAL_PAGE_LIMIT,
  FAILED,
}

enum class IndexingStatus {
  PENDING,
  INDEXING,
  INDEXED,
  INTERRUPTED,
  FAILED,
}

enum class OcrStatus {
  NOT_NEEDED,
  OCR_APPLIED,
  OCR_UNAVAILABLE,
}

fun interface OcrGateway {
  suspend fun extractTextFromScannedPdf(
    fileName: String,
    renderedPageImagesBase64: List<String>,
    rawBytes: ByteArray?,
  ): Result<Map<Int, String>>
}

data class DocumentChunk(
  val chunkId: String,
  val documentId: String,
  val userUid: String,
  val originalFilename: String,
  val fileFormat: KnowledgeFileFormat,
  val pageNumber: Int? = null,
  val sectionTitle: String? = null,
  val sheetName: String? = null,
  val rowRange: String? = null,
  val chunkIndex: Int,
  val startOffset: Int,
  val endOffset: Int,
  val content: String,
  val contentHash: String,
  val extractionVersion: Int = 1,
  val topicTags: Set<String> = emptySet(),
  val importTimestampMs: Long = System.currentTimeMillis(),
)

data class KnowledgeDocumentRecord(
  val documentId: String,
  val userUid: String,
  val originalFilename: String,
  val fileFormat: KnowledgeFileFormat,
  val fileSizeBytes: Long,
  val documentHash: String,
  val importTimestampMs: Long,
  val lastModifiedTimestampMs: Long? = null,
  val extractionStatus: ExtractionStatus,
  val indexingStatus: IndexingStatus,
  val pageCount: Int? = null,
  val detectedLanguage: String = "UNKNOWN",
  val chunkCount: Int = 0,
  val documentVersion: Int = 1,
  val indexVersion: Int = 1,
  val ocrStatus: OcrStatus = OcrStatus.NOT_NEEDED,
  val errorInfo: String? = null,
  val extractedText: String = "",
  val pageTextMap: Map<Int, String> = emptyMap(),
  val sheetNames: List<String> = emptyList(),
  val containsSensitiveWarning: Boolean = false,
)

data class IngestionResult(
  val document: KnowledgeDocumentRecord,
  val chunks: List<DocumentChunk>,
  val wasDeduplicated: Boolean = false,
  val wasReindexed: Boolean = false,
  val embeddingsGeneratedCount: Int = 0,
  val searchModeUsed: String = "LEXICAL_BM25_FALLBACK",
  val verificationPassed: Boolean = false,
  val warnings: List<String> = emptyList(),
)

object AstrynDocumentChunker {

  const val DEFAULT_MAX_CHUNK_CHARS = 900
  const val DEFAULT_OVERLAP_CHARS = 140
  const val MAX_CHUNKS_PER_DOCUMENT = 600
  const val EXTRACTION_VERSION = 1

  fun chunkDocument(
    documentId: String,
    userUid: String,
    originalFilename: String,
    fileFormat: KnowledgeFileFormat,
    extractedText: String,
    pageTextMap: Map<Int, String> = emptyMap(),
    xlsxSheets: List<Pair<String, List<List<String>>>> = emptyList(),
    importTimestampMs: Long = System.currentTimeMillis(),
    maxChunkChars: Int = DEFAULT_MAX_CHUNK_CHARS,
    overlapChars: Int = DEFAULT_OVERLAP_CHARS,
    extractionVersion: Int = EXTRACTION_VERSION,
  ): List<DocumentChunk> {
    val cleanUser = AstrynKnowledgeBaseEngine.normalizeUserScope(userUid)
    val safeFilename = AstrynSecurityGuard.sanitizeFileName(originalFilename)
    val cappedMaxChars = maxChunkChars.coerceIn(220, 2_000)
    val cappedOverlap = overlapChars.coerceIn(0, cappedMaxChars / 3)

    val rawChunks = mutableListOf<DocumentChunk>()

    when (fileFormat) {
      KnowledgeFileFormat.PDF -> {
        val resolvedPages =
          if (pageTextMap.isNotEmpty()) {
            pageTextMap.toSortedMap()
          } else {
            ChatAttachment.parsePageMapFromExtractedText(extractedText).toSortedMap()
          }

        if (resolvedPages.isNotEmpty()) {
          var globalOffset = 0
          for ((pageNum, pageContent) in resolvedPages) {
            if (rawChunks.size >= MAX_CHUNKS_PER_DOCUMENT) break
            val sections = splitByHeadingsAndParagraphs(pageContent)
            for ((secTitle, secBody) in sections) {
              if (rawChunks.size >= MAX_CHUNKS_PER_DOCUMENT) break
              val slices = slicePreservingSentencesAndTables(secBody, cappedMaxChars, cappedOverlap)
              for (slice in slices) {
                if (rawChunks.size >= MAX_CHUNKS_PER_DOCUMENT) break
                val startOff = globalOffset + slice.first
                val endOff = globalOffset + slice.second
                val idx = rawChunks.size
                rawChunks.add(
                  buildChunk(
                    documentId = documentId,
                    userUid = cleanUser,
                    originalFilename = safeFilename,
                    fileFormat = fileFormat,
                    pageNumber = pageNum,
                    sectionTitle = secTitle,
                    sheetName = null,
                    rowRange = null,
                    chunkIndex = idx,
                    startOffset = startOff,
                    endOffset = endOff,
                    content = slice.third,
                    extractionVersion = extractionVersion,
                    importTimestampMs = importTimestampMs,
                  )
                )
              }
            }
            globalOffset += pageContent.length + 1
          }
        } else {
          // Never invent page numbers when pageTextMap is absent
          chunkHeadingAwareText(
            rawChunks = rawChunks,
            documentId = documentId,
            userUid = cleanUser,
            originalFilename = safeFilename,
            fileFormat = fileFormat,
            fullText = extractedText,
            pageNumber = null,
            maxChunkChars = cappedMaxChars,
            overlapChars = cappedOverlap,
            extractionVersion = extractionVersion,
            importTimestampMs = importTimestampMs,
          )
        }
      }

      KnowledgeFileFormat.XLSX -> {
        if (xlsxSheets.isNotEmpty()) {
          var charCursor = 0
          for ((sheetName, rows) in xlsxSheets) {
            if (rows.isEmpty() || rawChunks.size >= MAX_CHUNKS_PER_DOCUMENT) continue
            val headers = rows.first()
            val dataRows = if (rows.size > 1) rows.drop(1) else emptyList()
            if (dataRows.isEmpty()) {
              val line = "${headers.joinToString(" | ")}"
              rawChunks.add(
                buildChunk(
                  documentId = documentId,
                  userUid = cleanUser,
                  originalFilename = safeFilename,
                  fileFormat = fileFormat,
                  pageNumber = null,
                  sectionTitle = null,
                  sheetName = sheetName,
                  rowRange = "Rows 1–1",
                  chunkIndex = rawChunks.size,
                  startOffset = charCursor,
                  endOffset = charCursor + line.length,
                  content = "[Sheet: $sheetName]\n$line",
                  extractionVersion = extractionVersion,
                  importTimestampMs = importTimestampMs,
                )
              )
              charCursor += line.length + 1
            } else {
              val rowsPerChunk = 12
              var rowIdx = 0
              while (rowIdx < dataRows.size && rawChunks.size < MAX_CHUNKS_PER_DOCUMENT) {
                val endIdx = minOf(rowIdx + rowsPerChunk, dataRows.size)
                val sliceRows = dataRows.subList(rowIdx, endIdx)
                val startSheetRow = rowIdx + 2 // 1-indexed spreadsheet row after header
                val endSheetRow = endIdx + 1
                val rowRangeLabel = "Rows $startSheetRow–$endSheetRow"
                val content = buildString {
                  appendLine("[Sheet: $sheetName | $rowRangeLabel]")
                  appendLine("Columns: ${headers.joinToString(" | ")}")
                  sliceRows.forEachIndexed { offset, row ->
                    val actualRowNum = startSheetRow + offset
                    val paired = headers.indices.joinToString(", ") { colIdx ->
                      val colName = headers.getOrNull(colIdx).orEmpty().ifBlank { "Col${colIdx + 1}" }
                      val cellVal = row.getOrNull(colIdx).orEmpty()
                      "$colName=$cellVal"
                    }
                    appendLine("Row $actualRowNum: $paired | Raw: ${row.joinToString(" | ")}")
                  }
                }.trim()

                rawChunks.add(
                  buildChunk(
                    documentId = documentId,
                    userUid = cleanUser,
                    originalFilename = safeFilename,
                    fileFormat = fileFormat,
                    pageNumber = null,
                    sectionTitle = null,
                    sheetName = sheetName,
                    rowRange = rowRangeLabel,
                    chunkIndex = rawChunks.size,
                    startOffset = charCursor,
                    endOffset = charCursor + content.length,
                    content = content,
                    extractionVersion = extractionVersion,
                    importTimestampMs = importTimestampMs,
                  )
                )
                charCursor += content.length + 1
                rowIdx = endIdx
              }
            }
          }
        } else {
          chunkTabularFallbackText(
            rawChunks = rawChunks,
            documentId = documentId,
            userUid = cleanUser,
            originalFilename = safeFilename,
            fileFormat = fileFormat,
            sheetName = "Sheet1",
            csvText = extractedText,
            extractionVersion = extractionVersion,
            importTimestampMs = importTimestampMs,
          )
        }
      }

      KnowledgeFileFormat.CSV -> {
        chunkTabularFallbackText(
          rawChunks = rawChunks,
          documentId = documentId,
          userUid = cleanUser,
          originalFilename = safeFilename,
          fileFormat = fileFormat,
          sheetName = "CSV",
          csvText = extractedText,
          extractionVersion = extractionVersion,
          importTimestampMs = importTimestampMs,
        )
      }

      KnowledgeFileFormat.SOURCE_CODE -> {
        chunkSourceCodeText(
          rawChunks = rawChunks,
          documentId = documentId,
          userUid = cleanUser,
          originalFilename = safeFilename,
          code = extractedText,
          maxChunkChars = cappedMaxChars,
          overlapChars = cappedOverlap,
          extractionVersion = extractionVersion,
          importTimestampMs = importTimestampMs,
        )
      }

      KnowledgeFileFormat.DOCX,
      KnowledgeFileFormat.MARKDOWN,
      KnowledgeFileFormat.TXT -> {
        chunkHeadingAwareText(
          rawChunks = rawChunks,
          documentId = documentId,
          userUid = cleanUser,
          originalFilename = safeFilename,
          fileFormat = fileFormat,
          fullText = extractedText,
          pageNumber = null,
          maxChunkChars = cappedMaxChars,
          overlapChars = cappedOverlap,
          extractionVersion = extractionVersion,
          importTimestampMs = importTimestampMs,
        )
      }
    }

    return rawChunks
  }

  private fun chunkHeadingAwareText(
    rawChunks: MutableList<DocumentChunk>,
    documentId: String,
    userUid: String,
    originalFilename: String,
    fileFormat: KnowledgeFileFormat,
    fullText: String,
    pageNumber: Int?,
    maxChunkChars: Int,
    overlapChars: Int,
    extractionVersion: Int,
    importTimestampMs: Long,
  ) {
    if (fullText.isBlank()) return
    val sections = splitByHeadingsAndParagraphs(fullText)
    var runningOffset = 0
    for ((sectionTitle, sectionBody) in sections) {
      if (rawChunks.size >= MAX_CHUNKS_PER_DOCUMENT) break
      val slices = slicePreservingSentencesAndTables(sectionBody, maxChunkChars, overlapChars)
      for (slice in slices) {
        if (rawChunks.size >= MAX_CHUNKS_PER_DOCUMENT) break
        rawChunks.add(
          buildChunk(
            documentId = documentId,
            userUid = userUid,
            originalFilename = originalFilename,
            fileFormat = fileFormat,
            pageNumber = pageNumber,
            sectionTitle = sectionTitle,
            sheetName = null,
            rowRange = null,
            chunkIndex = rawChunks.size,
            startOffset = runningOffset + slice.first,
            endOffset = runningOffset + slice.second,
            content = slice.third,
            extractionVersion = extractionVersion,
            importTimestampMs = importTimestampMs,
          )
        )
      }
      runningOffset += sectionBody.length + 1
    }
  }

  private fun chunkTabularFallbackText(
    rawChunks: MutableList<DocumentChunk>,
    documentId: String,
    userUid: String,
    originalFilename: String,
    fileFormat: KnowledgeFileFormat,
    sheetName: String,
    csvText: String,
    extractionVersion: Int,
    importTimestampMs: Long,
  ) {
    val rows = AstrynProductivityEngine.AstrynSpreadsheetEngine.parseCsvRows(csvText)
    if (rows.isEmpty()) return
    val headers = rows.first()
    val dataRows = if (rows.size > 1) rows.drop(1) else emptyList()
    var charCursor = 0
    if (dataRows.isEmpty()) {
      val content = headers.joinToString(" | ")
      rawChunks.add(
        buildChunk(
          documentId = documentId,
          userUid = userUid,
          originalFilename = originalFilename,
          fileFormat = fileFormat,
          pageNumber = null,
          sectionTitle = null,
          sheetName = sheetName,
          rowRange = "Rows 1–1",
          chunkIndex = rawChunks.size,
          startOffset = 0,
          endOffset = content.length,
          content = content,
          extractionVersion = extractionVersion,
          importTimestampMs = importTimestampMs,
        )
      )
      return
    }

    val rowsPerChunk = 12
    var i = 0
    while (i < dataRows.size && rawChunks.size < MAX_CHUNKS_PER_DOCUMENT) {
      val end = minOf(i + rowsPerChunk, dataRows.size)
      val startRowNum = i + 2
      val endRowNum = end + 1
      val rowRange = "Rows $startRowNum–$endRowNum"
      val content = buildString {
        appendLine("[$sheetName | $rowRange]")
        appendLine("Columns: ${headers.joinToString(" | ")}")
        for (idx in i until end) {
          val row = dataRows[idx]
          val actualRowNum = idx + 2
          val paired = headers.indices.joinToString(", ") { colIdx ->
            val colName = headers.getOrNull(colIdx).orEmpty().ifBlank { "Col${colIdx + 1}" }
            "$colName=${row.getOrNull(colIdx).orEmpty()}"
          }
          appendLine("Row $actualRowNum: $paired")
        }
      }.trim()
      rawChunks.add(
        buildChunk(
          documentId = documentId,
          userUid = userUid,
          originalFilename = originalFilename,
          fileFormat = fileFormat,
          pageNumber = null,
          sectionTitle = null,
          sheetName = sheetName,
          rowRange = rowRange,
          chunkIndex = rawChunks.size,
          startOffset = charCursor,
          endOffset = charCursor + content.length,
          content = content,
          extractionVersion = extractionVersion,
          importTimestampMs = importTimestampMs,
        )
      )
      charCursor += content.length + 1
      i = end
    }
  }

  private fun chunkSourceCodeText(
    rawChunks: MutableList<DocumentChunk>,
    documentId: String,
    userUid: String,
    originalFilename: String,
    code: String,
    maxChunkChars: Int,
    overlapChars: Int,
    extractionVersion: Int,
    importTimestampMs: Long,
  ) {
    if (code.isBlank()) return
    val symbolHeaderRegex =
      Regex("""^\s*(?:public|private|internal|protected|suspend|override|open|data|sealed|export|async)?\s*(?:fun|class|object|interface|def|function|const\s+\w+\s*=|SELECT|CREATE\s+TABLE)\s+([A-Za-z0-9_]+)""")
    val lines = code.lines()
    val blocks = mutableListOf<Pair<String?, String>>()
    var currentSymbol: String? = null
    val currentBlock = StringBuilder()

    for (line in lines) {
      val match = symbolHeaderRegex.find(line)
      if (match != null && currentBlock.length > 180) {
        blocks.add(currentSymbol to currentBlock.toString().trimEnd())
        currentBlock.clear()
        currentSymbol = match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
      } else if (currentSymbol == null && match != null) {
        currentSymbol = match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
      }
      currentBlock.appendLine(line)
    }
    if (currentBlock.isNotBlank()) {
      blocks.add(currentSymbol to currentBlock.toString().trimEnd())
    }

    var offset = 0
    for ((symbol, body) in blocks) {
      if (rawChunks.size >= MAX_CHUNKS_PER_DOCUMENT) break
      val slices = slicePreservingSentencesAndTables(body, maxChunkChars, overlapChars)
      for (slice in slices) {
        if (rawChunks.size >= MAX_CHUNKS_PER_DOCUMENT) break
        rawChunks.add(
          buildChunk(
            documentId = documentId,
            userUid = userUid,
            originalFilename = originalFilename,
            fileFormat = KnowledgeFileFormat.SOURCE_CODE,
            pageNumber = null,
            sectionTitle = symbol?.let { "Symbol: $it" },
            sheetName = null,
            rowRange = null,
            chunkIndex = rawChunks.size,
            startOffset = offset + slice.first,
            endOffset = offset + slice.second,
            content = slice.third,
            extractionVersion = extractionVersion,
            importTimestampMs = importTimestampMs,
          )
        )
      }
      offset += body.length + 1
    }
  }

  internal fun splitByHeadingsAndParagraphs(text: String): List<Pair<String?, String>> {
    val headingRegex =
      Regex("""^(?:#{1,6}\s+(.+)|((?:Chapter|Section|Unit|Part|अध्याय|भाग)\s+[0-9A-Za-z.\-]+(?:\s*[:\-–]\s*.+)?)|([A-Z][A-Za-z0-9\s,&()\-/]{2,55}):)$""")
    val lines = text.lines()
    val sections = mutableListOf<Pair<String?, String>>()
    var activeHeading: String? = null
    val buffer = StringBuilder()

    for (rawLine in lines) {
      val trimmed = rawLine.trim()
      if (trimmed.isEmpty()) {
        if (buffer.isNotEmpty() && !buffer.endsWith("\n\n")) {
          buffer.append("\n\n")
        }
        continue
      }
      val headingMatch = headingRegex.find(trimmed)
      val detectedTitle = headingMatch?.let { m ->
        (m.groupValues.drop(1).firstOrNull { it.isNotBlank() } ?: trimmed).trimEnd(':').trim()
      }
      if (detectedTitle != null && !trimmed.contains("|")) {
        val existingBody = buffer.toString().trim()
        if (existingBody.isNotEmpty()) {
          sections.add(activeHeading to existingBody)
          buffer.clear()
        }
        activeHeading = detectedTitle
        buffer.appendLine(trimmed)
      } else {
        buffer.appendLine(trimmed)
      }
    }
    val trailing = buffer.toString().trim()
    if (trailing.isNotEmpty()) {
      sections.add(activeHeading to trailing)
    }
    return if (sections.isEmpty() && text.isNotBlank()) {
      listOf(null to text.trim())
    } else {
      sections
    }
  }

  /**
   * Slices a section body into bounded chunks while keeping table blocks (`|`) intact where
   * feasible and avoiding mid-sentence cuts, carrying contextual overlap between adjacent chunks.
   */
  internal fun slicePreservingSentencesAndTables(
    body: String,
    maxChunkChars: Int,
    overlapChars: Int,
  ): List<Triple<Int, Int, String>> {
    val trimmed = body.trim()
    if (trimmed.isEmpty()) return emptyList()
    if (trimmed.length <= maxChunkChars) {
      return listOf(Triple(0, trimmed.length, trimmed))
    }

    // Group into logical blocks: paragraphs and contiguous Markdown/ASCII tables
    val logicalUnits = mutableListOf<String>()
    val paragraphs = trimmed.split(Regex("""\n{2,}"""))
    for (p in paragraphs) {
      val cleanP = p.trim()
      if (cleanP.isEmpty()) continue
      if (cleanP.length <= maxChunkChars || cleanP.lines().all { it.contains("|") }) {
        logicalUnits.add(cleanP)
      } else {
        // Split oversized paragraph strictly at sentence boundaries ('.', '!', '?', '।', '\n')
        val sentenceRegex = Regex("""[^.!?\n।]+(?:[.!?।]+|\n|$)""")
        val sentences = sentenceRegex.findAll(cleanP).map { it.value.trim() }.filter { it.isNotEmpty() }.toList()
        if (sentences.isEmpty()) {
          logicalUnits.add(cleanP)
        } else {
          logicalUnits.addAll(sentences)
        }
      }
    }

    val result = mutableListOf<Triple<Int, Int, String>>()
    val current = StringBuilder()
    var chunkStart = 0
    var cursor = 0

    fun flushChunk() {
      val txt = current.toString().trim()
      if (txt.isNotEmpty()) {
        val endOff = (chunkStart + txt.length).coerceAtMost(trimmed.length)
        result.add(Triple(chunkStart, endOff, txt))
      }
    }

    for (unit in logicalUnits) {
      val unitLen = unit.length
      if (current.isEmpty()) {
        chunkStart = cursor
        if (unitLen <= maxChunkChars) {
          current.append(unit)
        } else {
          // Very large table or single unbroken block: hard-split cleanly at whitespace
          var pos = 0
          while (pos < unit.length) {
            var end = minOf(pos + maxChunkChars, unit.length)
            if (end < unit.length) {
              val lastSpace = unit.lastIndexOf(' ', end)
              if (lastSpace > pos + maxChunkChars / 2) end = lastSpace
            }
            val part = unit.substring(pos, end).trim()
            if (part.isNotEmpty()) {
              result.add(Triple(chunkStart + pos, chunkStart + end, part))
            }
            pos = if (end < unit.length) (end - overlapChars).coerceAtLeast(pos + 1) else unit.length
          }
        }
      } else if (current.length + 2 + unitLen <= maxChunkChars) {
        current.append("\n\n").append(unit)
      } else {
        val prevText = current.toString().trim()
        flushChunk()
        current.clear()
        // Carry contextual overlap tail cleanly at word/sentence boundary
        val overlapSeed =
          if (overlapChars > 0 && prevText.length > overlapChars) {
            val tail = prevText.takeLast(overlapChars)
            val spaceIdx = tail.indexOf(' ')
            if (spaceIdx in 0 until tail.length - 1) tail.substring(spaceIdx + 1).trim() else tail
          } else {
            ""
          }
        chunkStart = cursor
        if (overlapSeed.isNotEmpty() && overlapSeed.length + 2 + unitLen <= maxChunkChars) {
          current.append(overlapSeed).append("\n\n").append(unit)
        } else {
          current.append(unit.take(maxChunkChars))
        }
      }
      cursor += unitLen + 2
    }
    flushChunk()
    return result
  }

  private fun buildChunk(
    documentId: String,
    userUid: String,
    originalFilename: String,
    fileFormat: KnowledgeFileFormat,
    pageNumber: Int?,
    sectionTitle: String?,
    sheetName: String?,
    rowRange: String?,
    chunkIndex: Int,
    startOffset: Int,
    endOffset: Int,
    content: String,
    extractionVersion: Int,
    importTimestampMs: Long,
  ): DocumentChunk {
    val cleanContent = content.trim()
    val hash = AstrynKnowledgeBaseEngine.sha256Hex(
      "$originalFilename|p=${pageNumber ?: ""}|s=${sectionTitle ?: ""}|sh=${sheetName ?: ""}|$cleanContent"
    )
    val topics = extractTopicKeywords(cleanContent, sectionTitle)
    return DocumentChunk(
      chunkId = "${documentId}_c$chunkIndex",
      documentId = documentId,
      userUid = userUid,
      originalFilename = originalFilename,
      fileFormat = fileFormat,
      pageNumber = pageNumber,
      sectionTitle = sectionTitle,
      sheetName = sheetName,
      rowRange = rowRange,
      chunkIndex = chunkIndex,
      startOffset = startOffset.coerceAtLeast(0),
      endOffset = endOffset.coerceAtLeast(startOffset.coerceAtLeast(0)),
      content = cleanContent,
      contentHash = hash,
      extractionVersion = extractionVersion,
      topicTags = topics,
      importTimestampMs = importTimestampMs,
    )
  }

  internal fun extractTopicKeywords(content: String, sectionTitle: String?): Set<String> {
    val combined = "${sectionTitle.orEmpty()} $content"
    val stopWords = setOf(
      "the", "and", "for", "that", "with", "this", "from", "are", "was", "were", "have",
      "has", "had", "not", "but", "what", "when", "where", "which", "who", "how", "will",
      "can", "could", "should", "would", "into", "about", "there", "their", "they", "been",
      "hai", "hain", "aur", "mein", "par", "se", "ko", "ka", "ki", "ke", "liye", "kya",
    )
    return combined
      .lowercase(Locale.ROOT)
      .split(Regex("[^a-z0-9\\u0900-\\u097F]+"))
      .filter { it.length >= 3 && it !in stopWords }
      .groupingBy { it }
      .eachCount()
      .entries
      .sortedByDescending { it.value }
      .take(12)
      .map { it.key }
      .toSet()
  }
}

object AstrynKnowledgeBaseEngine {

  const val CURRENT_INDEX_VERSION = 1
  const val MAX_KB_FILE_BYTES = AttachmentProcessor.MAX_FILE_SIZE_BYTES

  private val userDocuments =
    ConcurrentHashMap<String, ConcurrentHashMap<String, KnowledgeDocumentRecord>>()
  private val userChunks =
    ConcurrentHashMap<String, ConcurrentHashMap<String, List<DocumentChunk>>>()
  private val activeIndexingLocks = ConcurrentHashMap<String, Mutex>()

  @Volatile var defaultOcrGateway: OcrGateway? = null

  fun normalizeUserScope(userUid: String?): String {
    val trimmed = userUid?.trim().orEmpty()
    return if (trimmed.isEmpty()) "anonymous_local_user" else trimmed
  }

  fun sha256Hex(raw: String): String = sha256Hex(raw.toByteArray(StandardCharsets.UTF_8))

  fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString("") { "%02x".format(it) }
  }

  internal fun detectLanguage(text: String): String {
    if (text.isBlank()) return "UNKNOWN"
    val devanagariCount = text.count { it in '\u0900'..'\u097F' }
    val latinCount = text.count { it in 'a'..'z' || it in 'A'..'Z' }
    return when {
      devanagariCount > 10 && latinCount > 20 -> "HINDI_ENGLISH_MIXED"
      devanagariCount > 5 -> "HINDI"
      latinCount > 0 -> "ENGLISH"
      else -> "UNKNOWN"
    }
  }

  private fun lockFor(userScope: String, docKey: String): Mutex {
    return activeIndexingLocks.getOrPut("$userScope::$docKey") { Mutex() }
  }

  /**
   * Ingests raw file bytes, validates format & size, extracts text/pages/sheets, handles
   * image-only PDFs honestly via optional [ocrGateway], chunks content, indexes into
   * [AstrynSemanticSearchEngine] & [AstrynKnowledgeGraphEngine], and verifies retrieval.
   */
  suspend fun ingestRawBytes(
    userUid: String?,
    fileName: String,
    mimeType: String,
    bytes: ByteArray,
    documentId: String? = null,
    lastModifiedTimestampMs: Long? = null,
    storageRootDir: File? = null,
    cacheDir: File? = null,
    ocrGateway: OcrGateway? = defaultOcrGateway,
    embeddingGateway: EmbeddingGateway? = AstrynSemanticSearchEngine.defaultEmbeddingGateway,
    isOnline: Boolean = true,
    forceReindex: Boolean = false,
  ): Result<IngestionResult> {
    val scope = normalizeUserScope(userUid)
    val safeName = AstrynSecurityGuard.sanitizeFileName(fileName)
    if (bytes.isEmpty()) {
      return Result.failure(IllegalArgumentException("\"$safeName\" is empty (0 bytes) and cannot be indexed."))
    }
    if (bytes.size.toLong() > MAX_KB_FILE_BYTES) {
      return Result.failure(
        IllegalArgumentException(
          "\"$safeName\" exceeds the ${AttachmentProcessor.MAX_FILE_SIZE_BYTES / (1024 * 1024)} MB knowledge base file limit."
        )
      )
    }

    val docHash = sha256Hex(bytes)
    val lockKey = documentId ?: safeName.lowercase(Locale.ROOT)
    val mutex = lockFor(scope, lockKey)

    return mutex.withLock {
      currentCoroutineContext().ensureActive()

      // 1. Check duplicate document by content hash before doing expensive work
      val existingBucket = userDocuments.getOrPut(scope) { ConcurrentHashMap() }
      val duplicate = existingBucket.values.firstOrNull {
        it.documentHash == docHash && it.indexingStatus == IndexingStatus.INDEXED && !forceReindex
      }
      if (duplicate != null && (documentId == null || duplicate.documentId == documentId)) {
        val existingChunks = userChunks[scope]?.get(duplicate.documentId).orEmpty()
        return@withLock Result.success(
          IngestionResult(
            document = duplicate,
            chunks = existingChunks,
            wasDeduplicated = true,
            wasReindexed = false,
            embeddingsGeneratedCount = 0,
            searchModeUsed = "DEDUPLICATED_EXISTING_INDEX",
            verificationPassed = existingChunks.isNotEmpty(),
          )
        )
      }

      // 2. Process raw bytes through existing AttachmentProcessor
      val attachedRes = AttachmentProcessor.processRawBytes(
        fileName = safeName,
        mimeType = mimeType,
        bytes = bytes,
        cacheDir = cacheDir,
      )
      if (attachedRes.isFailure) {
        return@withLock Result.failure(
          attachedRes.exceptionOrNull() ?: IllegalArgumentException("Failed to extract \"$safeName\".")
        )
      }
      val attachment = attachedRes.getOrThrow()
      val format = KnowledgeFileFormat.fromAttachmentKind(attachment.kind)
        ?: return@withLock Result.failure(
          IllegalArgumentException(
            "\"$safeName\" is an image file. Image uploads belong to multimodal chat; Knowledge Base indexes PDF, DOCX, TXT, Markdown, CSV, XLSX, and Source Code."
          )
        )

      // 3. Extract multi-sheet XLSX data if applicable
      val xlsxSheets: List<Pair<String, List<List<String>>>> =
        if (format == KnowledgeFileFormat.XLSX) {
          AstrynProductivityEngine.AstrynSpreadsheetEngine.readWorkbookFromXlsxBytes(bytes, safeName)
            .getOrNull()
            ?.sheets
            ?.map { sheet ->
              sheet.sheetName to sheet.rows.map { row -> row.map { cell -> cell.evaluatedValue } }
            }
            .orEmpty()
        } else {
          emptyList()
        }

      ingestExtractedAttachment(
        userUid = scope,
        attachment = attachment,
        documentHashOverride = docHash,
        rawBytes = bytes,
        xlsxSheets = xlsxSheets,
        documentIdOverride = documentId,
        lastModifiedTimestampMs = lastModifiedTimestampMs,
        storageRootDir = storageRootDir,
        ocrGateway = ocrGateway,
        embeddingGateway = embeddingGateway,
        isOnline = isOnline,
        forceReindex = forceReindex,
      )
    }
  }

  /**
   * Ingests an already-parsed [ChatAttachment] into the user's personal knowledge base.
   */
  suspend fun ingestAttachment(
    userUid: String?,
    attachment: ChatAttachment,
    documentIdOverride: String? = null,
    lastModifiedTimestampMs: Long? = null,
    storageRootDir: File? = null,
    ocrGateway: OcrGateway? = defaultOcrGateway,
    embeddingGateway: EmbeddingGateway? = AstrynSemanticSearchEngine.defaultEmbeddingGateway,
    isOnline: Boolean = true,
    forceReindex: Boolean = false,
  ): Result<IngestionResult> {
    val scope = normalizeUserScope(userUid)
    val safeName = AstrynSecurityGuard.sanitizeFileName(attachment.fileName)
    val format = KnowledgeFileFormat.fromAttachmentKind(attachment.kind)
      ?: return Result.failure(
        IllegalArgumentException("\"$safeName\" is not a document format supported for knowledge-base indexing.")
      )
    val contentForHash = attachment.extractedText.orEmpty() + "|" + safeName + "|" + attachment.fileSizeBytes
    val hash = sha256Hex(contentForHash)
    val mutex = lockFor(scope, documentIdOverride ?: safeName.lowercase(Locale.ROOT))

    return mutex.withLock {
      val existingBucket = userDocuments.getOrPut(scope) { ConcurrentHashMap() }
      val duplicate = existingBucket.values.firstOrNull {
        it.documentHash == hash && it.indexingStatus == IndexingStatus.INDEXED && !forceReindex
      }
      if (duplicate != null && (documentIdOverride == null || duplicate.documentId == documentIdOverride)) {
        val existingChunks = userChunks[scope]?.get(duplicate.documentId).orEmpty()
        return@withLock Result.success(
          IngestionResult(
            document = duplicate,
            chunks = existingChunks,
            wasDeduplicated = true,
            wasReindexed = false,
            embeddingsGeneratedCount = 0,
            searchModeUsed = "DEDUPLICATED_EXISTING_INDEX",
            verificationPassed = existingChunks.isNotEmpty(),
          )
        )
      }

      ingestExtractedAttachment(
        userUid = scope,
        attachment = attachment,
        documentHashOverride = hash,
        rawBytes = null,
        xlsxSheets = emptyList(),
        documentIdOverride = documentIdOverride,
        lastModifiedTimestampMs = lastModifiedTimestampMs,
        storageRootDir = storageRootDir,
        ocrGateway = ocrGateway,
        embeddingGateway = embeddingGateway,
        isOnline = isOnline,
        forceReindex = forceReindex,
      )
    }
  }

  private suspend fun ingestExtractedAttachment(
    userUid: String,
    attachment: ChatAttachment,
    documentHashOverride: String,
    rawBytes: ByteArray?,
    xlsxSheets: List<Pair<String, List<List<String>>>>,
    documentIdOverride: String?,
    lastModifiedTimestampMs: Long?,
    storageRootDir: File?,
    ocrGateway: OcrGateway?,
    embeddingGateway: EmbeddingGateway?,
    isOnline: Boolean,
    forceReindex: Boolean,
  ): Result<IngestionResult> {
    val now = System.currentTimeMillis()
    val safeName = AstrynSecurityGuard.sanitizeFileName(attachment.fileName)
    val format = KnowledgeFileFormat.fromAttachmentKind(attachment.kind)
      ?: return Result.failure(IllegalArgumentException("Unsupported format for \"$safeName\"."))

    val existingBucket = userDocuments.getOrPut(userUid) { ConcurrentHashMap() }
    val targetDocId = documentIdOverride
      ?: existingBucket.values.firstOrNull { it.originalFilename.equals(safeName, ignoreCase = true) }?.documentId
      ?: "doc_${UUID.randomUUID().toString().take(12)}"
    val previousDoc = existingBucket[targetDocId]
    val nextDocVersion = if (previousDoc != null) previousDoc.documentVersion + 1 else 1
    val wasReindex = previousDoc != null

    // Handle OCR for scanned/image-only PDFs honestly
    var effectivePageMap = attachment.pageTextMap
    var effectiveText = attachment.extractedText.orEmpty()
    var ocrStatus = OcrStatus.NOT_NEEDED
    var extractionStatus = ExtractionStatus.SUCCESS
    val warnings = mutableListOf<String>()

    if (format == KnowledgeFileFormat.PDF && attachment.isScannedOrImageBasedPdf) {
      if (ocrGateway != null) {
        val ocrRes = ocrGateway.extractTextFromScannedPdf(
          fileName = safeName,
          renderedPageImagesBase64 = attachment.renderedPageImagesBase64,
          rawBytes = rawBytes,
        )
        val ocrPages = ocrRes.getOrNull().orEmpty().filterValues { it.isNotBlank() }
        if (ocrPages.isNotEmpty()) {
          effectivePageMap = ocrPages
          effectiveText = ocrPages.entries.sortedBy { it.key }.joinToString("\n\n") { (p, txt) ->
            "[Page $p of ${attachment.pageCount ?: ocrPages.size}]\n$txt"
          }
          ocrStatus = OcrStatus.OCR_APPLIED
          extractionStatus = ExtractionStatus.SUCCESS
        } else {
          ocrStatus = OcrStatus.OCR_UNAVAILABLE
          extractionStatus = ExtractionStatus.PARTIAL_OCR_UNAVAILABLE
          warnings.add(
            "\"$safeName\" is an image-only/scanned PDF and OCR returned no readable text. No scanned text was fabricated."
          )
        }
      } else {
        ocrStatus = OcrStatus.OCR_UNAVAILABLE
        extractionStatus = ExtractionStatus.PARTIAL_OCR_UNAVAILABLE
        warnings.add(
          "\"$safeName\" appears to be an image-only PDF without selectable text, and no on-device/cloud OCR engine is configured. Document metadata recorded without fabricating unreadable pages."
        )
      }
    } else if (attachment.omittedPageCount != null && attachment.omittedPageCount > 0) {
      extractionStatus = ExtractionStatus.PARTIAL_PAGE_LIMIT
      warnings.add("Analyzed pages 1–${attachment.analyzedPageCount}; ${attachment.omittedPageCount} trailing pages exceeded the extraction cap.")
    }

    if (format == KnowledgeFileFormat.XLSX && xlsxSheets.isNotEmpty()) {
      effectiveText = buildString {
        for ((sName, rows) in xlsxSheets) {
          if (isNotEmpty()) appendLine()
          appendLine("[Sheet: $sName]")
          rows.forEachIndexed { idx, row ->
            appendLine("Row ${idx + 1}: ${row.joinToString(" | ")}")
          }
        }
      }.trim()
    }

    // Mark transient INDEXING checkpoint so interrupted operations can recover cleanly
    val pendingRecord = KnowledgeDocumentRecord(
      documentId = targetDocId,
      userUid = userUid,
      originalFilename = safeName,
      fileFormat = format,
      fileSizeBytes = attachment.fileSizeBytes,
      documentHash = documentHashOverride,
      importTimestampMs = previousDoc?.importTimestampMs ?: now,
      lastModifiedTimestampMs = lastModifiedTimestampMs ?: now,
      extractionStatus = extractionStatus,
      indexingStatus = IndexingStatus.INDEXING,
      pageCount = attachment.pageCount,
      detectedLanguage = detectLanguage(effectiveText),
      chunkCount = 0,
      documentVersion = nextDocVersion,
      indexVersion = CURRENT_INDEX_VERSION,
      ocrStatus = ocrStatus,
      errorInfo = warnings.firstOrNull(),
      extractedText = effectiveText,
      pageTextMap = effectivePageMap,
      sheetNames = xlsxSheets.map { it.first },
      containsSensitiveWarning =
        AstrynPersonalIntelligenceEngine.containsSensitiveOrForbiddenMemoryData(effectiveText) ||
          SafeDiagnosticLogger.containsSecretCredential(effectiveText),
    )
    existingBucket[targetDocId] = pendingRecord

    try {
      currentCoroutineContext().ensureActive()

      // Clear previous vectors / graph edges before re-indexing this document
      if (wasReindex || forceReindex) {
        AstrynSemanticSearchEngine.removeDocumentVectors(userUid, targetDocId)
        AstrynKnowledgeGraphEngine.removeDocumentGraphData(userUid, targetDocId)
      }

      val chunks =
        if (extractionStatus == ExtractionStatus.PARTIAL_OCR_UNAVAILABLE) {
          emptyList()
        } else {
          AstrynDocumentChunker.chunkDocument(
            documentId = targetDocId,
            userUid = userUid,
            originalFilename = safeName,
            fileFormat = format,
            extractedText = effectiveText,
            pageTextMap = effectivePageMap,
            xlsxSheets = xlsxSheets,
            importTimestampMs = now,
          )
        }

      if (chunks.isEmpty() && extractionStatus != ExtractionStatus.PARTIAL_OCR_UNAVAILABLE) {
        throw IllegalStateException("No readable chunks could be extracted from \"$safeName\".")
      }

      currentCoroutineContext().ensureActive()

      // Index chunks (vector embeddings if available + lexical preparation)
      val indexingOutcome = AstrynSemanticSearchEngine.indexDocumentChunks(
        userUid = userUid,
        document = pendingRecord.copy(chunkCount = chunks.size),
        chunks = chunks,
        embeddingGateway = embeddingGateway,
        isOnline = isOnline,
      )

      // Update Knowledge Graph from extracted chunks
      if (chunks.isNotEmpty()) {
        AstrynKnowledgeGraphEngine.indexDocumentChunksIntoGraph(
          userUid = userUid,
          document = pendingRecord,
          chunks = chunks,
        )
      }

      val completedRecord = pendingRecord.copy(
        indexingStatus = IndexingStatus.INDEXED,
        chunkCount = chunks.size,
        errorInfo = warnings.joinToString(" | ").ifBlank { null },
      )

      // Commit chunks + record atomically
      val chunkBucket = userChunks.getOrPut(userUid) { ConcurrentHashMap() }
      chunkBucket[targetDocId] = chunks
      existingBucket[targetDocId] = completedRecord
      AstrynSemanticSearchEngine.invalidateUserCache(userUid)

      if (storageRootDir != null) {
        persistUserKnowledgeSnapshot(storageRootDir, userUid)
      }

      // Verify retrieval works for indexed content (Step 11 of Section 3)
      val probeTerms = chunks.firstOrNull()?.topicTags?.take(2)?.joinToString(" ").orEmpty()
      val verified = if (chunks.isEmpty() || probeTerms.isBlank()) {
        extractionStatus == ExtractionStatus.PARTIAL_OCR_UNAVAILABLE
      } else {
        AstrynSemanticSearchEngine.search(
          userUid = userUid,
          query = probeTerms,
          topK = 3,
          filter = SearchFilters(documentIds = setOf(targetDocId)),
          embeddingGateway = null, // fast lexical probe verification
          isOnline = false,
        ).matches.any { it.chunk.documentId == targetDocId }
      }

      return Result.success(
        IngestionResult(
          document = completedRecord,
          chunks = chunks,
          wasDeduplicated = false,
          wasReindexed = wasReindex,
          embeddingsGeneratedCount = indexingOutcome.embeddingsGeneratedCount,
          searchModeUsed = indexingOutcome.modeUsed.name,
          verificationPassed = verified,
          warnings = warnings,
        )
      )
    } catch (ce: CancellationException) {
      // Interrupted indexing: cleanly rollback incomplete vectors/chunks and mark INTERRUPTED
      AstrynSemanticSearchEngine.removeDocumentVectors(userUid, targetDocId)
      AstrynKnowledgeGraphEngine.removeDocumentGraphData(userUid, targetDocId)
      userChunks[userUid]?.remove(targetDocId)
      existingBucket[targetDocId] = pendingRecord.copy(
        indexingStatus = IndexingStatus.INTERRUPTED,
        errorInfo = "Indexing was interrupted before completion and can be recovered cleanly.",
      )
      throw ce
    } catch (t: Throwable) {
      // Failed import: zero orphan chunks/vectors/graph records
      AstrynSemanticSearchEngine.removeDocumentVectors(userUid, targetDocId)
      AstrynKnowledgeGraphEngine.removeDocumentGraphData(userUid, targetDocId)
      userChunks[userUid]?.remove(targetDocId)
      if (previousDoc != null) {
        existingBucket[targetDocId] = previousDoc
      } else {
        existingBucket.remove(targetDocId)
      }
      AstrynDiagnosticsManager.recordEvent(
        subsystem = AstrynDiagnosticsManager.Subsystem.ATTACHMENT_PROCESSOR,
        severity = AstrynDiagnosticsManager.Severity.ERROR,
        operation = "AstrynKnowledgeBaseEngine.ingest",
        rawMessage = t.message ?: "Knowledge document ingestion failed",
      )
      return Result.failure(t)
    }
  }

  /**
   * Re-indexes an existing document owned by [userUid] after verifying ownership.
   */
  suspend fun reIndexDocument(
    userUid: String?,
    documentId: String,
    storageRootDir: File? = null,
    embeddingGateway: EmbeddingGateway? = AstrynSemanticSearchEngine.defaultEmbeddingGateway,
    isOnline: Boolean = true,
  ): Result<IngestionResult> {
    val scope = normalizeUserScope(userUid)
    val record = getDocumentRecord(scope, documentId)
      ?: return Result.failure(SecurityException("Document '$documentId' not found or access denied."))

    val dummyAttachment = ChatAttachment(
      id = record.documentId,
      fileName = record.originalFilename,
      mimeType = when (record.fileFormat) {
        KnowledgeFileFormat.PDF -> "application/pdf"
        KnowledgeFileFormat.DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        KnowledgeFileFormat.XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        KnowledgeFileFormat.CSV -> "text/csv"
        KnowledgeFileFormat.MARKDOWN -> "text/markdown"
        else -> "text/plain"
      },
      fileSizeBytes = record.fileSizeBytes,
      kind = when (record.fileFormat) {
        KnowledgeFileFormat.PDF -> AttachmentKind.PDF
        KnowledgeFileFormat.DOCX -> AttachmentKind.DOCX
        KnowledgeFileFormat.XLSX -> AttachmentKind.XLSX
        KnowledgeFileFormat.CSV -> AttachmentKind.CSV
        KnowledgeFileFormat.MARKDOWN -> AttachmentKind.MARKDOWN
        KnowledgeFileFormat.SOURCE_CODE -> AttachmentKind.SOURCE_CODE
        KnowledgeFileFormat.TXT -> AttachmentKind.TXT
      },
      pageCount = record.pageCount,
      extractedText = record.extractedText,
      isScannedOrImageBasedPdf = record.ocrStatus != OcrStatus.NOT_NEEDED && record.extractedText.isBlank(),
      pageTextMap = record.pageTextMap,
    )

    return ingestAttachment(
      userUid = scope,
      attachment = dummyAttachment,
      documentIdOverride = record.documentId,
      lastModifiedTimestampMs = System.currentTimeMillis(),
      storageRootDir = storageRootDir,
      embeddingGateway = embeddingGateway,
      isOnline = isOnline,
      forceReindex = true,
    )
  }

  /**
   * Recovers any documents left in `INDEXING` or `INTERRUPTED` state for [userUid].
   */
  suspend fun recoverInterruptedIndexing(
    userUid: String?,
    storageRootDir: File? = null,
    embeddingGateway: EmbeddingGateway? = AstrynSemanticSearchEngine.defaultEmbeddingGateway,
    isOnline: Boolean = true,
  ): List<String> {
    val scope = normalizeUserScope(userUid)
    val bucket = userDocuments[scope] ?: return emptyList()
    val stalledIds = bucket.values
      .filter { it.indexingStatus == IndexingStatus.INTERRUPTED || it.indexingStatus == IndexingStatus.INDEXING }
      .map { it.documentId }

    val recovered = mutableListOf<String>()
    for (docId in stalledIds) {
      val res = reIndexDocument(scope, docId, storageRootDir, embeddingGateway, isOnline)
      if (res.isSuccess) {
        recovered.add(docId)
      } else {
        deleteDocumentIndex(scope, docId, storageRootDir)
      }
    }
    return recovered
  }

  /**
   * Simulates or marks a stalled indexing job as interrupted (e.g. crash recovery testing).
   */
  internal fun insertStalledRecordForRecovery(record: KnowledgeDocumentRecord) {
    val scope = normalizeUserScope(record.userUid)
    userDocuments.getOrPut(scope) { ConcurrentHashMap() }[record.documentId] =
      record.copy(userUid = scope)
  }

  fun checkIndexingStatus(userUid: String?, documentId: String): IndexingStatus? {
    return getDocumentRecord(userUid, documentId)?.indexingStatus
  }

  /**
   * Retrieves a document record ONLY after verifying strict [AstrynSecurityGuard.verifyUserOwnership].
   */
  fun getDocumentRecord(userUid: String?, documentId: String): KnowledgeDocumentRecord? {
    val scope = normalizeUserScope(userUid)
    val doc = userDocuments[scope]?.get(documentId) ?: return null
    return if (AstrynSecurityGuard.verifyUserOwnership(scope, doc.userUid)) doc else null
  }

  fun getDocumentChunks(userUid: String?, documentId: String): List<DocumentChunk> {
    val record = getDocumentRecord(userUid, documentId) ?: return emptyList()
    return userChunks[record.userUid]?.get(record.documentId).orEmpty()
  }

  fun listUserDocuments(userUid: String?): List<KnowledgeDocumentRecord> {
    val scope = normalizeUserScope(userUid)
    return userDocuments[scope]?.values
      ?.filter { AstrynSecurityGuard.verifyUserOwnership(scope, it.userUid) }
      ?.sortedByDescending { it.importTimestampMs }
      .orEmpty()
  }

  fun getAllUserChunks(userUid: String?): List<DocumentChunk> {
    val scope = normalizeUserScope(userUid)
    val docs = userDocuments[scope].orEmpty()
    val chunksMap = userChunks[scope] ?: return emptyList()
    val result = mutableListOf<DocumentChunk>()
    for ((docId, list) in chunksMap) {
      val owner = docs[docId]?.userUid
      if (owner != null && AstrynSecurityGuard.verifyUserOwnership(scope, owner)) {
        result.addAll(list)
      }
    }
    return result
  }

  /**
   * Deletes the index (chunks, vectors, graph edges) for [documentId] while keeping or marking
   * the metadata pending/deleted safely without leaving orphan records.
   */
  fun deleteDocumentIndex(
    userUid: String?,
    documentId: String,
    storageRootDir: File? = null,
  ): Boolean {
    val scope = normalizeUserScope(userUid)
    val existing = userDocuments[scope]?.get(documentId) ?: return false
    if (!AstrynSecurityGuard.verifyUserOwnership(scope, existing.userUid)) {
      return false
    }
    userChunks[scope]?.remove(documentId)
    AstrynSemanticSearchEngine.removeDocumentVectors(scope, documentId)
    AstrynKnowledgeGraphEngine.removeDocumentGraphData(scope, documentId)
    AstrynSemanticSearchEngine.invalidateUserCache(scope)
    userDocuments[scope]?.set(
      documentId,
      existing.copy(
        indexingStatus = IndexingStatus.PENDING,
        chunkCount = 0,
        lastModifiedTimestampMs = System.currentTimeMillis(),
      ),
    )
    if (storageRootDir != null) {
      persistUserKnowledgeSnapshot(storageRootDir, scope)
    }
    return true
  }

  /**
   * Atomically removes a document and ALL of its chunks, vectors, cached queries, and graph
   * provenance/relationships for [userUid]. Rejects cross-user deletion attempts.
   */
  fun deleteDocument(
    userUid: String?,
    documentId: String,
    storageRootDir: File? = null,
  ): Result<Unit> {
    val scope = normalizeUserScope(userUid)
    val existing = userDocuments[scope]?.get(documentId)
      ?: return Result.failure(SecurityException("Document '$documentId' not found or belongs to another user."))
    if (!AstrynSecurityGuard.verifyUserOwnership(scope, existing.userUid)) {
      return Result.failure(SecurityException("Cross-user document deletion denied."))
    }

    userDocuments[scope]?.remove(documentId)
    userChunks[scope]?.remove(documentId)
    AstrynSemanticSearchEngine.removeDocumentVectors(scope, documentId)
    AstrynKnowledgeGraphEngine.removeDocumentGraphData(scope, documentId)
    AstrynSemanticSearchEngine.invalidateUserCache(scope)
    if (storageRootDir != null) {
      persistUserKnowledgeSnapshot(storageRootDir, scope)
    }
    return Result.success(Unit)
  }

  suspend fun rebuildUserIndex(
    userUid: String?,
    storageRootDir: File? = null,
    embeddingGateway: EmbeddingGateway? = AstrynSemanticSearchEngine.defaultEmbeddingGateway,
    isOnline: Boolean = true,
  ): Int {
    val scope = normalizeUserScope(userUid)
    val docIds = listUserDocuments(scope).map { it.documentId }
    var count = 0
    for (docId in docIds) {
      if (reIndexDocument(scope, docId, storageRootDir, embeddingGateway, isOnline).isSuccess) {
        count++
      }
    }
    return count
  }

  // ============================================================================
  // ATOMIC PER-USER DISK PERSISTENCE (REUSING SAFE .tmp CLEANUP PATTERN)
  // ============================================================================

  fun persistUserKnowledgeSnapshot(storageRootDir: File, userUid: String?): Result<File> {
    val scope = normalizeUserScope(userUid)
    val safeUserDir = File(File(storageRootDir, "astryn_kb"), AstrynSecurityGuard.sanitizeFileName(scope))
    if (!safeUserDir.exists()) safeUserDir.mkdirs()

    val targetFile = File(safeUserDir, "knowledge_index.json")
    val tempFile = File(safeUserDir, ".tmp_knowledge_${UUID.randomUUID()}.json.tmp")

    return try {
      val docs = listUserDocuments(scope)
      val chunks = getAllUserChunks(scope)
      val rootJson = JSONObject().apply {
        put("userUid", scope)
        put("indexVersion", CURRENT_INDEX_VERSION)
        put("savedAtMs", System.currentTimeMillis())
        put(
          "documents",
          JSONArray().apply {
            docs.forEach { d ->
              put(
                JSONObject().apply {
                  put("documentId", d.documentId)
                  put("userUid", d.userUid)
                  put("originalFilename", d.originalFilename)
                  put("fileFormat", d.fileFormat.name)
                  put("fileSizeBytes", d.fileSizeBytes)
                  put("documentHash", d.documentHash)
                  put("importTimestampMs", d.importTimestampMs)
                  if (d.lastModifiedTimestampMs != null) put("lastModifiedTimestampMs", d.lastModifiedTimestampMs)
                  put("extractionStatus", d.extractionStatus.name)
                  put("indexingStatus", d.indexingStatus.name)
                  if (d.pageCount != null) put("pageCount", d.pageCount)
                  put("detectedLanguage", d.detectedLanguage)
                  put("chunkCount", d.chunkCount)
                  put("documentVersion", d.documentVersion)
                  put("indexVersion", d.indexVersion)
                  put("ocrStatus", d.ocrStatus.name)
                  if (d.errorInfo != null) put("errorInfo", d.errorInfo)
                  put("extractedText", d.extractedText.take(120_000))
                }
              )
            }
          },
        )
        put(
          "chunks",
          JSONArray().apply {
            chunks.forEach { c ->
              put(
                JSONObject().apply {
                  put("chunkId", c.chunkId)
                  put("documentId", c.documentId)
                  put("userUid", c.userUid)
                  put("originalFilename", c.originalFilename)
                  put("fileFormat", c.fileFormat.name)
                  if (c.pageNumber != null) put("pageNumber", c.pageNumber)
                  if (c.sectionTitle != null) put("sectionTitle", c.sectionTitle)
                  if (c.sheetName != null) put("sheetName", c.sheetName)
                  if (c.rowRange != null) put("rowRange", c.rowRange)
                  put("chunkIndex", c.chunkIndex)
                  put("startOffset", c.startOffset)
                  put("endOffset", c.endOffset)
                  put("content", c.content)
                  put("contentHash", c.contentHash)
                  put("extractionVersion", c.extractionVersion)
                  put("importTimestampMs", c.importTimestampMs)
                }
              )
            }
          },
        )
      }

      FileOutputStream(tempFile).use { fos ->
        fos.write(rootJson.toString().toByteArray(StandardCharsets.UTF_8))
        fos.flush()
      }
      if (targetFile.exists()) targetFile.delete()
      if (!tempFile.renameTo(targetFile)) {
        tempFile.copyTo(targetFile, overwrite = true)
      }
      Result.success(targetFile)
    } catch (t: Throwable) {
      Result.failure(t)
    } finally {
      if (tempFile.exists()) tempFile.delete()
    }
  }

  fun restoreUserKnowledgeSnapshot(storageRootDir: File, userUid: String?): Int {
    val scope = normalizeUserScope(userUid)
    val targetFile = File(File(File(storageRootDir, "astryn_kb"), AstrynSecurityGuard.sanitizeFileName(scope)), "knowledge_index.json")
    if (!targetFile.exists()) return 0

    return try {
      val root = JSONObject(targetFile.readText(StandardCharsets.UTF_8))
      val fileOwner = root.optString("userUid", "")
      if (!AstrynSecurityGuard.verifyUserOwnership(scope, fileOwner)) {
        return 0
      }

      val docsArr = root.optJSONArray("documents") ?: return 0
      val chunksArr = root.optJSONArray("chunks") ?: JSONArray()

      val docMap = userDocuments.getOrPut(scope) { ConcurrentHashMap() }
      val chunkMap = userChunks.getOrPut(scope) { ConcurrentHashMap() }

      var restoredCount = 0
      for (i in 0 until docsArr.length()) {
        val obj = docsArr.optJSONObject(i) ?: continue
        val fmt = runCatching { KnowledgeFileFormat.valueOf(obj.optString("fileFormat")) }.getOrNull() ?: continue
        val doc = KnowledgeDocumentRecord(
          documentId = obj.getString("documentId"),
          userUid = scope,
          originalFilename = obj.getString("originalFilename"),
          fileFormat = fmt,
          fileSizeBytes = obj.optLong("fileSizeBytes", 0L),
          documentHash = obj.optString("documentHash", ""),
          importTimestampMs = obj.optLong("importTimestampMs", System.currentTimeMillis()),
          lastModifiedTimestampMs = if (obj.has("lastModifiedTimestampMs")) obj.optLong("lastModifiedTimestampMs") else null,
          extractionStatus = runCatching { ExtractionStatus.valueOf(obj.optString("extractionStatus")) }.getOrDefault(ExtractionStatus.SUCCESS),
          indexingStatus = runCatching { IndexingStatus.valueOf(obj.optString("indexingStatus")) }.getOrDefault(IndexingStatus.INDEXED),
          pageCount = if (obj.has("pageCount")) obj.optInt("pageCount") else null,
          detectedLanguage = obj.optString("detectedLanguage", "UNKNOWN"),
          chunkCount = obj.optInt("chunkCount", 0),
          documentVersion = obj.optInt("documentVersion", 1),
          indexVersion = obj.optInt("indexVersion", CURRENT_INDEX_VERSION),
          ocrStatus = runCatching { OcrStatus.valueOf(obj.optString("ocrStatus")) }.getOrDefault(OcrStatus.NOT_NEEDED),
          errorInfo = obj.optString("errorInfo", "").ifBlank { null },
          extractedText = obj.optString("extractedText", ""),
        )
        docMap[doc.documentId] = doc
        restoredCount++
      }

      val groupedChunks = LinkedHashMap<String, MutableList<DocumentChunk>>()
      for (i in 0 until chunksArr.length()) {
        val cObj = chunksArr.optJSONObject(i) ?: continue
        val docId = cObj.optString("documentId", "")
        val fmt = runCatching { KnowledgeFileFormat.valueOf(cObj.optString("fileFormat")) }.getOrNull() ?: continue
        val content = cObj.optString("content", "")
        val chunk = DocumentChunk(
          chunkId = cObj.getString("chunkId"),
          documentId = docId,
          userUid = scope,
          originalFilename = cObj.optString("originalFilename", ""),
          fileFormat = fmt,
          pageNumber = if (cObj.has("pageNumber")) cObj.optInt("pageNumber") else null,
          sectionTitle = cObj.optString("sectionTitle", "").ifBlank { null },
          sheetName = cObj.optString("sheetName", "").ifBlank { null },
          rowRange = cObj.optString("rowRange", "").ifBlank { null },
          chunkIndex = cObj.optInt("chunkIndex", 0),
          startOffset = cObj.optInt("startOffset", 0),
          endOffset = cObj.optInt("endOffset", 0),
          content = content,
          contentHash = cObj.optString("contentHash", sha256Hex(content)),
          extractionVersion = cObj.optInt("extractionVersion", 1),
          topicTags = AstrynDocumentChunker.extractTopicKeywords(content, cObj.optString("sectionTitle", "").ifBlank { null }),
          importTimestampMs = cObj.optLong("importTimestampMs", System.currentTimeMillis()),
        )
        groupedChunks.getOrPut(docId) { mutableListOf() }.add(chunk)
      }
      for ((docId, list) in groupedChunks) {
        chunkMap[docId] = list
        docMap[docId]?.let { d ->
          AstrynKnowledgeGraphEngine.indexDocumentChunksIntoGraph(scope, d, list)
        }
      }
      restoredCount
    } catch (_: Exception) {
      0
    }
  }

  fun onUserLogoutOrAccountSwitch(userUid: String?) {
    val scope = normalizeUserScope(userUid)
    AstrynSemanticSearchEngine.invalidateUserCache(scope)
  }

  fun clearAllKnowledgeDataForTesting() {
    userDocuments.clear()
    userChunks.clear()
    activeIndexingLocks.clear()
    AstrynSemanticSearchEngine.clearAllForTesting()
    AstrynKnowledgeGraphEngine.clearAllForTesting()
  }
}
