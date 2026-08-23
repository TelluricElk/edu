package com.eduappml.ui.gnn

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

private val gnnQuizMilitary = listOf(
    QuizQuestion(
        "Почему в этой задаче нельзя обойтись обычной полносвязной сетью, глядя на признаки каждого узла по отдельности?",
        listOf(
            QuizOption("Признаки узлов случайны и не связаны с группой — узнать её можно только через структуру связей", true),
            QuizOption("Полносвязная сеть технически не может работать с числами", false),
            QuizOption("На самом деле FC-сеть решила бы эту задачу ничуть не хуже GNN", false),
            QuizOption("Задача вообще не может быть решена нейросетью", false)
        ),
        "Сама постановка задачи специально устроена так, что вся полезная информация — в графе связей, а не в исходных признаках узлов."
    ),
    QuizQuestion(
        "Что происходит с информацией после двух раундов обмена сообщениями?",
        listOf(
            QuizOption("Узел неявно получает информацию не только от прямых соседей, но и от соседей своих соседей", true),
            QuizOption("Информация остаётся точно такой же, как после одного раунда", false),
            QuizOption("Узел забывает информацию о себе самом", false),
            QuizOption("Граф перестраивается заново", false)
        ),
        "Поскольку каждый сосед на первом раунде уже впитал информацию от своих соседей, второй раунд агрегации распространяет её на расстояние в два шага."
    ),
    QuizQuestion(
        "Что такое пересглаживание (over-smoothing)?",
        listOf(
            QuizOption("При слишком большом числе раундов представления разных узлов становятся всё более похожими друг на друга, теряя различия", true),
            QuizOption("Технический сбой при обучении, не связанный с архитектурой", false),
            QuizOption("Способ ускорить обучение GNN", false),
            QuizOption("Проблема, которая возникает только при одном раунде обмена сообщениями", false)
        ),
        "Каждый дополнительный раунд усреднения дополнительно «размывает» индивидуальные различия узлов — при большом числе раундов все узлы связного графа начинают выглядеть похоже."
    ),
    QuizQuestion(
        "Почему при обратном распространении градиент для узла v учитывает не только его собственный путь вперёд, но и то, что v — сосед других узлов?",
        listOf(
            QuizOption("Потому что представление v на предыдущем слое участвовало в агрегации для всех узлов, где v является соседом", true),
            QuizOption("Это лишняя, ничего не значащая деталь реализации", false),
            QuizOption("Потому что граф всегда ориентированный", false),
            QuizOption("Такого дополнительного вклада на самом деле нет", false)
        ),
        "Поскольку граф неориентирован, если v — сосед w, то w тоже влияет на v, и это двустороннее влияние должно быть учтено и при обучении."
    )
)

/**
 * Военный вариант экрана "Решение задачи" для темы GNN: та же логика, что
 * у [GnnResult] (который остаётся нетронутым и больше не вызывается для
 * id = "gnn"), только со сценой сети радиосвязи вместо социальной сети.
 */
@Composable
fun GnnResultMilitary(modifier: Modifier = Modifier, title: String?, onBack: () -> Unit, onOpenChat: (String) -> Unit = {}) {
    val textColor = Color.White
    val accent = Color(0xFFB5179E)
    val topicTitle = title ?: "Графовая сеть"

    val model = remember { GnnLabMilitary.train(numLayers = 2, hidden = 4, lr = 0.03f, epochs = 250) }
    val accuracy = remember { GnnLabMilitary.accuracy(model) }

    LessonScaffold(
        eyebrow = "Решение задачи",
        title = title ?: "Графовая сеть",
        onBack = onBack,
        accent = accent,
        modifier = modifier
    ) {
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Эталонное решение", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text("Параметры: 2 раунда обмена сообщениями, скорость обучения 0,03, 250 эпох.", color = textColor.copy(alpha = 0.85f), fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Точность классификации групп: ${(accuracy * 100).roundToInt()}%",
                    color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Два раунда — тот самый баланс: достаточно, чтобы увидеть структуру групп, но ещё не настолько много, чтобы всё «размыть».",
                    color = textColor.copy(alpha = 0.7f), fontSize = 12.5.sp
                )
                Spacer(Modifier.height(10.dp))
                AskChatButton(accent = accent, onClick = {
                    onOpenChat(
                        buildResultChatPrompt(
                            topicTitle,
                            "2 раунда обмена сообщениями, скорость обучения 0,03, 250 эпох",
                            "точность классификации групп ${(accuracy * 100).roundToInt()}%"
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
                    "GNN распространяет информацию по графу через раунды обмена сообщениями между соседями.",
                    "Веса каждого слоя — общие для всех узлов, аналогично разделяемым весам в CNN и RNN.",
                    "Число раундов — компромисс: мало — не хватает контекста, много — пересглаживание.",
                    "Обратное распространение в GNN учитывает двустороннюю роль каждого узла: как себя самого и как чужого соседа."
                ).forEach { Text("•  $it", color = textColor.copy(alpha = 0.85f), fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp)) }
            }
        }

        Spacer(Modifier.height(20.dp))
        QuizSection(questions = gnnQuizMilitary, textColor = textColor)
    }
}
