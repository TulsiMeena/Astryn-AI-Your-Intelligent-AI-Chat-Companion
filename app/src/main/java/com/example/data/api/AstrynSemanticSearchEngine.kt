package com.example.data.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * ASTRYN AI — PHASE 15
 * REAL EMBEDDING & SEMANTIC SEARCH ENGINE (WITH HONEST BM25 LEXICAL FALLBACK)
 *
 * - Validates and uses verified Gemini embedding model (`gemini-embedding-001` on `v1beta`).
 * - Rejects deprecated embedding identifiers (`embedding-001`, `text-embedding-004`) and
 *   non-embedding text generation models (`gemini-2.5-flash`, `gemini-2.5-pro`, etc.).
 * - Supports incremental indexing keyed by `contentHash + embeddingVersion`.
 * - Falls back cleanly to a genuine BM25 + phrase/metadata lexical engine when offline,
 *   unconfigured, or if the embedding provider errors — and explicitly discloses [SearchMode].
 */
enum class EmbeddingTaskType {
  RETRIEVAL_DOCUMENT,
  RETRIEVAL_QUERY,
  SEMANTIC_SIMILARITY,
}

interface EmbeddingGateway {
  val providerName: String
  val modelId: String
  val embeddingVersion: String

  suspend fun isAvailable(): Boolean

  suspend fun embedText(
    text: String,
    taskType: EmbeddingTaskType = EmbeddingTaskType.RETRIEVAL_DOCUMENT,
  ): Result<FloatArray>
}

class GeminiEmbeddingGateway(
  private val geminiService: GeminiService = GeminiService(),
  override val modelId: String = VerifiedEmbeddingModelRegistry.DEFAULT_EMBEDDING_MODEL,
  private val outputDimensionality: Int = 768,
) : EmbeddingGateway {
  override val providerName: String = "Google Gemini Embedding API (v1beta)"
  override val embeddingVersion: String = "${modelId}_d${outputDimensionality}_v1"

  override suspend fun isAvailable(): Boolean {
    return VerifiedEmbeddingModelRegistry.validateEmbeddingModel(modelId).isSuccess &&
      GeminiService.isApiKeyConfigured()
  }

  override suspend fun embedText(
    text: String,
    taskType: EmbeddingTaskType,
  ): Result<FloatArray> {
    val validation = VerifiedEmbeddingModelRegistry.validateEmbeddingModel(modelId)
    if (validation.isFailure) {
      return Result.failure(validation.exceptionOrNull()!!)
    }
    return geminiService.embedContent(
      text = text,
      modelId = modelId,
      taskType = taskType.name,
      outputDimensionality = outputDimensionality,
    )
  }
}

object VerifiedEmbeddingModelRegistry {
  const val DEFAULT_EMBEDDING_MODEL = "gemini-embedding-001"

  private val SUPPORTED_EMBEDDING_MODELS = setOf(
    "gemini-embedding-001",
  )

  private val DEPRECATED_OR_UNSUPPORTED = setOf(
    "embedding-001",
    "text-embedding-004",
    "gemini-2.5-flash",
    "gemini-2.5-pro",
    "gemini-2.5-flash-lite",
    "gemini-3-flash-preview",
    "gemini-3-pro-preview",
  )

  fun validateEmbeddingModel(modelId: String): Result<String> {
    val clean = modelId.trim().removePrefix("models/")
    if (clean.isEmpty()) {
      return Result.failure(IllegalArgumentException("Embedding model identifier cannot be blank."))
    }
    val lower = clean.lowercase(Locale.ROOT)
    if (lower in DEPRECATED_OR_UNSUPPORTED || lower.contains("flash") || lower.contains("pro")) {
      return Result.failure(
        GeminiException.UnsupportedModel(
          modelId = clean,
          detail = "Model '$clean' is deprecated or is a text-generation model, not an embedding model. Use '$DEFAULT_EMBEDDING_MODEL'.",
        )
      )
    }
    if (lower !in SUPPORTED_EMBEDDING_MODELS) {
      return Result.failure(
        GeminiException.UnsupportedModel(
          modelId = clean,
          detail = "Unverified embedding model '$clean'. Verified model: '$DEFAULT_EMBEDDING_MODEL'.",
        )
      )
    }
    return Result.success(clean)
  }
}

