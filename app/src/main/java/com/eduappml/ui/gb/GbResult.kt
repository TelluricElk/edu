package com.eduappml.ui.gb

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

private val gbQuizIb = listOf(
    QuizQuestion(
        "Чему обучается каждое следующее дерево в градиентном бустинге?",
        listOf(
            QuizOption(
                "Остаткам — тому, чего не хватает сумме уже построенных деревьев на каждом обучающем примере",
                true
            ),
            QuizOption("Тем же исходным данным, но на другой случайной подвыборке", false),
            QuizOption("Данным, где ошибочно классифицированным примерам подняли вес", false),
            QuizOption("Предсказаниям предыдущего дерева", false)
        ),
        "Для квадратичной ошибки антиградиент по предсказанию совпадает с обычным остатком, " +
            "поэтому в коде никакого отдельного вычисления градиента нет: берётся разность между " +
            "истинным значением и текущей суммой. Вариант с весами примеров — это AdaBoost, " +
            "исторический предшественник; вариант со случайными подвыборками — бэггинг, то есть " +
            "случайный лес из предыдущей темы."
    ),
    QuizQuestion(
        "Чем бустинг принципиально отличается от случайного леса по устройству обучения?",
        listOf(
            QuizOption(
                "Деревья строятся последовательно, каждое исправляет ошибки предыдущих, и распараллелить это нельзя; в лесу деревья независимы",
                true
            ),
            QuizOption("Бустинг использует деревья другой структуры", false),
            QuizOption("Бустинг не требует контрольной выборки", false),
            QuizOption("Бустинг работает только с одним признаком", false)
        ),
        "Отсюда все практические различия. Лес раскладывается на сколько угодно ядер, бустинг " +
            "нет. Лесу нужны глубокие деревья с низким смещением, бустингу — наоборот, слабые: " +
            "он снижает смещение шаг за шагом, и сильные деревья ему только мешают. И лес почти " +
            "невозможно переобучить числом деревьев, а бустинг — легко."
    ),
    QuizQuestion(
        "Скорость обучения уменьшили с 0,5 до 0,1. Что нужно сделать с числом итераций?",
        listOf(
            QuizOption("Увеличить примерно во столько же раз — эти два параметра компенсируют друг друга", true),
            QuizOption("Оставить прежним: скорость на число итераций не влияет", false),
            QuizOption("Тоже уменьшить, иначе модель переобучится", false),
            QuizOption("Уменьшить вдвое, чтобы сохранить суммарный вклад", false)
        ),
        "В нашей задаче при скорости 0,50 минимум контрольной ошибки приходится на 18-ю " +
            "итерацию, при 0,10 — на 65-ю. Произведение шага на число итераций до минимума " +
            "держится около 6–9. При этом мелкий шаг даёт и лучшее качество: 1,953 против 1,995. " +
            "Так что размен не совсем симметричный — платой за качество становится время."
    ),
    QuizQuestion(
        "Обучающая ошибка бустинга падает на каждой итерации без исключений. О чём это говорит?",
        listOf(
            QuizOption(
                "Ни о чём, кроме устройства алгоритма: он по построению уменьшает её каждым шагом и остановиться сам не может",
                true
            ),
            QuizOption("Модель сходится к оптимуму, можно продолжать", false),
            QuizOption("Данные хорошо разделимы", false),
            QuizOption("Скорость обучения подобрана верно", false)
        ),
        "Число итераций в бустинге — такой же параметр регуляризации, как глубина у дерева, и " +
            "останавливать обучение приходится снаружи, по контрольной ошибке. В библиотеках это " +
            "называется ранней остановкой. В нашей задаче при шаге 1,0 контрольная ошибка " +
            "достигает минимума 2,198 на 38-й итерации и к двухсотой уходит до 2,512, а " +
            "обучающая всё это время продолжает падать."
    ),
    QuizQuestion(
        "Почему в этой задаче линейная регрессия проигрывает бустингу, хотя зависимость в целом возрастающая?",
        listOf(
            QuizOption(
                "Из-за разрыва на 60 днях: прямая обязана пройти сквозь него, а сумма деревьев воспроизводит скачок почти точно",
                true
            ),
            QuizOption("Линейная регрессия чувствительна к масштабу признака", false),
            QuizOption("Прямая не умеет работать с одним признаком", false),
            QuizOption("У прямой слишком много параметров для сорока наблюдений", false)
        ),
        "Модель даёт скачок 3,788 между 55 и 65 днями при настоящем 3,830; прямая — 0,923. " +
            "Контрольная ошибка 1,953 против 2,765. Причём порог первого же дерева встаёт на " +
            "58,0 дня — алгоритму никто не говорил про шестидесятый день, он нашёл его по " +
            "остаткам."
    )
)

