package com.example.data.model

import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Represents the participant role in a conversation.
 */
enum class MessageRole {
  USER,
  MODEL,
  SYSTEM,
}

/**
 * Status lifecycle of an individual chat message.
 * - [INTERRUPTED] explicitly marks partial or unfinished assistant responses
 *   so they are never misrepresented as complete answers after cancellation or restart.
 */
enum class MessageStatus {
  SENDING,
  STREAMING,
  SUCCESS,
  INTERRUPTED,
  ERROR,
}

/**
 * Type of user-attached file in the chat composer.
 */
enum class AttachmentKind {
  IMAGE,
  PDF,
  DOCX,
  XLSX,
  CSV,
  TXT,
  MARKDOWN,
  SOURCE_CODE,
}

/**
 * Immutable representation of an Image, PDF, Document, Spreadsheet, or Code attachment
 * associated with a chat message.
 *
 * - [inlineBase64Data] holds the Base64-encoded binary payload (downsampled JPEG/PNG/WebP image
 *   or PDF bytes / rendered page images) for multimodal Gemini requests.
 * - [extractedText] holds extracted page-numbered or structured text for PDFs, DOCX, XLSX, CSV,
 *   TXT, Markdown, and source-code files so follow-up questions in the conversation retain
 *   document context without resending large binary payloads on every turn.
 */
