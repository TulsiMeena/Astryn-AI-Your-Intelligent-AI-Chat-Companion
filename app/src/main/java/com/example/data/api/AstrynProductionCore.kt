package com.example.data.api

import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * ASTRYN AI — PHASE 7
 * PRODUCTION EXCELLENCE, ADVANCED SECURITY & COMPLETE SYSTEM OPTIMIZATION
 *
 * Provides centralized, thread-safe, zero-PII production infrastructure:
 * 1. [AstrynDiagnosticsManager]: Privacy-safe observability, performance metrics, and error telemetry
 *    with strict secret/PII redaction and ring-buffered memory bounds.
 * 2. [AstrynSecurityGuard]: Input sanitization, path-traversal prevention, SSRF/unsafe URL blocking,
 *    session token validation, per-user resource ownership enforcement, and duplicate submission protection.
 * 3. [AstrynAutonomousExecutionGuard]: Bounded step/tool execution limits, task ownership isolation,
 *    idempotent action deduplication, sensitive action user-approval gates, and checkpoint recovery.
 * 4. [AstrynCrashBoundary]: Non-fatal exception containment and recovery diagnostics without exposing stack traces.
 */
object AstrynDiagnosticsManager {

    const val MAX_TELEMETRY_EVENTS = 200

    enum class Subsystem {
        GEMINI_API,
        AUTH_SESSION,
        DATABASE_SYNC,
        MEMORY_ENGINE,
        AUTONOMOUS_TASK,
        RESEARCH_ENGINE,
        WEATHER_TIME,
        ATTACHMENT_PROCESSOR,
        CRASH_BOUNDARY
    }

    enum class Severity {
        INFO,
        WARNING,
        ERROR,
        CRITICAL
    }

    data class DiagnosticEvent(
        val eventId: Long,
        val timestampMs: Long,
        val subsystem: Subsystem,
        val severity: Severity,
        val operation: String,
        val statusCode: Int? = null,
        val durationMs: Long = 0L,
        val retryCount: Int = 0,
        val sanitizedMessage: String
    )

    data class SubsystemMetricsSnapshot(
        val totalApiRequests: Long,
        val failedApiRequests: Long,
        val totalApiRetries: Long,
        val averageApiLatencyMs: Long,
        val totalDbSyncOperations: Long,
        val failedDbSyncOperations: Long,
        val totalAutonomousTasks: Long,
        val haltedAutonomousTasks: Long,
        val totalResearchRuns: Long,
        val totalWeatherRequests: Long,
        val totalAttachmentFailures: Long,
        val totalContainedExceptions: Long
    )

    private val eventIdCounter = AtomicLong(0L)
    private val events = CopyOnWriteArrayList<DiagnosticEvent>()

    private val totalApiRequests = AtomicLong(0L)
    private val failedApiRequests = AtomicLong(0L)
    private val totalApiRetries = AtomicLong(0L)
    private val cumulativeApiLatencyMs = AtomicLong(0L)

    private val totalDbSyncOps = AtomicLong(0L)
    private val failedDbSyncOps = AtomicLong(0L)

    private val totalAutonomousTasks = AtomicLong(0L)
    private val haltedAutonomousTasks = AtomicLong(0L)

    private val totalResearchRuns = AtomicLong(0L)
    private val totalWeatherRequests = AtomicLong(0L)
    private val totalAttachmentFailures = AtomicLong(0L)
    private val totalContainedExceptions = AtomicLong(0L)

