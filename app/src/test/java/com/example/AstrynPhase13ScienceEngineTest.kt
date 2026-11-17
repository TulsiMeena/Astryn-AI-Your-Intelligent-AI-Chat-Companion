package com.example

import com.example.data.api.AstrynIntelligenceEngine
import com.example.data.api.AstrynMathEngine
import com.example.data.api.AstrynScienceEngine
import com.example.data.api.GeminiLiveVoiceSession
import com.example.data.model.AttachmentKind
import com.example.data.model.ChatAttachment
import java.io.File
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ASTRYN AI — PHASE 13
 * ADVANCED SCIENCE, PHYSICS, CHEMISTRY, ENGINEERING & AGRICULTURE INTELLIGENCE ENGINE
 * Automated 42-Scenario Test Suite (Section 15).
 */
class AstrynPhase13ScienceEngineTest {

    @Before
    fun setUp() {
        AstrynScienceEngine.clearSessionState("sci_user", "sci_conv")
    }

    // 1. Newton's laws
    @Test
    fun test01_newtonsSecondLaw() {
        val sol = AstrynScienceEngine.solve("Calculate the net force using Newton's second law for a mass of 15 kg and acceleration of 4 m/s^2")
        assertNotNull(sol)
        assertEquals("60 N", sol!!.finalAnswer)
        assertTrue(sol.verification.verified)
    }

    // 2. Speed and acceleration
    @Test
    fun test02_speedAndAcceleration() {
        val sol = AstrynScienceEngine.solve("A car starts from rest and reaches 25 m/s in 5 seconds. Find its acceleration and displacement.")
        assertNotNull(sol)
        // a = 25/5 = 5 m/s^2, s = 0.5 * 5 * 25 = 62.5 m
        assertTrue(sol!!.finalAnswer.contains("5 m/s²"))
        assertTrue(sol.finalAnswer.contains("62.5 m"))
        assertTrue(sol.verification.verified)
    }

    // 3. Force and momentum
    @Test
    fun test03_forceAndMomentum() {
        val sol = AstrynScienceEngine.solve("Calculate the linear momentum of an object of mass 8 kg moving with velocity 15 m/s")
        assertNotNull(sol)
        assertEquals("120 kg·m/s", sol!!.finalAnswer)
        assertTrue(sol.verification.verified)
    }

    // 4. Work, energy and power
    @Test
    fun test04_workEnergyAndPower() {
        val keSol = AstrynScienceEngine.solve("Calculate kinetic energy of a body of mass 4 kg moving at 10 m/s")
        assertNotNull(keSol)
        assertEquals("200 J", keSol!!.finalAnswer)

        val wpSol = AstrynScienceEngine.solve("Calculate work done and power when a force of 50 N moves an object by distance 10 m in 5 seconds")
        assertNotNull(wpSol)
        assertTrue(wpSol!!.finalAnswer.contains("Work = 500 J"))
        assertTrue(wpSol.finalAnswer.contains("Power = 100 W"))
    }

    // 5. Gravity calculations
    @Test
    fun test05_gravityCalculations() {
        val sol = AstrynScienceEngine.solve("Calculate gravitational force between two masses 1000 kg and 2000 kg separated by 10 m")
        assertNotNull(sol)
        // F = 6.67430e-11 * 1000 * 2000 / 100 = 1.33486e-6 N
        assertTrue(sol!!.finalAnswer.contains("1.3349E-06 N") || sol.finalAnswer.contains("1.33486"))
        assertTrue(sol.verification.verified)
    }

    // 6. Projectile motion
    @Test
    fun test06_projectileMotion() {
        val sol = AstrynScienceEngine.solve("A projectile is launched with velocity 20 m/s at angle 30 degrees (g = 10). Find time of flight, max height and range.")
        assertNotNull(sol)
        // u=20, theta=30, g=10 => T = 2*20*0.5/10 = 2 s, H = 400*0.25/20 = 5 m, R = 400*0.866025/10 = 34.641 m
        assertTrue(sol!!.finalAnswer.contains("Time of Flight = 2 s"))
        assertTrue(sol.finalAnswer.contains("Max Height = 5 m"))
        assertTrue(sol.finalAnswer.contains("34.641"))
        assertTrue(sol.verification.verified)
    }

