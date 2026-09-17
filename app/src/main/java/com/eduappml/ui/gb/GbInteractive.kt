package com.eduappml.ui.gb

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
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

private val ColorModel = Color(0xFFFFD93D)
private val ColorTruth = Color(0xFF6BCB77)
private val ColorPoint = Color(0xFF9AA7FF)
private val ColorTest = Color(0xFFFF6B6B)
private val ColorLine = Color(0xFFB0B6C4)

/** Всё, что считается в фоне за один заход. */
private class GbComputed(
    val model: GbModel,
    val trainMse: Double,
    val testMse: Double,
    val bestIter: Int,
    val bestMse: Double,
    val curve: DoubleArray,
    val truth: DoubleArray,
    val residuals: DoubleArray,
    val baseline: Double,
    val line: GbLab.Line,
    val firstThreshold: Double,
    val jumpModel: Double,
    val jumpLine: Double
)

private fun sliderToLr(t: Float): Double {
    val lo = log10(GbLab.LR_MIN)
    return 10.0.pow(lo + t.toDouble() * (log10(GbLab.LR_MAX) - lo))
}

private fun lrToSlider(lr: Double): Float {
    val lo = log10(GbLab.LR_MIN)
    return ((log10(lr) - lo) / (log10(GbLab.LR_MAX) - lo)).toFloat()
}

/**
 * Интерактив темы «Градиентный бустинг»: прогноз ущерба по времени до
 * обнаружения инцидента.
 *
 * Первый холст — данные, истинная зависимость пунктиром и ступенчатая
 * сумма деревьев. По ней видно, как приближение уточняется: при одной
 * итерации это почти прямая линия, при шестидесяти — узнаваемая кривая
 * с разрывом на 60 днях.
 *
 * Второй холст — остатки после текущей итерации. Это и есть то, на чём
 * будет учиться следующее дерево; когда остатки перестают иметь структуру,
 * добавлять деревья бессмысленно.
 *
 * Третий — кривые ошибки по итерациям в логарифмическом масштабе с
 * отметкой минимума контрольной ошибки.
 */
