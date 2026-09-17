package com.eduappml.ui.rf

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val rfQuizIb = listOf(
    QuizQuestion(
        "Бутстрап выключен, и каждому узлу разрешено смотреть все шесть признаков. Чему будет равна точность леса из 25 деревьев?",
        listOf(
            QuizOption(
                "Ровно точности одного дерева: алгоритм детерминирован, поэтому все 25 деревьев совпадут до последнего узла",
                true
            ),
            QuizOption("Примерно в 25 раз выше, голоса складываются", false),
            QuizOption("Чуть выше: даже одинаковые деревья немного страхуют друг друга", false),
            QuizOption("Ниже, чем у одного дерева, из-за ошибок голосования", false)
        ),
        "В нашей задаче это 0,7575 — то же самое число, что у одиночного дерева глубины 10. " +
            "Ансамбль из одинаковых моделей равен одной модели: усреднять можно только то, что " +
            "различается. Отсюда и название «случайный» лес — случайность в нём не украшение, " +
            "а несущая конструкция."
    ),
    QuizQuestion(
        "Почему дереву в лесу дают расти вглубь, хотя одиночному дереву глубину как раз ограничивают?",
        listOf(
            QuizOption(
                "Бэггинг снижает разброс, а не смещение: ему нужны деревья с низким смещением, то есть глубокие, и он сам гасит их разброс усреднением",
                true
            ),
            QuizOption("Глубокие деревья быстрее обучаются", false),
            QuizOption("Глубина в лесу не влияет ни на что", false),
            QuizOption("Чтобы деревья гарантированно отличались друг от друга", false)
        ),
        "На глубине 14 одиночное дерево даёт 0,7400 против 0,8250 у своего же варианта глубины 4 — " +
            "классическое переобучение. Лес из тех же самых деревьев глубины 14 даёт 0,8325, то есть " +
            "не теряет почти ничего. Подрезать деревья в лесу — значит поднять смещение там, где его " +
            "нечем компенсировать."
    ),
    QuizQuestion(
        "Что такое OOB-оценка и чем она ценна на практике?",
        listOf(
            QuizOption(
                "Проверка каждого дерева на тех обучающих файлах, которые не попали в его бутстрап-выборку; она даёт честную оценку качества без отдельной контрольной выборки",
                true
            ),
            QuizOption("Точность на самых трудных файлах коллекции", false),
            QuizOption("Точность леса за вычетом точности лучшего дерева", false),
            QuizOption("Оценка по файлам, на которых деревья разошлись во мнениях", false)
        ),
        "При выборке с возвратом каждое дерево не видит примерно 36,8% файлов — это предел " +
            "(1 − 1/n)^n. В нашей задаче OOB даёт 0,8275 против 0,8400 на честной контрольной " +
            "выборке. Когда размеченных данных мало, OOB позволяет не отрезать от них кусок под " +
            "контроль, и это часто решающий довод в пользу леса."
    ),
    QuizQuestion(
        "Лес выдал по файлу 13 голосов «вредоносный» из 25. Как этим стоит распорядиться в SOC?",
        listOf(
            QuizOption(
                "Отправить файл аналитику: доля голосов у порога означает, что модель спорит сама с собой, и её вердикт здесь почти случаен",
                true
            ),
            QuizOption("Заблокировать: большинство есть большинство", false),
            QuizOption("Пропустить: 13 из 25 статистически неотличимо от нуля", false),
            QuizOption("Переобучить лес с другим сидом и посмотреть ещё раз", false)
        ),
        "Доля голосов — это дополнительная информация, которой у одиночного дерева просто нет: " +
            "оно отвечает «да» или «нет». На гистограмме голосов видно, что у краёв стоят файлы, " +
            "где лес единодушен, а у середины — те, где он неустойчив. Порог по доле голосов " +
            "настраивается так же, как порог вероятности в логистической регрессии."
    ),
    QuizQuestion(
        "Чем важность признака по лесу лучше важности по одному дереву?",
        listOf(
            QuizOption(
                "В одном дереве корневой признак забирает всё и заслоняет свои коррелированные замены; в лесу узлы часто его не видят, и остальные признаки успевают проявиться",
                true
            ),
            QuizOption("По лесу важность считается точнее численно", false),
            QuizOption("По одному дереву важность вообще нельзя посчитать", false),
            QuizOption("Лес нормирует важности на единицу, а дерево нет", false)
        ),
        "Энтропия .text выходит на первое место (0,209), но остальные пять признаков идут " +
            "плотной группой 0,136–0,195 — и это правда о задаче, а не артефакт. Именно случайное " +
            "подпространство признаков заставляет лес пробовать альтернативы вместо того, чтобы " +
            "каждый раз хвататься за один и тот же разрез."
    )
)

