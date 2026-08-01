package com.eduappml.ui.cnn

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
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

private fun defaultDrawing(): List<List<Float>> =
    List(GRID) { r -> List(GRID) { c -> if (r == 3) 1f else 0f } } // стартовая горизонтальная линия

@Composable
fun CnnInteractive(
    modifier: Modifier = Modifier,
    title: String?,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onOpenChat: (String) -> Unit = {}
) {
    val textColor = Color.White
    val accent = Color(0xFFFFD93D)
    val topicTitle = title ?: "Свёрточная сеть"

    var nFilters by remember { mutableIntStateOf(1) }
    var learningRate by remember { mutableFloatStateOf(0.05f) }
    var epochs by remember { mutableIntStateOf(15) }

    var net by remember { mutableStateOf(CnnLab.train(nFilters, learningRate, epochs)) }
    LaunchedEffect(nFilters, learningRate, epochs) {
        delay(120)
        net = withContext(Dispatchers.Default) { CnnLab.train(nFilters, learningRate, epochs) }
    }

    val testAcc = remember(net) { CnnLab.accuracy(net, CnnLab.testSet) }

    var drawing by remember { mutableStateOf(defaultDrawing()) }
    val pixelsArray = remember(drawing) { Array(GRID) { r -> FloatArray(GRID) { c -> drawing[r][c] } } }
    val prediction = remember(net, drawing) { net.predictProba(pixelsArray) }
    val featureMaps = remember(net, drawing) { net.featureMaps(pixelsArray) }

    LessonScaffold(
        eyebrow = "Интерактив",
        title = title ?: "Свёрточная сеть",
        onBack = onBack,
        onNext = onNext,
        nextLabel = "К решению →",
        accent = accent,
        modifier = modifier
    ) {
        Text(
            "Нарисуйте линию сами (тап по клеткам) — сеть классифицирует её в реальном времени, независимо от положения.",
            fontSize = 14.sp, color = textColor.copy(alpha = 0.75f), modifier = Modifier.padding(bottom = 14.dp)
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Ваш рисунок", color = textColor.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                DrawableGrid(drawing = drawing, onToggle = { r, c ->
                    drawing = drawing.mapIndexed { ri, row ->
                        if (ri != r) row else row.mapIndexed { ci, v -> if (ci == c) (if (v > 0.5f) 0f else 1f) else v }
                    }
                })
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BottomPillButton(text = "Очистить", onClick = { drawing = List(GRID) { List(GRID) { 0f } } })
                    BottomPillButton(text = "Линия", onClick = { drawing = defaultDrawing() })
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Карты признаков", color = textColor.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                FeatureMapsGrid(featureMaps = featureMaps)
            }
        }

        Spacer(Modifier.height(10.dp))
        val isHorizontal = prediction >= 0.5f
        Text(
            text = "Сеть видит: ${if (isHorizontal) "горизонтальная линия" else "вертикальная линия"} (уверенность ${(if (isHorizontal) prediction else 1f - prediction).let { (it * 100).roundToInt() }}%)",
            color = textColor, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(Modifier.height(14.dp))

        Text("Обученные фильтры", color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            net.conv.filters.forEach { filter ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                ) {
                    FilterCanvas(filter = filter)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Параметры обучения", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))

                Text("Число фильтров = $nFilters", color = textColor, fontSize = 14.sp)
                Slider(value = nFilters.toFloat(), onValueChange = { nFilters = it.roundToInt() }, valueRange = 1f..6f, steps = 4,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White))

                Spacer(Modifier.height(8.dp))
                Text("Скорость обучения = ${"%.2f".format(learningRate)}", color = textColor, fontSize = 14.sp)
                Slider(value = learningRate, onValueChange = { learningRate = it }, valueRange = 0.01f..0.15f,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White))

                Spacer(Modifier.height(8.dp))
                Text("Число эпох = $epochs", color = textColor, fontSize = 14.sp)
                Slider(value = epochs.toFloat(), onValueChange = { epochs = it.roundToInt() }, valueRange = 1f..30f,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White))

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = textColor.copy(alpha = 0.15f))
                Spacer(Modifier.height(12.dp))

                Text("Точность на контрольной выборке: ${(testAcc * 100).roundToInt()}%", color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)

                Spacer(Modifier.height(8.dp))
                Text(text = cnnInsight(nFilters, testAcc), color = textColor.copy(alpha = 0.75f), fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(10.dp))
                AskChatButton(accent = accent, onClick = {
                    onOpenChat(
                        buildInteractiveChatPrompt(
                            topicTitle,
                            "число фильтров = $nFilters, скорость обучения = ${"%.2f".format(learningRate)}, эпох = $epochs",
                            "точность на контрольной выборке ${(testAcc * 100).roundToInt()}%"
                        )
                    )
                })
            }
        }
    }
}