@Composable
fun GbInteractive(
    modifier: Modifier = Modifier,
    title: String?,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onOpenChat: (String) -> Unit = {}
) {
    val textColor = Color.White
    val accent = ColorModel
    val topicTitle = title ?: "Градиентный бустинг"

    var nEstimators by remember { mutableIntStateOf(GbLab.DEFAULT_ESTIMATORS) }
    var lrSlider by remember { mutableFloatStateOf(lrToSlider(GbLab.DEFAULT_LEARNING_RATE)) }
    var depth by remember { mutableIntStateOf(GbLab.DEFAULT_DEPTH) }
    var minLeaf by remember { mutableIntStateOf(GbLab.DEFAULT_MIN_LEAF) }
    var noise by remember { mutableFloatStateOf(GbLab.DEFAULT_NOISE.toFloat()) }

    val learningRate = sliderToLr(lrSlider)
    val train = remember(noise) { GbLab.trainSet(noise.toDouble()) }
    val test = remember(noise) { GbLab.testSet(noise.toDouble()) }

    var computed by remember { mutableStateOf<GbComputed?>(null) }

    LaunchedEffect(nEstimators, lrSlider, depth, minLeaf, noise) {
        delay(150)
        computed = withContext(Dispatchers.Default) {
            val m = GbLab.train(train, test, nEstimators, learningRate, depth, minLeaf)
            val best = GbLab.bestIteration(m)
            val line = GbLab.fitLine(train, test)
            GbComputed(
                model = m,
                trainMse = m.trainMse[m.trainMse.size - 1],
                testMse = m.testMse[m.testMse.size - 1],
                bestIter = best,
                bestMse = m.testMse[best],
                curve = GbLab.curve(m, nEstimators),
                truth = GbLab.trueCurve(),
                residuals = GbLab.residuals(m, train, nEstimators),
                baseline = GbLab.baselineMse(train, test),
                line = line,
                firstThreshold = if (m.trees.isEmpty() || m.trees[0].isLeaf) -1.0
                    else m.trees[0].threshold,
                jumpModel = GbLab.predict(m, 65.0) - GbLab.predict(m, 55.0),
                jumpLine = line.slope * 10.0
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
            "${GbLab.TRAIN_SIZE} разобранных инцидентов, контроль — ${GbLab.TEST_SIZE}. " +
                "По горизонтали время до обнаружения в днях, по вертикали ущерб в миллионах. " +
                "Задача — восстановить зависимость, в которой есть разрыв: на шестидесятом дне " +
                "срабатывает шифровальная нагрузка.",
            fontSize = 14.sp, color = textColor.copy(alpha = 0.75f), lineHeight = 20.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // ---------------- Главный холст ----------------
        Text(
            "Сумма деревьев против истинной зависимости",
            color = textColor.copy(alpha = 0.9f), fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp)
        )
        ChartBox(height = 230.dp) {
            if (r != null) FitCanvas(r, train)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LegendDot(ColorPoint, "инциденты", textColor)
            LegendDot(ColorTruth, "истина", textColor)
            LegendDot(ColorModel, "модель", textColor)
            LegendDot(ColorLine, "прямая", textColor)
        }
        Text(
            "Серая прямая — то лучшее, что может дать линейная регрессия из предыдущей темы. " +
                "Разрыв она обязана пройти насквозь: прямая по определению не умеет " +
                "скачков. Ступенчатая сумма деревьев умеет.",
            fontSize = 12.sp, color = textColor.copy(alpha = 0.6f), lineHeight = 17.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
        )

        // ---------------- Остатки ----------------
        Text(
            "Остатки — то, на чём будет учиться следующее дерево",
            color = textColor.copy(alpha = 0.9f), fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp)
        )
        ChartBox(height = 180.dp) {
            if (r != null) ResidualCanvas(r.residuals, train)
        }
        Text(
            "Каждая следующая итерация видит не исходные данные, а вот эту картинку. " +
                "Пока в остатках осталась структура, новое дерево найдёт, за что зацепиться. " +
                "Когда они превратятся в бесформенное облако вокруг нуля, " +
                "алгоритм начнёт заучивать шум.",
            fontSize = 12.sp, color = textColor.copy(alpha = 0.6f), lineHeight = 17.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
        )

        // ---------------- Кривые ошибки ----------------
        Text(
            "Ошибка по итерациям, логарифмический масштаб",
            color = textColor.copy(alpha = 0.9f), fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp)
        )
        ChartBox(height = 190.dp) {
            if (r != null) MseCanvas(r.model, r.bestIter)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LegendDot(ColorPoint, "обучающая", textColor)
            LegendDot(ColorTest, "контрольная", textColor)
        }
        Text(
            "Обучающая ошибка падает всегда — бустинг по построению уменьшает её на каждом " +
                "шаге. Контрольная имеет минимум. Белая черта отмечает, где он находится " +
                "при текущих настройках.",
            fontSize = 12.sp, color = textColor.copy(alpha = 0.6f), lineHeight = 17.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
        )

        // ---------------- Размен ----------------
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Главный размен темы", color = textColor,
                    fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Шаг и число итераций компенсируют друг друга. Уменьшите шаг вдвое — " +
                        "и минимум уедет вправо примерно вдвое.",
                    color = textColor.copy(alpha = 0.6f), fontSize = 12.sp, lineHeight = 17.sp
                )

                SliderRow(
                    label = "Скорость обучения",
                    value = "%.3f".format(learningRate),
                    hint = when {
                        learningRate > 0.6 -> "крупный шаг: быстро, но грубо и с риском проскочить"
                        learningRate < 0.04 -> "шаг так мал, что за отведённые итерации не дойти"
                        else -> "рабочая область"
                    },
                    textColor = textColor
                ) {
                    Slider(
                        value = lrSlider, onValueChange = { lrSlider = it },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                    )
                }

                SliderRow(
                    label = "Итераций (деревьев)",
                    value = "$nEstimators",
                    hint = if (r != null && nEstimators > r.bestIter + 1)
                        "минимум контрольной ошибки был на ${r.bestIter + 1}-й итерации"
                    else "минимум ещё впереди",
                    textColor = textColor
                ) {
                    Slider(
                        value = nEstimators.toFloat(), onValueChange = { nEstimators = it.roundToInt() },
                        valueRange = GbLab.ESTIMATORS_MIN.toFloat()..GbLab.ESTIMATORS_MAX.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = ColorTest, activeTrackColor = ColorTest)
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text("Глубина каждого дерева", color = textColor, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (d in GbLab.DEPTH_MIN..GbLab.DEPTH_MAX) {
                        SegmentButton(
                            if (d == 1) "пень" else "$d",
                            depth == d, accent, Modifier.weight(1f)
                        ) { depth = d }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (depth == 1)
                        "Пень делает ровно один разрез. Признак здесь один, и большего не нужно."
                    else
                        "Глубина $depth даёт до ${1 shl depth} листьев за итерацию. При одном признаке " +
                            "это просто ускоряет заучивание: минимум наступает раньше, а дальше " +
                            "контрольная ошибка идёт вверх.",
                    color = textColor.copy(alpha = 0.55f), fontSize = 11.sp, lineHeight = 15.sp
                )

                SliderRow(
                    label = "Минимум инцидентов в листе",
                    value = "$minLeaf",
                    hint = if (minLeaf <= 3)
                        "листья могут обслуживать единичные инциденты"
                    else "деревья огрублены, заучивать отдельные точки им нечем",
                    textColor = textColor
                ) {
                    Slider(
                        value = minLeaf.toFloat(), onValueChange = { minLeaf = it.roundToInt() },
                        valueRange = GbLab.MIN_LEAF_MIN.toFloat()..GbLab.MIN_LEAF_MAX.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
                    )
                }

                SliderRow(
                    label = "Разброс оценки ущерба",
                    value = "±%.1f млн".format(noise),
                    hint = if (noise < 0.2f)
                        "оценки точны — видно, как модель ложится на истинную кривую"
                    else "ущерб оценивают приблизительно, как и бывает",
                    textColor = textColor
                ) {
                    Slider(
                        value = noise, onValueChange = { noise = it },
                        valueRange = GbLab.NOISE_MIN.toFloat()..GbLab.NOISE_MAX.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = ColorTruth, activeTrackColor = ColorTruth)
                    )
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
                        BigStat("Обучающая ошибка", "%.3f".format(r.trainMse),
                            ColorPoint, Modifier.weight(1f), textColor)
                        BigStat("Контрольная ошибка", "%.3f".format(r.testMse),
                            ColorTest, Modifier.weight(1f), textColor)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BigStat("Прямая на тех же данных", "%.3f".format(r.line.testMse),
                            ColorLine, Modifier.weight(1f), textColor)
                        BigStat("Скачок на 60 днях", "%.2f".format(r.jumpModel),
                            ColorModel, Modifier.weight(1f), textColor)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Модель-константа даёт ${"%.3f".format(r.baseline)}   •   " +
                            "лучшая итерация ${r.bestIter + 1} с ошибкой ${"%.3f".format(r.bestMse)}" +
                            (if (r.firstThreshold >= 0)
                                "   •   порог первого дерева ${"%.1f".format(r.firstThreshold)} дня"
                             else ""),
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
                        text = gbInsight(
                            nEstimators = nEstimators, learningRate = learningRate, depth = depth,
                            minLeaf = minLeaf, noise = noise.toDouble(), r = r
                        ),
                        color = textColor.copy(alpha = 0.82f), fontSize = 13.sp, lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    AskChatButton(accent = accent, onClick = {
                        onOpenChat(
                            buildInteractiveChatPrompt(
                                topicTitle,
                                "итераций = $nEstimators, скорость обучения = " +
                                    "${"%.3f".format(learningRate)}, глубина дерева = $depth, " +
                                    "минимум инцидентов в листе = $minLeaf, " +
                                    "разброс оценки ущерба = ±${"%.1f".format(noise)} млн",
                                "обучающая ошибка = ${"%.3f".format(r.trainMse)}, " +
                                    "контрольная = ${"%.3f".format(r.testMse)}, " +
                                    "минимум контрольной ${"%.3f".format(r.bestMse)} на итерации " +
                                    "${r.bestIter + 1}, прямая на тех же данных даёт " +
                                    "${"%.3f".format(r.line.testMse)}"
                            )
                        )
                    })
                }
            }
        } else {
            Text("Строим деревья…", color = textColor.copy(alpha = 0.6f), fontSize = 14.sp)
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
private fun FitCanvas(r: GbComputed, train: List<DamageCase>) {
    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val w = size.width
        val h = size.height

        var lo = Double.MAX_VALUE
        var hi = -Double.MAX_VALUE
        for (c in train) { if (c.damage < lo) lo = c.damage; if (c.damage > hi) hi = c.damage }
        for (v in r.truth) { if (v < lo) lo = v; if (v > hi) hi = v }
        for (v in r.curve) { if (v < lo) lo = v; if (v > hi) hi = v }
        val pad = (hi - lo) * 0.08
        lo -= pad; hi += pad
        if (hi - lo < 1e-9) hi = lo + 1.0

        fun yOf(v: Double) = (h - ((v - lo) / (hi - lo)).coerceIn(0.0, 1.0) * h).toFloat()
        fun xOf(d: Double) = (d / GbLab.DWELL_MAX * w).toFloat()

        for (k in 1 until 5) {
            val gy = h * k / 5f
            drawLine(Color.White.copy(alpha = 0.07f), Offset(0f, gy), Offset(w, gy), strokeWidth = designPx(1f))
        }

        // отметка настоящего разрыва
        val sx = xOf(GbLab.STEP_DAY)
        drawLine(Color.White.copy(alpha = 0.25f), Offset(sx, 0f), Offset(sx, h), strokeWidth = designPx(1.5f))

        fun drawSeries(values: DoubleArray, color: Color, width: Float) {
            var prev: Offset? = null
            for (i in values.indices) {
                val pt = Offset((i + 0.5f) / values.size * w, yOf(values[i]))
                prev?.let { drawLine(color, it, pt, strokeWidth = designPx(width)) }
                prev = pt
            }
        }

        // прямая наименьших квадратов
        drawLine(
            ColorLine.copy(alpha = 0.7f),
            Offset(0f, yOf(r.line.intercept)),
            Offset(w, yOf(r.line.slope * GbLab.DWELL_MAX + r.line.intercept)),
            strokeWidth = designPx(2f)
        )

        drawSeries(r.truth, ColorTruth.copy(alpha = 0.75f), 2f)
        drawSeries(r.curve, ColorModel, 2.8f)

        for (c in train) {
            drawCircle(ColorPoint.copy(alpha = 0.85f), radius = designPx(3.5f),
                center = Offset(xOf(c.dwell), yOf(c.damage)))
        }

        drawLine(Color.White.copy(alpha = 0.3f), Offset(0f, h), Offset(w, h), strokeWidth = designPx(1.5f))
    }
}

