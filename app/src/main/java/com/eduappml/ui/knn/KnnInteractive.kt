package com.eduappml.ui.knn

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eduappml.ui.common.AskChatButton
import com.eduappml.ui.common.LessonScaffold
import com.eduappml.ui.common.buildInteractiveChatPrompt
import com.eduappml.ui.common.designPx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val ClassColors = listOf(
    Color(0xFF6BCB77),   // ложное срабатывание
    Color(0xFFFFD93D),   // подозрительно
    Color(0xFFFF6B6B)    // подтверждённый инцидент
)
private val ColorAccent = Color(0xFFFFD93D)

/**
 * Интерактив темы «Метод ближайших соседей»: триаж алертов в SOC.
 *
 * Переключатель нормализации — главный элемент экрана. При выключенной
 * нормализации карта решений распадается на вертикальные полосы: расстояние
 * определяется числом событий, разброс которого в базе примерно в 513 раз
 * больше разброса второго признака. Точность при этом падает всего с 0.86
 * до 0.80, и в этом вся соль: ошибка не выглядит ошибкой.
 *
 * Слайдер шума разметки показывает, зачем вообще нужен подбор k. На чистой
 * базе k почти не важен, на базе с 20% ошибочных вердиктов разница между
 * k = 1 и k = 25 составляет более двадцати процентных пунктов.
 *
 * Касание карты задаёт новый алерт: подсвечиваются найденные соседи и
 * показываются их вердикты — объяснение через прецеденты, ради которого
 * метод и держат в SOC. Это единственное место темы, где нужен
 * detectTapGestures, а не clickable: требуются координаты касания
 * (см. DEVELOPMENT_NOTES, раздел 2.1).
 */
