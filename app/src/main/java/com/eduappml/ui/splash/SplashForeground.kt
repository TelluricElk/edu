package com.eduappml.ui.splash

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.min

import com.eduappml.MainActivity
import com.eduappml.ThemeManager
import com.eduappml.game.GameManager
import com.eduappml.managers.SessionManager
import com.eduappml.managers.SyncManager
import com.eduappml.ui.auth.AuthViewModel
import com.eduappml.ui.auth.AuthViewModelFactory
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SplashForeground(
    modifier: Modifier = Modifier,
    onFinishedFadeOut: () -> Unit = {},
    onOpenChat: () -> Unit = {}
) {
    val contentAlpha = remember { Animatable(1f) }
    var isExiting by remember { mutableStateOf(false) }

    suspend fun runExit() {
        if (isExiting) return
        isExiting = true
        contentAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(300, easing = CubicBezierEasing(0.3f, 0f, 0.2f, 1f))
        )
        onFinishedFadeOut()
    }

    val context = LocalContext.current
    val activity = context as? MainActivity
    val isDark = ThemeManager.isDarkThemeActive(context)
    val isGod = GameManager.isGodMode()
    val isLoggedIn = SessionManager.isLoggedIn()
    val scope = rememberCoroutineScope()

    val authViewModel: AuthViewModel = viewModel(factory = AuthViewModelFactory(context))

    BackHandler {
        if (isLoggedIn) {
            // остаёмся на экране
        } else {
            activity?.finish()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 36.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = "rnk@romannk.ru",
                    textAlign = TextAlign.Start,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.95f),
                    style = TextStyle()
                )
                Text(
                    text = "rnkromannk@gmail.com",
                    textAlign = TextAlign.Start,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.95f),
                    style = TextStyle()
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ThemeToggleButton(
                    isDark = isDark,
                    onClick = { activity?.toggleTheme() }
                )
                ModeToggleButton(
                    isGod = isGod,
                    onClick = {
                        GameManager.toggleMode()
                        activity?.recreate()
                    }
                )
                ResetProgressButton(
                    onClick = {
                        GameManager.resetProgress(context)
                        activity?.recreate()
                    }
                )
                if (isLoggedIn) {
                    LogoutButton(
                        onClick = {
                            authViewModel.logout()
                            activity?.recreate()
                        }
                    )
                }
                SyncButton(
                    onClick = {
                        val classic = GameManager.getUnlockedNodes("classic")
                        val neural = GameManager.getUnlockedNodes("neural")
                        SyncManager.syncProgress(context, classic, neural)
                        Toast.makeText(context, "Прогресс отправлен на сервер", Toast.LENGTH_SHORT).show()
                    }
                )
                LoadProgressButton(
                    onClick = {
                        scope.launch {
                            try {
                                Log.d("SplashForeground", "Loading progress from server...")
                                val progress = SyncManager.loadProgressFromServer(context)
                                if (progress != null) {
                                    SessionManager.saveProgress(progress)
                                    GameManager.updateFromProgress(progress)
                                    Toast.makeText(context, "Прогресс загружен с сервера", Toast.LENGTH_SHORT).show()
                                    activity?.recreate()
                                } else {
                                    Toast.makeText(context, "Прогресс на сервере не найден", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Log.e("SplashForeground", "Load error: ${e.message}")
                                Toast.makeText(context, "Ошибка загрузки: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
                ChatButton(
                    onClick = onOpenChat
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .alpha(contentAlpha.value),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Machine Learning",
                textAlign = TextAlign.Center,
                fontSize = 36.sp,
                lineHeight = 38.sp,
                letterSpacing = 1.5.sp,
                color = Color.White,
                style = TextStyle()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Educational App.",
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
                lineHeight = 18.sp,
                color = Color.White.copy(alpha = 0.92f),
                style = TextStyle()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Author: RNK",
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                color = Color.White.copy(alpha = 0.92f),
                style = TextStyle()
            )
        }

        ShimmerBubbleButton(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp)
                .alpha(contentAlpha.value),
            bubbleSizeDp = 64.dp,
            onClick = { runExit() }
        )
    }
}

@Composable
private fun ThemeToggleButton(
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = if (isDark) "☀️" else "🌙"
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = icon,
            fontSize = 22.sp,
            color = Color.White
        )
    }
}

@Composable
private fun ModeToggleButton(
    isGod: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = if (isGod) "⚡" else "🎯"
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = icon,
            fontSize = 22.sp,
            color = Color.White
        )
    }
}

@Composable
private fun ResetProgressButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "↺",
            fontSize = 22.sp,
            color = Color.White
        )
    }
}

@Composable
private fun LogoutButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "🚪",
            fontSize = 22.sp,
            color = Color.White
        )
    }
}

@Composable
private fun SyncButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "🔄",
            fontSize = 22.sp,
            color = Color.White
        )
    }
}

@Composable
private fun LoadProgressButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "⬇️",
            fontSize = 22.sp,
            color = Color.White
        )
    }
}

@Composable
private fun ChatButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "💬",
            fontSize = 22.sp,
            color = Color.White
        )
    }
}

@Composable
private fun ShimmerBubbleButton(
    modifier: Modifier = Modifier,
    bubbleSizeDp: Dp = 64.dp,
    onClick: suspend () -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "bubble-anim")
    val pulse by infinite.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            tween(1600, easing = LinearEasing),
            RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val shimmerAngle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(4200, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "shimmer-rot"
    )

    val pressScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .semantics { contentDescription = "Открыть меню" }
            .scale(pulse * pressScale.value)
            .size(bubbleSizeDp)
            .clickable(
                role = Role.Button,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                scope.launch {
                    pressScale.snapTo(0.94f)
                    pressScale.animateTo(
                        1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                    onClick()
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val r = min(w, h) / 2f
            val c = Offset(w / 2f, h / 2f)

            val coreBrush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFFFFF).copy(alpha = 0.85f),
                    Color(0xFFB9B6FF).copy(alpha = 0.65f),
                    Color(0xFF6FA8FF).copy(alpha = 0.55f),
                    Color(0xFF9E5CFF).copy(alpha = 0.55f)
                ),
                center = c,
                radius = r * 1.02f
            )
            drawCircle(brush = coreBrush, radius = r * 0.96f, center = c)

            drawCircle(
                color = Color.Black.copy(alpha = 0.08f),
                radius = r * 0.96f,
                center = c,
                style = Stroke(width = r * 0.10f)
            )

            drawArc(
                color = Color.White.copy(alpha = 0.28f),
                startAngle = shimmerAngle,
                sweepAngle = 40f,
                useCenter = false,
                topLeft = Offset(c.x - r * 0.90f, c.y - r * 0.90f),
                size = androidx.compose.ui.geometry.Size(r * 1.80f, r * 1.80f),
                style = Stroke(width = r * 0.10f)
            )
        }
    }
}