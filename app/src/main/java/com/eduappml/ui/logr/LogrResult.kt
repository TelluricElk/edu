package com.eduappml.ui.logr

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

private val logrQuiz = listOf(
    QuizQuestion(
        "Что означает выход логистической регрессии, равный 0,8?",
        listOf(
            QuizOption("Модель оценивает вероятность класса 1 в 80%", true),
            QuizOption("Модель уверена на 80% в классе 0", false),
            QuizOption("Ошибка модели составляет 80%", false),
            QuizOption("80% объектов обучающей выборки принадлежат классу 1", false)
        ),
        "Сигмоида выдаёт именно вероятность принадлежности к положительному классу, а не какую-либо другую метрику."
    ),
    QuizQuestion(
        "Как изменение порога классификации влияет на recall?",
        listOf(
            QuizOption("Понижение порога обычно увеличивает recall (находим больше истинных положительных)", true),
            QuizOption("Порог никак не связан с recall", false),
            QuizOption("Recall всегда равен 1 независимо от порога", false),
            QuizOption("Повышение порога увеличивает recall", false)
        ),
        "При низком пороге модель чаще предсказывает класс 1, значит реже пропускает истинные положительные объекты — recall растёт, но обычно за счёт precision."
    ),
    QuizQuestion(
        "Почему для обучения логистической регрессии используют логарифмическое правдоподобие, а не MSE как в линейной регрессии?",
        listOf(
            QuizOption("Log-loss сильнее штрафует уверенные ошибки и даёт выпуклую функцию потерь для вероятностей", true),
            QuizOption("MSE вообще нельзя посчитать для вероятностей", false),
            QuizOption("Log-loss и MSE — это одно и то же для этой модели", false),
            QuizOption("Потому что так исторически сложилось, разницы нет", false)
        ),
        "Log-loss устроен так, что модель получает очень большой штраф, если уверенно предсказала неверный класс — это подходящее поведение для вероятностной классификации."
    ),
    QuizQuestion(
        "В каком случае имеет смысл сдвинуть порог классификации ниже 0,5?",
        listOf(
            QuizOption("Когда пропустить положительный случай (например, заболевание) дороже, чем ложная тревога", true),
            QuizOption("Никогда, порог всегда должен быть 0,5", false),
            QuizOption("Только если модель обучена неправильно", false),
            QuizOption("Когда классов в выборке поровну", false)
        ),
        "Порог — это инструмент управления компромиссом между ложноположительными и ложноотрицательными ошибками с учётом их реальной цены."
    )
)

@Composable
fun LogrResult(modifier: Modifier = Modifier, title: String?, onBack: () -> Unit) {
    val textColor = Color.White
    val accent = Color(0xFFFFD93D)

    val fit = remember { LogrLab.fit(0.3f, 300) }
    val cm = remember { LogrLab.confusionMatrix(LogrLab.testSet, fit.w, fit.b, 0.5f) }

    LessonScaffold(
        eyebrow = "Решение задачи",
        title = title ?: "Логистическая регрессия",
        onBack = onBack,
        accent = accent,
        modifier = modifier
    ) {
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Эталонное решение", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text("Порог 0,5, 300 эпох обучения.", color = textColor.copy(alpha = 0.85f), fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Accuracy на контрольной выборке: ${"%.0f".format(cm.accuracy * 100)}%, Precision = ${"%.2f".format(cm.precision)}, Recall = ${"%.2f".format(cm.recall)}",
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
                    "Логистическая регрессия предсказывает вероятность, а не жёсткий класс.",
                    "Сигмоида переводит любое число в диапазон (0, 1).",
                    "Порог классификации — независимый от обучения инструмент управления precision/recall.",
                    "Обучение минимизирует log-loss, который сильно штрафует уверенные ошибки."
                ).forEach { Text("•  $it", color = textColor.copy(alpha = 0.85f), fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp)) }
            }
        }

        Spacer(Modifier.height(20.dp))
        QuizSection(questions = logrQuiz, textColor = textColor)
    }
}
