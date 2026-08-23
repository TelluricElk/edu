package com.eduappml.ui.dm

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.random.Random

private const val DISPLAY_RANGE_MIL = 8f // координаты данных от -8 до 8

/**
 * Военный вариант экрана "Интерактив" для темы диффузионной модели: та же
 * логика, что у [DmInteractive] (который остаётся нетронутым и больше не
 * вызывается для id = "dm"), только со сценой восстановления
 * аэрофотоснимка из шума помех.
 */
@Composable
fun DmInteractiveMilitary(
    modifier: Modifier = Modifier,
    title: String?,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onOpenChat: (String) -> Unit = {}
) {
    val textColor = Color.White
    val accent = Color(0xFF9D4EDD)
    val topicTitle = title ?: "Диффузионная модель"

    // --- Прямой процесс ---
    var noiseSigma by remember { mutableFloatStateOf(1f) }
    val originalPoint = remember { DmLabMilitary.realSample.first() }
    val fixedNoise = remember { val rnd = Random(42); Pair(rnd.nextFloat() * 2f - 1f, rnd.nextFloat() * 2f - 1f) }
    val noisyPoint = remember(noiseSigma) {
        Point2D(originalPoint.x + fixedNoise.first * noiseSigma * 1.6f, originalPoint.y + fixedNoise.second * noiseSigma * 1.6f)
    }

    // --- Обратный процесс ---
    var reverseSteps by remember { mutableIntStateOf(8) }
    var reverseSamples by remember { mutableStateOf(DmLabMilitary.reverseSample(150, reverseSteps)) }
    LaunchedEffect(reverseSteps) {
        delay(100)
        reverseSamples = withContext(Dispatchers.Default) { DmLabMilitary.reverseSample(150, reverseSteps) }
    }
    val convergence = remember(reverseSamples) { DmLabMilitary.convergenceRatio(reverseSamples) }

    LessonScaffold(
        eyebrow = "Интерактив",
        title = title ?: "Диффузионная модель",
        onBack = onBack,
        onNext = onNext,
        nextLabel = "К решению →",
        accent = accent,
        modifier = modifier
    ) {
        Text("Прямой процесс: точное зашумление", color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
        ) {
            ForwardCanvasMilitary(original = originalPoint, noisy = noisyPoint)
        }
        Spacer(Modifier.height(8.dp))
        Text("Уровень шума (σ) = ${"%.2f".format(noiseSigma)}", color = textColor, fontSize = 13.sp)
        Slider(value = noiseSigma, onValueChange = { noiseSigma = it }, valueRange = 0f..6f,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White))

        Spacer(Modifier.height(20.dp))
        Text("Обратный процесс: точный score, без обучения", color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
        ) {
            ReverseCanvasMilitary(samples = reverseSamples)
        }

        Spacer(Modifier.height(14.dp))
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Число шагов денойзинга = $reverseSteps", color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Slider(value = reverseSteps.toFloat(), onValueChange = { reverseSteps = it.roundToInt() }, valueRange = 1f..80f,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent))

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = textColor.copy(alpha = 0.15f))
                Spacer(Modifier.height(12.dp))

                Text("Доля точек, дошедших до одного из двух облаков: ${(convergence * 100).roundToInt()}%", color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(text = dmInsightMilitary(reverseSteps, convergence), color = textColor.copy(alpha = 0.75f), fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(10.dp))
                AskChatButton(accent = accent, onClick = {
                    onOpenChat(
                        buildInteractiveChatPrompt(
                            topicTitle,
                            "число шагов денойзинга = $reverseSteps, точная score-функция (без обучения)",
                            "доля точек, дошедших до одного из двух облаков ${(convergence * 100).roundToInt()}%"
                        )
                    )
                })
            }
        }
    }
}

private fun dmInsightMilitary(steps: Int, convergence: Float): String {
    val stepsText = when {
        steps < 5 -> "Мало шагов — путь от шума к данным разбит на слишком крупные скачки, точки не успевают дойти до цели."
        steps < 20 -> "Уже заметное улучшение — путь становится мельче, точнее следуя направлению score."
        else -> "Много шагов — траектория почти непрерывно следует за точным градиентом, результат близок к идеалу."
    }
    val convText = if (convergence > 0.9f) {
        "Точки почти идеально собрались в исходные два облака — при таком числе шагов дискретизация уже не заметна."
    } else if (convergence > 0.6f) {
        "Большинство точек нашли своё облако, но есть заметная доля промежуточных, «застрявших» между шагами."
    } else {
        "Большая часть точек так и не дошла до цели — шагов слишком мало для такого расстояния от шума до данных."
    }
    return "$stepsText $convText"
}

@Composable
private fun ForwardCanvasMilitary(original: Point2D, noisy: Point2D) {
    Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        val w = size.width
        val h = size.height
        fun toPx(p: Point2D) = Offset((p.x / DISPLAY_RANGE_MIL + 1f) / 2f * w, h - (p.y / DISPLAY_RANGE_MIL + 1f) / 2f * h)

        DmLabMilitary.realSample.forEach { p -> drawCircle(Color.White.copy(alpha = 0.10f), radius = 3f, center = toPx(p)) }
        drawLine(Color.White.copy(alpha = 0.4f), toPx(original), toPx(noisy), strokeWidth = 1.5f)
        drawCircle(Color(0xFF6BCB77), radius = 7f, center = toPx(original))
        drawCircle(Color(0xFF9D4EDD), radius = 8f, center = toPx(noisy))
    }
}

@Composable
private fun ReverseCanvasMilitary(samples: List<Point2D>) {
    Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        val w = size.width
        val h = size.height
        fun toPx(x: Float, y: Float) = Offset((x / DISPLAY_RANGE_MIL + 1f) / 2f * w, h - (y / DISPLAY_RANGE_MIL + 1f) / 2f * h)

        DmLabMilitary.realSample.forEach { p -> drawCircle(Color.White.copy(alpha = 0.12f), radius = 3.5f, center = toPx(p.x, p.y)) }
        samples.forEach { p -> drawCircle(Color(0xFF9D4EDD), radius = 4f, center = toPx(p.x, p.y)) }
    }
}
