package com.eduappml.ui.gb

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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eduappml.ui.common.AskChatButton
import com.eduappml.ui.common.LessonScaffold
import com.eduappml.ui.common.buildInteractiveChatPrompt
import com.eduappml.ui.lr.LrLabMilitary
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import com.eduappml.ui.common.designPx

/**
 * Военный вариант экрана "Интерактив" для темы градиентного бустинга: та
 * же логика, что у [GbInteractive] (который остаётся нетронутым и больше
 * не вызывается для id = "gb"), только с примером расхода топлива вместо
 * цены квартиры.
 */
@Composable
fun GbInteractiveMilitary(
    modifier: Modifier = Modifier,
    title: String?,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onOpenChat: (String) -> Unit = {}
) {
    val textColor = Color.White
    val accent = Color(0xFF00C2A8)
    val topicTitle = title ?: "Градиентный бустинг"

    var nEstimators by remember { mutableIntStateOf(30) }
    var learningRate by remember { mutableFloatStateOf(0.15f) }

    var model by remember { mutableStateOf(GbLabMilitary.train(nEstimators, learningRate)) }
    LaunchedEffect(nEstimators, learningRate) {
        delay(150)
        model = GbLabMilitary.train(nEstimators, learningRate)
    }

    val trainMse = model.trainMseHistory.lastOrNull() ?: 0f
    val testMse = model.testMseHistory.lastOrNull() ?: 0f
    val bestTestIdx = remember(model) { model.testMseHistory.indices.minByOrNull { model.testMseHistory[it] } ?: 0 }

    LessonScaffold(
        eyebrow = "Интерактив",
        title = title ?: "Градиентный бустинг",
        onBack = onBack,
        onNext = onNext,
        nextLabel = "К решению →",
        accent = accent,
        modifier = modifier
    ) {
        Text(
            "Каждая итерация добавляет ещё один «пенёк», исправляющий остатки предыдущих. Следите за ошибкой на контрольной выборке — в какой-то момент она перестаёт падать.",
            fontSize = 14.sp, color = textColor.copy(alpha = 0.75f), modifier = Modifier.padding(bottom = 16.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
        ) {
            GbFitCanvasMilitary(model = model)
        }

        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
        ) {
            GbLossCanvasMilitary(model = model, bestTestIdx = bestTestIdx)
        }
        Text(
            "Синяя линия — ошибка на обучающей выборке, оранжевая — на контрольной. Точка — лучшая итерация по контрольной ошибке.",
            fontSize = 11.5.sp, color = textColor.copy(alpha = 0.6f), modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(Modifier.height(14.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Параметры обучения", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))

                Text("Число итераций (n_estimators) = $nEstimators", color = textColor, fontSize = 14.sp)
                Slider(value = nEstimators.toFloat(), onValueChange = { nEstimators = it.roundToInt() }, valueRange = 1f..80f,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White))

                Spacer(Modifier.height(8.dp))
                Text("Скорость обучения (learning rate) = ${"%.2f".format(learningRate)}", color = textColor, fontSize = 14.sp)
                Slider(value = learningRate, onValueChange = { learningRate = it }, valueRange = 0.01f..0.6f,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White))

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = textColor.copy(alpha = 0.15f))
                Spacer(Modifier.height(12.dp))

                Text("MSE на обучающей выборке: ${"%.1f".format(trainMse)}", color = textColor.copy(alpha = 0.8f), fontSize = 14.sp)
                Text("MSE на контрольной выборке: ${"%.1f".format(testMse)}", color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Лучшая итерация по контрольной выборке: ${bestTestIdx + 1}", color = textColor.copy(alpha = 0.7f), fontSize = 13.sp)

                Spacer(Modifier.height(8.dp))
                Text(
                    text = gbInsightMilitary(nEstimators, learningRate, bestTestIdx, model.testMseHistory.size),
                    color = textColor.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(10.dp))
                AskChatButton(accent = accent, onClick = {
                    onOpenChat(
                        buildInteractiveChatPrompt(
                            topicTitle,
                            "число итераций = $nEstimators, скорость обучения = ${"%.2f".format(learningRate)}",
                            "MSE на обучающей ${"%.1f".format(trainMse)}, на контрольной ${"%.1f".format(testMse)}, лучшая итерация ${bestTestIdx + 1}"
                        )
                    )
                })
            }
        }
    }
}

