package com.eduappml.ui.dm

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eduappml.ui.common.AskChatButton
import com.eduappml.ui.common.LessonScaffold
import com.eduappml.ui.common.QuizOption
import com.eduappml.ui.common.QuizQuestion
import com.eduappml.ui.common.QuizSection
import com.eduappml.ui.common.buildResultChatPrompt
import kotlin.math.roundToInt

private val dmQuizMilitary = listOf(
    QuizQuestion(
        "Почему прямой процесс диффузии можно описать точной формулой, а обратный — обычно нельзя?",
        listOf(
            QuizOption("Прямой процесс — это просто добавление известного заранее шума; обратный требует знать распределение данных, которое обычно неизвестно", true),
            QuizOption("На самом деле оба процесса всегда точны и не требуют обучения", false),
            QuizOption("Прямой процесс сложнее обратного", false),
            QuizOption("Разницы между прямым и обратным процессом нет", false)
        ),
        "Добавить шум легко и не требует знания о данных; убрать его правильно требует понимания того, как выглядят настоящие данные — а это и есть то, что обучается."
    ),
    QuizQuestion(
        "Что такое score-функция?",
        listOf(
            QuizOption("Градиент логарифма плотности вероятности — направление, в котором данные становятся более вероятными", true),
            QuizOption("Итоговая оценка качества сгенерированного изображения", false),
            QuizOption("Число шагов денойзинга", false),
            QuizOption("Функция, измеряющая скорость обучения модели", false)
        ),
        "Именно score указывает, в какую сторону нужно немного сдвинуть точку на каждом шаге обратного процесса, чтобы она стала больше похожа на настоящие данные."
    ),
    QuizQuestion(
        "Почему в этом интерактиве обратный процесс можно показать честно, без обучения нейросети?",
        listOf(
            QuizOption("Потому что данные — известная заранее смесь гауссиан, для которой score вычисляется точно по формуле", true),
            QuizOption("Потому что диффузионные модели вообще никогда не требуют обучения", false),
            QuizOption("Потому что обратный процесс на самом деле не нужен для генерации", false),
            QuizOption("Потому что нейросеть здесь всё равно тайно обучается за кулисами", false)
        ),
        "Это единственный случай, когда точная математика доступна без обучения — для реальных данных (изображений, звука) распределение неизвестно, и score обязательно нужно аппроксимировать сетью."
    ),
    QuizQuestion(
        "Почему увеличение числа шагов денойзинга обычно улучшает качество результата?",
        listOf(
            QuizOption("Более мелкая дискретизация точнее следует за истинной траекторией от шума к данным", true),
            QuizOption("Большее число шагов ничего не меняет, кроме времени вычислений", false),
            QuizOption("После определённого числа шагов результат обязательно ухудшается", false),
            QuizOption("Число шагов влияет только на скорость, но не на итоговое качество", false)
        ),
        "Каждый шаг — это приближённый (дискретный) шаг вдоль непрерывной траектории; чем мельче шаги, тем меньше ошибка дискретизации и тем точнее итоговый результат."
    )
)

/**
 * Военный вариант экрана "Решение задачи" для темы диффузионной модели: та
 * же логика, что у [DmResult] (который остаётся нетронутым и больше не
 * вызывается для id = "dm"), только со сценой восстановления
 * аэрофотоснимка вместо старой фотографии.
 */
@Composable
fun DmResultMilitary(modifier: Modifier = Modifier, title: String?, onBack: () -> Unit, onOpenChat: (String) -> Unit = {}) {
    val textColor = Color.White
    val accent = Color(0xFF9D4EDD)
    val topicTitle = title ?: "Диффузионная модель"

    val samples = remember { DmLabMilitary.reverseSample(nPoints = 200, nSteps = 50) }
    val convergence = remember { DmLabMilitary.convergenceRatio(samples) }

    LessonScaffold(
        eyebrow = "Решение задачи",
        title = title ?: "Диффузионная модель",
        onBack = onBack,
        accent = accent,
        modifier = modifier
    ) {
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Эталонное решение", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                        val w = size.width; val h = size.height
                        fun toPx(x: Float, y: Float) = Offset((x / 8f + 1f) / 2f * w, h - (y / 8f + 1f) / 2f * h)
                        DmLabMilitary.realSample.forEach { p -> drawCircle(Color.White.copy(alpha = 0.12f), radius = 3f, center = toPx(p.x, p.y)) }
                        samples.forEach { p -> drawCircle(Color(0xFF9D4EDD), radius = 3.5f, center = toPx(p.x, p.y)) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Параметры: 50 шагов детерминированного обратного процесса, точная score-функция.", color = textColor.copy(alpha = 0.85f), fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Доля точек, дошедших до одного из двух облаков: ${(convergence * 100).roundToInt()}%",
                    color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))
                AskChatButton(accent = accent, onClick = {
                    onOpenChat(
                        buildResultChatPrompt(
                            topicTitle,
                            "50 шагов детерминированного обратного процесса, точная score-функция",
                            "доля точек, дошедших до одного из двух облаков ${(convergence * 100).roundToInt()}%"
                        )
                    )
                })
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Полученные знания", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                listOf(
                    "Прямой процесс диффузии — точная, заранее известная формула зашумления.",
                    "Обратный процесс двигается по score-функции — градиенту логарифма плотности данных.",
                    "В реальных моделях score аппроксимирует обученная нейросеть; здесь — точная формула для известного распределения.",
                    "Число шагов денойзинга напрямую влияет на качество результата — это настоящее, не выдуманное свойство диффузионных моделей."
                ).forEach { Text("•  $it", color = textColor.copy(alpha = 0.85f), fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp)) }
            }
        }

        Spacer(Modifier.height(20.dp))
        QuizSection(questions = dmQuizMilitary, textColor = textColor)
    }
}
