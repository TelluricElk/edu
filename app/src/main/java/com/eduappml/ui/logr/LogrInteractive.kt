package com.eduappml.ui.logr

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
import kotlin.math.abs
import kotlin.math.roundToInt

private val ColorPhish = Color(0xFFFF6B6B)
private val ColorLegit = Color(0xFF6BCB77)
private val ColorAccent = Color(0xFFFFD93D)

/**
 * Интерактив темы «Логистическая регрессия»: детектор фишинга в почтовом шлюзе.
 *
 * Шесть параметров разделены на две группы, и это разделение — главная мысль
 * экрана. Первые четыре меняют САМУ МОДЕЛЬ (требуют переобучения), последние
 * два — только РЕШАЮЩЕЕ ПРАВИЛО поверх уже посчитанных вероятностей.
 * Пользователь должен увидеть, что вес класса и порог двигают precision/recall
 * похоже, но природа у них разная.
 *
 * Три визуализации, каждая реагирует на свою группу параметров:
 *   1. Распределение предсказанных вероятностей по классам + линия порога.
 *      Обучение меняет форму гистограмм, порог — только линию.
 *   2. ROC-кривая с точкой текущего порога. Кривая зависит только от модели,
 *      точка ездит по ней при движении порога — наглядно показывает, что
 *      порог не улучшает модель, а лишь выбирает рабочую точку.
 *   3. Выученные веса признаков. Показывает, что возраст домена получает
 *      ОТРИЦАТЕЛЬНЫЙ вес, и что регуляризация буквально придавливает столбики.
 *
 * Обучение вынесено в Dispatchers.Default с дебаунсом: 300 писем x 400 эпох
 * x 5 признаков — это порядка 600 тысяч операций, на слабом устройстве
 * заметно, если считать в главном потоке при каждом движении слайдера.
 */
