package com.eduappml.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.exp
import kotlin.random.Random

/**
 * Единая строка глоссария — используется и в классическом ("lr", "logr", ...),
 * и в нейросетевом ("fc", "cnn", ...) разделах. Один источник стиля вместо
 * двух независимо написанных вариантов — гарантирует, что оба глоссария
 * выглядят как одно целое, а не как два разных экрана.
 */
@Composable
fun GlossaryRow(
    abbreviation: String,
    fullName: String,
    description: String,
    accent: Color,
    graphId: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .drawBehind {
                    drawCircle(accent.copy(alpha = 0.28f), radius = size.minDimension * 0.78f, center = center)
                }
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
                .border(1.dp, accent.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = abbreviation, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(text = fullName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(text = description, fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.75f), lineHeight = 16.sp)
        }

        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(11.dp))
                .padding(7.dp)
        ) {
            MiniGraph(graphId = graphId, accent = accent)
        }
    }
}

/** Маленький, узнаваемый с первого взгляда рисунок сути алгоритма — по цвету темы. */
@Composable
fun MiniGraph(graphId: String, accent: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        when (graphId) {
            "lr" -> miniLR(accent)
            "logr" -> miniLogR(accent)
            "knn" -> miniKnn(accent)
            "nb" -> miniNb(accent)
            "svm" -> miniSvm(accent)
            "dt" -> miniDt(accent)
            "rf" -> miniRf(accent)
            "gb" -> miniGb(accent)
            "km" -> miniKm(accent)
            "fc" -> miniFc(accent)
            "cnn" -> miniCnn(accent)
            "rnn" -> miniRnn(accent)
            "tr" -> miniTr(accent)
            "gnn" -> miniGnn(accent)
            "ae" -> miniAe(accent)
            "dm" -> miniDm(accent)
            "gan" -> miniGan(accent)
            "som" -> miniSom(accent)
            "rl" -> miniRl(accent)
        }
    }
}

/**
 * Сторона холста мини-графика в dp: Box 46dp минус padding 7dp с двух сторон.
 * Держим числом здесь, а не считаем «на глаз», чтобы связь с [GlossaryRow] была
 * явной: поменяете размер плашки — поправьте и это число.
 */
private const val MINI_GRAPH_SIDE_DP = 32f

/**
 * Пересчёт «пиксельных» констант мини-графиков в размер текущего холста.
 *
 * ЗАЧЕМ. Все 19 рисунков написаны так: КООРДИНАТЫ — в долях холста
 * (`0.5f * w`), а РАЗМЕРЫ (радиусы точек, толщина линий) — сырыми пикселями
 * (`drawCircle(c, 2.6f, ...)`). Доли тянутся за холстом, пиксели — нет.
 * Холст здесь 32dp, то есть 32 пикселя на mdpi и 128 на xxxhdpi: точка радиусом
 * 2.6px занимала на редком экране 16% ширины рисунка, а на плотном — 4%.
 * Один и тот же значок выглядел то жирной кляксой, то еле заметной пылью.
 *
 * Множитель приводит константы к размеру холста, поэтому рисунок теперь ведёт
 * себя как настоящая иконка: одинаково выглядит на любой плотности и корректно
 * укрупняется, если плашку глоссария когда-нибудь сделают больше.
 */
private fun DrawScope.s(value: Float): Float =
    value * (size.minDimension / (MINI_GRAPH_SIDE_DP * Adaptive.ReferenceDensity))

private val SECONDARY = Color(0xFFFFD93D)
private val CLASS_GREEN = Color(0xFF6BCB77)
private val CLASS_RED = Color(0xFFFF6B6B)
private val CLASS_BLUE = Color(0xFF4D96FF)

// ---------- Классические алгоритмы ----------

private fun DrawScope.miniLR(c: Color) {
    val w = size.width; val h = size.height
    val pts = listOf(0.12f to 0.78f, 0.3f to 0.6f, 0.42f to 0.66f, 0.58f to 0.4f, 0.72f to 0.46f, 0.88f to 0.2f)
    drawLine(c, Offset(0.04f * w, h * 0.85f), Offset(0.96f * w, h * 0.12f), strokeWidth = s(2.2f))
    pts.forEach { drawCircle(Color.White.copy(alpha = 0.85f), s(2.4f), Offset(it.first * w, it.second * h)) }
}

private fun DrawScope.miniLogR(c: Color) {
    val w = size.width; val h = size.height
    val path = Path()
    for (x in 0..40) {
        val t = x / 40f
        val y = h - h / (1f + exp((-9f * (t - 0.5f)).toDouble()).toFloat())
        if (x == 0) path.moveTo(t * w, y) else path.lineTo(t * w, y)
    }
    drawPath(path, c, style = Stroke(width = s(2.4f)))
    drawLine(
        c.copy(alpha = 0.35f), Offset(w * 0.5f, 0f), Offset(w * 0.5f, h),
        strokeWidth = s(1f), pathEffect = PathEffect.dashPathEffect(floatArrayOf(s(4f), s(4f)))
    )
}

