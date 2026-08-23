package com.eduappml.ui.som

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
import com.eduappml.ui.common.BottomPillButton
import com.eduappml.ui.common.LessonScaffold
import com.eduappml.ui.common.buildInteractiveChatPrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Военный вариант экрана "Интерактив" для темы SOM: та же логика, что у
 * [SomInteractive] (который остаётся нетронутым и больше не вызывается
 * для id = "som"), только со сценой складских красок для маскировки.
 */
@Composable
fun SomInteractiveMilitary(
    modifier: Modifier = Modifier,
    title: String?,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onOpenChat: (String) -> Unit = {}
) {
    val textColor = Color.White
    val accent = Color(0xFFE63946)
    val topicTitle = title ?: "Карта Кохонена"

    var step by remember { mutableIntStateOf(0) }
    var seed by remember { mutableIntStateOf(7) }

    var map by remember { mutableStateOf(SomLabMilitary.trainUpTo(step, seed)) }
    LaunchedEffect(step, seed) {
        delay(80)
        map = withContext(Dispatchers.Default) { SomLabMilitary.trainUpTo(step, seed) }
    }

    val smoothness = remember(map) { SomLabMilitary.averageNeighborDistance(map) }

    LessonScaffold(
        eyebrow = "Интерактив",
        title = title ?: "Карта Кохонена",
        onBack = onBack,
        onNext = onNext,
        nextLabel = "К решению →",
        accent = accent,
        modifier = modifier
    ) {
        Text(
            "Подвигайте «шаг обучения» — понаблюдайте, как случайные цвета в сетке слева превращаются в плавную карту.",
            fontSize = 14.sp, color = textColor.copy(alpha = 0.75f), modifier = Modifier.padding(bottom = 16.dp)
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(2f)) {
                Text("Карта (12×12 нейронов)", color = textColor.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                ) {
                    SomGridCanvasMilitary(map = map)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Исходные краски", color = textColor.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                ) {
                    SomInputSwatchesMilitary()
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Обучение", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))

                Text("Шаг обучения = $step из ${SomLabMilitary.DECAY_HORIZON}", color = textColor, fontSize = 14.sp)
                Slider(value = step.toFloat(), onValueChange = { step = it.roundToInt() }, valueRange = 0f..SomLabMilitary.DECAY_HORIZON.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent))

                Spacer(Modifier.height(10.dp))
                BottomPillButton(text = "Переинициализировать карту", onClick = { seed = (seed + 1) % 997; step = 0 })

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = textColor.copy(alpha = 0.15f))
                Spacer(Modifier.height(12.dp))

                Text("Средняя разница соседних нейронов: ${"%.3f".format(smoothness)}", color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

                Spacer(Modifier.height(8.dp))
                Text(
                    text = somInsightMilitary(step),
                    color = textColor.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(10.dp))
                AskChatButton(accent = accent, onClick = {
                    onOpenChat(
                        buildInteractiveChatPrompt(
                            topicTitle,
                            "шаг обучения = $step из ${SomLabMilitary.DECAY_HORIZON}",
                            "средняя разница соседних нейронов ${"%.3f".format(smoothness)}"
                        )
                    )
                })
            }
        }
    }
}

private fun somInsightMilitary(step: Int): String = when {
    step == 0 -> "Шаг 0 — сетка ещё не видела ни одного примера, цвета полностью случайны."
    step < 20 -> "На таких ранних шагах скорость обучения и радиус окрестности ещё велики — карта организуется очень быстро, крупными областями."
    step < 100 -> "Основная организация уже произошла — дальше карта в основном уточняет детали."
    else -> "К этому шагу скорость обучения и радиус почти обнулились — карта практически перестала меняться, что бы ни происходило дальше."
}

@Composable
private fun SomGridCanvasMilitary(map: SomLabMilitary.Map) {
    Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
        val cell = size.width / SomLabMilitary.GRID_SIZE
        for (gx in 0 until SomLabMilitary.GRID_SIZE) {
            for (gy in 0 until SomLabMilitary.GRID_SIZE) {
                val w = map.weights[gx][gy]
                val color = Color(w[0].coerceIn(0f, 1f), w[1].coerceIn(0f, 1f), w[2].coerceIn(0f, 1f))
                drawRect(color, topLeft = Offset(gx * cell, gy * cell), size = androidx.compose.ui.geometry.Size(cell + 1f, cell + 1f))
            }
        }
    }
}

@Composable
private fun SomInputSwatchesMilitary() {
    Column(modifier = Modifier.fillMaxSize().padding(6.dp)) {
        SomLabMilitary.inputColors.forEach { c ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(c.r, c.g, c.b))
            )
        }
    }
}
