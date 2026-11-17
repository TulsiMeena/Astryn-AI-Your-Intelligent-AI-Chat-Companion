package com.example.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.example.data.model.AttachmentKind
import com.example.data.model.ChatAttachment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.Inflater
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Genuine Image (JPEG, PNG, WebP) and PDF ingestion, validation, high-fidelity encoding,
 * page-indexed text/table/diagram extraction, document-level summarization, and user-isolated
 * session caching for Astryn AI (Phase 3 Multimodal Upgrade).
 */
object AttachmentProcessor {
  const val MAX_ATTACHMENTS_PER_MESSAGE = 4
  const val MAX_FILE_SIZE_BYTES = 15L * 1024L * 1024L // 15 MB max per file
  const val MAX_INLINE_PDF_BYTES = 10L * 1024L * 1024L // 10 MB inline PDF payload cap
  const val MAX_LOSSLESS_IMAGE_BYTES = 4L * 1024L * 1024L // 4 MB lossless PNG/WebP preservation cap
  const val MAX_IMAGE_DIMENSION_PX = 2048 // High-resolution vision sweet spot for small text & screenshots
  const val HIGH_FIDELITY_JPEG_QUALITY = 92 // High quality to preserve readable text, numbers & diagrams
  const val THUMBNAIL_DIMENSION_PX = 160
  const val MAX_PDF_PAGES_TO_ANALYZE = 100
  const val MAX_RENDERED_PDF_PAGES = 6

  val SUPPORTED_IMAGE_MIME_TYPES =
    setOf(
      "image/jpeg",
      "image/jpg",
      "image/png",
      "image/webp",
    )

  const val PDF_MIME_TYPE = "application/pdf"

  /**
   * User-isolated, conversation-scoped active attachment session cache.
   * Outer key: normalized `userUid` (`anonymous_local_scope` if signed out).
   * Inner key: `conversationId`.
   * Value: Ordered map of `attachmentId` -> full [ChatAttachment] (including binary `inlineBase64Data`,
   * `renderedPageImagesBase64`, and `pageTextMap`).
   *
   * Guarantees that follow-up questions in Turn 2..N retain full multimodal access to earlier
   * attachments in the same conversation while strictly preventing cross-user or cross-conversation leaks.
   */
  private val userConversationAttachmentStore =
    ConcurrentHashMap<String, ConcurrentHashMap<String, LinkedHashMap<String, ChatAttachment>>>()

  private fun normalizeUserScope(userUid: String?): String {
    val cleaned = userUid?.trim().orEmpty()
    return if (cleaned.isEmpty()) "anonymous_local_scope" else cleaned
  }

  /**
   * Registers uploaded attachments for a specific authenticated [userUid] and [conversationId].
   */
  fun registerConversationAttachments(
    userUid: String?,
    conversationId: String,
    attachments: List<ChatAttachment>,
  ) {
    val cleanConvId = conversationId.trim()
    if (cleanConvId.isEmpty() || attachments.isEmpty()) return
    val scope = normalizeUserScope(userUid)
    val userMap = userConversationAttachmentStore.getOrPut(scope) { ConcurrentHashMap() }
    val convMap = userMap.getOrPut(cleanConvId) { LinkedHashMap() }
    synchronized(convMap) {
      for (att in attachments) {
        convMap[att.id] = att
        // Bounded to 16 recent attachments per conversation to cap memory usage
        while (convMap.size > 16) {
          val eldestKey = convMap.keys.firstOrNull() ?: break
          convMap.remove(eldestKey)
        }
      }
    }
  }

  /**
   * Retrieves all cached attachments for the specified [userUid] and [conversationId] in chronological order.
   * Returns an empty list if the user or conversation has no registered attachments.
   */
  fun getConversationAttachments(
    userUid: String?,
    conversationId: String,
  ): List<ChatAttachment> {
    val cleanConvId = conversationId.trim()
    if (cleanConvId.isEmpty()) return emptyList()
    val scope = normalizeUserScope(userUid)
    val convMap = userConversationAttachmentStore[scope]?.get(cleanConvId) ?: return emptyList()
    synchronized(convMap) {
      return convMap.values.toList()
    }
  }

  /**
   * Restores binary `inlineBase64Data`, `renderedPageImagesBase64`, and `pageTextMap` for an attachment
   * loaded from conversation history if it belongs to the same [userUid] and [conversationId].
   * Never returns data belonging to a different user or conversation.
   */
  fun enrichAttachmentFromSession(
    userUid: String?,
    conversationId: String,
    attachment: ChatAttachment,
  ): ChatAttachment {
    val cleanConvId = conversationId.trim()
    if (cleanConvId.isEmpty()) return attachment
    val scope = normalizeUserScope(userUid)
    val convMap = userConversationAttachmentStore[scope]?.get(cleanConvId) ?: return attachment
    val cached =
      synchronized(convMap) {
        convMap[attachment.id]
          ?: convMap.values.lastOrNull {
            it.fileName.equals(attachment.fileName, ignoreCase = true) &&
              it.kind == attachment.kind &&
              it.fileSizeBytes == attachment.fileSizeBytes
          }
      } ?: return attachment

    return attachment.copy(
      inlineBase64Data = attachment.inlineBase64Data ?: cached.inlineBase64Data,
      renderedPageImagesBase64 =
        attachment.renderedPageImagesBase64.ifEmpty { cached.renderedPageImagesBase64 },
      pageTextMap = attachment.pageTextMap.ifEmpty { cached.pageTextMap },
      documentSummary = attachment.documentSummary ?: cached.documentSummary,
      imageWidth = attachment.imageWidth ?: cached.imageWidth,
      imageHeight = attachment.imageHeight ?: cached.imageHeight,
      analyzedPageCount = attachment.analyzedPageCount ?: cached.analyzedPageCount,
      omittedPageCount = attachment.omittedPageCount ?: cached.omittedPageCount,
      isScannedOrImageBasedPdf = attachment.isScannedOrImageBasedPdf || cached.isScannedOrImageBasedPdf,
    )
  }

  /**
   * Clears all cached session attachments for a specific [userUid] (e.g., on logout or account switch).
   */
  fun clearUserAttachmentSession(userUid: String?) {
    val scope = normalizeUserScope(userUid)
    userConversationAttachmentStore.remove(scope)
  }

