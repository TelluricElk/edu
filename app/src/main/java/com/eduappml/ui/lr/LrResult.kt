package com.eduappml.ui.lr

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eduappml.ui.common.LessonScaffold
import com.eduappml.ui.common.QuizOption
import com.eduappml.ui.common.QuizQuestion
import com.eduappml.ui.common.QuizSection

private val lrQuiz = listOf(
    QuizQuestion(
        "Что произойдёт, если скорость обучения слишком велика?",
        listOf(
            QuizOption("Модель может разойтись — веса будут расти без ограничений вместо схождения", true),
            QuizOption("Модель обучится мгновенно и точно", false),
            QuizOption("Ничего, скорость обучения не влияет на результат", false),
            QuizOption("MSE всегда станет равен нулю", false)
        ),
        "Слишком большой шаг заставляет веса «перепрыгивать» через минимум ошибки на каждой итерации, и вместо схождения ошибка растёт."
    ),
    QuizQuestion(
        "Зачем нужен метод наименьших квадратов, если есть градиентный спуск?",
        listOf(
            QuizOption("Он даёт точное аналитическое решение без итераций — быстрее и точнее для небольших данных", true),
            QuizOption("Он работает только для нелинейных моделей", false),
            QuizOption("Он всегда медленнее градиентного спуска", false),
            QuizOption("Он не связан с линейной регрессией", false)
        ),
        "Для линейной регрессии существует прямая формула (нормальное уравнение), которая находит оптимальные веса за один расчёт — без пошагового приближения."
    ),
    QuizQuestion(
        "Что показывает R² (коэффициент детерминации)?",
        listOf(
            QuizOption("Какую долю разброса целевой переменной объясняет модель (от 0 до 1)", true),
            QuizOption("Абсолютную ошибку в тех же единицах, что и цена", false),
            QuizOption("Число эпох, нужное для сходимости", false),
            QuizOption("Скорость обучения модели", false)
        ),
        "R² = 1 означает, что модель идеально объясняет данные, R² = 0 — что модель не лучше предсказания «среднее значение» для всех объектов."
    ),
    QuizQuestion(
        "Почему линейная регрессия плохо работает, если реальная зависимость нелинейна?",
        listOf(
            QuizOption("Модель по конструкции ищет только прямую линию — искривлённую зависимость она передать не может", true),
            QuizOption("Линейная регрессия автоматически превращается в полиномиальную", false),
            QuizOption("Это не так, линейная регрессия одинаково хороша для любых зависимостей", false),
            QuizOption("Проблема только в неправильной скорости обучения", false)
        ),
        "Линейная регрессия ограничена формой `w1·x + w0` — прямой линией. Для нелинейных зависимостей нужны другие модели или добавление нелинейных признаков."
    )
)

@Composable
fun LrResult(modifier: Modifier = Modifier, title: String?, onBack: () -> Unit) {
    val textColor = Color.White
    val accent = Color(0xFFFF6B6B)

    val (w1, w0) = remember { LrLab.closedFormFit() }
    val mse = remember { LrLab.mse(LrLab.testSet, w1, w0) }
    val r2 = remember { LrLab.r2(LrLab.testSet, w1, w0) }

    LessonScaffold(
        eyebrow = "Решение задачи",
        title = title ?: "Линейная регрессия",
        onBack = onBack,
        accent = accent,
        modifier = modifier
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Эталонное решение", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Уравнение прямой (метод наименьших квадратов): цена ≈ ${"%.2f".format(w1)}·площадь + ${"%.1f".format(w0)}",
                    color = textColor.copy(alpha = 0.85f), fontSize = 14.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "MSE на контрольной выборке: ${"%.1f".format(mse)}, R² = ${"%.3f".format(r2)}",
                    color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
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
                    "Линейная регрессия ищет прямую, минимизирующую сумму квадратов ошибок (MSE).",
                    "Для одного признака есть точное аналитическое решение — метод наименьших квадратов.",
                    "Градиентный спуск — итеративная альтернатива: полезна, когда аналитическое решение дорого считать.",
                    "Скорость обучения — компромисс между медленной сходимостью и риском разойтись."
                ).forEach { line ->
                    Text("•  $line", color = textColor.copy(alpha = 0.85f), fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        QuizSection(questions = lrQuiz, textColor = textColor)
    }
}
