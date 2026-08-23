package com.eduappml.ui.rl

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

private val rlQuizMilitary = listOf(
    QuizQuestion(
        "Что такое Q-значение Q(s, a)?",
        listOf(
            QuizOption("Оценка ожидаемой суммарной будущей награды при выборе действия a в состоянии s", true),
            QuizOption("Немедленная награда за один конкретный шаг", false),
            QuizOption("Число уже пройденных эпизодов", false),
            QuizOption("Вероятность того, что действие a доступно в состоянии s", false)
        ),
        "Q-значение — это не награда за один шаг, а прогноз на всё оставшееся будущее при условии, что дальше агент будет действовать оптимально."
    ),
    QuizQuestion(
        "Что произойдёт, если поставить ε (эпсилон) слишком большим?",
        listOf(
            QuizOption("Агент будет действовать почти случайно даже после того, как выучил хорошую стратегию", true),
            QuizOption("Обучение станет быстрее и точнее", false),
            QuizOption("Q-таблица перестанет обновляться", false),
            QuizOption("Агент никогда не сможет исследовать полигон", false)
        ),
        "Высокое ε означает, что агент почти всегда выбирает случайное действие вместо жадного (лучшего по Q) — прогресс в качестве пути замедляется или вовсе не виден."
    ),
    QuizQuestion(
        "Зачем вообще нужно исследование (exploration), если можно всегда выбирать действие с максимальным Q?",
        listOf(
            QuizOption("Иначе агент может навсегда застрять на посредственной, но случайно найденной первой стратегии, не узнав про лучшую", true),
            QuizOption("Исследование нужно только в самом первом эпизоде", false),
            QuizOption("Оно требуется исключительно для ускорения вычислений", false),
            QuizOption("Exploration и exploitation дают всегда одинаковый результат", false)
        ),
        "Без исследования агент никогда не попробует действия, которые кажутся сейчас хуже изученных, даже если на самом деле они ведут к лучшему результату."
    ),
    QuizQuestion(
        "Чем обучение с подкреплением принципиально отличается от алгоритмов, изученных раньше в этом приложении?",
        listOf(
            QuizOption("У него нет заранее размеченных «правильных ответов» — только сигнал награды от среды", true),
            QuizOption("Оно не использует никаких численных вычислений", false),
            QuizOption("Оно работает только с изображениями", false),
            QuizOption("Оно не может обучаться на маленьких данных", false)
        ),
        "В отличие от классификации или регрессии, где есть готовые пары «вход → правильный ответ», агент в RL сам должен через пробы и ошибки понять, какие действия хороши."
    )
)

/**
 * Военный вариант экрана "Решение задачи" для темы обучения с
 * подкреплением: та же логика, что у [RlResult] (который остаётся
 * нетронутым и больше не вызывается для id = "rl"), только со сценой
 * полигона с препятствиями вместо лабиринта.
 */
@Composable
fun RlResultMilitary(modifier: Modifier = Modifier, title: String?, onBack: () -> Unit, onOpenChat: (String) -> Unit = {}) {
    val textColor = Color.White
    val accent = Color(0xFF00B4D8)
    val topicTitle = title ?: "Обучение с подкреплением"

    val result = remember { RlLabMilitary.train(episodes = 150, alpha = 0.3f, epsilon = 0.2f) }
    val path = remember(result) { RlLabMilitary.greedyPath(result.q) }
    val reachedGoal = path.lastOrNull() == RlLabMilitary.GOAL

    LessonScaffold(
        eyebrow = "Решение задачи",
        title = title ?: "Обучение с подкреплением",
        onBack = onBack,
        accent = accent,
        modifier = modifier
    ) {
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Эталонное решение", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text("Параметры: 150 эпизодов, α = 0,3, ε = 0,2.", color = textColor.copy(alpha = 0.85f), fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                val resultText = if (reachedGoal) "Найденный путь: ${path.size - 1} шагов (оптимум для этого полигона — 14)."
                    else "Агент пока не нашёл устойчивый путь до пункта сбора с этими параметрами."
                Text(
                    resultText,
                    color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))
                AskChatButton(accent = accent, onClick = {
                    onOpenChat(
                        buildResultChatPrompt(
                            topicTitle,
                            "150 эпизодов, α = 0,3, ε = 0,2",
                            resultText
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
                    "Q-обучение оценивает не немедленную награду, а ожидаемую суммарную выгоду на будущее.",
                    "Баланс exploration/exploitation (параметр ε) критически влияет на скорость и качество обучения.",
                    "Агенту не нужны размеченные примеры — только сигнал награды от среды.",
                    "При достаточном числе эпизодов табличный Q-learning сходится к оптимальной политике."
                ).forEach { Text("•  $it", color = textColor.copy(alpha = 0.85f), fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp)) }
            }
        }

        Spacer(Modifier.height(20.dp))
        QuizSection(questions = rlQuizMilitary, textColor = textColor)
    }
}