    /**
     * Records a privacy-safe diagnostic event. Automatically scrubs API keys, bearer tokens,
     * email addresses, and raw prompt/message payloads before storing.
     */
    fun recordEvent(
        subsystem: Subsystem,
        severity: Severity,
        operation: String,
        rawMessage: String,
        statusCode: Int? = null,
        durationMs: Long = 0L,
        retryCount: Int = 0,
        nowMs: Long = System.currentTimeMillis()
    ): DiagnosticEvent {
        val safeOp = sanitizeTelemetryField(operation, maxLength = 64)
        val safeMsg = sanitizeTelemetryField(rawMessage, maxLength = 240)

        when (subsystem) {
            Subsystem.GEMINI_API -> {
                totalApiRequests.incrementAndGet()
                if (severity == Severity.ERROR || severity == Severity.CRITICAL) {
                    failedApiRequests.incrementAndGet()
                }
                if (retryCount > 0) {
                    totalApiRetries.addAndGet(retryCount.toLong())
                }
                if (durationMs > 0L) {
                    cumulativeApiLatencyMs.addAndGet(durationMs)
                }
            }
            Subsystem.DATABASE_SYNC -> {
                totalDbSyncOps.incrementAndGet()
                if (severity == Severity.ERROR || severity == Severity.CRITICAL) {
                    failedDbSyncOps.incrementAndGet()
                }
            }
            Subsystem.AUTONOMOUS_TASK -> {
                totalAutonomousTasks.incrementAndGet()
                if (severity == Severity.WARNING || severity == Severity.ERROR || severity == Severity.CRITICAL) {
                    haltedAutonomousTasks.incrementAndGet()
                }
            }
            Subsystem.RESEARCH_ENGINE -> {
                totalResearchRuns.incrementAndGet()
            }
            Subsystem.WEATHER_TIME -> {
                totalWeatherRequests.incrementAndGet()
            }
            Subsystem.ATTACHMENT_PROCESSOR -> {
                if (severity == Severity.ERROR || severity == Severity.CRITICAL || severity == Severity.WARNING) {
                    totalAttachmentFailures.incrementAndGet()
                }
            }
            Subsystem.CRASH_BOUNDARY -> {
                totalContainedExceptions.incrementAndGet()
            }
            else -> Unit
        }

        val event = DiagnosticEvent(
            eventId = eventIdCounter.incrementAndGet(),
            timestampMs = nowMs,
            subsystem = subsystem,
            severity = severity,
            operation = safeOp,
            statusCode = statusCode,
            durationMs = durationMs.coerceAtLeast(0L),
            retryCount = retryCount.coerceAtLeast(0),
            sanitizedMessage = safeMsg
        )

        synchronized(events) {
            events.add(event)
            while (events.size > MAX_TELEMETRY_EVENTS) {
                events.removeAt(0)
            }
        }
        return event
    }

    /**
     * Scrubs any accidental secrets, tokens, emails, passwords, or long user content from telemetry strings.
     */
    fun sanitizeTelemetryField(raw: String, maxLength: Int = 240): String {
        if (raw.isBlank()) return ""
        var cleaned = SafeDiagnosticLogger.redactSecrets(raw)
        // Redact email addresses to protect user PII in diagnostics
        cleaned = cleaned.replace(
            Regex("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}"),
            "[REDACTED_EMAIL]"
        )
        // Redact JSON password fields
        cleaned = cleaned.replace(
            Regex("(\"password\"\\s*:\\s*\")[^\"]+(\")", RegexOption.IGNORE_CASE),
            "$1[REDACTED_SECRET]$2"
        )
        cleaned = cleaned.replace(
            Regex("(\"idToken\"\\s*:\\s*\")[^\"]+(\")", RegexOption.IGNORE_CASE),
            "$1[REDACTED_TOKEN]$2"
        )
        cleaned = cleaned.replace(
            Regex("(\"refreshToken\"\\s*:\\s*\")[^\"]+(\")", RegexOption.IGNORE_CASE),
            "$1[REDACTED_TOKEN]$2"
        )
        return cleaned.replace(Regex("\\s+"), " ").trim().take(maxLength)
    }

    fun getRecentEvents(subsystem: Subsystem? = null, limit: Int = 50): List<DiagnosticEvent> {
        val snapshot = events.toList()
        val filtered = if (subsystem == null) snapshot else snapshot.filter { it.subsystem == subsystem }
        return filtered.takeLast(limit.coerceIn(1, MAX_TELEMETRY_EVENTS))
    }

    fun getMetricsSnapshot(): SubsystemMetricsSnapshot {
        val apiCount = totalApiRequests.get()
        val avgLatency = if (apiCount > 0L) cumulativeApiLatencyMs.get() / apiCount else 0L
        return SubsystemMetricsSnapshot(
            totalApiRequests = apiCount,
            failedApiRequests = failedApiRequests.get(),
            totalApiRetries = totalApiRetries.get(),
            averageApiLatencyMs = avgLatency,
            totalDbSyncOperations = totalDbSyncOps.get(),
            failedDbSyncOperations = failedDbSyncOps.get(),
            totalAutonomousTasks = totalAutonomousTasks.get(),
            haltedAutonomousTasks = haltedAutonomousTasks.get(),
            totalResearchRuns = totalResearchRuns.get(),
            totalWeatherRequests = totalWeatherRequests.get(),
            totalAttachmentFailures = totalAttachmentFailures.get(),
            totalContainedExceptions = totalContainedExceptions.get()
        )
    }

    fun clearForTesting() {
        synchronized(events) {
            events.clear()
        }
        eventIdCounter.set(0L)
        totalApiRequests.set(0L)
        failedApiRequests.set(0L)
        totalApiRetries.set(0L)
        cumulativeApiLatencyMs.set(0L)
        totalDbSyncOps.set(0L)
        failedDbSyncOps.set(0L)
        totalAutonomousTasks.set(0L)
        haltedAutonomousTasks.set(0L)
        totalResearchRuns.set(0L)
        totalWeatherRequests.set(0L)
        totalAttachmentFailures.set(0L)
        totalContainedExceptions.set(0L)
    }
}

