package com.example

import com.example.data.api.AstrynIntelligenceEngine
import com.example.data.api.AstrynPersonalIntelligenceEngine
import com.example.data.api.AstrynPersonalIntelligenceEngine.AstrynContextResolver
import com.example.data.api.AstrynPersonalIntelligenceEngine.AstrynLocationManager
import com.example.data.api.AstrynPersonalIntelligenceEngine.AstrynPersonalizationEngine
import com.example.data.api.AstrynPersonalIntelligenceEngine.AstrynPreferenceEngine
import com.example.data.api.AstrynPersonalIntelligenceEngine.AstrynProjectMemoryManager
import com.example.data.api.AstrynPersonalIntelligenceEngine.AstrynTimeIntelligence
import com.example.data.api.AstrynPersonalIntelligenceEngine.AstrynUserProfileManager
import com.example.data.api.AstrynPersonalIntelligenceEngine.AstrynWeatherIntelligence
import com.example.data.api.AstrynPersonalIntelligenceEngine.CompleteWeatherBundle
import com.example.data.api.AstrynPersonalIntelligenceEngine.CurrentWeatherSnapshot
import com.example.data.api.AstrynPersonalIntelligenceEngine.DailyWeatherForecastItem
import com.example.data.api.AstrynPersonalIntelligenceEngine.HourlyWeatherSlot
import com.example.data.api.AstrynPersonalIntelligenceEngine.LocationPermissionState
import com.example.data.api.AstrynPersonalIntelligenceEngine.LocationResolutionStatus
import com.example.data.api.AstrynPersonalIntelligenceEngine.PersonalMemoryCategory
import com.example.data.api.AstrynPersonalIntelligenceEngine.PreferredDepth
import com.example.data.api.AstrynPersonalIntelligenceEngine.PreferredLanguage
import com.example.data.api.AstrynPersonalIntelligenceEngine.PreferredStyle
import com.example.data.api.AstrynPersonalIntelligenceEngine.TimeOfDayPeriod
import com.example.data.api.AstrynPersonalIntelligenceEngine.WeatherAlertItem
import com.example.data.api.AstrynPersonalIntelligenceEngine.WeatherDataGateway
import com.example.data.api.AstrynResearchEngine
import com.example.data.api.GeminiLiveVoiceSession
import com.example.data.model.ChatMessage
import com.example.data.model.MessageRole
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * ASTRYN AI — PHASE 6 VERIFICATION SUITE (30 SCENARIOS)
 *
 * Verifies Personal AI Intelligence, 5-Category Smart Memory, Project Continuity,
 * Live Time & Date Intelligence, Location Privacy, Real-Time Weather Intelligence,
 * Offline Weather Cache Labeling, Daily Personal Assistant, Phase 4/5 Tool Integration,
 * and Voice-to-Voice Jarvis Lock.
 */
class AstrynPhase6PersonalIntelligenceTest {

    private val fixedInstant = Instant.parse("2026-05-12T13:00:00Z") // 18:30 IST (06:30 PM Evening in Asia/Kolkata, Tuesday)
    private val istZone = ZoneId.of("Asia/Kolkata")

    private val deterministicWeatherGateway = object : WeatherDataGateway {
        override fun fetchLiveWeather(
            cityName: String,
            latitude: Double,
            longitude: Double,
            nowMillis: Long
        ): CompleteWeatherBundle {
            val hourly = (0 until 24).map { hour ->
                val isEveningRain = hour in 18..20
                HourlyWeatherSlot(
                    timeLabel = String.format("%02d:00", hour),
                    hour24 = hour,
                    temperatureC = if (hour in 12..16) 34.0 else 27.5,
                    rainProbabilityPercent = if (isEveningRain) 75 else 15,
                    precipitationMm = if (isEveningRain) 4.2 else 0.0,
                    windSpeedKmh = if (isEveningRain) 22.0 else 10.0,
                    conditionText = if (isEveningRain) "Rain Showers" else "Partly Cloudy"
                )
            }
            val daily = (0 until 7).map { dayIdx ->
                val dateIso = "2026-05-${12 + dayIdx}"
                val label = when (dayIdx) {
                    0 -> "Today ($dateIso)"
                    1 -> "Tomorrow ($dateIso)"
                    else -> "Day +$dayIdx ($dateIso)"
                }
                DailyWeatherForecastItem(
                    dateIso = dateIso,
                    dayLabel = label,
                    minTempC = 25.0 + (dayIdx % 2),
                    maxTempC = 35.0 + (dayIdx % 3),
                    rainProbabilityPercent = if (dayIdx == 0) 75 else 20,
                    expectedPrecipitationMm = if (dayIdx == 0) 8.4 else 0.5,
                    conditionText = if (dayIdx == 0) "Rain Showers" else "Mainly Clear"
                )
            }
            return CompleteWeatherBundle(
                cityName = cityName,
                country = "India",
                fetchedAtMillis = nowMillis,
                isFromOfflineCache = false,
                isStaleCache = false,
                current = CurrentWeatherSnapshot(
                    temperatureC = 31.5,
                    feelsLikeC = 34.0,
                    conditionText = "Partly Cloudy",
                    humidityPercent = 64,
                    windSpeedKmh = 14.5,
                    windDirectionDegrees = 250,
                    precipitationMm = 0.4,
                    visibilityKm = 9.0,
                    pressureHpa = 1008.0
                ),
                hourlyNext24h = hourly,
                dailyNext7d = daily,
                alerts = emptyList()
            )
        }
    }

