package com.eduappml.ui.svm

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
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.roundToInt

private val ColorAnom = Color(0xFFFF6B6B)
private val ColorLegit = Color(0xFF6BCB77)
private val ColorAccent = Color(0xFFFFD93D)

/** Всё, что считается в фоне за один заход: модель, метрики и данные для
 *  отрисовки. Собрано в один класс, чтобы не таскать пять отдельных
 *  состояний и не приводить типы вручную. */
private class SvmComputed(
    val model: SvmLab.SvmModel,
    val accuracy: Double,
    val svFlags: BooleanArray,
    val svCount: Int,
    val grid: Array<DoubleArray>?
)

/**
 * Интерактив темы «Метод опорных векторов»: отделение аномального трафика.
 *
 * Зазор нарисован двумя пунктирными линиями, опорные векторы подсвечены
 * кольцом. Это главное, что должно быть видно: при росте C зазор сужается,
 * а опорных векторов остаётся всё меньше — модель начинает держаться на
 * горстке пограничных сессий.
 *
 * Слайдер доли выбросов добавляет в выборку «ночной бэкап» — легитимные
 * сессии с профилем эксфильтрации. На чистых данных C почти не влияет на
 * точность, а на данных с выбросами у неё появляется настоящий максимум
 * в середине диапазона.
 *
 * Слайдеры C и gamma логарифмические: оба параметра охватывают более двух
 * порядков, и на линейной шкале нижняя половина была бы недостижима.
 */