enum class SearchMode {
  SEMANTIC_EMBEDDING,
  HYBRID_SEMANTIC_PLUS_LEXICAL,
  LEXICAL_BM25_FALLBACK,
}

data class ChunkVectorRecord(
  val chunkId: String,
  val documentId: String,
  val userUid: String,
  val contentHash: String,
  val embeddingVersion: String,
  val modelId: String,
  val vector: FloatArray,
  val createdAtMs: Long = System.currentTimeMillis(),
)

data class SearchFilters(
  val documentIds: Set<String> = emptySet(),
  val fileFormats: Set<KnowledgeFileFormat> = emptySet(),
  val topicFilter: String? = null,
  val minImportTimestampMs: Long? = null,
  val maxImportTimestampMs: Long? = null,
)

data class RetrievedChunkMatch(
  val chunk: DocumentChunk,
  val score: Double,
  val semanticScore: Double?,
  val lexicalBm25Score: Double,
  val searchMode: SearchMode,
  val matchExplanation: String,
)

data class ChunkIndexingOutcome(
  val embeddingsGeneratedCount: Int,
  val embeddingsReusedCount: Int,
  val modeUsed: SearchMode,
  val fallbackReason: String? = null,
)

data class SemanticSearchResponse(
  val query: String,
  val searchMode: SearchMode,
  val embeddingProviderModel: String?,
  val fallbackReason: String?,
  val fromCache: Boolean,
  val totalCandidateChunks: Int,
  val matches: List<RetrievedChunkMatch>,
)

object AstrynSemanticSearchEngine {

  @Volatile var defaultEmbeddingGateway: EmbeddingGateway? = null

  // Per-user -> chunkId -> ChunkVectorRecord
  private val userVectorStore =
    ConcurrentHashMap<String, ConcurrentHashMap<String, ChunkVectorRecord>>()

  // Per-user index epoch (incremented on every index/update/delete for cache invalidation)
  private val userIndexEpoch = ConcurrentHashMap<String, Long>()

  // Bounded query cache per user: cacheKey -> (epoch, SemanticSearchResponse)
  private val userQueryCache =
    ConcurrentHashMap<String, ConcurrentHashMap<String, Pair<Long, SemanticSearchResponse>>>()

  private val BILINGUAL_EQUIVALENTS: Map<String, Set<String>> = mapOf(
    "kharch" to setOf("expense", "expenses", "cost", "budget", "spending"),
    "kharcha" to setOf("expense", "expenses", "cost", "budget", "spending"),
    "खर्च" to setOf("expense", "expenses", "cost", "budget", "spending"),
    "खर्चे" to setOf("expense", "expenses", "cost", "budget", "spending"),
    "laagat" to setOf("cost", "budget", "total"),
    "लागत" to setOf("cost", "budget", "total"),
    "कुल" to setOf("total", "sum"),
    "kheti" to setOf("agriculture", "farming", "crop", "soil"),
    "कृषि" to setOf("agriculture", "farming", "crop", "soil"),
    "फसल" to setOf("crop", "harvest", "yield"),
    "खाद" to setOf("fertilizer", "nutrients", "nitrogen", "urea"),
    "मिट्टी" to setOf("soil", "loam", "moisture"),
    "पौधे" to setOf("plant", "plants", "photosynthesis"),
    "अध्याय" to setOf("chapter", "unit", "section"),
    "महत्वपूर्ण" to setOf("important", "key", "critical"),
    "नियम" to setOf("rule", "law", "policy", "regulation"),
    "अंतर" to setOf("difference", "compare", "conflict", "vs"),
    "फर्क" to setOf("difference", "compare", "conflict"),
    "तुलना" to setOf("compare", "comparison"),
  )

  fun invalidateUserCache(userUid: String?) {
    val scope = AstrynKnowledgeBaseEngine.normalizeUserScope(userUid)
    userIndexEpoch.compute(scope) { _, prev -> (prev ?: 0L) + 1L }
    userQueryCache.remove(scope)
  }

