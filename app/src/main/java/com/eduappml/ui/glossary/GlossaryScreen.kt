package com.eduappml.ui.glossary

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eduappml.ui.common.BottomPillButton
import kotlin.math.exp

data class GlossaryItem(
    val abbreviation: String,
    val fullName: String,
    val description: String,
    val graphType: GraphType
)

enum class GraphType {
    LR, LogR, KNN, NB, SVM, DT, RF, GB, KM
}

private val glossaryData = listOf(
    GlossaryItem("LR", "Линейная регрессия", "Находит линию наилучшего соответствия.", GraphType.LR),
    GlossaryItem("LogR", "Логистическая регрессия", "Прогнозирует бинарные вероятности.", GraphType.LogR),
    GlossaryItem("KNN", "Метод k-ближайших соседей", "Классифицирует голосованием.", GraphType.KNN),
    GlossaryItem("NB", "Наивный Байес", "Вероятностная классификация.", GraphType.NB),
    GlossaryItem("SVM", "Метод опорных векторов", "Разделяет с помощью гиперплоскости.", GraphType.SVM),
    GlossaryItem("DT", "Дерево решений", "Классификация на основе правил.", GraphType.DT),
    GlossaryItem("RF", "Случайный лес", "Объединяет множество деревьев.", GraphType.RF),
    GlossaryItem("GB", "Градиентный бустинг", "Обучается на последовательных деревьях.", GraphType.GB),
    GlossaryItem("KM", "Кластеризация k-средних", "Группирует данные в 'k' кластеров.", GraphType.KM)
)

private val auraColors = mapOf(
    "LR" to Color(0xFFFF6B6B),
    "LogR" to Color(0xFFFFD93D),
    "KNN" to Color(0xFF6BCB77),
    "NB" to Color(0xFF4D96FF),
    "SVM" to Color(0xFFB5179E),
    "DT" to Color(0xFFFF914D),
    "RF" to Color(0xFF9D4EDD),
    "GB" to Color(0xFF00C2A8),
    "KM" to Color(0xFFE63946)
)

@Composable
fun GlossaryScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    Box(modifier = modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 16.dp)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "ГЛОССАРИЙ",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            Text(
                text = "Полные названия ключевых алгоритмов",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(glossaryData) { item ->
                    GlossaryRow(item)
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxSize().padding(bottom = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            BottomPillButton(
                text = "Назад к карте знаний",
                onClick = onBack,
                modifier = Modifier.wrapContentSize()
            )
        }
    }
}

@Composable
private fun GlossaryRow(item: GlossaryItem) {
    val auraColor = auraColors[item.abbreviation] ?: Color.White
    val bubbleSize = 36.dp
    val graphSize = 40.dp

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(bubbleSize)
                .drawBehind {
                    val radius = size.minDimension / 2f
                    drawCircle(
                        color = auraColor.copy(alpha = 0.25f),
                        radius = radius * 1.3f,
                        center = center
                    )
                    drawCircle(
                        color = auraColor.copy(alpha = 0.15f),
                        radius = radius * 1.6f,
                        center = center
                    )
                }
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.16f))
                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = item.abbreviation,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
        ) {
            Text(
                text = item.fullName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = item.description,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.9f),
                lineHeight = 18.sp
            )
        }

        Box(
            modifier = Modifier
                .size(graphSize)
                .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .padding(2.dp)
        ) {
            MiniGraph(graphType = item.graphType)
        }
    }
}