    @Before
    fun setUp() {
        AstrynPersonalIntelligenceEngine.clearAllPersonalIntelligenceStateForTesting()
        AstrynTimeIntelligence.clockProvider = Clock.fixed(fixedInstant, istZone)
        AstrynWeatherIntelligence.weatherGateway = deterministicWeatherGateway
    }

    @After
    fun tearDown() {
        AstrynPersonalIntelligenceEngine.clearAllPersonalIntelligenceStateForTesting()
    }

    // 1. Preference storage and retrieval
    @Test
    fun test01_preferenceStorageAndRetrieval() {
        val outcome = AstrynPersonalIntelligenceEngine.recordMemory(
            userId = "user_alpha",
            category = PersonalMemoryCategory.B_PERSONAL_PREFERENCE_MEMORY,
            key = "preferred_coding_stack",
            value = "Kotlin, Jetpack Compose, Coroutines, and Clean MVVM Architecture",
            tags = setOf("kotlin", "compose", "mvvm", "coding"),
            nowMillis = 1_000_000L
        )
        assertTrue(outcome.stored)
        assertFalse(outcome.deduplicated)

        val retrieved = AstrynPersonalIntelligenceEngine.retrieveRelevantMemories(
            userId = "user_alpha",
            query = "What is my preferred coding stack for Compose?",
            nowMillis = 1_005_000L
        )
        assertTrue(retrieved.isNotEmpty())
        assertEquals("preferred_coding_stack", retrieved.first().record.key)
        assertTrue(retrieved.first().record.value.contains("Jetpack Compose"))
    }

    // 2. Duplicate memory prevention
    @Test
    fun test02_duplicateMemoryPrevention() {
        val first = AstrynPersonalIntelligenceEngine.recordMemory(
            userId = "user_alpha",
            category = PersonalMemoryCategory.D_IMPORTANT_INFORMATION_MEMORY,
            key = "target_sdk",
            value = "Android targetSdk is 35 with Kotlin 2.0",
            nowMillis = 1_000_000L
        )
        val duplicate = AstrynPersonalIntelligenceEngine.recordMemory(
            userId = "user_alpha",
            category = PersonalMemoryCategory.D_IMPORTANT_INFORMATION_MEMORY,
            key = "target_sdk",
            value = "Android targetSdk is 35 with Kotlin 2.0!",
            nowMillis = 1_010_000L
        )
        assertTrue(first.stored)
        assertTrue(duplicate.stored)
        assertTrue(duplicate.deduplicated)

        val all = AstrynPersonalIntelligenceEngine.getAllUserMemories("user_alpha", 1_020_000L)
        assertEquals(1, all.count { it.key == "target_sdk" })
    }

