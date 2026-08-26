package com.eduappml.ui.rf

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.eduappml.ui.dt.CandidatePoint
import com.eduappml.ui.dt.DtLabMilitary
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import com.eduappml.ui.common.designPx

/**
 * Военный вариант экрана "Интерактив" для темы случайного леса: та же
 * логика, что у [RfInteractive] (который остаётся нетронутым и больше не
 * вызывается для id = "rf"), только с примером зачисления кандидата.
 */
@Composable
fun RfInteractiveMilitary(
    modifier: Modifier = Modifier,
    title: String?,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onOpenChat: (String) -> Unit = {}
) {
    val textColor = Color.White
    val accent = Color(0xFF9D4EDD)
    val topicTitle = title ?: "Случайный лес"

    var nTrees by remember { mutableIntStateOf(15) }
    var maxDepth by remember { mutableIntStateOf(4) }

    var forest by remember { mutableStateOf(RfLabMilitary.trainForest(nTrees, maxDepth)) }
    LaunchedEffect(nTrees, maxDepth) {
        delay(150)
        forest = withContext(kotlinx.coroutines.Dispatchers.Default) {
            RfLabMilitary.trainForest(nTrees, maxDepth)
        }
    }

    val forestAcc = remember(forest) { RfLabMilitary.accuracy(forest, RfLabMilitary.testSet) }
    val singleTreeAcc = remember(maxDepth) {
        val single = DtLabMilitary.buildTree(DtLabMilitary.trainSet, com.eduappml.ui.dt.DtCriterion.GINI, maxDepth, 4)
        DtLabMilitary.accuracy(single, DtLabMilitary.testSet)
    }

    LessonScaffold(
        eyebrow = "Интерактив",
        title = title ?: "Случайный лес",
        onBack = onBack,
        onNext = onNext,
        nextLabel = "К решению →",
        accent = accent,
        modifier = modifier
    ) {
        Text(
            "Сравните точность одного дерева и всего леса на тех же данных при одинаковой глубине.",
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
            RfCanvasMilitary(forest = forest)
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "Несколько отдельных деревьев леса — видно, что каждое проводит немного свою границу:",
            color = textColor.copy(alpha = 0.7f), fontSize = 12.5.sp, modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            forest.take(5).forEach { tree ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                ) {
                    MiniTreeCanvasMilitary(tree = tree)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Параметры обучения", color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))

                Text("Число деревьев (n_estimators) = $nTrees", color = textColor, fontSize = 14.sp)
                Slider(value = nTrees.toFloat(), onValueChange = { nTrees = it.roundToInt() }, valueRange = 1f..40f,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White))

                Spacer(Modifier.height(8.dp))
                Text("Максимальная глубина дерева = $maxDepth", color = textColor, fontSize = 14.sp)
                Slider(value = maxDepth.toFloat(), onValueChange = { maxDepth = it.roundToInt() }, valueRange = 1f..8f, steps = 6,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White))

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = textColor.copy(alpha = 0.15f))
                Spacer(Modifier.height(12.dp))

                Text("Точность одного дерева: ${(singleTreeAcc * 100).roundToInt()}%", color = textColor.copy(alpha = 0.8f), fontSize = 14.sp)
                Text("Точность всего леса: ${(forestAcc * 100).roundToInt()}%", color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)

                Spacer(Modifier.height(8.dp))
                Text(
                    text = rfInsightMilitary(nTrees, forestAcc, singleTreeAcc),
                    color = textColor.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(10.dp))
                AskChatButton(accent = accent, onClick = {
                    onOpenChat(
                        buildInteractiveChatPrompt(
                            topicTitle,
                            "число деревьев = $nTrees, максимальная глубина = $maxDepth",
                            "точность одного дерева ${(singleTreeAcc * 100).roundToInt()}%, точность леса ${(forestAcc * 100).roundToInt()}%"
                        )
                    )
                })
            }
        }
    }
}