@Composable
fun SvmInteractive(
    modifier: Modifier = Modifier,
    title: String?,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onOpenChat: (String) -> Unit = {}
) {
    val textColor = Color.White
    val accent = ColorAccent
    val topicTitle = title ?: "Метод опорных векторов"

    var cSlider by remember {
        mutableFloatStateOf(SvmLab.logToSlider(SvmLab.DEFAULT_C, SvmLab.C_MIN, SvmLab.C_MAX))
    }
    var gammaSlider by remember {
        mutableFloatStateOf(SvmLab.logToSlider(SvmLab.DEFAULT_GAMMA, SvmLab.GAMMA_MIN, SvmLab.GAMMA_MAX))
    }
    var kernel by remember { mutableStateOf(SvmLab.DEFAULT_KERNEL) }
    var iterations by remember { mutableIntStateOf(SvmLab.DEFAULT_ITERATIONS) }
    var outliers by remember { mutableFloatStateOf(SvmLab.DEFAULT_OUTLIERS.toFloat()) }

    val cValue = SvmLab.sliderToLog(cSlider, SvmLab.C_MIN, SvmLab.C_MAX)
    val gammaValue = SvmLab.sliderToLog(gammaSlider, SvmLab.GAMMA_MIN, SvmLab.GAMMA_MAX)

    val train = remember(outliers) { SvmLab.trainSet(outliers.toDouble()) }
    val test = remember(outliers) { SvmLab.testSet(outliers.toDouble()) }

    var computed by remember { mutableStateOf<SvmComputed?>(null) }

    LaunchedEffect(cSlider, gammaSlider, kernel, iterations, outliers) {
        delay(160)
        computed = withContext(Dispatchers.Default) {
            val m = SvmLab.train(cValue, kernel, gammaValue, iterations, train)
            val flags = BooleanArray(train.size) { m.isSupportVector(it) }
            SvmComputed(
                model = m,
                accuracy = SvmLab.accuracy(test, m),
                svFlags = flags,
                svCount = flags.count { it },
                grid = if (kernel == SvmKernel.RBF) SvmLab.decisionGrid(m) else null
            )
        }
    }

    val result = computed
    val current = result?.model
    val acc = result?.accuracy
    val svFlags = result?.svFlags
    val svCount = result?.svCount ?: 0
    val grid = result?.grid
    val margin = current?.marginWidth()
    val weights = current?.linearWeights()

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
            "Сетевые сессии: по горизонтали средний размер пакета, по вертикали доля SYN-пакетов " +
                "без ответа. Сплошная линия — граница решения, пунктирные — края зазора. " +
                "Опорные векторы обведены кольцом: только они держат границу.",
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
        ) {
            if (current != null && svFlags != null) {
                SvmCanvas(train, svFlags, weights, grid)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            LegendDot(ColorLegit, "легитимный", textColor)
            LegendDot(ColorAnom, "аномальный", textColor)
        }

        Spacer(Modifier.height(18.dp))

        // ---------------- Мягкий зазор ----------------
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Мягкий зазор", color = textColor,
                    fontWeight = FontWeight.SemiBold, fontSize = 16.sp)

                SliderRow(
                    label = "C — цена нарушения зазора (шкала логарифмическая)",
                    value = formatC(cValue),
                    hint = when {
                        cValue < 0.15 -> "нарушения почти бесплатны — зазор раздут, граница грубая"
                        cValue > 10.0 -> "нарушения очень дороги — модель подстраивается под каждую точку"
                        else -> "рабочая область"
                    },
                    textColor = textColor
                ) {
                    Slider(
                        value = cSlider, onValueChange = { cSlider = it },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                    )
                }

                SliderRow(
                    label = "Доля выбросов — «ночной бэкап»",
                    value = "${(outliers * 100).roundToInt()}%",
                    hint = if (outliers < 0.02f)
                        "выборка чистая: классы разделяются почти идеально"
                    else "легитимные сессии с профилем эксфильтрации тянут границу на себя",
                    textColor = textColor
                ) {
                    Slider(
                        value = outliers, onValueChange = { outliers = it },
                        valueRange = SvmLab.OUTLIERS_MIN.toFloat()..SvmLab.OUTLIERS_MAX.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = ColorAnom, activeTrackColor = ColorAnom)
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------------- Ядро ----------------
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Ядро", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (kv in SvmKernel.entries) {
                        SegmentButton(kv.label, kernel == kv, accent, Modifier.weight(1f)) { kernel = kv }
                    }
                }
                Text(
                    if (kernel == SvmKernel.LINEAR)
                        "Линейное ядро проводит прямую. Для неё есть явные веса, поэтому " +
                            "зазор рисуется точно, а не перебором."
                    else
                        "RBF-ядро даёт кривую границу. Явных весов нет — граница и зазор " +
                            "строятся перебором по сетке, ширина зазора не определена.",
                    color = textColor.copy(alpha = 0.55f), fontSize = 11.sp, lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )

                if (kernel == SvmKernel.RBF) {
                    SliderRow(
                        label = "Gamma — радиус влияния точки (шкала логарифмическая)",
                        value = "%.2f".format(gammaValue),
                        hint = when {
                            gammaValue < 0.6 -> "влияние далёкое — граница гладкая, почти прямая"
                            gammaValue > 12.0 -> "влияние локальное — граница рассыпается на островки вокруг точек"
                            else -> "умеренный радиус"
                        },
                        textColor = textColor
                    ) {
                        Slider(
                            value = gammaSlider, onValueChange = { gammaSlider = it },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
                        )
                    }
                }

                SliderRow(
                    label = "Число итераций Pegasos",
                    value = "$iterations",
                    hint = if (iterations < 400)
                        "граница ещё не встала на место — обучение приближённое"
                    else "результат уже стабилен",
                    textColor = textColor
                ) {
                    Slider(
                        value = iterations.toFloat(), onValueChange = { iterations = it.roundToInt() },
                        valueRange = SvmLab.ITER_MIN.toFloat()..SvmLab.ITER_MAX.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------------- Результат ----------------
        val currentAcc = acc
        if (current != null && currentAcc != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Результат на ${SvmLab.TEST_SIZE} контрольных сессиях",
                        color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BigStat("Точность", "%.3f".format(currentAcc),
                            accent, Modifier.weight(1f), textColor)
                        BigStat("Опорных векторов", "$svCount из ${train.size}",
                            ColorLegit, Modifier.weight(1f), textColor)
                    }

                    if (margin != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Ширина зазора: ${"%.4f".format(margin)}",
                            color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                        )
                        if (weights != null) {
                            Text(
                                "w = (${"%.3f".format(weights[0])}, ${"%.3f".format(weights[1])}), " +
                                    "b = ${"%.3f".format(weights[2])}",
                                color = textColor.copy(alpha = 0.6f), fontSize = 12.sp
                            )
                        }
                    } else {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Ширина зазора для RBF-ядра не определена: явных весов нет, " +
                                "а в пространстве, куда ядро отображает точки, расстояния " +
                                "не выражаются в исходных единицах.",
                            color = textColor.copy(alpha = 0.6f), fontSize = 12.sp, lineHeight = 16.sp
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
                    Text(
                        text = svmInsight(
                            c = cValue, kernel = kernel, gamma = gammaValue,
                            iterations = iterations, outliers = outliers.toDouble(),
                            accuracy = currentAcc, svCount = svCount,
                            total = train.size, margin = margin
                        ),
                        color = textColor.copy(alpha = 0.82f), fontSize = 13.sp, lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    AskChatButton(accent = accent, onClick = {
                        val gammaPart = if (kernel == SvmKernel.RBF)
                            ", gamma = ${"%.2f".format(gammaValue)}" else ""
                        onOpenChat(
                            buildInteractiveChatPrompt(
                                topicTitle,
                                "C = ${formatC(cValue)}, ядро — ${kernel.label}$gammaPart, " +
                                    "итераций = $iterations, " +
                                    "доля выбросов = ${(outliers * 100).roundToInt()}%",
                                "точность = ${"%.3f".format(currentAcc)}, " +
                                    "опорных векторов = $svCount из ${train.size}" +
                                    (if (margin != null) ", ширина зазора = ${"%.4f".format(margin)}" else "")
                            )
                        )
                    })
                }
            }
        } else {
            Text("Идёт обучение…", color = textColor.copy(alpha = 0.6f), fontSize = 14.sp)
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
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(label, color = textColor.copy(alpha = 0.7f), fontSize = 11.sp, lineHeight = 15.sp)
    }
}

