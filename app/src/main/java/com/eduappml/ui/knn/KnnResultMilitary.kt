package com.eduappml.ui.knn

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

/**
 * Военный вариант экрана "Решение задачи" для темы k-NN: та же логика, что
 * у приватного KnnResult внутри ResultScreen.kt (который остаётся нетронутым
 * и больше не вызывается для id = "knn"), только с примером классификации
 * воздушной цели вместо классификации фруктов.
 */
@Composable
fun KnnResultMilitary(modifier: Modifier = Modifier, title: String?, onBack: () -> Unit, onOpenChat: (String) -> Unit = {}) {
    val textColor = Color.White
    val accuracy = remember {
        KnnLabMilitary.evaluateAccuracy(KnnLabMilitary.referenceK, KnnLabMilitary.referenceMetric, KnnLabMilitary.referenceWeighting)
    }
    val topicTitle = title ?: "k-NN"

    LessonScaffold(
        eyebrow = "Решение задачи",
        title = title ?: "k-NN",
        onBack = onBack,
        accent = Color(0xFFE53935),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Эталонное решение", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                val paramsText = "k = ${KnnLabMilitary.referenceK}, метрика — ${KnnLabMilitary.referenceMetric.label}, " +
                    "взвешивание — ${KnnLabMilitary.referenceWeighting.label}."
                Text(
                    text = "Параметры: $paramsText",
                    color = textColor.copy(alpha = 0.85f),
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(4.dp))
                val metricText = "точность на контрольной выборке ${(accuracy * 100).roundToInt()}%"
                Text(
                    text = "Точность на контрольной выборке: ${(accuracy * 100).roundToInt()}%",
                    color = textColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))
                AskChatButton(
                    accent = Color(0xFFE53935),
                    onClick = { onOpenChat(buildResultChatPrompt(topicTitle, paramsText, metricText)) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Полученные знания", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                listOf(
                    "k-NN не строит модель заранее — он «ленивый» алгоритм, всё считает в момент предсказания.",
                    "Маленькое k делает модель чувствительной к шуму, большое — сглаживает границы между классами.",
                    "Взвешивание по расстоянию снижает влияние дальних, менее похожих контактов.",
                    "Выбор метрики расстояния имеет значение, особенно если признаки в разных масштабах."
                ).forEach { line ->
                    Text("•  $line", color = textColor.copy(alpha = 0.85f), fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        QuizSection(questions = knnQuizMilitary, textColor = textColor)
    }
}

private val knnQuizMilitary = listOf(
    QuizQuestion(
        question = "Что произойдёт с моделью, если выбрать k = 1?",
        options = listOf(
            QuizOption("Модель станет очень чувствительна к шуму и нетипичным отметкам", true),
            QuizOption("Модель всегда даст 100% точность на новых данных", false),
            QuizOption("Модель перестанет учитывать обучающую выборку", false),
            QuizOption("Расстояния между точками перестанут считаться", false)
        ),
        explanation = "При k = 1 класс определяется единственным ближайшим соседом, поэтому одна нетипичная или ошибочно размеченная отметка может полностью изменить предсказание."
    ),
    QuizQuestion(
        question = "Что произойдёт при слишком большом k (близком к размеру всей выборки)?",
        options = listOf(
            QuizOption("Модель почти всегда будет предсказывать самый частый класс", true),
            QuizOption("Модель станет точнее для редких классов", false),
            QuizOption("Алгоритм перестанет работать", false),
            QuizOption("Точность гарантированно вырастет до 100%", false)
        ),
        explanation = "Голосование среди слишком многих соседей сглаживает границы между классами — в пределе модель просто выдаёт самый распространённый класс в выборке."
    ),
    QuizQuestion(
        question = "Почему k-NN называют «ленивым» алгоритмом?",
        options = listOf(
            QuizOption("Он не строит модель заранее, а хранит все данные и считает всё во время предсказания", true),
            QuizOption("Он работает медленно на любых объёмах данных", false),
            QuizOption("Он никогда не достигает высокой точности", false),
            QuizOption("Его нельзя использовать для регрессии", false)
        ),
        explanation = "В отличие от линейной регрессии или деревьев, k-NN не «обучает» параметры заранее — вся работа (поиск соседей) откладывается до момента предсказания."
    ),
    QuizQuestion(
        question = "Как взвешивание по расстоянию (distance weighting) отличается от равного голосования?",
        options = listOf(
            QuizOption("Более близкие соседи получают больший вес голоса, чем дальние", true),
            QuizOption("Учитываются только соседи одного класса", false),
            QuizOption("Голос имеют только k/2 ближайших соседей", false),
            QuizOption("Все соседи по умолчанию имеют одинаковый вес независимо от настройки", false)
        ),
        explanation = "При взвешивании по расстоянию вклад соседа в голосование обратно пропорционален расстоянию до него — близкие контакты влияют сильнее дальних."
    )
)
