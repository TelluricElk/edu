package com.eduappml.ui.nb

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eduappml.ui.common.LessonScaffold

@Composable
fun NbInteractive(
    modifier: Modifier = Modifier,
    title: String?,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val textColor = Color.White
    val accent = Color(0xFF4D96FF)

    val stats = remember { NbLab.fitStats(NbLab.trainSet) }
    var queryPoint by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var probs by remember { mutableStateOf<Map<Boolean, Float>?>(null) }

    LessonScaffold(
        eyebrow = "Интерактив",
        title = title ?: "Наивный Байес",
        onBack = onBack,
        onNext = onNext,
        nextLabel = "К решению →",
        accent = accent,
        modifier = modifier
    ) {
        Text(
            "Коснитесь графика, чтобы поставить новый день (температура, влажность) и увидеть вероятность каждого класса.",
            fontSize = 14.sp, color = textColor.copy(alpha = 0.75f), modifier = Modifier.padding(bottom = 16.dp)
        )

        var canvasSize by remember { mutableStateOf(IntSize.Zero) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        if (canvasSize.width == 0) return@detectTapGestures
                        val temp = NbLab.TEMP_MIN + (offset.x / canvasSize.width) * (NbLab.TEMP_MAX - NbLab.TEMP_MIN)
                        val humidity = NbLab.HUMIDITY_MIN + (1f - offset.y / canvasSize.height) * (NbLab.HUMIDITY_MAX - NbLab.HUMIDITY_MIN)
                        queryPoint = temp to humidity
                        probs = NbLab.classProbabilities(WeatherPoint(temp, humidity, false), stats)
                    }
                }
        ) {
            NbCanvas(stats = stats, query = queryPoint)
        }

        Spacer(Modifier.height(10.dp))

        probs?.let { p ->
            val beachProb = (p[true] ?: 0f) * 100
            Text(
                "Пляж: ${"%.0f".format(beachProb)}%   Дом: ${"%.0f".format(100 - beachProb)}%",
                color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
        }

        Text(
            "Так наивный Байес «видит» каждый признак по отдельности — две колоколообразные кривые, по одной на класс. Вертикальная линия — значение вашей последней точки.",
            fontSize = 12.5.sp, color = textColor.copy(alpha = 0.65f), modifier = Modifier.padding(bottom = 8.dp)
        )
        GaussianDistributionRow(
            label = "Распределение по температуре",
            xMin = NbLab.TEMP_MIN, xMax = NbLab.TEMP_MAX,
            meanTrue = stats.getValue(true).meanTemp, varTrue = stats.getValue(true).varTemp,
            meanFalse = stats.getValue(false).meanTemp, varFalse = stats.getValue(false).varTemp,
            currentX = queryPoint?.first,
            textColor = textColor
        )
        Spacer(Modifier.height(10.dp))
        GaussianDistributionRow(
            label = "Распределение по влажности",
            xMin = NbLab.HUMIDITY_MIN, xMax = NbLab.HUMIDITY_MAX,
            meanTrue = stats.getValue(true).meanHumidity, varTrue = stats.getValue(true).varHumidity,
            meanFalse = stats.getValue(false).meanHumidity, varFalse = stats.getValue(false).varHumidity,
            currentX = queryPoint?.second,
            textColor = textColor
        )
        Row(modifier = Modifier.padding(top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendDot(Color(0xFFFFD93D)); Text(" пляж   ", color = textColor.copy(alpha = 0.7f), fontSize = 11.sp)
            LegendDot(Color(0xFF4D96FF)); Text(" дом", color = textColor.copy(alpha = 0.7f), fontSize = 11.sp)
        }

        Spacer(Modifier.height(6.dp))

        val accuracy = remember(stats) { NbLab.accuracy(stats, NbLab.testSet) }
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Точность на контрольной выборке: ${(accuracy * 100).toInt()}%",
                    color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Параметры (среднее и дисперсия по каждому классу) вычисляются заранее из обучающей выборки — " +
                        "в наивном Байесе нет ползунков вроде скорости обучения, ведь это не итеративный алгоритм.",
                    color = textColor.copy(alpha = 0.7f), fontSize = 12.5.sp
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(
        modifier = Modifier
            .size(9.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(color)
    )
}

/**
 * Две колоколообразные кривые нормального распределения (по одной на класс)
 * для одного признака — именно так наивный Байес "смотрит" на данные:
 * не как на облако точек, а как на распределение значений внутри каждого класса.
 */
@Composable
private fun GaussianDistributionRow(
    label: String,
    xMin: Float, xMax: Float,
    meanTrue: Float, varTrue: Float,
    meanFalse: Float, varFalse: Float,
    currentX: Float?,
    textColor: Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, color = textColor.copy(alpha = 0.8f), fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.05f))
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                val w = size.width
                val h = size.height

                fun density(x: Float, mean: Float, variance: Float): Float {
                    val safeVar = variance + 1e-3f
                    val exponent = -((x - mean) * (x - mean)) / (2f * safeVar)
                    return (1.0 / kotlin.math.sqrt(2.0 * Math.PI * safeVar) * kotlin.math.exp(exponent.toDouble())).toFloat()
                }

                val steps = 50
                val maxDensity = (0..steps).maxOf { i ->
                    val x = xMin + (xMax - xMin) * i / steps
                    maxOf(density(x, meanTrue, varTrue), density(x, meanFalse, varFalse))
                }.coerceAtLeast(1e-6f)

                fun curvePoints(mean: Float, variance: Float): List<Offset> = (0..steps).map { i ->
                    val x = xMin + (xMax - xMin) * i / steps
                    val d = (density(x, mean, variance) / maxDensity).coerceIn(0f, 1f)
                    Offset((i.toFloat() / steps) * w, h - d * h * 0.92f)
                }

                fun drawCurve(points: List<Offset>, color: Color) {
                    for (i in 0 until points.size - 1) {
                        drawLine(color, points[i], points[i + 1], strokeWidth = 2.5f)
                    }
                }

                drawCurve(curvePoints(meanFalse, varFalse), Color(0xFF4D96FF))
                drawCurve(curvePoints(meanTrue, varTrue), Color(0xFFFFD93D))

                currentX?.let { cx ->
                    val px = ((cx - xMin) / (xMax - xMin)).coerceIn(0f, 1f) * w
                    drawLine(Color.White.copy(alpha = 0.6f), Offset(px, 0f), Offset(px, h), strokeWidth = 1.5f)
                }
            }
        }
    }
}