@Immutable
data class ChatAttachment(
  val id: String = UUID.randomUUID().toString(),
  val fileName: String,
  val mimeType: String,
  val fileSizeBytes: Long,
  val kind: AttachmentKind = inferKindFromMimeAndName(mimeType, fileName),
  val pageCount: Int? = null,
  val extractedText: String? = null,
  val inlineBase64Data: String? = null,
  val inlineMimeType: String = mimeType,
  val renderedPageImagesBase64: List<String> = emptyList(),
  val thumbnailBase64: String? = null,
  val imageWidth: Int? = null,
  val imageHeight: Int? = null,
  val analyzedPageCount: Int? = null,
  val omittedPageCount: Int? = null,
  val isScannedOrImageBasedPdf: Boolean = false,
  val documentSummary: String? = null,
  val pageTextMap: Map<Int, String> = emptyMap(),
) {
  /**
   * Human-readable file size label (e.g., "245 KB", "1.4 MB").
   */
  val formattedFileSize: String
    get() = formatBytes(fileSizeBytes)

  /**
   * Retrieves the extracted text for specific 1-indexed page numbers or section/chapter keywords,
   * scanning both [pageTextMap] and `[Page X of Y]` blocks inside [extractedText].
   */
  fun extractTargetedPagesOrSections(
    targetPageNumbers: List<Int> = emptyList(),
    targetSectionKeywords: List<String> = emptyList(),
  ): String {
    if (kind != AttachmentKind.PDF) return extractedText.orEmpty()
    val resolvedMap =
      if (pageTextMap.isNotEmpty()) {
        pageTextMap
      } else {
        parsePageMapFromExtractedText(extractedText.orEmpty())
      }
    if (resolvedMap.isEmpty()) return extractedText.orEmpty()

    val matchedPages = LinkedHashMap<Int, String>()
    for (p in targetPageNumbers) {
      val text = resolvedMap[p]
      if (!text.isNullOrBlank()) {
        matchedPages[p] = text
      }
    }

    if (targetSectionKeywords.isNotEmpty()) {
      for ((pageNum, pageContent) in resolvedMap) {
        val lowerPage = pageContent.lowercase(java.util.Locale.ROOT)
        if (targetSectionKeywords.any { kw -> kw.isNotBlank() && lowerPage.contains(kw.lowercase(java.util.Locale.ROOT)) }) {
          matchedPages[pageNum] = pageContent
        }
      }
    }

    if (matchedPages.isEmpty()) return ""
    val totalPages = pageCount ?: resolvedMap.keys.maxOrNull() ?: matchedPages.size
    return buildString {
      for ((pageNum, pageContent) in matchedPages) {
        if (isNotEmpty()) append("\n\n")
        append("[Targeted Page $pageNum of $totalPages]\n")
        append(pageContent.trim())
      }
    }
  }

  /**
   * Builds a rich context representation for multi-turn follow-up questions
   * so the assistant remembers the attachment details, document-level summary,
   * targeted pages/chapters, and any omitted-page disclosures.
   */
  fun buildFollowUpContextSummary(
    maxChars: Int = 6_000,
    targetPageNumbers: List<Int> = emptyList(),
    targetSectionKeywords: List<String> = emptyList(),
  ): String {
    return buildString {
      append("[Attached ")
      append(
        when (kind) {
          AttachmentKind.PDF -> "PDF Document"
          AttachmentKind.DOCX -> "DOCX Document"
          AttachmentKind.XLSX -> "XLSX Spreadsheet"
          AttachmentKind.CSV -> "CSV Dataset"
          AttachmentKind.TXT -> "Text Document"
          AttachmentKind.MARKDOWN -> "Markdown Document"
          AttachmentKind.SOURCE_CODE -> "Source Code File"
          AttachmentKind.IMAGE -> "Image"
        }
      )
      append(": ")
      append(fileName)
      append(" (")
      append(formattedFileSize)
      if (pageCount != null && pageCount > 0) {
        append(", ")
        append(pageCount)
        append(if (pageCount == 1) " page" else " pages")
        if (analyzedPageCount != null && analyzedPageCount > 0 && analyzedPageCount < pageCount) {
          append(", analyzed pages 1–$analyzedPageCount")
        }
      }
      if (imageWidth != null && imageHeight != null && imageWidth > 0 && imageHeight > 0) {
        append(", ${imageWidth}x${imageHeight}px")
      }
      if (isScannedOrImageBasedPdf) {
        append(", scanned/visual PDF")
      }
      append(")]")

      if (!documentSummary.isNullOrBlank()) {
        append("\n[Document Overview: ")
        append(documentSummary.trim())
        append("]")
      }

      if (omittedPageCount != null && omittedPageCount > 0 && pageCount != null) {
        val startOmitted = (pageCount - omittedPageCount + 1).coerceAtLeast(1)
        append(
          "\n[Disclosure: Pages $startOmitted–$pageCount ($omittedPageCount pages) were beyond the extraction limit and are unavailable unless uploaded separately.]"
        )
      }

      val targetedBlock =
        if (targetPageNumbers.isNotEmpty() || targetSectionKeywords.isNotEmpty()) {
          extractTargetedPagesOrSections(targetPageNumbers, targetSectionKeywords)
        } else {
          ""
        }

      if (!extractedText.isNullOrBlank() || targetedBlock.isNotBlank()) {
        append("\n<untrusted_document_content file=\"")
        append(fileName.replace("\"", "'"))
        append("\">\n")
        if (targetedBlock.isNotBlank()) {
          append(targetedBlock.take(maxChars))
          val remainingBudget = (maxChars - targetedBlock.length - 120).coerceAtLeast(0)
          if (remainingBudget > 300 && !extractedText.isNullOrBlank()) {
            append("\n\n--- Additional Document Context ---\n")
            append(extractedText.trim().take(remainingBudget))
          }
        } else {
          val trimmed = extractedText.orEmpty().trim()
          if (trimmed.length <= maxChars) {
            append(trimmed)
          } else {
            append(trimmed.take(maxChars))
            append(
              "\n...[Document text truncated at $maxChars chars to fit context window; ask for a specific page number to inspect further pages]..."
            )
          }
        }
        append("\n</untrusted_document_content>")
      }
    }
  }

  fun toPersistedJsonObject(): JSONObject {
    return JSONObject().apply {
      put("id", id)
      put("fileName", fileName)
      put("mimeType", mimeType)
      put("fileSizeBytes", fileSizeBytes)
      put("kind", kind.name)
      if (pageCount != null) put("pageCount", pageCount)
      if (imageWidth != null) put("imageWidth", imageWidth)
      if (imageHeight != null) put("imageHeight", imageHeight)
      if (analyzedPageCount != null) put("analyzedPageCount", analyzedPageCount)
      if (omittedPageCount != null) put("omittedPageCount", omittedPageCount)
      if (isScannedOrImageBasedPdf) put("isScannedOrImageBasedPdf", true)
      if (!documentSummary.isNullOrBlank()) put("documentSummary", documentSummary.take(2_000))
      // Persist extracted text (up to 24 KB) and compact thumbnail so follow-up questions
      // and message bubbles work across app restarts without bloating SQLite rows.
      if (!extractedText.isNullOrBlank()) {
        put("extractedText", extractedText.take(24_000))
      }
      if (!thumbnailBase64.isNullOrBlank() && thumbnailBase64.length <= 64_000) {
        put("thumbnailBase64", thumbnailBase64)
      }
    }
  }

  companion object {
    fun inferKindFromMimeAndName(mimeType: String, fileName: String): AttachmentKind {
      val cleanMime = mimeType.lowercase(java.util.Locale.ROOT).trim()
      val lowerName = fileName.lowercase(java.util.Locale.ROOT).trim()
      return when {
        cleanMime == "application/pdf" || lowerName.endsWith(".pdf") -> AttachmentKind.PDF
        cleanMime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
          lowerName.endsWith(".docx") -> AttachmentKind.DOCX
        cleanMime == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ||
          lowerName.endsWith(".xlsx") -> AttachmentKind.XLSX
        cleanMime == "text/csv" || cleanMime == "application/csv" || lowerName.endsWith(".csv") ->
          AttachmentKind.CSV
        cleanMime == "text/markdown" || lowerName.endsWith(".md") || lowerName.endsWith(".markdown") ->
          AttachmentKind.MARKDOWN
        lowerName.endsWith(".kt") ||
          lowerName.endsWith(".kts") ||
          lowerName.endsWith(".java") ||
          lowerName.endsWith(".py") ||
          lowerName.endsWith(".js") ||
          lowerName.endsWith(".ts") ||
          lowerName.endsWith(".tsx") ||
          lowerName.endsWith(".jsx") ||
          lowerName.endsWith(".html") ||
          lowerName.endsWith(".css") ||
          lowerName.endsWith(".sql") ||
          lowerName.endsWith(".c") ||
          lowerName.endsWith(".cpp") ||
          lowerName.endsWith(".h") ||
          lowerName.endsWith(".hpp") ||
          lowerName.endsWith(".json") ||
          lowerName.endsWith(".xml") -> AttachmentKind.SOURCE_CODE
        cleanMime == "text/plain" || lowerName.endsWith(".txt") -> AttachmentKind.TXT
        else -> AttachmentKind.IMAGE
      }
    }

    fun formatBytes(bytes: Long): String {
      if (bytes <= 0L) return "0 B"
      if (bytes < 1024L) return "$bytes B"
      val kb = bytes / 1024.0
      if (kb < 1024.0) return String.format(java.util.Locale.US, "%.1f KB", kb)
      val mb = kb / 1024.0
      return String.format(java.util.Locale.US, "%.2f MB", mb)
    }

    fun parsePageMapFromExtractedText(rawText: String): Map<Int, String> {
      if (rawText.isBlank()) return emptyMap()
      val map = LinkedHashMap<Int, String>()
      val pageHeaderRegex = Regex("""\[Page\s+(\d+)\s+of\s+\d+\]\r?\n""")
      val matches = pageHeaderRegex.findAll(rawText).toList()
      if (matches.isEmpty()) return emptyMap()
      for (i in matches.indices) {
        val pageNum = matches[i].groupValues[1].toIntOrNull() ?: continue
        val start = matches[i].range.last + 1
        val end =
          if (i + 1 < matches.size) matches[i + 1].range.first
          else rawText.length
        if (start < end) {
          val slice = rawText.substring(start, end).trim()
          if (slice.isNotEmpty()) {
            map[pageNum] = slice
          }
        }
      }
      return map
    }

    fun fromPersistedJsonObject(obj: JSONObject): ChatAttachment? {
      val fileName = obj.optString("fileName", "").trim()
      val mimeType = obj.optString("mimeType", "").trim()
      if (fileName.isEmpty() || mimeType.isEmpty()) return null
      val kindStr = obj.optString("kind", "")
      val kind =
        AttachmentKind.entries.firstOrNull { it.name.equals(kindStr, ignoreCase = true) }
          ?: inferKindFromMimeAndName(mimeType, fileName)
      val pageCount = if (obj.has("pageCount")) obj.optInt("pageCount", 0).takeIf { it > 0 } else null
      val imageWidth = if (obj.has("imageWidth")) obj.optInt("imageWidth", 0).takeIf { it > 0 } else null
      val imageHeight = if (obj.has("imageHeight")) obj.optInt("imageHeight", 0).takeIf { it > 0 } else null
      val analyzedPageCount =
        if (obj.has("analyzedPageCount")) obj.optInt("analyzedPageCount", 0).takeIf { it > 0 } else null
      val omittedPageCount =
        if (obj.has("omittedPageCount")) obj.optInt("omittedPageCount", 0).takeIf { it > 0 } else null
      val isScanned = obj.optBoolean("isScannedOrImageBasedPdf", false)
      val docSummary = obj.optString("documentSummary", "").ifBlank { null }
      val extractedText = obj.optString("extractedText", "").ifBlank { null }
      val thumb = obj.optString("thumbnailBase64", "").ifBlank { null }
      val reconstructedMap =
        if (kind == AttachmentKind.PDF && !extractedText.isNullOrBlank()) {
          parsePageMapFromExtractedText(extractedText)
        } else {
          emptyMap()
        }
      return ChatAttachment(
        id = obj.optString("id", UUID.randomUUID().toString()).ifBlank { UUID.randomUUID().toString() },
        fileName = fileName,
        mimeType = mimeType,
        fileSizeBytes = obj.optLong("fileSizeBytes", 0L),
        kind = kind,
        pageCount = pageCount,
        extractedText = extractedText,
        thumbnailBase64 = thumb,
        imageWidth = imageWidth,
        imageHeight = imageHeight,
        analyzedPageCount = analyzedPageCount,
        omittedPageCount = omittedPageCount,
        isScannedOrImageBasedPdf = isScanned,
        documentSummary = docSummary,
        pageTextMap = reconstructedMap,
      )
    }

    fun serializeList(attachments: List<ChatAttachment>): String? {
      if (attachments.isEmpty()) return null
      val arr = JSONArray()
      for (att in attachments) {
        arr.put(att.toPersistedJsonObject())
      }
      return arr.toString()
    }

    fun deserializeList(json: String?): List<ChatAttachment> {
      if (json.isNullOrBlank()) return emptyList()
      return try {
        val arr = JSONArray(json)
        buildList {
          for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            fromPersistedJsonObject(obj)?.let { add(it) }
          }
        }
      } catch (_: Exception) {
        emptyList()
      }
    }
  }
}

