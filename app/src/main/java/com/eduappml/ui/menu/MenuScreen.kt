package com.eduappml.ui.menu

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.hypot

import com.eduappml.game.GameManager
import com.eduappml.ui.common.BottomPillButton
import com.eduappml.ui.glossary.GlossaryScreen

@Composable
fun MenuScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onForward: () -> Unit = {},
    onOpenInfo: (String) -> Unit = {},  // устаревший, оставлен для совместимости
    onOpenGlossary: () -> Unit = {},
    onOpenDetail: (String) -> Unit = {}, // новый колбэк для детального режима
    externalVisible: Boolean = true,
    interactionsEnabled: Boolean = true
) {
    var showGlossary by remember { mutableStateOf(false) }

    BackHandler {
        if (showGlossary) {
            showGlossary = false
        } else {
            onBack()
        }
    }

    Crossfade(
        targetState = showGlossary,
        animationSpec = tween(durationMillis = 700),
        label = "menuCrossfade"
    ) { isGlossary ->
        if (isGlossary) {
            GlossaryScreen(
                onBack = { showGlossary = false }
            )
        } else {
            MainMenuContent(
                onBack = onBack,
                onForward = onForward,
                onOpenInfo = onOpenInfo,
                onOpenGlossary = { showGlossary = true },
                onOpenDetail = onOpenDetail,
                externalVisible = externalVisible,
                interactionsEnabled = interactionsEnabled
            )
        }
    }
}

@Composable
private fun MainMenuContent(
    onBack: () -> Unit,
    onForward: () -> Unit,
    onOpenInfo: (String) -> Unit,
    onOpenGlossary: () -> Unit,
    onOpenDetail: (String) -> Unit,
    externalVisible: Boolean,
    interactionsEnabled: Boolean
) {
    val nodes = remember { defaultNodes() }
    val edges = remember { defaultEdges() }

    val unlockedNodes = remember { GameManager.getUnlockedNodes("classic") }
    val isGod = GameManager.isGodMode()

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    val exitMs = 1800
    val titleEnterMs = 2500
    val easeInOut = CubicBezierEasing(0.35f, 0.0f, 0.15f, 1.0f)

    var backAvoid by remember { mutableStateOf<AvoidCircle?>(null) }
    var glossaryAvoid by remember { mutableStateOf<AvoidCircle?>(null) }
    var fwdAvoid by remember { mutableStateOf<AvoidCircle?>(null) }
    val extraPadPx = with(LocalDensity.current) { 36.dp.toPx() }

    val visible = appeared && externalVisible

    Box(modifier = Modifier.fillMaxSize()) {
        // Заголовок
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(titleEnterMs, easing = easeInOut)),
            exit = fadeOut(animationSpec = tween(exitMs, easing = easeInOut))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                PulsingTitleChip(text = "Классические алгоритмы ML")
            }
        }

        // Граф пузырей – клик вызывает onOpenDetail
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(titleEnterMs, easing = easeInOut)),
            exit = fadeOut(animationSpec = tween(exitMs, easing = easeInOut))
        ) {
            BubbleGraph(
                nodes = nodes,
                edges = edges,
                modifier = Modifier.fillMaxSize(),
                avoidCircles = listOfNotNull(backAvoid, glossaryAvoid, fwdAvoid),
                activeNodeIds = if (isGod) null else unlockedNodes,
                onNodeClick = { nodeId ->
                    if (interactionsEnabled && (isGod || unlockedNodes.contains(nodeId))) {
                        onOpenDetail(nodeId)   // вызываем детальный режим
                    }
                }
            )
        }

        // Нижние кнопки
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(titleEnterMs, easing = easeInOut)),
            exit = fadeOut(animationSpec = tween(exitMs, easing = easeInOut))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Row(
                    modifier = Modifier.padding(bottom = 32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    BottomPillButton(
                        text = "Назад",
                        onClick = { if (interactionsEnabled) onBack() },
                        modifier = Modifier.onGloballyPositioned { coords ->
                            val r = coords.boundsInParent()
                            val center = Offset(r.center.x, r.center.y)
                            val halfDiag = 0.5f * hypot(r.width, r.height)
                            val radius = halfDiag * 1.20f + extraPadPx
                            backAvoid = AvoidCircle(center, radiusPx = radius, strength = 1.25f)
                        }
                    )

                    BottomPillButton(
                        text = "?",
                        onClick = { if (interactionsEnabled) onOpenGlossary() },
                        modifier = Modifier.onGloballyPositioned { coords ->
                            val r = coords.boundsInParent()
                            val center = Offset(r.center.x, r.center.y)
                            val halfDiag = 0.5f * hypot(r.width, r.height)
                            val radius = halfDiag * 1.20f + extraPadPx
                            glossaryAvoid = AvoidCircle(center, radiusPx = radius, strength = 1.25f)
                        }
                    )

                    BottomPillButton(
                        text = "Вперёд",
                        onClick = { if (interactionsEnabled) onForward() },
                        modifier = Modifier.onGloballyPositioned { coords ->
                            val r = coords.boundsInParent()
                            val center = Offset(r.center.x, r.center.y)
                            val halfDiag = 0.5f * hypot(r.width, r.height)
                            val radius = halfDiag * 1.20f + extraPadPx
                            fwdAvoid = AvoidCircle(center, radiusPx = radius, strength = 1.25f)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PulsingTitleChip(text: String) {
    val shape = RoundedCornerShape(20.dp)
    val infinite = rememberInfiniteTransition(label = "title-pulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            tween(2000, easing = CubicBezierEasing(0.35f, 0.0f, 0.15f, 1.0f)),
            RepeatMode.Reverse
        ),
        label = "scale"
    )
    val glowAlpha by infinite.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.20f,
        animationSpec = infiniteRepeatable(
            tween(2000, easing = CubicBezierEasing(0.35f, 0.0f, 0.15f, 1.0f)),
            RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(
        modifier = Modifier
            .scale(pulse)
            .clip(shape)
            .drawBehind {
                inset(-6.dp.toPx()) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = glowAlpha * 0.6f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                            20.dp.toPx(),
                            20.dp.toPx()
                        )
                    )
                }
            }
            .background(Color.White.copy(alpha = 0.16f), shape)
            .border(1.dp, Color.White.copy(alpha = 0.35f), shape)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium
        )
    }
}