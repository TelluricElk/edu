package com.eduappml.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Живой фон: плавные переливы фиолетового, пурпурного, розового, голубого и жёлтого.
 * Добавлен тёплый жёлтый акцент для более солнечного свечения.
 */
@Composable
fun WaveBackground(
    modifier: Modifier = Modifier
) {
    val t = rememberInfiniteTransition(label = "wave")

    val a1 by t.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle1"
    )

    val a2 by t.animateFloat(
        initialValue = PI.toFloat(),
        targetValue = 3f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(19000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle2"
    )

    val breathePhase by t.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val r = max(w, h) * 0.35f

        fun breathe(c: Color, k: Float): Color {
            val f = 0.10f * (k * 0.5f + 0.5f)
            return lerp(c, Color.White, f)
        }

        // Цвета — баланс фиолетового, пурпурного, розового, голубого и жёлтого
        val violet = Color(0xFF5A00C8)   // фиолетовый
        val purple = Color(0xFFB5179E)   // пурпурный
        val pink   = Color(0xFFFF6FB5)   // розовый
        val blue   = Color(0xFF4895EF)   // голубой
        val yellow = Color(0xFFFFE066)   // тёплый жёлтый (усилен)

        val cv = breathe(violet, breathePhase)
        val cp = breathe(purple, -breathePhase)
        val ck = breathe(pink, breathePhase * 0.7f)
        val cb = breathe(blue, -breathePhase * 0.8f)
        val cy = breathe(yellow, breathePhase * 1.0f)

        // ------- Слой 1 -------
        val c1 = Offset(w / 2f + cos(a1) * r, h / 2f + sin(a1) * r)
        val dir1 = a1 + PI.toFloat() / 2f
        val s1 = Offset(c1.x - cos(dir1) * r * 2f, c1.y - sin(dir1) * r * 2f)
        val e1 = Offset(c1.x + cos(dir1) * r * 2f, c1.y + sin(dir1) * r * 2f)

        val brush1 = Brush.linearGradient(
            colors = listOf(cv, cp, ck, cy, cb), // ← добавлен жёлтый ближе к центру
            start = s1,
            end = e1
        )
        drawRect(brush = brush1, size = size, style = Fill)

        // ------- Слой 2 (мягкий полупрозрачный слой поверх) -------
        val c2 = Offset(w / 2f + cos(a2) * r * 0.8f, h / 2f + sin(a2) * r * 0.8f)
        val dir2 = a2 - PI.toFloat() / 3f
        val s2 = Offset(c2.x - cos(dir2) * r * 1.6f, c2.y - sin(dir2) * r * 1.6f)
        val e2 = Offset(c2.x + cos(dir2) * r * 1.6f, c2.y + sin(dir2) * r * 1.6f)

        val brush2 = Brush.linearGradient(
            colors = listOf(
                cv.copy(alpha = 0.14f),
                cp.copy(alpha = 0.14f),
                ck.copy(alpha = 0.14f),
                cy.copy(alpha = 0.20f), // жёлтый немного сильнее
                cb.copy(alpha = 0.14f)
            ),
            start = s2,
            end = e2
        )
        drawRect(brush = brush2, size = size, style = Fill)
    }

}