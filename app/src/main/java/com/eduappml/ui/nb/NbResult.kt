package com.eduappml.ui.nb

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

private val nbQuiz = listOf(
    QuizQuestion(
        "В чём состоит «наивность» наивного Байеса?",
        listOf(
            QuizOption(
                "В допущении, что признаки внутри класса независимы, — это позволяет перемножать их правдоподобия",
                true
            ),
            QuizOption("В том, что модель обучается всего за один проход по данным", false),
            QuizOption("В том, что априорные вероятности берутся равными", false),
            QuizOption("В том, что признаки считаются нормально распределёнными", false)
        ),
        "Независимость позволяет заменить одну многомерную плотность на произведение одномерных. " +
            "Именно это делает метод устойчивым на малых выборках и в высоких размерностях — " +
            "и оно же почти всегда неверно. Нормальность признаков — отдельное допущение, " +
            "оно относится к гауссовскому варианту, а не к «наивности»."
    ),
    QuizQuestion(
        "Вы подняли связь признаков внутри класса, и F1 упал с 0,84 до 0,73. Что при этом произошло со средней уверенностью модели?",
        listOf(
            QuizOption("Практически не изменилась — модель стала чаще ошибаться, не став менее уверенной", true),
            QuizOption("Упала пропорционально F1", false),
            QuizOption("Упала до 0,5, то есть модель начала сомневаться", false),
            QuizOption("Выросла до 1,0", false)
        ),
        "Скоррелированные признаки модель считает независимыми уликами и складывает их логарифмы " +
            "в полную силу, преувеличивая силу свидетельства. Оценки прижимаются к нулю и единице " +
            "независимо от того, права модель или нет. Отсюда главный практический вывод: наивный " +
            "Байес хорошо отвечает «какой класс» и плохо — «с какой вероятностью»."
    ),
    QuizQuestion(
        "Зачем нужно сглаживание дисперсии и что происходит, если переусердствовать?",
        listOf(
            QuizOption(
                "Оно спасает от нулевой дисперсии, но в избытке делает все распределения одинаково широкими и модель перестаёт различать классы",
                true
            ),
            QuizOption("Оно ускоряет обучение, а в избытке замедляет его", false),
            QuizOption("Оно нужно только для мультиномиального варианта", false),
            QuizOption("Оно исправляет нарушение независимости признаков", false)
        ),
        "Нулевая дисперсия даёт бесконечную плотность и минус бесконечность в логарифме для всех " +
            "остальных значений — модель начинает отвергать любой объект, отличающийся от виденного. " +
            "Это прямой аналог сглаживания Лапласа. Но большая добавка выравнивает дисперсии, " +
            "квадратичные члены в границе решения сокращаются, и кривая граница выпрямляется в прямую."
    ),
    QuizQuestion(
        "Почему априорная вероятность в интерактиве задаётся отдельным слайдером, а не берётся из обучающей выборки?",
        listOf(
            QuizOption(
                "Доля DGA в обучающем корпусе выровнена искусственно, а базовая частота в реальном потоке несоизмеримо ниже",
                true
            ),
            QuizOption("Потому что из выборки её посчитать невозможно", false),
            QuizOption("Чтобы модель обучалась быстрее", false),
            QuizOption("Потому что априорная вероятность не влияет на результат", false)
        ),
        "Обучающий набор балансируют, чтобы модели хватило примеров редкого класса. Если взять " +
            "априорную вероятность оттуда, модель унаследует неверную базовую частоту. В SOC её " +
            "оценивают наблюдением потока. В sklearn за это отвечает параметр `priors` у GaussianNB — " +
            "без него берётся доля из выборки."
    )
)

/**
 * Экран «Решение задачи» для наивного Байеса.
 *
 * Показывает два прогона одной и той же модели — на независимых признаках и
 * на сильно связанных — потому что сравнение этих двух колонок и есть главный
 * вывод темы. Все числа считаются на лету через [NbLab].
 */
