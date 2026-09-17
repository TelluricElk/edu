package com.eduappml.ui.dt

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
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

private val ColorEscalate = Color(0xFFFF6B6B)
private val ColorKeep = Color(0xFF6BCB77)
private val ColorAccent = Color(0xFFFFD93D)
private val ColorTrain = Color(0xFF9AA7FF)

/** Всё, что считается в фоне за один заход. */
private class DtComputed(
    val tree: DtTreeNode,
    val trainAcc: Double,
    val testAcc: Double,
    val leaves: Int,
    val realDepth: Int,
    val map: Array<BooleanArray>,
    val curve: DtLab.DepthCurve
)

/**
 * Интерактив темы «Дерево решений»: восстановление плейбука эскалации.
 *
 * Центральный элемент экрана — график двух кривых точности по глубине.
 * Обучающая растёт монотонно, контрольная имеет максимум. Их расхождение и
 * есть содержание темы, поэтому кривые нарисованы на одном поле, а текущая
 * глубина отмечена вертикальной линией.
 *
 * Второй элемент — само дерево, нарисованное целиком с порогами и вердиктами.
 * Это единственная тема приложения, где модель можно просто прочитать.
 *
 * Третий — карта решений: на ней видно, что все границы параллельны осям.
 */
@Composable
fun DtInteractive(
    modifier: Modifier = Modifier,
    title: String?,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onOpenChat: (String) -> Unit = {}
) {
    val textColor = Color.White
    val accent = ColorAccent
    val topicTitle = title ?: "Дерево решений"

    var depth by remember { mutableIntStateOf(DtLab.DEFAULT_DEPTH) }
    var minSplit by remember { mutableIntStateOf(DtLab.DEFAULT_MIN_SPLIT) }
    var noise by remember { mutableFloatStateOf(DtLab.DEFAULT_NOISE.toFloat()) }
    var criterion by remember { mutableStateOf(DtLab.DEFAULT_CRITERION) }

    val train = remember(noise) { DtLab.trainSet(noise.toDouble()) }
    val test = remember(noise) { DtLab.testSet(noise.toDouble()) }

    var computed by remember { mutableStateOf<DtComputed?>(null) }

    LaunchedEffect(depth, minSplit, noise, criterion) {
        delay(150)
        computed = withContext(Dispatchers.Default) {
            val t = DtLab.buildTree(train, criterion, depth, minSplit)
            DtComputed(
                tree = t,
                trainAcc = DtLab.accuracy(t, train),
                testAcc = DtLab.accuracy(t, test),
                leaves = DtLab.leafCount(t),
                realDepth = DtLab.treeDepth(t),
                map = DtLab.decisionMap(t),
                curve = DtLab.depthCurve(train, test, criterion, minSplit)
            )
        }
    }

    val r = computed

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
            "История из ${DtLab.TRAIN_SIZE} разобранных инцидентов, контроль — ${DtLab.TEST_SIZE}. " +
                "По горизонтали число неудачных входов, по вертикали уровень привилегий. " +
                "Задача — восстановить правила эскалации в читаемом виде.",
            fontSize = 14.sp, color = textColor.copy(alpha = 0.75f), lineHeight = 20.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // ---------------- Главный график: две кривые точности ----------------
        Text(
            "Точность по глубине: обучающая и контрольная",
            color = textColor.copy(alpha = 0.9f), fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp)
        )
        ChartBox(height = 200.dp) {
            if (r != null) DepthCurveCanvas(r.curve, depth)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LegendDot(ColorTrain, "обучающая", textColor)
            LegendDot(ColorAccent, "контрольная", textColor)
        }
        Text(
            "Обучающая точность растёт монотонно — она росла бы так на любых данных, " +
                "даже полностью случайных. Контрольная имеет максимум. Расстояние между " +
                "кривыми и есть переобучение.",
            fontSize = 12.sp, color = textColor.copy(alpha = 0.6f), lineHeight = 17.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
        )

        // ---------------- Дерево ----------------
        Text(
            "Дерево целиком",
            color = textColor.copy(alpha = 0.9f), fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp)
        )
        ChartBox(height = 240.dp) {
            if (r != null) TreeCanvas(r.tree)
        }
        Text(
            if (r != null && r.realDepth > 4)
                "Показаны первые четыре уровня: глубже дерево уже не помещается на экран — " +
                    "и это само по себе сигнал, что правила перестали быть читаемыми."
            else
                "Каждый путь от корня до листа — готовое правило плейбука. Числа в листьях — " +
                    "сколько записей истории туда попало.",
            fontSize = 12.sp, color = textColor.copy(alpha = 0.6f), lineHeight = 17.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
        )

        // ---------------- Карта решений ----------------
        Text(
            "Карта решений",
            color = textColor.copy(alpha = 0.9f), fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp)
        )
        ChartBox(height = 230.dp) {
            if (r != null) DecisionMapCanvas(r.map, train)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LegendDot(ColorKeep, "не эскалировать", textColor)
            LegendDot(ColorEscalate, "эскалировать", textColor)
        }
        Text(
            "Все границы параллельны осям: каждый узел сравнивает один признак с одним " +
                "числом. Диагональ дерево изображало бы лесенкой.",
            fontSize = 12.sp, color = textColor.copy(alpha = 0.6f), lineHeight = 17.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
        )

        // ---------------- Ограничители роста ----------------
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Ограничители роста", color = textColor,
                    fontWeight = FontWeight.SemiBold, fontSize = 16.sp)

                SliderRow(
                    label = "Предельная глубина",
                    value = "$depth",
                    hint = when {
                        depth <= 2 -> "дерево слишком грубое, правил почти нет"
                        depth >= 8 -> "дерево заучивает историю вместе с ошибками аналитиков"
                        else -> "рабочая область"
                    },
                    textColor = textColor
                ) {
                    Slider(
                        value = depth.toFloat(), onValueChange = { depth = it.roundToInt() },
                        valueRange = DtLab.DEPTH_MIN.toFloat()..DtLab.DEPTH_MAX.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                    )
                }

                SliderRow(
                    label = "Минимум записей для разбиения",
                    value = "$minSplit",
                    hint = if (minSplit <= 4)
                        "узлы дробятся до последнего — ветви обслуживают единичные записи"
                    else "мелкие ветви не создаются, дерево остаётся обозримым",
                    textColor = textColor
                ) {
                    Slider(
                        value = minSplit.toFloat(), onValueChange = { minSplit = it.roundToInt() },
                        valueRange = DtLab.MIN_SPLIT_MIN.toFloat()..DtLab.MIN_SPLIT_MAX.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
                    )
                }

                SliderRow(
                    label = "Шум разметки: аналитик ошибся",
                    value = "${(noise * 100).roundToInt()}%",
                    hint = if (noise < 0.02f)
                        "все вердикты верны — дерево восстановит политику точно"
                    else "часть инцидентов разобрана неверно, как в настоящей базе",
                    textColor = textColor
                ) {
                    Slider(
                        value = noise, onValueChange = { noise = it },
                        valueRange = DtLab.NOISE_MIN.toFloat()..DtLab.NOISE_MAX.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = ColorEscalate, activeTrackColor = ColorEscalate)
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text("Критерий неоднородности", color = textColor, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (cv in DtCriterion.entries) {
                        SegmentButton(cv.label, criterion == cv, accent, Modifier.weight(1f)) { criterion = cv }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------------- Результат ----------------
        if (r != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Что получилось", color = textColor,
                        fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BigStat("Обучающая — растёт всегда", "%.3f".format(r.trainAcc),
                            ColorTrain, Modifier.weight(1f), textColor)
                        BigStat("Контрольная — по ней и судят", "%.3f".format(r.testAcc),
                            ColorAccent, Modifier.weight(1f), textColor)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Листьев: ${r.leaves}   •   фактическая глубина: ${r.realDepth}   •   " +
                            "разрыв: ${"%.3f".format(r.trainAcc - r.testAcc)}",
                        color = textColor.copy(alpha = 0.8f), fontSize = 12.sp, lineHeight = 17.sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = dtInsight(
                            depth = depth, minSplit = minSplit, noise = noise.toDouble(),
                            criterion = criterion, trainAcc = r.trainAcc, testAcc = r.testAcc,
                            leaves = r.leaves, curve = r.curve
                        ),
                        color = textColor.copy(alpha = 0.82f), fontSize = 13.sp, lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    AskChatButton(accent = accent, onClick = {
                        onOpenChat(
                            buildInteractiveChatPrompt(
                                topicTitle,
                                "предельная глубина = $depth, минимум записей для разбиения = $minSplit, " +
                                    "критерий — ${criterion.label}, " +
                                    "шум разметки = ${(noise * 100).roundToInt()}%",
                                "точность на обучающей = ${"%.3f".format(r.trainAcc)}, " +
                                    "на контрольной = ${"%.3f".format(r.testAcc)}, " +
                                    "листьев = ${r.leaves}, фактическая глубина = ${r.realDepth}"
                            )
                        )
                    })
                }
            }
        } else {
            Text("Строится дерево…", color = textColor.copy(alpha = 0.6f), fontSize = 14.sp)
        }

        Spacer(Modifier.height(8.dp))
    }
}