    // 3. Conflicting preference update (newer explicit instruction wins)
    @Test
    fun test03_conflictingPreferenceUpdateLatestWins() {
        AstrynPreferenceEngine.ingestTurnForPreferencesAndMemory(
            userId = "user_alpha",
            userPrompt = "Always reply in Hinglish and keep it concise",
            nowMillis = 1_000_000L
        )
        assertEquals(PreferredLanguage.HINGLISH, AstrynUserProfileManager.getProfile("user_alpha").preferredLanguage)
        assertEquals(PreferredStyle.CONCISE, AstrynUserProfileManager.getProfile("user_alpha").preferredStyle)

        // Newer explicit instruction overrides earlier preference
        AstrynPreferenceEngine.ingestTurnForPreferencesAndMemory(
            userId = "user_alpha",
            userPrompt = "Now always reply in English and give step-by-step explanations",
            nowMillis = 2_000_000L
        )
        val updatedProfile = AstrynUserProfileManager.getProfile("user_alpha")
        assertEquals(PreferredLanguage.ENGLISH, updatedProfile.preferredLanguage)
        assertEquals(PreferredStyle.STEP_BY_STEP, updatedProfile.preferredStyle)

        val langMem = AstrynPersonalIntelligenceEngine.getAllUserMemories("user_alpha", 2_001_000L)
            .first { it.key == "preferred_language" }
        assertEquals("English", langMem.value)
        assertEquals("Hinglish", langMem.supersededValue)
    }

    // 4. Expired temporary memory cleanup (Category E)
    @Test
    fun test04_expiredTemporaryMemoryCleanup() {
        val startMs = 10_000_000L
        val ttlOneHour = 3_600_000L
        AstrynPersonalIntelligenceEngine.recordMemory(
            userId = "user_alpha",
            category = PersonalMemoryCategory.E_TEMPORARY_CONTEXT,
            key = "today_debug_goal",
            value = "Fix WebSocket reconnection backoff in staging",
            nowMillis = startMs,
            ttlMillis = ttlOneHour
        )
        // Before expiry: present
        val activeList = AstrynPersonalIntelligenceEngine.getAllUserMemories("user_alpha", startMs + 1_800_000L)
        assertEquals(1, activeList.size)

        // After expiry: automatically pruned
        val afterExpiryList = AstrynPersonalIntelligenceEngine.getAllUserMemories("user_alpha", startMs + ttlOneHour + 1_000L)
        assertTrue(afterExpiryList.isEmpty())
    }

    // 5. Project memory retrieval ("Mere Astryn project ka next upgrade kya hai?", "Mere current project ka pending work batao.")
    @Test
    fun test05_projectMemoryRetrievalAndPendingWork() {
        val response1 = AstrynPersonalIntelligenceEngine.generatePersonalOrEnvironmentResponse(
            userId = "user_alpha",
            query = "Mere Astryn project ka next upgrade kya hai?"
        )
        assertNotNull(response1)
        assertTrue(response1!!.contains("Astryn AI"))
        assertTrue(response1.contains("Pending Work & Next Upgrade Roadmap"))
        assertTrue(response1.contains("Glassmorphism UI"))

        val response2 = AstrynPersonalIntelligenceEngine.generatePersonalOrEnvironmentResponse(
            userId = "user_alpha",
            query = "Mere current project ka pending work batao."
        )
        assertNotNull(response2)
        assertTrue(response2!!.contains("[Pending]"))
    }

    // 6. Cross-conversation reference resolution ("Kal jo baat hui thi usko continue karo", "Pichle project mein jo problem thi uska solution do")
    @Test
    fun test06_crossConversationReferenceResolution() {
        val history = listOf(
            ChatMessage(
                role = MessageRole.USER,
                content = "Humne kal Room database migration aur Firestore offline sync discuss kiya tha."
            )
        )
        val reply = AstrynPersonalIntelligenceEngine.generatePersonalOrEnvironmentResponse(
            userId = "user_alpha",
            query = "Kal jo baat hui thi usko continue karo aur pichle project mein jo problem thi uska solution do.",
            conversationHistory = history
        )
        assertNotNull(reply)
        assertTrue(reply!!.contains("Previous Problems & Verified Solutions"))
        assertTrue(reply.contains("Room database migration"))
    }

    // 7. Per-user memory isolation
    @Test
    fun test07_perUserMemoryIsolation() {
        AstrynPersonalIntelligenceEngine.recordMemory(
            userId = "user_alice",
            category = PersonalMemoryCategory.C_PROJECT_MEMORY,
            key = "secret_project_codename",
            value = "Project QuantumShield",
            nowMillis = 1_000L
        )
        AstrynLocationManager.setPreferredWeatherCity("user_alice", "Mumbai", 1_000L)

        // User Bob must never see User Alice's memories or preferred city
        val bobMemories = AstrynPersonalIntelligenceEngine.getAllUserMemories("user_bob", 2_000L)
        assertTrue(bobMemories.isEmpty())
        assertNull(AstrynLocationManager.getPreferredWeatherCity("user_bob"))

        // Logout User Alice -> clears Alice's volatile session state
        AstrynPersonalIntelligenceEngine.onUserLogoutOrAccountSwitch("user_alice")
        assertTrue(AstrynPersonalIntelligenceEngine.getAllUserMemories("user_alice", 3_000L).isEmpty())
    }

