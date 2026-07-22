package com.eduappml.ui.rf

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
import kotlin.math.roundToInt

private val rfQuiz = listOf(
    QuizQuestion(
        "Зачем каждому дереву в лесу нужна своя бутстрап-выборка?",
        listOf(
            QuizOption("Чтобы деревья ошибались по-разному — это делает их усреднённое предсказание устойчивее", true),
            QuizOption("Чтобы ускорить обучение каждого дерева", false),
            QuizOption("Бутстрап нужен только для больших датасетов", false),
            QuizOption("Без бутстрапа лес вообще не будет работать", false)
        ),
        "Если бы все деревья учились на одинаковых данных, они были бы почти идентичны — и голосование ничего бы не улучшило."
    ),
    QuizQuestion(
        "Почему увеличение числа деревьев (n_estimators) обычно не приводит к переобучению?",
        listOf(
            QuizOption("Усреднение снижает разброс предсказаний, а не подгоняет модель под конкретные шумные точки", true),
            QuizOption("Потому что каждое новое дерево обучается на всё меньшем количестве данных", false),
            QuizOption("Переобучение вообще невозможно в ансамблевых методах", false),
            QuizOption("Потому что деревья при этом становятся мельче", false)
        ),
        "В отличие от увеличения глубины одного дерева, добавление ещё одного дерева в лес просто добавляет ещё один голос — усреднение стабилизируется, а не переобучается."
    ),
    QuizQuestion(
        "Чем случайный выбор подмножества признаков в каждом узле помогает лесу?",
        listOf(
            QuizOption("Снижает корреляцию между деревьями — иначе они все выбирали бы один и тот же «сильный» признак", true),
            QuizOption("Ускоряет предсказание готовой модели", false),
            QuizOption("Гарантированно повышает точность на обучающей выборке", false),
            QuizOption("Заменяет собой бутстрап-выборку", false)
        ),
        "Без случайного выбора признаков деревья на разных бутстрап-выборках всё равно склонны находить одно и то же лучшее разбиение — снижая пользу от усреднения."
    ),
    QuizQuestion(
        "Что показывает важность признака (feature importance) в случайном лесу?",
        listOf(
            QuizOption("Насколько сильно в среднем этот признак уменьшал неоднородность узлов по всем деревьям", true),
            QuizOption("Корреляцию признака с другими признаками", false),
            QuizOption("Число уникальных значений признака", false),
            QuizOption("Порядок, в котором признак был добавлен в данные", false)
        ),
        "Чем чаще и эффективнее признак используется для разбиений во всех деревьях леса, тем выше его важность."
    )
)

@Composable
fun RfResult(modifier: Modifier = Modifier, title: String?, onBack: () -> Unit) {
    val textColor = Color.White
    val accent = Color(0xFF9D4EDD)

    val forest = remember { RfLab.trainForest(nTrees = 25, maxDepth = 5) }
    val accuracy = remember { RfLab.accuracy(forest, RfLab.testSet) }

    LessonScaffold(
        eyebrow = "Решение задачи",
        title = title ?: "Случайный лес",
        onBack = onBack,
        accent = accent,
        modifier = modifier
    ) {
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Эталонное решение", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text("Параметры: n_estimators = 25, max_depth = 5.", color = textColor.copy(alpha = 0.85f), fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Точность на контрольной выборке: ${(accuracy * 100).roundToInt()}%",
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
                    "Случайный лес — это bagging: множество деревьев на случайных подвыборках данных.",
                    "Усреднение голосов снижает дисперсию предсказаний по сравнению с одним деревом.",
                    "Случайный выбор признаков дополнительно уменьшает корреляцию между деревьями.",
                    "Рост числа деревьев стабилизирует точность, а не переобучает модель."
                ).forEach { Text("•  $it", color = textColor.copy(alpha = 0.85f), fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp)) }
            }
        }

        Spacer(Modifier.height(20.dp))
        QuizSection(questions = rfQuiz, textColor = textColor)
    }
}
