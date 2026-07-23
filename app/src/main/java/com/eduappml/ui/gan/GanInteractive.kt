package com.eduappml.ui.gan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
fun GanInteractive(
    modifier: Modifier = Modifier,
    title: String?,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val textColor = Color.White
    val accent = Color(0xFF00C2A8)

    var steps by remember { mutableIntStateOf(300) }
    var lrD by remember { mutableFloatStateOf(0.05f) }
    var lrG by remember { mutableFloatStateOf(0.05f) }

    var gan by remember { mutableStateOf(GanLab.train(steps, lrD, lrG)) }
    LaunchedEffect(steps, lrD, lrG) {
        delay(150)
        gan = withContext(Dispatchers.Default) { GanLab.train(steps, lrD, lrG) }
    }

    val generated = remember(gan) { GanLab.generatedSample(gan.g, 120) }
    val discAcc = remember(gan) { GanLab.discriminatorAccuracy(gan) }

    LessonScaffold(
        eyebrow = "Интерактив",
        title = title ?: "GAN",
        onBack = onBack,
        onNext = onNext,
        nextLabel = "К решению →",
        accent = accent,
        modifier = modifier
    ) {
        Text(
            "Зелёные точки — настоящее облако, бирюзовые — то, что сейчас выдаёт генератор. Обучение может идти не строго по прямой к цели.",
            fontSize = 14.sp, color = textColor.copy(alpha = 0.75f), modifier = Modifier.padding(bottom = 16.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
        ) {
            GanCanvas(generated = generated)
        }

        Spacer(Modifier.height(14.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Параметры обучения", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))

                Text("Число шагов обучения = $steps", color = textColor, fontSize = 14.sp)
                Slider(value = steps.toFloat(), onValueChange = { steps = it.roundToInt() }, valueRange = 10f..1000f,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White))

                Spacer(Modifier.height(8.dp))
                Text("Скорость обучения дискриминатора = ${"%.3f".format(lrD)}", color = textColor, fontSize = 14.sp)
                Slider(value = lrD, onValueChange = { lrD = it }, valueRange = 0.01f..0.15f,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF6BCB77), activeTrackColor = Color(0xFF6BCB77)))

                Spacer(Modifier.height(8.dp))
                Text("Скорость обучения генератора = ${"%.3f".format(lrG)}", color = textColor, fontSize = 14.sp)
                Slider(value = lrG, onValueChange = { lrG = it }, valueRange = 0.01f..0.15f,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF00C2A8), activeTrackColor = Color(0xFF00C2A8)))

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = textColor.copy(alpha = 0.15f))
                Spacer(Modifier.height(12.dp))

                Text(
                    "Точность дискриминатора: ${(discAcc * 100).roundToInt()}% (50% означает, что он больше не отличает подделку от оригинала)",
                    color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(8.dp))
                Text(
                    text = ganInsight(lrD, lrG, discAcc),
                    color = textColor.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

private fun ganInsight(lrD: Float, lrG: Float, discAcc: Float): String {
    val balanceText = when {
        lrD > lrG * 1.8f -> "Дискриминатор учится заметно быстрее генератора — он может слишком легко находить подделки, и генератору будет труднее его догнать."
        lrG > lrD * 1.8f -> "Генератор учится заметно быстрее дискриминатора — тот может не успевать становиться строже, и обучение легко потеряет ориентир."
        else -> "Скорости обучения примерно сбалансированы — это обычно самый устойчивый режим для GAN."
    }
    val accText = when {
        discAcc > 0.85f -> "Дискриминатор пока легко различает реальное и сгенерированное — генератору есть куда обучаться."
        discAcc < 0.6f -> "Дискриминатор почти не отличает подделку от оригинала — генератор обучился прилично, но помните: результат может ещё колебаться при дальнейшем обучении."
        else -> "Дискриминатор угадывает заметно лучше случайного, но уже не идеально — обучение где-то посередине пути."
    }
    return "$balanceText $accText"
}

@Composable
private fun GanCanvas(generated: List<Point2D>) {
    Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        val w = size.width
        val h = size.height
        fun toPx(x: Float, y: Float) = Offset((x / GanLab.FEATURE_MAX) * w, h - (y / GanLab.FEATURE_MAX) * h)

        GanLab.realSample.forEach { p ->
            drawCircle(Color(0xFF6BCB77).copy(alpha = 0.55f), radius = 4.5f, center = toPx(p.x, p.y))
        }
        generated.forEach { p ->
            drawCircle(Color(0xFF00C2A8), radius = 4.5f, center = toPx(p.x, p.y))
        }
    }
}
