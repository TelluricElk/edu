package com.eduappml.ui.interactive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.eduappml.ui.ae.AeInteractiveMilitary
import com.eduappml.ui.cnn.CnnInteractiveMilitary
import com.eduappml.ui.common.AskChatButton
import com.eduappml.ui.common.LessonScaffold
import com.eduappml.ui.common.buildInteractiveChatPrompt
import com.eduappml.ui.dm.DmInteractiveMilitary
import com.eduappml.ui.dt.DtInteractiveMilitary
import com.eduappml.ui.fc.FcInteractiveMilitary
import com.eduappml.ui.gan.GanInteractiveMilitary
import com.eduappml.ui.gb.GbInteractiveMilitary
import com.eduappml.ui.gnn.GnnInteractiveMilitary
import com.eduappml.ui.km.KmInteractiveMilitary
import com.eduappml.ui.knn.KnnInteractiveMilitary
import com.eduappml.ui.knn.KnnLab
import com.eduappml.ui.knn.KnnMetric
import com.eduappml.ui.knn.KnnWeighting
import com.eduappml.ui.logr.LogrInteractiveMilitary
import com.eduappml.ui.lr.LrInteractiveMilitary
import com.eduappml.ui.nb.NbInteractiveMilitary
import com.eduappml.ui.rf.RfInteractiveMilitary
import com.eduappml.ui.rl.RlInteractiveMilitary
import com.eduappml.ui.rnn.RnnInteractiveMilitary
import com.eduappml.ui.som.SomInteractiveMilitary
import com.eduappml.ui.svm.SvmInteractiveMilitary
import com.eduappml.ui.tr.TrInteractiveMilitary
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import com.eduappml.ui.common.designPx

/**
 * Экран "Интерактив" (пузырь-лампочка). Параметры обучения условные —
 * реального обучения модели на устройстве не происходит, всё считается
 * "по требованию" на маленьком фиксированном датасете (см. *Lab.kt каждой темы).
 *
 * [onOpenChat] — колбэк для кнопки "объяснить" рядом с текущим результатом:
 * пользователь подвигал ползунки, получил результат и может спросить у
 * Edu.AI, почему именно так — см. аналогичный параметр в ResultScreen.kt.
 *
 * Военный контент: темы "knn", "lr" и "logr" сейчас диспетчеризуются на
 * *Military-варианты (KnnInteractiveMilitary, LrInteractiveMilitary,
 * LogrInteractiveMilitary) — отдельные публичные composable-файлы в
 * пакетах com.eduappml.ui.knn, com.eduappml.ui.lr и com.eduappml.ui.logr.
 * Старые приватные KnnInteractive/KnnCanvas ниже в этом же файле и старые
 * файлы LrInteractive.kt/LogrInteractive.kt остаются нетронутыми, но
 * больше не вызываются ни для одной темы — это сознательный выбор
 * (см. обсуждение с владельцем проекта), а не забытый код.
 */
@Composable
fun InteractiveScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    id: String,
    screenType: String,
    title: String? = null,
    onNext: () -> Unit = {},
    onOpenChat: (String) -> Unit = {}
) {
    when (id) {
        "knn" -> KnnInteractiveMilitary(modifier = modifier, title = title, onBack = onBack, onNext = onNext, onOpenChat = onOpenChat)
        "lr" -> LrInteractiveMilitary(modifier = modifier, title = title, onBack = onBack, onNext = onNext, onOpenChat = onOpenChat)
        "logr" -> LogrInteractiveMilitary(modifier = modifier, title = title, onBack = onBack, onNext = onNext, onOpenChat = onOpenChat)
        "svm" -> SvmInteractiveMilitary(modifier = modifier, title = title, onBack = onBack, onNext = onNext, onOpenChat = onOpenChat)
        "dt" -> DtInteractiveMilitary(modifier = modifier, title = title, onBack = onBack, onNext = onNext, onOpenChat = onOpenChat)
        "nb" -> NbInteractiveMilitary(modifier = modifier, title = title, onBack = onBack, onNext = onNext, onOpenChat = onOpenChat)
        "rf" -> RfInteractiveMilitary(modifier = modifier, title = title, onBack = onBack, onNext = onNext, onOpenChat = onOpenChat)
        "gb" -> GbInteractiveMilitary(modifier = modifier, title = title, onBack = onBack, onNext = onNext, onOpenChat = onOpenChat)
        "km" -> KmInteractiveMilitary(modifier = modifier, title = title, onBack = onBack, onNext = onNext, onOpenChat = onOpenChat)
        "fc" -> FcInteractiveMilitary(modifier = modifier, title = title, onBack = onBack, onNext = onNext, onOpenChat = onOpenChat)
        "som" -> SomInteractiveMilitary(modifier = modifier, title = title, onBack = onBack, onNext = onNext, onOpenChat = onOpenChat)
        "rl" -> RlInteractiveMilitary(modifier = modifier, title = title, onBack = onBack, onNext = onNext, onOpenChat = onOpenChat)
        "ae" -> AeInteractiveMilitary(modifier = modifier, title = title, onBack = onBack, onNext = onNext, onOpenChat = onOpenChat)
        "gan" -> GanInteractiveMilitary(modifier = modifier, title = title, onBack = onBack, onNext = onNext, onOpenChat = onOpenChat)
        "cnn" -> CnnInteractiveMilitary(modifier = modifier, title = title, onBack = onBack, onNext = onNext, onOpenChat = onOpenChat)
        "rnn" -> RnnInteractiveMilitary(modifier = modifier, title = title, onBack = onBack, onNext = onNext, onOpenChat = onOpenChat)
        "gnn" -> GnnInteractiveMilitary(modifier = modifier, title = title, onBack = onBack, onNext = onNext, onOpenChat = onOpenChat)
        "tr" -> TrInteractiveMilitary(modifier = modifier, title = title, onBack = onBack, onNext = onNext, onOpenChat = onOpenChat)
        "dm" -> DmInteractiveMilitary(modifier = modifier, title = title, onBack = onBack, onNext = onNext, onOpenChat = onOpenChat)
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

// ---------------------------------------------------------------------
// Ниже — исходная (гражданская) реализация интерактива k-NN. С момента
// подключения KnnInteractiveMilitary в диспетчере выше эти функции больше
// не вызываются ни из одного места в приложении, но намеренно оставлены
// как есть: код никуда не делся, просто стал недостижим.
// ---------------------------------------------------------------------

@Composable
private fun KnnInteractive(
    modifier: Modifier = Modifier,
    title: String?,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onOpenChat: (String) -> Unit = {}
) {
    val textColor = Color.White
    val topicTitle = title ?: "k-NN"

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
                Spacer(Modifier.height(10.dp))
                AskChatButton(accent = Color(0xFF00C2A8), onClick = {
                    onOpenChat(
                        buildInteractiveChatPrompt(
                            topicTitle,
                            "k = $k, метрика — ${metric.label}, взвешивание — ${weighting.label}",
                            "точность на контрольной выборке ${(accuracy * 100).roundToInt()}%"
                        )
                    )
                })
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
                    .clickable { onSelected(index) },
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
            drawCircle(color = color, radius = designPx(6f), center = p)
            drawCircle(color = Color.White.copy(alpha = 0.6f), radius = designPx(6f), center = p, style = Stroke(width = designPx(1.2f)))
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
                    strokeWidth = designPx(1.5f)
                )
            }
            drawCircle(color = Color.White, radius = designPx(9f), center = qp, style = Stroke(width = designPx(3f)))
            drawCircle(color = Color.Black.copy(alpha = 0.4f), radius = designPx(9f), center = qp)
        }
    }
}