/** Результаты эталонных прогонов — считаются в фоне. */
private class GbReference(
    val trainMse: Double,
    val testMse: Double,
    val bestIter: Int,
    val bestMse: Double,
    val baseline: Double,
    val line: GbLab.Line,
    val firstThreshold: Double,
    val jumpModel: Double,
    val jumpTrue: Double,
    val jumpLine: Double,
    val lrRows: List<LrRow>,
    val depth2Best: Double,
    val depth2BestIter: Int,
    val depth2Final: Double,
    val depth2Train: Double
)

private class LrRow(
    val lr: Double,
    val bestIter: Int,
    val bestMse: Double,
    val finalMse: Double
)

/**
 * Экран «Решение задачи» для градиентного бустинга.
 *
 * Три сюжета, все числа считаются на лету через [GbLab]:
 *   1. разрыв на 60 днях, который прямая выразить не может;
 *   2. размен «скорость обучения против числа итераций»;
 *   3. переобучение числом итераций и почему глубина здесь лишняя.
 */
@Composable
fun GbResult(
    modifier: Modifier = Modifier,
    title: String?,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit = {}
) {
    val textColor = Color.White
    val accent = Color(0xFFFFD93D)
    val good = Color(0xFF6BCB77)
    val bad = Color(0xFFFF6B6B)
    val topicTitle = title ?: "Градиентный бустинг"

    var ref by remember { mutableStateOf<GbReference?>(null) }

    LaunchedEffect(Unit) {
        ref = withContext(Dispatchers.Default) {
            val train = GbLab.trainSet(GbLab.DEFAULT_NOISE)
            val test = GbLab.testSet(GbLab.DEFAULT_NOISE)
            val ml = GbLab.DEFAULT_MIN_LEAF

            val m = GbLab.train(
                train, test, GbLab.DEFAULT_ESTIMATORS,
                GbLab.DEFAULT_LEARNING_RATE, GbLab.DEFAULT_DEPTH, ml
            )
            val line = GbLab.fitLine(train, test)
            val best = GbLab.bestIteration(m)

            val rows = listOf(1.0, 0.5, 0.2, 0.1, 0.05).map { lr ->
                val mm = GbLab.train(train, test, GbLab.ESTIMATORS_MAX, lr, 1, ml)
                val b = GbLab.bestIteration(mm)
                LrRow(lr, b + 1, mm.testMse[b], mm.testMse[mm.testMse.size - 1])
            }

            val d2 = GbLab.train(train, test, GbLab.ESTIMATORS_MAX, 1.0, 2, ml)
            val d2b = GbLab.bestIteration(d2)

            GbReference(
                trainMse = m.trainMse[m.trainMse.size - 1],
                testMse = m.testMse[m.testMse.size - 1],
                bestIter = best,
                bestMse = m.testMse[best],
                baseline = GbLab.baselineMse(train, test),
                line = line,
                firstThreshold = if (m.trees.isEmpty() || m.trees[0].isLeaf) -1.0 else m.trees[0].threshold,
                jumpModel = GbLab.predict(m, 65.0) - GbLab.predict(m, 55.0),
                jumpTrue = GbLab.trueDamage(65.0) - GbLab.trueDamage(55.0),
                jumpLine = line.slope * 10.0,
                lrRows = rows,
                depth2Best = d2.testMse[d2b],
                depth2BestIter = d2b + 1,
                depth2Final = d2.testMse[d2.testMse.size - 1],
                depth2Train = d2.trainMse[d2.trainMse.size - 1]
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
            Text("Считаем эталонные прогоны…",
                color = textColor.copy(alpha = 0.6f), fontSize = 14.sp)
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
                        "${GbLab.DEFAULT_ESTIMATORS} итераций, скорость обучения " +
                            "${"%.2f".format(GbLab.DEFAULT_LEARNING_RATE)}, деревья глубины " +
                            "${GbLab.DEFAULT_DEPTH}, минимум ${GbLab.DEFAULT_MIN_LEAF} инцидентов " +
                            "в листе, разброс оценок ±${"%.1f".format(GbLab.DEFAULT_NOISE)} млн. " +
                            "${GbLab.TRAIN_SIZE} инцидентов на обучение, ${GbLab.TEST_SIZE} на контроль.",
                        color = textColor.copy(alpha = 0.85f), fontSize = 13.sp, lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Обучающая ${"%.4f".format(r.trainMse)}   •   контрольная " +
                            "${"%.4f".format(r.testMse)}",
                        color = accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        lineHeight = 21.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Для сравнения: модель-константа даёт ${"%.4f".format(r.baseline)}, " +
                            "прямая наименьших квадратов ${"%.4f".format(r.line.testMse)}.",
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
                    Text("Главный вывод: алгоритм сам нашёл шестидесятый день",
                        color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                        lineHeight = 21.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Порог первого дерева: ${"%.2f".format(r.firstThreshold)} дня",
                        color = good, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    listOf(
                        "скачок предсказания между 55 и 65 днями — ${"%.3f".format(r.jumpModel)}",
                        "настоящий скачок — ${"%.3f".format(r.jumpTrue)}",
                        "скачок у прямой — ${"%.3f".format(r.jumpLine)}"
                    ).forEach {
                        Text("•  $it", color = textColor.copy(alpha = 0.85f), fontSize = 13.sp,
                            lineHeight = 18.sp, modifier = Modifier.padding(vertical = 2.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Про шестидесятый день модели не сообщали. Константное приближение " +
                            "занижает ущерб на всех длинных инцидентах и завышает на коротких, " +
                            "поэтому остатки меняют знак — и самый крупный перепад в них " +
                            "приходится как раз на границу срабатывания шифровальщика. " +
                            "Первое же дерево встаёт туда.\n\n" +
                            "Прямая такого сделать не может в принципе: у неё нет разрывов. Она " +
                            "проходит сквозь скачок, недооценивая ущерб до него и переоценивая " +
                            "после. Отсюда и разница в контрольной ошибке — " +
                            "${"%.4f".format(r.line.testMse)} против ${"%.4f".format(r.testMse)}.",
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
                    Text("Размен: шаг против числа итераций",
                        color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                        lineHeight = 21.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Деревья глубины 1, до ${GbLab.ESTIMATORS_MAX} итераций. Для каждой " +
                            "скорости обучения — где минимум контрольной ошибки и что " +
                            "остаётся к концу:",
                        color = textColor.copy(alpha = 0.7f), fontSize = 12.sp, lineHeight = 17.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    r.lrRows.forEach { row ->
                        Text(
                            "•  шаг ${"%.2f".format(row.lr)} — минимум ${"%.4f".format(row.bestMse)} " +
                                "на ${row.bestIter}-й итерации, к ${GbLab.ESTIMATORS_MAX}-й " +
                                "${"%.4f".format(row.finalMse)}",
                            color = textColor.copy(alpha = 0.85f), fontSize = 13.sp,
                            lineHeight = 18.sp, modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Минимум уезжает вправо ровно настолько, насколько уменьшается шаг: " +
                            "их произведение держится в узком коридоре. Но размен не совсем " +
                            "честный — мелкий шаг даёт ещё и лучшее качество, потому что " +
                            "крупный проскакивает мимо оптимума и начинает раскачивать остатки.\n\n" +
                            "Практическое правило отсюда: шаг выбирают настолько малым, " +
                            "насколько позволяет бюджет времени, а число итераций подбирают " +
                            "ранней остановкой по контрольной ошибке.",
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
                    Text("Почему деревья должны быть слабыми",
                        color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                        lineHeight = 21.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Глубина 2, шаг 1,0: минимум ${"%.4f".format(r.depth2Best)} уже на " +
                            "${r.depth2BestIter}-й итерации, к ${GbLab.ESTIMATORS_MAX}-й — " +
                            "${"%.4f".format(r.depth2Final)}",
                        color = bad, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Обучающая ошибка при этом ${"%.4f".format(r.depth2Train)} — данные " +
                            "выучены почти наизусть.",
                        color = textColor.copy(alpha = 0.85f), fontSize = 13.sp, lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Здесь ровно противоположное требование к деревьям, чем в случайном " +
                            "лесу. Лес усредняет независимые модели и гасит их разброс, поэтому " +
                            "ему нужны деревья с низким смещением — глубокие. Бустинг снижает " +
                            "смещение сам, шаг за шагом, и разброс ему гасить нечем: каждое " +
                            "дерево входит в сумму со своим весом навсегда. Сильное дерево " +
                            "здесь просто быстрее затащит в сумму шум.\n\n" +
                            "Отсюда стандартная практика: в бустинге деревья глубины 3–6, " +
                            "в лесу — без ограничений. В нашей задаче признак один, поэтому " +
                            "оптимум — вообще пни.",
                        color = textColor.copy(alpha = 0.8f), fontSize = 13.sp, lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    AskChatButton(accent = accent, onClick = {
                        onOpenChat(
                            "Объясни, пожалуйста, простыми словами, почему получился именно такой " +
                                "результат в теме «$topicTitle» (Решение задачи).\n\n" +
                                "Задача: прогноз ущерба от инцидента по времени до обнаружения. " +
                                "Один признак — дни, от 0 до 120. В истинной зависимости есть " +
                                "разрыв на 60 днях: срабатывает шифровальная нагрузка.\n" +
                                "Эталон (${GbLab.DEFAULT_ESTIMATORS} итераций, шаг " +
                                "${"%.2f".format(GbLab.DEFAULT_LEARNING_RATE)}, пни): обучающая " +
                                "${"%.4f".format(r.trainMse)}, контрольная ${"%.4f".format(r.testMse)}. " +
                                "Константа ${"%.4f".format(r.baseline)}, прямая " +
                                "${"%.4f".format(r.line.testMse)}.\n" +
                                "Порог первого дерева ${"%.2f".format(r.firstThreshold)} дня; скачок " +
                                "модели ${"%.3f".format(r.jumpModel)} при настоящем " +
                                "${"%.3f".format(r.jumpTrue)}, у прямой ${"%.3f".format(r.jumpLine)}.\n" +
                                "Глубина 2 при шаге 1,0: минимум ${"%.4f".format(r.depth2Best)} на " +
                                "${r.depth2BestIter}-й итерации, к ${GbLab.ESTIMATORS_MAX}-й " +
                                "${"%.4f".format(r.depth2Final)}.\n\n" +
                                "Почему обучение на остатках находит разрыв и почему сильные " +
                                "деревья бустингу вредят?"
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
                        "Бустинг строит деревья последовательно: каждое обучается на остатках " +
                            "суммы предыдущих. Для квадратичной ошибки остаток и есть антиградиент.",
                        "Сумма ступенчатых функций воспроизводит разрывы, которых линейная " +
                            "модель выразить не может, и находит их сама — по структуре остатков.",
                        "Шаг обучения и число итераций компенсируют друг друга; мелкий шаг даёт " +
                            "лучшее качество ценой времени.",
                        "Обучающая ошибка падает всегда, поэтому число итераций подбирают " +
                            "ранней остановкой по контрольной выборке, а не по сходимости.",
                        "Деревьям в бустинге положено быть слабыми — в отличие от случайного " +
                            "леса, где им дают расти. Причина в том, что бустинг снижает " +
                            "смещение, а лес — разброс.",
                        "Обучение нельзя распараллелить по деревьям: следующее дерево зависит " +
                            "от всех предыдущих. Это главная практическая плата за качество."
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
        QuizSection(questions = gbQuizIb, textColor = textColor, nodeId = "gb")
    }
}