  /**
   * Clears all cached attachment sessions across all users (used in test teardown).
   */
  fun clearAllSessions() {
    userConversationAttachmentStore.clear()
  }

  /**
   * Resolves display filename, MIME type, and byte array from an Android content [Uri],
   * then processes the attachment on [ioDispatcher].
   */
  suspend fun processUri(
    context: Context,
    uri: Uri,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  ): Result<ChatAttachment> =
    withContext(ioDispatcher) {
      try {
        val contentResolver = context.contentResolver
        var resolvedName = "attachment"
        var resolvedSize = -1L

        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
          val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
          if (cursor.moveToFirst()) {
            if (nameIdx >= 0) {
              resolvedName = cursor.getString(nameIdx)?.trim().orEmpty().ifBlank { "attachment" }
            }
            if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
              resolvedSize = cursor.getLong(sizeIdx)
            }
          }
        }

        if (resolvedSize > MAX_FILE_SIZE_BYTES) {
          return@withContext Result.failure(
            IllegalArgumentException(
              "\"$resolvedName\" (${ChatAttachment.formatBytes(resolvedSize)}) exceeds the 15 MB attachment limit."
            )
          )
        }

        val rawMimeType = contentResolver.getType(uri)?.lowercase()?.trim().orEmpty()
        val inferredMimeType =
          resolveSupportedMimeType(rawMimeType, resolvedName)
            ?: return@withContext Result.failure(
              IllegalArgumentException(
                "Unsupported file format for \"$resolvedName\". Please attach a JPEG, PNG, WebP image or a PDF document."
              )
            )

        val bytes =
          contentResolver.openInputStream(uri)?.use { input ->
            readBytesBounded(input, MAX_FILE_SIZE_BYTES)
          }
            ?: return@withContext Result.failure(
              IllegalArgumentException("Unable to read \"$resolvedName\" from device storage.")
            )

