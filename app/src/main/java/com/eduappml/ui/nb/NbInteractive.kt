package com.eduappml.ui.nb

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

private val ColorDga = Color(0xFFFF6B6B)
private val ColorLegit = Color(0xFF6BCB77)
private val ColorAccent = Color(0xFFFFD93D)

/**
 * Интерактив темы «Наивный Байес»: детект DGA-доменов.
 *
 * Слайдер корреляции стоит ПЕРВЫМ и это сделано намеренно. Он ломает наивное
 * допущение, и рядом с F1 специально показана средняя уверенность модели:
 * при росте корреляции первое падает, второе — нет. Разрыв между этими двумя
 * числами и есть содержание темы.
 *
 * Касание графика создаёт «свой» домен. Это единственное место темы, где
 * нужен detectTapGestures, а не clickable: требуются координаты касания,
 * которых clickable не даёт (см. DEVELOPMENT_NOTES, раздел 2.1).
 */
@Composable
fun NbInteractive(
    modifier: Modifier = Modifier,
    title: String?,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onOpenChat: (String) -> Unit = {}
) {
    val textColor = Color.White
    val accent = ColorAccent
    val topicTitle = title ?: "Наивный Байес"

    var corr by remember { mutableFloatStateOf(NbLab.DEFAULT_CORR.toFloat()) }
    var trainSize by remember { mutableIntStateOf(NbLab.DEFAULT_TRAIN_SIZE) }
    var prior by remember { mutableFloatStateOf(NbLab.DEFAULT_PRIOR.toFloat()) }
    var smoothing by remember { mutableFloatStateOf(NbLab.DEFAULT_VAR_SMOOTHING.toFloat()) }
    var threshold by remember { mutableFloatStateOf(NbLab.DEFAULT_THRESHOLD.toFloat()) }

    var probe by remember { mutableStateOf<DoubleArray?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val train = remember(corr, trainSize) { NbLab.trainSet(corr.toDouble(), trainSize) }
    val test = remember(corr) { NbLab.testSet(corr.toDouble()) }

    var model by remember { mutableStateOf<NbModel?>(null) }
    LaunchedEffect(corr, trainSize, smoothing) {
        delay(120)
        model = withContext(Dispatchers.Default) { NbLab.fit(train, smoothing.toDouble()) }
    }

    val m = model
    val cm = remember(m, prior, threshold, test) {
        m?.let { NbLab.evaluate(test, it, prior.toDouble(), threshold.toDouble()) }
    }
    val confidence = remember(m, prior, test) {
        m?.let { NbLab.meanConfidence(test, it, prior.toDouble()) } ?: 0.5
    }
    val boundary = remember(m, prior, threshold) {
        m?.let { NbLab.decisionBoundary(it, prior.toDouble(), threshold.toDouble()) }
    }
    val probeProba = remember(probe, m, prior) {
        val p = probe
        if (p != null && m != null) NbLab.probaDga(p, m, prior.toDouble()) else null
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
            "Детектор DGA-доменов: по горизонтали энтропия имени, по вертикали доля цифр. " +
                "Обучающая выборка задаётся слайдерами, контрольная — всегда 200 доменов. " +
                "Коснитесь графика, чтобы проверить свой домен.",
            fontSize = 14.sp, color = textColor.copy(alpha = 0.75f), lineHeight = 20.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // ---------------- График 1: плоскость признаков ----------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
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
                            probe = doubleArrayOf(fx.toDouble(), fy.toDouble())
                        }
                    }
                }
        ) {
            if (m != null) {
                FeaturePlaneCanvas(train, boundary, probe)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LegendDot(ColorLegit, "легитимные", textColor)
            LegendDot(ColorDga, "DGA", textColor)
        }

        if (probe != null && probeProba != null && m != null) {
            Spacer(Modifier.height(10.dp))
            ProbeCard(probe!!, probeProba, m, threshold.toDouble(), textColor, accent) { probe = null }
        }

        Spacer(Modifier.height(18.dp))

        // ---------------- График 2: чему научилась модель ----------------
        Text(
            "Чему научилась модель: по одному нормальному распределению на класс и признак",
            color = textColor.copy(alpha = 0.9f), fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold, lineHeight = 19.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        for (j in 0 until NbLab.N_FEAT) {
            Text(
                NbLab.featureNames[j],
                color = textColor.copy(alpha = 0.7f), fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
            ) {
                if (m != null && m.ready) {
                    DensityCanvas(m, j, probe?.get(j))
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        if (m != null && m.ready) {
            Text(
                "Всего восемь чисел — это вся модель целиком. Чем сильнее две кривые " +
                    "перекрываются, тем слабее признак.",
                fontSize = 12.sp, color = textColor.copy(alpha = 0.6f), lineHeight = 17.sp,
                modifier = Modifier.padding(bottom = 18.dp)
            )
        }

        // ---------------- Параметры ----------------
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Наивное допущение",
                    color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp
                )
                SliderRow(
                    label = "Связь признаков внутри класса",
                    value = "%.2f".format(corr),
                    hint = if (corr < 0.05f)
                        "признаки независимы — допущение метода выполняется точно"
                    else
                        "признаки связаны: модель считает одну и ту же улику дважды",
                    textColor = textColor
                ) {
                    Slider(
                        value = corr, onValueChange = { corr = it },
                        valueRange = NbLab.CORR_MIN.toFloat()..NbLab.CORR_MAX.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = ColorDga, activeTrackColor = ColorDga)
                    )
                }

                SliderRow(
                    label = "Размер обучающей выборки",
                    value = "$trainSize",
                    hint = if (trainSize < 25)
                        "оценок мало — средние и дисперсии шумят"
                    else "данных достаточно для устойчивых оценок",
                    textColor = textColor
                ) {
                    Slider(
                        value = trainSize.toFloat(), onValueChange = { trainSize = it.roundToInt() },
                        valueRange = NbLab.TRAIN_MIN.toFloat()..NbLab.TRAIN_MAX.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
                    )
                }

                SliderRow(
                    label = "Сглаживание дисперсии",
                    value = "%.3f".format(smoothing),
                    hint = when {
                        smoothing < 0.002f -> "без добавки — риск вырожденных нулевых дисперсий"
                        smoothing > 0.04f -> "кривые расплылись, классы перестают различаться"
                        else -> "умеренная добавка, обычно слегка помогает"
                    },
                    textColor = textColor
                ) {
                    Slider(
                        value = smoothing, onValueChange = { smoothing = it },
                        valueRange = NbLab.SMOOTH_MIN.toFloat()..NbLab.SMOOTH_MAX.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
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
                    "Решающее правило",
                    color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp
                )
                SliderRow(
                    label = "Априорная вероятность DGA в потоке",
                    value = "%.2f".format(prior),
                    hint = if (prior < 0.06f)
                        "как в реальном DNS-потоке — модель становится очень осторожной"
                    else "в обучающей выборке доля DGA около трети",
                    textColor = textColor
                ) {
                    Slider(
                        value = prior, onValueChange = { prior = it },
                        valueRange = NbLab.PRIOR_MIN.toFloat()..NbLab.PRIOR_MAX.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                    )
                }

                SliderRow(
                    label = "Порог классификации",
                    value = "%.2f".format(threshold),
                    hint = "применяется к уже посчитанной вероятности, модель не трогает",
                    textColor = textColor
                ) {
                    Slider(
                        value = threshold, onValueChange = { threshold = it },
                        valueRange = NbLab.THRESHOLD_MIN.toFloat()..NbLab.THRESHOLD_MAX.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------------- Результат ----------------
        if (cm != null && m != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Результат на 200 контрольных доменах",
                        color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "TP = ${cm.tp}   FP = ${cm.fp}   TN = ${cm.tn}   FN = ${cm.fn}",
                        color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(10.dp))
                    MetricLine("Precision", "%.3f".format(cm.precision), textColor)
                    MetricLine("Recall", "%.3f".format(cm.recall), textColor)

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = textColor.copy(alpha = 0.15f))
                    Spacer(Modifier.height(12.dp))

                    // Две величины, ради сопоставления которых написана тема
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BigStat("F1 — насколько модель права", "%.3f".format(cm.f1),
                            ColorLegit, Modifier.weight(1f), textColor)
                        BigStat("Уверенность — насколько она в этом уверена",
                            "%.3f".format(confidence), accent, Modifier.weight(1f), textColor)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Двигайте связь признаков и следите за этой парой чисел: " +
                            "левое падает, правое — нет.",
                        color = textColor.copy(alpha = 0.6f), fontSize = 11.sp, lineHeight = 15.sp
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
                        text = nbInsight(
                            corr = corr.toDouble(),
                            trainSize = trainSize,
                            prior = prior.toDouble(),
                            smoothing = smoothing.toDouble(),
                            cm = cm,
                            confidence = confidence,
                            model = m
                        ),
                        color = textColor.copy(alpha = 0.82f), fontSize = 13.sp, lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    AskChatButton(accent = accent, onClick = {
                        onOpenChat(
                            buildInteractiveChatPrompt(
                                topicTitle,
                                "связь признаков = ${"%.2f".format(corr)}, " +
                                    "обучающая выборка = $trainSize доменов, " +
                                    "сглаживание дисперсии = ${"%.3f".format(smoothing)}, " +
                                    "априорная вероятность DGA = ${"%.2f".format(prior)}, " +
                                    "порог = ${"%.2f".format(threshold)}",
                                "TP=${cm.tp} FP=${cm.fp} TN=${cm.tn} FN=${cm.fn}, " +
                                    "precision=${"%.3f".format(cm.precision)}, " +
                                    "recall=${"%.3f".format(cm.recall)}, " +
                                    "F1=${"%.3f".format(cm.f1)}, " +
                                    "средняя уверенность модели=${"%.3f".format(confidence)}"
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
                .background(color.copy(alpha = 0.8f))
        )
        Spacer(Modifier.width(6.dp))
        Text(label, color = textColor.copy(alpha = 0.7f), fontSize = 12.sp)
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
private fun MetricLine(label: String, value: String, textColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = textColor.copy(alpha = 0.75f), fontSize = 12.sp,
            modifier = Modifier.weight(1f).padding(end = 10.dp))
        Text(value, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BigStat(label: String, value: String, color: Color, modifier: Modifier, textColor: Color) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Text(value, color = color, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(label, color = textColor.copy(alpha = 0.7f), fontSize = 11.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun ProbeCard(
    probe: DoubleArray,
    proba: Double,
    model: NbModel,
    threshold: Double,
    textColor: Color,
    accent: Color,
    onClear: () -> Unit
) {
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
                Text("Ваш домен", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
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
                "энтропия ${"%.2f".format(probe[0])}, доля цифр ${"%.2f".format(probe[1])}",
                color = textColor.copy(alpha = 0.7f), fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "P(DGA) = ${"%.3f".format(proba)} → ${if (proba >= threshold) "БЛОКИРОВАТЬ" else "пропустить"}",
                color = if (proba >= threshold) ColorDga else ColorLegit,
                fontSize = 17.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text("Вклад признаков в решение:", color = textColor.copy(alpha = 0.7f), fontSize = 12.sp)
            for (j in 0 until NbLab.N_FEAT) {
                val c = NbLab.featureContribution(probe, model, j)
                Text(
                    "  ${NbLab.featureNames[j]}: ${if (c >= 0) "+" else ""}${"%.2f".format(c)} " +
                        "(${if (c >= 0) "в пользу DGA" else "в пользу легитимного"})",
                    color = if (c >= 0) ColorDga.copy(alpha = 0.9f) else ColorLegit.copy(alpha = 0.9f),
                    fontSize = 12.sp, lineHeight = 17.sp
                )
            }
        }
    }
}

// =====================================================================
// Отрисовка
// =====================================================================

@Composable
private fun FeaturePlaneCanvas(
    train: List<DomainSample>,
    boundary: List<Double?>?,
    probe: DoubleArray?
) {
    Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        val w = size.width
        val h = size.height

        fun px(x1: Double, x2: Double) = Offset((x1 * w).toFloat(), (h - x2 * h).toFloat())

        // сетка
        for (k in 1 until 4) {
            val gx = w * k / 4f
            val gy = h * k / 4f
            drawLine(Color.White.copy(alpha = 0.07f), Offset(gx, 0f), Offset(gx, h), strokeWidth = designPx(1f))
            drawLine(Color.White.copy(alpha = 0.07f), Offset(0f, gy), Offset(w, gy), strokeWidth = designPx(1f))
        }

        // граница решения
        if (boundary != null) {
            var prev: Offset? = null
            for (i in boundary.indices) {
                val v = boundary[i]
                if (v == null) { prev = null; continue }
                val pt = px(i.toDouble() / (boundary.size - 1), v)
                prev?.let { drawLine(ColorAccent, it, pt, strokeWidth = designPx(3f)) }
                prev = pt
            }
        }

        // точки обучающей выборки
        for (s in train) {
            drawCircle(
                color = if (s.label >= 0.5) ColorDga.copy(alpha = 0.75f) else ColorLegit.copy(alpha = 0.75f),
                radius = designPx(4f),
                center = px(s.x[0], s.x[1])
            )
        }

        // проверяемый домен
        if (probe != null) {
            val c = px(probe[0], probe[1])
            drawCircle(Color.White, radius = designPx(9f), center = c)
            drawCircle(Color.Black.copy(alpha = 0.7f), radius = designPx(5f), center = c)
        }

        drawLine(Color.White.copy(alpha = 0.3f), Offset(0f, h), Offset(w, h), strokeWidth = designPx(1.5f))
    }
}

@Composable
private fun DensityCanvas(model: NbModel, feature: Int, probeValue: Double?) {
    Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
        val legit = model.legit ?: return@Canvas
        val dga = model.dga ?: return@Canvas
        val w = size.width
        val h = size.height
        val steps = 80

        // общий масштаб по вертикали, чтобы кривые были сравнимы между собой
        var maxD = 1e-6
        for (i in 0..steps) {
            val x = i.toDouble() / steps
            maxD = maxOf(
                maxD,
                NbLab.gaussDensity(x, legit.means[feature], legit.variances[feature]),
                NbLab.gaussDensity(x, dga.means[feature], dga.variances[feature])
            )
        }

        fun drawCurve(stat: ClassStat, color: Color) {
            var prev: Offset? = null
            for (i in 0..steps) {
                val x = i.toDouble() / steps
                val d = NbLab.gaussDensity(x, stat.means[feature], stat.variances[feature])
                val pt = Offset((x * w).toFloat(), (h - d / maxD * h * 0.92).toFloat())
                prev?.let { drawLine(color, it, pt, strokeWidth = designPx(2.5f)) }
                prev = pt
            }
        }

        drawCurve(legit, ColorLegit)
        drawCurve(dga, ColorDga)

        // средние — вертикальные засечки
        for ((stat, color) in listOf(legit to ColorLegit, dga to ColorDga)) {
            val mx = (stat.means[feature] * w).toFloat()
            drawLine(color.copy(alpha = 0.45f), Offset(mx, 0f), Offset(mx, h), strokeWidth = designPx(1.5f))
        }

        if (probeValue != null) {
            val px = (probeValue * w).toFloat()
            drawLine(Color.White.copy(alpha = 0.8f), Offset(px, 0f), Offset(px, h), strokeWidth = designPx(2f))
        }

        drawLine(Color.White.copy(alpha = 0.25f), Offset(0f, h), Offset(w, h), strokeWidth = designPx(1.5f))
    }
}

// =====================================================================
// Пояснения
// =====================================================================

private fun nbInsight(
    corr: Double,
    trainSize: Int,
    prior: Double,
    smoothing: Double,
    cm: NbLab.ConfusionMatrix,
    confidence: Double,
    model: NbModel
): String {
    val parts = ArrayList<String>()

    if (!model.ready) {
        return "В обучающей выборке не хватает примеров одного из классов — " +
            "увеличьте её размер."
    }

    if (corr > 0.1) {
        parts.add(
            "Связь признаков поднята до ${"%.2f".format(corr)}: энтропия и доля цифр внутри " +
                "класса больше не независимы, а модель продолжает перемножать их правдоподобия " +
                "как независимые улики. F1 сейчас ${"%.3f".format(cm.f1)}, а средняя уверенность " +
                "модели ${"%.3f".format(confidence)} — обратите внимание, что второе число " +
                "практически не изменилось. Модель ошибается чаще и никак об этом не сообщает."
        )
    } else {
        parts.add(
            "Признаки независимы — наивное допущение выполняется точно, и это лучший случай " +
                "для метода. F1 ${"%.3f".format(cm.f1)} при уверенности ${"%.3f".format(confidence)}."
        )
    }

    if (smoothing > 0.04) {
        parts.add(
            "Сглаживание дисперсии ${"%.3f".format(smoothing)} расплыло все четыре кривые до " +
                "почти одинаковой ширины. Когда дисперсии классов совпадают, квадратичные члены " +
                "в границе решения сокращаются — посмотрите на первый график, кривая граница " +
                "выпрямилась в прямую."
        )
    }

    if (trainSize < 25) {
        parts.add(
            "Обучающая выборка всего $trainSize доменов: средние и дисперсии оценены по горстке " +
                "примеров и заметно шумят. Наивный Байес переносит это лучше большинства методов — " +
                "ему нужно оценить всего по два числа на признак, — но предел есть и у него."
        )
    }

    if (prior < 0.08) {
        parts.add(
            "Априорная вероятность ${"%.2f".format(prior)} близка к реальной базовой частоте в " +
                "DNS-потоке. Модель стала осторожной: ложных срабатываний ${cm.fp}, зато пропущено " +
                "${cm.fn} доменов. Это не дефект — это правильная реакция на редкость события."
        )
    } else if (prior > 0.55) {
        parts.add(
            "Априорная вероятность ${"%.2f".format(prior)} завышена относительно реальности: " +
                "модель считает DGA частым событием и выдаёт ${cm.fp} ложных срабатываний."
        )
    }

    val gap = confidence - cm.accuracy
    if (gap > 0.06) {
        parts.add(
            "Разрыв между уверенностью ${"%.3f".format(confidence)} и долей правильных ответов " +
                "${"%.3f".format(cm.accuracy)} составляет ${"%.3f".format(gap)}. Это и есть мера " +
                "плохой калиброванности: числа на выходе выглядят как вероятности, но ими не являются."
        )
    }

    return parts.joinToString(" ")
}