/** Живое текстовое пояснение, меняющееся вместе с числом итераций и скоростью обучения. */
private fun gbInsightMilitary(nEstimators: Int, learningRate: Float, bestTestIdx: Int, totalIterations: Int): String {
    val overfittingGap = totalIterations - 1 - bestTestIdx
    val stageText = if (overfittingGap == 0) {
        "Лучшая итерация по контрольной выборке — прямо последняя: возможно, модели ещё есть куда улучшаться, попробуйте увеличить число итераций."
    } else if (overfittingGap > totalIterations / 3) {
        "Лучшая точка была достигнута задолго до конца обучения — после неё модель уже переобучалась, продолжая подгоняться под обучающую выборку."
    } else {
        "Модель недавно прошла свою лучшую по контрольной выборке точку — ещё немного итераций назад результат был бы чуть лучше."
    }
    val lrText = when {
        learningRate < 0.05f -> "Маленькая скорость обучения — каждый шаг вносит скромный вклад, поэтому для того же результата нужно больше итераций."
        learningRate > 0.4f -> "Большая скорость обучения — модель обучается быстро, но рискует «перескочить» через оптимум раньше, чем вы ожидаете."
        else -> "Средняя скорость обучения — разумный темп для этого числа итераций."
    }
    return "$stageText $lrText"
}

@Composable
private fun GbFitCanvasMilitary(model: GbLabMilitary.BoostingModel) {
    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val w = size.width
        val h = size.height
        val minDistance = LrLabMilitary.DISTANCE_MIN
        val maxDistance = LrLabMilitary.DISTANCE_MAX
        val fuelValues = LrLabMilitary.trainSet.map { it.fuel }
        val minFuel = fuelValues.min() - 20f
        val maxFuel = fuelValues.max() + 20f

        fun toPx(distance: Float, fuel: Float) = Offset(
            ((distance - minDistance) / (maxDistance - minDistance)) * w,
            h - ((fuel - minFuel) / (maxFuel - minFuel)) * h
        )

        LrLabMilitary.trainSet.forEach { p ->
            drawCircle(Color(0xFF00C2A8), radius = designPx(5f), center = toPx(p.distance, p.fuel))
        }

        var prev: Offset? = null
        var d = minDistance
        while (d <= maxDistance) {
            val pred = GbLabMilitary.predict(model, d)
            val pt = toPx(d, pred)
            prev?.let { drawLine(Color.White, it, pt, strokeWidth = designPx(3f)) }
            prev = pt
            d += (maxDistance - minDistance) / 60f
        }
    }
}

@Composable
private fun GbLossCanvasMilitary(model: GbLabMilitary.BoostingModel, bestTestIdx: Int) {
    Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        val w = size.width
        val h = size.height
        val allValues = model.trainMseHistory + model.testMseHistory
        if (allValues.isEmpty()) return@Canvas
        val maxVal = allValues.max().coerceAtLeast(1f)

        fun toPx(idx: Int, value: Float, n: Int) = Offset(
            (idx.toFloat() / (n - 1).coerceAtLeast(1)) * w,
            h - (value / maxVal) * h
        )

        fun drawSeries(values: List<Float>, color: Color) {
            var prev: Offset? = null
            values.forEachIndexed { idx, v ->
                val pt = toPx(idx, v, values.size)
                prev?.let { drawLine(color, it, pt, strokeWidth = designPx(2.5f)) }
                prev = pt
            }
        }

        drawSeries(model.trainMseHistory, Color(0xFF4D96FF))
        drawSeries(model.testMseHistory, Color(0xFFFF914D))

        if (bestTestIdx < model.testMseHistory.size) {
            val pt = toPx(bestTestIdx, model.testMseHistory[bestTestIdx], model.testMseHistory.size)
            drawCircle(Color.White, radius = designPx(5f), center = pt, style = Stroke(width = designPx(2f)))
        }
    }
}
