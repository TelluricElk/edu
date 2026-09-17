package com.eduappml.ui.knn

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
import kotlin.math.roundToInt

private val knnQuizIb = listOf(
    QuizQuestion(
        "Признаки измеряются в разных единицах: число событий сотнями, доля нерабочего времени долями единицы. Что произойдёт без нормализации?",
        listOf(
            QuizOption(
                "Расстояние будет определяться почти исключительно числом событий, второй признак фактически не будет участвовать в решении",
                true
            ),
            QuizOption("Алгоритм выдаст ошибку о несовместимых единицах", false),
            QuizOption("Точность упадёт до случайного угадывания", false),
            QuizOption("Ничего, метод ближайших соседей нечувствителен к масштабу", false)
        ),
        "Вклад признака в квадрат расстояния пропорционален квадрату его разброса. При отношении " +
            "разбросов около 500 отношение вкладов превышает 250 тысяч. Опаснее всего то, что " +
            "точность падает лишь с 0,86 до 0,80 — ошибка не выглядит ошибкой."
    ),
    QuizQuestion(
        "На базе с чистой разметкой значение k почти не влияет на точность, а на базе с 20% ошибочных вердиктов разница между k = 1 и k = 25 составляет более двадцати процентных пунктов. Почему?",
        listOf(
            QuizOption(
                "При k = 1 ошибочно размеченный сосед определяет ответ полностью, при большом k его голос остаётся в меньшинстве",
                true
            ),
            QuizOption("Большое k всегда точнее, просто на чистых данных это незаметно", false),
            QuizOption("При k = 1 алгоритм не успевает обучиться", false),
            QuizOption("Шум разметки меняет расстояния между точками", false)
        ),
        "Каждая неверно размеченная запись при k = 1 создаёт вокруг себя островок неправильных " +
            "предсказаний. Увеличение k растворяет её голос среди остальных. Отсюда практическое " +
            "правило: k подбирают перекрёстной проверкой, а грубый ориентир — корень из размера базы."
    ),
    QuizQuestion(
        "Взвешивание голосов по расстоянию на чистых данных даёт небольшой выигрыш, а на данных с шумной разметкой ухудшает результат. В чём причина?",
        listOf(
            QuizOption(
                "Оно усиливает голос ближайшего соседа — а если именно он размечен неверно, усиливается ошибка",
                true
            ),
            QuizOption("Оно замедляет вычисления и приходится брать меньшее k", false),
            QuizOption("Оно ломает нормализацию признаков", false),
            QuizOption("Это случайность, при другом seed результат был бы обратным", false)
        ),
        "Вес обратно пропорционален расстоянию, поэтому самый близкий сосед получает наибольший " +
            "вес. Это ровно то, что нужно на чистых данных, и ровно то, что вредит, когда среди " +
            "близких соседей встречаются записи с ошибочным вердиктом."
    ),
    QuizQuestion(
        "Почему метод ближайших соседей называют ленивым и чем это оборачивается на практике?",
        listOf(
            QuizOption(
                "Обучения нет вовсе — вся работа выполняется в момент запроса, поэтому стоимость переносится на применение и требуется хранить всю базу",
                true
            ),
            QuizOption("Он медленно сходится и требует много итераций обучения", false),
            QuizOption("Он игнорирует часть обучающей выборки ради скорости", false),
            QuizOption("Он обучается только при первом запросе, а потом использует кэш", false)
        ),
        "Новый разобранный инцидент добавляется в базу и работает немедленно, без переобучения — " +
            "это плюс. Минус в том, что каждый запрос требует сравнения со всей базой, а сама " +
            "модель буквально состоит из записей, что создаёт ещё и вопросы хранения персональных данных."
    )
)

/**
 * Экран «Решение задачи» для метода ближайших соседей.
 *
 * Показывает три прогона одной модели: эталонный, без нормализации и на базе
 * с шумной разметкой при разных k. Все числа считаются на лету через [KnnLab].
 */