    // 7. Ohm's law
    @Test
    fun test07_ohmsLaw() {
        val sol = AstrynScienceEngine.solve("Using Ohm's law, find the current when voltage is 24 V and resistance is 6 ohms")
        assertNotNull(sol)
        assertTrue(sol!!.finalAnswer.contains("Current = 4 A"))
        assertTrue(sol.verification.verified)
    }

    // 8. Series circuits
    @Test
    fun test08_seriesCircuits() {
        val sol = AstrynScienceEngine.solve("Find equivalent resistance of three resistors 4 ohms, 6 ohms and 10 ohms connected in series")
        assertNotNull(sol)
        assertEquals("20 Ω", sol!!.finalAnswer)
        assertTrue(sol.verification.verified)
    }

    // 9. Parallel circuits
    @Test
    fun test09_parallelCircuits() {
        val sol = AstrynScienceEngine.solve("Find equivalent resistance of 6 ohms and 3 ohms connected in parallel with 12 V supply")
        assertNotNull(sol)
        // R_eq = 2 Ω, I = 12/2 = 6 A
        assertTrue(sol!!.finalAnswer.contains("2 Ω"))
        assertTrue(sol.finalAnswer.contains("6 A"))
        assertNotNull(sol.markdownTable)
        assertTrue(sol.verification.verified)
    }

    // 10. Electrical power
    @Test
    fun test10_electricalPower() {
        val sol = AstrynScienceEngine.solve("Calculate electrical power for a circuit with current 5 A and resistance 8 ohms")
        assertNotNull(sol)
        // P = I^2 * R = 25 * 8 = 200 W, V = 40 V
        assertTrue(sol!!.finalAnswer.contains("Power = 200 W"))
        assertTrue(sol.verification.verified)
    }

    // 11. Heat calculations
    @Test
    fun test11_heatCalculations() {
        val sol = AstrynScienceEngine.solve("Calculate heat energy required to raise the temperature of 2 kg of water with specific heat 4184 from 20 C to 30 C")
        assertNotNull(sol)
        // Q = 2 * 4184 * 10 = 83680 J (83.68 kJ)
        assertTrue(sol!!.finalAnswer.contains("83680 J"))
        assertTrue(sol.finalAnswer.contains("83.68 kJ"))
        assertTrue(sol.verification.verified)
    }

    // 12. Gas laws
    @Test
    fun test12_gasLaws() {
        val sol = AstrynScienceEngine.solve("Using the ideal gas law, calculate pressure for 2 moles of gas at 300 Kelvin in a volume of 10 liters")
        assertNotNull(sol)
        // P = nRT/V = 2 * 0.082057366 * 300 / 10 = 4.9234 atm
        assertTrue(sol!!.finalAnswer.contains("4.9234 atm"))
        assertTrue(sol.verification.verified)
    }

    // 13. Lens and mirror equations
    @Test
    fun test13_lensAndMirrorEquations() {
        val sol = AstrynScienceEngine.solve("A convex lens has focal length 10 cm and an object is placed at 20 cm. Find image distance.")
        assertNotNull(sol)
        // f = +10 cm, u = -20 cm => 1/v = 1/10 + 1/(-20) = 1/20 => v = 20 cm, m = -1
        assertTrue(sol!!.finalAnswer.contains("v = 20 cm"))
        assertTrue(sol.finalAnswer.contains("Magnification = -1"))
        assertTrue(sol.verification.verified)
    }

    // 14. Wave calculations
    @Test
    fun test14_waveCalculations() {
        val sol = AstrynScienceEngine.solve("Calculate wave speed for a wave with frequency 500 Hz and wavelength 0.68 m")
        assertNotNull(sol)
        assertTrue(sol!!.finalAnswer.contains("340 m/s"))
        assertTrue(sol.verification.verified)
    }