private fun DrawScope.miniKnn(c: Color) {
    val w = size.width; val h = size.height
    val classA = listOf(0.16f to 0.22f, 0.3f to 0.38f, 0.14f to 0.48f)
    val classB = listOf(0.78f to 0.72f, 0.88f to 0.56f, 0.7f to 0.84f)
    val query = Offset(w * 0.5f, h * 0.5f)
    classA.forEach { drawCircle(c, s(2.6f), Offset(it.first * w, it.second * h)) }
    classB.forEach { drawCircle(SECONDARY, s(2.6f), Offset(it.first * w, it.second * h)) }
    listOf(classA[1], classB[0]).forEach {
        drawLine(Color.White.copy(alpha = 0.5f), query, Offset(it.first * w, it.second * h), strokeWidth = s(1f))
    }
    drawCircle(Color.White, s(3.6f), query, style = Stroke(s(1.6f)))
}

private fun DrawScope.miniNb(c: Color) {
    val w = size.width; val h = size.height
    fun bell(mean: Float, color: Color) {
        val path = Path()
        for (x in 0..40) {
            val t = x / 40f
            val g = exp((-((t - mean) * (t - mean)) * 18f).toDouble()).toFloat()
            val y = h * (0.92f - g * 0.75f)
            if (x == 0) path.moveTo(t * w, y) else path.lineTo(t * w, y)
        }
        drawPath(path, color, style = Stroke(width = s(2.1f)))
    }
    bell(0.38f, c)
    bell(0.64f, SECONDARY)
}

private fun DrawScope.miniSvm(c: Color) {
    val w = size.width; val h = size.height
    val dash = PathEffect.dashPathEffect(floatArrayOf(s(3f), s(3f)))
    drawLine(c.copy(alpha = 0.35f), Offset(w * 0.0f, h * 0.72f), Offset(w * 0.72f, h * 0.0f), strokeWidth = s(1f), pathEffect = dash)
    drawLine(c.copy(alpha = 0.35f), Offset(w * 0.28f, h), Offset(w, h * 0.28f), strokeWidth = s(1f), pathEffect = dash)
    drawLine(c, Offset(w * 0.14f, h * 0.86f), Offset(w * 0.86f, h * 0.14f), strokeWidth = s(2f))
    val left = listOf(0.1f to 0.62f, 0.24f to 0.78f)
    val right = listOf(0.76f to 0.22f, 0.9f to 0.38f)
    left.forEach { drawCircle(c, s(2.6f), Offset(it.first * w, it.second * h)) }
    right.forEach { drawCircle(SECONDARY, s(2.6f), Offset(it.first * w, it.second * h)) }
    drawCircle(Color.White, s(5f), Offset(left[0].first * w, left[0].second * h), style = Stroke(s(1.2f)))
    drawCircle(Color.White, s(5f), Offset(right[0].first * w, right[0].second * h), style = Stroke(s(1.2f)))
}

private fun DrawScope.miniDt(c: Color) {
    val w = size.width; val h = size.height
    val root = Offset(w * 0.5f, h * 0.1f)
    val l1 = Offset(w * 0.25f, h * 0.46f)
    val r1 = Offset(w * 0.75f, h * 0.46f)
    val l2 = Offset(w * 0.12f, h * 0.86f)
    val m2 = Offset(w * 0.38f, h * 0.86f)
    val r2 = Offset(w * 0.62f, h * 0.86f)
    val rr2 = Offset(w * 0.88f, h * 0.86f)
    listOf(root to l1, root to r1, l1 to l2, l1 to m2, r1 to r2, r1 to rr2)
        .forEach { (a, b) -> drawLine(c.copy(alpha = 0.75f), a, b, strokeWidth = s(1.6f)) }
    drawCircle(c, s(3f), root)
    drawCircle(c, s(2.4f), l1); drawCircle(c, s(2.4f), r1)
    listOf(l2, m2).forEach { drawCircle(CLASS_GREEN, s(2.4f), it) }
    listOf(r2, rr2).forEach { drawCircle(CLASS_RED, s(2.4f), it) }
}

private fun DrawScope.miniRf(c: Color) {
    val w = size.width; val h = size.height
    listOf(0.18f, 0.5f, 0.82f).forEach { cx ->
        val top = Offset(cx * w, h * 0.12f)
        val bl = Offset((cx - 0.13f) * w, h * 0.58f)
        val br = Offset((cx + 0.13f) * w, h * 0.58f)
        drawLine(c, top, bl, strokeWidth = s(1.8f))
        drawLine(c, top, br, strokeWidth = s(1.8f))
        drawLine(c, Offset(cx * w, h * 0.58f), Offset(cx * w, h * 0.92f), strokeWidth = s(1.8f))
        drawCircle(c, s(2.2f), top)
    }
}

