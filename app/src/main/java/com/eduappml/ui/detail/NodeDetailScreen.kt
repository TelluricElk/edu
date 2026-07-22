package com.eduappml.ui.detail

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.hypot

import com.eduappml.ui.menu.EdgeSpec

@Composable
fun NodeDetailScreen(
    modifier: Modifier = Modifier,
    nodeId: String,
    nodeLabel: String,
    screenType: String,
    edges: List<EdgeSpec>,
    auraColor: Color,
    onBack: () -> Unit,
    onOpenTheory: (String) -> Unit,
    onOpenMath: (String) -> Unit,
    onOpenCode: (String) -> Unit,
    onOpenInteractive: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenResult: (String) -> Unit
) {
    val density = LocalDensity.current

    var timeSec by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { now ->
                val dt = 1f / 60f
                timeSec += dt
            }
        }
    }

    val phase = 0f
    val speed = 0.25f
    val pulse = ((sin((timeSec * speed + phase) * 2f * PI.toFloat()) + 1f) * 0.5f)
    val auraRadiusScale = 1.12f + 0.28f * pulse
    val auraStrokeScale = 0.12f + 0.06f * pulse
    val auraAlpha = 0.15f + 0.15f * pulse

    val childPulse = ((sin((timeSec * 0.3f) * 2f * PI.toFloat()) + 1f) * 0.5f)
    val childScale = 1f + 0.08f * childPulse

    data class ChildData(
        val icon: ImageVector,
        val action: () -> Unit
    )

    // Порядок пузырей выстроен как последовательность обучения по часовой стрелке:
    // книга    -> Теория (общая информация) — сначала понять, что это такое
    // "?"      -> Эталонная задача — увидеть конкретный пример
    // "S"      -> Мат. основа — как это считается формально
    // "< >"    -> Программная реализация — как это реализовано в коде
    // лампочка -> Интерактив — попробовать своими руками
    // алмаз    -> Решение задачи — итог и проверочный тест
    val children = listOf(
        ChildData(Icons.Filled.MenuBook) {
            onOpenTheory(nodeId)
        },
        ChildData(Icons.Filled.QuestionMark) {
            onOpenTask(nodeId)
        },
        ChildData(Icons.Filled.Functions) {
            onOpenMath(nodeId)
        },
        ChildData(Icons.Filled.Code) {
            onOpenCode(nodeId)
        },
        ChildData(Icons.Filled.Lightbulb) {
            onOpenInteractive(nodeId)
        },
        ChildData(Icons.Filled.Diamond) {
            onOpenResult(nodeId)
        }
    )

    val mainSize = 100.dp
    val childSize = 56.dp
    val orbitRadius = 140.dp

    val childPositions = children.mapIndexed { index, _ ->
        val angle = (2 * PI / children.size) * index - PI / 2
        val x = orbitRadius * cos(angle).toFloat()
        val y = orbitRadius * sin(angle).toFloat()
        Pair(x, y)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable { onBack() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val cx = size.width / 2
            val cy = size.height / 2
            val radiusPx = with(density) { orbitRadius.toPx() }

            children.forEachIndexed { index, _ ->
                val angle = (2 * PI / children.size) * index - PI / 2
                val endX = cx + radiusPx * cos(angle)
                val endY = cy + radiusPx * sin(angle)
                val start = Offset(cx, cy)
                val end = Offset(endX.toFloat(), endY.toFloat())

                drawLine(
                    color = Color.White.copy(alpha = 0.20f),
                    start = start,
                    end = end,
                    strokeWidth = 2f,
                    pathEffect = PathEffect.cornerPathEffect(36f)
                )

                val speedPxPerSec = 150f
                val pulsesPerEdge = 2
                val pulseRadiusBase = 5f
                val pulseGlow = 1.8f
                val coreColor = Color.White.copy(alpha = 0.90f)
                val glowColor = Color.White.copy(alpha = 0.20f)

                val dx = end.x - start.x
                val dy = end.y - start.y
                val L = hypot(dx, dy).coerceAtLeast(1f)
                val dirX = dx / L
                val dirY = dy / L
                val period = 2f * L

                repeat(pulsesPerEdge) { i ->
                    val phaseShiftPx = (L / pulsesPerEdge) * i + (index * 37 % 100) * 0.01f * L
                    val traveled = timeSec * speedPxPerSec + phaseShiftPx
                    val m = (traveled % period + period) % period
                    val s = if (m <= L) m else (2f * L - m)
                    val p = Offset(start.x + dirX * s, start.y + dirY * s)
                    val r = pulseRadiusBase * (1.0f + 0.15f * sin((timeSec + i * 0.4f) * 2f * PI.toFloat()))
                    drawCircle(color = glowColor, radius = r * pulseGlow, center = p)
                    drawCircle(color = coreColor, radius = r, center = p)
                }
            }
        }

        Box(
            modifier = Modifier
                .size(mainSize)
                .drawBehind {
                    val radius = size.minDimension / 2f
                    drawCircle(
                        color = auraColor.copy(alpha = auraAlpha * 0.35f),
                        radius = radius * auraRadiusScale * 1.25f,
                        center = center
                    )
                    drawCircle(
                        color = auraColor.copy(alpha = auraAlpha),
                        radius = radius * auraRadiusScale,
                        center = center,
                        style = Stroke(width = radius * auraStrokeScale)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.10f),
                        radius = radius * 1.8f,
                        center = center
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.22f),
                        radius = radius * 1.3f,
                        center = center
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.45f),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 3f)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.18f),
                        radius = (radius - 6f).coerceAtLeast(0f),
                        center = center
                    )
                }
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.16f))
                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = nodeLabel,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

        children.forEachIndexed { index, child ->
            val (xOffset, yOffset) = childPositions[index]
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(
                        x = xOffset,
                        y = yOffset
                    )
                    .size(childSize * childScale)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                    .clickable { child.action() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = child.icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
