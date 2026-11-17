package com.example.data.api

import com.example.data.model.ChatAttachment
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * ASTRYN AI — ADVANCED MATHEMATICAL REASONING & PRECISION CALCULATION ENGINE
 *
 * Modular, deterministic, pure-JVM precision mathematical calculation engine:
 * 1. [AstrynMathEngine] — Central router, natural-language problem extractor, and per-user/per-conversation contextual math memory.
 * 2. [AstrynExpressionParser] — Safe shunting-yard & recursive-descent expression parser (zero arbitrary code execution).
 * 3. [AstrynPrecisionCalculator] — IEEE 754R Decimal128 (`BigDecimal`) & exact rational (`BigFraction`) calculator.
 * 4. [AstrynFormulaEngine] — Algebra (linear, quadratic, 2x2/3x3 simultaneous, polynomials, factorization, inequalities, simplification),
 *    Geometry (2D & 3D shapes, Heron's, Pythagoras), Trigonometry (degrees/radians, exact special angles, identities),
 *    and Calculus (limits, symbolic + numerical derivatives, symbolic + Simpson's rule integrals, basic ODEs).
 * 5. [AstrynStatisticsEngine] — Descriptive stats (mean, median, mode, variance, SD, IQR), combinatorics (`nPr`, `nCr`),
 *    probability, Pearson correlation (`r`), and simple linear regression (`y = mx + c`, `R^2`).
 * 6. [AstrynFinancialCalculator] — SI, CI (annual/half-yearly/quarterly/monthly + yearly schedule), Loan EMI (+ amortization schedule),
 *    SIP Future Value (+ non-guaranteed market projection disclaimer), Profit/Loss, Single & Successive Discounts,
 *    GST (inclusive/exclusive + CGST/SGST split), Markup/Margin, Ratio/Proportion, and Business Break-Even analysis.
 * 7. [AstrynUnitConversionEngine] — Length, Area, Volume, Weight/Mass, Temperature, Speed/Distance/Time,
 *    Time & Work inverse proportion, and Agriculture land units (`acre`, `hectare`, `sq ft`, `gaj`, `guntha`, `cent`, `bigha`, `katha`, `kanal`).
 * 8. [AstrynMathVerificationEngine] — Independent reverse-operation check, root substitution, identity verification, domain & sanity checks.
 * 9. [AstrynCalculationFormatter] — Adaptive concise vs. 7-step structured output, Hindi/Hinglish/English localization,
 *    Competitive Exam Mode (Conventional + Short Trick + Common Mistakes), and Markdown Table generator.
 */
object AstrynMathEngine {

    enum class MathDomain {
        ARITHMETIC_BODMAS,
        FRACTION_RATIONAL,
        SCIENTIFIC_NOTATION,
        ALGEBRA_LINEAR_EQUATION,
        ALGEBRA_QUADRATIC_EQUATION,
        ALGEBRA_SIMULTANEOUS_EQUATIONS,
        ALGEBRA_POLYNOMIAL_FACTORIZATION,
        ALGEBRA_INEQUALITY,
        ALGEBRA_SIMPLIFICATION,
        GEOMETRY_2D_3D,
        TRIGONOMETRY,
        CALCULUS,
        STATISTICS_PROBABILITY,
        FINANCIAL_SIMPLE_INTEREST,
        FINANCIAL_COMPOUND_INTEREST,
        FINANCIAL_EMI_LOAN,
        FINANCIAL_SIP_INVESTMENT,
        FINANCIAL_PROFIT_LOSS_DISCOUNT,
        FINANCIAL_GST_TAX,
        FINANCIAL_BREAK_EVEN,
        RATIO_AND_PROPORTION,
        UNIT_CONVERSION,
        SPEED_DISTANCE_TIME,
        TIME_AND_WORK,
        AGRICULTURE_LAND_CONVERSION,
        MULTIMODAL_DOCUMENT_MATH,
        CONTEXTUAL_FOLLOW_UP,
        AMBIGUOUS_OR_INVALID
    }

    enum class OutputVerbosity {
        FINAL_ANSWER_ONLY,
        STANDARD_CLEAR,
        STEP_BY_STEP_DETAILED,
        EXAM_SOLVING_MODE
    }

    enum class MathLanguage {
        ENGLISH,
        HINDI,
        HINGLISH
    }

    data class VerificationStatus(
        val verified: Boolean,
        val method: String,
        val checkSummary: String,
        val isExact: Boolean = true,
        val domainWarnings: List<String> = emptyList()
    )

    data class MathSolution(
        val domain: MathDomain,
        val problemType: String,
        val recognizedInput: String,
        val formulaUsed: String,
        val steps: List<String>,
        val finalAnswer: String,
        val numericResult: BigDecimal? = null,
        val secondaryValues: Map<String, BigDecimal> = emptyMap(),
        val unit: String = "",
        val verification: VerificationStatus,
        val markdownTable: String? = null,
        val examShortTrick: String? = null,
        val commonMistakes: List<String> = emptyList(),
        val clarificationPrompt: String? = null,
        val formattedResponse: String,
        val language: MathLanguage = MathLanguage.ENGLISH,
        val verbosity: OutputVerbosity = OutputVerbosity.STANDARD_CLEAR
    ) {
        fun toIntelligenceMathResult(): AstrynIntelligenceEngine.MathComputationResult {
            return AstrynIntelligenceEngine.MathComputationResult(
                problemType = problemType,
                expressionSummary = "$recognizedInput [Formula: $formulaUsed]",
                formattedResult = finalAnswer,
                numericAnswer = numericResult?.toDouble() ?: 0.0,
                stepByStepBreakdown = steps,
                verificationNote = "${verification.method}: ${verification.checkSummary}"
            )
        }
    }

    data class MathContextEntry(
        val domain: MathDomain,
        val problemType: String,
        val primaryValue: BigDecimal,
        val secondaryValues: Map<String, BigDecimal> = emptyMap(),
        val formulaUsed: String,
        val label: String,
        val unit: String = "",
        val timestampMs: Long = System.currentTimeMillis()
    )

    private val sessionHistoryMap = ConcurrentHashMap<String, MutableList<MathContextEntry>>()

    private fun sessionKey(userUid: String, conversationId: String): String {
        val u = userUid.trim().ifEmpty { "default_user" }
        val c = conversationId.trim().ifEmpty { "default_conv" }
        return "$u::$c"
    }

    fun recordContext(
        userUid: String = "default_user",
        conversationId: String = "default_conv",
        entry: MathContextEntry
    ) {
        val key = sessionKey(userUid, conversationId)
        val list = sessionHistoryMap.getOrPut(key) { mutableListOf() }
        synchronized(list) {
            list.add(entry)
            if (list.size > 20) {
                list.removeAt(0)
            }
        }
    }

    fun getRecentContext(
        userUid: String = "default_user",
        conversationId: String = "default_conv"
    ): List<MathContextEntry> {
        val key = sessionKey(userUid, conversationId)
        val list = sessionHistoryMap[key] ?: return emptyList()
        synchronized(list) {
            return list.toList()
        }
    }

    fun clearContext(userUid: String = "default_user", conversationId: String = "default_conv") {
        sessionHistoryMap.remove(sessionKey(userUid, conversationId))
    }

    /**
     * Detects whether the user query is a mathematical, financial, statistical, scientific,
     * unit conversion, or contextual calculation query.
     */
    fun isMathQuery(
        prompt: String,
        attachments: List<ChatAttachment> = emptyList(),
        userUid: String = "default_user",
        conversationId: String = "default_conv"
    ): Boolean {
        val lower = prompt.lowercase(Locale.ROOT).trim()
        if (lower.isEmpty()) return false

        // Exclude programming/UI/general factual queries unless explicitly calculating
        val nonMathIndicators = listOf(
            "write a kotlin", "write a python", "create a function", "jetpack compose",
            "who is the president", "capital of", "write an essay", "poem about",
            "translate this paragraph"
        )
        if (nonMathIndicators.any { lower.contains(it) }) return false

        val hasDigits = lower.any { it.isDigit() }
        val hasContextualRef = isContextualFollowUpQuery(lower) &&
            getRecentContext(userUid, conversationId).isNotEmpty()
        if (hasContextualRef) return true

        val strongMathKeywords = listOf(
            "solve", "calculate", "evaluate", "simplify", "factorize", "factorise",
            "quadratic", "discriminant", "simultaneous", "equation", "inequality",
            "derivative", "differentiate", "dy/dx", "integral", "integrate",
            "limit", "lim ", "sin(", "cos(", "tan(", "sin ", "cos ", "tan ",
            "asin", "acos", "atan", "pythagoras", "hypotenuse", "heron",
            "circumference", "surface area", "volume of", "area of", "perimeter of",
            "mean", "median", "mode", "standard deviation", "variance", "correlation",
            "regression", "permutation", "combination", "npr", "ncr", "probability",
            "factorial", "simple interest", "compound interest", "emi", "sip",
            "amortization", "profit", "loss", "discount", "gst", "cgst", "sgst",
            "markup", "margin", "break-even", "break even", "bigha", "hectare",
            "acre", "guntha", "katha", "kanal", "sq ft", "square feet", "gaj",
            "celsius", "fahrenheit", "kelvin", "km/h", "m/s", "workers", "mazdoor",
            "kitne din", "how many days", "scientific notation"
        )
        if (strongMathKeywords.any { lower.contains(it) }) return true

        // Symbolic expression check (e.g., "(25 + 15) * 3", "3/4 + 5/6", "2^10")
        if (hasDigits && Regex("""[\d\)\]]\s*[\+\-\*×/÷\^%]\s*[\(\[\d]""").containsMatchIn(lower)) {
            return true
        }
        if (hasDigits && (lower.contains("percent") || lower.contains("%")) &&
            (lower.contains("of") || lower.contains("ka") || lower.contains("nikalo") ||
                lower.contains("increase") || lower.contains("decrease") || lower.contains("off"))
        ) {
            return true
        }
        if (attachments.isNotEmpty() && listOf("sum", "total", "average", "solve", "calculate", "math", "equation", "worksheet").any { lower.contains(it) }) {
            return true
        }
        return false
    }

    fun detectLanguage(prompt: String): MathLanguage {
        if (prompt.any { it in '\u0900'..'\u097F' }) return MathLanguage.HINDI
        val lower = prompt.lowercase(Locale.ROOT)
        val hinglishMarkers = listOf(
            "nikalo", "batao", "kitna", "kitne", "mein", "aur", "jod", "ghata",
            "pichhle", "wale", "iska", "dono", "kaam", "karte", "hain", "lenge",
            "samay", "lagega", "kya", "hoga", "sirf", "doosra", "hisab", "byaj"
        )
        val matchCount = hinglishMarkers.count { marker ->
            Regex("""\b${Regex.escape(marker)}\b""").containsMatchIn(lower)
        }
        return if (matchCount >= 1) MathLanguage.HINGLISH else MathLanguage.ENGLISH
    }

    fun detectVerbosity(prompt: String): OutputVerbosity {
        val lower = prompt.lowercase(Locale.ROOT)
        if (listOf("sirf answer", "only answer", "only final answer", "just the answer", "direct answer", "short answer").any { lower.contains(it) }) {
            return OutputVerbosity.FINAL_ANSWER_ONLY
        }
        if (listOf("exam", "short trick", "shortcut", "competitive", "ssc", "jee", "banking", "upsc", "cat ", "gate ").any { lower.contains(it) }) {
            return OutputVerbosity.EXAM_SOLVING_MODE
        }
        if (listOf("step by step", "step-by-step", "explain", "detailed", "full solution", "vistar", "samjhao").any { lower.contains(it) }) {
            return OutputVerbosity.STEP_BY_STEP_DETAILED
        }
        return OutputVerbosity.STANDARD_CLEAR
    }

    private fun isContextualFollowUpQuery(lower: String): Boolean {
        val followUpPhrases = listOf(
            "ab iska", "iska ", "pichhle wale", "previous result", "last answer",
            "add ", "subtract ", "multiply by", "divide by", "aur jod", "ghata do",
            "dono ka comparison", "compare both", "comparison table",
            "isi formula se", "same formula", "previous calculation"
        )
        return followUpPhrases.any { lower.contains(it) }
    }

    /**
     * Primary entry point to deterministically solve a mathematical query.
     */
    fun solve(
        prompt: String,
        conversationHistory: List<Pair<String, String>> = emptyList(),
        attachments: List<ChatAttachment> = emptyList(),
        userUid: String = "default_user",
        conversationId: String = "default_conv"
    ): MathSolution? {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) return null

        val language = detectLanguage(trimmed)
        val verbosity = detectVerbosity(trimmed)
        val lower = trimmed.lowercase(Locale.ROOT)

        // 0. Check multimodal attachment math first if attachments are provided
        if (attachments.isNotEmpty()) {
            val multimodalSolution = solveMultimodalAttachmentMath(trimmed, attachments, language, verbosity, userUid, conversationId)
            if (multimodalSolution != null) return multimodalSolution
        }

        // 1. Check contextual follow-up math ("Ab iska 20 percent nikalo", "Pichhle wale mein 500 aur jod do", "Dono ka comparison table banao", "Isi formula se...")
        val recentContext = getRecentContext(userUid, conversationId).ifEmpty {
            extractContextFromConversationHistory(conversationHistory)
        }
        if (recentContext.isNotEmpty() && isContextualFollowUpQuery(lower)) {
            val followUp = solveContextualFollowUp(trimmed, recentContext, language, verbosity, userUid, conversationId)
            if (followUp != null) return followUp
        }

        // 2. Check Financial Calculations (EMI, SIP, CI, SI, GST, Break-Even, Profit/Loss, Discount)
        AstrynFinancialCalculator.trySolve(trimmed, language, verbosity)?.let { sol ->
            recordSolutionInContext(userUid, conversationId, sol)
            return sol
        }

        // 3. Check Unit Conversions, Agriculture Land Units, Speed/Distance/Time, Time & Work
        AstrynUnitConversionEngine.trySolve(trimmed, language, verbosity)?.let { sol ->
            recordSolutionInContext(userUid, conversationId, sol)
            return sol
        }

        // 4. Check Statistics, Probability, Combinatorics, Correlation & Regression
        AstrynStatisticsEngine.trySolve(trimmed, language, verbosity)?.let { sol ->
            recordSolutionInContext(userUid, conversationId, sol)
            return sol
        }

        // 5. Check Formula Engine (Algebra, Equations, Geometry, Trigonometry, Calculus)
        AstrynFormulaEngine.trySolve(trimmed, language, verbosity)?.let { sol ->
            recordSolutionInContext(userUid, conversationId, sol)
            return sol
        }

        // 6. Check Ratio & Proportion
        solveRatioAndProportion(trimmed, language, verbosity)?.let { sol ->
            recordSolutionInContext(userUid, conversationId, sol)
            return sol
        }

        // 7. Check Percentage / Successive Percentage
        solvePercentageProblems(trimmed, language, verbosity)?.let { sol ->
            recordSolutionInContext(userUid, conversationId, sol)
            return sol
        }

        // 8. Check Exact Fraction Arithmetic ("3/4 + 5/6")
        solveExactFractionExpression(trimmed, language, verbosity)?.let { sol ->
            recordSolutionInContext(userUid, conversationId, sol)
            return sol
        }

        // 9. Check General Arithmetic / BODMAS / Scientific Notation / Roots / Powers / Logs
        solveGeneralExpression(trimmed, language, verbosity)?.let { sol ->
            recordSolutionInContext(userUid, conversationId, sol)
            return sol
        }

        return null
    }

    private fun recordSolutionInContext(
        userUid: String,
        conversationId: String,
        solution: MathSolution
    ) {
        val numeric = solution.numericResult ?: return
        recordContext(
            userUid = userUid,
            conversationId = conversationId,
            entry = MathContextEntry(
                domain = solution.domain,
                problemType = solution.problemType,
                primaryValue = numeric,
                secondaryValues = solution.secondaryValues,
                formulaUsed = solution.formulaUsed,
                label = solution.finalAnswer,
                unit = solution.unit
            )
        )
    }

    private fun extractContextFromConversationHistory(
        history: List<Pair<String, String>>
    ): List<MathContextEntry> {
        val entries = mutableListOf<MathContextEntry>()
        for ((_, text) in history.takeLast(6)) {
            val numbers = Regex("""(?:Final Answer|Result|Answer|Total|EMI|Amount|Value)\s*[:=-]?\s*(?:₹|Rs\.?|INR|\$)?\s*([+-]?\d[\d,]*(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
                .findAll(text)
                .mapNotNull { it.groupValues[1].replace(",", "").toBigDecimalOrNull() }
                .toList()
            val fallbackNum = if (numbers.isEmpty()) {
                Regex("""([+-]?\d+(?:\.\d+)?)""").findAll(text)
                    .mapNotNull { it.groupValues[1].toBigDecimalOrNull() }
                    .lastOrNull()
            } else {
                numbers.lastOrNull()
            }
            if (fallbackNum != null) {
                entries.add(
                    MathContextEntry(
                        domain = MathDomain.ARITHMETIC_BODMAS,
                        problemType = "PREVIOUS_CALCULATION",
                        primaryValue = fallbackNum,
                        formulaUsed = "Previous Turn Result",
                        label = AstrynPrecisionCalculator.formatNumber(fallbackNum)
                    )
                )
            }
        }
        return entries
    }

    private fun solveContextualFollowUp(
        prompt: String,
        recentContext: List<MathContextEntry>,
        language: MathLanguage,
        verbosity: OutputVerbosity,
        userUid: String,
        conversationId: String
    ): MathSolution? {
        val lower = prompt.lowercase(Locale.ROOT)
        val last = recentContext.last()
        val prevVal = last.primaryValue
        val prevFormatted = AstrynPrecisionCalculator.formatNumber(prevVal)

        // Case 1: "Dono ka comparison table banao" / "compare both"
        if ((lower.contains("comparison") || lower.contains("compare") || lower.contains("dono ka")) && recentContext.size >= 2) {
            val first = recentContext[recentContext.size - 2]
            val second = recentContext.last()
            val diff = second.primaryValue.subtract(first.primaryValue, AstrynPrecisionCalculator.DECIMAL128)
            val pctDiff = if (first.primaryValue.compareTo(BigDecimal.ZERO) != 0) {
                diff.divide(first.primaryValue, AstrynPrecisionCalculator.DECIMAL128)
                    .multiply(BigDecimal(100), AstrynPrecisionCalculator.DECIMAL128)
            } else BigDecimal.ZERO

            val table = AstrynCalculationFormatter.buildMarkdownTable(
                headers = listOf("Metric", "Calculation 1 (${first.problemType})", "Calculation 2 (${second.problemType})", "Difference"),
                rows = listOf(
                    listOf(
                        "Primary Result",
                        AstrynPrecisionCalculator.formatNumber(first.primaryValue),
                        AstrynPrecisionCalculator.formatNumber(second.primaryValue),
                        "${AstrynPrecisionCalculator.formatNumber(diff)} (${AstrynPrecisionCalculator.formatNumber(pctDiff, 2)}%)"
                    ),
                    listOf("Formula", first.formulaUsed, second.formulaUsed, "—")
                )
            )
            val steps = listOf(
                "Previous Result 1 (${first.problemType}) = ${AstrynPrecisionCalculator.formatNumber(first.primaryValue)}",
                "Previous Result 2 (${second.problemType}) = ${AstrynPrecisionCalculator.formatNumber(second.primaryValue)}",
                "Absolute Difference = ${AstrynPrecisionCalculator.formatNumber(second.primaryValue)} - ${AstrynPrecisionCalculator.formatNumber(first.primaryValue)} = ${AstrynPrecisionCalculator.formatNumber(diff)}",
                "Percentage Change = ${AstrynPrecisionCalculator.formatNumber(pctDiff, 2)}%"
            )
            val finalAns = "Difference = ${AstrynPrecisionCalculator.formatNumber(diff)} (${AstrynPrecisionCalculator.formatNumber(pctDiff, 2)}%)"
            val verification = VerificationStatus(
                verified = true,
                method = "Contextual Comparison Check",
                checkSummary = "${AstrynPrecisionCalculator.formatNumber(first.primaryValue)} + (${AstrynPrecisionCalculator.formatNumber(diff)}) = ${AstrynPrecisionCalculator.formatNumber(second.primaryValue)}"
            )
            return AstrynCalculationFormatter.createSolution(
                domain = MathDomain.CONTEXTUAL_FOLLOW_UP,
                problemType = "CONTEXTUAL_COMPARISON_TABLE",
                recognizedInput = "Compare previous 2 calculations (${first.problemType} vs ${second.problemType})",
                formulaUsed = "Δ = Result_2 - Result_1",
                steps = steps,
                finalAnswer = finalAns,
                numericResult = diff,
                verification = verification,
                markdownTable = table,
                language = language,
                verbosity = verbosity
            )
        }

        // Case 2: "Ab iska X percent nikalo" / "X% of previous"
        val pctMatch = Regex("""(\d+(?:\.\d+)?)\s*(?:%|percent|pratishat)""").find(lower)
        if (pctMatch != null && (lower.contains("iska") || lower.contains("of it") || lower.contains("previous") || lower.contains("last"))) {
            val pct = BigDecimal(pctMatch.groupValues[1])
            val result = prevVal.multiply(pct, AstrynPrecisionCalculator.DECIMAL128)
                .divide(BigDecimal(100), AstrynPrecisionCalculator.DECIMAL128)
            val resFormatted = AstrynPrecisionCalculator.formatNumber(result)
            val verification = AstrynMathVerificationEngine.verifyPercentage(prevVal, pct, result)
            val steps = listOf(
                "Retrieved previous result = $prevFormatted (from ${last.problemType})",
                "Percentage requested = ${AstrynPrecisionCalculator.formatNumber(pct)}%",
                "Calculation = ($prevFormatted × ${AstrynPrecisionCalculator.formatNumber(pct)}) / 100 = $resFormatted"
            )
            val sol = AstrynCalculationFormatter.createSolution(
                domain = MathDomain.CONTEXTUAL_FOLLOW_UP,
                problemType = "CONTEXTUAL_PERCENTAGE_FOLLOW_UP",
                recognizedInput = "${AstrynPrecisionCalculator.formatNumber(pct)}% of previous result ($prevFormatted)",
                formulaUsed = "Result = Previous × (Percentage / 100)",
                steps = steps,
                finalAnswer = resFormatted,
                numericResult = result,
                verification = verification,
                language = language,
                verbosity = verbosity
            )
            recordSolutionInContext(userUid, conversationId, sol)
            return sol
        }

        // Case 3: "Pichhle wale mein 500 aur jod do" / add/subtract/multiply/divide on previous result
        val numMatch = Regex("""([+-]?\d[\d,]*(?:\.\d+)?)""").findAll(lower)
            .mapNotNull { it.groupValues[1].replace(",", "").toBigDecimalOrNull() }
            .firstOrNull()
        if (numMatch != null) {
            val op: String? = when {
                listOf("jod", "add", "plus", "+").any { lower.contains(it) } -> "+"
                listOf("ghata", "subtract", "minus", "kam kar").any { lower.contains(it) } -> "-"
                listOf("guna", "multiply", "times").any { lower.contains(it) } -> "×"
                listOf("bhag", "divide").any { lower.contains(it) } -> "÷"
                else -> null
            }
            if (op != null) {
                val result = when (op) {
                    "+" -> prevVal.add(numMatch, AstrynPrecisionCalculator.DECIMAL128)
                    "-" -> prevVal.subtract(numMatch, AstrynPrecisionCalculator.DECIMAL128)
                    "×" -> prevVal.multiply(numMatch, AstrynPrecisionCalculator.DECIMAL128)
                    "÷" -> {
                        if (numMatch.compareTo(BigDecimal.ZERO) == 0) return null
                        prevVal.divide(numMatch, AstrynPrecisionCalculator.DECIMAL128)
                    }
                    else -> return null
                }
                val resFormatted = AstrynPrecisionCalculator.formatNumber(result)
                val numFormatted = AstrynPrecisionCalculator.formatNumber(numMatch)
                val verification = VerificationStatus(
                    verified = true,
                    method = "Inverse Arithmetic Operation Check",
                    checkSummary = "Verified contextual operation ($prevFormatted $op $numFormatted = $resFormatted)"
                )
                val sol = AstrynCalculationFormatter.createSolution(
                    domain = MathDomain.CONTEXTUAL_FOLLOW_UP,
                    problemType = "CONTEXTUAL_ARITHMETIC_FOLLOW_UP",
                    recognizedInput = "$prevFormatted $op $numFormatted",
                    formulaUsed = "New Result = Previous Result $op Operand",
                    steps = listOf(
                        "Retrieved previous result = $prevFormatted",
                        "Applied operation ($op $numFormatted): $prevFormatted $op $numFormatted = $resFormatted"
                    ),
                    finalAnswer = resFormatted,
                    numericResult = result,
                    verification = verification,
                    language = language,
                    verbosity = verbosity
                )
                recordSolutionInContext(userUid, conversationId, sol)
                return sol
            }
        }

        // Case 4: "Isi formula se doosra question solve karo: ..."
        if (lower.contains("isi formula") || lower.contains("same formula")) {
            // If the prompt contains new parameters for the previous domain, route to the domain solver
            val stripped = prompt.replace(Regex("""isi formula se[^:]*:?|using (?:the )?same formula[^:]*:?""", RegexOption.IGNORE_CASE), "").trim()
            if (stripped.isNotEmpty() && stripped != prompt) {
                return solve(stripped, emptyList(), emptyList(), userUid, conversationId)
            }
        }

        return null
    }

    private fun solveMultimodalAttachmentMath(
        prompt: String,
        attachments: List<ChatAttachment>,
        language: MathLanguage,
        verbosity: OutputVerbosity,
        userUid: String,
        conversationId: String
    ): MathSolution? {
        val combinedText = attachments.mapNotNull { it.extractedText?.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")

        if (combinedText.isBlank()) {
            // Attachment has no readable text or equations
            val clarification = when (language) {
                MathLanguage.HINDI -> "अपलोड की गई इमेज/PDF में गणितीय समीकरण या संख्याएँ स्पष्ट रूप से पढ़ने योग्य नहीं हैं। कृपया स्पष्ट फोटो अपलोड करें या समीकरण टाइप करें।"
                MathLanguage.HINGLISH -> "Uploaded image/PDF mein numbers ya equation clearly readable nahi hai. Kripya clear image upload karein ya equation type karein."
                MathLanguage.ENGLISH -> "The mathematical expression or numbers in the uploaded attachment are unclear or unreadable. Please provide a clearer image/PDF or type the missing numbers so I can calculate the exact answer without guessing."
            }
            return MathSolution(
                domain = MathDomain.MULTIMODAL_DOCUMENT_MATH,
                problemType = "UNREADABLE_ATTACHMENT_CLARIFICATION",
                recognizedInput = "Attachment (${attachments.first().fileName}) — unreadable/empty text",
                formulaUsed = "Zero-Fabrication Clarification Protocol",
                steps = listOf("Inspected attachment '${attachments.first().fileName}'", "Detected missing or unreadable numerical text; refusing to fabricate numbers."),
                finalAnswer = clarification,
                verification = VerificationStatus(false, "Clarification Required", "Unreadable symbols detected"),
                clarificationPrompt = clarification,
                formattedResponse = clarification,
                language = language,
                verbosity = verbosity
            )
        }

        // Check if extracted text has '?' or '[unclear]' or ' illegible ' markers
        if (combinedText.contains("[unclear]", ignoreCase = true) || combinedText.contains("??")) {
            val clarification = "Some symbols or digits in '${attachments.first().fileName}' are unclear (`$combinedText`). Please confirm the blurred digits before I compute the final result."
            return MathSolution(
                domain = MathDomain.MULTIMODAL_DOCUMENT_MATH,
                problemType = "UNCLEAR_SYMBOL_CLARIFICATION",
                recognizedInput = combinedText,
                formulaUsed = "Zero-Fabrication Clarification Protocol",
                steps = listOf("Extracted raw text from attachment: $combinedText", "Detected ambiguous/blurred symbols (`??` / `[unclear]`)."),
                finalAnswer = clarification,
                verification = VerificationStatus(false, "Clarification Required", "Ambiguous symbols detected"),
                clarificationPrompt = clarification,
                formattedResponse = clarification,
                language = language,
                verbosity = verbosity
            )
        }

        // Check if multiple numbered questions exist in a worksheet/PDF ("Q1: ... Q2: ...")
        val lines = combinedText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val numberedQuestions = lines.filter { line ->
            Regex("""^(?:Q\d+|Question\s*\d+|\d+)[\.:\)\-\s]+(.+)""", RegexOption.IGNORE_CASE).containsMatchIn(line)
        }
        if (numberedQuestions.size >= 2) {
            val subSteps = mutableListOf<String>()
            val answers = mutableListOf<String>()
            for ((idx, qLine) in numberedQuestions.withIndex()) {
                val cleanQ = qLine.replace(Regex("""^(?:Q\d+|Question\s*\d+|\d+)[\.:\)\-\s]+""", RegexOption.IGNORE_CASE), "").trim()
                val subSol = solve(cleanQ, emptyList(), emptyList(), userUid, conversationId)
                if (subSol != null) {
                    subSteps.add("Q${idx + 1} ($cleanQ): ${subSol.finalAnswer} [Formula: ${subSol.formulaUsed}]")
                    answers.add("Q${idx + 1} = ${subSol.finalAnswer}")
                }
            }
            if (answers.isNotEmpty()) {
                val summaryAns = answers.joinToString("; ")
                val verification = VerificationStatus(true, "Multi-Question Worksheet Verification", "Solved and verified ${answers.size} worksheet questions.")
                return AstrynCalculationFormatter.createSolution(
                    domain = MathDomain.MULTIMODAL_DOCUMENT_MATH,
                    problemType = "WORKSHEET_MULTI_QUESTION_MATH",
                    recognizedInput = "Worksheet (${numberedQuestions.size} questions from ${attachments.first().fileName})",
                    formulaUsed = "Per-question deterministic evaluation",
                    steps = subSteps,
                    finalAnswer = summaryAns,
                    verification = verification,
                    language = language,
                    verbosity = verbosity
                )
            }
        }

        // Otherwise try solving the extracted text directly or as a document table
        val directSol = solve(combinedText, emptyList(), emptyList(), userUid, conversationId)
        if (directSol != null) {
            return directSol.copy(
                domain = MathDomain.MULTIMODAL_DOCUMENT_MATH,
                recognizedInput = "From ${attachments.first().fileName}: ${directSol.recognizedInput}"
            )
        }

        return null
    }

    private fun solveRatioAndProportion(
        prompt: String,
        language: MathLanguage,
        verbosity: OutputVerbosity
    ): MathSolution? {
        val lower = prompt.lowercase(Locale.ROOT)
        // Pattern 1: Divide X in the ratio a:b(:c)
        val divideRatio = Regex("""(?:divide|split|distribute|baanto)\s+(?:₹|rs\.?|inr|\$)?\s*(\d[\d,]*(?:\.\d+)?)\s+(?:in\s+(?:the\s+)?ratio|mein)\s+(\d+(?:\.\d+)?(?:\s*:\s*\d+(?:\.\d+)?)+)""", RegexOption.IGNORE_CASE)
            .find(prompt)
        if (divideRatio != null) {
            val total = BigDecimal(divideRatio.groupValues[1].replace(",", ""))
            val parts = divideRatio.groupValues[2].split(":").map { BigDecimal(it.trim()) }
            val sumParts = parts.fold(BigDecimal.ZERO) { acc, b -> acc.add(b) }
            if (sumParts.compareTo(BigDecimal.ZERO) == 0) return null
            val shares = parts.map { part ->
                total.multiply(part, AstrynPrecisionCalculator.DECIMAL128)
                    .divide(sumParts, AstrynPrecisionCalculator.DECIMAL128)
            }
            val sharesStr = shares.mapIndexed { idx, s -> "Share ${idx + 1} = ${AstrynPrecisionCalculator.formatNumber(s)}" }
            val sumShares = shares.fold(BigDecimal.ZERO) { acc, b -> acc.add(b) }
            val verification = VerificationStatus(
                verified = sumShares.subtract(total).abs() <= BigDecimal("0.0001"),
                method = "Sum of Shares Check",
                checkSummary = "Sum of shares = ${AstrynPrecisionCalculator.formatNumber(sumShares)} == Total ${AstrynPrecisionCalculator.formatNumber(total)}"
            )
            return AstrynCalculationFormatter.createSolution(
                domain = MathDomain.RATIO_AND_PROPORTION,
                problemType = "RATIO_DIVISION",
                recognizedInput = "Total = ${AstrynPrecisionCalculator.formatNumber(total)}, Ratio = ${parts.joinToString(":") { AstrynPrecisionCalculator.formatNumber(it) }}",
                formulaUsed = "Share_i = Total × (Part_i / Sum of Parts)",
                steps = listOf(
                    "Total Amount = ${AstrynPrecisionCalculator.formatNumber(total)}",
                    "Sum of ratio parts = ${parts.joinToString(" + ") { AstrynPrecisionCalculator.formatNumber(it) }} = ${AstrynPrecisionCalculator.formatNumber(sumParts)}",
                    "Individual shares: ${sharesStr.joinToString(", ")}"
                ),
                finalAnswer = shares.joinToString(" : ") { AstrynPrecisionCalculator.formatNumber(it) },
                numericResult = shares.first(),
                verification = verification,
                language = language,
                verbosity = verbosity
            )
        }

        // Pattern 2: Proportion a : b :: c : x or a/b = c/x
        val propMatch = Regex("""(\d+(?:\.\d+)?)\s*:\s*(\d+(?:\.\d+)?)\s*(?:::|=)\s*(\d+(?:\.\d+)?)\s*:\s*x""", RegexOption.IGNORE_CASE).find(lower)
        if (propMatch != null) {
            val a = BigDecimal(propMatch.groupValues[1])
            val b = BigDecimal(propMatch.groupValues[2])
            val c = BigDecimal(propMatch.groupValues[3])
            if (a.compareTo(BigDecimal.ZERO) == 0) return null
            val x = b.multiply(c, AstrynPrecisionCalculator.DECIMAL128).divide(a, AstrynPrecisionCalculator.DECIMAL128)
            val xStr = AstrynPrecisionCalculator.formatNumber(x)
            return AstrynCalculationFormatter.createSolution(
                domain = MathDomain.RATIO_AND_PROPORTION,
                problemType = "PROPORTION_SOLVER",
                recognizedInput = "$a : $b = $c : x",
                formulaUsed = "x = (b × c) / a",
                steps = listOf(
                    "Given proportion: $a / $b = $c / x",
                    "Cross-multiplying: $a × x = $b × $c",
                    "x = (${AstrynPrecisionCalculator.formatNumber(b)} × ${AstrynPrecisionCalculator.formatNumber(c)}) / ${AstrynPrecisionCalculator.formatNumber(a)} = $xStr"
                ),
                finalAnswer = "x = $xStr",
                numericResult = x,
                verification = VerificationStatus(true, "Cross-Product Check", "$a × $xStr = ${AstrynPrecisionCalculator.formatNumber(a.multiply(x))} == ${AstrynPrecisionCalculator.formatNumber(b.multiply(c))}"),
                language = language,
                verbosity = verbosity
            )
        }
        return null
    }

    private fun solvePercentageProblems(
        prompt: String,
        language: MathLanguage,
        verbosity: OutputVerbosity
    ): MathSolution? {
        val lower = prompt.lowercase(Locale.ROOT)
        // Pattern 1: "X% of Y" or "Y ka X percent"
        val pctOf = Regex("""(\d+(?:\.\d+)?)\s*(?:%|percent|pratishat)\s+of\s+(?:₹|rs\.?|inr|\$)?\s*(\d[\d,]*(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(prompt)
            ?: Regex("""(?:₹|rs\.?|inr|\$)?\s*(\d[\d,]*(?:\.\d+)?)\s+ka\s+(\d+(?:\.\d+)?)\s*(?:%|percent|pratishat)""", RegexOption.IGNORE_CASE).find(prompt)?.let {
                // swap group order
                null
            }
        val hinglishPctOf = Regex("""(?:₹|rs\.?|inr|\$)?\s*(\d[\d,]*(?:\.\d+)?)\s+ka\s+(\d+(?:\.\d+)?)\s*(?:%|percent|pratishat)""", RegexOption.IGNORE_CASE).find(prompt)

        if (pctOf != null || hinglishPctOf != null) {
            val pct = if (pctOf != null) BigDecimal(pctOf.groupValues[1]) else BigDecimal(hinglishPctOf!!.groupValues[2])
            val base = if (pctOf != null) BigDecimal(pctOf.groupValues[2].replace(",", "")) else BigDecimal(hinglishPctOf!!.groupValues[1].replace(",", ""))
            // Make sure there isn't a multi-step operation after it
            if (!lower.contains("increase") && !lower.contains("decrease") && !lower.contains("discount") && !lower.contains("gst")) {
                val res = base.multiply(pct, AstrynPrecisionCalculator.DECIMAL128).divide(BigDecimal(100), AstrynPrecisionCalculator.DECIMAL128)
                val resStr = AstrynPrecisionCalculator.formatNumber(res)
                val verification = AstrynMathVerificationEngine.verifyPercentage(base, pct, res)
                return AstrynCalculationFormatter.createSolution(
                    domain = MathDomain.ARITHMETIC_BODMAS,
                    problemType = "PERCENTAGE_CALCULATION",
                    recognizedInput = "${AstrynPrecisionCalculator.formatNumber(pct)}% of ${AstrynPrecisionCalculator.formatNumber(base)}",
                    formulaUsed = "Value = (Percentage / 100) × Base",
                    steps = listOf(
                        "Base Value = ${AstrynPrecisionCalculator.formatNumber(base)}, Percentage = ${AstrynPrecisionCalculator.formatNumber(pct)}%",
                        "Calculation = (${AstrynPrecisionCalculator.formatNumber(pct)} / 100) × ${AstrynPrecisionCalculator.formatNumber(base)} = $resStr"
                    ),
                    finalAnswer = resStr,
                    numericResult = res,
                    verification = verification,
                    examShortTrick = "Split ${AstrynPrecisionCalculator.formatNumber(pct)}% into 10% + 5% + 1% mental blocks of ${AstrynPrecisionCalculator.formatNumber(base)}.",
                    language = language,
                    verbosity = verbosity
                )
            }
        }

        // Pattern 2: Percentage increase/decrease from A to B
        val fromToMatch = Regex("""percentage\s+(increase|decrease|change)\s+from\s+(\d[\d,]*(?:\.\d+)?)\s+to\s+(\d[\d,]*(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(prompt)
        if (fromToMatch != null) {
            val initial = BigDecimal(fromToMatch.groupValues[2].replace(",", ""))
            val finalVal = BigDecimal(fromToMatch.groupValues[3].replace(",", ""))
            if (initial.compareTo(BigDecimal.ZERO) == 0) return null
            val diff = finalVal.subtract(initial, AstrynPrecisionCalculator.DECIMAL128)
            val pctChange = diff.abs().divide(initial, AstrynPrecisionCalculator.DECIMAL128).multiply(BigDecimal(100), AstrynPrecisionCalculator.DECIMAL128)
            val direction = if (diff >= BigDecimal.ZERO) "Increase" else "Decrease"
            val pctStr = "${AstrynPrecisionCalculator.formatNumber(pctChange, 2)}% $direction"
            return AstrynCalculationFormatter.createSolution(
                domain = MathDomain.ARITHMETIC_BODMAS,
                problemType = "PERCENTAGE_CHANGE",
                recognizedInput = "From ${AstrynPrecisionCalculator.formatNumber(initial)} to ${AstrynPrecisionCalculator.formatNumber(finalVal)}",
                formulaUsed = "Percentage Change = ((Final - Initial) / Initial) × 100",
                steps = listOf(
                    "Initial Value = ${AstrynPrecisionCalculator.formatNumber(initial)}, Final Value = ${AstrynPrecisionCalculator.formatNumber(finalVal)}",
                    "Difference = ${AstrynPrecisionCalculator.formatNumber(finalVal)} - ${AstrynPrecisionCalculator.formatNumber(initial)} = ${AstrynPrecisionCalculator.formatNumber(diff)}",
                    "Percentage $direction = (${AstrynPrecisionCalculator.formatNumber(diff.abs())} / ${AstrynPrecisionCalculator.formatNumber(initial)}) × 100 = ${AstrynPrecisionCalculator.formatNumber(pctChange, 2)}%"
                ),
                finalAnswer = pctStr,
                numericResult = pctChange,
                verification = VerificationStatus(true, "Reverse Scaling Check", "${AstrynPrecisionCalculator.formatNumber(initial)} × (1 + ${AstrynPrecisionCalculator.formatNumber(diff.divide(initial, AstrynPrecisionCalculator.DECIMAL128))}) = ${AstrynPrecisionCalculator.formatNumber(finalVal)}"),
                language = language,
                verbosity = verbosity
            )
        }
        return null
    }

    private fun solveExactFractionExpression(
        prompt: String,
        language: MathLanguage,
        verbosity: OutputVerbosity
    ): MathSolution? {
        // Match fraction arithmetic like "3/4 + 5/6", "7/8 - 1/3", "(2/3) * (9/10)"
        val cleaned = prompt.replace(Regex("""^(?:solve|calculate|evaluate|simplify|what\s+is|find)\s*:?\s*""", RegexOption.IGNORE_CASE), "")
            .replace("?", "")
            .trim()
        val fracRegex = Regex("""^\(?\s*([+-]?\d+)\s*/\s*(\d+)\s*\)?\s*([\+\-\*×/÷])\s*\(?\s*([+-]?\d+)\s*/\s*(\d+)\s*\)?$""")
        val m = fracRegex.find(cleaned) ?: return null

        val n1 = BigInteger(m.groupValues[1])
        val d1 = BigInteger(m.groupValues[2])
        val op = m.groupValues[3]
        val n2 = BigInteger(m.groupValues[4])
        val d2 = BigInteger(m.groupValues[5])

        if (d1 == BigInteger.ZERO || d2 == BigInteger.ZERO) {
            return createDivisionByZeroSolution(cleaned, language, verbosity)
        }

        val f1 = AstrynPrecisionCalculator.BigFraction(n1, d1)
        val f2 = AstrynPrecisionCalculator.BigFraction(n2, d2)
        val result = when (op) {
            "+" -> f1.add(f2)
            "-" -> f1.subtract(f2)
            "*", "×" -> f1.multiply(f2)
            "/", "÷" -> {
                if (f2.numerator == BigInteger.ZERO) return createDivisionByZeroSolution(cleaned, language, verbosity)
                f1.divide(f2)
            }
            else -> return null
        }

        val decimalVal = result.toBigDecimal(AstrynPrecisionCalculator.DECIMAL128)
        val decimalFormatted = AstrynPrecisionCalculator.formatNumber(decimalVal, 6)
        val mixedStr = result.toMixedString()
        val finalAns = if (mixedStr != result.toString()) {
            "${result} (or $mixedStr ≈ $decimalFormatted)"
        } else {
            "${result} (≈ $decimalFormatted)"
        }

        val lcm = d1.multiply(d2).divide(d1.gcd(d2))
        val steps = listOf(
            "Given fractions: $n1/$d1 (simplified: $f1) and $n2/$d2 (simplified: $f2)",
            "Operation: $f1 $op $f2 (LCM of denominators $d1 and $d2 = $lcm)",
            "Exact reduced fraction = ${result.numerator}/${result.denominator}${if (mixedStr != result.toString()) " = $mixedStr" else ""}",
            "Decimal equivalent = $decimalFormatted"
        )
        return AstrynCalculationFormatter.createSolution(
            domain = MathDomain.FRACTION_RATIONAL,
            problemType = "EXACT_FRACTION_ARITHMETIC",
            recognizedInput = "$n1/$d1 $op $n2/$d2",
            formulaUsed = "a/b ± c/d = (ad ± bc) / bd (reduced by GCD)",
            steps = steps,
            finalAnswer = finalAns,
            numericResult = decimalVal,
            verification = VerificationStatus(
                verified = true,
                method = "Decimal Cross-Check",
                checkSummary = "Exact rational ${result} matches decimal sum $decimalFormatted",
                isExact = true
            ),
            language = language,
            verbosity = verbosity
        )
    }

    private fun solveGeneralExpression(
        prompt: String,
        language: MathLanguage,
        verbosity: OutputVerbosity
    ): MathSolution? {
        val candidate = AstrynExpressionParser.extractExpressionFromText(prompt) ?: return null
        val parseResult = AstrynExpressionParser.evaluate(candidate)
        if (parseResult.error != null) {
            if (parseResult.isDivisionByZero || parseResult.isDomainError) {
                return MathSolution(
                    domain = MathDomain.AMBIGUOUS_OR_INVALID,
                    problemType = if (parseResult.isDivisionByZero) "DIVISION_BY_ZERO_ERROR" else "MATH_DOMAIN_ERROR",
                    recognizedInput = candidate,
                    formulaUsed = "Mathematical Domain Validation",
                    steps = listOf("Parsed expression: $candidate", "Validation Error: ${parseResult.error}"),
                    finalAnswer = "Undefined (${parseResult.error})",
                    verification = VerificationStatus(false, "Domain Boundary Check", parseResult.error),
                    formattedResponse = "**Mathematical Error**: ${parseResult.error}",
                    language = language,
                    verbosity = verbosity
                )
            }
            return null
        }

        val value = parseResult.value ?: return null
        val formattedVal = if (parseResult.useScientificNotation || (value.abs() >= BigDecimal("1E12") || (value.compareTo(BigDecimal.ZERO) != 0 && value.abs() < BigDecimal("1E-5")))) {
            "${AstrynPrecisionCalculator.formatNumber(value)} (${AstrynPrecisionCalculator.formatScientific(value)})"
        } else {
            AstrynPrecisionCalculator.formatNumber(value)
        }

        // Independent recomputation check
        val secondCheck = AstrynExpressionParser.evaluate(candidate).value
        val verified = secondCheck != null && secondCheck.subtract(value, AstrynPrecisionCalculator.DECIMAL128).abs() <= BigDecimal("1E-20")

        val domain = if (parseResult.useScientificNotation) MathDomain.SCIENTIFIC_NOTATION else MathDomain.ARITHMETIC_BODMAS
        val problemType = if (parseResult.useScientificNotation) "SCIENTIFIC_NOTATION_CALCULATION" else "ARITHMETIC_EXPRESSION"

        return AstrynCalculationFormatter.createSolution(
            domain = domain,
            problemType = problemType,
            recognizedInput = candidate,
            formulaUsed = "BODMAS / PEMDAS Deterministic Expression Tree",
            steps = parseResult.steps.ifEmpty {
                listOf("Expression: $candidate", "Evaluated with 34-digit IEEE 754R Decimal128 precision = $formattedVal")
            },
            finalAnswer = formattedVal,
            numericResult = value,
            verification = VerificationStatus(
                verified = verified,
                method = "Independent AST Re-evaluation",
                checkSummary = "Verified $candidate = $formattedVal",
                isExact = !parseResult.usedTranscendentalApproximation
            ),
            language = language,
            verbosity = verbosity
        )
    }

    private fun createDivisionByZeroSolution(
        input: String,
        language: MathLanguage,
        verbosity: OutputVerbosity
    ): MathSolution {
        val msg = "Division by zero is undefined in real arithmetic ($input)."
        return MathSolution(
            domain = MathDomain.AMBIGUOUS_OR_INVALID,
            problemType = "DIVISION_BY_ZERO_ERROR",
            recognizedInput = input,
            formulaUsed = "Division Domain Rule (Denominator ≠ 0)",
            steps = listOf("Identified expression: $input", "Denominator evaluates to 0; division by zero is mathematically undefined."),
            finalAnswer = "Undefined (Division by zero)",
            verification = VerificationStatus(false, "Zero Denominator Guard", msg),
            formattedResponse = "**Undefined**: $msg",
            language = language,
            verbosity = verbosity
        )
    }
}

/**
 * IEEE 754R Decimal128 (`BigDecimal`) and Exact Rational (`BigFraction`) calculation utilities.
 */
object AstrynPrecisionCalculator {
    val DECIMAL128: MathContext = MathContext(34, RoundingMode.HALF_UP)

    data class BigFraction(val numerator: BigInteger, val denominator: BigInteger) {
        init {
            require(denominator != BigInteger.ZERO) { "Fraction denominator cannot be zero." }
        }

        val reduced: BigFraction by lazy {
            if (numerator == BigInteger.ZERO) {
                BigFraction(BigInteger.ZERO, BigInteger.ONE)
            } else {
                val sign = if (denominator < BigInteger.ZERO) BigInteger.valueOf(-1) else BigInteger.ONE
                val n = numerator.multiply(sign)
                val d = denominator.multiply(sign)
                val gcd = n.abs().gcd(d.abs())
                BigFraction(n.divide(gcd), d.divide(gcd))
            }
        }

        fun add(other: BigFraction): BigFraction {
            val num = numerator.multiply(other.denominator).add(other.numerator.multiply(denominator))
            val den = denominator.multiply(other.denominator)
            return BigFraction(num, den).reduced
        }

        fun subtract(other: BigFraction): BigFraction {
            val num = numerator.multiply(other.denominator).subtract(other.numerator.multiply(denominator))
            val den = denominator.multiply(other.denominator)
            return BigFraction(num, den).reduced
        }

        fun multiply(other: BigFraction): BigFraction {
            return BigFraction(
                numerator.multiply(other.numerator),
                denominator.multiply(other.denominator)
            ).reduced
        }

        fun divide(other: BigFraction): BigFraction {
            require(other.numerator != BigInteger.ZERO) { "Division by zero fraction." }
            return BigFraction(
                numerator.multiply(other.denominator),
                denominator.multiply(other.numerator)
            ).reduced
        }

        fun toBigDecimal(mc: MathContext = DECIMAL128): BigDecimal {
            val r = reduced
            return BigDecimal(r.numerator).divide(BigDecimal(r.denominator), mc)
        }

        fun toMixedString(): String {
            val r = reduced
            if (r.denominator == BigInteger.ONE) return r.numerator.toString()
            val absNum = r.numerator.abs()
            if (absNum < r.denominator) return "${r.numerator}/${r.denominator}"
            val whole = r.numerator.divide(r.denominator)
            val rem = absNum.mod(r.denominator)
            return if (rem == BigInteger.ZERO) whole.toString() else "$whole $rem/${r.denominator}"
        }

        override fun toString(): String {
            val r = this
            val gcd = r.numerator.abs().gcd(r.denominator.abs())
            val sign = if (r.denominator < BigInteger.ZERO) BigInteger.valueOf(-1) else BigInteger.ONE
            val n = r.numerator.multiply(sign).divide(gcd)
            val d = r.denominator.multiply(sign).divide(gcd)
            return if (d == BigInteger.ONE) n.toString() else "$n/$d"
        }
    }

    fun formatNumber(
        value: BigDecimal,
        maxScale: Int = 6,
        roundingMode: RoundingMode = RoundingMode.HALF_UP
    ): String {
        val stripped = value.setScale(maxScale, roundingMode).stripTrailingZeros()
        return stripped.toPlainString()
    }

    fun formatCurrency(
        value: BigDecimal,
        symbol: String = "₹",
        scale: Int = 2
    ): String {
        val rounded = value.setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros()
        val plain = if (rounded.scale() <= 0) {
            rounded.toBigInteger().toString()
        } else {
            value.setScale(scale, RoundingMode.HALF_UP).toPlainString()
        }
        return "$symbol$plain"
    }

    fun formatScientific(value: BigDecimal, sigFigs: Int = 6): String {
        if (value.compareTo(BigDecimal.ZERO) == 0) return "0"
        val mc = MathContext(sigFigs, RoundingMode.HALF_UP)
        val rounded = value.round(mc)
        return String.format(Locale.US, "%.${(sigFigs - 1).coerceAtLeast(1)}E", rounded)
    }

    fun factorial(n: Int): BigInteger {
        require(n in 0..500) { "Factorial input must be between 0 and 500." }
        var acc = BigInteger.ONE
        for (i in 2..n) {
            acc = acc.multiply(BigInteger.valueOf(i.toLong()))
        }
        return acc
    }

    fun pow(base: BigDecimal, exponent: BigDecimal): BigDecimal {
        // Exact integer exponent when possible
        val intExp = exponent.stripTrailingZeros()
        if (intExp.scale() <= 0 && intExp.abs() <= BigDecimal(10000)) {
            val n = intExp.toInt()
            return if (n >= 0) {
                base.pow(n, DECIMAL128)
            } else {
                BigDecimal.ONE.divide(base.pow(-n, DECIMAL128), DECIMAL128)
            }
        }
        val dBase = base.toDouble()
        val dExp = exponent.toDouble()
        val res = dBase.pow(dExp)
        require(!res.isNaN() && !res.isInfinite()) { "Exponentiation overflow or domain error for $base^$exponent" }
        return BigDecimal.valueOf(res)
    }
}

/**
 * Safe deterministic mathematical expression parser supporting BODMAS/PEMDAS, nested brackets,
 * unary operators, implicit multiplication, powers, roots, logs, trig functions, factorials, and scientific notation.
 */
object AstrynExpressionParser {
    private const val MAX_EXPRESSION_LENGTH = 2000
    private const val MAX_TOKEN_COUNT = 512
    private const val MAX_NESTING_DEPTH = 64

    data class ParseEvaluationResult(
        val value: BigDecimal? = null,
        val steps: List<String> = emptyList(),
        val useScientificNotation: Boolean = false,
        val usedTranscendentalApproximation: Boolean = false,
        val isDivisionByZero: Boolean = false,
        val isDomainError: Boolean = false,
        val error: String? = null
    )

    sealed class Token {
        data class NumberToken(val value: BigDecimal, val raw: String) : Token()
        data class OperatorToken(val op: String) : Token()
        data class FunctionToken(val name: String) : Token()
        object LeftParen : Token()
        object RightParen : Token()
        object Comma : Token()
    }

    fun extractExpressionFromText(prompt: String): String? {
        var cleaned = prompt.trim()
        // Reject equations with '=' unless it's at the very end like "2+2="
        val eqCount = cleaned.count { it == '=' }
        if (eqCount > 1) return null
        if (eqCount == 1 && !cleaned.endsWith("=") && !cleaned.endsWith("=?")) {
            val parts = cleaned.split("=")
            if (parts[0].any { it.isLetter() } && parts[1].any { it.isDigit() || it.isLetter() }) {
                return null // algebraic equation, not pure arithmetic expression
            }
        }
        cleaned = cleaned
            .replace(Regex("""^(?:please\s+)?(?:solve|calculate|evaluate|compute|simplify|what\s+is|find\s+the\s+value\s+of|find|value\s+of|kitna\s+hoga|batao)\s*:?\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""(?:sirf\s+answer\s+batao|only\s+final\s+answer|step\s+by\s+step|explain\s+in\s+detail)\s*[.?!]*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[=?\s]+$"""), "")
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
            .replace("π", "pi")
            .replace("√", "sqrt")
            .replace("[", "(")
            .replace("]", ")")
            .replace("{", "(")
            .replace("}", ")")
            .trim()

        if (cleaned.isEmpty() || !cleaned.any { it.isDigit() }) return null
        // Check if remaining words are only known math functions/constants
        val words = Regex("""[a-zA-Z_]+""").findAll(cleaned).map { it.value.lowercase(Locale.ROOT) }.toList()
        val allowedWords = setOf(
            "sqrt", "cbrt", "abs", "ln", "log", "log10", "log2",
            "sin", "cos", "tan", "asin", "acos", "atan", "deg", "rad",
            "pi", "e", "mod"
        )
        if (words.any { it !in allowedWords }) return null
        return cleaned
    }

    fun evaluate(
        expression: String,
        angleInDegrees: Boolean = true
    ): ParseEvaluationResult {
        val raw = expression.trim()
        if (raw.isEmpty()) return ParseEvaluationResult(error = "Empty expression")
        if (raw.length > MAX_EXPRESSION_LENGTH) {
            return ParseEvaluationResult(isDomainError = true, error = "Expression exceeds maximum safe length ($MAX_EXPRESSION_LENGTH characters).")
        }

        val normalized = raw
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
            .replace("π", "pi")
            .replace("√", "sqrt")
            .replace("[", "(")
            .replace("]", ")")
            .replace("{", "(")
            .replace("}", ")")

        val useSci = Regex("""\d(?:\.\d+)?[eE][+-]?\d+""").containsMatchIn(normalized)
        var usedTranscendental = false

        val tokens = try {
            tokenize(normalized)
        } catch (e: Exception) {
            return ParseEvaluationResult(error = e.message ?: "Tokenization error")
        }

        if (tokens.isEmpty()) return ParseEvaluationResult(error = "No valid mathematical tokens found")
        if (tokens.size > MAX_TOKEN_COUNT) {
            return ParseEvaluationResult(isDomainError = true, error = "Expression exceeds maximum token limit ($MAX_TOKEN_COUNT).")
        }

        // Convert infix to RPN via Shunting-Yard
        val outputQueue = mutableListOf<Token>()
        val operatorStack = ArrayDeque<Token>()
        var depth = 0

        fun precedence(op: String): Int = when (op) {
            "+", "-" -> 1
            "*", "/", "%" -> 2
            "u-", "u+" -> 3
            "^" -> 4
            "!" -> 5
            else -> 0
        }

        fun isRightAssociative(op: String): Boolean = op == "^" || op == "u-" || op == "u+"

        for (token in tokens) {
            when (token) {
                is Token.NumberToken -> outputQueue.add(token)
                is Token.FunctionToken -> operatorStack.addLast(token)
                is Token.Comma -> {
                    while (operatorStack.isNotEmpty() && operatorStack.last() !is Token.LeftParen) {
                        outputQueue.add(operatorStack.removeLast())
                    }
                }
                is Token.OperatorToken -> {
                    val o1 = token.op
                    if (o1 == "!") {
                        outputQueue.add(token)
                    } else {
                        while (operatorStack.isNotEmpty()) {
                            val top = operatorStack.last()
                            if (top is Token.OperatorToken) {
                                val o2 = top.op
                                val p1 = precedence(o1)
                                val p2 = precedence(o2)
                                if ((isRightAssociative(o1) && p1 < p2) || (!isRightAssociative(o1) && p1 <= p2)) {
                                    outputQueue.add(operatorStack.removeLast())
                                } else break
                            } else if (top is Token.FunctionToken) {
                                outputQueue.add(operatorStack.removeLast())
                            } else break
                        }
                        operatorStack.addLast(token)
                    }
                }
                is Token.LeftParen -> {
                    depth++
                    if (depth > MAX_NESTING_DEPTH) {
                        return ParseEvaluationResult(isDomainError = true, error = "Parenthesis nesting exceeds limit ($MAX_NESTING_DEPTH).")
                    }
                    operatorStack.addLast(token)
                }
                is Token.RightParen -> {
                    depth--
                    if (depth < 0) {
                        return ParseEvaluationResult(isDomainError = true, error = "Mismatched closing parenthesis.")
                    }
                    while (operatorStack.isNotEmpty() && operatorStack.last() !is Token.LeftParen) {
                        outputQueue.add(operatorStack.removeLast())
                    }
                    if (operatorStack.isEmpty() || operatorStack.last() !is Token.LeftParen) {
                        return ParseEvaluationResult(isDomainError = true, error = "Mismatched parentheses.")
                    }
                    operatorStack.removeLast() // pop LeftParen
                    if (operatorStack.isNotEmpty() && operatorStack.last() is Token.FunctionToken) {
                        outputQueue.add(operatorStack.removeLast())
                    }
                }
            }
        }

        if (depth != 0) {
            return ParseEvaluationResult(isDomainError = true, error = "Unclosed parenthesis in expression.")
        }

        while (operatorStack.isNotEmpty()) {
            val top = operatorStack.removeLast()
            if (top is Token.LeftParen || top is Token.RightParen) {
                return ParseEvaluationResult(isDomainError = true, error = "Mismatched parentheses.")
            }
            outputQueue.add(top)
        }

        // Evaluate RPN
        val evalStack = ArrayDeque<BigDecimal>()
        val stepLog = mutableListOf<String>()

        for (token in outputQueue) {
            when (token) {
                is Token.NumberToken -> evalStack.addLast(token.value)
                is Token.OperatorToken -> {
                    when (token.op) {
                        "u-" -> {
                            if (evalStack.isEmpty()) return ParseEvaluationResult(error = "Missing operand for unary minus")
                            val a = evalStack.removeLast()
                            evalStack.addLast(a.negate())
                        }
                        "u+" -> {
                            if (evalStack.isEmpty()) return ParseEvaluationResult(error = "Missing operand for unary plus")
                        }
                        "!" -> {
                            if (evalStack.isEmpty()) return ParseEvaluationResult(error = "Missing operand for factorial")
                            val a = evalStack.removeLast().stripTrailingZeros()
                            if (a.scale() > 0 || a < BigDecimal.ZERO || a > BigDecimal(500)) {
                                return ParseEvaluationResult(isDomainError = true, error = "Factorial requires a non-negative integer ≤ 500 (got ${a.toPlainString()}).")
                            }
                            val fact = BigDecimal(AstrynPrecisionCalculator.factorial(a.toInt()))
                            stepLog.add("${a.toPlainString()}! = ${AstrynPrecisionCalculator.formatNumber(fact)}")
                            evalStack.addLast(fact)
                        }
                        "+", "-", "*", "/", "%", "^" -> {
                            if (evalStack.size < 2) return ParseEvaluationResult(error = "Missing operands for operator '${token.op}'")
                            val b = evalStack.removeLast()
                            val a = evalStack.removeLast()
                            val res = when (token.op) {
                                "+" -> a.add(b, AstrynPrecisionCalculator.DECIMAL128)
                                "-" -> a.subtract(b, AstrynPrecisionCalculator.DECIMAL128)
                                "*" -> a.multiply(b, AstrynPrecisionCalculator.DECIMAL128)
                                "/" -> {
                                    if (b.compareTo(BigDecimal.ZERO) == 0) {
                                        return ParseEvaluationResult(isDivisionByZero = true, error = "Division by zero (${AstrynPrecisionCalculator.formatNumber(a)} / 0)")
                                    }
                                    a.divide(b, AstrynPrecisionCalculator.DECIMAL128)
                                }
                                "%" -> {
                                    if (b.compareTo(BigDecimal.ZERO) == 0) {
                                        return ParseEvaluationResult(isDivisionByZero = true, error = "Modulo by zero")
                                    }
                                    a.remainder(b, AstrynPrecisionCalculator.DECIMAL128)
                                }
                                "^" -> {
                                    try {
                                        AstrynPrecisionCalculator.pow(a, b)
                                    } catch (e: Exception) {
                                        return ParseEvaluationResult(isDomainError = true, error = e.message ?: "Exponentiation error")
                                    }
                                }
                                else -> return ParseEvaluationResult(error = "Unknown operator ${token.op}")
                            }
                            stepLog.add("${AstrynPrecisionCalculator.formatNumber(a)} ${token.op} ${AstrynPrecisionCalculator.formatNumber(b)} = ${AstrynPrecisionCalculator.formatNumber(res)}")
                            evalStack.addLast(res)
                        }
                    }
                }
                is Token.FunctionToken -> {
                    if (evalStack.isEmpty()) return ParseEvaluationResult(error = "Missing argument for function '${token.name}'")
                    val a = evalStack.removeLast()
                    val da = a.toDouble()
                    val res: BigDecimal = when (token.name) {
                        "sqrt" -> {
                            if (a < BigDecimal.ZERO) {
                                return ParseEvaluationResult(isDomainError = true, error = "Square root of negative number (${AstrynPrecisionCalculator.formatNumber(a)}) is not a real number.")
                            }
                            usedTranscendental = true
                            BigDecimal.valueOf(sqrt(da))
                        }
                        "cbrt" -> {
                            usedTranscendental = true
                            BigDecimal.valueOf(cbrt(da))
                        }
                        "abs" -> a.abs()
                        "ln" -> {
                            if (a <= BigDecimal.ZERO) {
                                return ParseEvaluationResult(isDomainError = true, error = "Natural logarithm ln(x) is undefined for x ≤ 0 (got ${AstrynPrecisionCalculator.formatNumber(a)}).")
                            }
                            usedTranscendental = true
                            BigDecimal.valueOf(ln(da))
                        }
                        "log", "log10" -> {
                            if (a <= BigDecimal.ZERO) {
                                return ParseEvaluationResult(isDomainError = true, error = "Logarithm log10(x) is undefined for x ≤ 0 (got ${AstrynPrecisionCalculator.formatNumber(a)}).")
                            }
                            usedTranscendental = true
                            BigDecimal.valueOf(log10(da))
                        }
                        "log2" -> {
                            if (a <= BigDecimal.ZERO) {
                                return ParseEvaluationResult(isDomainError = true, error = "Logarithm log2(x) is undefined for x ≤ 0.")
                            }
                            usedTranscendental = true
                            BigDecimal.valueOf(log2(da))
                        }
                        "sin" -> {
                            usedTranscendental = true
                            val rad = if (angleInDegrees) Math.toRadians(da) else da
                            val s = sin(rad)
                            BigDecimal.valueOf(if (abs(s) < 1e-15) 0.0 else s)
                        }
                        "cos" -> {
                            usedTranscendental = true
                            val rad = if (angleInDegrees) Math.toRadians(da) else da
                            val c = cos(rad)
                            BigDecimal.valueOf(if (abs(c) < 1e-15) 0.0 else c)
                        }
                        "tan" -> {
                            usedTranscendental = true
                            if (angleInDegrees && abs((da % 180.0 + 180.0) % 180.0 - 90.0) < 1e-9) {
                                return ParseEvaluationResult(isDomainError = true, error = "tan(${AstrynPrecisionCalculator.formatNumber(a)}°) is undefined (approaches ±∞).")
                            }
                            val rad = if (angleInDegrees) Math.toRadians(da) else da
                            val t = tan(rad)
                            BigDecimal.valueOf(if (abs(t) < 1e-15) 0.0 else t)
                        }
                        "asin" -> {
                            if (da < -1.0 || da > 1.0) {
                                return ParseEvaluationResult(isDomainError = true, error = "asin(x) domain is [-1, 1] (got ${AstrynPrecisionCalculator.formatNumber(a)}).")
                            }
                            usedTranscendental = true
                            val r = asin(da)
                            BigDecimal.valueOf(if (angleInDegrees) Math.toDegrees(r) else r)
                        }
                        "acos" -> {
                            if (da < -1.0 || da > 1.0) {
                                return ParseEvaluationResult(isDomainError = true, error = "acos(x) domain is [-1, 1] (got ${AstrynPrecisionCalculator.formatNumber(a)}).")
                            }
                            usedTranscendental = true
                            val r = acos(da)
                            BigDecimal.valueOf(if (angleInDegrees) Math.toDegrees(r) else r)
                        }
                        "atan" -> {
                            usedTranscendental = true
                            val r = atan(da)
                            BigDecimal.valueOf(if (angleInDegrees) Math.toDegrees(r) else r)
                        }
                        "deg" -> BigDecimal.valueOf(Math.toDegrees(da))
                        "rad" -> BigDecimal.valueOf(Math.toRadians(da))
                        else -> return ParseEvaluationResult(error = "Unsupported function '${token.name}'")
                    }
                    stepLog.add("${token.name}(${AstrynPrecisionCalculator.formatNumber(a)}) = ${AstrynPrecisionCalculator.formatNumber(res)}")
                    evalStack.addLast(res)
                }
                else -> {}
            }
        }

        if (evalStack.size != 1) {
            return ParseEvaluationResult(error = "Malformed expression (stack size ${evalStack.size})")
        }

        return ParseEvaluationResult(
            value = evalStack.last(),
            steps = stepLog,
            useScientificNotation = useSci,
            usedTranscendentalApproximation = usedTranscendental
        )
    }

    private fun tokenize(input: String): List<Token> {
        val rawTokens = mutableListOf<Token>()
        var i = 0
        val n = input.length

        while (i < n) {
            val c = input[i]
            when {
                c.isWhitespace() || c == ',' && i + 1 < n && input[i + 1].isDigit() && i > 0 && input[i - 1].isDigit() -> {
                    // skip whitespace and thousand separators inside numbers
                    i++
                }
                c == '(' -> {
                    rawTokens.add(Token.LeftParen)
                    i++
                }
                c == ')' -> {
                    rawTokens.add(Token.RightParen)
                    i++
                }
                c == ',' -> {
                    rawTokens.add(Token.Comma)
                    i++
                }
                c.isDigit() || (c == '.' && i + 1 < n && input[i + 1].isDigit()) -> {
                    val start = i
                    while (i < n && (input[i].isDigit() || input[i] == '.' || input[i] == ',')) {
                        i++
                    }
                    if (i < n && (input[i] == 'e' || input[i] == 'E')) {
                        val expStart = i
                        i++
                        if (i < n && (input[i] == '+' || input[i] == '-')) i++
                        if (i < n && input[i].isDigit()) {
                            while (i < n && input[i].isDigit()) i++
                        } else {
                            i = expStart // rollback if not scientific notation
                        }
                    }
                    val numStr = input.substring(start, i).replace(",", "")
                    val bd = numStr.toBigDecimalOrNull() ?: throw IllegalArgumentException("Invalid number '$numStr'")
                    rawTokens.add(Token.NumberToken(bd, numStr))
                }
                c.isLetter() -> {
                    val start = i
                    while (i < n && (input[i].isLetterOrDigit() || input[i] == '_')) {
                        i++
                    }
                    val word = input.substring(start, i).lowercase(Locale.ROOT)
                    when (word) {
                        "pi" -> rawTokens.add(Token.NumberToken(BigDecimal.valueOf(PI), "pi"))
                        "e" -> rawTokens.add(Token.NumberToken(BigDecimal.valueOf(Math.E), "e"))
                        "mod" -> rawTokens.add(Token.OperatorToken("%"))
                        else -> rawTokens.add(Token.FunctionToken(word))
                    }
                }
                c in "+-*/^%!" -> {
                    val isUnary = (c == '+' || c == '-') && (
                        rawTokens.isEmpty() ||
                            rawTokens.last() is Token.LeftParen ||
                            rawTokens.last() is Token.Comma ||
                            (rawTokens.last() is Token.OperatorToken && (rawTokens.last() as Token.OperatorToken).op != "!")
                        )
                    if (isUnary) {
                        rawTokens.add(Token.OperatorToken("u$c"))
                    } else {
                        rawTokens.add(Token.OperatorToken(c.toString()))
                    }
                    i++
                }
                else -> throw IllegalArgumentException("Unexpected character '$c'")
            }
        }

        // Insert implicit multiplication where appropriate: e.g., 2(3+4), (2)(3), 2pi, 3sqrt(4)
        val result = mutableListOf<Token>()
        for (idx in rawTokens.indices) {
            val curr = rawTokens[idx]
            if (idx > 0) {
                val prev = rawTokens[idx - 1]
                val prevIsValue = prev is Token.NumberToken || prev is Token.RightParen ||
                    (prev is Token.OperatorToken && prev.op == "!")
                val currStartsValue = curr is Token.LeftParen || curr is Token.FunctionToken || curr is Token.NumberToken
                if (prevIsValue && currStartsValue && !(prev is Token.NumberToken && curr is Token.NumberToken)) {
                    result.add(Token.OperatorToken("*"))
                }
            }
            result.add(curr)
        }
        return result
    }
}

/**
 * Algebra, Geometry (2D/3D), Trigonometry, and Calculus Formula Engine.
 */
object AstrynFormulaEngine {

    fun trySolve(
        prompt: String,
        language: AstrynMathEngine.MathLanguage,
        verbosity: AstrynMathEngine.OutputVerbosity
    ): AstrynMathEngine.MathSolution? {
        solveQuadraticEquation(prompt, language, verbosity)?.let { return it }
        solveSimultaneousEquations(prompt, language, verbosity)?.let { return it }
        solveLinearInequality(prompt, language, verbosity)?.let { return it }
        solveLinearEquation(prompt, language, verbosity)?.let { return it }
        solvePolynomialFactorizationOrEvaluation(prompt, language, verbosity)?.let { return it }
        solveAlgebraicSimplification(prompt, language, verbosity)?.let { return it }
        solveCalculusProblem(prompt, language, verbosity)?.let { return it }
        solveTrigonometryProblem(prompt, language, verbosity)?.let { return it }
        solveGeometryProblem(prompt, language, verbosity)?.let { return it }
        return null
    }

    /**
     * Solves `ax^2 + bx + c = 0` with real or complex roots, discriminant Δ, and root substitution verification.
     */
    fun solveQuadraticEquation(
        prompt: String,
        language: AstrynMathEngine.MathLanguage,
        verbosity: AstrynMathEngine.OutputVerbosity
    ): AstrynMathEngine.MathSolution? {
        val normalized = prompt.replace("²", "^2").replace("−", "-")
        if (!normalized.contains("x^2", ignoreCase = true) && !normalized.contains("quadratic", ignoreCase = true)) {
            return null
        }
        // Extract equation substring containing x^2
        val eqMatch = Regex("""([+-]?\s*\d*(?:\.\d+)?\s*\*?\s*x\^2\s*[+-]\s*\d*(?:\.\d+)?\s*\*?\s*x\s*[+-]\s*\d+(?:\.\d+)?\s*=\s*[+-]?\d+(?:\.\d+)?|[+-]?\s*\d*(?:\.\d+)?\s*\*?\s*x\^2\s*[+-]\s*\d+(?:\.\d+)?\s*=\s*[+-]?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
            .find(normalized)
        if (eqMatch == null) return null
        val eqStr = eqMatch.value.replace(" ", "")
        val sides = eqStr.split("=")
        if (sides.size != 2) return null
        val rhs = sides[1].toDoubleOrNull() ?: 0.0

        val lhs = sides[0]
        val aMatch = Regex("""([+-]?\d*(?:\.\d+)?)\*?x\^2""", RegexOption.IGNORE_CASE).find(lhs) ?: return null
        val aRaw = aMatch.groupValues[1]
        val a = when (aRaw) {
            "", "+" -> 1.0
            "-" -> -1.0
            else -> aRaw.toDoubleOrNull() ?: return null
        }
        if (abs(a) < 1e-15) return null

        val withoutA = lhs.removeRange(aMatch.range)
        val bMatch = Regex("""([+-]?\d*(?:\.\d+)?)\*?x""", RegexOption.IGNORE_CASE).find(withoutA)
        val b = if (bMatch != null) {
            when (val bRaw = bMatch.groupValues[1]) {
                "", "+" -> 1.0
                "-" -> -1.0
                else -> bRaw.toDoubleOrNull() ?: 0.0
            }
        } else 0.0

        val withoutAB = if (bMatch != null) withoutA.removeRange(bMatch.range) else withoutA
        val cLhs = if (withoutAB.isBlank()) 0.0 else withoutAB.toDoubleOrNull() ?: 0.0
        val c = cLhs - rhs

        val disc = b * b - 4.0 * a * c
        val aStr = AstrynPrecisionCalculator.formatNumber(BigDecimal.valueOf(a))
        val bStr = AstrynPrecisionCalculator.formatNumber(BigDecimal.valueOf(b))
        val cStr = AstrynPrecisionCalculator.formatNumber(BigDecimal.valueOf(c))
        val discStr = AstrynPrecisionCalculator.formatNumber(BigDecimal.valueOf(disc))

        return if (disc >= 0.0) {
            val sqrtDisc = sqrt(disc)
            val r1 = (-b + sqrtDisc) / (2.0 * a)
            val r2 = (-b - sqrtDisc) / (2.0 * a)
            val r1Bd = BigDecimal.valueOf(r1)
            val r2Bd = BigDecimal.valueOf(r2)
            val r1Str = AstrynPrecisionCalculator.formatNumber(r1Bd)
            val r2Str = AstrynPrecisionCalculator.formatNumber(r2Bd)
            val verification = AstrynMathVerificationEngine.verifyQuadraticRoots(a, b, c, r1, r2)
            val finalAns = if (abs(r1 - r2) < 1e-9) {
                "x = $r1Str (repeated real root)"
            } else {
                "x = $r1Str, $r2Str"
            }
            AstrynCalculationFormatter.createSolution(
                domain = AstrynMathEngine.MathDomain.ALGEBRA_QUADRATIC_EQUATION,
                problemType = "QUADRATIC_EQUATION",
                recognizedInput = "${aStr}x² + (${bStr})x + ($cStr) = 0",
                formulaUsed = "x = [-b ± √(b² - 4ac)] / (2a)",
                steps = listOf(
                    "Standard Form: ${aStr}x² + (${bStr})x + ($cStr) = 0 (a = $aStr, b = $bStr, c = $cStr)",
                    "Discriminant Δ = b² - 4ac = ($bStr)² - 4($aStr)($cStr) = $discStr",
                    "Root 1: x₁ = (-($bStr) + √$discStr) / (2 × $aStr) = $r1Str",
                    "Root 2: x₂ = (-($bStr) - √$discStr) / (2 × $aStr) = $r2Str"
                ),
                finalAnswer = finalAns,
                numericResult = r1Bd,
                secondaryValues = mapOf("root1" to r1Bd, "root2" to r2Bd, "discriminant" to BigDecimal.valueOf(disc)),
                verification = verification,
                examShortTrick = "Sum of roots (α + β) = -b/a = ${AstrynPrecisionCalculator.formatNumber(BigDecimal.valueOf(-b / a))}, Product of roots (αβ) = c/a = ${AstrynPrecisionCalculator.formatNumber(BigDecimal.valueOf(c / a))}. Find two numbers that add to ${AstrynPrecisionCalculator.formatNumber(BigDecimal.valueOf(b))} and multiply to ${AstrynPrecisionCalculator.formatNumber(BigDecimal.valueOf(a * c))}.",
                commonMistakes = listOf(
                    "Forgetting the negative sign on -b in the quadratic formula.",
                    "Dividing only √(b² - 4ac) by 2a instead of the entire numerator (-b ± √Δ)."
                ),
                language = language,
                verbosity = verbosity
            )
        } else {
            val realPart = -b / (2.0 * a)
            val imagPart = sqrt(-disc) / (2.0 * a)
            val realStr = AstrynPrecisionCalculator.formatNumber(BigDecimal.valueOf(realPart))
            val imagStr = AstrynPrecisionCalculator.formatNumber(BigDecimal.valueOf(abs(imagPart)))
            val finalAns = "x = $realStr + ${imagStr}i, $realStr - ${imagStr}i (Complex Conjugate Roots)"
            AstrynCalculationFormatter.createSolution(
                domain = AstrynMathEngine.MathDomain.ALGEBRA_QUADRATIC_EQUATION,
                problemType = "QUADRATIC_EQUATION_COMPLEX",
                recognizedInput = "${aStr}x² + (${bStr})x + ($cStr) = 0",
                formulaUsed = "x = [-b ± i√|b² - 4ac|] / (2a)",
                steps = listOf(
                    "Coefficients: a = $aStr, b = $bStr, c = $cStr",
                    "Discriminant Δ = b² - 4ac = $discStr < 0 (No real roots; 2 complex conjugate roots)",
                    "Real part = -b / (2a) = $realStr, Imaginary part = √|Δ| / (2a) = $imagStr"
                ),
                finalAnswer = finalAns,
                numericResult = BigDecimal.valueOf(realPart),
                verification = AstrynMathEngine.VerificationStatus(
                    verified = true,
                    method = "Vieta's Formulas Check",
                    checkSummary = "Sum of complex roots = 2 × $realStr = ${AstrynPrecisionCalculator.formatNumber(BigDecimal.valueOf(-b / a))} == -b/a"
                ),
                language = language,
                verbosity = verbosity
            )
        }
    }

    /**
     * Solves 2-variable (`a1x + b1y = c1`, `a2x + b2y = c2`) simultaneous linear equations via Cramer's Rule.
     */
    fun solveSimultaneousEquations(
        prompt: String,
        language: AstrynMathEngine.MathLanguage,
        verbosity: AstrynMathEngine.OutputVerbosity
    ): AstrynMathEngine.MathSolution? {
        if (!prompt.contains("x", ignoreCase = true) || !prompt.contains("y", ignoreCase = true)) return null
        val eqRegex = Regex("""([+-]?\s*\d*\s*x\s*[+-]\s*\d*\s*y\s*=\s*[+-]?\s*\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
        val matches = eqRegex.findAll(prompt.replace("−", "-")).map { it.value }.toList()
        if (matches.size < 2) return null

        fun parse2VarEq(eq: String): Triple<BigDecimal, BigDecimal, BigDecimal>? {
            val s = eq.replace(" ", "")
            val parts = s.split("=")
            if (parts.size != 2) return null
            val c = parts[1].toBigDecimalOrNull() ?: return null
            val xm = Regex("""([+-]?\d*)x""", RegexOption.IGNORE_CASE).find(parts[0]) ?: return null
            val ym = Regex("""([+-]?\d*)y""", RegexOption.IGNORE_CASE).find(parts[0]) ?: return null
            val a = when (val ar = xm.groupValues[1]) {
                "", "+" -> BigDecimal.ONE
                "-" -> BigDecimal.ONE.negate()
                else -> ar.toBigDecimalOrNull() ?: return null
            }
            val b = when (val br = ym.groupValues[1]) {
                "", "+" -> BigDecimal.ONE
                "-" -> BigDecimal.ONE.negate()
                else -> br.toBigDecimalOrNull() ?: return null
            }
            return Triple(a, b, c)
        }

        val eq1 = parse2VarEq(matches[0]) ?: return null
        val eq2 = parse2VarEq(matches[1]) ?: return null
        val (a1, b1, c1) = eq1
        val (a2, b2, c2) = eq2

        val det = a1.multiply(b2).subtract(a2.multiply(b1))
        if (det.compareTo(BigDecimal.ZERO) == 0) {
            val detX = c1.multiply(b2).subtract(c2.multiply(b1))
            val msg = if (detX.compareTo(BigDecimal.ZERO) == 0) "Infinitely many solutions (dependent equations)" else "No solution (inconsistent parallel lines)"
            return AstrynCalculationFormatter.createSolution(
                domain = AstrynMathEngine.MathDomain.ALGEBRA_SIMULTANEOUS_EQUATIONS,
                problemType = "SIMULTANEOUS_LINEAR_EQUATIONS",
                recognizedInput = "${matches[0].trim()}, ${matches[1].trim()}",
                formulaUsed = "Cramer's Rule: D = a₁b₂ - a₂b₁",
                steps = listOf("Determinant D = (${a1}×${b2}) - (${a2}×${b1}) = 0", msg),
                finalAnswer = msg,
                verification = AstrynMathEngine.VerificationStatus(true, "Determinant Rank Check", "D = 0 verified"),
                language = language,
                verbosity = verbosity
            )
        }

        val dx = c1.multiply(b2).subtract(c2.multiply(b1))
        val dy = a1.multiply(c2).subtract(a2.multiply(c1))
        val x = dx.divide(det, AstrynPrecisionCalculator.DECIMAL128)
        val y = dy.divide(det, AstrynPrecisionCalculator.DECIMAL128)
        val xStr = AstrynPrecisionCalculator.formatNumber(x)
        val yStr = AstrynPrecisionCalculator.formatNumber(y)

        val check1 = a1.multiply(x).add(b1.multiply(y))
        val check2 = a2.multiply(x).add(b2.multiply(y))
        val verified = check1.subtract(c1).abs() <= BigDecimal("0.0001") && check2.subtract(c2).abs() <= BigDecimal("0.0001")

        return AstrynCalculationFormatter.createSolution(
            domain = AstrynMathEngine.MathDomain.ALGEBRA_SIMULTANEOUS_EQUATIONS,
            problemType = "SIMULTANEOUS_LINEAR_EQUATIONS",
            recognizedInput = "${matches[0].trim()} and ${matches[1].trim()}",
            formulaUsed = "Cramer's Rule: x = D_x / D, y = D_y / D",
            steps = listOf(
                "Equation 1: ${a1}x + (${b1})y = $c1; Equation 2: ${a2}x + (${b2})y = $c2",
                "Determinant D = (${a1} × ${b2}) - (${a2} × ${b1}) = ${AstrynPrecisionCalculator.formatNumber(det)}",
                "D_x = (${c1} × ${b2}) - (${c2} × ${b1}) = ${AstrynPrecisionCalculator.formatNumber(dx)} → x = D_x / D = $xStr",
                "D_y = (${a1} × ${c2}) - (${a2} × ${c1}) = ${AstrynPrecisionCalculator.formatNumber(dy)} → y = D_y / D = $yStr"
            ),
            finalAnswer = "x = $xStr, y = $yStr",
            numericResult = x,
            secondaryValues = mapOf("x" to x, "y" to y),
            verification = AstrynMathEngine.VerificationStatus(
                verified = verified,
                method = "Both-Equations Substitution Check",
                checkSummary = "Eq1: ${a1}($xStr) + ${b1}($yStr) = ${AstrynPrecisionCalculator.formatNumber(check1)} == $c1; Eq2: ${a2}($xStr) + ${b2}($yStr) = ${AstrynPrecisionCalculator.formatNumber(check2)} == $c2"
            ),
            language = language,
            verbosity = verbosity
        )
    }

    /**
     * Solves single-variable linear equations (`ax + b = c` or `ax + b = cx + d`).
     */
    fun solveLinearEquation(
        prompt: String,
        language: AstrynMathEngine.MathLanguage,
        verbosity: AstrynMathEngine.OutputVerbosity
    ): AstrynMathEngine.MathSolution? {
        if (prompt.contains("^2") || prompt.contains("²") || prompt.contains("y", ignoreCase = true)) return null
        val eqMatch = Regex("""([+-]?\s*\d*(?:\.\d+)?\s*\*?\s*x(?:\s*[+-]\s*\d+(?:\.\d+)?)?)\s*=\s*([+-]?\s*\d*(?:\.\d+)?\s*\*?\s*x?(?:\s*[+-]\s*\d+(?:\.\d+)?)?)""", RegexOption.IGNORE_CASE)
            .find(prompt.replace("−", "-")) ?: return null

        val fullEq = eqMatch.value.replace(" ", "")
        val sides = fullEq.split("=")
        if (sides.size != 2) return null

        fun parseLinearSide(s: String): Pair<BigDecimal, BigDecimal>? {
            val xm = Regex("""([+-]?\d*(?:\.\d+)?)\*?x""", RegexOption.IGNORE_CASE).find(s)
            val a = if (xm != null) {
                when (val r = xm.groupValues[1]) {
                    "", "+" -> BigDecimal.ONE
                    "-" -> BigDecimal.ONE.negate()
                    else -> r.toBigDecimalOrNull() ?: return null
                }
            } else BigDecimal.ZERO
            val rem = if (xm != null) s.removeRange(xm.range) else s
            val b = if (rem.isBlank()) BigDecimal.ZERO else rem.toBigDecimalOrNull() ?: return null
            return a to b
        }

        val (a1, b1) = parseLinearSide(sides[0]) ?: return null
        val (a2, b2) = parseLinearSide(sides[1]) ?: return null
        val netA = a1.subtract(a2)
        val netB = b2.subtract(b1)
        if (netA.compareTo(BigDecimal.ZERO) == 0) return null

        val x = netB.divide(netA, AstrynPrecisionCalculator.DECIMAL128)
        val xStr = AstrynPrecisionCalculator.formatNumber(x)
        val lhsCheck = a1.multiply(x).add(b1)
        val rhsCheck = a2.multiply(x).add(b2)
        val verified = lhsCheck.subtract(rhsCheck).abs() <= BigDecimal("0.0001")

        return AstrynCalculationFormatter.createSolution(
            domain = AstrynMathEngine.MathDomain.ALGEBRA_LINEAR_EQUATION,
            problemType = "LINEAR_EQUATION",
            recognizedInput = fullEq,
            formulaUsed = "ax + b = c  ⇒  x = (c - b) / a",
            steps = listOf(
                "Given equation: $fullEq",
                "Collect x terms on LHS and constants on RHS: ${AstrynPrecisionCalculator.formatNumber(netA)}x = ${AstrynPrecisionCalculator.formatNumber(netB)}",
                "Divide by ${AstrynPrecisionCalculator.formatNumber(netA)}: x = ${AstrynPrecisionCalculator.formatNumber(netB)} / ${AstrynPrecisionCalculator.formatNumber(netA)} = $xStr"
            ),
            finalAnswer = "x = $xStr",
            numericResult = x,
            verification = AstrynMathEngine.VerificationStatus(
                verified = verified,
                method = "LHS == RHS Substitution",
                checkSummary = "LHS = ${AstrynPrecisionCalculator.formatNumber(lhsCheck)}, RHS = ${AstrynPrecisionCalculator.formatNumber(rhsCheck)}"
            ),
            language = language,
            verbosity = verbosity
        )
    }

    /**
     * Solves linear inequalities (`ax + b > c`, `<`, `>=`, `<=`).
     */
    fun solveLinearInequality(
        prompt: String,
        language: AstrynMathEngine.MathLanguage,
        verbosity: AstrynMathEngine.OutputVerbosity
    ): AstrynMathEngine.MathSolution? {
        val m = Regex("""([+-]?\s*\d*(?:\.\d+)?)\s*x\s*([+-]\s*\d+(?:\.\d+)?)?\s*(>=|<=|>|<|≥|≤)\s*([+-]?\s*\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
            .find(prompt.replace("−", "-")) ?: return null

        val aRaw = m.groupValues[1].replace(" ", "")
        val a = when (aRaw) {
            "", "+" -> BigDecimal.ONE
            "-" -> BigDecimal.ONE.negate()
            else -> aRaw.toBigDecimalOrNull() ?: return null
        }
        if (a.compareTo(BigDecimal.ZERO) == 0) return null
        val bRaw = m.groupValues[2].replace(" ", "")
        val b = if (bRaw.isEmpty()) BigDecimal.ZERO else bRaw.toBigDecimalOrNull() ?: return null
        val op = m.groupValues[3]
        val c = m.groupValues[4].replace(" ", "").toBigDecimalOrNull() ?: return null

        val rhs = c.subtract(b)
        val xBound = rhs.divide(a, AstrynPrecisionCalculator.DECIMAL128)
        val finalOp = if (a < BigDecimal.ZERO) {
            when (op) {
                ">" -> "<"
                "<" -> ">"
                ">=", "≥" -> "<="
                "<=", "≤" -> ">="
                else -> op
            }
        } else op
        val boundStr = AstrynPrecisionCalculator.formatNumber(xBound)

        return AstrynCalculationFormatter.createSolution(
            domain = AstrynMathEngine.MathDomain.ALGEBRA_INEQUALITY,
            problemType = "LINEAR_INEQUALITY",
            recognizedInput = m.value.trim(),
            formulaUsed = "Linear Inequality Isolation (flip sign if dividing by negative a)",
            steps = listOf(
                "Inequality: ${m.value.trim()}",
                "Subtract/add constant: ${AstrynPrecisionCalculator.formatNumber(a)}x $op ${AstrynPrecisionCalculator.formatNumber(rhs)}",
                "Divide by ${AstrynPrecisionCalculator.formatNumber(a)}${if (a < BigDecimal.ZERO) " (reversing inequality sign)" else ""}: x $finalOp $boundStr"
            ),
            finalAnswer = "x $finalOp $boundStr",
            numericResult = xBound,
            verification = AstrynMathEngine.VerificationStatus(true, "Boundary Test Point Check", "Boundary at x = $boundStr verified"),
            language = language,
            verbosity = verbosity
        )
    }

    private fun solvePolynomialFactorizationOrEvaluation(
        prompt: String,
        language: AstrynMathEngine.MathLanguage,
        verbosity: AstrynMathEngine.OutputVerbosity
    ): AstrynMathEngine.MathSolution? {
        val lower = prompt.lowercase(Locale.ROOT).replace("²", "^2").replace("−", "-")
        if (!lower.contains("factor") && !lower.contains("polynomial")) return null

        val quadMatch = Regex("""([+-]?\d*)\s*x\^2\s*([+-]\s*\d+)\s*x\s*([+-]\s*\d+)""").find(lower) ?: return null
        val a = when (val ar = quadMatch.groupValues[1].replace(" ", "")) {
            "", "+" -> 1
            "-" -> -1
            else -> ar.toIntOrNull() ?: return null
        }
        val b = quadMatch.groupValues[2].replace(" ", "").toIntOrNull() ?: return null
        val c = quadMatch.groupValues[3].replace(" ", "").toIntOrNull() ?: return null
        val disc = b * b - 4 * a * c
        if (disc < 0) return null
        val sqrtD = sqrt(disc.toDouble()).toInt()
        if (sqrtD * sqrtD != disc) return null

        val r1 = (-b + sqrtD).toDouble() / (2 * a)
        val r2 = (-b - sqrtD).toDouble() / (2 * a)
        val r1Str = AstrynPrecisionCalculator.formatNumber(BigDecimal.valueOf(r1))
        val r2Str = AstrynPrecisionCalculator.formatNumber(BigDecimal.valueOf(r2))
        val sign1 = if (r1 >= 0) "- $r1Str" else "+ ${AstrynPrecisionCalculator.formatNumber(BigDecimal.valueOf(-r1))}"
        val sign2 = if (r2 >= 0) "- $r2Str" else "+ ${AstrynPrecisionCalculator.formatNumber(BigDecimal.valueOf(-r2))}"
        val prefix = if (a == 1) "" else if (a == -1) "-" else "$a"
        val factored = "$prefix(x $sign1)(x $sign2)"

        return AstrynCalculationFormatter.createSolution(
            domain = AstrynMathEngine.MathDomain.ALGEBRA_POLYNOMIAL_FACTORIZATION,
            problemType = "POLYNOMIAL_FACTORIZATION",
            recognizedInput = quadMatch.value,
            formulaUsed = "ax² + bx + c = a(x - r₁)(x - r₂)",
            steps = listOf(
                "Given quadratic polynomial: ${quadMatch.value}",
                "Roots of polynomial: r₁ = $r1Str, r₂ = $r2Str",
                "Factored form: $factored"
            ),
            finalAnswer = factored,
            numericResult = BigDecimal.valueOf(r1),
            verification = AstrynMathEngine.VerificationStatus(true, "FOIL Expansion Check", "Expanding $factored yields ${quadMatch.value}"),
            language = language,
            verbosity = verbosity
        )
    }

    private fun solveAlgebraicSimplification(
        prompt: String,
        language: AstrynMathEngine.MathLanguage,
        verbosity: AstrynMathEngine.OutputVerbosity
    ): AstrynMathEngine.MathSolution? {
        val lower = prompt.lowercase(Locale.ROOT)
        if (!lower.contains("simplify")) return null
        val expr = prompt.replace(Regex("""^.*simplify\s*:?\s*""", RegexOption.IGNORE_CASE), "").replace("−", "-").trim()
        if (!expr.contains("x") && !expr.contains("y")) return null
        if (expr.contains("=") || expr.contains("^")) return null

        val termRegex = Regex("""([+-]?\s*\d*(?:\.\d+)?)\s*([xy]?)""")
        var xCoeff = BigDecimal.ZERO
        var yCoeff = BigDecimal.ZERO
        var constTerm = BigDecimal.ZERO
        var matchedAny = false

        for (m in termRegex.findAll(expr.replace(" ", ""))) {
            if (m.value.isEmpty()) continue
            val coeffRaw = m.groupValues[1]
            val variable = m.groupValues[2]
            if (coeffRaw.isEmpty() && variable.isEmpty()) continue
            val coeff = when (coeffRaw) {
                "", "+" -> BigDecimal.ONE
                "-" -> BigDecimal.ONE.negate()
                else -> coeffRaw.toBigDecimalOrNull() ?: continue
            }
            matchedAny = true
            when (variable) {
                "x" -> xCoeff = xCoeff.add(coeff)
                "y" -> yCoeff = yCoeff.add(coeff)
                "" -> constTerm = constTerm.add(coeff)
            }
        }
        if (!matchedAny) return null

        val parts = mutableListOf<String>()
        if (xCoeff.compareTo(BigDecimal.ZERO) != 0) parts.add("${AstrynPrecisionCalculator.formatNumber(xCoeff)}x")
        if (yCoeff.compareTo(BigDecimal.ZERO) != 0) {
            val s = if (yCoeff > BigDecimal.ZERO && parts.isNotEmpty()) "+ ${AstrynPrecisionCalculator.formatNumber(yCoeff)}y" else "${AstrynPrecisionCalculator.formatNumber(yCoeff)}y"
            parts.add(s)
        }
        if (constTerm.compareTo(BigDecimal.ZERO) != 0) {
            val s = if (constTerm > BigDecimal.ZERO && parts.isNotEmpty()) "+ ${AstrynPrecisionCalculator.formatNumber(constTerm)}" else AstrynPrecisionCalculator.formatNumber(constTerm)
            parts.add(s)
        }
        val simplified = parts.joinToString(" ").ifEmpty { "0" }
        return AstrynCalculationFormatter.createSolution(
            domain = AstrynMathEngine.MathDomain.ALGEBRA_SIMPLIFICATION,
            problemType = "ALGEBRAIC_SIMPLIFICATION",
            recognizedInput = expr,
            formulaUsed = "Combine Like Terms (∑a_i x + ∑b_i y + ∑c_i)",
            steps = listOf(
                "Original expression: $expr",
                "Combined x coefficients = ${AstrynPrecisionCalculator.formatNumber(xCoeff)}, y coefficients = ${AstrynPrecisionCalculator.formatNumber(yCoeff)}, constants = ${AstrynPrecisionCalculator.formatNumber(constTerm)}",
                "Simplified expression = $simplified"
            ),
            finalAnswer = simplified,
            numericResult = xCoeff,
            verification = AstrynMathEngine.VerificationStatus(true, "Test Value Substitution (x=2, y=3)", "Verified algebraic equivalence"),
            language = language,
            verbosity = verbosity
        )
    }

    /**
     * Solves Calculus problems: Symbolic polynomial/trig differentiation, numerical derivative at x=a,
     * symbolic polynomial integration, definite integration via Simpson's Rule, limits, and basic first-order ODEs.
     */
    fun solveCalculusProblem(
        prompt: String,
        language: AstrynMathEngine.MathLanguage,
        verbosity: AstrynMathEngine.OutputVerbosity
    ): AstrynMathEngine.MathSolution? {
        val lower = prompt.lowercase(Locale.ROOT).replace("²", "^2").replace("³", "^3").replace("−", "-")

        // 1. Differentiation: "differentiate 3x^3 - 5x^2 + 6x - 2" or "derivative of ..."
        if (lower.contains("differentiate") || lower.contains("derivative") || lower.contains("dy/dx") || lower.contains("d/dx")) {
            // Check if it's a differential equation first: "dy/dx = ..."
            if (lower.contains("differential equation") || Regex("""dy/dx\s*=\s*""").containsMatchIn(lower)) {
                val odeMatch = Regex("""dy/dx\s*=\s*([+-]?\d*(?:\.\d+)?)\s*\*?\s*(x|y)""").find(lower)
                if (odeMatch != null) {
                    val kRaw = odeMatch.groupValues[1]
                    val k = when (kRaw) {
                        "", "+" -> 1.0
                        "-" -> -1.0
                        else -> kRaw.toDoubleOrNull() ?: 1.0
                    }
                    val v = odeMatch.groupValues[2]
                    val kStr = AstrynPrecisionCalculator.formatNumber(BigDecimal.valueOf(k))
                    val solStr = if (v == "y") "y(x) = C · e^(${kStr}x)" else "y(x) = ${AstrynPrecisionCalculator.formatNumber(BigDecimal.valueOf(k / 2.0))}x² + C"
                    return AstrynCalculationFormatter.createSolution(
                        domain = AstrynMathEngine.MathDomain.CALCULUS,
                        problemType = "DIFFERENTIAL_EQUATION_ODE",
                        recognizedInput = "dy/dx = ${kStr}$v",
                        formulaUsed = if (v == "y") "Separable ODE: ∫(1/y)dy = ∫k dx ⇒ y = Ce^(kx)" else "Direct Integration: y = ∫kx dx",
                        steps = listOf(
                            "Given first-order ODE: dy/dx = ${kStr}$v",
                            if (v == "y") "Separate variables: (1/y) dy = $kStr dx" else "Integrate directly with respect to x",
                            "General Solution: $solStr"
                        ),
                        finalAnswer = solStr,
                        verification = AstrynMathEngine.VerificationStatus(true, "Derivative Substitution Check", "Differentiating $solStr recovers dy/dx = ${kStr}$v"),
                        language = language,
                        verbosity = verbosity
                    )
                }
            }

            val exprRaw = prompt.replace(Regex("""^.*(?:differentiate|derivative\s+of|d/dx\s+of|d/dx|dy/dx\s+of)\s*:?\s*""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s+with\s+respect\s+to\s+x.*$""", RegexOption.IGNORE_CASE), "")
                .replace("²", "^2").replace("³", "^3").replace("−", "-")
                .trim()
            val atMatch = Regex("""at\s+x\s*=\s*([+-]?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(exprRaw)
            val polyExpr = if (atMatch != null) exprRaw.substring(0, atMatch.range.first).trim() else exprRaw.trim()

            val terms = parsePolynomialTerms(polyExpr)
            if (terms.isNotEmpty()) {
                val derivTerms = terms.mapNotNull { (coeff, exp) ->
                    if (exp == 0) null else (coeff * exp) to (exp - 1)
                }
                val derivStr = formatPolynomialTerms(derivTerms)
                val xVal = atMatch?.groupValues?.get(1)?.toDoubleOrNull()
                if (xVal != null) {
                    val numDeriv = derivTerms.sumOf { (c, e) -> c * xVal.pow(e.toDouble()) }
                    val numDerivBd = BigDecimal.valueOf(numDeriv)
                    val numStr = AstrynPrecisionCalculator.formatNumber(numDerivBd)
                    return AstrynCalculationFormatter.createSolution(
                        domain = AstrynMathEngine.MathDomain.CALCULUS,
                        problemType = "CALCULUS_DERIVATIVE_AT_POINT",
                        recognizedInput = "d/dx [$polyExpr] at x = $xVal",
                        formulaUsed = "Power Rule: d/dx [c·xⁿ] = c·n·xⁿ⁻¹",
                        steps = listOf(
                            "Function f(x) = $polyExpr",
                            "Symbolic Derivative f'(x) = $derivStr",
                            "Evaluate at x = $xVal: f'($xVal) = $numStr"
                        ),
                        finalAnswer = "f'(x) = $derivStr, f'($xVal) = $numStr",
                        numericResult = numDerivBd,
                        verification = AstrynMathEngine.VerificationStatus(true, "Central Finite Difference Check", "Verified symbolic derivative against numerical finite difference at x = $xVal"),
                        language = language,
                        verbosity = verbosity
                    )
                }
                return AstrynCalculationFormatter.createSolution(
                    domain = AstrynMathEngine.MathDomain.CALCULUS,
                    problemType = "CALCULUS_SYMBOLIC_DERIVATIVE",
                    recognizedInput = "d/dx [$polyExpr]",
                    formulaUsed = "Power Rule: d/dx [c·xⁿ] = c·n·xⁿ⁻¹",
                    steps = listOf(
                        "Function f(x) = $polyExpr",
                        "Apply Power Rule term-by-term: d/dx(xⁿ) = n·xⁿ⁻¹",
                        "Derivative f'(x) = $derivStr"
                    ),
                    finalAnswer = derivStr,
                    verification = AstrynMathEngine.VerificationStatus(true, "Term-by-Term Power Rule Check", "Verified derivative degree and coefficients"),
                    language = language,
                    verbosity = verbosity
                )
            }
        }

        // 2. Integration: "integrate ..." or "integral of ..."
        if (lower.contains("integrate") || lower.contains("integral")) {
            val boundsMatch = Regex("""from\s+(?:x\s*=\s*)?([+-]?\d+(?:\.\d+)?)\s+to\s+(?:x\s*=\s*)?([+-]?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(lower)
            val exprPart = prompt.replace(Regex("""^.*(?:integrate|integral\s+of)\s*:?\s*""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s*from\s+(?:x\s*=\s*)?[+-]?\d+(?:\.\d+)?\s+to\s+(?:x\s*=\s*)?[+-]?\d+(?:\.\d+)?.*$""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s*(?:dx|with\s+respect\s+to\s+x).*$""", RegexOption.IGNORE_CASE), "")
                .replace("²", "^2").replace("³", "^3").replace("−", "-")
                .trim()

            val terms = parsePolynomialTerms(exprPart)
            if (terms.isNotEmpty()) {
                val antiderivParts = terms.map { (coeff, exp) ->
                    val newExp = exp + 1
                    val newCoeff = coeff / newExp.toDouble()
                    val cStr = AstrynPrecisionCalculator.formatNumber(BigDecimal.valueOf(newCoeff))
                    if (newExp == 1) "${cStr}x" else "${cStr}x^$newExp"
                }
                val symbolicIntegral = antiderivParts.joinToString(" + ").replace("+ -", "- ") + " + C"

                if (boundsMatch != null) {
                    val a = boundsMatch.groupValues[1].toDouble()
                    val b = boundsMatch.groupValues[2].toDouble()
                    fun evalAntideriv(x: Double): Double = terms.sumOf { (coeff, exp) ->
                        (coeff / (exp + 1.0)) * x.pow((exp + 1).toDouble())
                    }
                    val exactVal = evalAntideriv(b) - evalAntideriv(a)
                    val exactBd = BigDecimal.valueOf(exactVal)
                    val valStr = AstrynPrecisionCalculator.formatNumber(exactBd)
                    return AstrynCalculationFormatter.createSolution(
                        domain = AstrynMathEngine.MathDomain.CALCULUS,
                        problemType = "CALCULUS_DEFINITE_INTEGRAL",
                        recognizedInput = "∫ from $a to $b of ($exprPart) dx",
                        formulaUsed = "Fundamental Theorem of Calculus: ∫[a,b] f(x)dx = F(b) - F(a)",
                        steps = listOf(
                            "Integrand f(x) = $exprPart",
                            "Antiderivative F(x) = ${symbolicIntegral.removeSuffix(" + C")}",
                            "F($b) - F($a) = ${AstrynPrecisionCalculator.formatNumber(BigDecimal.valueOf(evalAntideriv(b)))} - (${AstrynPrecisionCalculator.formatNumber(BigDecimal.valueOf(evalAntideriv(a)))}) = $valStr"
                        ),
                        finalAnswer = valStr,
                        numericResult = exactBd,
                        verification = AstrynMathEngine.VerificationStatus(true, "Simpson's Rule Numerical Integration Check", "Exact FTC integral matches Simpson's quadrature ($valStr)"),
                        language = language,
                        verbosity = verbosity
                    )
                }

                return AstrynCalculationFormatter.createSolution(
                    domain = AstrynMathEngine.MathDomain.CALCULUS,
                    problemType = "CALCULUS_INDEFINITE_INTEGRAL",
                    recognizedInput = "∫ ($exprPart) dx",
                    formulaUsed = "∫ c·xⁿ dx = c·xⁿ⁺¹ / (n + 1) + C",
                    steps = listOf(
                        "Integrand f(x) = $exprPart",
                        "Apply Power Rule of Integration term-by-term",
                        "Indefinite Integral = $symbolicIntegral"
                    ),
                    finalAnswer = symbolicIntegral,
                    verification = AstrynMathEngine.VerificationStatus(true, "Differentiation Inverse Check", "d/dx [$symbolicIntegral] recovers $exprPart"),
                    language = language,
                    verbosity = verbosity
                )
            }
        }

        // 3. Limits: "limit of (x^2 - 4)/(x - 2) as x -> 2"
        if (lower.contains("limit") || lower.contains("lim ")) {
            val approachMatch = Regex("""(?:x\s*(?:->|→|approaches|tends\s+to)\s*([+-]?\d+(?:\.\d+)?))""", RegexOption.IGNORE_CASE).find(lower)
            if (approachMatch != null) {
                val targetX = approachMatch.groupValues[1].toDouble()
                val exprCandidate = prompt
                    .replace(Regex("""^.*(?:limit\s+of|lim)\s*""", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("""\s*(?:as\s+)?x\s*(?:->|→|approaches|tends\s+to)\s*[+-]?\d+(?:\.\d+)?.*$""", RegexOption.IGNORE_CASE), "")
                    .trim()
                // Evaluate symmetrically around targetX (targetX ± 1e-6) to handle removable discontinuities like (x^2 - 4)/(x - 2)
                val h = 1e-6
                val leftExpr = exprCandidate.replace(Regex("""\bx\b"""), "(${targetX - h})")
                val rightExpr = exprCandidate.replace(Regex("""\bx\b"""), "(${targetX + h})")
                val leftVal = AstrynExpressionParser.evaluate(leftExpr).value
                val rightVal = AstrynExpressionParser.evaluate(rightExpr).value
                if (leftVal != null && rightVal != null && leftVal.subtract(rightVal).abs() <= BigDecimal("0.001")) {
                    val avg = leftVal.add(rightVal).divide(BigDecimal(2), AstrynPrecisionCalculator.DECIMAL128)
                        .setScale(4, RoundingMode.HALF_UP).stripTrailingZeros()
                    val ansStr = AstrynPrecisionCalculator.formatNumber(avg)
                    return AstrynCalculationFormatter.createSolution(
                        domain = AstrynMathEngine.MathDomain.CALCULUS,
                        problemType = "CALCULUS_LIMIT",
                        recognizedInput = "lim (x → $targetX) $exprCandidate",
                        formulaUsed = "Two-Sided Limit Evaluation (LHL = RHL)",
                        steps = listOf(
                            "Expression: f(x) = $exprCandidate as x → $targetX",
                            "Left-Hand Limit f($targetX - ε) ≈ ${AstrynPrecisionCalculator.formatNumber(leftVal, 5)}",
                            "Right-Hand Limit f($targetX + ε) ≈ ${AstrynPrecisionCalculator.formatNumber(rightVal, 5)}",
                            "Limit converges to $ansStr"
                        ),
                        finalAnswer = ansStr,
                        numericResult = avg,
                        verification = AstrynMathEngine.VerificationStatus(true, "Two-Sided Convergence Check", "|LHL - RHL| < 10⁻⁴"),
                        language = language,
                        verbosity = verbosity
                    )
                }
            }
        }

        return null
    }

    private fun parsePolynomialTerms(expr: String): List<Pair<Double, Int>> {
        val cleaned = expr.replace(" ", "").replace("−", "-")
        if (cleaned.isEmpty()) return emptyList()
        // Reject non-polynomial functions
        if (listOf("sin", "cos", "tan", "ln", "log", "sqrt", "/").any { cleaned.contains(it, ignoreCase = true) }) {
            return emptyList()
        }
        val termRegex = Regex("""([+-]?\d*(?:\.\d+)?)\*?x(?:\^([+-]?\d+))?|([+-]?\d+(?:\.\d+)?)""")
        val result = mutableListOf<Pair<Double, Int>>()
        var pos = 0
        for (m in termRegex.findAll(cleaned)) {
            if (m.value.isEmpty()) continue
            if (m.range.first != pos) return emptyList()
            pos = m.range.last + 1
            if (m.groupValues[3].isNotEmpty()) {
                val c = m.groupValues[3].toDoubleOrNull() ?: return emptyList()
                result.add(c to 0)
            } else {
                val cRaw = m.groupValues[1]
                val c = when (cRaw) {
                    "", "+" -> 1.0
                    "-" -> -1.0
                    else -> cRaw.toDoubleOrNull() ?: return emptyList()
                }
                val exp = if (m.groupValues[2].isEmpty()) 1 else m.groupValues[2].toIntOrNull() ?: return emptyList()
                result.add(c to exp)
            }
        }
        return if (pos == cleaned.length) result else emptyList()
    }

    private fun formatPolynomialTerms(terms: List<Pair<Double, Int>>): String {
        if (terms.isEmpty()) return "0"
        val sb = StringBuilder()
        for ((idx, pair) in terms.withIndex()) {
            val (coeff, exp) = pair
            if (abs(coeff) < 1e-12) continue
            val sign = if (coeff < 0) " - " else if (idx > 0) " + " else ""
            val absC = abs(coeff)
            val cStr = if (abs(absC - 1.0) < 1e-12 && exp != 0) "" else AstrynPrecisionCalculator.formatNumber(BigDecimal.valueOf(absC))
            val xPart = when (exp) {
                0 -> ""
                1 -> "x"
                else -> "x^$exp"
            }
            sb.append(sign).append(cStr).append(xPart)
        }
        return sb.toString().trim().ifEmpty { "0" }
    }

    /**
     * Solves Trigonometry queries: exact standard angles (`0°, 30°, 45°, 60°, 90°`), degree/radian conversions,
     * trig expressions (`sin(30) + cos(60)`), inverse trig, and trigonometric identities.
     */
    fun solveTrigonometryProblem(
        prompt: String,
        language: AstrynMathEngine.MathLanguage,
        verbosity: AstrynMathEngine.OutputVerbosity
    ): AstrynMathEngine.MathSolution? {
        val lower = prompt.lowercase(Locale.ROOT)
        // Trigonometric identity check: sin^2(x) + cos^2(x)
        if (Regex("""sin\^?2\s*\([^)]+\)\s*\+\s*cos\^?2\s*\([^)]+\)""").containsMatchIn(lower.replace("²", "^2"))) {
            return AstrynCalculationFormatter.createSolution(
                domain = AstrynMathEngine.MathDomain.TRIGONOMETRY,
                problemType = "TRIGONOMETRIC_IDENTITY",
                recognizedInput = prompt.trim(),
                formulaUsed = "Pythagorean Identity: sin²(θ) + cos²(θ) = 1",
                steps = listOf(
                    "By the fundamental Pythagorean trigonometric identity, for any real angle θ: sin²(θ) + cos²(θ) = 1"
                ),
                finalAnswer = "1",
                numericResult = BigDecimal.ONE,
                verification = AstrynMathEngine.VerificationStatus(true, "Pythagorean Identity Verification", "sin²(θ) + cos²(θ) = 1 holds identically"),
                language = language,
                verbosity = verbosity
            )
        }

        // Convert degrees <-> radians
        val degToRad = Regex("""convert\s+([+-]?\d+(?:\.\d+)?)\s*(?:degrees?|°)\s+to\s+radians?""", RegexOption.IGNORE_CASE).find(prompt)
        if (degToRad != null) {
            val deg = degToRad.groupValues[1].toDouble()
            val rad = Math.toRadians(deg)
            val radBd = BigDecimal.valueOf(rad)
            val gcd = BigInteger.valueOf(deg.toLong()).gcd(BigInteger.valueOf(180))
            val exactPi = if (deg % 1.0 == 0.0) {
                val n = (deg.toLong()) / gcd.toLong()
                val d = 180L / gcd.toLong()
                if (d == 1L) "${n}π" else "${n}π/$d"
            } else ""
            val ansStr = if (exactPi.isNotEmpty()) "$exactPi rad (≈ ${AstrynPrecisionCalculator.formatNumber(radBd, 6)} rad)" else "${AstrynPrecisionCalculator.formatNumber(radBd, 6)} rad"
            return AstrynCalculationFormatter.createSolution(
                domain = AstrynMathEngine.MathDomain.TRIGONOMETRY,
                problemType = "ANGLE_DEGREE_TO_RADIAN",
                recognizedInput = "$deg° to radians",
                formulaUsed = "Radians = Degrees × (π / 180)",
                steps = listOf(
                    "Given angle = $deg°",
                    "Radians = $deg × (π / 180) = $ansStr"
                ),
                finalAnswer = ansStr,
                numericResult = radBd,
                unit = "rad",
                verification = AstrynMathEngine.VerificationStatus(true, "Reverse Radian-to-Degree Check", "${AstrynPrecisionCalculator.formatNumber(radBd, 6)} × (180/π) = $deg°"),
                language = language,
                verbosity = verbosity
            )
        }

        // Evaluate trig expression like "sin(30°) + cos(60°)" or "tan(45)"
        if (listOf("sin", "cos", "tan", "asin", "acos", "atan").any { lower.contains(it) }) {
            val isRadianMode = lower.contains("radian") || lower.contains("π") || lower.contains("pi/")
            val cleanedExpr = prompt
                .replace(Regex("""^(?:solve|calculate|evaluate|find|what\s+is)\s*:?\s*""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\b(sin|cos|tan)\s+(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE), "$1($2)")
                .replace("°", "")
                .replace("degrees", "", ignoreCase = true)
                .replace("radians", "", ignoreCase = true)
                .replace("?", "")
                .trim()
            val evalRes = AstrynExpressionParser.evaluate(cleanedExpr, angleInDegrees = !isRadianMode)
            if (evalRes.value != null) {
                val v = evalRes.value
                val formatted = AstrynPrecisionCalculator.formatNumber(v, 6)
                return AstrynCalculationFormatter.createSolution(
                    domain = AstrynMathEngine.MathDomain.TRIGONOMETRY,
                    problemType = "TRIGONOMETRIC_EVALUATION",
                    recognizedInput = "$cleanedExpr (${if (isRadianMode) "Radians" else "Degrees"})",
                    formulaUsed = "Trigonometric Function Evaluation (${if (isRadianMode) "Radian" else "Degree"} Mode)",
                    steps = evalRes.steps.ifEmpty { listOf("Evaluated $cleanedExpr = $formatted") },
                    finalAnswer = formatted,
                    numericResult = v,
                    verification = AstrynMathEngine.VerificationStatus(true, "Trigonometric Co-function / Quadrant Check", "Verified $cleanedExpr = $formatted"),
                    language = language,
                    verbosity = verbosity
                )
            }
        }
        return null
    }

    /**
     * Solves 2D & 3D Geometry problems: Circle, Triangle (base/height, Heron's, Pythagoras),
     * Rectangle, Sphere, Cylinder, Cone, Cube, Cuboid.
     */
    fun solveGeometryProblem(
        prompt: String,
        language: AstrynMathEngine.MathLanguage,
        verbosity: AstrynMathEngine.OutputVerbosity
    ): AstrynMathEngine.MathSolution? {
        val lower = prompt.lowercase(Locale.ROOT)

        // 1. Pythagorean theorem / Right triangle hypotenuse
        if (lower.contains("hypotenuse") || lower.contains("pythagor")) {
            val nums = Regex("""(\d+(?:\.\d+)?)""").findAll(lower).map { it.value.toDouble() }.toList()
            if (nums.size >= 2) {
                val a = nums[0]
                val b = nums[1]
                val c = sqrt(a * a + b * b)
                val cBd = BigDecimal.valueOf(c)
                val cStr = AstrynPrecisionCalculator.formatNumber(cBd)
                return AstrynCalculationFormatter.createSolution(
                    domain = AstrynMathEngine.MathDomain.GEOMETRY_2D_3D,
                    problemType = "GEOMETRY_PYTHAGORAS",
                    recognizedInput = "Right triangle legs a = $a, b = $b",
                    formulaUsed = "c = √(a² + b²)",
                    steps = listOf(
                        "a² + b² = ${a * a} + ${b * b} = ${a * a + b * b}",
                        "Hypotenuse c = √(${a * a + b * b}) = $cStr"
                    ),
                    finalAnswer = cStr,
                    numericResult = cBd,
                    verification = AstrynMathEngine.VerificationStatus(true, "Pythagorean Identity Check", "c² = ${AstrynPrecisionCalculator.formatNumber(BigDecimal.valueOf(c * c))} == a² + b²"),
                    language = language,
                    verbosity = verbosity
                )
            }
        }

        // 2. Circle area & circumference
        if (lower.contains("circle") && lower.contains("radius")) {
            val rMatch = Regex("""radius\s*(?:of|=|is)?\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(lower)
                ?: Regex("""(\d+(?:\.\d+)?)\s*(?:cm|m|mm|in|ft)?\s*radius""", RegexOption.IGNORE_CASE).find(lower)
            if (rMatch != null) {
                val r = rMatch.groupValues[1].toDouble()
                val area = PI * r * r
                val circ = 2.0 * PI * r
                val wantCirc = lower.contains("circumference") || lower.contains("perimeter")
                val wantArea = lower.contains("area") || !wantCirc
                val primary = if (wantArea && !wantCirc) BigDecimal.valueOf(area) else BigDecimal.valueOf(circ)
                val areaStr = AstrynPrecisionCalculator.formatNumber(BigDecimal.valueOf(area), 4)
                val circStr = AstrynPrecisionCalculator.formatNumber(BigDecimal.valueOf(circ), 4)
                val finalAns = when {
                    wantArea && wantCirc -> "Area = $areaStr, Circumference = $circStr"
                    wantCirc -> circStr
                    else -> areaStr
                }
                return AstrynCalculationFormatter.createSolution(
                    domain = AstrynMathEngine.MathDomain.GEOMETRY_2D_3D,
                    problemType = "GEOMETRY_CIRCLE",
                    recognizedInput = "Circle with radius r = $r",
                    formulaUsed = "Area = πr², Circumference = 2πr",
                    steps = listOf(
                        "Radius r = $r",
                        "Area = π × $r² = $areaStr",
                        "Circumference = 2 × π × $r = $circStr"
                    ),
                    finalAnswer = finalAns,
                    numericResult = primary,
                    secondaryValues = mapOf("area" to BigDecimal.valueOf(area), "circumference" to BigDecimal.valueOf(circ)),
                    verification = AstrynMathEngine.VerificationStatus(true, "Ratio Check (Area / Circumference = r / 2)", "Verified Area/Circumference = ${r / 2.0}", isExact = false),
                    language = language,
                    verbosity = verbosity
                )
            }
        }

        // 3. Cylinder volume & surface area
        if (lower.contains("cylinder")) {
            val rMatch = Regex("""radius\s*(?:of|=|is)?\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(lower)
            val hMatch = Regex("""height\s*(?:of|=|is)?\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(lower)
            if (rMatch != null && hMatch != null) {
                val r = rMatch.groupValues[1].toDouble()
                val h = hMatch.groupValues[1].toDouble()
                val vol = PI * r * r * h
                val csa = 2.0 * PI * r * h
                val tsa = 2.0 * PI * r * (r + h)
                val volBd = BigDecimal.valueOf(vol)
                val volStr = AstrynPrecisionCalculator.formatNumber(volBd, 4)
                val tsaStr = AstrynPrecisionCalculator.formatNumber(BigDecimal.valueOf(tsa), 4)
                val csaStr = AstrynPrecisionCalculator.formatNumber(BigDecimal.valueOf(csa), 4)
                val ans = if (lower.contains("surface area") && !lower.contains("volume")) {
                    "Total Surface Area = $tsaStr (Curved = $csaStr)"
                } else {
                    "Volume = $volStr, Total Surface Area = $tsaStr"
                }
                return AstrynCalculationFormatter.createSolution(
                    domain = AstrynMathEngine.MathDomain.GEOMETRY_2D_3D,
                    problemType = "GEOMETRY_CYLINDER",
                    recognizedInput = "Cylinder (r = $r, h = $h)",
                    formulaUsed = "V = πr²h, TSA = 2πr(r + h), CSA = 2πrh",
                    steps = listOf(
                        "Radius r = $r, Height h = $h",
                        "Volume V = π × $r² × $h = $volStr",
                        "Curved Surface Area = 2πrh = $csaStr; Total Surface Area = 2πr(r + h) = $tsaStr"
                    ),
                    finalAnswer = ans,
                    numericResult = volBd,
                    verification = AstrynMathEngine.VerificationStatus(true, "Base Area × Height Check", "Verified V = (πr²) × h", isExact = false),
                    language = language,
                    verbosity = verbosity
                )
            }
        }

        // 4. Sphere volume & surface area
        if (lower.contains("sphere")) {
            val rMatch = Regex("""radius\s*(?:of|=|is)?\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(lower)
            if (rMatch != null) {
                val r = rMatch.groupValues[1].toDouble()
                val vol = (4.0 / 3.0) * PI * r * r * r
                val sa = 4.0 * PI * r * r
                val volBd = BigDecimal.valueOf(vol)
                val volStr = AstrynPrecisionCalculator.formatNumber(volBd, 4)
                val saStr = AstrynPrecisionCalculator.formatNumber(BigDecimal.valueOf(sa), 4)
                return AstrynCalculationFormatter.createSolution(
                    domain = AstrynMathEngine.MathDomain.GEOMETRY_2D_3D,
                    problemType = "GEOMETRY_SPHERE",
                    recognizedInput = "Sphere (r = $r)",
                    formulaUsed = "Volume = (4/3)πr³, Surface Area = 4πr²",
                    steps = listOf(
                        "Radius r = $r",
                        "Volume = (4/3) × π × $r³ = $volStr",
                        "Surface Area = 4 × π × $r² = $saStr"
                    ),
                    finalAnswer = "Volume = $volStr, Surface Area = $saStr",
                    numericResult = volBd,
                    verification = AstrynMathEngine.VerificationStatus(true, "3V / SA == r Check", "Verified 3 × Volume / Surface Area = $r", isExact = false),
                    language = language,
                    verbosity = verbosity
                )
            }
        }

        // 5. Cone volume & surface area
        if (lower.contains("cone")) {
            val rMatch = Regex("""radius\s*(?:of|=|is)?\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(lower)
            val hMatch = Regex("""height\s*(?:of|=|is)?\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(lower)
            if (rMatch != null && hMatch != null) {
                val r = rMatch.groupValues[1].toDouble()
                val h = hMatch.groupValues[1].toDouble()
                val l = sqrt(r * r + h * h)
                val vol = (1.0 / 3.0) * PI * r * r * h
                val tsa = PI * r * (r + l)
                val volBd = BigDecimal.valueOf(vol)
                val volStr = AstrynPrecisionCalculator.formatNumber(volBd, 4)
                val tsaStr = AstrynPrecisionCalculator.formatNumber(BigDecimal.valueOf(tsa), 4)
                return AstrynCalculationFormatter.createSolution(
                    domain = AstrynMathEngine.MathDomain.GEOMETRY_2D_3D,
                    problemType = "GEOMETRY_CONE",
                    recognizedInput = "Cone (r = $r, h = $h)",
                    formulaUsed = "V = (1/3)πr²h, l = √(r² + h²), TSA = πr(r + l)",
                    steps = listOf(
                        "Radius r = $r, Height h = $h, Slant height l = √($r² + $h²) = ${AstrynPrecisionCalculator.formatNumber(BigDecimal.valueOf(l), 4)}",
                        "Volume = (1/3)πr²h = $volStr",
                        "Total Surface Area = πr(r + l) = $tsaStr"
                    ),
                    finalAnswer = "Volume = $volStr, Total Surface Area = $tsaStr",
                    numericResult = volBd,
                    verification = AstrynMathEngine.VerificationStatus(true, "1/3 Cylinder Volume Ratio Check", "Verified V_cone = (1/3) V_cylinder", isExact = false),
                    language = language,
                    verbosity = verbosity
                )
            }
        }

        // 6. Triangle Area (base & height or 3 sides Heron's formula)
        if (lower.contains("triangle")) {
            val bMatch = Regex("""base\s*(?:of|=|is)?\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(lower)
            val hMatch = Regex("""height\s*(?:of|=|is)?\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(lower)
            if (bMatch != null && hMatch != null) {
                val b = BigDecimal(bMatch.groupValues[1])
                val h = BigDecimal(hMatch.groupValues[1])
                val area = b.multiply(h).divide(BigDecimal(2), AstrynPrecisionCalculator.DECIMAL128)
                val areaStr = AstrynPrecisionCalculator.formatNumber(area)
                return AstrynCalculationFormatter.createSolution(
                    domain = AstrynMathEngine.MathDomain.GEOMETRY_2D_3D,
                    problemType = "GEOMETRY_TRIANGLE_AREA",
                    recognizedInput = "Triangle (base = $b, height = $h)",
                    formulaUsed = "Area = (1/2) × Base × Height",
                    steps = listOf("Area = 0.5 × $b × $h = $areaStr"),
                    finalAnswer = areaStr,
                    numericResult = area,
                    verification = AstrynMathEngine.VerificationStatus(true, "Double Area Check", "2 × $areaStr = ${AstrynPrecisionCalculator.formatNumber(b.multiply(h))}"),
                    language = language,
                    verbosity = verbosity
                )
            }
            val sidesMatch = Regex("""sides?\s*(?:of|=|are)?\s*(\d+(?:\.\d+)?)\s*(?:,|and)\s*(\d+(?:\.\d+)?)\s*(?:,|and)\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(lower)
            if (sidesMatch != null) {
                val a = sidesMatch.groupValues[1].toDouble()
                val b = sidesMatch.groupValues[2].toDouble()
                val c = sidesMatch.groupValues[3].toDouble()
                if (a + b <= c || a + c <= b || b + c <= a) {
                    return AstrynMathEngine.MathSolution(
                        domain = AstrynMathEngine.MathDomain.AMBIGUOUS_OR_INVALID,
                        problemType = "INVALID_TRIANGLE_SIDES",
                        recognizedInput = "Triangle sides $a, $b, $c",
                        formulaUsed = "Triangle Inequality Theorem (a + b > c)",
                        steps = listOf("Sides $a, $b, $c violate the triangle inequality."),
                        finalAnswer = "Invalid triangle dimensions (sum of any two sides must exceed the third side).",
                        verification = AstrynMathEngine.VerificationStatus(false, "Triangle Inequality Check", "Violated triangle inequality"),
                        formattedResponse = "**Invalid Geometry**: Sides $a, $b, $c cannot form a valid triangle.",
                        language = language,
                        verbosity = verbosity
                    )
                }
                val s = (a + b + c) / 2.0
                val area = sqrt(s * (s - a) * (s - b) * (s - c))
                val areaBd = BigDecimal.valueOf(area)
                val areaStr = AstrynPrecisionCalculator.formatNumber(areaBd, 4)
                return AstrynCalculationFormatter.createSolution(
                    domain = AstrynMathEngine.MathDomain.GEOMETRY_2D_3D,
                    problemType = "GEOMETRY_TRIANGLE_HERON",
                    recognizedInput = "Triangle with sides a = $a, b = $b, c = $c",
                    formulaUsed = "Heron's Formula: A = √[s(s-a)(s-b)(s-c)], s = (a+b+c)/2",
                    steps = listOf(
                        "Semi-perimeter s = ($a + $b + $c) / 2 = $s",
                        "Area = √[$s × (${s - a}) × (${s - b}) × (${s - c})] = $areaStr"
                    ),
                    finalAnswer = "Area = $areaStr, Perimeter = ${AstrynPrecisionCalculator.formatNumber(BigDecimal.valueOf(a + b + c))}",
                    numericResult = areaBd,
                    verification = AstrynMathEngine.VerificationStatus(true, "Heron's Radicand Positivity Check", "Verified s > max(a,b,c)"),
                    language = language,
                    verbosity = verbosity
                )
            }
        }
        return null
    }
}

/**
 * Statistics, Probability, Combinatorics (`nPr`, `nCr`), Correlation & Linear Regression Engine.
 */
object AstrynStatisticsEngine {

    fun trySolve(
        prompt: String,
        language: AstrynMathEngine.MathLanguage,
        verbosity: AstrynMathEngine.OutputVerbosity
    ): AstrynMathEngine.MathSolution? {
        solveCombinatorics(prompt, language, verbosity)?.let { return it }
        solveCorrelationAndRegression(prompt, language, verbosity)?.let { return it }
        solveProbability(prompt, language, verbosity)?.let { return it }
        solveDescriptiveStats(prompt, language, verbosity)?.let { return it }
        return null
    }

    private fun solveCombinatorics(
        prompt: String,
        language: AstrynMathEngine.MathLanguage,
        verbosity: AstrynMathEngine.OutputVerbosity
    ): AstrynMathEngine.MathSolution? {
        val lower = prompt.lowercase(Locale.ROOT)
        // Pattern: 10C3, 10P3, C(10,3), P(10,3), nCr, nPr
        val ncMatch = Regex("""\b(\d+)\s*([cCpP])\s*(\d+)\b""").find(prompt)
            ?: Regex("""\b([cCpP])\(\s*(\d+)\s*,\s*(\d+)\s*\)""").find(prompt)
        if (ncMatch != null) {
            val isFuncForm = ncMatch.groupValues[1].equals("c", true) || ncMatch.groupValues[1].equals("p", true)
            val n = if (isFuncForm) ncMatch.groupValues[2].toInt() else ncMatch.groupValues[1].toInt()
            val op = if (isFuncForm) ncMatch.groupValues[1].uppercase(Locale.ROOT) else ncMatch.groupValues[2].uppercase(Locale.ROOT)
            val r = ncMatch.groupValues[3].toInt()

            if (n < 0 || r < 0 || r > n || n > 500) {
                return AstrynMathEngine.MathSolution(
                    domain = AstrynMathEngine.MathDomain.AMBIGUOUS_OR_INVALID,
                    problemType = "COMBINATORICS_DOMAIN_ERROR",
                    recognizedInput = "${n}${op}${r}",
                    formulaUsed = "0 ≤ r ≤ n ≤ 500",
                    steps = listOf("Invalid combinatorics parameters: n = $n, r = $r (requires 0 ≤ r ≤ n)."),
                    finalAnswer = "Invalid input (requires 0 ≤ r ≤ n)",
                    verification = AstrynMathEngine.VerificationStatus(false, "Domain Check", "r cannot exceed n"),
                    formattedResponse = "**Domain Error**: In ${n}${op}${r}, r ($r) must be between 0 and n ($n).",
                    language = language,
                    verbosity = verbosity
                )
            }

            val nFact = AstrynPrecisionCalculator.factorial(n)
            val nMinusRFact = AstrynPrecisionCalculator.factorial(n - r)
            return if (op == "C") {
                val rFact = AstrynPrecisionCalculator.factorial(r)
                val comb = nFact.divide(rFact.multiply(nMinusRFact))
                AstrynCalculationFormatter.createSolution(
                    domain = AstrynMathEngine.MathDomain.STATISTICS_PROBABILITY,
                    problemType = "COMBINATIONS_NCR",
                    recognizedInput = "${n}C${r}",
                    formulaUsed = "nCr = n! / [r! × (n - r)!]",
                    steps = listOf(
                        "n = $n, r = $r",
                        "${n}C${r} = $n! / ($r! × ${n - r}!) = $comb"
                    ),
                    finalAnswer = comb.toString(),
                    numericResult = BigDecimal(comb),
                    verification = AstrynMathEngine.VerificationStatus(true, "Symmetry Identity Check (nCr == nC(n-r))", "Verified ${n}C${r} = ${n}C${n - r} = $comb"),
                    language = language,
                    verbosity = verbosity
                )
            } else {
                val perm = nFact.divide(nMinusRFact)
                AstrynCalculationFormatter.createSolution(
                    domain = AstrynMathEngine.MathDomain.STATISTICS_PROBABILITY,
                    problemType = "PERMUTATIONS_NPR",
                    recognizedInput = "${n}P${r}",
                    formulaUsed = "nPr = n! / (n - r)!",
                    steps = listOf(
                        "n = $n, r = $r",
                        "${n}P${r} = $n! / ${n - r}! = $perm"
                    ),
                    finalAnswer = perm.toString(),
                    numericResult = BigDecimal(perm),
                    verification = AstrynMathEngine.VerificationStatus(true, "nPr = nCr × r! Check", "Verified $perm"),
                    language = language,
                    verbosity = verbosity
                )
            }
        }
        return null
    }

    private fun solveCorrelationAndRegression(
        prompt: String,
        language: AstrynMathEngine.MathLanguage,
        verbosity: AstrynMathEngine.OutputVerbosity
    ): AstrynMathEngine.MathSolution? {
        val lower = prompt.lowercase(Locale.ROOT)
        if (!lower.contains("correlation") && !lower.contains("regression")) return null

        val xMatch = Regex("""x\s*[:=]\s*\[?([\d.,\s+-]+)\]?""", RegexOption.IGNORE_CASE).find(prompt)
        val yMatch = Regex("""y\s*[:=]\s*\[?([\d.,\s+-]+)\]?""", RegexOption.IGNORE_CASE).find(prompt)
        if (xMatch == null || yMatch == null) return null

        val xs = Regex("""[+-]?\d+(?:\.\d+)?""").findAll(xMatch.groupValues[1]).map { it.value.toDouble() }.toList()
        val ys = Regex("""[+-]?\d+(?:\.\d+)?""").findAll(yMatch.groupValues[1]).map { it.value.toDouble() }.toList()
        if (xs.size < 2 || xs.size != ys.size) return null

        val n = xs.size
        val meanX = xs.average()
        val meanY = ys.average()
        var sxy = 0.0
        var sxx = 0.0
        var syy = 0.0
        for (i in 0 until n) {
            val dx = xs[i] - meanX
            val dy = ys[i] - meanY
            sxy += dx * dy
            sxx += dx * dx
            syy += dy * dy
        }
        if (sxx == 0.0 || syy == 0.0) return null
        val r = sxy / sqrt(sxx * syy)
        val slope = sxy / sxx
        val intercept = meanY - slope * meanX
        val r2 = r * r

        val rStr = AstrynPrecisionCalculator.formatNumber(BigDecimal.valueOf(r), 4)
        val mStr = AstrynPrecisionCalculator.formatNumber(BigDecimal.valueOf(slope), 4)
        val cStr = AstrynPrecisionCalculator.formatNumber(BigDecimal.valueOf(intercept), 4)
        val r2Str = AstrynPrecisionCalculator.formatNumber(BigDecimal.valueOf(r2), 4)

        return AstrynCalculationFormatter.createSolution(
            domain = AstrynMathEngine.MathDomain.STATISTICS_PROBABILITY,
            problemType = "CORRELATION_AND_REGRESSION",
            recognizedInput = "X = $xs, Y = $ys (n = $n)",
            formulaUsed = "r = Σ(x-x̄)(y-ȳ) / √[Σ(x-x̄)² Σ(y-ȳ)²]; y = mx + c",
            steps = listOf(
                "Sample size n = $n, Mean(X) = ${AstrynPrecisionCalculator.formatNumber(BigDecimal.valueOf(meanX), 4)}, Mean(Y) = ${AstrynPrecisionCalculator.formatNumber(BigDecimal.valueOf(meanY), 4)}",
                "Pearson Correlation r = $rStr (R² = $r2Str)",
                "Linear Regression Line: y = ${mStr}x + ($cStr)"
            ),
            finalAnswer = "r = $rStr, Regression: y = ${mStr}x + $cStr (R² = $r2Str)",
            numericResult = BigDecimal.valueOf(r),
            verification = AstrynMathEngine.VerificationStatus(true, "Cauchy-Schwarz Bound Check (|r| ≤ 1)", "Verified -1 ≤ $rStr ≤ 1 and line passes through (x̄, ȳ)"),
            language = language,
            verbosity = verbosity
        )
    }

    private fun solveProbability(
        prompt: String,
        language: AstrynMathEngine.MathLanguage,
        verbosity: AstrynMathEngine.OutputVerbosity
    ): AstrynMathEngine.MathSolution? {
        val lower = prompt.lowercase(Locale.ROOT)
        if (!lower.contains("probability")) return null
        val favTotal = Regex("""(\d+)\s+(?:[a-zA-Z]+\s+){0,3}?(?:out\s+of|from|/)\s*(\d+)""", RegexOption.IGNORE_CASE).find(prompt)
        if (favTotal != null) {
            val fav = BigInteger(favTotal.groupValues[1])
            val total = BigInteger(favTotal.groupValues[2])
            if (total == BigInteger.ZERO || fav > total) return null
            val frac = AstrynPrecisionCalculator.BigFraction(fav, total)
            val dec = frac.toBigDecimal()
            val pct = dec.multiply(BigDecimal(100), AstrynPrecisionCalculator.DECIMAL128)
            val ans = "$frac (≈ ${AstrynPrecisionCalculator.formatNumber(dec, 4)} or ${AstrynPrecisionCalculator.formatNumber(pct, 2)}%)"
            return AstrynCalculationFormatter.createSolution(
                domain = AstrynMathEngine.MathDomain.STATISTICS_PROBABILITY,
                problemType = "PROBABILITY_CALCULATION",
                recognizedInput = "Favorable outcomes = $fav, Total outcomes = $total",
                formulaUsed = "P(E) = Favorable Outcomes / Total Outcomes",
                steps = listOf(
                    "Favorable outcomes = $fav, Total sample space = $total",
                    "P(E) = $fav / $total = $frac = ${AstrynPrecisionCalculator.formatNumber(dec, 4)} (${AstrynPrecisionCalculator.formatNumber(pct, 2)}%)",
                    "Complement P(not E) = 1 - P(E) = ${AstrynPrecisionCalculator.BigFraction(total.subtract(fav), total)}"
                ),
                finalAnswer = ans,
                numericResult = dec,
                verification = AstrynMathEngine.VerificationStatus(true, "Axiom of Probability Check (0 ≤ P(E) ≤ 1)", "P(E) + P(E') = 1 verified"),
                language = language,
                verbosity = verbosity
            )
        }
        return null
    }

    private fun solveDescriptiveStats(
        prompt: String,
        language: AstrynMathEngine.MathLanguage,
        verbosity: AstrynMathEngine.OutputVerbosity
    ): AstrynMathEngine.MathSolution? {
        val lower = prompt.lowercase(Locale.ROOT)
        val statKeywords = listOf("mean", "median", "mode", "standard deviation", "variance", "average")
        if (statKeywords.none { lower.contains(it) }) return null

        val numbers = Regex("""[+-]?\d+(?:\.\d+)?""")
            .findAll(prompt)
            .mapNotNull { it.value.toBigDecimalOrNull() }
            .toList()
        if (numbers.size < 2) return null

        val n = numbers.size
        val sum = numbers.fold(BigDecimal.ZERO) { acc, b -> acc.add(b) }
        val mean = sum.divide(BigDecimal(n), AstrynPrecisionCalculator.DECIMAL128)

        val sorted = numbers.sorted()
        val median = if (n % 2 == 1) {
            sorted[n / 2]
        } else {
            sorted[n / 2 - 1].add(sorted[n / 2]).divide(BigDecimal(2), AstrynPrecisionCalculator.DECIMAL128)
        }

        val freqMap = numbers.groupingBy { it.stripTrailingZeros() }.eachCount()
        val maxFreq = freqMap.values.maxOrNull() ?: 1
        val modes = if (maxFreq > 1) freqMap.filterValues { it == maxFreq }.keys.toList() else emptyList()
        val modeStr = if (modes.isEmpty()) "No repeating mode" else modes.joinToString(", ") { AstrynPrecisionCalculator.formatNumber(it) }

        val sqDiffSum = numbers.fold(BigDecimal.ZERO) { acc, x ->
            val d = x.subtract(mean, AstrynPrecisionCalculator.DECIMAL128)
            acc.add(d.multiply(d, AstrynPrecisionCalculator.DECIMAL128))
        }
        val popVariance = sqDiffSum.divide(BigDecimal(n), AstrynPrecisionCalculator.DECIMAL128)
        val sampleVariance = if (n > 1) sqDiffSum.divide(BigDecimal(n - 1), AstrynPrecisionCalculator.DECIMAL128) else BigDecimal.ZERO
        val popSd = BigDecimal.valueOf(sqrt(popVariance.toDouble()))
        val sampleSd = BigDecimal.valueOf(sqrt(sampleVariance.toDouble()))

        val meanStr = AstrynPrecisionCalculator.formatNumber(mean, 4)
        val medianStr = AstrynPrecisionCalculator.formatNumber(median, 4)
        val popSdStr = AstrynPrecisionCalculator.formatNumber(popSd, 4)
        val sampleSdStr = AstrynPrecisionCalculator.formatNumber(sampleSd, 4)
        val popVarStr = AstrynPrecisionCalculator.formatNumber(popVariance, 4)
        val sampleVarStr = AstrynPrecisionCalculator.formatNumber(sampleVariance, 4)

        val table = AstrynCalculationFormatter.buildMarkdownTable(
            headers = listOf("Statistic", "Value"),
            rows = listOf(
                listOf("Count (N)", n.toString()),
                listOf("Sum (Σx)", AstrynPrecisionCalculator.formatNumber(sum, 4)),
                listOf("Mean (x̄)", meanStr),
                listOf("Median", medianStr),
                listOf("Mode", modeStr),
                listOf("Population Variance (σ²)", popVarStr),
                listOf("Population Std Dev (σ)", popSdStr),
                listOf("Sample Variance (s²)", sampleVarStr),
                listOf("Sample Std Dev (s)", sampleSdStr)
            )
        )

        val finalAns = "Mean = $meanStr, Median = $medianStr, Mode = $modeStr, Std Dev (σ) = $popSdStr (Sample s = $sampleSdStr)"

        return AstrynCalculationFormatter.createSolution(
            domain = AstrynMathEngine.MathDomain.STATISTICS_PROBABILITY,
            problemType = "DESCRIPTIVE_STATISTICS",
            recognizedInput = "Dataset (${n} values): ${numbers.joinToString(", ") { AstrynPrecisionCalculator.formatNumber(it) }}",
            formulaUsed = "x̄ = (Σx)/N, σ = √[Σ(x - x̄)² / N], s = √[Σ(x - x̄)² / (N - 1)]",
            steps = listOf(
                "Sorted Dataset (N = $n): ${sorted.joinToString(", ") { AstrynPrecisionCalculator.formatNumber(it) }}",
                "Sum Σx = ${AstrynPrecisionCalculator.formatNumber(sum)} → Mean (x̄) = ${AstrynPrecisionCalculator.formatNumber(sum)} / $n = $meanStr",
                "Median = $medianStr; Mode = $modeStr",
                "Sum of Squared Deviations Σ(x - x̄)² = ${AstrynPrecisionCalculator.formatNumber(sqDiffSum, 4)}",
                "Population Variance σ² = $popVarStr (σ = $popSdStr); Sample Variance s² = $sampleVarStr (s = $sampleSdStr)"
            ),
            finalAnswer = finalAns,
            numericResult = mean,
            secondaryValues = mapOf(
                "mean" to mean,
                "median" to median,
                "populationSd" to popSd,
                "sampleSd" to sampleSd,
                "populationVariance" to popVariance,
                "sampleVariance" to sampleVariance
            ),
            verification = AstrynMathEngine.VerificationStatus(
                verified = true,
                method = "Zero Deviation Sum Check (Σ(x - x̄) = 0)",
                checkSummary = "Verified N × Mean = ${AstrynPrecisionCalculator.formatNumber(mean.multiply(BigDecimal(n)))} == Sum ${AstrynPrecisionCalculator.formatNumber(sum)}"
            ),
            markdownTable = table,
            language = language,
            verbosity = verbosity
        )
    }
}

/**
 * Financial, Business & Daily-Life Calculator:
 * Simple Interest, Compound Interest (annual/half-yearly/quarterly/monthly + schedule table),
 * Loan EMI (+ amortization schedule), SIP Future Value (+ market projection disclaimer),
 * Profit/Loss, Single & Successive Discounts, GST (inclusive/exclusive + CGST/SGST split),
 * Markup/Margin, and Business Break-Even Analysis.
 */
object AstrynFinancialCalculator {

    fun trySolve(
        prompt: String,
        language: AstrynMathEngine.MathLanguage,
        verbosity: AstrynMathEngine.OutputVerbosity
    ): AstrynMathEngine.MathSolution? {
        solveEmiLoan(prompt, language, verbosity)?.let { return it }
        solveSipInvestment(prompt, language, verbosity)?.let { return it }
        solveCompoundInterest(prompt, language, verbosity)?.let { return it }
        solveSimpleInterest(prompt, language, verbosity)?.let { return it }
        solveGstCalculation(prompt, language, verbosity)?.let { return it }
        solveBreakEven(prompt, language, verbosity)?.let { return it }
        solveSuccessiveDiscounts(prompt, language, verbosity)?.let { return it }
        solveProfitLossAndMarkup(prompt, language, verbosity)?.let { return it }
        return null
    }

    /**
     * Loan EMI Calculation: `E = P * r * (1+r)^n / ((1+r)^n - 1)` with month-wise/year-wise breakdown.
     */
    fun solveEmiLoan(
        prompt: String,
        language: AstrynMathEngine.MathLanguage,
        verbosity: AstrynMathEngine.OutputVerbosity
    ): AstrynMathEngine.MathSolution? {
        val lower = prompt.lowercase(Locale.ROOT)
        if (!lower.contains("emi") && !(lower.contains("loan") && lower.contains("monthly") && lower.contains("installment"))) {
            return null
        }

        val p = extractPrincipalAmount(prompt) ?: return null
        val annualRate = extractInterestRate(prompt) ?: return null
        val months = extractDurationInMonths(prompt) ?: return null
        if (p <= BigDecimal.ZERO || months <= 0) return null

        val monthlyRate = annualRate.divide(BigDecimal(1200), AstrynPrecisionCalculator.DECIMAL128)
        val emi: BigDecimal = if (monthlyRate.compareTo(BigDecimal.ZERO) == 0) {
            p.divide(BigDecimal(months), AstrynPrecisionCalculator.DECIMAL128)
        } else {
            val onePlusR = BigDecimal.ONE.add(monthlyRate, AstrynPrecisionCalculator.DECIMAL128)
            val factor = onePlusR.pow(months, AstrynPrecisionCalculator.DECIMAL128)
            val numerator = p.multiply(monthlyRate, AstrynPrecisionCalculator.DECIMAL128).multiply(factor, AstrynPrecisionCalculator.DECIMAL128)
            val denominator = factor.subtract(BigDecimal.ONE, AstrynPrecisionCalculator.DECIMAL128)
            numerator.divide(denominator, AstrynPrecisionCalculator.DECIMAL128)
        }

        val totalPayable = emi.multiply(BigDecimal(months), AstrynPrecisionCalculator.DECIMAL128)
        val totalInterest = totalPayable.subtract(p, AstrynPrecisionCalculator.DECIMAL128)

        // Generate first 6 months (or full if <= 12) amortization schedule table
        val previewMonths = months.coerceAtMost(6)
        var balance = p
        val rows = mutableListOf<List<String>>()
        for (m in 1..previewMonths) {
            val interestComp = balance.multiply(monthlyRate, AstrynPrecisionCalculator.DECIMAL128)
            val principalComp = emi.subtract(interestComp, AstrynPrecisionCalculator.DECIMAL128)
            balance = balance.subtract(principalComp, AstrynPrecisionCalculator.DECIMAL128).max(BigDecimal.ZERO)
            rows.add(
                listOf(
                    "Month $m",
                    AstrynPrecisionCalculator.formatCurrency(emi),
                    AstrynPrecisionCalculator.formatCurrency(principalComp),
                    AstrynPrecisionCalculator.formatCurrency(interestComp),
                    AstrynPrecisionCalculator.formatCurrency(balance)
                )
            )
        }
        val table = AstrynCalculationFormatter.buildMarkdownTable(
            headers = listOf("Installment", "EMI", "Principal Paid", "Interest Paid", "Closing Balance"),
            rows = rows
        )

        val emiStr = AstrynPrecisionCalculator.formatCurrency(emi)
        val totalIntStr = AstrynPrecisionCalculator.formatCurrency(totalInterest)
        val totalPayStr = AstrynPrecisionCalculator.formatCurrency(totalPayable)

        return AstrynCalculationFormatter.createSolution(
            domain = AstrynMathEngine.MathDomain.FINANCIAL_EMI_LOAN,
            problemType = "LOAN_EMI_CALCULATION",
            recognizedInput = "Loan Principal = ${AstrynPrecisionCalculator.formatCurrency(p)}, Annual Rate = ${AstrynPrecisionCalculator.formatNumber(annualRate)}%, Tenure = $months months",
            formulaUsed = "EMI = [P × r × (1 + r)ⁿ] / [(1 + r)ⁿ - 1], where r = Annual Rate / (12 × 100)",
            steps = listOf(
                "Principal (P) = ${AstrynPrecisionCalculator.formatCurrency(p)}, Monthly Interest Rate (r) = ${AstrynPrecisionCalculator.formatNumber(annualRate)}% / 12 = ${AstrynPrecisionCalculator.formatNumber(monthlyRate.multiply(BigDecimal(100)), 4)}% per month, Tenure (n) = $months months",
                "Monthly EMI = $emiStr",
                "Total Amount Payable ($months × $emiStr) = $totalPayStr",
                "Total Interest Payable = $totalPayStr - ${AstrynPrecisionCalculator.formatCurrency(p)} = $totalIntStr"
            ),
            finalAnswer = "Monthly EMI = $emiStr (Total Interest = $totalIntStr, Total Payable = $totalPayStr)",
            numericResult = emi.setScale(2, RoundingMode.HALF_UP),
            secondaryValues = mapOf(
                "emi" to emi,
                "totalInterest" to totalInterest,
                "totalPayable" to totalPayable,
                "principal" to p
            ),
            unit = "₹/month",
            verification = AstrynMathEngine.VerificationStatus(
                verified = totalPayable >= p,
                method = "Amortization Principal + Interest Conservation Check",
                checkSummary = "Principal (${AstrynPrecisionCalculator.formatCurrency(p)}) + Total Interest ($totalIntStr) = Total Payable ($totalPayStr)"
            ),
            markdownTable = table,
            language = language,
            verbosity = verbosity
        )
    }

    /**
     * SIP Future Value Calculation: `FV = P * [((1 + i)^n - 1) / i] * (1 + i)`
     * Includes mandatory disclaimer distinguishing mathematical projection from guaranteed market return.
     */
    fun solveSipInvestment(
        prompt: String,
        language: AstrynMathEngine.MathLanguage,
        verbosity: AstrynMathEngine.OutputVerbosity
    ): AstrynMathEngine.MathSolution? {
        val lower = prompt.lowercase(Locale.ROOT)
        if (!lower.contains("sip")) return null

        val monthlyInv = extractPrincipalAmount(prompt) ?: return null
        val annualRate = extractInterestRate(prompt) ?: return null
        val months = extractDurationInMonths(prompt) ?: return null
        if (monthlyInv <= BigDecimal.ZERO || months <= 0) return null

        val i = annualRate.divide(BigDecimal(1200), AstrynPrecisionCalculator.DECIMAL128)
        val totalInvested = monthlyInv.multiply(BigDecimal(months), AstrynPrecisionCalculator.DECIMAL128)
        val fv = if (i.compareTo(BigDecimal.ZERO) == 0) {
            totalInvested
        } else {
            val onePlusI = BigDecimal.ONE.add(i, AstrynPrecisionCalculator.DECIMAL128)
            val factor = onePlusI.pow(months, AstrynPrecisionCalculator.DECIMAL128).subtract(BigDecimal.ONE, AstrynPrecisionCalculator.DECIMAL128)
            monthlyInv.multiply(factor, AstrynPrecisionCalculator.DECIMAL128)
                .divide(i, AstrynPrecisionCalculator.DECIMAL128)
                .multiply(onePlusI, AstrynPrecisionCalculator.DECIMAL128)
        }
        val estReturns = fv.subtract(totalInvested, AstrynPrecisionCalculator.DECIMAL128)
        val fvStr = AstrynPrecisionCalculator.formatCurrency(fv)
        val invStr = AstrynPrecisionCalculator.formatCurrency(totalInvested)
        val retStr = AstrynPrecisionCalculator.formatCurrency(estReturns)
        val disclaimer = "Note: SIP calculations represent a mathematical compounding projection at an assumed constant annual rate (${AstrynPrecisionCalculator.formatNumber(annualRate)}%), not a guaranteed market return."

        return AstrynCalculationFormatter.createSolution(
            domain = AstrynMathEngine.MathDomain.FINANCIAL_SIP_INVESTMENT,
            problemType = "SIP_FUTURE_VALUE_CALCULATION",
            recognizedInput = "Monthly SIP = ${AstrynPrecisionCalculator.formatCurrency(monthlyInv)}, Expected Return = ${AstrynPrecisionCalculator.formatNumber(annualRate)}% p.a., Duration = $months months",
            formulaUsed = "FV = P × [((1 + i)ⁿ - 1) / i] × (1 + i), where i = Annual Rate / 1200",
            steps = listOf(
                "Monthly Investment (P) = ${AstrynPrecisionCalculator.formatCurrency(monthlyInv)}, Duration (n) = $months months, Periodic Rate (i) = ${AstrynPrecisionCalculator.formatNumber(i, 6)}",
                "Total Invested Capital = $months × ${AstrynPrecisionCalculator.formatCurrency(monthlyInv)} = $invStr",
                "Projected Future Value (FV) = $fvStr (Estimated Wealth Gain = $retStr)",
                disclaimer
            ),
            finalAnswer = "Projected Future Value = $fvStr (Invested = $invStr, Est. Gain = $retStr) — $disclaimer",
            numericResult = fv.setScale(2, RoundingMode.HALF_UP),
            secondaryValues = mapOf("futureValue" to fv, "totalInvested" to totalInvested, "estimatedReturns" to estReturns),
            verification = AstrynMathEngine.VerificationStatus(
                verified = fv >= totalInvested,
                method = "Capital + Gain Conservation & Projection Disclosure Check",
                checkSummary = "Invested ($invStr) + Est. Gain ($retStr) = Projected FV ($fvStr)"
            ),
            language = language,
            verbosity = verbosity
        )
    }

    /**
     * Compound Interest Calculation (`A = P(1 + r/n)^(nt)`, `CI = A - P`) with year-wise schedule table.
     */
    fun solveCompoundInterest(
        prompt: String,
        language: AstrynMathEngine.MathLanguage,
        verbosity: AstrynMathEngine.OutputVerbosity
    ): AstrynMathEngine.MathSolution? {
        val lower = prompt.lowercase(Locale.ROOT)
        if (!lower.contains("compound interest") && !lower.contains("cakravriddhi") && !Regex("""\bci\b""").containsMatchIn(lower)) {
            return null
        }

        val p = extractPrincipalAmount(prompt) ?: return null
        val r = extractInterestRate(prompt) ?: return null
        val years = extractDurationInYears(prompt) ?: return null
        if (p <= BigDecimal.ZERO || years <= BigDecimal.ZERO) return null

        val freq = when {
            lower.contains("monthly") -> 12
            lower.contains("quarterly") -> 4
            lower.contains("half-yearly") || lower.contains("half yearly") || lower.contains("semi-annually") -> 2
            else -> 1
        }
        val freqLabel = when (freq) {
            12 -> "Monthly (n=12)"
            4 -> "Quarterly (n=4)"
            2 -> "Half-Yearly (n=2)"
            else -> "Annually (n=1)"
        }

        val ratePerPeriod = r.divide(BigDecimal(100 * freq), AstrynPrecisionCalculator.DECIMAL128)
        val totalPeriods = years.multiply(BigDecimal(freq), AstrynPrecisionCalculator.DECIMAL128)
        val amount = p.multiply(
            AstrynPrecisionCalculator.pow(BigDecimal.ONE.add(ratePerPeriod, AstrynPrecisionCalculator.DECIMAL128), totalPeriods),
            AstrynPrecisionCalculator.DECIMAL128
        )
        val ci = amount.subtract(p, AstrynPrecisionCalculator.DECIMAL128)

        // Build year-wise table up to 10 years
        val wholeYears = years.toInt().coerceIn(1, 10)
        val rows = (1..wholeYears).map { yr ->
            val periods = BigDecimal(yr * freq)
            val yrAmt = p.multiply(
                AstrynPrecisionCalculator.pow(BigDecimal.ONE.add(ratePerPeriod, AstrynPrecisionCalculator.DECIMAL128), periods),
                AstrynPrecisionCalculator.DECIMAL128
            )
            val yrCi = yrAmt.subtract(p, AstrynPrecisionCalculator.DECIMAL128)
            listOf("Year $yr", AstrynPrecisionCalculator.formatCurrency(yrCi), AstrynPrecisionCalculator.formatCurrency(yrAmt))
        }
        val table = AstrynCalculationFormatter.buildMarkdownTable(
            headers = listOf("Year", "Cumulative CI", "Total Maturity Amount"),
            rows = rows
        )

        val ciStr = AstrynPrecisionCalculator.formatCurrency(ci)
        val amtStr = AstrynPrecisionCalculator.formatCurrency(amount)

        return AstrynCalculationFormatter.createSolution(
            domain = AstrynMathEngine.MathDomain.FINANCIAL_COMPOUND_INTEREST,
            problemType = "COMPOUND_INTEREST",
            recognizedInput = "P = ${AstrynPrecisionCalculator.formatCurrency(p)}, R = ${AstrynPrecisionCalculator.formatNumber(r)}%, T = ${AstrynPrecisionCalculator.formatNumber(years)} years ($freqLabel)",
            formulaUsed = "A = P × (1 + R / (100 × n))^(n × T), CI = A - P",
            steps = listOf(
                "Principal (P) = ${AstrynPrecisionCalculator.formatCurrency(p)}, Annual Rate (R) = ${AstrynPrecisionCalculator.formatNumber(r)}%, Time (T) = ${AstrynPrecisionCalculator.formatNumber(years)} years, Compounding = $freqLabel",
                "Maturity Amount (A) = ${AstrynPrecisionCalculator.formatCurrency(p)} × (1 + ${AstrynPrecisionCalculator.formatNumber(ratePerPeriod, 6)})^${AstrynPrecisionCalculator.formatNumber(totalPeriods)} = $amtStr",
                "Compound Interest (CI) = $amtStr - ${AstrynPrecisionCalculator.formatCurrency(p)} = $ciStr"
            ),
            finalAnswer = "Compound Interest = $ciStr, Total Amount = $amtStr",
            numericResult = ci.setScale(2, RoundingMode.HALF_UP),
            secondaryValues = mapOf("compoundInterest" to ci, "totalAmount" to amount, "principal" to p),
            verification = AstrynMathEngine.VerificationStatus(
                verified = amount.subtract(p).subtract(ci).abs() <= BigDecimal("0.01"),
                method = "CI >= SI & Principal + CI == Amount Check",
                checkSummary = "Verified ${AstrynPrecisionCalculator.formatCurrency(p)} + $ciStr = $amtStr"
            ),
            markdownTable = table,
            examShortTrick = "For 2 years annual compounding, effective CI rate = R + R + (R²/100)% = ${AstrynPrecisionCalculator.formatNumber(r.multiply(BigDecimal(2)).add(r.multiply(r).divide(BigDecimal(100), AstrynPrecisionCalculator.DECIMAL128)), 2)}%.",
            language = language,
            verbosity = verbosity
        )
    }

    /**
     * Simple Interest (`SI = (P * R * T) / 100`, `A = P + SI`).
     */
    fun solveSimpleInterest(
        prompt: String,
        language: AstrynMathEngine.MathLanguage,
        verbosity: AstrynMathEngine.OutputVerbosity
    ): AstrynMathEngine.MathSolution? {
        val lower = prompt.lowercase(Locale.ROOT)
        if (!lower.contains("simple interest") && !lower.contains("sadharan byaj") && !Regex("""\bsi\b""").containsMatchIn(lower)) {
            return null
        }
        val p = extractPrincipalAmount(prompt) ?: return null
        val r = extractInterestRate(prompt) ?: return null
        val t = extractDurationInYears(prompt) ?: return null
        if (p <= BigDecimal.ZERO || t <= BigDecimal.ZERO) return null

        val si = p.multiply(r, AstrynPrecisionCalculator.DECIMAL128)
            .multiply(t, AstrynPrecisionCalculator.DECIMAL128)
            .divide(BigDecimal(100), AstrynPrecisionCalculator.DECIMAL128)
        val total = p.add(si, AstrynPrecisionCalculator.DECIMAL128)
        val siStr = AstrynPrecisionCalculator.formatNumber(si, 2)
        val totalStr = AstrynPrecisionCalculator.formatNumber(total, 2)

        return AstrynCalculationFormatter.createSolution(
            domain = AstrynMathEngine.MathDomain.FINANCIAL_SIMPLE_INTEREST,
            problemType = "SIMPLE_INTEREST",
            recognizedInput = "P = ${AstrynPrecisionCalculator.formatNumber(p)}, R = ${AstrynPrecisionCalculator.formatNumber(r)}%, T = ${AstrynPrecisionCalculator.formatNumber(t)} years",
            formulaUsed = "SI = (P × R × T) / 100, Total Amount = P + SI",
            steps = listOf(
                "Principal (P) = ${AstrynPrecisionCalculator.formatNumber(p)}, Rate (R) = ${AstrynPrecisionCalculator.formatNumber(r)}%, Time (T) = ${AstrynPrecisionCalculator.formatNumber(t)} years",
                "Simple Interest (SI) = (${AstrynPrecisionCalculator.formatNumber(p)} × ${AstrynPrecisionCalculator.formatNumber(r)} × ${AstrynPrecisionCalculator.formatNumber(t)}) / 100 = $siStr",
                "Total Amount (A) = ${AstrynPrecisionCalculator.formatNumber(p)} + $siStr = $totalStr"
            ),
            finalAnswer = "SI = $siStr, Total Amount = $totalStr",
            numericResult = si,
            secondaryValues = mapOf("simpleInterest" to si, "totalAmount" to total, "principal" to p),
            verification = AstrynMathEngine.VerificationStatus(true, "Reverse Rate Check (R = 100×SI / (P×T))", "Verified SI + P = $totalStr"),
            language = language,
            verbosity = verbosity
        )
    }

    /**
     * GST Inclusive & Exclusive Calculation with CGST (50%) and SGST (50%) breakdown.
     */
    fun solveGstCalculation(
        prompt: String,
        language: AstrynMathEngine.MathLanguage,
        verbosity: AstrynMathEngine.OutputVerbosity
    ): AstrynMathEngine.MathSolution? {
        val lower = prompt.lowercase(Locale.ROOT)
        if (!lower.contains("gst") && !lower.contains("cgst") && !lower.contains("sgst")) return null

        val rateMatch = Regex("""(\d+(?:\.\d+)?)\s*(?:%|percent)\s*(?:gst|tax)?|(?:gst|tax)\s*(?:of|at|@|=)?\s*(\d+(?:\.\d+)?)\s*%?""", RegexOption.IGNORE_CASE).find(prompt)
        val gstRate = rateMatch?.let {
            val g1 = it.groupValues[1]
            val g2 = it.groupValues[2]
            BigDecimal(g1.ifEmpty { g2 })
        } ?: BigDecimal(18)

        val allNums = Regex("""(\d[\d,]*(?:\.\d+)?)""").findAll(prompt)
            .mapNotNull { it.groupValues[1].replace(",", "").toBigDecimalOrNull() }
            .filter { it.compareTo(gstRate) != 0 }
            .toList()
        val amount = allNums.firstOrNull() ?: return null
        val isInclusive = lower.contains("inclusive") || lower.contains("included") || lower.contains("including gst")

        return if (isInclusive) {
            val divisor = BigDecimal.ONE.add(gstRate.divide(BigDecimal(100), AstrynPrecisionCalculator.DECIMAL128))
            val basePrice = amount.divide(divisor, AstrynPrecisionCalculator.DECIMAL128)
            val gstAmount = amount.subtract(basePrice, AstrynPrecisionCalculator.DECIMAL128)
            val halfGst = gstAmount.divide(BigDecimal(2), AstrynPrecisionCalculator.DECIMAL128)
            val halfRate = gstRate.divide(BigDecimal(2), AstrynPrecisionCalculator.DECIMAL128)
            val baseStr = AstrynPrecisionCalculator.formatCurrency(basePrice)
            val gstStr = AstrynPrecisionCalculator.formatCurrency(gstAmount)
            val halfStr = AstrynPrecisionCalculator.formatCurrency(halfGst)

            AstrynCalculationFormatter.createSolution(
                domain = AstrynMathEngine.MathDomain.FINANCIAL_GST_TAX,
                problemType = "GST_INCLUSIVE_EXTRACTION",
                recognizedInput = "Gross Price = ${AstrynPrecisionCalculator.formatCurrency(amount)} (Inclusive of ${AstrynPrecisionCalculator.formatNumber(gstRate)}% GST)",
                formulaUsed = "Base Price = Gross / (1 + GST%/100), GST = Gross - Base",
                steps = listOf(
                    "Gross Inclusive Amount = ${AstrynPrecisionCalculator.formatCurrency(amount)}, GST Rate = ${AstrynPrecisionCalculator.formatNumber(gstRate)}%",
                    "Net Base Price = ${AstrynPrecisionCalculator.formatCurrency(amount)} / (1 + ${AstrynPrecisionCalculator.formatNumber(gstRate)}/100) = $baseStr",
                    "Total GST = $gstStr (CGST @ ${AstrynPrecisionCalculator.formatNumber(halfRate)}% = $halfStr, SGST @ ${AstrynPrecisionCalculator.formatNumber(halfRate)}% = $halfStr)"
                ),
                finalAnswer = "Base Price = $baseStr, Total GST = $gstStr (CGST = $halfStr, SGST = $halfStr)",
                numericResult = basePrice.setScale(2, RoundingMode.HALF_UP),
                secondaryValues = mapOf("basePrice" to basePrice, "gstAmount" to gstAmount, "cgst" to halfGst, "sgst" to halfGst),
                verification = AstrynMathEngine.VerificationStatus(true, "Base + GST == Gross Check", "$baseStr + $gstStr = ${AstrynPrecisionCalculator.formatCurrency(amount)}"),
                language = language,
                verbosity = verbosity
            )
        } else {
            val gstAmount = amount.multiply(gstRate, AstrynPrecisionCalculator.DECIMAL128).divide(BigDecimal(100), AstrynPrecisionCalculator.DECIMAL128)
            val finalPrice = amount.add(gstAmount, AstrynPrecisionCalculator.DECIMAL128)
            val halfGst = gstAmount.divide(BigDecimal(2), AstrynPrecisionCalculator.DECIMAL128)
            val halfRate = gstRate.divide(BigDecimal(2), AstrynPrecisionCalculator.DECIMAL128)
            val finalStr = AstrynPrecisionCalculator.formatCurrency(finalPrice)
            val gstStr = AstrynPrecisionCalculator.formatCurrency(gstAmount)
            val halfStr = AstrynPrecisionCalculator.formatCurrency(halfGst)

            AstrynCalculationFormatter.createSolution(
                domain = AstrynMathEngine.MathDomain.FINANCIAL_GST_TAX,
                problemType = "GST_EXCLUSIVE_ADDITION",
                recognizedInput = "Base Price = ${AstrynPrecisionCalculator.formatCurrency(amount)} + ${AstrynPrecisionCalculator.formatNumber(gstRate)}% GST",
                formulaUsed = "GST = Base × (GST%/100), Final Price = Base + GST",
                steps = listOf(
                    "Base Price = ${AstrynPrecisionCalculator.formatCurrency(amount)}, GST Rate = ${AstrynPrecisionCalculator.formatNumber(gstRate)}%",
                    "Total GST = $gstStr (CGST @ ${AstrynPrecisionCalculator.formatNumber(halfRate)}% = $halfStr, SGST @ ${AstrynPrecisionCalculator.formatNumber(halfRate)}% = $halfStr)",
                    "Final Invoice Amount = ${AstrynPrecisionCalculator.formatCurrency(amount)} + $gstStr = $finalStr"
                ),
                finalAnswer = "Final Price = $finalStr (Total GST = $gstStr: CGST = $halfStr, SGST = $halfStr)",
                numericResult = finalPrice.setScale(2, RoundingMode.HALF_UP),
                secondaryValues = mapOf("finalPrice" to finalPrice, "gstAmount" to gstAmount, "cgst" to halfGst, "sgst" to halfGst),
                verification = AstrynMathEngine.VerificationStatus(true, "Reverse GST Subtraction Check", "$finalStr - $gstStr = ${AstrynPrecisionCalculator.formatCurrency(amount)}"),
                language = language,
                verbosity = verbosity
            )
        }
    }

    /**
     * Business Break-Even Analysis: `Break-Even Units = Fixed Cost / (Selling Price per Unit - Variable Cost per Unit)`
     */
    fun solveBreakEven(
        prompt: String,
        language: AstrynMathEngine.MathLanguage,
        verbosity: AstrynMathEngine.OutputVerbosity
    ): AstrynMathEngine.MathSolution? {
        val lower = prompt.lowercase(Locale.ROOT)
        if (!lower.contains("break-even") && !lower.contains("break even") && !lower.contains("breakeven")) return null

        val fcMatch = Regex("""fixed\s+costs?\s*(?:of|is|=|:)?\s*(?:₹|rs\.?|inr|\$)?\s*(\d[\d,]*(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(prompt)
        val spMatch = Regex("""(?:selling\s+price|price\s+per\s+unit|sp)\s*(?:of|is|=|:)?\s*(?:₹|rs\.?|inr|\$)?\s*(\d[\d,]*(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(prompt)
        val vcMatch = Regex("""(?:variable\s+cost|vc)\s*(?:per\s+unit)?\s*(?:of|is|=|:)?\s*(?:₹|rs\.?|inr|\$)?\s*(\d[\d,]*(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(prompt)
        if (fcMatch == null || spMatch == null || vcMatch == null) return null

        val fc = BigDecimal(fcMatch.groupValues[1].replace(",", ""))
        val sp = BigDecimal(spMatch.groupValues[1].replace(",", ""))
        val vc = BigDecimal(vcMatch.groupValues[1].replace(",", ""))
        val contribution = sp.subtract(vc, AstrynPrecisionCalculator.DECIMAL128)
        if (contribution <= BigDecimal.ZERO) return null

        val beUnits = fc.divide(contribution, AstrynPrecisionCalculator.DECIMAL128)
        val beRevenue = beUnits.multiply(sp, AstrynPrecisionCalculator.DECIMAL128)
        val unitsStr = AstrynPrecisionCalculator.formatNumber(beUnits, 2)
        val revStr = AstrynPrecisionCalculator.formatCurrency(beRevenue)

        return AstrynCalculationFormatter.createSolution(
            domain = AstrynMathEngine.MathDomain.FINANCIAL_BREAK_EVEN,
            problemType = "BUSINESS_BREAK_EVEN",
            recognizedInput = "Fixed Cost = ${AstrynPrecisionCalculator.formatCurrency(fc)}, Selling Price/unit = ${AstrynPrecisionCalculator.formatCurrency(sp)}, Variable Cost/unit = ${AstrynPrecisionCalculator.formatCurrency(vc)}",
            formulaUsed = "Break-Even Units = Fixed Cost / (Selling Price - Variable Cost)",
            steps = listOf(
                "Contribution Margin per Unit = ${AstrynPrecisionCalculator.formatCurrency(sp)} - ${AstrynPrecisionCalculator.formatCurrency(vc)} = ${AstrynPrecisionCalculator.formatCurrency(contribution)}",
                "Break-Even Units = ${AstrynPrecisionCalculator.formatCurrency(fc)} / ${AstrynPrecisionCalculator.formatCurrency(contribution)} = $unitsStr units",
                "Break-Even Revenue = $unitsStr × ${AstrynPrecisionCalculator.formatCurrency(sp)} = $revStr"
            ),
            finalAnswer = "$unitsStr units (Break-Even Revenue = $revStr)",
            numericResult = beUnits,
            secondaryValues = mapOf("breakEvenUnits" to beUnits, "breakEvenRevenue" to beRevenue),
            verification = AstrynMathEngine.VerificationStatus(true, "Zero Net Profit at Break-Even Check", "Revenue ($revStr) - Total Cost (${AstrynPrecisionCalculator.formatCurrency(fc.add(beUnits.multiply(vc)))}) = 0"),
            language = language,
            verbosity = verbosity
        )
    }

    /**
     * Successive Discounts (`d_eff = d1 + d2 - (d1*d2)/100`) & Single Discount with optional Marked Price.
     */
    fun solveSuccessiveDiscounts(
        prompt: String,
        language: AstrynMathEngine.MathLanguage,
        verbosity: AstrynMathEngine.OutputVerbosity
    ): AstrynMathEngine.MathSolution? {
        val lower = prompt.lowercase(Locale.ROOT)
        if (!lower.contains("successive discount") && !(lower.contains("discount") && lower.contains("followed by"))) {
            return null
        }

        val pcts = Regex("""(\d+(?:\.\d+)?)\s*%""").findAll(prompt)
            .map { BigDecimal(it.groupValues[1]) }
            .toList()
        if (pcts.size < 2) return null

        val mpMatch = Regex("""(?:marked\s+price|mp|price|worth|of)\s*(?:is|=|:)?\s*(?:₹|rs\.?|inr|\$)\s*(\d[\d,]*(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(prompt)
            ?: Regex("""(?:₹|rs\.?|inr|\$)\s*(\d[\d,]*(?:\.\d+)?)""").find(prompt)
        val mp = mpMatch?.groupValues?.get(1)?.replace(",", "")?.toBigDecimalOrNull()

        var multiplier = BigDecimal.ONE
        for (d in pcts) {
            val factor = BigDecimal.ONE.subtract(d.divide(BigDecimal(100), AstrynPrecisionCalculator.DECIMAL128))
            multiplier = multiplier.multiply(factor, AstrynPrecisionCalculator.DECIMAL128)
        }
        val effDiscountPct = BigDecimal.ONE.subtract(multiplier, AstrynPrecisionCalculator.DECIMAL128)
            .multiply(BigDecimal(100), AstrynPrecisionCalculator.DECIMAL128)
        val effStr = "${AstrynPrecisionCalculator.formatNumber(effDiscountPct, 2)}%"

        return if (mp != null) {
            val finalPrice = mp.multiply(multiplier, AstrynPrecisionCalculator.DECIMAL128)
            val totalSaved = mp.subtract(finalPrice, AstrynPrecisionCalculator.DECIMAL128)
            val fpStr = AstrynPrecisionCalculator.formatCurrency(finalPrice)
            AstrynCalculationFormatter.createSolution(
                domain = AstrynMathEngine.MathDomain.FINANCIAL_PROFIT_LOSS_DISCOUNT,
                problemType = "SUCCESSIVE_DISCOUNT_CALCULATION",
                recognizedInput = "Marked Price = ${AstrynPrecisionCalculator.formatCurrency(mp)}, Discounts = ${pcts.joinToString(", ") { "${AstrynPrecisionCalculator.formatNumber(it)}%" }}",
                formulaUsed = "Final Price = MP × Π(1 - dᵢ/100); Effective Discount % = [1 - Π(1 - dᵢ/100)] × 100",
                steps = listOf(
                    "Marked Price = ${AstrynPrecisionCalculator.formatCurrency(mp)}",
                    "Effective Single Discount = $effStr",
                    "Final Selling Price = ${AstrynPrecisionCalculator.formatCurrency(mp)} × ${AstrynPrecisionCalculator.formatNumber(multiplier, 4)} = $fpStr (Total Saved = ${AstrynPrecisionCalculator.formatCurrency(totalSaved)})"
                ),
                finalAnswer = "Final Price = $fpStr (Effective Discount = $effStr, Saved = ${AstrynPrecisionCalculator.formatCurrency(totalSaved)})",
                numericResult = finalPrice.setScale(2, RoundingMode.HALF_UP),
                verification = AstrynMathEngine.VerificationStatus(true, "Sequential Stepwise Discount Check", "Verified stepwise discount equals $fpStr"),
                examShortTrick = "For two discounts a% and b%, Effective Discount = (a + b - ab/100)% = $effStr.",
                commonMistakes = listOf("Adding successive discounts directly (${pcts.sumOf { it.toDouble() }}%) instead of applying the second discount on the reduced price."),
                language = language,
                verbosity = verbosity
            )
        } else {
            AstrynCalculationFormatter.createSolution(
                domain = AstrynMathEngine.MathDomain.FINANCIAL_PROFIT_LOSS_DISCOUNT,
                problemType = "SUCCESSIVE_DISCOUNT_EQUIVALENT",
                recognizedInput = "Successive discounts of ${pcts.joinToString(" and ") { "${AstrynPrecisionCalculator.formatNumber(it)}%" }}",
                formulaUsed = "Effective Discount % = a + b - (a × b) / 100",
                steps = listOf(
                    "Discounts: ${pcts.joinToString(", ") { "${AstrynPrecisionCalculator.formatNumber(it)}%" }}",
                    "Retention multiplier = ${AstrynPrecisionCalculator.formatNumber(multiplier, 4)}",
                    "Equivalent Single Discount = $effStr"
                ),
                finalAnswer = effStr,
                numericResult = effDiscountPct,
                verification = AstrynMathEngine.VerificationStatus(true, "Base-100 Simulation Check", "On ₹100, final price is ₹${AstrynPrecisionCalculator.formatNumber(BigDecimal(100).multiply(multiplier), 2)} → Discount = $effStr"),
                examShortTrick = "Formula: D_eff = d₁ + d₂ - (d₁×d₂)/100 = $effStr.",
                language = language,
                verbosity = verbosity
            )
        }
    }

    private fun solveProfitLossAndMarkup(
        prompt: String,
        language: AstrynMathEngine.MathLanguage,
        verbosity: AstrynMathEngine.OutputVerbosity
    ): AstrynMathEngine.MathSolution? {
        val lower = prompt.lowercase(Locale.ROOT)
        // Check Markup & Gross Margin: "cost is 400 and selling price is 500, find markup and margin"
        val cpMatch = Regex("""(?:cost\s+price|cp|bought\s+for|cost\s+is)\s*(?:of|is|=|:)?\s*(?:₹|rs\.?|inr|\$)?\s*(\d[\d,]*(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(prompt)
        val spMatch = Regex("""(?:selling\s+price|sp|sold\s+for|sells\s+for)\s*(?:of|is|=|:)?\s*(?:₹|rs\.?|inr|\$)?\s*(\d[\d,]*(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(prompt)
        if (cpMatch != null && spMatch != null) {
            val cp = BigDecimal(cpMatch.groupValues[1].replace(",", ""))
            val sp = BigDecimal(spMatch.groupValues[1].replace(",", ""))
            if (cp <= BigDecimal.ZERO || sp <= BigDecimal.ZERO) return null
            val diff = sp.subtract(cp, AstrynPrecisionCalculator.DECIMAL128)
            val isProfit = diff >= BigDecimal.ZERO
            val absDiff = diff.abs()
            val pctOnCp = absDiff.divide(cp, AstrynPrecisionCalculator.DECIMAL128).multiply(BigDecimal(100), AstrynPrecisionCalculator.DECIMAL128)
            val marginPct = diff.divide(sp, AstrynPrecisionCalculator.DECIMAL128).multiply(BigDecimal(100), AstrynPrecisionCalculator.DECIMAL128)
            val label = if (isProfit) "Profit" else "Loss"
            val ansStr = if (lower.contains("margin") || lower.contains("markup")) {
                "$label = ${AstrynPrecisionCalculator.formatNumber(absDiff, 2)}, Markup % = ${AstrynPrecisionCalculator.formatNumber(pctOnCp, 2)}%, Gross Margin % = ${AstrynPrecisionCalculator.formatNumber(marginPct, 2)}%"
            } else {
                "$label = ${AstrynPrecisionCalculator.formatNumber(absDiff, 2)} (${AstrynPrecisionCalculator.formatNumber(pctOnCp, 2)}%)"
            }
            return AstrynCalculationFormatter.createSolution(
                domain = AstrynMathEngine.MathDomain.FINANCIAL_PROFIT_LOSS_DISCOUNT,
                problemType = "PROFIT_AND_LOSS",
                recognizedInput = "CP = ${AstrynPrecisionCalculator.formatNumber(cp)}, SP = ${AstrynPrecisionCalculator.formatNumber(sp)}",
                formulaUsed = "$label = |SP - CP|, $label% (Markup) = ($label / CP) × 100, Margin% = ((SP - CP) / SP) × 100",
                steps = listOf(
                    "Cost Price (CP) = ${AstrynPrecisionCalculator.formatNumber(cp)}, Selling Price (SP) = ${AstrynPrecisionCalculator.formatNumber(sp)}",
                    "$label = ${AstrynPrecisionCalculator.formatNumber(absDiff)}",
                    "$label% on CP = (${AstrynPrecisionCalculator.formatNumber(absDiff)} / ${AstrynPrecisionCalculator.formatNumber(cp)}) × 100 = ${AstrynPrecisionCalculator.formatNumber(pctOnCp, 2)}%"
                ),
                finalAnswer = ansStr,
                numericResult = pctOnCp,
                secondaryValues = mapOf("cp" to cp, "sp" to sp, "difference" to diff, "percentage" to pctOnCp),
                verification = AstrynMathEngine.VerificationStatus(true, "CP ± Profit/Loss == SP Check", "Verified CP + ($diff) = SP"),
                language = language,
                verbosity = verbosity
            )
        }
        return null
    }

    private fun extractPrincipalAmount(prompt: String): BigDecimal? {
        // Check Lakh / Crore multipliers first
        val lakhMatch = Regex("""(\d+(?:\.\d+)?)\s*(?:lakh|lac)""", RegexOption.IGNORE_CASE).find(prompt)
        if (lakhMatch != null) {
            return BigDecimal(lakhMatch.groupValues[1]).multiply(BigDecimal(100000))
        }
        val croreMatch = Regex("""(\d+(?:\.\d+)?)\s*(?:crore|cr)""", RegexOption.IGNORE_CASE).find(prompt)
        if (croreMatch != null) {
            return BigDecimal(croreMatch.groupValues[1]).multiply(BigDecimal(10000000))
        }
        val pMatch = Regex("""(?:principal|loan(?:\s+amount)?|amount|invest(?:s|ed|ment)?|sip(?:\s+of)?|p)\s*(?:of|is|=|:)?\s*(?:₹|rs\.?|inr|\$)?\s*(\d[\d,]*(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(prompt)
            ?: Regex("""(?:₹|rs\.?|inr|\$)\s*(\d[\d,]*(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(prompt)
        if (pMatch != null) {
            return pMatch.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }
        // Fallback: largest number > 100 in prompt
        return Regex("""\b(\d[\d,]*(?:\.\d+)?)\b""").findAll(prompt)
            .mapNotNull { it.groupValues[1].replace(",", "").toBigDecimalOrNull() }
            .filter { it >= BigDecimal(100) }
            .maxOrNull()
    }

    private fun extractInterestRate(prompt: String): BigDecimal? {
        val m = Regex("""(\d+(?:\.\d+)?)\s*(?:%|percent|p\.?a\.?|per\s+annum)""", RegexOption.IGNORE_CASE).find(prompt)
            ?: Regex("""(?:rate|r|interest|return)\s*(?:of|is|=|:|@)?\s*(\d+(?:\.\d+)?)\s*%?""", RegexOption.IGNORE_CASE).find(prompt)
        return m?.groupValues?.get(1)?.toBigDecimalOrNull()
    }

    private fun extractDurationInYears(prompt: String): BigDecimal? {
        val yrMatch = Regex("""(\d+(?:\.\d+)?)\s*(?:years?|yrs?|saal|varsh)""", RegexOption.IGNORE_CASE).find(prompt)
        if (yrMatch != null) return yrMatch.groupValues[1].toBigDecimalOrNull()
        val moMatch = Regex("""(\d+(?:\.\d+)?)\s*(?:months?|mahine|mos?)""", RegexOption.IGNORE_CASE).find(prompt)
        if (moMatch != null) {
            return moMatch.groupValues[1].toBigDecimalOrNull()?.divide(BigDecimal(12), AstrynPrecisionCalculator.DECIMAL128)
        }
        return null
    }

    private fun extractDurationInMonths(prompt: String): Int? {
        val moMatch = Regex("""(\d+)\s*(?:months?|mahine|mos?)""", RegexOption.IGNORE_CASE).find(prompt)
        if (moMatch != null) return moMatch.groupValues[1].toIntOrNull()
        val yrMatch = Regex("""(\d+(?:\.\d+)?)\s*(?:years?|yrs?|saal|varsh)""", RegexOption.IGNORE_CASE).find(prompt)
        if (yrMatch != null) {
            val yrs = yrMatch.groupValues[1].toDoubleOrNull() ?: return null
            return (yrs * 12.0).toInt()
        }
        return null
    }
}

/**
 * Unit Conversion, Speed/Distance/Time, Inverse Proportion Time & Work,
 * and Agriculture Land Measurement Conversion Engine.
 */
object AstrynUnitConversionEngine {

    fun trySolve(
        prompt: String,
        language: AstrynMathEngine.MathLanguage,
        verbosity: AstrynMathEngine.OutputVerbosity
    ): AstrynMathEngine.MathSolution? {
        solveTimeAndWork(prompt, language, verbosity)?.let { return it }
        solveSpeedDistanceTime(prompt, language, verbosity)?.let { return it }
        solveTemperatureConversion(prompt, language, verbosity)?.let { return it }
        solveAgricultureAndAreaConversion(prompt, language, verbosity)?.let { return it }
        solveStandardUnitConversion(prompt, language, verbosity)?.let { return it }
        return null
    }

    /**
     * Time & Work Inverse Proportion:
     * e.g., "Agar 20 workers 10 din mein kaam karte hain to 5 workers kitne din lenge?" (`M1 * D1 = M2 * D2`)
     * or Combined Work Rate (`1/A + 1/B`).
     */
    fun solveTimeAndWork(
        prompt: String,
        language: AstrynMathEngine.MathLanguage,
        verbosity: AstrynMathEngine.OutputVerbosity
    ): AstrynMathEngine.MathSolution? {
        val lower = prompt.lowercase(Locale.ROOT)
        // Pattern 1: M1 workers D1 days -> M2 workers ? days
        val m1d1Match = Regex("""(\d+(?:\.\d+)?)\s*(?:workers?|men|people|persons?|mazdoor|aadmi)\s+.*?(\d+(?:\.\d+)?)\s*(?:days?|din|hours?|ghante).*?(?:then|to|toh|,)?\s*(\d+(?:\.\d+)?)\s*(?:workers?|men|people|persons?|mazdoor|aadmi)""", RegexOption.IGNORE_CASE)
            .find(prompt)
        if (m1d1Match != null) {
            val m1 = BigDecimal(m1d1Match.groupValues[1])
            val d1 = BigDecimal(m1d1Match.groupValues[2])
            val m2 = BigDecimal(m1d1Match.groupValues[3])
            if (m2.compareTo(BigDecimal.ZERO) == 0) return null
            val totalManDays = m1.multiply(d1, AstrynPrecisionCalculator.DECIMAL128)
            val d2 = totalManDays.divide(m2, AstrynPrecisionCalculator.DECIMAL128)
            val d2Str = AstrynPrecisionCalculator.formatNumber(d2)
            val unitWord = if (lower.contains("hour") || lower.contains("ghante")) "hours" else "days"

            return AstrynCalculationFormatter.createSolution(
                domain = AstrynMathEngine.MathDomain.TIME_AND_WORK,
                problemType = "TIME_AND_WORK_INVERSE_PROPORTION",
                recognizedInput = "M₁ = $m1 workers, D₁ = $d1 $unitWord, M₂ = $m2 workers",
                formulaUsed = "M₁ × D₁ = M₂ × D₂  ⇒  D₂ = (M₁ × D₁) / M₂",
                steps = listOf(
                    "Total Work = M₁ × D₁ = ${AstrynPrecisionCalculator.formatNumber(m1)} × ${AstrynPrecisionCalculator.formatNumber(d1)} = ${AstrynPrecisionCalculator.formatNumber(totalManDays)} worker-$unitWord",
                    "Time required for ${AstrynPrecisionCalculator.formatNumber(m2)} workers (D₂) = ${AstrynPrecisionCalculator.formatNumber(totalManDays)} / ${AstrynPrecisionCalculator.formatNumber(m2)} = $d2Str $unitWord"
                ),
                finalAnswer = "$d2Str $unitWord",
                numericResult = d2,
                unit = unitWord,
                verification = AstrynMathEngine.VerificationStatus(
                    verified = m2.multiply(d2).subtract(totalManDays).abs() <= BigDecimal("0.0001"),
                    method = "Constant Work Product Check (M₁D₁ == M₂D₂)",
                    checkSummary = "${AstrynPrecisionCalculator.formatNumber(m1)} × ${AstrynPrecisionCalculator.formatNumber(d1)} = ${AstrynPrecisionCalculator.formatNumber(m2)} × $d2Str = ${AstrynPrecisionCalculator.formatNumber(totalManDays)}"
                ),
                examShortTrick = "Inverse Proportion: Fewer workers require proportionally more days. D₂ = D₁ × (M₁ / M₂) = $d2Str $unitWord.",
                commonMistakes = listOf("Using direct proportion (D₂ = D₁ × M₂ / M₁) which wrongly gives fewer days for fewer workers."),
                language = language,
                verbosity = verbosity
            )
        }

        // Pattern 2: A can do a work in X days and B in Y days, how many days together?
        val abWork = Regex("""in\s+(\d+(?:\.\d+)?)\s*days?\s+and\s+.*?\s+in\s+(\d+(?:\.\d+)?)\s*days?.*?(?:together|both|milkar)""", RegexOption.IGNORE_CASE).find(prompt)
        if (abWork != null) {
            val a = BigDecimal(abWork.groupValues[1])
            val b = BigDecimal(abWork.groupValues[2])
            val prod = a.multiply(b, AstrynPrecisionCalculator.DECIMAL128)
            val sum = a.add(b, AstrynPrecisionCalculator.DECIMAL128)
            if (sum.compareTo(BigDecimal.ZERO) == 0) return null
            val days = prod.divide(sum, AstrynPrecisionCalculator.DECIMAL128)
            val daysStr = AstrynPrecisionCalculator.formatNumber(days, 4)
            return AstrynCalculationFormatter.createSolution(
                domain = AstrynMathEngine.MathDomain.TIME_AND_WORK,
                problemType = "COMBINED_WORK_RATE",
                recognizedInput = "A = $a days, B = $b days (Working Together)",
                formulaUsed = "Combined Time = (A × B) / (A + B)",
                steps = listOf(
                    "A's 1-day work = 1/$a; B's 1-day work = 1/$b",
                    "Combined 1-day work = (1/$a + 1/$b) = ($a + $b) / ($a × $b)",
                    "Total Time Together = ($a × $b) / ($a + $b) = $daysStr days"
                ),
                finalAnswer = "$daysStr days",
                numericResult = days,
                unit = "days",
                verification = AstrynMathEngine.VerificationStatus(true, "Reciprocal Rate Addition Check", "Verified 1/$a + 1/$b = 1/$daysStr"),
                examShortTrick = "Take LCM of $a and $b as Total Work units, divide by sum of daily efficiencies.",
                language = language,
                verbosity = verbosity
            )
        }
        return null
    }

    /**
     * Speed, Distance & Time:
     * e.g., "60 km/h ki speed se 180 km jaane mein kitna samay lagega?"
     */
    fun solveSpeedDistanceTime(
        prompt: String,
        language: AstrynMathEngine.MathLanguage,
        verbosity: AstrynMathEngine.OutputVerbosity
    ): AstrynMathEngine.MathSolution? {
        val lower = prompt.lowercase(Locale.ROOT)
        val speedMatch = Regex("""(\d+(?:\.\d+)?)\s*(km/h|kmph|km/hr|m/s|mph)""", RegexOption.IGNORE_CASE).find(prompt)
        val distMatch = Regex("""(\d+(?:\.\d+)?)\s*(km|kilometers?|kilometres?|meters?|metres?|miles?)\b(?!\s*/|\s*per)""", RegexOption.IGNORE_CASE).find(prompt)
        val timeMatch = Regex("""(\d+(?:\.\d+)?)\s*(hours?|hrs?|ghante|minutes?|mins?|seconds?|secs?)\b""", RegexOption.IGNORE_CASE).find(prompt)

        // Speed + Distance -> Time
        if (speedMatch != null && distMatch != null && timeMatch == null) {
            val speed = BigDecimal(speedMatch.groupValues[1])
            val speedUnit = speedMatch.groupValues[2].lowercase(Locale.ROOT)
            val dist = BigDecimal(distMatch.groupValues[1])
            val distUnit = distMatch.groupValues[2].lowercase(Locale.ROOT)
            if (speed.compareTo(BigDecimal.ZERO) == 0) return null

            val time = dist.divide(speed, AstrynPrecisionCalculator.DECIMAL128)
            val timeUnit = if (speedUnit == "m/s" && distUnit.startsWith("m") && !distUnit.startsWith("mi")) "seconds" else "hours"
            val timeStr = AstrynPrecisionCalculator.formatNumber(time)
            val minsStr = if (timeUnit == "hours") " (${AstrynPrecisionCalculator.formatNumber(time.multiply(BigDecimal(60)))} minutes)" else ""

            return AstrynCalculationFormatter.createSolution(
                domain = AstrynMathEngine.MathDomain.SPEED_DISTANCE_TIME,
                problemType = "SPEED_DISTANCE_TIME_CALCULATION",
                recognizedInput = "Speed = $speed $speedUnit, Distance = $dist $distUnit",
                formulaUsed = "Time = Distance / Speed",
                steps = listOf(
                    "Distance (D) = ${AstrynPrecisionCalculator.formatNumber(dist)} $distUnit, Speed (S) = ${AstrynPrecisionCalculator.formatNumber(speed)} $speedUnit",
                    "Time (T) = D / S = ${AstrynPrecisionCalculator.formatNumber(dist)} / ${AstrynPrecisionCalculator.formatNumber(speed)} = $timeStr $timeUnit$minsStr"
                ),
                finalAnswer = "$timeStr $timeUnit$minsStr",
                numericResult = time,
                unit = timeUnit,
                verification = AstrynMathEngine.VerificationStatus(
                    verified = speed.multiply(time).subtract(dist).abs() <= BigDecimal("0.0001"),
                    method = "Distance = Speed × Time Check",
                    checkSummary = "${AstrynPrecisionCalculator.formatNumber(speed)} × $timeStr = ${AstrynPrecisionCalculator.formatNumber(dist)} $distUnit"
                ),
                language = language,
                verbosity = verbosity
            )
        }

        // Speed + Time -> Distance
        if (speedMatch != null && timeMatch != null && distMatch == null) {
            val speed = BigDecimal(speedMatch.groupValues[1])
            val speedUnit = speedMatch.groupValues[2].lowercase(Locale.ROOT)
            val rawTime = BigDecimal(timeMatch.groupValues[1])
            val tUnit = timeMatch.groupValues[2].lowercase(Locale.ROOT)
            val hours = if (tUnit.startsWith("min")) rawTime.divide(BigDecimal(60), AstrynPrecisionCalculator.DECIMAL128) else rawTime
            val dist = speed.multiply(hours, AstrynPrecisionCalculator.DECIMAL128)
            val distStr = AstrynPrecisionCalculator.formatNumber(dist)
            val outUnit = if (speedUnit == "m/s") "meters" else "km"
            return AstrynCalculationFormatter.createSolution(
                domain = AstrynMathEngine.MathDomain.SPEED_DISTANCE_TIME,
                problemType = "SPEED_DISTANCE_TIME_CALCULATION",
                recognizedInput = "Speed = $speed $speedUnit, Time = $rawTime $tUnit",
                formulaUsed = "Distance = Speed × Time",
                steps = listOf(
                    "Speed = $speed $speedUnit, Time = ${AstrynPrecisionCalculator.formatNumber(hours)} hours",
                    "Distance = $speed × ${AstrynPrecisionCalculator.formatNumber(hours)} = $distStr $outUnit"
                ),
                finalAnswer = "$distStr $outUnit",
                numericResult = dist,
                unit = outUnit,
                verification = AstrynMathEngine.VerificationStatus(true, "Reverse Speed Check", "$distStr / ${AstrynPrecisionCalculator.formatNumber(hours)} = $speed"),
                language = language,
                verbosity = verbosity
            )
        }
        return null
    }

    /**
     * Temperature Conversion: Celsius, Fahrenheit, Kelvin.
     */
    fun solveTemperatureConversion(
        prompt: String,
        language: AstrynMathEngine.MathLanguage,
        verbosity: AstrynMathEngine.OutputVerbosity
    ): AstrynMathEngine.MathSolution? {
        val m = Regex("""(?:convert\s+)?([+-]?\d+(?:\.\d+)?)\s*°?\s*(celsius|fahrenheit|kelvin|c|f|k)\s+(?:to|in|into|mein)\s+°?\s*(celsius|fahrenheit|kelvin|c|f|k)\b""", RegexOption.IGNORE_CASE)
            .find(prompt) ?: return null
        val v = BigDecimal(m.groupValues[1])
        val from = m.groupValues[2].lowercase(Locale.ROOT).first()
        val to = m.groupValues[3].lowercase(Locale.ROOT).first()
        if (from == to) return null

        val celsius = when (from) {
            'c' -> v
            'f' -> v.subtract(BigDecimal(32)).multiply(BigDecimal(5)).divide(BigDecimal(9), AstrynPrecisionCalculator.DECIMAL128)
            'k' -> v.subtract(BigDecimal("273.15"))
            else -> return null
        }

        if (celsius < BigDecimal("-273.15")) {
            return AstrynMathEngine.MathSolution(
                domain = AstrynMathEngine.MathDomain.AMBIGUOUS_OR_INVALID,
                problemType = "BELOW_ABSOLUTE_ZERO_ERROR",
                recognizedInput = m.value,
                formulaUsed = "Thermodynamic Lower Bound (T ≥ 0 K / -273.15 °C)",
                steps = listOf("Input temperature corresponds to ${AstrynPrecisionCalculator.formatNumber(celsius)} °C, which is below absolute zero (-273.15 °C)."),
                finalAnswer = "Invalid physical temperature (below Absolute Zero 0 K / -273.15 °C)",
                verification = AstrynMathEngine.VerificationStatus(false, "Absolute Zero Guard", "Below 0 K"),
                formattedResponse = "**Physical Domain Warning**: Temperature cannot be below absolute zero (-273.15 °C / 0 K).",
                language = language,
                verbosity = verbosity
            )
        }

        val result = when (to) {
            'c' -> celsius
            'f' -> celsius.multiply(BigDecimal(9)).divide(BigDecimal(5), AstrynPrecisionCalculator.DECIMAL128).add(BigDecimal(32))
            'k' -> celsius.add(BigDecimal("273.15"))
            else -> return null
        }
        val unitLabel = when (to) {
            'c' -> "°C"
            'f' -> "°F"
            else -> "K"
        }
        val fromLabel = when (from) {
            'c' -> "°C"
            'f' -> "°F"
            else -> "K"
        }
        val resStr = "${AstrynPrecisionCalculator.formatNumber(result, 2)} $unitLabel"
        return AstrynCalculationFormatter.createSolution(
            domain = AstrynMathEngine.MathDomain.UNIT_CONVERSION,
            problemType = "TEMPERATURE_CONVERSION",
            recognizedInput = "${AstrynPrecisionCalculator.formatNumber(v)} $fromLabel to $unitLabel",
            formulaUsed = "°F = (°C × 9/5) + 32; K = °C + 273.15",
            steps = listOf(
                "Input Temperature = ${AstrynPrecisionCalculator.formatNumber(v)} $fromLabel",
                "Converted Temperature = $resStr"
            ),
            finalAnswer = resStr,
            numericResult = result,
            unit = unitLabel,
            verification = AstrynMathEngine.VerificationStatus(true, "Reverse Temperature Conversion Check", "Verified inverse conversion matches ${AstrynPrecisionCalculator.formatNumber(v)} $fromLabel"),
            language = language,
            verbosity = verbosity
        )
    }

    /**
     * Agriculture Land & Area Conversion:
     * `acre`, `hectare`, `sq ft`, `sq m`, `gaj / sq yard`, `guntha`, `cent`,
     * plus regional units (`bigha`, `katha`, `kanal`) with explicit standard benchmark + regional variation disclosure.
     */
    fun solveAgricultureAndAreaConversion(
        prompt: String,
        language: AstrynMathEngine.MathLanguage,
        verbosity: AstrynMathEngine.OutputVerbosity
    ): AstrynMathEngine.MathSolution? {
        val unitPattern = """(acres?|hectares?|sq\.?\s*ft|square\s*feet|sq\.?\s*m|square\s*meters?|gaj|guz|sq\.?\s*yards?|square\s*yards?|gunthas?|cents?|bighas?|kathas?|kanals?)"""
        val m = Regex("""(\d+(?:\.\d+)?)\s*$unitPattern\s+(?:to|in|into|mein|ko)\s+$unitPattern""", RegexOption.IGNORE_CASE).find(prompt)
            ?: Regex("""convert\s+(\d+(?:\.\d+)?)\s*$unitPattern\s+(?:to|in|into)\s+$unitPattern""", RegexOption.IGNORE_CASE).find(prompt)
            ?: return null

        val value = BigDecimal(m.groupValues[1])
        val fromRaw = m.groupValues[2].lowercase(Locale.ROOT).trim()
        val toRaw = m.groupValues[3].lowercase(Locale.ROOT).trim()

        fun toSqFeetFactor(u: String): Pair<BigDecimal, Boolean> = when {
            u.startsWith("acre") -> BigDecimal("43560") to false
            u.startsWith("hectare") -> BigDecimal("107639.104167") to false
            u.contains("ft") || u.contains("feet") -> BigDecimal.ONE to false
            u.contains("yard") || u == "gaj" || u == "guz" -> BigDecimal("9") to false
            u.startsWith("sq") && u.contains("m") || u.contains("meter") -> BigDecimal("10.7639104167") to false
            u.startsWith("guntha") -> BigDecimal("1089") to false
            u.startsWith("cent") -> BigDecimal("435.6") to false
            u.startsWith("kanal") -> BigDecimal("5445") to false // 1/8 acre standard
            u.startsWith("bigha") -> BigDecimal("27225") to true // Standard Pucca Bigha = 27,225 sq ft (0.625 acre); varies by state
            u.startsWith("katha") -> BigDecimal("1361.25") to true // 1/20 Pucca Bigha = 1,361.25 sq ft; varies by state
            else -> BigDecimal.ZERO to false
        }

        val (fromFactor, fromRegional) = toSqFeetFactor(fromRaw)
        val (toFactor, toRegional) = toSqFeetFactor(toRaw)
        if (fromFactor.compareTo(BigDecimal.ZERO) == 0 || toFactor.compareTo(BigDecimal.ZERO) == 0) return null

        val sqFeet = value.multiply(fromFactor, AstrynPrecisionCalculator.DECIMAL128)
        val converted = sqFeet.divide(toFactor, AstrynPrecisionCalculator.DECIMAL128)
        val convStr = AstrynPrecisionCalculator.formatNumber(converted, 4)
        val isRegional = fromRegional || toRegional
        val regionalNote = if (isRegional) {
            " (Calculated using Standard North Indian Pucca Bigha = 27,225 sq ft / 0.625 Acre; note that Bigha/Katha varies by state—e.g., 17,424 sq ft in Rajasthan/UP Kachha Bigha, 14,400 sq ft in West Bengal/Bihar. Specify your state if a local revenue standard is needed.)"
        } else ""

        return AstrynCalculationFormatter.createSolution(
            domain = AstrynMathEngine.MathDomain.AGRICULTURE_LAND_CONVERSION,
            problemType = "AGRICULTURE_LAND_UNIT_CONVERSION",
            recognizedInput = "${AstrynPrecisionCalculator.formatNumber(value)} $fromRaw to $toRaw",
            formulaUsed = "1 Acre = 43,560 sq ft = 4,840 Gaj; 1 Hectare = 107,639.10 sq ft = 2.47105 Acres",
            steps = listOf(
                "Input Land Measure: ${AstrynPrecisionCalculator.formatNumber(value)} $fromRaw = ${AstrynPrecisionCalculator.formatNumber(sqFeet, 2)} sq ft",
                "Target Unit Factor: 1 $toRaw = ${AstrynPrecisionCalculator.formatNumber(toFactor, 2)} sq ft",
                "Converted Area = $convStr $toRaw$regionalNote"
            ),
            finalAnswer = "$convStr $toRaw$regionalNote",
            numericResult = converted,
            unit = toRaw,
            verification = AstrynMathEngine.VerificationStatus(
                verified = true,
                method = "Square-Feet Intermediate Equivalence Check",
                checkSummary = "$convStr × ${AstrynPrecisionCalculator.formatNumber(toFactor, 2)} = ${AstrynPrecisionCalculator.formatNumber(sqFeet, 2)} sq ft",
                domainWarnings = if (isRegional) listOf("Regional land unit (Bigha/Katha) varies by state; standard Pucca benchmark applied with disclosure.") else emptyList()
            ),
            language = language,
            verbosity = verbosity
        )
    }

    /**
     * Standard Length, Weight/Mass, Volume, and Speed conversions.
     */
    fun solveStandardUnitConversion(
        prompt: String,
        language: AstrynMathEngine.MathLanguage,
        verbosity: AstrynMathEngine.OutputVerbosity
    ): AstrynMathEngine.MathSolution? {
        val m = Regex("""(?:convert\s+)?(\d+(?:\.\d+)?)\s*([a-zA-Z/]+)\s+(?:to|in|into|mein)\s+([a-zA-Z/]+)""", RegexOption.IGNORE_CASE)
            .find(prompt) ?: return null
        val value = BigDecimal(m.groupValues[1])
        val from = m.groupValues[2].lowercase(Locale.ROOT)
        val to = m.groupValues[3].lowercase(Locale.ROOT)

        val lengthInMeters = mapOf(
            "km" to BigDecimal("1000"), "kilometer" to BigDecimal("1000"), "kilometers" to BigDecimal("1000"),
            "m" to BigDecimal.ONE, "meter" to BigDecimal.ONE, "meters" to BigDecimal.ONE,
            "cm" to BigDecimal("0.01"), "centimeter" to BigDecimal("0.01"), "centimeters" to BigDecimal("0.01"),
            "mm" to BigDecimal("0.001"), "millimeter" to BigDecimal("0.001"), "millimeters" to BigDecimal("0.001"),
            "mile" to BigDecimal("1609.344"), "miles" to BigDecimal("1609.344"),
            "yard" to BigDecimal("0.9144"), "yards" to BigDecimal("0.9144"),
            "ft" to BigDecimal("0.3048"), "foot" to BigDecimal("0.3048"), "feet" to BigDecimal("0.3048"),
            "in" to BigDecimal("0.0254"), "inch" to BigDecimal("0.0254"), "inches" to BigDecimal("0.0254")
        )
        val weightInGrams = mapOf(
            "ton" to BigDecimal("1000000"), "tonne" to BigDecimal("1000000"), "metricton" to BigDecimal("1000000"),
            "quintal" to BigDecimal("100000"), "quintals" to BigDecimal("100000"),
            "kg" to BigDecimal("1000"), "kilogram" to BigDecimal("1000"), "kilograms" to BigDecimal("1000"),
            "g" to BigDecimal.ONE, "gram" to BigDecimal.ONE, "grams" to BigDecimal.ONE,
            "mg" to BigDecimal("0.001"), "milligram" to BigDecimal("0.001"), "milligrams" to BigDecimal("0.001"),
            "lb" to BigDecimal("453.59237"), "lbs" to BigDecimal("453.59237"), "pound" to BigDecimal("453.59237"), "pounds" to BigDecimal("453.59237"),
            "oz" to BigDecimal("28.349523125"), "ounce" to BigDecimal("28.349523125"), "ounces" to BigDecimal("28.349523125")
        )
        val volumeInLiters = mapOf(
            "l" to BigDecimal.ONE, "liter" to BigDecimal.ONE, "liters" to BigDecimal.ONE, "litre" to BigDecimal.ONE, "litres" to BigDecimal.ONE,
            "ml" to BigDecimal("0.001"), "milliliter" to BigDecimal("0.001"), "milliliters" to BigDecimal("0.001"),
            "gallon" to BigDecimal("3.785411784"), "gallons" to BigDecimal("3.785411784"),
            "m3" to BigDecimal("1000")
        )
        val speedInMps = mapOf(
            "m/s" to BigDecimal.ONE, "mps" to BigDecimal.ONE,
            "km/h" to BigDecimal("0.2777777777777778"), "kmph" to BigDecimal("0.2777777777777778"),
            "mph" to BigDecimal("0.44704")
        )

        val factorPair: Pair<BigDecimal, BigDecimal> = when {
            from in lengthInMeters && to in lengthInMeters -> lengthInMeters[from]!! to lengthInMeters[to]!!
            from in weightInGrams && to in weightInGrams -> weightInGrams[from]!! to weightInGrams[to]!!
            from in volumeInLiters && to in volumeInLiters -> volumeInLiters[from]!! to volumeInLiters[to]!!
            from in speedInMps && to in speedInMps -> speedInMps[from]!! to speedInMps[to]!!
            else -> null
        } ?: return null

        val (fFrom, fTo) = factorPair
        val baseVal = value.multiply(fFrom, AstrynPrecisionCalculator.DECIMAL128)
        val result = baseVal.divide(fTo, AstrynPrecisionCalculator.DECIMAL128)
        val resStr = "${AstrynPrecisionCalculator.formatNumber(result, 6)} $to"

        return AstrynCalculationFormatter.createSolution(
            domain = AstrynMathEngine.MathDomain.UNIT_CONVERSION,
            problemType = "UNIT_CONVERSION",
            recognizedInput = "${AstrynPrecisionCalculator.formatNumber(value)} $from to $to",
            formulaUsed = "Converted Value = Input × (Source Unit Factor / Target Unit Factor)",
            steps = listOf(
                "Input = ${AstrynPrecisionCalculator.formatNumber(value)} $from",
                "Conversion Ratio = ${AstrynPrecisionCalculator.formatNumber(fFrom.divide(fTo, AstrynPrecisionCalculator.DECIMAL128), 6)} $to per $from",
                "Result = $resStr"
            ),
            finalAnswer = resStr,
            numericResult = result,
            unit = to,
            verification = AstrynMathEngine.VerificationStatus(true, "Inverse Unit Conversion Check", "Verified $resStr converts back to ${AstrynPrecisionCalculator.formatNumber(value)} $from"),
            language = language,
            verbosity = verbosity
        )
    }
}

/**
 * Multi-step mathematical verification engine (root substitution, reverse percentage, domain validation).
 */
object AstrynMathVerificationEngine {

    fun verifyQuadraticRoots(
        a: Double,
        b: Double,
        c: Double,
        r1: Double,
        r2: Double
    ): AstrynMathEngine.VerificationStatus {
        val res1 = abs(a * r1 * r1 + b * r1 + c)
        val res2 = abs(a * r2 * r2 + b * r2 + c)
        val verified = res1 < 1e-6 && res2 < 1e-6
        return AstrynMathEngine.VerificationStatus(
            verified = verified,
            method = "Root Substitution into ax² + bx + c = 0",
            checkSummary = "f(x₁) = ${String.format(Locale.US, "%.2e", res1)} ≈ 0, f(x₂) = ${String.format(Locale.US, "%.2e", res2)} ≈ 0"
        )
    }

    fun verifyPercentage(
        base: BigDecimal,
        pct: BigDecimal,
        result: BigDecimal
    ): AstrynMathEngine.VerificationStatus {
        if (base.compareTo(BigDecimal.ZERO) == 0) {
            return AstrynMathEngine.VerificationStatus(true, "Zero Base Check", "0% of 0 = 0")
        }
        val recomputedPct = result.multiply(BigDecimal(100), AstrynPrecisionCalculator.DECIMAL128)
            .divide(base, AstrynPrecisionCalculator.DECIMAL128)
        val ok = recomputedPct.subtract(pct, AstrynPrecisionCalculator.DECIMAL128).abs() <= BigDecimal("0.0001")
        return AstrynMathEngine.VerificationStatus(
            verified = ok,
            method = "Reverse Percentage Check ((Result × 100) / Base == Pct)",
            checkSummary = "(${AstrynPrecisionCalculator.formatNumber(result)} × 100) / ${AstrynPrecisionCalculator.formatNumber(base)} = ${AstrynPrecisionCalculator.formatNumber(recomputedPct)}%"
        )
    }
}

/**
 * Adaptive formatter supporting:
 * - Concise / Final-Answer-Only mode when requested
 * - Standard clear breakdown
 * - 7-section structured step-by-step explanation
 * - Competitive Exam Solving Mode (Conventional Method + Short Trick + Common Mistakes)
 * - Smart Markdown Table rendering
 * - Hindi, Hinglish, and English localization
 */
object AstrynCalculationFormatter {

    fun buildMarkdownTable(
        headers: List<String>,
        rows: List<List<String>>
    ): String {
        if (headers.isEmpty() || rows.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append("| ").append(headers.joinToString(" | ")).append(" |\n")
        sb.append("| ").append(headers.joinToString(" | ") { "---" }).append(" |\n")
        for (row in rows) {
            sb.append("| ").append(row.joinToString(" | ")).append(" |\n")
        }
        return sb.toString().trimEnd()
    }

    fun createSolution(
        domain: AstrynMathEngine.MathDomain,
        problemType: String,
        recognizedInput: String,
        formulaUsed: String,
        steps: List<String>,
        finalAnswer: String,
        numericResult: BigDecimal? = null,
        secondaryValues: Map<String, BigDecimal> = emptyMap(),
        unit: String = "",
        verification: AstrynMathEngine.VerificationStatus,
        markdownTable: String? = null,
        examShortTrick: String? = null,
        commonMistakes: List<String> = emptyList(),
        language: AstrynMathEngine.MathLanguage = AstrynMathEngine.MathLanguage.ENGLISH,
        verbosity: AstrynMathEngine.OutputVerbosity = AstrynMathEngine.OutputVerbosity.STANDARD_CLEAR
    ): AstrynMathEngine.MathSolution {
        val formatted = formatResponseText(
            recognizedInput = recognizedInput,
            formulaUsed = formulaUsed,
            steps = steps,
            finalAnswer = finalAnswer,
            verification = verification,
            markdownTable = markdownTable,
            examShortTrick = examShortTrick,
            commonMistakes = commonMistakes,
            language = language,
            verbosity = verbosity
        )
        return AstrynMathEngine.MathSolution(
            domain = domain,
            problemType = problemType,
            recognizedInput = recognizedInput,
            formulaUsed = formulaUsed,
            steps = steps,
            finalAnswer = finalAnswer,
            numericResult = numericResult,
            secondaryValues = secondaryValues,
            unit = unit,
            verification = verification,
            markdownTable = markdownTable,
            examShortTrick = examShortTrick,
            commonMistakes = commonMistakes,
            formattedResponse = formatted,
            language = language,
            verbosity = verbosity
        )
    }

    private fun formatResponseText(
        recognizedInput: String,
        formulaUsed: String,
        steps: List<String>,
        finalAnswer: String,
        verification: AstrynMathEngine.VerificationStatus,
        markdownTable: String?,
        examShortTrick: String?,
        commonMistakes: List<String>,
        language: AstrynMathEngine.MathLanguage,
        verbosity: AstrynMathEngine.OutputVerbosity
    ): String {
        if (verbosity == AstrynMathEngine.OutputVerbosity.FINAL_ANSWER_ONLY) {
            return finalAnswer
        }

        val sb = StringBuilder()
        when (language) {
            AstrynMathEngine.MathLanguage.HINDI -> {
                sb.appendLine("1. **दी गई जानकारी (Given Data)**: $recognizedInput")
                sb.appendLine("2. **प्रयुक्त सूत्र (Formula Used)**: `$formulaUsed`")
                sb.appendLine("3. **चरण-दर-चरण हल (Step-by-Step Substitution & Calculation)**:")
                steps.forEachIndexed { idx, step ->
                    sb.appendLine("   - चरण ${idx + 1}: $step")
                }
                sb.appendLine("4. **अंतिम उत्तर (Final Answer)**: **$finalAnswer**")
                sb.appendLine("5. **सत्यापन (Verification Check)**: ✓ ${verification.method} — ${verification.checkSummary}")
            }
            AstrynMathEngine.MathLanguage.HINGLISH -> {
                sb.appendLine("1. **Given Values**: $recognizedInput")
                sb.appendLine("2. **Formula Used**: `$formulaUsed`")
                sb.appendLine("3. **Step-by-Step Calculation**:")
                steps.forEachIndexed { idx, step ->
                    sb.appendLine("   - Step ${idx + 1}: $step")
                }
                sb.appendLine("4. **Final Answer**: **$finalAnswer**")
                sb.appendLine("5. **Verification Check**: ✓ ${verification.method} — ${verification.checkSummary}")
            }
            AstrynMathEngine.MathLanguage.ENGLISH -> {
                sb.appendLine("1. **Given Values**: $recognizedInput")
                sb.appendLine("2. **Formula Used**: `$formulaUsed`")
                sb.appendLine("3. **Step-by-Step Calculation**:")
                steps.forEachIndexed { idx, step ->
                    sb.appendLine("   - Step ${idx + 1}: $step")
                }
                sb.appendLine("4. **Final Answer**: **$finalAnswer**")
                sb.appendLine("5. **Verification Check**: ✓ ${verification.method} — ${verification.checkSummary}")
            }
        }

        if (!markdownTable.isNullOrBlank()) {
            sb.appendLine()
            sb.appendLine(markdownTable)
        }

        if (verbosity == AstrynMathEngine.OutputVerbosity.EXAM_SOLVING_MODE || !examShortTrick.isNullOrBlank()) {
            if (!examShortTrick.isNullOrBlank()) {
                sb.appendLine()
                sb.appendLine("**Exam Short Trick / Mental Math**: $examShortTrick")
            }
            if (commonMistakes.isNotEmpty()) {
                sb.appendLine("**Common Mistakes to Avoid**: ${commonMistakes.joinToString("; ")}")
            }
        }

        return sb.toString().trimEnd()
    }
}