// =====================================================================
// Раскладка
// =====================================================================

@Composable
private fun ChartBox(height: androidx.compose.ui.unit.Dp, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp)),
        content = content
    )
}

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
            fontSize = 13.sp,
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
private fun BigStat(label: String, value: String, color: Color, modifier: Modifier, textColor: Color) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(label, color = textColor.copy(alpha = 0.7f), fontSize = 11.sp, lineHeight = 15.sp)
    }
}

// =====================================================================
// Отрисовка
// =====================================================================

@Composable
private fun DepthCurveCanvas(curve: DtLab.DepthCurve, currentDepth: Int) {
    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val w = size.width
        val h = size.height
        val n = curve.depths.size
        if (n < 2) return@Canvas

        // вертикальная ось от 0.5 до 1.0 — вся интересная область
        val lo = 0.5
        val hi = 1.0
        fun px(i: Int, v: Double) = Offset(
            i.toFloat() / (n - 1) * w,
            (h - ((v - lo) / (hi - lo)).coerceIn(0.0, 1.0) * h).toFloat()
        )

        // сетка
        for (k in 1 until 5) {
            val gy = h * k / 5f
            drawLine(Color.White.copy(alpha = 0.07f), Offset(0f, gy), Offset(w, gy), strokeWidth = designPx(1f))
        }

        // отметка текущей глубины
        val idx = (currentDepth - curve.depths[0]).coerceIn(0, n - 1)
        val markX = idx.toFloat() / (n - 1) * w
        drawLine(
            Color.White.copy(alpha = 0.45f),
            Offset(markX, 0f), Offset(markX, h),
            strokeWidth = designPx(2.5f)
        )

        fun drawSeries(values: DoubleArray, color: Color) {
            var prev: Offset? = null
            for (i in 0 until n) {
                val pt = px(i, values[i])
                prev?.let { drawLine(color, it, pt, strokeWidth = designPx(2.5f)) }
                prev = pt
            }
            for (i in 0 until n) {
                drawCircle(color, radius = designPx(3f), center = px(i, values[i]))
            }
        }

        drawSeries(curve.train, ColorTrain)
        drawSeries(curve.test, ColorAccent)

        drawLine(Color.White.copy(alpha = 0.3f), Offset(0f, h), Offset(w, h), strokeWidth = designPx(1.5f))
    }
}