/** Живое текстовое пояснение, меняющееся вместе с числом деревьев. */
private fun rfInsightMilitary(nTrees: Int, forestAcc: Float, singleTreeAcc: Float): String {
    val countText = when {
        nTrees <= 3 -> "При таком малом числе деревьев лес ведёт себя почти как одно дерево — усреднение почти не работает."
        nTrees >= 25 -> "При таком большом числе деревьев точность обычно уже стабилизировалась — добавление ещё деревьев мало что меняет."
        else -> "Среднее число деревьев — усреднение уже заметно сглаживает случайные ошибки отдельных деревьев."
    }
    val diff = forestAcc - singleTreeAcc
    val compareText = when {
        diff > 0.05f -> "Лес заметно точнее одного дерева на этих данных — усреднение явно помогает."
        diff < -0.02f -> "Сейчас лес не выигрывает у одного дерева — на таких простых данных с двумя признаками разница может быть небольшой."
        else -> "Лес и одно дерево показывают близкую точность — усреднение здесь скорее стабилизирует результат, чем резко улучшает его."
    }
    return "$countText $compareText"
}

@Composable
private fun MiniTreeCanvasMilitary(tree: com.eduappml.ui.dt.DtNodeMilitary) {
    val gridSteps = 10
    Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
        val w = size.width
        val h = size.height
        val cellW = w / gridSteps
        val cellH = h / gridSteps
        for (gx in 0 until gridSteps) {
            for (gy in 0 until gridSteps) {
                val age = DtLabMilitary.AGE_MIN + (gx + 0.5f) / gridSteps * (DtLabMilitary.AGE_MAX - DtLabMilitary.AGE_MIN)
                val score = DtLabMilitary.SCORE_MIN + (1f - (gy + 0.5f) / gridSteps) * (DtLabMilitary.SCORE_MAX - DtLabMilitary.SCORE_MIN)
                val admitted = DtLabMilitary.predict(tree, CandidatePoint(age, score, false))
                val color = if (admitted) Color(0xFF6BCB77) else Color(0xFFFF6B6B)
                drawRect(color.copy(alpha = 0.5f), topLeft = Offset(gx * cellW, gy * cellH), size = androidx.compose.ui.geometry.Size(cellW + 1f, cellH + 1f))
            }
        }
    }
}

@Composable
private fun RfCanvasMilitary(forest: List<com.eduappml.ui.dt.DtNodeMilitary>) {
    val gridSteps = 22
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cellW = w / gridSteps
        val cellH = h / gridSteps

        fun toPx(age: Float, score: Float) = Offset(
            ((age - DtLabMilitary.AGE_MIN) / (DtLabMilitary.AGE_MAX - DtLabMilitary.AGE_MIN)) * w,
            h - ((score - DtLabMilitary.SCORE_MIN) / (DtLabMilitary.SCORE_MAX - DtLabMilitary.SCORE_MIN)) * h
        )

        for (gx in 0 until gridSteps) {
            for (gy in 0 until gridSteps) {
                val age = DtLabMilitary.AGE_MIN + (gx + 0.5f) / gridSteps * (DtLabMilitary.AGE_MAX - DtLabMilitary.AGE_MIN)
                val score = DtLabMilitary.SCORE_MIN + (1f - (gy + 0.5f) / gridSteps) * (DtLabMilitary.SCORE_MAX - DtLabMilitary.SCORE_MIN)
                val admitted = RfLabMilitary.predictForest(forest, CandidatePoint(age, score, false))
                val color = if (admitted) Color(0xFF6BCB77) else Color(0xFFFF6B6B)
                drawRect(color.copy(alpha = 0.16f), topLeft = Offset(gx * cellW, gy * cellH), size = androidx.compose.ui.geometry.Size(cellW + 1f, cellH + 1f))
            }
        }

        RfLabMilitary.trainSet.forEach { p ->
            val pt = toPx(p.age, p.examScore)
            val color = if (p.admitted) Color(0xFF6BCB77) else Color(0xFFFF6B6B)
            drawCircle(color, radius = designPx(5f), center = pt)
        }
    }
}
