package com.eduappml.ui.lr

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
import kotlin.math.ln
import kotlin.math.roundToInt

private val ColorLine = Color(0xFFFFD93D)
private val ColorPoint = Color(0xFF6BCB77)
private val ColorOutlier = Color(0xFFFF6B6B)
private val ColorTrue = Color(0xFF9AA7FF)

/**
 * Интерактив темы «Линейная регрессия»: прогноз мощности DDoS-атаки по
 * размеру ботнета.
 *
 * Экран построен вокруг двух сюжетов, которые проверяются двумя разными
 * элементами управления.
 *
 * Переключатель нормализации — про обусловленность задачи. Выключив его и
 * оставив ту же скорость обучения, пользователь получает разлетевшуюся
 * модель; чтобы вернуть её к жизни, скорость приходится уменьшать в тысячи
 * раз. Слайдер скорости логарифмический, иначе вся эта область недостижима.
 *
 * Слайдер доли амплификаций — про робастность. Он пересобирает выборку, и на
 * графике реально появляются новые красные точки слева вверху, а прямая
 * заваливается. Наклон при этом уезжает от истинного 0.42 вплоть до
 * отрицательных значений — модель начинает утверждать, что крупный ботнет
 * бьёт слабее мелкого.
 */
@Composable
fun LrInteractive(
    modifier: Modifier = Modifier,
    title: String?,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onOpenChat: (String) -> Unit = {}
) {
    val textColor = Color.White
    val accent = ColorLine
    val topicTitle = title ?: "Линейная регрессия"

    var lrSlider by remember { mutableFloatStateOf(LrLab.lrToSlider(LrLab.DEFAULT_LR)) }
    var epochs by remember { mutableIntStateOf(LrLab.DEFAULT_EPOCHS) }
    var lambda by remember { mutableFloatStateOf(LrLab.DEFAULT_LAMBDA.toFloat()) }
    var ampRate by remember { mutableFloatStateOf(LrLab.DEFAULT_AMP_RATE.toFloat()) }
    var normalize by remember { mutableStateOf(LrLab.DEFAULT_NORMALIZE) }

    val learningRate = LrLab.sliderToLr(lrSlider)

    val data = remember(ampRate) { LrLab.generate(ampRate.toDouble()) }

    var fit by remember { mutableStateOf<LrLab.FitResult?>(null) }

    LaunchedEffect(lrSlider, epochs, lambda, ampRate, normalize) {
        delay(140)
        fit = withContext(Dispatchers.Default) {
            LrLab.fit(data, learningRate, epochs, normalize, lambda.toDouble())
        }
    }

    val current = fit
    val m = remember(current, data) { current?.let { LrLab.metrics(data, it) } }

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
            "История из ${LrLab.SAMPLE_COUNT} атак: по горизонтали — размер ботнета в тысячах узлов, " +
                "по вертикали — пиковая полоса в Гбит/с. Истинная зависимость, из которой " +
                "сгенерирована история: ${LrLab.TRUE_K} Гбит/с на тысячу узлов плюс " +
                "${LrLab.TRUE_B} Гбит/с фона. Задача обучения — восстановить эти два числа.",
            fontSize = 14.sp, color = textColor.copy(alpha = 0.75f), lineHeight = 20.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // ---------------- График 1: облако точек и прямая ----------------
        ChartBox(height = 250.dp) {
            if (current != null) {
                ScatterCanvas(data, current)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            LegendDot(ColorPoint, "обычные атаки", textColor)
            LegendDot(ColorOutlier, "амплификация", textColor)
            LegendDot(ColorTrue, "истина", textColor)
        }
        Text(
            "Жёлтая прямая — то, что выучила модель. Сиреневая — истинная зависимость. " +
                "Вертикальные чёрточки от точек до прямой — остатки, именно их квадраты " +
                "модель и минимизирует.",
            fontSize = 12.sp, color = textColor.copy(alpha = 0.6f), lineHeight = 17.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
        )

        // ---------------- График 2: кривая обучения ----------------
        Text(
            "Кривая обучения",
            color = textColor.copy(alpha = 0.9f), fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp)
        )
        ChartBox(height = 150.dp) {
            if (current != null && current.lossHistory.isNotEmpty()) {
                LossCanvas(current.lossHistory)
            }
        }
        Text(
            if (current?.diverged == true)
                "Кривая уходит вверх — ошибка растёт с каждой эпохой вместо того, чтобы падать. " +
                    "Это и есть расходящееся обучение."
            else
                "Ошибка по эпохам, вертикальная ось логарифмическая. Ровное плато справа " +
                    "означает, что спуск дошёл до минимума и лишние эпохи ничего не дают.",
            fontSize = 12.sp, color = textColor.copy(alpha = 0.6f), lineHeight = 17.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
        )

        if (current?.diverged == true) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ColorOutlier.copy(alpha = 0.16f))
                    .border(1.dp, ColorOutlier.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Text(
                    "Модель разошлась на эпохе ${current.epochsDone}: наклон ушёл в бесконечность. " +
                        if (!normalize)
                            "Признак измеряется десятками тысяч, и без нормализации предел скорости " +
                                "обучения лежит около ${"%.5f".format(LrLab.UNNORMALIZED_LIMIT)} — " +
                                "включите нормализацию или уменьшите скорость."
                        else "Уменьшите скорость обучения.",
                    color = ColorOutlier, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    lineHeight = 18.sp
                )
            }
            Spacer(Modifier.height(14.dp))
        }

        // ---------------- Параметры ----------------
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Подготовка данных", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(10.dp))

                Text("Нормализация признака", color = textColor, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SegmentButton("Включена", normalize, accent, Modifier.weight(1f)) { normalize = true }
                    SegmentButton("Выключена", !normalize, ColorOutlier, Modifier.weight(1f)) { normalize = false }
                }
                Text(
                    if (normalize)
                        "Узлы переведены в «сигмы»: градиенты по обоим параметрам сопоставимы, " +
                            "рабочий диапазон скорости обучения широкий."
                    else
                        "Обучение идёт прямо на тысячах узлов. Градиент по наклону в десятки раз " +
                            "крупнее градиента по свободному члену — предел скорости обучения " +
                            "падает примерно до ${"%.5f".format(LrLab.UNNORMALIZED_LIMIT)}.",
                    color = textColor.copy(alpha = 0.55f), fontSize = 11.sp, lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )

                Spacer(Modifier.height(14.dp))
                SliderRow(
                    label = "Доля атак с амплификацией",
                    value = "${(ampRate * 100).roundToInt()}%",
                    hint = if (ampRate < 0.02f) "выборка чистая, выбросов нет"
                    else "красные точки слева вверху — они тянут прямую вниз по наклону и вверх по фону",
                    textColor = textColor
                ) {
                    Slider(
                        value = ampRate, onValueChange = { ampRate = it },
                        valueRange = LrLab.AMP_MIN.toFloat()..LrLab.AMP_MAX.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = ColorOutlier, activeTrackColor = ColorOutlier)
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Обучение", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)

                SliderRow(
                    label = "Скорость обучения (шкала логарифмическая)",
                    value = formatLr(learningRate),
                    hint = when {
                        learningRate < 1e-4 -> "очень мелкие шаги — за сотни эпох никуда не дойдём"
                        !normalize && learningRate > LrLab.UNNORMALIZED_LIMIT ->
                            "выше предела для ненормализованного признака — ждите разлёта"
                        else -> "рабочая область"
                    },
                    textColor = textColor
                ) {
                    Slider(
                        value = lrSlider, onValueChange = { lrSlider = it },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
                    )
                }

                SliderRow(
                    label = "Число эпох",
                    value = "$epochs",
                    hint = if (epochs < 30) "спуск ещё в пути" else "обычно этого достаточно",
                    textColor = textColor
                ) {
                    Slider(
                        value = epochs.toFloat(), onValueChange = { epochs = it.roundToInt() },
                        valueRange = LrLab.EPOCHS_MIN.toFloat()..LrLab.EPOCHS_MAX.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
                    )
                }

                SliderRow(
                    label = "L2-регуляризация наклона",
                    value = "%.2f".format(lambda),
                    hint = if (lambda < 0.02f) "штрафа нет"
                    else "наклон притягивается к нулю — от выбросов это, вопреки ожиданиям, не лечит",
                    textColor = textColor
                ) {
                    Slider(
                        value = lambda, onValueChange = { lambda = it },
                        valueRange = LrLab.LAMBDA_MIN.toFloat()..LrLab.LAMBDA_MAX.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
                    )
                }

                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = 0.14f))
                        .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .clickable {
                            lrSlider = LrLab.lrToSlider(LrLab.DEFAULT_LR)
                            epochs = LrLab.DEFAULT_EPOCHS
                            lambda = LrLab.DEFAULT_LAMBDA.toFloat()
                            ampRate = LrLab.DEFAULT_AMP_RATE.toFloat()
                            normalize = LrLab.DEFAULT_NORMALIZE
                        }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Вернуть эталонные настройки",
                        color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------------- Результат ----------------
        if (current != null && m != null && !current.diverged) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Что выучила модель", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.height(10.dp))

                    ValueLine(
                        "Наклон, Гбит/с на тысячу узлов",
                        "%.3f".format(current.slopeReal),
                        "истина ${LrLab.TRUE_K}",
                        textColor,
                        if (abs(current.slopeReal - LrLab.TRUE_K) < 0.05) ColorPoint else ColorOutlier
                    )
                    ValueLine(
                        "Свободный член, Гбит/с",
                        "%.2f".format(current.interceptReal),
                        "истина ${LrLab.TRUE_B}",
                        textColor,
                        if (abs(current.interceptReal - LrLab.TRUE_B) < 3.0) ColorPoint else ColorOutlier
                    )

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = textColor.copy(alpha = 0.15f))
                    Spacer(Modifier.height(10.dp))

                    MetricLine("MSE — средний квадрат остатка", "%.2f".format(m.mse), textColor)
                    MetricLine("MAE — средний промах, Гбит/с", "%.2f".format(m.mae), textColor)
                    MetricLine("Коэффициент детерминации", "%.3f".format(m.r2), textColor)

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = textColor.copy(alpha = 0.15f))
                    Spacer(Modifier.height(10.dp))

                    Text(
                        "Прогноз для ботнета в ${LrLab.FORECAST_NODES.roundToInt()} тысяч узлов",
                        color = textColor.copy(alpha = 0.75f), fontSize = 12.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${"%.1f".format(current.predict(LrLab.FORECAST_NODES))} Гбит/с",
                        color = accent, fontSize = 22.sp, fontWeight = FontWeight.Bold
                    )
                    Text(
                        "истинное значение зависимости: " +
                            "${"%.1f".format(LrLab.TRUE_K * LrLab.FORECAST_NODES + LrLab.TRUE_B)} Гбит/с",
                        color = textColor.copy(alpha = 0.55f), fontSize = 11.sp
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
                        text = lrInsight(
                            normalize = normalize,
                            learningRate = learningRate,
                            epochs = epochs,
                            lambda = lambda.toDouble(),
                            ampRate = ampRate.toDouble(),
                            slope = current.slopeReal,
                            intercept = current.interceptReal,
                            m = m
                        ),
                        color = textColor.copy(alpha = 0.82f), fontSize = 13.sp, lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    AskChatButton(accent = accent, onClick = {
                        onOpenChat(
                            buildInteractiveChatPrompt(
                                topicTitle,
                                "скорость обучения = ${formatLr(learningRate)}, эпох = $epochs, " +
                                    "нормализация ${if (normalize) "включена" else "выключена"}, " +
                                    "L2 = ${"%.2f".format(lambda)}, " +
                                    "доля атак с амплификацией = ${(ampRate * 100).roundToInt()}%",
                                "наклон = ${"%.3f".format(current.slopeReal)} при истинном ${LrLab.TRUE_K}, " +
                                    "свободный член = ${"%.2f".format(current.interceptReal)} при истинном ${LrLab.TRUE_B}, " +
                                    "MSE = ${"%.2f".format(m.mse)}, MAE = ${"%.2f".format(m.mae)}, " +
                                    "коэффициент детерминации = ${"%.3f".format(m.r2)}"
                            )
                        )
                    })
                }
            }
        } else if (current == null) {
            Text("Идёт обучение…", color = textColor.copy(alpha = 0.6f), fontSize = 14.sp)
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
                .background(color.copy(alpha = 0.8f))
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
            .heightIn(min = 44.dp)          // минимальный тап-таргет
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f))
            .border(
                1.dp,
                if (selected) accent.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.15f),
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick),   // именно clickable, не detectTapGestures:
        contentAlignment = Alignment.Center  // экран прокручиваемый, жест перехватывается
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
private fun ValueLine(label: String, value: String, reference: String, textColor: Color, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(label, color = textColor.copy(alpha = 0.75f), fontSize = 12.sp, lineHeight = 16.sp)
            Text(reference, color = textColor.copy(alpha = 0.45f), fontSize = 11.sp)
        }
        Text(value, color = valueColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MetricLine(label: String, value: String, textColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label, color = textColor.copy(alpha = 0.75f), fontSize = 12.sp,
            lineHeight = 16.sp, modifier = Modifier.weight(1f).padding(end = 10.dp)
        )
        Text(value, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

// =====================================================================
// Отрисовка
// =====================================================================

@Composable
private fun ScatterCanvas(data: List<AttackSample>, f: LrLab.FitResult) {
    Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        val w = size.width
        val h = size.height
        val yMax = maxOf(LrLab.maxBandwidth(data), 60.0)

        fun px(nodes: Double, bw: Double) = Offset(
            ((nodes - LrLab.NODES_MIN) / (LrLab.NODES_MAX - LrLab.NODES_MIN) * w).toFloat(),
            (h - bw / yMax * h).toFloat()
        )

        // оси
        drawLine(Color.White.copy(alpha = 0.3f), Offset(0f, h), Offset(w, h), strokeWidth = designPx(1.5f))

        // истинная зависимость
        drawLine(
            ColorTrue.copy(alpha = 0.7f),
            px(LrLab.NODES_MIN, LrLab.TRUE_K * LrLab.NODES_MIN + LrLab.TRUE_B),
            px(LrLab.NODES_MAX, LrLab.TRUE_K * LrLab.NODES_MAX + LrLab.TRUE_B),
            strokeWidth = designPx(2f)
        )

        // остатки — тонкие чёрточки от точки к прямой
        for (p in data) {
            val pred = f.predict(p.nodes)
            if (pred.isNaN()) continue
            drawLine(
                Color.White.copy(alpha = 0.16f),
                px(p.nodes, p.bandwidth),
                px(p.nodes, pred.coerceIn(-yMax, yMax * 2)),
                strokeWidth = designPx(1f)
            )
        }

        // точки; выбросом считаем всё, что заметно выше истинной зависимости
        for (p in data) {
            val expected = LrLab.TRUE_K * p.nodes + LrLab.TRUE_B
            val isOutlier = p.bandwidth > expected + 18.0
            drawCircle(
                color = if (isOutlier) ColorOutlier else ColorPoint,
                radius = designPx(4f),
                center = px(p.nodes, p.bandwidth)
            )
        }

        // выученная прямая
        val y0 = f.predict(LrLab.NODES_MIN)
        val y1 = f.predict(LrLab.NODES_MAX)
        if (!y0.isNaN() && !y1.isNaN() && abs(y0) < 1e6 && abs(y1) < 1e6) {
            drawLine(
                ColorLine,
                px(LrLab.NODES_MIN, y0.coerceIn(-yMax, yMax * 2)),
                px(LrLab.NODES_MAX, y1.coerceIn(-yMax, yMax * 2)),
                strokeWidth = designPx(3f)
            )
        }
    }
}

@Composable
private fun LossCanvas(history: DoubleArray) {
    Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        val w = size.width
        val h = size.height
        if (history.size < 2) return@Canvas

        // логарифмическая вертикальная ось: ошибка падает на порядки,
        // на линейной шкале вся интересная часть слипается у нуля
        var lo = Double.MAX_VALUE
        var hi = -Double.MAX_VALUE
        for (v in history) {
            val lv = ln(maxOf(v, 1e-6))
            if (lv < lo) lo = lv
            if (lv > hi) hi = lv
        }
        if (hi - lo < 1e-6) { hi = lo + 1.0 }

        var prev: Offset? = null
        for (i in history.indices) {
            val lv = ln(maxOf(history[i], 1e-6))
            val pt = Offset(
                i.toFloat() / (history.size - 1) * w,
                (h - ((lv - lo) / (hi - lo)) * h).toFloat()
            )
            prev?.let { drawLine(ColorLine, it, pt, strokeWidth = designPx(2.5f)) }
            prev = pt
        }
        drawLine(Color.White.copy(alpha = 0.3f), Offset(0f, h), Offset(w, h), strokeWidth = designPx(1.5f))
    }
}