    // 8. Current local time query ("Abhi kitne baje hain?")
    @Test
    fun test08_currentLocalTimeQuery() {
        val reply = AstrynPersonalIntelligenceEngine.generatePersonalOrEnvironmentResponse(
            userId = "user_alpha",
            query = "Abhi kitne baje hain?"
        )
        assertNotNull(reply)
        // Fixed clock is 2026-05-12T13:00:00Z -> 06:30 PM (18:30) IST Evening (Shaam)
        assertTrue(reply!!.contains("06:30 PM"))
        assertTrue(reply.contains("18:30"))
        assertTrue(reply.contains("Evening"))
        assertTrue(reply.contains("Shaam"))
    }

    // 9. Current date and day query ("Aaj kaun sa din hai?")
    @Test
    fun test09_currentDateAndDayQuery() {
        val reply = AstrynPersonalIntelligenceEngine.generatePersonalOrEnvironmentResponse(
            userId = "user_alpha",
            query = "Aaj kaun sa din hai?"
        )
        assertNotNull(reply)
        assertTrue(reply!!.contains("12 May 2026"))
        assertTrue(reply.contains("Tuesday"))
        assertTrue(reply.contains("Mangalvaar"))
    }

    // 10. Tomorrow / relative date query ("Kal ki date kya hogi?", "Aaj shaam kitne baje hogi?")
    @Test
    fun test10_tomorrowAndRelativeDateQuery() {
        val reply = AstrynPersonalIntelligenceEngine.generatePersonalOrEnvironmentResponse(
            userId = "user_alpha",
            query = "Kal ki date kya hogi aur aaj shaam kitne baje hogi?"
        )
        assertNotNull(reply)
        assertTrue(reply!!.contains("13 May 2026"))
        assertTrue(reply.contains("Wednesday"))
        assertTrue(reply.contains("05:00 PM"))
    }

    // 11. Time-zone difference calculation ("India aur Dubai ke time mein kitna difference hai?")
    @Test
    fun test11_timeZoneDifferenceCalculation() {
        val reply = AstrynPersonalIntelligenceEngine.generatePersonalOrEnvironmentResponse(
            userId = "user_alpha",
            query = "India aur Dubai ke time mein kitna difference hai?"
        )
        assertNotNull(reply)
        // India (UTC+5:30) vs Dubai (UTC+4:00) = 1 hour 30 minutes
        assertTrue(reply!!.contains("1 hour 30 minutes"))
        assertTrue(reply.contains("Asia/Kolkata"))
        assertTrue(reply.contains("Asia/Dubai"))
    }

    // 12. Current weather with explicit city name ("Delhi ka weather batao")
    @Test
    fun test12_currentWeatherWithExplicitCityName() {
        AstrynLocationManager.simulatedPermissionState = LocationPermissionState.DENIED
        val reply = AstrynPersonalIntelligenceEngine.generatePersonalOrEnvironmentResponse(
            userId = "user_alpha",
            query = "Delhi ka weather batao"
        )
        assertNotNull(reply)
        assertTrue(reply!!.contains("New Delhi"))
        assertTrue(reply.contains("31.5°C"))
        assertTrue(reply.contains("Feels like 34.0°C"))
        assertTrue(reply.contains("Humidity:"))
    }

    // 13. Current weather with device location when permission is granted
    @Test
    fun test13_currentWeatherWithDeviceLocationPermissionGranted() {
        AstrynLocationManager.simulatedPermissionState = LocationPermissionState.GRANTED_COARSE
        AstrynLocationManager.simulatedGpsEnabled = true
        AstrynLocationManager.simulatedDeviceCity = "Jaipur"

        val reply = AstrynPersonalIntelligenceEngine.generatePersonalOrEnvironmentResponse(
            userId = "user_alpha",
            query = "Aaj mere yahan mausam kaisa hai?"
        )
        assertNotNull(reply)
        assertTrue(reply!!.contains("Jaipur"))
        assertTrue(reply.contains("31.5°C"))
    }

