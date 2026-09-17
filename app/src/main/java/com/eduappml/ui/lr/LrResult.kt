package com.eduappml.ui.lr

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

private val lrQuiz = listOf(
    QuizQuestion(
        "Признак измеряется десятками тысяч узлов. Почему без нормализации модель разваливается уже при скорости обучения 0,0008, хотя с нормализацией спокойно работает при 2,0?",
        listOf(
            QuizOption(
                "Градиент по наклону содержит множителем само значение признака, поэтому он на порядки крупнее градиента по свободному члену, и одна скорость обучения не подходит обоим параметрам",
                true
            ),
            QuizOption("Большие числа не помещаются в тип данных и переполняются", false),
            QuizOption("Нормализация уменьшает ошибку модели, поэтому спуск идёт мягче", false),
            QuizOption("Без нормализации функция потерь перестаёт быть выпуклой", false)
        ),
        "Дело исключительно в обусловленности задачи. Найденная прямая в обоих случаях одна и та же — " +
            "нормализация не меняет модель и не улучшает её качество, она лишь делает «овраг» функции " +
            "потерь круглым, а допустимый шаг — большим."
    ),
    QuizQuestion(
        "При росте доли атак с амплификацией наклон падает с 0,42 почти до нуля. Что это говорит о методе наименьших квадратов?",
        listOf(
            QuizOption(
                "Вклад наблюдения в потери пропорционален квадрату остатка, поэтому влияние выброса ничем не ограничено — метод не робастен",
                true
            ),
            QuizOption("Модель переобучилась и её нужно регуляризовать", false),
            QuizOption("Выбросов стало больше половины, поэтому они и есть новое большинство", false),
            QuizOption("Градиентный спуск застрял в локальном минимуме", false)
        ),
        "Наблюдение с остатком 40 весит столько же, сколько 400 наблюдений с остатком 2. Модель честно " +
            "минимизирует сумму и жертвует точностью на обычных атаках. Лечится это не настройкой, " +
            "а робастной функцией потерь или выделением амплификации в отдельный класс с отдельной моделью."
    ),
    QuizQuestion(
        "Помогает ли L2-регуляризация против выбросов в этой задаче?",
        listOf(
            QuizOption(
                "Нет, становится хуже: выброс занижает наклон, а штраф тянет его к нулю в ту же сторону",
                true
            ),
            QuizOption("Да, это основное средство борьбы с выбросами", false),
            QuizOption("Да, но только при очень больших значениях штрафа", false),
            QuizOption("Не влияет никак — штраф действует только на свободный член", false)
        ),
        "Регуляризация увеличивает знаменатель в формуле наклона, то есть всегда его занижает. " +
            "Выбросы с амплификацией занижают наклон и без неё, так что два эффекта складываются. " +
            "Регуляризация лечит переобучение и мультиколлинеарность, а не выбросы."
    ),
    QuizQuestion(
        "Коэффициент детерминации получился отрицательным. Что это означает?",
        listOf(
            QuizOption("Модель предсказывает хуже, чем если бы всегда выдавала среднее значение", true),
            QuizOption("В расчёте ошибка, отрицательным он быть не может", false),
            QuizOption("Зависимость обратная: чем больше ботнет, тем меньше полоса", false),
            QuizOption("Данные не нормализованы", false)
        ),
        "Коэффициент детерминации сравнивает сумму квадратов остатков модели с суммой квадратов " +
            "отклонений от среднего. Если первая больше второй, значение уходит ниже нуля. " +
            "Обычная причина — недоученная модель: у неё прямая ещё не дошла до облака точек."
    )
)

/**
 * Экран «Решение задачи» для линейной регрессии.
 *
 * Все числа считаются на лету через [LrLab] при эталонных настройках, а не
 * зашиты константами: если изменится генерация истории атак или алгоритм,
 * экран покажет актуальные значения.
 */
