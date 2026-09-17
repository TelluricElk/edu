package com.eduappml.ui.dt

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

private val dtQuizIb = listOf(
    QuizQuestion(
        "Глубокое дерево показало на обучающей выборке точность 0,96 — лучший результат за весь прогон. Что это говорит о качестве модели?",
        listOf(
            QuizOption(
                "Ничего. Обучающая точность дерева растёт с глубиной всегда, вплоть до единицы, даже на полностью случайных данных",
                true
            ),
            QuizOption("Модель хороша, её можно брать в работу", false),
            QuizOption("Модель недообучена, глубину надо увеличить ещё", false),
            QuizOption("Это признак того, что данные разделимы", false)
        ),
        "Без ограничений дерево дробит выборку, пока в каждом листе не останется по одному " +
            "наблюдению — тогда обучающая точность равна единице по построению. В нашей задаче " +
            "контрольная точность при этом падает с 0,850 до 0,729: модель выучила ошибки " +
            "аналитиков вместе с инцидентами."
    ),
    QuizQuestion(
        "Что произойдёт с деревом, если умножить один из признаков на 100?",
        listOf(
            QuizOption("Ничего: сравнение признака с порогом не меняется от масштаба", true),
            QuizOption("Этот признак станет доминировать, как в методе ближайших соседей", false),
            QuizOption("Дерево перестанет сходиться", false),
            QuizOption("Придётся заново подбирать глубину", false)
        ),
        "Дерево не считает расстояний и не складывает признаки — каждый узел сравнивает один " +
            "признак с одним числом, и порог отмасштабируется вместе с признаком. Это отличает " +
            "деревья от k-NN и SVM, где нормализация обязательна."
    ),
    QuizQuestion(
        "У дерева зафиксирована предельная глубина 10, и оно переобучено. Какой ещё рычаг позволяет это исправить, не трогая глубину?",
        listOf(
            QuizOption(
                "Поднять минимальное число записей, при котором узел ещё разрешено дробить",
                true
            ),
            QuizOption("Сменить критерий с Джини на энтропию", false),
            QuizOption("Увеличить обучающую выборку вдвое", false),
            QuizOption("Поменять порядок признаков", false)
        ),
        "При глубине 10 поднятие минимума с 2 до 16 возвращает контрольную точность с 0,743 до " +
            "0,821. Этот ограничитель аккуратнее: он режет ветви, обслуживающие единичные " +
            "наблюдения, а крупные оставляет расти. Смена критерия даёт эффект в разы меньший."
    ),
    QuizQuestion(
        "Почему одно дерево считают неустойчивой моделью и к чему это привело исторически?",
        listOf(
            QuizOption(
                "Изменение нескольких наблюдений может поменять разбиение в корне, после чего перестраивается всё дерево; из борьбы с этим выросли случайный лес и градиентный бустинг",
                true
            ),
            QuizOption("Дерево неустойчиво численно и накапливает ошибку округления", false),
            QuizOption("Результат зависит от случайной инициализации, как у нейросетей", false),
            QuizOption("Дерево неустойчиво к масштабу признаков", false)
        ),
        "Алгоритм жадный и иерархический: разбиение в корне выбирается по небольшому перевесу " +
            "прироста чистоты, и смена победителя перестраивает всё ниже. Дисперсию предсказания " +
            "снижают усреднением многих деревьев — это и есть идея ансамблей. Платой становится " +
            "потеря читаемости, ради которой деревья в безопасности и держат."
    )
)

/**
 * Экран «Решение задачи» для дерева решений.
 *
 * Показывает два прогона: на чистой истории (дерево восстанавливает политику)
 * и на истории с 15% ошибок разметки (кривые расходятся). Все числа считаются
 * на лету через [DtLab].
 */
