package com.eduappml.ui.rl

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
import kotlin.math.roundToInt
import com.eduappml.ui.common.designPx

/**
 * Военный вариант экрана "Интерактив" для темы обучения с подкреплением:
 * та же логика, что у [RlInteractive] (который остаётся нетронутым и
 * больше не вызывается для id = "rl"), только со сценой полигона с
 * препятствиями вместо лабиринта.
 */
@Composable
fun RlInteractiveMilitary(
    modifier: Modifier = Modifier,
    title: String?,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onOpenChat: (String) -> Unit = {}
) {
    val textColor = Color.White
    val accent = Color(0xFF00B4D8)
    val topicTitle = title ?: "Обучение с подкреплением"

    var episodes by remember { mutableIntStateOf(50) }
    var alpha by remember { mutableFloatStateOf(0.3f) }
    var epsilon by remember { mutableFloatStateOf(0.2f) }

    var result by remember { mutableStateOf(RlLabMilitary.train(episodes, alpha, epsilon)) }
    LaunchedEffect(episodes, alpha, epsilon) {
        delay(120)
        result = withContext(Dispatchers.Default) { RlLabMilitary.train(episodes, alpha, epsilon) }
    }

    val path = remember(result) { RlLabMilitary.greedyPath(result.q) }
    val reachedGoal = path.lastOrNull() == RlLabMilitary.GOAL
    val recentAvg = remember(result) {
        val last = result.stepsPerEpisode.takeLast(10)
        if (last.isEmpty()) 0f else last.average().toFloat()
    }

    LessonScaffold(
        eyebrow = "Интерактив",
        title = title ?: "Обучение с подкреплением",
        onBack = onBack,
        onNext = onNext,
        nextLabel = "К решению →",
        accent = accent,
        modifier = modifier
    ) {
        Text(
            "Увеличивайте число эпизодов и наблюдайте, как путь агента (белая линия) укорачивается и находит проём в ограждении.",
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
            RlMazeCanvasMilitary(path = path)
        }

        Spacer(Modifier.height(10.dp))
        if (!reachedGoal) {
            Text("Агент пока не находит пункт сбора за разумное число шагов — увеличьте число эпизодов.", color = Color(0xFFFF6B6B), fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
        }

        Text("Кривая обучения: шагов до цели по эпизодам", color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp, top = 6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
        ) {
            RlLearningCurveCanvasMilitary(steps = result.stepsPerEpisode)
        }

        Spacer(Modifier.height(14.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Параметры обучения", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))

                Text("Число эпизодов = $episodes", color = textColor, fontSize = 14.sp)
                Slider(value = episodes.toFloat(), onValueChange = { episodes = it.roundToInt() }, valueRange = 1f..300f,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White))

                Spacer(Modifier.height(8.dp))
                Text("Скорость обучения (α) = ${"%.2f".format(alpha)}", color = textColor, fontSize = 14.sp)
                Slider(value = alpha, onValueChange = { alpha = it }, valueRange = 0.05f..0.9f,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White))

                Spacer(Modifier.height(8.dp))
                Text("Исследование (ε) = ${"%.2f".format(epsilon)}", color = textColor, fontSize = 14.sp)
                Slider(value = epsilon, onValueChange = { epsilon = it }, valueRange = 0f..0.9f,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent))

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = textColor.copy(alpha = 0.15f))
                Spacer(Modifier.height(12.dp))

                Text("Среднее число шагов за последние 10 эпизодов: ${"%.1f".format(recentAvg)}", color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)

                Spacer(Modifier.height(8.dp))
                Text(
                    text = rlInsightMilitary(episodes, epsilon, recentAvg),
                    color = textColor.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(10.dp))
                AskChatButton(accent = accent, onClick = {
                    onOpenChat(
                        buildInteractiveChatPrompt(
                            topicTitle,
                            "число эпизодов = $episodes, α = ${"%.2f".format(alpha)}, ε = ${"%.2f".format(epsilon)}",
                            "среднее число шагов за последние 10 эпизодов ${"%.1f".format(recentAvg)}, цель ${if (reachedGoal) "достигнута" else "пока не достигнута"}"
                        )
                    )
                })
            }
        }
    }
}

private fun rlInsightMilitary(episodes: Int, epsilon: Float, recentAvg: Float): String {
    val episodesText = when {
        episodes < 20 -> "Мало эпизодов — агент только начал изучать полигон, Q-таблица почти пуста."
        episodes < 80 -> "Уже заметен прогресс — агент начинает находить проём в ограждении."
        else -> "Достаточно эпизодов, чтобы политика почти стабилизировалась."
    }
    val epsilonText = when {
        epsilon > 0.6f -> "Высокое ε означает, что агент почти всё время действует случайно — даже выучив хорошую стратегию, он редко ею пользуется. Оптимальный путь — 14 шагов, но при таком ε агент до него практически не доходит."
        epsilon < 0.05f -> "Низкое ε означает почти полное отсутствие исследования — если агент рано нашёл хоть какой-то путь, он будет повторять именно его, даже если существует более короткий."
        else -> "Такое ε даёт разумный баланс: агент в основном использует то, что уже выучил, но иногда пробует новое."
    }
    return "$episodesText $epsilonText"
}

@Composable
private fun RlMazeCanvasMilitary(path: List<Pair<Int, Int>>) {
    Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        val cell = size.width / RlLabMilitary.GRID

        fun toPx(pos: Pair<Int, Int>) = Offset(
            (pos.first + 0.5f) * cell,
            size.height - (pos.second + 0.5f) * cell
        )

        // Клетки-ограждение
        RlLabMilitary.WALLS.forEach { w ->
            drawRect(
                Color.White.copy(alpha = 0.25f),
                topLeft = Offset(w.first * cell, size.height - (w.second + 1) * cell),
                size = androidx.compose.ui.geometry.Size(cell, cell)
            )
        }

        // Старт и пункт сбора
        drawCircle(Color(0xFF4D96FF), radius = cell * 0.28f, center = toPx(RlLabMilitary.START))
        drawCircle(Color(0xFF6BCB77), radius = cell * 0.32f, center = toPx(RlLabMilitary.GOAL))

        // Путь агента
        var prev: Offset? = null
        path.forEach { p ->
            val pt = toPx(p)
            prev?.let { drawLine(Color.White, it, pt, strokeWidth = designPx(3f)) }
            prev = pt
        }
        path.lastOrNull()?.let { drawCircle(Color.White, radius = cell * 0.18f, center = toPx(it), style = Stroke(width = designPx(2f))) }
    }
}

@Composable
private fun RlLearningCurveCanvasMilitary(steps: List<Int>) {
    Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        if (steps.isEmpty()) return@Canvas
        val w = size.width
        val h = size.height
        val maxSteps = steps.max().coerceAtLeast(1)

        fun toPx(idx: Int, value: Int) = Offset(
            (idx.toFloat() / (steps.size - 1).coerceAtLeast(1)) * w,
            h - (value.toFloat() / maxSteps) * h
        )

        var prev: Offset? = null
        steps.forEachIndexed { idx, v ->
            val pt = toPx(idx, v)
            prev?.let { drawLine(Color(0xFF00B4D8), it, pt, strokeWidth = designPx(2f)) }
            prev = pt
        }
    }
}