        processRawBytes(
          fileName = resolvedName,
          mimeType = inferredMimeType,
          bytes = bytes,
          cacheDir = context.cacheDir,
        )
      } catch (e: IllegalArgumentException) {
        Result.failure(e)
      } catch (e: Exception) {
        Result.failure(
          IllegalArgumentException(
            e.localizedMessage ?: "Failed to process the selected attachment."
          )
        )
      }
    }

  /**
   * Processes raw bytes for an image or PDF attachment.
   * Directly callable in unit and Robolectric tests as well as production URI flows.
   */
  fun processRawBytes(
    fileName: String,
    mimeType: String,
    bytes: ByteArray,
    cacheDir: File? = null,
  ): Result<ChatAttachment> {
    val cleanName = com.example.data.api.AstrynSecurityGuard.sanitizeFileName(fileName)
    if (bytes.isEmpty()) {
      com.example.data.api.AstrynDiagnosticsManager.recordEvent(
        subsystem = com.example.data.api.AstrynDiagnosticsManager.Subsystem.ATTACHMENT_PROCESSOR,
        severity = com.example.data.api.AstrynDiagnosticsManager.Severity.WARNING,
        operation = "processRawBytes",
        rawMessage = "Rejected empty attachment (0 bytes)"
      )
      return Result.failure(
        IllegalArgumentException("\"$cleanName\" is empty (0 bytes). Please select a valid file.")
      )
    }
    if (bytes.size.toLong() > MAX_FILE_SIZE_BYTES) {
      com.example.data.api.AstrynDiagnosticsManager.recordEvent(
        subsystem = com.example.data.api.AstrynDiagnosticsManager.Subsystem.ATTACHMENT_PROCESSOR,
        severity = com.example.data.api.AstrynDiagnosticsManager.Severity.WARNING,
        operation = "processRawBytes",
        rawMessage = "Rejected oversized attachment (${bytes.size} bytes)"
      )
      return Result.failure(
        IllegalArgumentException(
          "\"$cleanName\" (${ChatAttachment.formatBytes(bytes.size.toLong())}) exceeds the 15 MB limit."
        )
      )
    }

    val normalizedMime =
      resolveSupportedMimeType(mimeType, cleanName)
        ?: run {
          com.example.data.api.AstrynDiagnosticsManager.recordEvent(
            subsystem = com.example.data.api.AstrynDiagnosticsManager.Subsystem.ATTACHMENT_PROCESSOR,
            severity = com.example.data.api.AstrynDiagnosticsManager.Severity.WARNING,
            operation = "processRawBytes",
            rawMessage = "Rejected unsupported attachment MIME ($mimeType)"
          )
          return Result.failure(
            IllegalArgumentException(
              "Unsupported file type ($mimeType) for \"$cleanName\". Supported formats: JPEG, PNG, WebP, and PDF."
            )
          )
        }

    val result =
      when {
        normalizedMime == PDF_MIME_TYPE -> processPdfBytes(cleanName, bytes, cacheDir)
        normalizedMime in SUPPORTED_IMAGE_MIME_TYPES -> processImageBytes(cleanName, normalizedMime, bytes)
        else -> processProductivityFileBytes(cleanName, normalizedMime, bytes)
      }
    if (result.isFailure) {
      com.example.data.api.AstrynDiagnosticsManager.recordEvent(
        subsystem = com.example.data.api.AstrynDiagnosticsManager.Subsystem.ATTACHMENT_PROCESSOR,
        severity = com.example.data.api.AstrynDiagnosticsManager.Severity.ERROR,
        operation = "processRawBytes",
        rawMessage = result.exceptionOrNull()?.message ?: "Attachment processing failed"
      )
    }
    return result
  }

  fun resolveSupportedMimeType(rawMimeType: String, fileName: String): String? {
    val cleanMime = rawMimeType.lowercase().trim()
    if (cleanMime == "image/jpg") return "image/jpeg"
    if (cleanMime in SUPPORTED_IMAGE_MIME_TYPES) return cleanMime
    if (cleanMime == PDF_MIME_TYPE) return PDF_MIME_TYPE
    if (cleanMime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document") return cleanMime
    if (cleanMime == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") return cleanMime
    if (cleanMime == "text/csv" || cleanMime == "application/csv") return "text/csv"
    if (cleanMime == "text/markdown") return "text/markdown"
    if (cleanMime == "text/plain") return "text/plain"

    val lowerName = fileName.lowercase().trim()
    return when {
      lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") -> "image/jpeg"
      lowerName.endsWith(".png") -> "image/png"
      lowerName.endsWith(".webp") -> "image/webp"
      lowerName.endsWith(".pdf") -> PDF_MIME_TYPE
      lowerName.endsWith(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
      lowerName.endsWith(".xlsx") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
      lowerName.endsWith(".csv") -> "text/csv"
      lowerName.endsWith(".md") || lowerName.endsWith(".markdown") -> "text/markdown"
      lowerName.endsWith(".txt") -> "text/plain"
      lowerName.endsWith(".kt") ||
        lowerName.endsWith(".kts") ||
        lowerName.endsWith(".java") ||
        lowerName.endsWith(".py") ||
        lowerName.endsWith(".js") ||
        lowerName.endsWith(".ts") ||
        lowerName.endsWith(".html") ||
        lowerName.endsWith(".css") ||
        lowerName.endsWith(".sql") ||
        lowerName.endsWith(".c") ||
        lowerName.endsWith(".cpp") -> "text/x-source-code"
      else -> null
    }
  }

  private fun processProductivityFileBytes(
    fileName: String,
    mimeType: String,
    bytes: ByteArray,
  ): Result<ChatAttachment> {
    val kind = ChatAttachment.inferKindFromMimeAndName(mimeType, fileName)
    return when (kind) {
      AttachmentKind.DOCX -> {
        val extractedRes = com.example.data.api.AstrynProductivityEngine.AstrynDocumentEngine.extractTextAndTablesFromDocxBytes(bytes)
        if (extractedRes.isFailure) {
          return Result.failure(extractedRes.exceptionOrNull() ?: IllegalArgumentException("Invalid DOCX document."))
        }
        val text = extractedRes.getOrThrow()
        Result.success(
          ChatAttachment(
            id = UUID.randomUUID().toString(),
            fileName = fileName,
            mimeType = mimeType,
            fileSizeBytes = bytes.size.toLong(),
            kind = AttachmentKind.DOCX,
            pageCount = max(1, (text.lines().size + 29) / 30),
            extractedText = text,
            documentSummary = "DOCX Document ($fileName, ${text.lines().size} lines extracted)",
          )
        )
      }

      AttachmentKind.XLSX -> {
        val wbRes = com.example.data.api.AstrynProductivityEngine.AstrynSpreadsheetEngine.readWorkbookFromXlsxBytes(bytes, fileName)
        if (wbRes.isFailure) {
          return Result.failure(wbRes.exceptionOrNull() ?: IllegalArgumentException("Invalid XLSX spreadsheet."))
        }
        val wb = wbRes.getOrThrow()
        val firstSheet = wb.sheets.firstOrNull()
        val csvText = if (firstSheet != null) {
          String(
            com.example.data.api.AstrynProductivityEngine.AstrynSpreadsheetEngine.writeSheetToCsvBytes(firstSheet),
            StandardCharsets.UTF_8,
          ).trim()
        } else {
          ""
        }
        Result.success(
          ChatAttachment(
            id = UUID.randomUUID().toString(),
            fileName = fileName,
            mimeType = mimeType,
            fileSizeBytes = bytes.size.toLong(),
            kind = AttachmentKind.XLSX,
            pageCount = wb.sheets.size,
            extractedText = csvText,
            documentSummary = "XLSX Workbook ($fileName, ${wb.sheets.size} sheet(s): ${wb.sheets.joinToString { it.sheetName }})",
          )
        )
      }

      AttachmentKind.CSV -> {
        val text = String(bytes, StandardCharsets.UTF_8).trim()
        if (text.isEmpty() || text.contains('\uFFFD')) {
          return Result.failure(IllegalArgumentException("\"$fileName\" is not a valid UTF-8 CSV file."))
        }
        val rows = com.example.data.api.AstrynProductivityEngine.AstrynSpreadsheetEngine.parseCsvRows(text)
        Result.success(
          ChatAttachment(
            id = UUID.randomUUID().toString(),
            fileName = fileName,
            mimeType = mimeType,
            fileSizeBytes = bytes.size.toLong(),
            kind = AttachmentKind.CSV,
            pageCount = 1,
            extractedText = text,
            documentSummary = "CSV Dataset ($fileName, ${max(0, rows.size - 1)} data rows, ${rows.firstOrNull()?.size ?: 0} columns)",
          )
        )
      }

      AttachmentKind.TXT, AttachmentKind.MARKDOWN, AttachmentKind.SOURCE_CODE -> {
        val text = String(bytes, StandardCharsets.UTF_8).trim()
        if (text.isEmpty() || text.contains('\uFFFD')) {
          return Result.failure(IllegalArgumentException("\"$fileName\" is not a valid UTF-8 text/code file."))
        }
        Result.success(
          ChatAttachment(
            id = UUID.randomUUID().toString(),
            fileName = fileName,
            mimeType = mimeType,
            fileSizeBytes = bytes.size.toLong(),
            kind = kind,
            pageCount = max(1, (text.lines().size + 39) / 40),
            extractedText = text,
            documentSummary = "${kind.name} File ($fileName, ${text.lines().size} lines)",
          )
        )
      }

      else -> Result.failure(IllegalArgumentException("Unsupported productivity file type for \"$fileName\"."))
    }
  }

  private fun processImageBytes(
    fileName: String,
    mimeType: String,
    bytes: ByteArray,
  ): Result<ChatAttachment> {
    // 1. Decode image bounds via BitmapFactory (with binary header fallback for headless JVM unit tests)
    var width = 0
    var height = 0
    try {
      val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)
      width = boundsOptions.outWidth
      height = boundsOptions.outHeight
    } catch (_: Throwable) {
      // Fallback to header inspection below
    }

    val headerDims = parseImageDimensionsFromHeader(bytes)
    if ((width <= 0 || height <= 0) && headerDims != null) {
      width = headerDims.first
      height = headerDims.second
    }

    if (width <= 0 || height <= 0) {
      return Result.failure(
        IllegalArgumentException(
          "\"$fileName\" could not be decoded as a valid image. Please choose a clear JPEG, PNG, or WebP file."
        )
      )
    }

    // 2. Downsample if larger than MAX_IMAGE_DIMENSION_PX (2048px) while preserving text & diagram legibility
    val maxDim = max(width, height)
    var sampleSize = 1
    while (maxDim / (sampleSize * 2) >= MAX_IMAGE_DIMENSION_PX) {
      sampleSize *= 2
    }

    var decodedBitmap: Bitmap? = null
    try {
      val decodeOptions =
        BitmapFactory.Options().apply {
          inSampleSize = sampleSize
          inPreferredConfig = Bitmap.Config.ARGB_8888
        }
      decodedBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
    } catch (_: Throwable) {
      decodedBitmap = null
    }

    val aspectDescription = describeAspectRatio(width, height)

    // Headless JVM fallback when BitmapFactory is stubbed but binary image header is verified
    if (decodedBitmap == null) {
      if (headerDims == null) {
        return Result.failure(
          IllegalArgumentException("Unable to decode image data from \"$fileName\".")
        )
      }
      val inlineB64 = encodeBase64NoWrap(bytes)
      return Result.success(
        ChatAttachment(
          id = UUID.randomUUID().toString(),
          fileName = fileName,
          mimeType = mimeType,
          fileSizeBytes = bytes.size.toLong(),
          kind = AttachmentKind.IMAGE,
          pageCount = null,
          extractedText =
            "Image dimensions: ${width}x${height}px ($aspectDescription, format: $mimeType)",
          inlineBase64Data = inlineB64,
          inlineMimeType = mimeType,
          thumbnailBase64 = inlineB64.takeIf { bytes.size <= 64_000 },
          imageWidth = width,
          imageHeight = height,
        )
      )
    }

    return try {
      val scaledBitmap = scaleBitmapDownIfNeeded(decodedBitmap, MAX_IMAGE_DIMENSION_PX)
      val thumbBitmap = scaleBitmapDownIfNeeded(scaledBitmap, THUMBNAIL_DIMENSION_PX)

      val outputMime: String
      val inlineBytes: ByteArray
      // Preserve lossless PNG/WebP for screenshots, UI designs, diagrams, tables, and crisp text up to 4 MB
      if (
        (mimeType == "image/png" || mimeType == "image/webp") &&
          bytes.size.toLong() <= MAX_LOSSLESS_IMAGE_BYTES &&
          scaledBitmap == decodedBitmap
      ) {
        outputMime = mimeType
        inlineBytes = bytes
      } else {
        outputMime = "image/jpeg"
        inlineBytes = compressBitmapToJpeg(scaledBitmap, quality = HIGH_FIDELITY_JPEG_QUALITY)
      }

      val thumbBytes = compressBitmapToJpeg(thumbBitmap, quality = 75)

      val inlineB64 = encodeBase64NoWrap(inlineBytes)
      val thumbB64 = encodeBase64NoWrap(thumbBytes)

      if (thumbBitmap !== scaledBitmap && thumbBitmap !== decodedBitmap) {
        thumbBitmap.recycle()
      }
      if (scaledBitmap !== decodedBitmap) {
        scaledBitmap.recycle()
      }

      Result.success(
        ChatAttachment(
          id = UUID.randomUUID().toString(),
          fileName = fileName,
          mimeType = mimeType,
          fileSizeBytes = bytes.size.toLong(),
          kind = AttachmentKind.IMAGE,
          pageCount = null,
          extractedText =
            "Image dimensions: ${width}x${height}px ($aspectDescription, format: $outputMime)",
          inlineBase64Data = inlineB64,
          inlineMimeType = outputMime,
          thumbnailBase64 = thumbB64,
          imageWidth = width,
          imageHeight = height,
        )
      )
    } finally {
      decodedBitmap.recycle()
    }
  }

  /**
   * Parses width and height directly from valid PNG, JPEG, or WebP binary headers.
   * Ensures authentic image validation even when Android's native BitmapFactory is unmocked in JVM tests.
   */
  internal fun parseImageDimensionsFromHeader(bytes: ByteArray): Pair<Int, Int>? {
    if (bytes.size < 24) return null

    // 1. PNG: 89 50 4E 47 0D 0A 1A 0A + IHDR chunk at offset 12..23
    if (
      bytes[0] == 0x89.toByte() &&
        bytes[1] == 0x50.toByte() &&
        bytes[2] == 0x4E.toByte() &&
        bytes[3] == 0x47.toByte()
    ) {
      val w = readIntBigEndian(bytes, 16)
      val h = readIntBigEndian(bytes, 20)
      if (w in 1..32768 && h in 1..32768) return w to h
    }

    // 2. JPEG: FF D8 ... SOF markers (FF C0, FF C1, FF C2, FF C3)
    if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
      var pos = 2
      while (pos + 8 < bytes.size) {
        if (bytes[pos] != 0xFF.toByte()) {
          pos++
          continue
        }
        var marker = bytes[pos + 1].toInt() and 0xFF
        while (marker == 0xFF && pos + 2 < bytes.size) {
          pos++
          marker = bytes[pos + 1].toInt() and 0xFF
        }
        if (marker == 0xD8 || marker == 0xD9 || marker in 0xD0..0xD7 || marker == 0x01) {
          pos += 2
          continue
        }
        if (pos + 3 >= bytes.size) break
        val segmentLength = ((bytes[pos + 2].toInt() and 0xFF) shl 8) or (bytes[pos + 3].toInt() and 0xFF)
        if (segmentLength < 2) break
        if (marker in listOf(0xC0, 0xC1, 0xC2, 0xC3) && pos + 8 < bytes.size) {
          val h = ((bytes[pos + 5].toInt() and 0xFF) shl 8) or (bytes[pos + 6].toInt() and 0xFF)
          val w = ((bytes[pos + 7].toInt() and 0xFF) shl 8) or (bytes[pos + 8].toInt() and 0xFF)
          if (w in 1..32768 && h in 1..32768) return w to h
        }
        pos += 2 + segmentLength
      }
    }

    // 3. WebP: "RIFF" .... "WEBP"
    if (
      bytes.size >= 30 &&
        bytes[0] == 'R'.code.toByte() &&
        bytes[1] == 'I'.code.toByte() &&
        bytes[2] == 'F'.code.toByte() &&
        bytes[3] == 'F'.code.toByte() &&
        bytes[8] == 'W'.code.toByte() &&
        bytes[9] == 'E'.code.toByte() &&
        bytes[10] == 'B'.code.toByte() &&
        bytes[11] == 'P'.code.toByte()
    ) {
      val chunkHeader = String(bytes, 12, 4, StandardCharsets.US_ASCII)
      when (chunkHeader) {
        "VP8X" -> {
          val w = 1 + ((bytes[24].toInt() and 0xFF) or ((bytes[25].toInt() and 0xFF) shl 8) or ((bytes[26].toInt() and 0xFF) shl 16))
          val h = 1 + ((bytes[27].toInt() and 0xFF) or ((bytes[28].toInt() and 0xFF) shl 8) or ((bytes[29].toInt() and 0xFF) shl 16))
          if (w in 1..32768 && h in 1..32768) return w to h
        }
        "VP8 " -> {
          val w = ((bytes[26].toInt() and 0xFF) or ((bytes[27].toInt() and 0xFF) shl 8)) and 0x3FFF
          val h = ((bytes[28].toInt() and 0xFF) or ((bytes[29].toInt() and 0xFF) shl 8)) and 0x3FFF
          if (w in 1..32768 && h in 1..32768) return w to h
        }
        "VP8L" -> {
          val b0 = bytes[21].toInt() and 0xFF
          val b1 = bytes[22].toInt() and 0xFF
          val b2 = bytes[23].toInt() and 0xFF
          val b3 = bytes[24].toInt() and 0xFF
          val w = 1 + (b0 or ((b1 and 0x3F) shl 8))
          val h = 1 + ((b1 shr 6) or (b2 shl 2) or ((b3 and 0x0F) shl 10))
          if (w in 1..32768 && h in 1..32768) return w to h
        }
      }
    }

    return null
  }

  private fun readIntBigEndian(bytes: ByteArray, offset: Int): Int {
    return ((bytes[offset].toInt() and 0xFF) shl 24) or
      ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
      ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
      (bytes[offset + 3].toInt() and 0xFF)
  }

  private fun describeAspectRatio(width: Int, height: Int): String {
    if (width <= 0 || height <= 0) return "standard"
    val ratio = width.toDouble() / height.toDouble()
    return when {
      ratio < 0.62 -> "tall portrait / mobile screenshot layout"
      ratio in 0.62..0.88 -> "portrait document / page layout"
      ratio in 0.88..1.15 -> "square / balanced visual layout"
      ratio in 1.15..1.90 -> "landscape photo / chart / diagram layout"
      else -> "wide panoramic / table / flowchart layout"
    }
  }

  private fun processPdfBytes(
    fileName: String,
    bytes: ByteArray,
    cacheDir: File?,
  ): Result<ChatAttachment> {
    // 1. Validate PDF magic header ("%PDF-")
    val headerPreview =
      String(bytes, 0, minOf(bytes.size, 1024), StandardCharsets.ISO_8859_1)
    if (!headerPreview.contains("%PDF-")) {
      return Result.failure(
        IllegalArgumentException(
          "\"$fileName\" is not a valid PDF document (missing %PDF header)."
        )
      )
    }

    val rawLatin1 = String(bytes, StandardCharsets.ISO_8859_1)
    val extractedPages = extractPdfTextPages(bytes, rawLatin1)

    if (
      rawLatin1.contains("/Encrypt") &&
        !rawLatin1.contains("/Subtype /Form") &&
        extractedPages.isEmpty()
    ) {
      return Result.failure(
        IllegalArgumentException(
          "\"$fileName\" appears to be password-protected or encrypted. Please provide an unencrypted PDF."
        )
      )
    }

    // 2. Render visual pages using Android PdfRenderer via a secure temp file that is always deleted
    var rendererPageCount = 0
    val renderedPageImagesB64 = mutableListOf<String>()
    var firstPageThumbB64: String? = null

    if (cacheDir != null) {
      var tempPdfFile: File? = null
      try {
        if (!cacheDir.exists()) {
          cacheDir.mkdirs()
        }
        tempPdfFile = File.createTempFile("astryn_pdf_${UUID.randomUUID()}_", ".tmp", cacheDir)
        FileOutputStream(tempPdfFile).use { fos ->
          fos.write(bytes)
          fos.flush()
        }

        ParcelFileDescriptor.open(tempPdfFile, ParcelFileDescriptor.MODE_READ_ONLY)?.use { pfd ->
          PdfRenderer(pfd).use { renderer ->
            rendererPageCount = renderer.pageCount
            val pagesToRender = minOf(renderer.pageCount, MAX_RENDERED_PDF_PAGES)
            for (pageIndex in 0 until pagesToRender) {
              renderer.openPage(pageIndex).use { page ->
                val scale =
                  minOf(
                    1440f / page.width.coerceAtLeast(1).toFloat(),
                    1920f / page.height.coerceAtLeast(1).toFloat(),
                    2.2f,
                  ).coerceAtLeast(1.0f)
                val bmpW = (page.width * scale).roundToInt().coerceAtLeast(1)
                val bmpH = (page.height * scale).roundToInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                try {
                  val canvas = Canvas(bitmap)
                  canvas.drawColor(Color.WHITE)
                  page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                  val jpegBytes = compressBitmapToJpeg(bitmap, quality = 88)
                  renderedPageImagesB64.add(encodeBase64NoWrap(jpegBytes))
                  if (pageIndex == 0) {
                    val thumbBmp = scaleBitmapDownIfNeeded(bitmap, THUMBNAIL_DIMENSION_PX)
                    val thumbJpeg = compressBitmapToJpeg(thumbBmp, quality = 72)
                    firstPageThumbB64 = encodeBase64NoWrap(thumbJpeg)
                    if (thumbBmp !== bitmap) {
                      thumbBmp.recycle()
                    }
                  }
                } finally {
                  bitmap.recycle()
                }
              }
            }
          }
        }
      } catch (_: Throwable) {
        // PdfRenderer may be stubbed in local JVM unit tests; stream text extractor + inline PDF handles it
      } finally {
        tempPdfFile?.let { file ->
          try {
            if (file.exists()) {
              file.delete()
            }
          } catch (_: Exception) {}
        }
      }
    }

    val regexPageCount = Regex("""/Type\s*/Page\b(?!s)""").findAll(rawLatin1).count()
    val hasEmbeddedImagesOrXObjects =
      rawLatin1.contains("/Subtype /Image") ||
        rawLatin1.contains("/Subtype/Image") ||
        rawLatin1.contains("/XObject")
    val hasVectorGraphics =
      Regex("""\b\d+(?:\.\d+)?\s+\d+(?:\.\d+)?\s+m\b""").containsMatchIn(rawLatin1) ||
        rawLatin1.contains("/Diagram") ||
        rawLatin1.contains("Flowchart")

    // Detect corrupted PDF that has no page objects, no text streams, no images, and no trailer/EOF
    if (
      regexPageCount == 0 &&
        rendererPageCount == 0 &&
        extractedPages.isEmpty() &&
        !hasEmbeddedImagesOrXObjects &&
        !rawLatin1.contains("%%EOF") &&
        !rawLatin1.contains("/Root")
    ) {
      return Result.failure(
        IllegalArgumentException(
          "\"$fileName\" appears to be corrupted or incomplete and contains no readable PDF pages."
        )
      )
    }

    val totalPageCount =
      maxOf(rendererPageCount, regexPageCount, extractedPages.size, 1)
    val analyzedPageCount =
      if (extractedPages.isNotEmpty()) {
        minOf(extractedPages.size, MAX_PDF_PAGES_TO_ANALYZE)
      } else {
        minOf(totalPageCount, MAX_PDF_PAGES_TO_ANALYZE)
      }
    val omittedPageCount = (totalPageCount - analyzedPageCount).coerceAtLeast(0)
    val isScannedPdf = extractedPages.isEmpty() || extractedPages.all { it.length < 15 }

    val pageTextMap = LinkedHashMap<Int, String>()
    for (i in 0 until minOf(extractedPages.size, MAX_PDF_PAGES_TO_ANALYZE)) {
      val pageText = extractedPages[i].trim()
      if (pageText.isNotEmpty()) {
        pageTextMap[i + 1] = pageText
      }
    }

    val docSummary =
      buildPdfDocumentOverview(
        fileName = fileName,
        totalPageCount = totalPageCount,
        analyzedPageCount = analyzedPageCount,
        omittedPageCount = omittedPageCount,
        isScannedPdf = isScannedPdf,
        hasEmbeddedImages = hasEmbeddedImagesOrXObjects,
        hasVectorDiagrams = hasVectorGraphics,
        pageTextMap = pageTextMap,
      )

    val combinedExtractedText =
      if (pageTextMap.isNotEmpty()) {
        buildString {
          for ((pageNum, pageText) in pageTextMap) {
            if (isNotEmpty()) append("\n\n")
            append("[Page $pageNum of $totalPageCount]\n")
            append(pageText)
          }
          if (omittedPageCount > 0) {
            append(
              "\n\n[Disclosure: Analyzed pages 1–$analyzedPageCount of $totalPageCount total pages. " +
                "Pages ${analyzedPageCount + 1}–$totalPageCount ($omittedPageCount pages) exceeded the extraction limit and were unavailable.]"
            )
          }
        }
      } else {
        "[Note: No embedded selectable text stream was found in \"$fileName\" ($totalPageCount pages). " +
          "This PDF is a scanned or image-based document; inspect the attached binary PDF / rendered page images directly, " +
          "and state clearly if any section is blurry or unreadable.]"
      }

    val inlinePdfB64 =
      if (bytes.size.toLong() <= MAX_INLINE_PDF_BYTES) {
        encodeBase64NoWrap(bytes)
      } else {
        null
      }

    return Result.success(
      ChatAttachment(
        id = UUID.randomUUID().toString(),
        fileName = fileName,
        mimeType = PDF_MIME_TYPE,
        fileSizeBytes = bytes.size.toLong(),
        kind = AttachmentKind.PDF,
        pageCount = totalPageCount,
        extractedText = combinedExtractedText,
        inlineBase64Data = inlinePdfB64,
        inlineMimeType = PDF_MIME_TYPE,
        renderedPageImagesBase64 = renderedPageImagesB64,
        thumbnailBase64 = firstPageThumbB64,
        analyzedPageCount = analyzedPageCount,
        omittedPageCount = omittedPageCount,
        isScannedOrImageBasedPdf = isScannedPdf,
        documentSummary = docSummary,
        pageTextMap = pageTextMap,
      )
    )
  }

  /**
   * Builds a structured document-level summary indexing headings, chapters, tables, diagrams,
   * questions, dates, and page coverage across the PDF.
   */
  internal fun buildPdfDocumentOverview(
    fileName: String,
    totalPageCount: Int,
    analyzedPageCount: Int,
    omittedPageCount: Int,
    isScannedPdf: Boolean,
    hasEmbeddedImages: Boolean,
    hasVectorDiagrams: Boolean,
    pageTextMap: Map<Int, String>,
  ): String {
    if (isScannedPdf) {
      return buildString {
        append("Scanned/Image-based PDF \"$fileName\" ($totalPageCount pages")
        if (hasEmbeddedImages) append(", contains embedded page scans/images")
        append("). Use visual multimodal inspection.")
      }
    }

    val detectedHeadings = mutableListOf<String>()
    var tablePageNumbers = mutableListOf<Int>()
    var diagramPageNumbers = mutableListOf<Int>()
    var questionCount = 0

    val headingRegex =
      Regex(
        """(?im)^(?:chapter\s+\d+[^\n]*|section\s+\d+[^\n]*|unit\s+\d+[^\n]*|part\s+[A-Z0-9]+[^\n]*|q(?:uestion)?\s*\.?\s*\d+[^\n]*|[A-Z][A-Za-z0-9\s,&\-]{3,45}:)$"""
      )
    for ((pageNum, text) in pageTextMap) {
      val lower = text.lowercase(Locale.ROOT)
      if (text.contains(" | ") || lower.contains("table") || Regex("""\b\d+\s+\|\s+\d+""").containsMatchIn(text)) {
        tablePageNumbers.add(pageNum)
      }
      if (
        lower.contains("diagram") ||
          lower.contains("figure") ||
          lower.contains("fig.") ||
          lower.contains("flowchart") ||
          lower.contains("architecture") ||
          lower.contains("chart")
      ) {
        diagramPageNumbers.add(pageNum)
      }
      questionCount += Regex("""(?im)(?:^|\b)(?:Q(?:uestion)?\.?\s*\d+|प्रश्न\s*\d+)""").findAll(text).count()

      for (line in text.lines()) {
        val trimmed = line.trim()
        if (
          trimmed.length in 4..70 &&
            (headingRegex.matches(trimmed) ||
              trimmed.startsWith("Chapter ", ignoreCase = true) ||
              trimmed.startsWith("Section ", ignoreCase = true) ||
              trimmed.startsWith("Table ", ignoreCase = true) ||
              trimmed.startsWith("Figure ", ignoreCase = true))
        ) {
          if (detectedHeadings.size < 8) {
            detectedHeadings.add("P.$pageNum: $trimmed")
          }
        }
      }
    }

    return buildString {
      append("Pages: $analyzedPageCount/$totalPageCount analyzed")
      if (omittedPageCount > 0) {
        append(" ($omittedPageCount pages omitted due to cap)")
      }
      if (tablePageNumbers.isNotEmpty()) {
        append(" | Tables on page(s): ${tablePageNumbers.distinct().joinToString(",")}")
      }
      if (diagramPageNumbers.isNotEmpty() || hasVectorDiagrams) {
        val diagDesc =
          if (diagramPageNumbers.isNotEmpty()) "page(s) ${diagramPageNumbers.distinct().joinToString(",")}"
          else "embedded vector graphics"
        append(" | Diagrams/Figures: $diagDesc")
      }
      if (questionCount > 0) {
        append(" | Questions detected: $questionCount")
      }
      if (detectedHeadings.isNotEmpty()) {
        append(" | Structure: ${detectedHeadings.joinToString("; ")}")
      }
    }
  }

  /**
   * Extracts readable page-grouped text from uncompressed and FlateDecode PDF content streams.
   * Supports `(text) Tj`, `[(text) -250 (col2)] TJ` table kerning, `<HEX>` strings (ASCII & UTF-16BE),
   * and PDF octal/escaped characters.
   */
  internal fun extractPdfTextPages(bytes: ByteArray, rawLatin1: String): List<String> {
    val streamMarkerRegex = Regex("""stream\r?\n""")
    val pages = mutableListOf<String>()
    var searchIndex = 0
    var scannedStreamChunks = 0
    val maxStreamChunks = 400

    while (
      searchIndex < rawLatin1.length &&
        pages.size < MAX_PDF_PAGES_TO_ANALYZE &&
        scannedStreamChunks < maxStreamChunks
    ) {
      val match = streamMarkerRegex.find(rawLatin1, searchIndex) ?: break
      scannedStreamChunks++
      val dataStart = match.range.last + 1
      val endStreamIndex = rawLatin1.indexOf("endstream", dataStart)
      if (endStreamIndex <= dataStart) {
        searchIndex = dataStart + 1
        continue
      }

      var dataEnd = endStreamIndex
      if (dataEnd > dataStart && rawLatin1[dataEnd - 1] == '\n') dataEnd--
      if (dataEnd > dataStart && rawLatin1[dataEnd - 1] == '\r') dataEnd--

      val dictStart = (match.range.first - 260).coerceAtLeast(0)
      val dictHeader = rawLatin1.substring(dictStart, match.range.first)
      val isFlate = dictHeader.contains("/FlateDecode")

      val streamBytes = bytes.copyOfRange(dataStart, dataEnd)
      val decodedStreamText =
        if (isFlate) {
          inflatePdfStreamSafely(streamBytes)
        } else {
          String(streamBytes, StandardCharsets.ISO_8859_1)
        }

      if (
        !decodedStreamText.isNullOrBlank() &&
          (decodedStreamText.contains("BT") ||
            decodedStreamText.contains("Tj") ||
            decodedStreamText.contains("TJ"))
      ) {
        val textFromStream = parsePdfTextOperators(decodedStreamText)
        if (textFromStream.isNotBlank()) {
          pages.add(textFromStream)
        }
      }

      searchIndex = endStreamIndex + "endstream".length
    }

    return pages
  }

  private fun inflatePdfStreamSafely(compressed: ByteArray): String? {
    for (nowrap in booleanArrayOf(false, true)) {
      val inflater = Inflater(nowrap)
      try {
        inflater.setInput(compressed)
        val out = ByteArrayOutputStream(compressed.size * 2)
        val buffer = ByteArray(4096)
        var totalWritten = 0
        while (!inflater.finished() && totalWritten < 768_000) {
          val count = inflater.inflate(buffer)
          if (count <= 0) {
            if (inflater.needsInput() || inflater.needsDictionary()) break
            break
          }
          out.write(buffer, 0, count)
          totalWritten += count
        }
        if (out.size() > 0) {
          return String(out.toByteArray(), StandardCharsets.ISO_8859_1)
        }
      } catch (_: Exception) {
        // Try alternate nowrap mode
      } finally {
        inflater.end()
      }
    }
    return null
  }

  private fun parsePdfTextOperators(contentStream: String): String {
    val btEtRegex = Regex("""BT\b([\s\S]*?)\bET""")
    val blocks = btEtRegex.findAll(contentStream).toList()
    val targetText =
      if (blocks.isNotEmpty()) {
        blocks.joinToString("\n") { it.groupValues[1] }
      } else {
        contentStream
      }

    val out = StringBuilder()
    var i = 0
    val len = targetText.length
    var insideTjArray = false

    while (i < len) {
      val ch = targetText[i]
      if (ch == '[') {
        insideTjArray = true
        i++
      } else if (ch == ']') {
        insideTjArray = false
        i++
      } else if (ch == '(') {
        val literal = StringBuilder()
        var depth = 1
        i++
        while (i < len && depth > 0) {
          val c = targetText[i]
          if (c == '\\' && i + 1 < len) {
            val next = targetText[i + 1]
            when (next) {
              'n' -> {
                literal.append('\n')
                i += 2
              }
              'r' -> {
                literal.append('\r')
                i += 2
              }
              't' -> {
                literal.append('\t')
                i += 2
              }
              '(', ')', '\\' -> {
                literal.append(next)
                i += 2
              }
              in '0'..'7' -> {
                var oct = "" + next
                var advance = 2
                if (i + 2 < len && targetText[i + 2] in '0'..'7') {
                  oct += targetText[i + 2]
                  advance++
                  if (i + 3 < len && targetText[i + 3] in '0'..'7') {
                    oct += targetText[i + 3]
                    advance++
                  }
                }
                val code = oct.toIntOrNull(8) ?: 32
                literal.append(code.toChar())
                i += advance
              }
              else -> {
                literal.append(next)
                i += 2
              }
            }
          } else if (c == '(') {
            depth++
            literal.append(c)
            i++
          } else if (c == ')') {
            depth--
            if (depth > 0) literal.append(c)
            i++
          } else {
            literal.append(c)
            i++
          }
        }
        val cleaned = decodeUtf8OrLatin1Literal(literal.toString())
        if (cleaned.isNotEmpty()) {
          out.append(cleaned)
        }
      } else if (ch == '<' && (i + 1 >= len || targetText[i + 1] != '<')) {
        // Parse PDF Hexadecimal string <...> (supports UTF-16BE FEFF and ASCII/UTF-8 hex)
        val endHex = targetText.indexOf('>', i + 1)
        if (endHex > i) {
          val hexContent = targetText.substring(i + 1, endHex).replace(Regex("\\s+"), "")
          val decodedHex = decodePdfHexString(hexContent)
          if (decodedHex.isNotEmpty()) {
            out.append(decodedHex)
          }
          i = endHex + 1
        } else {
          i++
        }
      } else if (insideTjArray && (ch == '-' || ch in '0'..'9')) {
        // Kerning adjustment inside [(col1) -350 (col2)] TJ
        val numStart = i
        i++
        while (i < len && (targetText[i] in '0'..'9' || targetText[i] == '.')) {
          i++
        }
        val kernVal = targetText.substring(numStart, i).toDoubleOrNull()
        if (kernVal != null) {
          if (kernVal <= -240.0) {
            if (out.isNotEmpty() && !out.endsWith(" | ")) {
              out.append(" | ")
            }
          } else if (kernVal <= -75.0) {
            if (out.isNotEmpty() && !out.last().isWhitespace()) {
              out.append(' ')
            }
          }
        }
      } else if (
        ch == 'T' &&
          i + 1 < len &&
          (targetText[i + 1] == 'D' || targetText[i + 1] == 'd' || targetText[i + 1] == '*')
      ) {
        if (out.isNotEmpty() && out.last() != '\n') {
          out.append('\n')
        }
        i += 2
      } else if (ch == 'T' && i + 1 < len && (targetText[i + 1] == 'j' || targetText[i + 1] == 'J')) {
        if (out.isNotEmpty() && !out.last().isWhitespace()) {
          out.append(' ')
        }
        i += 2
      } else {
        i++
      }
    }

    return out
      .toString()
      .lines()
      .map { it.replace(Regex("[ \\t]+"), " ").trim() }
      .filter { it.isNotEmpty() }
      .joinToString("\n")
  }

  private fun decodeUtf8OrLatin1Literal(rawLiteral: String): String {
    val stripped = rawLiteral.replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")
    if (stripped.isEmpty()) return ""
    // Check if the literal contains UTF-16BE BOM (\u00FE\u00FF) or UTF-8 multi-byte sequences encoded in Latin-1
    val bytes = stripped.toByteArray(StandardCharsets.ISO_8859_1)
    if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
      return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE).trim()
    }
    if (bytes.any { (it.toInt() and 0x80) != 0 }) {
      val utf8Candidate = String(bytes, StandardCharsets.UTF_8)
      if (!utf8Candidate.contains('\uFFFD')) {
        return utf8Candidate
      }
    }
    return stripped
  }

  private fun decodePdfHexString(hex: String): String {
    if (hex.isEmpty()) return ""
    val padded = if (hex.length % 2 == 1) "${hex}0" else hex
    val byteCount = padded.length / 2
    val bytes = ByteArray(byteCount)
    for (idx in 0 until byteCount) {
      val byteVal = padded.substring(idx * 2, idx * 2 + 2).toIntOrNull(16) ?: return ""
      bytes[idx] = byteVal.toByte()
    }
    if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
      return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)
        .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")
    }
    val utf8 = String(bytes, StandardCharsets.UTF_8)
    return if (!utf8.contains('\uFFFD')) {
      utf8.replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")
    } else {
      String(bytes, StandardCharsets.ISO_8859_1)
        .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")
    }
  }

  private fun encodeBase64NoWrap(bytes: ByteArray): String {
    return try {
      java.util.Base64.getEncoder().encodeToString(bytes)
    } catch (_: Throwable) {
      android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }
  }

  private fun scaleBitmapDownIfNeeded(source: Bitmap, maxDimension: Int): Bitmap {
    val w = source.width
    val h = source.height
    if (w <= maxDimension && h <= maxDimension) return source
    val ratio = maxDimension.toFloat() / max(w, h).toFloat()
    val targetW = (w * ratio).roundToInt().coerceAtLeast(1)
    val targetH = (h * ratio).roundToInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(source, targetW, targetH, true)
  }

  private fun compressBitmapToJpeg(bitmap: Bitmap, quality: Int): ByteArray {
    val bos = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, bos)
    return bos.toByteArray()
  }

  private fun readBytesBounded(input: java.io.InputStream, maxBytes: Long): ByteArray {
    val bos = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0L
    while (true) {
      val read = input.read(buffer)
      if (read < 0) break
      total += read
      if (total > maxBytes) {
        throw IllegalArgumentException(
          "Selected file exceeds the maximum allowed size of ${ChatAttachment.formatBytes(maxBytes)}."
        )
      }
      bos.write(buffer, 0, read)
    }
    return bos.toByteArray()
  }
}