private fun DrawScope.miniGb(c: Color) {
    val w = size.width; val h = size.height
    val path = Path()
    for (x in 0..40) {
        val t = x / 40f
        val y = h * 0.9f - (h * 0.72f) * (1f - exp((-3.2f * t).toDouble()).toFloat())
        if (x == 0) path.moveTo(t * w, y) else path.lineTo(t * w, y)
    }
    drawPath(path, c, style = Stroke(width = s(2.4f)))
    drawLine(c.copy(alpha = 0.25f), Offset(0f, h * 0.9f), Offset(w, h * 0.9f), strokeWidth = s(1f))
}

private fun DrawScope.miniKm(c: Color) {
    val w = size.width; val h = size.height
    val colors = listOf(c, SECONDARY, CLASS_GREEN)
    val centers = listOf(0.28f to 0.28f, 0.76f to 0.32f, 0.5f to 0.8f)
    centers.forEachIndexed { i, cl ->
        val cx = cl.first * w; val cy = cl.second * h
        val step = s(5f)
        for (dx in -1..1) for (dy in -1..1) drawCircle(colors[i], s(1.8f), Offset(cx + dx * step, cy + dy * step))
        drawCircle(
            colors[i], s(13f), Offset(cx, cy),
            style = Stroke(s(1f), pathEffect = PathEffect.dashPathEffect(floatArrayOf(s(3f), s(3f))))
        )
    }
}

// ---------- Нейросетевые архитектуры ----------

private fun DrawScope.miniFc(c: Color) {
    val w = size.width; val h = size.height
    val input = listOf(0.14f to 0.3f, 0.14f to 0.7f)
    val hidden = listOf(0.5f to 0.16f, 0.5f to 0.5f, 0.5f to 0.84f)
    val output = listOf(0.86f to 0.5f)
    input.forEach { i -> hidden.forEach { j -> drawLine(c.copy(alpha = 0.35f), Offset(i.first * w, i.second * h), Offset(j.first * w, j.second * h), strokeWidth = s(1f)) } }
    hidden.forEach { i -> output.forEach { j -> drawLine(c.copy(alpha = 0.35f), Offset(i.first * w, i.second * h), Offset(j.first * w, j.second * h), strokeWidth = s(1f)) } }
    (input + hidden + output).forEach { drawCircle(c, s(2.8f), Offset(it.first * w, it.second * h)) }
}

private fun DrawScope.miniCnn(c: Color) {
    val w = size.width; val h = size.height
    val cols = 4; val rows = 4
    val cellW = w / cols; val cellH = h / rows
    val gap = s(1.5f)
    for (i in 0 until cols) for (j in 0 until rows) {
        drawRect(c.copy(alpha = 0.14f), topLeft = Offset(i * cellW, j * cellH), size = Size(cellW - gap, cellH - gap))
    }
    drawRect(
        c, topLeft = Offset(cellW * 0.6f, cellH * 0.6f),
        size = Size(cellW * 2f, cellH * 2f), style = Stroke(width = s(2f))
    )
}

private fun DrawScope.miniRnn(c: Color) {
    val w = size.width; val h = size.height
    val xs = listOf(0.14f, 0.4f, 0.66f, 0.9f)
    val gap = s(5f)
    xs.forEachIndexed { i, x ->
        drawCircle(c, s(3.6f), Offset(x * w, h * 0.5f))
        if (i < xs.size - 1) {
            drawLine(c.copy(alpha = 0.6f), Offset(x * w + gap, h * 0.5f), Offset(xs[i + 1] * w - gap, h * 0.5f), strokeWidth = s(1.6f))
        }
    }
}

private fun DrawScope.miniTr(c: Color) {
    val w = size.width; val h = size.height
    val pts = listOf(0.18f to 0.18f, 0.82f to 0.18f, 0.18f to 0.82f, 0.82f to 0.82f, 0.5f to 0.5f)
    for (i in pts.indices) for (j in pts.indices) {
        if (i < j) {
            val a = 0.15f + 0.15f * ((i + j) % 3)
            drawLine(c.copy(alpha = a), Offset(pts[i].first * w, pts[i].second * h), Offset(pts[j].first * w, pts[j].second * h), strokeWidth = s(1f))
        }
    }
    pts.forEach { drawCircle(c, s(2.6f), Offset(it.first * w, it.second * h)) }
}

