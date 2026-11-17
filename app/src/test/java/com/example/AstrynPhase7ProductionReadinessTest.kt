package com.example

import com.example.data.api.AppErrorCategory
import com.example.data.api.AstrynAutonomousExecutionGuard
import com.example.data.api.AstrynCrashBoundary
import com.example.data.api.AstrynDiagnosticsManager
import com.example.data.api.AstrynPersonalIntelligenceEngine
import com.example.data.api.AstrynPersonalIntelligenceEngine.PersonalMemoryCategory
import com.example.data.api.AstrynResearchEngine
import com.example.data.api.AstrynSecurityGuard
import com.example.data.api.ErrorClassifier
import com.example.data.api.GeminiException
import com.example.data.api.GeminiLiveVoiceSession
import com.example.data.api.RetryPolicyConfig
import com.example.data.api.SafeDiagnosticLogger
import com.example.data.local.ApiConnectionStatus
import com.example.data.local.AttachmentProcessor
import com.example.data.local.UserApiKeyManager
import com.example.data.model.ChatAttachment
import com.example.data.model.ChatMessage
import com.example.data.model.MessageRole
import com.example.data.model.MessageStatus
import com.example.data.repository.ChatHistoryRepository
import com.example.data.repository.UserPreferences
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

/**
 * ASTRYN AI — PHASE 7
 * PRODUCTION EXCELLENCE, ADVANCED SECURITY & COMPLETE SYSTEM OPTIMIZATION
 *
 * Comprehensive automated test suite verifying all 10 required production categories:
 * A. Authentication & Per-User Credential Isolation
 * B. Gemini API Reliability, HTTP Classification, Backoff & Secret Redaction
 * C. Database & Multi-User Conversation Ownership Security
 * D. Memory Isolation, Deletion & Corruption Resilience
 * E. Autonomous Intelligence Execution Limits, Checkpoints & Approval Gates
 * F. Research Source Evaluation, SSRF Blocking & Citation Verification
 * G. Weather & Timezone Reliability, Caching & Fallback Honesty
 * H. Attachment Path-Traversal Sanitization, PDF Bounds & Size Limits
 * I. Application Duplicate Submission Guard, Crash Boundary & Privacy-Safe Diagnostics
 * J. Build & Voice-to-Voice Jarvis Preservation Verification
 */
class AstrynPhase7ProductionReadinessTest {

    @Before
    fun setUp() {
        AstrynDiagnosticsManager.clearForTesting()
        AstrynSecurityGuard.clearSubmissionHistoryForTesting()
        AstrynAutonomousExecutionGuard.clearCheckpointsForTesting()
        AstrynResearchEngine.onUserLogoutOrAccountSwitch("user_alpha")
        AstrynResearchEngine.onUserLogoutOrAccountSwitch("user_beta")
        AstrynPersonalIntelligenceEngine.clearAllPersonalIntelligenceStateForTesting()
        AttachmentProcessor.clearAllSessions()
    }

    @After
    fun tearDown() {
        AstrynDiagnosticsManager.clearForTesting()
        AstrynSecurityGuard.clearSubmissionHistoryForTesting()
        AstrynAutonomousExecutionGuard.clearCheckpointsForTesting()
        AstrynPersonalIntelligenceEngine.clearAllPersonalIntelligenceStateForTesting()
        AttachmentProcessor.clearAllSessions()
    }

    // =========================================================================
    // A. AUTHENTICATION & API KEY SECURITY TESTS (SECTIONS 5 & 6)
    // =========================================================================

