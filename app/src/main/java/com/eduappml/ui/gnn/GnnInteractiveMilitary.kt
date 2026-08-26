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
import com.eduappml.ui.common.AskChatButton
import com.eduappml.ui.common.LessonScaffold
import com.eduappml.ui.common.buildInteractiveChatPrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import com.eduappml.ui.common.designPx

/**
 * Военный вариант экрана "Интерактив" для темы GNN: та же логика, что у
 * [GnnInteractive] (который остаётся нетронутым и больше не вызывается
 * для id = "gnn"), только со сценой сети радиосвязи вместо социальной сети.
 */
@Composable
fun GnnInteractiveMilitary(
    modifier: Modifier = Modifier,
    title: String?,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onOpenChat: (String) -> Unit = {}
) {
    val textColor = Color.White
    val accent = Color(0xFFB5179E)
    val topicTitle = title ?: "Графовая сеть"

    var numLayers by remember { mutableIntStateOf(1) }
    var learningRate by remember { mutableFloatStateOf(0.03f) }
    var epochs by remember { mutableIntStateOf(250) }

    var model by remember { mutableStateOf(GnnLabMilitary.train(numLayers, 4, learningRate, epochs)) }
    LaunchedEffect(numLayers, learningRate, epochs) {
        delay(120)
        model = withContext(Dispatchers.Default) { GnnLabMilitary.train(numLayers, 4, learningRate, epochs) }
    }

    val probs = remember(model) { GnnLabMilitary.predict(model) }
    val accuracy = remember(probs) { GnnLabMilitary.accuracy(model) }

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
            "Цвет узла — предсказанная группа, белое кольцо — ошибка (предсказание не совпало с настоящей группой).",
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
            GraphCanvasMilitary(probs = probs)
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

                Text("Точность классификации групп: ${(accuracy * 100).roundToInt()}%", color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)

                Spacer(Modifier.height(8.dp))
                Text(text = gnnInsightMilitary(numLayers, accuracy), color = textColor.copy(alpha = 0.75f), fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(10.dp))
                AskChatButton(accent = accent, onClick = {
                    onOpenChat(
                        buildInteractiveChatPrompt(
                            topicTitle,
                            "число раундов обмена сообщениями = $numLayers, скорость обучения = ${"%.3f".format(learningRate)}, эпох = $epochs",
                            "точность классификации групп ${(accuracy * 100).roundToInt()}%"
                        )
                    )
                })
            }
        }
    }
}

private fun gnnInsightMilitary(numLayers: Int, accuracy: Float): String {
    val layersText = when (numLayers) {
        1 -> "Один раунд — каждый узел видит только непосредственных соседей. Узлам с малым числом связей внутри своей группы этого может не хватить."
        2 -> "Два раунда — теперь узел неявно «видит» и соседей своих соседей. Для графа такого размера это часто оптимальная глубина."
        else -> "При таком числе раундов агрегация захватывает уже очень широкую окрестность — начинает проявляться пересглаживание: представления разных узлов становятся всё более похожими друг на друга."
    }
    val accText = when {
        accuracy >= 0.95f -> "Почти идеальный результат — сеть уверенно восстановила структуру групп по одной только связности."
        accuracy >= 0.75f -> "Неплохой результат, но есть куда расти."
        else -> "Результат близок к случайному — на этом графе с таким числом раундов сети трудно уловить структуру групп."
    }
    return "$layersText $accText"
}

@Composable
private fun GraphCanvasMilitary(probs: FloatArray) {
    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val w = size.width
        val h = size.height
        val half = NODE_COUNT_MIL / 2

        fun nodePos(v: Int): Offset {
            val groupIndex = if (v < half) v else v - half
            val centerX = if (v < half) w * 0.28f else w * 0.72f
            val centerY = h * 0.5f
            val angle = (2 * Math.PI / half) * groupIndex
            val r = minOf(w, h) * 0.22f
            return Offset(
                (centerX + r * cos(angle)).toFloat(),
                (centerY + r * sin(angle)).toFloat()
            )
        }

        val positions = (0 until NODE_COUNT_MIL).map { nodePos(it) }

        GnnLabMilitary.adjacency.forEachIndexed { v, neighbors ->
            neighbors.forEach { u ->
                if (u > v) {
                    drawLine(Color.White.copy(alpha = 0.18f), positions[v], positions[u], strokeWidth = designPx(1.3f))
                }
            }
        }

        positions.forEachIndexed { v, pos ->
            val predicted = probs[v] >= 0.5f
            val actual = GnnLabMilitary.community[v] == 1
            val color = if (predicted) Color(0xFFB5179E) else Color(0xFF4D96FF)
            drawCircle(color, radius = designPx(9f), center = pos)
            if (predicted != actual) {
                drawCircle(Color.White, radius = designPx(12f), center = pos, style = Stroke(width = designPx(2f)))
            }
        }
    }
}