@Composable
fun LrResult(
    modifier: Modifier = Modifier,
    title: String?,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit = {}
) {
    val textColor = Color.White
    val accent = Color(0xFFFFD93D)
    val topicTitle = title ?: "Линейная регрессия"

    val clean = remember { LrLab.generate(0.0) }
    val fitClean = remember(clean) {
        LrLab.fit(clean, LrLab.DEFAULT_LR, LrLab.DEFAULT_EPOCHS, true, 0.0)
    }
    val mClean = remember(fitClean) { LrLab.metrics(clean, fitClean) }

    // та же модель на выборке, где половина мелких атак — с амплификацией
    val dirty = remember { LrLab.generate(0.5) }
    val fitDirty = remember(dirty) {
        LrLab.fit(dirty, LrLab.DEFAULT_LR, LrLab.DEFAULT_EPOCHS, true, 0.0)
    }
    val mDirty = remember(fitDirty) { LrLab.metrics(dirty, fitDirty) }

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
                    "Нормализация включена, скорость обучения ${LrLab.DEFAULT_LR}, " +
                        "${LrLab.DEFAULT_EPOCHS} эпох, без регуляризации, выборка без амплификаций.",
                    color = textColor.copy(alpha = 0.85f), fontSize = 13.sp, lineHeight = 18.sp
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Наклон = ${"%.4f".format(fitClean.slopeReal)} Гбит/с на тысячу узлов",
                    color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Свободный член = ${"%.3f".format(fitClean.interceptReal)} Гбит/с",
                    color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Истинная зависимость, из которой сгенерирована история: " +
                        "${LrLab.TRUE_K} и ${LrLab.TRUE_B}. Модель восстановила наклон практически " +
                        "точно; расхождение в свободном члене — шум выборки, а не ошибка метода.",
                    color = textColor.copy(alpha = 0.7f), fontSize = 12.sp, lineHeight = 17.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "MSE = ${"%.3f".format(mClean.mse)}, MAE = ${"%.3f".format(mClean.mae)} Гбит/с, " +
                        "коэффициент детерминации = ${"%.4f".format(mClean.r2)}",
                    color = textColor.copy(alpha = 0.9f), fontSize = 13.sp, lineHeight = 18.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Прогноз для ботнета в ${LrLab.FORECAST_NODES.roundToInt()} тысяч узлов: " +
                        "${"%.2f".format(fitClean.predict(LrLab.FORECAST_NODES))} Гбит/с",
                    color = accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
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
                    "Та же модель, но половина мелких атак — с амплификацией",
                    color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Наклон = ${"%.4f".format(fitDirty.slopeReal)} вместо ${LrLab.TRUE_K}, " +
                        "свободный член = ${"%.2f".format(fitDirty.interceptReal)} вместо ${LrLab.TRUE_B}, " +
                        "коэффициент детерминации = ${"%.3f".format(mDirty.r2)}.",
                    color = textColor.copy(alpha = 0.85f), fontSize = 13.sp, lineHeight = 18.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Настройки обучения не менялись ни на йоту. Изменились только данные — и прогноз " +
                        "для ботнета в ${LrLab.FORECAST_NODES.roundToInt()} тысяч узлов уехал с " +
                        "${"%.1f".format(fitClean.predict(LrLab.FORECAST_NODES))} до " +
                        "${"%.1f".format(fitDirty.predict(LrLab.FORECAST_NODES))} Гбит/с.",
                    color = textColor.copy(alpha = 0.7f), fontSize = 12.sp, lineHeight = 17.sp
                )
                Spacer(Modifier.height(12.dp))
                AskChatButton(accent = accent, onClick = {
                    onOpenChat(
                        "Объясни, пожалуйста, простыми словами, почему получился именно такой " +
                            "результат в теме «$topicTitle» (Решение задачи).\n\n" +
                            "Задача: прогноз пиковой полосы DDoS-атаки по размеру ботнета, " +
                            "${LrLab.SAMPLE_COUNT} атак в истории. Истинная зависимость: " +
                            "${LrLab.TRUE_K} Гбит/с на тысячу узлов плюс ${LrLab.TRUE_B} Гбит/с фона.\n" +
                            "На чистых данных модель нашла наклон ${"%.4f".format(fitClean.slopeReal)} " +
                            "и фон ${"%.3f".format(fitClean.interceptReal)}, " +
                            "коэффициент детерминации ${"%.4f".format(mClean.r2)}.\n" +
                            "Когда половина мелких атак стала атаками с амплификацией, наклон упал до " +
                            "${"%.4f".format(fitDirty.slopeReal)}, а фон вырос до " +
                            "${"%.2f".format(fitDirty.interceptReal)}.\n\n" +
                            "Почему выбросы так сильно влияют на метод наименьших квадратов и что с этим делают на практике?"
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
                    "Линейная регрессия минимизирует сумму квадратов остатков и отдаёт два числа, " +
                        "которые можно произнести вслух и записать в регламент.",
                    "Масштаб признака определяет допустимую скорость обучения: нормализация " +
                        "расширяет рабочий диапазон в тысячи раз, не меняя саму модель.",
                    "Квадратичная ошибка не робастна — влияние выброса ничем не ограничено, " +
                        "и в безопасности выбросы это часто отдельный класс атак, а не брак измерений.",
                    "Расхождение между MSE и MAE диагностично: если MSE растёт намного быстрее, " +
                        "ошибку делают немногочисленные крупные промахи."
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
        QuizSection(questions = lrQuiz, textColor = textColor, nodeId = "lr")
    }
}
