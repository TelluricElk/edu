package com.eduappml.ui.logr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eduappml.ui.common.AskChatButton
import com.eduappml.ui.common.LessonScaffold
import com.eduappml.ui.common.buildInteractiveChatPrompt
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Военный вариант экрана "Интерактив" для темы логистической регрессии: та
 * же логика, что у [LogrInteractive] (который остаётся нетронутым и больше
 * не вызывается для id = "logr"), только с примером сдачи норматива по
 * физподготовке вместо сдачи экзамена студентом.
 */
@Composable
fun LogrInteractiveMilitary(
    modifier: Modifier = Modifier,
    title: String?,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onOpenChat: (String) -> Unit = {}
) {
    val textColor = Color.White
    val accent = Color(0xFFFFD93D)
    val topicTitle = title ?: "Логистическая регрессия"

    var learningRate by remember { mutableFloatStateOf(0.3f) }
    var epochs by remember { mutableIntStateOf(150) }
    var threshold by remember { mutableFloatStateOf(0.5f) }

    var fitResult by remember { mutableStateOf(LogrLabMilitary.fit(learningRate, epochs)) }
    LaunchedEffect(learningRate, epochs) {
        delay(120)
        fitResult = LogrLabMilitary.fit(learningRate, epochs)
    }

    val cm = remember(fitResult, threshold) {
        LogrLabMilitary.confusionMatrix(LogrLabMilitary.testSet, fitResult.w, fitResult.b, threshold)
    }

    LessonScaffold(
        eyebrow = "Интерактив",
        title = title ?: "Логистическая регрессия",
        onBack = onBack,
        onNext = onNext,
        nextLabel = "К решению →",
        accent = accent,
        modifier = modifier
    ) {
        Text(
            "Подберите скорость обучения и число эпох, затем подвигайте порог классификации и понаблюдайте за confusion matrix.",
            fontSize = 14.sp, color = textColor.copy(alpha = 0.75f), modifier = Modifier.padding(bottom = 16.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
        ) {
            LogrCanvasMilitary(w = fitResult.w, b = fitResult.b, threshold = threshold)
        }

        Spacer(Modifier.height(10.dp))
        if (fitResult.diverged) {
            Text("Модель разошлась — уменьшите скорость обучения.", color = Color(0xFFFF6B6B), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
        }

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Параметры обучения", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))

                Text("Скорость обучения = ${"%.2f".format(learningRate)}", color = textColor, fontSize = 14.sp)
                Slider(value = learningRate, onValueChange = { learningRate = it }, valueRange = 0.01f..1.5f,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White))

                Spacer(Modifier.height(8.dp))
                Text("Число эпох = $epochs", color = textColor, fontSize = 14.sp)
                Slider(value = epochs.toFloat(), onValueChange = { epochs = it.roundToInt() }, valueRange = 1f..400f,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White))

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = textColor.copy(alpha = 0.15f))
                Spacer(Modifier.height(12.dp))

                Text("Порог классификации = ${"%.2f".format(threshold)}", color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Slider(value = threshold, onValueChange = { threshold = it }, valueRange = 0.05f..0.95f,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent))

                Spacer(Modifier.height(10.dp))
                Text("Confusion matrix на контрольной выборке:", color = textColor.copy(alpha = 0.85f), fontSize = 13.sp)
                Text("TP=${cm.tp}  FP=${cm.fp}  TN=${cm.tn}  FN=${cm.fn}", color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Precision = ${"%.2f".format(cm.precision)}   Recall = ${"%.2f".format(cm.recall)}   Accuracy = ${"%.2f".format(cm.accuracy)}",
                    color = textColor.copy(alpha = 0.85f), fontSize = 13.sp
                )

                Spacer(Modifier.height(8.dp))
                Text(
                    text = logrInsightMilitary(threshold, cm.precision, cm.recall, fitResult.diverged),
                    color = textColor.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(10.dp))
                AskChatButton(accent = accent, onClick = {
                    val resultText = if (fitResult.diverged) {
                        "модель разошлась — скорость обучения слишком велика"
                    } else {
                        "TP=${cm.tp} FP=${cm.fp} TN=${cm.tn} FN=${cm.fn}, precision=${"%.2f".format(cm.precision)}, recall=${"%.2f".format(cm.recall)}, accuracy=${"%.2f".format(cm.accuracy)}"
                    }
                    onOpenChat(
                        buildInteractiveChatPrompt(
                            topicTitle,
                            "скорость обучения = ${"%.2f".format(learningRate)}, эпох = $epochs, порог классификации = ${"%.2f".format(threshold)}",
                            resultText
                        )
                    )
                })
            }
        }
    }
}

/** Живое текстовое пояснение, меняющееся вместе с порогом классификации. */
private fun logrInsightMilitary(threshold: Float, precision: Float, recall: Float, diverged: Boolean): String {
    if (diverged) return "Модель разошлась — сигмоида не может настроиться на данные при такой скорости обучения."
    val thresholdText = when {
        threshold < 0.3f -> "Низкий порог — модель охотно предсказывает «сдал», почти не пропуская таких бойцов (высокий recall), но чаще ошибается в другую сторону (ниже precision)."
        threshold > 0.7f -> "Высокий порог — модель предсказывает «сдал» только при высокой уверенности: меньше ложных срабатываний (выше precision), но больше пропущенных случаев (ниже recall)."
        else -> "Порог около середины — сбалансированное соотношение между precision и recall."
    }
    val tradeoffText = if (precision > recall + 0.15f) {
        "Сейчас модель точнее, чем полна: она реже ошибается, когда говорит «сдал», но пропускает часть реальных «сдал»."
    } else if (recall > precision + 0.15f) {
        "Сейчас модель полнее, чем точна: она находит почти всех, кто сдал, но вместе с ними — и часть тех, кто не сдал."
    } else {
        "Precision и recall сейчас близки друг к другу."
    }
    return "$thresholdText $tradeoffText"
}

@Composable
private fun LogrCanvasMilitary(w: Float, b: Float, threshold: Float) {
    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val width = size.width
        val height = size.height

        fun toPx(hours: Float, prob: Float) = Offset(
            (hours / LogrLabMilitary.HOURS_MAX) * width,
            height - prob * height
        )

        // Зоны решения выше/ниже порога — подсказка, что будет предсказано в каждой из них
        val thresholdY = height - threshold * height
        drawRect(
            color = Color(0xFF6BCB77).copy(alpha = 0.06f),
            topLeft = Offset(0f, 0f),
            size = androidx.compose.ui.geometry.Size(width, thresholdY)
        )
        drawRect(
            color = Color(0xFFFF6B6B).copy(alpha = 0.06f),
            topLeft = Offset(0f, thresholdY),
            size = androidx.compose.ui.geometry.Size(width, height - thresholdY)
        )
        drawLine(Color.White.copy(alpha = 0.35f), Offset(0f, thresholdY), Offset(width, thresholdY), strokeWidth = 1.5f)

        // сигмоида
        var prev: Offset? = null
        var h = 0f
        while (h <= LogrLabMilitary.HOURS_MAX) {
            val prob = LogrLabMilitary.predictProba(h, w, b)
            val pt = toPx(h, prob)
            prev?.let { drawLine(Color.White, it, pt, strokeWidth = 3f) }
            prev = pt
            h += 0.2f
        }

        // точки данных
        LogrLabMilitary.trainSet.forEach { p ->
            val pt = toPx(p.hours, p.passed)
            val color = if (p.passed >= 1f) Color(0xFF6BCB77) else Color(0xFFFF6B6B)
            drawCircle(color, radius = 5f, center = pt)
        }
    }
}