// =====================================================================
// Отрисовка
// =====================================================================

@Composable
private fun SvmCanvas(
    train: List<FlowSample>,
    svFlags: BooleanArray?,
    weights: DoubleArray?,
    grid: Array<DoubleArray>?
) {
    Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        val w = size.width
        val h = size.height

        fun px(x1: Double, x2: Double) = Offset((x1 * w).toFloat(), (h - x2 * h).toFloat())

        // Для RBF явных весов нет — красим сетку по знаку решающей функции,
        // а полупрозрачной полосой показываем зону зазора.
        if (grid != null) {
            val steps = grid.size
            val cw = w / steps
            val ch = h / steps
            for (gx in 0 until steps) {
                for (gy in 0 until steps) {
                    val v = grid[gx][gy]
                    val base = if (v >= 0) ColorAnom else ColorLegit
                    val inMargin = abs(v) < 1.0
                    drawRect(
                        color = base.copy(alpha = if (inMargin) 0.09f else 0.20f),
                        topLeft = Offset(gx * cw, h - (gy + 1) * ch),
                        size = Size(cw + 0.6f, ch + 0.6f)
                    )
                }
            }
        }

        // Для линейного ядра рисуем границу и края зазора точно, по весам.
        // Граница: w0*x1 + w1*x2 + b = level, где level = 0, +1, -1.
        if (weights != null) {
            val w0 = weights[0]
            val w1 = weights[1]
            val b = weights[2]

            fun lineFor(level: Double): Pair<Offset, Offset>? {
                if (abs(w1) > 1e-6) {
                    val y0 = (level - b - w0 * 0.0) / w1
                    val y1 = (level - b - w0 * 1.0) / w1
                    return px(0.0, y0) to px(1.0, y1)
                }
                if (abs(w0) > 1e-6) {
                    val x = (level - b) / w0
                    return px(x, 0.0) to px(x, 1.0)
                }
                return null
            }

            // края зазора — пунктиром, вручную отрезками
            for (level in listOf(-1.0, 1.0)) {
                val seg = lineFor(level) ?: continue
                val dashes = 26
                for (k in 0 until dashes) {
                    if (k % 2 == 1) continue
                    val t0 = k.toFloat() / dashes
                    val t1 = (k + 1).toFloat() / dashes
                    drawLine(
                        Color.White.copy(alpha = 0.45f),
                        Offset(
                            seg.first.x + (seg.second.x - seg.first.x) * t0,
                            seg.first.y + (seg.second.y - seg.first.y) * t0
                        ),
                        Offset(
                            seg.first.x + (seg.second.x - seg.first.x) * t1,
                            seg.first.y + (seg.second.y - seg.first.y) * t1
                        ),
                        strokeWidth = designPx(2f)
                    )
                }
            }

            lineFor(0.0)?.let { seg ->
                drawLine(ColorAccent, seg.first, seg.second, strokeWidth = designPx(3f))
            }
        }

        // сессии
        for (i in train.indices) {
            val s = train[i]
            val c = px(s.x[0], s.x[1])
            val color = if (s.label == 1) ColorAnom else ColorLegit
            drawCircle(color, radius = designPx(4.5f), center = c)
            if (svFlags != null && svFlags[i]) {
                drawCircle(
                    Color.White.copy(alpha = 0.85f),
                    radius = designPx(8f),
                    center = c,
                    style = Stroke(width = designPx(1.8f))
                )
            }
        }

        drawLine(Color.White.copy(alpha = 0.3f), Offset(0f, h), Offset(w, h), strokeWidth = designPx(1.5f))
    }
}

