package com.example.ui.screens.quiz

import androidx.compose.runtime.Immutable
import java.util.UUID

@Immutable
data class QuizQuestion(
  val id: String = UUID.randomUUID().toString(),
  val question: String,
  val options: List<String>,
  val correctIndex: Int,
  val explanation: String,
)

@Immutable
data class QuizSession(
  val id: String = UUID.randomUUID().toString(),
  val title: String,
  val subtitle: String,
  val questions: List<QuizQuestion>,
) {
  companion object {
    val BUILT_IN_QUIZZES: List<QuizSession> =
      listOf(
        QuizSession(
          title = "General Science & Tech",
          subtitle = "Physics, AI, and everyday technology",
          questions =
            listOf(
              QuizQuestion(
                question = "What does AI stand for in computer science?",
                options = listOf("Automated Internet", "Artificial Intelligence", "Advanced Integration", "Action Index"),
                correctIndex = 1,
                explanation = "AI stands for Artificial Intelligence, enabling computers to reason, learn, and solve problems.",
              ),
              QuizQuestion(
                question = "Which planet in our solar system is known as the Red Planet?",
                options = listOf("Venus", "Jupiter", "Mars", "Saturn"),
                correctIndex = 2,
                explanation = "Mars is called the Red Planet because iron oxide (rust) on its surface gives it a reddish appearance.",
              ),
              QuizQuestion(
                question = "What is the primary unit of electric resistance in physics?",
                options = listOf("Volt", "Ampere", "Ohm", "Watt"),
                correctIndex = 2,
                explanation = "Ohm (symbol: Ω) is the SI unit of electrical resistance, defined by Ohm's Law (V = I * R).",
              ),
              QuizQuestion(
                question = "Which gas do plants primarily absorb during photosynthesis?",
                options = listOf("Oxygen", "Carbon Dioxide", "Nitrogen", "Hydrogen"),
                correctIndex = 1,
                explanation = "Plants take in Carbon Dioxide (CO2) and release Oxygen (O2) during daytime photosynthesis.",
              ),
              QuizQuestion(
                question = "What is the speed of light in vacuum approximately?",
                options = listOf("300,000 km/s", "150,000 km/s", "3,000 km/s", "30,000 km/s"),
                correctIndex = 0,
                explanation = "Light travels at approximately 299,792 km/s (rounded to 300,000 km/second) in a vacuum.",
              ),
            ),
        ),
        QuizSession(
          title = "Android & Kotlin Master",
          subtitle = "Coroutines, Compose, and architecture",
          questions =
            listOf(
              QuizQuestion(
                question = "In Jetpack Compose, which keyword preserves state across recompositions?",
                options = listOf("persist", "remember", "save", "retain"),
                correctIndex = 1,
                explanation = "'remember' stores a value produced by a calculation in memory across recompositions.",
              ),
              QuizQuestion(
                question = "Which Kotlin construct represents a hot stream with immediate latest value?",
                options = listOf("Channel", "SharedFlow", "StateFlow", "Sequence"),
                correctIndex = 2,
                explanation = "StateFlow is a hot Flow that always holds and emits its latest current state.",
              ),
              QuizQuestion(
                question = "What is the minimum recommended touch target size in Material Design 3?",
                options = listOf("24dp x 24dp", "36dp x 36dp", "48dp x 48dp", "64dp x 64dp"),
                correctIndex = 2,
                explanation = "Android accessibility guidelines require at least 48dp x 48dp interactive touch target sizes.",
              ),
              QuizQuestion(
                question = "Which dispatcher is designed for disk and network I/O in Kotlin coroutines?",
                options = listOf("Dispatchers.Main", "Dispatchers.IO", "Dispatchers.Default", "Dispatchers.Unconfined"),
                correctIndex = 1,
                explanation = "Dispatchers.IO uses an elastic pool of threads dedicated to blocking I/O tasks like database or file access.",
              ),
            ),
        ),
        QuizSession(
          title = "General Knowledge & India",
          subtitle = "Geography, Polity, and Facts",
          questions =
            listOf(
              QuizQuestion(
                question = "Who is known as the Chief Architect of the Indian Constitution?",
                options = listOf("Mahatma Gandhi", "Dr. B.R. Ambedkar", "Jawaharlal Nehru", "Sardar Patel"),
                correctIndex = 1,
                explanation = "Dr. Bhimrao Ramji Ambedkar chaired the Drafting Committee of the Constituent Assembly.",
              ),
              QuizQuestion(
                question = "Which is the longest river flowing entirely within India?",
                options = listOf("Ganga", "Yamuna", "Godavari", "Narmada"),
                correctIndex = 0,
                explanation = "The Ganga is India's longest and holiest river with a total length of approximately 2,525 km.",
              ),
              QuizQuestion(
                question = "Which state is known as the 'Spices Garden of India'?",
                options = listOf("Punjab", "Kerala", "Gujarat", "Assam"),
                correctIndex = 1,
                explanation = "Kerala has been a global center of spice trade since ancient times, famous for pepper, cardamom, and clove.",
              ),
            ),
        ),
      )

    /**
     * Parses structured AI generated quiz text into a list of [QuizQuestion].
     */
    fun parseGeneratedQuiz(rawText: String, fallbackTopic: String): QuizSession {
      val questions = mutableListOf<QuizQuestion>()
      val blocks = rawText.split(Regex("(?i)(?=Q(?:uestion)?\\s*\\d+[:.)])"))

      for (block in blocks) {
        val trimmed = block.trim()
        if (trimmed.length < 20) continue
        val lines = trimmed.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) continue

        val qLine = lines.firstOrNull { it.matches(Regex("(?i)^Q(?:uestion)?\\s*\\d+[:.)].*")) } ?: lines[0]
        val cleanQuestion = qLine.replace(Regex("(?i)^Q(?:uestion)?\\s*\\d+[:.)]\\s*"), "").trim()

        val optionLines = lines.filter { it.matches(Regex("^[A-Da-d][).:-].*")) }
        val options =
          if (optionLines.size >= 2) {
            optionLines.take(4).map { it.replace(Regex("^[A-Da-d][).:-]\\s*"), "").trim() }
          } else {
            listOf("Option A", "Option B", "Option C", "Option D")
          }

        val answerLine = lines.firstOrNull { it.contains("Answer:", ignoreCase = true) || it.contains("Correct:", ignoreCase = true) }
        var correctIdx = 0
        if (answerLine != null) {
          when {
            answerLine.contains("A", ignoreCase = true) -> correctIdx = 0
            answerLine.contains("B", ignoreCase = true) -> correctIdx = 1
            answerLine.contains("C", ignoreCase = true) -> correctIdx = 2
            answerLine.contains("D", ignoreCase = true) -> correctIdx = 3
          }
        }

        val explanationLine = lines.firstOrNull { it.contains("Explanation:", ignoreCase = true) }
        val explanation =
          explanationLine?.replace(Regex("(?i)^Explanation:\\s*"), "")?.trim()
            ?: "Correct answer is option ${('A' + correctIdx)}."

        if (cleanQuestion.isNotBlank()) {
          questions.add(
            QuizQuestion(
              question = cleanQuestion,
              options = options,
              correctIndex = correctIdx.coerceIn(0, (options.size - 1).coerceAtLeast(0)),
              explanation = explanation,
            )
          )
        }
      }

      val resolvedQuestions = if (questions.isNotEmpty()) questions else BUILT_IN_QUIZZES[0].questions
      return QuizSession(
        title = fallbackTopic.ifBlank { "Exam Quiz" },
        subtitle = "${resolvedQuestions.size} Questions • Interactive Practice",
        questions = resolvedQuestions,
      )
    }
  }
}
