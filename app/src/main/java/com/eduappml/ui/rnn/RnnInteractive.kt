package com.eduappml.ui.rnn

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eduappml.ui.common.LessonScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun RnnInteractive(
    modifier: Modifier = Modifier,
    title: String?,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val textColor = Color.White
    val accent = Color(0xFF6BCB77)

    var length by remember { mutableIntStateOf(3) }
    var learningRate by remember { mutableFloatStateOf(0.15f) }
    var epochs by remember { mutableIntStateOf(200) }

    var net by remember { mutableStateOf(RnnLab.train(length, learningRate, epochs)) }
    LaunchedEffect(length, learningRate, epochs) {
        delay(120)
        net = withContext(Dispatchers.Default) { RnnLab.train(length, learningRate, epochs) }
    }

    var signals by remember(length) { mutableStateOf(List(length) { 0f }) }
    LaunchedEffect(length) { signals = List(length) { 0f } }

    val testAcc = remember(net, length) { RnnLab.accuracy(net, RnnLab.testSet(length)) }
    val (history, prediction) = remember(net, signals) { net.forward(signals) }
    val isOn = prediction >= 0.5f

    LessonScaffold(
        eyebrow = "Интерактив",
        title = title ?: "Рекуррентная сеть",
        onBack = onBack,
        onNext = onNext,
        nextLabel = "К решению →",
        accent = accent,
        modifier = modifier
    ) {
        Text(
            "Тапайте по клеткам, чтобы задать свою последовательность сигналов. Свет стартует выключенным, каждая единица переключает его.",
            fontSize = 14.sp, color = textColor.copy(alpha = 0.75f), modifier = Modifier.padding(bottom = 14.dp)
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            signals.forEachIndexed { idx, v ->
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (v > 0.5f) accent.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .clickable {
                            signals = signals.mapIndexed { i, x -> if (i == idx) (if (x > 0.5f) 0f else 1f) else x }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (v > 0.5f) "1" else "0", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isOn) Color(0xFFFFD93D).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.1f))
                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("💡", fontSize = 20.sp)
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            "Сеть предсказывает: свет ${if (isOn) "включён" else "выключен"} (уверенность ${(if (isOn) prediction else 1f - prediction).let { (it * 100).roundToInt() }}%)",
            color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(14.dp))
        Text("Скрытое состояние по шагам", color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                .padding(6.dp)
        ) {
            HiddenStateCanvas(history = history)
        }
        Text(
            "Каждый столбец — момент времени, каждая строка — одно из 4 чисел скрытого состояния. Яркость = величина значения.",
            fontSize = 11.5.sp, color = textColor.copy(alpha = 0.6f), modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(Modifier.height(14.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Параметры обучения", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))

                Text("Длина последовательности = $length", color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Slider(value = length.toFloat(), onValueChange = { length = it.roundToInt() }, valueRange = 2f..9f, steps = 6,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent))

                Spacer(Modifier.height(8.dp))
                Text("Скорость обучения = ${"%.2f".format(learningRate)}", color = textColor, fontSize = 14.sp)
                Slider(value = learningRate, onValueChange = { learningRate = it }, valueRange = 0.05f..0.3f,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White))

                Spacer(Modifier.height(8.dp))
                Text("Число эпох = $epochs", color = textColor, fontSize = 14.sp)
                Slider(value = epochs.toFloat(), onValueChange = { epochs = it.roundToInt() }, valueRange = 10f..400f,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White))

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = textColor.copy(alpha = 0.15f))
                Spacer(Modifier.height(12.dp))

                Text("Точность на контрольной выборке: ${(testAcc * 100).roundToInt()}%", color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)

                Spacer(Modifier.height(8.dp))
                Text(text = rnnInsight(length, testAcc), color = textColor.copy(alpha = 0.75f), fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}

private fun rnnInsight(length: Int, testAcc: Float): String {
    val lengthText = when {
        length <= 3 -> "На такой короткой последовательности простая RNN обычно справляется уверенно."
        length <= 5 -> "Длина уже заметная — обучение может стать менее стабильным, даже с большим числом эпох."
        else -> "На такой длине классическая проблема затухающего градиента обычно даёт о себе знать в полную силу."
    }
    val accText = when {
        testAcc >= 0.9f -> "Высокая точность — сеть уверенно удерживает нужную информацию через всю последовательность."
        testAcc >= 0.65f -> "Средний результат — часть информации о ранних сигналах, похоже, теряется по пути."
        else -> "Точность близка к случайному угадыванию — характерный симптом затухающего градиента, а не признак того, что параметры подобраны неправильно."
    }
    return "$lengthText $accText"
}

@Composable
private fun HiddenStateCanvas(history: List<FloatArray>) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (history.isEmpty()) return@Canvas
        val cols = history.size
        val rows = history[0].size
        val cellW = size.width / cols
        val cellH = size.height / rows
        for (c in 0 until cols) {
            for (r in 0 until rows) {
                val v = history[c][r] // -1..1
                val intensity = ((v + 1f) / 2f).coerceIn(0f, 1f)
                drawRect(
                    Color(0xFF6BCB77).copy(alpha = 0.15f + intensity * 0.75f),
                    topLeft = Offset(c * cellW, r * cellH),
                    size = androidx.compose.ui.geometry.Size(cellW - 1f, cellH - 1f)
                )
            }
        }
    }
}
