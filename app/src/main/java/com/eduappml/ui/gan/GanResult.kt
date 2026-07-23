package com.eduappml.ui.gan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import com.eduappml.ui.common.LessonScaffold
import com.eduappml.ui.common.QuizOption
import com.eduappml.ui.common.QuizQuestion
import com.eduappml.ui.common.QuizSection
import kotlin.math.roundToInt

private val ganQuiz = listOf(
    QuizQuestion(
        "Что пытается максимизировать дискриминатор, а что — генератор?",
        listOf(
            QuizOption("Дискриминатор — точность различения реального и подделки; генератор — вероятность обмануть дискриминатора", true),
            QuizOption("Обе сети максимизируют одну и ту же величину одинаково", false),
            QuizOption("Генератор максимизирует точность дискриминатора", false),
            QuizOption("Дискриминатор не имеет собственной цели обучения", false)
        ),
        "Именно противоположность целей — минимаксная игра — и есть суть состязательного обучения."
    ),
    QuizQuestion(
        "Почему обучение GAN считается нестабильным?",
        listOf(
            QuizOption("Генератор оптимизируется относительно постоянно меняющегося дискриминатора — «целится в движущуюся мишень»", true),
            QuizOption("Потому что используется недостаточно данных", false),
            QuizOption("Потому что GAN никогда не используют градиентный спуск", false),
            QuizOption("Нестабильность — миф, на практике GAN обучаются так же гладко, как обычные сети", false)
        ),
        "В отличие от обычного обучения с учителем с одной фиксированной целью, здесь ландшафт функции потерь генератора постоянно меняется вместе с дискриминатором."
    ),
    QuizQuestion(
        "Что означает точность дискриминатора около 50% на смеси реальных и сгенерированных примеров?",
        listOf(
            QuizOption("Дискриминатор больше не может отличить подделку от оригинала лучше случайного гадания — признак хорошо обученного генератора", true),
            QuizOption("Модель полностью сломалась и ничему не научилась", false),
            QuizOption("Обучение остановилось", false),
            QuizOption("Генератор всегда выдаёт одну и ту же точку", false)
        ),
        "50% — это в точности то, что предсказывает теория GAN для идеального равновесия: дискриминатор угадывает не лучше подбрасывания монеты."
    ),
    QuizQuestion(
        "Что такое mode collapse (коллапс мод)?",
        listOf(
            QuizOption("Генератор находит один «удобный» вид подделки и перестаёт исследовать остальное разнообразие данных", true),
            QuizOption("Дискриминатор перестаёт обучаться", false),
            QuizOption("Обе сети одновременно достигают идеального качества", false),
            QuizOption("Это техническая ошибка, не связанная с самим алгоритмом GAN", false)
        ),
        "Вместо воспроизведения всего разнообразия реального распределения, генератор может «схитрить» и штамповать один и тот же (или очень похожие) удачно обманывающие дискриминатор примеры."
    )
)

@Composable
fun GanResult(modifier: Modifier = Modifier, title: String?, onBack: () -> Unit) {
    val textColor = Color.White
    val accent = Color(0xFF00C2A8)

    val gan = remember { GanLab.train(steps = 500, lrD = 0.05f, lrG = 0.05f) }
    val generated = remember(gan) { GanLab.generatedSample(gan.g, 120) }
    val discAcc = remember(gan) { GanLab.discriminatorAccuracy(gan) }

    LessonScaffold(
        eyebrow = "Решение задачи",
        title = title ?: "GAN",
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
                        .fillMaxWidth(0.6f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                        val w = size.width; val h = size.height
                        fun toPx(x: Float, y: Float) = Offset((x / GanLab.FEATURE_MAX) * w, h - (y / GanLab.FEATURE_MAX) * h)
                        GanLab.realSample.forEach { p -> drawCircle(Color(0xFF6BCB77).copy(alpha = 0.55f), radius = 4f, center = toPx(p.x, p.y)) }
                        generated.forEach { p -> drawCircle(Color(0xFF00C2A8), radius = 4f, center = toPx(p.x, p.y)) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Параметры: 500 шагов, скорость обучения дискриминатора и генератора — по 0,05.", color = textColor.copy(alpha = 0.85f), fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Точность дискриминатора при этом прогоне: ${(discAcc * 100).roundToInt()}%.",
                    color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Из-за нестабильности обучения GAN это число — не гарантированный результат, а иллюстрация одного конкретного прогона: при других шагах или скоростях обучения оно может быть заметно другим.",
                    color = textColor.copy(alpha = 0.65f), fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Полученные знания", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                listOf(
                    "GAN обучает две сети одновременно через противоположные цели — минимаксную игру.",
                    "Точность дискриминатора около 50% на смеси реального и сгенерированного — признак хорошо обученного генератора.",
                    "Обучение GAN структурно нестабильно: генератор оптимизируется относительно постоянно меняющегося дискриминатора.",
                    "Mode collapse — известная проблема, когда генератор жертвует разнообразием ради лёгкого обмана дискриминатора."
                ).forEach { Text("•  $it", color = textColor.copy(alpha = 0.85f), fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp)) }
            }
        }

        Spacer(Modifier.height(20.dp))
        QuizSection(questions = ganQuiz, textColor = textColor)
    }
}
