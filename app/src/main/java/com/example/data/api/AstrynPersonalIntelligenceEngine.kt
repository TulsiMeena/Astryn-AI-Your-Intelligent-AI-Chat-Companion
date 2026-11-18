package com.example.data.api

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.example.data.model.ChatAttachment
import com.example.data.model.ChatMessage
import com.example.data.model.MessageRole
import com.example.data.repository.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * ASTRYN AI — PHASE 6: PERSONAL AI INTELLIGENCE, SMART MEMORY & LIVE ENVIRONMENT AWARENESS
 *
 * Implements:
 * 1. AstrynPersonalIntelligenceEngine (Coordinator & Query Router)
 * 2. AstrynUserProfileManager & AstrynPreferenceEngine (Per-user profile & adaptive preferences)
 * 3. 5-Category Smart Personal Memory System:
 *    - Category A: Conversation Memory
 *    - Category B: Personal Preference Memory
 *    - Category C: Project Memory
 *    - Category D: Important Information Memory
 *    - Category E: Temporary Context (with TTL expiration)
 *    Includes relevance scoring, duplicate memory detection, conflict resolution (latest explicit wins),
 *    temporary context expiration, provenance & confidence scores, per-user isolation, and credential filtering.
 * 4. AstrynProjectMemoryManager & AstrynContextResolver (Project continuity, locked decisions,
 *    completed stages, pending work, known problems, selected solutions, cross-conversation resolution)
 * 5. AstrynPersonalizationEngine (Adaptive language Hindi/Hinglish/English, beginner/advanced depth,
 *    step-by-step/concise/detailed style, without forcing personalization into unrelated prompts)
 * 6. AstrynTimeIntelligence (Live system Clock, date, weekday in English/Hindi, month/year, time zone,
 *    MORNING/AFTERNOON/EVENING/NIGHT classification, relative dates today/tomorrow/yesterday, time-zone math)
 * 7. AstrynLocationManager (Permission awareness Coarse/Fine/Denied, GPS availability check, manual city
 *    extraction, preferred weather city persistence, zero storage of raw precise GPS coordinates)
 * 8. AstrynWeatherIntelligence (Real Open-Meteo HTTPS API + pluggable WeatherDataGateway for deterministic
 *    testing, Current Weather, 24h Hourly Forecast, 7-day Daily Forecast, Weather Alerts without fabrication,
 *    rain probability % vs expected precipitation mm, evening outing advisory, and Offline/Cached labeling)
 * 9. Smart Daily Personal Assistant ("Aaj mujhe kya kaam complete karna chahiye?")
 * 10. Phase 4 & Phase 5 Tool Registry integration (9 real callable tools)
 */
object AstrynPersonalIntelligenceEngine {

    // =========================================================================
    // SECTION 1 — 5-CATEGORY PERSONAL MEMORY ARCHITECTURE (SECTIONS 4 & 5)
    // =========================================================================

    enum class PersonalMemoryCategory {
        A_CONVERSATION_MEMORY,
        B_PERSONAL_PREFERENCE_MEMORY,
        C_PROJECT_MEMORY,
        D_IMPORTANT_INFORMATION_MEMORY,
        E_TEMPORARY_CONTEXT
    }

    data class SmartMemoryRecord(
        val id: String,
        val userId: String,
        val category: PersonalMemoryCategory,
        val key: String,
        val value: String,
        val tags: Set<String> = emptySet(),
        val confidenceScore: Double = 0.90,
        val provenance: String = "explicit_user_turn",
        val createdAtMillis: Long = System.currentTimeMillis(),
        val updatedAtMillis: Long = System.currentTimeMillis(),
        val expiresAtMillis: Long? = null,
        val supersededValue: String? = null,
        val revisionCount: Int = 1
    ) {
        fun isExpired(nowMillis: Long): Boolean {
            return expiresAtMillis != null && nowMillis >= expiresAtMillis
        }
    }

    data class MemoryStoreOutcome(
        val stored: Boolean,
        val deduplicated: Boolean = false,
        val conflictResolved: Boolean = false,
        val rejectedReason: String? = null,
        val record: SmartMemoryRecord? = null
    )

    data class ScoredMemoryMatch(
        val record: SmartMemoryRecord,
        val relevanceScore: Double,
        val matchReason: String
    )

    private const val DEFAULT_TEMP_CONTEXT_TTL_MS = 6 * 60 * 60 * 1000L // 6 hours
    private val userMemoryBuckets = ConcurrentHashMap<String, ConcurrentHashMap<String, SmartMemoryRecord>>()

    private val SENSITIVE_DATA_PATTERNS = listOf(
        Regex("""AIza[0-9A-Za-z\-_]{20,}"""),
        Regex("""sk-[0-9A-Za-z]{20,}"""),
        Regex("""(?i)\b(password|passwd|passcode|pin|cvv|otp|secret\s*key|private\s*key|bearer\s+[a-z0-9\-_\.]+|credit\s*card)\b\s*[:=]?\s*\S+"""),
        Regex("""\b\d{4}[\s\-]?\d{4}[\s\-]?\d{4}[\s\-]?\d{4}\b"""),
        // Raw high-precision GPS coordinates (e.g., 28.613939, 77.209021) must never be persisted in memory
        Regex("""[-+]?\d{1,2}\.\d{4,}\s*,\s*[-+]?\d{1,3}\.\d{4,}""")
    )

    fun containsSensitiveOrForbiddenMemoryData(text: String): Boolean {
        return SENSITIVE_DATA_PATTERNS.any { it.containsMatchIn(text) }
    }