@Composable
private fun ResidualCanvas(residuals: DoubleArray, train: List<DamageCase>) {
    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val w = size.width
        val h = size.height
        var m = 1e-9
        for (v in residuals) if (abs(v) > m) m = abs(v)
        m *= 1.15

        fun yOf(v: Double) = (h / 2 - (v / m).coerceIn(-1.0, 1.0) * h / 2).toFloat()
        fun xOf(d: Double) = (d / GbLab.DWELL_MAX * w).toFloat()

        val sx = xOf(GbLab.STEP_DAY)
        drawLine(Color.White.copy(alpha = 0.25f), Offset(sx, 0f), Offset(sx, h), strokeWidth = designPx(1.5f))

        drawLine(Color.White.copy(alpha = 0.45f), Offset(0f, h / 2), Offset(w, h / 2),
            strokeWidth = designPx(2f))

        for (i in residuals.indices) {
            val x = xOf(train[i].dwell)
            val y = yOf(residuals[i])
            drawLine(
                (if (residuals[i] >= 0) ColorModel else ColorTest).copy(alpha = 0.45f),
                Offset(x, h / 2), Offset(x, y), strokeWidth = designPx(1.5f)
            )
            drawCircle(
                if (residuals[i] >= 0) ColorModel else ColorTest,
                radius = designPx(3.5f), center = Offset(x, y)
            )
        }
    }
}