  fun removeDocumentVectors(userUid: String?, documentId: String) {
    val scope = AstrynKnowledgeBaseEngine.normalizeUserScope(userUid)
    val vectors = userVectorStore[scope] ?: return
    val toRemove = vectors.values.filter { it.documentId == documentId }.map { it.chunkId }
    for (cId in toRemove) {
      vectors.remove(cId)
    }
    invalidateUserCache(scope)
  }

  fun getUserVectorsCount(userUid: String?): Int {
    val scope = AstrynKnowledgeBaseEngine.normalizeUserScope(userUid)
    return userVectorStore[scope]?.size ?: 0
  }

  /**
   * Indexes chunks incrementally: reuses existing vector if `contentHash + embeddingVersion` matches,
   * validates the embedding model before network requests, and falls back to BM25 cleanly on error/offline.
   */
  suspend fun indexDocumentChunks(
    userUid: String?,
    document: KnowledgeDocumentRecord,
    chunks: List<DocumentChunk>,
    embeddingGateway: EmbeddingGateway? = defaultEmbeddingGateway,
    isOnline: Boolean = true,
  ): ChunkIndexingOutcome {
    val scope = AstrynKnowledgeBaseEngine.normalizeUserScope(userUid)
    invalidateUserCache(scope)
    if (chunks.isEmpty()) {
      return ChunkIndexingOutcome(0, 0, SearchMode.LEXICAL_BM25_FALLBACK, "No readable text chunks to embed.")
    }

    val vectorsBucket = userVectorStore.getOrPut(scope) { ConcurrentHashMap() }
    val gateway = embeddingGateway

    if (gateway == null) {
      return ChunkIndexingOutcome(
        embeddingsGeneratedCount = 0,
        embeddingsReusedCount = 0,
        modeUsed = SearchMode.LEXICAL_BM25_FALLBACK,
        fallbackReason = "No embedding gateway active; indexed cleanly with local BM25 lexical index.",
      )
    }

    val modelValidation = VerifiedEmbeddingModelRegistry.validateEmbeddingModel(gateway.modelId)
    if (modelValidation.isFailure) {
      return ChunkIndexingOutcome(
        embeddingsGeneratedCount = 0,
        embeddingsReusedCount = 0,
        modeUsed = SearchMode.LEXICAL_BM25_FALLBACK,
        fallbackReason = modelValidation.exceptionOrNull()?.message ?: "Unsupported embedding model.",
      )
    }

    if (!isOnline || !gateway.isAvailable()) {
      return ChunkIndexingOutcome(
        embeddingsGeneratedCount = 0,
        embeddingsReusedCount = 0,
        modeUsed = SearchMode.LEXICAL_BM25_FALLBACK,
        fallbackReason = if (!isOnline) "Device is offline; using local BM25 lexical index." else "Embedding API key/provider unavailable; using local BM25 lexical index.",
      )
    }

    var generated = 0
    var reused = 0
    var failureReason: String? = null

    for (chunk in chunks) {
      currentCoroutineContext().ensureActive()
      val existing = vectorsBucket[chunk.chunkId]
      if (
        existing != null &&
          existing.userUid == scope &&
          existing.contentHash == chunk.contentHash &&
          existing.embeddingVersion == gateway.embeddingVersion &&
          existing.vector.isNotEmpty()
      ) {
        reused++
        continue
      }

      val embRes = gateway.embedText(
        text = chunk.content,
        taskType = EmbeddingTaskType.RETRIEVAL_DOCUMENT,
      )
      if (embRes.isSuccess) {
        val vec = embRes.getOrThrow()
        if (vec.isNotEmpty()) {
          vectorsBucket[chunk.chunkId] = ChunkVectorRecord(
            chunkId = chunk.chunkId,
            documentId = document.documentId,
            userUid = scope,
            contentHash = chunk.contentHash,
            embeddingVersion = gateway.embeddingVersion,
            modelId = gateway.modelId,
            vector = vec,
          )
          generated++
        }
      } else {
        failureReason = embRes.exceptionOrNull()?.message ?: "Embedding generation failed."
        if (generated == 0 && reused == 0) {
          // Stop hammering endpoint on first failure (auth/rate-limit/network)
          break
        }
      }
    }

    val finalMode = if (generated > 0 || reused > 0) {
      SearchMode.HYBRID_SEMANTIC_PLUS_LEXICAL
    } else {
      SearchMode.LEXICAL_BM25_FALLBACK
    }

    return ChunkIndexingOutcome(
      embeddingsGeneratedCount = generated,
      embeddingsReusedCount = reused,
      modeUsed = finalMode,
      fallbackReason = failureReason,
    )
  }

