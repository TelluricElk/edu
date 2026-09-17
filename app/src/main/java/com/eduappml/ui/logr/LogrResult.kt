package com.eduappml.ui.logr

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

private val logrQuiz = listOf(
    QuizQuestion(
        "Почтовый шлюз выдал для письма оценку 0,8. Что это означает?",
        listOf(
            QuizOption("Модель оценивает вероятность того, что письмо фишинговое, в 80%", true),
            QuizOption("Модель уверена на 80%, что письмо легитимно", false),
            QuizOption("Письмо совпало с сигнатурой фишинга на 80%", false),
            QuizOption("80% писем в обучающей выборке были фишингом", false)
        ),
        "Сигмоида выдаёт именно оценку вероятности принадлежности к положительному классу. " +
            "Будет ли письмо заблокировано, решает уже отдельное правило — сравнение с порогом."
    ),
    QuizQuestion(
        "На контрольной выборке модель показала accuracy 0,91. Почему по одному этому числу нельзя судить о качестве детектора?",
        listOf(
            QuizOption(
                "Классы несбалансированы: модель, пропускающая вообще всё, набрала бы около 0,68 при нулевом recall",
                true
            ),
            QuizOption("Accuracy всегда завышена на контрольной выборке", false),
            QuizOption("Accuracy нельзя считать для вероятностных моделей", false),
            QuizOption("0,91 — слишком мало, нужно не меньше 0,99", false)
        ),
        "Две трети писем легитимны, поэтому большую часть accuracy обеспечивает тривиальное " +
            "поведение «ничего не блокировать». В реальном шлюзе, где фишинга доли процента, " +
            "этот эффект ещё сильнее. Смотреть нужно на матрицу ошибок, precision, recall и стоимость."
    ),
    QuizQuestion(
        "Чем повышение веса класса «фишинг» при обучении отличается от понижения порога классификации?",
        listOf(
            QuizOption(
                "Вес класса меняет саму модель и портит калиброванность вероятностей, порог — только правило поверх неё",
                true
            ),
            QuizOption("Ничем, это два способа записать одно и то же", false),
            QuizOption("Вес класса влияет только на скорость обучения", false),
            QuizOption("Порог меняет веса модели, а вес класса — нет", false)
        ),
        "Вес класса входит в функцию потерь, то есть модель обучается другой. Побочный эффект — " +
            "она начинает систематически завышать вероятность фишинга, и формула оптимального " +
            "порога перестаёт работать. Порог же применяется к уже готовым вероятностям и модель не трогает."
    ),
    QuizQuestion(
        "Политика ИБ говорит: пропущенный фишинг обходится в 10 раз дороже задержанного легитимного письма. Каким должен быть порог у хорошо откалиброванной модели?",
        listOf(
            QuizOption("Заметно ниже 0,5 — формула 1/(1+C) даёт около 0,09", true),
            QuizOption("Ровно 0,5 — это универсальное значение", false),
            QuizOption("Заметно выше 0,5, чтобы не беспокоить пользователей", false),
            QuizOption("Порог здесь не при чём, надо переобучить модель", false)
        ),
        "Блокировать выгодно, пока ожидаемая цена ложной тревоги меньше ожидаемой цены пропуска. " +
            "Отсюда порог, равный отношению цены ложной тревоги к сумме цен, то есть 1/(1+C). " +
            "Оговорка важная: формула верна только для откалиброванной модели, поэтому на практике " +
            "порог ещё и проверяют перебором по контрольной выборке."
    )
)

/**
 * Экран «Решение задачи» для логистической регрессии.
 *
 * Все числа считаются на лету через [LogrLab], а не зашиты константами:
 * если кто-то поменяет генерацию корпуса или алгоритм обучения, экран покажет
 * новую правду, а не устаревшую (правило из DEVELOPMENT_NOTES).
 */
