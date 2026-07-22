package com.eduappml.ui.dt

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun DtInteractive(
    modifier: Modifier = Modifier,
    title: String?,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val textColor = Color.White
    val accent = Color(0xFFFF914D)

    var maxDepth by remember { mutableIntStateOf(3) }
    var criterion by remember { mutableStateOf(DtCriterion.GINI) }

    var tree by remember { mutableStateOf(DtLab.buildTree(DtLab.trainSet, criterion, maxDepth, 4)) }
    LaunchedEffect(maxDepth, criterion) {
        delay(100)
        tree = withContext(kotlinx.coroutines.Dispatchers.Default) {
            DtLab.buildTree(DtLab.trainSet, criterion, maxDepth, 4)
        }
    }

    val trainAcc = remember(tree) { DtLab.accuracy(tree, DtLab.trainSet) }
    val testAcc = remember(tree) { DtLab.accuracy(tree, DtLab.testSet) }
    val leaves = remember(tree) { DtLab.leafCount(tree) }

    LessonScaffold(
        eyebrow = "Интерактив",
        title = title ?: "Дерево решений",
        onBack = onBack,
        onNext = onNext,
        nextLabel = "К решению →",
        accent = accent,
        modifier = modifier
    ) {
        Text(
            "Увеличивайте глубину и следите за разрывом между точностью на обучающей и контрольной выборках — это и есть переобучение.",
            fontSize = 14.sp, color = textColor.copy(alpha = 0.75f), modifier = Modifier.padding(bottom = 16.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
        ) {
            DtCanvas(tree = tree)
        }

        Spacer(Modifier.height(14.dp))
        Text("Структура дерева (листайте по горизонтали)", color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                .padding(vertical = 10.dp)
                .horizontalScroll(rememberScrollState())
        ) {
            DtTreeNodeView(node = tree, textColor = textColor)
        }

        Spacer(Modifier.height(10.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Параметры обучения", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))

                Text("Максимальная глубина = $maxDepth", color = textColor, fontSize = 14.sp)
                Slider(value = maxDepth.toFloat(), onValueChange = { maxDepth = it.roundToInt() }, valueRange = 1f..8f, steps = 6,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White))

                Spacer(Modifier.height(8.dp))
                Text("Критерий разбиения", color = textColor, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.08f))) {
                    DtCriterion.entries.forEach { crit ->
                        val selected = crit == criterion
                        Box(
                            modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) Color.White.copy(alpha = 0.22f) else Color.Transparent)
                                .padding(vertical = 10.dp)
                                .pointerInput(crit) { detectTapGestures { criterion = crit } },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(crit.label, color = Color.White, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = textColor.copy(alpha = 0.15f))
                Spacer(Modifier.height(12.dp))

                Text("Точность на обучающей выборке: ${(trainAcc * 100).roundToInt()}%", color = textColor, fontSize = 14.sp)
                Text("Точность на контрольной выборке: ${(testAcc * 100).roundToInt()}%", color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Число листьев: $leaves", color = textColor.copy(alpha = 0.7f), fontSize = 13.sp)
                if (trainAcc - testAcc > 0.12f) {
                    Spacer(Modifier.height(6.dp))
                    Text("Разрыв между обучающей и контрольной точностью растёт — похоже на переобучение.", color = Color(0xFFFF6B6B), fontSize = 13.sp)
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = dtInsight(maxDepth, leaves, trainAcc, testAcc),
                    color = textColor.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

/** Живое текстовое пояснение, меняющееся вместе с глубиной и числом листьев — как в теме kNN. */
private fun dtInsight(maxDepth: Int, leaves: Int, trainAcc: Float, testAcc: Float): String {
    val depthText = when {
        maxDepth <= 2 -> "Малая глубина — дерево видит только самые грубые закономерности, может недообучаться."
        maxDepth >= 6 -> "Большая глубина — дерево дробит пространство на множество мелких областей, вплоть до отдельных точек."
        else -> "Средняя глубина обычно даёт разумный баланс между простотой и точностью."
    }
    val leavesText = "Сейчас в дереве $leaves лист(ьев) — каждый лист это отдельное правило вида «если … и … то ответ»."
    val gapText = if (trainAcc - testAcc > 0.12f) {
        "Обучающая точность заметно выше контрольной — дерево запомнило частности обучающей выборки, а не только общую закономерность."
    } else {
        "Разрыв между обучающей и контрольной точностью небольшой — дерево скорее обобщает, чем запоминает."
    }
    return "$depthText $leavesText $gapText"
}

/**
 * Настоящая структура дерева, отрисованная вложенными Box/Column/Row —
 * без ручных вычислений координат: Compose сам раскладывает узлы.
 * Дерево может быть широким при большой глубине — контейнер снаружи
 * прокручивается по горизонтали.
 */
@Composable
private fun DtTreeNodeView(node: DtNode, textColor: Color) {
    if (node.prediction != null) {
        val approved = node.prediction == true
        val color = if (approved) Color(0xFF6BCB77) else Color(0xFFFF6B6B)
        Box(
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.25f))
                .border(1.dp, color, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(if (approved) "Одобрить" else "Отказать", color = textColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    } else {
        val featureName = if (node.feature == 0) "возраст" else "доход"
        val thresholdText = node.threshold?.roundToInt() ?: 0
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 4.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("$featureName ≤ $thresholdText", color = textColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("да", color = textColor.copy(alpha = 0.5f), fontSize = 9.sp)
                    Spacer(Modifier.height(2.dp))
                    DtTreeNodeView(node = node.left!!, textColor = textColor)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("нет", color = textColor.copy(alpha = 0.5f), fontSize = 9.sp)
                    Spacer(Modifier.height(2.dp))
                    DtTreeNodeView(node = node.right!!, textColor = textColor)
                }
            }
        }
    }
}

@Composable
private fun DtCanvas(tree: DtNode) {
    val gridSteps = 24
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cellW = w / gridSteps
        val cellH = h / gridSteps

        fun toPx(age: Float, income: Float) = Offset(
            ((age - DtLab.AGE_MIN) / (DtLab.AGE_MAX - DtLab.AGE_MIN)) * w,
            h - ((income - DtLab.INCOME_MIN) / (DtLab.INCOME_MAX - DtLab.INCOME_MIN)) * h
        )

        for (gx in 0 until gridSteps) {
            for (gy in 0 until gridSteps) {
                val age = DtLab.AGE_MIN + (gx + 0.5f) / gridSteps * (DtLab.AGE_MAX - DtLab.AGE_MIN)
                val income = DtLab.INCOME_MIN + (1f - (gy + 0.5f) / gridSteps) * (DtLab.INCOME_MAX - DtLab.INCOME_MIN)
                val approved = DtLab.predict(tree, CreditPoint(age, income, false))
                val color = if (approved) Color(0xFF6BCB77) else Color(0xFFFF6B6B)
                drawRect(color.copy(alpha = 0.16f), topLeft = Offset(gx * cellW, gy * cellH), size = androidx.compose.ui.geometry.Size(cellW + 1f, cellH + 1f))
            }
        }

        DtLab.trainSet.forEach { p ->
            val pt = toPx(p.age, p.income)
            val color = if (p.approved) Color(0xFF6BCB77) else Color(0xFFFF6B6B)
            drawCircle(color, radius = 5f, center = pt)
            drawCircle(Color.White.copy(alpha = 0.5f), radius = 5f, center = pt, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f))
        }
    }
}