@Composable
fun KnnInteractive(
    modifier: Modifier = Modifier,
    title: String?,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onOpenChat: (String) -> Unit = {}
) {
    val textColor = Color.White
    val accent = ColorAccent
    val topicTitle = title ?: "Метод ближайших соседей"

    var k by remember { mutableIntStateOf(KnnLab.DEFAULT_K) }
    var metric by remember { mutableStateOf(KnnLab.DEFAULT_METRIC) }
    var weighting by remember { mutableStateOf(KnnLab.DEFAULT_WEIGHTING) }
    var normalize by remember { mutableStateOf(KnnLab.DEFAULT_NORMALIZE) }
    var noise by remember { mutableFloatStateOf(KnnLab.DEFAULT_NOISE.toFloat()) }
    var baseSize by remember { mutableIntStateOf(KnnLab.DEFAULT_BASE_SIZE) }

    var probe by remember { mutableStateOf<DoubleArray?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val base = remember(baseSize, noise) { KnnLab.baseSet(baseSize, noise.toDouble()) }
    val test = remember { KnnLab.testSet() }
    val sc = remember(base, normalize) { KnnLab.scales(base, normalize) }

    var map by remember { mutableStateOf<Array<IntArray>?>(null) }
    var acc by remember { mutableStateOf<Double?>(null) }
    var conf by remember { mutableStateOf<Array<IntArray>?>(null) }
    var computing by remember { mutableStateOf(true) }

    LaunchedEffect(k, metric, weighting, normalize, noise, baseSize) {
        computing = true
        delay(140)
        val result = withContext(Dispatchers.Default) {
            Triple(
                KnnLab.decisionMap(base, k, metric, weighting, sc, steps = 22),
                KnnLab.accuracy(test, base, k, metric, weighting, sc),
                KnnLab.confusion(test, base, k, metric, weighting, sc)
            )
        }
        map = result.first
        acc = result.second
        conf = result.third
        computing = false
    }

    val probeNeighbors = remember(probe, base, k, metric, sc) {
        val p = probe
        if (p != null) KnnLab.neighbors(p, base, k, metric, sc) else null
    }
    val probeVerdict = remember(probe, base, k, metric, weighting, sc) {
        val p = probe
        if (p != null) KnnLab.classify(p, base, k, metric, weighting, sc) else null
    }

    LessonScaffold(
        eyebrow = "Интерактив",
        title = topicTitle,
        onBack = onBack,
        onNext = onNext,
        nextLabel = "К решению →",
        accent = accent,
        modifier = modifier
    ) {
        Text(
            "База из разобранных алертов: по горизонтали число событий (0…500), " +
                "по вертикали доля активности вне рабочего времени (0…1). Фон — карта решений: " +
                "какой вердикт модель предложит в каждой точке. Коснитесь карты, чтобы разобрать " +
                "свой алерт.",
            fontSize = 14.sp, color = textColor.copy(alpha = 0.75f), lineHeight = 20.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(290.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        if (canvasSize.width > 0 && canvasSize.height > 0) {
                            val pad = 14f
                            val w = canvasSize.width - 2 * pad
                            val h = canvasSize.height - 2 * pad
                            val fx = ((offset.x - pad) / w).coerceIn(0f, 1f)
                            val fy = (1f - (offset.y - pad) / h).coerceIn(0f, 1f)
                            probe = doubleArrayOf(fx.toDouble() * KnnLab.EVENTS_MAX, fy.toDouble())
                        }
                    }
                }
        ) {
            val currentMap = map
            if (currentMap != null) {
                DecisionMapCanvas(currentMap, base, probe, probeNeighbors)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            for (c in 0 until KnnLab.CLASS_COUNT) {
                LegendDot(ClassColors[c], KnnLab.classShort[c], textColor)
            }
        }

        if (!normalize) {
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ClassColors[2].copy(alpha = 0.14f))
                    .border(1.dp, ClassColors[2].copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Text(
                    "Карта распалась на вертикальные полосы. Цвет зависит только от числа " +
                        "событий: разброс этого признака в базе примерно в " +
                        "${KnnLab.scaleRatio(base).roundToInt()} раз больше разброса доли " +
                        "нерабочего времени, и в квадрате расстояния второй признак просто тонет. " +
                        "Он не «менее важен» — он не участвует.",
                    color = ClassColors[2], fontSize = 12.sp, lineHeight = 17.sp
                )
            }
        }

        if (probe != null && probeNeighbors != null && probeVerdict != null) {
            Spacer(Modifier.height(12.dp))
            ProbeCard(probe!!, probeNeighbors, probeVerdict, base, k, metric, weighting, sc,
                textColor) { probe = null }
        }

        Spacer(Modifier.height(18.dp))

        // ---------------- Подготовка данных ----------------
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Подготовка данных", color = textColor,
                    fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(10.dp))

                Text("Нормализация признаков", color = textColor, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SegmentButton("Включена", normalize, ClassColors[0], Modifier.weight(1f)) { normalize = true }
                    SegmentButton("Выключена", !normalize, ClassColors[2], Modifier.weight(1f)) { normalize = false }
                }
                Text(
                    if (normalize)
                        "Признаки приведены к одинаковому разбросу — оба реально участвуют в расстоянии."
                    else
                        "Расстояние считается в исходных единицах: сотни событий против долей единицы.",
                    color = textColor.copy(alpha = 0.55f), fontSize = 11.sp, lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )

                SliderRow(
                    label = "Шум разметки в базе",
                    value = "${(noise * 100).roundToInt()}%",
                    hint = if (noise < 0.02f)
                        "все вердикты верны — идеальный, но нереалистичный случай"
                    else "часть алертов разобрана неверно, как в настоящей базе SOC",
                    textColor = textColor
                ) {
                    Slider(
                        value = noise, onValueChange = { noise = it },
                        valueRange = KnnLab.NOISE_MIN.toFloat()..KnnLab.NOISE_MAX.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = ClassColors[2], activeTrackColor = ClassColors[2])
                    )
                }

                SliderRow(
                    label = "Размер базы разобранных алертов",
                    value = "$baseSize",
                    hint = if (baseSize < 40) "прецедентов мало — соседи находятся далеко"
                    else "база достаточная",
                    textColor = textColor
                ) {
                    Slider(
                        value = baseSize.toFloat(), onValueChange = { baseSize = it.roundToInt() },
                        valueRange = KnnLab.BASE_MIN.toFloat()..KnnLab.BASE_MAX.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------------- Правило голосования ----------------
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Правило голосования", color = textColor,
                    fontWeight = FontWeight.SemiBold, fontSize = 16.sp)

                SliderRow(
                    label = "Число соседей k",
                    value = "$k",
                    hint = when {
                        k == 1 -> "решение повторяет ближайший случай, включая ошибочно размеченный"
                        k > 30 -> "голосование сильно сглажено, тонкие различия теряются"
                        else -> "рабочая область"
                    },
                    textColor = textColor
                ) {
                    Slider(
                        value = k.toFloat(), onValueChange = { k = it.roundToInt() },
                        valueRange = KnnLab.K_MIN.toFloat()..KnnLab.K_MAX.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                    )
                }

                Spacer(Modifier.height(10.dp))
                Text("Метрика расстояния", color = textColor, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (mv in KnnMetric.entries) {
                        SegmentButton(mv.label, metric == mv, accent, Modifier.weight(1f)) { metric = mv }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("Вес голоса соседа", color = textColor, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (wv in KnnWeighting.entries) {
                        SegmentButton(wv.label, weighting == wv, accent, Modifier.weight(1f)) { weighting = wv }
                    }
                }
                Text(
                    if (weighting == KnnWeighting.DISTANCE)
                        "Ближний сосед весомее дальнего. На чистой базе помогает, на шумной — " +
                            "усиливает голос ошибочно размеченного соседа."
                    else "Все k соседей голосуют одинаково — устойчивее к ошибкам разметки.",
                    color = textColor.copy(alpha = 0.55f), fontSize = 11.sp, lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------------- Результат ----------------
        val currentAcc = acc
        val currentConf = conf
        if (currentAcc != null && currentConf != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Результат на ${KnnLab.TEST_SIZE} контрольных алертах",
                        color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Точность: ${"%.3f".format(currentAcc)}",
                        color = accent, fontSize = 22.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Матрица ошибок — строка истинный вердикт, столбец предсказанный:",
                        color = textColor.copy(alpha = 0.7f), fontSize = 12.sp, lineHeight = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    for (r in 0 until KnnLab.CLASS_COUNT) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                KnnLab.classShort[r],
                                color = ClassColors[r], fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.width(78.dp)
                            )
                            for (c in 0 until KnnLab.CLASS_COUNT) {
                                Text(
                                    "${currentConf[r][c]}",
                                    color = if (r == c) textColor else textColor.copy(alpha = 0.5f),
                                    fontSize = 14.sp,
                                    fontWeight = if (r == c) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = knnInsight(
                            k = k, normalize = normalize, noise = noise.toDouble(),
                            baseSize = baseSize, weighting = weighting, metric = metric,
                            accuracy = currentAcc, scaleRatio = KnnLab.scaleRatio(base)
                        ),
                        color = textColor.copy(alpha = 0.82f), fontSize = 13.sp, lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    AskChatButton(accent = accent, onClick = {
                        onOpenChat(
                            buildInteractiveChatPrompt(
                                topicTitle,
                                "k = $k, метрика — ${metric.label}, вес голоса — ${weighting.label}, " +
                                    "нормализация ${if (normalize) "включена" else "выключена"}, " +
                                    "шум разметки = ${(noise * 100).roundToInt()}%, " +
                                    "база = $baseSize алертов",
                                "точность на ${KnnLab.TEST_SIZE} контрольных алертах = " +
                                    "${"%.3f".format(currentAcc)}"
                            )
                        )
                    })
                }
            }
        } else {
            Text("Идёт пересчёт карты решений…", color = textColor.copy(alpha = 0.6f), fontSize = 14.sp)
        }

        Spacer(Modifier.height(8.dp))
    }
}

// =====================================================================
// Раскладка
// =====================================================================

@Composable
private fun LegendDot(color: Color, label: String, textColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(10.dp).clip(RoundedCornerShape(3.dp))
                .background(color.copy(alpha = 0.85f))
        )
        Spacer(Modifier.width(6.dp))
        Text(label, color = textColor.copy(alpha = 0.7f), fontSize = 12.sp)
    }
}