@Composable
private fun TreeCanvas(root: DtTreeNode) {
    Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        val w = size.width
        val h = size.height
        val maxLevels = 4      // глубже на экран не помещается

        fun draw(node: DtTreeNode, level: Int, x0: Float, x1: Float) {
            if (level > maxLevels) return
            val cx = (x0 + x1) / 2f
            val cy = h * (level + 0.5f) / (maxLevels + 1f)
            val r = designPx(if (node.isLeaf) 7f else 6f)

            if (!node.isLeaf && level < maxLevels) {
                val childY = h * (level + 1.5f) / (maxLevels + 1f)
                val leftX = (x0 + cx) / 2f
                val rightX = (cx + x1) / 2f
                drawLine(Color.White.copy(alpha = 0.3f), Offset(cx, cy), Offset(leftX, childY),
                    strokeWidth = designPx(1.5f))
                drawLine(Color.White.copy(alpha = 0.3f), Offset(cx, cy), Offset(rightX, childY),
                    strokeWidth = designPx(1.5f))
                draw(node.left!!, level + 1, x0, cx)
                draw(node.right!!, level + 1, cx, x1)
            }

            val color = when {
                node.isLeaf && node.prediction == true -> ColorEscalate
                node.isLeaf -> ColorKeep
                else -> ColorAccent
            }
            drawCircle(color, radius = r, center = Offset(cx, cy))
            if (!node.isLeaf && level == maxLevels) {
                // ветвь оборвана — показываем это явно
                drawCircle(Color.White.copy(alpha = 0.5f), radius = designPx(3f), center = Offset(cx, cy))
            }
        }

        draw(root, 0, 0f, w)
    }
}