@Composable
private fun MseCanvas(model: GbModel, bestIter: Int) {
    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val w = size.width
        val h = size.height
        val n = model.trainMse.size
        if (n < 1) return@Canvas

        var lo = Double.MAX_VALUE
        var hi = -Double.MAX_VALUE
        for (v in model.trainMse) { if (v > 0 && v < lo) lo = v; if (v > hi) hi = v }
        for (v in model.testMse) { if (v > 0 && v < lo) lo = v; if (v > hi) hi = v }
        if (lo == Double.MAX_VALUE) lo = 0.001
        if (hi <= lo) hi = lo * 10
        val loL = log10(lo) - 0.05
        val hiL = log10(hi) + 0.05

        fun yOf(v: Double): Float {
            val t = if (v <= 0) loL else log10(v)
            return (h - ((t - loL) / (hiL - loL)).coerceIn(0.0, 1.0) * h).toFloat()
        }
        fun xOf(i: Int) = if (n == 1) w / 2f else i.toFloat() / (n - 1) * w

        for (k in 1 until 5) {
            val gy = h * k / 5f
            drawLine(Color.White.copy(alpha = 0.07f), Offset(0f, gy), Offset(w, gy), strokeWidth = designPx(1f))
        }

        if (n > 1) {
            val bx = xOf(bestIter)
            drawLine(Color.White.copy(alpha = 0.5f), Offset(bx, 0f), Offset(bx, h),
                strokeWidth = designPx(2f))
        }

        fun drawSeries(values: DoubleArray, color: Color) {
            var prev: Offset? = null
            for (i in 0 until n) {
                val pt = Offset(xOf(i), yOf(values[i]))
                prev?.let { drawLine(color, it, pt, strokeWidth = designPx(2.5f)) }
                prev = pt
            }
            drawCircle(color, radius = designPx(4f), center = Offset(xOf(n - 1), yOf(values[n - 1])))
        }

        drawSeries(model.trainMse, ColorPoint)
        drawSeries(model.testMse, ColorTest)

        drawLine(Color.White.copy(alpha = 0.3f), Offset(0f, h), Offset(w, h), strokeWidth = designPx(1.5f))
    }
}

