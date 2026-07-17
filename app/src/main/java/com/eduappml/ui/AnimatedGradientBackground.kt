package com.eduappml.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope

@Composable
fun AnimatedCurvedGradientBackground(
    isDarkTheme: Boolean,
    content: @Composable () -> Unit
) {
    if (!isDarkTheme) {
        content()
        return
    }

    val infiniteTransition = rememberInfiniteTransition()

    // Позиции для розового сияния
    val pinkX by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(15000, easing = FastOutSlowInEasing), RepeatMode.Reverse)
    )
    val pinkY by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Reverse)
    )

    // Позиции для синего сияния
    val blueX by infiniteTransition.animateFloat(
        initialValue = 0.7f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(18000, easing = FastOutSlowInEasing), RepeatMode.Reverse)
    )
    val blueY by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse)
    )

    // Позиции для фиолетового сияния
    val violetX by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(20000, easing = CubicBezierEasing(0.2f, 0.8f, 0.6f, 1f)), RepeatMode.Reverse)
    )
    val violetY by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse)
    )

    // Радиусы сияний
    val pinkRadius by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(9000), RepeatMode.Reverse)
    )
    val blueRadius by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(11000), RepeatMode.Reverse)
    )
    val violetRadius by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(13000), RepeatMode.Reverse)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .drawBehind {
                drawAurora(
                    color = Color(0xFFE63946).copy(alpha = 0.55f),
                    centerX = pinkX,
                    centerY = pinkY,
                    radiusFactor = pinkRadius,
                    size = this.size
                )
                drawAurora(
                    color = Color(0xFF1E88E5).copy(alpha = 0.5f),
                    centerX = blueX,
                    centerY = blueY,
                    radiusFactor = blueRadius,
                    size = this.size
                )
                drawAurora(
                    color = Color(0xFF9C27B0).copy(alpha = 0.45f),
                    centerX = violetX,
                    centerY = violetY,
                    radiusFactor = violetRadius,
                    size = this.size
                )
            }
    ) {
        // Лёгкое затемнение для читаемости текста
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.2f))
        )
        content()
    }
}

private fun DrawScope.drawAurora(
    color: Color,
    centerX: Float,
    centerY: Float,
    radiusFactor: Float,
    size: androidx.compose.ui.geometry.Size
) {
    val center = Offset(centerX * size.width, centerY * size.height)
    val radius = size.width * radiusFactor
    if (radius <= 0f) return
    val brush = Brush.radialGradient(
        colors = listOf(
            color,
            color.copy(alpha = 0.2f),
            Color.Transparent
        ),
        center = center,
        radius = radius
    )
    drawCircle(
        brush = brush,
        radius = radius,
        center = center,
        blendMode = BlendMode.Screen
    )
}