    // 14. Weather when location permission is denied and no city is saved
    @Test
    fun test14_weatherWhenLocationPermissionDeniedAsksForCityPolitely() {
        AstrynLocationManager.simulatedPermissionState = LocationPermissionState.DENIED
        val reply = AstrynPersonalIntelligenceEngine.generatePersonalOrEnvironmentResponse(
            userId = "user_no_city",
            query = "Aaj mere yahan mausam kaisa hai?"
        )
        assertNotNull(reply)
        assertTrue(reply!!.contains("Location Needed for Live Weather"))
        assertTrue(reply.contains("Delhi ka weather batao"))
    }

    // 15. Hourly weather forecast ("Aaj kis time baarish ki possibility hai?")
    @Test
    fun test15_hourlyWeatherForecastAndPeakRainHour() {
        AstrynLocationManager.setPreferredWeatherCity("user_alpha", "Mumbai")
        val reply = AstrynPersonalIntelligenceEngine.generatePersonalOrEnvironmentResponse(
            userId = "user_alpha",
            query = "Aaj kis time baarish ki possibility hai?"
        )
        assertNotNull(reply)
        assertTrue(reply!!.contains("Mumbai"))
        assertTrue(reply.contains("Rain & Hourly Forecast Analysis"))
        assertTrue(reply.contains("18:00"))
        assertTrue(reply.contains("75%"))
        assertTrue(reply.contains("4.2 mm"))
    }

    // 16. 7-day weather forecast ("Agale 7 din ka weather batao.")
    @Test
    fun test16_sevenDayWeatherForecast() {
        AstrynLocationManager.setPreferredWeatherCity("user_alpha", "Bengaluru")
        val reply = AstrynPersonalIntelligenceEngine.generatePersonalOrEnvironmentResponse(
            userId = "user_alpha",
            query = "Agale 7 din ka weather batao."
        )
        assertNotNull(reply)
        assertTrue(reply!!.contains("7-Day Weather Forecast"))
        assertTrue(reply.contains("Today (2026-05-12)"))
        assertTrue(reply.contains("Tomorrow (2026-05-13)"))
    }

    // 17. Rain probability query distinguishing % probability from expected mm ("Aaj baarish hogi kya?")
    @Test
    fun test17_rainProbabilityDistinguishesPercentFromMillimeters() {
        AstrynLocationManager.setPreferredWeatherCity("user_alpha", "Pune")
        val reply = AstrynPersonalIntelligenceEngine.generatePersonalOrEnvironmentResponse(
            userId = "user_alpha",
            query = "Aaj baarish hogi kya?"
        )
        assertNotNull(reply)
        assertTrue(reply!!.contains("75% probability"))
        assertTrue(reply.contains("4.2 mm"))
    }

    // 18. Evening weather recommendation ("Shaam ko bahar jana sahi rahega?")
    @Test
    fun test18_eveningOutdoorSuitabilityRecommendation() {
        AstrynLocationManager.setPreferredWeatherCity("user_alpha", "New Delhi")
        val reply = AstrynPersonalIntelligenceEngine.generatePersonalOrEnvironmentResponse(
            userId = "user_alpha",
            query = "Shaam ko bahar jana sahi rahega?"
        )
        assertNotNull(reply)
        assertTrue(reply!!.contains("Evening Outdoor Advisory (5:00 PM – 9:00 PM)"))
        assertTrue(reply.contains("75%"))
        assertTrue(reply.contains("umbrella"))
    }

    // 19. Weather API error handling when offline and no cache exists
    @Test
    fun test19_weatherApiErrorHandlingWithoutCache() {
        AstrynWeatherIntelligence.forceOfflineMode = true
        AstrynWeatherIntelligence.clearWeatherCache()
        AstrynLocationManager.setPreferredWeatherCity("user_alpha", "Chennai")

        val reply = AstrynPersonalIntelligenceEngine.generatePersonalOrEnvironmentResponse(
            userId = "user_alpha",
            query = "Mere city ka weather check karo."
        )
        assertNotNull(reply)
        assertTrue(reply!!.contains("Live Weather Unavailable (Chennai)"))
        assertTrue(reply.contains("No cached weather snapshot is available"))
    }

