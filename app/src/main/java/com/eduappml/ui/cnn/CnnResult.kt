package com.eduappml.ui.cnn

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

private val cnnQuiz = listOf(
    QuizQuestion(
        "Что такое «разделяемые веса» в свёрточном слое?",
        listOf(
            QuizOption("Один и тот же фильтр применяется ко всем позициям изображения, а не имеет отдельного веса на каждый пиксель", true),
            QuizOption("Веса, которые случайно перемешиваются между слоями", false),
            QuizOption("Веса, общие для всех разных сетей одновременно", false),
            QuizOption("Отдельные веса для каждого пикселя, как в полносвязном слое", false)
        ),
        "Именно разделяемые веса резко уменьшают число параметров и дают инвариантность к сдвигу — сеть узнаёт признак в любом месте изображения."
    ),
    QuizQuestion(
        "Зачем нужен max-pooling после свёртки?",
        listOf(
            QuizOption("Уменьшает размер карты признаков и снижает чувствительность к точному положению признака", true),
            QuizOption("Увеличивает число параметров сети", false),
            QuizOption("Заменяет собой функцию активации", false),
            QuizOption("Нужен только для ускорения обучения, на качество не влияет", false)
        ),
        "Взятие максимума по маленькой области сохраняет «сработал ли признак где-то рядом», не требуя точной позиции."
    ),
    QuizQuestion(
        "Что произойдёт при обратном проходе через max-pooling?",
        listOf(
            QuizOption("Градиент передаётся только в ту позицию, которая была максимумом при прямом проходе, остальные получают ноль", true),
            QuizOption("Градиент делится поровну между всеми позициями в окне", false),
            QuizOption("Градиент удваивается для каждой позиции", false),
            QuizOption("Max-pooling не участвует в обратном распространении", false)
        ),
        "Раз только одно значение «победило» при выборе максимума, только оно и получает вклад в обучение на этом шаге."
    ),
    QuizQuestion(
        "Почему CNN хорошо работает именно с изображениями, но не даёт преимущества на произвольных табличных данных?",
        listOf(
            QuizOption("CNN использует пространственную связность соседних пикселей — там, где такой связности нет, разделяемые веса не помогают", true),
            QuizOption("CNN технически неспособна обрабатывать никакие данные, кроме изображений", false),
            QuizOption("Табличные данные всегда слишком велики для CNN", false),
            QuizOption("Разницы между CNN и FC для любых данных нет", false)
        ),
        "Сила CNN — в встроенном предположении о локальной пространственной структуре данных; для перемешанных табличных признаков такого предположения попросту нет."
    )
)

@Composable
fun CnnResult(modifier: Modifier = Modifier, title: String?, onBack: () -> Unit) {
    val textColor = Color.White
    val accent = Color(0xFFFFD93D)

    val net = remember { CnnLab.train(nFilters = 4, lr = 0.05f, epochs = 20) }
    val accuracy = remember { CnnLab.accuracy(net, CnnLab.testSet) }

    LessonScaffold(
        eyebrow = "Решение задачи",
        title = title ?: "Свёрточная сеть",
        onBack = onBack,
        accent = accent,
        modifier = modifier
    ) {
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Эталонное решение", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text("Параметры: 4 фильтра 3×3, 20 эпох обучения.", color = textColor.copy(alpha = 0.85f), fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Точность на контрольной выборке (другие позиции линий, чем при обучении): ${(accuracy * 100).roundToInt()}%",
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
                    "Свёрточный слой использует разделяемые веса — один фильтр применяется по всему изображению.",
                    "Max-pooling сжимает карту признаков, сохраняя главное и снижая чувствительность к точной позиции.",
                    "Обученные фильтры генерализуются на новые позиции признака, которых не было в обучающей выборке.",
                    "Преимущество CNN проявляется именно там, где у данных есть пространственная структура."
                ).forEach { Text("•  $it", color = textColor.copy(alpha = 0.85f), fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp)) }
            }
        }

        Spacer(Modifier.height(20.dp))
        QuizSection(questions = cnnQuiz, textColor = textColor)
    }
}