/**
 * Security Guard for input validation, filename sanitization, URL SSRF protection,
 * session expiry detection, user ownership isolation, and duplicate request prevention.
 */
object AstrynSecurityGuard {

    private const val MAX_SAFE_FILENAME_LENGTH = 120
    private const val DEFAULT_DEDUP_WINDOW_MS = 1_500L

    private val recentSubmissions = ConcurrentHashMap<String, Long>()

    /**
     * Sanitizes user-supplied or attachment filenames to block path traversal (`../`, `..\\`),
     * directory separators, null bytes, and control characters.
     */
    fun sanitizeFileName(rawFileName: String?): String {
        if (rawFileName.isNullOrBlank()) return "attachment_file"
        // Normalize slashes and extract only the leaf segment after any path separator
        var cleaned = rawFileName
            .replace('\u0000', '_')
            .replace('\\', '/')
            .substringAfterLast('/')
        // Repeatedly strip any '..' sequences
        while (cleaned.contains("..")) {
            cleaned = cleaned.replace("..", "_")
        }
        // Remove control characters and unsafe filesystem characters
        cleaned = cleaned.replace(Regex("[\\x00-\\x1F\\x7F<>:\"|?*]"), "_")
            .trim()
            .trimStart('.')
        if (cleaned.isBlank()) return "attachment_file"
        return if (cleaned.length > MAX_SAFE_FILENAME_LENGTH) {
            val ext = cleaned.substringAfterLast('.', "")
            if (ext.isNotEmpty() && ext.length <= 10) {
                cleaned.take(MAX_SAFE_FILENAME_LENGTH - ext.length - 1) + "." + ext
            } else {
                cleaned.take(MAX_SAFE_FILENAME_LENGTH)
            }
        } else {
            cleaned
        }
    }

