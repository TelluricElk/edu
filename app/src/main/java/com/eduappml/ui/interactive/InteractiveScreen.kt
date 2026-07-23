package com.eduappml.ui.interactive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eduappml.ui.common.LessonScaffold
import com.eduappml.ui.dt.DtInteractive
import com.eduappml.ui.fc.FcInteractive
import com.eduappml.ui.gb.GbInteractive
import com.eduappml.ui.km.KmInteractive
import com.eduappml.ui.knn.KnnLab
import com.eduappml.ui.knn.KnnMetric
import com.eduappml.ui.knn.KnnWeighting
import com.eduappml.ui.logr.LogrInteractive
import com.eduappml.ui.lr.LrInteractive
import com.eduappml.ui.nb.NbInteractive
import com.eduappml.ui.rf.RfInteractive
import com.eduappml.ui.rl.RlInteractive
import com.eduappml.ui.som.SomInteractive
import com.eduappml.ui.svm.SvmInteractive
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Экран "Интерактив" (пузырь-лампочка). Параметры обучения условные —
 * реального обучения модели на устройстве не происходит, всё считается
 * "по требованию" на маленьком фиксированном датасете (см. *Lab.kt каждой темы).
 */
@Composable
fun InteractiveScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    id: String,
    screenType: String,
    title: String? = null,
    onNext: () -> Unit = {}
) {
    when (id) {
        "knn" -> KnnInteractive(modifier = modifier, title = title, onBack = onBack, onNext = onNext)
        "lr" -> LrInteractive(modifier = modifier, title = title, onBack = onBack, onNext = onNext)
        "logr" -> LogrInteractive(modifier = modifier, title = title, onBack = onBack, onNext = onNext)
        "svm" -> SvmInteractive(modifier = modifier, title = title, onBack = onBack, onNext = onNext)
        "dt" -> DtInteractive(modifier = modifier, title = title, onBack = onBack, onNext = onNext)
        "nb" -> NbInteractive(modifier = modifier, title = title, onBack = onBack, onNext = onNext)
        "rf" -> RfInteractive(modifier = modifier, title = title, onBack = onBack, onNext = onNext)
        "gb" -> GbInteractive(modifier = modifier, title = title, onBack = onBack, onNext = onNext)
        "km" -> KmInteractive(modifier = modifier, title = title, onBack = onBack, onNext = onNext)
        "fc" -> FcInteractive(modifier = modifier, title = title, onBack = onBack, onNext = onNext)
        "som" -> SomInteractive(modifier = modifier, title = title, onBack = onBack, onNext = onNext)
        "rl" -> RlInteractive(modifier = modifier, title = title, onBack = onBack, onNext = onNext)
        else -> ComingSoonInteractive(modifier = modifier, title = title, id = id, onBack = onBack, onNext = onNext)
    }
}

@Composable
private fun ComingSoonInteractive(
    modifier: Modifier = Modifier,
    title: String?,
    id: String,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    LessonScaffold(
        eyebrow = "Интерактив",
        title = title ?: id,
        onBack = onBack,
        onNext = onNext,
        accent = Color(0xFF00C2A8),
        modifier = modifier
    ) {
        Text(
            text = "Интерактив для этой темы ещё готовится",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Загляните позже — здесь появится симуляция обучения с настраиваемыми параметрами.",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp
        )
    }
}

