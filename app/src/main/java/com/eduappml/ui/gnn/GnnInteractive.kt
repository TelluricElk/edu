package com.eduappml.ui.gnn

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun GnnInteractive(
    modifier: Modifier = Modifier,
    title: String?,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val textColor = Color.White
    val accent = Color(0xFFB5179E)

    var numLayers by remember { mutableIntStateOf(1) }
    var learningRate by remember { mutableFloatStateOf(0.03f) }
    var epochs by remember { mutableIntStateOf(250) }

    var model by remember { mutableStateOf(GnnLab.train(numLayers, 4, learningRate, epochs)) }
    LaunchedEffect(numLayers, learningRate, epochs) {
        delay(120)
        model = withContext(Dispatchers.Default) { GnnLab.train(numLayers, 4, learningRate, epochs) }
    }

    val probs = remember(model) { GnnLab.predict(model) }
    val accuracy = remember(probs) { GnnLab.accuracy(model) }

    LessonScaffold(
        eyebrow = "Интерактив",
        title = title ?: "Графовая сеть",
        onBack = onBack,
        onNext = onNext,
        nextLabel = "К решению →",
        accent = accent,
        modifier = modifier
    ) {
        Text(
            "Цвет узла — предсказанное сообщество, белое кольцо — ошибка (предсказание не совпало с настоящим сообществом).",
            fontSize = 14.sp, color = textColor.copy(alpha = 0.75f), modifier = Modifier.padding(bottom = 14.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
        ) {
            GraphCanvas(probs = probs)
        }

        Spacer(Modifier.height(14.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Параметры обучения", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))

                Text("Число раундов обмена сообщениями = $numLayers", color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Slider(value = numLayers.toFloat(), onValueChange = { numLayers = it.roundToInt() }, valueRange = 1f..4f, steps = 2,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent))

                Spacer(Modifier.height(8.dp))
                Text("Скорость обучения = ${"%.3f".format(learningRate)}", color = textColor, fontSize = 14.sp)
                Slider(value = learningRate, onValueChange = { learningRate = it }, valueRange = 0.005f..0.15f,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White))

                Spacer(Modifier.height(8.dp))
                Text("Число эпох = $epochs", color = textColor, fontSize = 14.sp)
                Slider(value = epochs.toFloat(), onValueChange = { epochs = it.roundToInt() }, valueRange = 20f..400f,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White))

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = textColor.copy(alpha = 0.15f))
                Spacer(Modifier.height(12.dp))

                Text("Точность классификации сообществ: ${(accuracy * 100).roundToInt()}%", color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)

                Spacer(Modifier.height(8.dp))
                Text(text = gnnInsight(numLayers, accuracy), color = textColor.copy(alpha = 0.75f), fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}

private fun gnnInsight(numLayers: Int, accuracy: Float): String {
    val layersText = when (numLayers) {
        1 -> "Один раунд — каждый узел видит только непосредственных соседей. Узлам с малым числом связей внутри своего сообщества этого может не хватить."
        2 -> "Два раунда — теперь узел неявно «видит» и соседей своих соседей. Для графа такого размера это часто оптимальная глубина."
        else -> "При таком числе раундов агрегация захватывает уже очень широкую окрестность — начинает проявляться пересглаживание: представления разных узлов становятся всё более похожими друг на друга."
    }
    val accText = when {
        accuracy >= 0.95f -> "Почти идеальный результат — сеть уверенно восстановила структуру сообществ по одной только связности."
        accuracy >= 0.75f -> "Неплохой результат, но есть куда расти."
        else -> "Результат близок к случайному — на этом графе с таким числом раундов сети трудно уловить структуру сообществ."
    }
    return "$layersText $accText"
}

@Composable
private fun GraphCanvas(probs: FloatArray) {
    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val w = size.width
        val h = size.height
        val half = NODE_COUNT / 2

        fun nodePos(v: Int): Offset {
            val communityIndex = if (v < half) v else v - half
            val centerX = if (v < half) w * 0.28f else w * 0.72f
            val centerY = h * 0.5f
            val angle = (2 * Math.PI / half) * communityIndex
            val r = minOf(w, h) * 0.22f
            return Offset(
                (centerX + r * cos(angle)).toFloat(),
                (centerY + r * sin(angle)).toFloat()
            )
        }

        val positions = (0 until NODE_COUNT).map { nodePos(it) }

        GnnLab.adjacency.forEachIndexed { v, neighbors ->
            neighbors.forEach { u ->
                if (u > v) {
                    drawLine(Color.White.copy(alpha = 0.18f), positions[v], positions[u], strokeWidth = 1.3f)
                }
            }
        }

        positions.forEachIndexed { v, pos ->
            val predicted = probs[v] >= 0.5f
            val actual = GnnLab.community[v] == 1
            val color = if (predicted) Color(0xFFB5179E) else Color(0xFF4D96FF)
            drawCircle(color, radius = 9f, center = pos)
            if (predicted != actual) {
                drawCircle(Color.White, radius = 12f, center = pos, style = Stroke(width = 2f))
            }
        }
    }
}