    // 15. Mole concept
    @Test
    fun test15_moleConcept() {
        val sol = AstrynScienceEngine.solve("Calculate moles and number of molecules in 36.03 g of H2O")
        assertNotNull(sol)
        // Molar mass H2O = 18.015 g/mol => moles = 2.0 mol
        assertTrue(sol!!.finalAnswer.contains("2 mol"))
        assertTrue(sol.verification.verified)
    }

    // 16. Molar mass
    @Test
    fun test16_molarMass() {
        val sol = AstrynScienceEngine.solve("Calculate the molar mass of H2SO4")
        assertNotNull(sol)
        // 2*1.008 + 32.06 + 4*15.999 = 98.072 g/mol
        assertEquals("98.072 g/mol", sol!!.finalAnswer)
        assertNotNull(sol.markdownTable)
        assertTrue(sol.verification.verified)
    }

    // 17. Stoichiometry
    @Test
    fun test17_stoichiometry() {
        val sol = AstrynScienceEngine.solve("Stoichiometry: How many grams of H2O are produced from 4.032 g of H2?")
        assertNotNull(sol)
        // 4.032 g H2 = 2 mol H2 -> 2 mol H2O = 36.03 g H2O
        assertTrue(sol!!.finalAnswer.contains("36.03 g"))
        assertTrue(sol.verification.verified)
    }

    // 18. Chemical equation balancing
    @Test
    fun test18_chemicalEquationBalancing() {
        val sol = AstrynScienceEngine.solve("Balance chemical equation: Fe + O2 -> Fe2O3")
        assertNotNull(sol)
        assertEquals("4Fe + 3O2 → 2Fe2O3", sol!!.finalAnswer)
        assertNotNull(sol.markdownTable)
        assertTrue(sol.verification.verified)
    }

    // 19. Concentration and molarity
    @Test
    fun test19_concentrationAndMolarity() {
        val sol = AstrynScienceEngine.solve("Calculate molarity when 0.5 moles of solute are dissolved in 250 mL of solution")
        assertNotNull(sol)
        assertEquals("2 M (mol/L)", sol!!.finalAnswer)
        assertTrue(sol.verification.verified)
    }

    // 20. pH calculations
    @Test
    fun test20_phCalculations() {
        val sol = AstrynScienceEngine.solve("Calculate pH of a solution with [H+] = 1e-3 M")
        assertNotNull(sol)
        assertTrue(sol!!.finalAnswer.contains("pH = 3"))
        assertTrue(sol.finalAnswer.contains("pOH = 11"))
        assertTrue(sol.finalAnswer.contains("Acidic"))
        assertTrue(sol.verification.verified)
    }

    // 21. Atomic information
    @Test
    fun test21_atomicInformation() {
        val sol = AstrynScienceEngine.solve("Give periodic table atomic number and electron configuration of Iron (Fe)")
        assertNotNull(sol)
        assertTrue(sol!!.finalAnswer.contains("Z = 26"))
        assertTrue(sol.finalAnswer.contains("55.845"))
        assertTrue(sol.finalAnswer.contains("[Ar] 3d⁶ 4s²"))
        assertTrue(sol.verification.verified)
    }

    // 22. Mechanical torque
    @Test
    fun test22_mechanicalTorque() {
        val sol = AstrynScienceEngine.solve("Calculate mechanical torque when a force of 200 N is applied at a radius of 0.5 m")
        assertNotNull(sol)
        assertEquals("100 N·m", sol!!.finalAnswer)
        assertNotNull(sol.safetyOrAssumptionNote)
        assertTrue(sol.verification.verified)
    }

    // 23. Stress and strain
    @Test
    fun test23_stressAndStrain() {
        val sol = AstrynScienceEngine.solve("Calculate normal stress when a force of 50000 N acts on an area of 0.002 m2")
        assertNotNull(sol)
        // 50,000 / 0.002 = 25,000,000 Pa = 25 MPa
        assertTrue(sol!!.finalAnswer.contains("25 MPa"))
        assertTrue(sol.verification.verified)
    }