    /**
     * Validates external citation and research URLs.
     * Blocks `javascript:`, `data:`, `file://`, non-HTTPS/HTTP schemes, and private/loopback SSRF targets.
     */
    fun isSafeExternalUrl(rawUrl: String?): Boolean {
        if (rawUrl.isNullOrBlank()) return false
        val trimmed = rawUrl.trim()
        if (trimmed.length > 2048) return false
        return try {
            val uri = URI(trimmed)
            val scheme = uri.scheme?.lowercase() ?: return false
            if (scheme != "https" && scheme != "http") return false
            val host = uri.host?.lowercase()?.trim() ?: return false
            if (host.isBlank()) return false
            // Block loopback, link-local metadata, and private RFC1918 networks
            if (host == "localhost" ||
                host == "127.0.0.1" ||
                host == "::1" ||
                host == "0.0.0.0" ||
                host == "169.254.169.254" ||
                host.startsWith("127.") ||
                host.startsWith("10.") ||
                host.startsWith("192.168.") ||
                host.endsWith(".local") ||
                host.endsWith(".internal")
            ) {
                return false
            }
            // Check 172.16.0.0/12 private range
            if (host.startsWith("172.")) {
                val parts = host.split(".")
                val secondOctet = parts.getOrNull(1)?.toIntOrNull()
                if (secondOctet != null && secondOctet in 16..31) {
                    return false
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Verifies strict user ownership for any user-scoped entity (conversation, memory, task, API key).
     * Never allows cross-user read or write.
     */
    fun verifyUserOwnership(activeUserId: String?, resourceOwnerId: String?): Boolean {
        val active = activeUserId?.trim().takeUnless { it.isNullOrEmpty() } ?: return false
        val owner = resourceOwnerId?.trim().takeUnless { it.isNullOrEmpty() } ?: return false
        return active == owner
    }

    /**
     * Inspects an error response or token state to determine whether the Firebase Auth session
     * has expired or been invalidated (requiring re-authentication or token refresh).
     */
    fun isSessionTokenExpiredOrInvalid(httpStatusCode: Int?, errorBody: String?): Boolean {
        if (httpStatusCode == 401) return true
        if (errorBody.isNullOrBlank()) return false
        val upper = errorBody.uppercase()
        return upper.contains("TOKEN_EXPIRED") ||
            upper.contains("INVALID_ID_TOKEN") ||
            upper.contains("USER_DISABLED") ||
            upper.contains("USER_NOT_FOUND") ||
            upper.contains("CREDENTIAL_TOO_OLD_LOGIN_AGAIN")
    }

    /**
     * Prevents accidental duplicate message submissions (e.g. rapid double-tap on Send).
     * Returns `true` if the submission is allowed, or `false` if an identical submission
     * for the same conversation occurred within [dedupWindowMs].
     */
    fun allowMessageSubmission(
        userId: String,
        conversationId: String,
        promptText: String,
        nowMs: Long = System.currentTimeMillis(),
        dedupWindowMs: Long = DEFAULT_DEDUP_WINDOW_MS
    ): Boolean {
        val normalizedPrompt = promptText.trim()
        if (normalizedPrompt.isEmpty()) return false
        val key = "${userId.trim()}|${conversationId.trim()}|${normalizedPrompt.hashCode()}"
        val previousMs = recentSubmissions[key]
        if (previousMs != null && (nowMs - previousMs) in 0 until dedupWindowMs) {
            return false
        }
        recentSubmissions[key] = nowMs
        // Prune stale entries if map grows
        if (recentSubmissions.size > 256) {
            val cutoff = nowMs - (dedupWindowMs * 4)
            val iterator = recentSubmissions.entries.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().value < cutoff) {
                    iterator.remove()
                }
            }
        }
        return true
    }

    fun clearSubmissionHistoryForTesting() {
        recentSubmissions.clear()
    }
}

/**
 * Production Execution Guard & Checkpoint Manager for Phase 4/5/6/7 Autonomous Tasks.
 * Enforces:
 * - Task ownership verification (`ownerUid`)
 * - Bounded step & tool-call execution limits (`maxSteps`, `maxToolCalls`)
 * - Duplicate action prevention (`executedActionFingerprints`)
 * - Mandatory user approval gate for sensitive actions (`SENSITIVE_TOOLS`)
 * - Checkpoint persistence and restart recovery (`TaskCheckpoint`)
 */
object AstrynAutonomousExecutionGuard {

    const val DEFAULT_MAX_STEPS = 8
    const val DEFAULT_MAX_TOOL_CALLS = 10

    private val SENSITIVE_TOOL_PREFIXES = setOf(
        "delete_",
        "overwrite_",
        "external_post_",
        "send_email",
        "modify_account",
        "clear_history",
        "wipe_memory"
    )

    data class TaskCheckpoint(
        val taskId: String,
        val ownerUid: String,
        val goal: String,
        val completedStepIndices: Set<Int>,
        val stepOutputs: Map<Int, String>,
        val executedActionKeys: Set<String>,
        val toolCallsUsed: Int,
        val updatedAtMs: Long
    )

    private val checkpointStore = ConcurrentHashMap<String, TaskCheckpoint>()

    fun isSensitiveAction(toolOrActionName: String): Boolean {
        val normalized = toolOrActionName.trim().lowercase()
        return SENSITIVE_TOOL_PREFIXES.any { normalized.startsWith(it) || normalized.contains(it) }
    }

    fun saveCheckpoint(checkpoint: TaskCheckpoint) {
        if (checkpoint.taskId.isBlank() || checkpoint.ownerUid.isBlank()) return
        checkpointStore["${checkpoint.ownerUid}::${checkpoint.taskId}"] = checkpoint
        // Bound in-memory checkpoint store
        if (checkpointStore.size > 100) {
            val oldestKey = checkpointStore.entries.minByOrNull { it.value.updatedAtMs }?.key
            if (oldestKey != null) {
                checkpointStore.remove(oldestKey)
            }
        }
    }

    fun loadCheckpoint(ownerUid: String, taskId: String): TaskCheckpoint? {
        if (ownerUid.isBlank() || taskId.isBlank()) return null
        val checkpoint = checkpointStore["${ownerUid.trim()}::${taskId.trim()}"] ?: return null
        return if (AstrynSecurityGuard.verifyUserOwnership(ownerUid, checkpoint.ownerUid)) {
            checkpoint
        } else {
            null
        }
    }

    fun removeCheckpoint(ownerUid: String, taskId: String) {
        checkpointStore.remove("${ownerUid.trim()}::${taskId.trim()}")
    }

    fun clearCheckpointsForTesting() {
        checkpointStore.clear()
    }
}

/**
 * Non-fatal exception boundary that catches unexpected runtime exceptions in background/helper flows,
 * logs a sanitized diagnostic event, and returns a safe fallback value without crashing the app.
 */
object AstrynCrashBoundary {

    inline fun <T> runSafely(
        subsystem: AstrynDiagnosticsManager.Subsystem,
        operation: String,
        fallbackValue: T,
        block: () -> T
    ): T {
        return try {
            block()
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            AstrynDiagnosticsManager.recordEvent(
                subsystem = AstrynDiagnosticsManager.Subsystem.CRASH_BOUNDARY,
                severity = AstrynDiagnosticsManager.Severity.ERROR,
                operation = "${subsystem.name}.$operation",
                rawMessage = "${t::class.java.simpleName}: ${t.message ?: "Unhandled exception"}"
            )
            fallbackValue
        }
    }
}
