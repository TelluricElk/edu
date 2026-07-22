package com.eduappml.ui.svm

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eduappml.ui.common.LessonScaffold
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun SvmInteractive(
    modifier: Modifier = Modifier,
    title: String?,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val textColor = Color.White
    val accent = Color(0xFFB5179E)

    var c by remember { mutableFloatStateOf(1f) }
    var kernel by remember { mutableStateOf(SvmKernel.LINEAR) }
    var gamma by remember { mutableFloatStateOf(0.3f) }

    var model by remember { mutableStateOf(SvmLab.train(c, kernel, gamma, 600)) }
    LaunchedEffect(c, kernel, gamma) {
        delay(150)
        model = SvmLab.train(c, kernel, gamma, 600)
    }

    val accuracy = remember(model) { SvmLab.accuracy(model, SvmLab.testSet) }
    val svCount = remember(model) { SvmLab.supportVectorCount(model) }

    LessonScaffold(
        eyebrow = "Интерактив",
        title = title ?: "SVM",
        onBack = onBack,
        onNext = onNext,
        nextLabel = "К решению →",
        accent = accent,
        modifier = modifier
    ) {
        Text(
            "Подберите C и ядро так, чтобы граница уверенно разделяла классы. Опорные векторы — точки, обведённые кольцом. Для линейного ядра пунктиром показан сам зазор (margin).",
            fontSize = 14.sp, color = textColor.copy(alpha = 0.75f), modifier = Modifier.padding(bottom = 16.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
        ) {
            SvmCanvas(model = model)
        }

        Spacer(Modifier.height(10.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Параметры обучения", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))

                Text("C (регуляризация) = ${"%.2f".format(c)}", color = textColor, fontSize = 14.sp)
                Slider(value = c, onValueChange = { c = it }, valueRange = 0.05f..5f,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White))

                Spacer(Modifier.height(8.dp))
                Text("Ядро", color = textColor, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.08f))) {
                    SvmKernel.entries.forEach { k ->
                        val selected = k == kernel
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) Color.White.copy(alpha = 0.22f) else Color.Transparent)
                                .padding(vertical = 10.dp)
                                .pointerInput(k) { detectTapGestures { kernel = k } },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(k.label, color = Color.White, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }

                if (kernel == SvmKernel.RBF) {
                    Spacer(Modifier.height(10.dp))
                    Text("Gamma (γ) = ${"%.2f".format(gamma)}", color = textColor, fontSize = 14.sp)
                    Slider(value = gamma, onValueChange = { gamma = it }, valueRange = 0.02f..1.2f,
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White))
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = textColor.copy(alpha = 0.15f))
                Spacer(Modifier.height(12.dp))

                Text("Точность на контрольной выборке: ${(accuracy * 100).roundToInt()}%", color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Число опорных векторов: $svCount из ${SvmLab.trainSet.size}", color = textColor.copy(alpha = 0.8f), fontSize = 13.sp)

                Spacer(Modifier.height(8.dp))
                Text(
                    text = svmInsight(c, kernel, gamma, svCount, SvmLab.trainSet.size),
                    color = textColor.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

/** Живое текстовое пояснение, меняющееся вместе с параметрами — как в теме kNN. */
private fun svmInsight(c: Float, kernel: SvmKernel, gamma: Float, svCount: Int, total: Int): String {
    val svShare = svCount.toFloat() / total
    val cText = when {
        c < 0.3f -> "Маленькое C — модель терпима к нарушениям, зазор широкий, граница устойчивее к шуму."
        c > 2.5f -> "Большое C — модель жёстко наказывает за каждое нарушение, зазор узкий, риск переобучения растёт."
        else -> "C в среднем диапазоне — разумный компромисс между шириной зазора и числом ошибок."
    }
    val kernelText = when (kernel) {
        SvmKernel.LINEAR -> "Линейное ядро может провести только прямую границу."
        SvmKernel.RBF -> if (gamma > 0.7f) {
            "Большая gamma — граница подстраивается под каждую точку почти индивидуально, риск переобучения высок."
        } else if (gamma < 0.15f) {
            "Маленькая gamma — граница очень гладкая, почти как линейная."
        } else {
            "Ядро RBF с этой gamma позволяет границе аккуратно изгибаться вокруг скоплений точек."
        }
    }
    val svText = when {
        svShare > 0.7f -> "Опорными стали почти все точки — граница слишком «нервная», это тоже признак возможного переобучения."
        svShare < 0.15f -> "Опорных векторов мало — граница опирается лишь на самые пограничные случаи, это хороший знак."
        else -> "Умеренная доля опорных векторов — обычная ситуация для этих данных."
    }
    return "$cText $kernelText $svText"
}

@Composable
private fun SvmCanvas(model: SvmLab.Model) {
    val gridSteps = 24
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cellW = w / gridSteps
        val cellH = h / gridSteps

        fun toPx(x1: Float, x2: Float) = Offset((x1 / SvmLab.FEATURE_MAX) * w, h - (x2 / SvmLab.FEATURE_MAX) * h)

        for (gx in 0 until gridSteps) {
            for (gy in 0 until gridSteps) {
                val x1 = ((gx + 0.5f) / gridSteps) * SvmLab.FEATURE_MAX
                val x2 = (1f - (gy + 0.5f) / gridSteps) * SvmLab.FEATURE_MAX
                val cls = SvmLab.classify(model, x1, x2)
                val color = if (cls == 1) Color(0xFF4D96FF) else Color(0xFFFF914D)
                drawRect(color.copy(alpha = 0.16f), topLeft = Offset(gx * cellW, gy * cellH), size = androidx.compose.ui.geometry.Size(cellW + 1f, cellH + 1f))
            }
        }

        // Для линейного ядра явно рисуем границу и зазор (margin) — две пунктирные линии
        // по обе стороны от сплошной границы, буквально то, что SVM максимизирует.
        SvmLab.linearWeights(model)?.let { (w1v, w2v, biasV) ->
            if (kotlin.math.abs(w2v) > 1e-4f) {
                fun pointsFor(k: Float): Pair<Offset, Offset> {
                    val x1a = SvmLab.FEATURE_MIN
                    val x2a = (k - biasV - w1v * x1a) / w2v
                    val x1b = SvmLab.FEATURE_MAX
                    val x2b = (k - biasV - w1v * x1b) / w2v
                    return toPx(x1a, x2a) to toPx(x1b, x2b)
                }
                val dashed = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                val (b1, b2) = pointsFor(0f)
                drawLine(Color.White, b1, b2, strokeWidth = 2.5f)
                val (m1a, m1b) = pointsFor(1f)
                drawLine(Color.White.copy(alpha = 0.55f), m1a, m1b, strokeWidth = 1.5f, pathEffect = dashed)
                val (m2a, m2b) = pointsFor(-1f)
                drawLine(Color.White.copy(alpha = 0.55f), m2a, m2b, strokeWidth = 1.5f, pathEffect = dashed)
            }
        }

        SvmLab.trainSet.forEachIndexed { idx, p ->
            val pt = toPx(p.x1, p.x2)
            val color = if (p.label == 1) Color(0xFF4D96FF) else Color(0xFFFF914D)
            drawCircle(color, radius = 6f, center = pt)
            if (model.alpha[idx] != 0f) {
                drawCircle(Color.White, radius = 10f, center = pt, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6f))
            }
        }
    }
}