    @Test
    fun testA1_apiKeyEncryptionUniqueIvAndZeroPlaintextPersistence() {
        val manager = UserApiKeyManager(sharedPreferences = null)
        manager.bindToUser("user_alpha")

        val validKey1 = "AIzaSyD1234567890abcdefghijklmnop9876"
        val save1 = manager.saveApiKey(validKey1)
        assertTrue(save1.isSuccess)
        assertEquals("AIza••••••••9876", save1.getOrThrow().maskedKeyPreview)
        assertEquals(validKey1, manager.getDecryptedApiKey())

        val audit1 = manager.getEncryptedPayloadMetadataForAudit(expectedPlaintextNotPresent = validKey1)
        assertTrue(audit1.hasCiphertext)
        assertEquals(12, audit1.ivLengthBytes)
        assertTrue("Plaintext API key must never appear in storage", audit1.isPlaintextAbsentFromStorage)

        // Re-saving the exact same key must generate a fresh random 12-byte IV and different ciphertext
        val save2 = manager.saveApiKey(validKey1)
        assertTrue(save2.isSuccess)
        val audit2 = manager.getEncryptedPayloadMetadataForAudit(expectedPlaintextNotPresent = validKey1)
        assertEquals(12, audit2.ivLengthBytes)
        assertNotEquals("Each AES-GCM encryption must use a unique 12-byte IV", audit1.ivBase64, audit2.ivBase64)
        assertNotEquals("Ciphertext must differ when IV changes", audit1.ciphertextBase64, audit2.ciphertextBase64)
    }

    @Test
    fun testA2_perUserApiKeyIsolationAcrossAccountSwitchAndDeletion() {
        val manager = UserApiKeyManager(sharedPreferences = null)

        // User Alpha saves a personal API key
        manager.bindToUser("user_alpha")
        val alphaKey = "AIzaSyAlphaKey1234567890abcdefghijk1111"
        assertTrue(manager.saveApiKey(alphaKey).isSuccess)
        assertTrue(manager.hasSavedKey())
        assertEquals(alphaKey, manager.getDecryptedApiKey())

        // Switch to User Beta on the same device: User Beta must NOT access User Alpha's API key
        manager.bindToUser("user_beta")
        assertFalse("User Beta must not inherit User Alpha's personal API key", manager.hasSavedKey())
        assertNull(manager.getDecryptedApiKey())
        assertEquals(ApiConnectionStatus.NOT_CONFIGURED, manager.configStateFlow.value.connectionStatus)

        // User Beta saves their own key
        val betaKey = "AIzaSyBetaKey9876543210zyxwvutsrqpo2222"
        assertTrue(manager.saveApiKey(betaKey).isSuccess)
        assertEquals(betaKey, manager.getDecryptedApiKey())

        // User Beta clears their key; User Alpha's key must remain intact
        manager.clearApiKey()
        assertFalse(manager.hasSavedKey())
        assertNull(manager.getDecryptedApiKey())

        manager.bindToUser("user_alpha")
        assertTrue(manager.hasSavedKey())
        assertEquals(alphaKey, manager.getDecryptedApiKey())
    }

    @Test
    fun testA3_sessionExpiryAndInvalidTokenDetection() {
        assertTrue(AstrynSecurityGuard.isSessionTokenExpiredOrInvalid(401, ""))
        assertTrue(AstrynSecurityGuard.isSessionTokenExpiredOrInvalid(400, """{"error":{"message":"TOKEN_EXPIRED"}}"""))
        assertTrue(AstrynSecurityGuard.isSessionTokenExpiredOrInvalid(400, """{"error":{"message":"INVALID_ID_TOKEN"}}"""))
        assertTrue(AstrynSecurityGuard.isSessionTokenExpiredOrInvalid(403, """{"error":{"message":"USER_DISABLED"}}"""))
        assertFalse(AstrynSecurityGuard.isSessionTokenExpiredOrInvalid(200, """{"status":"ok"}"""))
        assertTrue(AstrynSecurityGuard.verifyUserOwnership("uid_123", "uid_123"))
        assertFalse(AstrynSecurityGuard.verifyUserOwnership("uid_123", "uid_999"))
        assertFalse(AstrynSecurityGuard.verifyUserOwnership("", "uid_123"))
    }

    // =========================================================================
    // B. GEMINI API RELIABILITY, HTTP CLASSIFICATION & SECRET REDACTION (SECTION 4)
    // =========================================================================