  /**
   * Performs per-user isolated search across [AstrynKnowledgeBaseEngine] chunks.
   * Uses real vector cosine similarity combined with BM25 when embeddings are available
   * and online, or honest BM25 lexical fallback when embeddings are unavailable/offline.
   */
  suspend fun search(
    userUid: String?,
    query: String,
    topK: Int = 6,
    filter: SearchFilters = SearchFilters(),
    embeddingGateway: EmbeddingGateway? = defaultEmbeddingGateway,
    isOnline: Boolean = true,
    useCache: Boolean = true,
  ): SemanticSearchResponse {
    currentCoroutineContext().ensureActive()
    val scope = AstrynKnowledgeBaseEngine.normalizeUserScope(userUid)
    val cleanQuery = query.trim()
    val currentEpoch = userIndexEpoch[scope] ?: 0L

    val cacheKey = buildString {
      append(cleanQuery.lowercase(Locale.ROOT))
      append("|k=").append(topK)
      append("|docs=").append(filter.documentIds.sorted().joinToString(","))
      append("|fmts=").append(filter.fileFormats.map { it.name }.sorted().joinToString(","))
      append("|topic=").append(filter.topicFilter.orEmpty())
      append("|min=").append(filter.minImportTimestampMs ?: "")
      append("|max=").append(filter.maxImportTimestampMs ?: "")
      append("|online=").append(isOnline)
      append("|gw=").append(embeddingGateway?.embeddingVersion ?: "none")
    }

    if (useCache) {
      val cachedPair = userQueryCache[scope]?.get(cacheKey)
      if (cachedPair != null && cachedPair.first == currentEpoch) {
        return cachedPair.second.copy(fromCache = true)
      }
    }

    // Retrieve strictly owned chunks for this user and apply metadata filters
    val allUserChunks = AstrynKnowledgeBaseEngine.getAllUserChunks(scope)
      .filter { it.userUid == scope }
      .filter { chunk ->
        (filter.documentIds.isEmpty() || chunk.documentId in filter.documentIds) &&
          (filter.fileFormats.isEmpty() || chunk.fileFormat in filter.fileFormats) &&
          (filter.minImportTimestampMs == null || chunk.importTimestampMs >= filter.minImportTimestampMs) &&
          (filter.maxImportTimestampMs == null || chunk.importTimestampMs <= filter.maxImportTimestampMs) &&
          (filter.topicFilter.isNullOrBlank() ||
            chunk.content.contains(filter.topicFilter, ignoreCase = true) ||
            (chunk.sectionTitle?.contains(filter.topicFilter, ignoreCase = true) == true) ||
            chunk.topicTags.any { it.contains(filter.topicFilter.lowercase(Locale.ROOT)) })
      }

    if (allUserChunks.isEmpty() || cleanQuery.isBlank()) {
      val emptyResp = SemanticSearchResponse(
        query = cleanQuery,
        searchMode = SearchMode.LEXICAL_BM25_FALLBACK,
        embeddingProviderModel = null,
        fallbackReason = if (allUserChunks.isEmpty()) "No matching documents indexed for user." else "Empty query.",
        fromCache = false,
        totalCandidateChunks = 0,
        matches = emptyList(),
      )
      return emptyResp
    }

    // Tokenize & compute genuine BM25 scores across candidate chunks
    val queryTokens = tokenizeForSearch(cleanQuery, expandBilingual = true)
    val bm25Scores = computeBm25Scores(cleanQuery, queryTokens, allUserChunks)

    // Attempt genuine semantic query embedding if gateway + vectors + online available
    val userVectors = userVectorStore[scope].orEmpty()
    val chunksWithVectors = allUserChunks.filter { ch ->
      val vecRec = userVectors[ch.chunkId]
      vecRec != null && vecRec.userUid == scope && vecRec.vector.isNotEmpty()
    }

    var queryVector: FloatArray? = null
    var searchMode = SearchMode.LEXICAL_BM25_FALLBACK
    var fallbackReason: String? = null
    var activeModel: String? = null

    if (embeddingGateway != null && isOnline && chunksWithVectors.isNotEmpty()) {
      val validRes = VerifiedEmbeddingModelRegistry.validateEmbeddingModel(embeddingGateway.modelId)
      if (validRes.isFailure) {
        fallbackReason = validRes.exceptionOrNull()?.message
      } else if (!embeddingGateway.isAvailable()) {
        fallbackReason = "Embedding API credentials unavailable; using BM25 lexical fallback."
      } else {
        try {
          val qEmbRes = embeddingGateway.embedText(cleanQuery, EmbeddingTaskType.RETRIEVAL_QUERY)
          if (qEmbRes.isSuccess) {
            queryVector = qEmbRes.getOrThrow()
            activeModel = embeddingGateway.modelId
            searchMode = if (chunksWithVectors.size == allUserChunks.size) {
              SearchMode.SEMANTIC_EMBEDDING
            } else {
              SearchMode.HYBRID_SEMANTIC_PLUS_LEXICAL
            }
          } else {
            fallbackReason = "Embedding API failed (${qEmbRes.exceptionOrNull()?.message ?: "error"}); using BM25 lexical fallback."
          }
        } catch (ce: CancellationException) {
          throw ce
        } catch (t: Throwable) {
          fallbackReason = "Embedding call failed (${t.message}); using BM25 lexical fallback."
        }
      }
    } else if (!isOnline) {
      fallbackReason = "Offline mode active; using local BM25 lexical search."
    } else if (embeddingGateway == null || chunksWithVectors.isEmpty()) {
      fallbackReason = "Embeddings unavailable for indexed chunks; using BM25 lexical search."
    }

    val rawMatches = allUserChunks.mapNotNull { chunk ->
      val lexical = bm25Scores[chunk.chunkId] ?: 0.0
      val vecRec = userVectors[chunk.chunkId]
      val cosine: Double? = if (queryVector != null && vecRec != null && vecRec.vector.size == queryVector.size) {
        cosineSimilarity(queryVector, vecRec.vector).coerceIn(-1.0, 1.0)
      } else {
        null
      }

      val combinedScore = if (cosine != null) {
        val normCos = ((cosine + 1.0) / 2.0).coerceIn(0.0, 1.0)
        val normLex = (lexical / (lexical + 2.0)).coerceIn(0.0, 1.0)
        (normCos * 0.72) + (normLex * 0.28)
      } else {
        lexical
      }

      val threshold = if (cosine != null) 0.38 else 0.12
      if (combinedScore < threshold && lexical <= 0.05) {
        return@mapNotNull null
      }

      val matchMode = if (cosine != null) searchMode else SearchMode.LEXICAL_BM25_FALLBACK
      val explain = buildString {
        if (cosine != null) {
          append("Semantic cosine=${"%.3f".format(Locale.US, cosine)}")
        } else {
          append("Lexical BM25=${"%.3f".format(Locale.US, lexical)}")
        }
        if (chunk.sectionTitle != null) append(" | Section='${chunk.sectionTitle}'")
        if (chunk.pageNumber != null) append(" | Page=${chunk.pageNumber}")
      }

      RetrievedChunkMatch(
        chunk = chunk,
        score = combinedScore,
        semanticScore = cosine,
        lexicalBm25Score = lexical,
        searchMode = matchMode,
        matchExplanation = explain,
      )
    }

    // Deduplicate identical chunk content hashes keeping highest score
    val seenHashes = HashSet<String>()
    val ranked = rawMatches
      .sortedByDescending { it.score }
      .filter { seenHashes.add(it.chunk.contentHash) }
      .take(topK.coerceIn(1, 20))

    val response = SemanticSearchResponse(
      query = cleanQuery,
      searchMode = searchMode,
      embeddingProviderModel = activeModel,
      fallbackReason = fallbackReason,
      fromCache = false,
      totalCandidateChunks = allUserChunks.size,
      matches = ranked,
    )

    if (useCache) {
      val cacheBucket = userQueryCache.getOrPut(scope) { ConcurrentHashMap() }
      if (cacheBucket.size > 64) cacheBucket.clear()
      cacheBucket[cacheKey] = currentEpoch to response
    }

    return response
  }