// =====================================================================
// Пояснения
// =====================================================================

private fun gbInsight(
    nEstimators: Int,
    learningRate: Double,
    depth: Int,
    minLeaf: Int,
    noise: Double,
    r: GbComputed
): String {
    val parts = ArrayList<String>()

    if (r.firstThreshold >= 0) {
        parts.add(
            "Порог первого дерева — ${"%.1f".format(r.firstThreshold)} дня. Настоящий разрыв " +
                "стоит на 60 днях, и алгоритму об этом не говорили: он нашёл его сам, потому " +
                "что именно там остатки константной модели меняют знак сильнее всего."
        )
    }

    parts.add(
        "Модель даёт скачок ${"%.2f".format(r.jumpModel)} между 55 и 65 днями, прямая — " +
            "${"%.2f".format(r.jumpLine)}. Контрольная ошибка ${"%.3f".format(r.testMse)} против " +
            "${"%.3f".format(r.line.testMse)} у прямой и ${"%.3f".format(r.baseline)} у константы."
    )

    if (nEstimators > r.bestIter + 8) {
        parts.add(
            "Минимум контрольной ошибки был на итерации ${r.bestIter + 1} " +
                "(${"%.3f".format(r.bestMse)}), сейчас выбрано $nEstimators и ошибка " +
                "${"%.3f".format(r.testMse)}. Обучающая при этом продолжает падать: бустинг " +
                "уменьшает её на каждом шаге по построению, и остановиться сам он не может. " +
                "Число итераций здесь — такой же параметр регуляризации, как глубина у дерева."
        )
    } else if (nEstimators < r.bestIter + 1) {
        parts.add(
            "Итераций пока меньше, чем нужно: минимум контрольной ошибки будет на " +
                "${r.bestIter + 1}-й. Добавьте деревьев или увеличьте шаг."
        )
    }

    if (learningRate > 0.6) {
        parts.add(
            "Шаг ${"%.2f".format(learningRate)} крупный: модель добирается до приличного " +
                "качества за считаные итерации, но проскакивает мимо лучшего решения и потом " +
                "начинает раскачивать остатки. Уменьшите шаг вдвое и увеличьте число итераций " +
                "вдвое — качество, как правило, окажется лучше при тех же затратах."
        )
    } else if (learningRate < 0.04) {
        parts.add(
            "Шаг ${"%.3f".format(learningRate)} очень мелкий. Каждое дерево вносит настолько " +
                "малую поправку, что за ${GbLab.ESTIMATORS_MAX} итераций модель может просто " +
                "не дойти до цели — это недообучение, а не осторожность."
        )
    }

    if (depth > 1) {
        parts.add(
            "Глубина $depth при одном признаке — почти всегда лишняя. Смысл глубины в " +
                "бустинге — дать дереву выразить взаимодействие нескольких признаков; здесь " +
                "признак один, и лишние разрезы только ускоряют заучивание: минимум наступает " +
                "раньше, а после него контрольная ошибка растёт круче."
        )
    }

    if (minLeaf >= 8) {
        parts.add(
            "Минимум $minLeaf инцидентов в листе сильно огрубляет деревья. Разрыв на 60 днях " +
                "они ещё поймают, а тонкую кривизну в первые недели уже нет."
        )
    }

    if (noise < 0.2) {
        parts.add(
            "Разброс оценок убран — видно, как ступенчатая сумма ложится на истинную кривую, " +
                "и что ступеньки становятся мельче там, где кривая круче."
        )
    } else if (noise > 3.0) {
        parts.add(
            "При разбросе ±${"%.1f".format(noise)} млн шум сопоставим с самим разрывом, и " +
                "минимум контрольной ошибки смещается влево: модели стоит остановиться раньше, " +
                "пока она не начала объяснять погрешность оценщика."
        )
    }

    return parts.joinToString(" ")
}