@Composable
fun LogrResult(
    modifier: Modifier = Modifier,
    title: String?,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit = {}
) {
    val textColor = Color.White
    val accent = Color(0xFFFFD93D)
    val topicTitle = title ?: "Логистическая регрессия"

    val fit = remember {
        LogrLab.fit(
            lr = LogrLab.DEFAULT_LR,
            epochs = LogrLab.DEFAULT_EPOCHS,
            lambda = LogrLab.DEFAULT_LAMBDA,
            wPos = LogrLab.DEFAULT_W_POS
        )
    }
    val scores = remember(fit) { LogrLab.scoreAll(LogrLab.testSet, fit.w, fit.b) }
    val cm = remember(scores) { LogrLab.confusion(LogrLab.testSet, scores, LogrLab.DEFAULT_THRESHOLD) }
    val best = remember(scores) { LogrLab.bestThreshold(LogrLab.testSet, scores, LogrLab.DEFAULT_COST_FN) }
    val aucValue = remember(scores) { LogrLab.auc(LogrLab.testSet, scores) }
    val costAtHalf = remember(cm) { LogrLab.cost(cm, LogrLab.DEFAULT_COST_FN) }

    LessonScaffold(
        eyebrow = "Решение задачи",
        title = topicTitle,
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
                    "Скорость обучения ${"%.1f".format(LogrLab.DEFAULT_LR)}, " +
                        "${LogrLab.DEFAULT_EPOCHS} эпох, L2 = ${"%.2f".format(LogrLab.DEFAULT_LAMBDA)}, " +
                        "классы равноправны. Оценка на 200 контрольных письмах.",
                    color = textColor.copy(alpha = 0.85f), fontSize = 13.sp, lineHeight = 18.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "TP = ${cm.tp}   FP = ${cm.fp}   TN = ${cm.tn}   FN = ${cm.fn}",
                    color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Precision = ${"%.3f".format(cm.precision)}, " +
                        "Recall = ${"%.3f".format(cm.recall)}, " +
                        "F1 = ${"%.3f".format(cm.f1)}, " +
                        "AUC = ${"%.3f".format(aucValue)}",
                    color = textColor.copy(alpha = 0.9f), fontSize = 14.sp, lineHeight = 19.sp
                )

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = textColor.copy(alpha = 0.15f))
                Spacer(Modifier.height(12.dp))

                Text("Главный вывод", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "При цене пропуска ${"%.0f".format(LogrLab.DEFAULT_COST_FN)}× порог 0,5 даёт " +
                        "суммарный ущерб ${"%.0f".format(costAtHalf)}. Сдвиг порога до " +
                        "${"%.2f".format(best.threshold)} снижает его до ${"%.0f".format(best.cost)} — " +
                        "то есть примерно в ${"%.1f".format(costAtHalf / best.cost)} раза, " +
                        "не меняя в модели ни одного веса.",
                    color = textColor.copy(alpha = 0.85f), fontSize = 13.sp, lineHeight = 19.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Теоретическая формула для откалиброванной модели даёт " +
                        "${"%.2f".format(LogrLab.theoreticalThreshold(LogrLab.DEFAULT_COST_FN))}. " +
                        "Расхождение с найденным перебором значением — обычное дело: " +
                        "реальная модель откалибрована лишь приблизительно.",
                    color = textColor.copy(alpha = 0.65f), fontSize = 12.sp, lineHeight = 17.sp
                )

                Spacer(Modifier.height(12.dp))
                AskChatButton(accent = accent, onClick = {
                    onOpenChat(
                        "Объясни, пожалуйста, простыми словами, почему получился именно такой " +
                            "результат в теме «$topicTitle» (Решение задачи).\n\n" +
                            "Задача: детект фишинга в почтовом шлюзе, 5 признаков, " +
                            "300 писем на обучение и 200 на контроль, треть из них фишинг.\n" +
                            "Настройки: скорость обучения ${"%.1f".format(LogrLab.DEFAULT_LR)}, " +
                            "${LogrLab.DEFAULT_EPOCHS} эпох, L2 = ${"%.2f".format(LogrLab.DEFAULT_LAMBDA)}, " +
                            "порог 0,5.\n" +
                            "Результат: TP=${cm.tp}, FP=${cm.fp}, TN=${cm.tn}, FN=${cm.fn}, " +
                            "precision=${"%.3f".format(cm.precision)}, recall=${"%.3f".format(cm.recall)}, " +
                            "AUC=${"%.3f".format(aucValue)}.\n" +
                            "Ущерб при цене пропуска 10×: ${"%.0f".format(costAtHalf)} на пороге 0,5 " +
                            "против ${"%.0f".format(best.cost)} на пороге ${"%.2f".format(best.threshold)}.\n\n" +
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
                    "Логистическая регрессия выдаёт вероятность, а не готовое решение — это и " +
                        "позволяет отделить статистику от политики безопасности.",
                    "Порог классификации не участвует в обучении: он выбирается отдельно, " +
                        "исходя из цены ложной тревоги и цены пропуска.",
                    "Accuracy в задачах ИБ обманчива из-за дисбаланса классов; смотреть нужно на " +
                        "матрицу ошибок, precision, recall и суммарный ущерб.",
                    "Вес класса и порог двигают один и тот же компромисс, но вес класса меняет " +
                        "саму модель и ломает калиброванность вероятностей."
                ).forEach {
                    Text(
                        "•  $it",
                        color = textColor.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        QuizSection(questions = logrQuiz, textColor = textColor, nodeId = "logr")
    }
}
