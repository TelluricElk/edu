package com.eduappml.ui.fc

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

private val fcQuiz = listOf(
    QuizQuestion(
        "Почему сеть с одним нейроном в скрытом слое не может решить XOR-подобную задачу?",
        listOf(
            QuizOption("Она всё ещё способна провести только прямую (или почти прямую) границу решения", true),
            QuizOption("У неё недостаточно эпох обучения", false),
            QuizOption("Скорость обучения всегда слишком мала для одного нейрона", false),
            QuizOption("XOR вообще нельзя решить нейросетью", false)
        ),
        "Одного нейрона недостаточно, чтобы скомбинировать несколько «изгибов» в сложную границу — по факту это почти то же самое, что логистическая регрессия."
    ),
    QuizQuestion(
        "Что произойдёт, если убрать функцию активации (ReLU) между слоями?",
        listOf(
            QuizOption("Сеть любой глубины выродится в обычное линейное преобразование", true),
            QuizOption("Ничего не изменится, сеть будет работать так же", false),
            QuizOption("Сеть перестанет обучаться вообще", false),
            QuizOption("Обучение станет быстрее без потери качества", false)
        ),
        "Композиция линейных преобразований — снова линейное преобразование. Именно нелинейность между слоями даёт сети способность изгибать границу."
    ),
    QuizQuestion(
        "Что такое обратное распространение ошибки (backpropagation)?",
        listOf(
            QuizOption("Способ вычислить градиент ошибки по весам всех слоёв, передавая его от выхода к входу по цепному правилу", true),
            QuizOption("Отдельный алгоритм, независимый от градиентного спуска", false),
            QuizOption("Способ инициализации весов сети", false),
            QuizOption("Функция активации", false)
        ),
        "Backpropagation — это применение цепного правила дифференцирования для эффективного расчёта градиента по всем весам сети сразу, слой за слоем в обратном порядке."
    ),
    QuizQuestion(
        "Что показывает диаграмма архитектуры в интерактиве этой темы?",
        listOf(
            QuizOption("Силу обученных весов связей — толщина линии показывает величину веса", true),
            QuizOption("Скорость обучения каждого нейрона", false),
            QuizOption("Порядок, в котором нейроны обучались", false),
            QuizOption("Количество эпох обучения", false)
        ),
        "Более толстая и яркая линия связи означает больший по модулю вес — эта связь сильнее влияет на решение сети."
    )
)

@Composable
fun FcResult(modifier: Modifier = Modifier, title: String?, onBack: () -> Unit) {
    val textColor = Color.White
    val accent = Color(0xFFFF6B6B)

    val result = remember { FcLab.train(hiddenSize = 6, lr = 0.5f, epochs = 300) }
    val accuracy = remember { FcLab.accuracy(result.network, FcLab.testSet) }

    LessonScaffold(
        eyebrow = "Решение задачи",
        title = title ?: "Полносвязная сеть",
        onBack = onBack,
        accent = accent,
        modifier = modifier
    ) {
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Эталонное решение", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text("Параметры: 6 нейронов в скрытом слое, 300 эпох обучения.", color = textColor.copy(alpha = 0.85f), fontSize = 14.sp)
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
                    "Один нейрон в скрытом слое ограничен той же линейной границей, что и логистическая регрессия.",
                    "Нелинейность (ReLU) между слоями — единственная причина, почему несколько слоёв дают больше, чем один.",
                    "Backpropagation вычисляет градиент по всем весам сети через цепное правило.",
                    "Достаточное число нейронов позволяет сети приблизить сколь угодно сложную границу решения."
                ).forEach { Text("•  $it", color = textColor.copy(alpha = 0.85f), fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp)) }
            }
        }

        Spacer(Modifier.height(20.dp))
        QuizSection(questions = fcQuiz, textColor = textColor)
    }
}