@Composable
private fun DecisionMapCanvas(map: Array<BooleanArray>, train: List<IncidentCase>) {
    Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        val w = size.width
        val h = size.height
        val steps = map.size
        val cw = w / steps
        val ch = h / steps

        for (gx in 0 until steps) {
            for (gy in 0 until steps) {
                drawRect(
                    color = (if (map[gx][gy]) ColorEscalate else ColorKeep).copy(alpha = 0.20f),
                    topLeft = Offset(gx * cw, h - (gy + 1) * ch),
                    size = Size(cw + 0.6f, ch + 0.6f)
                )
            }
        }

        for (c in train) {
            val px = (c.x[0] / DtLab.LOGINS_MAX * w).toFloat()
            val py = (h - c.x[1] * h).toFloat()
            drawCircle(
                color = if (c.escalate) ColorEscalate else ColorKeep,
                radius = designPx(3.5f),
                center = Offset(px, py)
            )
        }

        drawLine(Color.White.copy(alpha = 0.3f), Offset(0f, h), Offset(w, h), strokeWidth = designPx(1.5f))
    }
}

// =====================================================================
// Пояснения
// =====================================================================

private fun dtInsight(
    depth: Int,
    minSplit: Int,
    noise: Double,
    criterion: DtCriterion,
    trainAcc: Double,
    testAcc: Double,
    leaves: Int,
    curve: DtLab.DepthCurve
): String {
    val parts = ArrayList<String>()

    // где максимум контрольной кривой
    var bestIdx = 0
    for (i in curve.test.indices) if (curve.test[i] > curve.test[bestIdx]) bestIdx = i
    val bestDepth = curve.depths[bestIdx]

    if (noise < 0.02) {
        parts.add(
            "Разметка чистая, и дерево восстанавливает исходную политику эскалации почти " +
                "дословно: посмотрите на пороги в узлах и сравните их с правилами из «Эталонной " +
                "задачи». Листьев при этом всего $leaves — правил ровно столько, сколько было " +
                "в настоящем плейбуке."
        )
    } else {
        parts.add(
            "В истории ${(noise * 100).roundToInt()}% инцидентов разобраны неверно. " +
                "Максимум контрольной точности приходится на глубину $bestDepth " +
                "(${"%.3f".format(curve.test[bestIdx])}), сейчас выбрана $depth."
        )
    }

    if (trainAcc - testAcc > 0.12) {
        parts.add(
            "Разрыв между кривыми ${"%.3f".format(trainAcc - testAcc)} — дерево заучило историю " +
                "вместе с ошибками аналитиков. Обучающая точность ${"%.3f".format(trainAcc)} здесь " +
                "ни о чём не говорит: она росла бы так же и на случайных данных."
        )
    }

    if (depth >= 8 && minSplit <= 4) {
        parts.add(
            "Попробуйте не уменьшать глубину, а поднять минимум записей для разбиения. " +
                "Это второй ограничитель, и он режет именно те ветви, которые обслуживают " +
                "единичные наблюдения, а крупные оставляет расти."
        )
    }

    if (minSplit >= 16) {
        parts.add(
            "Минимум $minSplit записей на разбиение — дерево осталось обозримым ($leaves листьев) " +
                "при предельной глубине $depth. Это тот же эффект, что от снижения глубины, " +
                "но достигнутый аккуратнее."
        )
    }

    if (criterion == DtCriterion.ENTROPY) {
        parts.add(
            "Критерий энтропии даёт дерево, похожее на построенное по Джини, но не идентичное. " +
                "Энтропия чуть сильнее наказывает узлы со слабым преобладанием одного класса; " +
                "разница между критериями обычно невелика, и это честный результат."
        )
    }

    if (leaves <= 3) {
        parts.add(
            "Всего $leaves листа — дерево слишком грубое, чтобы выразить политику из трёх " +
                "условий. Добавьте глубины."
        )
    }

    return parts.joinToString(" ")
}
