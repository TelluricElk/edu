package com.eduappml.ui.ae

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

private val aeQuiz = listOf(
    QuizQuestion(
        "Почему автокодировщик считается обучением без учителя?",
        listOf(
            QuizOption("Потому что вход одновременно служит и данными, и «правильным ответом» — размеченные метки не нужны", true),
            QuizOption("Потому что он вообще не использует градиентный спуск", false),
            QuizOption("Потому что энкодер и декодер обучаются раздельно", false),
            QuizOption("Автокодировщик — это метод обучения с учителем", false)
        ),
        "Сеть учится восстанавливать собственный вход — никакая внешняя разметка для этого не требуется."
    ),
    QuizQuestion(
        "Что произойдёт, если размер узкого горлышка меньше истинной размерности данных?",
        listOf(
            QuizOption("Ошибка реконструкции не сможет опуститься до нуля, сколько бы сеть ни обучалась", true),
            QuizOption("Сеть обучится ещё быстрее и точнее", false),
            QuizOption("Ничего не изменится по сравнению с большим горлышком", false),
            QuizOption("Обучение вообще остановится", false)
        ),
        "Если данные требуют минимум d чисел для точного описания, а горлышко меньше d, часть информации неизбежно теряется."
    ),
    QuizQuestion(
        "Чем вариационный автокодировщик (VAE) отличается от обычного?",
        listOf(
            QuizOption("VAE сжимает вход в распределение, а не в одну точку кода, что позволяет генерировать новые примеры", true),
            QuizOption("VAE вообще не использует декодер", false),
            QuizOption("VAE не может восстанавливать данные, только сжимать", false),
            QuizOption("Разницы между VAE и обычным автокодировщиком нет", false)
        ),
        "Вероятностное, гладкое устройство скрытого пространства VAE — именно то, что позволяет брать случайную точку и получать осмысленный результат при декодировании."
    ),
    QuizQuestion(
        "Зачем в архитектуре энкодера и декодера нужна нелинейность (ReLU) на скрытых слоях?",
        listOf(
            QuizOption("Без неё сеть могла бы выучить только линейное сжатие, эквивалентное классическому методу главных компонент (PCA)", true),
            QuizOption("Нелинейность нужна только для ускорения вычислений", false),
            QuizOption("ReLU нужен исключительно в декодере, но не в энкодере", false),
            QuizOption("Нелинейность не влияет на качество сжатия", false)
        ),
        "Так же, как в теме «Полносвязная сеть» — без нелинейности между слоями композиция линейных преобразований остаётся линейной, а значит, не сложнее PCA."
    )
)

@Composable
fun AeResult(modifier: Modifier = Modifier, title: String?, onBack: () -> Unit) {
    val textColor = Color.White
    val accent = Color(0xFFFF914D)

    val ae = remember { AeLab.train(bottleneck = 1, lr = 0.15f, epochs = 300) }
    val mse = remember { AeLab.mse(ae, AeLab.testSet) }

    LessonScaffold(
        eyebrow = "Решение задачи",
        title = title ?: "Автокодировщик",
        onBack = onBack,
        accent = accent,
        modifier = modifier
    ) {
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Эталонное решение", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text("Параметры: горлышко = 1 число, 300 эпох обучения.", color = textColor.copy(alpha = 0.85f), fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Ошибка реконструкции (MSE) на контрольной выборке: ${"%.4f".format(mse)}",
                    color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Даже сжимая пару чисел (X, Y) до одного, сеть восстанавливает положение точки на дуге с небольшой ошибкой — потому что дуга по сути одномерна.",
                    color = textColor.copy(alpha = 0.7f), fontSize = 12.5.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Полученные знания", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                listOf(
                    "Автокодировщик учится сжимать и восстанавливать данные без разметки — сам вход служит целью обучения.",
                    "Размер узкого горлышка определяет, сколько информации сеть обязана уместить.",
                    "Качество сжатия зависит от истинной внутренней размерности данных, а не только от архитектуры сети.",
                    "Вариационная версия (VAE) превращает автокодировщик в генеративную модель."
                ).forEach { Text("•  $it", color = textColor.copy(alpha = 0.85f), fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp)) }
            }
        }

        Spacer(Modifier.height(20.dp))
        QuizSection(questions = aeQuiz, textColor = textColor)
    }
}