@Composable
fun NbResult(
    modifier: Modifier = Modifier,
    title: String?,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit = {}
) {
    val textColor = Color.White
    val accent = Color(0xFFFFD93D)
    val topicTitle = title ?: "Наивный Байес"

    val prior = NbLab.DEFAULT_PRIOR
    val thr = NbLab.DEFAULT_THRESHOLD

    // независимые признаки — допущение метода выполняется
    val trainIndep = remember { NbLab.trainSet(0.0, NbLab.DEFAULT_TRAIN_SIZE) }
    val testIndep = remember { NbLab.testSet(0.0) }
    val modelIndep = remember(trainIndep) { NbLab.fit(trainIndep, NbLab.DEFAULT_VAR_SMOOTHING) }
    val cmIndep = remember(modelIndep) { NbLab.evaluate(testIndep, modelIndep, prior, thr) }
    val confIndep = remember(modelIndep) { NbLab.meanConfidence(testIndep, modelIndep, prior) }

    // сильно связанные признаки — допущение нарушено
    val trainDep = remember { NbLab.trainSet(0.9, NbLab.DEFAULT_TRAIN_SIZE) }
    val testDep = remember { NbLab.testSet(0.9) }
    val modelDep = remember(trainDep) { NbLab.fit(trainDep, NbLab.DEFAULT_VAR_SMOOTHING) }
    val cmDep = remember(modelDep) { NbLab.evaluate(testDep, modelDep, prior, thr) }
    val confDep = remember(modelDep) { NbLab.meanConfidence(testDep, modelDep, prior) }

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
                    "Независимые признаки, обучающая выборка ${NbLab.DEFAULT_TRAIN_SIZE} доменов, " +
                        "априорная вероятность ${"%.2f".format(prior)}, порог ${"%.1f".format(thr)}, " +
                        "без сглаживания. Оценка на ${NbLab.TEST_SIZE} контрольных доменах.",
                    color = textColor.copy(alpha = 0.85f), fontSize = 13.sp, lineHeight = 18.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "TP = ${cmIndep.tp}   FP = ${cmIndep.fp}   TN = ${cmIndep.tn}   FN = ${cmIndep.fn}",
                    color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Precision = ${"%.4f".format(cmIndep.precision)}, " +
                        "Recall = ${"%.4f".format(cmIndep.recall)}, " +
                        "F1 = ${"%.4f".format(cmIndep.f1)}, " +
                        "Accuracy = ${"%.4f".format(cmIndep.accuracy)}",
                    color = textColor.copy(alpha = 0.9f), fontSize = 13.sp, lineHeight = 18.sp
                )

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = textColor.copy(alpha = 0.15f))
                Spacer(Modifier.height(12.dp))

                Text("Модель целиком — восемь чисел", color = textColor,
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                modelIndep.legit?.let { s ->
                    Text(
                        "Легитимные: энтропия ${"%.3f".format(s.means[0])} " +
                            "(дисперсия ${"%.4f".format(s.variances[0])}), " +
                            "доля цифр ${"%.3f".format(s.means[1])} " +
                            "(дисперсия ${"%.4f".format(s.variances[1])})",
                        color = textColor.copy(alpha = 0.8f), fontSize = 12.sp, lineHeight = 17.sp
                    )
                }
                modelIndep.dga?.let { s ->
                    Text(
                        "DGA: энтропия ${"%.3f".format(s.means[0])} " +
                            "(дисперсия ${"%.4f".format(s.variances[0])}), " +
                            "доля цифр ${"%.3f".format(s.means[1])} " +
                            "(дисперсия ${"%.4f".format(s.variances[1])})",
                        color = textColor.copy(alpha = 0.8f), fontSize = 12.sp, lineHeight = 17.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Главный вывод темы",
                    color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Признаки независимы (допущение выполняется):",
                    color = textColor.copy(alpha = 0.7f), fontSize = 12.sp
                )
                Text(
                    "F1 = ${"%.3f".format(cmIndep.f1)},  уверенность модели = ${"%.3f".format(confIndep)}",
                    color = Color(0xFF6BCB77), fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Признаки сильно связаны (допущение нарушено):",
                    color = textColor.copy(alpha = 0.7f), fontSize = 12.sp
                )
                Text(
                    "F1 = ${"%.3f".format(cmDep.f1)},  уверенность модели = ${"%.3f".format(confDep)}",
                    color = Color(0xFFFF6B6B), fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "F1 упал на ${"%.3f".format(cmIndep.f1 - cmDep.f1)}, а уверенность модели " +
                        "изменилась на ${"%.3f".format(confDep - confIndep)} — то есть практически " +
                        "никак. Модель стала ошибаться заметно чаще и не подала об этом ни одного " +
                        "сигнала. Если ваша политика безопасности сформулирована в вероятностях, " +
                        "сырой наивный Байес использовать нельзя — нужна калибровка.",
                    color = textColor.copy(alpha = 0.8f), fontSize = 13.sp, lineHeight = 19.sp
                )
                Spacer(Modifier.height(12.dp))
                AskChatButton(accent = accent, onClick = {
                    onOpenChat(
                        "Объясни, пожалуйста, простыми словами, почему получился именно такой " +
                            "результат в теме «$topicTitle» (Решение задачи).\n\n" +
                            "Задача: детект DGA-доменов по двум признакам — энтропии имени и доле цифр. " +
                            "${NbLab.DEFAULT_TRAIN_SIZE} доменов на обучение, ${NbLab.TEST_SIZE} на контроль, " +
                            "около трети — DGA.\n" +
                            "При независимых признаках: TP=${cmIndep.tp}, FP=${cmIndep.fp}, " +
                            "TN=${cmIndep.tn}, FN=${cmIndep.fn}, F1=${"%.3f".format(cmIndep.f1)}, " +
                            "средняя уверенность модели ${"%.3f".format(confIndep)}.\n" +
                            "При сильно связанных признаках: F1=${"%.3f".format(cmDep.f1)}, " +
                            "средняя уверенность ${"%.3f".format(confDep)}.\n\n" +
                            "Почему точность упала, а уверенность осталась прежней, и что с этим делают на практике?"
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
                    "Наивный Байес перемножает правдоподобия признаков, сознательно считая их " +
                        "независимыми, — и обучается одним проходом по данным.",
                    "При нарушении независимости точность падает, а уверенность модели остаётся " +
                        "прежней: метод не сигнализирует, что ему стало хуже.",
                    "Числа на выходе выглядят как вероятности, но плохо откалиброваны — для политик, " +
                        "сформулированных в вероятностях, требуется отдельная калибровка.",
                    "Априорная вероятность берётся из наблюдения потока, а не из искусственно " +
                        "сбалансированной обучающей выборки."
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
        QuizSection(questions = nbQuiz, textColor = textColor, nodeId = "nb")
    }
}