@Composable
private fun NbCanvas(stats: Map<Boolean, ClassStats>, query: Pair<Float, Float>?) {
    val gridSteps = 24
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cellW = w / gridSteps
        val cellH = h / gridSteps

        fun toPx(temp: Float, hum: Float) = Offset(
            ((temp - NbLab.TEMP_MIN) / (NbLab.TEMP_MAX - NbLab.TEMP_MIN)) * w,
            h - ((hum - NbLab.HUMIDITY_MIN) / (NbLab.HUMIDITY_MAX - NbLab.HUMIDITY_MIN)) * h
        )

        for (gx in 0 until gridSteps) {
            for (gy in 0 until gridSteps) {
                val temp = NbLab.TEMP_MIN + (gx + 0.5f) / gridSteps * (NbLab.TEMP_MAX - NbLab.TEMP_MIN)
                val hum = NbLab.HUMIDITY_MIN + (1f - (gy + 0.5f) / gridSteps) * (NbLab.HUMIDITY_MAX - NbLab.HUMIDITY_MIN)
                val beach = NbLab.classify(WeatherPoint(temp, hum, false), stats)
                val color = if (beach) Color(0xFFFFD93D) else Color(0xFF4D96FF)
                drawRect(color.copy(alpha = 0.16f), topLeft = Offset(gx * cellW, gy * cellH), size = androidx.compose.ui.geometry.Size(cellW + 1f, cellH + 1f))
            }
        }

        NbLab.trainSet.forEach { p ->
            val pt = toPx(p.temperature, p.humidity)
            val color = if (p.beach) Color(0xFFFFD93D) else Color(0xFF4D96FF)
            drawCircle(color, radius = 5f, center = pt)
        }

        query?.let { (t, hmd) ->
            val pt = toPx(t, hmd)
            drawCircle(Color.White, radius = 9f, center = pt, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
            drawCircle(Color.Black.copy(alpha = 0.4f), radius = 9f, center = pt)
        }
    }
}
