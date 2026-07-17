package com.eduappml.ui.menu

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

data class AvoidCircle(
    val center: Offset,
    val radiusPx: Float,
    val strength: Float = 0.85f
)

@OptIn(ExperimentalTextApi::class)
@Composable
fun BubbleGraph(
    nodes: List<NodeSpec>,
    edges: List<EdgeSpec>,
    modifier: Modifier = Modifier,
    onNodeClick: (String) -> Unit = {},
    avoidCircles: List<AvoidCircle> = emptyList(),
    activeNodeIds: Set<String>? = null
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // --------- состояние ----------
    val dragOffsets = remember { mutableStateMapOf<String, Offset>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    val lastPositions = remember { mutableMapOf<String, Offset>() }
    val currentPositions = remember { mutableStateMapOf<String, Offset>() }

    // --------- палитра аур ----------
    val auraPalette = listOf(
        Color(0xFFFF6B6B), Color(0xFFFFD93D), Color(0xFF6BCB77), Color(0xFF4D96FF),
        Color(0xFFB5179E), Color(0xFFFF914D), Color(0xFF9D4EDD), Color(0xFF00C2A8)
    )
    val auraColorById = remember(nodes) {
        nodes.mapIndexed { i, n -> n.id to auraPalette[i % auraPalette.size] }.toMap()
    }

    // -------- Параметры дрейфа и время --------
    data class DriftParams(val ampX: Float, val ampY: Float, val fx: Float, val fy: Float, val mix1: Float, val phaseBias: Float)
    val rnd = remember { Random(73) }
    val driftParams = remember(nodes) {
        nodes.associate { n ->
            val ampX = 40f + rnd.nextFloat() * 60f
            val ampY = 40f + rnd.nextFloat() * 60f
            val fx   = 0.6f + rnd.nextFloat() * 1.0f
            val fy   = 0.6f + rnd.nextFloat() * 1.0f
            val mix1 = 0.30f + rnd.nextFloat() * 0.25f
            val bias = rnd.nextFloat() * 10_000f
            n.id to DriftParams(ampX, ampY, fx, fy, mix1, bias)
        }
    }

    var timeSec by remember { mutableStateOf(0f) }
    var dtSec by remember { mutableStateOf(1f / 60f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last == 0L) last = now
                val dt = (now - last) / 1_000_000_000f
                last = now
                dtSec = dt
                timeSec += dt
            }
        }
    }

    // -------- 1D noise --------
    fun fade(t: Float): Float = t * t * t * (t * (t * 6f - 15f) + 10f)
    fun hash(i: Int): Float {
        var x = i
        x = (x xor (x shl 13))
        x = x * 15731 + 789221
        x = x xor (x shr 7)
        x += 1376312589
        return (x and 0x7fffffff) / 2147483647f
    }
    fun noise1D(x: Float): Float {
        val i0 = floor(x).toInt()
        val i1 = i0 + 1
        val t = x - i0
        val a = hash(i0)
        val b = hash(i1)
        return (a + (b - a) * fade(t)) * 2f - 1f
    }
    fun rawDrift(id: String): Offset {
        val p = driftParams[id] ?: return Offset.Zero
        val seedX = p.phaseBias * 0.73f
        val seedY = p.phaseBias * 1.19f
        val n1x = noise1D(timeSec * p.fx + seedX)
        val n2x = noise1D(timeSec * (p.fx * 1.4f) + seedX * 1.3f)
        val n1y = noise1D(timeSec * p.fy + seedY)
        val n2y = noise1D(timeSec * (p.fy * 1.3f) + seedY * 0.9f)
        val nx = n1x * (1f - p.mix1) + n2x * p.mix1
        val ny = n1y * (1f - p.mix1) + n2y * p.mix1
        return Offset(nx * p.ampX, ny * p.ampY)
    }
    val lastDrift = remember { mutableStateMapOf<String, Offset>() }
    fun smoothDrift(id: String): Offset {
        val target = rawDrift(id)
        val prev = lastDrift[id] ?: target
        val alpha = 0.06f
        val sm = lerp(prev, target, alpha)
        lastDrift[id] = sm
        return sm
    }

    var canvasW by remember { mutableStateOf(0f) }
    var canvasH by remember { mutableStateOf(0f) }

    // ---------- Жесты: DRAG ----------
    val dragModifier = Modifier.pointerInput(nodes) {
        detectDragGestures(
            onDragStart = { pos ->
                var bestId: String? = null
                var bestDist = Float.MAX_VALUE
                nodes.forEach { n ->
                    val base = Offset(n.xFrac.coerceIn(0f,1f) * canvasW, n.yFrac.coerceIn(0f,1f) * canvasH)
                    val current = dragOffsets[n.id] ?: (base + smoothDrift(n.id))
                    val d = hypot(current.x - pos.x, current.y - pos.y)
                    if (d < bestDist) { bestDist = d; bestId = n.id }
                }
                draggingId = bestId
                bestId?.let { id -> dragOffsets[id] = pos }
            },
            onDrag = { change, dragAmount ->
                draggingId?.let { id ->
                    val cur = dragOffsets[id] ?: change.position
                    dragOffsets[id] = cur + dragAmount
                }
            },
            onDragEnd = { draggingId = null },
            onDragCancel = { draggingId = null }
        )
    }

    // ---------- Жесты: TAP ----------
    val tapModifier = Modifier.pointerInput(nodes) {
        detectTapGestures { pos ->
            var hitId: String? = null
            var best = Float.MAX_VALUE
            nodes.forEach { n ->
                val p = currentPositions[n.id] ?: return@forEach
                val rPx = with(density) { n.radiusDp.dp.toPx() }
                val d = hypot(p.x - pos.x, p.y - pos.y)
                if (d <= rPx && d < best) { best = d; hitId = n.id }
            }
            hitId?.let { onNodeClick(it) }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .then(dragModifier)
            .then(tapModifier)
    ) {
        canvasW = size.width
        canvasH = size.height

        val w = size.width
        val h = size.height

        fun basePos(n: NodeSpec): Offset =
            Offset(n.xFrac.coerceIn(0f, 1f) * w, n.yFrac.coerceIn(0f, 1f) * h)

        fun targetPosOf(n: NodeSpec): Offset {
            val base = basePos(n)
            val drifted = base + smoothDrift(n.id)
            val dragged = dragOffsets[n.id]
            return dragged ?: drifted
        }

        // 1) цели
        val positions = mutableMapOf<String, Offset>()
        nodes.forEach { n -> positions[n.id] = targetPosOf(n) }

        // 2) отталкивание от перетаскиваемого
        val repelRadiusPx = with(density) { 110.dp.toPx() }
        val repelStrength = 0.45f
        draggingId?.let { id ->
            val draggingPos = dragOffsets[id] ?: positions[id]
            if (draggingPos != null) {
                nodes.forEach { n ->
                    if (n.id == id) return@forEach
                    val p = positions[n.id] ?: return@forEach
                    val dx = p.x - draggingPos.x
                    val dy = p.y - draggingPos.y
                    val dist = hypot(dx, dy)
                    if (dist > 0f && dist < repelRadiusPx) {
                        val k = (repelRadiusPx - dist) / repelRadiusPx
                        val push = repelStrength * k * repelRadiusPx
                        val nx = dx / dist
                        val ny = dy / dist
                        positions[n.id] = Offset(p.x + nx * push, p.y + ny * push)
                    }
                }
            }
        }

        // 3) запретные зоны
        if (avoidCircles.isNotEmpty()) {
            nodes.forEach { n ->
                var p = positions[n.id] ?: return@forEach
                avoidCircles.forEach { ac ->
                    val dx = p.x - ac.center.x
                    val dy = p.y - ac.center.y
                    val dist = hypot(dx, dy)
                    if (dist > 0f && dist < ac.radiusPx) {
                        val k = (ac.radiusPx - dist) / ac.radiusPx
                        val push = (ac.radiusPx * ac.strength) * k
                        val nx = dx / dist
                        val ny = dy / dist
                        p = Offset(p.x + nx * push, p.y + ny * push)
                    }
                }
                positions[n.id] = p
            }
        }

        // 3.5) «стенки» экрана
        val wallStrength = 1.0f
        val wallMarginPx = with(density) { 8.dp.toPx() }
        nodes.forEach { n ->
            val rPx = with(density) { n.radiusDp.dp.toPx() }
            var p = positions[n.id] ?: return@forEach
            val leftOverlap = (wallMarginPx + rPx) - p.x
            if (leftOverlap > 0f) p = Offset(p.x + leftOverlap * wallStrength, p.y)
            val rightOverlap = p.x - (w - wallMarginPx - rPx)
            if (rightOverlap > 0f) p = Offset(p.x - rightOverlap * wallStrength, p.y)
            val bottomOverlap = p.y - (h - wallMarginPx - rPx)
            if (bottomOverlap > 0f) p = Offset(p.x, p.y - bottomOverlap * wallStrength)
            positions[n.id] = p
        }

        // 4) сглаживание + ограничение шага
        val smoothFactor = 0.08f
        val maxStepPx = with(density) { 24.dp.toPx() } * (dtSec / (1f / 60f))
        nodes.forEach { n ->
            val target = positions[n.id]!!
            val prev = lastPositions[n.id] ?: target
            var sm = lerp(prev, target, smoothFactor)
            val dx = sm.x - prev.x
            val dy = sm.y - prev.y
            val step = hypot(dx, dy)
            if (step > maxStepPx && step > 0f) {
                val scale = maxStepPx / step
                sm = Offset(prev.x + dx * scale, prev.y + dy * scale)
            }
            lastPositions[n.id] = sm
            positions[n.id] = sm
            currentPositions[n.id] = sm
        }

        // --- рёбра ---
        edges.forEach { e ->
            val pa = positions[e.fromId] ?: return@forEach
            val pb = positions[e.toId] ?: return@forEach
            drawLine(
                color = Color.White.copy(alpha = 0.20f),
                start = pa, end = pb,
                strokeWidth = 2f,
                pathEffect = PathEffect.cornerPathEffect(36f)
            )
        }

        // --- ИМПУЛЬСЫ по рёбрам ---
        run {
            val speedPxPerSec = 220f
            val pulsesPerEdge = 2
            val pulseRadiusBase = 5f
            val pulseGlow = 1.8f
            val coreColor = Color.White.copy(alpha = 0.90f)
            val glowColor = Color.White.copy(alpha = 0.20f)

            fun pingPong(pa: Offset, pb: Offset, traveledPx: Float): Offset {
                val dx = pb.x - pa.x
                val dy = pb.y - pa.y
                val L = hypot(dx, dy).coerceAtLeast(1f)
                val dirX = dx / L
                val dirY = dy / L
                val period = 2f * L
                val m = (traveledPx % period + period) % period
                val s = if (m <= L) m else (2f * L - m)
                return Offset(pa.x + dirX * s, pa.y + dirY * s)
            }

            edges.forEachIndexed { eIndex, e ->
                val pa = positions[e.fromId] ?: return@forEachIndexed
                val pb = positions[e.toId]   ?: return@forEachIndexed
                val L = hypot(pb.x - pa.x, pb.y - pa.y).coerceAtLeast(1f)

                repeat(pulsesPerEdge) { i ->
                    val phaseShiftPx = (L / pulsesPerEdge) * i + (eIndex * 37 % 100) * 0.01f * L
                    val traveled = timeSec * speedPxPerSec + phaseShiftPx
                    val p = pingPong(pa, pb, traveled)

                    val r = pulseRadiusBase * (1.0f + 0.15f * sin((timeSec + i * 0.4f) * 2f * PI.toFloat()))
                    drawCircle(color = glowColor,  radius = r * pulseGlow, center = p)
                    drawCircle(color = coreColor,  radius = r,               center = p)
                }
            }
        }

        // --- пузыри + подписи с пульсацией для всех (активные – цветные, неактивные – серые) ---
        nodes.forEach { n ->
            val p = positions[n.id]!!
            val rPx = with(density) { n.radiusDp.dp.toPx() }

            val isActive = activeNodeIds == null || activeNodeIds.contains(n.id)
            val alpha = if (isActive) 1f else 0.35f
            val nodeColor = if (isActive) Color.White else Color.Gray.copy(alpha = 0.5f)

            // Аура – пульсирует всегда, но цвет разный для активных и неактивных
            val auraBaseColor = if (isActive) (auraColorById[n.id] ?: Color.White) else Color.Gray
            val phase = (driftParams[n.id]?.phaseBias ?: 0f)
            val speed = 0.8f
            val pulse = ((sin((timeSec * speed + phase) * 2f * PI.toFloat()) + 1f) * 0.5f)
            val auraRadius = rPx * (1.12f + 0.28f * pulse)
            val auraStroke = rPx * (0.12f + 0.06f * pulse)
            val auraAlpha = 0.15f + 0.15f * pulse

            // Внешняя аура
            drawCircle(
                color = auraBaseColor.copy(alpha = auraAlpha * 0.35f * alpha),
                radius = auraRadius * 1.25f,
                center = p
            )
            // Внутренняя аура (обводка)
            drawCircle(
                color = auraBaseColor.copy(alpha = auraAlpha * alpha),
                radius = auraRadius,
                center = p,
                style = Stroke(width = auraStroke)
            )

            // Подсветка/контур (с учётом alpha)
            drawCircle(color = Color.White.copy(alpha = 0.10f * alpha), radius = rPx * 1.8f, center = p)
            drawCircle(color = Color.White.copy(alpha = 0.22f * alpha), radius = rPx * 1.3f, center = p)
            drawCircle(color = nodeColor, radius = rPx, center = p, style = Stroke(width = 3f * alpha))
            drawCircle(color = Color.White.copy(alpha = 0.18f * alpha), radius = max(0f, rPx - 6f), center = p)

            val textColor = if (isActive) Color.White else Color.Gray.copy(alpha = 0.6f)
            val layout = measurer.measure(
                text = n.label,
                style = TextStyle(
                    color = textColor,
                    fontSize = 14.sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                )
            )
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f)
            )
        }
    }
}