@Composable
private fun SegmentButton(
    label: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f))
            .border(
                1.dp,
                if (selected) accent.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.15f),
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) accent else Color.White.copy(alpha = 0.65f),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: String,
    hint: String,
    textColor: Color,
    slider: @Composable () -> Unit
) {
    Spacer(Modifier.height(10.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = textColor, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Text(value, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
    slider()
    Text(hint, color = textColor.copy(alpha = 0.55f), fontSize = 11.sp, lineHeight = 15.sp)
}

@Composable
private fun ProbeCard(
    probe: DoubleArray,
    neighbors: List<KnnLab.Neighbor>,
    verdict: Int,
    base: List<AlertCase>,
    k: Int,
    metric: KnnMetric,
    weighting: KnnWeighting,
    sc: DoubleArray,
    textColor: Color,
    onClear: () -> Unit
) {
    val votes = remember(probe, base, k, metric, weighting, sc) {
        KnnLab.votes(probe, base, k, metric, weighting, sc)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ваш алерт", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Box(
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onClear)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("убрать", color = textColor.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }
            Text(
                "${probe[0].roundToInt()} событий, доля нерабочего времени ${"%.2f".format(probe[1])}",
                color = textColor.copy(alpha = 0.7f), fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Вердикт: ${KnnLab.classNames[verdict]}",
                color = ClassColors[verdict], fontSize = 16.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            Text("Похожие случаи из базы:", color = textColor.copy(alpha = 0.7f), fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            for (nb in neighbors.take(7)) {
                Text(
                    "  расстояние ${"%.3f".format(nb.distance)} → ${KnnLab.classNames[nb.case.label]}",
                    color = ClassColors[nb.case.label].copy(alpha = 0.9f),
                    fontSize = 12.sp, lineHeight = 17.sp
                )
            }
            if (neighbors.size > 7) {
                Text(
                    "  …и ещё ${neighbors.size - 7}",
                    color = textColor.copy(alpha = 0.5f), fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Голоса: " + (0 until KnnLab.CLASS_COUNT).joinToString("   ") {
                    "${KnnLab.classShort[it]} ${"%.2f".format(votes[it])}"
                },
                color = textColor.copy(alpha = 0.75f), fontSize = 12.sp, lineHeight = 17.sp
            )
        }
    }
}

// =====================================================================
// Отрисовка
// =====================================================================

@Composable
private fun DecisionMapCanvas(
    map: Array<IntArray>,
    base: List<AlertCase>,
    probe: DoubleArray?,
    neighbors: List<KnnLab.Neighbor>?
) {
    Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        val w = size.width
        val h = size.height
        val steps = map.size

        fun px(events: Double, offHours: Double) = Offset(
            (events / KnnLab.EVENTS_MAX * w).toFloat(),
            (h - offHours * h).toFloat()
        )

        // карта решений
        val cw = w / steps
        val ch = h / steps
        for (gx in 0 until steps) {
            for (gy in 0 until steps) {
                drawRect(
                    color = ClassColors[map[gx][gy]].copy(alpha = 0.20f),
                    topLeft = Offset(gx * cw, h - (gy + 1) * ch),
                    size = Size(cw + 0.6f, ch + 0.6f)
                )
            }
        }

        // связи от проверяемого алерта к его соседям
        if (probe != null && neighbors != null) {
            val p = px(probe[0], probe[1])
            for (nb in neighbors) {
                drawLine(
                    Color.White.copy(alpha = 0.5f),
                    p,
                    px(nb.case.x[0], nb.case.x[1]),
                    strokeWidth = designPx(1.5f)
                )
            }
        }

        // база алертов
        for (c in base) {
            drawCircle(
                color = ClassColors[c.label],
                radius = designPx(4f),
                center = px(c.x[0], c.x[1])
            )
        }

        // проверяемый алерт
        if (probe != null) {
            val p = px(probe[0], probe[1])
            drawCircle(Color.White, radius = designPx(9f), center = p)
            drawCircle(Color.Black.copy(alpha = 0.7f), radius = designPx(5f), center = p)
        }

        drawLine(Color.White.copy(alpha = 0.3f), Offset(0f, h), Offset(w, h), strokeWidth = designPx(1.5f))
    }
}

// =====================================================================
// Пояснения
// =====================================================================

private fun knnInsight(
    k: Int,
    normalize: Boolean,
    noise: Double,
    baseSize: Int,
    weighting: KnnWeighting,
    metric: KnnMetric,
    accuracy: Double,
    scaleRatio: Double
): String {
    val parts = ArrayList<String>()

    if (!normalize) {
        parts.add(
            "Нормализация выключена. Разброс числа событий в базе примерно в " +
                "${scaleRatio.roundToInt()} раз больше разброса доли нерабочего времени, а в " +
                "квадрат расстояния вклады входят квадратами — то есть отношение вкладов " +
                "порядка ${(scaleRatio * scaleRatio / 1000.0).roundToInt()} тысяч. Второй признак " +
                "не участвует в решении вообще. Точность при этом ${"%.3f".format(accuracy)} — " +
                "достаточно прилично, чтобы такую ошибку никто не заметил в отчёте."
        )
    }

    if (noise > 0.05) {
        if (k <= 3) {
            parts.add(
                "В базе ${(noise * 100).roundToInt()}% ошибочных вердиктов, а k = $k. Каждая " +
                    "неверно размеченная запись создаёт вокруг себя островок неправильных " +
                    "предсказаний — это хорошо видно на карте. Увеличьте k: голос ошибочного " +
                    "соседа растворится среди остальных."
            )
        } else if (k >= 15) {
            parts.add(
                "k = $k при ${(noise * 100).roundToInt()}% шума разметки — голосование достаточно " +
                    "широкое, чтобы ошибочные вердикты оставались в меньшинстве. Именно поэтому " +
                    "на реальных базах k подбирают, а не берут наугад."
            )
        }
        if (weighting == KnnWeighting.DISTANCE) {
            parts.add(
                "Взвешивание по расстоянию на шумной базе обычно вредит: оно усиливает голос " +
                    "ближайшего соседа, и если именно он размечен неверно, усиливается ошибка. " +
                    "Попробуйте переключить на равное — на этих данных результат чаще улучшается."
            )
        }
    } else if (normalize) {
        parts.add(
            "База размечена без ошибок, и в этом случае k почти безразличен: точность держится " +
                "около ${"%.2f".format(accuracy)} на всём диапазоне. Поднимите шум разметки — " +
                "и выбор k сразу станет решающим."
        )
    }

    if (baseSize < 40) {
        parts.add(
            "База всего из $baseSize алертов: соседи находятся далеко, и «похожий случай» " +
                "перестаёт быть похожим. Теоретическая гарантия Кавера и Харта верна для " +
                "бесконечной выборки и на такой базе ничего не обещает."
        )
    }

    if (metric != KnnMetric.EUCLIDEAN) {
        parts.add(
            "Метрика ${metric.label.lowercase()}: на двух признаках разница между метриками " +
                "невелика, и это честный результат. Выбор метрики начинает что-то значить в " +
                "пространствах высокой размерности."
        )
    }

    if (parts.isEmpty()) {
        parts.add("Точность ${"%.3f".format(accuracy)} при k = $k.")
    }
    return parts.joinToString(" ")
}
