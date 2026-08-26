package com.eduappml.ui.lr

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

private val lrQuizMilitary = listOf(
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
            QuizOption("Абсолютную ошибку в тех же единицах, что и расход топлива", false),
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

/**
 * Военный вариант экрана "Решение задачи" для темы линейной регрессии: та же
 * логика, что у [LrResult] (который остаётся нетронутым и больше не
 * вызывается для id = "lr"), только с примером расхода топлива автоколонны
 * вместо цены квартиры.
 */
@Composable
fun LrResultMilitary(modifier: Modifier = Modifier, title: String?, onBack: () -> Unit, onOpenChat: (String) -> Unit = {}) {
    val textColor = Color.White
    val accent = Color(0xFFFF6B6B)

    val (w1, w0) = remember { LrLabMilitary.closedFormFit() }
    val mse = remember { LrLabMilitary.mse(LrLabMilitary.testSet, w1, w0) }
    val r2 = remember { LrLabMilitary.r2(LrLabMilitary.testSet, w1, w0) }

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
                    "Уравнение прямой (метод наименьших квадратов): расход ≈ ${"%.2f".format(w1)}·дальность + ${"%.1f".format(w0)}",
                    color = textColor.copy(alpha = 0.85f), fontSize = 14.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "MSE на контрольной выборке: ${"%.1f".format(mse)}, R² = ${"%.3f".format(r2)}",
                    color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))
                AskChatButton(accent = accent, onClick = {
                    onOpenChat(
                        "Объясни, пожалуйста, простыми словами, почему получился именно такой результат в теме «${title ?: "Линейная регрессия"}» (Решение задачи).\n\n" +
                        "Уравнение прямой: расход ≈ ${"%.2f".format(w1)}·дальность + ${"%.1f".format(w0)}\n" +
                        "MSE на контрольной выборке: ${"%.1f".format(mse)}, R² = ${"%.3f".format(r2)}\n\n" +
                        "Что означают эти числа и почему получились именно такими?"
                    )
                })
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
        QuizSection(questions = lrQuizMilitary, textColor = textColor, nodeId = "lr")
    }
}