    // 20. Offline cached weather behavior (labeled clearly as CACHED / OUTDATED with timestamp, never as live)
    @Test
    fun test20_offlineCachedWeatherClearlyLabeledWithTimestamp() {
        AstrynLocationManager.setPreferredWeatherCity("user_alpha", "Kolkata")
        val initialFetchMs = fixedInstant.minusSeconds(3600).toEpochMilli() // 1 hour ago (stale > 15 min)

        // First fetch online to populate cache
        AstrynWeatherIntelligence.forceOfflineMode = false
        val onlineBundle = AstrynWeatherIntelligence.getWeatherForCity("Kolkata", nowMillis = initialFetchMs)
        assertTrue(onlineBundle.isSuccess)
        assertFalse(onlineBundle.getOrThrow().isFromOfflineCache)

        // Now switch to offline mode and query 1 hour later
        AstrynWeatherIntelligence.forceOfflineMode = true
        val offlineReply = AstrynPersonalIntelligenceEngine.generatePersonalOrEnvironmentResponse(
            userId = "user_alpha",
            query = "Aaj mere yahan mausam kaisa hai?"
        )
        assertNotNull(offlineReply)
        assertTrue(offlineReply!!.contains("[CACHED WEATHER — OUTDATED / STALE CACHE]"))
        assertTrue(offlineReply.contains("not live data"))
        assertTrue(offlineReply.contains("Kolkata"))
    }

    // 21. Personalized language adaptation (Hindi, Hinglish, English)
    @Test
    fun test21_personalizedLanguageAdaptation() {
        val detectedHinglish = AstrynPersonalizationEngine.detectResponseLanguage(
            userId = "user_lang",
            query = "Mere project ka pending kaam kya hai batao"
        )
        assertEquals(PreferredLanguage.HINGLISH, detectedHinglish)

        val detectedEnglish = AstrynPersonalizationEngine.detectResponseLanguage(
            userId = "user_lang",
            query = "What are the pending tasks for my project?"
        )
        assertEquals(PreferredLanguage.ENGLISH, detectedEnglish)

        AstrynPreferenceEngine.ingestTurnForPreferencesAndMemory(
            userId = "user_lang",
            userPrompt = "Sirf Hindi mein jawab do"
        )
        assertEquals(
            PreferredLanguage.HINDI,
            AstrynPersonalizationEngine.detectResponseLanguage("user_lang", "Explain architecture")
        )
    }

    // 22. Personalized response depth (Beginner vs Advanced)
    @Test
    fun test22_personalizedResponseDepthAndStyle() {
        AstrynPreferenceEngine.ingestTurnForPreferencesAndMemory(
            userId = "user_depth",
            userPrompt = "I am a beginner, please keep it short and concise"
        )
        val p1 = AstrynUserProfileManager.getProfile("user_depth")
        assertEquals(PreferredDepth.BEGINNER, p1.preferredDepth)
        assertEquals(PreferredStyle.CONCISE, p1.preferredStyle)

        AstrynPreferenceEngine.ingestTurnForPreferencesAndMemory(
            userId = "user_depth",
            userPrompt = "I am a senior architect now, give advanced technical depth and detailed explanation"
        )
        val p2 = AstrynUserProfileManager.getProfile("user_depth")
        assertEquals(PreferredDepth.ADVANCED, p2.preferredDepth)
        assertEquals(PreferredStyle.DETAILED, p2.preferredStyle)
    }

    // 23. Daily priority planning ("Aaj mujhe kya kaam complete karna chahiye?")
    @Test
    fun test23_dailyPriorityPlanningAssistant() {
        AstrynPreferenceEngine.ingestTurnForPreferencesAndMemory(
            userId = "user_planner",
            userPrompt = "For today's session: Complete Phase 6 weather caching and 30 unit tests"
        )
        val reply = AstrynPersonalIntelligenceEngine.generatePersonalOrEnvironmentResponse(
            userId = "user_planner",
            query = "Aaj mujhe kya kaam complete karna chahiye?"
        )
        assertNotNull(reply)
        assertTrue(reply!!.contains("Smart Daily Priority Plan"))
        assertTrue(reply.contains("Tuesday, 12 May 2026"))
        assertTrue(reply.contains("Complete Phase 6 weather caching and 30 unit tests"))
        assertTrue(reply.contains("without inventing external calendar events"))
    }

