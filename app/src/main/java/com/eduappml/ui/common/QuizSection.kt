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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eduappml.game.GameManager

data class QuizOption(val text: String, val correct: Boolean)
data class QuizQuestion(val question: String, val options: List<QuizOption>, val explanation: String)

/**
 * Встроенный тест из нескольких вопросов с одним правильным ответом,
 * мгновенной подсветкой и итоговым счётом. Используется на всех экранах
 * "Решение задачи" — один компонент на все алгоритмы.
 *
 * [nodeId] — id пузыря темы ("lr", "cnn", ...). Если он передан и тест решён
 * без ошибок (4 из 4), в обычном режиме открываются соседние пузыри графа
 * (см. GameManager.unlockNeighborsFor). В режиме разработчика ("god mode")
 * карта и так открыта целиком, поэтому разблокировка не вызывается.
 *
 * При неполном результате тест можно пройти заново кнопкой «Пройти заново» —
 * раньше варианты блокировались навсегда после первого клика и пересдать
 * тест было невозможно.
 */
@Composable
fun QuizSection(
    questions: List<QuizQuestion>,
    textColor: Color,
    modifier: Modifier = Modifier,
    nodeId: String? = null
) {
    val context = LocalContext.current

    // attempt — счётчик попыток: его смена сбрасывает ответы (кнопка «Пройти заново»).
    var attempt by remember(questions) { mutableStateOf(0) }
    var answers by remember(questions, attempt) { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var unlockedLabels by remember(questions) { mutableStateOf<List<String>?>(null) }

    val answeredCount = answers.size
    val correctCount = answers.count { (qIdx, optIdx) -> questions[qIdx].options[optIdx].correct }
    val finished = questions.isNotEmpty() && answeredCount == questions.size
    val passed = finished && correctCount == questions.size

    // Разблокировка соседей — ровно один раз на успешный проход теста.
    LaunchedEffect(passed, nodeId, attempt) {
        if (passed && nodeId != null && !GameManager.isGodMode()) {
            unlockedLabels = GameManager.unlockNeighborsFor(nodeId, context)
        }
    }

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

        if (finished) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Результат теста: $correctCount из ${questions.size}",
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(10.dp))

            if (passed) {
                UnlockBanner(
                    nodeId = nodeId,
                    unlockedLabels = unlockedLabels,
                    textColor = textColor
                )
            } else {
                Text(
                    text = "Чтобы открыть следующие пузыри, нужно ответить верно на все " +
                        "${questions.size} вопроса. Разберите пояснения выше и попробуйте ещё раз.",
                    color = textColor.copy(alpha = 0.75f),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(10.dp))
                RetryButton(onClick = { attempt++ })
            }
        }
    }
}

/** Плашка с итогом успешного прохождения и списком открывшихся пузырей. */
@Composable
private fun UnlockBanner(
    nodeId: String?,
    unlockedLabels: List<String>?,
    textColor: Color
) {
    val shape = RoundedCornerShape(14.dp)
    val accent = Color(0xFF4CAF50)

    val message = when {
        nodeId == null -> "Тест пройден полностью."
        GameManager.isGodMode() ->
            "Тест пройден полностью. В режиме разработчика карта открыта целиком."
        unlockedLabels == null -> "Тест пройден полностью."
        unlockedLabels.isNotEmpty() ->
            "Тест пройден полностью — открыты новые пузыри: ${unlockedLabels.joinToString(", ")}."
        else ->
            "Тест пройден полностью. Все соседние пузыри этой темы уже были открыты."
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(accent.copy(alpha = 0.16f))
            .border(1.dp, accent.copy(alpha = 0.45f), shape)
            .padding(14.dp)
    ) {
        Text(
            text = message,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun RetryButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.30f), shape)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Пройти заново",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
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