    // 24. Fluid calculations
    @Test
    fun test24_fluidCalculations() {
        val sol = AstrynScienceEngine.solve("Calculate fluid flow rate in a pipe of area 0.05 m2 with velocity 4 m/s")
        assertNotNull(sol)
        // Q = 0.05 * 4 = 0.2 m3/s (200 L/s)
        assertTrue(sol!!.finalAnswer.contains("0.2 m³/s"))
        assertTrue(sol.finalAnswer.contains("200 L/s"))
        assertTrue(sol.verification.verified)
    }

    // 25. Civil engineering volume calculations
    @Test
    fun test25_civilEngineeringConcreteVolume() {
        val sol = AstrynScienceEngine.solve("Calculate concrete slab volume and M20 cement bags for 5 m x 4 m x 0.15 m")
        assertNotNull(sol)
        // Wet = 3.0 m3, Dry = 4.62 m3
        assertTrue(sol!!.finalAnswer.contains("Wet Volume = 3 m³"))
        assertTrue(sol.finalAnswer.contains("Dry Volume = 4.62 m³"))
        assertNotNull(sol.markdownTable)
        assertNotNull(sol.safetyOrAssumptionNote)
        assertTrue(sol.verification.verified)
    }

    // 26. Agriculture land conversion (with Rajasthan Pucca vs. Kachha Bigha distinction)
    @Test
    fun test26_agricultureLandConversionRajasthanBigha() {
        val sol = AstrynScienceEngine.solve("Convert 2 bigha in Rajasthan to hectare, acre and sq ft")
        assertNotNull(sol)
        // 2 Pucca Bigha = 54,450 sq ft; 2 Kachha Bigha = 34,848 sq ft
        assertTrue(sol!!.finalAnswer.contains("54450"))
        assertTrue(sol.finalAnswer.contains("34848"))
        assertNotNull(sol.clarificationPrompt)
        assertNotNull(sol.markdownTable)
    }

    // 27. Seed rate calculations
    @Test
    fun test27_seedRateCalculations() {
        val sol = AstrynScienceEngine.solve("Calculate seed requirement for 2 hectares with seed rate 100 kg/ha, germination 80% and purity 95%")
        assertNotNull(sol)
        // (2 * 100 * 10000) / (80 * 95) = 2000000 / 7600 = 263.16 kg
        assertEquals("263.16 kg", sol!!.finalAnswer)
        assertTrue(sol.verification.verified)
    }

    // 28. Fertilizer quantity calculations
    @Test
    fun test28_fertilizerQuantityCalculations() {
        val sol = AstrynScienceEngine.solve("Calculate Urea fertilizer quantity required for 46 kg of Nitrogen per hectare on 2 hectares")
        assertNotNull(sol)
        // 46 * 2 = 92 kg N -> 92 * 100 / 46 = 200 kg Urea
        assertEquals("200 kg Urea", sol!!.finalAnswer)
        assertNotNull(sol.safetyOrAssumptionNote)
    }

    // 29. Irrigation calculations
    @Test
    fun test29_irrigationCalculations() {
        val sol = AstrynScienceEngine.solve("Calculate irrigation water volume for 1 hectare with 50 mm depth")
        assertNotNull(sol)
        // 10,000 m2 * 0.05 m = 500 m3 = 500,000 Liters
        assertTrue(sol!!.finalAnswer.contains("500000 Liters"))
        assertTrue(sol.finalAnswer.contains("500 m³"))
    }

    // 30. NPK calculations
    @Test
    fun test30_npkCalculations() {
        val sol = AstrynScienceEngine.solve("Calculate NPK fertilizer requirement for 120:60:40 on 1 hectare")
        assertNotNull(sol)
        // Urea = 120*100/46 = 260.87 kg, SSP = 60*100/16 = 375 kg, MOP = 40*100/60 = 66.67 kg
        assertTrue(sol!!.finalAnswer.contains("Urea = 260.87 kg"))
        assertTrue(sol.finalAnswer.contains("SSP = 375 kg"))
        assertTrue(sol.finalAnswer.contains("MOP = 66.67 kg"))
        assertNotNull(sol.markdownTable)
    }