// =====================================================================
// Пояснения
// =====================================================================

private fun formatLr(lr: Double): String = when {
    lr >= 0.1 -> "%.3f".format(lr)
    lr >= 0.001 -> "%.4f".format(lr)
    else -> "%.6f".format(lr)
}

private fun lrInsight(
    normalize: Boolean,
    learningRate: Double,
    epochs: Int,
    lambda: Double,
    ampRate: Double,
    slope: Double,
    intercept: Double,
    m: LrLab.Metrics
): String {
    val parts = ArrayList<String>()

    if (!normalize) {
        parts.add(
            "Нормализация выключена, и обучение идёт прямо на тысячах узлов. Модель ещё держится " +
                "только потому, что скорость обучения ${formatLr(learningRate)} ниже предела " +
                "${"%.5f".format(LrLab.UNNORMALIZED_LIMIT)}. Попробуйте поднять её хотя бы вдвое."
        )
    }

    if (m.r2 < 0.3 && epochs < 40) {
        parts.add(
            "Коэффициент детерминации ${"%.2f".format(m.r2)} — модель пока хуже, чем простое " +
                "предсказание средним. Обучение не дошло до минимума: добавьте эпох."
        )
    }

    if (lambda > 0.1) {
        parts.add(
            "L2-штраф ${"%.2f".format(lambda)} притянул наклон к ${"%.3f".format(slope)}. " +
                "Регуляризация всегда занижает наклон — это её работа, и в задаче с одним " +
                "признаком и сотней наблюдений она приносит только вред."
        )
    }

    if (ampRate > 0.05) {
        val bias = LrLab.TRUE_K - slope
        parts.add(
            "Атаки с амплификацией составляют ${(ampRate * 100).roundToInt()}% выборки. " +
                "Наклон занижен на ${"%.3f".format(bias)} относительно истинного ${LrLab.TRUE_K}, " +
                "а фон завышен до ${"%.1f".format(intercept)} вместо ${LrLab.TRUE_B}. " +
                "Прямая старается пройти поближе к выбросам, потому что их квадратичные остатки " +
                "весят в сумме больше, чем остатки десятков обычных атак."
        )
        if (slope < 0) {
            parts.add(
                "Наклон стал отрицательным: модель утверждает, что чем крупнее ботнет, тем слабее " +
                    "атака. Это не ошибка вычислений, а честный минимум суммы квадратов на таких данных — " +
                    "и лучшая иллюстрация того, почему выбросы в безопасности нельзя игнорировать."
            )
        }
        if (m.mse / maxOf(m.mae, 1e-6) > 12.0) {
            parts.add(
                "MSE ${"%.1f".format(m.mse)} при MAE ${"%.1f".format(m.mae)}: квадратичная ошибка " +
                    "растёт намного быстрее абсолютной. Это диагностический признак того, что " +
                    "основной вклад в ошибку дают немногочисленные крупные промахи, а не общая неточность."
            )
        }
    } else if (abs(slope - LrLab.TRUE_K) < 0.03 && abs(intercept - LrLab.TRUE_B) < 1.5) {
        parts.add(
            "Модель восстановила истинную зависимость почти точно: наклон ${"%.3f".format(slope)} " +
                "против ${LrLab.TRUE_K} и фон ${"%.2f".format(intercept)} против ${LrLab.TRUE_B}. " +
                "Остаточное расхождение — это шум выборки, а не ошибка метода."
        )
    }

    if (parts.isEmpty()) {
        parts.add(
            "Наклон ${"%.3f".format(slope)}, фон ${"%.2f".format(intercept)}, " +
                "коэффициент детерминации ${"%.2f".format(m.r2)}."
        )
    }
    return parts.joinToString(" ")
}