@Composable
fun KnnResult(
    modifier: Modifier = Modifier,
    title: String?,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit = {}
) {
    val textColor = Color.White
    val accent = Color(0xFFFFD93D)
    val topicTitle = title ?: "Метод ближайших соседей"

    val base = remember { KnnLab.baseSet(KnnLab.DEFAULT_BASE_SIZE, 0.0) }
    val test = remember { KnnLab.testSet() }
    val scNorm = remember(base) { KnnLab.scales(base, true) }
    val scRaw = remember(base) { KnnLab.scales(base, false) }

    val accNorm = remember(base) {
        KnnLab.accuracy(test, base, KnnLab.DEFAULT_K, KnnMetric.EUCLIDEAN, KnnWeighting.UNIFORM, scNorm)
    }
    val accRaw = remember(base) {
        KnnLab.accuracy(test, base, KnnLab.DEFAULT_K, KnnMetric.EUCLIDEAN, KnnWeighting.UNIFORM, scRaw)
    }
    val ratio = remember(base) { KnnLab.scaleRatio(base) }

    val noisy = remember { KnnLab.baseSet(KnnLab.DEFAULT_BASE_SIZE, 0.2) }
    val scNoisy = remember(noisy) { KnnLab.scales(noisy, true) }
    val accNoisyK1 = remember(noisy) {
        KnnLab.accuracy(test, noisy, 1, KnnMetric.EUCLIDEAN, KnnWeighting.UNIFORM, scNoisy)
    }
    val accNoisyK25 = remember(noisy) {
        KnnLab.accuracy(test, noisy, 25, KnnMetric.EUCLIDEAN, KnnWeighting.UNIFORM, scNoisy)
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
                    "k = ${KnnLab.DEFAULT_K}, евклидово расстояние, равное голосование, " +
                        "нормализация включена, база из ${KnnLab.DEFAULT_BASE_SIZE} алертов без " +
                        "ошибок разметки. Оценка на ${KnnLab.TEST_SIZE} контрольных алертах.",
                    color = textColor.copy(alpha = 0.85f), fontSize = 13.sp, lineHeight = 18.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Точность = ${"%.4f".format(accNorm)}",
                    color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Цена масштаба признаков", color = textColor,
                    fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "С нормализацией: ${"%.4f".format(accNorm)}",
                    color = Color(0xFF6BCB77), fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Без нормализации: ${"%.4f".format(accRaw)}",
                    color = Color(0xFFFF6B6B), fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Разброс числа событий в базе примерно в ${ratio.roundToInt()} раз больше " +
                        "разброса доли нерабочего времени. В квадрат расстояния вклады входят " +
                        "квадратами, то есть отношение вкладов — порядка " +
                        "${(ratio * ratio / 1000.0).roundToInt()} тысяч: второй признак не " +
                        "участвует в решении вообще.\n\n" +
                        "И вот что важнее самих цифр. Разница в точности — всего " +
                        "${"%.3f".format(accNorm - accRaw)}. Такую просадку в отчёте не заметит " +
                        "никто, а половина признаков при этом выброшена.",
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
                Text("Когда k перестаёт быть безразличным", color = textColor,
                    fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "На базе, где 20% алертов разобраны неверно:",
                    color = textColor.copy(alpha = 0.7f), fontSize = 12.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "k = 1:   ${"%.4f".format(accNoisyK1)}",
                    color = Color(0xFFFF6B6B), fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
                Text(
                    "k = 25:  ${"%.4f".format(accNoisyK25)}",
                    color = Color(0xFF6BCB77), fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Разница в ${"%.3f".format(accNoisyK25 - accNoisyK1)} — и это та самая " +
                        "настройка, которая на чистой базе выглядела совершенно неважной. " +
                        "В реальной базе SOC ошибочные вердикты есть всегда.",
                    color = textColor.copy(alpha = 0.8f), fontSize = 13.sp, lineHeight = 19.sp
                )
                Spacer(Modifier.height(12.dp))
                AskChatButton(accent = accent, onClick = {
                    onOpenChat(
                        "Объясни, пожалуйста, простыми словами, почему получился именно такой " +
                            "результат в теме «$topicTitle» (Решение задачи).\n\n" +
                            "Задача: триаж алертов в SOC методом k ближайших соседей. Два признака — " +
                            "число событий (0…500) и доля активности вне рабочего времени (0…1). " +
                            "База из ${KnnLab.DEFAULT_BASE_SIZE} разобранных алертов, " +
                            "${KnnLab.TEST_SIZE} контрольных, три вердикта.\n" +
                            "Эталон (k=${KnnLab.DEFAULT_K}, евклид, равное голосование, нормализация): " +
                            "точность ${"%.4f".format(accNorm)}.\n" +
                            "Без нормализации: ${"%.4f".format(accRaw)} при отношении разбросов " +
                            "признаков около ${ratio.roundToInt()}.\n" +
                            "На базе с 20% ошибочных вердиктов: k=1 даёт ${"%.4f".format(accNoisyK1)}, " +
                            "k=25 даёт ${"%.4f".format(accNoisyK25)}.\n\n" +
                            "Почему масштаб признаков так важен именно для этого метода и почему " +
                            "k начинает иметь значение только при шумной разметке?"
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
                    "Метод не обучается: он хранит базу прецедентов и отвечает по аналогии, " +
                        "а объяснением служат сами найденные случаи.",
                    "Масштаб признаков — часть модели, а не подготовка к ней: другое расстояние " +
                        "даёт других соседей и другой ответ.",
                    "Ошибка масштабирования не проявляется как ошибка — точность падает " +
                        "незначительно, хотя часть признаков выброшена полностью.",
                    "Значение k важно ровно настолько, насколько шумна разметка базы; " +
                        "взвешивание по расстоянию на шумных данных вредит."
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
        QuizSection(questions = knnQuizIb, textColor = textColor, nodeId = "knn")
    }
}