@Composable
fun LogrInteractive(
    modifier: Modifier = Modifier,
    title: String?,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onOpenChat: (String) -> Unit = {}
) {
    val textColor = Color.White
    val accent = ColorAccent
    val topicTitle = title ?: "Логистическая регрессия"

    // --- параметры обучения (меняют модель) ---
    var learningRate by remember { mutableFloatStateOf(LogrLab.DEFAULT_LR.toFloat()) }
    var epochs by remember { mutableIntStateOf(LogrLab.DEFAULT_EPOCHS) }
    var lambda by remember { mutableFloatStateOf(LogrLab.DEFAULT_LAMBDA.toFloat()) }
    var wPos by remember { mutableFloatStateOf(LogrLab.DEFAULT_W_POS.toFloat()) }

    // --- параметры решающего правила (модель не трогают) ---
    var threshold by remember { mutableFloatStateOf(LogrLab.DEFAULT_THRESHOLD.toFloat()) }
    var costFn by remember { mutableFloatStateOf(LogrLab.DEFAULT_COST_FN.toFloat()) }

    var fit by remember { mutableStateOf<LogrLab.FitResult?>(null) }
    var training by remember { mutableStateOf(true) }

    LaunchedEffect(learningRate, epochs, lambda, wPos) {
        training = true
        delay(140)
        fit = withContext(Dispatchers.Default) {
            LogrLab.fit(
                lr = learningRate.toDouble(),
                epochs = epochs,
                lambda = lambda.toDouble(),
                wPos = wPos.toDouble()
            )
        }
        training = false
    }

    val current = fit
    val scores = remember(current) {
        current?.let { LogrLab.scoreAll(LogrLab.testSet, it.w, it.b) }
    }
    val cm = remember(scores, threshold) {
        scores?.let { LogrLab.confusion(LogrLab.testSet, it, threshold.toDouble()) }
    }
    val histogram = remember(scores) {
        scores?.let { LogrLab.probaHistogram(LogrLab.testSet, it) }
    }
    val roc = remember(scores) {
        scores?.let { LogrLab.rocCurve(LogrLab.testSet, it) }
    }
    val aucValue = remember(scores) {
        scores?.let { LogrLab.auc(LogrLab.testSet, it) } ?: 0.5
    }
    val best = remember(scores, costFn) {
        scores?.let { LogrLab.bestThreshold(LogrLab.testSet, it, costFn.toDouble()) }
    }
    val theoretical = LogrLab.theoreticalThreshold(costFn.toDouble())
    val currentCost = cm?.let { LogrLab.cost(it, costFn.toDouble()) } ?: 0.0

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
            "Почтовый шлюз: 300 писем на обучение, 200 на контроль, треть из них — фишинг. " +
                "Верхние четыре параметра переобучают модель, нижние два только меняют правило, " +
                "по которому вероятность превращается в решение.",
            fontSize = 14.sp,
            color = textColor.copy(alpha = 0.75f),
            lineHeight = 20.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // ---------------- График 1: распределение вероятностей ----------------
        SectionLabel("Распределение оценок модели", textColor)
        ChartBox(height = 230.dp) {
            if (histogram != null) {
                ProbabilityHistogramCanvas(histogram, threshold)
            }
        }
        LegendRow(textColor)
        Text(
            "Слева — письма, которым модель дала низкую вероятность, справа — высокую. " +
                "Жёлтая линия — порог: всё правее уходит в карантин. Чем дальше разъехались " +
                "два горба, тем лучше обучена модель.",
            fontSize = 12.sp, color = textColor.copy(alpha = 0.6f), lineHeight = 17.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
        )

        // ---------------- График 2: ROC ----------------
        SectionLabel("ROC-кривая и текущая рабочая точка", textColor)
        ChartBox(height = 200.dp) {
            if (roc != null && cm != null) {
                RocCanvas(roc, cm.fpr, cm.recall)
            }
        }
        Text(
            "Кривая зависит только от модели: порог её не меняет, он лишь двигает по ней " +
                "жёлтую точку. AUC = ${"%.3f".format(aucValue)} — вероятность того, что случайное " +
                "фишинговое письмо получит оценку выше случайного легитимного.",
            fontSize = 12.sp, color = textColor.copy(alpha = 0.6f), lineHeight = 17.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
        )

        // ---------------- График 3: веса ----------------
        SectionLabel("Выученные веса признаков", textColor)
        ChartBox(height = 165.dp) {
            if (current != null) {
                WeightsCanvas(current.w)
            }
        }
        Text(
            if (current != null) weightsComment(current.w)
            else "Идёт обучение…",
            fontSize = 12.sp, color = textColor.copy(alpha = 0.6f), lineHeight = 17.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
        )

        if (current?.diverged == true) {
            Text(
                "Модель разошлась: веса ушли в бесконечность. Уменьшите скорость обучения.",
                color = ColorPhish, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }

        // ---------------- Параметры обучения ----------------
        ParamCard(title = "Обучение модели", subtitle = "меняет саму модель", textColor = textColor) {
            SliderRow(
                label = "Скорость обучения",
                value = "%.2f".format(learningRate),
                hint = when {
                    learningRate < 0.2f -> "мелкие шаги — модель не успевает доучиться"
                    learningRate > 8f -> "крупные шаги — спуск перелетает минимум"
                    else -> "рабочая область"
                },
                textColor = textColor
            ) {
                Slider(
                    value = learningRate,
                    onValueChange = { learningRate = it },
                    valueRange = LogrLab.LR_MIN.toFloat()..LogrLab.LR_MAX.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
                )
            }

            SliderRow(
                label = "Число эпох",
                value = "$epochs",
                hint = if (epochs < 25) "модель ещё недоучена" else "хватает, чтобы дойти до минимума",
                textColor = textColor
            ) {
                Slider(
                    value = epochs.toFloat(),
                    onValueChange = { epochs = it.roundToInt() },
                    valueRange = LogrLab.EPOCHS_MIN.toFloat()..LogrLab.EPOCHS_MAX.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
                )
            }

            SliderRow(
                label = "L2-регуляризация",
                value = "%.3f".format(lambda),
                hint = when {
                    lambda < 0.003f -> "штрафа за большие веса почти нет"
                    lambda > 0.08f -> "веса придавлены — модель теряет способность различать"
                    else -> "умеренный штраф, обычно помогает обобщению"
                },
                textColor = textColor
            ) {
                Slider(
                    value = lambda,
                    onValueChange = { lambda = it },
                    valueRange = LogrLab.LAMBDA_MIN.toFloat()..LogrLab.LAMBDA_MAX.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
                )
            }

            SliderRow(
                label = "Вес класса «фишинг» при обучении",
                value = "%.1f".format(wPos) + "×",
                hint = if (wPos < 1.5f) "классы равноправны, вероятности откалиброваны"
                else "модель систематически завышает вероятность фишинга",
                textColor = textColor
            ) {
                Slider(
                    value = wPos,
                    onValueChange = { wPos = it },
                    valueRange = LogrLab.W_POS_MIN.toFloat()..LogrLab.W_POS_MAX.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------------- Решающее правило ----------------
        ParamCard(title = "Решающее правило", subtitle = "модель не переобучается", textColor = textColor) {
            SliderRow(
                label = "Порог классификации",
                value = "%.2f".format(threshold),
                hint = when {
                    threshold < 0.3f -> "карантин работает агрессивно"
                    threshold > 0.7f -> "в карантин уходит только очевидное"
                    else -> "нейтральная настройка"
                },
                textColor = textColor
            ) {
                Slider(
                    value = threshold,
                    onValueChange = { threshold = it },
                    valueRange = LogrLab.THRESHOLD_MIN.toFloat()..LogrLab.THRESHOLD_MAX.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                )
            }

            SliderRow(
                label = "Пропуск дороже ложной тревоги в",
                value = "%.0f".format(costFn) + "×",
                hint = "теоретический порог для калиброванной модели: ${"%.2f".format(theoretical)}",
                textColor = textColor
            ) {
                Slider(
                    value = costFn,
                    onValueChange = { costFn = it },
                    valueRange = LogrLab.COST_FN_MIN.toFloat()..LogrLab.COST_FN_MAX.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                )
            }

            if (best != null) {
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = 0.16f))
                        .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .clickable { threshold = best.threshold.toFloat() }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Поставить лучший порог: ${"%.2f".format(best.threshold)}  (ущерб ${"%.0f".format(best.cost)})",
                        color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------------- Результат ----------------
        if (cm != null && best != null) {
            ResultCard(
                cm = cm,
                aucValue = aucValue,
                currentCost = currentCost,
                bestCost = best.cost,
                costFn = costFn.toDouble(),
                textColor = textColor,
                accent = accent
            )

            Spacer(Modifier.height(14.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = logrInsight(
                            threshold = threshold.toDouble(),
                            wPos = wPos.toDouble(),
                            lambda = lambda.toDouble(),
                            learningRate = learningRate.toDouble(),
                            epochs = epochs,
                            cm = cm,
                            costFn = costFn.toDouble(),
                            currentCost = currentCost,
                            bestThreshold = best.threshold,
                            bestCost = best.cost,
                            theoretical = theoretical,
                            diverged = current?.diverged == true
                        ),
                        color = textColor.copy(alpha = 0.82f),
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    AskChatButton(accent = accent, onClick = {
                        onOpenChat(
                            buildInteractiveChatPrompt(
                                topicTitle,
                                "скорость обучения = ${"%.2f".format(learningRate)}, эпох = $epochs, " +
                                    "L2 = ${"%.3f".format(lambda)}, вес класса «фишинг» = ${"%.1f".format(wPos)}, " +
                                    "порог = ${"%.2f".format(threshold)}, цена пропуска = ${"%.0f".format(costFn)}",
                                "TP=${cm.tp} FP=${cm.fp} TN=${cm.tn} FN=${cm.fn}, " +
                                    "precision=${"%.3f".format(cm.precision)}, recall=${"%.3f".format(cm.recall)}, " +
                                    "F1=${"%.3f".format(cm.f1)}, AUC=${"%.3f".format(aucValue)}, " +
                                    "суммарный ущерб=${"%.0f".format(currentCost)} при лучшем возможном " +
                                    "${"%.0f".format(best.cost)} на пороге ${"%.2f".format(best.threshold)}"
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
// Мелкие переиспользуемые куски раскладки
// =====================================================================

@Composable
private fun SectionLabel(text: String, textColor: Color) {
    Text(
        text,
        color = textColor.copy(alpha = 0.9f),
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

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
private fun LegendRow(textColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LegendDot(ColorLegit, "легитимные", textColor)
        LegendDot(ColorPhish, "фишинг", textColor)
    }
}

@Composable
private fun LegendDot(color: Color, label: String, textColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color.copy(alpha = 0.75f))
        )
        Spacer(Modifier.width(6.dp))
        Text(label, color = textColor.copy(alpha = 0.7f), fontSize = 12.sp)
    }
}

@Composable
private fun ParamCard(
    title: String,
    subtitle: String,
    textColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "— $subtitle",
                    color = textColor.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(6.dp))
            content()
        }
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
        Text(
            label,
            color = textColor,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
        Text(value, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
    slider()
    Text(hint, color = textColor.copy(alpha = 0.55f), fontSize = 11.sp, lineHeight = 15.sp)
}

@Composable
private fun ResultCard(
    cm: LogrLab.ConfusionMatrix,
    aucValue: Double,
    currentCost: Double,
    bestCost: Double,
    costFn: Double,
    textColor: Color,
    accent: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Что получилось на 200 контрольных письмах", color = textColor,
                fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))

            // Матрица ошибок 2x2
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MatrixCell("Поймали фишинг", cm.tp, ColorLegit, "TP", Modifier.weight(1f), textColor)
                MatrixCell("Ложная тревога", cm.fp, ColorAccent, "FP", Modifier.weight(1f), textColor)
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MatrixCell("Верно пропустили", cm.tn, ColorLegit, "TN", Modifier.weight(1f), textColor)
                MatrixCell("ПРОПУСК фишинга", cm.fn, ColorPhish, "FN", Modifier.weight(1f), textColor)
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = textColor.copy(alpha = 0.15f))
            Spacer(Modifier.height(12.dp))

            MetricLine("Precision — доля настоящего фишинга в карантине", "%.3f".format(cm.precision), textColor)
            MetricLine("Recall — какую долю фишинга поймали", "%.3f".format(cm.recall), textColor)
            MetricLine("F1 — их гармоническое среднее", "%.3f".format(cm.f1), textColor)
            MetricLine("AUC — качество модели без учёта порога", "%.3f".format(aucValue), textColor)

            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Accuracy — почти бесполезна в этой задаче",
                    color = textColor.copy(alpha = 0.38f),
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "%.3f".format(cm.accuracy),
                    color = textColor.copy(alpha = 0.38f),
                    fontSize = 13.sp
                )
            }
            Text(
                "Модель, пропускающая вообще всё, получила бы ${"%.2f".format(1.0 - LogrLab.baseRate(LogrLab.testSet))} " +
                    "при нулевом recall.",
                color = textColor.copy(alpha = 0.38f), fontSize = 11.sp, lineHeight = 15.sp
            )

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = textColor.copy(alpha = 0.15f))
            Spacer(Modifier.height(12.dp))

            Text("Суммарный ущерб", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "${cm.fp} ложных тревог × 1 + ${cm.fn} пропусков × ${"%.0f".format(costFn)} = " +
                    "${"%.0f".format(currentCost)}",
                color = textColor.copy(alpha = 0.85f), fontSize = 13.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (currentCost <= bestCost + 0.5)
                    "Это минимум из достижимых при текущей модели."
                else
                    "Минимум при этой же модели — ${"%.0f".format(bestCost)}: " +
                        "вы теряете ${"%.0f".format(currentCost - bestCost)} только из-за выбора порога.",
                color = if (currentCost <= bestCost + 0.5) ColorLegit else accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun MatrixCell(
    label: String,
    value: Int,
    color: Color,
    code: String,
    modifier: Modifier,
    textColor: Color
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Text(code, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(2.dp))
        Text("$value", color = textColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, color = textColor.copy(alpha = 0.65f), fontSize = 11.sp, lineHeight = 14.sp)
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
            label,
            color = textColor.copy(alpha = 0.75f),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f).padding(end = 10.dp)
        )
        Text(value, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

// =====================================================================
// Отрисовка
// =====================================================================

@Composable
private fun ProbabilityHistogramCanvas(hist: LogrLab.ProbaHistogram, threshold: Float) {
    Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        val w = size.width
        val h = size.height
        val bins = hist.legit.size
        val barW = w / bins

        // зона карантина справа от порога
        val thrX = threshold * w
        drawRect(
            color = ColorPhish.copy(alpha = 0.05f),
            topLeft = Offset(thrX, 0f),
            size = Size(w - thrX, h)
        )

        for (k in 0 until bins) {
            val x = k * barW
            val legitH = hist.legit[k].toFloat() / hist.maxCount * h
            val phishH = hist.phish[k].toFloat() / hist.maxCount * h
            // легитимные рисуются от низа вверх, фишинг — поверх, со сдвигом
            drawRect(
                color = ColorLegit.copy(alpha = 0.55f),
                topLeft = Offset(x + barW * 0.06f, h - legitH),
                size = Size(barW * 0.44f, legitH)
            )
            drawRect(
                color = ColorPhish.copy(alpha = 0.6f),
                topLeft = Offset(x + barW * 0.5f, h - phishH),
                size = Size(barW * 0.44f, phishH)
            )
        }

        // линия порога — толстая и яркая: строго вертикальные тонкие линии
        // на Canvas пропадают из-за субпиксельного антиалиасинга
        drawLine(
            color = ColorAccent,
            start = Offset(thrX, 0f),
            end = Offset(thrX, h),
            strokeWidth = designPx(3f)
        )
        // ось
        drawLine(
            color = Color.White.copy(alpha = 0.3f),
            start = Offset(0f, h),
            end = Offset(w, h),
            strokeWidth = designPx(1.5f)
        )
    }
}

@Composable
private fun RocCanvas(points: List<Pair<Double, Double>>, curFpr: Double, curTpr: Double) {
    Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        val side = minOf(size.width, size.height)
        val ox = (size.width - side) / 2f
        val oy = (size.height - side) / 2f

        fun toPx(fpr: Double, tpr: Double) =
            Offset(ox + fpr.toFloat() * side, oy + side - tpr.toFloat() * side)

        // диагональ «случайного угадывания»
        drawLine(
            color = Color.White.copy(alpha = 0.25f),
            start = toPx(0.0, 0.0),
            end = toPx(1.0, 1.0),
            strokeWidth = designPx(1.5f)
        )
        // рамка
        drawRect(
            color = Color.White.copy(alpha = 0.18f),
            topLeft = Offset(ox, oy),
            size = Size(side, side),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = designPx(1.5f))
        )

        var prev: Offset? = null
        for (p in points) {
            val pt = toPx(p.first, p.second)
            prev?.let { drawLine(ColorLegit, it, pt, strokeWidth = designPx(2.5f)) }
            prev = pt
        }

        val cur = toPx(curFpr, curTpr)
        drawCircle(ColorAccent, radius = designPx(7f), center = cur)
        drawCircle(Color.Black.copy(alpha = 0.55f), radius = designPx(3f), center = cur)
    }
}

@Composable
private fun WeightsCanvas(weights: DoubleArray) {
    Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
        val w = size.width
        val h = size.height
        val n = weights.size
        val slot = w / n
        val midY = h / 2f

        var maxAbs = 0.5
        for (v in weights) if (abs(v) > maxAbs) maxAbs = abs(v)

        // нулевая ось
        drawLine(
            color = Color.White.copy(alpha = 0.35f),
            start = Offset(0f, midY),
            end = Offset(w, midY),
            strokeWidth = designPx(1.5f)
        )

        for (j in 0 until n) {
            val v = weights[j]
            val barH = (abs(v) / maxAbs * (h / 2f - designPx(10f))).toFloat()
            val x = j * slot + slot * 0.22f
            val barW = slot * 0.56f
            val positive = v >= 0.0
            drawRect(
                color = if (positive) ColorPhish.copy(alpha = 0.7f) else ColorLegit.copy(alpha = 0.7f),
                topLeft = Offset(x, if (positive) midY - barH else midY),
                size = Size(barW, barH)
            )
        }
    }
}

// =====================================================================
// Живые пояснения
// =====================================================================

private fun weightsComment(w: DoubleArray): String {
    var maxIdx = 0
    for (j in w.indices) if (abs(w[j]) > abs(w[maxIdx])) maxIdx = j
    val maxAbs = abs(w[maxIdx])
    if (maxAbs < 0.15) {
        return "Веса почти нулевые: модель ещё ничего не выучила — либо мало эпох, " +
            "либо регуляризация задавила её полностью."
    }
    val ageSign = if (w[1] < 0) "отрицательный, как и должно быть: чем старше домен, тем меньше подозрений"
    else "положительный — это странно и обычно означает, что модель недоучена"
    return "Красные столбики тянут решение к «фишингу», зелёные — к «легитимно». " +
        "Сильнее всего сейчас влияет признак «${LogrLab.featureNames[maxIdx]}» " +
        "(вес ${"%.2f".format(w[maxIdx])}). Вес возраста домена $ageSign."
}

private fun logrInsight(
    threshold: Double,
    wPos: Double,
    lambda: Double,
    learningRate: Double,
    epochs: Int,
    cm: LogrLab.ConfusionMatrix,
    costFn: Double,
    currentCost: Double,
    bestThreshold: Double,
    bestCost: Double,
    theoretical: Double,
    diverged: Boolean
): String {
    if (diverged) {
        return "Модель разошлась: при такой скорости обучения шаги градиентного спуска " +
            "перелетают минимум и веса уходят в бесконечность. Уменьшите скорость обучения."
    }

    val parts = ArrayList<String>()

    // 1. Состояние обучения
    if (cm.recall < 0.05) {
        parts.add(
            "Модель не поймала практически ничего: она отправляет в карантин ноль писем. " +
                "Так выглядит недоученная или перерегуляризованная модель — обратите внимание, " +
                "что accuracy при этом всё ещё около 0,68."
        )
    } else if (epochs < 25 || learningRate < 0.2) {
        parts.add(
            "Обучение ещё не дошло до минимума: recall заметно ниже, чем может быть. " +
                "Добавьте эпох или увеличьте скорость обучения."
        )
    } else if (learningRate > 8.0) {
        parts.add(
            "Скорость обучения очень велика: спуск перепрыгивает минимум, и метрики " +
                "начинают скакать от малейшего сдвига слайдера, вместо того чтобы плавно улучшаться."
        )
    }

    // 2. Регуляризация
    if (lambda > 0.08) {
        parts.add(
            "L2-штраф сейчас настолько велик, что веса придавлены к нулю: модель " +
                "теряет способность различать классы. Регуляризация лечит переобучение, " +
                "но в избытке она просто выключает модель."
        )
    }

    // 3. Порог
    val thresholdText = when {
        threshold < 0.3 ->
            "Низкий порог: карантин работает агрессивно, пропусков мало (recall " +
                "${"%.2f".format(cm.recall)}), но ложных тревог ${cm.fp} — аналитики будут разбирать их руками."
        threshold > 0.7 ->
            "Высокий порог: в карантин уходит только то, в чём модель почти уверена. " +
                "Ложных тревог всего ${cm.fp}, зато пропущено ${cm.fn} фишинговых писем."
        else ->
            "Порог около середины: ${cm.fp} ложных тревог против ${cm.fn} пропусков."
    }
    parts.add(thresholdText)

    // 4. Вес класса и калиброванность
    if (wPos > 1.5) {
        parts.add(
            "Вы подняли вес класса «фишинг» до ${"%.1f".format(wPos)}×. Это меняет саму модель, " +
                "а не правило поверх неё — и сознательно портит калиброванность: число 0,7 больше " +
                "не означает «70% таких писем окажутся фишингом». Поэтому теоретическая формула " +
                "порога 1/(1+C) здесь перестаёт работать."
        )
    }

    // 5. Стоимость и разрыв с теорией
    val gap = abs(bestThreshold - theoretical)
    if (currentCost > bestCost + 0.5) {
        parts.add(
            "При цене пропуска ${"%.0f".format(costFn)}× текущий ущерб ${"%.0f".format(currentCost)}, " +
                "а лучший достижимый — ${"%.0f".format(bestCost)} на пороге ${"%.2f".format(bestThreshold)}. " +
                "Модель та же самая: разницу даёт только выбор порога."
        )
    } else {
        parts.add(
            "Порог сейчас оптимален для цены пропуска ${"%.0f".format(costFn)}×: " +
                "ущерб ${"%.0f".format(currentCost)} — минимум из достижимых на этой модели."
        )
    }
    if (gap > 0.12) {
        parts.add(
            "Формула даёт порог ${"%.2f".format(theoretical)}, а перебор — ${"%.2f".format(bestThreshold)}. " +
                "Расхождение в ${"%.2f".format(gap)} — это мера того, насколько модель недокалибрована: " +
                "формула верна только тогда, когда вероятности честные."
        )
    }

    return parts.joinToString(" ")
}