/**
 * Verified web citation returned by Gemini Google Search grounding (`groundingMetadata`).
 * Never fabricated; only populated from actual search grounding chunks returned by the API.
 */
@Immutable
data class WebCitation(
  val title: String,
  val url: String,
  val domain: String = extractDomain(url),
) {
  fun toJsonObject(): JSONObject {
    return JSONObject().apply {
      put("title", title)
      put("url", url)
      put("domain", domain)
    }
  }

  companion object {
    fun extractDomain(rawUrl: String): String {
      return try {
        val host = java.net.URI(rawUrl.trim()).host.orEmpty()
        host.removePrefix("www.").ifBlank { "Source" }
      } catch (_: Exception) {
        "Source"
      }
    }

    fun serializeList(citations: List<WebCitation>): String? {
      if (citations.isEmpty()) return null
      val arr = JSONArray()
      for (c in citations) {
        arr.put(c.toJsonObject())
      }
      return arr.toString()
    }

    fun deserializeList(json: String?): List<WebCitation> {
      if (json.isNullOrBlank()) return emptyList()
      return try {
        val arr = JSONArray(json)
        buildList {
          for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val url = obj.optString("url", "").trim()
            if (url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)) {
              val title = obj.optString("title", "").trim().ifBlank { extractDomain(url) }
              val domain = obj.optString("domain", "").trim().ifBlank { extractDomain(url) }
              add(WebCitation(title = title, url = url, domain = domain))
            }
          }
        }
      } catch (_: Exception) {
        emptyList()
      }
    }
  }
}