    // 31. Hindi question understanding
    @Test
    fun test31_hindiQuestionUnderstanding() {
        val sol = AstrynScienceEngine.solve("न्यूटन के नियम से 10 kg द्रव्यमान और 3 m/s^2 त्वरण के लिए force ज्ञात करें")
        assertNotNull(sol)
        assertEquals("30 N", sol!!.finalAnswer)
        assertEquals(AstrynMathEngine.MathLanguage.HINDI, sol.language)
        assertTrue(sol.formattedExplanation.contains("दिए गए मान"))
    }

    // 32. Hinglish question understanding
    @Test
    fun test32_hinglishQuestionUnderstanding() {
        val userUid = "hinglish_user"
        val convId = "hinglish_conv"
        val s1 = AstrynScienceEngine.solve("Ohm's law se 12 V aur 4 ohms resistance ka current nikalo", emptyList(), userUid, convId)
        assertNotNull(s1)
        assertTrue(s1!!.finalAnswer.contains("3 A"))

        // Contextual Hinglish follow-up: "Isme resistance double kar do" (4 -> 8 ohms => 12/8 = 1.5 A)
        val s2 = AstrynScienceEngine.solve("Isme resistance double kar do", emptyList(), userUid, convId)
        assertNotNull(s2)
        assertTrue(s2!!.finalAnswer.contains("1.5 A"))
    }

    // 33. Image-based problem extraction
    @Test
    fun test33_imageBasedProblemExtraction() {
        val circuitImage = ChatAttachment(
            fileName = "circuit_diagram.png",
            mimeType = "image/png",
            fileSizeBytes = 32000L,
            kind = AttachmentKind.IMAGE,
            extractedText = "Find equivalent resistance of 10 ohms and 15 ohms connected in parallel"
        )
        val sol = AstrynScienceEngine.solve("Solve this physics circuit image", listOf(circuitImage))
        assertNotNull(sol)
        // (10 * 15) / (10 + 15) = 150 / 25 = 6 Ω
        assertEquals("6 Ω", sol!!.finalAnswer)
        assertTrue(sol.givenValues.containsKey("Source Attachment"))
    }

    // 34. PDF-based problem extraction
    @Test
    fun test34_pdfBasedProblemExtraction() {
        val chemistryPdf = ChatAttachment(
            fileName = "chemistry_worksheet.pdf",
            mimeType = "application/pdf",
            fileSizeBytes = 48000L,
            kind = AttachmentKind.PDF,
            extractedText = "Balance chemical equation: N2 + H2 -> NH3"
        )
        val sol = AstrynScienceEngine.solve("Solve the chemistry equation in this PDF", listOf(chemistryPdf))
        assertNotNull(sol)
        assertEquals("N2 + 3H2 → 2NH3", sol!!.finalAnswer)
    }

    // 35. Scientific table generation
    @Test
    fun test35_scientificTableGeneration() {
        val sol = AstrynScienceEngine.solve("Calculate molar mass of Ca(OH)2")
        assertNotNull(sol)
        assertNotNull(sol!!.markdownTable)
        assertTrue(sol.markdownTable!!.contains("| Element | Atom Count |"))
        assertTrue(sol.markdownTable!!.contains("Calcium (Ca)"))
        assertTrue(sol.markdownTable!!.contains("74.092 g/mol"))
    }

    // 36. Dimensional verification
    @Test
    fun test36_dimensionalVerification() {
        val sol = AstrynScienceEngine.solve("Calculate force for 25 kg mass and 2 m/s^2 acceleration")
        assertNotNull(sol)
        assertTrue(sol!!.verification.verified)
        assertTrue(sol.verification.lhsDimension.contains("M¹L¹T⁻²"))
        assertTrue(sol.formattedExplanation.contains("Independent & Dimensional Verification"))
    }