  /**
   * Synchronous local BM25 + metadata search for non-suspend fallback and directive paths.
   */
  fun searchLexicalSync(
    userUid: String?,
    query: String,
    topK: Int = 5,
    filter: SearchFilters = SearchFilters(),
  ): SemanticSearchResponse {
    val scope = AstrynKnowledgeBaseEngine.normalizeUserScope(userUid)
    val cleanQuery = query.trim()
    val candidateChunks = AstrynKnowledgeBaseEngine.getAllUserChunks(scope)
      .filter { it.userUid == scope }
      .filter { chunk ->
        (filter.documentIds.isEmpty() || chunk.documentId in filter.documentIds) &&
          (filter.fileFormats.isEmpty() || chunk.fileFormat in filter.fileFormats) &&
          (filter.minImportTimestampMs == null || chunk.importTimestampMs >= filter.minImportTimestampMs) &&
          (filter.maxImportTimestampMs == null || chunk.importTimestampMs <= filter.maxImportTimestampMs) &&
          (filter.topicFilter.isNullOrBlank() ||
            chunk.content.contains(filter.topicFilter, ignoreCase = true) ||
            (chunk.sectionTitle?.contains(filter.topicFilter, ignoreCase = true) == true) ||
            chunk.topicTags.any { it.contains(filter.topicFilter.lowercase(Locale.ROOT)) })
      }
    if (candidateChunks.isEmpty() || cleanQuery.isBlank()) {
      return SemanticSearchResponse(
        query = cleanQuery,
        searchMode = SearchMode.LEXICAL_BM25_FALLBACK,
        embeddingProviderModel = null,
        fallbackReason = "No matching chunks indexed locally.",
        fromCache = false,
        totalCandidateChunks = 0,
        matches = emptyList(),
      )
    }

    val queryTokens = tokenizeForSearch(cleanQuery, expandBilingual = true)
    val bm25Scores = computeBm25Scores(cleanQuery, queryTokens, candidateChunks)
    val seenHashes = HashSet<String>()
    val ranked = candidateChunks.mapNotNull { chunk ->
      val lexical = bm25Scores[chunk.chunkId] ?: 0.0
      if (lexical <= 0.12) return@mapNotNull null
      RetrievedChunkMatch(
        chunk = chunk,
        score = lexical,
        semanticScore = null,
        lexicalBm25Score = lexical,
        searchMode = SearchMode.LEXICAL_BM25_FALLBACK,
        matchExplanation = "Lexical BM25=${"%.3f".format(Locale.US, lexical)}",
      )
    }.sortedByDescending { it.score }
      .filter { seenHashes.add(it.chunk.contentHash) }
      .take(topK.coerceIn(1, 20))

    return SemanticSearchResponse(
      query = cleanQuery,
      searchMode = SearchMode.LEXICAL_BM25_FALLBACK,
      embeddingProviderModel = null,
      fallbackReason = "Local synchronous BM25 lexical lookup.",
      fromCache = false,
      totalCandidateChunks = candidateChunks.size,
      matches = ranked,
    )
  }