/**
 * Immutable chat message item used by the UI and API pipeline.
 */
@Immutable
data class ChatMessage(
  val id: String = UUID.randomUUID().toString(),
  val role: MessageRole,
  val content: String,
  val timestamp: Long = System.currentTimeMillis(),
  val status: MessageStatus = MessageStatus.SUCCESS,
  val errorMessage: String? = null,
  val errorTitle: String? = null,
  val errorHint: String? = null,
  val isRetryable: Boolean = true,
  val isLiked: Boolean = false,
  val isBookmarked: Boolean = false,
  val attachments: List<ChatAttachment> = emptyList(),
  val citations: List<WebCitation> = emptyList(),
  val webSearchQueries: List<String> = emptyList(),
) {
  /**
   * Formats the complete message text for Copy or Share actions, preserving paragraph breaks,
   * lists, and appending verified source URLs when present. Never includes internal metadata or secrets.
   */
  fun formatForCopyOrShare(): String {
    val cleanBody = content.trim()
    if (cleanBody.isEmpty()) return ""
    if (citations.isEmpty()) return cleanBody
    return buildString(cleanBody.length + citations.size * 64) {
      append(cleanBody)
      append("\n\nSources:\n")
      citations.forEachIndexed { idx, citation ->
        append("${idx + 1}. ${citation.title} — ${citation.url}\n")
      }
    }.trimEnd()
  }

  /**
   * Strips Markdown formatting symbols so TextToSpeech reads the AI response naturally.
   */
  fun formatForSpeech(): String {
    if (content.isBlank()) return ""
    return content
      .replace(Regex("```[a-zA-Z0-9_-]*\\n?"), " ")
      .replace("```", " ")
      .replace(Regex("`([^`]+)`"), "$1")
      .replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")
      .replace(Regex("(?m)^\\s*#{1,6}\\s*"), "")
      .replace(Regex("[*_~>|]+"), " ")
      .replace(Regex("\\s+"), " ")
      .trim()
  }

  /**
   * Generates 3 contextual follow-up prompt suggestions based on the AI response content and language.
   */
  fun generateFollowUpSuggestions(): List<String> {
    if (content.isBlank()) return emptyList()
    val hasHindi = content.any { ch -> ch in '\u0900'..'\u097F' }
    val hasCode = content.contains("```")
    val lower = content.lowercase()

    return when {
      hasHindi && hasCode ->
        listOf(
          "इस कोड को लाइन-बाय-लाइन देसी उदाहरण से समझाएं",
          "इसमें एरर हैंडलिंग और टेस्ट केस जोड़ें",
          "इसे और तेज़ (optimize) कैसे करें?",
        )
      hasHindi ->
        listOf(
          "😂 इसे मजेदार देसी उदाहरण के साथ समझाएं",
          "📋 इसका 7-दिन का प्रैक्टिकल एक्शन प्लान बनाएं",
          "🎓 इस टॉपिक पर मेरा एक क्विक टेस्ट लें",
        )
      hasCode ->
        listOf(
          "😂 Explain this code with a Desi real-life example",
          "Add unit tests & edge-case handling",
          "How can we optimize its performance?",
        )
      lower.contains("step") || lower.contains("1.") || lower.contains("plan") ->
        listOf(
          "📋 Iska 7-Day Step-by-Step Action Plan banao",
          "😂 Isko Desi majedar example se samjhao",
          "⚖️ Iska Pros/Cons Table aur Final Verdict dikhao",
        )
      else ->
        listOf(
          "😂 Isko Desi & Majedar example se samjhao",
          "📋 Iska pura 7-Day Practical Plan banao",
          "🔥 Iska Comparison Table aur Final Verdict do",
        )
    }
  }
}
