package com.eduappml.ui.svm

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

private val svmQuizIb = listOf(
    QuizQuestion(
        "Из всех прямых, одинаково хорошо разделяющих два класса, метод опорных векторов выбирает одну. По какому принципу?",
        listOf(
            QuizOption(
                "Ту, вокруг которой полоса, свободная от наблюдений, максимально широка",
                true
            ),
            QuizOption("Ту, что проходит через центры масс двух классов", false),
            QuizOption("Ту, что максимизирует правдоподобие разметки", false),
            QuizOption("Ту, что даёт наибольшую точность на обучающей выборке", false)
        ),
        "Широкий зазор — это запас прочности к тому, чего в обучающей выборке не было. " +
            "За этим стоит результат теории Вапника-Червоненкиса: чем шире зазор, тем уже класс " +
            "функций, способных так разделить данные, и тем лучше гарантия обобщения. " +
            "Максимизация правдоподобия — принцип логистической регрессии, а не SVM."
    ),
    QuizQuestion(
        "На чистых данных точность почти не меняется в широком диапазоне C, а на данных с выбросами у неё появляется максимум в середине. Почему?",
        listOf(
            QuizOption(
                "При большом C нарушения зазора дороги, и модель перестраивает границу под выбросы вместо того, чтобы их игнорировать",
                true
            ),
            QuizOption("При большом C обучение не успевает сойтись", false),
            QuizOption("Выбросы увеличивают число классов", false),
            QuizOption("Это случайность, при другом seed максимума не будет", false)
        ),
        "C — вес слагаемого, штрафующего нарушения, относительно слагаемого, отвечающего за " +
            "ширину зазора. Когда классы разделимы, обе цели достижимы одновременно и C почти " +
            "не важен. Когда есть легитимные сессии в чужом углу, большое C заставляет модель " +
            "обслуживать именно их — в ущерб всему остальному."
    ),
    QuizQuestion(
        "Что означает разреженность решения SVM и чем она отличается от устройства метода ближайших соседей?",
        listOf(
            QuizOption(
                "Границу определяют только опорные векторы, остальные наблюдения после обучения не нужны — тогда как k-NN хранит всю базу целиком",
                true
            ),
            QuizOption("SVM хранит только часть признаков, отбрасывая неинформативные", false),
            QuizOption("SVM сжимает данные, усредняя близкие наблюдения", false),
            QuizOption("Разреженность означает, что матрица признаков содержит много нулей", false)
        ),
        "Hinge loss равна точно нулю для объектов, классифицированных правильно и лежащих за " +
            "пределами зазора: они не вносят вклада в градиент и входят в решение с нулевым " +
            "коэффициентом. У логистической регрессии функция потерь никогда не обращается в " +
            "ноль, поэтому там на решение влияют все объекты."
    ),
    QuizQuestion(
        "Модель выдала для сессии значение решающей функции 2,7. Можно ли считать, что вероятность аномалии около 0,97?",
        listOf(
            QuizOption(
                "Нет. Это расстояние до границы со знаком, а не вероятность; для вероятности нужна отдельная калибровка",
                true
            ),
            QuizOption("Да, выход SVM уже является вероятностью после применения ядра", false),
            QuizOption("Да, если значение поделить на ширину зазора", false),
            QuizOption("Нет, потому что вероятность не может превышать единицу, нужно обрезать до 1,0", false)
        ),
        "Выход SVM — величина без вероятностного смысла: 2,7 означает «далеко за границей и " +
            "уверенно». Чтобы получить вероятность, поверх выхода обучают одномерную " +
            "логистическую регрессию (калибровка Платта). Без этого шага любая политика вида " +
            "«блокировать при риске выше 0,3» на сыром выходе SVM не имеет смысла."
    )
)

/**
 * Экран «Решение задачи» для метода опорных векторов.
 *
 * Показывает три прогона: эталонный, с малым C и с большим C на данных с
 * выбросами — потому что сравнение именно этих колонок и есть вывод темы.
 * Все числа считаются на лету через [SvmLab].
 */