  internal fun cosineSimilarity(a: FloatArray, b: FloatArray): Double {
    if (a.isEmpty() || a.size != b.size) return 0.0
    var dot = 0.0
    var normA = 0.0
    var normB = 0.0
    for (i in a.indices) {
      val av = a[i].toDouble()
      val bv = b[i].toDouble()
      dot += av * bv
      normA += av * av
      normB += bv * bv
    }
    val denom = sqrt(normA) * sqrt(normB)
    return if (denom <= 1e-12) 0.0 else (dot / denom)
  }

  internal fun tokenizeForSearch(text: String, expandBilingual: Boolean = false): List<String> {
    val normalized = AstrynIntelligenceEngine.normalizeTyposAndInformalHinglish(text)
    val stopWords = setOf(
      "the", "and", "for", "that", "with", "this", "from", "are", "was", "were", "have", "has",
      "what", "when", "where", "which", "who", "how", "why", "is", "in", "on", "of", "to", "a", "an",
      "kya", "hai", "hain", "ko", "ka", "ki", "ke", "mein", "par", "se", "aur", "batao", "karo", "do",
      "क्या", "है", "हैं", "को", "का", "की", "के", "में", "पर", "से", "और", "बताओ", "करो", "दो",
      "meri", "mere", "sabhi", "uploaded", "document", "documents", "file", "files", "pdf",
    )
    val baseTokens = normalized
      .lowercase(Locale.ROOT)
      .split(Regex("[^a-z0-9\\u0900-\\u097F]+"))
      .filter { it.length >= 2 && it !in stopWords }

    if (!expandBilingual) return baseTokens
    val expanded = LinkedHashSet<String>(baseTokens)
    for (tok in baseTokens) {
      BILINGUAL_EQUIVALENTS[tok]?.let { expanded.addAll(it) }
    }
    return expanded.toList()
  }