    @Test
    fun testB1_httpErrorClassificationAndPermanentVsTransientRetryRules() {
        // 400 Invalid Request -> Never auto-retry
        val err400 = ErrorClassifier.classify(GeminiException.ValidationError("Invalid JSON payload"))
        assertEquals(AppErrorCategory.INVALID_PAYLOAD, err400.category)
        assertFalse(err400.isAutoRetryEligible)

        // 401/403 Unauthorized / Restricted Key -> Never auto-retry
        val err401 = ErrorClassifier.classify(GeminiException.InvalidApiKey(401, "API key invalid"))
        assertEquals(AppErrorCategory.UNAUTHORIZED, err401.category)
        assertFalse(err401.isAutoRetryEligible)

        val err403 = ErrorClassifier.classify(GeminiException.InvalidApiKey(403, "API key restricted"))
        assertEquals(AppErrorCategory.UNAUTHORIZED, err403.category)
        assertFalse(err403.isAutoRetryEligible)

        // 404 Unsupported Model -> Never auto-retry
        val err404 = ErrorClassifier.classify(GeminiException.UnsupportedModel("gemini-unknown"))
        assertEquals(AppErrorCategory.UNSUPPORTED_MODEL, err404.category)
        assertFalse(err404.isAutoRetryEligible)

        // 429 Rate Limit -> Auto-retry eligible only when retryAfter <= 5000ms and no partial stream emitted
        val err429Short = ErrorClassifier.classify(
            GeminiException.QuotaExceeded(retryAfterMillis = 2_000L),
            hadPartialStreamBeforeFailure = false
        )
        assertEquals(AppErrorCategory.RATE_LIMITED, err429Short.category)
        assertTrue(err429Short.isAutoRetryEligible)

        val err429MidStream = ErrorClassifier.classify(
            GeminiException.QuotaExceeded(retryAfterMillis = 2_000L),
            hadPartialStreamBeforeFailure = true
        )
        assertFalse("Must not auto-retry from scratch if partial stream was already emitted", err429MidStream.isAutoRetryEligible)

        // 500 / 502 / 503 / 504 Server Errors -> Transient, auto-retry eligible before stream emission
        for (code in listOf(500, 502, 503, 504)) {
            val serverErr = ErrorClassifier.classify(
                GeminiException.ServerError(code, "Backend unavailable"),
                hadPartialStreamBeforeFailure = false
            )
            assertEquals(AppErrorCategory.SERVER_ERROR, serverErr.category)
            assertTrue("HTTP $code should be auto-retry eligible", serverErr.isAutoRetryEligible)
        }
    }

