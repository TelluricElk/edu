package com.eduappml.ui.fc

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
import com.eduappml.ui.common.LessonScaffold
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun FcInteractive(
    modifier: Modifier = Modifier,
    title: String?,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val textColor = Color.White
    val accent = Color(0xFFFF6B6B)

    var hiddenSize by remember { mutableIntStateOf(1) }
    var learningRate by remember { mutableFloatStateOf(0.5f) }
    var epochs by remember { mutableIntStateOf(150) }

    var result by remember { mutableStateOf(FcLab.train(hiddenSize, learningRate, epochs)) }
    LaunchedEffect(hiddenSize, learningRate, epochs) {
        delay(120)
        result = FcLab.train(hiddenSize, learningRate, epochs)
    }

    val testAcc = remember(result) { FcLab.accuracy(result.network, FcLab.testSet) }

    LessonScaffold(
        eyebrow = "Интерактив",
        title = title ?: "Полносвязная сеть",
        onBack = onBack,
        onNext = onNext,
        nextLabel = "К решению →",
        accent = accent,
        modifier = modifier
    ) {
        Text(
            "Начните с 1 нейрона в скрытом слое и постепенно добавляйте — заметьте момент, когда граница решения перестаёт быть прямой.",
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
            FcBoundaryCanvas(network = result.network)
        }

        Spacer(Modifier.height(14.dp))
        Text("Архитектура сети", color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
        ) {
            FcArchitectureCanvas(network = result.network)
        }
        Text(
            "Толщина линий — сила обученного веса связи. Красным — входы, белым — скрытый слой, зелёным — выход.",
            fontSize = 11.5.sp, color = textColor.copy(alpha = 0.6f), modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(Modifier.height(14.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Параметры обучения", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))

                Text("Нейронов в скрытом слое = $hiddenSize", color = textColor, fontSize = 14.sp)
                Slider(value = hiddenSize.toFloat(), onValueChange = { hiddenSize = it.roundToInt() }, valueRange = 1f..8f, steps = 6,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White))

                Spacer(Modifier.height(8.dp))
                Text("Скорость обучения = ${"%.2f".format(learningRate)}", color = textColor, fontSize = 14.sp)
                Slider(value = learningRate, onValueChange = { learningRate = it }, valueRange = 0.05f..2f,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White))

                Spacer(Modifier.height(8.dp))
                Text("Число эпох = $epochs", color = textColor, fontSize = 14.sp)
                Slider(value = epochs.toFloat(), onValueChange = { epochs = it.roundToInt() }, valueRange = 5f..400f,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White))

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = textColor.copy(alpha = 0.15f))
                Spacer(Modifier.height(12.dp))

                Text("Точность на контрольной выборке: ${(testAcc * 100).roundToInt()}%", color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)

                Spacer(Modifier.height(8.dp))
                Text(
                    text = fcInsight(hiddenSize, testAcc),
                    color = textColor.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

/** Живое текстовое пояснение, меняющееся вместе с числом нейронов. */
private fun fcInsight(hiddenSize: Int, testAcc: Float): String {
    val capacityText = when {
        hiddenSize == 1 -> "Один нейрон в скрытом слое по сути ведёт себя как логистическая регрессия — граница решения ограничена прямой линией."
        hiddenSize <= 3 -> "Малое число нейронов иногда достаточно для XOR, а иногда обучение застревает в неудачной точке — попробуйте добавить ещё."
        else -> "Такого числа нейронов обычно достаточно, чтобы граница решения изогнулась и повторила XOR-узор."
    }
    val accText = if (testAcc >= 0.95f) {
        "Точность близка к 100% — сеть нашла границу, повторяющую форму данных."
    } else if (testAcc <= 0.8f) {
        "Точность около 75% — типичный результат, когда прямая (или почти прямая) граница может правильно отделить только три четверти узора XOR."
    } else {
        "Промежуточный результат — сеть частично уловила структуру данных."
    }
    return "$capacityText $accText"
}

@Composable
private fun FcBoundaryCanvas(network: FcLab.Network) {
    val gridSteps = 24
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cellW = w / gridSteps
        val cellH = h / gridSteps

        for (gx in 0 until gridSteps) {
            for (gy in 0 until gridSteps) {
                val x1 = ((gx + 0.5f) / gridSteps) * FcLab.FEATURE_MAX
                val x2 = (1f - (gy + 0.5f) / gridSteps) * FcLab.FEATURE_MAX
                val p = network.predict(x1, x2)
                val color = if (p >= 0.5f) Color(0xFF6BCB77) else Color(0xFFFF6B6B)
                drawRect(color.copy(alpha = 0.16f), topLeft = Offset(gx * cellW, gy * cellH), size = androidx.compose.ui.geometry.Size(cellW + 1f, cellH + 1f))
            }
        }

        FcLab.trainSet.forEach { p ->
            val pt = Offset((p.x1 / FcLab.FEATURE_MAX) * w, h - (p.x2 / FcLab.FEATURE_MAX) * h)
            val color = if (p.label >= 0.5f) Color(0xFF6BCB77) else Color(0xFFFF6B6B)
            drawCircle(color, radius = 5f, center = pt)
            drawCircle(Color.White.copy(alpha = 0.5f), radius = 5f, center = pt, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f))
        }
    }
}

@Composable
private fun FcArchitectureCanvas(network: FcLab.Network) {
    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val w = size.width
        val h = size.height
        val hidden = network.hiddenSize

        val inputX = w * 0.12f
        val hiddenX = w * 0.52f
        val outputX = w * 0.90f

        fun columnY(index: Int, count: Int): Float {
            if (count == 1) return h / 2f
            val step = h / (count + 1)
            return step * (index + 1)
        }

        val inputPositions = listOf(Offset(inputX, columnY(0, 2)), Offset(inputX, columnY(1, 2)))
        val hiddenPositions = (0 until hidden).map { Offset(hiddenX, columnY(it, hidden)) }
        val outputPosition = Offset(outputX, h / 2f)

        var maxW1 = 0.01f
        for (row in network.w1) {
            for (v in row) {
                val a = abs(v)
                if (a > maxW1) maxW1 = a
            }
        }
        var maxW2 = 0.01f
        for (v in network.w2) {
            val a = abs(v)
            if (a > maxW2) maxW2 = a
        }

        // Связи вход -> скрытый слой
        inputPositions.forEachIndexed { ii, ip ->
            hiddenPositions.forEachIndexed { hi, hp ->
                val weight = network.w1[hi][ii]
                val alphaVal = (abs(weight) / maxW1).coerceIn(0.08f, 1f)
                drawLine(Color.White.copy(alpha = alphaVal * 0.8f), ip, hp, strokeWidth = 1f + alphaVal * 3f)
            }
        }
        // Связи скрытый слой -> выход
        hiddenPositions.forEachIndexed { hi, hp ->
            val weight = network.w2[hi]
            val alphaVal = (abs(weight) / maxW2).coerceIn(0.08f, 1f)
            drawLine(Color.White.copy(alpha = alphaVal * 0.8f), hp, outputPosition, strokeWidth = 1f + alphaVal * 3f)
        }

        inputPositions.forEach { drawCircle(Color(0xFFFF6B6B), radius = 8f, center = it) }
        hiddenPositions.forEach { drawCircle(Color.White, radius = 8f, center = it) }
        drawCircle(Color(0xFF6BCB77), radius = 9f, center = outputPosition)
    }
}