@Composable
private fun KnnInteractive(
    modifier: Modifier = Modifier,
    title: String?,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val textColor = Color.White

    var k by remember { mutableIntStateOf(5) }
    var metric by remember { mutableStateOf(KnnMetric.EUCLIDEAN) }
    var weighting by remember { mutableStateOf(KnnWeighting.UNIFORM) }

    // Точка запроса, которую пользователь ставит тапом по графику (в координатах датасета 0..10)
    var queryPoint by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var predictedLabel by remember { mutableStateOf<String?>(null) }

    var accuracy by remember { mutableFloatStateOf(0f) }

    // Пересчитываем точность на контрольной выборке с небольшой задержкой,
    // чтобы не грузить пересчётом каждое промежуточное положение слайдера
    LaunchedEffect(k, metric, weighting) {
        delay(120)
        accuracy = KnnLab.evaluateAccuracy(k, metric, weighting)
        queryPoint?.let { (sw, sz) ->
            predictedLabel = KnnLab.classify(sw, sz, k, metric, weighting)
        }
    }

    LessonScaffold(
        eyebrow = "Интерактив",
        title = title ?: "k-NN",
        onBack = onBack,
        onNext = onNext,
        nextLabel = "К решению →",
        accent = Color(0xFF00C2A8),
        modifier = modifier
    ) {
        Text(
            text = "Классификация фруктов по сладости и размеру. Коснитесь графика, чтобы добавить новый образец и увидеть, как его классифицирует модель.",
            fontSize = 14.sp,
            color = textColor.copy(alpha = 0.75f),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // ---------- График ----------
        var canvasSize by remember { mutableStateOf(IntSize.Zero) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                .onSizeChanged { canvasSize = it }
                .pointerInput(k, metric, weighting) {
                    detectTapGestures { offset ->
                        if (canvasSize.width == 0 || canvasSize.height == 0) return@detectTapGestures
                        val sweetness = (offset.x / canvasSize.width) * KnnLab.FEATURE_MAX
                        val size = (1f - offset.y / canvasSize.height) * KnnLab.FEATURE_MAX
                        queryPoint = sweetness to size
                        predictedLabel = KnnLab.classify(sweetness, size, k, metric, weighting)
                    }
                }
        ) {
            KnnCanvas(
                k = k,
                metric = metric,
                weighting = weighting,
                queryPoint = queryPoint
            )
        }

        Spacer(Modifier.height(10.dp))

        predictedLabel?.let { label ->
            val color = KnnLab.classColors[label] ?: Color.White
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Предсказанный класс новой точки: $label",
                    color = textColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        // ---------- Параметры ----------
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Параметры обучения", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))

                Text("k (число соседей) = $k", color = textColor, fontSize = 14.sp)
                Slider(
                    value = k.toFloat(),
                    onValueChange = { k = it.roundToInt().coerceIn(1, 15) },
                    valueRange = 1f..15f,
                    steps = 13,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
                )

                Spacer(Modifier.height(8.dp))
                Text("Метрика расстояния", color = textColor, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                SegmentedRow(
                    options = KnnMetric.entries.map { it.label },
                    selectedIndex = KnnMetric.entries.indexOf(metric),
                    onSelected = { metric = KnnMetric.entries[it] }
                )

                Spacer(Modifier.height(12.dp))
                Text("Взвешивание голосов соседей", color = textColor, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                SegmentedRow(
                    options = KnnWeighting.entries.map { it.label },
                    selectedIndex = KnnWeighting.entries.indexOf(weighting),
                    onSelected = { weighting = KnnWeighting.entries[it] }
                )

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = textColor.copy(alpha = 0.15f))
                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Точность на контрольной выборке: ${(accuracy * 100).roundToInt()}%",
                    color = textColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = when {
                        k <= 2 -> "Малое k — модель чувствительна к шуму и выбросам."
                        k >= 12 -> "Большое k — модель слишком сглаживает границы между классами."
                        else -> "Хороший баланс между чувствительностью и устойчивостью."
                    },
                    color = textColor.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun SegmentedRow(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f))
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) Color.White.copy(alpha = 0.22f) else Color.Transparent)
                    .padding(vertical = 10.dp)
                    .pointerInput(index) { detectTapGestures { onSelected(index) } },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

/**
 * Карта решений k-NN: закрашивает сетку 26x26 предсказанным классом (полупрозрачно),
 * поверх рисует точки обучающей выборки и, если поставлена, точку запроса
 * с линиями к её k ближайшим соседям.
 */
@Composable
private fun KnnCanvas(
    k: Int,
    metric: KnnMetric,
    weighting: KnnWeighting,
    queryPoint: Pair<Float, Float>?
) {
    val gridSteps = 26
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        fun toPx(sweetness: Float, sz: Float): Offset =
            Offset(
                (sweetness / KnnLab.FEATURE_MAX) * w,
                h - (sz / KnnLab.FEATURE_MAX) * h
            )

        // Фон — карта решений
        val cellW = w / gridSteps
        val cellH = h / gridSteps
        for (gx in 0 until gridSteps) {
            for (gy in 0 until gridSteps) {
                val sweetness = ((gx + 0.5f) / gridSteps) * KnnLab.FEATURE_MAX
                val sz = (1f - (gy + 0.5f) / gridSteps) * KnnLab.FEATURE_MAX
                val label = KnnLab.classify(sweetness, sz, k, metric, weighting)
                val color = KnnLab.classColors[label] ?: Color.Gray
                drawRect(
                    color = color.copy(alpha = 0.16f),
                    topLeft = Offset(gx * cellW, gy * cellH),
                    size = androidx.compose.ui.geometry.Size(cellW + 1f, cellH + 1f)
                )
            }
        }

        // Точки обучающей выборки
        KnnLab.trainSet.forEach { point ->
            val p = toPx(point.sweetness, point.size)
            val color = KnnLab.classColors[point.label] ?: Color.White
            drawCircle(color = color, radius = 6f, center = p)
            drawCircle(color = Color.White.copy(alpha = 0.6f), radius = 6f, center = p, style = Stroke(width = 1.2f))
        }

        // Точка запроса + линии к соседям
        queryPoint?.let { (sw, sz) ->
            val neighbors = KnnLab.nearestNeighbors(sw, sz, k, metric)
            val qp = toPx(sw, sz)
            neighbors.forEach { n ->
                val np = toPx(n.point.sweetness, n.point.size)
                drawLine(
                    color = Color.White.copy(alpha = 0.35f),
                    start = qp,
                    end = np,
                    strokeWidth = 1.5f
                )
            }
            drawCircle(color = Color.White, radius = 9f, center = qp, style = Stroke(width = 3f))
            drawCircle(color = Color.Black.copy(alpha = 0.4f), radius = 9f, center = qp)
        }
    }
}
