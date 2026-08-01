package com.eduappml.ui.result

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eduappml.ui.ae.AeResult
import com.eduappml.ui.cnn.CnnResult
import com.eduappml.ui.common.AskChatButton
import com.eduappml.ui.common.LessonScaffold
import com.eduappml.ui.common.buildResultChatPrompt
import com.eduappml.ui.dm.DmResult
import com.eduappml.ui.dt.DtResult
import com.eduappml.ui.fc.FcResult
import com.eduappml.ui.gan.GanResult
import com.eduappml.ui.gb.GbResult
import com.eduappml.ui.gnn.GnnResult
import com.eduappml.ui.km.KmResult
import com.eduappml.ui.knn.KnnLab
import com.eduappml.ui.common.QuizSection
import com.eduappml.ui.logr.LogrResult
import com.eduappml.ui.lr.LrResult
import com.eduappml.ui.nb.NbResult
import com.eduappml.ui.rf.RfResult
import com.eduappml.ui.rl.RlResult
import com.eduappml.ui.rnn.RnnResult
import com.eduappml.ui.som.SomResult
import com.eduappml.ui.svm.SvmResult
import com.eduappml.ui.tr.TrResult
import kotlin.math.roundToInt

/**
 * Экран "Решение задачи" (пузырь-алмаз). Показывает итог эталонного прогона
 * на рекомендованных гиперпараметрах и встроенный тест по теме — для каждого
 * алгоритма своя реализация, диспетчеризуемая по id.
 *
 * [onOpenChat] — колбэк для кнопки "Почему получился именно такой результат"
 * (см. аналогичный параметр в MathScreen.kt) — прокидывается в каждую тему.
 */
@Composable
fun ResultScreen(
    modifier: Modifier = Modifier,
    id: String,
    title: String? = null,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit = {}
) {
    when (id) {
        "knn" -> KnnResult(modifier = modifier, title = title, onBack = onBack, onOpenChat = onOpenChat)
        "lr" -> LrResult(modifier = modifier, title = title, onBack = onBack, onOpenChat = onOpenChat)
        "logr" -> LogrResult(modifier = modifier, title = title, onBack = onBack, onOpenChat = onOpenChat)
        "svm" -> SvmResult(modifier = modifier, title = title, onBack = onBack, onOpenChat = onOpenChat)
        "dt" -> DtResult(modifier = modifier, title = title, onBack = onBack, onOpenChat = onOpenChat)
        "nb" -> NbResult(modifier = modifier, title = title, onBack = onBack, onOpenChat = onOpenChat)
        "rf" -> RfResult(modifier = modifier, title = title, onBack = onBack, onOpenChat = onOpenChat)
        "gb" -> GbResult(modifier = modifier, title = title, onBack = onBack, onOpenChat = onOpenChat)
        "km" -> KmResult(modifier = modifier, title = title, onBack = onBack, onOpenChat = onOpenChat)
        "fc" -> FcResult(modifier = modifier, title = title, onBack = onBack, onOpenChat = onOpenChat)
        "som" -> SomResult(modifier = modifier, title = title, onBack = onBack, onOpenChat = onOpenChat)
        "rl" -> RlResult(modifier = modifier, title = title, onBack = onBack, onOpenChat = onOpenChat)
        "ae" -> AeResult(modifier = modifier, title = title, onBack = onBack, onOpenChat = onOpenChat)
        "gan" -> GanResult(modifier = modifier, title = title, onBack = onBack, onOpenChat = onOpenChat)
        "cnn" -> CnnResult(modifier = modifier, title = title, onBack = onBack, onOpenChat = onOpenChat)
        "rnn" -> RnnResult(modifier = modifier, title = title, onBack = onBack, onOpenChat = onOpenChat)
        "gnn" -> GnnResult(modifier = modifier, title = title, onBack = onBack, onOpenChat = onOpenChat)
        "tr" -> TrResult(modifier = modifier, title = title, onBack = onBack, onOpenChat = onOpenChat)
        "dm" -> DmResult(modifier = modifier, title = title, onBack = onBack, onOpenChat = onOpenChat)
        else -> ComingSoonResult(modifier = modifier, title = title, id = id, onBack = onBack)
    }
}

