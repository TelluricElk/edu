package com.eduappml.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class QuizOption(val text: String, val correct: Boolean)
data class QuizQuestion(val question: String, val options: List<QuizOption>, val explanation: String)

/**
 * Встроенный тест из нескольких вопросов с одним правильным ответом,
 * мгновенной подсветкой и итоговым счётом. Используется на всех экранах
 * "Решение задачи" — один компонент на все алгоритмы.
 */
@Composable
fun QuizSection(
    questions: List<QuizQuestion>,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    var answers by remember(questions) { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    val answeredCount = answers.size
    val correctCount = answers.count { (qIdx, optIdx) -> questions[qIdx].options[optIdx].correct }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Проверьте себя",
            color = textColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        questions.forEachIndexed { qIndex, question ->
            QuizCard(
                index = qIndex,
                question = question,
                selectedOption = answers[qIndex],
                onSelect = { optIndex -> answers = answers.toMutableMap().apply { put(qIndex, optIndex) } }
            )
            Spacer(Modifier.height(12.dp))
        }

        if (answeredCount == questions.size && questions.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Результат теста: $correctCount из ${questions.size}",
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun QuizCard(
    index: Int,
    question: QuizQuestion,
    selectedOption: Int?,
    onSelect: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text(
            text = "${index + 1}. ${question.question}",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(10.dp))

        question.options.forEachIndexed { optIndex, option ->
            val isSelected = selectedOption == optIndex
            val showFeedback = selectedOption != null
            val bg = when {
                !showFeedback -> Color.White.copy(alpha = if (isSelected) 0.18f else 0.05f)
                option.correct -> Color(0xFF4CAF50).copy(alpha = 0.28f)
                isSelected && !option.correct -> Color(0xFFE53935).copy(alpha = 0.28f)
                else -> Color.White.copy(alpha = 0.05f)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .clickable(enabled = selectedOption == null) { onSelect(optIndex) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${('A' + optIndex)})  ${option.text}",
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 13.5.sp
                )
            }
        }

        if (selectedOption != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = question.explanation,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 12.5.sp
            )
        }
    }
}
