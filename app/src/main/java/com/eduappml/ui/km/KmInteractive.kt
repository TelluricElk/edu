package com.eduappml.ui.km

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

private val clusterColors = listOf(
    Color(0xFFE63946), Color(0xFF4D96FF), Color(0xFFFFD93D), Color(0xFF6BCB77),
    Color(0xFFB5179E), Color(0xFFFF914D), Color(0xFF9D4EDD), Color(0xFF00C2A8)
)

@Composable
fun KmInteractive(
    modifier: Modifier = Modifier,
    title: String?,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val textColor = Color.White
    val accent = Color(0xFFE63946)

    var k by remember { mutableIntStateOf(4) }
    var iteration by remember { mutableIntStateOf(6) }
    var seed by remember { mutableIntStateOf(1) }

    var state by remember { mutableStateOf(KmLab.run(k, iteration, seed)) }
    LaunchedEffect(k, iteration, seed) {
        delay(100)
        state = KmLab.run(k, iteration, seed)
    }

    LessonScaffold(
        eyebrow = "Интерактив",
        title = title ?: "K-средних",
        onBack = onBack,
        onNext = onNext,
        nextLabel = "К решению →",
        accent = accent,
        modifier = modifier
    ) {
        Text(
            "Подвигайте номер итерации, чтобы увидеть, как центроиды сходятся шаг за шагом. Кнопка «Переинициализировать» запускает алгоритм заново со случайных центроидов.",
            fontSize = 14.sp, color = textColor.copy(alpha = 0.75f), modifier = Modifier.padding(bottom = 16.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
        ) {
            KmCanvas(state = state)
        }

        Spacer(Modifier.height(10.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Параметры", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))

                Text("Число кластеров k = $k", color = textColor, fontSize = 14.sp)
                Slider(value = k.toFloat(), onValueChange = { k = it.roundToInt() }, valueRange = 1f..8f, steps = 6,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White))

                Spacer(Modifier.height(8.dp))
                Text("Итерация № $iteration", color = textColor, fontSize = 14.sp)
                Slider(value = iteration.toFloat(), onValueChange = { iteration = it.roundToInt() }, valueRange = 0f..15f, steps = 14,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent))

                Spacer(Modifier.height(10.dp))
                com.eduappml.ui.common.BottomPillButton(text = "Переинициализировать центроиды", onClick = { seed = (seed + 1) % 997 })

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = textColor.copy(alpha = 0.15f))
                Spacer(Modifier.height(12.dp))

                Text("Инерция (J): ${"%.0f".format(state.inertia)}", color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)

                Spacer(Modifier.height(8.dp))
                Text(
                    text = kmInsight(k, iteration),
                    color = textColor.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text("Метод локтя: инерция в зависимости от k", color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
        ) {
            ElbowCanvas(currentK = k)
        }
    }
}

/** Живое текстовое пояснение, меняющееся вместе с k и номером итерации. */
private fun kmInsight(k: Int, iteration: Int): String {
    val kText = when {
        k == 1 -> "При k = 1 все точки — один кластер, инерция максимальна: это просто среднее всех данных."
        k >= 6 -> "При таком большом k кластеры мельчают — алгоритм начинает делить даже то, что интуитивно выглядело одной группой."
        else -> "Такое k близко к четырём естественным группам, заложенным в этот датасет покупателей."
    }
    val iterText = when {
        iteration == 0 -> "Итерация 0 — центроиды ещё в исходном случайном положении, кластеры выглядят хаотично."
        iteration <= 2 -> "На таких ранних итерациях центроиды только начали двигаться к своим группам."
        else -> "К этой итерации центроиды обычно уже почти перестали двигаться — алгоритм близок к сходимости."
    }
    return "$kText $iterText"
}

@Composable
private fun KmCanvas(state: KmLab.KMeansState) {
    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val w = size.width
        val h = size.height

        fun toPx(spending: Float, frequency: Float) = Offset(
            ((spending - KmLab.SPENDING_MIN) / (KmLab.SPENDING_MAX - KmLab.SPENDING_MIN)) * w,
            h - ((frequency - KmLab.FREQUENCY_MIN) / (KmLab.FREQUENCY_MAX - KmLab.FREQUENCY_MIN)) * h
        )

        KmLab.points.forEachIndexed { idx, p ->
            val color = clusterColors[state.assignments.getOrElse(idx) { 0 } % clusterColors.size]
            drawCircle(color, radius = 5f, center = toPx(p.spending, p.frequency))
        }

        state.centroids.forEachIndexed { idx, c ->
            val pt = toPx(c.spending, c.frequency)
            val color = clusterColors[idx % clusterColors.size]
            drawCircle(Color.Black.copy(alpha = 0.5f), radius = 11f, center = pt)
            drawCircle(color, radius = 9f, center = pt, style = Stroke(width = 3f))
        }
    }
}

@Composable
private fun ElbowCanvas(currentK: Int) {
    val series = remember { KmLab.elbowSeries(8) }
    Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        val w = size.width
        val h = size.height
        val maxVal = series.max().coerceAtLeast(1f)

        fun toPx(idx: Int, value: Float) = Offset(
            (idx.toFloat() / (series.size - 1)) * w,
            h - (value / maxVal) * h
        )

        var prev: Offset? = null
        series.forEachIndexed { idx, v ->
            val pt = toPx(idx, v)
            prev?.let { drawLine(Color.White.copy(alpha = 0.6f), it, pt, strokeWidth = 2f) }
            prev = pt
            val isCurrent = (idx + 1) == currentK
            drawCircle(if (isCurrent) Color(0xFFE63946) else Color.White.copy(alpha = 0.5f), radius = if (isCurrent) 6f else 3.5f, center = pt)
        }
    }
}