@Composable
fun DtResult(
    modifier: Modifier = Modifier,
    title: String?,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit = {}
) {
    val textColor = Color.White
    val accent = Color(0xFFFFD93D)
    val topicTitle = title ?: "Дерево решений"

    // эталон: шум 15%, глубина 4
    val train = remember { DtLab.trainSet(DtLab.DEFAULT_NOISE) }
    val test = remember { DtLab.testSet(DtLab.DEFAULT_NOISE) }
    val tree = remember(train) {
        DtLab.buildTree(train, DtCriterion.GINI, DtLab.DEFAULT_DEPTH, DtLab.DEFAULT_MIN_SPLIT)
    }
    val trainAcc = remember(tree) { DtLab.accuracy(tree, train) }
    val testAcc = remember(tree) { DtLab.accuracy(tree, test) }
    val leaves = remember(tree) { DtLab.leafCount(tree) }

    // переобученное дерево на той же истории
    val deep = remember(train) {
        DtLab.buildTree(train, DtCriterion.GINI, 12, DtLab.DEFAULT_MIN_SPLIT)
    }
    val deepTrain = remember(deep) { DtLab.accuracy(deep, train) }
    val deepTest = remember(deep) { DtLab.accuracy(deep, test) }
    val deepLeaves = remember(deep) { DtLab.leafCount(deep) }

    // чистая история — дерево восстанавливает политику
    val cleanTrain = remember { DtLab.trainSet(0.0) }
    val cleanTest = remember { DtLab.testSet(0.0) }
    val cleanTree = remember(cleanTrain) {
        DtLab.buildTree(cleanTrain, DtCriterion.GINI, 4, DtLab.DEFAULT_MIN_SPLIT)
    }
    val cleanTrainAcc = remember(cleanTree) { DtLab.accuracy(cleanTree, cleanTrain) }
    val cleanTestAcc = remember(cleanTree) { DtLab.accuracy(cleanTree, cleanTest) }
    val cleanLeaves = remember(cleanTree) { DtLab.leafCount(cleanTree) }

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
                    "Глубина ${DtLab.DEFAULT_DEPTH}, минимум ${DtLab.DEFAULT_MIN_SPLIT} записей " +
                        "на разбиение, критерий Джини, " +
                        "${(DtLab.DEFAULT_NOISE * 100).toInt()}% ошибок разметки. " +
                        "История ${DtLab.TRAIN_SIZE} инцидентов, контроль ${DtLab.TEST_SIZE}.",
                    color = textColor.copy(alpha = 0.85f), fontSize = 13.sp, lineHeight = 18.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Обучающая ${"%.4f".format(trainAcc)}   •   контрольная ${"%.4f".format(testAcc)}" +
                        "   •   листьев $leaves",
                    color = accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Главный вывод: обучающая точность бесполезна",
                    color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Глубина ${DtLab.DEFAULT_DEPTH}: обучающая ${"%.4f".format(trainAcc)}, " +
                        "контрольная ${"%.4f".format(testAcc)}, листьев $leaves",
                    color = Color(0xFF6BCB77), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    lineHeight = 19.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Глубина 12: обучающая ${"%.4f".format(deepTrain)}, " +
                        "контрольная ${"%.4f".format(deepTest)}, листьев $deepLeaves",
                    color = Color(0xFFFF6B6B), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    lineHeight = 19.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Глубокое дерево выигрывает на обучающей выборке " +
                        "${"%.3f".format(deepTrain - trainAcc)} и проигрывает на контрольной " +
                        "${"%.3f".format(testAcc - deepTest)}. Оно выделило отдельные ветви под " +
                        "инциденты, которые аналитик разобрал неверно, и теперь будет " +
                        "воспроизводить его ошибки на всех похожих случаях.\n\n" +
                        "Выбирать модель по первому числу нельзя: оно растёт всегда, вплоть до " +
                        "единицы, и росло бы точно так же на полностью случайных данных.",
                    color = textColor.copy(alpha = 0.8f), fontSize = 13.sp, lineHeight = 19.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("На чистой истории дерево читает политику обратно",
                    color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Обучающая ${"%.4f".format(cleanTrainAcc)}, контрольная " +
                        "${"%.4f".format(cleanTestAcc)}, листьев $cleanLeaves.",
                    color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Выведенные пороги против настоящей политики, которую модели не показывали:",
                    color = textColor.copy(alpha = 0.7f), fontSize = 12.sp
                )
                Spacer(Modifier.height(6.dp))
                listOf(
                    "неудачных входов ≤ 29,51   против настоящего 30",
                    "привилегии ≤ 0,60   против настоящего 0,60",
                    "неудачных входов ≤ 7,89   против настоящего 8",
                    "привилегии ≤ 0,87   против настоящего 0,85"
                ).forEach {
                    Text("•  $it", color = textColor.copy(alpha = 0.85f), fontSize = 13.sp,
                        lineHeight = 18.sp, modifier = Modifier.padding(vertical = 2.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Пять листьев — ровно столько правил, сколько было в исходном плейбуке. " +
                        "Ни одна другая модель в этом приложении не выдаёт результат в виде, " +
                        "который можно вставить в документ и показать аудитору.",
                    color = textColor.copy(alpha = 0.8f), fontSize = 13.sp, lineHeight = 19.sp
                )
                Spacer(Modifier.height(12.dp))
                AskChatButton(accent = accent, onClick = {
                    onOpenChat(
                        "Объясни, пожалуйста, простыми словами, почему получился именно такой " +
                            "результат в теме «$topicTitle» (Решение задачи).\n\n" +
                            "Задача: восстановить правила эскалации инцидентов по истории из " +
                            "${DtLab.TRAIN_SIZE} разобранных случаев. Два признака — число неудачных " +
                            "входов и уровень привилегий учётной записи.\n" +
                            "При глубине ${DtLab.DEFAULT_DEPTH} и 15% ошибок разметки: обучающая " +
                            "${"%.4f".format(trainAcc)}, контрольная ${"%.4f".format(testAcc)}, " +
                            "листьев $leaves.\n" +
                            "При глубине 12 на той же истории: обучающая ${"%.4f".format(deepTrain)}, " +
                            "контрольная ${"%.4f".format(deepTest)}, листьев $deepLeaves.\n" +
                            "На истории без ошибок разметки дерево глубины 4 даёт обучающую " +
                            "${"%.4f".format(cleanTrainAcc)} при $cleanLeaves листьях и " +
                            "восстанавливает исходные пороги почти дословно.\n\n" +
                            "Почему обучающая точность растёт всегда и почему глубокое дерево хуже?"
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
                    "Дерево жадно дробит выборку, выбирая признак и порог с наибольшим приростом " +
                        "чистоты, и выдаёт правила в том же виде, в каком пишут плейбуки.",
                    "Обучающая точность дерева растёт с глубиной всегда и потому не годится для " +
                        "выбора модели; контрольная имеет максимум на умеренной глубине.",
                    "Масштаб признаков дереву безразличен — сравнение с порогом не меняется от " +
                        "умножения признака на константу.",
                    "Одно дерево неустойчиво: смена разбиения в корне перестраивает всё ниже. " +
                        "Из борьбы с этим выросли случайный лес и градиентный бустинг."
                ).forEach {
                    Text(
                        "•  $it",
                        color = textColor.copy(alpha = 0.85f), fontSize = 13.sp,
                        lineHeight = 18.sp, modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        QuizSection(questions = dtQuizIb, textColor = textColor, nodeId = "dt")
    }
}