@Composable
private fun ComingSoonResult(modifier: Modifier = Modifier, title: String?, id: String, onBack: () -> Unit) {
    LessonScaffold(
        eyebrow = "Решение задачи",
        title = title ?: id,
        onBack = onBack,
        accent = Color(0xFFE53935),
        modifier = modifier
    ) {
        Text(
            text = "Решение задачи для этой темы ещё готовится",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private val knnQuiz = listOf(
    com.eduappml.ui.common.QuizQuestion(
        question = "Что произойдёт с моделью, если выбрать k = 1?",
        options = listOf(
            com.eduappml.ui.common.QuizOption("Модель станет очень чувствительна к шуму и выбросам", true),
            com.eduappml.ui.common.QuizOption("Модель всегда даст 100% точность на новых данных", false),
            com.eduappml.ui.common.QuizOption("Модель перестанет учитывать обучающую выборку", false),
            com.eduappml.ui.common.QuizOption("Расстояния между точками перестанут считаться", false)
        ),
        explanation = "При k = 1 класс определяется единственным ближайшим соседом, поэтому один шумный или ошибочно размеченный пример может полностью изменить предсказание."
    ),
    com.eduappml.ui.common.QuizQuestion(
        question = "Что произойдёт при слишком большом k (близком к размеру всей выборки)?",
        options = listOf(
            com.eduappml.ui.common.QuizOption("Модель почти всегда будет предсказывать самый частый класс", true),
            com.eduappml.ui.common.QuizOption("Модель станет точнее для редких классов", false),
            com.eduappml.ui.common.QuizOption("Алгоритм перестанет работать", false),
            com.eduappml.ui.common.QuizOption("Точность гарантированно вырастет до 100%", false)
        ),
        explanation = "Голосование среди слишком многих соседей сглаживает границы между классами — в пределе модель просто выдаёт самый распространённый класс в выборке."
    ),
    com.eduappml.ui.common.QuizQuestion(
        question = "Почему k-NN называют «ленивым» алгоритмом?",
        options = listOf(
            com.eduappml.ui.common.QuizOption("Он не строит модель заранее, а хранит все данные и считает всё во время предсказания", true),
            com.eduappml.ui.common.QuizOption("Он работает медленно на любых объёмах данных", false),
            com.eduappml.ui.common.QuizOption("Он никогда не достигает высокой точности", false),
            com.eduappml.ui.common.QuizOption("Его нельзя использовать для регрессии", false)
        ),
        explanation = "В отличие от линейной регрессии или деревьев, k-NN не «обучает» параметры заранее — вся работа (поиск соседей) откладывается до момента предсказания."
    ),
    com.eduappml.ui.common.QuizQuestion(
        question = "Как взвешивание по расстоянию (distance weighting) отличается от равного голосования?",
        options = listOf(
            com.eduappml.ui.common.QuizOption("Более близкие соседи получают больший вес голоса, чем дальние", true),
            com.eduappml.ui.common.QuizOption("Учитываются только соседи одного класса", false),
            com.eduappml.ui.common.QuizOption("Голос имеют только k/2 ближайших соседей", false),
            com.eduappml.ui.common.QuizOption("Все соседи по умолчанию имеют одинаковый вес независимо от настройки", false)
        ),
        explanation = "При взвешивании по расстоянию вклад соседа в голосование обратно пропорционален расстоянию до него — близкие точки влияют сильнее дальних."
    )
)

@Composable
private fun KnnResult(modifier: Modifier = Modifier, title: String?, onBack: () -> Unit, onOpenChat: (String) -> Unit = {}) {
    val textColor = Color.White
    val accuracy = remember {
        KnnLab.evaluateAccuracy(KnnLab.referenceK, KnnLab.referenceMetric, KnnLab.referenceWeighting)
    }
    val topicTitle = title ?: "k-NN"

    LessonScaffold(
        eyebrow = "Решение задачи",
        title = title ?: "k-NN",
        onBack = onBack,
        accent = Color(0xFFE53935),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Эталонное решение", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                val paramsText = "k = ${KnnLab.referenceK}, метрика — ${KnnLab.referenceMetric.label}, " +
                    "взвешивание — ${KnnLab.referenceWeighting.label}."
                Text(
                    text = "Параметры: $paramsText",
                    color = textColor.copy(alpha = 0.85f),
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(4.dp))
                val metricText = "точность на контрольной выборке ${(accuracy * 100).roundToInt()}%"
                Text(
                    text = "Точность на контрольной выборке: ${(accuracy * 100).roundToInt()}%",
                    color = textColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))
                AskChatButton(
                    accent = Color(0xFFE53935),
                    onClick = { onOpenChat(buildResultChatPrompt(topicTitle, paramsText, metricText)) }
                )
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
                    "k-NN не строит модель заранее — он «ленивый» алгоритм, всё считает в момент предсказания.",
                    "Маленькое k делает модель чувствительной к шуму, большое — сглаживает границы между классами.",
                    "Взвешивание по расстоянию снижает влияние дальних, менее похожих соседей.",
                    "Выбор метрики расстояния имеет значение, особенно если признаки в разных масштабах."
                ).forEach { line ->
                    Text("•  $line", color = textColor.copy(alpha = 0.85f), fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        QuizSection(questions = knnQuiz, textColor = textColor)
    }
}
