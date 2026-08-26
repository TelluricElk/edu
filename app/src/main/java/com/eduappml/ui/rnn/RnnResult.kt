package com.eduappml.ui.rnn

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

private val rnnQuiz = listOf(
    QuizQuestion(
        "Что такое скрытое состояние в RNN?",
        listOf(
            QuizOption("Вектор, хранящий «память» сети обо всём, что она видела в последовательности до текущего момента", true),
            QuizOption("Финальный ответ сети", false),
            QuizOption("Один из входных сигналов последовательности", false),
            QuizOption("Скорость обучения на текущем шаге", false)
        ),
        "Скрытое состояние обновляется на каждом шаге, комбинируя текущий вход с тем, что сеть «помнит» с предыдущих шагов."
    ),
    QuizQuestion(
        "Почему задачу с выключателем света нельзя решить сетью вроде CNN, смотрящей только на локальное окошко?",
        listOf(
            QuizOption("Финальное состояние зависит от ВСЕХ сигналов последовательности сразу, а не от каких-то нескольких соседних", true),
            QuizOption("CNN технически не может обрабатывать числа 0 и 1", false),
            QuizOption("На самом деле CNN справится ничуть не хуже RNN", false),
            QuizOption("Задача вообще не решается никакой нейросетью", false)
        ),
        "Чётность (или, как здесь, финальное состояние переключателя) — классический пример признака, требующего информации о произвольно далёком прошлом, а не о локальной окрестности."
    ),
    QuizQuestion(
        "Что такое затухающий градиент в контексте RNN?",
        listOf(
            QuizOption("Сигнал ошибки, проходя через много шагов времени назад, становится исчезающе малым, и ранние шаги почти перестают влиять на обучение", true),
            QuizOption("Постепенное уменьшение скорости обучения по расписанию", false),
            QuizOption("Способ намеренно упростить модель", false),
            QuizOption("Проблема, возникающая только при использовании ReLU", false)
        ),
        "Градиент, обратно распространяясь через много одинаковых шагов с производной tanh (всегда меньше 1), убывает экспоненциально с ростом длины последовательности."
    ),
    QuizQuestion(
        "Зачем были придуманы LSTM и GRU?",
        listOf(
            QuizOption("Чтобы решить проблему затухающего градиента и дать сети возможность помнить информацию на действительно длинных последовательностях", true),
            QuizOption("Чтобы полностью заменить понятие скрытого состояния", false),
            QuizOption("Чтобы ускорить работу простой RNN без изменения её возможностей", false),
            QuizOption("Это два разных названия одной и той же архитектуры, что и обычная RNN", false)
        ),
        "LSTM и GRU добавляют специальные «вентили», которые позволяют части информации проходить через много шагов времени почти без затухания."
    )
)

@Composable
fun RnnResult(modifier: Modifier = Modifier, title: String?, onBack: () -> Unit, onOpenChat: (String) -> Unit = {}) {
    val textColor = Color.White
    val accent = Color(0xFF6BCB77)
    val topicTitle = title ?: "Рекуррентная сеть"

    val shortNet = remember { RnnLab.train(length = 3, lr = 0.15f, epochs = 300) }
    val shortAcc = remember { RnnLab.accuracy(shortNet, RnnLab.testSet(3)) }
    val longNet = remember { RnnLab.train(length = 8, lr = 0.15f, epochs = 300) }
    val longAcc = remember { RnnLab.accuracy(longNet, RnnLab.testSet(8)) }

    LessonScaffold(
        eyebrow = "Решение задачи",
        title = title ?: "Рекуррентная сеть",
        onBack = onBack,
        accent = accent,
        modifier = modifier
    ) {
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Эталонное решение", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text("Одни и те же параметры обучения (300 эпох, learning rate 0,15), две разные длины последовательности:", color = textColor.copy(alpha = 0.85f), fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Text("Длина 3: точность ${(shortAcc * 100).roundToInt()}%", color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Длина 8: точность ${(longAcc * 100).roundToInt()}%", color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Разница — не случайность и не ошибка настройки, а прямое следствие затухающего градиента на длинных последовательностях.",
                    color = textColor.copy(alpha = 0.7f), fontSize = 12.5.sp
                )
                Spacer(Modifier.height(10.dp))
                AskChatButton(accent = accent, onClick = {
                    onOpenChat(
                        buildResultChatPrompt(
                            topicTitle,
                            "300 эпох, learning rate 0,15, длины последовательности 3 и 8",
                            "точность на длине 3 — ${(shortAcc * 100).roundToInt()}%, на длине 8 — ${(longAcc * 100).roundToInt()}%"
                        )
                    )
                })
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Полученные знания", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                listOf(
                    "RNN обрабатывает последовательность шаг за шагом, накапливая скрытое состояние.",
                    "Одни и те же веса используются на каждом шаге времени — разделение весов во времени.",
                    "Обучение идёт через BPTT — обратное распространение через развёрнутую по времени сеть.",
                    "Простая RNN плохо масштабируется на длинные последовательности из-за затухающего градиента — отсюда LSTM и GRU."
                ).forEach { Text("•  $it", color = textColor.copy(alpha = 0.85f), fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp)) }
            }
        }

        Spacer(Modifier.height(20.dp))
        QuizSection(questions = rnnQuiz, textColor = textColor, nodeId = "rnn")
    }
}
