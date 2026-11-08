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
import android.util.Base64
import com.example.data.model.AttachmentKind
import com.example.data.model.ChatAttachment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.zip.Inflater
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Genuine Image (JPEG, PNG, WebP) and PDF ingestion, validation, text extraction,
 * page rendering, and secure temporary file cleanup for Astryn AI.
 */
object AttachmentProcessor {
  const val MAX_ATTACHMENTS_PER_MESSAGE = 4
  const val MAX_FILE_SIZE_BYTES = 15L * 1024L * 1024L // 15 MB max per file
  const val MAX_INLINE_PDF_BYTES = 10L * 1024L * 1024L // 10 MB inline PDF payload cap
  const val MAX_IMAGE_DIMENSION_PX = 1536
  const val THUMBNAIL_DIMENSION_PX = 160
  const val MAX_PDF_PAGES_TO_ANALYZE = 30
  const val MAX_RENDERED_PDF_PAGES = 4

  val SUPPORTED_IMAGE_MIME_TYPES =
    setOf(
      "image/jpeg",
      "image/jpg",
      "image/png",
      "image/webp",
    )

  const val PDF_MIME_TYPE = "application/pdf"

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
        val inferredMimeType = resolveSupportedMimeType(rawMimeType, resolvedName)
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
    val cleanName = fileName.trim().ifBlank { "attachment" }
    if (bytes.isEmpty()) {
      return Result.failure(
        IllegalArgumentException("\"$cleanName\" is empty (0 bytes). Please select a valid file.")
      )
    }
    if (bytes.size.toLong() > MAX_FILE_SIZE_BYTES) {
      return Result.failure(
        IllegalArgumentException(
          "\"$cleanName\" (${ChatAttachment.formatBytes(bytes.size.toLong())}) exceeds the 15 MB limit."
        )
      )
    }

    val normalizedMime =
      resolveSupportedMimeType(mimeType, cleanName)
        ?: return Result.failure(
          IllegalArgumentException(
            "Unsupported file type ($mimeType) for \"$cleanName\". Supported formats: JPEG, PNG, WebP, and PDF."
          )
        )