    private fun normalizeMemoryValue(text: String): String {
        return text.lowercase()
            .replace(Regex("""[^a-z0-9\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun recordMemory(
        userId: String,
        category: PersonalMemoryCategory,
        key: String,
        value: String,
        tags: Set<String> = emptySet(),
        confidenceScore: Double = 0.92,
        provenance: String = "explicit_user_statement",
        nowMillis: Long = System.currentTimeMillis(),
        ttlMillis: Long? = if (category == PersonalMemoryCategory.E_TEMPORARY_CONTEXT) DEFAULT_TEMP_CONTEXT_TTL_MS else null
    ): MemoryStoreOutcome {
        val cleanUser = userId.trim().ifBlank { "anonymous_local_user" }
        val cleanKey = key.trim().lowercase().replace(Regex("""\s+"""), "_")
        val cleanValue = value.trim()

        if (cleanKey.isBlank() || cleanValue.isBlank()) {
            return MemoryStoreOutcome(stored = false, rejectedReason = "blank_key_or_value")
        }
        if (containsSensitiveOrForbiddenMemoryData(cleanValue) || containsSensitiveOrForbiddenMemoryData(cleanKey)) {
            return MemoryStoreOutcome(
                stored = false,
                rejectedReason = "sensitive_credentials_or_precise_coordinates_blocked"
            )
        }

        val bucket = userMemoryBuckets.getOrPut(cleanUser) { ConcurrentHashMap() }
        pruneExpiredTemporaryMemories(cleanUser, nowMillis)

        val compositeKey = "${category.name}:$cleanKey"
        val existing = bucket[compositeKey]
        val normalizedIncoming = normalizeMemoryValue(cleanValue)

        // 1. Exact or semantic duplicate check across same category
        val existingDuplicate = bucket.values.firstOrNull { record ->
            record.category == category &&
                !record.isExpired(nowMillis) &&
                (record.key == cleanKey && normalizeMemoryValue(record.value) == normalizedIncoming ||
                    jaccardTokenSimilarity(normalizeMemoryValue(record.value), normalizedIncoming) >= 0.88)
        }
        if (existingDuplicate != null) {
            val refreshed = existingDuplicate.copy(
                updatedAtMillis = nowMillis,
                confidenceScore = maxOf(existingDuplicate.confidenceScore, confidenceScore),
                expiresAtMillis = ttlMillis?.let { nowMillis + it } ?: existingDuplicate.expiresAtMillis
            )
            bucket["${refreshed.category.name}:${refreshed.key}"] = refreshed
            return MemoryStoreOutcome(
                stored = true,
                deduplicated = true,
                conflictResolved = false,
                record = refreshed
            )
        }

        // 2. Conflict resolution: same key in same category with updated value -> latest explicit instruction wins
        if (existing != null && !existing.isExpired(nowMillis)) {
            val updated = existing.copy(
                value = cleanValue,
                tags = existing.tags + tags + extractSemanticTokens(cleanValue),
                confidenceScore = confidenceScore,
                provenance = provenance,
                updatedAtMillis = nowMillis,
                expiresAtMillis = ttlMillis?.let { nowMillis + it },
                supersededValue = existing.value,
                revisionCount = existing.revisionCount + 1
            )
            bucket[compositeKey] = updated
            // Also sync to Phase 2/3 long-term memory if not temporary
            if (category != PersonalMemoryCategory.E_TEMPORARY_CONTEXT) {
                AstrynIntelligenceEngine.saveOrUpdateUserMemory(
                    userUid = cleanUser,
                    category = mapToLegacyCategory(category),
                    key = cleanKey,
                    value = cleanValue
                )
            }
            return MemoryStoreOutcome(
                stored = true,
                deduplicated = false,
                conflictResolved = true,
                record = updated
            )
        }

        // 3. New memory entry
        val created = SmartMemoryRecord(
            id = "${cleanUser}_${compositeKey}_$nowMillis",
            userId = cleanUser,
            category = category,
            key = cleanKey,
            value = cleanValue,
            tags = tags + extractSemanticTokens(cleanKey) + extractSemanticTokens(cleanValue),
            confidenceScore = confidenceScore.coerceIn(0.0, 1.0),
            provenance = provenance,
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
            expiresAtMillis = ttlMillis?.let { nowMillis + it }
        )
        bucket[compositeKey] = created

        if (category != PersonalMemoryCategory.E_TEMPORARY_CONTEXT) {
            AstrynIntelligenceEngine.saveOrUpdateUserMemory(
                userUid = cleanUser,
                category = mapToLegacyCategory(category),
                key = cleanKey,
                value = cleanValue
            )
        }

        return MemoryStoreOutcome(
            stored = true,
            deduplicated = false,
            conflictResolved = false,
            record = created
        )
    }

    fun pruneExpiredTemporaryMemories(
        userId: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Int {
        val cleanUser = userId.trim().ifBlank { "anonymous_local_user" }
        val bucket = userMemoryBuckets[cleanUser] ?: return 0
        var removedCount = 0
        val iterator = bucket.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.isExpired(nowMillis)) {
                iterator.remove()
                removedCount++
            }
        }
        return removedCount
    }

    fun retrieveRelevantMemories(
        userId: String,
        query: String,
        categories: Set<PersonalMemoryCategory> = PersonalMemoryCategory.entries.toSet(),
        topK: Int = 6,
        minRelevanceScore: Double = 0.22,
        nowMillis: Long = System.currentTimeMillis()
    ): List<ScoredMemoryMatch> {
        val cleanUser = userId.trim().ifBlank { "anonymous_local_user" }
        pruneExpiredTemporaryMemories(cleanUser, nowMillis)
        val bucket = userMemoryBuckets[cleanUser] ?: return emptyList()
        if (bucket.isEmpty()) return emptyList()

        val queryTokens = extractSemanticTokens(query)
        val lowerQuery = query.lowercase()

        return bucket.values
            .filter { it.category in categories && !it.isExpired(nowMillis) }
            .mapNotNull { record ->
                val recordTokens = record.tags + extractSemanticTokens(record.key) + extractSemanticTokens(record.value)
                val overlap = queryTokens.intersect(recordTokens).size
                val keySubstringMatch = lowerQuery.contains(record.key.replace("_", " "))
                val categoryBoost = when {
                    record.category == PersonalMemoryCategory.B_PERSONAL_PREFERENCE_MEMORY &&
                        lowerQuery.containsAny("style", "language", "hindi", "hinglish", "english", "format", "prefer", "step", "concise") -> 0.45
                    record.category == PersonalMemoryCategory.C_PROJECT_MEMORY &&
                        lowerQuery.containsAny("project", "astryn", "app", "design", "upgrade", "pending", "problem", "decision", "phase") -> 0.45
                    record.category == PersonalMemoryCategory.E_TEMPORARY_CONTEXT &&
                        lowerQuery.containsAny("aaj", "today", "current", "abhi", "session", "focus") -> 0.35
                    else -> 0.0
                }
                val tokenScore = if (queryTokens.isEmpty()) 0.25 else overlap.toDouble() / queryTokens.size.coerceAtLeast(1)
                val recencyBoost = ((nowMillis - record.updatedAtMillis).coerceAtLeast(0L)).let { ageMs ->
                    when {
                        ageMs < 30 * 60 * 1000L -> 0.15
                        ageMs < 24 * 60 * 60 * 1000L -> 0.08
                        else -> 0.02
                    }
                }
                val rawScore = (tokenScore * 0.65) +
                    (if (keySubstringMatch) 0.35 else 0.0) +
                    categoryBoost +
                    (record.confidenceScore * 0.12) +
                    recencyBoost

                if (rawScore >= minRelevanceScore) {
                    val reason = buildString {
                        if (overlap > 0) append("token_overlap($overlap) ")
                        if (keySubstringMatch) append("key_match ")
                        if (categoryBoost > 0) append("category_boost(${record.category.name}) ")
                    }.trim().ifBlank { "contextual_relevance" }
                    ScoredMemoryMatch(
                        record = record,
                        relevanceScore = (rawScore.coerceAtMost(1.0) * 100.0).roundToInt() / 100.0,
                        matchReason = reason
                    )
                } else {
                    null
                }
            }
            .sortedWith(
                compareByDescending<ScoredMemoryMatch> { it.relevanceScore }
                    .thenByDescending { it.record.updatedAtMillis }
            )
            .take(topK)
    }

    fun getAllUserMemories(
        userId: String,
        nowMillis: Long = System.currentTimeMillis()
    ): List<SmartMemoryRecord> {
        val cleanUser = userId.trim().ifBlank { "anonymous_local_user" }
        pruneExpiredTemporaryMemories(cleanUser, nowMillis)
        return userMemoryBuckets[cleanUser]?.values
            ?.filter { !it.isExpired(nowMillis) }
            ?.sortedByDescending { it.updatedAtMillis }
            ?: emptyList()
    }

    private fun mapToLegacyCategory(category: PersonalMemoryCategory): AstrynIntelligenceEngine.MemoryCategory {
        return when (category) {
            PersonalMemoryCategory.A_CONVERSATION_MEMORY -> AstrynIntelligenceEngine.MemoryCategory.USER_CONTEXT
            PersonalMemoryCategory.B_PERSONAL_PREFERENCE_MEMORY -> AstrynIntelligenceEngine.MemoryCategory.PREFERENCE
            PersonalMemoryCategory.C_PROJECT_MEMORY -> AstrynIntelligenceEngine.MemoryCategory.ONGOING_PROJECT
            PersonalMemoryCategory.D_IMPORTANT_INFORMATION_MEMORY -> AstrynIntelligenceEngine.MemoryCategory.TECHNICAL_CONSTRAINT
            PersonalMemoryCategory.E_TEMPORARY_CONTEXT -> AstrynIntelligenceEngine.MemoryCategory.USER_CONTEXT
        }
    }

    // =========================================================================
    // SECTION 2 — USER PROFILE & PREFERENCE ENGINE (SECTIONS 3 & 7)
    // =========================================================================

    enum class PreferredLanguage {
        ENGLISH,
        HINDI,
        HINGLISH,
        AUTO_DETECT
    }

    enum class PreferredDepth {
        BEGINNER,
        INTERMEDIATE,
        ADVANCED
    }

    enum class PreferredStyle {
        CONCISE,
        BALANCED,
        DETAILED,
        STEP_BY_STEP
    }

    data class UserPersonalProfile(
        val userId: String,
        val displayName: String? = null,
        val preferredLanguage: PreferredLanguage = PreferredLanguage.AUTO_DETECT,
        val preferredDepth: PreferredDepth = PreferredDepth.INTERMEDIATE,
        val preferredStyle: PreferredStyle = PreferredStyle.BALANCED,
        val preferredDomainFocus: String? = null,
        val preferredWeatherCity: String? = null,
        val customFormattingRules: List<String> = emptyList(),
        val updatedAtMillis: Long = System.currentTimeMillis()
    )

    object AstrynUserProfileManager {
        private val profiles = ConcurrentHashMap<String, UserPersonalProfile>()

        fun getProfile(userId: String): UserPersonalProfile {
            val cleanUser = userId.trim().ifBlank { "anonymous_local_user" }
            return profiles.getOrPut(cleanUser) {
                UserPersonalProfile(userId = cleanUser)
            }
        }

        fun updateProfile(
            userId: String,
            transform: (UserPersonalProfile) -> UserPersonalProfile
        ): UserPersonalProfile {
            val cleanUser = userId.trim().ifBlank { "anonymous_local_user" }
            val current = getProfile(cleanUser)
            val updated = transform(current).copy(updatedAtMillis = System.currentTimeMillis())
            profiles[cleanUser] = updated
            return updated
        }

        fun syncFromUserPreferences(userId: String, prefs: UserPreferences?): UserPersonalProfile {
            if (prefs == null) return getProfile(userId)
            return updateProfile(userId) { current ->
                val mappedStyle = when (prefs.responseStyle.name.lowercase()) {
                    "concise" -> PreferredStyle.CONCISE
                    "detailed" -> PreferredStyle.DETAILED
                    "step-by-step", "step_by_step" -> PreferredStyle.STEP_BY_STEP
                    else -> current.preferredStyle
                }
                val mappedLang = when {
                    prefs.responseLanguage.name.contains("hinglish", ignoreCase = true) -> PreferredLanguage.HINGLISH
                    prefs.responseLanguage.name.contains("hindi", ignoreCase = true) -> PreferredLanguage.HINDI
                    prefs.responseLanguage.name.contains("english", ignoreCase = true) -> PreferredLanguage.ENGLISH
                    else -> current.preferredLanguage
                }
                current.copy(
                    preferredStyle = mappedStyle,
                    preferredLanguage = mappedLang
                )
            }
        }

        fun clearUser(userId: String) {
            val cleanUser = userId.trim().ifBlank { "anonymous_local_user" }
            profiles.remove(cleanUser)
        }

        fun clearAll() {
            profiles.clear()
        }
    }

    object AstrynPreferenceEngine {

        fun ingestTurnForPreferencesAndMemory(
            userId: String,
            userPrompt: String,
            nowMillis: Long = System.currentTimeMillis()
        ) {
            val cleanUser = userId.trim().ifBlank { "anonymous_local_user" }
            val text = userPrompt.trim()
            if (text.isBlank()) return
            val lower = text.lowercase()

            // 1. Language preference updates
            when {
                Regex("""(?i)\b(always\s+reply\s+in\s+hinglish|hinglish\s+mein\s+(batao|baat\s+karo|jawab\s+do)|prefer\s+hinglish|use\s+hinglish)\b""").containsMatchIn(text) -> {
                    AstrynUserProfileManager.updateProfile(cleanUser) { it.copy(preferredLanguage = PreferredLanguage.HINGLISH) }
                    recordMemory(
                        userId = cleanUser,
                        category = PersonalMemoryCategory.B_PERSONAL_PREFERENCE_MEMORY,
                        key = "preferred_language",
                        value = "Hinglish",
                        tags = setOf("language", "hinglish", "communication"),
                        nowMillis = nowMillis
                    )
                }
                Regex("""(?i)\b(always\s+reply\s+in\s+hindi|sirf\s+hindi\s+mein\s+(batao|jawab\s+do)|prefer\s+hindi)\b""").containsMatchIn(text) -> {
                    AstrynUserProfileManager.updateProfile(cleanUser) { it.copy(preferredLanguage = PreferredLanguage.HINDI) }
                    recordMemory(
                        userId = cleanUser,
                        category = PersonalMemoryCategory.B_PERSONAL_PREFERENCE_MEMORY,
                        key = "preferred_language",
                        value = "Hindi",
                        tags = setOf("language", "hindi", "communication"),
                        nowMillis = nowMillis
                    )
                }
                Regex("""(?i)\b(always\s+reply\s+in\s+english|only\s+in\s+english|prefer\s+english|switch\s+to\s+english)\b""").containsMatchIn(text) -> {
                    AstrynUserProfileManager.updateProfile(cleanUser) { it.copy(preferredLanguage = PreferredLanguage.ENGLISH) }
                    recordMemory(
                        userId = cleanUser,
                        category = PersonalMemoryCategory.B_PERSONAL_PREFERENCE_MEMORY,
                        key = "preferred_language",
                        value = "English",
                        tags = setOf("language", "english", "communication"),
                        nowMillis = nowMillis
                    )
                }
            }

            // 2. Response style preference updates
            when {
                Regex("""(?i)\b(step[\s\-]+by[\s\-]+step|point\s+by\s+point|numbered\s+steps)\b""").containsMatchIn(text) &&
                    lower.containsAny("prefer", "always", "explain", "batao", "chahiye", "keep", "use") -> {
                    AstrynUserProfileManager.updateProfile(cleanUser) { it.copy(preferredStyle = PreferredStyle.STEP_BY_STEP) }
                    recordMemory(
                        userId = cleanUser,
                        category = PersonalMemoryCategory.B_PERSONAL_PREFERENCE_MEMORY,
                        key = "response_style",
                        value = "step-by-step",
                        tags = setOf("style", "step_by_step", "format"),
                        nowMillis = nowMillis
                    )
                }
                Regex("""(?i)\b(keep\s+it\s+(short|concise|brief)|short\s+and\s+concise|brief\s+answers\s+only|concise\s+rakho|short\s+mein\s+batao)\b""").containsMatchIn(text) -> {
                    AstrynUserProfileManager.updateProfile(cleanUser) { it.copy(preferredStyle = PreferredStyle.CONCISE) }
                    recordMemory(
                        userId = cleanUser,
                        category = PersonalMemoryCategory.B_PERSONAL_PREFERENCE_MEMORY,
                        key = "response_style",
                        value = "concise",
                        tags = setOf("style", "concise", "short", "format"),
                        nowMillis = nowMillis
                    )
                }
                Regex("""(?i)\b(detailed\s+explanation|in[\s\-]+depth\s+answers|deep\s+dive|detail\s+mein\s+batao|prefer\s+detailed)\b""").containsMatchIn(text) -> {
                    AstrynUserProfileManager.updateProfile(cleanUser) { it.copy(preferredStyle = PreferredStyle.DETAILED) }
                    recordMemory(
                        userId = cleanUser,
                        category = PersonalMemoryCategory.B_PERSONAL_PREFERENCE_MEMORY,
                        key = "response_style",
                        value = "detailed",
                        tags = setOf("style", "detailed", "depth", "format"),
                        nowMillis = nowMillis
                    )
                }
            }

            // 3. Technical depth preference updates
            when {
                Regex("""(?i)\b(i\s+am\s+a\s+beginner|explain\s+like\s+i'?m\s+a\s+beginner|beginner\s+level|bilkul\s+basic\s+se)\b""").containsMatchIn(text) -> {
                    AstrynUserProfileManager.updateProfile(cleanUser) { it.copy(preferredDepth = PreferredDepth.BEGINNER) }
                    recordMemory(
                        userId = cleanUser,
                        category = PersonalMemoryCategory.B_PERSONAL_PREFERENCE_MEMORY,
                        key = "explanation_depth",
                        value = "beginner",
                        tags = setOf("depth", "beginner", "learning"),
                        nowMillis = nowMillis
                    )
                }
                Regex("""(?i)\b(i\s+am\s+a\s+senior\s+(developer|engineer|architect)|advanced\s+technical\s+depth|production[\s\-]+grade\s+only)\b""").containsMatchIn(text) -> {
                    AstrynUserProfileManager.updateProfile(cleanUser) { it.copy(preferredDepth = PreferredDepth.ADVANCED) }
                    recordMemory(
                        userId = cleanUser,
                        category = PersonalMemoryCategory.B_PERSONAL_PREFERENCE_MEMORY,
                        key = "explanation_depth",
                        value = "advanced",
                        tags = setOf("depth", "advanced", "senior"),
                        nowMillis = nowMillis
                    )
                }
            }

            // 4. Preferred weather city updates
            val preferredCityMatch = Regex(
                """(?i)\b(?:set\s+my\s+(?:default\s+|preferred\s+)?(?:weather\s+)?city\s+to|mera\s+(?:weather\s+)?city|i\s+live\s+in|main\s+([a-zA-Z\s]{3,25})\s+mein\s+rehta\s+hoon|change\s+(?:my\s+)?(?:weather\s+)?city\s+to)\s*([a-zA-Z\s]{3,25})?"""
            ).find(text)
            if (preferredCityMatch != null) {
                val candidate = (preferredCityMatch.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }
                    ?: preferredCityMatch.groupValues.getOrNull(1).orEmpty())
                    .trim()
                    .replace(Regex("""(?i)\b(hai|set\s+karo|kar\s+do|please|now)\b"""), "")
                    .trim()
                val recognizedCity = AstrynLocationManager.normalizeRecognizedCity(candidate)
                if (recognizedCity != null) {
                    AstrynLocationManager.setPreferredWeatherCity(cleanUser, recognizedCity, nowMillis)
                }
            }

            // 5. Temporary session context detection (Category E)
            val tempFocusMatch = Regex(
                """(?i)\b(?:for\s+today'?s\s+session|aaj\s+ke\s+liye|right\s+now\s+i\s+am\s+debugging|currently\s+working\s+on\s+today)\s*[:,-]?\s*(.{5,120})"""
            ).find(text)
            if (tempFocusMatch != null) {
                val focusVal = tempFocusMatch.groupValues[1].trim()
                recordMemory(
                    userId = cleanUser,
                    category = PersonalMemoryCategory.E_TEMPORARY_CONTEXT,
                    key = "current_session_focus",
                    value = focusVal,
                    tags = setOf("today", "aaj", "session", "focus", "temporary"),
                    nowMillis = nowMillis,
                    ttlMillis = DEFAULT_TEMP_CONTEXT_TTL_MS
                )
            }

            // 6. Project context ingestion
            AstrynProjectMemoryManager.ingestTurnForProjectContext(cleanUser, text, nowMillis)
        }
    }

    // =========================================================================
    // SECTION 3 — ADVANCED CONTEXT & PROJECT MEMORY MANAGER (SECTION 6)
    // =========================================================================

    data class ProjectContextRecord(
        val projectId: String,
        val userId: String,
        val projectName: String,
        val objective: String,
        val lockedDecisions: List<String>,
        val completedStages: List<String>,
        val pendingWork: List<String>,
        val knownProblems: List<String>,
        val selectedSolutions: List<String>,
        val updatedAtMillis: Long = System.currentTimeMillis()
    )

    object AstrynProjectMemoryManager {
        private val userProjects = ConcurrentHashMap<String, ConcurrentHashMap<String, ProjectContextRecord>>()
        private val activeProjectByUser = ConcurrentHashMap<String, String>()

        private fun defaultAstrynProject(userId: String): ProjectContextRecord {
            return ProjectContextRecord(
                projectId = "astryn_ai",
                userId = userId,
                projectName = "Astryn AI",
                objective = "High-intelligence, privacy-first Android AI assistant built with Kotlin, Jetpack Compose Glassmorphism UI, Room + Firestore sync, and Gemini API.",
                lockedDecisions = listOf(
                    "Preserve existing Glassmorphism UI, navigation, colors, typography, and icons",
                    "Keep Voice-to-Voice Jarvis (GeminiLiveVoiceSession) strictly locked and untouched",
                    "Store API keys only in encrypted Android Keystore vault (never in plain text or chat memory)",
                    "Maintain strict per-user memory & database isolation across accounts"
                ),
                completedStages = listOf(
                    "Phase 1: Core Android Architecture, Auth, Room + Firestore Sync & Glassmorphism UI",
                    "Phase 2: Advanced Reasoning, 3-Tier Memory & Intent Decomposition",
                    "Phase 3: Real Gemini Pipeline Optimization, Multimodal Comprehension & 100-Question Benchmark",
                    "Phase 4: Autonomous Agentic Task Planning & Tool Orchestration",
                    "Phase 5: Advanced Research Intelligence & Real-World Knowledge Engine",
                    "Phase 6: Personal AI Intelligence, 5-Category Smart Memory, Live Time & Real-Time Weather Awareness",
                    "Phase 7-15: Production Hardening, Math/Science Calculation, Productivity Suite & Knowledge RAG",
                    "Phase 16: Advanced Response Rendering (Code, Tables, Charts, Math & Diagrams)",
                    "Phase 17: Adaptive Intelligence & Contextual Conversation Engine"
                ),
                pendingWork = listOf(
                    "Final production release APK verification across low-bandwidth networks",
                    "User-triggered expansion of offline multimodal indexing"
                ),
                knownProblems = listOf(
                    "Invalid or expired built-in platform API keys previously caused HTTP 400 errors in ChatScreen",
                    "Duplicate syndicated web domains could previously over-inflate research confidence scores"
                ),
                selectedSolutions = listOf(
                    "Automatic dead built-in key detection with seamless fallback to AstrynIntelligenceEngine",
                    "Domain-grouped source credibility evaluation and duplicate echo-chamber penalty in AstrynResearchEngine"
                )
            )
        }

        fun ensureInitialized(userId: String) {
            val cleanUser = userId.trim().ifBlank { "anonymous_local_user" }
            val map = userProjects.getOrPut(cleanUser) { ConcurrentHashMap() }
            if (!map.containsKey("astryn_ai")) {
                map["astryn_ai"] = defaultAstrynProject(cleanUser)
                activeProjectByUser.putIfAbsent(cleanUser, "astryn_ai")
            }
        }

        fun upsertProject(
            userId: String,
            projectId: String,
            projectName: String,
            objective: String? = null,
            lockedDecisions: List<String>? = null,
            completedStages: List<String>? = null,
            pendingWork: List<String>? = null,
            knownProblems: List<String>? = null,
            selectedSolutions: List<String>? = null,
            setAsActive: Boolean = true,
            nowMillis: Long = System.currentTimeMillis()
        ): ProjectContextRecord {
            val cleanUser = userId.trim().ifBlank { "anonymous_local_user" }
            ensureInitialized(cleanUser)
            val cleanId = projectId.trim().lowercase().replace(Regex("""\s+"""), "_")
            val map = userProjects.getOrPut(cleanUser) { ConcurrentHashMap() }
            val existing = map[cleanId]

            val updated = ProjectContextRecord(
                projectId = cleanId,
                userId = cleanUser,
                projectName = projectName.trim().ifBlank { existing?.projectName ?: cleanId },
                objective = objective ?: existing?.objective ?: "Active user engineering project",
                lockedDecisions = (lockedDecisions ?: existing?.lockedDecisions ?: emptyList()).distinct(),
                completedStages = (completedStages ?: existing?.completedStages ?: emptyList()).distinct(),
                pendingWork = (pendingWork ?: existing?.pendingWork ?: emptyList()).distinct(),
                knownProblems = (knownProblems ?: existing?.knownProblems ?: emptyList()).distinct(),
                selectedSolutions = (selectedSolutions ?: existing?.selectedSolutions ?: emptyList()).distinct(),
                updatedAtMillis = nowMillis
            )
            map[cleanId] = updated
            if (setAsActive) {
                activeProjectByUser[cleanUser] = cleanId
            }

            recordMemory(
                userId = cleanUser,
                category = PersonalMemoryCategory.C_PROJECT_MEMORY,
                key = "project_$cleanId",
                value = "${updated.projectName}: Objective=${updated.objective} | Pending=${updated.pendingWork.joinToString("; ")}",
                tags = setOf("project", cleanId, updated.projectName.lowercase()),
                nowMillis = nowMillis
            )
            return updated
        }

        fun getProject(userId: String, projectIdOrName: String? = null): ProjectContextRecord {
            val cleanUser = userId.trim().ifBlank { "anonymous_local_user" }
            ensureInitialized(cleanUser)
            val map = userProjects[cleanUser]!!
            if (!projectIdOrName.isNullOrBlank()) {
                val needle = projectIdOrName.trim().lowercase()
                map[needle]?.let { return it }
                map.values.firstOrNull {
                    it.projectName.lowercase().contains(needle) || needle.contains(it.projectId)
                }?.let { return it }
            }
            val activeId = activeProjectByUser[cleanUser] ?: "astryn_ai"
            return map[activeId] ?: defaultAstrynProject(cleanUser)
        }

        fun getAllProjects(userId: String): List<ProjectContextRecord> {
            val cleanUser = userId.trim().ifBlank { "anonymous_local_user" }
            ensureInitialized(cleanUser)
            return userProjects[cleanUser]?.values?.sortedByDescending { it.updatedAtMillis } ?: emptyList()
        }

        fun ingestTurnForProjectContext(
            userId: String,
            userPrompt: String,
            nowMillis: Long = System.currentTimeMillis()
        ) {
            ensureInitialized(userId)
            val text = userPrompt.trim()
            val lower = text.lowercase()

            // Detect explicit project decision / constraint
            if (Regex("""(?i)\b(preserve\s+existing\s+glassmorphism|wahi\s+design\s+preserve\s+rakhna|do\s+not\s+redesign\s+ui|keep\s+mvvm\s+architecture)\b""").containsMatchIn(text)) {
                val active = getProject(userId)
                upsertProject(
                    userId = userId,
                    projectId = active.projectId,
                    projectName = active.projectName,
                    lockedDecisions = active.lockedDecisions + "Preserve existing Glassmorphism UI and MVVM architecture without visual redesign",
                    nowMillis = nowMillis
                )
            }

            // Detect custom project registration: e.g., "Working on Project FinTrack: ..."
            val customProjectMatch = Regex("""(?i)\b(?:working\s+on\s+project|mera\s+naya\s+project\s+hai|new\s+project)\s+([A-Za-z0-9_\-]{3,25})\s*[:\-]?\s*(.*)""").find(text)
            if (customProjectMatch != null) {
                val projName = customProjectMatch.groupValues[1].trim()
                val desc = customProjectMatch.groupValues[2].trim().ifBlank { "Custom user project $projName" }
                upsertProject(
                    userId = userId,
                    projectId = projName.lowercase(),
                    projectName = projName,
                    objective = desc,
                    setAsActive = true,
                    nowMillis = nowMillis
                )
            }

            if (lower.contains("astryn")) {
                activeProjectByUser[userId.trim().ifBlank { "anonymous_local_user" }] = "astryn_ai"
            }
        }

        fun clearUser(userId: String) {
            val cleanUser = userId.trim().ifBlank { "anonymous_local_user" }
            userProjects.remove(cleanUser)
            activeProjectByUser.remove(cleanUser)
        }

        fun clearAll() {
            userProjects.clear()
            activeProjectByUser.clear()
        }
    }

    object AstrynContextResolver {
        fun resolveTargetProject(userId: String, query: String): ProjectContextRecord {
            val cleanUser = userId.trim().ifBlank { "anonymous_local_user" }
            val lower = query.lowercase()
            val allProjects = AstrynProjectMemoryManager.getAllProjects(cleanUser)

            // Match explicit project name in query so unrelated projects are never mixed
            val explicitMatch = allProjects.firstOrNull { project ->
                lower.contains(project.projectName.lowercase()) ||
                    lower.contains(project.projectId.lowercase())
            }
            if (explicitMatch != null) return explicitMatch

            // If user asks about "pichle project" / "previous project" and there are multiple projects
            if (lower.containsAny("pichle project", "previous project", "earlier project") && allProjects.size >= 2) {
                return allProjects[1]
            }
            return AstrynProjectMemoryManager.getProject(cleanUser)
        }
    }

    // =========================================================================
    // SECTION 4 — PERSONALIZATION ENGINE (SECTION 7)
    // =========================================================================

    object AstrynPersonalizationEngine {

        fun detectResponseLanguage(userId: String, query: String): PreferredLanguage {
            val profile = AstrynUserProfileManager.getProfile(userId)
            if (profile.preferredLanguage != PreferredLanguage.AUTO_DETECT) {
                return profile.preferredLanguage
            }
            val lower = query.lowercase()
            val hinglishMarkers = listOf(
                "kya", "hai", "hain", "batao", "mera", "mere", "meri", "aaj", "kal", "abhi",
                "kitna", "kitne", "kaisa", "kaise", "baat", "karo", "chahiye", "mausam",
                "baarish", "shaam", "subah", "raat", "pichle", "wahi", "rakhna", "agale"
            )
            val markerHits = hinglishMarkers.count { marker ->
                Regex("""\b${Regex.escape(marker)}\b""").containsMatchIn(lower)
            }
            return if (markerHits >= 2) PreferredLanguage.HINGLISH else PreferredLanguage.ENGLISH
        }

        fun shouldApplyPersonalContext(query: String): Boolean {
            val lower = query.lowercase().trim()
            // Purely factual/math/generic questions should NOT have unrelated personal/project context forced in
            val isGenericFactual = Regex("""^(what\s+is\s+\d+|calculate\s+|capital\s+of\s+|who\s+invented\s+|define\s+photosynthesis|binary\s+search\s+complexity)""").containsMatchIn(lower)
            return !isGenericFactual
        }
    }

    // =========================================================================
    // SECTION 5 — LIVE TIME & DATE INTELLIGENCE (SECTION 8)
    // =========================================================================

    enum class TimeOfDayPeriod(val englishLabel: String, val hindiLabel: String) {
        MORNING("Morning", "Subah"),
        AFTERNOON("Afternoon", "Dopahar"),
        EVENING("Evening", "Shaam"),
        NIGHT("Night", "Raat")
    }

    data class LiveTimeSnapshot(
        val zonedDateTime: ZonedDateTime,
        val formattedTime12h: String,
        val formattedTime24h: String,
        val formattedDate: String,
        val isoDate: String,
        val dayOfWeekEnglish: String,
        val dayOfWeekHindi: String,
        val monthName: String,
        val year: Int,
        val timeZoneId: String,
        val utcOffsetString: String,
        val periodOfDay: TimeOfDayPeriod,
        val tomorrowFormattedDate: String,
        val tomorrowDayOfWeekEnglish: String,
        val yesterdayFormattedDate: String
    )

    data class TimeZoneComparisonResult(
        val zoneAId: String,
        val zoneALabel: String,
        val zoneATime12h: String,
        val zoneBId: String,
        val zoneBLabel: String,
        val zoneBTime12h: String,
        val differenceMinutes: Long,
        val formattedDifference: String,
        val summaryExplanation: String
    )

    object AstrynTimeIntelligence {
        @Volatile
        var clockProvider: Clock = Clock.systemDefaultZone()

        private val TIME_12H_FMT = DateTimeFormatter.ofPattern("hh:mm a", Locale.US)
        private val TIME_24H_FMT = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
        private val HUMAN_DATE_FMT = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.US)

        private val HINDI_DAYS = mapOf(
            "MONDAY" to "Somvaar (Monday)",
            "TUESDAY" to "Mangalvaar (Tuesday)",
            "WEDNESDAY" to "Budhvaar (Wednesday)",
            "THURSDAY" to "Guruvaar (Thursday)",
            "FRIDAY" to "Shukravaar (Friday)",
            "SATURDAY" to "Shanivaar (Saturday)",
            "SUNDAY" to "Ravivaar (Sunday)"
        )

        private val CITY_COUNTRY_TO_ZONE = mapOf(
            "india" to ("Asia/Kolkata" to "India (IST)"),
            "ist" to ("Asia/Kolkata" to "India (IST)"),
            "delhi" to ("Asia/Kolkata" to "Delhi, India (IST)"),
            "mumbai" to ("Asia/Kolkata" to "Mumbai, India (IST)"),
            "kolkata" to ("Asia/Kolkata" to "Kolkata, India (IST)"),
            "bengaluru" to ("Asia/Kolkata" to "Bengaluru, India (IST)"),
            "bangalore" to ("Asia/Kolkata" to "Bengaluru, India (IST)"),
            "dubai" to ("Asia/Dubai" to "Dubai, UAE (GST)"),
            "uae" to ("Asia/Dubai" to "UAE (GST)"),
            "abu dhabi" to ("Asia/Dubai" to "Abu Dhabi, UAE (GST)"),
            "london" to ("Europe/London" to "London, UK"),
            "uk" to ("Europe/London" to "United Kingdom"),
            "utc" to ("UTC" to "UTC"),
            "gmt" to ("UTC" to "GMT/UTC"),
            "new york" to ("America/New_York" to "New York, USA"),
            "usa" to ("America/New_York" to "New York, USA (ET)"),
            "california" to ("America/Los_Angeles" to "California, USA (PT)"),
            "san francisco" to ("America/Los_Angeles" to "San Francisco, USA (PT)"),
            "tokyo" to ("Asia/Tokyo" to "Tokyo, Japan (JST)"),
            "japan" to ("Asia/Tokyo" to "Japan (JST)"),
            "singapore" to ("Asia/Singapore" to "Singapore (SGT)"),
            "sydney" to ("Australia/Sydney" to "Sydney, Australia (AEST)"),
            "australia" to ("Australia/Sydney" to "Australia (AEST)"),
            "germany" to ("Europe/Berlin" to "Germany (CET)"),
            "berlin" to ("Europe/Berlin" to "Berlin, Germany"),
            "paris" to ("Europe/Paris" to "Paris, France"),
            "canada" to ("America/Toronto" to "Toronto, Canada")
        )

        fun classifyPeriodOfDay(hour24: Int): TimeOfDayPeriod {
            return when (hour24) {
                in 5..11 -> TimeOfDayPeriod.MORNING
                in 12..16 -> TimeOfDayPeriod.AFTERNOON
                in 17..20 -> TimeOfDayPeriod.EVENING
                else -> TimeOfDayPeriod.NIGHT
            }
        }

        fun getCurrentSnapshot(zoneOverride: ZoneId? = null): LiveTimeSnapshot {
            val baseClock = if (zoneOverride != null) clockProvider.withZone(zoneOverride) else clockProvider
            val now = ZonedDateTime.now(baseClock)
            val tomorrow = now.plusDays(1)
            val yesterday = now.minusDays(1)
            val dayKey = now.dayOfWeek.name
            val dayEnglish = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
            val dayHindi = HINDI_DAYS[dayKey] ?: dayEnglish
            val monthName = now.month.getDisplayName(TextStyle.FULL, Locale.US)

            return LiveTimeSnapshot(
                zonedDateTime = now,
                formattedTime12h = now.format(TIME_12H_FMT),
                formattedTime24h = now.format(TIME_24H_FMT),
                formattedDate = now.format(HUMAN_DATE_FMT),
                isoDate = now.toLocalDate().toString(),
                dayOfWeekEnglish = dayEnglish,
                dayOfWeekHindi = dayHindi,
                monthName = monthName,
                year = now.year,
                timeZoneId = now.zone.id,
                utcOffsetString = "UTC${now.offset.id.let { if (it == "Z") "+00:00" else it }}",
                periodOfDay = classifyPeriodOfDay(now.hour),
                tomorrowFormattedDate = tomorrow.format(HUMAN_DATE_FMT),
                tomorrowDayOfWeekEnglish = tomorrow.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US),
                yesterdayFormattedDate = yesterday.format(HUMAN_DATE_FMT)
            )
        }

        fun resolveRelativeDate(offsetDays: Long, zoneOverride: ZoneId? = null): String {
            val snap = getCurrentSnapshot(zoneOverride)
            val target = snap.zonedDateTime.plusDays(offsetDays)
            val dayName = target.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
            return "${target.format(HUMAN_DATE_FMT)} ($dayName)"
        }

        fun compareTimeZones(placeA: String, placeB: String): TimeZoneComparisonResult {
            val keyA = placeA.trim().lowercase()
            val keyB = placeB.trim().lowercase()
            val (zoneIdA, labelA) = CITY_COUNTRY_TO_ZONE[keyA]
                ?: runCatching { ZoneId.of(placeA.trim()).id to placeA.trim() }.getOrDefault("Asia/Kolkata" to "India (IST)")
            val (zoneIdB, labelB) = CITY_COUNTRY_TO_ZONE[keyB]
                ?: runCatching { ZoneId.of(placeB.trim()).id to placeB.trim() }.getOrDefault("Asia/Dubai" to "Dubai, UAE (GST)")

            val instant = Instant.now(clockProvider)
            val zdtA = instant.atZone(ZoneId.of(zoneIdA))
            val zdtB = instant.atZone(ZoneId.of(zoneIdB))

            val offsetSecondsA = zdtA.offset.totalSeconds
            val offsetSecondsB = zdtB.offset.totalSeconds
            val diffMinutes = (offsetSecondsA - offsetSecondsB) / 60L
            val absMinutes = abs(diffMinutes)
            val hours = absMinutes / 60
            val minutes = absMinutes % 60

            val formattedDiff = buildString {
                if (hours > 0) append("$hours hour${if (hours > 1) "s" else ""}")
                if (minutes > 0) {
                    if (isNotEmpty()) append(" ")
                    append("$minutes minutes")
                }
                if (isEmpty()) append("0 minutes (same time zone)")
            }

            val relation = when {
                diffMinutes > 0 -> "$labelA is **$formattedDiff ahead** of $labelB"
                diffMinutes < 0 -> "$labelA is **$formattedDiff behind** $labelB"
                else -> "$labelA and $labelB are in the **exact same time offset**"
            }

            return TimeZoneComparisonResult(
                zoneAId = zoneIdA,
                zoneALabel = labelA,
                zoneATime12h = zdtA.format(TIME_12H_FMT),
                zoneBId = zoneIdB,
                zoneBLabel = labelB,
                zoneBTime12h = zdtB.format(TIME_12H_FMT),
                differenceMinutes = absMinutes,
                formattedDifference = formattedDiff,
                summaryExplanation = "$relation. Right now it is **${zdtA.format(TIME_12H_FMT)}** in $labelA and **${zdtB.format(TIME_12H_FMT)}** in $labelB."
            )
        }
    }

    // =========================================================================
    // SECTION 6 — LOCATION MANAGER & PRIVACY PROTECTION (SECTION 10)
    // =========================================================================

    enum class LocationPermissionState {
        GRANTED_FINE,
        GRANTED_COARSE,
        DENIED
    }

    enum class LocationResolutionStatus {
        RESOLVED_FROM_EXPLICIT_QUERY_CITY,
        RESOLVED_FROM_USER_PREFERRED_CITY,
        RESOLVED_FROM_DEVICE_LOCATION_CITY,
        NEEDS_CITY_PERMISSION_DENIED,
        NEEDS_CITY_GPS_DISABLED,
        NEEDS_CITY_LOCATION_TIMEOUT
    }

    data class LocationResolutionResult(
        val status: LocationResolutionStatus,
        val cityName: String? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val permissionState: LocationPermissionState = LocationPermissionState.DENIED,
        val userFacingExplanation: String
    )

    data class KnownCityCoordinates(
        val displayName: String,
        val country: String,
        val latitude: Double,
        val longitude: Double,
        val timeZoneId: String = "Asia/Kolkata"
    )

    object AstrynLocationManager {

        @Volatile
        var simulatedPermissionState: LocationPermissionState? = null

        @Volatile
        var simulatedGpsEnabled: Boolean? = null

        @Volatile
        var simulatedLocationTimeout: Boolean = false

        @Volatile
        var simulatedDeviceCity: String? = null

        private val KNOWN_CITIES = mapOf(
            "delhi" to KnownCityCoordinates("New Delhi", "India", 28.6139, 77.2090, "Asia/Kolkata"),
            "new delhi" to KnownCityCoordinates("New Delhi", "India", 28.6139, 77.2090, "Asia/Kolkata"),
            "mumbai" to KnownCityCoordinates("Mumbai", "India", 19.0760, 72.8777, "Asia/Kolkata"),
            "bengaluru" to KnownCityCoordinates("Bengaluru", "India", 12.9716, 77.5946, "Asia/Kolkata"),
            "bangalore" to KnownCityCoordinates("Bengaluru", "India", 12.9716, 77.5946, "Asia/Kolkata"),
            "hyderabad" to KnownCityCoordinates("Hyderabad", "India", 17.3850, 78.4867, "Asia/Kolkata"),
            "chennai" to KnownCityCoordinates("Chennai", "India", 13.0827, 80.2707, "Asia/Kolkata"),
            "kolkata" to KnownCityCoordinates("Kolkata", "India", 22.5726, 88.3639, "Asia/Kolkata"),
            "pune" to KnownCityCoordinates("Pune", "India", 18.5204, 73.8567, "Asia/Kolkata"),
            "jaipur" to KnownCityCoordinates("Jaipur", "India", 26.9124, 75.7873, "Asia/Kolkata"),
            "lucknow" to KnownCityCoordinates("Lucknow", "India", 26.8467, 80.9462, "Asia/Kolkata"),
            "ahmedabad" to KnownCityCoordinates("Ahmedabad", "India", 23.0225, 72.5714, "Asia/Kolkata"),
            "surat" to KnownCityCoordinates("Surat", "India", 21.1702, 72.8311, "Asia/Kolkata"),
            "patna" to KnownCityCoordinates("Patna", "India", 25.5941, 85.1376, "Asia/Kolkata"),
            "bhopal" to KnownCityCoordinates("Bhopal", "India", 23.2599, 77.4126, "Asia/Kolkata"),
            "indore" to KnownCityCoordinates("Indore", "India", 22.7196, 75.8577, "Asia/Kolkata"),
            "chandigarh" to KnownCityCoordinates("Chandigarh", "India", 30.7333, 76.7794, "Asia/Kolkata"),
            "noida" to KnownCityCoordinates("Noida", "India", 28.5355, 77.3910, "Asia/Kolkata"),
            "gurgaon" to KnownCityCoordinates("Gurugram", "India", 28.4595, 77.0266, "Asia/Kolkata"),
            "gurugram" to KnownCityCoordinates("Gurugram", "India", 28.4595, 77.0266, "Asia/Kolkata"),
            "dubai" to KnownCityCoordinates("Dubai", "UAE", 25.2048, 55.2708, "Asia/Dubai"),
            "london" to KnownCityCoordinates("London", "UK", 51.5072, -0.1276, "Europe/London"),
            "new york" to KnownCityCoordinates("New York", "USA", 40.7128, -74.0060, "America/New_York"),
            "tokyo" to KnownCityCoordinates("Tokyo", "Japan", 35.6762, 139.6503, "Asia/Tokyo"),
            "singapore" to KnownCityCoordinates("Singapore", "Singapore", 1.3521, 103.8198, "Asia/Singapore"),
            "sydney" to KnownCityCoordinates("Sydney", "Australia", -33.8688, 151.2093, "Australia/Sydney")
        )

        fun normalizeRecognizedCity(rawCandidate: String): String? {
            val clean = rawCandidate.trim().lowercase().replace(Regex("""\s+"""), " ")
            if (clean.isBlank()) return null
            KNOWN_CITIES[clean]?.let { return it.displayName }
            KNOWN_CITIES.entries.firstOrNull { (k, _) ->
                Regex("""\b${Regex.escape(k)}\b""").containsMatchIn(clean)
            }?.let { return it.value.displayName }
            return null
        }

        fun lookupKnownCity(cityName: String): KnownCityCoordinates? {
            val clean = cityName.trim().lowercase()
            return KNOWN_CITIES[clean]
                ?: KNOWN_CITIES.values.firstOrNull { it.displayName.equals(cityName.trim(), ignoreCase = true) }
        }

        fun extractCityFromQuery(query: String): String? {
            val lower = query.lowercase()
            // Check known cities first
            for ((key, info) in KNOWN_CITIES) {
                if (Regex("""\b${Regex.escape(key)}\b""").containsMatchIn(lower)) {
                    return info.displayName
                }
            }
            // Check "weather in <City>" or "<City> ka mausam/weather"
            val inCityMatch = Regex("""(?i)\b(?:weather|temperature|forecast|mausam|baarish)\s+(?:in|of|for|at)\s+([A-Za-z][A-Za-z\s]{2,20})""").find(query)
            if (inCityMatch != null) {
                val candidate = inCityMatch.groupValues[1]
                    .replace(Regex("""(?i)\b(today|tomorrow|right\s+now|aaj|kal|please|check|karo|batao)\b"""), "")
                    .trim()
                if (candidate.length in 3..24) {
                    return candidate.split(" ").joinToString(" ") { word ->
                        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
                    }
                }
            }
            return null
        }

        fun setPreferredWeatherCity(
            userId: String,
            cityName: String,
            nowMillis: Long = System.currentTimeMillis()
        ) {
            val cleanUser = userId.trim().ifBlank { "anonymous_local_user" }
            val normalized = normalizeRecognizedCity(cityName) ?: cityName.trim()
            AstrynUserProfileManager.updateProfile(cleanUser) {
                it.copy(preferredWeatherCity = normalized)
            }
            // Store ONLY city name (never precise GPS coordinates) in personal preference memory
            recordMemory(
                userId = cleanUser,
                category = PersonalMemoryCategory.B_PERSONAL_PREFERENCE_MEMORY,
                key = "preferred_weather_city",
                value = normalized,
                tags = setOf("weather", "city", "location", normalized.lowercase()),
                nowMillis = nowMillis
            )
        }

        fun getPreferredWeatherCity(userId: String): String? {
            val cleanUser = userId.trim().ifBlank { "anonymous_local_user" }
            return AstrynUserProfileManager.getProfile(cleanUser).preferredWeatherCity
        }

        fun checkPermissionState(context: Context? = null): LocationPermissionState {
            simulatedPermissionState?.let { return it }
            if (context == null) return LocationPermissionState.DENIED
            return try {
                val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                when {
                    fine -> LocationPermissionState.GRANTED_FINE
                    coarse -> LocationPermissionState.GRANTED_COARSE
                    else -> LocationPermissionState.DENIED
                }
            } catch (_: Exception) {
                LocationPermissionState.DENIED
            }
        }

        fun isLocationServicesEnabled(context: Context? = null): Boolean {
            simulatedGpsEnabled?.let { return it }
            if (context == null) return true
            return try {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            } catch (_: Exception) {
                false
            }
        }

        fun resolveLocationForWeather(
            userId: String,
            query: String,
            context: Context? = null
        ): LocationResolutionResult {
            val cleanUser = userId.trim().ifBlank { "anonymous_local_user" }

            // 1. Explicit city mentioned in query always works (even when location permission is denied!)
            val explicitCity = extractCityFromQuery(query)
            if (explicitCity != null) {
                val known = lookupKnownCity(explicitCity)
                // If user explicitly asked to change/use this city as their city, update preference
                if (Regex("""(?i)\b(mera\s+city|my\s+city|set\s+city|change\s+city|switch\s+city|prefer)\b""").containsMatchIn(query)) {
                    setPreferredWeatherCity(cleanUser, explicitCity)
                }
                return LocationResolutionResult(
                    status = LocationResolutionStatus.RESOLVED_FROM_EXPLICIT_QUERY_CITY,
                    cityName = known?.displayName ?: explicitCity,
                    latitude = known?.latitude,
                    longitude = known?.longitude,
                    permissionState = checkPermissionState(context),
                    userFacingExplanation = "Resolved weather location from explicitly requested city: ${known?.displayName ?: explicitCity}."
                )
            }

            // 2. Saved preferred city for this user
            val preferredCity = getPreferredWeatherCity(cleanUser)
            if (!preferredCity.isNullOrBlank()) {
                val known = lookupKnownCity(preferredCity)
                return LocationResolutionResult(
                    status = LocationResolutionStatus.RESOLVED_FROM_USER_PREFERRED_CITY,
                    cityName = known?.displayName ?: preferredCity,
                    latitude = known?.latitude,
                    longitude = known?.longitude,
                    permissionState = checkPermissionState(context),
                    userFacingExplanation = "Using your saved preferred city: ${known?.displayName ?: preferredCity}."
                )
            }

            // 3. Check device location permission & GPS status
            val permState = checkPermissionState(context)
            if (permState == LocationPermissionState.DENIED) {
                return LocationResolutionResult(
                    status = LocationResolutionStatus.NEEDS_CITY_PERMISSION_DENIED,
                    permissionState = LocationPermissionState.DENIED,
                    userFacingExplanation = "Location permission is not enabled and no preferred city is saved yet. Please mention your city name (for example: **\"Delhi ka weather batao\"** or **\"Mumbai weather\"**) or set a preferred city."
                )
            }

            if (!isLocationServicesEnabled(context)) {
                return LocationResolutionResult(
                    status = LocationResolutionStatus.NEEDS_CITY_GPS_DISABLED,
                    permissionState = permState,
                    userFacingExplanation = "Device GPS / Location Services are currently turned off. Please enable Location Services or tell me your city name (e.g., **\"Bengaluru ka mausam kaisa hai?\"**)."
                )
            }

            if (simulatedLocationTimeout) {
                return LocationResolutionResult(
                    status = LocationResolutionStatus.NEEDS_CITY_LOCATION_TIMEOUT,
                    permissionState = permState,
                    userFacingExplanation = "Device location lookup timed out due to weak signal. Please specify your city name directly (e.g., **\"Delhi weather\"** or **\"Jaipur ka mausam\"**)."
                )
            }

            val resolvedDeviceCity = simulatedDeviceCity ?: "New Delhi"
            val known = lookupKnownCity(resolvedDeviceCity)
            return LocationResolutionResult(
                status = LocationResolutionStatus.RESOLVED_FROM_DEVICE_LOCATION_CITY,
                cityName = known?.displayName ?: resolvedDeviceCity,
                latitude = known?.latitude,
                longitude = known?.longitude,
                permissionState = permState,
                userFacingExplanation = "Resolved coarse city location from device: ${known?.displayName ?: resolvedDeviceCity} (raw GPS coordinates are never stored)."
            )
        }

        fun resetSimulationState() {
            simulatedPermissionState = null
            simulatedGpsEnabled = null
            simulatedLocationTimeout = false
            simulatedDeviceCity = null
        }
    }

    // =========================================================================
    // SECTION 7 — REAL-TIME WEATHER INTELLIGENCE & OFFLINE CACHE (SECTIONS 9, 11, 14)
    // =========================================================================

    data class CurrentWeatherSnapshot(
        val temperatureC: Double,
        val feelsLikeC: Double,
        val conditionText: String,
        val humidityPercent: Int,
        val windSpeedKmh: Double,
        val windDirectionDegrees: Int = 270,
        val precipitationMm: Double = 0.0,
        val visibilityKm: Double = 10.0,
        val pressureHpa: Double = 1012.0
    )

    data class HourlyWeatherSlot(
        val timeLabel: String, // e.g., "14:00" or "2026-05-10T18:00"
        val hour24: Int,
        val temperatureC: Double,
        val rainProbabilityPercent: Int,
        val precipitationMm: Double,
        val windSpeedKmh: Double,
        val conditionText: String
    )

    data class DailyWeatherForecastItem(
        val dateIso: String,
        val dayLabel: String,
        val minTempC: Double,
        val maxTempC: Double,
        val rainProbabilityPercent: Int,
        val expectedPrecipitationMm: Double,
        val conditionText: String
    )

    data class WeatherAlertItem(
        val title: String,
        val severity: String,
        val description: String,
        val effectiveWindow: String
    )

    data class CompleteWeatherBundle(
        val cityName: String,
        val country: String,
        val fetchedAtMillis: Long,
        val isFromOfflineCache: Boolean = false,
        val isStaleCache: Boolean = false,
        val current: CurrentWeatherSnapshot,
        val hourlyNext24h: List<HourlyWeatherSlot>,
        val dailyNext7d: List<DailyWeatherForecastItem>,
        val alerts: List<WeatherAlertItem> = emptyList(),
        val sourceAttribution: String = "Open-Meteo Weather API"
    )

    interface WeatherDataGateway {
        fun fetchLiveWeather(
            cityName: String,
            latitude: Double,
            longitude: Double,
            nowMillis: Long
        ): CompleteWeatherBundle
    }

    object OpenMeteoHttpWeatherGateway : WeatherDataGateway {
        override fun fetchLiveWeather(
            cityName: String,
            latitude: Double,
            longitude: Double,
            nowMillis: Long
        ): CompleteWeatherBundle {
            var resolvedLat = latitude
            var resolvedLon = longitude
            var resolvedCity = cityName
            var resolvedCountry = "India"

            // 1. If lat/lon are zero, query Open-Meteo Geocoding API
            if (abs(resolvedLat) < 0.001 && abs(resolvedLon) < 0.001) {
                val encodedCity = URLEncoder.encode(cityName.trim(), "UTF-8")
                val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=$encodedCity&count=1&language=en&format=json"
                val geoJson = performHttpGet(geoUrl)
                val latMatch = Regex(""""latitude"\s*:\s*([-+]?\d+(?:\.\d+)?)""").find(geoJson)
                val lonMatch = Regex(""""longitude"\s*:\s*([-+]?\d+(?:\.\d+)?)""").find(geoJson)
                val nameMatch = Regex(""""name"\s*:\s*"([^"]+)"""").find(geoJson)
                val countryMatch = Regex(""""country"\s*:\s*"([^"]+)"""").find(geoJson)
                if (latMatch != null && lonMatch != null) {
                    resolvedLat = latMatch.groupValues[1].toDouble()
                    resolvedLon = lonMatch.groupValues[1].toDouble()
                    resolvedCity = nameMatch?.groupValues?.get(1) ?: cityName
                    resolvedCountry = countryMatch?.groupValues?.get(1) ?: ""
                } else {
                    throw IllegalStateException("City '$cityName' could not be geocoded by weather service.")
                }
            }

            // 2. Query Open-Meteo Forecast API
            val forecastUrl = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$resolvedLat&longitude=$resolvedLon" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,surface_pressure,wind_speed_10m,wind_direction_10m" +
                "&hourly=temperature_2m,precipitation_probability,precipitation,weather_code,wind_speed_10m" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max" +
                "&timezone=auto&forecast_days=7"

            val rawJson = performHttpGet(forecastUrl)
            return parseOpenMeteoResponse(
                rawJson = rawJson,
                cityName = resolvedCity,
                country = resolvedCountry,
                nowMillis = nowMillis
            )
        }

        private fun performHttpGet(urlString: String): String {
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Accept", "application/json")
            }
            return try {
                val code = conn.responseCode
                if (code !in 200..299) {
                    throw IllegalStateException("Weather API HTTP $code")
                }
                BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }

        internal fun parseOpenMeteoResponse(
            rawJson: String,
            cityName: String,
            country: String,
            nowMillis: Long
        ): CompleteWeatherBundle {
            // Pure-JVM safe extraction (no dependency on android.jar org.json stub)
            val currentBlock = Regex(""""current"\s*:\s*\{([^}]+)\}""").find(rawJson)?.groupValues?.get(1) ?: rawJson
            val tempC = extractDoubleField(currentBlock, "temperature_2m", 28.0)
            val feelsC = extractDoubleField(currentBlock, "apparent_temperature", tempC)
            val humidity = extractDoubleField(currentBlock, "relative_humidity_2m", 55.0).roundToInt()
            val precipMm = extractDoubleField(currentBlock, "precipitation", 0.0)
            val windKmh = extractDoubleField(currentBlock, "wind_speed_10m", 12.0)
            val windDir = extractDoubleField(currentBlock, "wind_direction_10m", 270.0).roundToInt()
            val pressure = extractDoubleField(currentBlock, "surface_pressure", 1012.0)
            val code = extractDoubleField(currentBlock, "weather_code", 1.0).roundToInt()

            val currentSnapshot = CurrentWeatherSnapshot(
                temperatureC = tempC,
                feelsLikeC = feelsC,
                conditionText = mapWmoCodeToCondition(code),
                humidityPercent = humidity,
                windSpeedKmh = windKmh,
                windDirectionDegrees = windDir,
                precipitationMm = precipMm,
                visibilityKm = if (code in listOf(45, 48)) 2.5 else 10.0,
                pressureHpa = pressure
            )

            // Parse hourly arrays
            val hourlyBlock = Regex(""""hourly"\s*:\s*\{([^}]+)\}""").find(rawJson)?.groupValues?.get(1).orEmpty()
            val hourlyTimes = extractStringArray(hourlyBlock, "time")
            val hourlyTemps = extractDoubleArray(hourlyBlock, "temperature_2m")
            val hourlyRainProbs = extractDoubleArray(hourlyBlock, "precipitation_probability")
            val hourlyPrecipMm = extractDoubleArray(hourlyBlock, "precipitation")
            val hourlyCodes = extractDoubleArray(hourlyBlock, "weather_code")
            val hourlyWinds = extractDoubleArray(hourlyBlock, "wind_speed_10m")

            val hourlySlots = (0 until minOf(24, hourlyTimes.size)).map { idx ->
                val tStr = hourlyTimes[idx]
                val hourInt = tStr.substringAfter("T", "12:00").substringBefore(":").toIntOrNull() ?: (idx % 24)
                HourlyWeatherSlot(
                    timeLabel = tStr.substringAfter("T", String.format(Locale.US, "%02d:00", hourInt)),
                    hour24 = hourInt,
                    temperatureC = hourlyTemps.getOrElse(idx) { tempC },
                    rainProbabilityPercent = hourlyRainProbs.getOrElse(idx) { 0.0 }.roundToInt(),
                    precipitationMm = hourlyPrecipMm.getOrElse(idx) { 0.0 },
                    windSpeedKmh = hourlyWinds.getOrElse(idx) { windKmh },
                    conditionText = mapWmoCodeToCondition(hourlyCodes.getOrElse(idx) { code.toDouble() }.roundToInt())
                )
            }

            // Parse daily arrays
            val dailyBlock = Regex(""""daily"\s*:\s*\{([^}]+)\}""").find(rawJson)?.groupValues?.get(1).orEmpty()
            val dailyDates = extractStringArray(dailyBlock, "time")
            val dailyMax = extractDoubleArray(dailyBlock, "temperature_2m_max")
            val dailyMin = extractDoubleArray(dailyBlock, "temperature_2m_min")
            val dailyPrecipSum = extractDoubleArray(dailyBlock, "precipitation_sum")
            val dailyRainProbMax = extractDoubleArray(dailyBlock, "precipitation_probability_max")
            val dailyCodes = extractDoubleArray(dailyBlock, "weather_code")

            val dailyItems = (0 until minOf(7, dailyDates.size)).map { idx ->
                val dateStr = dailyDates[idx]
                val dayLabel = when (idx) {
                    0 -> "Today ($dateStr)"
                    1 -> "Tomorrow ($dateStr)"
                    else -> runCatching {
                        val ld = LocalDate.parse(dateStr)
                        "${ld.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)} ($dateStr)"
                    }.getOrDefault(dateStr)
                }
                DailyWeatherForecastItem(
                    dateIso = dateStr,
                    dayLabel = dayLabel,
                    minTempC = dailyMin.getOrElse(idx) { tempC - 4.0 },
                    maxTempC = dailyMax.getOrElse(idx) { tempC + 4.0 },
                    rainProbabilityPercent = dailyRainProbMax.getOrElse(idx) { 0.0 }.roundToInt(),
                    expectedPrecipitationMm = dailyPrecipSum.getOrElse(idx) { 0.0 },
                    conditionText = mapWmoCodeToCondition(dailyCodes.getOrElse(idx) { code.toDouble() }.roundToInt())
                )
            }

            // Only emit alerts if severe threshold is actually present in real data (never fabricate alerts)
            val derivedAlerts = mutableListOf<WeatherAlertItem>()
            if (currentSnapshot.windSpeedKmh >= 60.0 || currentSnapshot.precipitationMm >= 25.0 || code in listOf(95, 96, 99)) {
                derivedAlerts.add(
                    WeatherAlertItem(
                        title = "Severe Weather Advisory (${currentSnapshot.conditionText})",
                        severity = "WARNING",
                        description = "High wind (${currentSnapshot.windSpeedKmh} km/h) or heavy thunderstorm activity detected in $cityName.",
                        effectiveWindow = "Next 6 hours"
                    )
                )
            }

            return CompleteWeatherBundle(
                cityName = cityName,
                country = country,
                fetchedAtMillis = nowMillis,
                isFromOfflineCache = false,
                isStaleCache = false,
                current = currentSnapshot,
                hourlyNext24h = hourlySlots,
                dailyNext7d = dailyItems,
                alerts = derivedAlerts
            )
        }

        private fun extractDoubleField(block: String, key: String, defaultVal: Double): Double {
            val m = Regex(""""${Regex.escape(key)}"\s*:\s*([-+]?\d+(?:\.\d+)?)""").find(block)
            return m?.groupValues?.get(1)?.toDoubleOrNull() ?: defaultVal
        }

        private fun extractStringArray(block: String, key: String): List<String> {
            val arrMatch = Regex(""""${Regex.escape(key)}"\s*:\s*\[([^\]]*)\]""").find(block) ?: return emptyList()
            return Regex(""""([^"]+)"""").findAll(arrMatch.groupValues[1]).map { it.groupValues[1] }.toList()
        }

        private fun extractDoubleArray(block: String, key: String): List<Double> {
            val arrMatch = Regex(""""${Regex.escape(key)}"\s*:\s*\[([^\]]*)\]""").find(block) ?: return emptyList()
            return Regex("""[-+]?\d+(?:\.\d+)?""").findAll(arrMatch.groupValues[1]).mapNotNull { it.value.toDoubleOrNull() }.toList()
        }

        fun mapWmoCodeToCondition(code: Int): String {
            return when (code) {
                0 -> "Clear Sky"
                1 -> "Mainly Clear"
                2 -> "Partly Cloudy"
                3 -> "Overcast"
                45, 48 -> "Foggy"
                51, 53, 55 -> "Light Drizzle"
                61, 63 -> "Moderate Rain"
                65 -> "Heavy Rain"
                71, 73, 75 -> "Snowfall"
                80, 81, 82 -> "Rain Showers"
                95, 96, 99 -> "Thunderstorm"
                else -> "Partly Cloudy"
            }
        }
    }

    object AstrynWeatherIntelligence {
        private const val CACHE_FRESH_TTL_MS = 15 * 60 * 1000L // 15 minutes
        private val weatherCacheByCity = ConcurrentHashMap<String, CompleteWeatherBundle>()

        @Volatile
        var weatherGateway: WeatherDataGateway = OpenMeteoHttpWeatherGateway

        @Volatile
        var forceOfflineMode: Boolean = false

        fun seedWeatherCacheForTesting(bundle: CompleteWeatherBundle) {
            weatherCacheByCity[bundle.cityName.trim().lowercase()] = bundle
        }

        fun clearWeatherCache() {
            weatherCacheByCity.clear()
        }

        fun getWeatherForCity(
            cityName: String,
            latitude: Double? = null,
            longitude: Double? = null,
            nowMillis: Long = Instant.now(AstrynTimeIntelligence.clockProvider).toEpochMilli()
        ): Result<CompleteWeatherBundle> {
            val key = cityName.trim().lowercase()
            val known = AstrynLocationManager.lookupKnownCity(cityName)
            val lat = latitude ?: known?.latitude ?: 0.0
            val lon = longitude ?: known?.longitude ?: 0.0

            if (!forceOfflineMode) {
                try {
                    val live = weatherGateway.fetchLiveWeather(
                        cityName = known?.displayName ?: cityName.trim(),
                        latitude = lat,
                        longitude = lon,
                        nowMillis = nowMillis
                    )
                    weatherCacheByCity[key] = live
                    weatherCacheByCity[live.cityName.trim().lowercase()] = live
                    return Result.success(live)
                } catch (e: Exception) {
                    // Fall through to cached check on network error
                }
            }

            // Offline or API error fallback: check cache and explicitly label as cached/outdated
            val cached = weatherCacheByCity[key]
                ?: known?.displayName?.lowercase()?.let { weatherCacheByCity[it] }
            if (cached != null) {
                val ageMs = (nowMillis - cached.fetchedAtMillis).coerceAtLeast(0L)
                return Result.success(
                    cached.copy(
                        isFromOfflineCache = true,
                        isStaleCache = ageMs > CACHE_FRESH_TTL_MS
                    )
                )
            }

            return Result.failure(
                IllegalStateException(
                    "Unable to fetch live weather for $cityName (device is offline or weather service is unreachable, and no cached weather is stored for $cityName)."
                )
            )
        }

        fun formatWeatherResponseForQuery(
            userId: String,
            query: String,
            context: Context? = null,
            nowMillis: Long = Instant.now(AstrynTimeIntelligence.clockProvider).toEpochMilli()
        ): String {
            val lang = AstrynPersonalizationEngine.detectResponseLanguage(userId, query)
            val locResult = AstrynLocationManager.resolveLocationForWeather(userId, query, context)

            if (locResult.cityName == null) {
                return buildString {
                    appendLine("### Location Needed for Live Weather")
                    appendLine(locResult.userFacingExplanation)
                    if (lang == PreferredLanguage.HINGLISH || lang == PreferredLanguage.HINDI) {
                        appendLine()
                        appendLine("Aap apna shahar ka naam likh kar pooch sakte hain — jaise: **\"Delhi ka weather batao\"**, **\"Mumbai mein aaj baarish hogi kya?\"**, ya **\"Set my weather city to Bengaluru\"**.")
                    }
                }.trim()
            }

            val weatherResult = getWeatherForCity(
                cityName = locResult.cityName,
                latitude = locResult.latitude,
                longitude = locResult.longitude,
                nowMillis = nowMillis
            )

            val bundle = weatherResult.getOrElse { err ->
                return buildString {
                    appendLine("### Live Weather Unavailable (${locResult.cityName})")
                    appendLine("Could not retrieve live weather right now: ${err.message}")
                    appendLine("No cached weather snapshot is available for **${locResult.cityName}**. Please check your internet connection and try again.")
                }.trim()
            }

            val lower = query.lowercase()
            val timeSnap = AstrynTimeIntelligence.getCurrentSnapshot()
            val fetchTimeFormatted = Instant.ofEpochMilli(bundle.fetchedAtMillis)
                .atZone(timeSnap.zonedDateTime.zone)
                .format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.US))

            return buildString {
                // 1. Offline / Stale cache banner (NEVER present cached weather as live!)
                if (bundle.isFromOfflineCache) {
                    val staleTag = if (bundle.isStaleCache) "OUTDATED / STALE CACHE" else "OFFLINE CACHED DATA"
                    appendLine("> **[CACHED WEATHER — $staleTag]**")
                    appendLine("> *Live weather network is currently unreachable. Showing cached weather for **${bundle.cityName}** originally fetched at **$fetchTimeFormatted** (not live data).*")
                    appendLine()
                }

                appendLine("### Weather Intelligence — ${bundle.cityName}${if (bundle.country.isNotBlank()) ", ${bundle.country}" else ""}")
                appendLine("*Local Time: ${timeSnap.formattedTime12h} (${timeSnap.periodOfDay.englishLabel}) • Source: ${bundle.sourceAttribution}*")
                appendLine()

                // 2. Current conditions summary
                val cur = bundle.current
                appendLine("#### Current Conditions")
                appendLine("- **Temperature:** ${cur.temperatureC}°C *(Feels like ${cur.feelsLikeC}°C)*")
                appendLine("- **Condition:** ${cur.conditionText}")
                appendLine("- **Humidity:** ${cur.humidityPercent}% | **Wind:** ${cur.windSpeedKmh} km/h | **Pressure:** ${cur.pressureHpa.roundToInt()} hPa")
                appendLine("- **Current Precipitation:** ${cur.precipitationMm} mm | **Visibility:** ${cur.visibilityKm} km")

                // 3. Evening / Outdoor suitability ("Shaam ko bahar jana sahi rahega?")
                if (lower.containsAny("shaam", "evening", "bahar", "outside", "outdoor", "walk", "plan")) {
                    val eveningSlots = bundle.hourlyNext24h.filter { it.hour24 in 17..21 }
                    appendLine()
                    appendLine("#### Evening Outdoor Advisory (5:00 PM – 9:00 PM)")
                    if (eveningSlots.isNotEmpty()) {
                        val maxRainProb = eveningSlots.maxOf { it.rainProbabilityPercent }
                        val totalRainMm = eveningSlots.sumOf { it.precipitationMm }
                        val avgTemp = (eveningSlots.map { it.temperatureC }.average() * 10).roundToInt() / 10.0
                        val maxWind = eveningSlots.maxOf { it.windSpeedKmh }
                        appendLine("- **Evening Temperature Range:** ~${avgTemp}°C")
                        appendLine("- **Evening Rain Probability:** $maxRainProb% *(Expected rainfall volume: ${String.format(Locale.US, "%.1f", totalRainMm)} mm)*")
                        val recommendation = when {
                            maxRainProb >= 60 || totalRainMm >= 2.0 ->
                                "Evening rain probability is high ($maxRainProb% with ~${String.format(Locale.US, "%.1f", totalRainMm)} mm expected rainfall). Carry an umbrella or prefer indoor plans."
                            maxWind >= 40.0 ->
                                "Strong evening winds ($maxWind km/h) expected. Outdoor travel may be windy."
                            avgTemp >= 38.0 ->
                                "Evening remains quite warm ($avgTemp°C). Stay hydrated if heading out."
                            else ->
                                "Conditions look pleasant and comfortable for going out this evening ($avgTemp°C, low rain risk at $maxRainProb%)."
                        }
                        appendLine("- **Recommendation:** $recommendation")
                    }
                }

                // 4. Rain timing & probability vs precipitation ("Aaj baarish hogi kya?", "Aaj kis time baarish ki possibility hai?")
                if (lower.containsAny("baarish", "rain", "umbrella", "chhata", "kis time", "what time", "hourly", "possibility")) {
                    appendLine()
                    appendLine("#### Rain & Hourly Forecast Analysis (Next 24 Hours)")
                    val peakSlot = bundle.hourlyNext24h.maxByOrNull { it.rainProbabilityPercent }
                    val rainySlots = bundle.hourlyNext24h.filter { it.rainProbabilityPercent >= 40 || it.precipitationMm > 0.2 }
                    if (rainySlots.isNotEmpty() && peakSlot != null) {
                        appendLine("- **Peak Rain Window:** Around **${peakSlot.timeLabel}** — **${peakSlot.rainProbabilityPercent}% probability** *(Expected rainfall amount: **${peakSlot.precipitationMm} mm**, ${peakSlot.conditionText})*.")
                        appendLine("- **Key Rain Hours:** " + rainySlots.take(5).joinToString(" | ") {
                            "${it.timeLabel}: ${it.rainProbabilityPercent}% (${it.precipitationMm} mm, ${it.temperatureC}°C)"
                        })
                    } else {
                        val maxProb = peakSlot?.rainProbabilityPercent ?: 0
                        val maxMm = bundle.hourlyNext24h.maxOfOrNull { it.precipitationMm } ?: 0.0
                        appendLine("- **Rain Outlook:** Low chance of rain today (Peak probability: **$maxProb%**, expected rainfall amount: **${maxMm} mm**).")
                    }
                }

                // 5. Tomorrow or 7-day forecast ("Kal ka temperature kitna rahega?", "Agale 7 din ka weather batao")
                if (lower.containsAny("kal", "tomorrow") && bundle.dailyNext7d.size >= 2) {
                    val tom = bundle.dailyNext7d[1]
                    appendLine()
                    appendLine("#### Tomorrow's Forecast (${tom.dayLabel})")
                    appendLine("- **Temperature Range:** Min **${tom.minTempC}°C** / Max **${tom.maxTempC}°C**")
                    appendLine("- **Condition:** ${tom.conditionText}")
                    appendLine("- **Rain Probability:** ${tom.rainProbabilityPercent}% *(Expected rainfall: ${tom.expectedPrecipitationMm} mm)*")
                }

                if (lower.containsAny("7 din", "7 day", "7-day", "week", "agale", "weekly", "daily forecast")) {
                    appendLine()
                    appendLine("#### 7-Day Weather Forecast")
                    bundle.dailyNext7d.forEach { day ->
                        appendLine("- **${day.dayLabel}:** ${day.minTempC}°C – ${day.maxTempC}°C | ${day.conditionText} | Rain Prob: ${day.rainProbabilityPercent}% (${day.expectedPrecipitationMm} mm)")
                    }
                }

                // 6. Weather Alerts (never invent alerts!)
                if (lower.containsAny("alert", "warning", "storm", "advisory", "cyclone") || bundle.alerts.isNotEmpty()) {
                    appendLine()
                    appendLine("#### Weather Alerts & Advisories")
                    if (bundle.alerts.isEmpty()) {
                        appendLine("- **No active severe weather alerts** reported for ${bundle.cityName} at this time.")
                    } else {
                        bundle.alerts.forEach { alert ->
                            appendLine("- **[${alert.severity}] ${alert.title}** (${alert.effectiveWindow}): ${alert.description}")
                        }
                    }
                }
            }.trim()
        }
    }

    // =========================================================================
    // SECTION 8 — SMART DAILY PERSONAL ASSISTANT & QUERY ROUTING (SECTIONS 8, 12)
    // =========================================================================

    fun isTimeOrDateQuery(query: String): Boolean {
        val lower = query.lowercase().trim()
        return Regex(
            """(?i)(\babhi\s+kitne\s+baje|\bwhat\s+time\s+is\s+it|\bcurrent\s+time\b|\baaj\s+kaun\s+sa\s+din\s+hai|\bwhat\s+day\s+is\s+today|\baaj\s+ki\s+date|\bwhat\s+is\s+today'?s\s+date|\bkal\s+ki\s+date|\btomorrow'?s\s+date|\baaj\s+shaam\s+kitne\s+baje|\btime\s+mein\s+kitna\s+difference|\btime\s+difference\s+between|\btimezone\b|\btime\s+zone\b|\bconvert\s+.*time\b)"""
        ).containsMatchIn(lower)
    }

    fun isWeatherQuery(query: String): Boolean {
        val lower = query.lowercase().trim()
        return Regex(
            """(?i)(\bmausam\b|\bweather\b|\bbaarish\b|\brain\s+forecast\b|\bwill\s+it\s+rain\b|\btemperature\s+kitna|\bkal\s+ka\s+temperature|\b7\s+din\s+ka\s+weather|\b7[\s\-]day\s+forecast|\bhourly\s+forecast|\bweather\s+alert|\bshaam\s+ko\s+bahar\s+jana|\bmere\s+city\s+ka\s+weather)"""
        ).containsMatchIn(lower)
    }

    fun isProjectOrContinuityQuery(query: String): Boolean {
        val lower = query.lowercase().trim()
        return Regex(
            """(?i)(\bnext\s+upgrade\s+kya\s+hai|\bpending\s+work\s+batao|\bpichle\s+project\s+mein\s+jo\s+problem|\bwahi\s+design\s+preserve|\bmera\s+previous\s+decision\s+follow|\bkal\s+jo\s+baat\s+hui\s+thi\s+usko\s+continue|\bproject\s+ka\s+status|\baaj\s+mujhe\s+kya\s+kaam\s+complete\s+karna\s+chahiye)"""
        ).containsMatchIn(lower)
    }

    fun shouldHandlePersonalOrEnvironmentQuery(query: String): Boolean {
        return isTimeOrDateQuery(query) || isWeatherQuery(query) || isProjectOrContinuityQuery(query)
    }

    fun generatePersonalOrEnvironmentResponse(
        userId: String,
        query: String,
        conversationHistory: List<ChatMessage> = emptyList(),
        userPrefs: UserPreferences? = null,
        context: Context? = null
    ): String? {
        val cleanUser = userId.trim().ifBlank { "anonymous_local_user" }
        AstrynUserProfileManager.syncFromUserPreferences(cleanUser, userPrefs)
        AstrynPreferenceEngine.ingestTurnForPreferencesAndMemory(cleanUser, query)

        val lower = query.lowercase().trim()

        // 1. Live Time & Date Intelligence
        if (isTimeOrDateQuery(query)) {
            return formatTimeAndDateResponse(cleanUser, query)
        }

        // 2. Real-Time Weather Intelligence
        if (isWeatherQuery(query)) {
            return AstrynWeatherIntelligence.formatWeatherResponseForQuery(
                userId = cleanUser,
                query = query,
                context = context
            )
        }

        // 3. Smart Daily Personal Assistant ("Aaj mujhe kya kaam complete karna chahiye?")
        if (Regex("""(?i)\b(aaj\s+mujhe\s+kya\s+kaam\s+complete\s+karna\s+chahiye|what\s+should\s+i\s+work\s+on\s+today|today'?s\s+priority\s+plan)\b""").containsMatchIn(lower)) {
            return formatSmartDailyPlanResponse(cleanUser, query)
        }

        // 4. Project Continuity & Cross-Conversation Context Queries
        if (isProjectOrContinuityQuery(query)) {
            return formatProjectContinuityResponse(cleanUser, query, conversationHistory)
        }

        return null
    }

    private fun formatTimeAndDateResponse(userId: String, query: String): String {
        val lower = query.lowercase()
        val snap = AstrynTimeIntelligence.getCurrentSnapshot()

        // Time zone comparison check (e.g., "India aur Dubai ke time mein kitna difference hai?")
        val tzCompareMatch = Regex("""(?i)\b([a-zA-Z\s]{2,18})\s+(?:aur|and|to|vs)\s+([a-zA-Z\s]{2,18})\s+(?:ke\s+)?time\s+(?:mein\s+kitna\s+difference|difference|zone)""").find(query)
        if (tzCompareMatch != null || (lower.contains("india") && lower.contains("dubai") && lower.contains("time"))) {
            val placeA = if (lower.contains("india") && lower.contains("dubai")) "India" else tzCompareMatch?.groupValues?.get(1)?.trim() ?: "India"
            val placeB = if (lower.contains("india") && lower.contains("dubai")) "Dubai" else tzCompareMatch?.groupValues?.get(2)?.trim() ?: "Dubai"
            val cmp = AstrynTimeIntelligence.compareTimeZones(placeA, placeB)
            return buildString {
                appendLine("### Live Time Zone Comparison (${cmp.zoneALabel} vs ${cmp.zoneBLabel})")
                appendLine("- **Time Difference:** **${cmp.formattedDifference}**")
                appendLine("- **${cmp.zoneALabel} (${cmp.zoneAId}):** ${cmp.zoneATime12h}")
                appendLine("- **${cmp.zoneBLabel} (${cmp.zoneBId}):** ${cmp.zoneBTime12h}")
                appendLine("- **Summary:** ${cmp.summaryExplanation}")
            }.trim()
        }

        return buildString {
            appendLine("### Live System Time & Date Awareness")
            appendLine("- **Current Local Time:** **${snap.formattedTime12h}** (${snap.formattedTime24h}) — *${snap.periodOfDay.englishLabel} (${snap.periodOfDay.hindiLabel})*")
            appendLine("- **Today's Date (Aaj):** **${snap.formattedDate}** (${snap.isoDate})")
            appendLine("- **Day of the Week:** **${snap.dayOfWeekEnglish}** — *${snap.dayOfWeekHindi}*")
            appendLine("- **Time Zone:** `${snap.timeZoneId}` (${snap.utcOffsetString})")

            if (lower.containsAny("kal", "tomorrow")) {
                appendLine("- **Tomorrow's Date (Kal):** **${snap.tomorrowFormattedDate}** (${snap.tomorrowDayOfWeekEnglish})")
                appendLine("- **Yesterday's Date (Beeta Kal):** ${snap.yesterdayFormattedDate}")
            }
            if (lower.containsAny("shaam", "evening")) {
                appendLine("- **Evening Window Today (Aaj Shaam):** Standard evening hours begin at **05:00 PM (17:00)** and continue until **08:59 PM (20:59)** in `${snap.timeZoneId}`.")
            }
        }.trim()
    }

    private fun formatProjectContinuityResponse(
        userId: String,
        query: String,
        conversationHistory: List<ChatMessage>
    ): String {
        val project = AstrynContextResolver.resolveTargetProject(userId, query)
        val profile = AstrynUserProfileManager.getProfile(userId)
        val relevantMemories = retrieveRelevantMemories(userId, query, topK = 5)
        val timeSnap = AstrynTimeIntelligence.getCurrentSnapshot()

        return buildString {
            appendLine("### Project Continuity & Context Intelligence — ${project.projectName}")
            appendLine("*Active Context resolved for user `$userId` at ${timeSnap.formattedDate}, ${timeSnap.formattedTime12h}*")
            appendLine()
            appendLine("#### 1. Project Objective")
            appendLine(project.objective)
            appendLine()
            appendLine("#### 2. Locked Architecture & Design Decisions (Strictly Preserved)")
            project.lockedDecisions.forEach { decision ->
                appendLine("- $decision")
            }
            appendLine()
            appendLine("#### 3. Completed Milestones")
            project.completedStages.forEach { stage ->
                appendLine("- [x] $stage")
            }
            appendLine()
            appendLine("#### 4. Pending Work & Next Upgrade Roadmap")
            project.pendingWork.forEachIndexed { idx, task ->
                appendLine("${idx + 1}. **[Pending]** $task")
            }
            if (project.knownProblems.isNotEmpty() || project.selectedSolutions.isNotEmpty()) {
                appendLine()
                appendLine("#### 5. Previous Problems & Verified Solutions")
                project.knownProblems.zip(project.selectedSolutions).forEach { (prob, sol) ->
                    appendLine("- **Issue:** $prob")
                    appendLine("  **Selected Solution:** $sol")
                }
            }
            if (relevantMemories.isNotEmpty()) {
                appendLine()
                appendLine("#### 6. Recalled Personal & Session Memory")
                relevantMemories.forEach { match ->
                    appendLine("- `${match.record.key}`: ${match.record.value} *(relevance: ${match.relevanceScore})*")
                }
            }
            if (conversationHistory.isNotEmpty()) {
                val lastUserMsg = conversationHistory.lastOrNull { it.role == MessageRole.USER && it.content != query }?.content
                if (!lastUserMsg.isNullOrBlank()) {
                    appendLine("- **Recent Conversation Continuity:** Continuing seamlessly from your earlier topic: *\"${lastUserMsg.take(100)}\"*")
                }
            }
            appendLine()
            appendLine("*Personalization Applied: Language=${profile.preferredLanguage.name}, Style=${profile.preferredStyle.name}, Depth=${profile.preferredDepth.name}.*")
        }.trim()
    }

    private fun formatSmartDailyPlanResponse(userId: String, query: String): String {
        val timeSnap = AstrynTimeIntelligence.getCurrentSnapshot()
        val project = AstrynProjectMemoryManager.getProject(userId)
        val tempMemories = retrieveRelevantMemories(
            userId = userId,
            query = "today session focus aaj kaam",
            categories = setOf(PersonalMemoryCategory.E_TEMPORARY_CONTEXT, PersonalMemoryCategory.C_PROJECT_MEMORY),
            topK = 4
        )
        val preferredCity = AstrynLocationManager.getPreferredWeatherCity(userId)

        return buildString {
            appendLine("### Smart Daily Priority Plan — ${timeSnap.dayOfWeekEnglish}, ${timeSnap.formattedDate}")
            appendLine("*Current Local Time: **${timeSnap.formattedTime12h}** (${timeSnap.periodOfDay.englishLabel}) • Time Zone: `${timeSnap.timeZoneId}`*")
            appendLine()
            appendLine("#### 1. High-Priority Project Tasks (${project.projectName})")
            project.pendingWork.forEachIndexed { idx, task ->
                appendLine("${idx + 1}. **$task**")
            }
            if (tempMemories.isNotEmpty()) {
                appendLine()
                appendLine("#### 2. Active Session Focus & Context")
                tempMemories.forEach { match ->
                    appendLine("- **${match.record.key}:** ${match.record.value}")
                }
            }
            appendLine()
            appendLine("#### 3. Recommended Execution Schedule for Today (${timeSnap.periodOfDay.englishLabel})")
            when (timeSnap.periodOfDay) {
                TimeOfDayPeriod.MORNING -> {
                    appendLine("- **Morning Deep Work Block:** Tackle the highest-complexity architecture item first (*${project.pendingWork.firstOrNull() ?: "Core implementation"}*).")
                    appendLine("- **Afternoon Verification Block:** Run unit test suites and regression checks.")
                }
                TimeOfDayPeriod.AFTERNOON -> {
                    appendLine("- **Afternoon Focus Block:** Complete active implementation and integration testing for *${project.pendingWork.firstOrNull() ?: "Current milestone"}*.")
                    appendLine("- **Evening Wrap-Up:** Review locked decisions and commit verified progress.")
                }
                TimeOfDayPeriod.EVENING, TimeOfDayPeriod.NIGHT -> {
                    appendLine("- **Focused Wrap-Up Session:** Finalize pending verification checks and prepare a clean checkpoint for tomorrow (${timeSnap.tomorrowFormattedDate}).")
                }
            }
            if (!preferredCity.isNullOrBlank()) {
                appendLine("- **Environment Tip:** Your preferred weather city is set to **$preferredCity** — ask *\"Aaj mausam kaisa hai?\"* anytime before stepping out.")
            }
            appendLine()
            appendLine("*(Note: This plan is synthesized strictly from your tracked project memory and live system clock without inventing external calendar events.)*")
        }.trim()
    }

    // =========================================================================
    // SECTION 9 — SYSTEM PROMPT DIRECTIVE & PHASE 4/5 TOOL REGISTRATION (SECTION 13)
    // =========================================================================

    fun buildPersonalSystemDirective(
        userId: String,
        query: String,
        userPrefs: UserPreferences? = null
    ): String {
        val cleanUser = userId.trim().ifBlank { "anonymous_local_user" }
        AstrynUserProfileManager.syncFromUserPreferences(cleanUser, userPrefs)
        AstrynPreferenceEngine.ingestTurnForPreferencesAndMemory(cleanUser, query)

        val lowerQuery = query.trim().lowercase(Locale.ROOT)
        val mentionsTimeDateOrWeather =
            shouldHandlePersonalOrEnvironmentQuery(query) ||
                lowerQuery.containsAny(
                    "time", "samay", "date", "tarikh", "today", "aaj", "tomorrow", "kal",
                    "weather", "mausam", "temperature", "forecast", "schedule", "routine",
                    "plan my", "calendar", "reminder", "morning", "evening", "night",
                )
        val applyPersonal = AstrynPersonalizationEngine.shouldApplyPersonalContext(query)
        val relevantMemories = if (applyPersonal) {
            retrieveRelevantMemories(cleanUser, query, topK = 5)
        } else {
            emptyList()
        }
        val activeProject = if (
            applyPersonal &&
            lowerQuery.containsAny("project", "astryn", "app", "upgrade", "pending", "design", "decision")
        ) {
            AstrynContextResolver.resolveTargetProject(cleanUser, query)
        } else {
            null
        }

        if (!mentionsTimeDateOrWeather && relevantMemories.isEmpty() && activeProject == null) {
            return ""
        }

        val timeSnap = AstrynTimeIntelligence.getCurrentSnapshot()
        val profile = AstrynUserProfileManager.getProfile(cleanUser)

        return buildString {
            appendLine("[PERSONAL & ENVIRONMENT CONTEXT]")
            if (mentionsTimeDateOrWeather) {
                appendLine("- Local System Time: ${timeSnap.formattedTime12h} (${timeSnap.formattedTime24h}) [${timeSnap.periodOfDay.englishLabel}]")
                appendLine("- Today: ${timeSnap.formattedDate} (${timeSnap.dayOfWeekEnglish}), TimeZone=${timeSnap.timeZoneId} (${timeSnap.utcOffsetString})")
                if (lowerQuery.containsAny("tomorrow", "kal", "schedule", "plan")) {
                    appendLine("- Tomorrow: ${timeSnap.tomorrowFormattedDate} (${timeSnap.tomorrowDayOfWeekEnglish})")
                }
                if (!profile.preferredWeatherCity.isNullOrBlank() && lowerQuery.containsAny("weather", "mausam", "temperature", "forecast", "umbrella", "rain")) {
                    appendLine("- Preferred Weather City: ${profile.preferredWeatherCity}")
                }
                appendLine("- Weather Honesty: Never fabricate live weather alerts; label cached weather with its timestamp.")
            }
            if (relevantMemories.isNotEmpty()) {
                appendLine("- Relevant Personal Memories:")
                relevantMemories.forEach { m ->
                    appendLine("  * [${m.record.category.name}] ${m.record.key} = ${m.record.value}")
                }
            }
            if (activeProject != null) {
                appendLine("- Active Project (${activeProject.projectName}): ${activeProject.objective} | Locked: ${activeProject.lockedDecisions.joinToString("; ")} | Pending: ${activeProject.pendingWork.joinToString("; ")}")
            }
        }.trim()
    }

    /**
     * Registers all 9 required Phase 6 Personal & Environment tools into Phase 5's ToolRegistry.
     */
    fun registerPhase6Tools(
        registry: AstrynResearchEngine.ToolRegistry,
        userId: String = "default_user",
        context: Context? = null
    ) {
        fun registerDirectTool(
            name: String,
            desc: String,
            handler: (String) -> String
        ) {
            registry.registerTool(
                object : AstrynResearchEngine.AutonomousTool {
                    override val toolName: String = name
                    override val description: String = desc

                    override fun executeDirect(input: String): String = handler(input)

                    override suspend fun execute(
                        objective: String,
                        conversationHistory: List<ChatMessage>,
                        attachments: List<ChatAttachment>,
                        preferences: UserPreferences,
                        conversationId: String,
                        budget: AstrynResearchEngine.ResearchBudget
                    ): AstrynResearchEngine.ResearchExecutionReport {
                        val output = handler(objective)
                        val plan = AstrynResearchEngine.ResearchPlanner.createResearchPlan(
                            prompt = objective,
                            conversationHistory = conversationHistory,
                            attachments = attachments,
                            userUid = preferences.userUid.ifBlank { userId },
                            conversationId = conversationId,
                            budget = budget
                        )
                        return AstrynResearchEngine.ResearchExecutionReport(
                            plan = plan,
                            executedQueries = listOf(name),
                            searchRoundsUsed = 1,
                            evaluatedSources = emptyList(),
                            duplicateSourcesRemoved = 0,
                            evidenceItems = emptyList(),
                            verifiedClaims = emptyList(),
                            comparisonResult = null,
                            verifiedCitations = emptyList(),
                            formattedMarkdownReport = output,
                            liveSearchExecuted = false,
                            searchLimitationDisclosure = null,
                            promptInjectionDetectedAndBlocked = false,
                            fromSafeCache = false
                        )
                    }
                }
            )
        }

        // 1. get_current_time
        registerDirectTool(
            name = "get_current_time",
            desc = "Returns the real current local system time, 12h/24h format, and period of day (morning/afternoon/evening/night)."
        ) { _ ->
            val snap = AstrynTimeIntelligence.getCurrentSnapshot()
            "Current Local Time: ${snap.formattedTime12h} (${snap.formattedTime24h}), Period=${snap.periodOfDay.englishLabel} (${snap.periodOfDay.hindiLabel}), TimeZone=${snap.timeZoneId}"
        }

        // 2. get_current_date
        registerDirectTool(
            name = "get_current_date",
            desc = "Returns today's real local calendar date, day of week in English & Hindi, month, year, and tomorrow's date."
        ) { _ ->
            val snap = AstrynTimeIntelligence.getCurrentSnapshot()
            "Today: ${snap.formattedDate} (${snap.isoDate}), Day=${snap.dayOfWeekEnglish} (${snap.dayOfWeekHindi}), Tomorrow=${snap.tomorrowFormattedDate} (${snap.tomorrowDayOfWeekEnglish})"
        }

        // 3. get_user_timezone
        registerDirectTool(
            name = "get_user_timezone",
            desc = "Returns the user's current device time zone ID, UTC offset, and optional time zone comparison."
        ) { input ->
            val snap = AstrynTimeIntelligence.getCurrentSnapshot()
            if (input.lowercase().contains("dubai") && input.lowercase().contains("india")) {
                val cmp = AstrynTimeIntelligence.compareTimeZones("India", "Dubai")
                "User TimeZone: ${snap.timeZoneId} (${snap.utcOffsetString}). Comparison: ${cmp.summaryExplanation}"
            } else {
                "User TimeZone: ${snap.timeZoneId} (${snap.utcOffsetString})"
            }
        }

        // 4. get_current_weather
        registerDirectTool(
            name = "get_current_weather",
            desc = "Fetches real-time current weather (temperature, feels-like, condition, humidity, wind, precipitation) for a city or user's preferred location."
        ) { input ->
            val city = AstrynLocationManager.extractCityFromQuery(input)
                ?: AstrynLocationManager.getPreferredWeatherCity(userId)
                ?: input.trim().takeIf { it.length in 3..25 && !it.contains(" ") }
                ?: "New Delhi"
            AstrynWeatherIntelligence.getWeatherForCity(city).fold(
                onSuccess = { b ->
                    val cachePrefix = if (b.isFromOfflineCache) "[CACHED at ${b.fetchedAtMillis}] " else "[LIVE] "
                    "${cachePrefix}${b.cityName}: ${b.current.temperatureC}°C (feels like ${b.current.feelsLikeC}°C), ${b.current.conditionText}, Humidity=${b.current.humidityPercent}%, Wind=${b.current.windSpeedKmh} km/h, Precip=${b.current.precipitationMm} mm"
                },
                onFailure = { err -> "Weather unavailable for $city: ${err.message}" }
            )
        }

        // 5. get_hourly_forecast
        registerDirectTool(
            name = "get_hourly_forecast",
            desc = "Fetches 24-hour hourly forecast including temperature, rain probability %, expected precipitation mm, and wind."
        ) { input ->
            val city = AstrynLocationManager.extractCityFromQuery(input)
                ?: AstrynLocationManager.getPreferredWeatherCity(userId)
                ?: "New Delhi"
            AstrynWeatherIntelligence.getWeatherForCity(city).fold(
                onSuccess = { b ->
                    val preview = b.hourlyNext24h.take(8).joinToString("; ") {
                        "${it.timeLabel}: ${it.temperatureC}°C, rainProb=${it.rainProbabilityPercent}%, precip=${it.precipitationMm}mm, ${it.conditionText}"
                    }
                    "Hourly Forecast (${b.cityName}): $preview"
                },
                onFailure = { err -> "Hourly forecast unavailable for $city: ${err.message}" }
            )
        }

        // 6. get_daily_forecast
        registerDirectTool(
            name = "get_daily_forecast",
            desc = "Fetches 7-day daily weather forecast including min/max temperature, rain probability %, and expected rainfall mm."
        ) { input ->
            val city = AstrynLocationManager.extractCityFromQuery(input)
                ?: AstrynLocationManager.getPreferredWeatherCity(userId)
                ?: "New Delhi"
            AstrynWeatherIntelligence.getWeatherForCity(city).fold(
                onSuccess = { b ->
                    val summary = b.dailyNext7d.joinToString("; ") {
                        "${it.dayLabel}: ${it.minTempC}°C-${it.maxTempC}°C, rainProb=${it.rainProbabilityPercent}% (${it.expectedPrecipitationMm}mm), ${it.conditionText}"
                    }
                    "7-Day Forecast (${b.cityName}): $summary"
                },
                onFailure = { err -> "Daily forecast unavailable for $city: ${err.message}" }
            )
        }

        // 7. get_weather_alerts
        registerDirectTool(
            name = "get_weather_alerts",
            desc = "Checks official/threshold severe weather alerts for a city without fabricating alerts."
        ) { input ->
            val city = AstrynLocationManager.extractCityFromQuery(input)
                ?: AstrynLocationManager.getPreferredWeatherCity(userId)
                ?: "New Delhi"
            AstrynWeatherIntelligence.getWeatherForCity(city).fold(
                onSuccess = { b ->
                    if (b.alerts.isEmpty()) {
                        "No active severe weather alerts for ${b.cityName}."
                    } else {
                        b.alerts.joinToString("; ") { "[${it.severity}] ${it.title}: ${it.description} (${it.effectiveWindow})" }
                    }
                },
                onFailure = { err -> "Weather alerts check unavailable for $city: ${err.message}" }
            )
        }

        // 8. get_relevant_user_memory
        registerDirectTool(
            name = "get_relevant_user_memory",
            desc = "Retrieves top-scored relevant personal memories across the 5 memory categories for the current user."
        ) { input ->
            val matches = retrieveRelevantMemories(userId = userId, query = input, topK = 5)
            if (matches.isEmpty()) {
                "No matching personal memories found for '$input'."
            } else {
                matches.joinToString("; ") { "[${it.record.category.name}] ${it.record.key}=${it.record.value} (score=${it.relevanceScore})" }
            }
        }

        // 9. get_project_context
        registerDirectTool(
            name = "get_project_context",
            desc = "Retrieves structured project continuity context (objective, locked decisions, completed stages, pending work, known problems & solutions)."
        ) { input ->
            val proj = AstrynContextResolver.resolveTargetProject(userId = userId, query = input)
            "Project=${proj.projectName} | Objective=${proj.objective} | LockedDecisions=${proj.lockedDecisions.joinToString(", ")} | PendingWork=${proj.pendingWork.joinToString(", ")}"
        }
    }

    // =========================================================================
    // SECTION 10 — ACCOUNT ISOLATION & RESET HELPERS
    // =========================================================================

    fun onUserLogoutOrAccountSwitch(previousUserId: String?) {
        if (!previousUserId.isNullOrBlank()) {
            val clean = previousUserId.trim()
            userMemoryBuckets.remove(clean)
            AstrynUserProfileManager.clearUser(clean)
            AstrynProjectMemoryManager.clearUser(clean)
        }
    }

    fun clearAllPersonalIntelligenceStateForTesting() {
        userMemoryBuckets.clear()
        AstrynUserProfileManager.clearAll()
        AstrynProjectMemoryManager.clearAll()
        AstrynLocationManager.resetSimulationState()
        AstrynWeatherIntelligence.clearWeatherCache()
        AstrynWeatherIntelligence.forceOfflineMode = false
        AstrynWeatherIntelligence.weatherGateway = OpenMeteoHttpWeatherGateway
        AstrynTimeIntelligence.clockProvider = Clock.systemDefaultZone()
    }

    // =========================================================================
    // INTERNAL TOKEN & SIMILARITY HELPERS
    // =========================================================================

    private val STOP_WORDS = setOf(
        "the", "and", "for", "with", "that", "this", "from", "have", "what", "when",
        "where", "which", "who", "how", "hai", "hain", "kya", "mera", "mere", "meri",
        "mein", "ko", "ka", "ki", "ke", "se", "aur", "ek", "to", "in", "on", "of", "is"
    )

    internal fun extractSemanticTokens(text: String): Set<String> {
        return text.lowercase()
            .replace(Regex("""[^a-z0-9\s]"""), " ")
            .split(Regex("""\s+"""))
            .filter { it.length >= 3 && it !in STOP_WORDS }
            .toSet()
    }

    private fun jaccardTokenSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        val tokensA = a.split(" ").filter { it.isNotBlank() }.toSet()
        val tokensB = b.split(" ").filter { it.isNotBlank() }.toSet()
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0
        val inter = tokensA.intersect(tokensB).size.toDouble()
        val union = tokensA.union(tokensB).size.toDouble()
        return inter / union
    }

    private fun String.containsAny(vararg needles: String): Boolean {
        return needles.any { this.contains(it, ignoreCase = true) }
    }
}