private fun DrawScope.miniGnn(c: Color) {
    val w = size.width; val h = size.height
    val pts = listOf(0.18f to 0.22f, 0.5f to 0.12f, 0.82f to 0.28f, 0.28f to 0.58f, 0.72f to 0.62f, 0.5f to 0.88f)
    val edges = listOf(0 to 1, 1 to 2, 0 to 3, 1 to 3, 1 to 4, 2 to 4, 3 to 5, 4 to 5)
    edges.forEach { (a, b) -> drawLine(c.copy(alpha = 0.5f), Offset(pts[a].first * w, pts[a].second * h), Offset(pts[b].first * w, pts[b].second * h), strokeWidth = s(1.4f)) }
    pts.forEach { drawCircle(c, s(2.8f), Offset(it.first * w, it.second * h)) }
}

private fun DrawScope.miniAe(c: Color) {
    val w = size.width; val h = size.height
    val left = listOf(0.08f to 0.2f, 0.08f to 0.5f, 0.08f to 0.8f)
    val mid = Offset(w * 0.5f, h * 0.5f)
    val right = listOf(0.92f to 0.2f, 0.92f to 0.5f, 0.92f to 0.8f)
    left.forEach { drawLine(c.copy(alpha = 0.4f), Offset(it.first * w, it.second * h), mid, strokeWidth = s(1f)) }
    right.forEach { drawLine(c.copy(alpha = 0.4f), mid, Offset(it.first * w, it.second * h), strokeWidth = s(1f)) }
    left.forEach { drawCircle(c, s(2.3f), Offset(it.first * w, it.second * h)) }
    right.forEach { drawCircle(c, s(2.3f), Offset(it.first * w, it.second * h)) }
    drawCircle(c, s(3.6f), mid)
}

private fun DrawScope.miniDm(c: Color) {
    val w = size.width; val h = size.height
    val rnd = Random(7)
    for (i in 0 until 5) {
        val t = i / 4f
        val spread = 0.03f + t * 0.17f
        val baseX = 0.14f + t * 0.72f
        repeat(3) {
            val dx = (rnd.nextFloat() - 0.5f) * spread * 2f
            val dy = (rnd.nextFloat() - 0.5f) * spread * 2f
            drawCircle(c.copy(alpha = 1f - t * 0.45f), s(1.9f), Offset((baseX + dx) * w, (0.5f + dy) * h))
        }
    }
}

private fun DrawScope.miniGan(c: Color) {
    val w = size.width; val h = size.height
    drawCircle(c, s(10f), Offset(w * 0.28f, h * 0.5f), style = Stroke(s(2f)))
    drawCircle(SECONDARY, s(10f), Offset(w * 0.72f, h * 0.5f), style = Stroke(s(2f)))
    drawLine(Color.White.copy(alpha = 0.75f), Offset(w * 0.42f, h * 0.4f), Offset(w * 0.58f, h * 0.4f), strokeWidth = s(1.6f))
    drawLine(Color.White.copy(alpha = 0.75f), Offset(w * 0.58f, h * 0.6f), Offset(w * 0.42f, h * 0.6f), strokeWidth = s(1.6f))
}

private fun DrawScope.miniSom(c: Color) {
    val w = size.width; val h = size.height
    val cols = 4; val rows = 4
    val cellW = w / cols; val cellH = h / rows
    val gap = s(1.5f)
    val palette = listOf(Color(0xFFE63946), CLASS_BLUE, SECONDARY, CLASS_GREEN, c)
    for (i in 0 until cols) for (j in 0 until rows) {
        val color = palette[(i + j * 2) % palette.size]
        drawRect(color.copy(alpha = 0.6f), topLeft = Offset(i * cellW, j * cellH), size = Size(cellW - gap, cellH - gap))
    }
}

private fun DrawScope.miniRl(c: Color) {
    val w = size.width; val h = size.height
    val cols = 4; val rows = 4
    val cellW = w / cols; val cellH = h / rows
    for (i in 0..cols) drawLine(c.copy(alpha = 0.15f), Offset(i * cellW, 0f), Offset(i * cellW, h), strokeWidth = s(0.8f))
    for (j in 0..rows) drawLine(c.copy(alpha = 0.15f), Offset(0f, j * cellH), Offset(w, j * cellH), strokeWidth = s(0.8f))
    val path = listOf(0 to 3, 1 to 3, 1 to 2, 2 to 2, 2 to 0, 3 to 0)
    for (k in 0 until path.size - 1) {
        val (x1, y1) = path[k]; val (x2, y2) = path[k + 1]
        drawLine(
            c, Offset((x1 + 0.5f) * cellW, (y1 + 0.5f) * cellH), Offset((x2 + 0.5f) * cellW, (y2 + 0.5f) * cellH),
            strokeWidth = s(2f)
        )
    }
    drawCircle(CLASS_GREEN, s(3.4f), Offset(3.5f * cellW, 0.5f * cellH))
    drawCircle(SECONDARY, s(3.4f), Offset(0.5f * cellW, 0.5f * cellH))
}