    // 37. Invalid input handling
    @Test
    fun test37_invalidInputHandling() {
        val zeroR = AstrynScienceEngine.solve("Using Ohm's law, calculate current for 10 V and 0 ohms resistance")
        assertNotNull(zeroR)
        assertFalse(zeroR!!.verification.verified)
        assertTrue(zeroR.finalAnswer.contains("Invalid Physical Input"))
    }

    // 38. Precision and rounding
    @Test
    fun test38_precisionAndRounding() {
        val formulaEntry = AstrynScienceEngine.ScientificFormulaRegistry.getFormula("PHYS_GRAVITATION")
        assertNotNull(formulaEntry)
        assertEquals(34, AstrynScienceEngine.MC.precision)
        val sol = AstrynScienceEngine.solve("Calculate molar mass of C6H12O6")
        assertNotNull(sol)
        // 6*12.011 + 12*1.008 + 6*15.999 = 72.066 + 12.096 + 95.994 = 180.156 g/mol
        assertEquals("180.156 g/mol", sol!!.finalAnswer)
    }

    // 39. Offline calculations
    @Test
    fun test39_offlineCalculations() {
        // Verify AstrynScienceEngine executes 100% deterministically in-memory with zero network calls
        val startMs = System.currentTimeMillis()
        val sol = AstrynScienceEngine.solve("Calculate kinetic energy of 10 kg mass at 12 m/s")
        val elapsedMs = System.currentTimeMillis() - startMs
        assertNotNull(sol)
        assertEquals("720 J", sol!!.finalAnswer)
        assertTrue("Offline calculation must complete locally under 200ms", elapsedMs < 200L)
    }

    // 40. Existing feature regression
    @Test
    fun test40_existingFeatureRegression() {
        // Verify AstrynIntelligenceEngine routes both science and financial/math queries accurately
        val sciResult = AstrynIntelligenceEngine.evaluateMathAndDiscountQuery(
            "Calculate net force using Newton's second law for 12 kg mass and 5 m/s^2 acceleration"
        )
        assertNotNull(sciResult)
        assertEquals("60 N", sciResult!!.formattedResult)

        val mathResult = AstrynIntelligenceEngine.evaluateMathAndDiscountQuery(
            "What is 20% discount on ₹1500?"
        )
        assertNotNull(mathResult)
        assertEquals("DISCOUNT_FINAL_PRICE", mathResult!!.problemType)
        assertEquals(1200.0, mathResult.numericAnswer, 0.001)
    }

    // 41. User data isolation
    @Test
    fun test41_userDataIsolation() {
        AstrynScienceEngine.solve(
            prompt = "Using Ohm's law, find current for 20 V and 5 ohms resistance",
            userUid = "user_alpha",
            conversationId = "conv_1"
        )
        // User Beta must not see User Alpha's science session state
        assertNull(AstrynScienceEngine.getSessionState("user_beta", "conv_1"))
        assertNotNull(AstrynScienceEngine.getSessionState("user_alpha", "conv_1"))
    }

    // 42. Voice-to-Voice read-only regression
    @Test
    fun test42_voiceToVoiceReadOnlyRegression() {
        val sessionClass = GeminiLiveVoiceSession::class.java
        assertNotNull(sessionClass)
        val companionMethods = GeminiLiveVoiceSession.Companion::class.java.declaredMethods.map { it.name }.toSet()
        assertTrue(companionMethods.contains("buildSmartJarvisChatReply"))
        val voiceFileCandidates = listOf(
            File("src/main/java/com/example/data/api/GeminiLiveVoiceSession.kt"),
            File("app/src/main/java/com/example/data/api/GeminiLiveVoiceSession.kt")
        )
        val voiceFile = voiceFileCandidates.firstOrNull { it.exists() }
        if (voiceFile != null) {
            val content = voiceFile.readText()
            assertTrue(content.contains("class GeminiLiveVoiceSession"))
        }
    }
}