    // 24. Tool registry execution for all 9 Phase 6 tools
    @Test
    fun test24_toolRegistryExecutionForAllNinePhase6Tools() {
        val orchestrator = AstrynResearchEngine.TaskOrchestrator()
        val registry = orchestrator.toolRegistry
        val registeredNames = registry.listRegisteredTools()

        val expectedTools = listOf(
            "get_current_time",
            "get_current_date",
            "get_user_timezone",
            "get_current_weather",
            "get_hourly_forecast",
            "get_daily_forecast",
            "get_weather_alerts",
            "get_relevant_user_memory",
            "get_project_context"
        )
        for (toolName in expectedTools) {
            assertTrue("Missing tool: $toolName", registeredNames.contains(toolName))
        }

        val timeOut = registry.executeToolDirect("get_current_time")
        assertNotNull(timeOut)
        assertTrue(timeOut!!.contains("06:30 PM"))

        val dateOut = registry.executeToolDirect("get_current_date")
        assertNotNull(dateOut)
        assertTrue(dateOut!!.contains("12 May 2026"))

        val tzOut = registry.executeToolDirect("get_user_timezone", "India and Dubai")
        assertNotNull(tzOut)
        assertTrue(tzOut!!.contains("1 hour 30 minutes"))

        val weatherOut = registry.executeToolDirect("get_current_weather", "Delhi")
        assertNotNull(weatherOut)
        assertTrue(weatherOut!!.contains("31.5°C"))

        val hourlyOut = registry.executeToolDirect("get_hourly_forecast", "Mumbai")
        assertNotNull(hourlyOut)
        assertTrue(hourlyOut!!.contains("Hourly Forecast (Mumbai)"))

        val dailyOut = registry.executeToolDirect("get_daily_forecast", "Bengaluru")
        assertNotNull(dailyOut)
        assertTrue(dailyOut!!.contains("7-Day Forecast (Bengaluru)"))

        val alertOut = registry.executeToolDirect("get_weather_alerts", "Delhi")
        assertNotNull(alertOut)
        assertTrue(alertOut!!.contains("No active severe weather alerts"))

        val projOut = registry.executeToolDirect("get_project_context", "Astryn")
        assertNotNull(projOut)
        assertTrue(projOut!!.contains("Project=Astryn AI"))
    }

    // 25. Voice-to-Voice Jarvis untouched verification
    @Test
    fun test25_voiceToVoiceJarvisUntouchedVerification() {
        // Verify GeminiLiveVoiceSession class and its Companion Jarvis methods remain intact and untouched
        val clazz = GeminiLiveVoiceSession::class.java
        assertNotNull(clazz)
        val companionMethods = GeminiLiveVoiceSession.Companion::class.java.declaredMethods.map { it.name }.toSet()
        assertTrue(companionMethods.contains("buildSmartJarvisChatReply"))
        val jarvisSample = GeminiLiveVoiceSession.buildSmartJarvisChatReply(
            userPrompt = "Hello Jarvis",
            liveVoiceMood = null,
            conversationHistory = emptyList(),
            userUid = "jarvis_verify_user"
        )
        assertTrue(jarvisSample.isNotBlank())
    }

    // 26. Credential and sensitive data exclusion from memory (including raw precise GPS coordinates)
    @Test
    fun test26_credentialAndPreciseCoordinateExclusionFromMemory() {
        val apiKeyAttempt = AstrynPersonalIntelligenceEngine.recordMemory(
            userId = "user_sec",
            category = PersonalMemoryCategory.D_IMPORTANT_INFORMATION_MEMORY,
            key = "gemini_key",
            value = "AIzaSyD9876543210abcdefGHIJKLMN012345"
        )
        assertFalse(apiKeyAttempt.stored)

        val passwordAttempt = AstrynPersonalIntelligenceEngine.recordMemory(
            userId = "user_sec",
            category = PersonalMemoryCategory.D_IMPORTANT_INFORMATION_MEMORY,
            key = "user_cred",
            value = "password = MySuperSecret123!"
        )
        assertFalse(passwordAttempt.stored)

        val rawGpsAttempt = AstrynPersonalIntelligenceEngine.recordMemory(
            userId = "user_sec",
            category = PersonalMemoryCategory.B_PERSONAL_PREFERENCE_MEMORY,
            key = "raw_gps",
            value = "28.613939, 77.209021"
        )
        assertFalse(rawGpsAttempt.stored)
        assertTrue(AstrynPersonalIntelligenceEngine.getAllUserMemories("user_sec").isEmpty())
    }

    // 27. Changing preferred weather city
    @Test
    fun test27_changingPreferredWeatherCityUpdatesProfileAndMemory() {
        AstrynPreferenceEngine.ingestTurnForPreferencesAndMemory(
            userId = "user_travel",
            userPrompt = "Set my preferred weather city to Mumbai",
            nowMillis = 1_000L
        )
        assertEquals("Mumbai", AstrynLocationManager.getPreferredWeatherCity("user_travel"))

        AstrynPreferenceEngine.ingestTurnForPreferencesAndMemory(
            userId = "user_travel",
            userPrompt = "Change my weather city to Bengaluru",
            nowMillis = 2_000L
        )
        assertEquals("Bengaluru", AstrynLocationManager.getPreferredWeatherCity("user_travel"))
    }

