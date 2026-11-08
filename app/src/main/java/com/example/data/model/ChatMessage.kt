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
}

/**
 * Immutable representation of an Image or PDF attachment associated with a chat message.
 *
 * - [inlineBase64Data] holds the Base64-encoded binary payload (downsampled JPEG/PNG/WebP image
 *   or PDF bytes / rendered page images) for multimodal Gemini requests.
 * - [extractedText] holds extracted page-numbered text for PDFs so follow-up questions in the
 *   conversation retain document context without resending large binary payloads on every turn.
 */
@Immutable
data class ChatAttachment(
  val id: String = UUID.randomUUID().toString(),
  val fileName: String,
  val mimeType: String,
  val fileSizeBytes: Long,
  val kind: AttachmentKind =
    if (mimeType.equals("application/pdf", ignoreCase = true)) AttachmentKind.PDF
    else AttachmentKind.IMAGE,
  val pageCount: Int? = null,
  val extractedText: String? = null,
  val inlineBase64Data: String? = null,
  val inlineMimeType: String = mimeType,
  val renderedPageImagesBase64: List<String> = emptyList(),
  val thumbnailBase64: String? = null,
) {
  /**
   * Human-readable file size label (e.g., "245 KB", "1.4 MB").
   */
  val formattedFileSize: String
    get() = formatBytes(fileSizeBytes)

  /**
   * Builds a concise context representation for multi-turn follow-up questions
   * so the assistant remembers the attachment details and extracted document text.
   */
  fun buildFollowUpContextSummary(maxChars: Int = 6_000): String {
    return buildString {
      append("[Attached ")
      append(if (kind == AttachmentKind.PDF) "PDF Document" else "Image")
      append(": ")
      append(fileName)
      append(" (")
      append(formattedFileSize)
      if (pageCount != null && pageCount > 0) {
        append(", ")
        append(pageCount)
        append(if (pageCount == 1) " page" else " pages")
      }
      append(")]")
      if (!extractedText.isNullOrBlank()) {
        append("\n<untrusted_document_content file=\"")
        append(fileName.replace("\"", "'"))
        append("\">\n")
        val trimmed = extractedText.trim()
        if (trimmed.length <= maxChars) {
          append(trimmed)
        } else {
          append(trimmed.take(maxChars))
          append("\n...[Document text truncated to fit context window]...")
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
      // Persist extracted text (up to 12 KB) and compact thumbnail so follow-up questions
      // and message bubbles work across app restarts without bloating SQLite rows.
      if (!extractedText.isNullOrBlank()) {
        put("extractedText", extractedText.take(12_000))
      }
      if (!thumbnailBase64.isNullOrBlank() && thumbnailBase64.length <= 64_000) {
        put("thumbnailBase64", thumbnailBase64)
      }
    }
  }

  companion object {
    fun formatBytes(bytes: Long): String {
      if (bytes <= 0L) return "0 B"
      if (bytes < 1024L) return "$bytes B"
      val kb = bytes / 1024.0
      if (kb < 1024.0) return String.format(java.util.Locale.US, "%.1f KB", kb)
      val mb = kb / 1024.0
      return String.format(java.util.Locale.US, "%.2f MB", mb)
    }

    fun fromPersistedJsonObject(obj: JSONObject): ChatAttachment? {
      val fileName = obj.optString("fileName", "").trim()
      val mimeType = obj.optString("mimeType", "").trim()
      if (fileName.isEmpty() || mimeType.isEmpty()) return null
      val kindStr = obj.optString("kind", "")
      val kind =
        AttachmentKind.entries.firstOrNull { it.name.equals(kindStr, ignoreCase = true) }
          ?: if (mimeType.equals("application/pdf", ignoreCase = true)) AttachmentKind.PDF
          else AttachmentKind.IMAGE
      val pageCount = if (obj.has("pageCount")) obj.optInt("pageCount", 0).takeIf { it > 0 } else null
      val extractedText = obj.optString("extractedText", "").ifBlank { null }
      val thumb = obj.optString("thumbnailBase64", "").ifBlank { null }
      return ChatAttachment(
        id = obj.optString("id", UUID.randomUUID().toString()).ifBlank { UUID.randomUUID().toString() },
        fileName = fileName,
        mimeType = mimeType,
        fileSizeBytes = obj.optLong("fileSizeBytes", 0L),
        kind = kind,
        pageCount = pageCount,
        extractedText = extractedText,
        thumbnailBase64 = thumb,
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
