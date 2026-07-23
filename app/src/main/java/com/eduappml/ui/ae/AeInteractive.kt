package com.eduappml.ui.ae

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eduappml.ui.common.LessonScaffold
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun AeInteractive(
    modifier: Modifier = Modifier,
    title: String?,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val textColor = Color.White
    val accent = Color(0xFFFF914D)

    var bottleneck by remember { mutableIntStateOf(1) }
    var learningRate by remember { mutableFloatStateOf(0.15f) }
    var epochs by remember { mutableIntStateOf(120) }

    var ae by remember { mutableStateOf(AeLab.train(bottleneck, learningRate, epochs)) }
    LaunchedEffect(bottleneck, learningRate, epochs) {
        delay(120)
        ae = AeLab.train(bottleneck, learningRate, epochs)
    }

    val testMse = remember(ae) { AeLab.mse(ae, AeLab.testSet) }

    LessonScaffold(
        eyebrow = "Интерактив",
        title = title ?: "Автокодировщик",
        onBack = onBack,
        onNext = onNext,
        nextLabel = "К решению →",
        accent = accent,
        modifier = modifier
    ) {
        Text(
            "Оранжевые точки — исходная дуга, белые — то, что восстановил декодер из сжатого кода.",
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
            AeCanvas(ae = ae)
        }

        Spacer(Modifier.height(14.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Параметры обучения", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))

                Text("Размер узкого горлышка", color = textColor, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.08f))) {
                    listOf(1, 2).forEach { size ->
                        val selected = size == bottleneck
                        Box(
                            modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) Color.White.copy(alpha = 0.22f) else Color.Transparent)
                                .padding(vertical = 10.dp)
                                .pointerInput(size) { detectTapGestures { bottleneck = size } },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$size число${if (size == 1) "" else "а"}", color = Color.White, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text("Скорость обучения = ${"%.2f".format(learningRate)}", color = textColor, fontSize = 14.sp)
                Slider(value = learningRate, onValueChange = { learningRate = it }, valueRange = 0.02f..0.5f,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White))

                Spacer(Modifier.height(8.dp))
                Text("Число эпох = $epochs", color = textColor, fontSize = 14.sp)
                Slider(value = epochs.toFloat(), onValueChange = { epochs = it.roundToInt() }, valueRange = 1f..300f,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White))

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = textColor.copy(alpha = 0.15f))
                Spacer(Modifier.height(12.dp))

                Text("Ошибка реконструкции (MSE) на контрольной выборке: ${"%.4f".format(testMse)}", color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)

                Spacer(Modifier.height(8.dp))
                Text(
                    text = aeInsight(bottleneck, testMse),
                    color = textColor.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

private fun aeInsight(bottleneck: Int, testMse: Float): String {
    val sizeText = if (bottleneck == 1) {
        "Горлышко размера 1 — сеть обязана уместить положение точки на дуге в одно-единственное число."
    } else {
        "Горлышко размера 2 совпадает с реальной размерностью входа — сети не нужно ничего по-настоящему сжимать."
    }
    val qualityText = when {
        testMse < 0.02f -> "Ошибка реконструкции очень мала — восстановленные точки почти неотличимы от исходных."
        testMse < 0.15f -> "Ошибка умеренная — форма дуги в целом сохранена, но есть заметное сглаживание."
        else -> "Ошибка ещё велика — сеть пока недообучена, попробуйте увеличить число эпох."
    }
    return "$sizeText $qualityText"
}

@Composable
private fun AeCanvas(ae: AeLab.Autoencoder) {
    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val w = size.width
        val h = size.height

        fun toPx(x: Float, y: Float) = Offset((x / AeLab.FEATURE_MAX) * w, h - (y / AeLab.FEATURE_MAX) * h)

        AeLab.trainSet.forEach { p ->
            val recon = ae.reconstruct(p)
            val orig = toPx(p.x, p.y)
            val rec = toPx(recon.x, recon.y)
            drawLine(Color.White.copy(alpha = 0.25f), orig, rec, strokeWidth = 1f)
            drawCircle(Color(0xFFFF914D), radius = 5f, center = orig)
            drawCircle(Color.White, radius = 3.5f, center = rec)
        }
    }
}
