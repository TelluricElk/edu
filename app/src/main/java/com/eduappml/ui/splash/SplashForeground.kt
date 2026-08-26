package com.eduappml.ui.splash

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch
import kotlin.math.min

import com.eduappml.MainActivity
import com.eduappml.R
import com.eduappml.ui.common.currentUiScale
import com.eduappml.ThemeManager
import com.eduappml.game.GameManager
import com.eduappml.managers.SessionManager
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

    val authViewModel: AuthViewModel = viewModel(factory = AuthViewModelFactory(context))

    var showSettings by remember { mutableStateOf(false) }

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
                // Было `top = 46.dp` — подобранное на глаз число, в котором
                // «спрятана» высота статус-бара конкретного телефона. Теперь
                // высоту бара спрашиваем у системы, а 8.dp — уже честный
                // визуальный отступ, одинаковый на всех устройствах.
                .statusBarsPadding()
                .padding(top = 8.dp, start = 16.dp, end = 16.dp),
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
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    SettingsIconButton(onClick = { showSettings = true })
                    SettingsPanel(
                        expanded = showSettings,
                        isDark = isDark,
                        isGod = isGod,
                        isLoggedIn = isLoggedIn,
                        onToggleTheme = { activity?.toggleTheme() },
                        onToggleMode = {
                            GameManager.toggleMode()
                            activity?.recreate()
                        },
                        onLogout = {
                            authViewModel.logout()
                            activity?.recreate()
                        },
                        onDismiss = { showSettings = false }
                    )
                }

                ChatOrbButton(onClick = onOpenChat)
            }
        }

        // Эмблема и заголовки масштабируются вместе с экраном: на планшете
        // эмблема 108dp посреди 10 дюймов теряется, а 36sp читается как
        // подпись, а не как титул. На телефоне коэффициент равен 1.0 —
        // там ничего не меняется. Подъём колонки (-40dp) тоже пропорционален,
        // иначе на большом экране он перестаёт быть заметен, а на маленьком
        // становится слишком грубым.
        val titleScale = currentUiScale()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .alpha(contentAlpha.value)
                .offset(y = (-40).dp * titleScale),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.emblem_krasnodar),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(108.dp * titleScale)
            )
            Spacer(Modifier.height(18.dp * titleScale))
            Text(
                text = "Машинное обучение",
                textAlign = TextAlign.Center,
                fontSize = 36.sp * titleScale,
                lineHeight = 38.sp * titleScale,
                letterSpacing = 1.5.sp,
                color = Color.White,
                style = TextStyle()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Образовательное приложение",
                textAlign = TextAlign.Center,
                fontSize = 16.sp * titleScale,
                lineHeight = 18.sp * titleScale,
                color = Color.White.copy(alpha = 0.92f),
                style = TextStyle()
            )
        }

        ShimmerBubbleButton(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .alpha(contentAlpha.value),
            bubbleSizeDp = 64.dp,
            onClick = { runExit() }
        )
    }
}

/** Нейтральная кнопка-утилита: тёмное стекло, без свечения — визуально «второстепенная» рядом с чатом. */
@Composable
private fun SettingsIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Настройки" },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Панель настроек — тема, режим, выход. Раньше это были три отдельных
 * кружка с эмодзи без подписей; теперь один явный список с иконками и
 * текстом, разворачивающийся из кнопки-шестерёнки.
 */
@Composable
private fun SettingsPanel(
    expanded: Boolean,
    isDark: Boolean,
    isGod: Boolean,
    isLoggedIn: Boolean,
    onToggleTheme: () -> Unit,
    onToggleMode: () -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!expanded) return

    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(0, 132),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(140)) + expandVertically(tween(160)),
            exit = fadeOut(tween(100)) + shrinkVertically(tween(120))
        ) {
            Column(
                modifier = Modifier
                    .width(232.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF1B1526))
                    .padding(vertical = 6.dp)
            ) {
                SettingsRow(
                    icon = if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                    label = if (isDark) "Светлая тема" else "Тёмная тема",
                    onClick = { onToggleTheme(); onDismiss() }
                )
                SettingsRow(
                    icon = if (isGod) Icons.Filled.Person else Icons.Filled.AdminPanelSettings,
                    label = if (isGod) "Обычный режим" else "Режим разработчика",
                    onClick = { onToggleMode(); onDismiss() }
                )
                if (isLoggedIn) {
                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 12.dp)
                    )
                    SettingsRow(
                        icon = Icons.Filled.ExitToApp,
                        label = "Выйти из аккаунта",
                        tint = Color(0xFFFF8A8A),
                        onClick = { onLogout(); onDismiss() }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint.copy(alpha = 0.92f),
            modifier = Modifier.size(19.dp)
        )
        Text(text = label, color = tint.copy(alpha = 0.95f), fontSize = 14.sp)
    }
}

/**
 * Кнопка чата — маленький светящийся шар, повторяющий рецепт градиента
 * главной кнопки внизу экрана (ShimmerBubbleButton), чтобы визуально
 * читаться как «ещё один магический вход», а не рядовая утилита.
 */
@Composable
private fun ChatOrbButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "chat-orb-pulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val pressScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .size(40.dp)
            .scale(pulse * pressScale.value)
            .clickable(
                role = Role.Button,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                scope.launch {
                    pressScale.snapTo(0.92f)
                    pressScale.animateTo(1f, animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
                }
                onClick()
            }
            .semantics { contentDescription = "Открыть чат с Edu.AI" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            val coreBrush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFFFFF).copy(alpha = 0.85f),
                    Color(0xFFB9B6FF).copy(alpha = 0.65f),
                    Color(0xFF6FA8FF).copy(alpha = 0.55f),
                    Color(0xFF9E5CFF).copy(alpha = 0.55f)
                ),
                center = c,
                radius = r * 1.05f
            )
            drawCircle(brush = coreBrush, radius = r * 0.96f, center = c)
            drawCircle(
                color = Color.Black.copy(alpha = 0.10f),
                radius = r * 0.96f,
                center = c,
                style = Stroke(width = r * 0.16f)
            )
        }
        Icon(
            imageVector = Icons.Filled.ChatBubble,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(17.dp)
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
