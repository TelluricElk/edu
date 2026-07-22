package com.eduappml.ui.nb

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eduappml.ui.common.LessonScaffold
import com.eduappml.ui.common.QuizOption
import com.eduappml.ui.common.QuizQuestion
import com.eduappml.ui.common.QuizSection

private val nbQuiz = listOf(
    QuizQuestion(
        "В чём заключается «наивность» наивного Байеса?",
        listOf(
            QuizOption("В предположении, что признаки условно независимы при заданном классе", true),
            QuizOption("В том, что алгоритм не использует обучающую выборку", false),
            QuizOption("В том, что он не умеет считать вероятности", false),
            QuizOption("В отсутствии математического обоснования", false)
        ),
        "Реальные признаки почти всегда коррелируют, но допущение независимости сильно упрощает расчёты и часто работает достаточно хорошо."
    ),
    QuizQuestion(
        "Зачем в гауссовском наивном Байесе используют логарифмы вероятностей?",
        listOf(
            QuizOption("Чтобы избежать численного исчезновения при перемножении многих малых чисел", true),
            QuizOption("Логарифмы делают вычисления медленнее, но точнее", false),
            QuizOption("Это чисто эстетическое решение, на результат не влияет", false),
            QuizOption("Логарифмы нужны только для дискретных признаков", false)
        ),
        "Произведение множества вероятностей быстро становится числом, близким к нулю; сумма логарифмов решает эту проблему без потери информации о том, какой класс вероятнее."
    ),
    QuizQuestion(
        "Что оценивается по обучающей выборке для гауссовского наивного Байеса?",
        listOf(
            QuizOption("Среднее, дисперсия и априорная вероятность для каждого класса", true),
            QuizOption("Расстояния между всеми парами точек", false),
            QuizOption("Только количество объектов в выборке", false),
            QuizOption("Веса линейной модели", false)
        ),
        "Именно эти параметры полностью описывают предполагаемое нормальное распределение признаков внутри каждого класса."
    ),
    QuizQuestion(
        "Когда наивный Байес особенно хорошо подходит для задачи?",
        listOf(
            QuizOption("Когда признаков много, а данных немного — например, в классификации текста", true),
            QuizOption("Только когда признаки идеально независимы", false),
            QuizOption("Только для задач регрессии", false),
            QuizOption("Никогда, это устаревший алгоритм", false)
        ),
        "Наивный Байес быстро обучается и устойчив при большом числе признаков и ограниченном объёме данных — классический пример: фильтрация спама."
    )
)

@Composable
fun NbResult(modifier: Modifier = Modifier, title: String?, onBack: () -> Unit) {
    val textColor = Color.White
    val accent = Color(0xFF4D96FF)

    val stats = remember { NbLab.fitStats(NbLab.trainSet) }
    val accuracy = remember { NbLab.accuracy(stats, NbLab.testSet) }

    LessonScaffold(
        eyebrow = "Решение задачи",
        title = title ?: "Наивный Байес",
        onBack = onBack,
        accent = accent,
        modifier = modifier
    ) {
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Эталонное решение", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text("Гауссовский наивный Байес, параметры оценены по обучающей выборке.", color = textColor.copy(alpha = 0.85f), fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Точность на контрольной выборке: ${(accuracy * 100).toInt()}%",
                    color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Полученные знания", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                listOf(
                    "Наивный Байес выбирает класс с максимальной апостериорной вероятностью.",
                    "Предположение о независимости признаков почти никогда не верно, но часто не мешает.",
                    "Для непрерывных признаков обычно используют нормальное распределение внутри каждого класса.",
                    "Сглаживание решает проблему нулевой вероятности для редких сочетаний признаков."
                ).forEach { Text("•  $it", color = textColor.copy(alpha = 0.85f), fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp)) }
            }
        }

        Spacer(Modifier.height(20.dp))
        QuizSection(questions = nbQuiz, textColor = textColor)
    }
}