@Composable
fun SvmResult(
    modifier: Modifier = Modifier,
    title: String?,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit = {}
) {
    val textColor = Color.White
    val accent = Color(0xFFFFD93D)
    val topicTitle = title ?: "Метод опорных векторов"

    // эталон: 10% выбросов, C = 1
    val train = remember { SvmLab.trainSet(SvmLab.DEFAULT_OUTLIERS) }
    val test = remember { SvmLab.testSet(SvmLab.DEFAULT_OUTLIERS) }
    val model = remember(train) {
        SvmLab.train(SvmLab.DEFAULT_C, SvmKernel.LINEAR, 0.0, SvmLab.DEFAULT_ITERATIONS, train)
    }
    val acc = remember(model) { SvmLab.accuracy(test, model) }
    val svCount = remember(model) { model.supportVectorCount() }
    val margin = remember(model) { model.marginWidth() }
    val weights = remember(model) { model.linearWeights() }

    // грязная выборка: 25% выбросов, три значения C
    val dirtyTrain = remember { SvmLab.trainSet(0.25) }
    val dirtyTest = remember { SvmLab.testSet(0.25) }
    val dirtyLow = remember(dirtyTrain) {
        SvmLab.train(0.2, SvmKernel.LINEAR, 0.0, SvmLab.DEFAULT_ITERATIONS, dirtyTrain)
    }
    val dirtyMid = remember(dirtyTrain) {
        SvmLab.train(1.0, SvmKernel.LINEAR, 0.0, SvmLab.DEFAULT_ITERATIONS, dirtyTrain)
    }
    val dirtyHigh = remember(dirtyTrain) {
        SvmLab.train(25.0, SvmKernel.LINEAR, 0.0, SvmLab.DEFAULT_ITERATIONS, dirtyTrain)
    }

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
                    "C = ${SvmLab.DEFAULT_C}, линейное ядро, ${SvmLab.DEFAULT_ITERATIONS} итераций, " +
                        "${(SvmLab.DEFAULT_OUTLIERS * 100).toInt()}% сессий ночного бэкапа. " +
                        "Обучение на ${SvmLab.TRAIN_SIZE} сессиях, оценка на ${SvmLab.TEST_SIZE}.",
                    color = textColor.copy(alpha = 0.85f), fontSize = 13.sp, lineHeight = 18.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Точность = ${"%.4f".format(acc)}",
                    color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Опорных векторов: $svCount из ${SvmLab.TRAIN_SIZE}",
                    color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                )
                if (margin != null && weights != null) {
                    Text(
                        "Ширина зазора = ${"%.4f".format(margin)}, " +
                            "w = (${"%.4f".format(weights[0])}, ${"%.4f".format(weights[1])}), " +
                            "b = ${"%.4f".format(weights[2])}",
                        color = textColor.copy(alpha = 0.8f), fontSize = 12.sp, lineHeight = 17.sp
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Чуть больше половины обучающей выборки оказалось опорными векторами — " +
                        "остальные сессии на положение границы не влияют вовсе и после обучения " +
                        "не нужны.",
                    color = textColor.copy(alpha = 0.65f), fontSize = 12.sp, lineHeight = 17.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Главный вывод: C на выборке с 25% выбросов",
                    color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp
                )
                Spacer(Modifier.height(10.dp))

                CRow("C = 0,2", dirtyLow, dirtyTest, textColor, Color(0xFFFF6B6B))
                CRow("C = 1,0", dirtyMid, dirtyTest, textColor, Color(0xFF6BCB77))
                CRow("C = 25", dirtyHigh, dirtyTest, textColor, Color(0xFFFF6B6B))

                Spacer(Modifier.height(10.dp))
                Text(
                    "Точность не растёт с C монотонно: у неё максимум в середине. При C = 0,2 " +
                        "зазор раздут и граница слишком груба. При C = 25 модель перестроилась " +
                        "под ночной бэкап — зазор сузился, опорных векторов почти не осталось, " +
                        "и на новых сессиях стало хуже.\n\n" +
                        "Обратите внимание, что ширина зазора и число опорных векторов меняются " +
                        "монотонно, а точность — нет. Именно поэтому C подбирают перекрёстной " +
                        "проверкой, а не рассуждением.",
                    color = textColor.copy(alpha = 0.8f), fontSize = 13.sp, lineHeight = 19.sp
                )
                Spacer(Modifier.height(12.dp))
                AskChatButton(accent = accent, onClick = {
                    onOpenChat(
                        "Объясни, пожалуйста, простыми словами, почему получился именно такой " +
                            "результат в теме «$topicTitle» (Решение задачи).\n\n" +
                            "Задача: отделение аномального сетевого трафика от легитимного методом " +
                            "опорных векторов. Два признака — средний размер пакета и доля SYN " +
                            "без ответа. ${SvmLab.TRAIN_SIZE} сессий на обучение, ${SvmLab.TEST_SIZE} " +
                            "на контроль. Часть легитимных сессий — ночной бэкап с профилем " +
                            "эксфильтрации.\n" +
                            "Эталон (C=1, линейное ядро, 10% выбросов): точность ${"%.4f".format(acc)}, " +
                            "опорных векторов $svCount из ${SvmLab.TRAIN_SIZE}" +
                            (if (margin != null) ", ширина зазора ${"%.4f".format(margin)}" else "") + ".\n" +
                            "На выборке с 25% выбросов: C=0,2 даёт " +
                            "${"%.4f".format(SvmLab.accuracy(dirtyTest, dirtyLow))}, " +
                            "C=1 даёт ${"%.4f".format(SvmLab.accuracy(dirtyTest, dirtyMid))}, " +
                            "C=25 даёт ${"%.4f".format(SvmLab.accuracy(dirtyTest, dirtyHigh))}.\n\n" +
                            "Почему у точности появляется максимум в середине и что такое зазор?"
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
                    "Метод ищет границу с максимально широкой пустой полосой вокруг неё: " +
                        "ширина зазора — это мера запаса прочности к данным, которых не было в обучении.",
                    "Положение границы определяют только опорные векторы; остальные наблюдения " +
                        "после обучения не нужны — в этом отличие от метода ближайших соседей.",
                    "Параметр C задаёт цену нарушения зазора. На разделимых данных он почти " +
                        "безразличен, на данных с выбросами у него есть оптимум, и большое C вредит.",
                    "Выход SVM — расстояние до границы со знаком, а не вероятность; " +
                        "для вероятностной политики требуется отдельная калибровка."
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
        QuizSection(questions = svmQuizIb, textColor = textColor, nodeId = "svm")
    }
}

@Composable
private fun CRow(
    label: String,
    model: SvmLab.SvmModel,
    test: List<FlowSample>,
    textColor: Color,
    color: Color
) {
    val acc = SvmLab.accuracy(test, model)
    val sv = model.supportVectorCount()
    val margin = model.marginWidth()
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(72.dp))
        Text(
            "точность ${"%.4f".format(acc)}   опорных $sv" +
                (if (margin != null) "   зазор ${"%.3f".format(margin)}" else ""),
            color = textColor.copy(alpha = 0.85f), fontSize = 12.sp, lineHeight = 17.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