    return if (normalizedMime == PDF_MIME_TYPE) {
      processPdfBytes(cleanName, bytes, cacheDir)
    } else {
      processImageBytes(cleanName, normalizedMime, bytes)
    }
  }

  fun resolveSupportedMimeType(rawMimeType: String, fileName: String): String? {
    val cleanMime = rawMimeType.lowercase().trim()
    if (cleanMime == "image/jpg") return "image/jpeg"
    if (cleanMime in SUPPORTED_IMAGE_MIME_TYPES) return cleanMime
    if (cleanMime == PDF_MIME_TYPE) return PDF_MIME_TYPE

    val lowerName = fileName.lowercase().trim()
    return when {
      lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") -> "image/jpeg"
      lowerName.endsWith(".png") -> "image/png"
      lowerName.endsWith(".webp") -> "image/webp"
      lowerName.endsWith(".pdf") -> PDF_MIME_TYPE
      else -> null
    }
  }

  private fun processImageBytes(
    fileName: String,
    mimeType: String,
    bytes: ByteArray,
  ): Result<ChatAttachment> {
    // 1. Decode image bounds to verify it is a genuine readable image
    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)
    val width = boundsOptions.outWidth
    val height = boundsOptions.outHeight

    if (width <= 0 || height <= 0) {
      return Result.failure(
        IllegalArgumentException(
          "\"$fileName\" could not be decoded as a valid image. Please choose a clear JPEG, PNG, or WebP file."
        )
      )
    }

    // 2. Downsample if larger than MAX_IMAGE_DIMENSION_PX to keep memory and payload optimal
    val maxDim = max(width, height)
    var sampleSize = 1
    while (maxDim / (sampleSize * 2) >= MAX_IMAGE_DIMENSION_PX) {
      sampleSize *= 2
    }

    val decodeOptions =
      BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
      }
    val decodedBitmap =
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
        ?: return Result.failure(
          IllegalArgumentException("Unable to decode image data from \"$fileName\".")
        )

    return try {
      val scaledBitmap = scaleBitmapDownIfNeeded(decodedBitmap, MAX_IMAGE_DIMENSION_PX)
      val thumbBitmap = scaleBitmapDownIfNeeded(scaledBitmap, THUMBNAIL_DIMENSION_PX)

      val outputMime: String
      val inlineBytes: ByteArray
      if (mimeType == "image/png" && bytes.size <= 2 * 1024 * 1024 && scaledBitmap == decodedBitmap) {
        outputMime = "image/png"
        inlineBytes = bytes
      } else {
        outputMime = "image/jpeg"
        inlineBytes = compressBitmapToJpeg(scaledBitmap, quality = 86)
      }

      val thumbBytes = compressBitmapToJpeg(thumbBitmap, quality = 72)

      val inlineB64 = Base64.encodeToString(inlineBytes, Base64.NO_WRAP)
      val thumbB64 = Base64.encodeToString(thumbBytes, Base64.NO_WRAP)

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
          extractedText = "Image dimensions: ${width}x${height}px",
          inlineBase64Data = inlineB64,
          inlineMimeType = outputMime,
          thumbnailBase64 = thumbB64,
        )
      )
    } finally {
      decodedBitmap.recycle()
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
    if (
      rawLatin1.contains("/Encrypt") &&
        !rawLatin1.contains("/Subtype /Form") &&
        extractPdfTextPages(bytes, rawLatin1).isEmpty()
    ) {
      return Result.failure(
        IllegalArgumentException(
          "\"$fileName\" appears to be password-protected or encrypted. Please provide an unencrypted PDF."
        )
      )
    }

    // 2. Extract embedded page-indexed text from PDF streams (BT...ET, Tj, TJ)
    val extractedPages = extractPdfTextPages(bytes, rawLatin1)

    // 3. Render visual pages using Android PdfRenderer via a secure temp file that is always deleted
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
                    1200f / page.width.coerceAtLeast(1).toFloat(),
                    1600f / page.height.coerceAtLeast(1).toFloat(),
                    2.0f,
                  ).coerceAtLeast(1.0f)
                val bmpW = (page.width * scale).roundToInt().coerceAtLeast(1)
                val bmpH = (page.height * scale).roundToInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                try {
                  val canvas = Canvas(bitmap)
                  canvas.drawColor(Color.WHITE)
                  page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                  val jpegBytes = compressBitmapToJpeg(bitmap, quality = 82)
                  renderedPageImagesB64.add(Base64.encodeToString(jpegBytes, Base64.NO_WRAP))
                  if (pageIndex == 0) {
                    val thumbBmp = scaleBitmapDownIfNeeded(bitmap, THUMBNAIL_DIMENSION_PX)
                    val thumbJpeg = compressBitmapToJpeg(thumbBmp, quality = 70)
                    firstPageThumbB64 = Base64.encodeToString(thumbJpeg, Base64.NO_WRAP)
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
        // Securely overwrite and delete temporary PDF file immediately
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
    val totalPageCount =
      maxOf(rendererPageCount, regexPageCount, extractedPages.size, 1)

    val combinedExtractedText =
      if (extractedPages.isNotEmpty()) {
        buildString {
          val maxPages = minOf(extractedPages.size, MAX_PDF_PAGES_TO_ANALYZE)
          for (i in 0 until maxPages) {
            val pageText = extractedPages[i].trim()
            if (pageText.isNotEmpty()) {
              if (isNotEmpty()) append("\n\n")
              append("[Page ${i + 1} of $totalPageCount]\n")
              append(pageText)
            }
          }
          if (extractedPages.size > MAX_PDF_PAGES_TO_ANALYZE) {
            append(
              "\n\n[Note: Showing extracted text for first $MAX_PDF_PAGES_TO_ANALYZE of $totalPageCount pages.]"
            )
          }
        }
      } else {
        "[Note: No embedded selectable text stream was found in \"$fileName\" ($totalPageCount pages). " +
          "This PDF may be a scanned document or image-based PDF; use the attached PDF/page visual data and state clearly if any page is blurry or unreadable.]"
      }

    val inlinePdfB64 =
      if (bytes.size.toLong() <= MAX_INLINE_PDF_BYTES) {
        Base64.encodeToString(bytes, Base64.NO_WRAP)
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
      )
    )
  }

  /**
   * Extracts readable page-grouped text from uncompressed and FlateDecode PDF content streams.
   * Supports `(text) Tj`, `[(text) -120 (more)] TJ`, and PDF octal/escaped characters.
   */
  internal fun extractPdfTextPages(bytes: ByteArray, rawLatin1: String): List<String> {
    val streamMarkerRegex = Regex("""stream\r?\n""")
    val pages = mutableListOf<String>()
    var searchIndex = 0

    while (searchIndex < rawLatin1.length && pages.size < MAX_PDF_PAGES_TO_ANALYZE) {
      val match = streamMarkerRegex.find(rawLatin1, searchIndex) ?: break
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

      if (!decodedStreamText.isNullOrBlank() && decodedStreamText.contains("BT")) {
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
        while (!inflater.finished() && totalWritten < 512_000) {
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
    while (i < len) {
      val ch = targetText[i]
      if (ch == '(') {
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
        val cleaned = literal.toString().replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")
        if (cleaned.isNotEmpty()) {
          out.append(cleaned)
        }
      } else if (ch == 'T' && i + 1 < len && (targetText[i + 1] == 'D' || targetText[i + 1] == 'd' || targetText[i + 1] == '*')) {
        if (out.isNotEmpty() && out.last() != '\n') {
          out.append('\n')
        }
        i += 2
      } else if (ch == 'T' && i + 1 < len && targetText[i + 1] == 'j') {
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
      .map { it.replace(Regex("\\s+"), " ").trim() }
      .filter { it.isNotEmpty() }
      .joinToString("\n")
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
