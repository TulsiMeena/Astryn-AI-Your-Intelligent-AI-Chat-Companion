package com.example.ui.screens.quiz

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.components.GlassTextField
import com.example.ui.theme.AppBorder
import com.example.ui.theme.AppRadius
import com.example.ui.theme.AppSpacing
import com.example.ui.theme.AppThemeTokens

/**
 * Interactive Exam Quiz & Flashcard Generator Dialog with Live Score Counter.
 */
@Composable
fun InteractiveQuizDialog(
  initialTopic: String = "",
  onDismiss: () -> Unit,
  onRequestAiQuiz: (topic: String) -> Unit,
  isGeneratingAiQuiz: Boolean = false,
  customQuizSession: QuizSession? = null,
  modifier: Modifier = Modifier,
) {
  val glass = AppThemeTokens.glass
  var activeSession by remember(customQuizSession) {
    mutableStateOf(customQuizSession ?: QuizSession.BUILT_IN_QUIZZES[0])
  }
  var currentQuestionIndex by remember { mutableIntStateOf(0) }
  var selectedOptionIndex by remember { mutableStateOf<Int?>(null) }
  var isAnswerSubmitted by remember { mutableStateOf(false) }
  var score by remember { mutableIntStateOf(0) }
  var isQuizComplete by remember { mutableStateOf(false) }

  var customTopicInput by remember { mutableStateOf(initialTopic) }
  var showTopicGenerator by remember { mutableStateOf(false) }

  val questions = activeSession.questions
  val currentQuestion = questions.getOrNull(currentQuestionIndex) ?: questions.first()

  fun restartCurrentQuiz() {
    currentQuestionIndex = 0
    selectedOptionIndex = null
    isAnswerSubmitted = false
    score = 0
    isQuizComplete = false
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties =
      DialogProperties(
        usePlatformDefaultWidth = false,
        dismissOnBackPress = true,
      ),
  ) {
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(Color.Black.copy(alpha = 0.85f))
          .padding(14.dp)
          .testTag("interactive_quiz_dialog"),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        modifier =
          Modifier.fillMaxWidth(0.98f)
            .fillMaxHeight(0.94f)
            .clip(RoundedCornerShape(20.dp))
            .background(if (glass.isDark) Color(0xFF0C1226) else Color(0xFFF8FAFC))
            .border(
              AppBorder.hairline,
              if (glass.isDark) glass.accentCyan.copy(alpha = 0.45f) else glass.borderSpecular,
              RoundedCornerShape(20.dp),
            )
            .verticalScroll(rememberScrollState()),
      ) {
        // -------------------------------------------------------------
        // Header Bar (Title, Quiz Selector, Close)
        // -------------------------------------------------------------
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .background(if (glass.isDark) Color(0xFF131A33) else Color(0xFFE2E8F0))
              .padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Box(
              modifier =
                Modifier.size(32.dp)
                  .clip(CircleShape)
                  .background(glass.accentCyan.copy(alpha = 0.20f)),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Outlined.Quiz,
                contentDescription = null,
                tint = glass.accentCyan,
                modifier = Modifier.size(18.dp),
              )
            }

            Column {
              Text(
                text = "Exam Quiz & Flashcards",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = glass.textPrimary,
              )
              Text(
                text = activeSession.title,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                color = glass.accentCyan,
              )
            }
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            // AI Generate Quiz Button
            Box(
              modifier =
                Modifier.clip(AppRadius.shapePill)
                  .background(glass.accentCyan.copy(alpha = 0.16f))
                  .border(AppBorder.hairline, glass.accentCyan, AppRadius.shapePill)
                  .clickable { showTopicGenerator = !showTopicGenerator }
                  .padding(horizontal = 8.dp, vertical = 4.dp)
                  .testTag("quiz_custom_generate_toggle"),
              contentAlignment = Alignment.Center,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(
                  imageVector = Icons.Outlined.AutoAwesome,
                  contentDescription = null,
                  tint = glass.accentCyan,
                  modifier = Modifier.size(12.dp),
                )
                Text(
                  text = "New Topic",
                  style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                  fontWeight = FontWeight.Bold,
                  color = glass.accentCyan,
                )
              }
            }

            // Close Button
            Box(
              modifier =
                Modifier.size(30.dp)
                  .clip(CircleShape)
                  .background(glass.surfaceStandard.copy(alpha = 0.7f))
                  .clickable(role = Role.Button, onClick = onDismiss)
                  .semantics {
                    role = Role.Button
                    contentDescription = "Close Quiz"
                  }
                  .testTag("quiz_close_button"),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = null,
                tint = glass.textPrimary,
                modifier = Modifier.size(15.dp),
              )
            }
          }
        }

        // -------------------------------------------------------------
        // Preset Quiz Selector Chips
        // -------------------------------------------------------------
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .background(if (glass.isDark) Color(0xFF0E152B) else Color(0xFFECEFF1))
              .horizontalScroll(rememberScrollState())
              .padding(horizontal = 12.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          QuizSession.BUILT_IN_QUIZZES.forEachIndexed { idx, session ->
            val isSelected = session.title == activeSession.title
            Row(
              modifier =
                Modifier.clip(AppRadius.shapePill)
                  .background(
                    if (isSelected) glass.accentCyan.copy(alpha = 0.22f)
                    else Color.Transparent
                  )
                  .border(
                    width = if (isSelected) 1.dp else 0.dp,
                    color = if (isSelected) glass.accentCyan else Color.Transparent,
                    shape = AppRadius.shapePill,
                  )
                  .clickable {
                    activeSession = session
                    restartCurrentQuiz()
                  }
                  .padding(horizontal = 10.dp, vertical = 4.dp)
                  .testTag("quiz_preset_chip_$idx"),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text(
                text = session.title,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) glass.accentCyan else glass.textSecondary,
              )
            }
          }
        }

        // -------------------------------------------------------------
        // Collapsible Custom Topic Generator Input
        // -------------------------------------------------------------
        AnimatedVisibility(
          visible = showTopicGenerator,
          enter = fadeIn(),
          exit = fadeOut(),
        ) {
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .background(if (glass.isDark) Color(0xFF161F3D) else Color(0xFFE2E8F0))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            GlassTextField(
              value = customTopicInput,
              onValueChange = { customTopicInput = it },
              placeholder = "Enter any topic (e.g., Photosynthesis, Indian Polity)…",
              singleLine = true,
              maxLines = 1,
              modifier = Modifier.weight(1f).testTag("quiz_topic_input"),
            )

            Box(
              modifier =
                Modifier.clip(RoundedCornerShape(10.dp))
                  .background(glass.accentCyan)
                  .clickable(enabled = customTopicInput.isNotBlank() && !isGeneratingAiQuiz) {
                    onRequestAiQuiz(customTopicInput.trim())
                    showTopicGenerator = false
                  }
                  .padding(horizontal = 10.dp, vertical = 8.dp)
                  .testTag("quiz_topic_submit_button"),
              contentAlignment = Alignment.Center,
            ) {
              if (isGeneratingAiQuiz) {
                CircularProgressIndicator(
                  modifier = Modifier.size(14.dp),
                  strokeWidth = 1.6.dp,
                  color = Color.Black,
                )
              } else {
                Text(
                  text = "Create Quiz",
                  style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                  fontWeight = FontWeight.Bold,
                  color = Color.Black,
                )
              }
            }
          }
        }

        // -------------------------------------------------------------
        // Main Quiz Body: Question Flow OR Results Card
        // -------------------------------------------------------------
        Column(
          modifier =
            Modifier.fillMaxWidth()
              .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          if (!isQuizComplete) {
            // Progress Bar & Count
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = "Question ${currentQuestionIndex + 1} of ${questions.size}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = glass.accentCyan,
                modifier = Modifier.testTag("quiz_progress_text"),
              )

              Text(
                text = "Score: $score",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = glass.statusSuccess,
                modifier = Modifier.testTag("quiz_current_score_text"),
              )
            }

            val progress = (currentQuestionIndex + 1).toFloat() / questions.size.coerceAtLeast(1)
            LinearProgressIndicator(
              progress = { progress },
              modifier = Modifier.fillMaxWidth().height(6.dp).clip(AppRadius.shapePill),
              color = glass.accentCyan,
              trackColor = if (glass.isDark) Color(0xFF1E293B) else Color(0xFFCBD5E1),
            )

            // Question Box Card
            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(14.dp))
                  .background(if (glass.isDark) Color(0xFF141C38) else Color.White)
                  .border(
                    AppBorder.hairline,
                    if (glass.isDark) Color(0x6600F2FE) else glass.borderSubtle,
                    RoundedCornerShape(14.dp),
                  )
                  .padding(16.dp),
            ) {
              Text(
                text = currentQuestion.question,
                style = MaterialTheme.typography.titleMedium.copy(lineHeight = 24.sp),
                fontWeight = FontWeight.SemiBold,
                color = glass.textPrimary,
                modifier = Modifier.testTag("quiz_question_text"),
              )
            }

            // Options Cards (A, B, C, D)
            Column(
              modifier = Modifier.fillMaxWidth(),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              currentQuestion.options.forEachIndexed { optIndex, optionText ->
                val optionLetter = ('A' + optIndex)
                val isSelected = selectedOptionIndex == optIndex
                val isCorrectAnswer = optIndex == currentQuestion.correctIndex

                val cardBg =
                  when {
                    isAnswerSubmitted && isCorrectAnswer -> Color(0x3310B981)
                    isAnswerSubmitted && isSelected && !isCorrectAnswer -> Color(0x33EF4444)
                    isSelected -> glass.accentCyan.copy(alpha = 0.20f)
                    else -> if (glass.isDark) Color(0xFF0F172E) else Color.White
                  }

                val cardBorder =
                  when {
                    isAnswerSubmitted && isCorrectAnswer -> Color(0xFF10B981)
                    isAnswerSubmitted && isSelected && !isCorrectAnswer -> Color(0xFFEF4444)
                    isSelected -> glass.accentCyan
                    else -> if (glass.isDark) Color(0xFF1E293B) else glass.borderSubtle
                  }

                Row(
                  modifier =
                    Modifier.fillMaxWidth()
                      .clip(RoundedCornerShape(12.dp))
                      .background(cardBg)
                      .border(1.5.dp, cardBorder, RoundedCornerShape(12.dp))
                      .clickable(enabled = !isAnswerSubmitted) {
                        selectedOptionIndex = optIndex
                        isAnswerSubmitted = true
                        if (optIndex == currentQuestion.correctIndex) {
                          score++
                        }
                      }
                      .padding(horizontal = 14.dp, vertical = 12.dp)
                      .testTag("quiz_option_$optIndex"),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  Box(
                    modifier =
                      Modifier.size(26.dp)
                        .clip(CircleShape)
                        .background(cardBorder.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                  ) {
                    Text(
                      text = "$optionLetter",
                      style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                      fontWeight = FontWeight.Bold,
                      color = cardBorder,
                    )
                  }

                  Text(
                    text = optionText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected || (isAnswerSubmitted && isCorrectAnswer)) FontWeight.Bold else FontWeight.Normal,
                    color = glass.textPrimary,
                    modifier = Modifier.weight(1f),
                  )
                }
              }
            }

            // Explanation Card displayed after selection
            if (isAnswerSubmitted) {
              Box(
                modifier =
                  Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x2600F2FE))
                    .border(1.dp, glass.accentCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
                    .testTag("quiz_explanation_box"),
              ) {
                Row(
                  verticalAlignment = Alignment.Top,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Lightbulb,
                    contentDescription = null,
                    tint = glass.accentCyan,
                    modifier = Modifier.size(16.dp).padding(top = 2.dp),
                  )
                  Column {
                    Text(
                      text = if (selectedOptionIndex == currentQuestion.correctIndex) "Correct! Shabaash!" else "Incorrect!",
                      style = MaterialTheme.typography.labelMedium,
                      fontWeight = FontWeight.Bold,
                      color = if (selectedOptionIndex == currentQuestion.correctIndex) Color(0xFF34D399) else Color(0xFFF87171),
                    )
                    Text(
                      text = currentQuestion.explanation,
                      style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                      color = glass.textPrimary,
                    )
                  }
                }
              }

              // Next Question / See Results Button
              Box(
                modifier =
                  Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                      Brush.horizontalGradient(
                        listOf(Color(0xFF00F2FE), Color(0xFF3B82F6))
                      )
                    )
                    .clickable {
                      if (currentQuestionIndex + 1 < questions.size) {
                        currentQuestionIndex++
                        selectedOptionIndex = null
                        isAnswerSubmitted = false
                      } else {
                        isQuizComplete = true
                      }
                    }
                    .padding(vertical = 12.dp)
                    .testTag("quiz_next_button"),
                contentAlignment = Alignment.Center,
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Text(
                    text = if (currentQuestionIndex + 1 < questions.size) "Next Question" else "See Final Score",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                  )
                  Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp),
                  )
                }
              }
            }
          } else {
            // ---------------------------------------------------------
            // Final Results Scorecard View
            // ---------------------------------------------------------
            Column(
              modifier = Modifier.fillMaxWidth().testTag("quiz_results_card"),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
              Box(
                modifier =
                  Modifier.size(110.dp)
                    .clip(CircleShape)
                    .background(
                      Brush.linearGradient(
                        listOf(Color(0xFF00F2FE), Color(0xFF8B5CF6))
                      )
                    ),
                contentAlignment = Alignment.Center,
              ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Icon(
                    imageVector = Icons.Outlined.EmojiEvents,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(36.dp),
                  )
                  Text(
                    text = "$score / ${questions.size}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.Black,
                  )
                }
              }

              val percentage = (score * 100) / questions.size.coerceAtLeast(1)
              val verdict =
                when {
                  percentage == 100 -> "🏆 100% Perfect Score! Shabaash!"
                  percentage >= 75 -> "🌟 Excellent Performance! Bahut badiya!"
                  percentage >= 50 -> "👍 Good Job! Keep learning!"
                  else -> "💪 Nice effort! Ek baar aur revise karein!"
                }

              Text(
                text = verdict,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = glass.accentCyan,
                textAlign = TextAlign.Center,
              )

              Text(
                text = "Aapne ${questions.size} mein se $score sawaalon ke sahi jawab diye ($percentage%).",
                style = MaterialTheme.typography.bodyMedium,
                color = glass.textSecondary,
                textAlign = TextAlign.Center,
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                // Retake Quiz Button
                Box(
                  modifier =
                    Modifier.weight(1f)
                      .clip(RoundedCornerShape(12.dp))
                      .background(glass.surfaceStandard)
                      .border(AppBorder.hairline, glass.borderSpecular, RoundedCornerShape(12.dp))
                      .clickable { restartCurrentQuiz() }
                      .padding(vertical = 12.dp)
                      .testTag("quiz_retake_button"),
                  contentAlignment = Alignment.Center,
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Outlined.Refresh,
                      contentDescription = null,
                      tint = glass.accentCyan,
                      modifier = Modifier.size(16.dp),
                    )
                    Text(
                      text = "Retake Quiz",
                      style = MaterialTheme.typography.labelMedium,
                      fontWeight = FontWeight.Bold,
                      color = glass.textPrimary,
                    )
                  }
                }

                // Close / Done Button
                Box(
                  modifier =
                    Modifier.weight(1f)
                      .clip(RoundedCornerShape(12.dp))
                      .background(
                        Brush.horizontalGradient(
                          listOf(Color(0xFF00F2FE), Color(0xFF3B82F6))
                        )
                      )
                      .clickable(role = Role.Button, onClick = onDismiss)
                      .padding(vertical = 12.dp)
                      .testTag("quiz_done_button"),
                  contentAlignment = Alignment.Center,
                ) {
                  Text(
                    text = "Done",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