    // 28. Unavailable GPS and location timeout handling
    @Test
    fun test28_unavailableGpsAndLocationTimeoutHandling() {
        AstrynLocationManager.simulatedPermissionState = LocationPermissionState.GRANTED_FINE
        AstrynLocationManager.simulatedGpsEnabled = false

        val gpsOffRes = AstrynLocationManager.resolveLocationForWeather("user_gps", "Aaj mausam kaisa hai?")
        assertEquals(LocationResolutionStatus.NEEDS_CITY_GPS_DISABLED, gpsOffRes.status)
        assertTrue(gpsOffRes.userFacingExplanation.contains("turned off"))

        AstrynLocationManager.simulatedGpsEnabled = true
        AstrynLocationManager.simulatedLocationTimeout = true
        val timeoutRes = AstrynLocationManager.resolveLocationForWeather("user_gps", "Aaj mausam kaisa hai?")
        assertEquals(LocationResolutionStatus.NEEDS_CITY_LOCATION_TIMEOUT, timeoutRes.status)
        assertTrue(timeoutRes.userFacingExplanation.contains("timed out"))
    }

    // 29. Weather Alert integrity (never inventing alerts; surfacing real severe threshold alert)
    @Test
    fun test29_weatherAlertHonestyNoFabrication() {
        AstrynLocationManager.setPreferredWeatherCity("user_alert", "Hyderabad")
        val calmReply = AstrynPersonalIntelligenceEngine.generatePersonalOrEnvironmentResponse(
            userId = "user_alert",
            query = "Any weather alert in Hyderabad?"
        )
        assertNotNull(calmReply)
        assertTrue(calmReply!!.contains("No active severe weather alerts"))

        // Now test OpenMeteo parser when WMO code 95 (Thunderstorm) + 68 km/h wind is returned
        val stormJson = """
            {
              "current": {
                "temperature_2m": 26.0,
                "apparent_temperature": 28.0,
                "relative_humidity_2m": 88,
                "precipitation": 30.0,
                "wind_speed_10m": 68.0,
                "wind_direction_10m": 180,
                "surface_pressure": 996.0,
                "weather_code": 95
              },
              "hourly": {
                "time": ["2026-05-12T18:00"],
                "temperature_2m": [26.0],
                "precipitation_probability": [95],
                "precipitation": [30.0],
                "weather_code": [95],
                "wind_speed_10m": [68.0]
              },
              "daily": {
                "time": ["2026-05-12"],
                "temperature_2m_max": [31.0],
                "temperature_2m_min": [24.0],
                "precipitation_sum": [45.0],
                "precipitation_probability_max": [95],
                "weather_code": [95]
              }
            }
        """.trimIndent()
        val parsedStorm = AstrynPersonalIntelligenceEngine.OpenMeteoHttpWeatherGateway.parseOpenMeteoResponse(
            rawJson = stormJson,
            cityName = "Hyderabad",
            country = "India",
            nowMillis = 5_000L
        )
        assertTrue(parsedStorm.alerts.isNotEmpty())
        assertEquals("WARNING", parsedStorm.alerts.first().severity)
    }

    // 30. Unrelated project isolation & non-intrusive personalization on generic factual queries
    @Test
    fun test30_unrelatedProjectIsolationAndNonIntrusivePersonalization() {
        AstrynProjectMemoryManager.upsertProject(
            userId = "user_multi",
            projectId = "fintrack",
            projectName = "FinTrack Ledger",
            objective = "Personal expense tracker with zero-knowledge encryption",
            pendingWork = listOf("Implement CSV export"),
            setAsActive = false
        )
        val resolvedFinTrack = AstrynContextResolver.resolveTargetProject("user_multi", "Tell me about FinTrack Ledger pending work")
        assertEquals("FinTrack Ledger", resolvedFinTrack.projectName)

        val resolvedAstryn = AstrynContextResolver.resolveTargetProject("user_multi", "Mere Astryn project ka next upgrade kya hai?")
        assertEquals("Astryn AI", resolvedAstryn.projectName)

        // Generic factual query should not force personal project context
        assertFalse(AstrynPersonalizationEngine.shouldApplyPersonalContext("What is 25 * 40?"))
        assertFalse(AstrynPersonalizationEngine.shouldApplyPersonalContext("Define photosynthesis"))
    }
}