private fun cnnInsight(nFilters: Int, testAcc: Float): String {
    val filterText = if (nFilters == 1) {
        "Один фильтр — сети часто не хватает выразительности, чтобы надёжно различать оба типа линий сразу."
    } else {
        "Несколько фильтров могут специализироваться по-разному: один лучше ловит горизонтальные признаки, другой — вертикальные."
    }
    val accText = when {
        testAcc >= 0.9f -> "Высокая точность — фильтры научились находить линию независимо от её положения на изображении."
        testAcc >= 0.7f -> "Неплохой результат, но есть куда расти — попробуйте больше фильтров или эпох."
        else -> "Пока сеть путается — пространства для улучшения ещё много."
    }
    return "$filterText $accText"
}

@Composable
private fun DrawableGrid(drawing: List<List<Float>>, onToggle: (Int, Int) -> Unit) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (canvasSize.width == 0) return@detectTapGestures
                    val cell = canvasSize.width / GRID
                    val c = (offset.x / cell).toInt().coerceIn(0, GRID - 1)
                    val r = (offset.y / cell).toInt().coerceIn(0, GRID - 1)
                    onToggle(r, c)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cell = size.width / GRID
            for (r in 0 until GRID) {
                for (c in 0 until GRID) {
                    val v = drawing[r][c]
                    drawRect(
                        Color.White.copy(alpha = 0.12f + v * 0.75f),
                        topLeft = Offset(c * cell, r * cell),
                        size = androidx.compose.ui.geometry.Size(cell - 1f, cell - 1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureMapsGrid(featureMaps: Array<Array<FloatArray>>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
            .padding(6.dp)
    ) {
        val cols = if (featureMaps.size <= 2) featureMaps.size.coerceAtLeast(1) else 2
        val rows = (featureMaps.size + cols - 1) / cols
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (row in 0 until rows) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (col in 0 until cols) {
                        val idx = row * cols + col
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            if (idx < featureMaps.size) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val map = featureMaps[idx]
                                    var maxV = 0.01f
                                    for (rowArr in map) {
                                        for (v in rowArr) {
                                            if (v > maxV) maxV = v
                                        }
                                    }
                                    val cell = size.width / map.size
                                    for (i in map.indices) {
                                        for (j in map[i].indices) {
                                            val intensity = (map[i][j] / maxV).coerceIn(0f, 1f)
                                            drawRect(
                                                Color(0xFFFFD93D).copy(alpha = intensity),
                                                topLeft = Offset(j * cell, i * cell),
                                                size = androidx.compose.ui.geometry.Size(cell - 1f, cell - 1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterCanvas(filter: Array<FloatArray>) {
    Canvas(modifier = Modifier.fillMaxSize().padding(3.dp)) {
        var maxAbs = 0.01f
        for (rowArr in filter) {
            for (v in rowArr) {
                val a = kotlin.math.abs(v)
                if (a > maxAbs) maxAbs = a
            }
        }
        val cell = size.width / filter.size
        for (i in filter.indices) {
            for (j in filter[i].indices) {
                val v = filter[i][j] / maxAbs
                val color = if (v >= 0f) Color(0xFF6BCB77).copy(alpha = v.coerceIn(0f, 1f)) else Color(0xFFFF6B6B).copy(alpha = (-v).coerceIn(0f, 1f))
                drawRect(color, topLeft = Offset(j * cell, i * cell), size = androidx.compose.ui.geometry.Size(cell - 1f, cell - 1f))
            }
        }
    }
}