  private fun computeBm25Scores(
    rawQuery: String,
    queryTokens: List<String>,
    chunks: List<DocumentChunk>,
  ): Map<String, Double> {
    if (chunks.isEmpty() || queryTokens.isEmpty()) return emptyMap()
    val k1 = 1.5
    val b = 0.75
    val docCount = chunks.size.toDouble()

    val chunkTokensMap = HashMap<String, List<String>>(chunks.size)
    var totalLen = 0.0
    val docFreq = HashMap<String, Int>()

    for (chunk in chunks) {
      val tokens = tokenizeForSearch(
        "${chunk.originalFilename} ${chunk.sectionTitle.orEmpty()} ${chunk.sheetName.orEmpty()} ${chunk.content}",
        expandBilingual = false,
      )
      chunkTokensMap[chunk.chunkId] = tokens
      totalLen += tokens.size
      for (uniq in tokens.toSet()) {
        docFreq[uniq] = (docFreq[uniq] ?: 0) + 1
      }
    }
    val avgDocLen = max(1.0, totalLen / docCount)
    val cleanLowerPhrase = rawQuery.trim().lowercase(Locale.ROOT)
      .replace(Regex("[^a-z0-9\\u0900-\\u097F\\s]+"), " ")
      .replace(Regex("\\s+"), " ")
      .trim()

    val scores = HashMap<String, Double>(chunks.size)
    for (chunk in chunks) {
      val docTokens = chunkTokensMap[chunk.chunkId].orEmpty()
      if (docTokens.isEmpty()) {
        scores[chunk.chunkId] = 0.0
        continue
      }
      val tfMap = docTokens.groupingBy { it }.eachCount()
      val docLen = docTokens.size.toDouble()
      var bm25 = 0.0

      for (qTok in queryTokens.distinct()) {
        val tf = (tfMap[qTok] ?: 0).toDouble()
        if (tf <= 0.0) continue
        val df = (docFreq[qTok] ?: 0).toDouble()
        val idf = ln(1.0 + ((docCount - df + 0.5) / (df + 0.5)))
        val tfNorm = (tf * (k1 + 1.0)) / (tf + k1 * (1.0 - b + b * (docLen / avgDocLen)))
        bm25 += idf * tfNorm
      }

      // Boost exact phrase match & section/filename metadata match
      val contentLower = chunk.content.lowercase(Locale.ROOT)
      if (cleanLowerPhrase.length >= 6 && contentLower.contains(cleanLowerPhrase)) {
        bm25 += 2.5
      }
      val metaCombined = "${chunk.originalFilename} ${chunk.sectionTitle.orEmpty()} ${chunk.sheetName.orEmpty()}".lowercase(Locale.ROOT)
      val metaHits = queryTokens.count { metaCombined.contains(it) }
      if (metaHits > 0) {
        bm25 += metaHits * 0.45
      }

      scores[chunk.chunkId] = bm25
    }
    return scores
  }

  fun clearAllForTesting() {
    userVectorStore.clear()
    userIndexEpoch.clear()
    userQueryCache.clear()
  }
}
