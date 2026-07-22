package com.eduappml.ui.lr

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
import com.eduappml.ui.common.LessonScaffold
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun LrInteractive(
    modifier: Modifier = Modifier,
    title: String?,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val textColor = Color.White
    val accent = Color(0xFFFF6B6B)

    var learningRate by remember { mutableFloatStateOf(0.04f) }
    var epochs by remember { mutableIntStateOf(25) }

    var result by remember { mutableStateOf(LrLab.fitGradientDescent(learningRate, epochs)) }

    LaunchedEffect(learningRate, epochs) {
        delay(150)
        result = LrLab.fitGradientDescent(learningRate, epochs)
    }

    val (closedW1, closedW0) = remember { LrLab.closedFormFit() }
    val testMse = remember(result) { LrLab.mse(LrLab.testSet, result.w1, result.w0) }
    val testR2 = remember(result) { LrLab.r2(LrLab.testSet, result.w1, result.w0) }

    LessonScaffold(
        eyebrow = "Интерактив",
        title = title ?: "Линейная регрессия",
        onBack = onBack,
        onNext = onNext,
        nextLabel = "К решению →",
        accent = accent,
        modifier = modifier
    ) {
        Text(
            text = "Подберите скорость обучения и число эпох, чтобы линия легла на облако точек «площадь → цена». Пунктиром — эталонная (аналитическая) линия для сравнения.",
            fontSize = 14.sp,
            color = textColor.copy(alpha = 0.75f),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
        ) {
            LrCanvas(w1 = result.w1, w0 = result.w0, diverged = result.diverged, refW1 = closedW1, refW0 = closedW0)
        }

        Spacer(Modifier.height(10.dp))

        if (result.diverged) {
            Text(
                text = "Модель разошлась — скорость обучения слишком велика. Уменьшите её.",
                color = Color(0xFFFF6B6B),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Параметры обучения", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))

                Text("Скорость обучения (learning rate) = ${"%.3f".format(learningRate)}", color = textColor, fontSize = 14.sp)
                Slider(
                    value = learningRate,
                    onValueChange = { learningRate = it },
                    valueRange = 0.01f..1.1f,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
                )

                Spacer(Modifier.height(8.dp))
                Text("Число эпох = $epochs", color = textColor, fontSize = 14.sp)
                Slider(
                    value = epochs.toFloat(),
                    onValueChange = { epochs = it.roundToInt() },
                    valueRange = 1f..150f,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
                )

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = textColor.copy(alpha = 0.15f))
                Spacer(Modifier.height(12.dp))

                if (!result.diverged) {
                    Text(
                        "MSE на контрольной выборке: ${"%.1f".format(testMse)}",
                        color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "R² на контрольной выборке: ${"%.3f".format(testR2)}",
                        color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    "Аналитическое решение (для сравнения): цена ≈ ${"%.2f".format(closedW1)}·площадь + ${"%.1f".format(closedW0)}",
                    color = textColor.copy(alpha = 0.7f), fontSize = 12.5.sp
                )

                Spacer(Modifier.height(8.dp))
                Text(
                    text = lrInsight(learningRate, epochs, result.diverged, result.w1, closedW1),
                    color = textColor.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

/** Живое текстовое пояснение, меняющееся вместе со скоростью обучения и числом эпох. */
private fun lrInsight(lr: Float, epochs: Int, diverged: Boolean, currentW1: Float, refW1: Float): String {
    if (diverged) {
        return "Шаг оказался слишком большим: вместо приближения к минимуму ошибки веса раскачиваются и улетают всё дальше. Уменьшите скорость обучения хотя бы вдвое."
    }
    val gap = kotlin.math.abs(currentW1 - refW1)
    val epochsText = when {
        lr < 0.03f && epochs < 30 -> "При такой маленькой скорости обучения и небольшом числе эпох модель ещё явно не успела «доехать» до оптимума — линия заметно недообучена."
        epochs < 8 -> "Слишком мало эпох — даже при неплохой скорости обучения модель не успела толком обучиться."
        else -> "При таких параметрах модель, вероятно, уже близка к оптимуму или почти сошлась к нему."
    }
    val gapText = if (gap < 0.05f) {
        "Наклон линии сейчас почти совпадает с эталонным решением — хороший результат."
    } else {
        "Наклон линии всё ещё заметно отличается от эталонного (${"%.2f".format(refW1)}) — есть куда сходиться."
    }
    return "$epochsText $gapText"
}

@Composable
private fun LrCanvas(w1: Float, w0: Float, diverged: Boolean, refW1: Float, refW0: Float) {
    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val w = size.width
        val h = size.height

        val minArea = LrLab.AREA_MIN
        val maxArea = LrLab.AREA_MAX
        val prices = LrLab.trainSet.map { it.price }
        val minPrice = (prices.min() - 20f)
        val maxPrice = (prices.max() + 20f)

        fun toPx(area: Float, price: Float): Offset {
            val x = ((area - minArea) / (maxArea - minArea)) * w
            val y = h - ((price - minPrice) / (maxPrice - minPrice)) * h
            return Offset(x, y)
        }

        LrLab.trainSet.forEach { p ->
            val pt = toPx(p.area, p.price)
            drawCircle(color = Color(0xFFFF6B6B), radius = 5f, center = pt)
            drawCircle(color = Color.White.copy(alpha = 0.5f), radius = 5f, center = pt, style = Stroke(width = 1f))
        }

        // Эталонная (аналитическая) линия — полупрозрачный пунктир для сравнения
        val refStart = toPx(minArea, refW1 * minArea + refW0)
        val refEnd = toPx(maxArea, refW1 * maxArea + refW0)
        drawLine(
            color = Color.White.copy(alpha = 0.35f),
            start = refStart, end = refEnd, strokeWidth = 2f,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
        )

        if (!diverged) {
            val start = toPx(minArea, w1 * minArea + w0)
            val end = toPx(maxArea, w1 * maxArea + w0)
            drawLine(color = Color.White, start = start, end = end, strokeWidth = 3f)
        }
    }
}
