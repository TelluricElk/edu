package com.eduappml.ui.gb

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

private val gbQuiz = listOf(
    QuizQuestion(
        "На чём обучается каждое следующее дерево в градиентном бустинге?",
        listOf(
            QuizOption("На остатках (ошибках) суммы всех предыдущих деревьев", true),
            QuizOption("На тех же исходных метках y, что и первое дерево", false),
            QuizOption("На случайной подвыборке, как в случайном лесе", false),
            QuizOption("На предсказаниях самого себя с предыдущей эпохи", false)
        ),
        "Именно последовательное исправление остатков — ключевое отличие бустинга от bagging-ансамблей вроде случайного леса."
    ),
    QuizQuestion(
        "Чем градиентный бустинг принципиально отличается от случайного леса?",
        listOf(
            QuizOption("Бустинг строит деревья последовательно и исправляет ошибки, лес — параллельно и независимо усредняет", true),
            QuizOption("Бустинг не использует деревья решений", false),
            QuizOption("Лес всегда точнее бустинга", false),
            QuizOption("Разницы нет, это два названия одного алгоритма", false)
        ),
        "Случайный лес снижает разброс усреднением независимых деревьев, бустинг снижает смещение последовательным исправлением ошибок."
    ),
    QuizQuestion(
        "Как связаны learning rate и число итераций в бустинге?",
        listOf(
            QuizOption("Маленький learning rate обычно требует больше итераций, но даёт более устойчивый результат", true),
            QuizOption("Они никак не связаны между собой", false),
            QuizOption("Большой learning rate всегда лучше независимо от числа итераций", false),
            QuizOption("Число итераций автоматически подбирается по learning rate", false)
        ),
        "Learning rate масштабирует вклад каждого дерева — при маленьком шаге нужно больше шагов, чтобы дойти до того же результата, зато путь получается более плавным."
    ),
    QuizQuestion(
        "Что произойдёт при слишком большом числе итераций бустинга?",
        listOf(
            QuizOption("Ошибка на обучающей выборке продолжит падать, а на контрольной — начнёт расти (переобучение)", true),
            QuizOption("Модель автоматически остановится сама", false),
            QuizOption("Ошибка на контрольной выборке всегда будет падать вместе с обучающей", false),
            QuizOption("Ничего не изменится после определённого момента", false)
        ),
        "Каждая новая итерация всё точнее подгоняется под обучающую выборку, включая её шум — отсюда важность ранней остановки по контрольной ошибке."
    )
)

@Composable
fun GbResult(modifier: Modifier = Modifier, title: String?, onBack: () -> Unit) {
    val textColor = Color.White
    val accent = Color(0xFF00C2A8)

    val model = remember { GbLab.train(nEstimators = 40, learningRate = 0.15f) }
    val bestIdx = remember(model) { model.testMseHistory.indices.minByOrNull { model.testMseHistory[it] } ?: 0 }
    val bestTestMse = model.testMseHistory.getOrElse(bestIdx) { 0f }

    LessonScaffold(
        eyebrow = "Решение задачи",
        title = title ?: "Градиентный бустинг",
        onBack = onBack,
        accent = accent,
        modifier = modifier
    ) {
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Эталонное решение", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text("Параметры: n_estimators = 40, learning rate = 0,15.", color = textColor.copy(alpha = 0.85f), fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Лучшая контрольная MSE достигнута на итерации ${bestIdx + 1}: ${"%.1f".format(bestTestMse)}",
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
                    "Бустинг строит модели последовательно, каждая исправляет остатки предыдущих.",
                    "Learning rate масштабирует вклад каждого дерева в общий прогноз.",
                    "Слишком много итераций ведёт к переобучению — нужна ранняя остановка по контрольной выборке.",
                    "Даже «пеньки» (деревья глубины 1) в сумме способны приближать сложные зависимости."
                ).forEach { Text("•  $it", color = textColor.copy(alpha = 0.85f), fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp)) }
            }
        }

        Spacer(Modifier.height(20.dp))
        QuizSection(questions = gbQuiz, textColor = textColor)
    }
}