/** Результаты эталонного прогона — считаются в фоне, их довольно много. */
private class RfReference(
    val singleTrain: Double,
    val singleTest: Double,
    val forestTrain: Double,
    val forestTest: Double,
    val oob: Double,
    val shallowSingleTest: Double,
    val shallowForestTest: Double,
    val deepSingleTrain: Double,
    val deepSingleTest: Double,
    val deepForestTest: Double,
    val bootMtry2: Double,
    val bootMtry6: Double,
    val noBootMtry2: Double,
    val noBootMtry6: Double,
    val importance: DoubleArray
)

/**
 * Экран «Решение задачи» для случайного леса.
 *
 * Три сюжета, все числа считаются на лету через [RfLab]:
 *   1. эталонный прогон и выигрыш леса над одиночным деревом;
 *   2. глубина ломает дерево и не ломает лес;
 *   3. два источника разнообразия, и что происходит, когда их выключить.
 *
 * Прогонов здесь семь, поэтому считается всё в фоне: на слабом телефоне
 * синхронный расчёт заметно подвесил бы переход на экран.
 */
@Composable
fun RfResult(
    modifier: Modifier = Modifier,
    title: String?,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit = {}
) {
    val textColor = Color.White
    val accent = Color(0xFFFFD93D)
    val good = Color(0xFF6BCB77)
    val bad = Color(0xFFFF6B6B)
    val topicTitle = title ?: "Случайный лес"

    var ref by remember { mutableStateOf<RfReference?>(null) }

    LaunchedEffect(Unit) {
        ref = withContext(Dispatchers.Default) {
            val train = RfLab.trainSet(RfLab.DEFAULT_NOISE)
            val test = RfLab.testSet(RfLab.DEFAULT_NOISE)
            val c = RfCriterion.GINI
            val ms = RfLab.DEFAULT_MIN_SPLIT
            val nt = RfLab.DEFAULT_TREES

            fun forestAt(depth: Int, mtry: Int, bootstrap: Boolean) =
                RfLab.trainForest(train, c, nt, depth, ms, mtry, bootstrap)

            val single = RfLab.buildSingleTree(train, c, RfLab.DEFAULT_DEPTH, ms)
            val forest = forestAt(RfLab.DEFAULT_DEPTH, RfLab.DEFAULT_MTRY, true)
            val curve = RfLab.forestCurve(forest, train, test)

            val shallowSingle = RfLab.buildSingleTree(train, c, 4, ms)
            val shallowForest = forestAt(4, RfLab.DEFAULT_MTRY, true)

            val deepSingle = RfLab.buildSingleTree(train, c, 14, ms)
            val deepForest = forestAt(14, RfLab.DEFAULT_MTRY, true)

            val noBoot2 = forestAt(RfLab.DEFAULT_DEPTH, 2, false)
            val boot6 = forestAt(RfLab.DEFAULT_DEPTH, RfLab.N_FEAT, true)
            val noBoot6 = forestAt(RfLab.DEFAULT_DEPTH, RfLab.N_FEAT, false)

            RfReference(
                singleTrain = RfLab.accuracy(single, train),
                singleTest = RfLab.accuracy(single, test),
                forestTrain = RfLab.forestAccuracy(forest, train),
                forestTest = RfLab.forestAccuracy(forest, test),
                oob = curve.oobCurve[curve.oobCurve.size - 1],
                shallowSingleTest = RfLab.accuracy(shallowSingle, test),
                shallowForestTest = RfLab.forestAccuracy(shallowForest, test),
                deepSingleTrain = RfLab.accuracy(deepSingle, train),
                deepSingleTest = RfLab.accuracy(deepSingle, test),
                deepForestTest = RfLab.forestAccuracy(deepForest, test),
                bootMtry2 = RfLab.forestAccuracy(forest, test),
                bootMtry6 = RfLab.forestAccuracy(boot6, test),
                noBootMtry2 = RfLab.forestAccuracy(noBoot2, test),
                noBootMtry6 = RfLab.forestAccuracy(noBoot6, test),
                importance = RfLab.importance(forest)
            )
        }
    }

    LessonScaffold(
        eyebrow = "Решение задачи",
        title = topicTitle,
        onBack = onBack,
        accent = accent,
        modifier = modifier
    ) {
        val r = ref
        if (r == null) {
            Text(
                "Считаем эталонные прогоны…",
                color = textColor.copy(alpha = 0.6f), fontSize = 14.sp
            )
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Эталонное решение", color = textColor,
                        fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${RfLab.DEFAULT_TREES} деревьев, предельная глубина ${RfLab.DEFAULT_DEPTH}, " +
                            "${RfLab.DEFAULT_MTRY} признака на узел из ${RfLab.N_FEAT}, минимум " +
                            "${RfLab.DEFAULT_MIN_SPLIT} файла на разбиение, критерий Джини, " +
                            "${(RfLab.DEFAULT_NOISE * 100).toInt()}% ошибок разметки. " +
                            "Коллекция ${RfLab.TRAIN_SIZE} файлов, контроль ${RfLab.TEST_SIZE}.",
                        color = textColor.copy(alpha = 0.85f), fontSize = 13.sp, lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Лес: обучающая ${"%.4f".format(r.forestTrain)}   •   контрольная " +
                            "${"%.4f".format(r.forestTest)}   •   OOB ${"%.4f".format(r.oob)}",
                        color = accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        lineHeight = 21.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Одно дерево той же глубины: обучающая ${"%.4f".format(r.singleTrain)}, " +
                            "контрольная ${"%.4f".format(r.singleTest)}. " +
                            "Выигрыш леса ${"%+.4f".format(r.forestTest - r.singleTest)}.",
                        color = textColor.copy(alpha = 0.85f), fontSize = 13.sp, lineHeight = 18.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Главный вывод: глубина ломает дерево и не ломает лес",
                        color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                        lineHeight = 21.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Глубина 4:  дерево ${"%.4f".format(r.shallowSingleTest)}, " +
                            "лес ${"%.4f".format(r.shallowForestTest)}",
                        color = good, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Глубина 14:  дерево ${"%.4f".format(r.deepSingleTest)}, " +
                            "лес ${"%.4f".format(r.deepForestTest)}",
                        color = bad, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Одиночное дерево, получив свободу расти, теряет " +
                            "${"%.4f".format(r.shallowSingleTest - r.deepSingleTest)} " +
                            "контрольной точности — при том, что на обучающей оно поднимается до " +
                            "${"%.4f".format(r.deepSingleTrain)}. Лес из точно таких же " +
                            "переученных деревьев глубины 14 не теряет ничего.\n\n" +
                            "Это и есть содержание бэггинга. Ошибка модели раскладывается на " +
                            "смещение и разброс; усреднение независимых моделей давит разброс и " +
                            "не трогает смещение. Значит, в ансамбль надо отдавать модели с " +
                            "низким смещением — то есть глубокие деревья, каждое из которых " +
                            "по отдельности переобучено. Подрезать их здесь — ошибка.",
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
                    Text("Два источника разнообразия",
                        color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                        lineHeight = 21.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Контрольная точность леса из ${RfLab.DEFAULT_TREES} деревьев глубины " +
                            "${RfLab.DEFAULT_DEPTH} в четырёх режимах:",
                        color = textColor.copy(alpha = 0.7f), fontSize = 12.sp, lineHeight = 17.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    listOf(
                        "бутстрап есть, 2 признака на узел — ${"%.4f".format(r.bootMtry2)}",
                        "бутстрап есть, все 6 признаков — ${"%.4f".format(r.bootMtry6)}",
                        "бутстрапа нет, 2 признака на узел — ${"%.4f".format(r.noBootMtry2)}",
                        "бутстрапа нет, все 6 признаков — ${"%.4f".format(r.noBootMtry6)}"
                    ).forEach {
                        Text("•  $it", color = textColor.copy(alpha = 0.85f), fontSize = 13.sp,
                            lineHeight = 18.sp, modifier = Modifier.padding(vertical = 2.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Последняя строка — ${"%.4f".format(r.noBootMtry6)} — это ровно точность " +
                            "одиночного дерева (${"%.4f".format(r.singleTest)}). Без обоих " +
                            "механизмов алгоритм построения дерева детерминирован, поэтому все " +
                            "${RfLab.DEFAULT_TREES} деревьев получаются буквально одинаковыми, и " +
                            "голосование ничего не решает.\n\n" +
                            "Любого одного механизма уже хватает, чтобы лес ожил, — они во многом " +
                            "взаимозаменяемы. Вместе они работают чуть лучше, но главное здесь " +
                            "не размер прибавки, а то, что случайность в этом алгоритме — " +
                            "несущая конструкция, а не деталь реализации.",
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
                    Text("На что опирается лес", color = textColor,
                        fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    val order = (0 until RfLab.N_FEAT).sortedByDescending { r.importance[it] }
                    order.forEach { i ->
                        Text(
                            "•  ${RfLab.featureNames[i]} — ${"%.4f".format(r.importance[i])}",
                            color = textColor.copy(alpha = if (i == order[0]) 0.95f else 0.8f),
                            fontSize = 13.sp, lineHeight = 18.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Энтропия секции кода впереди — упаковщики и шифровальщики поднимают её " +
                            "почти всегда. Но отрыв невелик, и это содержательный результат: " +
                            "детектор, построенный на одной энтропии, обходится тривиально — " +
                            "достаточно не упаковывать файл. Остальные пять признаков идут " +
                            "плотной группой, и обходить их приходится все сразу.",
                        color = textColor.copy(alpha = 0.8f), fontSize = 13.sp, lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    AskChatButton(accent = accent, onClick = {
                        onOpenChat(
                            "Объясни, пожалуйста, простыми словами, почему получился именно такой " +
                                "результат в теме «$topicTitle» (Решение задачи).\n\n" +
                                "Задача: статический детект вредоносного ПО по шести признакам " +
                                "PE-файла, коллекция ${RfLab.TRAIN_SIZE} размеченных файлов, " +
                                "контроль ${RfLab.TEST_SIZE}, ${(RfLab.DEFAULT_NOISE * 100).toInt()}% " +
                                "вердиктов в коллекции ошибочны.\n" +
                                "Эталонный прогон — ${RfLab.DEFAULT_TREES} деревьев, глубина " +
                                "${RfLab.DEFAULT_DEPTH}, ${RfLab.DEFAULT_MTRY} признака на узел. " +
                                "лес на контрольной ${"%.4f".format(r.forestTest)}, OOB " +
                                "${"%.4f".format(r.oob)}, одиночное дерево той же глубины " +
                                "${"%.4f".format(r.singleTest)}.\n" +
                                "Глубина 4: дерево ${"%.4f".format(r.shallowSingleTest)}, " +
                                "лес ${"%.4f".format(r.shallowForestTest)}. " +
                                "Глубина 14: дерево ${"%.4f".format(r.deepSingleTest)}, " +
                                "лес ${"%.4f".format(r.deepForestTest)}.\n" +
                                "Без бутстрапа и со всеми шестью признаками на узел лес даёт " +
                                "${"%.4f".format(r.noBootMtry6)} — столько же, сколько одно дерево.\n\n" +
                                "Почему усреднение помогает именно переученным деревьям и почему " +
                                "лесу нужна случайность?"
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
                    Text("Полученные знания", color = textColor,
                        fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    listOf(
                        "Бэггинг усредняет много переобученных деревьев: он давит разброс и не " +
                            "трогает смещение, поэтому деревьям в лесу дают расти вглубь.",
                        "Разнообразие деревьев обеспечивают бутстрап-выборки и случайное " +
                            "подпространство признаков в каждом узле; без обоих лес вырождается " +
                            "в одно дерево.",
                        "OOB-оценка получается бесплатно — по файлам, не попавшим в бутстрап " +
                            "конкретного дерева, — и заменяет отдельную контрольную выборку.",
                        "Доля голосов даёт меру уверенности, которой у одиночного дерева нет: " +
                            "файлы у порога стоит отправлять аналитику вручную.",
                        "Важность признаков по лесу честнее, чем по дереву, потому что корневой " +
                            "признак не заслоняет свои коррелированные замены.",
                        "Плата за всё это — потеря читаемости: плейбук из леса уже не выписать, " +
                            "и там, где вердикт нужно объяснить аудитору, дерево остаётся на месте."
                    ).forEach {
                        Text(
                            "•  $it",
                            color = textColor.copy(alpha = 0.85f), fontSize = 13.sp,
                            lineHeight = 18.sp, modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        QuizSection(questions = rfQuizIb, textColor = textColor, nodeId = "rf")
    }
}