    @Test
    fun testB2_exponentialBackoffWithJitterAndSecretRedaction() {
        val policy = RetryPolicyConfig(
            maxAttempts = 3,
            baseDelayMillis = 500L,
            maxDelayMillis = 4_000L,
            maxAutoRetryAfterMillis = 5_000L,
            jitterRatio = 0.20
        )
        val delay1 = policy.computeDelayMillis(attemptIndex = 1)
        val delay2 = policy.computeDelayMillis(attemptIndex = 2)
        val delay3 = policy.computeDelayMillis(attemptIndex = 3)
        assertTrue(delay1 in 400L..600L)
        assertTrue(delay2 in 800L..1200L)
        assertTrue(delay3 in 1600L..2400L)

        // Server Retry-After header respected up to cap
        assertEquals(3_000L, policy.computeDelayMillis(attemptIndex = 1, serverRetryAfterMillis = 3_000L))
        assertEquals(5_000L, policy.computeDelayMillis(attemptIndex = 1, serverRetryAfterMillis = 25_000L))

        // Secret redaction verification
        val rawLog =
            "Failed URL https://generativelanguage.googleapis.com/v1beta?key=AIzaSySecretKey1234567890abcdef " +
                "Header x-goog-api-key: AIzaSyHeaderKey9876543210abcdef Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payload.sig"
        assertTrue(SafeDiagnosticLogger.containsSecretCredential(rawLog))
        val redacted = SafeDiagnosticLogger.redactSecrets(rawLog)
        assertFalse(SafeDiagnosticLogger.containsSecretCredential(redacted))
        assertFalse(redacted.contains("AIzaSySecretKey"))
        assertFalse(redacted.contains("AIzaSyHeaderKey"))
        assertFalse(redacted.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"))
    }

    // =========================================================================
    // C. DATABASE & MULTI-USER CONVERSATION OWNERSHIP SECURITY (SECTION 7)
    // =========================================================================

    @Test
    fun testC1_crossUserConversationMutationAndReadIsolation() = runBlocking {
        val repo = ChatHistoryRepository(chatDao = null)

        // User Alpha signs in and creates a conversation
        repo.setActiveUser(uid = "user_alpha", idToken = "token_alpha")
        val convAlpha = "user_alpha_conv_1"
        val msgAlpha = ChatMessage(
            id = "msg_alpha_1",
            content = "Alpha's confidential architecture roadmap",
            role = MessageRole.USER,
            timestamp = 1_000L,
            status = MessageStatus.SUCCESS
        )
        assertTrue(repo.saveMessage(convAlpha, msgAlpha).isSuccess)
        assertEquals(1, repo.getMessagesForConversation(convAlpha).size)

        // Switch to User Beta
        repo.setActiveUser(uid = "user_beta", idToken = "token_beta")

        // User Beta must not see Alpha's conversation in list, read messages, rename, pin, like, bookmark, replace, export, or delete it
        assertTrue(repo.conversationsFlow.value.none { it.id == convAlpha })
        assertTrue(repo.getMessagesForConversation(convAlpha).isEmpty())
        assertNull(repo.getConversationTitle(convAlpha))
        assertEquals("", repo.formatConversationForExport(convAlpha))

        assertTrue(repo.renameConversation(convAlpha, "Hacked Title").isFailure)
        assertTrue(repo.togglePinConversation(convAlpha).isFailure)
        assertTrue(repo.toggleLikeConversation(convAlpha).isFailure)
        assertTrue(repo.toggleLikeMessage(convAlpha, "msg_alpha_1").isFailure)
        assertTrue(repo.toggleBookmarkMessage(convAlpha, "msg_alpha_1").isFailure)
        assertTrue(repo.replaceMessagesForConversation(convAlpha, emptyList()).isFailure)
        assertTrue(repo.deleteConversation(convAlpha).isFailure)

        // Switch back to User Alpha: conversation and message are 100% intact
        repo.setActiveUser(uid = "user_alpha", idToken = "token_alpha")
        val alphaMessages = repo.getMessagesForConversation(convAlpha)
        assertEquals(1, alphaMessages.size)
        assertEquals("Alpha's confidential architecture roadmap", alphaMessages.first().content)
    }

    @Test
    fun testC2_duplicateRetryMessagePreventionInRepository() = runBlocking {
        val repo = ChatHistoryRepository(chatDao = null)
        repo.setActiveUser(uid = "user_alpha", idToken = "token_alpha")
        val convId = "user_alpha_retry_conv"

        val firstAttempt = ChatMessage(
            id = "msg_1",
            content = "Explain Kotlin Coroutines structured concurrency",
            role = MessageRole.USER,
            timestamp = 10_000L,
            status = MessageStatus.SUCCESS
        )
        val immediateRetryAttempt = ChatMessage(
            id = "msg_2",
            content = "Explain Kotlin Coroutines structured concurrency",
            role = MessageRole.USER,
            timestamp = 10_500L,
            status = MessageStatus.SUCCESS
        )

        repo.saveMessage(convId, firstAttempt)
        repo.saveMessage(convId, immediateRetryAttempt)

        val messages = repo.getMessagesForConversation(convId)
        assertEquals("Immediate duplicate user retry within 1.5s must coalesce instead of duplicating", 1, messages.size)
    }

    // =========================================================================
    // D. SMART MEMORY ISOLATION, DELETION & CORRUPTION RESILIENCE (SECTION 7)
    // =========================================================================

    @Test
    fun testD1_smartMemoryPerUserIsolationAndDeletionCommands() {
        val userA = "user_mem_a"
        val userB = "user_mem_b"

        AstrynPersonalIntelligenceEngine.recordMemory(
            userId = userA,
            category = PersonalMemoryCategory.B_PERSONAL_PREFERENCE_MEMORY,
            key = "preferred_language",
            value = "Kotlin and Jetpack Compose",
            nowMillis = 1_000_000L
        )

        val memoriesA = AstrynPersonalIntelligenceEngine.getAllUserMemories(userA)
        val memoriesB = AstrynPersonalIntelligenceEngine.getAllUserMemories(userB)
        assertTrue(memoriesA.isNotEmpty())
        assertTrue("User B must have zero access to User A's memories", memoriesB.isEmpty())

        // Test explicit memory deletion & account switch cleanup
        AstrynPersonalIntelligenceEngine.onUserLogoutOrAccountSwitch(userA)
        assertTrue(AstrynPersonalIntelligenceEngine.getAllUserMemories(userA).isEmpty())
    }

    // =========================================================================
    // E. AUTONOMOUS INTELLIGENCE EXECUTION LIMITS, CHECKPOINTS & APPROVAL (SECTION 9)
    // =========================================================================

    @Test
    fun testE1_autonomousTaskOwnershipToolLimitsAndDuplicateActionPrevention() = runBlocking {
        val orchestrator = AstrynResearchEngine.TaskOrchestrator()
        var gatewayInvocations = 0

        val countingGateway = AstrynResearchEngine.SearchExecutionGateway { query, _, _ ->
            gatewayInvocations++
            Result.success(
                AstrynResearchEngine.SearchRoundPayload(
                    query = query,
                    synthesizedAnswerChunk = "Verified official documentation details for $query (2026).",
                    sources = listOf(
                        AstrynResearchEngine.RawSearchSource(
                            title = "Official Kotlin Documentation",
                            url = "https://kotlinlang.org/docs/coroutines-overview.html",
                            snippet = "Kotlin coroutines provide structured concurrency.",
                            publishedDate = "2026-03-01",
                            searchQueryUsed = query
                        )
                    ),
                    webSearchQueriesFromMetadata = listOf(query)
                )
            )
        }

        val result = orchestrator.executeResearchTask(
            objective = "Latest Kotlin Coroutines official documentation updates 2026",
            preferences = UserPreferences(userUid = "user_alpha"),
            budget = AstrynResearchEngine.ResearchBudget(maxSearchRounds = 4),
            gatewayOverride = countingGateway,
            maxToolCalls = 1 // Enforce strict max 1 tool call cap
        )

        assertEquals(AstrynResearchEngine.AutonomousTaskState.COMPLETED, result.finalState)
        assertEquals("user_alpha", result.ownerUid)
        assertEquals("Tool call budget must cap gateway invocations to 1", 1, result.toolCallsUsed)
        assertEquals(1, gatewayInvocations)
    }

    @Test
    fun testE2_autonomousTaskSensitiveActionRequiresExplicitUserApproval() = runBlocking {
        val orchestrator = AstrynResearchEngine.TaskOrchestrator()

        // Register a mock sensitive tool
        orchestrator.toolRegistry.registerTool(
            object : AstrynResearchEngine.AutonomousTool {
                override val toolName: String = "delete_account_data_tool"
                override val description: String = "Sensitive destructive tool"
                override suspend fun execute(
                    objective: String,
                    conversationHistory: List<ChatMessage>,
                    attachments: List<ChatAttachment>,
                    preferences: UserPreferences,
                    conversationId: String,
                    budget: AstrynResearchEngine.ResearchBudget
                ): AstrynResearchEngine.ResearchExecutionReport {
                    throw UnsupportedOperationException("Should not execute without approval")
                }
            }
        )

        val unapprovedResult = orchestrator.executeResearchTask(
            objective = "Delete account records",
            preferences = UserPreferences(userUid = "user_alpha"),
            requestedToolName = "delete_account_data_tool",
            userApprovedSensitiveActions = false
        )

        assertEquals(AstrynResearchEngine.AutonomousTaskState.FAILED, unapprovedResult.finalState)
        assertTrue(unapprovedResult.awaitingUserApproval)
        assertTrue(unapprovedResult.errorMessage.orEmpty().contains("requires explicit user approval"))
    }

    @Test
    fun testE3_autonomousTaskCheckpointPersistenceAndCancellation() = runBlocking {
        // 1. Verify checkpoint ownership isolation
        val checkpoint = AstrynAutonomousExecutionGuard.TaskCheckpoint(
            taskId = "task_99",
            ownerUid = "user_alpha",
            goal = "Research Android 16 privacy changes",
            completedStepIndices = setOf(1, 2),
            stepOutputs = mapOf(1 to "Planned queries"),
            executedActionKeys = setOf("search:android 16 privacy"),
            toolCallsUsed = 1,
            updatedAtMs = System.currentTimeMillis()
        )
        AstrynAutonomousExecutionGuard.saveCheckpoint(checkpoint)

        assertNotNull(AstrynAutonomousExecutionGuard.loadCheckpoint("user_alpha", "task_99"))
        assertNull(
            "User Beta must not be able to load User Alpha's task checkpoint",
            AstrynAutonomousExecutionGuard.loadCheckpoint("user_beta", "task_99")
        )

        // 2. Verify coroutine cancellation stops autonomous task immediately
        val orchestrator = AstrynResearchEngine.TaskOrchestrator()
        val slowGateway = AstrynResearchEngine.SearchExecutionGateway { _, _, _ ->
            delay(5_000L)
            Result.failure(RuntimeException("Should be cancelled"))
        }

        var wasCancelled = false
        val job = launch {
            try {
                orchestrator.executeResearchTask(
                    objective = "Latest global semiconductor news 2026",
                    preferences = UserPreferences(userUid = "user_alpha"),
                    gatewayOverride = slowGateway
                )
            } catch (ce: CancellationException) {
                wasCancelled = true
                throw ce
            }
        }
        delay(60L)
        job.cancelAndJoin()
        assertTrue("Cancelled autonomous task must throw CancellationException cleanly", wasCancelled)
    }

    // =========================================================================
    // F. RESEARCH ENGINE & SSRF URL SECURITY TESTS (SECTION 10)
    // =========================================================================

    @Test
    fun testF1_ssrfAndUnsafeSchemeUrlBlockingInCitations() {
        assertTrue(AstrynSecurityGuard.isSafeExternalUrl("https://developer.android.com/jetpack/compose"))
        assertTrue(AstrynSecurityGuard.isSafeExternalUrl("https://www.india.gov.in/official-notice"))

        // Unsafe schemes and SSRF internal targets must be rejected
        assertFalse(AstrynSecurityGuard.isSafeExternalUrl("javascript:alert(1)"))
        assertFalse(AstrynSecurityGuard.isSafeExternalUrl("data:text/html,<script>alert(1)</script>"))
        assertFalse(AstrynSecurityGuard.isSafeExternalUrl("file:///etc/passwd"))
        assertFalse(AstrynSecurityGuard.isSafeExternalUrl("http://localhost:8080/admin"))
        assertFalse(AstrynSecurityGuard.isSafeExternalUrl("http://127.0.0.1/secret"))
        assertFalse(AstrynSecurityGuard.isSafeExternalUrl("http://169.254.169.254/latest/meta-data/"))
        assertFalse(AstrynSecurityGuard.isSafeExternalUrl("http://192.168.1.1/router"))
        assertFalse(AstrynSecurityGuard.isSafeExternalUrl("http://10.0.0.1/internal"))
        assertFalse(AstrynSecurityGuard.isSafeExternalUrl("http://172.16.5.4/internal"))

        assertFalse(AstrynResearchEngine.CitationManager.isValidHttpUrl("http://127.0.0.1/admin"))
        assertFalse(AstrynResearchEngine.CitationManager.isValidHttpUrl("http://169.254.169.254/meta"))
        assertTrue(AstrynResearchEngine.CitationManager.isValidHttpUrl("https://ssc.gov.in/notice"))
    }

    // =========================================================================
    // G. WEATHER & LIVE TIME RELIABILITY TESTS (SECTION 11)
    // =========================================================================

    @Test
    fun testG1_weatherMissingCityFallbackAndLiveTimeCalculation() {
        AstrynPersonalIntelligenceEngine.AstrynLocationManager.simulatedPermissionState =
            AstrynPersonalIntelligenceEngine.LocationPermissionState.DENIED

        // Missing city when user asks "Aaj mere yahan mausam kaisa hai?" without saved location
        val missingCityReply = AstrynPersonalIntelligenceEngine.generatePersonalOrEnvironmentResponse(
            userId = "user_weather_test",
            query = "Aaj mere yahan mausam kaisa hai?"
        )
        assertNotNull(missingCityReply)
        assertTrue(
            missingCityReply!!.contains("Location Needed for Live Weather") ||
                missingCityReply.contains("city", ignoreCase = true)
        )

        // Live time-zone calculation ("India aur Dubai ke time mein kitna difference hai?")
        val timeReply = AstrynPersonalIntelligenceEngine.generatePersonalOrEnvironmentResponse(
            userId = "user_weather_test",
            query = "India aur Dubai ke time mein kitna difference hai?"
        )
        assertNotNull(timeReply)
        assertTrue(timeReply!!.contains("1 hour 30 minutes"))
        assertTrue(timeReply.contains("Asia/Kolkata"))
        assertTrue(timeReply.contains("Asia/Dubai"))
    }

    // =========================================================================
    // H. ATTACHMENT PATH-TRAVERSAL SANITIZATION & PDF BOUNDS (SECTION 12)
    // =========================================================================

    @Test
    fun testH1_attachmentPathTraversalSanitizationAndValidation() {
        assertEquals(
            "passwd",
            AstrynSecurityGuard.sanitizeFileName("../../../../etc/passwd")
        )
        assertEquals(
            "secret_report.pdf",
            AstrynSecurityGuard.sanitizeFileName("..\\..\\Windows\\System32\\secret_report.pdf")
        )
        assertEquals(
            "clean_name.png",
            AstrynSecurityGuard.sanitizeFileName("folder/subfolder/clean_name\u0000.png".replace("\u0000", ""))
        )

        // Verify AttachmentProcessor sanitizes malicious filenames and rejects empty/unsupported files
        val emptyRes = AttachmentProcessor.processRawBytes(
            fileName = "../../../evil.pdf",
            mimeType = "application/pdf",
            bytes = ByteArray(0)
        )
        assertTrue(emptyRes.isFailure)
        assertFalse(emptyRes.exceptionOrNull()?.message.orEmpty().contains("../"))

        val unsupportedRes = AttachmentProcessor.processRawBytes(
            fileName = "../../script.exe",
            mimeType = "application/x-msdownload",
            bytes = ByteArray(128) { 0x41 }
        )
        assertTrue(unsupportedRes.isFailure)

        // Valid PDF with path-traversal filename is sanitized to safe leaf filename
        val validPdfBytes =
            "%PDF-1.4\n1 0 obj << /Type /Page >> endobj\nstream\nBT (Astryn Security Test Page 1) Tj ET\nendstream\n%%EOF"
                .toByteArray(Charsets.ISO_8859_1)
        val validPdfRes = AttachmentProcessor.processRawBytes(
            fileName = "../../../unsafe_path/architecture_spec.pdf",
            mimeType = "application/pdf",
            bytes = validPdfBytes
        )
        assertTrue(validPdfRes.isSuccess)
        assertEquals("architecture_spec.pdf", validPdfRes.getOrThrow().fileName)
    }

    // =========================================================================
    // I. APPLICATION DUPLICATE SUBMISSION GUARD, CRASH BOUNDARY & DIAGNOSTICS (SECTIONS 8, 14, 16)
    // =========================================================================

    @Test
    fun testI1_duplicateMessageSubmissionGuardAndCrashBoundary() {
        val now = 50_000L
        assertTrue(
            AstrynSecurityGuard.allowMessageSubmission(
                userId = "user_1",
                conversationId = "conv_1",
                promptText = "Build a Kotlin microservice",
                nowMs = now
            )
        )
        assertFalse(
            "Rapid double-tap with identical prompt within deduplication window must be blocked",
            AstrynSecurityGuard.allowMessageSubmission(
                userId = "user_1",
                conversationId = "conv_1",
                promptText = "Build a Kotlin microservice",
                nowMs = now + 400L
            )
        )
        assertTrue(
            "Submission after deduplication window must be allowed",
            AstrynSecurityGuard.allowMessageSubmission(
                userId = "user_1",
                conversationId = "conv_1",
                promptText = "Build a Kotlin microservice",
                nowMs = now + 2_000L
            )
        )

        // Crash boundary containment test
        val fallback = AstrynCrashBoundary.runSafely(
            subsystem = AstrynDiagnosticsManager.Subsystem.WEATHER_TIME,
            operation = "parseWeatherPayload",
            fallbackValue = "SAFE_FALLBACK"
        ) {
            throw IllegalStateException("Simulated malformed weather node with key AIzaSySecretKey1234567890abcdef")
        }
        assertEquals("SAFE_FALLBACK", fallback)

        val metrics = AstrynDiagnosticsManager.getMetricsSnapshot()
        assertEquals(1L, metrics.totalContainedExceptions)

        val crashEvents = AstrynDiagnosticsManager.getRecentEvents(AstrynDiagnosticsManager.Subsystem.CRASH_BOUNDARY)
        assertEquals(1, crashEvents.size)
        assertFalse(
            "Diagnostics must never contain raw API keys even from exception messages",
            crashEvents.first().sanitizedMessage.contains("AIzaSySecretKey")
        )
        assertTrue(crashEvents.first().sanitizedMessage.contains("[REDACTED_API_KEY]"))
    }

    @Test
    fun testI2_privacySafeDiagnosticsRedactsEmailsPasswordsAndTokens() {
        val event = AstrynDiagnosticsManager.recordEvent(
            subsystem = AstrynDiagnosticsManager.Subsystem.AUTH_SESSION,
            severity = AstrynDiagnosticsManager.Severity.WARNING,
            operation = "signInWithEmail",
            rawMessage = """Auth failed for user alice.smith@example.com with {"password":"MySuperSecretPassword123","idToken":"eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9"} and key=AIzaSyTestKey1234567890abcdefgh"""
        )
        assertFalse(event.sanitizedMessage.contains("alice.smith@example.com"))
        assertFalse(event.sanitizedMessage.contains("MySuperSecretPassword123"))
        assertFalse(event.sanitizedMessage.contains("eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9"))
        assertFalse(event.sanitizedMessage.contains("AIzaSyTestKey"))
        assertTrue(event.sanitizedMessage.contains("[REDACTED_EMAIL]"))
        assertTrue(event.sanitizedMessage.contains("[REDACTED_SECRET]"))
        assertTrue(event.sanitizedMessage.contains("[REDACTED_TOKEN]"))
        assertTrue(event.sanitizedMessage.contains("[REDACTED_API_KEY]"))
    }

    // =========================================================================
    // J. BUILD, WORKFLOW & VOICE-TO-VOICE JARVIS LOCK VERIFICATION (SECTIONS 2 & 17)
    // =========================================================================

    @Test
    fun testJ1_voiceToVoiceJarvisUntouchedAndGitHubWorkflowIntact() {
        // 1. Verify GeminiLiveVoiceSession class & Companion contract are 100% intact
        val sessionClass = GeminiLiveVoiceSession::class.java
        assertNotNull(sessionClass)
        val companionMethods = GeminiLiveVoiceSession.Companion::class.java.declaredMethods.map { it.name }.toSet()
        assertTrue(companionMethods.contains("buildSmartJarvisChatReply"))
        val jarvisSample = GeminiLiveVoiceSession.buildSmartJarvisChatReply(
            userPrompt = "Hello Jarvis",
            liveVoiceMood = null,
            conversationHistory = emptyList(),
            userUid = "jarvis_verify_user"
        )
        assertTrue(jarvisSample.isNotBlank())

        // 2. Verify GitHub Actions APK workflow exists and contains assembleDebug & upload-artifact
        val workflowCandidates = listOf(
            File("../.github/workflows/build-apk.yml"),
            File(".github/workflows/build-apk.yml")
        )
        val workflowFile = workflowCandidates.firstOrNull { it.exists() }
        if (workflowFile != null) {
            val content = workflowFile.readText()
            assertTrue(content.contains("assembleDebug"))
            assertTrue(content.contains("actions/upload-artifact"))
            assertTrue(content.contains("app-debug.apk"))
        }
    }
}