// =====================================================================
// Пояснения
// =====================================================================

private fun formatC(c: Double): String = if (c >= 1.0) "%.2f".format(c) else "%.3f".format(c)

private fun svmInsight(
    c: Double,
    kernel: SvmKernel,
    gamma: Double,
    iterations: Int,
    outliers: Double,
    accuracy: Double,
    svCount: Int,
    total: Int,
    margin: Double?
): String {
    val parts = ArrayList<String>()

    if (iterations < 400) {
        parts.add(
            "Итераций всего $iterations — обучение приближённое, и граница ещё не встала на " +
                "место. У точного решателя такого слайдера не было бы: он находит оптимум сразу, " +
                "но ценой сложности, растущей как квадрат числа объектов."
        )
    }

    if (c < 0.15) {
        parts.add(
            "C = ${formatC(c)}: нарушения зазора почти бесплатны, и модель раздувает его до " +
                "бессмысленных размеров. Точность ${"%.3f".format(accuracy)} — регуляризация " +
                "настолько велика, что данные почти не учитываются."
        )
    } else if (c > 10.0) {
        parts.add(
            "C = ${formatC(c)}: нарушения очень дороги, зазор сузился" +
                (if (margin != null) " до ${"%.3f".format(margin)}" else "") +
                ", опорных векторов осталось $svCount из $total. Вся граница держится на горстке " +
                "наблюдений — это и есть подгонка под отдельные точки."
        )
    }

    if (outliers > 0.05) {
        parts.add(
            "В выборке ${(outliers * 100).roundToInt()}% сессий ночного бэкапа — легитимных, но с " +
                "профилем эксфильтрации. Именно на них проверяется, устоит ли граница. " +
                "Попробуйте пройти весь диапазон C: точность перестанет расти монотонно, " +
                "у неё появится максимум в середине."
        )
    } else {
        parts.add(
            "Выборка чистая, классы разделяются почти идеально. Обратите внимание, что в широком " +
                "диапазоне C точность держится одинаковой — а вот ширина зазора и число опорных " +
                "векторов меняются сильно. Разница между этими настройками проявится, как только " +
                "появятся выбросы."
        )
    }

    if (kernel == SvmKernel.RBF) {
        if (gamma > 12.0) {
            parts.add(
                "Gamma = ${"%.1f".format(gamma)}: влияние каждой точки локально, и граница " +
                    "рассыпается на островки вокруг отдельных наблюдений. Так выглядит " +
                    "переобучение ядровой моделью."
            )
        } else if (gamma < 0.6) {
            parts.add(
                "Gamma = ${"%.2f".format(gamma)}: влияние точек далёкое, граница почти прямая — " +
                    "RBF-ядро в таком режиме мало отличается от линейного."
            )
        }
        parts.add(
            "На двух наших признаках классы разделяются почти линейно, поэтому выигрыш от ядра " +
                "невелик — это честный результат, а не недоработка. Ядра начинают решать там, " +
                "где граница действительно изогнута."
        )
    }

    return parts.joinToString(" ")
}
