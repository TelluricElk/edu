package com.eduappml.ui.third

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eduappml.game.GameManager
import com.eduappml.ui.common.BottomPillButton
import com.eduappml.ui.glossary.NeuralGlossaryScreen
import com.eduappml.ui.menu.AvoidCircle
import com.eduappml.ui.menu.BubbleGraph
import com.eduappml.ui.menu.EdgeSpec
import com.eduappml.ui.menu.NodeSpec
import kotlin.math.hypot

@Composable
fun ThirdScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenInfo: (String) -> Unit = {},  // устаревший
    onOpenDetail: (String) -> Unit = {}  // новый колбэк
) {
    var showGlossary by remember { mutableStateOf(false) }

    Crossfade(
        targetState = showGlossary,
        animationSpec = tween(durationMillis = 700),
        label = "thirdCrossfade"
    ) { isGlossary ->
        if (isGlossary) {
            NeuralGlossaryScreen(
                onBack = { showGlossary = false }
            )
        } else {
            MainThirdContent(
                onBack = onBack,
                onOpenInfo = onOpenInfo,
                onOpenGlossary = { showGlossary = true },
                onOpenDetail = onOpenDetail
            )
        }
    }
}

@Composable
private fun MainThirdContent(
    onBack: () -> Unit,
    onOpenInfo: (String) -> Unit,
    onOpenGlossary: () -> Unit,
    onOpenDetail: (String) -> Unit
) {
    val nodes = remember { thirdNodes() }
    val edges = remember { thirdEdges() }

    val unlockedNodes = remember { GameManager.getUnlockedNodes("neural") }
    val isGod = GameManager.isGodMode()

    var backAvoid by remember { mutableStateOf<AvoidCircle?>(null) }
    var glossaryAvoid by remember { mutableStateOf<AvoidCircle?>(null) }
    val extraPadPx = with(LocalDensity.current) { 36.dp.toPx() }

    BackHandler { onBack() }

    Box(modifier = Modifier.fillMaxSize()) {
        // Заголовок
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(2500)),
            exit = fadeOut(animationSpec = tween(700))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                TitleChip(text = "Искусственные нейронные сети")
            }
        }

        // Граф пузырей – клик вызывает onOpenDetail
        BubbleGraph(
            nodes = nodes,
            edges = edges,
            modifier = Modifier.fillMaxSize(),
            avoidCircles = listOfNotNull(backAvoid, glossaryAvoid),
            activeNodeIds = if (isGod) null else unlockedNodes,
            onNodeClick = { id ->
                if (isGod || unlockedNodes.contains(id)) {
                    onOpenDetail(id)
                }
            }
        )

        // Кнопки "Назад" и "?"
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(2500)),
            exit = fadeOut(animationSpec = tween(700))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BottomPillButton(
                        text = "Назад",
                        onClick = onBack,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .onGloballyPositioned { coords ->
                                val rect = coords.boundsInParent()
                                val center = Offset(rect.center.x, rect.center.y)
                                val halfDiag = 0.5f * hypot(rect.width, rect.height)
                                val radius = halfDiag * 1.2f + extraPadPx
                                backAvoid = AvoidCircle(center, radiusPx = radius, strength = 1.2f)
                            }
                    )

                    BottomPillButton(
                        text = "?",
                        onClick = onOpenGlossary,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .onGloballyPositioned { coords ->
                                val rect = coords.boundsInParent()
                                val center = Offset(rect.center.x, rect.center.y)
                                val halfDiag = 0.5f * hypot(rect.width, rect.height)
                                val radius = halfDiag * 1.2f + extraPadPx
                                glossaryAvoid = AvoidCircle(center, radiusPx = radius, strength = 1.2f)
                            }
                    )

                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// ---------- Узлы и рёбра ----------
fun thirdNodes(): List<NodeSpec> = listOf(
    NodeSpec(id = "fc",   label = "FC",   xFrac = 0.50f, yFrac = 0.40f, radiusDp = 30f),
    NodeSpec(id = "ae",   label = "AE",   xFrac = 0.50f, yFrac = 0.14f, radiusDp = 26f),
    NodeSpec(id = "cnn",  label = "CNN",  xFrac = 0.84f, yFrac = 0.18f, radiusDp = 28f),
    NodeSpec(id = "gan",  label = "GAN",  xFrac = 0.16f, yFrac = 0.18f, radiusDp = 28f),
    NodeSpec(id = "dm",   label = "DM",   xFrac = 0.86f, yFrac = 0.55f, radiusDp = 26f),
    NodeSpec(id = "gnn",  label = "GNN",  xFrac = 0.14f, yFrac = 0.55f, radiusDp = 26f),
    NodeSpec(id = "rnn",  label = "RNN",  xFrac = 0.80f, yFrac = 0.80f, radiusDp = 28f),
    NodeSpec(id = "tr",   label = "TR",   xFrac = 0.38f, yFrac = 0.80f, radiusDp = 28f),
    NodeSpec(id = "som",  label = "SOM",  xFrac = 0.54f, yFrac = 0.82f, radiusDp = 26f),
    NodeSpec(id = "rl",   label = "RL",   xFrac = 0.12f, yFrac = 0.75f, radiusDp = 28f)
)

fun thirdEdges(): List<EdgeSpec> = listOf(
    EdgeSpec("fc", "cnn"),
    EdgeSpec("fc", "rnn"),
    EdgeSpec("rnn", "tr"),
    EdgeSpec("tr", "gnn"),
    EdgeSpec("fc", "ae"),
    EdgeSpec("ae", "dm"),
    EdgeSpec("gan", "dm"),
    EdgeSpec("cnn", "gan"),
    EdgeSpec("cnn", "dm"),
    EdgeSpec("som", "ae"),
    EdgeSpec("som", "fc"),
    EdgeSpec("rl", "fc"),
    EdgeSpec("rl", "cnn"),
    EdgeSpec("rl", "tr"),
    EdgeSpec("rl", "gnn"),
    EdgeSpec("cnn", "rnn"),
    EdgeSpec("gan", "fc"),
    EdgeSpec("gnn", "fc"),
    EdgeSpec("rnn", "ae"),
    EdgeSpec("tr", "dm"),
    EdgeSpec("gnn", "som"),
    EdgeSpec("som", "rl")
)

// ---------- Заголовок (чип) ----------
@Composable
private fun TitleChip(text: String) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .drawBehind {
                inset(-6.dp.toPx()) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.10f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx(), 20.dp.toPx())
                    )
                }
            }
            .background(Color.White.copy(alpha = 0.16f), shape)
            .border(1.dp, Color.White.copy(alpha = 0.35f), shape)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
}