@Composable
private fun MiniGraph(graphType: GraphType) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val c = Color.White

        when (graphType) {
            GraphType.LR -> {
                drawLine(c, Offset(4f, h - 4f), Offset(w - 4f, 4f), strokeWidth = 2f)
                val pts = listOf(
                    0.15f to 0.75f, 0.25f to 0.6f, 0.35f to 0.55f,
                    0.55f to 0.35f, 0.7f to 0.25f, 0.85f to 0.15f
                )
                pts.forEach { p ->
                    drawCircle(c, 3f, Offset(p.first * w, p.second * h))
                }
            }
            GraphType.LogR -> {
                val path = Path()
                path.moveTo(0f, h * 0.9f)
                for (x in 0..100) {
                    val t = x / 100f
                    val y = h / (1f + exp(-10f * (t - 0.5f)))
                    path.lineTo(t * w, y)
                }
                drawPath(path, c, style = Stroke(width = 2.5f))
                drawLine(
                    c.copy(alpha = 0.4f),
                    Offset(w * 0.5f, 0f),
                    Offset(w * 0.5f, h),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                )
            }
            GraphType.KNN -> {
                val pts = listOf(
                    0.2f to 0.3f, 0.25f to 0.55f, 0.35f to 0.7f,
                    0.55f to 0.35f, 0.6f to 0.65f, 0.75f to 0.5f, 0.8f to 0.75f
                )
                pts.forEach {
                    drawCircle(c, 3f, Offset(it.first * w, it.second * h))
                }
            }
            GraphType.NB -> {
                val path = Path()
                path.moveTo(0f, h * 0.9f)
                for (x in 0..100) {
                    val t = x / 100f
                    val g = exp(-((t - 0.5f) * (t - 0.5f)) * 10f)
                    val y = h * (0.9f - g * 0.8f)
                    path.lineTo(t * w, y)
                }
                drawPath(path, c, style = Stroke(width = 2.5f))
            }
            GraphType.SVM -> {
                drawLine(c, Offset(w * 0.25f, h * 0.8f), Offset(w * 0.75f, h * 0.2f), strokeWidth = 2f)
                val left = listOf(0.15f to 0.7f, 0.25f to 0.6f, 0.3f to 0.75f)
                val right = listOf(0.7f to 0.35f, 0.8f to 0.45f, 0.75f to 0.25f)
                left.forEach { drawCircle(c, 3f, Offset(it.first * w, it.second * h)) }
                right.forEach { drawCircle(c, 3f, Offset(it.first * w, it.second * h)) }
            }
            GraphType.DT -> {
                val root = Offset(w / 2, h * 0.15f)
                val l1 = Offset(w * 0.3f, h * 0.45f)
                val r1 = Offset(w * 0.7f, h * 0.45f)
                val l2 = Offset(w * 0.2f, h * 0.75f)
                val m2 = Offset(w * 0.4f, h * 0.75f)
                val r2 = Offset(w * 0.6f, h * 0.75f)
                val rr2 = Offset(w * 0.8f, h * 0.75f)
                drawLine(c, root, l1, 2f)
                drawLine(c, root, r1, 2f)
                drawLine(c, l1, l2, 2f)
                drawLine(c, l1, m2, 2f)
                drawLine(c, r1, r2, 2f)
                drawLine(c, r1, rr2, 2f)
                listOf(root, l1, r1, l2, m2, r2, rr2).forEach {
                    drawCircle(c, 3f, it)
                }
            }
            GraphType.RF -> {
                repeat(3) { i ->
                    val x = w * (0.3f + i * 0.2f)
                    drawLine(c, Offset(x, h * 0.8f), Offset(x, h * 0.5f), 2f)
                    drawCircle(c, 8f, Offset(x, h * 0.4f), style = Stroke(2f))
                    drawCircle(c, 6f, Offset(x - 6f, h * 0.45f), style = Stroke(2f))
                    drawCircle(c, 6f, Offset(x + 6f, h * 0.45f), style = Stroke(2f))
                }
            }
            GraphType.GB -> {
                val pts = listOf(
                    0f to 0.9f, 0.3f to 0.7f, 0.5f to 0.6f,
                    0.7f to 0.35f, 0.9f to 0.15f
                )
                for (i in 0 until pts.size - 1) {
                    val a = pts[i]
                    val b = pts[i + 1]
                    drawLine(c, Offset(a.first * w, a.second * h), Offset(b.first * w, a.second * h), 2f)
                    drawLine(c, Offset(b.first * w, a.second * h), Offset(b.first * w, b.second * h), 2f)
                }
                drawLine(c, Offset(0f, h * 0.9f), Offset(w, h * 0.9f), 2f)
            }
            GraphType.KM -> {
                val clusters = listOf(
                    Triple(0.3f, 0.35f, "k1"),
                    Triple(0.7f, 0.35f, "k2"),
                    Triple(0.5f, 0.7f, "k3")
                )
                clusters.forEach { cl ->
                    val cx = cl.first * w
                    val cy = cl.second * h
                    for (i in -1..1) {
                        for (j in -1..1) {
                            drawCircle(c, 2.5f, Offset(cx + i * 6f, cy + j * 6f))
                        }
                    }
                    drawCircle(
                        c, 14f, Offset(cx, cy),
                        style = Stroke(1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
                    )
                }
            }
        }
    }
}