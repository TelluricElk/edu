package com.eduappml.ui.som

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

private val somQuiz = listOf(
    QuizQuestion(
        "Что такое нейрон-победитель (BMU)?",
        listOf(
            QuizOption("Нейрон сетки, чей весовой вектор ближе всего к текущему входному примеру", true),
            QuizOption("Нейрон, который обучался дольше всех остальных", false),
            QuizOption("Нейрон в центре сетки", false),
            QuizOption("Случайно выбранный нейрон на каждом шаге", false)
        ),
        "BMU находится через простой поиск ближайшего по расстоянию весового вектора среди всех нейронов сетки."
    ),
    QuizQuestion(
        "Чем SOM принципиально отличается от k-средних?",
        listOf(
            QuizOption("SOM обновляет не только победителя, но и его соседей по сетке, создавая топологический порядок", true),
            QuizOption("SOM не использует расстояния между точками", false),
            QuizOption("SOM работает только с изображениями", false),
            QuizOption("SOM не требует обучения вообще", false)
        ),
        "Именно обновление окрестности вокруг победителя — а не только самого победителя, как в k-средних — заставляет соседние нейроны становиться похожими друг на друга."
    ),
    QuizQuestion(
        "Зачем скорость обучения и радиус окрестности уменьшают со временем?",
        listOf(
            QuizOption("Чтобы сначала грубо распределить структуру, а затем аккуратно уточнить детали, не разрушая уже сложившийся порядок", true),
            QuizOption("Это чисто техническое требование, не влияющее на результат", false),
            QuizOption("Чтобы ускорить работу процессора", false),
            QuizOption("Чтобы гарантировать, что все нейроны станут одинаковыми", false)
        ),
        "Большие начальные значения быстро создают общую структуру карты, а последующее уменьшение позволяет доводить детали, не расшатывая уже найденный порядок."
    ),
    QuizQuestion(
        "Почему два запуска SOM с разной случайной инициализацией могут дать по-разному повёрнутые, но одинаково «правильные» карты?",
        listOf(
            QuizOption("Топологический порядок (что рядом с чем) сохраняется, но конкретное расположение зависит от случайного старта", true),
            QuizOption("Это означает, что алгоритм работает неправильно", false),
            QuizOption("SOM всегда даёт идентичный результат при любой инициализации", false),
            QuizOption("Расположение цветов на карте вообще не имеет значения", false)
        ),
        "SOM гарантирует локальный порядок («похожее — рядом»), но не единственное глобальное расположение — это нормальное свойство метода, не ошибка."
    )
)

@Composable
fun SomResult(modifier: Modifier = Modifier, title: String?, onBack: () -> Unit) {
    val textColor = Color.White
    val accent = Color(0xFFE63946)

    val map = remember { SomLab.trainUpTo(SomLab.DECAY_HORIZON, seed = 7) }
    val smoothness = remember { SomLab.averageNeighborDistance(map) }

    LessonScaffold(
        eyebrow = "Решение задачи",
        title = title ?: "Карта Кохонена",
        onBack = onBack,
        accent = accent,
        modifier = modifier
    ) {
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Эталонное решение", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cell = size.width / SomLab.GRID_SIZE
                        for (gx in 0 until SomLab.GRID_SIZE) {
                            for (gy in 0 until SomLab.GRID_SIZE) {
                                val w = map.weights[gx][gy]
                                drawRect(
                                    Color(w[0].coerceIn(0f, 1f), w[1].coerceIn(0f, 1f), w[2].coerceIn(0f, 1f)),
                                    topLeft = Offset(gx * cell, gy * cell),
                                    size = androidx.compose.ui.geometry.Size(cell + 1f, cell + 1f)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "После ${SomLab.DECAY_HORIZON} шагов обучения. Средняя разница соседних нейронов: ${"%.3f".format(smoothness)} (было ~0,65 при случайной инициализации).",
                    color = textColor.copy(alpha = 0.85f), fontSize = 13.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Полученные знания", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                listOf(
                    "SOM обновляет не только нейрон-победитель, но и его окрестность — это и создаёт топологический порядок.",
                    "Скорость обучения и радиус окрестности убывают со временем: сначала грубая структура, потом детали.",
                    "Результат зависит от случайной инициализации, но локальный порядок («похожее рядом») сохраняется всегда.",
                    "SOM — метод обучения без учителя: явных меток классов не требуется."
                ).forEach { Text("•  $it", color = textColor.copy(alpha = 0.85f), fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp)) }
            }
        }

        Spacer(Modifier.height(20.dp))
        QuizSection(questions = somQuiz, textColor = textColor)
    }
}
